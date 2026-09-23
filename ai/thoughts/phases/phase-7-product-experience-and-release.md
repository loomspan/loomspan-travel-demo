# Phase 7 — Product Experience and Release

## Outcome

DeTour presents a cohesive, accessible, responsive experience; explains its limited fictional dataset in one intentional location; and contains no obsolete Wayfarer/Loomspan/demo workflow.

## Work packages

### 7.1 Establish DeTour visual system

- Design a clean visual identity around the DeTour name.
- Establish typography, color, spacing, focus, status, warning, and component conventions.
- Keep the interface calm by displaying optional scope only after user action.
- Use consistent trip, itinerary, status, price, and warning language.

### 7.2 Build navigation and responsive behavior

- Provide clear access to Home, Profile, the active trip/itinerary, and logout.
- Preserve autosaved context when navigating between component selection pages.
- Make profile nesting and comparison usable on narrow screens without hiding information.
- Add purposeful empty states, loading states, save status, and retry paths.

### 7.3 Add the single demo disclosure

- Create a collapsed side tab available on public and authenticated pages.
- Include fictional-data/no-real-booking disclosure, supported origin/destinations/dates, and concise usage instructions.
- Ensure the tab is keyboard accessible, focus-contained while open, dismissible, and non-blocking.
- Remove all other routine demo badges, walkthroughs, controls, footers, and source-facing product copy.

### 7.4 Accessibility and quality pass

- Verify keyboard-only workflows, semantic headings, input labels, error summaries, focus placement, dialogs/drawers, tables, and status announcements.
- Check color contrast, zoom, reduced motion, and mobile layouts.
- Test time-zone and date presentation for all destinations.
- Verify that filters, warnings, budget treatment, and booking confirmations are understandable without relying on color alone.

### 7.5 Documentation and release verification

- Replace README setup, configuration, architecture, fixture, and verification guidance.
- Document the explicit development database reset and clean-break policy.
- Verify that owning implementation tickets already removed obsolete Wayfarer skills, scripts, tests, scenario documents, generated artifacts, routes, schemas, and compatibility paths; Phase 7 must not defer their removal until release cleanup.
- Run backend, frontend, integration, concurrency, accessibility, packaged-application, restart, and clean-database verification.
- Search executable code, configuration, tests, generated artifacts, and product-facing copy for forbidden Loomspan, Wayfarer, obsolete route, compatibility, and misplaced demo references. Historical planning records may identify the former system but are not implementation authority.

## Exit criteria

- A first-time user can register and complete a representative plan without external instructions.
- The same core workflows work on desktop and mobile with keyboard-only navigation.
- The About this demo tab is the only intentional product-level demo disclosure.
- The application builds, tests, packages, restarts, and preserves users/bookings without model credentials.
- Repository documentation describes DeTour rather than the removed application.

## Annotations

- **[DECIDED]** Refine the existing teal palette, Georgia headings, and text-only DeTour wordmark. A separate logo is not required for the first release. Consolidate these into a consistent visual system and verify contrast and focus treatments.
- **[DECIDED]** Keep modal dialogs for destructive confirmations. Make their wording, focus behavior, keyboard operation, and dismissal rules consistent.
- **[DECIDED]** Phase 7 ends with a release-ready packaged application and verification evidence; deployment is outside this phase.
- **[PROPOSED COPY]** Public introduction: “Plan a trip your way. Start with airfare, a stay, or a complete itinerary. Save alternatives, compare total costs, and choose what works for you.” The existing login/register action labels remain functional copy.
- **[PROPOSED COPY]** About this demo: “DeTour uses fictional suppliers, schedules, prices, availability, and bookings. No payment is collected and no real reservation is made. Trips depart from PDX, travel to San Francisco, Munich, or Mexico City, and use dates in March 2027. Start with Plan Trip, Airfare, or Stay. Add other components when you want them, save a Draft as Planned, compare up to three Planned itineraries, and review one to book. An active Booking can be canceled before its departure date begins in Portland time.” Final wording remains to be reviewed during the Phase 7 copy pass.
- **[CLARIFICATION]** The About tab is the sole general product-level demo disclosure. Keep the explicit fictional-booking notice at booking review required by Phase 6.
- **[FUTURE]** Native mobile apps, offline planning, localization, dark mode, marketing pages, and public catalog browsing.
