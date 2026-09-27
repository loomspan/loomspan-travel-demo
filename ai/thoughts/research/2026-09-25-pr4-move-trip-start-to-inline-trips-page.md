---
date: 2026-09-26
repository: loomspan-travel-demo
branch: main
commit: f2ad58e9180c680eaee5f404b7f8d3d3247952fa
ticket: ai/thoughts/tickets/2026-09-25-pr4-move-trip-start-to-inline-trips-page.md
tags: [frontend, trips, navigation, authentication, accessibility]
---

# Inline Trips Page Research

## Research Question

How do the current Home, Trips, Profile, trip-start, and authentication flows work after PR3, and where are the PR4 behaviors currently represented?

## Summary

The application already has separate Home and Trips navigation buttons and an inline `TripStartForm` for guests and signed-in users. Home's three entry buttons select a start mode and open that form without a create request. The form gathers name, destination, dates, count, and ages, validates before authentication or save, and sends one authenticated create request. Guest draft values live in `App` state across cancel and login/registration. The signed-in Trips view currently contains only the start form; the actual trip list remains inside Profile alongside email and password controls. The list still presents a shared-looking Trip date range and alternative IDs, even though the API now exposes named, separately dated options.

## Repository State

- Observed 2026-09-26 21:31 America/Los_Angeles on `main`, commit `f2ad58e9180c680eaee5f404b7f8d3d3247952fa` (`pr3 — Save one Working plan and create comparison options only on request`). `git status --short` was empty before this document.
- This step changed only this research document. No live services were contacted.

## Current Behavior and Data Flow

1. `App` initially renders a public Home and checks `/api/profile` in the background. Signed-out primary navigation has Home, Trips, and Log in; authenticated navigation is in `ProfileScreen` and has Home, Trips, Profile, workspace when active, and Log out (`frontend/src/App.tsx:28-64`, `:132-159`; `frontend/src/components/ProfileScreen.tsx:263-271`). Profile data includes the user's upcoming and past Trip summaries from the authenticated controller (`src/main/java/app/detour/identity/IdentityController.java:68-72`).
2. Home offers Plan Trip, Airfare, and Stay modes. Either App or ProfileScreen records the mode and selects Trips; merely opening the form does not call `createTrip` (`frontend/src/components/HomeScreen.tsx:18-20`; `frontend/src/App.tsx:156-157`; `frontend/src/components/ProfileScreen.tsx:140-145`). `TripCreateModal.tsx` remains in the repository, but current App/ProfileScreen imports and renders `TripStartForm` instead (`frontend/src/App.tsx:7`, `:157`; `frontend/src/components/ProfileScreen.tsx:8`, `:304`).
3. `TripStartForm` holds editable name, destination, March 2027 dates, count 1–8, and one visible age input per traveler. Client validation checks a nonblank name, both dates within the supported window, 1–14 nights, count, and whole-number ages 0–120. Count changes resize the age array, retaining indices that remain visible. `STAY` also exposes accommodation type (`frontend/src/components/TripStartForm.tsx:5-14`, `:29-50`, `:71-85`). The initial name is blank; the form currently has no budget field (`:11-14`, `:75-85`).
4. On valid guest submission, the form calls the authentication callback without writing a Trip. App preserves the draft in its own state while `AuthScreen` is displayed; cancel returns to that state. After login or registration, `ProfileScreen` receives the same draft and initial Trips destination, and another explicit Start planning click submits it (`frontend/src/components/TripStartForm.tsx:50-65`; `frontend/src/App.tsx:31`, `:78-84`, `:143-159`). Logout clears the draft (`frontend/src/App.tsx:85-99`).
5. For a signed-in user, Start planning calls `POST /api/trips` with name, destination, dates, count, and ages, guards against duplicate pending submissions, and reports validation, expired session, or network failures (`frontend/src/components/TripStartForm.tsx:51-68`). The backend validates those values and optional budget, inserts an owned Trip and its one Working row, and returns the Trip (`src/main/java/app/detour/trip/TripService.java:79-97`; `src/main/java/app/detour/trip/TripController.java:37-41`). `ProfileScreen` then sets the active Trip and switches to its workspace view (`frontend/src/components/ProfileScreen.tsx:304-310`). The workspace is a separate view, rather than content within the Trips view.
6. Authenticated Trips currently renders only `TripStartForm`; Profile renders email, TripListSection or EmptyProfileState, and password form (`frontend/src/components/ProfileScreen.tsx:303-354`). `navigateTo('profile')` refreshes summaries, while `navigateTo('trips')` does not (`:147-152`). Workspace Back and deletion also return to Profile (`:287-299`). Opening an existing Trip fetches its owned detail and switches to workspace, with retry feedback on failure (`:116-139`, `:273-280`).
7. Trip cards use `trip.label` as heading, then destination and `trip.startDate`–`trip.endDate`; alternative items show lifecycle/version and a truncated technical ID rather than option name and dates (`frontend/src/components/TripListSection.tsx:15-31`, `:49-96`). API summary types already carry Trip `name`, alternative `name`, and alternative start/end dates (`frontend/src/api/tripsApi.ts:3-32`). The server has an owned, version-guarded rename route (`src/main/java/app/detour/trip/TripController.java:58-60`; `src/main/java/app/detour/trip/TripService.java:100-107`), while `tripsApi` currently exposes creation, listing, detail, shared updates, and option actions without a rename client method (`frontend/src/api/tripsApi.ts:666-685`).

## Key Components

- `frontend/src/App.tsx:28` — public/authenticated shell, draft persistence, and auth handoff.
- `frontend/src/components/ProfileScreen.tsx:47` — signed-in navigation, trip list placement, workspace transitions, and account actions.
- `frontend/src/components/TripStartForm.tsx:29` — start validation, guest gate, authenticated create, and field rendering.
- `frontend/src/components/TripListSection.tsx:15` — Trip and alternative summary presentation and list actions.
- `frontend/src/components/HomeScreen.tsx:18` — three start entry points.
- `frontend/src/api/tripsApi.ts:3` — list and detail response types; `:428` create request.
- `src/main/java/app/detour/trip/TripService.java:79` — create and shared-field validation; `:100` owned rename.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Navigation and list | Trips is an explicit destination, but the list is rendered in Profile (`ProfileScreen.tsx:263-265`, `:304`, `:327-340`). |
| Start form | One inline form serves all entry modes; the name starts blank, budget is absent, and count resize truncates hidden age entries (`TripStartForm.tsx:11-14`, `:75-85`). |
| Guest handoff | Draft is held in App state across auth; save waits for a second explicit click (`App.tsx:31`, `:143-159`; `TripStartForm.tsx:50-65`). |
| Working plan | Creation writes one Working row; signed-in UI opens a separate workspace view after save (`TripService.java:79-97`; `ProfileScreen.tsx:304-310`). |
| List meaning | Trip card uses compatibility dates and alternative IDs, despite named and dated option summary fields (`TripListSection.tsx:15-31`, `:61-96`; `tripsApi.ts:3-32`). |
| Accessibility and width | Navigation uses `aria-current` and heading focus; form labels are present, but validation text is not consistently bound to fields with `aria-describedby`/`aria-invalid` (`ProfileScreen.tsx:57-65`, `:263-265`; `TripStartForm.tsx:75-85`). CSS has responsive navigation, field-group, and card rules (`frontend/src/style.css:103`, `:634-658`, `:716-726`). |

## Existing Tests and Fixtures

- `frontend/src/PublicTripFlow.test.tsx:28-145` exercises public Home, guest draft handoff through cancel/registration, complete ages, direct login, failed create, and duplicate pending submission. Its workspace session tests start at `:148`.
- `frontend/src/App.test.tsx:47-57` checks navigation heading focus; `:206-286` covers empty-profile onboarding and creation; `:287-344` expects list hierarchy on Profile. Many other App tests navigate through Profile using the helper at `:25-34`, so moving the list changes those test paths.
- `src/test/java/app/detour/trip/TripApiIntegrationTest.java:61-166` covers creation and named/dated model behavior; `:240-247` and `:377-394` include missing/invalid age and create validation cases. `frontend/src/api/tripsApi.test.ts` covers client request serialization.
- Frontend scripts are `npm test` (Vitest) and `npm run build` (TypeScript/Vite) (`frontend/package.json`). Backend integration tests require the Java/Gradle environment. No browser-level narrow-width or assistive-technology test was located in the enumerated frontend test files.

## Dependencies and Operational Constraints

- `/api/profile` supplies private summaries only after authentication; Trip create/detail/rename are owned routes (`IdentityController.java:68-72`; `TripController.java:37-60`). The research did not call these live endpoints.
- Supported destinations and date window are enforced in backend validation (`TripService.java:82-90`, `:762-783`). The UI duplicates this as local validation and hard-coded choices (`TripStartForm.tsx:35-49`, `:76-81`).
- Trip summaries are coupled to the profile response, so placement and refresh behavior depend on `App.loadProfile` and `ProfileScreen.onRefreshProfile` (`App.tsx:41-63`, `:142`; `ProfileScreen.tsx:147-152`).

## Historical Context

- The PR1 ticket specifies the public Home and authentication handoff; its current behavior is now implemented in App and TripStartForm (`ai/thoughts/tickets/2026-09-25-pr1-open-home-and-gate-trip-saving.md:9-21`).
- PR2 established Trip names, complete ages, one Working row, and separately dated options; PR3 added explicit option saving. Those commits precede the observed PR4 baseline (`git log -5 --oneline`; `ai/thoughts/tickets/2026-09-25-pr2-model-named-trips-and-dated-options.md:9-21`). Historical research describes earlier baselines and does not supersede this checkout.

## Open Questions

- The ticket says Start planning opens the single Working plan “on the same page,” while the current signed-in UI switches from Trips to a separate workspace view. The exact intended page composition is not otherwise specified in the checked-out code.
- The ticket requires an editable name suggestion but does not prescribe wording or whether it updates after a destination change.
