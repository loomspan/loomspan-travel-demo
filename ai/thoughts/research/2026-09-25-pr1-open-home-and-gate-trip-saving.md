---
date: 2026-09-26
repository: loomspan-travel-demo
branch: main
commit: 7c473e08fbea1ec2841376cca6da09ccfc5dc325
ticket: ai/thoughts/tickets/2026-09-25-pr1-open-home-and-gate-trip-saving.md
tags: [frontend, authentication, navigation, trips, security]
---

# Public Home and Trip Save Gate Research

## Research Question

How do the current public entry, authentication, navigation, trip creation, save failure, and server authorization flows work for PR1?

## Summary

The SPA checks `/api/profile` on mount and renders the login/registration screen after an unauthenticated response. Home, trip list, trip creation, and workspace are all inside the authenticated `ProfileScreen`; there is no public Home or Trips screen. The existing Trip form immediately posts to `/api/trips` on valid submission. The server permits the SPA document and auth endpoints publicly while protecting all Trip endpoints with session authentication and CSRF for mutations.

## Repository State

Observed 2026-09-26 10:10 PDT. Repository root `C:/code/loomspan-travel-demo`, branch `main`, commit `7c473e08fbea1ec2841376cca6da09ccfc5dc325`; `git status --short` was empty at research start. No application files were modified during research.

## Current Behavior and Data Flow

1. `App` begins in `loading`, calls `identityApi.getProfile()` on mount, and switches to either `profile` or `public`. A 401 on initial load is suppressed as a notice; any other failure displays an error and shows `public` (`frontend/src/App.tsx:8-9`, `frontend/src/App.tsx:38-62`). The `public` branch renders only `AuthScreen`, while the `profile` branch renders `ProfileScreen` (`frontend/src/App.tsx:131-149`).
2. `AuthScreen` toggles login and registration forms, checks email presence and password length, clears password when switching modes or after submission, and invokes the callbacks supplied by `App` (`frontend/src/components/AuthScreen.tsx:11-58`). After either auth request, `App` reloads `/api/profile` to enter the authenticated screen; logout clears the screen to `public` after server success (`frontend/src/App.tsx:68-95`). No intended destination or unsaved trip-start data is currently carried through authentication.
3. `ProfileScreen` initializes `viewMode` to `home`. Its navigation currently offers Home, Profile, optional active Trip, and Log out; it has no Trips destination (`frontend/src/components/ProfileScreen.tsx:38-46`, `frontend/src/components/ProfileScreen.tsx:249-260`). Home's Plan Trip, Airfare, and Stay actions open `TripCreateModal` (`frontend/src/components/ProfileScreen.tsx:290-307`). Profile contains email, Trip list or empty state, and password form (`frontend/src/components/ProfileScreen.tsx:308-365`).
4. `TripCreateModal` holds destination, dates, count, and optional accommodation preference in local React state. It validates supported March 2027 dates and 1–8 travelers, then posts immediately to `/api/trips`; success opens the returned Trip workspace (`frontend/src/components/TripCreateModal.tsx:30-38`, `frontend/src/components/TripCreateModal.tsx:94-140`, `frontend/src/components/ProfileScreen.tsx:367-382`). It does not collect a Trip name or traveler ages, and its introductory hint says ages can be configured after creation (`frontend/src/components/TripCreateModal.tsx:249-266`). Cancel merely closes the dialog; no request occurs before submission.
5. The frontend request helper sends same-origin credentials and a CSRF cookie token for unsafe requests, parses JSON errors into `IdentityApiError`, and distinguishes network and malformed-response failures (`frontend/src/api/identityApi.ts:8-72`). `tripsApi.createTrip` uses that helper to POST `/api/trips` (`frontend/src/api/tripsApi.ts:643-644`). The create payload can include optional ages, but the current modal omits them (`frontend/src/api/tripsApi.ts:414-422`, `frontend/src/api/tripsApi.ts:513-522`).
6. `TripWorkspace` tracks unsaved details and offers a Retry save action after autosave failure (`frontend/src/components/TripWorkspace.tsx:397-406`, `frontend/src/components/TripWorkspace.tsx:440-503`, `frontend/src/components/TripWorkspace.tsx:1334-1337`). It currently handles network, validation, immutable Trip, and version conflict failures, but has no explicit unauthenticated branch in this save handler (`frontend/src/components/TripWorkspace.tsx:482-503`). `App` switches to `public` when a profile refresh or password change receives `UNAUTHENTICATED`, unmounting `ProfileScreen` and its workspace state (`frontend/src/App.tsx:47-61`, `frontend/src/App.tsx:112-123`).
7. Server security permits `/`, `/index.html`, assets, `GET /profile`, registration, and login; all other requests require authentication. CSRF is active and backed by a readable `XSRF-TOKEN` cookie (`src/main/java/app/detour/security/SecurityConfiguration.java:38-63`). `GET /profile` forwards to the SPA document, while `/api/profile` remains protected (`src/main/java/app/detour/web/SpaRouteController.java:6-13`). Identity login/registration save the principal to a session and rotate an existing session ID; logout invalidates the session (`src/main/java/app/detour/identity/IdentityController.java:40-62`, `src/main/java/app/detour/identity/IdentityController.java:85-93`).
8. `TripController` takes the authenticated principal for each Trip, draft, component, and booking route and passes its owner ID to services; a missing principal yields `UNAUTHENTICATED` (`src/main/java/app/detour/trip/TripController.java:36-57`, `src/main/java/app/detour/trip/TripController.java:236-282`). Creation is transactional, validates destination, dates, count, ages, and budget, then creates the owner-scoped Trip and initial Draft together (`src/main/java/app/detour/trip/TripService.java:78-105`). Trip detail is looked up by both public ID and owner ID (`src/main/java/app/detour/trip/TripService.java:98-105`).

## Key Components

- `frontend/src/App.tsx:8-149` — session bootstrap, auth callbacks, top-level public/profile switch, notices.
- `frontend/src/components/AuthScreen.tsx:11-58` — combined login and registration form.
- `frontend/src/components/ProfileScreen.tsx:38-46` — authenticated navigation and retained workspace state.
- `frontend/src/components/ProfileScreen.tsx:249-382` — Home, Profile with saved Trip list, and modal creation handoff.
- `frontend/src/components/TripCreateModal.tsx:94-140` — validation and immediate create request.
- `frontend/src/components/TripWorkspace.tsx:440-503` — mutable shared-details save and failure status.
- `frontend/src/api/identityApi.ts:27-72` — CSRF, credentials, and error response contract.
- `src/main/java/app/detour/security/SecurityConfiguration.java:38-63` — public routes, authenticated catch-all, CSRF policy.
- `src/main/java/app/detour/identity/IdentityController.java:40-93` — session lifecycle and protected profile.
- `src/main/java/app/detour/trip/TripController.java:36-57` — owner-derived Trip API entry points.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Public entry | Unauthenticated profile probe leads to `AuthScreen`, not Home (`frontend/src/App.tsx:38-62`, `frontend/src/App.tsx:131-149`). |
| Navigation | Home and Profile are the only primary destinations; Trips live inside Profile (`frontend/src/components/ProfileScreen.tsx:249-260`, `frontend/src/components/ProfileScreen.tsx:308-339`). |
| Trip start | Modal submission creates a Trip immediately; no guest draft or auth handoff exists (`frontend/src/components/TripCreateModal.tsx:94-124`). |
| Session expiry | Top-level profile refresh can unmount workspace; workspace save failure retains local edits while mounted, with no specific 401 handoff (`frontend/src/App.tsx:47-61`, `frontend/src/components/TripWorkspace.tsx:482-503`). |
| Server boundary | Trip API is authenticated, owner scoped, and CSRF protected (`src/main/java/app/detour/security/SecurityConfiguration.java:38-63`, `src/main/java/app/detour/trip/TripController.java:36-57`). |

## Existing Tests and Fixtures

- `frontend/src/App.test.tsx:34-178` exercises session restore, auth form behavior, logout, errors, and password change. Its signed-out tests currently expect the auth form; `frontend/src/App.test.tsx:196-272` exercises modal Trip creation.
- `frontend/src/App.test.tsx:424-610` exercises autosave input retention, retry, refresh failure, and navigation during in-flight saves. `frontend/src/ProgressiveTripBuilder.test.tsx:88-162` covers Home navigation and entry actions after an authenticated profile loads.
- `frontend/src/api/identityApi.test.ts:8-27` covers CSRF headers, malformed responses, and profile parsing.
- `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java:39-144` covers canonical registration, generic login failures, session/logout, CSRF, public SPA document, and protected profile response.
- `src/test/java/app/detour/trip/TripApiIntegrationTest.java:150-166` covers owner isolation; its creation tests begin at line 59. Backend tests use MockMvc and local database fixtures. Frontend tests run under Vitest/jsdom per `frontend/package.json`; Java tests use the repository Maven setup. Research did not execute tests or contact services.

## Dependencies and Operational Constraints

The current API has no anonymous Trip persistence path. The public shell must supply a CSRF cookie before unauthenticated registration/login POSTs, while session cookies are sent same-origin (`frontend/src/api/identityApi.ts:27-48`, `src/main/java/app/detour/security/SecurityConfiguration.java:38-63`). `pr4` specifies that its later inline Trips page will own the named Trip form, complete traveler ages, and guest handoff (`ai/thoughts/tickets/2026-09-25-pr4-move-trip-start-to-inline-trips-page.md:9-15`). The current backend create contract still derives Trip label from destination and dates (`src/main/java/app/detour/trip/TripService.java:78-95`).

## Historical Context

The Phase 1 identity ticket required public registration/login and static assets while keeping application/catalog endpoints authenticated (`ai/thoughts/tickets/2026-09-17-p01-t02-establish-secure-user-identity.md:16-29`). PR1 changes the frontend public-entry flow; PR4 later moves Trip start and list into the inline Trips destination (`ai/thoughts/tickets/2026-09-25-pr4-move-trip-start-to-inline-trips-page.md:9-15`). Checked-out source is the behavior described above.

## Open Questions

- PR1 calls for preserving name and ages through auth, but the current create modal and server create contract do not yet carry an editable name, while PR2/PR4 introduce those fields. Planning needs to identify the handoff representation that can survive those later changes.
- The current app has no URL-level client router; only `/profile` is a server SPA fallback. “Return to intended page” may refer to in-memory navigation, browser URL navigation, or both; the ticket does not specify which.
