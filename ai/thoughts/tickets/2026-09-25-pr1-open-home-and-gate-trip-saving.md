# pr1 — Open DeTour on Home and require an account when saving a trip

## Outcome

Visitors can see DeTour's Home page and begin entering trip details before logging in. Registration or login is required at the first save, so exploration is easy while every persisted Trip and Working plan belongs to an authenticated user.

## Requirements

- Show Home as the initial page for signed-out and signed-in visitors. Do not make an account check block the public Home page. The signed-out navigation includes Home, Trips, and Log in; authenticated navigation includes Home, Trips, Profile, and Log out.
- A signed-out visitor may open the Trips page and fill the inline trip-start form without creating a server-side Trip, Working plan, or Saved option. Explain before submission that saving requires an account.
- When a signed-out visitor selects Start planning, offer login and registration. After successful authentication, return to the trip-start flow with the entered name, destination, dates, traveler count, and ages intact, then save only once the user continues. Canceling authentication must return to the entered form without saving.
- A direct Log in navigation action opens the same login/registration experience and returns the user to their intended page. A signed-in visitor can open Profile and saved Trips without a second prompt.
- Keep Trip reads, writes, component selections, Saved options, and booking actions protected on the server. The public Home and unsaved trip-start form do not authorize anonymous API mutations. If a session ends during a save, retain the unsaved form or Working plan edits, request login, and allow an explicit retry without claiming the failed save succeeded.
- Preserve the existing registration and login security properties, including session handling, CSRF protection, owner isolation, and useful authentication errors. Do not create anonymous Trip records or a guest-account migration mechanism.

## Acceptance criteria

- [ ] A new signed-out visitor lands on Home with visible Log in and Trips navigation and can read public Home content without an authentication prompt.
- [ ] A signed-out visitor can enter the trip-start details; no Trip or Working plan is persisted before successful authentication and continuation.
- [ ] Login or registration from Start planning returns to the entered trip form with its values intact and creates exactly one owned Trip when the user continues; canceling authentication creates none.
- [ ] Direct login returns the user to the intended page, and an authenticated visitor can navigate to Home, Trips, and Profile.
- [ ] An expired session during a save leaves the edited values available, displays that saving failed, and succeeds only after login and an explicit retry.
- [ ] Anonymous Trip, option, component, and booking API requests remain rejected; authenticated owner checks and CSRF behavior still pass.

## Context

- This is the public-entry and authentication ticket for the named-Trip, single-Working-plan planning change. The inline trip-start experience is delivered by `2026-09-25-pr4-move-trip-start-to-inline-trips-page.md`; coordinate the handoff contract with it. The trip/option data model and save workflow are separate dependent tickets.
- Today the unauthenticated app renders the login form instead of Home. Home and the trip list are inside the authenticated Profile screen. Static application assets are already public, while Trip APIs require authentication. These are source hints, not a direction to weaken server protection.
- Scope excludes anonymous catalog searching, anonymous bookings, password recovery, third-party identity providers, and server-side storage of an unfinished guest form.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Public navigation and the authentication handoff change the user-visible security boundary and session-expiry behavior across the application.
- **Reassessment triggers:** none.
