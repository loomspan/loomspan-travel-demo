---
date: 2026-09-17
repository: loomspan-travel-demo
branch: main
commit: dba72bd727e2957f0cb38fc47a2fc2db28a3981e
ticket: ai/thoughts/tickets/2026-09-17-p00-t02-define-replacement-architecture.md
tags: [detour, architecture, replacement, phase-0]
---

# P00-T02 Define Replacement Architecture Research

## Research Question

What current Wayfarer capabilities, technical boundaries, platform choices, and baseline-test dispositions must the P00-T02 architecture record account for before later DeTour implementation tickets begin?

## Summary

Wayfarer is a single Spring Boot application that serves a built React frontend, persists a fixed Boston--New York scenario in H2/Flyway, and uses Loomspan for asynchronous planning and natural-language trip changes. Java currently owns catalog evaluation, request validation, persistence, and transactional inventory mutation, but model output still selects and explains proposed trips.

The authoritative DeTour roadmap establishes `app.detour`, `detour` technical identifiers, deterministic application-owned planning/validation/ranking/explanation, authenticated per-user data, new H2/Flyway history, and a distinct Trip--Itinerary--Booking lifecycle. The P00-T01 baseline found no concrete incompatibility with Java 21, Spring Boot, React, H2, Flyway, or the single packaged-application shape; its Maven failures were environmental (Java unavailable on `PATH` before Maven started), while `npm.cmd run build --prefix frontend` passed.

## Repository State

- Recorded at `2026-09-17T14:17:26.7935496-07:00` on branch `main`, commit `dba72bd727e2957f0cb38fc47a2fc2db28a3981e`.
- `git status --short` was empty before this research artifact was written. The baseline record attributes its own preceding workflow artifacts to P00-T01 and records no tracked user modifications at its capture time.
- Recent history is `dba72bd` (P00-T01 cleanup), `fd3bfcb` (P00-T01 baseline), then `5fb7d2b` (roadmap/Phase 0). No earlier DeTour architecture-decision record was found.

## Current Behavior and Data Flow

The React client calls `/api` endpoints and stores a selected Wayfarer trip ID in browser local storage (`frontend/src/main.tsx:10`, `frontend/src/main.tsx:36-40`). `ApiController` exposes unauthenticated create/list/revise/assessment/booking routes plus model-backed intake, exchange, and simulated return-cancellation routes (`src/main/java/demo/wayfarer/ApiController.java:10-27`). There is no user identity in these routes or in the current schema.

For assessment, `AssessmentService` snapshots a trip and catalog through `TripStore`, queues work in an in-process executor, invokes the Loomspan `planTrip` skill, stores selected execution events, then accepts or fails the assessment (`src/main/java/demo/wayfarer/AssessmentService.java:17-48`). `TravelSkills` exposes Java inventory searches and evaluation to that model orchestration and records immutable per-assessment catalog receipts (`src/main/java/demo/wayfarer/TravelSkills.java:10-47`). `TripCalculator` validates requests and computes candidate totals, while `TripStore.complete` recalculates and validates the model result against the captured snapshot (`src/main/java/demo/wayfarer/TripStore.java:266-275`).

`TripStore` uses JDBC/Flyway tables directly. It locks the trip and catalog rows before starting an assessment or accepting a booking (`src/main/java/demo/wayfarer/TripStore.java:46-56`, `src/main/java/demo/wayfarer/TripStore.java:193-216`, `src/main/java/demo/wayfarer/TripStore.java:307-373`). The current booking path revalidates the quote against current inventory, adjusts both travel legs and hotel-night stock, and persists one booking per trip in one transaction (`src/main/java/demo/wayfarer/TripStore.java:347-385`; `src/main/resources/db/migration/V1__business_schema.sql:10`). It also implements an exchange path and a supplier-style return-service disruption/recovery path, rather than a traveler cancellation lifecycle (`src/main/java/demo/wayfarer/TripStore.java:126-141`, `src/main/java/demo/wayfarer/TripStore.java:302-373`).

Current configuration names the Spring application `wayfarer`, defaults to the `data/wayfarer` H2 path, and configures an OpenAI-compatible Loomspan connection, model, skills, execution trace persistence, and optional Loomspan observability (`src/main/resources/application.yml:1-29`). Maven currently builds the frontend and copies `frontend/dist` into the Boot application; the project artifact is `wayfarer` and the frontend package is `wayfarer-frontend` (`pom.xml:11-17`, `pom.xml:67-113`, `frontend/package.json:1`).

## Key Components

- `pom.xml:11-17` — current `demo.loomspan:wayfarer` artifact, Java 21, Loomspan and Spring AI version properties.
- `pom.xml:33-113` — Loomspan/Spring/H2/Flyway dependencies and Maven frontend-build/resource-copy packaging shape.
- `src/main/java/demo/wayfarer/WayfarerApplication.java:1-8` — current Spring Boot entry point and package namespace.
- `src/main/java/demo/wayfarer/ApiController.java:10-33` — current unauthenticated REST surface and generic malformed-JSON/domain error responses.
- `src/main/java/demo/wayfarer/AssessmentService.java:17-48` — asynchronous Loomspan planning execution and restart/queue failure handling.
- `src/main/java/demo/wayfarer/IntakeService.java:60-91` — `interpretTripChange` model invocation for natural-language changes; failed interpretation leaves the current request/booking unchanged.
- `src/main/java/demo/wayfarer/TravelSkills.java:10-47` — Loomspan-exposed catalog searches and deterministic evaluation handoff.
- `src/main/java/demo/wayfarer/TripCalculator.java:16-169` — current deterministic request validation, catalog evaluation, candidate selection rules, and model-result validation.
- `src/main/java/demo/wayfarer/TripStore.java:46-56` — consistent trip/catalog locking used by assessment, booking, and exchange operations.
- `src/main/resources/application.yml:1-29` — Wayfarer names, H2 location, model endpoint/credential configuration, skill locations, and observability.
- `src/main/resources/db/migration/V1__business_schema.sql:1-10` — legacy shared catalog/trip/assessment/booking schema; booking is unique per trip.
- `src/main/resources/db/migration/V4__booking_exchanges.sql:1-7`, `V5__service_disruptions.sql:1-7`, and `V6__conversation_drafts.sql:1-7` — legacy exchange, supplier-disruption, and model-conversation persistence.
- `frontend/src/main.tsx:10-64` and `frontend/src/Conversation.tsx:21-29` — current assessment/booking polling UI and Loomspan change-conversation UI.
- `scripts/generate-skill-manifests.py:1-108` — generator for the current YAML model skill manifests.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Reusable inventory and transaction behavior | Java captures inventory, locks trip/catalog records, rechecks quote/availability, performs stock mutation transactionally, and supports idempotent booking requests. The P00-T01 disposition retains those general properties while replacing Wayfarer fixtures/contracts. |
| Trip planning and validation | Wayfarer validates and evaluates a fixed two-adult Boston--New York rail/flight/hotel request in Java, then persists model-derived assessment choices. DeTour requirements instead define user-owned Trip details and progressive airfare/stay/car alternatives. |
| Model orchestration and natural-language changes | `AssessmentService`, `IntakeService`, `TravelSkills`, five YAML manifests, model configuration, and live tests depend on Loomspan. The roadmap explicitly makes deterministic Java services responsible for planning, interpretation, ranking, explanation, and validation, without a substitute model integration. |
| Booking, exchange, disruption, cancellation, and recovery | Current booking is a mutable single booking row per globally visible trip; exchanges persist before/after JSON, and return-service cancellation simulates a supplier disruption. The roadmap distinguishes immutable booked snapshots, active Bookings, and Canceled Booking history; it defers exchange, disruption, and recovery. |
| Authentication | No authentication, users, owner foreign key, or authorization checks exist in the controller or V1--V6 schema. The roadmap requires login and backend-enforced per-user isolation. |
| Persistence | Current Flyway history stores legacy JSON payloads, fixed October 2026 inventory, assessments/model receipts, exchanges, disruptions, and intake drafts. The roadmap states that this database/data is non-migratable and DeTour uses a new H2 database and fresh Flyway history. |
| Frontend flows | The current frontend is a single fixed-scenario workspace for requirements, asynchronous assessment, model trace display, booking/exchange/recovery, and free-text conversation. The roadmap defines authenticated DeTour entry points, profile/trip alternatives, component selection, comparison, and booking/cancellation flows instead. |
| Observability | `application.yml` configures Loomspan observability and execution trace persistence; the UI displays model execution events. The current observability surface is coupled to Loomspan. |
| Scripts | `scripts/generate-skill-manifests.py` creates model skill YAML; `scripts/run.ps1` targets `wayfarer-0.1.0-SNAPSHOT.jar`; `scripts/reset-demo.ps1` deletes only named Wayfarer database files after explicit confirmation. |
| Documentation | Root README and `docs/` describe Wayfarer, its fixed demo scenario, model credentials, skills, provider tests, exchange, and disruption recovery. Roadmap and Phase documents are the DeTour product authority; they state that old UI copy, YAML skills, and demo documents are not interpretive sources for DeTour behavior. |
| Tests | Five JUnit classes cover the current app. P00-T01 classified all 41 current tests: 6 reusable general transaction/HTTP behaviors, 19 Wayfarer-scenario behaviors to replace, and 16 Loomspan/model-coupled behaviors to remove. No frontend test command or tracked frontend test/spec file exists. |

## Existing Tests and Fixtures

P00-T01 is the governing test-disposition input (`ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md:49-73`). The reusable group consists of five `TripApplicationTest` booking/inventory properties (atomic reservation, concurrency, idempotency, stale quote/state rejection, and cross-trip isolation) plus `HttpFlowTest.malformedJsonAndUnknownIdsReturnUsefulHttpStatuses`. It does not preserve old routes, data, or fixture meaning.

The replacement group covers fixed Boston--New York catalog/request behavior, current assessment/booking HTTP flow, and exchange/disruption/recovery scenarios. The removal group contains model-output/result-validation lifecycle tests, all seven `IntakeServiceTest` model-conversation tests, opt-in live tests gated by `WAYFARER_LIVE_TEST=true`, and model-provider HTTP handling.

P00-T01 attempted `./mvnw.cmd test -DskipFrontend=true` and `./mvnw.cmd package`; both stopped before execution because Java was unavailable on `PATH`. It found no configured frontend test command, and `npm.cmd run build --prefix frontend` passed. These results are environment observations, not evidence of a retained-platform incompatibility.

## Dependencies and Operational Constraints

- The current executable product depends on a Loomspan Spring Boot starter, Spring AI BOM, OpenAI-compatible endpoint, and environment-provided model credentials (`pom.xml:16-35`, `src/main/resources/application.yml:11-29`). Its live tests may call a provider when explicitly enabled; no such external action was run for this research.
- The current `data/` database and frontend/generated build directories are ignored. The baseline and roadmap require that old Wayfarer files not be migrated, read, mutated, or automatically deleted for DeTour.
- Maven's existing lifecycle executes `npm ci`, `npm run build`, and copies the built frontend into the Boot output. This is the observed single packaged-application shape; current naming is `wayfarer-0.1.0-SNAPSHOT.jar` in `scripts/run.ps1:3`.
- Repository evidence contains no selected target Maven artifact, Spring application name, configuration prefix, frontend package/application name, or packaged-output filename beyond the ticket's required `detour` convention. Detailed schemas and HTTP APIs are likewise absent from the Phase 0 scope.

## Historical Context

The roadmap was added in commit `5fb7d2b`; the P00-T01 baseline was added in `fd3bfcb` and cleaned up in `dba72bd`. The authoritative roadmap, continuation guide, and Phase 0 file agree that DeTour uses `app.detour`, removes Loomspan without replacement, retains Java 21/Spring Boot/React/H2/Flyway/single-package deployment unless a concrete incompatibility appears, and does not migrate the old database (`ai/thoughts/phases/README.md:24-32`, `ai/thoughts/phases/CONTINUATION.md:33-43`, `ai/thoughts/phases/phase-0-baseline-and-boundaries.md:9-26`).

The roadmap also establishes the target ownership/lifecycle vocabulary: User owns Trips and downstream user data; Trip holds shared travel intent and alternatives; Itinerary holds selected components/totals; Booking is the inventory-reserving immutable snapshot; catalogs/inventory are application reference data. Canceled Booking history is not an itinerary state, and exchange/disruption/recovery are deferred (`ai/thoughts/phases/README.md:46-64`, `ai/thoughts/phases/README.md:114-131`; `ai/thoughts/phases/CONTINUATION.md:85-108`).

## Open Questions

No research blocker was found. The current repository contains no target naming selection beyond the required `app.detour`/`detour` constraints, so the ticket's architecture record is the durable place where those conventions are to be selected. Detailed schema, endpoint, authentication, catalog-fixture, workflow, and visual-design choices remain outside this ticket's stated scope.
