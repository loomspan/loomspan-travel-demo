# P06-T03 Transactional Cancellation Engine and Lifecycle Policies Testing Plan

## Change Summary
Deliver rigorous automated test coverage for fee-free booking cancellation, trip cancellation, and lifecycle policy enforcement. Verifications prove atomic inventory restoration across flight instances, stay nightly inventory, and rental vehicle occupancy; immutable historical preservation of canceled booking snapshots; permanent prevention of trip deletion once booked; safeguards against deleting actively booked planned alternatives; rejection of mutations on canceled trips while permitting trip duplication; boundary enforcement at departure date midnight in `America/Los_Angeles`; multi-threaded concurrency safety; and strict multi-user data isolation.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| **Inventory Restoration** | Capacity leakage or phantom seats/rooms if restoration increments incorrectly or fails partially. | `BookingCancellationIntegrationTest.cancelingActiveBookingRestoresInventoryAndAdvancesTripVersion` asserts exact seat counts, nightly room inventory, and rental occupancy status before booking, after booking, and after cancellation. |
| **Atomic Rollback** | Inconsistent state where booking is canceled but inventory restoration fails, or vice versa. | `BookingCancellationIntegrationTest.cancelingTripWithActiveBookingAtomicallyRestoresInventoryAndSetsTripCanceled` verifies transactional all-or-nothing atomicity. |
| **Immutable History & Cascade Deletion** | Deletion of an unbooked or formerly booked planned alternative destroying historical `detour_booking` records via foreign key cascade. | `BookingCancellationIntegrationTest.deletingUnbookedPlannedAlternativeSucceedsAndPreservesHistoricalCanceledBookings` and `BookingSchemaIntegrationTest` verify `ON DELETE SET NULL` preserves booking rows and snapshots. |
| **Canceled Trip Mutations** | Stale or invalid trip modifications occurring after trip cancellation. | `BookingCancellationIntegrationTest.allMutationsOnCanceledTripRejectedWithHttp409` exercises every trip mutation endpoint on a canceled trip and asserts HTTP 409 `TRIP_CANCELED`. |
| **Alternative Deletion Safeguard** | Active bookings orphaned if an actively booked planned alternative is deleted. | `BookingCancellationIntegrationTest.deletingActivelyBookedPlannedAlternativeRejectedWithHttp409` asserts HTTP 409 `CANNOT_DELETE_ACTIVE_BOOKED_ALTERNATIVE`. |
| **Trip Deletion Safeguard** | Booked trips deleted permanently instead of canceled. | `BookingCancellationIntegrationTest.deletingTripWithBookingHistoryPermanentlyRejectedWithHttp409` asserts HTTP 409 `CANNOT_DELETE_BOOKED_TRIP`. |
| **Expiration Boundary** | Expired travel modified or past inventory restored due to timezone confusion. | `BookingCancellationIntegrationTest.cancelingBookingOnExpiredTripRejectedWithHttp400` and `cancelingExpiredTripRejectedWithHttp400` verify rejection at departure midnight in `ClockConfiguration.PDX_ZONE` (`America/Los_Angeles`). |
| **Concurrency & Double-Cancellation** | Phantom inventory created if concurrent cancellation requests restore inventory twice. | `BookingConcurrencyIntegrationTest.concurrentDuplicateCancellationRequestsSucceedsExactlyOnce` and `concurrentCancellationAndRebookingMaintainsExactInventoryConsistency` test thread race conditions with `CyclicBarrier`. |
| **Multi-User Isolation** | Cross-tenant cancellation or information disclosure. | `BookingCancellationIntegrationTest.multiUserIsolationPreventsCancelingOtherUsersBookingsOrTrips` verifies HTTP 404 `RESOURCE_NOT_FOUND` across accounts. |

## Existing Coverage and Environment Constraints
- **Test Infrastructure:** Spring Boot `@SpringBootTest` with `@AutoConfigureMockMvc`, in-memory H2 database per test class, and `TestClockConfiguration.TestClock` for deterministic time manipulation without real-time delays.
- **Existing Suites:**
  - `src/test/java/app/detour/booking/BookingApiIntegrationTest.java` (649 lines) — tests reservation, idempotency, expiration, and multi-user isolation on booking creation.
  - `src/test/java/app/detour/booking/BookingConcurrencyIntegrationTest.java` (365 lines) — tests high-concurrency contention for flight seats, stay inventory, and rental occupancy.
  - `src/test/java/app/detour/booking/BookingSchemaIntegrationTest.java` (147 lines) — tests active booking unique constraint and schema integrity.
  - `src/test/java/app/detour/trip/TripApiIntegrationTest.java` (1376 lines) — tests trip CRUD, drafts, versioning, and alternative lifecycles.
- **Constraints:**
  - Fast execution: in-memory H2 database with zero external service dependencies or network calls.
  - Timezone anchor: tests must configure or advance `TestClock` relative to `ClockConfiguration.PDX_ZONE` (`America/Los_Angeles`).

## Failing Test First
- **Name:** `cancelingActiveBookingRestoresInventoryAndAdvancesTripVersion`
- **Type:** Integration Test (`@SpringBootTest` with `MockMvc`)
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Arrange:**
  - Register a new client and create a trip with departure date in 2027.
  - Select airfare (2 travelers), stay (2 rooms for 4 nights), and rental vehicle.
  - Promote draft to planned itinerary and execute booking via `POST /api/trips/{tripId}/bookings`.
  - Record pre-cancellation inventory: flight available seats = capacity - 2, stay nightly available = capacity - 2, rental occupancy status = `'ACTIVE'`.
- **Act:**
  - Send `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` with payload `{ "expectedVersion": 1 }`.
- **Assert:**
  - Response status is HTTP 200 OK.
  - Response body `TripResponse` has `status = "ACTIVE"`, `version = 2`, and `booking.status = "CANCELED"`.
  - Database assertions:
    - `flight_instance.available_seats` equals original capacity.
    - `accommodation_nightly_inventory.available_inventory` equals original capacity across all 4 nights.
    - `rental_unit_occupancy.occupancy_status` equals `'RELEASED'`.
    - `detour_booking.status = 'CANCELED'` and `canceled_at` is non-null.
    - Snapshot tables (`detour_booking_airfare_snapshot`, `detour_booking_stay_snapshot`, `detour_booking_rental_snapshot`) remain completely intact.
- **Expected pre-fix failure:** Endpoint `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` returns HTTP 404 (Not Found) because the endpoint is not yet defined on `TripController`.

## Tests to Add or Update

### 1. `cancelingActiveBookingRestoresInventoryAndAdvancesTripVersion`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** Fee-free cancellation restores exact flight seats, stay nightly inventory, and rental occupancy in a single transaction; updates booking status to `CANCELED` with `canceled_at`; advances trip version; and preserves trip in `ACTIVE` status.
- **Inputs/fixture:** Trip with airfare (outbound + return), stay (2 units), and rental car booked.
- **Doubles or boundary isolation:** In-memory H2 with `TestClock`.
- **Edge cases:** Multi-day stay inventory increments for every reserved night.

### 2. `rebookingPossibleAfterBookingCancellation`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** After an active booking is canceled, the unique constraint on active bookings allows creating a new booking on the same trip with the newly restored inventory.
- **Inputs/fixture:** Trip booked, then booking canceled, then `POST /api/trips/{tripId}/bookings` executed with `expectedVersion = 2`.
- **Doubles or boundary isolation:** Standard Spring Boot MockMvc.
- **Edge cases:** Verifies inventory is decremented again cleanly without conflict.

### 3. `cancelingBookingOnExpiredTripRejectedWithHttp400`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** Booking cannot be canceled if the trip is expired (`isExpired(startDate)` in `America/Los_Angeles`).
- **Inputs/fixture:** Booked trip; `testClock` advanced to departure date midnight in `America/Los_Angeles`.
- **Doubles or boundary isolation:** `TestClockConfiguration.TestClock.setInstant(...)`.
- **Edge cases:** Exactly at departure midnight vs 1 second before.

### 4. `cancelingAlreadyCanceledBookingRejectedWithHttp409`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** Calling cancel on an already canceled booking is rejected with HTTP 409 `BOOKING_NOT_ACTIVE` and does not duplicate inventory increments.
- **Inputs/fixture:** Trip with canceled booking; second cancel request sent with matching or updated version.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Verifies available seats and nightly inventory do not exceed capacity.

### 5. `cancelingTripWithActiveBookingAtomicallyRestoresInventoryAndSetsTripCanceled`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** `POST /api/trips/{tripId}/cancel` on a trip with an active booking atomically restores all finite inventory, marks booking `CANCELED`, and transitions trip status to `CANCELED`.
- **Inputs/fixture:** Trip with active booking; `POST /api/trips/{tripId}/cancel` with `{ "expectedVersion": 1 }`.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Trip response shows `status: "CANCELED"` and `version: 2`.

### 6. `cancelingTripWithOnlyCanceledBookingsSetsTripCanceledWithoutInventoryAdjustment`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** `Cancel Trip` succeeds when a trip has only historical canceled bookings, setting trip status to `CANCELED` without touching catalog inventory.
- **Inputs/fixture:** Trip booked, booking canceled, then trip canceled.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Inventory counts checked to ensure zero secondary increment occurs.

### 7. `cancelingTripWithoutBookingHistoryRejectedWithHttp400`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** Unbooked trips cannot be canceled via `Cancel Trip`; requests reject with HTTP 400 `NO_BOOKING_HISTORY`, instructing the caller to use Delete Trip.
- **Inputs/fixture:** Trip created with drafts/planned alternatives but 0 bookings; `POST /api/trips/{tripId}/cancel` called.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Trip remains in `ACTIVE` status.

### 8. `cancelingExpiredTripRejectedWithHttp400`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** Trip cancellation is blocked once departure midnight in `America/Los_Angeles` has passed, returning HTTP 400 `TRIP_EXPIRED`.
- **Inputs/fixture:** Trip with booking; `testClock` set after departure date start.
- **Doubles or boundary isolation:** `TestClock`.
- **Edge cases:** Past travel remains immutable.

### 9. `cancelingAlreadyCanceledTripRejectedWithHttp409`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** Repeated trip cancellation requests on a `CANCELED` trip are rejected with HTTP 409 `TRIP_ALREADY_CANCELED`.
- **Inputs/fixture:** Canceled trip; second `POST /api/trips/{tripId}/cancel`.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Idempotent safety without double version increment.

### 10. `allMutationsOnCanceledTripRejectedWithHttp409`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** Once a trip is `CANCELED`, all mutations (`replaceSharedDetails`, `createDraft`, `duplicateDraft`, `deleteDraft`, `promoteDraft`, `deleteAlternative`, `selectAirfare`, `removeAirfare`, `selectStay`, `removeStay`, `selectRental`, `removeRental`, `createBooking`) are rejected with HTTP 409 `TRIP_CANCELED`.
- **Inputs/fixture:** Canceled trip aggregate.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Tests every mutation endpoint individually.

### 11. `duplicateTripAllowedOnCanceledTrip`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** `POST /api/trips/{tripId}/duplicate` is explicitly allowed on a `CANCELED` trip, producing a new trip aggregate in `ACTIVE` status with new IDs and version 0.
- **Inputs/fixture:** Canceled trip with planned alternatives; `POST /api/trips/{tripId}/duplicate`.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** The original trip remains `CANCELED`; new trip has `status = "ACTIVE"`.

### 12. `deletingActivelyBookedPlannedAlternativeRejectedWithHttp409`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** `DELETE /api/trips/{tripId}/alternatives/{alternativeId}` is rejected with HTTP 409 `CANNOT_DELETE_ACTIVE_BOOKED_ALTERNATIVE` while the planned alternative has an `ACTIVE` booking.
- **Inputs/fixture:** Trip with active booking; attempt to delete the planned itinerary referenced by the booking.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Alternative remains in planned list.

### 13. `deletingUnbookedPlannedAlternativeSucceedsAndPreservesHistoricalCanceledBookings`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** Deleting an unbooked planned alternative (or one whose booking was canceled) succeeds with zero inventory effect, sets `detour_booking.planned_itinerary_id = NULL`, and preserves the canceled booking row and all snapshots intact.
- **Inputs/fixture:** Trip booked, booking canceled, then planned alternative deleted with confirmation.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** `hasBookingHistory` continues to return `true`.

### 14. `deletingTripWithBookingHistoryPermanentlyRejectedWithHttp409`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** `DELETE /api/trips/{tripId}` is permanently blocked with HTTP 409 `CANNOT_DELETE_BOOKED_TRIP` on any trip that has booking history (active or canceled).
- **Inputs/fixture:** Trip with canceled booking; `DELETE /api/trips/{tripId}` with confirmation.
- **Doubles or boundary isolation:** Standard MockMvc.
- **Edge cases:** Verifies trip and booking records remain untouched in database.

### 15. `multiUserIsolationPreventsCancelingOtherUsersBookingsOrTrips`
- **Type:** Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`
- **Proves:** A user cannot cancel a booking or trip owned by another user; returns HTTP 404 `RESOURCE_NOT_FOUND`.
- **Inputs/fixture:** User A creates and books trip; User B attempts `POST /api/trips/{tripAId}/bookings/{bookingId}/cancel` and `POST /api/trips/{tripAId}/cancel`.
- **Doubles or boundary isolation:** Standard MockMvc with distinct user cookies/sessions.
- **Edge cases:** Zero state or inventory modification on User A's data.

### 16. `concurrentCancellationAndRebookingMaintainsExactInventoryConsistency`
- **Type:** Concurrency Test
- **Location:** `src/test/java/app/detour/booking/BookingConcurrencyIntegrationTest.java`
- **Proves:** Concurrent cancellation and rebooking requests are concurrency-safe; optimistic version checks prevent race conditions, and inventory is never over-allocated or leaked.
- **Inputs/fixture:** Multi-threaded executor with `CyclicBarrier` launching concurrent cancel and rebook operations on the same trip.
- **Doubles or boundary isolation:** Multi-threaded execution in Spring Boot Test.
- **Edge cases:** Final inventory equals initial capacity minus seats/rooms/vehicles allocated to any surviving active booking.

### 17. `concurrentDuplicateCancellationRequestsSucceedsExactlyOnce`
- **Type:** Concurrency Test
- **Location:** `src/test/java/app/detour/booking/BookingConcurrencyIntegrationTest.java`
- **Proves:** Simultaneous cancellation requests for the same booking result in exactly one HTTP 200 success and one HTTP 409 conflict, restoring inventory exactly once.
- **Inputs/fixture:** 2 threads racing `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` with the same `expectedVersion`.
- **Doubles or boundary isolation:** `CyclicBarrier` with 2 threads.
- **Edge cases:** Available seats and room nights increment by party size exactly once.

### 18. `schemaConstraintVerifiesDetourTripStatusDefaultAndAllowedValues`
- **Type:** Schema Integration Test
- **Location:** `src/test/java/app/detour/booking/BookingSchemaIntegrationTest.java`
- **Proves:** Flyway migration `V18` sets default status `'ACTIVE'`, enforces check constraint `ck_detour_trip_status`, and foreign key `fk_booking_planned_itinerary` allows `ON DELETE SET NULL`.
- **Inputs/fixture:** Direct JDBC queries on `INFORMATION_SCHEMA` and table inserts.
- **Doubles or boundary isolation:** In-memory H2.
- **Edge cases:** Attempting to insert status `'INVALID'` throws constraint violation exception.

## Safe Verification Commands
- Focused: `.\mvnw.cmd test -Dtest=BookingCancellationIntegrationTest`
- Related suite: `.\mvnw.cmd test -Dtest="Booking*Test"`
- Full safe suite: `.\mvnw.cmd test` and `npm.cmd test -- --run` (in `frontend/`)

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
