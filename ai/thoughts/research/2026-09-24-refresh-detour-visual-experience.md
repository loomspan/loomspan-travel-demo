---
date: 2026-09-24
repository: loomspan-travel-demo
branch: main
commit: 40412cbc4add760caba93a4a16d42a3362fd1271
ticket: ai/thoughts/tickets/2026-09-24-refresh-detour-visual-experience.md
tags: [frontend, visual-system, accessibility, responsive, imagery]
---

# DeTour Visual Experience Research

## Research Question

How do the current public and authenticated screens, Home entry actions, native controls, styling, assets, and tests work before the requested app-wide visual refresh?

## Summary

The frontend is React/TypeScript mounted by `frontend/src/main.tsx:1-5`. `App` restores an identity session, then renders either the public authentication screen or `ProfileScreen` (`frontend/src/App.tsx:24-51`, `frontend/src/App.tsx:106-130`). `ProfileScreen` owns Home, Profile, and workspace navigation and the three Home entry actions (`frontend/src/components/ProfileScreen.tsx:34-59`, `frontend/src/components/ProfileScreen.tsx:247-294`). Home currently contains a text introduction and `EmptyProfileState`; it has no Featured destinations section or destination images. The CSS already establishes teal, Georgia headings, light surfaces, responsive breakpoints, reduced motion, and visible focus (`frontend/src/style.css:1-34`, `frontend/src/style.css:551-579`, `frontend/src/style.css:645-751`). Native selects and checkboxes are distributed across creation, search, comparison, promotion, revision, and workspace screens.

## Repository State

- Inspected 2026-09-24 14:21 PDT on `main` at `40412cbc4add760caba93a4a16d42a3362fd1271`; `git status --short` was empty. No pre-existing developer changes were present at research start.
- The recent history includes the new ticket and earlier frontend style/responsive changes (`git log -6 --oneline`). Historical tickets P07-T01 and P07-T04 describe the existing visual and accessibility pass; checked-out code below is the current implementation evidence.
- No `AGENTS.md` was found by `rg --files -g AGENTS.md`. `ai/thoughts/design-lens.md` reports no active project-specific design guardrails.

## Current Behavior and Data Flow

1. On load, `App` calls `/api/profile` through `identityApi.getProfile`, shows `AuthScreen` for an unauthenticated session and `ProfileScreen` for an authenticated one, and routes registration, login, logout, password changes, notices, and focus (`frontend/src/App.tsx:24-129`; `frontend/src/api/identityApi.ts:40-42,66-85`). The public screen has the text DeTour wordmark, Georgia heading via shared CSS, login/register toggles, labeled fields, inline errors, and a submit button (`frontend/src/components/AuthScreen.tsx:11-54`).
2. Authenticated navigation is local `viewMode` state with `home`, `profile`, and `workspace`; the primary navigation uses buttons and `aria-current`, and the workspace is retained but hidden when another view is active (`frontend/src/components/ProfileScreen.tsx:34-52,247-287`). Focus moves to the view heading (`:44-52`). Home renders the wordmark, `Home` heading, brief text, and `EmptyProfileState` (`:288-294`). The latter renders three labeled buttons: Plan Trip, Airfare, Stay (`frontend/src/components/EmptyProfileState.tsx:1-40`). No current Home destination imagery or `img` elements were located in `frontend/src`.
3. Each Home button calls `startCreateTrip` with `PLAN_TRIP`, `AIRFARE`, or `STAY` (`frontend/src/components/ProfileScreen.tsx:128-131,288-293`). That opens `TripCreateModal`; after creation, `ProfileScreen` passes entry mode and optional accommodation type into `TripWorkspace` (`frontend/src/components/ProfileScreen.tsx:261-268,355-366`). In `TripWorkspace`, mode initializes airfare or stay search and leaves rental hidden until requested (`frontend/src/components/TripWorkspace.tsx:126-144,1459-1514`). The modal's destination and accommodation type are native selects with explicit labels; date and traveler fields also have labels and conditional error descriptions (`frontend/src/components/TripCreateModal.tsx:171-267`). The supported destinations are drawn from the modal's `SUPPORTED_DESTINATIONS` constant (`:185-190`).
4. Workspace styling reaches the editable details, component slots and search results, alternatives, autosave, comparison, booking review, confirmation, and cancellation states (`frontend/src/components/TripWorkspace.tsx:1209-1253,1306-1443,1443-1771`). Search filters use native controls (`frontend/src/components/AirfareSearchSection.tsx:78-106`; `frontend/src/components/StaySearchSection.tsx:77-101`; `frontend/src/components/RentalSearchSection.tsx:189-201`). Comparison has desktop table semantics and a mobile tablist (`frontend/src/components/ItineraryComparisonView.tsx:119-151,297-301`). Destructive flows use `role="dialog"` and `aria-modal="true"`, including creation, booking cancellation, trip cancellation, deletion, and post-cancellation triage (`frontend/src/components/TripCreateModal.tsx:144-149`; `frontend/src/components/CancelBookingModal.tsx:99-100`; `frontend/src/components/PostCancellationTriageModal.tsx:105-106`).
5. Browser actions call same-origin JSON endpoints under `/api` (`frontend/src/api/identityApi.ts:40-42`; `frontend/src/api/tripsApi.ts:643-727`). The trip API module has distinct create, update, search/select, promotion, booking, and cancellation calls. The server owns pricing, eligibility, authorization, booking, and inventory checks according to `README.md:31`; the ticket's visual work is across frontend presentation, with existing workflow requests being important observable behavior.

## Key Components

- `frontend/src/style.css:1-34,551-579,634-751` — shared palette, typography, buttons, controls, focus, navigation, surfaces, and responsive rules. It contains earlier rules and later overrides in one stylesheet.
- `frontend/src/components/AuthScreen.tsx:41-54` — public account surface and form.
- `frontend/src/components/ProfileScreen.tsx:247-366` — navigation and rendering of Home, Profile, workspace, and creation dialog.
- `frontend/src/components/EmptyProfileState.tsx:13-39` — present Home entry action content, also usable in profile onboarding.
- `frontend/src/components/TripCreateModal.tsx:144-277` — entry dialog with native select and input controls and validation descriptions.
- `frontend/src/components/TripWorkspace.tsx:1209-1771` — workspace and transitions to comparison, review, confirmation, and component selection.
- `frontend/src/components/ItineraryComparisonView.tsx:119-151,297-301` — mobile tabs and desktop comparison table.
- `frontend/src/api/tripsApi.ts:643-727` — server request boundary for trip, selection, booking, and cancellation behavior.

## Affected Areas

| Area | Current behavior and evidence |
| --- | --- |
| Approved direction | The local mock-up in the ticket was readable during research. It shows a dark teal navigation bar, editorial hero on a pale teal surface, three action cards, three photographic destination cards, and a native-control preview. This is reference imagery, while current source behavior is described separately above. |
| Home | Current Home is a card with brief text and `EmptyProfileState`; all three buttons open the corresponding create modal (`frontend/src/components/ProfileScreen.tsx:288-294`; `frontend/src/components/EmptyProfileState.tsx:13-39`). |
| Public/profile/navigation | Public authentication is a centered card; authenticated navigation is a card containing button links and save status; Profile includes identity, trip lists, and password form (`frontend/src/components/AuthScreen.tsx:41-54`; `frontend/src/components/ProfileScreen.tsx:247-257,295-352`). |
| Native selects | Selects occur in trip creation, workspace details, trip revision, airfare sort, stay type/sort, and rental sort (`frontend/src/components/TripCreateModal.tsx:179-209`; `frontend/src/components/TripWorkspace.tsx:1555`; `frontend/src/components/TripRevisionModal.tsx:209`; `frontend/src/components/AirfareSearchSection.tsx:93`; `frontend/src/components/StaySearchSection.tsx:77,90`; `frontend/src/components/RentalSearchSection.tsx:189`). Shared CSS gives select/textarea minimum height, padding, border, radius, and focus outline (`frontend/src/style.css:576-579`). |
| Native checkboxes | Checkbox inputs occur in direct-only airfare filtering, alternative comparison selection, overage acknowledgment, and trip revision selection (`frontend/src/components/AirfareSearchSection.tsx:82`; `frontend/src/components/AlternativeCard.tsx:76`; `frontend/src/components/BudgetOverageModal.tsx:132`; `frontend/src/components/TripRevisionModal.tsx:285`). Current `.checkbox-label` controls layout while its input stays auto width (`frontend/src/style.css:107-108`). |
| States | Shared CSS includes button hover/disabled states and global visible keyboard focus; select/checkbox hover, checked, disabled, and invalid treatments are not given dedicated shared rules in the inspected stylesheet (`frontend/src/style.css:562-579`, with full `rg` search of control selectors). Error text and warning surfaces have separate classes (`:580-608`). |
| Responsive | Breakpoints at 768px, 720px, and 520px adjust comparison mode, notices/About panel, navigation, card grids, workspace controls, and modal layout; 900px and 1100px expand trip and builder grids (`frontend/src/style.css:645-751`). Body minimum width is 320px (`:21`). |
| Imagery/icons | No committed `.png`, `.jpg`, `.webp`, `.avif`, or `.svg` files were found under `frontend`; no image elements were found in current frontend component source. The local mock-up is outside the repository and is a direction reference, not an owned production asset. |

## Existing Tests and Fixtures

- `frontend/src/ProgressiveTripBuilder.test.tsx:121-133` checks that all three Home actions open the expected dialog and cause no write merely from navigation; adjacent tests at `:176`, `:252`, and `:330` exercise distinct Airfare, Stay, and Plan Trip creation flows and progressive reveal. No test currently asserts Featured destinations or image loading/semantics.
- `frontend/src/VisualSystem.test.tsx:10-35` checks public wordmark, price/missing-selection meaning, reduced motion CSS, and focus outline. It does not render new Home visual content or inspect selected/disabled control styling.
- `frontend/src/App.test.tsx:25-35,1090-1108` exercises heading focus on view navigation and dialog Escape/focus restoration. The broader file covers identity, autosave, deletion, and trip behavior. `frontend/src/ItineraryComparisonAndBookingReview.test.tsx` covers desktop/mobile comparison and booking, while `frontend/src/FeeFreeCancellationAndTriage.test.tsx` covers cancellation dialogs and states.
- The frontend test setup is Vitest with jsdom and Testing Library (`frontend/vite.config.ts:1-3`; `frontend/package.json`). These tests exercise DOM semantics and interactions; jsdom alone does not verify actual browser layout, image rendering, visual contrast, or mobile overflow.
- Historical release evidence reports `npm test` passing 134 tests, Maven `clean verify` passing 185 backend tests, and a desktop packaged-browser check (`ai/thoughts/release/2026-09-23-p07-t05-verification.md:12-22`). Those are prior results, not checks run in this research step. That report notes a 320px rendered keyboard walkthrough was not completed (`:45`).

## Dependencies and Operational Constraints

- Frontend dependencies are React 19, Vite 8, TypeScript 5, Vitest 4, jsdom, and Testing Library (`frontend/package.json`). `npm test` runs Vitest; `npm run build` runs TypeScript then Vite.
- Maven build integrates `npm ci`, frontend build, and copies `frontend/dist` into JAR static assets (`pom.xml:72-117`). `README.md:7,61-72` documents Java/Node requirements and build verification. The packaged verifier starts a temporary loopback JAR and database; it is a separate operational check, not a research action.
- The app uses same-origin session/CSRF requests and local server-backed catalog data (`frontend/src/api/identityApi.ts:40-42`; `README.md:16,31`). No external image service is configured in checked-out frontend source.
- This research did not run tests or start live services. Existing generated `frontend/dist` and `node_modules` directories were present locally; source and tracked assets were the basis for findings.

## Historical Context

- P07-T01 established the DeTour teal/Georgia/text-wordmark system and names progressive reveal and unchanged pricing/booking behavior as requirements (`ai/thoughts/tickets/2026-09-23-p07-t01-establish-detour-visual-system.md`).
- P07-T04 addressed cross-workflow accessibility, dialog/focus semantics, zoom, reduced motion, and mobile presentation (`ai/thoughts/tickets/2026-09-23-p07-t04-complete-accessibility-and-presentation-pass.md`).
- The current ticket explicitly extends the visual system with photography, icons, stronger control styling, and consistency across screens. These ticket requirements are newer intent; implementation evidence above describes the checked-out state.

## Open Questions

- Which source and license record will govern the three production destination photos? No production image assets are currently present in the frontend tree.
- Which browser/viewport tooling is available for rendered responsive, image-load, keyboard, and assistive-technology checks? The existing automated suite uses jsdom, and the prior release record explicitly left a 320px rendered walkthrough open.
