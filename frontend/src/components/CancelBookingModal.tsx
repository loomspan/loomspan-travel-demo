import {useEffect, useRef} from 'react';

type CancelBookingModalProps = {
  isOpen: boolean;
  tripLabel: string;
  bookingReference: string;
  pending: boolean;
  errorMessage?: string;
  onClose: () => void;
  onConfirm: () => void;
};

export function CancelBookingModal({
  isOpen,
  tripLabel,
  bookingReference,
  pending,
  errorMessage,
  onClose,
  onConfirm,
}: CancelBookingModalProps) {
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
          '#workspace-heading, #active-booking-heading, h1, h2'
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
        className="modal card cancel-booking-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cancel-booking-heading"
        ref={modalRef}
      >
        <div className="modal-header">
          <h2 id="cancel-booking-heading">Cancel Booking</h2>
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
            Are you sure you want to cancel the active reservation for <strong>{tripLabel}</strong> (Reference:{' '}
            <strong className="ref-code">{bookingReference}</strong>)?
          </p>

          <ul className="cancellation-details-list">
            <li>
              <strong>Fee-free cancellation:</strong> This fictional reservation will be canceled without any cancellation fees or penalties.
            </li>
            <li>
              <strong>Inventory release:</strong> All reserved flight seats, hotel rooms, and rental vehicles will be released back to catalog inventory immediately.
            </li>
            <li>
              <strong>Booking history retained:</strong> The booking reference and full cancellation record will be preserved in your Trip history for future reference.
            </li>
            <li>
              <strong>Trip remains active:</strong> Your trip remains open and active for planning, allowing you to select an alternative or start a new draft.
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
            Keep Reservation
          </button>
          <button
            type="button"
            className="primary danger-button cancel-booking-confirm-btn"
            onClick={onConfirm}
            disabled={pending}
          >
            {pending ? 'Canceling reservation…' : 'Cancel Booking'}
          </button>
        </div>
      </div>
    </div>
  );
}
