# Independent Trip Plans Testing Plan

## Change Summary
Implement the governing plan `ai/thoughts/plans/2026-10-01-manage-independent-trip-plans.md` for ticket `ai/thoughts/tickets/2026-10-01-manage-independent-trip-plans.md`, using research `ai/thoughts/research/2026-10-01-manage-independent-trip-plans.md`. All three inputs were read completely; the implementation plan was created in this same planning context. This step edits no production or test code and executes no tests. No active project guardrails are recorded.

Every plan gains its own party/dates/selections and stable editable identity; one primary pointer owns preference. Working migrates into canonical snapshot storage. Purchased party/dates and component snapshots remain frozen. UI gains top navigation, independent management, explicit detail save with guarded transitions, and 2–3-plan comparison. Trip destination/budget remain shared and explicitly labeled. Booking history and cancellation restrictions remain intact.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Migration | Losing Working selections, names, dates, ages or booking links | Synthetic V20 upgrade and V18-lineage assertions, failure-preservation fixture |
| Primary lifecycle | Empty trip, foreign/multiple primary or failed deletion | HTTP/SQL invariants after create/promote/delete, race and rollback assertions |
| Independent editing | Trip-level party still leaks into another plan | Different-party plans, sibling snapshot equality, search/pricing assertions |
| Purchased context | Edited dates/count distort purchase display, cutoff or inventory restoration | Book partial plan, edit context/unconfirmed component, exact frozen response and inventory checks |
| Ownership/concurrency | Forged child IDs or stale overwrite | Cross-owner 404, strict payload validation, same-version one-winner tests |
| Dirty transitions | Saving wrong UUID, silently discarded edits or stale response overwrite | Deferred mocked requests, three dialog outcomes, history/exit coordination |
| Comparison | Primary excluded, duplicate IDs, false zero-cost complete itinerary | Launch defaults, selection bounds, missing/partial/unavailable semantics |
| Navigation/restart | Promoted booked IDs lose detail/review access | browser history tests, file-backed restart and packaged HTTP smoke |

## Existing Coverage and Environment Constraints
Backend uses Spring Boot HTTP/JDBC/Flyway integration tests, H2 fixtures, JUnit and `TestClockConfiguration`. `TripApiIntegrationTest` covers named saves/load/update, owner/version checks and rollback; component integration suites cover eligibility/revalidation and capacity; `TripModelForwardMigrationIntegrationTest` targets V18 and asserts V20 today; restart tests use disposable file databases. `BookingConcurrencyIntegrationTest` uses synchronized transactions for inventory/idempotency/cancellation races. `BookingCancellationIntegrationTest` protects cutoff, canceled-trip and retained history rules. Preserve these behaviors while replacing obsolete Working-only assertions.

Frontend uses Vitest/jsdom/testing-library/user-event, mocked API functions, controlled promises and fake timers. `DraftPromotion.test.tsx` tests the old copy editor; replace its lifecycle expectations with independent save/copy semantics and retain error/focus assertions. `ProgressiveTripBuilder.test.tsx` covers stale responses, slots/search/remove/conflicts; `ItineraryComparisonAndBookingReview.test.tsx` covers bounds, keyboard tabs, missing prices, booking/history and screen history. `tripsApi.test.ts` verifies CSRF and exact HTTP shape. `VisualSystem.test.tsx` and public/guest flow tests remain regression gates.

Java 21, npm and the repository Maven wrapper are required. Maven's default resource phase runs npm ci/build; `-DskipFrontend=true` is verified in pom.xml for focused backend tests. `frontend/package.json` defines `test` as `vitest run` and `build` as `tsc -b && vite build`. Run commands below from repository root. No credentials/supplier/payment services are needed: purchases are fictional local transactions. All tests must isolate their DB/server and clocks; never use `./data/detour`, a running developer service, or real accounts. Packaged verifier owns a temporary local server/database and requires a successful packaged build. The original planning sections describe intended coverage; Step 4 execution receipts below distinguish actual results.

## Failing Test First
- Name: `savingAlternativeChangesOnlyItsOwnDatesPartyAndSelections`
- Type: authenticated HTTP integration test with post-write database/detail reads.
- Location: `src/test/java/app/detour/trip/IndependentTripPlanIntegrationTest.java` (new).
- Arrange/Act/Assert: create an owner trip; create named alternative B through canonical plans API; preserve primary A's UUID/name/dates/party/snapshots and primary pointer. PUT B's dates/ages/count and accepted unconfirmed selections with current aggregate/plan versions. Reload: B keeps UUID/name and changes requested details; A and pointer remain identical. Assert non-owner sees 404.
- Expected pre-fix failure: canonical plan create/save route does not exist (404); current option updates copy Working and cannot support independently editable travelers. Keep the baseline failure focused on missing capability, not an accidental compile failure. A test may use raw JSON before DTOs exist.
- Follow with a frontend red test selecting B through top tabs and asserting no `/load` or mutation request occurs; current workspace has no such tab navigation.

## Tests to Add or Update

### 1. `preservesWorkingOptionsAndBookingLinksWhenMigratingToPlans`
- Type: Flyway/JDBC integration.
- Location: `src/test/java/app/detour/trip/TripModelForwardMigrationIntegrationTest.java`.
- Proves: AC1/14; latest V21 schema and exactly one same-trip primary; Working UUID/details become primary even when a saved option is booked; existing option IDs/names/snapshots/history remain unchanged.
- Inputs/fixture: target V20 fixture with airfare including connection facts, stay night rows/unit/prices, rental offsets and descriptions; empty Working; null legacy ages; booked named option with active and canceled rows. Additional V20 missing-Working trip with options and no-options trip; V18 existing recovery fixture upgraded through latest.
- Doubles or boundary isolation: unique local file-H2 URLs; raw SQL fixture rows using repository catalog seeds.
- Edge cases: absent Working selects oldest existing option, never booking-biased; no plan creates empty primary; preserved dates/party on every plan; old draft UUID resolves canonically; no stale runtime draft writes. Snapshot missing stay-night fails conversion without losing source rows/history. Because H2 DDL may commit, assert data retention and restart diagnostic behavior rather than assuming schema rollback. Update old V20/count assertions to reflect deliberate migration and retain old V19 failure test.

### 2. `independentSaveRenameCreateCopyAndSearchUsePlanContext`
- Type: HTTP integration with persisted snapshots.
- Location: `src/test/java/app/detour/trip/IndependentTripPlanIntegrationTest.java`; adapt `TripApiIntegrationTest.java`, `AirfareSearchAndSelectionIntegrationTest.java`, `StaySearchAndSelectionIntegrationTest.java`, `RentalSearchAndSelectionIntegrationTest.java`, `DraftReadinessAndPlannedSnapshotIntegrationTest.java` in the same directory.
- Proves: AC3/4/5; red test behavior; save preserves name/identity; rename alters no selections; empty/partial create; copy preserves source/primary and has a new UUID with zero booking associations. Distinct dates/party drive search eligibility, room count, airfare cost and driver eligibility independently.
- Inputs/fixture: primary with 2 adults, alternative with a distinct valid date range and 3 people including a child; known seeded flights/stay/car; booked source copied into unconfirmed plan.
- Doubles or boundary isolation: authenticated test owners and seeded isolated catalog; GET reload plus normalized sibling JSON/SQL comparison.
- Edge cases: null legacy ages readable but disallowed for age-dependent purchase, 1/8 travelers and validation limits, unavailable inventory still permits valid planning snapshot (existing policy), empty save/create succeeds, unsupported dates fail without changing persisted plan, source copy stale version, name validation, destination/budget remain explicitly trip-scoped. Former Working-copy endpoint returns explicit retirement error with zero writes.

### 3. `primaryPromotionAndDeletionAreAtomicAndOwnerScoped`
- Type: authenticated HTTP/JDBC integration, synchronized concurrent operations and injected repository failure.
- Location: `src/test/java/app/detour/trip/IndependentTripPlanIntegrationTest.java`; retain rollback conventions from `TripApiIntegrationTest.java`.
- Proves: AC6/7/9/10/11. Promote an alternative, including booked, preserving both plans and every booking/inventory row; primary delete requires distinct existing replacement; sole eligible plan deletes trip only with explicit confirmation.
- Inputs/fixture: trip with three plans, sole empty plan, booked/historical plans, second owner's IDs. Capture primary pointer, rows/versions and catalog inventory before each action.
- Doubles or boundary isolation: barrier/latches for two same-version promotions, promotion versus delete, and delete versus booking; injected failure after pointer switch before child deletion; isolated transactions.
- Edge cases: missing/self/foreign/deleted replacement, missing consent, stale aggregate and child versions, changed plan count, canceled trip, active/canceled history; canceled actions make no API call in UI tests. Every loser is conflict/not-found and every persisted surviving trip has nonempty plans and one reachable primary. CAS loser does not increment versions or remove snapshots. New-trip failure after parent/child insertion rolls back entirely; sole deletion failure leaves both rows.

### 4. `purchasedComponentsAndContextSurvivePlanningEdits`
- Type: HTTP/JDBC booking and cancellation integration.
- Location: `src/test/java/app/detour/booking/BookingApiIntegrationTest.java`, `BookingCancellationIntegrationTest.java`, `BookingSchemaIntegrationTest.java`, `BookingConcurrencyIntegrationTest.java`; selected-plan mutation assertions in `src/test/java/app/detour/trip/IndependentTripPlanIntegrationTest.java`.
- Proves: AC4/7/8/9/11. Book a partial plan, then change planning dates/party and add/edit a previously unselected component. Purchased snapshot responses/grand total remain byte-for-byte equivalent except explicit booking status; protected component replace/remove fails through canonical and legacy routes. Rename/promotion work without changing purchases. Cancellation restores purchased seat count and booked nights, and cutoff uses frozen departure.
- Inputs/fixture: 2-person purchased airfare, then editable plan becomes 3 people/different dates with unconfirmed stay; separately purchased stay/rental fixtures and historical canceled bookings. Migration fixture with null planned FK uses documented trip fallback once, then frozen fields.
- Doubles or boundary isolation: controllable Portland departure clock, before/after inventory queries, simulated purchases only.
- Edge cases: midnight boundary; before/after new planning start differs from purchase start; forced JSON omission/replacement of protected slots; protected historical components; extra unconfirmed component does not trigger incremental purchase; one-active-booking remains; idempotent cancellation restores inventory once; trip cancel stays immutable; any booking history continues to block trip/plan deletion. Preserve existing race/idempotency and failure rollback tests.

### 5. `planAndPurchaseTalliesUseTheirOwnPricingContexts`
- Type: tally unit/HTTP integration.
- Location: `src/test/java/app/detour/trip/ItineraryTallyEngineTest.java`, `TripPricingAndTallyIntegrationTest.java`.
- Proves: AC4/8/13. Unconfirmed fares use plan count; confirmed fares use purchased count; stay/night and rental-cycle facts preserve snapshots. Zero/missing selections yield explicit partial status, missing pricing remains unavailable, and purchase total stays frozen.
- Inputs/fixture: complete plan, each missing component, empty plan, mixed booked/unconfirmed context and shared trip budget.
- Doubles or boundary isolation: pure sanitized selections for units and isolated seeded DB for response mapping.
- Edge cases: no budget/over budget, rental duration rounding, changed catalog prices do not mutate saved/confirmed facts merely by GET, optional rental does not become required to book due to display partial labeling.

### 6. `selectingNamedPlanOnlyChangesViewAndSupportsAccessibleOverflow`
- Type: Vitest DOM interactions and API-call assertions.
- Location: `frontend/src/IndependentTripPlans.test.tsx` (new), `frontend/src/ProgressiveTripBuilder.test.tsx`.
- Proves: AC2/3/14. Primary first with text badge, named alternatives, selected panel dates/party/slots/tally, pure selection and independent controls.
- Inputs/fixture: many long named plans including a named primary and booked alternative, with differing party/dates.
- Doubles or boundary isolation: typed canonical fixtures, mocked trips API; assert zero POST/PUT/DELETE during clean switching.
- Edge cases: tablist/tab/panel ARIA links, aria-selected/tabIndex, arrow/Home/End focus, Enter/Space activation, All plans selector exposes every ID, deleted history ID falls back to primary, new opening defaults primary, focus remains usable after reorder/promotion/deletion. jsdom proves semantics and selector reachability; visual clipping requires optional viewport observation.

### 7. `dirtyPlanTransitionsSaveDiscardOrCancelWithoutLosingFailures`
- Type: Vitest controlled async UI tests.
- Location: `frontend/src/IndependentTripPlans.test.tsx`, `DraftPromotion.test.tsx`, `ProgressiveTripBuilder.test.tsx`, `App.test.tsx`.
- Proves: AC4/6/7/10/14. Save targets selected UUID and unchanged name; dirty switch/copy/promote/delete/compare/review/exit requires Save/Discard/Cancel. Successful save permits transition; failed save/conflict keeps input/selected plan and error without falsely updating persisted baseline. Discard uses existing server baseline; Cancel does nothing.
- Inputs/fixture: deferred save response, rejected network/session/409 response, parent refresh arriving during edits, user changes during an in-flight request, valid browser history states.
- Doubles or boundary isolation: mocked API, deterministic timers only where required, user-event focus interactions and history push/pop.
- Edge cases: save failed then unrelated action succeeds must not clear error; duplicate click makes one mutation; no component mutation while pending detail save; save then switching cannot apply old response to new editor; focus trapping/Escape/return focus; reload discard consent; logout/other-trip/back guard; beforeunload registered only while dirty/pending. React controls/tally use persisted versus pending facts honestly.

### 8. `managePlansPreservesNamesAndExplainsProtectedDeletion`
- Type: Vitest DOM/dialog/client tests.
- Location: `frontend/src/IndependentTripPlans.test.tsx`, `frontend/src/api/tripsApi.test.ts`.
- Proves: AC5/7/8/9/10/11. Create/copy/rename use exact versions and selected ID; Make primary enabled for booked alternatives; locked component text/controls faithfully show purchased details while planning differs. Protected delete explains confirmed/history rules. Primary deletion lists existing replacement choices and sends chosen ID only after confirmation.
- Inputs/fixture: mixed confirmed/unconfirmed plan, booked and canceled history, sole eligible plan, replacement alternatives and stale conflict response.
- Doubles or boundary isolation: HTTP shape fetch mocks include CSRF; canonical workspace API mocks never mutate real service.
- Edge cases: exact sole prompt "This is the only plan for this trip. Delete the trip?" with Keep trip/Delete trip; Keep/cancel sends no write; history cannot use sole flow; failure retains designation and dialog/errors; copy shows no Confirmed badge/booked associations; protected edit/remove UI absent/disabled and backend still tested for enforcement.

### 9. `compareTwoOrThreeDistinctPlansWithAccuratePartialTotals`
- Type: Vitest comparison/selection/navigation tests.
- Location: `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/components/BookingConfirmationView.test.tsx`.
- Proves: AC12/13/14. Compare plans entry beside tabs; primary plus one works; alternative launch preselects it plus primary; primary launch requires chooser; change to alternatives-only; 2–3 distinct IDs; all plans available including empty/partial.
- Inputs/fixture: primary, three named alternatives, booked/history flags, different dates/party, missing/absent tally and zero selections.
- Doubles or boundary isolation: mocked canonical data and booking calls, screen history.
- Edge cases: no duplicates or fourth selection, fewer than two cannot launch; names/Primary/Booked/date/party, Not selected slots, Partial total vs complete and Total unavailable, semantic desktop table/mobile tab keyboard; booking review returns to selected plan/comparison; old comparedOptionIds/reviewOptionId history IDs still resolve after promotion.

### 10. `primaryEditsAndBookingsPersistAcrossRestartAndProfileNavigation`
- Type: file-H2 restart integration, frontend regression, packaged HTTP smoke.
- Location: `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`, `src/test/java/app/detour/booking/BookingApplicationRestartIntegrationTest.java`, `frontend/src/components/TripListSection.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `scripts/verify-packaged-release.mjs`.
- Proves: AC1/7/14. Save different parties/dates, promote booked alternative, stop/restart, reload canonical IDs/pointer/names/snapshots/history and cancellation metadata. My Trips opens correct trip; summary primary facts and booking facts are separate.
- Inputs/fixture: synthetic accounts/trips/plans/bookings persisted only in disposable test database.
- Doubles or boundary isolation: existing restart harness and packaged verifier's own spawned server; UI mocked profile response.
- Edge cases: primary booking resolves despite not appearing in legacy savedOptions projection; historical detail navigation; trip duplicate creates new unconfirmed identities and a valid primary; comparison back/forward; owner isolation and CSRF remain enforced.

## Safe Verification Commands
- Focused red: `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest#savingAlternativeChangesOnlyItsOwnDatesPartyAndSelections" test`.
- Migration/purchased-context: `.\mvnw.cmd -DskipFrontend=true "-Dtest=TripModelForwardMigrationIntegrationTest,BookingCancellationIntegrationTest,BookingSchemaIntegrationTest" test`.
- Related backend: `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest,TripApiIntegrationTest,TripPricingAndTallyIntegrationTest,BookingApiIntegrationTest,BookingConcurrencyIntegrationTest" test`.
- Broad backend during development: `.\mvnw.cmd -DskipFrontend=true test`.
- Focused frontend: `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx src/DraftPromotion.test.tsx src/ProgressiveTripBuilder.test.tsx src/ItineraryComparisonAndBookingReview.test.tsx src/api/tripsApi.test.ts`.
- Frontend type/asset gate: `npm --prefix frontend run build`.
- Full frontend suite: `npm --prefix frontend test`.
- Full safe build/test suite: `.\mvnw.cmd clean verify` (includes backend tests and production frontend build, but not Vitest; run full frontend suite separately).
- Packaged release: `.\scripts\verify-packaged-release.ps1` after clean verify; disposable local server/database only.
- Commands are planned, not executed. Record actual commands, outcomes and environmental limitations in implementation/review Step Reports. If a command cannot run, report NOT RUN and its residual risk; do not replace it with a claimed pass.

## Optional Developer Checks
- At approximately 320 CSS pixels and with many long names, inspect reachable tabs/All plans, header controls, full keyboard focus and dialog layout. Screen-reader check announces Primary, selected tab, confirmed status and save/conflict errors.
- On desktop/mobile compare primary plus one and alternatives-only, and inspect purchased dates/party/prices alongside changed planning details. Use fictional local test data and the application's existing cancellation workflow.
- These checks are nonblocking supplements to required automated evidence and are not claimed as performed.

## Exit Criteria
- [x] Red canonical independent-save test fails on the unimplemented baseline for missing capability, then passes after implementation.
- [x] Migration preserves every source plan selection/name/public ID and booking association, including absent/empty Working and unknown legacy ages; latest schema test passes.
- [x] Every committed surviving trip has one reachable primary and at least one plan, including after races and injected rollback failures.
- [x] Foreign owners/children are rejected, unsupported writable booking/primary fields are rejected, and stale writes retain saved data.
- [x] Confirmed and historical purchased components cannot be changed/removed through any supported route; edited plan details do not change purchase totals, cutoffs or inventory restoration.
- [x] Dirty transition tests cover Save/Discard/Cancel, failures and stale/in-flight responses, with no silent overwrite/discard.
- [x] Comparison and tab accessibility semantics, launch defaults, bounds and partial prices have executable evidence.
- [x] All 14 acceptance criteria map to test cases above and the implementation plan traceability table.
- [x] New/updated tests and both full Java/frontend suites pass; production build and isolated packaged verifier pass.
- [x] Old booking/history/canceled-trip/ownership/capacity/idempotency/restart regressions remain covered; obsolete Working-only assertions are deliberately replaced.
- [x] Routine tests never access the development DB, real accounts, suppliers or payments.
- [x] Optional observations and any unrun gate are reported honestly without representing them as passed.


## Step 4 execution
The implementation plan's **Executable acceptance evidence (Step 4)** maps all 14 AC to actual test methods and UI cases. Its **Final receipts and handoff** records exact commands, counts, failures repaired, release verification, and the final Profile-only refinement after the clean gate. Final executable outcomes: Java clean verify 216/216; final Vitest 212/212 in 18 files; final TypeScript/Vite build; refreshed packaged JAR; isolated packaged lifecycle/restart verifier; whitespace diff check all pass. Baseline canonical save capability was verified red (404 vs expected 201) before implementation and green afterward.

Migration fixtures include V18 snapshots/connecting flights, V20 Working UUID preservation, oldest option fallback rather than booked preference, no-plan fallback, unknown age storage, frozen booked context, and failed missing-night conversion with source preservation/restart diagnostic. Canonical mutation tests include injected sole-trip and primary-pointer rollback plus synchronized promotion/promotion, promotion/delete, and booking/delete races. Frozen cutoff/restoration and stay/rental/unconfirmed-airfare tests remain covered. UI tests exercise three-action dirty transitions, real Profile/logout/other-trip history, late refresh suppression, in-flight duplication prevention, tab/overflow keyboard semantics, comparison bounds/partial totals, booked locks and purchased party/details. Old inventory/idempotency/cancellation/history/ownership/restart regressions pass.

Optional developer checks above remain NOT PERFORMED; no live database/accounts/suppliers/payment services were used. Independent Step 5 review remains required under the Full profile.
