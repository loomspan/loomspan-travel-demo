import {useState, type FormEvent} from 'react';
import type {TripProfileSummary, AlternativeProfileSummary} from '../api/tripsApi';
import {IdentityApiError} from '../api/identityApi';

type TripListSectionProps = {
  upcoming: TripProfileSummary[];
  past: TripProfileSummary[];
  onSelectTrip: (tripId: string) => void;
  onDeleteTrip: (trip: TripProfileSummary) => void;
  onCancelTrip?: (trip: TripProfileSummary) => void;
  onRenameTrip?: (trip: TripProfileSummary, name: string) => Promise<void>;
  onPlanTrip?: () => void;
  onStartPlanTrip?: () => void;
  onStartAirfare?: () => void;
  onStartStay?: () => void;
  hideHeading?: boolean;
  headingLevel?: 2 | 3;
};

function AlternativeSummaryItem({alt}: {alt: AlternativeProfileSummary}) {
  const isExpired = alt.expired || alt.status.toUpperCase() === 'EXPIRED';

  return (
    <li className="alternative-summary-item">
      <div className="alternative-summary-meta">
        <span className="badge badge-planned">Saved option</span>
        {isExpired && <span className="badge badge-expired">Expired</span>}
        {alt.booked && <span className="badge badge-booked">Booked</span>}
        <strong>{alt.name ?? 'Saved option'}</strong>
        {alt.startDate && alt.endDate && <span>{alt.startDate} to {alt.endDate}</span>}
      </div>
    </li>
  );
}

function TripCard({
  trip,
  onSelect,
  onDelete,
  onCancel,
  onRename,
}: {
  trip: TripProfileSummary;
  onSelect: (tripId: string) => void;
  onDelete: (trip: TripProfileSummary) => void;
  onCancel?: (trip: TripProfileSummary) => void;
  onRename?: (trip: TripProfileSummary, name: string) => Promise<void>;
}) {
  const isPast = trip.temporalStatus === 'PAST';
  const isCanceled = trip.status === 'CANCELED';
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(trip.name ?? trip.label);
  const [renamePending, setRenamePending] = useState(false);
  const [renameError, setRenameError] = useState<string>();
  const submitRename = async (event: FormEvent) => {
    event.preventDefault();
    if (renamePending) return;
    const trimmed = name.trim();
    if (!trimmed) { setRenameError('Enter a trip name.'); return; }
    if (trimmed.length > 300) { setRenameError('Trip name must be 300 characters or fewer.'); return; }
    setRenamePending(true); setRenameError(undefined);
    try { if (!onRename) return; await onRename(trip, trimmed); setEditing(false); }
    catch (error) {
      setRenameError(error instanceof IdentityApiError && error.code === 'VERSION_CONFLICT'
        ? 'This trip changed on the server. Refresh Trips and try again.'
        : error instanceof Error ? error.message : 'Trip could not be renamed. Try again.');
    } finally { setRenamePending(false); }
  };

  return (
    <article className="card trip-card" aria-labelledby={`trip-heading-${trip.id}`}>
      <div className="trip-card-header">
        <div>
          <span className={`badge ${isCanceled ? 'badge-canceled' : isPast ? 'badge-past' : 'badge-upcoming'}`}>
            {isCanceled ? 'Canceled Trip' : isPast ? 'Past' : 'Upcoming'}
          </span>
          {trip.bookedCount > 0 && (
            <span className="badge badge-booked">Booking</span>
          )}
          <h3 id={`trip-heading-${trip.id}`} className="trip-card-title">
            {trip.name ?? trip.label}
          </h3>
          <p className="trip-card-subtitle">{trip.destinationName}</p>
          <p className="trip-card-subtitle">Working plan: {trip.startDate} to {trip.endDate}</p>
          {editing && <form className="trip-rename-form" onSubmit={submitRename}>
            <label htmlFor={`rename-${trip.id}`}>Trip name</label>
            <input id={`rename-${trip.id}`} value={name} aria-invalid={Boolean(renameError)} aria-describedby={renameError ? `rename-error-${trip.id}` : undefined} onChange={event => setName(event.target.value)} />
            {renameError && <p id={`rename-error-${trip.id}`} className="field-error" role="alert">{renameError}</p>}
            <button type="submit" disabled={renamePending}>{renamePending ? 'Saving…' : 'Save name'}</button>
            <button type="button" disabled={renamePending} onClick={() => {setEditing(false); setRenameError(undefined); setName(trip.name ?? trip.label);}}>Cancel rename</button>
          </form>}
          {trip.bookedCount > 0 && trip.primaryBookingReference && (
            <p className="trip-card-booking-ref">
              Booking Reference: <strong>{trip.primaryBookingReference}</strong>
            </p>
          )}
        </div>
        <div className="trip-card-counts">
          <span className="count-pill">{trip.plannedCount} Saved option{trip.plannedCount === 1 ? '' : 's'}</span>
          {trip.expiredAlternativeCount > 0 && (
            <span className="count-pill badge-expired">{trip.expiredAlternativeCount} Expired</span>
          )}
          {trip.bookedCount > 0 && (
            <span className="count-pill badge-booked">{trip.bookedCount} Booking{trip.bookedCount === 1 ? '' : 's'}</span>
          )}
        </div>
      </div>

      {trip.alternatives && trip.alternatives.some(alt => alt.lifecycle.toUpperCase() === 'PLANNED') && (
        <div className="trip-card-alternatives">
          <h4 className="alternatives-heading">Saved options</h4>
          <ul className="alternatives-summary-list" aria-label={`Saved options for ${trip.name ?? trip.label}`}>
            {trip.alternatives.filter(alt => alt.lifecycle.toUpperCase() === 'PLANNED').map((alt) => (
              <AlternativeSummaryItem key={alt.id} alt={alt} />
            ))}
          </ul>
        </div>
      )}

      <div className="trip-card-actions">
        {!editing && onRename && <button type="button" className="text-button" onClick={() => {setName(trip.name ?? trip.label); setEditing(true);}}>Rename trip</button>}
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
  onRenameTrip,
  onPlanTrip,
  onStartPlanTrip,
  onStartAirfare,
  onStartStay,
  hideHeading = false,
  headingLevel = 3,
}: TripListSectionProps) {
  const [filter, setFilter] = useState<'all' | 'upcoming' | 'past' | 'canceled'>('all');
  const upcomingTrips = upcoming.filter(trip => trip.status !== 'CANCELED');
  const pastTrips = past.filter(trip => trip.status !== 'CANCELED');
  const canceledTrips = [...upcoming, ...past].filter(trip => trip.status === 'CANCELED');
  const handlePlanTrip = onStartPlanTrip ?? onPlanTrip;
  const SectionHeading = headingLevel === 2 ? 'h2' : 'h3';

  return (
    <div className="trips-container">
      {!hideHeading && <div className="trips-header">
        <h2>My Trips</h2>
        {handlePlanTrip && <div className="trips-header-actions" style={{display: 'flex', gap: '0.5rem', flexWrap: 'wrap'}}>
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
        </div>}
      </div>}

      <div className="trip-filters" role="group" aria-label="Filter my trips">
        {([
          ['all', 'All trips', upcoming.length + past.length],
          ['upcoming', 'Upcoming', upcomingTrips.length],
          ['past', 'Past', pastTrips.length],
          ['canceled', 'Canceled', canceledTrips.length],
        ] as const).map(([value, label, count]) => (
          <button key={value} type="button" className={filter === value ? 'secondary selected' : 'secondary'}
            aria-pressed={filter === value} onClick={() => setFilter(value)}>{label} ({count})</button>
        ))}
      </div>

      {(filter === 'all' || filter === 'upcoming') && <section className="trips-section" aria-labelledby="upcoming-trips-heading">
        <SectionHeading id="upcoming-trips-heading" className="section-title" tabIndex={-1}>
          Upcoming trips ({upcomingTrips.length})
        </SectionHeading>
        {upcomingTrips.length === 0 ? (
          <p className="hint">No upcoming trips planned yet.</p>
        ) : (
          <div className="trips-grid">
            {upcomingTrips.map((trip) => (
              <TripCard
                key={trip.id}
                trip={trip}
                onSelect={onSelectTrip}
                onDelete={onDeleteTrip}
                onCancel={onCancelTrip}
                onRename={onRenameTrip}
              />
            ))}
          </div>
        )}
      </section>}

      {(filter === 'all' || filter === 'past') && <section className="trips-section" aria-labelledby="past-trips-heading">
        <SectionHeading id="past-trips-heading" className="section-title" tabIndex={-1}>
          Past trips ({pastTrips.length})
        </SectionHeading>
        {pastTrips.length === 0 ? (
          <p className="hint">No past trips.</p>
        ) : (
          <div className="trips-grid">
            {pastTrips.map((trip) => (
              <TripCard
                key={trip.id}
                trip={trip}
                onSelect={onSelectTrip}
                onDelete={onDeleteTrip}
                onRename={onRenameTrip}
                onCancel={onCancelTrip}
              />
            ))}
          </div>
        )}
      </section>}

      {(filter === 'canceled' || (filter === 'all' && canceledTrips.length > 0)) && <section className="trips-section" aria-labelledby="canceled-trips-heading">
        <SectionHeading id="canceled-trips-heading" className="section-title" tabIndex={-1}>
          Canceled trips ({canceledTrips.length})
        </SectionHeading>
        {canceledTrips.length === 0 ? (
          <p className="hint">No canceled trips.</p>
        ) : (
          <div className="trips-grid">
            {canceledTrips.map(trip => (
              <TripCard key={trip.id} trip={trip} onSelect={onSelectTrip} onDelete={onDeleteTrip}
                onCancel={onCancelTrip} onRename={onRenameTrip} />
            ))}
          </div>
        )}
      </section>}
    </div>
  );
}
