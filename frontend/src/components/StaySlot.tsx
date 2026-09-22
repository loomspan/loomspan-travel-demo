import type {
  TripResponse,
  StayComponentResponse,
  StayOptionResponse,
  AccommodationType,
} from '../api/tripsApi';
import {StaySearchSection} from './StaySearchSection';
import {formatCents, computeStayTotalCents} from './ItinerarySummaryTally';

type StaySlotProps = {
  trip: TripResponse;
  draftId: string;
  selectedStay: StayComponentResponse | null;
  mode: 'empty' | 'searching' | 'selected';
  highlighted?: boolean;
  initialType?: AccommodationType;
  onStartSearch: () => void;
  onSelect: (option: StayOptionResponse) => Promise<void>;
  onChange: () => void;
  onRemove: () => void;
  onCancelSearch: () => void;
  pending: boolean;
};

export function StaySlot({
  trip,
  draftId,
  selectedStay,
  mode,
  highlighted = false,
  initialType = 'HOTEL',
  onStartSearch,
  onSelect,
  onChange,
  onRemove,
  onCancelSearch,
  pending,
}: StaySlotProps) {
  const stayTotal = selectedStay
    ? computeStayTotalCents({airfare: null, stay: selectedStay, rental: null})
    : 0;

  const showSelected = mode === 'selected' && Boolean(selectedStay);
  const showEmpty = mode === 'empty' || (mode === 'selected' && !selectedStay);

  return (
    <section className={`card component-slot stay-slot ${highlighted ? 'slot-highlighted' : ''}`} aria-labelledby="stay-slot-heading">
      <div className="slot-header">
        <h3 id="stay-slot-heading" tabIndex={-1}>Stay</h3>
        {showSelected && <span className="badge badge-success">Selected</span>}
      </div>

      {showEmpty && (
        <div className="slot-empty-state">
          <p className="hint">No accommodations selected for this itinerary.</p>
          <button
            type="button"
            className="secondary"
            onClick={onStartSearch}
            disabled={pending}
          >
            Add stay
          </button>
        </div>
      )}

      {mode === 'searching' && (
        <StaySearchSection
          trip={trip}
          draftId={draftId}
          initialType={initialType}
          onSelect={onSelect}
          onCancel={onCancelSearch}
          pending={pending}
        />
      )}

      {showSelected && selectedStay && (
        <div className="slot-selected-content">
          <div className="selected-details">
            <p className="selected-title">{selectedStay.propertyName}</p>
            <p className="selected-meta">{selectedStay.unitName}</p>
            <p className="selected-meta">
              {selectedStay.unitCount} room{selectedStay.unitCount === 1 ? '' : 's'} •{' '}
              {selectedStay.nights.length} night{selectedStay.nights.length === 1 ? '' : 's'}
            </p>
            <p className="selected-price">
              Total price: <strong>{formatCents(stayTotal)}</strong>{' '}
              <span className="hint">(taxes &amp; fees included)</span>
            </p>
          </div>
          <div className="slot-actions">
            <button
              type="button"
              className="text-button"
              onClick={onChange}
              disabled={pending}
            >
              Change stay
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
