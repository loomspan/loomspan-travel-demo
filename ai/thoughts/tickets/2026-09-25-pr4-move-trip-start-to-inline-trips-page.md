# pr4 — Start named trips from an inline Trips page

## Outcome

Travelers can find their trips in a dedicated Trips area and start a named trip in a clear inline form. They provide the dates and the complete travel party together so recommendations use the right travelers from the beginning.

## Requirements

- Move the Trip list and trip-opening actions out of Profile into a persistent Trips destination in the main navigation. Profile contains account information and account actions. Use Trips as the navigation label because the area includes work in progress, Saved options, and bookings.
- Home's Plan Trip entry opens the Trips page's inline Start a trip section rather than a modal. Airfare and Stay entry points may preselect the first component to explore, but they use the same trip-start form and must not save a Trip simply by being opened.
- The inline form collects an editable Trip name, destination, start and end dates for the initial Working plan, traveler count, and one required whole-number age for each traveler. Changing the count adjusts the visible age fields without silently changing the remaining entries. Offer a useful editable name suggestion; do not require unique names or expose technical IDs as user-facing titles.
- The traveler count and ages are shared across the Trip's Working plan and Saved options. Budget may be entered initially but is optional at this step. The supported destination and travel-date rules remain in force.
- For a signed-out visitor, keep entered values in the client through the login/registration handoff specified in `2026-09-25-pr1-open-home-and-gate-trip-saving.md`. No Trip is persisted until the authenticated user explicitly continues. For a signed-in visitor, Start planning saves the entered Trip and opens its single Working plan on the same page.
- The Trips list leads with the user-entered Trip name and destination and summarizes Working plan and Saved-option state. Do not show one date range as though it applies to all Saved options, because options may have different dates. Renaming a Trip changes its title without creating another Trip or option.
- Keep the form and navigation usable by keyboard, assistive technology, and on narrow screens, with field-level validation and a clear save/authentication failure state.

## Acceptance criteria

- [ ] Home, Trips, and Profile have distinct destinations; Trip lists no longer live in Profile, and guests do not see private Trip data.
- [ ] Plan Trip, Airfare, and Stay enter the same inline trip-start flow with no creation pop-up and no automatic Trip save on opening.
- [ ] The user can set a Trip name, destination, dates, traveler count, and every traveler's age together; invalid or incomplete details cannot start planning.
- [ ] Start planning creates one owned Trip with its single Working plan and keeps the user on the Trips page; a canceled or unfinished start creates no Trip.
- [ ] A guest can complete the start form, authenticate, and continue without re-entering details or creating duplicate Trips.
- [ ] The Trips list distinguishes named Trips from named Saved options and does not imply that every option shares one date range. Trip renaming leaves alternatives and bookings attached to the same Trip.
- [ ] The flow remains accessible and usable at desktop and mobile widths, and save errors do not claim that data was persisted.

## Context

- Depends on `2026-09-25-pr1-open-home-and-gate-trip-saving.md` for the guest authentication handoff and `2026-09-25-pr2-model-named-trips-and-dated-options.md` for the named Trip and dated Working-plan contract. Component selection and Saved-option controls are delivered by the later save-workflow and workspace tickets.
- Today the creation modal asks for traveler count but collects ages only later in the Trip workspace. The current backend accepts ages on creation, though the UI does not send them. This is context for planning, not a requirement to preserve that split.
- Scope excludes changing the set of supported destinations, the fictional catalog travel window, account fields, and Saved-option comparison behavior.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Navigation, trip creation, authentication handoff, and responsive form behavior cross multiple screens and persisted request contracts.
- **Reassessment triggers:** none.
