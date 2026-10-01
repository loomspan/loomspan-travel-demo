# Organize My Trips Testing Plan

## Change Summary
Ticket: `ai/thoughts/tickets/2026-10-01-organize-my-trips.md`. Research: `ai/thoughts/research/2026-10-01-organize-my-trips.md`. Implementation plan: `ai/thoughts/plans/2026-10-01-organize-my-trips.md`.

Pipeline Steps 2 and 3 use the developer-approved `full` profile. No active design-lens guardrails exist. Shared profile classification will follow displayed parent/Working dates and expose server-derived `inProgress`, retaining UPCOMING/PAST and independent option expiration. The frontend sorts list copies, compacts cards and moves existing secondary actions into a keyboard-accessible menu. Existing navigation/mutation refresh routes and booking permissions remain intact. This step specifies tests only; no tests, production changes or live operations have been executed.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Shared projection | Later Saved dates keep an ended displayed trip Upcoming; endpoints disagree after edits | Supported save/change-Working API regression and both authenticated projections |
| Time authority | Browser/host timezone or midnight request changes in-progress/Past | Mutable injected clock, inclusive boundary/DST tests; frontend consumes explicit flag with contradictory browser time |
| Expiration/eligibility | End classification inadvertently changes option departure or booked-option cancellation | Independent expiration assertions and existing BookingCancellationIntegrationTest suite |
| Ordering/filtering | Wrong relevant date, cancelled duplication, shuffled equal keys, mutated props | Component fixtures deliberately unordered, equal-key rerenders, all four filter counts |
| Card summary | Raw dates/local-day shifts, missing years, booking history mistaken for active booking, details remain | Component formatting/hierarchy/count tests and workspace details regression |
| Menu/action lifecycle | Guard predicates change, callback payloads stale, disabled actions invoke mutations, rename errors lost | Component action matrix and existing version/confirmation integration tests |
| Focus/accessibility | Closed menu item disappears before dialog records focus, Tab trap/escape/outside dismissal fails | Keyboard component tests and App modal integration, focus restoration after removal |
| Refresh/recovery | Date/name/status updates do not reorder/reclassify; stale request wins; dirty Working overwritten | App sequential profile responses and existing stale/dirty workspace tests |
| Layout | Menu/long labels overflow narrow cards or lose visible control states | Existing VisualSystem CSS assertions plus optional rendered viewport/AT checks |

## Existing Coverage and Environment Constraints
- `src/test/java/app/detour/trip/TripApiIntegrationTest.java`: Spring Boot/MockMvc, independently named in-memory H2 datasource, JdbcTemplate selection helpers, real authenticated Client/CSRF helpers, mutable `TestClockConfiguration.TestClock`, reset after each test. Existing `profileProjectionReturnsOwnerTripsPartitionedByDateWithAccurateCounts`, `profileProjectionPartitionsUpcomingAndPastWithDeterministicSort` and `clockControlsExpirationAtDepartureMidnightAndBlocksPromotion` are adjacent coverage. Keep existing endpoint-order assertions because server ordering is not changing.
- `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`: booked-option vs Working departure, Portland midnight, inventory/history/ownership/version behavior. Reuse the suite; only add assertions if current coverage cannot prove the unchanged independent authority.
- `frontend/src/App.test.tsx`: mocked fetch, Testing Library/user-event, navigation/profile refresh, rename/conflict, cancellation/delete confirmation/error/pending/focus behavior. Existing direct secondary-action clicks must open the relevant card menu first; keep meaningful assertions rather than weakening them to accommodate hidden actions.
- `frontend/src/FeeFreeCancellationAndTriage.test.tsx`: directly mounts list and workspace; cancelled summary must still override missing history. `frontend/src/ProgressiveTripBuilder.test.tsx`: independent option expiry, stale response protection and unsaved state. `frontend/src/VisualSystem.test.tsx`: CSS/native focus and forced-color checks.
- New focused component file: `frontend/src/components/TripListSection.test.tsx`, using Vitest, Testing Library and user-event already in package.json. Use one valid Working plan, representative Saved options and typed summary builder with required `inProgress`, avoiding legacy multi-Draft fixtures in new tests.
- Java 21+, Maven wrapper, Node/npm are required. `-DskipFrontend=true` is existing Maven configuration for focused backend tests. Maven clean verify runs backend tests and builds/packages frontend, but does not run Vitest.
- Supported create/edit dates are March 2027, one to fourteen nights; fixtures must obey these bounds. Cross-year formatting fixtures are pure display-unit fixtures, not invalid API writes.
- No external credentials or services are needed. Routine API writes occur only inside isolated test H2, frontend writes only to mocks. Do not start the app against `data/detour`, reset development data, or invoke booking/model/live operations.
- jsdom proves DOM order, names/ARIA, callback behavior and focus; it cannot prove rendered 320px geometry or actual assistive-technology announcements. Those remain optional observations, explicitly reported as unperformed when absent.

## Failing Test First
- Name: `profileUsesDisplayedEndDateWhenSavedOptionEndsLater`.
- Type: existing MockMvc/H2 API integration.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`.
- Arrange: while test clock is before March 2, create Working March 15–19 with a supported name, add a valid stay selection using existing helper/catalog, save a named option with expected versions, then `PUT /api/trips/{tripId}/working-dates` to March 2–5 using returned parent/draft versions. Assert the Saved snapshot still reads March 15–19 and parent/Working dates now read March 2–5. Set clock to March 6 midday in PDX_ZONE.
- Act: authenticated GET `/api/trips` and GET `/api/profile` for the same owner.
- Assert: the trip is in Past only; temporalStatus is PAST and displayed dates March 2–5. After implementation, also assert `inProgress=false`, one expired Working and unexpired future Saved option, preserving planned/booking counts.
- Expected pre-fix failure: current code keeps this trip Upcoming because the future Saved option has not ended. Introduce the membership/status assertions first so the red is semantic, not merely a missing JSON field/TypeScript compile failure. Record the targeted failing assertion before adding the new contract assertion.
- First frontend missing-capability test: render a typed component fixture with future entries preceding `inProgress=true` active entries; assert in-progress cards lead Upcoming and carry the badge. Before implementation, card order/badge assertions fail. Test missing frontend capabilities after the backend red is established; do not require every added test to have a separate red run.

## Tests to Add or Update

### 1. `profileUsesDisplayedEndDateWhenSavedOptionEndsLater` and inverse dates
- Type: MockMvc integration.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`.
- Proves: displayed trip end governs shared classification after a real supported save/edit path, and independent snapshots are untouched.
- Inputs/fixture: red fixture above; inverse variant saves March 2–5 then changes Working to March 15–19 before advancing clock to March 6.
- Doubles or boundary isolation: actual repository/HTTP against test H2; mutable server clock. No browser clock assumptions.
- Edge cases: both endpoints show identical fields/partition, owner-scoped counts remain correct, inverse stays UPCOMING with inProgress=false despite expired Saved option, changing/loading Working dates later refreshes projection correctly.

### 2. `profileMarksInclusiveInProgressBoundariesFromPortlandClock`
- Type: MockMvc integration, parameterized cases or compact repeated requests following existing conventions.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`.
- Proves: UPCOMING before and during trip, inProgress only on inclusive active dates, PAST after end, CANCELED never gets an active in-progress signal.
- Inputs/fixture: March 13–15 crossing Portland DST March 14; clock at start-day minus one millisecond, start midnight, end-day final millisecond, next midnight using ZonedDateTime/PDX_ZONE.
- Doubles or boundary isolation: injected mutable TestClock; existing Client and isolated H2. Avoid fixed UTC-offset comments/assertions across DST.
- Edge cases: UTC calendar day differs from Portland date; false for future/Past/cancelled; `isExpired`/alternative expiration turns true at departure while trip remains in progress. If testing per-request clock snapshot directly, use a focused clock double that crosses midnight between reads and assert one coherent classification; do not introduce a full service-mocking harness solely for this.

### 3. `ordersTripsByGroupRelevantDateNameAndStableId`
- Type: component behavior.
- Location: `frontend/src/components/TripListSection.test.tsx`.
- Proves: in-progress first, start ascending within in-progress/future, end descending for Past, start descending for Cancelled, name A–Z then ID for equal keys.
- Inputs/fixture: deliberately shuffled upcoming/past arrays with cancelled entries in both; Past trips whose start and end ordering conflict; cancelled trips whose end and start ordering conflict; mixed-case Alpha/Zulu names; equal date/name two IDs.
- Doubles or boundary isolation: typed summary factory and callback spies, no fetch or clock. Read rendered article headings within named sections.
- Edge cases: freeze input arrays or assert unchanged ordering to prove nonmutation; rerender reversed equal-key inputs and assert same ID order; missing optional name falls back to label; same collator-equivalent names still use ID; cancelled inProgress=true malformed fixture still never appears in active grouping/badges.

### 4. `preservesCountsSectionsFiltersAndEmptyStates`
- Type: component interaction.
- Location: `frontend/src/components/TripListSection.test.tsx`; retain App cancelled filtering coverage.
- Proves: all count includes active plus cancelled once, cancelled isolation, Upcoming/Past/Cancelled counts, All section order and aria-pressed transitions.
- Inputs/fixture: active ongoing/future/Past and cancelled in each source array, plus empty list and an empty selected filter.
- Doubles or boundary isolation: user-event click/keyboard and callbacks only.
- Edge cases: no cancelled section in All when zero, Cancelled dedicated empty hint still appears, In progress remains accessible through Upcoming. Do not add malformed duplicate-ID normalization behavior absent a product requirement.

### 5. `rendersCompactReadableTripHierarchy`
- Type: component DOM assertions.
- Location: `frontend/src/components/TripListSection.test.tsx`; update `frontend/src/App.test.tsx` hierarchy test.
- Proves: name before destination/date before compact status/counts before primary Open; year-bearing human dates, correct hierarchy levels and accessible article associations, no expanded Saved-option details.
- Inputs/fixture: long Unicode name/destination, same-month and cross-month date ranges, cross-year display-only range; 0/1/multiple Saved options, active bookings/history-only, expired counts and reference.
- Doubles or boundary isolation: component fixture with detailed alternatives whose names must not appear on the card. Use explicit formatted endpoint assertions, no broad markup snapshot.
- Edge cases: format date-only with UTC explicitly so it cannot become previous day; assert each endpoint year; false inProgress with browser time inside fixture dates must not show badge, true flag with browser time outside dates must show it. Restore mocked time after test. Card h3 under h2 and h4 under h3 section configurations; Open callback works for cancelled card. Workspace options/details existing tests still pass.

### 6. `supportsAccessibleActionMenuKeyboardAndDismissal`
- Type: component user-event interaction.
- Location: `frontend/src/components/TripListSection.test.tsx`.
- Proves: trigger name/haspopup/expanded/controls, labelled menu, menuitems, first/last entry focus, enabled-item Arrow/Home/End navigation, Escape restoration, Tab departure, outside dismissal and no stale menu after unmount.
- Inputs/fixture: unbooked active card, history/expired/Past variants with disabled Cancel, cancelled card with disabled Trip canceled and permitted existing rename UI.
- Doubles or boundary isolation: callbacks are spies; no mutation needed merely to open/navigate.
- Edge cases: Enter/Space opens; ArrowDown first and ArrowUp last; disabled actions never invoke callback; Shift+Tab can exit, normal Tab exits rather than traps; clicking another card closes previous menu; outside pointer interaction does not steal focus from its target; actions have visible text, not icon-only ambiguity.

### 7. `preservesSecondaryAvailabilityAndRenameErrors`
- Type: component interaction plus retained API mutation tests.
- Location: `frontend/src/components/TripListSection.test.tsx`; typed fixtures/update direct action callers in `frontend/src/FeeFreeCancellationAndTriage.test.tsx`.
- Proves: original action matrix, exact summary/version payload, trimmed validated rename, pending double-submit prevention, error/conflict presentation and focus lifecycle.
- Inputs/fixture: never booked -> Delete; active historical booking/no expiry -> Cancel; Past or any expired alternative -> disabled Cancel; CANCELED -> disabled Trip canceled regardless of missing history; optional rename callback available/unavailable.
- Doubles or boundary isolation: deferred Promise for rename pending, rejected IdentityApiError VERSION_CONFLICT and general errors; spy onDelete/onCancel receives unchanged selected summary. Retain backend ownership/booking-history/count/version tests.
- Edge cases: blank/301-character names reject before callback; trim successful name; input receives focus; failure keeps form/error aria-invalid/describedby/role=alert; Escape menu vs rename form semantics do not conflate; Cancel rename/success restores persistent trigger; pending form controls disabled. Cancelled rename preserves current callback/error behavior including TRIP_CANCELED; do not weaken server restrictions or silently hide the action.

### 8. `restoresMenuTriggerAfterTripConfirmation`
- Type: App integration with mocked HTTP.
- Location: `frontend/src/App.test.tsx`; update relevant `frontend/src/FeeFreeCancellationAndTriage.test.tsx` cases.
- Proves: selecting a menu action closes menu and restores trigger before existing modal captures focus, initial Cancel focus and trap persist, modal dismissal returns to trigger, successful removal uses existing heading fallback.
- Inputs/fixture: named unbooked card delete flow and eligible history card cancel flow; deferred request, confirmation failure and stale count/version refresh; active filter cases where cancelled/deleted card leaves the current section.
- Doubles or boundary isolation: mocked fetch captures existing confirmation/version/count request bodies. No real deletes/cancellations.
- Edge cases: Escape/backdrop blocked pending, backend errors leave dialog available with retry, focus after cancellation success when card survives and after delete/removal when trigger disappears; upcoming heading fallback remains focusable. Existing confirmation payload assertions must remain unchanged.

### 9. `refreshReclassifiesAndReordersSavedTrips`
- Type: App navigation/data-flow integration.
- Location: `frontend/src/App.test.tsx`; retain `frontend/src/ProgressiveTripBuilder.test.tsx` stale/dirty tests.
- Proves: returning/reopening My Trips uses new server profile, preserving existing request sequencing and dirty Working state.
- Inputs/fixture: sequence profile responses future -> inProgress -> Past with changed start/end/name; rename moves equal-date cards by name; cancellation moves card to cancelled; deletion updates counts; simulate My Trips click, popstate return and workspace Back/update through existing test helpers.
- Doubles or boundary isolation: mocked `/api/profile`, `/api/trips/{id}` and mutation responses. Time transitions are explicit server flags/partitions, not automatic client dates.
- Edge cases: delayed old refresh resolves after newer success; failure retains authenticated screen and prior cards; rename persisted success plus refresh failure keeps existing explanatory error; returning with unsaved Working retains editor state. Reuse current tests for already-proven paths and add missing changed-order/classification assertions instead of duplicating every route.

### 10. `keepsMenuAndCardControlStatesUsableOnNarrowLayouts`
- Type: repository existing CSS/source-level affordance assertions, component semantic tests, optional rendering observation.
- Location: `frontend/src/VisualSystem.test.tsx`; semantic cases in new component suite.
- Proves: existing one-column/wrapping/min-width behavior remains, menu constrained within card, focus-visible and forced colors remain present.
- Inputs/fixture: new menu/card classes and existing media/control rules; long labels in component DOM.
- Doubles or boundary isolation: source read matching meaningful width/wrapping/focus rules as current VisualSystem conventions; no browser dependency added.
- Edge cases: do not label CSS checks as proof of pixel geometry. Optional 320/375px, desktop, 200% zoom, keyboard/screen-reader observations cover actual geometry and announcements when available.

## Safe Verification Commands
Run from repository root unless stated. These are planned commands, not completed checks.

- First red: `./mvnw.cmd "-DskipFrontend=true" "-Dtest=TripApiIntegrationTest#profileUsesDisplayedEndDateWhenSavedOptionEndsLater" test`. Expected failure must be Past membership/status, not setup/catalog/CSRF or absent new field.
- Focused backend: `./mvnw.cmd "-DskipFrontend=true" "-Dtest=TripApiIntegrationTest,BookingCancellationIntegrationTest" test`.
- Focused frontend, working directory `frontend`: `npm test -- src/components/TripListSection.test.tsx`.
- Related frontend, working directory `frontend`: `npm test -- src/components/TripListSection.test.tsx src/App.test.tsx src/FeeFreeCancellationAndTriage.test.tsx src/ProgressiveTripBuilder.test.tsx src/VisualSystem.test.tsx`.
- Frontend compile/build during development, working directory `frontend`: `npm run build`.
- Full safe frontend, working directory `frontend`: `npm test` (run `npm ci` first only when dependencies need installation, per README).
- Full safe backend/build/package: `./mvnw.cmd clean verify`. Includes backend unit/API/migration/concurrency coverage and bundled frontend build; does not replace npm test.
- `./scripts/verify-packaged-release.ps1` is an existing isolated packaged-release check, but not a required gate for this projection/list-only ticket because no persistence/startup/release behavior changes. Run only if implementation introduces a packaging concern; report execution honestly. Never run default application mutation flows as verification.

## Optional Developer Checks
- On an explicitly isolated local app, inspect 320px/375px/desktop and 200% zoom with long names, many trips and open menu; verify no page/menu horizontal overflow and readable dates/counts.
- Keyboard and assistive technology pass: filter pressed state, In progress, menu name/state/navigation/dismissal, rename validation errors, initial dialog focus/trap and restored card/fallback heading focus; forced colors if available.
- A configured server clock may demonstrate start/end transitions on a disposable database; automated mutable-clock tests are the required evidence. No need for real waiting until midnight or live booking operations.
- All optional checks are nonblocking and must be reported as unperformed if not executed.

## Exit Criteria
- [x] The red mismatch test fails for the intended current classification defect before the fix, with supported API setup intact.
- [x] Shared endpoint classification/inProgress obey inclusive Portland dates and independent option expiration; cancelled and inverse dates covered.
- [x] New/updated component and App tests pass for counts/grouping/relevant-date/name/ID ordering, immutable inputs, compact hierarchy/date years and refresh changes.
- [x] Menu keyboard/ARIA/dismissal/focus and original action matrix/confirmations/pending/errors pass; adjacent booked-option eligibility and dirty/stale workspace tests pass.
- [x] Full `npm test` and `./mvnw.cmd clean verify` pass; any actual failure is recorded with exact command and resolved before handoff.
- [x] Each of the seven acceptance criteria maps to the implementation plan's executable evidence; narrow geometry/AT observations remain explicitly optional with the jsdom limitation recorded.
- [x] Routine tests do not access the persistent development database or perform unintended live/destructive operations.
- [x] Optional observations are not represented as performed; no fixture/style assertion is presented as proof beyond its actual boundary.

## Step Report: 2_create_plan + 3_testing_plan
STATUS: complete
ARTIFACTS:
  - ai/thoughts/plans/2026-10-01-organize-my-trips.md
  - ai/thoughts/plans/2026-10-01-organize-my-trips-testing.md
SUMMARY: Completed concrete implementation phases and focused regression coverage for authoritative dates, ordering, compact cards, accessible secondary actions and refresh paths. Inspected current production/test/build sources; no production/test changes, verification runs or live operations were performed.
DECISIONS:
  - Use displayed parent/Working dates for Past and an additive server inProgress boolean with one projection clock snapshot; retain UPCOMING/PAST and independent departure guards.
  - Own list ordering in TripListSection copied arrays, with name and public-ID ties; preserve existing endpoint array order and action availability/errors.
  - Restore persistent menu-trigger focus before existing confirmation callbacks and after rename dismissal/success; retain removed-card fallback.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run Step 4 using both completed plan artifacts with the full profile.


## Step 4 red evidence and resolved development failures

- `./mvnw.cmd "-DskipFrontend=true" "-Dtest=TripApiIntegrationTest#profileUsesDisplayedEndDateWhenSavedOptionEndsLater" test`: first setup attempt failed to compile an unqualified `List.of` reference; corrected to `java.util.List.of`. The subsequent pre-fix run reached the supported save/date-edit setup and failed exactly at `$.upcoming.length()` (expected 0, actual 1), proving the displayed March 2–5 trip remained UPCOMING due to its independent March 15–19 Saved option. The same targeted command passed after the projection fix. Assertions then expanded to both endpoints, independent expiry/counts and loading the future Saved option back into Working.
- From `frontend`, `npm test -- src/components/TripListSection.test.tsx`: after installing missing local dependencies with `npm ci`, pre-fix run failed on Current/Future DOM order (actual Future, Current; expected Current, Future). Post-fix focused run passed 15 cases; final broad suite includes the additional equal-key/Open cases.
- From `frontend`, initial `npm run build` found two typed profile-summary fixtures lacking the required new field. Added only `inProgress: false` to them; subsequent build passed.
- From `frontend`, `npm test -- src/components/TripListSection.test.tsx src/App.test.tsx src/FeeFreeCancellationAndTriage.test.tsx`: initial failures were old expanded-option/Booking assertions and direct secondary-button queries. Updated expectations for compact counts and activated each card menu before checking its menuitems, preserving callback/confirmation payload assertions. The expanded related command initially exposed a test using a detached dialog after Escape/reopen; requerying the active dialog resolved it. A later related run exposed an ambiguous date assertion after adding a rival card; scoped it to the named original article.
- A mistakenly root-invoked `npm test -- src/components/TripListSection.test.tsx` could not find package.json; all completed frontend checks run from `frontend`. Early file creation was likewise corrected to the appropriate working-directory-relative path. These setup mistakes did not perform application operations.

- Stronger removed-card focus assertion in full `npm test` exposed the real fallback defect: expected My Trips h1, actual hidden About h2. Corrected the two dialog fallbacks through `tripDialogFocus.ts`; `npm test -- src/App.test.tsx -t "restores focus to section heading"` then passed (1 case, 42 intentionally skipped). Added visible-section/hidden-mounted-workspace helper coverage; full frontend and package verification rerun after the production fix.


## Completed verification

- PASS — `./mvnw.cmd "-DskipFrontend=true" "-Dtest=TripApiIntegrationTest,BookingCancellationIntegrationTest" test`: 66 tests, no failures/errors/skips. Final full backend run also covers the subsequently added supported load assertions.
- PASS — from `frontend`, `npm test -- src/components/TripListSection.test.tsx`: initial completed focused suite, 15 tests. Final component suite has 19 cases, all included in the full frontend pass.
- PASS — from `frontend`, `npm test -- src/components/TripListSection.test.tsx src/App.test.tsx src/FeeFreeCancellationAndTriage.test.tsx src/ProgressiveTripBuilder.test.tsx src/VisualSystem.test.tsx`: 108 tests. The final full suite additionally verifies stronger removal/survival focus assertions and the new fallback helper case.
- PASS — from `frontend`, `npm run build`: TypeScript/Vite build; repeated by final Maven verification after the final modal change.
- PASS — from `frontend`, `npm test`: **188 tests / 17 files**, no failures; final run completed 2026-10-01 13:15:47 America/Los_Angeles.
- PASS — `./mvnw.cmd clean verify`: **206 backend tests**, no failures/errors/skips; final frontend build/package passed, completed 2026-10-01 13:17:18 America/Los_Angeles.
- PASS — `git diff --check`; complete ticket-scoped source/test diff inspected for unrelated edits, real sensitive data and unintended live/default-database operations.
- NOT RUN — `./scripts/verify-packaged-release.ps1`: not required for this projection/list ticket; no startup/persistence/release contract changed.
- NOT PERFORMED — optional real 320px/375px/desktop/200% zoom geometry, screen-reader/keyboard observation and forced-color rendering. DOM/ARIA/user-event and CSS assertions do not prove those visual/AT observations.

All seven acceptance criteria map to executable evidence in the implementation plan's completed traceability table. Backend mutations use isolated H2/test servers; frontend mutations use mocked fetch/spies. Independent Step 5 review remains the next pipeline gate.

## Delayed confirmation refresh regression

`App.test.tsx` now parameterizes cancellation/deletion removal with a deferred mocked profile response. Assert that the pending dialog retains focus and rejects Escape while refresh waits; after refresh removes the card, closing restores focus to the visible Upcoming section (cancellation under the Upcoming filter) or My Trips heading (deletion of the last card). The delayed cancellation case initially failed with focus on `document.body`; immediate mocked refresh completion had hidden this timing boundary. The production sequencing above and final focused two-case rerun establish the removal behavior without live mutations.

- PASS — from `frontend`, `npm test -- src/App.test.tsx -t "refresh removes"`: 2 delayed refresh cases, 43 unrelated cases intentionally skipped.
- PASS — from `frontend`, final `npm test`: 190 tests / 17 files.
- PASS — final `./mvnw.cmd clean verify > target-review-1-verify.log 2>&1`: 206 backend tests with zero failures/errors/skips, updated frontend build and JAR packaging; completed 2026-10-01 13:24:48 America/Los_Angeles. Temporary diagnostic log inspected and removed.
- PASS — final `git diff --check`.

## Opened-workspace confirmation regression

The delayed-refresh App case also opens the unbooked Trip workspace, returns to My Trips, and deletes that same Trip. This tests the retained `activeTrip` navigation timer as well as the list-card removal boundary. Its first run used the wrong Back button name and failed during setup; after using the actual `← Back to all trips` name, it failed at the intended pending-dialog focus assertion: My Trips held focus while the profile response was unresolved. Deferring active-workspace cleanup until refresh completes fixes that timing path.

- PASS — from `frontend`, `npm test -- src/App.test.tsx -t "refresh removes"`: 3 delayed-refresh cases, 43 unrelated cases skipped.
- PASS — from `frontend`, `npm test`: 191 tests / 17 files.
