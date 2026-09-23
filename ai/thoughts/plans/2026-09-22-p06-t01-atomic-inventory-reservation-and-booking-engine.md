# Atomic Inventory Reservation and Booking Record Engine Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-22-p06-t01-atomic-inventory-reservation-and-booking-engine.md`
- Research: `ai/thoughts/research/2026-09-22-p06-t01-atomic-inventory-reservation-and-booking-engine.md`
- Outcome: An authenticated traveler can confirm a simulated booking for any valid Planned itinerary, resulting in atomic reservation of finite catalog inventory (flight seats, stay unit-nights, and rental car intervals), safe handling of concurrent attempts and retries via an idempotency key, generation of realistic fictional confirmation references (`DT-`, `FL-`, `HT-`, `RC-`), persistent booking snapshot records, and profile/deletion policy enforcement.

## Current State
- `detour_trip` aggregates hold Draft alternatives (`detour_trip_draft`) and Planned alternatives (`detour_planned_itinerary`).
- Planned alternatives copy and freeze catalog attributes and prices into `detour_planned_airfare_snapshot`, `detour_planned_stay_snapshot`, `detour_planned_stay_night_snapshot`, and `detour_planned_rental_snapshot` (`src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql` and `V16__enhance_planned_snapshot_schema.sql`).
- Finite catalog inventory is tracked in `flight_instance.available_seats`, `accommodation_nightly_inventory.available_inventory`, and `rental_unit_occupancy` with `RentalUnitOccupancyOverlapTrigger` enforcing half-open `[pickup_at, return_at)` intervals (`src/main/resources/db/migration/V4__create_catalog_inventory_schema.sql`).
- `detour_booking` does not exist; migrations end at `V16`.
- `TripRepository.hasBookingHistory(long tripId)` returns hardcoded `false` (`src/main/java/app/detour/trip/JdbcTripRepository.java:255`), which is currently spied and mocked in `TripApiIntegrationTest:1295`.
- `TripService.tripsProfile` passes hardcoded `0` for `bookedCount` (`src/main/java/app/detour/trip/TripService.java:156`).
- Trip controllers expose no booking creation or retrieval endpoints (`src/main/java/app/detour/trip/TripController.java`).

## Desired End State
- Database migration `V17__create_booking_schema.sql` establishes `detour_booking` and frozen component snapshot tables (`detour_booking_airfare_snapshot`, `detour_booking_stay_snapshot`, `detour_booking_stay_night_snapshot`, `detour_booking_rental_snapshot`) along with a compatibility view `detour_booking_item_snapshot`.
- At most one `ACTIVE` booking per trip is strictly enforced at the database level using a generated column `active_trip_id` with a `UNIQUE` constraint, fully compatible with H2 2.4.240.
- Trip-scoped idempotency is enforced by unique constraint `uq_detour_booking_trip_idempotency` on `(trip_id, idempotency_key)`.
- Booking a Planned itinerary executes in a single database transaction with deterministic locking order:
  1. Airfare flight instances ordered by `id ASC FOR UPDATE`.
  2. Accommodation nightly inventory rows ordered by `(accommodation_unit_id ASC, night_date ASC) FOR UPDATE`.
  3. Rental unit occupancy interval non-overlap validation under `rental_unit FOR UPDATE`.
- Inventory decrements:
  - `flight_instance.available_seats` decremented by `traveler_count`.
  - `accommodation_nightly_inventory.available_inventory` decremented by `unit_count` for each night.
  - `rental_unit_occupancy` inserted with `occupancy_status = 'ACTIVE'`.
- If any component has insufficient inventory, the transaction rolls back completely (zero components reserved) and returns HTTP 409 Conflict with structured, itemized conflict details in `fields`.
- Duplicate requests with the same `(tripId, idempotencyKey)` return the existing `BookingResponse` with HTTP 200/201 without duplicate inventory decrements.
- Attempting to book an expired trip (`isExpired(startDate)` in `America/Los_Angeles`) fails with HTTP 400.
- Attempting to book when an active booking already exists fails with HTTP 409 `ALREADY_BOOKED`.
- Attempting to book with a stale version fails with HTTP 409 `VERSION_CONFLICT`.
- Successful booking generates realistic fictional confirmation references:
  - Master booking: `DT-` + 6 uppercase alphanumeric characters.
  - Airfare: `FL-` + 6 uppercase alphanumeric characters (null if omitted).
  - Stay: `HT-` + 6 uppercase alphanumeric characters (null if omitted).
  - Rental car: `RC-` + 6 uppercase alphanumeric characters (null if omitted).
- Booking records and component snapshots survive application restarts without relying on live catalog joins.
- `TripRepository.hasBookingHistory(long tripId)` checks `detour_booking`, blocking `DELETE /api/trips/{tripId}` with HTTP 409 `CANNOT_DELETE_BOOKED_TRIP`.
- `TripProfileSummary.bookedCount` reflects `1` when an active booking exists, `0` otherwise.
- REST endpoints are fully functional:
  - `POST /api/trips/{tripId}/bookings`
  - `GET /api/trips/{tripId}/bookings/active`
  - `GET /api/trips/{tripId}/bookings`

## Scope

### In scope
- Flyway migration `V17__create_booking_schema.sql` creating `detour_booking`, snapshot tables, indices, and constraints.
- Generated column and unique constraint for single active booking in H2.
- Deterministic locking order and conditional inventory updates preventing deadlocks and overselling.
- Fictional reference generation engine (`DT-`, `FL-`, `HT-`, `RC-`).
- Idempotency key handling via request body or `Idempotency-Key` header.
- Itemized conflict reporting on inventory exhaustion (HTTP 409).
- Integration into `TripService.tripsProfile` (`bookedCount`) and `JdbcTripRepository.hasBookingHistory`.
- `POST /api/trips/{tripId}/bookings`, `GET /api/trips/{tripId}/bookings/active`, `GET /api/trips/{tripId}/bookings` endpoints.
- Integration, concurrency, and application restart tests.

### Out of scope
- Real payment processing, credit card details, billing addresses.
- Cancellation and inventory restoration (owned by P06-T03 and P06-T04).
- Frontend UI interactive confirmation wiring in `BookingReviewView.tsx` (owned by P06-T02).
- Airline GDS, CRS, or live supplier integrations.
- Version 2 Events.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
- **H2 Partial Index Limitation:** H2 does not support `CREATE UNIQUE INDEX ... WHERE status = 'ACTIVE'`.
  *Mitigation:* Use generated column `active_trip_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN trip_id ELSE NULL END)` with a `UNIQUE` constraint. When `status = 'CANCELED'`, `active_trip_id` evaluates to `NULL`, permitting multiple canceled records while allowing at most one `ACTIVE` record per trip.
- **Deadlock Risk under High Concurrency:** Concurrent bookings across trips could deadlock if rows are locked in arbitrary order.
  *Mitigation:* Strict deterministic locking order: flight instances sorted by `id ASC`, stay nights sorted by `(accommodation_unit_id ASC, night_date ASC)`, followed by `rental_unit` lock.
- **Race Condition on Final Inventory:** Multiple threads competing for the last seat, room night, or car could result in negative inventory or duplicate bookings.
  *Mitigation:* Use pessimistic row locks (`SELECT ... FOR UPDATE`), atomic conditional updates (`WHERE available_seats >= ?`), and reliance on `RentalUnitOccupancyOverlapTrigger` which already locks `rental_unit FOR UPDATE`.
- **Idempotency Races:** Two concurrent requests with the identical idempotency key could race to create two bookings.
  *Mitigation:* Database unique constraint `uq_detour_booking_trip_idempotency` ensures exactly one insert succeeds; the competing insert catches the constraint violation and loads the already created booking record.
- **Historical Snapshot Integrity:** Historical bookings must never mutate or reconstruct state from live catalog tables that may change.
  *Mitigation:* All component descriptive details and prices are copied from `detour_planned_*_snapshot` into dedicated `detour_booking_*_snapshot` tables during booking creation.

## Implementation Approach
1. **Schema & Migration:** Add `V17__create_booking_schema.sql` defining `detour_booking` and component snapshot tables matching the existing snapshot patterns from `V14` and `V16`.
2. **Reference Generator:** Implement `BookingReferenceGenerator` with secure random generation for `DT-XXXXXX`, `FL-XXXXXX`, `HT-XXXXXX`, and `RC-XXXXXX`.
3. **Repository Layer:** Create `BookingRepository` and `JdbcBookingRepository` in package `app.detour.booking` handling locking, atomic updates, snapshot copying, and queries. Update `JdbcTripRepository.hasBookingHistory` to query `detour_booking`.
4. **Service Layer:** Implement `BookingService` in package `app.detour.booking` providing the transactional booking engine, pre-condition checks (ownership, expiration, version, reservable components), idempotency deduplication, deterministic locking, error mapping, and tally calculation.
5. **API & Controller Layer:** Add request parsing to `TripRequests.BookingCreate` and wire endpoints in `TripController` (or a dedicated `BookingController` under `/api/trips/{tripId}/bookings`). Update `TripService.tripsProfile` to supply live `bookedCount`.

---

## Phase 1: Database Schema Migration & Verification

### Changes
- [x] `src/main/resources/db/migration/V17__create_booking_schema.sql` —
  - Table `detour_booking`:
    - `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`
    - `public_id UUID NOT NULL UNIQUE`
    - `trip_id BIGINT NOT NULL REFERENCES detour_trip(id) ON DELETE CASCADE`
    - `planned_itinerary_id BIGINT NOT NULL REFERENCES detour_planned_itinerary(id)`
    - `booking_reference VARCHAR(20) NOT NULL UNIQUE`
    - `status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELED'))`
    - `grand_total_cents BIGINT NOT NULL CHECK (grand_total_cents >= 0)`
    - `idempotency_key VARCHAR(100) NOT NULL`
    - `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
    - `canceled_at TIMESTAMP WITH TIME ZONE`
    - `airfare_reference VARCHAR(20)`
    - `stay_reference VARCHAR(20)`
    - `rental_reference VARCHAR(20)`
    - `rental_occupancy_id BIGINT REFERENCES rental_unit_occupancy(id)`
    - `active_trip_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN trip_id ELSE NULL END)`
    - `CONSTRAINT uq_detour_booking_active_trip UNIQUE (active_trip_id)`
    - `CONSTRAINT uq_detour_booking_trip_idempotency UNIQUE (trip_id, idempotency_key)`
    - `INDEX idx_detour_booking_trip ON detour_booking (trip_id, created_at DESC)`
  - Table `detour_booking_airfare_snapshot`:
    - `booking_id BIGINT PRIMARY KEY REFERENCES detour_booking(id) ON DELETE CASCADE`
    - `outbound_flight_instance_id BIGINT NOT NULL REFERENCES flight_instance(id)`
    - `return_flight_instance_id BIGINT NOT NULL REFERENCES flight_instance(id)`
    - Descriptive, schedule, timezone, layover, carrier, and pricing columns matching `detour_planned_airfare_snapshot`
  - Table `detour_booking_stay_snapshot`:
    - `booking_id BIGINT PRIMARY KEY REFERENCES detour_booking(id) ON DELETE CASCADE`
    - `accommodation_unit_id BIGINT NOT NULL REFERENCES accommodation_unit(id)`
    - `unit_count INTEGER NOT NULL CHECK (unit_count > 0)`
    - Property name, unit name, category, description, capacity columns matching `detour_planned_stay_snapshot`
  - Table `detour_booking_stay_night_snapshot`:
    - `booking_id BIGINT NOT NULL`
    - `night_date DATE NOT NULL`
    - `base_price_cents`, `tax_cents`, `fee_cents`
    - `PRIMARY KEY (booking_id, night_date)`
    - `CONSTRAINT fk_booking_stay_night FOREIGN KEY (booking_id) REFERENCES detour_booking_stay_snapshot(booking_id) ON DELETE CASCADE`
  - Table `detour_booking_rental_snapshot`:
    - `booking_id BIGINT PRIMARY KEY REFERENCES detour_booking(id) ON DELETE CASCADE`
    - `rental_unit_id BIGINT NOT NULL REFERENCES rental_unit(id)`
    - `pickup_at`, `return_at`, location, vehicle class, identifier, pricing columns matching `detour_planned_rental_snapshot`
  - View `detour_booking_item_snapshot`:
    - Union view of booked component snapshots for standardized introspection.
- [x] `src/test/java/app/detour/booking/BookingSchemaIntegrationTest.java` —
  - Verify migration executes cleanly.
  - Verify check constraints (status, grand total, unit counts, non-negative money).
  - Verify `active_trip_id` unique constraint rejects multiple `ACTIVE` bookings on the same trip but permits multiple `CANCELED` bookings.
  - Verify `(trip_id, idempotency_key)` uniqueness.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=BookingSchemaIntegrationTest -DskipFrontend=true` — Migration and constraints verified.

### Optional developer checks
- None.

---

## Phase 2: Confirmation Reference Generator & DTO Models

### Changes
- [x] `src/main/java/app/detour/booking/BookingReferenceGenerator.java` —
  - `generateBookingReference()`: returns `DT-` + 6 uppercase chars `[A-Z0-9]`.
  - `generateAirfareReference()`: returns `FL-` + 6 uppercase chars `[A-Z0-9]`.
  - `generateStayReference()`: returns `HT-` + 6 uppercase chars `[A-Z0-9]`.
  - `generateRentalReference()`: returns `RC-` + 6 uppercase chars `[A-Z0-9]`.
- [x] `src/main/java/app/detour/booking/BookingResponse.java` —
  - Record containing `UUID id`, `UUID tripId`, `UUID plannedItineraryId`, `String bookingReference`, `String status`, `long grandTotalCents`, `String idempotencyKey`, `OffsetDateTime bookedAt`, `OffsetDateTime canceledAt`, `String airfareReference`, `String stayReference`, `String rentalReference`, `DraftSelectionResponse selections`, `ItineraryTallyResponse tally`.
- [x] `src/main/java/app/detour/booking/BookingRecord.java` —
  - Internal persistence record representing the row in `detour_booking`.
- [x] `src/test/java/app/detour/booking/BookingReferenceGeneratorTest.java` —
  - Unit tests verifying exact regex formats (`DT-[A-Z0-9]{6}`, `FL-[A-Z0-9]{6}`, `HT-[A-Z0-9]{6}`, `RC-[A-Z0-9]{6}`).
  - Verification that distinct calls produce unique references.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=BookingReferenceGeneratorTest -DskipFrontend=true` — Reference generator formats and randomness verified.

### Optional developer checks
- None.

---

## Phase 3: Booking Repository Layer & TripRepository Integration

### Changes
- [x] `src/main/java/app/detour/booking/BookingRepository.java` —
  - Interface defining inventory locking, inventory decrements, rental occupancy insertion, booking insertion, snapshot copying, and query methods.
- [x] `src/main/java/app/detour/booking/JdbcBookingRepository.java` —
  - Implementation using `JdbcTemplate`:
    - `lockFlightInstances(List<Long> ids)`: `SELECT id, available_seats FROM flight_instance WHERE id = ? FOR UPDATE` sorted ascending.
    - `lockStayNightlyInventory(long unitId, LocalDate start, LocalDate end)`: `SELECT ... FROM accommodation_nightly_inventory WHERE accommodation_unit_id = ? AND night_date >= ? AND night_date < ? ORDER BY accommodation_unit_id ASC, night_date ASC FOR UPDATE`.
    - `lockRentalUnit(long rentalUnitId)`: `SELECT id FROM rental_unit WHERE id = ? FOR UPDATE`.
    - `checkRentalOverlap(long rentalUnitId, OffsetDateTime pickup, OffsetDateTime returnAt)`: query active overlapping occupancies.
    - `decrementFlightSeats(long flightInstanceId, int seats)`: conditional `UPDATE ... WHERE id = ? AND available_seats >= ?`.
    - `decrementStayInventory(long unitId, LocalDate date, int units)`: conditional `UPDATE ... WHERE accommodation_unit_id = ? AND night_date = ? AND available_inventory >= ?`.
    - `insertRentalOccupancy(long rentalUnitId, OffsetDateTime pickup, OffsetDateTime returnAt)`: inserts active row into `rental_unit_occupancy`.
    - `insertBooking(BookingRecord record)`: inserts into `detour_booking`.
    - `copySnapshotsFromPlanned(long bookingId, long plannedItineraryId)`: copies rows from planned snapshot tables into booking snapshot tables.
    - `findActiveBookingByTripId(long tripId)`: loads active booking with component snapshots.
    - `findBookingsByTripId(long tripId)`: loads all bookings for a trip ordered by `created_at DESC`.
    - `findByTripIdAndIdempotencyKey(long tripId, String idempotencyKey)`: loads booking by idempotency key.
    - `hasActiveBooking(long tripId)`: checks if active booking exists.
- [x] `src/main/java/app/detour/trip/JdbcTripRepository.java` —
  - Update `hasBookingHistory(long tripId)`: replace `return false;` with query against `detour_booking` (`SELECT COUNT(*) FROM detour_booking WHERE trip_id = ?` > 0).
  - Add `activeBookingCount(long tripId)`: queries active bookings for the trip.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=TripApiIntegrationTest -DskipFrontend=true` — Existing trip tests pass with real `hasBookingHistory`.

### Optional developer checks
- None.

---

## Phase 4: Transactional Booking Engine & Service Logic

### Changes
- [x] `src/main/java/app/detour/booking/BookingService.java` —
  - `@Transactional` booking execution:
    1. Pre-booking validation:
       - Validate trip ownership via `TripRepository` (404 on mismatch).
       - Validate trip departure expiration via `ClockConfiguration.PDX_ZONE` midnight (`isExpired(startDate)` -> HTTP 400 `TRIP_EXPIRED`).
       - Validate `expectedVersion` against `trip.version()` (HTTP 409 `VERSION_CONFLICT`).
       - Resolve Planned itinerary by UUID on this trip (404 if missing).
       - Validate planned itinerary has at least one reservable component (HTTP 400 `NO_RESERVABLE_COMPONENTS`).
    2. Idempotency check:
       - Check if booking already exists for `(tripId, idempotencyKey)`. If found, return existing `BookingResponse` with HTTP 200/201 (no inventory mutation).
    3. Active booking check:
       - Check if trip already has an active booking. If found, reject with HTTP 409 `ALREADY_BOOKED`.
    4. Deterministic locking & inventory reservation:
       - Lock flight instances in ascending ID order (`FOR UPDATE`).
       - Lock accommodation nightly rows in `(unitId ASC, nightDate ASC)` order (`FOR UPDATE`).
       - Lock rental unit (`FOR UPDATE`).
       - Evaluate availability across all components. If any component is unavailable, collect all conflict messages into `Map<String, String>` and throw `ApiException(409, "INVENTORY_CONFLICT", "One or more selected components are unavailable.", conflicts)`. The transaction rolls back cleanly; no inventory is reserved.
       - If all available:
         - Decrement flight seats conditionally.
         - Decrement stay nightly inventory conditionally.
         - Insert `rental_unit_occupancy` with `occupancy_status = 'ACTIVE'`.
    5. References & Financial Tally:
       - Generate references (`DT-`, `FL-` if airfare present, `HT-` if stay present, `RC-` if rental present).
       - Compute tally using `ItineraryTallyEngine.tallyPlanned(...)`.
    6. Persistence & Versioning:
       - Insert `detour_booking` and copy frozen snapshots.
       - Increment trip version: `advanceVersion(tripId, ownerUserId, expectedVersion)`.
       - Return `BookingResponse` with HTTP 201.
    7. Concurrency conflict recovery:
       - Catch `DuplicateKeyException` on `(trip_id, idempotency_key)` to safely recover and return the winning concurrent booking.
       - Catch `DuplicateKeyException` on `active_trip_id` and map to HTTP 409 `ALREADY_BOOKED`.
  - Read methods:
    - `getActiveBooking(long ownerUserId, String tripPublicId)`: returns `BookingResponse` or throws 404 `RESOURCE_NOT_FOUND`.
    - `getBookingHistory(long ownerUserId, String tripPublicId)`: returns `List<BookingResponse>`.
- [x] `src/main/java/app/detour/trip/TripService.java` —
  - In `tripsProfile`, replace hardcoded `0` at line 156 with active booking count (`trips.activeBookingCount(trip.id())`).

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=TripPricingAndTallyIntegrationTest -DskipFrontend=true` — Tally engine compatibility verified.

### Optional developer checks
- None.

---

## Phase 5: REST API Endpoints & Request Parsing

### Changes
- [x] `src/main/java/app/detour/trip/TripRequests.java` —
  - Add record `BookingCreate(UUID plannedItineraryId, long expectedVersion, String idempotencyKey)`.
  - Add parser `booking(JsonNode body, String headerIdempotencyKey)` validating required fields and extracting idempotency key from JSON body or `Idempotency-Key` HTTP header.
- [x] `src/main/java/app/detour/trip/TripController.java` —
  - Inject `BookingService`.
  - Map `POST /{tripId}/bookings`: accepts `@RequestBody JsonNode request`, `@RequestHeader(value = "Idempotency-Key", required = false) String headerKey`, calls `bookingService.book(...)`, returns `ResponseEntity<BookingResponse>`.
  - Map `GET /{tripId}/bookings/active`: returns `BookingResponse`.
  - Map `GET /{tripId}/bookings`: returns `List<BookingResponse>`.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=TripApiIntegrationTest -DskipFrontend=true` — All trip API tests continue passing.

### Optional developer checks
- None.

---

## Phase 6: Integration, Concurrency, and Application Restart Verification

### Changes
- [x] `src/test/java/app/detour/booking/BookingApiIntegrationTest.java` —
  - Full happy path booking for all component combinations (airfare-only, stay-only, rental-only, all 3).
  - Verifies exact reference code formatting (`DT-`, `FL-`, `HT-`, `RC-`) and omitted references for unselected components.
  - Verifies inventory decrements in database (`flight_instance.available_seats`, `accommodation_nightly_inventory.available_inventory`, `rental_unit_occupancy`).
  - Verifies idempotency replay with same key returns identical booking and decrements inventory only once.
  - Verifies rejection on expired trip (HTTP 400).
  - Verifies rejection when already booked (HTTP 409 `ALREADY_BOOKED`).
  - Verifies rejection on version mismatch (HTTP 409 `VERSION_CONFLICT`).
  - Verifies rejection when planned itinerary has no components (HTTP 400).
  - Verifies rollback on inventory exhaustion (seats sold out, room night sold out, car occupied) returning HTTP 409 with itemized `fields`.
  - Verifies owner isolation (user B cannot view or book user A's trip -> 404).
  - Verifies `GET /api/trips/{tripId}/bookings/active` and `GET /api/trips/{tripId}/bookings`.
  - Verifies `TripProfileSummary` reflects `bookedCount: 1` and `hasBookingHistory: true`.
  - Verifies trip deletion is rejected with 409 `CANNOT_DELETE_BOOKED_TRIP` on booked trip.
- [x] `src/test/java/app/detour/booking/BookingConcurrencyIntegrationTest.java` —
  - Multi-threaded booking tests using `CyclicBarrier` and `ExecutorService`:
    - 2 threads competing for 1 remaining flight seat -> exactly 1 succeeds, 1 receives 409 conflict, available seats equals 0.
    - 2 threads competing for 1 remaining room night -> exactly 1 succeeds, 1 receives 409 conflict.
    - 2 threads competing for 1 remaining rental car interval -> exactly 1 succeeds, 1 receives 409 conflict.
    - Concurrent requests with identical idempotency key -> exactly 1 booking row created.
    - No deadlocks observed.
- [x] `src/test/java/app/detour/booking/BookingApplicationRestartIntegrationTest.java` —
  - File-backed H2 restart test: creates trip and booking, stops application context, starts new context, verifies booking records, references, and frozen snapshots are fully preserved without live catalog joins.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest="*Booking*" -DskipFrontend=true` — All booking unit, integration, concurrency, and restart tests pass.
- [x] `.\mvnw.cmd test -DskipFrontend=true` — Full application test suite passes.

### Optional developer checks
- None.

---

## Test Strategy
- **Unit Testing:** `BookingReferenceGeneratorTest` verifying prefix format, length, character set, and random entropy.
- **Schema & Constraint Testing:** `BookingSchemaIntegrationTest` verifying Flyway migration `V17`, foreign keys, money check constraints, single active booking generated column constraint, and idempotency uniqueness.
- **REST API Integration Testing:** `BookingApiIntegrationTest` testing end-to-end booking flow, status codes (201, 200, 400, 404, 409), error response shapes, header/body idempotency, reference generation, and user isolation.
- **Inventory & Transaction Rollback Testing:** Test cases deliberately depleting flight seats, nightly room inventory, or creating rental interval overlaps to verify complete transaction rollback (zero components reserved) and structured error detail reporting.
- **Multi-threaded Concurrency Testing:** `BookingConcurrencyIntegrationTest` executing simultaneous booking attempts on the final inventory unit to prove race-condition safety, absence of deadlocks, and idempotency deduplication under concurrency.
- **Persistence & Restart Testing:** `BookingApplicationRestartIntegrationTest` using file-backed H2 to prove booking records and frozen snapshots survive application shutdown and restart.

---

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Schema migration `V17__create_booking_schema.sql` cleanly establishes `detour_booking` and snapshot structures with required constraints and indices. | `V17__create_booking_schema.sql` with `detour_booking`, snapshot tables, generated column `active_trip_id`, unique indices, check constraints. | `BookingSchemaIntegrationTest` verifying table creation, constraints, foreign keys, and H2 generated column uniqueness. |
| Attempting to book a valid Planned itinerary decrements flight `available_seats`, accommodation `available_inventory`, and inserts active `rental_unit_occupancy` in a single atomic transaction. | `BookingService.bookItinerary` & `JdbcBookingRepository` conditional updates and insert in a single `@Transactional` method. | `BookingApiIntegrationTest.booksItineraryAndDecrementsAllComponents` asserting exact inventory counts before and after booking. |
| If any component has insufficient inventory, the transaction rolls back completely, reserving zero components, and returns HTTP 409 with itemized conflict reasons. | `BookingService.validateAndReserveInventory` collecting all conflicts, throwing `ApiException(409, "INVENTORY_CONFLICT", ..., conflicts)` causing transaction rollback. | `BookingApiIntegrationTest.rollsBackCompletelyWhenInventoryExhausted` asserting zero inventory decremented and verifying 409 response fields. |
| Re-sending a booking request with the same `idempotencyKey` returns the existing booking record without deducting inventory again. | `BookingService.bookItinerary` querying `findByTripIdAndIdempotencyKey` before reservation, returning cached `BookingResponse`. | `BookingApiIntegrationTest.idempotentReplayReturnsExistingBookingWithoutDoubleDecrement` verifying identical response and unchanged inventory. |
| Attempting to book an itinerary on an Expired trip (evaluated at start of departure date in `America/Los_Angeles`) is rejected. | `BookingService` checking `TripService.isExpired(startDate)` and throwing HTTP 400 `TRIP_EXPIRED`. | `BookingApiIntegrationTest.rejectsBookingOnExpiredTrip` with controlled test clock. |
| Attempting to book a trip that already has an active booking is rejected with HTTP 409. | `BookingService` checking `hasActiveBooking` and database constraint `uq_detour_booking_active_trip`, throwing HTTP 409 `ALREADY_BOOKED`. | `BookingApiIntegrationTest.rejectsSecondActiveBookingOnTrip` verifying 409 `ALREADY_BOOKED`. |
| Booking produces realistic fictional reference numbers (`DT-XXXXXX`, `FL-XXXXXX`, `HT-XXXXXX`, `RC-XXXXXX`) only for components included in the booking. | `BookingReferenceGenerator` formatting prefixes and random alphanumeric characters, storing null for omitted components. | `BookingReferenceGeneratorTest` and `BookingApiIntegrationTest.generatesComponentReferencesOnlyForPresentComponents`. |
| Concurrent booking attempts against the final available seat, room night, or car unit result in exactly one successful booking and clean conflict rejection for the other, without negative inventory or deadlocks. | Deterministic locking order (`id ASC FOR UPDATE`), conditional updates (`WHERE available_seats >= ?`), and `RentalUnitOccupancyOverlapTrigger`. | `BookingConcurrencyIntegrationTest` executing concurrent threads via `CyclicBarrier` against final available unit. |
| Booking records and component snapshots survive an application restart and are strictly isolated to the authenticated trip owner. | Frozen snapshot tables populated on booking; queries strictly scoped by `ownerUserId` and `tripId`. | `BookingApplicationRestartIntegrationTest` restarting Spring context on file-backed database; `BookingApiIntegrationTest.enforcesTripOwnerIsolation`. |

---

## Risks and Rollback/Recovery
- **Migration Failure Risk:** If `V17__create_booking_schema.sql` has syntax errors on fresh or existing databases, the Flyway migration will fail on startup.
  *Recovery:* Automated test `BookingSchemaIntegrationTest` tests migration on an empty and seeded database before committing.
- **Rollback Procedure:** If a migration issue occurs in production, rollback entails reverting migration `V17` and dropping `detour_booking` and associated snapshot tables.
- **Lock Contention Risk:** Under high concurrency on the same flight or accommodation, database locks could cause thread pool starvation if held too long.
  *Recovery:* Transaction boundaries are tightly scoped strictly to the booking execution; no external network calls or expensive computations occur within the transaction.

## References
- Ticket: `ai/thoughts/tickets/2026-09-22-p06-t01-atomic-inventory-reservation-and-booking-engine.md`
- Research: `ai/thoughts/research/2026-09-22-p06-t01-atomic-inventory-reservation-and-booking-engine.md`
- Related Tickets: `ai/thoughts/tickets/2026-09-22-p06-t02-booking-confirmation-and-active-itinerary-experience.md`, `ai/thoughts/tickets/2026-09-22-p06-t03-transactional-cancellation-and-lifecycle-policies.md`
- Migration Baseline: `src/main/resources/db/migration/V14__create_draft_selection_and_planned_snapshot_schema.sql`, `V16__enhance_planned_snapshot_schema.sql`
- Core Domain Classes: `src/main/java/app/detour/trip/TripService.java`, `src/main/java/app/detour/trip/JdbcTripRepository.java`, `src/main/java/app/detour/trip/ItineraryTallyEngine.java`
- Concurrency Reference: `src/main/java/app/detour/catalog/persistence/RentalUnitOccupancyOverlapTrigger.java`
