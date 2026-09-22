# Deliver Progressive Trip-Builder and Component Selection Experience Testing Plan

## Change Summary
Implements the progressive trip builder experience for DeTour: three authenticated entry points ("Plan Trip", "Airfare", "Stay" with upfront accommodation preference), progressive disclosure of component slots (Airfare, Stay, and strictly hidden Rental Car until "Add a car"), interactive search and selection interfaces for flights, stays, and rental cars, real-time persistent itinerary summary and budget tally, safe component removal with confirmation dialogs, concurrency conflict handling (409), and full keyboard/screen-reader accessibility across desktop and mobile viewports.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| **Entry Points Flow** | User enters via "Airfare" or "Stay" but lands in generic empty workspace or wrong search state | Component integration tests verifying "Airfare" opens directly in flight search, "Stay" collects upfront accommodation type and opens stay search, and "Plan Trip" displays ready slots. |
| **Progressive Disclosure** | Rental Car slot accidentally appears upfront before user requests it | Component test asserting Rental Car slot is absent until "Add a car" is clicked. |
| **Pricing & Budget Tally** | Tally calculates incorrect totals or displays out-of-sync remaining budget/overage amounts | Unit and integration tests validating exact party flight totals, room/night stay totals, consecutive 24-hr rental totals, and budget remaining/overage math. |
| **Budget Absent Suppression** | Absence of budget causes NaN, crash, or unwanted overage warnings | Test confirming null budget suppresses budget-fit ranking and remaining/overage presentation without blocking progress. |
| **Driver Age Enforcement** | Ineligible travelers can select rental car or explanation text is missing/divergent | Test confirming selection is disabled and exact explanation text *"Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."* is displayed when no traveler is 25+. |
| **Component Removal Safety** | Saved components deleted immediately on click without confirmation or wrong price reported | Test verifying clicking "Remove" on saved component opens modal naming component and price, cancelling preserves selection, and confirming executes DELETE mutation and updates tally. |
| **Unselected Form Closure** | Unopened or searching forms unnecessarily trigger removal modal | Test verifying clicking cancel/close on searching slot returns to empty state without showing modal. |
| **Concurrency (409)** | Version conflict crashes workspace or loses user search input | Test verifying 409 `VERSION_CONFLICT` displays reload alert, preserves form inputs, and provides reload action. |
| **Accessibility & Focus** | Dialogs lack `aria-modal`, leak focus outside modal, fail Escape dismissal, or lose focus on close | Test validating focus trapping, Escape key closing, and focus restoration to trigger element. |

## Existing Coverage and Environment Constraints
- Existing Vitest suite (`frontend/src/App.test.tsx`, `tripsApi.test.ts`, `identityApi.test.ts`, `PasswordField.test.tsx`): 36 passing tests in 4 files.
- Existing backend integration tests (`AirfareSearchAndSelectionIntegrationTest.java`, `StaySearchAndSelectionIntegrationTest.java`, `RentalSearchAndSelectionIntegrationTest.java`): full coverage of backend search, ranking, selection, and deletion contracts.
- Frontend test environment: Vitest 4.1.11, `@testing-library/react` 16.3.0, `@testing-library/user-event` 14.6.1, `jsdom` 27.4.0.
- All frontend tests use mocked fetch responses to avoid running live backend servers during unit testing.

## Failing Test First
- **Name:** `initiates trip creation through Airfare entry point and launches directly into flight search`
- **Type:** Component Integration Test (Vitest / React Testing Library)
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Arrange/Act/Assert:**
  - *Arrange:* Render `App` authenticated with a user profile and upcoming trips.
  - *Act:* Click the "Airfare" entry button on the profile screen, fill destination (`destination-sfo`) and March 2027 dates (`2027-03-10` to `2027-03-14`), submit the creation modal.
  - *Assert:* Workspace opens with the Airfare slot directly in search mode (searching flight options from `GET /api/trips/{id}/drafts/{draftId}/airfare`), the Stay slot displayed in empty ready state ("Add stay"), and the Rental Car slot not rendered.
- **Expected pre-fix failure:** Pre-fix, no "Airfare" button exists in `ProfileScreen` or `TripListSection`; `TripCreateModal` does not accept entry mode; `TripWorkspace` lacks progressive slot shell. The test fails immediately with:
  `TestingLibraryElementError: Unable to find an accessible element with the role "button" and name "Airfare"`.

## Tests to Add or Update

### 1. `initiates trip creation through Airfare entry point and launches directly into flight search`
- **Type:** Component Integration Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** "Airfare" entry point collects trip details, creates trip and initial draft, and opens workspace directly into flight search while Stay is empty and Rental Car is hidden.
- **Inputs/fixture:** Destination `destination-sfo`, dates `2027-03-10` to `2027-03-14`, traveler count 2. Mocked `POST /api/trips` and `GET /api/trips/{id}/drafts/{draftId}/airfare`.
- **Doubles or boundary isolation:** Global `fetch` mocked with synthetic JSON responses.
- **Edge cases:** Verifies Stay slot displays "Add stay" and Rental Car slot is not present in DOM.

### 2. `initiates trip creation through Stay entry point with upfront accommodation type preference and opens stay search`
- **Type:** Component Integration Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** "Stay" entry button opens modal requiring accommodation type selection (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`), creates trip and initial draft, and launches directly into stay search.
- **Inputs/fixture:** Destination `destination-muc`, dates `2027-03-05` to `2027-03-12`, traveler count 3, accommodation type `HOTEL`. Mocked `POST /api/trips` and `GET /api/trips/{id}/drafts/{draftId}/stays?type=HOTEL&sort=DEFAULT`.
- **Doubles or boundary isolation:** Global `fetch` mocked.
- **Edge cases:** Changing accommodation type to `VACATION_RENTAL` refreshes search with updated type.

### 3. `initiates trip creation through Plan Trip entry point with both Airfare and Stay slots ready and Rental Car hidden`
- **Type:** Component Integration Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** "Plan Trip" entry flow creates trip and initial draft, presenting both Airfare and Stay slots ready for configuration (unopened/empty), with Rental Car slot hidden.
- **Inputs/fixture:** Destination `destination-mex`, dates `2027-03-15` to `2027-03-20`, traveler count 1. Mocked `POST /api/trips`.
- **Doubles or boundary isolation:** Global `fetch` mocked.
- **Edge cases:** Neither search endpoint is queried until user clicks "Add airfare" or "Add stay".

### 4. `progressively reveals Rental Car slot only after explicit Add a car action`
- **Type:** Component Integration Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** Rental Car section is completely hidden upfront and revealed in search mode only after clicking "Add a car". Cancelling search hides the slot without prompting confirmation.
- **Inputs/fixture:** Active trip workspace with component-empty draft.
- **Doubles or boundary isolation:** Global `fetch` mocked.
- **Edge cases:** Unopened rental slot closes cleanly when clicking cancel/close.

### 5. `airfare search supports direct-only filter, sort overrides, and flight selection updates draft and persistent tally`
- **Type:** Component Integration Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** Direct flights checkbox updates query parameter (`directOnly=true`), sort dropdown updates `sort` parameter (`LOWEST_PRICE`, `SHORTEST_DURATION`, `EARLIEST_DEPARTURE`, `FEWEST_STOPS`), combinations render carrier, stops, layover, departure/arrival in local time with timezone info, total duration, and complete-party price, and selecting a flight calls `PUT .../airfare` and updates draft and persistent tally.
- **Inputs/fixture:** Mock flight combinations (direct and 1-stop options with layovers).
- **Doubles or boundary isolation:** Mocked API endpoints.
- **Edge cases:** Layover details display airport code, airport name, and duration; party pricing includes taxes and fees.

### 6. `stay search displays calculated room count, rating, distance to center, transparent nightly breakdown, and updates draft and persistent tally`
- **Type:** Component Integration Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** Renders accommodation type selector (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`), sort dropdown (`LOWEST_PRICE`, `HIGHEST_RATING`, `NEAREST_CITY_CENTER`), stay cards with room count, guest rating, distance to city center, complete stay total, transparent nightly/room breakdown, and selecting a stay calls `PUT .../stays` and updates draft and persistent tally.
- **Inputs/fixture:** Mock stay options with nightly breakdowns and budget fit indicators.
- **Doubles or boundary isolation:** Mocked API endpoints.
- **Edge cases:** Whole property vacation rentals indicate 1 property rather than multiple rooms.

### 7. `rental car search validates dates, enforces 25+ driver age rule with required explanation text, and updates draft and persistent tally`
- **Type:** Component Integration Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** Pickup and return date/time inputs are collected; when traveler ages contain no traveler 25+, selection button is disabled and exact explanation text *"Rental cars require at least one traveler aged 25 or older. Please add traveler ages in Trip Details to select a car."* is displayed; when traveler 25+ is present, selection is enabled; selecting car calls `PUT .../rentals` and updates draft and tally.
- **Inputs/fixture:** Traveler ages `[21, 22]` (ineligible) vs `[30, 22]` (eligible). Economy, Standard, SUV mock candidates.
- **Doubles or boundary isolation:** Mocked API endpoints.
- **Edge cases:** Billing cycles calculated based on 24-hour intervals rounded up.

### 8. `persistent summary calculates accurate component totals, grand total, and remaining budget or overage when budget is set`
- **Type:** Component Integration Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** Persistent tally displays itemized prices for selected airfare, stay, and rental car, authoritative grand total, overall budget (when set), and remaining budget or overage warning. Suppresses budget-fit ranking and remaining/overage presentation when budget is null without blocking progress.
- **Inputs/fixture:** Selections totaling $1,200.00 with budget $1,500.00 (remaining: $300.00), budget $1,000.00 (overage: $200.00), and budget null (grand total only).
- **Doubles or boundary isolation:** Mocked API responses.
- **Edge cases:** Updating budget in Trip Details immediately updates tally remaining/overage status.

### 9. `safe component removal requires explicit confirmation naming component and discarded price before deletion`
- **Type:** Component Integration Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** Clicking "Remove" on a saved flight, stay, or car slot opens confirmation modal stating the component name and price; clicking "Cancel" keeps selection; clicking confirm executes `DELETE` mutation and updates draft and persistent tally.
- **Inputs/fixture:** Saved airfare selection totaling $540.00.
- **Doubles or boundary isolation:** Mocked `DELETE /api/trips/{id}/drafts/{draftId}/airfare`.
- **Edge cases:** Closing an unselected search interface does NOT show confirmation modal.

### 10. `concurrency conflict (409 VERSION_CONFLICT) displays alert with reload action while preserving user search inputs`
- **Type:** Component Integration Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** When a selection mutation fails with HTTP 409 `VERSION_CONFLICT`, the workspace displays an alert explaining newer server data exists, provides a "Reload from server" button, and preserves active search inputs.
- **Inputs/fixture:** Mocked 409 error response with code `VERSION_CONFLICT`.
- **Doubles or boundary isolation:** Mocked `PUT` endpoint returning 409 followed by `GET /api/trips/{id}`.
- **Edge cases:** User input in search forms is not wiped on error.

### 11. `modal dialogs enforce accessibility: role=dialog, aria-modal=true, focus trapping, and Escape key dismissal`
- **Type:** Component Accessibility Test
- **Location:** `frontend/src/ProgressiveTripBuilder.test.tsx`
- **Proves:** `ConfirmRemoveModal` and `TripCreateModal` have `role="dialog"`, `aria-modal="true"`, trap keyboard focus on Tab/Shift+Tab, dismiss on Escape, and restore focus to the trigger button upon closing.
- **Inputs/fixture:** Rendered modal dialogs.
- **Doubles or boundary isolation:** Synthetic DOM event dispatchers.
- **Edge cases:** Focus returns to removal button when modal is dismissed.

### 12. `tripsApi client covers all search, selection, and removal endpoints with expected payloads and CSRF headers`
- **Type:** Unit Test
- **Location:** `frontend/src/api/tripsApi.test.ts`
- **Proves:** `searchAirfare`, `selectAirfare`, `removeAirfare`, `searchStays`, `selectStay`, `removeStay`, `searchRentals`, `selectRental`, `removeRental` invoke correct URLs, query strings, headers (`X-XSRF-TOKEN`), and payload bodies.
- **Inputs/fixture:** Synthetic requests matching backend specifications.
- **Doubles or boundary isolation:** Global `fetch` spy.
- **Edge cases:** Verification of optional query parameters (e.g. `directOnly`, `sort`, `pickupAt`, `returnAt`).

## Safe Verification Commands
- Focused: `cd frontend && npm.cmd test -- src/ProgressiveTripBuilder.test.tsx`
- Related suite: `cd frontend && npm.cmd test`
- Full safe suite: `mvn.cmd test`

## Optional Developer Checks
- Nonblocking manual verification: Run `npm.cmd run dev` in `frontend/` and launch `http://localhost:5173` against running backend to visually verify slot transitions, sticky tally layout, and mobile viewport responsiveness down to 320px.

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes (`npm.cmd test` and `mvn.cmd test`).
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
