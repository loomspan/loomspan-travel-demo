import {useEffect, useId, useRef, useState} from 'react';
import type {AccommodationType, AirfareComponentResponse, FlightCombinationResponse, StayComponentResponse, StayOptionResponse, RentalComponentResponse, RentalOptionResponse, ItineraryTallyResponse, TripResponse} from '../api/tripsApi';
import {AirfareSearchSection} from './AirfareSearchSection';
import {StaySearchSection} from './StaySearchSection';
import {RentalSearchSection, isDriverEligible} from './RentalSearchSection';
import {PlanDialog} from './PlanDialog';
import {FlightSchedule} from './FlightSchedule';
import {PlanSelectionsSummary, type SelectionRow} from './PlanSelectionsSummary';
import {formatTallyCents} from './ItinerarySummaryTally';
import {flightTotal, stayTotal, rentalTotal, priceDifference, validCents, flightComparable, stayComparable, sameRental, rentalCycles} from './planSearchComparison';
type Category = 'AIRFARE' | 'STAY' | 'RENTAL';
type Candidate = {category: Category; title: string; total?: number; basis: string; difference: string; save: () => Promise<boolean | void>; context: string};
type Props = {
  trip: TripResponse; draftId: string; name: string; initialSearch: Category | null;
  accommodationType?: AccommodationType; guest?: boolean; guestAirfare?: FlightCombinationResponse; guestStay?: StayOptionResponse;
  savedAirfare?: AirfareComponentResponse | null; savedStay?: StayComponentResponse | null; savedRental?: RentalComponentResponse | null;
  tally?: ItineraryTallyResponse; purchasedTally?: ItineraryTallyResponse;
  onSelectAirfare: (option: FlightCombinationResponse) => Promise<boolean | void>;
  onSelectStay: (option: StayOptionResponse) => Promise<boolean | void>;
  onSelectRental?: (option: RentalOptionResponse, pickup: string, returned: string) => Promise<boolean | void>;
  onRemoveAirfare: () => void; onRemoveStay: () => void; onRemoveRental?: () => void; onSave?: () => void;
  pending?: boolean; readOnly?: boolean; lockedAirfare?: boolean; lockedStay?: boolean; lockedRental?: boolean;
  purchasedTravelerCount?: number; purchasedStartDate?: string; purchasedEndDate?: string; purchaseCanceled?: boolean;
};
export function TripComparisonPage(props: Props) {
  const {trip, draftId, name, initialSearch, accommodationType = 'HOTEL', guest = false, guestAirfare, guestStay, savedAirfare, savedStay, savedRental, tally, purchasedTally, onSelectAirfare, onSelectStay, onSelectRental, onRemoveAirfare, onRemoveStay, onRemoveRental, onSave, pending = false, readOnly = false, lockedAirfare = false, lockedStay = false, lockedRental = false, purchasedTravelerCount, purchasedStartDate, purchasedEndDate, purchaseCanceled = false} = props;
  const id = useId(), tabs = useRef<Partial<Record<Category, HTMLButtonElement | null>>>({});
  const categories: Category[] = guest || !onSelectRental ? ['AIRFARE', 'STAY'] : ['AIRFARE', 'STAY', 'RENTAL'];
  const locked = {AIRFARE: lockedAirfare, STAY: lockedStay, RENTAL: lockedRental};
  const available = categories.filter(c => !readOnly && !locked[c]);
  const initial = initialSearch && available.includes(initialSearch) ? initialSearch : available.find(c => c !== 'RENTAL' || isDriverEligible(trip.travelerAges)) ?? null;
  const [searching, setSearching] = useState<Category | null>(initial), [focus, setFocus] = useState<Category | null>(initial ?? available[0] ?? null);
  const rovingFocus = focus && available.includes(focus) ? focus : searching && available.includes(searching) ? searching : available[0] ?? null;
  const [candidate, setCandidate] = useState<Candidate | null>(null), [saving, setSaving] = useState(false), [error, setError] = useState<string>();
  const busy = useRef(false);
  const airfare = guest ? guestAirfare : savedAirfare, stay = guest ? guestStay : savedStay;
  const party = lockedAirfare ? purchasedTravelerCount : trip.travelerCount;
  const price = (server: unknown, fallback: number | undefined) => validCents(server) ? server : fallback;
  const airfarePrice = guest ? guestAirfare?.pricing.partyTotalPriceCents : price((lockedAirfare ? purchasedTally : tally)?.airfareTotalCents, flightTotal(savedAirfare, party ?? NaN));
  const stayPrice = guest ? guestStay?.pricing.totalPriceCents : price((lockedStay ? purchasedTally : tally)?.stayTotalCents, stayTotal(savedStay));
  const carPrice = price((lockedRental ? purchasedTally : tally)?.rentalTotalCents, rentalTotal(savedRental));
  const dates = trip.startDate + ' to ' + trip.endDate;
  const flightBasis = (lockedAirfare ? purchasedStartDate ?? 'Dates unavailable' : trip.startDate) + ' to ' + (lockedAirfare ? purchasedEndDate ?? 'Dates unavailable' : trip.endDate) + ' · ' + (party ?? 'Unknown') + ' travelers · round trip, taxes and fees included';
  const stayBasis = (lockedStay ? purchasedStartDate ?? 'Dates unavailable' : trip.startDate) + ' to ' + (lockedStay ? purchasedEndDate ?? 'Dates unavailable' : trip.endDate) + ' · ' + (guestStay?.pricing.requiredRooms ?? savedStay?.unitCount ?? 'Unknown') + ' rooms · full stay, taxes and fees included';
  const carBasis = savedRental ? savedRental.pickupAt + ' to ' + savedRental.returnAt + ' · full rental, taxes and fees included' : '';
  const context = JSON.stringify([trip.id, draftId, trip.version, trip.startDate, trip.endDate, trip.travelerCount, trip.travelerAges, airfare, stay, savedRental, locked, readOnly]);
  const live = useRef({context, pending, readOnly}); live.current = {context, pending, readOnly};
  useEffect(() => {if (!busy.current) {setCandidate(null); setError(undefined);}}, [context]);
  useEffect(() => {if (pending && !busy.current) setCandidate(null);}, [pending]);
  useEffect(() => {if (searching && !available.includes(searching) || !searching && initial) {setSearching(initial); setFocus(initial ?? available[0] ?? null);}}, [readOnly, lockedAirfare, lockedStay, lockedRental]);
  const change = (category: Category, moveFocus = false) => {
    if (pending || saving || !available.includes(category)) return;
    setSearching(category); setFocus(category); if (moveFocus) tabs.current[category]?.focus();
  };
  const mutation = async (save: () => Promise<boolean | void>): Promise<boolean> => {
    if (busy.current || live.current.pending || live.current.readOnly) return false;
    busy.current = true; setSaving(true); setError(undefined);
    try {const result = await save(); if (result === false) {setError('Could not save this choice. Your previous selection is unchanged. Retry or cancel.'); return false;} return true;}
    catch (e) {setError(e instanceof Error ? e.message : 'Could not save this choice. Your previous selection is unchanged.'); return false;}
    finally {busy.current = false; setSaving(false);}
  };
  const choose = async (next: Omit<Candidate, 'context'>, current: boolean) => {
    if (pending || saving || readOnly || locked[next.category]) return;
    if (current) {setError(undefined); setCandidate({...next, context});} else await mutation(next.save);
  };
  const flightDifference = (o: FlightCombinationResponse) => priceDifference(flightComparable(airfare, o, trip.startDate, trip.endDate, trip.travelerCount) ? airfarePrice : undefined, o.pricing.partyTotalPriceCents);
  const stayDifference = (o: StayOptionResponse) => priceDifference(stayComparable(stay, o, trip.startDate, trip.endDate) ? stayPrice : undefined, o.pricing.totalPriceCents);
  const carDifference = (o: RentalOptionResponse, p: string, r: string) => priceDifference(rentalCycles(p, r) && rentalCycles(savedRental?.pickupAt ?? '', savedRental?.returnAt ?? '') ? carPrice : undefined, o.pricing.totalPriceCents);
  const label = (category: string, present: boolean) => present ? 'Replace ' + category : guest ? 'Add ' + category : 'Save ' + category + ' to plan';
  const lockedLabel = (category: Category) => locked[category] ? (purchaseCanceled ? 'Canceled booking' : 'Confirmed') + ' ' + (category === 'AIRFARE' ? 'flight' : category === 'STAY' ? 'stay' : 'car') + ' · locked' : undefined;
  const rows: SelectionRow[] = [
    {category: 'AIRFARE', label: 'Flight', present: Boolean(airfare), title: guestAirfare ? guestAirfare.outbound.carrier + ' · round trip' : savedAirfare?.outboundCarrierName ? savedAirfare.outboundCarrierName + ' · round trip' : savedAirfare?.outboundDescription ?? '', details: guestAirfare ? guestAirfare.outbound.originAirportCode + ' ⇄ ' + guestAirfare.outbound.destinationAirportCode + ' · ' + guestAirfare.outbound.flightNumber + ' / ' + guestAirfare.returnFlight.flightNumber : (savedAirfare?.outboundDescription ?? '') + ' · ' + (savedAirfare?.returnDescription ?? ''), basis: flightBasis, total: airfarePrice, locked: lockedLabel('AIRFARE'), remove: onRemoveAirfare},
    {category: 'STAY', label: 'Stay', present: Boolean(stay), title: stay?.propertyName ?? '', details: (stay?.unitName ?? '') + (stay?.locationDescription ? ' · ' + stay.locationDescription : ''), basis: stayBasis, total: stayPrice, locked: lockedLabel('STAY'), remove: onRemoveStay},
    ...(!guest && onSelectRental ? [{category: 'RENTAL' as const, label: 'Car', present: Boolean(savedRental), title: savedRental?.vehicleClassName ?? '', details: (savedRental?.locationName ?? '') + ' · ' + (savedRental?.unitIdentifier ?? ''), basis: carBasis, total: carPrice, locked: lockedLabel('RENTAL'), remove: onRemoveRental}] : []),
  ];
  rows[0].extraDetails = <>
    <FlightSchedule departureAirport={guestAirfare?.outbound.originAirportCode ?? trip.originAirportCode} departureTime={guestAirfare?.outbound.departureTime ?? savedAirfare?.outboundDepartureTime} departureTimeZone={guestAirfare?.outbound.departureTimeZone ?? savedAirfare?.outboundDepartureTimeZone} arrivalAirport={guestAirfare?.outbound.destinationAirportCode ?? trip.destinationKey.replace('destination-', '').toUpperCase()} arrivalTime={guestAirfare?.outbound.arrivalTime ?? savedAirfare?.outboundArrivalTime} arrivalTimeZone={guestAirfare?.outbound.arrivalTimeZone ?? savedAirfare?.outboundArrivalTimeZone} />
    <FlightSchedule departureAirport={guestAirfare?.returnFlight.originAirportCode ?? trip.destinationKey.replace('destination-', '').toUpperCase()} departureTime={guestAirfare?.returnFlight.departureTime ?? savedAirfare?.returnDepartureTime} departureTimeZone={guestAirfare?.returnFlight.departureTimeZone ?? savedAirfare?.returnDepartureTimeZone} arrivalAirport={guestAirfare?.returnFlight.destinationAirportCode ?? trip.originAirportCode} arrivalTime={guestAirfare?.returnFlight.arrivalTime ?? savedAirfare?.returnArrivalTime} arrivalTimeZone={guestAirfare?.returnFlight.arrivalTimeZone ?? savedAirfare?.returnArrivalTimeZone} />
  </>;
  const total = guest ? rows.every(r => !r.present || validCents(r.total)) ? rows.reduce((sum, r) => sum + (r.present ? r.total! : 0), 0) : undefined : tally?.grandTotalCents;
  const partial = tally?.partial ?? rows.some(r => !r.present), current = rows.find(r => r.category === searching && r.present);
  return <section className={'card guest-trip-explorer ' + (guest ? '' : 'saved-trip-explorer')} aria-labelledby={id + '-heading'}>
    {guest ? <h1 id={id + '-heading'} tabIndex={-1}>Build your plan</h1> : <h2 id={id + '-heading'}>Build your plan</h2>}
    <p className="plan-search-context">{name} · {trip.originAirportCode ? trip.originAirportCode + ' → ' : ''}{trip.destinationName ?? trip.destinationKey} · {dates} · {trip.travelerCount} traveler{trip.travelerCount === 1 ? '' : 's'}</p>
    <p className="hint">{guest ? 'Explore flights and stays. Your choices are not yet saved.' : 'Search with saved planning details. Add choices to get started, or explore alternatives.'}</p>
    <div className="plan-search-layout"><section className="plan-search-main" aria-label="Search options">
      <div className="guest-search-tabs" role="tablist" aria-label="Search type">{categories.map(c => <button key={c} type="button" className="secondary" role="tab" ref={node => {tabs.current[c] = node;}} id={id + '-' + c + '-tab'} aria-controls={id + '-' + c + '-panel'} aria-selected={searching === c} tabIndex={rovingFocus === c && !pending && !saving && available.includes(c) ? 0 : -1} disabled={pending || saving || !available.includes(c)} onClick={() => change(c)} onKeyDown={event => {
        if (!['ArrowRight', 'ArrowLeft', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault(); const index = available.indexOf(c);
        const next = event.key === 'Home' ? available[0] : event.key === 'End' ? available.at(-1)! : available[(index + (event.key === 'ArrowRight' ? 1 : -1) + available.length) % available.length];
        setFocus(next); tabs.current[next]?.focus();
      }}>Search {c === 'AIRFARE' ? 'flights' : c === 'STAY' ? 'stays' : 'cars'}</button>)}</div>
      {current && <section className="panel-inset plan-current-selection" aria-label="Current selection"><h3>Current {current.label.toLowerCase()}</h3><strong>{current.title}</strong><p>{formatTallyCents(current.total)} · {current.basis}</p></section>}
      {categories.map(c => <div key={c} id={id + '-' + c + '-panel'} role="tabpanel" aria-labelledby={id + '-' + c + '-tab'} hidden={searching !== c}>
        {searching === c && c === 'AIRFARE' && <AirfareSearchSection trip={trip} draftId={draftId} publicSearch={guest} inTabs pending={pending || saving || readOnly || lockedAirfare} selectedKey={guestAirfare?.combinationKey} selectedFlightIds={savedAirfare ? [savedAirfare.outboundFlightInstanceId, savedAirfare.returnFlightInstanceId] : undefined} actionLabel={label('flight', Boolean(airfare))} comparison={airfare ? flightDifference : undefined} onSelect={async o => {
          if (guestAirfare?.combinationKey === o.combinationKey || savedAirfare && savedAirfare.outboundFlightInstanceId === o.outbound.flightInstanceId && savedAirfare.returnFlightInstanceId === o.returnFlight.flightInstanceId) return;
          await choose({category: 'AIRFARE', title: o.outbound.carrier + ' ' + o.outbound.flightNumber + ' / ' + o.returnFlight.carrier + ' ' + o.returnFlight.flightNumber, total: o.pricing.partyTotalPriceCents, basis: dates + ' · ' + o.pricing.travelerCount + ' travelers · round trip, taxes and fees included', difference: flightDifference(o), save: () => onSelectAirfare(o)}, Boolean(airfare));
        }} />}
        {searching === c && c === 'STAY' && <StaySearchSection trip={trip} draftId={draftId} publicSearch={guest} inTabs initialType={accommodationType} pending={pending || saving || readOnly || lockedStay} selectedUnitId={stay?.accommodationUnitId} selectedRoomCount={guestStay?.pricing.requiredRooms ?? savedStay?.unitCount} actionLabel={label('stay', Boolean(stay))} comparison={stay ? stayDifference : undefined} onSelect={async o => {await choose({category: 'STAY', title: o.propertyName + ' · ' + o.unitName, total: o.pricing.totalPriceCents, basis: dates + ' · ' + o.pricing.requiredRooms + ' rooms · full stay, taxes and fees included', difference: stayDifference(o), save: () => onSelectStay(o)}, Boolean(stay));}} />}
        {searching === c && c === 'RENTAL' && onSelectRental && <RentalSearchSection trip={trip} draftId={draftId} inTabs pending={pending || saving || readOnly || lockedRental} selectedRental={savedRental} actionLabel={label('car', Boolean(savedRental))} comparison={savedRental ? carDifference : undefined} onSelect={async (o, p, r) => {
          if (sameRental(savedRental, o.rentalUnitId, p, r)) return;
          await choose({category: 'RENTAL', title: o.vehicleClassName + ' · ' + o.locationName, total: o.pricing.totalPriceCents, basis: p + ' to ' + r + ' · full rental, taxes and fees included', difference: carDifference(o, p, r), save: () => onSelectRental(o, p, r)}, Boolean(savedRental));
        }} />}
      </div>)}
      {searching === null && <p className="callout callout-neutral">{available.length ? 'A qualifying driver is needed to select a car. Open Search cars for eligibility details.' : 'This plan is read-only. Purchased selections and booking history remain available.'}</p>}
      {error && !candidate && <p role="alert" className="field-error">{error}</p>}
    </section><PlanSelectionsSummary rows={rows} total={validCents(total) ? total : undefined} partial={partial} guest={guest} pending={pending || saving || readOnly} onChange={c => change(c, true)} onSave={onSave} /></div>
    {candidate && <PlanDialog title={'Replace ' + (candidate.category === 'AIRFARE' ? 'flight' : candidate.category === 'STAY' ? 'stay' : 'car') + '?'} pending={saving || pending} onClose={() => {if (!busy.current && !pending) setCandidate(null);}}>
      <p>{guest ? 'Your current local choice remains until you confirm.' : 'Your current choice stays in your plan until this replacement succeeds.'}</p>
      <div className="plan-replacement-details"><section><h3>Current choice</h3><strong>{rows.find(r => r.category === candidate.category)?.title}</strong><p>{formatTallyCents(rows.find(r => r.category === candidate.category)?.total)}</p><p>{rows.find(r => r.category === candidate.category)?.basis}</p></section><section><h3>New choice</h3><strong>{candidate.title}</strong><p>{formatTallyCents(candidate.total)}</p><p>{candidate.basis}</p></section></div>
      <p className="plan-price-difference">{candidate.difference}</p>{error && <p className="field-error" role="alert">{error}</p>}
      <div className="modal-actions"><button type="button" className="primary" disabled={saving || pending || candidate.context !== context || readOnly || locked[candidate.category]} onClick={() => {if (candidate.context !== live.current.context) return; void mutation(candidate.save).then(ok => {if (ok) setCandidate(null);});}}>Confirm replacement</button><button type="button" className="text-button" disabled={saving || pending} onClick={() => setCandidate(null)}>Cancel</button></div>
    </PlanDialog>}
  </section>;
}
