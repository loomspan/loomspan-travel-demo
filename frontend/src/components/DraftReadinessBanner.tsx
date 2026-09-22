type DraftReadinessBannerProps = {
  issues: Record<string, string>;
  onJumpTo: (key: string) => void;
  onDismiss: () => void;
};

function formatIssueLabel(key: string): string {
  switch (key) {
    case 'travelerAges':
      return 'traveler ages';
    case 'adult':
      return 'adult traveler requirement';
    case 'budgetCents':
      return 'trip budget';
    case 'components':
      return 'component selections';
    case 'airfare':
      return 'airfare selection';
    case 'stay':
      return 'stay selection';
    case 'rental':
      return 'rental car selection';
    case 'destination':
      return 'destination';
    case 'dates':
      return 'trip dates';
    case 'travelerCount':
      return 'traveler count';
    default:
      return key.replace(/([A-Z])/g, ' $1').toLowerCase();
  }
}

export function DraftReadinessBanner({
  issues,
  onJumpTo,
  onDismiss,
}: DraftReadinessBannerProps) {
  const issueEntries = Object.entries(issues);
  if (issueEntries.length === 0) return null;

  return (
    <div
      className="alert alert-warning readiness-banner"
      role="region"
      aria-labelledby="readiness-banner-heading"
    >
      <div className="readiness-banner-header">
        <h4 id="readiness-banner-heading" className="readiness-banner-title">
          Draft Not Ready to Save as Planned
        </h4>
        <button
          type="button"
          className="text-button readiness-dismiss-btn"
          onClick={onDismiss}
          aria-label="Dismiss readiness issues"
        >
          Dismiss
        </button>
      </div>
      <p className="readiness-banner-intro">
        Please resolve the following issue{issueEntries.length === 1 ? '' : 's'} before saving this draft as a planned itinerary:
      </p>
      <ul className="readiness-issues-list">
        {issueEntries.map(([key, message]) => (
          <li key={key} className="readiness-issue-item">
            <span className="readiness-issue-message">{message}</span>
            <button
              type="button"
              className="secondary-action-button readiness-jump-button"
              onClick={() => onJumpTo(key)}
              aria-label={`Fix issue for ${formatIssueLabel(key)}`}
            >
              Fix issue
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
