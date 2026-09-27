# Public Trip Planning Journey Integration Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-25-pr0-integrate-public-trip-planning-journey.md`
- Research: `ai/thoughts/research/2026-09-25-pr0-integrate-public-trip-planning-journey.md`
- Outcome: Close cross-ticket gaps and demonstrate the public-to-booked named Trip journey on the PR1–PR5 implementation.

## Current State
`App` and `TripStartForm` keep guest input in memory and require another explicit submission after authentication. `TripService.create` persists one Working row; option routes save and update named snapshots. `BookingTransactionExecutor` resolves the selected option's dates. V19 migrates old alternatives. The remaining conflicts are the reachable legacy `/drafts/{id}/plan` route, dormant frontend Draft/Planned actions and props, generic names in `duplicateTrip`, the old-flow packaged verifier, and README workflow text. Existing frontend and Java tests cover components separately; the packaged verifier still promotes a Draft.

## Desired End State
A guest can begin on public Home, enter a Trip on Trips, authenticate and explicitly save exactly one Trip with one Working plan. That plan can change in place; two named options can carry different dates, be compared, and one can be updated or booked without altering booked records. Failed saves preserve unsaved input and permit retry. Existing data and bookings survive V19. Product entry points and docs use Trip, Working plan, and Saved option. Backend persistence names such as `draft` and `planned` may remain where renaming would add migration risk; they must not expose an alternate user workflow.

## Scope
### In scope
- Remove reachable duplicate creation/promotion actions, preserve the one-Working invariant, and align revision copies and user-visible messages.
- Add integrated API and UI regressions for public entry, named options, booking, failure/retry, migration, ownership, and date semantics.
- Update the packaged release verifier and README to the supported journey.

### Out of scope
- Anonymous persistence, expanded catalog/destinations, per-option traveler variation, revision history, schema redesign, or a new browser automation framework.

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis
Legacy `/drafts/{id}/plan` can still create an unnamed option, bypassing the named-option gate. Retiring that write changes an HTTP contract, so replace its old tests and verify it fails without mutation; retain `/drafts/{id}` selection/readiness routes used for the sole Working row. `duplicateTrip` currently discards source option names; carry names through revision creation while using the revision dates and existing revalidation, then verify unique ownership and no booked source mutation. Guest values are intentionally memory-only and survive in-app authentication, not a reload. The release verifier uses an isolated H2 file and loopback server, so it is safe to extend for cross-component coverage. Migration must be tested from V18 data and not edited retrospectively after release; V19 already contains the upgrade.

## Implementation Approach
During implementation, the V18-to-V19 fixture exposed a user-visible V19-generated name, `Option from Draft <id>`. A forward V20 migration renames only that generated pattern to `Recovered option <id>`; it leaves V19 unchanged, preserves option and booking identities, and leaves unrelated user-chosen names intact. The migration test stages a V19 database with a user-renamed booked option before applying V20. V19 did not record provenance for these names, so a traveler who deliberately chose the exact `Option from Draft <digits>` pattern before V20 would be indistinguishable from a generated name and would also be renamed.

Treat the named `/options` route as the only creation/update route for Saved options. Remove stale UI handlers and props rather than maintaining unreachable alternate actions. Make legacy promotion return a nonmutating conflict (or remove its mapping if no caller requires a structured response); the chosen behavior must be asserted and all in-repository callers/tests moved to `/options`. Retain low-level `draft` terminology in persistence/API fields that carry the Working row to avoid an unrelated schema migration. Preserve source option names in trip revision copies while revalidating under revision dates; the user supplied a revised date range, and the revision modal already describes that operation. Update the packaged verifier to create a named Trip, use `/options`, exercise two dates and booking against a chosen option, and retain restart and isolation checks.

## Phase 1: Retire competing legacy actions
### Changes
- [x] `src/main/java/app/detour/trip/TripController.java` and `TripService.java` — stop `/drafts/{draftId}/plan` from inserting an option; preserve Working selection and readiness routes. Make the obsolete action a clear, nonmutating response.
- [x] `frontend/src/components/TripWorkspace.tsx`, `AlternativeCard.tsx`, and `frontend/src/api/tripsApi.ts` — remove dormant Draft duplication/promotion controls and handlers, including stale overage flow tied only to promotion; keep named-option save/update and booking actions.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` and affected frontend tests — assert legacy promotion cannot create an option, and migrate product-flow assertions to named `/options`.
### Automated verification
- [x] `./mvnw.cmd -DskipFrontend=true -Dtest=TripApiIntegrationTest test` — old endpoint cannot mutate, named endpoint remains usable.
- [x] `npm test -- --run` from `frontend` — current option controls remain reachable and old controls absent.
### Optional developer checks
- [x] None.

## Phase 2: Resolve cross-ticket revision and copy consistency
### Changes
- [x] `src/main/java/app/detour/trip/TripService.java` — carry each selected source option's user name into its copied option; keep one new Working row and revalidate selections against the revision dates.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — assert copied names, dates, one Working row, component revalidation, source/booked immutability, ownership and version conflicts.
- [x] `frontend/src/components/TripRevisionModal.tsx` and `TripWorkspace.tsx` — ensure revision and cancellation copy wording accurately describes new option dates and the existing Working plan; remove stale user-visible Draft/Planned messages.
### Automated verification
- [x] `./mvnw.cmd -DskipFrontend=true -Dtest=TripApiIntegrationTest test` — revision creates named copies with correct identities and dates.
- [x] `npm test -- --run` from `frontend` — revision and cancellation messages are accurate.
### Optional developer checks
- [x] None.

## Phase 3: End-to-end acceptance evidence and documentation
### Changes
- [x] `frontend/src/PublicTripFlow.test.tsx`, `ProgressiveTripBuilder.test.tsx`, and `ItineraryComparisonAndBookingReview.test.tsx` — fill cross-flow gaps: guest/auth handoff and retry, Working save status, two differently dated named options, comparison, edit-in-place versus explicit copy, booking and keyboard/mobile states at component boundaries.
- [x] `src/test/java/app/detour/trip/TripModelForwardMigrationIntegrationTest.java` and `src/test/java/app/detour/booking/BookingApiIntegrationTest.java` — assert migrated populated options and booked references survive, empty drafts are removed, and option-specific dates govern booking/expiry.
- [x] `scripts/verify-packaged-release.mjs` — replace Draft promotion with named option creation; prove a complete API journey through two option dates, selected-option booking/cancellation, restart, authentication/CSRF and ownership using isolated data.
- [x] `README.md` — replace the Draft/Planned workflow with public Home, Trips, Working plan, Saved options, Profile and explicit save; document optional budget and supported fixture dates.
### Automated verification
- [x] `npm test` from `frontend` and `npm run build` — UI assertions and type/build pass.
- [x] `./mvnw.cmd clean verify` — backend, migration, booking and packaged app build pass.
- [x] `./scripts/verify-packaged-release.ps1` — isolated packaged API journey passes.
### Optional developer checks
- [ ] Use a browser at mobile width and keyboard-only navigation to inspect focus order, dialogs, copy and layout; report as optional observation rather than claiming automation.

## Test Strategy
See companion testing plan. Begin with a failing legacy-promotion assertion and a named revision-copy assertion; add focused React and Java integration cases, then run safe broad suites and isolated packaged verification. Reuse seeded March 2027 catalog and existing test clients; never use the default development database.

## Acceptance-Criteria Traceability
| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Guest starts, authenticates, saves one owned Trip/Working | `App`, `TripStartForm`, `TripService.create` | `PublicTripFlow.test.tsx`; `TripApiIntegrationTest`; packaged verifier |
| Change Working, save two dates, compare, update, book | `TripWorkspace`, option routes, `BookingTransactionExecutor` | `ProgressiveTripBuilder.test.tsx`; comparison/booking tests; packaged verifier |
| Session ends mid-flow; retry without duplicates | `TripStartForm` error and retained state, auth handoff | `PublicTripFlow.test.tsx`; API ownership/count checks |
| Migrated data and bookings retained | V19 migration and trip repository | `TripModelForwardMigrationIntegrationTest` and booking integration |
| Public/private navigation, mobile/keyboard, ownership, dates, cancellation, concurrency | App navigation, option/booking services | frontend interaction tests; booking/API suites; optional browser observation |
| Consistent copy; no old creation path | workspace/card cleanup, legacy route, README | UI absence assertions; legacy route regression; README review |

## Risks and Rollback/Recovery
The legacy promotion route is a contract change for clients outside this repository. No external client is documented, but a structured conflict is preferable to silently creating generic options; revert the route change if an actual supported client is identified, then require names through a compatibility adapter. For release verification failures, retain the isolated verifier log and fix the smallest failing layer. Do not roll back or reset user data; V19 migration tests must establish safe upgrade before release.

## References
- `ai/thoughts/tickets/2026-09-25-pr0-integrate-public-trip-planning-journey.md`
- `ai/thoughts/research/2026-09-25-pr0-integrate-public-trip-planning-journey.md`
- `src/main/java/app/detour/trip/TripService.java`
- `frontend/src/components/TripWorkspace.tsx`
- `scripts/verify-packaged-release.mjs`
