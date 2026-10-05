# Promote Plan Search Code Review — Cycle 1

## Scope and Repository State

Independent Step 5, pipeline mode, profile `full`. Read the complete supplied ticket, research, implementation/testing plans, AGENTS.md, frontend/DESIGN.md, design lens and review/protocol instructions. No prior review artifacts were consulted. Base is main HEAD `5963e78d30d506c09c7ec73ede754f3813ec07e1`; committed trip-name work is outside scope. Step 0 began clean. Inventoried unstaged and untracked work; no staged changes. Reviewed all 18 changed tracked frontend files, the five new frontend summary/helper/test/fixture files, and supplied research/plans. No backend production, dependency, schema, catalog or endpoint changes.

Traced shared search through guest, independent plans and legacy adapters, selected-plan/server totals, full-fee comparison bases, replacement success/failure/context invalidation, edit guards/current versions, historical locks, rental validation, loading/stale/error/retry, modal/tab keyboard behavior, CSS ancestors and adjacent management/booking/auth flows. Checked connected backend ownership/version/purchase enforcement. Completed initial independent review before editing implementation artifacts.

## Findings

No open actionable findings after fixes and internal re-review. Initial review found these two defects:

### [P2] Keep dirty editing disclosures open after native toggles
- Location: `frontend/src/components/IndependentPlansWorkspace.tsx:207`; `frontend/src/components/TripWorkspace.tsx:1409`.
- Scenario: Open details, change/clear a date, then close the native disclosure.
- Impact: Unsaved/invalid input and save/discard controls disappear while mutations remain disabled, violating the requirement to keep unsaved edits visible.
- Evidence: The original onToggle ignored dirty-state changes; an unchanged React open prop does not restore the browser-mutated attribute. Two new regressions simulated the native open-property change and toggle event and failed with the open attribute absent.
- Fix: Restore the native open property while dirty; continue recording the clean-state disclosure preference. Add both entry-path regressions.

### [P2] Remove the obsolete outer tally column from legacy planning
- Location: `frontend/src/style.css:3523`.
- Scenario: Legacy workspace at viewport width >=1100px after removal of standalone tally.
- Impact: The old outer grid reserves an empty 18–22rem column, squeezing the new search/sidebar grid into the remaining left column.
- Evidence: Outer builder still declared two columns and `heading heading` / `slots tally`, although it now contains only header and slots. New VisualSystem regression failed against this rule.
- Fix: Use one full-width outer column with heading/slots areas and remove unused standalone-tally positioning; shared search layout owns desktop columns.

## Findings Resolved in This Context

Both findings were reproduced before production fixes. Changed only IndependentPlansWorkspace, TripWorkspace, style.css and the three relevant test files. Full frontend tests pass after fixes. Re-reviewed the complete final ticket diff and connected behavior; no additional actionable findings remain. Fresh Step 5 review is required because implementation artifacts changed.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1: active empty-plan search, compact context, single heading | TripComparisonPage defaults/fallback; collapsed editor | Canonical entry; hints/lock-release tests | implemented |
| AC2: selection masks/viewed-plan totals/responsive structure | PlanSelectionsSummary, plan.tally, shared grid | Eight saved/four guest masks; zero/unknown/partial/nonprimary totals; VisualSystem | implemented |
| AC3: current reference/identity/full deltas | Comparison helper and three search sections | Fee/party/night/room/interval matrix; selected identities/card deltas | implemented |
| AC4: change/add/remove/confirm/cancel/failure/success | Candidate dialog; guarded boolean adapters; server ownership | Three-category recovery/current-version/returned-total tests; legacy removal | implemented |
| AC5: compact editing/saved context/unsaved guards | Corrected disclosures; dirty/pending gates; current revision refs | Save/Discard/leave; nonprimary context; legacy autosave; native-toggle regressions | implemented |
| AC6: rental eligibility/interval/guest boundary | Authenticated rental capability; retained validation; guest flights/stays only | Driver unknown/24/25, server-disabled/reversed interval/timezones, public auth/handoff | implemented |
| AC7: purchased history/read-only | Plan locks/context; linked legacy history; confirm-time checks | Active/canceled purchases, unrelated copy, permission invalidation, backend suites | implemented |
| AC8: entry/booking parity and retained management | Shared adapters, management/history, booking language | Full guest/canonical/legacy/management/comparison/booking suites | implemented |
| AC9: Golden Hour/keyboard/focus/states | Tokens/primitives, manual tabs, pending modal trap, category effects | ARIA/focus/stale/loading/error/retry/empty; VisualSystem; build | implemented |
| Full profile/frontend-only scope | No new persisted/external contract; unchanged backend authority | Independent review and sequential full gates | implemented |

## Active Project Guardrails

Golden Hour: new screen rules use tokens in style.css section 11, responsive rules in section 12; no new inline styles or component color literals. Intentional button variants/rows, panel tabs and modal primitives remain. The CSS fix preserves original VisualSystem contracts and essential content remains rendered on narrow screens. Reviewed security/privacy applicability: no introduced secret/log exposure or ownership bypass; authenticated mutation endpoints and backend locks remain authority, guest rental remains absent. Routine verification used doubles/disposable databases.

## Open Questions and Assumptions

None affecting completion. Older response shapes lacking comparison metadata/tally intentionally show unavailable evidence instead of fabricated zero comparisons.

## Verification Results

- PASS — frontend `npm test -- src/components/planSearchComparison.test.ts src/components/TripComparisonPage.test.tsx src/IndependentTripPlans.test.tsx src/ProgressiveTripBuilder.test.tsx` — 106 tests before fixes.
- FAIL (expected red) — frontend `npm test -- src/IndependentTripPlans.test.tsx src/ProgressiveTripBuilder.test.tsx src/VisualSystem.test.tsx -t "keeps unsaved plan edits visible|keeps invalid legacy edits visible|reflows the promoted search"` — three regressions reproduced both findings.
- PASS — frontend `npm test` — 284 tests in 20 files after fixes, including all red regressions.
- PASS — frontend `npm run build` — TypeScript/Vite, 58 modules.
- PASS — root `.\mvnw.cmd clean verify` — 221 backend tests, zero failures/errors/skips; frontend bundle and executable JAR. Authorized existing-cache escalation; isolated databases, including temporary restart files under target. Java 25 runtime compiled release 21; no default/live app/database launch.
- PASS — root `git diff --check` — no whitespace defects, Windows line-ending notices only.
- NOT RUN — rendered 1440px/820px/390px inspection — optional check; CSS/DOM evidence does not prove pixels.

## Residual Risks and Optional Developer Checks

Inspect guest/canonical/legacy empty/partial/populated and purchased/read-only views around 1440px, 820px and 390px using disposable local data: long text, compact editors, search prominence, summaries, dialog focus and booking typography. Optional rendered observation is not a completion gate. No other unresolved correctness risk identified.

## Disposition

`fixes-applied`. Required verification passed and internal re-review found no remaining actionable findings. Launch a fresh independent cycle 2.
