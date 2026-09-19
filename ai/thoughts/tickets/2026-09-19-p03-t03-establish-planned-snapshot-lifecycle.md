# P03-T03 — Establish Immutable Planned Snapshot Lifecycle

## Outcome

Valid Draft alternatives can become stable Planned snapshots that never change with later Draft or catalog edits, and users can deliberately duplicate or delete alternatives without confusing a snapshot with its mutable source.

## Requirements

- Represent Draft and Planned alternatives as distinct lifecycle concepts under one Trip. A Trip may own multiple of each, and promotion creates an immutable Planned snapshot without mutating or deleting unrelated alternatives.
- Permit promotion only when the Trip has exact ages for every traveler, at least one adult, a supplied nonnegative budget, and at least one structurally valid reservable airfare, stay, or rental-car selection. Return every blocking readiness issue together rather than only the first.
- Copy the selected component's resolved descriptive and price data into immutable snapshot records while retaining stable catalog/inventory references for later authoritative revalidation. Later catalog display or price changes must not rewrite prior Planned content.
- Limit this ticket's price/readiness check to the data and deterministic catalog rules available at implementation time. Phase 5 remains responsible for the final canonical total calculation, availability/staleness checks, budget-overage acknowledgment, comparison, and booking-choice experience; do not create a competing pricing contract.
- Allow explicit duplication of a Draft or Planned alternative into a new Draft. The new Draft receives copied content and current mutable versions, while the source remains byte-for-byte stable.
- Allow explicit deletion of a Draft and confirmation-gated deletion of a Planned snapshot when no booking-history rule protects it. Deletion must affect only the selected alternative and must not mutate catalog inventory.
- Preserve the rule that Planned alternatives are never edited in place. Any attempt to use a Draft-update operation against a Planned identifier must fail deterministically.
- Design the alternative/snapshot boundary so Phase 6 can add duplication from immutable Booked and Canceled Booking snapshots without representing Canceled Booking as a mutable itinerary state. Do not implement booking records or fake booking states here.

## Acceptance criteria

- [ ] A ready Draft can be promoted to a Planned snapshot, while a Draft missing ages, an adult, budget, or all reservable components is rejected with the complete set of relevant issues.
- [ ] A Planned snapshot retains its copied component descriptions and prices after its source Draft, another Draft, or referenced catalog display data changes.
- [ ] Draft and Planned sources can each be explicitly duplicated into a new mutable Draft without changing the source; stable identifiers and copied content are verifiable after restart.
- [ ] Planned update attempts are rejected, and deleting one Draft or confirmation-gated Planned alternative leaves every other alternative unchanged and has no inventory effect.
- [ ] Multiple Planned alternatives can coexist under one Trip, and owner-scoped tests prevent cross-user promotion, duplication, reads, and deletion without disclosing protected data.
- [ ] Transactional and concurrency coverage proves repeated/racing promotion or duplication does not create unintended duplicate snapshots or partially copied content.
- [ ] Phase 5 pricing/readiness and Phase 6 booking/cancellation behavior are not prematurely introduced, and no Canceled Booking is modeled as an itinerary status.

## Context

- **Phase/work packages:** Phase 3 — Trips, Itineraries, and Profile; Planned and individual-duplication portions of work package 3.2 and deletion terminology from 3.4.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), [`../phases/phase-3-trips-itineraries-and-profile.md`](../phases/phase-3-trips-itineraries-and-profile.md), and the downstream boundaries in [`../phases/phase-5-planning-budget-and-comparison.md`](../phases/phase-5-planning-budget-and-comparison.md) and [`../phases/phase-6-booking-and-cancellation.md`](../phases/phase-6-booking-and-cancellation.md).
- **Hard dependencies:** P03-T01 and P03-T02 must be complete. The persisted component-selection shape must be coordinated with Phase 4; this ticket may establish the snapshot storage contract but must not add Phase 4 search UI or results APIs.
- **Downstream dependencies:** P03-T04 uses Planned snapshots as selective Trip-duplication sources; P03-T05 counts and protects them; Phase 5 completes authoritative readiness/pricing and comparison; Phase 6 adds Booked and Canceled Booking snapshot sources.
- **Scope exclusions:** search/ranking UI, component builder, final Phase 5 canonical pricing and comparison, inventory reservation, active Booking enforcement, cancellation/history records, and Version 2 Events.
- This ticket is sized for GPT-5.6 Terra around the immutable snapshot boundary and its lifecycle invariants; comparison and transactional inventory are deliberately separate work.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The ticket establishes persisted immutable snapshot contracts and lifecycle/concurrency behavior that later pricing, comparison, booking, and audit history depend on.
- **Reassessment triggers:** If component selections cannot be snapshotted without first settling a material Phase 4 or Phase 5 contract, stop and return the specific contract question rather than persisting an opaque payload or duplicating pricing logic.
