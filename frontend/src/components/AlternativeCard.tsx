import type {AlternativeResponse} from '../api/tripsApi';
import {formatCents} from './ItinerarySummaryTally';

type AlternativeCardProps = {
  alternative: AlternativeResponse;
  tripExpired?: boolean;
  tripCanceled?: boolean;
  hasBookingHistory?: boolean;
  promotionPending?: boolean;
  isSelectedForCompare?: boolean;
  isBooked?: boolean;
  hasActiveBooking?: boolean;
  onToggleCompare?: (alternativeId: string, checked: boolean) => void;
  onSelectForBookingReview?: (alternativeId: string) => void;
  onViewBookingDetails?: () => void;
  onDuplicateDraft: (draftId: string, version: number) => void;
  onDuplicatePlanned: (alternativeId: string) => void;
  onDeleteDraft: (draftId: string, version: number) => void;
  onDeletePlanned: (alternativeId: string) => void;
  onPromoteDraft?: (draftId: string, version: number) => void;
};

export function AlternativeCard({
  alternative,
  tripExpired = false,
  tripCanceled = false,
  hasBookingHistory = false,
  promotionPending = false,
  isSelectedForCompare = false,
  isBooked = false,
  hasActiveBooking = false,
  onToggleCompare,
  onSelectForBookingReview,
  onViewBookingDetails,
  onDuplicateDraft,
  onDuplicatePlanned,
  onDeleteDraft,
  onDeletePlanned,
  onPromoteDraft,
}: AlternativeCardProps) {
  const isDraft = alternative.lifecycle.toUpperCase() === 'DRAFT';
  const isPlanned = alternative.lifecycle.toUpperCase() === 'PLANNED';
  const selections = alternative.selections;
  const hasSelections = Boolean(
    selections && (selections.airfare || selections.stay || selections.rental)
  );

  return (
    <article
      className={`card alternative-card ${isPlanned ? 'alternative-card-planned' : 'alternative-card-draft'}`}
      aria-labelledby={`alt-heading-${alternative.id}`}
    >
      <div className="alternative-card-header">
        <div>
          <div className="badge-row">
            <span className={`badge ${isDraft ? 'badge-draft' : 'badge-planned'}`}>
              {isDraft
                ? alternative.version !== null
                  ? `Draft v${alternative.version}`
                  : 'Draft'
                : 'Planned itinerary (read-only)'}
            </span>
            {isBooked && <span className="badge badge-booked">Booking</span>}
            {tripCanceled && <span className="badge badge-canceled">Canceled Trip</span>}
            {tripExpired && <span className="badge badge-expired">Expired</span>}
          </div>
          <h4 id={`alt-heading-${alternative.id}`} className="alternative-card-title">
            {isDraft ? 'Draft' : 'Planned itinerary'}
          </h4>
          <span className="alternative-id">ID: {alternative.id.slice(0, 8)}…</span>
        </div>
        {isPlanned && !tripCanceled && onToggleCompare && (
          <div className="compare-checkbox-wrapper">
            <label className="checkbox-label" htmlFor={`compare-select-${alternative.id}`}>
              <input
                type="checkbox"
                id={`compare-select-${alternative.id}`}
                checked={isSelectedForCompare}
                onChange={(e) => onToggleCompare(alternative.id, e.target.checked)}
              />
              <span>Select for comparison</span>
            </label>
          </div>
        )}
      </div>

      <div className="alternative-card-content">
        {tripCanceled ? (
          <p className="hint read-only-hint">
            This trip is canceled. All alternatives are read-only.
          </p>
        ) : isPlanned ? (
          <p className="hint read-only-hint">
            This planned itinerary is snapshot-locked and read-only. Use &ldquo;Duplicate to draft&rdquo; to make modifications.
          </p>
        ) : null}

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
                aria-label={`View booking details for itinerary ${alternative.id}`}
              >
                View Booking Details
              </button>
            )}
            <span className="hint read-only-hint">Read-only (trip canceled)</span>
          </>
        ) : isDraft ? (
          <>
            <button
              type="button"
              className="text-button delete-button"
              onClick={() => onDeleteDraft(alternative.id, alternative.version ?? 0)}
              aria-label={`Delete draft ${alternative.id.slice(0, 8)}`}
            >
              Delete draft
            </button>
            <button
              type="button"
              className="secondary-action-button"
              onClick={() => onDuplicateDraft(alternative.id, alternative.version ?? 0)}
              aria-label={`Duplicate draft ${alternative.id.slice(0, 8)}`}
            >
              Duplicate to new draft
            </button>
            {onPromoteDraft && (
              <button
                type="button"
                className="primary-button promote-draft-btn"
                disabled={tripExpired || promotionPending}
                onClick={() => onPromoteDraft(alternative.id, alternative.version ?? 0)}
                aria-label={`Promote draft ${alternative.id.slice(0, 8)} to planned`}
              >
                {promotionPending ? 'Saving planned itinerary…' : 'Promote to Planned'}
              </button>
            )}
          </>
        ) : (
          <>
            <button
              type="button"
              className="text-button delete-button"
              onClick={() => onDeletePlanned(alternative.id)}
              aria-label={`Delete planned itinerary ${alternative.id}`}
            >
              Delete planned itinerary
            </button>
            <button
              type="button"
              className="secondary-action-button"
              onClick={() => onDuplicatePlanned(alternative.id)}
              aria-label={`Duplicate planned itinerary ${alternative.id} to draft`}
            >
              Duplicate to draft
            </button>
            {isBooked && onViewBookingDetails ? (
              <button
                type="button"
                className="primary-button view-booking-details-btn"
                onClick={onViewBookingDetails}
                aria-label={`View booking details for itinerary ${alternative.id}`}
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
                  aria-label={`Select planned itinerary ${alternative.id} for booking review`}
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
