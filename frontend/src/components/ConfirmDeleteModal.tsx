import {useEffect, useRef} from 'react';

export type DeleteTarget =
  | {
      kind: 'draft';
      tripId: string;
      draftId: string;
      draftVersion: number;
    }
  | {
      kind: 'planned';
      tripId: string;
      alternativeId: string;
    }
  | {
      kind: 'trip';
      tripId: string;
      label: string;
      draftCount: number;
      plannedCount: number;
      hasBookingHistory: boolean;
    };

type ConfirmDeleteModalProps = {
  isOpen: boolean;
  target: DeleteTarget | null;
  pending: boolean;
  errorMessage?: string;
  onClose: () => void;
  onConfirm: () => void;
};

export function ConfirmDeleteModal({
  isOpen,
  target,
  pending,
  errorMessage,
  onClose,
  onConfirm,
}: ConfirmDeleteModalProps) {
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
          '#upcoming-trips-heading, #alternatives-heading, #profile-heading, #empty-heading, h1, h2'
        );
        fallback?.focus();
      }
      previousActiveElement.current = null;
    }
    return () => {
      const previous = previousActiveElement.current;
      if (!previous) return;
      if (document.body.contains(previous)) previous.focus();
      else document.querySelector<HTMLElement>('#workspace-heading, #profile-heading, #home-heading, h1, h2')?.focus();
      previousActiveElement.current = null;
    };
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    if (pending) modalRef.current?.focus();
    else if (document.activeElement === modalRef.current) modalRef.current?.querySelector<HTMLElement>('button:not(:disabled)')?.focus();
  }, [isOpen, pending]);

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
          'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"]):not(:disabled)'
        );
        if (!focusable.length) { e.preventDefault(); return; }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (!Array.from(focusable).includes(document.activeElement as HTMLElement)) {
          e.preventDefault();
          (e.shiftKey ? last : first).focus();
        } else if (e.shiftKey && document.activeElement === first) {
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

  if (!isOpen || !target) return null;

  const isTrip = target.kind === 'trip';
  const isBookedTrip = isTrip && target.hasBookingHistory;

  let title = 'Confirm deletion';
  let description = 'Are you sure you want to delete this item?';
  let confirmLabel = 'Delete';

  if (target.kind === 'draft') {
    title = 'Delete draft alternative';
    description = 'Permanently delete this draft alternative? Other alternatives will remain.';
    confirmLabel = 'Delete draft';
  } else if (target.kind === 'planned') {
    title = 'Delete planned itinerary';
    description = 'Permanently delete this planned itinerary snapshot? This action cannot be undone.';
    confirmLabel = 'Delete planned itinerary';
  } else if (target.kind === 'trip') {
    title = `Delete ${target.label}`;
    if (isBookedTrip) {
      description = 'Trips with booking history cannot be permanently deleted.';
    } else {
      description = `Permanently delete ${target.label} and all its contents? ${target.draftCount} Draft alternative(s) and ${target.plannedCount} Planned itinerary(ies) will be removed.`;
    }
    confirmLabel = 'Delete trip';
  }

  return (
    <div className="modal-backdrop" onClick={(e) => { if (!pending && e.target === e.currentTarget) onClose(); }}>
      <div
        className="modal card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-delete-heading"
        tabIndex={-1}
        ref={modalRef}
      >
        <div className="modal-header">
          <h2 id="confirm-delete-heading">{title}</h2>
          <button type="button" className="text-button" onClick={onClose} aria-label="Close dialog" disabled={pending}>
            ✕
          </button>
        </div>

        <p className="delete-description">{description}</p>

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
            Cancel
          </button>
          <button
            type="button"
            className="primary delete-confirm-button"
            onClick={onConfirm}
            disabled={pending || isBookedTrip}
          >
            {pending ? 'Deleting…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
