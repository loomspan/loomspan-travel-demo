import type {AlternativeResponse} from '../api/tripsApi';
import {formatCents} from './ItinerarySummaryTally';

type AlternativeCardProps = {
  alternative: AlternativeResponse;
  tripExpired?: boolean;
  tripCanceled?: boolean;
  hasBookingHistory?: boolean;
  isSelectedForCompare?: boolean;
  isBooked?: boolean;
  hasActiveBooking?: boolean;
  onToggleCompare?: (alternativeId: string, checked: boolean) => void;
  onSelectForBookingReview?: (alternativeId: string) => void;
  onViewBookingDetails?: () => void;
  onOpenForEditing?: (optionId: string) => void;
  onRename?: (optionId: string) => void;
};

export function AlternativeCard({
  alternative,
  tripExpired = false,
  tripCanceled = false,
  hasBookingHistory = false,
  isSelectedForCompare = false,
  isBooked = false,
  hasActiveBooking = false,
  onToggleCompare,
  onSelectForBookingReview,
  onViewBookingDetails,
  onOpenForEditing,
  onRename,
}: AlternativeCardProps) {
  const selections = alternative.selections;
  const hasSelections = Boolean(
    selections && (selections.airfare || selections.stay || selections.rental)
  );

  return (
    <article
      className="card alternative-card alternative-card-planned"
      aria-labelledby={`alt-heading-${alternative.id}`}
    >
      <div className="alternative-card-header">
        <div>
          <div className="badge-row">
            <span className="badge badge-planned">Saved option</span>
            {isBooked && <span className="badge badge-booked">Booking</span>}
            {tripCanceled && <span className="badge badge-canceled">Canceled Trip</span>}
            {tripExpired && <span className="badge badge-expired">Expired</span>}
          </div>
          <h4 id={`alt-heading-${alternative.id}`} className="alternative-card-title">
            {alternative.name || 'Saved option'}
          </h4>
          <p className="trip-meta">{alternative.startDate} to {alternative.endDate}</p>
        </div>
        {!tripCanceled && onToggleCompare && (
          <div className="compare-checkbox-wrapper">
            <label className="checkbox-label" htmlFor={`compare-select-${alternative.id}`}>
              <input
                type="checkbox"
                id={`compare-select-${alternative.id}`}
                checked={isSelectedForCompare}
                onChange={(e) => onToggleCompare(alternative.id, e.target.checked)}
              />
              <span>Select {alternative.name || 'saved option'} for comparison</span>
            </label>
          </div>
        )}
      </div>

      <div className="alternative-card-content">
        {tripCanceled ? (
          <p className="hint read-only-hint">
            This trip is canceled. Saved options are read-only.
          </p>
        ) : (
          <p className="hint read-only-hint">
            Open a copy in the Working plan to edit it. Your saved option stays available.
          </p>
        )}

        {hasSelections ? (
          <div className="alternative-selections">
            {selections.airfare && (
              <div className="selection-item">
                <strong>Airfare:</strong> {selections.airfare.outboundDescription} / {selections.airfare.returnDescription}
              </div>
            )}
            {selections.stay && (
              <div className="selection-item">
                <strong>Stay:</strong> {selections.stay.propertyName} ({selections.stay.unitName})
              </div>
            )}
            {selections.rental && (
              <div className="selection-item">
                <strong>Rental Car:</strong> {selections.rental.vehicleClassName} at {selections.rental.locationName}
              </div>
            )}
          </div>
        ) : (
          <p className="hint">No components selected yet.</p>
        )}
        {alternative.tally && (
          <div className="alternative-costs" aria-label="Itinerary totals in USD">
            <div><span>Airfare total</span><strong>{selections.airfare ? formatCents(alternative.tally.airfareTotalCents) : 'Not selected'}</strong></div>
            <div><span>Stay total</span><strong>{selections.stay ? formatCents(alternative.tally.stayTotalCents) : 'Not selected'}</strong></div>
            <div><span>Rental Car total</span><strong>{selections.rental ? formatCents(alternative.tally.rentalTotalCents) : 'Not selected'}</strong></div>
            <div className="alternative-grand-total"><span>Grand total</span><strong>{formatCents(alternative.tally.grandTotalCents)} USD</strong></div>
            {alternative.tally.isOverBudget && <div className="alternative-overage"><span>Budget overage</span><strong>{formatCents(alternative.tally.budgetOverageCents ?? 0)} USD</strong></div>}
            {!alternative.tally.isOverBudget && alternative.tally.remainingBudgetCents !== null && <div><span>Budget remaining</span><strong>{formatCents(alternative.tally.remainingBudgetCents)} USD</strong></div>}
          </div>
        )}
        {!alternative.tally && hasSelections && (
          <p className="hint" role="status">Itinerary totals unavailable. Reopen this Trip to refresh prices before booking.</p>
        )}
      </div>

      <div className="alternative-card-actions">
        {tripCanceled ? (
          <>
            {isBooked && onViewBookingDetails && (
              <button
                type="button"
                className="primary-button view-booking-details-btn"
                onClick={onViewBookingDetails}
                aria-label={`View booking details for ${alternative.name || 'saved option'}`}
              >
                View Booking Details
              </button>
            )}
            <span className="hint read-only-hint">Read-only (trip canceled)</span>
          </>
        ) : (
          <>
            {onOpenForEditing && <button type="button" className="secondary-action-button"
              onClick={() => onOpenForEditing(alternative.id)}>Open {alternative.name || 'option'} as a copy in Working plan</button>}
            {onRename && !isBooked && <button type="button" className="secondary-action-button"
              onClick={() => onRename(alternative.id)}>Rename {alternative.name || 'option'}</button>}
            {isBooked && onViewBookingDetails ? (
              <button
                type="button"
                className="primary-button view-booking-details-btn"
                onClick={onViewBookingDetails}
                aria-label={`View booking details for ${alternative.name || 'saved option'}`}
              >
                View Booking Details
              </button>
            ) : onSelectForBookingReview ? (
              <>
                <button
                  type="button"
                  className="primary-button select-for-booking-btn"
                  disabled={hasActiveBooking}
                  aria-disabled={hasActiveBooking}
                  onClick={() => !hasActiveBooking && onSelectForBookingReview(alternative.id)}
                  aria-label={`Select ${alternative.name || 'saved option'} for booking review`}
                >
                  Select for Booking Review
                </button>
                {hasActiveBooking && (
                  <p className="hint booking-disabled-hint">
                    This trip already has an active booking. Only one active booking is permitted per trip.
                  </p>
                )}
              </>
            ) : null}
          </>
        )}
      </div>
    </article>
  );
}
