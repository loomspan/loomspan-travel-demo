# P07-T05 DeTour Release Verification — 2026-09-23

## Package

- Built artifact: `target/detour-0.1.0-SNAPSHOT.jar` (37,668,157 bytes).
- SHA-256: `B1E0C47B36F48AD3F159F70017A78D543394E2FD0D49419D4DEA8DCEAB0675F1`.
- Build: Java 25, Node 24.19.0, npm 11.17.0; application target remains Java 21.
- `jar tf` showed DeTour migrations V1–V18 and one current React JS/CSS pair in `BOOT-INF/classes/static/assets/` after a clean build.
- The build uses no model credentials or external supplier service. The packaged verifier removes model/API credential environment variables from its JAR child process.

## Executed checks

| Check | Result and evidence |
| --- | --- |
| `npm test` in `frontend/` | PASS: 13 files, 134 tests. Includes registration/onboarding, accessible focus and dialogs, disclosure, responsive component behavior, reduced motion CSS, comparison, booking, and cancellation UI. |
| `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' clean verify` | PASS: 185 backend tests, 0 failures/errors/skips, frontend `npm ci` and build, executable JAR. Full output: `2026-09-23-p07-t05-clean-verify.log`. The explicit repository path was needed only because this sandbox defaults Maven to inaccessible `C:\.m2`. |
| `.\scripts\verify-packaged-release.ps1` | PASS: isolated clean file database, V1–V18 startup, registration, SFO airfare selection and Planned itinerary, booking and idempotent replay, cancellation, process restart, old session rejected, new login, persisted Trip/Planned/Booking snapshots and reference/cancellation history, other-owner 404, and obsolete-route 404. Temporary loopback port/database; no default database or provider touched. |
| `.\scripts\show-catalog-fixture-summary.ps1` | PASS: 3 destinations, 9 airports, 720 flight instances, 18 stay properties, 540 stay nights, 21 rental units. Direct and connecting options and all three rental classes were reported for SFO, MUC, and MEX. |
| `.\scripts\reset-detour.ps1` | PASS preview: printed `data/detour.mv.db` and confirmation instruction. Did not run `-ConfirmReset`; existing default file remained present. |
| Packaged browser at local loopback | PASS desktop first-use render: registration action and About tab present. Opening About focused its heading; Escape closed it and restored focus to the trigger. |

An initial focused backend run failed because `RentalSearchAndSelectionIntegrationTest` still called the removed `/car` aliases. The test was corrected to use `/rentals`; the subsequent clean full build passed. The first packaged verifier run found that Windows force termination could lose an H2 delayed write; the isolated test URL now uses `WRITE_DELAY=0`, and the full packaged journey passed twice afterward. The default application database configuration was not changed.

After a final review removed duplicate canonical-route calls and stale alias comments from two test files, `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' -DskipFrontend=true '-Dtest=StaySearchAndSelectionIntegrationTest,TripApiIntegrationTest' test` passed: 51 tests, 0 failures. This test-only cleanup did not change the packaged application artifact.

## Acceptance evidence

| Requirement | Evidence |
| --- | --- |
| Current README and clean-break reset | `README.md`, preview-only reset script result; startup code contains no database-file deletion. |
| Clean DeTour lineage and no keys | `DetourApplicationTest` asserts V1–V18 and empty user/Trip state; clean JAR verifier creates a user without model credentials. |
| Representative first-time plan | Packaged HTTP registration, Trip creation, search, airfare selection, readiness, promotion and booking; frontend tests cover corresponding first-use actions. |
| Restart and history | Identity, Trip and Booking restart integration tests plus packaged old-session/new-login check; packaged canceled booking history and frozen reference/totals survived restart. |
| Owner, price, eligibility and availability rechecks | `TripApiIntegrationTest`, component selection suites, `TripPricingAndTallyIntegrationTest`, `DraftReadinessAndPlannedSnapshotIntegrationTest`, `BookingApiIntegrationTest` and `BookingCancellationIntegrationTest`. |
| Atomic and idempotent booking/cancellation | `BookingConcurrencyIntegrationTest` covers flight/stay/rental contention, duplicate booking keys, duplicate cancellations, and cancellation/rebooking races. Packaged verifier checked booking replay. |
| Upcoming, Past, Expired and cutoff | `TripApiIntegrationTest.profileProjectionPartitionsUpcomingAndPastWithDeterministicSort`, `TripApplicationRestartIntegrationTest.upcomingAndPastDeriveCorrectlyAcrossRestartWithoutMutation`, expired planning/booking tests, and cancellation cutoff tests use injected test clocks. |
| Accessibility and responsive behavior | 134 frontend tests include semantics, focus traps, focus restoration, reduced motion, comparison/mobile controls; desktop browser focus check passed. CSS contains 768px comparison switch and 520px one-column/mobile rules. |

## Obsolete behavior and product copy search

Scoped `rg` scans covered `src/`, `frontend/src/`, `scripts/`, `README.md`, `pom.xml`, `frontend/dist/`, and `target/classes/static/`. No executable or product-facing Wayfarer/Loomspan names, old `/api/v*` routes, `/demo-disclosure/**` allowance, controller route aliases, or stale generated frontend bundles remain. The release verifier intentionally sends requests to two removed routes and asserts 404; historical planning artifacts and negative legacy-table assertions are outside the obsolete-implementation finding. General demo copy exists only in `AboutDemoTab`; booking review/confirmation retains the required transaction-specific fictional notice.

## Optional developer check

The browser interface available to this run had no viewport override, so a rendered 320px/mobile-width keyboard walkthrough was not performed. CSS breakpoints and frontend interaction tests passed; a physical or responsive browser viewport check remains useful before a public demo. Deployment was outside this release task.
