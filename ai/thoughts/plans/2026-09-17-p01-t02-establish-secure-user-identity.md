# Establish Secure DeTour Accounts and Tenant Isolation Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-17-p01-t02-establish-secure-user-identity.md`
- Research: `ai/thoughts/research/2026-09-17-p01-t02-establish-secure-user-identity.md`
- Outcome: Persist ordinary DeTour accounts, authenticate them with non-persistent server sessions, and expose a same-origin, CSRF-protected identity API with an enforceable ownership foundation.

## Current State

The repository is the P01-T01 minimal baseline: `DetourApplication` only boots Spring, `application.yml` uses a file-backed H2 database, and `V1__detour_platform.sql` is intentionally schema-empty. `pom.xml` has MVC, JDBC, Flyway, H2, and test starters but no Spring Security or password encoder. The packaged React build is served from Spring Boot static resources; it currently makes no API calls. The only test (`DetourApplicationTest`) starts an in-memory H2 context and asserts that only migration `1` exists.

There are no current HTTP handlers, identity records, sessions, roles, user-owned records, or API identifiers. The architecture requires all future user-owned persistence to follow `User -> Trip -> downstream data` and be scoped by the authenticated user. P01-T03 is explicitly downstream of the identity/session/CSRF contract established here.

## Desired End State

The backend will provide these stable, same-origin JSON contracts for P01-T03. Password values are request-only and never appear in success or error bodies.

| Endpoint | Access | Request | Successful response |
| --- | --- | --- | --- |
| `POST /api/auth/register` | public, CSRF protected | `{ "email", "password" }` | `201` with `{ "email" }`; creates an account and its authenticated session |
| `POST /api/auth/login` | public, CSRF protected | `{ "email", "password" }` | `204`; creates an authenticated session |
| `POST /api/auth/logout` | authenticated, CSRF protected | none | `204`; clears the security context and invalidates the active session |
| `GET /api/profile` | authenticated | none | `200` with `{ "email" }` for the session principal only |
| `PUT /api/profile/password` | authenticated, CSRF protected | `{ "currentPassword", "newPassword" }` | `204`; replaces the credential only when the current password verifies |

The static application shell and generated static assets remain public so the public identity UI and its demo disclosure can load. No API route accepting a user identifier will be added: the only profile is the authenticated principal's profile. This is the smallest contract that permits P01-T03 while preventing the phase from inventing a cross-user profile capability.

All identity errors will use one JSON error envelope, with a stable machine code, a user-safe message, and field errors that name only invalid fields/rules without echoing submitted values. The planned mappings are: `400 VALIDATION_FAILED` for valid JSON failing request validation, `400 MALFORMED_REQUEST` for unreadable JSON, `400 CURRENT_PASSWORD_INVALID` when the authenticated caller cannot verify the supplied current password, `401 UNAUTHENTICATED` for a protected request without a usable session, `401 AUTHENTICATION_FAILED` for any syntactically valid unsuccessful login, `403 CSRF_INVALID` for a rejected unsafe request, `404 RESOURCE_NOT_FOUND` for an authenticated request to a missing application resource, and `409 EMAIL_UNAVAILABLE` for a normalized-email uniqueness conflict. The duplicate-registration message may say that an account cannot be created with that email; it must not identify or describe an existing account. Password mismatch uses a non-secret generic message and never returns either password.

The acceptance criteria map as follows:

- Migration and restart persistence: a new `V2` migration creates the identity data while default in-memory servlet sessions are intentionally not made persistent.
- Password rules and secrecy: one shared request validator counts 12--128 Unicode code points, has no composition test, and passes values only to an adaptive encoder and verifier.
- Normalization/immutable profile: canonicalize accepted email by trimming and lowercasing with `Locale.ROOT`, enforce a unique canonical database value, and expose no email-write operation.
- Session, logout, cookies, and CSRF: Spring Security owns the security context, session fixation protection, session invalidation, secure cookie attributes, and a cookie/header CSRF exchange.
- Authorization/isolation: all non-public API paths require authentication, and every profile read/mutation loads the authenticated user rather than accepting a client-supplied owner. There are no foreign identifiers in this phase to probe or a user lookup endpoint to leak information from.
- Empty, unprivileged accounts and safe failures: the schema contains only identity data, no seed SQL, roles, or user-owned domain tables; controller advice emits safe contract errors without stack traces or secrets.

## Scope

### In scope

- A Flyway `V2` identity schema for DeTour users and credential hashes.
- Spring Security, an adaptive password encoder, in-memory servlet sessions, secure session/CSRF cookie configuration, and same-origin authorization rules.
- Registration, login, logout, current-profile, and password-change services/controllers plus safe error handling.
- Account normalization, database uniqueness, authenticated-principal ownership wiring, and test-only fixtures/helpers.
- HTTP integration, migration, restart, cookie/CSRF, failure, and two-session isolation coverage.

### Out of scope

- React identity views, disclosure copy, browser storage inspection, accessibility work, and protected client routing (P01-T03).
- Email changes, verification, recovery, social login, MFA, deletion, administrators, roles, and default credentials.
- Trip, itinerary, catalog, booking, Event, or other user-owned tables/endpoints; no placeholder identifier endpoint is added merely to demonstrate future tenancy.
- Cross-origin/CORS support, an identity provider, an external session store, or a production TLS/proxy deployment mechanism.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

The applicable ticket and architecture constraints are nevertheless binding: retain the Java/Spring Boot/React/H2/Flyway packaged-app baseline; do not revive Wayfarer/Loomspan behavior; do not silently reset the local database; use the DeTour namespace; keep sessions non-persistent; and preserve the relational ownership rule for later domain work.

## Impact and Risk Analysis

- **Credential safety:** BCrypt-style byte truncation is unsuitable for a 128-character contract. Configure Spring Security's Argon2id password encoder (and its required provider dependency) so complete accepted passwords are hashed adaptively. Password request records must not be serialized, logged, or used as error detail.
- **Duplicate-registration race:** service-level lookup is not enough. A unique canonical-email constraint is authoritative; translate a concurrent duplicate-key failure to the same `409 EMAIL_UNAVAILABLE` contract.
- **Session lifecycle:** retain Spring's normal servlet `HttpSession` security-context storage and do not add Spring Session/JDBC/Redis persistence. This makes restart invalidation a deliberate property while the file-backed H2 user rows survive.
- **HTTPS/local development:** set `JSESSIONID` and CSRF cookie `Secure`, `HttpOnly` (session only), `SameSite=Lax`, and `Path=/` in the production/default configuration. Keep the secure-cookie setting overridable only through a documented `DETOUR_` application property for loopback HTTP tests/development; a deployed HTTPS configuration must leave it enabled. Do not add CORS because the packaged client is same-origin.
- **CSRF bootstrap:** the React client needs a readable anti-CSRF cookie but must never read a session credential. Use `CookieCsrfTokenRepository` with an `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header, plus a SPA request handler/filter that eagerly initializes or refreshes the CSRF cookie on ordinary static/API GET responses. This avoids creating an extra anonymously accessible API endpoint beyond the ticket's public surface.
- **Authorization and privacy:** explicit public matchers are limited to registration, login, the static shell/assets, and the eventual static demo disclosure. All other application/catalog paths challenge unauthenticated callers. A custom authentication entry point and access-denied handler must preserve the JSON contract instead of redirects or framework HTML.
- **Schema/deployment:** this is a clean-break DeTour `V2` addition to the already-fresh `V1` lineage. It preserves new DeTour account rows, does not reset existing files, and has no old Wayfarer compatibility/migration path. Rollback is code/migration rollback before deployment or, for disposable development data, an explicit reset—not automatic deletion.

## Implementation Approach

Use a focused `app.detour.identity` slice: immutable request/response records, an email canonicalizer and password validator, a JDBC-backed `DetourUserRepository`, a transaction-owning `IdentityService`, and a REST controller. Spring Security authenticates by loading that same user record by canonical email and stores the resulting principal in the normal in-memory servlet session. This keeps account persistence and authorization ownership in one well-defined backend boundary and gives later repositories a principal user ID to use in relational predicates/joins.

The `detour_user` table will contain an internal immutable primary key, canonical email, Argon2id password hash, and creation timestamp. It will have no roles, privilege columns, seed rows, plaintext password column, or Trip foreign-key placeholders. The public profile response contains only the canonical immutable email, so it exposes no owner identifier that another client can use. A future ticket adding `trip` must add a foreign key to this internal key and scope SQL through the authenticated principal; it must add its own two-user foreign-ID `404` tests.

The alternative of exposing `/api/users/{id}` now is rejected: it would create a profile-discovery contract and identifier attack surface with no product need. The alternative of a public `/api/csrf` route is rejected because the ticket limits public application endpoints; cookie initialization during already-public static loading gives P01-T03 the required token without broadening that surface.

## Phase 1: Add secure identity persistence and shared primitives

### Changes

- [ ] `pom.xml` — add Spring Security and the Argon2 provider dependency managed by Spring Boot; retain the existing MVC/JDBC/Flyway/H2 stack and add no session-store or identity-provider dependency.
- [ ] `src/main/resources/db/migration/V2__create_detour_user_identity.sql` — create `detour_user` with an internal generated primary key, non-null canonical email, non-null adaptive password hash, created timestamp, and a unique constraint/index on canonical email. Do not insert a credential, role, administrator, Trip, or compatibility record.
- [ ] `src/main/java/app/detour/identity/DetourUser.java`, `DetourUserRepository.java`, and `JdbcDetourUserRepository.java` — represent the persisted owner and implement create/find-by-canonical-email/update-password operations with explicit column lists. Keep password hashes internal to the identity/security layer.
- [ ] `src/main/java/app/detour/identity/EmailCanonicalizer.java` and `PasswordPolicy.java` — trim/lowercase accepted email with `Locale.ROOT` before both lookup and insert; validate the normalized value and enforce 12--128 Unicode code points without composition requirements.
- [ ] `src/main/java/app/detour/identity/PasswordEncoderConfiguration.java` — expose one Argon2id `PasswordEncoder` used by registration, login, and password change; do not encode manually or use reversible encryption.
- [ ] `src/test/java/app/detour/DetourApplicationTest.java` — update the clean-context migration assertion for versions `1` and `2`, verify the DeTour identity table is present, retain the legacy-table absence checks, and ensure no seed account is created.

### Automated verification

- [ ] `mvn -DskipFrontend=true -Dtest=DetourApplicationTest test` — a clean in-memory database applies the identity migration and no legacy/schema-seed regression appears.

### Optional developer checks

- [ ] Run the documented explicit development reset only against a disposable local database if an old local Flyway history blocks the new migration; confirm application startup itself did not delete a database file.

**Success criteria:** a fresh Flyway database has exactly `V1` and `V2`, can persist only the minimal account data, enforces canonical-email uniqueness, and contains neither privileged state nor speculative domain data.

## Phase 2: Implement identity HTTP, sessions, CSRF, and authorization

### Changes

- [ ] `src/main/java/app/detour/security/SecurityConfiguration.java` — define a stateless-looking JSON API on top of Spring's in-memory servlet session: protect every non-public application/catalog request, permit only the static shell/assets/disclosure and `POST /api/auth/register`/`POST /api/auth/login`, enable session fixation protection, disable form/basic/login-page redirects, and install JSON `401`/`403` handlers. Do not configure CORS or persistent sessions.
- [ ] `src/main/resources/application.yml` — add documented `detour` security properties and bind them to `server.servlet.session.cookie` and the CSRF cookie: production/default secure cookies, `HttpOnly` session cookies, `SameSite=Lax`, `Path=/`, and an explicit `DETOUR_` override for local HTTP test/development only. Preserve the existing file-backed datasource and loopback defaults.
- [ ] `src/main/java/app/detour/security/SpaCsrfTokenRequestHandler.java` (or equivalent local security component) — configure `CookieCsrfTokenRepository` to issue readable `XSRF-TOKEN` tokens and require the `X-XSRF-TOKEN` header on unsafe requests while ensuring public static loading can initialize the token. Keep `JSESSIONID` unavailable to JavaScript.
- [ ] `src/main/java/app/detour/identity/IdentityService.java`, `IdentityController.java`, `IdentityRequests.java`, and `ProfileResponse.java` — implement the five selected contracts, authenticate registration/login through the configured authentication manager, establish/clear the security context, invalidate logout sessions, load `GET /api/profile` strictly from the principal, and verify/replace the current password transactionally. Registration must create an authenticated session so P01-T03 can enter the empty profile without a second login.
- [ ] `src/main/java/app/detour/api/ApiError.java` and `ApiExceptionHandler.java` — convert validation, malformed JSON, duplicate email, invalid credentials, unauthenticated, CSRF/forbidden, and missing-resource cases to the selected safe JSON error envelope. Do not include exception traces, user records, credential hashes, plaintext values, or rejected password values.
- [ ] `src/main/java/app/detour/identity/DetourUserPrincipal.java` and security user-details/authentication adapters — retain the internal user key in the authenticated principal for future relational ownership, but never serialize it in this phase. Keep all profile reads/writes principal-scoped and accept no owner/user ID request parameter.

### Automated verification

- [ ] `mvn -DskipFrontend=true -Dtest=IdentityApiIntegrationTest test` — exercise the complete same-origin identity contract, CSRF, cookie attributes, authorization, and error envelope against H2.

### Optional developer checks

- [ ] Start the packaged application behind the intended HTTPS termination and inspect `Set-Cookie`: `JSESSIONID` is `Secure; HttpOnly; SameSite=Lax`; the separate `XSRF-TOKEN` is readable only because P01-T03 must echo it as a header.

**Success criteria:** valid registration starts a server session; login/logout/profile/password-change work only as specified; old credentials stop authenticating after a successful change; unsafe requests without the CSRF header/token do not mutate data; and unauthenticated/non-public paths do not redirect to or expose an HTML login page.

## Phase 3: Prove persistence lifecycle, privacy, and tenant isolation

### Changes

- [ ] `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java` — add representative HTTP integration coverage for password boundaries, normalization/uniqueness, generic login failure, immutable profile behavior, no credential disclosure, session/logout transitions, CSRF, cookie attributes, static-public/API-protected routing, safe malformed/missing errors, and two independent authenticated cookie jars.
- [ ] `src/test/java/app/detour/identity/ApplicationRestartIntegrationTest.java` — start two application contexts sequentially against one temporary file-backed H2 database, retain the first session cookie for the second context, and prove the account persists but the stale session is rejected until a new login succeeds.
- [ ] `src/test/java/app/detour/identity/IdentityTestSupport.java` — centralize sanitized valid email/password builders, CSRF cookie/header acquisition, separate authenticated clients, and database cleanup so isolation tests never accidentally share a session.
- [ ] `README.md` — add only the operator-facing identity runtime facts needed by this ticket: accounts persist in the configured DeTour database, servlet sessions do not survive restart, HTTPS deployments require the secure-cookie default, and reset remains explicit. Do not document passwords, test accounts, or a default administrator because none exist.

### Automated verification

- [ ] `mvn -DskipFrontend=true -Dtest=IdentityApiIntegrationTest,ApplicationRestartIntegrationTest,DetourApplicationTest test` — prove the ticket-scoped backend behavior without a frontend build.
- [ ] `mvn test` — run the repository-standard full test lifecycle, including the configured frontend production build.
- [ ] `mvn package` — produce the single packaged JAR after the full test lifecycle.

### Optional developer checks

- [ ] With the packaged JAR, register an account, restart the process without resetting `data/detour`, observe the old session fail, then log in again and observe the account remain available.

**Success criteria:** tests distinguish two independent users on every available user-owned operation. Each can read only its own `/api/profile`, change only its own credential, and logout only its own session; the contract exposes no foreign owner ID to guess. Authenticated unknown application paths return the safe `404` envelope rather than user or credential information.

## Test Strategy

Step 3 will use HTTP integration tests as the primary boundary because persistence, Spring Security filters, cookies, CSRF, session state, and error serialization interact. Small focused tests may cover email/password primitives, but must not replace HTTP evidence. Tests will use isolated H2 databases and manually separated cookie jars; they will not contact a live service or reuse a shared authenticated session.

The restart test must use a temporary file-backed H2 URL rather than the existing per-class in-memory URL so it proves both required lifecycle halves. Cookie flag assertions remain in a normal test configuration with secure cookies enabled; restart flow may set only the documented local-test override if a real HTTP client refuses to resend a Secure cookie over test HTTP. The full `mvn test` and `mvn package` gates additionally prove the existing Maven/Vite packaging path still works.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Clean migration; accounts persist, sessions do not restart | `V2__create_detour_user_identity.sql`, file H2 config, no persistent session dependency | clean migration test; two-context restart test |
| Inclusive password length/no composition/no plaintext | `PasswordPolicy`, Argon2id encoder, request-only DTOs | 11/12/128/129 boundary and no-hash/plaintext response/database assertions |
| Canonical email and immutable email | canonicalizer, unique constraint, no email mutation route | equivalent duplicate and canonical-login tests; unsupported email-write route/error test |
| Session login/profile/logout/relogin | security configuration and identity controller/service | cookie-jar lifecycle test |
| Password change verifies current and rotates login credential | transactional `IdentityService.changePassword` | wrong current/new length failure; old login denied/new login accepted |
| Secure cookies and CSRF | session/CSRF cookie properties and repository/handler | `Set-Cookie` attributes and missing/invalid CSRF no-mutation tests |
| Public/static surface and protected remainder | explicit matchers plus JSON auth entry point | public shell/auth tests and unauthenticated protected/unknown API tests |
| Two-user isolation/non-disclosure | principal-only profile/password/logout implementation, no owner-ID contract | separate authenticated clients prove no cross-session data/mutation; authenticated missing path has safe `404` |
| No enumeration/credential exposure | generic login errors, duplicate conflict, safe error mapper | existing/nonexistent login body equality; no password/hash/stack in responses |
| Empty ordinary new account | minimal migration with no inserts/roles/domain tables | registration/profile/database schema assertions |
| Stable malformed/missing HTTP errors | request validation and controller advice | malformed JSON, validation, CSRF, unauthenticated, and missing-resource envelope tests |

## Risks and Rollback/Recovery

The principal delivery risks are a CSRF bootstrap that prevents first login/registration, secure cookies that are misconfigured behind HTTPS, a password encoder that silently truncates accepted input, and a controller that bypasses principal-scoped persistence. The phase ordering and contract tests address each directly.

Before shared deployment, rollback is a normal application rollback paired with an appropriate migration policy. In disposable development, use the existing explicit reset procedure if a database must be recreated; never make startup delete `data/detour`. Do not preserve a legacy Wayfarer identity or session path. Account recovery remains deliberately out of scope, so a lost password cannot be reset in this version.

## References

- `ai/thoughts/tickets/2026-09-17-p01-t02-establish-secure-user-identity.md`
- `ai/thoughts/research/2026-09-17-p01-t02-establish-secure-user-identity.md`
- `ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`
- `ai/thoughts/phases/README.md`
- `ai/thoughts/phases/phase-1-platform-reset-and-identity.md`
- `pom.xml`, `src/main/resources/application.yml`, `src/main/resources/db/migration/V1__detour_platform.sql`, and `src/test/java/app/detour/DetourApplicationTest.java`
