# Manage independent trip plans Code Review — Cycle 7

## Scope and Repository State

Independent pipeline Step 5 under the developer-confirmed Full 5-Step Pipeline (`full`). Comparison base is original clean-checkout HEAD `1ffcacd5cf204e09e2a3800322d79e6d379e4ff0`, still the current HEAD. This is the complete ticket change, not a comparison against another review's edits. No prior review document was read.

Read the ticket, research, implementation plan, testing plan, code-review command, automation protocol and design lens. No repository/ancestor AGENTS.md or active design guardrail was found. `git diff --cached --stat` is empty. The original-HEAD inventory includes 45 modified tracked files and nine untracked production/test files, plus governing research/plans and review audit artifacts. Nothing in the implementation inventory was excluded as unrelated.

Production scope: V21 migration; canonical TripPlan/PlanResponse; trip model, repository, service, strict requests and controller; tally completeness; booking record/response/repository/service/transaction executor; canonical workspace, tab/dialog components, Profile navigation, history, API client, comparison, purchased confirmation, selection locks, cancellation text, duplication text and CSS. Reviewed README and packaged verifier, migration/restart/API/component/booking tests, the new independent-plan suites and canonical fixture helper. Dependencies and existing migrations V1–V20 are unchanged. Prior audit documents are outside the source review and were not inspected.

## Findings

No unresolved actionable findings after the fix and re-review. One introduced P2 was found and resolved in this context.

### [P2] Resolve the current persisted baseline after Save-and-continue

- Location: `frontend/src/components/IndependentPlansWorkspace.tsx:213`.
- Scenario: Change Departure date from March 10 to March 11, click **Discard edits**, then select **Save** in the three-action dialog. The save succeeds, but the queued discard action used the `plan` object captured before saving.
- Impact: The editor immediately displayed March 10 again while the server had saved March 11, and marked the editor dirty. A subsequent Save could revert the successful save. This breaks truthful save/discard behavior without affecting reservation data.
- Evidence: Added `keeps the saved baseline when Save is chosen after Discard edits` at `frontend/src/IndependentTripPlans.test.tsx:27`. Before the fix its observable DOM assertion failed: expected `2027-03-11`, received `2027-03-10`. Existing tests exercised Save when switching/promoting, but did not exercise this queued same-plan action.
- Fix: Resolve the selected plan from `tripRef.current` and `selectedRef.current` when the discard action actually executes. The current ref is updated synchronously by `apply(fresh)` before save continuation runs. Direct Discard still resolves the unchanged persisted baseline; Cancel remains a no-op.

## Findings Resolved in This Context

Changed only `frontend/src/components/IndependentPlansWorkspace.tsx` and `frontend/src/IndependentTripPlans.test.tsx` as implementation artifacts. The regression asserts one save, the saved date remaining displayed, a clean workspace handle and a disabled Save plan button. Full frontend verification passes with this case. Re-traced Save failure, pending mutation, tab switching, primary actions, reload, component mutations and parent/history guards after the fix; no additional actionable findings remain.

## Independent Risk Review

- **Correctness/persistence:** Canonical rows retain public IDs and names. Saves target one child and rewrite only its party/snapshots; trip context is projected from the selected plan. One pointer plus same-trip FK prevents multiple/foreign primaries. Creation, promotion, replacement deletion and sole-trip deletion run transactionally; rollback and synchronized races assert reachable surviving primaries. Archived draft tables are not runtime read/write targets.
- **Compatibility/migration:** V21 uses Working UUID as primary, preserves existing options/bookings, falls back to oldest option when Working is absent and creates an empty primary only when needed. Snapshot conversion checks component and stay-night counts. Synthetic V18/V20 lineage and failure/restart tests establish preservation and diagnostic failure; H2 DDL rollback is not falsely assumed.
- **Security/privacy:** Controller derives owner from the authenticated principal. Child lookup is inside the owned aggregate; replacement ownership is checked. Request allowlists reject client-written primary, owner, confirmed and booking-association fields. Existing session/CSRF middleware protects the new routes. API and packaged tests exercise foreign-owner 404, unauthenticated writes and missing-CSRF rejection. Changed logging/docs/fixtures contain no live credentials or user data.
- **Concurrency/lifecycle:** Aggregate CAS and child versions reject stale saves/actions; source copy/promotion do not rewrite source child facts. Booking advances aggregate version before dependent purchase/inventory writes; cancellation remains transactional. Repository hydration holds the owned parent lock through child reads, and profile hydration captures locked IDs deterministically. Injected rollback, contention, idempotency and restart assertions cover these boundaries.
- **Purchased state:** Locks derive from booking snapshots/history and apply to canonical and legacy component routes. Frozen purchased dates/count/ages/budget govern booking responses, cancellation cutoff and seat restoration. Plan airfare tally uses the latest purchased count where locked; unconfirmed selections use plan context. Copy creates no booking rows/locks, and invalid copied dates are rejected before purchase. History continues to prevent deletion.
- **UI/state:** Primary-first labeled tabs provide manual keyboard activation and All plans overflow selection. Clean selection changes view/history only. Pending mutations block dependent transitions; dirty transitions offer Save/Discard/Cancel, and errors retain edits. Late refresh tests protect newer input/saves. Confirmation history records the actual purchase ID and reloads historical details when required. Comparison includes canonical primary and alternatives, limits selection to two/three distinct IDs and labels missing/partial totals.
- **Test quality/operations:** HTTP tests assert persisted rows, UUIDs, versions, snapshot equality and ownership rather than just mocked service calls. Migration tests use real old schemas, and race/rollback tests exercise transactions. UI/client mocks cover request shape and observable DOM state; legacy rendering tests supplement the canonical suite rather than establishing canonical behavior alone. Release verifier owns its temporary process/DB and cleans them up. No supplier, payment, live DB or deployment operation was used.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Executable test evidence | Result |
| --- | --- | --- | --- |
| AC1 one primary; legacy data retained | V21; canonical hydration/create | V18/V20 migration fixtures, absent Working, empty trip, UUID/history assertions | implemented |
| AC2 accessible top named navigation | PlanNavigation; workspace/CSS | Canonical primary-first, ARIA relationships, keyboard manual activation and overflow test | implemented |
| AC3 view-only switching | switchPlan; selectedPlanId/history | No load/save/promotion requests during switching; retired copy/replace routes no-write assertions | implemented |
| AC4 independent save/rename/details | savePlan, plan party/snapshots, name action | Independent save, sibling/source identity checks, component search suites, frozen purchase tests | implemented |
| AC5 independent Create/Copy | insertPlan; copy action | New UUID/no locks or booking associations; packaged copy assertions | implemented |
| AC6 unsaved/failure/conflict/owner protection | guard/save/current baseline; CAS/principal | Save/Discard/Cancel, pending/late responses, new discard-save regression, 404/409 tests | implemented |
| AC7 booked primary promotion retained | pointer action; canonical booking lookup | Booked promotion and unchanged purchase/inventory assertions; restart/release tests | implemented |
| AC8 locked confirmed parts; accurate context | purchased helper; frozen booking mapper; locked controls | Frozen airfare/stay/rental, tampering/legacy removal rejection, purchased UI context | implemented |
| AC9 history/canceled deletion restrictions | history checks; active-trip policy | Booking cancellation/history and protected deletion integration tests | implemented |
| AC10 replacement primary atomicity | planAction replacement and transaction | Missing/self/foreign replacement, pointer rollback, same-version races; UI replacement consent | implemented |
| AC11 sole-plan trip deletion | explicit deleteTrip/count/version policy | Missing consent, injected rollback, successful 204, exact Keep/Delete prompt | implemented |
| AC12 top Compare plans; 2–3 distinct | chooser and canonical comparison | Primary-plus-one launch, alternatives-only, fourth disabled; comparison suite | implemented |
| AC13 names/status/dates/selections/partial prices | comparison; per-plan tally | Named/primary/booked/dates/party, Not selected, Partial total, unavailable price assertions | implemented |
| AC14 links/reopen/history/persistence | preserved IDs; Profile/history; canonical booking mapper | Real Profile/history, trip and booking file-H2 restart, packaged restart/ownership | implemented |
| Shared destination/budget; retired editor | explicit Trip settings; canonical/legacy projections | API shared-settings and retirement tests; canonical client request shapes | implemented |
| Forward-only schema and isolated verification | V21; README; verifier temporary DB/process | Migration failure-preservation and packaged lifecycle/restart | implemented |

The retained legacy workspace branch is the recorded compatibility decision; real V21 responses select the canonical branch. Trip summary ordering in the UI sorts projected primary dates, so the repository's legacy SQL order does not misorder displayed My Trips. No full-profile reassessment or product decision is unresolved.

## Active Project Guardrails

None recorded. The actual authenticated principal, strict payload, transactional state and protected booking/history requirements were independently checked.

## Open Questions and Assumptions

None affecting correctness. The supplied original-clean-checkout attribution is consistent with HEAD and the ticket's complete modified/untracked inventory.

## Verification Results

- PASS — `npm --prefix frontend test` before editing: 218 tests in 18 files.
- FAIL (expected reproduction, resolved) — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'keeps the saved baseline'`: new case failed expected March 11/actual March 10 before the production fix.
- PASS — `npm --prefix frontend test` after the fix: 219 tests in 18 files, including the new regression.
- PASS — `.\mvnw.cmd clean verify`: 219 backend tests; zero failures/errors/skips; TypeScript/Vite production build and packaged JAR succeeded.
- PASS — `.\scripts\verify-packaged-release.ps1`: canonical identity/party/dates/copy/promotion, purchased locks/context, authentication/CSRF, idempotent purchase, cancellation/history, restart and owner isolation. Its temporary server/DB were cleaned up.
- PASS — `git diff --check`.
- NOT RUN — Optional narrow-viewport visual/screen-reader inspection and desktop/mobile visual purchase comparison. DOM accessibility/navigation and executable state tests provide the required automated evidence; these remain supplementary observations.

Fresh logs: `%TEMP%/review7-frontend.log`, `%TEMP%/review7-red.log`, `%TEMP%/review7-frontend-final.log`, `%TEMP%/review7-maven.log`, `%TEMP%/review7-packaged.log`. Frontend and Maven gates ran sequentially to avoid Windows native-module locks.

## Residual Risks and Optional Developer Checks

Optional visual/screen-reader checks remain unperformed. No live database upgrade or deployment was attempted; a real upgrade requires the documented backup/application-schema release procedure. Aggregate CAS deliberately makes independent plans contend rather than silently merging concurrent writes. No unresolved ticket-attributable failure remains.

## Disposition

`fixes-applied`. The initial whole-change review preceded implementation edits; the resolved finding has a failing-before/passing-after regression. Final re-review and required safe gates pass. Because this context changed implementation artifacts, another fresh Step 5 context must review the complete change.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-01-manage-independent-trip-plans-review-7.md
  - frontend/src/components/IndependentPlansWorkspace.tsx
  - frontend/src/IndependentTripPlans.test.tsx
SUMMARY: Independently reviewed the complete original-HEAD ticket scope and resolved one P2 stale-baseline bug after Save-and-continue from Discard edits. Added a failing-before/passing-after regression. Full frontend, backend/build and isolated packaged verification pass with no unresolved actionable findings.
DECISIONS:
  - Resolve queued discard from the latest persisted trip ref so successful Save remains the editor baseline.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - FAIL — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'keeps the saved baseline'`: expected pre-fix regression reproduction; resolved.
  - PASS — `npm --prefix frontend test`: final 219 tests in 18 files.
  - PASS — `.\mvnw.cmd clean verify`: 219 backend tests and production asset/JAR build.
  - PASS — `.\scripts\verify-packaged-release.ps1`
  - PASS — `git diff --check`
OPTIONAL_DEVELOPER_CHECKS:
  - Narrow viewport/long-name and screen-reader observation; desktop/mobile comparison of purchased versus planning facts remain unperformed.
REVIEW_RESULT: fixes-applied
NEXT: Launch fresh independent Step 5 review cycle 8 under full.
