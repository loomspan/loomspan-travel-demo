import {useState} from 'react';
import type {TripResponse, BookingResponse} from '../api/tripsApi';
import {formatCents} from './ItinerarySummaryTally';

export type BookingConfirmationViewProps = {
  trip: TripResponse;
  booking: BookingResponse;
  onViewInWorkspace: () => void;
  onViewAllTrips: () => void;
};

export function BookingConfirmationView({
  trip,
  booking,
  onViewInWorkspace,
  onViewAllTrips,
}: BookingConfirmationViewProps) {
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  const handleCopy = async (code: string) => {
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(code);
        setCopiedCode(code);
        setTimeout(() => {
          setCopiedCode((prev) => (prev === code ? null : prev));
        }, 2500);
      }
    } catch {
      // Gracefully ignore clipboard write failures without showing false Copied state
    }
  };

  const tally = booking.tally;
  const selections = booking.selections;

  return (
    <section className="booking-confirmation-view" aria-labelledby="confirmation-heading">
      {/* Accessible screen-reader live announcement */}
      <div className="sr-only" role="status" aria-live="polite">
        {`Booking confirmed! Reference: ${booking.bookingReference}`}
      </div>

      <div className="confirmation-banner card">
        <div className="confirmation-banner-header">
          <span className="success-icon" aria-hidden="true">✓</span>
          <div>
            <p className="eyebrow">SIMULATED RESERVATION COMPLETE</p>
            <h2 id="confirmation-heading" className="confirmation-title">Booking Confirmed!</h2>
            <p className="confirmation-booking-ref">
              Booking Reference: <strong className="ref-code">{booking.bookingReference}</strong>
            </p>
          </div>
        </div>
      </div>

      {/* Component Confirmation Codes */}
      <div className="card component-references-card">
        <h3 className="section-title">Component Confirmation Codes</h3>
        <p className="hint">
          Use these fictional locator codes when referencing specific travel components.
        </p>

        <ul className="component-references-list" aria-label="Component reference codes">
          {booking.airfareReference && (
            <li className="reference-item">
              <div className="reference-info">
                <span className="reference-label">Airline Record Locator</span>
                <span className="reference-code">{booking.airfareReference}</span>
              </div>
              <button
                type="button"
                className="secondary-action-button copy-code-btn"
                onClick={() => void handleCopy(booking.airfareReference!)}
                aria-label={`Copy airline record locator ${booking.airfareReference}`}
              >
                {copiedCode === booking.airfareReference ? 'Copied!' : 'Copy'}
              </button>
            </li>
          )}

          {booking.stayReference && (
            <li className="reference-item">
              <div className="reference-info">
                <span className="reference-label">Accommodation Confirmation</span>
                <span className="reference-code">{booking.stayReference}</span>
              </div>
              <button
                type="button"
                className="secondary-action-button copy-code-btn"
                onClick={() => void handleCopy(booking.stayReference!)}
                aria-label={`Copy accommodation confirmation ${booking.stayReference}`}
              >
                {copiedCode === booking.stayReference ? 'Copied!' : 'Copy'}
              </button>
            </li>
          )}

          {booking.rentalReference && (
            <li className="reference-item">
              <div className="reference-info">
                <span className="reference-label">Rental Confirmation</span>
                <span className="reference-code">{booking.rentalReference}</span>
              </div>
              <button
                type="button"
                className="secondary-action-button copy-code-btn"
                onClick={() => void handleCopy(booking.rentalReference!)}
                aria-label={`Copy rental confirmation ${booking.rentalReference}`}
              >
                {copiedCode === booking.rentalReference ? 'Copied!' : 'Copy'}
              </button>
            </li>
          )}
        </ul>
      </div>

      {/* Trip Details & Itinerary Summary */}
      <div className="card booking-details-card">
        <h3 className="section-title">Trip Summary</h3>
        <div className="summary-grid">
          <div className="summary-item">
            <span className="summary-label">Destination</span>
            <span className="summary-value">{trip.destinationName}</span>
          </div>
          <div className="summary-item">
            <span className="summary-label">Travel Dates</span>
            <span className="summary-value">{trip.startDate} to {trip.endDate}</span>
          </div>
          <div className="summary-item">
            <span className="summary-label">Travelers</span>
            <span className="summary-value">
              {trip.travelerCount} traveler{trip.travelerCount === 1 ? '' : 's'}
              {trip.travelerAges && trip.travelerAges.length > 0 && ` (ages: ${trip.travelerAges.join(', ')})`}
            </span>
          </div>
        </div>

        {/* Financial Breakdown */}
        <div className="confirmation-financial-summary">
          <h4 className="financial-heading">Cost Breakdown</h4>
          <div className="financial-rows">
            {selections?.airfare && (
              <div className="financial-row">
                <span>Airfare Total</span>
                <span>{formatCents(tally?.airfareTotalCents ?? 0)}</span>
              </div>
            )}
            {selections?.stay && (
              <div className="financial-row">
                <span>Accommodation Total</span>
                <span>{formatCents(tally?.stayTotalCents ?? 0)}</span>
              </div>
            )}
            {selections?.rental && (
              <div className="financial-row">
                <span>Rental Car Total</span>
                <span>{formatCents(tally?.rentalTotalCents ?? 0)}</span>
              </div>
            )}
            <div className="financial-row total-row">
              <strong>Grand Total</strong>
              <strong className="grand-total-value">{formatCents(booking.grandTotalCents)}</strong>
            </div>
          </div>
        </div>
      </div>

      {/* Mandatory Simulated Booking Disclosure */}
      <div className="booking-disclosure-callout" role="note" aria-label="Simulated booking disclosure">
        <div className="disclosure-icon" aria-hidden="true">ℹ️</div>
        <div className="disclosure-content">
          <h4>Simulated Booking Disclosure</h4>
          <p className="disclosure-text">
            This was a simulated reservation using fictional inventory. No credit card was charged and no live supplier booking was made.
          </p>
        </div>
      </div>

      {/* Navigation Actions */}
      <div className="confirmation-actions">
        <button
          type="button"
          className="primary-button view-in-workspace-btn"
          onClick={onViewInWorkspace}
        >
          View in Trip Workspace
        </button>
        <button
          type="button"
          className="secondary-action-button view-all-trips-btn"
          onClick={onViewAllTrips}
        >
          View All Trips
        </button>
      </div>
    </section>
  );
}
