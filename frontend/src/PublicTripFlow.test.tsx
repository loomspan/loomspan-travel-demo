import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json'}});
const noContent = () => new Response(null, {status: 204});
const trip = {id: 'trip-1', destinationKey: 'destination-muc', destinationName: 'Munich', originAirportCode: 'PDX',
  startDate: '2027-03-10', endDate: '2027-03-14', travelerCount: 2, travelerAges: [30, 12],
  budgetCents: null, label: 'Munich — Mar 10–14, 2027', version: 0, drafts: [], planned: [], alternatives: [], revisionSummary: null};
const ownedProfile = {email: 'ada@example.test', upcoming: [{id: trip.id, label: trip.label, destinationKey: trip.destinationKey,
  destinationName: trip.destinationName, startDate: trip.startDate, endDate: trip.endDate, version: 0,
  temporalStatus: 'UPCOMING', draftCount: 0, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0,
  hasBookingHistory: false, alternatives: []}], past: []};

async function fillTrip(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', {name: 'Trips'}));
  await user.clear(screen.getByLabelText('Trip name'));
  await user.type(screen.getByLabelText('Trip name'), 'Family visit');
  await user.selectOptions(screen.getByLabelText('Destination'), 'destination-muc');
  await user.type(screen.getByLabelText('Departure date'), '2027-03-10');
  await user.type(screen.getByLabelText('Return date'), '2027-03-14');
  await user.clear(screen.getByLabelText('Travelers'));
  await user.type(screen.getByLabelText('Travelers'), '2');
  await user.type(screen.getByLabelText('Traveler 1 age'), '30');
  await user.type(screen.getByLabelText('Traveler 2 age'), '12');
}

describe('public trip start and authentication handoff', () => {
  const fetchMock = vi.fn();
  beforeEach(() => { vi.stubGlobal('fetch', fetchMock); document.cookie = 'XSRF-TOKEN=token; path=/'; });
  afterEach(() => { vi.unstubAllGlobals(); fetchMock.mockReset(); document.cookie = 'XSRF-TOKEN=; max-age=0; path=/'; });

  it('shows Home immediately, and guest Trips only holds local form data', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const localSpy = vi.spyOn(window.localStorage, 'setItem');
    const sessionSpy = vi.spyOn(window.sessionStorage, 'setItem');
    const user = userEvent.setup(); render(<App />);
    expect(screen.getByRole('heading', {name: 'Home'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Log in'})).toBeInTheDocument();
    await fillTrip(user);
    expect(screen.getByText(/account is required/)).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/profile', expect.anything()));
    expect(fetchMock.mock.calls.filter(([url]) => String(url).startsWith('/api/trips'))).toHaveLength(0);
    expect(localSpy).not.toHaveBeenCalled(); expect(sessionSpy).not.toHaveBeenCalled();
    localSpy.mockRestore(); sessionSpy.mockRestore();
  });

  it('keeps all fields through cancel and registration, then creates once on explicit continuation', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const user = userEvent.setup(); render(<App />);
    await fillTrip(user);
    await user.type(screen.getByLabelText('Budget (optional, dollars)'), '19.99');
    await user.clear(screen.getByLabelText('Travelers'));
    await user.type(screen.getByLabelText('Travelers'), '1');
    await user.clear(screen.getByLabelText('Travelers'));
    await user.type(screen.getByLabelText('Travelers'), '2');
    expect(screen.getByLabelText('Traveler 2 age')).toHaveValue(12);
    await user.click(screen.getByRole('button', {name: 'Start planning'}));
    expect(await screen.findByRole('heading', {name: 'Welcome back'})).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(0);
    await user.click(screen.getByRole('button', {name: 'Cancel'}));
    expect(screen.getByLabelText('Trip name')).toHaveValue('Family visit');
    expect(screen.getByLabelText('Traveler 2 age')).toHaveValue(12);
    expect(screen.getByLabelText('Budget (optional, dollars)')).toHaveValue(19.99);
    await user.click(screen.getByRole('button', {name: 'Start planning'}));
    await user.click(screen.getByRole('button', {name: 'Register'}));
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    fetchMock.mockResolvedValueOnce(json(201, {email: 'ada@example.test'}))
      .mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    await user.click(screen.getByRole('button', {name: 'Create account'}));
    expect(await screen.findByRole('heading', {name: 'Trips'})).toBeInTheDocument();
    expect(screen.getByLabelText('Trip name')).toHaveValue('Family visit');
    expect(screen.getByLabelText('Destination')).toHaveValue('destination-muc');
    expect(screen.getByLabelText('Departure date')).toHaveValue('2027-03-10');
    expect(screen.getByLabelText('Return date')).toHaveValue('2027-03-14');
    expect(screen.getByLabelText('Travelers')).toHaveValue(2);
    expect(screen.getByLabelText('Traveler 1 age')).toHaveValue(30);
    expect(screen.getByLabelText('Traveler 2 age')).toHaveValue(12);
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(0);
    fetchMock.mockResolvedValueOnce(json(201, trip)).mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    await user.click(screen.getByRole('button', {name: 'Start planning'}));
    await screen.findByRole('heading', {name: trip.label});
    const writes = fetchMock.mock.calls.filter(([url, options]) => url === '/api/trips' && options?.method === 'POST');
    expect(writes).toHaveLength(1);
    expect(JSON.parse(writes[0][1].body)).toEqual({name: 'Family visit', destinationKey: 'destination-muc', startDate: '2027-03-10', endDate: '2027-03-14', travelerCount: 2, travelerAges: [30, 12], budgetCents: 1999});
  });

  it('requires every traveler age before saving', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    await fillTrip(user);
    await user.clear(screen.getByLabelText('Traveler 2 age'));
    await user.click(screen.getByRole('button', {name: 'Start planning'}));
    expect(screen.getByText('Enter an age between 0 and 120 for each traveler.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(0);
  });

  it('suggests an editable name and binds invalid dates, ages, and budget to fields', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    await user.click(screen.getByRole('button', {name: 'Trips'}));
    expect(screen.getByLabelText('Trip name')).toHaveValue('San Francisco trip');
    await user.clear(screen.getByLabelText('Trip name'));
    await user.type(screen.getByLabelText('Trip name'), 'My spring trip');
    await user.type(screen.getByLabelText('Budget (optional, dollars)'), '1000001');
    await user.click(screen.getByRole('button', {name: 'Start planning'}));
    expect(screen.getByLabelText('Trip name')).toHaveValue('My spring trip');
    for (const label of ['Departure date', 'Return date', 'Traveler 1 age', 'Budget (optional, dollars)']) {
      const input = screen.getByLabelText(label);
      expect(input).toHaveAttribute('aria-invalid', 'true');
      expect(document.getElementById(input.getAttribute('aria-describedby')!)).toHaveClass('field-error');
    }
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(0);
  });

  it('returns direct login to Home and never reads Trips before authentication', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const user = userEvent.setup(); render(<App />);
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await user.click(screen.getByRole('button', {name: 'Cancel'}));
    expect(screen.getByRole('heading', {name: 'Home'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    fetchMock.mockResolvedValueOnce(noContent()).mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    expect(await screen.findByRole('heading', {name: 'Home'})).toBeInTheDocument();
    expect(await screen.findByRole('button', {name: 'Profile'})).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => String(url).startsWith('/api/trips'))).toHaveLength(0);
  });

  it('retains a failed create and requires login and another Start planning click', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    await fillTrip(user);
    fetchMock.mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    await user.click(screen.getByRole('button', {name: 'Start planning'}));
    expect(await screen.findByRole('heading', {name: 'Welcome back'})).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(1);
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    fetchMock.mockResolvedValueOnce(noContent()).mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    expect(await screen.findByText(/Your Trip was not saved/)).toBeInTheDocument();
    expect(screen.getByLabelText('Trip name')).toHaveValue('Family visit');
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(1);
    fetchMock.mockResolvedValueOnce(json(201, trip)).mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    await user.click(screen.getByRole('button', {name: 'Start planning'}));
    await screen.findByRole('heading', {name: trip.label});
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(2);
  });

  it('disables duplicate submission while Trip creation is pending', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    await fillTrip(user);
    let completeCreate!: (response: Response) => void;
    fetchMock.mockImplementationOnce(() => new Promise<Response>(resolve => { completeCreate = resolve; }));
    await user.click(screen.getByRole('button', {name: 'Start planning'}));
    expect(screen.getByRole('button', {name: 'Starting…'})).toBeDisabled();
    await user.click(screen.getByRole('button', {name: 'Starting…'}));
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(1);
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    completeCreate(json(201, trip));
    await screen.findByRole('heading', {name: trip.label});
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(1);
  });

  it('keeps workspace edits through session expiry and saves only on same-account retry', async () => {
    fetchMock.mockResolvedValueOnce(json(200, ownedProfile))
      .mockResolvedValueOnce(json(200, ownedProfile))
      .mockResolvedValueOnce(json(200, trip))
      .mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    await user.click(screen.getByRole('button', {name: 'Trips'}));
    await user.click(await screen.findByRole('button', {name: `Open trip ${trip.label}`}));
    await user.type(screen.getByLabelText('Budget (USD)'), '2500');
    expect(await screen.findByRole('heading', {name: 'Welcome back'})).toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: trip.label})).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(1);
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    fetchMock.mockResolvedValueOnce(noContent()).mockResolvedValueOnce(json(200, ownedProfile));
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    expect(await screen.findByRole('button', {name: 'Retry save'})).toBeInTheDocument();
    expect(screen.getByLabelText('Budget (USD)')).toHaveValue(2500);
    expect(screen.queryByText('All changes saved.')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(1);
    fetchMock.mockResolvedValueOnce(json(200, {...trip, version: 1, budgetCents: 250000}));
    await user.click(screen.getByRole('button', {name: 'Retry save'}));
    expect(await screen.findByText('All changes saved.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(2);
  });

  it('clears the previous owner workspace when a different account logs in', async () => {
    fetchMock.mockResolvedValueOnce(json(200, ownedProfile))
      .mockResolvedValueOnce(json(200, ownedProfile))
      .mockResolvedValueOnce(json(200, trip))
      .mockResolvedValueOnce(json(401, {code: 'UNAUTHENTICATED'}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    await user.click(screen.getByRole('button', {name: 'Trips'}));
    await user.click(await screen.findByRole('button', {name: `Open trip ${trip.label}`}));
    await user.type(screen.getByLabelText('Budget (USD)'), '2500');
    await screen.findByRole('heading', {name: 'Welcome back'});
    await user.type(screen.getByLabelText('Email address'), 'bob@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    fetchMock.mockResolvedValueOnce(noContent()).mockResolvedValueOnce(json(200, {email: 'bob@example.test', upcoming: [], past: []}));
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    expect(await screen.findByRole('heading', {name: 'Home'})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: `Trip: ${trip.label}`})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Retry save'})).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(1);
  });
});
