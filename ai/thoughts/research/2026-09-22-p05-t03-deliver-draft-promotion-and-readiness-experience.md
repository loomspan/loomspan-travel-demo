---
date: 2026-09-22
repository: loomspan-travel-demo
branch: main
commit: 7aed48e95ef9cfb2597cfa35263c9208b93f86e3
ticket: ai/thoughts/tickets/2026-09-22-p05-t03-deliver-draft-promotion-and-readiness-experience.md
tags: [frontend, trip-workspace, progressive-builder, draft-promotion, readiness, budget-overage, modal, accessibility, vitest]
---

# Draft Promotion, Actionable Readiness, and Budget Overage Acknowledgment Research

## Research Question

How does the current codebase support draft promotion, readiness inspection, budget-overage warning and acknowledgment, and alternative lifecycle transitions across the progressive trip builder workspace and alternative cards? What exact backend contracts, frontend components, state flows, DOM hooks, and test suites exist today?

## Summary

The backend infrastructure for draft readiness validation, planned snapshot persistence, and budget-overage acknowledgment was established in P05-T02 (`104a60a`). The backend exposes two primary endpoints:
1. `GET /api/trips/{tripId}/drafts/{draftId}/readiness` which evaluates blocking issues (destination, trip dates, traveler count, traveler ages, adult traveler, budget, selected components, and live catalog availability for seats, stay nights, and car rentals) and budget overage status.
2. `POST /api/trips/{tripId}/drafts/{draftId}/plan` which validates readiness and budget overage acknowledgment (`budgetOverageAcknowledged: true`), advances the trip and draft versions, creates an immutable Planned itinerary snapshot in Flyway V16 tables, and returns the updated `TripResponse` (HTTP 201).

The frontend API client (`frontend/src/api/tripsApi.ts`) already defines the TypeScript types (`DraftReadinessResponse`, `PromotionRequest`, `AlternativeResponse`, `TripResponse`) and client methods (`tripsApi.getDraftReadiness`, `tripsApi.promoteDraft`). However, the user interface currently lacks any promotion trigger in the Progressive Trip Builder workspace or on `AlternativeCard`. Furthermore, there is no readiness summary banner to present blocking issues or guide users to invalid fields/slots, no budget overage confirmation modal with required checkbox acknowledgment, and no client-side state handling for resetting acknowledgments upon user edits or handling version conflicts during promotion.

## Repository State

- **Date:** 2026-09-22
- **Repository:** `loomspan-travel-demo`
- **Current Branch:** `main`
- **Commit:** `7aed48e95ef9cfb2597cfa35263c9208b93f86e3` (clean working tree)
- **Active Test Suites:** 
  - Frontend (`npm.cmd test`): 5 test files, 55 tests passing in Vitest (`identityApi.test.ts`, `tripsApi.test.ts`, `PasswordField.test.tsx`, `ProgressiveTripBuilder.test.tsx`, `App.test.tsx`).
  - Backend (`mvn test`): Complete Spring Boot MockMvc integration tests passing in `DraftReadinessAndPlannedSnapshotIntegrationTest.java`.

## Current Behavior and Data Flow

### 1. Backend Readiness & Promotion Pipeline
- **Readiness inspection (`TripService.java:605-727`):**
  - Endpoint: `GET /api/trips/{tripId}/drafts/{draftId}/readiness`.
  - Evaluates blocking issues via `evaluateDraftBlockingIssues`:
    - `destination`: Destination must be present and supported.
    - `dates`: Must be within March 1–31, 2027, 1–14 nights, start < end, not expired (`startDate.isBefore(clock.today())`).
    - `travelerCount`: Must be between 1 and 8.
    - `travelerAges`: Every traveler must have an age in `[0, 120]`.
    - `adult`: At least one traveler must be age 18 or older.
    - `budgetCents`: Must be non-null and `>= 0`.
    - `components`: At least one reservable component (airfare, stay, or rental) must be selected.
    - `airfare`: Verifies outbound and return flight instances exist, route matches destination, departure/return dates match trip interval, and `available_seats >= travelerCount`.
    - `stay`: Verifies unit matches destination, guest capacity matches party size, required nights match trip interval, and `available_inventory >= unitCount` for all nights.
    - `rental`: Verifies rental unit exists, matches destination, pickup < return, within trip interval, at least one traveler 25+, and unit available over interval.
  - Tally check: Evaluates `isOverBudget` and `budgetOverageCents` via `ItineraryTallyEngine`.
  - Response payload:
    ```json
    {
      "ready": false,
      "blockingIssues": { "travelerAges": "...", "components": "..." },
      "isOverBudget": false,
      "budgetOverageCents": 0,
      "requiresOverageAcknowledgment": false
    }
    ```
- **Promotion to Planned snapshot (`TripService.java:280-309`):**
  - Endpoint: `POST /api/trips/{tripId}/drafts/{draftId}/plan`.
  - Request body:
    ```json
    {
      "expectedVersion": 0,
      "expectedDraftVersion": 0,
      "budgetOverageAcknowledged": true
    }
    ```
  - Validation steps:
    1. Rejects expired trips with HTTP 400 `ALTERNATIVE_EXPIRED` ("Expired alternatives cannot be promoted.").
    2. Re-evaluates blocking issues. If non-empty, rejects with HTTP 400 `PLANNING_NOT_READY` ("The Draft is not ready to be planned.") and populates error `fields` with the issue map.
    3. Re-evaluates tally. If `tally.isOverBudget()` and `budgetOverageAcknowledged` is not `Boolean.TRUE`, rejects with HTTP 400 `BUDGET_OVERAGE_UNACKNOWLEDGED` and returns error `fields` containing `grandTotalCents`, `budgetCents`, and `budgetOverageCents`.
    4. Advances version via `trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())`. If version mismatch, throws HTTP 409 `VERSION_CONFLICT`.
    5. Inserts immutable planned itinerary snapshot into `detour_planned_itinerary` and V16 tables (`detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, `detour_planned_rental_snapshot`).
    6. Returns HTTP 201 with full updated `TripResponse` containing incremented trip version, updated `planned` list, and updated `alternatives` list.

### 2. Frontend State and Lifecycle in `TripWorkspace.tsx`
- **Draft Selection & Management (`TripWorkspace.tsx:104`):**
  - `activeDraft` is derived as `trip.drafts && trip.drafts.length > 0 ? trip.drafts[0] : null`.
  - Component modes (`airfareMode`, `stayMode`, `rentalMode`) reflect selection states.
- **Autosave Pipeline (`TripWorkspace.tsx:263-365`):**
  - Debounced (600ms) execution of `tripsApi.replaceSharedDetails`.
  - Tracks `autosaveStatus` ('idle' | 'saving' | 'saved' | 'error' | 'conflict') and `autosaveMessage`.
  - When HTTP 409 `VERSION_CONFLICT` occurs, sets `autosaveStatus = 'conflict'` and provides a "Reload from server" button calling `handleReloadFromServer`.
- **Alternatives Rendering (`TripWorkspace.tsx:1006-1044`):**
  - Renders `AlternativeCard` for each entry in `trip.alternatives`.
  - Handles `onDuplicateDraft`, `onDuplicatePlanned`, `onDeleteDraft`, and `onDeletePlanned`.
  - Does NOT pass any promotion callback or render any promotion trigger.
- **Progressive Builder Section (`TripWorkspace.tsx:778-846`):**
  - Renders `ItinerarySummaryTally`, `AirfareSlot`, `StaySlot`, `RentalSlot`, and "Add a car" button.
  - Does NOT contain any "Promote to Planned" or "Save as Planned Itinerary" action.

### 3. AlternativeCard (`AlternativeCard.tsx`)
- Renders draft alternatives (`lifecycle === 'DRAFT'`) with `badge-draft` ("Draft v{version}") and planned itineraries (`lifecycle === 'PLANNED'`) with `badge-planned` ("Planned itinerary (read-only)").
- For planned itineraries, displays read-only notice: `"This planned alternative is snapshot-locked and read-only. Use 'Duplicate to draft' to make modifications."`
- Actions container (`alternative-card-actions`) currently includes:
  - Draft: "Delete draft" and "Duplicate to new draft".
  - Planned: "Delete planned itinerary" and "Duplicate to draft".
- Draft cards do NOT currently expose a promotion action.

### 4. ItinerarySummaryTally (`ItinerarySummaryTally.tsx`)
- Computes airfare, stay, and rental totals using client-side helpers (`computeAirfareTotalCents`, `computeStayTotalCents`, `computeRentalTotalCents`).
- Derives `isOverBudget = hasBudget && grandTotal > budgetCents` and `overageCents = grandTotal - budgetCents`.
- Renders a warning badge (`Over Budget`) with `role="alert"` when `isOverBudget` is true.

## Key Components

- `frontend/src/api/tripsApi.ts:100-106, 663-668` — Defines `DraftReadinessResponse`, `PromotionRequest`, `tripsApi.getDraftReadiness`, and `tripsApi.promoteDraft`.
- `frontend/src/components/TripWorkspace.tsx:778-846` — Progressive Trip Builder workspace hosting the summary tally and component slots.
- `frontend/src/components/TripWorkspace.tsx:848-1004` — Trip Details form managing destination, dates, budget (`#workspace-budget`), traveler count (`#workspace-traveler-count`), and traveler ages (`#traveler-age-${i}`).
- `frontend/src/components/TripWorkspace.tsx:1006-1044` — Alternatives section rendering `AlternativeCard` instances.
- `frontend/src/components/AlternativeCard.tsx:81-121` — Alternative card action buttons (duplicate, delete; currently missing promote).
- `frontend/src/components/ItinerarySummaryTally.tsx:47-124` — Persistent itinerary summary tally calculating totals, budget status, and overage.
- `frontend/src/components/AirfareSlot.tsx:42` — Airfare slot card with `#airfare-slot-heading` and `.airfare-slot`.
- `frontend/src/components/StaySlot.tsx:45` — Stay slot card with `#stay-slot-heading` and `.stay-slot`.
- `frontend/src/components/RentalSlot.tsx:41` — Rental car slot card with `#rental-slot-heading` and `.rental-slot`.
- `frontend/src/components/ConfirmDeleteModal.tsx:41-88` — Reference implementation for accessible modal dialogs with focus trapping, Escape handling, and restore-focus.
- `frontend/src/components/StatusRegion.tsx:1-7` — Accessible live region (`role="status" aria-atomic="true"`).
- `frontend/src/style.css:17-32, 101-159, 160-181` — Modal styles, alternative cards, builder slots, badges, tally rows, and mobile breakpoint (`@media (max-width: 520px)`).
- `src/main/java/app/detour/trip/TripService.java:280-309, 605-727` — Authoritative server logic for evaluating draft readiness, enforcing overage acknowledgment, and promoting drafts.
- `src/main/java/app/detour/trip/TripController.java:67-77` — REST endpoints for draft readiness inspection and promotion.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **Workspace Builder Section** (`TripWorkspace.tsx`) | Renders `ItinerarySummaryTally` and component slots; has no promotion button, no readiness feedback banner, and no jump navigation. |
| **Alternative Cards** (`AlternativeCard.tsx`) | Renders Draft cards with delete and duplicate buttons only; lacks "Save as Planned Itinerary" / "Promote to Planned" button. |
| **Readiness Feedback & Navigation** | No component or banner displays `blockingIssues` from the server; DOM inputs (`#traveler-age-${i}`, `#workspace-budget`, component slots) have no focus/scroll target logic linked to server error keys. |
| **Budget Overage Interception & Modal** | When grand total exceeds budget, promotion fails on the server if unacknowledged, but the client provides no modal dialog, no explicit checkbox acknowledgment, and no overage breakdown. |
| **Acknowledgment Invalidation** | Client does not track or reset overage acknowledgment state when form fields (destination, dates, travelers, budget, or components) change. |
| **Workspace State Synchronization** | Successful promotion returns an updated `TripResponse` with the newly inserted `planned` item, but `TripWorkspace` currently lacks a handler to process this transition, update state, and announce success via `aria-live`. |
| **Version Conflict Handling (409)** | Exists for autosave and slot mutations, but is not yet wired to promotion failure handling. |
| **Styles & Responsive Layout** (`style.css`) | Contains modal and slot styling, but lacks dedicated classes for the readiness banner, budget overage dialog, overage highlighting, and responsive stacking for promotion controls. |

## Existing Tests and Fixtures

- `frontend/src/ProgressiveTripBuilder.test.tsx`:
  - 14 tests verifying trip creation flows (Airfare, Stay, Plan Trip entry points), flight search, sort/filter options, slot state transitions, and component removal modals.
  - Uses `vi.stubGlobal('fetch', fetchMock)`, `createMockTrip()`, and testing-library userEvent.
- `frontend/src/App.test.tsx`:
  - 27 integration tests verifying authentication, empty profile onboarding, debounced autosave, optimistic concurrency conflicts (409 `VERSION_CONFLICT`), inline field validations, and trip revisions.
- `frontend/src/api/tripsApi.test.ts`:
  - Tests 9 and 10 verify `tripsApi.getDraftReadiness` and `tripsApi.promoteDraft` HTTP requests, URLs, headers, CSRF token inclusion, and payload serialization.
- `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`:
  - 14 comprehensive backend MockMvc integration tests verifying all readiness rules, missing field groupings, catalog staleness/inventory exhaustion, overage acknowledgment enforcement, and snapshot persistence.

## Dependencies and Operational Constraints

- **Backend Readiness Contract:**
  - `GET /api/trips/{tripId}/drafts/{draftId}/readiness`: returns `ready` (boolean), `blockingIssues` (map of string key to descriptive message), `isOverBudget` (boolean), `budgetOverageCents` (integer), and `requiresOverageAcknowledgment` (boolean).
  - Issue keys returned by server: `"destination"`, `"dates"`, `"travelerCount"`, `"travelerAges"`, `"adult"`, `"budgetCents"`, `"components"`, `"airfare"`, `"stay"`, `"rental"`.
- **Backend Promotion Contract:**
  - `POST /api/trips/{tripId}/drafts/{draftId}/plan`: accepts `expectedVersion` (trip version), `expectedDraftVersion` (draft version), and optional `budgetOverageAcknowledged` (boolean).
  - Error responses:
    - 400 `ALTERNATIVE_EXPIRED`: trip departure date is in the past.
    - 400 `PLANNING_NOT_READY`: draft has blocking issues; error `fields` contains issue map.
    - 400 `BUDGET_OVERAGE_UNACKNOWLEDGED`: itinerary exceeds budget without acknowledgment; error `fields` contains `grandTotalCents`, `budgetCents`, and `budgetOverageCents`.
    - 409 `VERSION_CONFLICT`: trip or draft version has changed.
- **Accessibility & Focus Constraints:**
  - Modal dialogs must specify `role="dialog"`, `aria-modal="true"`, `aria-labelledby`, trap Tab focus within the modal, dismiss on `Escape`, and restore focus to the trigger element when closed.
  - Status updates (saving, saved, errors, conflicts) must announce to assistive technology via `aria-live="polite"`.
  - Issue jump actions must move DOM focus directly to the target element (`document.getElementById(...).focus()`) and scroll into view.

## Historical Context

- `104a60a` (P05-T02): Delivered authoritative readiness validation engine, Flyway V16 snapshot schema, catalog inventory availability checks, and the `readiness` and `plan` backend endpoints.
- `381a94d` (P05-T01): Implemented canonical pricing engine and server tally calculation, establishing consistent tally calculations across drafts and planned snapshots.
- `de1c525`: Outlined Phase 5 scope, defining P05-T03 as the user-facing experience connecting the progressive builder and alternative cards to backend readiness and promotion.

## Open Questions

- *None for research.* The backend contracts, API client methods, error codes, DOM structures, and modal accessibility patterns are completely documented and verified in source code and passing test suites. Ready for planning.
