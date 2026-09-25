import type {ReactNode} from 'react';

type ActionIconName = 'home' | 'profile' | 'trip' | 'airfare' | 'stay' | 'logout';

const paths: Record<ActionIconName, ReactNode> = {
  home: <><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z"/><path d="M9 21v-7h6v7"/></>,
  profile: <><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></>,
  trip: <><rect x="4" y="6" width="16" height="15" rx="2"/><path d="M9 6V4a3 3 0 0 1 6 0v2M4 12h16"/></>,
  airfare: <path d="m2 15 8-2 7-10 3 1-4 11 5 3-1 2-7-1-4 3-2-1 2-5-7 1z"/>,
  stay: <><path d="M3 21V6l9-4 9 4v15M3 21h18M9 21v-6h6v6M8 9h.01M16 9h.01"/></>,
  logout: <><path d="M10 3H4v18h6M14 7l5 5-5 5M8 12h11"/></>,
};

export function ActionIcon({name}: {name: ActionIconName}) {
  return <svg className="action-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{paths[name]}</svg>;
}
