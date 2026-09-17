# Phase 1 — Platform Reset and Identity

## Outcome

The Wayfarer application is destructively replaced by a clean DeTour Spring Boot and React application with no Loomspan/model dependency or legacy compatibility path. DeTour starts from a fresh database lineage and provides secure self-service accounts with strict per-user isolation.

## Work packages

### 1.1 Remove Loomspan and model behavior

- Remove the Loomspan Maven dependency and version property.
- Remove `SkillTemplate`, skill annotations, execution views, and related exception handling.
- Remove Loomspan configuration, model credentials, observability settings, YAML skills, manifest generation, and live model tests.
- Remove conversational change interpretation and skill-coordination evidence from backend and frontend.
- Delete obsolete Wayfarer planning, booking, exchange, disruption, recovery, trace, route, and frontend paths rather than retaining adapters or placeholder compatibility interfaces.
- Remove obsolete tests, scripts, generated artifacts, and technical documentation with the paths they describe. Preserve only behavioral requirements explicitly carried into the DeTour roadmap, implemented through new DeTour contracts.
- Ensure DeTour requires no external model endpoint or API key.

### 1.2 Rebrand and reset configuration

- Rename all visible and technical Wayfarer identifiers to DeTour; do not retain aliases, old storage keys, environment-variable fallbacks, or dual naming.
- Change Spring application name, artifact naming, frontend metadata, storage keys, and environment-variable prefix.
- Delete the Wayfarer Flyway migrations and create a fresh DeTour `V1` lineage in the standard `classpath:db/migration` location.
- Configure the DeTour H2 path with no legacy datasource fallback. Document and verify the explicit local database reset required for the destructive schema replacement; startup itself must not delete files.
- Retain Java 21, Spring Boot, React, H2, Flyway, and the packaged single-application deployment shape unless implementation research finds an incompatibility.

### 1.3 Add user accounts and session security

- Add user and credential tables in Flyway.
- Implement self-service registration, login, logout, and current-profile endpoints.
- Hash passwords with an established adaptive password encoder.
- Accept passwords from 12 through 128 characters without forced uppercase, numeric, or symbol composition rules.
- Provide a show/hide password control and allow authenticated password changes only after verifying the current password.
- Keep profile email immutable in the initial release.
- Use secure, HTTP-only, same-site session cookies and CSRF protection appropriate to the React client.
- Do not persist authentication sessions across an application restart; require the user to log in again while preserving all application data.
- Normalize email addresses for uniqueness.
- Prevent user enumeration through authentication errors where practical.
- Require authentication for every application/catalog endpoint; only registration, login, static assets, and demo disclosure remain public.

### 1.4 Enforce tenant isolation

- Scope every user-owned record introduced in this phase to the authenticated user and establish the repository/service pattern later phases must follow.
- Return a non-disclosing not-found response for another user's identifiers.
- Add integration tests proving that guessed IDs cannot cross account boundaries for the resources available in this phase.
- Require each later ticket that introduces Trip or downstream persistence to add ownership through the enforceable `User -> Trip -> downstream data` relational path and its own cross-account tests; do not add speculative compatibility tables in Phase 1.

## Exit criteria

- The application builds and starts with no Loomspan/model dependency or configuration.
- No executable Wayfarer route, schema, migration, compatibility alias, fallback, or transitional application path remains.
- A clean database applies the new DeTour migration lineage, and documented development reset verification succeeds against an obsolete local database.
- A user can register, log in, refresh, log out, and log back in.
- Two users cannot read or mutate one another's data.
- A newly registered user has an empty profile.
- No administrator role or default privileged credential exists.

## Annotations

- **[FUTURE]** Email verification, forgotten-password recovery, social login, MFA, administrators, and account deletion.
