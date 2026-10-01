# Manage independent trip plans Code Review — Cycle 9

## Scope and Repository State

Pipeline Step 5, confirmed Full 5-Step Pipeline (`full`). Comparison base is original checkout HEAD `1ffcacd5cf204e09e2a3800322d79e6d379e4ff0` on main; the initial checkout was clean. Reconstructed the complete ticket change from tracked Git changes and untracked implementation files, rather than limiting review to edits from the immediately preceding context. No prior review documents were read. Read the ticket, research, implementation plan, testing plan, review command, automation protocol and design lens. No repository/ancestor AGENTS.md instruction was supplied or found.

Scope includes canonical TripPlan/PlanResponse and existing trip/request/controller/repository/service contracts; V21 migration and frozen booking party/date/budget storage; booking response mapping, purchase, cancellation and inventory restoration; plan-aware pricing/search/selection; compatibility aliases and retired replacement routes; new IndependentPlansWorkspace, PlanNavigation and PlanDialog; connected Profile/history/comparison/booking-confirmation/revision/rental consumers; API client and CSS; migration, ownership, contention, rollback, restart and frontend tests; fixture adapter; README and packaged release verifier. Existing old workspace remains a compatibility rendering branch; current server responses always use canonical plans.

Independent correctness review preceded plan comparison and edits. Traced owner authentication and CSRF to all new controllers, selected UUIDs to same-trip plans, parent/child CAS to transactions, primary replacement to FK and rollback, confirmed protections to booking snapshots, frozen metadata to purchase display/cutoff/restoration, and UI transitions through Profile and browser history. Reviewed assertions and isolation boundaries, not implementation receipts or completed checkboxes.

## Findings

No unresolved actionable findings after the fix and re-review below.

### [P3] Order profile results by the current primary plan's date

- Location: `src/main/java/app/detour/trip/JdbcTripRepository.java:80`.
- Scenario: Create two trips starting March 10. Save the first trip's primary to March 15. `/api/trips` projects March 15 from its canonical primary, but the original query continues ordering by the retained trip-row March 10 value and ID. Similarly, promoting a March 20 alternative leaves the obsolete trip-row ordering date behind.
- Impact: The existing chronological profile API ordering contradicts the dates it returns after a supported independent save or promotion. The normal My Trips cards independently sort the response, reducing visible impact there; that safeguard does not repair the supported API's existing order. This is a bounded compatibility regression, not a request to redesign My Trips sorting.
- Evidence: `loadTrip` projects primary dates and canonical saves/promotions no longer update `detour_trip.start_date`. The existing `TripApiIntegrationTest.profileProjectionPartitionsUpcomingAndPastWithDeterministicSort` asserts date/ID order, but uses only freshly created trips. New `IndependentTripPlanIntegrationTest.profileOrdersByCurrentPrimaryDatesAfterSaveAndPromotion` failed against unchanged production code at its first ordering assertion (1 test, 1 failure), then passed with the fix. It also exercises subsequent promotion reversing the order again.
- Fix: Read the canonical primary's start date in the profile query's ORDER BY, preserving trip-ID tie breaking and deterministic hydration locks. No source plan facts or trip metadata are rewritten.

## Findings Resolved in This Context

Changed only the profile ordering expression and added the HTTP regression above. The source query still filters ownership and hydrates only the trip IDs captured under deterministic parent locks; the primary FK guarantees same-trip membership. Internal re-review found no remaining actionable finding. These production/test changes require a fresh Step 5 context even though all gates pass.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1: exactly one primary, retain legacy facts | V21 conversion/checks/fallback and same-trip primary FK; canonical hydrate/create | V20 Working UUID/oldest option/empty fallback; V18 recovery; missing-night failure/source-retention fixtures | implemented |
| AC2–3: accessible top navigation and view-only selection | PlanNavigation manual keyboard activation, All plans selector, selected UUID and panel; canonical workspace branch | IndependentTripPlans tab ordering/ARIA/keyboard/overflow/no-mutation assertions | implemented |
| AC4: independent identity/name/date/party/selections | savePlan uses owned TripPlan, plan party/context, unconfirmed revalidation; rename changes only name | IndependentTripPlanIntegrationTest independent save/lifecycle; related component/pricing tests; new primary-date profile regression | implemented |
| AC5: independent create/copy without purchases | insertPlan new UUID; copy stored planning facts without booking rows; empty create | lifecycle test source equality/new IDs/no locks; frozen purchased-copy test; packaged lifecycle | implemented |
| AC6: dirty edits, failure/conflict/ownership | explicit Save and persisted baseline; three-action guard; scoped clean refresh; strict parser/principal ownership/CAS | deferred and late refresh tests, Save/Discard/Cancel, failed save, real Profile/logout/cross-trip history; stale and foreign requests | implemented |
| AC7: booked promotion/persistence | pointer-only transactional promotion; booking mapper resolves all canonical IDs | booked promotion/invariant and race tests; file-backed restart; packaged booked promotion/reopen | implemented |
| AC8: confirmed component protection and faithful facts | snapshot-derived slot locks; frozen purchase metadata; locked selections merged into plan save; purchased airfare count | purchased-flight and stay/rental tests, omission/removal and legacy-route rejection, latest-party tally/search test, UI locks/active and canceled confirmation | implemented |
| AC9: history/canceled-trip deletion restrictions | any booking row blocks plan/trip deletion; active-trip mutation guard; cancellation retains history | retained cancellation/API tests; canonical historical delete rejection; UI protected deletion explanation | implemented |
| AC10: replacement primary and atomic failures/races | distinct same-trip replacement, shared CAS, pointer before child removal in transaction | missing replacement/foreign/stale requests, injected delete rollback, promotion/promotion and promotion/delete contention | implemented |
| AC11: sole-plan trip deletion consent | explicit deleteTrip and expected count/version; existing history policy; transactional deleteTrip | exact UI prompt and Keep trip/no-write; sole consent/stale-count/failure rollback | implemented |
| AC12–13: distinct comparison, primary inclusion, names/status/partial totals | top chooser with 2–3 bounds/defaults, canonical alternatives; per-plan party/dates; missing and partial labels | primary-plus-one/alternatives-only/fourth-disabled tests; comparison desktop/mobile semantics and unavailable pricing | implemented |
| AC14: navigation and reopening | preserved UUIDs, canonical history/booking lookup, screenHistory; frozen confirmation context | historical confirmation and review-return tests, Profile navigation, trip/booking restart, packaged persistence/owner isolation | implemented |
| Shared destination/budget; preserved cancellation and no new provider workflow | explicit Trip settings, destination restrictions, existing simulated inventory/idempotency/cancel paths | full existing regression suite plus purchased-context tests | implemented |
| Legacy projection and retired copy/replace decision | primary drafts/workingPlan; other planned/savedOptions; all canonical plans; retired load/update error | deliberate API lifecycle replacements and current canonical UI tests | safe deviation from legacy write contract, authorized by governing plan |

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`. Full-profile persistence/lifecycle/concurrency triggers remain applicable and satisfied; no profile change or developer decision was needed.

## Open Questions and Assumptions

None requiring a developer decision. Retained the existing profile API start-date then ID ordering rather than adopting a new sorting policy. The UI's separate date/name sorting remains unchanged.

## Verification Results

Commands run sequentially on Windows, with no overlapping frontend native-module use during Maven npm ci/build.

- PASS — `npm --prefix frontend test` — 221 tests in 18 files. UI sources were unchanged by the subsequent backend-only ordering fix. Log: `%TEMP%/independent-review9-frontend.log`.
- PASS — `.\mvnw.cmd clean verify` — initial complete implementation: 219 Java tests, zero failures/errors/skips, frontend TypeScript/Vite build and executable JAR. Log: `%TEMP%/independent-review9-maven.log`.
- FAIL (intentional reproduction before production fix) — `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest#profileOrdersByCurrentPrimaryDatesAfterSaveAndPromotion" test` — 1 assertion failure demonstrating stale profile ordering. Log: `%TEMP%/independent-review9-red.log`.
- PASS — same focused command after fix — 1 test, both save and promotion paths. Log: `%TEMP%/independent-review9-green.log`.
- PASS — `.\mvnw.cmd clean verify` — final implementation: 220 Java tests, zero failures/errors/skips, frontend production build and refreshed packaged JAR. Log: `%TEMP%/independent-review9-final-maven.log`.
- PASS — `.\scripts\verify-packaged-release.ps1` — isolated temporary packaged server/database; public write protection, registration, canonical primary, independent dates/party, unconfirmed copy, booked promotion, confirmed locks, CSRF, idempotent booking, cancellation, restart, persisted snapshots/history and owner isolation. Log: `%TEMP%/independent-review9-packaged.log`.
- PASS — `git diff --check` — no whitespace errors.
- PASS — runtime source search `rg -n 'detour_trip_draft' src/main/java` returned no matches (exit 1 is expected for this absence check).

No live development database, deployment, supplier, payment or model service was used. The new regression exercises authenticated HTTP behavior, not merely SQL structure, and was actually red before the fix. The full migration/race/rollback suites remain executable evidence, while mocked DOM tests establish semantics and state behavior rather than pixel layout.

## Residual Risks and Optional Developer Checks

Optional narrow-screen/long-name visual inspection, actual screen-reader announcements, and desktop/mobile purchased-detail/comparison visual inspection were not performed. Automated DOM keyboard/focus/overflow-selector tests and production asset build pass; these observations are nonblocking supplements. V21 remains a forward migration requiring application/schema rollout together and database backup/recovery procedures documented in README. At-least-one-plan is enforced by service transactions and migration validation rather than a non-null database pointer alone. No unresolved ticket-attributable failure remains.

## Disposition

`fixes-applied`. Safe in-scope profile API regression fixed and independently reproduced; full internal re-review and required verification completed. Launch another fresh Step 5 reviewer for the complete ticket scope.
