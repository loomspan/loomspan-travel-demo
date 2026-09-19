# P03-T01 Establish Trip Draft Foundation Code Review - Cycle 2

## Scope and Repository State

Reviewed the ticket, research, implementation plan, testing plan, active design lens, and all current ticket-scoped staged, unstaged, and untracked work against `main` at `c01de88`. The implementation adds the V12 Trip/traveler/Draft schema, the authenticated create/detail boundary, JDBC aggregate persistence, rollback/ownership/restart coverage, and migration assertions. No active project-specific guardrails are recorded.

The initial review found one user-visible defect in the newly derived label. It was fixed only after the review was complete, then the complete ticket change was re-read and focused integration, restart, and migration verification was rerun.

## Findings

No actionable findings.

## Findings Resolved in This Context

### [P3] Render the derived label with the intended punctuation

- Location: `src/main/java/app/detour/trip/TripService.java:91`
- Scenario: Creating any Trip previously stored and returned a label containing mis-decoded dash sequences in place of its separator and date-range punctuation.
- Impact: Every user-visible derived Trip label was malformed despite otherwise successful creation and retrieval.
- Evidence: The original string literals contained UTF-8 decoding artifacts, and the existing test repeated the corrupted expected value.
- Fix: Replaced the literals with Java Unicode escapes for the em dash and en dash, and updated `TripApiIntegrationTest` to assert the correct rendered response.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Authenticated, atomic create returns normalized Trip and initial Draft | `TripController`, `TripService.create`, `JdbcTripRepository.createAggregate`, V12 | `createsOwnedTripAndInitialComponentEmptyDraft` | implemented |
| Reject invalid shared data/money without partial persistence | `TripRequests`, `TripService` validation, V12 constraints, transaction | `validatesEnvelopeAndNeverPersistsPartialAggregate`, rollback trigger test | implemented |
| Adult-less complete ages remain Draft-valid | Age validation permits `0..120` without adult readiness | optional-age coverage includes `[0,17]` | implemented |
| Preserve absent optional values separately from zero | Nullable mapped fields and strict budget validation | `preservesUnknownAgesAndAbsentBudgetSeparatelyFromZero` | implemented |
| Owner isolation and non-disclosure | Owner and public ID are combined in repository detail lookup | `doesNotDiscloseForeignTripAndBindsCreationToPrincipal` | implemented |
| No orphaned aggregate on failed/concurrent creation and restart durability | Transactional create plus V12 foreign keys | rollback, concurrent-failure, and `TripApplicationRestartIntegrationTest` | implemented |
| Clean migration and existing identity forward migration | forward-only V12 plus migration test updates | `DetourApplicationTest`, `PhaseOneCatalogForwardMigrationIntegrationTest` | implemented |
| Preserve scope boundary | Narrow API/schema/response surface; no mutation, component, lifecycle, UI, or booking route | API response assertions and source review | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS - `git diff --check` - no whitespace errors.
- PASS - `.\mvnw.cmd -DskipFrontend=true '-Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest,DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest' test` - 9 tests passed after the label correction.

## Residual Risks and Optional Developer Checks

- The broader frontend/build/package checks were not rerun in this review cycle because the only review fix changes a Java response string and its focused integration assertion; prior ticket-scoped build/package verification remains subject to the final fresh review's independent assessment.
- Optional: manually inspect a created Trip label in a browser once the Phase 3 UI exists; this ticket intentionally adds no Trip UI.

## Disposition

- `fixes-applied`
