---
date: 2026-10-01
repository: loomspan-travel-demo
branch: main
commit: 6fbb44739c9efd8a182c8c55c796ee389c2c9f27
ticket: ai/thoughts/tickets/2026-10-01-organize-my-trips.md
tags: [my-trips, dates, ordering, accessibility]
---

# Organize My Trips Research

## Research Question

What current components, contracts, date semantics, refresh paths, action restrictions, and executable evidence govern the ticket's seven acceptance criteria? In particular, why can a trip remain Upcoming after its displayed Working plan end date?

Research checklist completed: repository/guardrails; projection and persistence; classification and ordering; card hierarchy; refresh/navigation; secondary actions and accessibility; tests and history. This is Step 1 in pipeline mode with selected profile `full`.

## Summary

My Trips renders profile data through `App` → `ProfileScreen` → `TripListSection`. The list already separates cancelled trips from active Upcoming/Past sections and exposes four counted filters. Cards render server array order, raw Working date strings, expanded Saved options, and directly visible secondary buttons; there is no in-progress badge or list action menu.

The server projects Working dates but assigns Past only once both Working and every Saved option have ended. Working dates synchronize with the parent Trip dates, while Saved options retain their own dates. The mismatch is therefore reproducible from supported edits, rather than merely a frontend formatting issue. End-date classification is inclusive in Portland time, whereas option expiration and booking/cancellation eligibility begin at departure midnight and serve a different purpose.

## Repository State

- Recorded 2026-10-01T12:53:39-07:00 on `main`, commit above.
- Initial working tree: only `ai/thoughts/tickets/2026-10-01-organize-my-trips.md` modified. Its execution notes belong to this pipeline and contain the approved full-profile upgrade. No production or test changes existed.
- No `AGENTS.md` found in the checkout by `rg --files -g AGENTS.md`; root file is absent. `ai/thoughts/design-lens.md` explicitly records no active project-specific design guardrails.
- Ticket advisory fast-track profile remains separate from this run's developer-approved `full` profile. The older reconnaissance note says upgrade pending, but the first execution note records the subsequent approval.

## Current Behavior and Data Flow

### Ownership and projection

`TripController.java:43` serves authenticated `GET /api/trips`; `IdentityController.java:67` embeds the same `TripService.tripsProfile` projection in authenticated `GET /api/profile`. Actual app loading uses `identityApi.getProfile` from `App.tsx:52`, not `tripsApi.listTrips` (`tripsApi.ts:644`). Both endpoints therefore share classification changes.

`JdbcTripRepository.java:68` fetches owner trips ordered by parent start date ascending, then database row ID ascending. Its aggregate loader reads exactly the supported Working/Saved representation. `TripService.java:185` builds summaries with parent dates, label/name, version, persisted ACTIVE/CANCELED status, temporal status, Working count, Saved count, aggregate expiration count, active booking count, booking-history flag, primary booking reference, and alternatives. `TripProfileSummary.java:7` and `tripsApi.ts:17` define these contracts. The name accessor currently inherits label through the summary constructor; repository rename keeps name and display_label synchronized (`JdbcTripRepository.java:237`).

Upcoming preserves repository order. Past simply reverses the projected list (`TripService.java:247`), giving descending start order and reversed row-ID ties, not end-date order or name order. There is no distinct server cancelled array: cancelled trips retain temporal classification and live in either array.

### Date classification and mismatch

`ClockConfiguration.java:13` defines `America/Los_Angeles`; the clock is real unless `detour.clock.fixed-instant` config supplies a fixed instant. `TripService.isPast` (`:169`) converts that clock instant to a Portland calendar day and tests `today.isAfter(endDate)`. The end day is therefore still Upcoming.

`tripsProfile` (`:191`) requires `isPast(working.endDate)` AND `allMatch(isPast(saved.endDate))`. For Working March 2–5 and a Saved option March 15–19, the March 6 projection remains UPCOMING while the card displays March 2–5. The symmetric case, future Working with already ended Saved dates, remains Upcoming because Working has not ended.

`updateWorkingDates` (`JdbcTripRepository.java:240`) writes both Working and parent start/end dates. Saving an option captures independent Working dates (`TripService.java:381`); later Working edits do not rewrite these snapshots. Loading an option updates Working/parent dates (`JdbcTripRepository.java:370`). The ticket's displayed trip date semantics currently refer to Working, and the independent-primary-plan model is absent from this checkout.

`isExpired` (`TripService.java:174`) becomes true at the start date's Portland midnight. `expiredAlternativeCount` includes expired Working AND each independently expired Saved option (`:195–217`). This count is not an in-progress or Past count. `TripWorkspace.tsx:252` separately derives Portland today from browser time for option/booked departure eligibility. Browser time can disagree with the configured server clock; there is currently no server today value in the projection.

`ProfileScreen.tsx:308` also derives aggregate `isExpired` and temporalStatus from the active summary and forwards them into `TripWorkspace`. Workspace status badges and some cancellation controls consume those values (`TripWorkspace.tsx:1245,1273`), while individual Saved options use their own dates (`:1695`). This is a shared projection with adjacent visible consumers.

### Filters, summaries, and card actions

`TripListSection.tsx:190` removes CANCELED from both active sections and collects cancelled summaries from both inputs. All count is total input length; active counts are filtered lengths. All renders Upcoming, Past, then Cancelled (the cancelled section appears only when nonempty); dedicated empty filters show hints. Arrays are mapped unchanged, without local sorting or deduplication of malformed duplicate input IDs.

Cards (`:39`) display status badges before the trip title, then destination and `Working plan: YYYY-MM-DD to YYYY-MM-DD`, then optional booking reference and counts. `plannedCount` is Saved options; `bookedCount` is active bookings, while `hasBookingHistory` includes cancelled history. Expanded planned alternatives show names, individual dates, Expired and Booked badges (`:107`). Workspace already renders the complete Saved options section (`TripWorkspace.tsx:1634`) and associated comparison/edit/booking actions.

Secondary availability (`TripListSection.tsx:118`): rename whenever callback exists, including a cancelled card; cancelled cards show disabled Trip canceled; booking-history trips show Cancel trip disabled if Past or any aggregate expiration; never-booked trips show Delete trip. Open trip always invokes its callback, including cancelled trips. Rename trims input, rejects blank or >300 characters, tracks pending state, displays errors with aria-invalid/describedby/role=alert, and special-cases VERSION_CONFLICT.

The server has an additional restriction: rename calls `requireActiveTrip` (`TripService.java:102`), rejecting cancelled trip mutation, so a cancelled card's visible rename currently produces a server error. Name uniqueness is owner-scoped and case-insensitive (`:777`). Delete requires explicit confirmation, matching version/counts, ownership, and no booking history (`:594`); booking history prevents deletion even after cancellation.

`BookingTransactionExecutor.java:259` uses the active booked option's start date for trip cancellation, falling back to parent Working start without an active booking. It rejects at departure midnight, preserves history, restores active booking inventory transactionally, and checks parent version and booking history. Frontend aggregate-expiration blocking is stricter in some mixed-date cases; this is existing behavior, not a new ticket decision.

### Refresh, failure handling, and focus

`App.tsx:52` requests `/api/profile`, ignores obsolete request IDs, and returns success/failure. Initial mount loads it (`:136`); the ProfileScreen callback preserves the authenticated screen on refresh failure (`:232`). No list clock timer, tab-visibility refresh, or window-focus refresh exists.

`ProfileScreen.tsx:157` refreshes when My Trips navigation is clicked; `:169` refreshes on browser back/forward returning to Trips. Workspace Back, deletion, and successful workspace updates refresh (`:337–352`). Creation, list deletion/cancellation, and rename also refresh. Rename reports persisted success plus refresh failure distinctly (`:203`). Reopening an already active trip invokes workspace `refreshIfClean`; opening a different trip fetches it and ignores obsolete open responses (`:128`). Unsaved Working state is retained in a hidden mounted workspace.

Delete and cancellation use existing confirmation modals (`ProfileScreen.tsx:185–305`). Errors remain in the modal; stale delete counts trigger refresh. `ConfirmDeleteModal.tsx:45` and `CancelTripModal.tsx:40` focus Cancel initially, trap Tab, suppress dismissal while pending, handle Escape/backdrop, and restore focus to the previous element or a fallback heading if removed. Moving the invoking secondary button into a closing menu changes whether that previous element remains mounted, which matters for the existing restoration mechanism.

Filters are a labeled native button group with aria-pressed; articles and sections have heading relationships. Current card headings are always h3, while section heading is configurable h2/h3. CSS uses wrapping filter/action flex layouts, overflow-wrap, min-width:0, one-column narrow cards, visible focus, reduced-motion and forced-colors support (`style.css:72–86,134,646–654,670–682,831`). Wide grids use two columns (`:748`). No menu keyboard pattern currently exists in this list.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Counts/sections | Four filters and cancelled isolation already exist; `TripListSection.tsx:190`. |
| In-progress/boundaries | Only UPCOMING/PAST projected; Portland end inclusive and departure expiration separate; `TripService.java:169–195`. |
| Ordering | Server start ascending/row ID, Past reverse; frontend consumes unchanged arrays; `JdbcTripRepository.java:68`, `TripService.java:247`. |
| Refresh | Navigation, popstate, updates and mutations refresh profile; no passive clock transition mechanism; `ProfileScreen.tsx:157–182,337`. |
| Compact hierarchy | Expanded options and raw date strings in cards; detailed Saved options already in workspace; `TripListSection.tsx:70–117`, `TripWorkspace.tsx:1634`. |
| Action restrictions | Card availability, existing modal/version/count errors, server ownership/history and departure restrictions; files above. |
| Accessibility/narrow layout | Native filters and focus-aware modals exist; menu absent; wrapping/min-width and narrow stacking CSS exist. |

## Existing Tests and Fixtures

- `TripApiIntegrationTest.java:1412` verifies both profile endpoints, owner projection and counts. `:1433` verifies existing backend ordering and end-day/next-midnight partition boundaries; equal start ties currently expect insertion order. `:1483` verifies departure expiration and DST, and saving expired Working rejection. These tests do not cover Working ending before a later Saved option or in-progress classification.
- `TripApiIntegrationTest.java:264,334` exercises named Working dates, versioned rename, independently dated Saved options and booked snapshot preservation. `:1574` onward exercises atomic deletion, confirmation counts and version errors. TestClockConfiguration provides a mutable fixed instant in Portland time, independent of test-host timezone.
- `BookingCancellationIntegrationTest.java:144,253,268` covers departure midnight and booked-option dates differing from Working, plus inventory/history/ownership/version guards.
- `App.test.tsx:396` asserts existing card hierarchy/counts/status and backend order. `:453` tests cancelled filtering and aria-pressed. `:61,93` tests rename/conflict and mounted Working refresh. `:819,874,903,1141,1294` tests delete guards, stale counts, dialog keyboard behavior and focus after removal. Several fixtures retain legacy multi-Draft shapes even though current production has one Working plan.
- `FeeFreeCancellationAndTriage.test.tsx:898` directly mounts TripListSection and expects cancelled state to override missing booking history. Other cases exercise cancellation restrictions and dialogs. `ProgressiveTripBuilder.test.tsx:124` asserts independent option expiry and `:244,259` stale refresh protection. `VisualSystem.test.tsx` verifies CSS/native focus/forced-color affordances through source assertions.
- There is no dedicated TripListSection test file and no executable browser layout suite listed in package.json. jsdom tests cannot prove rendered narrow-screen geometry. Frontend uses Vitest/Testing Library; `npm test` and `npm run build` are available. Maven wrapper tests Spring Boot/MockMvc on isolated H2; `-DskipFrontend=true` bypasses npm install/build for focused backend commands. README describes full clean verification and the isolated packaged-release script. Research ran no tests and triggered no application mutation or live service.

## Dependencies and Operational Constraints

README requires Java 21+, Node/npm and PowerShell. Maven generate-resources runs npm ci/build and packages frontend assets; frontend tests are separate. Fictional catalog dates are March 2027; validation requires 1–14 nights and supported catalog bounds (`TripService.java:821`). H2/Flyway stores parent, Working, Saved snapshots and booking history. No external travel supplier, payment, model service or credentials are used. Default data/detour is persistent; tests and packaged verification provide isolated alternatives.

## Historical Context

`18d196d` (fix navigation in browser/back) changed ProfileScreen/Workspace/history and navigation tests. `7c297ca` added unique name behavior and transient notices; `8f5a9a1` separated My Trips from Plan a Trip. Current source, rather than commit titles, establishes behavior above. `6fbb447` removed old tickets/roadmaps; no prior research or plan artifact for this ticket exists. The independent-plans ticket is separate and its future terminology/model is not implemented here.

## Open Questions

Planning must resolve how the list's in-progress signal shares the authoritative server clock, how end-date semantics interact with the two shared projection consumers, and how menu dismissal interacts with modal/rename focus restoration. These are implementation/design questions grounded in ticket intent and repository evidence; research itself requires no additional developer answer. Existing differences between card availability and server restrictions are recorded above without selecting new authorization behavior.

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - ai/thoughts/research/2026-10-01-organize-my-trips.md
SUMMARY: Traced all seven acceptance criteria, shared date mismatch, adjacent projection consumers, refresh/action restrictions and existing verification. Research is grounded in current source and no implementation or tests were changed.
DECISIONS:
  - Treat departure expiration and inclusive end-date classification as distinct existing behaviors; planning must account for both.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Run Steps 2 and 3 with the full profile and this research artifact.
