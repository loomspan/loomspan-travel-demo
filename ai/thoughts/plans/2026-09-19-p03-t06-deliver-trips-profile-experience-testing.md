# P03-T06 Deliver the Trips and Alternatives Profile Experience Testing Plan

## Change Summary
Deliver the end-to-end frontend Trips and alternatives profile workspace in React 19 / TypeScript, connecting to the stabilized P03-T01 through P03-T05 backend contracts. This introduces:
- Typed domain models and API methods for trips, drafts, alternatives, revisions, and deletions in `tripsApi.ts`.
- Empty-state onboarding with an informative explanation of Trips and a primary "Plan Trip" action.
- Trip creation modal collecting origin (PDX), destination, March 2027 dates (1–14 nights), and traveler count (1–8).
- Upcoming and Past trip list sections ordered by start date with nested alternatives and Draft/Planned/Expired badges.
- Focused Trip Workspace supporting mutable traveler ages (0–120) and budget ($0–$1,000,000.00 / 0–100,000,000 cents) with debounced autosave, accessible live status announcements (`Saving…`, `Saved`), and optimistic concurrency conflict handling (`409 VERSION_CONFLICT`) that preserves user edits.
- Alternatives management with visibly distinct actions for creating component-empty drafts vs duplicating existing drafts or planned alternatives.
- Visibly read-only Planned alternatives routing edits through "Duplicate to Draft".
- Revision workflow for trips with Planned alternatives (`POST .../duplicate`) and structured removal/adjustment summaries (`RevisionSummaryResponse`).
- Scoped deletion confirmation dialogs for Draft, Planned itinerary, and Trip (with server-reported Draft and Planned counts), blocking deletion when booking history exists.
- Keyboard navigation, visible focus indicators, modal focus trapping, and focus recovery across all flows.
- Responsive layout down to 320px viewport width without horizontal page scrolling.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| **API Payload Compliance** | Backend `TripRequests.requireObject` rejects unexpected JSON fields with `400 VALIDATION_FAILED`. | Unit tests in `tripsApi.test.ts` verifying exact request serialization for all mutation endpoints. |
| **Autosave & Input Preservation** | Keystrokes lost, race conditions, or uncommitted inputs cleared upon validation error or network failure. | Integration tests in `App.test.tsx` verifying debounce timer, `Saving…` / `Saved` announcements in `StatusRegion`, and retention of invalid/uncommitted inputs on error. |
| **Optimistic Concurrency** | Simultaneous edits cause `409 VERSION_CONFLICT`; silent overwrite or unprompted data loss. | Test verifying that on 409, unsaved input is preserved, a conflict message is displayed, and clicking "Reload from server" refreshes data. |
| **Planned Itinerary Immutability** | Modifying destination/dates on a trip with Planned alternatives triggers backend `409 IMMUTABLE_TRIP`. | Test verifying in-place destination/date editing is disabled when Planned alternatives exist and directs user to "Revise Trip". |
| **Trip Deletion Safeguards** | Deleting a trip with booking history (forbidden) or with stale counts (`409 STALE_CONFIRMATION`). | Tests verifying deletion button is disabled when `hasBookingHistory: true`, confirmation displays server counts, and 409 stale confirmation refreshes the view. |
| **Accessibility & Focus** | Modals failing to trap focus, focus jumping to `<body>` after deletion, or screen readers missing status announcements. | Tests verifying dialog focus trap, Escape key handling, focus return to trigger element, and `role="status"` announcements without focus theft. |
| **Identity Regression** | Profile changes break existing login, logout, registration, password change, CSRF, or About demo. | All 9 existing tests in `App.test.tsx` and 3 tests in `identityApi.test.ts` continue to pass. |

## Existing Coverage and Environment Constraints
- **Test Runner & Environment:** Vitest 4.1.11, React Testing Library 16.3.0, `@testing-library/user-event` 14.6.1, `jsdom` 27.4.0.
- **Doubles & Mocks:** Global `fetch` is mocked via `vi.stubGlobal('fetch', fetchMock)` with helper functions `json(status, body)` and `noContent()`. `document.cookie` is populated with `XSRF-TOKEN=token; path=/`.
- **Backend Test Isolation:** Spring Boot `TripApiIntegrationTest.java` (1,366 lines) already tests all server endpoints with dynamic in-memory H2 database, controllable test clock, and transaction boundaries. No backend code or tests are modified.
- **Zero External Network Dependencies:** All tests execute locally and deterministically.

## Failing Test First
- **Name:** `displays empty profile onboarding with Plan Trip action and creates an initial trip`
- **Type:** Frontend integration test
- **Location:** `frontend/src/App.test.tsx`
- **Arrange/Act/Assert:**
  - *Arrange:* Mock `fetch` responses:
    1. `GET /api/profile` -> `200` with `{ email: 'ada@example.test', upcoming: [], past: [] }`.
    2. `POST /api/trips` -> `201` with `TripResponse` (`destinationKey: 'destination-sfo'`, `label: 'San Francisco — Mar 10–14, 2027'`, `version: 0`, 1 draft).
    3. Subsequent `GET /api/profile` (or state update) displaying the created trip in Upcoming.
  - *Act:*
    1. Render `<App />`.
    2. Await profile load.
    3. Assert onboarding text explaining Trips and `"Plan Trip"` button are in the document.
    4. Click `"Plan Trip"`.
    5. Assert creation modal appears with origin PDX.
    6. Select destination `"destination-sfo"`, enter start date `"2027-03-10"`, end date `"2027-03-14"`, traveler count `"2"`.
    7. Click `"Create trip"`.
  - *Assert:*
    1. `fetch` called with `POST /api/trips` containing `{ destinationKey: 'destination-sfo', startDate: '2027-03-10', endDate: '2027-03-14', travelerCount: 2 }` and `X-XSRF-TOKEN` header.
    2. Workspace or upcoming section renders `"San Francisco — Mar 10–14, 2027"`.
    3. Shows 1 Draft alternative.
- **Expected pre-fix failure:**
  - Fails because `ProfileScreen.tsx` currently displays static placeholder text `"There is nothing else to manage here yet."` without a "Plan Trip" button or trip creation dialog.

---

## Tests to Add or Update

### 1. `displays empty profile onboarding with Plan Trip action and creates an initial trip`
- **Type:** Integration test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 1: A new account sees an informative empty profile, can create a supported Trip with one Draft, and view it in the profile.
- **Inputs/fixture:** New user profile `{ email: 'ada@example.test', upcoming: [], past: [] }`, trip creation payload for San Francisco (March 10–14, 2027, 2 travelers).
- **Doubles or boundary isolation:** Global `fetch` mock returning JSON responses.
- **Edge cases:** Date inputs outside March 1–31, 2027 or duration > 14 nights show validation errors before submit.

### 2. `renders Upcoming and Past trips in backend order with clear nested hierarchy and status badges`
- **Type:** Integration test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 3: Upcoming and Past Trips render in backend order with a clear nested Trip/alternative hierarchy and accurate Draft, Planned, and Expired information; no fabricated booking data.
- **Inputs/fixture:** Profile response containing:
  - Upcoming trip: `San Francisco — Mar 10–14, 2027`, 2 drafts, 1 planned, `temporalStatus: 'UPCOMING'`, `expired: false`.
  - Past trip: `Munich — Mar 01–05, 2027`, 1 draft, 0 planned, `temporalStatus: 'PAST'`, `expired: true`.
- **Doubles or boundary isolation:** Mocked `GET /api/profile`.
- **Edge cases:** Trips with expired departure midnight show `"Expired"` badge on alternatives; booked count is only shown when supplied by backend.

### 3. `creates component-empty draft and explicitly duplicates existing draft with distinct actions`
- **Type:** Integration test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 2 & Requirement 5: Users can create component-empty Draft alternatives and explicitly duplicate existing sources; empty creation and duplication are visibly distinct actions.
- **Inputs/fixture:** Trip aggregate with 1 draft (`version: 0`). Mock `POST /api/trips/{id}/drafts` with `{ expectedVersion: 0 }`, and `POST /api/trips/{id}/drafts/{draftId}/duplicate` with `{ expectedVersion: 1, expectedDraftVersion: 0 }`.
- **Doubles or boundary isolation:** Mocked `fetch`.
- **Edge cases:** Attempting duplication while draft version has advanced sends correct versions and updates local state.

### 4. `autosaves mutable traveler ages and budget with debouncing, status announcements, and input preservation on error`
- **Type:** Integration test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 2: User can edit allowed shared details and see durable `Saving`, `Saved`, validation-error, and network-error states without clearing unsaved input.
- **Inputs/fixture:** Trip workspace with 2 travelers. User types age `25` for traveler 1, age `30` for traveler 2, and budget `$2,500.00`. Mock `PUT /api/trips/{id}` returning updated version.
- **Doubles or boundary isolation:** Fake timers (`vi.useFakeTimers()`) to test 600ms debounce.
- **Edge cases:** Entering negative age or >120 age shows inline validation error and prevents network save; network failure displays error notice while retaining user inputs.

### 5. `handles optimistic concurrency conflict (409 VERSION_CONFLICT) by preserving user edits and offering reload`
- **Type:** Integration test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 2 & Requirement 6: A version conflict explains that newer data exists and offers an explicit reload/retry path rather than silently merging or clearing unsaved input.
- **Inputs/fixture:** `PUT /api/trips/{id}` returns `409` with `{ code: 'VERSION_CONFLICT', message: 'The Trip has changed. Reload before saving.', fields: { currentVersion: '2' } }`.
- **Doubles or boundary isolation:** Mocked `fetch`.
- **Edge cases:** Clicking "Reload from server" fetches `GET /api/trips/{id}`, updates `version` to 2, and allows user to re-apply edits.

### 6. `displays Planned alternative as visibly read-only and routes editing through Duplicate to Draft`
- **Type:** Integration test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 4: Planned alternatives are visibly read-only, revision workflows preserve the source Trip, and direct in-place edits are prevented.
- **Inputs/fixture:** Trip containing 1 Planned itinerary. Card shows "Planned itinerary (read-only)" badge, has no editable inputs, and provides a "Duplicate to draft" button.
- **Doubles or boundary isolation:** Mocked `POST /api/trips/{id}/alternatives/{altId}/duplicate`.
- **Edge cases:** Duplicating a planned alternative sends `expectedDraftVersion: null` (omitted) and creates a new Draft containing the planned selections.

### 7. `displays structured revision summary (removals and adjustments) when returned by backend`
- **Type:** Integration test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 4: Revision summaries name every removed or changed item and its reason.
- **Inputs/fixture:** `TripResponse` containing `revisionSummary` with:
  - `removals`: `[{ draftId: '...', component: 'Airfare', reason: 'Flight dates no longer match trip dates.' }]`
  - `adjustments`: `[{ draftId: '...', component: 'Stay', changeType: 'UNIT_COUNT', previousUnitCount: 1, newUnitCount: 2, reason: 'Traveler count increased.' }]`
- **Doubles or boundary isolation:** Mocked `fetch`.
- **Edge cases:** User can review the details and dismiss/acknowledge the summary banner.

### 8. `confirms and executes scoped draft deletion, planned deletion, and trip deletion with server counts`
- **Type:** Integration test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 5: Distinct Delete Draft, Delete Planned itinerary, and Delete Trip actions with confirmation text naming the affected scope and server-reported counts.
- **Inputs/fixture:**
  - Delete Draft: confirms draft scope, sends `{ expectedVersion: 1, expectedDraftVersion: 0 }`.
  - Delete Planned: confirms planned scope, sends `{ expectedVersion: 2, confirmed: true }`.
  - Delete Trip: dialog displays `1 Draft alternative(s) and 1 Planned itinerary(ies)`, sends `{ expectedVersion: 3, expectedDraftCount: 1, expectedPlannedCount: 1, confirmed: true }`.
- **Doubles or boundary isolation:** Mocked `fetch` with `204 No Content` for trip deletion.
- **Edge cases:** Trip is removed from profile list and polite live announcement "Trip deleted." is made.

### 9. `blocks trip deletion when server reports booking history`
- **Type:** Integration test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 5 & Requirement 16: Do not offer permanent deletion when the server reports booking history.
- **Inputs/fixture:** Trip with `hasBookingHistory: true`.
- **Doubles or boundary isolation:** Mocked `fetch`.
- **Edge cases:** Delete button is disabled or confirmation dialog warns that trips with booking history cannot be permanently deleted.

### 10. `handles stale trip deletion confirmation (409 STALE_CONFIRMATION) and refreshes view`
- **Type:** Integration test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 5: Stale or rejected deletion leaves the current view consistent and reports the failure.
- **Inputs/fixture:** `DELETE /api/trips/{id}` returns `409 STALE_CONFIRMATION`.
- **Doubles or boundary isolation:** Mocked `fetch`.
- **Edge cases:** Displays clear error message, closes modal, and refreshes the profile projection so counts match server reality.

### 11. `enforces accessible keyboard navigation, dialog focus trapping, Escape dismissal, and focus restoration`
- **Type:** Integration test / accessibility test
- **Location:** `frontend/src/App.test.tsx`
- **Proves:** Acceptance Criterion 6: Keyboard and screen reader semantics, visible focus, dialog focus trapping, and focus recovery after navigation, dialogs, and destructive actions.
- **Inputs/fixture:** User opens "Plan Trip" modal, presses `Tab` (focus cycles within modal), presses `Escape` (modal closes, focus returns to "Plan Trip" button).
- **Doubles or boundary isolation:** Mocked `fetch`.
- **Edge cases:** Deleting a trip safely focuses the parent section heading rather than resetting to `<body>`.

### 12. `strict payload serialization in tripsApi unit tests`
- **Type:** Unit test
- **Location:** `frontend/src/api/tripsApi.test.ts`
- **Proves:** API Client correctness and strict compliance with `TripRequests.java:91-96` (no extra fields).
- **Inputs/fixture:** Payloads for `createTrip`, `replaceSharedDetails`, `duplicateTrip`, `createDraft`, `duplicateDraft`, `duplicateAlternative`, `deleteDraft`, `deleteAlternative`, `deleteTrip`.
- **Doubles or boundary isolation:** Mocked `fetch`.
- **Edge cases:** Verifies CSRF token header (`X-XSRF-TOKEN`), `credentials: 'same-origin'`, and exact body JSON keys.

---

## Safe Verification Commands
- **Focused:**
  - `npm.cmd test -- src/api/tripsApi.test.ts`
  - `npm.cmd test -- src/App.test.tsx`
- **Related suite:**
  - `npm.cmd test`
- **Full safe suite:**
  - `npm.cmd test && npm.cmd run build && .\mvnw.cmd test-compile`
  *(Note: `.\mvnw.cmd test-compile` compiles frontend static assets and compiles all Java production and test classes cleanly without running slow end-to-end integration tests during routine development)*

---

## Optional Developer Checks
- Nonblocking manual check: Open application in browser at `http://localhost:5173`, register a new user, observe the empty profile state with "Plan Trip", create a trip for San Francisco, edit traveler ages and budget, confirm debounced "Saving…" and "Saved" status transitions, test mobile responsive view at 320px width in devtools, and verify absence of horizontal scrollbars.

---

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes (`npm.cmd test && npm.cmd run build && .\mvnw.cmd test-compile`).
- [x] Acceptance criteria map to executable evidence across all 9 ticket criteria.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases (concurrency, strict JSON validation, stale deletion, immutability, accessibility) are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
