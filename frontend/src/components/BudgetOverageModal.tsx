import {useEffect, useRef, useState} from 'react';
import {formatCents} from './ItinerarySummaryTally';

export type BudgetOverageModalProps = {
  isOpen: boolean;
  budgetCents: number;
  grandTotalCents: number;
  budgetOverageCents: number;
  pending: boolean;
  errorMessage?: string;
  onClose: () => void;
  onConfirm: () => void;
};

export function BudgetOverageModal({
  isOpen,
  budgetCents,
  grandTotalCents,
  budgetOverageCents,
  pending,
  errorMessage,
  onClose,
  onConfirm,
}: BudgetOverageModalProps) {
  const [acknowledged, setAcknowledged] = useState(false);
  const modalRef = useRef<HTMLDivElement>(null);
  const previousActiveElement = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (isOpen) {
      setAcknowledged(false);
      previousActiveElement.current = document.activeElement as HTMLElement;
      // Focus cancel button or modal
      const cancelButton = modalRef.current?.querySelector<HTMLElement>('button.cancel-button');
      cancelButton?.focus();
    } else if (previousActiveElement.current) {
      if (document.body.contains(previousActiveElement.current)) {
        previousActiveElement.current.focus();
      } else {
        const fallback = document.querySelector<HTMLElement>(
          '#builder-heading, #alternatives-heading, h1, h2, button'
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
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key === 'Tab' && modalRef.current) {
        const focusable = Array.from(modalRef.current.querySelectorAll<HTMLElement>('*')).filter((el) =>
          el.matches(
            'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"]):not(:disabled)'
          )
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

  return (
    <div className="modal-backdrop" role="presentation">
      <div
        ref={modalRef}
        className="modal budget-overage-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="overage-modal-heading"
        aria-describedby="overage-modal-description"
      >
        <div className="modal-header">
          <h3 id="overage-modal-heading">Trip Budget Overage Warning</h3>
        </div>

        <div className="modal-body">
          <p id="overage-modal-description" className="modal-description">
            This itinerary exceeds your overall trip budget. Please review the financial breakdown below before saving as a planned itinerary.
          </p>

          <div className="budget-overage-breakdown">
            <div className="breakdown-row">
              <span className="breakdown-label">Trip Budget:</span>
              <span className="breakdown-value">{formatCents(budgetCents)}</span>
            </div>
            <div className="breakdown-row">
              <span className="breakdown-label">Itinerary Grand Total:</span>
              <span className="breakdown-value">{formatCents(grandTotalCents)}</span>
            </div>
            <div className="breakdown-row breakdown-overage" role="alert">
              <span className="breakdown-label">Budget Overage:</span>
              <span className="breakdown-value overage-highlight">+{formatCents(budgetOverageCents)}</span>
            </div>
          </div>

          {errorMessage && (
            <div className="alert alert-error" role="alert">
              {errorMessage}
            </div>
          )}

          <div className="acknowledgment-checkbox-container">
            <label className="checkbox-label" htmlFor="budget-overage-ack">
              <input
                id="budget-overage-ack"
                type="checkbox"
                checked={acknowledged}
                onChange={(e) => setAcknowledged(e.target.checked)}
                disabled={pending}
              />
              <span>I understand this itinerary exceeds my overall trip budget</span>
            </label>
          </div>
        </div>

        <div className="modal-actions">
          <button
            type="button"
            className="secondary-action-button cancel-button"
            onClick={onClose}
            disabled={pending}
          >
            Cancel
          </button>
          <button
            type="button"
            className="primary-button confirm-button"
            onClick={onConfirm}
            disabled={!acknowledged || pending}
          >
            {pending ? 'Saving planned itinerary…' : 'Confirm and Save as Planned'}
          </button>
        </div>
      </div>
    </div>
  );
}
