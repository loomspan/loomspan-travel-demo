# Working Plan and Saved Options Presentation Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-25-pr5-present-working-plan-and-saved-options.md`
- Research: `ai/thoughts/research/2026-09-25-pr5-present-working-plan-and-saved-options.md`
- Outcome: Make the Trips list, open Working plan, Saved options, comparison, and follow-up flows use named, individually dated options with clear copy and update effects.

## Current State
`TripWorkspace` edits `trip.drafts[0]` and already calls save, update, and load option endpoints. It still renders `trip.alternatives` in one Draft/Planned grid, exposes IDs and technical versions, and puts save/update behind one name field. `TripListSection` mixes Draft/Planned counts with named option summaries. `ItineraryComparisonView` uses numbered itineraries and the Working date range. `TripService.updateOption` replaces name, dates, and components together, so it cannot safely implement rename only. `TripService.changeWorkingDates` and shared-detail replacement return a `revisionSummary` after revalidation; there is no option preview endpoint.

## Desired End State
The Trip name heads the workspace. One editable Working plan shows its dates, shared travelers, selected components, tally, and honest save state. Saved options appear in their own section with each name, dates, components, tally, and option-specific expired/booked state. Copying an option explains the Working replacement and preserves the original. Updating an unbooked option requires confirmation with the proposed dates and component/price differences; saving as new remains separate. Rename changes the option name without altering its snapshot. Comparison and list cards show option-specific facts. Existing booking and cancellation paths remain reachable and understandable.

## Scope
### In scope
- Workspace, list, card, comparison, status and confirmation copy, responsive layout, focus and accessible names.
- Small rename-only option endpoint using existing authorization, version, validation, and repository methods.
- Focused frontend and API tests.

### Out of scope
- Changing the persisted Working/Saved lifecycle, catalog inventory, public Home, revision history, or option-specific destination/travelers.
- Removing legacy compatibility endpoints or response aliases used by older callers.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
The visible UI has multiple old Draft/Planned controls, including cancellation follow-up, so remove or relabel them consistently without breaking booking routes. Save status must distinguish local dirty data and failed mutations from persisted changes; a transient success message must never hide an unresolved failure. Option rename must check ownership, Trip active state, Trip and option versions, name validation, and booked immutability while preserving dates, selections, and booking references. Option update already snapshots current Working state, so confirming the current facts before the PUT provides review without inventing a speculative server preview. Server revalidation summaries are authoritative for Working date changes and must remain visible until acknowledged; changing the date itself has already persisted at that point.

## Implementation Approach
Use `workingPlan ?? drafts[0]` and `savedOptions ?? planned` as the presentation data, with `alternatives` only where legacy booking interfaces need it. Refactor `AlternativeCard` into a Saved-option presentation or replace its invocation with an option-specific card; do not show a second Working card. Keep the existing save/update/load calls and guard them with explicit name, component, dirty-state, booked-state, and confirmation checks. Add `PUT /api/trips/{tripId}/options/{optionId}/name` because the existing update method would overwrite a snapshot during rename. The rename endpoint uses `TripRepository.renameOption` and the existing version advance. Update confirmation compares the currently loaded option with the current Working snapshot and includes any retained date-revalidation summary. In response to a version conflict, require reload and re-review before submitting. Do not clear a revision summary automatically on unrelated success.

## Phase 1: Separate Working and Saved presentation
### Changes
- [x] `frontend/src/components/TripWorkspace.tsx` — heading from `trip.name ?? trip.label`; render one Working builder with Working dates, shared traveler ages/count, selections and tally; render only `savedOptions ?? planned` below under Saved options with a count and empty state. Remove Draft creation, duplication, promotion, and ID/version terminology from visible primary controls, while retaining required legacy code paths for other flows.
- [x] `frontend/src/components/AlternativeCard.tsx` — present each Saved option's name, own dates, selected component summary, tally, and own expired/booked status; use named accessible action labels and omit technical ID. Keep booking and copy actions available, including booked copy.
- [x] `frontend/src/components/TripListSection.tsx` — show Trip name, destination, labeled Working status/dates, Saved-option count and each option's own name/dates/status; omit Draft/Planned counts and Working entry from the Saved list.
- [x] `frontend/src/style.css` — preserve readable card/actions at desktop and narrow breakpoints, with no horizontal overflow of option names or status.
- [x] `frontend/src/App.test.tsx`, `frontend/src/DraftPromotion.test.tsx` — assert exactly one Working area, separate named Saved options, incomplete Working editing, and absence of old visible controls.
### Automated verification
- [x] `npm test -- --run App.test.tsx DraftPromotion.test.tsx` from `frontend` — list/workspace tests pass.
### Optional developer checks
- [ ] At narrow phone width, inspect action wrapping and long names.

## Phase 2: Clarify option mutations and durable status
### Changes
- [x] `src/main/java/app/detour/trip/TripRequests.java`, `TripController.java`, `TripService.java` — add rename-only request/route/service operation with validated name, active Trip and owner checks, booked-option rejection, optimistic Trip/option versions, and `renameOption`; return refreshed Trip without replacing snapshots.
- [x] `frontend/src/api/tripsApi.ts` — add typed rename-only request/method; keep save/update/load contract intact.
- [x] `frontend/src/components/TripWorkspace.tsx` — separate name entry for new option, in-context rename, and update target; disable Save as new option with explanation when all Working selections are empty; request explicit update confirmation showing target, own dates, selected component and price differences, and the retained revision summary before PUT. State whether an action replaces an unbooked option or keeps both. Prevent update of a booked target while still allowing copy into Working. Keep failed/expired-session and dirty-state messages visible with retry/reload; only mark saved after success. Preserve the two-step keep-current-then-load recovery state.
- [x] `frontend/src/components/RevisionSummaryBanner.tsx` — use date-change language and expose a deliberate review/acknowledgment action, with summary retained across unrelated actions until acknowledged.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — prove rename leaves option dates/components/tally/booking reference intact and rejects stale, foreign, and booked changes.
- [x] `frontend/src/DraftPromotion.test.tsx` — prove save-new/update/copy outcomes, empty guard, confirmation/cancel, failed save, session expiry and summary persistence.
### Automated verification
- [x] `./mvnw.cmd -Dtest=TripApiIntegrationTest test` from root — rename contract and existing option tests pass.
- [x] `npm test -- --run DraftPromotion.test.tsx` from `frontend` — option-action tests pass.
### Optional developer checks
- [ ] None.

## Phase 3: Comparison and journey verification
### Changes
- [x] `frontend/src/components/ItineraryComparisonView.tsx` — use option names and own date ranges in desktop columns, mobile tabs/panels, live announcement and booking labels; display each saved snapshot's current component and tally facts and option-specific expired/booked state; remove misleading Working date range from comparison header.
- [x] `frontend/src/components/TripWorkspace.tsx`, `PostCancellationTriageModal.tsx` — audit comparison selection, booking review, cancellation follow-up and modal focus return for named-option terminology and copy behavior; preserve selected IDs across harmless rerenders, clear stale IDs when an option disappears.
- [x] `frontend/src/style.css` — maintain accessible focus indicators, mobile tab/column readability, and scroll behavior.
- [x] `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/FeeFreeCancellationAndTriage.test.tsx`, `frontend/src/PublicTripFlow.test.tsx` — assert per-option dates, names, components/tallies and states across desktop/mobile, keyboard tabs, booking and cancellation paths.
### Automated verification
- [x] `npm test` from `frontend` — all frontend regressions pass.
- [x] `npm run build` from `frontend` — TypeScript and Vite build pass.
- [x] `./mvnw.cmd test` from root — backend regression suite passes.
### Optional developer checks
- [ ] Keyboard and screen-reader smoke check at desktop and narrow width, especially modal focus and live status.

## Test Strategy
Begin with a failing list/workspace test showing Draft terminology and duplicated Working presentation. Add a backend rename test before the route. Use existing mocked `tripsApi` frontend seams for action outcomes and conflict/auth failures. Compare two Saved options with different dates, components and prices, including a booked option and an expired one. Keep integration tests isolated through the established MockMvc and database test setup; do not call live endpoints.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| One Working and separate named/dates options | Workspace, card, list | App and DraftPromotion tests |
| No arbitrary empty Draft flow; incomplete Working editable | Workspace action/UI cleanup | App and DraftPromotion tests |
| Save/update/copy outcome and rename clarity | Workspace confirmation, rename endpoint | DraftPromotion and TripApiIntegration tests |
| Correct option facts and state | Card, comparison, list | Comparison, booking, App tests |
| Date summary and failure status persist | Workspace and RevisionSummaryBanner | DraftPromotion failure/summary tests |
| Keyboard, assistive and narrow layout | Modal/tab focus, labels, CSS | Comparison keyboard and flow tests; optional visual smoke |

## Risks and Rollback/Recovery
Version conflicts and partial keep-current/load failures must show the actual persisted state; refetch before retrying. Rename is additive and can be rolled back independently of the visual changes. If a backend test reveals that booked name changes should be allowed, preserve the stricter current `updateOption` immutability unless the ticket is amended. The legacy API remains available to old clients while the new UI stops offering Draft controls.

## References
- Ticket and research above.
- `frontend/src/components/TripWorkspace.tsx`, `AlternativeCard.tsx`, `TripListSection.tsx`, `ItineraryComparisonView.tsx`, `RevisionSummaryBanner.tsx`, `PostCancellationTriageModal.tsx`
- `frontend/src/api/tripsApi.ts`; `src/main/java/app/detour/trip/{TripController,TripRequests,TripService,TripRepository}.java`

## Implementation notes

- Saved option and Trips profile responses now expose an additive `booked` flag derived from booking history. This lets the UI identify previously booked options after cancellation, not only the current active booking. The existing IDs and compatibility aliases remain available.
- The option rename route advances the Trip and option versions and changes only the option name. A booked option is rejected, including one whose booking was canceled.
- The update confirmation uses the persisted Working snapshot and retained server revision summary. Version conflicts close the confirmation and require reload and another review before retrying.
- Automated verification uses the in-memory test database and mocked frontend APIs. Narrow-width visual inspection and screen-reader smoke checks remain optional developer checks.
