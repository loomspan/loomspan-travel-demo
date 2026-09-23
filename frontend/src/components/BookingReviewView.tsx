import type {TripResponse, AlternativeResponse} from '../api/tripsApi';
import {formatCents, computeRentalTotalCents} from './ItinerarySummaryTally';
import {formatMinutes, formatTime} from './AirfareSearchSection';

export type BookingReviewViewProps = {
  trip: TripResponse;
  alternative: AlternativeResponse;
  returnTarget: 'workspace' | 'compare';
  onBack: () => void;
};

export function BookingReviewView({
  trip,
  alternative,
  returnTarget,
  onBack,
}: BookingReviewViewProps) {
  const selections = alternative.selections;
  const tally = alternative.tally;

  const renderMissing = (type: 'airfare' | 'stay' | 'rental') => {
    const labels = {
      airfare: 'No flights selected',
      stay: 'No accommodation selected',
      rental: 'No rental car selected',
    };
    return (
      <div className="missing-component" aria-label={labels[type]}>
        <span className="missing-dash">—</span> {labels[type]}
      </div>
    );
  };

  const getRentalCycles = (pickupAt: string, returnAt: string): number => {
    const pickup = new Date(pickupAt).getTime();
    const ret = new Date(returnAt).getTime();
    const diffHours = (ret - pickup) / (1000 * 60 * 60);
    return Math.max(1, Math.ceil(diffHours / 24));
  };

  return (
    <section className="booking-review-view" aria-labelledby="booking-review-heading">
      <div className="booking-review-top-nav">
        <button
          type="button"
          className="text-button back-navigation-btn"
          onClick={onBack}
        >
          {returnTarget === 'compare' ? '← Back to comparison' : '← Back to Trip Workspace'}
        </button>
      </div>

      <header className="booking-review-header">
        <p className="eyebrow">SIMULATED BOOKING REVIEW</p>
        <h2 id="booking-review-heading">Review Itinerary &amp; Component Snapshots</h2>
        <span className="alternative-id">Itinerary Snapshot ID: {alternative.id}</span>
      </header>

      {/* Trip parameters overview */}
      <div className="card booking-trip-summary">
        <h3 className="section-title">Trip Parameters</h3>
        <div className="summary-grid">
          <div className="summary-item">
            <span className="summary-label">Destination</span>
            <span className="summary-value">
              {trip.destinationName} ({trip.originAirportCode} → {trip.destinationKey.replace('destination-', '').toUpperCase()})
            </span>
          </div>
          <div className="summary-item">
            <span className="summary-label">Dates</span>
            <span className="summary-value">
              {trip.startDate} to {trip.endDate}
            </span>
          </div>
          <div className="summary-item">
            <span className="summary-label">Travelers</span>
            <span className="summary-value">
              {trip.travelerCount} traveler{trip.travelerCount === 1 ? '' : 's'}
              {trip.travelerAges && trip.travelerAges.length > 0 && ` (Ages: ${trip.travelerAges.join(', ')})`}
            </span>
          </div>
        </div>
      </div>

      {/* Snapshot-locked components */}
      <div className="booking-components-grid">
        {/* Airfare Component */}
        <article className="card booking-component-card" aria-labelledby="review-airfare-heading">
          <div className="component-card-header">
            <h3 id="review-airfare-heading">Airfare Snapshot</h3>
            {selections.airfare && (
              <span className="component-price">
                {formatCents(tally?.airfareTotalCents ?? 0)}
              </span>
            )}
          </div>
          {selections.airfare ? (
            <div className="component-snapshot-details">
              <div className="flight-leg-detail">
                <h4>Outbound Flight</h4>
                <p>
                  <strong>{selections.airfare.outboundCarrierName || 'Unknown Carrier'}</strong> {selections.airfare.outboundFlightNumber || ''} •{' '}
                  {selections.airfare.outboundStopCount === 0
                    ? 'Nonstop'
                    : `${selections.airfare.outboundStopCount} stop (${selections.airfare.outboundLayoverAirportCode || ''}${selections.airfare.outboundLayoverDurationMinutes ? `, ${selections.airfare.outboundLayoverDurationMinutes}m` : ''})`}
                </p>
                <p>
                  {formatTime(selections.airfare.outboundDepartureTime || '', selections.airfare.outboundDepartureTimeZone || undefined)} ({selections.airfare.outboundDepartureTimeZone || 'Local'}) → {formatTime(selections.airfare.outboundArrivalTime || '', selections.airfare.outboundArrivalTimeZone || undefined)} ({selections.airfare.outboundArrivalTimeZone || 'Local'})
                </p>
                <p className="hint">
                  Duration: {formatMinutes(selections.airfare.outboundDurationMinutes || 0)}
                </p>
              </div>

              <div className="flight-leg-detail" style={{marginTop: '0.75rem'}}>
                <h4>Return Flight</h4>
                <p>
                  <strong>{selections.airfare.returnCarrierName || 'Unknown Carrier'}</strong> {selections.airfare.returnFlightNumber || ''} •{' '}
                  {selections.airfare.returnStopCount === 0
                    ? 'Nonstop'
                    : `${selections.airfare.returnStopCount} stop (${selections.airfare.returnLayoverAirportCode || ''}${selections.airfare.returnLayoverDurationMinutes ? `, ${selections.airfare.returnLayoverDurationMinutes}m` : ''})`}
                </p>
                <p>
                  {formatTime(selections.airfare.returnDepartureTime || '', selections.airfare.returnDepartureTimeZone || undefined)} ({selections.airfare.returnDepartureTimeZone || 'Local'}) → {formatTime(selections.airfare.returnArrivalTime || '', selections.airfare.returnArrivalTimeZone || undefined)} ({selections.airfare.returnArrivalTimeZone || 'Local'})
                </p>
                <p className="hint">
                  Duration: {formatMinutes(selections.airfare.returnDurationMinutes || 0)}
                </p>
              </div>

              <p className="meta-note" style={{marginTop: '0.75rem'}}>
                Total air duration: {formatMinutes(selections.airfare.totalDurationMinutes || 0)} • Party of {trip.travelerCount}
              </p>
            </div>
          ) : (
            renderMissing('airfare')
          )}
        </article>

        {/* Stay Component */}
        <article className="card booking-component-card" aria-labelledby="review-stay-heading">
          <div className="component-card-header">
            <h3 id="review-stay-heading">Stay Snapshot</h3>
            {selections.stay && (
              <span className="component-price">
                {formatCents(tally?.stayTotalCents ?? 0)}
              </span>
            )}
          </div>
          {selections.stay ? (
            <div className="component-snapshot-details">
              <div>
                <strong>{selections.stay.propertyName}</strong>{' '}
                <span className="badge badge-upcoming">{selections.stay.propertyCategory || 'HOTEL'}</span>
              </div>
              <p>
                {selections.stay.unitName} ({selections.stay.requiredRoomCount || selections.stay.unitCount || 1} room{(selections.stay.requiredRoomCount || selections.stay.unitCount || 1) > 1 ? 's' : ''}, up to {selections.stay.guestCapacity || 2} guests)
              </p>
              <p>
                {selections.stay.locationDescription || 'Central location'}{' '}
                {selections.stay.distanceToCityCenterMeters !== null && selections.stay.distanceToCityCenterMeters !== undefined && (
                  <span>• {(selections.stay.distanceToCityCenterMeters / 1000).toFixed(1)} km to city center</span>
                )}
              </p>
              <div className="stay-nights-list" style={{marginTop: '0.75rem'}}>
                <h4>Nightly Breakdown ({selections.stay.nights.length} nights)</h4>
                <ul>
                  {selections.stay.nights.map((n) => (
                    <li key={n.date}>
                      {n.date}: {formatCents(n.basePriceCents + n.taxCents + n.feeCents)} (Base: {formatCents(n.basePriceCents)}, Tax: {formatCents(n.taxCents)}, Fees: {formatCents(n.feeCents)})
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          ) : (
            renderMissing('stay')
          )}
        </article>

        {/* Rental Car Component */}
        <article className="card booking-component-card" aria-labelledby="review-rental-heading">
          <div className="component-card-header">
            <h3 id="review-rental-heading">Rental Car Snapshot</h3>
            {selections.rental && (
              <span className="component-price">
                {formatCents(tally?.rentalTotalCents ?? computeRentalTotalCents(selections))}
              </span>
            )}
          </div>
          {selections.rental ? (
            <div className="component-snapshot-details">
              <div>
                <strong>{selections.rental.vehicleClassName}</strong>{' '}
                <span className="badge badge-draft">{selections.rental.vehicleCategory || 'SEDAN'}</span>
              </div>
              <p>Location: {selections.rental.locationName}</p>
              <p>
                Pickup: {new Date(selections.rental.pickupAt).toLocaleString('en-US', {month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'})}
              </p>
              <p>
                Return: {new Date(selections.rental.returnAt).toLocaleString('en-US', {month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'})}
              </p>
              <p className="meta-note">
                Billing Cycles: {getRentalCycles(selections.rental.pickupAt, selections.rental.returnAt)} days (24-hour cycles)
              </p>
              <p className="hint">
                Daily Rate: {formatCents(selections.rental.dailyBasePriceCents + selections.rental.dailyTaxCents + selections.rental.dailyFeeCents)} (Base: {formatCents(selections.rental.dailyBasePriceCents)}, Tax: {formatCents(selections.rental.dailyTaxCents)}, Fees: {formatCents(selections.rental.dailyFeeCents)})
              </p>
            </div>
          ) : (
            renderMissing('rental')
          )}
        </article>
      </div>

      {/* Itemized totals & budget position */}
      <div className="card booking-totals-card">
        <h3 className="section-title">Authoritative Booking Totals</h3>
        <div className="totals-breakdown-list">
          <div className="total-row">
            <span>Airfare Subtotal:</span>
            <span>{selections.airfare ? formatCents(tally?.airfareTotalCents ?? 0) : renderMissing('airfare')}</span>
          </div>
          <div className="total-row">
            <span>Stay Subtotal:</span>
            <span>{selections.stay ? formatCents(tally?.stayTotalCents ?? 0) : renderMissing('stay')}</span>
          </div>
          <div className="total-row">
            <span>Rental Car Subtotal:</span>
            <span>{selections.rental ? formatCents(tally?.rentalTotalCents ?? 0) : renderMissing('rental')}</span>
          </div>
          <hr className="tally-divider" />
          <div className="total-row grand-total-row">
            <span>Final Booking Grand Total:</span>
            <span className="grand-total-highlight">
              {formatCents(tally?.grandTotalCents ?? 0)}
            </span>
          </div>
          <div className="total-row budget-position-row">
            <span>Budget Position:</span>
            <span>
              {tally?.isOverBudget ? (
                <span className="badge badge-warning" role="alert">
                  Over Budget by {formatCents(tally.budgetOverageCents ?? 0)}
                </span>
              ) : (
                <span className="badge badge-success">
                  Within Budget ({formatCents(tally?.remainingBudgetCents ?? 0)} remaining)
                </span>
              )}
            </span>
          </div>
        </div>
      </div>

      {/* Mandatory Fictional Inventory Disclosure */}
      <div className="booking-disclosure-callout" role="note" aria-label="Fictional inventory disclosure">
        <div className="disclosure-icon" aria-hidden="true">ℹ️</div>
        <div className="disclosure-content">
          <h4>Simulated Booking Disclosure</h4>
          <p className="disclosure-text">
            This is a simulated booking with fictional inventory. No real payment, billing address, or external reservation is required.
          </p>
        </div>
      </div>

      {/* Staged Confirm Booking Section */}
      <div className="booking-confirm-section">
        <button
          type="button"
          className="primary-button confirm-booking-btn"
          disabled={true}
          aria-disabled="true"
        >
          Confirm Booking
        </button>
        <p className="confirm-booking-note hint">
          Ready for Phase 6 simulated booking implementation.
        </p>
      </div>
    </section>
  );
}
