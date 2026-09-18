# P01-T03 — Deliver the DeTour Authentication and Profile Experience Code Review — Cycle 6

## Scope and Repository State

Reviewed the ticket-scoped unstaged and untracked implementation against `main`/`origin/main` at `2e8a69b`: React identity screens and API client, frontend test tooling, `/profile` SPA document fallback/security matcher and MVC coverage, packaged-JAR smoke script, README guidance, and supporting styles. Untracked research and plan artifacts were read as durable context; prior review documents were intentionally not read. No staged changes were present.

The review traced registration, login/session restoration, logout, password rotation, API error parsing, CSRF token use, direct `/profile` navigation, Spring authorization, packaged static assets, and smoke-script cleanup beyond the changed hunks. Security/privacy, asynchronous state, lifecycle, accessibility, responsive behavior, package/deployment, and scoped legacy-copy risks were considered.

## Findings

No actionable findings.

## Findings Resolved in This Context

None. This review made no implementation-artifact changes.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result: implemented/partial/missing/safe deviation |
| --- | --- | --- | --- |
| Register, empty profile, refresh, logout, and relogin | `frontend/src/App.tsx`, `AuthScreen.tsx`, and `ProfileScreen.tsx` drive state from `GET /api/profile` and clear it on unauthenticated/logout transitions. | `App.test.tsx`; `IdentityApiIntegrationTest`; `ApplicationRestartIntegrationTest`. | implemented |
| Restart retains account but rejects the old session | No client/session persistence was added; server session remains servlet-memory state. | `ApplicationRestartIntegrationTest.persistsAccountButRejectsPreRestartSession`. | implemented |
| Inclusive 12–128 code-point password range, no composition rules, operable show/hide | `PasswordField.tsx` uses `Array.from`; labels, rules, and toggle are present for registration and rotation. | `PasswordField.test.tsx`; Spring policy integration coverage. | implemented |
| Current-password-only password change and credential secrecy | `ProfileScreen.tsx` submits only current/new password; `identityApi.ts` has no client persistence and forms clear secrets on completion/error/view change. | App password-change and storage-spy coverage; focused Spring identity tests. | implemented |
| Immutable email and deliberate empty profile | `ProfileScreen.tsx` exposes a display-only email and an explicit empty state without Trip, role, or administrator UI. | Authenticated App interaction assertions and source review. | implemented |
| Direct route/session/CSRF/error coherence without protected-data exposure | `App.tsx` renders a non-sensitive loading state then derives screen state from the protected API; `SpaRouteController` forwards exactly `/profile`; `SecurityConfiguration` leaves `/api/**` protected. | `App.test.tsx`; `IdentityApiIntegrationTest.servesOnlyTheProfileDocumentFallbackWhileKeepingProfileDataProtected`; packaged-JAR smoke. | implemented |
| No JavaScript-managed session or credential persistence | `identityApi.ts` reads only the CSRF cookie; no storage or session-cookie access appears in delivered source. | Storage spies and scoped source search. | implemented |
| Accessible required disclosure and clean routine product copy | `AboutDemoTab.tsx` is a collapsed button/disclosure on both screens and contains every required fact. | App disclosure test; scoped cleanup search has no matches. | implemented |
| Keyboard, focus, labels, status, and responsive-equivalent identity functionality | Semantic controls, headings, error-summary focus, live status, `:focus-visible`, and responsive styles are implemented. | RTL interaction/focus tests; production build. | implemented |
| Frontend, backend, package, and local JAR flow without external/model dependency | Vitest tooling, Maven frontend build integration, and bounded local smoke script are present. | Frontend test/build, focused Spring suite, `mvn package`, and smoke script all pass. | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.
- Ticket-specific protections are observed: the document fallback is limited to `GET /profile`, `GET /api/profile` remains authenticated JSON, the client never treats its path as authentication, and only `XSRF-TOKEN` is read by JavaScript.

## Open Questions and Assumptions

None.

## Verification Results

- PASS — `npm.cmd --prefix frontend run test` — 3 files and 13 Vitest tests passed.
- PASS — `.\\mvnw.cmd '-Dmaven.repo.local=C:\\code\\loomspan-travel-demo\\target\\m2-review' -DskipFrontend=true '-Dtest=IdentityApiIntegrationTest,ApplicationRestartIntegrationTest,DetourApplicationTest' test` — 8 focused Spring tests passed.
- PASS — `npm.cmd --prefix frontend run build` — TypeScript/Vite production build completed.
- PASS — `.\\mvnw.cmd '-Dmaven.repo.local=C:\\code\\loomspan-travel-demo\\target\\m2-review' package` — full Maven test/package lifecycle, including `npm ci` and production asset packaging, passed.
- PASS — `powershell.exe -ExecutionPolicy Bypass -File .\\scripts\\verify-packaged-identity.ps1` — packaged JAR served `/` and `/profile` on an isolated loopback process/database, then cleaned up.
- PASS — `git diff --check` — no whitespace errors.
- PASS — `rg -n -i -S "wayfarer|loomspan|model|conversation|trace|exchange|disruption|recovery" frontend/src src/main/java src/main/resources scripts README.md` — no scoped product-surface matches.

## Residual Risks and Optional Developer Checks

- Run the optional browser DevTools inspection to confirm a real browser exposes only the readable CSRF cookie and stores no identity data in URLs, local storage, or session storage.
- At approximately 320px and a desktop width, complete the keyboard-only public/authenticated flows and observe focus/status behavior with a screen reader. These are nonblocking manual accessibility/browser observations; the available automated coverage passed.

## Disposition

- `clean`
