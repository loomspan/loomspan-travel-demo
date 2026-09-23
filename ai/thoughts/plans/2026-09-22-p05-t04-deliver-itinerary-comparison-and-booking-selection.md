# Multi-Alternative Planned Itinerary Comparison and Booking Selection Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-22-p05-t04-deliver-itinerary-comparison-and-booking-selection.md`
- Research: `ai/thoughts/research/2026-09-22-p05-t04-deliver-itinerary-comparison-and-booking-selection.md`
- Outcome: Deliver an accessible, responsive itinerary comparison matrix and candidate booking review experience for Planned alternatives in DeTour. Users can select 2 or 3 Planned alternatives, compare them side-by-side on desktop or with a persistent switcher on mobile, clearly distinguish missing optional components from zero-cost items, and select an alternative to review simulated booking snapshots with the mandatory fictional inventory disclosure before Phase 6.

## Current State
- The backend stores and serves immutable, complete snapshot facts for Planned itineraries via `V16__enhance_planned_snapshot_schema.sql`, `JdbcTripRepository.java`, and `TripService.java`. The `TripResponse` payload contains `alternatives` and `planned` lists with authoritative `ItineraryTallyResponse` objects including grand totals, component totals, and budget remaining/overage.
- In `frontend/src/components/TripWorkspace.tsx`, the Alternatives section renders `AlternativeCard` components for drafts and planned alternatives. When planned alternatives exist, cards are read-only and offer "Delete planned itinerary" and "Duplicate to draft".
- There is currently no multi-alternative comparison trigger, no selection tray or checkboxes, no 2-to-3 selection constraint enforcement, no comparison matrix view, no mobile alternative switcher, and no simulated booking review screen.
- In `frontend/src/components/ItinerarySummaryTally.tsx`, missing components currently display a bare `'—'`. There is no visual distinction between an unselected optional component and an item that costs $0.00.
- `frontend/src/style.css` does not define `.badge-warning` and `.badge-success` with WCAG AA compliant contrast ratios.

## Desired End State
- In the Trip Workspace Alternatives section, when a trip has at least 2 Planned alternatives, users see a comparison selection interface. Checkboxes on Planned alternative cards allow selecting 2 or 3 alternatives.
- The 2-to-3 alternative constraint is cleanly enforced: the "Compare selected itineraries" button is disabled when fewer than 2 alternatives are selected; attempting to select a 4th alternative is prevented and announces an accessible explanatory alert.
- Launching comparison opens `ItineraryComparisonView`:
  - **Desktop layout:** Displays a side-by-side comparison matrix with semantic table/grid headers across grand total, budget position, airfare (carrier, flight numbers, stops, layovers, departure/arrival local times with timezone labels, duration, complete-party fare breakdown), stay (property name, category, unit, rooms, distance to city center, complete stay total, nightly rates), and rental car (vehicle class, pickup/return dates/times, 24-hr billing cycles, total price).
  - **Mobile layout:** Displays a stacked view with a persistent itinerary switcher / tab bar (`role="tablist"`), keyboard arrow navigation, active tab indicator, and screen reader announcements (`aria-live="polite"`).
- Missing optional components are visually and semantically distinguished from zero-cost selections (e.g. "— No rental car selected" with muted background and italic styling vs "$0.00").
- Both the comparison view and standalone Planned cards provide a primary "Select for Booking Review" action.
- Selecting an alternative transitions to `BookingReviewView`:
  - Displays destination, trip dates, traveler count.
  - Displays full descriptive snapshot details for all included components (and missing badges for absent optional components).
  - Displays itemized component totals, budget position, and final grand total.
  - Displays prominent fictional inventory disclosure: *"This is a simulated booking with fictional inventory. No real payment, billing address, or external reservation is required."*
  - Displays disabled "Confirm Booking" action labeled as ready for Phase 6 simulated booking.
  - Provides a back navigation path to return to the comparison view or trip workspace.

## Scope

### In scope
- Client-side in-memory selection of 2 or 3 Planned alternatives for comparison within a Trip.
- Comparison launch trigger, selection controls, and 2-to-3 constraint enforcement with accessible alerts.
- Responsive side-by-side comparison matrix on desktop.
- Responsive mobile layout with persistent keyboard-accessible itinerary switcher tab bar and live screen-reader announcements.
- Full attribute comparison: financial tallies, budget position badges, airfare flight/stops/layover/times/timezones/fares, stay property/category/unit/rooms/distance/rates, and rental car class/times/cycles/rates.
- Semantic and visual differentiation of missing optional components from zero-cost items.
- Standalone and comparison card "Select for Booking Review" action.
- Booking Review screen with trip parameters, complete component snapshots, itemized totals, prominent simulated booking disclosure, and disabled Phase 6 "Confirm Booking" button.
- Clean two-way navigation between workspace, comparison view, and booking review screen.
- WCAG AA compliant contrast ratios for badges and text.
- Full Vitest test suite covering all acceptance criteria.

### Out of scope
- Server-side persisted comparison sets or shared comparison URLs (explicitly [FUTURE] in roadmap).
- Transactional booking execution, payment collection, or reservation records (Phase 6 scope).
- Booking cancellation workflow (Phase 6 scope).
- Version 2 trip events.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
- **Data Integrity and Snapshot Reliability:** Planned alternatives are immutable snapshots stored in `detour_planned_*` tables and exposed via `TripResponse.alternatives` / `TripResponse.planned`. All descriptive attributes and tallies are server-authoritative and require no backend mutations.
- **Client-Side State Management:** Comparison selection is kept in-memory within `TripWorkspace`. When a user deletes a planned alternative or reloads the trip, the selection state automatically prunes any deleted IDs to prevent stale references.
- **Accessibility and Screen Readers:** The comparison matrix must provide semantic headers (`scope="col"`, `scope="row"` or ARIA `grid`, `rowheader`, `columnheader`). The mobile tab switcher must handle Left/Right arrow keys, Home/End keys, Enter/Space activation, `aria-selected`, `aria-controls`, and `aria-live="polite"` status updates.
- **Visual Contrast Standards:** Budget badges (`Within Budget` and `Over Budget`) must satisfy WCAG AA contrast (>= 4.5:1). Colors `#14532d` on `#dcfce7` (within budget) and `#7f1d1d` on `#fee2e2` (over budget) exceed 7:1.

## Implementation Approach
1. **Component Architecture:**
   - Keep `TripWorkspace` as the stateful orchestrator managing the active view (`workspaceView: 'workspace' | 'compare' | 'booking-review'`).
   - Create `frontend/src/components/ItineraryComparisonView.tsx` as a focused, accessible comparison component rendering desktop side-by-side columns and mobile stacked switcher views.
   - Create `frontend/src/components/BookingReviewView.tsx` as the transitional simulated booking screen displaying snapshot details, totals, disclosure, and disabled Phase 6 action.
   - Update `frontend/src/components/AlternativeCard.tsx` to support comparison selection checkboxes and the "Select for Booking Review" action.
   - Export shared formatting helpers (`formatMinutes`, `formatTime`) from `frontend/src/components/AirfareSearchSection.tsx` for consistent time/duration presentation.
   - Define WCAG AA compliant badge styles and comparison layouts in `frontend/src/style.css`.
2. **Why this approach fits:**
   - Avoids route changes or url rewrites, preserving the existing single-page modal/workspace architecture used throughout `App.tsx` and `ProfileScreen.tsx`.
   - Adheres strictly to the Phase 5 roadmap constraint of in-memory client-side comparison without requiring backend or schema changes.
   - Reuses existing server-calculated `ItineraryTallyResponse` and snapshot data models from P05-T01 and P05-T02.

---

## Phase 1: Comparison Selection and Constraint Enforcement in Alternatives Section

### Changes
- [x] `frontend/src/components/AlternativeCard.tsx`:
  - Add optional props:
    - `isSelectedForCompare?: boolean;`
    - `onToggleCompare?: (alternativeId: string, checked: boolean) => void;`
    - `onSelectForBookingReview?: (alternativeId: string) => void;`
  - In `AlternativeCard`, when `isPlanned`, render:
    - A comparison checkbox `<input type="checkbox" id={`compare-select-${alternative.id}`} checked={isSelectedForCompare} onChange={(e) => onToggleCompare?.(alternative.id, e.target.checked)} />` with label "Select for comparison".
    - A primary action button "Select for Booking Review" with `onClick={() => onSelectForBookingReview?.(alternative.id)}`.
- [x] `frontend/src/components/TripWorkspace.tsx`:
  - Add state variables:
    - `selectedForCompareIds: string[]` (tracks selected Planned alternative IDs).
    - `compareNotification: string | undefined` (tracks accessible warning when attempting > 3 selections).
    - `workspaceView: 'workspace' | 'compare' | 'booking-review'`.
    - `reviewAlternativeId: string | null`.
    - `reviewReturnView: 'workspace' | 'compare'`.
  - In the Alternatives section header:
    - Compute `plannedAlternatives = (trip.alternatives || []).filter(a => a.lifecycle.toUpperCase() === 'PLANNED')`.
    - When `plannedAlternatives.length >= 2`, render comparison launch controls:
      - Primary button: "Compare selected itineraries ({selectedForCompareIds.length})".
      - Disabled when `selectedForCompareIds.length < 2`.
      - Click handler: transitions `workspaceView` to `'compare'`.
      - Selection helper action: "Clear comparison selection" when `selectedForCompareIds.length > 0`.
    - When `plannedAlternatives.length < 2`, display hint: "Promote at least 2 draft alternatives to Planned to compare them."
  - In `handleToggleCompare(id: string, checked: boolean)`:
    - If `checked` and `selectedForCompareIds.length >= 3`, do not add; set `compareNotification` to `"You can compare at most 3 itineraries at once. Deselect one before adding another."`.
    - If `checked` and count < 3, add ID to `selectedForCompareIds` and clear `compareNotification`.
    - If unchecked, remove ID and clear `compareNotification`.
  - Render an `aria-live="polite"` region for `compareNotification` with `role="alert"`.
  - Ensure deletion of a planned alternative removes it from `selectedForCompareIds`.

### Automated verification
- [x] `npm.cmd --prefix frontend test -- --run` — existing tests pass, and new tests verify selection of 2 or 3 alternatives, disabling comparison for < 2, and blocking > 3 with explanatory message.

### Optional developer checks
- [x] None.

---

## Phase 2: Attribute Comparison Matrix and Responsive Layouts (Desktop & Mobile)

### Changes
- [x] `frontend/src/components/AirfareSearchSection.tsx`:
  - Export `formatMinutes` and `formatTime` so they can be reused across comparison and review views without code duplication.
- [x] `frontend/src/components/ItineraryComparisonView.tsx`:
  - Create new component `ItineraryComparisonView` accepting props:
    - `trip: TripResponse;`
    - `alternatives: AlternativeResponse[];`
    - `onBack: () => void;`
    - `onSelectForBookingReview: (alternativeId: string) => void;`
  - Implement Desktop Matrix:
    - Render a comparison table with `role="grid"` or semantic `<table>`.
    - Column headers (`<th scope="col">`): Alternative label ("Planned Alternative #1", ID snippet), grand total, budget badge, and "Select for Booking Review" action button.
    - Row categories (`<th scope="row">`):
      1. **Financial Summary:** Grand total, budget position (remaining cents or overage cents with distinct color badges), and complete breakdown (airfare total, stay total, rental total).
      2. **Airfare:** Outbound flight number, carrier name, stops count, layover airport and duration, departure and arrival local times with timezone labels, total duration, and complete-party fare breakdown. Return flight matching attributes.
      3. **Stay:** Property name, property category (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`), unit name, room count, distance to city center in km, complete stay total, and itemized nightly rates.
      4. **Rental car:** Vehicle class, pickup and return airport local date/times, consecutive 24-hour billing cycle count, and total rental price including taxes/fees.
  - Implement Mobile Switcher Layout:
    - Render a persistent alternative switcher tab bar (`role="tablist"` with `aria-label="Compared itineraries"`).
    - Render tabs for each compared alternative with `role="tab"`, `aria-selected`, `aria-controls`.
    - Support keyboard navigation: Left/Right arrow keys change active tab, Home/End jump to first/last tab, Space/Enter activate.
    - Provide `aria-live="polite"` announcement when active alternative changes: `"Showing itinerary {i+1} of {total}: Planned {id.slice(0, 8)}"`.
    - Render the active alternative's complete attribute card stacked beneath the tab bar, retaining scroll context and quick toggling.
  - Provide a top navigation bar with "← Back to Trip Workspace" button and trip header summary.
- [x] `frontend/src/components/TripWorkspace.tsx`:
  - When `workspaceView === 'compare'`, render `<ItineraryComparisonView />` with selected alternatives.

### Automated verification
- [x] `npm.cmd --prefix frontend test -- --run` — verifies comparison rendering of all required attributes, table semantics, and mobile tab keyboard navigation.

### Optional developer checks
- [x] None.

---

## Phase 3: Visual Distinction for Missing Optional Components vs Zero-Cost Selections

### Changes
- [x] `frontend/src/components/ItineraryComparisonView.tsx` & `frontend/src/components/BookingReviewView.tsx`:
  - When `selections.airfare === null`:
    - Render `<div className="missing-component" aria-label="No airfare selected"><span className="missing-dash">—</span> No flights selected</div>`.
    - Explicitly distinguish from an included zero-cost flight ($0.00).
  - When `selections.stay === null`:
    - Render `<div className="missing-component" aria-label="No stay selected"><span className="missing-dash">—</span> No accommodation selected</div>`.
  - When `selections.rental === null`:
    - Render `<div className="missing-component" aria-label="No rental car selected"><span className="missing-dash">—</span> No rental car selected</div>`.
- [x] `frontend/src/style.css`:
  - Style `.missing-component` with muted text (`#64748b`), italic styling, subtle neutral background (`#f8fafc`), and dashed or light border (`1px dashed #cbd5e1`) to clearly separate unselected optional components from zero-cost components.

### Automated verification
- [x] `npm.cmd --prefix frontend test -- --run` — unit tests assert that null components render the distinct missing label with muted styling, while $0 items render "$0.00".

### Optional developer checks
- [x] None.

---

## Phase 4: Simulated Booking Review Screen with Fictional Inventory Disclosure and Phase 6 Staging

### Changes
- [x] `frontend/src/components/BookingReviewView.tsx`:
  - Create `BookingReviewView` accepting props:
    - `trip: TripResponse;`
    - `alternative: AlternativeResponse;`
    - `returnTarget: 'workspace' | 'compare';`
    - `onBack: () => void;`
  - Display trip parameters: Destination name and airport, start date, end date, traveler count, and traveler ages.
  - Display snapshot-locked components:
    - Airfare card: outbound & return flight numbers, carriers, stops, layovers, departure/arrival local times, timezones, durations, and complete fare breakdown.
    - Stay card: property name, category badge, unit name, room count, distance to city center, location description, guest capacity, and nightly rates table.
    - Rental car card: vehicle class, location, pickup & return date/times, 24-hr cycle count, daily base/tax/fee breakdown, and total price.
    - Missing components: display the distinct missing component notification if any optional component was not selected.
  - Display authoritative totals:
    - Itemized airfare, stay, and rental totals.
    - Final booking grand total (`alternative.tally?.grandTotalCents`).
    - Budget comparison (budget amount, remaining budget or overage with high-contrast badge).
  - Display prominent disclosure callout:
    - Container with class `booking-disclosure-callout` and `role="note"`.
    - Text: *"This is a simulated booking with fictional inventory. No real payment, billing address, or external reservation is required."*
  - Display Phase 6 staging button:
    - Button labeled "Confirm Booking (Simulated)" or "Confirm Booking".
    - Attribute `disabled={true}`.
    - Explanatory caption: *"Ready for Phase 6 simulated booking implementation."*
  - Top navigation:
    - Button labeled `returnTarget === 'compare' ? '← Back to comparison' : '← Back to Trip Workspace'` invoking `onBack()`.
- [x] `frontend/src/components/TripWorkspace.tsx`:
  - When `workspaceView === 'booking-review'`, find the alternative by `reviewAlternativeId` and render `<BookingReviewView />`.
  - Wire `onSelectForBookingReview` from both `AlternativeCard` and `ItineraryComparisonView` to set `reviewAlternativeId`, set `reviewReturnView`, and transition `workspaceView` to `'booking-review'`.

### Automated verification
- [x] `npm.cmd --prefix frontend test -- --run` — tests verify that clicking "Select for Booking Review" from either comparison view or standalone cards opens Booking Review with complete snapshot details, disclosure text, and disabled Phase 6 action.

### Optional developer checks
- [x] None.

---

## Phase 5: Accessibility, Keyboard Navigation, ARIA Semantics, and High-Contrast WCAG AA Badges

### Changes
- [x] `frontend/src/style.css`:
  - Add explicit high-contrast badge definitions:
    - `.badge-success` (within budget): background `#dcfce7`, text `#14532d`, border `1px solid #86efac` (contrast > 7:1, exceeds WCAG AAA).
    - `.badge-warning` / `.badge-overage` (over budget): background `#fee2e2`, text `#7f1d1d`, border `1px solid #fca5a5` (contrast > 8.5:1, exceeds WCAG AAA).
  - Add comparison matrix styles:
    - `.comparison-table`: responsive table with border collapse, alternating row backgrounds, sticky column/row headers.
    - `.comparison-cell`: aligned content, padding, borders.
    - `.comparison-header-cell`: strong visual distinction for alternative headers and action buttons.
  - Add mobile switcher tab styles:
    - `.mobile-switcher`: visible only on small screens via media query `@media (max-width: 768px)`.
    - `.mobile-tablist`: horizontal flex/grid with scrollable container and active focus-visible styling.
    - `.mobile-tab`: accessible button styled with tab semantics.
  - Add booking review styles:
    - `.booking-review-card`: structured cards for each component snapshot.
    - `.booking-disclosure-callout`: high-visibility notice box with accent border and distinct icon/eyebrow.
    - `.confirm-booking-disabled`: disabled button styling with helper text.
- [x] Accessibility refinements in `ItineraryComparisonView.tsx`:
  - Full keyboard support for mobile switcher tablist (`onKeyDown` handling for ArrowLeft, ArrowRight, Home, End).
  - ARIA attributes: `role="grid"`, `role="row"`, `role="columnheader"`, `role="rowheader"`, `role="gridcell"`.
  - Accessible names on all action buttons (`aria-label={`Select alternative ${id} for booking review`}`).

### Automated verification
- [x] `npm.cmd --prefix frontend test -- --run` — full test suite verifying keyboard interaction, ARIA roles, contrast classes, and screen-reader announcements.
- [x] `.\mvnw.cmd test` — full backend test suite to ensure zero regressions across all 140 JUnit tests.

### Optional developer checks
- [ ] None.

---

## Test Strategy
- **Unit / Component Tests (`frontend/src/ItineraryComparisonAndBookingReview.test.tsx`):**
  - **Constraint enforcement:** Verify selecting 2 or 3 Planned alternatives enables comparison; selecting < 2 keeps button disabled; selecting > 3 is blocked and renders accessible notification.
  - **Desktop Matrix Display:** Verify side-by-side rendering of grand totals, budget remaining/overage badges, flight stops/durations/times/timezones, stay property/category/room count, and rental vehicle class/cycles/prices.
  - **Mobile Switcher & Tabs:** Verify mobile layout switcher tabs, ArrowLeft/ArrowRight keyboard navigation, Home/End shortcuts, and `aria-live` announcements.
  - **Missing Component Distinction:** Verify that unselected optional components display "— No [component] selected" with muted styling and are distinct from $0 items.
  - **Booking Review Navigation & Content:** Verify that clicking "Select for Booking Review" from comparison or card opens review view displaying destination, traveler count, complete snapshot facts, itemized totals, the exact fictional inventory disclosure, and the disabled Phase 6 button.
  - **Return Navigation:** Verify returning from Booking Review back to comparison or back to trip workspace.
- **Regression Testing:**
  - Run all existing 64 frontend tests (`npm.cmd --prefix frontend test -- --run`).
  - Run all 140 backend tests (`.\mvnw.cmd test`).

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| 1. Users can select 2 or 3 Planned alternatives for a Trip and launch the comparison view; selecting fewer than 2 or more than 3 is cleanly constrained. | `AlternativeCard.tsx` (compare checkboxes), `TripWorkspace.tsx` (`handleToggleCompare`, 2-to-3 selection logic, disabled trigger button, alert notification) | Tests asserting compare button disabled at 0 or 1 selection, enabled at 2 and 3, and 4th selection blocked with accessible notification |
| 2. Desktop viewport renders side-by-side comparison columns; mobile viewport renders a stacked layout with a persistent alternative switcher. | `ItineraryComparisonView.tsx` (table/grid layout with side-by-side columns, mobile tablist switcher with active card) | Tests verifying presence of desktop grid headers and mobile switcher tabs with `role="tab"` and `role="tablist"` |
| 3. Comparison displays grand total, budget position (remaining/overage), flight stops/duration/times, stay type/location/rooms, and car class/times. | `ItineraryComparisonView.tsx` rendering all snapshot attributes from `selections.airfare`, `selections.stay`, `selections.rental`, and `tally` | Tests verifying values for grand total, budget position badge, flight carrier/stops/times/timezones, stay property/category/rooms, and rental car class/cycles |
| 4. Missing optional components are visually and semantically distinguished from zero-cost selections. | `ItineraryComparisonView.tsx` and `BookingReviewView.tsx` (`.missing-component` with muted styling, em-dash, and "No [component] selected") | Tests asserting presence of `.missing-component` text when component is null, contrasting with "$0.00" for zero-cost selections |
| 5. Clicking "Select for Booking Review" on any compared alternative opens the booking review summary with all component details, totals, and the fictional booking disclosure. | `BookingReviewView.tsx` with snapshot details, itemized totals, disclosure notice, disabled Confirm Booking button; triggered by `AlternativeCard` and `ItineraryComparisonView` | Tests clicking "Select for Booking Review", verifying snapshot items, disclosure text, disabled button, and return navigation |
| 6. Keyboard navigation, screen-reader announcements, and responsive viewport behavior are thoroughly verified in Vitest tests. | `ItineraryComparisonView.tsx` (`onKeyDown` handling for arrow keys, `aria-live="polite"` region, ARIA grid roles) | Tests using `@testing-library/user-event` sending `{arrowright}`, `{arrowleft}`, `{home}`, `{end}`, and checking focus and `aria-selected` / `aria-live` attributes |

## Risks and Rollback/Recovery
- **Stale Comparison Selection:** If a user deletes a planned alternative while it is selected for comparison, `TripWorkspace` could have held an orphaned ID. Mitigation: In `TripWorkspace`, whenever `trip` updates or an alternative is deleted, `selectedForCompareIds` is filtered to only include IDs present in current `trip.alternatives`.
- **Responsive Context Switch:** Switching between desktop and mobile layouts should not drop or reset comparison selections. Mitigation: The selected IDs and comparison data are owned by `TripWorkspace` and passed as props, keeping data state independent of layout viewports.
- **Rollback:** The feature is completely additive to frontend components and styles. If needed, reverting the commits restores `TripWorkspace` and `AlternativeCard` to their prior read-only state.

## References
- Ticket: `ai/thoughts/tickets/2026-09-22-p05-t04-deliver-itinerary-comparison-and-booking-selection.md`
- Research: `ai/thoughts/research/2026-09-22-p05-t04-deliver-itinerary-comparison-and-booking-selection.md`
- Source files:
  - `frontend/src/components/TripWorkspace.tsx`
  - `frontend/src/components/AlternativeCard.tsx`
  - `frontend/src/components/ItinerarySummaryTally.tsx`
  - `frontend/src/components/AirfareSearchSection.tsx`
  - `frontend/src/api/tripsApi.ts`
  - `frontend/src/style.css`
- Existing tests:
  - `frontend/src/DraftPromotion.test.tsx`
  - `frontend/src/ProgressiveTripBuilder.test.tsx`
