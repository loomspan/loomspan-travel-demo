# P01-T02 — Establish Secure DeTour Accounts and Tenant Isolation Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket-scoped unstaged and untracked implementation on `main` against merge base `09786c4` (`P01-T01` clean platform). Scope included identity/security production code, Flyway `V2`, application configuration, README, migration and HTTP/restart tests, and the supplied research and plans. The implementation-plan, testing-plan, and research artifacts are untracked pipeline artifacts; no prior review artifact was read. `data/detour.mv.db`, if present from a prior optional restart check, was left untouched and excluded from scope.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. No implementation artifact was changed in this review context.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Clean migration; accounts persist but sessions do not survive restart | `V2__create_detour_user_identity.sql`; normal `HttpSessionSecurityContextRepository` with no external session store | `DetourApplicationTest` migration assertion and `ApplicationRestartIntegrationTest` sequential file-H2 contexts | implemented |
| 12–128 code-point passwords, no composition rule, no plaintext exposure | `PasswordPolicy`; Argon2 encoder; request records redact `toString()`; profile/error DTOs omit credentials | HTTP boundary registration coverage for 11/12/128/129 and safe profile/error responses | implemented |
| Canonical immutable email | `EmailCanonicalizer`, database unique constraint, principal-only profile contract with no email mutation route | mixed-case/space duplicate and canonical login checks | implemented |
| Registration/login/profile/logout/relogin server-session flow | `IdentityController` explicitly saves the verified security context and invalidates logout session | HTTP lifecycle test plus restart test | implemented |
| Current-password verification and credential rotation | transactional `IdentityService.changePassword` verifies current hash before update | own-password failure/success and old/new login assertions | implemented |
| Secure, HTTP-only, same-site session cookies and CSRF | default secure cookie configuration; `CookieCsrfTokenRepository`; CSRF filter/JSON denial handling | restart HTTP test asserts session-cookie flags; HTTP tests reject a missing CSRF token | implemented |
| Public identity/static surface; all remaining paths authenticated | narrow public matchers and `.anyRequest().authenticated()` with JSON handlers | public shell, protected profile, and CSRF/auth error HTTP tests | implemented |
| Principal-scoped ownership and non-disclosure | profile/password derive owner only from `DetourUserPrincipal`; no public owner identifier endpoint exists | two separately registered sessions receive only their own profile, password effect, and logout lifecycle | implemented |
| No unnecessary account enumeration or credential leakage | generic authentication failure; bounded duplicate conflict; sanitized API error envelope | existing/unknown login response equality and response assertions | implemented |
| New account is empty/unprivileged, with no seeded credentials/admin | minimal `detour_user` migration contains no roles, domain tables, or inserts; default security user disabled | clean migration count/legacy-table tests and profile-only response checks | implemented |
| Stable safe malformed/missing errors | controller advice plus JSON authentication/CSRF handlers | malformed JSON, validation, unauthenticated, and CSRF envelopes | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.
- The implementation retains the required packaged Spring Boot/React, H2/Flyway, DeTour-only baseline, does not reset data at startup, adds no Wayfarer compatibility route, and leaves sessions in servlet memory.

## Open Questions and Assumptions

None.

## Verification Results

- PASS — `./mvnw.cmd '-DskipFrontend=true' '-Dtest=IdentityApiIntegrationTest,ApplicationRestartIntegrationTest,DetourApplicationTest' test` — focused migration, HTTP identity, CSRF/cookie, isolation, and restart checks passed (7 tests).
- PASS — `./mvnw.cmd test` — repository-standard Maven test lifecycle, including frontend production build, passed (7 tests).
- PASS — `./mvnw.cmd package` — frontend build, complete tests, and executable JAR packaging passed.
- PASS — `git diff --check` — no whitespace errors in tracked changes; untracked source was inspected directly.

## Residual Risks and Optional Developer Checks

- In the intended HTTPS deployment, inspect browser headers and SPA behavior to confirm `JSESSIONID` remains `Secure; HttpOnly; SameSite=Lax` and only `XSRF-TOKEN` is JavaScript-readable. Automated HTTP coverage verifies the server contract but does not exercise a real TLS proxy/browser.

## Disposition

- `clean`
