# Phase 1 — Platform Reset and Identity

## Outcome

DeTour starts as a Spring Boot and React application with no Loomspan/model dependency, uses a new database, and provides secure self-service accounts with strict per-user isolation.

## Work packages

### 1.1 Remove Loomspan and model behavior

- Remove the Loomspan Maven dependency and version property.
- Remove `SkillTemplate`, skill annotations, execution views, and related exception handling.
- Remove Loomspan configuration, model credentials, observability settings, YAML skills, manifest generation, and live model tests.
- Remove conversational change interpretation and skill-coordination evidence from backend and frontend.
- Replace any retained planning entry points with deterministic application-owned service interfaces.
- Ensure DeTour requires no external model endpoint or API key.

### 1.2 Rebrand and reset configuration

- Rename visible Wayfarer branding to DeTour.
- Change Spring application name, artifact naming, frontend metadata, storage keys, and environment-variable prefix.
- Configure the new DeTour H2 path and new Flyway baseline.
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

- Associate every trip and downstream record with its owning user through an enforceable relational path.
- Scope every read and mutation to the authenticated user.
- Return a non-disclosing not-found response for another user's identifiers.
- Add integration tests proving that guessed IDs cannot cross account boundaries.

## Exit criteria

- The application builds and starts with no Loomspan/model dependency or configuration.
- A user can register, log in, refresh, log out, and log back in.
- Two users cannot read or mutate one another's data.
- A newly registered user has an empty profile.
- No administrator role or default privileged credential exists.

## Annotations

- **[FUTURE]** Email verification, forgotten-password recovery, social login, MFA, administrators, and account deletion.
