import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {TripWorkspace} from './components/TripWorkspace';
import {ProfileScreen} from './components/ProfileScreen';
import {CancelBookingModal} from './components/CancelBookingModal';
import {PostCancellationTriageModal} from './components/PostCancellationTriageModal';
import {CancelTripModal} from './components/CancelTripModal';
import {BookingHistorySection} from './components/BookingHistorySection';
import {TripListSection} from './components/TripListSection';
import {tripsApi, type TripResponse, type BookingResponse, type TripProfileSummary} from './api/tripsApi';

function createMockTrip(overrides: Partial<TripResponse> = {}): TripResponse {
  return {
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
    version: 1,
    drafts: [],
    planned: [
      {
        id: 'alt-planned-1',
        selections: {
          airfare: {
            outboundFlightInstanceId: 101,
            returnFlightInstanceId: 202,
            outboundDescription: 'PDX to SFO',
            returnDescription: 'SFO to PDX',
            outboundBaseFareCents: 15000,
            outboundTaxCents: 1500,
            outboundFeeCents: 500,
            returnBaseFareCents: 15000,
            returnTaxCents: 1500,
            returnFeeCents: 500,
          },
          stay: {
            accommodationUnitId: 1,
            unitCount: 1,
            propertyName: 'Harbor Hotel',
            unitName: 'Queen Room',
            nights: [
              {date: '2027-03-10', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
              {date: '2027-03-11', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
              {date: '2027-03-12', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
              {date: '2027-03-13', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
            ],
          },
          rental: null,
        },
        tally: {
          airfareTotalCents: 34000,
          stayTotalCents: 90000,
          rentalTotalCents: 0,
          grandTotalCents: 124000,
          remainingBudgetCents: 76000,
          budgetOverageCents: 0,
          isOverBudget: false,
        },
      },
    ],
    alternatives: [
      {
        id: 'alt-planned-1',
        lifecycle: 'PLANNED',
        version: 1,
        selections: {
          airfare: {
            outboundFlightInstanceId: 101,
            returnFlightInstanceId: 202,
            outboundDescription: 'PDX to SFO',
            returnDescription: 'SFO to PDX',
            outboundBaseFareCents: 15000,
            outboundTaxCents: 1500,
            outboundFeeCents: 500,
            returnBaseFareCents: 15000,
            returnTaxCents: 1500,
            returnFeeCents: 500,
          },
          stay: {
            accommodationUnitId: 1,
            unitCount: 1,
            propertyName: 'Harbor Hotel',
            unitName: 'Queen Room',
            nights: [
              {date: '2027-03-10', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
              {date: '2027-03-11', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
              {date: '2027-03-12', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
              {date: '2027-03-13', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
            ],
          },
          rental: null,
        },
        tally: {
          airfareTotalCents: 34000,
          stayTotalCents: 90000,
          rentalTotalCents: 0,
          grandTotalCents: 124000,
          remainingBudgetCents: 76000,
          budgetOverageCents: 0,
          isOverBudget: false,
        },
      },
    ],
    revisionSummary: null,
    ...overrides,
  };
}

function createMockBooking(overrides: Partial<BookingResponse> = {}): BookingResponse {
  return {
    id: 'booking-1',
    tripId: 'trip-1',
    plannedItineraryId: 'alt-planned-1',
    bookingReference: 'DT-K8M2P4',
    status: 'ACTIVE',
    grandTotalCents: 124000,
    idempotencyKey: 'idem-1',
    bookedAt: '2027-01-10T12:00:00Z',
    airfareReference: 'AIR-12345',
    stayReference: 'STY-67890',
    rentalReference: null,
    selections: {
      airfare: {
        outboundFlightInstanceId: 101,
        returnFlightInstanceId: 202,
        outboundDescription: 'PDX to SFO',
        returnDescription: 'SFO to PDX',
        outboundBaseFareCents: 15000,
        outboundTaxCents: 1500,
        outboundFeeCents: 500,
        returnBaseFareCents: 15000,
        returnTaxCents: 1500,
        returnFeeCents: 500,
      },
      stay: {
        accommodationUnitId: 1,
        unitCount: 1,
        propertyName: 'Harbor Hotel',
        unitName: 'Queen Room',
        nights: [
          {date: '2027-03-10', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
          {date: '2027-03-11', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
          {date: '2027-03-12', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
          {date: '2027-03-13', basePriceCents: 20000, taxCents: 2000, feeCents: 500},
        ],
      },
      rental: null,
    },
    tally: {
      airfareTotalCents: 34000,
      stayTotalCents: 90000,
      rentalTotalCents: 0,
      grandTotalCents: 124000,
      remainingBudgetCents: 76000,
      budgetOverageCents: 0,
      isOverBudget: false,
    },
    ...overrides,
  };
}

describe('Fee-Free Cancellation and Post-Cancellation Triage', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    document.cookie = 'XSRF-TOKEN=secret-token; path=/';
    vi.spyOn(tripsApi, 'getBookingHistory').mockResolvedValue([]);
    vi.spyOn(tripsApi, 'getActiveBooking').mockResolvedValue(null as unknown as BookingResponse);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; max-age=0; path=/';
    fetchMock.mockReset();
    vi.restoreAllMocks();
  });

  // Test 1: Cancel Booking button opens CancelBookingModal with fee-free explanation
  it('Cancel Booking button opens CancelBookingModal with fee-free explanation and inventory notice', async () => {
    const trip = createMockTrip();
    const activeBooking = createMockBooking();

    render(
      <TripWorkspace
        initialTrip={trip}
        initialActiveBooking={activeBooking}
        hasBookingHistory={true}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );

    const user = userEvent.setup();
    const cancelBtn = screen.getByRole('button', {name: /cancel booking/i});
    expect(cancelBtn).toBeInTheDocument();
    await user.click(cancelBtn);

    const dialog = screen.getByRole('dialog', {name: /cancel booking/i});
    expect(dialog).toBeInTheDocument();
    expect(within(dialog).getByText(/fee-free cancellation/i)).toBeInTheDocument();
    expect(within(dialog).getByText(/inventory release/i)).toBeInTheDocument();
    expect(within(dialog).getByText(/booking history retained/i)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', {name: 'Cancel Booking'})).toHaveClass('danger-button');
  });

  // Test 2: Confirming Cancel Booking releases inventory and launches PostCancellationTriageModal
  it('Confirming Cancel Booking calls API, releases inventory, and launches PostCancellationTriageModal', async () => {
    const trip = createMockTrip();
    const activeBooking = createMockBooking();
    const updatedTrip = createMockTrip({version: 2});

    const cancelBookingSpy = vi.spyOn(tripsApi, 'cancelBooking').mockResolvedValueOnce(updatedTrip);
    vi.spyOn(tripsApi, 'getBookingHistory').mockResolvedValue([]);

    const user = userEvent.setup();
    render(
      <TripWorkspace
        initialTrip={trip}
        initialActiveBooking={activeBooking}
        hasBookingHistory={true}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );

    await user.click(screen.getByRole('button', {name: /cancel booking/i}));
    const dialog = screen.getByRole('dialog', {name: /cancel booking/i});

    const confirmBtn = within(dialog).getByRole('button', {name: 'Cancel Booking'});
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(cancelBookingSpy).toHaveBeenCalledWith('trip-1', 'booking-1', {
        expectedVersion: 1,
      });
    });

    // CancelBookingModal closes and PostCancellationTriageModal opens
    await waitFor(() => {
      expect(screen.queryByRole('dialog', {name: /cancel booking/i})).not.toBeInTheDocument();
      expect(screen.getByRole('dialog', {name: /reservation canceled/i})).toBeInTheDocument();
    });

    // Active booking banner is cleared
    expect(screen.queryByRole('heading', {name: 'Active Booking'})).not.toBeInTheDocument();
  });

  // Test 3: Triage option 1: Use a saved alternative duplicates planned alternative into a fresh draft
  it('Triage option 1: Use a saved alternative duplicates planned alternative into a fresh draft', async () => {
    const trip = createMockTrip();
    const activeBooking = createMockBooking();
    const tripWithNewDraft = createMockTrip({
      version: 2,
      drafts: [
        {
          id: 'alt-draft-new',
          version: 1,
          selections: trip.planned[0].selections,
          tally: trip.planned[0].tally,
        },
      ],
      alternatives: [
        trip.alternatives[0],
        {
          id: 'alt-draft-new',
          lifecycle: 'DRAFT',
          version: 1,
          selections: trip.planned[0].selections,
          tally: trip.planned[0].tally,
        },
      ],
    });

    vi.spyOn(tripsApi, 'cancelBooking').mockResolvedValueOnce(trip);
    const duplicateAltSpy = vi.spyOn(tripsApi, 'duplicateAlternative').mockResolvedValueOnce(tripWithNewDraft);
    vi.spyOn(tripsApi, 'getBookingHistory').mockResolvedValue([]);

    const user = userEvent.setup();
    render(
      <TripWorkspace
        initialTrip={trip}
        initialActiveBooking={activeBooking}
        hasBookingHistory={true}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );

    // Cancel booking to reach triage
    await user.click(screen.getByRole('button', {name: /cancel booking/i}));
    const dialog = screen.getByRole('dialog', {name: /cancel booking/i});
    await user.click(within(dialog).getByRole('button', {name: 'Cancel Booking'}));

    const triageDialog = await screen.findByRole('dialog', {name: /reservation canceled/i});
    expect(triageDialog).toBeInTheDocument();

    // Click "Use this alternative"
    const useAltBtn = within(triageDialog).getByRole('button', {name: /use this alternative/i});
    await user.click(useAltBtn);

    await waitFor(() => {
      expect(duplicateAltSpy).toHaveBeenCalledWith('trip-1', 'alt-planned-1', {
        expectedVersion: 1,
      });
    });

    // Triage modal dismissed
    await waitFor(() => {
      expect(screen.queryByRole('dialog', {name: /reservation canceled/i})).not.toBeInTheDocument();
    });
  });

  // Test 4: Triage option 2: Create a new Draft creates an empty draft alternative
  it('Triage option 2: Create a new Draft creates an empty draft alternative', async () => {
    const trip = createMockTrip();
    const activeBooking = createMockBooking();
    const tripWithEmptyDraft = createMockTrip({
      version: 2,
      drafts: [
        {
          id: 'alt-draft-empty',
          version: 1,
          selections: {airfare: null, stay: null, rental: null},
          tally: {
            airfareTotalCents: 0,
            stayTotalCents: 0,
            rentalTotalCents: 0,
            grandTotalCents: 0,
            remainingBudgetCents: 200000,
            budgetOverageCents: 0,
            isOverBudget: false,
          },
        },
      ],
      alternatives: [
        trip.alternatives[0],
        {
          id: 'alt-draft-empty',
          lifecycle: 'DRAFT',
          version: 1,
          selections: {airfare: null, stay: null, rental: null},
          tally: {
            airfareTotalCents: 0,
            stayTotalCents: 0,
            rentalTotalCents: 0,
            grandTotalCents: 0,
            remainingBudgetCents: 200000,
            budgetOverageCents: 0,
            isOverBudget: false,
          },
        },
      ],
    });

    vi.spyOn(tripsApi, 'cancelBooking').mockResolvedValueOnce(trip);
    const createDraftSpy = vi.spyOn(tripsApi, 'createDraft').mockResolvedValueOnce(tripWithEmptyDraft);
    vi.spyOn(tripsApi, 'getBookingHistory').mockResolvedValue([]);

    const user = userEvent.setup();
    render(
      <TripWorkspace
        initialTrip={trip}
        initialActiveBooking={activeBooking}
        hasBookingHistory={true}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );

    // Cancel booking to reach triage
    await user.click(screen.getByRole('button', {name: /cancel booking/i}));
    const dialog = screen.getByRole('dialog', {name: /cancel booking/i});
    await user.click(within(dialog).getByRole('button', {name: 'Cancel Booking'}));

    const triageDialog = await screen.findByRole('dialog', {name: /reservation canceled/i});

    // Click "Create a new Draft"
    const createDraftBtn = within(triageDialog).getByRole('button', {name: /create a new draft/i});
    await user.click(createDraftBtn);

    await waitFor(() => {
      expect(createDraftSpy).toHaveBeenCalledWith('trip-1', {
        expectedVersion: 1,
      });
    });

    // Triage modal dismissed
    await waitFor(() => {
      expect(screen.queryByRole('dialog', {name: /reservation canceled/i})).not.toBeInTheDocument();
    });
  });

  // Test 5: Triage option 3: Done for now dismisses triage modal
  it('Triage option 3: Done for now dismisses triage modal', async () => {
    const trip = createMockTrip();
    const activeBooking = createMockBooking();

    vi.spyOn(tripsApi, 'cancelBooking').mockResolvedValueOnce(trip);
    vi.spyOn(tripsApi, 'getBookingHistory').mockResolvedValue([]);

    const user = userEvent.setup();
    render(
      <TripWorkspace
        initialTrip={trip}
        initialActiveBooking={activeBooking}
        hasBookingHistory={true}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );

    await user.click(screen.getByRole('button', {name: /cancel booking/i}));
    const dialog = screen.getByRole('dialog', {name: /cancel booking/i});
    await user.click(within(dialog).getByRole('button', {name: 'Cancel Booking'}));

    const triageDialog = await screen.findByRole('dialog', {name: /reservation canceled/i});
    const doneBtn = within(triageDialog).getByRole('button', {name: /done for now/i});
    expect(triageDialog).toContainElement(document.activeElement as HTMLElement);
    await user.click(doneBtn);

    await waitFor(() => {
      expect(screen.queryByRole('dialog', {name: /reservation canceled/i})).not.toBeInTheDocument();
    });
    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('heading', {name: trip.label})));
  });

  // Test 6: Trip deletion vs trip cancellation gating based on hasBookingHistory
  it('gates Delete Trip vs Cancel Trip based on hasBookingHistory in workspace', async () => {
    const unbookedTrip = createMockTrip();
    const bookedTrip = createMockTrip({id: 'trip-booked'});

    // 1. In TripWorkspace: unbooked trip shows "Delete trip"
    const {unmount} = render(
      <TripWorkspace
        initialTrip={unbookedTrip}
        hasBookingHistory={false}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );
    expect(screen.getByRole('button', {name: `Delete trip ${unbookedTrip.label}`})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: `Cancel trip ${unbookedTrip.label}`})).not.toBeInTheDocument();
    unmount();

    // 2. In TripWorkspace: booked trip shows "Cancel trip"
    render(
      <TripWorkspace
        initialTrip={bookedTrip}
        hasBookingHistory={true}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );
    await waitFor(() => {
      expect(screen.queryByRole('button', {name: `Delete trip ${bookedTrip.label}`})).not.toBeInTheDocument();
      expect(screen.getByRole('button', {name: `Cancel trip ${bookedTrip.label}`})).toBeInTheDocument();
    });
  });

  // Test 7: Confirming Cancel Trip updates workspace to Canceled read-only state
  it('Confirming Cancel Trip updates workspace to Canceled state with badge and banner', async () => {
    const trip = createMockTrip();
    const canceledTrip = createMockTrip({
      status: 'CANCELED',
      version: 2,
    });

    const cancelTripSpy = vi.spyOn(tripsApi, 'cancelTrip').mockResolvedValueOnce(canceledTrip);
    vi.spyOn(tripsApi, 'getBookingHistory').mockResolvedValue([]);

    const user = userEvent.setup();
    render(
      <TripWorkspace
        initialTrip={trip}
        hasBookingHistory={true}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );

    // Click "Cancel trip" in workspace nav
    const cancelTripNavBtn = screen.getByRole('button', {name: `Cancel trip ${trip.label}`});
    await user.click(cancelTripNavBtn);

    const dialog = screen.getByRole('dialog', {name: /cancel trip/i});
    expect(dialog).toBeInTheDocument();
    expect(within(dialog).getByText(/read-only/i)).toBeInTheDocument();

    // Confirm trip cancellation
    const confirmBtn = within(dialog).getByRole('button', {name: 'Cancel Trip'});
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(cancelTripSpy).toHaveBeenCalledWith('trip-1', {
        expectedVersion: 1,
      });
    });

    // Dialog closed, workspace header now displays Canceled badge and banner
    await waitFor(() => {
      expect(screen.queryByRole('dialog', {name: /cancel trip/i})).not.toBeInTheDocument();
      expect(screen.getByText('This trip has been canceled')).toBeInTheDocument();
      expect(screen.getByRole('button', {name: 'Duplicate into a new Trip'})).toBeInTheDocument();
    });
  });

  // Test 8: Canceled trip disables builder slots, hides draft mutations, and provides Duplicate Trip action
  it('Canceled trip disables details form inputs and presents Duplicate Trip revision modal', async () => {
    const canceledTrip = createMockTrip({
      status: 'CANCELED',
      version: 2,
      drafts: [
        {
          id: 'alt-draft-1',
          version: 1,
          selections: {airfare: null, stay: null, rental: null},
          tally: {
            airfareTotalCents: 0,
            stayTotalCents: 0,
            rentalTotalCents: 0,
            grandTotalCents: 0,
            remainingBudgetCents: 200000,
            budgetOverageCents: 0,
            isOverBudget: false,
          },
        },
      ],
      alternatives: [
        ...createMockTrip().alternatives,
        {
          id: 'alt-draft-1',
          lifecycle: 'DRAFT',
          version: 1,
          selections: {airfare: null, stay: null, rental: null},
        },
      ],
    });
    vi.spyOn(tripsApi, 'getBookingHistory').mockResolvedValue([]);

    const user = userEvent.setup();
    render(
      <TripWorkspace
        initialTrip={canceledTrip}
        hasBookingHistory={true}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );

    // Builder slots and promotion are disabled
    expect(screen.getByRole('button', {name: /save as planned itinerary/i})).toBeDisabled();
    expect(screen.getByRole('button', {name: /add airfare/i})).toBeDisabled();
    expect(screen.getByRole('button', {name: /add stay/i})).toBeDisabled();

    // Details fields are disabled
    expect(screen.getByLabelText(/destination/i)).toBeDisabled();
    expect(screen.getByLabelText(/departure date/i)).toBeDisabled();
    expect(screen.getByLabelText(/return date/i)).toBeDisabled();
    expect(screen.getByLabelText(/budget/i)).toBeDisabled();

    // "Revise Trip" button is not shown on canceled trip (replaced by Duplicate banner action)
    expect(screen.queryByRole('button', {name: 'Revise Trip'})).not.toBeInTheDocument();

    // Click "Duplicate into a new Trip"
    const duplicateBtn = screen.getByRole('button', {name: 'Duplicate into a new Trip'});
    await user.click(duplicateBtn);

    // TripRevisionModal opens in duplicate mode
    expect(screen.getByRole('dialog', {name: /duplicate into a new trip/i})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /duplicate trip/i})).toBeInTheDocument();
  });

  // Test 9: Collapsible Booking History section renders historical bookings and components
  it('BookingHistorySection renders audit records with reference codes and breakdown', async () => {
    const historicalBookings: BookingResponse[] = [
      createMockBooking({
        id: 'booking-active-1',
        bookingReference: 'DT-ACT123',
        status: 'ACTIVE',
        grandTotalCents: 124000,
        bookedAt: '2027-01-10T12:00:00Z',
      }),
      createMockBooking({
        id: 'booking-canceled-1',
        bookingReference: 'DT-CAN456',
        status: 'CANCELED',
        grandTotalCents: 98000,
        bookedAt: '2027-01-05T10:00:00Z',
        canceledAt: '2027-01-08T15:00:00Z',
      }),
    ];

    vi.spyOn(tripsApi, 'getBookingHistory').mockResolvedValueOnce(historicalBookings);

    render(<BookingHistorySection tripId="trip-1" />);

    await waitFor(() => {
      expect(screen.getByText('Booking History (2)')).toBeInTheDocument();
    });

    expect(screen.getByText('DT-ACT123')).toBeInTheDocument();
    expect(screen.getByText('DT-CAN456')).toBeInTheDocument();
    expect(screen.getByText('Grand total: $1,240.00 USD')).toBeInTheDocument();
    expect(screen.getByText('Grand total: $980.00 USD')).toBeInTheDocument();
    expect(screen.getByText('Canceled Booking')).toBeInTheDocument();
  });

  // Test 10: Expired and Past trips suppress cancel actions
  it('suppresses or disables cancellation actions on past and expired trips', async () => {
    const trip = createMockTrip();
    const activeBooking = createMockBooking();

    // Render with temporalStatus: 'PAST'
    const {unmount} = render(
      <TripWorkspace
        initialTrip={trip}
        initialActiveBooking={activeBooking}
        hasBookingHistory={true}
        temporalStatus="PAST"
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );

    await waitFor(() => {
      const cancelBookingBtn = screen.getByRole('button', {name: /cancel booking/i});
      expect(cancelBookingBtn).toBeDisabled();

      const cancelTripBtn = screen.getByRole('button', {name: `Cancel trip ${trip.label}`});
      expect(cancelTripBtn).toBeDisabled();
    });
    unmount();

    // Render with isExpired: true
    render(
      <TripWorkspace
        initialTrip={trip}
        initialActiveBooking={activeBooking}
        hasBookingHistory={true}
        isExpired={true}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /cancel booking/i})).toBeDisabled();
      expect(screen.getByRole('button', {name: `Cancel trip ${trip.label}`})).toBeDisabled();
    });
  });

  // Test 11: Modal accessibility: focus trap, Escape dismissal, and live announcements
  it('modal dialogs support Escape key dismissal and maintain accessible attributes', async () => {
    const handleCloseBookingModal = vi.fn();
    const user = userEvent.setup();

    const {rerender} = render(
      <CancelBookingModal
        isOpen={true}
        tripLabel="San Francisco"
        bookingReference="DT-TEST01"
        pending={false}
        onClose={handleCloseBookingModal}
        onConfirm={vi.fn()}
      />
    );

    expect(screen.getByRole('dialog', {name: /cancel booking/i})).toHaveAttribute('aria-modal', 'true');
    await user.keyboard('{Escape}');
    expect(handleCloseBookingModal).toHaveBeenCalledTimes(1);

    // PostCancellationTriageModal Escape test
    const handleCloseTriageModal = vi.fn();
    rerender(
      <PostCancellationTriageModal
        isOpen={true}
        plannedAlternatives={[]}
        pending={false}
        onUseAlternative={vi.fn()}
        onCreateDraft={vi.fn()}
        onClose={handleCloseTriageModal}
      />
    );

    expect(screen.getByRole('dialog', {name: /reservation canceled/i})).toHaveAttribute('aria-modal', 'true');
    await user.keyboard('{Escape}');
    expect(handleCloseTriageModal).toHaveBeenCalledTimes(1);

    // CancelTripModal Escape test
    const handleCloseTripModal = vi.fn();
    rerender(
      <CancelTripModal
        isOpen={true}
        tripLabel="San Francisco"
        hasActiveBooking={false}
        pending={false}
        onClose={handleCloseTripModal}
        onConfirm={vi.fn()}
      />
    );

    expect(screen.getByRole('dialog', {name: /cancel trip/i})).toHaveAttribute('aria-modal', 'true');
    await user.keyboard('{Escape}');
    expect(handleCloseTripModal).toHaveBeenCalledTimes(1);
  });

  // Test 12: Profile screen renders CANCELED badge and handles trip cancellation directly
  it('ProfileScreen renders CANCELED badge and handles trip cancellation directly from trip list', async () => {
    const canceledTripSummary: TripProfileSummary = {
      id: 'trip-canceled-1',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      label: 'Canceled SFO Trip',
      version: 2,
      temporalStatus: 'UPCOMING',
      status: 'CANCELED',
      draftCount: 0,
      plannedCount: 1,
      expiredAlternativeCount: 0,
      bookedCount: 0,
      hasBookingHistory: true,
      alternatives: [],
    };

    const bookedTripSummary: TripProfileSummary = {
      id: 'trip-booked-1',
      destinationKey: 'destination-muc',
      destinationName: 'Munich',
      startDate: '2027-03-15',
      endDate: '2027-03-20',
      label: 'Booked Munich Trip',
      version: 1,
      temporalStatus: 'UPCOMING',
      status: 'ACTIVE',
      draftCount: 0,
      plannedCount: 1,
      expiredAlternativeCount: 0,
      bookedCount: 1,
      hasBookingHistory: true,
      alternatives: [],
    };

    const cancelTripSpy = vi.spyOn(tripsApi, 'cancelTrip').mockResolvedValueOnce({
      ...createMockTrip({id: 'trip-booked-1', status: 'CANCELED', version: 2}),
    });
    const refreshProfileMock = vi.fn().mockResolvedValue(true);

    const user = userEvent.setup();
    render(
      <ProfileScreen
        email="traveler@example.test"
        upcoming={[canceledTripSummary, bookedTripSummary]}
        past={[]}
        onLogout={vi.fn()}
        logoutPending={false}
        onPasswordChange={vi.fn()}
        onFailure={vi.fn()}
        onRefreshProfile={refreshProfileMock}
      />
    );

    await user.click(screen.getByRole('button', {name: 'Profile'}));

    // Canceled trip displays Canceled badge and does not display Cancel trip or Delete trip
    expect(screen.getByText('Canceled Trip', {selector: '.badge-canceled'})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Cancel trip Canceled SFO Trip'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Delete trip Canceled SFO Trip'})).not.toBeInTheDocument();

    // Booked trip displays "Cancel trip" button
    const cancelTripBtn = screen.getByRole('button', {name: 'Cancel trip Booked Munich Trip'});
    expect(cancelTripBtn).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Delete trip Booked Munich Trip'})).not.toBeInTheDocument();

    // Click Cancel trip from profile
    await user.click(cancelTripBtn);

    const dialog = screen.getByRole('dialog', {name: /cancel trip/i});
    expect(dialog).toBeInTheDocument();
    expect(within(dialog).getByText(/Booked Munich Trip/i)).toBeInTheDocument();

    // Confirm cancellation
    const confirmBtn = within(dialog).getByRole('button', {name: 'Cancel Trip'});
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(cancelTripSpy).toHaveBeenCalledWith('trip-booked-1', {expectedVersion: 1});
      expect(refreshProfileMock).toHaveBeenCalled();
    });
  });

  // Test 13: Duplicating a canceled trip into a fresh trip resets booking history gating so Delete Trip is available on the new trip
  it('duplicating a canceled trip resets booking history gating so Delete Trip is available on the new trip', async () => {
    const canceledTrip = createMockTrip({
      id: 'trip-canceled-src',
      label: 'Canceled Hawaii Trip',
      status: 'CANCELED',
      version: 3,
    });

    const duplicatedTrip = createMockTrip({
      id: 'trip-duplicated-fresh',
      label: 'Duplicated Fresh Hawaii Trip',
      status: 'ACTIVE',
      version: 0,
      drafts: [
        {
          id: 'draft-fresh-1',
          version: 1,
          selections: canceledTrip.planned[0].selections,
          tally: canceledTrip.planned[0].tally,
        },
      ],
      planned: [],
      alternatives: [
        {
          id: 'draft-fresh-1',
          lifecycle: 'DRAFT',
          version: 1,
          selections: canceledTrip.planned[0].selections,
          tally: canceledTrip.planned[0].tally,
        },
      ],
    });

    vi.spyOn(tripsApi, 'duplicateTrip').mockResolvedValueOnce(duplicatedTrip);
    vi.spyOn(tripsApi, 'getBookingHistory').mockResolvedValue([]);

    const user = userEvent.setup();
    render(
      <TripWorkspace
        initialTrip={canceledTrip}
        hasBookingHistory={true}
        onBack={vi.fn()}
        onTripDeleted={vi.fn()}
      />
    );

    // Initial state: canceled banner shown, nav displays "Trip canceled" disabled button
    expect(screen.getByRole('button', {name: `Trip ${canceledTrip.label} is canceled`})).toBeDisabled();
    expect(screen.queryByRole('button', {name: `Delete trip ${canceledTrip.label}`})).not.toBeInTheDocument();

    // Click "Duplicate into a new Trip"
    await user.click(screen.getByRole('button', {name: 'Duplicate into a new Trip'}));

    const dialog = screen.getByRole('dialog', {name: /duplicate into a new trip/i});
    expect(dialog).toBeInTheDocument();

    // Submit duplication
    const submitBtn = within(dialog).getByRole('button', {name: 'Duplicate trip'});
    await user.click(submitBtn);

    // Workspace transitions to the new trip
    await waitFor(() => {
      expect(screen.queryByRole('dialog', {name: /duplicate into a new trip/i})).not.toBeInTheDocument();
      expect(screen.getByRole('heading', {name: duplicatedTrip.label})).toBeInTheDocument();
    });

    // The new duplicated trip must offer "Delete trip", NOT "Cancel trip"
    expect(screen.getByRole('button', {name: `Delete trip ${duplicatedTrip.label}`})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: `Cancel trip ${duplicatedTrip.label}`})).not.toBeInTheDocument();
  });

  // Test 14: Modal dialogs do not dismiss on Escape or backdrop click when pending
  it('prevents Escape key and backdrop dismissal while pending in cancellation dialogs', async () => {
    const handleClose = vi.fn();
    const user = userEvent.setup();

    const {rerender} = render(
      <CancelBookingModal
        isOpen={true}
        tripLabel="San Francisco"
        bookingReference="DT-TEST01"
        pending={true}
        onClose={handleClose}
        onConfirm={vi.fn()}
      />
    );

    await user.keyboard('{Escape}');
    expect(handleClose).not.toHaveBeenCalled();

    rerender(
      <CancelTripModal
        isOpen={true}
        tripLabel="San Francisco"
        hasActiveBooking={false}
        pending={true}
        onClose={handleClose}
        onConfirm={vi.fn()}
      />
    );

    await user.keyboard('{Escape}');
    expect(handleClose).not.toHaveBeenCalled();

    rerender(
      <PostCancellationTriageModal
        isOpen={true}
        plannedAlternatives={[]}
        pending={true}
        onUseAlternative={vi.fn()}
        onCreateDraft={vi.fn()}
        onClose={handleClose}
      />
    );

    await user.keyboard('{Escape}');
    expect(handleClose).not.toHaveBeenCalled();
  });

  // Test 15: BookingHistorySection displays error message when fetch fails
  it('BookingHistorySection displays error message when fetch fails', async () => {
    vi.spyOn(tripsApi, 'getBookingHistory').mockRejectedValueOnce(new Error('Network connection lost.')).mockResolvedValueOnce([]);

    render(<BookingHistorySection tripId="trip-err" />);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Network connection lost.');
    });
    await userEvent.setup().click(screen.getByRole('button', {name: 'Retry booking history'}));
    expect(await screen.findByText('No booking history yet.')).toBeInTheDocument();
  });

  // Test 16: TripListSection displays "Trip canceled" even if hasBookingHistory is false
  it('TripListSection displays Trip canceled even if hasBookingHistory is false on a canceled trip', () => {
    const canceledSummary: TripProfileSummary = {
      id: 'trip-c1',
      destinationKey: 'destination-sfo',
      destinationName: 'San Francisco',
      startDate: '2027-03-10',
      endDate: '2027-03-14',
      label: 'Canceled SFO',
      version: 1,
      temporalStatus: 'UPCOMING',
      status: 'CANCELED',
      draftCount: 0,
      plannedCount: 0,
      expiredAlternativeCount: 0,
      bookedCount: 0,
      hasBookingHistory: false,
      alternatives: [],
    };

    render(
      <TripListSection
        upcoming={[canceledSummary]}
        past={[]}
        onSelectTrip={vi.fn()}
        onDeleteTrip={vi.fn()}
      />
    );

    expect(screen.getByRole('button', {name: 'Trip Canceled SFO is canceled'})).toBeDisabled();
    expect(screen.queryByRole('button', {name: 'Delete trip Canceled SFO'})).not.toBeInTheDocument();
  });
});
