# Manage independent trip plans Code Review — Cycle 6

## Scope and Repository State

Independent pipeline Step 5 under the developer-confirmed Full 5-Step Pipeline (`full`). Compared the current worktree against original HEAD `1ffcacd5cf204e09e2a3800322d79e6d379e4ff0`; the initial checkout was clean. No staged changes or intervening commits exist. The scope includes all 45 tracked modified files and the nine new implementation/test files, plus the ticket, research and both governing plans. Prior review documents were not read.

Production scope: canonical plan/domain/response/request/controller/service/repository contracts, V21 forward migration, purchased booking context and cancellation inventory, tally completeness, API clients, independent workspace/navigation/dialogs, Profile/history coordination, comparison, confirmation, component locks, duplication, CSS, README and packaged verifier. Test scope: canonical integration tests, migration/restart/concurrency tests, converted snapshot fixtures, existing booking/catalog/trip regressions, UI transitions/history/comparison and HTTP-client payload assertions. Untracked files were read directly; ordinary Git diff alone was not used as the inventory.

Completed correctness/security/state/lifecycle/persistence/concurrency/UI review before editing. Traced owner authentication and CSRF through strict request parsers to aggregate/child CAS and transactional writes; primary replacement/sole deletion through rollback and history guards; canonical and legacy component routes through purchase locks; booking/cancellation through frozen snapshots, party and departure fields; tabs, parent exits and history through dirty/pending guards; and comparisons/profile/restart through canonical UUID projections. No live database, deployment, supplier, payment or model service was used.

## Findings

No remaining actionable findings after the fix and internal re-review.

## Findings Resolved in This Context

### [P2] Validate the trip budget before serializing the settings request
- Location: `frontend/src/components/IndependentPlansWorkspace.tsx:158` (validation helper), `:239` (settings input/action).
- Scenario: On the new canonical workspace, open Trip settings with an existing budget, enter `abc` (or a non-finite number), and click Save trip budget. The previous implementation called `Math.round(Number(budget) * 100)` without validation.
- Impact: `NaN`/`Infinity` serialize to JSON `null` in the existing HTTP client. The server deliberately accepts null as an explicit budget removal, so malformed input could silently clear a persisted shared budget and alter every plan's budget comparison. The unchanged legacy workspace already validates the amount before sending it; this regression is introduced by the new workspace branch.
- Evidence: The new interaction regression failed before the fix because `replaceSharedDetails` was called for `abc`. Traced the client request serialization and server `validateBudget` null handling. The original broad UI suite passed despite this gap.
- Fix: Validate the trimmed input as a finite amount in the existing $0–$1,000,000 range before any transition or request; display a local accessible validation error and preserve the entered text. Preserve blank-as-explicit-clear behavior and ordinary cent rounding. Separate budget validation errors from saved-plan errors so budget correction does not erase a failed plan save.
- Test: `IndependentTripPlans.test.tsx` now rejects `abc`, `Infinity`, negative and over-limit values with zero write calls, preserves input, accepts intentional blank clearing and sends 1234 cents for $12.34. Focused red/green and final broad gates are recorded below.

Only `IndependentPlansWorkspace.tsx` and `IndependentTripPlans.test.tsx` were changed as implementation artifacts in this context. No ticket/plan decision or product scope change was needed: this restores the existing budget validation contract.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Independently inspected executable evidence | Result |
| --- | --- | --- | --- |
| AC1 exactly one primary and legacy preservation | V21 Working UUID conversion, oldest-plan/no-plan fallback, single same-trip pointer; canonical hydration | `TripModelForwardMigrationIntegrationTest` V18/V20 lineage, absent/empty Working, unknown ages and failed-night source retention; canonical invariant assertions | implemented |
| AC2 top accessible named plans | `PlanNavigation`, labeled tabs/panel, Primary text badge, native All plans and overflow CSS | canonical UI primary-first/manual arrow/Home/End/Enter/Space/overflow tests | implemented |
| AC3 selection changes view only | UUID selection, `switchPlan`; retired load/replace writes | UI zero save/load/promote calls on selection; `retiresCopyAndReplaceWithoutAnyWrites` | implemented |
| AC4 independent save/rename | canonical party/snapshot storage, save revalidation and name-only action | independent HTTP save/sibling equality, lifecycle test, direct alternative component mutation, restart assertions | implemented |
| AC5 create/copy independence | empty alternative insertion, copied new UUID without booking rows | create/copy lifecycle, purchased copy unlocked/unbooked and packaged copy assertions | implemented |
| AC6 dirty/failure/conflict/ownership | Save/Discard/Cancel guard, pending serialization, refresh checks, owner principal and aggregate/child versions | deferred-save/late-refresh/input/focus/parent-history UI tests; foreign-owner/stale HTTP checks; added budget no-write regression | implemented |
| AC7 booked primary promotion | pointer-only operation and aggregate CAS | booked promotion snapshots unchanged, same-version promotion winner test, restart and packaged persistence | implemented |
| AC8 faithful purchased locks | snapshot-derived component protection, purchased dates/party/pricing/cutoff | canonical/legacy omission/remove rejection, airfare/stay/rental locks, mixed context totals/search budget and cancellation restoration assertions | implemented |
| AC9 history/canceled restrictions | any-history deletion block; active-trip mutation guards | existing cancellation/history API tests plus canonical protected delete and locked canceled UI cases | implemented |
| AC10 atomic primary deletion | replacement validation, pointer/delete transaction and same-trip FK | required/foreign replacement, injected deletion rollback, promotion/delete and purchase/delete races | implemented |
| AC11 sole-plan deletion | explicit trip-delete consent and version/count/history checks | sole-flow Keep/Delete UI assertions; explicit consent and injected sole-delete rollback integration | implemented |
| AC12 2–3 plan comparison | navigation-adjacent chooser, default primary/alternative selection and bounds | canonical launch/default/alternatives-only/fourth-disabled UI assertions; existing comparison semantics | implemented |
| AC13 accurate names/status/dates/partial totals | canonical comparison facts and explicit missing/partial labels | named dates/party/Primary/Booked, Not selected, Partial $0 and unavailable-tally assertions | implemented |
| AC14 navigation and reopen | preserved IDs/compatibility projections, selected-plan and confirmation history IDs | real Profile/logout/cross-trip guard, confirmation Back/reopen/status, file-H2 restart and packaged verifier | implemented |
| Shared destination/budget; frozen purchases | trip settings, per-plan details, separate booking snapshot mapper | shared-setting validation restored here; purchase equality and frozen cutoff/seat assertions | implemented |
| Service-enforced nonempty primary invariant | pointer temporarily nullable only within transaction; archived drafts have no runtime writes | rollback/race invariant tests, hydration read-lock tests and source search | implemented |
| Legacy rendering compatibility branch | `TripWorkspace` chooses canonical branch when plans exist | canonical tests cover new branch; old fixtures continue to exercise legacy rendering | safe deviation recorded in governing plan |

Test-quality assessment: canonical integration tests perform authenticated HTTP operations and direct persisted invariant checks; failure injections occur at destructive repository boundaries and assert pointer/version restoration; races synchronize two supported mutations and assert one winner; migration tests use actual old-schema rows; restart tests close/reopen file databases. Fixture conversion builds complete canonical snapshots rather than merely adjusting expected counts. UI tests assert selected identity, field values, API payloads/call counts and focus with controlled async responses. Legacy-only tests do not by themselves establish canonical behavior; the new suite and packaged checks supply that evidence. The missing malformed-budget boundary was added as a behavioral regression, not an implementation-mirroring assertion.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`. Its placeholder examples were not treated as policy. Full-profile assurance remains appropriate because persisted contracts, API lifecycle, concurrency and booking boundaries change.

## Open Questions and Assumptions

None requiring a developer decision. Existing trip destination/budget scope, optional rental booking and historical purchased-component restrictions follow the governing plan and current supported behavior.

## Verification Results

- PASS — `npm --prefix frontend test` before fixes: 217/217 tests in 18 files. Receipt `%TEMP%/review6-frontend.log`.
- PASS — `.\mvnw.cmd clean verify`: 219/219 Java tests, TypeScript/Vite production build and executable JAR. Receipt `%TEMP%/review6-maven.log`. Completed before the small UI fix; no Java/schema changes occurred in this context.
- FAIL (expected red) — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'rejects invalid trip budgets'`: 1 failed/25 filtered, because malformed text issued a settings write. Receipt `%TEMP%/review6-budget-red.log`.
- PASS — the same focused command after validation: 1 passed/25 filtered. Receipt `%TEMP%/review6-budget-green.log`.
- PASS — `npm --prefix frontend test` on final edits, including the extended valid decimal assertion: 218/218 in 18 files. Receipt `%TEMP%/review6-frontend-final.log`.
- PASS — `npm --prefix frontend run build`: final TypeScript and Vite assets. Receipt `%TEMP%/review6-build.log`.
- PASS — `.\mvnw.cmd -DskipFrontend=true -DskipTests package`: packaged final assets into the executable JAR; tests intentionally skipped here because the unchanged backend already passed clean verify and the final frontend passed Vitest/build. Receipt `%TEMP%/review6-package.log`.
- PASS — `.\scripts\verify-packaged-release.ps1`: isolated final-JAR HTTP lifecycle, independent party/dates, unconfirmed copy, booked primary promotion, locked purchases, CSRF, idempotent replay, frozen purchased details, cancellation/history, file-backed restart and owner isolation. Its temporary processes/database were cleaned up. Receipt `%TEMP%/review6-packaged.log`.
- PASS — `git diff --check`: no whitespace errors.
- PASS — `rg 'detour_trip_draft' src/main/java/app/detour`: no runtime source references (rg's no-match result is expected).

Frontend/Maven commands were executed sequentially to avoid Windows native-module locks. Re-reviewed the final validation handler, its guarded settings call, API serialization and test assertions against the full previously reviewed scope; no remaining actionable finding was identified. Verification attribution distinguishes the intentionally failing red test from passing final gates.

## Residual Risks and Optional Developer Checks

- NOT PERFORMED: 320px/long-name visual inspection and screen-reader announcements of selected/Primary/Confirmed/error states. Automated semantics/keyboard/focus/overflow reachability are covered; physical layout and actual assistive-technology output remain optional observations.
- NOT PERFORMED: desktop/mobile comparison and purchased-detail visual inspection with fictional local data.
- No real database migration or deployment performed. V21 is forward-only; H2 DDL can commit during failure, so archived source preservation and restart diagnostics are verified rather than claiming complete schema rollback. README documents backup/forward-recovery and prohibits using an older application against V21.

## Disposition

`fixes-applied`: one in-scope correctness regression fixed; full internal re-review found no other actionable findings. A fresh Step 5 context must review these implementation edits before a clean pipeline result.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-01-manage-independent-trip-plans-review-6.md
  - frontend/src/components/IndependentPlansWorkspace.tsx
  - frontend/src/IndependentTripPlans.test.tsx
SUMMARY: Independently reviewed the full original-HEAD ticket scope and fixed malformed budget input silently clearing a saved shared budget. Added a failing-first regression, validated the final UI and packaged application, and found no remaining actionable findings.
DECISIONS:
  - Restore existing finite/range budget validation while preserving intentional blank clearing; no product decision or scope change required.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `.\mvnw.cmd clean verify`: 219 Java tests and pre-fix frontend production/JAR gate.
  - FAIL — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'rejects invalid trip budgets'`: expected pre-fix red; then PASS after fix.
  - PASS — `npm --prefix frontend test`: final 218 UI/client tests.
  - PASS — `npm --prefix frontend run build`
  - PASS — `.\mvnw.cmd -DskipFrontend=true -DskipTests package`
  - PASS — `.\scripts\verify-packaged-release.ps1`
  - PASS — `git diff --check`
OPTIONAL_DEVELOPER_CHECKS:
  - Narrow-screen/long-name visual and real screen-reader inspection; desktop/mobile comparison/purchased-detail visual inspection remain unperformed.
REVIEW_RESULT: fixes-applied
NEXT: Launch fresh independent Step 5 review 7 under the confirmed full profile.
