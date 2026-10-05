import {forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState} from 'react';
import {tripsApi, type PlanResponse, type TripResponse, type AlternativeResponse, type PlanDetails, type BookingResponse} from '../api/tripsApi';
import type {TripWorkspaceHandle, TripWorkspaceProps} from './TripWorkspace';
import {PlanNavigation} from './PlanNavigation';
import {PlanDialog} from './PlanDialog';
import {TripComparisonPage} from './TripComparisonPage';
import {TravelerAgeInput} from './TravelerAgeInput';
import {ItineraryComparisonView} from './ItineraryComparisonView';
import {BookingReviewView} from './BookingReviewView';
import {BookingConfirmationView} from './BookingConfirmationView';
import {BookingHistorySection} from './BookingHistorySection';
import {CancelBookingModal} from './CancelBookingModal';
import {TripRevisionModal} from './TripRevisionModal';
import {CancelTripModal} from './CancelTripModal';
import {RevisionSummaryBanner} from './RevisionSummaryBanner';
import {currentScreen, rememberScreen, type WorkspaceView} from '../screenHistory';
import {formatTallyCents} from './ItinerarySummaryTally';

type Input = {startDate: string; endDate: string; travelerCount: string; ages: string[]};
const fields = (p: PlanResponse): Input => ({startDate: p.startDate, endDate: p.endDate, travelerCount: String(p.travelerCount), ages: p.travelerAges?.map(age => age == null ? '' : String(age)) ?? Array(p.travelerCount).fill('')});
const alternative = (p: PlanResponse): AlternativeResponse => ({...p, lifecycle: 'PLANNED'});
const purchaseForHistory = (trip: TripResponse, bookingId?: string, planId?: string): BookingResponse | null =>
  bookingId ? [trip.booking, ...trip.plans!.map(p => p.purchase)].find(p => p?.id === bookingId) ?? null
  : trip.plans!.find(p => p.id === planId)?.purchase ?? trip.booking ?? null;

export const IndependentPlansWorkspace = forwardRef<TripWorkspaceHandle, TripWorkspaceProps>(function IndependentPlansWorkspace(props, ref) {
  const [trip, setTrip] = useState(props.initialTrip);
  const initialHistory = currentScreen();
  const initialId = initialHistory?.tripId === trip.id && trip.plans!.some(p => p.id === initialHistory.selectedPlanId) ? initialHistory.selectedPlanId! : trip.primaryPlanId!;
  const [selectedId, setSelectedId] = useState(initialId);
  const plans = [...trip.plans!].sort((a, b) => Number(b.primary) - Number(a.primary));
  const plan = plans.find(p => p.id === selectedId) ?? plans[0];
  const [input, setInput] = useState(() => fields(plan));
  const [pending, setPending] = useState(false);
  const busy = useRef(false);
  const [error, setError] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [transition, setTransition] = useState<(() => void) | null>(null);
  const [dialog, setDialog] = useState<'create' | 'copy' | 'name' | 'delete' | 'compare' | null>(null);
  const [name, setName] = useState('');
  const [replacement, setReplacement] = useState('');
  const [compareIds, setCompareIds] = useState<string[]>(initialHistory?.comparedOptionIds ?? []);
  const [view, setView] = useState<WorkspaceView>(initialHistory?.tripId === trip.id ? initialHistory.workspaceView ?? 'workspace' : 'workspace');
  const [reviewId, setReviewId] = useState(initialHistory?.reviewOptionId ?? selectedId);
  const [reviewReturn, setReviewReturn] = useState<'workspace' | 'compare'>(initialHistory?.reviewReturnView ?? 'workspace');
  const [confirmation, setConfirmation] = useState<BookingResponse | null>(() => initialHistory?.tripId === trip.id
    ? purchaseForHistory(trip, initialHistory.confirmationBookingId, initialHistory.selectedPlanId)
    : props.initialActiveBooking ?? trip.booking ?? null);
  const [confirmationId, setConfirmationId] = useState(initialHistory?.tripId === trip.id ? initialHistory.confirmationBookingId : undefined);
  const [confirmationError, setConfirmationError] = useState<string>();
  const [cancelBookingOpen, setCancelBookingOpen] = useState(false);
  const [cancelTripOpen, setCancelTripOpen] = useState(false);
  const [duplicateOpen, setDuplicateOpen] = useState(false);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [budget, setBudget] = useState(trip.budgetCents == null ? '' : String(trip.budgetCents / 100));
  const [budgetError, setBudgetError] = useState<string>();
  const inputRef = useRef(input); inputRef.current = input;
  const tripRef = useRef(trip); tripRef.current = trip;
  const selectedRef = useRef(plan.id); selectedRef.current = plan.id;
  const dirty = JSON.stringify(input) !== JSON.stringify(fields(plan));
  const dirtyRef = useRef(dirty); dirtyRef.current = dirty;
  const canceled = trip.status === 'CANCELED';
  const activeBooking = trip.booking?.status === 'ACTIVE' ? trip.booking : null;
  const context: TripResponse = {...trip, startDate: plan.startDate, endDate: plan.endDate, travelerCount: plan.travelerCount, travelerAges: plan.travelerAges};

  const record = useCallback((nextView = view, id = plan.id, replace = false, bookingId = confirmation?.id ?? confirmationId) => rememberScreen('trips', trip.id, replace, {
    selectedPlanId: id, workspaceView: nextView, comparedOptionIds: compareIds,
    reviewOptionId: reviewId, reviewReturnView: reviewReturn,
    ...(nextView === 'booking-confirmation' && bookingId ? {confirmationBookingId: bookingId} : {}),
  }), [view, plan.id, trip.id, compareIds, reviewId, reviewReturn, confirmation?.id, confirmationId]);
  const guard = useCallback((action: () => void) => {
    if (busy.current) return;
    if (dirtyRef.current) setTransition(() => action);
    else action();
  }, []);
  const apply = (fresh: TripResponse, resetInput = false) => {
    tripRef.current = fresh; setTrip(fresh); props.onTripUpdated?.(fresh);
    setConfirmation(previous => previous ? purchaseForHistory(fresh, previous.id) ?? previous : null);
    const selected = fresh.plans!.find(p => p.id === selectedRef.current) ?? fresh.plans!.find(p => p.primary)!;
    if (selected.id !== selectedRef.current) {setSelectedId(selected.id); setInput(fields(selected));}
    else if (resetInput) setInput(fields(selected));
  };
  const switchPlan = (id: string, history = true) => {
    const next = tripRef.current.plans!.find(p => p.id === id) ?? tripRef.current.plans!.find(p => p.primary)!;
    setSelectedId(next.id); setInput(fields(next)); setMessage(undefined);
    if (history) record('workspace', next.id);
    setView('workspace');
  };
  const save = async (): Promise<boolean> => {
    if (busy.current) return false;
    const captured = inputRef.current;
    const count = Number(captured.travelerCount);
    if (!Number.isInteger(count) || count < 1 || count > 8 || captured.ages.length !== count || captured.ages.some(age => age.trim() === '' || !Number.isInteger(Number(age)) || Number(age) < 0 || Number(age) > 120)) {
      setError('Provide 1–8 travelers and one age from 0 to 120 for each traveler.'); return false;
    }
    const details: PlanDetails = {startDate: captured.startDate, endDate: captured.endDate, travelerCount: count, travelerAges: captured.ages.map(Number)};
    busy.current = true; setPending(true); setMessage('Saving plan…');
    try {
      const fresh = await tripsApi.savePlan(trip.id, plan.id, {...details, expectedVersion: trip.version, expectedPlanVersion: plan.version});
      apply(fresh); setMessage('Plan saved.'); setError(undefined);
      return JSON.stringify(inputRef.current) === JSON.stringify(captured);
    } catch (e) {setError(e instanceof Error ? e.message : 'Could not save plan. Your edits remain here.'); setMessage(undefined); return false;}
    finally {busy.current = false; setPending(false);}
  };
  const mutate = async (action: () => Promise<TripResponse | void>): Promise<boolean> => {
    if (busy.current) return false;
    busy.current = true; setPending(true);
    try {const fresh = await action(); if (fresh) apply(fresh, true); else props.onTripDeleted(); setError(undefined); return true;}
    catch (e) {setError(e instanceof Error ? e.message : 'Could not complete the action.'); return false;}
    finally {busy.current = false; setPending(false);}
  };
  useEffect(() => {props.onSaveStatusChange?.(pending ? 'saving' : error ? 'error' : 'idle', dirty || pending);}, [dirty, pending, error, props.onSaveStatusChange]);
  useEffect(() => {
    if (view !== 'booking-confirmation' || !confirmationId || confirmation?.id === confirmationId) return;
    let current = true; setConfirmationError(undefined);
    void tripsApi.getBookingHistory(trip.id).then(history => {
      if (!current) return;
      const booking = history.find(b => b.id === confirmationId);
      if (booking) setConfirmation(booking); else setConfirmationError('This booking could not be found.');
    }).catch(e => {if (current) setConfirmationError(e instanceof Error ? e.message : 'Could not load booking details.');});
    return () => {current = false;};
  }, [view, confirmationId, confirmation?.id, trip.id]);
  useEffect(() => {
    if (!dirty && !pending) return;
    const handler = (event: BeforeUnloadEvent) => {event.preventDefault(); event.returnValue = '';};
    window.addEventListener('beforeunload', handler); return () => window.removeEventListener('beforeunload', handler);
  }, [dirty, pending]);
  useEffect(() => {
    const restore = () => {
      const state = currentScreen();
      if (state?.tripId !== trip.id) return;
      const action = () => {switchPlan(state.selectedPlanId ?? trip.primaryPlanId!, false); setCompareIds(state.comparedOptionIds ?? []); setReviewId(state.reviewOptionId ?? trip.primaryPlanId!); setReviewReturn(state.reviewReturnView ?? 'workspace'); setView(state.workspaceView ?? 'workspace');
        if (state.workspaceView === 'booking-confirmation') {setConfirmationId(state.confirmationBookingId); setConfirmationError(undefined);
          setConfirmation(state.confirmationBookingId === confirmation?.id ? confirmation : purchaseForHistory(tripRef.current, state.confirmationBookingId, state.selectedPlanId));}};
      if (dirtyRef.current || busy.current) {record(view, plan.id, true); guard(() => {action(); rememberScreen('trips', trip.id, false, state);});}
      else action();
    };
    window.addEventListener('popstate', restore); return () => window.removeEventListener('popstate', restore);
  });
  const refreshIfClean = async () => {
    if (dirtyRef.current || busy.current) return;
    const version = tripRef.current.version;
    try {const fresh = await tripsApi.getTrip(trip.id); if (!dirtyRef.current && !busy.current && tripRef.current.version === version) apply(fresh, true);}
    catch (e) {setError(e instanceof Error ? e.message : 'Could not refresh trip.');}
  };
  useImperativeHandle(ref, () => ({hasUnsavedChanges: () => dirtyRef.current || busy.current,
    requestNavigation: guard, rememberNavigation: () => record(), refreshIfClean,
  }));
  const open = (action: typeof dialog) => guard(() => {setDialog(action); setName(action === 'name' ? plan.name : ''); setReplacement('');});
  const review = (id: string, from: 'workspace' | 'compare') => guard(() => {setReviewId(id); setReviewReturn(from); setView('booking-review'); rememberScreen('trips', trip.id, false, {selectedPlanId: plan.id, workspaceView: 'booking-review', comparedOptionIds: compareIds, reviewOptionId: id, reviewReturnView: from});});
  const component = async (category: string, action: () => Promise<TripResponse>): Promise<boolean> => {
    const current = tripRef.current.plans!.find(p => p.id === selectedRef.current)!;
    if (dirtyRef.current || tripRef.current.status === 'CANCELED' || current.id !== plan.id || current.lockedComponents.includes(category)) return false;
    return mutate(action);
  };
  const revision = () => ({expectedVersion: tripRef.current.version, expectedDraftVersion: tripRef.current.plans!.find(p => p.id === selectedRef.current)!.version});
  const versions = () => ({expectedVersion: tripRef.current.version, expectedPlanVersion: tripRef.current.plans!.find(p => p.id === selectedRef.current)!.version});
  const saveBudget = () => {
    const amount = budget.trim() === '' ? null : Number(budget);
    if (amount !== null && (!Number.isFinite(amount) || amount < 0 || amount > 1000000)) {
      setBudgetError('Budget must be between $0.00 and $1,000,000.00.');
      return;
    }
    setBudgetError(undefined);
    guard(() => {void mutate(() => tripsApi.replaceSharedDetails(trip.id, {
      expectedVersion: tripRef.current.version, expectedDraftVersion: tripRef.current.workingPlan?.version,
      destinationKey: tripRef.current.destinationKey, startDate: tripRef.current.startDate, endDate: tripRef.current.endDate,
      travelerCount: tripRef.current.travelerCount, travelerAges: tripRef.current.travelerAges,
      budgetCents: amount === null ? null : Math.round(amount * 100),
    }));});
  };

  if (view === 'compare' && compareIds.filter(id => plans.some(p => p.id === id)).length >= 2) return <ItineraryComparisonView trip={trip}
    alternatives={plans.filter(p => compareIds.includes(p.id)).map(alternative)} hasActiveBooking={Boolean(activeBooking)}
    onBack={() => {setView('workspace'); record('workspace');}} onSelectForBookingReview={id => review(id, 'compare')} />;
  const reviewPlan = plans.find(p => p.id === reviewId);
  if (view === 'booking-review' && reviewPlan) return <BookingReviewView trip={{...trip, startDate: reviewPlan.startDate, endDate: reviewPlan.endDate, travelerCount: reviewPlan.travelerCount, travelerAges: reviewPlan.travelerAges}}
    alternative={alternative(reviewPlan)} returnTarget={reviewReturn} onBack={() => {setView(reviewReturn); record(reviewReturn);}}
    onBookingSuccess={booking => {setConfirmation(booking); setConfirmationId(booking.id); setView('booking-confirmation'); record('booking-confirmation', plan.id, false, booking.id); void refreshIfClean();}} />;
  if (view === 'booking-confirmation' && confirmation) return <BookingConfirmationView trip={trip} booking={confirmation}
    onViewInWorkspace={() => {setView('workspace'); record('workspace');}} onViewAllTrips={() => guard(props.onBack)} />;
  if (view === 'booking-confirmation') return <section className="card"><button type="button" onClick={() => {setView('workspace'); record('workspace');}}>Back to Trip Workspace</button>
    {confirmationError ? <p role="alert">{confirmationError}</p> : <p role="status">Loading booking details…</p>}</section>;

  return <section className="card workspace-card" aria-labelledby="workspace-heading">
    <div className="workspace-nav"><button type="button" className="text-button back-link" onClick={() => guard(props.onBack)}>← Back to all trips</button>
      {props.onLogout && <button type="button" className="text-button" disabled={pending || props.logoutPending} onClick={() => guard(() => {void props.onLogout?.();})}>Log out</button>}</div>
    <header className="workspace-header"><div><p className="eyebrow wordmark">DeTour</p><h1 id="workspace-heading" tabIndex={-1}>{trip.name ?? trip.label}</h1>
      <p className="trip-route">From {trip.originAirportCode} to {trip.destinationName}</p>{canceled && <p className="badge badge-canceled">Canceled trip · plans are read-only</p>}</div></header>
    <PlanNavigation plans={plans} selectedId={plan.id} onSelect={id => guard(() => switchPlan(id))} disabled={pending} />
    <div className="plan-actions">
      <div className="plan-actions-group">
      <button type="button" className="secondary button-sm" disabled={pending || plans.length < 2} onClick={() => guard(() => {setCompareIds(plan.primary ? [plan.id] : [trip.primaryPlanId!, plan.id]); setDialog('compare');})}>Compare plans</button>
      <button type="button" className="secondary button-sm" disabled={pending || canceled} onClick={() => open('create')}>Create plan</button>
      <button type="button" className="secondary button-sm" disabled={pending || canceled} onClick={() => open('copy')}>Copy plan</button>
      <button type="button" className="secondary button-sm" disabled={pending || canceled} onClick={() => open('name')}>Rename plan</button>
      {!plan.primary && <button type="button" className="secondary button-sm" disabled={pending || canceled} onClick={() => guard(() => {void mutate(() => tripsApi.makePrimary(trip.id, plan.id, versions()));})}>Make primary</button>}
      <button type="button" className="button-danger-outline button-sm" disabled={pending || canceled} onClick={() => open('delete')}>Delete plan</button>
      </div>
      <button type="button" className="primary button-sm" disabled={pending || canceled || Boolean(activeBooking) || plan.booked} onClick={() => review(plan.id, 'workspace')}>Review booking</button>
    </div>
    <div className="workspace-status-row">{message && <p role="status">{message}</p>}{error && <p role="alert" className="field-error">{error} Your plan has not been overwritten.</p>}
      <button type="button" className="text-button button-sm" disabled={pending} onClick={() => guard(() => {void mutate(() => tripsApi.getTrip(trip.id));})}>Reload from server</button></div>
    {trip.revisionSummary && <RevisionSummaryBanner summary={trip.revisionSummary} onDismiss={() => setTrip({...trip, revisionSummary: null})} />}
    <section role="tabpanel" className="plan-panel" id={`plan-panel-${plan.id}`} aria-labelledby={`plan-tab-${plan.id}`}>
      <div className="plan-panel-header"><h2>{plan.name}</h2></div>
      <details className="settings-disclosure" open={detailsOpen || dirty} onToggle={event => {if (dirty) event.currentTarget.open = true; else setDetailsOpen(event.currentTarget.open);}}><summary>Edit plan details</summary>
      <fieldset className="plan-details" disabled={pending || canceled}><legend>Planning details for {plan.name}</legend>
        <div className="plan-details-fields">
        <label>Departure date <input type="date" value={input.startDate} min="2027-03-01" max="2027-03-30" onChange={e => setInput({...input, startDate: e.target.value})} /></label>
        <label>Return date <input type="date" value={input.endDate} min="2027-03-02" max="2027-03-31" onChange={e => setInput({...input, endDate: e.target.value})} /></label>
        <label>Travelers <input type="number" value={input.travelerCount} min={1} max={8} onChange={e => {
          const count = Number(e.target.value); setInput({...input, travelerCount: e.target.value, ages: Number.isInteger(count) && count >= 1 && count <= 8 ? Array.from({length: count}, (_, i) => input.ages[i] ?? '') : input.ages});
        }} /></label>
        {input.ages.map((age, i) => <label key={i}>Traveler {i + 1} age <TravelerAgeInput id={`plan-age-${i}`} value={age} onChange={value => setInput({...input, ages: input.ages.map((old, j) => i === j ? value : old)})} /></label>)}
        </div>
        <div className="plan-details-actions">
        <button type="button" className="primary" disabled={!dirty} onClick={() => {void save();}}>Save plan</button>
        <button type="button" className="secondary" disabled={!dirty} onClick={() => guard(() => {setInput(fields(tripRef.current.plans!.find(p => p.id === selectedRef.current)!));})}>Discard edits</button>
        </div>
      </fieldset></details>
      {dirty && <p className="hint plan-dirty-hint">Prices and searches use saved planning details. Save to search with your changes.</p>}
      {plan.purchase && <section className="plan-purchase" aria-label="Purchased details"><h3>{plan.purchase.status === 'ACTIVE' ? 'Confirmed bookings' : 'Booking history'} · purchased details are locked</h3>
        <p>Purchased dates: {plan.purchase.purchasedStartDate} to {plan.purchase.purchasedEndDate} · {plan.purchase.purchasedTravelerCount} travelers · Total {formatTallyCents(plan.purchase.grandTotalCents)}</p>
        <button type="button" className="secondary button-sm" onClick={() => guard(() => {setConfirmation(plan.purchase!); setConfirmationId(plan.purchase!.id); setView('booking-confirmation'); record('booking-confirmation', plan.id, false, plan.purchase!.id);})}>View booking details</button></section>}
      <TripComparisonPage key={plan.id} trip={context} draftId={plan.id} name={plan.name}
        initialSearch={props.initialEntryMode === 'AIRFARE' && !plan.lockedComponents.includes('airfare') ? 'AIRFARE' : props.initialEntryMode === 'STAY' && !plan.lockedComponents.includes('stay') ? 'STAY' : null}
        savedAirfare={plan.selections.airfare} savedStay={plan.selections.stay} savedRental={plan.selections.rental} tally={plan.tally} purchasedTally={plan.purchase?.tally} pending={pending || dirty} readOnly={canceled}
        lockedAirfare={plan.lockedComponents.includes('airfare')} lockedStay={plan.lockedComponents.includes('stay')}
        purchaseCanceled={plan.purchase?.status === 'CANCELED'}
        purchasedTravelerCount={plan.purchase?.purchasedTravelerCount} purchasedStartDate={plan.purchase?.purchasedStartDate} purchasedEndDate={plan.purchase?.purchasedEndDate}
        lockedRental={plan.lockedComponents.includes('rental')}
        onSelectAirfare={async option => component('airfare', () => tripsApi.selectAirfare(trip.id, plan.id, {...revision(), outboundFlightInstanceId: option.outbound.flightInstanceId, returnFlightInstanceId: option.returnFlight.flightInstanceId}))}
        onSelectStay={async option => component('stay', () => tripsApi.selectStay(trip.id, plan.id, {...revision(), accommodationUnitId: option.accommodationUnitId, unitCount: option.pricing.requiredRooms}))}
        onSelectRental={async (option, pickupAt, returnAt) => component('rental', () => tripsApi.selectRental(trip.id, plan.id, {...revision(), rentalUnitId: option.rentalUnitId, pickupAt, returnAt}))}
        onRemoveAirfare={() => {void component('airfare', () => tripsApi.removeAirfare(trip.id, plan.id, revision()));}}
        onRemoveStay={() => {void component('stay', () => tripsApi.removeStay(trip.id, plan.id, revision()));}}
        onRemoveRental={() => {void component('rental', () => tripsApi.removeRental(trip.id, plan.id, revision()));}} />
    </section>
    <details className="settings-disclosure"><summary>Trip settings</summary><div className="disclosure-body"><p>Destination and budget are shared trip settings.</p>
      <label>Trip budget (USD) <input value={budget} disabled={pending || canceled} aria-invalid={Boolean(budgetError)} onChange={e => {setBudget(e.target.value); setBudgetError(undefined);}} /></label>
      {budgetError && <p role="alert" className="field-error">{budgetError}</p>}
      <button type="button" className="secondary" disabled={pending || canceled} onClick={saveBudget}>Save trip budget</button></div></details>
    <BookingHistorySection tripId={trip.id} refreshKey={trip.version} />
    {(canceled || activeBooking || trip.booking) && <div className="workspace-footer-actions">
      {canceled && <button type="button" className="secondary" onClick={() => setDuplicateOpen(true)}>Duplicate trip</button>}
      {activeBooking && <button type="button" className="button-danger-outline" disabled={pending || canceled} onClick={() => guard(() => setCancelBookingOpen(true))}>Cancel booking</button>}
      {trip.booking && <button type="button" className="button-danger-outline" disabled={pending || canceled} onClick={() => guard(() => setCancelTripOpen(true))}>Cancel trip</button>}
    </div>}
    <TripRevisionModal isOpen={duplicateOpen} trip={{...trip, planned: plans.map(p => ({id: p.id, name: p.name, startDate: p.startDate, endDate: p.endDate, version: p.version, selections: p.selections, tally: p.tally}))}} existingNames={props.existingTripNames} mode="duplicate" onClose={() => setDuplicateOpen(false)} onSuccess={fresh => {setDuplicateOpen(false); props.onTripUpdated?.(fresh); props.onBack();}} />
    <CancelBookingModal isOpen={cancelBookingOpen} tripLabel={trip.label} bookingReference={activeBooking?.bookingReference ?? ''} pending={pending} errorMessage={error}
      onClose={() => setCancelBookingOpen(false)} onConfirm={() => {if (activeBooking) void mutate(() => tripsApi.cancelBooking(trip.id, activeBooking.id, {expectedVersion: trip.version})).then(ok => {if (ok) setCancelBookingOpen(false);});}} />
    <CancelTripModal isOpen={cancelTripOpen} tripLabel={trip.label} hasActiveBooking={Boolean(activeBooking)} pending={pending} errorMessage={error}
      onClose={() => setCancelTripOpen(false)} onConfirm={() => {void mutate(() => tripsApi.cancelTrip(trip.id, {expectedVersion: trip.version})).then(ok => {if (ok) setCancelTripOpen(false);});}} />
    {transition && <PlanDialog title="Unsaved plan edits" pending={pending} onClose={() => setTransition(null)}>
      <p>Save your edits, discard them, or cancel to keep editing.</p>{error && <p role="alert" className="field-error">{error}</p>}
      <div className="modal-actions">
      <button type="button" className="primary" disabled={pending} onClick={() => {void save().then(ok => {if (ok) {const action = transition; setTransition(null); action();}});}}>Save</button>
      <button type="button" className="secondary" disabled={pending} onClick={() => {setInput(fields(plan)); const action = transition; setTransition(null); action();}}>Discard</button>
      <button type="button" className="text-button" disabled={pending} onClick={() => setTransition(null)}>Cancel</button>
      </div>
    </PlanDialog>}
    {dialog && <PlanDialog title={dialog === 'delete' ? plans.length === 1 ? 'This is the only plan for this trip. Delete the trip?' : `Delete ${plan.name}?` : dialog === 'compare' ? 'Compare plans' : `${dialog === 'name' ? 'Rename' : dialog === 'copy' ? 'Copy' : 'Create'} plan`} pending={pending} onClose={() => setDialog(null)}>
      {dialog === 'compare' ? <><p>Select two or three distinct plans.</p><div className="checkbox-list">{plans.map(p => <label key={p.id} className="checkbox-label"><input type="checkbox" checked={compareIds.includes(p.id)} disabled={!compareIds.includes(p.id) && compareIds.length >= 3} onChange={e => setCompareIds(e.target.checked ? [...compareIds, p.id] : compareIds.filter(id => id !== p.id))} />{p.name}{p.primary && ' (Primary)'}</label>)}</div></>
        : dialog === 'delete' ? plan.booked ? <p>This plan contains confirmed bookings or booking history and can't be deleted.</p>
          : plan.primary && plans.length > 1 && <label>Replacement primary <select value={replacement} onChange={e => setReplacement(e.target.value)}><option value="">Choose a plan</option>{plans.filter(p => p.id !== plan.id).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
        : <label>Plan name <input value={name} maxLength={300} disabled={pending} onChange={e => setName(e.target.value)} /></label>}
      {error && <p role="alert" className="field-error">{error}</p>}
      <div className="modal-actions">
        {dialog === 'compare' ? <button type="button" className="primary" disabled={compareIds.length < 2 || compareIds.length > 3} onClick={() => {setDialog(null); setView('compare'); record('compare');}}>Compare selected plans</button>
          : dialog === 'delete' ? !plan.booked && <button type="button" className="danger-button" disabled={pending || (plan.primary && plans.length > 1 && !replacement)} onClick={() => {void mutate(() => tripsApi.deletePlan(trip.id, plan.id, {...versions(), confirmed: true, expectedPlanCount: plans.length, ...(plans.length === 1 ? {deleteTrip: true} : replacement ? {replacementPrimaryPlanId: replacement} : {})})).then(ok => {if (ok) setDialog(null);});}}>{plans.length === 1 ? 'Delete trip' : 'Delete plan'}</button>
          : <button type="button" className="primary" disabled={pending || !name.trim()} onClick={() => {void mutate(() => dialog === 'create' ? tripsApi.createPlan(trip.id, {expectedVersion: trip.version, name: name.trim(), startDate: plan.startDate, endDate: plan.endDate, travelerCount: plan.travelerCount, travelerAges: plan.travelerAges}) : dialog === 'copy' ? tripsApi.copyPlan(trip.id, plan.id, {...versions(), name: name.trim()}) : tripsApi.renamePlan(trip.id, plan.id, {...versions(), name: name.trim()})).then(ok => {if (ok) setDialog(null);});}}>{dialog === 'name' ? 'Rename' : dialog === 'copy' ? 'Copy' : 'Create'}</button>}
        <button type="button" className="text-button" disabled={pending} onClick={() => setDialog(null)}>{dialog === 'delete' && plans.length === 1 ? 'Keep trip' : 'Cancel'}</button>
      </div>
    </PlanDialog>}
  </section>;
});
