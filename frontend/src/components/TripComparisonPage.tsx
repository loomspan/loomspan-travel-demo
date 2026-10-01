import {useId, useState} from 'react';
import type {AccommodationType, AirfareComponentResponse, FlightCombinationResponse, FlightLegResponse, StayComponentResponse, StayOptionResponse, TripResponse} from '../api/tripsApi';
import {AirfareSearchSection, formatMinutes} from './AirfareSearchSection';
import {StaySearchSection} from './StaySearchSection';
import {computeAirfareTotalCents, computeStayTotalCents, formatCents} from './ItinerarySummaryTally';

type Props = {
  trip: TripResponse;
  draftId: string;
  name: string;
  initialSearch: 'AIRFARE' | 'STAY' | null;
  accommodationType?: AccommodationType;
  guest?: boolean;
  guestAirfare?: FlightCombinationResponse;
  guestStay?: StayOptionResponse;
  savedAirfare?: AirfareComponentResponse | null;
  savedStay?: StayComponentResponse | null;
  onSelectAirfare: (option: FlightCombinationResponse) => Promise<void>;
  onSelectStay: (option: StayOptionResponse) => Promise<void>;
  onRemoveAirfare: () => void;
  onRemoveStay: () => void;
  onSave?: () => void;
  pending?: boolean;
  lockedAirfare?: boolean;
  lockedStay?: boolean;
  purchasedTravelerCount?: number;
  purchasedStartDate?: string;
  purchasedEndDate?: string;
  purchaseCanceled?: boolean;
};

function formatLocalTime(instant: string, timeZone: string): string {
  try {
    return new Intl.DateTimeFormat('en-US', {month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', timeZone, timeZoneName: 'short'}).format(new Date(instant));
  } catch {
    return 'Schedule unavailable';
  }
}

function formatTripDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  if (!year || !month || !day) return date;
  return new Intl.DateTimeFormat('en-US', {month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC'}).format(new Date(Date.UTC(year, month - 1, day)));
}

function SelectedFlightLeg({label, leg}: {label: string; leg: FlightLegResponse}) {
  return <div className="guest-selected-leg">
    <span className="guest-selected-leg-label">{label}</span>
    <div>
      <strong>{leg.carrier} · {leg.flightNumber}</strong>
      <p>{leg.originAirportCode} {formatLocalTime(leg.departureTime, leg.departureTimeZone)} → {leg.destinationAirportCode} {formatLocalTime(leg.arrivalTime, leg.arrivalTimeZone)}</p>
      <p className="guest-selected-meta">{formatMinutes(leg.durationMinutes)} · {leg.stopCount === 0 ? 'Nonstop' : `${leg.stopCount} stop${leg.stopCount === 1 ? '' : 's'}`}</p>
    </div>
  </div>;
}

export function TripComparisonPage({trip, draftId, name, initialSearch, accommodationType = 'HOTEL', guest = false,
  guestAirfare, guestStay, savedAirfare, savedStay, onSelectAirfare, onSelectStay, onRemoveAirfare, onRemoveStay, onSave, pending = false, lockedAirfare = false, lockedStay = false, purchasedTravelerCount, purchasedStartDate, purchasedEndDate, purchaseCanceled = false}: Props) {
  const [searching, setSearching] = useState<'AIRFARE' | 'STAY' | null>(initialSearch);
  const id = useId();
  const airfare = guest ? guestAirfare : savedAirfare;
  const stay = guest ? guestStay : savedStay;
  const airfarePrice = guestAirfare ? guestAirfare.pricing.partyTotalPriceCents
    : savedAirfare ? computeAirfareTotalCents({airfare: savedAirfare, stay: null, rental: null}, (lockedAirfare ? purchasedTravelerCount : undefined) ?? trip.travelerCount) : 0;
  const stayPrice = guestStay ? guestStay.pricing.totalPriceCents
    : savedStay ? computeStayTotalCents({airfare: null, stay: savedStay, rental: null}) : 0;
  const startDate = formatTripDate(trip.startDate);
  const endDate = formatTripDate(trip.endDate);

  return <section className={`card profile-card guest-trip-explorer ${guest ? '' : 'saved-trip-explorer'}`} aria-labelledby={`${id}-heading`}>
    {guest ? <h1 id={`${id}-heading`} tabIndex={-1}>Explore your trip</h1> : <h4 id={`${id}-heading`}>Explore your trip</h4>}
    <p className="guest-trip-intro">Choose flights and stays for {name}. {guest ? 'Save when you are ready.' : 'Your selections are saved to this plan.'}</p>

    {(airfare || stay) && <section className="guest-selections" aria-labelledby={`${id}-selections-heading`}>
      <div className="guest-section-heading">{guest ? <h2 id={`${id}-selections-heading`}>Your selections</h2> : <h5 id={`${id}-selections-heading`}>Your selections</h5>}<p>Review what you picked before comparing more options.</p></div>
      <div className="guest-selection-grid">
        {airfare && <article className="guest-selection-card">
          <div className="guest-selection-card-header"><div>
            <span className="guest-selection-label">{lockedAirfare ? `${purchaseCanceled ? 'Canceled booking' : 'Confirmed'} flight · locked` : 'Selected flight'}</span>
            {guest ? <h3>{guestAirfare?.outbound.carrier} · round trip</h3> : <h6>{savedAirfare?.outboundCarrierName ?? 'Flight'} · round trip</h6>}
            <p>{guestAirfare ? `${guestAirfare.outbound.originAirportCode} ⇄ ${guestAirfare.outbound.destinationAirportCode}` : savedAirfare?.outboundDescription} · {(lockedAirfare ? purchasedTravelerCount : undefined) ?? trip.travelerCount} traveler{((lockedAirfare ? purchasedTravelerCount : undefined) ?? trip.travelerCount) === 1 ? '' : 's'}</p>
          </div><strong className="guest-selection-price">{formatCents(airfarePrice)}</strong></div>
          <div className="guest-selected-legs">
            {guestAirfare ? <><SelectedFlightLeg label="Outbound" leg={guestAirfare.outbound} /><SelectedFlightLeg label="Return" leg={guestAirfare.returnFlight} /></>
              : <><div className="guest-selected-leg"><span className="guest-selected-leg-label">Outbound</span><p>{savedAirfare?.outboundDescription}</p></div><div className="guest-selected-leg"><span className="guest-selected-leg-label">Return</span><p>{savedAirfare?.returnDescription}</p></div></>}
          </div>
          <div className="guest-selection-card-footer"><span>Party total, including both flights</span><button type="button" className="text-button" onClick={onRemoveAirfare} disabled={pending || lockedAirfare}>Remove flight</button></div>
        </article>}
        {stay && <article className="guest-selection-card">
          <div className="guest-selection-card-header"><div>
            <span className="guest-selection-label">{lockedStay ? `${purchaseCanceled ? 'Canceled booking' : 'Confirmed'} stay · locked` : 'Selected stay'}</span>
            {guest ? <h3>{stay.propertyName}</h3> : <h6>{stay.propertyName}</h6>}
            <p>{stay.unitName}{stay.locationDescription ? ` · ${stay.locationDescription}` : ''}</p>
          </div><strong className="guest-selection-price">{formatCents(stayPrice)}</strong></div>
          <div className="guest-selected-stay-details">
            <p><strong>Dates</strong><span>{lockedStay ? purchasedStartDate ?? startDate : startDate} – {lockedStay ? purchasedEndDate ?? endDate : endDate} · {guestStay?.pricing.nightCount ?? savedStay?.nights.length} nights</span></p>
            <p><strong>Details</strong><span>{guestStay?.pricing.requiredRooms ?? savedStay?.unitCount} rooms{guestStay ? ` · ${guestStay.guestRating} / 5 guest rating · ${(guestStay.distanceToCityCenterMeters / 1000).toFixed(1)} km to city center` : ''}</span></p>
          </div>
          <div className="guest-selection-card-footer"><span>Total stay price</span><button type="button" className="text-button" onClick={onRemoveStay} disabled={pending || lockedStay}>Remove stay</button></div>
        </article>}
      </div>
      {guest && onSave && <div className="guest-selection-actions"><button type="button" className="primary" onClick={onSave}>Save selections</button></div>}
    </section>}

    <section className="guest-compare" aria-labelledby={`${id}-compare-heading`}>
      <div className="guest-section-heading">{guest ? <h2 id={`${id}-compare-heading`}>Compare more options</h2> : <h5 id={`${id}-compare-heading`}>Compare more options</h5>}<p>Search results stay below your current selections.</p></div>
      <div className="guest-search-tabs" role="tablist" aria-label="Search type">
        <button type="button" role="tab" id={`${id}-flights-tab`} aria-controls={`${id}-flights-panel`} aria-selected={searching === 'AIRFARE'} onClick={() => setSearching('AIRFARE')} disabled={pending || lockedAirfare}>Search flights</button>
        <button type="button" role="tab" id={`${id}-stays-tab`} aria-controls={`${id}-stays-panel`} aria-selected={searching === 'STAY'} onClick={() => setSearching('STAY')} disabled={pending || lockedStay}>Search stays</button>
      </div>
      <div id={`${id}-flights-panel`} role="tabpanel" aria-labelledby={`${id}-flights-tab`} hidden={searching !== 'AIRFARE'}>
        {searching === 'AIRFARE' && <AirfareSearchSection trip={trip} draftId={draftId} publicSearch={guest} inTabs pending={pending} selectedKey={guestAirfare?.combinationKey}
          selectedFlightIds={savedAirfare ? [savedAirfare.outboundFlightInstanceId, savedAirfare.returnFlightInstanceId] : undefined} onSelect={onSelectAirfare} />}
      </div>
      <div id={`${id}-stays-panel`} role="tabpanel" aria-labelledby={`${id}-stays-tab`} hidden={searching !== 'STAY'}>
        {searching === 'STAY' && <StaySearchSection trip={trip} draftId={draftId} publicSearch={guest} inTabs initialType={accommodationType} pending={pending}
          selectedUnitId={guestStay?.accommodationUnitId ?? savedStay?.accommodationUnitId} onSelect={onSelectStay} />}
      </div>
      {searching === null && <p className="hint">Choose flights or stays to compare options.</p>}
    </section>
  </section>;
}
