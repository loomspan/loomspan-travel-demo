---
date: 2026-09-19
repository: loomspan-travel-demo
branch: main
commit: bdcdb2407f76718fbe285d8069b917506d06d07c
ticket: ai/thoughts/tickets/2026-09-19-p03-t02-deliver-versioned-draft-alternatives.md
tags: [phase-3, trips, drafts, optimistic-concurrency, autosave, persistence, authorization]
---

# P03-T02 Versioned Draft Alternatives and Autosave Research

## Research Question

What persisted Trip/Draft model, authenticated API conventions, ownership protections, validation rules, error contracts, and executable test patterns exist for adding component-empty Draft alternatives and versioned autosave without introducing later Planned or component-selection behavior?

## Summary

P03-T01 provides an owner-scoped, normalized Trip aggregate with one initial component-empty Draft and separate non-negative `version` columns for Trip and Draft. It exposes only authenticated creation and owner-scoped detail retrieval; neither mutable-update, alternative-create/duplicate/delete, nor conflict behavior currently exists. The existing Draft table has a unique `trip_id` constraint, so the checked-out schema expressly permits only one Draft per Trip.

The service already centralizes supported-travel, age, budget, and derived-label validation, while the repository maps the aggregate through owner-qualified SQL. `ApiException`/`ApiExceptionHandler` establish JSON `{code,message,fields}` errors for validation, not-found, malformed JSON, and unexpected persistence failures; the type can carry arbitrary HTTP status/code but has no current-version conflict payload. There is no frontend Trip client or UI yet, so P03-T06 remains the stated UI owner.

## Repository State

- Observed 2026-09-19T16:00:10-07:00 at `C:/code/loomspan-travel-demo`.
- Branch `main`, commit `bdcdb2407f76718fbe285d8069b917506d06d07c` (`cleanup after P03-T01`).
- `git status --short --branch` reported `## main...origin/main`; no staged, unstaged, or untracked files were present before this research artifact.
- No prior P03-T02 research, plan, testing, or review artifact existed in the checkout. Commit `d2f236c` introduced P03-T01; its removed historical research/plan records identify this ticket as the next owner of extra Drafts, mutable shared-detail updates, and conflict handling.

## Current Behavior and Data Flow

1. Spring Security requires authentication for `/api/trips/**`; unsafe requests use the cookie/header CSRF mechanism. `TripController` obtains the internal owner ID only from `DetourUserPrincipal` and exposes `POST /api/trips` plus `GET /api/trips/{tripId}` (`src/main/java/app/detour/security/SecurityConfiguration.java:35-62`, `src/main/java/app/detour/trip/TripController.java:16-38`).
2. Creation parses an allow-listed JSON envelope, validates the required destination/dates/traveler count and optional ages/budget, creates UUID Trip and Draft public IDs, derives the label, and runs the aggregate write under `@Transactional` (`src/main/java/app/detour/trip/TripRequests.java:16-63`, `src/main/java/app/detour/trip/TripService.java:29-46`). Omitted ages become ordered null traveler rows; a supplied budget remains nullable while zero is accepted (`TripService.java:59-79`).
3. `JdbcTripRepository.createAggregate` inserts a Trip at version `0`, one traveler row per ordinal, and one Draft at version `0` (`src/main/java/app/detour/trip/JdbcTripRepository.java:33-63`). The current service then reloads and returns the owner-scoped aggregate.
4. Detail parses the public UUID and queries by both `trip.public_id` and `trip.owner_user_id`; malformed, unknown, and foreign identifiers resolve to the same `404 RESOURCE_NOT_FOUND` response (`src/main/java/app/detour/trip/TripService.java:49-56`, `src/main/java/app/detour/trip/JdbcTripRepository.java:66-87`). The response includes the Trip version and a list of Draft public IDs/versions, but Drafts contain no mutable fields/components today (`src/main/java/app/detour/trip/TripResponse.java:7-10`, `src/main/java/app/detour/trip/DraftResponse.java:5`).
5. The controller has no `PUT`, `PATCH`, or `DELETE` mapping, the repository has no update/delete or conditional-write operation, and no source currently checks an expected version. Therefore concurrent writes cannot occur through the present Trip API and no current path produces a version conflict.

## Key Components

- `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql:1-21` — `detour_trip` has owner/destination references, normalized shared fields, derived label, nullable budget, and a non-negative version defaulting to zero.
- `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql:23-47` — traveler rows retain ordinal ages; `detour_trip_draft` has public ID/version but `uq_detour_trip_draft_trip_id` permits one Draft only and its foreign key has no declared cascade behavior.
- `src/main/java/app/detour/trip/TripService.java:17-20,59-92` — present shared-detail limits: March 1--31 2027, 1--14 nights, 1--8 travelers, ages 0--120, and nullable integer-cent budget 0--100,000,000; the label is derived from catalog name and dates.
- `src/main/java/app/detour/trip/TripRepository.java:8-15` and `src/main/java/app/detour/trip/JdbcTripRepository.java:66-87` — repository interface and implementation currently provide only destination lookup, transactional aggregate creation, and owner-qualified aggregate lookup.
- `src/main/java/app/detour/trip/TripRequests.java:20-63` — strict field allow-list and JSON parsing style for the current create request. It differentiates absent/null, string, date, integer, and array inputs.
- `src/main/java/app/detour/api/ApiException.java:5-31`, `src/main/java/app/detour/api/ApiError.java:5-8`, and `src/main/java/app/detour/api/ApiExceptionHandler.java:10-34` — generic stable API-error transport. Expected validation maps to `400 VALIDATION_FAILED`, malformed JSON to `400 MALFORMED_REQUEST`, absent resources to `404 RESOURCE_NOT_FOUND`, and unhandled persistence failures to `500 INTERNAL_ERROR`.
- `src/main/java/app/detour/security/SecurityConfiguration.java:39-52` — all non-public routes are authenticated and retain CSRF enforcement; authorization of a specific Trip/Draft is still a service/repository responsibility.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Durable alternative cardinality | The unique `detour_trip_draft.trip_id` constraint enforces exactly one Draft per Trip (`V12__create_owned_trip_and_initial_draft_schema.sql:36-44`). The parent query already reads Drafts as a list ordered by internal ID (`JdbcTripRepository.java:79-80`), so response mapping is collection-shaped even though the schema cardinality is one. |
| Version boundary | Both shared Trip and individual Draft records start at version `0` and are returned separately (`V12__create_owned_trip_and_initial_draft_schema.sql:11,40`; `TripService.java:95-98`). No conditional SQL predicate, database optimistic-lock column mapping, or API expected-version field exists. |
| Shared details | Destination, dates, traveler count/ages, and budget reside on the Trip plus traveler child rows; the label is derived server-side (`Trip.java:7-9`, `TripService.java:82-98`). Current validation has no Planned-state check because the schema and source contain no Planned representation. |
| Draft content | A Draft currently has only internal/public IDs, parent ID, and version. No selection/component, status/lifecycle, price, or snapshot storage exists (`TripDraft.java:5-6`, `V12__create_owned_trip_and_initial_draft_schema.sql:36-45`). |
| Ownership/non-disclosure | Detail lookup combines public Trip ID and owner user ID in the database query and tests compare foreign and unknown responses (`JdbcTripRepository.java:66-87`; `TripApiIntegrationTest.java:132-151`). There is no independent Draft lookup endpoint yet. |
| Transaction and persistence failure | Trip creation is service-transactional, and the existing controlled H2 trigger test confirms a Draft-insert failure rolls back parent/traveler/Draft rows (`TripService.java:29-46`; `TripApiIntegrationTest.java:154-169`). The generic exception handler intentionally hides persistence exception details. |
| HTTP/frontend | The only Trip endpoints are create/detail (`TripController.java:25-34`). `rg` finds no Trip/Draft API module, component, or frontend tests under `frontend/src`; the current frontend’s existing tests target identity/app surfaces. |
| Migration baseline | Flyway currently applies `V1` through `V12`, and clean/forward-migration tests assert the new Trip tables are empty and identity/catalog data remain intact (`DetourApplicationTest.java:35-90`; `PhaseOneCatalogForwardMigrationIntegrationTest.java:19-60`). New persistence evolution must follow the numbered forward migration lineage. |

## Existing Tests and Fixtures

- `src/test/java/app/detour/trip/TripApiIntegrationTest.java:42-72` proves authenticated creation/detail of the one-Draft aggregate, UUID-like public IDs, version zero, derived label, absent component fields, and zero budget.
- `TripApiIntegrationTest.java:74-113` covers strict create validation/malformed JSON and asserts rejected requests persist no aggregate rows; `:115-130` proves absent ages/budget remain distinct from supplied zero/max values.
- `TripApiIntegrationTest.java:132-151` supplies two session/CSRF-backed users and proves foreign versus unknown Trip reads have the same not-found response; `:154-190` covers controlled persistence rollback and concurrent invalid creation/no-orphan checks.
- `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java:21-63` uses a real loopback server and temporary file-backed H2 to confirm a created Trip/Draft survives restart, while the original session does not.
- The current MockMvc helper creates a CSRF cookie through `GET /`, retains each user’s `MockHttpSession`, and attaches `X-XSRF-TOKEN` to unsafe requests (`TripApiIntegrationTest.java:205-230`). This is the established HTTP integration pattern for new authenticated mutations.
- There are no tests for multiple Drafts, explicit duplication, Draft deletion, mutable Trip/Draft autosave, stale expected versions, successful-versus-conflicted racing mutations, retry idempotence after an infrastructure failure, or conflict error fields/current version.

## Dependencies and Operational Constraints

- The platform uses Java 21, Spring Boot, Spring Security, `JdbcTemplate`, H2, and Flyway; the default database is file-backed H2 with a `LOCK_TIMEOUT=10000` configuration (`pom.xml:20-74`, `src/main/resources/application.yml:1-22`). Existing tests use isolated in-memory or temporary file H2 and loopback HTTP; no external supplier or model access is necessary.
- The roadmap requires all owner checks in backend code, deterministic server validation, USD integer cents, fixed PDX, and Trip/Draft autosave states that do not claim success early (`ai/thoughts/phases/README.md:42-66,103-111,153-162`).
- Phase 3 permits component-empty Draft alternatives and pre-Planned shared-detail edits, but reserves component revalidation/pricing/removal summaries for P03-T04 and Planned lifecycle/snapshots for P03-T03 (`ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md:7-39`; ticket `P03-T02` requirements).
- The development clean-break policy requires forward-only DeTour schema changes without compatibility routes, aliases, dual schemas, legacy migrations, or live database deletion (`ai/thoughts/phases/README.md:11-17`).

## Historical Context

- Commit `d2f236c` (`P03-T01 — Establish Owned Trips and Initial Drafts`) added V12, the `app.detour.trip` package, and Trip API/restart tests; its implementation record deliberately deferred mutable alternatives and versioned autosave to P03-T02. The following commit `bdcdb24` removed the completed ticket’s transient pipeline artifacts but retained the code and ticket.
- P03-T01’s historical plan selected normalized traveler rows, owner-and-public-ID combined lookup, UUID public identifiers, strict budget parsing, and separate Trip/Draft version fields. These implementation facts are present in the checkout; its former planning rationale does not establish P03-T02’s future API shape.
- P03-T03 later adds Planned snapshots and duplication of them into Drafts; P03-T04 later owns populated-Draft compatibility, repricing, and removal summaries. The current ticket explicitly limits duplicate/create/delete and mutable shared updates to component-empty Drafts.

## Open Questions

- The ticket requires a deterministic conflict response containing current-version information, but the present `ApiError.fields` is `Map<String,String>` and no source selects the exact HTTP status/code/body shape or whether to return only the affected resource version versus a refreshed aggregate. Planning must establish the concrete stable contract from the ticket’s stated client reload requirement.
- The current separate Trip and Draft versions do not yet show which mutation requires which expected version, nor whether a create/duplicate/delete operation also changes the parent Trip version. This is a concurrency-boundary design question called out by the ticket’s reassessment trigger.
- No existing data model distinguishes Draft from later Planned/Booked alternatives. For this component-empty ticket, the current `detour_trip_draft` table is the only alternative relation; planning must keep any storage/API decisions within that boundary rather than infer later lifecycle states.
