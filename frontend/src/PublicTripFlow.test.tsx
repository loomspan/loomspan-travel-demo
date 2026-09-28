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
  const planNewTrip = screen.queryByRole('button', {name: 'Plan a new trip'});
  if (planNewTrip) await user.click(planNewTrip);
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
  beforeEach(() => { vi.stubGlobal('fetch', fetchMock); document.cookie = 'XSRF-TOKEN=token; path=/'; window.history.replaceState({}, '', '/'); });
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

  it('searches and selects a stay before login, then saves after explicit confirmation', async () => {
    const stay = {accommodationUnitId: 17, propertyId: 2, propertyCatalogKey: 'hotel', unitCatalogKey: 'room', propertyName: 'Central Hotel', unitName: 'Double room', propertyCategory: 'HOTEL', unitKind: 'ROOM', locationDescription: 'Center', guestRating: 4.5, distanceToCityCenterMeters: 1000, latitude: 0, longitude: 0, guestCapacity: 2, inventoryCapacity: 4, pricing: {requiredRooms: 1, nightCount: 4, perRoomTotalPriceCents: 40000, totalPriceCents: 40000, nights: []}, fitsBudget: null};
    const created = {...trip, drafts: [{id: 'draft-1', version: 0, selections: {airfare: null, stay: null, rental: null}}]};
    fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
      if (url === '/api/profile') return json(fetchMock.mock.calls.some(([path]) => path === '/api/auth/login') ? 200 : 401,
        fetchMock.mock.calls.some(([path]) => path === '/api/auth/login') ? {email: 'ada@example.test', upcoming: [], past: []} : {code: 'UNAUTHENTICATED'});
      if (url.startsWith('/api/public/stays')) return json(200, {options: [stay]});
      if (url.startsWith('/api/public/airfare')) return json(200, {options: []});
      if (url === '/api/auth/login') return noContent();
      if (url === '/api/trips' && options?.method === 'POST') return json(201, created);
      if (url === '/api/trips/trip-1/drafts/draft-1/stays') return json(200, {...created, version: 1, drafts: [{...created.drafts[0], version: 1, selections: {airfare: null, stay: {accommodationUnitId: 17}, rental: null}}]});
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<App />);
    await fillTrip(user);
    await user.click(screen.getByRole('button', {name: 'Search options'}));
    await user.click(screen.getByRole('tab', {name: 'Search stays'}));
    await user.click(await screen.findByRole('button', {name: 'Select stay'}));
    expect(screen.getByText('Selected stay')).toBeInTheDocument();
    expect(screen.getByRole('tab', {name: 'Search stays'})).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('button', {name: 'Selected'})).toBeDisabled();
    const selection = screen.getByText('Selected stay').closest('article')!;
    const save = screen.getByRole('button', {name: 'Save selections'});
    const compare = screen.getByRole('heading', {name: 'Compare more options'});
    expect(selection.compareDocumentPosition(save) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(save.compareDocumentPosition(compare) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(0);
    await user.click(screen.getByRole('button', {name: 'Home'}));
    expect(screen.getByRole('dialog', {name: 'Save your selections?'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Keep planning'}));
    expect(screen.getByText('Selected stay')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Save selections'}));
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(1));
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips/trip-1/drafts/draft-1/stays')).toHaveLength(1));
  });

  it('keeps a selected flight prominent above searchable options', async () => {
    const flight = {
      combinationKey: 'meridian-round-trip',
      outbound: {carrier: 'Meridian Air', flightNumber: 'MA118', originAirportCode: 'PDX', destinationAirportCode: 'MUC',
        departureTime: '2027-03-10T21:40:00Z', arrivalTime: '2027-03-11T23:45:00Z', departureTimeZone: 'America/Los_Angeles',
        arrivalTimeZone: 'Europe/Berlin', durationMinutes: 845, stopCount: 1},
      returnFlight: {carrier: 'Meridian Air', flightNumber: 'MA119', originAirportCode: 'MUC', destinationAirportCode: 'PDX',
        departureTime: '2027-03-14T15:20:00Z', arrivalTime: '2027-03-15T03:20:00Z', departureTimeZone: 'Europe/Berlin',
        arrivalTimeZone: 'America/Los_Angeles', durationMinutes: 780, stopCount: 1},
      pricing: {travelerCount: 2, partyTotalPriceCents: 68200},
    };
    fetchMock.mockImplementation(async (url: string) => {
      if (url === '/api/profile') return json(401, {code: 'UNAUTHENTICATED'});
      if (url.startsWith('/api/public/airfare')) return json(200, {options: [flight]});
      if (url.startsWith('/api/public/stays')) return json(200, {options: []});
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<App />);
    await fillTrip(user);
    await user.click(screen.getByRole('button', {name: 'Search options'}));
    await user.click(await screen.findByRole('button', {name: 'Select flight'}));
    const selection = screen.getByText('Selected flight').closest('article')!;
    expect(selection).toHaveTextContent('MA118');
    expect(selection).toHaveTextContent('MA119');
    expect(selection).toHaveTextContent('$682.00');
    expect(screen.getByRole('button', {name: 'Selected'})).toBeDisabled();
    expect(selection.compareDocumentPosition(screen.getByRole('button', {name: 'Save selections'})) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    await user.click(screen.getByRole('tab', {name: 'Search stays'}));
    expect(screen.getByText('Selected flight')).toBeInTheDocument();
    expect(screen.getByRole('tabpanel', {name: 'Search stays'})).toBeVisible();
  });

  it('discards an unsaved selection when the guest chooses not to save on navigation', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url === '/api/profile') return json(401, {code: 'UNAUTHENTICATED'});
      if (url.startsWith('/api/public/stays')) return json(200, {options: [{accommodationUnitId: 17, propertyName: 'Central Hotel', unitName: 'Double room', guestRating: 4.5, distanceToCityCenterMeters: 1000, pricing: {requiredRooms: 1, nightCount: 4, perRoomTotalPriceCents: 40000, totalPriceCents: 40000}}]});
      if (url.startsWith('/api/public/airfare')) return json(200, {options: []});
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<App />);
    await fillTrip(user);
    await user.click(screen.getByRole('button', {name: 'Search options'}));
    await user.click(screen.getByRole('tab', {name: 'Search stays'}));
    await user.click(await screen.findByRole('button', {name: 'Select stay'}));
    await user.click(screen.getByRole('button', {name: 'Home'}));
    await user.click(screen.getByRole('button', {name: 'No, discard'}));
    expect(screen.getByRole('heading', {name: 'Home'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Trips'}));
    expect(screen.getByLabelText('Trip name')).toHaveValue('San Francisco trip');
    expect(screen.queryByText('Selected stay')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(0);
  });

  it('offers login from the leave prompt and saves the selection afterward', async () => {
    const stay = {accommodationUnitId: 17, propertyName: 'Central Hotel', unitName: 'Double room', guestRating: 4.5, distanceToCityCenterMeters: 1000, pricing: {requiredRooms: 1, nightCount: 4, perRoomTotalPriceCents: 40000, totalPriceCents: 40000}};
    const created = {...trip, drafts: [{id: 'draft-1', version: 0, selections: {airfare: null, stay: null, rental: null}}]};
    fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
      if (url === '/api/profile') return fetchMock.mock.calls.some(([path]) => path === '/api/auth/login')
        ? json(200, {email: 'ada@example.test', upcoming: [], past: []}) : json(401, {code: 'UNAUTHENTICATED'});
      if (url.startsWith('/api/public/stays')) return json(200, {options: [stay]});
      if (url.startsWith('/api/public/airfare')) return json(200, {options: []});
      if (url === '/api/auth/login') return noContent();
      if (url === '/api/trips' && options?.method === 'POST') return json(201, created);
      if (url === '/api/trips/trip-1/drafts/draft-1/stays') return json(200, {...created, version: 1, drafts: [{...created.drafts[0], version: 1, selections: {airfare: null, stay: {accommodationUnitId: 17}, rental: null}}]});
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<App />);
    await fillTrip(user);
    await user.click(screen.getByRole('button', {name: 'Search options'}));
    await user.click(screen.getByRole('tab', {name: 'Search stays'}));
    await user.click(await screen.findByRole('button', {name: 'Select stay'}));
    await user.click(screen.getByRole('button', {name: 'Home'}));
    await user.click(screen.getByRole('button', {name: 'Yes, save'}));
    expect(screen.getByRole('heading', {name: 'Welcome back'})).toBeInTheDocument();
    await user.type(screen.getByLabelText('Email address'), 'ada@example.test');
    await user.type(screen.getByLabelText('Password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', {name: 'Log in'}));
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips/trip-1/drafts/draft-1/stays')).toHaveLength(1));
    expect(await screen.findByRole('heading', {name: 'Home'})).toBeInTheDocument();
  });

  it('requires every traveler age before saving', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    await fillTrip(user);
    await user.clear(screen.getByLabelText('Traveler 2 age'));
    await user.click(screen.getByRole('button', {name: 'Start planning'}));
    expect(screen.getByText('Select an age from under 1 through 95 for each traveler.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/trips')).toHaveLength(0);
  });

  it('suggests an editable name and binds invalid dates, ages, and budget to fields', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    const user = userEvent.setup(); render(<App />);
    await screen.findByRole('button', {name: 'Profile'});
    await user.click(screen.getByRole('button', {name: 'Trips'}));
    await user.click(screen.getByRole('button', {name: 'Plan a new trip'}));
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
    expect(await screen.findByRole('heading', {name: 'Your trips'})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: `Trip: ${trip.label}`})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Retry save'})).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(1);
  });
});
