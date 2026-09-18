# Reset the Application to a Clean DeTour Platform Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-17-p01-t01-reset-detour-platform.md`
- Research: `ai/thoughts/research/2026-09-17-p01-t01-reset-detour-platform.md`
- Outcome: replace the Wayfarer/Loomspan demo with a buildable, packaged, non-AI DeTour shell that has a fresh empty Flyway lineage and an explicit, narrowly scoped local-database reset.

## Current State

The application is wholly owned by the `demo.wayfarer` package: `WayfarerApplication`, `ApiController`, stores, contracts, model services, and the five JUnit classes all assume the Boston--New York scenario and Loomspan. `pom.xml` uses the `demo.loomspan:wayfarer` coordinates and imports both the Loomspan starter and Spring AI BOM; `application.yml` configures an OpenAI-compatible Loomspan connection and a `data/wayfarer` H2 database.

The frontend's `main.tsx` is the complete legacy planning/booking UI, imports the conversation, coordination, guide, and trip-contract components, calls `/api` routes, and writes the `wayfarer.trip` storage key. The legacy database migration sequence is `V1` through `V6`; `design/weekend-inventory.json`, `docs/`, `README.md`, `.env.example`, `.vscode/launch.json`, and several scripts document or run the same scenario. Maven already owns the retained frontend-to-static-JAR build pipeline.

## Desired End State

The executable application has a single `app.detour.DetourApplication` entry point, `app.detour:detour` Maven coordinates, `detour` Spring name, `DETOUR_` environment variables, default `jdbc:h2:file:./data/detour`, and no legacy fallback. A new `V1__detour_platform.sql` is the only Flyway migration and deliberately creates no account, catalog, Trip, itinerary, booking, cancellation, or Event data/model.

The packaged JAR serves a minimal DeTour React shell without contacting or configuring an AI/model service. `scripts/reset-detour.ps1` previews only `data/detour.mv.db`, requires `-ConfirmReset` before deleting it, and startup never deletes files. Historical material below `ai/thoughts/` remains unchanged as the explicitly permitted provenance exception.

| Acceptance criterion | Current flow | Planned result |
| --- | --- | --- |
| Builds/package and starts | Maven packages the old frontend and Boot application | Preserve the pipeline while producing `target/detour-0.1.0-SNAPSHOT.jar` that serves the shell. |
| No AI/model or legacy targets | Loomspan dependencies/config/services/routes/UI are spread across the application | Delete the coupled paths together; retain no alias, adapter, endpoint, credential, or UI trace. |
| Clean Flyway/reset lineage | Legacy `V1`--`V6` and `data/wayfarer` contain fixed scenario data | Replace them with one empty DeTour `V1` and one precise development reset target. |
| Identity consistency | Maven, Java, config, scripts, frontend metadata/storage, and launch configuration use Wayfarer | Change every executable/product-facing identifier to the architecture-record conventions. |
| Minimal scope | Current app implements planning through recovery | Leave only the application/static shell for the later identity tickets. |

## Scope

### In scope

- Renaming/replacing all executable and product-facing application identity surfaces with DeTour conventions.
- Removing the legacy backend, API, model integration, migrations, fixtures, UI, tests, scripts, and documentation.
- Adding the smallest Spring Boot/React/H2/Flyway shell that proves the retained packaging platform.
- A confirmation-gated development reset for the default DeTour H2 file and docs/tests that prove its boundary.

### Out of scope

- Users/accounts/authentication, catalog data or inventory, Trips/itineraries, planning, booking, cancellation, exchange, disruption/recovery, or Version 2 Events.
- Visual-brand decisions or final public/demo copy beyond concise shell and developer setup text.
- Any migration/import/compatibility support for a Wayfarer database or route.
- Rewriting historical planning, architecture, baseline, phase, or ticket records below `ai/thoughts/`.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`. The ticket and replacement architecture are binding for this work: retain Java 21, Spring Boot, React, H2, Flyway, Maven, and one packaged JAR; no replacement AI framework or model integration is allowed.

## Impact and Risk Analysis

- **Destructive local-data boundary:** Existing Wayfarer data is intentionally incompatible and must not be read or removed by the new application. The selected DeTour default is `data/detour`, whose H2 file is exactly `data/detour.mv.db`; the reset script must canonicalize that one target under the repository `data` directory, print it before confirmation, and never enumerate/delete neighbors.
- **Fresh Flyway contract:** The old `V1`--`V6` history cannot coexist with the replacement. A comment-only/empty-schema `V1__detour_platform.sql` establishes a recorded clean lineage without prematurely adding domain tables or fixtures.
- **Packaging regression:** Maven installs/builds the Vite frontend in `generate-resources` and copies `frontend/dist` into the JAR. The replacement must keep that lifecycle and validate the packaged static asset rather than relying only on the Vite development server.
- **Clean-break completeness:** Legacy identifiers appear in source, package-lock metadata, ignored data paths, docs, `.vscode`, and scripts. Scoped searches must intentionally exclude `ai/thoughts/**` and ignored/generated build/data directories, whose historical/procedural references are not runtime targets.
- **Environment limitation:** prior baseline Maven failures were caused by Java not being on `PATH`, while `npm.cmd run build --prefix frontend` passed. This is not authority to alter the retained platform; report any still-present toolchain limitation rather than changing it.

## Implementation Approach

Choose a replacement shell, not a compatibility refactor. Delete `demo.wayfarer` and create the single `app.detour` Boot application with no controller, domain service, or API. Let Spring Boot serve the Vite-built static `index.html`, which is sufficient to demonstrate the retained packaged shape without inventing identity behavior.

Use `data/detour` as the configuration base name because it matches the required `detour` application/configuration convention; H2's concrete file target is therefore `data/detour.mv.db`. Do not retain the old `.trace.db` target: Loomspan trace persistence is removed, so a second DeTour target would be an unjustified compatibility remnant. The reset command will be renamed from `reset-demo.ps1` to `reset-detour.ps1`; its no-switch invocation is a non-destructive preview and `-ConfirmReset` is the explicit deletion gate.

Replacing the old frontend with a small static React component is preferable to retaining any old route/contract layer or adding an API merely to make the shell interactive. The only requested product statement is a coherent DeTour shell, so no local-storage key is needed. Product docs are replaced by a concise DeTour README; scenario-specific `docs/` and `design/` material is deleted rather than rebranded.

## Phase 1: Establish the DeTour build, runtime, and persistence shell

### Changes

- [x] `pom.xml` — change group/artifact coordinates to `app.detour:detour`; remove `loomspan.version`, `spring-ai.version`, Spring AI dependency management, and the Loomspan starter; retain Java 21, Spring Boot, H2, Flyway, Web MVC, test, and the frontend build/copy lifecycle so the resulting JAR name is `detour-0.1.0-SNAPSHOT.jar`.
- [x] `src/main/java/demo/wayfarer/` — delete `WayfarerApplication`, `ApiController`, `ApiProblem`, contracts, stores/calculators, assessment/intake/model services, and `TravelSkills`; none has a DeTour-shell responsibility.
- [x] `src/main/java/app/detour/DetourApplication.java` — add the minimal `@SpringBootApplication` in package `app.detour`, with only the normal `main` bootstrap.
- [x] `src/main/resources/application.yml` — replace all Wayfarer, Loomspan, model, credential, and trace configuration with loopback server configuration, `spring.application.name: detour`, and a `DETOUR_DATABASE_URL`-overridable H2 URL defaulting to `jdbc:h2:file:./data/detour;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000`; preserve no legacy datasource variable/fallback and add no startup cleanup behavior.
- [x] `src/main/resources/skills/` and `scripts/generate-skill-manifests.py` — delete all YAML manifests and their generator because the manifest format and model behavior are removed.
- [x] `src/main/resources/db/migration/V1__business_schema.sql` through `V6__conversation_drafts.sql` — delete the entire legacy lineage, including its catalog/fixture data.
- [x] `src/main/resources/db/migration/V1__detour_platform.sql` — add the sole fresh V1 migration, containing only an explanatory SQL comment (no domain tables, scenario data, or imported Flyway state), so Flyway records one clean DeTour baseline at startup.
- [x] `src/test/java/demo/wayfarer/` — delete all five legacy test classes, including live-provider tests and the old transactional/HTTP contracts; preserve their Phase 0 disposition in `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` rather than retaining a test harness.
- [x] `src/test/java/app/detour/DetourApplicationTest.java` — add a focused boot/migration test using an isolated H2 database that proves the DeTour context starts, Flyway records only version `1`, and no legacy business/fixture tables are introduced.

### Automated verification

- [x] `./mvnw.cmd test -DskipFrontend=true` — the isolated DeTour shell context and clean V1 migration pass without Loomspan or a model credential.

### Optional developer checks

- [ ] None in this phase; packaged startup and static serving are verified after the frontend is rebuilt in Phase 3.

### Success criteria

The backend source/test namespace is exclusively `app.detour`; Flyway starts a new H2 database with exactly its V1 record; and a Maven test run needs no provider, model endpoint, API key, or Wayfarer configuration.

## Phase 2: Replace the frontend and executable identity surfaces

### Changes

- [x] `frontend/package.json` and `frontend/package-lock.json` — rename the package/application identifier to `detour-frontend` while retaining the existing React, TypeScript, and Vite toolchain and `build` script.
- [x] `frontend/index.html` — replace the document title and metadata with DeTour identity, with no Wayfarer reference.
- [x] `frontend/src/main.tsx` and `frontend/src/style.css` — replace the trip/API/local-storage workflow with a minimal accessible DeTour application shell that performs no fetch, has no local-storage compatibility key, and describes no deferred domain behavior as implemented.
- [x] `frontend/src/Conversation.tsx`, `frontend/src/Coordination.tsx`, `frontend/src/DemoGuide.tsx`, and `frontend/src/types.ts` — delete the model conversation, execution trace, walkthrough, and trip-contract components with the old workflow.
- [x] `frontend/vite.config.ts` — retain the Vite/React build configuration but remove the obsolete `/api` proxy if the static shell has no backend API; do not create another transitional route.
- [x] `.vscode/launch.json` — rename the launch configuration and update its `mainClass` to `app.detour.DetourApplication`.
- [x] `.env.example` — replace model credentials and `WAYFARER_*` variables with the supported optional `DETOUR_PORT` and `DETOUR_DATABASE_URL` settings only.
- [x] `scripts/run.ps1` — point the launch helper to `target/detour-0.1.0-SNAPSHOT.jar` while retaining its Java selection behavior.

### Automated verification

- [x] `npm.cmd run build --prefix frontend` — TypeScript and Vite build the minimal DeTour shell.
- [x] `./mvnw.cmd package` — rebuild the frontend through the retained Maven lifecycle and package it with the DeTour application.

### Optional developer checks

- [x] Start `target/detour-0.1.0-SNAPSHOT.jar` on an unused loopback port and request `/`; confirm the returned packaged page identifies DeTour and no Vite dev server is required.

### Success criteria

The only runtime/frontend identity is DeTour, no frontend request or storage key preserves the old API, and Maven produces the expected JAR with built static assets.

## Phase 3: Make the clean break operationally safe and document it

### Changes

- [x] `scripts/reset-demo.ps1` — delete the Wayfarer-named reset path rather than leaving a renamed-target compatibility command.
- [x] `scripts/reset-detour.ps1` — add a development-only reset command that resolves the repository data directory and literal `detour.mv.db` target, prints `data/detour.mv.db` as the preview, returns without deletion unless `-ConfirmReset` is supplied, verifies the resolved file remains directly beneath that data directory, and removes only that exact file when confirmed. It must not inspect/delete `wayfarer` files or arbitrary siblings.
- [x] `scripts/verify-design.py` and `design/weekend-inventory.json` — delete the old Boston--New York calculator and fixture because neither is a DeTour-shell input.
- [x] `README.md` — replace the demo/model walkthrough with concise DeTour build, packaged run, `DETOUR_` configuration, and reset instructions; explicitly state the reset target, preview-before-confirmation behavior, and that startup never deletes local files.
- [x] `docs/calculated-combinations.md`, `docs/design-brief.md`, `docs/first-demo-scenario.md`, `docs/implementation-notes.md`, `docs/verification.md`, and `docs/workspace-and-contracts.md` — delete superseded product/technical documentation rather than preserving Wayfarer scenario/model claims.
- [x] `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md` and the rest of `ai/thoughts/` — leave untouched; it is the intentional historical exception and retains the test-disposition evidence needed by later tickets.

### Automated verification

- [x] `rg -n -i "loomspan|skilltemplate|spring-ai|openai|openrouter|wayfarer|WAYFARER_|OPENAI_API_KEY|OPENROUTER_API_KEY" --glob '!ai/thoughts/**' --glob '!target/**' --glob '!data/**' --glob '!frontend/node_modules/**' --glob '!frontend/dist/**'` — exit with no runtime/product-facing matches.
- [x] `powershell -ExecutionPolicy Bypass -File scripts/reset-detour.ps1` followed by a controlled fixture check with `-ConfirmReset` — prove preview/no deletion before confirmation and exact-target-only deletion, including preservation of an unrelated sibling file.

### Optional developer checks

- [ ] With the packaged app stopped, run `./scripts/reset-detour.ps1`, inspect the printed target, then intentionally run `./scripts/reset-detour.ps1 -ConfirmReset` only when discarding the local DeTour database is desired.

### Success criteria

The only disposable default target exposed by docs/script is `data/detour.mv.db`; the command cannot delete an unrelated neighbor, startup performs no file deletion, and scoped legacy/model searches are empty apart from preserved historical records intentionally excluded from the search.

## Test Strategy

Step 3 will define the exact DeTour context/migration test and a controlled PowerShell reset-boundary exercise. Development gates are the focused backend test, the Windows-safe `npm.cmd` frontend production build, then Maven `package`; the package/startup check proves the embedded frontend rather than a development proxy. Search-based checks are required because this ticket's principal behavior is removal of cross-cutting contracts.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Retained platform builds the expected JAR | `pom.xml`, `DetourApplication`, Maven frontend resource copy | frontend build and `./mvnw.cmd package`; assert `target/detour-0.1.0-SNAPSHOT.jar` exists |
| Packaged app starts and serves shell without AI | app/config/frontend static output | start JAR with only `DETOUR_` variables and request `/` |
| No runtime model/Wayfarer remnants | deletion of legacy packages, skills, config, UI, docs/scripts | scoped `rg` command, with `ai/thoughts` documented as exception |
| Fresh V1 only, no scenario data/history | new `V1__detour_platform.sql`, old migrations deleted | isolated H2/Flyway test asserts only version `1` and absence of legacy domain tables |
| Safe reset | `scripts/reset-detour.ps1`, README | controlled preview/confirm/sibling-preservation exercise |
| Consistent DeTour naming | POM, Java package, config/env, frontend metadata, launch/run scripts | source/config identity scan and JAR-name assertion |
| Obsolete tests/scripts removed; disposition remains | deleted Wayfarer test/script paths; unchanged baseline record | path checks plus baseline record existence |
| No deferred domain behavior | no controller/services/domain contracts/API frontend calls/schema | source/path scan and context boot test |

## Risks and Rollback/Recovery

This is an authorized clean break; keeping the old application as a fallback would violate the ticket. If a post-change issue is found before a release, restore the repository revision rather than introducing a dual path. The DeTour reset is intentionally irreversible for `data/detour.mv.db`, so its preview and confirmation gate are the recovery boundary; it must never target legacy or unrelated user files. Fail a build/test due to missing Java or npm tooling visibly rather than weakening the retained platform or adding a runtime workaround.

## References

- `ai/thoughts/tickets/2026-09-17-p01-t01-reset-detour-platform.md`
- `ai/thoughts/research/2026-09-17-p01-t01-reset-detour-platform.md`
- `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`
- `ai/thoughts/baselines/2026-09-17-p00-t01-capture-replacement-baseline.md`
- `pom.xml`, `src/main/resources/application.yml`, `frontend/src/main.tsx`, and `scripts/reset-demo.ps1`
