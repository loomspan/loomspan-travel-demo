# Inline Trips Page Testing Plan

## Change Summary
Move the authenticated list and Working plan into the Trips destination, complete the shared inline start form, display named and individually dated options, and expose owned Trip rename through the existing endpoint.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Navigation/private data | List remains in Profile or leaks to guests | Render public/authenticated `App`; assert destinations and list visibility |
| Start form | Opening writes, incomplete travelers save, count change loses ages, or guest handoff loses values | User-event flow and POST-count/body assertions |
| Creation/workspace | Duplicate Trip or separate workspace destination | Single POST, active Trips nav, Working plan, Back-to-Trips assertions |
| Summaries | Shared-looking dates, ID titles, or missing Working/Saved distinction | Card DOM assertions using two options with different names/dates |
| Rename | Wrong Trip, stale version, duplicate write, false success, detached options | API request test and UI success/conflict tests |
| Accessibility/layout | Unbound field errors or narrow-screen overflow | DOM accessibility attributes; optional browser viewport and assistive-technology check |

## Existing Coverage and Environment Constraints
`frontend` uses Vitest, Testing Library, jsdom, and fetch mocks (`frontend/package.json`, `frontend/src/App.test.tsx`, `frontend/src/PublicTripFlow.test.tsx`, `frontend/src/api/tripsApi.test.ts`). Existing public-flow tests cover auth handoff, required ages, failed creates, and duplicate pending submission; most App trip-list tests currently enter Profile and need their navigation expectations updated. `npm test` and `npm run build` run without live services. The backend's create, rename, and ownership routes are covered by `src/test/java/app/detour/trip/TripApiIntegrationTest.java`; no backend change is planned, and running Java integration tests is not needed to prove a frontend-only diff. jsdom cannot prove actual small-viewport layout or screen-reader announcement quality.

## Failing Test First
- Name: `shows the owned Trip list in Trips and account controls only in Profile`
- Type: React integration test
- Location: `frontend/src/App.test.tsx`
- Arrange/Act/Assert: Mock an authenticated profile with one named Trip; render App, select Trips and assert its named card and start form, then select Profile and assert email/password controls with no trip card.
- Expected pre-fix failure: the card is absent from Trips and present in Profile.

## Tests to Add or Update

### 1. `shows the owned Trip list in Trips and account controls only in Profile`
- Type: React integration
- Location: `frontend/src/App.test.tsx`
- Proves: Home, Trips, Profile are distinct; list and account controls live in correct destinations.
- Inputs/fixture: Existing authenticated profile fixture, with current `name` populated.
- Doubles or boundary isolation: Mock `/api/profile`; no live service.
- Edge cases: Guest Trips omits owned cards and calls no private Trips endpoint.

### 2. `opens each Home start mode without saving and retains the chosen mode`
- Type: React integration
- Location: `frontend/src/PublicTripFlow.test.tsx`
- Proves: Plan Trip/Airfare/Stay share the inline form; opening makes no POST; Stay's accommodation field appears only in Stay mode.
- Inputs/fixture: Public and signed-in profile responses.
- Doubles or boundary isolation: Fetch spy/request count.
- Edge cases: Navigate away before submission and assert no Trip write.

### 3. `preserves complete start details through count changes and authentication`
- Type: React integration
- Location: `frontend/src/PublicTripFlow.test.tsx`
- Proves: Editable suggested name, destination/dates, optional budget, and age fields survive shrink/regrow and guest auth; save occurs once only after an authenticated click.
- Inputs/fixture: Two or three ages, name edit, budget in dollars, March 2027 dates.
- Doubles or boundary isolation: Mock login/profile/create responses; assert exact POST payload including integer `budgetCents`.
- Edge cases: Blank budget omitted/null according to chosen client serializer; temporarily hidden age retained at original index; cancel authentication retains draft.

### 4. `rejects incomplete or invalid start details with bound field errors`
- Type: React integration
- Location: `frontend/src/PublicTripFlow.test.tsx`
- Proves: No create for blank name, invalid date window/duration, invalid count/age, or invalid budget; `aria-invalid` and `aria-describedby` reference visible field-level messages.
- Inputs/fixture: Table of one invalid field per case.
- Doubles or boundary isolation: Fetch spy proves no POST.
- Edge cases: Fractional/negative/out-of-range age, missing age for increased count, fractional cents or out-of-range budget; server validation error maps to fields and retains values.

### 5. `creates one Trip and opens its Working plan within Trips`
- Type: React integration
- Location: `frontend/src/App.test.tsx` or `frontend/src/PublicTripFlow.test.tsx`
- Proves: One POST, Trips remains `aria-current=page`, Working plan visible, Back returns to list/start instead of Profile.
- Inputs/fixture: Create response containing one Working row and no Saved options.
- Doubles or boundary isolation: Mock create and profile refresh.
- Edge cases: Failed create keeps form and does not announce success; pending click cannot duplicate POST.

### 6. `labels Trip, Working plan, and separately dated Saved options`
- Type: React integration
- Location: `frontend/src/App.test.tsx`
- Proves: Card title is Trip name, destination separate; Working dates explicitly scoped; Saved options show their own names/dates and no truncated ID.
- Inputs/fixture: One Trip with two Saved options on distinct date ranges, plus booking state.
- Doubles or boundary isolation: Mock profile response.
- Edge cases: No options, canceled/expired option and booking badges remain understandable.

### 7. `renames an owned Trip without changing its identity or options`
- Type: API-client unit plus React integration
- Location: `frontend/src/api/tripsApi.test.ts`, `frontend/src/App.test.tsx`
- Proves: `PUT /api/trips/{id}/name` includes `expectedVersion`, trimmed name and CSRF; UI refreshes same Trip ID while option names/booking remain attached.
- Inputs/fixture: Profile summary version, rename response with incremented version, refreshed profile retaining option IDs.
- Doubles or boundary isolation: Mock fetch responses.
- Edge cases: Blank name blocked locally, pending duplicate click blocked; stale-version or network failure retains original title and shows retryable error.

### 8. `preserves workspace safety while navigating Trips`
- Type: React integration
- Location: `frontend/src/App.test.tsx`
- Proves: Existing dirty workspace confirmation, open-retry, cancel/delete and Back routes continue to work after destination consolidation.
- Inputs/fixture: Reuse existing workspace test fixtures and adapt navigation helper from Profile to Trips for list actions.
- Doubles or boundary isolation: Existing mocked fetch and `window.confirm` pattern.
- Edge cases: Discard declined, profile refresh failure, session expiry draft preservation.

## Safe Verification Commands
- Focused: from `frontend`, `npm test -- --run src/App.test.tsx src/PublicTripFlow.test.tsx src/api/tripsApi.test.ts`
- Related suite: from `frontend`, `npm test`
- Full safe suite: from `frontend`, `npm test` and `npm run build`

## Optional Developer Checks
- At desktop and mobile widths, inspect horizontal overflow, form/card stacking, focus order, and visible focus.
- With a screen reader, check field-level error and save-failure announcements. These observations are nonblocking and should be reported as unperformed unless actually checked.

## Exit Criteria
- [x] The planned red test fails for list placement before implementation.
- [x] New and updated tests pass after implementation.
- [x] Full frontend test suite and production build pass.
- [x] Every ticket acceptance criterion has executable evidence; visual/AT observations are reported separately.
- [x] Tests use mocks and make no live or destructive calls.
- [x] Guest privacy, duplicate-create prevention, per-option dates, rename conflict, and workspace navigation regressions are covered.
