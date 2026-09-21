# P03-T04 — Protect Shared-Detail Revisions and Selective Trip Duplication

## Outcome

Changing destination, dates, or travelers never silently corrupts an alternative: mutable Draft selections are revalidated with an explicit change summary, while Trips that already contain Planned snapshots are preserved and revised through a new Trip built from only the Planned sources the user selected.

## Requirements

- While a Trip has no Planned alternative, allow owner-authorized shared-detail changes in place using the existing optimistic-concurrency contract.
- When traveler count or ages change, reprice each component from authoritative Phase 2 catalog inputs and revalidate every populated Draft selection for capacity, room count, and age eligibility. Retain selections that remain valid; remove those that do not. Return an immediate structured summary of every changed price, room count, eligibility result, or removed selection, including a user-readable reason. Phase 5 later composes these component totals into the canonical itinerary and budget calculations.
- When destination or dates change, remove every Draft component that is incompatible with the revised Trip and return a structured summary naming what was removed and why. Never retain a catalog reference merely because its identifier still exists.
- When at least one Planned alternative exists, reject in-place changes to destination, dates, or travelers. Instead, create a new Trip with the revised shared details and require a nonempty explicit list of Planned snapshot sources. Convert each selected Planned source into a Draft in the new Trip; never copy Draft alternatives through this Trip-level operation.
- Revalidate every copied component against the new shared details. Retain compatible content, remove incompatible content, and return one post-duplication summary of all removals and price, capacity, room-count, or eligibility changes with reasons. The source Trip and every source Planned snapshot must remain unchanged.
- Reject an active-Trip revision when no Planned source is selected, and reject unknown, duplicate, cross-Trip, or cross-owner source identifiers without partial creation. The component-empty fallback is a Phase 6 extension used only when duplicating a canceled Trip with no selected/available Planned source.
- A budget-only change must recalculate current Draft budget presentation but must not add, remove, or rewrite snapshot selections. An absent budget and a zero budget remain distinct.
- Keep the selective Planned-source copying and compatibility rules usable by Phase 6 when it adds canceled-Trip duplication. Phase 6 owns canceled status, the component-empty fallback when no Planned source is selected or available, Booking history, and access to Booked/Canceled Booking snapshots; this ticket must not invent those states or records.

## Acceptance criteria

- [x] Pre-Planned traveler edits retain still-valid Draft selections, remove invalid ones, and report every resulting price, room-count, eligibility, capacity, and selection change with specific reasons.
- [x] Pre-Planned destination/date edits remove incompatible components and report each removal; no incompatible reference or stale displayed total remains.
- [x] Once any Planned snapshot exists, an in-place destination/date/traveler edit is rejected and the revision workflow creates a separate owned Trip from exactly the selected Planned sources.
- [x] Active-Trip selective duplication requires at least one source and produces one Draft per selected Planned snapshot while preserving all source Trips and snapshots unchanged.
- [x] Invalid or unauthorized source lists fail atomically without revealing protected alternatives or leaving a partial new Trip.
- [x] Budget-only updates change Draft budget presentation without mutating Planned selections, and optimistic conflicts cannot silently overwrite either source or new aggregate state.
- [x] Deterministic tests cover supported destination/date/traveler revisions, partial component compatibility, zero versus absent budget, concurrent requests, restart persistence, and two-user isolation.
- [x] Draft, Booked, and Canceled Booking records are not copied by the Trip-level operation, and Phase 6 history/cancellation behavior is not implemented here.

## Context

- **Phase/work package:** Phase 3 — Trips, Itineraries, and Profile; work package 3.3 and the canceled-Trip duplication contract from 3.4.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-3-trips-itineraries-and-profile.md`](../phases/phase-3-trips-itineraries-and-profile.md).
- **Hard dependency:** P03-T03 must be complete with a persisted selection/snapshot shape that can retain the Phase 2 catalog references and resolved values needed for revalidation. Phase 4 consumes and extends this behavior through its search/selection flows; it is not a prerequisite and its UI/results APIs are out of scope here.
- **Downstream dependencies:** P03-T05 exposes revision/deletion policy in profile projections; P03-T06 presents summaries. Phase 6 calls the same selective Planned-source operation for canceled Trips and adds booking-history restrictions.
- **Scope exclusions:** component search/ranking UI, editing Planned snapshots, copying Drafts or booking history at Trip level, booking/cancellation, automatic source selection, silent compatibility fallback, and Version 2 Events.
- This is intentionally a separate GPT-5.6 Terra ticket because cross-alternative revalidation, atomic selective duplication, and explanatory change summaries are materially more complex than ordinary Draft autosave.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The work is cross-cutting lifecycle logic over persisted alternatives, performs authoritative revalidation and transactional copying, and must preserve immutable history under concurrency and authorization constraints.
- **Reassessment triggers:** Missing authoritative pricing/capacity semantics for any populated component, or evidence that Phase 4 stores insufficient snapshot data, requires full design reconciliation before that component's revision behavior is implemented.
