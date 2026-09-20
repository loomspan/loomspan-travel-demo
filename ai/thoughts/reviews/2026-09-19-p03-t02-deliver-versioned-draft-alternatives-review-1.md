# P03-T02 Deliver Versioned Draft Alternatives and Autosave Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket-scoped unstaged production, migration, test, ticket, research, and plan changes against merge base `bdcdb24`. No staged changes were present. The untracked research/plan artifacts and V13 migration belong to this ticket; no unrelated source changes were identified.

The review traced the authenticated controller routes through strict request parsing, transactional service operations, owner-qualified JDBC aggregate reads/conditional version advances, Flyway migration, and MockMvc plus loopback-restart coverage. Security, owner non-disclosure, persistence rollback, optimistic concurrency, lifecycle, scope exclusions, and response/error contracts were considered.

## Findings

No remaining actionable findings.

## Findings Resolved in This Context

### [P3] Remove the redundant child lookup index
- Location: `src/main/resources/db/migration/V13__allow_multiple_component_empty_drafts.sql:3` (before fix)
- Scenario: V12 already creates `ix_detour_trip_draft_trip_id` on `detour_trip_draft(trip_id)`. V13 added a second identical `ix_detour_trip_draft_trip_id_v13` after removing the one-Draft unique constraint.
- Impact: Every Draft insert/delete would maintain two equivalent B-tree indexes for no query benefit, adding needless storage and write cost to the new lifecycle path.
- Evidence: `V12__create_owned_trip_and_initial_draft_schema.sql:47` retains the existing index; the fresh and forward Flyway tests pass after the V13 duplicate-index statement is removed.
- Fix: Removed only the redundant V13 index creation. The migration still removes the one-Draft restriction and restores the parent FK.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Multiple component-empty Drafts and explicit source-preserving duplication persist | V13 removes one-Draft cardinality; explicit create/duplicate routes and fresh UUID inserts in `TripController`/`TripService` | `createsAdditionalComponentEmptyDraftOnlyWhenExplicitlyRequested`, lifecycle test, restart test | implemented |
| Shared-detail replacement is validated, owner-scoped, label-derived, and versioned | `TripRequests.update`, `TripService.replaceSharedDetails`, conditional `advanceVersion`, transactional JDBC traveler replacement | lifecycle/shared-update assertions and restart test | implemented |
| Same observed Trip version cannot silently overwrite | Atomic owner-qualified version predicates; stable `VERSION_CONFLICT` with current version | sequential stale assertion and barrier-concurrent shared-save test | implemented |
| Failures are distinguishable and failed alternatives can be retried safely | Validation/error envelope plus transactional parent advance and Draft insert | H2 insert-trigger rollback/retry test | implemented |
| Owner-only selected Draft deletion leaves zero-alternative aggregate reusable | Owner-qualified aggregate/Draft resolution; guarded single-row delete; independent empty-Draft creation | lifecycle delete-last/create-after-empty and foreign-versus-unknown test | implemented |
| Migration and restart durability | Forward-only V13; reloaded aggregate has ordered Drafts and retained versions/details | clean/forward Flyway tests and loopback file-H2 restart test | implemented |
| No later-phase component, lifecycle, merge, booking, sharing, UI, or Event behavior | DTOs/routes/storage remain limited to empty Draft identifiers and versions | scoped source/diff inspection | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `git diff --check` — no whitespace errors.
- PASS — `.\mvnw.cmd -DskipFrontend=true '-Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest,DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest' test` — 14 tests passed after the migration fix.
- PASS — `.\mvnw.cmd test -DskipFrontend=true` — full backend suite passed after the migration fix.

## Residual Risks and Optional Developer Checks

- Browser autosave status and conflict presentation remain intentionally deferred to P03-T06; the backend contract is covered here.

## Disposition

- `fixes-applied`
