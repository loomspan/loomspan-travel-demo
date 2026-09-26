# pr3 — Save one Working plan and create comparison options only on request

## Outcome

A Trip has one continuously editable Working plan. The user's actual changes save to that same work, while named comparison options are created or replaced only through explicit actions. Users can try different dates without accumulating empty Drafts or itinerary edit history.

## Requirements

- Autosave a changed value or selected component to the existing Working plan with visible saving, saved, failure, retry, and stale-conflict states. No-op edits, opening a page, searching, and changing an unsaved form field before Trip creation must not create another plan or option. Preserve owner-scoped optimistic conflict protection; do not silently overwrite another tab's changes.
- The initial Working plan may be incomplete and component-empty. Save as option requires at least one structurally valid selected flight, stay, or car; a user cannot create an empty Saved option. Budget may remain absent when saving an option; an absent budget disables budget-fit presentation rather than becoming zero. Keep all existing booking-time eligibility, inventory, and payment-simulation checks unless this ticket explicitly changes them.
- Save as new option creates a user-named comparison option from the current Working plan only when the user requests it. The option captures its own dates, selected components, and authoritative pricing facts. The Working plan remains available and no additional Working plan is created.
- Opening a Saved option for editing loads a copy into the single Working plan without mutating the source option. If that would replace different Working-plan contents, explain the effect and let the user keep them as another Saved option or explicitly replace them. Canceling leaves both unchanged.
- Update this option replaces the selected unbooked option with the Working-plan values after validation and repricing; it does not append a revision record or create a second option. Save as new option preserves the source and creates the branch the user requested. A booked option and any option needed by booking history remain immutable; editing it requires an explicit new option.
- Dates may differ among options under one Trip. Date edits to the Working plan trigger revalidation of its flight, stay, and car selections. Show which selections, prices, capacity, or eligibility changed or were removed. Updating a Saved option performs the same validation before replacing it. The unchanged Saved options retain their own dates and facts.
- Determine expiry and booking/cancellation deadlines from the relevant Working plan, Saved option, or booked option's dates, rather than a Trip-wide date. Preserve atomic booking, inventory protection, idempotency, ownership, and canceled-booking records. No user-facing itinerary revision history is added.
- Remove the general ability to create extra component-empty Drafts or duplicate mutable Drafts as independent working records. Post-cancellation continuation may load a saved option into the Working plan or start the existing single Working plan after an explicit user action; it must not create multiple hidden Drafts.

## Acceptance criteria

- [ ] Editing the Working plan updates that one plan, persists across reload, and shows accurate save/failure/conflict status; no-op interactions and searches create no extra options.
- [ ] Save as new option creates exactly one named, dated option after an explicit request and rejects a component-empty Working plan without creating a record.
- [ ] A user can load a Saved option into the Working plan, choose Update this option to replace an unbooked option, or choose Save as new option to retain both; none of those actions silently overwrites different Working-plan content.
- [ ] Editing dates revalidates selected components, explains removals and price or eligibility changes, and leaves other Saved options unchanged.
- [ ] Booked and booking-history options remain fixed; attempts to update them in place do not alter booking facts or inventory, while explicit copying to a new option remains available.
- [ ] Booking, expiration, and cancellation decisions use the selected option's dates and retain owner, concurrency, atomicity, and idempotency protections.
- [ ] A Trip cannot accumulate multiple Working plans through creation, duplication, cancellation follow-up, retry, or concurrent requests.

## Context

- Depends on `2026-09-25-pr2-model-named-trips-and-dated-options.md`; its API behavior supports `2026-09-25-pr5-present-working-plan-and-saved-options.md`. The inline start flow is covered separately.
- This replaces the current multiple-Draft and immutable-Planned promotion workflow. Existing booked snapshots and booking history remain operational records, even though the product does not expose itinerary edit history.
- Scope excludes changing the supported catalog, anonymous Trip persistence, traveler-count or age variation among options, and a new collaborative editing or automatic merge feature.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Working-plan updates and options change lifecycle, persisted contracts, pricing, expiry, booking, inventory, and concurrency behavior.
- **Reassessment triggers:** none.
