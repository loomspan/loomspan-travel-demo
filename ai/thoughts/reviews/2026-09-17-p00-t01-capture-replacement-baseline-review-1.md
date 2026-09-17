# P00-T01 Capture Replacement Baseline Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket, phase authorities, research, implementation plan, testing plan, active design lens, all five current Java test classes, frontend/package and Maven build configuration, and current Git state. The tracked worktree is clean; the untracked research and plan artifacts predate the execution-time snapshot, and the baseline record is the only Step 4 implementation artifact. This review also creates this required audit record.

## Findings

### [P2] Record the blocked PowerShell command without inventing an exit code
- Location: `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md:33`
- Scenario: Running `npm run build --prefix frontend` under the checkout's PowerShell execution policy blocks `npm.ps1` before an npm process begins.
- Impact: The record said the command exited with code 1, but the shell exposes a failed command (`$? = False`) with no npm exit code. A later agent could treat the claimed code as reproducible evidence when it is not.
- Evidence: A fresh invocation produced the execution-policy error, `success=False`, and an empty `$LASTEXITCODE`; `npm.cmd run build --prefix frontend` then ran the same configured script successfully.
- Fix: State the observable blocked-command result and explicitly say that no npm exit code was available.

## Findings Resolved in This Context

- Corrected the frontend documented-invocation row to remove the inaccurate exit-code claim and preserve the actual execution-policy evidence. Re-reviewed the complete record and current scope after the edit; no actionable findings remain.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Starting branch, commit, and worktree changes are recorded before verification | Baseline capture metadata and starting snapshot list `main`, `5fb7d2b45209c9034bf40ce40e6c580618abc5c8`, both empty tracked-diff categories, and all three pre-existing untracked pipeline artifacts. | Fresh `git status --porcelain=v1 --untracked-files=all`, name-only diffs, and review inspection match the recorded categories before this review artifact. | implemented |
| Known IntakeService and TripStore work is preserved | Baseline explicitly records both named paths as absent from the capture and unchanged. | Current staged and unstaged name-only diffs remain empty. | implemented |
| Backend, frontend test availability, frontend build, and package outcomes are exact and unambiguous | Baseline has all required categories, exact commands, outcomes, the frontend-test unavailable reason, failed Maven-wrapper observations, documented PowerShell invocation, and successful `npm.cmd` equivalent. | Fresh backend/package invocations still cannot start Maven without Java; direct PowerShell `npm` is blocked; `npm.cmd` production build passes. | implemented |
| Every existing automated test has a disposition | Baseline lists named test methods in reusable, replace, and remove groups totaling 6 + 19 + 16. | Fresh `rg -n '@Test' src/test/java/demo/wayfarer` counts 41; all five test classes were inspected against the grouping. | implemented |
| Ticket implementation changes only the baseline record | No tracked application/test/configuration/generated/database changes exist. Baseline records its own scope comparison. | Fresh tracked name-only diffs are empty; all working files are untracked pipeline artifacts, including this review artifact. | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `$record = 'ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md'; ...` — required record sections/commands/totals found; `@Test` inventory is 41; no tracked frontend test/spec files; `git diff --check` passed.
- PASS — `npm.cmd run build --prefix frontend` — TypeScript and Vite built 18 modules successfully.
- FAIL — `./mvnw.cmd test -DskipFrontend=true` — Maven wrapper cannot start because Java is unavailable on `PATH`; JUnit did not execute.
- FAIL — `./mvnw.cmd package` — Maven wrapper cannot start because Java is unavailable on `PATH`; packaging did not execute.
- FAIL — `npm run build --prefix frontend` — PowerShell execution policy blocks `npm.ps1` before npm runs; `$?` is `False` and no npm exit code is available.
- PASS — `git status --porcelain=v1 --untracked-files=all; git diff --name-only; git diff --cached --name-only; git diff --check` — no tracked changes or whitespace errors; all observed files are pipeline artifacts.

## Residual Risks and Optional Developer Checks

- A Java 21 runtime on `PATH` is required to execute the ordinary JUnit suite and packaged-application build. The baseline accurately records that environmental limitation; this ticket must not install or configure it.
- The record's final scope snapshot predates this review-cycle correction and this required review audit file. Those additions are pipeline artifacts, not product changes; the final orchestration receipt should preserve that distinction.

## Disposition

- `fixes-applied`
