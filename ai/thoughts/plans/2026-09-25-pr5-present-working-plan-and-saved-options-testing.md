# Working Plan and Saved Options Presentation Testing Plan

## Change Summary
The Trips list and workspace will show one Working plan and named, dated Saved options. Save-new, copy-to-edit, replace/update, and rename will have distinct outcomes; comparison and booking/cancellation flows will use each option's own facts.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Presentation | Duplicate Working card or old Draft controls confuse the journey | List/workspace render assertions |
| Option writes | Wrong target replaced, accidental extra option, or rename mutates snapshot | Frontend action tests and API integration test |
| State and failures | Dirty or failed save falsely appears saved; conflict/session expiry loses context | Mocked failure and retry tests |
| Dates and pricing | Working dates leak into option card or comparison; summary disappears before review | Distinct-date fixtures and summary persistence test |
| Booking and cancellation | Booked option offered in-place update or copy/triage broken | Booking/cancellation tests |
| Accessibility/responsiveness | Unnamed actions, broken focus or mobile tab switching | Role/name/focus assertions and existing keyboard tab tests; optional visual smoke |

## Existing Coverage and Environment Constraints
`frontend/package.json` uses Vitest (`npm test`) and TypeScript/Vite (`npm run build`). `DraftPromotion.test.tsx`, `App.test.tsx`, `ItineraryComparisonAndBookingReview.test.tsx`, `FeeFreeCancellationAndTriage.test.tsx`, and `PublicTripFlow.test.tsx` render React with mocked API seams. `TripApiIntegrationTest.java` uses the existing MockMvc/integration database setup for option contracts. Root Maven wrapper is `mvnw.cmd` on Windows. No live credentials or production service are needed; backend integration tests may require the repository's configured test database/runtime.

## Failing Test First
- Name: `shows one Working plan and separately named Saved options without Draft controls`
- Type: React component/integration test.
- Location: `frontend/src/App.test.tsx` or `frontend/src/DraftPromotion.test.tsx` using their existing fixtures.
- Arrange/Act/Assert: Render a Trip with one Working entry and two Saved options with distinct names/dates; open the workspace; assert one Working heading, the two option names/dates under Saved options, and absence of Create empty draft, Duplicate draft, Draft count/version, and Planned itinerary UI.
- Expected pre-fix failure: the current Alternatives grid renders the Working record again and exposes Draft/Planned counts and technical labels.

## Tests to Add or Update

### 1. `rendersWorkingAndSavedOptionsWithDistinctFacts`
- Type: React render test.
- Location: `frontend/src/App.test.tsx` and `frontend/src/DraftPromotion.test.tsx`.
- Proves: Trip name versus option names; one editable Working plan; dates and shared traveler group; component and tally display; incomplete Working remains editable while save-new is disabled with explanation.
- Inputs/fixture: One Working selection set, two named Saved option snapshots with distinct March 2027 dates, components and tally, plus an empty Working variant.
- Doubles or boundary isolation: Mock `tripsApi` calls as existing tests do.
- Edge cases: Zero options, one option, long option name, missing optional compatibility alias in a legacy-shaped fixture.

### 2. `savesNewUpdatesTargetAndCopiesWithoutMutatingSavedOriginal`
- Type: React interaction test.
- Location: `frontend/src/DraftPromotion.test.tsx`.
- Proves: Save as new increases count; update confirmation names the target and preserves count/ID; copy replaces Working while Saved original remains; booked option offers copy and no update; rename form targets only the chosen option.
- Inputs/fixture: Versioned Working and Saved responses, including booked option.
- Doubles or boundary isolation: Mock `saveOption`, `updateOption`, `loadOption`, `renameOption`; assert call payloads and final visible facts.
- Edge cases: Cancel confirmation, missing name, no selections, unsaved Working data, version conflict, failed first half of keep-current/load sequence.

### 3. `renameOptionPreservesSnapshotAndRejectsUnsafeWrites`
- Type: MockMvc integration.
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`.
- Proves: Rename-only endpoint modifies name/version only, leaves dates and component snapshots/tally untouched, requires owner and current Trip/option versions, rejects empty/oversized name and booked option.
- Inputs/fixture: Existing saved option and booking fixture patterns in this class.
- Doubles or boundary isolation: Existing test database and authenticated test user; no external calls.
- Edge cases: Stale version, foreign Trip/option, canceled Trip, booked history, unchanged Working plan.

### 4. `keepsDateRevisionAndSaveFailureVisibleUntilResolved`
- Type: React interaction test.
- Location: `frontend/src/DraftPromotion.test.tsx`.
- Proves: Server-supplied selection removal/price adjustment summary remains visible before update confirmation and until explicit acknowledgment; failed save or session expiry leaves unsaved state and retry/reload path; success is announced only after fulfilled mutation.
- Inputs/fixture: Mock shared-detail response with `revisionSummary`, rejected option call, `IdentityApiError` for auth/conflict.
- Doubles or boundary isolation: Fake timers for debounced save; mock API.
- Edge cases: Subsequent harmless rerender, canceled update dialog, retry after failure.

### 5. `comparesNamedOptionsWithOwnDatesAndPrices`
- Type: React render and keyboard interaction test.
- Location: `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`.
- Proves: Desktop columns and mobile tab/panel labels use user names and individual dates; components and tallies come from each snapshot; expired and booked state attach to correct option; Arrow/Home/End switching and focus work.
- Inputs/fixture: Two or three options with deliberately different dates, prices, components and statuses.
- Doubles or boundary isolation: Existing component fixtures/API mocks; no live catalog.
- Edge cases: Deselect to fewer than two, selected option disappears after refresh, active booking disables new booking without obscuring option facts.

### 6. `cancellationFollowUpStillCopiesNamedOption`
- Type: React journey test.
- Location: `frontend/src/FeeFreeCancellationAndTriage.test.tsx` and `frontend/src/PublicTripFlow.test.tsx`.
- Proves: Named option remains available for triage copy after cancellation where permitted; canceled read-only presentation and navigation are clear; keyboard-accessible action labels and focus return are preserved.
- Inputs/fixture: Existing cancellation and Trip flow fixtures extended with names/dates.
- Doubles or boundary isolation: Existing mocked API calls.
- Edge cases: Canceled Trip versus canceled booking, expired option, narrow-layout structure via semantic DOM assertions.

## Safe Verification Commands
- Focused: `npm test -- --run App.test.tsx DraftPromotion.test.tsx` from `frontend`.
- Related suite: `npm test -- --run ItineraryComparisonAndBookingReview.test.tsx FeeFreeCancellationAndTriage.test.tsx PublicTripFlow.test.tsx` from `frontend`; `./mvnw.cmd -Dtest=TripApiIntegrationTest test` from root.
- Full safe suite: `npm test` and `npm run build` from `frontend`; `./mvnw.cmd test` from root.

## Optional Developer Checks
- At desktop and narrow phone widths, inspect long names, action wrapping, modal focus/scroll, and comparison panels with keyboard and a screen reader. Report these as nonblocking observations if not performed.

## Exit Criteria
- [ ] The first render test fails for the intended old terminology/duplicate Working behavior before implementation.
- [ ] Rename API test fails for missing route before implementation.
- [x] New and updated tests pass after implementation.
- [x] Frontend full test and build commands pass, and the backend suite passes or a concrete environment limitation is reported.
- [x] Each acceptance criterion maps to executable evidence above.
- [x] No routine test performs live or destructive operations.
- [x] Conflict, auth, incomplete Working, booked option, and distinct-date/price cases are covered.
- [x] Optional visual/accessibility checks are reported honestly and not presented as completed automated proof.

The implementation resumed from a partially edited checkout, so the two planned pre-fix failure checks were not recorded and remain unchecked. Final verification passed: frontend build; 153 frontend tests across 14 files; 201 backend tests, including the rename and booked-status contract checks. The backend command used the existing Maven cache via `MAVEN_OPTS=-Duser.home=C:\Users\rmelcher` and `-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository` because this environment otherwise resolved its cache to an unwritable `C:\.m2` path.
