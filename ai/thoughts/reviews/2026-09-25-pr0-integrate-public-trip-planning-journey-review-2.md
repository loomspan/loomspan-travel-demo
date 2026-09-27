# pr0 Code Review — Cycle 2

## Scope and Repository State

Reviewed the PR0 unstaged and untracked change on `main` against the ticket, research, implementation plan, testing plan, and active design lens. The scope includes frontend cleanup and copy, the blocked legacy promotion route, revised Trip option names, V20 migration, Java and React tests, README, and packaged verifier. No staged changes were present. The preceding PR1–PR5 work is committed; this review did not read the prior PR0 review document.

## Findings

### [P2] Update the auth introduction assertion with the product copy
- Location: `frontend/src/App.test.tsx:196`
- Scenario: The public login or registration form renders the new Trip/Working plan/Saved option introduction.
- Impact: The full frontend suite fails despite the intended copy rendering correctly, blocking verification of the integrated journey.
- Evidence: `npm test -- --run` failed 1 of 152 tests; the assertion expected the old airfare/alternatives sentence while `AuthScreen.tsx` renders the new sentence.
- Fix: Update the shared introduction expectation in the test to the current sentence.

## Findings Resolved in This Context

- Updated `frontend/src/App.test.tsx` to expect the current auth introduction. The frontend suite passes 152/152 afterward. An internal re-review of the change found no further actionable findings.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Public Home, guest draft, auth and explicit save of one Trip/Working plan | `App`, `TripStartForm`, `TripService.create` | `PublicTripFlow` and packaged verifier | implemented |
| One Working plan, two dated named options, compare/update/book | `TripWorkspace`, option routes, booking service | React option/booking tests, Java API/booking tests, packaged verifier | implemented |
| Failed save retains values and retry avoids duplicates | `TripStartForm` retains in-memory fields on failure | `PublicTripFlow` tests | implemented |
| Existing options/bookings survive upgrade and empty Drafts disappear | V19 and forward V20 migration | `TripModelForwardMigrationIntegrationTest` | implemented |
| Ownership, date expiry, cancellation, concurrency and CSRF | Trip and booking services, security configuration | Java suites and packaged verifier | implemented |
| No competing promotion path or old product copy | `promoteDraft` returns a nonmutating conflict; dormant UI actions removed; README and auth copy updated | legacy route regression, React suite | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None. V20's narrow generated-name pattern can also match a deliberately chosen name; the implementation plan records this known ambiguity.

## Verification Results

- FAIL — `npm test -- --run` (frontend) — 1 stale auth introduction assertion; fixed here.
- PASS — `npm test -- --run` (frontend) — 14 files, 152 tests.
- PASS — `npm run build` (frontend) — TypeScript and Vite build.
- PASS — `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' -DskipFrontend=true '-Dtest=TripApiIntegrationTest,TripModelForwardMigrationIntegrationTest,BookingApiIntegrationTest' test` — 65 tests.
- FAIL — `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' clean verify` — transient Java compiler failure reading a dependency JAR during clean rebuild, before tests.
- FAIL — `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' verify` — transient Java test compiler failure reading a different dependency JAR; the compiled classes were subsequently usable.
- PASS — `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' -DskipFrontend=true verify` — 200 tests and packaged JAR; the frontend build separately passed.
- PASS — `.\scripts\verify-packaged-release.ps1` — isolated public-to-booked API journey, restart, ownership, and CSRF checks.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- Browser observation at mobile width and with keyboard-only navigation remains optional; no browser automation was added. The packaged verifier covers API boundaries, while React tests cover UI interactions.

## Disposition

- `fixes-applied` — a fresh Step 5 context must review the updated assertion and current implementation diff.
