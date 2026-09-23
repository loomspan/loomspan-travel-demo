# P06-T01 Code Review — Cycle 1

## Scope and Repository State

- **Ticket:** `ai/thoughts/tickets/2026-09-22-p06-t01-atomic-inventory-reservation-and-booking-engine.md`
- **Research:** `ai/thoughts/research/2026-09-22-p06-t01-atomic-inventory-reservation-and-booking-engine.md`
- **Implementation Plan:** `ai/thoughts/plans/2026-09-22-p06-t01-atomic-inventory-reservation-and-booking-engine.md`
- **Testing Plan:** `ai/thoughts/plans/2026-09-22-p06-t01-atomic-inventory-reservation-and-booking-engine-testing.md`
- **Comparison Base:** `main` (commit `be91bb69369b756af6b5ccb45024821dc8fdae92`)
- **Review Cycle:** 1
- **Working Tree Scope:**
  - Production code:
    - Schema migration `src/main/resources/db/migration/V17__create_booking_schema.sql`
    - Booking domain: `src/main/java/app/detour/booking/` (`BookingRecord`, `BookingReferenceGenerator`, `BookingRepository`, `JdbcBookingRepository`, `BookingResponse`, `BookingService`, `BookingTransactionExecutor`)
    - Trip domain integrations: `src/main/java/app/detour/trip/` (`TripController`, `TripService`, `TripRepository`, `JdbcTripRepository`, `TripRequests`, plus public record extractions in `AlternativeResponse`, `PlannedItinerary`, etc.)
  - Test suite:
    - `src/test/java/app/detour/booking/` (`BookingSchemaIntegrationTest`, `BookingReferenceGeneratorTest`, `BookingApiIntegrationTest`, `BookingConcurrencyIntegrationTest`, `BookingApplicationRestartIntegrationTest`)
    - Existing test updates: `DetourApplicationTest`, `PhaseOneCatalogForwardMigrationIntegrationTest`, `TripApiIntegrationTest`

## Findings

No actionable findings.

## Findings Resolved in This Context

None (no implementation changes made in this review cycle).

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Schema migration `V17__create_booking_schema.sql` cleanly establishes `detour_booking` and snapshot structures with required constraints and indices. | `V17__create_booking_schema.sql:4-137` defines table `detour_booking`, snapshots, compatibility view, checks, and unique active index. | `BookingSchemaIntegrationTest`, `DetourApplicationTest`, `PhaseOneCatalogForwardMigrationIntegrationTest` | implemented |
| Attempting to book a valid Planned itinerary decrements flight `available_seats`, accommodation `available_inventory`, and inserts active `rental_unit_occupancy` in a single atomic transaction. | `BookingTransactionExecutor.java:48-193`, `JdbcBookingRepository.java:76-106` | `BookingApiIntegrationTest.booksValidPlannedItineraryAndDecrementsAllComponents`, `booksAllThreeComponentsAndPopulatesAllReferences` | implemented |
| If any component has insufficient inventory, the transaction rolls back completely, reserving zero components, and returns HTTP 409 with itemized conflict reasons. | `BookingTransactionExecutor.java:79-123`, `ApiException(409, "INVENTORY_CONFLICT", ...)` | `BookingApiIntegrationTest.rollsBackCompletelyWhenAirfareInventoryExhausted`, `rollsBackCompletelyWhenStayInventoryExhausted`, `rollsBackCompletelyWhenRentalOccupancyOverlaps`, `reportsMultipleExhaustedComponentsSimultaneously` | implemented |
| Re-sending a booking request with the same `idempotencyKey` returns the existing booking record without deducting inventory again. | `BookingService.java:60-78`, `detour_booking` constraint `uq_detour_booking_trip_idempotency` | `BookingApiIntegrationTest.idempotentReplayReturnsExistingBookingWithoutDoubleDecrement`, `supportsIdempotencyKeyViaHttpHeader`, `BookingConcurrencyIntegrationTest.concurrentDuplicateIdempotencyRace` | implemented |
| Attempting to book an itinerary on an Expired trip (evaluated at start of departure date in `America/Los_Angeles`) is rejected. | `BookingTransactionExecutor.java:50-54` checking departure midnight in `ClockConfiguration.PDX_ZONE` | `BookingApiIntegrationTest.rejectsBookingOnExpiredTrip` | implemented |
| Attempting to book a trip that already has an active booking is rejected with HTTP 409. | `BookingTransactionExecutor.java:61-64`, `detour_booking` generated column `active_trip_id` and unique constraint `uq_detour_booking_active_trip` | `BookingApiIntegrationTest.rejectsSecondActiveBookingOnTrip`, `BookingSchemaIntegrationTest.allowsSingleActiveBookingPerTripAndMultipleCanceledBookings` | implemented |
| Booking produces realistic fictional reference numbers (`DT-XXXXXX`, `FL-XXXXXX`, `HT-XXXXXX`, `RC-XXXXXX`) only for components included in the booking. | `BookingReferenceGenerator.java:12-35`, `BookingTransactionExecutor.java:143-146` | `BookingReferenceGeneratorTest.generatedReferencesMatchExpectedFormats`, `BookingApiIntegrationTest.booksValidPlannedItineraryAndDecrementsAllComponents` | implemented |
| Concurrent booking attempts against the final available seat, room night, or car unit result in exactly one successful booking and clean conflict rejection for the other, without negative inventory or deadlocks. | Deterministic locking order in `BookingTransactionExecutor.java:81-120`: flights sorted by ID ASC, stay nights sorted by unit and date ASC, rental unit locked FOR UPDATE. | `BookingConcurrencyIntegrationTest.flightSeatContentionRace`, `stayNightContentionRace`, `rentalCarContentionRace` | implemented |
| Booking records and component snapshots survive an application restart and are strictly isolated to the authenticated trip owner. | `JdbcBookingRepository.java:144-212`, `BookingService.java:122-129` | `BookingApplicationRestartIntegrationTest.persistsBookingAndSnapshotsAcrossApplicationRestart`, `BookingApiIntegrationTest.enforcesTripOwnerIsolation` | implemented |

## Active Project Guardrails

- `ai/thoughts/design-lens.md`: None recorded.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd test -DskipFrontend=true` — 166 tests run, 0 failures, 0 errors, 0 skipped (37.4s).

## Residual Risks and Optional Developer Checks

- **High-throughput load testing:** Concurrent execution was verified with 2-thread barriers across flight, stay, rental, and duplicate idempotency scenarios. Verification with higher worker concurrency (e.g. 10–50 parallel threads) can be performed in staging if capacity benchmarking is desired.
- **Frontend review view integration:** Wiring the frontend "Confirm Booking" button in `BookingReviewView.tsx` to the `POST /api/trips/{tripId}/bookings` endpoint is planned for the next ticket (P06-T02).

## Disposition

- `clean`
