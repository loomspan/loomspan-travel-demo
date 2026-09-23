---
date: 2026-09-22
repository: loomspan-travel-demo
branch: main
commit: 762d4ffbd4402c37fa538cb2f47d2619726a647c
ticket: ai/thoughts/tickets/2026-09-22-p05-t04-deliver-itinerary-comparison-and-booking-selection.md
tags: [phase-5, comparison, planned-itinerary, booking-review, frontend, accessibility, responsive]
---

# Multi-Alternative Planned Itinerary Comparison and Booking Selection Research

## Research Question

What is the current state of the DeTour codebase regarding Planned itinerary representation, alternative selection, comparison presentation across desktop and mobile layouts, visual treatment of optional missing components, and candidate selection for the transitional simulated booking review screen?

## Summary

The backend and database already capture and expose all descriptive, schedule, and pricing attributes required for Planned itinerary comparison without relying on live catalog joins. Flyway migration `V16__enhance_planned_snapshot_schema.sql`, `JdbcTripRepository.java`, and `TripService.java` persist and populate complete airfare flight numbers, carriers, stops, layover codes and durations, departure/arrival timestamps and timezones, stay property categories, distances to city center, room counts, guest capacities, nightly rate breakdowns, and rental car classes, pickup/return times, and daily rates into immutable Planned snapshots. `TripResponse` provides these snapshots under both `planned` and `alternatives` with server-calculated `ItineraryTallyResponse` objects.

In the frontend, `AlternativeCard.tsx` renders Planned alternatives in a read-only card with actions for "Duplicate to draft" and "Delete planned itinerary", but currently lacks a comparison selection mechanism, candidate selection for booking review, or side-by-side/stacked comparison views. There are currently no comparison components, no mobile alternative switchers, and no simulated booking review screen in `frontend/src/components/`. All needed data models and types are already defined in `frontend/src/api/tripsApi.ts`, and currency/duration/time formatting helpers exist across `ItinerarySummaryTally.tsx` and `AirfareSearchSection.tsx`.

## Repository State

- **Date:** 2026-09-22
- **Repository:** `loomspan-travel-demo`
- **Branch:** `main`
- **Commit:** `762d4ffbd4402c37fa538cb2f47d2619726a647c`
- **Working Tree:** Clean (verified via `git status`)
- **Test Baseline:**
  - Frontend: 64 of 64 Vitest tests passing (`npm.cmd --prefix frontend test -- --run`)
  - Backend: 140 of 140 JUnit integration tests passing (`.\mvnw.cmd test`)

## Current Behavior and Data Flow

### 1. Alternative Creation and Representation
- Users create trips with shared details (`destinationKey`, `startDate`, `endDate`, `travelerCount`, `travelerAges`, `budgetCents`).
- Trips contain mutable Drafts (`TripDraft`) and immutable Planned snapshots (`PlannedItinerary`).
- When a Draft is promoted via `POST /api/trips/{tripId}/drafts/{draftId}/plan` (implemented in P05-T02 and integrated in P05-T03), the server copies all selected catalog facts into snapshot tables:
  - `detour_planned_itinerary`
  - `detour_planned_airfare_snapshot`
  - `detour_planned_stay_snapshot` and `detour_planned_stay_night_snapshot`
  - `detour_planned_rental_snapshot`
- The server recalculates tallies using `ItineraryTallyEngine.java` and returns a `TripResponse` containing:
  - `trip.planned`: List of `PlannedResponse(id, selections, tally)`
  - `trip.alternatives`: Unified list of `AlternativeResponse(id, lifecycle, version, selections, tally)` where Planned entries have `lifecycle = "PLANNED"` and `version = null`.

### 2. Frontend Workspace Alternatives Section
- In `TripWorkspace.tsx` (lines 1238–1278), the "Alternatives" section lists all alternatives from `trip.alternatives`.
- Each alternative is rendered with `AlternativeCard.tsx`:
  - Drafts display "Draft v{version}", selections summary, "Delete draft", "Duplicate to new draft", and "Promote to Planned".
  - Planned alternatives display "Planned itinerary (read-only)", read-only hint, selections summary, "Delete planned itinerary", and "Duplicate to draft".
- There is currently no checkbox or selection control on `AlternativeCard.tsx`.
- There is no comparison trigger or comparison view in `TripWorkspace.tsx`.
- There is no "Select for Booking Review" action on `AlternativeCard.tsx` or in the workspace.

### 3. Component Data Structures
- **Airfare:** `AirfareComponentResponse` holds `outboundCarrierName`, `outboundFlightNumber`, `outboundStopCount`, `outboundLayoverAirportCode`, `outboundLayoverDurationMinutes`, `outboundDepartureTime`, `outboundArrivalTime`, `outboundDepartureTimeZone`, `outboundArrivalTimeZone`, `outboundDurationMinutes`, corresponding return flight fields, `totalDurationMinutes`, and base/tax/fee fare breakdowns.
- **Stay:** `StayComponentResponse` holds `accommodationUnitId`, `unitCount`, `propertyName`, `unitName`, `propertyCategory` (`HOTEL`, `BED_AND_BREAKFAST`, `VACATION_RENTAL`), `locationDescription`, `distanceToCityCenterMeters`, `guestCapacity`, `requiredRoomCount`, and `nights: StayNightResponse[]`.
- **Rental:** `RentalComponentResponse` holds `rentalUnitId`, `pickupAt`, `returnAt`, `locationName`, `vehicleClassName`, `unitIdentifier`, `dailyBasePriceCents`, `dailyTaxCents`, `dailyFeeCents`, and `vehicleCategory`.
- **Tally:** `ItineraryTallyResponse` holds `airfareTotalCents`, `stayTotalCents`, `rentalTotalCents`, `grandTotalCents`, `remainingBudgetCents`, `budgetOverageCents`, and `isOverBudget`.

### 4. Optional Component Absence
- In DeTour, an itinerary must have at least one reservable component, but any individual component type is optional.
- When an optional component was not selected in the source Draft, the promoted Planned snapshot has `selections.airfare == null`, `selections.stay == null`, or `selections.rental == null`.
- Currently, `AlternativeCard.tsx` lines 64–84 checks `if (hasSelections)` and renders only the non-null components, or renders "No components selected yet". In `ItinerarySummaryTally.tsx` lines 77, 83, 89, a missing component shows `'—'`.
- Missing optional components are not yet visually distinguished with specialized messaging (such as "No rental car selected" with muted styling) in a dedicated comparison or review context.

## Key Components

- `frontend/src/components/TripWorkspace.tsx:1238-1278` — Renders the Alternatives section and controls workspace section layout.
- `frontend/src/components/AlternativeCard.tsx:15-141` — Renders individual Draft and Planned alternative cards with lifecycle badges and action buttons.
- `frontend/src/components/ItinerarySummaryTally.tsx:8-45` — Exports helper functions: `formatCents`, `computeAirfareTotalCents`, `computeStayTotalCents`, and `computeRentalTotalCents` (billing cycles calculated as `Math.max(1, Math.ceil(diffHours / 24))`).
- `frontend/src/components/AirfareSearchSection.tsx:18-36` — Contains `formatMinutes` and `formatTime` with timezone parameter support.
- `frontend/src/api/tripsApi.ts:33-98,332-408` — TypeScript interfaces for `AirfareComponentResponse`, `StayComponentResponse`, `RentalComponentResponse`, `ItineraryTallyResponse`, `AlternativeResponse`, and `TripResponse`.
- `frontend/src/style.css:34-42,101-115,190-214` — CSS definitions for badges, alternative cards, builder slots, and responsive media queries (`max-width: 520px`).
- `src/main/java/app/detour/trip/JdbcTripRepository.java:162-219` — Database loader `loadPlannedSelections` executing queries for V16 snapshot tables.
- `src/main/java/app/detour/trip/TripService.java:541-603` — Service mapping from entity records to `TripResponse`, `PlannedResponse`, and `AlternativeResponse` with `ItineraryTallyResponse`.
- `src/main/java/app/detour/trip/ItineraryTallyEngine.java:9-70` — Authoritative server computation of component totals, consecutive 24-hour rental cycles, grand total, and budget overage/remaining.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **Alternatives Section & Selection Trigger** | `TripWorkspace.tsx:1238` lists alternatives with a "Create empty draft" button. It does not count Planned alternatives, provide comparison checkboxes, enforce the 2–3 selection constraint, or offer a "Compare" trigger. |
| **Planned Alternative Actions** | `AlternativeCard.tsx:118-138` provides only "Delete planned itinerary" and "Duplicate to draft". It lacks a comparison selection checkbox and does not have the primary "Select for Booking Review" action. |
| **Comparison View (Desktop & Mobile)** | No comparison component currently exists in `frontend/src/components/`. There are no multi-column side-by-side layouts or mobile switcher tab bars. |
| **Attribute Comparison Matrix** | No component renders the full comparison matrix across financial tallies, flight stops/duration/times, stay property/category/room counts, and car rental cycles/classes. |
| **Missing vs Zero-Cost Components** | `ItinerarySummaryTally.tsx` renders `'—'` when a component is null. No distinction exists between an item that costs $0 and an optional component that was not selected. |
| **Booking Review Screen** | No Booking Review component exists. The application has no screen presenting destination, traveler count, complete snapshot details, grand total, the mandatory fictional booking disclosure, and the disabled Phase 6 "Confirm Booking" action. |
| **WCAG AA Badge Styling** | `style.css:34-42` defines `.badge-planned`, `.badge-draft`, `.badge-expired`, etc., but `.badge-warning` and `.badge-success` used in `ItinerarySummaryTally.tsx` are not explicitly styled in `style.css`. High-contrast budget badges meeting WCAG AA (>= 4.5:1) are needed. |

## Existing Tests and Fixtures

- **Frontend Tests:**
  - `frontend/src/DraftPromotion.test.tsx` (689 lines): Tests draft promotion, validation issues, budget overage modal acknowledgment, and Planned alternative rendering in `AlternativeCard`.
  - `frontend/src/ProgressiveTripBuilder.test.tsx` (477 lines): Tests airfare, stay, rental selection flows, and summary tally calculations.
  - `frontend/src/App.test.tsx` (368 lines): Tests authentication, profile navigation, and trip workspace integration.
  - Test utilities: Vitest, React Testing Library (`render`, `screen`, `within`, `waitFor`), `@testing-library/user-event`, global fetch mock.
- **Backend Tests:**
  - `src/test/java/app/detour/trip/DraftReadinessAndPlannedSnapshotIntegrationTest.java` (595 lines): Verifies V16 schema persistence and immutable Planned snapshot fields for airfare, stay, and rental car.
  - `src/test/java/app/detour/trip/TripPricingAndTallyIntegrationTest.java` (272 lines): Verifies authoritative server tally calculation across all component combinations.
  - `src/test/java/app/detour/trip/ItineraryTallyEngineTest.java` (95 lines): Pure unit tests for airfare, stay, rental, and overage calculations.

## Dependencies and Operational Constraints

- **Client-Side In-Memory Comparison:**
  - The ticket and Phase 5 roadmap specify: *“Saved comparison sets, sharing, printable proposals, and collaborative selection are [FUTURE]. Comparison is restricted to in-memory client selection of 2–3 Planned alternatives for a Trip.”*
  - No new database schema, Flyway migrations, or backend endpoints are required for saving comparison sets.
- **Constraint Enforcement:**
  - Minimum 2 Planned alternatives required to compare.
  - Maximum 3 Planned alternatives allowed in a single comparison.
  - Selection of fewer than 2 disables comparison launch.
  - Selection of more than 3 must be blocked with an accessible notification.
- **Simulated Booking Boundary:**
  - The Booking Review screen is transitional into Phase 6.
  - "Confirm Booking" must be present but non-interactive / disabled, labeled as ready for Phase 6 simulated booking.
  - Must include the mandatory disclosure: *"This is a simulated booking with fictional inventory. No real payment, billing address, or external reservation is required."*
- **Accessibility & Responsive Standards:**
  - Accessible ARIA semantics: `grid` or table headers (`columnheader`, `rowheader`) for the comparison matrix.
  - Fully keyboard operable mobile switcher (`role="tablist"` with `role="tab"`, arrow key navigation, `aria-selected`, `aria-controls`).
  - Screen reader announcements for active alternative changes (`aria-live="polite"`).
  - WCAG AA contrast compliance for within-budget and over-budget badges.

## Historical Context

- `ai/thoughts/tickets/2026-09-22-p05-t01-canonical-pricing-and-server-tally-engine.md`: Established the integer-cent canonical pricing engine and tally responses on all alternative representations.
- `ai/thoughts/tickets/2026-09-22-p05-t02-authoritative-readiness-and-planned-snapshots.md`: Introduced Flyway `V16` schema enhancements storing complete descriptive facts (carrier names, flight numbers, stops, layovers, timezones, property categories, distances, vehicle classes) directly into Planned snapshot tables.
- `ai/thoughts/tickets/2026-09-22-p05-t03-deliver-draft-promotion-and-readiness-experience.md`: Delivered the promotion workflow, actionable readiness feedback, and budget-overage modal dialog.
- `ai/thoughts/phases/phase-5-planning-budget-and-comparison.md`: Work package 5.4 specifies comparing 2–3 Planned alternatives side-by-side on desktop, stacked on mobile with a persistent switcher, visual distinction of missing optional items, and candidate selection for booking review.

## Open Questions

- *For Planning:* What is the optimal view hierarchy for `TripWorkspace` when displaying the comparison view and booking review screen (e.g. state-driven workspace view modes `'builder' | 'compare' | 'booking-review'` vs separate overlay containers)?
- *For Planning:* Should comparison selection checkboxes on `AlternativeCard` be always visible on Planned cards when 2+ Planned alternatives exist, or toggled via a "Select to compare" mode?
- *For Planning:* In mobile stacked comparison view, what is the preferred breakpoint (e.g., standard `max-width: 768px`) for transitioning between the multi-column table/grid layout and the persistent switcher tab layout?
- Note: None of these open questions require developer intervention; all can be resolved during Step 2 planning based on repository patterns and accessibility guidelines.

---

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-09-22-p05-t04-deliver-itinerary-comparison-and-booking-selection.md
SUMMARY: Researched the existing codebase for ticket P05-T04 across backend snapshot models, frontend API contracts, alternative cards, and workspace navigation. Verified that all descriptive snapshot facts, tallies, and types are already available from P05-T01 and P05-T02 without requiring backend changes or server-side comparison persistence. Documented the missing frontend comparison matrix, mobile switcher, missing-component treatments, and booking review screen along with existing test baselines.
DECISIONS:
  - Confirmed that multi-alternative comparison is entirely client-side in-memory selection over existing Planned alternatives in `trip.alternatives`, matching the roadmap constraint that excludes server-side comparison sets.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Proceed to Step 2 (Create Plan) to design the comparison matrix, mobile tab switcher, booking review screen, and test suite.
