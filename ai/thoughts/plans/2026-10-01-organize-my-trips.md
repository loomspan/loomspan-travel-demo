# Organize My Trips Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-10-01-organize-my-trips.md`
- Research: `ai/thoughts/research/2026-10-01-organize-my-trips.md`
- Outcome: predictable, compact My Trips cards with authoritative inclusive date classification, explicit ordering, the existing four counted filters, and accessible secondary actions.
- Execution: pipeline mode, developer-approved `full` profile. No unresolved material product decision remains. This artifact plans implementation; no production/test edits or live operations were performed while preparing it.

## Current State
- `src/main/java/app/detour/trip/TripService.java`, `tripsProfile`: shared by `/api/trips` and `/api/profile`. Past currently requires Working AND every Saved option to have ended, although summaries display parent dates synchronized with Working. Independently dated Saved options can keep an already-ended displayed trip Upcoming.
- `src/main/java/app/detour/common/ClockConfiguration.java`, `PDX_ZONE`: Portland calendar dates govern inclusive end-date classification. Departure expiration separately starts at start-day midnight.
- `src/main/java/app/detour/trip/TripProfileSummary.java`: projects UPCOMING/PAST, counts, booking history, references and alternatives. `frontend/src/api/tripsApi.ts` mirrors it. `ProfileScreen` forwards temporalStatus and aggregate expiration into `TripWorkspace`, whose booking/departure guards must remain independent.
- `frontend/src/components/TripListSection.tsx`, `TripListSection`/`TripCard`: already isolates CANCELED from active arrays and implements four counts; renders server array order, status before name, raw dates, expanded Saved options and exposed secondary buttons.
- `frontend/src/components/ProfileScreen.tsx`: refreshes on My Trips navigation, history return, workspace return/update and mutations. `frontend/src/App.tsx` ignores stale profile responses and preserves authentication on refresh failures.
- `ConfirmDeleteModal` and `CancelTripModal` capture the previously focused element; removing menu items before opening these modals requires returning focus to a persistent trigger.
- Existing tests: MockMvc/H2 in `TripApiIntegrationTest` and `BookingCancellationIntegrationTest`; Vitest/Testing Library in App, FeeFreeCancellationAndTriage, ProgressiveTripBuilder and VisualSystem tests. No browser geometry test harness exists.

## Desired End State
- All renders Upcoming, Past, then Cancelled; each trip appears once in its appropriate status section. Counts retain existing definitions and filter semantics.
- Working/parent displayed dates govern date classification. Start <= Portland today <= end means in progress; today > end means Past. Saved options retain independent dates and expiration.
- In-progress active trips precede future active trips in Upcoming; both groups sort start ascending. Past sorts end descending; Cancelled sorts planned/displayed start descending. Each uses name A–Z then public trip ID as a deterministic final tie-breaker.
- Cards show name, destination/date range, compact status/booking/Saved-option counts, then Open trip. Every endpoint date includes a year. Detailed Saved options remain in the existing workspace.
- Secondary actions move to an accessible menu while preserving callback payloads, permission/availability predicates, confirmations, pending states and errors. Open trip remains directly available for every card including cancelled trips.
- Existing profile refresh paths recompute server dates and list ordering after persisted changes. No passive midnight timer or background refresh is required by the ticket.

## Scope
### In scope
- Shared summary classification and an additive server-derived `inProgress` boolean.
- My Trips presentation, deterministic local sorting, accessible action menu and menu-to-form/dialog focus.
- Targeted API, component, navigation/action and CSS regression coverage, including adjacent workspace consumers.

### Out of scope
- Independently editable/primary-plan model, persistence schema, migrations, supplier integrations, inventory or booking lifecycle changes.
- Changing departure expiration, broadening cancellation/rename/delete permissions, redesigning the workspace, adding sort controls/destination/month grouping.
- Background clock polling, tab/focus refresh, new browser-test infrastructure or unrelated fixture modernization.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`. Preserve repository patterns reconstructed from the inspected source.

## Impact and Risk Analysis
- Shared API: changing Past affects both authenticated projections and workspace badges. Preserve UPCOMING/PAST and arrays; add `inProgress` rather than an IN_PROGRESS enum value that existing workspace branches would interpret incorrectly. No persisted contract changes.
- Clock consistency: derive classification once from an instant captured at entry to `tripsProfile`, converted with PDX_ZONE. Use the same captured instant for option expiration within that projection to avoid a midnight request mixing two days. Keep public `isPast`/`isExpired` behavior and mutation guards unchanged; use private helpers accepting the captured date/instant if needed.
- Displayed dates: parent and Working synchronize through `JdbcTripRepository.updateWorkingDates`/option loading. Classify using the exact parent start/end values emitted by the summary; do not incorporate independent Saved dates into Past. Test the supported API edit path proving synchronization.
- Adjacent eligibility: Working already expired whenever it becomes Past; the aggregate expiration guard therefore already blocks the list/workspace general cancellation in the mismatch case. Booked-option-specific guards still use their independent option dates. Assert these facts rather than changing guards to use `inProgress`.
- Menu focus: modal restoration must target the persistent card trigger, not an unmounted menu item. Return focus synchronously before invoking cancellation/deletion callbacks. Rename should focus its input after mount and restore the trigger on dismissal/success. Removed-card modal fallback remains supported.
- Name/date sorting: locale-dependent sorting and raw input order are insufficient final tie-breakers. Use a fixed English case-insensitive name comparator and a lexical public-ID fallback; sort fresh arrays without mutating profile props. Duplicate date/name fixture values are appropriate at the component boundary even though owner name uniqueness prevents those duplicates through normal creation.
- Narrow screens: an absolutely positioned menu can escape card/view boundaries. Prefer an in-flow menu panel constrained to card width, wrapping long labels; preserve visible keyboard focus and forced-color states. jsdom cannot verify pixel geometry; retain focused optional browser observations.

## Implementation Approach
Ownership is split by existing responsibilities: the server owns time and summary classification; `TripListSection` owns list-specific grouping/order/presentation and action interaction. Add `inProgress` to each summary, true for ACTIVE trips whose displayed dates inclusively contain the request's Portland date, false for CANCELED/Past/future trips. Preserve existing Java constructor signatures as delegating overloads defaulting the new field to false; update the actual service constructor call to supply the computed value. Frontend type should declare the server contract (`inProgress: boolean`); update typed fixtures/summary builders needed to compile. Untyped historical mocks without the field may render it as false, but production must not fall back to browser time.

Keep the server's existing upcoming/past array ordering, avoiding a second list comparator and unnecessary endpoint ordering change. My Trips sorts active Upcoming/Past and merged Cancelled copied arrays locally using the authoritative field and ISO calendar dates. Workspace lookup is by ID, so list-only sorting has no effect on workspace state. Changing only frontend classification was rejected because the shared server status would disagree; a response-level today value was rejected because a per-summary boolean supplies the exact missing signal with less plumbing.

Use `Intl.Collator('en', {sensitivity: 'base'})` for name A–Z and a direct lexical ID comparison after collator equality. ISO strings compare chronologically. Human-readable dates use `Intl.DateTimeFormat('en-US', {month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC'})` on date-only midnight UTC values (never browser-local parsing/formatting); format both endpoints, including cross-month/year cases. A small local formatter/comparator in TripListSection is sufficient.

Use a named native menu button (`Actions for <trip name>`) with `aria-haspopup="menu"`, `aria-expanded`, `aria-controls`, a labelled menu and native button menuitems. Enter/Space/ArrowDown opens and focuses the first enabled item; ArrowUp can open on the last; ArrowUp/Down and Home/End move among enabled items. Escape closes and restores trigger focus. Tab closes without trapping or cancelling native focus advancement; outside interaction closes without stealing pointer focus. Disabled existing actions remain disabled with their explanatory title/accessibility text. Close before activating an action, restore trigger before dialog callbacks, and preserve form validation/pending behavior. Only one card menu should remain open after pointer/focus moves to another card; document listeners clean up on unmount.

Preserve the current rename availability, including cancelled cards where the server returns TRIP_CANCELED; this ticket preserves existing action behavior and does not silently introduce a new client authorization decision. Retain booking-history rather than booked-count checks: never-booked trips Delete, historical bookings Cancel with existing Past/aggregate-expiration restriction, cancelled cards disabled Trip canceled. Optional callbacks keep their current availability behavior.

## Phase 1: Authoritative summary dates

### Changes
- [x] `src/main/java/app/detour/trip/TripService.java` — in `tripsProfile`, snapshot clock instant/Portland date once; replace Working-plus-Saved Past aggregation with emitted trip end-date comparison; derive active inclusive `inProgress`; retain per-option departure expiration and counts with one captured instant.
- [x] `src/main/java/app/detour/trip/TripProfileSummary.java` — append boolean field, supply it from service, preserve existing constructor overloads with false defaults; retain name/label behavior and all old JSON members.
- [x] `frontend/src/api/tripsApi.ts` — add required `inProgress` to TripProfileSummary; update typed summary fixtures in `frontend/src/FeeFreeCancellationAndTriage.test.tsx` and any compiler-identified summary builders. Do not rewrite unrelated legacy draft fixtures.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — add red mismatch test via save option/change Working dates; assert both endpoints, inverse date mismatch, inclusive start/end transitions, cancellation override and expiration independence. Retain existing projection/count/order assertions.
- [x] `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java` — retain independent booked-option departure boundary coverage; add only a focused assertion if current tests do not prove the projection change leaves that authority intact.

### Automated verification
- [x] `./mvnw.cmd "-DskipFrontend=true" "-Dtest=TripApiIntegrationTest,BookingCancellationIntegrationTest" test` — both projections agree on displayed date boundaries; independent expiration, inventory/history/owner/version cancellation tests still pass.
- [x] From `frontend`: `npm run build` — additive contract and fixtures compile.

### Optional developer checks
- None required for this phase.

## Phase 2: Compact, explicitly ordered lists

### Changes
- [x] `frontend/src/components/TripListSection.tsx` — sort copies of isolated active arrays and merged cancelled array with the declared comparators; maintain filter counts/section order/empty hints. Render In progress from server boolean only for active Upcoming cards.
- [x] `frontend/src/components/TripListSection.tsx` — reorder TripCard content to name, destination/date, status/count summary and actions; remove AlternativeSummaryItem and expanded list/import while retaining workspace access and compact Saved/booking/expired counts and booking reference. Use the declared date-only formatter with year on each endpoint. Set card heading one level below section heading.
- [x] `frontend/src/style.css` — adapt card summary/spacing/wrapping styles and existing narrow stacking to the new hierarchy; do not remove workspace/shared alternatives CSS still used elsewhere.
- [x] `frontend/src/components/TripListSection.test.tsx` (new) — deterministic grouping/sorting/ties, immutable props, filters/empties, card hierarchy/date format, compact counts and details exclusion.
- [x] `frontend/src/App.test.tsx` — update old hierarchy expectations; retain profile/navigation integration and prove refresh changes in-progress/Past/Cancelled membership plus date/name ordering. Do not classify from mocked browser time.

### Automated verification
- [x] From `frontend`: `npm test -- src/components/TripListSection.test.tsx src/App.test.tsx` — grouping/order/card and refresh assertions pass.
- [x] From `frontend`: `npm run build` — presentation changes compile and bundle.

### Optional developer checks
- Inspect long names/destinations, many trips and date ranges at 320px/375px and desktop, including zoom. Observe wrapping and readable section/card hierarchy; not a substitute for executable sorting/filter tests.

## Phase 3: Accessible secondary menu and full regression

### Changes
- [x] `frontend/src/components/TripListSection.tsx` — add menu state/refs/effects/keyboard handling, persistent named trigger and menuitems; move existing secondary action branches intact into the menu; preserve direct Open trip. Focus rename input after activation; restore trigger on cancellation/success; do not close the form or allow duplicate submission while pending.
- [x] `frontend/src/style.css` — bound in-flow menu width, wrap labels and preserve disabled/focus/forced-color presentation; retain one-column narrow card/actions.
- [x] `frontend/src/components/TripListSection.test.tsx` — keyboard and pointer dismissal, named ARIA relationships, disabled actions, callbacks/version payloads, rename validation/pending/error/conflict and focus behavior.
- [x] `frontend/src/App.test.tsx` and `frontend/src/FeeFreeCancellationAndTriage.test.tsx` — activate menu before existing secondary-action assertions; prove menu-to-delete/cancel dialog initial focus/trap/Escape/pending/errors/restoration and deleted-card fallback; retain workspace behavior.
- [x] `frontend/src/VisualSystem.test.tsx` — extend existing CSS checks only for meaningful menu width/wrapping/focus/forced-color regressions, avoiding style snapshots that mirror every declaration.
- [x] Inspect `frontend/src/components/ProfileScreen.tsx`, `frontend/src/App.tsx`, `frontend/src/components/TripWorkspace.tsx` — preserve existing refresh/request sequencing/unsaved Working state and eligibility branches. Production edits here only if a targeted refresh regression demonstrates a missing required path; no background refresh or booked-option clock rewrite.

### Automated verification
- [x] From `frontend`: `npm test -- src/components/TripListSection.test.tsx src/App.test.tsx src/FeeFreeCancellationAndTriage.test.tsx src/ProgressiveTripBuilder.test.tsx src/VisualSystem.test.tsx` — list/action/navigation/focus and adjacent workspace regressions pass.
- [x] From `frontend`: `npm test` — broad frontend suite passes, including previously direct secondary-action callers.
- [x] Repository root: `./mvnw.cmd clean verify` — full backend suite, frontend compile/build and packaging pass. This does not run Vitest, hence separate npm test gate.

### Optional developer checks
- Keyboard-only pass through filters, Open, menu, rename and confirmation dialogs on an isolated local app; inspect focus after dismissal and successful removal. Screen-reader check for filter pressed state, menu trigger/state/items, errors and In progress. Inspect narrow menu overflow and forced colors. These observations are nonblocking and must not be represented as performed unless actually performed.

## Test Strategy
Use actual supported API operations plus mutable test clock for the projection mismatch/boundaries; use component user interactions for list order/card/menu behavior; use App integration for refresh and modal focus. Preserve existing cancellation/deletion integration coverage rather than reproducing inventory tests in a list fixture. Step 3 details cases, commands and exit criteria in the companion testing plan. All routine tests use mocked fetch or isolated H2; no default development database or live services.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Four counts/filters, All order, cancelled isolation | TripListSection filtered copied arrays and existing sections | TripListSection filters/empty/mixed-cancelled tests; App cancelled refresh |
| In-progress first/badge/Upcoming; inclusive boundaries | TripService request date + summary inProgress; TripCard badge/comparator | MockMvc Portland boundary and mismatch tests; component server-flag tests |
| Explicit date/name/stable ordering | TripListSection three comparators with public-ID final tie | Unsorted fixtures, conflicting start/end order, equal names/dates and reversed rerender |
| Reopen/refresh date and persisted changes | Existing ProfileScreen/App refresh routes, server recomputation, local fresh sorting | App navigation/popstate/workspace/mutation changed-profile tests, stale/failure regressions |
| Name/destination/dates/status/Open; compact details | TripCard markup/date formatter/removal of expanded options | Component DOM hierarchy, years/timezone/count/details absence; workspace options remain |
| Accessible menu and preserved actions | TripCard menu and intact callback/guard/form branches | Component keyboard/guard/error tests; existing App modal and API mutation suites |
| Narrow screens, keyboard/AT | Wrapping/in-flow bounded menu, native controls/ARIA, focus lifecycle | Component keyboard/ARIA + VisualSystem checks; optional geometry/AT observations |

## Risks and Rollback/Recovery
No migration or data rewrite is planned. Roll back production projection/UI changes together if required; do not restore the mismatched semantics in only one endpoint or calculate list time from browser time. Backend/frontend should ship from the same Maven artifact; deploying old server with a new frontend would omit the badge signal. Refresh failure retains prior profile and reports existing errors; a later successful request recovers classification/order. Pending mutations retain existing version/conflict safeguards. No documentation claims a completed test or visual check at planning time.

## References
- Ticket and research above; `ai/thoughts/design-lens.md`.
- `src/main/java/app/detour/trip/{TripService,TripProfileSummary,JdbcTripRepository,TripController}.java`.
- `src/main/java/app/detour/identity/{IdentityController,ProfileResponse}.java`.
- `src/main/java/app/detour/booking/BookingTransactionExecutor.java`; `src/main/java/app/detour/common/ClockConfiguration.java`.
- `frontend/src/components/{TripListSection,ProfileScreen,TripWorkspace,ConfirmDeleteModal,CancelTripModal}.tsx`; `frontend/src/{App.tsx,style.css}`.
- `pom.xml`, `frontend/package.json`, `README.md`, `src/test/java/app/detour/trip/TestClockConfiguration.java`.


## Step 4 implementation context

- Selected run remains developer-approved `full`. Initial checkout contained only this pipeline's ticket/research/plans; no unrelated production/test changes were present or overwritten.
- Implemented the approved server snapshot/displayed-date contract, copied-array list comparators, UTC date-only formatting, compact counts/reference and persistent menu trigger. Existing booking/departure guards, endpoint array order, profile refresh sequencing, workspace details and dirty-state handling remain in their existing implementations.
- Routine fixture adaptation: TypeScript build identified two additional summary fixtures in `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`; only their required `inProgress: false` field was added.
- Menu Tab uses native focus advancement followed by dismissal, with a deferred dismissal for leaving the document (where no focusin event follows), and listener/timer cleanup on unmount. Disabled-only menus focus their container so Escape/Tab remain usable. Rename success restores focus after the pending controls become enabled.
- Optional 320px/375px/desktop/200% zoom, actual screen-reader and forced-color observations were NOT PERFORMED. Executable evidence is DOM/ARIA/user-event/CSS coverage, not pixel geometry or real assistive-technology announcements. No manual application session was started; backend suites may start isolated embedded test servers. The default development database was not reset or mutated and no live booking/model service was invoked.

- Required removed-card focus verification uncovered a pre-existing modal selector defect: a combined named/generic heading query selected the hidden About heading before the active My Trips heading. Routine adaptation within the plan's confirmation/removal-focus requirement adds `tripDialogFocus.ts` and uses it in `ConfirmDeleteModal`/`CancelTripModal`. It chooses known current-view headings by priority, skips `[hidden]` ancestors (including mounted Working workspace), and uses a visible generic heading only as a final fallback. The removed-card App assertion now verifies the actual My Trips heading; a focused helper test verifies hidden workspace/About exclusion.


## Completed acceptance evidence

| Criterion | Executed evidence |
| --- | --- |
| Four counted filters/section order/cancelled isolation | Component counts/filter/empty/mixed-cancelled cases; retained App cancelled-filter assertions. |
| Inclusive in-progress/Upcoming/date boundaries | Both API endpoints in mismatch, inverse and Portland DST boundary tests; component authoritative-flag cases with contradictory browser time. |
| Relevant date/name/stable ordering | Frozen shuffled component inputs, conflicting start/end order, reversed rerender and equal-date/name/ID tests in Upcoming/Past/Cancelled. |
| Reopen/refresh/persisted changes | App My Trips/popstate changed-profile tests, persisted rename ordering, stale profile response/failure preservation, mounted Working rename refresh; supported Saved-option load reclassification in API test; retained dirty/stale workspace regressions. |
| Compact readable hierarchy | Component DOM order/headings, month/year endpoint fixtures, booking/reference/counts and detailed-option absence; App compact expectations and unchanged workspace option suites. |
| Preserved accessible actions | Component keyboard/ARIA/dismissal/action matrix/rename validation/pending/errors; App delete confirmation/version/count payloads, pending/error/focus and stale-count refresh; FeeFree cancellation payload and trigger restoration. |
| Narrow/keyboard/AT support | In-flow bounded/wrapping CSS plus VisualSystem assertions, native control semantics, menu keyboard tests and visible dialog fallback tests. Actual geometry/AT/zoom observations remain optional and unperformed. |

Final required gates: `npm test` from `frontend` passed 188 tests in 17 files; `./mvnw.cmd clean verify` passed 206 backend tests, final TypeScript/Vite build and JAR packaging. The earlier focused backend command passed 66 tests; component-focused command passed its initial 15 cases and the related frontend command passed 108 cases before the final fallback regression was added. Final full suites cover all current cases. Phase-specific frontend command subsets are covered by these executed supersets. `git diff --check` passes. No unresolved ticket-scoped test failure remains. Independent Step 5 review is still required.

## Confirmation refresh and focus sequencing

Successful list cancellation/deletion keeps its confirmation pending until the existing profile refresh finishes, then closes the dialog. This ensures its focus-restoration effect observes the final card membership: restore the persistent trigger when it survives, or the visible section/page heading when it disappears. A delayed profile response must preserve the dialog focus trap and pending dismissal restrictions. This is a routine adaptation of Phase 3's required removed-card focus behavior; mutation payloads, permissions and refresh-failure handling remain unchanged.

When deleting the Trip retained by a previously opened, hidden workspace, clear `activeTrip` only after the profile refresh completes, together with dialog dismissal. Clearing it earlier triggers the navigation heading-focus timer while the confirmation is still pending. The delayed-refresh App regression includes this opened-workspace path and asserts the pending dialog retains focus before the response resolves.
