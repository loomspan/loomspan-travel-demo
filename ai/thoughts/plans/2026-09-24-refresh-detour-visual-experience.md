# DeTour Visual Experience Implementation Plan

## Overview

- Ticket: `ai/thoughts/tickets/2026-09-24-refresh-detour-visual-experience.md`
- Research: `ai/thoughts/research/2026-09-24-refresh-detour-visual-experience.md`
- Outcome: A cohesive, photo-led DeTour experience from authentication and Home through trip planning, comparison, booking, cancellation, and dialogs, while preserving existing workflows.

## Current State

`ProfileScreen` owns Home, Profile, workspace navigation, and the three `startCreateTrip` modes. Home currently reuses `EmptyProfileState`, which also appears on an empty Profile; neither has destination imagery (`frontend/src/components/ProfileScreen.tsx`, `frontend/src/components/EmptyProfileState.tsx`). The app-wide teal/Georgia system and most component styling live in `frontend/src/style.css`, with later overrides of earlier rules. Native selects and checkboxes are used in `TripCreateModal`, `TripWorkspace`, search sections, `AlternativeCard`, `BudgetOverageModal`, and `TripRevisionModal`. Existing React Testing Library tests cover entry flows, focus, booking, cancellation, and responsive comparison semantics, but not destination content or rendered image/layout behavior. The checkout had no production changes at planning start; the research document is untracked.

The accepted mock-up was inspected. Its useful direction is a dark teal navigation surface, airy pale-teal hero, restrained action cards, three image-led destination cards, and polished native controls. Its control preview is reference material, not a new product feature.

## Desired End State

Home has a distinct travel hero, three clear entry actions, and a labeled Featured destinations section with San Francisco, Munich, and Mexico City images. Featured cards are informational only; they do not trigger trip creation, alter form state, or navigate. `EmptyProfileState` continues to serve Profile onboarding without duplicating the Featured section. Public auth, profile, workspace, search/selection, comparison, booking, confirmation, cancellation, and dialogs share the same palette, typography, spacing, borders, button language, and carefully chosen icons. Native selects and checkboxes preserve labels, browser behavior, validation, disabled states, and keyboard operation. At 320px and desktop widths, imagery has reserved aspect ratio, controls and cards fit, and no page horizontal overflow appears. Server-owned prices, eligibility, trip state, autosave, booking, and cancellation are unchanged.

## Scope

### In scope

- Home presentation and three owned destination images.
- Shared visual tokens and targeted component styling across public and authenticated surfaces.
- Accessible decorative icons for existing navigation and actions.
- Native select/checkbox visual states, including hover, focus, checked, disabled, and invalid states.
- Tests and rendered responsive/keyboard checks for affected presentation and preserved flows.

### Out of scope

- New destination navigation or changes to `startCreateTrip` modes.
- Changes to API requests, persisted trip data, catalog/pricing rules, authentication, or booking eligibility.
- A literal copy of the mock-up, its control-preview section, Booking.com assets, branding, layout, or copy.
- Replacing native form controls with custom widgets.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

- **Workflow regression:** `ProfileScreen` keeps the workspace mounted while another view is shown; visual refactoring must retain this and its focus/unsaved-change behavior. Keep event handlers and form state in their existing owners.
- **Asset provenance and reliability:** No destination assets exist in `frontend`. Create original project assets or use images with documented redistribution rights; record source/provenance alongside assets. Bundle optimized files locally under `frontend/public/images/destinations/`, so rendering never depends on a remote host. Avoid embedding the reference mock-up or importing Booking.com imagery.
- **Accessibility:** Text labels remain present with icons hidden from assistive technology. Real image alternatives name the destination/scene without repeating adjacent card copy excessively. Keep `h1`/section heading order, existing `aria-current`, dialog focus behavior, field labels, `aria-invalid`, and error descriptions.
- **Responsive layout:** Fixed image dimensions/aspect ratio and `object-fit` prevent layout shift; `minmax(0, 1fr)` grids and wrapping constrain long trip labels, cards, and modal controls at 320px. Test actual rendered widths because jsdom cannot establish layout.
- **CSS cascade:** Earlier and later rules coexist in `style.css`; update or consolidate directly relevant selectors rather than appending conflicting global overrides. Avoid changing semantic warning, danger, and success cues to decorative color alone.

## Implementation Approach

Keep Home data static because the supported destinations are fixed in this demo and the cards are only inspiration. Add a small `FeaturedDestinations` presentation component with three explicit entries and static image paths; no click handlers or API calls. Keep `EmptyProfileState` as a shared action component only if its Profile wording/layout remains appropriate; render Home-specific hero and actions in `ProfileScreen` or a dedicated `HomeView` using the existing callbacks. Preserve accessible names `Plan Trip`, `Airfare`, and `Stay` so existing actions and tests remain stable.

Evolve the existing CSS token layer and component selectors. Use lightweight inline SVG icons or a local SVG symbol component for existing actions, with `aria-hidden="true"` and text labels. This gives consistent stroke/size without adding a dependency or external icon CDN. Use native select appearance with a decorative chevron only if keyboard, high-contrast, and disabled behavior remain clear; native checkboxes can use `accent-color` plus explicit sizing/spacing and visible focus, avoiding fragile pseudo-control replacements.

## Phase 1: Home and owned imagery

### Changes

- [x] `frontend/src/components/ProfileScreen.tsx` — retain `startCreateTrip` and navigation handlers; replace only Home's visual markup with the hero/action composition and Featured destinations section. Keep `home-heading` focus target and active-trip return action.
- [x] `frontend/src/components/FeaturedDestinations.tsx` — add three noninteractive destination entries with image path, label, optional location subtitle, meaningful image alt text, and a section heading. Do not add buttons or links to these cards.
- [x] `frontend/src/components/EmptyProfileState.tsx` — retain Profile onboarding callbacks; adjust shared action markup only as needed to apply the new card styling and existing accessible names.
- [x] `frontend/public/images/destinations/` and a nearby provenance note — add three original or appropriately licensed, optimized images with explicit dimensions/aspect ratio. Confirm each file is bundled and loads locally after build.
- [x] `frontend/src/style.css` — add Home hero, action, and destination grid styles that match the accepted direction at desktop and collapse cleanly by 320px. Reserve image space before load and provide a legible label surface over or below photos.
- [x] `frontend/src/ProgressiveTripBuilder.test.tsx` — assert three named Featured entries and their image semantics while retaining the existing three entry-action flow test and no-write assertion.

### Automated verification

- [x] `npm test -- src/ProgressiveTripBuilder.test.tsx` from `frontend` — the destination/entry regression passes; card rendering causes no write merely by displaying Home.
- [x] `npm run build` from `frontend` — TypeScript and Vite succeed and bundle local assets.

### Optional developer checks

- [x] Compare Home's visual direction with the accepted mock-up; photography and copy need not match literally.

## Phase 2: Shared visual system, controls, and icons

### Changes

- [x] `frontend/src/style.css` — refine tokens and shared `.shell`, `.card`, `.modal`, navigation, headings, buttons, status surfaces, grids, and spacing so auth, Profile, builder, search results, comparison, review, confirmation, cancellation, and dialogs use coherent light surfaces and teal accents. Preserve distinct status text and danger treatment.
- [x] `frontend/src/style.css` — specify native `select` and `input[type="checkbox"]` dimensions, border/radius, hover, checked, disabled, `:focus-visible`, and `[aria-invalid="true"]` treatments. Keep minimum touch area through labels/layout; avoid hiding native controls. Verify high-contrast/forced-colors behavior rather than relying on a background image as the sole select affordance.
- [x] `frontend/src/components/ProfileScreen.tsx`, `frontend/src/components/EmptyProfileState.tsx`, and selected existing action/navigation components such as `TripWorkspace.tsx`, `AirfareSearchSection.tsx`, `StaySearchSection.tsx`, `BookingReviewView.tsx`, and cancellation dialogs — add a small set of purposeful, decorative icons to text-bearing actions/headings where they improve recognition; keep existing button text and semantic attributes. Centralize repeated icon SVGs in `frontend/src/components/ActionIcon.tsx` if multiple components need them.
- [x] `frontend/src/components/TripCreateModal.tsx`, `frontend/src/components/TripRevisionModal.tsx`, `frontend/src/components/AlternativeCard.tsx`, and `frontend/src/components/BudgetOverageModal.tsx` — touch markup only where a control needs an existing label/description wrapper for consistent state styling; preserve the native input and handlers.
- [x] `frontend/src/VisualSystem.test.tsx` and affected UI tests — assert the durable control semantics and styling hooks (focus/invalid/checked/disabled selectors and labels), decorative icon treatment, and unchanged navigation names and dialog semantics.

### Automated verification

- [x] `npm test -- src/VisualSystem.test.tsx src/App.test.tsx src/ItineraryComparisonAndBookingReview.test.tsx src/FeeFreeCancellationAndTriage.test.tsx` from `frontend` — changed control/navigation semantics and representative flows pass.
- [x] `npm run build` from `frontend` — no TS/CSS/bundling errors.

### Optional developer checks

- [ ] Inspect legibility and visual consistency of auth, Profile, trip builder, search, comparison, booking, cancellation, and dialogs in a rendered browser.

## Phase 3: Cross-flow verification and responsive finishing

### Changes

- [x] `frontend/src/style.css` and affected components above — correct issues found by rendered checks at 320px, tablet, and desktop, including image crop, text wrapping, focus clipping, dialog scrolling, and horizontal overflow.
- [x] `frontend/src/ProgressiveTripBuilder.test.tsx`, `frontend/src/App.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, and `frontend/src/FeeFreeCancellationAndTriage.test.tsx` — update only expectations affected by legitimate presentation changes; retain behavioral assertions for creation, autosave, booking, and cancellation.

### Automated verification

- [x] `npm test` from `frontend` — complete frontend regression suite passes.
- [x] `npm run build` from `frontend` — production build passes and emitted destination image URLs resolve.
- [x] Rendered browser check at 320px, 768px, and desktop — no document horizontal overflow; Home images load with reserved size; forms/dialogs are operable by keyboard and visible focus. Use available local browser tooling and a safe local demo/test environment; record exact commands/steps and results. If a browser or suitable data setup is unavailable, report the unverified acceptance portion explicitly rather than claiming jsdom coverage.

### Optional developer checks

- [ ] Human visual comparison against the accepted concept and assistive-technology listening pass on representative flows, if available; report as observations, not automated passes.

## Test Strategy

Start with a failing Home destination test, then update unit/integration tests at existing React Testing Library boundaries. Keep API mocks and existing tests' no-write assertions. Test DOM roles, names, alt/decorative semantics, labels, selected/disabled behavior, and dialog focus; CSS source assertions should verify only key shared state rules, because jsdom does not compute usable layout or contrast. Run targeted suites during implementation, then the full frontend suite and build. Use a real rendered browser for image loading, 320px/768px/desktop overflow, focus visibility, control usability, and representative auth→Home→create→search→compare→book→cancel states. The testing plan supplies exact coverage and exit criteria.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Three Featured destinations and preserved entry actions | `FeaturedDestinations`, Home composition in `ProfileScreen` | Home rendering/alt tests and existing three-mode/no-write test in `ProgressiveTripBuilder.test.tsx` |
| Consistent direction across public, planning, booking/cancellation, dialogs | Shared tokens/selectors in `style.css`, targeted text-bearing icons | `VisualSystem.test.tsx`, relevant flow suites, rendered route/state inspection |
| Polished native dropdowns/checkboxes with full state/accessibility | `style.css` state selectors; existing labeled native controls retained | `VisualSystem.test.tsx`, affected interaction tests, rendered keyboard/forced-colors check |
| Accessible stable imagery/icons without overflow | Local destination assets with provenance, fixed aspect ratio, decorative SVG treatment | Image DOM/asset checks, build, rendered image/width/focus inspection |
| Unchanged trip/booking flows with responsive/accessibility coverage | Existing handlers and API modules retained | Full `npm test`, build, rendered representative flow at narrow and desktop widths |

## Implementation verification notes (2026-09-24)

- The initial browser view is Home. Older `App` and `ProgressiveTripBuilder` tests expected Profile immediately on `/profile`; their fixtures now navigate there explicitly and replay the already loaded profile for that navigation so queued trip responses retain their intended meaning. Production navigation behavior was not changed.
- `npm test` passed 136 tests in 13 files; `npm run build` passed and copied all three local WebP files into `frontend/dist/images/destinations/`. The pre-change Featured destinations test failed on the missing section as planned.
- Local Vite (`npm run dev -- --port 4173`) and headless installed Chrome through Playwright checked public, Home, and Profile at 320, 768, and 1440px. Each had `documentElement.scrollWidth === clientWidth`. The Home images decoded at all widths with 3:2 reserved boxes. The create dialog opened, kept a native labeled destination `SELECT`, responded to ArrowDown, showed date validation, and closed with Escape; focus outlines were visible. Forced-colors emulation retained the native select. The temporary verification script and screenshot were removed after inspection. No live API or booking calls were used; `/api/profile` was mocked locally.
- A human screen-reader listening pass and a configured-data rendered walkthrough of workspace booking/cancellation states were not available in this step. Existing React integration tests cover those workflows, but they do not prove their rendered layout. These are optional developer checks for Step 5 to carry forward.

## Risks and Rollback/Recovery

If asset creation or licensing cannot be established, use newly generated original imagery with documented provenance; do not ship reference imagery. If a custom-looking control treatment weakens native affordances or forced-colors use, simplify to native appearance plus border, focus, and `accent-color`. Keep implementation changes in frontend presentation so rollback can remove the new assets/component and CSS deltas without data migration or server changes. Preserve prior functional tests as a rollback signal.

## References

- `ai/thoughts/tickets/2026-09-24-refresh-detour-visual-experience.md`
- `ai/thoughts/research/2026-09-24-refresh-detour-visual-experience.md`
- `ai/thoughts/design-lens.md`
- `frontend/src/components/ProfileScreen.tsx`, `EmptyProfileState.tsx`, `TripCreateModal.tsx`, `TripWorkspace.tsx`
- `frontend/src/style.css`, `frontend/src/ProgressiveTripBuilder.test.tsx`, `frontend/src/VisualSystem.test.tsx`
