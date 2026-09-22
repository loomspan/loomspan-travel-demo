import type {
  TripResponse,
  AirfareComponentResponse,
  FlightCombinationResponse,
} from '../api/tripsApi';
import {AirfareSearchSection} from './AirfareSearchSection';
import {formatCents, computeAirfareTotalCents} from './ItinerarySummaryTally';

type AirfareSlotProps = {
  trip: TripResponse;
  draftId: string;
  selectedAirfare: AirfareComponentResponse | null;
  mode: 'empty' | 'searching' | 'selected';
  highlighted?: boolean;
  onStartSearch: () => void;
  onSelect: (option: FlightCombinationResponse) => Promise<void>;
  onChange: () => void;
  onRemove: () => void;
  onCancelSearch: () => void;
  pending: boolean;
};

export function AirfareSlot({
  trip,
  draftId,
  selectedAirfare,
  mode,
  highlighted = false,
  onStartSearch,
  onSelect,
  onChange,
  onRemove,
  onCancelSearch,
  pending,
}: AirfareSlotProps) {
  const airfareTotal = selectedAirfare
    ? computeAirfareTotalCents({airfare: selectedAirfare, stay: null, rental: null}, trip.travelerCount)
    : 0;

  const showSelected = mode === 'selected' && Boolean(selectedAirfare);
  const showEmpty = mode === 'empty' || (mode === 'selected' && !selectedAirfare);

  return (
    <section className={`card component-slot airfare-slot ${highlighted ? 'slot-highlighted' : ''}`} aria-labelledby="airfare-slot-heading">
      <div className="slot-header">
        <h3 id="airfare-slot-heading" tabIndex={-1}>Airfare</h3>
        {showSelected && (
          <span className="badge badge-success">Selected</span>
        )}
      </div>

      {showEmpty && (
        <div className="slot-empty-state">
          <p className="hint">No flights selected for this itinerary.</p>
          <button
            type="button"
            className="secondary"
            onClick={onStartSearch}
            disabled={pending}
          >
            Add airfare
          </button>
        </div>
      )}

      {mode === 'searching' && (
        <AirfareSearchSection
          trip={trip}
          draftId={draftId}
          onSelect={onSelect}
          onCancel={onCancelSearch}
          pending={pending}
        />
      )}

      {showSelected && selectedAirfare && (
        <div className="slot-selected-content">
          <div className="selected-details">
            <p className="selected-title">
              Flight #{selectedAirfare.outboundFlightInstanceId} / #{selectedAirfare.returnFlightInstanceId}
            </p>
            <p className="selected-meta">{selectedAirfare.outboundDescription}</p>
            <p className="selected-meta">{selectedAirfare.returnDescription}</p>
            <p className="selected-price">
              Total price: <strong>{formatCents(airfareTotal)}</strong>{' '}
              <span className="hint">
                (includes all taxes and fees for {trip.travelerCount} traveler
                {trip.travelerCount === 1 ? '' : 's'})
              </span>
            </p>
          </div>
          <div className="slot-actions">
            <button
              type="button"
              className="text-button"
              onClick={onChange}
              disabled={pending}
            >
              Change flight
            </button>
            <button
              type="button"
              className="text-button delete-button"
              onClick={onRemove}
              disabled={pending}
            >
              Remove
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
