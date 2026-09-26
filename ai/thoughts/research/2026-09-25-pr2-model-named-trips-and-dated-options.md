---
date: 2026-09-26
repository: loomspan-travel-demo
branch: main
commit: 7c473e08fbea1ec2841376cca6da09ccfc5dc325
ticket: ai/thoughts/tickets/2026-09-25-pr2-model-named-trips-and-dated-options.md
tags: [trips, persistence, migration, bookings, concurrency]
---

# Named Trips and Dated Options Research

## Research Question

How are Trips, mutable Drafts, Planned alternatives, and Bookings stored and exposed today, and which existing data and contracts intersect the PR2 requirements?

## Summary

The current Trip row owns destination, one date pair, traveler count, optional ages and budget, and a destination/date-derived `display_label`. It can have multiple Drafts and multiple Planned itineraries. Drafts retain mutable catalog references; Planned and Booking component snapshots retain copied descriptive and price facts. The backend has owner-scoped version checks, but no persisted Trip or option names, no per-Draft or per-Planned dates, and no mutable Planned option update contract. The PR1 frontend has a Trip name input, but its create payload omits the name.

## Repository State

- Observed 2026-09-26 10:41 PDT, branch `main`, commit `7c473e08fbea1ec2841376cca6da09ccfc5dc325`.
- Working tree contains completed PR1 modifications in its ticket, frontend App/auth/profile/workspace and tests, Trip API integration test, and new PR1 research/plan/review artifacts plus `HomeScreen.tsx`, `TripStartForm.tsx`, and `PublicTripFlow.test.tsx`. Those changes are the baseline for this research; none was edited here.

## Current Behavior and Data Flow

1. `TripController` exposes authenticated `/api/trips` create/list/detail/update, Draft/alternative mutations, component selection, and booking routes; each delegates the principal's user ID (`src/main/java/app/detour/trip/TripController.java:36-95`, `:240-309`).
2. Creation validates supported destination, March 2027 dates of 1–14 nights, 1–8 travelers, provided ages, and optional budget; it generates Trip and initial Draft UUIDs transactionally (`src/main/java/app/detour/trip/TripService.java:79-105`, `:525-555`). `validateAges` accepts `null`, so creation can persist unknown ages; when supplied, there must be one integer age 0–120 per traveler (`:525-535`).
3. `JdbcTripRepository.createAggregateWithDrafts` inserts the Trip, ordinal traveler rows, and every requested Draft; normal create supplies one empty Draft (`src/main/java/app/detour/trip/JdbcTripRepository.java:27-51`). Owner-filtered reads assemble all Drafts and Planned rows ordered by row ID (`:53-88`). Draft stay pricing is reconstructed from live nightly inventory for the Trip date pair (`:90-161`). Planned selections load copied snapshot fields (`:163-219`).
4. Trip updates currently replace destination, dates, travelers, budget, and derived label together. Once a Planned row exists, destination/date/traveler edits return `IMMUTABLE_TRIP`; before that, every Draft's selections are revalidated and removals/adjustments are returned (`src/main/java/app/detour/trip/TripService.java:186-259`). The date edit therefore changes the shared Trip date pair, not an individual Draft.
5. Explicit create/duplicate Draft routes can add more Draft rows, including empty ones; promotion validates readiness, resolves selected component facts, and inserts a new immutable Planned snapshot (`src/main/java/app/detour/trip/TripService.java:262-329`). Planned alternatives can be duplicated into a Draft; deleting an actively booked Planned row is blocked, while V18 permits a deleted Planned row to leave the Booking reference null (`:331-369`; `src/main/resources/db/migration/V18__add_trip_status_and_cancellation_constraints.sql:7-10`).
6. API detail projects `drafts`, `planned`, and combined `alternatives`, all under Trip-level dates/label; profile list derives past/upcoming and every alternative's expiry from that one Trip date pair (`src/main/java/app/detour/trip/TripService.java:124-181`, `:595-636`; `src/main/java/app/detour/trip/TripResponse.java:8-16`).
7. Booking resolves a Planned public ID under the owned Trip, checks Trip version and expiry using Trip start date, locks and checks inventory, inserts a Booking referencing the Planned row, copies Planned component snapshots, and advances Trip version in one transaction (`src/main/java/app/detour/booking/BookingTransactionExecutor.java:55-83`, `:86-181`). Cancellation deadline also uses Trip start date (`:212-223`, `:262-273`). Booking responses load frozen Booking selections (`src/main/java/app/detour/booking/BookingService.java:111-139`).
8. PR1's client-held trip-start form collects and validates a name, but `tripsApi.createTrip` receives destination, dates, count, and ages only; its age validation permits all blank fields (`frontend/src/components/TripStartForm.tsx:31-59`). The existing TypeScript create request and response contain no name (`frontend/src/api/tripsApi.ts:393-429`, `:513-522`).

## Key Components

- `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql:1-47` — Trip dates, label, traveler rows, and original unique Draft-per-Trip constraint.
- `src/main/resources/db/migration/V13__allow_multiple_component_empty_drafts.sql:1-3` — removes Draft-per-Trip uniqueness.
- `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql:1-39` — Draft catalog-reference tables and Planned identity; following tables snapshot prices/descriptions.
- `src/main/resources/db/migration/V16__enhance_planned_snapshot_schema.sql:1-44` — complete descriptive fields on Planned component snapshots.
- `src/main/resources/db/migration/V17__create_booking_schema.sql:4-29` — Booking Trip/Planned foreign keys, unique active Booking per Trip and Trip/idempotency key; following tables freeze Booking components.
- `src/main/resources/db/migration/V18__add_trip_status_and_cancellation_constraints.sql:1-10` — Trip status and nullable Planned reference with `ON DELETE SET NULL` for Booking history.
- `src/main/java/app/detour/trip/Trip.java:7-14` — current domain aggregate with dates/label and lists of Draft/Planned records.
- `src/main/java/app/detour/trip/TripRequests.java:18-55` — strict create/update/mutation JSON shapes, expected Trip and Draft versions, Booking Planned ID.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:221-263` — conditional owner/version updates on Trip and Draft; transaction callers distinguish stale writes.
- `src/main/java/app/detour/trip/TripService.java:507-523` — conflict response construction; `:683-789` ties readiness and structural selection checks to Trip dates.
- `src/main/java/app/detour/trip/TripRepository.java:10-86` — persistence contract consumed by Trip and Booking services.
- `src/main/java/app/detour/booking/JdbcBookingRepository.java:130-243` — Booking insert and Planned-to-Booking snapshot copy.
- `frontend/src/api/tripsApi.ts:393-475` — current client response/request contracts; `:642-727` maps all Trip/Draft/Planned/Booking endpoints.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Naming | `display_label` is required and derived from destination/dates at create/update; no independent rename field or endpoint (`V12:10`; `TripService.java:92-95`, `:214`, `:556-559`). |
| Shared traveler group | Trip count and ordinal traveler ages are stored separately; DB permits null age, while service accepts `null` age list (`V12:23-32`; `TripService.java:525-535`; `JdbcTripRepository.java:71-88`). |
| Working record cardinality | V13 removed uniqueness, and service explicitly creates/duplicates Draft rows (`V13:1-3`; `TripService.java:262-283`). |
| Option facts | Planned rows have UUID and Trip FK but no name/date/version; component snapshots have copied prices and descriptions (`V14:33-39`; `V14:41-115`; `V16:1-44`). |
| Stale and owner checks | Trip owner/public-ID lookup and conditional Trip/Draft version increments reject stale mutations (`JdbcTripRepository.java:53-69`, `:221-242`; `TripService.java:507-523`). Planned alternatives have no mutable version (`AlternativeResponse.java:9-16`). |
| Booking identity | `detour_booking.planned_itinerary_id` references Planned row and can become null on deletion; Booking also keeps its own public ID, reference, status, total, and component snapshots (`V17:4-29`; `V18:7-10`; `JdbcBookingRepository.java:130-243`). |
| Time semantics | Profile expiry, readiness, promotion, booking, and cancellation all use Trip-level dates (`TripService.java:108-115`, `:130-165`, `:303-305`, `:688-695`; `BookingTransactionExecutor.java:55-59`, `:212-215`). |
| Frontend | PR1 trip-start form holds a name only in client state; existing workspace/list render `trip.label` and Draft/Planned projections (`TripStartForm.tsx:6-14`, `:54-58`; `TripWorkspace.tsx:1322`, `:1709`; `TripListSection.tsx:49-89`). |

## Existing Tests and Fixtures

- `TripApiIntegrationTest.java:81-112`, `:113-170`, `:232-337` cover aggregate create/validation, optional ages, multiple empty Drafts, and version conflicts; `:338-473`, `:670-831` cover Planned snapshots and Trip-wide edit/revalidation rules; `:1080-1213` cover list date partition/expiry. The PR1-auth test at `:58-80` checks protected routes. These express existing behavior, including behavior PR2 supersedes.
- `DraftReadinessAndPlannedSnapshotIntegrationTest.java:281-410`, `:439-513` exercises copied Planned facts, immutability, races, and owner isolation.
- `BookingApiIntegrationTest.java:58-568` covers booking, idempotency, inventory conflicts, owner isolation, and history. `BookingConcurrencyIntegrationTest.java:59-440` covers inventory and cancellation races. `BookingSchemaIntegrationTest.java:51-162` checks active-booking uniqueness and Planned deletion behavior.
- `TripApplicationRestartIntegrationTest.java:24-211` and `BookingApplicationRestartIntegrationTest.java:26` exercise persistent H2 restart paths. `PhaseOneCatalogForwardMigrationIntegrationTest.java:19-32` is the adjacent Flyway target-version migration pattern, but there is no located V18-to-new-model migration fixture yet.
- Integration tests use isolated H2 databases and test clocks (`TripApiIntegrationTest.java:40-55`; `BookingApiIntegrationTest.java:40-56`). This research ran source inspection only; no tests or live services.

## Dependencies and Operational Constraints

- Spring JDBC, Flyway, and H2 are configured in `pom.xml:29-42`; default runtime uses a persistent H2 file (`src/main/resources/application.yml:15-18`; `README.md:38`). Existing V1–V18 migrations are applied history and PR2 calls for a forward-only addition.
- Current date constraints are DB and service enforced for March 2027, 1–14 nights (`V12:15`; `TripService.java:548-555`). The repository supports three destination keys (`JdbcTripRepository.java:18-25`).
- Booking makes real local database inventory updates and persists reservation/confirmation records; research did not invoke those paths (`BookingTransactionExecutor.java:86-181`).

## Historical Context

- The earlier architecture defines mutable Draft, stable Planned snapshot, and independent immutable Booking snapshot (`ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:76-80`). PR2 explicitly changes the user-facing Working/Saved model while retaining booked records (`ai/thoughts/tickets/2026-09-25-pr2-model-named-trips-and-dated-options.md:5-15`).
- `ai/thoughts/phases/CONTINUATION.md:51-55` records prior decisions allowing ages to be completed later, date changes at Trip level before Planned, and Trip duplication after Planned; those are historical inputs, not PR2 requirements.

## Open Questions

- Existing databases can contain multiple populated Drafts and nullable traveler ages. The ticket requires preserving populated alternatives and a single Working plan, while also requiring complete ages for *new* Trips; the exact upgraded response for legacy unknown ages is not specified.
- Historical Planned snapshots lack their own dates. Their effective dates currently derive from the owning Trip, while Booking retains component snapshots and a Planned reference that may be null after deletion.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-09-25-pr2-model-named-trips-and-dated-options.md
SUMMARY: Mapped current Trip/Draft/Planned/Booking persistence, API, frontend, migration, and test coverage against PR2. Existing PR1 changes were preserved.
DECISIONS:
  - Treated PR1 uncommitted files as the current baseline because the orchestrator identified PR1 as completed.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run steps 2 and 3 to plan the PR2 change and its verification.
