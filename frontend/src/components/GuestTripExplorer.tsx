import {useState} from 'react';
import type {FlightCombinationResponse, StayOptionResponse, TripResponse} from '../api/tripsApi';
import type {TripStartDraft} from './TripStartForm';
import type {StartMode} from './HomeScreen';
import {AirfareSearchSection} from './AirfareSearchSection';
import {StaySearchSection} from './StaySearchSection';
import {formatCents} from './ItinerarySummaryTally';

export type GuestSelections = {airfare?: FlightCombinationResponse; stay?: StayOptionResponse};

type Props = {
  draft: TripStartDraft;
  mode: StartMode;
  selections: GuestSelections;
  onChange: (selections: GuestSelections) => void;
  onSave: () => void;
};

export function GuestTripExplorer({draft, mode, selections, onChange, onSave}: Props) {
  const [searching, setSearching] = useState<'AIRFARE' | 'STAY' | null>(mode === 'STAY' ? 'STAY' : 'AIRFARE');
  const trip = {
    id: 'guest', destinationKey: draft.destinationKey, startDate: draft.startDate,
    endDate: draft.endDate, travelerCount: Number(draft.travelerCount),
    budgetCents: draft.budget ? Math.round(Number(draft.budget) * 100) : null,
  } as TripResponse;
  const hasSelection = Boolean(selections.airfare || selections.stay);

  return <section className="card profile-card" aria-labelledby="guest-search-heading">
    <h1 id="guest-search-heading" tabIndex={-1}>Explore your trip</h1>
    <p>Choose flights and stays for {draft.name}. Save when you are ready.</p>
    <div className="field-group">
      <button type="button" className="secondary" onClick={() => setSearching('AIRFARE')}>Search flights</button>
      <button type="button" className="secondary" onClick={() => setSearching('STAY')}>Search stays</button>
    </div>
    {selections.airfare && <p>Selected flight: {selections.airfare.outbound.carrier} {selections.airfare.outbound.flightNumber} / {selections.airfare.returnFlight.carrier} {selections.airfare.returnFlight.flightNumber} — {formatCents(selections.airfare.pricing.partyTotalPriceCents)} <button type="button" className="text-button" onClick={() => onChange({...selections, airfare: undefined})}>Remove flight</button></p>}
    {selections.stay && <p>Selected stay: {selections.stay.propertyName}, {selections.stay.unitName} — {formatCents(selections.stay.pricing.totalPriceCents)} <button type="button" className="text-button" onClick={() => onChange({...selections, stay: undefined})}>Remove stay</button></p>}
    {hasSelection && <button type="button" className="primary" onClick={onSave}>Save selections</button>}
    {searching === 'AIRFARE' && <AirfareSearchSection trip={trip} draftId="guest" publicSearch pending={false} onCancel={() => setSearching(null)} onSelect={async option => { onChange({...selections, airfare: option}); setSearching(null); }} />}
    {searching === 'STAY' && <StaySearchSection trip={trip} draftId="guest" publicSearch initialType={draft.accommodationType} pending={false} onCancel={() => setSearching(null)} onSelect={async option => { onChange({...selections, stay: option}); setSearching(null); }} />}
  </section>;
}
