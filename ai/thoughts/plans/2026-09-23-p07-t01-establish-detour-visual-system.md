# DeTour Visual System Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-23-p07-t01-establish-detour-visual-system.md`
- Research: `ai/thoughts/research/2026-09-23-p07-t01-establish-detour-visual-system.md`
- Outcome: One teal-led visual and terminology system from authentication through booking history.

## Current State
`frontend/src/style.css` defines one-off screen palettes, with shared Georgia headings only partly applied. `AuthScreen.tsx` and `ProfileScreen.tsx` show the wordmark, while `TripWorkspace.tsx` uses a different eyebrow. `ItinerarySummaryTally.tsx`, comparison, review, confirmation, and history have inconsistent labels for totals and missing components. Existing actions and API behavior are covered by frontend tests.

## Desired End State
The wordmark, typography, spacing, fields, buttons, cards, focus ring, badges, warnings, and state feedback form a recognizable system. Lifecycle and component labels are consistent; missing components say “Not selected” and remain distinct from `$0.00`. Costs explicitly indicate USD. Entry-specific reveal, booking, cancellation, and confirmation behavior remain as tested.

## Scope
### In scope
- Frontend CSS tokens and component styling.
- Text presentation for lifecycle, component, and money labels.
- Focused regression assertions for those labels and preserved workflows.

### Out of scope
- Navigation/layout restructuring (P07-T02), final marketing/disclosure copy (P07-T03), complete dialog audit (P07-T04), new logo, dark mode, backend rules.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
CSS changes reach every screen and must preserve responsive layouts and visible focus. Terminology edits can break accessible labels and tests. Money changes must remain display-only; the server tally and deterministic calculations must stay intact. Component absence must not become a numeric zero. The existing modal action labels and scope must remain distinct.

## Implementation Approach
Use CSS custom properties in `style.css` for a teal/ink/surface palette, spacing, typography, and state colors. Normalize existing class families instead of rebuilding component structure. Give the existing text wordmark one reusable class and place it in the workspace heading. Standardize display labels in the existing components, keeping model/API identifiers untouched. A separate logo or behavior abstraction adds no value here.

## Phase 1: Shared visual language
### Changes
- [x] `frontend/src/style.css` — add palette, type, spacing, action, field, focus, card, badge, state, and warning rules; normalize formerly blue/purple state treatments to teal-led variants while keeping destructive/overage distinct.
- [x] `frontend/src/components/AuthScreen.tsx`, `ProfileScreen.tsx`, `TripWorkspace.tsx` — apply one wordmark class without altering heading focus or entry mode.
### Automated verification
- [x] `npm run build` in `frontend` — TypeScript/CSS bundle succeeds.
### Optional developer checks
- [ ] Inspect narrow and wide screens for spacing, focus, warning, and card hierarchy.

## Phase 2: Terminology and money presentation
### Changes
- [x] `frontend/src/components/TripListSection.tsx`, `AlternativeCard.tsx`, `BookingHistorySection.tsx` — align Trip, Draft, Planned itinerary, Booking, Canceled Booking, Expired, and Canceled Trip display names.
- [x] `frontend/src/components/ItinerarySummaryTally.tsx`, `ItineraryComparisonView.tsx`, `BookingReviewView.tsx`, `BookingConfirmationView.tsx` — align Airfare/Stay/Rental Car, component total, grand total, budget labels; show explicit missing text and USD context while retaining authoritative values.
- [x] `frontend/src/*.test.tsx`, `frontend/src/components/*.test.tsx` — assert key visual-system text and preserved entry, booking, and cancellation outcomes.
### Automated verification
- [x] `npm test` in `frontend` — relevant and full mocked UI suite passes.
- [x] `npm run build` in `frontend` — production bundle passes.
### Optional developer checks
- [ ] Inspect actual rendered screens at representative widths; no live external service needed.

## Test Strategy
Add focused assertions for wordmark, state text, missing versus zero, USD context, and labels; reuse mocked server responses. Existing workflow tests provide regression coverage for reveal and server-backed actions. CSS should be checked for selector consistency and optionally viewed in a browser, since Vitest does not evaluate visual appearance.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Shared identity | CSS tokens and wordmark in public/profile/workspace | App and workspace rendering assertions; build |
| Actions and states readable without color | action, badge, status, warning CSS plus text | status/action text assertions and existing workflow tests |
| Consistent lifecycle/components/money | list, cards, tally, comparison, review, confirmation, history | focused labels/missing/zero tests and booking tests |
| Progressive entry and server-backed results | preserve entry handlers and API calls | existing progressive, booking, cancellation suites |

## Risks and Rollback/Recovery
CSS reach is broad; isolate via variables and existing class names so individual treatments can be adjusted. Revert only ticket hunks if needed. No migration or data action is involved.

## References
Ticket and research above; `frontend/src/style.css`; `frontend/src/components/TripWorkspace.tsx`; `frontend/src/components/ItinerarySummaryTally.tsx`.
