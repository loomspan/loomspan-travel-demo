---
date: 2026-09-26
repository: loomspan-travel-demo
branch: main
commit: f9ebcc232af0455821b2d9dbc2b76922cd399882
ticket: ai/thoughts/tickets/2026-09-25-pr5-present-working-plan-and-saved-options.md
tags: [trips, frontend, options, comparison, accessibility]
---

# Working Plan and Saved Options Presentation Research

## Research Question

How do the current Trips list, workspace, option actions, comparison, and cancellation follow-up present the PR3/PR4 Working plan and Saved option contracts, and where are their test seams?

## Summary

The server supplies one Working plan, individually named and dated Saved options, option snapshots and tally facts. The frontend has save, update, and copy-to-edit calls, but the workspace still combines Working and Saved records in an `Alternatives` grid with Draft and Planned counts, IDs, and older terminology. The list also shows Draft/Planned counts; comparison uses generic itinerary numbers and the Working plan date range above individually saved options.

## Repository State

- Observed 2026-09-26 22:01 PDT on `main` at `f9ebcc232af0455821b2d9dbc2b76922cd399882` (`pr4 — Start named trips from an inline Trips page`). `git status --short` had no entries when research began.
- No production code or tests were changed in this step.

## Current Behavior and Data Flow

- `ProfileScreen` owns Trips navigation and mounts `TripWorkspace`; the workspace initializes from `initialTrip`, keeps local shared-detail and option-action state, and receives refreshed Trip responses after mutations. `TripWorkspace.tsx:79-185`, `:309-339`.
- Workspace edits Working dates, destination, travelers, and budget. A 600 ms debounced PUT of shared details uses Trip and Working versions; `saved`, `error`, and `conflict` messages are rendered in a live status region with retry/reload controls. Session expiry keeps an unsaved warning and calls the authentication callback. `TripWorkspace.tsx:394-565`, `:1447-1478`.
- The builder uses `trip.drafts[0]` as its active Working plan and shows its tally and flight/stay/car slots. It has an option-name field with Save as new option and, after loading an option, Update this option. `TripWorkspace.tsx:126`, `:1557-1647`.
- Saving a named option first requires a saved Working plan and name, then POSTs it. Updating PUTs the current Working snapshot into the selected option. Loading presents a replacement dialog, optionally saves the current Working plan first, then POSTs the selected option into Working and preserves the Saved original. Failures become status messages. `TripWorkspace.tsx:919-1022`.
- Server save requires at least one usable component and snapshots the Working plan's dates and selections; update rejects an option with booking history. Loading validates and reprices selections and can return a `revisionSummary`. `TripService.java:386-476`. Shared-detail changes revalidate Working selections, while destination/travelers cannot change once Saved options exist. `TripService.java:244-307`.
- The workspace header uses `trip.label`, Working dates, traveler count, and technical Trip version. It then renders an `Alternatives` section with Working and Saved records together, Draft and Planned counts, comparison selection, and older explanatory text. `TripWorkspace.tsx:1425-1450`, `:1824-1909`. `AlternativeCard` renders selection names, tally, booking/expired badges, generic ID, and actions; `singleWorking` changes some labels and hides some Draft actions, but the card still exposes the ID. `AlternativeCard.tsx:53-142`, `:146-235`.
- The list uses the Trip name and destination, labels its dates as Working plan dates, then shows Draft and Planned counts. Its option summary has each option's name and dates, but also lifecycle badges. `TripListSection.tsx:19-35`, `:76-125`.
- Comparison selects two or three Planned entries from `trip.alternatives`, with separate desktop table and mobile tab views. Both use current component/tally snapshots; the header shows the Working plan dates, while option headings and tabs use generic itinerary numbers/IDs. Mobile tabs support arrow/Home/End keyboard movement. `TripWorkspace.tsx:1171-1191`, `:1325-1339`; `ItineraryComparisonView.tsx:87-165`, `:297-350`.
- Booking review, confirmation, cancellation and post-cancellation triage are separate workspace modes/components. The cancellation triage can open a Saved option into the existing Working plan or continue that plan. `TripWorkspace.tsx:1193-1323`, `:1342-1365`; `PostCancellationTriageModal.tsx:140-195`.

## Key Components

- `frontend/src/components/TripWorkspace.tsx:919` — save guard and named-option mutations.
- `frontend/src/components/TripWorkspace.tsx:1325` — comparison and booking view routing.
- `frontend/src/components/TripWorkspace.tsx:1557` — Working builder and actions.
- `frontend/src/components/TripWorkspace.tsx:1824` — combined alternatives presentation.
- `frontend/src/components/AlternativeCard.tsx:53` — Working/Saved card facts, badges, and actions.
- `frontend/src/components/TripListSection.tsx:19` — Trip card and option summary.
- `frontend/src/components/ItineraryComparisonView.tsx:87` — responsive comparison views.
- `frontend/src/components/RevisionSummaryBanner.tsx:8` — displayed selection removals and price/quantity adjustments.
- `frontend/src/api/tripsApi.ts:354` — typed Working, Planned, Alternative, and revision response fields.
- `frontend/src/api/tripsApi.ts:676` — Trip name, shared-detail, option save/update/load endpoints.
- `src/main/java/app/detour/trip/TripService.java:829` — server response builds Working, Saved, combined alternatives, individual dates/tallies, and booking.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Naming and dates | Trip list uses `trip.name ?? trip.label`; workspace uses `trip.label`; option cards have `alternative.name` but no dates; comparison uses itinerary numbers/IDs and the Working date range (`TripListSection.tsx:84-88`, `AlternativeCard.tsx:70-79`, `ItineraryComparisonView.tsx:98-105`). |
| Save actions | Save/update/load APIs exist; the UI has a single shared option-name input. Update appears after load; no rename-only control is visible in `TripWorkspace.tsx:1557-1576` or `AlternativeCard.tsx:146-235`. |
| Empty Working plan | Working plan is editable without components. Save button is enabled except while pending or canceled; server rejects componentless snapshot (`TripWorkspace.tsx:1568-1575`, `TripService.java:433-437`). |
| Date-change summary | Server returns removals/adjustments and workspace renders `RevisionSummaryBanner`, whose dismiss button clears local display (`TripWorkspace.tsx:1545-1551`, `RevisionSummaryBanner.tsx:8-59`). |
| Responsive/accessibility | CSS changes comparison to mobile tabs at 768 px and makes cards/action rows stack at narrow widths; workspace focuses view headings and comparison tabs implement keyboard switching (`style.css:649-678`, `TripWorkspace.tsx:223-228`, `ItineraryComparisonView.tsx:119-151`). |

## Existing Tests and Fixtures

- `frontend/src/DraftPromotion.test.tsx:36-117` covers save, load replacement/preserve, update, and stale conflicts with mocked API calls.
- `frontend/src/App.test.tsx:54-106`, `:427-457` covers named Trip list/workspace refresh and one Working plan with named-option affordance.
- `frontend/src/ItineraryComparisonAndBookingReview.test.tsx:254-464`, `:588-657` covers selection limit, desktop matrix, mobile keyboard tabs, tally states, and date/time formatting of components; it does not assert each option's own Trip-level date range and name in comparison.
- `frontend/src/FeeFreeCancellationAndTriage.test.tsx:256-391`, `:469-566` covers post-cancellation open/continue behavior and canceled read-only state.
- `frontend/package.json` defines `npm test` (Vitest) and `npm run build` (TypeScript plus Vite). Tests were located but not run during read-only research.

## Dependencies and Operational Constraints

- The authenticated `/api/trips` endpoints own writes; this step made no live requests. Option mutations use Trip/Working/option version preconditions (`tripsApi.ts:451-453`, `TripService.java:386-445`).
- Supported travel dates are March 2027 (`TripService.java:42-43`); the workspace date controls encode the same range (`TripWorkspace.tsx:1701-1737`).
- CSS contains both general and breakpoint-specific card/comparison rules (`frontend/src/style.css:73-124`, `:214-250`, `:649-678`).

## Historical Context

- The current HEAD is the PR4 inline Trips-page change; preceding commits are PR3 Working plan/options and PR2 data model. The PR5 ticket names PR3 and PR4 as dependencies and limits this ticket to presentation and comparison, rather than persisted lifecycle rules.

## Open Questions

- The frontend `TripResponse` types mark `name`, `workingPlan`, and `savedOptions` optional while server responses populate them through compatibility accessors. Planning should confirm which legacy-shaped fixtures and callers remain relevant.
- The current `revisionSummary` is returned after a mutation. The ticket's requirement to review effects before updating an option may need an existing preview route or a client-side comparison; no preview route appeared in the current `tripsApi` option endpoints.
