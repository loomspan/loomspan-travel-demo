import {describe, expect, it, vi} from 'vitest';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {PasswordField, passwordRangeError} from './PasswordField';

describe('PasswordField', () => {
  it('enforces inclusive code-point range without composition rules and preserves visibility value', async () => {
    expect(passwordRangeError('a'.repeat(11))).toBeDefined();
    expect(passwordRangeError('a'.repeat(12))).toBeUndefined();
    expect(passwordRangeError('a'.repeat(128))).toBeUndefined();
    expect(passwordRangeError('a'.repeat(129))).toBeDefined();
    expect(passwordRangeError('😀'.repeat(12))).toBeUndefined();
    const update = vi.fn();
    const user = userEvent.setup();
    const {rerender} = render(<PasswordField id="secret" label="Password" value="lowercaseonly" onChange={update} autoComplete="new-password" />);
    await user.click(screen.getByRole('button', {name: 'Show password'}));
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'text');
    expect(screen.getByLabelText('Password')).toHaveValue('lowercaseonly');
    rerender(<PasswordField id="secret" label="Password" value="lowercaseonly" onChange={update} autoComplete="new-password" />);
    expect(screen.getByLabelText('Password')).toHaveValue('lowercaseonly');
  });
});
