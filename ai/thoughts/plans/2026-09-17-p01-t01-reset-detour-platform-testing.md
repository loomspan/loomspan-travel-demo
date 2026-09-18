# Reset the Application to a Clean DeTour Platform Testing Plan

## Change Summary

The change deletes the Wayfarer/Loomspan demo and replaces it with a minimal DeTour Spring Boot and React shell. It changes Maven/runtime identifiers, frontend metadata and static output, database lineage, the disposable local H2 target, scripts, and product documentation while deliberately adding no domain API or data.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Boot/Flyway shell | A replacement application can compile while still loading old migrations, model configuration, or fixed fixture tables. | Isolated Spring Boot + H2 test proves context startup and exactly the new V1 lineage. |
| Maven packaging | Static shell may build independently but be omitted from the executable JAR. | Frontend production build, Maven package, JAR existence, and packaged HTTP response check. |
| Clean-break removal | A legacy dependency, source package, environment variable, API/storage key, script, fixture, or docs surface may survive. | Scoped identifier/path scans that exclude only intentional `ai/thoughts` provenance and generated/ignored outputs. |
| Development reset | A preview could delete early, a confirmation could delete a sibling, or reset could accidentally broaden to legacy data. | Controlled disposable-file exercise for no-confirmation, exact confirmed target, and sibling preservation. |
| Scope | A convenience endpoint/table/fixture could accidentally reintroduce a deferred capability. | Package/migration/frontend scans plus no-controller DeTour shell test. |

## Existing Coverage and Environment Constraints

All five existing JUnit classes under `src/test/java/demo/wayfarer/` assert obsolete contracts. The Phase 0 baseline classifies all 41 methods as later replacement or removal work, so none is retained as a DeTour contract; the baseline record itself remains available. There are no tracked frontend test files or frontend test script, only `npm.cmd run build --prefix frontend`.

Use JUnit/Spring Boot Test already supplied by `spring-boot-starter-test`; do not add a model mock, a provider test, a frontend test framework, or a new external service. The previous baseline found Java unavailable on `PATH`, so Maven test/package may remain environment-blocked. On Windows, use `npm.cmd`, because PowerShell execution policy can block `npm.ps1`. The reset verification is file-destructive only for the exact disposable target and must not run if a user-owned `data/detour.mv.db` is already present.

## Failing Test First

- Name: `DetourApplicationTest.startsWithOnlyFreshDetourMigration`
- Type: Spring Boot integration test with isolated in-memory H2
- Location: `src/test/java/app/detour/DetourApplicationTest.java`
- Arrange/Act/Assert: configure a unique in-memory H2 datasource; start the DeTour application context; query Flyway history; assert its sole applied migration has version `1`; assert legacy business fixture tables such as `travel_service`, `hotel_night`, `trip`, `assessment`, `booking`, and `intake_draft` are absent.
- Expected pre-fix failure: before the reset there is no `app.detour` application and the legacy V1--V6 schema creates the listed scenario tables, so the new test cannot compile/start and would not observe a sole clean V1 record.

## Tests to Add or Update

### 1. `startsWithOnlyFreshDetourMigration`

- Type: Spring Boot integration test
- Location: `src/test/java/app/detour/DetourApplicationTest.java`
- Proves: the minimal DeTour context starts without model/provider configuration; Flyway applies a new lineage beginning and ending at V1; the migration creates no old Boston--New York domain/schema data.
- Inputs/fixture: unique `jdbc:h2:mem:detour-test-<unique>;DB_CLOSE_DELAY=-1` supplied as a test property; no files, model credentials, or network.
- Doubles or boundary isolation: none; exercise actual Boot/H2/Flyway auto-configuration.
- Edge cases: explicitly assert that the expected legacy domain tables are absent, not merely that Flyway ran.

### 2. `reset-detour.ps1 preview and confirmation boundary`

- Type: controlled PowerShell script integration check (no new test framework)
- Location: `scripts/reset-detour.ps1`, exercised from the repository root during Step 4
- Proves: default invocation prints only `data/detour.mv.db` and does not remove it; `-ConfirmReset` removes that exact file; an adjacent sentinel such as `data/unrelated.mv.db` remains; no `wayfarer` target is inspected or removed.
- Inputs/fixture: only create `data/detour.mv.db` and `data/unrelated.mv.db` after verifying neither is an existing user-owned DeTour database. Remove only the fixture/sentinel after evidence is captured, retaining any preexisting unrelated file untouched.
- Doubles or boundary isolation: filesystem-only local fixture; stop the application first and never invoke against a custom database location.
- Edge cases: absent target is a harmless confirmed no-op; path canonicalization must reject any path outside the direct repository data directory.

### 3. `minimal packaged DeTour shell`

- Type: package/startup smoke verification
- Location: `pom.xml`, `frontend/index.html`, `frontend/src/main.tsx`, and the generated `target/detour-0.1.0-SNAPSHOT.jar`
- Proves: Maven embeds Vite output in the Boot JAR; a clean application starts on loopback with no model endpoint/credential; `GET /` returns the DeTour shell.
- Inputs/fixture: a free loopback port through `DETOUR_PORT`; an isolated disposable `DETOUR_DATABASE_URL` only if needed to avoid a preexisting default database.
- Doubles or boundary isolation: no Vite dev server, model service, provider, or remote request; poll the local process only.
- Edge cases: launch with no `OPENAI_*`, `OPENROUTER_*`, `WAYFARER_*`, or Loomspan variables set; ensure static content says `DeTour` and does not say `Wayfarer`.

### 4. `clean-break scoped search`

- Type: repository static-contract check
- Location: all tracked executable/product-facing paths except `ai/thoughts/**`
- Proves: removed dependencies/configuration, legacy package/imports, routes/storage keys, schema/fixtures, model identifiers, credentials, and transitional names are not retained.
- Inputs/fixture: `rg` patterns for `loomspan`, `skilltemplate`, `spring-ai`, `openai`, `openrouter`, `wayfarer`, `WAYFARER_`, `OPENAI_API_KEY`, and `OPENROUTER_API_KEY`; separately assert the intentional historical matches remain only under `ai/thoughts/**`.
- Doubles or boundary isolation: exclude ignored `target/**`, `data/**`, `frontend/node_modules/**`, and `frontend/dist/**` to avoid stale/generated artifacts; do not hide source, config, scripts, docs, or `.vscode`.
- Edge cases: inspect `package-lock.json`, `.env.example`, launch/run/reset scripts, and migration filenames in addition to Java/React source.

## Safe Verification Commands

- Focused: `./mvnw.cmd test -DskipFrontend=true`
- Related suite: `npm.cmd run build --prefix frontend`
- Full safe suite: `./mvnw.cmd package` (runs the retained frontend build/copy lifecycle and backend tests; use only after the focused Maven test and frontend build)
- Static clean-break gate: `rg -n -i "loomspan|skilltemplate|spring-ai|openai|openrouter|wayfarer|WAYFARER_|OPENAI_API_KEY|OPENROUTER_API_KEY" --glob '!ai/thoughts/**' --glob '!target/**' --glob '!data/**' --glob '!frontend/node_modules/**' --glob '!frontend/dist/**'`
- Packaged smoke: set an unused `DETOUR_PORT`, run `java -jar target/detour-0.1.0-SNAPSHOT.jar`, then use a local HTTP request to `/` and stop the process; record a toolchain failure as not run/fail, not as a product regression.

## Optional Developer Checks

- With the packaged app stopped and only after reviewing the printed target, run `./scripts/reset-detour.ps1` and then `./scripts/reset-detour.ps1 -ConfirmReset` to discard the default local DeTour database. This is nonblocking once the controlled fixture check has passed.

## Exit Criteria

- [ ] The planned red test fails for the intended reason before implementation, when applicable.
- [x] The DeTour Boot/Flyway test passes using isolated H2 and no model/provider setup.
- [x] `npm.cmd run build --prefix frontend` succeeds.
- [x] `./mvnw.cmd package` succeeds and produces `target/detour-0.1.0-SNAPSHOT.jar`, or an environment/toolchain block is recorded accurately.
- [x] The packaged JAR starts locally and returns its embedded DeTour frontend shell without a Vite server or external AI service.
- [x] The reset preview/confirmation/sibling-preservation exercise passes without touching user-owned database files.
- [x] Scoped searches find no removed identifier in executable/product-facing scope and document `ai/thoughts/**` as the only intentional historical exception.
- [x] Legacy Wayfarer/Loomspan tests/scripts are gone, the Phase 0 disposition record remains, and no deferred domain behavior has been introduced.

## Implementation Verification Note

The planned pre-fix red test was not runnable before source replacement: the first Maven invocation was blocked before compilation because its sandboxed local repository resolved to inaccessible `C:\.m2\repository`. Post-change clean Maven verification passed after using the normal local cache outside that sandbox. The isolated boot test, static scans, packaged HTTP smoke test, and controlled reset exercise provide the executable replacement evidence.
