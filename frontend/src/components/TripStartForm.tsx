import {useRef, useState, type FormEvent} from 'react';
import {tripsApi, type AccommodationType, type TripResponse} from '../api/tripsApi';
import {IdentityApiError} from '../api/identityApi';
import type {StartMode} from './HomeScreen';
import {TravelerAgeInput} from './TravelerAgeInput';

export type TripStartDraft = {
  name: string; destinationKey: string; startDate: string; endDate: string;
  travelerCount: string; ages: string[]; budget: string; accommodationType: AccommodationType;
};

export const emptyTripStartDraft: TripStartDraft = {
  name: 'San Francisco trip', destinationKey: 'destination-sfo', startDate: '', endDate: '',
  travelerCount: '1', ages: [''], budget: '', accommodationType: 'HOTEL',
};

export function createTripFromDraft(draft: TripStartDraft): Promise<TripResponse> {
  const travelerCount = Number(draft.travelerCount);
  return tripsApi.createTrip({
    name: draft.name.trim(), destinationKey: draft.destinationKey, startDate: draft.startDate, endDate: draft.endDate,
    travelerCount, travelerAges: draft.ages.slice(0, travelerCount).map(Number),
    ...(draft.budget ? {budgetCents: Math.round(Number(draft.budget) * 100)} : {}),
  });
}

const suggestedName = (destinationKey: string) => ({'destination-sfo': 'San Francisco trip', 'destination-muc': 'Munich trip', 'destination-mex': 'Mexico City trip'}[destinationKey] ?? 'My trip');

type Props = {
  draft: TripStartDraft;
  onChange: (draft: TripStartDraft) => void;
  mode: StartMode;
  authenticated: boolean;
  onAuthenticationRequired: () => void;
  onSuccess: (trip: TripResponse, mode: StartMode, accommodationType?: AccommodationType) => void;
  onExplore?: () => void;
};

export function TripStartForm({draft, onChange, mode, authenticated, onAuthenticationRequired, onSuccess, onExplore}: Props) {
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);
  const pendingRef = useRef(false);
  const change = (patch: Partial<TripStartDraft>) => onChange({...draft, ...patch});
  const count = Number(draft.travelerCount);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (pendingRef.current) return;
    const nextErrors: Record<string, string> = {};
    if (!draft.name.trim()) nextErrors.name = 'Enter a trip name.';
    else if (draft.name.trim().length > 300) nextErrors.name = 'Trip name must be 300 characters or fewer.';
    if (!draft.destinationKey) nextErrors.destinationKey = 'Select a destination.';
    if (!draft.startDate || !draft.endDate) nextErrors.dates = 'Please enter both departure and return dates.';
    else if (draft.startDate < '2027-03-01' || draft.endDate > '2027-03-31' || draft.startDate > '2027-03-31' || draft.endDate < '2027-03-01') nextErrors.dates = 'Travel must take place in March 2027.';
    else if (draft.endDate <= draft.startDate) nextErrors.dates = 'Return date must be after departure date.';
    else {
      const nights = (Date.parse(draft.endDate) - Date.parse(draft.startDate)) / 86400000;
      if (nights < 1 || nights > 14) nextErrors.dates = 'Trip duration must be between 1 and 14 nights.';
    }
    if (!Number.isInteger(count) || count < 1 || count > 8) nextErrors.travelerCount = 'Traveler count must be between 1 and 8.';
    if (draft.ages.slice(0, count).length !== count || draft.ages.slice(0, count).some(age => !/^\d+$/.test(age) || Number(age) > 95)) nextErrors.ages = 'Select an age from under 1 through 95 for each traveler.';
    const [budgetDollars, budgetFraction = ''] = draft.budget.split('.');
    const budgetCents = Number(budgetDollars) * 100 + Number(budgetFraction.padEnd(2, '0'));
    if (draft.budget && (!/^\d+(\.\d{1,2})?$/.test(draft.budget) || !Number.isSafeInteger(budgetCents) || budgetCents > 100000000)) nextErrors.budget = 'Enter a budget between $0 and $1,000,000 with at most two decimal places.';
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    if (onExplore) { setMessage(undefined); onExplore(); return; }
    if (!authenticated) { setMessage(undefined); onAuthenticationRequired(); return; }
    pendingRef.current = true; setPending(true); setMessage(undefined);
    try {
      const trip = await createTripFromDraft(draft);
      onSuccess(trip, mode, mode === 'STAY' ? draft.accommodationType : undefined);
    } catch (error) {
      if (error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') {
        setMessage('Your session has ended. Your Trip was not saved. Log in, then select Start planning again.');
        onAuthenticationRequired();
      } else if (error instanceof IdentityApiError && (error.code === 'VALIDATION_FAILED' || Object.keys(error.fields).length)) {
        setErrors({
          ...error.fields,
          ...(error.fields.startDate || error.fields.endDate ? {dates: error.fields.startDate ?? error.fields.endDate} : {}),
          ...(error.fields.travelerAges ? {ages: error.fields.travelerAges} : {}),
          ...(error.fields.budgetCents ? {budget: error.fields.budgetCents} : {}),
        });
        setMessage('Please correct the highlighted fields. Your Trip was not saved.');
      } else {
        setMessage('Your Trip was not saved. Check your connection and try again.');
      }
    } finally { pendingRef.current = false; setPending(false); }
  };

  return <section className="card profile-card" aria-labelledby="trips-heading">
    <p className="eyebrow wordmark">DeTour</p><h1 id="trips-heading" tabIndex={-1}>Trips</h1>
    <h2>Start a new trip</h2>
    <p>Explore your trip details now. An account is required when you save your selections.</p>
    {message && <p className="field-error" role="alert">{message}</p>}
    <form onSubmit={submit} noValidate>
      <div className="field"><label htmlFor="trip-name">Trip name</label><input id="trip-name" value={draft.name} aria-invalid={Boolean(errors.name)} aria-describedby={errors.name ? 'trip-name-error' : undefined} onChange={e => change({name: e.target.value})} />{errors.name && <p id="trip-name-error" className="field-error">{errors.name}</p>}</div>
      <div className="field"><label htmlFor="trip-destination">Destination</label><select id="trip-destination" value={draft.destinationKey} aria-invalid={Boolean(errors.destinationKey)} aria-describedby={errors.destinationKey ? 'trip-destination-error' : undefined} onChange={e => change({destinationKey: e.target.value, ...(draft.name === suggestedName(draft.destinationKey) ? {name: suggestedName(e.target.value)} : {})})}><option value="destination-sfo">San Francisco</option><option value="destination-muc">Munich</option><option value="destination-mex">Mexico City</option></select>{errors.destinationKey && <p id="trip-destination-error" className="field-error">{errors.destinationKey}</p>}</div>
      {mode === 'STAY' && <div className="field"><label htmlFor="trip-accommodation-type">Accommodation type</label><select id="trip-accommodation-type" value={draft.accommodationType} onChange={e => change({accommodationType: e.target.value as AccommodationType})}><option value="HOTEL">Hotel</option><option value="BED_AND_BREAKFAST">Bed &amp; Breakfast</option><option value="VACATION_RENTAL">Vacation Rental</option></select></div>}
      <div className="field-group"><div className="field"><label htmlFor="trip-start-date">Departure date</label><input id="trip-start-date" type="date" min="2027-03-01" max="2027-03-31" value={draft.startDate} aria-invalid={Boolean(errors.dates)} aria-describedby={errors.dates ? 'trip-dates-error' : undefined} onChange={e => change({startDate: e.target.value})} /></div><div className="field"><label htmlFor="trip-end-date">Return date</label><input id="trip-end-date" type="date" min="2027-03-01" max="2027-03-31" value={draft.endDate} aria-invalid={Boolean(errors.dates)} aria-describedby={errors.dates ? 'trip-dates-error' : undefined} onChange={e => change({endDate: e.target.value})} /></div></div>
      <p className="hint">Travel must take place between March 1 and March 31, 2027 (1–14 nights).</p>{errors.dates && <p id="trip-dates-error" className="field-error">{errors.dates}</p>}
      <div className="field"><label htmlFor="trip-budget">Budget (optional, dollars)</label><input id="trip-budget" type="number" min="0" max="1000000" step="0.01" value={draft.budget} aria-invalid={Boolean(errors.budget)} aria-describedby={errors.budget ? 'trip-budget-error' : undefined} onChange={e => change({budget: e.target.value})} />{errors.budget && <p id="trip-budget-error" className="field-error">{errors.budget}</p>}</div>
      <div className="field"><label htmlFor="trip-traveler-count">Travelers</label><input id="trip-traveler-count" type="number" min="1" max="8" value={draft.travelerCount} aria-invalid={Boolean(errors.travelerCount)} aria-describedby={errors.travelerCount ? 'trip-traveler-count-error' : undefined} onChange={e => change({travelerCount: e.target.value})} />{errors.travelerCount && <p id="trip-traveler-count-error" className="field-error">{errors.travelerCount}</p>}</div>
      {Number.isInteger(count) && count >= 1 && count <= 8 && <div className="field-group">{Array.from({length: count}, (_, i) => <div className="field" key={i}><label htmlFor={`trip-age-${i}`}>Traveler {i + 1} age</label><TravelerAgeInput id={`trip-age-${i}`} value={draft.ages[i] ?? ''} aria-invalid={Boolean(errors.ages)} aria-describedby={errors.ages ? 'trip-ages-error' : undefined} onChange={value => { const ages = [...draft.ages]; ages[i] = value; change({ages}); }} /></div>)}</div>}
      {errors.ages && <p id="trip-ages-error" className="field-error">{errors.ages}</p>}
      <button className="primary" type="submit" disabled={pending}>{pending ? 'Starting…' : onExplore ? 'Search options' : 'Start planning'}</button>
    </form>
  </section>;
}
