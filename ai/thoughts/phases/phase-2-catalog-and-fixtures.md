# Phase 2 — Catalog and Fixture Data

## Outcome

Flyway creates a compact but varied fictional airfare, accommodation, and rental-car catalog for March 2027. The catalog supports deterministic search, price calculation, capacity checks, and later atomic booking without hand-writing thousands of rows.

## Work packages

### 2.1 Design normalized catalog schema

- Define destinations, airports, suppliers, schedules/instances, accommodations/units/nights, rental locations/classes/units, time-bounded rental reservations, and inventory.
- Store monetary values as integer cents and timestamps with explicit zones/offsets.
- Store accommodation locations and deterministic distance-to-city-center values for stay sorting.
- Separate catalog definitions from dated inventory where that keeps migrations compact.
- Add constraints that prevent negative capacity and structurally invalid fixture data.

### 2.2 Seed airfare

- Generate round-trip-searchable direct and one-stop flight instances from PDX to SFO, MUC, and MEX for every direction/date combination that can participate in a valid 1–14-night Trip and complete arrival by March 31, 2027.
- For each required direction/date combination, generate two direct and two one-stop choices per destination.
- Use Seattle and Salt Lake City connections for San Francisco, Seattle and Chicago for Munich, and Los Angeles and Dallas–Fort Worth for Mexico City.
- Include complete itinerary duration, local departure/arrival time, connection airport, layover, per-seat fare, taxes/fees, and seat capacity.
- Exclude schedules whose final arrival would occur after March 31, 2027.
- Charge the same airfare for every traveler and reserve one seat per traveler regardless of age.
- Use fictional carrier names and real airport codes/geography.
- Keep Flyway SQL compact by generating dated instances from recurring schedule rows or database-supported sequences.

### 2.3 Seed accommodations

- Create two fictional properties of each type—hotel, B&B, and vacation rental—in each destination.
- Seed nightly price, taxes/fees, room or property capacity, location, rating/display metadata, and daily inventory.
- Ensure the dataset includes meaningful budget and capacity tradeoffs for parties of 1–8.

### 2.4 Seed rental cars

- Create fictional airport rental suppliers at each destination.
- Seed economy, standard, and SUV units that can be checked for overlapping pickup/return intervals throughout the supported date window.
- Store all-inclusive daily estimates or the inputs needed to calculate taxes and fees deterministically.
- Price rentals in consecutive 24-hour billing cycles from pickup to return.

### 2.5 Verify fixture integrity

- Add automated checks for full date coverage, destination/type coverage, capacity, pricing, time-zone validity, connection feasibility, city-center distance, and the March 31 arrival boundary.
- Add a lightweight fixture summary generator so reviewers can inspect counts and representative options without reading migration rows.

## Exit criteria

- Every direction/date combination usable by a valid Trip has direct and one-stop flight choices for its destination.
- Every destination has two choices for each accommodation type and all three car classes.
- Fixture generation is deterministic and Flyway-managed.
- Integrity tests detect missing dates, impossible schedules, invalid prices, or negative capacity.

## Annotations

- **[RESOLVED]** Exact names, prices, schedules, ratings, capacities, locations, and city-center distances are defined by the implemented Flyway catalog fixtures.
- **[FUTURE]** Live suppliers, real-time inventory, dynamic pricing, multiple currencies, and additional date ranges.
