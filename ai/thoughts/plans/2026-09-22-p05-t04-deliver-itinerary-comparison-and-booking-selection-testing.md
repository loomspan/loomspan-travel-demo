# Multi-Alternative Planned Itinerary Comparison and Booking Selection Testing Plan

## Change Summary
Ticket P05-T04 delivers multi-alternative Planned itinerary comparison and candidate booking selection in the DeTour frontend. The changes introduce in-memory selection of 2 or 3 Planned alternatives in `TripWorkspace`, constraint enforcement with accessible alerts, a responsive comparison matrix (`ItineraryComparisonView`) supporting desktop side-by-side columns and a mobile persistent switcher tab bar, visual and semantic differentiation between missing optional components and zero-cost items, and a transitional simulated booking review screen (`BookingReviewView`) with full snapshot details, the required fictional inventory disclosure, and a disabled Phase 6 action button.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| **Selection Constraints** | Users select < 2 or > 3 alternatives, or stale deleted alternatives remain selected | Automated test checking button disabled at 0/1 selection, enabled at 2/3, 4th selection blocked with accessible notification, and deleted alternative IDs pruned |
| **Attribute Matrix Accuracy** | Discrepancies between snapshot facts (flights, stops, timezones, stay distance, room count, car cycles) and displayed columns | Automated assertions verifying exact attributes from `AirfareComponentResponse`, `StayComponentResponse`, `RentalComponentResponse`, and `ItineraryTallyResponse` |
| **Missing vs Zero-Cost Components** | Users confuse an unselected optional component with a free ($0) service | Automated test checking that null component renders "— No [component] selected" with `.missing-component` styling, while $0 items render formatted currency |
| **Mobile Switcher & Accessibility** | Mobile switcher tab bar lacks keyboard navigation, focus management, or screen-reader announcements | Automated test asserting `role="tablist"`, `role="tab"`, ArrowLeft/ArrowRight navigation, Home/End jumping, and `aria-live="polite"` announcements |
| **Booking Review Transition** | Missing descriptive snapshots, absent fictional disclosure, or interactive booking button before Phase 6 | Automated test verifying snapshot rendering, exact disclosure text, and non-interactive `disabled` attribute on "Confirm Booking" |
| **Visual Contrast (WCAG AA)** | Budget position badges (`Within Budget` / `Over Budget`) fail color contrast standards | Automated test asserting presence of WCAG AA compliant badge classes (`badge-success`, `badge-warning`) with documented contrast ratios (> 7:1) |

## Existing Coverage and Environment Constraints
- **Existing Frontend Suites:**
  - `frontend/src/DraftPromotion.test.tsx` (9 tests passing): Covers draft promotion, readiness issues, overage modals, and initial Planned card rendering.
  - `frontend/src/ProgressiveTripBuilder.test.tsx` (14 tests passing): Covers builder slot mutations, tallies, and removals.
  - `frontend/src/App.test.tsx` (27 tests passing): Covers auth, profile, and trip navigation.
- **Backend Suites:**
  - `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java` (140 backend tests passing): Verifies database persistence of V16 snapshot fields for airfare, stays, and rental cars.
- **Environment:**
  - Standard Node.js / Vitest runner with `@testing-library/react` and `@testing-library/user-event`.
  - All tests execute in-memory with mocked fetch and simulated DOM; no external networks, real payments, or live services are used.

## Failing Test First
- **Name:** `enforces 2-to-3 planned alternative selection constraint and enables comparison launch`
- **Type:** React Testing Library component integration test (Vitest)
- **Location:** `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- **Arrange/Act/Assert:**
  - *Arrange:* Render `TripWorkspace` with a mock trip containing 3 Planned alternatives (`lifecycle: 'PLANNED'`).
  - *Act:* Look for the "Compare selected itineraries" button and comparison selection checkboxes on the Planned cards.
  - *Assert:* Verify that initially the compare button is disabled. Check the first Planned alternative; button remains disabled. Check the second Planned alternative; button becomes enabled. Check the third Planned alternative; button remains enabled.
- **Expected pre-fix failure:** `TestingLibraryElementError: Unable to find an accessible element with the role "button" and name /compare selected itineraries/i` because the comparison controls have not yet been added to `TripWorkspace` or `AlternativeCard`.

## Tests to Add or Update

### 1. `enforces 2-to-3 planned alternative selection constraint and prevents selecting more than 3 with accessible alert`
- **Type:** Component integration test
- **Location:** `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- **Proves:** Acceptance Criterion 1
- **Inputs/fixture:** Trip fixture with 4 Planned alternatives.
- **Doubles or boundary isolation:** Global `fetch` mocked; cookie mocked.
- **Edge cases:**
  - Attempting to check a 4th Planned alternative when 3 are already selected is prevented (checkbox stays unchecked).
  - An accessible alert message (`role="alert"` or `aria-live="polite"`) appears: *"You can compare at most 3 itineraries at once. Deselect one before adding another."*
  - Deselecting an alternative clears the alert and brings count back to 2.
  - Fewer than 2 Planned alternatives displays guidance to promote drafts.

### 2. `renders desktop side-by-side comparison matrix with complete attribute columns for 2 or 3 alternatives`
- **Type:** Component integration test
- **Location:** `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- **Proves:** Acceptance Criteria 2 & 3
- **Inputs/fixture:** Trip with 2 Planned alternatives containing distinct airfare (carriers, flight numbers, stops, layovers, timezones, durations), stays (property name, category, unit, room count, distance to center, nightly rates), and rentals (vehicle class, pickup/return times, 24-hr cycles, total prices).
- **Doubles or boundary isolation:** In-memory rendering of `ItineraryComparisonView`.
- **Edge cases:**
  - Verifies presence of semantic grid/table headers (`scope="col"`, `scope="row"` or `role="columnheader"`, `role="rowheader"`).
  - Grand total and budget position badges (`Within Budget` and `Over Budget` with dollar remaining/overage).
  - Complete airfare attributes: outbound/return flight numbers, carriers, stops, layover airport and minutes, local times with timezones, total duration, passenger fare breakdown.
  - Complete stay attributes: property category (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`), distance to city center in km, room count, nightly breakdown.
  - Complete rental attributes: vehicle class, 24-hr cycle calculation, daily rate breakdown.

### 3. `renders mobile comparison layout with persistent keyboard-accessible alternative switcher tab bar and announcements`
- **Type:** Component integration test
- **Location:** `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- **Proves:** Acceptance Criteria 2 & 6
- **Inputs/fixture:** Trip with 3 Planned alternatives.
- **Doubles or boundary isolation:** In-memory rendering of `ItineraryComparisonView`.
- **Edge cases:**
  - Tablist element exists with `role="tablist"` and tabs with `role="tab"`.
  - ArrowRight key moves focus and active selection to the next tab.
  - ArrowLeft key moves focus and active selection to the previous tab (with circular wrap or boundary clamp).
  - Home key jumps to first tab; End key jumps to last tab.
  - Changing tabs announces new selection via `aria-live="polite"` region: *"Showing itinerary 2 of 3: Planned ..."*.
  - Active tab controls its corresponding content panel (`aria-controls`, `aria-selected="true"`).

### 4. `visually and semantically distinguishes missing optional components from zero-cost selections`
- **Type:** Component integration test
- **Location:** `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- **Proves:** Acceptance Criterion 4
- **Inputs/fixture:** Alternative A with no rental car (`selections.rental = null`); Alternative B with a hypothetical $0 item or rental present.
- **Doubles or boundary isolation:** In-memory rendering of `ItineraryComparisonView` and `BookingReviewView`.
- **Edge cases:**
  - Missing component renders text containing em-dash `" — No rental car selected "` with class `missing-component`.
  - Missing component does not display "$0.00" or confusing pricing values.
  - Zero-cost component displays "$0.00" and regular component metadata.

### 5. `transitions from comparison matrix to booking review screen with complete snapshots, itemized totals, disclosure, and disabled Phase 6 action`
- **Type:** Component integration test
- **Location:** `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- **Proves:** Acceptance Criterion 5
- **Inputs/fixture:** `TripWorkspace` with 2 Planned alternatives in comparison view.
- **Doubles or boundary isolation:** Full workspace rendering with simulated user clicks.
- **Edge cases:**
  - Clicking "Select for Booking Review" on Alternative 1 transitions to `BookingReviewView`.
  - Displays trip destination, dates, and traveler count.
  - Displays full descriptive snapshot cards for airfare, stay, and rental.
  - Displays itemized component totals and authoritative grand total matching tally.
  - Displays exact mandatory disclosure text: *"This is a simulated booking with fictional inventory. No real payment, billing address, or external reservation is required."*
  - Displays "Confirm Booking" button with `disabled` attribute and label indicating Phase 6 readiness.
  - Clicking "← Back to comparison" returns back to the comparison matrix view.

### 6. `transitions from standalone planned alternative card to booking review screen and returns to workspace`
- **Type:** Component integration test
- **Location:** `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- **Proves:** Acceptance Criterion 5
- **Inputs/fixture:** `TripWorkspace` in standard workspace view with 1 or more Planned cards.
- **Doubles or boundary isolation:** Workspace rendering.
- **Edge cases:**
  - Clicking "Select for Booking Review" on an individual `AlternativeCard` transitions directly to `BookingReviewView`.
  - Back button is labeled "← Back to Trip Workspace" and clicking it returns to the workspace view.

### 7. `maintains WCAG AA compliant contrast classes and accessible ARIA table/grid semantics in comparison matrix`
- **Type:** Component integration test
- **Location:** `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- **Proves:** Acceptance Criterion 6
- **Inputs/fixture:** Planned alternatives with over-budget and within-budget tallies.
- **Doubles or boundary isolation:** Rendered elements inspected for classes and ARIA attributes.
- **Edge cases:**
  - Within-budget badge has class `badge-success`.
  - Over-budget badge has class `badge-warning` and `role="alert"`.
  - Comparison table has `role="grid"` or semantic table elements with column and row headers.

## Safe Verification Commands
- **Focused:**
  `npm.cmd --prefix frontend test -- -t "ItineraryComparisonAndBookingReview" --run`
- **Related suite:**
  `npm.cmd --prefix frontend test -- --run`
- **Full safe suite:**
  `.\mvnw.cmd test`

## Optional Developer Checks
- None. (All acceptance criteria are fully automated via Vitest component integration tests).

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes (`npm.cmd --prefix frontend test -- --run` and `.\mvnw.cmd test`).
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
