# P01-T03 — Deliver the DeTour Authentication and Profile Experience Code Review — Cycle 1

## Scope and Repository State

Reviewed all ticket-scoped staged, unstaged, and untracked work against the ticket, research, implementation plan, testing plan, and active repository instructions. The change replaces the static React shell with same-origin identity/profile flows and adds the narrow `/profile` document fallback, frontend interaction tooling, packaging smoke script, and supporting documentation.

The review traced the React request/state lifecycle, CSRF/header boundary, Spring authorization fallback, response/error paths, credential persistence boundary, accessibility controls, responsive CSS, frontend tests, backend integration tests, and packaged-JAR script. No active project-specific design guardrails are recorded.

## Findings

No actionable findings remain after the internal fix and re-review.

## Findings Resolved in This Context

### [P2] Do not announce registration success before profile restoration succeeds
- Location: `frontend/src/App.tsx:31`
- Scenario: Registration returned `201`, but the immediate `GET /api/profile` returned `401` or otherwise failed.
- Impact: The UI could render public authentication while announcing that the account was ready, which is a misleading result for the user-visible flow.
- Evidence: `loadProfile` previously consumed failures without reporting success/failure to `register`; `register` always set the success notice after awaiting it.
- Fix: `loadProfile` now returns a completion result, reports post-authentication unauthenticated failures, and registration emits its success notice only after the profile loads. `App.test.tsx:48` covers the regression.

### [P2] Prevent an earlier password change from overwriting logout state
- Location: `frontend/src/App.tsx:57`
- Scenario: A password-change request remained in flight while the user selected Log out; it then completed after logout.
- Impact: The stale completion could replace the logout notice with a password-update success notice on the public screen.
- Evidence: The prior request guard covered profile loads but not password-change completion.
- Fix: An authentication epoch invalidates stale password-change completions as logout begins. `App.test.tsx:90` proves the later logout state remains authoritative.

### [P3] Restrict the public profile fallback to document GET requests
- Location: `src/main/java/app/detour/security/SecurityConfiguration.java:47`
- Scenario: Any method to `/profile` matched the public security rule even though only `GET /profile` is the SPA document route.
- Impact: The fallback boundary was broader than the supported route contract, allowing unauthenticated non-GET requests to reach routing rather than the normal authentication boundary.
- Evidence: The matcher lacked an HTTP method while `SpaRouteController` only defines `@GetMapping`.
- Fix: The permit rule now matches only `HttpMethod.GET`; `IdentityApiIntegrationTest.java:143` asserts anonymous `POST /profile` remains unauthorized.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Register, refresh live session, logout, and relogin reach an empty profile | `frontend/src/App.tsx`, `ProfileScreen.tsx`, `identityApi.ts` | `App.test.tsx`; Spring identity integration tests | implemented |
| Restart retains account but rejects old session | No client session persistence; existing servlet-session architecture | `ApplicationRestartIntegrationTest` through focused and package runs | implemented |
| 12–128 code-point range, no composition rule, show/hide | `PasswordField.tsx`, `AuthScreen.tsx`, `ProfileScreen.tsx` | `PasswordField.test.tsx`; identity integration boundary test | implemented |
| Current password is required; credentials remain ephemeral | `ProfileScreen.tsx`, `identityApi.ts`, `App.tsx` | Password-change UI and backend integration tests; storage-write spy | implemented |
| Immutable email and deliberate empty profile | `ProfileScreen.tsx` | `App.test.tsx` and identity integration assertions | implemented |
| Safe direct route, session, CSRF, validation, network, and server-failure states | `SpaRouteController.java`, `SecurityConfiguration.java`, `App.tsx` | React failure tests and Spring MVC fallback/API boundary test | implemented |
| No JavaScript-managed session or credential persistence | `identityApi.ts` reads only the CSRF cookie; no storage calls in production source | Storage-write spy and scoped source search | implemented |
| One keyboard-accessible required disclosure with no routine demo copy | `AboutDemoTab.tsx` | Disclosure interaction test and legacy/model-copy search | implemented |
| Labels, focus, error discovery, live status, and responsive parity | Form components, `StatusRegion.tsx`, `style.css` | Role/label/focus/keyboard interaction tests; responsive CSS inspected | implemented; optional real-browser observation remains |
| Frontend, backend, package, and local JAR flow without external model service | `package.json`, `pom.xml`, `verify-packaged-identity.ps1` | Frontend tests/build, Maven package, packaged smoke run | implemented |
| Scoped legacy/model UI cleanup | Delivered product surfaces | No-match scoped search | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None affecting correctness or review confidence.

## Verification Results

- PASS — `npm.cmd --prefix frontend run test` — 3 files and 9 tests passed.
- PASS — `npm.cmd --prefix frontend run build` — TypeScript compilation and Vite production build passed.
- PASS — `.\mvnw.cmd '-DskipFrontend=true' '-Dtest=IdentityApiIntegrationTest,ApplicationRestartIntegrationTest,DetourApplicationTest' test` — 8 focused Spring tests passed.
- PASS — `.\mvnw.cmd package` — Maven-managed frontend install/build, complete Java tests, and executable JAR packaging passed.
- PASS — `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — isolated loopback packaged-JAR identity-shell smoke test passed.
- PASS — `rg -n -i -S "wayfarer|loomspan|model|conversation|trace|exchange|disruption|recovery" frontend/src src/main/java src/main/resources scripts README.md` — no delivered-product-surface matches.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- In a local browser at roughly 320px and desktop width, complete all identity and disclosure flows using only keyboard controls; observe focus movement and status/error announcements with a screen reader if available.
- Inspect browser storage/cookies on the loopback app: no identity values in URLs or web storage; `JSESSIONID` remains JavaScript-inaccessible and only `XSRF-TOKEN` is readable for CSRF echo.

## Disposition

- `fixes-applied`
