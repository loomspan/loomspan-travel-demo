# P02-T02 — Seed Complete Deterministic Airfare Fixtures

## Outcome

Populate the DeTour catalog with compact, deterministic March 2027 airfare fixtures so every supported round trip from PDX to San Francisco, Munich, or Mexico City can offer multiple fictional direct and one-stop choices with authoritative timing, pricing, and seat capacity.

## Requirements

- Use Flyway-managed fixture generation based on recurring definitions, database-supported sequences, or an equivalently compact deterministic SQL technique. Do not hand-author thousands of dated rows, generate data nondeterministically at application startup, or depend on an external service.
- Generate outbound PDX-to-destination and inbound destination-to-PDX instances for every direction/date combination that can participate in a valid 1–14-night Trip whose selected travel dates are within March 1–31, 2027 and whose final flight arrival is no later than March 31, 2027.
- For each required destination, direction, and usable departure date, provide exactly two direct choices and two one-stop choices before later inventory depletion. Derive usable dates from the settled Trip-length and final-arrival rules rather than assuming every March departure date is valid in both directions.
- Use the real airport codes and geography for PDX, SFO, MUC, MEX, and the required connections. San Francisco one-stop choices connect through SEA and SLC; Munich choices through SEA and ORD; Mexico City choices through LAX and DFW. Carrier and supplier identities remain fictional.
- Each choice must contain stable identity; carrier and flight display data; origin, destination, and any connection; local departure and arrival values with unambiguous zone/offset semantics; total elapsed itinerary duration; stop count; feasible segment and layover timing; per-traveler base fare; per-traveler taxes/fees; and positive initial seat capacity.
- Charge every traveler the same airfare and require one seat per traveler regardless of age. Preserve the pricing inputs needed for later complete-party totals without implementing the Phase 4 search/selection API or UI.
- Exclude any choice whose final arrival is after March 31, 2027 even if it departs during March. Overnight and international schedules may cross calendar dates when their zoned elapsed timing is valid and their final arrival remains within the boundary.
- Choose exact fictional carrier names, flight numbers, schedules, fares, fees, and capacities during planning/implementation. Values must be deterministic, plausible enough to exercise sorting and comparison, and varied enough that lowest price, shortest duration, earliest departure, and fewest stops do not all collapse to the same ordering.
- Preserve stable immutable identifiers and deterministic generation across clean database builds so later sort tie-breakers, snapshots, and booking references do not change between runs.
- Add airfare-specific integrity verification for complete date/direction/destination/stop coverage, unique stable identifiers, positive pricing and capacity, valid time zones and elapsed duration, geographically correct connections, feasible layovers, and the March 31 final-arrival boundary.
- Do not add round-trip search endpoints, frontend results, selection persistence, inventory mutation, Trip/itinerary behavior, booking/cancellation, live carriers, dynamic pricing, multiple currencies, or dates outside March 2027.

## Acceptance criteria

- [ ] A clean Flyway migration deterministically creates exactly two direct and two one-stop choices for every required destination, direction, and usable departure date, with no required combination missing or duplicated.
- [ ] Every one-stop SFO, MUC, and MEX choice uses its required connection airport; every itinerary has chronologically feasible legs and layover, a correct stop count and elapsed duration, and unambiguous local/zoned timing.
- [ ] No generated flight has a final arrival after March 31, 2027, while all direction/date combinations that can participate in at least one valid 1–14-night supported Trip remain covered.
- [ ] Every choice has a stable immutable identifier, fictional carrier/display data, positive seat capacity, and nonnegative integer-cent base fare and taxes/fees sufficient to calculate an all-inclusive per-traveler and complete-party total.
- [ ] Verification demonstrates that one seat and the same fare are attributable to every traveler regardless of age, and fixture variation produces meaningful differences in price, duration, departure time, and stop count.
- [ ] Rebuilding the database produces the same airfare identifiers, values, counts, and representative records.
- [ ] Automated integrity checks fail when representative coverage, timing, connection, price, capacity, identifier, or final-arrival invariants are deliberately violated in test scope.
- [ ] The existing identity behavior, backend tests, clean-database Flyway verification, and packaged-application startup continue to pass without external supplier or model services.
- [ ] No search/selection API or UI, Trip/itinerary behavior, inventory mutation, booking/cancellation, live-supplier integration, dynamic pricing, multi-currency, or expanded-date-range behavior is introduced.

## Context

- **Phase/work package:** Phase 2 — Catalog and Fixture Data; work packages 2.2 and the airfare portion of 2.5.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-2-catalog-and-fixtures.md`](../phases/phase-2-catalog-and-fixtures.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependency:** P02-T01 must be complete so fixtures target the settled shared catalog and inventory schema.
- **Downstream dependencies:** P02-T03 completes the other component fixtures and cross-catalog summary. Phase 4 consumes these records for deterministic round-trip search, filtering, sorting, and selection; Phases 5–6 retain identifiers for authoritative repricing and booking.
- **Scope exclusions:** accommodation and rental-car fixtures; user-facing search/selection; Trip and itinerary data; booking/cancellation; live suppliers; dynamic pricing; multiple currencies; additional origins, destinations, or date ranges.
- Exact fictional values are implementation-design choices, not a deferred product decision. The full pipeline may select them provided the observable coverage, determinism, validity, and variation requirements above are met.
- This ticket is sized for GPT-5.6 Terra around the combinatorial flight-generation problem and its boundary-focused verification, separate from the simpler nightly and interval inventory fixtures.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The work adds a large deterministic persisted dataset through Flyway, must derive nontrivial temporal coverage across time zones, and establishes identifiers and price/inventory inputs consumed by later supported behavior.
- **Reassessment triggers:** Evidence that P02-T01 cannot represent a required connection, zoned schedule, or stable generated identifier without changing its persisted contract should return the ticket to full schema/design review rather than introducing an ad hoc fixture workaround.
