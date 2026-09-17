# P00-T02 Define DeTour Replacement Architecture Testing Plan

## Change Summary

P00-T02 adds one documentation-only architecture decision record. The record selects DeTour identifiers and the retained platform shape, disposes every required Wayfarer capability, establishes deterministic/non-AI application ownership, defines domain ownership and lifecycle distinctions, and carries P00-T01's test disposition into Phase 1. It must not change production code, tests, configuration, migrations, generated files, or databases.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Downstream naming contract | One inconsistent identifier causes Phase 1, P00-T03, scripts, and packaging to diverge. | Text-contract check requires `app.detour`, `detour`, `DETOUR_`, frontend name, and `target/detour-0.1.0-SNAPSHOT.jar` in the record. |
| Capability disposition | A category is omitted or defers DeTour behavior to obsolete Wayfarer material. | Matrix-category/disposition check and forbidden-delegation scan. |
| Domain/lifecycle boundary | A future ticket might make catalog data user-owned, allow multiple active bookings, or treat cancellation as an itinerary state. | Required ownership and lifecycle wording check. |
| AI removal boundary | A later implementation might swap Loomspan for another model integration. | Required deterministic-Java and prohibited-integration wording check. |
| Test migration boundary | Useful transaction behavior could be discarded with old scenario/model tests. | P00-T01 totals and reusable/replace/remove disposition check. |
| Documentation-only scope | A Phase 1 change could be introduced under this architecture ticket. | Diff path allowlist and whitespace check. |

## Existing Coverage and Environment Constraints

The repository has five JUnit classes (41 current tests) in `src/test/java/demo/wayfarer`, all tied to the legacy application. P00-T01 already classifies six transaction/HTTP properties for recreation with DeTour contracts, 19 fixed-scenario tests for replacement, and 16 Loomspan/model tests for removal. This ticket changes none of them.

No tracked frontend test command or frontend spec exists; `frontend/package.json` exposes `dev` and `build` only. P00-T01's `npm.cmd run build --prefix frontend` passed, but Maven test/package commands could not start because Java was unavailable on `PATH`. Neither runtime build is necessary proof for a documentation-only diff; do not enable `WAYFARER_LIVE_TEST` or call a provider.

## Failing Test First

- Name: `replacementArchitectureRecordMeetsPhaseZeroContract`
- Type: deterministic documentation-contract shell check (introduced only if the repository later adopts executable documentation checks; otherwise run as an ad hoc verification command for this record)
- Location: `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`
- Arrange/Act/Assert: Read the new record; assert the required naming, all required matrix categories, ownership/lifecycle rules, deterministic-Java/no-AI rule, platform statement, and P00-T01 disposition; fail if any token/section is absent or a prohibited delegating phrase is present.
- Expected pre-fix failure: The architecture-record path does not exist before implementation, so the check fails at its explicit file-existence assertion. It becomes green only after the record contains the complete contract.

## Tests to Add or Update

No production or test-source files should be added or updated for this documentation-only ticket. Perform the following deterministic verification against the architecture record during implementation/review.

### 1. `replacementArchitectureRecordMeetsPhaseZeroContract`

- Type: documentation-contract check.
- Location: `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`.
- Proves: The record contains the selected conventions, platform decision, each required capability disposition, domain ownership/lifecycle constraints, AI prohibition, and P00-T01 test rule.
- Inputs/fixture: The ticket, Phase 0 roadmap/continuation documents, and P00-T01 baseline record; no runtime fixtures.
- Doubles or boundary isolation: None; inspect repository documentation only and make no network, database, provider, or file-deletion call.
- Edge cases: Assert `Canceled Booking` is distinct from itinerary state; `at most one active Booking per Trip` is present; catalog/inventory are shared rather than user-owned; exchange/disruption/recovery are deferred/removed rather than retained; and P00-T01's 6/19/16 disposition is represented.

### 2. `replacementArchitectureRecordHasNoDelegatedOrUnresolvedBehavior`

- Type: documentation static scan.
- Location: `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`.
- Proves: No unresolved placeholder and no direction to derive DeTour behavior from Wayfarer YAML skills, old demo documentation, or current UI copy.
- Inputs/fixture: The completed record text.
- Doubles or boundary isolation: None.
- Edge cases: Permit historical references only when they identify a current artifact being removed/replaced; reject wording that makes it an authority for target behavior.

### 3. `replacementArchitectureDiffIsDocumentationOnly`

- Type: repository diff check.
- Location: repository working tree.
- Proves: The ticket's implementation artifact is confined to `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md` and does not touch code, test, configuration, migration, generated, or database paths.
- Inputs/fixture: `git diff --name-only`, `git status --short`, and `git diff --check` after recording the pre-existing untracked research artifact. `git status` is required because the new record may be untracked and therefore absent from `git diff`.
- Doubles or boundary isolation: None; read-only Git inspection only.
- Edge cases: Preserve existing `ai/thoughts/research/` workflow provenance and do not attribute it to the implementation diff.

## Safe Verification Commands

- Focused: `@('app.detour','Maven artifact','detour','DETOUR_','detour-frontend','target/detour-0.1.0-SNAPSHOT.jar','Java 21','Spring Boot','React','H2','Flyway','6 reusable','19','16','at most one active Booking per Trip','Canceled Booking','deterministic Java','Loomspan','substitute AI') | ForEach-Object { if (-not (Select-String -LiteralPath 'ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md' -SimpleMatch $_ -Quiet)) { throw "Missing required architecture-contract text: $_" } }`.
- Related suite: `rg -n "TODO|TBD|<[^>]+>" ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md; rg -n "(derive|determine|interpret).*(Wayfarer|YAML|demo document|current UI)|(Wayfarer|YAML|demo document|current UI).*(derive|determine|interpret)" ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`.
- Full safe suite: `git diff --check; git diff --name-only; git status --short`.

The related scan needs human interpretation: historical references to Wayfarer are required for the matrix, but no result may say that an obsolete source determines DeTour behavior. The focused command may be adjusted only if implementation uses equivalent explicit prose; it must preserve every underlying assertion rather than weakening the contract.

## Optional Developer Checks

- Review the document as the author of P00-T03 and Phase 1: confirm that the naming table resolves identifiers without prematurely choosing a database filename, and that the domain section leaves schema/API design to later tickets.
- Do not run the existing Maven suite merely to validate this documentation change while Java remains unavailable on `PATH`; if a later environment runs it, report it as baseline/regression information, not as proof of the architecture record's content.

## Exit Criteria

- [x] The planned red test fails for the intended missing-record reason before implementation.
- [x] The architecture record contains every contract item covered by the focused check.
- [x] The delegating/placeholder scan has no unresolved result after contextual review.
- [x] `git diff --check` passes and the changed-path inspection confirms documentation-only scope.
- [x] Acceptance criteria map to executable or directly inspectable evidence.
- [x] Routine automated checks do not perform provider calls, start the application, modify a database, or delete files.
- [x] The matrix, ownership/lifecycle, deterministic-AI, platform, and P00-T01 edge cases above are covered.
- [x] Optional checks are reported as nonblocking and are not represented as already performed.
