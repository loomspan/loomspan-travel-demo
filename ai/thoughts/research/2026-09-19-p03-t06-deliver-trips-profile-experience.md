---
date: 2026-09-21
repository: loomspan-travel-demo
branch: main
commit: 1fc772f35fb5303189b6a26eb5aff795488af9a8
ticket: c:\code\loomspan-travel-demo\ai\thoughts\tickets\2026-09-19-p03-t06-deliver-trips-profile-experience.md
tags: [trips, profile, alternatives, draft, planned, revisions, autosave, deletion, responsive, accessibility, p03-t06]
---

# P03-T06 Trips and Alternatives Profile Experience Research

## Research Question

How is the DeTour Trips and alternatives domain implemented across the Spring Boot backend and React frontend, and what contracts, projections, lifecycle states, concurrency controls, deletion policies, accessibility requirements, and styling structures govern the delivery of the responsive profile workspace for P03-T06?

## Summary

The repository has completed backend tickets P03-T01 through P03-T05. All backend contracts for owned trips, versioned draft alternatives, immutable planned snapshots, shared-detail revalidation and selective trip duplication, date-derived profile projections, and deletion policies are fully implemented, verified with integration tests, and exposed via REST endpoints on `TripController` and `IdentityController`.

The frontend is currently a React 19 single-page application built with Vite and TypeScript. At present, `ProfileScreen.tsx` displays only the user email, a change password form, and a placeholder section (`"Your profile is ready. There is nothing else to manage here yet."`). `identityApi.getProfile()` calls `/api/profile` (which already returns `{ email, upcoming, past }`), but currently extracts only `email`.

The frontend build (`npm.cmd run build`) and test suite (`npm.cmd test`) are clean and functional. Delivering P03-T06 involves replacing the profile placeholder with an end-to-end trip workspace that supports empty-state onboarding, trip creation (destination, dates, traveler count), completing traveler ages and budget, managing component-empty drafts and duplicated alternatives, autosave feedback with optimistic concurrency handling, read-only planned snapshots, post-revision removal/repricing summaries, scoped deletion confirmations (draft, planned itinerary, and trip with server-reported counts), and responsive, accessible keyboard and screen-reader operation without introducing Phase 4–6 capabilities.

## Repository State

- **Date:** 2026-09-21
- **Repository:** `loomspan-travel-demo`
- **Branch:** `main`
- **Commit:** `1fc772f35fb5303189b6a26eb5aff795488af9a8` (`clean up after P03-T05`)
- **Working Tree:** Clean (`nothing to commit, working tree clean`)
- **Build & Test Tools:** Maven wrapper (`mvnw.cmd`), Node.js / npm (`npm.cmd`), Vite 8.2.2, Vitest 4.1.11, React 19.2.6, TypeScript 5.9.3, Spring Boot 4.1.0 on Java 21.

## Current Behavior and Data Flow

### 1. Authentication, Identity, and Profile Loading
- Entry point: `frontend/src/App.tsx:33-47` (`loadProfile`). On mount, `App` calls `identityApi.getProfile()`.
- Endpoint `/api/profile`: Implemented in `src/main/java/app/detour/identity/IdentityController.java:67-73`. It loads `ProfileResponse identity` (email) from `IdentityService` and `TripsProfileResponse trips` from `TripService.tripsProfile(userId)`, returning `ProfileResponse(email, upcoming, past)`.
- Client response consumption: In `frontend/src/api/identityApi.ts:52-59`, `getProfile` currently discards `upcoming` and `past`, returning only `{ email }`.
- Screen transition: `App.tsx:38` sets `screen: { kind: 'profile', profile }` and mounts `ProfileScreen.tsx:7`.

### 2. Backend Trip Profile Projections and Temporal Partitioning
- Service logic: `src/main/java/app/detour/trip/TripService.java:74-131` (`tripsProfile`).
- Loads all trips owned by the authenticated user ordered by `start_date ASC, id ASC`.
- Partitions into `upcoming` and `past` based on `isPast(trip.endDate())`:
  - `today.isAfter(endDate)` using PDX timezone (`America/Los_Angeles`). A trip remains upcoming through 23:59:59.999 on its end date and transitions to past at midnight following the end date.
  - `upcoming` trips maintain `start_date ASC` order.
  - `past` trips are returned in reverse chronological order (`Collections.reverse(past)`).
- Expiration check: `isExpired(trip.startDate())`:
  - Compares clock against departure midnight in PDX timezone (`Instant departureMidnight = startDate.atStartOfDay(PDX_ZONE).toInstant()`).
  - Unbooked alternatives for trips whose departure midnight has arrived have `expired: true` and `status: "EXPIRED"`.
- Counts projected on `TripProfileSummary`:
  - `draftCount`: count of draft alternatives.
  - `plannedCount`: count of planned alternatives.
  - `expiredAlternativeCount`: `expired ? (draftCount + plannedCount) : 0`.
  - `bookedCount`: currently always `0` in Phase 3.
  - `hasBookingHistory`: boolean from `tripRepository.hasBookingHistory(tripId)`.
- Alternative summaries projected on `AlternativeProfileSummary`:
  - `id`: UUID.
  - `lifecycle`: `"DRAFT"` or `"PLANNED"`.
  - `version`: Long draft version for drafts; `null` for planned.
  - `status`: `"EXPIRED"` if expired, else `"DRAFT"` or `"PLANNED"`.
  - `expired`: boolean.

### 3. Trip Creation Flow
- Endpoint: `POST /api/trips` handled by `TripController.java:27-31`.
- Request DTO: `TripRequests.Create` (`destinationKey`, `startDate`, `endDate`, `travelerCount`, `travelerAges`, `budgetCents`).
  - Strict field check: `TripRequests.java:91-96` rejects any unsupported payload property with `400 VALIDATION_FAILED: The request contains an unsupported field.`
- Validations in `TripService.java:35-52`:
  - Supported destinations: `destination-sfo` (San Francisco), `destination-muc` (Munich), `destination-mex` (Mexico City).
  - Dates: `2027-03-01` to `2027-03-31`, 1 to 14 nights (`ChronoUnit.DAYS.between(startDate, endDate)` between 1 and 14).
  - Traveler count: 1 to 8.
  - Traveler ages: optional on creation. If provided, length must equal `travelerCount` and each age must be 0..120. If null, stored as null in `detour_trip_traveler`.
  - Budget cents: optional on creation. If provided, integer cents 0..100,000,000 ($1,000,000.00).
- Side effects:
  - Generates `detour_trip` row with `version = 0` and derived label: `<Destination Name> — <MMM D–D, YYYY>` (e.g., `San Francisco — Mar 10–14, 2027`).
  - Generates 1 initial component-empty draft in `detour_trip_draft` with `version = 0`.
  - Returns `201 Created` with `TripResponse` containing trip fields, 1 draft, 0 planned, and `revisionSummary: null`.

### 4. Trip Shared-Details Update and Autosave
- Endpoint: `PUT /api/trips/{tripId}` handled by `TripController.java:43-46`.
- Request DTO: `TripRequests.SharedDetailsUpdate` (`expectedVersion`, `destinationKey`, `startDate`, `endDate`, `travelerCount`, `travelerAges`, `budgetCents`).
- Immutability guard (`TripService.java:151-158`):
  - If `!trip.planned().isEmpty()` and destination, dates, or travelers change: throws `409 IMMUTABLE_TRIP: Trips with Planned alternatives cannot change destination, dates, or travelers in place. Create a revised trip instead.`
  - If planned alternatives exist, only budget can be updated in place.
- Optimistic locking:
  - Verifies and increments `expectedVersion` atomically (`advanceVersion`). If version does not match, throws `409 VERSION_CONFLICT: The Trip has changed. Reload before saving.` with `fields.currentVersion`.
- Draft selection revalidation:
  - When destination, dates, or travelers change on a trip without planned alternatives, existing draft selections (airfare, stay, rental) are revalidated. Incompatible selections are removed; capacity changes are adjusted.
  - Returns `TripResponse` with updated `version` and `revisionSummary` containing `removals` and `adjustments`.

### 5. Revised Trip Duplication (Revisions)
- Endpoint: `POST /api/trips/{tripId}/duplicate` or `POST /api/trips/{tripId}/revisions` handled by `TripController.java:48-51`.
- Request DTO: `TripRequests.TripRevision` (`expectedVersion`, `destinationKey`, `startDate`, `endDate`, `travelerCount`, `travelerAges`, `budgetCents`, `sourcePlannedItineraryIds`).
- Requires non-empty `sourcePlannedItineraryIds` containing valid planned snapshot UUIDs from the source trip.
- Validates version against source trip. Revalidates each selected planned itinerary's selections against the new destination/dates/travelers and converts each into a new draft in a newly created trip.
- Returns `201 Created` with the new `TripResponse` and `revisionSummary` listing all removals and adjustments.

### 6. Draft Creation, Duplication, and Promotion
- Component-empty draft creation: `POST /api/trips/{tripId}/drafts` with `{ expectedVersion }`. Advances trip version by 1 and inserts new draft with `version = 0`.
- Draft duplication: `POST /api/trips/{tripId}/drafts/{draftId}/duplicate` with `{ expectedVersion, expectedDraftVersion }`. Advances trip version by 1, checks draft version, and copies selections into a new draft.
- Alternative duplication: `POST /api/trips/{tripId}/alternatives/{alternativeId}/duplicate` with `{ expectedVersion, expectedDraftVersion? }`.
  - For a draft source: `expectedDraftVersion` is required.
  - For a planned source: `expectedDraftVersion` must be null/omitted. Copies planned selections into a new draft.
- Draft promotion to planned snapshot: `POST /api/trips/{tripId}/drafts/{draftId}/plan` with `{ expectedVersion, expectedDraftVersion }`.
  - Fails with `400 ALTERNATIVE_EXPIRED` if trip departure midnight has passed.
  - Fails with `400 PLANNING_NOT_READY` if traveler ages are missing, no adult (>=18) exists, budget is null, or draft has no reservable components selected.
  - When valid, creates immutable planned snapshot.

### 7. Deletion Operations and Policies
- Delete Draft:
  - Endpoint: `DELETE /api/trips/{tripId}/drafts/{draftId}` with `{ expectedVersion, expectedDraftVersion }` (or `DELETE /api/trips/{tripId}/alternatives/{alternativeId}`).
  - Validates versions, deletes draft and its selections, returns updated `TripResponse`.
- Delete Planned Itinerary:
  - Endpoint: `DELETE /api/trips/{tripId}/alternatives/{alternativeId}` with `{ expectedVersion, confirmed: true }`.
  - `expectedDraftVersion` must NOT be supplied.
  - `confirmed` must be explicitly `true` (otherwise `400 VALIDATION_FAILED`).
  - Advances trip version, deletes planned snapshot, returns updated `TripResponse`.
- Delete Trip:
  - Endpoint: `DELETE /api/trips/{tripId}` with `{ expectedVersion, expectedDraftCount, expectedPlannedCount, confirmed: true }`.
  - Blocked if `hasBookingHistory == true`: throws `409 CANNOT_DELETE_BOOKED_TRIP: Trips with booking history cannot be permanently deleted.`
  - Validates `trip.version() == expectedVersion` (otherwise `409 VERSION_CONFLICT`).
  - Validates `trip.drafts().size() == expectedDraftCount && trip.planned().size() == expectedPlannedCount` (otherwise `409 STALE_CONFIRMATION: The Trip alternative counts have changed since confirmation.`).
  - Requires `confirmed == true`.
  - Deletes trip and cascades to travelers, drafts, and planned itineraries. Returns `204 No Content`.

### 8. Frontend Structure and Conventions
- Styling: `frontend/src/style.css` contains all application styles. Uses CSS variables, semantic tags, `.shell`, `.card`, `.profile-card`, `.eyebrow`, `.tabs`, `.field`, `.primary`, `.hint`, `.field-error`, `.status`, `.error-summary`, `.about-tab`.
- CSRF Protection: Handled in `identityApi.ts:16-19` via cookie extraction (`XSRF-TOKEN`) and sent in `X-XSRF-TOKEN` header for unsafe HTTP methods (`POST`, `PUT`, `DELETE`).
- Error Handling: `IdentityApiError` class captures `kind`, `status`, `code`, and `fields`. `App.tsx:11-21` maps API errors to user-facing messages.
- Focus and Accessibility:
  - `errorRef` in `App.tsx:48` automatically focuses the alert container (`role="alert"`) when an error appears.
  - `h1` elements have `tabIndex={-1}` and are focused on screen navigation (`App.tsx:49-51`).
  - Live status updates rendered via `components/StatusRegion.tsx:5` (`role="status"`, `aria-atomic="true"`).

## Key Components

- `src/main/java/app/detour/identity/IdentityController.java:67-73` — Returns `ProfileResponse` combining user identity with `upcoming` and `past` trip summaries.
- `src/main/java/app/detour/identity/ProfileResponse.java:6-10` — DTO holding email and lists of `TripProfileSummary` for upcoming and past trips.
- `src/main/java/app/detour/trip/TripController.java:20-96` — REST controller exposing endpoints for trip creation, listing, detail, shared-details replacement, duplication/revision, draft operations, promotion, and deletions.
- `src/main/java/app/detour/trip/TripRequests.java:17-89` — Record DTOs and strict JSON deserialization enforcing allowed properties for each mutation.
- `src/main/java/app/detour/trip/TripService.java:35-52` — Trip creation with destination, date, traveler count, ages, and budget validation.
- `src/main/java/app/detour/trip/TripService.java:74-131` — `tripsProfile` projection partitioning trips into upcoming and past based on `ClockConfiguration.PDX_ZONE`.
- `src/main/java/app/detour/trip/TripService.java:134-206` — `replaceSharedDetails`: enforces planned-itinerary immutability (`IMMUTABLE_TRIP`), optimistic concurrency, draft selection revalidation, and revision summaries.
- `src/main/java/app/detour/trip/TripService.java:217-276` — Draft duplication, promotion with readiness checks, and alternative duplication into drafts.
- `src/main/java/app/detour/trip/TripService.java:279-313` — Safe deletion operations for alternatives and trips with booking history and stale confirmation guards.
- `src/main/java/app/detour/trip/TripService.java:316-404` — `duplicateTrip`: selective planned-snapshot duplication into new trip with component revalidation and revision summaries.
- `src/main/java/app/detour/trip/TripResponse.java:7-19` — Full trip aggregate response including version, drafts, planned, alternatives, and revision summary.
- `src/main/java/app/detour/trip/TripProfileSummary.java:7-22` — Compact summary DTO for profile lists (id, label, destination, dates, counts, booking history, alternatives).
- `src/main/java/app/detour/trip/AlternativeProfileSummary.java:5-6` — Compact alternative summary (id, lifecycle, version, status, expired).
- `src/main/java/app/detour/trip/AlternativeResponse.java:20-31` — `RevisionSummaryResponse`, `ComponentRemovalResponse`, and `ComponentAdjustmentResponse`.
- `frontend/src/App.tsx:23-103` — Root React component managing screen states (`loading`, `public`, `profile`), notices, global focus, and auth workflows.
- `frontend/src/components/ProfileScreen.tsx:7-34` — Current profile screen containing email, empty state placeholder, and password change form.
- `frontend/src/api/identityApi.ts:21-64` — Fetch client handling CSRF tokens, JSON envelopes, and identity API calls.
- `frontend/src/style.css:1-17` — Application CSS styles, card styling, responsive layouts, and accessibility focus outlines.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| **Profile Screen Hierarchy** | `ProfileScreen.tsx:24-33` currently renders an email `<dl>`, an empty placeholder `<section className="empty-state">`, and a password change form inside a single card (`width: min(100%, 35rem)`). It must be expanded into a responsive workspace displaying Upcoming and Past trip sections, empty state with Plan Trip action, and nested alternatives. |
| **Profile API Integration** | `identityApi.ts:52-59` discards `upcoming` and `past` from `/api/profile`. Needs typed models for `TripsProfileResponse`, `TripProfileSummary`, `AlternativeProfileSummary`, `TripResponse`, `RevisionSummary`, and API methods for trip CRUD, duplication, and alternative operations. |
| **Empty State & Creation Flow** | Current empty state at `ProfileScreen.tsx:27` states `"There is nothing else to manage here yet."` without action. Ticket requires an empty state explaining Trips and offering a Plan Trip action collecting destination (`destination-sfo`, `destination-muc`, `destination-mex`), dates (March 1–31, 2027, 1–14 nights), and traveler count (1–8). |
| **Trip Workspace & Detail** | Once created or selected, the trip detail allows completing traveler ages (0–120 per traveler) and budget ($0–$1,000,000.00 / 0–100,000,000 cents), viewing drafts and planned itineraries, and editing shared details. |
| **Autosave & Concurrency** | Currently no autosave exists in the frontend. Autosaving shared details on `PUT /api/trips/{tripId}` requires optimistic version tracking (`expectedVersion`), accessible `Saving` / `Saved` states, retaining unsaved input on failure, and displaying version conflict feedback (`VERSION_CONFLICT`) with explicit reload/retry. |
| **Read-only Planned & Revision Workflows** | Planned alternatives are immutable (`TripService.java:151-158`, `416-418`). Editing requires Duplicating to Draft (`POST .../alternatives/{id}/duplicate`). Changing destination/dates/travelers when Planned alternatives exist requires `POST .../duplicate` with selected source planned IDs. Post-revision summaries (`RevisionSummaryResponse`) with removals and adjustments must be displayed. |
| **Draft Alternatives Management** | Users can create empty drafts (`POST .../drafts`) or duplicate existing drafts (`POST .../drafts/{id}/duplicate`) or planned alternatives. UI must make empty creation and duplication visibly distinct. |
| **Deletion Modals & Safeguards** | Three distinct deletion paths: Delete Draft, Delete Planned itinerary (requires `confirmed: true`), and Delete Trip (requires `confirmed: true`, `expectedDraftCount`, `expectedPlannedCount`, and is blocked if `hasBookingHistory: true`). Confirmations must clearly state affected scope. |
| **Accessibility & Keyboard Operation** | Visible focus outlines (`:focus-visible` outline in `style.css:15`), semantic headings (`h1` through `h4`), live regions (`StatusRegion.tsx:3-6` for polite announcements), error summary alerts (`App.tsx:96`), and focus recovery after dialogs, deletions, and errors. |
| **Responsive Layout** | `style.css:3-4, 16` constrains cards to `width: min(100%, 35rem)`. The trip workspace and alternative cards must remain readable and functional at both narrow widths (320px) and wider desktop viewports without horizontal scrolling. |

## Existing Tests and Fixtures

### Backend Tests
- `src/test/java/app/detour/trip/TripApiIntegrationTest.java`:
  - 1,366 lines covering trip creation, envelope validation, owner scoping, drafts, planned snapshots, duplication, revalidation of airfare/stay/rental, profile projections with controllable clock (`TestClockConfiguration`), expiration at departure midnight, DST boundary transitions, atomic trip deletion with cascade, stale confirmation rejections, and booking history deletion guards.
- `src/test/java/app/detour/trip/TripApplicationRestartIntegrationTest.java`:
  - Covers disk-backed H2 restart across full lifecycle: creates trip, airfare/stay selections, promotes to planned, restarts application, verifies persistence and catalog separation.
- `src/test/java/app/detour/DetourApplicationTest.java`:
  - Verifies application context bootstrap and catalog integrity.

### Frontend Tests
- `frontend/src/App.test.tsx`:
  - 9 tests verifying profile restoration, registration/login/logout without browser persistence, password field clearing, failed session registration handling, offline/error summary alert focus, password update with disclosure tab, and race condition prevention between password change and logout.
- `frontend/src/api/identityApi.test.ts`:
  - 3 tests verifying CSRF cookie token propagation on unsafe methods, CSRF missing and malformed response normalization, and invalid profile payload rejection.
- `frontend/src/components/PasswordField.test.tsx`:
  - Tests password visibility toggle and range validation error text.

### Verification Execution Status
- Frontend tests run with `npm.cmd test` (3 files, 13 tests) pass in ~8s.
- Frontend production build runs with `npm.cmd run build` (`tsc -b && vite build`) and passes in <1s.
- Full Maven build `.\mvnw.cmd test-compile` compiles frontend, packages static assets, and compiles all Java and test classes cleanly in ~7s.

## Dependencies and Operational Constraints

1. **Origin Airport Fixed to PDX:**
   - Departure airport is fixed to `PDX` (`originAirportCode: "PDX"` in `TripResponse.java:495`).
2. **Supported Destinations:**
   - Exactly 3 supported catalog destinations:
     - `destination-sfo`: San Francisco
     - `destination-muc`: Munich
     - `destination-mex`: Mexico City
3. **Supported Dates and Trip Length:**
   - All travel dates must fall between March 1, 2027 and March 31, 2027 (`FIRST_SUPPORTED_DATE` and `LAST_SUPPORTED_DATE` in `TripService.java:24-25`).
   - Trip duration must be between 1 and 14 nights (`ChronoUnit.DAYS.between(startDate, endDate)`).
4. **Traveler Limits:**
   - Traveler count: integer between 1 and 8.
   - Ages: integers 0 to 120 (`MAX_TRAVELER_AGE = 120`). Array length must equal `travelerCount`.
   - Adult requirement for planned promotion: at least one traveler age >= 18.
5. **Budget Constraints:**
   - Integer cents between 0 and 100,000,000 cents ($1,000,000.00).
6. **Timezone and Temporal Transitions:**
   - `ClockConfiguration.PDX_ZONE` (`America/Los_Angeles`).
   - Past trip transition: occurs after 23:59:59.999 on the trip's end date.
   - Alternative expiration: occurs at departure midnight (00:00:00) on the trip's start date.
7. **Strict Backend Request Parsing:**
   - `TripRequests.requireObject` rejects unexpected JSON fields with `400 VALIDATION_FAILED`. The frontend API client must only send defined properties.
8. **Deletion Rules:**
   - Trip deletion blocked when `hasBookingHistory == true` (`CANNOT_DELETE_BOOKED_TRIP`).
   - Trip deletion requires `expectedDraftCount` and `expectedPlannedCount`; mismatches throw `409 STALE_CONFIRMATION`.
   - Planned deletion requires `confirmed: true`.
9. **Scope Exclusions (Hard Boundaries):**
   - No catalog search or component results (Phase 4).
   - No comparison or booking-choice UI (Phase 5).
   - No booking or cancellation actions (Phase 6).
   - No custom names, notes, sharing, collaboration, voting, or merging.
   - No Version 2 Events.

## Historical Context

- `ai/thoughts/phases/phase-3-trips-itineraries-and-profile.md` defines work packages 3.1–3.4: trip aggregate, alternatives, shared-detail revisions, and profile organization.
- P03-T01 through P03-T05 established the backend foundation in sequence:
  - P03-T01: Initial Trip and Draft foundation (`Trip`, `TripDraft`, `TripService.create`, `TripController`).
  - P03-T02: Versioned draft alternatives, optimistic concurrency (`advanceVersionForDraft`), and autosave backend support.
  - P03-T03: Immutable planned snapshot lifecycle, readiness validation, and draft promotion.
  - P03-T04: Shared-detail revision protection (`replaceSharedDetails`), selective trip duplication (`duplicateTrip`), and component revalidation summaries (`RevisionSummaryResponse`).
  - P03-T05: Date-derived profile projections (`TripsProfileResponse`), temporal partitioning (`UPCOMING` vs `PAST`), departure midnight expiration, and safe deletion policies (`deleteTrip`, `deleteDraft`, `deleteAlternative`).
- P03-T06 is the final ticket in Phase 3. It provides the end-to-end frontend integration connecting the user profile experience to all stabilized Phase 3 backend contracts.

## Open Questions

These questions are documented for consideration during Step 2 (Planning) and do not block codebase research:

1. **Profile Navigation and State Architecture:**
   - How should the user view transitions be organized within `ProfileScreen` (e.g. Master-Detail view where the list of upcoming/past trips is shown, and selecting a trip or "Plan Trip" enters a focused Trip Workspace, or an expandable accordion card layout directly on the profile)?
2. **Autosave Debounce and Visual Feedback Granularity:**
   - What debounce duration (e.g., 500ms–1000ms after user stops typing) best balances responsiveness and network efficiency for traveler ages and budget, and should saving indicators appear inline per field, per trip card, or in the global status region?
3. **Revision Summary Modal vs Inline Presentation:**
   - When a shared-detail update or selective trip duplication returns a `revisionSummary` with removals or adjustments, should it be presented in a dedicated confirmation/acknowledgment dialog or an inline banner before the user resumes editing?
4. **Card Hierarchy and Responsive Grid:**
   - How should alternative cards (Draft and Planned) be nested inside their parent Trip card to ensure clear distinction between primary Trip attributes (destination, dates, travelers, budget) and secondary alternative actions (Duplicate to Draft, Delete) across 320px mobile to 1200px+ desktop?

---

## Step Report: 1_research_codebase
STATUS: complete
ARTIFACTS:
  - c:\code\loomspan-travel-demo\ai\thoughts\research/2026-09-19-p03-t06-deliver-trips-profile-experience.md
SUMMARY: Researched backend trip/profile endpoints, DTO contracts, validation constraints, and deletion policies from P03-T01 through P03-T05 alongside the existing React 19 frontend architecture. Documented repository state, data flows, strict request validation rules, responsive styling, accessibility patterns, and existing verification suites. Identified four focused design questions for Step 2 planning.
DECISIONS:
  - Documented strict backend JSON property filtering in TripRequests as a critical constraint for frontend API client design.
  - Confirmed and recorded that GET /api/profile already returns complete upcoming and past trip profile projections from TripService.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
NEXT: Proceed to Step 2 (Create Plan) and Step 3 (Testing Plan) of the Full 5-Step Pipeline.
