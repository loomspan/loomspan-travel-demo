# P00-T01 Capture Replacement Baseline Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-17-p00-t01-capture-replacement-baseline.md`
- Research: `ai/thoughts/research/2026-09-17-p00-t01-capture-replacement-baseline.md`
- Outcome: add one concise, committed-ready execution-time baseline record at `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md`; it preserves the starting worktree evidence, verification outcomes, and complete test-disposition inventory needed by P00-T02.

## Current State

The checked-out commit is `5fb7d2b45209c9034bf40ce40e6c580618abc5c8` on `main`. Research captured a clean worktree before this pipeline began, but this planning step now observes the pipeline's durable research artifact as untracked; Step 4 must refresh the status before it creates the baseline record and distinguish workflow-created artifacts from user-owned files only where provenance proves the distinction.

`pom.xml` supplies the supported Maven commands and invokes the frontend install/build during `generate-resources`; `frontend/package.json` has only `dev` and `build` scripts. The ordinary JUnit suite uses in-memory H2 and mocked `SkillTemplate`, while live provider tests are opt-in through `WAYFARER_LIVE_TEST=true`. Research found no Java executable on PATH, so Maven-wrapper commands may fail before tests or packaging start; that is a baseline result to record rather than fix.

## Desired End State

The new baseline record will:

- identify the timestamp, branch, commit, staged/unstaged tracked changes, and every untracked file observed before verification;
- label the named `IntakeService.java` and `TripStore.java` modifications as pre-existing user work if they are present, and otherwise state that they were absent at the execution-time refresh without altering either file;
- list each requested verification with its exact command and `PASS`, `FAIL`, or `NOT RUN` result, including relevant failure output and a clear reason for the unavailable frontend-test check;
- assign all 41 existing backend test methods, in clearly named cohesive groups, to `reusable`, `replace`, or `remove`, with a concise reason; and
- show that the implementation-added change is only the baseline record, while separately preserving already-present pipeline artifacts as workflow provenance rather than silently attributing them to the user.

| Acceptance criterion | Planned evidence |
| --- | --- |
| Starting revision and worktree captured | Pre-verification snapshot section with raw status disposition, branch, commit, and diff-name outputs. |
| Named user work preserved | Explicit named-file status and no source-file edits; absent files are reported as absent, not reconstructed. |
| Verification outcomes recorded | Command-results table covering backend tests, frontend-test availability, frontend build, and Maven package. |
| All tests classified | Test-disposition table totaling 41 methods and covering all five tracked test classes. |
| Ticket-only implementation change shown | Final status/diff comparison records the baseline record as the sole Step-4-created implementation artifact and lists prior pipeline artifacts separately. |

## Scope

### In scope

- Create `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md`.
- Capture a fresh Git snapshot immediately before running baseline verification.
- Run the documented ordinary backend test command, inspect frontend-test configuration, run the documented frontend production build, and run the documented Maven package command.
- Record the complete research-backed disposition of the five tracked JUnit test classes.
- Compare the final worktree with the pre-verification snapshot without reverting, cleaning, staging, or overwriting any files.

### Out of scope

- Any change to Java, React, tests, dependencies, Maven/Vite configuration, skill manifests, generated assets, or H2 database files.
- Repairing unavailable tooling, test failures, build failures, dependency resolution, or provider configuration.
- Running opt-in provider-backed live tests, starting the application, calling a model provider, or resetting the database.
- Renaming Wayfarer, creating DeTour behavior/schema, removing Loomspan, and all Version 2 Events work.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

- The timing of the Git snapshot is material: verification commands and pipeline artifacts can otherwise obscure user-owned changes. Capture `git status --porcelain=v1 --untracked-files=all`, staged and unstaged name-only diffs, branch, and full commit before any baseline command.
- The normal Maven test/package commands are expected not to need a provider, but Maven cannot currently start without Java. Record the actual wrapper failure; do not install Java, change `PATH`, or add a build workaround.
- Maven packaging runs `npm ci`, frontend build, and resource copying. Those can create ignored `frontend/node_modules`, `frontend/dist`, and `target` output; do not delete or commit them and do not treat ignored products as tracked ticket changes.
- The default H2 database is persistent. Do not start the application, invoke live tests, reset data, or use any command that targets `data/`.
- The test disposition is a downstream contract, so list all 41 methods explicitly or in unambiguous named groups, including the count for each group; Wayfarer fixture details must not be represented as DeTour requirements.

## Implementation Approach

Keep the evidence in a new date-and-ticket-named Markdown record under `ai/thoughts/baselines/`, rather than modifying source documentation or tests. This separates observed baseline facts from the research handoff and gives P00-T02 one stable input. The record will use a short metadata/starting-state section, a verification-results table, and a disposition table with totals.

The alternative of updating the research document was rejected: research contains pre-execution reconnaissance and its timestamped clean snapshot, whereas this ticket requires an execution-time, committed-ready record of commands actually run. A distinct baseline record makes that boundary auditable.

## Phase 1: Capture the protected execution-time snapshot

### Changes

- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — create the record with an ISO-8601 capture timestamp; record `git branch --show-current`, `git rev-parse HEAD`, `git status --porcelain=v1 --untracked-files=all`, `git diff --name-only`, and `git diff --cached --name-only` before verification begins.
- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — enumerate every captured status entry by staged/unstaged/untracked category. Treat entries as user-owned unless known pipeline provenance proves they were created by this workflow; name the research and planning artifacts as workflow-created if present.
- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — evaluate `src/main/java/demo/wayfarer/IntakeService.java` and `src/main/java/demo/wayfarer/TripStore.java` against that snapshot. If listed, mark them pre-existing user-owned and preserve them; if not listed, state that they were not modified at capture time and preserve that fact.

### Automated verification

- [ ] `git status --porcelain=v1 --untracked-files=all` — output is copied to the record and can be compared after all checks.
- [ ] `git diff --name-only` and `git diff --cached --name-only` — establish whether any tracked modifications exist before verification.

### Optional developer checks

- [ ] None.

**Success criteria:** the record contains one reproducible before-verification snapshot, makes provenance explicit for every non-clean entry, and no file outside the record is modified by this phase.

## Phase 2: Execute and record supported baseline checks

### Changes

- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — add a command-results table with the exact shell command, `PASS`/`FAIL`/`NOT RUN`, concise relevant output, and whether the command could have reached the test/build work.
- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — run and record `./mvnw.cmd test -DskipFrontend=true` as the ordinary backend suite. Do not enable `WAYFARER_LIVE_TEST`.
- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — record frontend automated tests as `NOT RUN` after inspecting `frontend/package.json`: no `test` script and no tracked frontend test files exist, so no checkout-supported frontend-test command is available.
- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — run and record `npm run build --prefix frontend` as the frontend production build.
- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — run and record `./mvnw.cmd package` as the packaged-application build, without changing `skipFrontend` or attempting a substitute package command.

### Automated verification

- [ ] `./mvnw.cmd test -DskipFrontend=true` — records the ordinary backend result or wrapper/tooling failure exactly.
- [ ] `npm run build --prefix frontend` — records production TypeScript/Vite result.
- [ ] `./mvnw.cmd package` — records packaged-build result or wrapper/tooling failure exactly.

### Optional developer checks

- [ ] None; application startup and provider-backed live tests are intentionally excluded from the baseline.

**Success criteria:** all four required verification categories have an unambiguous outcome; unavailable frontend tests and any Maven failure include their reason without source/configuration changes.

## Phase 3: Record dispositions and prove implementation scope

### Changes

- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — add the complete disposition inventory: reusable 6 (`TripApplicationTest` transactional reservation/idempotency/stale-state group and `HttpFlowTest.malformedJsonAndUnknownIdsReturnUsefulHttpStatuses`); replace 19 (fixed-scenario calculation, exchange/disruption/recovery, and fixed HTTP workflow groups); remove 16 (model-result/lifecycle, all `IntakeServiceTest`, all live tests, and model-backed HTTP groups).
- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — list every method in each group or point to an exhaustive named-method sublist, explain why the group preserves generic behavior or ties to obsolete Wayfarer/Loomspan behavior, and include totals `6 + 19 + 16 = 41`.
- [ ] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` — append a final-worktree comparison using the same Git status and diff-name commands, identify only the baseline record as this ticket's Step-4 implementation output, and preserve rather than clean pre-existing/workflow artifacts.

### Automated verification

- [ ] `git diff --check` — confirms no whitespace errors in tracked diff content.
- [ ] `git status --porcelain=v1 --untracked-files=all` — compares final entries to the capture and confirms named user files were not changed by this ticket.
- [ ] `git diff --name-only` and `git diff --cached --name-only` — confirms the ticket did not alter tracked application, test, configuration, generated-asset, or database paths.

### Optional developer checks

- [ ] None.

**Success criteria:** later agents can consume a self-contained test-disposition table and final status evidence without inspecting old Wayfarer behavior; no product/test/config/database file is written.

## Test Strategy

Step 3 will define artifact-level completeness checks plus the exact existing build commands this ticket must execute and record. No test source is added: the deliverable is a reliable evidence record, so coverage consists of validating the record's required fields, exhaustive test inventory, and final diff/status scope.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Starting branch/commit/worktree are identified | Baseline record starting-state snapshot. | Re-run status/diff and compare to captured entries. |
| Known modifications are preserved | Named-file status subsection. | Final status/diff verifies no edit to either source path. |
| Checks include commands and outcomes | Baseline command-results table. | Execute backend/build commands; configuration inspection proves frontend tests unavailable. |
| Every test receives a disposition | Exhaustive 41-method inventory and totals. | Artifact completeness/count check against five tracked test files. |
| Only baseline record is implementation output | Final scope-comparison subsection. | `git status`, name-only diffs, and `git diff --check`. |

## Risks and Rollback/Recovery

The only created implementation artifact is additive Markdown. If the record needs correction before commit, edit that record and repeat its final snapshot comparison; never restore or clean the worktree as part of this ticket. Failed or unavailable commands remain evidence, not a reason to change application code. Ignored build output and any pre-existing user/pipeline artifacts are retained and explicitly attributed in the record.

## References

- `ai/thoughts/tickets/2026-09-17-p00-t01-capture-replacement-baseline.md`
- `ai/thoughts/research/2026-09-17-p00-t01-capture-replacement-baseline.md`
- `ai/thoughts/phases/phase-0-baseline-and-boundaries.md`
- `ai/thoughts/phases/CONTINUATION.md`
- `pom.xml`
- `frontend/package.json`
- `README.md`
- `src/test/java/demo/wayfarer/TripApplicationTest.java`
- `src/test/java/demo/wayfarer/HttpFlowTest.java`
- `src/test/java/demo/wayfarer/IntakeServiceTest.java`
- `src/test/java/demo/wayfarer/LivePlanningTest.java`
- `src/test/java/demo/wayfarer/LiveIntakeTest.java`
