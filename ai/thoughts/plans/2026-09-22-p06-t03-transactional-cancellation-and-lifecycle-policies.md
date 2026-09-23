# P06-T03 Transactional Cancellation Engine and Lifecycle Policies Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-22-p06-t03-transactional-cancellation-and-lifecycle-policies.md`
- Research: `ai/thoughts/research/2026-09-22-p06-t03-transactional-cancellation-and-lifecycle-policies.md`
- Outcome: Deliver atomic, fee-free cancellation of active bookings with exact inventory restoration (flight seats, room nights, rental vehicle occupancy), distinguish temporary booking cancellation from permanent trip cancellation, enforce immutable history for past/expired travel, prevent deletion of trips with booking history, and safeguard booked alternatives against premature deletion.

## Current State
- **Booking Creation & Inventory Decrement:** `BookingTransactionExecutor.executeBookingTransaction` locks flight instances, stay nightly inventory, and rental units in deterministic order, decrements seats/inventory, inserts `rental_unit_occupancy` with `occupancy_status = 'ACTIVE'`, inserts `detour_booking` with `status = 'ACTIVE'`, copies component snapshots, and increments `detour_trip.version` ([`BookingTransactionExecutor.java:48-193`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/booking/BookingTransactionExecutor.java#L48-L193)).
- **Database Schema Constraints:** `detour_booking` has `ck_detour_booking_status CHECK (status IN ('ACTIVE', 'CANCELED'))` and generated column `active_trip_id` with unique constraint `uq_detour_booking_active_trip` ([`V17__create_booking_schema.sql:19-27`](file:///c:/code/loomspan-travel-demo/src/main/resources/db/migration/V17__create_booking_schema.sql#L19-L27)). Foreign key `fk_booking_planned_itinerary` has `ON DELETE CASCADE` ([`V17__create_booking_schema.sql:23`](file:///c:/code/loomspan-travel-demo/src/main/resources/db/migration/V17__create_booking_schema.sql#L23)).
- **Missing Trip Status:** `detour_trip` table ([`V12__create_owned_trip_and_initial_draft_schema.sql:1-19`](file:///c:/code/loomspan-travel-demo/src/main/resources/db/migration/V12__create_owned_trip_and_initial_draft_schema.sql#L1-L19)) and domain record `Trip` ([`Trip.java:7-10`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/Trip.java#L7-L10)) contain no `status` column or field; trips are implicitly active without a persistent `CANCELED` state.
- **Unprotected Mutations:** All mutation methods in `TripService` (`replaceSharedDetails`, `createDraft`, `duplicateDraft`, `promoteDraft`, `deleteDraft`, `duplicateAlternative`, `deleteAlternative`, selection methods) execute without checking if a trip is canceled.
- **Unprotected Alternative Deletion:** `TripService.deleteAlternative` does not verify if a Planned alternative is actively booked before deletion ([`TripService.java:332-347`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripService.java#L332-L347)).
- **Trip Deletion vs Booking History:** `TripService.deleteTrip` already calls `trips.hasBookingHistory(trip.id())` and blocks deletion with HTTP 409 `CANNOT_DELETE_BOOKED_TRIP` ([`TripService.java:356-358`](file:///c:/code/loomspan-travel-demo/src/main/java/app/detour/trip/TripService.java#L356-L358)).
- **No Cancellation Endpoints:** Endpoints `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` and `POST /api/trips/{tripId}/cancel` do not exist.

## Desired End State
- `POST /api/trips/{tripId}/bookings/{bookingId}/cancel`:
  - Validates `expectedVersion`, trip ownership, and expiration (`!isExpired(startDate)`).
  - Inside a single transaction, locks booking row `FOR UPDATE`, checks `status = 'ACTIVE'`, restores flight seats, restores room nights, sets rental occupancy to `'RELEASED'`, updates booking to `status = 'CANCELED'` and `canceled_at = CURRENT_TIMESTAMP`, advances trip version, and keeps trip `status = 'ACTIVE'`.
  - Returns HTTP 200 with updated `TripResponse` containing trip `status: "ACTIVE"` and booking `status: "CANCELED"`.
- `POST /api/trips/{tripId}/cancel`:
  - Validates `expectedVersion`, trip ownership, expiration (`!isExpired(startDate)`), and booking history presence (`hasBookingHistory`). Trips with no booking history reject with HTTP 400 `NO_BOOKING_HISTORY`.
  - If trip has an `ACTIVE` booking, atomically releases all finite inventory, marks booking `CANCELED`, and updates trip `status = 'CANCELED'`. If restoration fails, entire transaction rolls back.
  - If trip has only canceled bookings, updates trip `status = 'CANCELED'` with zero inventory adjustments.
  - Returns HTTP 200 with updated `TripResponse` containing trip `status: "CANCELED"`.
- **Canceled Trip Safeguards:** All mutations (draft creations, draft updates, promotions, component selections, trip shared details updates, deletions) on a `CANCELED` trip are rejected with HTTP 409 `TRIP_CANCELED`. Trip duplication via `duplicateTrip` remains permitted.
- **Alternative Deletion Safeguards:** Attempting to delete a Planned alternative with an `ACTIVE` booking is rejected with HTTP 409 `CANNOT_DELETE_ACTIVE_BOOKED_ALTERNATIVE`. Foreign key on `detour_booking.planned_itinerary_id` is changed to `ON DELETE SET NULL` so deleting an unbooked or formerly booked planned itinerary preserves immutable booking history.
- **Immutable Past & Expired Travel:** Expiration is evaluated at departure midnight in `America/Los_Angeles` (`ClockConfiguration.PDX_ZONE`). Attempts to cancel expired trips or bookings return HTTP 400 `TRIP_EXPIRED`.

## Scope

### In scope
- Flyway database migration `V18__add_trip_status_and_cancellation_constraints.sql` adding `status` to `detour_trip` and altering `fk_booking_planned_itinerary` to `ON DELETE SET NULL`.
- Domain and DTO updates: `Trip.java`, `TripResponse.java`, `TripProfileSummary.java`, `BookingRecord.java`, and `TripRequests.Cancel`.
- Repository methods in `BookingRepository`/`JdbcBookingRepository` for flight seat increment, nightly stay inventory increment, rental occupancy release, booking status update, and active booking check by planned itinerary ID.
- Repository methods in `TripRepository`/`JdbcTripRepository` for trip status mapping, cancellation update, and version advancement.
- Transactional orchestration in `BookingTransactionExecutor` for fee-free booking cancellation and trip cancellation with atomic rollback.
- Guardrails in `TripService` for canceled trip mutation blocking (`requireActiveTrip`) and actively booked alternative deletion blocking.
- REST endpoints in `TripController`: `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` and `POST /api/trips/{tripId}/cancel`.
- Frontend API client types and method signatures in `frontend/src/api/tripsApi.ts`.
- Comprehensive integration tests covering all cancellation scenarios, rollback, concurrency, and multi-user isolation.

### Out of scope
- Frontend cancellation modals, dialogs, and triage UI (deferred to `P06-T04`).
- Cancellation fees or supplier penalties (system is fee-free).
- Flight rescheduling, route modifications, or date exchanges.
- Version 2 Events infrastructure.

## Active Project Guardrails
- `ai/thoughts/design-lens.md`: None recorded.

## Impact and Risk Analysis
- **Concurrency & Deadlock Prevention:** Concurrent booking and cancellation operations could deadlock if resources are acquired in opposing orders. The implementation strictly mirrors booking reservation locking order: flight instances sorted by ID ascending, stay inventory sorted by accommodation unit ID and night date ascending, and rental unit occupancy released by ID.
- **Phantom Inventory / Capacity Leakage:** Double-cancellation is prevented by pessimistic locking on the booking row and optimistic version checking on `detour_trip.version`. Once a booking is marked `CANCELED`, subsequent cancellations fail with HTTP 409 (`BOOKING_NOT_ACTIVE` or `VERSION_CONFLICT`).
- **Data Integrity on Planned Itinerary Deletion:** In `V17`, `fk_booking_planned_itinerary` had `ON DELETE CASCADE`. If a traveler canceled a booking and deleted the planned alternative, H2 would cascade-delete the booking history. Migration `V18` alters this constraint to `ON DELETE SET NULL` (and drops `NOT NULL` on `planned_itinerary_id`), guaranteeing immutable booking persistence even when alternatives are pruned.
- **Timezone Anchor:** Expiration logic strictly evaluates against `ClockConfiguration.PDX_ZONE` (`America/Los_Angeles`) at departure date midnight (`startDate.atStartOfDay(PDX_ZONE).toInstant()`).

## Implementation Approach
1. **Migration Layer:** `V18__add_trip_status_and_cancellation_constraints.sql` adds `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` to `detour_trip` with check constraint `CHECK (status IN ('ACTIVE', 'CANCELED'))`. It alters `detour_booking.planned_itinerary_id` to nullable and redefines `fk_booking_planned_itinerary` as `ON DELETE SET NULL`.
2. **Domain & DTO Layer:** Add `status` to `Trip` and `TripResponse`, maintaining backward-compatible constructors for existing tests and consumers. Add `booking` (or `BookingResponse`) to `TripResponse` so cancellation responses include the updated booking snapshot alongside trip status. Add `TripRequests.Cancel(long expectedVersion)`.
3. **Repository Layer:** Add `incrementFlightSeats`, `incrementStayInventory`, `releaseRentalOccupancy`, `updateBookingStatus`, and `isPlannedItineraryActivelyBooked` to `BookingRepository` and `JdbcBookingRepository`. Add `cancelTrip(tripId, ownerUserId, expectedVersion)` to `TripRepository` and `JdbcTripRepository`.
4. **Service & Transaction Layer:** Implement `executeCancelBookingTransaction` and `executeCancelTripTransaction` on `BookingTransactionExecutor` (annotated `@Transactional`). Implement `requireActiveTrip` on `TripService` to intercept all draft, alternative, selection, and shared details mutations when a trip is canceled. Check active booking before deleting planned alternatives.
5. **Controller Layer:** Map `POST /api/trips/{tripId}/bookings/{bookingId}/cancel` and `POST /api/trips/{tripId}/cancel` in `TripController`.

---

## Phase 1: Database Migration and Schema Evolution

### Changes
- [x] `src/main/resources/db/migration/V18__add_trip_status_and_cancellation_constraints.sql` — Add `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` and `ck_detour_trip_status CHECK (status IN ('ACTIVE', 'CANCELED'))` to `detour_trip`. Alter `detour_booking.planned_itinerary_id` to drop `NOT NULL`. Drop constraint `fk_booking_planned_itinerary` and re-add with `FOREIGN KEY (planned_itinerary_id) REFERENCES detour_planned_itinerary(id) ON DELETE SET NULL`.
- [x] `src/test/java/app/detour/booking/BookingSchemaIntegrationTest.java` — Add assertions verifying that `detour_trip` status defaults to `ACTIVE`, rejects invalid status strings, and deleting a planned itinerary with a canceled booking sets `planned_itinerary_id = NULL` without deleting the booking row.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=BookingSchemaIntegrationTest` — Schema migration applies cleanly and constraints behave as expected.

### Optional developer checks
- [ ] None.

---

## Phase 2: Domain Records, DTOs, and Request Parsers

### Changes
- [x] `src/main/java/app/detour/trip/Trip.java` — Add `String status` to canonical constructor/record definition: `Trip(long id, UUID publicId, long ownerUserId, Destination destination, LocalDate startDate, LocalDate endDate, int travelerCount, List<Integer> travelerAges, Long budgetCents, String label, String status, long version, List<TripDraft> drafts, List<PlannedItinerary> planned)`.
- [x] `src/main/java/app/detour/trip/TripResponse.java` — Add `String status` and `BookingResponse booking` to `TripResponse`. Provide overloaded constructors preserving default `status = "ACTIVE"` and `booking = null` for all existing callers.
- [x] `src/main/java/app/detour/trip/TripProfileSummary.java` — Add `String status` to record components and provide overloaded constructor if needed.
- [x] `src/main/java/app/detour/booking/BookingRecord.java` — Change `plannedItineraryId` from `long` to `Long` (nullable) to accommodate `ON DELETE SET NULL`.
- [x] `src/main/java/app/detour/trip/TripRequests.java` — Add `public record Cancel(long expectedVersion) { }` and static parser `cancel(JsonNode body)` that validates `expectedVersion` is a non-negative integer.
- [x] `frontend/src/api/tripsApi.ts` — Update `TripResponse` and `TripProfileSummary` TypeScript interfaces with `status?: string;` and `booking?: BookingResponse | null;`. Add client methods `cancelBooking: (tripId: string, bookingId: string, payload: { expectedVersion: number }) => Promise<TripResponse>` and `cancelTrip: (tripId: string, payload: { expectedVersion: number }) => Promise<TripResponse>`.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — Java compilation succeeds across all domain records and DTOs.
- [x] `npm.cmd test -- --run` in `frontend/` — Frontend test suite compiles and passes with updated TypeScript types.

### Optional developer checks
- [ ] None.

---

## Phase 3: Repository Enhancements

### Changes
- [x] `src/main/java/app/detour/booking/BookingRepository.java` — Declare:
  - `boolean incrementFlightSeats(long flightInstanceId, int seatsToIncrement);`
  - `boolean incrementStayInventory(long accommodationUnitId, LocalDate date, int unitsToIncrement);`
  - `void releaseRentalOccupancy(long rentalOccupancyId);`
  - `void updateBookingStatus(long bookingId, String status, OffsetDateTime canceledAt);`
  - `Optional<BookingRecord> findActiveBookingRecordByTripIdForUpdate(long tripId);`
  - `Optional<BookingRecord> findBookingRecordByTripIdAndPublicIdForUpdate(long tripId, UUID bookingPublicId);`
  - `Optional<BookingRecord> findPrimaryBookingRecordByTripId(long tripId);`
  - `boolean isPlannedItineraryActivelyBooked(long plannedItineraryId);`
- [x] `src/main/java/app/detour/booking/JdbcBookingRepository.java` — Implement all newly declared methods using `JdbcTemplate`:
  - `incrementFlightSeats`: `UPDATE flight_instance SET available_seats = available_seats + ? WHERE id = ?`.
  - `incrementStayInventory`: `UPDATE accommodation_nightly_inventory SET available_inventory = available_inventory + ? WHERE accommodation_unit_id = ? AND night_date = ?`.
  - `releaseRentalOccupancy`: `UPDATE rental_unit_occupancy SET occupancy_status = 'RELEASED' WHERE id = ?`.
  - `updateBookingStatus`: `UPDATE detour_booking SET status = ?, canceled_at = ? WHERE id = ?`.
  - `findBookingRecordByTripIdAndPublicIdForUpdate`: `SELECT ... FROM detour_booking WHERE trip_id = ? AND public_id = ? FOR UPDATE`.
  - `findActiveBookingRecordByTripIdForUpdate`: `SELECT ... FROM detour_booking WHERE trip_id = ? AND status = 'ACTIVE' FOR UPDATE`.
  - `findPrimaryBookingRecordByTripId`: `SELECT ... FROM detour_booking WHERE trip_id = ? ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, created_at DESC LIMIT 1`.
  - `isPlannedItineraryActivelyBooked`: `SELECT COUNT(*) FROM detour_booking WHERE planned_itinerary_id = ? AND status = 'ACTIVE'`.
  - Update `mapBookingRecord` to handle nullable `planned_itinerary_id`.
- [x] `src/main/java/app/detour/trip/TripRepository.java` — Declare:
  - `boolean cancelTrip(long tripId, long ownerUserId, long expectedVersion);`
- [x] `src/main/java/app/detour/trip/JdbcTripRepository.java` — Implement:
  - Include `trip.status` in `findByPublicIdAndOwnerUserId` and `findAllByOwnerUserId` SELECT queries.
  - Map `row.getString("status")` in `loadTrip`.
  - In `cancelTrip`: execute `UPDATE detour_trip SET status = 'CANCELED', version = version + 1 WHERE id = ? AND owner_user_id = ? AND version = ?`.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=BookingRepository*Test` or focused slice test — Repository methods read, write, and lock correctly.

### Optional developer checks
- [ ] None.

---

## Phase 4: Service Orchestration, Cancellation Engine, and Lifecycle Guardrails

### Changes
- [x] `src/main/java/app/detour/booking/BookingTransactionExecutor.java` — Implement:
  - `TripResponse executeCancelBookingTransaction(long ownerUserId, Trip trip, UUID bookingPublicId, TripRequests.Cancel request)`:
    1. Check `!clock.instant().isBefore(departureMidnight)` -> throw 400 `TRIP_EXPIRED`.
    2. Check `trip.version() != request.expectedVersion()` -> throw 409 `VERSION_CONFLICT`.
    3. Check `"CANCELED".equals(trip.status())` -> throw 409 `TRIP_CANCELED`.
    4. Lock booking row: `bookingRepository.findBookingRecordByTripIdAndPublicIdForUpdate(trip.id(), bookingPublicId)`. If missing, throw 404 `RESOURCE_NOT_FOUND`.
    5. Check `!"ACTIVE".equals(record.status())` -> throw 409 `BOOKING_NOT_ACTIVE`.
    6. Restore inventory:
       - Flights: read airfare snapshot, sort flight IDs ASC, lock flight instances, increment seats by `trip.travelerCount()`.
       - Stays: read stay and night snapshots, lock stay nights, increment inventory by `unitCount`.
       - Rental car: if `rentalOccupancyId != null`, release rental occupancy (`occupancy_status = 'RELEASED'`).
    7. Update booking: `updateBookingStatus(record.id(), "CANCELED", now)`.
    8. Advance trip version: `tripRepository.advanceVersion(trip.id(), ownerUserId, request.expectedVersion())`.
    9. Construct and return updated `TripResponse` showing trip `ACTIVE` and booking `CANCELED`.
  - `TripResponse executeCancelTripTransaction(long ownerUserId, Trip trip, TripRequests.Cancel request)`:
    1. Check `!clock.instant().isBefore(departureMidnight)` -> throw 400 `TRIP_EXPIRED`.
    2. Check `"CANCELED".equals(trip.status())` -> throw 409 `TRIP_ALREADY_CANCELED`.
    3. Check `trip.version() != request.expectedVersion()` -> throw 409 `VERSION_CONFLICT`.
    4. Check `!bookingRepository.hasBookingHistory(trip.id())` -> throw 400 `NO_BOOKING_HISTORY` ("Trips without booking history cannot be canceled. Use Delete Trip instead.").
    5. Lock active booking if present: `findActiveBookingRecordByTripIdForUpdate(trip.id())`.
       - If present: restore airfare, stay, and rental inventory (exact same logic as Cancel Booking), and mark booking `CANCELED`.
       - If no active booking: do nothing to inventory.
    6. Atomically update trip status to `CANCELED` and advance version: `tripRepository.cancelTrip(trip.id(), ownerUserId, request.expectedVersion())`.
    7. Return updated `TripResponse` showing trip `CANCELED`.
  - In `executeBookingTransaction`:
    - Add check: if `"CANCELED".equals(trip.status())` -> throw 409 `TRIP_CANCELED`.
- [x] `src/main/java/app/detour/booking/BookingService.java` — Expose:
  - `TripResponse cancelBooking(long ownerUserId, String tripPublicId, String bookingPublicId, TripRequests.Cancel request)`
  - `TripResponse cancelTrip(long ownerUserId, String tripPublicId, TripRequests.Cancel request)`
  - Helper to assemble `TripResponse` with primary booking info.
- [x] `src/main/java/app/detour/trip/TripService.java` — Guardrails:
  - Add helper `requireActiveTrip(Trip trip)`: if `"CANCELED".equals(trip.status())` throw `ApiException(409, "TRIP_CANCELED", "This trip has been canceled and cannot be modified.")`.
  - Apply `requireActiveTrip(trip)` to:
    - `replaceSharedDetails`
    - `createDraft`
    - `duplicateDraft`
    - `deleteDraft`
    - `promoteDraft`
    - `duplicateAlternative`
    - `deleteAlternative`
    - `selectAirfare`, `removeAirfare`
    - `selectStay`, `removeStay`
    - `selectRental`, `removeRental`
  - In `deleteAlternative`:
    - If `source instanceof PlannedItinerary planned`:
      Check `bookingRepository.isPlannedItineraryActivelyBooked(planned.id())`. If true, throw `ApiException(409, "CANNOT_DELETE_ACTIVE_BOOKED_ALTERNATIVE", "Cannot delete a planned alternative that is actively booked.")`.
  - In `deleteTrip`:
    - Verify `hasBookingHistory(trip.id())` check and error message.
  - In `toResponse(Trip trip)`:
    - Populate `status: trip.status()` and `booking: primaryBookingResponse(trip)`.
  - In `tripsProfile`:
    - Populate `status: trip.status()` in `TripProfileSummary`.

### Automated verification
- [x] `.\mvnw.cmd test-compile` — All service methods and guardrail checks compile cleanly.

### Optional developer checks
- [ ] None.

---

## Phase 5: REST Endpoints and Controller Integration

### Changes
- [x] `src/main/java/app/detour/trip/TripController.java` — Add endpoints:
  - `@PostMapping("/{tripId}/bookings/{bookingId}/cancel`:
    ```java
    @PostMapping("/{tripId}/bookings/{bookingId}/cancel")
    TripResponse cancelBooking(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @PathVariable String bookingId,
            @RequestBody JsonNode request) {
        return bookingService.cancelBooking(
                requirePrincipal(principal).userId(),
                tripId,
                bookingId,
                TripRequests.cancel(request)
        );
    }
    ```
  - `@PostMapping("/{tripId}/cancel`:
    ```java
    @PostMapping("/{tripId}/cancel")
    TripResponse cancelTrip(
            @AuthenticationPrincipal DetourUserPrincipal principal,
            @PathVariable String tripId,
            @RequestBody JsonNode request) {
        return bookingService.cancelTrip(
                requirePrincipal(principal).userId(),
                tripId,
                TripRequests.cancel(request)
        );
    }
    ```

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=TripApiIntegrationTest` — Existing trip controller endpoints pass without regression.

### Optional developer checks
- [ ] None.

---

## Phase 6: Integration Testing & Concurrency Verification

### Changes
- [x] `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java` (NEW):
  - `cancelingActiveBookingRestoresInventoryAndAdvancesTripVersion` — Verifies flight seats, room nights, and rental occupancy restored, booking marked `CANCELED`, `detour_trip.version` advanced, and trip remains `ACTIVE`.
  - `rebookingPossibleAfterBookingCancellation` — Verifies new booking can be created after active booking is canceled.
  - `cancelingBookingOnExpiredTripRejectedWithHttp400` — Fast-forwards clock past departure midnight; verifies HTTP 400 `TRIP_EXPIRED`.
  - `cancelingAlreadyCanceledBookingRejectedWithHttp409` — Re-cancellation rejected with HTTP 409 `BOOKING_NOT_ACTIVE`.
  - `cancelingTripWithActiveBookingAtomicallyRestoresInventoryAndSetsTripCanceled` — Verifies inventory restored, booking `CANCELED`, and trip status `CANCELED`.
  - `cancelingTripWithOnlyCanceledBookingsSetsTripCanceledWithoutInventoryAdjustment` — Verifies trip cancels cleanly without touching inventory.
  - `cancelingTripWithoutBookingHistoryRejectedWithHttp400` — Trip with 0 bookings rejected with HTTP 400 `NO_BOOKING_HISTORY`.
  - `cancelingExpiredTripRejectedWithHttp400` — Trip past departure midnight rejected with HTTP 400 `TRIP_EXPIRED`.
  - `allMutationsOnCanceledTripRejectedWithHttp409` — Verifies `replaceSharedDetails`, `createDraft`, `duplicateDraft`, `deleteDraft`, `promoteDraft`, `deleteAlternative`, selection updates, and re-booking all throw HTTP 409 `TRIP_CANCELED`.
  - `duplicateTripAllowedOnCanceledTrip` — Verifies `duplicateTrip` succeeds on `CANCELED` trip, creating a new `ACTIVE` trip.
  - `deletingActivelyBookedPlannedAlternativeRejectedWithHttp409` — Verifies HTTP 409 `CANNOT_DELETE_ACTIVE_BOOKED_ALTERNATIVE`.
  - `deletingUnbookedPlannedAlternativeSucceedsAndPreservesHistoricalCanceledBookings` — Verifies `fk_booking_planned_itinerary` sets null on delete, preserving booking record and `hasBookingHistory`.
  - `deletingTripWithBookingHistoryPermanentlyRejectedWithHttp409` — Verifies `CANNOT_DELETE_BOOKED_TRIP`.
  - `multiUserIsolationPreventsCancelingOtherUsersBookingsOrTrips` — Verifies HTTP 404 when user B accesses user A's booking or trip.
- [x] `src/test/java/app/detour/booking/BookingConcurrencyIntegrationTest.java`:
  - `concurrentCancellationAndRebookingMaintainsExactInventoryConsistency` — Multi-threaded test verifying no phantom inventory or capacity leakage.
  - `concurrentDuplicateCancellationRequestsSucceedsExactlyOnce` — Verifies double-cancel race condition is handled gracefully without double-restoring inventory.

### Automated verification
- [x] `.\mvnw.cmd test -Dtest=BookingCancellationIntegrationTest` — All cancellation integration tests pass.
- [x] `.\mvnw.cmd test -Dtest=BookingConcurrencyIntegrationTest` — Concurrency tests pass without deadlock or capacity leaks.
- [x] `.\mvnw.cmd test` — Full backend test suite passes cleanly.
- [x] `npm.cmd test -- --run` in `frontend/` — Full frontend test suite passes cleanly.

### Optional developer checks
- [ ] None.

---

## Test Strategy
- **Unit / Schema Level:** Validate H2 schema migration `V18` constraints (`ck_detour_trip_status`, `ON DELETE SET NULL` on `detour_booking.planned_itinerary_id`).
- **Integration Level (`BookingCancellationIntegrationTest`):** Comprehensive end-to-end integration tests using Spring Boot `MockMvc` asserting exact inventory counts in `flight_instance`, `accommodation_nightly_inventory`, and `rental_unit_occupancy` before booking, after booking, and after cancellation.
- **Boundary & Guardrail Tests:** Verify `isExpired` boundary at departure midnight in `America/Los_Angeles` (`ClockConfiguration.PDX_ZONE`), trip mutation guardrails on `CANCELED` trips, active alternative deletion guardrails, and trip deletion prevention on trips with booking history.
- **Concurrency Level (`BookingConcurrencyIntegrationTest`):** Multi-threaded execution testing race conditions between simultaneous cancellations, and cancellation concurrent with re-booking.
- **Multi-Tenant Security:** Cross-account isolation ensuring unauthorized users receive HTTP 404 `RESOURCE_NOT_FOUND`.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Canceling an active booking restores flight seats, nightly room inventory, and updates rental car occupancy to `RELEASED` in a single atomic transaction. | `BookingTransactionExecutor.executeCancelBookingTransaction`, `BookingRepository.incrementFlightSeats`, `incrementStayInventory`, `releaseRentalOccupancy` | `BookingCancellationIntegrationTest.cancelingActiveBookingRestoresInventoryAndAdvancesTripVersion` |
| Following Cancel Booking, the booking record is retained with `status = 'CANCELED'` and `canceled_at` timestamp, and the trip remains active. | `BookingTransactionExecutor.executeCancelBookingTransaction`, `JdbcBookingRepository.updateBookingStatus` | `BookingCancellationIntegrationTest.cancelingActiveBookingRestoresInventoryAndAdvancesTripVersion` |
| Attempting to cancel a booking on an Expired trip (evaluated at start of departure date in `America/Los_Angeles`) is rejected with HTTP 400. | `BookingTransactionExecutor.executeCancelBookingTransaction` checking `clock.instant().isBefore(departureMidnight)` | `BookingCancellationIntegrationTest.cancelingBookingOnExpiredTripRejectedWithHttp400` |
| `hasBookingHistory` returns true whenever any active or canceled booking exists for the trip, permanently preventing `DELETE /api/trips/{tripId}` with HTTP 409. | `JdbcTripRepository.hasBookingHistory`, `TripService.deleteTrip` | `BookingCancellationIntegrationTest.deletingTripWithBookingHistoryPermanentlyRejectedWithHttp409` |
| Canceling a trip with an active booking atomically releases all inventory and sets the trip status to `CANCELED`. | `BookingTransactionExecutor.executeCancelTripTransaction`, `JdbcTripRepository.cancelTrip` | `BookingCancellationIntegrationTest.cancelingTripWithActiveBookingAtomicallyRestoresInventoryAndSetsTripCanceled` |
| On a `CANCELED` trip, all mutations (creating/editing/deleting drafts, promotions) are rejected with HTTP 409. | `TripService.requireActiveTrip` called in all mutation methods | `BookingCancellationIntegrationTest.allMutationsOnCanceledTripRejectedWithHttp409` |
| Attempting to delete a Planned alternative that is actively booked is rejected with HTTP 409. | `TripService.deleteAlternative` checking `bookingRepository.isPlannedItineraryActivelyBooked` | `BookingCancellationIntegrationTest.deletingActivelyBookedPlannedAlternativeRejectedWithHttp409` |
| Concurrent cancellation and rebooking requests are concurrency-safe and cannot create phantom inventory or leak capacity. | Pessimistic locking on booking rows, deterministic inventory lock ordering, optimistic version check on `detour_trip.version` | `BookingConcurrencyIntegrationTest.concurrentCancellationAndRebookingMaintainsExactInventoryConsistency` |
| Multi-user isolation tests verify users cannot cancel bookings or trips belonging to other accounts. | `TripRepository.findByPublicIdAndOwnerUserId` enforcing `owner_user_id` match | `BookingCancellationIntegrationTest.multiUserIsolationPreventsCancelingOtherUsersBookingsOrTrips` |

## Risks and Rollback/Recovery
- **Deadlock Risk on Inventory Locking:** Cancellation locks inventory before updating. Lock ordering strictly matches `BookingTransactionExecutor` (flight instances sorted by ID ascending, stay inventory sorted by accommodation unit ID and night date ascending, rental occupancy updated directly by ID).
- **Rollback Consistency:** Any unexpected failure during cancellation (e.g. database error, constraint violation) triggers Spring `@Transactional` rollback, ensuring inventory and booking status never enter a partially restored or inconsistent state.
- **Rollback of Changes:** If this feature needs to be rolled back, code changes can be reverted and Flyway migration `V18` can be removed in a pre-production environment or reversed with a forward migration.

## References
- Ticket: `ai/thoughts/tickets/2026-09-22-p06-t03-transactional-cancellation-and-lifecycle-policies.md`
- Research: `ai/thoughts/research/2026-09-22-p06-t03-transactional-cancellation-and-lifecycle-policies.md`
- Booking schema: `src/main/resources/db/migration/V17__create_booking_schema.sql`
- Booking transaction executor: `src/main/java/app/detour/booking/BookingTransactionExecutor.java`
- Trip service: `src/main/java/app/detour/trip/TripService.java`
- Trip controller: `src/main/java/app/detour/trip/TripController.java`
