# P00-T01 Capture Replacement Baseline Testing Plan

## Change Summary

The implementation adds a Markdown baseline record and no executable product change. Verification must prove that the record is complete and truthful: it captures the worktree before checks, records the exact checkout-supported backend/frontend/package commands and outcomes, exhaustively classifies the current 41 backend tests, and demonstrates that the implementation did not modify product, test, configuration, generated-asset, or database paths.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Worktree preservation | A status captured after build output or record creation could misattribute user-owned work. | Capture porcelain status and both name-only diffs first; repeat them after writing the record. |
| Backend verification | Java is unavailable on PATH, so Maven may fail before JUnit executes. | Record the exact `mvnw.cmd` command, exit result, and wrapper diagnostic; do not substitute a passing command. |
| Frontend verification | There is no configured test runner, while the production build is configured. | Inspect `frontend/package.json`, record frontend tests as `NOT RUN`, then execute the Vite build command. |
| Packaged build | `mvnw.cmd package` runs `npm ci` and can create ignored outputs or fail before package construction. | Record exact outcome; final Git scope checks exclude neither untracked user files nor evidence of tracked application changes. |
| Replacement boundary | An incomplete or ambiguous group could leave P00-T02 guessing which legacy tests matter. | Exhaustive method/group inventory with counts 6 reusable, 19 replace, 16 remove, totaling 41. |

## Existing Coverage and Environment Constraints

The existing backend suite is five JUnit/Spring Boot test classes under `src/test/java/demo/wayfarer/`; ordinary tests use isolated in-memory H2 and mocked `SkillTemplate`. `LivePlanningTest` and `LiveIntakeTest` are opt-in provider tests guarded by `WAYFARER_LIVE_TEST=true`, so they are not enabled. No frontend test files are tracked and `frontend/package.json` provides only `dev` and `build` scripts. README documents `./mvnw.cmd test -DskipFrontend=true`, `npm run build --prefix frontend`, and `./mvnw.cmd package`; `pom.xml` confirms package invokes frontend install/build unless `skipFrontend` is changed.

Research observed that Java was not discoverable on PATH and `./mvnw.cmd -v` could not start Maven. That condition is a known constraint to reproduce/record, not a failure to repair in this ticket. Node/npm and `frontend/node_modules` were present during research, but frontend build status must be captured at implementation time.

## Failing Test First

- Name: `baselineRecordHasRequiredExecutionEvidence`
- Type: deterministic documentation-contract inspection (no new test source)
- Location: `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md`
- Arrange/Act/Assert: before the record exists, inspect the planned path and required headings/command literals; after it is written, confirm the file exists and contains the starting-state, command-results, test-disposition, and final-scope sections plus each exact command.
- Expected pre-fix failure: the baseline path is absent, so the required execution evidence and exhaustive test-disposition inventory cannot be found.

No product-level red test applies because the ticket intentionally makes no product behavior change. The artifact-completeness inspection is the smallest deterministic proof of the requested new capability.

## Tests to Add or Update

### 1. `baselineRecordHasRequiredExecutionEvidence`

- Type: documentation-contract inspection
- Location: `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md`
- Proves: the durable record names the captured revision/worktree, all required command categories and outcomes, the absent frontend-test reason, complete dispositions, and final scope evidence.
- Inputs/fixture: the execution-time record; exact strings `./mvnw.cmd test -DskipFrontend=true`, `npm run build --prefix frontend`, `./mvnw.cmd package`, `PASS`/`FAIL`/`NOT RUN`, and total `41`.
- Doubles or boundary isolation: none; inspect Markdown only. Do not enable provider-backed tests or start the application.
- Edge cases: Maven commands fail before test/package execution; there are no frontend tests; the known source files are absent from the current modifications; pipeline-created artifacts are present at the snapshot.

### 2. `baselineDispositionInventoryIsExhaustive`

- Type: repository inventory comparison
- Location: `src/test/java/demo/wayfarer/` and the baseline record
- Proves: every current `@Test` method is assigned exactly one of reusable, replace, or remove and the group totals reconcile to the live source inventory.
- Inputs/fixture: the five tracked `*Test.java` files and `rg -n "@Test" src/test/java/demo/wayfarer` output.
- Doubles or boundary isolation: none; do not execute live/provider tests.
- Edge cases: grouped entries must still list each method, `@EnabledIfEnvironmentVariable` tests count in the inventory even when skipped, and no frontend test group is fabricated.

### 3. `baselineImplementationPreservesScope`

- Type: Git worktree integrity check
- Location: repository root plus baseline record
- Proves: no tracked product, test, dependency, configuration, generated-asset, or database path was changed by Step 4; status changes are either the baseline record or already-present artifacts captured before the work.
- Inputs/fixture: before/after `git status --porcelain=v1 --untracked-files=all`, `git diff --name-only`, `git diff --cached --name-only`, and `git diff --check` outputs.
- Doubles or boundary isolation: none; never use reset, checkout, clean, database reset, or live service commands.
- Edge cases: untracked pipeline artifacts have verifiable workflow provenance; ignored `frontend/dist`, `target`, and `node_modules` products may be regenerated but are not committed or removed; pre-existing user work remains untouched.

## Safe Verification Commands

- Focused: `Test-Path ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md`
- Related suite: `rg -n "@Test" src/test/java/demo/wayfarer`
- Full safe suite: `./mvnw.cmd test -DskipFrontend=true`; `npm run build --prefix frontend`; `./mvnw.cmd package`
- Record-scope checks: `git status --porcelain=v1 --untracked-files=all`; `git diff --name-only`; `git diff --cached --name-only`; `git diff --check`

Each command must be executed or deliberately marked `NOT RUN` in the baseline record with the exact reason. The frontend-test category is not a runnable suite: use `Get-Content -Raw frontend/package.json` and tracked-file inspection to document that no supported test command exists.

## Optional Developer Checks

- None. Running the application, live provider tests, or database-reset commands would exceed this ticket's evidence-only scope.

## Exit Criteria

- [ ] The planned artifact inspection fails before the baseline record exists for the intended missing-record reason.
- [ ] The completed record includes the fresh before-verification Git snapshot and source-file preservation statement.
- [ ] Backend test, frontend-test availability, frontend production build, and package build each have an exact command or an explicit unavailable-command reason plus `PASS`, `FAIL`, or `NOT RUN` result.
- [ ] The baseline disposition inventory assigns all 41 current tests exactly once and reconciles to the five tracked Java test files.
- [ ] The broadest safe relevant repository checks were attempted and their actual results are recorded, including any Java/Maven limitation.
- [ ] Final Git status/diff evidence distinguishes prior pipeline artifacts from the sole Step-4 baseline record and shows no tracked application/test/configuration/generated/database edit.
- [ ] Routine verification does not enable live model tests, start the application, contact a provider, or perform database-destructive work.
- [ ] Any optional check is reported as nonblocking and is not represented as already performed.
