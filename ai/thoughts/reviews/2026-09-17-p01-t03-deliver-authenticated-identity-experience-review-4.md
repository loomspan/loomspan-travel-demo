# P01-T03 — Deliver the DeTour Authentication and Profile Experience Code Review — Cycle 4

## Scope and Repository State

Reviewed the complete ticket-scoped working-tree change against `origin/main` / merge base `2e8a69b`, including tracked modifications, all untracked frontend/server/script files, test tooling/lockfile, README, and the durable research and implementation/testing plans. There were no staged changes and no unrelated production changes identified. Earlier review artifacts were deliberately not consulted.

The review traced the public bootstrap, CSRF request boundary, registration/login/profile/logout/password-change flows, `/profile` document forwarding and API authorization, credential lifetime, disclosure/accessibility behavior, Maven packaging, and the local JAR smoke lifecycle. Full-pipeline assurance remains appropriate because the change crosses the protected client-route, session/CSRF, packaging, and accessibility boundaries.

## Findings

No remaining actionable findings.

## Findings Resolved in This Context

### [P3] Clear the password when public account modes change
- Location: `frontend/src/components/AuthScreen.tsx:17`
- Scenario: A user entered a password in login and selected Register (or the reverse) before submitting.
- Impact: The secret remained in the newly selected public form, exceeding the intended active-form lifetime and making an accidental reuse more likely.
- Evidence: Both public modes shared one controlled `password` state, while mode-switch handlers previously changed only the mode and field errors.
- Fix: Added a shared mode-switch handler that clears the password and field errors while retaining the useful email input; public mode controls are also disabled while a request is pending. Added a focused App interaction regression test.

### [P2] Upgrade the newly added vulnerable test runner
- Location: `frontend/package.json:1`, `frontend/package-lock.json:24`
- Scenario: The ticket added direct development dependency `vitest@4.1.0`; `npm audit` reports its transitive mocker component is vulnerable to path traversal/arbitrary file read when redirect mocks are used.
- Impact: A vulnerable local test/tooling dependency was introduced into the repository. The advisory has a compatible patch release.
- Evidence: `npm.cmd --prefix frontend audit --json` reported GHSA-82fw-gwwq-j7x9 for `vitest` / `@vitest/mocker`, affecting versions below `4.1.11`.
- Fix: Pinned Vitest and its lockfile resolution to `4.1.11`, then reinstalled and verified the audit reports zero vulnerabilities.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Register, profile, refresh, logout, relogin | `App`, `AuthScreen`, `ProfileScreen`, `identityApi` | Vitest lifecycle interaction; Spring identity/restart tests | implemented |
| Restart retains account but rejects prior session | Existing server-managed session remains unchanged | `ApplicationRestartIntegrationTest` | implemented |
| Password range, no composition, disclosure control | `PasswordField`, `AboutDemoTab` | Password-field and App interaction tests | implemented |
| Current-password change and credential secrecy | `ProfileScreen`, `App`, `identityApi`; public mode-switch clearing | App interaction/storage-spy tests | implemented |
| Immutable email and deliberate empty profile | `ProfileScreen` | Authenticated App interaction test | implemented |
| Direct navigation, session/CSRF, safe failures | `SpaRouteController`, security matcher, API error mapping/App state | MVC boundary and frontend mocked-failure tests | implemented |
| No JavaScript credential/session persistence | No client storage API; only CSRF-cookie parsing | Storage spies and scoped source search | implemented |
| Disclosure, labels, focus, status, responsive controls | `AboutDemoTab`, semantic form controls, `StatusRegion`, stylesheet | App/RTL keyboard, focus, and disclosure coverage | implemented |
| Frontend, backend, package, JAR startup verification | Vite/Maven configuration and `verify-packaged-identity.ps1` | Commands below | implemented |
| Scoped legacy/model cleanup | Delivered product surfaces and README | Scoped `rg` returned no matches | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `npm.cmd --prefix frontend run test` — 3 files, 12 tests passed.
- PASS — `npm.cmd --prefix frontend run build` — TypeScript and Vite production build passed.
- PASS — `npm.cmd --prefix frontend ci` — clean lockfile install completed with zero reported vulnerabilities.
- PASS — `npm.cmd --prefix frontend audit --json` — zero known dependency vulnerabilities after the Vitest patch upgrade.
- PASS — `.\mvnw.cmd "-Duser.home=C:/Users/rmelcher" "-Dmaven.repo.local=C:/Users/rmelcher/.m2/repository" -DskipFrontend=true "-Dtest=IdentityApiIntegrationTest,ApplicationRestartIntegrationTest,DetourApplicationTest" test` — 8 focused Java tests passed.
- PASS — `.\mvnw.cmd "-Duser.home=C:/Users/rmelcher" "-Dmaven.repo.local=C:/Users/rmelcher/.m2/repository" test` — full Maven lifecycle passed before the compatible Vitest patch; the later package command repeated the complete lifecycle after the upgrade.
- PASS — `.\mvnw.cmd "-Duser.home=C:/Users/rmelcher" "-Dmaven.repo.local=C:/Users/rmelcher/.m2/repository" package` — full package lifecycle passed after the Vitest patch and produced the executable JAR.
- PASS — `powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-packaged-identity.ps1` — temporary loopback packaged-JAR smoke passed.
- PASS — `rg -n -i -S "wayfarer|loomspan|model|conversation|trace|exchange|disruption|recovery" frontend/src src/main/java src/main/resources scripts README.md` — no product-surface matches.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- At representative 320px and desktop widths, complete the flows keyboard-only and inspect focus/status behavior with a screen reader.
- In browser DevTools during a local loopback run, verify only `XSRF-TOKEN` is JavaScript-readable and no identity data appears in storage or URLs.

## Disposition

- `fixes-applied`
