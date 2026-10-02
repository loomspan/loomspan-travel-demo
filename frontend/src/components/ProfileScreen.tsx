import {FormEvent, useEffect, useRef, useState} from 'react';
import {PasswordField, passwordRangeError} from './PasswordField';
import type {FormFailure} from './AuthScreen';
import {ActionIcon} from './ActionIcon';
import {BrandMark} from './BrandMark';
import {TripListSection} from './TripListSection';
import {HomeScreen, type StartMode} from './HomeScreen';
import {TripStartForm, emptyTripStartDraft, type TripStartDraft} from './TripStartForm';
import {TripWorkspace, type TripWorkspaceHandle} from './TripWorkspace';
import {ConfirmDeleteModal, type DeleteTarget} from './ConfirmDeleteModal';
import {CancelTripModal} from './CancelTripModal';
import {tripsApi, type TripProfileSummary, type TripResponse, type AccommodationType} from '../api/tripsApi';
import {IdentityApiError} from '../api/identityApi';
import {currentScreen, rememberScreen} from '../screenHistory';

type ProfileScreenProps = {
  email: string;
  upcoming?: TripProfileSummary[];
  past?: TripProfileSummary[];
  onLogout: () => Promise<void>;
  logoutPending: boolean;
  onPasswordChange: (currentPassword: string, newPassword: string) => Promise<void>;
  onFailure: (failure: FormFailure) => void;
  onRefreshProfile?: () => Promise<boolean | void>;
  initialDestination?: 'home' | 'trips' | 'profile';
  initialStartMode?: StartMode;
  tripDraft?: TripStartDraft;
  onTripDraftChange?: (draft: TripStartDraft) => void;
  onAuthenticationRequired?: () => void;
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
  initialDestination = 'home',
  initialStartMode = 'PLAN_TRIP',
  tripDraft = emptyTripStartDraft,
  onTripDraftChange = () => {},
  onAuthenticationRequired = () => {},
}: ProfileScreenProps) {
  // Navigation mode
  const [viewMode, setViewMode] = useState<'home' | 'trips' | 'profile'>(initialDestination);
  const [showWorkspace, setShowWorkspace] = useState(false);
  const [startMode, setStartMode] = useState<StartMode>(initialStartMode);
  const [showStartForm, setShowStartForm] = useState(
    initialDestination === 'trips' &&
    (Boolean(currentScreen()?.planningMode) || initialStartMode !== 'PLAN_TRIP' || JSON.stringify(tripDraft) !== JSON.stringify(emptyTripStartDraft))
  );
  const [activeTrip, setActiveTrip] = useState<TripResponse | null>(null);
  const workspaceRef = useRef<TripWorkspaceHandle>(null);
  const openingSequence = useRef(0);
  const [openingTripId, setOpeningTripId] = useState<string | null>(null);
  const [openError, setOpenError] = useState<{tripId: string; message: string} | null>(null);
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error' | 'conflict'>('idle');
  const [tripDirty, setTripDirty] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const heading = viewMode === 'trips' && showWorkspace
        ? document.querySelector<HTMLElement>('#workspace-heading, #comparison-heading, #booking-review-heading, #confirmation-heading')
        : document.getElementById(viewMode === 'home' ? 'home-heading' : viewMode === 'trips' ? 'trips-heading' : 'profile-heading');
      heading?.focus();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [viewMode, showWorkspace, showStartForm, activeTrip?.id]);

  // Modals state
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

  const handleOpenTrip = async (tripId: string, recordHistory = true, guarded = false) => {
    if (!guarded && workspaceRef.current?.requestNavigation && activeTrip?.id !== tripId) {workspaceRef.current.requestNavigation(() => {void handleOpenTrip(tripId, recordHistory, true);}); return;}
    if (activeTrip?.id === tripId) {
      setViewMode('trips'); setShowWorkspace(true);
      if (recordHistory) workspaceRef.current?.rememberNavigation();
      await workspaceRef.current?.refreshIfClean();
      return;
    }
    if (!guarded && workspaceRef.current?.hasUnsavedChanges() && !window.confirm('Discard unsaved Trip edits and open another Trip?')) return;
    const sequence = ++openingSequence.current;
    setOpeningTripId(tripId);
    setOpenError(null);
    try {
      const trip = await tripsApi.getTrip(tripId);
      if (sequence !== openingSequence.current) return;
      setEntryContext(null);
      setActiveTrip(trip);
      setViewMode('trips'); setShowWorkspace(true);
      if (recordHistory) rememberScreen('trips', tripId);
    } catch (err) {
      if (sequence !== openingSequence.current) return;
      setOpenError({tripId, message: err instanceof IdentityApiError ? err.message : 'Could not open trip.'});
    } finally {
      if (sequence === openingSequence.current) setOpeningTripId(null);
    }
  };

  const startCreateTrip = (mode: 'PLAN_TRIP' | 'AIRFARE' | 'STAY', guarded = false) => {
    if (!guarded && workspaceRef.current?.requestNavigation) {workspaceRef.current.requestNavigation(() => startCreateTrip(mode, true)); return;}
    if (!guarded && workspaceRef.current?.hasUnsavedChanges() && !window.confirm('Discard unsaved Trip edits and start another Trip?')) return;
    setStartMode(mode);
    setShowStartForm(true);
    setViewMode('trips'); setShowWorkspace(false);
    rememberScreen('trips', undefined, false, {planningMode: mode});
  };

  const navigateTo = (destination: 'home' | 'trips' | 'profile', guarded = false) => {
    if (!guarded && workspaceRef.current?.requestNavigation) {workspaceRef.current.requestNavigation(() => navigateTo(destination, true)); return;}
    openingSequence.current += 1;
    setOpeningTripId(null);
    setOpenError(null);
    setViewMode(destination);
    rememberScreen(destination);
    if (destination === 'trips') { setShowWorkspace(false); setShowStartForm(false); if (onRefreshProfile) void onRefreshProfile(); }
  };

  useEffect(() => {
    const restoreScreen = () => {
      const previous = currentScreen();
      const restore = () => {
      if (previous?.tripId && previous.destination === 'trips') {
        void handleOpenTrip(previous.tripId, false, true);
        return;
      }
      openingSequence.current += 1;
      setOpeningTripId(null);
      setOpenError(null);
      setShowWorkspace(false);
      setShowStartForm(Boolean(previous?.planningMode));
      if (previous?.planningMode) setStartMode(previous.planningMode);
      const destination = previous?.destination;
      setViewMode(destination === 'trips' || destination === 'profile' ? destination : 'home');
      if (destination === 'trips' && onRefreshProfile) void onRefreshProfile();
      };
      if (workspaceRef.current?.requestNavigation && previous?.tripId !== activeTrip?.id) {
        const guarded = workspaceRef.current.hasUnsavedChanges();
        const transition = () => {restore(); if (guarded && previous) rememberScreen(previous.destination, previous.tripId, false, previous);};
        // Let workspace history listeners read the original destination before restoring our current entry.
        if (guarded) queueMicrotask(() => {workspaceRef.current?.rememberNavigation(); workspaceRef.current?.requestNavigation?.(transition);});
        else workspaceRef.current.requestNavigation(transition);
        return;
      }
      restore();
    };
    window.addEventListener('popstate', restoreScreen);
    return () => window.removeEventListener('popstate', restoreScreen);
  });

  const handlePromptDeleteTrip = (trip: TripProfileSummary, guarded = false) => {
    if (!guarded && activeTrip?.id === trip.id && workspaceRef.current?.requestNavigation) {workspaceRef.current.requestNavigation(() => handlePromptDeleteTrip(trip, true)); return;}
    if (!guarded && activeTrip?.id === trip.id && workspaceRef.current?.hasUnsavedChanges() && !window.confirm('Discard unsaved Trip edits and delete this Trip?')) return;
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

  const handleRenameTrip = async (trip: TripProfileSummary, name: string) => {
    if (activeTrip?.id === trip.id && workspaceRef.current?.hasUnsavedChanges()) {
      throw new Error('Save or discard your Working plan changes before renaming this trip.');
    }
    const updated = await tripsApi.renameTrip(trip.id, {expectedVersion: trip.version, name});
    if (activeTrip?.id === trip.id) setActiveTrip(updated);
    if (onRefreshProfile) {
      const refreshed = await onRefreshProfile();
      if (refreshed === false) throw new Error('Trip renamed, but the list could not refresh. Reload to see the latest name.');
    }
    if (activeTrip?.id === trip.id) await workspaceRef.current?.refreshIfClean();
  };

  const handleConfirmCancelTrip = async () => {
    if (!cancelTripTarget) return;
    setCancelTripPending(true);
    setCancelTripError(undefined);
    try {
      await tripsApi.cancelTrip(cancelTripTarget.id, {
        expectedVersion: cancelTripTarget.version,
      });
      if (onRefreshProfile) await onRefreshProfile();
      setCancelTripTarget(null);
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
      if (onRefreshProfile) await onRefreshProfile();
      if (activeTrip?.id === deleteTarget.tripId) setActiveTrip(null);
      setDeleteTarget(null);
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

  const isExpired = activeTripSummary ? activeTripSummary.expiredAlternativeCount > 0 : false;
  const temporalStatus = activeTripSummary ? activeTripSummary.temporalStatus : 'UPCOMING';

  return (
    <>
    <nav className="primary-navigation" aria-label="Primary navigation">
      <BrandMark />
      <button type="button" className="text-button" aria-current={viewMode === 'home' ? 'page' : undefined} onClick={() => navigateTo('home')}><ActionIcon name="home" />Home</button>
      <button type="button" className="text-button" aria-current={viewMode === 'trips' && showStartForm && !showWorkspace ? 'page' : undefined} onClick={() => startCreateTrip('PLAN_TRIP')}><ActionIcon name="trip" />Plan a Trip</button>
      <button type="button" className="text-button" aria-current={viewMode === 'trips' && (!showStartForm || showWorkspace) ? 'page' : undefined} onClick={() => navigateTo('trips')}><ActionIcon name="trip" />My Trips</button>
      <button type="button" className="text-button" aria-current={viewMode === 'profile' ? 'page' : undefined} onClick={() => navigateTo('profile')}><ActionIcon name="profile" />Profile</button>
      <button type="button" className="text-button" disabled={logoutPending} onClick={() => {
        if (workspaceRef.current?.requestNavigation) {workspaceRef.current.requestNavigation(() => {void onLogout();}); return;}
        if (workspaceRef.current?.hasUnsavedChanges() && !window.confirm('Discard unsaved Trip edits and log out?')) return;
        void onLogout();
      }}><ActionIcon name="logout" />{logoutPending ? 'Logging out…' : 'Log out'}</button>
      {activeTrip && <span className={`navigation-save-status status-${saveState}`}>{tripDirty ? saveState === 'conflict' ? 'Trip has a save conflict' : saveState === 'error' ? 'Trip changes not saved' : 'Trip changes pending' : saveState === 'saved' ? 'Trip saved' : ''}</span>}
    </nav>
    {openingTripId && <p className="card" role="status">Opening Trip…</p>}
    {openError && <div className="callout callout-danger" role="alert"><p>{openError.message}</p><button type="button" className="secondary" onClick={() => void handleOpenTrip(openError.tripId)}>Retry opening Trip</button></div>}
    {activeTrip && <div hidden={viewMode !== 'trips' || !showWorkspace}>
      <TripWorkspace
        ref={workspaceRef}
        key={activeTrip.id}
        initialTrip={activeTrip}
        existingTripNames={[...upcoming, ...past].map(item => item.name ?? item.label)}
        initialEntryMode={entryContext?.mode ?? 'PLAN_TRIP'}
        initialAccommodationType={entryContext?.accommodationType}
        hasBookingHistory={activeTripBookingHistory}
        temporalStatus={temporalStatus}
        isExpired={isExpired}
        onSaveStatusChange={(status, dirty) => { setSaveState(status); setTripDirty(dirty); }}
        onAuthenticationRequired={onAuthenticationRequired}
        onBack={() => {
          setShowWorkspace(false); setViewMode('trips');
          rememberScreen('trips');
          if (onRefreshProfile) void onRefreshProfile();
        }}
        onTripDeleted={() => {
          setShowWorkspace(false); setViewMode('trips');
          rememberScreen('trips', undefined, true);
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
    {viewMode === 'home' && <HomeScreen onStart={startCreateTrip} onReturn={activeTrip ? {label: activeTrip.label, open: () => void handleOpenTrip(activeTrip.id)} : undefined} />}
    {viewMode === 'trips' && !showWorkspace && (showStartForm ? <div className="trip-start-view">
      <button type="button" className="text-button back-link trip-list-back" onClick={() => navigateTo('trips')}>← Back to your trips</button>
      <TripStartForm draft={tripDraft} onChange={onTripDraftChange} mode={startMode} authenticated existingNames={[...upcoming, ...past].map(item => item.name ?? item.label)} onAuthenticationRequired={onAuthenticationRequired} onSuccess={(createdTrip, mode, accommodationType) => {
      openingSequence.current += 1;
      setEntryContext({mode, accommodationType});
      setActiveTrip(createdTrip);
      setShowWorkspace(true);
      setShowStartForm(false);
      rememberScreen('trips', createdTrip.id);
      if (onRefreshProfile) void onRefreshProfile();
    }} />
    </div> : <section className="card trips-page" aria-labelledby="trips-heading">
      <div className="trips-page-header">
        <div><p className="eyebrow wordmark">DeTour</p><h1 id="trips-heading" tabIndex={-1}>My Trips</h1></div>
        {(upcoming.length > 0 || past.length > 0) && <button type="button" className="primary" onClick={() => startCreateTrip('PLAN_TRIP')}>Plan a new trip</button>}
      </div>
      {upcoming.length === 0 && past.length === 0
        ? <div className="trips-empty-prompt"><h2>No trips yet</h2><p>Trips you save will appear here.</p><button type="button" className="primary" onClick={() => startCreateTrip('PLAN_TRIP')}>Plan a new trip</button></div>
        : <TripListSection upcoming={upcoming} past={past} onSelectTrip={(id) => void handleOpenTrip(id)} onDeleteTrip={handlePromptDeleteTrip} onCancelTrip={handlePromptCancelTrip} onRenameTrip={handleRenameTrip} hideHeading headingLevel={2} />}
    </section>)}
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
