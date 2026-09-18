# P02-T01 — Establish the DeTour Catalog and Inventory Foundation

## Outcome

Extend the fresh DeTour Flyway lineage with a normalized, application-owned catalog and finite-inventory model for airfare, accommodations, and rental cars. The schema must make the March 2027 fictional catalog deterministic to seed, straightforward to search and price, and safe for later atomic booking and cancellation without introducing those later workflows now.

## Requirements

- Add forward-only Flyway migrations after the completed Phase 1 lineage. Do not rewrite an already-applied DeTour migration or add a compatibility path for the discarded Wayfarer schema or data.
- Model shared reference data for the fixed PDX origin; San Francisco, Munich, and Mexico City destinations; real airport codes and geography; and fictional suppliers. Catalog and inventory data are application-owned shared data, not user-owned data.
- Normalize the catalog concepts needed by all three component families: recurring flight definitions and dated flight instances; accommodation properties, bookable rooms or whole-property units, and nightly inventory; and destination-airport rental locations, vehicle classes, physical units, and time-bounded unit reservations or equivalent occupancy records.
- Separate reusable catalog definitions from dated inventory where doing so keeps fixture migrations compact while retaining stable, immutable identifiers that later search, snapshot, booking, and cancellation behavior can reference.
- Represent all money in USD integer cents. Preserve base prices and taxes/fees at the granularity needed to calculate transparent, deterministic all-inclusive totals without floating-point arithmetic.
- Represent instants with explicit zone/offset semantics and retain the airport or destination time-zone information needed to derive correct local departure, arrival, pickup, and return presentation, including trips that cross calendar dates or time zones.
- Support flight seat capacity by dated instance, accommodation capacity and nightly availability, and rental-unit availability over half-open pickup/return intervals so a unit may be returned and picked up again at the same instant without a false overlap.
- Enforce relational and database constraints against negative prices, taxes, fees, capacity, or inventory; nonpositive bookable capacity; invalid date/time ranges; orphaned records; invalid component categories; and inventory values that exceed their catalog capacity.
- Leave exact table names, fictional names, numeric fixture values, and migration decomposition to implementation design. The chosen design must support the settled Phase 2 fixture contract and later authoritative price revalidation, immutable Planned/Booked snapshots, atomic inventory reservation, and exact-once restoration.
- Do not add Trip, itinerary, search API, catalog UI, booking, cancellation, payment, live-supplier, dynamic-pricing, multi-currency, or post-March-2027 behavior in this ticket.

## Acceptance criteria

- [ ] A clean database applies the complete DeTour Flyway lineage and creates normalized catalog and finite-inventory structures for airfare, accommodations, and rental cars without any Wayfarer compatibility schema or data.
- [ ] An existing Phase 1 database migrates forward without rewriting its applied migrations or losing registered DeTour users.
- [ ] Schema-level verification proves that negative monetary/inventory values, nonpositive bookable capacities, invalid temporal ranges, orphan references, invalid categories, and inventory above capacity are rejected.
- [ ] The schema can represent recurring and dated direct/one-stop flights with zoned local timing, per-seat pricing and capacity; hotel/B&B rooms and whole vacation rentals with nightly inventory; and individual rental units with non-overlapping time-bounded occupancy.
- [ ] Stable catalog and inventory identifiers can be retained by later Planned/Booked snapshots and booking revalidation without treating shared catalog data as user-owned.
- [ ] Money and temporal round-trip tests preserve integer-cent values and unambiguous instants/local times across the supported airports and destination time zones.
- [ ] Backend tests and clean-database Flyway verification pass, and the packaged application still starts with the Phase 1 identity behavior intact.
- [ ] No Trip, itinerary, component-search UI/API, booking, cancellation, payment, live-supplier, dynamic-pricing, multi-currency, or expanded-date-range behavior is introduced.

## Context

- **Phase/work package:** Phase 2 — Catalog and Fixture Data; work package 2.1.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-2-catalog-and-fixtures.md`](../phases/phase-2-catalog-and-fixtures.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependency:** Phase 1 is complete, including its DeTour Flyway lineage and persistent identity schema.
- **Downstream dependencies:** P02-T02 and P02-T03 populate and verify this schema. Phases 4–6 depend on its stable identifiers, pricing inputs, and finite-inventory semantics.
- **Scope exclusions:** actual Phase 2 fixture population; user-facing catalog search/selection; Trip and itinerary persistence; inventory mutation services; booking/cancellation; Version 2 Events.
- This ticket is sized for GPT-5.6 Terra around one cohesive persisted-domain design. It should leave a clean, buildable schema foundation rather than combine the materially different flight-generation and fixture-value design work into the same implementation context.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This ticket introduces normalized persisted contracts, temporal and monetary semantics, Flyway migrations, and inventory structures that constrain later search and transactional booking behavior; material design and compatibility analysis remain.
- **Reassessment triggers:** Discovery that the completed Phase 1 lineage is not safely forward-migratable, or that the proposed schema cannot support later atomic reservation/restoration without adding lifecycle behavior now, requires developer review before implementation continues.
