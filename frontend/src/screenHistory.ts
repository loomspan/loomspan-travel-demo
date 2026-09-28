export type ScreenDestination = 'home' | 'trips' | 'profile' | 'auth';

export type ScreenHistoryState = {
  detourScreen: true;
  destination: ScreenDestination;
  tripId?: string;
};

export function currentScreen(): ScreenHistoryState | null {
  const state: unknown = window.history.state;
  if (!state || typeof state !== 'object' || !('detourScreen' in state) || state.detourScreen !== true ||
      !('destination' in state) || !['home', 'trips', 'profile', 'auth'].includes(String(state.destination))) return null;
  return state as ScreenHistoryState;
}

export function rememberScreen(destination: ScreenDestination, tripId?: string, replace = false) {
  const next: ScreenHistoryState = {detourScreen: true, destination, ...(tripId ? {tripId} : {})};
  const current = currentScreen();
  if (current?.destination === destination && current.tripId === tripId) return;
  if (replace) window.history.replaceState(next, '');
  else window.history.pushState(next, '');
}
