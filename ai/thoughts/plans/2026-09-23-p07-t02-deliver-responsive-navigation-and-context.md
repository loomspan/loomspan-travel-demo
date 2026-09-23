# Responsive Navigation and Trip Context Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-23-p07-t02-deliver-responsive-navigation-and-context.md`
- Research: `ai/thoughts/research/2026-09-23-p07-t02-deliver-responsive-navigation-and-context.md`
- Outcome: Authenticated Home, Profile, and active Trip navigation with preserved Draft state, recoverable saves, and usable narrow-screen presentation.

## Current State

`App` renders one `ProfileScreen` after `/api/profile`. `ProfileScreen` uses `overview` and `workspace` modes, clears `activeTrip` when leaving the workspace, and unmounts `TripWorkspace` (`frontend/src/App.tsx`, `frontend/src/components/ProfileScreen.tsx`). `TripWorkspace` owns the fetched Trip, first active Draft, component modes, search state, autosave inputs, and comparison/booking subviews (`frontend/src/components/TripWorkspace.tsx`). Comparison already filters Planned alternatives from the loaded Trip, limits selection to three, and offers a desktop matrix and mobile selected panel (`frontend/src/components/ItineraryComparisonView.tsx`). Existing CSS has mobile stacking but does not keep the comparison selector visible while scrolling (`frontend/src/style.css`).

## Desired End State

Every authenticated primary view presents Home, Profile, active Trip/itinerary when open, and Log out with distinct names. Home offers Plan Trip, Airfare, and Stay; Profile contains trip listing and account management. Opening a Trip loads its owner-scoped server response; moving between Home, Profile, workspace, comparison, and booking subviews keeps that Trip and Draft context. Saved selections and tally come from the latest server Trip response on refresh and safe return. Pending or failed local changes remain visibly distinct from saved data, with retry/reload controls. At mobile widths, nested content and actions fit without clipping; comparison retains at most three Planned alternatives in desktop columns and mobile stacked detail with a visible selector. Absent components remain distinct from actual zero-cost selections.

## Scope
### In scope
- Authenticated navigation, Home/Profile partition, Trip opening/return, and loading/error/empty states.
- Trip context and save lifecycle in the existing SPA, including explicit retry and authoritative refresh.
- Responsive CSS/markup for profile hierarchy, alternatives, search, booking, history, and comparison.
- Focused frontend regression coverage and build verification.

### Out of scope
- New browser routes, public catalog pages, backend mutations or ownership changes, and new Trip lifecycle rules.
- P07-T01 shared visual system, P07-T03 disclosure copy, and P07-T04 final accessibility audit beyond interaction correctness needed here.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

The main risk is losing a debounced or failed edit when navigation unmounts the workspace or when a stale `initialTrip` prop overwrites local form state. Keep the open workspace mounted across Home/Profile switching, report save status in navigation, and do not refresh over dirty, saving, error, or conflict state. Explicit Retry save reuses current inputs and versions; conflict recovery uses the existing Reload from server path with clear replacement wording. A failed component selection must not claim the new selection persisted; retained server selections and tally remain authoritative. Trip switching, deletion, and logout should discard the old workspace only through explicit actions after pending work has settled or with clear unsaved-change handling. Profile reads and Trip detail remain existing authenticated owner-scoped APIs. No local storage or new route contract is needed.

Comparison must not admit Drafts, Booked alternatives, another Trip, or a fourth Planned item. At small widths, long IDs, prices, and option details can create page overflow; responsive wrapping and min-width rules should preserve every action and field. CSS stickiness must be attached to the mobile selector within a container that permits vertical sticking. Existing jsdom tests prove interaction, while actual width/scroll fit requires an optional browser observation.

## Implementation notes

The initial authenticated screen remains Profile to preserve the existing account and trip-list entry journey. Home now carries all three creation actions and Profile retains its existing creation shortcuts, so neither entry path breaks. The single keyed workspace stays mounted while Home or Profile is shown. Existing comparison markup and Planned-only selection enforcement already met the planned interaction contract, so Phase 3 changed CSS rather than that component.

The first navigation regression was added and passed after the implementation; a pre-fix red run was not captured. The full mocked frontend suite and build are the automated gates. Browser geometry and sticky-scroll checks remain optional configured-environment observations.

## Implementation Approach

Keep authenticated navigation in `ProfileScreen` above its Home, Profile, and Trip panels so it appears even when `TripWorkspace` renders comparison or booking review. Use local view state, not URL routes. Home owns the three creation entry points and an active Trip return; Profile owns trip list and password form. Retain a single keyed `TripWorkspace` instance for the open Trip while it is visually hidden on Home/Profile, and add a narrow state/refresh interface so a return can fetch authoritative Trip data only when no unsaved local state would be overwritten. Keep active Trip state after navigation; clear it only when the Trip is deleted, a different Trip opens, or authentication ends. Consolidate duplicate workspace logout/back controls with the shared nav while retaining local comparison/booking back actions.

Use the current `tripsApi.getTrip` and mutation responses for server state. Expose explicit save retry, loading/error retry for opening a Trip, and purposeful empty/loading/retry treatment for booking history and search sections. Continue existing two-or-three comparison launch behavior: Phase 5 says compare multiple alternatives, and a one-item comparison would not compare alternatives. Preserve the current server/API contract.

## Phase 1: Authenticated navigation and Trip continuity

### Changes
- [x] `frontend/src/components/ProfileScreen.tsx` — replace overview/workspace toggle with Home/Profile/Trip selection and shared named nav; keep `activeTrip` and keyed `TripWorkspace` mounted while Home/Profile is visible; move three creation actions to Home, trip list/password to Profile; add active Trip return, opening spinner/error with Retry, and explicit handling for switching away from unsaved edits.
- [ ] `frontend/src/components/TripWorkspace.tsx` — remove duplicate global logout/back affordances in favor of shared nav, expose current save/dirty state and safe refresh/retry operations to the parent, and retain local component/search/comparison state when hidden and shown. Guard `initialTrip` synchronization so background profile refresh does not overwrite newer or unsaved workspace state.
- [ ] `frontend/src/components/EmptyProfileState.tsx` and `frontend/src/components/TripListSection.tsx` — adapt empty/profile and creation-entry presentation to the separated Home/Profile layout without adding implicit components to a Draft.
- [ ] `frontend/src/App.test.tsx` and `frontend/src/ProgressiveTripBuilder.test.tsx` — cover Home/Profile/Trip/logout from each primary state, all three first-trip entries, preservation of Trip/Draft state through navigation, and refreshed saved tally.

### Automated verification
- [ ] `npm test -- src/App.test.tsx src/ProgressiveTripBuilder.test.tsx` from `frontend` — navigation and Draft continuity pass, including no extra creation/selection mutation caused by navigation.
- [x] `npm run build` from `frontend` — TypeScript and Vite build complete.

### Optional developer checks
- [ ] In a configured local browser, navigate across Home/Profile/Trip with a slow network and confirm loading and active-Trip return are understandable.

## Phase 2: Save, retry, and loading state correctness

### Changes
- [x] `frontend/src/components/TripWorkspace.tsx` — show pending, saved, failed, and conflict states consistently; add Retry save for retryable autosave failures, use the current edited values, prevent accidental success language for failed writes, and make server reload an explicit discard action for conflicts. Ensure navigation during debounce/in-flight save does not erase inputs or late results.
- [x] `frontend/src/components/BookingHistorySection.tsx` — preserve loading/error state with a visible retry action and meaningful empty result instead of returning `null`; keep prior history visible when a refresh fails if applicable.
- [x] `frontend/src/components/AirfareSearchSection.tsx`, `frontend/src/components/StaySearchSection.tsx`, and `frontend/src/components/RentalSearchSection.tsx` — retain clear loading/empty/error states and add or verify direct retry for failed searches without changing selection contracts.
- [x] `frontend/src/App.test.tsx`, `frontend/src/ProgressiveTripBuilder.test.tsx`, and `frontend/src/FeeFreeCancellationAndTriage.test.tsx` — cover rejected then retried saves, conflict reload, search retry, and history failure/retry with authoritative resulting data.

### Automated verification
- [x] `npm test -- src/App.test.tsx src/ProgressiveTripBuilder.test.tsx src/FeeFreeCancellationAndTriage.test.tsx` from `frontend` — retry and state tests pass.

### Optional developer checks
- [ ] Use local network throttling to observe saving, error, retry, and return states; do not run against production services.

## Phase 3: Responsive content and comparison

### Changes
- [ ] `frontend/src/style.css` — make shared nav, nested profile/trip cards, component option details/actions, booking review, cancellation history, and comparison responsive at 320px and common mobile widths; wrap long content and keep required actions visible; give mobile comparison selector a sticky top position and suitable background/z-index while retaining desktop table columns.
- [ ] `frontend/src/components/ItineraryComparisonView.tsx` — keep mobile selector and stacked panel association usable as alternatives change; preserve explicit absent-component labels and zero-cost numbers; retain per-Trip Planned-only selection and maximum-three handling in `TripWorkspace`.
- [ ] `frontend/src/ItineraryComparisonAndBookingReview.test.tsx` and `frontend/src/DraftPromotion.test.tsx` — assert two/three Planned choices, fourth-choice rejection, mobile selector interaction, absent versus zero-cost display, and required booking actions in mobile structure.

### Automated verification
- [ ] `npm test -- src/ItineraryComparisonAndBookingReview.test.tsx src/DraftPromotion.test.tsx` from `frontend` — comparison/booking interactions pass.
- [x] `npm test` and `npm run build` from `frontend` — complete safe frontend regression/build gate passes.

### Optional developer checks
- [ ] Inspect authenticated Home, Profile, Trip, search results, booking review/history, and comparison at 320px, 375px, and desktop widths; scroll the mobile comparison and verify the selector remains reachable and no required content or action is clipped.

## Test Strategy

Use existing Vitest/Testing Library API mocks for navigation, returned Trip data, save failure/retry, and comparison interaction. Start with a red test for returning from Home/Profile to the same active Trip without losing Draft selection and saved tally. Add cases for delayed save and explicit failure to distinguish local inputs from authoritative server data. Keep owner isolation and immutable Planned/Booked rules covered by existing backend suites because this plan changes no backend endpoint or data contract. Visual fit and CSS stickiness require a local browser observation in addition to automated semantic/interaction tests.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Named Home, Profile, active Trip, logout on every authenticated primary screen | Shared nav in `ProfileScreen` surrounding every view | `App.test.tsx` traversal from Home/Profile/workspace/comparison/booking and logout |
| Airfare/stay/rental navigation preserves Draft and authoritative saved tally | Mounted `TripWorkspace`, guarded refresh, existing owner-scoped `getTrip` and mutation results | `ProgressiveTripBuilder.test.tsx` selection, switch, return, refresh, and no implicit component mutation |
| First-time starts, empty/loading, failed save retry | Home entries, opening state, `TripWorkspace` retry, history/search states | `App.test.tsx`, `ProgressiveTripBuilder.test.tsx`, history/search tests |
| Profile/Trip/core actions fit narrow widths | Responsive rules in `style.css` and preserved markup | Automated action presence; optional 320px/375px browser inspection |
| Up to three Planned comparisons, desktop columns/mobile stacked selector, missing versus zero | Existing Planned filter/selection cap, `ItineraryComparisonView`, sticky mobile CSS | `ItineraryComparisonAndBookingReview.test.tsx`; optional scroll/width inspection |

## Risks and Rollback/Recovery

If local state and server state diverge, keep the visible unsaved/error state and offer Retry or explicit Reload; never silently adopt a stale response. If a Trip is unavailable, keep Home/Profile navigable and allow retry or another Trip. The change is frontend-only; rollback is reverting the frontend UI/state/CSS changes without data migration. Existing backend ownership, validation, and snapshots remain untouched.

## References

- `ai/thoughts/tickets/2026-09-23-p07-t02-deliver-responsive-navigation-and-context.md`
- `ai/thoughts/research/2026-09-23-p07-t02-deliver-responsive-navigation-and-context.md`
- `ai/thoughts/phases/phase-5-planning-budget-and-comparison.md`
- `frontend/src/App.tsx`, `frontend/src/components/ProfileScreen.tsx`, `frontend/src/components/TripWorkspace.tsx`, `frontend/src/components/ItineraryComparisonView.tsx`, `frontend/src/style.css`
