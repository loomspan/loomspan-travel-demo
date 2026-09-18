import {useState} from 'react';

type PasswordFieldProps = {id: string; label: string; value: string; onChange: (value: string) => void; autoComplete: string; error?: string};

export function PasswordField({id, label, value, onChange, autoComplete, error}: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);
  return <div className="field">
    <label htmlFor={id}>{label}</label>
    <div className="password-input">
      <input id={id} name={id} type={visible ? 'text' : 'password'} value={value} autoComplete={autoComplete}
        aria-describedby={`${id}-rule${error ? ` ${id}-error` : ''}`} onChange={(event) => onChange(event.target.value)} required />
      <button type="button" className="text-button" aria-pressed={visible} onClick={() => setVisible((current) => !current)}>
        {visible ? 'Hide password' : 'Show password'}
      </button>
    </div>
    <p id={`${id}-rule`} className="hint">Use 12–128 characters. Any characters are allowed.</p>
    {error && <p id={`${id}-error`} className="field-error">{error}</p>}
  </div>;
}

export function passwordRangeError(password: string): string | undefined {
  const length = Array.from(password).length;
  return length < 12 || length > 128 ? 'Password must be between 12 and 128 characters.' : undefined;
}
