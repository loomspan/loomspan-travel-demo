# Responsive Navigation and Trip Context Testing Plan

## Change Summary

Add shared authenticated Home/Profile/Trip/logout navigation, keep the open Trip/Draft workspace through view changes, provide explicit save and loading recovery, and complete mobile layouts and comparison selector behavior. No server endpoint or persistence contract changes are planned.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Navigation | A subview omits a primary destination or loses the active Trip | Traverse Home, Profile, workspace, comparison, and booking review in `App.test.tsx` |
| Draft continuity | View switch or stale prop overwrites local edits/selected components | Deferred API response and return tests in `ProgressiveTripBuilder.test.tsx` |
| Save recovery | Failed save is labeled saved, retry uses stale values/version, or refresh loses edits | Rejection/retry/conflict tests in `App.test.tsx` |
| Creation | Home entry creates an unintended component or wrong initial search | Existing entry-mode tests plus Home-navigation assertions in `ProgressiveTripBuilder.test.tsx` |
| Search/history | Empty/error states strand the user | Failed request then Retry in relevant frontend tests |
| Comparison | Fourth/Draft/Booked item admitted; missing component shown as zero; mobile selector loses panel | `ItineraryComparisonAndBookingReview.test.tsx` |
| Responsive layout | Required information/actions clip at narrow widths; selector scrolls away | Automated DOM/action checks plus optional browser width/scroll observation |

## Existing Coverage and Environment Constraints

Frontend uses Vitest, jsdom, React Testing Library, and mocked `tripsApi`/identity calls. `frontend/package.json` defines `npm test` and `npm run build`. `App.test.tsx` covers account restoration, empty onboarding, autosave and conflict; `ProgressiveTripBuilder.test.tsx` covers entry modes and component selection; `ItineraryComparisonAndBookingReview.test.tsx` covers desktop/mobile comparison and booking; `FeeFreeCancellationAndTriage.test.tsx` covers history failure. These are safe local tests with mocked network boundaries. jsdom does not calculate real responsive geometry or CSS sticky behavior, so narrow-width and scroll fit remain optional local browser observations. No credentials, live service, destructive backend operation, or new browser tool is required for automated checks.

## Failing Test First

- Name: `keeps the active Draft when moving through Home and Profile and returning to Trip`
- Type: React integration test with mocked APIs
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx`
- Arrange/Act/Assert: Open an owned Trip with a selected component and tally; use named Home and Profile controls, return through the active Trip control, and assert the same Draft ID, selected component, tally, and search mode. Verify navigation itself sent no create/select/remove mutation.
- Expected pre-fix failure: The current workspace has no named Home/Profile/active Trip navigation, and leaving it clears `activeTrip` and unmounts local workspace state.

## Tests to Add or Update

### 1. `shows named navigation from each authenticated primary state`
- Type: React integration.
- Location: `frontend/src/App.test.tsx`
- Proves: Home, Profile, active Trip/itinerary, and Log out are reachable from Home, Profile, Trip workspace, comparison, booking review/confirmation as applicable; logout ends the authenticated view.
- Inputs/fixture: Existing account and Trip fixtures; mocked logout.
- Doubles or boundary isolation: Mock identity and Trip API; no live auth.
- Edge cases: Active Trip control absent or disabled with clear context before any Trip opens; failed Trip opening leaves other navigation usable.

### 2. `starts a first Trip from Plan Trip, Airfare, and Stay on Home`
- Type: React integration.
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx`
- Proves: All three Home entry actions open creation and initialize intended component search only after explicit action.
- Inputs/fixture: Existing creation responses per entry mode.
- Doubles or boundary isolation: Mock create/search/select API.
- Edge cases: No rental search or selection starts without Add a car; empty and populated profile cases both retain Home entry points.

### 3. `keeps unsaved changes visible and retries the current save`
- Type: React integration with fake timers/deferred promises where existing suite uses them.
- Location: `frontend/src/App.test.tsx`
- Proves: Navigation during debounce/in-flight save retains edit; rejected write shows failed status, does not show saved status, Retry sends the edited values and current expected version; successful response updates state.
- Inputs/fixture: Existing Trip fixture, first `replaceSharedDetails` rejected as network failure, second resolved with newer Trip.
- Doubles or boundary isolation: Mock `tripsApi.replaceSharedDetails` and `getTrip`.
- Edge cases: Validation error stays associated with fields; conflict offers explicit server reload and does not auto-overwrite unsaved edits.

### 4. `restores authoritative selections and tally after Trip reopen`
- Type: React integration.
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx`
- Proves: Return/refresh uses owner-scoped Trip detail response and displays server selection/tally; absent selection is shown as absent.
- Inputs/fixture: Two versions of the same Trip with distinct Draft selection and tally.
- Doubles or boundary isolation: Mock `getTrip` and selection mutation.
- Edge cases: Later stale response cannot overwrite a newer mutation result; switching to another Trip discards old context only through the explicit open flow.

### 5. `retries search and booking-history load failures`
- Type: React integration.
- Location: `frontend/src/ProgressiveTripBuilder.test.tsx` for component search; `frontend/src/FeeFreeCancellationAndTriage.test.tsx` for history.
- Proves: Loading, empty, failure, and Retry are purposeful, and recovered results display without opening a new Trip.
- Inputs/fixture: First request rejects, retry resolves with representative sanitized options/bookings.
- Doubles or boundary isolation: Mock `tripsApi` reads only.
- Edge cases: Empty history remains labeled empty; failed refresh retains previously loaded history if available.

### 6. `limits comparison to three Planned alternatives and keeps mobile content distinct`
- Type: React integration.
- Location: `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`.
- Proves: Two and three alternatives from one Trip compare; fourth is rejected; Draft/Booked alternatives are ineligible; mobile selector switches the one visible stacked panel; absent component text differs from a selected zero-cost amount; booking-review action remains present.
- Inputs/fixture: Existing Planned alternatives with one absent and one zero-cost component, plus a Draft/Booked alternative.
- Doubles or boundary isolation: Mock Trip and booking APIs.
- Edge cases: Selected alternative removal clamps current mobile panel; no one-item launch because Phase 5 calls for multiple alternatives.

### 7. `preserves required controls in mobile markup`
- Type: React semantic/interaction test, supplemented by browser observation.
- Location: `frontend/src/App.test.tsx` and `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`.
- Proves: Core actions remain in the DOM and operable at a mocked mobile viewport; selector and panel labels remain associated.
- Inputs/fixture: Profile with nested alternatives, search options, history, and comparison fixtures.
- Doubles or boundary isolation: Mock API and viewport; do not claim geometry from jsdom.
- Edge cases: Long IDs and monetary values receive CSS wrapping/overflow treatment verified in local browser.

## Safe Verification Commands

- Focused: `npm test -- src/ProgressiveTripBuilder.test.tsx` from `frontend`.
- Related suite: `npm test -- src/App.test.tsx src/ProgressiveTripBuilder.test.tsx src/ItineraryComparisonAndBookingReview.test.tsx src/FeeFreeCancellationAndTriage.test.tsx src/DraftPromotion.test.tsx` from `frontend`.
- Full safe suite: `npm test` and `npm run build` from `frontend`.

## Optional Developer Checks

- In a configured local browser at 320px, 375px, and desktop widths, inspect Home, Profile, Trip, search results, booking review/history, and comparison for horizontal loss of required content or controls. Scroll a long mobile comparison and confirm the itinerary selector remains visible/reachable. Observe slow and failed local-network requests to confirm save/retry wording. Do not use production services.

## Exit Criteria

- [ ] The planned red test fails for the intended navigation/context reason before implementation.
- [x] New and updated tests pass after implementation.
- [x] The full safe frontend test suite and build pass.
- [ ] Each ticket acceptance criterion has executable interaction/state evidence, with mobile geometry explicitly reported as optional observation.
- [x] Tests use mocked boundaries and perform no unintended live or destructive operation.
- [ ] Failed saves, conflict reload, stale responses, empty searches/history, and comparison limits are covered.
- [x] Optional browser checks are reported as nonblocking and never claimed as performed unless actually run.
