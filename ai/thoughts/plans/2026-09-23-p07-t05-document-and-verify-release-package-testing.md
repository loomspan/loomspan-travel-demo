# DeTour Release Package Testing Plan

## Change Summary
Document the implemented app, remove one obsolete public route matcher, and prove a clean packaged release without provider credentials.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Clean build | Old generated assets enter package | `mvnw.cmd clean verify`, JAR entry inspection |
| Security | Stale public route remains | scoped search, HTTP unauthorized route assertion |
| Persistence | Restart loses users, Trips, Plans, bookings or history | restart integration tests and packaged smoke |
| Booking | Double decrement or partial cancellation | booking API, cancellation and concurrency suites |
| Time | Wall-clock tests miss status or cutoff | `TestClockConfiguration` tests |
| UI | Registration path, keyboard or mobile flow unclear | Vitest interaction tests and viewport/keyboard walkthrough |

## Existing Coverage and Environment Constraints
JUnit 5 Spring Boot tests use in-memory/temporary H2; Vitest/jsdom covers frontend semantics. Java, Node, and npm are installed. Browser-level visual and keyboard checks may need a browser driver, while configured external services are not required.

## Failing Test First
- Name: obsolete demo disclosure route authorization
- Type: scoped source/HTTP regression
- Location: `SecurityConfiguration.java` and packaged smoke
- Arrange/Act/Assert: request `/demo-disclosure/obsolete` anonymously and expect unauthorized, plus no matcher in source.
- Expected pre-fix failure: the scoped source search finds the public matcher and aliases. The HTTP path may still return 404 without a controller, so an HTTP 404 alone cannot prove removal.

## Tests to Add or Update
### 1. `verify-packaged-release.ps1` release journey
- Type: packaged HTTP smoke
- Location: `scripts/verify-packaged-release.ps1`, `scripts/verify-packaged-release.mjs`
- Proves: clean startup, registration, Trip/Plan, restart requiring login, persisted Trip/Plan and no credentials.
- Inputs/fixture: temporary H2 file, March 2027 SFO dates, fixed pre-departure instant.
- Doubles or boundary isolation: local loopback JAR child process only.
- Edge cases: stale cookie after restart; cleanup on failure.

### 2. `persistsBookingAndSnapshotsAcrossApplicationRestart`
- Type: JUnit HTTP integration
- Location: `src/test/java/app/detour/booking/BookingApplicationRestartIntegrationTest.java`
- Proves: booking reference and frozen snapshots persist; extend for cancellation history if needed.
- Inputs/fixture: temporary H2 file and seeded catalog.
- Doubles or boundary isolation: local application contexts.
- Edge cases: catalog mutation after booking.

## Safe Verification Commands
- Focused: `.\mvnw.cmd -DskipFrontend=true -Dtest=DetourApplicationTest,BookingApiIntegrationTest,BookingCancellationIntegrationTest,BookingConcurrencyIntegrationTest,BookingApplicationRestartIntegrationTest test`
- Related suite: `npm test` in `frontend/`
- Full safe suite: `.\mvnw.cmd clean verify`
- Packaged: `.\scripts\verify-packaged-release.ps1`
- Fixtures: `.\scripts\show-catalog-fixture-summary.ps1`

## Optional Developer Checks
- Browser keyboard-only and 320px/mobile viewport walkthrough if browser automation is unavailable.

## Exit Criteria
- [x] Route allowance removed and source/generated-asset search clean; deliberate negative route probes remain in the verifier.
- [x] New/updated checks pass.
- [x] Backend and frontend suites pass after a clean build.
- [x] Packaged app registers, plans, books, cancels, restarts and preserves state without model API keys.
- [x] Tests and scripts use memory or temporary file databases; no external provider is contacted.
- [x] Mobile-width rendered walkthrough is recorded as optional, not passed.
