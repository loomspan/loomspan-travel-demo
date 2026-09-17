# Phase 0 — Baseline and Boundaries

## Status

Complete. The original persistence-preservation decision from work package 0.3 was superseded before Phase 1 ticket authoring by the roadmap's development-stage clean-break policy. Phase 0 evidence remains historical context, not a compatibility constraint.

## Outcome

Establish a safe, testable replacement boundary before removing Wayfarer behavior. Preserve unrelated user work and make the DeTour requirements authoritative for later tickets.

## Work packages

### 0.1 Record the replacement architecture

- Use `app.detour` as the Java package namespace and define consistent module/artifact naming around `detour`.
- Map former Wayfarer capabilities to keep, replace, or remove.
- Record that Loomspan/model orchestration and natural-language interpretation are removed; deterministic Java services own explicit planning, ranking, explanation, and validation workflows.
- Define the Trip, Itinerary, Booking, User, and catalog ownership boundaries.

### 0.2 Protect the starting repository

- Identify existing uncommitted files before implementation and avoid overwriting unrelated work.
- Capture a baseline test/build result before structural changes.
- Identify tests that protect reusable inventory and transaction behavior versus tests coupled to the obsolete scenario.

### 0.3 Establish migration policy

- This work package originally selected a separate DeTour database and protected old Wayfarer database files from automated cleanup.
- That preservation policy is superseded. Phase 1 instead replaces the old Flyway chain with a fresh DeTour `V1` lineage and treats pre-production Wayfarer databases as disposable development state.
- No compatibility migration, import, fallback, parallel schema, or legacy cleanup architecture is required.
- Database removal remains an explicit developer reset rather than a hidden application-startup side effect.

## Exit criteria

- The keep/replace/remove matrix is reviewed.
- Package, database, and configuration naming are selected.
- A baseline verification record exists.
- Later tickets do not need to infer requirements from old YAML skills or demo documentation.

## Annotations

- The completed baseline and architecture records are retained as planning provenance. Where they conflict with the clean-break policy, the current roadmap governs.
