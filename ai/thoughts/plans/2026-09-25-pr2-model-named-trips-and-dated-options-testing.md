# Named Trips and Dated Options Testing Plan

## Change Summary
The change adds Trip names and complete-age creation, one dated Working plan, independently named/dated Saved options, a V18-to-V19 data migration, and option-date-aware Booking while preserving ownership and version protection.

## Impacted Areas and Risks
| Category | Risk | Planned evidence |
| --- | --- | --- |
| Migration | Extra populated Drafts or Booking references disappear | Seeded V18 forward-migration test compares source rows/selections and stable Booking/Planned IDs after V19 |
| Data model | Multiple Working Drafts or auto-created options | Fresh create and migrated multi-Draft API/DB assertions plus unique constraint |
| Shared details | Dates/rename alter traveler ages or unrelated options | Two-option isolation and rename tests |
| Concurrency and ownership | Lost update or foreign-user access | Expected-version 409 and cross-owner 404/denial cases |
| Booking | Wrong expiry date or modified booked snapshot | Differently dated options, booking/cancellation and restart tests |
| Client handoff | PR1 form omits name or accepts blank ages | React form payload/validation test and TypeScript build |

## Existing Coverage and Environment Constraints
`TripApiIntegrationTest` already covers creation, Draft cardinality, versioning, profile list, and Planned snapshots; some old assertions allowing optional ages/multiple Drafts must be updated to the new requirements. `DraftReadinessAndPlannedSnapshotIntegrationTest` covers copied facts and owner isolation. `BookingApiIntegrationTest`, `BookingConcurrencyIntegrationTest`, and Booking/Trip restart tests cover booking identity and persistent H2. `PhaseOneCatalogForwardMigrationIntegrationTest` shows the existing Flyway `target`/upgrade fixture convention. Backend tests run with Maven, isolated H2 and fixed clocks; no service, credentials, or network are needed once dependencies are cached. Frontend uses Vitest and `npm run build` in `frontend/`. Do not invoke the app's persistent default `jdbc:h2:file` database from automated checks.

## Failing Test First
- Name: `createRequiresNameAndCompleteAgesAndReturnsOneWorkingPlan`
- Type: HTTP integration test
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Arrange/Act/Assert: As an authenticated owner, POST a Trip with a name, valid destination/dates, count two, and two ages; assert persisted name, one Working plan with supplied dates, zero Saved options, and stable reread. POST missing name or one missing age and assert validation errors with no inserted Trip.
- Expected pre-fix failure: The strict request parser rejects `name`; existing creation permits nullable ages and returns no `workingPlan` or `savedOptions`.

## Tests to Add or Update

### 1. `migratesMultipleDraftsAndKeepsBookedReferences`
- Type: Flyway/H2 integration
- Location: `src/test/java/app/detour/trip/TripModelForwardMigrationIntegrationTest.java`
- Proves: V18 history upgrades with one Working row, Saved copies of each extra populated Draft, no redundant empty Drafts, existing Planned PK/public IDs and Booking FK/reference/snapshots unchanged; old labels become readable names and legacy null ages remain representable.
- Inputs/fixture: Seed V18 Trips with populated-first and empty-first Draft orderings, two populated Drafts with distinct component selections, extra empty Drafts, no-Draft case, booked Planned option, duplicate derived labels, and null age rows. Snapshot row counts/IDs before migration.
- Doubles or boundary isolation: Unique in-memory H2 URL and Flyway `target` V18, then migrate to latest; no external database.
- Edge cases: Partial selections, missing catalog resolution should abort rather than silently drop data; assert migration transaction rollback where H2 supports it.

### 2. `createRequiresNameAndCompleteAgesAndReturnsOneWorkingPlan`
- Type: HTTP integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Name/age validation and exactly one Working, no automatic Saved option.
- Inputs/fixture: Nonblank trimmed name; count 1 and 2; missing/blank/overlong name; absent, null, short, null-item, and out-of-range ages.
- Doubles or boundary isolation: Existing authenticated test user and isolated H2.
- Edge cases: Same-name Trips have distinct IDs; GET list/detail and opening UI/read routes do not create records.

### 3. `renameTripPreservesIdentityOptionsAndBooking`
- Type: HTTP integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Rename updates only name/Trip version and leaves Working ID/dates, Saved option IDs/dates/selections, traveler ages, Booking ID/reference and snapshot facts unchanged.
- Inputs/fixture: Two same-name Trips, one with Saved and booking history; rename one.
- Doubles or boundary isolation: Existing test clock and inventory fixtures.
- Edge cases: Stale expected version returns 409; other user's Trip cannot be renamed.

### 4. `savedOptionsKeepIndependentDatesAndFacts`
- Type: HTTP/repository integration
- Location: `src/test/java/app/detour/trip/TripApiIntegrationTest.java`
- Proves: Two Saved options under one Trip have distinct names, dates, copied prices/selections, and version values; changing one option or Working dates leaves the other and shared traveler ages unchanged. Old Trip date projection equals Working dates only.
- Inputs/fixture: Two March 2027 date pairs and compatible selections/price fixtures.
- Doubles or boundary isolation: Existing catalog seed; use repository fixture helper if PR3 owns public save/update routes.
- Edge cases: Stale option/Trip version conflicts; option for another owner denied; booked option mutation rejected.

### 5. `bookingUsesSelectedOptionDatesAndPreservesSnapshots`
- Type: HTTP integration plus restart
- Location: `src/test/java/app/detour/booking/BookingApiIntegrationTest.java`, `BookingApplicationRestartIntegrationTest.java`
- Proves: Booking expiry follows selected option's start date when Working dates differ; copied Booking facts and Planned reference survive restart; updating an unbooked sibling option does not change booked Planned/Booking rows.
- Inputs/fixture: Fixed clock between the two option start dates; one expired and one future Saved option.
- Doubles or boundary isolation: Isolated H2 and fixed clock; no live inventory.
- Edge cases: Canceled Booking history, option-specific cancellation deadline, idempotent retry, inventory race coverage remains green.

### 6. `tripStartSendsNameAndAllAges`
- Type: React/Vitest integration
- Location: `frontend/src/PublicTripFlow.test.tsx`
- Proves: PR1 start form sends the entered name and numeric ages exactly once after authentication; blank age blocks save before API call; viewing Home/Trips/start form makes no Trip create call.
- Inputs/fixture: Mock `tripsApi.createTrip`, name, destination, dates, two ages.
- Doubles or boundary isolation: API mock, no network.
- Edge cases: Cancel/reopen maintains client draft but does not persist a Trip.

## Safe Verification Commands
- Focused migration: `mvn -DskipFrontend=true -Dtest=TripModelForwardMigrationIntegrationTest test` from repository root.
- Focused API: `mvn -DskipFrontend=true -Dtest=TripApiIntegrationTest test` from repository root.
- Related backend: `mvn -DskipFrontend=true -Dtest=TripApiIntegrationTest,DraftReadinessAndPlannedSnapshotIntegrationTest,BookingApiIntegrationTest,BookingConcurrencyIntegrationTest,TripApplicationRestartIntegrationTest,BookingApplicationRestartIntegrationTest test` from repository root.
- Frontend: `npm test` and `npm run build` from `frontend/`.
- Full safe suite: `mvn -DskipFrontend=true test` from repository root, then frontend commands above. All use test resources and isolated DBs.

## Optional Developer Checks
- On a backed-up copy of a real persistent H2 database, inspect migrated names, Working selection, Saved option dates, and historic Booking references before production use. Report any mismatch; this observation does not replace automated migration fixtures.

## Exit Criteria
- [ ] Red creation test fails before implementation for the expected parser/response reason.
- [x] V18 upgrade fixture and fresh schema tests pass, with counts and IDs proving no populated selection or Booking reference loss.
- [x] New and updated API, booking, restart, and frontend tests pass.
- [x] Broad backend suite and frontend test/build commands pass without touching the default persistent DB.
- [x] Every acceptance criterion in the implementation plan maps to executable assertions, including stale/cross-owner cases and different option dates.
- [x] Booked Planned and Booking snapshot rows remain unchanged after sibling Saved-option edits.
- [ ] Optional configured-environment observation is reported separately if performed; absence is not represented as a pass.
