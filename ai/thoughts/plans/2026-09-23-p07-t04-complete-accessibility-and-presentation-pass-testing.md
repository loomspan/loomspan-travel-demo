# Core Workflow Accessibility and Presentation Testing Plan

## Change Summary

The implementation will make flight endpoint dates and zones explicit, keep price/missing-component meaning consistent, and correct keyboard focus, announcements, dialog/comparison semantics, responsive layout, and reduced-motion behavior across the registration-to-cancellation path. Server pricing, inventory, authorization, and PDX lifecycle rules remain untouched.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Flight schedule | Browser-local time masks date changes or wrong endpoint zone; missing snapshot fields create `Invalid Date` | Formatter and search/comparison/review tests for PDX–SFO, PDX–MUC, PDX–MEX, overnight arrival, and absent data |
| Navigation and focus | Focus remains on removed controls, jumps on autosave, or falls behind a dialog | Keyboard path tests asserting `document.activeElement` after each view change and after return |
| Modal actions | Escape/backdrop dismisses a pending destructive action; focus escapes or restoration targets a removed element | Parameterized dialog tests plus cancellation-to-triage handoff |
| Announcements and errors | Duplicate or missing status, or error summary gives no next action | Live-region and error-recovery assertions for auth, search, autosave, readiness, booking, cancellation |
| Comparison | Faux grid implies missing keyboard behavior; mobile tabs have stale `aria-controls` | Native table semantics and tab/tabpanel link, roving focus, arrow/Home/End tests |
| Money | Missing optional component shown as `$0`, or client-derived amounts diverge from authoritative tally | Selected/unselected/over-budget tests across draft, comparison, review, confirmation, history |
| Zoom and motion | Fixed elements obscure focus; cards overflow; smooth movement persists under reduced-motion preference | CSS/DOM checks and documented rendered 200%/narrow/reduced-motion observation |

## Existing Coverage and Environment Constraints

`frontend/package.json` declares `npm test` (`vitest run`) and `npm run build` (`tsc -b && vite build`). Tests use jsdom, Testing Library, and user-event. Existing suites are `frontend/src/App.test.tsx`, `ProgressiveTripBuilder.test.tsx`, `DraftPromotion.test.tsx`, `ItineraryComparisonAndBookingReview.test.tsx`, `FeeFreeCancellationAndTriage.test.tsx`, `VisualSystem.test.tsx`, and component tests for About and confirmation. They cover many individual flows but do not establish a rendered keyboard journey, contrast, or 200% zoom. There is no Playwright or axe dependency in the frontend package; do not invent a browser test command or use a live external service. Backend temporal and cancellation tests already protect airport zone fixtures and the PDX rule; a frontend-only presentation change does not require rerunning them unless backend code changes.

Fixtures should use explicit ISO instants and endpoint IANA zones, not locale-dependent expected clock strings. Assert full local date, zone label, and relative date change; either pin `en-US` formatting or use stable accessible text assertions. For server tallies, intentionally make fixture totals differ from easy client sums so a recomputation regression fails.

## Failing Test First

- Name: `shows destination-local arrival date when a flight crosses midnight`
- Type: Vitest/Testing Library component test
- Location: `frontend/src/components/FlightSchedule.test.tsx` (or the first consuming view test if the helper is introduced after the red test)
- Arrange/Act/Assert: Render a PDX-to-MUC leg with a UTC instant and separate `America/Los_Angeles` departure and `Europe/Berlin` arrival zones. Assert both endpoint-local calendar dates, clock times, airport/direction labels, and IANA zones are readable; assert the arrival date is the next local date. Add a Mexico City case and a missing-snapshot case in the same suite.
- Expected pre-fix failure: Current `formatTime`/view markup shows only clock time and zone; no endpoint-local date or direction label is available.

## Tests to Add or Update

### 1. `renders local date and zone at each flight endpoint`
- Type: component test
- Location: `frontend/src/components/FlightSchedule.test.tsx`
- Proves: Dates, clocks, and zones are tied to each endpoint; date-changing/overnight itineraries are explicit; absent or invalid optional timestamp produces `Schedule unavailable` rather than `Invalid Date` or guessed trip date.
- Inputs/fixture: PDX/SFO/MUC/MEX ISO timestamp pairs with `America/Los_Angeles`, `America/Chicago` or destination-specific catalog zone only as supplied by fixture; use `America/Mexico_City` for MEX and `Europe/Berlin` for MUC.
- Doubles or boundary isolation: No API; use fixed timestamp strings.
- Edge cases: Return leg with endpoints reversed, partial snapshot fields, invalid timestamp.

### 2. `uses endpoint schedule display in search, comparison, and review`
- Type: rendered view tests
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- Proves: Both legs in search, desktop comparison, mobile comparison, and review expose the same full local schedule and no time-only rendering remains.
- Inputs/fixture: Existing trip/alternative fixtures extended with one date-changing outbound and return leg.
- Doubles or boundary isolation: Mock `tripsApi` as existing tests do; no server/network.
- Edge cases: Optional legacy snapshot fields absent in comparison/review.

### 3. `moves focus through registration, trip creation, comparison, booking, and return`
- Type: integration-style jsdom workflow test
- Location: `frontend/src/App.test.tsx`, `frontend/src/ProgressiveTripBuilder.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- Proves: New view heading receives focus after a route-like transition; returning focuses a surviving workspace/nav target; autosave state updates do not steal focus from editing/search controls.
- Inputs/fixture: Existing mocked identity/trip endpoints and a Planned alternative.
- Doubles or boundary isolation: API mocks; `userEvent.keyboard`/`tab` for keyboard actions.
- Edge cases: Initiating button removed by transition, reopening active Trip, asynchronous refresh after booking.

### 4. `announces actionable error and recovery state`
- Type: integration-style component tests
- Location: `frontend/src/App.test.tsx`, `frontend/src/ProgressiveTripBuilder.test.tsx`, `frontend/src/DraftPromotion.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- Proves: Auth field errors are associated and summary focused; search failure has Retry; autosave conflict/failure tells user how to recover; readiness jumps target the issue; booking conflict and cancellation failure are announced; successful booking and cancellation identify the result and next action.
- Inputs/fixture: Existing failure mocks (`VALIDATION_FAILED`, version conflict, network error) and success responses.
- Doubles or boundary isolation: Mock APIs; fake timers only for 600 ms autosave debounce.
- Edge cases: Status updates do not produce duplicate conflicting announcements or focus jumps.

### 5. `keeps modal focus and pending cancellation contained`
- Type: component/workflow keyboard tests
- Location: `frontend/src/FeeFreeCancellationAndTriage.test.tsx`, `frontend/src/ProgressiveTripBuilder.test.tsx`, `frontend/src/App.test.tsx`, `frontend/src/components/AboutDemoTab.test.tsx`
- Proves: Initial focus is within dialog; Tab/Shift+Tab wrap; Escape/backdrop restore initiator before pending; pending destructive operation cannot dismiss; cancellation success transfers focus to triage and later to a connected workspace fallback; About opens at its heading, Escape closes from inside it, and the trigger regains focus.
- Inputs/fixture: Deferred cancellation promise and existing modal fixtures.
- Doubles or boundary isolation: API mocks; no real mutation.
- Edge cases: Removed initiator, failure then retry, non-modal About permits page focus.

### 6. `exposes native comparison table and linked mobile tabs`
- Type: component test
- Location: `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- Proves: Desktop uses a native table with headers and no faux `grid`; mobile tabs have one selected/Tab-reachable tab, `aria-controls` resolves to rendered panel, and arrow/Home/End keys update focus and selection.
- Inputs/fixture: Two to three Planned alternatives, including one missing optional component.
- Doubles or boundary isolation: Pure rendered view.
- Edge cases: Alternative list shrinks and active index clamps; disabled booking action with active Booking.

### 7. `preserves authoritative USD and absent-component meaning`
- Type: rendered view tests
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/components/BookingConfirmationView.test.tsx`, `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- Proves: Selected component totals, grand total, and overage/remaining position retain server tally in comparison, review, confirmation, and history; draft selected snapshot total is clear; unselected stay/rental says `Not selected` or equivalent rather than `$0`.
- Inputs/fixture: Non-round cent values, one missing optional component, over-budget and within-budget alternatives, canceled history record.
- Doubles or boundary isolation: Mock API responses; no price calculation service.
- Edge cases: Truly zero-valued selected component remains distinguishable from absence; booking-history status remains textual.

### 8. `honors reduced motion and retains non-color state cues`
- Type: stylesheet/DOM regression test
- Location: `frontend/src/VisualSystem.test.tsx`, `frontend/src/ProgressiveTripBuilder.test.tsx`
- Proves: Reduced-motion media rule exists and focus jump requests instant scrolling when preference matches; badges, availability, budget warning, validation, and booking status include text or icon plus accessible name.
- Inputs/fixture: `matchMedia` mock for reduce/no-preference; existing rendered state fixtures.
- Doubles or boundary isolation: Stub `scrollIntoView`; do not infer rendered zoom/contrast from jsdom.
- Edge cases: Focus outline still present when transitions are disabled.

## Safe Verification Commands

- Focused: `npm test -- --run src/components/FlightSchedule.test.tsx` from `frontend` after red test creation; use the affected test file name if the red test lives in a consuming view.
- Related suite: `npm test -- --run src/App.test.tsx src/ProgressiveTripBuilder.test.tsx src/DraftPromotion.test.tsx src/ItineraryComparisonAndBookingReview.test.tsx src/FeeFreeCancellationAndTriage.test.tsx src/VisualSystem.test.tsx src/components/AboutDemoTab.test.tsx src/components/BookingConfirmationView.test.tsx src/components/FlightSchedule.test.tsx` from `frontend`.
- Full safe suite: `npm test` and `npm run build` from `frontend`. These are local, mock-backed commands and need no credentials or external service.

## Optional Developer Checks

- In a local rendered browser, complete a keyboard-only registration-to-booking and Cancel Booking journey at desktop and narrow mobile widths; verify focus visibility, no unreachable action, and no accidental modal dismissal. Use a local demo/test account and non-production data only.
- At 200% browser zoom, inspect Home, builder/search, comparison, review, confirmation, history, modal, and About panel for clipping, overlap, focus obscuration, and horizontal page scrolling. Check rendered color contrast and text/icon cues.
- With reduced motion and a screen reader enabled, verify schedule announcements for SFO/MUC/MEX and date-changing legs, live outcomes, and the cancellation-to-triage transition. Record browser/AT versions and any limitations; these observations are not represented as automated passes.

## Exit Criteria

- [x] The planned schedule test failed before the display component existed (import resolution) and passed after the schedule display was added; the existing time-only markup was separately confirmed by source inspection.
- [x] New and updated tests pass after implementation.
- [x] `npm test` and `npm run build` pass from `frontend`.
- [x] Every ticket acceptance criterion maps to the executable tests above plus clearly identified rendered observations for visual/AT properties that jsdom cannot prove.
- [x] Automated tests use mocked/local boundaries and perform no live or destructive operation.
- [x] Missing snapshot data, removed focus targets, pending cancellation, date changes, server tally preservation, and narrow mobile comparison are covered.
- [x] Optional browser/AT observations are reported honestly as performed or not performed, with any material residual risk carried into review.
