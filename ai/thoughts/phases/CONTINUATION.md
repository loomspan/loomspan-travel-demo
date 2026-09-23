# DeTour Planning Continuation Guide

## Purpose

This handoff makes the DeTour planning work resumable without access to the conversation that produced it. It describes the current repository, the authority of the roadmap, settled boundaries that are easy to misread from the old code, and the next work to perform.

Last updated: 2026-09-23.

## Read order

1. Read [README.md](README.md) completely. It is the authoritative product roadmap and consolidated decision record.
2. Read this continuation guide completely before creating tickets.
3. Read the phase document relevant to the next ticket. Phase files contain work packages, exit criteria, and local annotations.
4. Consult the [Version 2 Events roadmap](../future/version-2-events.md) only to enforce the Version 1 exclusion or when explicitly working on Version 2; do not pull its behavior into current-release tickets.
5. Before implementation, inspect the current worktree and preserve all pre-existing user changes.
6. Do not infer desired DeTour behavior from Wayfarer tests, YAML skills, demo documents, or current UI copy when they conflict with the roadmap.

If documents appear to conflict, treat [README.md](README.md) as authoritative, reconcile the relevant phase file in the same change, and do not ask the user to re-decide behavior already recorded here.

## Current planning status

- Product discovery is substantially complete.
- The dependency-ordered roadmap and eight delivery phases are written.
- Detailed implementation tickets have been written through Phase 6 in `ai/thoughts/tickets/`.
- Phases 1–6 have been implemented in the current repository. Phase 7 remains the next planned work; its release checks have not yet been completed.
- Phase 0 is complete. Its conservative persistence-preservation decision was superseded before implementation by the roadmap's development-stage clean-break policy.
- Event functionality has been deferred from the initial release to the [Version 2 Events roadmap](../future/version-2-events.md).
- The next expected activity is to write and implement cohesive Phase 7 tickets, including product experience, documentation, and release verification.
- Visual direction, destructive confirmation pattern, and release target are settled in the Phase 7 document. Final public/authentication and About copy remains for its copy pass; proposed wording is recorded there.
- The user wants solutions to remain as simple as possible while fully satisfying the recorded requirements.

## Current repository state

The current application is DeTour, a Spring Boot/React/H2 application:

- Java 21 and Spring Boot 4.1.0.
- React 19.2.6, TypeScript 5.9.3, and Vite 8.2.2.
- H2 persistence with Flyway.
- Maven builds the frontend and packages it into the Spring Boot JAR.
- Java package: `app.detour`.
- Current behavior includes account identity, fictional March 2027 catalog inventory, trips and alternatives, component search, planning and comparison, simulated booking, and cancellation.
- No Loomspan or other model integration is part of DeTour. Planning and ranking are deterministic application logic; exchange, disruption, and recovery are outside the initial release.

The completed Phase 0 baseline later confirmed that the previously noted `IntakeService.java` and `TripStore.java` changes were not present in its execution checkout. A future context must still run `git status` before implementation and must not overwrite or revert unrelated work. The clean-break policy authorizes removal of obsolete application paths through scoped tickets; it does not authorize broad worktree cleanup.

## Settled clarifications from final roadmap review

The following decisions were explicitly reviewed with the user. Treat them as requirements, not questions to reopen during ticket writing:

- **Version boundary:** Version 1 contains airfare, accommodations, and rental cars. Events are entirely Version 2. Current-release documents may link to the future roadmap but must not introduce Event schema, fixtures, APIs, UI, warnings, allowances, inventory, or booking behavior.
- **Trip creation:** destination, start/end dates, and traveler count are required before creating a Trip and its first component-empty Draft. Traveler ages and budget may be completed later but are required before promotion to Planned.
- **Planning readiness:** at least one reservable airfare, accommodation, or rental-car component is required before a Draft may become Planned or Booked.
- **Draft shared-detail changes:** before any Planned snapshot exists, traveler changes reprice and revalidate retained selections; invalid selections are removed. Destination/date changes remove incompatible selections. Every resulting price, capacity, eligibility, room-count, or removal change is summarized to the user.
- **Trip duplication:** after a Planned snapshot exists, shared-detail changes create a new Trip. The user selects which Planned snapshots to use as sources; each becomes a Draft. Draft, Booked, and Canceled alternatives are not copied by this Trip-level operation. Incompatible components are removed with reasons. A canceled Trip follows the same rule; if it has no selected/available Planned snapshot, duplicate only its shared details into one component-empty Draft and explain that no alternative was copied.
- **Booking history:** a Canceled Booking is an immutable historical booking snapshot, not an itinerary lifecycle state. The source Planned snapshot remains a separate alternative.
- **Time boundary:** a Trip and its unbooked alternatives expire at the start of the departure date in the departure location's timezone. With fixed origin PDX, use `America/Los_Angeles`.
- **Flight coverage:** all final arrivals must occur by March 31, 2027. The two-direct/two-one-stop fixture guarantee applies to direction/date combinations usable by a valid 1–14-night Trip, not literally every calendar date in both directions.
- **Cancellation cutoff:** Cancel Booking and Cancel Trip are available only before expiration. Expired and Past Bookings remain immutable history and never restore past inventory.
- **Rental behavior:** pickup and return occur at the destination airport, use local date/times within the Trip interval, and require return after pickup. Charge consecutive 24-hour cycles, rounding a partial final cycle up. Check unit availability over the complete interval. Default order is economy, standard, SUV; then lowest complete total; then immutable identifier.
- **Budget:** zero is valid, negative is invalid, and implementation must choose and document a safe upper bound for the integer-cent type. An absent Draft budget suppresses budget-fit ordering and remaining/overage presentation. A component's available budget excludes the component being searched or replaced.
- **Determinism:** every catalog ordering ends with an immutable identifier tie-breaker.
- **Clean break:** delete superseded Wayfarer code, routes, schemas, migrations, configuration, tests, scripts, documentation, and assets in the ticket that replaces them. Do not create compatibility endpoints, aliases, fallbacks, dual schemas, data migrations, deprecated wrappers, or transitional paths.
- **Release search scope:** remove obsolete Wayfarer/Loomspan references from executable code, configuration, tests, generated artifacts, and product-facing copy. Historical planning records may name the former system but do not govern implementation.

## Non-negotiable product boundaries

- Product name is **DeTour**; technical identifiers use `detour` and Java uses `app.detour`.
- There is no Loomspan, other AI framework, model endpoint, prompt, conversational interpreter, or API key in DeTour.
- Planning and ranking are deterministic and server-validated.
- Login is required. Accounts are ordinary self-service users; there is no administrator or role system.
- Every query and mutation is scoped to the authenticated owner in backend code.
- Every new account starts empty; no seeded user credentials or trips.
- Origin is fixed to PDX. Destinations are San Francisco, Munich, and Mexico City.
- All airfare, accommodation, and rental fixtures are limited to March 1–31, 2027.
- Suppliers and inventory are fictional; airport codes and geography are real.
- All money is USD integer cents. Displayed totals include applicable taxes and fees.
- Old Wayfarer database files, schema, fixtures, and Flyway history are disposable development state. Phase 1 replaces the migration chain with a fresh DeTour `V1`; developers perform an explicit local reset, while application startup never silently deletes files.
- Ordinary UI must not call the product a demo. The global collapsed About this demo side tab is the sole disclosure exception.

## Domain model in one view

```text
User
└── Trip (shared destination, dates, travelers, budget)
    ├── Draft itinerary (mutable, autosaved, may be incomplete)
    ├── Planned itinerary (immutable snapshot)
    ├── Planned itinerary (another alternative)
    └── Booked itinerary / Booking (at most one active per Trip)
```

- Destination, dates, and traveler count are required before creating a Trip and component-empty Draft.
- A user may start a component-empty Draft or duplicate an individual alternative into a Draft.
- Shared destination/date/traveler changes create a new Trip once any Planned snapshot exists. The user selects which Planned snapshots become source Drafts; incompatible components are omitted with an explanatory summary. The same rule applies when duplicating a canceled Trip.
- Users may keep making alternatives after booking, but cannot create a second active Booking.
- Upcoming/Past/Expired are derived from dates. They are not scheduled lifecycle transitions.
- A Planned snapshot can be compared but not edited; edit-by-duplication protects comparisons.
- At least one reservable airfare, accommodation, or rental-car component is required before a Draft may be Planned or Booked.
- Booking and cancellation are inventory-safe, atomic, idempotent operations.

## Cancellation terminology

Do not implement a generic ambiguous Cancel action:

- **Delete Draft:** remove one unfinished alternative; no inventory effect.
- **Delete Planned itinerary:** remove one snapshot after confirmation; no inventory effect.
- **Cancel Booking:** before the Trip expires, release inventory, retain history, and leave the Trip active. Then offer a saved alternative (duplicated/revalidated), a new Draft, or no immediate follow-up. Expired and Past Bookings are immutable history.
- **Delete Trip:** allowed only if no Booking has ever existed; permanently remove its Draft/Planned alternatives after showing their counts.
- **Cancel Trip:** before the Trip expires, use this when booking history exists. Cancel any active Booking and close the Trip atomically, retain all history, make alternatives read-only, and offer Duplicate into a new Trip.

## Fixture scale already approved

- Two direct and two one-stop choices per destination for every direction/date combination usable by a valid 1–14-night Trip that completes arrival by March 31.
- Connections: SFO through SEA or SLC; MUC through SEA or ORD; MEX through LAX or DFW.
- Same airfare for every traveler; every traveler reserves a seat.
- Two properties of each type (hotel, B&B, vacation rental) per destination.
- Economy, standard, and SUV rental inventory at each destination airport.
- Fixtures are compact Flyway SQL generated from recurring definitions/sequences rather than thousands of hand-authored inserts.

## Remaining decisions

The remaining Phase 7 **[OPEN QUESTION]** is final public authentication-page and About this demo copy. Proposed wording is in the Phase 7 document.

The Phase 7 visual direction is to refine the existing teal palette and Georgia headings with a text-only wordmark. Destructive confirmations remain modal. The delivery target is a release-ready packaged application, not deployment.

Earlier implementation-design annotations have been resolved:

- The earlier phase annotations for catalog details, profile card hierarchy, and confirmation references are resolved by their current implementations. Consult fixtures and UI/code for exact values; they are not Phase 7 decisions.
- The development reset command and default database target are documented in the repository README and `scripts/reset-detour.ps1`.

These implementation choices do not change the settled product behavior in the roadmap.

## Recommended next steps

1. Draft Phase 7 tickets from the current code and the settled decisions in the Phase 7 document.
2. Finalize the proposed public and About copy during the copy pass.
3. Complete the Phase 7 experience, documentation, accessibility, and packaged-application verification work.
4. Keep [README.md](README.md), this guide, and the relevant phase file synchronized whenever a ticket resolves an annotation or changes scope.

## Ticket-authoring contract

Each implementation ticket should be independently understandable without the prior conversation and should contain:

1. **Identity and outcome:** phase/work-package identifier, concise title, and a user- or system-observable outcome.
2. **Dependencies:** prerequisite tickets, schemas, APIs, fixtures, or decisions; distinguish hard dependencies from work that can run in parallel.
3. **Settled requirements:** quote or paraphrase the relevant roadmap behavior precisely, including edge cases and terminology. Link the authoritative roadmap and phase sections.
4. **Scope:** enumerate data/migration, backend/domain, API/security, frontend/interaction, documentation, and cleanup work that actually belongs in the ticket. Mark unaffected layers explicitly when useful.
5. **Acceptance criteria:** concrete, testable behavior including success, validation, empty/error, authorization, concurrency, restart/persistence, responsive, and accessibility cases as applicable.
6. **Verification:** name the unit, integration, frontend, migration, concurrency, packaged-application, or manual checks needed. Avoid a generic “add tests” requirement.
7. **Clean-break and reset impact:** identify superseded paths to delete, clean-database expectations, the explicit development reset required by schema changes, and verification that no compatibility alias, fallback, migration, or dual path was introduced. Rollback means reverting the code change and recreating disposable development data, not preserving old application contracts.
8. **Exclusions:** call out adjacent later-phase work and deferred enhancements, especially Version 2 Events, so ticket scope cannot expand by implication.
9. **Open implementation choices:** list only decisions genuinely owned by the ticket. Propose a simple default where the roadmap permits it; do not relabel settled product behavior as an implementation choice.
10. **Completion evidence:** specify what a reviewer should be able to inspect or run to confirm completion.

Ticket sizing should favor a cohesive behavior that can be verified end to end. Avoid tickets that merely create disconnected layers, and avoid combining unrelated work solely because it belongs to the same phase. Security, ownership scoping, server-side validation, accessibility, deterministic behavior, and relevant tests belong in the ticket that introduces the behavior rather than in a later cleanup ticket.

## Verification expectations for future tickets

- Backend unit and HTTP integration tests.
- Authorization/isolation tests using at least two users.
- Flyway clean-database migration and fixture-integrity tests.
- Deterministic ranking/filtering/tally tests.
- Concurrency and idempotency tests for booking and cancellation.
- Controllable-clock tests for Upcoming, Past, and Expired views.
- Frontend build and focused interaction tests.
- Responsive and keyboard-accessibility verification.
- Packaged-JAR startup, restart, persistence, and no-model-credential verification.
- Scoped repository searches confirming that obsolete Loomspan, Wayfarer, route, compatibility, and misplaced demo references are gone from executable/product-facing surfaces. Historical planning records may retain former-system terminology but are not implementation authority.
