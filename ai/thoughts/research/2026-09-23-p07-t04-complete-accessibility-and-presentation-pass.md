---
date: 2026-09-23
repository: loomspan-travel-demo
branch: main
commit: cf4f61989fb2e92550427d2cbebfa6a3474540ac
ticket: ai/thoughts/tickets/2026-09-23-p07-t04-complete-accessibility-and-presentation-pass.md
tags: [accessibility, frontend, booking, timezone, presentation]
---

# Core Workflow Accessibility and Presentation Research

## Research Question

How do the current registration, planning, comparison, booking, cancellation, and history screens expose keyboard interaction, announcements, schedule dates and zones, and money semantics? What executable coverage exists for those behaviors?

## Summary

The application is a React single-page frontend backed by Spring trip, catalog, identity, and booking APIs. `App` switches between public authentication and authenticated `ProfileScreen`; the latter owns Home/Profile/Trip navigation, while `TripWorkspace` owns drafts, component searches, promotion, comparison, booking, cancellation, and history. Server responses carry explicit flight departure/arrival zones and authoritative itinerary tallies. Current presentation uses several live regions, labeled forms, and focus-handling dialogs, but time displays in search, comparison, and booking review show time and zone without the local calendar date. The test suite exercises many individual flows in jsdom; rendered 200% zoom, contrast, and complete keyboard-only journeys are not evidenced by those tests.

## Repository State

- Investigated 2026-09-23 at 14:43 PDT. Branch `main`, commit `cf4f61989fb2e92550427d2cbebfa6a3474540ac`. `git status --short` was empty at research start. The branch is one commit ahead of `origin/main`; the prior triage identified that commit as deletion of an unrelated earlier review document.
- No `AGENTS.md` was found by `rg --files -g AGENTS.md`. This step made no production changes and ran no tests.

## Current Behavior and Data Flow

1. `App` loads the server profile, then renders `AuthScreen` or `ProfileScreen`. Its top-level error summary has `role="alert"` and receives focus; status messages use `StatusRegion`. A screen change focuses the first `h1` when there is no error (`frontend/src/App.tsx:33-53`, `frontend/src/App.tsx:98-112`). `AuthScreen` has login/register mode buttons, a first-level heading, labeled fields, and per-field errors (`frontend/src/components/AuthScreen.tsx:40-81`).
2. `ProfileScreen` holds authenticated Home, Profile, and active Trip view state. Its navigation uses `aria-current`, and Home exposes Plan Trip, Airfare, and Stay entries. Opening a Trip fetches `tripsApi.getTrip`, while profile refresh remains server-backed (`frontend/src/components/ProfileScreen.tsx:28-57`, `frontend/src/components/ProfileScreen.tsx:104-145`, `frontend/src/components/ProfileScreen.tsx:250-324`). Trip creation is a labeled modal with initial focus, a Tab loop, Escape handling, and inline validation (`frontend/src/components/TripCreateModal.tsx:43-80`, `frontend/src/components/TripCreateModal.tsx:140-267`).
3. `TripWorkspace` retains the active draft, selection modes, and local shared-details form. Changes are debounced for 600 ms and sent through the trip API; conflict and failure states remain visible with reload actions (`frontend/src/components/TripWorkspace.tsx:199-258`, `frontend/src/components/TripWorkspace.tsx:390-555`, `frontend/src/components/TripWorkspace.tsx:1314-1344`). Flight, stay, and rental slots reveal searches as their mode changes. Each search has labeled controls, loading status, errors, and retry actions (`frontend/src/components/AirfareSearchSection.tsx:45-122`, `frontend/src/components/StaySearchSection.tsx:28-105`, `frontend/src/components/RentalSearchSection.tsx:64-199`). Selection mutations use `tripsApi.selectAirfare`, `selectStay`, and `selectRental` and update Trip state (`frontend/src/components/TripWorkspace.tsx:607-704`, `frontend/src/api/tripsApi.ts:679-701`).
4. Draft readiness is checked before promotion. Blocking issues appear in a banner with jump actions; budget overage opens an acknowledgment dialog. Planned alternatives are selected for a two-to-three item comparison, then one can enter booking review (`frontend/src/components/TripWorkspace.tsx:879-1058`, `frontend/src/components/DraftReadinessBanner.tsx:45-75`, `frontend/src/components/BudgetOverageModal.tsx:82-151`). The comparison uses a mobile tablist with arrow/Home/End keys and a desktop table labeled as a grid (`frontend/src/components/ItineraryComparisonView.tsx:21-51`, `frontend/src/components/ItineraryComparisonView.tsx:110-140`, `frontend/src/components/ItineraryComparisonView.tsx:292-345`).
5. Booking review displays selected snapshots, component totals, a simulated-booking disclosure, and an error alert on conflict; submission has a live announcement (`frontend/src/components/BookingReviewView.tsx:84-99`, `frontend/src/components/BookingReviewView.tsx:127-180`, `frontend/src/components/BookingReviewView.tsx:261-355`). Confirmation has a booking-reference status announcement and cost breakdown (`frontend/src/components/BookingConfirmationView.tsx:38-58`, `frontend/src/components/BookingConfirmationView.tsx:120-151`). Active Booking cancellation calls `tripsApi.cancelBooking`; on success, the workspace closes the confirmation and opens a post-cancellation triage dialog, refreshes history, and announces the outcome via autosave status (`frontend/src/components/TripWorkspace.tsx:1074-1109`). History fetches booking records and shows reference codes, status, totals, and optional component absence (`frontend/src/components/BookingHistorySection.tsx:48-81`, `frontend/src/components/BookingHistorySection.tsx:90-158`).
6. The About panel is rendered on public and authenticated pages. Opening focuses its heading, Escape within the panel closes it, and close restores trigger focus. It is a non-modal `aside`; keyboard focus may proceed into the page (`frontend/src/App.tsx:101`, `frontend/src/components/AboutDemoTab.tsx:3-29`). Cancellation and removal dialogs use `role="dialog"`, `aria-modal`, initial focus, a Tab loop, Escape, and return-focus logic. `CancelBookingModal`, `CancelTripModal`, `ConfirmRemoveModal`, and post-cancellation triage suppress Escape/backdrop dismissal while pending (`frontend/src/components/CancelBookingModal.tsx:25-100`, `frontend/src/components/CancelTripModal.tsx:25-100`, `frontend/src/components/ConfirmRemoveModal.tsx:25-103`, `frontend/src/components/PostCancellationTriageModal.tsx:25-105`).

## Key Components

- `frontend/src/components/AirfareSearchSection.tsx:18-36` — `formatTime` formats only a clock time in an optional IANA zone; search cards show airport code, clock time, and zone but no date at lines 132-153.
- `frontend/src/components/ItineraryComparisonView.tsx:200-228` and `frontend/src/components/ItineraryComparisonView.tsx:418-466` — mobile and desktop flight comparisons also use `formatTime` without dates.
- `frontend/src/components/BookingReviewView.tsx:139-167` — booking review uses the same time-only flight presentation.
- `frontend/src/api/tripsApi.ts:138-157` — flight search responses include departure/arrival timestamp and separate time zone for both ends. `AirfareComponentResponse` has the corresponding snapshot fields at lines 35-67.
- `src/main/java/app/detour/airfare/AirfareSearchResponses.java:24-51` — backend search response supplies offset date-times and zone IDs; catalog rows map airport zones (`src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql:10-17`).
- `src/main/java/app/detour/common/ClockConfiguration.java:13` and `src/main/java/app/detour/trip/TripService.java:108-116` — expiration uses the fixed PDX `America/Los_Angeles` zone. Booking cancellation applies that trip expiration rule (`src/main/java/app/detour/booking/BookingTransactionExecutor.java:211-224`).
- `src/main/java/app/detour/trip/ItineraryTallyResponse.java:3-11` and `frontend/src/api/tripsApi.ts:334-349` — server tally separates component totals, grand total, remaining budget, and overage. Planned and booking responses carry tally/snapshot data (`frontend/src/api/tripsApi.ts:357-410`, `frontend/src/api/tripsApi.ts:487-500`).
- `frontend/src/components/ItinerarySummaryTally.tsx:8-45` — draft tally derives cents from selected snapshots; it renders unselected components as “Not selected,” a grand total in USD, and budget status (`:47-115`). Alternative, review, confirmation, and history views use server tally and preserve absence labels (`frontend/src/components/AlternativeCard.tsx:120-126`, `frontend/src/components/BookingReviewView.tsx:261-300`, `frontend/src/components/BookingConfirmationView.tsx:141-151`, `frontend/src/components/BookingHistorySection.tsx:153-158`).
- `frontend/src/style.css:578-588` — visible focus outlines and text-bearing status badges. Media rules at lines 645-680 switch comparison to mobile tabs and stack cards/actions. No `prefers-reduced-motion` rule was found; `TripWorkspace` requests smooth scrolling for a rental focus jump (`frontend/src/components/TripWorkspace.tsx:260-269`).

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Authentication and entry | Public forms have a heading, explicit modes, labels, and field errors; global error summary receives focus (`frontend/src/App.tsx:50-53`, `frontend/src/components/AuthScreen.tsx:40-81`). |
| Navigation and profile | Home/Profile/Trip buttons carry `aria-current`; profile lists trips and actions; workspace remains mounted when hidden, retaining draft state (`frontend/src/components/ProfileScreen.tsx:250-324`). |
| Component searches | Controls are labeled, searches expose loading/error/retry, and selections update the draft (`frontend/src/components/AirfareSearchSection.tsx:78-122`, `frontend/src/components/StaySearchSection.tsx:61-105`, `frontend/src/components/RentalSearchSection.tsx:139-199`). |
| Autosave/readiness | Autosave has live status, conflict/error actions; readiness presents issues and jump buttons (`frontend/src/components/TripWorkspace.tsx:1314-1344`, `frontend/src/components/DraftReadinessBanner.tsx:45-75`). |
| Comparison | Desktop table and mobile tabs present alternatives; mobile tab keys change focus and selection (`frontend/src/components/ItineraryComparisonView.tsx:31-51`, `:110-140`, `:292-345`). |
| Modal interactions | Each dialog implements its own focus loop and dismissal logic; pending dismissal suppression is explicit in cancellation/removal flows (`frontend/src/components/CancelBookingModal.tsx:25-100`, `frontend/src/components/ConfirmRemoveModal.tsx:25-103`). |
| Schedule display | Flight data contain both local zones; present search/comparison/review formatting omits date, so a date-changing leg is not distinguishable from clock time alone (`frontend/src/api/tripsApi.ts:138-157`, `frontend/src/components/AirfareSearchSection.tsx:132-153`). |
| Money | Selection, tally, planned alternatives, review, confirmation, and history render USD totals; absent optional components use “Not selected” (`frontend/src/components/ItinerarySummaryTally.tsx:47-115`, `frontend/src/components/BookingHistorySection.tsx:153-158`). |
| Responsive/motion | CSS has 768 px and 520 px adaptations and focus outlines; no reduced-motion media rule appears in the stylesheet (`frontend/src/style.css:578-680`). |

## Existing Tests and Fixtures

- Frontend tests use Vitest, jsdom, Testing Library, and user-event (`frontend/package.json`, `frontend/vite.config.ts`). `npm test` from `frontend` and `npm run build` are the declared commands; no browser automation or axe package is declared.
- `frontend/src/App.test.tsx:9-153` exercises identity errors, registration, logout, mode changes, and the top-level focus summary. Later tests cover profile, autosave, deletion, dialog keyboard interactions, and focus restoration (`:379-560`, `:751-1087`, `:1221-1261`).
- `frontend/src/ProgressiveTripBuilder.test.tsx:54-1186` covers Home entry modes, all three component searches, selections, tally, removal, conflict, and modal focus handling. `frontend/src/DraftPromotion.test.tsx:67-651` covers readiness jumps, overage acknowledgment, promotion, announcements, and a mobile layout test in jsdom.
- `frontend/src/ItineraryComparisonAndBookingReview.test.tsx:214-757` covers selection constraints, desktop/mobile comparison markup and tab keys, missing components, review, booking success/conflict, and active booking restrictions. `frontend/src/FeeFreeCancellationAndTriage.test.tsx:169-931` covers cancellation, triage, history, and pending dismissal. `frontend/src/components/AboutDemoTab.test.tsx:6` covers About keyboard behavior.
- `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java:20-104` verifies airport zones and local departure/arrival dates. `src/test/java/app/detour/catalog/CatalogTemporalMoneyRoundTripIntegrationTest.java:36-54` covers time-zone round trips; booking cancellation integration tests cover PDX midnight (`src/test/java/app/detour/booking/BookingCancellationIntegrationTest.java:148-150`).
- Existing frontend tests assert structure and behavior in jsdom. No source evidence establishes rendered contrast measurements, actual browser 200% zoom behavior, visual viewport overflow, or a complete registration-to-cancellation keyboard journey. Research did not execute tests or contact services.

## Dependencies and Operational Constraints

- The frontend sends same-origin `/api` requests (`frontend/src/api/tripsApi.ts:643-727`); backend identity, trip, and booking APIs own persisted results. The ticket's presentation pass therefore sits over established server pricing, eligibility, inventory, and authorization boundaries.
- Flight fixtures cover PDX, SFO, MUC, and MEX zones and date-changing arrivals (`src/main/resources/db/migration/V10__seed_march_2027_airfare_catalog.sql:10-17`, `src/test/java/app/detour/catalog/AirfareFixtureIntegrityAssertions.java:20-104`).
- The backend test suite uses Maven/Spring integration tests; the frontend scripts are local Vitest and Vite build commands (`pom.xml`, `frontend/package.json`). Browser-rendered checks require a browser environment beyond jsdom. No live external service is needed by the tests identified here.

## Historical Context

- P07-T01 established visual/terminology scope; P07-T02 established responsive navigation and Draft context; P07-T03 established the non-modal About panel and its copy (`ai/thoughts/tickets/2026-09-23-p07-t01-establish-detour-visual-system.md`, `...-p07-t02-deliver-responsive-navigation-and-context.md`, `...-p07-t03-finalize-public-copy-and-demo-disclosure.md`). These tickets describe intent; the checked-out React and CSS are the current behavior.
- Phase 7 assigns keyboard workflows, semantics, zoom, reduced motion, and time-zone display to work package 7.4 (`ai/thoughts/phases/phase-7-product-experience-and-release.md`).

## Open Questions

- Which browser and assistive-technology combinations are available for rendered keyboard, zoom, and screen-reader verification in this workspace? Source/tests alone cannot establish those outcomes.
- Search results expose full flight leg timestamps and zones; snapshot responses expose optional timestamp fields. Planning should confirm behavior for any legacy or absent snapshot fields when presenting dates.
- Some view changes remove the initiating button and mount a new screen; the existing source has focused headings in some flows but no single view-transition focus policy. The full transition matrix needs verification during planning and implementation.
