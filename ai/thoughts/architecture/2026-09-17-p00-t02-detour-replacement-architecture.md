# DeTour Replacement Architecture Decision Record

## Decision

DeTour is the replacement application and the source of truth for future implementation. The DeTour roadmap and continuation guide govern target behavior; the former Wayfarer application is evidence only for identifying capabilities to retain, replace, or remove.

DeTour uses deterministic Java application services for planning, ranking, explanation, and validation. Loomspan is removed and no substitute AI framework, model endpoint, prompt layer, natural-language interpreter, conversational interpreter, or API key belongs in the target architecture.

This record establishes an architecture boundary. It does not rename current code, design schemas or APIs, create migrations, select catalog fixtures, or design frontend visuals.

## Authoritative Inputs

1. `ai/thoughts/phases/README.md` is the authoritative product roadmap.
2. `ai/thoughts/phases/CONTINUATION.md` supplies the settled replacement boundaries and implementation handoff details.
3. `ai/thoughts/phases/phase-0-baseline-and-boundaries.md` records the completed Phase 0 replacement boundary and notes the superseded persistence-preservation decision.
4. `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` supplies baseline platform and test-disposition evidence.

Current Wayfarer YAML skills, demo documents, and UI copy are not DeTour requirements. They may be cited only as legacy material to remove or replace.

## Naming Conventions

| Concern | Selected convention | Later-ticket boundary |
| --- | --- | --- |
| Java package namespace | `app.detour` | Future Java sources move to this namespace. |
| Maven group ID | `app.detour` | Use this group ID when the Maven coordinates are changed. |
| Maven artifact ID | `detour` | Maven artifact ID and packaged-output stem are `detour`. |
| Spring application | `detour` | The Spring application name is `detour`. |
| Configuration prefix | `detour` | Application-owned configuration properties use the `detour` prefix. |
| Environment variables | `DETOUR_` | Environment-backed configuration uses the `DETOUR_` prefix. |
| Frontend package/application | `detour-frontend` | The frontend package and application identifier are `detour-frontend`. |
| Packaged output | `detour` | With the existing version unchanged, the expected JAR is `target/detour-0.1.0-SNAPSHOT.jar`. |

No layer retains `wayfarer` as a target technical identifier. Phase 1 selects the DeTour H2 database path under the `DETOUR_` convention, replaces the old migrations with a fresh `V1` lineage in the standard Flyway location, and documents the explicit development reset required to discard obsolete local data. There is no database migration or compatibility path, and application startup does not silently delete files.

## Retained Platform Baseline

Retain Java 21, Spring Boot, React, H2, Flyway, Maven, and the existing shape in which Maven builds the frontend and produces one packaged application JAR. P00-T01 found no concrete incompatibility with this platform baseline.

P00-T01 could not start Maven test or package commands because Java was not available on `PATH`; that environmental limitation is not an incompatibility. Its `npm.cmd run build --prefix frontend` result passed. A materially different platform requires evidence of a concrete incompatibility and developer review before selection.

## Capability Disposition Matrix

| Capability category | Disposition | Target DeTour rule |
| --- | --- | --- |
| Reusable inventory and transaction behavior | Keep general properties; replace contracts and fixtures | Preserve transactional, concurrency-safe, idempotent inventory reservation; revalidate authoritative price, availability, ownership, and eligibility server-side. Do not retain the Wayfarer scenario or route contract. |
| Trip planning and validation | Replace | User-owned Trips and progressive itinerary alternatives replace the fixed scenario. Deterministic Java services own planning and validation. |
| Model orchestration and natural-language changes | Remove | Remove Loomspan orchestration, skills, model calls, model traces, model-produced change drafts, and natural-language or conversational interpretation. Deterministic Java services own explicit planning, ranking, explanation, and validation workflows. |
| Booking | Replace | A Booking is an inventory-reserving, immutable booked snapshot created from a valid Planned itinerary; its reservation is atomic, concurrency-safe, and idempotent. |
| Exchange | Remove | Booking modification and exchange are deferred enhancements and have no Version 1 replacement flow. |
| Disruption and recovery | Remove | Supplier disruption and recovery behavior are deferred enhancements and have no Version 1 replacement flow. |
| Cancellation | Replace | Cancel Booking atomically restores applicable inventory before expiration, retains immutable Canceled Booking history, and leaves the Trip active. Cancellation is not a generic itinerary state. |
| Authentication | Replace | Login, registration, logout, and backend-enforced per-user data isolation replace unauthenticated Wayfarer routes. |
| Persistence | Replace destructively | Delete the Wayfarer migrations and create a fresh DeTour `V1` lineage. Existing development databases and Boston--New York records are disposable and require an explicit reset; no import, upgrade, dual-schema, or fallback path exists. |
| Frontend flows | Replace | Authenticated profile, Trip, alternative, component-selection, comparison, booking, and cancellation flows replace the fixed workspace, model conversation, trace display, exchange, and recovery UI. |
| Observability | Remove Loomspan-specific behavior; replace only when a later ticket defines application observability | Remove Loomspan execution-trace persistence, provider observability, and model-trace UI. This record does not prescribe a replacement observability design. |
| Scripts | Replace or remove in the owning ticket | Remove model-skill-manifest generation and obsolete Wayfarer run or cleanup scripts when their paths are replaced. A DeTour development-reset script may explicitly remove only the documented local DeTour database targets after confirmation; it is not a compatibility mechanism. |
| Documentation | Replace | Product-facing documentation describes DeTour and its deterministic architecture; retained historical planning and migration references may identify legacy removal boundaries. |
| Tests | Rebuild or remove by P00-T01 disposition | Reimplement required transaction and HTTP invariants with new DeTour contracts and fixtures. Delete obsolete Wayfarer scenario and Loomspan/model tests with their owning paths; do not retain legacy test harnesses or adapters. |

## Domain Ownership and Lifecycle

### Ownership boundary

The enforceable relational ownership path is `User -> Trip -> Itinerary/Booking and downstream data`. Every query and mutation for user data must be scoped to the authenticated User through that relational path.

- A User owns Trips and all downstream user data through the enforceable relational path.
- A Trip owns destination, dates, travelers, overall budget, and all itinerary alternatives for one shared travel intent.
- An Itinerary owns its selected airfare, stay, rental car, and calculated totals.
- A Booking is the inventory-reserving, immutable booked snapshot. There is at most one active Booking per Trip.
- Application-owned catalogs and inventory are shared reference data, are not user-owned, and are never reached by treating catalog ownership as Trip or User ownership.

This boundary deliberately does not choose table shapes, foreign-key columns, repository APIs, or HTTP endpoints. Later implementation must make the stated path enforceable in relational persistence and backend authorization.

### Lifecycle boundary

- A Draft itinerary is mutable, autosaved, and may be incomplete after the required shared Trip details are supplied.
- A Planned itinerary is a stable snapshot. It is changed by duplicating it into a new Draft rather than editing it in place.
- Booking starts from a valid Planned itinerary, but a Booking is distinct from an itinerary state. The booked snapshot is immutable and reserves inventory.
- An active Booking prevents a second active Booking for the same Trip while leaving unselected Planned alternatives visible.
- Cancel Booking produces immutable Canceled Booking history and releases applicable inventory atomically before expiration. A Canceled Booking is not a generic itinerary state; the source Planned alternative remains separate.
- Exchange, supplier disruption, and recovery do not alter this lifecycle in Version 1 because they are deferred.

## Fresh-test rule

P00-T01 classified all 41 legacy tests. The classification is requirements evidence, not a direction to preserve test code. Later tickets recreate the **6 reusable** inventory and HTTP properties from scratch using DeTour contracts and fixtures: atomic reservation, concurrency safety, idempotency, stale quote or state rejection, cross-trip isolation, and useful malformed-input/missing-resource HTTP behavior.

Later tickets replace the **19** fixed-scenario tests with DeTour behavior and fixtures. They remove the **16** Loomspan/model-coupled tests together with the obsolete model paths. This classification preserves general transaction and HTTP protection without preserving Wayfarer endpoints, Boston--New York data, or model behavior.

## Deferred Detail

This record leaves the following work to its owning tickets: detailed schemas, the fresh Flyway lineage, DeTour database path and explicit development-reset instructions, APIs, authentication mechanics, catalog fixtures, workflow details, frontend visual design, exact application observability, and Version 2 Events. Deferred exchange, disruption, recovery, and AI-generated capabilities are not implicit substitutes for the removed Wayfarer behavior.

## References

- `ai/thoughts/tickets/2026-09-17-p00-t02-define-replacement-architecture.md`
- `ai/thoughts/research/2026-09-17-p00-t02-define-replacement-architecture.md`
- `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md`
- `ai/thoughts/phases/README.md`
- `ai/thoughts/phases/CONTINUATION.md`
- `ai/thoughts/phases/phase-0-baseline-and-boundaries.md`
