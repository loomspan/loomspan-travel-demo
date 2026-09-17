# Phase 0 — Baseline and Boundaries

## Outcome

Establish a safe, testable replacement boundary before removing Wayfarer behavior. Preserve unrelated user work and make the DeTour requirements authoritative for later tickets.

## Work packages

### 0.1 Record the replacement architecture

- Use `app.detour` as the Java package namespace and define consistent module/artifact naming around `detour`.
- Map former Wayfarer capabilities to keep, replace, or remove.
- Record that deterministic Java services replace model orchestration and natural-language interpretation.
- Define the Trip, Itinerary, Booking, User, and catalog ownership boundaries.

### 0.2 Protect the starting repository

- Identify existing uncommitted files before implementation and avoid overwriting unrelated work.
- Capture a baseline test/build result before structural changes.
- Identify tests that protect reusable inventory and transaction behavior versus tests coupled to the obsolete scenario.

### 0.3 Establish migration policy

- Use a new DeTour H2 database name and a fresh Flyway history.
- Do not read, mutate, or automatically delete old Wayfarer database files.
- Document manual cleanup of obsolete local database files separately.
- Treat Boston–New York records as non-migratable fixtures.

## Exit criteria

- The keep/replace/remove matrix is reviewed.
- Package, database, and configuration naming are selected.
- A baseline verification record exists.
- Later tickets do not need to infer requirements from old YAML skills or demo documentation.

## Annotations

- **[UNDECIDED]** The old database filename cleanup procedure should be chosen during implementation without deleting data automatically.
- **[FUTURE]** Importing Wayfarer trips into DeTour is explicitly excluded.
