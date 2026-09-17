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

- **[OPEN QUESTION]** Select visual identity, colors, typography, and whether DeTour needs a logo beyond its wordmark.
- **[OPEN QUESTION]** Finalize the exact demo-drawer instructions and disclosure copy.
- **[UNDECIDED]** Decide whether destructive actions use modal dialogs or inline confirmation patterns as part of UX design.
- **[FUTURE]** Native mobile apps, offline planning, localization, dark mode, marketing pages, and public catalog browsing.
