---
date: 2026-09-22
repository: loomspan-travel-demo
branch: main
commit: be91bb69369b756af6b5ccb45024821dc8fdae92
ticket: ai/thoughts/tickets/2026-09-22-p06-t01-atomic-inventory-reservation-and-booking-engine.md
tags: [booking, inventory, concurrency, schema, p06]
---

# Atomic Inventory Reservation and Booking Record Engine Research

## Research Question

How does the codebase currently represent and manage catalog inventory (flights, accommodations, rental cars), trip alternatives (Drafts and Planned itineraries), locks and transaction boundaries, error handling, reference generation, and persistence? What components and schemas will be affected when adding the atomic booking engine?

## Summary

The repository currently supports authenticated travelers creating trips, maintaining draft selections across airfare, stays, and rental cars, evaluating draft readiness, and promoting drafts into immutable Planned itineraries (`detour_planned_itinerary`) with frozen component snapshots (`detour_planned_*_snapshot`). However, booking is not yet implemented: `detour_booking` does not exist, `TripRepository.hasBookingHistory` returns hardcoded `false`, `TripProfileSummary.bookedCount` is hardcoded to `0`, and the frontend review screen (`BookingReviewView.tsx`) has a staged, disabled "Confirm Booking" button awaiting the Phase 6 engine.

Finite inventory is managed across three distinct models:
1. `flight_instance.available_seats` (integer count checked against `seat_capacity` and decremented per seat).
2. `accommodation_nightly_inventory.available_inventory` (per unit and per night date, checked against `inventory_capacity` and decremented per unit).
3. `rental_unit_occupancy` (half-open `[pickup_at, return_at)` intervals with `occupancy_status = 'ACTIVE'`, protected against overlap by `RentalUnitOccupancyOverlapTrigger` which already executes a pessimistic `SELECT id FROM rental_unit WHERE id = ? FOR UPDATE`).

The backend runs on Java 21/25 with Spring Boot 4.1.0 using Spring JDBC (`JdbcTemplate`, no JPA), Flyway migrations up to `V16`, Jackson 3 (`tools.jackson.databind.JsonNode`), and H2 database 2.4.240.

## Repository State

- **Date:** 2026-09-22
- **Repository:** loomspan-travel-demo
- **Branch:** `main`
- **Commit:** `be91bb69369b756af6b5ccb45024821dc8fdae92`
- **Working tree:** Clean
- **Migration baseline:** 16 applied migrations (`V1` through `V16` in `src/main/resources/db/migration/`).

## Current Behavior and Data Flow

### 1. Trip and Itinerary Lifecycle
- **Trip Aggregate:** A trip (`detour_trip`) is owned by an authenticated user (`owner_user_id`), bound to a supported destination (`catalog_destination_id`), travel dates (`start_date`, `end_date` in March 2027), traveler count (1–8), individual traveler ages, budget in cents, and an optimistic locking version counter (`version`).
- **Drafts:** Each trip has one or more drafts (`detour_trip_draft`). Drafts hold mutable foreign key references to catalog items:
  - Airfare: `detour_trip_draft_airfare_selection` (`outbound_flight_instance_id`, `return_flight_instance_id`).
  - Stay: `detour_trip_draft_stay_selection` (`accommodation_unit_id`, `unit_count`).
  - Rental: `detour_trip_draft_rental_selection` (`rental_unit_id`, `pickup_at`, `return_at`).
- **Promotion to Planned:** When a draft is promoted (`POST /api/trips/{tripId}/drafts/{draftId}/plan`), `TripService.promoteDraft` validates readiness (`evaluateDraftBlockingIssues`), enforces budget acknowledgment if over budget, and persists a `detour_planned_itinerary` row. It copies and freezes all descriptive display attributes and prices into:
  - `detour_planned_airfare_snapshot`
  - `detour_planned_stay_snapshot`
  - `detour_planned_stay_night_snapshot`
  - `detour_planned_rental_snapshot`
  Once planned, itineraries are immutable (`IMMUTABLE_ALTERNATIVE`).
- **Trip Versioning and Optimistic Locking:** Mutations to trips and drafts require `expectedVersion` (and `expectedDraftVersion`). `JdbcTripRepository.advanceVersion` increments `detour_trip.version` conditionally (`WHERE id = ? AND owner_user_id = ? AND version = ?`). Stale versions fail with HTTP 409 `VERSION_CONFLICT`.
- **Trip Expiration:** Evaluated in `TripService.isExpired(LocalDate startDate)`:
  `Instant departureMidnight = startDate.atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant(); return !clock.instant().isBefore(departureMidnight);`
  Where `PDX_ZONE` is `ZoneId.of("America/Los_Angeles")`.

### 2. Finite Inventory Model
- **Flight Seats:**
  `flight_instance` has `seat_capacity` and `available_seats`. Constraint `ck_flight_instance_available_seats` ensures `available_seats BETWEEN 0 AND seat_capacity`.
- **Accommodation Units:**
  `accommodation_nightly_inventory` has `(accommodation_unit_id, night_date)` as primary key, with `inventory_capacity` and `available_inventory`. Constraint `ck_accommodation_nightly_available` ensures `available_inventory BETWEEN 0 AND inventory_capacity`. Stay nights span `[startDate, endDate)`: `night_date >= startDate AND night_date < endDate`.
- **Rental Car Units:**
  `rental_unit_occupancy` tracks reservations with `rental_unit_id`, `pickup_at`, `return_at`, `occupancy_status IN ('ACTIVE', 'RELEASED')`. Half-open non-overlapping active occupancy is enforced at the database level by trigger `RentalUnitOccupancyOverlapTrigger`.

### 3. Concurrency and Locking
- `RentalUnitOccupancyOverlapTrigger` executes `SELECT id FROM rental_unit WHERE id = ? FOR UPDATE` on each insert or update of an `ACTIVE` row.
- Transactions are managed via Spring `@Transactional` on service methods.
- The repository utilizes pure `JdbcTemplate` for queries and updates.

### 4. Pricing and Calculations
- `ItineraryTallyEngine` provides canonical price calculation:
  - Airfare: `(outboundTotal + inboundTotal) * travelerCount`.
  - Stay: sum of `(basePrice + tax + fee)` for all nights multiplied by `unitCount`.
  - Rental: `billingCycles * dailyRate`, where billing cycles are 24-hour cycles: `Math.max(1, (Duration.between(pickupAt, returnAt).getSeconds() + 86399) / 86400)`.
  - Grand total: sum of component subtotals.

### 5. API and Error Handling
- REST endpoints are mapped in `TripController` under `@RequestMapping("/api/trips")`.
- Authentication is enforced by Spring Security (`anyRequest().authenticated()`). Controllers access `DetourUserPrincipal` via `@AuthenticationPrincipal`.
- Access isolation: `ownedTrip(ownerUserId, tripId)` throws `ApiException(404, "RESOURCE_NOT_FOUND")` when a trip does not belong to the user, preventing cross-user information disclosure.
- `ApiException` carries `status` (HTTP status code), `code` (string error code), `message`, and optional `Map<String, String> fields`.
- `ApiExceptionHandler` formats `ApiException` into `ApiError(code, message, fields)`.

## Key Components

- `src/main/resources/db/migration/V3__create_shared_catalog_and_flight_schema.sql:85-105` — `flight_instance` table definition, seat capacity, available seats, and check constraints.
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:38-52` — `accommodation_nightly_inventory` table definition, nightly available inventory, and check constraints.
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:97-114` — `rental_unit_occupancy` table definition, status constraint (`ACTIVE`, `RELEASED`), and `RentalUnitOccupancyOverlapTrigger` trigger declaration.
- `src/main/java/app/detour/catalog/persistence/RentalUnitOccupancyOverlapTrigger.java:22-49` — Trigger enforcing half-open active interval non-overlap using `SELECT id FROM rental_unit WHERE id = ? FOR UPDATE`.
- `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql:1-48` — `detour_trip`, `detour_trip_traveler`, and `detour_trip_draft` tables.
- `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql:33-97` — `detour_planned_itinerary` and planned snapshot tables (`detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, `detour_planned_stay_night_snapshot`, `detour_planned_rental_snapshot`).
- `src/main/resources/db/migration/V16__enhance_planned_snapshot_schema.sql:1-35` — Full snapshot attribute definitions ensuring planned itineraries never join live catalog tables for display.
- `src/main/java/app/detour/trip/PlannedItinerary.java:8-66` — `TripAlternative` sealed interface, `PlannedItinerary`, `DraftSelections`, `AirfareSelection`, `StaySelection`, `StayNight`, `RentalSelection` records.
- `src/main/java/app/detour/trip/ItineraryTallyEngine.java:9-50` — Pure calculation engine for airfare, stay, rental, and grand total.
- `src/main/java/app/detour/trip/TripService.java:107-110` — `isExpired(LocalDate startDate)` method checking departure midnight in `America/Los_Angeles`.
- `src/main/java/app/detour/trip/TripService.java:112-169` — `tripsProfile` building summary where `bookedCount` is currently hardcoded to `0` and `trips.hasBookingHistory(trip.id())` is queried.
- `src/main/java/app/detour/trip/TripService.java:348-366` — `deleteTrip` method checking `trips.hasBookingHistory(trip.id())` and rejecting deletion with 409 `CANNOT_DELETE_BOOKED_TRIP`.
- `src/main/java/app/detour/trip/TripRepository.java:22` and `src/main/java/app/detour/trip/JdbcTripRepository.java:255` — `hasBookingHistory(long tripId)` interface method and repository implementation (currently returns `false`).
- `src/main/java/app/detour/trip/JdbcTripRepository.java:162-215` — `loadPlannedSelections` retrieving frozen descriptive and financial attributes from planned snapshot tables.
- `src/main/java/app/detour/trip/TripController.java:22-250` — REST endpoints for trip creation, retrieval, updates, draft mutations, readiness inspection, and promotions.
- `src/main/java/app/detour/trip/TripRequests.java:135-238` — Request parser using Jackson 3 (`tools.jackson.databind.JsonNode`) with strict validation helpers.
- `src/main/java/app/detour/api/ApiExceptionHandler.java:12-16` and `ApiError.java:5-9` — Central error handling mapping `ApiException` to structured JSON HTTP responses.
- `frontend/src/components/BookingReviewView.tsx:269-281` — UI component for booking review displaying component snapshots, authoritative totals, fictional inventory disclosure, and disabled "Confirm Booking" button.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Database Migration (`V17__create_booking_schema.sql`) | Currently migrations end at `V16`. A new migration is required to create `detour_booking` and snapshot persistence structures with unique constraints and foreign keys. |
| Unique Active Booking Constraint | H2 2.4.240 does not support `CREATE UNIQUE INDEX ... WHERE status = 'ACTIVE'`. Enforcing at most one active booking per trip requires a generated column or trigger in H2 (e.g. `active_trip_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN trip_id ELSE NULL END) UNIQUE`). |
| `TripRepository` & `JdbcTripRepository` | `hasBookingHistory(long tripId)` returns `false` (`JdbcTripRepository.java:255`). It must be updated to query `detour_booking`. New repository methods for booking creation, inventory locking, inventory decrements, and booking queries are needed. |
| `TripService` / Booking Engine | No booking engine exists. Must implement atomic booking logic, deterministic ordering of inventory row locks, conflict detection, transaction rollback on exhaustion, idempotency key deduplication, and reference generation. |
| `TripProfileSummary` Projection | `TripService.java:156` passes hardcoded `0` for `bookedCount`. This should reflect active bookings for the trip. |
| Trip Controllers & API Endpoints | `TripController.java` does not expose `POST /api/trips/{tripId}/bookings`, `GET /api/trips/{tripId}/bookings/active`, or `GET /api/trips/{tripId}/bookings`. |
| Request & Response DTOs | `TripRequests.java` does not parse booking requests (`plannedItineraryId`, `expectedVersion`, `idempotencyKey`). `BookingResponse` record does not exist. |

## Existing Tests and Fixtures

- `src/test/java/app/detour/catalog/CatalogSchemaIntegrationTest.java`:
  Verifies check constraints, foreign keys, and temporal/monetary limits on catalog tables.
- `src/test/java/app/detour/catalog/RentalUnitOccupancyConstraintIntegrationTest.java`:
  Verifies that `RentalUnitOccupancyOverlapTrigger` rejects overlapping active occupancies while permitting half-open adjacent boundaries (`t1 <= pickup < t2` and `t2 <= pickup < t3`).
- `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`:
  Verifies draft validation, promotion, snapshot creation, and concurrent promotion races using `CyclicBarrier` and `ExecutorService` (lines 452–468).
- `src/test/java/app/detour/trip/TripApiIntegrationTest.java`:
  Verifies trip creation, optimistic concurrency control (`VERSION_CONFLICT`), ownership isolation, expiration checks, and mocked `hasBookingHistory` blocking trip deletion (`CANNOT_DELETE_BOOKED_TRIP`).
- `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`:
  Verifies that aggregates and snapshots survive application restarts without relying on live catalog state by stopping and starting `ConfigurableApplicationContext` against file-backed H2 databases.

## Dependencies and Operational Constraints

1. **H2 Partial Index Limitation:**
   H2 does not support partial/filtered indexes with `WHERE` clauses (e.g. `CREATE UNIQUE INDEX ... ON detour_booking (trip_id) WHERE status = 'ACTIVE'` fails with a syntax error in H2 2.4.240). The standard mechanism in H2 is a computed column (e.g. `active_trip_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN trip_id ELSE NULL END)`) with a `UNIQUE` constraint or unique index, because H2 permits multiple `NULL` values in a unique constraint.
2. **Framework and Library Versions:**
   - Spring Boot 4.1.0 with Spring JDBC (`JdbcTemplate`).
   - JSON parsing uses Jackson 3 packages (`tools.jackson.databind.JsonNode`, `tools.jackson.databind.json.JsonMapper`).
   - Pure relational persistence without JPA or Hibernate.
3. **Pessimistic Locking in H2:**
   Pessimistic locking in H2 is achieved via `SELECT ... FOR UPDATE` inside a `@Transactional` boundary with an active transaction. `RentalUnitOccupancyOverlapTrigger` already executes `SELECT id FROM rental_unit WHERE id = ? FOR UPDATE`.
4. **Time Zone Rules:**
   Trip expiration is strictly anchored to `America/Los_Angeles` (`ClockConfiguration.PDX_ZONE`) midnight of the trip's `startDate`.
5. **No Live External Calls:**
   The booking engine is strictly local and simulated. Real payment gateways, airline GDSs, or third-party APIs are excluded.

## Historical Context

- **Phase 0 & 1:** Established core platform boundaries, secure session authentication, and database migration policies.
- **Phase 2:** Established shared catalog and finite inventory schema (`flight_instance`, `accommodation_nightly_inventory`, `rental_unit_occupancy`), ensuring inventory is application-owned and never user-owned.
- **Phase 3:** Introduced `detour_trip`, versioned drafts, and immutable planned snapshots (`detour_planned_*_snapshot`) so historical trip records never join live catalog data.
- **Phase 5:** Introduced `ItineraryTallyEngine` for canonical financial calculations and created `BookingReviewView.tsx` with a disabled "Confirm Booking" button in anticipation of Phase 6.

## Open Questions

1. **Active Booking Uniqueness in H2:**
   Because H2 does not support `CREATE UNIQUE INDEX ... WHERE status = 'ACTIVE'`, planning must evaluate whether to use a generated column (e.g. `active_trip_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN trip_id ELSE NULL END) UNIQUE`) or a trigger/check to enforce at most one active booking per trip.
2. **Snapshot Persistence Schema:**
   The ticket mentions "Create table `detour_booking_item_snapshot` (or snapshot columns) preserving the exact frozen descriptive details and prices of booked components". Planning must decide whether to use dedicated component snapshot tables (e.g. `detour_booking_airfare_snapshot`, `detour_booking_stay_snapshot`, `detour_booking_stay_night_snapshot`, `detour_booking_rental_snapshot`) mirroring the planned snapshot design, or a unified `detour_booking_item_snapshot` table.
3. **Airfare Reference Format Convention:**
   The ticket requirements describe airfare confirmation as "6 uppercase alphanumeric characters (airline PNR record locator style, e.g. W3X8PL)", while acceptance criterion #7 mentions `FL-XXXXXX`. Planning should establish whether a prefix (`FL-`) or 6-character PNR format is used.
4. **Trip Version Increment on Booking:**
   The ticket requires validating `expectedVersion` against `detour_trip.version` to prevent booking against stale trip state. Planning should clarify whether a successful booking also increments `detour_trip.version` (consistent with draft and planning mutations).
5. **Controller Placement:**
   Planning should decide whether the booking endpoints (`POST /api/trips/{tripId}/bookings`, `GET /api/trips/{tripId}/bookings/active`, `GET /api/trips/{tripId}/bookings`) are added directly to `TripController` or placed in a dedicated `BookingController`.

---

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-09-22-p06-t01-atomic-inventory-reservation-and-booking-engine.md
SUMMARY: Researched the existing inventory, trip, draft, planned snapshot, and transaction models across the codebase. Identified the H2 partial index constraint limitation and mapped out all affected database, service, and API layers. Documented key components, existing concurrency and restart tests, and open design questions for planning.
DECISIONS:
  - none
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Proceed to Step 2 (create plan) and Step 3 (testing plan) using execution profile full.
