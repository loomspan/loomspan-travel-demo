import {createRef, useState} from 'react';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {act, render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import {
  ItinerarySummaryTally,
  formatCents,
} from './components/ItinerarySummaryTally';
import {ConfirmRemoveModal} from './components/ConfirmRemoveModal';
import {formatLocalToDestinationIso} from './components/RentalSearchSection';
import {AirfareSearchSection} from './components/AirfareSearchSection';
import {TripWorkspace, type TripWorkspaceHandle} from './components/TripWorkspace';
import {tripsApi} from './api/tripsApi';
import type {TripResponse, DraftSelectionResponse} from './api/tripsApi';

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json'}});

function createMockTrip(overrides: Partial<TripResponse> = {}): TripResponse {
  const baseTrip: TripResponse = {
    id: 'trip-1',
    destinationKey: 'destination-sfo',
    destinationName: 'San Francisco',
    originAirportCode: 'PDX',
    startDate: '2027-03-10',
    endDate: '2027-03-14',
    travelerCount: 2,
    travelerAges: [30, 28],
    budgetCents: 200000,
    label: 'Trip to San Francisco',
    version: 0,
    drafts: [
      {
        id: 'draft-1',
        version: 0,
        selections: {airfare: null, stay: null, rental: null},
      },
    ],
    planned: [],
    alternatives: [
      {
        id: 'draft-1',
        lifecycle: 'DRAFT',
        version: 0,
        selections: {airfare: null, stay: null, rental: null},
      },
    ],
    revisionSummary: null,
  };
  return {...baseTrip, ...overrides};
}

describe('Progressive Trip Builder Experience', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    document.cookie = 'XSRF-TOKEN=secret-token; path=/';
    window.history.replaceState({}, '', '/profile');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; max-age=0; path=/';
    fetchMock.mockReset();
  });

  it('keeps the active Draft through Home and Profile and returns to the same saved tally', async () => {
    const user = userEvent.setup();
    const trip = createMockTrip();
    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test', upcoming: [{id: trip.id, label: trip.label,
        destinationKey: trip.destinationKey, destinationName: trip.destinationName,
        startDate: trip.startDate, endDate: trip.endDate, version: trip.version,
        temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0,
        expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []}], past: [],
    })).mockResolvedValueOnce(json(200, trip)).mockResolvedValueOnce(json(200, {
      email: 'ada@example.test', upcoming: [], past: [],
    })).mockResolvedValueOnce(json(200, trip));

    render(<App />);
    await user.click(await screen.findByRole('button', {name: `Open trip ${trip.label}`}));
    expect(await screen.findByRole('heading', {name: 'Progressive Trip Builder'})).toBeInTheDocument();
    const draftTotal = screen.getByRole('heading', {name: /Draft totals/});
    expect(draftTotal).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Home'}));
    expect(screen.getByRole('heading', {name: 'Home'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Profile'}));
    expect(screen.getByText('ada@example.test')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: `Trip: ${trip.label}`}));
    expect(screen.getByRole('heading', {name: 'Progressive Trip Builder'})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /Draft totals/})).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter((call) => ['POST', 'PUT', 'DELETE'].includes(call[1]?.method as string))).toHaveLength(0);
  });

  it('does not reopen a Trip after navigating Home during a slow open', async () => {
    const trip = createMockTrip();
    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test', upcoming: [{id: trip.id, label: trip.label,
        destinationKey: trip.destinationKey, destinationName: trip.destinationName,
        startDate: trip.startDate, endDate: trip.endDate, version: trip.version,
        temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0,
        expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []}], past: [],
    }));
    let resolveTrip!: (trip: TripResponse) => void;
    const pendingTrip = new Promise<TripResponse>((resolve) => { resolveTrip = resolve; });
    const getTrip = vi.spyOn(tripsApi, 'getTrip').mockReturnValueOnce(pendingTrip);
    render(<App />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', {name: `Open trip ${trip.label}`}));
    expect(screen.getByText('Opening Trip…')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Home'}));
    await act(async () => { resolveTrip(trip); await pendingTrip; });
    expect(screen.getByRole('heading', {name: 'Home'})).toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: 'Progressive Trip Builder'})).not.toBeInTheDocument();
    getTrip.mockRestore();
  });

  it('offers Plan Trip, Airfare, and Stay entry points on Home without creating a Draft on navigation', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValueOnce(json(200, {email: 'ada@example.test', upcoming: [], past: []}));
    render(<App />);
    await screen.findByText('ada@example.test');
    await user.click(screen.getByRole('button', {name: 'Home'}));
    for (const [name, title] of [['Plan Trip', /plan a new trip/i], ['Airfare', /plan a trip with airfare/i], ['Stay', /plan a trip with stay/i]] as const) {
      await user.click(screen.getByRole('button', {name}));
      expect(screen.getByRole('dialog', {name: title})).toBeInTheDocument();
      await user.click(screen.getByRole('button', {name: 'Cancel'}));
    }
    expect(fetchMock.mock.calls.filter((call) => ['POST', 'PUT', 'DELETE'].includes(call[1]?.method as string))).toHaveLength(0);
  });

  it('retries a failed flight search and shows an empty result', async () => {
    const user = userEvent.setup();
    fetchMock.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(json(200, {options: []}));
    render(<AirfareSearchSection trip={createMockTrip()} draftId="draft-1" onSelect={vi.fn()} onCancel={vi.fn()} pending={false} />);
    expect(await screen.findByRole('button', {name: 'Retry flight search'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Retry flight search'}));
    expect(await screen.findByText(/No flights/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('does not replace newer local edits with a stale Trip refresh response', async () => {
    let resolveRefresh!: (trip: TripResponse) => void;
    const refresh = new Promise<TripResponse>((resolve) => { resolveRefresh = resolve; });
    const getTrip = vi.spyOn(tripsApi, 'getTrip').mockReturnValueOnce(refresh);
    const ref = createRef<TripWorkspaceHandle>();
    render(<TripWorkspace ref={ref} initialTrip={createMockTrip()} onBack={() => {}} onTripDeleted={() => {}} />);
    let refreshResult!: Promise<void>;
    await act(async () => { refreshResult = ref.current!.refreshIfClean(); });
    await userEvent.setup().clear(screen.getByLabelText('Budget (USD)'));
    await userEvent.setup().type(screen.getByLabelText('Budget (USD)'), '2500');
    await act(async () => { resolveRefresh(createMockTrip({version: 1, budgetCents: 100000})); await refreshResult; });
    expect(screen.getByLabelText('Budget (USD)')).toHaveValue(2500);
    getTrip.mockRestore();
  });

  it('does not show a stale refresh failure after local edits begin', async () => {
    let rejectRefresh!: (error: Error) => void;
    const refresh = new Promise<TripResponse>((_resolve, reject) => { rejectRefresh = reject; });
    const getTrip = vi.spyOn(tripsApi, 'getTrip').mockReturnValueOnce(refresh);
    const ref = createRef<TripWorkspaceHandle>();
    render(<TripWorkspace ref={ref} initialTrip={createMockTrip()} onBack={() => {}} onTripDeleted={() => {}} />);
    let refreshResult!: Promise<void>;
    await act(async () => { refreshResult = ref.current!.refreshIfClean(); });
    await userEvent.setup().clear(screen.getByLabelText('Budget (USD)'));
    await userEvent.setup().type(screen.getByLabelText('Budget (USD)'), '2500');
    await act(async () => { rejectRefresh(new Error('offline')); await refreshResult; });
    expect(screen.getByLabelText('Budget (USD)')).toHaveValue(2500);
    expect(screen.queryByText('Could not refresh trip. Retry when you return.')).not.toBeInTheDocument();
    getTrip.mockRestore();
  });

  it('initiates trip creation through Airfare entry point and launches directly into flight search', async () => {
    const user = userEvent.setup();

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [],
        past: [],
      })
    );

    render(<App />);
    await screen.findByText('ada@example.test');

    const airfareBtn = screen.getByRole('button', {name: 'Airfare'});
    await user.click(airfareBtn);

    await screen.findByRole('dialog', {name: /plan a trip with airfare/i});

    await user.type(screen.getByLabelText('Departure date'), '2027-03-10');
    await user.type(screen.getByLabelText('Return date'), '2027-03-14');

    fetchMock.mockResolvedValueOnce(json(201, createMockTrip()));

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [{id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-10', endDate: '2027-03-14', label: 'Trip to San Francisco', version: 0, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: [{id: 'draft-1', lifecycle: 'DRAFT', version: 0, status: 'DRAFT', expired: false}]}],
        past: [],
      })
    );

    fetchMock.mockResolvedValueOnce(
      json(200, {
        tripId: 'trip-1',
        draftId: 'draft-1',
        destinationKey: 'destination-sfo',
        originAirportCode: 'PDX',
        destinationAirportCode: 'SFO',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        travelerCount: 2,
        directOnly: false,
        sort: 'DEFAULT',
        options: [],
      })
    );

    await user.click(screen.getByRole('button', {name: /create trip/i}));

    await screen.findByText('Trip to San Francisco');

    expect(await screen.findByRole('heading', {name: /search flights/i})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /add stay/i})).toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: /rental car|search rental cars/i})).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: /add a car/i})).toBeInTheDocument();

    const anotherTrip = createMockTrip({id: 'trip-2', label: 'Second trip'});
    fetchMock.mockResolvedValueOnce(json(200, {
      email: 'ada@example.test', upcoming: [
        {id: 'trip-1', label: 'Trip to San Francisco', destinationKey: 'destination-sfo', destinationName: 'San Francisco',
          startDate: '2027-03-10', endDate: '2027-03-14', version: 0, temporalStatus: 'UPCOMING', draftCount: 1,
          plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []},
        {id: anotherTrip.id, label: anotherTrip.label, destinationKey: anotherTrip.destinationKey,
          destinationName: anotherTrip.destinationName, startDate: anotherTrip.startDate, endDate: anotherTrip.endDate,
          version: anotherTrip.version, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0,
          expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []},
      ], past: [],
    })).mockResolvedValueOnce(json(200, anotherTrip));
    await user.click(screen.getByRole('button', {name: 'Profile'}));
    await user.click(await screen.findByRole('button', {name: `Open trip ${anotherTrip.label}`}));
    expect(await screen.findByRole('heading', {name: 'Progressive Trip Builder'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /add airfare/i})).toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: /search flights/i})).not.toBeInTheDocument();
  });

  it('initiates trip creation through Stay entry point with upfront accommodation type preference and opens stay search', async () => {
    const user = userEvent.setup();

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [],
        past: [],
      })
    );

    render(<App />);
    await screen.findByText('ada@example.test');

    const stayBtn = screen.getByRole('button', {name: 'Stay'});
    await user.click(stayBtn);

    await screen.findByRole('dialog', {name: /plan a trip with stay/i});

    // Check mandatory accommodation type selector is present
    const typeSelect = screen.getByLabelText('Accommodation type');
    expect(typeSelect).toBeInTheDocument();
    expect(typeSelect).toHaveValue('HOTEL');

    // Change to VACATION_RENTAL
    await user.selectOptions(typeSelect, 'VACATION_RENTAL');
    expect(typeSelect).toHaveValue('VACATION_RENTAL');

    await user.type(screen.getByLabelText('Departure date'), '2027-03-05');
    await user.type(screen.getByLabelText('Return date'), '2027-03-12');

    fetchMock.mockResolvedValueOnce(
      json(201, createMockTrip({
        startDate: '2027-03-05',
        endDate: '2027-03-12',
      }))
    );

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [{id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-05', endDate: '2027-03-12', label: 'Trip to San Francisco', version: 0, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: [{id: 'draft-1', lifecycle: 'DRAFT', version: 0, status: 'DRAFT', expired: false}]}],
        past: [],
      })
    );

    // Mock search stays triggered on entry
    fetchMock.mockResolvedValueOnce(
      json(200, {
        tripId: 'trip-1',
        draftId: 'draft-1',
        destinationKey: 'destination-sfo',
        accommodationType: 'VACATION_RENTAL',
        startDate: '2027-03-05',
        endDate: '2027-03-12',
        travelerCount: 2,
        sort: 'DEFAULT',
        options: [],
      })
    );

    await user.click(screen.getByRole('button', {name: /create trip/i}));

    await screen.findByText('Trip to San Francisco');

    // Stay slot is directly searching
    expect(await screen.findByRole('heading', {name: /search stays/i})).toBeInTheDocument();
    // The search section initialized with VACATION_RENTAL
    const staySearchType = screen.getByLabelText('Accommodation type');
    expect(staySearchType).toHaveValue('VACATION_RENTAL');

    // Airfare slot is empty ("Add airfare")
    expect(screen.getByRole('button', {name: /add airfare/i})).toBeInTheDocument();
    // Rental slot is hidden
    expect(screen.queryByRole('heading', {name: /rental car|search rental cars/i})).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: /add a car/i})).toBeInTheDocument();
  });

  it('initiates trip creation through Plan Trip entry point with both Airfare and Stay slots ready and Rental Car hidden', async () => {
    const user = userEvent.setup();

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [],
        past: [],
      })
    );

    render(<App />);
    await screen.findByText('ada@example.test');

    const planBtn = screen.getByRole('button', {name: 'Plan Trip'});
    await user.click(planBtn);

    await screen.findByRole('dialog', {name: /plan a new trip/i});

    await user.type(screen.getByLabelText('Departure date'), '2027-03-15');
    await user.type(screen.getByLabelText('Return date'), '2027-03-20');

    fetchMock.mockResolvedValueOnce(json(201, createMockTrip({
      startDate: '2027-03-15',
      endDate: '2027-03-20',
    })));

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [{id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-15', endDate: '2027-03-20', label: 'Trip to San Francisco', version: 0, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: [{id: 'draft-1', lifecycle: 'DRAFT', version: 0, status: 'DRAFT', expired: false}]}],
        past: [],
      })
    );

    await user.click(screen.getByRole('button', {name: /create trip/i}));

    await screen.findByText('Trip to San Francisco');

    // Both slots ready / empty
    expect(screen.getByRole('button', {name: /add airfare/i})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /add stay/i})).toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: /search flights/i})).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: /search stays/i})).not.toBeInTheDocument();

    // Rental slot is hidden
    expect(screen.queryByRole('heading', {name: /rental car|search rental cars/i})).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: /add a car/i})).toBeInTheDocument();
  });

  it('progressively reveals Rental Car slot only after explicit Add a car action and closes without confirmation on cancel', async () => {
    const user = userEvent.setup();

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [{
          id: 'trip-1',
          destinationKey: 'destination-sfo',
          destinationName: 'San Francisco',
          startDate: '2027-03-10',
          endDate: '2027-03-14',
          label: 'Trip to San Francisco',
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
      })
    );

    render(<App />);
    await screen.findByText('ada@example.test');

    // Open existing trip
    fetchMock.mockResolvedValueOnce(json(200, createMockTrip()));
    await user.click(screen.getByRole('button', {name: /open trip/i}));

    await screen.findByText('Trip to San Francisco');

    // Rental car is hidden upfront
    expect(screen.queryByRole('heading', {name: /rental car|search rental cars/i})).not.toBeInTheDocument();
    const addCarBtn = screen.getByRole('button', {name: 'Add a car'});

    // Mock rental search
    fetchMock.mockResolvedValueOnce(
      json(200, {
        tripId: 'trip-1',
        draftId: 'draft-1',
        destinationKey: 'destination-sfo',
        billingCycles: 4,
        driverEligible: true,
        selectionDisabled: false,
        sort: 'DEFAULT',
        options: [],
      })
    );

    // Click "Add a car"
    await user.click(addCarBtn);

    // Rental search is revealed
    expect(await screen.findByRole('heading', {name: /search rental cars/i})).toBeInTheDocument();

    // Click "Cancel" in rental search
    const cancelBtn = screen.getByRole('button', {name: 'Cancel'});
    await user.click(cancelBtn);

    // Rental search closes without showing confirmation modal
    expect(screen.queryByRole('heading', {name: /search rental cars/i})).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Add a car'})).toBeInTheDocument();
  });

  it('airfare search supports direct-only filter, sort overrides, and flight selection updates draft and persistent tally', async () => {
    const user = userEvent.setup();

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [{id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-10', endDate: '2027-03-14', label: 'Trip to San Francisco', version: 0, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []}],
        past: [],
      })
    );

    render(<App />);
    await screen.findByText('ada@example.test');

    fetchMock.mockResolvedValueOnce(json(200, createMockTrip()));
    await user.click(screen.getByRole('button', {name: /open trip/i}));

    await screen.findByText('Trip to San Francisco');

    // Click "Add airfare"
    const flightOptions = [
      {
        combinationKey: 'comb-1',
        outbound: {
          flightInstanceId: 101,
          catalogKey: 'cat-1',
          carrier: 'SkyWays',
          flightNumber: 'SK101',
          stopCount: 0,
          originAirportCode: 'PDX',
          originAirportName: 'Portland',
          destinationAirportCode: 'SFO',
          destinationAirportName: 'San Francisco',
          departureTime: '2027-03-10T08:00:00Z',
          arrivalTime: '2027-03-10T09:45:00Z',
          departureTimeZone: 'PST',
          arrivalTimeZone: 'PST',
          durationMinutes: 105,
          availableSeats: 5,
          baseFareCents: 10000,
          taxCents: 2000,
          feeCents: 1000,
          totalFareCents: 13000,
          layover: null,
        },
        returnFlight: {
          flightInstanceId: 202,
          catalogKey: 'cat-2',
          carrier: 'SkyWays',
          flightNumber: 'SK202',
          stopCount: 0,
          originAirportCode: 'SFO',
          originAirportName: 'San Francisco',
          destinationAirportCode: 'PDX',
          destinationAirportName: 'Portland',
          departureTime: '2027-03-14T17:00:00Z',
          arrivalTime: '2027-03-14T18:45:00Z',
          departureTimeZone: 'PST',
          arrivalTimeZone: 'PST',
          durationMinutes: 105,
          availableSeats: 5,
          baseFareCents: 11000,
          taxCents: 2000,
          feeCents: 1000,
          totalFareCents: 14000,
          layover: null,
        },
        totalDurationMinutes: 210,
        direct: true,
        pricing: {
          travelerCount: 2,
          perTravelerBaseFareCents: 21000,
          perTravelerTaxCents: 4000,
          perTravelerFeeCents: 2000,
          perTravelerTotalCents: 27000,
          partyBaseFareCents: 42000,
          partyTaxCents: 8000,
          partyFeeCents: 4000,
          partyTotalPriceCents: 54000,
        },
      },
    ];

    fetchMock.mockResolvedValueOnce(
      json(200, {
        tripId: 'trip-1',
        draftId: 'draft-1',
        destinationKey: 'destination-sfo',
        originAirportCode: 'PDX',
        destinationAirportCode: 'SFO',
        startDate: '2027-03-10',
        endDate: '2027-03-14',
        travelerCount: 2,
        directOnly: false,
        sort: 'DEFAULT',
        options: flightOptions,
      })
    );

    await user.click(screen.getByRole('button', {name: 'Add airfare'}));

    expect(await screen.findByText('SkyWays • #SK101')).toBeInTheDocument();
    expect(screen.getByText('$540.00')).toBeInTheDocument();

    // Filter by direct only
    fetchMock.mockResolvedValueOnce(
      json(200, {
        tripId: 'trip-1',
        draftId: 'draft-1',
        directOnly: true,
        sort: 'DEFAULT',
        options: flightOptions,
      })
    );
    await user.click(screen.getByLabelText('Direct flights only'));

    // Sort by lowest price
    fetchMock.mockResolvedValueOnce(
      json(200, {
        tripId: 'trip-1',
        draftId: 'draft-1',
        directOnly: true,
        sort: 'LOWEST_PRICE',
        options: flightOptions,
      })
    );
    await user.selectOptions(screen.getByLabelText('Sort by'), 'LOWEST_PRICE');

    // Select flight
    const tripWithAirfare = createMockTrip({
      version: 1,
      drafts: [
        {
          id: 'draft-1',
          version: 1,
          selections: {
            airfare: {
              outboundFlightInstanceId: 101,
              returnFlightInstanceId: 202,
              outboundDescription: 'SkyWays PDX → SFO',
              returnDescription: 'SkyWays SFO → PDX',
              outboundBaseFareCents: 10000,
              outboundTaxCents: 2000,
              outboundFeeCents: 1000,
              returnBaseFareCents: 11000,
              returnTaxCents: 2000,
              returnFeeCents: 1000,
            },
            stay: null,
            rental: null,
          },
        },
      ],
    });

    fetchMock.mockResolvedValueOnce(json(200, tripWithAirfare));

    await user.click(screen.getByRole('button', {name: 'Select flight'}));

    // Airfare slot is now selected
    expect(await screen.findByText('Flight #101 / #202')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Change flight'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Remove'})).toBeInTheDocument();

    // Persistent tally updated
    expect(screen.getByTestId('tally-airfare-price')).toHaveTextContent('$540.00');
    expect(screen.getByTestId('tally-grand-total')).toHaveTextContent('$540.00');
  });

  it('stay search displays calculated room count, rating, distance to center, transparent nightly breakdown, and updates draft and persistent tally', async () => {
    const user = userEvent.setup();

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [{id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-10', endDate: '2027-03-14', label: 'Trip to San Francisco', version: 0, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []}],
        past: [],
      })
    );

    render(<App />);
    await screen.findByText('ada@example.test');

    fetchMock.mockResolvedValueOnce(json(200, createMockTrip()));
    await user.click(screen.getByRole('button', {name: /open trip/i}));

    await screen.findByText('Trip to San Francisco');

    // Click "Add stay"
    const stayOptions = [
      {
        accommodationUnitId: 301,
        propertyId: 10,
        propertyCatalogKey: 'prop-hotel',
        unitCatalogKey: 'unit-queen',
        propertyName: 'Pacific View Hotel',
        unitName: 'Deluxe Double Queen',
        propertyCategory: 'HOTEL',
        unitKind: 'ROOM',
        locationDescription: 'Downtown SF',
        guestRating: 4.8,
        distanceToCityCenterMeters: 1200,
        latitude: 37.7749,
        longitude: -122.4194,
        guestCapacity: 4,
        inventoryCapacity: 10,
        pricing: {
          requiredRooms: 1,
          nightCount: 4,
          perRoomBasePriceCents: 60000,
          perRoomTaxCents: 9000,
          perRoomFeeCents: 3000,
          perRoomTotalPriceCents: 72000,
          totalBasePriceCents: 60000,
          totalTaxCents: 9000,
          totalFeeCents: 3000,
          totalPriceCents: 72000,
          nights: [
            {date: '2027-03-10', basePriceCents: 15000, taxCents: 2250, feeCents: 750, totalCents: 18000, availableInventory: 5},
            {date: '2027-03-11', basePriceCents: 15000, taxCents: 2250, feeCents: 750, totalCents: 18000, availableInventory: 5},
            {date: '2027-03-12', basePriceCents: 15000, taxCents: 2250, feeCents: 750, totalCents: 18000, availableInventory: 5},
            {date: '2027-03-13', basePriceCents: 15000, taxCents: 2250, feeCents: 750, totalCents: 18000, availableInventory: 5},
          ],
        },
        fitsBudget: true,
      },
    ];

    fetchMock.mockResolvedValueOnce(
      json(200, {
        tripId: 'trip-1',
        draftId: 'draft-1',
        destinationKey: 'destination-sfo',
        accommodationType: 'HOTEL',
        sort: 'DEFAULT',
        options: stayOptions,
      })
    );

    await user.click(screen.getByRole('button', {name: 'Add stay'}));

    // Verify card display
    expect(await screen.findByText('Pacific View Hotel')).toBeInTheDocument();
    expect(screen.getByText('Deluxe Double Queen')).toBeInTheDocument();
    expect(screen.getByTestId('stay-rating')).toHaveTextContent('★ 4.8 / 5');
    expect(screen.getByTestId('stay-distance')).toHaveTextContent('1.2 km to city center');
    expect(screen.getByTestId('stay-rooms')).toHaveTextContent('1 room');
    expect(screen.getByText('Fits budget')).toBeInTheDocument();

    // Select stay
    const tripWithStay = createMockTrip({
      version: 1,
      drafts: [
        {
          id: 'draft-1',
          version: 1,
          selections: {
            airfare: null,
            stay: {
              accommodationUnitId: 301,
              unitCount: 1,
              propertyName: 'Pacific View Hotel',
              unitName: 'Deluxe Double Queen',
              nights: stayOptions[0].pricing.nights,
            },
            rental: null,
          },
        },
      ],
    });

    fetchMock.mockResolvedValueOnce(json(200, tripWithStay));

    await user.click(screen.getByRole('button', {name: 'Select stay'}));

    // Slot is now selected
    expect(await screen.findByText('Pacific View Hotel')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Change stay'})).toBeInTheDocument();

    // Tally updated
    expect(screen.getByTestId('tally-stay-price')).toHaveTextContent('$720.00');
    expect(screen.getByTestId('tally-grand-total')).toHaveTextContent('$720.00');
  });

  it('rental car search validates dates, enforces 25+ driver age rule with required explanation text, and updates draft and persistent tally', async () => {
    const user = userEvent.setup();

    // Trip with no traveler aged 25+
    const tripIneligible = createMockTrip({
      travelerAges: [21, 22],
    });

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [{id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-10', endDate: '2027-03-14', label: 'Trip to San Francisco', version: 0, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []}],
        past: [],
      })
    );

    render(<App />);
    await screen.findByText('ada@example.test');

    fetchMock.mockResolvedValueOnce(json(200, tripIneligible));
    await user.click(screen.getByRole('button', {name: /open trip/i}));

    await screen.findByText('Trip to San Francisco');

    const carOptions = [
      {
        rentalUnitId: 401,
        unitCatalogKey: 'car-std',
        unitIdentifier: 'STD-1',
        vehicleClassId: 2,
        vehicleClassCatalogKey: 'standard-sedan',
        vehicleClassName: 'Standard Sedan',
        vehicleCategory: 'STANDARD',
        locationId: 5,
        locationCatalogKey: 'loc-sfo',
        locationName: 'SFO Airport Center',
        airportIataCode: 'SFO',
        pricing: {
          billingCycles: 4,
          dailyBasePriceCents: 5000,
          dailyTaxCents: 1000,
          dailyFeeCents: 500,
          dailyTotalPriceCents: 6500,
          totalBasePriceCents: 20000,
          totalTaxCents: 4000,
          totalFeeCents: 2000,
          totalPriceCents: 26000,
        },
        fitsBudget: true,
      },
    ];

    fetchMock.mockResolvedValueOnce(
      json(200, {
        tripId: 'trip-1',
        draftId: 'draft-1',
        destinationKey: 'destination-sfo',
        billingCycles: 4,
        driverEligible: false,
        selectionDisabled: true,
        sort: 'DEFAULT',
        options: carOptions,
      })
    );

    // Reveal rental car slot
    await user.click(screen.getByRole('button', {name: 'Add a car'}));

    // Verify 25+ explanation text is displayed
    expect(
      await screen.findByText(
        'Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car.'
      )
    ).toBeInTheDocument();

    // Verify select button is disabled
    const selectCarBtn = screen.getByRole('button', {name: 'Select car'});
    expect(selectCarBtn).toBeDisabled();
  });

  it('persistent summary calculates accurate component totals, grand total, and remaining budget or overage when budget is set', () => {
    // 1. With budget $2,000.00 and selections totaling $1,260.00
    const tripWithBudget = createMockTrip({
      budgetCents: 200000,
      travelerCount: 2,
    });

    const selections: DraftSelectionResponse = {
      airfare: {
        outboundFlightInstanceId: 101,
        returnFlightInstanceId: 202,
        outboundDescription: 'Outbound',
        returnDescription: 'Return',
        outboundBaseFareCents: 10000,
        outboundTaxCents: 2000,
        outboundFeeCents: 1000,
        returnBaseFareCents: 11000,
        returnTaxCents: 2000,
        returnFeeCents: 1000,
      }, // (130 + 140) * 2 = 540.00
      stay: {
        accommodationUnitId: 301,
        unitCount: 1,
        propertyName: 'Hotel',
        unitName: 'Room',
        nights: [
          {date: '2027-03-10', basePriceCents: 15000, taxCents: 2000, feeCents: 1000},
          {date: '2027-03-11', basePriceCents: 15000, taxCents: 2000, feeCents: 1000},
          {date: '2027-03-12', basePriceCents: 15000, taxCents: 2000, feeCents: 1000},
        ],
      }, // 3 * 180 = 540.00
      rental: {
        rentalUnitId: 401,
        pickupAt: '2027-03-10T10:00:00Z',
        returnAt: '2027-03-12T10:00:00Z',
        locationName: 'SFO',
        vehicleClassName: 'Sedan',
        unitIdentifier: 'SEDAN',
        dailyBasePriceCents: 7000,
        dailyTaxCents: 1500,
        dailyFeeCents: 500,
      }, // 2 cycles * 90 = 180.00
    }; // Total = 540 + 540 + 180 = 1,260.00. Remaining = 2,000 - 1,260 = 740.00

    const {rerender} = render(
      <ItinerarySummaryTally trip={tripWithBudget} selections={selections} />
    );

    expect(screen.getByTestId('tally-airfare-price')).toHaveTextContent('$540.00');
    expect(screen.getByTestId('tally-stay-price')).toHaveTextContent('$540.00');
    expect(screen.getByTestId('tally-rental-price')).toHaveTextContent('$180.00');
    expect(screen.getByTestId('tally-grand-total')).toHaveTextContent('$1,260.00');
    expect(screen.getByTestId('tally-budget-total')).toHaveTextContent('$2,000.00');
    expect(screen.getByTestId('tally-remaining')).toHaveTextContent('$740.00');
    expect(screen.getByText('Within Budget')).toBeInTheDocument();

    // 2. Over budget: budget $1,000.00, total $1,260.00 -> overage $260.00
    const tripOverBudget = createMockTrip({
      budgetCents: 100000,
      travelerCount: 2,
    });
    rerender(<ItinerarySummaryTally trip={tripOverBudget} selections={selections} />);

    expect(screen.getByTestId('tally-overage')).toHaveTextContent('$260.00');
    expect(screen.getByText('Over Budget')).toBeInTheDocument();

    // 3. Absent budget: budgetCents is null
    const tripNoBudget = createMockTrip({
      budgetCents: null,
      travelerCount: 2,
    });
    rerender(<ItinerarySummaryTally trip={tripNoBudget} selections={selections} />);

    expect(screen.getByTestId('tally-grand-total')).toHaveTextContent('$1,260.00');
    expect(screen.queryByTestId('tally-budget-total')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tally-remaining')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tally-overage')).not.toBeInTheDocument();
    expect(screen.queryByText('Within Budget')).not.toBeInTheDocument();
    expect(screen.queryByText('Over Budget')).not.toBeInTheDocument();
  });

  it('safe component removal requires explicit confirmation naming component and discarded price before deletion', async () => {
    const user = userEvent.setup();

    const tripWithAirfare = createMockTrip({
      version: 1,
      drafts: [
        {
          id: 'draft-1',
          version: 1,
          selections: {
            airfare: {
              outboundFlightInstanceId: 101,
              returnFlightInstanceId: 202,
              outboundDescription: 'SkyWays PDX → SFO',
              returnDescription: 'SkyWays SFO → PDX',
              outboundBaseFareCents: 10000,
              outboundTaxCents: 2000,
              outboundFeeCents: 1000,
              returnBaseFareCents: 11000,
              returnTaxCents: 2000,
              returnFeeCents: 1000,
            },
            stay: null,
            rental: null,
          },
        },
      ],
    });

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [{id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-10', endDate: '2027-03-14', label: 'Trip to San Francisco', version: 1, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []}],
        past: [],
      })
    );

    render(<App />);
    await screen.findByText('ada@example.test');

    fetchMock.mockResolvedValueOnce(json(200, tripWithAirfare));
    await user.click(screen.getByRole('button', {name: /open trip/i}));

    await screen.findByText('Flight #101 / #202');

    // Click "Remove"
    await user.click(screen.getByRole('button', {name: 'Remove'}));

    // Confirmation modal opens naming component and discarded price
    const modal = await screen.findByRole('dialog', {name: /remove airfare\?/i});
    expect(
      within(modal).getByText(
        'Are you sure you want to remove this airfare? This will discard the saved option totaling $540.00 from your itinerary.'
      )
    ).toBeInTheDocument();

    // Click Cancel -> modal closes, selection remains intact
    await user.click(within(modal).getByRole('button', {name: 'Cancel'}));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('Flight #101 / #202')).toBeInTheDocument();

    // Click Remove again and confirm
    await user.click(screen.getByRole('button', {name: 'Remove'}));
    const confirmModal = await screen.findByRole('dialog', {name: /remove airfare\?/i});

    const tripCleared = createMockTrip({
      version: 2,
      drafts: [
        {
          id: 'draft-1',
          version: 2,
          selections: {airfare: null, stay: null, rental: null},
        },
      ],
    });

    fetchMock.mockResolvedValueOnce(json(200, tripCleared));

    await user.click(within(confirmModal).getByRole('button', {name: 'Remove airfare'}));

    // DELETE request was sent with expectedVersion: 1 and expectedDraftVersion: 1
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/trips/trip-1/drafts/draft-1/airfare',
      expect.objectContaining({
        method: 'DELETE',
        body: JSON.stringify({expectedVersion: 1, expectedDraftVersion: 1}),
      })
    );

    // Slot is now empty
    expect(await screen.findByRole('button', {name: 'Add airfare'})).toBeInTheDocument();
    expect(screen.getByTestId('tally-airfare-price')).toHaveTextContent('Not selected');
  });

  it('concurrency conflict (409 VERSION_CONFLICT) displays alert with reload action while preserving user search inputs', async () => {
    const user = userEvent.setup();

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [{id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-10', endDate: '2027-03-14', label: 'Trip to San Francisco', version: 0, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []}],
        past: [],
      })
    );

    render(<App />);
    await screen.findByText('ada@example.test');

    fetchMock.mockResolvedValueOnce(json(200, createMockTrip()));
    await user.click(screen.getByRole('button', {name: /open trip/i}));

    await screen.findByText('Trip to San Francisco');

    // Click "Add airfare"
    const flightOptions = [
      {
        combinationKey: 'comb-1',
        outbound: {
          flightInstanceId: 101,
          catalogKey: 'cat-1',
          carrier: 'SkyWays',
          flightNumber: 'SK101',
          stopCount: 0,
          originAirportCode: 'PDX',
          originAirportName: 'Portland',
          destinationAirportCode: 'SFO',
          destinationAirportName: 'San Francisco',
          departureTime: '2027-03-10T08:00:00Z',
          arrivalTime: '2027-03-10T09:45:00Z',
          departureTimeZone: 'PST',
          arrivalTimeZone: 'PST',
          durationMinutes: 105,
          availableSeats: 5,
          baseFareCents: 10000,
          taxCents: 2000,
          feeCents: 1000,
          totalFareCents: 13000,
          layover: null,
        },
        returnFlight: {
          flightInstanceId: 202,
          catalogKey: 'cat-2',
          carrier: 'SkyWays',
          flightNumber: 'SK202',
          stopCount: 0,
          originAirportCode: 'SFO',
          originAirportName: 'San Francisco',
          destinationAirportCode: 'PDX',
          destinationAirportName: 'Portland',
          departureTime: '2027-03-14T17:00:00Z',
          arrivalTime: '2027-03-14T18:45:00Z',
          departureTimeZone: 'PST',
          arrivalTimeZone: 'PST',
          durationMinutes: 105,
          availableSeats: 5,
          baseFareCents: 11000,
          taxCents: 2000,
          feeCents: 1000,
          totalFareCents: 14000,
          layover: null,
        },
        totalDurationMinutes: 210,
        direct: true,
        pricing: {
          travelerCount: 2,
          perTravelerBaseFareCents: 21000,
          perTravelerTaxCents: 4000,
          perTravelerFeeCents: 2000,
          perTravelerTotalCents: 27000,
          partyBaseFareCents: 42000,
          partyTaxCents: 8000,
          partyFeeCents: 4000,
          partyTotalPriceCents: 54000,
        },
      },
    ];

    fetchMock.mockResolvedValueOnce(
      json(200, {
        tripId: 'trip-1',
        draftId: 'draft-1',
        options: flightOptions,
      })
    );

    await user.click(screen.getByRole('button', {name: 'Add airfare'}));
    expect(await screen.findByText('SkyWays • #SK101')).toBeInTheDocument();

    // Select flight fails with 409 VERSION_CONFLICT
    fetchMock.mockResolvedValueOnce(
      json(409, {code: 'VERSION_CONFLICT', message: 'The Trip has changed on the server.'})
    );

    await user.click(screen.getByRole('button', {name: 'Select flight'}));

    // Conflict alert is rendered
    expect(
      await screen.findByText('The Trip has changed on the server. Reload before saving.')
    ).toBeInTheDocument();
    const reloadBtn = screen.getByRole('button', {name: 'Reload from server'});
    expect(reloadBtn).toBeInTheDocument();

    // User's search input context is preserved (still on flight options screen)
    expect(screen.getByText('SkyWays • #SK101')).toBeInTheDocument();

    // Reload from server updates trip version and recovers
    fetchMock.mockResolvedValueOnce(
      json(200, createMockTrip({version: 5, drafts: [{id: 'draft-1', version: 5, selections: {airfare: null, stay: null, rental: null}}]}))
    );

    await user.click(reloadBtn);

    // Conflict cleared, context still preserved
    await waitFor(() => {
      expect(
        screen.queryByText('The Trip has changed on the server. Reload before saving.')
      ).not.toBeInTheDocument();
    });
    expect(screen.getByText('SkyWays • #SK101')).toBeInTheDocument();
  });

  it('modal dialogs enforce accessibility: role=dialog, aria-modal=true, focus trapping, and Escape key dismissal', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onConfirm = vi.fn();

    render(
      <ConfirmRemoveModal
        isOpen={true}
        componentTitle="Airfare"
        formattedPrice="$540.00"
        pending={false}
        onClose={onClose}
        onConfirm={onConfirm}
      />
    );

    const dialog = screen.getByRole('dialog', {name: /remove airfare\?/i});
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute('aria-modal', 'true');

    // Press Escape
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('formatLocalToDestinationIso appends accurate destination airport timezone offsets', () => {
    expect(formatLocalToDestinationIso('2027-03-02T06:00', 'destination-sfo')).toBe(
      '2027-03-02T06:00:00-08:00'
    );
    expect(formatLocalToDestinationIso('2027-03-02T23:00', 'destination-muc')).toBe(
      '2027-03-02T23:00:00+01:00'
    );
    expect(formatLocalToDestinationIso('2027-03-02T12:00', 'destination-mex')).toBe(
      '2027-03-02T12:00:00-06:00'
    );
  });

  it('restores focus to trigger button when ConfirmRemoveModal closes/unmounts', async () => {
    const user = userEvent.setup();
    function TestWrapper() {
      const [open, setOpen] = useState(false);
      return (
        <div>
          <button type="button" onClick={() => setOpen(true)}>
            Open Remove Modal
          </button>
          {open && (
            <ConfirmRemoveModal
              isOpen={open}
              componentTitle="Airfare"
              formattedPrice="$540.00"
              pending={false}
              onClose={() => setOpen(false)}
              onConfirm={() => setOpen(false)}
            />
          )}
        </div>
      );
    }

    render(<TestWrapper />);
    const trigger = screen.getByRole('button', {name: 'Open Remove Modal'});
    await user.click(trigger);

    expect(screen.getByRole('dialog', {name: /remove airfare\?/i})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.activeElement).toBe(trigger);
  });

  it('defensively resets slot to empty when draft selection becomes null on reload', async () => {
    const user = userEvent.setup();

    const tripWithAirfare = createMockTrip({
      version: 1,
      drafts: [
        {
          id: 'draft-1',
          version: 1,
          selections: {
            airfare: {
              outboundFlightInstanceId: 101,
              returnFlightInstanceId: 202,
              outboundDescription: 'SkyWays PDX → SFO',
              returnDescription: 'SkyWays SFO → PDX',
              outboundBaseFareCents: 10000,
              outboundTaxCents: 2000,
              outboundFeeCents: 1000,
              returnBaseFareCents: 11000,
              returnTaxCents: 2000,
              returnFeeCents: 1000,
            },
            stay: null,
            rental: null,
          },
        },
      ],
    });

    fetchMock.mockResolvedValueOnce(
      json(200, {
        email: 'ada@example.test',
        upcoming: [{id: 'trip-1', destinationKey: 'destination-sfo', destinationName: 'San Francisco', startDate: '2027-03-10', endDate: '2027-03-14', label: 'Trip to San Francisco', version: 1, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 0, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []}],
        past: [],
      })
    );

    render(<App />);
    await screen.findByText('ada@example.test');

    fetchMock.mockResolvedValueOnce(json(200, tripWithAirfare));
    await user.click(screen.getByRole('button', {name: /open trip/i}));

    expect(await screen.findByText('Flight #101 / #202')).toBeInTheDocument();

    // Mock searchAirfare when clicking Change flight
    fetchMock.mockResolvedValueOnce(
      json(200, {
        tripId: 'trip-1',
        draftId: 'draft-1',
        options: [
          {
            combinationKey: 'comb-1',
            outbound: {
              flightInstanceId: 101,
              catalogKey: 'cat-1',
              carrier: 'SkyWays',
              flightNumber: 'SK101',
              stopCount: 0,
              originAirportCode: 'PDX',
              originAirportName: 'Portland',
              destinationAirportCode: 'SFO',
              destinationAirportName: 'San Francisco',
              departureTime: '2027-03-10T08:00:00Z',
              arrivalTime: '2027-03-10T09:45:00Z',
              departureTimeZone: 'America/Los_Angeles',
              arrivalTimeZone: 'America/Los_Angeles',
              durationMinutes: 105,
              availableSeats: 5,
              baseFareCents: 10000,
              taxCents: 2000,
              feeCents: 1000,
              totalFareCents: 13000,
              layover: null,
            },
            returnFlight: {
              flightInstanceId: 202,
              catalogKey: 'cat-2',
              carrier: 'SkyWays',
              flightNumber: 'SK202',
              stopCount: 0,
              originAirportCode: 'SFO',
              originAirportName: 'San Francisco',
              destinationAirportCode: 'PDX',
              destinationAirportName: 'Portland',
              departureTime: '2027-03-14T17:00:00Z',
              arrivalTime: '2027-03-14T18:45:00Z',
              departureTimeZone: 'America/Los_Angeles',
              arrivalTimeZone: 'America/Los_Angeles',
              durationMinutes: 105,
              availableSeats: 5,
              baseFareCents: 11000,
              taxCents: 2000,
              feeCents: 1000,
              totalFareCents: 14000,
              layover: null,
            },
            totalDurationMinutes: 210,
            direct: true,
            pricing: {
              travelerCount: 2,
              perTravelerBaseFareCents: 21000,
              perTravelerTaxCents: 4000,
              perTravelerFeeCents: 2000,
              perTravelerTotalCents: 27000,
              partyBaseFareCents: 42000,
              partyTaxCents: 8000,
              partyFeeCents: 4000,
              partyTotalPriceCents: 54000,
            },
          },
        ],
      })
    );

    await user.click(screen.getByRole('button', {name: 'Change flight'}));
    expect(await screen.findByText('SkyWays • #SK101')).toBeInTheDocument();
    expect(screen.getByText(/Departure: PDX.*March 10, 2027.*America\/Los_Angeles/)).toBeInTheDocument();
    expect(screen.getByText(/Arrival: SFO.*March 10, 2027.*America\/Los_Angeles/)).toBeInTheDocument();
    expect(screen.getByText(/Departure: SFO.*March 14, 2027.*America\/Los_Angeles/)).toBeInTheDocument();

    // Trigger conflict by failing a mutation, which reveals Reload from server
    fetchMock.mockResolvedValueOnce(
      json(409, {code: 'VERSION_CONFLICT', message: 'The Trip has changed on the server.'})
    );
    await user.click(screen.getByRole('button', {name: 'Select flight'}));

    expect(await screen.findByText('The Trip has changed on the server. Reload before saving.')).toBeInTheDocument();

    // Now reload from server, but server has cleared the airfare selection!
    const tripWithClearedAirfare = createMockTrip({
      version: 2,
      drafts: [
        {
          id: 'draft-1',
          version: 2,
          selections: {
            airfare: null,
            stay: null,
            rental: null,
          },
        },
      ],
    });

    fetchMock.mockResolvedValueOnce(json(200, tripWithClearedAirfare));
    await user.click(screen.getByRole('button', {name: 'Reload from server'}));

    // Context is preserved on reload; clicking Cancel gracefully transitions to empty slot since airfare was cleared
    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    // Slot gracefully switches to empty with "Add airfare" button instead of blank card
    expect(await screen.findByRole('button', {name: 'Add airfare'})).toBeInTheDocument();
  });
});
