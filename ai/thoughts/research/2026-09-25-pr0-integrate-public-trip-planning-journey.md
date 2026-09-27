---
date: 2026-09-27
repository: loomspan-travel-demo
branch: main
commit: 987085f16fac494c5c992985e994e509bc9d62aa
ticket: ai/thoughts/tickets/2026-09-25-pr0-integrate-public-trip-planning-journey.md
tags: [trip, integration, frontend, booking, migration]
---

# Public Trip Planning Journey Research

## Research Question

How do the committed PR1–PR5 outcomes connect across public entry, authentication, Trip creation, Working plan, Saved options, booking, and existing-data upgrade, and what integration surfaces remain in the checked-out code?

## Summary

The checkout contains sequential PR2–PR5 commits on `main`; PR1's outcome was included before those commits. Public Home and Trips are separate, and the guest's trip form lives in React state until authentication and another explicit Start planning submission (`frontend/src/App.tsx:29-35`, `frontend/src/components/TripStartForm.tsx:48-53`). Creation makes one owned Trip and Working plan (`src/main/java/app/detour/trip/TripService.java:80-96`). The current workspace saves named options explicitly, can load an option into Working, and updates an unbooked option in place (`frontend/src/components/TripWorkspace.tsx:981-1054`, `src/main/java/app/detour/trip/TripService.java:385-425`). Booking uses the selected option's dates (`src/main/java/app/detour/booking/BookingTransactionExecutor.java:55-59`). V19 normalizes existing drafts and preserves existing planned and booking rows (`src/main/resources/db/migration/V19__model_named_trips_and_dated_options.sql:1-45`).

The README still describes Trip creation as making a Draft and saving it as Planned (`README.md:22-27`). Legacy Draft and Planned handlers, endpoints, and test names remain; the current Saved option card is rendered with `singleWorking`, which hides its Draft branch (`frontend/src/components/TripWorkspace.tsx:1999-2021`, `frontend/src/components/AlternativeCard.tsx:47-49`, `frontend/src/components/AlternativeCard.tsx:149-179`). The Trip revision flow still generates generic `Option N` names for copied options (`src/main/java/app/detour/trip/TripService.java:710-717`). These are current facts for integration planning, rather than a proposed disposition.

## Repository State

- Observed 2026-09-27 16:07 PDT; `main` at `987085f16fac494c5c992985e994e509bc9d62aa`; `git status --short` was empty before this research artifact.
- Recent commits: PR2 `3d995be`, PR3 `f2ad58e`, PR4 `f9ebcc2`, PR5 `987085f`. Historical PR5 review records a focused frontend run of 90 passing tests (`ai/thoughts/reviews/2026-09-25-pr5-present-working-plan-and-saved-options-review-3.md:38`); that is prior evidence, not a fresh PR0 test result.

## Current Behavior and Data Flow

1. `App` mounts public Home, tries `/api/profile` for an existing session, and renders either public Home/Trips/auth or authenticated Home/Trips/Profile (`frontend/src/App.tsx:29-65`, `frontend/src/App.tsx:129-161`). A guest enters all Trip start fields on Trips. Validation precedes authentication; no Trip request is made for the guest (`frontend/src/components/TripStartForm.tsx:25-54`). After login/registration, the form draft remains in `App` state and a new Start planning click calls `tripsApi.createTrip`; failed requests leave entered values and report failure (`frontend/src/components/TripStartForm.tsx:54-80`). This state is in memory, so a browser reload is a separate boundary.
2. Authenticated Trips displays the same inline start form alongside the Trip list and opens the new Trip's workspace after success (`frontend/src/components/ProfileScreen.tsx:328-335`). Profile contains account details and password change (`frontend/src/components/ProfileScreen.tsx:338-373`). Navigation retains the mounted workspace behind other views (`frontend/src/components/ProfileScreen.tsx:291-325`). The app hides it while an expired session is reauthenticated (`frontend/src/App.tsx:129-148`).
3. Creation validates name, dates, travelers, ages, optional budget, and destination, then persists a Trip with one Working draft (`src/main/java/app/detour/trip/TripService.java:80-96`, `src/main/java/app/detour/trip/JdbcTripRepository.java:29-50`). Working date changes revalidate selected components (`src/main/java/app/detour/trip/TripService.java:110-150`). Working changes and failures are represented through workspace autosave state and messages (`frontend/src/components/TripWorkspace.tsx:467`, `frontend/src/components/TripWorkspace.tsx:1684-1693`).
4. The workspace saves a new option only from a component-bearing Working plan and with an entered name. It can load an option into Working and either update that same unbooked option or save a new one (`frontend/src/components/TripWorkspace.tsx:981-1134`, `frontend/src/components/TripWorkspace.tsx:1684-1694`). The service enforces names, versions, ownership, a selected component, booked immutability, and option-specific date snapshots (`src/main/java/app/detour/trip/TripService.java:385-425`, `src/main/java/app/detour/trip/TripService.java:446-510`). Comparison and booking operate on Saved options in the workspace (`frontend/src/components/TripWorkspace.tsx:1943-2021`).
5. The booking transaction reads the requested planned/option ID, checks that option's Portland departure midnight, current Trip version, active-booking uniqueness, party eligibility, inventory, and pricing before committing (`src/main/java/app/detour/booking/BookingTransactionExecutor.java:55-105`). Cancellation also resolves option dates and maintains booking snapshots (`src/main/java/app/detour/booking/BookingTransactionExecutor.java:266-350`).
6. V19 assigns names and dates to old planned rows, converts surplus populated Drafts to Saved options with copied snapshots, removes surplus empty Drafts, and establishes one Working row per Trip (`src/main/resources/db/migration/V19__model_named_trips_and_dated_options.sql:1-42`, `src/main/resources/db/migration/V19__model_named_trips_and_dated_options.sql:130-174`).

## Key Components

- `frontend/src/App.tsx:29-161` — public/authenticated shell, auth handoff, and retained trip start draft.
- `frontend/src/components/ProfileScreen.tsx:31-49` — authenticated navigation, Trip list/workspace, profile details.
- `frontend/src/components/TripStartForm.tsx:11-22` — Trip start fields and defaults; `:25-80` validation and explicit creation.
- `frontend/src/components/TripWorkspace.tsx:975-1134` — named option save, update, and load actions; `:1943-2021` presentation.
- `frontend/src/components/AlternativeCard.tsx:47-49` — legacy lifecycle branching; `:149-179` legacy Draft controls; `:182-220` Saved option controls.
- `src/main/java/app/detour/trip/TripController.java:27-130` — owned Trip and option HTTP routes, including retained legacy routes.
- `src/main/java/app/detour/trip/TripService.java:324-382` — legacy Draft mutation endpoints; create/duplicate/delete return one-Working conflicts, while promotion still inserts a generic named option.
- `src/main/java/app/detour/trip/TripService.java:710-717` — revision copy assigns `Option 1`, `Option 2`, etc. to source options.
- `src/main/java/app/detour/security/SecurityConfiguration.java:35-53` — public SPA/auth endpoints, authenticated remainder, session and CSRF setup.
- `README.md:22-27` — user documentation still describes Draft and Planned product flow.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Public and auth handoff | Guest form values remain in `App` state; auth is required at submit, with another explicit submit afterward (`frontend/src/App.tsx:29-35`, `frontend/src/components/TripStartForm.tsx:48-70`). |
| Working and options | UI displays one Working plan and named options; backend has one-Working database constraint and separate option dates (`frontend/src/components/TripWorkspace.tsx:1684-1694`, `src/main/resources/db/migration/V19__model_named_trips_and_dated_options.sql:168-173`). |
| Remaining old actions | Legacy workspace handler code and HTTP methods remain, although current Saved option cards use `singleWorking` and hide Draft controls; legacy promotion endpoint still creates a generic `Option N` (`frontend/src/components/TripWorkspace.tsx:644-680`, `:1138-1205`, `:1999-2021`; `src/main/java/app/detour/trip/TripService.java:354-382`). |
| Revision naming | Revised Trip copies source option selections but assigns generic names (`src/main/java/app/detour/trip/TripService.java:710-717`); `TripRevisionModal` labels selected sources as Saved options (`frontend/src/components/TripRevisionModal.tsx:274-293`). |
| Cancellation follow-up | The modal offers Saved option load or continuing the existing Working plan; the latter only closes the modal and sets status, with no new Draft persisted (`frontend/src/components/PostCancellationTriageModal.tsx:133-200`, `frontend/src/components/TripWorkspace.tsx:1364-1400`). |
| Documentation | README steps 2–4 and persistence description retain Draft/Planned terminology (`README.md:22-27`); About demo uses Working plan and named options (`frontend/src/components/AboutDemoTab.tsx:26`). |

## Existing Tests and Fixtures

- `frontend/src/PublicTripFlow.test.tsx:29-202` covers public Home, in-memory guest fields, auth continuation, failed creation retry, pending duplicate submission, session expiry, and account switching with React Testing Library/Vitest.
- `frontend/src/ProgressiveTripBuilder.test.tsx:55-1295` covers builder entry modes, explicit option save, searches/selections, conflict/refresh behavior, and accessibility interactions. `frontend/src/ItineraryComparisonAndBookingReview.test.tsx:219-874` covers comparison, option date display, review, booking submit, and conflicts.
- `src/test/java/app/detour/trip/TripModelForwardMigrationIntegrationTest.java:23-128` exercises old V18 to current migration. `src/test/java/app/detour/trip/TripApiIntegrationTest.java` exercises Trip/option HTTP contracts. `src/test/java/app/detour/booking/BookingApiIntegrationTest.java:249` includes a Saved option with independent dates; booking cancellation and concurrency have separate integration test classes.
- Existing tests are split between mocked frontend flows and backend integration tests. The researched files do not contain one executable browser journey running public start through real backend booking and migrated-data acceptance as a single scenario. No tests were run during this read-only research step.

## Dependencies and Operational Constraints

- Frontend scripts are `npm test` and `npm run build` (`frontend/package.json`); backend uses Maven wrapper and Spring/H2/Flyway (`pom.xml`, `src/main/resources/application.yml`).
- Public SPA routes and auth actions are permitted; other API calls require authentication and CSRF (`src/main/java/app/detour/security/SecurityConfiguration.java:35-53`). The research did not contact live services.
- Catalog-supported travel remains March 2027 and destination limited in the form (`frontend/src/components/TripStartForm.tsx:10-15`, `:33-43`).

## Historical Context

The PR0 ticket says the five implementation tickets are predecessors and asks for final integration. The latest PR5 review reports its own scoped independent review and tests (`ai/thoughts/reviews/2026-09-25-pr5-present-working-plan-and-saved-options-review-3.md:5-38`); the checked-out source above is the current authority for PR0.

## Open Questions

- Should revision copies retain source Saved option names or receive distinct derived names? Current behavior is generic `Option N`; the ticket asks for named options but does not specify revision naming.
- Which retained legacy Draft/Planned handlers and API routes are still compatibility contracts versus obsolete surfaces? Current UI reachability and backend behavior differ by route.
