---
date: 2026-10-01
repository: loomspan-travel-demo
branch: main
commit: 1ffcacd5cf204e09e2a3800322d79e6d379e4ff0
ticket: ai/thoughts/tickets/2026-10-01-manage-independent-trip-plans.md
tags: [trip-plans, persistence, migration, booking, concurrency, accessibility]
---

# Independent trip plans Research

## Research Question

How does the checked-out application represent, edit, save, compare, book, and delete Working plans and Saved options, and which current contracts govern the ticket's independently editable plans and preferred primary designation?

Research checklist completed: repository state; model and migrations; owner-scoped APIs; workspace editing and navigation; saved selections and totals; booking snapshots, cancellation and history; deletion and concurrency; comparison/accessibility; tests and operational boundaries; historical evidence. This is source research, with no production edits or implementation recommendations.

## Summary

The application has one editable Working plan per trip, plus named independently dated Saved options stored as selection snapshots. Travelers, destination, and budget belong to the trip. There is no persisted preferred-primary designation. The workspace always edits the Working plan; opening an option replaces its selections and dates with a revalidated copy, and updating an option replaces its snapshots from that Working plan.

Bookings are simulated local database transactions against one Saved option; at most one booking is active per trip. Confirmed components are frozen in separate booking snapshot tables. Any booking history makes its option immutable and undeletable through service operations, including cancelled bookings. Booking response totals, cancellation inventory restoration, and cancellation eligibility still depend partly on mutable trip/option metadata rather than exclusively on booking snapshots.

Comparison currently excludes Working and requires at least two Saved options. The top workspace has no plan selector. Existing migration and test patterns cover data preservation, optimistic conflicts, transactional rollback, owner isolation, simulated inventory contention, restart persistence, accessible dialogs and comparison navigation. Tests have not been executed in this research step.

## Repository State

- Recorded at 2026-10-01T13:40:27-07:00 in `C:\opendev\code\loomspan-travel-demo`.
- Branch `main`, commit `1ffcacd5cf204e09e2a3800322d79e6d379e4ff0`; `git status --short` was empty before this artifact.
- The orchestrator reported no repository/ancestor `AGENTS.md`; this stage follows `1_research_codebase.md` and the shared automation protocol under the confirmed **Full 5-Step Pipeline** (`full`).
- Supplied ticket and command/protocol were read completely before searching. No pre-existing ticket-scoped implementation diff or prior research/plan artifact was supplied.

## Current Behavior and Data Flow

### Storage and compatibility

`Trip.java:7` owns destination, start/end dates, traveler count/ages, budget, label/name, active/cancelled status, aggregate version, draft list and planned list. `TripDraft.java:6` has an ID, version, selections and dates but no saved name, traveler data or primary flag. `PlannedItinerary.java:6` adds a saved name and its own dates/version, but likewise has no traveler data or preferred-primary flag.

`V12__create_owned_trip_and_initial_draft_schema.sql:1` creates owner-scoped trips, trip travelers and drafts. V13 previously allowed multiple drafts. V14 adds lightweight draft component-selection tables and descriptive/priced planned component snapshots; V16 expands snapshot facts. V15 makes trip traveler/draft deletion cascade. V19 restores one draft per trip with `uq_detour_trip_draft_working` (`V19__model_named_trips_and_dated_options.sql:166`), adds dated drafts and named/dated/versioned options, and backfills missing drafts (`:155`). The unique constraint enforces at most one draft; creation/migration establish the application's assumed existence of one, rather than a database constraint requiring every trip to have a child.

V19 chooses the oldest populated draft ahead of empty drafts (`:21`), converts populated surplus drafts into fully described options, validates successful component/night copying (`:128`) before deleting surplus drafts (`:154`), and retains pre-existing planned and booking rows. V20 only renames the generated `Option from Draft <number>` pattern to `Recovered option <number>` (`V20__rename_recovered_options.sql:3`). Trips whose draft was absent before V19 gain an empty dated draft. Post-V19 code repeatedly calls `trip.drafts().get(0)` and does not generally tolerate a missing draft at runtime (`TripService.java:117`, `:202`, `:276`, `:379`).

`JdbcTripRepository.java:79` loads trip travelers, then drafts ordered by internal ID and options ordered by internal ID. Draft selections are hydrated from current catalog data (`:104`); planned selections are loaded from stored snapshots (`:177`). Unknown legacy traveler ages produce a null age list (`:81`). UUIDs are public identities while numeric keys join persistence graphs.

`TripResponse.java:9` exposes the original `drafts`, `planned`, and `alternatives` alongside compatibility aliases `name`, `workingPlan`, and `savedOptions`; the constructor aliases Working to the first draft and options to planned (`:23`). Response tally uses trip traveler count and budget for every plan (`TripService.java:878`). The trip tally prefers the first draft, then first planned fallback (`:904`). `findPrimaryBookingRecordByTripId` means active-first/latest booking, not a preferred plan (`JdbcBookingRepository.java:278`).

### APIs, editing and saving

`TripController.java:27` serves `/api/trips`. It binds the authenticated principal's user ID to operations (`:37`, `:343`), rather than accepting ownership from the JSON request. `TripService.java:719` loads by UUID and owner; child IDs must belong to that aggregate (`:724`, `:736`), returning not-found for foreign/malformed resources. Supplying a planned ID to a draft component API raises `IMMUTABLE_ALTERNATIVE` (`:730`). `TripRequests.java` manually validates allowed JSON fields, types and nonnegative expected versions; unsupported fields are rejected.

Creation validates a named trip, dates, 1–8 travelers with complete ages, and nullable budget; it writes one empty Working plan transactionally (`TripService.java:79`, `JdbcTripRepository.java:29`). Supported dates are March 2027, stays 1–14 nights, ages 0–120, and budget $0–$1,000,000 (`TripService.java:40`, `:762`, `:826`). The age selector normally offers through 95 and allows unchanged older legacy values (`TripWorkspace.tsx:403`). Trip name uniqueness is owner-scoped through a user-row lock and name lookup (`JdbcTripRepository.java:71`); generated trip names may change following Working date edits (`TripService.java:806`).

The existing HTTP surfaces are:

- GET/POST `/api/trips`, GET/PUT/DELETE `/{tripId}`, PUT `/{tripId}/name`, PUT `/{tripId}/working-dates` (`TripController.java:37`–`:63`, `:283`).
- POST `/{tripId}/options`, PUT `/{tripId}/options/{optionId}`, PUT its `/name`, POST its `/load` (`:68`–`:89`).
- Draft search/select/remove endpoints under `/{tripId}/drafts/{draftId}/airfare`, `/stays`, `/rentals`, plus legacy draft lifecycle/readiness and trip-level search endpoints (`:101`–`:278`).
- Alternative duplicate/delete and trip duplicate endpoints (`:96`, `:123`, `:129`); booking/history/cancellation endpoints (`:289`–`:331`).

Working date edits revalidate existing selections and report removals/price/room adjustments (`TripService.java:114`). They update both draft and trip dates (`JdbcTripRepository.java:238`). Shared-detail saves replace the trip destination, party, budget and dates, then revalidate draft selections (`TripService.java:256`). When saved options exist, changing destination or travelers is rejected as `IMMUTABLE_TRIP_PARTY` (`:274`); budget and Working dates can still change. `replaceSharedDetails` updates every draft's dates/version and rewrites trip travelers (`JdbcTripRepository.java:280`). Search, capacity/age eligibility and tallies read trip-level party/budget (`TripService.java:1074`, `:1186`, `:1280`).

Saving a named option creates a new UUID and freezes a structurally valid snapshot of Working selections with its own dates (`TripService.java:375`). Empty Working cannot be saved as an option; at least one component is required, but a complete itinerary or over-budget acknowledgment is not (`:499`). Updating an existing option preserves its ID but replaces all snapshots and dates from Working, and accepts a name (`:391`, `JdbcTripRepository.java:360`). Rename changes only the option name (`TripService.java:415`). Both operations reject any booking history. Opening an option compares option version and requires explicit replacement when dates/selection identities differ, revalidates catalog selections, drops invalid components, and replaces Working without modifying the source option (`:433`, `JdbcTripRepository.java:370`).

Additional draft creation/duplication and alternative duplication currently return `WORKING_PLAN_EXISTS`; draft deletion returns `WORKING_PLAN_REQUIRED`; old draft promotion returns `USE_NAMED_OPTION` (`TripService.java:337`–`:367`, `:567`). Old test names sometimes still mention promotion/duplication even where assertions exercise named save or rejection; names alone are not behavioral evidence.

### Workspace, unsaved state and navigation

`TripWorkspace.tsx:128` always derives active draft from `workingPlan ?? drafts[0]`; `:129` normalizes only Saved options for cards and comparison. Working slots, dates/party/budget form and option save UI are followed by the bottom Saved options section (`:1398`, `:1634`). `AlternativeCard.tsx:79` explicitly tells users to open a copy in Working to edit it. `TripWorkspace.tsx:980` optionally saves Working as another named option, then posts `/load` and remembers `editingOptionId` for subsequent replacement of the original. The dialog has focus restoration, focus trapping, and Escape cancellation (`:162`).

Details autosave after 600 ms, serializes in-flight requests and queues changed input (`TripWorkspace.tsx:483`, `:576`). Component selections/removals call persisted draft APIs directly; the local trip changes only after successful responses. Dirty detection includes details, pending saves/components, conflicts and failed Working component mutations (`:429`, `:449`). Option actions first require saved Working (`:868`). Errors distinguish validation, session expiry, network failure and version conflict (`:530`); option failures remain separately recorded (`:150`, `:905`). Reload replaces local fields with server state and warns that it discards local edits (`:605`, `:1313`). Clean refresh ignores stale responses when edits or a newer version appear (`:461`).

`ProfileScreen.tsx:121` opens a trip by GET, retains its keyed workspace across other app views, and refreshes only when clean. Opening/starting another trip, deletion, and logout currently use native discard confirmation where dirty (`:128`, `:148`, `:186`, `:310`); the existing workspace has no per-plan save/discard/cancel transition. Guest selections have their own save/discard/keep dialog and beforeunload warning (`App.tsx:130`, `:250`). `screenHistory.ts:1` stores trip ID, workspace view, comparison IDs and review option ID in browser history state; there is no selected plan ID. Profile restores trip screens on popstate (`ProfileScreen.tsx:167`); workspace restores comparison/review views (`TripWorkspace.tsx:264`). `/profile` is a public SPA document route, with API authorization kept separate (`SpaRouteController.java:8`).

### Booking, confirmed items, history and cancellation

`BookingService.java:55` owner-loads the trip, handles idempotent repeats and duplicate-key races, then invokes a transaction. `BookingTransactionExecutor.java:54` books a Saved option's selections, requires a pre-departure active trip and eligible adults/drivers, locks catalog inventory deterministically, deducts seats/stay inventory, creates rental occupancy, copies planned snapshots to booking snapshots, and advances aggregate version (`:92`, `:182`). No real provider or payment is called.

V17 freezes purchased component facts and grand total, enforces unique active trip booking and trip/idempotency key (`V17__create_booking_schema.sql:4`). It does **not** persist purchased traveler count/ages or a separate booked start/end date. V18 changes the option FK to nullable `ON DELETE SET NULL` (`V18__add_trip_status_and_cancellation_constraints.sql:7`); service protections, rather than that FK, retain booked options. Trip booking FK still cascades in the schema, with deletion blocked by the service.

`JdbcBookingRepository.java:400` defines `isPlannedItineraryBooked` as any booking row, regardless of active/cancelled status. Active and historical booked options cannot update or rename (`TripService.java:398`, `:421`), and cannot delete (`:587`). The current protection is whole-option immutability, rather than component-level editable/unconfirmed partitioning. A partial option can be booked, freezing all its selected components in the one purchase; no component amendment/additional purchase workflow exists.

Although component snapshots are frozen, several purchased-detail consumers depend on current planning metadata: `TripService.java:843` and `BookingService.java:111` calculate booking tally with `trip.travelerCount()`/budget; cancellation flight-seat restoration increments using that current count (`BookingTransactionExecutor.java:317`); cancellation departure checks resolve the linked option's start date with trip fallback (`:346`). Booking confirmation also receives trip context. Current party and booked-option immutability prevent those fields from changing through normal APIs after saved options exist. This coupling is verified source behavior relevant to independently editable travelers and dates.

Cancellation restores inventory from frozen booking selections, changes the booking to CANCELED and advances trip version without removing history (`BookingTransactionExecutor.java:212`). Trip cancellation requires booking history, releases any active booking and marks the trip CANCELED (`:261`). Canceled trips reject normal mutations through `requireActiveTrip` (`TripService.java:179`), but duplication to a new trip is supported (`:618`). Cutoffs use Portland midnight on the booked option date. Booking history and details are fetched separately and displayed through `BookingHistorySection`, `BookingConfirmationView`, the active-booking banner and review views.

### Deletion, primary and concurrency

There is no Make primary API or designation to promote today. Deleting a Saved option requires confirmation, active trip, no booking history and expected aggregate version (`TripService.java:577`). Working cannot be deleted, so the sole-plan and replacement-primary flows do not exist. Deleting a trip requires confirmation, no booking history, expected aggregate version and exact draft/planned counts (`:599`). Trip deletion does not call `requireActiveTrip`; normally canceled trips necessarily have protected booking history. Profile hides/decorates relevant actions and keeps stale confirmation/error state (`ProfileScreen.tsx:245`–`:287`). The current Saved option card exposes editing/rename/review/compare, without a plan delete action (`AlternativeCard.tsx:6`).

Service mutations are transactional. Version SQL performs conditional aggregate UPDATE by owner and expected version, frequently requiring matching child version (`JdbcTripRepository.java:234`, `:242`, `:257`). Draft component mutation advances both trip and draft (`:259`); option mutation requires matching option version and no booking rows, then advances option (`:242`). Failure exceptions cause rollback. `TripService.java:744` distinguishes aggregate and draft conflicts with current version metadata. Aggregate version is shared across plans, so changes on separate options still contend. Existing deletion checks and booking CAS operate across the same trip version; no database rule for exactly one preferred-primary exists.

### Comparison and price presentation

The bottom section offers Compare selected options only with at least two Saved options (`TripWorkspace.tsx:1642`) and allows at most three chosen IDs (`:1034`). The comparison view filters saved options only (`:1168`); Working is excluded. `ItineraryComparisonView.tsx:24` has accessible mobile tabs with arrow/Home/End navigation and roving focus (`:37`), announcements (`:122`), and a semantic desktop table. It shows saved names/dates and Booked/Expired indicators (`:60`), uses trip-wide traveler count, and has no Primary indicator or in-view selection chooser. Missing components render explanatory text (`:77`); server absent tally formats as Total unavailable (`ItinerarySummaryTally.tsx:15`), but existing numeric sum labels are Grand Total rather than explicitly partial. The tally engine returns zero for absent components and sums selected costs, with no completeness/partial-total field (`ItineraryTallyEngine.java:48`). Rental charges use rounded-up 24-hour cycles. Working tally separately computes client totals from saved selection facts and trip count (`ItinerarySummaryTally.tsx:58`).

## Key Components

- `src/main/java/app/detour/trip/TripController.java:27` — authenticated endpoint contract and request parsing boundary.
- `src/main/java/app/detour/trip/TripService.java:375` — named snapshot lifecycle, ownership, validation, version conflicts, response aliases.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:79` — aggregate loading, selections/snapshots, child and parent conditional writes.
- `src/main/java/app/detour/booking/BookingTransactionExecutor.java:54` — simulated purchase and cancellation side effects.
- `src/main/java/app/detour/booking/JdbcBookingRepository.java:400` — protected history association semantics.
- `frontend/src/components/TripWorkspace.tsx:128` — one Working editor, autosave, saved-option workflows, booking navigation.
- `frontend/src/components/ItineraryComparisonView.tsx:24` — desktop/mobile comparison presentation.
- `frontend/src/components/ProfileScreen.tsx:121` — opening and retaining workspaces, profile mutation navigation.
- `frontend/src/api/tripsApi.ts:410` — trip response shape; `:657` named-option API calls.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Persisted identity/lifecycle | Separate draft and option tables; no preferred-primary; UUID identities and legacy aliases (`TripResponse.java:9`, V19:166). |
| Planning details | Trip party/destination/budget, draft/option dates, current versus frozen selection representations (`Trip.java:7`, `JdbcTripRepository.java:104`, `:177`). |
| Confirmed protection | Any history blocks whole option changes/deletion; booking tally/cancellation depend on trip count and option dates (`TripService.java:398`, `BookingTransactionExecutor.java:317`, `:346`). |
| User transitions | Keyed single Working editor, autosave/errors, explicit copy/replace option dialogs and browser state (`TripWorkspace.tsx:128`, `:980`, `screenHistory.ts:1`). |
| My Trips | Profile summary dates/status derive trip metadata; alternative summaries report Working plus options; booking reference means active/latest booking (`TripService.java:185`, `JdbcTripRepository.java:312`). |
| Documentation and release checks | README and packaged verifier describe/exercise one Working plus saved options (`README.md:25`, scripts verifier:100). |

## Existing Tests and Fixtures

Tests were inspected, not run. Their assertions, rather than retained old lifecycle names, establish the checked-out expectations.

- `TripApiIntegrationTest.java:61`–`:263` covers empty save rejection, option copy/load/update identity, rename isolation, stale/foreign/booked changes, failure rollback and catalog price refresh. `:333` covers independent option dates/snapshots; `:622` covers one-winner same-version saves; `:816` now asserts duplication rejection despite its historical name. Existing component search/selection tests cover draft capacity, prices, eligibility, revalidation and owner/version checks.
- `TripModelForwardMigrationIntegrationTest.java:21` asserts latest Flyway version 20. `:35` migrates V18 multi/empty/absent drafts, descriptive snapshots, nullable historical ages, existing named option and both active/cancelled booking links. `:111` verifies failure preserves drafts when required stay catalog nights are missing. These fixtures are SQL-created synthetic local data.
- `DraftReadinessAndPlannedSnapshotIntegrationTest.java:105` covers snapshot saving despite temporary inventory shortages, descriptive frozen facts, owner isolation and races. Planned snapshot reads after catalog changes are tested.
- `TripApplicationRestartIntegrationTest` and `BookingApplicationRestartIntegrationTest` cover file-backed recovery after restart. Booking schema/API tests cover snapshot persistence and idempotency. `BookingConcurrencyIntegrationTest.java:59`, `:119`, `:181`, `:240`, `:297`, `:375` covers competing inventory, idempotent booking/cancellation and cancellation/rebooking consistency. `BookingCancellationIntegrationTest.java:253`, `:300`, `:444`, `:484` covers option-date cutoffs, cancelled trip immutability, retained cancelled option links and permanent history deletion restrictions.
- `DraftPromotion.test.tsx:44` tests explicit named saves, empty Working, name-only rename, unsaved/failure retention, cancelled load with no request, preservation before load, and update conflict. `ProgressiveTripBuilder.test.tsx:244` covers stale refresh protection, plus slots/search/removal/conflicts/dialog focus. `ItineraryComparisonAndBookingReview.test.tsx:244` covers history navigation; `:319` comparison bounds, `:468` keyboard tabs, `:535` Booked/Expired names, `:559` missing component semantics, `:726` absent tally, booking conflict/confirmation and active-booking restrictions. API-client tests assert method/body/query/CSRF shape.
- `scripts/verify-packaged-release.mjs:100` exercises workingPlan IDs, draft searches, named options, independent option dates, booking/cancellation, owner isolation, restart and savedOptions links. Its expectations depend directly on the old response aliases and endpoints.
- No existing tests establish independent editable plan travelers, preferred-primary invariant/promotion, confirmed component editing boundaries, or sole-plan replacement/deletion transitions; these behaviors do not exist in the current implementation.

## Dependencies and Operational Constraints

Spring Boot/JDBC/Security/Flyway with H2 and Java 21; React 19/TypeScript/Vite with Vitest/jsdom/testing-library (`pom.xml:15`, `frontend/package.json`). Spring Security requires session authentication and CSRF for protected writes, using `XSRF-TOKEN` cookies (`SecurityConfiguration.java:40`). Error contracts use `ApiException` and handler responses, including 409 conflicts and 404 owner isolation.

Default runtime is loopback port 8082 and file H2 `./data/detour` (`application.yml:1`); tests choose isolated H2 databases and controllable clocks. Source builds need npm; Maven normally installs/builds frontend via exec plugin, with `skipFrontend` available (`pom.xml:16`, `:68`). Documented verification is frontend `npm test`, Maven `.\mvnw.cmd clean verify`, then `scripts/verify-packaged-release.ps1` (`README.md:65`). Packaged verification starts only its own temporary local server/database. Research did not start services, touch the development database, or trigger purchases/cancellations.

README says bookings are fictional and no AI, supplier API or payment connection exists (`README.md:3`). The product's supported fixed catalog/date boundaries are real validation constraints even though reservations are simulated. Dates are ISO local dates; component flight/rental times include offsets and destination timezone display; Portland midnight controls departure expiry.

## Historical Context

`git log` attributes V19 to `3d995be` ("pr2 — Model named Trips with one Working plan and independently dated options"). Recent navigation work includes `18d196d` ("fix navigation in browser/back"). Current HEAD follows the related My Trips ticket commits; research found no related ticket file retained beside the supplied ticket, while its relevant component code is present.

The retained architecture record `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:33` describes replacing the obsolete Wayfarer lineage with clean DeTour V1 and no import path. README likewise documents a clean-break development reset (`README.md:76`). That historical boundary differs from this ticket's explicit requirement to preserve existing DeTour Working/options/bookings; current DeTour migrations already include V19/V20 forward data preservation. Historical statements do not override the new ticket.

## Open Questions

These are design matters for planning, not blockers to completing research:

- The source has no single editable persisted plan model or preferred-primary contract, and no rule for deriving profile dates/status after promotion. The supplied ticket owns those lifecycle/data changes; related My Trips consumers currently use trip-level dates and counts.
- The ticket allows date/traveler edits beside confirmed components; source purchase records lack independent party/date facts and several cancellation/display consumers use current planning metadata. Existing behavior cannot establish how those details are represented after the transition.
- Budget and destination are currently trip-level; the ticket explicitly requires per-plan dates/travelers/selections but does not require alternate destinations or specify budget scope. The existing contract and validation remain documented evidence.
- New independent incomplete plans differ from the current nonempty Saved-option save rule. Current primary/sole-plan deletion/promotion and per-plan unsaved transition behavior has no executable precedent.
- V19 establishes missing Working drafts and existing code assumes one; the ticket explicitly includes absent/incomplete Working compatibility. Research did not inspect any live user database, so no distribution of legacy shapes is claimed.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-10-01-manage-independent-trip-plans.md
SUMMARY: Traced the current one-Working-plus-options model across persistence, APIs, UI, comparison, booking and deletion. Documented migration, ownership and concurrency evidence, plus booking metadata dependencies and existing tests. No implementation or live external actions were performed.
DECISIONS:
  - Research treats the supplied ticket's preservation requirements as governing current DeTour compatibility; historical clean-break policy describes the retired application lineage.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run Step 2 create plan and Step 3 testing plan under the confirmed Full 5-Step Pipeline.
