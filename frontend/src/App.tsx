import {useEffect, useRef, useState} from 'react';
import {identityApi, IdentityApiError, type Profile} from './api/identityApi';
import {AboutDemoTab} from './components/AboutDemoTab';
import {AuthScreen, type FormFailure} from './components/AuthScreen';
import {ProfileScreen} from './components/ProfileScreen';
import {HomeScreen, type StartMode} from './components/HomeScreen';
import {TripStartForm, createTripFromDraft, emptyTripStartDraft, type TripStartDraft} from './components/TripStartForm';
import {ActionIcon} from './components/ActionIcon';
import {BrandMark} from './components/BrandMark';
import {StatusRegion} from './components/StatusRegion';
import {GuestTripExplorer, type GuestSelections} from './components/GuestTripExplorer';
import {tripsApi, type TripResponse} from './api/tripsApi';
import {currentScreen, rememberScreen} from './screenHistory';

type Screen = {kind: 'public'} | {kind: 'profile'; profile: Profile};
type Notice = {kind: 'error' | 'status'; message: string; fields?: Record<string, string>};
const LOGIN_SUCCESS_MESSAGE = 'You are now logged in.';

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
  const [screen, setScreen] = useState<Screen>({kind: 'public'});
  const [publicDestination, setPublicDestination] = useState<'home' | 'trips'>('home');
  const [startMode, setStartMode] = useState<StartMode>('PLAN_TRIP');
  const [tripDraft, setTripDraft] = useState<TripStartDraft>(emptyTripStartDraft);
  const [guestExploring, setGuestExploring] = useState(false);
  const [guestSelections, setGuestSelections] = useState<GuestSelections>({});
  const [leaveTarget, setLeaveTarget] = useState<'home' | 'trips' | 'auth' | null>(null);
  const [saveAfterLogin, setSaveAfterLogin] = useState(false);
  const [profileViewNonce, setProfileViewNonce] = useState(0);
  const guestSaveInFlight = useRef(false);
  const guestSavedTrip = useRef<TripResponse | null>(null);
  const [guestSaveFailed, setGuestSaveFailed] = useState(false);
  const [authActive, setAuthActive] = useState(false);
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
      setScreen({kind: 'profile', profile});
      setAuthActive(false);
      if (currentScreen()?.destination === 'auth') rememberScreen(publicDestination, undefined, true);
      if (afterLogin) setNotice({kind: 'status', message: LOGIN_SUCCESS_MESSAGE});
      return true;
    } catch (error) {
      if (requestId !== profileRequestId.current) return false;
      const failure = failureFor(error, 'profile');
      if (preserveAuthenticatedOnFailure && error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') {
        setAuthActive(true);
        showFailure(failure);
      } else if (!preserveAuthenticatedOnFailure) {
        setScreen({kind: 'public'});
      }
      if (!(preserveAuthenticatedOnFailure && error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') &&
          (reportUnauthenticated || !(error instanceof IdentityApiError) || error.code !== 'UNAUTHENTICATED')) showFailure(failure);
      return false;
    }
  };
  useEffect(() => {
    if (!currentScreen()) rememberScreen('home', undefined, true);
    const restoreScreen = () => {
      if (screen.kind !== 'public') return;
      const previous = currentScreen();
      if ((guestSelections.airfare || guestSelections.stay) && previous?.destination !== 'auth' &&
          (previous?.destination !== 'trips' || !guestExploring)) {
        rememberScreen('trips', undefined, false);
        setLeaveTarget(previous?.destination === 'home' ? 'home' : 'trips');
        return;
      }
      setAuthActive(previous?.destination === 'auth');
      if (previous?.destination === 'home' || previous?.destination === 'trips') setPublicDestination(previous.destination);
    };
    window.addEventListener('popstate', restoreScreen);
    return () => window.removeEventListener('popstate', restoreScreen);
  }, [screen.kind, guestSelections, guestExploring]);
  useEffect(() => {
    if (!saveAfterLogin || screen.kind !== 'profile' || guestSaveInFlight.current) return;
    guestSaveInFlight.current = true;
    const save = async () => {
      try {
        let trip = guestSavedTrip.current ?? await createTripFromDraft(tripDraft,
          [...screen.profile.upcoming, ...screen.profile.past].map(item => item.name ?? item.label));
        guestSavedTrip.current = trip;
        let draft = trip.workingPlan ?? trip.drafts[0];
        if (guestSelections.airfare && !draft.selections.airfare) {
          trip = await tripsApi.selectAirfare(trip.id, draft.id, {expectedVersion: trip.version,
            expectedDraftVersion: draft.version, outboundFlightInstanceId: guestSelections.airfare.outbound.flightInstanceId,
            returnFlightInstanceId: guestSelections.airfare.returnFlight.flightInstanceId});
          draft = trip.workingPlan ?? trip.drafts[0];
          guestSavedTrip.current = trip;
        }
        if (guestSelections.stay && !draft.selections.stay) {
          trip = await tripsApi.selectStay(trip.id, draft.id, {expectedVersion: trip.version,
            expectedDraftVersion: draft.version, accommodationUnitId: guestSelections.stay.accommodationUnitId,
            unitCount: guestSelections.stay.pricing.requiredRooms});
          guestSavedTrip.current = trip;
        }
        setGuestSelections({}); setGuestExploring(false); setTripDraft(emptyTripStartDraft);
        guestSavedTrip.current = null; setGuestSaveFailed(false);
        setStartMode('PLAN_TRIP');
        rememberScreen('trips'); setPublicDestination('trips');
        await loadProfile();
        setProfileViewNonce(value => value + 1);
        setNotice({kind: 'status', message: 'Your selections were saved to your Trip.'});
      } catch (error) {
        setGuestSaveFailed(true);
        setNotice({kind: 'error', message: error instanceof Error ? `Your selections were not fully saved. ${error.message}` : 'Your selections could not be saved.'});
        if (guestSavedTrip.current) await loadProfile();
      } finally { setSaveAfterLogin(false); guestSaveInFlight.current = false; }
    };
    void save();
  }, [screen.kind, saveAfterLogin]);
  useEffect(() => {
    if (!guestSelections.airfare && !guestSelections.stay) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [guestSelections]);
  useEffect(() => { void loadProfile(); }, []);
  useEffect(() => { if (notice?.kind === 'error') errorRef.current?.focus(); }, [notice]);
  useEffect(() => {
    if (notice?.kind !== 'status') return;
    const timer = window.setTimeout(() => {
      setNotice(current => current === notice ? undefined : current);
    }, 6000);
    return () => window.clearTimeout(timer);
  }, [notice]);
  useEffect(() => {
    if (notice?.kind !== 'error') document.querySelector<HTMLElement>('h1')?.focus();
  }, [screen.kind, publicDestination, authActive, notice?.kind]);

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
      setScreen({kind: 'public'}); setAuthActive(false); setPublicDestination('home'); rememberScreen('home', undefined, true); setTripDraft(emptyTripStartDraft); setNotice({kind: 'status', message: 'You have logged out.'});
    } catch (error) {
      const failure = failureFor(error, 'logout');
      if (error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') {
        ++profileRequestId.current; ++sessionEpoch.current;
        setScreen({kind: 'public'}); setAuthActive(false); setTripDraft(emptyTripStartDraft);
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
      if (error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') { ++sessionEpoch.current; setAuthActive(true); }
      throw failureFor(error, 'password');
    }
  };

  const navigatePublic = (destination: 'home' | 'trips' | 'auth') => {
    if ((guestSelections.airfare || guestSelections.stay) && destination !== 'trips') { setLeaveTarget(destination); return; }
    rememberScreen(destination);
    if (destination === 'auth') setAuthActive(true);
    else setPublicDestination(destination);
  };
  const discardAndNavigate = () => {
    if (!leaveTarget) return;
    const destination = leaveTarget;
    setLeaveTarget(null); setGuestSelections({}); setGuestExploring(false); setTripDraft(emptyTripStartDraft);
    guestSavedTrip.current = null; setGuestSaveFailed(false);
    rememberScreen(destination);
    if (destination === 'auth') setAuthActive(true);
    else setPublicDestination(destination);
  };

  return <main className="shell">
    {notice?.kind === 'error' && <div className="error-summary" role="alert" tabIndex={-1} ref={errorRef}>
      <strong>We need your attention.</strong><p>{notice.message}</p>
      {notice.fields && Object.keys(notice.fields).length > 0 && <ul>
        {Object.entries(notice.fields).map(([field, message]) => {
          const id = {currentPassword: 'current-password', newPassword: 'new-password'}[field as 'currentPassword' | 'newPassword'] ?? field;
          return <li key={field}><a href={`#${id}`}>{message}</a></li>;
        })}
      </ul>}
    </div>}
    <StatusRegion message={notice?.kind === 'status' ? notice.message : undefined} />
    {screen.kind === 'profile' && saveAfterLogin && <p className="card" role="status">Saving your selections…</p>}
    {screen.kind === 'profile' && guestSaveFailed && <button type="button" className="primary" onClick={() => { setGuestSaveFailed(false); setSaveAfterLogin(true); }}>Retry saving selections</button>}
    <AboutDemoTab />
    {screen.kind === 'profile'
      ? <div className="app-frame" hidden={authActive}>
        <ProfileScreen
          key={`${screen.profile.email}:${profileViewNonce}`}
          email={screen.profile.email}
          upcoming={screen.profile.upcoming}
          past={screen.profile.past}
          onLogout={logout}
          logoutPending={logoutPending}
          onPasswordChange={changePassword}
          onFailure={showFailure}
          onRefreshProfile={() => loadProfile(false, false, true)}
          initialDestination={currentScreen()?.destination === 'profile' ? 'profile' : currentScreen()?.destination === 'trips' ? 'trips' : publicDestination}
          initialStartMode={startMode}
          tripDraft={tripDraft}
          onTripDraftChange={setTripDraft}
          onAuthenticationRequired={() => { showFailure({message: 'Your session has ended. Please log in again. Your unsaved changes are still here.'}); setAuthActive(true); }}
        />
        </div>
      : !authActive && <>
        <nav className="primary-navigation" aria-label="Primary navigation">
          <BrandMark />
          <button type="button" className="text-button" aria-current={publicDestination === 'home' ? 'page' : undefined} onClick={() => navigatePublic('home')}><ActionIcon name="home" />Home</button>
          <button type="button" className="text-button" aria-current={publicDestination === 'trips' ? 'page' : undefined} onClick={() => navigatePublic('trips')}><ActionIcon name="trip" />Plan a Trip</button>
          <button type="button" className="text-button" onClick={() => navigatePublic('auth')}><ActionIcon name="profile" />Log in</button>
        </nav>
        {publicDestination === 'home' ? <HomeScreen onStart={mode => { setStartMode(mode); navigatePublic('trips'); }} />
          : guestExploring ? <GuestTripExplorer draft={tripDraft} mode={startMode} selections={guestSelections} onChange={setGuestSelections} onSave={() => { setSaveAfterLogin(true); rememberScreen('auth'); setAuthActive(true); }} />
          : <TripStartForm draft={tripDraft} onChange={setTripDraft} mode={startMode} authenticated={false} onAuthenticationRequired={() => navigatePublic('auth')} onSuccess={() => {}} onExplore={() => setGuestExploring(true)} />}
      </>}
    {leaveTarget && <div className="modal-backdrop"><section className="card modal" role="dialog" aria-modal="true" aria-labelledby="leave-guest-heading"><h2 id="leave-guest-heading">Save your selections?</h2><p>You have a flight or stay selected. Save it before leaving this page?</p><div className="modal-actions"><button type="button" className="primary" onClick={() => { setLeaveTarget(null); setSaveAfterLogin(true); rememberScreen('auth'); setAuthActive(true); }}>Yes, save</button><button type="button" className="secondary" onClick={discardAndNavigate}>No, discard</button><button type="button" className="text-button" onClick={() => setLeaveTarget(null)}>Keep planning</button></div></section></div>}
    {authActive && <AuthScreen onRegister={register} onLogin={login} onFailure={showFailure} onCancel={screen.kind === 'public' ? () => { setSaveAfterLogin(false); window.history.back(); setAuthActive(false); } : undefined} />}
  </main>;
}
