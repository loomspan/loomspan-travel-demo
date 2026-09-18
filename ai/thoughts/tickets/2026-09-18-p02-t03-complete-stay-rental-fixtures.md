# P02-T03 — Complete Stay and Rental Fixtures with Catalog Integrity Reporting

## Outcome

Complete the deterministic March 2027 catalog with varied accommodation and rental-car inventory for all three destinations, and provide automated integrity checks plus a concise summary that lets reviewers verify the entire Phase 2 fixture set without reading migration rows.

## Requirements

- Populate fixtures through compact, deterministic Flyway-managed SQL using the P02-T01 schema. Do not generate nondeterministic catalog data at startup or depend on external suppliers.
- Create exactly two fictional properties of each supported type—hotel, B&B, and vacation rental—in San Francisco, Munich, and Mexico City. Each property has a stable immutable identifier, fictional supplier/display metadata, an accurate destination/location association, guest rating, deterministic distance to city center, and pricing/capacity data appropriate to its type.
- Model hotels and B&Bs as room inventory with per-room guest capacity and nightly availability. Model vacation rentals as whole-property units with maximum guest capacity and nightly availability. Seed every supported night needed for valid March 1–31, 2027 Trips.
- Store nightly base prices and taxes/fees in USD integer cents at a granularity that supports transparent complete-stay totals. Values and capacities must produce meaningful budget and capacity tradeoffs across supported parties of 1–8 rather than making every option interchangeable, while retaining usable options for the supported range.
- Create fictional rental suppliers at SFO, MUC, and MEX airport locations. Populate economy, standard, and SUV inventory at every destination with stable supplier, class, and physical-unit identifiers.
- Store an all-inclusive daily estimate or deterministic integer-cent base-price and taxes/fees inputs that produce one. Later pricing must charge consecutive 24-hour cycles from pickup to return and round a partial final cycle up; fixture data must not encode calendar-day billing.
- Make each physical rental unit checkable for availability over a complete local pickup/return interval. Seed availability for the supported March travel window and ensure the model permits back-to-back non-overlapping rentals while rejecting actual overlap when reservations are later created.
- Select exact fictional property/supplier names, ratings, addresses or location descriptions, distances, prices, fees, capacities, and unit counts during planning/implementation. Values must be deterministic, plausible, and deliberately varied so later price, rating, distance, capacity, and class ordering rules exercise different results.
- Add automated integrity checks across the complete Phase 2 catalog: airfare direction/date and direct/connection coverage; destination and accommodation-type coverage; airport and rental-class coverage; stable identifier uniqueness; full supported nightly/date coverage; nonnegative integer-cent pricing; valid ratings, distances, capacities, and inventory; valid time-zone data; feasible flight connections; and the March 31 flight-arrival boundary.
- Provide one lightweight, documented, repeatable fixture-summary command or generator. Its deterministic human-readable output must report counts and representative options for every destination and component type, including enough timing, price, capacity, rating/distance, and inventory detail to spot an obviously incomplete or malformed catalog without dumping every row.
- Do not add catalog search/selection APIs or UI, Trip/itinerary persistence, inventory mutation services, booking/cancellation, payment, live suppliers, dynamic pricing, multiple currencies, or dates outside March 2027.

## Acceptance criteria

- [ ] A clean Flyway migration creates exactly two hotels, two B&Bs, and two vacation rentals in each destination, with stable identifiers, deterministic display/location/rating/distance data, appropriate capacity semantics, and nightly inventory covering every supported stay night.
- [ ] For parties from 1 through 8, fixture verification demonstrates usable stay inventory and meaningful differences in required room/property fit, complete-stay price, rating, and distance rather than identical ordering across all options.
- [ ] SFO, MUC, and MEX each have fictional airport rental supply with economy, standard, and SUV physical units, deterministic integer-cent daily pricing inputs, and availability across the supported pickup/return window.
- [ ] Rental verification proves consecutive 24-hour billing with a rounded-up partial final cycle can be calculated from the fixtures, and that back-to-back intervals do not overlap while intersecting intervals do.
- [ ] Cross-catalog integrity checks pass for all settled Phase 2 coverage, identifier, pricing, capacity, inventory, temporal, time-zone, connection, distance, and March 31 boundary rules and fail when representative violations are introduced in test scope.
- [ ] A documented single command produces a deterministic, concise human-readable summary with total counts and representative airfare, stay, and rental options for all destinations; reviewers do not need to inspect raw migration rows.
- [ ] Rebuilding the database produces the same property, rental, nightly inventory, identifier, value, count, and summary output.
- [ ] The complete Phase 2 catalog is entirely Flyway-managed and fictional, uses real airport codes/geography, and requires no external supplier or model service.
- [ ] The existing identity behavior, backend tests, clean-database Flyway verification, and packaged-application startup continue to pass.
- [ ] No search/selection UI or API, Trip/itinerary behavior, inventory mutation service, booking/cancellation, payment, live-supplier integration, dynamic pricing, multi-currency, or expanded-date-range behavior is introduced.

## Context

- **Phase/work packages:** Phase 2 — Catalog and Fixture Data; work packages 2.3, 2.4, and the final cross-catalog portion of 2.5.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-2-catalog-and-fixtures.md`](../phases/phase-2-catalog-and-fixtures.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependencies:** P02-T01 and P02-T02 must be complete so this ticket can populate the settled schema and verify/report the complete catalog.
- **Downstream dependencies:** Phase 3 establishes Trips that reference the supported destinations/dates. Phase 4 consumes the stay/rental fixtures for deterministic search and selection; Phases 5–6 retain their identifiers for pricing and atomic booking/cancellation.
- **Scope exclusions:** user-facing catalog APIs and UI; Trip, itinerary, or snapshot persistence; booking/cancellation and inventory mutation; payment; live suppliers; dynamic pricing; multi-currency; added origins, destinations, component types, or date ranges.
- Exact fictional values are implementation-design choices, not a deferred product decision. The full pipeline may select them provided the observable coverage, tradeoff, determinism, and integrity requirements above are met.
- This ticket is sized for GPT-5.6 Terra around the two smaller inventory domains and the final whole-catalog audit surface, after the schema and combinatorial airfare work have stabilized.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The ticket adds persisted dated and interval inventory, chooses fixture values that drive later ranking and capacity behavior, and verifies the complete Phase 2 data contract across multiple domains.
- **Reassessment triggers:** If P02-T01 lacks the capacity, nightly-inventory, interval, monetary, or stable-identifier semantics required here, or P02-T02 lacks reportable deterministic identifiers, return to full persisted-contract design rather than compensating in the summary generator.
