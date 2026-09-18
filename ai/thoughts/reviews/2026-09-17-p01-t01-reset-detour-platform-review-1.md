# P01-T01 Reset the Application to a Clean DeTour Platform Code Review — Cycle 1

## Scope and Repository State

Reviewed the complete ticket-scoped change against `main`/`origin/main` at merge base `908caa6`, including staged (none), unstaged, deleted, and untracked work. The change replaces the Maven and Java identities, deletes the Wayfarer/Loomspan backend, migrations, tests, frontend workflow, scripts, fixtures, and superseded docs, and adds the minimal DeTour application, clean Flyway V1, reset command, and shell test. Untracked research/planning artifacts are ticket context; no unrelated user changes were identified.

Behavior was traced through Maven frontend packaging, Spring Boot/Flyway startup, static-resource serving, the reset script's confirmation and target boundary, and the React entry point. Security/privacy, lifecycle, persistence, external-boundary, operational, performance, documentation, and test-quality risks were considered; this full-profile review found no new external call, secret/configuration path, runtime model dependency, persistent domain behavior, or unsafe automatic cleanup.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. This review made no implementation-artifact changes.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Retained platform produces the expected JAR | `pom.xml` identifies `app.detour:detour`; Maven retains the React build/copy lifecycle. | `./mvnw.cmd package` produced `target/detour-0.1.0-SNAPSHOT.jar`. | implemented |
| Packaged shell starts without AI/model configuration | `DetourApplication`, lean `application.yml`, and packaged Vite assets contain no model integration. | Local packaged-JAR smoke returned HTTP 200 with `<title>DeTour</title>`. | implemented |
| No executable/product-facing legacy/model remnant | Legacy source, skills, migrations, frontend workflow, scripts, fixtures, and docs are deleted; retained provenance is confined to `ai/thoughts`. | Scoped `rg` clean-break search returned no matches. | implemented |
| Fresh V1 only and no former scenario/history | Sole migration is `V1__detour_platform.sql`, containing only an explanatory comment. | `DetourApplicationTest` uses a unique H2 database and asserts only version `1` plus absence of legacy tables. | implemented |
| Reset is confirmation-gated and exact-target-only | `scripts/reset-detour.ps1` previews `data/detour.mv.db`, returns without `-ConfirmReset`, validates direct-parent placement, and removes only the literal target. | Controlled fixture exercise preserved the target in preview and preserved a sibling after confirmed deletion. | implemented |
| DeTour naming is consistent | Maven coordinates/artifact, `app.detour` namespace, app name, `DETOUR_` variables, frontend package/title, launch config, and run helper all use DeTour. | Clean-break scan and package name/path inspection passed. | implemented |
| Obsolete tests/scripts removed while baseline remains | Legacy `demo/wayfarer` tests and reset/generator/design paths are deleted; Phase 0 baseline stays in `ai/thoughts`. | Repository path inventory verified the replacement test is the only test source. | implemented |
| No deferred domain behavior | The Spring package has only the bootstrap class; no controllers, domain classes, APIs, local storage, fetches, schema tables, or fixtures remain. | Source/API/storage scan and successful shell boot verify the minimal boundary. | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- FAIL — `./mvnw.cmd test -DskipFrontend=true` — the sandbox could not create its forced local repository at `C:\.m2\repository`; this was environmental, not a ticket failure.
- PASS — `./mvnw.cmd test -DskipFrontend=true` — rerun with the normal local Maven cache; 1 test passed.
- PASS — `npm.cmd run build --prefix frontend` — TypeScript/Vite production build succeeded.
- PASS — `rg -n -i "loomspan|skilltemplate|spring-ai|openai|openrouter|wayfarer|WAYFARER_|OPENAI_API_KEY|OPENROUTER_API_KEY" --glob '!ai/thoughts/**' --glob '!target/**' --glob '!data/**' --glob '!frontend/node_modules/**' --glob '!frontend/dist/**'` — no executable/product-facing matches.
- PASS — `./mvnw.cmd package` — frontend install/build, backend test, static resource copy, and Boot repackaging succeeded.
- PASS — `java -jar target/detour-0.1.0-SNAPSHOT.jar --server.port=18082 --spring.datasource.url=jdbc:h2:mem:detour_smoke;DB_CLOSE_DELAY=-1` — local HTTP smoke check returned 200 and a DeTour-only packaged page; process was stopped after the check.
- PASS — `powershell -ExecutionPolicy Bypass -File scripts/reset-detour.ps1` and `powershell -ExecutionPolicy Bypass -File scripts/reset-detour.ps1 -ConfirmReset` — disposable target/sibling fixture proved preview and exact confirmed deletion behavior.

## Residual Risks and Optional Developer Checks

- The review ran with Java 25 while `pom.xml` retains Java release 21; no Java-25-specific source or behavior is used, and Maven's configured Java 21 target remains intact. A Java 21 runtime smoke is optional before a release environment that enforces that runtime.

## Disposition

- `clean`
