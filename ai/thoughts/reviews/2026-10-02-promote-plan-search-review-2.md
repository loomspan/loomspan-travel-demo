# Promote Plan Search Code Review — Cycle 2

## Scope and Repository State

Independent Step 5 of `0_run_pipeline.md`, pipeline mode, selected profile `full` (Full 5-Step Pipeline). Read the ticket, research, implementation plan, testing plan, command/protocol, AGENTS, design lens, Golden Hour design document and ticket mockups. No prior review document was read.

HEAD is `5963e78d30d506c09c7ec73ede754f3813ec07e1` on main. The original pipeline checkout was clean; committed trip-name changes are baseline and outside this ticket. Review compared the working tree with HEAD, inspected staged changes (none), unstaged changes and untracked frontend artifacts. Scope includes 18 tracked frontend files and five new frontend files: summary, comparison helpers, helper/shared tests and reusable fixtures. Research/plans are pipeline artifacts; other reviews were inventoried only as directory entries, never read.

Reviewed shared default/fallback search, all category result/reference paths, summary totals, guest handoff, canonical independent edits/versions/locks, legacy autosave/history/mutation/removal, dialog lifecycle/focus, booking presentation and responsive CSS. Connected evidence includes App guest sequential saves, API response/request shapes, backend purchased locks and tally construction, security public-search rules, and existing integration tests. The frontend `partial` field reflects an existing server field; backend contracts, persistence, dependencies, catalog and trip-name baseline remain unchanged.

Full-profile rigor remains appropriate for this coordinated presentation/lifecycle change. No scope or profile escalation is needed for the bounded keyboard fix below. Initial independent review completed before implementation/test edits.

## Findings

No remaining actionable findings after the repair and internal re-review.

### [P2] Preserve an enabled roving tab stop when permissions change — resolved

- Location: `frontend/src/components/TripComparisonPage.tsx:87` (fallback at line 33).
- Scenario: Search stays is selected. ArrowLeft moves manual focus to Search flights without activating it. A refreshed permission state locks flights while stays remains available and selected.
- Impact: The stored focus still identifies the disabled flight tab, so every enabled tab has `tabIndex=-1`. Keyboard users tabbing back into search cannot reach its tab strip, violating the single enabled roving-stop requirement.
- Evidence: New regression at `frontend/src/components/TripComparisonPage.test.tsx:44` reproduced the failure before the repair: selected stays expected `tabindex="0"`, received `"-1"`. Existing availability effect only repairs an unavailable selected category, not an unavailable separately focused category.
- Fix: Resolve the roving stop from the available stored focus, then the available selected category, then the first available category. Preserve manual activation and pending/read-only disabling.

## Findings Resolved in This Context

Changed only `TripComparisonPage.tsx` and its new test file. Added the dynamic-lock regression and the derived roving-focus fallback. Focused verification now passes 49 tests. Internal re-review checked ordinary arrows/Home/End, manual activation, all-locked/read-only, restored permissions and temporary pending states; one enabled stop remains whenever tabs are usable. No pricing, mutation, purchase or API behavior was changed by this repair.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1: single Build your plan, compact context, usable initial search | Shared header/context, default/hint/locked-category fallback; canonical collapsed details | Canonical empty-plan entry, shared hints/fallback/history release | Implemented |
| AC2: every selection combination, selected-plan total, reflow | Summary rows and valid zero/unknown distinction; selected `plan.tally`; server `partial` with older-shape fallback; CSS grid/min-width/wrap and 1050px collapse | Eight saved and four guest masks, zero/unknown/server-partial, nonprimary returned tally; VisualSystem contracts | Implemented; rendered widths optional |
| AC3: current reference, selected identity, valid full deltas | Comparison helpers validate full snapshot fees/basis; fee-inclusive server option totals; flight pair, stay unit/rooms, rental unit/instants identity | Helper fees/party/rooms/date/invalid/zero/duration matrix; actual category cards and selected flight/car | Implemented |
| AC4: Change/add/remove, deliberate recoverable replacement | Matching category activation/focus; shared PlanDialog, busy gate and explicit boolean result; parent response applied only after success; legacy ConfirmRemoveModal retained | Three-category cancel/failure/retry, no mutation while browsing, deferred duplicate guard, returned selected-plan tally/current versions; legacy removal recovery | Implemented |
| AC5: compact editing, saved searches, dirty protections, independent context | Separate saved/input state, forced-open dirty disclosure, canonical guards/ref versions, legacy dirty/invalid/saving/conflict gate | Canonical independent saves/discard/cancel/navigation/background refresh/current versions; legacy dirty/autosave and disclosure tests | Implemented |
| AC6: integrated saved car search and guest boundary | Optional authenticated rental capability; existing eligibility/interval validation; guest public flight/stay only and local adapters | Driver unknown/24/25, server restriction/reversed interval, normalized/changed interval; existing timezone and public auth/save recovery | Implemented |
| AC7: purchased historical context and restrictions | Canonical component locks/purchased metadata; linked legacy history with conservative unresolved lock; read-only/candidate/context guards; unchanged server lock enforcement | Active/canceled frozen-context, unrelated-copy, disabled actions, candidate permission invalidation; backend purchase/ownership/version suites | Implemented |
| AC8: consistent entry/booking language and adjacent workflows | Shared explorer adapters; review/confirmation labels; management/comparison/settings/history remain in owners | Public/legacy/canonical and management/comparison/booking/confirmation suites | Implemented |
| AC9: Golden Hour, keyboard/focus, search states | Tokens/primitives in screen/responsive sections; linked tab panels/manual keyboard; repaired dynamic roving stop; dialog trap/pending focus; category effects | VisualSystem, shared keyboard/ARIA/Change/dialog/pending/dynamic lock; loading/stale/error/retry/empty; type/build | Implemented; rendered pixels optional |

Plans match the final implementation. Defensive rejection of a stale component choice after detail edits is coherent with the requirement to save/discard and choose again; successful selections remain server-owned. Different rental durations disclose both complete bases. Optional car retains existing server partial-total semantics. Added test doubles and response fixtures exercise observable UI, request versions and failure retention; returning a mock success alone is supplemented by canonical integration assertions on the returned tally. Existing authentication, pending/conflict, removal and booking coverage remains in the full suite.

## Active Project Guardrails

- Golden Hour: new styles remain in `style.css` screen/responsive sections and use existing color/type/spacing/radius tokens. Summary/current-reference layout reuses panels, variants/rows, missing badges and PlanDialog. No new inline styles/colors or hidden essential phone data.
- Panel tabs keep roles/relationships and manual activation, with one enabled stop including dynamic lock updates. Existing visible-focus, reduced-motion and forced-colors contracts pass.
- Full frontend tests/build and root clean verify executed sequentially, preventing Maven npm ci from colliding with running Vitest on Windows.
- Security/privacy review found no new public rental capability, secret-bearing production values, sensitive logging or changed trust boundary. Selection requests carry identity/version/interval, not client price authority; owner/version/lock validation remains backend-owned. Tests use doubles/disposable H2, never the default application/database or live external services.

## Open Questions and Assumptions

None affecting correctness or sufficient automated review confidence. Existing server response contracts are authoritative; optional older metadata is handled as unavailable rather than a fabricated comparison.

## Verification Results

- PASS — frontend `npm test -- src/components/TripComparisonPage.test.tsx src/components/planSearchComparison.test.ts` before repair: 48/48.
- FAIL (intentional regression reproduction, then resolved) — frontend `npm test -- src/components/TripComparisonPage.test.tsx -t "keeps an enabled tab reachable"`: selected enabled stays had tabindex -1.
- PASS — frontend `npm test -- src/components/TripComparisonPage.test.tsx src/components/planSearchComparison.test.ts` after repair: 49/49.
- PASS — frontend `npm test`: 285/285 tests, 20/20 files.
- PASS — frontend `npm run build`: TypeScript/Vite, 58 modules.
- FAIL (environment, resolved) — root `.\mvnw.cmd clean verify` in sandbox: could not create/access existing `C:\.m2\repository`.
- PASS — root `.\mvnw.cmd clean verify` with authorized sandbox escalation: BUILD SUCCESS, 221 backend tests, zero failures/errors/skips, frontend bundled and executable JAR produced. Runtime Java 25, compilation release 21. No automatic approval rejection.
- PASS — root `git diff --check` after settled changes; final `git status --short` and empty staged diff inspected.
- NOT RUN — rendered browser inspection near 1440px/820px/390px; optional under the testing plan. Automated CSS/DOM evidence establishes structure, not pixel geometry.

## Residual Risks and Optional Developer Checks

Inspect guest and saved empty/partial/populated, locked/canceled and booking views at the listed widths using disposable local data: long text, search prominence, editable details, summary totals, visible actions/focus and replacement dialog. No pixel-level result is claimed. No remaining actionable finding or required automated verification gap remains.

## Disposition

`fixes-applied`. This context changed an implementation artifact, so it cannot return clean. Launch a fresh independent Step 5 cycle 3 on the settled repository; only its clean verification establishes pipeline completion.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-02-promote-plan-search-review-2.md
  - frontend/src/components/TripComparisonPage.tsx
  - frontend/src/components/TripComparisonPage.test.tsx
SUMMARY: Independently reviewed the full ticket scope and repaired the dynamic-lock roving-tab defect. No actionable findings remain after internal re-review and required verification.
DECISIONS:
  - Restore an enabled tab stop without changing the selected search panel or manual activation.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — frontend `npm test -- src/components/TripComparisonPage.test.tsx src/components/planSearchComparison.test.ts`: 49/49 after repair.
  - PASS — frontend `npm test`: 285/285, 20 files.
  - PASS — frontend `npm run build`.
  - PASS — root `.\mvnw.cmd clean verify`: authorized escalation, 221 backend tests; initial cache-access failure resolved.
  - PASS — root `git diff --check`.
  - NOT RUN — optional rendered widths: CSS/DOM checks do not measure pixels.
OPTIONAL_DEVELOPER_CHECKS:
  - Inspect disposable guest/saved/locked/booking views near 1440px, 820px and 390px for long text, focus and dialog/reflow.
REVIEW_RESULT: fixes-applied
NEXT: Launch fresh Step 5 cycle 3.
