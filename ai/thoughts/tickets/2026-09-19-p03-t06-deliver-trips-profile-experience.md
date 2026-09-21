# P03-T06 — Deliver the Trips and Alternatives Profile Experience

## Outcome

Authenticated users have a useful profile that lets them create Trips, understand upcoming and past work, manage Draft and Planned alternatives, observe autosave and conflicts, and complete revision or deletion actions without losing context.

## Requirements

- Replace the current “nothing else to manage” profile placeholder with a responsive Trip workspace while retaining email, password change, logout, global status/error behavior, and the sole collapsed About this demo disclosure.
- Give a new user an empty state that explains what Trips are and offers a clear Plan Trip action. The Phase 3 creation flow collects destination, dates, and traveler count before creating the Trip and first Draft, then allows ages and budget to be completed; Phase 4 later adds the Airfare and Stay entry points and progressive component builder.
- Present Upcoming and Past sections from the backend projection, with Trips ordered by start date and alternatives nested under their Trip. Show compact Draft, Planned, Booked, and Expired counts/statuses when supplied by the backend; do not fabricate Booking/Canceled Booking data before Phase 6.
- Choose a simple card hierarchy during design that keeps the Trip's derived destination/date label primary and alternative status/actions secondary. Exact visual treatment is an implementation-design choice, but desktop and narrow-screen layouts must preserve hierarchy, readable dates/statuses, and reachable actions without horizontal page scrolling.
- Let users create component-empty Draft alternatives and explicitly duplicate supported individual sources into a new Draft. Make empty creation and duplication visibly different actions so users do not copy content accidentally.
- Autosave mutable Trip/Draft fields and expose accessible `Saving`, `Saved`, and failure/conflict feedback. Never clear unsaved input or claim success after a rejected save; a version conflict must explain that newer data exists and offer an explicit reload/retry path rather than silently merging.
- Keep Planned alternatives visibly read-only and route edits through Duplicate to Draft. Present the structured removal/repricing summary returned by shared-detail revision or Trip duplication before the user continues.
- Present distinct Delete Draft, Delete Planned itinerary, and Delete Trip actions with confirmation text naming the affected scope. Trip deletion confirmation must list the Draft and Planned counts supplied by the server; do not offer permanent deletion when the server reports booking history.
- Preserve keyboard operation, visible focus, semantic headings/landmarks, programmatic labels and errors, status announcements that do not steal focus, and focus recovery after navigation, dialogs, errors, and destructive actions.
- Do not add Phase 4 component search/results, Phase 5 comparison/booking-choice UI, Phase 6 booking/cancellation controls, custom names, sharing/collaboration, or Version 2 Events.

## Acceptance criteria

- [x] A new account sees an informative empty profile and can create a supported Trip with one Draft, then refresh and see it in the correct profile section.
- [x] A user can create multiple Draft alternatives, explicitly duplicate a supported source, edit allowed shared details, and see durable `Saving`, `Saved`, validation-error, network-error, and version-conflict states.
- [x] Upcoming and Past Trips render in backend order with a clear nested Trip/alternative hierarchy and accurate Draft, Planned, and Expired information; Booked information appears only when the backend supplies it.
- [x] Planned alternatives are visibly read-only, revision workflows preserve the source Trip, and incompatibility/change summaries name every removed or changed item and its reason.
- [x] Delete Draft, Delete Planned itinerary, and Delete Trip confirmations accurately describe scope; stale or rejected deletion leaves the current view consistent and reports the failure.
- [x] Profile, creation, conflict, summary, empty, loading, and error states work by keyboard and screen reader semantics and remain usable at narrow and desktop widths without horizontal page scrolling.
- [x] Frontend interaction tests cover the major success/error/conflict/destructive paths, while backend isolation and clock behavior remain covered; the production frontend build and packaged application pass.
- [x] Existing registration, login, logout, password change, CSRF/session handling, and About this demo behavior remain intact.
- [x] No component-search, comparison, booking/cancellation, sharing/collaboration, custom-name, or Version 2 Event experience is introduced.

## Context

- **Phase/work packages:** Phase 3 — Trips, Itineraries, and Profile; user-facing integration of work packages 3.1–3.4.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-3-trips-itineraries-and-profile.md`](../phases/phase-3-trips-itineraries-and-profile.md).
- **Hard dependencies:** P03-T01 through P03-T05 must be complete so the UI consumes settled owner-scoped, concurrency, snapshot, revision-summary, temporal, and deletion contracts rather than inventing client-only behavior.
- **Downstream dependencies:** Phase 4 extends the creation/Draft workspace with Airfare and Stay entry points and component selection; Phase 5 adds complete readiness, totals, comparison, and booking choice; Phase 6 adds Booked/Canceled Booking history and cancellation actions.
- **Scope exclusions:** catalog search/results, component selection, canonical price tally/comparison, inventory mutation, booking/cancellation, payment, custom Trip names, notes, sharing, collaboration, voting, merging, and Version 2 Events.
- The phase's exact card hierarchy/status presentation remains an implementation-design choice. This ticket settles the observable hierarchy, information, responsive, and accessibility constraints without prescribing a pixel design.
- This ticket is sized for GPT-5.6 Terra as one end-to-end frontend integration after the domain contracts stabilize, avoiding repeated UI rewrites across the five backend-heavy tickets.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Material interaction and responsive design remain, and the work integrates multiple lifecycle, conflict, deletion, authorization, and accessibility contracts across the production profile experience.
- **Reassessment triggers:** If preceding tickets leave materially different profile projections or revision/deletion contracts viable, resolve those contracts on the full route before binding the UI to temporary shapes.
