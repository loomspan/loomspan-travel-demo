# DeTour Visual Experience Testing Plan

## Change Summary

Home gains three informational, photo-led destinations; existing entry actions remain wired to their current modes. Shared surfaces, icons, native selects, and checkboxes receive visual treatment across the app without changing trip, account, pricing, booking, cancellation, or autosave logic.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Home actions | New layout changes button names, handlers, or opens an unwanted trip flow from a destination card | `ProgressiveTripBuilder.test.tsx` Home tests assert destinations are informational, all three modes still open, and no write occurs until form submission |
| Asset semantics and loading | Missing local files, empty/duplicative alternatives, network dependency, layout shift | DOM `img`/alt assertions, build output/local load check, rendered image dimensions before and after load |
| Native controls | Restyling obscures focus, selection, disabled or error states, or breaks label/keyboard access | Control semantics tests plus CSS state-rule assertions; rendered keyboard and forced-colors checks |
| Navigation and dialogs | Icon markup alters accessible names or focus; restyling clips dialog at narrow widths | Existing `App.test.tsx` focus/dialog test, cancellation dialog tests, rendered keyboard walk |
| Workspace, comparison, booking | CSS layout changes hide status/pricing cues or block actions | Existing `App`, `ItineraryComparisonAndBookingReview`, and `FeeFreeCancellationAndTriage` suites; rendered representative states |
| Responsive layout | Photo cards, long labels, tables, or forms overflow at 320px | Rendered `scrollWidth <= clientWidth` and visual inspection at 320px, 768px, and desktop |

## Existing Coverage and Environment Constraints

`frontend/package.json` supplies `npm test` (Vitest run) and `npm run build` (TypeScript plus Vite). Tests run in jsdom with Testing Library (`frontend/vite.config.ts`). `ProgressiveTripBuilder.test.tsx` already exercises the three Home modes and verifies no POST/PUT/DELETE on navigation; `App.test.tsx` covers heading focus, dialog Escape/restoration, identity, and autosave; `ItineraryComparisonAndBookingReview.test.tsx` covers comparison/booking; `FeeFreeCancellationAndTriage.test.tsx` covers cancellation; `VisualSystem.test.tsx` checks core CSS and wordmark. Keep their mocked API boundary. No Playwright/Puppeteer dependency or image assets are currently in `frontend`; jsdom cannot prove image decode, viewport overflow, touch target sizing, color contrast, or actual focus appearance. A safe local rendered browser check is therefore required for those claims. Do not run a production service or destructive live booking as routine verification.

## Failing Test First

- Name: `shows three informational Featured destinations and preserves Home trip entry actions`
- Type: React integration test with mocked profile/API response.
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx`.
- Arrange/Act/Assert: Render authenticated `App` with empty profile; assert `Featured destinations` region/heading, exactly three named entries and meaningful image alternatives; assert the destination entries have no link/button role and do not change trip state. Click `Plan Trip`, `Airfare`, and `Stay` in turn; assert corresponding existing dialog titles; cancel each; assert zero write requests.
- Expected pre-fix failure: current Home has no Featured destinations heading or images, while the existing action assertions pass.

## Tests to Add or Update

### 1. `shows three informational Featured destinations and preserves Home trip entry actions`

- Type: React integration.
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx`.
- Proves: AC1, part of AC4/AC5; exact labels, image alternatives, no destination click behavior, current entry modes and no premature write.
- Inputs/fixture: Existing empty authenticated profile `fetchMock` pattern.
- Doubles or boundary isolation: Mock `/api/profile`; inspect fetch methods, do not reach server.
- Edge cases: Each action opens a distinct title; cancel returns to Home; destination image paths are local.

### 2. `keeps navigation and action names with decorative icons`

- Type: React component/integration.
- Location: `frontend/src/App.test.tsx` and/or `frontend/src/VisualSystem.test.tsx`.
- Proves: Text names and `aria-current` survive icon additions; icons are `aria-hidden`; existing Home/Profile heading focus still works.
- Inputs/fixture: Existing authenticated profile mock.
- Doubles or boundary isolation: Mock profile response.
- Edge cases: Hidden workspace remains hidden in Home; active trip navigation retains its label.

### 3. `retains native control semantics and visual state hooks`

- Type: Component interaction plus focused CSS source assertion.
- Location: `frontend/src/VisualSystem.test.tsx`, with existing interaction coverage in `frontend/src/ProgressiveTripBuilder.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, and `frontend/src/FeeFreeCancellationAndTriage.test.tsx`.
- Proves: Selects and checkboxes remain native/labeled; `change` and Space/keyboard operation work; checked and disabled behavior remains intact; stylesheet includes visible `:focus-visible`, `:hover`, `:checked`, `:disabled`, and `[aria-invalid="true"]` rules for relevant controls.
- Inputs/fixture: Render representative `TripCreateModal`, `AirfareSearchSection`, `AlternativeCard` or existing flow fixtures as appropriate; use current mocks.
- Doubles or boundary isolation: No live API; use component props and mocked fetch.
- Edge cases: Disabled confirmation/acknowledgment cannot activate, field error still exposes `aria-invalid` and described error, checkbox labels are clickable. Avoid tests that duplicate every CSS declaration or claim computed contrast from jsdom.

### 4. `preserves trip, booking, and cancellation flows after presentation changes`

- Type: Existing broad React integration regression.
- Location: `frontend/src/App.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/FeeFreeCancellationAndTriage.test.tsx`.
- Proves: Trip creation, autosave, alternative selection/comparison, booking review/confirmation, and cancellation states still operate; status and cost meaning remains text-bearing.
- Inputs/fixture: Existing test fixtures and mocked API replies.
- Doubles or boundary isolation: Existing fetch mocks; no live writes.
- Edge cases: Preserve existing conflict/error and dialog focus tests. Update snapshots or markup-dependent queries only when new presentation makes them stale, without weakening behavior assertions.

### 5. `loads owned destination assets without responsive overflow`

- Type: Production build check plus rendered browser observation/measurement.
- Location: Local built/served frontend in a safe test/demo environment; document exact execution in Step 4 report.
- Proves: AC2/AC4/AC5 where jsdom is insufficient: images decode, stable card geometry, readable overlay text, no horizontal overflow at 320px/768px/desktop, visible focus, and operable controls/dialogs.
- Inputs/fixture: Locally served app with test account/fixture data when available; otherwise inspect public and Home states possible in a safe local environment and explicitly list unverified states.
- Doubles or boundary isolation: Local/demo server only; avoid live production accounts or external booking operations.
- Edge cases: Long trip labels, portrait/narrow viewport, image load failure, keyboard Tab/Shift+Tab/Escape, select Arrow keys, checkbox Space, error and disabled states, forced-colors when browser tooling permits.

## Safe Verification Commands

Run from `frontend`:

- Focused: `npm test -- src/ProgressiveTripBuilder.test.tsx src/VisualSystem.test.tsx`
- Related suite: `npm test -- src/App.test.tsx src/ItineraryComparisonAndBookingReview.test.tsx src/FeeFreeCancellationAndTriage.test.tsx`
- Full safe suite: `npm test`
- Build: `npm run build`

The Maven backend suite is not a routine gate for frontend-only presentation changes because no API/backend contract changes are planned. If implementation touches backend logic or build integration, reassess and run its repository-standard verification. Rendered checks require available browser tooling and a safe local app setup; record the exact setup and observation rather than inventing a command in advance.

## Optional Developer Checks

- Compare the finished experience with the approved directional mock-up, including brand feel and image quality.
- Listen with a screen reader on representative Home, form, and dialog flows if a configured screen reader is available; document observations separately from automated test results.

## Exit Criteria

- [x] The Home red test fails for the missing Featured section before implementation.
- [x] New and updated tests pass after implementation, with native labels and action names preserved.
- [x] `npm test` and `npm run build` pass; local image assets are emitted and load.
- [x] At 320px, 768px, and desktop, a rendered browser confirms no document horizontal overflow, stable image geometry, visible keyboard focus, and operable native controls/dialogs on representative screens.
- [x] Existing creation, autosave, comparison, booking, and cancellation regression tests remain passing with their behavior assertions intact.
- [x] No routine test performs live or destructive operations.
- [x] Any unavailable browser, assistive-technology, or configured-data check is reported as NOT RUN/optional as appropriate, with the affected acceptance evidence explicit; it is never reported as passed by jsdom.
