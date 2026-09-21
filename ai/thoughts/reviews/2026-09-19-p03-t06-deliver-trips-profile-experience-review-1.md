# P03-T06 Deliver the Trips and Alternatives Profile Experience Code Review — Cycle 1

## Scope and Repository State

- **Ticket:** `c:\code\loomspan-travel-demo\ai\thoughts\tickets\2026-09-19-p03-t06-deliver-trips-profile-experience.md`
- **Reviewed Changes:**
  - `frontend/src/api/tripsApi.ts` — TypeScript domain types, clean payload cleaners, and REST client methods for Trips, Drafts, Alternatives, Revisions, and Deletions.
  - `frontend/src/api/identityApi.ts` — Profile type extended with `upcoming` and `past` projection arrays; exported CSRF helper and generic request method.
  - `frontend/src/components/EmptyProfileState.tsx` — Informative onboarding empty state explaining Trips with primary "Plan Trip" button.
  - `frontend/src/components/TripCreateModal.tsx` — Modal collecting destination (SFO, MUC, MEX), March 2027 dates, and traveler count (1–8) with focus trapping and inline validation.
  - `frontend/src/components/TripListSection.tsx` — Upcoming and Past sections displaying trips in backend order with primary derived labels, badges, counts, nested alternatives, and delete guards.
  - `frontend/src/components/TripWorkspace.tsx` — Focused workspace supporting debounced autosave, optimistic version tracking, conflict reload, immutable trip constraints when Planned alternatives exist, and distinct draft creation/duplication actions.
  - `frontend/src/components/AlternativeCard.tsx` — Alternative card distinguishing Draft vs Planned snapshots, read-only locking, duplication to draft, and deletion.
  - `frontend/src/components/RevisionSummaryBanner.tsx` — Accessible alert rendering removed and adjusted components with change details and dismissal.
  - `frontend/src/components/TripRevisionModal.tsx` — Selective planned itinerary duplication dialog for trips with Planned alternatives (`POST /api/trips/{id}/duplicate`).
  - `frontend/src/components/ConfirmDeleteModal.tsx` — Scoped deletion confirmation modal for Draft, Planned itinerary, and Trip (with server-reported counts and booking history guards).
  - `frontend/src/style.css` — Responsive layout rules for workspace cards, modal dialogs, status alerts, badges, and 320px mobile viewports.
  - `frontend/src/api/tripsApi.test.ts` — Unit tests for strict payload serialization and CSRF header transmission.
  - `frontend/src/App.test.tsx` — End-to-end integration tests covering all profile, workspace, alternative, autosave, conflict, deletion, revision, and accessibility user journeys.

## Findings

### [P1] State synchronization and autosave corruption after Trip Revision in `TripWorkspace.tsx`
- Location: `frontend/src/components/TripWorkspace.tsx:693-703`
- Scenario: User revises a trip containing Planned alternatives via `TripRevisionModal`. The modal calls `duplicateTrip` and passes the new trip aggregate to `onSuccess(newTrip)`.
- Impact: `TripWorkspace` previously only called `setTrip(newTrip)`. Because the `initialTrip` prop did not change, local form inputs (`destinationKey`, `startDate`, `endDate`, `travelerCount`, `travelerAges`, `budgetDollars`) and `revisionSummary` remained bound to the old trip. 600ms later, the debounced autosave hook detected an input mismatch against `newTrip`, overwriting the newly created trip with the stale values and suppressing the revision summary banner.
- Evidence: Inspected `TripWorkspace.tsx` lines 688–703 and `useEffect([initialTrip])`. Form input states were decoupled from `newTrip`.
- Fix: Introduced an `applyTripState(t: TripResponse)` helper that synchronizes all form fields, budget, ages, and revision summaries when a trip is loaded or revised. Updated `TripRevisionModal.onSuccess` to call `applyTripState(newTrip)` and notify parent `onTripUpdated(newTrip)`.

### [P2] Traveler age inputs were not disabled when Planned alternatives exist, leading to `409 IMMUTABLE_TRIP` rejection
- Location: `frontend/src/components/TripWorkspace.tsx:620-637`
- Scenario: A trip has Planned alternatives (`hasPlanned === true`). Destination, dates, and traveler count inputs were disabled, but traveler age inputs remained enabled.
- Impact: When a user edited an age input, autosave submitted `replaceSharedDetails`. In `TripService.java:149-153`, `travelersChanged` checks traveler ages. The backend rejected the mutation with `409 IMMUTABLE_TRIP`, causing an unexpected error state.
- Evidence: `TripWorkspace.tsx` rendered `<input type="number" ... />` without `disabled={hasPlanned}`.
- Fix: Added `disabled={hasPlanned}` to traveler age inputs, and added explicit handling for `err.code === 'IMMUTABLE_TRIP'` in `executeAutosave` to direct users to "Revise Trip".

### [P3] Focus lost to `<body>` after deleting a trip or draft
- Location: `frontend/src/components/ConfirmDeleteModal.tsx:47-58`
- Scenario: User confirms deletion of a trip or draft alternative. The item and its trigger button are removed from the DOM.
- Impact: `ConfirmDeleteModal` unconditionally attempted `previousActiveElement.current.focus()`. On detached DOM nodes this fails silently, leaving focus orphaned on `document.body` and degrading keyboard/screen-reader navigation.
- Evidence: `ConfirmDeleteModal.tsx` lacked a fallback check for `document.body.contains(...)`.
- Fix: Added a check for `document.body.contains(previousActiveElement.current)`. When false, focus falls back to a meaningful landmark/heading (`#upcoming-trips-heading`, `#alternatives-heading`, etc.). Added `tabIndex={-1}` to section headings in `TripListSection.tsx` and `TripWorkspace.tsx`.

### [P3] `TripRevisionModal` discarded existing budget and traveler ages during duplication
- Location: `frontend/src/components/TripRevisionModal.tsx:117-127`
- Scenario: User revised a trip that had a budget or traveler ages configured.
- Impact: `TripRevisionModal` omitted `budgetCents` and `travelerAges` from the `duplicateTrip` payload. The backend treated missing properties as null, wiping out the user's budget and ages on the new trip.
- Evidence: `TripRevisionModal.tsx` payload only included destination, dates, traveler count, and source IDs.
- Fix: Passed `travelerAges: count === trip.travelerCount ? trip.travelerAges : null` and `budgetCents: trip.budgetCents` to preserve existing budget and traveler ages on revised trips.

### [P3] Missing `tripExpired` and temporal badges in `TripWorkspace.tsx`
- Location: `frontend/src/components/TripWorkspace.tsx:455-465, 675-685`
- Scenario: User opened an upcoming, past, or expired trip in the workspace.
- Impact: The workspace header lacked the temporal status badge and expired badge, and `AlternativeCard` was not passed `tripExpired`, so alternatives never showed the `Expired` badge in workspace view.
- Evidence: `AlternativeCard.tsx:42` had `tripExpired` logic, but `TripWorkspace.tsx` omitted the prop.
- Fix: Passed `temporalStatus` and `isExpired` from `ProfileScreen` to `TripWorkspace`, rendered badges in the workspace header, and forwarded `tripExpired={isExpired}` to `AlternativeCard`.

### [P3] Missing end-to-end integration test for Trip Revision in `App.test.tsx`
- Location: `frontend/src/App.test.tsx`
- Scenario: End-to-end user journey for revising trips with Planned alternatives.
- Impact: The revision flow was only covered in unit tests for `tripsApi.ts`, leaving the workspace state-sync bug uncovered.
- Evidence: No test in `App.test.tsx` exercised `TripRevisionModal` or `POST /api/trips/{id}/duplicate`.
- Fix: Added integration tests in `App.test.tsx` verifying opening `TripRevisionModal`, changing destination/dates, asserting `duplicateTrip` payload, verifying workspace update, and verifying revision summary rendering and dismissal.

## Findings Resolved in This Context

All findings ([P1], [P2], and [P3]) were resolved directly within scope:
1. `frontend/src/components/TripWorkspace.tsx`: Added `applyTripState` synchronization, `onTripUpdated` callback, `disabled={hasPlanned}` on traveler ages, temporal/expired header badges, `tripExpired` prop forwarding, `tabIndex={-1}` on headings, and `IMMUTABLE_TRIP` handling.
2. `frontend/src/components/ProfileScreen.tsx`: Wired `temporalStatus`, `isExpired`, and `onTripUpdated` props to `TripWorkspace`.
3. `frontend/src/components/TripRevisionModal.tsx`: Preserved budget and traveler ages in duplication payload.
4. `frontend/src/components/ConfirmDeleteModal.tsx`: Added focus recovery fallback when triggering button is detached.
5. `frontend/src/components/TripListSection.tsx`: Added `tabIndex={-1}` to section headings for programmatic focus recovery.
6. `frontend/src/App.test.tsx`: Added end-to-end integration tests for trip revision workflow and focus recovery.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| 1. Empty profile onboarding & Trip creation with first Draft | `EmptyProfileState.tsx`, `TripCreateModal.tsx`, `tripsApi.createTrip` | `App.test.tsx`: "displays empty profile onboarding with Plan Trip action and creates an initial trip" | implemented |
| 2. Multiple Drafts, duplication, shared-details autosave (`Saving`, `Saved`, validation error, network error, 409 conflict) | `TripWorkspace.tsx`, `tripsApi.replaceSharedDetails`, `StatusRegion.tsx` | `App.test.tsx`: "creates component-empty draft and explicitly duplicates", "autosaves mutable traveler ages and budget...", "handles optimistic concurrency conflict (409 VERSION_CONFLICT)..." | implemented |
| 3. Upcoming and Past Trips in backend order with clear nested hierarchy and accurate Draft, Planned, Expired badges; no fabricated booking data | `TripListSection.tsx`, `AlternativeCard.tsx`, `ProfileScreen.tsx` | `App.test.tsx`: "renders Upcoming and Past trips in backend order with clear nested hierarchy and status badges" | implemented |
| 4. Planned alternatives visibly read-only, revision workflow preserves source Trip, change summary names removals/reasons | `AlternativeCard.tsx`, `TripRevisionModal.tsx`, `RevisionSummaryBanner.tsx` | `App.test.tsx`: "displays Planned alternative as visibly read-only...", "opens TripRevisionModal for trip with planned alternatives, creates revised trip...", "displays structured revision summary..." | implemented |
| 5. Scoped Delete Draft, Delete Planned, and Delete Trip confirmations with server counts and stale/rejected deletion handling | `ConfirmDeleteModal.tsx`, `tripsApi.delete*` | `App.test.tsx`: "confirms and executes scoped draft deletion...", "blocks trip deletion when server reports booking history", "handles stale trip deletion confirmation (409 STALE_CONFIRMATION)..." | implemented |
| 6. Keyboard and screen reader semantics, focus recovery, and responsive layout without horizontal scrolling | `style.css`, focus trapping/recovery in `ConfirmDeleteModal.tsx` & `TripCreateModal.tsx` | `App.test.tsx`: "enforces accessible keyboard navigation, dialog focus trapping, Escape dismissal...", "restores focus to section heading when deleted trip card button is removed from DOM" | implemented |
| 7. Frontend interaction tests cover major success/error/conflict/destructive paths; build passes | `App.test.tsx`, `tripsApi.test.ts` | 34 passing tests in `npm.cmd --prefix frontend test`, `npm.cmd --prefix frontend run build` passes, `.\mvnw.cmd test-compile` passes | implemented |
| 8. Existing registration, login, logout, password change, CSRF, and About demo remain intact | Retained `ProfileScreen.tsx` password form, `AboutDemoTab.tsx`, `identityApi.ts` | All 9 pre-existing identity tests in `App.test.tsx` and 3 tests in `identityApi.test.ts` pass | implemented |
| 9. No Phase 4–6 experiences introduced (zero catalog search, comparison UI, or booking flows) | All components inspected: `TripWorkspace.tsx`, `AlternativeCard.tsx`, `TripListSection.tsx` | Verified zero search, comparison tally, or booking controls | implemented |

## Active Project Guardrails
- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions
- None. All requirements, domain constraints, and error envelopes align strictly with backend contracts from P03-T01 through P03-T05.

## Verification Results
- PASS — `npm.cmd --prefix frontend test` — 34 tests passed across 4 test files.
- PASS — `npm.cmd --prefix frontend run build` — Clean production bundle generated (HTML, CSS 9.55 kB, JS 240.35 kB).
- PASS — `.\mvnw.cmd test-compile` — Maven frontend assets packaged and Java classes compiled cleanly with 0 errors.

## Residual Risks and Optional Developer Checks
- Nonblocking manual check: Run `npm.cmd --prefix frontend run dev` and open `http://localhost:5173` in a desktop and mobile browser to visually observe the responsive grid, smooth autosave indicators, and modal transitions down to 320px viewport width.

## Disposition
- `fixes-applied`
