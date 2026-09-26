# pr5 — Present one Working plan and clear Saved options on the Trips page

## Outcome

The Trips page makes it obvious which itinerary is being edited and which named options were deliberately saved for comparison. Users no longer have to understand Draft counts, version badges, or Planned promotion to manage a Trip.

## Requirements

- Present one prominent Working plan for the open Trip and a separate Saved options section below it. Use the Trip's user-entered name as the main heading and each option's user-entered name and dates in its card and comparison view. Do not expose Draft counts, `Draft vN`, generic technical IDs, Create empty draft, or Duplicate Draft as primary product controls.
- Show the Working plan's dates, traveler group, selected flight/stay/car components, tally, and visible save state. Shared traveler count and ages are edited at the Trip level; options can vary dates but not the traveler group or destination.
- Provide clear actions to Save as new option, open an option as a copy in the Working plan, Update this option, rename an option, and compare options. Name entry and confirmation must make it clear whether an action replaces an unbooked option or keeps both. A booked option offers explicit copying into new work but cannot be updated in place.
- Disable or explain Save as new option when the Working plan has no selected component. The Working plan may still be saved while incomplete. If date changes remove an invalid selection or change price or eligibility, display a concise summary where the user can review it before updating an option.
- Comparison shows each option's own dates and current saved component and price facts. A Trip list card shows the Trip name, destination, working status, and Saved-option count; do not present one date range as applying to every option. Expired or booked state is attached to the relevant option.
- Keep navigation, save messages, option actions, comparison, and cancellation follow-up usable on desktop and narrow mobile screens, with keyboard access, meaningful labels, focus behavior, and recovery from save failures or session expiration.

## Acceptance criteria

- [ ] An open Trip displays exactly one Working plan and clearly separated named Saved options with their individual dates; Trip and option names are distinguishable in the list and workspace.
- [ ] No user-facing flow offers arbitrary empty-Draft creation or shows Draft version/count terminology; an incomplete Working plan remains editable.
- [ ] Save as new option, Update this option, and copy-to-edit actions state their effect clearly and produce the expected single option, replaced option, or intentional additional option.
- [ ] Option cards and comparison display the correct date range, components, price, and relevant expired/booked state for each option.
- [ ] Date-change summaries and save/authentication failures remain visible until the user can review or resolve them; the UI never reports unsaved work as saved.
- [ ] The Trips list, Working plan, Saved options, comparison, and booking/cancellation follow-up remain usable with keyboard, assistive technology, and narrow mobile layouts.

## Context

- Depends on `2026-09-25-pr4-move-trip-start-to-inline-trips-page.md` and `2026-09-25-pr3-save-working-plan-and-manage-options.md`; these provide the navigation and API contracts. This ticket owns the workspace and comparison presentation, not their persisted lifecycle rules.
- Existing Trip workspace code always edits the first Draft and currently shows an alternatives grid with create, duplicate, delete, and promote actions. That behavior is the confusion this ticket removes.
- Scope excludes redesigning public Home imagery, adding new catalog inventory, allowing options to vary destination or travelers, and adding itinerary revision history.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The changed workspace, comparison, accessibility, and booking/cancellation follow-up span several user journeys and depend on new persisted contracts.
- **Reassessment triggers:** none.
