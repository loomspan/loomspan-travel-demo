# Manage trip plans independently with a clear primary plan

## Outcome

Users can see all plans for a trip together, identify their preferred primary plan, and edit each alternative directly without copying it into a separate Working plan. Primary promotion, plan management, and comparison are discoverable near the top of the workspace. Confirmed purchases remain protected while users continue planning unconfirmed parts of their trip.

## Requirements

### Plan identity and workspace navigation

- Every existing trip and newly created trip has exactly one primary plan. Primary means the user's preferred plan and the default plan displayed when opening the trip; it is separate from booking status.
- Place a plan tab bar directly below the trip header, before the selected plan's detailed content. Show the primary first and identify it explicitly with a Primary label or badge that does not rely on color alone. Show other plans using their saved names rather than permanent generic Option 1/Option 2 labels. The primary may also have its own name.
- Display the selected plan in a consistent layout covering its dates, travelers, flights, stay, rental, and price summary. Keep plans accessible on narrow screens and when there are more plans than fit across the screen; support keyboard and assistive-technology navigation.
- Selecting a plan changes the view only. It must not rename, copy, overwrite, promote, or book a plan. Remove the requirement to open an option into a Working plan to edit it, and remove that copy-and-replace workflow from normal plan editing.
- Offer a discoverable Make primary action for alternative plans. Promotion preserves both plans' identities, names, contents, and booking associations; the previous primary becomes an alternative. A booked plan can be promoted. Promotion persists and does not create, change, or cancel a reservation.

### Independent plan management

- Users can edit, save, rename, create, copy, and delete individual plans subject to the booking and deletion rules below. Editing and saving an existing plan retains its identity and name unless the user explicitly renames it.
- Each plan owns its planning details and selections. Editing a plan's dates, travelers, or unconfirmed components must not change another plan or its confirmed reservations. Trip-level organization must not be used to silently propagate plan-specific edits across alternatives.
- Creating a plan adds an independent alternative without replacing an existing plan or changing which plan is primary. Copy is an explicit, optional action that creates a separately editable alternative and preserves its source. Copying must not duplicate purchases or give the new plan the source's confirmed booking associations; copied planning selections are unconfirmed.
- Saving targets the selected plan. Switching plans, copying, promoting, or deleting must not silently discard unsaved edits; provide a clear save/discard/cancel choice when an action would otherwise lose them. Surface save failures without showing unsuccessful edits as persisted.
- Preserve ownership and authorization boundaries. Concurrent changes must not silently overwrite another saved change, and a failed promotion or deletion must not leave a trip with zero or multiple primaries.

### Confirmed items and deletion

- Plans containing confirmed items remain editable for their unconfirmed parts. Confirmed flights, stays, rentals, and purchased details are clearly marked and locked against editing or removal through plan editing. Plan date/traveler changes cannot mutate confirmed item details or reservations. Display confirmed item details faithfully even when editable planning details differ.
- Block deletion of any plan containing confirmed items and explain why, for example: "This plan contains confirmed bookings and can't be deleted." Apply the same protection to deletion of the trip through the last-plan flow.
- When deleting a primary with other plans available, require the user to choose another existing plan as primary before completing deletion. Cancellation leaves both the plan and primary designation unchanged. Successful deletion leaves exactly one primary.
- When deleting the only remaining plan that is eligible for deletion, prompt: "This is the only plan for this trip. Delete the trip?" Offer Keep trip and Delete trip actions. Keep trip leaves the trip and plan unchanged; confirming performs trip deletion under existing trip-deletion eligibility rules rather than leaving an empty trip.
- Preserve existing cancellation workflows and booking history. Cancelling a booking does not erase history or automatically make a plan deletable; retain existing deletion restrictions for booking history. Existing restrictions on cancelled trips and historical booked items remain in effect. Do not introduce reservation amendment, rebooking, or new booking-history cleanup behavior in this ticket.

### Compare plans

- Rename the plan comparison entry point to Compare plans and position it beside the plan navigation so users do not need to scroll to a bottom options section.
- Include the primary plan and all alternatives in comparison selection. Enable comparison with a primary plus one alternative; users must not need two saved alternatives to compare.
- Compare two or three distinct plans side by side. When launched from an alternative tab, initially select that alternative and the primary; allow users to change the selection, including comparing alternatives without the primary. When launched from the primary, let the user choose the additional plan or plans.
- Show plan names and Primary/Booked indicators in comparison, along with dates, selections, and price summaries. Include incomplete plans with missing components labeled Not selected and partial totals explicitly identified; missing selections must not appear as a complete zero-cost itinerary.

### Existing trip compatibility

- Preserve existing trips, Working plan contents, saved option names and details, and booking/history associations when introducing the model. Use the existing Working plan as the initial primary and retain saved options as independent alternatives; do not silently choose a booked alternative as primary instead. Retain valid trips without a populated Working plan by establishing a primary without discarding their existing plan data or booking records.
- Existing links and navigation must continue to open the appropriate trip and retain access to its plans and booking details. Persisted primary choices and saved edits must survive refresh and reopening the trip.

## Acceptance criteria

- [ ] New and existing trips open with exactly one clearly labeled primary plan. Existing working contents, option names/details, and booking history remain available after the transition, including trips whose Working plan was incomplete or absent.
- [ ] The top plan navigation exposes the primary and named alternatives in a consistent workspace, with every plan reachable on narrow screens and through keyboard and assistive technology.
- [ ] Switching tabs changes only the viewed plan and never performs the old Working plan copy/replace operation or changes plan names.
- [ ] Editing, saving, and renaming a plan retain its identity and affect only that plan; changing dates, travelers, or unconfirmed selections preserves other plans and all confirmed item details.
- [ ] Create and Copy add independent alternatives without changing the source or primary; a copy does not inherit confirmed purchases or booking associations.
- [ ] Actions cannot silently discard unsaved edits. Save failures and conflicting concurrent changes remain visible and do not overwrite persisted data or another user's trip.
- [ ] Make primary works for an alternative, including a booked plan, persists across reopening, retains both plans and their booking associations, and leaves exactly one primary without changing reservations.
- [ ] Confirmed items are clearly marked and cannot be edited or removed. Unconfirmed parts remain editable, and confirmed details remain accurately displayed when planning details change.
- [ ] Deleting a plan with confirmed items is blocked with an explanation. Existing cancellation, booking-history, cancelled-trip, and historical booking restrictions remain enforced.
- [ ] Deleting a primary with alternatives requires selecting its replacement; cancelling preserves the original state, and success leaves one primary. Failure or concurrency conflicts cannot leave an invalid primary designation.
- [ ] Deleting the sole eligible plan offers Keep trip or Delete trip; Keep trip changes nothing, and Delete trip removes the trip only when existing trip-deletion rules permit it. Confirmed items or protected booking history cannot be bypassed through this flow.
- [ ] Compare plans is visible beside plan navigation and supports two or three distinct plans, including primary plus one alternative, with the agreed initial selection and selection controls.
- [ ] Comparison identifies names, primary/booked status, dates, selections, and prices; incomplete plans show missing selections and clearly labeled partial totals.
- [ ] Existing trip navigation and booking-detail access continue to work, and saved per-plan changes and primary designation survive refresh.

## Context

The current workspace has one editable Working plan and saved options further down the page. Opening an option for editing copies it into that Working plan. This ticket deliberately replaces that user model with independently managed plans; moving the existing option cards alone does not satisfy the outcome.

Related ticket: [Make My Trips predictable to scan and navigate](2026-10-01-organize-my-trips.md). The tickets can be planned separately; this workspace ticket owns the plan model and persistence changes. Coordinate terminology and summary data if the workspace changes affect My Trips. Neither ticket requires the other to ship first, but each must remain functional against the supported trip model.

Scrollable tabs with an All plans selector are a suggested way to handle overflow, not a prescribed implementation. Research and planning determine storage, migration, API, and component details while preserving the product behavior above.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Independent plan editing and persisted primary designation change plan lifecycle and data contracts. Compatibility, migration, concurrency, comparison, and confirmed booking protections require research, design, verification planning, implementation, and independent review.
- **Reassessment triggers:** Discovery of additional booking or legacy-data cases must be addressed within the full profile. Splitting implementation into smaller changes does not remove the lifecycle, persistence, or booking-protection requirements of the overall outcome.
