---
date: 2026-09-22
repository: loomspan-travel-demo
branch: main
commit: c84ee48929b253c310fe7a6c22fdeef5d180a580
ticket: ai/thoughts/tickets/2026-09-22-p06-t03-transactional-cancellation-and-lifecycle-policies.md
tags: [backend, booking, cancellation, inventory, lifecycle, transactions, concurrency]
---

# Transactional Cancellation Engine and Lifecycle Policies Research

## Research Question

How does the current codebase manage booking persistence, catalog inventory reservation, trip lifecycle states, version concurrency, alternative deletion safeguards, trip deletion policies, and expiration checks, and how are these current mechanisms structured in preparation for fee-free booking cancellation, trip cancellation, and lifecycle policy enforcement?

## Summary

The backend system currently supports atomic inventory reservation during booking creation (`P06-T01`) and exposes active booking inspection and history retrieval (`P06-T02`). Booking state is stored in `detour_booking` with an active/canceled status check constraint, an immutable grand total, confirmation references, and frozen component snapshots across airfare, accommodation, and rental car reservations. Finite catalog inventory is decremented across `flight_instance.available_seats`, `accommodation_nightly_inventory.available_inventory`, and `rental_unit_occupancy` with an active overlap trigger.

However, cancellation mechanisms do not yet exist in the codebase:
1. **No inventory increment or occupancy release methods** exist on `BookingRepository` or `JdbcBookingRepository`.
2. **`detour_trip` table and `Trip` domain class do not possess a `status` column or field**, meaning trips are implicitly active without a persistent `CANCELED` state.
3. **Trip mutation endpoints in `TripService` do not check for a canceled trip status**, allowing draft creations, draft modifications, promotions, and shared details updates regardless of trip state.
4. **`TripService.deleteAlternative` does not verify if a Planned alternative is actively booked**, risking deletion of active booking references or constraint violations.
5. **No cancellation endpoints** (`POST /api/trips/{tripId}/bookings/{bookingId}/cancel` or `POST /api/trips/{tripId}/cancel`) are declared on `TripController` or implemented in `BookingService`/`TripService`.
6. **`TripRepository.hasBookingHistory`** is already implemented and enforced in `TripService.deleteTrip`, rejecting deletion of trips with booking history with HTTP 409 (`CANNOT_DELETE_BOOKED_TRIP`).

## Repository State

- **Date:** 2026-09-22
- **Repository:** `loomspan-travel-demo`
- **Branch:** `main`
- **Commit:** `c84ee48929b253c310fe7a6c22fdeef5d180a580`
- **Working Tree:** Clean (zero uncommitted files).
- **Test Baseline:**
  - Backend: 166 passing tests (`.\mvnw.cmd test`, 0 failures, 0 errors).
  - Frontend: 88 passing tests across 8 test suites (`npm.cmd test -- --run` in `frontend/`).

## Current Behavior and Data Flow

### 1. Booking Creation and Inventory Reservation Flow

- **Entry Point:** `POST /api/trips/{tripId}/bookings` handled by `TripController.createBooking` ([`TripController.java:251-263`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripController.java#L251-L263)).
- **Service Delegation:** `BookingService.book` ([`BookingService.java:54-79`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/BookingService.java#L54-L79)) verifies trip ownership via `ownedTrip` and checks for existing idempotency before delegating to `BookingTransactionExecutor`.
- **Transaction Execution (`@Transactional`):** `BookingTransactionExecutor.executeBookingTransaction` ([`BookingTransactionExecutor.java:48-193`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/BookingTransactionExecutor.java#L48-L193)) runs the reservation sequence:
  1. *Expiration check:* `trip.startDate().atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant()`. If `!clock.instant().isBefore(departureMidnight)`, throws `ApiException(400, "TRIP_EXPIRED")` ([`BookingTransactionExecutor.java:51-54`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/BookingTransactionExecutor.java#L51-L54)).
  2. *Expected version check:* `if (trip.version() != request.expectedVersion())` throws `ApiException(409, "VERSION_CONFLICT")` ([`BookingTransactionExecutor.java:57-59`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/BookingTransactionExecutor.java#L57-L59)).
  3. *Active booking check:* `if (bookingRepository.hasActiveBooking(trip.id()))` throws `ApiException(409, "ALREADY_BOOKED")` ([`BookingTransactionExecutor.java:62-64`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/BookingTransactionExecutor.java#L62-L64)).
  4. *Deterministic locking & availability check:*
     - Flight instances: `bookingRepository.lockFlightInstances(flightInstanceIds)` locks flight instances with `SELECT available_seats FROM flight_instance WHERE id = ? FOR UPDATE` sorted by ID ([`JdbcBookingRepository.java:32-40`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/JdbcBookingRepository.java#L32-L40)).
     - Stay nightly inventory: `bookingRepository.lockStayNightlyInventory(stay.accommodationUnitId(), minDate, maxDate)` locks nightly rows with `SELECT night_date, available_inventory FROM accommodation_nightly_inventory WHERE ... ORDER BY accommodation_unit_id ASC, night_date ASC FOR UPDATE` ([`JdbcBookingRepository.java:43-55`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/JdbcBookingRepository.java#L43-L55)).
     - Rental unit: `bookingRepository.lockRentalUnit(rental.rentalUnitId())` locks unit with `SELECT id FROM rental_unit WHERE id = ? FOR UPDATE` ([`JdbcBookingRepository.java:58-60`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/JdbcBookingRepository.java#L58-L60)), and `isRentalAvailable` counts active overlapping occupancy rows ([`JdbcBookingRepository.java:63-73`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/JdbcBookingRepository.java#L63-L73)).
  5. *Inventory decrements:*
     - Flights: `decrementFlightSeats` decrements `available_seats = available_seats - ?` with `available_seats >= ?` ([`JdbcBookingRepository.java:76-81`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/JdbcBookingRepository.java#L76-L81)).
     - Stay: `decrementStayInventory` decrements `available_inventory = available_inventory - ?` with `available_inventory >= ?` ([`JdbcBookingRepository.java:84-89`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/JdbcBookingRepository.java#L84-L89)).
     - Rental: `insertRentalOccupancy` inserts a row into `rental_unit_occupancy` with `occupancy_status = 'ACTIVE'` ([`JdbcBookingRepository.java:92-106`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/JdbcBookingRepository.java#L92-L106)).
  6. *Record insertion & frozen snapshots:* `insertBooking` writes to `detour_booking` with generated references (`DT-`, `FL-`, `HT-`, `RC-`), `status = 'ACTIVE'`, and `rental_occupancy_id` ([`JdbcBookingRepository.java:109-141`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/JdbcBookingRepository.java#L109-L141)). `copySnapshotsFromPlanned` copies frozen component rows into `detour_booking_*_snapshot` tables ([`JdbcBookingRepository.java:144-212`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/JdbcBookingRepository.java#L144-L212)).
  7. *Trip version advance:* `tripRepository.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())` executes `UPDATE detour_trip SET version = version + 1 WHERE id = ? AND owner_user_id = ? AND version = ?` ([`JdbcTripRepository.java:221`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/JdbcTripRepository.java#L221)).

### 2. Booking Record and State Storage

- Schema is defined in `V17__create_booking_schema.sql` ([`V17__create_booking_schema.sql:4-29`](file:///c:/code/loomspan-travel-demo/src/main/resources/db/migration/V17__create_booking_schema.sql#L4-L29)):
  - `status VARCHAR(20) NOT NULL` constrained by `ck_detour_booking_status CHECK (status IN ('ACTIVE', 'CANCELED'))`.
  - `canceled_at TIMESTAMP WITH TIME ZONE` (nullable).
  - `rental_occupancy_id BIGINT` references `rental_unit_occupancy(id)`.
  - `active_trip_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN trip_id ELSE NULL END)` with unique constraint `uq_detour_booking_active_trip UNIQUE (active_trip_id)`. This enforces that at most one booking per trip can be `ACTIVE`. When canceled, `active_trip_id` becomes `NULL`, allowing subsequent re-booking.
  - `detour_booking_airfare_snapshot` stores `outbound_flight_instance_id` and `return_flight_instance_id`.
  - `detour_booking_stay_snapshot` stores `accommodation_unit_id` and `unit_count`; `detour_booking_stay_night_snapshot` stores each `night_date`.
  - `detour_booking_rental_snapshot` stores `rental_unit_id`, `pickup_at`, `return_at`.

### 3. Trip Lifecycle and Domain Representation

- **`detour_trip` table:** Created in `V12__create_owned_trip_and_initial_draft_schema.sql:1-19` ([`V12__create_owned_trip_and_initial_draft_schema.sql:1-19`](file:///c:/code/loomspan-travel-demo/src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql#L1-L19)) with columns: `id`, `public_id`, `owner_user_id`, `catalog_destination_id`, `start_date`, `end_date`, `traveler_count`, `budget_cents`, `display_label`, `version`. It currently has **no `status` column**.
- **`Trip` record:** Defined in `Trip.java` ([`Trip.java:7-10`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/Trip.java#L7-L10)). Contains `id`, `publicId`, `ownerUserId`, `destination`, `startDate`, `endDate`, `travelerCount`, `travelerAges`, `budgetCents`, `label`, `version`, `drafts`, `planned`. It currently has **no `status` field**.
- **`TripResponse` record:** Defined in `TripResponse.java` ([`TripResponse.java:7-27`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripResponse.java#L7-L27)). Exposes destination, dates, traveler count, version, drafts, planned, alternatives, revisionSummary, and tally. It currently has **no `status` field**.
- **`TripProfileSummary` record:** Defined in `TripProfileSummary.java` ([`TripProfileSummary.java:7-23`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripProfileSummary.java#L7-L23)). Contains `temporalStatus` (`"UPCOMING"` or `"PAST"`), `draftCount`, `plannedCount`, `expiredAlternativeCount`, `bookedCount`, `hasBookingHistory`, `primaryBookingReference`, `alternatives`.

### 4. Trip Deletion Policy

- Handled in `TripService.deleteTrip` ([`TripService.java:350-366`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripService.java#L350-L366)):
  ```java
  if (!Boolean.TRUE.equals(request.confirmed())) {
      throw validation("confirmed", "Set confirmed to true before deleting a Trip.");
  }
  if (trips.hasBookingHistory(trip.id())) {
      throw new ApiException(409, "CANNOT_DELETE_BOOKED_TRIP", "Trips with booking history cannot be permanently deleted.");
  }
  if (trip.version() != request.expectedVersion()) {
      throw parentConflict(ownerUserId, trip.publicId());
  }
  if (trip.drafts().size() != request.expectedDraftCount() || trip.planned().size() != request.expectedPlannedCount()) {
      throw new ApiException(409, "STALE_CONFIRMATION", "The Trip alternative counts have changed since confirmation.");
  }
  trips.deleteTrip(trip.id(), ownerUserId);
  ```
- Checked via `JdbcTripRepository.hasBookingHistory` ([`JdbcTripRepository.java:256-259`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/JdbcTripRepository.java#L256-L259)):
  ```java
  Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM detour_booking WHERE trip_id = ?", Integer.class, tripId);
  return count != null && count > 0;
  ```
  Verified by test `retrievesActiveBookingAndHistoryAndUpdatesProfile` in `BookingApiIntegrationTest.java:544-549` ([`BookingApiIntegrationTest.java:544-549`](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/booking/BookingApiIntegrationTest.java#L544-L549)).

### 5. Alternative Deletion Safeguards

- Handled in `TripService.deleteAlternative` ([`TripService.java:332-347`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripService.java#L332-L347)):
  ```java
  if (source instanceof TripDraft draft) {
      if (request.expectedDraftVersion() == null) throw validation("expectedDraftVersion", "This field is required for a Draft source.");
      if (!trips.advanceVersionForDraft(trip.id(), ownerUserId, request.expectedVersion(), draft.id(), request.expectedDraftVersion())) throw mutationConflict(ownerUserId, trip.publicId(), draft.publicId(), request.expectedDraftVersion());
      trips.deleteDraft(trip.id(), draft.id());
  } else {
      if (!Boolean.TRUE.equals(request.confirmed())) throw validation("confirmed", "Set confirmed to true before deleting a Planned alternative.");
      if (request.expectedDraftVersion() != null) throw validation("expectedDraftVersion", "Planned alternatives do not have a Draft version.");
      if (!trips.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())) throw parentConflict(ownerUserId, trip.publicId());
      trips.deletePlanned(trip.id(), source.id());
  }
  ```
- **Current gap:** `deletePlanned` has no check to verify whether `source.id()` is referenced by an `ACTIVE` booking. In `V17__create_booking_schema.sql:23`, `CONSTRAINT fk_booking_planned_itinerary FOREIGN KEY (planned_itinerary_id) REFERENCES detour_planned_itinerary(id) ON DELETE CASCADE`. If a planned alternative with an active booking were deleted, it would either cascade-delete the active booking or trigger constraint conflicts without returning the required HTTP 409 `CANNOT_DELETE_ACTIVE_BOOKED_ALTERNATIVE`.

### 6. Trip Expiration Checks

- `TripService.isExpired(LocalDate startDate)` ([`TripService.java:107-110`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripService.java#L107-L110)):
  ```java
  public boolean isExpired(LocalDate startDate) {
      Instant departureMidnight = startDate.atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant();
      return !clock.instant().isBefore(departureMidnight);
  }
  ```
- `BookingTransactionExecutor.java:51-54` ([`BookingTransactionExecutor.java:51-54`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/BookingTransactionExecutor.java#L51-L54)):
  ```java
  Instant departureMidnight = trip.startDate().atStartOfDay(ClockConfiguration.PDX_ZONE).toInstant();
  if (!clock.instant().isBefore(departureMidnight)) {
      throw new ApiException(400, "TRIP_EXPIRED", "Cannot book an expired trip.");
  }
  ```
  Verified by test `rejectsBookingOnExpiredTrip` in `BookingApiIntegrationTest.java:203-225` ([`BookingApiIntegrationTest.java:203-225`](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/booking/BookingApiIntegrationTest.java#L203-L225)).

### 7. Rental Unit Occupancy Trigger

- Defined in `V4__create_catalog_inventory_schema.sql:97-114` ([`V4__create_catalog_inventory_schema.sql:97-114`](file:///c:/code/loomspan-travel-demo/src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql#L97-L114)) and implemented in `RentalUnitOccupancyOverlapTrigger.java` ([`RentalUnitOccupancyOverlapTrigger.java:22-49`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/catalog/persistence/RentalUnitOccupancyOverlapTrigger.java#L22-L49)).
- When `occupancy_status` is updated to `'RELEASED'`, line 23 (`if (!"ACTIVE".equals(newRow[OCCUPANCY_STATUS])) return;`) immediately bypasses overlap conflict detection, freeing the vehicle unit for other reservations.

## Key Components

- `src/main/resources/db/migration/V17__create_booking_schema.sql:4-29` — Creates `detour_booking` with `status IN ('ACTIVE', 'CANCELED')`, `active_trip_id` generated column, and foreign keys.
- `src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql:1-19` — Defines `detour_trip` table without a `status` column.
- `src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql:97-114` — Defines `rental_unit_occupancy` with `occupancy_status IN ('ACTIVE', 'RELEASED')` and overlap trigger.
- `src/main/java/app/detour/booking/BookingRecord.java:6-21` — Java record representing a row in `detour_booking`.
- `src/main/java/app/detour/booking/BookingResponse.java:8-24` — API response representation for a booking, including `status` and `canceledAt`.
- `src/main/java/app/detour/booking/BookingRepository.java:10-45` — Interface for inventory locking and booking persistence.
- `src/main/java/app/detour/booking/JdbcBookingRepository.java:23-345` — JDBC implementation of locking, decrements, insertion, and booking query operations.
- `src/main/java/app/detour/booking/BookingTransactionExecutor.java:31-198` — Coordinates transactional reservation, optimistic concurrency check, inventory decrements, and snapshot generation.
- `src/main/java/app/detour/booking/BookingService.java:32-134` — Service layer managing booking creation and query retrieval.
- `src/main/java/app/detour/trip/Trip.java:7-10` — Domain model representing an owned trip aggregate.
- `src/main/java/app/detour/trip/TripResponse.java:7-27` — Outgoing DTO for trip details.
- `src/main/java/app/detour/trip/TripProfileSummary.java:7-23` — Profile summary projection for trip lists.
- `src/main/java/app/detour/trip/TripRequests.java:14-261` — Request DTOs and JSON parser validators for trip mutations and booking operations.
- `src/main/java/app/detour/trip/TripRepository.java:9-84` — Trip persistence interface with version advance, aggregate insertion, and query methods.
- `src/main/java/app/detour/trip/JdbcTripRepository.java:23-720` — JDBC implementation for trips, draft selections, planned snapshots, and versioning.
- `src/main/java/app/detour/trip/TripService.java:55-1024` — Core service orchestrating trip lifecycle, expiration checks, draft mutations, and deletions.
- `src/main/java/app/detour/trip/TripController.java:26-283` — REST controller handling `/api/trips` endpoints.
- `src/main/java/app/detour/catalog/persistence/RentalUnitOccupancyOverlapTrigger.java:8-60` — H2 database trigger enforcing non-overlapping active occupancy on rental cars.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **Inventory Restoration (`Cancel Booking`)** | Decrement operations exist (`JdbcBookingRepository.java:76-89`), but no increment methods exist for `flight_instance.available_seats` or `accommodation_nightly_inventory.available_inventory`. No release update method exists for `rental_unit_occupancy` (`occupancy_status = 'RELEASED'`). |
| **Booking Cancellation State** | `detour_booking` supports `status = 'CANCELED'` and `canceled_at` in schema (`V17__create_booking_schema.sql:10-14`), but no repository or service method transitions an existing `ACTIVE` booking to `CANCELED`. |
| **Trip Lifecycle (`TripStatus`)** | `detour_trip` table has no `status` column (`V12__create_owned_trip_and_initial_draft_schema.sql:1-19`). `Trip.java:7-10` and `TripResponse.java:7-27` have no `status` field. `JdbcTripRepository.java:56-87` does not query or map a trip status. |
| **Canceled Trip Mutation Protection** | All mutation methods in `TripService` (`replaceSharedDetails:173`, `createDraft:250`, `duplicateDraft:258`, `promoteDraft:270`, `duplicateAlternative:315`, `deleteAlternative:332`, `deleteDraft:302`, selection methods: `790-1010`) execute without verifying if the trip is canceled. |
| **Alternative Deletion Safeguards** | `TripService.deleteAlternative` (`TripService.java:340-345`) deletes planned alternatives without verifying if an active booking exists for the planned itinerary. |
| **Trip Deletion vs Booking History** | `TripService.deleteTrip` (`TripService.java:356-358`) calls `trips.hasBookingHistory(trip.id())` and blocks deletion with HTTP 409 `CANNOT_DELETE_BOOKED_TRIP`. |
| **Trip Expiration Boundary** | Expiration calculation is centralized in `TripService.isExpired(startDate)` (`TripService.java:107-110`) using `America/Los_Angeles` (`ClockConfiguration.PDX_ZONE`). It is enforced on booking creation, but no cancel endpoints exist yet to enforce it on cancellation. |
| **REST Endpoints** | Endpoints `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` and `POST /api/trips/{tripId}/cancel` are not declared in `TripController.java`. |

## Existing Tests and Fixtures

- **`src/test/java/app/detour/booking/BookingApiIntegrationTest.java` (649 lines):**
  - Verifies booking of flight, stay, and rental car components and inventory decrements ([lines 59-131](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/booking/BookingApiIntegrationTest.java#L59-L131)).
  - Verifies idempotency replay behavior ([lines 134-201](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/booking/BookingApiIntegrationTest.java#L134-L201)).
  - Verifies `TRIP_EXPIRED` rejection at departure midnight in `America/Los_Angeles` ([lines 203-225](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/booking/BookingApiIntegrationTest.java#L203-L225)).
  - Verifies `ALREADY_BOOKED` rejection on second active booking attempt ([lines 227-263](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/booking/BookingApiIntegrationTest.java#L227-L263)).
  - Verifies complete rollback on inventory failure across components ([lines 305-422](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/booking/BookingApiIntegrationTest.java#L305-L422)).
  - Verifies multi-user isolation on booking operations ([lines 462-493](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/booking/BookingApiIntegrationTest.java#L462-L493)).
  - Verifies `CANNOT_DELETE_BOOKED_TRIP` rejection on trip deletion with booking history ([lines 544-549](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/booking/BookingApiIntegrationTest.java#L544-L549)).
- **`src/test/java/app/detour/booking/BookingConcurrencyIntegrationTest.java` (365 lines):**
  - Verifies thread concurrency contention across flight seats, stay inventory, and rental occupancy.
- **`src/test/java/app/detour/booking/BookingSchemaIntegrationTest.java` (147 lines):**
  - Verifies single active booking constraint and coexistence of multiple canceled bookings ([lines 52-71](file:///c:/code/loomspan-travel-demo/src/test/java/app/detour/booking/BookingSchemaIntegrationTest.java#L52-L71)).
- **`src/test/java/app/detour/trip/TripApiIntegrationTest.java` (1376 lines):**
  - Covers full CRUD lifecycle of trips, draft versioning, planned snapshots, and deletion.
- **`src/test/java/app/detour/catalog/RentalUnitOccupancyConstraintIntegrationTest.java` (158 lines):**
  - Covers rental unit occupancy intervals and active vs released status trigger execution.

## Dependencies and Operational Constraints

- **Database Migrations:** Flyway manages schema evolution. The latest applied migration is `V17__create_booking_schema.sql`. Any database modifications (such as adding a `status` column to `detour_trip`) must be sequenced in `V18__*.sql`.
- **Concurrency & Deadlock Prevention:** Inventory locking uses pessimistic `SELECT ... FOR UPDATE` row locks in consistent key order (`flight_instance` sorted by ID, `accommodation_nightly_inventory` sorted by unit ID and night date, `rental_unit` locked by ID). Inventory restoration during cancellation must follow lock and update ordering compatible with reservations to avoid deadlocks.
- **Optimistic Versioning:** `detour_trip.version` is incremented on every structural state change using `advanceVersion(tripId, ownerUserId, expectedVersion)`. Both booking cancellation and trip cancellation must check `expectedVersion` and advance `version`.
- **Timezone Anchor:** The application anchors expiration calculations to `ClockConfiguration.PDX_ZONE` (`ZoneId.of("America/Los_Angeles")`). Start of departure day (`startDate.atStartOfDay(PDX_ZONE).toInstant()`) marks the expiration boundary.
- **Cross-User Data Isolation:** All trip and booking operations enforce that queries filter by `owner_user_id` and return HTTP 404 `RESOURCE_NOT_FOUND` when unowned IDs are requested, avoiding information leakage.

## Historical Context

- **P06-T01 (`84be1fc`):** Established `detour_booking` and frozen component snapshot tables (`V17`), implemented `BookingTransactionExecutor` for atomic reservations across all three catalog components, and added the unique constraint on active bookings.
- **P06-T02 (`e1ef66d`):** Delivered the booking review and confirmation frontend workflows, integrated active booking inspection (`GET /api/trips/{tripId}/bookings/active`), and integrated booking history into profile summaries (`bookedCount`, `hasBookingHistory`, `primaryBookingReference`).
- **Phase 6 Scope Partition:**
  - `P06-T03` owns backend cancellation execution, atomic inventory restoration, trip and booking status transitions, alternative deletion guards, mutation guards on canceled trips, and API contracts.
  - `P06-T04` owns frontend user experience: confirmation modals (`CancelBookingModal`, `CancelTripModal`), post-cancellation triage dialog, read-only UI enforcement on canceled trips, and profile badge display.

## Open Questions

1. **`TripResponse` Payload Structure for Cancellation:** The ticket requires that `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` returns an updated `TripResponse` "showing booking marked CANCELED, Trip remaining active, and inventory restored." Currently, `TripResponse` does not contain a booking or status field. Planning must determine how `TripResponse` represents the canceled booking state (e.g. adding `status` to `TripResponse`, or including booking details) while maintaining backward compatibility with existing frontend consumers.
2. **Foreign Key Cascade on Planned Itinerary Deletion:** In `V17__create_booking_schema.sql:23`, `fk_booking_planned_itinerary` specifies `ON DELETE CASCADE`. If a traveler cancels a booking and subsequently deletes that Planned alternative (as an unbooked planned alternative), the cascade would delete the historical `detour_booking` row, destroying immutable booking history. Planning should address whether `planned_itinerary_id` foreign key should be altered or whether deletion of planned alternatives that have any booking history should be restricted.
3. **Trip Cancellation on Trips Without Booking History:** The ticket specifies that Cancel Trip is allowed when the trip has booking history (active or canceled). Planning should formalize the exact error response (e.g., HTTP 400 or HTTP 409 `NO_BOOKING_HISTORY`) when `POST /api/trips/{tripId}/cancel` is invoked on a trip with zero booking history.

---

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-09-22-p06-t03-transactional-cancellation-and-lifecycle-policies.md
SUMMARY: Researched the existing backend booking, inventory reservation, and trip lifecycle architecture. Identified the exact components for inventory restoration, missing `status` column on `detour_trip`, missing active booking checks on alternative deletion, and current deletion policy enforcement. All 166 backend tests and 88 frontend tests pass cleanly on main at commit c84ee48.
DECISIONS:
  - none
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: 2_create_plan
