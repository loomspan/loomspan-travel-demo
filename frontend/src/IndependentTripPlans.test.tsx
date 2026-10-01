import {createRef} from 'react';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {act, render, screen, within, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ProfileScreen} from './components/ProfileScreen';
import {TripWorkspace, type TripWorkspaceHandle} from './components/TripWorkspace';
import {tripsApi, type BookingResponse, type PlanResponse, type TripResponse} from './api/tripsApi';

const p = (id: string, name: string, primary = false): PlanResponse => ({id, name, primary, booked: false, version: 0,
  startDate: primary ? '2027-03-10' : '2027-03-15', endDate: primary ? '2027-03-14' : '2027-03-19',
  travelerCount: primary ? 2 : 1, travelerAges: primary ? [25, 30] : [40], selections: {airfare: null, stay: null, rental: null}, lockedComponents: [],
  tally: {airfareTotalCents: 0, stayTotalCents: 0, rentalTotalCents: 0, grandTotalCents: 0, remainingBudgetCents: null, budgetOverageCents: null, isOverBudget: false}});
function fixture(plans = [p('a', 'Preferred spring', true), p('b', 'Later getaway')]): TripResponse {
  return {id: 'trip', name: 'Spring', label: 'Spring', destinationKey: 'destination-sfo', destinationName: 'San Francisco', originAirportCode: 'PDX',
    startDate: '2027-03-10', endDate: '2027-03-14', travelerCount: 2, travelerAges: [25, 30], budgetCents: null, version: 0,
    drafts: [], planned: [], alternatives: [], revisionSummary: null, plans, primaryPlanId: plans.find(p => p.primary)!.id};
}
function setup(trip = fixture()) {
  const ref = createRef<TripWorkspaceHandle>(), back = vi.fn(), deleted = vi.fn();
  render(<TripWorkspace ref={ref} initialTrip={trip} onBack={back} onTripDeleted={deleted} />);
  return {user: userEvent.setup(), ref, back, deleted};
}
beforeEach(() => {window.history.replaceState({}, '', '/profile'); vi.spyOn(tripsApi, 'getBookingHistory').mockResolvedValue([]);});
afterEach(() => vi.restoreAllMocks());

describe('Independent plan workspace', () => {
  it('restores the comparison return target when revisiting booking review history', async () => {
    const {user} = setup();
    await user.click(screen.getByRole('tab', {name: 'Later getaway'}));
    await user.click(screen.getByRole('button', {name: 'Compare plans'}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Compare selected plans'}));
    await user.click(screen.getAllByRole('button', {name: 'Select Later getaway for booking review'})[0]);
    const comparisonReview = window.history.state;
    await user.click(screen.getByRole('button', {name: /Back to/}));
    expect(screen.getByRole('heading', {name: 'Comparing 2 plans'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /Back to/}));
    await user.click(screen.getByRole('button', {name: 'Review booking'}));
    act(() => {window.history.replaceState(comparisonReview, ''); window.dispatchEvent(new PopStateEvent('popstate'));});
    await user.click(screen.getByRole('button', {name: /Back to/}));
    expect(screen.getByRole('heading', {name: 'Comparing 2 plans'})).toBeInTheDocument();
  });

  it('keeps the saved baseline when Save is chosen after Discard edits', async () => {
    const trip = fixture();
    const fresh = {...trip, version: 1, plans: [{...trip.plans![0], version: 1, startDate: '2027-03-11'}, trip.plans![1]]};
    const save = vi.spyOn(tripsApi, 'savePlan').mockResolvedValue(fresh);
    const {user, ref} = setup(trip);
    await user.clear(screen.getByLabelText('Departure date'));
    await user.type(screen.getByLabelText('Departure date'), '2027-03-11');
    await user.click(screen.getByRole('button', {name: 'Discard edits'}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Save'}));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(save).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText('Departure date')).toHaveValue('2027-03-11');
    expect(ref.current!.hasUnsavedChanges()).toBe(false);
    expect(screen.getByRole('button', {name: 'Save plan'})).toBeDisabled();
  });

  it('rejects invalid trip budgets without clearing the saved amount and permits explicit blank clearing', async () => {
    const trip = {...fixture(), budgetCents: 250000};
    const update = vi.spyOn(tripsApi, 'replaceSharedDetails').mockResolvedValue({...trip, version: 1, budgetCents: null});
    const {user} = setup(trip);
    await user.click(screen.getByText('Trip settings'));
    const budget = screen.getByLabelText('Trip budget (USD)');
    for (const amount of ['abc', 'Infinity', '-1', '1000000.01']) {
      await user.clear(budget); await user.type(budget, amount);
      await user.click(screen.getByRole('button', {name: 'Save trip budget'}));
      expect(update).not.toHaveBeenCalled();
      expect(screen.getByRole('alert')).toHaveTextContent('Budget must be between $0.00 and $1,000,000.00.');
      expect(budget).toHaveValue(amount);
    }
    await user.clear(budget);
    await user.click(screen.getByRole('button', {name: 'Save trip budget'}));
    await waitFor(() => expect(update).toHaveBeenCalledWith('trip', expect.objectContaining({budgetCents: null})));
    await user.type(budget, '12.34');
    await user.click(screen.getByRole('button', {name: 'Save trip budget'}));
    await waitFor(() => expect(update).toHaveBeenLastCalledWith('trip', expect.objectContaining({budgetCents: 1234})));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('restores canceled status when returning to a previously viewed confirmation', async () => {
    const trip = fixture(); const plan = trip.plans![0];
    const booking: BookingResponse = {id: 'purchase', tripId: trip.id, plannedItineraryId: plan.id,
      bookingReference: 'REF-purchase', status: 'ACTIVE', grandTotalCents: 0, idempotencyKey: 'key',
      bookedAt: '2026-10-01T12:00:00Z', selections: plan.selections, tally: plan.tally!};
    plan.booked = true; plan.purchase = booking; trip.booking = booking;
    const canceled = {...booking, status: 'CANCELED'};
    vi.spyOn(tripsApi, 'cancelBooking').mockResolvedValue({...trip, version: 1, booking: canceled,
      plans: [{...plan, purchase: canceled}, trip.plans![1]]});
    const {user} = setup(trip);
    await user.click(screen.getByRole('button', {name: 'View booking details'}));
    const confirmationHistory = window.history.state;
    await user.click(screen.getByRole('button', {name: /View in Trip Workspace/}));
    await user.click(screen.getByRole('button', {name: 'Cancel booking'}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: /Cancel booking/i}));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    act(() => {window.history.replaceState(confirmationHistory, ''); window.dispatchEvent(new PopStateEvent('popstate'));});
    expect(screen.getByRole('heading', {name: 'Booking Canceled'})).toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: 'Booking Confirmed!'})).not.toBeInTheDocument();
  });

  it.each([false, true])('preserves edits and newer saves when the post-booking refresh arrives late (saved=%s)', async saved => {
    const trip = fixture(); const plan = trip.plans![0];
    const booking: BookingResponse = {id: 'purchase', tripId: trip.id, plannedItineraryId: plan.id,
      bookingReference: 'REF-purchase', status: 'ACTIVE', grandTotalCents: 0, idempotencyKey: 'key',
      bookedAt: '2026-10-01T12:00:00Z', selections: plan.selections, tally: plan.tally!};
    let resolve!: (trip: TripResponse) => void;
    vi.spyOn(tripsApi, 'getTrip').mockImplementation(() => new Promise(done => {resolve = done;}));
    vi.spyOn(tripsApi, 'createBooking').mockResolvedValue(booking);
    const fresh = {...trip, version: 2, plans: [{...plan, startDate: '2027-03-11', version: 1}, trip.plans![1]]};
    const save = vi.spyOn(tripsApi, 'savePlan').mockResolvedValue(fresh);
    const {user, ref} = setup(trip);
    await user.click(screen.getByRole('button', {name: 'Review booking'}));
    await user.click(screen.getByRole('button', {name: 'Confirm Booking'}));
    await user.click(await screen.findByRole('button', {name: /View in Trip Workspace/}));
    await user.clear(screen.getByLabelText('Departure date')); await user.type(screen.getByLabelText('Departure date'), '2027-03-11');
    if (saved) {await user.click(screen.getByRole('button', {name: 'Save plan'})); await waitFor(() => expect(ref.current!.hasUnsavedChanges()).toBe(false));}
    await act(async () => resolve({...trip, version: 1, booking}));
    expect(screen.getByLabelText('Departure date')).toHaveValue('2027-03-11');
    expect(ref.current!.hasUnsavedChanges()).toBe(!saved);
    expect(save).toHaveBeenCalledTimes(saved ? 1 : 0);
  });

  it('restores the viewed purchase on Back and after reopening confirmation history', async () => {
    const trip = fixture();
    const purchase = (id: string, planId: string): BookingResponse => ({id, tripId: trip.id, plannedItineraryId: planId,
      bookingReference: `REF-${id}`, status: 'CANCELED', grandTotalCents: 0, idempotencyKey: id,
      bookedAt: '2026-10-01T12:00:00Z', purchasedStartDate: '2027-03-10', purchasedEndDate: '2027-03-14',
      purchasedTravelerCount: 2, selections: trip.plans![0].selections, tally: trip.plans![0].tally!});
    trip.plans![0].purchase = purchase('newer', 'a'); trip.plans![1].purchase = purchase('older', 'b');
    trip.plans!.forEach(plan => {plan.booked = true;}); trip.booking = trip.plans![0].purchase;
    const props = {initialTrip: trip, onBack: vi.fn(), onTripDeleted: vi.fn()};
    const mounted = render(<TripWorkspace {...props} />); const user = userEvent.setup();
    await user.click(screen.getByRole('tab', {name: /Later getaway/}));
    await user.click(screen.getByRole('button', {name: 'View booking details'}));
    expect(screen.getAllByText(/REF-older/).length).toBeGreaterThan(0);
    const olderHistory = window.history.state;
    await user.click(screen.getByRole('button', {name: /View in Trip Workspace/}));
    await user.click(screen.getByRole('tab', {name: /Preferred spring/}));
    await user.click(screen.getByRole('button', {name: 'View booking details'}));
    act(() => {window.history.replaceState(olderHistory, ''); window.dispatchEvent(new PopStateEvent('popstate'));});
    expect(screen.getAllByText(/REF-older/).length).toBeGreaterThan(0);
    mounted.unmount(); const reopened = render(<TripWorkspace {...props} />);
    expect(screen.getAllByText(/REF-older/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/REF-newer/)).not.toBeInTheDocument();
    reopened.unmount();
    const older = trip.plans![1].purchase!;
    trip.plans![1].purchase = purchase('replacement', 'b');
    vi.mocked(tripsApi.getBookingHistory).mockResolvedValue([trip.booking!, older]);
    render(<TripWorkspace {...props} />);
    await screen.findAllByText(/REF-older/);
    expect(screen.queryByText(/REF-replacement/)).not.toBeInTheDocument();
    expect(screen.queryByText(/REF-newer/)).not.toBeInTheDocument();
  });

  it('shows primary first and switches named plans without a mutation, including keyboard overflow', async () => {
    const trip = fixture([p('b', 'Later getaway'), p('a', 'Preferred spring', true), ...Array.from({length: 8}, (_, i) => p(`long-${i}`, `Long named plan ${i}`))]);
    const load = vi.spyOn(tripsApi, 'loadOption'), save = vi.spyOn(tripsApi, 'savePlan'), promote = vi.spyOn(tripsApi, 'makePrimary');
    const {user} = setup(trip); const tabs = within(screen.getByRole('tablist', {name: 'Trip plans'})).getAllByRole('tab');
    expect(tabs[0]).toHaveTextContent('Preferred spring Primary');
    tabs[0].focus(); await user.keyboard('{ArrowRight}'); expect(tabs[1]).toHaveFocus(); expect(tabs[0]).toHaveAttribute('aria-selected', 'true');
    await user.keyboard('{Enter}'); expect(tabs[1]).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByLabelText('Travelers')).toHaveValue(1); expect(screen.getByLabelText('Departure date')).toHaveValue('2027-03-15');
    expect(screen.getByRole('tabpanel', {name: 'Later getaway'})).toHaveAttribute('id', tabs[1].getAttribute('aria-controls'));
    await user.keyboard('{End}'); expect(tabs.at(-1)).toHaveFocus(); await user.keyboard(' ');
    await user.selectOptions(screen.getByRole('combobox', {name: 'All plans'}), 'a');
    expect(load).not.toHaveBeenCalled(); expect(save).not.toHaveBeenCalled(); expect(promote).not.toHaveBeenCalled();
  });

  it('saves selected identity and name independently; discard and cancel send no request', async () => {
    const trip = fixture(); const result = {...trip, version: 1, plans: [trip.plans![0], {...trip.plans![1], version: 1, startDate: '2027-03-16'}]};
    const save = vi.spyOn(tripsApi, 'savePlan').mockResolvedValue(result); const {user} = setup(trip);
    await user.click(screen.getByRole('tab', {name: 'Later getaway'}));
    await user.clear(screen.getByLabelText('Departure date')); await user.type(screen.getByLabelText('Departure date'), '2027-03-16');
    await user.click(screen.getByRole('tab', {name: /Preferred spring/}));
    let dialog = screen.getByRole('dialog'); await user.click(within(dialog).getByRole('button', {name: 'Cancel'}));
    expect(screen.getByLabelText('Departure date')).toHaveValue('2027-03-16'); expect(save).not.toHaveBeenCalled();
    await user.click(screen.getByRole('tab', {name: /Preferred spring/})); dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', {name: 'Save'}));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(save).toHaveBeenCalledWith('trip', 'b', {expectedVersion: 0, expectedPlanVersion: 0, startDate: '2027-03-16', endDate: '2027-03-19', travelerCount: 1, travelerAges: [40]});
    expect(screen.getByRole('tab', {name: /Preferred spring/})).toHaveAttribute('aria-selected', 'true');
    await user.clear(screen.getByLabelText('Departure date')); await user.type(screen.getByLabelText('Departure date'), '2027-03-11');
    await user.click(screen.getByRole('tab', {name: 'Later getaway'}));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Discard'}));
    expect(save).toHaveBeenCalledTimes(1); expect(screen.getByLabelText('Departure date')).toHaveValue('2027-03-16');
  });

  it('retains failed/conflicting saves and pending input, traps focus, and restores it on Escape', async () => {
    const save = vi.spyOn(tripsApi, 'savePlan').mockRejectedValue(new Error('Version conflict. Reload before saving.')); const {user} = setup();
    await user.clear(screen.getByLabelText('Departure date')); await user.type(screen.getByLabelText('Departure date'), '2027-03-11');
    const target = screen.getByRole('tab', {name: 'Later getaway'}); await user.click(target);
    const dialog = screen.getByRole('dialog'); const cancel = within(dialog).getByRole('button', {name: 'Cancel'});
    cancel.focus(); await user.tab(); expect(within(dialog).getByRole('button', {name: 'Save'})).toHaveFocus();
    await user.click(within(dialog).getByRole('button', {name: 'Save'})); await screen.findAllByRole('alert');
    expect(screen.getByLabelText('Departure date')).toHaveValue('2027-03-11'); expect(target).toHaveAttribute('aria-selected', 'false');
    expect(save).toHaveBeenCalledTimes(1); await user.keyboard('{Escape}'); expect(target).toHaveFocus();
    expect(screen.getByRole('alert')).toHaveTextContent('Version conflict');
  });

  it('waits for an in-flight save and ignores background refresh while dirty', async () => {
    let resolve!: (trip: TripResponse) => void; const trip = fixture();
    const save = vi.spyOn(tripsApi, 'savePlan').mockImplementation(() => new Promise(done => {resolve = done;}));
    const get = vi.spyOn(tripsApi, 'getTrip').mockResolvedValue(trip); const {user, ref} = setup(trip);
    await user.clear(screen.getByLabelText('Departure date')); await user.type(screen.getByLabelText('Departure date'), '2027-03-11');
    await act(() => ref.current!.refreshIfClean()); expect(get).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', {name: 'Save plan'})); await user.click(screen.getByRole('button', {name: 'Save plan'}));
    expect(save).toHaveBeenCalledTimes(1); expect(screen.getByRole('tab', {name: 'Later getaway'})).toBeDisabled();
    await act(async () => resolve({...trip, version: 1, plans: [{...trip.plans![0], startDate: '2027-03-11', version: 1}, trip.plans![1]]}));
    expect(ref.current!.hasUnsavedChanges()).toBe(false);
  });

  it('ignores a late refresh response after editing begins', async () => {
    let resolve!: (trip: TripResponse) => void; const trip = fixture();
    vi.spyOn(tripsApi, 'getTrip').mockImplementation(() => new Promise(done => {resolve = done;}));
    const {user, ref} = setup(trip); let refresh!: Promise<void>;
    act(() => {refresh = ref.current!.refreshIfClean();});
    await user.clear(screen.getByLabelText('Departure date')); await user.type(screen.getByLabelText('Departure date'), '2027-03-11');
    await act(async () => {resolve({...trip, version: 99, plans: [{...trip.plans![0], name: 'Stale incoming name'}, trip.plans![1]]}); await refresh;});
    expect(screen.getByLabelText('Departure date')).toHaveValue('2027-03-11');
    expect(screen.queryByRole('tab', {name: /Stale incoming name/})).not.toBeInTheDocument();
    expect(ref.current!.hasUnsavedChanges()).toBe(true);
  });

  it('keeps canceled-trip duplication available with the canonical primary source', async () => {
    const trip = fixture([p('a', 'Preferred spring', true)]); trip.status = 'CANCELED'; const {user} = setup(trip);
    expect(screen.getByRole('button', {name: 'Save plan'})).toBeDisabled();
    await user.click(screen.getByRole('button', {name: 'Duplicate trip'}));
    expect(screen.getByRole('dialog')).toHaveTextContent('Preferred spring');
  });

  it.each(['Copy plan', 'Create plan', 'Rename plan', 'Delete plan', 'Compare plans', 'Review booking', 'Back to all trips'])('guards dirty %s with save/discard/cancel', async action => {
    const {user, back} = setup(); await user.clear(screen.getByLabelText('Departure date')); await user.type(screen.getByLabelText('Departure date'), '2027-03-11');
    await user.click(screen.getByRole('button', {name: new RegExp(action)})); expect(screen.getByRole('dialog')).toHaveAccessibleName('Unsaved plan edits');
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Cancel'})); expect(back).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Departure date')).toHaveValue('2027-03-11');
  });

  it('guards parent exits and browser history, registers beforeunload only while dirty', async () => {
    const {user, ref} = setup(); const exit = vi.fn();
    await user.clear(screen.getByLabelText('Departure date')); await user.type(screen.getByLabelText('Departure date'), '2027-03-11');
    const before = new Event('beforeunload', {cancelable: true}); window.dispatchEvent(before); expect(before.defaultPrevented).toBe(true);
    act(() => ref.current!.requestNavigation!(exit)); expect(exit).not.toHaveBeenCalled();
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Cancel'}));
    act(() => {window.history.replaceState({detourScreen: true, destination: 'trips', tripId: 'trip', selectedPlanId: 'b'}, ''); window.dispatchEvent(new PopStateEvent('popstate'));});
    expect(screen.getByRole('dialog')).toHaveAccessibleName('Unsaved plan edits');
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Discard'}));
    expect(screen.getByRole('tab', {name: 'Later getaway'})).toHaveAttribute('aria-selected', 'true');
    const clean = new Event('beforeunload', {cancelable: true}); window.dispatchEvent(clean); expect(clean.defaultPrevented).toBe(false);
  });

  it('guards real Profile and logout exits and opens another trip from history once after Discard', async () => {
    const trip = fixture(), other = {...fixture(), id: 'other-trip', name: 'Other trip', label: 'Other trip'};
    const get = vi.spyOn(tripsApi, 'getTrip').mockImplementation(async id => id === trip.id ? trip : other); const logout = vi.fn(async () => {});
    const summary = (t: TripResponse) => ({id: t.id, label: t.label, destinationKey: t.destinationKey, destinationName: t.destinationName, startDate: t.startDate, endDate: t.endDate, version: 0, inProgress: false, temporalStatus: 'UPCOMING', draftCount: 1, plannedCount: 1, expiredAlternativeCount: 0, bookedCount: 0, hasBookingHistory: false, alternatives: []});
    render(<ProfileScreen email="person@example.test" initialDestination="trips" upcoming={[summary(trip), summary(other)]} onLogout={logout} logoutPending={false} onPasswordChange={async () => {}} onFailure={() => {}} />);
    const user = userEvent.setup(); await user.click(screen.getByRole('button', {name: `Open trip ${trip.label}`})); await screen.findByRole('tab', {name: /Preferred spring/});
    await user.clear(screen.getByLabelText('Departure date')); await user.type(screen.getByLabelText('Departure date'), '2027-03-11');
    await user.click(screen.getByRole('button', {name: 'Profile'})); await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Cancel'}));
    await user.click(screen.getAllByRole('button', {name: 'Log out'})[0]); await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Cancel'})); expect(logout).not.toHaveBeenCalled();
    act(() => {window.history.replaceState({detourScreen: true, destination: 'trips', tripId: 'other-trip'}, ''); window.dispatchEvent(new PopStateEvent('popstate'));});
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', {name: 'Discard'}));
    await screen.findByRole('heading', {name: 'Other trip'}); expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(get.mock.calls.filter(([id]) => id === 'other-trip')).toHaveLength(1);
  });

  it('allows booked promotion, exposes replacement deletion, and sole Keep trip makes no write', async () => {
    const trip = fixture(); trip.plans![1].booked = true;
    const promote = vi.spyOn(tripsApi, 'makePrimary').mockResolvedValue({...trip, version: 1, primaryPlanId: 'b', plans: trip.plans!.map(p => ({...p, primary: p.id === 'b'}))});
    const del = vi.spyOn(tripsApi, 'deletePlan'); const {user} = setup(trip);
    await user.click(screen.getByRole('tab', {name: /Later getaway/})); await user.click(screen.getByRole('button', {name: 'Make primary'}));
    await waitFor(() => expect(promote).toHaveBeenCalledWith('trip', 'b', {expectedVersion: 0, expectedPlanVersion: 0}));
    await user.click(screen.getByRole('button', {name: 'Delete plan'})); expect(screen.getByRole('dialog')).toHaveTextContent("can't be deleted");
    await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Cancel'})); expect(del).not.toHaveBeenCalled();
  });

  it('requires replacement primary and sends exact current versions only after confirmation', async () => {
    const trip = fixture(); const del = vi.spyOn(tripsApi, 'deletePlan').mockResolvedValue({...trip, version: 1, primaryPlanId: 'b', plans: [{...trip.plans![1], primary: true}]}); const {user} = setup(trip);
    await user.click(screen.getByRole('button', {name: 'Delete plan'})); let dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('button', {name: 'Delete plan'})).toBeDisabled();
    await user.selectOptions(within(dialog).getByRole('combobox', {name: 'Replacement primary'}), 'b');
    await user.click(within(dialog).getByRole('button', {name: 'Delete plan'})); await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(del).toHaveBeenCalledWith('trip', 'a', {expectedVersion: 0, expectedPlanVersion: 0, confirmed: true, expectedPlanCount: 2, replacementPrimaryPlanId: 'b'});
    await user.click(screen.getByRole('button', {name: 'Delete plan'})); dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAccessibleName('This is the only plan for this trip. Delete the trip?');
    await user.click(within(dialog).getByRole('button', {name: 'Keep trip'})); expect(del).toHaveBeenCalledTimes(1);
  });

  it('save then promote uses the new versions and preserves the selected identity', async () => {
    const trip = fixture(); const fresh = {...trip, version: 1, plans: [trip.plans![0], {...trip.plans![1], version: 1, startDate: '2027-03-16'}]};
    const save = vi.spyOn(tripsApi, 'savePlan').mockResolvedValue(fresh);
    const promote = vi.spyOn(tripsApi, 'makePrimary').mockResolvedValue({...fresh, version: 2, primaryPlanId: 'b', plans: fresh.plans.map(p => ({...p, primary: p.id === 'b'}))});
    const {user} = setup(trip); await user.click(screen.getByRole('tab', {name: 'Later getaway'}));
    await user.clear(screen.getByLabelText('Departure date')); await user.type(screen.getByLabelText('Departure date'), '2027-03-16');
    await user.click(screen.getByRole('button', {name: 'Make primary'})); await user.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Save'}));
    await waitFor(() => expect(promote).toHaveBeenCalledWith('trip', 'b', {expectedVersion: 1, expectedPlanVersion: 1}));
    expect(save).toHaveBeenCalledTimes(1);
  });

  it.each(['ACTIVE', 'CANCELED'])('marks purchased flight locked and displays accurate %s purchase details beside edited planning fields', async status => {
    const trip = fixture(); const plan = trip.plans![0]; plan.travelerCount = 3; plan.travelerAges = [25,30,5]; plan.startDate = '2027-03-15';
    plan.selections.airfare = {outboundFlightInstanceId: 1, returnFlightInstanceId: 2, outboundDescription: 'Purchased outbound', returnDescription: 'Purchased return', outboundBaseFareCents: 10000, outboundTaxCents: 0, outboundFeeCents: 0, returnBaseFareCents: 10000, returnTaxCents: 0, returnFeeCents: 0};
    plan.booked = true; plan.lockedComponents = ['airfare'];
    if (status === 'CANCELED') {
      plan.selections.stay = {accommodationUnitId: 3, unitCount: 1, propertyName: 'Purchased hotel', unitName: 'Purchased room', nights: []};
      plan.selections.rental = {rentalUnitId: 4, pickupAt: '2027-03-10T10:00:00Z', returnAt: '2027-03-14T10:00:00Z', locationName: 'Purchased location', vehicleClassName: 'Purchased car', unitIdentifier: 'car-4', dailyBasePriceCents: 10000, dailyTaxCents: 0, dailyFeeCents: 0};
      plan.lockedComponents.push('stay', 'rental');
    }
    plan.purchase = {id: 'booking', tripId: trip.id, plannedItineraryId: plan.id, bookingReference: 'DETOUR-123', status, grandTotalCents: 40000, idempotencyKey: 'key', bookedAt: '2026-10-01T12:00:00Z', purchasedStartDate: '2027-03-10', purchasedEndDate: '2027-03-14', purchasedTravelerCount: 2, selections: plan.selections, tally: plan.tally!};
    trip.booking = plan.purchase;
    const {user} = setup(trip);
    expect(screen.getByText(`${status === 'ACTIVE' ? 'Confirmed' : 'Canceled booking'} flight · locked`)).toBeInTheDocument();
    expect(screen.getByText(/Purchased outbound · 2 travelers/)).toBeInTheDocument();
    expect(screen.getByLabelText('Purchased details')).toHaveTextContent('2027-03-10 to 2027-03-14 · 2 travelers · Total $400.00');
    expect(screen.getByRole('tab', {name: 'Search flights'})).toBeDisabled(); expect(screen.getByRole('button', {name: 'Remove flight'})).toBeDisabled();
    if (status === 'ACTIVE') expect(screen.getByRole('tab', {name: 'Search stays'})).toBeEnabled();
    else {
      expect(screen.getByText('Canceled booking stay · locked')).toBeInTheDocument();
      expect(screen.getByText('Canceled booking · locked')).toBeInTheDocument();
      expect(screen.getByRole('tab', {name: 'Search stays'})).toBeDisabled();
      expect(screen.getByRole('button', {name: 'Remove stay'})).toBeDisabled();
      expect(screen.getByRole('button', {name: 'Remove'})).toBeDisabled();
    }
    await user.click(screen.getByRole('button', {name: 'View booking details'}));
    expect(screen.getAllByText(/DETOUR-123/).length).toBeGreaterThan(0);
    expect(screen.getByRole('heading', {name: status === 'ACTIVE' ? 'Booking Confirmed!' : 'Booking Canceled'})).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(status === 'ACTIVE' ? 'Booking confirmed!' : 'Booking canceled.');
    expect(screen.getByText('2027-03-10 to 2027-03-14')).toBeInTheDocument();
  });

  it('launches comparison from an alternative with primary, supports alternatives only and partial zero totals', async () => {
    const trip = fixture([p('a', 'Preferred spring', true), p('b', 'Later getaway'), p('c', 'City break'), p('d', 'Other dates')]); const {user} = setup(trip);
    await user.click(screen.getByRole('tab', {name: 'Later getaway'})); await user.click(screen.getByRole('button', {name: 'Compare plans'}));
    const dialog = screen.getByRole('dialog'); expect(within(dialog).getByRole('checkbox', {name: 'Preferred spring (Primary)'})).toBeChecked(); expect(within(dialog).getByRole('checkbox', {name: 'Later getaway'})).toBeChecked();
    await user.click(within(dialog).getByRole('checkbox', {name: 'City break'})); expect(within(dialog).getByRole('checkbox', {name: 'Other dates'})).toBeDisabled();
    await user.click(within(dialog).getByRole('checkbox', {name: 'Preferred spring (Primary)'})); await user.click(within(dialog).getByRole('button', {name: 'Compare selected plans'}));
    expect(screen.getByRole('heading', {name: 'Comparing 2 plans'})).toBeInTheDocument();
    expect(screen.getAllByText(/Partial total: \$0.00/).length).toBeGreaterThan(0); expect(screen.getAllByText('Not selected').length).toBeGreaterThan(0);
  });
});
