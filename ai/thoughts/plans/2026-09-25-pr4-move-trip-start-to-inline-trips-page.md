# Inline Trips Page Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-25-pr4-move-trip-start-to-inline-trips-page.md`
- Research: `ai/thoughts/research/2026-09-25-pr4-move-trip-start-to-inline-trips-page.md`
- Outcome: Trips owns the inline start flow, private Trip list, and open Working plan; Profile contains account controls.

## Current State
`App` keeps the guest draft in memory across authentication (`frontend/src/App.tsx`). `TripStartForm` already validates and posts the required name, dates, count, and ages, but starts with a blank name, omits budget, truncates ages when count falls, and shows unbound error text (`frontend/src/components/TripStartForm.tsx`). `ProfileScreen` has a Trips navigation item and form, but renders `TripListSection` in Profile, refreshes summaries on Profile navigation, and switches to a separate workspace destination after creation (`frontend/src/components/ProfileScreen.tsx`). `TripListSection` shows a trip-wide date range and truncated option IDs despite named, dated summary fields (`frontend/src/components/TripListSection.tsx`; `frontend/src/api/tripsApi.ts`). The owned version-guarded rename endpoint already exists at `PUT /api/trips/{tripId}/name` (`TripController.rename`, `TripService.rename`).

## Desired End State
Home, Trips, and Profile remain distinct navigation destinations. Guest Trips shows only the start form; authenticated Trips shows that form and private list, then the selected Working plan within the Trips destination after an explicit create/open. Profile shows email, password, and logout only. Plan Trip/Airfare/Stay set mode without a write. The draft has an editable suggested name, dates, destination, optional budget, traveler count, and one required age per visible traveler. Count changes preserve age entries by index, including temporarily hidden entries. Guest authentication retains all draft values, and only a later explicit authenticated submit creates one Trip. List cards distinguish Trip name, Working dates/state, and individually named/dated Saved options. Inline rename updates the owned Trip in place.

## Scope
### In scope
- Signed-in Trips composition, list refresh/navigation, Working plan presentation, card content and Trip rename.
- Start-form name suggestion, optional budget, age preservation, field errors, and responsive layout.
- Frontend integration and API client tests.

### Out of scope
- Destination/catalog window changes, account fields, Saved-option comparison or component-save behavior, server contract changes.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
Navigation changes affect existing Profile-centered tests and workspace Back/delete/cancel routes. Keep `ProfileScreen` mounted as it is today to preserve unsaved workspace state, and retain its existing discard confirmation on switching Trips. Profile summaries originate in authenticated `/api/profile`; never expose them in the public branch. Refresh summaries when Trips opens and after create, rename, delete, cancel, and workspace updates. Rename uses the current summary version and must surface conflict/network errors without claiming success. The optional budget is converted from dollars to integer cents within server range 0–1,000,000 dollars. Avoid a shared-looking Trip date in list copy because Saved options have independent dates. Existing older fixture fields are optional in TypeScript, but current server responses include names/dates.

## Implementation Approach
Keep the current `ProfileScreen` owner of signed-in navigation, modal actions, and workspace state. Treat `viewMode='trips'` as the Trips destination and use an internal selected-Trip state to show `TripWorkspace` within it, with a Back to all trips control returning to the Trips start/list view. Keep the Trips nav current while the workspace is open; remove the separate workspace nav item. This is a bounded composition change and preserves workspace component behavior. Reuse App's guest draft state and existing create route. Add a small `tripsApi.renameTrip` client for the existing server route; perform rename from `TripListSection`/`ProfileScreen` using the Trip summary's version and refresh profile after success. Do not introduce a second Trip or alternative to rename.

## Phase 1: Complete the shared inline start form
### Changes
- [x] `frontend/src/components/TripStartForm.tsx` — initialize a useful editable name suggestion for a fresh draft (for example “San Francisco trip”) without overwriting user edits; add optional dollar budget to `TripStartDraft`, validate finite whole cents and server range, send `budgetCents` only when provided, and preserve age-array entries when count shrinks and grows.
- [x] `frontend/src/components/TripStartForm.tsx` — give each invalid input `aria-invalid` and a stable `aria-describedby` field error; bind the shared date error to both dates and age errors to the applicable age fields; announce failed save while retaining entered data.
- [x] `frontend/src/App.tsx` — carry the expanded draft through guest authentication and clear it only under the established logout/account boundary.
- [x] `frontend/src/PublicTripFlow.test.tsx` — cover suggestion editing, optional budget serialization, count shrink/regrow, required ages, failed save, and guest handoff with all values.
### Automated verification
- [x] `npm test -- --run src/PublicTripFlow.test.tsx` from `frontend` — focused start/handoff tests pass.
### Optional developer checks
- [ ] Observe keyboard focus, error announcements, and form layout at a narrow viewport.

## Phase 2: Make Trips the signed-in home for trips
### Changes
- [x] `frontend/src/components/ProfileScreen.tsx` — render `TripStartForm` plus `TripListSection` in Trips when no Trip is selected; remove list/empty onboarding from Profile and leave account information/actions there. Refresh private summaries when entering Trips. After create/open, show `TripWorkspace` under the Trips destination with Trips nav current; Back and deletion return to Trips. Retain existing unsaved-change confirmation, opening retry, and auth handling.
- [x] `frontend/src/components/TripListSection.tsx` — show Trip name and destination, label the Working plan's dates specifically, and list each Saved option by its name, own dates and state; remove technical ID display and avoid a date presented as shared by every option. Keep booking/cancel/delete states.
- [x] `frontend/src/components/EmptyProfileState.tsx` — remove or repurpose any Profile-specific empty-trip content no longer used.
- [x] `frontend/src/style.css` — make inline Trips sections, controls, and cards wrap without horizontal overflow at mobile width; maintain visible focus and readable labels.
- [x] `frontend/src/App.test.tsx` and `frontend/src/PublicTripFlow.test.tsx` — update Profile-centered paths and assert distinct destinations, guest privacy, no open-only write, create-once, and Working plan under Trips.
### Automated verification
- [x] `npm test -- --run src/App.test.tsx src/PublicTripFlow.test.tsx` from `frontend` — navigation and workspace regressions pass.
### Optional developer checks
- [ ] Inspect desktop/mobile layout and keyboard traversal through Trips list and workspace.

## Phase 3: Rename the owned Trip and verify integration
### Changes
- [x] `frontend/src/api/tripsApi.ts` — add `renameTrip(tripId, {expectedVersion, name})` using the existing `PUT /api/trips/{id}/name` endpoint and CSRF-aware request helper.
- [x] `frontend/src/components/TripListSection.tsx` and `frontend/src/components/ProfileScreen.tsx` — provide an accessible inline rename control, trim/validate the nonempty name, disable duplicate pending saves, use current version, show conflict/network feedback, and refresh list and active Trip after success while retaining the same Trip ID, alternatives, and booking association.
- [x] `frontend/src/api/tripsApi.test.ts` — assert endpoint, method, body, and CSRF behavior.
- [x] `frontend/src/App.test.tsx` — assert rename keeps the card and options under the same ID and failed rename retains the original title.
### Automated verification
- [x] `npm test` from `frontend` — full frontend suite passes.
- [x] `npm run build` from `frontend` — TypeScript and production build pass.
### Optional developer checks
- [ ] None beyond the focused UI checks above.

## Test Strategy
Start with a red `App.test.tsx` case proving the Trip list is present in Trips and absent from Profile. Add focused user-event tests for entry modes, preservation and validation of form fields, one explicit create, same-destination workspace, list wording, rename conflicts, and guest privacy. Use mocked fetch responses and existing fixture style; no live writes. Exercise the API client serialization separately. The existing Java integration tests cover creation, age validation, single Working row, and owned rename; no server change is planned.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Distinct Home/Trips/Profile; private list only in Trips | `ProfileScreen` composition | `App.test.tsx` navigation/privacy assertions |
| Common inline entry, no opening save | `HomeScreen`/`TripStartForm` retained, Trips composition | `PublicTripFlow.test.tsx` entry-mode request counts |
| Complete validated details | `TripStartForm` | `PublicTripFlow.test.tsx` values and invalid cases |
| One owned Trip, Working plan in Trips | existing create API; `ProfileScreen` destination | `App.test.tsx` single POST and current-nav/workspace assertions |
| Guest handoff without duplicate | `App` draft, `TripStartForm` gate | `PublicTripFlow.test.tsx` authentication journey |
| Named Trips/options, independent dates, safe rename | `TripListSection`, `tripsApi.renameTrip` | `App.test.tsx`, `tripsApi.test.ts` |
| Accessible narrow layout and honest errors | form attributes, CSS, status text | DOM assertions plus optional viewport/AT observation |

## Risks and Rollback/Recovery
The main regression risk is existing workspace navigation and tests that assume list-on-Profile. Update those paths together, preserve existing workspace state and discard guards, and use full frontend suite as the gate. On rename conflict, leave displayed summary intact and offer refresh/retry; on create failure retain draft. All changes are frontend; reverting this ticket restores prior navigation without data migration.

## References
- Ticket and research paths above
- `frontend/src/App.tsx`
- `frontend/src/components/ProfileScreen.tsx`
- `frontend/src/components/TripStartForm.tsx`
- `frontend/src/components/TripListSection.tsx`
- `frontend/src/api/tripsApi.ts`
- `src/main/java/app/detour/trip/TripController.java`
