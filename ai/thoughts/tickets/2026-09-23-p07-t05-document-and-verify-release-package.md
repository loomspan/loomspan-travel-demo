# P07-T05 — Document DeTour and Verify a Release-Ready Package

## Outcome

A developer can set up, reset, build, verify, package, restart, and inspect DeTour from current documentation, and the packaged application preserves users and bookings without model credentials or obsolete product behavior.

## Requirements

- Replace stale repository README guidance with accurate setup, configuration, architecture, fixture, workflow, database reset, and verification instructions for the implemented DeTour application. Document the explicit confirmation-gated development database reset and clean-break policy; application startup must never delete a database file.
- Verify the released scope is airfare, accommodations, and rental cars for PDX departures and the three supported March 2027 destinations. Version 2 Events, AI/model integrations, real suppliers, payment, exchanges, and disruption/recovery flows are outside this package.
- Run and record backend, frontend, HTTP/integration, inventory concurrency and idempotency, accessibility, responsive, clean-database migration, packaged-JAR startup, restart, and persistence verification. Use controllable time for Upcoming, Past, Expired, and cancellation-cutoff behavior. Confirm the first-time registration-to-plan path needs no external instructions.
- Verify owner isolation and server-side revalidation remain enforced for prices, totals, eligibility, availability, and booking/cancellation. Booking and cancellation must remain atomic and idempotent; no Phase 7 polish may weaken them.
- Search executable code, configuration, tests, scripts, generated artifacts, and product-facing copy for obsolete Wayfarer/Loomspan, old routes, compatibility paths, and misplaced general demo references. Historical planning records may retain former-system terms. Remove any residual obsolete implementation content in the ticket that discovers it; do not add aliases, fallbacks, dual schemas, or data migration for disposable development state.
- Deliver a release-ready packaged application and verification evidence. Deployment, native apps, dark mode, localization, marketing pages, public catalog browsing, and Version 2 Events are outside Phase 7.

## Acceptance criteria

- [ ] The README accurately explains current DeTour behavior and gives working setup, configuration, fixture inspection, build/test/package, restart, and explicit development-reset instructions.
- [ ] A clean database starts from the DeTour Flyway lineage, a new user can register and complete a representative plan, and the packaged JAR works without API keys or model credentials.
- [ ] Restart requires a new login while preserving registered users, Trips, Planned/Booked snapshots, booking references, and cancellation history in the configured persistent database.
- [ ] Backend, frontend, integration, concurrency/idempotency, accessibility, responsive, and packaged-application checks have inspectable results; failures are resolved before declaring the package release-ready.
- [ ] A scoped repository search finds no obsolete executable/product-facing Wayfarer or Loomspan behavior, compatibility path, or misplaced general demo disclosure.
- [ ] The resulting package is release-ready without requiring deployment to a hosting service.

## Context

- **Phase/work package:** Phase 7, 7.5 and Phase 7 exit criteria. Authoritative sources: [roadmap](../phases/README.md), [continuation guide](../phases/CONTINUATION.md), and [Phase 7](../phases/phase-7-product-experience-and-release.md).
- **Hard dependencies:** Phases 1–6 and P07-T01 through P07-T04 are complete. This is the final release gate.
- **Scope boundary:** Owning implementation tickets should have removed superseded behavior when replacing it. This gate verifies that boundary and corrects any residual finding rather than preserving a compatibility layer.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This is broad release assurance across persistence, packaged operation, security, concurrency, documentation, and obsolete-path removal.
- **Reassessment triggers:** none.
