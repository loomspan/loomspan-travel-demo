# Deliver the DeTour Authentication and Profile Experience Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-17-p01-t03-deliver-authenticated-identity-experience.md`
- Research: `ai/thoughts/research/2026-09-17-p01-t03-deliver-authenticated-identity-experience.md`
- Outcome: Ship an accessible, responsive same-origin React identity experience that consumes the existing P01-T02 session/CSRF API, restores only a live session, keeps credentials ephemeral, and makes the empty authenticated profile usable without implementing later travel features.

## Current State

`frontend/src/main.tsx` is a static foundation message and `frontend/src/style.css` contains only its shell styles; no component owns routes, form state, requests, errors, accessibility announcements, disclosure, or browser storage. `frontend/package.json` has only `dev` and `build` scripts, so there is no frontend interaction-test convention.

P01-T02 already owns the API contract: `IdentityController` supplies registration, login, logout, current-profile, and password-change endpoints; `SecurityConfiguration` supplies the cookie/header CSRF exchange and server-side authorization; `SpaCsrfCookieFilter` materializes `XSRF-TOKEN` on public-shell loading. The session cookie is server-managed and HTTP-only, while only the anti-CSRF cookie is deliberately readable. `IdentityApiIntegrationTest` and `ApplicationRestartIntegrationTest` already prove the backend contract, including account persistence with restart-invalidated sessions.

The remaining routing gap is material: Spring currently permits only `/`, `/index.html`, assets, favicon, and `/demo-disclosure/**`; it neither serves the SPA shell at `/profile` nor lets the client turn an unauthenticated profile route into the public authentication view. A client-side guard alone cannot handle a direct document request to `/profile`.

## Desired End State

The browser starts at public authentication or an authenticated profile view based on `GET /api/profile`. Registration authenticates the new account and enters an empty profile; a refresh reruns that request and restores only the server's still-live session. Login, password rotation, and logout use same-origin requests with browser-managed cookies and the established `XSRF-TOKEN`/`X-XSRF-TOKEN` CSRF pair. No session identifier, password, profile credential field, or secret is placed in web storage, a URL, or application state after its form is cleared.

`/profile` becomes a public SPA-document fallback only: the React guard immediately requests the protected profile API and renders public authentication on `UNAUTHENTICATED`, without rendering account data first. The API and all non-shell application routes remain server protected. This is the smallest design that satisfies direct navigation and the P01-T02 public-static-shell rule without adding a redirect-specific server contract or weakening protected data authorization.

The UI provides interim DeTour copy, an empty profile, password visibility controls, discoverable errors and status, focus movement, and one keyboard-operable collapsed **About this demo** side tab on every public/authenticated view. It includes exactly the required fictional-service facts but no catalog, Trip, itinerary, comparison, booking, cancellation, role, administrator, model, Wayfarer, recovery, exchange, disruption, or conversational workflow.

## Scope

### In scope

- A small React application split into an API/CSRF boundary, identity view state, accessible forms, profile empty state, and shared disclosure control.
- A `/profile` SPA-document fallback and its narrow security matcher, while preserving server protection for `/api/**` and all other application paths.
- Frontend interaction-test tooling and focused tests, plus the backend routing/contract regression test needed by that fallback.
- A safe packaged-JAR smoke command/script that starts only a locally built JAR with a temporary database and no model configuration.
- Responsive CSS, keyboard/focus/error/status behavior, scoped product-surface cleanup checks, and documentation only where the new verification command needs it.

### Out of scope

- Any change to account persistence, session implementation, cookie policy, endpoint request/response shape, password hashing, server validation rule, or P01-T02 error contract.
- Email edits, verification, recovery/reset, MFA, social login, roles, administrators, or account deletion.
- Catalog browsing, Trip creation/grouping, itineraries, component selection, comparison, booking/cancellation behavior, Events, and Phase 7 final visual identity/copy.
- CORS, an identity provider, external session store, model service, model credential, or a legacy Wayfarer compatibility route.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

The ticket, architecture, and roadmap remain binding: DeTour is the only product name outside the one disclosure title; sessions are intentionally non-persistent; only the CSRF token is JavaScript-readable; static shell delivery does not authorize protected API data; and no AI/model or legacy Wayfarer behavior may return.

## Impact and Risk Analysis

- **Session and privacy:** UI state must be derived from `GET /api/profile`, not inferred from browser storage. Every response that means unauthenticated (including expired/invalid session) must atomically discard displayed profile state and return to the public form before any protected view can render.
- **CSRF:** every unsafe request must read the current `XSRF-TOKEN` cookie only to echo it in `X-XSRF-TOKEN`; missing-token or `CSRF_INVALID` responses must report a recoverable safe error and never claim completion. The client must not handle or synthesize `JSESSIONID`.
- **Credential lifetime:** form passwords may live only in controlled input state while the form is mounted. Clear them after a successful request, successful logout/navigation, and unmount/view change; never put them in URL/query state, logs, status/error text, localStorage, or sessionStorage.
- **Direct route behavior:** permitting a document fallback at `/profile` must be limited to that known client route and must not introduce a broad `/**` fallback that hides missing resources or grants API access. The protected API call remains the authorization decision.
- **Errors and focus:** backend messages/codes are safe but client rendering must preserve useful non-secret values, identify field errors, expose a summary, announce outcomes, and move focus to the route heading after successful view changes or the summary after failures. Network/non-JSON/5xx handling needs a generic user-safe message.
- **Accessibility/responsiveness:** semantic form labels, real buttons, `aria-expanded` disclosure state, `aria-live` status, visible `:focus-visible`, and layout breakpoints must be implemented as behavior, not visual-only decoration. The same controls must remain available at narrow widths.
- **Packaging:** Maven executes `npm ci` and the production build before copying `frontend/dist` into the JAR. New test-only dependencies/scripts must not make `npm run build`, Maven tests, or `mvn package` require a browser, an external service, or a model credential.

## Implementation Approach

Use a small explicit React state machine rather than adding a routing or data-fetching framework to an otherwise empty application. `App` owns a bootstrap state (`loading`, public-auth, authenticated profile) and performs the sole session-restoration request. A typed `identityApi` module centralizes `fetch` (`credentials: 'same-origin'`), CSRF header lookup for unsafe methods, JSON error parsing, and outcome classification. Form components own only active input values and submit through callbacks; `App` owns authenticated email and clears it on any unauthenticated transition.

Use `window.location.pathname`/History API for the one protected route rather than introducing a router dependency. A new server `SpaRouteController` forwards exactly `GET /profile` to the public index document, and `SecurityConfiguration` permits exactly that document path along with the existing static assets. The client treats `/profile` as a request for the authenticated view, but it never uses the URL as proof of authentication. This is preferable to returning an API JSON `401` for a browser document navigation (which cannot produce a usable public page) and to a wildcard fallback (which would mask resource/API failures).

Add Vitest, jsdom, React Testing Library, user-event, and jest-dom as dev-only frontend tooling, with tests beside the app/API modules. They are sufficient for deterministic interaction, status, field/error, cookie-header, and storage-boundary tests without a live service. Keep actual cookie flags, persistence/restart, and HTTP security coverage in the existing Spring integration tests, which already exercise the real filter chain. Add a focused `SpaRouteController` HTTP assertion to that suite. A bounded PowerShell smoke script verifies the packaged JAR itself starts locally and serves the public shell; its temporary H2 database and selected free local port prevent use of `data/detour` or external dependencies.

Implementation adaptation: the otherwise independent registration and login forms remain compactly colocated in `AuthScreen.tsx`; their state and callbacks are isolated there, while shared status behavior is in `StatusRegion.tsx`. This preserves the planned behavior without adding wrappers to a newly created application.

## Phase 1: Establish the client and route boundary

### Changes

- [x] `frontend/src/api/identityApi.ts` — add typed request/response/error models and `getProfile`, `register`, `login`, `logout`, and `changePassword` functions. Send JSON only to the five existing endpoints, include `credentials: 'same-origin'`, read only the `XSRF-TOKEN` cookie for unsafe-request header echo, and normalize malformed/network/5xx responses to safe client outcomes without storing payloads or secrets.
- [x] `frontend/src/App.tsx` and `frontend/src/main.tsx` — replace the foundation message with bootstrap state that probes `GET /api/profile` on load/refresh, renders a non-sensitive loading state until resolved, uses the pathname only to request the profile view, and clears profile state on `UNAUTHENTICATED`, expired session, or logout. Keep `/` and `/profile` browser navigation coherent without trusting the route as authentication.
- [x] `src/main/java/app/detour/web/SpaRouteController.java` — add a narrowly scoped `GET /profile` controller that forwards to `/index.html` so a direct client-route document request receives the same packaged SPA shell.
- [x] `src/main/java/app/detour/security/SecurityConfiguration.java` — permit only the new SPA document path in the existing static-shell matcher; leave all API authorization, CSRF settings, JSON error handlers, and non-shell paths unchanged.
- [x] `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java` — add real-MVC assertions that anonymous and authenticated `GET /profile` receive the SPA document (not protected profile JSON), while anonymous `GET /api/profile` remains the JSON `401 UNAUTHENTICATED` boundary.

### Automated verification

- [x] `mvn -DskipFrontend=true -Dtest=IdentityApiIntegrationTest test` — proves the direct-document fallback is narrow and does not weaken API authorization or CSRF behavior.

### Optional developer checks

- [ ] With a local packaged app, paste `/profile` into a fresh browser profile: the shell loads, it resolves to public authentication, and no account email appears before a successful login.

**Success criteria:** browser document navigation to `/profile` is possible from a fresh or active session, while every protected identity API remains protected and returns the existing JSON contract when no valid session exists.

## Phase 2: Deliver accessible public and authenticated identity views

### Changes

- [x] `frontend/src/components/AuthScreen.tsx`, `RegistrationForm.tsx`, and `LoginForm.tsx` — implement mutually reachable public registration/login forms with programmatic email/password labels, native email/password semantics, inline and summary validation, generic safe login failure presentation, and submit-pending behavior. Enforce the same inclusive 12–128 Unicode-code-point range in the browser for immediate feedback but retain the server as authority; do not add composition rules.
- [x] `frontend/src/components/PasswordField.tsx` — supply reusable password controls with an explicit show/hide button, accessible name/state, keyboard and pointer support, and a type toggle that never modifies the current value.
- [x] `frontend/src/components/ProfileScreen.tsx` and `PasswordChangeForm.tsx` — render only the immutable authenticated email and a deliberate empty-profile state, plus current/new password fields and clear success/failure handling. Require server success before announcing a password update, clear secret fields at safe completion/view transitions, and retain only non-secret useful input on errors.
- [x] `frontend/src/components/AboutDemoTab.tsx` — render one collapsed, keyboard-operable side-tab/disclosure on both public and authenticated screens. Its expanded content must include fictional suppliers/schedules/prices/availability/bookings; no payment or real reservations; PDX; San Francisco, Munich, and Mexico City; March 2027 only; and the future create/expand/compare/book/cancel itinerary capabilities. Do not use “demo” elsewhere in routine identity/profile UI.
- [x] `frontend/src/App.tsx` and `frontend/src/components/StatusRegion.tsx` — centralize visible error summary and `aria-live` status handling; move focus to the main heading after public/authenticated navigation and to the summary for submit failure. Map validation fields, `AUTHENTICATION_FAILED`, `CURRENT_PASSWORD_INVALID`, `CSRF_INVALID`, unauthenticated, network, and unexpected-server outcomes to non-disclosing language without echoing request values or falsely reporting success.
- [x] `frontend/src/style.css` — replace the foundation-only styles with responsive public/profile form, disclosure, error, status, layout, and `:focus-visible` rules. Preserve all actions, labels, and disclosure functionality from 320px/mobile through representative desktop widths without deciding Phase 7 brand identity.

### Automated verification

- [x] `npm --prefix frontend run test` — executes deterministic accessible interaction tests for registration, login, profile, password change, logout, disclosure, focus, errors, and client boundary behavior.
- [x] `npm --prefix frontend run build` — type-checks and produces the production Vite assets after the new UI/test configuration.

### Optional developer checks

- [ ] At representative 320px and desktop widths, use keyboard-only Tab/Shift+Tab/Enter/Space to complete public and authenticated flows; verify focus, outline, error summary, live status, show/hide value preservation, and disclosure operation with a screen reader where available.

**Success criteria:** every ticket-required identity action is reachable and understandable from keyboard and pointer, rendering never contains a seeded Trip/role/admin placeholder, and only server cookies plus active in-memory form values participate in identity state.

## Phase 3: Integrate tests, package, and prove clean boundaries

### Changes

- [x] `frontend/package.json`, `frontend/package-lock.json`, `frontend/vite.config.ts`, and `frontend/src/test/setup.ts` — add only dev-time Vitest/jsdom/Testing Library support and a `test` script; preserve the existing production `build` script and ensure Maven's `npm ci` remains lockfile reproducible.
- [x] `frontend/src/App.test.tsx`, `frontend/src/api/identityApi.test.ts`, and any focused component test files under `frontend/src/components/` — add the test cases specified in the testing plan using mocked same-origin fetch and sanitized fixtures; spy on `localStorage`/`sessionStorage` so identity flows prove they do not write credentials or session identifiers.
- [x] `scripts/verify-packaged-identity.ps1` — add a bounded local smoke script that requires `target/detour-0.1.0-SNAPSHOT.jar`, creates an isolated temporary H2 location, selects a loopback port, starts the JAR with `DETOUR_SECURE_COOKIES=false` only for HTTP loopback, waits with a timeout, requests `/` and `/profile`, checks a successful static shell, and always stops the child process/removes only its own temporary directory. It must not reset `data/detour`, contact a network service, or require model configuration.
- [x] `README.md` — document the safe packaged identity smoke command and its local-only temporary cookie/database behavior if the new script is added; retain the existing production HTTPS-cookie warning.
- [x] `frontend/src/**`, `src/main/java/**`, `src/main/resources/**`, `scripts/**`, and product-facing `README.md` — remove/avoid any ticket-scoped Wayfarer/Loomspan/model/conversation/trace/exchange/disruption/recovery UI, storage key, or product copy; historical `ai/thoughts` records remain excluded from the product-surface search.

### Automated verification

- [x] `mvn -DskipFrontend=true -Dtest=IdentityApiIntegrationTest,ApplicationRestartIntegrationTest,DetourApplicationTest test` — rechecks the client-consumed HTTP/session/CSRF/restart contract without reinstalling frontend dependencies.
- [x] `npm --prefix frontend run test` — runs all focused interaction/API-boundary tests without a live backend.
- [x] `mvn test` — runs the repository-standard full Java test lifecycle and Maven-managed frontend production build.
- [x] `mvn package` — creates `target/detour-0.1.0-SNAPSHOT.jar` with the built static identity UI.
- [x] `.\scripts\verify-packaged-identity.ps1` — proves the built JAR starts and serves its identity shell locally without a model credential/service or durable development-data mutation.
- [x] `rg -n -i -S "wayfarer|loomspan|model|conversation|trace|exchange|disruption|recovery" frontend/src src/main/java src/main/resources scripts README.md` — must return no product-surface matches; run separately from historical planning records.

### Optional developer checks

- [ ] In browser DevTools on a local loopback run, confirm neither localStorage nor sessionStorage contains identity values, URLs omit credentials, `JSESSIONID` is absent from JavaScript-visible cookies, and the separate `XSRF-TOKEN` alone is readable for the header exchange.

**Success criteria:** the new UI is testable and buildable in the existing Maven/Vite pipeline; the packaged artifact starts in isolation; backend session/CSRF behavior remains covered; and the delivered product surface contains no scoped legacy/model terminology or client persistence.

## Test Strategy

Step 3 specifies focused Vitest/Testing Library cases at the React/API-client boundary, continuing existing Spring HTTP integration tests for actual authorization, CSRF, and restart behavior. A small server fallback assertion is necessary because the new `/profile` document route crosses the Spring static/security boundary. The packaged-JAR smoke script is a local lifecycle test, not a replacement for interaction tests.

Use mocked `fetch` for deterministic UI failures and successful transitions, cookie setup only for `XSRF-TOKEN`, and explicit local/session-storage spies. Do not simulate or persist a session in JavaScript. Browser DevTools and assistive-technology/responsive checks remain optional observations because this checkout has no installed browser automation harness; all automateable acceptance behavior remains covered by the planned focused tests and Spring tests.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Register, empty profile, refresh, logout, relogin | `identityApi`, `App` bootstrap state, `AuthScreen`, `ProfileScreen` | App interaction flow plus existing `IdentityApiIntegrationTest` session lifecycle |
| Restart retains account but not session | No persistent client/session addition; existing in-memory session architecture | `ApplicationRestartIntegrationTest.persistsAccountButRejectsPreRestartSession` |
| 12–128/no composition/show-hide | `PasswordField`, registration/password-change client length helper | boundary/no-composition and show-hide value-preservation interaction tests; existing HTTP boundary test |
| Current-password-only change and credential secrecy | `PasswordChangeForm`, `identityApi.changePassword`, form clearing | success/wrong-current/validation tests; existing password-rotation integration test; storage spies |
| Immutable email and empty profile | `ProfileScreen` renders email/read-only identity plus empty state only | authenticated-profile assertions excluding Trip/role/admin controls |
| Coherent direct-route/session/CSRF/error failures | `SpaRouteController`, `SecurityConfiguration`, `App` error classification | Spring `/profile` fallback/API-401 test and mocked network/401/403/validation/5xx UI tests |
| No JS credential/session persistence | API module accesses only CSRF cookie; no storage helper | storage spies/source-scope search; optional DevTools cookie/storage inspection |
| Required accessible disclosure and no routine demo copy | `AboutDemoTab` and product copy | disclosure keyboard/content test and scoped copy search |
| Keyboard, focus, labels, status, responsive-equivalent functionality | semantic components, focus manager, live/status regions, CSS breakpoints | RTL keyboard/focus/label/status tests; optional mobile/desktop and screen-reader observation |
| Frontend/build/backend/packaged flow without model | package scripts, Maven config preservation, smoke script | `npm run test`, frontend build, focused Maven tests, `mvn test`, `mvn package`, smoke script |
| Legacy/model cleanup | delivered frontend/server/script/product-copy scope | scoped `rg` command with no matches |

## Risks and Rollback/Recovery

The principal implementation risk is treating a public `/profile` document fallback as API authorization. The plan avoids that by preserving `GET /api/profile` as the sole authentication decision and by testing it separately. Other material risks are stale async results rendering after logout, a lost CSRF token being reported as success, and accidental browser persistence; `App` transitions, typed client errors, form clearing, and storage tests must address these before merge.

Rollback is a normal application/code rollback. There is no schema, API, persistent-session, or user-data migration in this ticket. The packaged smoke test uses only its own temporary database and must clean it up; it must never invoke the existing destructive reset command or modify the developer's `data/detour` database.

## References

- Ticket and research named above
- `frontend/src/main.tsx`, `frontend/src/style.css`, `frontend/package.json`, `frontend/vite.config.ts`
- `src/main/java/app/detour/identity/IdentityController.java`, `src/main/java/app/detour/security/SecurityConfiguration.java`, `src/main/java/app/detour/security/SpaCsrfCookieFilter.java`
- `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`, `src/test/java/app/detour/identity/ApplicationRestartIntegrationTest.java`
- `pom.xml`, `scripts/run.ps1`, `README.md`
