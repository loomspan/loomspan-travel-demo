import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {TripWorkspace} from './components/TripWorkspace';
import {ItineraryComparisonView} from './components/ItineraryComparisonView';
import {BookingReviewView} from './components/BookingReviewView';
import {tripsApi, type TripResponse, type AlternativeResponse, type BookingResponse} from './api/tripsApi';
import {IdentityApiError} from './api/identityApi';

function createMockPlannedAlternative(
  id: string,
  overrides: Partial<AlternativeResponse> = {}
): AlternativeResponse {
  return {
    id,
    lifecycle: 'PLANNED',
    version: null,
    selections: {
      airfare: {
        outboundFlightInstanceId: 101,
        returnFlightInstanceId: 102,
        outboundDescription: 'PDX -> SFO Nonstop',
        returnDescription: 'SFO -> PDX Nonstop',
        outboundBaseFareCents: 15000,
        outboundTaxCents: 2000,
        outboundFeeCents: 1000,
        returnBaseFareCents: 16000,
        returnTaxCents: 2000,
        returnFeeCents: 1000,
        outboundCarrierName: 'SkyWays',
        outboundFlightNumber: 'SW-101',
        outboundStopCount: 0,
        outboundLayoverAirportCode: null,
        outboundLayoverDurationMinutes: null,
        outboundDepartureTime: '2027-03-10T08:00:00Z',
        outboundArrivalTime: '2027-03-10T10:15:00Z',
        outboundDepartureTimeZone: 'America/Los_Angeles',
        outboundArrivalTimeZone: 'America/Los_Angeles',
        outboundDurationMinutes: 135,
        returnCarrierName: 'SkyWays',
        returnFlightNumber: 'SW-202',
        returnStopCount: 0,
        returnLayoverAirportCode: null,
        returnLayoverDurationMinutes: null,
        returnDepartureTime: '2027-03-14T17:00:00Z',
        returnArrivalTime: '2027-03-14T19:20:00Z',
        returnDepartureTimeZone: 'America/Los_Angeles',
        returnArrivalTimeZone: 'America/Los_Angeles',
        returnDurationMinutes: 140,
        totalDurationMinutes: 275,
      },
      stay: {
        accommodationUnitId: 201,
        unitCount: 1,
        propertyName: 'Pacific Heights Inn',
        unitName: 'Deluxe King Room',
        propertyCategory: 'HOTEL',
        locationDescription: 'Pacific Heights, San Francisco',
        distanceToCityCenterMeters: 2500,
        guestCapacity: 2,
        requiredRoomCount: 1,
        nights: [
          {date: '2027-03-10', basePriceCents: 18000, taxCents: 2500, feeCents: 1000},
          {date: '2027-03-11', basePriceCents: 18000, taxCents: 2500, feeCents: 1000},
          {date: '2027-03-12', basePriceCents: 20000, taxCents: 2800, feeCents: 1000},
          {date: '2027-03-13', basePriceCents: 20000, taxCents: 2800, feeCents: 1000},
        ],
      },
      rental: {
        rentalUnitId: 301,
        pickupAt: '2027-03-10T11:00:00Z',
        returnAt: '2027-03-14T16:00:00Z',
        locationName: 'SFO Airport Terminal 2',
        vehicleClassName: 'Standard Sedan',
        unitIdentifier: 'SEDAN-01',
        dailyBasePriceCents: 4500,
        dailyTaxCents: 500,
        dailyFeeCents: 250,
        vehicleCategory: 'SEDAN',
      },
      ...overrides.selections,
    },
    tally: {
      airfareTotalCents: 74000,
      stayTotalCents: 91600,
      rentalTotalCents: 26250,
      grandTotalCents: 191850,
      remainingBudgetCents: 8150,
      budgetOverageCents: 0,
      isOverBudget: false,
      ...overrides.tally,
    },
    ...overrides,
  };
}

function createMockTripWithPlanned(plannedCount: number): TripResponse {
  const alternatives: AlternativeResponse[] = [];
  const planned: Array<{id: string; selections: any; tally: any}> = [];

  for (let i = 1; i <= plannedCount; i++) {
    const isOver = i === 2;
    const isThird = i === 3;
    const alt = createMockPlannedAlternative(`planned-${i}`, {
      selections: {
        airfare: {
          outboundFlightInstanceId: 100 + i,
          returnFlightInstanceId: 200 + i,
          outboundDescription: `PDX -> SFO Flight ${i}`,
          returnDescription: `SFO -> PDX Return ${i}`,
          outboundBaseFareCents: 15000,
          outboundTaxCents: 2000,
          outboundFeeCents: 1000,
          returnBaseFareCents: 16000,
          returnTaxCents: 2000,
          returnFeeCents: 1000,
          outboundCarrierName: i % 2 === 0 ? 'Coastal Air' : 'SkyWays',
          outboundFlightNumber: `CA-${100 + i}`,
          outboundStopCount: i === 2 ? 1 : 0,
          outboundLayoverAirportCode: i === 2 ? 'SEA' : null,
          outboundLayoverDurationMinutes: i === 2 ? 75 : null,
          outboundDepartureTime: '2027-03-10T08:00:00Z',
          outboundArrivalTime: '2027-03-10T10:15:00Z',
          outboundDepartureTimeZone: 'America/Los_Angeles',
          outboundArrivalTimeZone: 'America/Los_Angeles',
          outboundDurationMinutes: 135,
          returnCarrierName: i % 2 === 0 ? 'Coastal Air' : 'SkyWays',
          returnFlightNumber: `CA-${200 + i}`,
          returnStopCount: 0,
          returnLayoverAirportCode: null,
          returnLayoverDurationMinutes: null,
          returnDepartureTime: '2027-03-14T17:00:00Z',
          returnArrivalTime: '2027-03-14T19:20:00Z',
          returnDepartureTimeZone: 'America/Los_Angeles',
          returnArrivalTimeZone: 'America/Los_Angeles',
          returnDurationMinutes: 140,
          totalDurationMinutes: 275,
        },
        stay: {
          accommodationUnitId: 200 + i,
          unitCount: 1,
          propertyName: i === 2 ? 'Boutique B&B Nob Hill' : 'Pacific Heights Inn',
          unitName: `Suite ${i}`,
          propertyCategory: i === 2 ? 'BED_AND_BREAKFAST' : isThird ? 'VACATION_RENTAL' : 'HOTEL',
          locationDescription: 'San Francisco, CA',
          distanceToCityCenterMeters: 1800,
          guestCapacity: 2,
          requiredRoomCount: 1,
          nights: [
            {date: '2027-03-10', basePriceCents: 20000, taxCents: 2500, feeCents: 1000},
            {date: '2027-03-11', basePriceCents: 20000, taxCents: 2500, feeCents: 1000},
            {date: '2027-03-12', basePriceCents: 20000, taxCents: 2500, feeCents: 1000},
            {date: '2027-03-13', basePriceCents: 20000, taxCents: 2500, feeCents: 1000},
          ],
        },
        rental: i === 2 ? null : {
          rentalUnitId: 300 + i,
          pickupAt: '2027-03-10T11:00:00Z',
          returnAt: '2027-03-14T16:00:00Z',
          locationName: 'SFO Airport Terminal 2',
          vehicleClassName: isThird ? 'Compact SUV' : 'Standard Sedan',
          unitIdentifier: `CAR-${i}`,
          dailyBasePriceCents: 4500,
          dailyTaxCents: 500,
          dailyFeeCents: 250,
          vehicleCategory: 'SEDAN',
        },
      },
      tally: isOver
        ? {
            airfareTotalCents: 74000,
            stayTotalCents: 140000,
            rentalTotalCents: 0,
            grandTotalCents: 214000,
            remainingBudgetCents: 0,
            budgetOverageCents: 14000,
            isOverBudget: true,
          }
        : {
            airfareTotalCents: 74000,
            stayTotalCents: 91600,
            rentalTotalCents: 26250,
            grandTotalCents: 191850,
            remainingBudgetCents: 8150,
            budgetOverageCents: 0,
            isOverBudget: false,
          },
    });

    alternatives.push(alt);
    planned.push({id: alt.id, selections: alt.selections, tally: alt.tally});
  }

  return {
    id: 'trip-comp-1',
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
    drafts: [],
    planned,
    alternatives,
    revisionSummary: null,
    tally: planned[0]?.tally,
  };
}

describe('Itinerary Comparison and Booking Selection', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    document.cookie = 'detour_session=valid-token; path=/';
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.cookie = 'detour_session=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
  });

  // AC 1: Enforces 2-to-3 planned alternative selection constraint and enables comparison launch
  it('enforces 2-to-3 planned alternative selection constraint and enables comparison launch', async () => {
    const user = userEvent.setup();
    const trip = createMockTripWithPlanned(3);

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    // Initial state: Compare button should exist and be disabled (0 selected)
    const compareBtn = screen.getByRole('button', {name: /compare selected itineraries/i});
    expect(compareBtn).toBeDisabled();

    // Checkboxes for each planned alternative
    const compareCheckboxes = screen.getAllByRole('checkbox', {name: /select for comparison/i});
    expect(compareCheckboxes).toHaveLength(3);

    // Select 1 alternative -> still disabled
    await user.click(compareCheckboxes[0]);
    expect(compareCheckboxes[0]).toBeChecked();
    expect(compareBtn).toBeDisabled();

    // Select 2nd alternative -> enabled
    await user.click(compareCheckboxes[1]);
    expect(compareCheckboxes[1]).toBeChecked();
    expect(compareBtn).toBeEnabled();

    // Select 3rd alternative -> remains enabled
    await user.click(compareCheckboxes[2]);
    expect(compareCheckboxes[2]).toBeChecked();
    expect(compareBtn).toBeEnabled();
  });

  // AC 1: Enforces 2-to-3 constraint, blocks selecting > 3 with alert, and enables clearing
  it('enforces 2-to-3 planned alternative selection constraint and prevents selecting more than 3 with accessible alert', async () => {
    const user = userEvent.setup();
    const trip = createMockTripWithPlanned(4);

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    const compareCheckboxes = screen.getAllByRole('checkbox', {name: /select for comparison/i});
    expect(compareCheckboxes).toHaveLength(4);

    // Select 3 alternatives
    await user.click(compareCheckboxes[0]);
    await user.click(compareCheckboxes[1]);
    await user.click(compareCheckboxes[2]);

    expect(compareCheckboxes[0]).toBeChecked();
    expect(compareCheckboxes[1]).toBeChecked();
    expect(compareCheckboxes[2]).toBeChecked();

    // Attempt to select 4th alternative -> should be blocked with an alert
    await user.click(compareCheckboxes[3]);
    expect(compareCheckboxes[3]).not.toBeChecked();

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent(
      /you can compare at most 3 itineraries at once\. deselect one before adding another\./i
    );

    // Deselect one alternative -> alert clears and count returns to 2
    await user.click(compareCheckboxes[0]);
    expect(compareCheckboxes[0]).not.toBeChecked();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    // Now 4th alternative can be selected
    await user.click(compareCheckboxes[3]);
    expect(compareCheckboxes[3]).toBeChecked();

    // Test Clear comparison selection
    const clearBtn = screen.getByRole('button', {name: /clear comparison selection/i});
    await user.click(clearBtn);
    expect(compareCheckboxes[1]).not.toBeChecked();
    expect(compareCheckboxes[2]).not.toBeChecked();
    expect(compareCheckboxes[3]).not.toBeChecked();
    expect(screen.getByRole('button', {name: /compare selected itineraries/i})).toBeDisabled();
  });

  // AC 2 & 3: Desktop side-by-side comparison matrix with complete attribute columns
  it('renders desktop side-by-side comparison matrix with complete attribute columns for 2 or 3 alternatives', async () => {
    const user = userEvent.setup();
    const trip = createMockTripWithPlanned(2);

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    // Select 2 alternatives and launch comparison
    const checkboxes = screen.getAllByRole('checkbox', {name: /select for comparison/i});
    await user.click(checkboxes[0]);
    await user.click(checkboxes[1]);

    const compareBtn = screen.getByRole('button', {name: /compare selected itineraries/i});
    await user.click(compareBtn);

    // Comparison view rendered
    expect(screen.getByRole('heading', {name: /comparing 2 planned alternatives/i})).toBeInTheDocument();

    // Semantic grid / table elements
    const grid = screen.getByRole('grid', {name: /itinerary comparison table/i});
    expect(grid).toBeInTheDocument();

    // Column headers for each alternative
    expect(within(grid).getByText(/planned #1/i)).toBeInTheDocument();
    expect(within(grid).getByText(/planned #2/i)).toBeInTheDocument();

    // Authoritative totals & budget badges
    expect(within(grid).getAllByText('$1,918.50').length).toBeGreaterThan(0);
    expect(within(grid).getAllByText('$2,140.00').length).toBeGreaterThan(0);
    expect(within(grid).getAllByText(/within budget/i).length).toBeGreaterThan(0);
    expect(within(grid).getAllByText(/over budget/i).length).toBeGreaterThan(0);

    // Flight details
    expect(within(grid).getAllByText(/pdx -> sfo/i).length).toBeGreaterThan(0);
    expect(within(grid).getAllByText(/coastal air/i).length).toBeGreaterThan(0);
    expect(within(grid).getByText(/1 stop \(sea, 75m\)/i)).toBeInTheDocument();
    expect(within(grid).getAllByText(/nonstop/i).length).toBeGreaterThan(0);

    // Stay details: property names, category badges, rooms, distances
    expect(within(grid).getByText('Pacific Heights Inn')).toBeInTheDocument();
    expect(within(grid).getByText('Boutique B&B Nob Hill')).toBeInTheDocument();
    expect(within(grid).getByText('BED_AND_BREAKFAST')).toBeInTheDocument();
    expect(within(grid).getAllByText(/1\.8 km to city center/i).length).toBeGreaterThan(0);

    // Rental car details: vehicle class, pickup/return, cycles
    expect(within(grid).getByText('Standard Sedan')).toBeInTheDocument();
    expect(within(grid).getByText(/5 days \(24-hour cycles\)/i)).toBeInTheDocument();
  });

  // AC 2 & 6: Mobile comparison layout with persistent keyboard-accessible switcher tab bar and announcements
  it('renders mobile comparison layout with persistent keyboard-accessible alternative switcher tab bar and announcements', async () => {
    const user = userEvent.setup();
    const trip = createMockTripWithPlanned(3);

    const handleBack = vi.fn();
    const handleSelectReview = vi.fn();

    render(
      <ItineraryComparisonView
        trip={trip}
        alternatives={trip.alternatives}
        onBack={handleBack}
        onSelectForBookingReview={handleSelectReview}
      />
    );

    // Mobile tablist with 3 tabs
    const tablist = screen.getByRole('tablist', {name: /compared itineraries/i});
    expect(tablist).toBeInTheDocument();

    const tabs = within(tablist).getAllByRole('tab');
    expect(tabs).toHaveLength(3);

    // Initial state: Tab 1 selected
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true');
    expect(tabs[1]).toHaveAttribute('aria-selected', 'false');
    expect(tabs[2]).toHaveAttribute('aria-selected', 'false');

    // Live region announces Tab 1
    const liveRegion = screen.getByRole('status');
    expect(liveRegion).toHaveTextContent(/showing itinerary 1 of 3: planned planned-/i);

    // Focus first tab and use ArrowRight key
    tabs[0].focus();
    await user.keyboard('{ArrowRight}');
    expect(tabs[1]).toHaveAttribute('aria-selected', 'true');
    expect(liveRegion).toHaveTextContent(/showing itinerary 2 of 3: planned planned-/i);

    // ArrowRight again moves to Tab 3
    await user.keyboard('{ArrowRight}');
    expect(tabs[2]).toHaveAttribute('aria-selected', 'true');
    expect(liveRegion).toHaveTextContent(/showing itinerary 3 of 3: planned planned-/i);

    // ArrowRight wraps to Tab 1
    await user.keyboard('{ArrowRight}');
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true');

    // ArrowLeft wraps backwards to Tab 3
    await user.keyboard('{ArrowLeft}');
    expect(tabs[2]).toHaveAttribute('aria-selected', 'true');

    // Home key jumps to Tab 1
    await user.keyboard('{Home}');
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true');

    // End key jumps to Tab 3
    await user.keyboard('{End}');
    expect(tabs[2]).toHaveAttribute('aria-selected', 'true');
  });

  // AC 4: Missing optional components are visually and semantically distinguished from zero-cost selections
  it('visually and semantically distinguishes missing optional components from zero-cost selections', async () => {
    const trip = createMockTripWithPlanned(2);
    // Alternative 2 has selections.rental === null
    expect(trip.alternatives[1].selections.rental).toBeNull();

    render(
      <ItineraryComparisonView
        trip={trip}
        alternatives={trip.alternatives}
        onBack={() => {}}
        onSelectForBookingReview={() => {}}
      />
    );

    // Missing rental car indicator
    const missingIndicators = screen.getAllByLabelText(/no rental car selected/i);
    expect(missingIndicators.length).toBeGreaterThan(0);
    expect(missingIndicators[0]).toHaveClass('missing-component');
    expect(missingIndicators[0]).toHaveTextContent(/— No rental car selected/i);

    // The present rental car on Alternative 1 displays its actual total price, not missing indicator
    expect(screen.getAllByText('$262.50').length).toBeGreaterThan(0);
  });

  // AC 5: Transitions from comparison matrix to booking review screen with complete snapshots, disclosure, and disabled Phase 6 action
  it('transitions from comparison matrix to booking review screen with complete snapshots, itemized totals, disclosure, and disabled Phase 6 action', async () => {
    const user = userEvent.setup();
    const trip = createMockTripWithPlanned(2);

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    // Select alternatives and enter comparison view
    const checkboxes = screen.getAllByRole('checkbox', {name: /select for comparison/i});
    await user.click(checkboxes[0]);
    await user.click(checkboxes[1]);
    await user.click(screen.getByRole('button', {name: /compare selected itineraries/i}));

    // In comparison view, click "Select for Booking Review" on Alternative 1
    const reviewButtons = screen.getAllByRole('button', {name: /select alternative planned-1 for booking review/i});
    await user.click(reviewButtons[0]);

    // Booking Review View rendered
    expect(screen.getByRole('heading', {name: /review itinerary & component snapshots/i})).toBeInTheDocument();

    // Trip parameters displayed
    expect(screen.getByText(/destination/i)).toBeInTheDocument();
    expect(screen.getByText(/san francisco \(pdx → sfo\)/i)).toBeInTheDocument();
    expect(screen.getByText(/2027-03-10 to 2027-03-14/i)).toBeInTheDocument();
    expect(screen.getByText(/2 travelers \(ages: 30, 28\)/i)).toBeInTheDocument();

    // Component snapshots displayed
    expect(screen.getByRole('heading', {name: /airfare snapshot/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /stay snapshot/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /rental car snapshot/i})).toBeInTheDocument();

    // Itemized and grand total
    expect(screen.getAllByText('$740.00').length).toBeGreaterThan(0); // Airfare
    expect(screen.getAllByText('$916.00').length).toBeGreaterThan(0); // Stay
    expect(screen.getAllByText('$262.50').length).toBeGreaterThan(0); // Rental
    expect(screen.getByText('$1,918.50')).toBeInTheDocument(); // Grand total

    // Mandatory fictional disclosure notice
    const disclosureNote = screen.getByRole('note', {name: /fictional inventory disclosure/i});
    expect(disclosureNote).toBeInTheDocument();
    expect(disclosureNote).toHaveTextContent(
      'This is a simulated booking with fictional inventory. No real payment, billing address, or external reservation is required.'
    );

    // Active Confirm Booking action
    const confirmBtn = screen.getByRole('button', {name: /confirm booking/i});
    expect(confirmBtn).toBeEnabled();
    expect(screen.queryByText(/ready for phase 6 simulated booking implementation\./i)).not.toBeInTheDocument();

    // Return back to comparison view
    const backBtn = screen.getByRole('button', {name: /← back to comparison/i});
    await user.click(backBtn);
    expect(screen.getByRole('heading', {name: /comparing 2 planned alternatives/i})).toBeInTheDocument();
  });

  // AC 5: Transitions from standalone planned alternative card to booking review screen and returns to workspace
  it('transitions from standalone planned alternative card to booking review screen and returns to workspace', async () => {
    const user = userEvent.setup();
    const trip = createMockTripWithPlanned(1);

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    // Standalone planned card has "Select for Booking Review"
    const selectBtn = screen.getByRole('button', {name: /select planned itinerary planned-1 for booking review/i});
    expect(selectBtn).toBeInTheDocument();
    await user.click(selectBtn);

    // Booking Review View rendered
    expect(screen.getByRole('heading', {name: /review itinerary & component snapshots/i})).toBeInTheDocument();

    // Back button says "← Back to Trip Workspace"
    const backBtn = screen.getByRole('button', {name: /← back to trip workspace/i});
    expect(backBtn).toBeInTheDocument();
    await user.click(backBtn);

    // Returns to standard workspace
    expect(screen.getByRole('heading', {name: /trip details & travelers/i})).toBeInTheDocument();
  });

  // AC 6: Maintains WCAG AA compliant contrast classes and accessible ARIA table/grid semantics in comparison matrix
  it('maintains WCAG AA compliant contrast classes and accessible ARIA table/grid semantics in comparison matrix', async () => {
    const trip = createMockTripWithPlanned(2);

    render(
      <ItineraryComparisonView
        trip={trip}
        alternatives={trip.alternatives}
        onBack={() => {}}
        onSelectForBookingReview={() => {}}
      />
    );

    // Within-budget badge has badge-success class
    const successBadges = screen.getAllByText(/within budget/i);
    expect(successBadges[0].closest('.badge')).toHaveClass('badge-success');

    // Over-budget badge has badge-warning class and alert role
    const overageBadges = screen.getAllByText(/over budget/i);
    const overageBadgeEl = overageBadges[0].closest('.badge');
    expect(overageBadgeEl).toHaveClass('badge-warning');
    expect(overageBadgeEl).toHaveAttribute('role', 'alert');

    // Grid table and column headers
    const grid = screen.getByRole('grid', {name: /itinerary comparison table/i});
    expect(grid).toBeInTheDocument();
    const colHeaders = within(grid).getAllByRole('columnheader');
    expect(colHeaders.length).toBeGreaterThanOrEqual(3); // Attribute label + 2 alternatives
  });

  // AC 1 & 2: Handles interactive booking submission with pending state and transitions to confirmation
  it('handles interactive booking submission with pending state and transitions to confirmation', async () => {
    const user = userEvent.setup();
    const trip = createMockTripWithPlanned(1);
    const mockBooking: BookingResponse = {
      id: 'booking-res-123',
      tripId: trip.id,
      plannedItineraryId: 'planned-1',
      bookingReference: 'DT-TEST99',
      status: 'ACTIVE',
      grandTotalCents: 191850,
      idempotencyKey: 'idemp-uuid',
      bookedAt: '2027-01-15T12:00:00Z',
      canceledAt: null,
      airfareReference: 'FL-TESTAIR',
      stayReference: 'HT-TESTSTAY',
      rentalReference: 'RC-TESTRENT',
      selections: trip.alternatives[0].selections,
      tally: trip.alternatives[0].tally!,
    };

    let resolveBooking: (val: any) => void;
    const bookingPromise = new Promise((resolve) => {
      resolveBooking = resolve;
    });

    vi.spyOn(tripsApi, 'getActiveBooking').mockRejectedValue(new IdentityApiError('api', 404, 'NOT_FOUND'));
    const createBookingSpy = vi.spyOn(tripsApi, 'createBooking').mockReturnValue(bookingPromise as any);
    vi.spyOn(tripsApi, 'getTrip').mockResolvedValue({...trip, version: trip.version + 1});

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    // Navigate to booking review
    await user.click(screen.getByRole('button', {name: /select planned itinerary planned-1 for booking review/i}));
    expect(screen.getByRole('heading', {name: /review itinerary & component snapshots/i})).toBeInTheDocument();

    // Click Confirm Booking
    const confirmBtn = screen.getByRole('button', {name: /confirm booking/i});
    await user.click(confirmBtn);

    // Verify in-flight pending state
    expect(confirmBtn).toBeDisabled();
    expect(confirmBtn).toHaveTextContent(/reserving inventory/i);
    expect(createBookingSpy).toHaveBeenCalledTimes(1);
    const callArgs = createBookingSpy.mock.calls[0];
    expect(callArgs[0]).toBe(trip.id);
    expect(callArgs[1].plannedItineraryId).toBe('planned-1');
    expect(callArgs[1].expectedVersion).toBe(trip.version);
    expect(callArgs[1].idempotencyKey).toBeDefined();

    // Resolve booking
    resolveBooking!(mockBooking);

    // Transitions to confirmation view
    await waitFor(() => {
      expect(screen.getByRole('heading', {name: /booking confirmed!/i})).toBeInTheDocument();
    });
    expect(screen.getByText('DT-TEST99')).toBeInTheDocument();
    expect(screen.getByText('FL-TESTAIR')).toBeInTheDocument();
    expect(screen.getByText('HT-TESTSTAY')).toBeInTheDocument();
    expect(screen.getByText('RC-TESTRENT')).toBeInTheDocument();
  });

  // AC 3: Handles 409 inventory conflict with accessible alert and preserved selections
  it('handles 409 inventory conflict with accessible alert and preserved selections', async () => {
    const user = userEvent.setup();
    const trip = createMockTripWithPlanned(1);

    vi.spyOn(tripsApi, 'getActiveBooking').mockRejectedValue(new IdentityApiError('api', 404, 'NOT_FOUND'));
    vi.spyOn(tripsApi, 'createBooking').mockRejectedValue(
      new IdentityApiError(
        'api',
        409,
        'INVENTORY_CONFLICT',
        {
          airfare: 'Selected flight sold out',
          stay: 'Accommodation unit no longer available',
        },
        'One or more components are no longer available.'
      )
    );

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    await user.click(screen.getByRole('button', {name: /select planned itinerary planned-1 for booking review/i}));
    const confirmBtn = screen.getByRole('button', {name: /confirm booking/i});
    await user.click(confirmBtn);

    // Accessible error alert container rendered
    const alert = await screen.findByRole('alert', {name: /reservation could not be completed/i});
    expect(alert).toBeInTheDocument();
    expect(screen.getByText(/one or more components are no longer available\./i)).toBeInTheDocument();
    expect(screen.getByText(/selected flight sold out/i)).toBeInTheDocument();
    expect(screen.getByText(/accommodation unit no longer available/i)).toBeInTheDocument();

    // User selections and snapshots remain intact
    expect(screen.getByRole('heading', {name: /airfare snapshot/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /stay snapshot/i})).toBeInTheDocument();

    // Confirm button re-enabled after error
    expect(screen.getByRole('button', {name: /confirm booking/i})).toBeEnabled();

    // Return to workspace button inside alert works
    const returnBtn = screen.getByRole('button', {name: /return to trip workspace/i});
    await user.click(returnBtn);
    expect(screen.getByRole('heading', {name: /trip details & travelers/i})).toBeInTheDocument();
  });

  // AC 4 & 5: Renders active booking banner and enforces single-booking constraint in workspace
  it('renders active booking banner and enforces single-booking constraint in workspace', async () => {
    const user = userEvent.setup();
    const trip = createMockTripWithPlanned(2);
    const mockActiveBooking: BookingResponse = {
      id: 'active-booking-01',
      tripId: trip.id,
      plannedItineraryId: 'planned-1',
      bookingReference: 'DT-ACT001',
      status: 'ACTIVE',
      grandTotalCents: 191850,
      idempotencyKey: 'idemp-uuid',
      bookedAt: '2027-01-20T10:00:00Z',
      canceledAt: null,
      airfareReference: 'FL-AIR1',
      stayReference: 'HT-STAY1',
      rentalReference: 'RC-RENT1',
      selections: trip.alternatives[0].selections,
      tally: trip.alternatives[0].tally!,
    };

    vi.spyOn(tripsApi, 'getActiveBooking').mockResolvedValue(mockActiveBooking);

    render(
      <TripWorkspace
        initialTrip={trip}
        initialActiveBooking={mockActiveBooking}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    // Active Booking banner rendered
    expect(screen.getByRole('heading', {name: /active booking/i})).toBeInTheDocument();
    expect(screen.getByText('DT-ACT001')).toBeInTheDocument();

    // Planned-1 displays BOOKED badge and View Booking Details
    const bookedBadges = screen.getAllByText('BOOKED');
    expect(bookedBadges.length).toBeGreaterThan(0);
    expect(screen.getByRole('button', {name: /view booking details for itinerary planned-1/i})).toBeInTheDocument();

    // Planned-2 has "Select for Booking Review" disabled with single-booking hint
    const reviewBtnAlt2 = screen.getByRole('button', {name: /select planned itinerary planned-2 for booking review/i});
    expect(reviewBtnAlt2).toBeDisabled();
    expect(
      screen.getByText(/this trip already has an active booking\. only one active booking is permitted per trip\./i)
    ).toBeInTheDocument();

    // Creating drafts and duplicating remains enabled
    expect(screen.getByRole('button', {name: /create empty draft/i})).toBeEnabled();
    expect(screen.getByRole('button', {name: /duplicate planned itinerary planned-2 to draft/i})).toBeEnabled();

    // Clicking "View Booking Details" navigates to confirmation view
    await user.click(screen.getByRole('button', {name: /view booking details for itinerary planned-1/i}));
    expect(screen.getByRole('heading', {name: /booking confirmed!/i})).toBeInTheDocument();
  });

  // AC 5: Disables select for booking review in comparison view when active booking exists
  it('disables select for booking review in comparison view when active booking exists', async () => {
    const trip = createMockTripWithPlanned(2);

    render(
      <ItineraryComparisonView
        trip={trip}
        alternatives={trip.alternatives}
        onBack={() => {}}
        onSelectForBookingReview={() => {}}
        hasActiveBooking={true}
      />
    );

    // All "Select for Booking Review" buttons in comparison are disabled
    const selectButtons = screen.getAllByRole('button', {name: /select alternative planned-[12] for booking review/i});
    for (const btn of selectButtons) {
      expect(btn).toBeDisabled();
    }

    // Explanatory copy displayed
    expect(
      screen.getAllByText(/this trip already has an active booking\. only one active booking is permitted per trip\./i).length
    ).toBeGreaterThan(0);
  });
});
