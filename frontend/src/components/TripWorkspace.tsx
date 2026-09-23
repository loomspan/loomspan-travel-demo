import {useEffect, useRef, useState, useCallback} from 'react';
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
import {DraftReadinessBanner} from './DraftReadinessBanner';
import {BudgetOverageModal} from './BudgetOverageModal';
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

  const activeDraft = trip.drafts && trip.drafts.length > 0 ? trip.drafts[0] : null;

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

  const [promotionPending, setPromotionPending] = useState(false);
  const [readinessIssues, setReadinessIssues] = useState<Record<string, string> | null>(null);
  const [isReadinessBannerOpen, setIsReadinessBannerOpen] = useState(false);
  const [isOverageModalOpen, setIsOverageModalOpen] = useState(false);
  const [overageDetails, setOverageDetails] = useState<{
    budgetCents: number;
    grandTotalCents: number;
    budgetOverageCents: number;
    draftId: string;
    draftVersion: number;
  } | null>(null);
  const [overageErrorMessage, setOverageErrorMessage] = useState<string | undefined>();
  const [budgetOverageAcknowledged, setBudgetOverageAcknowledged] = useState(false);
  const [highlightedSlot, setHighlightedSlot] = useState<'airfare' | 'stay' | 'rental' | null>(null);

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
  const [triageAction, setTriageAction] = useState<'use-alternative' | 'create-draft' | null>(null);
  const [triageError, setTriageError] = useState<string | undefined>();

  const [isCancelTripModalOpen, setIsCancelTripModalOpen] = useState(false);
  const [cancelTripPending, setCancelTripPending] = useState(false);
  const [cancelTripError, setCancelTripError] = useState<string | undefined>();

  const [historyRefreshKey, setHistoryRefreshKey] = useState(0);

  const [activeBooking, setActiveBooking] = useState<BookingResponse | null>(
    initialActiveBooking ?? null
  );
  const [workspaceView, setWorkspaceView] = useState<
    'workspace' | 'compare' | 'booking-review' | 'booking-confirmation'
  >('workspace');
  const [reviewAlternativeId, setReviewAlternativeId] = useState<string | null>(null);
  const [reviewReturnView, setReviewReturnView] = useState<'workspace' | 'compare'>('workspace');

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
    if (!trip.alternatives) {
      setSelectedForCompareIds([]);
      return;
    }
    const validIds = new Set(
      trip.alternatives
        .filter((a) => a.lifecycle.toUpperCase() === 'PLANNED')
        .map((a) => a.id)
    );
    setSelectedForCompareIds((prev) => prev.filter((id) => validIds.has(id)));
  }, [trip.alternatives]);

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

  useEffect(() => {
    if (highlightedSlot === 'rental' && rentalMode !== 'hidden') {
      const el = document.getElementById('rental-slot-heading');
      if (el && document.activeElement !== el) {
        if (typeof el.scrollIntoView === 'function') {
          el.scrollIntoView({behavior: 'smooth', block: 'center'});
        }
        el.focus();
      }
    }
  }, [highlightedSlot, rentalMode]);

  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isInitialMount = useRef(true);
  const isSavingRef = useRef(false);
  const pendingSaveRef = useRef(false);
  const isPromotingRef = useRef(false);

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

  // Sync state if initialTrip changes
  useEffect(() => {
    applyTripState(initialTrip);
  }, [initialTrip, applyTripState]);

  const hasPlanned = trip.planned && trip.planned.length > 0;
  const plannedAlternatives = (trip.alternatives || []).filter(
    (a) => a.lifecycle.toUpperCase() === 'PLANNED'
  );

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
    if (isSavingRef.current || isTripCanceled) {
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
      setBudgetOverageAcknowledged(false);
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
      setBudgetOverageAcknowledged(false);
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
      setBudgetOverageAcknowledged(false);
      setAutosaveStatus('saved');
      setAutosaveMessage('Planned itinerary duplicated to draft.');
    } catch (err) {
      const failure = err instanceof IdentityApiError ? err.message : 'Could not duplicate alternative.';
      setAutosaveStatus('error');
      setAutosaveMessage(failure);
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
      setBudgetOverageAcknowledged(false);
      setAirfareMode('selected');
      setAutosaveStatus('saved');
      setAutosaveMessage('Flight saved to draft.');
    } catch (err) {
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
      setBudgetOverageAcknowledged(false);
      setStayMode('selected');
      setAutosaveStatus('saved');
      setAutosaveMessage('Stay saved to draft.');
    } catch (err) {
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
      setBudgetOverageAcknowledged(false);
      setRentalMode('selected');
      setAutosaveStatus('saved');
      setAutosaveMessage('Rental car saved to draft.');
    } catch (err) {
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
      setBudgetOverageAcknowledged(false);
      const title = removeTarget.title;
      setRemoveTarget(null);
      setAutosaveStatus('saved');
      setAutosaveMessage(`${title} removed from draft.`);
    } catch (err) {
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
    setBudgetOverageAcknowledged(false);
    setTravelerAges((prev) => {
      const next = [...prev];
      next[index] = val;
      return next;
    });
  };

  const handlePromoteDraft = async (
    draftId: string,
    draftVersion: number,
    forceAcknowledged?: boolean
  ) => {
    if (isPromotingRef.current || promotionPending) return;
    isPromotingRef.current = true;
    setPromotionPending(true);
    setOverageErrorMessage(undefined);
    setAutosaveStatus('saving');
    setAutosaveMessage('Saving draft as planned itinerary…');

    const acknowledge = forceAcknowledged ?? budgetOverageAcknowledged;

    try {
      const updatedTrip = await tripsApi.promoteDraft(trip.id, draftId, {
        expectedVersion: trip.version,
        expectedDraftVersion: draftVersion,
        budgetOverageAcknowledged: acknowledge ? true : undefined,
      });
      applyTripState(updatedTrip);
      if (onTripUpdated) {
        onTripUpdated(updatedTrip);
      }
      setIsOverageModalOpen(false);
      setIsReadinessBannerOpen(false);
      setReadinessIssues(null);
      setOverageDetails(null);
      setBudgetOverageAcknowledged(false);
      setHighlightedSlot(null);
      setAutosaveStatus('saved');
      setAutosaveMessage('Draft successfully saved as planned itinerary.');
    } catch (err) {
      if (err instanceof IdentityApiError) {
        if (err.code === 'PLANNING_NOT_READY') {
          const issues =
            err.fields && Object.keys(err.fields).length > 0
              ? err.fields
              : {general: err.apiMessage || 'Draft is not ready to be saved as a planned itinerary.'};
          setReadinessIssues(issues);
          setIsReadinessBannerOpen(true);
          setAutosaveStatus('error');
          setAutosaveMessage('Draft cannot be planned yet. Please review the blocking issues.');
        } else if (err.code === 'BUDGET_OVERAGE_UNACKNOWLEDGED') {
          const targetDraft = trip.drafts?.find((d) => d.id === draftId) ?? activeDraft;
          const bCents = err.fields?.budgetCents
            ? parseInt(err.fields.budgetCents, 10)
            : (trip.budgetCents ?? 0);
          const gtCents = err.fields?.grandTotalCents
            ? parseInt(err.fields.grandTotalCents, 10)
            : (targetDraft?.tally?.grandTotalCents ?? 0);
          const boCents = err.fields?.budgetOverageCents
            ? parseInt(err.fields.budgetOverageCents, 10)
            : Math.max(0, gtCents - bCents);

          setOverageDetails({
            budgetCents: bCents,
            grandTotalCents: gtCents,
            budgetOverageCents: boCents,
            draftId,
            draftVersion,
          });
          setIsOverageModalOpen(true);
          setAutosaveStatus('idle');
          setAutosaveMessage(undefined);
        } else if (err.code === 'VERSION_CONFLICT') {
          setAutosaveStatus('conflict');
          setAutosaveMessage('The Trip has changed on the server. Reload before saving.');
          if (isOverageModalOpen) {
            setOverageErrorMessage('The Trip has changed on the server. Please reload.');
          }
        } else if (err.code === 'ALTERNATIVE_EXPIRED') {
          setAutosaveStatus('error');
          setAutosaveMessage('This trip or draft alternative has expired and can no longer be planned.');
          if (isOverageModalOpen) {
            setOverageErrorMessage('This trip or draft alternative has expired.');
          }
        } else {
          const msg = err.apiMessage || 'Failed to save draft as planned itinerary.';
          setAutosaveStatus('error');
          setAutosaveMessage(msg);
          if (isOverageModalOpen) {
            setOverageErrorMessage(msg);
          }
        }
      } else {
        const msg = err instanceof Error ? err.message : 'Something went wrong. Please try again.';
        setAutosaveStatus('error');
        setAutosaveMessage(msg);
        if (isOverageModalOpen) {
          setOverageErrorMessage(msg);
        }
      }
    } finally {
      isPromotingRef.current = false;
      setPromotionPending(false);
    }
  };

  const handleConfirmOveragePromotion = async () => {
    if (!overageDetails) return;
    setBudgetOverageAcknowledged(true);
    await handlePromoteDraft(overageDetails.draftId, overageDetails.draftVersion, true);
  };

  const handleJumpToIssue = (key: string) => {
    setHighlightedSlot(null);
    let targetEl: HTMLElement | null = null;

    if (key === 'travelerAges') {
      let firstEmpty = travelerAges.findIndex((age) => !age.trim());
      if (firstEmpty === -1) firstEmpty = 0;
      targetEl = document.getElementById(`traveler-age-${firstEmpty}`);
    } else if (key === 'adult') {
      targetEl = document.getElementById('traveler-age-0');
    } else if (key === 'budgetCents' || key === 'budget') {
      targetEl = document.getElementById('workspace-budget');
    } else if (key === 'components') {
      targetEl = document.getElementById('builder-heading') || document.querySelector('.component-slots-grid');
    } else if (key === 'airfare') {
      setHighlightedSlot('airfare');
      targetEl = document.getElementById('airfare-slot-heading');
    } else if (key === 'stay') {
      setHighlightedSlot('stay');
      targetEl = document.getElementById('stay-slot-heading');
    } else if (key === 'rental') {
      if (rentalMode === 'hidden') {
        setRentalMode('searching');
      }
      setHighlightedSlot('rental');
      targetEl = document.getElementById('rental-slot-heading');
      if (!targetEl) {
        setTimeout(() => {
          const el = document.getElementById('rental-slot-heading');
          if (el) {
            if (typeof el.scrollIntoView === 'function') {
              el.scrollIntoView({behavior: 'smooth', block: 'center'});
            }
            el.focus();
          }
        }, 0);
      }
    } else if (key === 'destination') {
      targetEl = document.getElementById('workspace-destination');
    } else if (key === 'dates' || key === 'startDate' || key === 'endDate') {
      targetEl = document.getElementById('workspace-start-date');
    } else if (key === 'travelerCount') {
      targetEl = document.getElementById('workspace-traveler-count');
    }

    if (targetEl) {
      if (typeof targetEl.scrollIntoView === 'function') {
        targetEl.scrollIntoView({behavior: 'smooth', block: 'center'});
      }
      targetEl.focus();
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
      const updatedTrip = await tripsApi.duplicateAlternative(trip.id, plannedId, {
        expectedVersion: trip.version,
      });
      applyTripState(updatedTrip);
      setIsTriageModalOpen(false);
      setAutosaveStatus('saved');
      setAutosaveMessage('Planned itinerary copied to new draft.');
      if (onTripUpdated) onTripUpdated(updatedTrip);
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

  const handleTriageCreateDraft = async () => {
    setTriagePending(true);
    setTriageAction('create-draft');
    setTriageError(undefined);
    try {
      const updatedTrip = await tripsApi.createDraft(trip.id, {
        expectedVersion: trip.version,
      });
      applyTripState(updatedTrip);
      setIsTriageModalOpen(false);
      setAutosaveStatus('saved');
      setAutosaveMessage('New empty draft created.');
      if (onTripUpdated) onTripUpdated(updatedTrip);
    } catch (err) {
      if (err instanceof IdentityApiError) {
        setTriageError(err.message || 'Could not create draft.');
      } else {
        setTriageError(err instanceof Error ? err.message : 'Could not create draft.');
      }
    } finally {
      setTriagePending(false);
      setTriageAction(null);
    }
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
      setAutosaveMessage('Trip canceled. Alternatives are now read-only.');
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
    const comparedAlternatives = (trip.alternatives || []).filter(
      (a) => a.lifecycle.toUpperCase() === 'PLANNED' && selectedForCompareIds.includes(a.id)
    );
    return (
      <section aria-labelledby="comparison-heading" className="card workspace-card comparison-workspace-card">
        <ItineraryComparisonView
          trip={trip}
          alternatives={comparedAlternatives}
          onBack={() => setWorkspaceView('workspace')}
          onSelectForBookingReview={(id) => handleSelectForBookingReview(id, 'compare')}
          hasActiveBooking={Boolean(activeBooking)}
        />
      </section>
    );
  }

  if (workspaceView === 'booking-review' && reviewAlternativeId) {
    const reviewAlternative = (trip.alternatives || []).find((a) => a.id === reviewAlternativeId);
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
          <p className="eyebrow">TRIP WORKSPACE</p>
          <div className="badge-row" style={{marginBottom: '0.35rem'}}>
            <span className={`badge ${temporalStatus === 'PAST' ? 'badge-past' : 'badge-upcoming'}`}>
              {temporalStatus === 'PAST' ? 'Past' : 'Upcoming'}
            </span>
            {isTripCanceled && <span className="badge badge-canceled">Canceled</span>}
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
          {autosaveStatus === 'saving' && <span>{autosaveMessage || 'Saving…'}</span>}
          {autosaveStatus === 'saved' && <span>{autosaveMessage || 'All changes saved.'}</span>}
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

      {/* Canceled Trip Banner */}
      {isTripCanceled && (
        <section className="canceled-trip-banner card" aria-labelledby="canceled-trip-banner-heading">
          <div className="canceled-trip-banner-content">
            <h2 id="canceled-trip-banner-heading">This trip has been canceled</h2>
            <p>
              All reservations have been released without fees, and booking history has been permanently preserved.
              All alternatives on this trip are now read-only. You can duplicate this trip into a fresh travel plan to make new revisions.
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
                <span className="badge badge-booked">BOOKED</span>
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
                disabled={isExpired || temporalStatus === 'PAST'}
                title={isExpired || temporalStatus === 'PAST' ? 'Past or expired bookings cannot be canceled' : undefined}
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

      {isReadinessBannerOpen && readinessIssues && (
        <DraftReadinessBanner
          issues={readinessIssues}
          onJumpTo={handleJumpToIssue}
          onDismiss={() => {
            setIsReadinessBannerOpen(false);
            setHighlightedSlot(null);
          }}
        />
      )}

      {/* Progressive Builder & Component Slots */}
      {activeDraft && (
        <section className="workspace-section builder-section" aria-labelledby="builder-heading">
          <div className="section-header builder-header">
            <h3 id="builder-heading" tabIndex={-1}>Progressive Trip Builder</h3>
            <button
              type="button"
              className="primary-button promote-draft-btn"
              disabled={isExpired || promotionPending || isTripCanceled}
              onClick={() => void handlePromoteDraft(activeDraft.id, activeDraft.version)}
              aria-label="Save as Planned Itinerary"
            >
              {promotionPending ? 'Saving planned itinerary…' : 'Save as Planned Itinerary'}
            </button>
          </div>

          <ItinerarySummaryTally trip={trip} selections={activeDraft.selections} />

          <div className="component-slots-grid">
            <AirfareSlot
              trip={trip}
              draftId={activeDraft.id}
              selectedAirfare={activeDraft.selections.airfare}
              mode={airfareMode}
              highlighted={highlightedSlot === 'airfare'}
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
              highlighted={highlightedSlot === 'stay'}
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
              highlighted={highlightedSlot === 'rental'}
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
            Trips with Planned alternatives cannot change destination, dates, or traveler count in place.
            Use &ldquo;Revise Trip&rdquo; to create a new version.
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
                  setBudgetOverageAcknowledged(false);
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
                disabled={hasPlanned || isTripCanceled}
                min="2027-03-01"
                max="2027-03-31"
                onChange={(e) => {
                  setStartDate(e.target.value);
                  setBudgetOverageAcknowledged(false);
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
                disabled={hasPlanned || isTripCanceled}
                min="2027-03-01"
                max="2027-03-31"
                onChange={(e) => {
                  setEndDate(e.target.value);
                  setBudgetOverageAcknowledged(false);
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
                  setBudgetOverageAcknowledged(false);
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
                  setBudgetOverageAcknowledged(false);
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

      {/* Alternatives section */}
      <section className="workspace-section" aria-labelledby="alternatives-heading">
        <div className="section-header">
          <h3 id="alternatives-heading" tabIndex={-1}>
            Alternatives ({trip.alternatives ? trip.alternatives.length : 0})
          </h3>
          <span className="count-pill">
            {trip.drafts ? trip.drafts.length : 0} Draft alternative{trip.drafts && trip.drafts.length === 1 ? '' : 's'}
          </span>
          <span className="count-pill">
            {plannedAlternatives.length} Planned alternative{plannedAlternatives.length === 1 ? '' : 's'}
          </span>
          <div className="alternatives-actions" style={{display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center'}}>
            {plannedAlternatives.length >= 2 && (
              <>
                <button
                  type="button"
                  className="primary-button compare-launch-btn"
                  disabled={selectedForCompareIds.length < 2}
                  onClick={() => setWorkspaceView('compare')}
                  aria-label={`Compare selected itineraries (${selectedForCompareIds.length})`}
                >
                  Compare selected itineraries ({selectedForCompareIds.length})
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
            {!isTripCanceled && (
              <button
                type="button"
                className="primary create-empty-draft-btn"
                onClick={() => void handleCreateEmptyDraft()}
              >
                Create empty draft
              </button>
            )}
          </div>
        </div>

        {plannedAlternatives.length >= 2 ? (
          <p className="hint">
            Draft alternatives let you explore and compare options. Select 2 or 3 Planned alternatives to compare them side-by-side.
          </p>
        ) : (
          <p className="hint">
            Draft alternatives let you explore and compare options. Promote at least 2 draft alternatives to Planned to compare them.
          </p>
        )}

        {compareNotification && (
          <div className="compare-notification" role="alert" aria-live="polite">
            {compareNotification}
          </div>
        )}

        {(!trip.alternatives || trip.alternatives.length === 0) ? (
          <p className="hint">No alternatives yet. Create an empty draft to get started.</p>
        ) : (
          <div className="alternatives-grid">
            {trip.alternatives.map((alt: AlternativeResponse) => (
              <AlternativeCard
                key={alt.id}
                alternative={alt}
                tripExpired={isExpired}
                tripCanceled={isTripCanceled}
                hasBookingHistory={effectiveHasBookingHistory}
                promotionPending={promotionPending}
                isSelectedForCompare={selectedForCompareIds.includes(alt.id)}
                isBooked={activeBooking?.plannedItineraryId === alt.id}
                hasActiveBooking={Boolean(activeBooking)}
                onViewBookingDetails={() => setWorkspaceView('booking-confirmation')}
                onToggleCompare={handleToggleCompare}
                onSelectForBookingReview={(id) => handleSelectForBookingReview(id, 'workspace')}
                onDuplicateDraft={(id, ver) => void handleDuplicateDraft(id, ver)}
                onDuplicatePlanned={(id) => void handleDuplicatePlanned(id)}
                onDeleteDraft={(id, ver) => promptDeleteDraft(id, ver)}
                onDeletePlanned={(id) => promptDeletePlanned(id)}
                onPromoteDraft={(id, ver) => void handlePromoteDraft(id, ver)}
              />
            ))}
          </div>
        )}
      </section>

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
          onCreateDraft={() => void handleTriageCreateDraft()}
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

      {/* Budget overage acknowledgment modal */}
      {isOverageModalOpen && overageDetails && (
        <BudgetOverageModal
          isOpen={isOverageModalOpen}
          budgetCents={overageDetails.budgetCents}
          grandTotalCents={overageDetails.grandTotalCents}
          budgetOverageCents={overageDetails.budgetOverageCents}
          pending={promotionPending}
          errorMessage={overageErrorMessage}
          onClose={() => {
            setIsOverageModalOpen(false);
            setOverageErrorMessage(undefined);
          }}
          onConfirm={() => void handleConfirmOveragePromotion()}
        />
      )}
    </section>
  );
}
