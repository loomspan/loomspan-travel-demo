import type {
  TripResponse,
  RentalComponentResponse,
  RentalOptionResponse,
} from '../api/tripsApi';
import {RentalSearchSection} from './RentalSearchSection';
import {formatCents, computeRentalTotalCents} from './ItinerarySummaryTally';

type RentalSlotProps = {
  trip: TripResponse;
  draftId: string;
  selectedRental: RentalComponentResponse | null;
  mode: 'hidden' | 'searching' | 'selected';
  onSelect: (option: RentalOptionResponse, pickupAtIso: string, returnAtIso: string) => Promise<void>;
  onChange: () => void;
  onRemove: () => void;
  onCancelSearch: () => void;
  pending: boolean;
};

export function RentalSlot({
  trip,
  draftId,
  selectedRental,
  mode,
  onSelect,
  onChange,
  onRemove,
  onCancelSearch,
  pending,
}: RentalSlotProps) {
  if (mode === 'hidden' || (mode === 'selected' && !selectedRental)) return null;

  const rentalTotal = selectedRental
    ? computeRentalTotalCents({airfare: null, stay: null, rental: selectedRental})
    : 0;

  const showSelected = mode === 'selected' && Boolean(selectedRental);

  return (
    <section className="card component-slot rental-slot" aria-labelledby="rental-slot-heading">
      <div className="slot-header">
        <h3 id="rental-slot-heading">Rental Car</h3>
        {showSelected && <span className="badge badge-success">Selected</span>}
      </div>

      {mode === 'searching' && (
        <RentalSearchSection
          trip={trip}
          draftId={draftId}
          onSelect={onSelect}
          onCancel={onCancelSearch}
          pending={pending}
        />
      )}

      {showSelected && selectedRental && (
        <div className="slot-selected-content">
          <div className="selected-details">
            <p className="selected-title">{selectedRental.vehicleClassName}</p>
            <p className="selected-meta">{selectedRental.locationName}</p>
            <p className="selected-meta">
              Pickup: {selectedRental.pickupAt} • Return: {selectedRental.returnAt}
            </p>
            <p className="selected-price">
              Total price: <strong>{formatCents(rentalTotal)}</strong>{' '}
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
              Change car
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
