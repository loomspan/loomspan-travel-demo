# P03-T06 Deliver the Trips and Alternatives Profile Experience Implementation Plan

## Overview
- Ticket: `c:\code\loomspan-travel-demo\ai\thoughts\tickets\2026-09-19-p03-t06-deliver-trips-profile-experience.md`
- Research: `c:\code\loomspan-travel-demo\ai\thoughts\research\2026-09-19-p03-t06-deliver-trips-profile-experience.md`
- Outcome: Replace the placeholder profile with a responsive, accessible React 19 Trip workspace supporting empty-state onboarding, trip creation (destination, dates, traveler count), completing traveler ages and budget, managing component-empty drafts and duplicated alternatives, autosave feedback with optimistic concurrency handling, read-only planned snapshots, post-revision removal/repricing summaries, scoped deletion confirmations (draft, planned itinerary, and trip with server-reported counts), and responsive keyboard/screen-reader operation without introducing Phase 4–6 capabilities.

## Current State
- `frontend/src/components/ProfileScreen.tsx` displays only the user's email, a change password form, and a placeholder section (`"Your profile is ready. There is nothing else to manage here yet."`).
- `frontend/src/api/identityApi.ts` fetches `/api/profile`, but extracts only `{ email }`, discarding `upcoming` and `past` projection arrays that the backend `IdentityController` and `TripService` already supply.
- Backend tickets P03-T01 through P03-T05 are complete and verified. All REST endpoints for trip CRUD (`POST /api/trips`, `GET /api/trips`, `GET /api/trips/{id}`, `PUT /api/trips/{id}`, `DELETE /api/trips/{id}`), draft operations (`POST .../drafts`, `POST .../drafts/{id}/duplicate`, `DELETE .../drafts/{id}`), planned alternatives (`POST .../alternatives/{id}/duplicate`, `DELETE .../alternatives/{id}`), trip revisions (`POST .../duplicate`), and date-derived profile projections (`TripsProfileResponse`) are production-ready.
- Strict backend JSON validation in `TripRequests.java:91-96` rejects any unsupported payload property with `400 VALIDATION_FAILED`.
- Frontend styles in `frontend/src/style.css` constrain cards to `width: min(100%, 35rem)` and need responsive extension for the workspace and alternative card hierarchy without horizontal overflow down to 320px.

## Desired End State
- Authenticated profile renders a comprehensive Trip workspace:
  1. An informative empty state explaining Trips and offering a clear "Plan Trip" action when no trips exist.
  2. "Upcoming trips" and "Past trips" sections displaying trips in backend-projected order with clear nested alternative hierarchies.
  3. Trip creation flow collecting destination (`destination-sfo`, `destination-muc`, `destination-mex`), dates (March 1–31, 2027, 1–14 nights), and traveler count (1–8), creating the trip and first draft.
  4. Trip workspace allowing completion and editing of traveler ages (0–120) and budget ($0–$1,000,000.00 / 0–100,000,000 cents), and editing destination/dates/travelers when no Planned alternatives exist.
  5. Autosaving with debounced updates, accessible `Saving`, `Saved`, and error feedback via `StatusRegion` (`aria-live="polite"`), retaining unsaved user input on failure, and offering explicit reload on `409 VERSION_CONFLICT`.
  6. Visibly distinct actions for creating empty drafts and duplicating existing drafts or planned alternatives.
  7. Visibly read-only Planned alternatives routing edits through "Duplicate to Draft".
  8. Revision workflow for locked trips (with Planned alternatives) using selective duplication (`POST /api/trips/{id}/duplicate`) and presenting structured removal/adjustment summaries.
  9. Scoped deletion confirmations for Draft, Planned itinerary, and Trip (with server-reported Draft and Planned counts), blocking deletion when booking history exists.
  10. Keyboard navigation, visible focus indicators, modal focus trapping, and focus recovery across all flows.
  11. Fully responsive across narrow mobile (320px) to wide desktop layouts without horizontal scrolling.
  12. Existing identity behavior (login, logout, password change, CSRF, About this demo disclosure) remains 100% intact.
  13. Zero Phase 4–6 features introduced (no catalog search/results, comparison/booking-choice UI, booking/cancellation controls, custom names, sharing, collaboration, or Version 2 Events).

## Scope
### In scope
- Full TypeScript domain types and API client methods in `frontend/src/api/tripsApi.ts` and updated `Profile` in `frontend/src/api/identityApi.ts`.
- Profile screen restructuring: Master-Detail navigation between Profile Overview (Upcoming / Past trips list) and focused Trip Workspace, plus empty state with onboarding copy.
- Trip creation modal collecting origin (fixed to PDX), destination, March 2027 dates, and traveler count (1–8).
- Trip workspace with traveler ages (dynamic inputs) and budget inputs.
- Autosave manager with optimistic versioning (`expectedVersion`), debouncing, live status region announcements, unsaved input preservation, and 409 conflict reload handling.
- Alternative cards distinguishing Draft vs Planned vs Expired, with distinct empty creation and duplication actions.
- Read-only Planned alternative presentation with "Duplicate to Draft".
- Revision summary presentation (removals and adjustments) for shared detail revisions and trip duplication.
- Scoped deletion confirmation dialogs for Drafts, Planned itineraries, and Trips (with server count verification and booking history guard).
- Modal dialog accessibility: focus trapping, Escape dismissal, and focus restoration to triggering buttons.
- Responsive styling updates in `frontend/src/style.css` supporting wider workspace cards, hierarchy, and 320px mobile viewports.
- Comprehensive Vitest and React Testing Library tests covering all user flows, error handling, conflict resolution, deletions, and accessibility.

### Out of scope
- Phase 4 component search and results (Airfare, Stay, Rental search).
- Phase 5 comparison and booking-choice UI.
- Phase 6 booking, payment, and cancellation flows.
- Custom Trip names, notes, sharing, collaboration, voting, or merging.
- Version 2 Events.
- Backend code modifications (P03-T01 through P03-T05 backend contracts are complete).

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
1. **Strict Backend JSON Deserialization:**
   - *Risk:* `TripRequests.requireObject` throws `400 VALIDATION_FAILED: The request contains an unsupported field.` if any extra property is sent in JSON payloads.
   - *Mitigation:* Explicit payload constructors in `frontend/src/api/tripsApi.ts` will strictly include only allowed fields per operation.
2. **Optimistic Concurrency & Data Loss:**
   - *Risk:* User edits traveler ages or budget while an autosave is in flight or another tab modifies the trip, causing silent overwrite or data loss.
   - *Mitigation:* Version tracking ensures `expectedVersion` is strictly maintained. On `409 VERSION_CONFLICT`, uncommitted form inputs are never cleared, the user is notified with an accessible conflict alert, and an explicit "Reload latest data" action is provided.
3. **Immutability of Trips with Planned Alternatives:**
   - *Risk:* Attempting to update destination, dates, or travelers in place on a trip with Planned alternatives triggers backend `409 IMMUTABLE_TRIP`.
   - *Mitigation:* The UI disables in-place editing of destination, dates, and travelers when `trip.planned.length > 0`, displays an explanation, and provides the "Revise Trip" workflow using `POST /api/trips/{id}/duplicate`.
4. **Stale Deletion Confirmations:**
   - *Risk:* Deleting a trip when alternative counts have changed causes `409 STALE_CONFIRMATION`.
   - *Mitigation:* The delete confirmation modal captures exact `expectedDraftCount` and `expectedPlannedCount` from the server trip aggregate and sends them with `confirmed: true`. On 409, the UI catches the error, notifies the user, and refreshes the profile projection.
5. **Horizontal Scrolling on Small Viewports:**
   - *Risk:* Complex nested cards or wide date labels could cause horizontal page overflow on 320px screens.
   - *Mitigation:* Using `width: min(100%, ...)`, `flex-wrap: wrap`, CSS grid with minmax, and `overflow-wrap: anywhere` ensures responsive layout without horizontal scrolling.
6. **Focus Trapping and Screen-Reader Clarity:**
   - *Risk:* Modals failing to trap focus or focus being lost when items are deleted degrades keyboard accessibility.
   - *Mitigation:* Accessible dialog component with focus trapping, `aria-modal="true"`, Escape key handling, and focus restoration to the trigger element or parent section heading.

## Implementation Approach
- **Master-Detail & Workspace Architecture:**
  - `ProfileScreen.tsx` manages view mode: `'overview'` (default profile showing email, password change, empty state or Upcoming/Past trip list) and `'workspace'` (focused trip workspace for a selected or newly created trip).
  - Both views share the same shell, header, logout, status notices, and About this demo tab.
  - A breadcrumb / "Back to all trips" button in workspace mode smoothly returns to overview and refreshes profile projections.
- **Component Decomposition:**
  - `frontend/src/api/tripsApi.ts`: Types and API methods for all Trip and Alternative endpoints.
  - `frontend/src/components/EmptyProfileState.tsx`: Informative empty state explaining Trips with "Plan Trip" button.
  - `frontend/src/components/TripListSection.tsx`: Renders Upcoming and Past sections, trip cards with primary derived label, badges, summary counts, and nested alternative summaries.
  - `frontend/src/components/TripCreateModal.tsx`: Accessible dialog collecting destination, March 2027 dates, and traveler count.
  - `frontend/src/components/TripWorkspace.tsx`: Main trip view with mutable shared details (ages, budget, dates/destination when allowed), autosave indicator, and alternatives list.
  - `frontend/src/components/AlternativeCard.tsx`: Card for Draft (editable, version, duplicate action, delete action) or Planned (visibly read-only, duplicate to draft action, delete action).
  - `frontend/src/components/RevisionSummaryBanner.tsx`: Accessible notification displaying removals and adjustments when returned by the backend.
  - `frontend/src/components/TripRevisionModal.tsx`: Revision flow for trips with Planned alternatives.
  - `frontend/src/components/ConfirmDeleteModal.tsx`: Reusable accessible confirmation dialog for Draft, Planned, and Trip deletions.
- **State & Autosave Mechanics:**
  - Autosave hook/manager with 600ms debounce for traveler ages and budget.
  - Accessible announcement via `StatusRegion` ("Saving changes…", "All changes saved.") without moving keyboard focus.
  - Concurrency conflict handling with explicit "Reload" option and input retention.

---

## Phase 1: Domain Types, API Client, and Profile Projection Integration

### Changes
- [x] `frontend/src/api/tripsApi.ts` — Define complete TypeScript interfaces for `TripProfileSummary`, `AlternativeProfileSummary`, `TripResponse`, `DraftResponse`, `PlannedResponse`, `AlternativeResponse`, `RevisionSummaryResponse`, `ComponentRemovalResponse`, `ComponentAdjustmentResponse`, and strict request types (`CreateTripRequest`, `SharedDetailsUpdateRequest`, `TripRevisionRequest`, `DraftCreateRequest`, `DraftMutationRequest`, `AlternativeDuplicateRequest`, `AlternativeDeleteRequest`, `TripDeleteRequest`).
- [x] `frontend/src/api/tripsApi.ts` — Implement API functions utilizing the CSRF token and `IdentityApiError` handling:
  - `createTrip(payload: CreateTripRequest): Promise<TripResponse>`
  - `getTrip(tripId: string): Promise<TripResponse>`
  - `replaceSharedDetails(tripId: string, payload: SharedDetailsUpdateRequest): Promise<TripResponse>`
  - `duplicateTrip(tripId: string, payload: TripRevisionRequest): Promise<TripResponse>`
  - `createDraft(tripId: string, payload: DraftCreateRequest): Promise<TripResponse>`
  - `duplicateDraft(tripId: string, draftId: string, payload: DraftMutationRequest): Promise<TripResponse>`
  - `duplicateAlternative(tripId: string, alternativeId: string, payload: AlternativeDuplicateRequest): Promise<TripResponse>`
  - `deleteDraft(tripId: string, draftId: string, payload: DraftMutationRequest): Promise<TripResponse>`
  - `deleteAlternative(tripId: string, alternativeId: string, payload: AlternativeDeleteRequest): Promise<TripResponse>`
  - `deleteTrip(tripId: string, payload: TripDeleteRequest): Promise<void>`
- [x] `frontend/src/api/identityApi.ts` — Update `Profile` type to include `upcoming: TripProfileSummary[]` and `past: TripProfileSummary[]`, safely defaulting to `[]` if omitted in response so existing tests remain green.
- [x] `frontend/src/api/tripsApi.test.ts` — Unit tests verifying correct URL, HTTP method, headers (CSRF, Content-Type), and payload serialization for all trip operations, including strict omission of unexpected fields.

### Automated verification
- [x] `npm.cmd test -- src/api/tripsApi.test.ts` — All API client tests pass.
- [x] `npm.cmd run build` — TypeScript compilation succeeds without errors.

### Optional developer checks
- [x] Verify that existing `identityApi.test.ts` passes without modification.

---

## Phase 2: Empty Profile State, Trip Creation Modal, and Sectioned Profile Projections

### Changes
- [x] `frontend/src/components/EmptyProfileState.tsx` — Create component rendering `"Your profile is ready"` heading, explanatory copy about Trips and planning travel options from PDX to San Francisco, Munich, or Mexico City, and a primary `"Plan Trip"` button.
- [x] `frontend/src/components/TripCreateModal.tsx` — Create accessible modal dialog with:
  - Origin airport fixed to `PDX`.
  - Destination select: `destination-sfo` (San Francisco), `destination-muc` (Munich), `destination-mex` (Mexico City).
  - Dates: start and end date inputs validated for March 1–31, 2027 and 1–14 nights duration.
  - Traveler count: input validated for 1–8 travelers.
  - Inline field validation and error messages.
  - Focus trap, Escape key handling, and focus recovery.
  - Calls `tripsApi.createTrip`, handles errors, and passes the created `TripResponse` to the parent.
- [x] `frontend/src/components/TripListSection.tsx` — Create component rendering "Upcoming trips" and "Past trips" sections from `profile.upcoming` and `profile.past`:
  - Each trip card renders:
    - Primary label: derived label (e.g. `San Francisco — Mar 10–14, 2027`).
    - Secondary details: destination name, formatted dates, traveler count.
    - Badges: Temporal status (`Upcoming` / `Past`), `Expired` (if expired).
    - Summary counts: Draft count, Planned count, Expired alternative count, and Booked count (only when > 0, from backend).
    - Nested alternative summaries: compact list showing alternative ID, lifecycle (`Draft` / `Planned`), and `Expired` status.
    - Actions: `"Open trip"` button (switches to Workspace) and `"Delete trip"` button.
- [x] `frontend/src/components/ProfileScreen.tsx` — Integrate `EmptyProfileState`, `TripCreateModal`, and `TripListSection`. Retain email display, password change section, and logout button.

### Automated verification
- [x] `npm.cmd test -- src/App.test.tsx` — Existing profile tests pass with empty state and retained password change.
- [x] `npm.cmd run build` — Clean production build.

### Optional developer checks
- [x] Verify that a user with 0 trips sees the empty state with "Plan Trip", and clicking "Plan Trip" opens the creation modal.

---

## Phase 3: Trip Workspace, Shared Details Editing, Autosave, and Concurrency Conflict Handling

### Changes
- [x] `frontend/src/components/TripWorkspace.tsx` — Create trip workspace view:
  - Navigation: `"← Back to all trips"` button returning to profile list.
  - Header: derived trip label (`h1` or primary heading), temporal status badge, and `Expired` badge.
  - Form for mutable shared details:
    - Destination select and Date inputs: editable only if `trip.planned.length === 0`. If Planned alternatives exist, fields are disabled with an explanatory note and `"Revise Trip"` button.
    - Traveler count: integer 1–8.
    - Traveler ages: dynamically renders an age input (0–120) for each traveler (1 to `travelerCount`).
    - Budget: input in dollars, formatted/converted to integer cents (0 to 100,000,000 cents / $1,000,000.00).
  - Autosave manager:
    - 600ms debounce on input changes.
    - Sends `PUT /api/trips/{tripId}` with `expectedVersion`.
    - Updates local `trip.version` on success.
    - Status indicator: `Saving…` and `Saved` announced via live region without stealing focus.
    - Failure handling: preserves user inputs; never clears unsaved input.
    - Version conflict (`409 VERSION_CONFLICT`): displays conflict alert explaining newer data exists on the server, retains user's uncommitted values, and provides a `"Reload from server"` button that fetches `GET /api/trips/{tripId}`.
- [x] `frontend/src/components/ProfileScreen.tsx` — Wire workspace view mode: selecting a trip from the list or creating a new trip opens `TripWorkspace`.

### Automated verification
- [x] `npm.cmd test` — Tests for autosave debounce, saving states, input retention on validation error, and 409 conflict reload pass.
- [x] `npm.cmd run build` — Clean production build.

### Optional developer checks
- [x] Test typing in budget or traveler age and verify "Saving…" appears followed by "Saved" in the status indicator.

---

## Phase 4: Alternatives Management, Read-Only Planned Snapshots, and Revision Summaries

### Changes
- [x] `frontend/src/components/AlternativeCard.tsx` — Component for alternative items:
  - Header with lifecycle badge (`Draft` or `Planned`), version number (for drafts), and `Expired` badge (if expired).
  - For Drafts:
    - Clear `"Draft alternative"` label.
    - Action: `"Duplicate to new draft"` button calling `tripsApi.duplicateDraft` (or `duplicateAlternative`).
    - Action: `"Delete draft"` button opening delete confirmation.
  - For Planned:
    - Visibly marked as `"Planned itinerary (read-only)"`.
    - Read-only selections indicator (components are snapshot-locked).
    - Action: `"Duplicate to draft"` calling `tripsApi.duplicateAlternative` to route edits through a new draft.
    - Action: `"Delete planned itinerary"` button opening delete confirmation.
- [x] `frontend/src/components/TripWorkspace.tsx` — Add alternatives header with:
  - Visibly distinct `"Create empty draft"` button calling `tripsApi.createDraft({ expectedVersion: trip.version })`.
  - Distinguish empty draft creation from duplication so users never duplicate accidentally.
- [x] `frontend/src/components/RevisionSummaryBanner.tsx` — Accessible alert/card displaying `RevisionSummaryResponse`:
  - Lists every removed component with its reason (e.g. flight dates no longer match).
  - Lists every adjusted component with change type, previous/new unit counts, and price differences.
  - Provides an `"Acknowledge"` / `"Dismiss"` button.
- [x] `frontend/src/components/TripRevisionModal.tsx` — Modal for revising trips with Planned alternatives:
  - Allows selecting which source planned itineraries to copy (`sourcePlannedItineraryIds`).
  - Inputs for revised destination, dates, traveler count, ages, budget.
  - Submits `POST /api/trips/{tripId}/duplicate`.
  - On success, switches workspace to the newly created revised trip and displays the revision summary.

### Automated verification
- [x] `npm.cmd test` — Tests for empty draft creation, draft duplication, planned duplication to draft, and revision summary rendering pass.
- [x] `npm.cmd run build` — Clean production build.

### Optional developer checks
- [x] Verify that planned alternatives do not render any editable inputs and clearly offer only "Duplicate to draft".

---

## Phase 5: Scoped Deletion Confirmations and Booking History Safeguards

### Changes
- [x] `frontend/src/components/ConfirmDeleteModal.tsx` — Accessible confirmation dialog with three specialized modes:
  1. `Delete Draft`:
     - Explains: `"Permanently delete this draft alternative? Other alternatives will remain."`
     - Calls `tripsApi.deleteDraft(tripId, draftId, { expectedVersion, expectedDraftVersion })`.
  2. `Delete Planned Itinerary`:
     - Explains: `"Permanently delete this planned itinerary snapshot?"`
     - Calls `tripsApi.deleteAlternative(tripId, alternativeId, { expectedVersion, confirmed: true })`.
  3. `Delete Trip`:
     - Explains: `"Permanently delete <Trip Label> and all its contents?"`
     - Lists server-reported counts: `"{draftCount} Draft alternative(s) and {plannedCount} Planned itinerary(ies) will be removed."`
     - Calls `tripsApi.deleteTrip(tripId, { expectedVersion, expectedDraftCount, expectedPlannedCount, confirmed: true })`.
     - Guard: If `trip.hasBookingHistory === true`, deletion is blocked with message: `"Trips with booking history cannot be permanently deleted."`
     - Handles `409 STALE_CONFIRMATION`: alerts the user that alternative counts have changed on the server, updates local state, and refreshes the view.
- [x] Focus management: Focus traps inside modal; on close or cancel, focus returns to the triggering button; on successful trip deletion, focus moves to the trips section heading or "Plan Trip" button.

### Automated verification
- [x] `npm.cmd test` — Tests for draft deletion, planned deletion, trip deletion with accurate counts, booking history guard, and stale confirmation handling pass.
- [x] `npm.cmd run build` — Clean production build.

### Optional developer checks
- [x] Confirm that deleting a trip updates the list immediately and announces deletion in the status region.

---

## Phase 6: Accessibility, Responsive Styling, and Polish

### Changes
- [x] `frontend/src/style.css` — Extend CSS:
  - Add workspace container styling with responsive max-width (`min(100%, 56rem)`) allowing cards to expand on desktop while remaining 100% on mobile.
  - Card hierarchy: primary trip derived label (`h2`/`h3`), distinct status badges (`.badge-upcoming`, `.badge-past`, `.badge-draft`, `.badge-planned`, `.badge-expired`), secondary actions.
  - Mobile responsiveness: flexbox wrap and CSS grid with `minmax` to guarantee no horizontal scrollbar down to 320px viewport width.
  - Dialog and backdrop styles for accessible modals (`role="dialog"`), focus rings (`:focus-visible` with `#e36d27`).
  - Autosave status indicator styling (`.autosave-status`).
  - Revision summary styling (`.revision-summary`).
- [x] Screen reader & keyboard audit:
  - Ensure all headings have correct semantic levels (`h1` -> `h2` -> `h3`).
  - Ensure form inputs have programmatic labels and error descriptions (`aria-describedby`).
  - Ensure status regions use `role="status"` and `aria-live="polite"` so they do not steal user focus.

### Automated verification
- [x] `npm.cmd test` — Full Vitest test suite passes.
- [x] `npm.cmd run build` — Production build passes.
- [x] `.\mvnw.cmd test-compile` — Maven packaging and compilation pass.

### Optional developer checks
- [x] Resize viewport to 320px and 1280px in browser to verify layout responsiveness and absence of horizontal scrolling.

---

## Test Strategy
- **API Client Layer Tests (`frontend/src/api/tripsApi.test.ts`):**
  - Verify strict JSON request construction (no disallowed properties).
  - Verify CSRF token propagation and error envelope parsing.
- **Frontend Interaction Tests (`frontend/src/App.test.tsx` and `frontend/src/components/*.test.tsx`):**
  - New user empty state rendering and "Plan Trip" trigger.
  - Trip creation with valid destination, dates, and traveler count.
  - Validation rejections for invalid dates/traveler count without losing inputs.
  - Profile projection display: Upcoming vs Past trips ordered by start date with nested alternatives.
  - Expired alternative badges when departure midnight has passed.
  - Autosave debouncing, status transitions (`Saving…` -> `Saved`), and input retention on network/validation errors.
  - Concurrency conflict handling (`409 VERSION_CONFLICT`): retains unsaved edits, shows conflict banner, reload button updates state.
  - Alternative operations: create component-empty draft, duplicate draft, duplicate planned to draft.
  - Read-only planned alternative display.
  - Revision workflow and revision summary presentation (removals and adjustments).
  - Scoped deletion confirmation dialogs for Draft, Planned, and Trip with server-reported counts.
  - Deletion rejection when `hasBookingHistory` is true.
  - Stale deletion confirmation handling (`409 STALE_CONFIRMATION`).
  - Accessibility: modal focus trapping, Escape key closing, focus recovery, keyboard navigation.
  - Regression assurance: all 9 existing identity tests pass.
- **Full Safe Verification Commands:**
  - `npm.cmd test`
  - `npm.cmd run build`
  - `.\mvnw.cmd test-compile`

---

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| 1. Informative empty profile and Trip creation with initial Draft | `EmptyProfileState.tsx`, `TripCreateModal.tsx`, `tripsApi.createTrip` | Test in `App.test.tsx`: empty state renders, clicking Plan Trip opens modal, submitting creates trip and renders in Upcoming section |
| 2. Multiple Drafts, duplication, shared-details editing, and autosave states (`Saving`, `Saved`, validation error, network error, version conflict) | `TripWorkspace.tsx`, `tripsApi.replaceSharedDetails`, `StatusRegion.tsx` | Tests in `TripWorkspace.test.tsx` / `App.test.tsx`: creating draft, duplicating draft, editing ages/budget, observing debounced save, failure retention, and 409 conflict reload |
| 3. Upcoming and Past Trips in backend order with clear nested hierarchy and accurate Draft, Planned, Expired, and Booked information | `TripListSection.tsx`, `AlternativeCard.tsx`, `ProfileScreen.tsx` | Test in `App.test.tsx`: renders upcoming and past trips from profile projection with nested alternatives and correct badges; no fabricated booking data |
| 4. Planned alternatives visibly read-only, revision workflow preserves source Trip, and change summary names removals/reasons | `AlternativeCard.tsx`, `TripRevisionModal.tsx`, `RevisionSummaryBanner.tsx` | Tests in `TripWorkspace.test.tsx`: planned cards are read-only with "Duplicate to draft"; revising trip calls `duplicateTrip` and renders `RevisionSummaryBanner` |
| 5. Scoped Delete Draft, Delete Planned, and Delete Trip confirmations with server counts and stale/rejected deletion handling | `ConfirmDeleteModal.tsx`, `tripsApi.delete*` | Tests in `App.test.tsx`: deleting draft, deleting planned, deleting trip with server draft/planned counts, blocking booked trip deletion, handling stale counts |
| 6. Keyboard and screen reader semantics, focus recovery, and responsive layout without horizontal scrolling | `style.css`, focus management in `ConfirmDeleteModal.tsx` and `TripCreateModal.tsx`, `aria-live` | Tests for focus trap, Escape key, focus restoration, semantic landmarks; CSS layout verification |
| 7. Frontend interaction tests cover major success/error/conflict/destructive paths; build passes | `App.test.tsx`, `tripsApi.test.ts`, `TripWorkspace.test.tsx` | `npm.cmd test`, `npm.cmd run build`, `.\mvnw.cmd test-compile` pass |
| 8. Existing registration, login, logout, password change, CSRF, and About demo remain intact | Retained `ProfileScreen.tsx` password form, `AboutDemoTab.tsx`, `identityApi.ts` | All 9 existing `App.test.tsx` identity tests continue to pass |
| 9. No Phase 4–6 experiences introduced | Scope boundaries in all new components | Inspection of all components: zero search results, comparison UI, or booking flows |

---

## Risks and Rollback/Recovery
- **Rollback:** All changes are localized to `frontend/src`. Reverting the git commit returns the profile to the pre-P03-T06 baseline without touching database schemas or backend code.
- **Strict Validation Guard:** Payloads sent by `tripsApi.ts` are strictly typed to omit any extra fields, preventing `400 VALIDATION_FAILED` errors from `TripRequests.java`.
- **Concurrency Safety:** Autosave never overwrites server state when versions diverge; explicit reload ensures user awareness before overwriting.

---

## References
- Ticket: `c:\code\loomspan-travel-demo\ai\thoughts\tickets\2026-09-19-p03-t06-deliver-trips-profile-experience.md`
- Research: `c:\code\loomspan-travel-demo\ai\thoughts\research\2026-09-19-p03-t06-deliver-trips-profile-experience.md`
- Backend Controllers & Services: `src/main/java/app/detour/trip/TripController.java`, `TripService.java`, `TripRequests.java`, `IdentityController.java`
- Existing Frontend: `frontend/src/App.tsx`, `ProfileScreen.tsx`, `identityApi.ts`, `style.css`
