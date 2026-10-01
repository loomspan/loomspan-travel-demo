# Manage independent trip plans Code Review — Cycle 1

## Scope and Repository State

Independent Step 5 under the developer-confirmed Full 5-Step Pipeline (`full`). Comparison is the dirty checkout against `HEAD` (`1ffcacd` on `main`), with the orchestrator's clean initial checkout establishing attribution. Scope includes all tracked modifications and untracked canonical models, V21 migration, workspace/dialog/navigation, helpers, and integration/UI tests. No prior review documents were read. Ticket, research, implementation plan, testing plan, command/protocol and design lens were read; their completed checkboxes and verification claims were not used as passes.

Reviewed canonical plan persistence and legacy projections; principal ownership, strict JSON parsing and child/aggregate versions; create/copy/save/rename/promotion/deletion and rollback; purchase snapshot mapping, cutoff and inventory restoration; migration data preservation and failed-conversion behavior; profile/history navigation and dirty state; comparison, prices, locks and accessibility; tests, README and isolated packaged verifier. Connected component/search/booking consumers were traced beyond diff hunks. The full initial defect review was completed before implementation edits.

## Findings

No actionable findings remain after the fix and internal re-review below.

## Findings Resolved in This Context

### [P2] Identify canceled purchases as history throughout plan details

- Location: `frontend/src/components/IndependentPlansWorkspace.tsx:180` (new View booking details path), `frontend/src/components/BookingConfirmationView.tsx:44`, `frontend/src/components/TripComparisonPage.tsx:79`, `frontend/src/components/RentalSlot.tsx:50`.
- Scenario: Cancel a purchase, reopen its retained plan, and choose View booking details. The new workspace passed that canceled purchase into a component previously used for successful active bookings. Its banner and live announcement unconditionally said Booking Confirmed/Reservation complete. The locked flight, stay and rental labels also unconditionally called historical canceled components Confirmed.
- Impact: Users and assistive technology received a current confirmation for a canceled reservation, even though the underlying booking status and history were correctly preserved. This violated faithful purchased/history display; the governing plan explicitly requires accurate canceled/history status alongside retained locks.
- Evidence: Added the CANCELED case to the executable workspace purchased-details scenario; `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'accurate CANCELED'` failed on the unchanged production implementation because the canceled-history label was absent. Source tracing confirmed the unconditional confirmation banner/announcement on the same reachable path.
- Fix: Confirmation derives its heading, eyebrow, symbol and live announcement from persisted booking status. The workspace passes canceled purchase status to the shared flight/stay and rental presentation. Canceled labels retain explicit locked wording and existing disabled controls. No booking, cancellation, version, inventory or persistence behavior changed.
- Regression evidence: The parameterized ACTIVE/CANCELED workspace test verifies frozen purchased dates/party, flight/stay/rental history labels, locked search/removal controls, booking reference, heading and live announcement. Existing active-confirmation and guest/legacy callers preserve their prior behavior through default props.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Executable test evidence | Result |
| --- | --- | --- | --- |
| AC1 one primary and legacy data preserved | V21 converts preserved Working UUIDs, copies snapshot facts/party, chooses oldest existing fallback, validates conversion and same-trip primary FK; canonical hydration rejects missing primary | `TripModelForwardMigrationIntegrationTest` V18/V20, absent/empty Working, unknown ages, booked links, missing-night failure/restart; canonical create invariant | implemented |
| AC2 top named accessible navigation | `PlanNavigation`, `IndependentPlansWorkspace`, overflow CSS; primary-first sort, text badge, native All plans, manual keyboard activation and tab/panel links | Independent workspace keyboard/overflow and panel assertions | implemented |
| AC3 switching is view-only | Selected UUID/history state; retired load/replace routes reject without writes | Workspace no-mutation switching; `TripApiIntegrationTest.retiresCopyAndReplaceWithoutAnyWrites` | implemented |
| AC4 independent save/rename | Canonical plan party/snapshots; `savePlanInternal`, `planAction`; sibling plans not written | Independent alternative save, lifecycle identity/name assertions; selected-plan catalog/search regressions | implemented |
| AC5 independent create/copy without purchases | New UUID via `insertPlan`; no copied booking rows or lock flags; empty create allowed | Canonical lifecycle, purchased-source unconfirmed copy and packaged lifecycle | implemented |
| AC6 no silent discard, failed save/concurrency/ownership | Persisted baseline plus Save/Discard/Cancel guard; busy ref and clean-refresh version checks; owner/child lookup and aggregate CAS | Workspace failed/conflict, pending, late refresh, CRUD/exit/history guards; cross-owner and synchronized same-version API races | implemented |
| AC7 booked promotion persists without reservation changes | Single same-trip pointer; transactional aggregate version guard; canonical lookup preserves booking plan ID after promotion | Booked promotion/booking equality, concurrent promotion, file-backed restart and packaged reopen | implemented |
| AC8 faithful locked purchases with editable planning | Snapshot-derived component locks on canonical/legacy APIs; frozen purchased dates/count/budget; status-aware UI after this fix | Frozen airfare/stay/rental API tests; confirmed/unconfirmed UI and added ACTIVE/CANCELED purchase-detail test | implemented |
| AC9 confirmed/history/canceled protections retained | `isPlannedItineraryBooked`, history checks and `requireActiveTrip`; cancellation preserves rows | Historical delete/remove block, cancellation/cutoff/inventory regressions | implemented |
| AC10 replacement-primary deletion is atomic | Required distinct owned replacement; pointer move and child deletion in one transaction and CAS | Missing replacement, rollback injection, promotion/delete and booking/delete races | implemented |
| AC11 sole eligible plan deletes trip only by explicit consent | Plan count/versions, `deleteTrip` consent, history protection; no child-first deletion | Sole consent/missing-consent and rollback tests; exact UI prompt and Keep trip no-write assertions | implemented |
| AC12 compare 2–3 distinct plans beside navigation | Canonical chooser includes primary; launch defaults, checked-ID set and bounds | Primary+alternative, alternatives-only and fourth-disabled chooser assertions; comparison navigation/mobile keyboard suite | implemented |
| AC13 names/status/dates/prices/partial | Per-plan headers and server tallies; Not selected and explicit partial labels | Comparison missing/partial-zero/unavailable pricing and status assertions; frozen pricing tests | implemented |
| AC14 existing trip/booking navigation and persistence | Preserved UUIDs, legacy aliases, canonical booking lookup, selected history state and profile integration | Real Profile/history guards, trip/booking file-backed restart and packaged CSRF/owner/reopen verifier | implemented |
| Trip destination/budget scope and compatibility strategy | Shared settings labeled; plan-specific dates/party; legacy rendering branch used only when canonical plans absent; archived draft tables have no runtime writes | Existing shared-settings/catalog regressions and canonical response assertions | safe deviation/implemented: compatibility rendering branch is documented and production uses canonical responses |
| Release and recovery | Forward-only V21 with copied-data validation; README warns against old runtime and requires backup/forward recovery | Synthetic migration failure preserves source and restart diagnostic; isolated packaged verifier | implemented |

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`. Placeholder examples were not treated as policy. Full-profile scope remains appropriate because migration, persisted contracts, lifecycle/concurrency and booking metadata change.

## Open Questions and Assumptions

- The initial checkout was clean as supplied by the orchestrator; no unrelated dirty work was excluded or overwritten.
- Supported purchases remain fictional local operations. Dates/catalog restrictions and existing no-amendment/no-history-cleanup scope are preserved.
- No unresolved product decision affects this review.

## Verification Results

Verification commands were run sequentially across frontend and Maven to avoid the documented Windows native-module unlink conflict.

- PASS — `npm --prefix frontend test` — independent initial gate: 212 tests across 18 files.
- PASS — `.\mvnw.cmd clean verify` — 216 backend tests, zero failures/errors/skips; frontend TypeScript/Vite build and packaged JAR. Log: `%TEMP%/independent-review1-cleanverify.log`. Backend files did not change afterward.
- FAIL (expected red) — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx -t 'accurate CANCELED'` — 1 reproduced status-display failure, 20 intentionally filtered cases, before production fix.
- FAIL (test correction) — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx src/components/BookingConfirmationView.test.tsx` — 28 passed, new canceled test used nonexistent accessible name Remove rental; corrected to the actual Remove button. This was a test selector error, not an unresolved production defect.
- PASS — `npm --prefix frontend test` — final production fix: 213 tests across 18 files. Log: `%TEMP%/independent-review1-ui-final.log`.
- FAIL (test typing correction) — `npm --prefix frontend run build` — new test used unsupported `exact` option in `getByRole`. Removed that option; string accessible names already match exactly.
- PASS — `npm --prefix frontend run build` — final TypeScript/Vite assets. Log: `%TEMP%/independent-review1-build.log`.
- PASS — `npm --prefix frontend test -- src/IndependentTripPlans.test.tsx src/components/BookingConfirmationView.test.tsx` — final test correction: 29 tests across 2 files. Full suite above tested the same production implementation; last correction only removed an unsupported test option. Log: `%TEMP%/independent-review1-focused-final.log`.
- PASS — `.\mvnw.cmd -DskipFrontend=true -DskipTests package` — refreshed JAR includes final verified frontend assets; tests intentionally skipped in this packaging-only command. Log: `%TEMP%/independent-review1-package.log`.
- PASS — `.\scripts\verify-packaged-release.ps1` — isolated packaged HTTP primary/lifecycle/party/copy/booking/promotion/locks/CSRF/idempotency/cancellation/restart/snapshots/history/owner checks. Log: `%TEMP%/independent-review1-packaged.log`; verifier owns its disposable server/database and cleans up.
- PASS — `git diff --check` — complete tracked diff has no whitespace errors; untracked canonical implementation/test files were explicitly read and reviewed in addition to ordinary Git diff.
- NOT RUN — manual narrow-screen and actual screen-reader checks — optional, not automated passes; residual observation described below.

## Residual Risks and Optional Developer Checks

- Optional 320-pixel/long-name visual inspection and actual screen-reader announcements were not performed. DOM semantics, focus, keyboard navigation, selector reachability and status text are executable evidence, not a claim of visual or assistive-device testing.
- Optional desktop/mobile visual comparison and purchased-details inspection remain available with fictional local data.
- No deployment, live development database migration, external provider, payment, or model operation was performed. The migration requires the documented real-data backup/release procedure when deployed; the archived drafts are diagnostic evidence, not a supported downgrade path.

## Disposition

`fixes-applied`: implementation changes in this context require a fresh independent Step 5 review. Internal re-review finds no further actionable ticket-scoped defects; verification receipts below establish the final fix.
