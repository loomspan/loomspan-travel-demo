# Manage independent trip plans Code Review — Cycle 10

## Scope and Repository State

Fresh independent pipeline Step 5 under the confirmed `full` profile. Comparison base is the original clean checkout HEAD `1ffcacd5cf204e09e2a3800322d79e6d379e4ff0` on `main`, verified with Git. Reviewed the complete ticket change, including 46 modified tracked files and nine untracked implementation/test files, rather than only recent changes. The index has no staged change. No prior review document was read.

Read the ticket, research, implementation plan, testing plan, Step 5 command, automation protocol and design lens. There are no repository/ancestor AGENTS.md instructions and no active project-specific design guardrails. Full remains appropriate: this change affects serialized/persisted contracts, migration, concurrency and booking lifecycle.

Scope includes canonical TripPlan/PlanResponse, V21 migration, Trip/JDBC repository/service/request/controller/response/tally changes; booking metadata, mapper, transaction and JDBC changes; IndependentPlansWorkspace, PlanNavigation, PlanDialog, workspace dispatch, API/history/Profile/comparison/component/confirmation/cancellation/duplication consumers and CSS; all affected Java/frontend tests and TestPlanSelections; README and packaged verifier; and governing ticket/research/plans. Existing review audit files were excluded from evidence and were not changed.

## Findings

No actionable findings.

The independent defect review preceded editing and requirements comparison. Concrete paths examined:

- **Identity, ownership and writable fields:** controller methods bind the authenticated principal; ownedTrip and ownedPlan restrict parent and child membership. Strict parsers reject writable primary/owner/purchase fields. Save retains the existing UUID/name; name changes are explicit. Canonical component aliases and preserved draft UUID routes converge on the same owner/version/confirmed protection. The old option load/replace endpoints reject without writes.
- **Primary and transaction boundaries:** aggregate and child versions gate saves, while copy/promotion advance aggregate without modifying source contents/version. Deletion checks current versions/count, history and explicit consent; primary deletion resolves a different same-trip replacement before moving the pointer and removing the child. Sole deletion removes the aggregate, not just its final child. The same-trip composite FK prevents foreign pointers; nullable primary exists only as a creation/deletion implementation detail, with transactional service writes establishing a reachable primary before commit. Hydration locks the parent through child reads; profile captures and locks a deterministic ID set. Race and injected-failure tests check winners, rollbacks and surviving pointer reachability.
- **Purchased facts:** purchased components derive from booking snapshots across history, not client flags. Full-save omission/replacement checks and individual component mutations retain protections. Plan date/party edits preserve locked snapshots; purchased traveler count, dates and budget drive booking responses, cutoff and seat restoration. Search-budget and plan-airfare pricing use purchased party where relevant. Copy preserves planning facts but strips booking associations; purchase validates that copied component dates fit the new planning interval without silently refreshing the saved price. Active-booking uniqueness and idempotency recovery remain enforced.
- **Migration and recovery:** V21 retains option IDs/names/snapshots/booking links, converts Working with its UUID and component facts, clones traveler context, selects Working first or oldest existing option when absent, and creates an empty primary only when necessary. Copy-count and stay-night checks fail incomplete conversion; archived draft data is retained. Failure/restart tests establish preserved source data and a diagnostic failure, not transactional H2 DDL rollback. Runtime Java contains no archived draft-table reference.
- **UI state and lifecycle:** a saved baseline is separate from pending plan fields; Save uses selected identity and does not rename. Dirty transitions offer Save/Discard/Cancel and preserve input on failure. Busy guards and disabled controls prevent overlapping editor mutations; refresh checks dirty/busy/version before applying results. Browser history carries selected plan, review return target and confirmation booking identity; historical details can resolve from history when no longer the plan's latest purchase. Profile navigation shares the workspace guard. Cancellation refresh updates previously viewed confirmation status. Dialog focus trapping/restoration and tab keyboard/overflow semantics are executable.
- **Security, operations and maintainability:** existing session/CSRF protection covers the new endpoints; owner/child checks and parameterized SQL preserve trust boundaries. No secret, supplier/payment integration or production data operation was introduced. Snapshot and lock lookup work grows with plans/history, but review found no concrete ticket-scoped operational defect requiring an unrelated architecture change. README documents the forward migration, archived data and supported recovery boundary.

## Findings Resolved in This Context

None. No implementation artifact was edited in this context; only this review was added.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| AC1 new/legacy reachable primary and preservation | V21; createAggregate; loadTrip | TripModelForwardMigrationIntegrationTest fresh/V18/V20/absent/empty/failure fixtures; IndependentTripPlanIntegrationTest lifecycle | implemented |
| AC2 top named accessible navigation | IndependentPlansWorkspace; PlanNavigation; overflow CSS | IndependentTripPlans keyboard/manual activation/tab-panel/All plans assertions | implemented |
| AC3 view-only switching | switchPlan; canonical workspace dispatch; retired load/replace routes | UI no-mutation spies; retiresCopyAndReplaceWithoutAnyWrites | implemented |
| AC4 independent edit/save/name | savePlanInternal; plan party/snapshots; name action | savingAlternativeChangesOnlyItsOwnDatesPartyAndSelections; lifecycle/sibling equality; selected-ID UI save | implemented |
| AC5 independent create/copy without purchases | createPlan; copy action; insertPlan | canonical lifecycle and purchased-copy assertions; packaged copy check | implemented |
| AC6 dirty failures, conflicts and ownership | guard/save/refresh; owner membership; aggregate/child CAS | UI deferred save/refresh/conflict/Profile/history tests; API foreign-owner/stale tests | implemented |
| AC7 booked promotion and persisted choice | primary action; setPrimary; canonical booking-ID mapper | purchased promotion and same-version race; file-H2 restart; packaged restart | implemented |
| AC8 locked accurate purchased facts with editable remainder | purchased/requireUnlocked; frozen booking context; component/confirmation UI | purchasedFlightsRemainFrozen; confirmedStayAndRentalRemainLocked; API omission/legacy protection; UI lock/context cases | implemented |
| AC9 retained history/canceled-trip restrictions | booked/history deletion checks; requireActiveTrip; cancellation services | booking cancellation/API/concurrency regressions; historical plan/component protection | implemented |
| AC10 replacement choice and atomicity | planAction delete; transactional pointer/deletion | injected deletion rollback; promotion/delete race; UI replacement/current versions/Cancel | implemented |
| AC11 sole-plan consent and trip policy | sole deletion branch; deleteTrip history/count/version rules | explicit consent and rollback test; exact UI prompt/Keep trip no write | implemented |
| AC12 top comparison, two/three distinct plans and launch defaults | comparison chooser; canonical plans selection | alternative+primary default; alternatives-only; fourth disabled and two-plan launch UI cases | implemented |
| AC13 names/status/details/prices and partial labels | ItineraryComparisonView; per-plan tally; missing/partial rendering | comparison semantics/money/unavailable regressions; Partial total/Not selected canonical test | implemented |
| AC14 navigation, details and refresh persistence | canonical UUID projection; screenHistory; booking mapper; primary profile ordering | selected/review/confirmation history; Profile regression; trip/booking restart; packaged isolation/reopen | implemented |
| Shared destination/budget; archived drafts; compatibility branch | Trip settings; canonical writes; TripWorkspace dispatch | shared-settings/legacy regressions and canonical suite | safe deviation from obsolete Working-only fixtures, consistent with recorded design |

Test quality was evaluated from assertions and boundaries, not checkmarks. HTTP tests exercise authenticated controllers/service/JDBC and compare persisted state; migration fixtures run real Flyway; rollback tests inject failure after protected mutations; synchronized races assert one winner and valid surviving state; restart tests reopen real disposable file databases. TestPlanSelections builds canonical snapshots from catalog fixture IDs without reintroducing runtime draft writes. Frontend doubles isolate network calls while exercising real components and controlled promises; they establish DOM/focus/request behavior, not browser pixels or screen-reader speech. The isolated JAR verifier supplies independent deployed-HTTP evidence for CSRF, ownership, lifecycle and restart.

## Active Project Guardrails

None recorded. Existing authorization, history retention, fictional inventory and disposable verification boundaries were preserved.

## Open Questions and Assumptions

None requiring a developer decision. The ticket and recorded plan explicitly keep destination/budget trip-scoped, preserve history and exclude amendments/additional active-trip purchases.

## Verification Results

Executed sequentially on Windows to avoid overlapping Vitest with Maven npm installation/native modules:

- PASS — `npm --prefix frontend test` — 221 tests in 18 files; zero failures. Log: `%TEMP%/review10-frontend.log`.
- PASS — `.\mvnw.cmd clean verify` — 220 Java tests; zero failures/errors/skips, TypeScript/Vite production build and repackaged JAR successful. Log: `%TEMP%/review10-maven.log`.
- PASS — `.\scripts\verify-packaged-release.ps1` — its own temporary loopback server/file-H2 only; authentication/public-write protection, CSRF, independent date/party edits, unconfirmed copy, booked promotion/locks, idempotency, cancellation, restart, frozen history and owner isolation. Log: `%TEMP%/review10-packaged.log`.
- PASS — `git diff --check` — no whitespace errors.
- Static verification — `rg -n 'detour_trip_draft' src/main/java` has no matches, confirming archived tables are not runtime targets.
- NOT RUN — live database migration/deployment — prohibited by task scope and unnecessary for isolated acceptance evidence.

## Residual Risks and Optional Developer Checks

No live DB, deployment, real account, supplier or payment was used. V21 remains forward-only; H2 migration failure can leave staged schema/data requiring documented backup or corrective migration recovery. Optional 320px/long-name visual inspection, actual screen-reader announcements and desktop/mobile comparison/purchase-detail visual inspection were not performed. Automated navigation/semantics/focus and isolated persistence evidence is sufficient for this review; those observations remain nonblocking supplements.

## Disposition

`clean`: full independent ticket review completed, no actionable issue remains, required executable verification passed, and no implementation artifact changed.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-01-manage-independent-trip-plans-review-10.md
SUMMARY: Independently reviewed the complete original-HEAD ticket scope and connected callers, state, security, migration, booking and UI boundaries. No actionable findings and no implementation edits. Frontend, clean Maven build/test and isolated packaged verifier passed.
DECISIONS:
  - Retained full profile because persisted contracts and lifecycle/concurrency are affected; isolated verification provides sufficient evidence without live data operations.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - PASS — `npm --prefix frontend test`: 221 tests.
  - PASS — `.\mvnw.cmd clean verify`: 220 Java tests and production frontend/JAR build.
  - PASS — `.\scripts\verify-packaged-release.ps1`: isolated lifecycle/restart/security assertions.
  - PASS — `git diff --check`.
OPTIONAL_DEVELOPER_CHECKS:
  - Narrow-screen/long-name visual and screen-reader inspection; desktop/mobile comparison and purchased-detail visuals.
REVIEW_RESULT: clean
NEXT: Orchestrator may finish the pipeline with this independent clean review.
