# Public Home and Trip Save Gate Testing Plan

## Change Summary

PR1 makes Home and trip-start accessible without a session, moves account entry to the first save, restores the trip form after authentication, and preserves edited values when a save loses its session. Server Trip APIs remain authenticated, owner-scoped, and CSRF-protected. The client-held name is an auth-handoff value until PR2 adds its persisted contract.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Public entry | Initial 401 still shows auth instead of Home, or private data appears to guests | App tests for initial 401, public Home/Trips, guest-only nav, and zero Trip GET requests |
| Save gate | Guest submission or auth success creates a Trip prematurely or twice | Fetch assertions across guest form, auth, cancellation, and deferred duplicate submission |
| Draft retention | Name, destination, dates, count, or ages are lost on auth/cancel | Field-by-field assertions after login, registration, and cancel |
| Session expiry | Failed save reports success, drops edits, or retries automatically | 401 create/workspace tests with explicit retry and saved-status assertions |
| Account isolation | A different account can see/retry prior owner's retained workspace | Account-switch test clearing private state and no cross-account write |
| Server security | Public SPA inadvertently permits Trip/option/component/booking requests | MockMvc anonymous, CSRF, and cross-owner cases |

## Existing Coverage and Environment Constraints

`frontend/src/App.test.tsx` currently tests auth-first bootstrap, registration/login/logout, modal creation, autosave failure/retry, and profile refresh. `frontend/src/ProgressiveTripBuilder.test.tsx` tests authenticated Home entry and workspace retention. These assertions need updates to reflect public Home/Trips and inline start. Vitest runs in jsdom via `frontend/vite.config.ts`; `frontend/package.json` provides `npm test` and `npm run build`. `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java` and `src/test/java/app/detour/trip/TripApiIntegrationTest.java` use MockMvc and local fixtures. `.\mvnw.cmd clean verify` is the documented broad gate and runs the packaged verifier with isolated temporary H2 data; no live external service is required.

## Failing Test First

- Name: `shows public Home and Trips after an anonymous profile probe`
- Type: Vitest/Testing Library integration
- Location: `frontend/src/App.test.tsx`
- Arrange/Act/Assert: Mock `/api/profile` as 401, render `App`, then assert Home and the Home/Trips/Log in navigation appear and no auth form or `/api/trips` request occurs.
- Expected pre-fix failure: Current `App` renders `AuthScreen` after 401 and has no guest Home/Trips navigation.

## Tests to Add or Update

### 1. `shows public Home and Trips after an anonymous profile probe`
- Type: UI integration
- Location: `frontend/src/App.test.tsx`
- Proves: Initial Home is public, 401 is normal guest state, and Trips exposes an unsaved form only.
- Inputs/fixture: Profile 401 response; public Home content and Trips navigation.
- Doubles or boundary isolation: Mock `fetch`; inspect all request URLs/methods.
- Edge cases: Unexpected profile failure still reports an error while public content remains navigable.

### 2. `holds trip details through registration login and cancel until explicit continue`
- Type: UI integration
- Location: `frontend/src/App.test.tsx`
- Proves: Name, destination, dates, count, and each age survive both auth modes and cancel; auth success makes no create request; one subsequent Start planning creates one owned Trip.
- Inputs/fixture: Complete supported trip values; 401 profile, successful auth/profile responses, one 201 create response.
- Doubles or boundary isolation: Mock `fetch`, count `POST /api/trips` and inspect payload for supported fields and ages. Do not expect a persisted name until PR2.
- Edge cases: Mode switching clears password but not trip draft; repeated click while create pending does not duplicate POST; incomplete values never invoke auth/create.

### 3. `returns direct login to its origin and exposes signed-in destinations`
- Type: UI integration
- Location: `frontend/src/App.test.tsx`
- Proves: Direct Log in uses the common form; cancel and success return to Home or Trips as appropriate; Profile and saved Trips are available after authentication.
- Inputs/fixture: Guest profile 401, login success, populated profile with Trip summary.
- Doubles or boundary isolation: Mock identity and Trip responses.
- Edge cases: No private Trip summary appears before authentication; authentication errors leave intended destination intact.

### 4. `retains unsaved trip form after unauthenticated create and retries only by request`
- Type: UI integration
- Location: `frontend/src/App.test.tsx`
- Proves: A 401 create shows failed-save state, keeps all input values, requests auth, and creates only after login and another explicit click.
- Inputs/fixture: Authenticated profile, complete form, create 401, login/profile success, create 201.
- Doubles or boundary isolation: Mock `fetch` sequence and count writes.
- Edge cases: No success notice after 401; no automatic second POST during auth.

### 5. `keeps workspace edits after save 401 until same-account login and explicit retry`
- Type: UI integration
- Location: `frontend/src/App.test.tsx` or `frontend/src/ProgressiveTripBuilder.test.tsx`
- Proves: Autosave 401 retains dirty inputs and version, shows unsaved error, pauses automatic retries, hides private workspace during auth, restores it for the same account, and saves only on Retry save.
- Inputs/fixture: Existing owned Trip with initial version; edit a shared field; 401 replace response, login/profile success, 200 replace response.
- Doubles or boundary isolation: Mock `fetch`, use fake timers/deferred responses only for debounce boundaries.
- Edge cases: A different-account login clears retained private state; explicit logout clears it; network and version-conflict paths continue to behave as before.

### 6. `keeps anonymous and cross-owner Trip operations rejected`
- Type: MockMvc integration
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java` and `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Public SPA and auth still work, while anonymous create/list/detail/component/option/booking paths reject requests; authenticated mutation without CSRF rejects; other owners cannot read/write a Trip.
- Inputs/fixture: Existing test users, Trip, Draft/option, and booking fixtures where available.
- Doubles or boundary isolation: MockMvc and repository test database; reuse existing coverage and add only missing route families.
- Edge cases: Session logout/expiry; registration/login CSRF behavior.

## Safe Verification Commands
- Focused: `cd frontend; npm test -- --run src/App.test.tsx src/ProgressiveTripBuilder.test.tsx`
- Related suite: `cd frontend; npm test`
- Full safe suite: `.\mvnw.cmd clean verify`

## Optional Developer Checks
- Inspect guest Home/Trips, auth cancel/return focus, and narrow viewport layout in a local browser.
- With a locally expired test session, inspect that edited workspace fields remain and Retry save is clearly deliberate.

## Exit Criteria
- [ ] The public Home red test fails for the intended reason before implementation. This pre-fix check was not run; the new Home test passed after implementation.
- [x] New and updated frontend tests pass, including exact write counts and draft retention.
- [x] The broadest safe relevant repository test suite passes.
- [x] Each PR1 acceptance criterion maps to an executable assertion above.
- [x] Routine tests use mock HTTP/local fixtures and perform no live or destructive operation.
- [x] Auth expiry, cancel, duplicate submit, and account isolation paths are covered.
- [x] Optional browser observations are reported as nonblocking and never represented as completed automated checks.
