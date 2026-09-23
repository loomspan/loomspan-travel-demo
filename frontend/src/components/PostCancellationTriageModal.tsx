import {useEffect, useRef} from 'react';
import type {PlannedResponse} from '../api/tripsApi';

type PostCancellationTriageModalProps = {
  isOpen: boolean;
  plannedAlternatives: PlannedResponse[];
  pending: boolean;
  pendingAction?: 'use-alternative' | 'create-draft' | null;
  errorMessage?: string;
  onUseAlternative: (plannedId: string) => void;
  onCreateDraft: () => void;
  onClose: () => void;
};

export function PostCancellationTriageModal({
  isOpen,
  plannedAlternatives,
  pending,
  pendingAction,
  errorMessage,
  onUseAlternative,
  onCreateDraft,
  onClose,
}: PostCancellationTriageModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const previousActiveElement = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (isOpen) {
      previousActiveElement.current = document.activeElement as HTMLElement;
      // Focus first focusable interactive item
      const firstFocusable = modalRef.current?.querySelector<HTMLElement>(
        'button.triage-action-btn, button.text-button'
      );
      firstFocusable?.focus();
    } else if (previousActiveElement.current) {
      if (document.body.contains(previousActiveElement.current)) {
        previousActiveElement.current.focus();
      } else {
        const fallback = document.querySelector<HTMLElement>(
          '#workspace-heading, h1, h2'
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

  if (!isOpen) return null;

  return (
    <div
      className="modal-backdrop"
      onClick={(e) => {
        if (!pending && e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="modal card triage-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="triage-dialog-heading"
        tabIndex={-1}
        ref={modalRef}
      >
        <div className="modal-header">
          <h2 id="triage-dialog-heading">Reservation Canceled</h2>
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

        <div className="triage-success-banner" role="status" aria-live="polite">
          <span className="badge badge-success">Fee-free cancellation confirmed</span>
          <p className="triage-success-text">
            Your reservation has been canceled without any fees, and reserved items have been restored to catalog inventory. What would you like to do next?
          </p>
        </div>

        {errorMessage && (
          <div className="field-error" role="alert" style={{marginBottom: '1rem'}}>
            {errorMessage}
          </div>
        )}

        <div className="triage-options-grid">
          {/* Option 1: Use a saved alternative */}
          <section className="triage-option-card card" aria-labelledby="triage-saved-alt-heading">
            <h3 id="triage-saved-alt-heading" className="triage-option-title">
              1. Use a saved alternative
            </h3>
            <p className="hint">
              Copy a saved Planned itinerary into a new Draft to revalidate current pricing and availability before booking.
            </p>
            {plannedAlternatives.length === 0 ? (
              <p className="hint read-only-hint">No other Planned alternatives are saved on this trip.</p>
            ) : (
              <ul className="triage-alternatives-list">
                {plannedAlternatives.map((alt) => {
                  const s = alt.selections;
                  const summaryParts: string[] = [];
                  if (s?.airfare) summaryParts.push('Airfare');
                  if (s?.stay) summaryParts.push(s.stay.propertyName);
                  if (s?.rental) summaryParts.push(s.rental.vehicleClassName);
                  return (
                    <li key={alt.id} className="triage-alternative-item">
                      <div className="triage-alt-info">
                        <strong>Planned ({alt.id.slice(0, 8)}…)</strong>
                        <span className="triage-alt-components">
                          {summaryParts.length > 0 ? summaryParts.join(' • ') : 'No components'}
                        </span>
                      </div>
                      <button
                        type="button"
                        className="secondary-action-button triage-action-btn use-alternative-btn"
                        onClick={() => onUseAlternative(alt.id)}
                        disabled={pending}
                      >
                        {pending && pendingAction === 'use-alternative'
                          ? 'Copying to draft…'
                          : 'Use this alternative'}
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          {/* Option 2: Create a new Draft */}
          <section className="triage-option-card card" aria-labelledby="triage-new-draft-heading">
            <h3 id="triage-new-draft-heading" className="triage-option-title">
              2. Create a new Draft
            </h3>
            <p className="hint">
              Start building a fresh alternative with empty flight, stay, and car slots.
            </p>
            <button
              type="button"
              className="primary-button triage-action-btn create-new-draft-btn"
              onClick={onCreateDraft}
              disabled={pending}
            >
              {pending && pendingAction === 'create-draft' ? 'Creating draft…' : 'Create a new Draft'}
            </button>
          </section>

          {/* Option 3: Done for now */}
          <section className="triage-option-card card" aria-labelledby="triage-done-heading">
            <h3 id="triage-done-heading" className="triage-option-title">
              3. Done for now
            </h3>
            <p className="hint">
              Keep your trip active in your profile and return to planning whenever you are ready.
            </p>
            <button
              type="button"
              className="text-button triage-action-btn done-for-now-btn"
              onClick={onClose}
              disabled={pending}
            >
              Done for now
            </button>
          </section>
        </div>
      </div>
    </div>
  );
}
