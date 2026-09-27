# Save the Working Plan and Manage Options Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-25-pr3-save-working-plan-and-manage-options.md`
- Research: `ai/thoughts/research/2026-09-25-pr3-save-working-plan-and-manage-options.md`
- Outcome: Persist edits in one Working plan and create, load, or replace named dated Saved options only through explicit user actions, while preserving booking and concurrency guarantees.

## Current State

PR2 created exactly one dated Draft row per Trip and versioned, named, dated Planned snapshot rows. `TripService.changeWorkingDates` mutates the one Draft, but `TripWorkspace.executeAutosave` still uses `replaceSharedDetails`, which refuses date edits after any option exists. `TripService.promoteDraft` appends an automatically named option only after booking-like readiness, including a budget; there is no load-option or replace-option route. `JdbcTripRepository.advanceVersionForOption` guards parent/option versions and any booking history, but currently only metadata update primitives use it. Search, selection, readiness, and snapshot resolution still reference `trip.startDate`/`endDate`; those dates mirror Working, yet each option has its own dates. Booking and cancellation already resolve the selected/booked option's dates. Existing extra-Draft routes reject calls, and V19 enforces `UNIQUE (trip_id)` on Drafts. `TripService.duplicateTrip` creates one Working plan and inserts selected sources as options.

## Desired End State

- A changed Working value or component persists to the same row; no-op, read, search, and local pre-creation input do not create a plan or option. Saving, saved, error, retry, and conflict states reflect the actual request result.
- A named option is inserted once for an explicit save request if at least one selected component remains structurally valid. A missing budget stays null and suppresses budget-fit figures. Snapshot prices are refreshed from catalog at save time; inventory and eligibility remain booking gates.
- An option can be copied into the existing Working plan. Different Working contents require an explicit keep-as-option or replace choice; cancel writes nothing. Update this option replaces only an unbooked option in place after validation and repricing. Save as new option branches without altering its source. Any option referenced by booking history remains immutable.
- Working date changes revalidate selections, return removals and adjustments including price/eligibility changes, and do not alter other options. Booking, expiry, and cancellation use the relevant dated record.
- One Working plan remains the database invariant across all paths, including retries, cancellation continuation, and concurrent calls. No itinerary revision log or additional mutable Draft is introduced.

## Scope

### In scope
- Working autosave and date mutation API/client wiring; search and selection date consistency.
- Named option creation, explicit option-to-Working load, in-place option update, and immutable booking-history guard.
- Revalidation summaries, snapshot repricing, response/client contracts, minimal actionable UI, and focused regression tests.

### Out of scope
- Supported catalog changes, anonymous Trip persistence, per-option party/age variation, collaborative merge, user-facing itinerary revision history, and the broader PR5 presentation redesign.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

- The persisted option graph is a booking input. Updating an option must replace its component snapshot children atomically, preserve its public ID, and be refused whenever any booking record refers to it, including canceled history. Concurrent booking/update attempts must serialize through guarded writes and transaction locks so booking cannot observe a partially replaced graph.
- Existing `advanceVersionForDraft` advances only the parent; `updateWorkingDates` advances the Draft separately. All Working content replacement paths need both versions checked and advanced exactly once. A stale tab must get a conflict rather than silently overwrite.
- The current readiness function combines structural validity with budget, live inventory, age eligibility, and expiration. Reusing it unchanged would incorrectly block option saving. Separate the option-save structural check from booking readiness; preserve all booking checks in `BookingTransactionExecutor`.
- Snapshot resolution currently uses Trip compatibility dates. Pass the target Working/option dates explicitly so price facts and components correspond to that dated record. Do not mutate Saved option facts during Working date edits or reads.
- Keep the PR2 legacy `drafts`/`planned` response aliases while the client migrates to `workingPlan`/`savedOptions`; changes to their meaning must be tested. The database unique constraint remains the final cardinality guard.

## Implementation Approach

Keep the existing Draft/Planned persistence model. Use explicit API actions for create, load, and update. `TripService` owns structural validation, date revalidation, ownership, and conflict handling; `JdbcTripRepository` owns atomic graph persistence and version guards. A load request will carry the Working version, the source option version, and an explicit replacement confirmation. The UI first compares Working dates and selected component identities to the source; if different, it offers to save current Working as a new named option, then load, or to replace explicitly. The server independently requires explicit replacement for differing content so another client cannot bypass the confirmation. The UI must finish or stop pending autosave before an explicit load/save/update and must not fire a stale queued save afterward.

For option creation/update, validate at least one component and coherent catalog identity, route, dates, and capacity. Re-resolve chosen components against catalog facts and capture current prices. Live inventory and booking eligibility are reported as unavailable or ineligible for booking but are not a new option-save prerequisite. The current promotion readiness checks adult/driver eligibility and availability, while `BookingTransactionExecutor` checks departure, inventory, and payment simulation; move or repeat the former checks at the actual booking boundary so relaxing option save never relaxes booking. Preserve idempotency and ownership checks. If catalog structure cannot produce a valid selected component, return field-level validation without writing an empty option. Keep an option update tied to the option explicitly selected for editing; Working edits alone never update it.

## Phase 1: Make Working mutations date-aware and conflict-safe

### Changes
- [x] `src/main/java/app/detour/trip/TripService.java` — make the Working row's dates the source for search, selection, readiness, and `resolveSelectionsForPromotion`; route date edits through the same revalidation path whether submitted alone or with shared details. Preserve version guards and ensure every changed Working value advances its Draft version. Do not reject Working date edits merely because options exist.
- [x] `src/main/java/app/detour/trip/JdbcTripRepository.java` and `src/main/java/app/detour/trip/TripRepository.java` — make snapshot resolution accept target dates explicitly; keep Trip compatibility dates synchronized with Working, and ensure paired parent/Draft version updates are transactional.
- [x] `src/main/java/app/detour/trip/TripRequests.java` and `src/main/java/app/detour/trip/TripController.java` — expose a single coherent Working mutation contract with expected parent and Draft versions; retain owner-scoped validation and clear conflict codes.
- [x] `frontend/src/api/tripsApi.ts` and `frontend/src/components/TripWorkspace.tsx` — send both versions for changed Working values, separate local no-op detection from writes, serialize save and component actions, and preserve accurate saving/saved/error/retry/conflict states. Display server `revisionSummary` after date changes.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java` and `frontend/src/ProgressiveTripBuilder.test.tsx` — cover one-row persistence, post-option Working date edits, no-op behavior, stale two-tab writes, and visible save states.

### Automated verification
- [x] `mvn -q -DskipFrontend=true -Dtest=TripApiIntegrationTest test` — Working mutations and stale writes pass.
- [x] `npm test -- --run ProgressiveTripBuilder.test.tsx` from `frontend` — save-state and no-op behavior pass.

### Optional developer checks
- [ ] With two browser tabs, edit the same Working plan and confirm the stale tab offers reload/retry rather than replacing the first tab's work.

## Phase 2: Add explicit named option creation and snapshot replacement

### Changes
- [x] `src/main/java/app/detour/trip/TripRequests.java` and `src/main/java/app/detour/trip/TripController.java` — add strict create-option and update-option payloads with name, expected parent/Draft/option versions, and route handlers. Reject blank/oversized names and missing required fields.
- [x] `src/main/java/app/detour/trip/TripService.java` — split structural option-save validation from full booking readiness; reject an empty or structurally invalid Working selection before any write. Save with Working dates and current resolved price facts. For update, revalidate/reprice and target only the requested unbooked option, keeping its ID; reject a booked-history option. Return a meaningful summary for removed or adjusted components.
- [x] `src/main/java/app/detour/trip/JdbcTripRepository.java` and `src/main/java/app/detour/trip/TripRepository.java` — add one-transaction replacement of the target option's child snapshots and metadata under parent/option version and no-booking guards; reuse existing insert snapshot construction. Ensure SQL failure rolls back parent/option version changes and all child writes. Keep canceled booking references immutable.
- [x] `src/main/java/app/detour/trip/ItineraryTallyEngine.java` and response mapping as needed — keep null budget distinct from zero and avoid budget-fit presentation when absent.
- [x] `src/main/java/app/detour/booking/BookingTransactionExecutor.java` — retain inventory, deadline, and payment checks and add any eligibility check previously guaranteed only by promotion readiness, including adult/driver rules, for a saved option that can now reach booking without that gate.
- [x] `src/test/java/app/detour/trip/TripApiIntegrationTest.java`, `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java`, and `src/test/java/app/detour/booking/BookingApiIntegrationTest.java` — cover empty rejection, named one-row creation, null budget, repriced snapshots, in-place replacement, rollback, history immutability, and booking invariants.

### Automated verification
- [x] `mvn -q -DskipFrontend=true -Dtest=TripApiIntegrationTest,DraftReadinessAndPlannedSnapshotIntegrationTest,BookingApiIntegrationTest test` — option persistence and booking boundaries pass.

### Optional developer checks
- [ ] None.

## Phase 3: Load options into the sole Working plan and expose explicit choices

### Changes
- [x] `src/main/java/app/detour/trip/TripRequests.java`, `src/main/java/app/detour/trip/TripController.java`, and `src/main/java/app/detour/trip/TripService.java` — add option-to-Working load with source version, both Working versions, and explicit confirmation when the source differs. Revalidate source selections for Working dates, copy catalog IDs/time selections into the one Draft, return removals/adjustments, and never mutate the source option. Cancel is a client-only no-op.
- [x] `src/main/java/app/detour/trip/JdbcTripRepository.java` and `src/main/java/app/detour/trip/TripRepository.java` — replace the one Draft's selection children and dates atomically; never insert another Draft. Keep the unique constraint and existing rejecting Draft-creation routes.
- [x] `frontend/src/api/tripsApi.ts`, `frontend/src/api/tripsApi.test.ts`, and `frontend/src/components/TripWorkspace.tsx` — provide Save as new option, Open for editing, Update this option, and a confirmation flow for differing Working content. Block explicit actions while unsaved/in-flight changes remain, offer save-current-first, cancel without a request, show validation/revalidation outcomes, and handle 409 conflicts.
- [x] `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/ProgressiveTripBuilder.test.tsx`, and `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — cover user choices, source immutability, cardinality, and race/conflict responses.

### Automated verification
- [x] `mvn -q -DskipFrontend=true -Dtest=TripApiIntegrationTest test` — load and update lifecycle tests pass.
- [x] `npm test -- --run tripsApi.test.ts ProgressiveTripBuilder.test.tsx ItineraryComparisonAndBookingReview.test.tsx` from `frontend` — explicit actions and status flows pass.

### Optional developer checks
- [ ] Inspect the load confirmation in the browser and confirm the destination option, current Working contents, and replace effect are understandable.

## Phase 4: Reconcile lifecycle and verify the full flow

### Changes
- [x] `src/main/java/app/detour/trip/TripService.java`, `src/main/java/app/detour/booking/BookingTransactionExecutor.java`, and `frontend/src/components/TripWorkspace.tsx` — make expiry and cancellation follow-up use the relevant Working/option/booked dates. Replace the existing post-cancellation create-Draft/duplicate-alternative actions with explicit load into or continuation of the sole Working plan.
- [x] `src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java`, `src/test/java/app/detour/trip/TripModelForwardMigrationIntegrationTest.java`, and `frontend/src/PublicTripFlow.test.tsx` — prove canceled-history immutability, date-specific deadlines, one Working row after continuation and migration, and no hidden Draft creation.

### Automated verification
- [x] `mvn -q -DskipFrontend=true test` — backend suite passes, including booking, migration, and concurrency cases.
- [x] `npm test` and `npm run build` from `frontend` — frontend tests and production build pass.

### Optional developer checks
- [ ] In a configured non-production app, exercise create/edit/save/open/update/book/cancel/reopen flow and inspect displayed messages.

## Test Strategy

Start with a failing integration test for saving one named option from a component-bearing, budgetless Working plan. Add API integration tests for record counts, versions, snapshots, date isolation, rejected mutations, and races. Add UI tests for no-op autosave, save-state transitions, and explicit confirmation. Retain booking and migration suites as the broad regression gate. Use H2 test databases and mocked frontend API calls; do not book against a live environment.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| One Working row and accurate autosave states | Working mutations, API/client serialization, V19 uniqueness | Trip API concurrency and frontend save-state tests |
| Exactly one explicit named option; no empty option | create-option structural validator and named snapshot insert | empty rejection, named count, null-budget tests |
| Load, replace unbooked, or branch without silent overwrite | explicit load/update APIs and UI confirmation | load/cancel/replace/branch API and UI tests |
| Date edits revalidate and isolate options | Working date source and summary | component removal/adjustment and unchanged-option tests |
| Booked/history options fixed | no-booking update guard and booking FK | active/canceled booking mutation rejection tests |
| Option-specific booking/expiry/cancellation | dated option/booked records | booking and cancellation clock tests |
| Never multiple Working plans | reject legacy extra-Draft paths, unique constraint | creation/duplicate/cancel/race/migration count tests |

## Risks and Rollback/Recovery

The highest risk is partially replacing a booking candidate or writing a stale option over another tab. Use one transaction for guarded version and snapshot writes, and fail closed on unknown source IDs, booking history, or invalid catalog facts. A failed save leaves Working and options unchanged; the client retains local edits and offers retry/reload. Since PR2 already has the required tables and uniqueness constraint, no schema migration is expected; if implementation reveals a schema need, add a forward migration and migration test rather than editing V19.

## References

- Ticket and research above.
- `src/main/java/app/detour/trip/{TripService,TripController,TripRequests,TripRepository,JdbcTripRepository}.java`
- `src/main/java/app/detour/booking/BookingTransactionExecutor.java`
- `frontend/src/components/TripWorkspace.tsx`, `frontend/src/api/tripsApi.ts`
- `src/main/resources/db/migration/V19__model_named_trips_and_dated_options.sql`

## Implementation notes

- The new UI uses explicit Save as new option, Open for editing, and Update this option actions. The old promotion route remains for compatibility with existing clients and its regression tests, but the UI no longer calls it.
- Working date and budget edits remain available after options exist. Destination and traveler edits remain locked then because those values are shared by all options. The shared-details API accepts an optional Draft version for legacy callers; the new client sends both versions and the service guards the Working row.
- A successful keep-current save is reflected in local state immediately, before the subsequent load request. If loading fails, retrying the open action does not create a duplicate option.
- Existing `ItineraryTallyEngine`, cancellation, migration, and public-flow code did not need changes; their relevant behavior was covered by the full backend/frontend suites. The browser and two-tab checks above remain optional and unperformed.
