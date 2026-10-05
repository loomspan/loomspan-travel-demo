# Promote Plan Search Testing Plan

## Change Summary
- Ticket: `ai/thoughts/tickets/2026-10-02-promote-plan-search.md`
- Research: `ai/thoughts/research/2026-10-02-promote-plan-search.md`
- Implementation plan: `ai/thoughts/plans/2026-10-02-promote-plan-search.md`
- Pipeline profile: `full`; Step 3 continues Step 2 in the same context.

Promote one shared Build your plan layout with flight/stay/authenticated-car tabs, compact saved context/editor/selection summary, current-category reference, validated full-total differences, and confirmation before replacement. Preserve server-result ownership, independent plans, locks/history, unsaved edits, guest auth/save, management and booking flows. This artifact plans tests only; no tests or production code were edited or run during planning.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Shared layout | Empty plan still has no active search; duplicate cards/headings | shared explorer/default and entry tests; canonical/legacy/guest assertions |
| Context and totals | Nonprimary plan accidentally uses primary dates/party/tally | distinct canonical fixtures, exact endpoint arguments and server totals |
| Prices | Unit price versus total; unknown becomes zero; wrong interval/rooms/party | pure helper and actual result-card tests with unequal unit/full prices and malformed evidence |
| Replacement | Optimistic overwrite, duplicate save, swallowed error, stale candidate | delayed/rejected callbacks, cancel/no-request assertions, context invalidation and latest-version integration |
| Protected states | Historical canceled purchase becomes editable; unrelated working copy overlocked | active/canceled linked purchase matrix and confirm-time gate; backend lock regressions |
| Dirty details | Editor hides unsaved edits; autosave races mutation | canonical save/discard/leave and legacy dirty/invalid/pending/conflict tests |
| Rental | Public search added; wrong interval identity; driver validation lost | public/private spy assertions, age boundary, interval/billing/timezone cases |
| Accessibility/style | Disabled focus target, hidden actions, duplicate IDs | keyboard/ARIA/dialog tests, VisualSystem contracts and optional rendered widths |
| Adjacent flows | Auth partial-save recovery, management/history or booking semantics regress | full existing frontend/backend suites, updated label assertions |

## Existing Coverage and Environment Constraints
Frontend uses Vitest 4, React Testing Library, user-event and jsdom via `frontend/vite.config.ts` and `src/test/setup.ts`. Existing tests mock `tripsApi` with spies and deterministic sanitized option/plan fixtures. IndependentTripPlans includes separate dates/party and canonical dispatch; ProgressiveTripBuilder chiefly exercises legacy shapes. PublicTripFlow tests login/sequential save/retry/partial success. Booking/confirmation tests cover conflict and pending behavior. VisualSystem reads style.css and uses regex contracts; jsdom does not calculate actual rendered geometry.

IndependentTripPlans existing cases render empty plans without mocking active searches. Default-active-search changes require safe search mocks in those fixtures; do not allow unintended fetches or unexplained async errors. Mock history calls as existing tests do. Use realistic fee-inclusive option/component fields, not type-cast partial prices that accidentally mask missing evidence. Older-shape fixtures deliberately lacking tally/metadata must assert explicit unavailable handling or supply complete validated snapshots.

Backend tests use JUnit/Spring MockMvc, isolated in-memory H2 UUID URLs and TestClockConfiguration; IndependentTripPlanIntegrationTest and category/pricing/public suites already cover owner isolation, versions, frozen purchases, rental validation and totals. No backend production edit is planned, so run existing coverage rather than mirroring server tests with new frontend-only backend changes.

Commands need installed Node/npm and Java 21; Maven generate-resources runs npm ci/build and can require package/cache access. Dependency/cache restrictions are environment failures, not passing evidence. Do not change defaults to a persistent development H2 URL. Routine frontend doubles and Maven test databases require no credentials or live supplier/model/payment services. README's isolated packaged verifier exists, but is not required for this frontend-only ticket after clean verify; it proves packaging/persistence rather than new browser interaction. No new browser-test dependency/profile is presumed.

## Failing Test First
- Name: `opens a usable flight search for a newly saved empty plan`.
- Type: component interaction test exercising canonical workspace dispatch.
- Location: `frontend/src/IndependentTripPlans.test.tsx`.
- Arrange: canonical fixture with empty selected primary plan, known tally zero, two saved travelers; mock searchAirfare/history with deterministic data.
- Act: render TripWorkspace with generic entry and do not click a tab.
- Assert: exactly one Build your plan heading; compact saved context; Search flights selected with linked visible flight panel/search call; no Compare more options or choose-search hint; collapsed editor; one compact summary containing missing components and server-backed partial zero.
- Expected pre-fix failure: Build your plan is absent and initialSearch is null, so no active flight search occurs. Assert this intended behavioral failure, not a missing import/new file failure.

## Tests to Add or Update

### 1. `validates full component totals and comparison bases`
- Type: pure unit tests, parameterized.
- Location: new `frontend/src/components/planSearchComparison.test.ts`.
- Proves: correct signed delta, same price, valid zero and unavailable evidence; AC3.
- Inputs: party of 3, two round-trip legs with base/tax/fee; 4 stay nights and 2 rooms; rental 24h versus 24h+1min. Option unit price deliberately differs from full total. Selected canonical tally differs from top-level primary tally to catch wrong source.
- Isolation: no API, use sanitized full metadata and integer cents.
- Edges: missing selected/option totals, NaN/Infinity/negative/incomplete fees/empty nights/invalid dates; unmatched flight dates/party or stay rooms/context; valid zero; purchased party distinct from current party. Do not show delta for missing selection. Rental same unit+same instants is Selected, different interval is a replacement with disclosed complete old/new bases; unavailable if interval evidence is invalid.

### 2. `uses one layout for every selection combination`
- Type: React component tests.
- Location: new `frontend/src/components/TripComparisonPage.test.tsx`.
- Proves: AC1/2/8; one heading, compact context and summary, current category reference only, concise guidance and details access.
- Inputs: eight saved flight/stay/car presence masks, unique titles/full amounts/tally; guest flight/stay masks with local not-yet-saved language. Server partial true/false, missing partial older shape, zero and absent tally.
- Isolation: mock all category endpoints; inspect named regions via within rather than global duplicate-text counts.
- Edges: absent metadata remains readable, car optional label versus server partial, no duplicated large cards/missing sections, guest no rental tab or rental endpoint, partial summary reflects selected server total rather than guessed sum.

### 3. `chooses available initial search and operates tabs by keyboard`
- Type: component interaction tests.
- Location: `frontend/src/components/TripComparisonPage.test.tsx`.
- Proves: AC1/6/7/9; AIRFARE/STAY hints respected, generic flights default, locked-flight fallback to stays, both locked fallback to eligible rental, all locked/canceled no unusable search.
- Inputs: category permission matrix, pending/dirty flags and guest capability boundary.
- Isolation: private/public endpoint spies and user-event keyboard.
- Edges: ArrowLeft/Right wrap, Home/End skip disabled tabs, one roving stop, focus does not select until Enter/Space, correct aria-controls/labelledby and unique panel IDs. Change flight/stay/car activates corresponding category and focuses reachable search; no mutation. Purchased labels/details remain visible even if category is unsearchable.

### 4. `renders accurate deltas and selected actions in all result cards`
- Type: component/search integration tests.
- Location: `TripComparisonPage.test.tsx`; relevant cases in `frontend/src/ProgressiveTripBuilder.test.tsx`.
- Proves: AC3/4; current reference above results, fee-inclusive totals and same/less/more/unavailable differences; Selected disabled versus Replace or Save/Add language.
- Inputs: same and alternative flight pair IDs/guest combinationKey, stay unit/requiredRooms, rental unit+interval; multiple travelers, multiple rooms and multi-cycle rental.
- Isolation: return distinct realistic options from search spies and examine actual result articles.
- Edges: selected flight requires both leg IDs; rental same unit with different valid interval offers Replace car, not Selected. Retain full/per-traveler, full/per-room and daily display prices but compare only full totals. No valid comparison must say unavailable, never $0 difference.

### 5. `confirms replacements and retains old choices until successful save`
- Type: shared and workspace interaction tests, parameterized for all three categories.
- Location: `TripComparisonPage.test.tsx`, `frontend/src/IndependentTripPlans.test.tsx`, legacy cases in `ProgressiveTripBuilder.test.tsx`.
- Proves: AC4/7; old/new names, totals, basis and delta/unavailable shown; browsing/cancel/Selected send no PUT; missing selection adds without replacement dialog.
- Inputs: old selection and server tally, candidate, deferred result, rejected error/conflict and a successful updated response with deliberately distinct server grand total.
- Isolation: selectAirfare/selectStay/selectRental spies and deferred promises; assert request category, identity, interval, saved plan ID and current versions.
- Edges: repeated confirm sends one request; pending blocks dismissal/tab/navigation; failed save retains old choice/total and retry/cancel/reload available; success closes and uses returned total. Context/plan/selection reload invalidates candidate. If lock/canceled/dirty context changes while dialog exists, confirm cannot mutate. Focus enters dialog, traps Tab/Shift+Tab, Escape cancels when idle and restores connected trigger/fallback.

### 6. `keeps saved context and explicit edit protections independent per plan`
- Type: workspace interaction tests.
- Location: `frontend/src/IndependentTripPlans.test.tsx`.
- Proves: AC2/5; selected nonprimary plan uses its own dates/party/selections/tally, compact disclosure starts collapsed, dirty edits remain visible and searches use saved context.
- Inputs: primary and nonprimary with distinct dates, parties, selections, tally and versions; save result advances both versions and alters/revalidates choices.
- Isolation: savePlan/search/mutation spies, mocked history, deferred save/background refresh.
- Edges: dirty add/replace/remove blocked; Save/Discard/Cancel and beforeunload/parent/history guards remain; failed/invalid save keeps edit values; changing inputs during save is preserved. After save/discard, refresh results and reconfirm rather than applying old candidate. Actual mutation reads new versions, including a deferred guarded action path. Plan switch resets candidate/search appropriately, changes view without mutation, retains independent contexts. Update existing editor tests to open disclosure instead of deleting protections.

### 7. `retains purchased context and read-only restrictions`
- Type: frontend workspace tests plus existing backend suites.
- Location: `IndependentTripPlans.test.tsx`, `ProgressiveTripBuilder.test.tsx`, existing `src/test/java/app/detour/trip/IndependentTripPlanIntegrationTest.java`.
- Proves: AC7; historically locked active and canceled purchases cannot change/remove/replace, even after plan edits; frozen dates/party/component prices remain readable.
- Inputs: purchased party 2 but current party 3, differing dates, linked booking selection/tally, partial purchased categories plus editable missing component, canceled trip and all-locked plan.
- Isolation: mocked component mutation spies must remain uncalled; history returns linked canceled purchase for legacy where not supplied directly.
- Edges: unknown linked purchase metadata/loading cannot unlock a known booked option; unrelated unconfirmed working copy remains editable. Confirmation after state update cannot bypass locks. Backend existing cases recheck owner/version/locks and frozen-price semantics unchanged.

### 8. `preserves rental eligibility interval and search state recovery`
- Type: category/workspace interaction tests.
- Location: `ProgressiveTripBuilder.test.tsx`, shared `TripComparisonPage.test.tsx`.
- Proves: AC6/9; integrated saved car panel keeps qualification and local/ISO interval handling; all category loading/error/retry/empty/filter behavior survives.
- Inputs: unknown ages, only age 24, one age 25; missing/reversed/out-of-plan interval; exactly 24h and extra minute; SFO/MUC/MEX local offsets within supported dates; server eligibility/selectionDisabled explanation.
- Isolation: searchRentals spy and deferred/error/empty responses; spies for other categories. Fake timers only for legacy autosave and reset afterward.
- Edges: invalid interval prevents search/select; client and server eligibility block select; stale category/filter/context response cannot appear or change summary after switching; retry succeeds. Check each category loading, empty and failure/retry, preserving filter/sort/budget/room metadata, no duplicate heading/cancel in tabs.

### 9. `preserves guest handoff legacy safeguards management and booking purpose`
- Type: existing broad interaction suites with focused additions.
- Location: `frontend/src/PublicTripFlow.test.tsx`, `ProgressiveTripBuilder.test.tsx`, `IndependentTripPlans.test.tsx`, `ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/components/BookingConfirmationView.test.tsx`, `frontend/src/components/ConfirmationPending.test.tsx`.
- Proves: AC5/6/8; guest selections remain local until save/auth, failed partial save recoverable; no public rental. Legacy editor is compact but dirty/invalid changes visible, saved-context search and mutation gates prevent autosave races. Existing ConfirmRemoveModal still confirms removal and retains failure recovery.
- Inputs: existing login/session-expiry/partial-save fixtures; legacy dirty debounce, invalid ages/dates, pending autosave, conflict, successful latest save; retained management and booking fixtures.
- Isolation: existing API mocks and timer conventions; component requests cannot occur during dirty/pending/conflict, then use current version after successful save.
- Edges: create/copy/rename/primary/delete/compare/budget/history/review stay accessible; option comparison and independent-plan comparison distinct. Review uses reviewed plan totals; confirmation uses purchased snapshots/codes/history. Update Review booking/Your selections label queries without weakening missing-tally, inventory/conflict, pending/idempotency or cancellation assertions.

### 10. `preserves Golden Hour responsive and focus contracts`
- Type: CSS contract and semantic DOM tests.
- Location: `frontend/src/VisualSystem.test.tsx`, `TripComparisonPage.test.tsx`.
- Proves: AC2/9 and active guardrail; desktop minmax search/sidebar grid, narrow one-column reflow, min-width/wrapping, essential information/actions stay in DOM; inherited focus and forced-colors/reduced-motion contracts.
- Inputs: style.css plus summary/current reference with long property/plan names and fully populated states.
- Isolation: existing fs CSS test convention and jsdom; no claim of pixel-layout verification.
- Edges: preserve all original regex contracts and intended button variants/rows; tabs use roles, filters aria-pressed; no hidden essential summary/action rule at phone breakpoint. Build catches type/prop mismatches.

## Safe Verification Commands
Run PowerShell commands with the stated working directory; `npm test` is already `vitest run`.

- Red/focused, working directory `frontend`: `npm test -- src/IndependentTripPlans.test.tsx -t "opens a usable flight search for a newly saved empty plan"`.
- Helper/shared, working directory `frontend`: `npm test -- src/components/planSearchComparison.test.ts src/components/TripComparisonPage.test.tsx`.
- Related suite, working directory `frontend`: `npm test -- src/IndependentTripPlans.test.tsx src/ProgressiveTripBuilder.test.tsx src/PublicTripFlow.test.tsx src/ItineraryComparisonAndBookingReview.test.tsx src/components/BookingConfirmationView.test.tsx src/components/ConfirmationPending.test.tsx src/VisualSystem.test.tsx`.
- Full frontend gate, working directory `frontend`: `npm test`.
- Type/build gate, working directory `frontend`: `npm run build`.
- Full safe suite and packaging, repository root: `.\mvnw.cmd clean verify`.
- Scope/whitespace, repository root: `git diff --check` and `git status --short`.

Implement the red test against current source first and record intended failure before edits. Run focused tests during phases, then full gates once changes settle; repeat after failures/fixes as needed. No live/default application launch, real database migration, external services or credentialed operation is part of these commands. Do not claim Maven executes Vitest: npm test is a separate required gate.

## Optional Developer Checks
At approximately 1440px, 820px and 390px, inspect guest and canonical saved empty/partial/populated layouts, then locked/canceled state and review/confirmation. Observe compact context/editing, readily reachable search, summary/current reference/full totals, long text, visible focus and readable dialog actions. If a browser is available, use disposable local data and the documented non-production conventions, never the default development database. These observations are nonblocking; report any actual browser evidence and explicitly identify an unperformed visual check. CSS/DOM checks support responsive structure but do not prove rendered pixels.

## Acceptance-Criteria Evidence Map
| Criterion | Required executable evidence |
| --- | --- |
| AC1 | red canonical entry test; default/hint/fallback shared tests |
| AC2 | eight selection masks, selected nonprimary server tally, partial/zero/unknown tests, CSS reflow/DOM essential content |
| AC3 | pure basis/price/identity matrix plus three actual result-card/reference tests |
| AC4 | change/add/remove, category replacement cancel/pending/failure/success and returned server totals |
| AC5 | compact editor/dirty visibility, save/discard/leave, independent context/current-version and legacy gates |
| AC6 | saved rental driver/interval/lock tests, guest endpoint and auth/save regressions |
| AC7 | active/historical canceled purchase/frozen context/read-only/confirm-time guards and existing backend locks |
| AC8 | guest/canonical/legacy labels and retained management/comparison/budget/history/booking suites |
| AC9 | keyboard/ARIA/focus/dialog, category loading/stale/error/retry/empty, CSS contracts and full build |

## Exit Criteria
- [x] Planned red test fails on current source for missing default search/heading before implementation.
- [x] New/updated tests pass; deliberate label/layout changes do not erase previous safeguard coverage.
- [x] Full frontend tests, frontend build and root clean verify pass; material failed checks are resolved or escalated with exact evidence.
- [x] Every acceptance criterion maps to executable evidence above.
- [x] All three category replacements preserve current state on cancel/failure, commit successful result only, and cannot bypass current locks/dirty state.
- [x] Full fee-inclusive comparison, valid-zero/unknown distinctions, selected plan/purchased context and interval identity are proved.
- [x] Guest, canonical and supported legacy entry paths and adjacent auth/management/booking regressions are covered.
- [x] Routine automated tests use doubles/disposable databases and no unintended live/destructive operations.
- [x] Golden Hour CSS/keyboard/focus/reflow evidence passes; optional pixel checks are recorded accurately as nonblocking and never claimed performed when absent.
- [x] Diff remains frontend/ticket-scoped and preserves unrelated committed trip-name baseline; independent Step 5 review still required by full profile.

## Step 4 verification receipt

The implementation plan's Step 4 observations record the exact red-to-green command, material decisions, final acceptance evidence map and resolved intermediate/environment failures. Final settled commands: frontend `npm test` passed 282/282 tests in 20 files; frontend `npm run build` passed; root `.\mvnw.cmd clean verify` passed 221 backend tests with zero failures/errors/skips and packaged frontend; root `git diff --check` passed. Full frontend execution includes each planned subset suite; subset checkbox completion is backed by that full gate. No live/default application was launched. Optional rendered 1440px/820px/390px checks were not run and remain nonblocking; CSS/DOM assertions do not prove pixels. Fresh independent Step 5 review remains required.

