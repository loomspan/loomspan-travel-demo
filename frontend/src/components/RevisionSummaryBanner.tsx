import type {RevisionSummaryResponse} from '../api/tripsApi';

type RevisionSummaryBannerProps = {
  summary: RevisionSummaryResponse;
  onDismiss: () => void;
};

export function RevisionSummaryBanner({summary, onDismiss}: RevisionSummaryBannerProps) {
  const hasRemovals = summary.removals && summary.removals.length > 0;
  const hasAdjustments = summary.adjustments && summary.adjustments.length > 0;

  if (!hasRemovals && !hasAdjustments) return null;

  return (
    <aside className="revision-summary-banner" role="region" aria-labelledby="revision-summary-heading">
      <div className="revision-summary-header">
        <h3 id="revision-summary-heading">Revision changes applied</h3>
        <button type="button" className="text-button" onClick={onDismiss} aria-label="Dismiss revision summary">
          ✕ Dismiss
        </button>
      </div>
      <p className="hint">
        When updating shared trip details or duplicating planned itineraries, some selections were adjusted or removed:
      </p>

      {hasRemovals && (
        <div className="revision-removals">
          <h4>Removed selections:</h4>
          <ul>
            {summary.removals.map((removal, index) => (
              <li key={index}>
                <strong>{removal.component}:</strong> {removal.reason}
              </li>
            ))}
          </ul>
        </div>
      )}

      {hasAdjustments && (
        <div className="revision-adjustments">
          <h4>Adjusted selections:</h4>
          <ul>
            {summary.adjustments.map((adj, index) => (
              <li key={index}>
                <strong>{adj.component}:</strong> {adj.reason}
                {adj.previousUnitCount !== null && adj.newUnitCount !== null && (
                  <span> (units: {adj.previousUnitCount} → {adj.newUnitCount})</span>
                )}
                {adj.previousPriceCents !== null && adj.newPriceCents !== null && (
                  <span> (price: ${(adj.previousPriceCents / 100).toFixed(2)} → ${(adj.newPriceCents / 100).toFixed(2)})</span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </aside>
  );
}
