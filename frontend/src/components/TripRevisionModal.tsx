import {FormEvent, useEffect, useRef, useState} from 'react';
import {tripsApi, type TripResponse, type PlannedResponse} from '../api/tripsApi';
import {IdentityApiError} from '../api/identityApi';

type TripRevisionModalProps = {
  isOpen: boolean;
  trip: TripResponse;
  onClose: () => void;
  onSuccess: (newTrip: TripResponse) => void;
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

export function TripRevisionModal({isOpen, trip, onClose, onSuccess}: TripRevisionModalProps) {
  const [destinationKey, setDestinationKey] = useState(trip.destinationKey);
  const [startDate, setStartDate] = useState(trip.startDate);
  const [endDate, setEndDate] = useState(trip.endDate);
  const [travelerCount, setTravelerCount] = useState(String(trip.travelerCount));
  const [selectedPlannedIds, setSelectedPlannedIds] = useState<string[]>(() =>
    trip.planned.map((p) => p.id)
  );
  const [pending, setPending] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [globalError, setGlobalError] = useState<string | undefined>();

  const modalRef = useRef<HTMLDivElement>(null);
  const previousActiveElement = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (isOpen) {
      previousActiveElement.current = document.activeElement as HTMLElement;
      setDestinationKey(trip.destinationKey);
      setStartDate(trip.startDate);
      setEndDate(trip.endDate);
      setTravelerCount(String(trip.travelerCount));
      setSelectedPlannedIds(trip.planned.map((p: PlannedResponse) => p.id));
      const first = modalRef.current?.querySelector<HTMLElement>('select, input, button');
      first?.focus();
    } else if (previousActiveElement.current) {
      if (document.body.contains(previousActiveElement.current)) {
        previousActiveElement.current.focus();
      }
      previousActiveElement.current = null;
    }
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key === 'Tab' && modalRef.current) {
        const focusable = modalRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );
        if (!focusable.length) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const togglePlannedId = (id: string) => {
    setSelectedPlannedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const errors: Record<string, string> = {};
    const dateError = validateDates(startDate, endDate);
    if (dateError) errors.dates = dateError;
    const count = parseInt(travelerCount, 10);
    if (isNaN(count) || count < 1 || count > 8) errors.travelerCount = 'Traveler count must be between 1 and 8.';
    if (selectedPlannedIds.length === 0) {
      errors.sourcePlanned = 'Select at least one Planned alternative to copy into the revised trip.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setPending(true);
    setFieldErrors({});
    setGlobalError(undefined);

    try {
      const newTrip = await tripsApi.duplicateTrip(trip.id, {
        expectedVersion: trip.version,
        destinationKey,
        startDate,
        endDate,
        travelerCount: count,
        travelerAges: count === trip.travelerCount ? trip.travelerAges : null,
        budgetCents: trip.budgetCents,
        sourcePlannedItineraryIds: selectedPlannedIds,
      });
      onSuccess(newTrip);
      onClose();
    } catch (err) {
      if (err instanceof IdentityApiError) {
        if (err.kind === 'network') {
          setGlobalError('We could not reach DeTour. Check your connection and try again.');
        } else if (err.code === 'VALIDATION_FAILED' || Object.keys(err.fields).length > 0) {
          setFieldErrors(err.fields);
          setGlobalError('Please correct the highlighted fields.');
        } else if (err.code === 'VERSION_CONFLICT') {
          setGlobalError('The source Trip has changed on the server. Please reload and try again.');
        } else {
          setGlobalError(err.message || 'Could not revise trip. Please try again.');
        }
      } else {
        setGlobalError('Something went wrong. Please try again.');
      }
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div
        className="modal card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="revise-trip-heading"
        ref={modalRef}
      >
        <div className="modal-header">
          <h2 id="revise-trip-heading">Revise trip</h2>
          <button type="button" className="text-button" onClick={onClose} aria-label="Close dialog">
            ✕
          </button>
        </div>

        <p className="hint">
          Because this trip has Planned alternatives, modifying destination, dates, or travelers creates a new revised Trip copy.
        </p>

        {globalError && (
          <div className="field-error" role="alert" style={{marginBottom: '1rem'}}>
            {globalError}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div className="field">
            <label htmlFor="revise-destination">Destination</label>
            <select
              id="revise-destination"
              value={destinationKey}
              onChange={(e) => setDestinationKey(e.target.value)}
            >
              {SUPPORTED_DESTINATIONS.map((dest) => (
                <option key={dest.key} value={dest.key}>
                  {dest.name}
                </option>
              ))}
            </select>
          </div>

          <div className="field-group">
            <div className="field">
              <label htmlFor="revise-start-date">Departure date</label>
              <input
                type="date"
                id="revise-start-date"
                value={startDate}
                min="2027-03-01"
                max="2027-03-31"
                onChange={(e) => setStartDate(e.target.value)}
                aria-describedby={fieldErrors.dates ? 'revise-dates-error' : undefined}
              />
            </div>
            <div className="field">
              <label htmlFor="revise-end-date">Return date</label>
              <input
                type="date"
                id="revise-end-date"
                value={endDate}
                min="2027-03-01"
                max="2027-03-31"
                onChange={(e) => setEndDate(e.target.value)}
                aria-describedby={fieldErrors.dates ? 'revise-dates-error' : undefined}
              />
            </div>
          </div>
          {fieldErrors.dates && (
            <p className="field-error" id="revise-dates-error">
              {fieldErrors.dates}
            </p>
          )}

          <div className="field">
            <label htmlFor="revise-traveler-count">Travelers</label>
            <input
              type="number"
              id="revise-traveler-count"
              min="1"
              max="8"
              value={travelerCount}
              onChange={(e) => setTravelerCount(e.target.value)}
              aria-describedby={fieldErrors.travelerCount ? 'revise-traveler-count-error' : undefined}
            />
            {fieldErrors.travelerCount && (
              <p className="field-error" id="revise-traveler-count-error">
                {fieldErrors.travelerCount}
              </p>
            )}
          </div>

          <div className="field">
            <fieldset>
              <legend>Planned alternatives to copy</legend>
              <p className="hint">Selected itineraries will be revalidated and added as Drafts in the new trip:</p>
              {trip.planned.map((p) => (
                <label key={p.id} className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={selectedPlannedIds.includes(p.id)}
                    onChange={() => togglePlannedId(p.id)}
                  />
                  <span>Planned snapshot ({p.id.slice(0, 8)}…)</span>
                </label>
              ))}
              {fieldErrors.sourcePlanned && (
                <p className="field-error">{fieldErrors.sourcePlanned}</p>
              )}
            </fieldset>
          </div>

          <div className="modal-actions">
            <button type="button" className="text-button" onClick={onClose} disabled={pending}>
              Cancel
            </button>
            <button type="submit" className="primary" disabled={pending}>
              {pending ? 'Revising…' : 'Create revised trip'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
