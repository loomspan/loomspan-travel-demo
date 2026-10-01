# Independent Trip Plans Code Review — Cycle 3

## Scope and Repository State

Independent Step 5 under the developer-confirmed Full 5-Step Pipeline (`full`). Comparison base: original clean checkout HEAD `1ffcacd5cf204e09e2a3800322d79e6d379e4ff0` on `main`. Reconstructed tracked edits and untracked canonical model, V21 migration, workspace/navigation/dialog, test, and governing artifacts. Prior review documents were excluded and were not read. No repository or ancestor AGENTS.md was found; design-lens records no active guardrails.

Read the ticket, research, implementation plan, and testing plan. Reviewed the entire production change and connected paths: owner-scoped parsing/controllers, canonical repository reads and writes, service transactions, component eligibility/revalidation, compatibility routes/projections, booking snapshots and cancellation inventory, profile/navigation, dirty transitions, comparison, confirmation/history, release verifier and README. Examined relevant integration and DOM assertions rather than trusting earlier receipts. Initial review completed before editing.

## Findings

No remaining actionable findings after the fix and internal re-review. The initial review identified the following resolved finding.

### [P1] Guard the post-booking refresh against newer editor state

- Location: `frontend/src/components/IndependentPlansWorkspace.tsx:163` (booking-success callback; previously unconditional `getTrip(...).then(fresh => apply(fresh, true))`).
- Scenario: Confirm a booking, return to the workspace while its background trip GET is still pending, and edit the departure date. Resolve the older GET. The same race occurs if the user completes a newer plan save before the original GET resolves.
- Impact: The late response silently resets unsaved input, or replaces the displayed baseline of a newer saved plan with stale data. This violates AC6's edit-preservation and response-race requirements.
- Evidence: Added two controlled-promise DOM cases in `frontend/src/IndependentTripPlans.test.tsx:27`. Both failed independently before the fix: expected departure `2027-03-11`, received `2027-03-10`. Existing generic refresh tests covered the imperative refresh path but never the booking-success path.
- Fix: Extract the existing `refreshIfClean` implementation and reuse it from booking success. At dispatch and resolution it respects dirty/in-flight state; captured aggregate version prevents a response from replacing a newer mutation. Confirmation continues to use the booking response immediately. No server or persistence behavior changed.

## Findings Resolved in This Context

One P1 response race fixed; two executable regression cases added. The canonical workspace suite passes after the change. Re-reviewed the complete change and the shared refresh entry points; no other ticket-introduced actionable issue remained. The context changed implementation artifacts, so it must report `fixes-applied`, even though the final internal review has no open findings.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1 primary and compatibility | V21 preserves Working UUID and existing options; fallback oldest option/empty primary; canonical loader requires reachable pointer | V20/V18 migration fixtures, fresh schema, missing-night failure/source preservation, restart | implemented |
| AC2 accessible top navigation | Primary-first PlanNavigation, text badge, roving/manual activation, scroll overflow and All plans | Canonical workspace tab/keyboard/overflow assertions | implemented |
| AC3 pure view switching | Selected UUID and history state; no load/copy call | No mutation calls during clean switching; retired load/replace HTTP tests | implemented |
| AC4 independent edits and identity | Per-plan party/dates/snapshots and targeted save/rename | Canonical independent save, sibling equality, direct alternative removal, component suites | implemented |
| AC5 create/copy | Empty alternative and new UUID copy without booking rows | Lifecycle, purchased-source copy, packaged verifier | implemented |
| AC6 dirty/conflict/ownership | Save/Discard/Cancel, busy refs, guarded refresh, principal ownership and aggregate/child CAS | Dirty management/exit/history/failure tests, late refresh tests, new post-booking race tests, HTTP 404/409 | implemented |
| AC7 booked promotion | Sole persisted same-trip primary pointer; promotion does not rewrite snapshots/bookings | Booked promotion, synchronized promotion race, file restart and package gate | implemented |
| AC8 confirmed locks and faithful purchases | History-derived component locks on canonical/legacy routes; frozen purchased dates/count/ages; one response mapper | Airfare/stay/rental tampering and frozen-context tests; DOM locks and purchased details | implemented |
| AC9 booking/history/canceled protection | History-based delete restriction; active-trip checks; frozen cancellation/restoration | Booking cancellation/concurrency, canonical protected deletion, canceled-trip UI | implemented |
| AC10 primary replacement/atomicity | Required distinct owned replacement; transaction, aggregate CAS and same-trip FK | Replacement rejection, injected pointer rollback, promotion/delete and purchase/delete races | implemented |
| AC11 sole-plan deletion | Explicit trip-delete consent/count; trip history eligibility; transactional deletion | Consent/rollback/404 assertions, exact sole prompt and Keep trip zero mutation | implemented |
| AC12 comparison | Top chooser includes all canonical plans and allows 2–3 distinct choices; alternative launch includes primary | Chooser defaults, alternatives-only and fourth-disabled assertions | implemented |
| AC13 truthful comparison | Plan names/party/dates, primary/booked badges, missing selection and partial/unavailable totals | Canonical empty comparison and retained desktop/mobile price/semantics tests | implemented |
| AC14 navigation and reopen | Preserved public IDs, profile summary from primary, history confirmation ID, canonical booked lookup | Profile/history tests, trip/booking file restart, packaged lifecycle/restart | implemented |
| Destination/budget remain shared | Explicit Trip settings; primary save and settings share transaction; alternatives' party/date storage separate | Shared-save conflicts, independent-plan tests and release checks | implemented |
| No amendments/incremental purchase | Active-trip booking uniqueness unchanged; confirmed components server-locked | Existing idempotency, capacity, cancellation/rebooking and ALREADY_BOOKED tests retained | implemented |
| Archived drafts and deployment contract | No Java runtime references to archived draft tables; V12–V20 unchanged; README backup/forward recovery guidance | Runtime source search; V21 migration failure and latest-schema assertions | implemented |

## Review Risk Assessment and Test Quality

- Persistence/lifecycle: A nullable primary pointer supports intermediate transactional creation/deletion; it is not itself an at-least-one database guarantee. Canonical creation establishes it, loader validates it, replacement deletion and last-plan deletion remain transactional, and same-trip FK rejects foreign membership. Race tests exercise real HTTP operations and database invariants; injected failures assert rollback rather than merely HTTP errors.
- Purchased context: Reviewed actual response, restoration and cutoff consumers. They use frozen purchased count/dates and snapshots; plan mutation overlays locked slots from history. Copies create no booking associations. Existing supported cancellation/rebooking coverage remains; this ticket adds no reservation amendments.
- Security/privacy: New routes stay inside authenticated/CSRF-protected security configuration. Ownership comes from the principal and owner-filtered aggregate; children/replacements resolve within that aggregate. Strict writable-field parsers reject primary/purchase/owner injection. SQL parameters remain bound; JSX displays names as text. No live accounts, development database, model/supplier/payment calls or credentials were used.
- UI/lifecycle: Traced dirty switches, management, parent exits, browser history, in-flight saves, stale refreshes and confirmation identity. The discovered unguarded booking refresh was a separate reachable response path, which explains why the previous generic refresh assertions missed it. New cases exercise the actual booking-review/confirmation/editor sequence with deferred GET responses.
- Test boundaries: Synthetic JDBC fixtures now resolve descriptive canonical snapshots via TestPlanSelections; migration tests still insert real old-schema rows. Existing snapshot stability, inventory, owner isolation, conflict, cancellation and restart assertions remain meaningful. DOM tests prove semantics and navigation, not actual narrow-screen clipping or screen-reader behavior.
- Operations/performance: Release verifier spawns its own loopback server with a unique temporary file H2 database, strips credential-like environment variables, and stops/cleans its own resources. Response building performs repeated per-plan/history reads; this follows the existing small demo aggregate usage and no concrete unbounded workload regression was established. V21 failure is intentionally diagnosed with preserved source rows rather than claiming H2 DDL rollback.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

None requiring a developer decision. Original checkout was clean as supplied by the orchestrator and recorded in research; all current production/test/README/release changes belong to this ticket. Full remains required because persisted contracts and plan/booking lifecycle change.

## Verification Results

- PASS — `npm --prefix frontend test` (initial independent gate): 214 tests in 18 files.
- FAIL (expected red reproduction, repaired) — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'post-booking refresh'`: both added race cases failed with stale departure date.
- PASS — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx`: 24 tests.
- PASS — `npm --prefix frontend test` (final): 216 tests in 18 files.
- PASS — `.\mvnw.cmd clean verify`: 217 backend tests, zero failures/errors/skips; frontend TypeScript/Vite production build and packaged JAR succeeded. Ran sequentially after Vitest to avoid Windows native-module locks.
- PASS — `.\scripts\verify-packaged-release.ps1`: disposable packaged server/database authentication, CSRF, independent plan dates/party, copy without purchases, booked promotion, locked components, idempotency, cancellation/history, restart and owner isolation.
- Receipts: `%TEMP%/review3-red.log`, `review3-focused.log`, `review3-frontend-final.log`, `review3-cleanverify.log`, `review3-packaged.log`. All results above were executed in this context rather than copied from implementation receipts.
- PASS — `git diff --check`.
- PASS — `rg -n 'detour_trip_draft' src/main/java`: no matches (expected search exit 1).

## Residual Risks and Optional Developer Checks

Actual 320px viewport, long-name clipping, visible focus, screen-reader Primary/Confirmed/error announcements, and desktop/mobile purchased-detail appearance remain optional and were not performed. Automated semantics/interaction coverage is complementary evidence. No deployment or real database migration was performed. Production upgrades still require a backup and matching V21 application release; archived draft tables are diagnostic evidence, not an automatic downgrade path.

## Disposition

`fixes-applied`; final internal re-review has no actionable findings and all required safe gates passed. A fresh independent Step 5 context must review this context's fix.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-01-manage-independent-trip-plans-review-3.md
  - frontend/src/components/IndependentPlansWorkspace.tsx
  - frontend/src/IndependentTripPlans.test.tsx
SUMMARY: Independently reviewed the entire original-HEAD ticket change. Fixed a reproduced P1 post-booking refresh race that erased unsaved input or replaced a newer saved baseline. Final internal review has no open findings; all required safe gates passed.
DECISIONS:
  - Reuse the existing dirty/busy/version refresh guard for booking-success GETs, matching the planned response-race contract without changing persistence.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - FAIL — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'post-booking refresh'`: expected pre-fix red reproduction, both cases repaired.
  - PASS — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx`
  - PASS — `npm --prefix frontend test`
  - PASS — `.\mvnw.cmd clean verify`
  - PASS — `.\scripts\verify-packaged-release.ps1`
  - PASS — `git diff --check`
OPTIONAL_DEVELOPER_CHECKS:
  - Narrow-screen long-name/focus inspection and screen-reader announcements; desktop/mobile purchased-detail appearance (not performed).
REVIEW_RESULT: fixes-applied
NEXT: Launch fresh independent Step 5 review cycle 4 against the complete original-HEAD ticket change.
