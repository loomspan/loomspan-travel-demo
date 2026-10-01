# Independent Trip Plans Code Review — Cycle 2

## Scope and Repository State

Pipeline Step 5, confirmed profile `full`. Reviewed the ticket, research, implementation plan, testing plan, review command, shared automation protocol, and design lens completely. No active design guardrails or applicable AGENTS.md were found. Prior review documents were not read.

Comparison base is original HEAD `1ffcacd5cf204e09e2a3800322d79e6d379e4ff0` on `main`. The orchestrator established that the initial checkout was clean and all implementation work belongs to this ticket. Staged diff was empty. Reconstructed the entire unstaged tracked diff and explicitly inventoried/read the untracked implementation: canonical TripPlan/PlanResponse, V21 migration, independent workspace/navigation/dialog, independent Java/UI tests and canonical test fixture adapter. Research/plans/ticket documentation were included; review records were excluded from implementation evidence.

Production scope covers canonical plan persistence and response projections, independent dates/party/search/selection/lifecycle, primary promotion/deletion, purchased context and cancellation, top navigation/comparison/dirty/history transitions, profile consumers, release smoke and README. Inspected changed code in context and connected booking, search, pricing, profile, screen history and test callers beyond diff hunks. Completed the initial independent review before modifying implementation artifacts.

## Findings

No actionable findings remain after the fixes and internal re-review below.

## Findings Resolved in This Context

### [P2] Subtract purchased airfare using its frozen party in component searches

- Location: `src/main/java/app/detour/trip/TripService.java:1081` and `:1207`.
- Scenario: Purchase two travelers' airfare, change that plan to three travelers, then search an unconfirmed stay or rental.
- Impact: Search budgets used the new party to multiply already purchased airfare, conflicting with the protected purchase and displayed plan tally. In the seeded reproduction, remaining budget should be 426800 cents but was 390200 cents. Budget-fit labels and search sorting could therefore mislead users.
- Evidence: `searchesAndTalliesUseLatestPurchasedAirfareParty` independently reproduced the exact expected/actual budget mismatch through the canonical authenticated HTTP route. The first attempted test lacked required `type=HOTEL` and failed validation; that fixture mistake was corrected before the meaningful red run.
- Fix: Reuse a purchased airfare party helper in plan tally and both search budget calculations. Unconfirmed airfare still uses that plan's party. The test checks stay and rental budgets separately while plan party remains three.

### [P2] Use the latest purchase when projecting a plan's airfare total

- Location: `src/main/java/app/detour/trip/TripService.java:773`, `src/main/java/app/detour/booking/JdbcBookingRepository.java:291` and `:304`.
- Scenario: The existing API cancellation/purchase lifecycle buys the same airfare plan with two travelers, then later buys it with three after a planning edit.
- Impact: The old loop traversed newest-to-oldest history and overwrote the newer party with the older one, understating the current plan airfare total even while its latest purchase correctly showed three travelers.
- Evidence: The added HTTP regression performs cancellation and a second permitted purchase, then compares latest booking airfare total with the canonical plan tally. This exercises the existing purchase lifecycle; it adds no reservation amendment or new rebooking feature.
- Fix: Stop at the newest matching airfare purchase. Add internal-ID tie breakers to booking history and active/latest projection ordering so equal timestamps do not reverse the authoritative purchase. The regression passes with the latest three-person purchase and old history retained.

### [P2] Restore the purchase selected by confirmation history

- Location: `frontend/src/components/IndependentPlansWorkspace.tsx:23`, `:48`, `:132`; `frontend/src/screenHistory.ts:9`.
- Scenario: View an older alternative's booking, view another plan's newer booking, then use Back or reopen the saved confirmation history entry. Previously only the view/plan was recorded; the confirmation state remained the newer purchase or initialized from `trip.booking`.
- Impact: The confirmation showed a different booking reference and purchased details from the record the user navigated to.
- Evidence: `restores the viewed purchase on Back and after reopening confirmation history` failed with the older reference absent after Back. It now verifies Back, remount, and restoration after that plan's latest purchase changes.
- Fix: Record `confirmationBookingId` on purchase-detail and successful booking navigation. Resolve that exact purchase on restoration. For older records absent from the summary projection, fetch the owner-scoped existing booking history, suppress stale async responses on navigation/unmount, and show loading/error instead of a different purchase. Preserve compatibility fallback for older entries without the new field and the local successful confirmation if a refresh has not completed.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1 one primary and existing data preservation | V21 Working conversion, oldest-option/empty fallback, same-trip pointer; canonical loader | V18/V20/fresh migration, missing-night source preservation/restart, create/lifecycle invariants | implemented |
| AC2 top named accessible navigation | PlanNavigation primary-first tabs, All plans, keyboard/manual activation, panel linkage and overflow CSS | IndependentTripPlans keyboard/overflow/tab semantics | implemented |
| AC3 selection only changes view | UUID-selected independent workspace; retired load/replace writes | zero mutation navigation assertions; retired routes preserve data | implemented |
| AC4 independent save/rename and frozen purchase | plan party tables, owner-scoped save/name, purchased locks/context | independent sibling/detail assertions, airfare/stay/rental locks, purchased response equality | implemented |
| AC5 independent create/copy | new UUID snapshots and party; no copied booking rows | empty create, source equality, booked-source unconfirmed copy, packaged lifecycle | implemented |
| AC6 dirty/failure/conflict/ownership | explicit save/baseline, guard/busy refs, parent/history guard; aggregate/child CAS | Save/Discard/Cancel, conflict retention, deferred/late responses, actual Profile/history exits; foreign/stale/rejected payload integration | implemented |
| AC7 booked primary promotion | single pointer and aggregate/source version check | booked promotion/inventory equality, concurrent one-winner promotion, restart | implemented |
| AC8 confirmed components accurately protected | booking snapshots, frozen dates/party/budget, all component route guards | canonical/legacy tampering, missing unconfirmed additions, cutoff/seat restoration, purchased UI and new search-budget/latest-purchase regression | implemented |
| AC9 deletion/history/canceled restrictions | booking-history deletion guards and active-trip checks; unchanged cancellation workflows | active/canceled history and canceled-trip integration; canceled badges/locks | implemented |
| AC10 replacement primary atomicity | replacement membership checks, transactional pointer/delete and CAS | missing/foreign/stale replacement, injected rollback, promotion/delete and booking/delete races | implemented |
| AC11 sole-plan trip deletion | explicit consent/count/version checks and history protection | exact Keep/Delete UI, no-write cancel, injected sole-delete rollback | implemented |
| AC12 2–3 distinct plan comparison | top chooser, primary/alternative defaults, bounds and alternatives-only | chooser defaults/selection bounds/comparison regressions | implemented |
| AC13 names/status/dates/prices/partial | canonical comparison props and explicit missing/partial totals | names/Primary/Booked/date/party, Not selected, partial zero, absent tally | implemented |
| AC14 navigation and persisted edits/primary/purchases | preserved UUIDs/projections, profile/history, frozen response mapper | trip/booking file-H2 restart, packaged reopen, new exact confirmation history regression | implemented |
| Trip organization and compatibility | destination/budget remain explicit shared settings; aliases project primary/alternatives; archived drafts have no runtime writes | shared detail/search regressions, source scan, canonical/legacy route tests | implemented |
| Simulated purchase and no amendment/incremental purchase | existing one-active-booking, idempotency, deterministic inventory transaction retained | inventory/idempotency/cancellation races and rollback tests | implemented |

## Active Project Guardrails

None recorded. Full profile remains appropriate because this change affects serialized persistence, migrations, concurrency and booking lifecycle. No route downgrade or further developer decision was needed.

## Open Questions and Assumptions

None affecting completion. Synthetic isolated databases establish migration and mutation behavior; no claim is made about the contents or distribution of a live database.

## Verification Results

- PASS — `npm --prefix frontend test` — initial independent run: 213 tests in 18 files; final run after all fixes: 214 tests in 18 files. Final log `%TEMP%/review2-frontend-complete.log`.
- PASS — `.\mvnw.cmd clean verify` — initial independent run: 216 Java tests, production frontend build and JAR. Initial log `%TEMP%/review2-verify.log`.
- PASS — `.\mvnw.cmd clean verify` — final run: 217 Java tests, zero failures/errors/skips, TypeScript/Vite production assets and packaged JAR. Final log `%TEMP%/review2-verify-final.log`.
- PASS — `.\scripts\verify-packaged-release.ps1` — final packaged JAR, disposable server/database, independent lifecycle, owner isolation/CSRF, copy, booked promotion/locks, idempotency, frozen purchased facts, cancellation/history and restart. Log `%TEMP%/review2-packaged.log`; verifier exited successfully and cleaned up its temporary server/files.
- FAIL, expected red — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'restores the viewed purchase'` — older reference absent after Back before the fix.
- PASS — same focused frontend command after fix; `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx` — all 22 canonical workspace tests after extension to older history lookup.
- FAIL, corrected test fixture — `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest#searchesAndTalliesUseLatestPurchasedAirfareParty" test` — initial missing `type=HOTEL` query returned validation 400.
- FAIL, expected red — same focused backend command with corrected fixture and pre-fix budget calculation — expected 426800, actual 390200 cents.
- PASS — same focused backend command after fixes — exact stay/rental budgets and latest purchase tally pass.
- PASS — `git diff --check` — no whitespace errors.

All frontend and Maven gates were run sequentially on Windows. Test receipts/checkmarks from earlier stages were not accepted as evidence; commands above were run in this context. The final complete diff was re-reviewed after the fixes, including canonical and legacy route safeguards, purchase/history mapping, async cleanup and dirty transitions. No implementation edits followed the final gates.

## Residual Risks and Optional Developer Checks

Optional narrow viewport/long-name clipping, visible focus, screen-reader announcement, and desktop/mobile purchased-detail/comparison visual observations were not performed. Automated DOM/keyboard/focus/navigation and HTTP/SQL evidence covers the required behaviors; these remain nonblocking observations. No live development database, account, supplier, payment, deployment or model call was used. V21 retains source tables and explicitly fails incomplete snapshot conversion; H2 DDL is not assumed transactional. Real deployment still requires the documented backup and coordinated schema/application upgrade.

## Disposition

`fixes-applied`. This context changed implementation and regression tests and therefore cannot certify its own fixes as a fresh `clean` review. Internal re-review found no remaining actionable findings; a new independent Step 5 context must review the final repository.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-01-manage-independent-trip-plans-review-2.md
  - src/main/java/app/detour/trip/TripService.java
  - src/main/java/app/detour/booking/JdbcBookingRepository.java
  - src/test/java/app/detour/trip/IndependentTripPlanIntegrationTest.java
  - frontend/src/components/IndependentPlansWorkspace.tsx
  - frontend/src/screenHistory.ts
  - frontend/src/IndependentTripPlans.test.tsx
SUMMARY: Completed an independent review of the entire current ticket change. Fixed frozen airfare search budgets/latest-purchase tally and exact booking-confirmation history restoration, with reproductions and regressions. Internal re-review has no remaining actionable findings; all final gates pass.
DECISIONS:
  - Resolve confirmation history by booking UUID and use the existing owner-scoped history API when the summary no longer contains that record.
  - Use the newest matching purchased airfare party consistently in tally and unconfirmed component search budgets, preserving the existing cancellation/purchase lifecycle.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `npm --prefix frontend test`: final 214 tests in 18 files.
  - PASS — `.\mvnw.cmd clean verify`: final 217 Java tests and production frontend/JAR.
  - PASS — `.\scripts\verify-packaged-release.ps1`: isolated packaged lifecycle/restart.
  - PASS — `git diff --check`.
  - PASS — focused confirmation and purchased-party regressions after their documented red runs.
OPTIONAL_DEVELOPER_CHECKS:
  - Narrow-screen/long-name visual focus and screen-reader announcements; desktop/mobile comparison and purchased-detail inspection remain unperformed nonblocking observations.
REVIEW_RESULT: fixes-applied
NEXT: Launch fresh independent Step 5 review cycle 3 against the complete current change without prior review documents.
