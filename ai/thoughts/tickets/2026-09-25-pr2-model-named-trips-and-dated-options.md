# pr2 — Model named Trips with one Working plan and independently dated options

## Outcome

Each user-owned Trip is a named container for one editable Working plan and any explicitly saved comparison options. Options can have different travel dates while destination, traveler count, and traveler ages remain shared, so users can recognize a Trip without confusing its scenarios with separate Trips.

## Requirements

- Store an editable user-facing Trip name independent of derived destination/date labels. New Trips receive the name entered in the trip-start form. Existing Trips retain a readable name during migration; users can rename a Trip without changing its identity, ownership, options, or bookings. Trip names need not be unique.
- Store the destination, traveler count, and exactly one age per traveler at the Trip level. Require complete ages at new Trip creation. The shared traveler details apply to the Working plan and every Saved option; changing a date does not silently alter traveler ages.
- Enforce at most one editable Working plan per Trip. The initial Working plan has the start and end dates entered when the Trip is created and may have no selected component. Opening Home, Trips, or the trip-start form creates no records.
- Give each Saved option its own user-facing name, dates, component selections, and authoritative price/availability facts needed for comparison and booking. Saved options under one Trip may have different dates. The Trip list must not treat a Trip-level date pair as authoritative for every option.
- Keep owner-scoped version/conflict protection for edits and option changes. A stale update cannot silently overwrite current work. Preserve stable identifiers needed by existing bookings.
- Apply a forward-only schema/data migration to existing DeTour databases. Preserve every booked itinerary and its booking references, and preserve populated alternatives and their selections. Consolidate redundant component-empty Drafts so migration does not perpetuate the reported empty-Draft clutter. Do not require users to delete or reset their existing Trip data to adopt the new model.
- Keep existing booked itinerary records immutable even when an unbooked Saved option is later updated. The later workflow ticket owns the user actions that save, update, or copy options; this ticket establishes the data boundary and read/write contracts they need.

## Acceptance criteria

- [ ] A new Trip has an editable name, shared destination and complete traveler ages, and one Working plan with its own dates; no additional Draft or option appears automatically.
- [ ] Two Saved options under one Trip can retain different date ranges and names without changing each other or the shared traveler group.
- [ ] A Trip rename preserves its identity, Working plan, Saved options, and bookings; same-name Trips remain distinct.
- [ ] A database upgraded from the existing schema retains booked references and populated alternatives, exposes a single Working plan per Trip, and does not present redundant empty Drafts as Saved options.
- [ ] Concurrent stale updates fail clearly, cross-user access remains denied, and booking references still resolve after migration and restart.
- [ ] New and upgraded databases produce consistent Trip, Working-plan, and Saved-option responses for the later UI tickets.

## Context

- Precedes `2026-09-25-pr3-save-working-plan-and-manage-options.md`, `2026-09-25-pr4-move-trip-start-to-inline-trips-page.md`, and `2026-09-25-pr5-present-working-plan-and-saved-options.md`.
- Today Trip dates live on `detour_trip`, multiple Draft rows are allowed, and Planned itineraries and bookings reference separate snapshot rows. These are migration inputs, not desired product terminology. Existing Flyway migrations have already been applied and should not be rewritten.
- Scope excludes a full traveler identity/date-of-birth system, destination variation within one Trip, anonymous persistence, visual redesign, and an itinerary edit-history feature. Booking records remain required operational records, not user-facing itinerary revision history.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change affects persisted contracts, migration, ownership, concurrency, and booking references across existing data.
- **Reassessment triggers:** none.
