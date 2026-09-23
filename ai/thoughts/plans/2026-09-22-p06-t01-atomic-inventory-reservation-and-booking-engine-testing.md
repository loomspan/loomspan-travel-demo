# Atomic Inventory Reservation and Booking Record Engine Testing Plan

## Change Summary
Deliver verification coverage for the Phase 6 atomic booking engine: Flyway database schema migration `V17__create_booking_schema.sql` (`detour_booking` and frozen component snapshots), reference generator (`DT-`, `FL-`, `HT-`, `RC-`), transactional inventory reservation and deterministic locking, idempotency controls, pre-booking eligibility and constraints (ownership, expiration, versioning, single active booking), and trip profile/deletion integration.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Database Migration & Schema | Migration syntax failure in H2; broken unique constraint or check constraint on `detour_booking`. | `BookingSchemaIntegrationTest` verifying `V17` migration, foreign keys, money checks, and unique constraints. |
| Single Active Booking Invariant | H2 lacking partial indexes could permit multiple active bookings if constraint is misconfigured. | `BookingSchemaIntegrationTest` and `BookingApiIntegrationTest` verifying second active booking fails at service and database levels. |
| Inventory Atomicity & Deadlocks | Concurrent booking requests on shared resources deadlock or cause partial inventory allocation. | `BookingConcurrencyIntegrationTest` using `CyclicBarrier` and deterministic row locking to prove absence of deadlocks and all-or-nothing atomicity. |
| Race on Final Inventory Unit | Multiple concurrent requests competing for the last available seat, room night, or car could oversell. | `BookingConcurrencyIntegrationTest` proving exactly 1 thread succeeds and remaining threads receive 409 conflict, leaving inventory at 0 without negative numbers. |
| Idempotency Deduplication | Replayed requests deduct inventory twice or fail with unhandled 500 error instead of returning existing booking. | `BookingApiIntegrationTest` and `BookingConcurrencyIntegrationTest` verifying duplicate idempotency keys return identical response with 0 extra decrements. |
| Trip Expiration Boundary | Bookings confirmed for expired trips. | `BookingApiIntegrationTest` using `TestClock` anchored to `America/Los_Angeles` departure midnight. |
| Tenant Isolation & Security | Traveler B accessing, booking, or inspecting traveler A's trip. | `BookingApiIntegrationTest` testing cross-user 404 isolation across all booking endpoints. |
| Snapshot Durability | Historical booking records mutated or broken by subsequent catalog updates or server restarts. | `BookingApplicationRestartIntegrationTest` restarting Spring context on file-backed database and verifying immutable snapshots. |
| Trip Deletion Protection | Trip with booking history accidentally deleted. | `BookingApiIntegrationTest` confirming `DELETE /api/trips/{tripId}` fails with 409 `CANNOT_DELETE_BOOKED_TRIP` using real database state. |

## Existing Coverage and Environment Constraints
- Spring Boot 4.1.0, Spring JDBC (`JdbcTemplate`), H2 Database 2.4.240.
- Spring MVC integration tests use `@SpringBootTest`, `@AutoConfigureMockMvc`, and isolated in-memory H2 databases (`jdbc:h2:mem:...`).
- `TestClockConfiguration.TestClock` is imported and controlled in tests to verify temporal boundaries in `America/Los_Angeles`.
- Concurrency tests in `DraftReadinessAndPlannedSnapshotIntegrationTest` use `CyclicBarrier` and `ExecutorService` as the established pattern.
- Context restart tests in `TripApplicationRestartIntegrationTest` and `ApplicationRestartIntegrationTest` use file-backed H2 databases (`jdbc:h2:file:...`).
- Frontend build is skipped during Java test runs via `-DskipFrontend=true`.

## Failing Test First
- **Name:** `booksValidPlannedItineraryAndDecrementsAllComponents`
- **Type:** Spring Boot Integration Test (`@SpringBootTest`, `@AutoConfigureMockMvc`)
- **Location:** `src/test/java/app/detour/booking/BookingApiIntegrationTest.java`
- **Arrange:**
  1. Register traveler `Client owner = register("booking-red-test@example.test")`.
  2. Create trip for destination SFO with travel dates `2027-03-10` to `2027-03-14` and 2 adult travelers.
  3. Add airfare selection (outbound flight instance ID 1, return flight instance ID 2) and stay selection (accommodation unit ID 1, 1 unit).
  4. Promote draft to Planned itinerary (`POST /api/trips/{tripId}/drafts/{draftId}/plan`) with version 0.
  5. Record baseline `available_seats` for flight instances and `available_inventory` for stay nights.
- **Act:**
  - Submit `POST /api/trips/{tripId}/bookings` with payload:
    `{ "plannedItineraryId": "<plannedId>", "expectedVersion": 1, "idempotencyKey": "red-test-idempotency-key" }`
- **Assert:**
  - HTTP 201 Created.
  - JSON payload contains:
    - `id` (valid UUID)
    - `bookingReference` matching `^DT-[A-Z0-9]{6}$`
    - `status` equal to `"ACTIVE"`
    - `airfareReference` matching `^FL-[A-Z0-9]{6}$`
    - `stayReference` matching `^HT-[A-Z0-9]{6}$`
    - `rentalReference` is null
    - `grandTotalCents` matching authoritative sum of component subtotals
    - `selections` containing frozen component details
  - Database assertions:
    - Outbound and return `flight_instance.available_seats` decremented by 2.
    - Each night in `accommodation_nightly_inventory.available_inventory` decremented by 1.
    - `detour_booking` row exists with `status = 'ACTIVE'`.
- **Expected pre-fix failure:**
  - HTTP 404 Not Found (or 405 Method Not Allowed) because endpoint `POST /api/trips/{tripId}/bookings` and schema table `detour_booking` do not exist yet.

## Tests to Add or Update

### 1. `BookingSchemaIntegrationTest`
- **Type:** Schema Integration Test (`@SpringBootTest`)
- **Location:** `src/test/java/app/detour/booking/BookingSchemaIntegrationTest.java`
- **Proves:** `V17__create_booking_schema.sql` applies cleanly and enforces all constraints.
- **Inputs/fixture:** Raw `JdbcTemplate` against fresh Flyway migration.
- **Doubles or boundary isolation:** None (direct database integration).
- **Edge cases:**
  - `active_trip_id` generated column enforces at most 1 `ACTIVE` booking per `trip_id`.
  - Multiple `CANCELED` bookings for the same trip are permitted (generated column evaluates to `NULL`).
  - `(trip_id, idempotency_key)` unique constraint prevents duplicate idempotency keys per trip.
  - Check constraint `status IN ('ACTIVE', 'CANCELED')` rejects invalid status values.
  - Check constraint `grand_total_cents >= 0` rejects negative total.
  - Foreign key cascade: deleting `detour_trip` deletes `detour_booking` and component snapshots.

### 2. `BookingReferenceGeneratorTest`
- **Type:** Unit Test
- **Location:** `src/test/java/app/detour/booking/BookingReferenceGeneratorTest.java`
- **Proves:** References match required prefixes and character set without collision.
- **Inputs/fixture:** Direct method calls to `BookingReferenceGenerator`.
- **Doubles or boundary isolation:** Pure unit logic, no database.
- **Edge cases:**
  - Booking reference matches `^DT-[A-Z0-9]{6}$`.
  - Airfare reference matches `^FL-[A-Z0-9]{6}$`.
  - Stay reference matches `^HT-[A-Z0-9]{6}$`.
  - Rental reference matches `^RC-[A-Z0-9]{6}$`.
  - 10,000 generated references produce 10,000 unique values (entropy proof).

### 3. `BookingApiIntegrationTest`
- **Type:** Web MVC / Persistence Integration Test (`@SpringBootTest`, `@AutoConfigureMockMvc`)
- **Location:** `src/test/java/app/detour/booking/BookingApiIntegrationTest.java`
- **Proves:** All functional acceptance criteria of the booking engine.
- **Inputs/fixture:** Seeded March 2027 catalog and real trip/draft/planned aggregates.
- **Doubles or boundary isolation:** `TestClockConfiguration` for temporal expiration control.
- **Edge cases:**
  - **Full Itinerary Booking:** Books trip with all 3 components (airfare, stay, rental car); decrements flight seats, stay inventory, and creates `rental_unit_occupancy` with `occupancy_status = 'ACTIVE'`; populates `DT-`, `FL-`, `HT-`, `RC-` references.
  - **Partial Itinerary Booking:** Books itinerary with only airfare and stay; rental reference is `null`; rental occupancy is untouched.
  - **Idempotency Replay via Body:** Sending duplicate request with same `idempotencyKey` returns HTTP 200/201 and existing booking; inventory counts remain unchanged.
  - **Idempotency Replay via Header:** Sending `Idempotency-Key` in HTTP header works identically to JSON body.
  - **Trip Expiration Rejection:** Setting `testClock` to departure midnight in `America/Los_Angeles` causes booking attempt to fail with HTTP 400 `TRIP_EXPIRED`.
  - **Already Booked Rejection:** Attempting to book a second planned itinerary on a trip that already has an active booking fails with HTTP 409 `ALREADY_BOOKED`.
  - **Version Conflict Rejection:** Stale `expectedVersion` fails with HTTP 409 `VERSION_CONFLICT`.
  - **Empty Planned Itinerary Rejection:** Planned itinerary without reservable components fails with HTTP 400 `NO_RESERVABLE_COMPONENTS`.
  - **Flight Inventory Exhaustion Rollback:** When flight seats are insufficient, booking fails with HTTP 409 `INVENTORY_CONFLICT` and `fields.airfare`; stay inventory and rental occupancy remain uncommitted (zero changes).
  - **Stay Inventory Exhaustion Rollback:** When room nights are sold out, booking fails with HTTP 409 `INVENTORY_CONFLICT` and `fields.stay`; flight seats remain uncommitted.
  - **Rental Overlap Rollback:** When rental vehicle has overlapping active occupancy, booking fails with HTTP 409 `INVENTORY_CONFLICT` and `fields.rental`; flight seats and room nights remain uncommitted.
  - **Multiple Component Exhaustion:** All unavailable components are itemized in `fields` simultaneously.
  - **Owner Isolation:** Client B attempting `POST /api/trips/{tripAId}/bookings` or `GET /api/trips/{tripAId}/bookings/active` receives HTTP 404 `RESOURCE_NOT_FOUND`.
  - **Active Booking Retrieval:** `GET /api/trips/{tripId}/bookings/active` returns active booking when present; returns 404 when absent.
  - **Booking History Listing:** `GET /api/trips/{tripId}/bookings` returns all bookings ordered by `created_at DESC`.
  - **Trip Profile Projection:** `GET /api/trips` returns `bookedCount: 1` and `hasBookingHistory: true` on booked trip.
  - **Trip Deletion Block:** `DELETE /api/trips/{tripId}` fails with HTTP 409 `CANNOT_DELETE_BOOKED_TRIP` using real `detour_booking` data.

### 4. `BookingConcurrencyIntegrationTest`
- **Type:** Concurrency Integration Test (`@SpringBootTest`)
- **Location:** `src/test/java/app/detour/booking/BookingConcurrencyIntegrationTest.java`
- **Proves:** Multi-threaded race condition safety, absence of deadlocks, and exact inventory bounds under contention.
- **Inputs/fixture:** 2 or more concurrent threads coordinated with `CyclicBarrier`.
- **Doubles or boundary isolation:** None.
- **Edge cases:**
  - **Flight Seat Race:** 2 threads simultaneously attempt to book the final available seat on a flight -> exactly 1 succeeds (HTTP 201), exactly 1 fails with HTTP 409 `INVENTORY_CONFLICT`; `available_seats` ends at exactly 0.
  - **Stay Night Race:** 2 threads simultaneously attempt to book the last unit for a date interval -> exactly 1 succeeds, 1 fails with 409 conflict; `available_inventory` ends at exactly 0.
  - **Rental Car Race:** 2 threads simultaneously attempt to book the same vehicle for overlapping intervals -> exactly 1 succeeds, 1 fails with 409 conflict; exactly 1 active occupancy row exists.
  - **Concurrent Idempotency Race:** 2 threads simultaneously submit the same booking request with identical idempotency key -> exactly 1 booking row is created; both threads receive successful responses with identical booking reference.

### 5. `BookingApplicationRestartIntegrationTest`
- **Type:** Persistence & Restart Integration Test (`@SpringBootTest`)
- **Location:** `src/test/java/app/detour/booking/BookingApplicationRestartIntegrationTest.java`
- **Proves:** Bookings and frozen component snapshots survive application restart without live catalog joins.
- **Inputs/fixture:** File-backed H2 database instance (`jdbc:h2:file:...`).
- **Doubles or boundary isolation:** Context lifecycle management (`SpringApplication.run`, `context.close()`).
- **Edge cases:**
  - Create trip, draft, planned itinerary, and booking.
  - Shut down Spring `ApplicationContext`.
  - Start new Spring `ApplicationContext` pointing to same file database.
  - Query `GET /api/trips/{tripId}/bookings/active` and verify master booking reference, status, grand total, and component snapshots are 100% intact.

### 6. `TripApiIntegrationTest` Update
- **Type:** Regression Test Update
- **Location:** `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- **Proves:** Existing `tripDeletionBlocksWhenBookingHistoryPresent` test succeeds with real database backing (removing the need for Mockito spy stubbing of `hasBookingHistory`).

## Safe Verification Commands
- Focused: `.\mvnw.cmd test -Dtest=BookingApiIntegrationTest -DskipFrontend=true`
- Related suite: `.\mvnw.cmd test -Dtest="*Booking*" -DskipFrontend=true`
- Full safe suite: `.\mvnw.cmd test -DskipFrontend=true`

## Optional Developer Checks
- None.

## Exit Criteria
- [x] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated tests pass after implementation.
- [x] The broadest safe relevant repository test suite passes.
- [x] Acceptance criteria map to executable evidence.
- [x] Routine automated tests do not perform unintended live or destructive operations.
- [x] Material risks and edge cases identified above are covered.
- [x] Any optional check is reported as nonblocking and is not represented as already performed.
