---
date: 2026-10-02
repository: loomspan-travel-demo
branch: main
commit: 5963e78d30d506c09c7ec73ede754f3813ec07e1
ticket: ai/thoughts/tickets/2026-10-02-promote-plan-search.md
tags: [planning, frontend, search, pricing, independent-plans, purchase-locks]
---

# Promote Plan Search Research

## Research Question

How do guest exploration, saved independent plans, and the supported legacy workspace currently present searches, selections, totals, edits, and purchases, and what interfaces and safeguards govern the ticket's presentation and replacement behavior?

Completed research checklist: entry paths and both mockups; selected-plan context and unsaved edits; all three searches/prices; component mutations/failures; historical purchases/read-only state; plan management/booking navigation; design/accessibility; tests/persistence/history. This is Step 1 in pipeline mode under selected profile `full`. No implementation recommendations or production changes belong to this artifact.

## Summary

All three planning paths share `TripComparisonPage` for flights/stays. It currently renders Explore your trip, large selection cards, then Compare more options and tabs. Saved empty plans normally have no active search. Rental search is separate; independent plans repeat missing-component lines below it. There is no price-difference or replacement-confirmation UI.

Independent plans already own distinct dates, party, selections, purchase locks, and server tallies. Top-level trip context describes primary, not necessarily selected, plan. Component mutations apply only successful server responses. Purchased locks include canceled booking history; purchased party/prices can legitimately differ from current planning details. These boundaries are relevant to every new summary and mutation entry path.

## Repository State

- Captured 2026-10-02T17:40:09.9060350-07:00 in `C:\code\loomspan-travel-demo`.
- Branch main; HEAD `5963e78d30d506c09c7ec73ede754f3813ec07e1` (minor trip name change). Start `git status --short` was empty.
- Ticket mentions pre-existing default trip-name edits. They are committed at HEAD: frontend tripName code/tests and backend TripService naming/tests are baseline, not ticket changes to remove.
- Only this research document is produced. No test/application/database/live-service execution occurred.

## Current Behavior and Data Flow

### Entry paths and layout

`frontend/src/App.tsx:249` displays guest exploration after the public form. `GuestTripExplorer.tsx:16` creates a local pseudo-trip with ID guest from entered destination/dates/party/budget. It supplies full search option objects as local selections and chooses stays only for a STAY entry, flights otherwise (`:27`). Browsing does not persist anything.

`TripWorkspace.tsx:1884` dispatches nonempty `plans` responses to `IndependentPlansWorkspace`, otherwise legacy workspace. Canonical backend responses contain plans (`TripService.java:798`), so legacy is principally old-shaped responses/test fixtures rather than normal server hydration.

Independent workspace selects restored ID or primary (`IndependentPlansWorkspace.tsx:30`), sorts primary first, and overrides dates/party in the shared explorer context using the selected plan (`:59`). Explorer is keyed by plan ID (`:226`). Explicit AIRFARE/STAY entry opens only if that component is unlocked; otherwise it passes null (`:227`). Generic entry also passes null. Legacy supplies draft dates with trip-level party and similarly passes null for generic entry (`TripWorkspace.tsx:1423`).

`TripComparisonPage.tsx:60` initializes local category state once. Switching tabs unmounts the previous category and its local filters/results. Null renders a choose-flights-or-stays hint. Its heading is Explore your trip (`:72`); Your selections and full flight/stay cards appear only when a choice exists (`:75`); Compare more options is unconditional (`:105`). Guest flight cards show schedules/zones; saved cards use descriptions despite richer optional API schedule fields. No compact sidebar or summary Change flight/stay action exists.

Independent layout contains plan name and full/partial total, always-expanded date/party fields, purchased details, explorer, RentalSlot/Add a car, then repeated missing-component paragraphs (`IndependentPlansWorkspace.tsx:205`). Any absent component, including optional car, currently labels total partial. Legacy shows Working plan and a separate tally above searches (`TripWorkspace.tsx:1406`, `:1420`).

Both ticket HTML assets and their scripts were read. Search first is the selected variant at `ai/thoughts/tickets/assets/2026-10-02-promote-plan-search/trip-search-layout.html:43`; Guided start is retained context. Populated mockup (`trip-with-selections.html:41`) shows current reference, price delta, sidebar Change actions, and replacement dialog (`:53`). Its catalog values/controls and immediate local saves are illustrative. Empty demo script changes its heading after save, but ticket explicitly controls the persistent Build your plan heading; code and mockup omissions do not override requirements.

### Saved context, edits, and failures

Independent input is distinct from saved plan (`IndependentPlansWorkspace.tsx:20`, `:54`). Explicit save validates 1–8 travelers and integer ages 0–120, sends parent/plan versions, and does not overwrite edits typed during a request (`:85`). Searches use saved context. While dirty, pending disables tabs/component actions, and a visible hint says searches/prices use saved details (`:222`, `:228`).

`guard` captures deferred transitions while dirty (`:66`). Save/Discard/Cancel dialog (`:259`) protects switching, management, reloads, parent navigation, and review. Beforeunload exists while dirty/pending (`:120`); browser history uses the guard (`:126`); background refresh rejects dirty/busy state and rechecks version after awaiting (`:136`). Workspace handle lets parent exits participate.

Independent mutations set pending, await an API result, then apply successful server trip and reset input (`:99`); failure retains previous trip and exposes error/reload. `component` also guards dirty inputs (`:149`). Component callbacks capture a render-time revision object (`:153`), whereas management callbacks use a versions function reading refs (`:154`). These are different version-capture patterns when a mutation is deferred until after saving edits.

Legacy details instead autosave with 600ms debounce, serialization, validation, and auth/conflict recovery (`TripWorkspace.tsx:485`, `:579`). Component handlers save immediately using current rendered versions (`:636`); their rendered pending gate includes component mutation/canceled state, not all unsaved-detail states (`:1435`). Existing legacy behavior therefore differs from explicit independent Save/Discard and does not establish parity with the ticket's unsaved-edit protections.

### Searches, prices, and identity

All monetary fields are integer USD cents. Search results have full fee-inclusive component totals:

| Category | Full option price | Existing selected calculation |
| --- | --- | --- |
| Flight | `pricing.partyTotalPriceCents`, both legs for response travelerCount (`tripsApi.ts:167`) | Each leg base+tax+fee, multiplied by saved party (`ItinerarySummaryTally.tsx:20`). Purchased airfare uses purchased party. |
| Stay | `pricing.totalPriceCents`, every night and required room (`tripsApi.ts:211`) | Nightly base+tax+fee sum times selected unitCount (`ItinerarySummaryTally.tsx:31`). |
| Rental | `pricing.totalPriceCents` for searched interval/billingCycles (`tripsApi.ts:258`) | Daily base+tax+fee times ceil(elapsed duration/24h) (`ItinerarySummaryTally.tsx:41`). |

Backend tally implements those formulas (`ItineraryTallyEngine.java:9`, `:18`, `:28`). `TripService.java:789` selects purchased airfare traveler count from bookings; per-plan `planTally` uses that count (`:795`). Response exposes tally for each plan (`:804`), but top-level tally/working aliases describe primary (`:808`). Selected sidebar context cannot be inferred from top-level primary totals for a different viewed plan.

`formatTallyCents` preserves unknown versus real zero (`ItinerarySummaryTally.tsx:16`). Legacy tally computes locally from selections/trip party (`:52`), and missing-selection helpers return zero. Backend tally carries a partial flag (`ItineraryTallyEngine.java:71` construction), absent from frontend tally type (`tripsApi.ts:340`); independent UI currently derives partial from missing selections. This is a type/display difference in existing code, not an inferred requirement for API change.

Flight search fetch effect (`AirfareSearchSection.tsx:44`) supports public/private endpoints, direct-only and sorting, stale/unmounted response suppression, loading/error/retry/empty. Stay equivalent (`StaySearchSection.tsx:40`) supports type, sorting, budget, required rooms, rating/distance and full/per-room price. Flight identity uses combinationKey for guest or both instance IDs for saved (`AirfareSearchSection.tsx:183`); stay identity uses unit ID (`StaySearchSection.tsx:172`). Already-selected results disable Selected; other results always say Select flight/stay. No category receives comparison price inputs or renders deltas/unavailable state.

Rental is separate and authenticated only (`RentalSearchSection.tsx:76`). It defaults saved start/end at 10:00, validates required and increasing times plus trip-date containment (`:84`, `:101`), and converts destination-local input via SFO/MUC/MEX timezone mapping (`:20`, `:30`). Eligibility requires one traveler >=25 (`:15`). Server independently validates increasing instants and destination-airport-local dates (`RentalSearchService.java:106`), 24-hour billing cycles (`:141`), driver and inventory on select (`TripService.java:1244`).

RentalSlot replaces selected card with search (`RentalSlot.tsx:38`) instead of keeping reference visible. Rental results have no selected unit/interval matching; same-choice identity logically includes both interval and unit, as backend selection identities show (`TripService.java:468`). Different searched durations yield different full totals even for one unit. Guest supplies no rental capabilities.

### Mutation/persistence and protected states

Component client GET/PUT/DELETE uses `/api/trips/{trip}/drafts/{id}/...` (`tripsApi.ts:706`); controller accepts draft/plan aliases (`TripController.java:161`, `:208`, `:255`). ownedDraft resolves a canonical plan (`TripService.java:633`). Requests send expected parent/draft versions and candidate identities, required room count, or rental interval; they do not send invented prices. Existing PUT overwrites editable selection immediately on Select; no replacement confirmation exists.

Independent mutate and legacy handlers apply only after success (`IndependentPlansWorkspace.tsx:99`, `TripWorkspace.tsx:636`, `:666`, `:696`). Backend transactional selection methods recheck ownership, active trip, lock, saved plan context, availability/eligibility, and versions before persistence (`TripService.java:1017`, `:1116`, `:1244`). JDBC version CAS (`JdbcTripRepository.java:200`) and category snapshot writes (`:216`, `:556`, `:568`) update canonical selections. Selection is distinct from inventory-reserving booking.

Purchased locks scan every linked booking record without ACTIVE filtering (`TripService.java:96`). `requireUnlocked` returns 409 CONFIRMED_COMPONENT_LOCKED (`:107`). savePlan preserves purchases and revalidates unconfirmed selections against new context (`:121`); response supplies lockedComponents and purchase per plan (`:800`). Historical cancellation does not unlock purchased components.

Independent purchased section displays purchased dates/count/total and detail navigation (`IndependentPlansWorkspace.tsx:223`). Shared flight/stay explorer receives historical context and lock props; rental uses stored pickup/return with pending including rental lock (`:238`). Canceled trip disables editing, retaining history/duplication. Legacy shared explorer currently receives canceled pending but no component lock props (`TripWorkspace.tsx:1423`); backend restrictions still enforce authenticated requests.

Removal differs by path: independent callbacks directly DELETE through guard (`IndependentPlansWorkspace.tsx:234`, `:240`); legacy opens ConfirmRemoveModal naming component and discarded price, then DELETEs (`TripWorkspace.tsx:733`, `:767`, `:1864`); guest removal updates local state (`GuestTripExplorer.tsx:34`). These are verified existing differences, not recommendations to change safeguards.

Security permits only public flight/stay search GETs, static assets, login/register; other requests authenticate (`SecurityConfiguration.java:47`). Owned-trip queries are owner-scoped and inaccessible records return not-found (`TripService.java:628`). README identifies deterministic fictional catalog and no model/supplier/payment services.

### Management, booking, guest save

Independent toolbar keeps compare/create/copy/rename/make-primary/delete/review (`IndependentPlansWorkspace.tsx:187`). Tab switching is view-only. Compare accepts two/three distinct plans with per-plan data (`:173`). Delete protects booked/history plans, requires replacement primary/count/version and explicit sole-trip confirmation (`:269`). Shared budget lives in Trip settings (`:244`); history/cancellation/duplication remain below planning.

Review uses reviewed plan context (`IndependentPlansWorkspace.tsx:177`). `BookingReviewView.tsx:29` reads alternative selections/tally, posts separate idempotent booking, retains choices on conflict, and disables confirmation without tally (`:33`, `:350`). Heading currently says Review Itinerary & Component Snapshots (`:98`); detailed sections use Airfare/Stay/Rental Car Snapshot and Booking totals.

Confirmation switches to purchased dates/party (`BookingConfirmationView.tsx:18`) and booking selections/tally (`:37`), preserving codes, canceled status, disclosure, and navigation. Current summary heading is Trip Summary (`:124`). Review and confirmation remain distinct from search-option comparison and independent-plan comparison.

Guest save is separately orchestrated in `App.tsx:95`: create trip then sequential flight/stay saves with returned versions; preserve partially completed progress on failure; clear local choices only after success (`:116`). Leave offers save/discard/keep planning (`:252`). Local guest choices are not persisted before this authentication flow.

## Key Components

- `frontend/src/components/TripComparisonPage.tsx:57` — shared flight/stay exploration and selections.
- `frontend/src/components/IndependentPlansWorkspace.tsx:27` — canonical context, edits/versions, navigation and history.
- `frontend/src/components/TripWorkspace.tsx:82` — supported legacy lifecycle; dispatch at `:1884`.
- `frontend/src/components/GuestTripExplorer.tsx:16`, `frontend/src/App.tsx:95` — guest local choices and authenticated save handoff.
- `frontend/src/components/PlanDialog.tsx:3` — initial focus, Tab trap, pending-gated Escape, trigger restoration.
- `frontend/src/components/PlanNavigation.tsx:4` — roving-focus Arrow/Home/End tabs and All plans select.
- `frontend/src/components/ItinerarySummaryTally.tsx:16` — unknown/zero formatting and legacy calculators.
- `src/main/java/app/detour/trip/TripService.java:798` — canonical response and purchased locks/tallies.

## Affected Areas

| Area | Current behavior/evidence |
| --- | --- |
| Prominence | Explore heading, unconditional Compare more options, null generic initial search and cards above results (`TripComparisonPage.tsx:72`, `:105`). |
| Context/editing | Always-expanded selected-plan details; unsaved input distinct and guarded (`IndependentPlansWorkspace.tsx:205`, `:259`). |
| Summary | Independent server tally; local legacy calculations; guest unpersisted option objects. |
| Replacement | PUT already overwrites, but no old/new confirmation or delta. Selected flight/stay result disabled; rental not matched. |
| Protected states | All historical purchases locked; component dates/party distinct from current plan. |
| Accessibility | Search tabs have roles/relationships but no arrow-key or roving focus; plan tabs already implement these. |
| Adjacent flows | Plan management, comparison, budget, booking review, confirmation/history outside shared explorer. |

## Existing Tests and Fixtures

These are source-level coverage findings, not fresh passing results; research ran no tests.

- `frontend/src/IndependentTripPlans.test.tsx:156`: navigation/keyboard; `:170` independent saves/discard/cancel; `:189` failed/conflicting saves/modal focus; `:201` pending/background refresh; `:239` parent/history/beforeunload; `:277` deletion versions; `:289` fresh save-then-promote versions; `:331` comparisons/partial zero totals. Fixtures carry separate plan contexts and canonical dispatch.
- `frontend/src/PublicTripFlow.test.tsx:53`: guest stay/login/save; `:95` currently expects selected flight prominent above results; `:127` discard; `:148` leave/login/save; `:282` session expiry/same-account retry. Some current layout assertions encode behavior intentionally changed by ticket.
- `frontend/src/ProgressiveTripBuilder.test.tsx:234`: retry/empty flight; `:275`/`:354` entry modes; `:435` generic entry/separate rental; `:487` Add a car; `:556` airfare filters/save; `:724` stay pricing/rooms; `:839` car driver/interval; `:1000` legacy confirmed removal; `:1094` conflict/reload; `:1221` modal accessibility. Many fixtures exercise legacy dispatch.
- `frontend/src/ItineraryComparisonAndBookingReview.test.tsx:468`: mobile comparison keyboard; `:559` missing-vs-zero; `:726` missing tally; `:744` booking disabled without tally; `:757` pending; `:826` inventory conflict. Adjacent confirmation/pending tests live in components/BookingConfirmationView.test.tsx and ConfirmationPending.test.tsx.
- `frontend/src/VisualSystem.test.tsx:27`: absent tally versus zero and CSS regex contracts for focus/reduced-motion/controls/forced-colors/menu/responsive grid. This is not a rendered width check.
- `src/test/java/app/detour/trip/IndependentTripPlanIntegrationTest.java:163`: locked stay/car with editable missing flight; `:228` frozen flights after edits and unconfirmed copy; `:263` purchased airfare party in tally/search budget; `:283` plan-specific context. Other cases cover owner isolation, versions/deletion/races.
- Category backend tests AirfareSearchAndSelectionIntegrationTest, StaySearchAndSelectionIntegrationTest, RentalSearchAndSelectionIntegrationTest cover catalog search/selection validation. `TripPricingAndTallyIntegrationTest.java:235` covers server-consistent totals, `:395` searched-component budget exclusion. PublicSearchIntegrationTest and ItineraryTallyEngineTest cover public boundaries and calculations.
- No matching production/test text for Build your plan, Replace flight/stay/car, or price difference exists in current frontend. Existing tests do not establish new replacement confirmation, lock fallback, integrated rental tabs, or compact summary responsive acceptance.

## Dependencies and Operational Constraints

Golden Hour (`frontend/DESIGN.md`) requires tokens, intentional button variants/one primary per region, panel-tab keyboard handling, no inline styles, and reflow without hidden essential info. Existing CSS: plan/details `style.css:1988`; explorer `:2121`; slots `:2437`; review `:2982`; confirmation `:3128`; wide builder grid `:3458`; sidebar collapse `:3488`; mobile slot grid `:3661`; tab focus `:893`. Search tabs currently lack keyboard handlers despite matching panels. Styles remain in existing numbered screen/responsive/accessibility sections.

`frontend/package.json` defines npm test (Vitest/jsdom) and npm run build (TypeScript/Vite). Root `.\mvnw.cmd clean verify` builds frontend into JAR and runs backend tests. Node/npm and Java 21 required; tests use isolated H2/deterministic catalogs. README's separate packaged verifier uses isolated loopback/JAR/temp H2. No credentials or supplier service required; local cache/download readiness was not tested. No default development database or running application was touched.

Persistence is canonical H2/Flyway V21 independent plans, with legacy aliases/fallback retained. API/schema/catalog/supported dates/public rental/purchase semantics are explicitly outside ticket scope.

## Historical Context

Relevant history: 3cd4ec3 (consolidate duplicate planning logged in/out), a587ea8 (independent plans/primary), f394c7f (front end redesign). HEAD 5963e78 contains unrelated date-format name changes plus ticket/assets. Current sources, not commit subject alone, establish behavior.

`ai/thoughts/architecture/2026-09-17-p00-t02-detour-replacement-architecture.md:5` establishes deterministic services and ownership/immutable-booking boundaries. Its original shared-date domain language predates independent plans; current code and README V21 note now give each plan dates/party. Historical references to removed roadmap documents were not treated as current requirements.

## Open Questions

- Canonical and legacy editing/removal/lock props differ; existing safeguards cannot be assumed identical across ticket entry paths.
- Optional frontend tally and detailed metadata plus older fixtures mean zero-returning calculators alone do not prove missing/malformed comparison evidence is valid.
- Rental comparison interval can differ from selected interval; identity and full-total basis include pickup/return as well as unit.
- Purchased context can differ from current plan. Current saved planning context, component historical dates/party/prices, and selected server tally are distinct evidence.
- Research supplies coverage locations but no executed test or rendered desktop/phone verification.

No material developer answer is needed to finish research. These are bounded planning/verification considerations within authorized scope.
