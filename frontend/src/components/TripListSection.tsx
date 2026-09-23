import type {TripProfileSummary, AlternativeProfileSummary} from '../api/tripsApi';

type TripListSectionProps = {
  upcoming: TripProfileSummary[];
  past: TripProfileSummary[];
  onSelectTrip: (tripId: string) => void;
  onDeleteTrip: (trip: TripProfileSummary) => void;
  onCancelTrip?: (trip: TripProfileSummary) => void;
  onPlanTrip?: () => void;
  onStartPlanTrip?: () => void;
  onStartAirfare?: () => void;
  onStartStay?: () => void;
};

function AlternativeSummaryItem({alt}: {alt: AlternativeProfileSummary}) {
  const isDraft = alt.lifecycle.toUpperCase() === 'DRAFT';
  const isExpired = alt.expired || alt.status.toUpperCase() === 'EXPIRED';

  return (
    <li className="alternative-summary-item">
      <div className="alternative-summary-meta">
        <span className={`badge ${isDraft ? 'badge-draft' : 'badge-planned'}`}>
          {isDraft ? (alt.version !== null ? `Draft v${alt.version}` : 'Draft') : 'Planned'}
        </span>
        {isExpired && <span className="badge badge-expired">Expired</span>}
        <span className="alternative-id" title={alt.id}>
          ID: {alt.id.slice(0, 8)}…
        </span>
      </div>
    </li>
  );
}

function TripCard({
  trip,
  onSelect,
  onDelete,
  onCancel,
}: {
  trip: TripProfileSummary;
  onSelect: (tripId: string) => void;
  onDelete: (trip: TripProfileSummary) => void;
  onCancel?: (trip: TripProfileSummary) => void;
}) {
  const isPast = trip.temporalStatus === 'PAST';
  const isCanceled = trip.status === 'CANCELED';

  return (
    <article className="card trip-card" aria-labelledby={`trip-heading-${trip.id}`}>
      <div className="trip-card-header">
        <div>
          <span className={`badge ${isPast ? 'badge-past' : 'badge-upcoming'}`}>
            {isPast ? 'Past' : 'Upcoming'}
          </span>
          {isCanceled && (
            <span className="badge badge-canceled">Canceled</span>
          )}
          {trip.bookedCount > 0 && (
            <span className="badge badge-booked">BOOKED</span>
          )}
          <h3 id={`trip-heading-${trip.id}`} className="trip-card-title">
            {trip.label}
          </h3>
          <p className="trip-card-subtitle">
            {trip.destinationName} • {trip.startDate} to {trip.endDate}
          </p>
          {trip.bookedCount > 0 && trip.primaryBookingReference && (
            <p className="trip-card-booking-ref">
              Booking Reference: <strong>{trip.primaryBookingReference}</strong>
            </p>
          )}
        </div>
        <div className="trip-card-counts">
          <span className="count-pill">{trip.draftCount} Draft alternative{trip.draftCount === 1 ? '' : 's'}</span>
          <span className="count-pill">{trip.plannedCount} Planned</span>
          {trip.expiredAlternativeCount > 0 && (
            <span className="count-pill badge-expired">{trip.expiredAlternativeCount} Expired</span>
          )}
          {trip.bookedCount > 0 && (
            <span className="count-pill badge-booked">{trip.bookedCount} Booked</span>
          )}
        </div>
      </div>

      {trip.alternatives && trip.alternatives.length > 0 && (
        <div className="trip-card-alternatives">
          <h4 className="alternatives-heading">Alternatives</h4>
          <ul className="alternatives-summary-list" aria-label={`Alternatives for ${trip.label}`}>
            {trip.alternatives.map((alt) => (
              <AlternativeSummaryItem key={alt.id} alt={alt} />
            ))}
          </ul>
        </div>
      )}

      <div className="trip-card-actions">
        {isCanceled ? (
          <button
            type="button"
            className="text-button"
            disabled
            title="This trip has been canceled"
            aria-label={`Trip ${trip.label} is canceled`}
          >
            Trip canceled
          </button>
        ) : trip.hasBookingHistory ? (
          isPast || trip.expiredAlternativeCount > 0 ? (
            <button
              type="button"
              className="text-button delete-button"
              disabled
              title="Past or expired trips cannot be canceled"
              aria-label={`Cancel trip ${trip.label}`}
            >
              Cancel trip
            </button>
          ) : (
            <button
              type="button"
              className="text-button delete-button"
              onClick={() => onCancel && onCancel(trip)}
              aria-label={`Cancel trip ${trip.label}`}
            >
              Cancel trip
            </button>
          )
        ) : (
          <button
            type="button"
            className="text-button delete-button"
            onClick={() => onDelete(trip)}
            aria-label={`Delete trip ${trip.label}`}
          >
            Delete trip
          </button>
        )}
        <button
          type="button"
          className="primary"
          onClick={() => onSelect(trip.id)}
          aria-label={`Open trip ${trip.label}`}
        >
          Open trip
        </button>
      </div>
    </article>
  );
}

export function TripListSection({
  upcoming,
  past,
  onSelectTrip,
  onDeleteTrip,
  onCancelTrip,
  onPlanTrip,
  onStartPlanTrip,
  onStartAirfare,
  onStartStay,
}: TripListSectionProps) {
  const handlePlanTrip = onStartPlanTrip ?? onPlanTrip;

  return (
    <div className="trips-container">
      <div className="trips-header">
        <h2>Your trips</h2>
        <div className="trips-header-actions" style={{display: 'flex', gap: '0.5rem', flexWrap: 'wrap'}}>
          <button type="button" className="primary" onClick={handlePlanTrip}>
            Plan Trip
          </button>
          <button
            type="button"
            className="secondary"
            onClick={onStartAirfare ?? handlePlanTrip}
          >
            Airfare
          </button>
          <button
            type="button"
            className="secondary"
            onClick={onStartStay ?? handlePlanTrip}
          >
            Stay
          </button>
        </div>
      </div>

      <section className="trips-section" aria-labelledby="upcoming-trips-heading">
        <h3 id="upcoming-trips-heading" className="section-title" tabIndex={-1}>
          Upcoming trips ({upcoming.length})
        </h3>
        {upcoming.length === 0 ? (
          <p className="hint">No upcoming trips planned yet.</p>
        ) : (
          <div className="trips-grid">
            {upcoming.map((trip) => (
              <TripCard
                key={trip.id}
                trip={trip}
                onSelect={onSelectTrip}
                onDelete={onDeleteTrip}
                onCancel={onCancelTrip}
              />
            ))}
          </div>
        )}
      </section>

      <section className="trips-section" aria-labelledby="past-trips-heading">
        <h3 id="past-trips-heading" className="section-title" tabIndex={-1}>
          Past trips ({past.length})
        </h3>
        {past.length === 0 ? (
          <p className="hint">No past trips.</p>
        ) : (
          <div className="trips-grid">
            {past.map((trip) => (
              <TripCard
                key={trip.id}
                trip={trip}
                onSelect={onSelectTrip}
                onDelete={onDeleteTrip}
                onCancel={onCancelTrip}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
