# Establish Secure DeTour Accounts and Tenant Isolation Testing Plan

## Change Summary

P01-T02 adds a Flyway identity schema and a same-origin Spring Security boundary for self-service registration, login, logout, current-profile retrieval, and password change. It persists account/credential hashes in H2, deliberately keeps sessions in normal in-memory servlet storage, and defines a cookie/header CSRF contract for the packaged React client.

The implementation plan selects these public contracts: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/profile`, and `PUT /api/profile/password`. Registration authenticates the new account; the profile response contains only immutable canonical email; no profile/user-ID route is created in this phase.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Flyway/H2 persistence | A clean database misses identity constraints, accidentally seeds users, or account rows disappear on restart. | Migration assertions plus a sequential two-context file-H2 restart test. |
| Password processing | An inclusive 12--128 rule is implemented inconsistently, composition is accidentally required, or an encoder truncates/persists plaintext. | Boundary, no-composition, hash-only, old/new credential, and response-sanitization integration tests. |
| Authentication privacy | Login or registration errors reveal account existence or secret data. | Compare validly formed failed-login responses for existing/nonexistent accounts; inspect duplicate and error bodies. |
| Session/cookie lifecycle | Login does not establish a usable session, logout fails to invalidate it, restart accepts stale state, or cookie flags are unsafe. | Separate cookie-jar flow, logout/relogin flow, restart test, and `Set-Cookie` assertions. |
| CSRF | The SPA cannot initialize a token, or a missing/invalid token mutates state. | Obtain token from the public shell, use cookie/header success path, then omit/mismatch it and assert no mutation. |
| Authorization/tenant isolation | A principal can receive another account's profile or affect another account's credential/session. | Two independently authenticated clients invoke every user-owned operation in this phase and assert only principal-owned results/effects. |
| HTTP compatibility | Framework redirects/HTML/default exceptions replace the planned JSON contract. | Exact status, media type, envelope-code, and sensitive-field absence assertions for validation, malformed, 401, 403, and 404 paths. |
| Packaged application | Backend changes break the Maven-managed frontend/static resource packaging. | Full `mvn test`, `mvn package`, and an optional packaged-JAR lifecycle observation. |

## Existing Coverage and Environment Constraints

`src/test/java/app/detour/DetourApplicationTest.java` is the only current test. It uses `@SpringBootTest`, one generated in-memory H2 URL, Flyway, and a JDBC connection; it currently asserts only migration `1` and absence of legacy tables. No existing test helper supports HTTP cookies, CSRF, independent authenticated users, error envelopes, password fixtures, or application restarts.

Maven is the repository-standard runner. Its normal lifecycle runs `npm ci` and `npm run build` in `frontend` before copying `frontend/dist` to static resources. Focused backend tests can use the existing `skipFrontend` property to avoid repeating the Vite work, but full verification must run normal Maven lifecycle. Tests must use random/isolated H2 URLs and `@TempDir` file databases only; they must not use the production `data/detour` file, external services, live HTTPS endpoints, a real identity provider, or destructive reset scripts.

The default secure-cookie configuration means a real HTTP test client may refuse to return a Secure cookie over a random local HTTP port. Cookie-flag tests must retain the secure default. The restart-flow test may set the planned documented local test override and manually preserve the session cookie where necessary; that does not relax the production assertion.

## Failing Test First

- Name: `registersCanonicalAccountAndExposesOnlyItsEmptyProfile`
- Type: Spring Boot HTTP integration test with real security filters, Flyway, and H2.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`
- Arrange/Act/Assert: obtain the public-shell CSRF cookie; submit a valid lower-complexity 12-character password and mixed-case/whitespace email to registration; retain the returned session; fetch `/api/profile`; assert `201`, then `200`, canonical email only, and no Trip/role/id/credential fields.
- Expected pre-fix failure: the baseline has no controller/security/schema, so registration is unavailable and cannot create an authenticated principal or return the planned profile contract.

## Tests to Add or Update

### 1. `startsWithFreshDetourIdentityMigrationAndNoSeededPrivilege`

- Type: Spring Boot migration/context integration test.
- Location: `src/test/java/app/detour/DetourApplicationTest.java`
- Proves: Flyway applies only DeTour versions `1` and `2`; `detour_user` exists; legacy tables remain absent; zero identity rows, roles, and speculative Trip tables exist in a clean database.
- Inputs/fixture: generated per-class in-memory H2 URL.
- Doubles or boundary isolation: no doubles; direct Flyway/JDBC inspection.
- Edge cases: verify the unique canonical-email database constraint is present or demonstrate it in the HTTP duplicate test; do not assert database implementation details unrelated to identity.

### 2. `registersCanonicalAccountAndExposesOnlyItsEmptyProfile`

- Type: Spring Boot HTTP integration test.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`
- Proves: valid registration starts a session, sends only allowed profile information, and a new user has an empty/no-privilege identity surface.
- Inputs/fixture: `"  Ada@Example.test  "` and a 12-code-point password with no mixed character classes.
- Doubles or boundary isolation: real H2, controller, security chain, CSRF cookie/header, and an isolated cookie jar.
- Edge cases: assert a response/error never contains `password`, `currentPassword`, `newPassword`, `passwordHash`, Argon2 text, an internal owner ID, role, or a Trip; query the stored hash only in test code to confirm it differs from plaintext and uses the selected adaptive encoding prefix.

### 3. `enforcesInclusivePasswordLengthWithoutCompositionRules`

- Type: HTTP integration test, optionally supplemented by a narrow `PasswordPolicyTest` if the custom code-point validator has branches not naturally reached through MVC.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java` (and, only if useful, `PasswordPolicyTest.java`).
- Proves: exactly 12 and 128 code-point passwords register; 11 and 129 are rejected with `400 VALIDATION_FAILED`; lowercase/repeated-character passwords are accepted; the same rule applies to a replacement password.
- Inputs/fixture: ASCII boundary strings plus a multi-code-point string if needed to prove character counting rather than encoder byte length.
- Doubles or boundary isolation: independent fresh email for each registration; no mocked encoder.
- Edge cases: ensure invalid new password leaves the old hash/login valid and validation details do not echo either supplied password.

### 4. `normalizesEmailUniquenessAndAuthenticatesTheCanonicalAccount`

- Type: HTTP integration test.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`
- Proves: trimmed/case-equivalent accepted emails cannot create separate accounts, successful login uses the same canonicalization, and profile email cannot be changed because no mutation contract accepts it.
- Inputs/fixture: register mixed-case/outer-whitespace email; register its lowercased/trimmed equivalent; login using another equivalent spelling.
- Doubles or boundary isolation: real unique constraint and repository; concurrently or sequentially exercise the duplicate-key translation as repository conventions permit.
- Edge cases: duplicate registration is only `409 EMAIL_UNAVAILABLE` with no existing profile detail; malformed email is a `400`, not a login-existence signal.

### 5. `usesOneGenericFailureForExistingAndUnknownCredentials`

- Type: HTTP integration test.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`
- Proves: a wrong password for a registered canonical email and a valid-format unknown email both return the same `401 AUTHENTICATION_FAILED` status/body shape/message; neither response includes account/profile/credential information.
- Inputs/fixture: one registered account, one unknown valid email, wrong passwords of valid length.
- Doubles or boundary isolation: use a CSRF token for each login attempt so the result is authentication failure rather than CSRF failure.
- Edge cases: keep the useful duplicate-registration conflict separate; it is the limited, ticket-permitted uniqueness disclosure.

### 6. `loginLogoutAndReloginMaintainOnlyTheActiveSession`

- Type: HTTP integration test.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`
- Proves: login produces a server session; `/api/profile` succeeds with it; CSRF-protected logout invalidates it; the old cookie then receives `401 UNAUTHENTICATED`; a subsequent login creates a usable new session.
- Inputs/fixture: registered account and two captured cookie jars/token values.
- Doubles or boundary isolation: do not use `@WithMockUser`; exercise the real authentication manager and servlet session.
- Edge cases: assert `JSESSIONID` has `Secure`, `HttpOnly`, `SameSite=Lax`, and `Path=/`; assert the CSRF cookie is not the session ID and is intentionally not `HttpOnly` because the SPA must echo it in a header.

### 7. `requiresCsrfTokenAndLeavesStateUnchangedWhenRejected`

- Type: HTTP integration test.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`
- Proves: registration, login, logout, and password change accept the repository cookie/header pair and return `403 CSRF_INVALID` when missing or mismatched; rejected requests create no account, retain an active session, and do not change a password.
- Inputs/fixture: public static-shell request to bootstrap `XSRF-TOKEN`; registered authenticated user for password/logout checks.
- Doubles or boundary isolation: make raw cookie/header requests rather than relying only on a test-framework CSRF postprocessor, so the P01-T03 contract is exercised.
- Edge cases: the safe GET profile has no CSRF requirement; no public `/api/csrf` endpoint is expected.

### 8. `changesPasswordOnlyAfterCurrentCredentialVerification`

- Type: HTTP integration test.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`
- Proves: wrong current password produces `400 CURRENT_PASSWORD_INVALID` and leaves the existing password usable; valid current/new password changes the stored hash; old-password login then fails generically and new-password login succeeds.
- Inputs/fixture: one authenticated account, valid old/new values, then a fresh unauthenticated cookie jar for login checks.
- Doubles or boundary isolation: real adaptive encoder/repository; do not stub `matches`.
- Edge cases: new 11/129-character passwords reject without mutation; successful change does not expose the new hash or plaintext.

### 9. `protectsAllNonPublicPathsAndProvidesStableSafeErrors`

- Type: HTTP integration test.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`
- Proves: public shell/static resource and registration/login are reachable without authentication; profile/password/logout and unknown `/api/**` paths return JSON `401` without a session; an authenticated unknown application path returns `404 RESOURCE_NOT_FOUND`; malformed JSON and invalid request fields return their planned `400` envelopes; CSRF returns the planned `403` envelope.
- Inputs/fixture: anonymous and authenticated cookie jars; malformed body; invalid email/blank required field; known public root/static asset and missing API path.
- Doubles or boundary isolation: real exception handlers and security entry point/denied handler.
- Edge cases: assert `application/json` (or the selected documented JSON media type), no HTML redirect/login page, no stack trace/class name/credential/account detail, and no old Wayfarer route is restored.

### 10. `keepsTwoIndependentUsersStrictlyPrincipalScoped`

- Type: HTTP integration test.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`
- Proves: two separately registered/authenticated cookie jars receive their own canonical profile email, cannot cause a password change in the other user's session, and logout from one does not invalidate the other. All currently user-owned operations are principal-derived and accept no foreign owner identifier.
- Inputs/fixture: user A and user B with different canonical emails/passwords and separate CSRF cookies/sessions.
- Doubles or boundary isolation: `IdentityTestSupport` must acquire unique CSRF/session values and never share a MockMvc session or cookie store between users.
- Edge cases: send irrelevant/guessed `userId`/`email` query values only if the controller binding would otherwise accept them; assert they cannot select an owner. There is deliberately no foreign-ID profile endpoint to test in this phase. Authenticated missing API resource coverage in test 9 is the non-disclosing `404` contract; later resource tickets must add concrete foreign-ID `404` tests.

### 11. `persistsAccountButRejectsPreRestartSession`

- Type: application-restart integration test.
- Location: `src/test/java/app/detour/identity/ApplicationRestartIntegrationTest.java`
- Proves: account rows survive a real application-context/server restart against one temporary file-backed H2 URL, while a `JSESSIONID` captured before close is not accepted by the new context; a new login against the persisted account succeeds.
- Inputs/fixture: JUnit `@TempDir` H2 file path, runtime random ports, one registered account, captured session cookie, and a second application context with the same datasource properties.
- Doubles or boundary isolation: start/close real `SpringApplication` contexts sequentially; never target `data/detour` or use a persistent external session store.
- Edge cases: set only the documented test/local secure-cookie override if necessary to transmit a test-HTTP cookie; run cookie-flag assertions separately under the secure default.

## Safe Verification Commands

- Focused: `mvn -DskipFrontend=true -Dtest=IdentityApiIntegrationTest,ApplicationRestartIntegrationTest,DetourApplicationTest test`
- Related suite: `mvn -DskipFrontend=true test`
- Full safe suite: `mvn test`
- Packaging gate: `mvn package`

## Optional Developer Checks

- Start the packaged JAR with a disposable `DETOUR_DATABASE_URL`, register a user, restart without deleting that database, and confirm stale session rejection followed by successful login.
- In the intended HTTPS deployment, inspect browser network headers to confirm the session cookie is `Secure`, `HttpOnly`, and `SameSite=Lax`, while only the anti-CSRF cookie is JavaScript-readable.

## Exit Criteria

- [ ] The planned red test fails for the intended reason before implementation, when applicable.
- [ ] New and updated tests pass after implementation.
- [ ] The broadest safe relevant repository test suite passes.
- [ ] Acceptance criteria map to executable evidence.
- [ ] Routine automated tests do not perform unintended live or destructive operations.
- [ ] Material risks and edge cases identified above are covered.
- [ ] Any optional check is reported as nonblocking and is not represented as already performed.
