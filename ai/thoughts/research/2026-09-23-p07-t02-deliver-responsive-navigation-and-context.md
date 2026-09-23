---
date: 2026-09-23
repository: loomspan-travel-demo
branch: main
commit: 2d3712a0c6f03a00a79b27a636bc49bf5eb51b1c
ticket: ai/thoughts/tickets/2026-09-23-p07-t02-deliver-responsive-navigation-and-context.md
tags: [frontend, navigation, responsive, trips, drafts, comparison]
---

# Responsive Navigation and Trip Context Research

## Research Question

How do authenticated navigation, Trip/Draft context, component selection, responsive presentation, comparison, and save/retry states currently work for P07-T02?

## Summary

The authenticated application renders one `ProfileScreen` containing the trip overview and an exclusive Trip workspace view. The overview supplies Plan Trip, Airfare, and Stay entry actions, while the workspace owns the Trip response, the first Draft as its active Draft, component modes, autosave, comparison, and booking subviews. Returning to the overview clears the active Trip; there is no distinct Home control or persistent authenticated navigation across all primary subviews. The server already returns owned Trip detail with Draft selections and calculated tallies. Desktop comparison uses a matrix, mobile comparison uses a tab-selected stacked panel, and CSS swaps them at 768px; the mobile selector currently scrolls horizontally but is not styled as persistent during vertical scrolling.

## Repository State

- Checked at 2026-09-23 13:26 PDT. Branch `main`, commit `2d3712a0c6f03a00a79b27a636bc49bf5eb51b1c`; `git status --short` was empty. No pre-existing changes were observed.
- No `AGENTS.md` was found by `rg --files -g AGENTS.md` in the checkout.
- The current checkout includes the preceding P07-T01 visual-system commit (`5d4ea02` in recent frontend history), so its CSS is the baseline for this ticket.

## Current Behavior and Data Flow

1. `App` initially renders account checking, loads `/api/profile`, and renders the public auth or authenticated `ProfileScreen` from that response (`frontend/src/App.tsx:8-9`, `:23-51`, `:95-110`; `frontend/src/api/identityApi.ts:64-85`). Successful logout replaces the authenticated screen with the public screen; a network failure leaves the authenticated view visible with an error (`frontend/src/App.tsx:61-80`).
2. `ProfileScreen` uses `viewMode` (`overview`/`workspace`) and `activeTrip` in component state (`frontend/src/components/ProfileScreen.tsx:34-43`). Opening a trip first calls `tripsApi.getTrip` and then sets the workspace view (`:88-96`). A new trip is created by `TripCreateModal`, which returns the created Trip and entry mode to the same state (`:305-317`; `frontend/src/components/TripCreateModal.tsx:87-133`). The overview presents the three entry actions in both empty and populated states (`frontend/src/components/EmptyProfileState.tsx:16-43`; `frontend/src/components/TripListSection.tsx:151-187`).
3. While the workspace is visible, `ProfileScreen` returns only `TripWorkspace`; its back callback sets overview, clears `activeTrip` and entry context, then refreshes profile (`frontend/src/components/ProfileScreen.tsx:199-231`). The overview shows Log out but no separate Home or active Trip control (`:233-252`). The ordinary workspace shows Back to all trips and Log out (`frontend/src/components/TripWorkspace.tsx:1187-1239`); comparison, booking review, and booking confirmation return alternate top-level JSX before those workspace controls (`:1140-1185`). Comparison has its own Back to Trip Workspace action (`frontend/src/components/ItineraryComparisonView.tsx:75-98`), and booking confirmation has view-workspace/all-trips actions through props (`frontend/src/components/TripWorkspace.tsx:1174-1183`).
4. `TripWorkspace` initializes its Trip from `initialTrip`; `activeDraft` is `trip.drafts[0]` when present (`frontend/src/components/TripWorkspace.tsx:72-116`). Airfare and Stay initialize to selected/searching/empty based on saved selection and entry mode; rental initializes selected or hidden (`:118-137`). Saved selection changes the corresponding mode to selected, and removal/cancel returns the mode to selected or empty/hidden (`:228-250`, `:550-645`, `:1385-1445`). `Add a car` is a user action that reveals rental search (`:1434-1445`). Component searches receive both Trip and Draft IDs (`frontend/src/components/AirfareSearchSection.tsx:57`; `frontend/src/components/StaySearchSection.tsx:40`; `frontend/src/components/RentalSearchSection.tsx:111`).
5. Shared Trip fields are locally edited and debounced 600ms before `replaceSharedDetails` with `expectedVersion`; concurrent saves serialize through refs (`frontend/src/components/TripWorkspace.tsx:264-302`, `:309-479`). Success stores the returned Trip and reports saved. Validation, conflict, network, and other failures set error or conflict; only conflict currently renders a Reload from server control, and `handleReloadFromServer` replaces local inputs with server data (`:377-500`, `:1258-1279`). Airfare, Stay, and rental selection mutations send Trip and Draft versions, apply the returned Trip on success, and set status/error on failure (`:550-645`). The error status text has no direct retry control in the current status region (`:1258-1279`).
6. Draft tally is rendered from the active Draft's selections, showing `Not selected` for absent components (`frontend/src/components/TripWorkspace.tsx:1368-1384`; `frontend/src/components/ItinerarySummaryTally.tsx:47-100`). The server's Trip response includes Draft selections and calculated tallies (`src/main/java/app/detour/trip/TripService.java:599-635`). `getTrip` is the existing refresh path (`frontend/src/api/tripsApi.ts:649-650`); the controller takes an authenticated principal and delegates to owner-scoped detail (`src/main/java/app/detour/trip/TripController.java:43-55`; `src/main/java/app/detour/trip/TripService.java:482-496`).
7. Comparison candidates are selected from Planned alternatives in one loaded Trip; `handleToggleCompare` rejects a fourth, and the launch control needs at least two (`frontend/src/components/TripWorkspace.tsx:981-995`, `:1140-1153`, `:1645-1649`). The comparison component renders a mobile selected itinerary panel and a desktop table; absent components use explicit `No ... selected` content (`frontend/src/components/ItineraryComparisonView.tsx:53-66`, `:107-194`, `:292-295`).

## Key Components

- `frontend/src/App.tsx:23` — authentication and global notice state, account load and logout.
- `frontend/src/components/ProfileScreen.tsx:24` — overview/workspace state, entry actions, trip open/creation, profile password actions.
- `frontend/src/components/TripWorkspace.tsx:72` — Trip/Draft state, autosave, component modes, comparison/booking subviews, workspace header.
- `frontend/src/components/TripCreateModal.tsx:30` — first-time creation form and chosen entry mode.
- `frontend/src/components/TripListSection.tsx:151` and `frontend/src/components/EmptyProfileState.tsx:8` — populated and empty overview entry points.
- `frontend/src/components/ItineraryComparisonView.tsx:14` — mobile itinerary tabs/stacked content and desktop matrix.
- `frontend/src/components/BookingHistorySection.tsx:11` — async history load, error state, and nested accordion.
- `frontend/src/style.css:635` — 768px comparison switch and 520px mobile adjustments.
- `src/main/java/app/detour/trip/TripController.java:26` and `src/main/java/app/detour/trip/TripService.java:482` — authenticated, owner-scoped Trip operations.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Authenticated navigation | Overview has profile heading and Log out; workspace has Back to all trips and Log out; comparison and booking subviews have local back actions (`frontend/src/components/ProfileScreen.tsx:199-252`; `frontend/src/components/TripWorkspace.tsx:1140-1239`). |
| Trip/Draft continuity | Parent clears active Trip on back, while workspace always chooses first Draft and owns transient component/search state (`frontend/src/components/ProfileScreen.tsx:213-218`; `frontend/src/components/TripWorkspace.tsx:86-137`). |
| Creation | Empty and populated overviews expose the same three entry points, which open a modal and then the workspace (`frontend/src/components/EmptyProfileState.tsx:16-43`; `frontend/src/components/TripListSection.tsx:164-187`; `frontend/src/components/ProfileScreen.tsx:305-317`). |
| Responsive profile and cards | Cards, trip counts, actions, and alternatives are nested; 520px CSS stacks profile/trip/action areas (`frontend/src/components/TripListSection.tsx:48-147`; `frontend/src/style.css:66-88`, `:640-654`). |
| Comparison | Desktop table has minimum column widths and horizontal overflow; mobile uses one selected itinerary panel and horizontally scrollable tabs; CSS switches at 768px (`frontend/src/style.css:226-259`, `:635-638`; `frontend/src/components/ItineraryComparisonView.tsx:107-194`). |
| Search, booking, history | Component searches each have loading/error/empty rendering; booking history loads separately, shows loading or error when empty, then an accordion (`frontend/src/components/AirfareSearchSection.tsx:119-126`; `frontend/src/components/StaySearchSection.tsx:102-109`; `frontend/src/components/RentalSearchSection.tsx:195-203`; `frontend/src/components/BookingHistorySection.tsx:20-77`). |
| API and persistence | API client sends same-origin credentials and CSRF on mutation; server uses owner ID and optimistic Trip/Draft versions; Planned alternatives are rejected as mutable Drafts (`frontend/src/api/identityApi.ts:28-61`; `src/main/java/app/detour/trip/TripService.java:482-522`, `:855-859`). |

## Existing Tests and Fixtures

- `frontend/src/App.test.tsx:14-121` covers account restoration, logout, auth failures, and password behavior. Its Trip tests cover empty onboarding (`:139`), nested profile hierarchy (`:215`), autosave including failed input preservation (`:365`), conflict/reload (`:442`), and direct workspace logout (`:1208`). The current tests do not establish a distinct Home/Profile/active Trip navigation model.
- `frontend/src/ProgressiveTripBuilder.test.tsx:66-252` covers all three entry modes and explicit car reveal. Selection/tally, component removal, and conflict cases begin at `:321`, `:490`, `:605`, `:685`, `:766`, and `:860`.
- `frontend/src/ItineraryComparisonAndBookingReview.test.tsx:226-434` covers two-to-three Planned selection, desktop matrix, mobile tab interaction, and absent-vs-zero component content. Booking review and active-booking paths follow at `:459-757`.
- `frontend/src/FeeFreeCancellationAndTriage.test.tsx:580`, `:918` covers booking history rendering and fetch failure. `frontend/src/DraftPromotion.test.tsx:651` contains a mobile-viewport-oriented interaction test.
- Backend integration tests include `src/test/java/app/detour/trip/TripApiIntegrationTest.java`, component selection suites, `DraftReadinessAndPlannedSnapshotIntegrationTest.java`, and `src/test/java/app/detour/booking/BookingApiIntegrationTest.java`; these target server contracts rather than browser layout. No live service was contacted for research.
- Available frontend commands are `npm test` (Vitest) and `npm run build` (TypeScript and Vite) from `frontend/package.json`. These were located, not run in this research step. Visual width/overflow behavior cannot be established by the cited jsdom tests alone.

## Dependencies and Operational Constraints

- The frontend uses React 19 and Vite with no client routing library (`frontend/package.json`); the backend exposes one SPA fallback path, `/profile` (`src/main/java/app/detour/web/SpaRouteController.java:6-12`).
- `identityApi.request` sends cookies and a CSRF header for unsafe requests, normalizes network/API failures, and does not use browser-local persistence (`frontend/src/api/identityApi.ts:23-61`).
- The server scopes Trip list and detail to the authenticated owner (`src/main/java/app/detour/trip/TripController.java:37-55`; `src/main/java/app/detour/trip/TripService.java:124-182`, `:482-496`). Mutation conflict responses include current version fields (`src/main/java/app/detour/trip/TripService.java:507-522`).
- This ticket's Phase 7 sources assign shared visual conventions to P07-T01, disclosure copy to P07-T03, and final keyboard/assistive-technology audit to P07-T04 (`ai/thoughts/tickets/2026-09-23-p07-t02-deliver-responsive-navigation-and-context.md:25-28`; `ai/thoughts/phases/phase-7-product-experience-and-release.md:16-28`).

## Historical Context

- Phase 5 specifies at most three Planned alternatives from one Trip, desktop columns, mobile stacked comparison with a persistent itinerary selector, and immutable Planned snapshots (`ai/thoughts/phases/phase-5-planning-budget-and-comparison.md:30-47`). The checked-out code supplies the current mechanics cited above.
- The Phase 7 work package calls for Home/Profile/active Trip access, Draft context preservation, narrow-screen usability, and purposeful save/retry states (`ai/thoughts/phases/phase-7-product-experience-and-release.md:16-21`). Recent frontend history shows P07-T01 after the Phase 6 cancellation and booking work; the checked-out code remains the behavior source.

## Open Questions

- The ticket calls Home and Profile separate navigation targets, while the current overview combines Home entries, profile trip list, and account details. Their exact screen boundary is not represented in the current code.
- Current comparison begins at two alternatives, while the acceptance wording says “up to three”; Phase 5 says choose multiple. Whether a one-item comparison must be launchable is not explicit in the current sources.
- Actual 320px/zoom overflow and sticky-selector behavior need browser-level inspection; source CSS and jsdom interaction tests do not prove visual fit or persistence during scroll.
