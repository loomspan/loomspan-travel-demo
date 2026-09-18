# P01-T03 — Deliver the DeTour Authentication and Profile Experience Code Review — Cycle 5

## Scope and Repository State

Reviewed the complete current P01-T03 working-tree change against `main` at `2e8a69b`, including modified, staged (none), and untracked files. Scope included the React identity/API boundary, accessible forms and disclosure, `/profile` SPA fallback/security matcher, HTTP integration test, test tooling, packaged-JAR smoke script, README, and ticket research/planning/testing artifacts. No unrelated changed source was identified.

The selected `full` profile remains appropriate: the change crosses an authentication/CSRF boundary, protected document routing, session lifecycle behavior, and packaged application deployment. No profile reassessment mismatch exists.

## Findings

No actionable findings remain.

## Findings Resolved in This Context

### [P2] Keep the authenticated view when logout is not confirmed
- Location: `frontend/src/App.tsx`
- Scenario: With a live server session but a missing or rejected CSRF token, clicking **Log out** made no successful logout request yet immediately rendered the public form. Reloading would restore the still-valid server session.
- Impact: The page appeared signed out even though the server session remained active, which is misleading on a shared browser and prevents the user from retrying the failed logout from the authenticated screen.
- Evidence: The previous logout catch path unconditionally set the public screen for all failures before the server could invalidate the session.
- Fix: Only move to public state after a confirmed logout or an explicit `UNAUTHENTICATED` response; retain the profile for network/CSRF/unknown failures, prevent duplicate concurrent logout requests, and cover the rejected-CSRF case with a frontend regression test.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Register, refresh, logout, and login | `App.tsx`, `AuthScreen.tsx`, `identityApi.ts` | `App.test.tsx`; identity HTTP integration tests | implemented |
| Restart invalidates former session but retains account | Existing server session design remains unchanged | `ApplicationRestartIntegrationTest` | implemented |
| 12–128 Unicode-code-point password range and visibility controls | `PasswordField.tsx`, public/profile forms | `PasswordField.test.tsx`, frontend suite | implemented |
| Correct-current-password update with ephemeral secret fields | `ProfileScreen.tsx`, `App.tsx` | `App.test.tsx`, identity HTTP integration tests | implemented |
| Immutable email and deliberate empty profile | `ProfileScreen.tsx` | `App.test.tsx` | implemented |
| Protected route, invalid session, CSRF, validation, network, and server failure handling | `SpaRouteController.java`, `SecurityConfiguration.java`, `App.tsx` | frontend suite; `IdentityApiIntegrationTest` | implemented |
| No JavaScript session/credential persistence | `identityApi.ts` reads only `XSRF-TOKEN`; no storage writes | storage-boundary frontend test and scoped search | implemented |
| Keyboard-operable required disclosure with no routine demo copy | `AboutDemoTab.tsx`, `style.css` | frontend disclosure interaction test; scoped search | implemented |
| Labels, focus, errors/status, and responsive controls | form components, `StatusRegion.tsx`, `style.css` | frontend interaction tests; manual screen-reader/viewport observation remains optional | implemented |
| Frontend, backend, package, and JAR-flow verification | Vite/Maven configuration and smoke script | commands below | implemented |
| Legacy/model surface cleanup | delivered product files | scoped search returned no matches | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `npm.cmd --prefix frontend run test` — 3 files and 13 tests passed.
- PASS — `npm.cmd --prefix frontend run build` — TypeScript/Vite production build completed.
- PASS — `.\mvnw.cmd '-Dmaven.repo.local=C:\code\loomspan-travel-demo\target\review-m2' -DskipFrontend=true '-Dtest=IdentityApiIntegrationTest,ApplicationRestartIntegrationTest,DetourApplicationTest' test` — focused Spring HTTP/session/restart tests passed (8 tests).
- PASS — `.\mvnw.cmd '-Dmaven.repo.local=C:\code\loomspan-travel-demo\target\review-m2' package` — full Maven lifecycle, frontend build, Java tests, and executable JAR packaging passed.
- PASS — `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — packaged JAR started with an isolated local database and served `/` and `/profile`.
- PASS — `rg -n -i -S "wayfarer|loomspan|model|conversation|trace|exchange|disruption|recovery" frontend/src src/main/java src/main/resources scripts README.md` — no product-surface matches.

## Residual Risks and Optional Developer Checks

- In a browser at representative mobile and desktop widths, complete the keyboard-only flows and inspect storage/cookies with a screen reader if available. Confirm only the readable CSRF cookie is exposed and `JSESSIONID` remains HTTP-only.

## Disposition

- fixes-applied
