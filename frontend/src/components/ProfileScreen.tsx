import {FormEvent, useEffect, useRef, useState} from 'react';
import {PasswordField, passwordRangeError} from './PasswordField';
import type {FormFailure} from './AuthScreen';
import {EmptyProfileState} from './EmptyProfileState';
import {TripListSection} from './TripListSection';
import {TripCreateModal} from './TripCreateModal';
import {TripWorkspace, type TripWorkspaceHandle} from './TripWorkspace';
import {ConfirmDeleteModal, type DeleteTarget} from './ConfirmDeleteModal';
import {CancelTripModal} from './CancelTripModal';
import {tripsApi, type TripProfileSummary, type TripResponse, type AccommodationType} from '../api/tripsApi';
import {IdentityApiError} from '../api/identityApi';

type ProfileScreenProps = {
  email: string;
  upcoming?: TripProfileSummary[];
  past?: TripProfileSummary[];
  onLogout: () => Promise<void>;
  logoutPending: boolean;
  onPasswordChange: (currentPassword: string, newPassword: string) => Promise<void>;
  onFailure: (failure: FormFailure) => void;
  onRefreshProfile?: () => Promise<boolean | void>;
};

export function ProfileScreen({
  email,
  upcoming = [],
  past = [],
  onLogout,
  logoutPending,
  onPasswordChange,
  onFailure,
  onRefreshProfile,
}: ProfileScreenProps) {
  // Navigation mode
  const [viewMode, setViewMode] = useState<'home' | 'profile' | 'workspace'>('profile');
  const [activeTrip, setActiveTrip] = useState<TripResponse | null>(null);
  const workspaceRef = useRef<TripWorkspaceHandle>(null);
  const openingSequence = useRef(0);
  const [openingTripId, setOpeningTripId] = useState<string | null>(null);
  const [openError, setOpenError] = useState<{tripId: string; message: string} | null>(null);
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error' | 'conflict'>('idle');
  const [tripDirty, setTripDirty] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const heading = viewMode === 'workspace'
        ? document.querySelector<HTMLElement>('#workspace-heading, #comparison-heading, #booking-review-heading, #confirmation-heading')
        : document.getElementById(viewMode === 'home' ? 'home-heading' : 'profile-heading');
      heading?.focus();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [viewMode, activeTrip?.id]);

  // Modals state
  const [createModalMode, setCreateModalMode] = useState<'PLAN_TRIP' | 'AIRFARE' | 'STAY' | null>(null);
  const [entryContext, setEntryContext] = useState<{
    mode: 'PLAN_TRIP' | 'AIRFARE' | 'STAY';
    accommodationType?: AccommodationType;
  } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [deletePending, setDeletePending] = useState(false);
  const [deleteError, setDeleteError] = useState<string | undefined>();
  const [cancelTripTarget, setCancelTripTarget] = useState<TripProfileSummary | null>(null);
  const [cancelTripPending, setCancelTripPending] = useState(false);
  const [cancelTripError, setCancelTripError] = useState<string | undefined>();

  // Password form state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordPending, setPasswordPending] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handlePasswordSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const errors: Record<string, string> = {};
    const currentRangeError = passwordRangeError(currentPassword);
    if (currentRangeError) errors.currentPassword = currentPassword ? currentRangeError : 'Enter your current password.';
    const rangeError = passwordRangeError(newPassword);
    if (rangeError) errors.newPassword = rangeError;
    if (Object.keys(errors).length) {
      setCurrentPassword('');
      setNewPassword('');
      setFieldErrors(errors);
      onFailure({message: 'Please correct the highlighted fields.', fields: errors});
      return;
    }
    setPasswordPending(true);
    setFieldErrors({});
    try {
      await onPasswordChange(currentPassword, newPassword);
      setCurrentPassword('');
      setNewPassword('');
    } catch (failure) {
      const result = failure as FormFailure;
      setCurrentPassword('');
      setNewPassword('');
      setFieldErrors(result.fields ?? {});
      onFailure(result);
    } finally {
      setPasswordPending(false);
    }
  };

  const handleOpenTrip = async (tripId: string) => {
    if (activeTrip?.id === tripId) {
      setViewMode('workspace');
      await workspaceRef.current?.refreshIfClean();
      return;
    }
    if (workspaceRef.current?.hasUnsavedChanges() && !window.confirm('Discard unsaved Trip edits and open another Trip?')) return;
    const sequence = ++openingSequence.current;
    setOpeningTripId(tripId);
    setOpenError(null);
    try {
      const trip = await tripsApi.getTrip(tripId);
      if (sequence !== openingSequence.current) return;
      setEntryContext(null);
      setActiveTrip(trip);
      setViewMode('workspace');
    } catch (err) {
      if (sequence !== openingSequence.current) return;
      setOpenError({tripId, message: err instanceof IdentityApiError ? err.message : 'Could not open trip.'});
    } finally {
      if (sequence === openingSequence.current) setOpeningTripId(null);
    }
  };

  const startCreateTrip = (mode: 'PLAN_TRIP' | 'AIRFARE' | 'STAY') => {
    if (workspaceRef.current?.hasUnsavedChanges() && !window.confirm('Discard unsaved Trip edits and start another Trip?')) return;
    setCreateModalMode(mode);
  };

  const navigateTo = (destination: 'home' | 'profile') => {
    openingSequence.current += 1;
    setOpeningTripId(null);
    setOpenError(null);
    setViewMode(destination);
    if (destination === 'profile' && onRefreshProfile) void onRefreshProfile();
  };

  const handlePromptDeleteTrip = (trip: TripProfileSummary) => {
    if (activeTrip?.id === trip.id && workspaceRef.current?.hasUnsavedChanges() && !window.confirm('Discard unsaved Trip edits and delete this Trip?')) return;
    setDeleteError(undefined);
    setDeleteTarget({
      kind: 'trip',
      tripId: trip.id,
      label: trip.label,
      draftCount: trip.draftCount,
      plannedCount: trip.plannedCount,
      hasBookingHistory: trip.hasBookingHistory,
    });
  };

  const handlePromptCancelTrip = (trip: TripProfileSummary) => {
    setCancelTripError(undefined);
    setCancelTripTarget(trip);
  };

  const handleConfirmCancelTrip = async () => {
    if (!cancelTripTarget) return;
    setCancelTripPending(true);
    setCancelTripError(undefined);
    try {
      await tripsApi.cancelTrip(cancelTripTarget.id, {
        expectedVersion: cancelTripTarget.version,
      });
      setCancelTripTarget(null);
      if (onRefreshProfile) await onRefreshProfile();
    } catch (err) {
      if (err instanceof IdentityApiError) {
        if (err.code === 'VERSION_CONFLICT') {
          setCancelTripError('The trip has changed on the server. Please refresh and try again.');
        } else {
          setCancelTripError(err.message || 'Failed to cancel trip.');
        }
      } else {
        setCancelTripError(err instanceof Error ? err.message : 'Could not cancel trip.');
      }
    } finally {
      setCancelTripPending(false);
    }
  };

  const activeTripSummary = [...upcoming, ...past].find((t) => t.id === activeTrip?.id);
  const activeTripBookingHistory = activeTripSummary ? activeTripSummary.hasBookingHistory : false;

  useEffect(() => {
    if (deleteTarget && deleteTarget.kind === 'trip') {
      const found = [...upcoming, ...past].find((t) => t.id === deleteTarget.tripId);
      if (
        found &&
        (found.draftCount !== deleteTarget.draftCount || found.plannedCount !== deleteTarget.plannedCount)
      ) {
        setDeleteTarget({
          ...deleteTarget,
          draftCount: found.draftCount,
          plannedCount: found.plannedCount,
        });
      }
    }
  }, [upcoming, past, deleteTarget]);

  const handleConfirmDelete = async () => {
    if (!deleteTarget || deleteTarget.kind !== 'trip') return;
    setDeletePending(true);
    setDeleteError(undefined);

    // Find the trip summary to get its latest version and counts
    const foundTrip = [...upcoming, ...past].find((t) => t.id === deleteTarget.tripId);
    const version = foundTrip ? foundTrip.version : 0;
    const draftCount = foundTrip ? foundTrip.draftCount : deleteTarget.draftCount;
    const plannedCount = foundTrip ? foundTrip.plannedCount : deleteTarget.plannedCount;

    try {
      await tripsApi.deleteTrip(deleteTarget.tripId, {
        expectedVersion: version,
        expectedDraftCount: draftCount,
        expectedPlannedCount: plannedCount,
        confirmed: true,
      });
      setDeleteTarget(null);
      if (activeTrip?.id === deleteTarget.tripId) setActiveTrip(null);
      if (onRefreshProfile) await onRefreshProfile();
    } catch (err) {
      if (err instanceof IdentityApiError) {
        if (err.code === 'STALE_CONFIRMATION') {
          setDeleteError('The alternative counts on the server have changed since confirmation.');
          if (onRefreshProfile) void onRefreshProfile();
        } else if (err.code === 'CANNOT_DELETE_BOOKED_TRIP') {
          setDeleteError('Trips with booking history cannot be permanently deleted.');
        } else {
          setDeleteError(err.message || 'Deletion failed.');
        }
      } else {
        setDeleteError('Something went wrong. Please try again.');
      }
    } finally {
      setDeletePending(false);
    }
  };

  const hasTrips = upcoming.length > 0 || past.length > 0;

  const isExpired = activeTripSummary ? activeTripSummary.expiredAlternativeCount > 0 : false;
  const temporalStatus = activeTripSummary ? activeTripSummary.temporalStatus : 'UPCOMING';

  return (
    <>
    <nav className="card primary-navigation" aria-label="Primary navigation">
      <button type="button" className="text-button" aria-current={viewMode === 'home' ? 'page' : undefined} onClick={() => navigateTo('home')}>Home</button>
      <button type="button" className="text-button" aria-current={viewMode === 'profile' ? 'page' : undefined} onClick={() => navigateTo('profile')}>Profile</button>
      {activeTrip && <button type="button" className="text-button" aria-current={viewMode === 'workspace' ? 'page' : undefined} onClick={() => void handleOpenTrip(activeTrip.id)}>Trip: {activeTrip.label}</button>}
      <button type="button" className="text-button" disabled={logoutPending} onClick={() => {
        if (workspaceRef.current?.hasUnsavedChanges() && !window.confirm('Discard unsaved Trip edits and log out?')) return;
        void onLogout();
      }}>{logoutPending ? 'Logging out…' : 'Log out'}</button>
      {activeTrip && <span className={`navigation-save-status status-${saveState}`}>{tripDirty ? saveState === 'conflict' ? 'Trip has a save conflict' : saveState === 'error' ? 'Trip changes not saved' : 'Trip changes pending' : saveState === 'saved' ? 'Trip saved' : ''}</span>}
    </nav>
    {openingTripId && <p className="card" role="status">Opening Trip…</p>}
    {openError && <div className="card" role="alert"><p>{openError.message}</p><button type="button" onClick={() => void handleOpenTrip(openError.tripId)}>Retry opening Trip</button></div>}
    {activeTrip && <div hidden={viewMode !== 'workspace'}>
      <TripWorkspace
        ref={workspaceRef}
        key={activeTrip.id}
        initialTrip={activeTrip}
        initialEntryMode={entryContext?.mode ?? 'PLAN_TRIP'}
        initialAccommodationType={entryContext?.accommodationType}
        hasBookingHistory={activeTripBookingHistory}
        temporalStatus={temporalStatus}
        isExpired={isExpired}
        onSaveStatusChange={(status, dirty) => { setSaveState(status); setTripDirty(dirty); }}
        onBack={() => {
          setViewMode('profile');
          if (onRefreshProfile) void onRefreshProfile();
        }}
        onTripDeleted={() => {
          setViewMode('profile');
          setActiveTrip(null);
          setEntryContext(null);
          if (onRefreshProfile) void onRefreshProfile();
        }}
        onTripUpdated={(updatedTrip) => {
          setActiveTrip(updatedTrip);
          if (onRefreshProfile) void onRefreshProfile();
        }}
      />
    </div>}
    {viewMode === 'home' && <section className="card profile-card" aria-labelledby="home-heading">
      <p className="eyebrow wordmark">DeTour</p>
      <h1 id="home-heading" tabIndex={-1}>Home</h1>
      <p>Start a Trip from airfare, stay, or a full plan.</p>
      <EmptyProfileState onStartPlanTrip={() => startCreateTrip('PLAN_TRIP')} onStartAirfare={() => startCreateTrip('AIRFARE')} onStartStay={() => startCreateTrip('STAY')} />
      {activeTrip && <button type="button" className="primary" onClick={() => void handleOpenTrip(activeTrip.id)}>Return to {activeTrip.label}</button>}
    </section>}
    {viewMode === 'profile' && <section className="card profile-card" aria-labelledby="profile-heading">
      <div className="profile-heading">
        <div>
          <p className="eyebrow wordmark">DeTour</p>
          <h1 id="profile-heading" tabIndex={-1}>
            Your profile
          </h1>
        </div>
      </div>

      <dl className="identity">
        <dt>Email address</dt>
        <dd>{email}</dd>
      </dl>

      {/* Trips Section */}
      {hasTrips ? (
        <TripListSection
          upcoming={upcoming}
          past={past}
          onSelectTrip={(id) => void handleOpenTrip(id)}
          onDeleteTrip={handlePromptDeleteTrip}
          onCancelTrip={handlePromptCancelTrip}
          onPlanTrip={() => startCreateTrip('PLAN_TRIP')}
          onStartPlanTrip={() => startCreateTrip('PLAN_TRIP')}
          onStartAirfare={() => startCreateTrip('AIRFARE')}
          onStartStay={() => startCreateTrip('STAY')}
        />
      ) : (
        <EmptyProfileState onPlanTrip={() => startCreateTrip('PLAN_TRIP')} onStartPlanTrip={() => startCreateTrip('PLAN_TRIP')} onStartAirfare={() => startCreateTrip('AIRFARE')} onStartStay={() => startCreateTrip('STAY')} />
      )}

      {/* Change Password Section */}
      <section aria-labelledby="password-heading">
        <h2 id="password-heading">Change password</h2>
        <form onSubmit={handlePasswordSubmit} noValidate>
          <PasswordField
            id="current-password"
            label="Current password"
            value={currentPassword}
            onChange={setCurrentPassword}
            autoComplete="current-password"
            error={fieldErrors.currentPassword}
          />
          <PasswordField
            id="new-password"
            label="New password"
            value={newPassword}
            onChange={setNewPassword}
            autoComplete="new-password"
            error={fieldErrors.newPassword}
          />
          <button className="primary" type="submit" disabled={passwordPending}>
            {passwordPending ? 'Updating…' : 'Update password'}
          </button>
        </form>
      </section>

    </section>}
      {/* Plan Trip Creation Modal */}
      <TripCreateModal
        isOpen={createModalMode !== null}
        mode={createModalMode ?? 'PLAN_TRIP'}
        onClose={() => setCreateModalMode(null)}
        onSuccess={(createdTrip, mode, accommodationType) => {
          openingSequence.current += 1;
          setOpeningTripId(null);
          setOpenError(null);
          setCreateModalMode(null);
          setEntryContext({mode, accommodationType});
          setActiveTrip(createdTrip);
          setViewMode('workspace');
          if (onRefreshProfile) void onRefreshProfile();
        }}
      />

      {/* Delete Confirmation Modal */}
      <ConfirmDeleteModal
        isOpen={Boolean(deleteTarget)}
        target={deleteTarget}
        pending={deletePending}
        errorMessage={deleteError}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => void handleConfirmDelete()}
      />

      {/* Cancel Trip Confirmation Modal */}
      <CancelTripModal
        isOpen={Boolean(cancelTripTarget)}
        tripLabel={cancelTripTarget?.label ?? ''}
        hasActiveBooking={Boolean(cancelTripTarget && cancelTripTarget.bookedCount > 0)}
        pending={cancelTripPending}
        errorMessage={cancelTripError}
        onClose={() => {
          setCancelTripTarget(null);
          setCancelTripError(undefined);
        }}
        onConfirm={() => void handleConfirmCancelTrip()}
      />
    </>
  );
}
