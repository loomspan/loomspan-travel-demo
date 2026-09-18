---
date: 2026-09-18
repository: loomspan-travel-demo
branch: main
commit: 2e8a69bb92ca09e8964e36083a7060bd982a3d01
ticket: ai/thoughts/tickets/2026-09-17-p01-t03-deliver-authenticated-identity-experience.md
tags: [detour, react, identity, csrf, session, accessibility, routing]
---

# Deliver the DeTour Authentication and Profile Experience Research

## Research Question

What frontend, same-origin identity/CSRF contracts, routing behavior, disclosure surface, packaging path, and tests exist for P01-T03 to deliver the authenticated DeTour identity experience?

## Summary

The checked-out React application is a single static foundation message with no identity views, routing, API client, browser-storage use, disclosure content, or frontend interaction-test setup. The completed P01-T02 backend supplies the five required same-origin JSON endpoints, server-managed session behavior, readable CSRF-token cookie plus request header, and safe JSON error codes; account persistence and restart-session invalidation already have HTTP integration coverage.

The client delivery must span the React shell and the existing Spring Security/static-resource boundary. Only `/`, `/index.html`, built assets, `/favicon.ico`, and a future `/demo-disclosure/**` path are public; `/profile` is not a current static fallback or public matcher, while every other request is authenticated. Consequently, the checkout does not yet demonstrate the ticket's required direct protected client navigation behavior.

## Repository State

- Recorded 2026-09-18 in the America/Los_Angeles workspace timezone.
- Repository: `loomspan-travel-demo`; branch: `main`; commit: `2e8a69bb92ca09e8964e36083a7060bd982a3d01` (`cleanup after P01-T02`).
- `git status --short` returned no staged, unstaged, or untracked files before this research artifact was created.
- The immediately preceding P01-T02 commit is `796c523`; the current cleanup commit removed that ticket's temporary research, plan, testing-plan, and review records. Those historical records were consulted only to confirm the client contract now implemented in source.

## Current Behavior and Data Flow

1. Maven runs `npm ci` and `npm run build` from `frontend`, then copies `frontend/dist` into the executable JAR's static resources (`pom.xml:76-125`). `scripts/run.ps1` starts `target/detour-0.1.0-SNAPSHOT.jar` (`scripts/run.ps1:1-7`); no separate frontend server or external provider is needed.
2. The only React component renders the DeTour foundation heading and an explicit absence of trip/catalog/booking experience. It has no stateful flows or network requests (`frontend/src/main.tsx:5-14`), and the stylesheet is limited to that foundation shell (`frontend/src/style.css:1-9`). `frontend/package.json:1` has only `dev` and `build` scripts and declares no frontend test runner or interaction-testing libraries.
3. A normal public shell GET materializes a CSRF token in the readable `XSRF-TOKEN` cookie through `SpaCsrfCookieFilter` (`src/main/java/app/detour/security/SpaCsrfCookieFilter.java:11-20`). The security configuration uses `CookieCsrfTokenRepository` and requires the matching `X-XSRF-TOKEN` header for unsafe requests (`src/main/java/app/detour/security/SecurityConfiguration.java:39-42,58-61`). This cookie is intentionally readable by the SPA; the session cookie remains configured HTTP-only, secure by default, same-site Lax, and path-scoped to `/` (`src/main/resources/application.yml:4-11`).
4. Registration accepts `{email,password}` at `POST /api/auth/register`, creates the account and current server session, and returns `201` with `{email}`. Login accepts the same body at `POST /api/auth/login` and returns `204`; logout requires the active session and returns `204` after invalidating it (`src/main/java/app/detour/identity/IdentityController.java:38-62`). `GET /api/profile` returns only the principal's immutable email, and `PUT /api/profile/password` accepts `{currentPassword,newPassword}` and returns `204` (`IdentityController.java:65-75`; `src/main/java/app/detour/identity/ProfileResponse.java:1-4`).
5. Backend password validation is 12 through 128 Unicode code points inclusive and has no character-class rule (`src/main/java/app/detour/identity/PasswordPolicy.java:3-16`). Email input is trimmed and lowercased before validation or lookup (`src/main/java/app/detour/identity/EmailCanonicalizer.java:12-18`), and `IdentityService` supplies field validation, generic login failure, duplicate-email, and current-password-invalid error responses without returning credential values (`src/main/java/app/detour/identity/IdentityService.java:20-85`).
6. Public/authenticated access is enforced at the server: only the root static shell/assets, favicon, and `/demo-disclosure/**` are permitted along with registration and login; all other requests require authentication (`SecurityConfiguration.java:45-48`). There is no React router or server-side static-resource fallback in the source. An unauthenticated protected request receives JSON `401 UNAUTHENTICATED` (`src/main/java/app/detour/security/JsonAuthenticationEntryPoint.java:21-26`); rejected unsafe requests receive JSON `403 CSRF_INVALID` (`src/main/java/app/detour/security/JsonAccessDeniedHandler.java:21-26`).
7. Controller and security errors use `{code,message,fields}` and include stable mappings for malformed JSON, validation, authentication, CSRF, missing resources, and unexpected failures (`src/main/java/app/detour/api/ApiError.java:3-8`; `src/main/java/app/detour/api/ApiExceptionHandler.java:10-33`). The backend persists accounts in H2 but retains the Spring Security context in the ordinary servlet session, so restart preserves the account but rejects the prior session (`README.md:14-16`; `IdentityController.java:77-85`).

## Key Components

- `frontend/src/main.tsx:5-14` — sole React entry point; currently static and contains no identity client, protected view, routes, disclosure tab, storage access, or user-visible error/status handling.
- `frontend/src/style.css:1-9` — only current responsive styling surface; no form, focus, status, error, disclosure, or profile styles exist.
- `frontend/package.json:1` — React/Vite/TypeScript build dependencies; only `dev` and `build` scripts, with no automated frontend-test command.
- `src/main/java/app/detour/identity/IdentityController.java:38-75` — stable registration, login, logout, current-profile, and password-change HTTP contract.
- `src/main/java/app/detour/identity/IdentityService.java:20-85` — request validation, generic authentication failure, immutable principal-scoped profile, and current-password verification.
- `src/main/java/app/detour/security/SecurityConfiguration.java:39-61` — CSRF cookie/header exchange, in-memory security context, session fixation protection, and public/protected request matchers.
- `src/main/java/app/detour/security/SpaCsrfCookieFilter.java:11-20` — public-shell CSRF-cookie bootstrap.
- `src/main/java/app/detour/security/JsonAuthenticationEntryPoint.java:21-26` and `src/main/java/app/detour/security/JsonAccessDeniedHandler.java:21-26` — machine-readable unauthenticated and CSRF rejection responses consumed by the client boundary.
- `pom.xml:67-125` — Maven-managed frontend install/build and static-resource packaging into the single executable JAR.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Public identity UI | No registration, login, password visibility control, validation presentation, error summary, status announcement, or disclosure content exists in React (`frontend/src/main.tsx:5-14`). |
| Authenticated profile | `GET /api/profile` exposes only immutable email for the active principal (`IdentityController.java:65-68`; `ProfileResponse.java:1-4`); no profile UI or empty-state rendering exists. There are no Trip endpoints, tables, or fixtures in this checkout. |
| Session restoration | The current server session is loaded from `HttpSessionSecurityContextRepository` (`SecurityConfiguration.java:21-24`) and account/profile calls are principal-derived. A browser refresh can therefore re-query the profile using its server cookie; frontend code does not currently do so. |
| CSRF and credential boundaries | P01-T02 intentionally exposes `XSRF-TOKEN` for client header echo while keeping `JSESSIONID` HTTP-only (`SecurityConfiguration.java:58-61`; `application.yml:4-11`). No frontend source currently reads cookies, writes browser storage, or persists credentials. |
| Protected client navigation | The app has no route library or client routes. The only public shell paths are enumerated in `SecurityConfiguration.java:45-48`; no source registers `/profile` as a static fallback or public shell path. Thus a direct `/profile` request is not covered by the existing public shell behavior. |
| Disclosure | The security layer reserves public access for `/demo-disclosure/**` (`SecurityConfiguration.java:45-47`), but the frontend tree contains no matching asset or component, and no current UI contains the required disclosure facts. |
| Error handling | Backend returns safe JSON messages/codes (`ApiExceptionHandler.java:10-33`; `JsonSecurityErrorWriter.java:13-18`), but no React code interprets validation, authentication, session, CSRF, network, or unexpected-server outcomes. |
| Responsive/accessibility | The existing viewport metadata and `min-width: 320px` establish only a base shell (`frontend/index.html:3-8`; `style.css:1-4`). There are no identity controls, labels, focus rules, error discovery treatment, or live regions to test. |
| Legacy cleanup | Executable/product source searches found no active Wayfarer/Loomspan/model/conversation/trace/exchange/disruption/recovery UI, browser-storage key, or frontend client code. Historical planning documents intentionally retain legacy terminology and are outside the delivered product surface. |

## Existing Tests and Fixtures

- `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java:38-129` exercises registration, profile retrieval, inclusive password boundaries, canonical emails, generic authentication failure, password changes, logout, public-shell CSRF bootstrap, protected API `401`, CSRF `403`, and malformed-input handling. Its local client helper explicitly supplies `XSRF-TOKEN` and `X-XSRF-TOKEN` (`IdentityApiIntegrationTest.java:145-170`).
- `src/test/java/app/detour/identity/ApplicationRestartIntegrationTest.java:23-54` starts two application contexts against one temporary file-backed H2 database, confirms the account remains available after restart, rejects the old session, and permits a fresh login. It also asserts session-cookie attributes (`ApplicationRestartIntegrationTest.java:31-51`).
- `src/test/java/app/detour/DetourApplicationTest.java` is the remaining general Spring/Flyway context test.
- No frontend unit/component/interaction test files, browser test runner, mock HTTP layer, accessibility assertion library, responsive test harness, or packaged-JAR browser-flow test exists. `frontend/package.json:1` declares no `test` script.
- No tests were executed during research. The existing backend integration tests use in-memory/temporary H2 and localhost runtime contexts; they do not contact external services.

## Dependencies and Operational Constraints

- P01-T03 consumes the established same-origin P01-T02 contract; it does not need a cross-origin API, identity provider, persistent external session store, model credential, or model service. The current secure-cookie default is intended for HTTPS; `DETOUR_SECURE_COOKIES=false` is limited to local loopback development or HTTP tests (`README.md:14-24`; `application.yml:22-25`).
- The session identifier must remain server-cookie managed and JavaScript-inaccessible. The distinct anti-CSRF token is the only browser-readable security cookie because the SPA must submit it as a request header (`SecurityConfiguration.java:58-61`).
- Maven's normal lifecycle performs a fresh frontend dependency install and production build before Java resources are packaged (`pom.xml:76-125`), so frontend dependency/test-tool additions affect the build/package path.
- The roadmap fixes the product name as DeTour and permits the word “demo” only in the collapsed About this demo disclosure. Its required facts cover fictional suppliers/schedules/prices/availability/bookings, no payment/real reservation, PDX, the three destinations, March 2027, and itinerary creation/expansion/comparison/booking/cancellation (`ai/thoughts/phases/README.md:127-138`). This ticket does not authorize catalog, Trip, itinerary, comparison, booking, cancellation, or Events implementation.

## Historical Context

- P01-T02 was delivered in commit `796c523`, immediately before the current artifact-cleanup commit. Its archived implementation plan records the same five endpoint contracts and the `XSRF-TOKEN`/`X-XSRF-TOKEN` exchange now present in source; it marks React views, protected client routing, disclosure copy, browser-storage inspection, and accessibility as P01-T03 scope.
- The P01-T01 reset established the existing clean DeTour React/JAR shape and removed Wayfarer/model UI paths. The architecture retains Java 21, Spring Boot, React, H2, Flyway, Maven, and a single packaged application, while forbidding a replacement AI/model stack (`ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:35-39,47,54`).
- The product roadmap reserves final visual identity and final public authentication/disclosure prose for Phase 7, but already requires the functional interim disclosure and accessible responsive identity behavior (`ai/thoughts/phases/README.md:127-138,161-172`).

## Open Questions

- The codebase contains no prior frontend testing convention. Planning must establish the focused interaction-test location, runner, and package/build invocation that can exercise browser cookie/CSRF behavior without introducing JS-managed credential persistence.
- The requirement for direct navigation to a protected client route intersects the current public static-shell matcher and absence of routing/fallback source. Planning must trace the resulting server/client request behavior and preserve the server-side protected API boundary.
- No repository source specifies the exact interim screen copy beyond the mandatory password rule, error-safety constraints, and disclosure facts. The ticket permits functional interim content while reserving final copy/visual identity for Phase 7.
