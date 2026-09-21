import {FormEvent, useEffect, useRef, useState} from 'react';
import {tripsApi, type TripResponse} from '../api/tripsApi';
import {IdentityApiError} from '../api/identityApi';

type TripCreateModalProps = {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (trip: TripResponse) => void;
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

export function TripCreateModal({isOpen, onClose, onSuccess}: TripCreateModalProps) {
  const [destinationKey, setDestinationKey] = useState('destination-sfo');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [travelerCount, setTravelerCount] = useState('1');
  const [pending, setPending] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [globalError, setGlobalError] = useState<string | undefined>();

  const modalRef = useRef<HTMLDivElement>(null);
  const previousActiveElement = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (isOpen) {
      previousActiveElement.current = document.activeElement as HTMLElement;
      // Focus first input or heading
      const firstInput = modalRef.current?.querySelector<HTMLElement>('select, input, button');
      firstInput?.focus();
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

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const errors: Record<string, string> = {};
    if (!destinationKey) errors.destinationKey = 'Select a destination.';
    const dateError = validateDates(startDate, endDate);
    if (dateError) {
      errors.dates = dateError;
    }
    const count = parseInt(travelerCount, 10);
    if (isNaN(count) || count < 1 || count > 8) {
      errors.travelerCount = 'Traveler count must be between 1 and 8.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setPending(true);
    setFieldErrors({});
    setGlobalError(undefined);

    try {
      const trip = await tripsApi.createTrip({
        destinationKey,
        startDate,
        endDate,
        travelerCount: count,
      });
      onSuccess(trip);
      onClose();
    } catch (err) {
      if (err instanceof IdentityApiError) {
        if (err.kind === 'network') {
          setGlobalError('We could not reach DeTour. Check your connection and try again.');
        } else if (err.code === 'VALIDATION_FAILED' || Object.keys(err.fields).length > 0) {
          setFieldErrors(err.fields);
          setGlobalError('Please correct the highlighted fields.');
        } else {
          setGlobalError(err.message || 'Could not create trip. Please try again.');
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
        aria-labelledby="create-trip-heading"
        ref={modalRef}
      >
        <div className="modal-header">
          <h2 id="create-trip-heading">Plan a new trip</h2>
          <button type="button" className="text-button" onClick={onClose} aria-label="Close dialog">
            ✕
          </button>
        </div>

        {globalError && (
          <div className="field-error" role="alert" style={{marginBottom: '1rem'}}>
            {globalError}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div className="field">
            <label htmlFor="trip-origin">Origin</label>
            <input id="trip-origin" value="Portland (PDX)" readOnly disabled />
          </div>

          <div className="field">
            <label htmlFor="trip-destination">Destination</label>
            <select
              id="trip-destination"
              value={destinationKey}
              onChange={(e) => setDestinationKey(e.target.value)}
              aria-describedby={fieldErrors.destinationKey ? 'trip-destination-error' : undefined}
            >
              {SUPPORTED_DESTINATIONS.map((dest) => (
                <option key={dest.key} value={dest.key}>
                  {dest.name}
                </option>
              ))}
            </select>
            {fieldErrors.destinationKey && (
              <p className="field-error" id="trip-destination-error">
                {fieldErrors.destinationKey}
              </p>
            )}
          </div>

          <div className="field-group">
            <div className="field">
              <label htmlFor="trip-start-date">Departure date</label>
              <input
                type="date"
                id="trip-start-date"
                value={startDate}
                min="2027-03-01"
                max="2027-03-31"
                onChange={(e) => setStartDate(e.target.value)}
                aria-describedby={fieldErrors.dates ? 'trip-dates-error' : 'trip-dates-hint'}
              />
            </div>
            <div className="field">
              <label htmlFor="trip-end-date">Return date</label>
              <input
                type="date"
                id="trip-end-date"
                value={endDate}
                min="2027-03-01"
                max="2027-03-31"
                onChange={(e) => setEndDate(e.target.value)}
                aria-describedby={fieldErrors.dates ? 'trip-dates-error' : undefined}
              />
            </div>
          </div>
          <p className="hint" id="trip-dates-hint">
            Travel must take place between March 1 and March 31, 2027 (1–14 nights).
          </p>
          {fieldErrors.dates && (
            <p className="field-error" id="trip-dates-error">
              {fieldErrors.dates}
            </p>
          )}

          <div className="field">
            <label htmlFor="trip-traveler-count">Travelers</label>
            <input
              type="number"
              id="trip-traveler-count"
              min="1"
              max="8"
              value={travelerCount}
              onChange={(e) => setTravelerCount(e.target.value)}
              aria-describedby={fieldErrors.travelerCount ? 'trip-traveler-count-error' : 'trip-traveler-count-hint'}
            />
            <p className="hint" id="trip-traveler-count-hint">
              1 to 8 travelers. Ages and budget can be configured after creation.
            </p>
            {fieldErrors.travelerCount && (
              <p className="field-error" id="trip-traveler-count-error">
                {fieldErrors.travelerCount}
              </p>
            )}
          </div>

          <div className="modal-actions">
            <button type="button" className="text-button" onClick={onClose} disabled={pending}>
              Cancel
            </button>
            <button type="submit" className="primary" disabled={pending}>
              {pending ? 'Creating…' : 'Create trip'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
