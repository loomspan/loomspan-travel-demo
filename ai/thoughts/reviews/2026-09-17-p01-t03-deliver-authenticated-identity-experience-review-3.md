# P01-T03 — Deliver the DeTour Authentication and Profile Experience Code Review — Cycle 3

## Scope and Repository State

Reviewed the ticket-scoped tracked and untracked React identity experience, Spring `/profile` fallback, focused tests, packaging smoke script, documentation, dependency lockfile, and the supplied research and planning artifacts against `main` at `2e8a69b`. The worktree has no staged files; all observed production, test, script, documentation, and artifact changes are attributable to P01-T03, except earlier pipeline artifacts that remain in their designated directories. No active project-specific guardrails are recorded in `ai/thoughts/design-lens.md`.

## Findings

No actionable findings remain after the review/fix loop.

## Findings Resolved in This Context

### [P2] Reject malformed successful profile responses
- Location: `frontend/src/api/identityApi.ts:52`
- Scenario: A `200` profile response with a missing, non-string, or blank `email` was accepted through the TypeScript-only `Profile` assertion and could render an incoherent authenticated screen.
- Impact: An unexpected server/proxy response did not reliably reach the safe failure treatment required for the protected bootstrap boundary.
- Evidence: `request<T>` deserializes arbitrary JSON; the prior `getProfile` returned that result as `Profile` without runtime validation.
- Fix: Validate the response shape and nonblank email before transitioning to authenticated state; added malformed-payload regression assertions in `frontend/src/api/identityApi.test.ts:27`.

### [P2] Preserve error-summary focus on error-driven screen changes
- Location: `frontend/src/App.tsx:46`
- Scenario: During an initial profile failure, the error effect focused the summary, then the loading-to-public heading effect immediately moved focus to the heading.
- Impact: Keyboard and assistive-technology users could miss the discoverable error treatment when the state transition itself caused the error.
- Evidence: React runs both effects after the same commit in source order; the heading effect previously had no error-state guard.
- Fix: Suppress route-heading focus while an error notice is active and cover the initial profile-failure focus path in `frontend/src/App.test.tsx:76`.

### [P2] Clear cached profile state after any indeterminate logout failure
- Location: `frontend/src/App.tsx:59`
- Scenario: If a logout request reached the server but its response was lost, the UI retained the cached email/profile view despite the server session potentially already being invalidated.
- Impact: A sensitive authenticated view could remain visible after a user attempted logout and encountered a network or unexpected response failure.
- Evidence: Only an explicit `UNAUTHENTICATED` error previously moved the app to the public state; all other logout failures left the profile screen rendered.
- Fix: Move to the public state for every logout failure while retaining a non-success error message.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Register, restore a live session, empty profile, logout, relogin | `App`, `AuthScreen`, `ProfileScreen`, `identityApi` | Vitest registration/logout/login and profile-bootstrap cases | implemented |
| Restart retains account but rejects prior session | Existing server-managed session design unchanged | Restart test executed its assertions, but the JUnit temp-directory cleanup fails in this sandbox | partial |
| Password range, no composition rule, show/hide | `PasswordField`, code-point length helper | Password component tests plus backend contract test | implemented |
| Current-password change and credential lifetime | `ProfileScreen`, `App.changePassword`, same-origin API module | Password success/race tests; storage-source scan | implemented |
| Immutable email and deliberate empty profile | `ProfileScreen` | Authenticated profile interaction test | implemented |
| Protected direct route and safe error/session states | `SpaRouteController`, `SecurityConfiguration`, `App` | Passing MVC fallback/API-boundary test and failure tests | implemented |
| No JavaScript persistence | API reads only the CSRF cookie | Production-source storage search returned no matches | implemented |
| Single accessible disclosure and cleanup | `AboutDemoTab`, CSS | Disclosure test; scoped product-surface search returned no matches | implemented |
| Keyboard/focus/status and responsive controls | Native controls, focus effects, live status, media query | Focus/status/keyboard interaction coverage; responsive/screen-reader observation remains manual | implemented |
| Production build, Java boundary, packaged JAR | Maven/Vite integration and smoke script | Frontend tests/build, focused MVC suite, package, packaged shell smoke | partial |

## Active Project Guardrails

- None recorded.

## Open Questions and Assumptions

- The restart-test cleanup failure is attributable to the sandbox denying `@TempDir` cleanup beneath the user temporary directory, after its HTTP assertions completed; it is not evidence of a ticket regression. A normal developer environment should rerun the full Maven test lifecycle.

## Verification Results

- PASS — `npm.cmd --prefix frontend run test` — 3 files and 11 tests passed.
- PASS — `npm.cmd --prefix frontend run build` — TypeScript check and Vite production build passed.
- PASS — `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' -DskipFrontend=true '-Dtest=IdentityApiIntegrationTest' test` — 6 MVC/security/identity tests passed.
- FAIL — `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' -DskipFrontend=true '-Dtest=IdentityApiIntegrationTest,ApplicationRestartIntegrationTest,DetourApplicationTest' test` — `ApplicationRestartIntegrationTest` completed its assertions but JUnit failed closing its user-temp `@TempDir` with sandbox `AccessDeniedException`.
- PASS — `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' -DskipTests package` — packaged JAR built after Maven-managed `npm ci` and production build.
- PASS — `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — packaged identity shell startup and `/profile` fallback smoke passed.
- PASS — `rg -n -i -S "wayfarer|loomspan|model|conversation|trace|exchange|disruption|recovery" frontend/src src/main/java src/main/resources scripts README.md` — no scoped product-surface matches.
- PASS — `rg -n -i -S "localStorage|sessionStorage" frontend/src --glob '!*.test.*'` — no production browser-storage API usage.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- Re-run the full Maven test lifecycle in an environment that permits JUnit's temporary-directory cleanup; this independently confirms the restart assertion under normal filesystem permissions.
- At representative mobile and desktop widths, complete the identity/disclosure paths with a keyboard and assistive technology; inspect browser storage/cookies to confirm that only the CSRF cookie is script-readable.

## Disposition

- `fixes-applied`
