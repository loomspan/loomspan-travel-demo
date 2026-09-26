# pr0 — Deliver the public-to-saved named Trip planning journey

## Outcome

DeTour opens on public Home, lets a visitor begin a named Trip without an account wall, and asks for login or registration only when the visitor is ready to save. Authenticated travelers manage one Working plan per Trip and deliberately create named, independently dated Saved options for comparison and booking. The complete journey is clear from Home through booking and no longer accumulates empty Draft alternatives.

## Requirements

- Deliver and integrate the five dependent tickets named below as one coherent user journey. Home is public; Trips is a separate navigation area; Profile contains account details. A guest can fill the inline trip-start form, but no Trip or plan is persisted before authentication and explicit continuation.
- A Trip has a user-facing name and shared destination, traveler count, and required ages. Its single Working plan starts with dates and can be incomplete. Saved options are created only by user request, have their own names and dates, and require at least one selected flight, stay, or car. Budget is optional at the initial trip-start and Saved-option steps.
- Changed Working-plan values save to that same work with truthful status. Users can edit an unbooked option through the Working plan and update it in place, or explicitly save a new option to retain both. Date changes revalidate selections and explain impacts. Traveler count and ages stay the same across a Trip's options.
- Booked options and booking records remain stable. Booking, cancellation, expiry, pricing, inventory, concurrency, ownership, authentication, and CSRF protections continue to use the relevant option's dates and identity.
- Existing DeTour Trip data is upgraded without losing populated alternatives or booked references. Redundant empty Drafts and their user-facing terminology do not carry forward. No itinerary edit-history feature is introduced.
- Align navigation, labels, error states, and documentation with the new flow. Do not leave competing old creation, Draft duplication, or Planned-promotion actions in another entry point.

## Acceptance criteria

- [ ] A signed-out visitor reaches Home, enters Trip name, destination, dates, travelers, and ages on the Trips page, authenticates at Start planning, and saves exactly one owned Trip with one Working plan.
- [ ] An authenticated traveler can change the Working plan, deliberately create two differently dated named options, compare them, update one unbooked option, and book an eligible option without unintended copies or stale data.
- [ ] A session ending mid-flow retains unsaved values, reports the failed save, and supports login and retry without duplicate Trips or options.
- [ ] Existing data survives upgrade with populated alternatives and booking references intact, while redundant empty Drafts no longer appear in the product.
- [ ] Public and private navigation, mobile and keyboard use, ownership boundaries, date-based expiry, booking/cancellation, and concurrency behavior work together across the completed journey.
- [ ] Product copy and user documentation consistently use Trip, Working plan, and Saved option; no core flow requires knowledge of Draft version or Planned snapshot terminology.

## Context

- This is the final integration and acceptance ticket after the following pipeline-ready implementation tickets:
  1. `2026-09-25-pr1-open-home-and-gate-trip-saving.md`
  2. `2026-09-25-pr2-model-named-trips-and-dated-options.md`
  3. `2026-09-25-pr3-save-working-plan-and-manage-options.md`
  4. `2026-09-25-pr4-move-trip-start-to-inline-trips-page.md`
  5. `2026-09-25-pr5-present-working-plan-and-saved-options.md`
- Dependencies may be integrated incrementally, but this ticket starts only when the five implementation outcomes are available. Its purpose is to resolve cross-ticket gaps, retire conflicting old entry points, and verify the complete user journey, not to reopen the settled product model.
- Scope excludes anonymous Trip persistence, changing supported destinations or catalog inventory, traveler variation among options, and itinerary revision history.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Final integration crosses public/authenticated navigation, migrated data, option lifecycle, and booking behavior, where local ticket success does not prove the whole journey.
- **Reassessment triggers:** If all five implementation tickets already include passing end-to-end evidence and this ticket has only bounded copy or documentation cleanup left, reassess the route.
