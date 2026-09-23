# DeTour Visual System Testing Plan

## Change Summary
Shared CSS and display terminology change across authentication, trip planning, comparison, booking, and history; server-backed behavior remains intact.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Identity | Nested views omit wordmark or headings lose focus | App/workspace tests and build |
| Lifecycle | Draft, Planned, Booked, canceled, or expired text becomes ambiguous | list/card/history assertions |
| Money | Missing shown as zero or totals lose budget meaning | tally/comparison/review/confirmation assertions |
| Behavior | Optional component forms appear early; actions change | existing progressive, booking, cancellation suites |
| CSS | Focus or responsive hierarchy weakens | selector review and optional visual inspection |

## Existing Coverage and Environment Constraints
Vitest/Testing Library mocks API calls. `npm test` and `npm run build` run locally without live supplier services. Existing suites cover the three entry flows, booking, cancellation, and dialogs. CSS visual appearance is not measured in jsdom.

## Failing Test First
- Name: missing-component and money-label rendering
- Type: frontend component test
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx` and `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`
- Arrange/Act/Assert: render representative empty and selected itineraries; assert missing text differs from zero value and USD context appears.
- Expected pre-fix failure: current draft tally renders a dash for absence and money has no explicit USD context.

## Tests to Add or Update
### 1. `renders consistent missing and money labels`
- Type: component integration
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx`
- Proves: Draft tally shows distinct missing state, grand total, budget, and USD context.
- Inputs/fixture: existing draft and budget fixtures.
- Doubles or boundary isolation: mocked trips API.
- Edge cases: no selection, zero total, overage.

### 2. `retains lifecycle and booking vocabulary`
- Type: component integration
- Location: `frontend/src/App.test.tsx`, `frontend/src/FeeFreeCancellationAndTriage.test.tsx`
- Proves: visible text identifies states and scoped destructive actions.
- Inputs/fixture: existing trip/booking fixtures.
- Doubles or boundary isolation: mocked API.
- Edge cases: canceled trip and booking history.

### 3. `maintains entry and booking flows`
- Type: regression suite
- Location: existing `ProgressiveTripBuilder.test.tsx`, `ItineraryComparisonAndBookingReview.test.tsx`, `BookingConfirmationView.test.tsx`.
- Proves: optional reveals and server results unchanged.
- Inputs/fixture: existing fixtures.
- Doubles or boundary isolation: existing API mocks.
- Edge cases: overage, cancellation, empty components.

## Safe Verification Commands
- Focused: `npm test -- --run src/ProgressiveTripBuilder.test.tsx`
- Related suite: `npm test`
- Full safe suite: `npm test` and `npm run build`

## Optional Developer Checks
- Visual inspection at desktop and mobile widths with keyboard focus; jsdom cannot prove appearance.

## Exit Criteria
- [x] New text assertion fails for the intended pre-fix reason.
- [x] New and updated tests pass.
- [x] `npm test` and `npm run build` pass.
- [x] Each acceptance criterion has executable evidence.
- [x] Mocked tests perform no live or destructive operation.
- [x] Missing, zero, state, and entry-flow risks are covered.
- [x] Optional visual check is reported as not performed unless actually done.
