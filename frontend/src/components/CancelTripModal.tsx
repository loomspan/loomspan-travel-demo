import {useEffect, useRef} from 'react';

type CancelTripModalProps = {
  isOpen: boolean;
  tripLabel: string;
  hasActiveBooking?: boolean;
  pending: boolean;
  errorMessage?: string;
  onClose: () => void;
  onConfirm: () => void;
};

export function CancelTripModal({
  isOpen,
  tripLabel,
  hasActiveBooking = false,
  pending,
  errorMessage,
  onClose,
  onConfirm,
}: CancelTripModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const previousActiveElement = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (isOpen) {
      previousActiveElement.current = document.activeElement as HTMLElement;
      const cancelButton = modalRef.current?.querySelector<HTMLElement>('button.cancel-button');
      cancelButton?.focus();
    } else if (previousActiveElement.current) {
      if (document.body.contains(previousActiveElement.current)) {
        previousActiveElement.current.focus();
      } else {
        const fallback = document.querySelector<HTMLElement>(
          '#workspace-heading, #upcoming-trips-heading, h1, h2'
        );
        fallback?.focus();
      }
      previousActiveElement.current = null;
    }
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (pending) return;
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
  }, [isOpen, pending, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className="modal-backdrop"
      onClick={(e) => {
        if (!pending && e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="modal card cancel-trip-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cancel-trip-heading"
        ref={modalRef}
      >
        <div className="modal-header">
          <h2 id="cancel-trip-heading">Cancel Trip</h2>
          <button
            type="button"
            className="text-button"
            onClick={onClose}
            aria-label="Close dialog"
            disabled={pending}
          >
            ✕
          </button>
        </div>

        <div className="cancellation-notice-box">
          <p className="cancellation-summary">
            Are you sure you want to cancel the trip <strong>{tripLabel}</strong>?
          </p>

          <ul className="cancellation-details-list">
            {hasActiveBooking && (
              <li>
                <strong>Active reservation released:</strong> Any active booking will be released immediately without any cancellation fees.
              </li>
            )}
            <li>
              <strong>Booking history permanently retained:</strong> All past reservations, confirmation codes, and cancellation timestamps will be saved permanently.
            </li>
            <li>
              <strong>Alternatives become read-only:</strong> All Draft and Planned itineraries on this trip will be locked and cannot be edited or booked.
            </li>
            <li>
              <strong>Duplication available:</strong> You can duplicate this trip into a fresh travel plan at any time to revise dates, destinations, or itineraries.
            </li>
          </ul>
        </div>

        {errorMessage && (
          <div className="field-error" role="alert" style={{marginBottom: '1rem'}}>
            {errorMessage}
          </div>
        )}

        <div className="modal-actions">
          <button
            type="button"
            className="text-button cancel-button"
            onClick={onClose}
            disabled={pending}
          >
            Keep Trip Active
          </button>
          <button
            type="button"
            className="primary danger-button cancel-trip-confirm-btn"
            onClick={onConfirm}
            disabled={pending}
          >
            {pending ? 'Canceling trip…' : 'Cancel Trip'}
          </button>
        </div>
      </div>
    </div>
  );
}
