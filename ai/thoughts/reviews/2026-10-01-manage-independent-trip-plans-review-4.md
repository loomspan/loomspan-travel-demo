# Manage independent trip plans Code Review — Cycle 4

## Scope and Repository State

Independent Step 5 under the developer-confirmed Full 5-Step Pipeline (`full`). Comparison base is original clean checkout HEAD `1ffcacd5cf204e09e2a3800322d79e6d379e4ff0` on main. Reviewed the whole ticket implementation against that base, including unstaged tracked modifications and untracked canonical models, V21 migration, workspace/navigation/dialog components, tests, and governing documentation. There were no staged changes. Prior review documents were not read.

Read the ticket, research, implementation plan, testing plan, review command, shared automation protocol and design lens. Reconstructed actual behavior before assessing plan conformance. The initial defect review covered canonical and legacy routes, repository hydration and writes, response projections, profile consumers, comparison, browser navigation, booking/cancellation inventory, migration failure/restart behavior, and tests beyond changed hunks. Implementation editing began after this initial independent review; the new regression test was then used to reproduce the remaining candidate.

## Findings

No unresolved actionable findings after fixes and re-review.

### [P2] Reconcile the viewed confirmation after cancellation
- Location: `frontend/src/components/IndependentPlansWorkspace.tsx:78` (response application), and `:134` (history restoration).
- Scenario: View an ACTIVE plan purchase, return to the workspace, successfully cancel it, then navigate back to the previously viewed confirmation history entry.
- Impact: The new workspace retained the old ACTIVE confirmation object. History restoration preferred this cached object when its ID matched the requested entry, displaying “Booking Confirmed!” after cancellation had succeeded.
- Evidence: The new DOM interaction test `restores canceled status when returning to a previously viewed confirmation` failed on the reviewed implementation because “Booking Canceled” was absent. It exercised the real cancellation modal and history restoration, with a successful canceled server response. Existing tests covered different purchase IDs and reopening but did not combine cancellation with a cached confirmation.
- Fix: Reconcile the cached confirmation against the same booking ID in every successfully applied trip response. Preserve the existing cached historical record only when the response does not contain that ID. This neither substitutes another booking nor changes purchases.

### [P3] Replace obsolete instructions in reachable plan dialogs
- Location: `frontend/src/components/CancelBookingModal.tsx:135`, `frontend/src/components/CancelTripModal.tsx:131`, and `frontend/src/components/TripRevisionModal.tsx:291`.
- Scenario: Open cancellation or duplicate-trip dialogs from the canonical independent-plan workspace.
- Impact: Cancellation explicitly directed the user to open a Saved option in Working, a workflow retired by this ticket. The duplication dialog described canonical source plans as Saved options and described the new empty primary as a separate Working plan.
- Evidence: These unchanged shared dialogs are directly imported and rendered by `IndependentPlansWorkspace`; canonical duplication supplies all plans including the primary as source choices. The normal editor exposes no Working copy/replace action.
- Fix: Describe editing unconfirmed parts of any plan, all plans becoming read-only on trip cancellation, and copying named plans into a new trip with a separate empty primary. Backend behavior and cancellation eligibility remain unchanged.

## Findings Resolved in This Context

Both findings above are resolved. Changed only the new confirmation response reconciliation, its focused regression test, and user-facing text in the three shared dialogs. Re-reviewed the complete ticket change and connected consumers after fixes. No backend, migration, primary-pointer, booking, or inventory behavior was altered by this review.

## Independent Risk Review

- **Identity/persistence:** V21 preserves Working public UUIDs, existing option IDs/names/snapshots and booking links. Working becomes primary; absent Working uses oldest existing option, with empty-plan fallback. Archived draft tables have no runtime references. Canonical plan party is loaded separately; primary response projections do not propagate edits to siblings.
- **Lifecycle/concurrency:** Canonical mutation methods are transactional; owner-scoped aggregate CAS and child versions serialize save, rename, promotion, deletion and booking. Copy/promotion preserve source child facts. Primary replacement precedes deletion inside one transaction; sole-plan deletion checks explicit trip consent, history, counts and versions. Same-trip composite FK constrains pointer membership. Nullable primary is a transaction-time implementation detail, not a database guarantee of a nonempty trip.
- **Confirmed/history protection:** Component locks derive from booking snapshots across retained history. Save payload omission/replacement, legacy component mutation routes and canonical routes cannot remove protected components. Purchased dates/party/budget are frozen on migration and purchase; cancellation cutoff and seat restoration use those facts. Booking response construction is shared. Copy has no purchase associations; purchase rejects copied components inconsistent with planning dates.
- **Authorization/privacy:** Controllers derive owners from the authenticated principal; UUID children are resolved inside that owner's trip. Strict parsers reject writable owner/primary/purchase fields; existing CSRF applies to writes. No added credential/payload logging, external suppliers, payment calls or real-data operations were found.
- **UI/async lifecycle:** Explicit per-plan details Save, persisted baseline, dirty navigation guard, busy serialization and version-scoped clean refresh prevent silent discard and stale refresh overwrite. Accessible tabs use manual activation/roving focus plus an overflow selector. Confirmation history retains booking identity and now reconciles cancellation status. Async history loads suppress results after cleanup.
- **Test quality:** HTTP tests assert persisted sibling facts, stable identity, primary reachability, foreign/stale requests, rollback, races and booking snapshots. Migration tests include V18/V20 lineage, absent Working, unknown ages and failed-night conversion with source retention. File-H2 restart and packaged HTTP tests exercise persistence without mocked repositories. DOM tests assert API targets, dialog decisions, pending/late responses, parent history and keyboard semantics; the added regression was independently red before the fix.
- **Rejected candidates:** Legacy trip DELETE's preexisting version-check/unconditional-delete sequence was compared with HEAD and excluded as unchanged baseline behavior, rather than expanding this ticket. Repository list ordering by old trip dates is compensated by current My Trips client sorting using projected primary dates, so it is not an observable UI regression.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1 one primary and legacy preservation | V21; JdbcTripRepository.loadTrip/createAggregate | TripModelForwardMigrationIntegrationTest; IndependentTripPlanIntegrationTest invariant | implemented |
| AC2 accessible top named navigation | PlanNavigation; canonical workspace; overflow CSS | IndependentTripPlans keyboard/overflow/tab-panel test | implemented |
| AC3 view-only switching | selected UUID/history; retired load/update routes | zero-mutation tab test; retiresCopyAndReplaceWithoutAnyWrites | implemented |
| AC4 independent edits/save/rename | plan save/name; plan traveler storage; plan-context search | independent-save/lifecycle tests; component selection suites | implemented |
| AC5 independent create/copy | insertPlan with new UUID; no booking rows | createCopyRenamePromoteAndDelete; purchased copy assertions; packaged smoke | implemented |
| AC6 dirty/conflict/failure/ownership | three-action guard; busy refs; owner/aggregate/child checks | failed-save, dirty-action, parent/history and refresh-race DOM tests; owner/stale HTTP assertions | implemented |
| AC7 booked promotion persistence | setPrimary transaction; canonical booking ID lookup | booked promotion HTTP/UI; restart; packaged smoke | implemented |
| AC8 faithful locked purchases | purchased helper; frozen metadata; shared mapper | frozen airfare/stay/rental tests, cutoff/restoration, DOM purchased-context assertions | implemented |
| AC9 history/canceled protections | active-trip/history checks; shared component locks | booking cancellation suites; retained historical delete/edit rejection | implemented |
| AC10 replacement atomicity | replacement validation; CAS; transactional pointer/delete | injected post-pointer failure; synchronized promotion/delete tests; replacement DOM test | implemented |
| AC11 sole eligible plan deletes trip | explicit deleteTrip/count/version/history checks | sole consent/rollback HTTP test; exact Keep trip UI | implemented |
| AC12 compare 2–3 distinct plans | top chooser; primary/alternative launch defaults | alternative launch, alternatives-only, fourth-disabled UI; comparison suite | implemented |
| AC13 named/status/context/partial totals | comparison per-plan data; partial tally/Not selected | comparison regression suite; canonical partial-zero fixture | implemented |
| AC14 navigation/history/reopen | preserved UUIDs; screenHistory; confirmation reconciliation | profile/booking history DOM tests including new cancellation regression; trip/booking restart; packaged verifier | implemented |
| Shared destination/budget and retired replacement writes | explicit Trip settings; plan workflow retirement | budget/search pricing and legacy route regression suites | implemented |
| Full-profile rigor | independent full-scope reconstruction, defect review, red/green regression and sequential gates | commands below | implemented |

## Active Project Guardrails

None recorded. Design lens is explicitly a placeholder; its examples were not treated as policy.

## Open Questions and Assumptions

None requiring a developer decision. Ordinary fixes remain within the confirmed full profile. Historical immutable purchase components remain protected after cancellation, as explicitly required. Legacy response rendering is the documented compatibility branch; server V21 responses always use canonical plans.

## Verification Results

- PASS — `npm --prefix frontend test` — initial reviewed implementation: 216 tests/18 files. Final implementation (after confirmation regression and all dialog updates): 217 tests/18 files, zero failures. Final receipt: `%TEMP%/review4-final-frontend2.log`.
- FAIL (expected pre-fix reproduction) — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'restores canceled status'` — one regression failed at the canceled-heading assertion; the restored view still displayed confirmed status. Receipt: `%TEMP%/review4-red.log`.
- PASS — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'restores canceled status'` — same regression passed after reconciliation; 24 other tests intentionally filtered. Receipt: `%TEMP%/review4-green.log`.
- PASS — `.\\mvnw.cmd clean verify` — initial and final sequential runs each passed 217 Java tests, zero failures/errors/skips; npm ci, TypeScript/Vite production build and packaged JAR succeeded. Final receipt: `%TEMP%/review4-final-maven.log`. Expected migration-failure fixture log messages are assertions exercised by passing tests, not unhandled gate failures.
- PASS — `.\\scripts\\verify-packaged-release.ps1` — latest packaged JAR: authentication/public write protection, owner isolation, CSRF, independent plans/party/dates, copy without purchases, booked promotion, component locks, idempotent purchase, frozen purchased context, cancellation/history, restart. The verifier owned its disposable server/database and cleaned them up. Receipt: `%TEMP%/review4-packaged.log`.
- PASS — `git diff --check` — no whitespace errors.
- PASS — source inspection with `rg -n 'detour_trip_draft' src/main/java` — no runtime archived-draft references (rg no-match status is expected).

Frontend and Maven/native-module operations ran sequentially. The original comparison base was obtained using `git rev-parse HEAD`; staged/unstaged/untracked scope was established through Git status/diffs and explicit reads of new files. No stored implementation receipts were treated as proof.

## Residual Risks and Optional Developer Checks

No deployment, live DB migration, development DB access, real account, supplier or payment operation was performed. Migration rollback/recovery still requires the documented backup/application pairing: H2 DDL may commit before a failed forward migration, and archived draft rows are diagnostic evidence rather than a supported downgrade path.

Optional approximately 320px long-name/overflow visual inspection, screen-reader announcements, and desktop/mobile purchased-details comparison remain NOT PERFORMED. DOM semantics, keyboard/focus, authoritative backend behavior and release tests supply executable evidence; these observations are supplementary.

## Disposition

`fixes-applied`: implementation artifacts changed in this context; the orchestrator must launch a fresh independent Step 5 review. No open actionable findings remain after internal re-review and sufficient verification.

