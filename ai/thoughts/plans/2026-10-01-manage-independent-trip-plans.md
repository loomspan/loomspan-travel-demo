# Independent Trip Plans Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-10-01-manage-independent-trip-plans.md`
- Research: `ai/thoughts/research/2026-10-01-manage-independent-trip-plans.md`
- Outcome: Each trip has one persisted preferred primary and independently editable named plans, with protected purchased facts and discoverable navigation, management, and comparison.
- Execution: confirmed Full 5-Step Pipeline (`full`); this artifact covers design only.

## Current State
Verified the current checkout after research: only the research artifact is untracked; no implementation changes exist. `Trip`, `TripDraft`, and `PlannedItinerary` divide one Working draft from saved snapshots. `TripService.changeWorkingDates`, `replaceSharedDetails`, `saveOption`, `updateOption`, and `loadOption` operate through the Working draft. Dates exist on alternatives, but travelers remain on Trip. `TripResponse` exposes Working/saved aliases. `JdbcTripRepository` owns child snapshot writes and optimistic aggregate versions. V19 preserves surplus drafts and enforces one draft; V20 renames recovered options.

`BookingTransactionExecutor.restoreInventory` uses the current trip traveler count; `bookedStartDate` uses current option dates. Both `BookingService.toBookingResponse` and `TripService.toBookingResponse` price booking selections with current trip travelers/budget. Any booking history blocks option mutations/deletion. These dependencies must change before plan party/date editing becomes available.

`TripWorkspace` always selects `workingPlan ?? drafts[0]`, autosaves details, immediately persists component changes, and copies options into Working. `screenHistory` records comparison/review IDs but no selected plan. `ItineraryComparisonView` supports accessible mobile tabs but compares only saved options. `ProfileScreen` and trip summaries consume trip dates and legacy counts. Maven runs npm install/build, Java tests use isolated H2 and controllable clocks, and Vitest uses jsdom/testing-library.

## Desired End State
Opening a trip defaults to its primary. Selecting a tab is a view operation. Each plan retains a UUID, name, version, dates, party, and selection snapshots. Save edits that plan; Rename changes only its name. Empty and partial plans are valid planning objects. Booked status is independent of primary status. Existing booking/history routes remain reachable by their preserved UUIDs.

Create requests a name and starts an empty alternative using the selected plan's dates/party as initial values; it neither copies components nor changes primary. Copy requests a name and duplicates planning facts/selections into a new UUID, without any booking links or confirmed flags. A copied purchase snapshot is an unconfirmed planning selection subject to ordinary eligibility checks before booking. Existing March 2027, duration, party, age, budget, capacity, adult/driver and cancellation rules continue to apply.

Trip name, destination, budget, ownership, and canceled status remain trip-level organization. The ticket explicitly moves dates, travelers and selections to plans; it does not request different destinations or budgets. Label budget controls explicitly as Trip budget and keep their persistence outside plan save. Retain current destination-change restrictions when alternatives/history exist rather than silently propagating destination changes. Trip summary dates/party/tally project the preferred primary; active/latest booking reference/status remains a separate history concept. Promotion must not rewrite trip name or booking facts.

## Scope
### In scope
- Forward migration of Working, options, travelers, primary, and purchased metadata.
- Plan-scoped search/edit/save/create/copy/rename/promote/delete, owner checks, optimistic conflicts and rollback.
- Confirmed/historical component locks, faithful booking details, unchanged booking/cancellation side effects.
- Workspace plan navigation, explicit Save, guarded transitions, comparison, browser history and My Trips compatibility.
- Required tests, packaged verifier and user documentation.

### Out of scope
- Real suppliers/payments, new reservations on an already actively booked trip, amendment/rebooking, history cleanup, catalog/date expansion, unrelated My Trips redesign, or per-plan destinations/budgets.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`. Ticket requirements and existing authorization, validation, cancellation and history contracts govern this change.

## Impact and Risk Analysis
- **Persisted data:** Working has catalog references while options have snapshots. Migrate Working facts using V19's descriptive snapshot conversion, preserve draft public UUID and existing option numeric/public IDs, and fail visibly if a referenced selection cannot be faithfully converted. Never erase selections to make migration pass. Unknown legacy ages remain unknown, and must be supplied before operations requiring age eligibility.
- **Purchased facts:** Freeze booked dates, traveler count/ages and budget at migration/purchase. Existing immutable option/trip metadata supplies the best available historical facts; null option links fall back to trip dates. Component purchase snapshots and stored purchase grand total remain authoritative. Planning changes must not alter display, cutoff or seat restoration.
- **Concurrency:** Retain aggregate CAS even across different plans; no automatic last-write-wins merge. Child versions additionally guard the edited/source plan. Promotion/replacement deletion/book/cancel share aggregate version so exactly one competing mutation wins. Failures roll back parent, children and pointer changes together.
- **Lifecycle:** A primary reference is the sole designation, avoiding multiple boolean primaries. New-trip creation and last-plan deletion are atomic; no committed API state may have an empty plan list or missing primary. A foreign replacement ID is a 404, and the server rechecks booking history after concurrency control.
- **UI state:** Explicit Save replaces details autosave for plan fields, so Save/Discard/Cancel can be truthful. Responses are scoped by selected plan ID and request generation; stale responses cannot replace dirty input or claim a failed save persisted.

## Implementation Approach
### Storage and ownership
Use existing `detour_planned_itinerary` and its descriptive snapshot tables as canonical storage for every plan. This preserves existing booking FKs and option IDs while avoiding two mutable representations and cross-table primary rules. Introduce a `TripPlan` domain/response model; old Draft/Planned response shapes become compatibility projections, not independent writable stores. Extend canonical rows with traveler count and a plan-traveler child table. Add `detour_trip.primary_plan_id` and a same-trip reference (unique `(trip_id,id)` on plans and composite FK from trip ID/pointer where supported by H2). The pointer may be temporarily null only within create/delete transactions due to immediate FK checks; all public service writes must set/validate it before commit. Migration validates one reachable pointer for every retained trip. This is a service-enforced at-least-one invariant, reinforced by the FK's same-trip membership and a single pointer's at-most-one property; do not claim the nullable column alone guarantees existence.

Keep V12–V20 unchanged. Add V21 forward migration. Preserve old draft tables as inactive migration evidence rather than destructively dropping them in this release; all runtime reads/writes must use canonical rows and never resynchronize archived drafts. Migrate each existing Working to a canonical row with the same UUID and name `Working plan`, including empty/incomplete Working. Existing option names, snapshots, versions and IDs remain unchanged. Clone trip travelers into each plan. For a V20 trip with no Working, choose the oldest existing option by internal ID as primary without changing its data/status; if no plan exists create an empty `Primary plan` with trip dates/party. Never favor a booked plan merely because it is booked. V18 fixtures still pass through V19's existing Working recovery first.

Before allowing edits, extend booking rows/booking traveler storage with purchased party/date/budget facts and backfill from the linked option/trip. Snapshot these facts at every future purchase. Normalize booking response construction into one owner so trip detail and booking-history endpoints cannot diverge.

### API and mutation semantics
Add canonical `/api/trips/{tripId}/plans` create, `/{planId}` save, `/{planId}/name` rename, `/{planId}/copy`, `/{planId}/primary`, delete, and plan component search/select/remove/readiness surfaces. Strict request parsers reject owner, primary, confirmed or booking-association fields supplied as writable data. Save carries expected aggregate/plan versions and dates/party plus only accepted unconfirmed selections; it never implicitly changes name. Component operations retain current immediate-save behavior and expected versions, while the frontend saves pending details before launching them. Reuse existing catalog validation/revalidation helpers with plan context rather than trip party. Date/party changes revalidate only unconfirmed components, returning existing removal/adjustment summaries.

A single server-owned protection helper derives locked component types from booking snapshots/history. Reject changes/removal of purchased components on every API, including legacy routes, even if the plan payload omits/replaces those fields. Do not recompute purchased prices or capacity from edited party. For display/tally, purchased airfare uses its purchased party; unconfirmed airfare uses plan party. Historical purchased components remain protected under existing historical restrictions and show their canceled/history status accurately. Rename and promotion of a booked plan are permitted because they do not modify purchases. Adding an unconfirmed missing component remains allowed; the existing one-active-booking rule remains, with no new add-to-reservation workflow.

Delete alternatives by expected versions and confirmation. Delete-primary requires a distinct same-trip replacement; atomically move pointer and delete target, without touching reservation rows. Reject any target with booking history, whether active or canceled. The sole-plan path calls existing trip-deletion policy with current version/count confirmation; never delete the child first. Reject sole-plan DELETE without explicit trip-delete consent. Cancellation or failure changes nothing.

Keep existing trip URL and review/booking IDs. Add `plans` and `primaryPlanId` to responses; top-level planning summary projects primary. Preserve legacy response aliases as documented projections (`workingPlan`/`drafts` = primary, `savedOptions`/`planned` = other plans, `alternatives` = all distinct plans). These aliases must not drive booking lookup: existing option IDs resolve through canonical plans even after promotion. Legacy draft component/search routes resolve preserved UUIDs to the same plan and apply all protections. Legacy Working details routes target primary only. Retire option `/load` and copy-from-Working replacement writes with explicit conflict errors directing callers to plan APIs; never retain a silent overwrite backdoor. Legacy named-save may delegate to explicit copy of primary. This is a deliberate lifecycle contract change; the ticket requires navigation/data compatibility, not continued use of the old copy-and-replace editor.

### UI and transitions
The workspace owns selectedPlanId, defaulting to primary when newly opened; restore a valid history ID for Back/Forward. Put primary-first scrollable tabs immediately below header, plus an All plans native selector for overflow and Compare plans/plan actions. Use labeled tablist/tabs/panel, roving focus, arrow/Home/End navigation and visible text Primary badge. Manual tab activation (Enter/Space) avoids prompting merely while moving keyboard focus. Dialogs trap focus, restore it, and announce save/conflict errors.

Maintain a persisted baseline and pending details keyed by plan UUID. Save retains name/identity; Discard restores the baseline; Cancel aborts the requested transition. Gate switching, copy, create, promotion, deletion, comparison/review and workspace exit/history transitions when local edits would be lost. While a mutation is in flight wait for its outcome or disable the dependent action; don't treat pending work as safely persisted. Save-and-continue executes only after a successful response. On failure retain input and transition target, show retry/reload choice; reload needs explicit discard consent. Native beforeunload protects browser reload/close, where a custom three-action dialog is unavailable. Coordinate `ProfileScreen` exit/logout/other-trip navigation through the same workspace guard.

Show confirmed component snapshots with status and locked controls even when editable dates/party differ; show purchased dates/party alongside the changed planning context. Render a consistent selected-plan details/slots/tally layout. Remove editingOptionId, load dialogs and bottom saved-option editor workflow from normal navigation. Book/review accepts any canonical plan ID including primary.

Compare plans opens a selection chooser beside tabs. From alternative preselect primary plus selected alternative; from primary preselect primary and request at least one additional selection. Allow two or three unique IDs and alternatives-only combinations. Comparison includes names, primary/history/booked indicators, per-plan party/dates and accurate component facts. Missing components say Not selected; totals say Partial total whenever any component is absent (zero-selection plans have a partial $0 selected total, never a complete itinerary). Preserve Total unavailable for absent pricing. Rental remains optional to book under existing rules; comparison completeness labeling is presentation, not a new booking eligibility rule.

## Phase 1: Canonical storage and purchased metadata
### Changes
- [x] `src/main/resources/db/migration/V21__independent_trip_plans.sql` — canonical party/traveler storage, Working snapshot conversion, primary reference/backfill/validation, and frozen booking metadata. Reuse all V19 descriptive conversion fields and validate selected stay-night counts before abandoning old reads.
- [x] `src/main/java/app/detour/trip/TripPlan.java`, `Trip.java`, `TripRepository.java`, `JdbcTripRepository.java` — canonical hydration, primary access, snapshot/party persistence and transactional creation; retain preserved UUIDs.
- [x] `src/main/java/app/detour/booking/BookingRecord.java`, `BookingRepository.java`, `JdbcBookingRepository.java`, `BookingResponse.java`, `BookingService.java`, `BookingTransactionExecutor.java`, `src/main/java/app/detour/trip/TripService.java` — load/write frozen purchased metadata, remove mutable-plan dependency from restoration/cutoff/booking pricing, reuse one booking response mapper.
- [x] `src/test/java/app/detour/trip/TripModelForwardMigrationIntegrationTest.java`, `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java` — preserve data/link fixtures and freeze purchased-context regressions.
### Automated verification
- [x] `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest,TripModelForwardMigrationIntegrationTest,BookingCancellationIntegrationTest,BookingSchemaIntegrationTest" test` — 31 tests passed; final clean verify also passes the later V20/failure additions.
### Optional developer checks
- None; synthetic migration evidence is mandatory. Do not open the local development database.

## Phase 2: Owner-scoped independent lifecycle and booking protections
### Changes
- [x] `src/main/java/app/detour/trip/TripRequests.java`, `TripController.java`, `TripService.java`, `TripRepository.java`, `JdbcTripRepository.java`, `TripResponse.java`, `PlanResponse.java` — canonical contracts/mutations, owner+aggregate+child version checks, independent details, rename, empty create, copy without associations, promotion and protected atomic deletion.
- [x] `src/main/java/app/detour/trip/TripService.java` — refactor search/select/remove/readiness/revalidation/snapshot/tally/duplicate-trip/profile paths to canonical plan context; remove draft-first assumptions and immutable-party workaround. Keep trip settings explicit.
- [x] `src/main/java/app/detour/trip/ItineraryTallyEngine.java`, `ItineraryTallyResponse.java` — distinguish missing/partial and confirmed-vs-unconfirmed pricing contexts; preserve stored booking grand total.
- [x] `src/main/java/app/detour/booking/BookingTransactionExecutor.java` — book primary/alternative canonical plans using plan party/dates; retain deterministic inventory locks, idempotency, active-booking uniqueness and cancellation eligibility.
- [x] `src/test/java/app/detour/trip/IndependentTripPlanIntegrationTest.java`, `TripApiIntegrationTest.java`, `DraftReadinessAndPlannedSnapshotIntegrationTest.java`, component search tests, `TripPricingAndTallyIntegrationTest.java`, `TripApplicationRestartIntegrationTest.java`, booking API/concurrency/restart tests — prove independent operations and adapt obsolete lifecycle assertions without deleting protection coverage.
### Automated verification
- [x] `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest,TripApiIntegrationTest,DraftReadinessAndPlannedSnapshotIntegrationTest" test` — 66 tests passed; final clean verify covers pricing and booking contention.
- [x] `.\mvnw.cmd clean verify` — all 216 backend tests pass, including final canonical primary/profile and duplication assertions (supersedes the phase broad test command).
### Optional developer checks
- None.

## Phase 3: Independent accessible workspace and guarded edits
### Changes
- [x] `frontend/src/api/tripsApi.ts`, `frontend/src/api/tripsApi.test.ts` — canonical plan shapes/client calls, CSRF/version payloads, typed protected status and error contracts.
- [x] `frontend/src/components/TripWorkspace.tsx`, `PlanNavigation.tsx`, `frontend/src/screenHistory.ts` — selected UUID, canonical editor, explicit Save, three-action transition guard, plan management dialogs, primary-first accessible overflow navigation and history restoration.
- [x] `frontend/src/components/ProfileScreen.tsx`, `App.tsx` — coordinate workspace dirty exits/other-trip/logout/history; preserve trip/profile links and guest flow.
- [x] `frontend/src/components/AlternativeCard.tsx`, `BookingReviewView.tsx`, `BookingConfirmationView.tsx`, `BookingHistorySection.tsx`, component selection sections, `ItinerarySummaryTally.tsx` — remove copy/replace instructions, apply component locks and frozen purchased context, book any plan, selected-plan tally and partial labels.
- [x] `frontend/src/style.css` — use existing navigation/dialog tokens, horizontal overflow, visible focus and narrow-screen controls.
- [x] `frontend/src/IndependentTripPlans.test.tsx`, `DraftPromotion.test.tsx`, `ProgressiveTripBuilder.test.tsx`, `FeeFreeCancellationAndTriage.test.tsx`, `components/BookingConfirmationView.test.tsx` — navigation, save/discard/cancel, management/locks, response races and dialog focus.
### Automated verification
- [x] `npm --prefix frontend test` — final 212 tests pass, including all planned focused files and canonical guards; focused real Profile/history regression also passes.
- [x] `npm --prefix frontend run build` — TypeScript and production asset build pass.
### Optional developer checks
- [ ] At a narrow viewport, many long named plans remain reachable; keyboard focus and dialog return focus are visible, and a screen reader announces Primary/Confirmed/errors correctly. These observations supplement executable accessibility semantics tests.

## Phase 4: Comparison, consumer compatibility and release verification
### Changes
- [x] `frontend/src/components/ItineraryComparisonView.tsx`, `TripWorkspace.tsx`, `ItinerarySummaryTally.tsx`, `TripListSection.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `components/TripListSection.test.tsx` — canonical 2–3 plan chooser, launch defaults, primary/booked indicators, partial totals, summary/navigation compatibility.
- [x] `scripts/verify-packaged-release.mjs`, `README.md` — exercise/document canonical plans, identity preservation, preferred-primary persistence, independent dates/party, copy without associations, booking/cancel/restart and protected deletion. Stop documenting the required Working copy/replace workflow.
- [x] `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`, `src/test/java/app/detour/booking/BookingApplicationRestartIntegrationTest.java` — reopen primary/edits and booking details after file-backed restart.
### Automated verification
- [x] `npm --prefix frontend test` — full UI/client regression suite passes.
- [x] `.\mvnw.cmd clean verify` — full backend suite and bundled frontend build pass.
- [x] `.\scripts\verify-packaged-release.ps1` — packaged application supports owner isolation, canonical lifecycle, booking/cancellation and restart using only its temporary server/database.
### Optional developer checks
- [ ] Compare primary plus one alternative and an alternatives-only set on desktop/mobile; inspect historical confirmation beside changed planning dates/party. Use fictional local test data only.

## Test Strategy
Use HTTP integration tests and direct DB assertions for ownership, versions, invariants and snapshot preservation; two synchronized same-version operations for races; injected repository failure for rollback. Use target-V20 migration fixtures, V18 lineage regression, absent/incomplete Working and dangling nullable historical option links. Use Vitest testing-library with controlled promises/fake timers for guarded transitions and stale responses. Preserve catalog eligibility, booking inventory/idempotency/cancellation and profile navigation coverage; update obsolete assertions deliberately. Full details and command gates are in the companion testing plan.

## Acceptance-Criteria Traceability
Acceptance criteria are numbered in their ticket order.
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| AC1 exactly one primary; legacy preservation | V21 migration; canonical load/create | migration fixtures, new trip invariant, restart |
| AC2 top accessible named navigation | PlanNavigation, TripWorkspace | tab semantics, keyboard/overflow reachability |
| AC3 view-only switching | selectedPlanId and history | no mutation requests or name/identity changes |
| AC4 independent edit/save/rename | plan service/repository; plan party/revalidation | sibling snapshots unchanged; identity/name retention |
| AC5 create/copy independence | create/copy with new UUID and no booking rows | empty create; booked-source copy unconfirmed |
| AC6 dirty/failure/conflict/ownership | transition guard, CAS, principal ownership | three decisions, failed save, response races, 404/409 |
| AC7 booked promotion persistence | primary pointer mutation | booked promotion, unchanged booking/inventory, restart |
| AC8 protected accurate confirmed items | snapshot-derived lock/helper; purchased metadata | API tampering and UI locks; mixed-context tally/cancellation |
| AC9 protected deletion/history/canceled trips | existing eligibility plus plan service | active/canceled/history and canceled-trip regressions |
| AC10 replacement primary atomicity | transactional replacement+delete | missing/foreign replacement; cancel/failure/race invariant |
| AC11 sole-plan trip deletion | consent flow delegates trip deletion | exact prompt/actions; version/count/history guards |
| AC12 comparison selection and placement | top chooser and comparison | launch defaults; 2–3 unique; alternatives-only |
| AC13 names/status/dates/prices/partial | comparison and tally model | per-plan pricing; Not selected; Partial total; unavailable tally |
| AC14 navigation/reopen persistence | preserved UUID routes, screenHistory/profile | review/history/back/restart and packaged verifier |

## Risks and Rollback/Recovery
Use a forward-only migration and application/schema release together. Back up a real database before deployment; do not run the old application against the migrated schema because its writes would ignore canonical plans. Archived draft rows are evidence, not a downgrade path. A migration failure must leave source selections/history available for diagnosis and must not silently drop facts; H2 DDL is not generally transactional, so stage validation before destructive actions and test failure/restart behavior explicitly. Application mutations use transactional rollback; UI failures retain persisted baseline and pending input. Recover a deployment through a database backup plus previous application, or a tested corrective forward migration. Do not delete booking history to recover.

## References
- Ticket and research paths above; `ai/thoughts/design-lens.md`.
- `src/main/resources/db/migration/V19__model_named_trips_and_dated_options.sql`, `V20__rename_recovered_options.sql`.
- `src/main/java/app/detour/trip/{TripService,JdbcTripRepository,TripController,TripRequests,TripResponse}.java`.
- `src/main/java/app/detour/booking/{BookingService,BookingTransactionExecutor,JdbcBookingRepository}.java`.
- `frontend/src/components/{TripWorkspace,ProfileScreen,ItineraryComparisonView}.tsx`, `frontend/src/screenHistory.ts`, `pom.xml`, `frontend/package.json`, `README.md`.


## Implementation decisions (Step 4, 2026-10-01)
- The server always emits canonical plans. The previous workspace component is retained as a compatibility rendering branch for legacy response consumers/fixtures without `plans`; real V21 responses use `IndependentPlansWorkspace`. Legacy component UUID routes resolve canonical plans, and canonical component aliases use the same owner/version/protection checks. Obsolete load/replace routes return `PLAN_WORKFLOW_RETIRED` without writes.
- Tests that inserted live draft rows now use `TestPlanSelections` to build canonical snapshots; historical migration fixtures still insert actual old schema rows. Existing regressions retain their ownership, inventory, history, pricing, and rollback expectations; only obsolete Working-copy assertions were deliberately replaced.
- Aggregate CAS runs before dependent booking rows and inventory writes. This serializes purchase against deletion/promotion; concurrent idempotent callers recover the already committed purchase. Source child versions stay unchanged on copy/promotion because those operations do not edit that source's facts.
- Canonical aggregate hydration holds trip-row locks in a transaction through the parent pointer and child snapshot reads. Detail reads lock only their owned trip; profile reads lock trip IDs in deterministic order and hydrate only that captured set, leaving concurrently created trips for the next refresh. Existing aggregate CAS remains authoritative for writes, and plan actions reject stale aggregate versions before interpreting changed primary/deletion requirements.
- Copy retains planning snapshot facts and strips purchase associations. If frozen source selections no longer match copied planning dates, purchase rejects `PLAN_SELECTIONS_INVALID` until unconfirmed components are reselected; purchase never silently refreshes stored prices or books dates different from planning.
- Canceled-trip duplication remains available and includes the canonical primary among source choices. Destination and budget remain trip-scoped; budget updates preserve names and sibling dates/party. Unknown migrated ages render blank and require explicit valid ages before a details save.
- No live DB, supplier, payment, or model service was used. Verification databases/accounts are disposable synthetic fixtures. Optional visual/screen-reader observations below remain NOT PERFORMED; independent Step 5 review is still required.

## Executable acceptance evidence (Step 4)
`IP` below is `src/test/java/app/detour/trip/IndependentTripPlanIntegrationTest.java`; `UI` is `frontend/src/IndependentTripPlans.test.tsx`. These are executable assertions, not a replacement for independent review.

| AC | Code and executable evidence |
|---|---|
| 1 | V21, `JdbcTripRepository.loadTrip`; `TripModelForwardMigrationIntegrationTest` fresh/V18/V20 UUID, oldest option, booked links, absent/empty Working and unknown-age fixtures; `IP.createCopyRenamePromoteAndDeletePreserveIndependentIdentities` invariant checks. |
| 2 | `PlanNavigation`, focus/overflow CSS; UI primary-first tabpanel relationship, arrows/Home/End/manual activation and All plans long-name test. |
| 3 | UUID-selected workspace; UI verifies zero legacy load/save/promotion requests during navigation; `TripApiIntegrationTest.retiresCopyAndReplaceWithoutAnyWrites`. |
| 4 | canonical save/name and plan party tables; IP independent save + identity/name/copy lifecycle; frozen airfare/stay/rental tests; existing snapshot/price adjustment regressions. |
| 5 | create/copy service; IP empty/partial and lifecycle checks, purchased copy has no booked/locks; packaged HTTP copy asserts no associations. |
| 6 | aggregate/child CAS + guarded workspace; IP stale and foreign UUID/rejected writable fields; UI Save/Discard/Cancel, failed/conflict input retention, duplicate pending click/refresh suppression, parameterized CRUD/compare/review/back guards, parent exit/popstate/beforeunload; save-then-promote uses newly saved versions. |
| 7 | primary pointer service/FK; IP booked promotion + invariant, synchronized same-version promotion race; UI booked promotion; `TripApplicationRestartIntegrationTest.persistsTripAndDraftAcrossApplicationRestart` now persists distinct dates/party and promoted UUID; packaged booked promotion/restart. |
| 8 | shared locked-component helper, frozen response/tally, search/remove controls; IP frozen airfare dates/party/price + payload omission and legacy route block, confirmed stay/rental locks with missing airfare editable; UI Confirmed flight, disabled search/remove, purchased party/total/detail access. |
| 9 | history-protected deletion + existing cancellation services; IP canceled/history delete blocked, purchased cutoff clock and original-seat restoration; full booking cancellation/canceled-trip/API regressions. |
| 10 | transactional replacement/delete; IP missing/foreign/stale replacement + injected post-pointer failure rollback, synchronized promotion/delete and purchase/delete races; UI exact replacement/version/count and cancel behavior. |
| 11 | explicit `deleteTrip`, plan count/version + existing trip eligibility; IP missing consent/stale count and injected trip-delete rollback; UI exact sole prompt and Keep trip sends no mutation. |
| 12 | top Compare plans chooser; UI launch primary+alternative and alternatives-only selection, fourth disabled, 2–3 bounds; comparison regression suite covers side-by-side and keyboard/mobile semantics. |
| 13 | comparison attributes + partial tally; UI Not selected/Partial zero totals, primary/booked/date/party labels; comparison regressions retain money/budget and unavailable-tally assertions. |
| 14 | preserved UUID routes, profile/screenHistory, frozen booking mapper; UI historical details and parent/history restore, profile/list/review regressions, file-H2 trip/booking restart and packaged authentication/reopen/history/owner isolation. |

## Verification receipts (Step 4)
Baseline red: `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest#savingAlternativeChangesOnlyItsOwnDatesPartyAndSelections" test` failed expected 201/actual 404 for missing canonical create; the same focused command later passed.
Development broad failures were ticket-attributable obsolete fixtures/retired-route expectations or newly exposed behavior bugs, repaired before final gates. Final receipts are recorded below.


### Final receipts and handoff
- PASS — `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest,TripApiIntegrationTest,DraftReadinessAndPlannedSnapshotIntegrationTest" test`: 66 tests (development focused fix gate).
- PASS — `.\mvnw.cmd -DskipFrontend=true "-Dtest=IndependentTripPlanIntegrationTest,TripModelForwardMigrationIntegrationTest,BookingCancellationIntegrationTest,BookingSchemaIntegrationTest" test`: 31 tests (development purchased/migration gate).
- PASS — `.\mvnw.cmd -DskipFrontend=true "-Dtest=TripModelForwardMigrationIntegrationTest,IndependentTripPlanIntegrationTest,BookingLifecycleIntegrationTest" test`: 12 tests actually selected; `BookingLifecycleIntegrationTest` is not an existing class and contributed no tests. Booking coverage is established by the real classes in clean verify, not this mistyped selector.
- PASS — `.\mvnw.cmd clean verify`: 216 tests, zero failures/errors; frontend production build and JAR passed. Receipt: `%TEMP%/independent-cleanverify-release.log`.
- The last production refinement after that clean gate was confined to Profile popstate/dirty coordination: defer restoring history until workspace listeners read the original destination, then bypass the already approved guard. Its new real Profile/logout/cross-trip test reproduced the competing-listener issue and passed after the fix. Backend sources remained unchanged. The full final frontend gate and rebuilt/package gates below verify this final UI version.
- PASS — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'guards real Profile'`: 1 passed, 19 intentionally filtered; then `npm --prefix frontend test`: all 212 tests across 18 files passed (`%TEMP%/independent-ui-release2.log`).
- PASS — `npm --prefix frontend run build`: final TypeScript and Vite assets passed (`%TEMP%/independent-build-release.log`).
- PASS — `.\mvnw.cmd -DskipFrontend=true -DskipTests package`: refreshed JAR with those final frontend assets; tests intentionally skipped here because backend clean verify and final Vitest already passed (`%TEMP%/independent-package.log`).
- PASS — `.\scripts\verify-packaged-release.ps1`: final JAR HTTP lifecycle, independent party/dates, unconfirmed copy, booked promotion, locks, CSRF, idempotent replay, purchased context, cancellation/history, restart and owner isolation. Temporary server/database stopped and removed (`%TEMP%/independent-packaged.log`).
- PASS — `git diff --check`; runtime source search for `detour_trip_draft` has no matches. Reviewable code/tests/docs contain fictional fixtures, no credentials or live data.
- Earlier failures (recorded to avoid representing them as passes): baseline canonical 404; obsolete draft fixture writes/retired lifecycle assertions in broad development gates; new migration fixture missing explicit traveler columns; response mock body reuse/Partial total text assertions; real Profile listener regression. All repaired. One clean verify retry failed npm ci with Windows EPERM unlinking the loaded Rolldown module during concurrent Vitest; running sequentially restored the full gate. No unresolved ticket failures remain.
- Optional narrow-screen/long-name visual inspection and screen-reader announcements, and desktop/mobile comparison/purchased-detail visual inspection: NOT PERFORMED. Executable semantics/focus/navigation tests pass; these are nonblocking developer observations.
- Full pipeline: implementation ready for independent Step 5 review. No deployment, external writes, or live database migration was performed.
