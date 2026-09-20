# P03-T02 — Deliver Versioned Draft Alternatives and Autosave

## Outcome

A user can maintain multiple mutable Draft alternatives for one Trip, refresh without losing work, and see whether each save succeeded or conflicted instead of having one browser tab silently overwrite another.

## Requirements

- Allow the owner to create an additional component-empty Draft or explicitly duplicate an existing Draft into a new Draft. Never infer duplication from the create action, and never mutate the source.
- Autosave mutable Draft data and mutable Trip details through explicit authenticated APIs. Every update must carry the last observed version; stale updates must be rejected with a conflict response containing enough current-version information for the client to reload deliberately.
- Version the shared Trip details and each mutable Draft at the boundary needed to prevent silent lost updates from concurrent tabs. Do not use last-write-wins behavior.
- While no Planned alternative exists, permit the owner to change destination, dates, traveler count/ages, and budget subject to the creation and supported-travel rules. Keep Trip creation requirements intact, and distinguish an absent budget from zero.
- For this ticket's component-empty Drafts, update shared details without inventing component changes. The compatibility, repricing, and removal summaries for populated Drafts belong to P03-T04 after snapshot/selection storage exists.
- Support deletion of an individual Draft owned by the current user. A Trip may temporarily have no alternatives after its final Draft is deleted; it remains available for creating a new component-empty Draft or for the explicit Trip-deletion workflow.
- Return stable machine-readable validation, conflict, ownership, and persistence failures so the frontend can expose `saving`, `saved`, and `error/conflict` states without claiming success early.
- Preserve all successful alternatives and updates across refresh and application restart. Do not add background polling, real-time collaboration, merging, or automatic conflict resolution.

## Acceptance criteria

- [x] A user can create multiple independently identified component-empty Drafts and explicitly duplicate a Draft; the source remains unchanged and all alternatives survive refresh and restart.
- [x] Supported shared-detail edits succeed while the Trip has no Planned alternative, increment the appropriate version, and retain the aggregate's ownership and derived label rules.
- [x] Two clients updating from the same version cannot silently overwrite one another: one succeeds, the stale mutation receives a deterministic conflict, and persisted state matches the successful update.
- [x] Validation and persistence failures are distinguishable from successful saves, and retrying after a failure does not create duplicate alternatives.
- [x] Draft deletion removes only the selected alternative, rejects cross-user access without disclosure, and leaves a zero-alternative Trip usable for creating a new Draft or explicitly deleting the Trip.
- [x] Backend integration tests cover two-user isolation, duplicate/create/delete behavior, stale versions, concurrent requests, rollback on failure, and restart persistence.
- [x] No component search/selection, automatic conflict merge, Planned promotion, booking/cancellation, sharing, collaboration, or Version 2 Event behavior is introduced.

## Context

- **Phase/work packages:** Phase 3 — Trips, Itineraries, and Profile; Draft portions of work packages 3.1 and 3.2 and the component-empty portion of 3.3.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-3-trips-itineraries-and-profile.md`](../phases/phase-3-trips-itineraries-and-profile.md).
- **Hard dependency:** P03-T01 must be complete so Draft alternatives extend the settled owner-scoped Trip aggregate and version contract.
- **Downstream dependencies:** P03-T03 adds Planned sources and immutability; P03-T04 extends shared-detail edits to populated selections; P03-T06 exposes autosave status in the UI. Phase 4 supplies component selection through these Draft contracts.
- **Scope exclusions:** component search or selection, canonical pricing, Planned promotion, compare, Booked/Canceled Booking sources, Trip-level selective duplication, profile UI, booking/cancellation, automatic merge, and Version 2 Events.
- This ticket is sized for GPT-5.6 Terra around one concurrency-sensitive Draft lifecycle and its API-level proof, leaving the broader profile and snapshot workflows to separate contexts.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The work changes persisted lifecycle and concurrency behavior, including stale-write handling, transactional duplication, and owner-scoped mutation contracts.
- **Reassessment triggers:** Discovery that the P03-T01 version is insufficient to distinguish shared Trip edits from alternative edits should trigger explicit concurrency design in the full plan, not a last-write-wins shortcut.

## Execution notes

- Implemented V13 to remove the one-Draft-per-Trip restriction while retaining the parent FK and a child lookup index.
- Shared-detail replacement and every alternative collection mutation now use owner-scoped optimistic guards. Named duplicate/delete operations condition the parent advance on the selected Draft version, returning stable `VERSION_CONFLICT` fields for either stale boundary.
- Verification passed with the focused Trip API, restart/migration, full backend, frontend test/build, package, and packaged-identity checks. The browser autosave-status observation remains optional and deferred to P03-T06.
