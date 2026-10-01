/** Restore a removed trip control to a visible heading in the current view. */
export function focusTripDialogFallback() {
  const headingIds = ['upcoming-trips-heading', 'past-trips-heading', 'canceled-trips-heading',
    'workspace-heading', 'alternatives-heading', 'trips-heading', 'profile-heading', 'home-heading', 'empty-heading'];
  const visible = (element: HTMLElement | null): element is HTMLElement => Boolean(element && !element.closest('[hidden]'));
  const knownHeading = headingIds.map(id => document.getElementById(id)).find(visible);
  const fallback = knownHeading ?? Array.from(document.querySelectorAll<HTMLElement>('h1, h2')).find(visible);
  fallback?.focus();
}
