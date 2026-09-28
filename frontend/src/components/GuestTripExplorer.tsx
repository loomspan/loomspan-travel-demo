import {useState} from 'react';
import type {FlightCombinationResponse, FlightLegResponse, StayOptionResponse, TripResponse} from '../api/tripsApi';
import type {TripStartDraft} from './TripStartForm';
import type {StartMode} from './HomeScreen';
import {AirfareSearchSection, formatMinutes} from './AirfareSearchSection';
import {StaySearchSection} from './StaySearchSection';
import {formatCents} from './ItinerarySummaryTally';

export type GuestSelections = {airfare?: FlightCombinationResponse; stay?: StayOptionResponse};

function formatLocalTime(instant: string, timeZone: string): string {
  try {
    return new Intl.DateTimeFormat('en-US', {
      month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
      timeZone, timeZoneName: 'short',
    }).format(new Date(instant));
  } catch {
    return 'Schedule unavailable';
  }
}

function formatTripDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  if (!year || !month || !day) return date;
  return new Intl.DateTimeFormat('en-US', {month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC'})
    .format(new Date(Date.UTC(year, month - 1, day)));
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

type Props = {
  draft: TripStartDraft;
  mode: StartMode;
  selections: GuestSelections;
  onChange: (selections: GuestSelections) => void;
  onSave: () => void;
};

export function GuestTripExplorer({draft, mode, selections, onChange, onSave}: Props) {
  const [searching, setSearching] = useState<'AIRFARE' | 'STAY'>(mode === 'STAY' ? 'STAY' : 'AIRFARE');
  const trip = {
    id: 'guest', destinationKey: draft.destinationKey, startDate: draft.startDate,
    endDate: draft.endDate, travelerCount: Number(draft.travelerCount),
    budgetCents: draft.budget ? Math.round(Number(draft.budget) * 100) : null,
  } as TripResponse;
  const hasSelection = Boolean(selections.airfare || selections.stay);

  return <section className="card profile-card guest-trip-explorer" aria-labelledby="guest-search-heading">
    <h1 id="guest-search-heading" tabIndex={-1}>Explore your trip</h1>
    <p className="guest-trip-intro">Choose flights and stays for {draft.name}. Save when you are ready.</p>

    {hasSelection && <section className="guest-selections" aria-labelledby="guest-selections-heading">
      <div className="guest-section-heading"><h2 id="guest-selections-heading">Your selections</h2><p>Review what you picked before comparing more options.</p></div>
      <div className="guest-selection-grid">
        {selections.airfare && <article className="guest-selection-card">
          <div className="guest-selection-card-header"><div>
            <span className="guest-selection-label">Selected flight</span>
            <h3>{selections.airfare.outbound.carrier} · round trip</h3>
            <p>{selections.airfare.outbound.originAirportCode} ⇄ {selections.airfare.outbound.destinationAirportCode} · {selections.airfare.pricing.travelerCount} traveler{selections.airfare.pricing.travelerCount === 1 ? '' : 's'}</p>
          </div><strong className="guest-selection-price">{formatCents(selections.airfare.pricing.partyTotalPriceCents)}</strong></div>
          <div className="guest-selected-legs">
            <SelectedFlightLeg label="Outbound" leg={selections.airfare.outbound} />
            <SelectedFlightLeg label="Return" leg={selections.airfare.returnFlight} />
          </div>
          <div className="guest-selection-card-footer"><span>Party total, including both flights</span><button type="button" className="text-button" onClick={() => onChange({...selections, airfare: undefined})}>Remove flight</button></div>
        </article>}
        {selections.stay && <article className="guest-selection-card">
          <div className="guest-selection-card-header"><div>
            <span className="guest-selection-label">Selected stay</span>
            <h3>{selections.stay.propertyName}</h3>
            <p>{selections.stay.unitName}{selections.stay.locationDescription ? ` · ${selections.stay.locationDescription}` : ''}</p>
          </div><strong className="guest-selection-price">{formatCents(selections.stay.pricing.totalPriceCents)}</strong></div>
          <div className="guest-selected-stay-details">
            <p><strong>Dates</strong><span>{formatTripDate(draft.startDate)} – {formatTripDate(draft.endDate)} · {selections.stay.pricing.nightCount} night{selections.stay.pricing.nightCount === 1 ? '' : 's'}</span></p>
            <p><strong>Details</strong><span>{selections.stay.pricing.requiredRooms} room{selections.stay.pricing.requiredRooms === 1 ? '' : 's'} · {selections.stay.guestRating} / 5 guest rating · {(selections.stay.distanceToCityCenterMeters / 1000).toFixed(1)} km to city center</span></p>
          </div>
          <div className="guest-selection-card-footer"><span>Total stay price</span><button type="button" className="text-button" onClick={() => onChange({...selections, stay: undefined})}>Remove stay</button></div>
        </article>}
      </div>
      <div className="guest-selection-actions"><button type="button" className="primary" onClick={onSave}>Save selections</button></div>
    </section>}

    <section className="guest-compare" aria-labelledby="guest-compare-heading">
      <div className="guest-section-heading"><h2 id="guest-compare-heading">Compare more options</h2><p>Search results stay below your current selections.</p></div>
      <div className="guest-search-tabs" role="tablist" aria-label="Search type">
        <button type="button" role="tab" id="guest-flights-tab" aria-controls="guest-flights-panel" aria-selected={searching === 'AIRFARE'} onClick={() => setSearching('AIRFARE')}>Search flights</button>
        <button type="button" role="tab" id="guest-stays-tab" aria-controls="guest-stays-panel" aria-selected={searching === 'STAY'} onClick={() => setSearching('STAY')}>Search stays</button>
      </div>
      <div id="guest-flights-panel" role="tabpanel" aria-labelledby="guest-flights-tab" hidden={searching !== 'AIRFARE'}>
        {searching === 'AIRFARE' && <AirfareSearchSection trip={trip} draftId="guest" publicSearch inTabs pending={false} selectedKey={selections.airfare?.combinationKey} onSelect={async option => { onChange({...selections, airfare: option}); }} />}
      </div>
      <div id="guest-stays-panel" role="tabpanel" aria-labelledby="guest-stays-tab" hidden={searching !== 'STAY'}>
        {searching === 'STAY' && <StaySearchSection trip={trip} draftId="guest" publicSearch inTabs initialType={draft.accommodationType} pending={false} selectedUnitId={selections.stay?.accommodationUnitId} onSelect={async option => { onChange({...selections, stay: option}); }} />}
      </div>
    </section>
  </section>;
}
