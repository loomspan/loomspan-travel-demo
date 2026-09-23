import {describe, expect, it, vi, beforeEach} from 'vitest';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {BookingConfirmationView} from './BookingConfirmationView';
import type {TripResponse, BookingResponse} from '../api/tripsApi';

function createMockTrip(): TripResponse {
  return {
    id: 'trip-101',
    destinationKey: 'destination-sfo',
    destinationName: 'San Francisco',
    originAirportCode: 'PDX',
    startDate: '2027-03-10',
    endDate: '2027-03-14',
    travelerCount: 2,
    travelerAges: [30, 28],
    budgetCents: 300000,
    label: 'San Francisco — Mar 10–14, 2027',
    version: 2,
    drafts: [],
    planned: [],
    alternatives: [],
    revisionSummary: null,
  };
}

function createMockBooking(includeAllComponents = true): BookingResponse {
  return {
    id: 'booking-abc-123',
    tripId: 'trip-101',
    plannedItineraryId: 'planned-1',
    bookingReference: 'DT-K8M2P4',
    status: 'ACTIVE',
    grandTotalCents: 191850,
    idempotencyKey: 'idemp-uuid-1',
    bookedAt: '2027-01-15T12:00:00Z',
    canceledAt: null,
    airfareReference: 'FL-W3X8PL',
    stayReference: includeAllComponents ? 'HT-4M9Q2P' : null,
    rentalReference: includeAllComponents ? 'RC-7T1N5V' : null,
    selections: {
      airfare: {
        outboundFlightInstanceId: 10,
        returnFlightInstanceId: 43,
        outboundDescription: 'Flight CS101',
        returnDescription: 'Flight CS102',
        outboundBaseFareCents: 16500,
        outboundTaxCents: 1410,
        outboundFeeCents: 390,
        returnBaseFareCents: 16500,
        returnTaxCents: 1410,
        returnFeeCents: 390,
      },
      stay: includeAllComponents
        ? {
            accommodationUnitId: 2,
            unitCount: 1,
            propertyName: 'Harbor Light Hotel',
            unitName: 'King Deluxe Room',
            nights: [],
          }
        : null,
      rental: includeAllComponents
        ? {
            rentalUnitId: 1,
            pickupAt: '2027-03-10T10:00:00Z',
            returnAt: '2027-03-14T10:00:00Z',
            locationName: 'SFO Airport',
            vehicleClassName: 'Compact Sedan',
            unitIdentifier: 'CS-101',
            dailyBasePriceCents: 5000,
            dailyTaxCents: 500,
            dailyFeeCents: 200,
          }
        : null,
    },
    tally: {
      airfareTotalCents: 74000,
      stayTotalCents: includeAllComponents ? 91600 : 0,
      rentalTotalCents: includeAllComponents ? 26250 : 0,
      grandTotalCents: includeAllComponents ? 191850 : 74000,
      remainingBudgetCents: 108150,
      budgetOverageCents: 0,
      isOverBudget: false,
    },
  };
}

describe('BookingConfirmationView', () => {
  let writeTextSpy: any;

  beforeEach(() => {
    if (!navigator.clipboard) {
      Object.defineProperty(navigator, 'clipboard', {
        value: {writeText: () => Promise.resolve()},
        writable: true,
        configurable: true,
      });
    }
    writeTextSpy = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);
  });

  it('renders master booking reference with aria-live polite announcement and success banner', () => {
    const trip = createMockTrip();
    const booking = createMockBooking(true);

    render(
      <BookingConfirmationView
        trip={trip}
        booking={booking}
        onViewInWorkspace={() => {}}
        onViewAllTrips={() => {}}
      />
    );

    // Live status announcement
    const statusRegion = screen.getByRole('status');
    expect(statusRegion).toHaveAttribute('aria-live', 'polite');
    expect(statusRegion).toHaveTextContent('Booking confirmed! Reference: DT-K8M2P4');

    // Header & reference code
    expect(screen.getByRole('heading', {name: /booking confirmed!/i})).toBeInTheDocument();
    expect(screen.getByText('DT-K8M2P4')).toBeInTheDocument();
  });

  it('renders all component reference codes with copy buttons when components are booked', () => {
    const trip = createMockTrip();
    const booking = createMockBooking(true);

    render(
      <BookingConfirmationView
        trip={trip}
        booking={booking}
        onViewInWorkspace={() => {}}
        onViewAllTrips={() => {}}
      />
    );

    expect(screen.getByText('Airline Record Locator')).toBeInTheDocument();
    expect(screen.getByText('FL-W3X8PL')).toBeInTheDocument();

    expect(screen.getByText('Accommodation Confirmation')).toBeInTheDocument();
    expect(screen.getByText('HT-4M9Q2P')).toBeInTheDocument();

    expect(screen.getByText('Rental Confirmation')).toBeInTheDocument();
    expect(screen.getByText('RC-7T1N5V')).toBeInTheDocument();

    const copyButtons = screen.getAllByRole('button', {name: /copy/i});
    expect(copyButtons.length).toBe(3);
  });

  it('omits component reference rows for unselected optional components', () => {
    const trip = createMockTrip();
    const booking = createMockBooking(false); // Airfare only

    render(
      <BookingConfirmationView
        trip={trip}
        booking={booking}
        onViewInWorkspace={() => {}}
        onViewAllTrips={() => {}}
      />
    );

    expect(screen.getByText('Airline Record Locator')).toBeInTheDocument();
    expect(screen.getByText('FL-W3X8PL')).toBeInTheDocument();

    expect(screen.queryByText('Accommodation Confirmation')).not.toBeInTheDocument();
    expect(screen.queryByText('HT-4M9Q2P')).not.toBeInTheDocument();

    expect(screen.queryByText('Rental Confirmation')).not.toBeInTheDocument();
    expect(screen.queryByText('RC-7T1N5V')).not.toBeInTheDocument();
  });

  it('displays mandatory simulated booking disclosure with role="note"', () => {
    const trip = createMockTrip();
    const booking = createMockBooking(true);

    render(
      <BookingConfirmationView
        trip={trip}
        booking={booking}
        onViewInWorkspace={() => {}}
        onViewAllTrips={() => {}}
      />
    );

    const note = screen.getByRole('note', {name: /simulated booking disclosure/i});
    expect(note).toBeInTheDocument();
    expect(note).toHaveTextContent(
      'This was a simulated reservation using fictional inventory. No credit card was charged and no live supplier booking was made.'
    );
  });

  it('copies confirmation code to clipboard on button click and provides visual confirmation', async () => {
    const user = userEvent.setup();
    const writeSpy = vi.spyOn(navigator.clipboard, 'writeText');
    const trip = createMockTrip();
    const booking = createMockBooking(true);

    render(
      <BookingConfirmationView
        trip={trip}
        booking={booking}
        onViewInWorkspace={() => {}}
        onViewAllTrips={() => {}}
      />
    );

    const copyAirfareBtn = screen.getByRole('button', {name: /copy airline record locator fl-w3x8pl/i});
    await user.click(copyAirfareBtn);

    expect(writeSpy).toHaveBeenCalledWith('FL-W3X8PL');
    expect(screen.getByText('Copied!')).toBeInTheDocument();
  });

  it('navigates to workspace and all trips via action buttons', async () => {
    const user = userEvent.setup();
    const trip = createMockTrip();
    const booking = createMockBooking(true);
    const onViewInWorkspace = vi.fn();
    const onViewAllTrips = vi.fn();

    render(
      <BookingConfirmationView
        trip={trip}
        booking={booking}
        onViewInWorkspace={onViewInWorkspace}
        onViewAllTrips={onViewAllTrips}
      />
    );

    const workspaceBtn = screen.getByRole('button', {name: /view in trip workspace/i});
    await user.click(workspaceBtn);
    expect(onViewInWorkspace).toHaveBeenCalledTimes(1);

    const allTripsBtn = screen.getByRole('button', {name: /view all trips/i});
    await user.click(allTripsBtn);
    expect(onViewAllTrips).toHaveBeenCalledTimes(1);
  });

  it('displays itemized cost breakdown and authoritative grand total', () => {
    const trip = createMockTrip();
    const booking = createMockBooking(true);

    render(
      <BookingConfirmationView
        trip={trip}
        booking={booking}
        onViewInWorkspace={() => {}}
        onViewAllTrips={() => {}}
      />
    );

    expect(screen.getByText('Cost Breakdown')).toBeInTheDocument();
    expect(screen.getByText('$740.00')).toBeInTheDocument();
    expect(screen.getByText('$916.00')).toBeInTheDocument();
    expect(screen.getByText('$262.50')).toBeInTheDocument();
    expect(screen.getByText('$1,918.50')).toBeInTheDocument();
  });

  it('gracefully handles clipboard write failures without showing false Copied feedback', async () => {
    const user = userEvent.setup();
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('Permission denied'));
    const trip = createMockTrip();
    const booking = createMockBooking(true);

    render(
      <BookingConfirmationView
        trip={trip}
        booking={booking}
        onViewInWorkspace={() => {}}
        onViewAllTrips={() => {}}
      />
    );

    const copyAirfareBtn = screen.getByRole('button', {name: /copy airline record locator fl-w3x8pl/i});
    await user.click(copyAirfareBtn);

    expect(screen.queryByText('Copied!')).not.toBeInTheDocument();
    expect(copyAirfareBtn).toHaveTextContent('Copy');
  });
});
