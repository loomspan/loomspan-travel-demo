import type {FlightCombinationResponse, StayOptionResponse, TripResponse} from '../api/tripsApi';
import type {TripStartDraft} from './TripStartForm';
import type {StartMode} from './HomeScreen';
import {TripComparisonPage} from './TripComparisonPage';

export type GuestSelections = {airfare?: FlightCombinationResponse; stay?: StayOptionResponse};

type Props = {
  draft: TripStartDraft;
  mode: StartMode;
  selections: GuestSelections;
  onChange: (selections: GuestSelections) => void;
  onSave: () => void;
};

export function GuestTripExplorer({draft, mode, selections, onChange, onSave}: Props) {
  const trip = {
    id: 'guest', destinationKey: draft.destinationKey, startDate: draft.startDate,
    endDate: draft.endDate, travelerCount: Number(draft.travelerCount),
    budgetCents: draft.budget ? Math.round(Number(draft.budget) * 100) : null,
  } as TripResponse;

  return <TripComparisonPage
    trip={trip}
    draftId="guest"
    name={draft.name}
    initialSearch={mode === 'STAY' ? 'STAY' : 'AIRFARE'}
    accommodationType={draft.accommodationType}
    guest
    guestAirfare={selections.airfare}
    guestStay={selections.stay}
    onSelectAirfare={async option => onChange({...selections, airfare: option})}
    onSelectStay={async option => onChange({...selections, stay: option})}
    onRemoveAirfare={() => onChange({...selections, airfare: undefined})}
    onRemoveStay={() => onChange({...selections, stay: undefined})}
    onSave={onSave}
  />;
}
