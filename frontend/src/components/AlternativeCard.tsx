import type {AlternativeResponse} from '../api/tripsApi';

type AlternativeCardProps = {
  alternative: AlternativeResponse;
  tripExpired?: boolean;
  onDuplicateDraft: (draftId: string, version: number) => void;
  onDuplicatePlanned: (alternativeId: string) => void;
  onDeleteDraft: (draftId: string, version: number) => void;
  onDeletePlanned: (alternativeId: string) => void;
};

export function AlternativeCard({
  alternative,
  tripExpired = false,
  onDuplicateDraft,
  onDuplicatePlanned,
  onDeleteDraft,
  onDeletePlanned,
}: AlternativeCardProps) {
  const isDraft = alternative.lifecycle.toUpperCase() === 'DRAFT';
  const isPlanned = alternative.lifecycle.toUpperCase() === 'PLANNED';
  const selections = alternative.selections;
  const hasSelections = Boolean(
    selections && (selections.airfare || selections.stay || selections.rental)
  );

  return (
    <article
      className={`card alternative-card ${isPlanned ? 'alternative-card-planned' : 'alternative-card-draft'}`}
      aria-labelledby={`alt-heading-${alternative.id}`}
    >
      <div className="alternative-card-header">
        <div>
          <div className="badge-row">
            <span className={`badge ${isDraft ? 'badge-draft' : 'badge-planned'}`}>
              {isDraft
                ? alternative.version !== null
                  ? `Draft v${alternative.version}`
                  : 'Draft'
                : 'Planned itinerary (read-only)'}
            </span>
            {tripExpired && <span className="badge badge-expired">Expired</span>}
          </div>
          <h4 id={`alt-heading-${alternative.id}`} className="alternative-card-title">
            {isDraft ? 'Draft alternative' : 'Planned itinerary snapshot'}
          </h4>
          <span className="alternative-id">ID: {alternative.id.slice(0, 8)}…</span>
        </div>
      </div>

      <div className="alternative-card-content">
        {isPlanned && (
          <p className="hint read-only-hint">
            This planned alternative is snapshot-locked and read-only. Use &ldquo;Duplicate to draft&rdquo; to make modifications.
          </p>
        )}

        {hasSelections ? (
          <div className="alternative-selections">
            {selections.airfare && (
              <div className="selection-item">
                <strong>Airfare:</strong> {selections.airfare.outboundDescription} / {selections.airfare.returnDescription}
              </div>
            )}
            {selections.stay && (
              <div className="selection-item">
                <strong>Stay:</strong> {selections.stay.propertyName} ({selections.stay.unitName})
              </div>
            )}
            {selections.rental && (
              <div className="selection-item">
                <strong>Rental:</strong> {selections.rental.vehicleClassName} at {selections.rental.locationName}
              </div>
            )}
          </div>
        ) : (
          <p className="hint">No components selected yet.</p>
        )}
      </div>

      <div className="alternative-card-actions">
        {isDraft ? (
          <>
            <button
              type="button"
              className="text-button delete-button"
              onClick={() => onDeleteDraft(alternative.id, alternative.version ?? 0)}
              aria-label={`Delete draft ${alternative.id.slice(0, 8)}`}
            >
              Delete draft
            </button>
            <button
              type="button"
              className="secondary-action-button"
              onClick={() => onDuplicateDraft(alternative.id, alternative.version ?? 0)}
              aria-label={`Duplicate draft ${alternative.id.slice(0, 8)}`}
            >
              Duplicate to new draft
            </button>
          </>
        ) : (
          <>
            <button
              type="button"
              className="text-button delete-button"
              onClick={() => onDeletePlanned(alternative.id)}
              aria-label={`Delete planned itinerary ${alternative.id.slice(0, 8)}`}
            >
              Delete planned itinerary
            </button>
            <button
              type="button"
              className="secondary-action-button"
              onClick={() => onDuplicatePlanned(alternative.id)}
              aria-label={`Duplicate planned itinerary ${alternative.id.slice(0, 8)} to draft`}
            >
              Duplicate to draft
            </button>
          </>
        )}
      </div>
    </article>
  );
}
