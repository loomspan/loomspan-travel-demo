# Promote Plan Search Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-10-02-promote-plan-search.md`
- Research: `ai/thoughts/research/2026-10-02-promote-plan-search.md`
- Execution: Steps 2 and 3 of the Full 5-Step Pipeline, selected profile `full`.
- Outcome: One search-first Build your plan surface across guest, independent-plan, and supported legacy views, with a compact selection summary and deliberate, recoverable replacements.

## Current State
- `frontend/src/components/TripComparisonPage.tsx` owns shared flight/stay tabs, large selected cards above results, Explore your trip/Compare more options headings, and nullable initial search. Rentals, deltas and replacement confirmation are absent.
- `IndependentPlansWorkspace.tsx` owns selected-plan context, explicit Save/Discard, `guard`, `save`, `mutate`, purchased locks, plan management and server `plan.tally`. Component callbacks capture render-time `revision`; deferred callbacks must not reuse that after detail saves. `mutate` returns boolean, but `component` discards it.
- `TripWorkspace.tsx` dispatches canonical responses to independent plans. Its legacy branch uses `activeDraft`, serialized debounced `executeAutosave`, component handlers that catch failures, separate tally/RentalSlot and ConfirmRemoveModal. Component pending props omit dirty edits and lock context.
- `GuestTripExplorer.tsx` holds option objects locally through `App.tsx`; login/save persists sequentially and retains partial progress on failure.
- Option totals are fee-inclusive integer cents: flight `partyTotalPriceCents`, stay/rental `totalPriceCents`. Existing legacy calculators return zero for missing components; this is insufficient comparison evidence.
- `ItineraryTallyEngine.calculateTally` returns `partial` when any component is absent. TypeScript `ItineraryTallyResponse` omits this existing field. Canonical top-level tally aliases describe primary, not the viewed nonprimary plan.
- At planning start only the research artifact is untracked. HEAD `5963e78` includes unrelated trip-name work and ticket assets; preserve baseline.

## Desired End State
One Build your plan heading, compact saved context, usable initial search, desktop search/Your selections grid and phone reflow. Honor flight/stay entry hints when unlocked; otherwise flights, stays, then eligible cars. Canceled/all-locked state opens no unusable panel. Locked categories remain visibly unavailable and purchased choices retain historical context.

Summary rows expose concise names, full prices, dates/party or rental interval, missing badges, details disclosures and editable Change/Remove actions. Category reference stays above results. Exact selected options say Selected without redundant replacement; alternatives say Replace flight/stay/car, or Save flight/stay/car to plan when missing. Guest actions say Add flight/stay and disclose not yet saved.

Replacement identifies old/new choices, amounts/bases and delta or Comparison unavailable. Cancel/browse make no mutation; failure retains the old choice and recovery. Summary updates only from successful server response (or deliberate guest local update). Existing auth, versions, ownership, booking/history, removal and saved-detail protections remain.

## Scope
### In scope
- Shared explorer, compact editing, integrated authenticated rental tab, compact summary/reference, full-total differences, replacement dialog, keyboard handling and entry adapters.
- Necessary frontend safeguards for dirty edits, latest versions, historical locks, save-result signaling and stale candidate invalidation.
- Booking review/confirmation language and heading typography, retaining detailed review/purchase purpose.
- Focused regression tests, CSS contracts, frontend build and existing full safe verification.
### Out of scope
- Backend/API/schema/catalog changes, public rental search, purchase/inventory semantics, supported dates, model services and trip-name formatting.
- Rewriting management, independent-plan comparison, auth/save handoff or legacy autosave beyond required protections.

## Active Project Guardrails
The design lens's Shared Golden Hour visual design system applies. Follow `frontend/DESIGN.md`: tokens, `.card`, intentional button variants/rows, badges/callouts and PlanDialog; one primary per region. Filters remain segmented controls and panel switches are tabs. Screen styles go in section 11 of `frontend/src/style.css`, responsive rules in 12, accessibility in 13. No inline styles or literal colors. Preserve VisualSystem regex contracts, visible focus, reduced motion, forced colors and usable reflow near 390px.

## Impact and Risk Analysis
- **Wrong plan context:** Pass viewed plan dates/party/selections/tally explicitly. Historical context applies to locked components; top-level aliases and a different plan's purchases are not authoritative.
- **Invalid prices:** Missing, nonfinite, negative or incomplete evidence is unavailable, not zero. Real zero remains valid. Compare full fee-inclusive component totals with declared basis, never display unit rates.
- **Partial semantics:** Keep existing server partial definition. Car is optional, while partial means selected-component subtotal, including server partial for an absent car. For older shapes lacking partial, derive from missing selections. Do not redefine backend completeness.
- **Stale mutations:** Detail saves can reprice/drop selections and advance versions. Do not dispatch pre-save candidates. Recheck lock/dirty/pending/canceled/context at confirm; invalidate candidates on relevant save/reload/switch. Read versions at dispatch.
- **Failure recovery:** Parent catches currently resolve void on failure; shared UI needs explicit success. Dialog closes only on success, with no optimistic summary. Preserve conflict reload and authentication recovery.
- **Legacy locks:** Protect only the linked active draft/plan, not an unrelated working copy because another saved option has purchases. Canceled booking history remains locked. Dirty/invalid/saving/conflicting details prevent mutations.
- **Accessibility:** Follow PlanNavigation manual activation; focus movement and panel selection are distinct. PlanDialog traps/restores focus, gates pending dismissal; do not nest edit-guard and replacement dialogs.

## Implementation Approach
Extend TripComparisonPage as the shared presentation owner, with optional rental capabilities. Workspace adapters continue owning trip state, requests and permissions. Extract small summary/details and pure price/identity helpers rather than duplicating explorers or moving persistence into them.

Return explicit success/failure from explorer selection adapters, using a boolean promise. Existing standalone category callers may keep void callbacks via wrappers. A discriminated candidate stores option/interval and originating context; confirm invokes the adapter, missing selections invoke it directly, and guests deliberately replace local choices with the same confirmation language.

Prefer valid selected-plan component tally (or relevant purchased tally) for selected price. Legacy responses without tally can use validated complete snapshots; malformed snapshots remain unavailable. Guest choices use valid option totals. Flight basis is saved round trip and party; stay basis is saved dates and required rooms. Rental identity is unit plus normalized pickup/return instants. Different old/new rental intervals can be compared as full replacement amounts only with both intervals disclosed, never as a same-duration rate comparison. Incompatible flight/stay basis or missing basis metadata is unavailable. Prospective plan totals require a valid grand total and delta; actual saved totals remain server authority.

## Phase 1: Shared search-first layout and comparison evidence
### Changes
- [x] `frontend/src/components/TripComparisonPage.tsx` — AIRFARE/STAY/RENTAL state, available-category default/fallback/read-only, compact context, one Build your plan heading, search/sidebar layout. Remove Compare more options and duplicated large-card region. Guests have no rental capability.
- [x] `frontend/src/components/PlanSelectionsSummary.tsx` (new) — concise flight/stay/optional-car rows, missing/zero/unknown distinctions, server full/partial total, useful metadata and details disclosures, historical labels, Change/Remove/save callbacks.
- [x] `frontend/src/components/planSearchComparison.ts` (new) — validated totals/bases, identities, signed deltas and unavailable state. Preserve existing ItinerarySummaryTally exports and missing-zero convention for other consumers.
- [x] `frontend/src/api/tripsApi.ts` — optional `partial?: boolean` on ItineraryTallyResponse, reflecting an existing field without API changes.
- [x] `frontend/src/components/AirfareSearchSection.tsx`, `StaySearchSection.tsx`, `RentalSearchSection.tsx` — optional comparison/action inputs, full-total delta/unavailable and Selected state. Rental identity includes interval, supports inTabs without redundant heading/cancel, retains driver/interval/filter/retry controls and server selectionDisabled evidence.
- [x] `frontend/src/components/TripComparisonPage.test.tsx`, `planSearchComparison.test.ts` (new) — initial/fallback searches, summary combinations and price/identity/basis edge cases.
### Automated verification
- [x] From `frontend`: `npm test -- src/components/TripComparisonPage.test.tsx src/components/planSearchComparison.test.ts` — mocked shared presentation and full-total behavior pass.
- [x] From `frontend`: `npm run build` — interfaces compile.
### Optional developer checks
- None for this phase.

## Phase 2: Deliberate replacements and independent-plan integration
### Changes
- [x] `frontend/src/components/TripComparisonPage.tsx` — candidate state and PlanDialog identifying old/new details/full totals/bases/delta; cancel and tab browsing send no request. Exact current choice cannot replace itself. Pending blocks duplicate submit/dismissal; false/rejected outcome retains choices/error recovery. Clear candidate when context/selection/permissions change.
- [x] `frontend/src/components/IndependentPlansWorkspace.tsx` — default-collapsed edit disclosure above search; compact saved context/dirty warning remain visible. Keep disclosure open while dirty. Preserve save/guard/browser/parent Save/Discard/Cancel. Pass selected plan tally, selections, purchase context, locks and rental adapter; remove separate RentalSlot/Add a car/missing paragraphs and redundant total.
- [x] `IndependentPlansWorkspace.tsx` `component`/`mutate` — boolean outcome, clear stale error after success, ref-based current plan/parent versions at execution. Disable mutation entries while dirty; defensive dirty guard finishes save/discard then refreshes search/confirmation instead of applying a candidate from previous saved context. Deferred work is not reported successful before completion.
- [x] `frontend/src/IndependentTripPlans.test.tsx` — open disclosure in editor tests; cover nonprimary context/tally, replacement recovery, fresh versions, dirty protections, active/historical locks, all-locked fallback, management/history and pending guards.
### Automated verification
- [x] From `frontend`: `npm test -- src/IndependentTripPlans.test.tsx src/components/TripComparisonPage.test.tsx` — successful-server-only changes, independent context, locks and versions pass.
### Optional developer checks
- None for this phase.

## Phase 3: Guest/legacy parity and booking language
### Changes
- [x] `frontend/src/components/GuestTripExplorer.tsx` — valid compact destination/route metadata from existing draft/destination definitions, local totals and boolean adapters. Exclude rental and persisted language. Preserve App login/sequential save/retry/partial-progress flow; only adapt props if necessary.
- [x] `frontend/src/components/TripWorkspace.tsx` legacy branch — working saved context/tally, integrated rental and result-returning handlers; remove standalone planning tally/RentalSlot/Add a car but retain option-save/update/compare controls. Compact editing disclosure keeps dirty/invalid edits visible and autosave/status/reload intact. Block add/replace/remove during dirty/invalid/saving/conflict, with saved-context warning and defensive handler checks. Re-enable only on current saved state. Retain ConfirmRemoveModal and removal recovery.
- [x] `TripWorkspace.tsx`, and if needed `frontend/src/components/BookingHistorySection.tsx` — match purchases including canceled history by active plan/draft ID, derive per-component locks and frozen metadata/tally; reuse existing getBookingHistory data through a parent callback if required. Known booked options with unresolved history must not become replaceable/removable while loading. An unconfirmed working copy of another booked option remains editable. No new endpoint.
- [x] `frontend/src/components/BookingReviewView.tsx` — Review booking / Your selections and Flight/Stay/Car language; keep reviewed plan identity, detailed snapshots, totals, pending/conflicts/auth and booking action.
- [x] `frontend/src/components/BookingConfirmationView.tsx` — Your selections and purchased-context language using shared typography; preserve references, codes, frozen prices, canceled history and navigation.
- [x] `frontend/src/PublicTripFlow.test.tsx`, `ProgressiveTripBuilder.test.tsx`, `ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/components/BookingConfirmationView.test.tsx`, `frontend/src/components/ConfirmationPending.test.tsx` — update intentional heading/action/order changes while retaining underlying behavior; add entry/lock/recovery regressions.
### Automated verification
- [x] From `frontend`: `npm test -- src/PublicTripFlow.test.tsx src/ProgressiveTripBuilder.test.tsx src/ItineraryComparisonAndBookingReview.test.tsx src/components/BookingConfirmationView.test.tsx src/components/ConfirmationPending.test.tsx` — guest handoff, legacy protection/removal and booking flow pass.
### Optional developer checks
- None for this phase.

## Phase 4: Golden Hour reflow, keyboard/focus and full verification
### Changes
- [x] `frontend/src/components/TripComparisonPage.tsx` — ArrowLeft/Right wrap, Home/End focus enabled tabs; Enter/Space activate, one enabled roving tab stop and linked panels with unique useId IDs. Change actions activate matching category and focus/bring search into view without bypassing guards.
- [x] `frontend/src/style.css` — shared explorer section for compact context/summary/reference/delta, desktop minmax search/sidebar grid, single-column reflow at existing sidebar breakpoint, phone action wrapping, min-width:0 and long-content wrapping. Keep essential details/actions rendered; tokens and primitives only, responsive/accessibility in sections 12/13.
- [x] `frontend/src/VisualSystem.test.tsx` and shared tests — preserve regex contracts; focused grid/reflow/focus CSS evidence and DOM essential-content assertions; disabled-tab navigation, confirmation focus/pending/recovery.
### Automated verification
- [x] From `frontend`: `npm test` — complete interaction/accessibility/style suite passes.
- [x] From `frontend`: `npm run build` — TypeScript/Vite production build passes.
- [x] From root: `.\mvnw.cmd clean verify` — full safe backend suites plus packaged frontend compile.
- [x] From root: `git diff --check` — no whitespace defects; inspect ticket scope against baseline.
### Optional developer checks
- Render guest and saved empty/partial/populated plans near 1440px, 820px and 390px in a disposable local fixture/non-production environment. Observe search prominence, context/totals/current reference, dirty visibility, long text, actions, focus/dialogs and locked/canceled state. Do not launch the default development database. Vitest/jsdom and CSS contracts do not measure actual layout pixels; report observations actually obtained and any limitations.

## Test Strategy
Pure comparison-helper tests, shared component/dialog tests, existing workspace integration tests for context/versions/auth/locks, CSS contracts and existing backend suites for unchanged security/persistence/purchases. Mock newly automatic search requests in every affected frontend fixture. No live supplier/payment/model services or production data. Detailed cases and safe commands are in the testing plan.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| AC1 empty active search, compact context, single heading | TripComparisonPage, editor disclosure | defaults/entry/fallback and empty canonical entry |
| AC2 selection combinations, selected server totals, phone/desktop | PlanSelectionsSummary, selected tally, style sections 11/12 | eight saved masks; zero/unknown/partial tests; VisualSystem and optional widths |
| AC3 reference/identity/full deltas/unavailable | helper and all search sections | fee-inclusive round-trip/multiroom/rental interval, selected identity and invalid basis cases |
| AC4 change/add/remove/confirm cancel/failure/success | shared dialog and workspace adapters | three-category delayed/failing saves, no-request browsing/cancel, existing removal safeguards |
| AC5 compact independent edits, saved context/unsaved protections | disclosure/guard/ref versions, legacy gate | dirty save/discard/leave, nonprimary context and fresh versions |
| AC6 saved rental eligibility/interval/locks, guest auth/no rental | integrated rental, guest adapter, unchanged App | age/bounds/timezone/interval identity and PublicTripFlow endpoints/handoff |
| AC7 historical locks and canceled restrictions | explicit permissions, frozen context, confirm checks | active/canceled purchases after detail edits, stale candidate, all locked; backend locks |
| AC8 consistent entry/booking language, management/history access | adapters, booking labels | guest/legacy/canonical tests, retained management, booking/confirmation suites |
| AC9 design/keyboard/focus/reflow/search states | tabs/PlanDialog/CSS/category effects | keyboard/focus, loading/stale/error/retry/empty, VisualSystem, build and optional widths |

## Risks and Rollback/Recovery
No migrations or new persisted state. Revert ticket-scoped frontend edits to restore prior UI while preserving baseline trip-name work. Failed saves retain previous response and recovery; conflicts require existing reload and fresh search/confirmation, auth expiry uses existing login/retry. Never fabricate missing comparisons or override backend rejection. If implementation needs API/purchase-semantic changes, stop for scope reassessment.

## Implementation observations and evidence (Step 4)

- Shared presentation now supports authenticated rental capabilities, current references and one compact summary. Guest choices remain local; the guest Build your plan heading retains the existing application's heading-focus handoff. Saved plan totals use the viewed plan's server tally; full/partial uses the existing optional server field, with missing-selection fallback for older responses. Unknown amounts remain unavailable; a genuine zero remains zero.
- Selection adapters return boolean success. A replacement keeps its previous choice and candidate on failure and updates only from a successful parent response. Missing components save directly. Context fingerprints invalidate candidates on saved context, version, selection or permission changes; parent pending state invalidates externally pending candidates without erasing a failed request's recovery dialog. A ref guards duplicate submissions.
- Independent-plan component mutations reject dirty/current-plan/lock/canceled mismatches instead of deferring an old candidate through a detail-save guard. Requests read current parent and plan versions at dispatch. After save/discard, search refetches on version changes and the user chooses/confirm a fresh candidate. Existing leave, Save/Discard/Cancel and management guards remain in their owners.
- Compact detail disclosures start collapsed and retain the user's open preference; dirty/invalid edits force visibility. Legacy autosave and conflict recovery remain, with defensive dirty/invalid/saving/conflict/lock gates on selection and removal. The saved context above search remains unchanged until the save succeeds.
- Legacy purchase locks match the active draft/plan ID against active booking, trip booking and existing booking history. A known booked option locks conservatively until history resolves. Canceled history retains component locks and purchased dates/party/prices; an unrelated unconfirmed working copy stays editable. The existing history fetch supplies parent evidence through an optional callback; no endpoint or purchase contract changed. Resolving history opens a newly available search category.
- Comparison helpers require safe nonnegative integer cents and complete snapshot fee evidence. Flight comparisons require saved and prospective local dates plus party; stay comparisons require saved and prospective full dated nights plus required rooms; rental identity uses unit and normalized interval instants. Different valid rental intervals disclose both full bases rather than claiming equal-duration rates. Invalid or incompatible evidence says Comparison unavailable.
- New deterministic fixtures are shared through planSearchFixtures.ts, never through importing test modules. Existing label/order/automatic-search fixtures were adapted without removing auth, conflict, removal, pending, management or purchase assertions. No backend production files, catalog, default database or committed trip-name baseline changed.
- Golden Hour styles use existing tokens/primitives, min-width and wrapping plus desktop search/sidebar grid and 1050px one-column reflow. Tabs retain manual activation, enabled roving focus and unique panel linkage; replacement dialogs trap and restore focus, including pending/retry recovery. Static CSS/DOM evidence supports structure but does not measure rendered pixel geometry.

### Actual verification

- Before production changes, from frontend: `npm test -- src/IndependentTripPlans.test.tsx -t "opens a usable flight search for a newly saved empty plan"` failed for the intended absent Build your plan/default active search (1 failed, 28 skipped). After implementation the same test passed (1 passed, 28 skipped).
- Focused helper/shared and workspace/guest/legacy/booking suites were run during implementation. The complete final `npm test` from frontend passed **282 tests in 20 files**, zero failures, on the settled code. This full run includes every phase's named suite and the unchanged confirmation/pending tests; phase checkboxes reflect this executable evidence, not a claim that every listed subset command was rerun independently.
- Final `npm run build` from frontend passed TypeScript and Vite (58 modules). Final root `.\mvnw.cmd clean verify` passed **221 backend tests**, zero failures/errors/skips, bundled the frontend and produced the executable JAR. Compilation targets Java 21; the available runtime was Java 25. Tests used frontend doubles and isolated UUID in-memory H2 databases; no live/default application launch or external supplier/payment/model operation occurred.
- Root `git diff --check` passed after removing introduced EOF blank lines. Ticket diff consists of frontend components/types/styles/tests and the two governing plans; the prior research artifact belongs to earlier pipeline stages. Temporary Vitest JSON reports were removed from the workspace before handoff. Independent Step 5 review is still required by the selected full profile.
- Earlier broad frontend runs failed on changed action/heading/tally selectors, newly automatic search fixture ordering and a DST interval fixture; those fixture failures were corrected. The first broad run was 203 passed/24 failed; later 279 passed/2 failed, then 281 passed/0 failed before the final lock-release regression raised the settled total to 282. A same-option comparison fixture incorrectly carried an alternative price and was corrected. Two new test fixture TypeScript errors (undefined ages and a null active-booking mock) were corrected before final build.
- Initial root `.\mvnw.cmd clean verify` failed because the sandbox could not use the existing C:\.m2 cache; authorized escalation resolved this. A later Maven attempt failed typecheck for the two fixture errors above. Running npm ci while Vitest held Windows' native rolldown binding caused EPERM and removed the Vitest shim, so a repeat `npm test -- --reporter=json --outputFile=.ticket-full-results.json` reported command unavailable. Sequential clean verify restored dependencies and passed; final npm test/build and a final sequential clean verify all passed. No automatic approval rejection occurred.
- Optional rendered inspection at 1440px, 820px and 390px was **not run**. It remains a nonblocking developer observation in a disposable environment; no pixel-level responsive claim is made.

### Acceptance evidence obtained

| Criterion | Executable evidence in the passing final gates |
| --- | --- |
| AC1 | IndependentTripPlans canonical red-to-green entry; shared default/hint/locked fallback and history-release test |
| AC2 | Shared eight saved masks/four guest masks, zero/unknown/partial totals; nonprimary selected-plan server totals; VisualSystem grid/reflow contracts |
| AC3 | planSearchComparison complete-fee/party/rooms/dates/interval/invalid matrix; actual flight/stay/card comparison and exact selected flight/rental identity |
| AC4 | Shared three-category cancellation/failure/retry, pending duplicate guard, missing direct add, Change focus; canonical three-category returned-tally/current-version tests; existing legacy removal confirmations/recovery |
| AC5 | Canonical saved nonprimary context and dirty Save/Discard/leave tests; compact editor entry; legacy dirty/invalid/saving autosave/version integration |
| AC6 | Shared driver unknown/24/25, server selectionDisabled and reversed interval, changed same-car interval; legacy timezone helpers; PublicTripFlow auth/sequential-save/recovery and no public rental |
| AC7 | Canonical ACTIVE/CANCELED historical context; legacy linked ACTIVE/CANCELED history and unrelated-copy test; shared read-only/locks/candidate invalidation; existing backend purchase/version/ownership suites |
| AC8 | Full guest/canonical/legacy, plan management/comparison/budget/history/review/confirmation suites with only intentional language changes |
| AC9 | Shared manual keyboard/ARIA/focus/pending dialogs and category stale/loading/error/retry/empty; VisualSystem focus/reflow contracts; frontend build and packaged verification |

## References
- Ticket/research and both ticket assets; Search first is chosen, Guided start/sample catalog amounts are not requirements.
- `frontend/DESIGN.md`, `ai/thoughts/design-lens.md`, `AGENTS.md`.
- `frontend/src/components/TripComparisonPage.tsx`, `IndependentPlansWorkspace.tsx`, `TripWorkspace.tsx`, `GuestTripExplorer.tsx`, `PlanNavigation.tsx`, `PlanDialog.tsx`, `ItinerarySummaryTally.tsx`.
- `frontend/src/api/tripsApi.ts`, `frontend/src/style.css`, `frontend/package.json`, `frontend/vite.config.ts`, `pom.xml`, `README.md`.
- `src/main/java/app/detour/trip/ItineraryTallyEngine.java`, `TripService.java`; existing independent-plan/category/pricing/public integration tests.

