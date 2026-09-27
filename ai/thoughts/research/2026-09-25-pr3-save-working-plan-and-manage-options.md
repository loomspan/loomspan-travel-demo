---
date: 2026-09-26
repository: loomspan-travel-demo
branch: main
commit: 3d995be35ca427799f7d5bc6538a6050e0e3a542
ticket: ai/thoughts/tickets/2026-09-25-pr3-save-working-plan-and-manage-options.md
tags: [trip, working-plan, saved-option, booking, concurrency]
---

# Working Plan and Saved Options Research

## Research Question

How does the post-PR2 code currently save the single Working plan, create and manage Saved options, validate date changes, and protect booking and concurrent edits?

## Summary

PR2 persists one dated Draft row per Trip and dated, named Planned snapshot rows, exposed additionally as `workingPlan` and `savedOptions`. Working component selection and date changes mutate that row with parent/Draft versions, but the current frontend still calls the older Draft and Planned workflow. Promotion appends a generic named option and applies full booking-like readiness, including a required budget; there is no service/controller route to load an option into Working or update an existing option's component snapshots. Booking already uses the selected option's dates and protects inventory and idempotency.

## Repository State

- Observed 2026-09-26 15:46 America/Los_Angeles. Branch `main`, commit `3d995be35ca427799f7d5bc6538a6050e0e3a542` (`pr2 — Model named Trips with one Working plan and independently dated options`); `git status --short` was empty.
- No production or test files were edited for this research. The PR2 commit is the checked-out baseline.

## Current Behavior and Data Flow

1. `POST /api/trips` creates an owned Trip and one empty Working Draft in a transaction after validating name, destination, dates, count, complete ages, and optional budget (`TripController.java:37-41`, `TripService.java:78-96`). V19 makes `detour_trip_draft.trip_id` unique, and supplies Working/option date and option name/version columns (`V19__model_named_trips_and_dated_options.sql:2-19`, `:163-172`).
2. Reads load the one Draft's dates and mutable catalog selections, plus Planned rows with copied snapshots, names, dates, and versions (`JdbcTripRepository.java:71-93`, `:96-225`). Responses include both legacy `drafts`/`planned`/`alternatives` and `workingPlan`/`savedOptions` fields (`TripService.java:645-689`, `TripResponse.java:8-28`).
3. The frontend edits `trip.drafts[0]`, compares local shared fields to the last response, and debounces a `PUT /api/trips/{id}` after a dirty change. It serializes in-flight saves, sends `expectedVersion`, reports saving/saved/error/conflict, and preserves retry/reload controls (`TripWorkspace.tsx:126`, `:381-556`, `:1328-1356`). Component choices use separate immediate `PUT`/`DELETE` calls with both parent and Draft versions (`TripWorkspace.tsx:627-755`, `TripController.java:126-249`). Search routes are GETs (`TripController.java:107-125`, `:154-172`, `:201-220`).
4. `PUT /working-dates` validates the new dates, revalidates each selected component, removes invalid selections, adjusts stay room count, updates the same Draft and Trip compatibility dates, and returns `revisionSummary` with removals/adjustments (`TripService.java:109-145`, `JdbcTripRepository.java:232-234`). The older shared-details route also revalidates Draft selections, but rejects destination, date, and traveler edits once any Planned option exists (`TripService.java:237-310`). The frontend currently autosaves through that older route, not `/working-dates` (`TripWorkspace.tsx:433-520`).
5. `POST /drafts/{draftId}/plan` checks expiry against the Trip date, readiness, budget overage acknowledgment, resolves current catalog facts, then inserts a new Planned snapshot; it does not replace Working (`TripService.java:343-374`, `JdbcTripRepository.java:327-421`). The inserted option is automatically named `Option N` using current Planned count (`JdbcTripRepository.java:327-333`). Readiness requires a budget as well as one structurally valid selected component, adult eligibility, available inventory, and matching dates (`TripService.java:721-842`).
6. The repository has `advanceVersionForOption`, `renameOption`, and `updateOptionDates` primitives. The guard checks owner, parent version, option version, and absence of **any** Booking row for the option (`JdbcTripRepository.java:236-250`). No controller/service route uses these primitives yet. Existing duplicate Draft/option and empty-Draft creation routes return `WORKING_PLAN_EXISTS`; deletion of the only Draft returns `WORKING_PLAN_REQUIRED` (`TripService.java:313-341`, `:376-407`). The frontend still displays controls wired to these routes (`TripWorkspace.tsx:579-625`, `:1715-1796`).
7. Booking resolves the selected Planned row, checks its departure date, parent version and active booking, then locks/checks component inventory inside a transaction (`BookingTransactionExecutor.java:53-103`). Cancellation resolves the booked option's date (with Trip-date fallback), restores inventory, retains canceled Booking records, and advances Trip version (`BookingTransactionExecutor.java:204-253`, `:253-345`). The Booking service handles idempotent replay (`BookingService.java:55-81`).

## Key Components

- `src/main/java/app/detour/trip/TripController.java:37` — authenticated Trip API entry; existing Working-date and Draft/Planned routes.
- `src/main/java/app/detour/trip/TripRequests.java:15` — strict request shapes and version fields; current Promotion has no user option name.
- `src/main/java/app/detour/trip/TripService.java:109` — Working-date revalidation and change summary.
- `src/main/java/app/detour/trip/TripService.java:343` — current append-only promotion and readiness boundary.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:236` — version-guarded option persistence primitive and booking-history guard.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:327` — named/dated Planned insert and copied component facts.
- `src/main/java/app/detour/trip/ItineraryTallyEngine.java:43` — absent budget produces null remaining/overage and `isOverBudget=false`, distinct from zero budget.
- `src/main/java/app/detour/booking/BookingTransactionExecutor.java:53` — selected-option expiry, atomic inventory booking and cancellation.
- `frontend/src/components/TripWorkspace.tsx:433` — debounced shared-detail autosave; `:899` promotion UI handler.
- `frontend/src/api/tripsApi.ts:406` — response shape includes new Working/option fields while client actions still use legacy Draft/Planned routes (`:670-724`).

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Single Working row | V19 unique constraint and service rejection of extra Draft operations (`V19:166`; `TripService.java:313-341`). |
| Date isolation | Working and Saved rows have own dates; frontend autosave and readiness/promotion still use Trip dates or reject edits when options exist (`TripService.java:109-145`, `:251-261`, `:349`, `:737-750`). |
| Option identity and mutability | Planned rows have name/date/version and copied facts; no active option load/update API, while repository has guarded metadata primitives (`JdbcTripRepository.java:236-250`, `:327-421`). |
| Save readiness | Current promotion requires budget and booking-like eligibility/availability beyond structural component validity (`TripService.java:737-842`). |
| Conflict handling | Parent/Draft conditional versions protect Working component writes and return 409 on stale input (`JdbcTripRepository.java:252-267`; `TripService.java:865-931`). Frontend distinguishes conflict and failed saves (`TripWorkspace.tsx:433-520`, `:1328-1356`). |
| Booking history | `isPlannedItineraryBooked` prevents deletion of any booked-history option; Booking selection snapshots and canceled records remain separate (`TripService.java:387-406`, `BookingTransactionExecutor.java:204-253`). |

## Existing Tests and Fixtures

- `TripApiIntegrationTest.java:61-166` covers PR2 creation, Working dates, option name/date isolation; `:345-441` covers Draft cardinality and stale writes; `:442-576` covers promotion/readiness and immutable snapshots. Several older tests still encode promotion and duplicate-Draft behavior (`:577-723`).
- `DraftReadinessAndPlannedSnapshotIntegrationTest.java:57-281` covers readiness, inventory failures and budget acknowledgment; `:282-476` covers snapshot stability and promotion races.
- `BookingApiIntegrationTest.java:59-237` covers booking, idempotency and option-date use; `BookingCancellationIntegrationTest.java:252-266` covers cancellation using booked-option dates, and `:443-482` checks canceled booking references.
- `TripModelForwardMigrationIntegrationTest.java:20-126` covers fresh schema and V18 upgrade of multiple Drafts and booked references.
- `ProgressiveTripBuilder.test.tsx`, `ItineraryComparisonAndBookingReview.test.tsx`, `PublicTripFlow.test.tsx`, and `frontend/src/api/tripsApi.test.ts` are adjacent UI/client patterns. No current test was located for loading one Saved option into Working or replacing that option's snapshots; those routes do not yet exist.

## Dependencies and Operational Constraints

- Spring Boot/JDBC/H2/Flyway backend and React/Vitest frontend (`pom.xml:29-68`, `frontend/package.json:1`). Integration tests use isolated in-memory H2 URLs (`TripApiIntegrationTest.java:43-48`); default runtime points to persistent `./data/detour` (`src/main/resources/application.yml:13-19`).
- The supported date window is March 1–31, 2027, with 1–14 nights (`TripService.java:42-43`, `:598-604`; V19 date constraints `:165`, `:171`). Booking deadlines use Pacific time (`BookingTransactionExecutor.java:60`).
- Trip, Draft, Planned, snapshot, and Booking IDs are persisted; the option Booking FK and snapshots are existing operational records (`V19__model_named_trips_and_dated_options.sql:12-19`, `BookingTransactionExecutor.java:53-103`).

## Historical Context

- The PR2 plan chose the existing Draft and Planned tables for Working and Saved data, with compatibility Trip dates synchronized to Working dates (`ai/thoughts/plans/2026-09-25-pr2-model-named-trips-and-dated-options.md:38-43`). The checked-out source confirms that shape.
- The older PR2 research describes the pre-migration multiple-Draft model (`ai/thoughts/research/2026-09-25-pr2-model-named-trips-and-dated-options.md:16-18`); it predates the current commit and is not evidence of current behavior.

## Open Questions

- Current `TripService` has both Working-date and shared-details mutations. The client uses shared-details autosave, whose rule differs once Saved options exist. Their eventual API division is not represented in current client code.
- Current readiness and promotion enforce full eligibility and budget, while the PR3 ticket distinguishes structural option-saving validity from booking-time checks. There is no separate current check for that narrower boundary.
- Current option metadata write primitives exist without component replacement or a public route; no current behavior shows how a selected option is associated with a subsequent Working edit.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-09-25-pr3-save-working-plan-and-manage-options.md
SUMMARY: Mapped Working autosave, dated option persistence, legacy promotion, booking boundaries, and tests at the PR2 baseline. Identified the current API and UI gaps without changing production code.
DECISIONS:
  - none
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run pipeline steps 2 and 3 for PR3 planning and test planning.
