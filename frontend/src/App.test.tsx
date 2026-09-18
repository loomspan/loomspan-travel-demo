import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json'}});
const noContent = () => new Response(null, {status: 204});

describe('App identity experience', () => {
  const fetchMock = vi.fn();
  beforeEach(() => { vi.stubGlobal('fetch', fetchMock); document.cookie = 'XSRF-TOKEN=token; path=/'; window.history.replaceState({}, '', '/profile'); });
  afterEach(() => { vi.unstubAllGlobals(); document.cookie = 'XSRF-TOKEN=; max-age=0; path=/'; fetchMock.mockReset(); });

  it('restores the authenticated profile from the existing server session', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test'}));
    render(<App />);
    expect(screen.getByText('Checking your account…')).toBeInTheDocument();
    expect(await screen.findByText('ada@example.test')).toBeInTheDocument();
    expect(screen.getByText('Your profile is ready')).toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/profile', expect.objectContaining({credentials: 'same-origin'}));
  });

  it('registers, logs out, and logs in again without browser persistence', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}))
      .mockResolvedValueOnce(json(201, {email: 'ada@example.test'}))
      .mockResolvedValueOnce(json(200, {email: 'ada@example.test'}))
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(json(200, {email: 'ada@example.test'}));
    const localSpy = vi.spyOn(window.localStorage, 'setItem'); const sessionSpy = vi.spyOn(window.sessionStorage, 'setItem');
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', {name: 'Welcome back'});
    await user.click(screen.getByRole('button', {name: 'Register'}));
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', {name: 'Create account'}));
    await screen.findByText('ada@example.test');
    await user.click(screen.getByRole('button', {name: 'Log out'}));
    await screen.findByRole('heading', {name: 'Welcome back'});
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await screen.findByText('ada@example.test');
    expect(localSpy).not.toHaveBeenCalled(); expect(sessionSpy).not.toHaveBeenCalled();
  });

  it('clears the active password when switching between public account forms', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', {name: 'Welcome back'});
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', {name: 'Register'}));
    expect(screen.getByLabelText('Password')).toHaveValue('');
    await user.type(screen.getByLabelText('Password'), 'bbbbbbbbbbbb');
    await user.click(screen.getByRole('button', {name: 'Log in form'}));
    expect(screen.getByLabelText('Password')).toHaveValue('');
  });

  it('does not announce registration success when the new session cannot load', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}))
      .mockResolvedValueOnce(json(201, {email: 'ada@example.test'}))
      .mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', {name: 'Welcome back'});
    await user.click(screen.getByRole('button', {name: 'Register'}));
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', {name: 'Create account'}));
    expect(await screen.findByRole('alert')).toHaveTextContent('session has ended');
    expect(screen.queryByText('Your account is ready.')).not.toBeInTheDocument();
  });

  it('shows safe failures and keeps protected data hidden when session is invalid', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'})).mockRejectedValueOnce(new TypeError('offline'));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('heading', {name: 'Welcome back'});
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    const error = await screen.findByRole('alert');
    expect(error).toHaveTextContent('could not reach');
    expect(error).toHaveFocus();
    expect(screen.queryByText('ada@example.test')).not.toBeInTheDocument();
  });

  it('keeps error-summary focus when the initial profile check fails', async () => {
    fetchMock.mockResolvedValueOnce(new Response('not json', {status: 500}));
    render(<App />);
    expect(await screen.findByRole('alert')).toHaveFocus();
    expect(screen.queryByText('Your profile is ready')).not.toBeInTheDocument();
  });

  it('changes a password only after server success and provides an accessible disclosure', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test'})).mockResolvedValueOnce(noContent());
    const user = userEvent.setup(); render(<App />);
    await screen.findByText('ada@example.test');
    await user.type(screen.getByLabelText('Current password'), 'aaaaaaaaaaaa');
    await user.type(screen.getByLabelText('New password'), 'bbbbbbbbbbbb');
    await user.click(screen.getByRole('button', {name: 'Update password'}));
    await waitFor(() => expect(screen.getByText('Your password has been updated.')).toBeInTheDocument());
    expect(screen.getByLabelText('Current password')).toHaveValue('');
    await user.keyboard('{Tab}');
    await user.click(screen.getByRole('button', {name: 'About this demo'}));
    expect(screen.getByText(/Suppliers, schedules, prices/)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'About this demo'})).toHaveAttribute('aria-expanded', 'true');
  });

  it('keeps the authenticated view when logout cannot reach the server session', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test'}))
      .mockResolvedValueOnce(json(403, {code: 'CSRF_INVALID'}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByText('ada@example.test');
    await user.click(screen.getByRole('button', {name: 'Log out'}));
    expect(await screen.findByRole('alert')).toHaveTextContent('security check');
    expect(screen.getByText('ada@example.test')).toBeInTheDocument();
    expect(screen.queryByText('You have logged out.')).not.toBeInTheDocument();
  });

  it('does not let an earlier password change overwrite a later logout', async () => {
    let completePasswordChange: ((response: Response) => void) | undefined;
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test'}))
      .mockImplementationOnce(() => new Promise<Response>((resolve) => { completePasswordChange = resolve; }))
      .mockResolvedValueOnce(noContent());
    const user = userEvent.setup(); render(<App />);
    await screen.findByText('ada@example.test');
    await user.type(screen.getByLabelText('Current password'), 'aaaaaaaaaaaa');
    await user.type(screen.getByLabelText('New password'), 'bbbbbbbbbbbb');
    await user.click(screen.getByRole('button', {name: 'Update password'}));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    await user.click(screen.getByRole('button', {name: 'Log out'}));
    await screen.findByRole('heading', {name: 'Welcome back'});
    completePasswordChange!(noContent());
    await waitFor(() => expect(screen.queryByText('Your password has been updated.')).not.toBeInTheDocument());
    expect(screen.getByRole('status')).toHaveTextContent('You have logged out.');
  });
});
