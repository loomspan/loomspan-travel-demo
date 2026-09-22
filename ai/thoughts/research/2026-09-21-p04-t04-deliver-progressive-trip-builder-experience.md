---
date: 2026-09-22
repository: loomspan-travel-demo
branch: main
commit: a12dbdc2bb31452126d3ed4f0006e78c07ce9d6c
ticket: ai/thoughts/tickets/2026-09-21-p04-t04-deliver-progressive-trip-builder-experience.md
tags: [frontend, progressive-builder, airfare, stay, rental-car, component-selection, vitest, react]
---

# Progressive Trip-Builder and Component Selection Experience Research

## Research Question

How does the current DeTour codebase support the progressive trip builder and component selection experience (P04-T04), including authenticated entry points (Plan Trip, Airfare, Stay), component search and draft selection/removal for airfare (P04-T01), stays (P04-T02), and rental cars (P04-T03), real-time persistent itinerary summary and budget tally, confirmation dialogs, error/concurrency handling, and frontend test harnesses?

## Summary

The backend foundation for search and selection is complete, tested, and active across Spring Boot controllers and services:
- **Airfare (P04-T01):** Endpoints `GET /api/trips/{tripId}/drafts/{draftId}/airfare`, `PUT .../airfare`, and `DELETE .../airfare` are fully implemented with seat availability filtering, deterministic ranking, sort overrides (`DEFAULT`, `LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, `FEWEST_STOPS`), and round-trip flight combination selections.
- **Stay (P04-T02):** Endpoints `GET /api/trips/{tripId}/drafts/{draftId}/stays`, `PUT .../stays`, and `DELETE .../stays` require mandatory accommodation type selection (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`), calculate required room count, filter inventory capacity, rank by budget fit and rating, and support sort overrides (`DEFAULT`, `LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`).
- **Rental Car (P04-T03):** Endpoints `GET /api/trips/{tripId}/drafts/{draftId}/rentals`, `PUT .../rentals`, and `DELETE .../rentals` require local pickup and return date/times within trip dates, compute consecutive 24-hour billing cycles, validate driver age eligibility (at least one traveler 25+), disable selection when ineligible with standard explanation, and rank options by class and price.

The frontend (`frontend/`) is a React 19 / TypeScript / Vite application with Vitest and `@testing-library/react`. Currently, it only implements the Phase 3 profile and trip details workspace (`TripWorkspace.tsx`), containing shared trip details autosave, alternative list cards, and trip revision/deletion modals. It currently lacks:
1. Entry point triggers and flows for "Airfare" and "Stay" alongside "Plan Trip" in `ProfileScreen.tsx`, `TripListSection.tsx`, `EmptyProfileState.tsx`, and `TripCreateModal.tsx`.
2. Client API methods and types in `tripsApi.ts` for airfare, stay, and rental search, selection, and removal.
3. The progressive builder workspace shell in `TripWorkspace.tsx` managing component slots (Airfare, Stay, and initially hidden Rental Car).
4. Interactive search and selection interfaces for flights, stays, and cars.
5. Persistent itinerary summary and budget tally with real-time grand totals and remaining/overage indicators.
6. Explicit confirmation dialogs when discarding already saved selections.

## Repository State

- **Date:** 2026-09-22
- **Repository:** `loomspan-travel-demo`
- **Branch:** `main`
- **Commit:** `a12dbdc2bb31452126d3ed4f0006e78c07ce9d6c` (`clean up from P4-T03`)
- **Working Tree:** Clean (no uncommitted or untracked changes).
- **Test Status:** 
  - Frontend Vitest suite: 4 test files, 36 tests passing (`npm.cmd test`).
  - Backend integration tests: All catalog, identity, trip, airfare, stay, and rental test suites passing.

## Current Behavior and Data Flow

### 1. Trip and Draft Lifecycle

```
[Authenticated Profile Screen]
  ├── Entry points: Plan Trip | Airfare | Stay
  │     └── TripCreateModal (destination, dates [2027-03-01..2027-03-31, 1-14 nights], travelers [1-8])
  │           └── (if Stay: also accommodationType [HOTEL | BED_AND_BREAKFAST | VACATION_RENTAL])
  │                 └── POST /api/trips -> creates Trip + Draft 0
  │                       └── Opens TripWorkspace with active Draft
  │
[TripWorkspace]
  ├── Shared Details (destination, dates, travelers, ages, budget) with debounced autosave
  ├── Persistent Itinerary Summary & Budget Tally (Airfare $, Stay $, Rental $, Grand Total, Budget vs Overage)
  ├── Progressive Component Builder Slots:
  │     ├── Airfare Slot: [Empty: "Add airfare"] | [Searching: Filter & Sort] | [Selected: Details + Change/Remove]
  │     ├── Stay Slot:    [Empty: "Add stay"]    | [Searching: Type & Sort]   | [Selected: Details + Change/Remove]
  │     └── Rental Slot:  [Hidden upfront] -> Click "Add a car" -> [Searching: Pickup/Return] | [Selected: Details + Change/Remove]
  └── Removal Confirmation Modal (required only when discarding saved components)
```

1. **Trip Creation:**
   - Client sends `POST /api/trips` with `{ destinationKey, startDate, endDate, travelerCount, travelerAges?, budgetCents? }`.
   - Backend `TripService.create(...)` creates a `Trip` record with an initial `TripDraft` (version 0, all selections null) and returns `TripResponse`.
2. **Component Search:**
   - Client sends `GET /api/trips/{tripId}/drafts/{draftId}/airfare` with `directOnly` and `sort`.
   - Client sends `GET /api/trips/{tripId}/drafts/{draftId}/stays` with `type` and `sort`.
   - Client sends `GET /api/trips/{tripId}/drafts/{draftId}/rentals` with `pickupAt`, `returnAt`, and `sort`.
3. **Component Selection Mutation:**
   - Client sends `PUT /api/trips/{tripId}/drafts/{draftId}/airfare` with `{ expectedVersion, expectedDraftVersion, outboundFlightInstanceId, returnFlightInstanceId }`.
   - Client sends `PUT /api/trips/{tripId}/drafts/{draftId}/stays` with `{ expectedVersion, expectedDraftVersion, accommodationUnitId, unitCount }`.
   - Client sends `PUT /api/trips/{tripId}/drafts/{draftId}/rentals` with `{ expectedVersion, expectedDraftVersion, rentalUnitId, pickupAt, returnAt }`.
   - Backend increments `draft.version`, saves selection, and returns full `TripResponse`.
4. **Component Removal Mutation:**
   - Client sends `DELETE /api/trips/{tripId}/drafts/{draftId}/airfare` with `{ expectedVersion, expectedDraftVersion }`.
   - Client sends `DELETE /api/trips/{tripId}/drafts/{draftId}/stays` with `{ expectedVersion, expectedDraftVersion }`.
   - Client sends `DELETE /api/trips/{tripId}/drafts/{draftId}/rentals` with `{ expectedVersion, expectedDraftVersion }`.
   - Backend increments `draft.version`, deletes selection, and returns full `TripResponse`.
5. **Concurrency Control:**
   - All draft mutations require `expectedVersion` (trip version) and `expectedDraftVersion` (draft version).
   - If mismatch occurs, backend throws 409 `VERSION_CONFLICT` with current version numbers.

## Key Components

- [`TripController.java:85-227`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripController.java#L85-L227) — Spring MVC endpoints for airfare, stay, and rental car search, selection (PUT), and removal (DELETE).
- [`TripRequests.java:31-99`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripRequests.java#L31-L99) — Request records and validation parsers: `AirfareSelectionRequest`, `StaySelectionRequest`, `RentalSelectionRequest`, and `DraftMutation`.
- [`TripService.java:552-870`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripService.java#L552-L870) — Domain validation, budget deduction calculations, concurrency checks, repository selection persistence, and response building for all three components.
- [`AirfareSearchResponses.java:8-79`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/airfare/AirfareSearchResponses.java#L8-L79) — Airfare search contracts including `FlightCombinationResponse`, `FlightLegResponse`, and `PartyPricingResponse`.
- [`StaySearchResponses.java:8-75`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/stay/StaySearchResponses.java#L8-L75) — Stay search contracts including `StayOptionResponse`, `StayPricingResponse`, and `StayNightPricingResponse`.
- [`RentalSearchResponses.java:8-58`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/rental/RentalSearchResponses.java#L8-L58) — Rental car search contracts including `RentalOptionResponse` and `RentalPricingResponse`.
- [`RentalSearchService.java:20-22`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/rental/RentalSearchService.java#L20-L22) — `DRIVER_AGE_EXPLANATION` string: `"Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."`
- [`tripsApi.ts:1-305`](file:///c:/code/loomspan-travel-demo/frontend/src/api/tripsApi.ts#L1-L305) — Frontend API client with trip domain types and fetch wrappers. Currently lacks search/select/remove methods and search response types; `DraftResponse` currently omits `version: number`.
- [`ProfileScreen.tsx:215-275`](file:///c:/code/loomspan-travel-demo/frontend/src/components/ProfileScreen.tsx#L215-L275) — Profile view rendering `TripListSection` or `EmptyProfileState`; currently triggers only `TripCreateModal`.
- [`EmptyProfileState.tsx:5-18`](file:///c:/code/loomspan-travel-demo/frontend/src/components/EmptyProfileState.tsx#L5-L18) — Onboarding state with single "Plan Trip" button.
- [`TripListSection.tsx:102-159`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripListSection.tsx#L102-L159) — Trips list with single "Plan Trip" button in header.
- [`TripCreateModal.tsx:29-252`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripCreateModal.tsx#L29-L252) — Modal dialog collecting origin, destination, dates, and traveler count. Needs accommodation type collection for Stay flow and entry point awareness.
- [`TripWorkspace.tsx:44-753`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripWorkspace.tsx#L44-L753) — Workspace screen managing shared trip details, autosave, revision modal, and alternative cards. Needs progressive builder slots, summary/tally, search interfaces, and removal modal.
- [`AlternativeCard.tsx:12-124`](file:///c:/code/loomspan-travel-demo/frontend/src/components/AlternativeCard.tsx#L12-L124) — Card displaying draft or planned alternative summaries, duplication, and deletion actions.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **API Client (`tripsApi.ts`)** | Contains shared details, revisions, and alternative duplication/deletion endpoints. Missing search, select, and remove methods for airfare, stay, and rental car. Missing search response models and sort/filter enums. `DraftResponse` is missing `version: number`. |
| **Entry Points (`ProfileScreen`, `TripListSection`, `EmptyProfileState`)** | Exposes only "Plan Trip" button. Ticket requires three distinct entry points: **Plan Trip**, **Airfare**, and **Stay**. |
| **Trip Creation Dialog (`TripCreateModal`)** | Collects destination, dates, and traveler count. Needs to support entry point modes: for "Stay", collect mandatory accommodation type (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`). Needs to notify parent about the selected entry point flow so the workspace opens in the appropriate state. |
| **Progressive Workspace Shell (`TripWorkspace`)** | Manages trip metadata and lists alternatives, but does not display component slots for active draft. Must render Airfare and Stay slots (empty or selected), keep Rental Car slot hidden until "Add a car" is clicked, and support progressive disclosure. |
| **Persistent Itinerary Summary & Budget Tally** | No component price breakdown or budget comparison in workspace. Must show individual component prices, authoritative grand total, overall budget (if set), and remaining budget or overage warning. Must suppress budget-fit and overage display when budget is not set. |
| **Airfare Search & Selection UI** | No airfare search UI. Must provide direct flights filter, sort dropdown (lowest price, shortest duration, earliest departure, fewest stops), round-trip combination cards (carrier, flight numbers, stops, layovers, local times, timezone, duration, complete price), and select action. |
| **Stay Search & Selection UI** | No stay search UI. Must provide accommodation type selector, sort dropdown (lowest price, highest rating, nearest city center), stay cards (room count, rating, distance to center, complete price, nightly breakdown, budget fit badge), and select action. |
| **Rental Car Search & Selection UI** | No rental car search UI. Must collect pickup and return date/times within trip dates, enforce 25+ driver age rule with required explanation text, show Economy, Standard, SUV options with billing cycles and complete price, and select action. |
| **Component Removal & Confirmation Modal** | Removing an active selection must show an accessible confirmation dialog specifying the component name and discarded price. Empty slots discard without confirmation. |
| **State Management & Concurrency** | Status notifications (`Saving...`, `Saved`, errors) must announce to screen readers. 409 `VERSION_CONFLICT` must preserve search/form inputs and provide an actionable reload button. Focus management, Escape dismissal, and focus trapping required on all modals. |
| **Styles (`style.css`)** | Has base layout, cards, and modal styles. Needs responsive CSS for slot cards, search result grids, pricing breakdown tallies, and budget status indicators. |
| **Tests (`App.test.tsx`, `tripsApi.test.ts`)** | Test suite covers registration, profile, trip creation, shared details autosave, concurrency conflict on shared details, and deletion. Must be expanded with comprehensive Vitest tests covering the 3 entry flows, search/select/remove interactions, confirmation dialogs, and budget tally updates. |

## Existing Tests and Fixtures

### Backend Executable Evidence
- [`AirfareSearchAndSelectionIntegrationTest.java`](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/trip/AirfareSearchAndSelectionIntegrationTest.java):
  - Validates `GET /api/trips/{tripId}/drafts/{draftId}/airfare` with direct-only filter and all sort modes.
  - Validates seat availability cutoff and round-trip schedule validation.
  - Validates `PUT .../airfare` selection saving and draft version incrementation.
  - Validates `DELETE .../airfare` removal.
  - Validates optimistic locking (409 on version conflict).
- [`StaySearchAndSelectionIntegrationTest.java`](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java):
  - Validates mandatory accommodation type validation (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`).
  - Validates automatic room calculation (`ceil(travelers / capacity)` for hotels/B&Bs; 1 for vacation rental whole properties).
  - Validates budget-fit ranking and sort overrides.
  - Validates `PUT .../stays` and `DELETE .../stays`.
- [`RentalSearchAndSelectionIntegrationTest.java`](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/trip/RentalSearchAndSelectionIntegrationTest.java):
  - Validates pickup/return date interval validation against destination timezone.
  - Validates consecutive 24-hour billing cycle calculation.
  - Validates driver eligibility rule: disables selection and returns `explanation` when all travelers are under 25.
  - Validates `PUT .../rentals` and `DELETE .../rentals`.

### Frontend Tests
- Test framework: Vitest 4.1.11, `@testing-library/react` 16.3.0, `@testing-library/user-event` 14.6.1, `jsdom` 27.4.0.
- Executed via: `npm.cmd test` in `frontend/`.
- Setup file: `frontend/src/test/setup.ts` imports `@testing-library/jest-dom`.
- [`App.test.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/App.test.tsx): 27 tests covering identity, profile, trip creation, autosave, conflict handling, revision summary, and deletions.
- [`tripsApi.test.ts`](file:///c:/code/loomspan-travel-demo/frontend/src/api/tripsApi.test.ts): 5 tests verifying fetch requests, headers (`X-XSRF-TOKEN`), and exact payloads for existing trip endpoints.

## Dependencies and Operational Constraints

1. **Deterministic Pricing and Server Authority:**
   - All displayed prices for selections must use the authoritative values calculated and persisted by the backend.
   - For Airfare: party total = `(outbound total + return total) * travelerCount`.
   - For Stay: stay total = `perRoomTotal * unitCount`.
   - For Rental Car: rental total = `dailyTotalPrice * billingCycles`.
   - Persistent summary grand total = `airfareTotal + stayTotal + rentalTotal`.
2. **Date Boundaries:**
   - Supported travel dates are strictly March 1 through March 31, 2027 (1–14 nights).
   - Destination airport timezones:
     - San Francisco (`destination-sfo`): `America/Los_Angeles`
     - Munich (`destination-muc`): `Europe/Berlin`
     - Mexico City (`destination-mex`): `America/Mexico_City`
3. **Driver Age Rule:**
   - At least one traveler in `trip.travelerAges` must be 25 or older.
   - When ineligible: backend returns `driverEligible: false`, `selectionDisabled: true`, and `explanation: "Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."`.
   - Selection button must be disabled and explanation shown.
4. **Removal Safety:**
   - Removing an active/saved component selection requires an explicit confirmation modal naming the component and price being discarded.
   - Discarding an unopened or unselected search form requires NO confirmation modal.
5. **Progressive Disclosure:**
   - Rental Car slot is never displayed upfront; appears only after clicking "Add a car".
   - Airfare flow starts directly in flight search; Stay and Rental slots remain unopened.
   - Stay flow starts directly in stay search with upfront accommodation type; Airfare and Rental slots remain unopened.
   - Plan Trip flow starts with Airfare and Stay slots ready for configuration (unopened/empty), with Rental hidden.
6. **Concurrency (409 Conflict):**
   - Concurrency conflicts must preserve user inputs (such as search parameters or unsaved selections) and offer an actionable "Reload from server" button.
7. **Accessibility Requirements:**
   - Dialogs must have `role="dialog"`, `aria-modal="true"`, accessible label (`aria-labelledby`), focus trapping, Escape key closing, and focus return to trigger element.
   - Live regions (`role="status"`, `aria-live="polite"`) for saving state announcements.
8. **Explicit Out-of-Scope Items:**
   - Multi-alternative comparison columns (Phase 5).
   - Booking confirmation/checkout and cancellation workflows (Phase 6).
   - Custom trip nicknames and Version 2 Events.

## Historical Context

- `ai/thoughts/phases/phase-4-component-selection.md`: Details work packages 4.1 through 4.4, defining progressive shell behavior, airfare/stay/rental search requirements, and exit criteria.
- `ai/thoughts/phases/CONTINUATION.md`: Confirms clean-break development policy, PDX origin, March 2027 fixtures, deterministic ranking, integer-cent money types, and lack of model/AI dependencies.
- Commits `beb390d` (P04-T01), `a1e6ea7` (P04-T02), and `a548c3b` (P04-T03): Introduced the complete backend search services, repositories, and controller endpoints with extensive integration test coverage.

## Open Questions

None. All backend contracts, domain rules, validation criteria, error messages, and frontend architectural boundaries are fully specified in the ticket, architecture guides, and existing backend implementation. Research is complete and ready for planning.

---

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-09-21-p04-t04-deliver-progressive-trip-builder-experience.md
SUMMARY: Investigated backend search/mutation endpoints and frontend architecture for the progressive trip builder experience. Documented contracts, data models, state flows, test harness, accessibility obligations, and exact gaps across entry points, component slots, tallying, and confirmation modals. Verified clean working tree and 36 passing frontend tests.
DECISIONS:
  - none
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Proceed to Step 2 & 3 (Create Plan + Testing Plan) for P04-T04 on the Full 5-Step Pipeline.
