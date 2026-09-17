# DeTour Product Roadmap

## Status

This is the durable roadmap for replacing the Wayfarer application with DeTour. It records the product decisions established during discovery and divides the work into dependency-ordered delivery phases. Detailed implementation tickets should be written from the relevant phase document rather than from the former Wayfarer behavior.

For continuation in a new working context, read this file completely and then read [CONTINUATION.md](CONTINUATION.md). The continuation guide records repository state, non-obvious constraints, remaining decisions, and the next expected planning work.

Last updated: 2026-09-17.

## Annotation legend

- **[OPEN QUESTION]** A product or technical decision still needs an answer before the affected ticket is implementation-ready.
- **[UNDECIDED]** The capability is in scope, but an exact value, presentation, or implementation choice has not been selected. A ticket may propose a default, but must not silently assume one.
- **[FUTURE]** A deliberately deferred enhancement. It is not part of the initial DeTour release and must not expand the current implementation scope.

## Product vision

DeTour is a clean, deterministic travel-planning application for small or large trips. It lets authenticated users assemble airfare, accommodations, and rental cars one component at a time; save multiple alternatives for the same trip; compare them; and complete a simulated booking against fictional local inventory.

The interface must reveal additional scope only when the user asks for it. Optional cars must not clutter a simple airfare or stay workflow. Prices, availability, eligibility, ranking, and booking behavior are application-owned rules, not model decisions.

## Settled product decisions

### Brand and application boundary

- The product name is **DeTour**. Use `detour` for technical identifiers unless a language convention requires another form.
- Use `app.detour` as the Java package namespace.
- Remove Loomspan completely: Maven dependencies, imports, annotations, configuration, YAML skills, tests, scripts, UI explanations, observability, and documentation.
- Do not replace Loomspan with another AI or model integration. Planning, ranking, explanations, and validation are deterministic application logic.
- Remove ordinary references to the product being a demo. A single collapsed **About this demo** side tab is the explicit exception.
- Existing Wayfarer database files and Boston–New York data are not compatible and will not be migrated. DeTour uses a new H2 database and fresh Flyway history.

### Users and profiles

- Login is required to browse or plan.
- Support self-service registration, login, logout, and per-user data isolation.
- Each new user starts with an empty profile.
- There is no administrator account, role model, admin page, email verification, password-reset email, or social login in the initial release.
- Passwords are 12–128 characters with no forced character-class rules. Registration provides a show/hide control.
- Users may change their password after supplying the current password. Profile email is immutable in the initial release.
- Authentication sessions do not survive an application restart; persistent user and trip data do.
- Profiles group trips into Upcoming and Past based on dates, expose Draft, Planned, Booked, and Expired itinerary information, and retain Canceled Booking history as applicable.
- PDX is the fixed origin for this release.

### Trip and itinerary model

- A **trip** owns destination, dates, travelers, overall budget, and all itinerary alternatives for that travel intent.
- An **itinerary** owns selected airfare, stay, rental car, and calculated totals.
- A trip may have multiple Draft and Planned itineraries but at most one active Booked itinerary.
- Destination, start/end dates, and traveler count are required before a Trip and its first Draft are created. The Draft may initially contain no selected components; traveler ages and budget may be completed before promotion to Planned.
- Draft itineraries autosave and may be incomplete after those initial shared details are supplied.
- Users may start a component-empty Draft or explicitly duplicate an existing Draft, Planned, Booked itinerary, or Canceled Booking snapshot into a new Draft.
- Saving as Planned creates a stable snapshot. A Planned itinerary is changed only by duplicating it into a new Draft.
- Booking starts from a valid Planned itinerary.
- Unselected Planned alternatives remain visible after another itinerary is booked.
- Users may continue creating Draft and Planned alternatives after booking, but may not book a second itinerary while the trip has an active booking.
- A Booked itinerary is immutable. It may be canceled or duplicated into a new Draft.
- Destination, dates, and travelers are shared trip details. If a trip already has a Planned itinerary, changing those details creates a new Trip rather than modifying it. The user chooses which Planned snapshots to use as sources; each selected snapshot becomes a Draft in the new Trip. Components incompatible with the revised shared details are omitted, and the user receives a summary of everything removed. The same rules apply when duplicating a canceled Trip.
- Trip labels are derived from destination and dates; custom nicknames are not in the initial release.
- Upcoming and Past are date-derived views, not persisted lifecycle states.

### Entry points and progressive scope

- The authenticated home page offers **Plan Trip**, **Airfare**, and **Stay**.
- Plan Trip begins with airfare and one accommodation-type preference, then offers rental car explicitly.
- Airfare and Stay begin with only the fields relevant to that component after collecting the mandatory destination, dates, and traveler count.
- A Draft may expand only through explicit actions such as Add airfare, Add a stay, or Add a car.
- A Draft requires at least one selected reservable component before it can become Planned or Booked.

### Supported travel

- Origin: Portland International Airport (`PDX`).
- Destinations: San Francisco, Munich, and Mexico City.
- Supported trip dates: March 1 through March 31, 2027. Every selected component must fall within that window. Fixture guarantees apply only to direction/date combinations that can participate in a valid 1–14-night Trip and complete arrival within March.
- Do not offer a flight whose final arrival is after March 31, 2027, even when it departs within the supported window.
- Trip length: 1–14 nights.
- Party size: 1–8 travelers.
- Capture each traveler's exact age for the trip.
- Derived age bands: infant 0–1, child 2–12, teen 13–17, adult 18+.
- At least one traveler must be 18 or older.
- A rental car requires at least one traveler aged 25 or older.

### Components and ranking

- Airfare is round trip and supports fictional direct and one-stop inventory for each destination.
- Each destination has two direct and two one-stop choices for every direction/date combination usable by a valid Trip. San Francisco connections use Seattle or Salt Lake City; Munich uses Seattle or Chicago; Mexico City uses Los Angeles or Dallas–Fort Worth.
- Every traveler reserves a seat and pays the same airfare. Age does not change airfare in the initial release.
- A **Direct flights only** filter is available. Without it, the default order is direct before connecting, then lowest complete-party fare, then shortest duration.
- Users can override flight sorting with lowest price, shortest duration, earliest departure, or fewest stops.
- Every default and user-selected sort ends with an immutable catalog identifier as the final tie-breaker.
- Whole-trip accommodation starts with exactly one preferred type: hotel, B&B, or vacation rental.
- Stay results default to options that fit the available trip budget, then highest guest rating, lowest complete-stay price, and nearest city center. Users may instead sort by lowest total price, highest guest rating, or nearest city center.
- Hotels and B&Bs automatically calculate required rooms from party size and room capacity. Vacation rentals are whole properties with a maximum guest capacity.
- Rental cars are picked up and returned at the destination airport. Users supply local pickup and return date/times within the Trip's start/end-date interval, and return must be after pickup. Duration and price use consecutive 24-hour billing cycles, rounding any partial final cycle up to a full cycle. Displayed totals include taxes and fees. Initial classes are economy, standard, and SUV.
- Rental results default to economy, standard, then SUV; within a class they sort by lowest complete total and then immutable catalog identifier.

### Budget behavior

- The overall budget is a planning target, not a booking prohibition.
- The budget may be zero but not negative. Apply a documented upper bound that is safely representable in the application's integer-cent money type and reject values above it.
- Every screen with selections shows the component tally, actual selected total, and remaining budget or overage.
- A component search calculates available trip budget as the overall budget minus the authoritative totals of already selected components other than the component being searched or replaced.
- Budget overages warn but do not block planning or booking.
- The booking grand total includes every selected paid component.

### Booking and cancellation

- Simulated booking collects no payment or billing address.
- Confirmation reserves applicable flight seats, rooms/properties, and rental inventory atomically.
- Booking produces fictional confirmation references and persists across restart.
- Use **Delete Draft** and **Delete Planned itinerary** for unfinished alternatives. Both require appropriate confirmation, remove only that alternative, and have no inventory effect.
- Use **Cancel Booking** for an active reservation. It requires confirmation, charges no fee, restores all reserved inventory atomically, retains booking history, and leaves the Trip active.
- After Cancel Booking, offer **Use a saved alternative**, **Create a new Draft**, and **Done for now**. Using a saved alternative duplicates it into a Draft for current price and availability validation; it is not booked directly.
- Use **Delete Trip** only when the Trip has never had a Booking. Confirmation lists the Draft and Planned alternatives that will be permanently removed. There is no inventory effect.
- Use **Cancel Trip** when booking history exists. If a Booking is active, cancel the Booking and close the Trip in one atomic operation; failure to release the Booking leaves the Trip open. If no Booking is active, close the Trip without another inventory operation.
- A canceled Trip retains every booking/cancellation record, makes Draft and Planned alternatives read-only, appears under Canceled Trips, and offers **Duplicate into a new Trip** rather than reopen.
- Cancel Booking and Cancel Trip are available only before the Trip becomes Expired. An Expired or Past Booking is immutable history and never restores past inventory.
- Booking changes, exchanges, supplier disruptions, and recovery flows are deferred.

## Demo disclosure exception

The collapsed **About this demo** side tab must be available on public authentication pages and authenticated application pages. It explains:

- suppliers, schedules, prices, availability, and bookings are fictional;
- no payments or real reservations occur;
- origin is limited to PDX;
- destinations are limited to San Francisco, Munich, and Mexico City;
- travel dates are limited to March 2027; and
- how to create, expand, compare, book, and cancel an itinerary.

No other routine product copy should describe DeTour as a demo.

## Delivery sequence

| Phase | Outcome | Depends on |
| --- | --- | --- |
| [0. Baseline and boundaries](phase-0-baseline-and-boundaries.md) | Agreed replacement boundary and protected starting point | None |
| [1. Platform reset and identity](phase-1-platform-reset-and-identity.md) | DeTour runs without Loomspan and supports isolated user accounts | Phase 0 |
| [2. Catalog and fixture data](phase-2-catalog-and-fixtures.md) | March 2027 inventory exists for every supported component | Phase 1 |
| [3. Trips, itineraries, and profile](phase-3-trips-itineraries-and-profile.md) | Users can manage autosaved alternatives under upcoming and past trips | Phases 1–2 |
| [4. Component selection](phase-4-component-selection.md) | Users can select airfare, stays, and cars progressively | Phases 2–3 |
| [5. Planning, budget, and comparison](phase-5-planning-budget-and-comparison.md) | Alternatives can be promoted, compared, and reviewed for booking | Phase 4 |
| [6. Simulated booking and cancellation](phase-6-booking-and-cancellation.md) | Inventory-safe booking and fee-free cancellation work end to end | Phase 5 |
| [7. Product experience and release](phase-7-product-experience-and-release.md) | DeTour is polished, accessible, documented, and free of obsolete behavior | Phases 1–6 |

## Cross-cutting release requirements

- Authorization is enforced in repositories/services, not only hidden in the UI.
- All client-supplied prices, totals, availability, ownership, and eligibility are revalidated on the server.
- Inventory mutation is transactional, concurrency-safe, and idempotent.
- Autosave must make its saving, saved, and failure states apparent without interrupting the user.
- Time-zone-aware schedules display origin and destination local times unambiguously.
- Currency is USD throughout the initial release.
- Desktop and mobile layouts must preserve the same functionality.
- Keyboard access, labels, focus management, error summaries, and meaningful status announcements are acceptance criteria.
- Tests use controllable time so Upcoming, Past, and Expired behavior does not depend on the machine clock.
- A Trip and its unbooked alternatives become Expired at the start of the departure date in the departure location's timezone. For the fixed PDX origin, this is the `America/Los_Angeles` timezone.
- Users may compare at most three Planned itineraries at once. Desktop uses columns; mobile uses a stacked comparison with a persistent itinerary selector.
- Warning acknowledgments are invalidated whenever an affected selection, traveler, date, price, or budget setting changes.

## Global unresolved items

- **[OPEN QUESTION]** What visual identity, colors, typography, and wordmark/logo treatment should DeTour use?
- **[OPEN QUESTION]** What is the final public authentication-page and About this demo copy?
- **[UNDECIDED]** Exact fictional supplier/property names, schedules, capacities, prices, ratings, and locations will be designed in Phase 2.

## Deferred enhancement register

- **[FUTURE]** Additional origins, destinations, currencies, date ranges, and live supplier integrations.
- **[FUTURE]** Administrator accounts and catalog-management UI.
- **[FUTURE]** Email verification, password reset, social login, multifactor authentication, and account recovery.
- **[FUTURE]** Custom trip nicknames, collaboration, sharing, approvals, and group voting.
- **[FUTURE]** Booking modification/exchange, cancellation penalties, supplier disruption, and recovery workflows.
- **[FUTURE]** Multiple cabins, fare families, multi-city/open-jaw travel, more than one stop, and mixed-airline ticketing.
- **[FUTURE]** Loyalty programs, real payments, traveler identity documents, and supplier confirmation.
- **[FUTURE]** Rental pickup locations other than the destination airport, multiple vehicles, and detailed driver assignment.
- **[FUTURE]** Event discovery, planning, allowance, and booking are defined in the [Version 2 Events roadmap](../future/version-2-events.md).
- **[FUTURE]** Maps, travel time routing, and live distance services.
- **[FUTURE]** AI-generated plans, explanations, or conversational changes.
