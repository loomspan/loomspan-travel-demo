# Public Home and Trip Save Gate Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-25-pr1-open-home-and-gate-trip-saving.md`
- Research: `ai/thoughts/research/2026-09-25-pr1-open-home-and-gate-trip-saving.md`
- Outcome: Public Home and Trips entry, with client-held trip details until an authenticated user explicitly continues; expired saves retain edits for login and retry.

## Current State

`App` renders `AuthScreen` after `/api/profile` returns 401 and renders `ProfileScreen` only with a profile (`frontend/src/App.tsx`). `ProfileScreen` owns Home, the Trip list inside Profile, the creation modal, and the mounted workspace (`frontend/src/components/ProfileScreen.tsx`). `TripCreateModal` posts immediately to `/api/trips`; its request has no name and currently omits ages, though the API accepts ages (`frontend/src/components/TripCreateModal.tsx`, `frontend/src/api/tripsApi.ts`). `TripWorkspace.executeAutosave` keeps inputs on a failed request but treats 401 as a generic error; top-level profile refresh currently unmounts its state on 401 (`frontend/src/components/TripWorkspace.tsx`, `frontend/src/App.tsx`). The server already permits the SPA and auth endpoints but requires authenticated, CSRF-protected, owner-scoped Trip operations (`SecurityConfiguration`, `TripController`).

## Desired End State

The initial destination is Home for either session state. The public navigation shows Home, Trips, and Log in; authenticated navigation adds Profile and Log out. Trips contains a usable unsaved start form for guests, explains the account requirement, and exposes only public form content to them. Starting as a guest opens the common auth experience without posting; successful login or registration restores the same form, and only a subsequent Start planning creates a Trip. Cancel restores it unchanged. Direct login returns to the prior Home or Trips destination. A save that receives `UNAUTHENTICATED` visibly fails, retains the entered form or workspace edits, then requires login and an explicit retry. Existing backend authorization and CSRF remain intact.

## Scope
### In scope
- Public Home and Trips shell, auth destination/cancel state, and a client-held trip-start draft.
- Session-expiry handling for trip creation and Working/Draft workspace saves, preserving unsaved edits and explicit retry.
- Tests for public navigation, guest-to-auth handoff, exactly-one create, and auth/security regressions.

### Out of scope
- Anonymous API persistence or guest migration, anonymous catalog/booking, and identity provider changes.
- PR2's persisted name/Working-plan schema and PR3's option workflow. PR4 will refine the inline start form and saved Trips presentation against PR2's contract.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

The client must not issue `/api/trips` until authentication and a second explicit continue action. The guest draft should stay only in React memory; do not store credentials or trip details in browser storage. Initial 401 is the expected guest state, while network/server errors remain visible. Auth success alone must not trigger creation. A 401 during a create may have an ambiguous server outcome if the response is lost, so show failure and avoid an automatic retry; when practical refresh the owned Trip list before a retry to avoid presenting a false success. A 401 on workspace save must not unmount the workspace, and the retry must use the current edits and current session. If a different account logs in, clear prior owner's private Trip/workspace state rather than reveal or write it under the new account. Explicit logout likewise clears private state. Keep CSRF cookie handling and owner checks server-side as they are.

## Implementation Approach

Place navigation and auth destination in `App`, which already owns session transitions. Extract the current Home content for reuse by guest and authenticated visitors. Introduce a Trips view and a reusable client-held trip-start draft with destination, dates, count, and ages; include a client-held name in the draft so the auth handoff preserves it, while PR2 supplies the persisted name field. The current create endpoint does not accept name, so do not silently claim this first ticket persists it; PR2 and PR4 complete that named-trip contract. Keep the draft mounted or lift its state above the auth view, then render `AuthScreen` as the common login/registration step with a cancel control. Return to the recorded destination after auth, with no create request until Start planning is selected again.

Preserve the mounted workspace under an auth interruption (hidden/inert while login is shown), instead of replacing it with a public screen. `TripWorkspace` reports `UNAUTHENTICATED` through a callback and stops queued autosaves; it keeps its dirty inputs and exposes Retry save after same-account login. Clear retained private state on explicit logout or account switch. This is preferable to serializing the full workspace, which includes versioned Trip and component state and would introduce a second source of truth.

## Phase 1: Public shell and client-held trip-start draft

### Changes
- [x] `frontend/src/App.tsx` — replace the public/auth-only branch with a Home-first destination state and common Home/Trips navigation; keep profile bootstrap as a session probe, not a gate for public content.
- [x] `frontend/src/components/ProfileScreen.tsx` — extract existing Home content and Trip list/workspace ownership into shared renderable pieces or props so authenticated Home and Trips use the same destinations as guests; keep Profile account-focused. Route Home entry actions to the Trips start section.
- [x] `frontend/src/components/TripCreateModal.tsx` or replacement `frontend/src/components/TripStartForm.tsx` — make a reusable inline, client-held start form. Preserve name, destination, dates, traveler count, and age entries across navigation/auth; validate before asking for auth or creating. Explain that an account is needed to save; do not post on opening or on guest submit.
- [x] `frontend/src/api/tripsApi.ts` — its existing `CreateTripRequest` accepts ages; `TripStartForm` now passes them. Add persisted name only after PR2 supplies a server contract.
- [x] `frontend/src/App.test.tsx` and `frontend/src/ProgressiveTripBuilder.test.tsx` — replace signed-out auth-first and modal expectations with public Home/Trips and no-mutation opening assertions.

### Automated verification
- [x] `cd frontend; npm test -- --run src/App.test.tsx src/ProgressiveTripBuilder.test.tsx src/PublicTripFlow.test.tsx` — Home and Trips render for guests, with no Trip write before authenticated continuation.

### Optional developer checks
- [ ] Inspect Home/Trips keyboard flow and narrow viewport layout in a local browser.

## Phase 2: Authentication handoff and save interruption

### Changes
- [x] `frontend/src/App.tsx` — record login origin (Home or Trips, plus pending trip-start intent), render the common `AuthScreen`, and return on success or cancel without posting. Keep errors/focus behavior and session request race guards. Preserve retained workspace only for a matching account; clear it on explicit logout or different-account login.
- [x] `frontend/src/components/AuthScreen.tsx` — accept an `onCancel` callback when auth was entered from a destination and provide a visible cancel action. Keep existing mode-switch password clearing and error handling.
- [x] `frontend/src/components/TripStartForm.tsx` — after successful auth, keep the fields and require an explicit second Start planning action; disable duplicate submit while create is pending, and display create failures without clearing the draft. On 401 request login and retain the draft.
- [x] `frontend/src/components/TripWorkspace.tsx` — distinguish `UNAUTHENTICATED` from validation/conflict/network errors, retain dirty state, suspend automatic save attempts while auth is required, and expose a deliberate Retry save after login. Thread the signal through `ProfileScreen` to `App`.
- [x] `frontend/src/App.test.tsx` and `frontend/src/PublicTripFlow.test.tsx` — cover login, registration, cancel, direct login return, double-submit prevention, expired create, expired workspace save, same-account retry, and different-account private-state clearing.

### Automated verification
- [x] `cd frontend; npm test -- --run src/App.test.tsx src/ProgressiveTripBuilder.test.tsx src/PublicTripFlow.test.tsx` — guest and interruption flows pass, with exactly one create and no false saved status.
- [x] `cd frontend; npm run build` — TypeScript and Vite build succeed.

### Optional developer checks
- [ ] Observe the login handoff and retained form/workspace edits in a local browser with an expiring test session.

## Phase 3: Server boundary regression and full verification

### Changes
- [x] `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java` and `src/test/java/app/detour/trip/TripApiIntegrationTest.java` — extend only where existing cases do not prove public SPA access, unauthenticated Trip/component/option/booking rejection, owner isolation, and CSRF. Do not loosen `SecurityConfiguration` to make the guest form work.
- [x] `frontend/src/PublicTripFlow.test.tsx` — assert no browser storage of guest form or credentials, and no guest Trip reads after public navigation.

### Automated verification
- [x] `cd frontend; npm test` — frontend interaction suite passes.
- [x] `.\mvnw.cmd clean verify` — Java integration/security tests, frontend build, and packaged verifier pass against isolated test data.

### Optional developer checks
- [x] None beyond Phase 1/2 visual observations.

## Test Strategy

Use Vitest/Testing Library with mocked `fetch` for navigation and handoff, asserting request methods and bodies as well as screen state. Use a deferred response for duplicate-submit and session-expiry timing. Use MockMvc integration tests for server security; never treat client gating as authorization. Keep backend tests on repository-managed isolated databases and avoid live services.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Public Home and navigation | `App`, extracted Home, common nav | Signed-out bootstrap 401 shows Home, Trips, Log in, no auth prompt |
| Unsaved guest start | `TripStartForm`, client draft | Filled fields cause no Trip API write before auth and continuation |
| Login/register handoff and cancel | `App`, `AuthScreen`, form draft | Both auth paths restore fields; cancel creates none; one explicit continue creates once |
| Direct login and signed-in destinations | `App`, `ProfileScreen` | Login returns to origin; Home, Trips, Profile navigation works |
| Expired save preserves edits | `TripStartForm`, `TripWorkspace`, `App` | 401 shows failure, retains values, no saved status; login then explicit retry succeeds |
| Server Trip/security boundary | Unchanged `SecurityConfiguration`/owner services | MockMvc unauthorized, CSRF, owner-isolation cases pass |

## Risks and Rollback/Recovery

The current API cannot persist an editable name; PR2 must add that contract before PR4 can complete named-trip creation. Preserve the name in the PR1 client draft through auth and avoid sending an unsupported field. For a failed create with uncertain network outcome, do not auto-replay; leave fields and display an honest failure. Revert the frontend shell/handoff as one change if needed; server security remains untouched. Keep backend data migrations for PR2 separate.

## References
- `ai/thoughts/tickets/2026-09-25-pr1-open-home-and-gate-trip-saving.md`
- `ai/thoughts/research/2026-09-25-pr1-open-home-and-gate-trip-saving.md`
- `ai/thoughts/tickets/2026-09-25-pr2-model-named-trips-and-dated-options.md`
- `ai/thoughts/tickets/2026-09-25-pr4-move-trip-start-to-inline-trips-page.md`
- `frontend/src/App.tsx`, `frontend/src/components/ProfileScreen.tsx`, `frontend/src/components/TripCreateModal.tsx`, `frontend/src/components/TripWorkspace.tsx`
- `src/main/java/app/detour/security/SecurityConfiguration.java`, `src/main/java/app/detour/trip/TripController.java`
