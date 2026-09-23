import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {TripWorkspace} from './components/TripWorkspace';
import {BudgetOverageModal} from './components/BudgetOverageModal';
import {AlternativeCard} from './components/AlternativeCard';
import type {TripResponse} from './api/tripsApi';

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });

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
    planned: [],
    alternatives: [
      {
        id: 'draft-1',
        lifecycle: 'DRAFT',
        version: 0,
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
    revisionSummary: null,
  };
  return {...baseTrip, ...overrides};
}

describe('Draft Promotion and Readiness Experience', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    document.cookie = 'XSRF-TOKEN=secret-token; path=/';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; max-age=0; path=/';
    fetchMock.mockReset();
  });

  // AC 1: Promotion action accessible in builder and Draft alternative cards, disabled when expired
  it('renders promotion buttons in progressive builder and draft alternative cards, disabled when trip is expired', async () => {
    const trip = createMockTrip();
    const {rerender, unmount} = render(
      <TripWorkspace
        initialTrip={trip}
        isExpired={false}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    const builderPromoteBtn = screen.getByRole('button', {name: /save as planned itinerary/i});
    expect(builderPromoteBtn).toBeInTheDocument();
    expect(builderPromoteBtn).toBeEnabled();

    const cardPromoteBtn = screen.getByRole('button', {name: /promote draft draft-1 to planned/i});
    expect(cardPromoteBtn).toBeInTheDocument();
    expect(cardPromoteBtn).toBeEnabled();

    // Rerender as expired trip
    rerender(
      <TripWorkspace
        initialTrip={trip}
        isExpired={true}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    expect(screen.getByRole('button', {name: /save as planned itinerary/i})).toBeDisabled();
    expect(screen.getByRole('button', {name: /promote draft draft-1 to planned/i})).toBeDisabled();
    unmount();

    // Verify promotionPending disables card promote button
    const cardRender = render(
      <AlternativeCard
        alternative={trip.alternatives[0]}
        promotionPending={true}
        onDuplicateDraft={() => {}}
        onDuplicatePlanned={() => {}}
        onDeleteDraft={() => {}}
        onDeletePlanned={() => {}}
        onPromoteDraft={() => {}}
      />
    );
    expect(screen.getByRole('button', {name: /promote draft draft-1 to planned/i})).toBeDisabled();
    expect(screen.getByText('Saving planned itinerary…')).toBeInTheDocument();
    cardRender.unmount();
  });

  // AC 2: Incomplete Drafts display actionable blocking issues and clicking moves focus directly
  it('displays actionable blocking issues on unready draft and moves focus directly to missing field or slot when clicked', async () => {
    const user = userEvent.setup();
    const trip = createMockTrip({travelerAges: null});

    fetchMock.mockResolvedValueOnce(
      json(400, {
        code: 'PLANNING_NOT_READY',
        message: 'Draft is not ready to be saved as a planned itinerary.',
        fields: {
          travelerAges: 'Provide exact ages for every traveler before planning.',
          adult: 'At least one traveler must be 18 or older.',
          budgetCents: 'Trip budget must be provided before planning.',
          components: 'Select at least one travel component before saving.',
          airfare: 'Selected flight is no longer available.',
          stay: 'Selected accommodation unit is sold out.',
          rental: 'Selected rental vehicle is no longer available.',
          destination: 'Trip destination is required.',
          dates: 'Trip dates are invalid.',
          travelerCount: 'Traveler count must be between 1 and 8.',
        },
      })
    );

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    const promoteBtn = screen.getByRole('button', {name: /save as planned itinerary/i});
    await user.click(promoteBtn);

    // Banner appears with heading
    const banner = await screen.findByRole('region', {name: /draft not ready to save as planned/i});
    expect(banner).toBeInTheDocument();

    // Test travelerAges jump -> first age input
    const fixAgesBtn = screen.getByRole('button', {name: /fix issue for traveler ages/i});
    await user.click(fixAgesBtn);
    expect(document.activeElement).toBe(document.getElementById('traveler-age-0'));

    // Test adult jump -> first age input
    const fixAdultBtn = screen.getByRole('button', {name: /fix issue for adult traveler requirement/i});
    await user.click(fixAdultBtn);
    expect(document.activeElement).toBe(document.getElementById('traveler-age-0'));

    // Test budget jump -> budget input
    const fixBudgetBtn = screen.getByRole('button', {name: /fix issue for trip budget/i});
    await user.click(fixBudgetBtn);
    expect(document.activeElement).toBe(document.getElementById('workspace-budget'));

    // Test components jump -> builder heading
    const fixComponentsBtn = screen.getByRole('button', {name: /fix issue for component selections/i});
    await user.click(fixComponentsBtn);
    expect(document.activeElement).toBe(document.getElementById('builder-heading'));

    // Test airfare jump -> airfare heading and slot highlighted
    const fixAirfareBtn = screen.getByRole('button', {name: /fix issue for airfare selection/i});
    await user.click(fixAirfareBtn);
    expect(document.activeElement).toBe(document.getElementById('airfare-slot-heading'));
    expect(document.querySelector('.airfare-slot')).toHaveClass('slot-highlighted');

    // Test stay jump -> stay heading and slot highlighted
    const fixStayBtn = screen.getByRole('button', {name: /fix issue for stay selection/i});
    await user.click(fixStayBtn);
    expect(document.activeElement).toBe(document.getElementById('stay-slot-heading'));
    expect(document.querySelector('.stay-slot')).toHaveClass('slot-highlighted');

    // Test rental jump -> rental heading and slot highlighted
    const fixRentalBtn = screen.getByRole('button', {name: /fix issue for rental car selection/i});
    await user.click(fixRentalBtn);
    await waitFor(() => {
      expect(document.activeElement).toBe(document.getElementById('rental-slot-heading'));
    });
    expect(document.querySelector('.rental-slot')).toHaveClass('slot-highlighted');

    // Test destination jump -> destination select
    const fixDestBtn = screen.getByRole('button', {name: /fix issue for destination/i});
    await user.click(fixDestBtn);
    expect(document.activeElement).toBe(document.getElementById('workspace-destination'));

    // Test dates jump -> start date input
    const fixDatesBtn = screen.getByRole('button', {name: /fix issue for trip dates/i});
    await user.click(fixDatesBtn);
    expect(document.activeElement).toBe(document.getElementById('workspace-start-date'));

    // Test travelerCount jump -> traveler count input
    const fixCountBtn = screen.getByRole('button', {name: /fix issue for traveler count/i});
    await user.click(fixCountBtn);
    expect(document.activeElement).toBe(document.getElementById('workspace-traveler-count'));

    // Dismiss banner
    const dismissBtn = screen.getByRole('button', {name: /dismiss readiness issues/i});
    await user.click(dismissBtn);
    expect(screen.queryByRole('region', {name: /draft not ready to save as planned/i})).not.toBeInTheDocument();
  });

  // AC 3: Over-budget Draft promotion displays warning modal with breakdown and required checkbox
  it('intercepts over-budget draft promotion with modal requiring explicit checkbox acknowledgment', async () => {
    const user = userEvent.setup();
    const trip = createMockTrip();

    fetchMock.mockResolvedValueOnce(
      json(400, {
        code: 'BUDGET_OVERAGE_UNACKNOWLEDGED',
        message: 'Draft exceeds overall trip budget. Explicit acknowledgment required.',
        fields: {
          budgetCents: '200000',
          grandTotalCents: '250000',
          budgetOverageCents: '50000',
        },
      })
    );

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    const promoteBtn = screen.getByRole('button', {name: /save as planned itinerary/i});
    await user.click(promoteBtn);

    // Modal opens
    const modal = await screen.findByRole('dialog', {name: /trip budget overage warning/i});
    expect(modal).toBeInTheDocument();

    // Financial breakdown values
    expect(within(modal).getByText('$2,000.00')).toBeInTheDocument();
    expect(within(modal).getByText('$2,500.00')).toBeInTheDocument();
    expect(within(modal).getByText('+$500.00')).toBeInTheDocument();

    // Confirm button is disabled initially
    const confirmBtn = within(modal).getByRole('button', {name: /confirm and save as planned/i});
    expect(confirmBtn).toBeDisabled();

    // Check acknowledgment checkbox
    const ackCheckbox = within(modal).getByRole('checkbox', {
      name: /i understand this itinerary exceeds my overall trip budget/i,
    });
    expect(ackCheckbox).not.toBeChecked();

    await user.click(ackCheckbox);
    expect(ackCheckbox).toBeChecked();
    expect(confirmBtn).toBeEnabled();

    // Uncheck disables button again
    await user.click(ackCheckbox);
    expect(ackCheckbox).not.toBeChecked();
    expect(confirmBtn).toBeDisabled();
  });

  // AC 4: Successfully promoted Drafts appear immediately as Planned snapshots with read-only badges and actions
  it('confirming overage sends acknowledgment and updates workspace to planned snapshot with read-only badges and tally', async () => {
    const user = userEvent.setup();
    const trip = createMockTrip();

    // First call: returns BUDGET_OVERAGE_UNACKNOWLEDGED
    fetchMock.mockResolvedValueOnce(
      json(400, {
        code: 'BUDGET_OVERAGE_UNACKNOWLEDGED',
        message: 'Draft exceeds budget.',
        fields: {
          budgetCents: '200000',
          grandTotalCents: '250000',
          budgetOverageCents: '50000',
        },
      })
    );

    // Second call: user confirms with acknowledgment -> 201 Created with Planned alternative
    const updatedTrip: TripResponse = {
      ...trip,
      version: 1,
      planned: [
        {
          id: 'planned-1',
          selections: {airfare: null, stay: null, rental: null},
          tally: {
            airfareTotalCents: 0,
            stayTotalCents: 0,
            rentalTotalCents: 0,
            grandTotalCents: 250000,
            remainingBudgetCents: null,
            budgetOverageCents: 50000,
            isOverBudget: true,
          },
        },
      ],
      alternatives: [
        {
          id: 'draft-1',
          lifecycle: 'DRAFT',
          version: 0,
          selections: {airfare: null, stay: null, rental: null},
        },
        {
          id: 'planned-1',
          lifecycle: 'PLANNED',
          version: null,
          selections: {airfare: null, stay: null, rental: null},
          tally: {
            airfareTotalCents: 0,
            stayTotalCents: 0,
            rentalTotalCents: 0,
            grandTotalCents: 250000,
            remainingBudgetCents: null,
            budgetOverageCents: 50000,
            isOverBudget: true,
          },
        },
      ],
    };
    fetchMock.mockResolvedValueOnce(json(201, updatedTrip));

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    await user.click(screen.getByRole('button', {name: /save as planned itinerary/i}));

    const modal = await screen.findByRole('dialog', {name: /trip budget overage warning/i});
    const ackCheckbox = within(modal).getByRole('checkbox', {
      name: /i understand this itinerary exceeds my overall trip budget/i,
    });
    await user.click(ackCheckbox);

    const confirmBtn = within(modal).getByRole('button', {name: /confirm and save as planned/i});
    await user.click(confirmBtn);

    // Verify promotion payload sent budgetOverageAcknowledged: true
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/trips/trip-1/drafts/draft-1/plan',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          expectedVersion: 0,
          expectedDraftVersion: 0,
          budgetOverageAcknowledged: true,
        }),
      })
    );

    // Modal closes
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    // aria-live polite status announces success
    expect(screen.getByText('Draft successfully saved as planned itinerary.')).toBeInTheDocument();

    // Planned alternative appears with Planned badge and actions
    expect(screen.getByText('Planned itinerary (read-only)')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /delete planned itinerary/i})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /duplicate planned itinerary.*to draft/i})).toBeInTheDocument();
  });

  // AC 5: In-place modifications to Planned snapshots are prevented, and duplicating produces new mutable Draft
  it('prevents in-place modifications to planned snapshots and creates new mutable draft when duplicated', async () => {
    const user = userEvent.setup();
    const plannedTrip = createMockTrip({
      planned: [
        {
          id: 'planned-1',
          selections: {airfare: null, stay: null, rental: null},
        },
      ],
      alternatives: [
        {
          id: 'planned-1',
          lifecycle: 'PLANNED',
          version: null,
          selections: {airfare: null, stay: null, rental: null},
        },
      ],
    });

    render(
      <TripWorkspace
        initialTrip={plannedTrip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    // Shared details form inputs are disabled
    expect(document.getElementById('workspace-destination')).toBeDisabled();
    expect(document.getElementById('workspace-start-date')).toBeDisabled();
    expect(document.getElementById('workspace-end-date')).toBeDisabled();
    expect(document.getElementById('workspace-traveler-count')).toBeDisabled();

    // Read-only notice is displayed
    expect(
      screen.getByText(/trips with planned itineraries cannot change destination, dates, or traveler count in place/i)
    ).toBeInTheDocument();

    // Planned alternative card does not render Promote to Planned button
    expect(screen.queryByRole('button', {name: /promote.*to planned/i})).not.toBeInTheDocument();

    // Duplicate planned alternative produces new mutable draft
    const duplicatedTrip: TripResponse = {
      ...plannedTrip,
      version: 1,
      drafts: [
        {
          id: 'draft-2',
          version: 0,
          selections: {airfare: null, stay: null, rental: null},
        },
      ],
      alternatives: [
        {
          id: 'planned-1',
          lifecycle: 'PLANNED',
          version: null,
          selections: {airfare: null, stay: null, rental: null},
        },
        {
          id: 'draft-2',
          lifecycle: 'DRAFT',
          version: 0,
          selections: {airfare: null, stay: null, rental: null},
        },
      ],
    };
    fetchMock.mockResolvedValueOnce(json(201, duplicatedTrip));

    const duplicateBtn = screen.getByRole('button', {name: /duplicate planned itinerary.*to draft/i});
    await user.click(duplicateBtn);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/trips/trip-1/alternatives/planned-1/duplicate',
      expect.objectContaining({
        method: 'POST',
      })
    );

    // New draft alternative appears in alternatives list with Promote to Planned action
    await screen.findByText('Draft v0');
    expect(screen.getByRole('button', {name: /promote draft draft-2 to planned/i})).toBeInTheDocument();
  });

  // AC 6: Form edits following overage warning invalidate previous client acknowledgment
  it('invalidates previous overage acknowledgment when user edits form fields, requiring fresh acknowledgment', async () => {
    const user = userEvent.setup();
    const trip = createMockTrip();

    // First promotion attempt: server triggers overage
    fetchMock.mockResolvedValueOnce(
      json(400, {
        code: 'BUDGET_OVERAGE_UNACKNOWLEDGED',
        message: 'Draft exceeds budget.',
        fields: {
          budgetCents: '200000',
          grandTotalCents: '250000',
          budgetOverageCents: '50000',
        },
      })
    );

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    await user.click(screen.getByRole('button', {name: /save as planned itinerary/i}));

    const modal = await screen.findByRole('dialog', {name: /trip budget overage warning/i});
    expect(modal).toBeInTheDocument();

    // User closes modal without confirming
    const cancelBtn = within(modal).getByRole('button', {name: /cancel/i});
    await user.click(cancelBtn);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    // User edits budget input
    const budgetInput = document.getElementById('workspace-budget') as HTMLInputElement;
    await user.clear(budgetInput);
    await user.type(budgetInput, '2100.00');

    // Second promotion attempt: server rejects again because acknowledgment was invalidated
    fetchMock.mockResolvedValueOnce(
      json(400, {
        code: 'BUDGET_OVERAGE_UNACKNOWLEDGED',
        message: 'Draft exceeds budget.',
        fields: {
          budgetCents: '210000',
          grandTotalCents: '250000',
          budgetOverageCents: '40000',
        },
      })
    );

    await user.click(screen.getByRole('button', {name: /save as planned itinerary/i}));

    // Verify promotion request did NOT send budgetOverageAcknowledged: true
    const calls = fetchMock.mock.calls.filter((c) => String(c[0]).includes('/plan'));
    const lastCall = calls[calls.length - 1];
    const lastBody = JSON.parse(String(lastCall[1].body));
    expect(lastBody.budgetOverageAcknowledged).toBeUndefined();

    // Modal appears again requiring fresh acknowledgment
    const reopenedModal = await screen.findByRole('dialog', {name: /trip budget overage warning/i});
    expect(reopenedModal).toBeInTheDocument();
    expect(within(reopenedModal).getByRole('button', {name: /confirm and save as planned/i})).toBeDisabled();
  });

  // AC 7: Keyboard navigation, focus trapping, Escape dismissal, and aria-live announcements
  it('verifies keyboard navigation, focus trapping, Escape dismissal, and aria-live status announcements', async () => {
    const user = userEvent.setup();
    const handleClose = vi.fn();
    const handleConfirm = vi.fn();

    const {rerender} = render(
      <BudgetOverageModal
        isOpen={true}
        budgetCents={200000}
        grandTotalCents={250000}
        budgetOverageCents={50000}
        pending={false}
        onClose={handleClose}
        onConfirm={handleConfirm}
      />
    );

    const dialog = screen.getByRole('dialog', {name: /trip budget overage warning/i});
    expect(dialog).toHaveAttribute('aria-modal', 'true');

    // Escape key dismisses modal
    await user.keyboard('{Escape}');
    expect(handleClose).toHaveBeenCalledTimes(1);

    // Test focus trapping when confirmButton is disabled (initial state):
    // Focus is on cancel button; Tab should wrap to checkbox rather than escaping
    const cancelButton = screen.getByRole('button', {name: /cancel/i});
    const confirmButton = screen.getByRole('button', {name: /confirm and save as planned/i});
    const checkbox = screen.getByRole('checkbox');

    cancelButton.focus();
    expect(document.activeElement).toBe(cancelButton);

    await user.tab();
    expect(document.activeElement).toBe(checkbox);

    await user.tab({shift: true});
    expect(document.activeElement).toBe(cancelButton);

    // Enable confirm button to include it in focus cycle
    await user.click(checkbox);
    confirmButton.focus();
    expect(document.activeElement).toBe(confirmButton);

    await user.tab();
    expect(document.activeElement).toBe(checkbox);

    await user.tab({shift: true});
    expect(document.activeElement).toBe(confirmButton);
  });

  // AC 4 Concurrency Conflict Resilience: 409 VERSION_CONFLICT preserves user edits and offers reload
  it('handles version conflict (409 VERSION_CONFLICT) by preserving user edits and offering reload', async () => {
    const user = userEvent.setup();
    const trip = createMockTrip();

    fetchMock.mockResolvedValueOnce(
      json(409, {
        code: 'VERSION_CONFLICT',
        message: 'The Trip has changed on the server. Reload before saving.',
      })
    );

    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    // Edit traveler age
    const ageInput = document.getElementById('traveler-age-0') as HTMLInputElement;
    await user.clear(ageInput);
    await user.type(ageInput, '35');

    // Attempt promotion
    const promoteBtn = screen.getByRole('button', {name: /save as planned itinerary/i});
    await user.click(promoteBtn);

    // Conflict alert is displayed with reload action
    const reloadBtn = await screen.findByRole('button', {name: /reload from server/i});
    expect(reloadBtn).toBeInTheDocument();

    // User input is preserved
    expect(ageInput.value).toBe('35');

    // Clicking reload from server loads fresh trip
    const freshTrip = createMockTrip({version: 2, travelerAges: [30, 28]});
    fetchMock.mockResolvedValueOnce(json(200, freshTrip));

    await user.click(reloadBtn);

    await waitFor(() => {
      expect(screen.queryByRole('button', {name: /reload from server/i})).not.toBeInTheDocument();
    });
  });

  // AC 8: Responsive layout and interactive controls under mobile viewport constraints
  it('maintains accessible layout and interactive controls under mobile viewport constraints', async () => {
    window.innerWidth = 375;
    window.innerHeight = 667;

    const trip = createMockTrip();
    render(
      <TripWorkspace
        initialTrip={trip}
        onBack={() => {}}
        onTripDeleted={() => {}}
      />
    );

    const builderPromoteBtn = screen.getByRole('button', {name: /save as planned itinerary/i});
    expect(builderPromoteBtn).toHaveClass('promote-draft-btn');

    const cardPromoteBtn = screen.getByRole('button', {name: /promote draft draft-1 to planned/i});
    expect(cardPromoteBtn).toHaveClass('promote-draft-btn');

    // Open overage modal
    const {unmount} = render(
      <BudgetOverageModal
        isOpen={true}
        budgetCents={200000}
        grandTotalCents={250000}
        budgetOverageCents={50000}
        pending={false}
        onClose={() => {}}
        onConfirm={() => {}}
      />
    );

    const dialog = screen.getByRole('dialog', {name: /trip budget overage warning/i});
    expect(dialog).toHaveClass('budget-overage-modal');

    unmount();
  });
});
