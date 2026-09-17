# P00-T02 — Define the DeTour Replacement Architecture

## Outcome

Create a reviewed architecture boundary that makes DeTour—not the former Wayfarer application or its Loomspan skills—the source of truth for later implementation. The record must tell later tickets which capabilities are retained, replaced, or removed and establish clear ownership for users, trips, itineraries, bookings, and catalog inventory.

## Requirements

- Use `app.detour` as the Java package namespace and `detour` for technical identifiers unless a platform convention requires another form. Select and document consistent Maven artifact, Spring application, frontend package/application, configuration-prefix, and packaged-output naming for later tickets.
- Preserve the current Java 21, Spring Boot, React, H2, Flyway, and single packaged-application shape unless the Phase 0 baseline demonstrates a concrete incompatibility. Record any such incompatibility as a decision requiring developer review rather than silently changing the platform.
- Produce a complete keep/replace/remove matrix for current Wayfarer capabilities. At minimum, address reusable inventory and transaction behavior; trip planning and validation; model orchestration and natural-language changes; booking, exchange, disruption, cancellation, and recovery behavior; authentication; persistence; frontend flows; observability; scripts; documentation; and tests.
- Record that deterministic Java services replace Loomspan/model orchestration, natural-language interpretation, ranking, explanation, and validation. DeTour must not introduce another AI framework, model endpoint, prompt layer, or API key.
- Define these ownership boundaries without designing their detailed schemas or APIs:
  - a User owns Trips and all downstream user data through an enforceable relational path;
  - a Trip owns destination, dates, travelers, overall budget, and itinerary alternatives;
  - an Itinerary owns its selected airfare, stay, rental car, and calculated totals;
  - a Booking is the inventory-reserving, immutable booked snapshot, with at most one active Booking per Trip;
  - application-owned catalogs and inventory are shared reference data and are not user-owned.
- Preserve the roadmap's distinction between Draft and Planned itineraries, active Bookings, and immutable Canceled Booking history. Do not collapse cancellation into a generic itinerary state.
- Use the P00-T01 test disposition as an input and explain how retained behavior remains protected while obsolete scenario- or model-coupled behavior is removed in Phase 1.
- Write a durable architecture decision record or equivalent repository document. Do not perform the package rename, dependency removal, schema creation, API design, or frontend redesign in this ticket.

## Acceptance criteria

- [ ] A committed-ready architecture record names the selected DeTour package, artifact, application, configuration, frontend, and packaged-output conventions consistently.
- [ ] The record contains a keep/replace/remove decision for every capability category listed in the requirements, with no category delegated back to Wayfarer YAML skills, demo documents, or current UI copy for interpretation.
- [ ] The record unambiguously assigns ownership for User, Trip, Itinerary, Booking, catalog, and inventory concepts and preserves the roadmap's lifecycle distinctions.
- [ ] The record states that DeTour planning and validation are deterministic application logic and that no Loomspan or substitute AI/model integration belongs in the target architecture.
- [ ] Retained platform choices and any evidence-backed incompatibility are explicit; a materially different platform cannot be selected without developer review.
- [ ] A diff confirms that this ticket adds or updates only architecture documentation and does not implement Phase 1 changes.

## Context

- **Phase:** 0 — Baseline and Boundaries.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-0-baseline-and-boundaries.md`](../phases/phase-0-baseline-and-boundaries.md).
- **Hard dependency:** P00-T01 must be complete so its baseline and test-disposition evidence can inform this record.
- **Downstream dependencies:** P00-T03 and all Phase 1 tickets rely on the naming and ownership decisions recorded here.
- **Scope exclusions:** application code changes; detailed schema or endpoint design; authentication implementation; catalog fixtures; trip workflows; booking implementation; UI visual design; Version 2 Events.
- Keep the work bounded for GPT-5.6 Terra: produce one decision record with a capability matrix and domain-boundary section. Detailed implementation research belongs to the later ticket that changes each area.

## Verification

- Cross-check every matrix category against the Phase 0 sources and the P00-T01 test disposition.
- Search the record for unresolved placeholders or language that delegates requirements to obsolete Wayfarer sources.
- Inspect the final diff to verify that no production, test, configuration, generated, or database file changed.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This ticket makes cross-cutting architecture and capability-disposition decisions that govern all subsequent phases. Material discovery and design remain even though the immediate deliverable is documentation.
- **Reassessment triggers:** none.
