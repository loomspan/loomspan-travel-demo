# P03-T01 — Establish Owned Trips and Initial Drafts

## Outcome

An authenticated user can create and retrieve a persistent Trip with its first component-empty Draft, while invalid shared travel details and access by another user are rejected. This establishes the owned aggregate that every later planning, snapshot, profile, and booking workflow extends.

## Requirements

- Persist each Trip under exactly one authenticated owner with destination, start date, end date, traveler count and exact traveler ages when supplied, optional overall budget in USD integer cents, a derived display label, and a concurrency version. Persist the first Draft in the same transaction as Trip creation.
- Accept only the three supported destinations and Trips from PDX whose dates fall within March 1–31, 2027, span 1–14 nights, and have 1–8 travelers. Destination, dates, and traveler count are required at creation; ages and budget may be absent until later planning.
- When ages are supplied, require exactly one age per traveler and accept only nonnegative realistic integer ages within a documented bound. A Draft may temporarily contain no adult; at least one traveler age 18 or older becomes mandatory at Planned promotion. Accept a zero budget, reject a negative budget, and choose and document a maximum that is safe for the persisted integer-cent type.
- Derive the user-facing Trip label from destination and dates. Do not persist or accept a custom name.
- Expose authenticated create and detail retrieval behavior with stable resource identifiers and a response that includes the Trip version and its initial Draft. All lookups and mutations must scope ownership in backend code and must not reveal whether another user's identifier exists.
- Persist successfully created Trips, travelers, budgets, and Draft identifiers across application restart. A failed validation, failed ownership check, or partial persistence error must not leave a Trip without its initial Draft.
- Keep the initial Draft component-empty. Do not add component search/selection, canonical pricing, Planned promotion, profile organization, booking records, or cancellation behavior in this ticket.

## Acceptance criteria

- [ ] A signed-in user can create a supported Trip and atomically receives the derived label, version, normalized shared details, and one component-empty Draft with stable identifiers.
- [ ] Creation rejects missing or unsupported destination/date/traveler data, dates outside the supported window, invalid trip length, traveler counts outside 1–8, mismatched or invalid supplied ages, negative or excessive budgets, and malformed money without persisting a partial aggregate.
- [ ] A Draft with complete valid ages but no adult can be saved as incomplete and is blocked only when Planned promotion is attempted.
- [ ] Ages and budget may be omitted at creation, while zero budget is retained distinctly from an absent budget.
- [ ] Two-user integration coverage proves a user cannot read or mutate another user's Trip and receives no protected Trip data from the rejection.
- [ ] Concurrent or duplicate failed creation does not produce an orphan Trip or Draft, and persisted records survive an application restart.
- [ ] Clean-database migration, backend tests, frontend build, and packaged-application startup continue to pass without external supplier or model services.
- [ ] No component-selection, pricing, promotion, profile-listing, booking, cancellation, custom-name, sharing, collaboration, or Version 2 Event behavior is introduced.

## Context

- **Phase/work package:** Phase 3 — Trips, Itineraries, and Profile; the creation and persistence portion of work package 3.1.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-3-trips-itineraries-and-profile.md`](../phases/phase-3-trips-itineraries-and-profile.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependency:** Phase 2 must be complete so destination references and supported catalog boundaries are settled, and Phase 1 identity must remain the ownership root.
- **Downstream dependencies:** P03-T02 adds Draft alternatives and versioned autosave; P03-T03 adds Planned lifecycle; all later Trip tickets depend on this aggregate and ownership path.
- **Scope exclusions:** Trip/profile frontend, component selection, authoritative component pricing, comparison, booking/cancellation, live suppliers, multiple currencies, custom Trip names, notes, sharing, collaboration, voting, merging, and Version 2 Events.
- This ticket is sized for GPT-5.6 Terra around one persisted aggregate, its creation transaction, validation boundary, owner-scoped API, and restart/isolation verification.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The work introduces a user-owned persisted contract, transactional aggregate creation, monetary and temporal validation, and an authorization boundary consumed by every later phase.
- **Reassessment triggers:** If the Phase 2 destination keys or Phase 1 ownership schema cannot support a direct immutable reference without changing their contracts, keep the work on the full route and reconcile the persisted design rather than adding a compatibility path.
