---
date: 2026-09-23
repository: loomspan-travel-demo
branch: main
commit: a71c695a55327e7a5ad37d5a266665272570e40f
ticket: ai/thoughts/tickets/2026-09-23-p07-t01-establish-detour-visual-system.md
tags: [frontend, design-system, terminology]
---

# DeTour Visual System Research

## Research Question

How do current screens express DeTour identity, actions, status, components, and amounts, and where are the behavior boundaries for visual refinement?

## Summary

The React frontend uses one stylesheet and component-local text. Public and profile screens use a DETOUR eyebrow and Georgia primary heading, while the workspace and booking subviews use varied headings. Colors mix teal with blue, purple, green, amber, and slate state treatments. Core trip and booking behavior is supplied by the existing API clients and component state; CSS and presentation text are separable from those operations.

## Repository State

At 2026-09-23 12:55 PDT, `main` was at `a71c695a55327e7a5ad37d5a266665272570e40f` with a clean working tree. No AGENTS.md or active design-lens guardrails were found.

## Current Behavior and Data Flow

- `frontend/src/App.tsx:116` chooses public authentication or authenticated profile after loading the server profile. Notices are textual.
- `frontend/src/components/ProfileScreen.tsx:206` passes one of `PLAN_TRIP`, `AIRFARE`, or `STAY` into `TripWorkspace` after trip creation. `TripWorkspace.tsx:120` and `:126` open search for entry-specific airfare or stay. The rental slot is explicitly revealed by user action, covered by `ProgressiveTripBuilder.test.tsx:202` and `:252`.
- `frontend/src/components/AlternativeCard.tsx:40` maps Draft and Planned lifecycle to cards and actions. Comparison and booking review consume planned alternatives and authoritative tally fields. Booking and cancellation are server calls in their existing handlers.
- `frontend/src/components/ItinerarySummaryTally.tsx:58` computes draft display totals, uses a dash for missing components, and displays grand total and budget position. `BookingReviewView.tsx:261` uses authoritative tally data. `BookingConfirmationView.tsx:138` displays booked totals. `BookingHistorySection.tsx:79` displays booking status and grand total.

## Key Components

- `frontend/src/style.css:1` — global typography, layout, controls, and focus styling; later sections style each flow with several distinct palettes.
- `frontend/src/components/AuthScreen.tsx:40` and `ProfileScreen.tsx:237` — current text wordmark.
- `frontend/src/components/TripListSection.tsx:16` — trip and alternative list badges/counts.
- `frontend/src/components/AlternativeCard.tsx:49` — Draft/Planned/Booked badges and component descriptions.
- `frontend/src/components/ItineraryComparisonView.tsx:42` — mobile and desktop comparison, missing-component rendering, selection controls.
- `frontend/src/components/BookingReviewView.tsx:261`, `BookingConfirmationView.tsx:138`, and `BookingHistorySection.tsx:79` — later-stage totals and state language.
- `frontend/src/components/ConfirmDeleteModal.tsx:92`, `CancelBookingModal.tsx:88`, and `CancelTripModal.tsx` — separate destructive confirmation scopes.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Identity | Auth/profile show `DETOUR` eyebrow, while nested workspace views use other headings (`AuthScreen.tsx:41`, `ProfileScreen.tsx:237`). |
| Status | Badges, save feedback, warnings, and booking banners have text but use mixed palettes (`style.css:35-44`, `:75-79`, `:273-296`). |
| Money | Draft tally distinguishes missing with a dash; booking review has text for missing; confirmation omits absent component rows (`ItinerarySummaryTally.tsx:73-99`, `BookingReviewView.tsx:261-296`, `BookingConfirmationView.tsx:138-164`). |
| Terminology | List and cards use Draft alternative, Planned, Planned itinerary, and rental variants (`TripListSection.tsx:20-75`, `AlternativeCard.tsx:55-113`). |
| Entry flows | Creation mode is passed to workspace, while car reveal requires an action (`ProfileScreen.tsx:206`, `TripWorkspace.tsx:120-126`, `ProgressiveTripBuilder.test.tsx:66-252`). |

## Existing Tests and Fixtures

`frontend/package.json` provides `npm test` (Vitest) and `npm run build` (TypeScript and Vite). `App.test.tsx` covers authentication, trip lifecycle, deletion, and autosave. `ProgressiveTripBuilder.test.tsx` covers all three entry modes, optional reveal, totals, overage, and removal. `ItineraryComparisonAndBookingReview.test.tsx`, `BookingConfirmationView.test.tsx`, and `FeeFreeCancellationAndTriage.test.tsx` cover later flows. Existing tests assert many exact strings, so terminology changes require intentional assertion updates. CSS appearance is not presently browser-tested.

## Dependencies and Operational Constraints

The frontend has React/Vite/Vitest only. The Spring backend and local database need not change for this ticket. README describes deterministic fictional inventory and a safe packaged identity check with an isolated temporary database; routine frontend tests use mocked API boundaries.

## Historical Context

The Phase 7 ticket assigns this work to visual and terminology consistency. It reserves navigation/layout for P07-T02, public/disclosure copy for P07-T03, and full dialog interaction audit for P07-T04.

## Open Questions

No material product question blocks planning. Exact palette values and shared presentation conventions remain design choices within the settled teal/Georgia direction.
