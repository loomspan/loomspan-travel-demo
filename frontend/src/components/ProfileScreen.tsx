import {FormEvent, useState} from 'react';
import {PasswordField, passwordRangeError} from './PasswordField';
import type {FormFailure} from './AuthScreen';

type ProfileScreenProps = {email: string; onLogout: () => Promise<void>; logoutPending: boolean; onPasswordChange: (currentPassword: string, newPassword: string) => Promise<void>; onFailure: (failure: FormFailure) => void};

export function ProfileScreen({email, onLogout, logoutPending, onPasswordChange, onFailure}: ProfileScreenProps) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const errors: Record<string, string> = {};
    const currentRangeError = passwordRangeError(currentPassword);
    if (currentRangeError) errors.currentPassword = currentPassword ? currentRangeError : 'Enter your current password.';
    const rangeError = passwordRangeError(newPassword); if (rangeError) errors.newPassword = rangeError;
    if (Object.keys(errors).length) { setCurrentPassword(''); setNewPassword(''); setFieldErrors(errors); onFailure({message: 'Please correct the highlighted fields.', fields: errors}); return; }
    setPending(true); setFieldErrors({});
    try { await onPasswordChange(currentPassword, newPassword); setCurrentPassword(''); setNewPassword(''); }
    catch (failure) { const result = failure as FormFailure; setCurrentPassword(''); setNewPassword(''); setFieldErrors(result.fields ?? {}); onFailure(result); }
    finally { setPending(false); }
  };
  return <section className="card profile-card" aria-labelledby="profile-heading">
    <div className="profile-heading"><div><p className="eyebrow">DETOUR</p><h1 id="profile-heading" tabIndex={-1}>Your profile</h1></div><button type="button" className="text-button" disabled={logoutPending} onClick={() => void onLogout()}>{logoutPending ? 'Logging out…' : 'Log out'}</button></div>
    <dl className="identity"><dt>Email address</dt><dd>{email}</dd></dl>
    <section className="empty-state" aria-labelledby="empty-heading"><h2 id="empty-heading">Your profile is ready</h2><p>There is nothing else to manage here yet.</p></section>
    <section aria-labelledby="password-heading"><h2 id="password-heading">Change password</h2><form onSubmit={submit} noValidate>
      <PasswordField id="current-password" label="Current password" value={currentPassword} onChange={setCurrentPassword} autoComplete="current-password" error={fieldErrors.currentPassword} />
      <PasswordField id="new-password" label="New password" value={newPassword} onChange={setNewPassword} autoComplete="new-password" error={fieldErrors.newPassword} />
      <button className="primary" type="submit" disabled={pending}>{pending ? 'Updating…' : 'Update password'}</button>
    </form></section>
  </section>;
}
