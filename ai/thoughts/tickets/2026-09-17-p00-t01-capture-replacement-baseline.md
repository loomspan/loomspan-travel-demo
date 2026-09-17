# P00-T01 — Capture a Safe Wayfarer Replacement Baseline

## Outcome

Create a durable, reproducible record of the repository state before DeTour structural work begins. Later tickets must be able to distinguish reusable behavior from obsolete Wayfarer behavior, preserve user-owned changes, and compare their results with a known baseline instead of guessing what was already broken.

## Requirements

- Record the current Git branch, commit, tracked modifications, and untracked files before any structural change. Treat all pre-existing changes as user-owned unless repository evidence proves they were created by the executing workflow.
- Specifically preserve the currently known modifications to `src/main/java/demo/wayfarer/IntakeService.java` and `src/main/java/demo/wayfarer/TripStore.java`; refresh the status at execution time because the worktree may have changed.
- Run and record the repository's existing backend tests, frontend tests if configured, frontend production build, and packaged-application build using the commands supported by the checkout. Record exact commands, outcomes, and relevant failures without changing product code merely to make the baseline green.
- Classify existing automated tests into three disposition groups: reusable behavior worth preserving, Wayfarer-scenario behavior to replace, and Loomspan/model-coupled behavior to remove. Give each test or cohesive test group a disposition and a concise reason.
- Make the baseline record durable in the repository and concise enough for later implementation and review agents to consume directly.
- Do not modify production code, tests, dependencies, configuration, generated application assets, or database files in this ticket. The only intended repository change is the baseline record.

## Acceptance criteria

- [ ] A committed-ready baseline record identifies the starting branch and commit and lists all tracked and untracked worktree changes observed before verification.
- [ ] The record explicitly identifies the known `IntakeService.java` and `TripStore.java` modifications as pre-existing user work when they are still present, and it does not overwrite or revert them.
- [ ] Exact commands and pass/fail results are recorded for the existing backend tests, configured frontend tests, frontend production build, and packaged-application build; any unavailable check is labeled not run with the reason.
- [ ] Every existing test or clearly named test group is assigned to reusable, replace, or remove, with enough rationale that Phase 1 does not need to infer its intended disposition from old product behavior.
- [ ] A diff confirms that this ticket changed only the baseline record and did not modify application code, tests, configuration, generated assets, or local database files.

## Context

- **Phase:** 0 — Baseline and Boundaries.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-0-baseline-and-boundaries.md`](../phases/phase-0-baseline-and-boundaries.md).
- **Hard dependencies:** none. This is the first DeTour implementation-preparation ticket.
- **Downstream dependency:** P00-T02 uses the baseline and test disposition when defining the replacement boundary.
- **Scope exclusions:** fixing baseline failures; renaming packages or artifacts; removing Loomspan; changing tests; changing database configuration; creating DeTour schema or application behavior; Version 2 Events.
- This ticket should remain small and evidence-oriented for GPT-5.6 Terra: gather the named facts, classify them, and write one baseline record. Do not broaden it into implementation or general repository cleanup.

## Verification

- Re-run `git status --short` after writing the record and compare it with the captured starting status.
- Inspect the final diff to prove that only the baseline record was added.
- Check that every verification result includes an exact command and an unambiguous pass, fail, or not-run outcome.
- Check that the test-disposition inventory has no unexplained test files or groups.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The ticket is non-invasive, but it intentionally requires repository-wide discovery, baseline execution, and judgment about which existing tests protect reusable behavior. Research and planning should remain explicit so later structural work can trust the result.
- **Reassessment triggers:** none.
