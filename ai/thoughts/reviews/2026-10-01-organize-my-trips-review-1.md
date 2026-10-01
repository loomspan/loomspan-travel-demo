# Organize My Trips Code Review — Cycle 1

## Scope and Repository State

Independent Step 5 in pipeline mode, selected profile `full`. Read the complete ticket, research, implementation/testing plans, review command, shared protocol and design lens. No prior review document was read. No AGENTS.md was found in the checkout or checked ancestor paths.

Comparison base is the current HEAD (`6fbb44739c9efd8a182c8c55c796ee389c2c9f27`, main); the initial checkout was clean and this pipeline owns all listed changes. Independently inventoried unstaged, staged (empty) and untracked changes. Reviewed the shared Java/TypeScript projection, TripListSection, CSS, both confirmation modals, new focus helper, all changed/new test hunks and process artifacts. Traced authenticated endpoint ownership, parent/Working synchronization, option loading, booking expiration/cancellation authority, App/ProfileScreen refresh sequencing, workspace consumers, and prior card predicates beyond the diff. No dependency, migration, persisted contract or external supplier/model change exists.

Completed the independent review before editing implementation artifacts. Full remains appropriate for the shared API/date projection. Initial focused verification passed; a delayed-refresh acceptance check subsequently confirmed the focus lifecycle defect below. Final internal re-review found no remaining actionable issues.

## Findings

### [P2] Close confirmation after refreshing card membership — resolved
- Location: `frontend/src/components/ProfileScreen.tsx:224` and `:279`.
- Scenario: Cancel an eligible trip under the Upcoming filter, with the successful mutation followed by a delayed profile refresh. The original handler closed the modal before awaiting refresh; the modal restored focus to the still-mounted card trigger, then the updated profile removed that card. Last-card deletion has the same sequencing boundary.
- Impact: Focus falls to document.body after a successful primary workflow, breaking the required visible list/removal focus behavior for keyboard and assistive-technology users.
- Evidence: Deferred mocked App flow failed specifically at the Upcoming heading focus assertion (actual body). Immediate refresh mocks had passed because completion could batch with dialog closure. Neither persistent menu-trigger restoration nor the visible-heading fallback compensates when the card disappears only after the modal has already closed.
- Fix: Await the existing profile refresh before clearing cancellation/deletion targets. The pending dialog retains its focus trap and dismissal restrictions until final membership is known, then existing modal restoration chooses the surviving trigger or visible heading. No mutation payload, permission predicate or refresh-failure branch changed.

## Findings Resolved in This Context

- Updated the two success paths in ProfileScreen.
- Added parameterized App integration cases for delayed cancellation and deletion refresh, pending focus/Escape restrictions, disappearance and section/page fallback focus.
- Persisted the sequencing decision and delayed-refresh test boundary in the governing implementation/testing plans.
- Re-reviewed the complete current ticket diff; no open finding remains. This context changed implementation artifacts and therefore requires another fresh reviewer.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Four counts/filters; All section order; cancelled isolation | TripListSection filters exclude CANCELED from active groups and merge cancelled inputs | Component mixed groups/counts/pressed/empty cases; App cancelled filters | implemented |
| Inclusive authoritative in-progress and Past | TripService captures one instant/Portland date; emitted parent end determines Past; ACTIVE start/end determines inProgress | Both endpoints, DST/start/end millisecond boundaries, later/inverse Saved dates and cancelled override; contradictory browser-time component fixtures | implemented |
| Relevant date, name and stable ordering | Copied filtered groups sort by progress/start, end descending or cancelled start descending; fixed English collator then lexical public ID | Frozen shuffled inputs, conflicting date orders, equal-key fixtures and reversed rerender | implemented |
| Revisiting/refreshing persisted changes | Existing App request IDs and ProfileScreen navigation/popstate/workspace/mutation refresh; fresh service projection and local sorting | Changed profile date/name/status App flows, persisted rename reorder, delayed stale response and refresh failures, option load API reclassification; retained dirty/stale workspace tests | implemented |
| Readable compact hierarchy | Card heading/destination/UTC date-only endpoints with years/status counts/Open; no expanded alternatives | DOM order, heading levels, cross-month/year, booking/reference/count/detail exclusion; workspace suites retained | implemented |
| Accessible secondary actions and preserved restrictions | Native named trigger/menuitems; Arrow/Home/End/Escape/Tab/outside dismissal; existing history/expiry/cancelled predicates and rename validation; delayed confirmation sequencing | Component menu/guard/callback/rename pending/errors; App delete/cancel payload/modal/focus cases, new deferred removal cases; API owner/version/history/inventory guards | implemented |
| Narrow screens and keyboard/AT semantics | In-flow bounded menu, wrapping card/control CSS, visible focus; labelled native controls/articles/regions and aria-pressed/expanded | Component keyboard/ARIA/focus interactions and VisualSystem affordance assertions | implemented; actual geometry/AT observation optional |
| Preserve shared consumers and independent option expiration | Existing UPCOMING/PAST arrays/ordering and constructor overloads; additive inProgress; option expiration uses own departure and shared captured instant | Existing endpoint/count tests, independent Saved expiration/load tests and booked-option cancellation boundary suite | implemented |

## Active Project Guardrails

None recorded. Checked design-lens placeholder examples were not treated as policy.

## Open Questions and Assumptions

None requiring a developer decision. Parent/Working dates remain synchronized by the existing supported edit/load operations. The repository ships frontend/backend together in its Maven artifact; no browser-clock fallback or passive midnight refresh is required by the approved plan.

## Verification Results

- PASS — from frontend, `npm test -- src/components/TripListSection.test.tsx src/App.test.tsx src/FeeFreeCancellationAndTriage.test.tsx src/ProgressiveTripBuilder.test.tsx src/VisualSystem.test.tsx`: 108 tests before review fixes.
- PASS — `./mvnw.cmd "-DskipFrontend=true" "-Dtest=TripApiIntegrationTest,BookingCancellationIntegrationTest" test`: 66 tests, zero failures/errors/skips.
- PASS — from frontend, `npm test`: initial 188 tests/17 files; final post-fix run 190 tests/17 files.
- FAIL (intended regression) — from frontend, `npm test -- src/App.test.tsx -t "cancellation refresh removes"`: deferred refresh leaves body focused before the fix. The immediate-response version had passed; deferring the response was necessary to exercise the real lifecycle boundary. An intermediate post-fix run still expected premature dialog closure; that obsolete waiting condition was replaced with assertions for pending focus and completion.
- PASS — from frontend, `npm test -- src/App.test.tsx -t "refresh removes"`: final 2 delayed cancellation/deletion cases; 43 unrelated cases intentionally skipped.
- PASS — `./mvnw.cmd clean verify`: initial 206 backend tests and frontend build/package. Repeated after the production fix as `./mvnw.cmd clean verify > target-review-1-verify.log 2>&1`: 206 backend tests, zero failures/errors/skips, updated TypeScript/Vite build and JAR packaging. Inspected successful log and removed that temporary diagnostic file.
- PASS — `git diff --check`: final diff has no whitespace errors.
- NOT RUN — `./scripts/verify-packaged-release.ps1`: no startup/persistence/release contract change; not a required gate in the approved testing plan.

Test assertions exercise supported API operations in isolated H2, mocked frontend requests and user interactions. No live/default development database or external supplier/model operation was performed. Reviewed data rendering and request/log paths: ownership/CSRF and mutation validation remain server-controlled; React escapes displayed names; no real secrets or personal records were introduced. Sorting adds bounded O(n log n) local work; existing repository query amplification was not broadened. Menu listeners/timer cleanup, disabled-only focus, failure recovery and constructor compatibility were checked.

## Residual Risks and Optional Developer Checks

Actual 320px/375px/desktop geometry, 200% zoom, screen-reader announcements, real keyboard observation and forced-color rendering remain unperformed optional checks on an isolated app. jsdom DOM/focus and CSS assertions establish only their stated boundaries. No unresolved automated verification failure remains.

## Disposition

`fixes-applied`. One focus finding was fixed and verified; no actionable finding remains in this context. A fresh Step 5 context must independently review the current implementation before pipeline completion.
