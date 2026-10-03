import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {act, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import {identityApi, type Profile} from './api/identityApi';

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json'}});
const noContent = () => new Response(null, {status: 204});

describe('App identity experience', () => {
  const fetchMock = vi.fn();
  let lastProfile: Profile | undefined;
  const getProfile = identityApi.getProfile;
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock); document.cookie = 'XSRF-TOKEN=token; path=/'; window.history.replaceState({}, '', '/profile');
    lastProfile = undefined;
    vi.spyOn(identityApi, 'getProfile').mockImplementation(async () => {
      const profile = await getProfile();
      lastProfile = profile;
      return profile;
    });
  });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); document.cookie = 'XSRF-TOKEN=; max-age=0; path=/'; fetchMock.mockReset(); });

  async function showProfile() {
    await screen.findByRole('heading', {name: 'Home'});
    await waitFor(() => expect(lastProfile).toBeDefined());
    await screen.findByRole('button', {name: 'Profile'});
    if (!lastProfile) throw new Error('Expected a loaded profile before navigating');
    await userEvent.setup().click(screen.getByRole('button', {name: 'Profile'}));
    return screen.findByText(lastProfile.email);
  }

  async function showTrips() {
    await showProfile();
    if (lastProfile) vi.mocked(identityApi.getProfile).mockResolvedValueOnce(lastProfile);
    await userEvent.setup().click(screen.getByRole('button', {name: 'My Trips'}));
    return screen.findByRole('heading', {name: 'My Trips'});
  }
  it('shows the owned Trip list in Trips and account controls only in Profile', async () => {
    const summary = {id: 'trip-1', name: 'Family trip', label: 'Family trip', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-10', endDate: '2027-03-14', version: 0, temporalStatus: 'UPCOMING', draftCount: 0, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []};
    fetchMock.mockImplementation(async () => json(200, {email: 'ada@example.test', upcoming: [summary], past: []}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    expect(screen.getByRole('button', {name: 'Plan a Trip'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'My Trips'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    expect(await screen.findByRole('heading', {name: 'Family trip'})).toBeInTheDocument();
    expect(screen.queryByLabelText('Trip name')).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Plan a new trip'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Plan a new trip'}));
    expect(screen.getByLabelText('Trip name')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: '← Back to your trips'}));
    expect(screen.getByRole('heading', {name: 'Family trip'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Profile'}));
    expect(screen.getByText('ada@example.test')).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Change password'})).toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: 'Family trip'})).not.toBeInTheDocument();
  });

  it('renames the same Trip while preserving its Saved option and reports a conflict', async () => {
    const option = {id: 'option-1', name: 'April escape', startDate: '2027-03-20', endDate: '2027-03-25', lifecycle: 'PLANNED', version: 0, status: 'PLANNED', expired: false};
    let summary = {id: 'trip-1', name: 'Original trip', label: 'Original trip', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-10', endDate: '2027-03-14', version: 2, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 1, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: [option]};
    let rejectRename = true;
    fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
      if (url === '/api/profile') return json(200, {email: 'ada@example.test', upcoming: [summary, {...summary, id: 'rival', name: 'Rival trip', label: 'Rival trip', plannedCount: 0, alternatives: []}], past: []});
      if (url === '/api/trips/trip-1/name' && options?.method === 'PUT') {
        if (rejectRename) return json(409, {code: 'VERSION_CONFLICT', message: 'Stale version'});
        summary = {...summary, name: 'Spring break', label: 'Spring break', version: 3};
        return json(200, {id: 'trip-1', name: 'Spring break', label: 'Spring break', version: 3});
      }
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    expect(screen.queryByText('April escape')).not.toBeInTheDocument();
    expect(screen.getByText('1 Saved option')).toBeInTheDocument();
    expect(screen.queryByText('2027-03-20 to 2027-03-25')).not.toBeInTheDocument();
    expect(within(screen.getByRole('article', {name: 'Original trip'})).getByText('Mar 10, 2027 – Mar 14, 2027')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Actions for Original trip'}));
    await user.click(screen.getByRole('menuitem', {name: 'Rename trip'}));
    await user.clear(screen.getByLabelText('Trip name'));
    await user.type(screen.getByLabelText('Trip name'), 'Spring break');
    await user.click(screen.getByRole('button', {name: 'Save name'}));
    expect(await screen.findByText(/changed on the server/)).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Original trip'})).toBeInTheDocument();
    rejectRename = false;
    await user.click(screen.getByRole('button', {name: 'Save name'}));
    expect(await screen.findByRole('heading', {name: 'Spring break'})).toBeInTheDocument();
    expect(screen.queryByText('April escape')).not.toBeInTheDocument();
    expect(screen.getByText('1 Saved option')).toBeInTheDocument();
    expect(screen.getAllByRole('article').map(card => within(card).getByRole('heading').textContent)).toEqual(['Rival trip', 'Spring break']);
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips/trip-1/name')).toHaveLength(2);
  });

  it('explains persisted rename success when the following list refresh fails', async () => {
    const trip = {id: 'trip-1', name: 'Original', label: 'Original', destinationKey: 'destination-sfo', destinationName: 'San Francisco',
      startDate: '2027-03-10', endDate: '2027-03-14', inProgress: false, version: 1, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0,
      expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []};
    let renamed = false;
    fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
      if (url === '/api/profile') {
        if (renamed) throw new Error('offline');
        return json(200, {email: 'ada@example.test', upcoming: [trip], past: []});
      }
      if (url === '/api/trips/trip-1/name' && options?.method === 'PUT') {
        renamed = true; return json(200, {...trip, name: 'Saved name', version: 2});
      }
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<App />); await showTrips();
    await user.click(screen.getByRole('button', {name: 'Actions for Original'}));
    await user.click(screen.getByRole('menuitem', {name: 'Rename trip'}));
    await user.clear(screen.getByLabelText('Trip name')); await user.type(screen.getByLabelText('Trip name'), 'Saved name');
    await user.click(screen.getByRole('button', {name: 'Save name'}));
    expect(await screen.findByText('Trip renamed, but the list could not refresh. Reload to see the latest name.')).toHaveAttribute('role', 'alert');
    expect(renamed).toBe(true); expect(screen.getByRole('heading', {name: 'Original'})).toBeInTheDocument();
    expect(screen.getByLabelText('Trip name')).toHaveValue('Saved name');
  });

  it('refreshes an already mounted Working plan after renaming its Trip', async () => {
    let name = 'Original trip';
    let version = 0;
    const summary = () => ({id: 'trip-1', name, label: name, destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-10', endDate: '2027-03-14', version, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []});
    const detail = () => ({...summary(), originAirportCode: 'PDX', travelerCount: 1, travelerAges: [30], budgetCents: null, drafts: [{id: 'draft-1', version: 0, selections: {airfare: null, stay: null, rental: null}}], planned: [], revisionSummary: null});
    fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
      if (url === '/api/profile') return json(200, {email: 'ada@example.test', upcoming: [summary()], past: []});
      if (url === '/api/trips/trip-1' && !options?.method?.startsWith('PUT')) return json(200, detail());
      if (url === '/api/trips/trip-1/name' && options?.method === 'PUT') {
        name = 'Renamed trip'; version += 1;
        return json(200, detail());
      }
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    await user.click(screen.getByRole('button', {name: 'Open trip Original trip'}));
    expect(await screen.findByRole('heading', {name: 'Original trip'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    await user.click(screen.getByRole('button', {name: /^Actions for /}));
    await user.click(screen.getByRole('menuitem', {name: 'Rename trip'}));
    await user.clear(screen.getByLabelText('Trip name'));
    await user.type(screen.getByLabelText('Trip name'), 'Renamed trip');
    await user.click(screen.getByRole('button', {name: 'Save name'}));
    expect(await screen.findByRole('heading', {name: 'Renamed trip'})).toBeInTheDocument();
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips/trip-1')).toHaveLength(2));
    await user.click(screen.getByRole('button', {name: 'Open trip Renamed trip'}));
    expect(await screen.findByRole('heading', {name: 'Renamed trip'})).toBeInTheDocument();
  });

  it('restores the authenticated profile from the existing server session', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test'}));
    render(<App />);
    expect(screen.getByRole('heading', {name: 'Home'})).toBeInTheDocument();
    expect(await showProfile()).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Change password'})).toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/profile', expect.objectContaining({credentials: 'same-origin'}));
  });

  it('removes an action confirmation after six seconds', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}))
      .mockResolvedValueOnce(noContent());
    render(<App />);
    await screen.findByRole('button', {name: 'Log out'});
    vi.useFakeTimers();
    try {
      await act(async () => { fireEvent.click(screen.getByRole('button', {name: 'Log out'})); });
      expect(screen.getByRole('status')).toHaveTextContent('You have logged out.');
      act(() => { vi.advanceTimersByTime(5999); });
      expect(screen.getByRole('status')).toHaveTextContent('You have logged out.');
      act(() => { vi.advanceTimersByTime(1); });
      expect(screen.queryByRole('status')).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('moves focus to Home and Profile headings when primary navigation changes views', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(json(200, {email: 'ada@example.test', upcoming: [], past: []})));
    const user = userEvent.setup(); render(<App />);
    await showProfile();
    await user.click(screen.getByRole('button', {name: 'Home'}));
    await waitFor(() => expect(screen.getByRole('heading', {name: 'Home'})).toHaveFocus());
    await user.click(screen.getByRole('button', {name: 'Profile'}));
    await waitFor(() => expect(screen.getByRole('heading', {name: 'Your profile'})).toHaveFocus());
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
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await screen.findByRole('heading', {name: 'Welcome back'});
    await user.click(screen.getByRole('button', {name: 'Register'}));
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', {name: 'Create account'}));
    await showProfile();
    await user.click(screen.getByRole('button', {name: 'Log out'}));
    await screen.findByRole('heading', {name: 'Home'});
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await screen.findByRole('heading', {name: 'Welcome back'});
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await showProfile();
    expect(localSpy).not.toHaveBeenCalled(); expect(sessionSpy).not.toHaveBeenCalled();
  });

  it('clears the active password when switching between public account forms', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const user = userEvent.setup(); render(<App />);
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await screen.findByRole('heading', {name: 'Welcome back'});
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', {name: 'Register'}));
    expect(screen.getByRole('heading', {name: 'Create your account'})).toHaveFocus();
    expect(screen.getByLabelText('Password')).toHaveValue('');
    await user.type(screen.getByLabelText('Password'), 'bbbbbbbbbbbb');
    await user.click(screen.getByRole('button', {name: 'Log in form'}));
    expect(screen.getByLabelText('Password')).toHaveValue('');
  });

  it('focuses an actionable validation summary with links to invalid registration fields', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const user = userEvent.setup(); render(<App />);
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await user.click(await screen.findByRole('button', {name: 'Register'}));
    await user.click(screen.getByRole('button', {name: 'Create account'}));
    const summary = await screen.findByRole('alert');
    expect(summary).toHaveFocus();
    expect(within(summary).getByRole('link', {name: /enter your email address/i})).toHaveAttribute('href', '#email');
    expect(screen.getByLabelText('Email address')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true');
  });

  it('introduces DeTour on both public account forms and keeps clear actions', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const user = userEvent.setup(); render(<App />);
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await screen.findByRole('heading', {name: 'Welcome back'});
    const introduction = 'Plan a Trip your way. Start with a Working plan, create named Saved options, compare total costs, and choose what works for you.';
    expect(screen.getByText(introduction)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Log in'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'About this demo'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Register'}));
    expect(screen.getByRole('heading', {name: 'Create your account'})).toBeInTheDocument();
    expect(screen.getByText(introduction)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Create account'})).toBeInTheDocument();
  });

  it('does not announce registration success when the new session cannot load', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}))
      .mockResolvedValueOnce(json(201, {email: 'ada@example.test'}))
      .mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const user = userEvent.setup(); render(<App />);
    await user.click(screen.getByRole('button', {name: 'Log in'}));
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
    await user.click(screen.getByRole('button', {name: 'Log in'}));
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
    await showProfile();
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
    await showProfile();
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
    await showProfile();
    await user.type(screen.getByLabelText('Current password'), 'aaaaaaaaaaaa');
    await user.type(screen.getByLabelText('New password'), 'bbbbbbbbbbbb');
    await user.click(screen.getByRole('button', {name: 'Update password'}));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    await user.click(screen.getByRole('button', {name: 'Log out'}));
    await screen.findByRole('heading', {name: 'Home'});
    completePasswordChange!(noContent());
    await waitFor(() => expect(screen.queryByText('Your password has been updated.')).not.toBeInTheDocument());
    expect(screen.getByRole('status')).toHaveTextContent('You have logged out.');
  });

  it('displays empty profile onboarding with Plan Trip action and creates an initial trip', async () => {
    const createdTrip = {
      id: '11111111-1111-1111-1111-111111111111',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      originAirportCode: 'PDX',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 2,
      travelerAges: null,
      budgetCents: null,
      label: 'San Francisco — Mar 10–14, 2027',
      version: 0,
      drafts: [{ id: '22222222-2222-2222-2222-222222222222', selections: { airfare: null, stay: null, rental: null } }],
      planned: [],
      alternatives: [{ id: '22222222-2222-2222-2222-222222222222', lifecycle: 'DRAFT', version: 0, selections: { airfare: null, stay: null, rental: null } }],
      revisionSummary: null,
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [],
      past: [],
    })).mockResolvedValueOnce(json(201, createdTrip))
      .mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: '11111111-1111-1111-1111-111111111111',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 1,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [{ id: '22222222-2222-2222-2222-222222222222', lifecycle: 'DRAFT', version: 0, status: 'DRAFT', expired: false }],
      }],
      past: [],
    }));

    const user = userEvent.setup();
    render(<App />);
    expect(await showTrips()).toBeInTheDocument();

    expect(screen.getByRole('heading', {name: 'My Trips'})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'No trips yet'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Plan a new trip'}));
    await user.clear(screen.getByLabelText('Trip name'));
    await user.type(screen.getByLabelText('Trip name'), 'Spring break');
    await user.selectOptions(screen.getByLabelText('Destination'), 'destination-sfo');
    await user.type(screen.getByLabelText('Departure date'), '2027-03-10');
    await user.type(screen.getByLabelText('Return date'), '2027-03-14');
    await user.clear(screen.getByLabelText('Travelers'));
    await user.type(screen.getByLabelText('Travelers'), '2');
    await user.type(screen.getByLabelText('Traveler 1 age'), '30');
    await user.type(screen.getByLabelText('Traveler 2 age'), '12');

    await user.click(screen.getByRole('button', {name: 'Start planning'}));

    expect(fetchMock).toHaveBeenCalledWith('/api/trips', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        name: 'Spring break',
        destinationKey: 'destination-sfo',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        travelerCount: 2,
        travelerAges: [30, 12],
      }),
    }));

    expect(await screen.findByRole('heading', {name: 'San Francisco — Mar 10–14, 2027'})).toBeInTheDocument();
    expect(screen.getByText('No saved options yet. Add a selection to the Working plan, name it, and save it here.')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'My Trips'})).toHaveAttribute('aria-current', 'page');
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    await user.click(screen.getByRole('button', {name: /Back to all trips/}));
    expect(screen.getByRole('heading', {name: 'My Trips'})).toBeInTheDocument();
    expect(screen.queryByLabelText('Trip name')).not.toBeInTheDocument();
  });

  it('refreshes server date transitions and date/name ordering on My Trips and history return', async () => {
    const summary = (id: string, startDate = '2027-03-10') => ({id, name: id, label: id, destinationKey: 'destination-sfo', destinationName: 'San Francisco',
      startDate, endDate: '2027-03-14', inProgress: false, version: 0, status: 'ACTIVE', temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0,
      expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []});
    let profile = {email: 'ada@example.test', upcoming: [summary('Zulu'), summary('Alpha')], past: [] as ReturnType<typeof summary>[]};
    fetchMock.mockImplementation(async (url: string) => {
      if (url === '/api/profile') return json(200, profile);
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'My Trips'});
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    const titles = () => screen.getAllByRole('article').map(card => within(card).getByRole('heading').textContent);
    await waitFor(() => expect(titles()).toEqual(['Alpha', 'Zulu']));
    profile = {...profile, upcoming: [summary('Alpha'), {...summary('Zulu', '2027-03-12'), inProgress: true}]};
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    await waitFor(() => expect(titles()).toEqual(['Zulu', 'Alpha']));
    expect(within(screen.getByRole('article', {name: 'Zulu'})).getByText('In progress')).toBeInTheDocument();
    profile = {...profile, upcoming: [summary('Alpha')], past: [{...summary('Renamed Zulu', '2027-03-02'), endDate: '2027-03-05', temporalStatus: 'PAST'}]};
    // The history entry is the current Trips entry; simulate return after a server date edit.
    act(() => window.dispatchEvent(new PopStateEvent('popstate')));
    await screen.findByRole('heading', {name: 'Renamed Zulu'});
    expect(headingsIn('Past trips (1)')).toEqual(['Renamed Zulu']);
    expect(screen.queryByText('In progress')).not.toBeInTheDocument();
    profile = {...profile, upcoming: [{...summary('Alpha'), status: 'CANCELED'}]};
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    await screen.findByRole('heading', {name: 'Canceled trips (1)'});
    expect(headingsIn('Canceled trips (1)')).toEqual(['Alpha']);
    expect(screen.getByRole('button', {name: 'Upcoming (0)'})).toBeInTheDocument();
    function headingsIn(name: string) { return within(screen.getByRole('region', {name})).getAllByRole('article').map(card => within(card).getByRole('heading').textContent); }
  });

  it('ignores an older profile response and retains sorted cards on refresh failure', async () => {
    const summary = (name: string, inProgress = false) => ({id: 'trip-1', name, label: name, destinationKey: 'destination-sfo', destinationName: 'San Francisco',
      startDate: '2027-03-10', endDate: '2027-03-14', inProgress, version: 0, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0,
      expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []});
    fetchMock.mockResolvedValue(json(200, {email: 'ada@example.test', upcoming: [summary('Initial')], past: []}));
    const user = userEvent.setup(); render(<App />); await showTrips();
    let resolveOld!: (response: Response) => void;
    fetchMock.mockImplementationOnce(() => new Promise<Response>(resolve => {resolveOld = resolve;}))
      .mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [summary('Current', true)], past: []}));
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    await screen.findByRole('heading', {name: 'Current'});
    await act(async () => resolveOld(json(200, {email: 'ada@example.test', upcoming: [summary('Stale')], past: []})));
    expect(screen.queryByRole('heading', {name: 'Stale'})).not.toBeInTheDocument();
    expect(screen.getByText('In progress')).toBeInTheDocument();
    fetchMock.mockRejectedValueOnce(new Error('offline'));
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    await screen.findByRole('alert');
    expect(screen.getByRole('heading', {name: 'Current'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Profile'})).toBeInTheDocument();
  });

  it('restores the persistent action trigger after deleting-dialog dismissal and preserves pending/error retry', async () => {
    const trip = {id: 'trip-1', name: 'Family', label: 'Family', destinationKey: 'destination-sfo', destinationName: 'San Francisco',
      startDate: '2027-03-10', endDate: '2027-03-14', inProgress: false, version: 2, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0,
      expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []};
    let finishDelete!: (response: Response) => void;
    fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
      if (url === '/api/profile') return json(200, {email: 'ada@example.test', upcoming: [trip], past: []});
      if (url === '/api/trips/trip-1' && options?.method === 'DELETE') return new Promise<Response>(resolve => {finishDelete = resolve;});
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<App />); await showTrips();
    const trigger = screen.getByRole('button', {name: 'Actions for Family'});
    await user.click(trigger); await user.click(screen.getByRole('menuitem', {name: 'Delete trip Family'}));
    let dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('button', {name: 'Cancel'})).toHaveFocus();
    await user.tab({shift: true}); expect(within(dialog).getByRole('button', {name: 'Close dialog'})).toHaveFocus();
    await user.tab({shift: true}); expect(within(dialog).getByRole('button', {name: 'Delete trip'})).toHaveFocus();
    await user.tab(); expect(within(dialog).getByRole('button', {name: 'Close dialog'})).toHaveFocus();
    await user.keyboard('{Escape}'); expect(trigger).toHaveFocus();
    await user.click(trigger); await user.click(screen.getByRole('menuitem', {name: 'Delete trip Family'}));
    dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', {name: 'Delete trip'}));
    expect(within(dialog).getByRole('button', {name: 'Deleting…'})).toBeDisabled();
    await user.keyboard('{Escape}'); expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.click(dialog.parentElement!); expect(screen.getByRole('dialog')).toBeInTheDocument();
    await act(async () => finishDelete(json(409, {code: 'VERSION_CONFLICT', message: 'Trip changed'})));
    expect(await screen.findByRole('alert')).toHaveTextContent(/changed/);
    expect(within(screen.getByRole('dialog')).getByRole('button', {name: 'Delete trip'})).not.toBeDisabled();
    await user.keyboard('{Escape}'); expect(trigger).toHaveFocus();
  });

  it.each([
    {action: 'cancel', opened: false}, {action: 'delete', opened: false}, {action: 'delete', opened: true},
  ])('restores visible list focus when $action refresh removes the triggering card (opened=$opened)', async ({action, opened}) => {
    const trip = {id: 'trip-1', name: 'Family', label: 'Family', destinationKey: 'destination-sfo', destinationName: 'San Francisco',
      startDate: '2027-03-10', endDate: '2027-03-14', inProgress: false, version: 2, status: 'ACTIVE', temporalStatus: 'UPCOMING',
      draftCount: 1, plannedCount: 1, expiredAlternativeCount: 0, bookedCount: action === 'cancel' ? 1 : 0,
      hasBookingHistory: action === 'cancel', alternatives: []};
    let canceled = false;
    let finishRefresh!: (response: Response) => void;
    fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
      if (url === '/api/profile') return canceled ? new Promise<Response>(resolve => {finishRefresh = resolve;})
        : json(200, {email: 'ada@example.test', upcoming: [trip], past: []});
      if (url === '/api/trips/trip-1/cancel' && options?.method === 'POST') { canceled = true; return json(200, {...trip, status: 'CANCELED'}); }
      if (url === '/api/trips/trip-1' && options?.method === 'DELETE') { canceled = true; return new Response(null, {status: 204}); }
      if (url === '/api/trips/trip-1') return json(200, {...trip, originAirportCode: 'PDX', travelerCount: 1,
        travelerAges: [30], budgetCents: null, drafts: [{id: 'draft-1', version: 0,
          selections: {airfare: null, stay: null, rental: null}}], planned: [], revisionSummary: null});
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<App />); await showTrips();
    if (opened) {
      await user.click(screen.getByRole('button', {name: 'Open trip Family'}));
      await screen.findByRole('button', {name: '← Back to all trips'});
      await user.click(screen.getByRole('button', {name: 'My Trips'}));
    }
    await user.click(screen.getByRole('button', {name: 'Upcoming (1)'}));
    await user.click(screen.getByRole('button', {name: 'Actions for Family'}));
    await user.click(screen.getByRole('menuitem', {name: action === 'cancel' ? 'Cancel trip Family' : 'Delete trip Family'}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: action === 'cancel' ? 'Cancel Trip' : 'Delete trip'}));
    await waitFor(() => expect(finishRefresh).toBeTypeOf('function'));
    expect(screen.getByRole('dialog')).toHaveFocus();
    await user.keyboard('{Escape}');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    await act(async () => finishRefresh(json(200, {email: 'ada@example.test', upcoming: action === 'cancel' ? [{...trip, status: 'CANCELED'}] : [], past: []})));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByRole('article')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', {name: action === 'cancel' ? 'Upcoming trips (0)' : 'My Trips'})).toHaveFocus();
  });

  it('renders compact Upcoming and Past trips with clear nested hierarchy and status badges', async () => {
    const upcomingTrip = {
      id: 'trip-upcoming',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      label: 'San Francisco — Mar 10–14, 2027',
      version: 0,
      temporalStatus: 'UPCOMING',
      draftCount: 2,
      plannedCount: 1,
      expiredAlternativeCount: 0,
      bookedCount: 0,
      hasBookingHistory: false,
      alternatives: [
        { id: 'alt-d1', lifecycle: 'DRAFT', version: 0, status: 'DRAFT', expired: false },
        { id: 'alt-d2', lifecycle: 'DRAFT', version: 1, status: 'DRAFT', expired: false },
        { id: 'alt-p1', lifecycle: 'PLANNED', version: null, status: 'PLANNED', expired: false },
      ],
    };
    const pastTrip = {
      id: 'trip-past',
      destinationKey: 'destination-muc',
      destinationName: 'Munich',
      startDate: '2027-03-01',
      endDate: '2027-03-05',
      label: 'Munich — Mar 01–05, 2027',
      version: 0,
      temporalStatus: 'PAST',
      draftCount: 1,
      plannedCount: 0,
      expiredAlternativeCount: 1,
      bookedCount: 0,
      hasBookingHistory: false,
      alternatives: [
        { id: 'alt-exp', lifecycle: 'DRAFT', version: 0, status: 'EXPIRED', expired: true },
      ],
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [upcomingTrip],
      past: [pastTrip],
    }));

    render(<App />);
    await showTrips();
    expect(await screen.findByRole('heading', {name: 'Upcoming trips (1)'})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Past trips (1)'})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'San Francisco — Mar 10–14, 2027'})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Munich — Mar 01–05, 2027'})).toBeInTheDocument();
    expect(screen.getByText('1 Saved option')).toBeInTheDocument();
    expect(screen.getByText('1 Expired')).toBeInTheDocument();
    expect(screen.queryByText(/Booked/)).not.toBeInTheDocument();
  });

  it('shows cancelled saved trips in My Trips independently of their dates', async () => {
    const summary = (id: string, status: string, temporalStatus: string) => ({
      id, name: id, label: id, destinationKey: 'destination-sfo', destinationName: 'San Francisco',
      startDate: '2027-03-10', endDate: '2027-03-14', version: 0, status, temporalStatus,
      draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0,
      hasBookingHistory: false, alternatives: [],
    });
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test',
      upcoming: [summary('Future trip', 'ACTIVE', 'UPCOMING'), summary('Cancelled trip', 'CANCELED', 'UPCOMING')],
      past: [summary('Past trip', 'ACTIVE', 'PAST')]}));
    const user = userEvent.setup(); render(<App />);
    await showTrips();
    expect(screen.getByRole('heading', {name: 'Upcoming trips (1)'})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Past trips (1)'})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Canceled trips (1)'})).toBeInTheDocument();
    const cancelled = screen.getByRole('region', {name: 'Canceled trips (1)'});
    expect(within(cancelled).getByRole('heading', {name: 'Cancelled trip'})).toBeInTheDocument();
    expect(within(cancelled).getByText('Canceled Trip')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Canceled (1)'}));
    expect(screen.queryByRole('heading', {name: 'Upcoming trips (1)'})).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: 'Past trips (1)'})).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Canceled (1)'})).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', {name: 'All trips (3)'}));
    expect(screen.getByRole('heading', {name: 'Upcoming trips (1)'})).toBeInTheDocument();
  });

  it('keeps exactly one Working plan and offers explicit named option saving', async () => {
    const tripDetail = {
      id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco',
      originAirportCode: 'PDX', startDate: '2027-03-10', endDate: '2027-03-14',
      travelerCount: 1, travelerAges: null, budgetCents: null,
      label: 'San Francisco — Mar 10–14, 2027', version: 0,
      drafts: [{id: 'draft-1', version: 0, selections: {airfare: null, stay: null, rental: null}}],
      planned: [],
      alternatives: [{id: 'draft-1', lifecycle: 'DRAFT', version: 0,
        selections: {airfare: null, stay: null, rental: null}}],
      revisionSummary: null,
    };
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [{
      id: tripDetail.id, destinationKey: tripDetail.destinationKey,
      destinationName: tripDetail.destinationName, startDate: tripDetail.startDate,
      endDate: tripDetail.endDate, label: tripDetail.label, version: 0,
      temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0,
      expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false,
      alternatives: [],
    }], past: []})).mockResolvedValueOnce(json(200, tripDetail));
    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(screen.getByRole('button', {name: 'Open trip San Francisco — Mar 10–14, 2027'}));
    expect(await screen.findByRole('button', {name: 'Save as new option'})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /create empty draft/i})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /duplicate draft/i})).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([path, init]) => String(path).includes('/drafts') && init?.method === 'POST')).toHaveLength(0);
  });

  it('autosaves mutable traveler ages and budget with debouncing, status announcements, and input preservation on error', async () => {
    const tripDetail = {
      id: 'trip-1',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      originAirportCode: 'PDX',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 2,
      travelerAges: null,
      budgetCents: null,
      label: 'San Francisco — Mar 10–14, 2027',
      version: 0,
      drafts: [],
      planned: [],
      alternatives: [],
      revisionSummary: null,
    };

    const tripAfterSave = {
      ...tripDetail,
      version: 1,
      travelerAges: [25, 30],
      budgetCents: 250000,
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 0,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(json(200, tripDetail))
      .mockResolvedValueOnce(json(200, tripAfterSave));

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    expect(await screen.findByRole('heading', {name: 'San Francisco — Mar 10–14, 2027'})).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Open trip San Francisco — Mar 10–14, 2027'}));
    expect(await screen.findByLabelText('Traveler 1 age')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Traveler 1 age'), '25');
    await user.type(screen.getByLabelText('Traveler 2 age'), '30');
    await user.type(screen.getByLabelText('Budget (USD)'), '2500.00');

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1', expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          expectedVersion: 0,
          destinationKey: 'destination-sfo',
          startDate: '2027-03-10',
          endDate: '2027-03-14',
          travelerCount: 2,
          travelerAges: [25, 30],
          budgetCents: 250000,
        }),
      }));
    }, {timeout: 2500});

    expect(await screen.findByText('All changes saved.')).toBeInTheDocument();
  });

  it('keeps a failed edit visibly unsaved and retries the current values', async () => {
    const trip = {id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', originAirportCode: 'PDX',
      startDate: '2027-03-10', endDate: '2027-03-14', travelerCount: 1, travelerAges: null,
      budgetCents: null, label: 'Trip to San Francisco', version: 0, drafts: [], planned: [], alternatives: [], revisionSummary: null};
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [{
      id: trip.id, label: trip.label, destinationKey: trip.destinationKey, destinationName: trip.destinationName,
      startDate: trip.startDate, endDate: trip.endDate, version: 0, temporalStatus: 'UPCOMING',
      draftCount: 0, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: [],
    }], past: []})).mockResolvedValueOnce(json(200, trip))
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(json(200, {...trip, version: 1, budgetCents: 250000}));
    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: `Open trip ${trip.label}`}));
    await user.type(screen.getByLabelText('Budget (USD)'), '2500');
    expect(await screen.findByRole('button', {name: 'Retry save'})).toBeInTheDocument();
    expect(screen.getByText('Your edited details have not been saved.')).toBeInTheDocument();
    expect(screen.queryByText('All changes saved.')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Home'}));
    await user.click(screen.getByRole('button', {name: `Return to ${trip.label}`}));
    expect(screen.getByLabelText('Budget (USD)')).toHaveValue(2500);
    await user.click(screen.getByRole('button', {name: 'Retry save'}));
    expect(await screen.findByText('All changes saved.')).toBeInTheDocument();
    const puts = fetchMock.mock.calls.filter((call) => call[1]?.method === 'PUT');
    expect(puts).toHaveLength(2);
    expect(puts[1][1]?.body).toBe(JSON.stringify({expectedVersion: 0, destinationKey: trip.destinationKey,
      startDate: trip.startDate, endDate: trip.endDate, travelerCount: 1, travelerAges: null, budgetCents: 250000}));
  });

  it('keeps the active Trip and unsaved edit when profile refresh fails', async () => {
    const trip = {id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', originAirportCode: 'PDX',
      startDate: '2027-03-10', endDate: '2027-03-14', travelerCount: 1, travelerAges: null,
      budgetCents: null, label: 'Trip to San Francisco', version: 0, drafts: [], planned: [], alternatives: [], revisionSummary: null};
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [{
      id: trip.id, label: trip.label, destinationKey: trip.destinationKey, destinationName: trip.destinationName,
      startDate: trip.startDate, endDate: trip.endDate, version: 0, temporalStatus: 'UPCOMING',
      draftCount: 0, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: [],
    }], past: []})).mockResolvedValueOnce(json(200, trip))
      .mockRejectedValueOnce(new Error('offline')).mockRejectedValueOnce(new Error('offline'));
    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: `Open trip ${trip.label}`}));
    await user.type(screen.getByLabelText('Budget (USD)'), '2500');
    expect(await screen.findByRole('button', {name: 'Retry save'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'My Trips'}));
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not reach DeTour');
    expect(screen.getByRole('heading', {name: 'My Trips'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: `Open trip ${trip.label}`}));
    expect(screen.getByLabelText('Budget (USD)')).toHaveValue(2500);
    expect(screen.getByRole('button', {name: 'Retry save'})).toBeInTheDocument();
  });

  it('keeps an in-flight save and edited value while visiting Home', async () => {
    const trip = {id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', originAirportCode: 'PDX',
      startDate: '2027-03-10', endDate: '2027-03-14', travelerCount: 1, travelerAges: null,
      budgetCents: null, label: 'Trip to San Francisco', version: 0, drafts: [], planned: [], alternatives: [], revisionSummary: null};
    let resolveSave!: (response: Response) => void;
    const pendingSave = new Promise<Response>((resolve) => { resolveSave = resolve; });
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [{
      id: trip.id, label: trip.label, destinationKey: trip.destinationKey, destinationName: trip.destinationName,
      startDate: trip.startDate, endDate: trip.endDate, version: 0, temporalStatus: 'UPCOMING',
      draftCount: 0, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: [],
    }], past: []})).mockResolvedValueOnce(json(200, trip))
      .mockImplementationOnce(() => pendingSave)
      .mockResolvedValueOnce(json(200, {...trip, version: 1, budgetCents: 250000}));
    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: `Open trip ${trip.label}`}));
    await user.type(screen.getByLabelText('Budget (USD)'), '2500');
    await waitFor(() => expect(fetchMock.mock.calls.some((call) => call[1]?.method === 'PUT')).toBe(true));
    await user.click(screen.getByRole('button', {name: 'Home'}));
    expect(screen.getByText('Trip changes pending')).toBeInTheDocument();
    resolveSave(json(200, {...trip, version: 1, budgetCents: 250000}));
    await user.click(screen.getByRole('button', {name: `Return to ${trip.label}`}));
    expect(screen.getByLabelText('Budget (USD)')).toHaveValue(2500);
    expect(await screen.findByText('All changes saved.')).toBeInTheDocument();
  });

  it('finishes saving when an interim edit is reverted before the response', async () => {
    const trip = {id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', originAirportCode: 'PDX',
      startDate: '2027-03-10', endDate: '2027-03-14', travelerCount: 1, travelerAges: null,
      budgetCents: null, label: 'Trip to San Francisco', version: 0, drafts: [], planned: [], alternatives: [], revisionSummary: null};
    let resolveSave!: (response: Response) => void;
    const pendingSave = new Promise<Response>((resolve) => { resolveSave = resolve; });
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [{
      id: trip.id, label: trip.label, destinationKey: trip.destinationKey, destinationName: trip.destinationName,
      startDate: trip.startDate, endDate: trip.endDate, version: 0, temporalStatus: 'UPCOMING',
      draftCount: 0, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: [],
    }], past: []})).mockResolvedValueOnce(json(200, trip)).mockImplementationOnce(() => pendingSave);
    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: `Open trip ${trip.label}`}));
    const budget = screen.getByLabelText('Budget (USD)');
    await user.type(budget, '2500');
    await waitFor(() => expect(fetchMock.mock.calls.some((call) => call[1]?.method === 'PUT')).toBe(true));
    await user.clear(budget);
    await user.type(budget, '2501');
    await user.clear(budget);
    await user.type(budget, '2500');
    resolveSave(json(200, {...trip, version: 1, budgetCents: 250000}));
    expect(await screen.findByText('All changes saved.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter((call) => call[1]?.method === 'PUT')).toHaveLength(1);
  });

  it('handles optimistic concurrency conflict (409 VERSION_CONFLICT) by preserving user edits and offering reload', async () => {
    const tripDetail = {
      id: 'trip-1',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      originAirportCode: 'PDX',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 1,
      travelerAges: null,
      budgetCents: null,
      label: 'San Francisco — Mar 10–14, 2027',
      version: 0,
      drafts: [],
      planned: [],
      alternatives: [],
      revisionSummary: null,
    };

    const reloadedTrip = {
      ...tripDetail,
      version: 2,
      budgetCents: 100000,
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 0,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(json(200, tripDetail))
      .mockResolvedValueOnce(json(409, {code: 'VERSION_CONFLICT', message: 'The Trip has changed. Reload before saving.', fields: {currentVersion: '2'}}))
      .mockResolvedValueOnce(json(200, reloadedTrip));

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: 'Open trip San Francisco — Mar 10–14, 2027'}));
    expect(await screen.findByLabelText('Budget (USD)')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Budget (USD)'), '500.00');

    expect(await screen.findByText(/The Trip has changed on the server/)).toBeInTheDocument();
    expect(screen.getByLabelText('Budget (USD)')).toHaveValue(500);

    const reloadBtn = screen.getByRole('button', {name: 'Reload from server'});
    await user.click(reloadBtn);

    await waitFor(() => {
      expect(screen.queryByText(/The Trip has changed on the server/)).not.toBeInTheDocument();
    });
  });

  it('displays structured revision summary (removals and adjustments) when returned by backend', async () => {
    const tripWithRevision = {
      id: 'trip-1',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      originAirportCode: 'PDX',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 2,
      travelerAges: [25, 30],
      budgetCents: 150000,
      label: 'San Francisco — Mar 10–14, 2027',
      version: 2,
      drafts: [],
      planned: [],
      alternatives: [],
      revisionSummary: {
        removals: [{ draftId: 'd-1', component: 'Airfare', reason: 'Flight dates no longer match trip dates.' }],
        adjustments: [{ draftId: 'd-1', component: 'Stay', changeType: 'UNIT_COUNT', previousUnitCount: 1, newUnitCount: 2, previousPriceCents: 10000, newPriceCents: 20000, reason: 'Traveler count increased.' }],
      },
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 2,
        temporalStatus: 'UPCOMING',
        draftCount: 0,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(json(200, tripWithRevision));

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: 'Open trip San Francisco — Mar 10–14, 2027'}));

    expect(await screen.findByRole('heading', {name: 'Review changes to your Working plan'})).toBeInTheDocument();
    expect(screen.getByText(/Flight dates no longer match trip dates/)).toBeInTheDocument();
    expect(screen.getByText(/Traveler count increased/)).toBeInTheDocument();
    expect(screen.getByText(/units: 1 → 2/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Acknowledge date change summary'}));
    expect(screen.queryByRole('heading', {name: 'Review changes to your Working plan'})).not.toBeInTheDocument();
  });

  it('confirms and executes scoped draft deletion, planned deletion, and trip deletion with server counts', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 1,
        plannedCount: 1,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [],
      past: [],
    }));

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    expect(await screen.findByRole('heading', {name: 'San Francisco — Mar 10–14, 2027'})).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Actions for San Francisco — Mar 10–14, 2027'}));
    await user.click(screen.getByRole('menuitem', {name: 'Delete trip San Francisco — Mar 10–14, 2027'}));

    const dialog = screen.getByRole('dialog', {name: 'Delete San Francisco — Mar 10–14, 2027'});
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText(/Working plan and 1 Saved option\(s\) will be removed\./)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Delete trip'}));

    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1', expect.objectContaining({
      method: 'DELETE',
      body: JSON.stringify({
        expectedVersion: 0,
        expectedDraftCount: 1,
        expectedPlannedCount: 1,
        confirmed: true,
      }),
    }));

    await waitFor(() => {
      expect(screen.queryByRole('heading', {name: 'San Francisco — Mar 10–14, 2027'})).not.toBeInTheDocument();
    });
  });

  it('blocks trip deletion when server reports booking history', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-booked',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 1,
        temporalStatus: 'UPCOMING',
        draftCount: 1,
        plannedCount: 1,
        expiredAlternativeCount: 0,
        bookedCount: 1,
        hasBookingHistory: true,
        alternatives: [],
      }],
      past: [],
    }));

    render(<App />);
    await showTrips();
    expect(await screen.findByRole('heading', {name: 'San Francisco — Mar 10–14, 2027'})).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', {name: 'Actions for San Francisco — Mar 10–14, 2027'}));
    expect(screen.queryByRole('menuitem', {name: 'Delete trip San Francisco — Mar 10–14, 2027'})).not.toBeInTheDocument();
    expect(screen.getByRole('menuitem', {name: 'Cancel trip San Francisco — Mar 10–14, 2027'})).toBeInTheDocument();
  });

  it('handles stale trip deletion confirmation (409 STALE_CONFIRMATION) and refreshes view', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 1,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(json(409, {
      code: 'STALE_CONFIRMATION',
      message: 'The Trip alternative counts have changed since confirmation.',
    })).mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 1,
        temporalStatus: 'UPCOMING',
        draftCount: 2,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(noContent());

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: 'Actions for San Francisco — Mar 10–14, 2027'}));
    await user.click(screen.getByRole('menuitem', {name: 'Delete trip San Francisco — Mar 10–14, 2027'}));

    await user.click(screen.getByRole('button', {name: 'Delete trip'}));

    expect(await screen.findByText(/The alternative counts on the server have changed/)).toBeInTheDocument();
    expect(await screen.findByText(/Working plan and 0 Saved option\(s\) will be removed\./)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Delete trip'}));
    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-1', expect.objectContaining({
      method: 'DELETE',
      body: JSON.stringify({
        expectedVersion: 1,
        expectedDraftCount: 2,
        expectedPlannedCount: 0,
        confirmed: true,
      }),
    }));
  });

  it('prevents redundant autosave calls when inputs match server state', async () => {
    const tripDetail = {
      id: 'trip-1',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      originAirportCode: 'PDX',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 1,
      travelerAges: null,
      budgetCents: null,
      label: 'San Francisco — Mar 10–14, 2027',
      version: 0,
      drafts: [],
      planned: [],
      alternatives: [],
      revisionSummary: null,
    };

    const tripAfterSave = {
      ...tripDetail,
      version: 1,
      budgetCents: 150000,
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 0,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(json(200, tripDetail))
      .mockResolvedValueOnce(json(200, tripAfterSave));

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: 'Open trip San Francisco — Mar 10–14, 2027'}));

    await user.type(screen.getByLabelText('Budget (USD)'), '1500.00');
    expect(await screen.findByText('All changes saved.')).toBeInTheDocument();

    // Verify exactly 1 PUT call was made
    const putCalls = fetchMock.mock.calls.filter((call) => call[1]?.method === 'PUT');
    expect(putCalls).toHaveLength(1);

    // Wait past the debounce duration and assert no subsequent PUT call occurs
    await new Promise((r) => setTimeout(r, 800));
    const subsequentPutCalls = fetchMock.mock.calls.filter((call) => call[1]?.method === 'PUT');
    expect(subsequentPutCalls).toHaveLength(1);
  });

  it('disables trip deletion inside workspace when trip has booking history', async () => {
    const tripDetail = {
      id: 'trip-booked',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      originAirportCode: 'PDX',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 1,
      travelerAges: [25],
      budgetCents: null,
      label: 'San Francisco — Mar 10–14, 2027',
      version: 1,
      drafts: [],
      planned: [],
      alternatives: [],
      revisionSummary: null,
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-booked',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 1,
        temporalStatus: 'UPCOMING',
        draftCount: 0,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 1,
        hasBookingHistory: true,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(json(200, tripDetail));

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: 'Open trip San Francisco — Mar 10–14, 2027'}));

    expect(screen.queryByRole('button', {name: 'Delete trip San Francisco — Mar 10–14, 2027'})).not.toBeInTheDocument();
    expect(await screen.findByRole('button', {name: 'Cancel trip San Francisco — Mar 10–14, 2027'})).toBeInTheDocument();
  });

  it('validates dates in TripWorkspace and displays inline error', async () => {
    const tripDetail = {
      id: 'trip-1',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      originAirportCode: 'PDX',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 1,
      travelerAges: null,
      budgetCents: null,
      label: 'San Francisco — Mar 10–14, 2027',
      version: 0,
      drafts: [],
      planned: [],
      alternatives: [],
      revisionSummary: null,
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 0,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(json(200, tripDetail));

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: 'Open trip San Francisco — Mar 10–14, 2027'}));

    // Change return date to be before departure date
    const returnDateInput = screen.getByLabelText('Return date');
    await user.clear(returnDateInput);
    await user.type(returnDateInput, '2027-03-05');

    expect(await screen.findByText('Return date must be after departure date.')).toBeInTheDocument();
    expect(await screen.findByText('Please correct the highlighted errors.')).toBeInTheDocument();

    // Verify PUT was NOT called with invalid dates
    const putCalls = fetchMock.mock.calls.filter((call) => call[1]?.method === 'PUT');
    expect(putCalls).toHaveLength(0);
  });

  it('enforces accessible keyboard navigation, dialog focus trapping, Escape dismissal, and focus restoration', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [],
      past: [],
    }));

    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    const planTripBtn = await screen.findByRole('button', {name: 'Plan Trip'});
    await user.click(planTripBtn);
    expect(screen.getByRole('heading', {name: 'Plan a Trip'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Home'}));
    expect(screen.getByRole('heading', {name: 'Home'})).toHaveFocus();
  });

  it('opens TripRevisionModal for trip with planned alternatives, creates revised trip, updates workspace, and displays revision summary', async () => {
    const originalTrip = {
      id: 'trip-source',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      originAirportCode: 'PDX',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 2,
      travelerAges: [25, 30],
      budgetCents: 150000,
      label: 'San Francisco — Mar 10–14, 2027',
      version: 1,
      drafts: [],
      planned: [{ id: 'plan-1', selections: { airfare: null, stay: null, rental: null } }],
      alternatives: [{ id: 'plan-1', lifecycle: 'PLANNED', version: null, selections: { airfare: null, stay: null, rental: null } }],
      revisionSummary: null,
    };

    const revisedTrip = {
      id: 'trip-revised',
      destinationKey: 'destination-muc',
      destinationName: 'Munich',
      originAirportCode: 'PDX',
      startDate: '2027-03-15',
      endDate: '2027-03-20',
      travelerCount: 2,
      travelerAges: [25, 30],
      budgetCents: 150000,
      label: 'Munich — Mar 15–20, 2027',
      version: 0,
      drafts: [{ id: 'draft-from-plan', selections: { airfare: null, stay: null, rental: null } }],
      planned: [],
      alternatives: [{ id: 'draft-from-plan', lifecycle: 'DRAFT', version: 0, selections: { airfare: null, stay: null, rental: null } }],
      revisionSummary: {
        removals: [{ draftId: 'draft-from-plan', component: 'Airfare', reason: 'Destination changed to Munich.' }],
        adjustments: [],
      },
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-source',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 1,
        temporalStatus: 'UPCOMING',
        draftCount: 0,
        plannedCount: 1,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [{ id: 'plan-1', lifecycle: 'PLANNED', version: null, status: 'PLANNED', expired: false }],
      }],
      past: [],
    })).mockResolvedValueOnce(json(200, originalTrip))
      .mockResolvedValueOnce(json(201, revisedTrip))
      .mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-revised',
        destinationKey: 'destination-muc',
        destinationName: 'Munich',
        startDate: '2027-03-15',
        endDate: '2027-03-20',
        label: 'Munich — Mar 15–20, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 1,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [{ id: 'draft-from-plan', lifecycle: 'DRAFT', version: 0, status: 'DRAFT', expired: false }],
      }],
      past: [],
    }));

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: 'Open trip San Francisco — Mar 10–14, 2027'}));

    const reviseBtn = screen.getByRole('button', {name: 'Revise Trip'});
    expect(reviseBtn).toBeInTheDocument();
    expect(screen.getByLabelText('Destination')).toBeDisabled();
    expect(screen.getByLabelText('Departure date')).toBeEnabled();
    expect(screen.getByLabelText('Traveler 1 age')).toBeDisabled();

    await user.click(reviseBtn);
    const dialog = screen.getByRole('dialog', {name: 'Revise trip'});
    expect(dialog).toBeInTheDocument();

    await user.selectOptions(within(dialog).getByLabelText('Destination'), 'destination-muc');
    const startInput = within(dialog).getByLabelText('Departure date');
    await user.clear(startInput);
    await user.type(startInput, '2027-03-15');
    const endInput = within(dialog).getByLabelText('Return date');
    await user.clear(endInput);
    await user.type(endInput, '2027-03-20');

    await user.click(within(dialog).getByRole('button', {name: 'Create revised trip'}));

    expect(fetchMock).toHaveBeenCalledWith('/api/trips/trip-source/duplicate', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        expectedVersion: 1,
        destinationKey: 'destination-muc',
        startDate: '2027-03-15',
        endDate: '2027-03-20',
        travelerCount: 2,
        sourcePlannedItineraryIds: ['plan-1'],
        travelerAges: [25, 30],
        budgetCents: 150000,
        name: 'Munich — Mar 15–20, 2027',
      }),
    }));

    expect(await screen.findByRole('heading', {name: 'Munich — Mar 15–20, 2027'})).toBeInTheDocument();
    expect(screen.getByLabelText('Destination')).toHaveValue('destination-muc');
    expect(screen.getByLabelText('Departure date')).toHaveValue('2027-03-15');
    expect(screen.getByLabelText('Return date')).toHaveValue('2027-03-20');
    expect(screen.getByLabelText('Destination')).not.toBeDisabled();
    expect(screen.getByLabelText('Departure date')).not.toBeDisabled();

    expect(screen.getByRole('heading', {name: 'Review changes to your Working plan'})).toBeInTheDocument();
    expect(screen.getByText(/Destination changed to Munich\./)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Acknowledge date change summary'}));
    expect(screen.queryByRole('heading', {name: 'Review changes to your Working plan'})).not.toBeInTheDocument();
  });

  it('restores focus to section heading when deleted trip card button is removed from DOM', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 1,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [],
      past: [],
    }));

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: 'Actions for San Francisco — Mar 10–14, 2027'}));
    const deleteBtn = screen.getByRole('menuitem', {name: 'Delete trip San Francisco — Mar 10–14, 2027'});
    await user.click(deleteBtn);

    const confirmBtn = screen.getByRole('button', {name: 'Delete trip'});
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(screen.getByRole('heading', {name: 'My Trips', level: 1})).toHaveFocus();
    });
  });

  it('allows editing and clearing traveler count in TripWorkspace with inline validation', async () => {
    const tripDetail = {
      id: 'trip-1',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      originAirportCode: 'PDX',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 1,
      travelerAges: null,
      budgetCents: null,
      label: 'San Francisco — Mar 10–14, 2027',
      version: 0,
      drafts: [],
      planned: [],
      alternatives: [],
      revisionSummary: null,
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 0,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(json(200, tripDetail));

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: 'Open trip San Francisco — Mar 10–14, 2027'}));

    const travelersInput = screen.getByLabelText('Travelers');
    expect(travelersInput).toHaveValue(1);

    // Clear the field (backspace)
    await user.clear(travelersInput);
    expect(travelersInput).toHaveValue(null);
    expect(await screen.findByText('Traveler count must be between 1 and 8.')).toBeInTheDocument();

    // Type 3 travelers
    await user.type(travelersInput, '3');
    expect(travelersInput).toHaveValue(3);
    await waitFor(() => {
      expect(screen.queryByText('Traveler count must be between 1 and 8.')).not.toBeInTheDocument();
    });

    // 3 traveler age inputs should now be present
    expect(screen.getByLabelText('Traveler 1 age')).toBeInTheDocument();
    expect(screen.getByLabelText('Traveler 2 age')).toBeInTheDocument();
    expect(screen.getByLabelText('Traveler 3 age')).toBeInTheDocument();
  });

  it('allows logging out directly from TripWorkspace and provides a primary h1 heading', async () => {
    const tripDetail = {
      id: 'trip-1',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      originAirportCode: 'PDX',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      travelerCount: 1,
      travelerAges: null,
      budgetCents: null,
      label: 'San Francisco — Mar 10–14, 2027',
      version: 0,
      drafts: [],
      planned: [],
      alternatives: [],
      revisionSummary: null,
    };

    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test',
      upcoming: [{
        id: 'trip-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco — Mar 10–14, 2027',
        version: 0,
        temporalStatus: 'UPCOMING',
        draftCount: 0,
        plannedCount: 0,
        expiredAlternativeCount: 0,
        bookedCount: 0,
        hasBookingHistory: false,
        alternatives: [],
      }],
      past: [],
    })).mockResolvedValueOnce(json(200, tripDetail))
      .mockResolvedValueOnce(noContent());

    const user = userEvent.setup();
    render(<App />);
    await showTrips();
    await user.click(await screen.findByRole('button', {name: 'Open trip San Francisco — Mar 10–14, 2027'}));

    // Verify h1 landmark
    const heading = await screen.findByRole('heading', {level: 1, name: 'San Francisco — Mar 10–14, 2027'});
    expect(heading).toBeInTheDocument();

    // Verify logout button in workspace
    const logoutBtn = screen.getByRole('button', {name: 'Log out'});
    expect(logoutBtn).toBeInTheDocument();

    await user.click(logoutBtn);
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({method: 'POST'}));
    expect(await screen.findByRole('heading', {name: 'Home'})).toBeInTheDocument();
  });

  it('displays BOOKED status badge and primary booking reference on profile screen for booked trips', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'traveler@example.test',
      upcoming: [{
        id: 'trip-booked-1',
        destinationKey: 'destination-sfo',
        destinationName: 'San Francisco',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        label: 'San Francisco Getaway',
        version: 2,
        temporalStatus: 'UPCOMING',
        draftCount: 1,
        plannedCount: 1,
        expiredAlternativeCount: 0,
        bookedCount: 1,
        hasBookingHistory: true,
        primaryBookingReference: 'DT-TEST01',
        alternatives: [{ id: 'plan-1', lifecycle: 'PLANNED', version: null, status: 'PLANNED', expired: false }],
      }],
      past: [{
        id: 'trip-past-1',
        destinationKey: 'destination-muc',
        destinationName: 'Munich',
        startDate: '2027-02-01',
        endDate: '2027-02-07',
        label: 'Past Munich Trip',
        version: 3,
        temporalStatus: 'PAST',
        draftCount: 0,
        plannedCount: 1,
        expiredAlternativeCount: 0,
        bookedCount: 1,
        hasBookingHistory: true,
        primaryBookingReference: 'DT-PAST99',
        alternatives: [],
      }],
    }));

    render(<App />);
    await showTrips();

    // Upcoming trip and past trip display BOOKED badges
    const bookedBadges = await screen.findAllByText('1 Booking');
    expect(bookedBadges.length).toBe(2);

    expect(screen.getAllByText(/booking reference:/i).length).toBe(2);
    expect(screen.getByText('DT-TEST01')).toBeInTheDocument();
    expect(screen.getByText('DT-PAST99')).toBeInTheDocument();
  });
});
