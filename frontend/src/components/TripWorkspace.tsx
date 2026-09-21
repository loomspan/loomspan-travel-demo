import {useEffect, useRef, useState, useCallback} from 'react';
import {
  tripsApi,
  type TripResponse,
  type AlternativeResponse,
  type RevisionSummaryResponse,
} from '../api/tripsApi';
import {IdentityApiError} from '../api/identityApi';
import {AlternativeCard} from './AlternativeCard';
import {RevisionSummaryBanner} from './RevisionSummaryBanner';
import {TripRevisionModal} from './TripRevisionModal';
import {ConfirmDeleteModal, type DeleteTarget} from './ConfirmDeleteModal';

type TripWorkspaceProps = {
  initialTrip: TripResponse;
  hasBookingHistory?: boolean;
  temporalStatus?: string;
  isExpired?: boolean;
  onBack: () => void;
  onTripDeleted: () => void;
  onTripUpdated?: (trip: TripResponse) => void;
  onLogout?: () => Promise<void>;
  logoutPending?: boolean;
};

const SUPPORTED_DESTINATIONS = [
  {key: 'destination-sfo', name: 'San Francisco'},
  {key: 'destination-muc', name: 'Munich'},
  {key: 'destination-mex', name: 'Mexico City'},
];

function validateDates(startDate: string, endDate: string): string | undefined {
  if (!startDate || !endDate) return 'Please enter both departure and return dates.';
  if (startDate < '2027-03-01' || startDate > '2027-03-31') return 'Departure date must be in March 2027.';
  if (endDate < '2027-03-01' || endDate > '2027-03-31') return 'Return date must be in March 2027.';
  if (endDate <= startDate) return 'Return date must be after departure date.';
  const start = new Date(startDate);
  const end = new Date(endDate);
  const diffDays = Math.round((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24));
  if (diffDays < 1 || diffDays > 14) return 'Trip duration must be between 1 and 14 nights.';
  return undefined;
}

export function TripWorkspace({
  initialTrip,
  hasBookingHistory = false,
  temporalStatus = 'UPCOMING',
  isExpired = false,
  onBack,
  onTripDeleted,
  onTripUpdated,
  onLogout,
  logoutPending = false,
}: TripWorkspaceProps) {
  const [trip, setTrip] = useState<TripResponse>(initialTrip);
  const [destinationKey, setDestinationKey] = useState(initialTrip.destinationKey);
  const [startDate, setStartDate] = useState(initialTrip.startDate);
  const [endDate, setEndDate] = useState(initialTrip.endDate);
  const [travelerCount, setTravelerCount] = useState(initialTrip.travelerCount);
  const [travelerCountInput, setTravelerCountInput] = useState<string>(String(initialTrip.travelerCount));
  const [travelerAges, setTravelerAges] = useState<string[]>(() => {
    if (initialTrip.travelerAges && initialTrip.travelerAges.length === initialTrip.travelerCount) {
      return initialTrip.travelerAges.map(String);
    }
    return Array.from({length: initialTrip.travelerCount}, () => '');
  });
  const [budgetDollars, setBudgetDollars] = useState<string>(() => {
    return initialTrip.budgetCents !== null && initialTrip.budgetCents !== undefined
      ? (initialTrip.budgetCents / 100).toFixed(2)
      : '';
  });

  const [autosaveStatus, setAutosaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error' | 'conflict'>('idle');
  const [autosaveMessage, setAutosaveMessage] = useState<string | undefined>();
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [revisionSummary, setRevisionSummary] = useState<RevisionSummaryResponse | null>(
    initialTrip.revisionSummary ?? null
  );

  const [isRevisionModalOpen, setIsRevisionModalOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [deletePending, setDeletePending] = useState(false);
  const [deleteError, setDeleteError] = useState<string | undefined>();

  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isInitialMount = useRef(true);
  const isSavingRef = useRef(false);
  const pendingSaveRef = useRef(false);

  const applyTripState = useCallback((t: TripResponse) => {
    setTrip(t);
    setDestinationKey(t.destinationKey);
    setStartDate(t.startDate);
    setEndDate(t.endDate);
    setTravelerCount(t.travelerCount);
    setTravelerCountInput(String(t.travelerCount));
    setTravelerAges(
      t.travelerAges && t.travelerAges.length === t.travelerCount
        ? t.travelerAges.map(String)
        : Array.from({length: t.travelerCount}, () => '')
    );
    setBudgetDollars(
      t.budgetCents !== null && t.budgetCents !== undefined
        ? (t.budgetCents / 100).toFixed(2)
        : ''
    );
    if (t.revisionSummary) {
      setRevisionSummary(t.revisionSummary);
    }
  }, []);

  // Sync state if initialTrip changes
  useEffect(() => {
    applyTripState(initialTrip);
  }, [initialTrip, applyTripState]);

  const hasPlanned = trip.planned && trip.planned.length > 0;

  // Validation helper
  const validateInputs = useCallback(() => {
    const errors: Record<string, string> = {};

    if (!hasPlanned) {
      const dateErr = validateDates(startDate, endDate);
      if (dateErr) errors.dates = dateErr;
    }

    const count = parseInt(travelerCountInput, 10);
    if (isNaN(count) || count < 1 || count > 8) {
      errors.travelerCount = 'Traveler count must be between 1 and 8.';
    }

    // Validate ages
    const agesList: number[] = [];
    let hasAnyAge = false;
    const effectiveCount = !isNaN(count) && count >= 1 && count <= 8 ? count : travelerCount;
    for (let i = 0; i < effectiveCount; i++) {
      const ageStr = (travelerAges[i] ?? '').trim();
      if (ageStr !== '') {
        hasAnyAge = true;
        const ageNum = parseInt(ageStr, 10);
        if (isNaN(ageNum) || ageNum < 0 || ageNum > 120) {
          errors[`age_${i}`] = 'Age must be between 0 and 120.';
        } else {
          agesList.push(ageNum);
        }
      }
    }

    if (hasAnyAge && agesList.length !== effectiveCount) {
      errors.travelerAges = 'Please provide ages for all travelers or leave all blank.';
    }

    // Validate budget
    let cents: number | null = null;
    if (budgetDollars.trim() !== '') {
      const parsedDollars = parseFloat(budgetDollars);
      if (isNaN(parsedDollars) || parsedDollars < 0 || parsedDollars > 1000000) {
        errors.budget = 'Budget must be between $0.00 and $1,000,000.00.';
      } else {
        cents = Math.round(parsedDollars * 100);
      }
    }

    return {errors, ages: hasAnyAge && agesList.length === effectiveCount ? agesList : null, budgetCents: cents};
  }, [hasPlanned, startDate, endDate, travelerCount, travelerCountInput, travelerAges, budgetDollars]);

  // Dirty checking
  const isDirty = useCallback((ages: number[] | null, cents: number | null) => {
    if (destinationKey !== trip.destinationKey) return true;
    if (startDate !== trip.startDate) return true;
    if (endDate !== trip.endDate) return true;
    const count = parseInt(travelerCountInput, 10);
    if (isNaN(count) || count !== trip.travelerCount) return true;
    if (cents !== (trip.budgetCents ?? null)) return true;

    const savedAges = trip.travelerAges ?? null;
    if (ages === null && savedAges === null) return false;
    if (ages === null || savedAges === null) return true;
    if (ages.length !== savedAges.length) return true;
    for (let i = 0; i < ages.length; i++) {
      if (ages[i] !== savedAges[i]) return true;
    }
    return false;
  }, [destinationKey, startDate, endDate, travelerCountInput, trip]);

  // Autosave execution with serialization
  const executeAutosave = useCallback(async () => {
    if (isSavingRef.current) {
      pendingSaveRef.current = true;
      return;
    }

    const {errors, ages, budgetCents} = validateInputs();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setAutosaveStatus('error');
      setAutosaveMessage('Please correct the highlighted errors.');
      return;
    }

    if (!isDirty(ages, budgetCents)) {
      return;
    }

    setFieldErrors({});
    setAutosaveStatus('saving');
    setAutosaveMessage('Saving changes…');
    isSavingRef.current = true;
    pendingSaveRef.current = false;

    try {
      const count = parseInt(travelerCountInput, 10);
      const updatedTrip = await tripsApi.replaceSharedDetails(trip.id, {
        expectedVersion: trip.version,
        destinationKey,
        startDate,
        endDate,
        travelerCount: count,
        travelerAges: ages,
        budgetCents,
      });

      setTrip(updatedTrip);
      if (updatedTrip.revisionSummary) {
        setRevisionSummary(updatedTrip.revisionSummary);
      }
      setAutosaveStatus('saved');
      setAutosaveMessage('All changes saved.');
    } catch (err) {
      if (err instanceof IdentityApiError) {
        if (err.code === 'VERSION_CONFLICT') {
          setAutosaveStatus('conflict');
          setAutosaveMessage('The Trip has changed on the server. Reload before saving.');
        } else if (err.code === 'IMMUTABLE_TRIP') {
          setAutosaveStatus('error');
          setAutosaveMessage('Trips with Planned alternatives cannot change destination, dates, or travelers in place. Use "Revise Trip".');
        } else if (err.code === 'VALIDATION_FAILED' || Object.keys(err.fields).length > 0) {
          setFieldErrors(err.fields);
          setAutosaveStatus('error');
          setAutosaveMessage('Please correct the highlighted fields.');
        } else if (err.kind === 'network') {
          setAutosaveStatus('error');
          setAutosaveMessage('Network error. Check your connection.');
        } else {
          setAutosaveStatus('error');
          setAutosaveMessage(err.message || 'Could not save changes.');
        }
      } else {
        setAutosaveStatus('error');
        setAutosaveMessage('Something went wrong. Please try again.');
      }
    } finally {
      isSavingRef.current = false;
      if (pendingSaveRef.current) {
        pendingSaveRef.current = false;
        if (debounceTimer.current) clearTimeout(debounceTimer.current);
        debounceTimer.current = setTimeout(() => {
          void executeAutosaveRef.current();
        }, 300);
      }
    }
  }, [trip.id, trip.version, destinationKey, startDate, endDate, travelerCountInput, validateInputs, isDirty]);

  const executeAutosaveRef = useRef(executeAutosave);
  useEffect(() => {
    executeAutosaveRef.current = executeAutosave;
  }, [executeAutosave]);

  // Trigger debounced autosave on inputs change
  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
      return;
    }

    const {ages, budgetCents} = validateInputs();
    if (!isDirty(ages, budgetCents)) {
      return;
    }

    if (debounceTimer.current) clearTimeout(debounceTimer.current);
    debounceTimer.current = setTimeout(() => {
      void executeAutosaveRef.current();
    }, 600);

    return () => {
      if (debounceTimer.current) clearTimeout(debounceTimer.current);
    };
  }, [travelerAges, budgetDollars, destinationKey, startDate, endDate, travelerCountInput, validateInputs, isDirty]);

  // Reload latest from server on conflict
  const handleReloadFromServer = async () => {
    try {
      const freshTrip = await tripsApi.getTrip(trip.id);
      applyTripState(freshTrip);
      setAutosaveStatus('idle');
      setAutosaveMessage(undefined);
      setFieldErrors({});
      if (deleteTarget && deleteTarget.kind === 'trip') {
        setDeleteTarget({
          ...deleteTarget,
          draftCount: freshTrip.drafts ? freshTrip.drafts.length : 0,
          plannedCount: freshTrip.planned ? freshTrip.planned.length : 0,
        });
      }
    } catch {
      setAutosaveStatus('error');
      setAutosaveMessage('Could not reload trip data.');
    }
  };

  // Alternative handlers
  const handleCreateEmptyDraft = async () => {
    try {
      const updated = await tripsApi.createDraft(trip.id, {expectedVersion: trip.version});
      setTrip(updated);
      setAutosaveStatus('saved');
      setAutosaveMessage('Empty draft created.');
    } catch (err) {
      const failure = err instanceof IdentityApiError ? err.message : 'Could not create draft.';
      setAutosaveStatus('error');
      setAutosaveMessage(failure);
    }
  };

  const handleDuplicateDraft = async (draftId: string, version: number) => {
    try {
      const updated = await tripsApi.duplicateDraft(trip.id, draftId, {
        expectedVersion: trip.version,
        expectedDraftVersion: version,
      });
      setTrip(updated);
      setAutosaveStatus('saved');
      setAutosaveMessage('Draft duplicated.');
    } catch (err) {
      const failure = err instanceof IdentityApiError ? err.message : 'Could not duplicate draft.';
      setAutosaveStatus('error');
      setAutosaveMessage(failure);
    }
  };

  const handleDuplicatePlanned = async (alternativeId: string) => {
    try {
      const updated = await tripsApi.duplicateAlternative(trip.id, alternativeId, {
        expectedVersion: trip.version,
      });
      setTrip(updated);
      setAutosaveStatus('saved');
      setAutosaveMessage('Planned itinerary duplicated to draft.');
    } catch (err) {
      const failure = err instanceof IdentityApiError ? err.message : 'Could not duplicate alternative.';
      setAutosaveStatus('error');
      setAutosaveMessage(failure);
    }
  };

  // Delete modal triggers
  const promptDeleteDraft = (draftId: string, version: number) => {
    setDeleteError(undefined);
    setDeleteTarget({
      kind: 'draft',
      tripId: trip.id,
      draftId,
      draftVersion: version,
    });
  };

  const promptDeletePlanned = (alternativeId: string) => {
    setDeleteError(undefined);
    setDeleteTarget({
      kind: 'planned',
      tripId: trip.id,
      alternativeId,
    });
  };

  const promptDeleteTrip = () => {
    setDeleteError(undefined);
    setDeleteTarget({
      kind: 'trip',
      tripId: trip.id,
      label: trip.label,
      draftCount: trip.drafts ? trip.drafts.length : 0,
      plannedCount: trip.planned ? trip.planned.length : 0,
      hasBookingHistory,
    });
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    setDeletePending(true);
    setDeleteError(undefined);

    try {
      if (deleteTarget.kind === 'draft') {
        const updated = await tripsApi.deleteDraft(deleteTarget.tripId, deleteTarget.draftId, {
          expectedVersion: trip.version,
          expectedDraftVersion: deleteTarget.draftVersion,
        });
        setTrip(updated);
        setDeleteTarget(null);
        setAutosaveStatus('saved');
        setAutosaveMessage('Draft deleted.');
      } else if (deleteTarget.kind === 'planned') {
        const updated = await tripsApi.deleteAlternative(deleteTarget.tripId, deleteTarget.alternativeId, {
          expectedVersion: trip.version,
          confirmed: true,
        });
        setTrip(updated);
        setDeleteTarget(null);
        setAutosaveStatus('saved');
        setAutosaveMessage('Planned itinerary deleted.');
      } else if (deleteTarget.kind === 'trip') {
        await tripsApi.deleteTrip(deleteTarget.tripId, {
          expectedVersion: trip.version,
          expectedDraftCount: deleteTarget.draftCount,
          expectedPlannedCount: deleteTarget.plannedCount,
          confirmed: true,
        });
        setDeleteTarget(null);
        onTripDeleted();
      }
    } catch (err) {
      if (err instanceof IdentityApiError) {
        if (err.code === 'STALE_CONFIRMATION') {
          setDeleteError('The alternative counts on the server have changed. Reloading latest data…');
          void handleReloadFromServer();
        } else if (err.code === 'VERSION_CONFLICT') {
          setDeleteError('The Trip has changed on the server. Reloading latest data…');
          void handleReloadFromServer();
        } else {
          setDeleteError(err.message || 'Deletion failed. Please try again.');
        }
      } else {
        setDeleteError('Something went wrong. Please try again.');
      }
    } finally {
      setDeletePending(false);
    }
  };

  const updateTravelerAge = (index: number, val: string) => {
    setTravelerAges((prev) => {
      const next = [...prev];
      next[index] = val;
      return next;
    });
  };

  return (
    <section className="card workspace-card" aria-labelledby="workspace-heading">
      <div className="workspace-nav">
        <button type="button" className="text-button" onClick={onBack}>
          ← Back to all trips
        </button>
        <div style={{display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap'}}>
          <button
            type="button"
            className="text-button delete-button"
            onClick={promptDeleteTrip}
            disabled={hasBookingHistory}
            title={hasBookingHistory ? 'Trips with booking history cannot be deleted' : undefined}
            aria-label={`Delete trip ${trip.label}`}
          >
            {hasBookingHistory ? 'Has booking history' : 'Delete trip'}
          </button>
          {onLogout && (
            <button
              type="button"
              className="text-button"
              disabled={logoutPending}
              onClick={() => void onLogout()}
            >
              {logoutPending ? 'Logging out…' : 'Log out'}
            </button>
          )}
        </div>
      </div>

      <header className="workspace-header">
        <div>
          <p className="eyebrow">TRIP WORKSPACE</p>
          <div className="badge-row" style={{marginBottom: '0.35rem'}}>
            <span className={`badge ${temporalStatus === 'PAST' ? 'badge-past' : 'badge-upcoming'}`}>
              {temporalStatus === 'PAST' ? 'Past' : 'Upcoming'}
            </span>
            {isExpired && <span className="badge badge-expired">Expired</span>}
          </div>
          <h1 id="workspace-heading" tabIndex={-1}>{trip.label}</h1>
          <p className="trip-meta">
            From {trip.originAirportCode} to {trip.destinationName} • {trip.startDate} to {trip.endDate} • {trip.travelerCount} traveler{trip.travelerCount === 1 ? '' : 's'} (v{trip.version})
          </p>
        </div>

        {/* Autosave status indicator with aria-live="polite" */}
        <div
          className={`autosave-status status-${autosaveStatus}`}
          role="status"
          aria-live="polite"
        >
          {autosaveStatus === 'saving' && <span>Saving…</span>}
          {autosaveStatus === 'saved' && <span>All changes saved.</span>}
          {autosaveStatus === 'error' && <span className="field-error">{autosaveMessage}</span>}
          {autosaveStatus === 'conflict' && (
            <div className="conflict-alert" role="alert">
              <span>{autosaveMessage}</span>
              <button
                type="button"
                className="text-button reload-button"
                onClick={() => void handleReloadFromServer()}
              >
                Reload from server
              </button>
            </div>
          )}
        </div>
      </header>

      {revisionSummary && (
        <RevisionSummaryBanner
          summary={revisionSummary}
          onDismiss={() => setRevisionSummary(null)}
        />
      )}

      {/* Shared details form */}
      <section className="workspace-section" aria-labelledby="shared-details-heading">
        <div className="section-header">
          <h3 id="shared-details-heading">Trip Details &amp; Travelers</h3>
          {hasPlanned && (
            <button
              type="button"
              className="text-button revise-button"
              onClick={() => setIsRevisionModalOpen(true)}
            >
              Revise Trip
            </button>
          )}
        </div>

        {hasPlanned && (
          <p className="hint read-only-hint">
            Trips with Planned alternatives cannot change destination, dates, or traveler count in place.
            Use &ldquo;Revise Trip&rdquo; to create a new version.
          </p>
        )}

        <form onSubmit={(e) => e.preventDefault()} noValidate>
          <div className="field-group">
            <div className="field">
              <label htmlFor="workspace-destination">Destination</label>
              <select
                id="workspace-destination"
                value={destinationKey}
                disabled={hasPlanned}
                onChange={(e) => setDestinationKey(e.target.value)}
              >
                {SUPPORTED_DESTINATIONS.map((dest) => (
                  <option key={dest.key} value={dest.key}>
                    {dest.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="workspace-start-date">Departure date</label>
              <input
                type="date"
                id="workspace-start-date"
                value={startDate}
                disabled={hasPlanned}
                min="2027-03-01"
                max="2027-03-31"
                onChange={(e) => setStartDate(e.target.value)}
                aria-describedby={fieldErrors.dates ? 'workspace-dates-error' : undefined}
              />
            </div>

            <div className="field">
              <label htmlFor="workspace-end-date">Return date</label>
              <input
                type="date"
                id="workspace-end-date"
                value={endDate}
                disabled={hasPlanned}
                min="2027-03-01"
                max="2027-03-31"
                onChange={(e) => setEndDate(e.target.value)}
                aria-describedby={fieldErrors.dates ? 'workspace-dates-error' : undefined}
              />
            </div>
          </div>
          {fieldErrors.dates && (
            <p className="field-error" id="workspace-dates-error">
              {fieldErrors.dates}
            </p>
          )}

          <div className="field-group">
            <div className="field">
              <label htmlFor="workspace-budget">Budget (USD)</label>
              <input
                type="number"
                id="workspace-budget"
                min="0"
                max="1000000"
                step="0.01"
                placeholder="e.g. 2500.00"
                value={budgetDollars}
                onChange={(e) => setBudgetDollars(e.target.value)}
                aria-describedby={fieldErrors.budget ? 'workspace-budget-error' : undefined}
              />
              {fieldErrors.budget && (
                <p className="field-error" id="workspace-budget-error">
                  {fieldErrors.budget}
                </p>
              )}
            </div>

            <div className="field">
              <label htmlFor="workspace-traveler-count">Travelers</label>
              <input
                type="number"
                id="workspace-traveler-count"
                min="1"
                max="8"
                disabled={hasPlanned}
                value={travelerCountInput}
                onChange={(e) => {
                  const raw = e.target.value;
                  setTravelerCountInput(raw);
                  const val = parseInt(raw, 10);
                  if (!isNaN(val) && val >= 1 && val <= 8) {
                    setTravelerCount(val);
                    setTravelerAges((prev) => Array.from({length: val}, (_, i) => prev[i] ?? ''));
                  }
                }}
                aria-describedby={fieldErrors.travelerCount ? 'workspace-traveler-count-error' : undefined}
              />
              {fieldErrors.travelerCount && (
                <p className="field-error" id="workspace-traveler-count-error">
                  {fieldErrors.travelerCount}
                </p>
              )}
            </div>
          </div>

          <div className="field">
            <fieldset>
              <legend>Traveler Ages (0–120)</legend>
              <p className="hint">Optional. Needed before promoting a draft to a planned itinerary.</p>
              <div className="ages-grid">
                {Array.from({length: travelerCount}, (_, i) => (
                  <div key={i} className="field age-field">
                    <label htmlFor={`traveler-age-${i}`}>Traveler {i + 1} age</label>
                    <input
                      type="number"
                      id={`traveler-age-${i}`}
                      min="0"
                      max="120"
                      disabled={hasPlanned}
                      placeholder="Age"
                      value={travelerAges[i] ?? ''}
                      onChange={(e) => updateTravelerAge(i, e.target.value)}
                      aria-describedby={fieldErrors[`age_${i}`] ? `age-error-${i}` : undefined}
                    />
                    {fieldErrors[`age_${i}`] && (
                      <p className="field-error" id={`age-error-${i}`}>
                        {fieldErrors[`age_${i}`]}
                      </p>
                    )}
                  </div>
                ))}
              </div>
              {fieldErrors.travelerAges && (
                <p className="field-error">{fieldErrors.travelerAges}</p>
              )}
            </fieldset>
          </div>
        </form>
      </section>

      {/* Alternatives section */}
      <section className="workspace-section" aria-labelledby="alternatives-heading">
        <div className="section-header">
          <h3 id="alternatives-heading" tabIndex={-1}>
            Alternatives ({trip.alternatives ? trip.alternatives.length : 0})
          </h3>
          <span className="count-pill">
            {trip.drafts ? trip.drafts.length : 0} Draft alternative{trip.drafts && trip.drafts.length === 1 ? '' : 's'}
          </span>
          <button
            type="button"
            className="primary create-empty-draft-btn"
            onClick={() => void handleCreateEmptyDraft()}
          >
            Create empty draft
          </button>
        </div>
        <p className="hint">
          Draft alternatives let you explore and compare options. Empty draft creation starts fresh, while duplication copies an existing source.
        </p>

        {(!trip.alternatives || trip.alternatives.length === 0) ? (
          <p className="hint">No alternatives yet. Create an empty draft to get started.</p>
        ) : (
          <div className="alternatives-grid">
            {trip.alternatives.map((alt: AlternativeResponse) => (
              <AlternativeCard
                key={alt.id}
                alternative={alt}
                tripExpired={isExpired}
                onDuplicateDraft={(id, ver) => void handleDuplicateDraft(id, ver)}
                onDuplicatePlanned={(id) => void handleDuplicatePlanned(id)}
                onDeleteDraft={(id, ver) => promptDeleteDraft(id, ver)}
                onDeletePlanned={(id) => promptDeletePlanned(id)}
              />
            ))}
          </div>
        )}
      </section>

      {/* Revision modal */}
      {isRevisionModalOpen && (
        <TripRevisionModal
          isOpen={isRevisionModalOpen}
          trip={trip}
          onClose={() => setIsRevisionModalOpen(false)}
          onSuccess={(newTrip) => {
            applyTripState(newTrip);
            setIsRevisionModalOpen(false);
            if (onTripUpdated) onTripUpdated(newTrip);
          }}
        />
      )}

      {/* Delete confirmation modal */}
      {deleteTarget && (
        <ConfirmDeleteModal
          isOpen={Boolean(deleteTarget)}
          target={deleteTarget}
          pending={deletePending}
          errorMessage={deleteError}
          onClose={() => setDeleteTarget(null)}
          onConfirm={() => void handleConfirmDelete()}
        />
      )}
    </section>
  );
}
