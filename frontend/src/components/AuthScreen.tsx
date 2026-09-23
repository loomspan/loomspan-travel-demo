import {FormEvent, useEffect, useState} from 'react';
import {PasswordField, passwordRangeError} from './PasswordField';

export type FormFailure = {message: string; fields?: Record<string, string>};
type AuthScreenProps = {
  onRegister: (email: string, password: string) => Promise<void>;
  onLogin: (email: string, password: string) => Promise<void>;
  onFailure: (failure: FormFailure) => void;
};

export function AuthScreen({onRegister, onLogin, onFailure}: AuthScreenProps) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  useEffect(() => { document.getElementById('auth-heading')?.focus(); }, [mode]);
  const switchMode = (nextMode: 'login' | 'register') => {
    setMode(nextMode);
    setPassword('');
    setFieldErrors({});
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const errors: Record<string, string> = {};
    if (!email.trim()) errors.email = 'Enter your email address.';
    const passwordError = passwordRangeError(password);
    if (passwordError) errors.password = passwordError;
    if (Object.keys(errors).length) { setPassword(''); setFieldErrors(errors); onFailure({message: 'Please correct the highlighted fields.', fields: errors}); return; }
    setPending(true); setFieldErrors({});
    try {
      if (mode === 'register') await onRegister(email, password); else await onLogin(email, password);
      setPassword('');
    } catch (failure) {
      const result = failure as FormFailure;
      setPassword(''); setFieldErrors(result.fields ?? {}); onFailure(result);
    } finally { setPending(false); }
  };

  return <section className="card auth-card" aria-labelledby="auth-heading">
    <p className="eyebrow wordmark">DeTour</p>
    <h1 id="auth-heading" tabIndex={-1}>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
    <p className="auth-introduction">Plan a trip your way. Start with airfare, a stay, or a complete itinerary. Save alternatives, compare total costs, and choose what works for you.</p>
    <div className="tabs" role="group" aria-label="Account actions">
      <button type="button" aria-pressed={mode === 'login'} disabled={pending} onClick={() => switchMode('login')}>Log in form</button>
      <button type="button" aria-pressed={mode === 'register'} disabled={pending} onClick={() => switchMode('register')}>Register</button>
    </div>
    <form onSubmit={submit} noValidate>
      <div className="field"><label htmlFor="email">Email address</label><input id="email" name="email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} aria-invalid={Boolean(fieldErrors.email)} aria-describedby={fieldErrors.email ? 'email-error' : undefined} required />{fieldErrors.email && <p id="email-error" className="field-error">{fieldErrors.email}</p>}</div>
      <PasswordField id="password" label="Password" value={password} onChange={setPassword} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} error={fieldErrors.password} />
      <button className="primary" type="submit" disabled={pending}>{pending ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}</button>
    </form>
  </section>;
}
