import {useEffect, useRef} from 'react';

type ConfirmRemoveModalProps = {
  isOpen: boolean;
  componentTitle: string;
  formattedPrice: string;
  pending: boolean;
  errorMessage?: string;
  onClose: () => void;
  onConfirm: () => void;
};

export function ConfirmRemoveModal({
  isOpen,
  componentTitle,
  formattedPrice,
  pending,
  errorMessage,
  onClose,
  onConfirm,
}: ConfirmRemoveModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const previousActiveElement = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (isOpen) {
      previousActiveElement.current = document.activeElement as HTMLElement;
      // Focus first focusable button
      const firstBtn = modalRef.current?.querySelector<HTMLElement>('button');
      firstBtn?.focus();
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

  if (!isOpen) return null;

  return (
    <div
      className="modal-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget && !pending) onClose();
      }}
    >
      <div
        className="modal card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-remove-heading"
        tabIndex={-1}
        ref={modalRef}
      >
        <div className="modal-header">
          <h2 id="confirm-remove-heading">Remove {componentTitle}?</h2>
          <button
            type="button"
            className="text-button"
            onClick={onClose}
            disabled={pending}
            aria-label="Close dialog"
          >
            ✕
          </button>
        </div>

        {errorMessage && (
          <div className="field-error" role="alert" style={{marginBottom: '1rem'}}>
            {errorMessage}
          </div>
        )}

        <p className="confirm-modal-body">
          Are you sure you want to remove this {componentTitle.toLowerCase()}? This will discard
          the saved option totaling {formattedPrice} from your itinerary.
        </p>

        <div className="modal-actions">
          <button
            type="button"
            className="text-button"
            onClick={onClose}
            disabled={pending}
          >
            Cancel
          </button>
          <button
            type="button"
            className="primary danger-button"
            onClick={onConfirm}
            disabled={pending}
          >
            {pending ? 'Removing…' : `Remove ${componentTitle.toLowerCase()}`}
          </button>
        </div>
      </div>
    </div>
  );
}
