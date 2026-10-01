# Manage independent trip plans Code Review — Cycle 5

## Scope and Repository State

Pipeline Step 5, selected profile `full` (Full 5-Step Pipeline). Reviewed against original HEAD `1ffcacd5cf204e09e2a3800322d79e6d379e4ff0` on `main`; the initial checkout was clean and the ticket implementation remains uncommitted. Scope included the complete tracked diff and the new, untracked implementation files, not just changes from a preceding cycle. No prior review documents were read.

Read the ticket, research, implementation plan, testing plan, command/protocol and design lens. Independently inspected canonical trip/plan persistence, V21 migration, parsers/controller/service/API contracts, booking execution and cancellation, snapshots/tallies, search/selection adapters, workspace/dialog/navigation, comparison, Profile/history/confirmation callers, styles, packaged verifier and connected tests. Reconstructed the old Working/option model from HEAD rather than relying on completion checkmarks. The initial defect, security, state, concurrency and UI review was completed before implementing fixes.

Risk analysis covered owner scoping and CSRF, malformed/versioned requests, trip/plan CAS and rollback, primary reachability, migrated working UUIDs and booked references, unknown traveler ages, confirmed and historical item protection, partial purchase contexts, refresh/restart, dirty navigation and late responses, cancellation history, and test doubles versus real JDBC integration. No new dependencies or live model/booking operations were required. Unchanged baseline issues were excluded.

## Findings

No unresolved actionable findings after fixes and internal re-review.

## Findings Resolved in This Context

### [P2] Keep parent and plan hydration consistent during primary deletion

- Location: `src/main/java/app/detour/trip/JdbcTripRepository.java:56` and `:67`; conflict classification at `src/main/java/app/detour/trip/TripService.java:152`.
- Scenario: A detail or Profile read obtains the parent row containing the old `primary_plan_id`. Before its separate child queries hydrate the plans, another transaction promotes a replacement and deletes the original primary. The committed database is valid, but the reader combines the old parent pointer with the new child set.
- Impact: `loadTrip` throws `IllegalStateException: Trip has no reachable primary plan`, producing a server error during supported concurrent plan management. The independent-primary deletion operation introduced this reachable failure; this is not a report of an unrelated legacy race.
- Evidence: Added a real H2 integration interleaving in `IndependentTripPlanIntegrationTest:85`. It pauses at parent mapping while an actual HTTP primary deletion runs. Before the repository fix, the focused regression failed with the exact unreachable-primary exception. Final coverage exercises both detail and Profile hydration; each reader sees a coherent original aggregate and the subsequent request sees the replacement primary.
- Fix: Hydrated repository reads hold only owned trip-row locks in a transaction until child hydration finishes. Profile locks trip IDs in deterministic order, then reads only that captured set so a concurrent creation cannot introduce an unlocked aggregate into the result. Catalog join rows are not locked. Plan actions reject stale aggregate versions before applying replacement-primary rules, preserving 409 conflict behavior after serialized competing deletions. Existing aggregate/plan CAS remains authoritative for writes.
- Re-review: Checked transaction callers, booking execution/cancellation, naming locks, response hydration, ownership parameters, rollback and resource cleanup. The Profile SQL builds only placeholder syntax and binds owner/IDs. Concurrent trip creation appears on the next refresh. A trial that locked joined catalog rows was rejected; a two-second regression pause exceeded H2's lock timeout and was reduced to 500 ms. Existing concurrency tests exposed a stale primary-deletion classification mismatch, fixed by the early version check. The final full suite passes these paths.
- Durable decision: The governing implementation plan records hydration locking, captured Profile IDs and conflict handling; no review conclusions were copied into ticket execution notes.

## Acceptance-Criteria and Plan Conformance

Every row below was checked against current code and test assertions, separately from defect review.

| Criterion/decision | Code evidence | Executable evidence | Result |
| --- | --- | --- | --- |
| 1. Exactly one primary; preserve existing data, including absent/incomplete Working | V21 canonical conversion, parent pointer, repository primary-first hydration | `TripModelForwardMigrationIntegrationTest` preserves Working UUID, absent Working fallback, booked references and rejects missing catalog nights without discarding source; fresh database tests | Implemented |
| 2. Top named navigation, narrow/keyboard/assistive access | `PlanNavigation`, `IndependentPlansWorkspace`, scroll/overflow styles, labeled tab and selector controls | `IndependentTripPlans.test.tsx` primary-first, named switch and keyboard overflow; focus assertions | Implemented; supplemental visual/assistive checks below |
| 3. Tab selection changes view only | Selected UUID state; canonical branch does not invoke Working-copy endpoints | UI switch assertions verify no mutation and unchanged names | Implemented |
| 4. Independent save/rename, identity and purchased detail preservation | `TripPlan`, per-plan traveler/date storage, save action; purchased component overlays and locked setters | Independent integration save/rename/isolation and purchased-flight context tests; UI selected identity/name assertions | Implemented |
| 5. Create/copy independent alternatives without purchases | Plan create/copy service and repository, selection copies without booking rows | Independent lifecycle/duplication/copy tests; packaged copy-without-purchases assertion | Implemented |
| 6. Dirty protection, visible failure/conflict, ownership | Save/discard/cancel dialog, in-flight transition guard, dirty refresh suppression, history/Profile/logout guard; request versions and owned plan lookup | UI dirty exits, failed/conflicting saves, late responses; Trip API/independent concurrency and owner tests | Implemented |
| 7. Booked promotion persists without reservation effects | Pointer promotion inside aggregate CAS transaction; booking records remain tied to plan | Independent identity/booked promotion and concurrency tests; restart/package assertions | Implemented |
| 8. Confirmed components locked, unconfirmed editable, purchased details faithful | Historical snapshot locks and immutable purchased date/party/budget fields; UI confirmed details | Independent partial bookings and frozen-flight/party tests; booking tests; packaged confirmed locks | Implemented |
| 9. Confirmed/history/cancelled deletion restrictions | Booking/history presence checks and active-trip requirements in plan/trip deletion | Independent locked deletion, booking cancellation and Trip API restrictions; packaged cancellation/history | Implemented |
| 10. Primary deletion needs replacement, atomic failure/conflict | Replacement validation, transactional pointer/delete; coherent hydration fix | Independent replacement, rollback, competing promotions/deletions and new detail/Profile hydration regressions; UI replacement confirmation | Implemented |
| 11. Sole-plan Keep/Delete trip and existing eligibility | Explicit confirmation/count requirements and existing trip deletion path | Independent sole consent/rollback tests; UI Keep makes no write; existing Trip API deletion restrictions | Implemented |
| 12. Nearby comparison, distinct 2–3 plans, initial selection | Canonical comparison entry and selected ID initialization | UI primary/alternative launch, alternatives-only selection and comparison tests | Implemented |
| 13. Comparison names/status/details and partial totals | `ItineraryComparisonView`, canonical mapping, tally completeness | Comparison and independent UI tests including partial zero totals | Implemented |
| 14. Existing navigation/booking details and persistence | Profile canonical adapters, screen history booking ID, confirmation restored from current data; durable canonical IDs | UI Back/reopen cancellation and selected purchase tests; trip/booking restart suites; packaged restart | Implemented |
| Material plan decisions: same destination and shared budget, independent dates/party, compatibility adapters, no reservation amendment | Service canonical context and immutable booking fields; old API adapters target canonical storage | Search/tally integration, original Trip API tests and packaged verifier | Implemented |

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`; placeholder examples were not treated as policy. Repository naming, H2 migration and isolated test conventions were retained.

## Open Questions and Assumptions

No developer decision remains. Supported flows use application-managed transactional mutation paths and the existing seeded catalog/fake booking implementation. Confirmed and historical components retain current reservation/deletion rules; no amendment or cleanup scope was added. Profile hydration intentionally returns the captured lock set when a new trip is created concurrently. That technical decision is recorded in the governing plan.

## Verification Results

Commands ran sequentially on Windows to avoid frontend native-module locks. Redirection below writes diagnostics to `$env:TEMP`; no live database or deployed application was used.

- PASS — `npm --prefix frontend test` — 217 tests across 18 files. Log `independent-review5-ui.log`.
- PASS — `.\mvnw.cmd clean verify` — initial independent verification: 217 Java tests, TypeScript/Vite production frontend build and executable JAR. Log `independent-review5-maven.log`.
- FAIL (expected red) — `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest#hydrationRemainsConsistentWhenPrimaryIsReplacedAndDeleted" test` — initial new regression reproduced unreachable primary before the fix. Log `independent-review5-red.log`.
- FAIL, then PASS after fixes — `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest,BookingConcurrencyIntegrationTest,TripApiIntegrationTest" test` — trial lock scope, test wait and stale-deletion conflict issues described above were corrected; final focused run passed 66 tests. Logs `independent-review5-focused.log`, `independent-review5-focused2.log`, `independent-review5-focused-final.log`, `independent-review5-focused3.log`.
- PASS — `.\mvnw.cmd clean verify` — intermediate 218-test build before the final Profile captured-ID refinement and second hydration variant. Log `independent-review5-maven-final.log`; not used as the final receipt.
- FAIL — `.\mvnw.cmd clean verify` — 219 tests, one failure in unchanged `BookingReferenceGeneratorTest.generatedReferencesHaveHighEntropyWithoutCollisions`: 9,999 distinct random references among 10,000. All ticket tests passed. Confirmed both the generator and test have no ticket diff; no unrelated implementation change made. Log `independent-review5-maven-release.log`.
- PASS — `.\mvnw.cmd clean verify` — final exact source: 219 Java tests, zero failures/errors/skips, TypeScript/Vite production build and packaged JAR; 1:34 elapsed. Log `independent-review5-maven-release2.log`. This complete rerun passed the probabilistic baseline test as well.
- PASS — `.\scripts\verify-packaged-release.ps1` — isolated packaged JAR verifies public write protection, registration, canonical primary, independent dates/party, copy without purchases, booked promotion, confirmed locks, CSRF, idempotent booking, cancellation, restart, persisted snapshots/history and owner isolation. It starts its own loopback process with temporary H2 storage, removes credentials from the process environment, and cleans up the owned process/storage. Log `independent-review5-packaged.log`.
- PASS — `git diff --check` — final patch whitespace verification.
- NOT RUN — live deployment/database migration — excluded from routine review; forward migration and packaged restart used isolated H2 fixtures instead.

## Residual Risks and Optional Developer Checks

- Supplemental browser checks at 320 px with long plan names and 3-column comparison, plus screen-reader announcement and keyboard walkthrough, were not performed. DOM/interaction tests establish labels, reachability, focus trapping/restoration and keyboard switching; visual wrapping and assistive output remain optional observations.
- Supplemental desktop/mobile review of confirmed purchased details beside changed planning details was not performed; executable assertions and immutable server snapshots cover correctness.
- The unchanged six-character random-reference collision test is probabilistic. Its failed attempt is preserved above rather than hidden; the final full gate passed. This is an unrelated baseline observation, not a ticket finding.
- Trip-row locks serialize supported same-trip reads/mutations during hydration. No catalog row locking or production database operation was introduced by this review.

## Disposition

`fixes-applied`. One concrete concurrency finding was resolved with production changes and regression tests; the governing plan also changed. Full internal re-review found no remaining actionable ticket findings, and final required verification passed. A fresh independent Step 5 context must review the complete current change; this cycle cannot claim `clean`.
