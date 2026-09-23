import {useEffect, useRef, useState} from 'react';
import {identityApi, IdentityApiError, type Profile} from './api/identityApi';
import {AboutDemoTab} from './components/AboutDemoTab';
import {AuthScreen, type FormFailure} from './components/AuthScreen';
import {ProfileScreen} from './components/ProfileScreen';
import {StatusRegion} from './components/StatusRegion';

type Screen = {kind: 'loading'} | {kind: 'public'} | {kind: 'profile'; profile: Profile};
type Notice = {kind: 'error' | 'status'; message: string; fields?: Record<string, string>};

function failureFor(error: unknown, action: 'login' | 'register' | 'password' | 'logout' | 'profile'): FormFailure {
  if (!(error instanceof IdentityApiError)) return {message: 'Something went wrong. Please try again.'};
  if (error.kind === 'network') return {message: 'We could not reach DeTour. Check your connection and try again.'};
  if (error.kind === 'csrf-missing' || error.code === 'CSRF_INVALID') return {message: 'Your security check needs to be refreshed. Reload the page and try again.'};
  if (error.code === 'UNAUTHENTICATED') return {message: 'Your session has ended. Please log in again.'};
  if (error.code === 'AUTHENTICATION_FAILED') return {message: 'We could not log you in. Check your details and try again.'};
  if (error.code === 'CURRENT_PASSWORD_INVALID') return {message: 'Your current password could not be confirmed.'};
  if (error.code === 'VALIDATION_FAILED' || Object.keys(error.fields).length) return {message: 'Please correct the highlighted fields.', fields: error.fields};
  if (action === 'register' && error.code === 'EMAIL_UNAVAILABLE') return {message: 'That email address cannot be used. Try another address.'};
  return {message: 'Something went wrong. Please try again.'};
}

export default function App() {
  const [screen, setScreen] = useState<Screen>({kind: 'loading'});
  const [notice, setNotice] = useState<Notice | undefined>();
  const errorRef = useRef<HTMLDivElement>(null);
  const profileRequestId = useRef(0);
  const sessionEpoch = useRef(0);
  const logoutInFlight = useRef(false);
  const [logoutPending, setLogoutPending] = useState(false);

  const showFailure = (failure: FormFailure) => setNotice({message: failure.message, fields: failure.fields, kind: 'error'});
  const loadProfile = async (afterLogin = false, reportUnauthenticated = false, preserveAuthenticatedOnFailure = false): Promise<boolean> => {
    const requestId = ++profileRequestId.current;
    try {
      const profile = await identityApi.getProfile();
      if (requestId !== profileRequestId.current) return false;
      setScreen({kind: 'profile', profile}); if (afterLogin) setNotice({kind: 'status', message: 'You are now logged in.'});
      return true;
    } catch (error) {
      if (requestId !== profileRequestId.current) return false;
      const failure = failureFor(error, 'profile');
      if (!preserveAuthenticatedOnFailure || (error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED')) {
        setScreen({kind: 'public'});
      }
      if (reportUnauthenticated || !(error instanceof IdentityApiError) || error.code !== 'UNAUTHENTICATED') showFailure(failure);
      return false;
    }
  };
  useEffect(() => { void loadProfile(); }, []);
  useEffect(() => { if (notice?.kind === 'error') errorRef.current?.focus(); }, [notice]);
  useEffect(() => {
    if (screen.kind !== 'loading' && notice?.kind !== 'error') document.querySelector<HTMLElement>('h1')?.focus();
  }, [screen.kind, notice?.kind]);

  const register = async (email: string, password: string) => {
    try { await identityApi.register(email, password); if (await loadProfile(false, true)) setNotice({kind: 'status', message: 'Your account is ready.'}); }
    catch (error) { throw failureFor(error, 'register'); }
  };
  const login = async (email: string, password: string) => {
    try { await identityApi.login(email, password); await loadProfile(true, true); }
    catch (error) { throw failureFor(error, 'login'); }
  };
  const logout = async () => {
    if (logoutInFlight.current) return;
    logoutInFlight.current = true;
    setLogoutPending(true);
    try {
      await identityApi.logout();
      ++profileRequestId.current; ++sessionEpoch.current;
      setScreen({kind: 'public'}); setNotice({kind: 'status', message: 'You have logged out.'});
    } catch (error) {
      const failure = failureFor(error, 'logout');
      if (error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') {
        ++profileRequestId.current; ++sessionEpoch.current;
        setScreen({kind: 'public'});
      }
      showFailure(failure);
    } finally {
      logoutInFlight.current = false;
      setLogoutPending(false);
    }
  };
  const changePassword = async (currentPassword: string, newPassword: string) => {
    const actionEpoch = sessionEpoch.current;
    try {
      await identityApi.changePassword(currentPassword, newPassword);
      if (actionEpoch !== sessionEpoch.current) return;
      setNotice({kind: 'status', message: 'Your password has been updated.'});
    }
    catch (error) {
      if (actionEpoch !== sessionEpoch.current) return;
      if (error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') { ++sessionEpoch.current; setScreen({kind: 'public'}); }
      throw failureFor(error, 'password');
    }
  };

  return <main className="shell">
    {notice?.kind === 'error' && <div className="error-summary" role="alert" tabIndex={-1} ref={errorRef}><strong>We need your attention.</strong><p>{notice.message}</p></div>}
    <StatusRegion message={notice?.kind === 'status' ? notice.message : undefined} />
    <AboutDemoTab />
    {screen.kind === 'loading' ? <p className="loading">Checking your account…</p> : screen.kind === 'profile'
      ? <ProfileScreen
          email={screen.profile.email}
          upcoming={screen.profile.upcoming}
          past={screen.profile.past}
          onLogout={logout}
          logoutPending={logoutPending}
          onPasswordChange={changePassword}
          onFailure={showFailure}
          onRefreshProfile={() => loadProfile(false, false, true)}
        />
      : <AuthScreen onRegister={register} onLogin={login} onFailure={showFailure} />}
  </main>;
}
