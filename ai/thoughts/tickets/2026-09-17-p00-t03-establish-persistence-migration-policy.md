# P00-T03 - Establish a Safe DeTour Persistence Boundary

> **Status: superseded before Phase 1 ticket authoring.** This ticket records the conservative persistence decision completed during Phase 0. The authoritative roadmap now adopts destructive pre-production schema replacement with no migration or compatibility path. The historical execution details below are retained as provenance and must not be used as Phase 1 requirements.

## Outcome

Define the persistence and migration policy that lets DeTour start with a fresh database while leaving every existing Wayfarer database untouched. Phase 1 must be able to configure the new database and Flyway history without guessing whether old data should be imported, upgraded, deleted, or reused.

## Requirements

- Select and document a new DeTour H2 database name and corresponding configuration convention that cannot accidentally open the existing Wayfarer database. Keep the naming consistent with P00-T02.
- Require a fresh DeTour Flyway history. Existing Wayfarer Flyway versions, Boston-New York records, seeded trips, and scenario fixtures are incompatible and must not be migrated or copied into the DeTour schema.
- Explicitly prohibit application startup, migrations, tests, and cleanup scripts from reading, mutating, renaming, or deleting old Wayfarer database files.
- Define a manual cleanup procedure for developers who later choose to remove obsolete local Wayfarer database files. The procedure must be opt-in, identify exact file patterns or locations from repository evidence, include a preview/confirmation step, and make clear that cleanup is not required for DeTour startup.
- Define clean-database and existing-development-machine expectations for Phase 1: a clean environment creates only the new DeTour database and history; a machine with old Wayfarer files starts DeTour against the new database while leaving the old files byte-for-byte untouched.
- Define rollback/recovery at the policy level: before production data exists, a failed DeTour development reset may remove only explicitly identified DeTour database files after confirmation; it must never broaden the target to Wayfarer or unrelated H2 files.
- Record the policy in durable repository documentation. Do not change datasource configuration, create Flyway migrations, delete files, or start Phase 1 implementation in this ticket.

## Acceptance criteria

- [x] A committed-ready persistence policy names the DeTour database/configuration convention and clearly distinguishes it from the current Wayfarer database.
- [x] The policy requires a fresh Flyway history and explicitly excludes migration or import of Wayfarer schema, trips, and Boston-New York fixtures.
- [x] The policy states that automated DeTour behavior never reads, changes, renames, or deletes old Wayfarer database files.
- [x] An opt-in manual cleanup procedure identifies only evidence-backed Wayfarer targets, previews them before deletion, requires explicit developer confirmation, and is not part of normal startup.
- [x] The clean-machine, existing-machine, and failed-DeTour-reset cases each have an unambiguous expected outcome, including preservation of old Wayfarer files.
- [x] A diff confirms that this ticket changes only policy documentation and does not change configuration, migrations, application code, tests, or database files.

## Context

- **Phase:** 0 - Baseline and Boundaries.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-0-baseline-and-boundaries.md`](../phases/phase-0-baseline-and-boundaries.md).
- **Required predecessor output:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md). Use its naming and persistence boundaries; do not infer them from the P00-T02 ticket alone.
- **Hard dependency:** P00-T02 must be complete so database and configuration naming follow the selected DeTour conventions.
- **Downstream dependency:** Phase 1 platform-reset work implements this policy when configuring H2 and the new Flyway baseline.
- **Scope exclusions:** importing old data; deleting local files; datasource configuration changes; migration scripts; DeTour schema design; catalog fixture design; deployment migration automation; Version 2 Events.
- This is a narrow policy ticket sized for GPT-5.6 Terra. Repository inspection is permitted only to identify the current database naming and safe manual-cleanup targets; implementation stays in Phase 1.

## Verification

- Compare the selected names with [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md) and resolve any inconsistency in the policy before completion.
- Validate the documented cleanup preview against repository evidence without executing deletion.
- Inspect the final diff and filesystem status to confirm that no configuration, migration, application, test, or database file was modified or removed.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The deliverable is documentation, but repository discovery is needed to establish collision-free database naming and a precise cleanup policy, and the decision constrains later persistence and migration work.
- **Reassessment triggers:** none.

## Execution notes

- Fast-track Step 4 selected by the developer for this documentation-only ticket; no research, implementation-plan, or testing-plan artifact was created.
- The ticket originally produced `ai/thoughts/architecture/2026-09-17-p00-t03-detour-persistence-migration-policy.md`. That untracked policy was retired after the developer selected clean-break implementation and is not an active architecture record.
- Repository evidence at implementation time: the current Wayfarer datasource root is `data/wayfarer`; the local legacy files are `data/wayfarer.mv.db` and `data/wayfarer.trace.db`; `scripts/reset-demo.ps1` has the same two literal targets; and the current Wayfarer migrations are `src/main/resources/db/migration/V1__business_schema.sql` through `V6__conversation_drafts.sql`.
- This ticket changes only the policy document and this ticket's completion/context record. Existing configuration, migrations, application code, tests, scripts, and database files remain untouched.
