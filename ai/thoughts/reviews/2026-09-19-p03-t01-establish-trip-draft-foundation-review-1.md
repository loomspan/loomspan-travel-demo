# P03-T01 Owned Trips and Initial Drafts Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket-scoped working-tree change against `main`/`origin/main` at `c01de88`, including modified, untracked, and generated-artifact-adjacent paths: the V12 Flyway migration; the Trip controller, service, repository, request/response records; migration regressions; Trip HTTP/restart integration tests; and ticket/research/plan artifacts. There were no staged changes and no unrelated production changes to exclude. `ai/thoughts/design-lens.md` records no active guardrails.

The review traced authenticated create and owner-scoped retrieval through Spring Security, request parsing, validation, the transaction boundary, JDBC writes/reads, Flyway constraints, and error handling. It also checked the foreign-ID path, rollback path, restart path, and packaged startup behavior.

## Findings

No actionable findings remain after the in-context fix below.

## Findings Resolved in This Context

### [P2] Exercise the required create-validation boundary
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java:91`
- Scenario: Before this review, the validation matrix did not execute required missing `startDate`, `endDate`, or `travelerCount` requests; malformed date values; reverse dates; or null/fractional/string age and string-budget values.
- Impact: Regressions in those explicit creation-contract rejection paths could ship while the suite remained green, despite the ticket and testing plan requiring validation-boundary coverage and no partial persistence.
- Evidence: The implementation validates these paths, but the original `invalidBodies` matrix only covered a missing destination, selected numeric boundaries, and malformed JSON.
- Fix: Added those contract cases to the existing HTTP integration matrix, which already asserts `400 VALIDATION_FAILED` and unchanged Trip/traveler/Draft row counts after the complete set.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Authenticated atomic create with label/version/one Draft | `TripController`, transactional `TripService#create`, `JdbcTripRepository#createAggregate`, V12 | `createsOwnedTripAndInitialComponentEmptyDraft`; controlled Draft failure rollback | implemented |
| Required envelope, strict optional ages/budget, and no partial rows | `TripRequests`, `TripService` validation and V12 checks | expanded `validatesEnvelopeAndNeverPersistsPartialAggregate`; optional-value test | implemented |
| Adult-less complete ages remain Draft-valid | `validateAges` has no adult requirement | `preservesUnknownAgesAndAbsentBudgetSeparatelyFromZero` creates `[0,17]` | implemented |
| Owner binding and foreign-ID non-disclosure | principal-only controller create and `findByPublicIdAndOwnerUserId` predicates | `doesNotDiscloseForeignTripAndBindsCreationToPrincipal` | implemented |
| Failure/concurrency isolation and restart durability | `@Transactional` aggregate writes, FKs, durable public IDs | rollback trigger, concurrent invalid-create test, `TripApplicationRestartIntegrationTest` | implemented |
| Clean/forward migration and repository/package health | V12 and migration assertions | backend suite, frontend test/build, package, loopback packaged check | implemented |
| No excluded workflow behavior | only create/detail routes and component-empty Draft response | response-shape assertions and reviewed route surface | implemented |

## Active Project Guardrails

- None recorded. The change conforms to the architecture ownership rule: all Trip reads are constrained by authenticated owner ID and public Trip ID, and create derives ownership only from the authenticated principal.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\\mvnw.cmd -DskipFrontend=true "-Dtest=TripApiIntegrationTest,TripApplicationRestartIntegrationTest" test` — Trip HTTP, rollback, isolation, and restart coverage passes after the test fix (7 tests).
- PASS — `.\\mvnw.cmd -DskipFrontend=true "-Dtest=DetourApplicationTest,PhaseOneCatalogForwardMigrationIntegrationTest,IdentityApiIntegrationTest" test` — identity and clean/forward Flyway compatibility passes (8 tests).
- PASS — `.\\mvnw.cmd test -DskipFrontend=true` — complete backend regression suite passes.
- PASS — `npm.cmd run test --prefix frontend; npm.cmd run build --prefix frontend` — frontend suite (13 tests) and production build pass.
- PASS — `.\\mvnw.cmd package` — package, frontend build, and backend suite pass.
- PASS — `powershell.exe -ExecutionPolicy Bypass -File .\\scripts\\verify-packaged-identity.ps1` — packaged application starts on loopback with an isolated temporary H2 database and serves the shell.

## Residual Risks and Optional Developer Checks

- The package verification intentionally checks the existing identity shell, not an authenticated Trip request. Trip HTTP behavior is covered separately by MockMvc and real loopback restart integration tests; an optional manual two-session browser check remains nonblocking.

## Disposition

- `fixes-applied`
