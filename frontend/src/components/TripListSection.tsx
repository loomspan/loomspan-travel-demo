import {useEffect, useRef, useState, type FormEvent, type KeyboardEvent} from 'react';
import type {TripProfileSummary} from '../api/tripsApi';
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

const nameCollator = new Intl.Collator('en', {sensitivity: 'base'});
const lexical = (a: string, b: string) => a < b ? -1 : a > b ? 1 : 0;
const byName = (a: TripProfileSummary, b: TripProfileSummary) =>
  nameCollator.compare(a.name ?? a.label, b.name ?? b.label) || lexical(a.id, b.id);
const dateFormatter = new Intl.DateTimeFormat('en-US', {month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC'});
const formatDate = (date: string) => dateFormatter.format(new Date(`${date}T00:00:00Z`));

function TripCard({trip, onSelect, onDelete, onCancel, onRename, headingLevel}: {
  trip: TripProfileSummary;
  onSelect: (tripId: string) => void;
  onDelete: (trip: TripProfileSummary) => void;
  onCancel?: (trip: TripProfileSummary) => void;
  onRename?: (trip: TripProfileSummary, name: string) => Promise<void>;
  headingLevel: 2 | 3;
}) {
  const isPast = trip.temporalStatus === 'PAST';
  const isCanceled = trip.status === 'CANCELED';
  const title = trip.name ?? trip.label;
  const CardHeading = headingLevel === 2 ? 'h3' : 'h4';
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(title);
  const [renamePending, setRenamePending] = useState(false);
  const [renameError, setRenameError] = useState<string>();
  const [menuOpen, setMenuOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const initialMenuFocus = useRef<'first' | 'last'>('first');
  const wasEditing = useRef(false);
  const tabLeaving = useRef(false);
  const tabDismissTimer = useRef<number | undefined>(undefined);
  const menuId = `trip-menu-${trip.id}`;
  const triggerId = `trip-actions-${trip.id}`;
  const closeMenu = (restore = false) => {
    setMenuOpen(false);
    if (restore) triggerRef.current?.focus();
  };
  const enabledItems = () => Array.from(menuRef.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? []);
  useEffect(() => {
    if (!menuOpen) return;
    const items = enabledItems();
    (initialMenuFocus.current === 'last' ? items.at(-1) : items[0])?.focus();
    if (!items.length) menuRef.current?.focus();
    const dismissOutside = (event: Event) => {
      const target = event.target as Node;
      if (tabLeaving.current || (!menuRef.current?.contains(target) && !triggerRef.current?.contains(target))) setMenuOpen(false);
    };
    document.addEventListener('pointerdown', dismissOutside);
    document.addEventListener('focusin', dismissOutside);
    return () => {
      document.removeEventListener('pointerdown', dismissOutside);
      document.removeEventListener('focusin', dismissOutside);
      window.clearTimeout(tabDismissTimer.current);
    };
  }, [menuOpen]);
  useEffect(() => {
    if (editing) inputRef.current?.focus();
    else if (wasEditing.current) triggerRef.current?.focus();
    wasEditing.current = editing;
  }, [editing]);
  const openMenu = (last = false) => {
    tabLeaving.current = false;
    initialMenuFocus.current = last ? 'last' : 'first';
    setMenuOpen(true);
  };
  const menuKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Tab') {
      tabLeaving.current = true;
      // Defer removal until native focus advancement, including departure to
      // browser chrome where no document focusin event follows.
      tabDismissTimer.current = window.setTimeout(() => setMenuOpen(false), 0);
      return;
    }
    if (event.key === 'Escape') { event.preventDefault(); closeMenu(true); return; }
    // Native Tab moves beyond the menu's programmatically focused items. The
    // focusin listener closes it after that move, without trapping focus.
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const items = enabledItems();
    const index = items.indexOf(document.activeElement as HTMLButtonElement);
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1
      : (index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
    items[next]?.focus();
  };
  const finishRename = () => {
    setEditing(false); setRenameError(undefined); setName(title);
    triggerRef.current?.focus();
  };
  const activate = (action: () => void) => {
    // The dialogs capture the active element: restore the persistent trigger
    // before their callback replaces the menu with a modal.
    closeMenu(true); action();
  };
  const submitRename = async (event: FormEvent) => {
    event.preventDefault();
    if (renamePending) return;
    const trimmed = name.trim();
    if (!trimmed) { setRenameError('Enter a trip name.'); return; }
    if (trimmed.length > 300) { setRenameError('Trip name must be 300 characters or fewer.'); return; }
    setRenamePending(true); setRenameError(undefined);
    try { if (!onRename) return; await onRename(trip, trimmed); finishRename(); }
    catch (error) {
      setRenameError(error instanceof IdentityApiError && error.code === 'VERSION_CONFLICT'
        ? 'This trip changed on the server. Refresh Trips and try again.'
        : error instanceof Error ? error.message : 'Trip could not be renamed. Try again.');
    } finally { setRenamePending(false); }
  };

  return (
    <article className="card trip-card" aria-labelledby={`trip-heading-${trip.id}`}>
      <CardHeading id={`trip-heading-${trip.id}`} className="trip-card-title">{title}</CardHeading>
      <p className="trip-card-subtitle">{trip.destinationName}</p>
      <p className="trip-card-subtitle">{formatDate(trip.startDate)} – {formatDate(trip.endDate)}</p>
      <div className="trip-card-summary">
        <span className={`badge ${isCanceled ? 'badge-canceled' : isPast ? 'badge-past' : 'badge-upcoming'}`}>
          {isCanceled ? 'Canceled Trip' : isPast ? 'Past' : trip.inProgress ? 'In progress' : 'Upcoming'}
        </span>
        <span className="count-pill">{trip.plannedCount} Saved option{trip.plannedCount === 1 ? '' : 's'}</span>
        {trip.expiredAlternativeCount > 0 && <span className="count-pill badge-expired">{trip.expiredAlternativeCount} Expired</span>}
        {trip.bookedCount > 0 && <span className="count-pill badge-booked">{trip.bookedCount} Booking{trip.bookedCount === 1 ? '' : 's'}</span>}
      </div>
      {trip.bookedCount > 0 && trip.primaryBookingReference && <p className="trip-card-booking-ref">
        Booking Reference: <strong>{trip.primaryBookingReference}</strong>
      </p>}
      <div className="trip-card-actions">
        <button type="button" className="primary" onClick={() => onSelect(trip.id)} aria-label={`Open trip ${trip.label}`}>Open trip</button>
        <button ref={triggerRef} id={triggerId} type="button" className="secondary"
          aria-label={`Actions for ${title}`} aria-haspopup="menu" aria-expanded={menuOpen} aria-controls={menuId}
          disabled={renamePending}
          onClick={() => menuOpen ? closeMenu(true) : openMenu()}
          onKeyDown={event => {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
              event.preventDefault(); openMenu(event.key === 'ArrowUp');
            }
          }}>Actions</button>
      </div>
      {menuOpen && <div ref={menuRef} id={menuId} role="menu" tabIndex={-1} aria-labelledby={triggerId} className="trip-action-menu" onKeyDown={menuKeyDown}>
        {!editing && onRename && <button type="button" role="menuitem" tabIndex={-1} className="text-button"
          onClick={() => activate(() => {setName(title); setRenameError(undefined); setEditing(true);})}>Rename trip</button>}
        {isCanceled ? <button type="button" role="menuitem" tabIndex={-1} className="text-button" disabled
          title="This trip has been canceled" aria-label={`Trip ${trip.label} is canceled`}>Trip canceled</button>
          : trip.hasBookingHistory ? <button type="button" role="menuitem" tabIndex={-1} className="text-button delete-button"
            disabled={isPast || trip.expiredAlternativeCount > 0}
            title={isPast || trip.expiredAlternativeCount > 0 ? 'Past or expired trips cannot be canceled' : undefined}
            onClick={() => activate(() => onCancel?.(trip))} aria-label={`Cancel trip ${trip.label}`}>Cancel trip</button>
          : <button type="button" role="menuitem" tabIndex={-1} className="text-button delete-button"
            onClick={() => activate(() => onDelete(trip))} aria-label={`Delete trip ${trip.label}`}>Delete trip</button>}
      </div>}
      {editing && <form className="trip-rename-form" onSubmit={submitRename}>
        <label htmlFor={`rename-${trip.id}`}>Trip name</label>
        <input ref={inputRef} id={`rename-${trip.id}`} value={name} disabled={renamePending} aria-invalid={Boolean(renameError)}
          aria-describedby={renameError ? `rename-error-${trip.id}` : undefined} onChange={event => setName(event.target.value)} />
        {renameError && <p id={`rename-error-${trip.id}`} className="field-error" role="alert">{renameError}</p>}
        <button type="submit" className="primary button-sm" disabled={renamePending}>{renamePending ? 'Saving…' : 'Save name'}</button>
        <button type="button" className="text-button" disabled={renamePending} onClick={finishRename}>Cancel rename</button>
      </form>}
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
  const upcomingTrips = upcoming.filter(trip => trip.status !== 'CANCELED').sort((a, b) =>
    Number(Boolean(b.inProgress)) - Number(Boolean(a.inProgress)) || lexical(a.startDate, b.startDate) || byName(a, b));
  const pastTrips = past.filter(trip => trip.status !== 'CANCELED').sort((a, b) => lexical(b.endDate, a.endDate) || byName(a, b));
  const canceledTrips = [...upcoming, ...past].filter(trip => trip.status === 'CANCELED').sort((a, b) => lexical(b.startDate, a.startDate) || byName(a, b));
  const handlePlanTrip = onStartPlanTrip ?? onPlanTrip;
  const SectionHeading = headingLevel === 2 ? 'h2' : 'h3';

  return (
    <div className="trips-container">
      {!hideHeading && <div className="trips-header">
        <h2>My Trips</h2>
        {handlePlanTrip && <div className="trips-header-actions">
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
                trip={trip} headingLevel={headingLevel}
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
                trip={trip} headingLevel={headingLevel}
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
              <TripCard key={trip.id} trip={trip} headingLevel={headingLevel} onSelect={onSelectTrip} onDelete={onDeleteTrip}
                onCancel={onCancelTrip} onRename={onRenameTrip} />
            ))}
          </div>
        )}
      </section>}
    </div>
  );
}
