# Deliver Progressive Trip-Builder and Component Selection Experience Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-21-p04-t04-deliver-progressive-trip-builder-experience.md`
- Research: `ai/thoughts/research/2026-09-21-p04-t04-deliver-progressive-trip-builder-experience.md`
- Outcome: Authenticated users can initiate trips through Plan Trip, Airfare, or Stay entry points, progressively configure only the components they choose, monitor a real-time persistent itinerary summary and budget tally without exposing unopened component forms, search and select flights, stays, and rental cars, and safely remove selections through explicit confirmation dialogs.

## Current State
- Backend APIs for Airfare (P04-T01), Stay (P04-T02), and Rental Car (P04-T03) search, selection (`PUT`), and removal (`DELETE`) are implemented and verified in [`TripController.java`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripController.java) and [`TripService.java`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripService.java).
- The frontend API client in [`tripsApi.ts`](file:///c:/code/loomspan-travel-demo/frontend/src/api/tripsApi.ts) currently defines `DraftResponse` without its `version: number` field and lacks methods and response types for component search, selection, and removal.
- The UI in [`ProfileScreen.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/ProfileScreen.tsx), [`TripListSection.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripListSection.tsx), and [`EmptyProfileState.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/EmptyProfileState.tsx) only provides a single "Plan Trip" button, lacking the required "Airfare" and "Stay" entry flows.
- [`TripCreateModal.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripCreateModal.tsx) collects destination, dates, and traveler count, but does not capture accommodation type preference for the Stay flow or propagate entry-point context to the workspace.
- [`TripWorkspace.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripWorkspace.tsx) manages shared details autosave, alternative card lists, and trip revision modals, but does not display component slots (Airfare, Stay, Rental Car), persistent itinerary summary and budget tally, search interfaces, or component removal confirmation dialogs.

## Desired End State
- **Three Authenticated Entry Points**: Users can initiate trip creation from "Plan Trip", "Airfare", or "Stay" buttons on the profile screen (both in empty profile and trip list states).
  - All three collect destination, start/end dates (March 1–31, 2027, 1–14 nights), and traveler count (1–8).
  - "Stay" entry flow collects mandatory accommodation type preference (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`) during the initial modal.
  - "Plan Trip" opens the workspace with Airfare and Stay slots ready for configuration (unopened/empty), with Rental Car slot hidden.
  - "Airfare" opens the workspace directly into flight search, keeping Stay and Rental Car unopened.
  - "Stay" opens the workspace directly into stay search using the selected accommodation type preference, keeping Airfare and Rental Car unopened.
- **Progressive Disclosure & Component Slots**:
  - Rental Car slot is never displayed upfront; it appears only after clicking an explicit "Add a car" action.
  - Empty slots display an explicit "Add [component]" action.
  - Selected slots display full item details (carrier, flight numbers, stops, layover times/airports, departure/arrival in local time with timezone info, total duration; property name, unit name, room count, nights; vehicle class, location, pickup/return times, billing cycles), complete taxes-and-fees-inclusive prices, a "Change" button, and a "Remove" button.
- **Persistent Itinerary Summary & Budget Tally**:
  - Displays individual component prices, authoritative grand total of selected items, overall trip budget (if set), and remaining budget or overage indicator.
  - An absent budget suppresses budget-fit ranking and remaining/overage presentation without blocking progress.
- **Component Search Interfaces**:
  - **Airfare Search**: Direct flights filter checkbox, sort override dropdown (`DEFAULT`, `LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, `FEWEST_STOPS`), combination cards with party pricing and leg details, and "Select flight" action.
  - **Stay Search**: Accommodation type selector (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`), sort override dropdown (`DEFAULT`, `LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`), stay cards with room count, guest rating, distance to center, total price, nightly/room breakdown, and "Select stay" action.
  - **Rental Car Search**: Local pickup/return date and time inputs within trip dates at destination airport, driver age rule check (disabling selection and displaying *"Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."* when ineligible), Economy/Standard/SUV options with 24-hour cycle pricing and taxes/fees totals, and "Select car" action.
- **Safe Component Removal**:
  - Removing an active selection opens an accessible confirmation modal naming the discarded component and price.
  - Closing an empty slot or unselected search form requires no confirmation.
- **State, Concurrency, and Accessibility**:
  - Status announcements (`Saving...`, `Saved`, and error messages) in live polite regions.
  - Optimistic concurrency conflict (409 `VERSION_CONFLICT`) preserves user search/form inputs and offers an actionable reload button.
  - Dialogs have `role="dialog"`, `aria-modal="true"`, focus trapping, Escape dismissal, and return focus to trigger.
  - Desktop and mobile layouts maintain full functionality without horizontal scrolling.

## Scope
### In scope
- API client methods and TypeScript types in `tripsApi.ts` for airfare, stay, and rental search, selection, and removal; updating `DraftResponse` with `version: number`.
- Three entry point buttons on `EmptyProfileState.tsx`, `TripListSection.tsx`, and `ProfileScreen.tsx`.
- Entry flow mode support and accommodation type preference selection in `TripCreateModal.tsx`.
- Progressive builder workspace shell in `TripWorkspace.tsx` managing active draft slots and disclosure states.
- Search and selection components for Airfare, Stay, and Rental Car.
- Persistent itinerary summary and budget tally component.
- Safe component removal confirmation modal dialog.
- Concurrency conflict handling with input preservation and reload button.
- Responsive styling in `style.css` matching design conventions.
- Vitest frontend tests covering entry flows, progressive disclosure, search/select/remove interactions, confirmation dialogs, and budget tallies.

### Out of scope
- Phase 5 multi-alternative comparison columns and promote-to-planned workflows.
- Phase 6 booking review, checkout, and cancellation workflows.
- Custom trip nicknames or renaming.
- Version 2 Events, map widgets, live seat selection, or vehicle extras.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
- **Authoritative Pricing Consistency**: Displayed item prices and persistent grand totals must match server-calculated amounts (including taxes and fees) exactly, preventing rounding or calculation drift between frontend and backend.
- **Progressive Disclosure Contract**: Rental car must remain strictly hidden until explicitly requested. Entry points must initialize the active draft slot states according to flow requirements (Airfare flow directly in airfare search, Stay flow directly in stay search with upfront preference, Plan Trip with ready/unopened slots).
- **Driver Age Eligibility**: Must evaluate traveler ages against the 25+ rule in real-time, disabling selection buttons and rendering the exact required explanation text when ineligible.
- **Removal Safety**: Saved components must never be discarded without explicit confirmation naming the component and discarded dollar amount, while unselected search forms must close cleanly without modal interruptions.
- **Concurrency Conflicts**: 409 `VERSION_CONFLICT` must alert the user that newer server changes exist and offer reload without wiping pending form selections or crashing the workspace.

## Implementation Approach
1. **API Client Layer (`tripsApi.ts`)**:
   - Add TypeScript contracts for search responses (`AirfareSearchResponse`, `StaySearchResponse`, `RentalSearchResponse`), selection requests (`AirfareSelectionRequest`, `StaySelectionRequest`, `RentalSelectionRequest`), and sort enums.
   - Update `DraftResponse` to include `version: number`.
   - Implement `searchAirfare`, `selectAirfare`, `removeAirfare`, `searchStays`, `selectStay`, `removeStay`, `searchRentals`, `selectRental`, and `removeRental`.
2. **Entry Flow Architecture (`ProfileScreen`, `TripListSection`, `EmptyProfileState`, `TripCreateModal`)**:
   - Add "Plan Trip", "Airfare", and "Stay" buttons.
   - `TripCreateModal` accepts `mode: 'PLAN_TRIP' | 'AIRFARE' | 'STAY'`. For `'STAY'`, render mandatory accommodation type dropdown (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`).
   - On success, pass created trip, entry mode, and optional accommodation type to parent to initialize `TripWorkspace`.
3. **Workspace State & Progressive Disclosure (`TripWorkspace`)**:
   - Identify active draft (`trip.drafts[0]` or active draft selection).
   - Maintain slot view states:
     - `airfareMode`: `'empty' | 'searching' | 'selected'`
     - `stayMode`: `'empty' | 'searching' | 'selected'`
     - `rentalMode`: `'hidden' | 'searching' | 'selected'`
   - Initialize states based on entry mode:
     - `AIRFARE`: `airfareMode = 'searching'`, `stayMode = 'empty'`, `rentalMode = 'hidden'`.
     - `STAY`: `stayMode = 'searching'` (with selected accommodation type), `airfareMode = 'empty'`, `rentalMode = 'hidden'`.
     - `PLAN_TRIP` / default: if selection exists -> `'selected'`, else -> `'empty'`. `rentalMode` is `'hidden'` unless rental is selected.
   - "Add a car" button reveals the rental slot in `'searching'` mode.
4. **Persistent Summary & Budget Tally (`ItinerarySummaryTally`)**:
   - Compute selected airfare, stay, and rental car totals.
   - Sum to authoritative grand total.
   - When `trip.budgetCents !== null`, compute remaining budget or overage and display warning/badge. When null, suppress remaining/overage without blocking.
5. **Component Search Components**:
   - `AirfareSearchSection`: fetches combinations from `tripsApi.searchAirfare`, supports direct-only filter and sort dropdown, displays round-trip cards with layover/timezone/price info, triggers `selectAirfare`.
   - `StaySearchSection`: fetches stay options from `tripsApi.searchStays`, supports accommodation type toggle and sort dropdown, displays cards with room count, ratings, distance, nightly breakdown, budget fit badge, triggers `selectStay`.
   - `RentalSearchSection`: date/time inputs for local pickup and return within trip dates, calculates billing cycles, evaluates driver age eligibility, renders Economy/Standard/SUV cards, triggers `selectRental`.
6. **Safe Removal Confirmation (`ConfirmRemoveModal`)**:
   - Displays modal naming target component and formatted price being discarded.
   - Confirms deletion via API `removeAirfare`, `removeStay`, or `removeRental`.

---

## Phase 1: API Client Extensions and Domain Models

### Changes
- [x] [`frontend/src/api/tripsApi.ts`](file:///c:/code/loomspan-travel-demo/frontend/src/api/tripsApi.ts):
  - Update `DraftResponse` interface to include `version: number`.
  - Export types: `AirfareSort`, `StaySort`, `RentalSort`, `AccommodationType`.
  - Export search models: `AirfareSearchResponse`, `FlightCombinationResponse`, `FlightLegResponse`, `PartyPricingResponse`, `LayoverResponse`.
  - Export stay models: `StaySearchResponse`, `StayOptionResponse`, `StayPricingResponse`, `StayNightPricingResponse`.
  - Export rental models: `RentalSearchResponse`, `RentalOptionResponse`, `RentalPricingResponse`.
  - Export selection request types: `SelectAirfareRequest`, `SelectStayRequest`, `SelectRentalRequest`.
  - Add API methods: `searchAirfare`, `selectAirfare`, `removeAirfare`, `searchStays`, `selectStay`, `removeStay`, `searchRentals`, `selectRental`, `removeRental`.
- [x] [`frontend/src/api/tripsApi.test.ts`](file:///c:/code/loomspan-travel-demo/frontend/src/api/tripsApi.test.ts):
  - Add unit tests verifying query parameters, JSON payloads, CSRF headers, and HTTP methods for all new search, selection, and removal endpoints.

### Automated verification
- [x] `cd frontend && npm.cmd test -- src/api/tripsApi.test.ts` — all client API tests pass.

### Optional developer checks
- [x] Verify TypeScript compiles cleanly without type errors via `npx tsc --noEmit`.

---

## Phase 2: Authenticated Entry Points and Trip Creation Flow

### Changes
- [x] [`frontend/src/components/EmptyProfileState.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/EmptyProfileState.tsx):
  - Replace single `onPlanTrip` with entry actions: `onStartPlanTrip`, `onStartAirfare`, and `onStartStay`.
  - Render buttons for "Plan Trip", "Airfare", and "Stay".
- [x] [`frontend/src/components/TripListSection.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripListSection.tsx):
  - In `.trips-header`, render entry buttons: "Plan Trip", "Airfare", and "Stay".
  - Accept callbacks `onStartPlanTrip`, `onStartAirfare`, `onStartStay` (or `onStartEntry(mode)`).
- [x] [`frontend/src/components/TripCreateModal.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripCreateModal.tsx):
  - Add prop `mode: 'PLAN_TRIP' | 'AIRFARE' | 'STAY'`.
  - When `mode === 'STAY'`, render mandatory accommodation type selector with options `HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL` (defaulting to `HOTEL`).
  - Update `onSuccess` signature to `(trip: TripResponse, mode: 'PLAN_TRIP' | 'AIRFARE' | 'STAY', accommodationType?: AccommodationType) => void`.
- [x] [`frontend/src/components/ProfileScreen.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/ProfileScreen.tsx):
  - Add state `createModalMode: 'PLAN_TRIP' | 'AIRFARE' | 'STAY' | null`.
  - Connect entry buttons to open modal with respective mode.
  - Pass created trip and entry context (`initialEntryMode`, `initialAccommodationType`) to `TripWorkspace`.

### Automated verification
- [x] `cd frontend && npm.cmd test` — existing and updated profile/creation tests pass.

### Optional developer checks
- [x] Verify clicking "Airfare" and "Stay" opens modal with proper heading and accommodation type select for Stay.

---

## Phase 3: Persistent Itinerary Summary and Budget Tally

### Changes
- [x] [`frontend/src/components/ItinerarySummaryTally.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/ItinerarySummaryTally.tsx) [NEW]:
  - Receive `trip: TripResponse` and active `selections: DraftSelectionResponse`.
  - Compute authoritative individual component prices:
    - Airfare: `(outbound total + return total) * travelerCount`.
    - Stay: `sum(night prices) * unitCount`.
    - Rental car: `billingCycles * dailyTotalPrice`.
  - Compute authoritative grand total.
  - Display budget tally:
    - If `trip.budgetCents !== null`: display overall budget, remaining budget (if grand total <= budget) or overage warning (if grand total > budget).
    - If `trip.budgetCents === null`: suppress budget-fit ranking and remaining/overage presentation without blocking progress.
  - Provide clear visual structure with semantic tags and accessible labels.
- [x] [`frontend/src/style.css`](file:///c:/code/loomspan-travel-demo/frontend/src/style.css):
  - Add styles for itinerary summary banner/card, tally grid, component cost items, budget comparison badge, and overage alert.

### Automated verification
- [x] `cd frontend && npm.cmd test` — suite passes.

### Optional developer checks
- [x] Check tally responsiveness on mobile viewport.

---

## Phase 4: Progressive Slot Shell and Component Search Interfaces

### Changes
- [x] [`frontend/src/components/AirfareSearchSection.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/AirfareSearchSection.tsx) [NEW]:
  - Filter controls: "Direct flights only" checkbox, sort override dropdown (`DEFAULT`, `LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, `FEWEST_STOPS`).
  - Fetches flight options via `tripsApi.searchAirfare`.
  - Displays round-trip combination cards: carrier, flight numbers, stops, layover times/airports, departure/arrival in local time with timezone info, total duration, and complete-party price.
  - "Select flight" button calls `tripsApi.selectAirfare` and updates draft.
  - "Cancel" button closes search without confirmation.
- [x] [`frontend/src/components/StaySearchSection.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/StaySearchSection.tsx) [NEW]:
  - Controls: accommodation type selector (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`), sort dropdown (`DEFAULT`, `LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`).
  - Fetches stay options via `tripsApi.searchStays`.
  - Displays stay cards: automatic room count, guest rating, distance to city center, complete-stay price, transparent nightly/room breakdown, and budget fit badge (when budget is present).
  - "Select stay" button calls `tripsApi.selectStay` and updates draft.
  - "Cancel" button closes search without confirmation.
- [x] [`frontend/src/components/RentalSearchSection.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/RentalSearchSection.tsx) [NEW]:
  - Controls: local pickup and return date/time inputs within trip dates at destination airport, sort dropdown (`DEFAULT`, `LOWEST_PRICE`).
  - Enforces driver age rule: if no traveler is 25+, disable selection button and display explanation: *"Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."*
  - Displays Economy, Standard, SUV options with consecutive 24-hour cycle pricing and complete taxes/fees totals.
  - "Select car" button calls `tripsApi.selectRental` and updates draft.
  - "Cancel" button closes search / hides slot without confirmation.
- [x] [`frontend/src/components/AirfareSlot.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/AirfareSlot.tsx) [NEW]:
  - Handles empty state ("Add airfare" action), searching state (`AirfareSearchSection`), and selected state (carrier, times, stops, total price, "Change flight", "Remove" trigger).
- [x] [`frontend/src/components/StaySlot.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/StaySlot.tsx) [NEW]:
  - Handles empty state ("Add stay" action), searching state (`StaySearchSection`), and selected state (property name, unit name, rooms, nights, total price, "Change stay", "Remove" trigger).
- [x] [`frontend/src/components/RentalSlot.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/RentalSlot.tsx) [NEW]:
  - Handles searching state (`RentalSearchSection`) and selected state (vehicle class, location, pickup/return times, billing cycles, total price, "Change car", "Remove" trigger).
- [x] [`frontend/src/components/TripWorkspace.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripWorkspace.tsx):
  - Integrate `initialEntryMode` and `initialAccommodationType`.
  - Manage active draft and slot states (`airfareMode`, `stayMode`, `rentalMode`).
  - Rental car slot remains hidden until user clicks "Add a car" action or a rental car is already selected.
  - Render persistent `ItinerarySummaryTally` above or alongside the component slots.
  - Provide live region status announcements (`Saving...`, `Saved`, error messages) for all draft mutations.

### Automated verification
- [x] `cd frontend && npm.cmd test` — suite passes.

### Optional developer checks
- [x] Verify progressive slot reveal and keyboard focus navigation across all slots.

---

## Phase 5: Safe Component Removal, Concurrency, and Accessibility

### Changes
- [x] [`frontend/src/components/ConfirmRemoveModal.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/ConfirmRemoveModal.tsx) [NEW]:
  - Accessible modal dialog (`role="dialog"`, `aria-modal="true"`, focus trap, Escape dismissal, return focus on close).
  - Explicitly states the component name and discarded price: *"Are you sure you want to remove this [Component]? This will discard the saved option totaling [Price] from your itinerary."*
  - Buttons: "Cancel" and "Remove [Component]".
- [x] [`frontend/src/components/TripWorkspace.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripWorkspace.tsx):
  - Connect removal buttons on selected slots to open `ConfirmRemoveModal`.
  - Unselected/searching forms close directly without confirmation modal.
  - Concurrency handling (409 `VERSION_CONFLICT`): display actionable alert with "Reload from server" button while preserving user search/form inputs.
- [x] [`frontend/src/style.css`](file:///c:/code/loomspan-travel-demo/frontend/src/style.css):
  - Add styles for component slots, search cards, flight leg segments, stay pricing breakdowns, rental vehicle cards, disabled driver warning alert, and confirmation dialogs.
  - Ensure responsive flex/grid layouts with no horizontal scrollbars on mobile viewports down to 320px.

### Automated verification
- [x] `cd frontend && npm.cmd test` — suite passes.

### Optional developer checks
- [x] Verify Escape key closes the removal modal and returns focus to the remove button.

---

## Phase 6: Comprehensive Verification and Vitest Test Coverage

### Changes
- [x] [`frontend/src/ProgressiveTripBuilder.test.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/ProgressiveTripBuilder.test.tsx) [NEW]:
  - Test 1: Authenticated entry points: "Plan Trip", "Airfare", and "Stay" trigger modal with correct modes.
  - Test 2: "Stay" flow collects accommodation type preference upfront; "Airfare" flow launches directly into flight search; "Plan Trip" opens builder with flight and stay slots ready.
  - Test 3: Rental car slot remains hidden upfront until "Add a car" is clicked.
  - Test 4: Airfare search filters by direct flights, sorts by lowest price/duration/departure/stops, and selecting a combination updates draft and persistent tally.
  - Test 5: Stay search displays calculated room count, rating, distance, supports sort overrides, and selecting a stay updates draft and persistent tally.
  - Test 6: Rental car search validates pickup/return dates, enforces 25+ driver age rule (disabling select and showing explanation when ineligible), and selecting a car updates draft and tally.
  - Test 7: Persistent summary displays accurate component totals, grand total, and remaining budget or overage when budget is defined; suppresses budget comparison when budget is null.
  - Test 8: Safe component removal requires confirmation modal naming component and price; confirming removes it and updates tally; unselected forms close without confirmation.
  - Test 9: Concurrency conflict (409 `VERSION_CONFLICT`) shows reload button and preserves user search input context.
  - Test 10: Modal accessibility: `aria-modal`, focus trap, Escape key closes modal and returns focus to trigger.
- [x] [`frontend/src/App.test.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/App.test.tsx):
  - Update any existing profile tests to account for the new entry point buttons.

### Automated verification
- [x] `cd frontend && npm.cmd test` — all Vitest tests pass cleanly.
- [x] `mvn.cmd test` — full backend test suite remains clean.

### Optional developer checks
- [x] Run production build `npm.cmd run build` in `frontend/` to verify zero TypeScript or Vite bundle warnings.

---

## Test Strategy
- **Unit & Component Tests**:
  - `tripsApi.test.ts`: endpoint routes, query serialization, mutation payloads, CSRF token propagation.
  - `ProgressiveTripBuilder.test.tsx`: complete component tests using `@testing-library/react` and `userEvent` mocking fetch responses for deterministic search and mutation flows.
- **Edge Cases & Boundary Coverage**:
  - Empty slots vs selected slots vs searching slots.
  - Missing budget (`budgetCents === null`) vs defined budget vs over-budget calculations.
  - Driver age eligibility: no ages, under 25 only, exactly 25, 25+ present.
  - Confirmation modal required only when discarding saved items.
  - Optimistic locking 409 conflict and recovery.
  - Full keyboard accessibility and focus restoration.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Authenticated users can start trip creation from "Plan Trip", "Airfare", or "Stay" entry actions. | `EmptyProfileState.tsx`, `TripListSection.tsx`, `ProfileScreen.tsx` | `ProgressiveTripBuilder.test.tsx` (entry points test) |
| "Stay" flow collects accommodation type preference upfront; "Airfare" flow launches directly into flight search; "Plan Trip" opens builder with flight and stay slots visible. | `TripCreateModal.tsx`, `TripWorkspace.tsx` (`initialEntryMode`, `initialAccommodationType`) | `ProgressiveTripBuilder.test.tsx` (flow initialization test) |
| Optional car component remains hidden until the user explicitly clicks "Add a car". | `TripWorkspace.tsx`, `RentalSlot.tsx` | `ProgressiveTripBuilder.test.tsx` (rental progressive disclosure test) |
| Persistent summary displays accurate component totals, total itinerary cost, and remaining budget or overage when a budget is defined. | `ItinerarySummaryTally.tsx`, `TripWorkspace.tsx` | `ProgressiveTripBuilder.test.tsx` (budget tally test) |
| Airfare search interface allows filtering by direct flights, sorting by price/duration/departure/stops, and selecting a combination updates the draft. | `AirfareSearchSection.tsx`, `AirfareSlot.tsx`, `tripsApi.ts` | `ProgressiveTripBuilder.test.tsx` (airfare search & select test) |
| Stay search interface displays calculated room count, complete stay price, rating, and city center distance, allows sorting, and selecting a stay updates the draft. | `StaySearchSection.tsx`, `StaySlot.tsx`, `tripsApi.ts` | `ProgressiveTripBuilder.test.tsx` (stay search & select test) |
| Rental car search interface validates pickup/return dates, shows the 25+ age requirement explanation when ineligible, and selecting a car updates the draft. | `RentalSearchSection.tsx`, `RentalSlot.tsx`, `tripsApi.ts` | `ProgressiveTripBuilder.test.tsx` (rental validation & age restriction test) |
| Removing an active selection opens a confirmation modal naming the discarded item; confirming removes it and updates the persistent tally. | `ConfirmRemoveModal.tsx`, `TripWorkspace.tsx` | `ProgressiveTripBuilder.test.tsx` (safe removal test) |
| Concurrency conflicts explain that newer server data exists and provide a reload button without losing context. | `TripWorkspace.tsx` (status `conflict`, reload action) | `ProgressiveTripBuilder.test.tsx` (409 conflict handling test) |
| All interactive dialogs trap focus, close on Escape, return focus to the trigger, and provide screen-reader announcements. | `ConfirmRemoveModal.tsx`, `TripCreateModal.tsx`, `TripWorkspace.tsx` | `ProgressiveTripBuilder.test.tsx` (dialog accessibility test) |
| Frontend tests (Vitest) cover the 3 entry flows, progressive disclosure, search/select/remove interactions, confirmation dialogs, and budget tally updates. | `ProgressiveTripBuilder.test.tsx`, `tripsApi.test.ts` | `npm.cmd test` execution evidence |

## Risks and Rollback/Recovery
- **Component State Desynchronization**: If draft mutations succeed but local workspace state fails to update, draft version conflicts could occur on subsequent actions. Mitigated by returning the full `TripResponse` from all backend mutation endpoints (`selectDraftAirfare`, `removeDraftAirfare`, etc.) and using `applyTripState` to update trip and draft state atomically.
- **Budget Tally Drift**: If frontend calculations differ from backend totals, the user sees inconsistent numbers. Mitigated by strictly replicating the exact backend price formulas (`PartyPricingResponse.partyTotalPriceCents`, `perRoomTotal * unitCount`, `cycles * dailyTotal`).
- **Rollback**: All changes are localized to `frontend/src/` components, API client, and tests. A git checkout of `frontend/src/` safely restores the baseline state.

## References
- Ticket: [`ai/thoughts/tickets/2026-09-21-p04-t04-deliver-progressive-trip-builder-experience.md`](file:///c:/code/loomspan-travel-demo/ai/thoughts/tickets/2026-09-21-p04-t04-deliver-progressive-trip-builder-experience.md)
- Research: [`ai/thoughts/research/2026-09-21-p04-t04-deliver-progressive-trip-builder-experience.md`](file:///c:/code/loomspan-travel-demo/ai/thoughts/research/2026-09-21-p04-t04-deliver-progressive-trip-builder-experience.md)
- Phase 4 Guide: [`ai/thoughts/phases/phase-4-component-selection.md`](file:///c:/code/loomspan-travel-demo/ai/thoughts/phases/phase-4-component-selection.md)
- Spring Controller: [`TripController.java`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripController.java)
- Backend Service: [`TripService.java`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripService.java)
- Frontend Workspace: [`TripWorkspace.tsx`](file:///c:/code/loomspan-travel-demo/frontend/src/components/TripWorkspace.tsx)
