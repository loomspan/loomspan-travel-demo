# Core Workflow Accessibility and Presentation Implementation Plan

## Overview
- Ticket: `ai/thoughts/tickets/2026-09-23-p07-t04-complete-accessibility-and-presentation-pass.md`
- Research: `ai/thoughts/research/2026-09-23-p07-t04-complete-accessibility-and-presentation-pass.md`
- Outcome: Registration through booking and cancellation is keyboard operable, clearly announced, legible in responsive and zoomed layouts, and explicit about local flight dates and authoritative USD totals.

## Current State

`App` focuses a global error and the first `h1` when the authentication screen changes (`frontend/src/App.tsx`). `ProfileScreen` switches among Home, Profile, and a mounted Trip workspace without an equivalent focus policy (`frontend/src/components/ProfileScreen.tsx`). `TripWorkspace` replaces its subtree for comparison, booking review, and confirmation without focusing the new heading (`frontend/src/components/TripWorkspace.tsx`). The destructive dialogs already have Tab loops and pending dismissal guards; `AboutDemoTab` is intentionally a non-modal aside. Search and draft views have labeled inputs and several live regions, but status/error and focus behavior need a complete path audit.

Flight search results and snapshot views format clocks without calendar dates. Search responses carry both end-specific IANA zones; snapshot timestamp/zone fields are optional (`frontend/src/components/AirfareSearchSection.tsx`, `frontend/src/components/ItineraryComparisonView.tsx`, `frontend/src/components/BookingReviewView.tsx`, `frontend/src/api/tripsApi.ts`). The comparison desktop table is labeled `role="grid"` although it behaves as a native table. Component totals and budget values already mostly use server tallies or selected snapshot prices, with absence text rather than zero (`frontend/src/components/ItinerarySummaryTally.tsx`, `frontend/src/components/AlternativeCard.tsx`, booking views and history). CSS has visible focus styling and responsive breakpoints but no reduced-motion override (`frontend/src/style.css`).

## Desired End State

- A keyboard user can traverse authentication, Home, Trip creation, all three component searches, readiness, comparison, review, confirmation, cancellation, history, and Profile, with focus moved to the new context or returned to a meaningful initiator after transient UI closes. Error summaries and status messages identify the next action.
- Destructive dialogs remain modal and contained while open. Escape and backdrop dismiss only when no destructive action is pending. About remains non-modal and restores trigger focus when closed. Comparison uses native table semantics on desktop and correctly linked, keyboard-operated tabs on mobile.
- Flight legs show departure and arrival airport, local date, local clock time, and zone separately, including date changes. Missing legacy snapshot timestamps are stated as unavailable instead of formatted as an invalid date or guessed from the trip date. The fixed PDX rule for expiration/cancellation remains unchanged.
- Each selected component, grand total, and budget position retains its USD meaning in builder, comparison, review, confirmation, and history. Missing optional components say they were not selected, never `$0`.
- Color-coded states have words or icons, keyboard focus remains visible, reduced motion is honored, and core content remains operable at narrow widths and 200% zoom.

## Scope

### In scope
- Frontend semantics, announcements, focus transitions, schedule formatting, money presentation, and CSS for the named workflows.
- Focused regression tests and safe frontend build/test verification; rendered browser checks when an appropriate local environment is available.

### Out of scope
- Server pricing, inventory, trip lifecycle, authorization, API response contracts, persisted data, and the PDX expiration/cancellation rule.
- New planning or booking features, external services, or changing the non-modal nature of About established in P07-T03.

## Active Project Guardrails

None recorded in `ai/thoughts/design-lens.md`.

## Impact and Risk Analysis

The biggest risk is moving focus to a hidden or removed element as nested views mount, especially when a modal closes after cancellation and triage immediately opens. Focus transitions should run after the target mounts, favor a stable heading or button in the current view, and avoid refocusing on data-only updates. The existing dialogs duplicate focus code; correct any defect found in those components while preserving pending-action containment. `AboutDemoTab` must remain operable while focus is elsewhere in the page; its Escape handler applies only when focus is within the aside.

Date formatting must use each endpoint's supplied IANA zone, not the browser zone or the fixed PDX lifecycle zone. Optional snapshot fields need an explicit unavailable state. The server remains authoritative for planned/booking totals and budget; frontend changes must not recalculate those values or turn missing selections into zero-priced products. CSS changes should avoid sticky/fixed controls obscuring focused elements at zoom, and should not suppress focus indication when disabling transitions.

## Implementation Approach

Use a shared frontend flight endpoint formatter for the repeated search/comparison/review displays. Format a full date and clock with `Intl.DateTimeFormat` and the endpoint zone; display the IANA zone alongside it. A small reusable display component can express `Departure` and `Arrival` with airport labels, preventing the current arrow-only presentation from losing direction. For optional snapshot fields, show `Schedule unavailable` for the missing endpoint and retain any known airport/zone details; do not synthesize a date. Keep this strictly presentational.

Handle focus at the navigation owners: `ProfileScreen` for Home/Profile/Trip, `TripWorkspace` for workspace/compare/review/confirmation, and individual dialogs for opening/closing. Use focusable headings (`tabIndex={-1}`) only on actual view transitions; preserve focused controls during autosave and asynchronous content refresh. Prefer a native comparison table over a faux grid because cells do not offer spreadsheet-style interaction. Avoid introducing an accessibility library or a new routing model for this pass.

## Phase 1: Schedule and financial meaning

### Changes
- [x] `frontend/src/components/FlightSchedule.tsx` (new) — format each available ISO timestamp with its endpoint IANA zone into full local month/day/year and clock text, with explicit `Departure`/`Arrival`, airport code, and zone labels; render an unavailable message for absent/invalid snapshot fields without falling back to browser local time.
- [x] `frontend/src/components/AirfareSearchSection.tsx`, `frontend/src/components/ItineraryComparisonView.tsx`, `frontend/src/components/BookingReviewView.tsx` — replace duplicated time-only outbound/return text with the shared endpoint display for all four legs and both responsive comparison views.
- [x] `frontend/src/components/ItinerarySummaryTally.tsx`, `frontend/src/components/AlternativeCard.tsx`, `frontend/src/components/BookingReviewView.tsx`, `frontend/src/components/BookingConfirmationView.tsx`, `frontend/src/components/BookingHistorySection.tsx` — audit each amount against selected component and server tally; make currency and missing-component labels consistent and explicit, and preserve overage/remaining text without recomputing server values.
- [x] `frontend/src/components/FlightSchedule.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/components/BookingConfirmationView.test.tsx` — cover SFO/MUC/MEX local dates, overnight/date-changing legs, missing snapshots, and absence versus zero-price semantics.

### Automated verification
- [x] `npm test -- --run src/components/FlightSchedule.test.tsx src/ItineraryComparisonAndBookingReview.test.tsx src/components/BookingConfirmationView.test.tsx` from `frontend` — endpoint dates/zones and money semantics pass.
- [x] `npm run build` from `frontend` — TypeScript and Vite build pass.

### Optional developer checks
- [ ] Read a date-changing flight aloud in a browser screen reader and verify both local dates and zones are understandable.

## Phase 2: Navigation, forms, announcements, and modal interaction

### Changes
- [x] `frontend/src/App.tsx`, `frontend/src/components/AuthScreen.tsx`, `frontend/src/components/ProfileScreen.tsx` — complete registration/login and Home/Profile/Trip transition focus; make form errors link to their fields and summaries describe correction/retry; ensure only true view changes move focus.
- [x] `frontend/src/components/TripWorkspace.tsx` — focus comparison, review, confirmation, and returned workspace headings after view transitions; retain focus on save/retry actions during data updates; announce autosave, readiness, booking, cancellation, and recovery outcomes once with the next useful action. Correct the cancellation-to-triage handoff so focus moves into the new modal and later returns to a surviving workspace control.
- [x] `frontend/src/components/AirfareSearchSection.tsx`, `frontend/src/components/StaySearchSection.tsx`, `frontend/src/components/RentalSearchSection.tsx`, `frontend/src/components/DraftReadinessBanner.tsx`, `frontend/src/components/BookingReviewView.tsx`, `frontend/src/components/BookingHistorySection.tsx` — audit headings, label/description/error associations, empty/loading/retry messages, jump targets, and meaningful action names. Fix concrete gaps found along each path without changing search or booking rules.
- [x] `frontend/src/components/ItineraryComparisonView.tsx` — remove `role="grid"` and redundant row roles from the native desktop table; retain row/column headers. Verify mobile tab `aria-controls` always points to an existing panel as selection changes, roving Tab index and arrow/Home/End keys work, and selection/readiness state is spoken clearly.
- [x] `frontend/src/components/AboutDemoTab.tsx` and destructive dialog components (`CancelBookingModal.tsx`, `CancelTripModal.tsx`, `ConfirmRemoveModal.tsx`, `ConfirmDeleteModal.tsx`, `BudgetOverageModal.tsx`, `PostCancellationTriageModal.tsx`, `TripCreateModal.tsx`, `TripRevisionModal.tsx`) — audit initial focus, Tab containment for modals, Escape/backdrop behavior, pending dismissal, error focus, and restoration to connected initiator or stable fallback; apply focused corrections where the representative path reveals defects.
- [x] `frontend/src/App.test.tsx`, `frontend/src/ProgressiveTripBuilder.test.tsx`, `frontend/src/DraftPromotion.test.tsx`, `frontend/src/ItineraryComparisonAndBookingReview.test.tsx`, `frontend/src/FeeFreeCancellationAndTriage.test.tsx`, `frontend/src/components/AboutDemoTab.test.tsx` — add deterministic keyboard/focus, live-status, error, and restoration coverage for the transitions and dialogs above.

### Automated verification
- [x] `npm test` from `frontend` — all frontend workflow tests pass.
- [x] `npm run build` from `frontend` — all frontend changes typecheck and bundle.

### Optional developer checks
- [ ] Traverse registration through booking and Cancel Booking with keyboard and a screen reader in a rendered desktop and narrow browser viewport; note any environment-specific AT behavior.

## Phase 3: Responsive layout, focus visibility, and motion

### Changes
- [x] `frontend/src/style.css` — add `prefers-reduced-motion: reduce` treatment for transitions and smooth scroll, maintain distinct `:focus-visible` outlines, and resolve any 200% zoom/narrow viewport overflow or fixed About/status overlap found in the core screens. Preserve text-bearing badges, warnings, budget labels, and booking states.
- [x] `frontend/src/components/TripWorkspace.tsx` — switch readiness/rental jump scrolling to instant when reduced motion is requested; keep the focused destination visible.
- [x] `frontend/src/VisualSystem.test.tsx` and relevant workflow tests — assert text/icon state cues and reduced-motion rule/jump behavior where DOM or stylesheet tests can prove them.

### Automated verification
- [x] `npm test` from `frontend` — responsive markup and motion regressions pass.
- [x] `npm run build` from `frontend` — production bundle passes.

### Optional developer checks
- [ ] In a local browser at 200% zoom and a narrow viewport, inspect each core screen for clipping, hidden focused controls, horizontal page overflow, and readable contrast; repeat with reduced motion enabled. Record viewport/browser and any residual observations.

## Test Strategy

Step 3 specifies the red tests, fixtures, and commands. Start with a deterministic date-changing flight schedule test, then cover view focus and modal handoff, comparison semantics, status/error paths, and money meaning. Use Vitest/Testing Library for component behavior and the repository's safe frontend build for type and bundle checks. Browser rendering and assistive-technology observations supplement rather than replace executable tests; do not claim that jsdom proves contrast or zoom.

## Acceptance-Criteria Traceability

| Acceptance criterion | Planned code evidence | Planned test evidence |
| --- | --- | --- |
| Keyboard path through booking and cancellation on desktop/mobile | `App`, `ProfileScreen`, `TripWorkspace`, search sections, dialogs, responsive CSS | Workflow keyboard/focus tests plus rendered desktop/narrow observation |
| Errors, autosave, warnings, booking/cancellation outcomes announced with next action | Global/form summaries, workspace status/readiness, booking and triage views | Live-region and failure/recovery tests across App, builder, promotion, review, cancellation |
| Modal/About/comparison focus, Escape, restoration, labels | Dialog components, `AboutDemoTab`, native table/mobile tabs | Dialog keyboard and return-focus tests; comparison semantics/tab tests |
| Zoom, reduced motion, and non-color meaning | `style.css`, jump scroll behavior, text-bearing badges | DOM/style checks and rendered 200%/reduced-motion observation |
| Local date/time/zone at both ends for SFO/MUC/MEX and date changes | Shared flight schedule display in search/comparison/review | Formatter and rendered-view tests with destination and overnight fixtures |
| Price/missing-component meaning throughout | Tally and component views, comparison, review, confirmation, history | Cross-view money and absence tests using server tally fixtures |

## Implementation verification notes

The shared schedule display now uses endpoint IANA zones in search, comparison, and booking review; optional snapshot fields render an unavailable schedule. Native comparison table semantics, linked mobile tabs, view and dialog focus, actionable form errors, reduced-motion scrolling, and unknown-tally labels are covered by frontend tests. The complete frontend suite and build passed after these changes (see Step 4 report for commands and counts). The local rendered keyboard, assistive-technology, contrast, and 200% zoom checks remain optional developer observations and were not performed in this implementation context.

## Risks and Rollback/Recovery

If endpoint timestamps or zones are absent in older snapshots, show an unavailable schedule instead of guessing. If a live browser or AT environment is unavailable, report rendered checks as not performed and retain deterministic frontend evidence; do not claim visual or AT outcomes from jsdom. Revert frontend presentation changes as a unit if they obscure focus or alter price meaning; no schema or server rollback is expected. A discovered need to change persisted/API, authorization, lifecycle, or pricing contracts requires pipeline reassessment before implementation.

## References

- `ai/thoughts/tickets/2026-09-23-p07-t04-complete-accessibility-and-presentation-pass.md`
- `ai/thoughts/research/2026-09-23-p07-t04-complete-accessibility-and-presentation-pass.md`
- `frontend/src/App.tsx`, `frontend/src/components/ProfileScreen.tsx`, `frontend/src/components/TripWorkspace.tsx`
- `frontend/src/components/AirfareSearchSection.tsx`, `frontend/src/components/ItineraryComparisonView.tsx`, `frontend/src/components/BookingReviewView.tsx`
- `frontend/src/components/BookingConfirmationView.tsx`, `frontend/src/components/BookingHistorySection.tsx`, `frontend/src/style.css`
