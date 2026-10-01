export type ScreenDestination = 'home' | 'trips' | 'profile' | 'auth';
export type WorkspaceView = 'workspace' | 'compare' | 'booking-review' | 'booking-confirmation';
export type ScreenDetails = {
  planningMode?: 'PLAN_TRIP' | 'AIRFARE' | 'STAY';
  workspaceView?: WorkspaceView;
  comparedOptionIds?: string[];
  reviewOptionId?: string;
  reviewReturnView?: 'workspace' | 'compare';
};

export type ScreenHistoryState = ScreenDetails & {
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

export function rememberScreen(destination: ScreenDestination, tripId?: string, replace = false, details: ScreenDetails = {}) {
  const next: ScreenHistoryState = {detourScreen: true, destination, ...(tripId ? {tripId} : {}), ...details};
  const current = currentScreen();
  if (JSON.stringify(current) === JSON.stringify(next)) return;
  if (replace) window.history.replaceState(next, '');
  else window.history.pushState(next, '');
}
