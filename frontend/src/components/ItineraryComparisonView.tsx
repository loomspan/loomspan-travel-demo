import {useState, useRef, useEffect} from 'react';
import type {TripResponse, AlternativeResponse} from '../api/tripsApi';
import {formatTallyCents} from './ItinerarySummaryTally';
import {formatMinutes} from './AirfareSearchSection';
import {FlightSchedule} from './FlightSchedule';

export type ItineraryComparisonViewProps = {
  trip: TripResponse;
  alternatives: AlternativeResponse[];
  onBack: () => void;
  onSelectForBookingReview: (alternativeId: string) => void;
  hasActiveBooking?: boolean;
};

export function ItineraryComparisonView({
  trip,
  alternatives,
  onBack,
  onSelectForBookingReview,
  hasActiveBooking = false,
}: ItineraryComparisonViewProps) {
  const [activeMobileIndex, setActiveMobileIndex] = useState(0);
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  // Clamp mobile index if alternatives list shrinks
  useEffect(() => {
    if (activeMobileIndex >= alternatives.length) {
      setActiveMobileIndex(Math.max(0, alternatives.length - 1));
    }
  }, [alternatives.length, activeMobileIndex]);

  const handleTabKeyDown = (e: React.KeyboardEvent, index: number) => {
    let nextIndex = index;
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault();
      nextIndex = (index + 1) % alternatives.length;
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault();
      nextIndex = (index - 1 + alternatives.length) % alternatives.length;
    } else if (e.key === 'Home') {
      e.preventDefault();
      nextIndex = 0;
    } else if (e.key === 'End') {
      e.preventDefault();
      nextIndex = alternatives.length - 1;
    }

    if (nextIndex !== index) {
      setActiveMobileIndex(nextIndex);
      tabRefs.current[nextIndex]?.focus();
    }
  };

  const activeAlt = alternatives[activeMobileIndex] || alternatives[0];

  const budgetPosition = (alt: AlternativeResponse) => {
    if (!alt.tally) return <span>Budget position unavailable</span>;
    if (alt.tally.remainingBudgetCents === null && !alt.tally.isOverBudget) return <span>No budget set</span>;
    return alt.tally.isOverBudget ? (
      <span className="badge badge-warning" role="alert">Over Budget by {formatTallyCents(alt.tally.budgetOverageCents)}</span>
    ) : (
      <span className="badge badge-success">Within Budget: {formatTallyCents(alt.tally.remainingBudgetCents)} remaining</span>
    );
  };

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
    <section className="comparison-view-container" aria-labelledby="comparison-heading">
      <div className="comparison-top-nav">
        <button
          type="button"
          className="text-button back-to-workspace-btn"
          onClick={onBack}
        >
          ← Back to Trip Workspace
        </button>
      </div>

      <header className="comparison-header">
        <div>
          <p className="eyebrow">ITINERARY COMPARISON</p>
          <h2 id="comparison-heading" tabIndex={-1}>
            Comparing {alternatives.length} Planned itineraries
          </h2>
          <p className="trip-meta">
            {trip.destinationName} ({trip.originAirportCode} → {trip.destinationKey.replace('destination-', '').toUpperCase()}) • {trip.startDate} to {trip.endDate} • {trip.travelerCount} traveler{trip.travelerCount === 1 ? '' : 's'}
          </p>
          <p className="hint">All amounts in USD.</p>
        </div>
      </header>

      {/* Screen reader live announcement for active mobile switcher tab */}
      <div className="sr-only" aria-live="polite" role="status">
        {activeAlt
          ? `Showing itinerary ${activeMobileIndex + 1} of ${alternatives.length}: Planned itinerary ${activeAlt.id}`
          : ''}
      </div>

      {/* Mobile Switcher Tab Bar */}
      <div className="mobile-comparison-switcher">
        <div
          role="tablist"
          aria-label="Compared itineraries"
          className="mobile-tablist"
        >
          {alternatives.map((alt, idx) => {
            const isSelected = idx === activeMobileIndex;
            return (
              <button
                key={alt.id}
                ref={(el) => { tabRefs.current[idx] = el; }}
                role="tab"
                id={`mobile-tab-${alt.id}`}
                aria-controls={`mobile-panel-${alt.id}`}
                aria-selected={isSelected}
                tabIndex={isSelected ? 0 : -1}
                className={`mobile-tab ${isSelected ? 'active' : ''}`}
                onClick={() => setActiveMobileIndex(idx)}
                onKeyDown={(e) => handleTabKeyDown(e, idx)}
              >
                Itinerary {idx + 1} ({alt.id})
              </button>
            );
          })}
        </div>

        {activeAlt && (
          <div
            role="tabpanel"
            id={`mobile-panel-${activeAlt.id}`}
            aria-labelledby={`mobile-tab-${activeAlt.id}`}
            className="mobile-stacked-panel"
          >
            <div className="card mobile-alt-card">
              <div className="mobile-alt-header">
                <h3>Planned itinerary #{activeMobileIndex + 1}</h3>
                <span className="alternative-id">ID: {activeAlt.id.slice(0, 8)}…</span>
                <div className="mobile-price-banner">
                  <span className="grand-total-amount">
                    {formatTallyCents(activeAlt.tally?.grandTotalCents)}
                  </span>
                  {budgetPosition(activeAlt)}
                </div>
                <button
                  type="button"
                  className="primary-button select-for-booking-btn"
                  disabled={hasActiveBooking}
                  aria-disabled={hasActiveBooking}
                  onClick={() => !hasActiveBooking && onSelectForBookingReview(activeAlt.id)}
                  aria-label={`Select alternative ${activeAlt.id} for booking review`}
                >
                  Select for Booking Review
                </button>
                {hasActiveBooking && (
                  <p className="hint booking-disabled-hint">
                    This trip already has an active booking. Only one active booking is permitted per trip.
                  </p>
                )}
              </div>

              {/* Financial Section */}
              <div className="mobile-section">
                <h4>Financial summary (USD)</h4>
                <div className="mobile-detail-row">
                  <span>Grand Total:</span>
                  <strong>{formatTallyCents(activeAlt.tally?.grandTotalCents)}</strong>
                </div>
                <div className="mobile-detail-row">
                  <span>Airfare Total:</span>
                  <span>{activeAlt.selections.airfare ? formatTallyCents(activeAlt.tally?.airfareTotalCents) : renderMissing('airfare')}</span>
                </div>
                <div className="mobile-detail-row">
                  <span>Stay Total:</span>
                  <span>{activeAlt.selections.stay ? formatTallyCents(activeAlt.tally?.stayTotalCents) : renderMissing('stay')}</span>
                </div>
                <div className="mobile-detail-row">
                  <span>Rental Car Total:</span>
                  <span>{activeAlt.selections.rental ? formatTallyCents(activeAlt.tally?.rentalTotalCents) : renderMissing('rental')}</span>
                </div>
              </div>

              {/* Airfare Section */}
              <div className="mobile-section">
                <h4>Flights</h4>
                {activeAlt.selections.airfare ? (
                  <div className="mobile-flight-details">
                    <div>
                      <strong>Outbound:</strong> {activeAlt.selections.airfare.outboundCarrierName || 'Unknown Carrier'} {activeAlt.selections.airfare.outboundFlightNumber || ''} •{' '}
                      {activeAlt.selections.airfare.outboundStopCount === 0
                        ? 'Nonstop'
                        : `${activeAlt.selections.airfare.outboundStopCount} stop (${activeAlt.selections.airfare.outboundLayoverAirportCode || ''}${activeAlt.selections.airfare.outboundLayoverDurationMinutes ? `, ${activeAlt.selections.airfare.outboundLayoverDurationMinutes}m` : ''})`}
                    </div>
                    {activeAlt.selections.airfare.outboundDescription && (
                      <div className="meta-note">{activeAlt.selections.airfare.outboundDescription}</div>
                    )}
                    <FlightSchedule departureAirport={trip.originAirportCode} departureTime={activeAlt.selections.airfare.outboundDepartureTime} departureTimeZone={activeAlt.selections.airfare.outboundDepartureTimeZone} arrivalAirport={trip.destinationKey.replace('destination-', '').toUpperCase()} arrivalTime={activeAlt.selections.airfare.outboundArrivalTime} arrivalTimeZone={activeAlt.selections.airfare.outboundArrivalTimeZone} />
                    <div>Duration: {formatMinutes(activeAlt.selections.airfare.outboundDurationMinutes || 0)}</div>
                    <div style={{marginTop: '0.35rem'}}>
                      <strong>Return:</strong> {activeAlt.selections.airfare.returnCarrierName || 'Unknown Carrier'} {activeAlt.selections.airfare.returnFlightNumber || ''} •{' '}
                      {activeAlt.selections.airfare.returnStopCount === 0
                        ? 'Nonstop'
                        : `${activeAlt.selections.airfare.returnStopCount} stop (${activeAlt.selections.airfare.returnLayoverAirportCode || ''}${activeAlt.selections.airfare.returnLayoverDurationMinutes ? `, ${activeAlt.selections.airfare.returnLayoverDurationMinutes}m` : ''})`}
                    </div>
                    {activeAlt.selections.airfare.returnDescription && (
                      <div className="meta-note">{activeAlt.selections.airfare.returnDescription}</div>
                    )}
                    <FlightSchedule departureAirport={trip.destinationKey.replace('destination-', '').toUpperCase()} departureTime={activeAlt.selections.airfare.returnDepartureTime} departureTimeZone={activeAlt.selections.airfare.returnDepartureTimeZone} arrivalAirport={trip.originAirportCode} arrivalTime={activeAlt.selections.airfare.returnArrivalTime} arrivalTimeZone={activeAlt.selections.airfare.returnArrivalTimeZone} />
                    <div>Duration: {formatMinutes(activeAlt.selections.airfare.returnDurationMinutes || 0)}</div>
                    <div className="meta-note">
                      Total flight duration: {formatMinutes(activeAlt.selections.airfare.totalDurationMinutes || 0)}
                    </div>
                  </div>
                ) : (
                  renderMissing('airfare')
                )}
              </div>

              {/* Stay Section */}
              <div className="mobile-section">
                <h4>Stay</h4>
                {activeAlt.selections.stay ? (
                  <div className="mobile-stay-details">
                    <div>
                      <strong>{activeAlt.selections.stay.propertyName}</strong>{' '}
                      <span className="badge badge-upcoming">{activeAlt.selections.stay.propertyCategory || 'HOTEL'}</span>
                    </div>
                    <div>
                      {activeAlt.selections.stay.unitName} ({activeAlt.selections.stay.requiredRoomCount || activeAlt.selections.stay.unitCount || 1} room{(activeAlt.selections.stay.requiredRoomCount || activeAlt.selections.stay.unitCount || 1) > 1 ? 's' : ''})
                    </div>
                    <div>
                      {activeAlt.selections.stay.distanceToCityCenterMeters !== null && activeAlt.selections.stay.distanceToCityCenterMeters !== undefined
                        ? `${(activeAlt.selections.stay.distanceToCityCenterMeters / 1000).toFixed(1)} km to city center`
                        : '—'}
                    </div>
                    <div className="meta-note">
                      {activeAlt.selections.stay.nights.length} night{activeAlt.selections.stay.nights.length === 1 ? '' : 's'} ({formatTallyCents(activeAlt.tally?.stayTotalCents)})
                    </div>
                  </div>
                ) : (
                  renderMissing('stay')
                )}
              </div>

              {/* Rental Section */}
              <div className="mobile-section">
                <h4>Rental Car</h4>
                {activeAlt.selections.rental ? (
                  <div className="mobile-rental-details">
                    <div>
                      <strong>{activeAlt.selections.rental.vehicleClassName}</strong> ({activeAlt.selections.rental.vehicleCategory || 'SEDAN'})
                    </div>
                    <div>
                      Location: {activeAlt.selections.rental.locationName}
                    </div>
                    <div>
                      Pickup: {new Date(activeAlt.selections.rental.pickupAt).toLocaleString('en-US', {month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'})}
                    </div>
                    <div>
                      Return: {new Date(activeAlt.selections.rental.returnAt).toLocaleString('en-US', {month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'})}
                    </div>
                    <div className="meta-note">
                      {getRentalCycles(activeAlt.selections.rental.pickupAt, activeAlt.selections.rental.returnAt)} days (24-hour cycles) • {formatTallyCents(activeAlt.tally?.rentalTotalCents)}
                    </div>
                  </div>
                ) : (
                  renderMissing('rental')
                )}
              </div>
            </div>
          </div>
        )}
        {alternatives.filter((alt) => alt.id !== activeAlt?.id).map((alt) => (
          <div key={alt.id} role="tabpanel" id={`mobile-panel-${alt.id}`} aria-labelledby={`mobile-tab-${alt.id}`} hidden />
        ))}
      </div>

      {/* Desktop Side-by-Side Comparison Matrix */}
      <div className="desktop-comparison-matrix">
        <table className="comparison-table" aria-label="Itinerary comparison table">
          <thead>
            <tr>
              <th scope="col" className="comparison-label-header">
                Attribute
              </th>
              {alternatives.map((alt, idx) => (
                <th
                  key={alt.id}
                  scope="col"
                  id={`col-${alt.id}`}
                  className="comparison-alt-header"
                >
                  <div className="alt-header-box">
                    <span className="badge badge-planned">Planned itinerary #{idx + 1}</span>
                    <span className="alternative-id">ID: {alt.id.slice(0, 8)}…</span>
                    <div className="header-grand-total">
                      {formatTallyCents(alt.tally?.grandTotalCents)}
                    </div>
                    <div className="header-budget-badge">
                      {budgetPosition(alt)}
                    </div>
                    <button
                      type="button"
                      className="primary-button select-for-booking-btn"
                      disabled={hasActiveBooking}
                      aria-disabled={hasActiveBooking}
                      onClick={() => !hasActiveBooking && onSelectForBookingReview(alt.id)}
                      aria-label={`Select alternative ${alt.id} for booking review`}
                    >
                      Select for Booking Review
                    </button>
                    {hasActiveBooking && (
                      <p className="hint booking-disabled-hint">
                        This trip already has an active booking. Only one active booking is permitted per trip.
                      </p>
                    )}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {/* --- Section: Financial --- */}
            <tr className="section-divider-row">
              <th colSpan={alternatives.length + 1} scope="colgroup">
                Financial summary (USD)
              </th>
            </tr>
            <tr>
              <th scope="row" className="row-header">Grand Total</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell strong-value">
                  {formatTallyCents(alt.tally?.grandTotalCents)}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Budget Position</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {budgetPosition(alt)}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Airfare total</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.airfare ? (
                    formatTallyCents(alt.tally?.airfareTotalCents)
                  ) : (
                    renderMissing('airfare')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Stay total</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.stay ? (
                    formatTallyCents(alt.tally?.stayTotalCents)
                  ) : (
                    renderMissing('stay')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Rental Car total</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.rental ? (
                    formatTallyCents(alt.tally?.rentalTotalCents)
                  ) : (
                    renderMissing('rental')
                  )}
                </td>
              ))}
            </tr>

            {/* --- Section: Airfare --- */}
            <tr className="section-divider-row">
              <th colSpan={alternatives.length + 1} scope="colgroup">
                Airfare Details
              </th>
            </tr>
            <tr>
              <th scope="row" className="row-header">Outbound Flight</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.airfare ? (
                    <div className="cell-flight-block">
                      <div className="carrier-flight">
                        <strong>{alt.selections.airfare.outboundCarrierName || 'Carrier'}</strong> {alt.selections.airfare.outboundFlightNumber || ''}
                      </div>
                      {alt.selections.airfare.outboundDescription && (
                        <div className="meta-note">{alt.selections.airfare.outboundDescription}</div>
                      )}
                      <div className="stops-badge">
                        {alt.selections.airfare.outboundStopCount === 0
                          ? 'Nonstop'
                          : `${alt.selections.airfare.outboundStopCount} stop (${alt.selections.airfare.outboundLayoverAirportCode || ''}${alt.selections.airfare.outboundLayoverDurationMinutes ? `, ${alt.selections.airfare.outboundLayoverDurationMinutes}m` : ''})`}
                      </div>
                      <FlightSchedule departureAirport={trip.originAirportCode} departureTime={alt.selections.airfare.outboundDepartureTime} departureTimeZone={alt.selections.airfare.outboundDepartureTimeZone} arrivalAirport={trip.destinationKey.replace('destination-', '').toUpperCase()} arrivalTime={alt.selections.airfare.outboundArrivalTime} arrivalTimeZone={alt.selections.airfare.outboundArrivalTimeZone} />
                      <div className="duration">
                        Duration: {formatMinutes(alt.selections.airfare.outboundDurationMinutes || 0)}
                      </div>
                    </div>
                  ) : (
                    renderMissing('airfare')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Return Flight</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.airfare ? (
                    <div className="cell-flight-block">
                      <div className="carrier-flight">
                        <strong>{alt.selections.airfare.returnCarrierName || 'Carrier'}</strong> {alt.selections.airfare.returnFlightNumber || ''}
                      </div>
                      {alt.selections.airfare.returnDescription && (
                        <div className="meta-note">{alt.selections.airfare.returnDescription}</div>
                      )}
                      <div className="stops-badge">
                        {alt.selections.airfare.returnStopCount === 0
                          ? 'Nonstop'
                          : `${alt.selections.airfare.returnStopCount} stop (${alt.selections.airfare.returnLayoverAirportCode || ''}${alt.selections.airfare.returnLayoverDurationMinutes ? `, ${alt.selections.airfare.returnLayoverDurationMinutes}m` : ''})`}
                      </div>
                      <FlightSchedule departureAirport={trip.destinationKey.replace('destination-', '').toUpperCase()} departureTime={alt.selections.airfare.returnDepartureTime} departureTimeZone={alt.selections.airfare.returnDepartureTimeZone} arrivalAirport={trip.originAirportCode} arrivalTime={alt.selections.airfare.returnArrivalTime} arrivalTimeZone={alt.selections.airfare.returnArrivalTimeZone} />
                      <div className="duration">
                        Duration: {formatMinutes(alt.selections.airfare.returnDurationMinutes || 0)}
                      </div>
                    </div>
                  ) : (
                    renderMissing('airfare')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Total Flight Duration</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.airfare ? (
                    formatMinutes(alt.selections.airfare.totalDurationMinutes || 0)
                  ) : (
                    renderMissing('airfare')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Airfare Complete-Party Total</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.airfare ? (
                    formatTallyCents(alt.tally?.airfareTotalCents)
                  ) : (
                    renderMissing('airfare')
                  )}
                </td>
              ))}
            </tr>

            {/* --- Section: Stay --- */}
            <tr className="section-divider-row">
              <th colSpan={alternatives.length + 1} scope="colgroup">
                Stay Details
              </th>
            </tr>
            <tr>
              <th scope="row" className="row-header">Property &amp; Category</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.stay ? (
                    <div className="cell-stay-block">
                      <strong>{alt.selections.stay.propertyName}</strong>
                      <div style={{marginTop: '0.25rem'}}>
                        <span className="badge badge-upcoming">
                          {alt.selections.stay.propertyCategory || 'HOTEL'}
                        </span>
                      </div>
                    </div>
                  ) : (
                    renderMissing('stay')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Unit &amp; Rooms</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.stay ? (
                    <div>
                      {alt.selections.stay.unitName} ({alt.selections.stay.requiredRoomCount || alt.selections.stay.unitCount || 1} room{(alt.selections.stay.requiredRoomCount || alt.selections.stay.unitCount || 1) > 1 ? 's' : ''})
                    </div>
                  ) : (
                    renderMissing('stay')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Distance to Center</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.stay ? (
                    alt.selections.stay.distanceToCityCenterMeters !== null && alt.selections.stay.distanceToCityCenterMeters !== undefined ? (
                      `${(alt.selections.stay.distanceToCityCenterMeters / 1000).toFixed(1)} km to city center`
                    ) : (
                      '—'
                    )
                  ) : (
                    renderMissing('stay')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Stay Total</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.stay ? (
                    <div>
                      <strong>{formatTallyCents(alt.tally?.stayTotalCents)}</strong>
                      <div className="meta-note">
                        {alt.selections.stay.nights.length} night{alt.selections.stay.nights.length === 1 ? '' : 's'}
                      </div>
                    </div>
                  ) : (
                    renderMissing('stay')
                  )}
                </td>
              ))}
            </tr>

            {/* --- Section: Rental Car --- */}
            <tr className="section-divider-row">
              <th colSpan={alternatives.length + 1} scope="colgroup">
                Rental Car Details
              </th>
            </tr>
            <tr>
              <th scope="row" className="row-header">Vehicle Class</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.rental ? (
                    <div>
                      <strong>{alt.selections.rental.vehicleClassName}</strong>
                      <span className="badge badge-draft" style={{marginLeft: '0.4rem'}}>
                        {alt.selections.rental.vehicleCategory || 'SEDAN'}
                      </span>
                    </div>
                  ) : (
                    renderMissing('rental')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Pickup &amp; Return</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.rental ? (
                    <div className="cell-rental-dates">
                      <div>Pickup: {new Date(alt.selections.rental.pickupAt).toLocaleString('en-US', {month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'})}</div>
                      <div>Return: {new Date(alt.selections.rental.returnAt).toLocaleString('en-US', {month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'})}</div>
                      <div className="meta-note">At {alt.selections.rental.locationName}</div>
                    </div>
                  ) : (
                    renderMissing('rental')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">24-Hour Billing Cycles</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.rental ? (
                    `${getRentalCycles(alt.selections.rental.pickupAt, alt.selections.rental.returnAt)} days (24-hour cycles)`
                  ) : (
                    renderMissing('rental')
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="row-header">Rental Car Total</th>
              {alternatives.map((alt) => (
                <td key={alt.id} className="comparison-cell">
                  {alt.selections.rental ? (
                    <strong>{formatTallyCents(alt.tally?.rentalTotalCents)}</strong>
                  ) : (
                    renderMissing('rental')
                  )}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  );
}
