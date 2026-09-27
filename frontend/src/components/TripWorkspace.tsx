import {useEffect, useRef, useState, useCallback, useImperativeHandle, forwardRef} from 'react';
import {
  tripsApi,
  type TripResponse,
  type AlternativeResponse,
  type RevisionSummaryResponse,
  type FlightCombinationResponse,
  type StayOptionResponse,
  type RentalOptionResponse,
  type AccommodationType,
  type BookingResponse,
} from '../api/tripsApi';
import {IdentityApiError} from '../api/identityApi';
import {AlternativeCard} from './AlternativeCard';
import {ItineraryComparisonView} from './ItineraryComparisonView';
import {BookingReviewView} from './BookingReviewView';
import {BookingConfirmationView} from './BookingConfirmationView';
import {RevisionSummaryBanner} from './RevisionSummaryBanner';
import {TripRevisionModal} from './TripRevisionModal';
import {ConfirmDeleteModal, type DeleteTarget} from './ConfirmDeleteModal';
import {CancelBookingModal} from './CancelBookingModal';
import {PostCancellationTriageModal} from './PostCancellationTriageModal';
import {CancelTripModal} from './CancelTripModal';
import {BookingHistorySection} from './BookingHistorySection';
import {
  ItinerarySummaryTally,
  formatCents,
  computeAirfareTotalCents,
  computeStayTotalCents,
  computeRentalTotalCents,
} from './ItinerarySummaryTally';
import {AirfareSlot} from './AirfareSlot';
import {StaySlot} from './StaySlot';
import {RentalSlot} from './RentalSlot';
import {ConfirmRemoveModal} from './ConfirmRemoveModal';

type TripWorkspaceProps = {
  initialTrip: TripResponse;
  initialActiveBooking?: BookingResponse | null;
  initialEntryMode?: 'PLAN_TRIP' | 'AIRFARE' | 'STAY';
  initialAccommodationType?: AccommodationType;
  hasBookingHistory?: boolean;
  temporalStatus?: string;
  isExpired?: boolean;
  onBack: () => void;
  onTripDeleted: () => void;
  onTripUpdated?: (trip: TripResponse) => void;
  onLogout?: () => Promise<void>;
  logoutPending?: boolean;
  onSaveStatusChange?: (status: 'idle' | 'saving' | 'saved' | 'error' | 'conflict', dirty: boolean) => void;
  onAuthenticationRequired?: () => void;
};

export type TripWorkspaceHandle = {
  refreshIfClean: () => Promise<void>;
  hasUnsavedChanges: () => boolean;
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

export const TripWorkspace = forwardRef<TripWorkspaceHandle, TripWorkspaceProps>(function TripWorkspace({
  initialTrip,
  initialActiveBooking,
  initialEntryMode = 'PLAN_TRIP',
  initialAccommodationType,
  hasBookingHistory = false,
  temporalStatus = 'UPCOMING',
  isExpired = false,
  onBack,
  onTripDeleted,
  onTripUpdated,
  onLogout,
  logoutPending = false,
  onSaveStatusChange,
  onAuthenticationRequired,
}: TripWorkspaceProps, ref) {
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
  const [refreshFailed, setRefreshFailed] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [revisionSummary, setRevisionSummary] = useState<RevisionSummaryResponse | null>(
    initialTrip.revisionSummary ?? null
  );

  const [isRevisionModalOpen, setIsRevisionModalOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [deletePending, setDeletePending] = useState(false);
  const [deleteError, setDeleteError] = useState<string | undefined>();

  const activeDraft = trip.workingPlan ?? trip.drafts?.[0] ?? null;
  const savedOptions: AlternativeResponse[] = (trip.savedOptions ?? trip.planned ?? []).map(option => ({
    ...option, lifecycle: 'PLANNED', version: option.version ?? 0,
  }));

  const [airfareMode, setAirfareMode] = useState<'empty' | 'searching' | 'selected'>(() => {
    if (initialTrip.drafts?.[0]?.selections?.airfare) return 'selected';
    if (initialEntryMode === 'AIRFARE') return 'searching';
    return 'empty';
  });

  const [stayMode, setStayMode] = useState<'empty' | 'searching' | 'selected'>(() => {
    if (initialTrip.drafts?.[0]?.selections?.stay) return 'selected';
    if (initialEntryMode === 'STAY') return 'searching';
    return 'empty';
  });

  const [rentalMode, setRentalMode] = useState<'hidden' | 'searching' | 'selected'>(() => {
    if (initialTrip.drafts?.[0]?.selections?.rental) return 'selected';
    return 'hidden';
  });

  const [stayAccommodationType, setStayAccommodationType] = useState<AccommodationType>(
    initialAccommodationType ?? 'HOTEL'
  );

  const [removeTarget, setRemoveTarget] = useState<{
    component: 'AIRFARE' | 'STAY' | 'RENTAL';
    title: string;
    formattedPrice: string;
  } | null>(null);
  const [removeError, setRemoveError] = useState<string | undefined>();

  const [componentMutationPending, setComponentMutationPending] = useState(false);
  const [workingMutationFailed, setWorkingMutationFailed] = useState(false);

  const [optionName, setOptionName] = useState('');
  const [editingOptionId, setEditingOptionId] = useState<string | null>(null);
  const [updateConfirmationOpen, setUpdateConfirmationOpen] = useState(false);
  const [renameOptionId, setRenameOptionId] = useState<string | null>(null);
  const [renameName, setRenameName] = useState('');
  const [loadTargetId, setLoadTargetId] = useState<string | null>(null);
  const [currentPreservedForLoad, setCurrentPreservedForLoad] = useState(false);
  const [optionActionPending, setOptionActionPending] = useState(false);
  const [optionFailure, setOptionFailure] = useState<{action: 'save' | 'update' | 'rename' | 'load'; message: string} | null>(null);
  const optionDialogRef = useRef<HTMLDivElement>(null);
  const optionDialogReturnFocusRef = useRef<HTMLElement | null>(null);
  const activeOptionDialog = updateConfirmationOpen || Boolean(renameOptionId) || Boolean(loadTargetId);
  useEffect(() => {
    if (!activeOptionDialog) return;
    optionDialogReturnFocusRef.current = document.activeElement as HTMLElement;
    const dialog = optionDialogRef.current;
    (dialog?.querySelector<HTMLElement>('input:not(:disabled)') ?? dialog?.querySelector<HTMLElement>('button:not(:disabled)'))?.focus();
    return () => {
      const previous = optionDialogReturnFocusRef.current;
      if (previous && document.body.contains(previous)) previous.focus();
      else document.getElementById('workspace-heading')?.focus();
      optionDialogReturnFocusRef.current = null;
    };
  }, [activeOptionDialog]);
  useEffect(() => {
    if (!activeOptionDialog) return;
    const onKeyDown = (event: KeyboardEvent) => {
      const dialog = optionDialogRef.current;
      if (!dialog) return;
      if (event.key === 'Escape' && !optionActionPending) {
        event.preventDefault();
        setUpdateConfirmationOpen(false);
        setRenameOptionId(null);
        setLoadTargetId(null);
        setCurrentPreservedForLoad(false);
      }
      if (event.key !== 'Tab') return;
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled)'));
      if (!focusable.length) { event.preventDefault(); return; }
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (!focusable.includes(document.activeElement as HTMLElement)) {
        event.preventDefault();
        (event.shiftKey ? last : first).focus();
      } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [activeOptionDialog, optionActionPending]);

  const [selectedForCompareIds, setSelectedForCompareIds] = useState<string[]>([]);
  const [compareNotification, setCompareNotification] = useState<string | undefined>();
  const isTripCanceled = trip.status === 'CANCELED';

  const [hasEverBooked, setHasEverBooked] = useState<boolean>(
    hasBookingHistory || Boolean(initialActiveBooking) || trip.status === 'CANCELED'
  );
  const isInitialTrip = trip.id === initialTrip.id;
  const effectiveHasBookingHistory = (isInitialTrip && Boolean(hasBookingHistory)) || hasEverBooked;

  const [isCancelBookingModalOpen, setIsCancelBookingModalOpen] = useState(false);
  const [cancelBookingPending, setCancelBookingPending] = useState(false);
  const [cancelBookingError, setCancelBookingError] = useState<string | undefined>();

  const [isTriageModalOpen, setIsTriageModalOpen] = useState(false);
  const [triagePending, setTriagePending] = useState(false);
  const [triageAction, setTriageAction] = useState<'use-alternative' | null>(null);
  const [triageError, setTriageError] = useState<string | undefined>();

  const [isCancelTripModalOpen, setIsCancelTripModalOpen] = useState(false);
  const [cancelTripPending, setCancelTripPending] = useState(false);
  const [cancelTripError, setCancelTripError] = useState<string | undefined>();

  const [historyRefreshKey, setHistoryRefreshKey] = useState(0);

  const [activeBooking, setActiveBooking] = useState<BookingResponse | null>(
    initialActiveBooking ?? null
  );
  const bookedOptionStartDate = activeBooking
    ? trip.planned.find(option => option.id === activeBooking.plannedItineraryId)?.startDate
    : undefined;
  const pacificDateParts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Los_Angeles', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const pacificToday = `${pacificDateParts.find(part => part.type === 'year')?.value}-${pacificDateParts.find(part => part.type === 'month')?.value}-${pacificDateParts.find(part => part.type === 'day')?.value}`;
  const bookedOptionExpired = bookedOptionStartDate
    ? bookedOptionStartDate <= pacificToday : isExpired || temporalStatus === 'PAST';
  const [workspaceView, setWorkspaceView] = useState<
    'workspace' | 'compare' | 'booking-review' | 'booking-confirmation'
  >('workspace');
  const [reviewAlternativeId, setReviewAlternativeId] = useState<string | null>(null);
  const [reviewReturnView, setReviewReturnView] = useState<'workspace' | 'compare'>('workspace');

  useEffect(() => {
    const headingId = {
      workspace: 'workspace-heading', compare: 'comparison-heading',
      'booking-review': 'booking-review-heading', 'booking-confirmation': 'confirmation-heading',
    }[workspaceView];
    const timer = window.setTimeout(() => document.getElementById(headingId)?.focus(), 0);
    return () => window.clearTimeout(timer);
  }, [workspaceView]);

  const scrollToIssue = (element: HTMLElement) => {
    if (typeof element.scrollIntoView === 'function') {
      const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
      element.scrollIntoView({behavior: reduceMotion ? 'instant' : 'smooth', block: 'center'});
    }
    element.focus();
  };

  useEffect(() => {
    if (!hasBookingHistory || initialActiveBooking !== undefined) {
      return;
    }
    let cancelled = false;
    tripsApi
      .getActiveBooking(trip.id)
      .then((booking) => {
        if (!cancelled) setActiveBooking(booking);
      })
      .catch(() => {
        if (!cancelled) setActiveBooking(null);
      });
    return () => {
      cancelled = true;
    };
  }, [trip.id, hasBookingHistory, initialActiveBooking]);

  useEffect(() => {
    if (!trip.savedOptions && !trip.planned) {
      setSelectedForCompareIds([]);
      return;
    }
    const validIds = new Set(
      (trip.savedOptions ?? trip.planned)
        .map((a) => a.id)
    );
    setSelectedForCompareIds((prev) => prev.filter((id) => validIds.has(id)));
  }, [trip.savedOptions, trip.planned]);

  useEffect(() => {
    if (activeDraft?.selections?.airfare) {
      setAirfareMode('selected');
    } else {
      setAirfareMode((prev) => (prev === 'selected' ? 'empty' : prev));
    }
  }, [activeDraft?.selections?.airfare]);

  useEffect(() => {
    if (activeDraft?.selections?.stay) {
      setStayMode('selected');
    } else {
      setStayMode((prev) => (prev === 'selected' ? 'empty' : prev));
    }
  }, [activeDraft?.selections?.stay]);

  useEffect(() => {
    if (activeDraft?.selections?.rental) {
      setRentalMode('selected');
    } else {
      setRentalMode((prev) => (prev === 'selected' ? 'hidden' : prev));
    }
  }, [activeDraft?.selections?.rental]);

  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isInitialMount = useRef(true);
  const isSavingRef = useRef(false);
  const pendingSaveRef = useRef(false);

  const applyTripState = useCallback((t: TripResponse) => {
    setTrip((prev) => {
      if (t.id !== prev.id) {
        setHasEverBooked(t.status === 'CANCELED');
        setActiveBooking(null);
        setSelectedForCompareIds([]);
      }
      return t;
    });
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

  // A keyed workspace receives its opening Trip once. Parent profile refreshes must
  // never replace local edits or a newer mutation response.

  const hasPlanned = trip.planned && trip.planned.length > 0;
  const plannedAlternatives = savedOptions;

  // Validation helper
  const validateInputs = useCallback(() => {
    const errors: Record<string, string> = {};

    const dateErr = validateDates(startDate, endDate);
    if (dateErr) errors.dates = dateErr;

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

  const hasUnsavedChanges = useCallback(() => {
    const {ages, budgetCents} = validateInputs();
    return isSavingRef.current || componentMutationPending || workingMutationFailed || isDirty(ages, budgetCents) || autosaveStatus === 'saving' || autosaveStatus === 'conflict';
  }, [validateInputs, isDirty, autosaveStatus, componentMutationPending, workingMutationFailed]);
  const latestRefreshState = useRef({hasUnsavedChanges, version: trip.version});
  latestRefreshState.current = {hasUnsavedChanges, version: trip.version};

  useEffect(() => {
    onSaveStatusChange?.(autosaveStatus, hasUnsavedChanges());
  }, [autosaveStatus, hasUnsavedChanges, onSaveStatusChange]);

  useImperativeHandle(ref, () => ({
    hasUnsavedChanges,
    refreshIfClean: async () => {
      if (hasUnsavedChanges()) return;
      const requestedVersion = trip.version;
      try {
        const freshTrip = await tripsApi.getTrip(trip.id);
        if (!latestRefreshState.current.hasUnsavedChanges() && freshTrip.version >= latestRefreshState.current.version) {
          applyTripState(freshTrip);
          setRefreshFailed(false);
          setAutosaveStatus((current) => current === 'saved' ? 'saved' : 'idle');
          setAutosaveMessage((current) => current === 'All changes saved.' ? current : undefined);
        }
      } catch {
        if (latestRefreshState.current.hasUnsavedChanges() || latestRefreshState.current.version !== requestedVersion) return;
        setRefreshFailed(true);
        setAutosaveStatus('error');
        setAutosaveMessage('Could not refresh trip. Retry when you return.');
      }
    },
  }), [hasUnsavedChanges, trip.id, trip.version, applyTripState]);

  // Autosave execution with serialization
  const executeAutosave = useCallback(async () => {
    if (isSavingRef.current || isTripCanceled || autosaveStatus === 'conflict') {
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
      // An edit made during the previous request may have been reverted before
      // that request completed. In that case the returned Trip is already current.
      if (autosaveStatus === 'saving') {
        pendingSaveRef.current = false;
        setAutosaveStatus('saved');
        setAutosaveMessage('All changes saved.');
      }
      return;
    }

    setFieldErrors({});
    setAutosaveStatus('saving');
    setAutosaveMessage('Saving changes…');
    isSavingRef.current = true;
    pendingSaveRef.current = false;
    let saved = false;

    try {
      const count = parseInt(travelerCountInput, 10);
      const updatedTrip = await tripsApi.replaceSharedDetails(trip.id, {
        expectedVersion: trip.version,
        expectedDraftVersion: activeDraft?.version,
        destinationKey,
        startDate,
        endDate,
        travelerCount: count,
        travelerAges: ages,
        budgetCents,
      });

      setTrip(updatedTrip);
      saved = true;
      if (updatedTrip.revisionSummary) {
        setRevisionSummary(updatedTrip.revisionSummary);
      }
      setAutosaveStatus(pendingSaveRef.current ? 'saving' : 'saved');
      setAutosaveMessage(pendingSaveRef.current ? 'Saving latest changes…' : 'All changes saved.');
    } catch (err) {
      if (err instanceof IdentityApiError) {
        if (err.code === 'UNAUTHENTICATED') {
          setAutosaveStatus('error');
          setAutosaveMessage('Your session has ended. Your edited details have not been saved. Log in, then retry save.');
          onAuthenticationRequired?.();
        } else if (err.code === 'VERSION_CONFLICT') {
          setAutosaveStatus('conflict');
          setAutosaveMessage('The Trip has changed on the server. Reload before saving.');
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
      if (pendingSaveRef.current && saved) {
        pendingSaveRef.current = false;
        if (debounceTimer.current) clearTimeout(debounceTimer.current);
        debounceTimer.current = setTimeout(() => {
          void executeAutosaveRef.current();
        }, 300);
      }
    }
  }, [trip.id, trip.version, activeDraft?.version, destinationKey, startDate, endDate, travelerCountInput, validateInputs, isDirty, autosaveStatus, isTripCanceled, onAuthenticationRequired]);

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

    if (autosaveStatus === 'error' || autosaveStatus === 'conflict') return;
    if (isSavingRef.current) {
      pendingSaveRef.current = true;
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
      setWorkingMutationFailed(false);
      setRefreshFailed(false);
      setAutosaveStatus('idle');
      setAutosaveMessage(undefined);
      setFieldErrors({});
      setEditingOptionId(null);
      setUpdateConfirmationOpen(false);
      setRenameOptionId(null);
      setLoadTargetId(null);
      setCurrentPreservedForLoad(false);
      setOptionFailure(null);
      if (deleteTarget && deleteTarget.kind === 'trip') {
        setDeleteTarget({
          ...deleteTarget,
          draftCount: freshTrip.drafts ? freshTrip.drafts.length : 0,
          plannedCount: freshTrip.planned ? freshTrip.planned.length : 0,
        });
      }
    } catch {
      setRefreshFailed(true);
      setAutosaveStatus('error');
      setAutosaveMessage('Could not reload trip data.');
    }
  };

  // Component Selection handlers
  const handleSelectAirfare = async (option: FlightCombinationResponse) => {
    if (!activeDraft) return;
    setComponentMutationPending(true);
    setAutosaveStatus('saving');
    setAutosaveMessage('Saving flight selection…');
    try {
      const updatedTrip = await tripsApi.selectAirfare(trip.id, activeDraft.id, {
        expectedVersion: trip.version,
        expectedDraftVersion: activeDraft.version,
        outboundFlightInstanceId: option.outbound.flightInstanceId,
        returnFlightInstanceId: option.returnFlight.flightInstanceId,
      });
      applyTripState(updatedTrip);
      setWorkingMutationFailed(false);
      setAirfareMode('selected');
      setAutosaveStatus('saved');
      setAutosaveMessage('Flight saved to Working plan.');
    } catch (err) {
      setWorkingMutationFailed(true);
      if (err instanceof IdentityApiError && err.code === 'VERSION_CONFLICT') {
        setAutosaveStatus('conflict');
        setAutosaveMessage('The Trip has changed on the server. Reload before saving.');
      } else {
        setAutosaveStatus('error');
        setAutosaveMessage(err instanceof Error ? err.message : 'Could not save flight.');
      }
    } finally {
      setComponentMutationPending(false);
    }
  };

  const handleSelectStay = async (option: StayOptionResponse) => {
    if (!activeDraft) return;
    setComponentMutationPending(true);
    setAutosaveStatus('saving');
    setAutosaveMessage('Saving stay selection…');
    try {
      const updatedTrip = await tripsApi.selectStay(trip.id, activeDraft.id, {
        expectedVersion: trip.version,
        expectedDraftVersion: activeDraft.version,
        accommodationUnitId: option.accommodationUnitId,
        unitCount: option.pricing.requiredRooms,
      });
      applyTripState(updatedTrip);
      setWorkingMutationFailed(false);
      setStayMode('selected');
      setAutosaveStatus('saved');
      setAutosaveMessage('Stay saved to Working plan.');
    } catch (err) {
      setWorkingMutationFailed(true);
      if (err instanceof IdentityApiError && err.code === 'VERSION_CONFLICT') {
        setAutosaveStatus('conflict');
        setAutosaveMessage('The Trip has changed on the server. Reload before saving.');
      } else {
        setAutosaveStatus('error');
        setAutosaveMessage(err instanceof Error ? err.message : 'Could not save stay.');
      }
    } finally {
      setComponentMutationPending(false);
    }
  };

  const handleSelectRental = async (
    option: RentalOptionResponse,
    pickupAtIso: string,
    returnAtIso: string
  ) => {
    if (!activeDraft) return;
    setComponentMutationPending(true);
    setAutosaveStatus('saving');
    setAutosaveMessage('Saving rental car selection…');
    try {
      const updatedTrip = await tripsApi.selectRental(trip.id, activeDraft.id, {
        expectedVersion: trip.version,
        expectedDraftVersion: activeDraft.version,
        rentalUnitId: option.rentalUnitId,
        pickupAt: pickupAtIso,
        returnAt: returnAtIso,
      });
      applyTripState(updatedTrip);
      setWorkingMutationFailed(false);
      setRentalMode('selected');
      setAutosaveStatus('saved');
      setAutosaveMessage('Rental car saved to Working plan.');
    } catch (err) {
      setWorkingMutationFailed(true);
      if (err instanceof IdentityApiError && err.code === 'VERSION_CONFLICT') {
        setAutosaveStatus('conflict');
        setAutosaveMessage('The Trip has changed on the server. Reload before saving.');
      } else {
        setAutosaveStatus('error');
        setAutosaveMessage(err instanceof Error ? err.message : 'Could not save car.');
      }
    } finally {
      setComponentMutationPending(false);
    }
  };

  const promptRemoveAirfare = () => {
    if (!activeDraft?.selections.airfare) return;
    const priceCents = computeAirfareTotalCents(activeDraft.selections, trip.travelerCount);
    setRemoveError(undefined);
    setRemoveTarget({
      component: 'AIRFARE',
      title: 'Airfare',
      formattedPrice: formatCents(priceCents),
    });
  };

  const promptRemoveStay = () => {
    if (!activeDraft?.selections.stay) return;
    const priceCents = computeStayTotalCents(activeDraft.selections);
    setRemoveError(undefined);
    setRemoveTarget({
      component: 'STAY',
      title: 'Stay',
      formattedPrice: formatCents(priceCents),
    });
  };

  const promptRemoveRental = () => {
    if (!activeDraft?.selections.rental) return;
    const priceCents = computeRentalTotalCents(activeDraft.selections);
    setRemoveError(undefined);
    setRemoveTarget({
      component: 'RENTAL',
      title: 'Rental Car',
      formattedPrice: formatCents(priceCents),
    });
  };

  const handleConfirmRemove = async () => {
    if (!removeTarget || !activeDraft) return;
    setComponentMutationPending(true);
    setRemoveError(undefined);
    setAutosaveStatus('saving');
    setAutosaveMessage(`Removing ${removeTarget.title.toLowerCase()}…`);
    try {
      let updatedTrip: TripResponse;
      if (removeTarget.component === 'AIRFARE') {
        updatedTrip = await tripsApi.removeAirfare(trip.id, activeDraft.id, {
          expectedVersion: trip.version,
          expectedDraftVersion: activeDraft.version,
        });
        setAirfareMode('empty');
      } else if (removeTarget.component === 'STAY') {
        updatedTrip = await tripsApi.removeStay(trip.id, activeDraft.id, {
          expectedVersion: trip.version,
          expectedDraftVersion: activeDraft.version,
        });
        setStayMode('empty');
      } else {
        updatedTrip = await tripsApi.removeRental(trip.id, activeDraft.id, {
          expectedVersion: trip.version,
          expectedDraftVersion: activeDraft.version,
        });
        setRentalMode('hidden');
      }
      applyTripState(updatedTrip);
      setWorkingMutationFailed(false);
      const title = removeTarget.title;
      setRemoveTarget(null);
      setAutosaveStatus('saved');
      setAutosaveMessage(`${title} removed from Working plan.`);
    } catch (err) {
      setWorkingMutationFailed(true);
      if (err instanceof IdentityApiError && err.code === 'VERSION_CONFLICT') {
        setRemoveTarget(null);
        setRemoveError(undefined);
        setAutosaveStatus('conflict');
        setAutosaveMessage('The Trip has changed on the server. Reload before saving.');
      } else {
        const failure = err instanceof Error ? err.message : 'Could not remove component.';
        setRemoveError(failure);
        setAutosaveStatus('error');
        setAutosaveMessage(failure);
      }
    } finally {
      setComponentMutationPending(false);
    }
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
      if (deleteTarget.kind === 'trip') {
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
          setDeleteError('The Trip contents on the server have changed. Reloading latest data…');
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

  const ensureSavedWorking = () => {
    if (hasUnsavedChanges() || !activeDraft) {
      setAutosaveStatus('error');
      setAutosaveMessage('Save your Working plan before managing options. Retry the save if it failed.');
      return false;
    }
    return true;
  };

  const handleSaveNamedOption = async () => {
    if (optionActionPending || !ensureSavedWorking()) return;
    if (!activeDraft?.selections.airfare && !activeDraft?.selections.stay && !activeDraft?.selections.rental) {
      setAutosaveStatus('error');
      setAutosaveMessage('Choose a flight, stay, or rental car before saving a new option. Your incomplete Working plan remains saved.');
      return;
    }
    if (!optionName.trim()) {
      setAutosaveStatus('error');
      setAutosaveMessage('Enter a name for the Saved option.');
      return;
    }
    setOptionActionPending(true);
    setAutosaveStatus('saving');
    setAutosaveMessage('Saving option…');
    try {
      const updated = await tripsApi.saveOption(trip.id, {
        expectedVersion: trip.version, expectedDraftVersion: activeDraft!.version, name: optionName.trim(),
      });
      applyTripState(updated);
      onTripUpdated?.(updated);
      setOptionName('');
      setEditingOptionId(null);
      setAutosaveStatus('saved');
      setAutosaveMessage('Saved as a new option.');
      if (optionFailure?.action === 'save') setOptionFailure(null);
    } catch (error) {
      if (error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') onAuthenticationRequired?.();
      const conflict = error instanceof IdentityApiError && error.code === 'VERSION_CONFLICT';
      setAutosaveStatus(conflict ? 'conflict' : 'error');
      setAutosaveMessage(error instanceof IdentityApiError ? error.message : 'Could not save option.');
      setOptionFailure({action: 'save', message: error instanceof IdentityApiError ? error.message : 'Could not save option.'});
    } finally {
      setOptionActionPending(false);
    }
  };

  const handleUpdateNamedOption = async () => {
    if (optionActionPending || !editingOptionId || !ensureSavedWorking()) return;
    if (!activeDraft?.selections.airfare && !activeDraft?.selections.stay && !activeDraft?.selections.rental) {
      setAutosaveStatus('error');
      setAutosaveMessage('Choose a component before updating a Saved option. Your incomplete Working plan remains available.');
      return;
    }
    const option = (trip.savedOptions ?? trip.planned).find(item => item.id === editingOptionId);
    if (!option) return;
    if (option.booked || activeBooking?.plannedItineraryId === option.id || trip.booking?.plannedItineraryId === option.id) {
      setAutosaveStatus('error');
      setAutosaveMessage('Booked options cannot be updated. Save your Working plan as a new option.');
      return;
    }
    setOptionActionPending(true);
    setAutosaveStatus('saving');
    setAutosaveMessage('Updating option…');
    try {
      const updated = await tripsApi.updateOption(trip.id, option.id, {
        expectedVersion: trip.version, expectedDraftVersion: activeDraft!.version,
        expectedOptionVersion: option.version ?? 0, name: option.name || 'Saved option',
      });
      applyTripState(updated);
      onTripUpdated?.(updated);
      setAutosaveStatus('saved');
      setAutosaveMessage('Option updated.');
      if (optionFailure?.action === 'update') setOptionFailure(null);
      setUpdateConfirmationOpen(false);
    } catch (error) {
      if (error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') onAuthenticationRequired?.();
      const conflict = error instanceof IdentityApiError && error.code === 'VERSION_CONFLICT';
      setAutosaveStatus(conflict ? 'conflict' : 'error');
      setAutosaveMessage(error instanceof IdentityApiError ? error.message : 'Could not update option.');
      setOptionFailure({action: 'update', message: error instanceof IdentityApiError ? error.message : 'Could not update option.'});
      if (conflict) setUpdateConfirmationOpen(false);
    } finally {
      setOptionActionPending(false);
    }
  };

  const handleRenameOption = async () => {
    if (optionActionPending || !renameOptionId || !ensureSavedWorking()) return;
    const option = savedOptions.find(item => item.id === renameOptionId);
    if (!option || !renameName.trim() || option.booked || activeBooking?.plannedItineraryId === renameOptionId || trip.booking?.plannedItineraryId === renameOptionId) return;
    setOptionActionPending(true);
    try {
      const updated = await tripsApi.renameOption(trip.id, option.id, {
        expectedVersion: trip.version, expectedOptionVersion: option.version ?? 0, name: renameName.trim(),
      });
      applyTripState(updated);
      onTripUpdated?.(updated);
      setRenameOptionId(null);
      setAutosaveStatus('saved');
      setAutosaveMessage('Option renamed.');
      if (optionFailure?.action === 'rename') setOptionFailure(null);
    } catch (error) {
      if (error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') onAuthenticationRequired?.();
      const conflict = error instanceof IdentityApiError && error.code === 'VERSION_CONFLICT';
      setAutosaveStatus(conflict ? 'conflict' : 'error');
      setAutosaveMessage(error instanceof IdentityApiError ? error.message : 'Could not rename option.');
      setOptionFailure({action: 'rename', message: error instanceof IdentityApiError ? error.message : 'Could not rename option.'});
      if (conflict) setRenameOptionId(null);
    } finally {
      setOptionActionPending(false);
    }
  };

  const handleLoadNamedOption = async (keepCurrent: boolean) => {
    if (optionActionPending || !loadTargetId || !ensureSavedWorking()) return;
    const option = (trip.savedOptions ?? trip.planned).find(item => item.id === loadTargetId);
    if (!option) return;
    if (keepCurrent && !currentPreservedForLoad && !Boolean(activeDraft?.selections.airfare || activeDraft?.selections.stay || activeDraft?.selections.rental)) {
      setAutosaveStatus('error');
      setAutosaveMessage('Add a selection before keeping the current Working plan as an option, or replace it without saving a copy.');
      return;
    }
    if (keepCurrent && !currentPreservedForLoad && !optionName.trim()) {
      setAutosaveStatus('error');
      setAutosaveMessage('Name your current Working plan before keeping it as an option.');
      return;
    }
    setOptionActionPending(true);
    setAutosaveStatus('saving');
    setAutosaveMessage('Opening option in Working plan…');
    try {
      let current = trip;
      if (keepCurrent && !currentPreservedForLoad) {
        current = await tripsApi.saveOption(trip.id, {
          expectedVersion: trip.version, expectedDraftVersion: activeDraft!.version, name: optionName.trim(),
        });
        applyTripState(current);
        onTripUpdated?.(current);
        setCurrentPreservedForLoad(true);
      }
      const working = current.workingPlan ?? current.drafts[0];
      const updated = await tripsApi.loadOption(trip.id, option.id, {
        expectedVersion: current.version, expectedDraftVersion: working.version,
        expectedOptionVersion: option.version ?? 0, replaceWorking: true,
      });
      applyTripState(updated);
      onTripUpdated?.(updated);
      setEditingOptionId(option.id);
      setOptionName('');
      setLoadTargetId(null);
      setCurrentPreservedForLoad(false);
      setAutosaveStatus('saved');
      setAutosaveMessage('Option opened in the Working plan. The Saved option is unchanged.');
      if (optionFailure?.action === 'load') setOptionFailure(null);
    } catch (error) {
      if (error instanceof IdentityApiError && error.code === 'UNAUTHENTICATED') onAuthenticationRequired?.();
      const conflict = error instanceof IdentityApiError && error.code === 'VERSION_CONFLICT';
      setAutosaveStatus(conflict ? 'conflict' : 'error');
      setAutosaveMessage(error instanceof IdentityApiError ? error.message : 'Could not open option.');
      setOptionFailure({action: 'load', message: error instanceof IdentityApiError ? error.message : 'Could not open option.'});
      if (conflict) setLoadTargetId(null);
    } finally {
      setOptionActionPending(false);
    }
  };

  const handleToggleCompare = (id: string, checked: boolean) => {
    if (checked) {
      if (selectedForCompareIds.length >= 3) {
        setCompareNotification(
          'You can compare at most 3 itineraries at once. Deselect one before adding another.'
        );
        return;
      }
      setSelectedForCompareIds((prev) => [...prev, id]);
      setCompareNotification(undefined);
    } else {
      setSelectedForCompareIds((prev) => prev.filter((item) => item !== id));
      setCompareNotification(undefined);
    }
  };

  const handleSelectForBookingReview = (alternativeId: string, returnTarget: 'workspace' | 'compare') => {
    if (activeBooking) return;
    setReviewAlternativeId(alternativeId);
    setReviewReturnView(returnTarget);
    setWorkspaceView('booking-review');
  };

  const handleBookingSuccess = async (booking: BookingResponse) => {
    setActiveBooking(booking);
    setHasEverBooked(true);
    setHistoryRefreshKey((prev) => prev + 1);
    setWorkspaceView('booking-confirmation');
    try {
      const refreshedTrip = await tripsApi.getTrip(trip.id);
      applyTripState(refreshedTrip);
      if (onTripUpdated) onTripUpdated(refreshedTrip);
    } catch {
      setTrip((prev) => ({...prev, version: prev.version + 1}));
    }
  };

  const handlePromptCancelBooking = () => {
    setCancelBookingError(undefined);
    setIsCancelBookingModalOpen(true);
  };

  const handleConfirmCancelBooking = async () => {
    if (!activeBooking) return;
    setCancelBookingPending(true);
    setCancelBookingError(undefined);
    try {
      const updatedTrip = await tripsApi.cancelBooking(trip.id, activeBooking.id, {
        expectedVersion: trip.version,
      });
      applyTripState(updatedTrip);
      setActiveBooking(null);
      setHasEverBooked(true);
      setHistoryRefreshKey((prev) => prev + 1);
      setIsCancelBookingModalOpen(false);
      setIsTriageModalOpen(true);
      setAutosaveStatus('saved');
      setAutosaveMessage('Reservation canceled fee-free. Inventory restored.');
      if (onTripUpdated) onTripUpdated(updatedTrip);
    } catch (err) {
      if (err instanceof IdentityApiError) {
        if (err.code === 'VERSION_CONFLICT') {
          setCancelBookingError('The Trip has changed on the server. Please reload and try again.');
        } else {
          setCancelBookingError(err.message || 'Failed to cancel reservation.');
        }
      } else {
        setCancelBookingError(err instanceof Error ? err.message : 'Could not cancel reservation.');
      }
    } finally {
      setCancelBookingPending(false);
    }
  };

  const handleTriageUseAlternative = async (plannedId: string) => {
    setTriagePending(true);
    setTriageAction('use-alternative');
    setTriageError(undefined);
    try {
      setIsTriageModalOpen(false);
      setLoadTargetId(plannedId);
    } catch (err) {
      if (err instanceof IdentityApiError) {
        setTriageError(err.message || 'Could not copy alternative.');
      } else {
        setTriageError(err instanceof Error ? err.message : 'Could not copy alternative.');
      }
    } finally {
      setTriagePending(false);
      setTriageAction(null);
    }
  };

  const handleTriageContinueWorking = () => {
    setIsTriageModalOpen(false);
    setAutosaveStatus('saved');
    setAutosaveMessage('Continue editing the existing Working plan.');
  };

  const handlePromptCancelTrip = () => {
    setCancelTripError(undefined);
    setIsCancelTripModalOpen(true);
  };

  const handleConfirmCancelTrip = async () => {
    setCancelTripPending(true);
    setCancelTripError(undefined);
    try {
      const updatedTrip = await tripsApi.cancelTrip(trip.id, {
        expectedVersion: trip.version,
      });
      applyTripState(updatedTrip);
      setActiveBooking(null);
      setHasEverBooked(true);
      setHistoryRefreshKey((prev) => prev + 1);
      setIsCancelTripModalOpen(false);
      setAutosaveStatus('saved');
      setAutosaveMessage('Trip canceled. Saved options are now read-only.');
      if (onTripUpdated) onTripUpdated(updatedTrip);
    } catch (err) {
      if (err instanceof IdentityApiError) {
        if (err.code === 'VERSION_CONFLICT') {
          setCancelTripError('The Trip has changed on the server. Please reload and try again.');
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

  if (workspaceView === 'compare') {
    const comparedAlternatives = savedOptions.filter(a => selectedForCompareIds.includes(a.id));
    return (
      <section aria-labelledby="comparison-heading" className="card workspace-card comparison-workspace-card">
        <ItineraryComparisonView
          trip={trip}
          alternatives={comparedAlternatives}
          onBack={() => setWorkspaceView('workspace')}
          onSelectForBookingReview={(id) => handleSelectForBookingReview(id, 'compare')}
          hasActiveBooking={Boolean(activeBooking)}
          bookedOptionId={activeBooking?.plannedItineraryId ?? trip.booking?.plannedItineraryId}
        />
      </section>
    );
  }

  if (workspaceView === 'booking-review' && reviewAlternativeId) {
    const reviewAlternative = savedOptions.find(a => a.id === reviewAlternativeId);
    if (reviewAlternative) {
      return (
        <section aria-labelledby="booking-review-heading" className="card workspace-card booking-review-workspace-card">
          <BookingReviewView
            trip={trip}
            alternative={reviewAlternative}
            returnTarget={reviewReturnView}
            onBack={() => setWorkspaceView(reviewReturnView)}
            onBookingSuccess={(booking) => void handleBookingSuccess(booking)}
          />
        </section>
      );
    }
  }

  if (workspaceView === 'booking-confirmation' && activeBooking) {
    return (
      <section aria-labelledby="confirmation-heading" className="card workspace-card booking-confirmation-workspace-card">
        <BookingConfirmationView
          trip={trip}
          booking={activeBooking}
          onViewInWorkspace={() => setWorkspaceView('workspace')}
          onViewAllTrips={onBack}
        />
      </section>
    );
  }

  return (
    <section className="card workspace-card" aria-labelledby="workspace-heading">
      <div className="workspace-nav">
        <button type="button" className="text-button" onClick={onBack}>
          ← Back to all trips
        </button>
        <div style={{display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap'}}>
          {!effectiveHasBookingHistory ? (
            <button
              type="button"
              className="text-button delete-button"
              onClick={promptDeleteTrip}
              aria-label={`Delete trip ${trip.label}`}
            >
              Delete trip
            </button>
          ) : isTripCanceled ? (
            <button
              type="button"
              className="text-button"
              disabled
              title="This trip has been canceled"
              aria-label={`Trip ${trip.label} is canceled`}
            >
              Trip canceled
            </button>
          ) : (
            <button
              type="button"
              className="text-button delete-button"
              onClick={handlePromptCancelTrip}
              disabled={isExpired || temporalStatus === 'PAST'}
              title={
                isExpired || temporalStatus === 'PAST'
                  ? 'Past or expired trips cannot be canceled'
                  : 'Cancel this trip'
              }
              aria-label={`Cancel trip ${trip.label}`}
            >
              Cancel trip
            </button>
          )}
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
          <p className="eyebrow wordmark">DeTour</p>
          <div className="badge-row" style={{marginBottom: '0.35rem'}}>
            <span className={`badge ${temporalStatus === 'PAST' ? 'badge-past' : 'badge-upcoming'}`}>
              {temporalStatus === 'PAST' ? 'Past' : 'Upcoming'}
            </span>
            {isTripCanceled && <span className="badge badge-canceled">Canceled Trip</span>}
            {isExpired && <span className="badge badge-expired">Expired</span>}
          </div>
          <h1 id="workspace-heading" tabIndex={-1}>{trip.name ?? trip.label}</h1>
          <p className="trip-meta">
            From {trip.originAirportCode} to {trip.destinationName} • Working dates: {activeDraft?.startDate ?? trip.startDate} to {activeDraft?.endDate ?? trip.endDate} • {trip.travelerCount} traveler{trip.travelerCount === 1 ? '' : 's'}
          </p>
        </div>

        {/* Autosave status indicator with aria-live="polite" */}
        <div
          className={`autosave-status status-${autosaveStatus}`}
          role="status"
          aria-live="polite"
        >
          {autosaveStatus === 'saving' && <span>{autosaveMessage || 'Saving…'}</span>}
          {autosaveStatus === 'saved' && <span>{autosaveMessage || 'All changes saved.'}</span>}
          {autosaveStatus === 'error' && (
            <div className="save-error" role="alert">
              <span className="field-error">{autosaveMessage}</span>
              {refreshFailed ? (
                <button type="button" className="text-button" onClick={() => void handleReloadFromServer()}>Retry refresh</button>
              ) : isDirty(validateInputs().ages, validateInputs().budgetCents) ? (
                <><span>Your edited details have not been saved.</span><button type="button" className="text-button" onClick={() => void executeAutosaveRef.current()}>Retry save</button></>
              ) : <span>Retry the action from this Trip.</span>}
            </div>
          )}
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
              <span className="hint">Reloading discards your local edits.</span>
            </div>
          )}
        </div>
      </header>

      {optionFailure && <div className="save-error" aria-live="polite">
        {optionFailure.action === 'save' ? 'Save as new option' : optionFailure.action === 'update'
          ? 'Update option' : optionFailure.action === 'rename' ? 'Rename option' : 'Open option'} failed: {optionFailure.message}
        {' '}Retry that action, or reload the Trip if its version changed.
      </div>}

      {/* Canceled Trip Banner */}
      {isTripCanceled && (
        <section className="canceled-trip-banner card" aria-labelledby="canceled-trip-banner-heading">
          <div className="canceled-trip-banner-content">
            <h2 id="canceled-trip-banner-heading">This trip has been canceled</h2>
            <p>
              All reservations have been released without fees, and booking history has been permanently preserved.
              All Saved options on this Trip are now read-only. You can duplicate this Trip to make new plans.
            </p>
          </div>
          <button
            type="button"
            className="primary-button duplicate-canceled-trip-btn"
            onClick={() => setIsRevisionModalOpen(true)}
          >
            Duplicate into a new Trip
          </button>
        </section>
      )}

      {/* Active Booking Banner */}
      {activeBooking && !isTripCanceled && (
        <section className="active-booking-banner card" aria-labelledby="active-booking-heading">
          <div className="active-booking-header">
            <div>
              <div className="badge-row">
                <span className="badge badge-booked">Booking</span>
                <span className="badge badge-active">Active Reservation</span>
              </div>
              <h2 id="active-booking-heading" className="active-booking-title">Active Booking</h2>
              <p className="active-booking-ref">
                Booking Reference: <strong className="ref-code">{activeBooking.bookingReference}</strong>
              </p>
            </div>
            <div className="active-booking-actions" style={{display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap'}}>
              <button
                type="button"
                className="primary-button view-details-action-btn"
                onClick={() => setWorkspaceView('booking-confirmation')}
              >
                View Booking Details
              </button>
              <button
                type="button"
                className="secondary-action-button cancel-booking-btn"
                onClick={handlePromptCancelBooking}
                disabled={bookedOptionExpired}
                title={bookedOptionExpired ? 'Past or expired bookings cannot be canceled' : undefined}
              >
                Cancel Booking
              </button>
            </div>
          </div>
          <div className="active-booking-meta">
            <span>
              Booked:{' '}
              <strong>
                {activeBooking.bookedAt ? new Date(activeBooking.bookedAt).toLocaleDateString() : 'Confirmed'}
              </strong>
            </span>
            <span>
              Grand Total: <strong>{formatCents(activeBooking.grandTotalCents)}</strong>
            </span>
          </div>
        </section>
      )}

      {revisionSummary && (
        <RevisionSummaryBanner
          summary={revisionSummary}
          onDismiss={() => setRevisionSummary(null)}
        />
      )}

      {/* Progressive Builder & Component Slots */}
      {activeDraft && (
        <section className="workspace-section builder-section" aria-labelledby="builder-heading">
          <div className="section-header builder-header">
            <h3 id="builder-heading" tabIndex={-1}>Working plan</h3>
            <div className="option-actions">
              <label htmlFor="option-name">Option name</label>
              <input id="option-name" value={optionName} maxLength={300}
                onChange={event => setOptionName(event.target.value)} placeholder="Name this option" />
              <button type="button" className="primary-button" disabled={optionActionPending || isTripCanceled || !Boolean(activeDraft.selections.airfare || activeDraft.selections.stay || activeDraft.selections.rental)}
                onClick={() => void handleSaveNamedOption()}>Save as new option</button>
              {!activeDraft.selections.airfare && !activeDraft.selections.stay && !activeDraft.selections.rental && <p className="hint">Choose a flight, stay, or rental car to save an option. You can keep editing this incomplete Working plan.</p>}
              {editingOptionId && <p className="hint">Editing a copy of {savedOptions.find(item => item.id === editingOptionId)?.name || 'a Saved option'}. Save as new to keep both, or update the original below.</p>}
              {editingOptionId && !savedOptions.find(item => item.id === editingOptionId)?.booked && activeBooking?.plannedItineraryId !== editingOptionId && trip.booking?.plannedItineraryId !== editingOptionId && <button type="button" className="secondary-action-button"
                disabled={optionActionPending || isTripCanceled || !Boolean(activeDraft.selections.airfare || activeDraft.selections.stay || activeDraft.selections.rental)}
                onClick={() => setUpdateConfirmationOpen(true)}>Update this option</button>}
            </div>
          </div>

          <ItinerarySummaryTally trip={trip} selections={activeDraft.selections} />

          <div className="component-slots-grid">
            <AirfareSlot
              trip={trip}
              draftId={activeDraft.id}
              selectedAirfare={activeDraft.selections.airfare}
              mode={airfareMode}
              onStartSearch={() => setAirfareMode('searching')}
              onSelect={handleSelectAirfare}
              onChange={() => setAirfareMode('searching')}
              onRemove={promptRemoveAirfare}
              onCancelSearch={() =>
                setAirfareMode(activeDraft.selections.airfare ? 'selected' : 'empty')
              }
              pending={componentMutationPending || isTripCanceled}
            />

            <StaySlot
              trip={trip}
              draftId={activeDraft.id}
              selectedStay={activeDraft.selections.stay}
              mode={stayMode}
              initialType={stayAccommodationType}
              onStartSearch={() => setStayMode('searching')}
              onSelect={handleSelectStay}
              onChange={() => setStayMode('searching')}
              onRemove={promptRemoveStay}
              onCancelSearch={() =>
                setStayMode(activeDraft.selections.stay ? 'selected' : 'empty')
              }
              pending={componentMutationPending || isTripCanceled}
            />

            <RentalSlot
              trip={trip}
              draftId={activeDraft.id}
              selectedRental={activeDraft.selections.rental}
              mode={rentalMode}
              onSelect={handleSelectRental}
              onChange={() => setRentalMode('searching')}
              onRemove={promptRemoveRental}
              onCancelSearch={() =>
                setRentalMode(activeDraft.selections.rental ? 'selected' : 'hidden')
              }
              pending={componentMutationPending || isTripCanceled}
            />

            {(rentalMode === 'hidden' || (rentalMode === 'selected' && !activeDraft.selections.rental)) && !isTripCanceled && (
              <div className="add-car-container">
                <button
                  type="button"
                  className="secondary add-car-btn"
                  onClick={() => setRentalMode('searching')}
                  disabled={componentMutationPending}
                >
                  Add a car
                </button>
              </div>
            )}
          </div>
        </section>
      )}

      {/* Shared details form */}
      <section className="workspace-section" aria-labelledby="shared-details-heading">
        <div className="section-header">
          <h3 id="shared-details-heading">Trip Details &amp; Travelers</h3>
          {hasPlanned && !isTripCanceled && (
            <button
              type="button"
              className="text-button revise-button"
              onClick={() => setIsRevisionModalOpen(true)}
            >
              Revise Trip
            </button>
          )}
        </div>

        {hasPlanned && !isTripCanceled && (
          <p className="hint read-only-hint">
            Working dates and budget can change without changing Saved options. Use Revise Trip to change the destination or travelers.
          </p>
        )}
        {isTripCanceled && (
          <p className="hint read-only-hint">
            This trip is canceled. Trip details are read-only.
          </p>
        )}

        <form onSubmit={(e) => e.preventDefault()} noValidate>
          <div className="field-group">
            <div className="field">
              <label htmlFor="workspace-destination">Destination</label>
              <select
                id="workspace-destination"
                value={destinationKey}
                disabled={hasPlanned || isTripCanceled}
                onChange={(e) => {
                  setDestinationKey(e.target.value);
                }}
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
                disabled={isTripCanceled}
                min="2027-03-01"
                max="2027-03-31"
                onChange={(e) => {
                  setStartDate(e.target.value);
                }}
                aria-describedby={fieldErrors.dates ? 'workspace-dates-error' : undefined}
              />
            </div>

            <div className="field">
              <label htmlFor="workspace-end-date">Return date</label>
              <input
                type="date"
                id="workspace-end-date"
                value={endDate}
                disabled={isTripCanceled}
                min="2027-03-01"
                max="2027-03-31"
                onChange={(e) => {
                  setEndDate(e.target.value);
                }}
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
                disabled={isTripCanceled}
                onChange={(e) => {
                  setBudgetDollars(e.target.value);
                }}
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
                disabled={hasPlanned || isTripCanceled}
                value={travelerCountInput}
                onChange={(e) => {
                  const raw = e.target.value;
                  setTravelerCountInput(raw);
                  setFieldErrors((previous) => ({...previous, travelerCount: ''}));
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
              <p className="hint">Optional while editing the Working plan. Enter all traveler ages before saving an option for booking.</p>
              <div className="ages-grid">
                {Array.from({length: travelerCount}, (_, i) => (
                  <div key={i} className="field age-field">
                    <label htmlFor={`traveler-age-${i}`}>Traveler {i + 1} age</label>
                    <input
                      type="number"
                      id={`traveler-age-${i}`}
                      min="0"
                      max="120"
                      disabled={hasPlanned || isTripCanceled}
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

      {/* Saved options section */}
      <section className="workspace-section" aria-labelledby="alternatives-heading">
        <div className="section-header">
          <h3 id="alternatives-heading" tabIndex={-1}>
            Saved options ({savedOptions.length})
          </h3>
          <div className="alternatives-actions" style={{display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center'}}>
            {plannedAlternatives.length >= 2 && (
              <>
                <button
                  type="button"
                  className="primary-button compare-launch-btn"
                  disabled={selectedForCompareIds.length < 2}
                  onClick={() => setWorkspaceView('compare')}
                  aria-label={`Compare selected options (${selectedForCompareIds.length})`}
                >
                  Compare selected options ({selectedForCompareIds.length})
                </button>
                {selectedForCompareIds.length > 0 && (
                  <button
                    type="button"
                    className="text-button clear-compare-btn"
                    onClick={() => {
                      setSelectedForCompareIds([]);
                      setCompareNotification(undefined);
                    }}
                  >
                    Clear comparison selection
                  </button>
                )}
              </>
            )}
          </div>
        </div>

        {plannedAlternatives.length >= 2 ? (
          <p className="hint">
            Select 2 or 3 saved options to compare them side by side.
          </p>
        ) : (
          <p className="hint">
            Save at least two options to compare their dates, selections, and prices.
          </p>
        )}

        {compareNotification && (
          <div className="compare-notification" role="alert" aria-live="polite">
            {compareNotification}
          </div>
        )}

        {savedOptions.length === 0 ? (
          <p className="hint">No saved options yet. Add a selection to the Working plan, name it, and save it here.</p>
        ) : (
          <div className="alternatives-grid">
            {savedOptions.map((alt: AlternativeResponse) => (
              <AlternativeCard
                key={alt.id}
                alternative={alt}
                onOpenForEditing={(id) => { setCurrentPreservedForLoad(false); setLoadTargetId(id); }}
                onRename={(id) => {setRenameOptionId(id); setRenameName(savedOptions.find(item => item.id === id)?.name ?? '');}}
                tripExpired={alt.startDate ? alt.startDate <= pacificToday : isExpired}
                tripCanceled={isTripCanceled}
                hasBookingHistory={effectiveHasBookingHistory}
                isSelectedForCompare={selectedForCompareIds.includes(alt.id)}
                isBooked={Boolean(alt.booked || activeBooking?.plannedItineraryId === alt.id || trip.booking?.plannedItineraryId === alt.id)}
                hasActiveBooking={Boolean(activeBooking)}
                onViewBookingDetails={activeBooking?.plannedItineraryId === alt.id ? () => setWorkspaceView('booking-confirmation') : undefined}
                onToggleCompare={handleToggleCompare}
                onSelectForBookingReview={(id) => handleSelectForBookingReview(id, 'workspace')}
              />
            ))}
          </div>
        )}
      </section>

      {updateConfirmationOpen && editingOptionId && (() => {
        const target = savedOptions.find(item => item.id === editingOptionId);
        if (!target || !activeDraft) return null;
        const changes = (['airfare', 'stay', 'rental'] as const).filter(key =>
          JSON.stringify(target.selections[key]) !== JSON.stringify(activeDraft.selections[key]));
        return <div className="modal-backdrop" role="presentation">
          <div ref={optionDialogRef} className="modal" role="dialog" aria-modal="true" aria-labelledby="update-option-heading">
            <h3 id="update-option-heading">Replace {target.name || 'this option'}?</h3>
            <p>This updates the unbooked Saved option with the current Working plan. The option keeps its place in your list.</p>
            <p>Dates: {target.startDate} to {target.endDate} → {activeDraft.startDate ?? trip.startDate} to {activeDraft.endDate ?? trip.endDate}</p>
            <p>Components changed: {changes.length ? changes.join(', ') : 'none'}.</p>
            <p>Total: {formatCents(target.tally?.grandTotalCents ?? 0)} → {formatCents(activeDraft.tally?.grandTotalCents ?? 0)}.</p>
            {revisionSummary && (revisionSummary.removals.length > 0 || revisionSummary.adjustments.length > 0) &&
              <div className="revision-summary-banner">
                <h4>Date changes to review</h4>
                <ul>
                  {revisionSummary.removals.map((item, index) => <li key={`removed-${index}`}>{item.component} removed: {item.reason}</li>)}
                  {revisionSummary.adjustments.map((item, index) => <li key={`adjusted-${index}`}>
                    {item.component} changed: {item.reason}
                    {item.previousPriceCents !== null && item.newPriceCents !== null &&
                      <> ({formatCents(item.previousPriceCents)} → {formatCents(item.newPriceCents)})</>}
                  </li>)}
                </ul>
              </div>}
            <div className="modal-actions">
              <button type="button" onClick={() => setUpdateConfirmationOpen(false)} disabled={optionActionPending}>Cancel</button>
              <button type="button" className="primary-button" onClick={() => void handleUpdateNamedOption()} disabled={optionActionPending}>Replace Saved option</button>
            </div>
          </div>
        </div>;
      })()}

      {renameOptionId && <div className="modal-backdrop" role="presentation">
        <div ref={optionDialogRef} className="modal" role="dialog" aria-modal="true" aria-labelledby="rename-option-heading">
          <h3 id="rename-option-heading">Rename Saved option</h3>
          <p>The name changes; its dates, selections, and price stay the same.</p>
          <label htmlFor="rename-option-name">Option name</label>
          <input id="rename-option-name" value={renameName} maxLength={300} onChange={event => setRenameName(event.target.value)} />
          <div className="modal-actions">
            <button type="button" onClick={() => setRenameOptionId(null)} disabled={optionActionPending}>Cancel</button>
            <button type="button" className="primary-button" onClick={() => void handleRenameOption()} disabled={optionActionPending || !renameName.trim()}>Save name</button>
          </div>
        </div>
      </div>}

      {loadTargetId && (
        <div className="modal-backdrop" role="presentation">
          <div ref={optionDialogRef} className="modal" role="dialog" aria-modal="true" aria-labelledby="load-option-heading">
            <h3 id="load-option-heading">Open Saved option for editing?</h3>
            <p>This copies the option into your one Working plan. The Saved option stays unchanged. Different Working dates and selections will be replaced.</p>
            <p>You can keep the current Working plan as another named Saved option first.</p>
            {!currentPreservedForLoad && <>
              <label htmlFor="keep-working-option-name">Name the current Working plan to keep both</label>
              <input id="keep-working-option-name" value={optionName} maxLength={300}
                onChange={event => setOptionName(event.target.value)} placeholder="Name your current option" />
              {!activeDraft?.selections.airfare && !activeDraft?.selections.stay && !activeDraft?.selections.rental &&
                <p className="hint">Add a selection before keeping the current Working plan as a Saved option.</p>}
            </>}
            <div className="modal-actions">
              <button type="button" onClick={() => { setCurrentPreservedForLoad(false); setLoadTargetId(null); }} disabled={optionActionPending}>Cancel</button>
              <button type="button" onClick={() => void handleLoadNamedOption(true)} disabled={optionActionPending || (!currentPreservedForLoad && (!optionName.trim() || !Boolean(activeDraft?.selections.airfare || activeDraft?.selections.stay || activeDraft?.selections.rental)))}>{currentPreservedForLoad ? 'Retry opening option' : 'Save current first, then open'}</button>
              <button type="button" className="primary-button" onClick={() => void handleLoadNamedOption(false)} disabled={optionActionPending}>Replace Working plan</button>
            </div>
          </div>
        </div>
      )}

      {/* Booking History Section */}
      {(effectiveHasBookingHistory || isTripCanceled) && (
        <BookingHistorySection
          tripId={trip.id}
          refreshKey={historyRefreshKey}
        />
      )}

      {/* Revision modal */}
      {isRevisionModalOpen && (
        <TripRevisionModal
          isOpen={isRevisionModalOpen}
          trip={trip}
          mode={isTripCanceled ? 'duplicate' : 'revise'}
          onClose={() => setIsRevisionModalOpen(false)}
          onSuccess={(newTrip) => {
            applyTripState(newTrip);
            setIsRevisionModalOpen(false);
            if (onTripUpdated) onTripUpdated(newTrip);
          }}
        />
      )}

      {/* Cancel booking modal */}
      {isCancelBookingModalOpen && activeBooking && (
        <CancelBookingModal
          isOpen={isCancelBookingModalOpen}
          tripLabel={trip.label}
          bookingReference={activeBooking.bookingReference}
          pending={cancelBookingPending}
          errorMessage={cancelBookingError}
          onClose={() => {
            setIsCancelBookingModalOpen(false);
            setCancelBookingError(undefined);
          }}
          onConfirm={() => void handleConfirmCancelBooking()}
        />
      )}

      {/* Post-cancellation triage modal */}
      {isTriageModalOpen && (
        <PostCancellationTriageModal
          isOpen={isTriageModalOpen}
          plannedAlternatives={trip.planned || []}
          pending={triagePending}
          pendingAction={triageAction}
          errorMessage={triageError}
          onUseAlternative={(plannedId) => void handleTriageUseAlternative(plannedId)}
          onContinueWorking={handleTriageContinueWorking}
          onClose={() => {
            setIsTriageModalOpen(false);
            setTriageError(undefined);
          }}
        />
      )}

      {/* Cancel trip modal */}
      {isCancelTripModalOpen && (
        <CancelTripModal
          isOpen={isCancelTripModalOpen}
          tripLabel={trip.label}
          hasActiveBooking={Boolean(activeBooking)}
          pending={cancelTripPending}
          errorMessage={cancelTripError}
          onClose={() => {
            setIsCancelTripModalOpen(false);
            setCancelTripError(undefined);
          }}
          onConfirm={() => void handleConfirmCancelTrip()}
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

      {/* Removal confirmation modal */}
      {removeTarget && (
        <ConfirmRemoveModal
          isOpen={Boolean(removeTarget)}
          componentTitle={removeTarget.title}
          formattedPrice={removeTarget.formattedPrice}
          pending={componentMutationPending}
          errorMessage={removeError}
          onClose={() => {
            setRemoveTarget(null);
            setRemoveError(undefined);
          }}
          onConfirm={() => void handleConfirmRemove()}
        />
      )}

    </section>
  );
});
