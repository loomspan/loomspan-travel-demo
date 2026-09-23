# P07-T02 — Deliver Clear Navigation and Responsive Trip Context

## Outcome

Travelers can move among Home, Profile, their active Trip and itinerary, and logout without losing Draft context or information on narrow screens.

## Requirements

- Provide clear authenticated navigation to Home, Profile, the current Trip/itinerary when one is open, and logout. Preserve the trip and Draft a user is editing when they move among component selection views; autosaved selections remain visible when they return.
- Keep Plan Trip, Airfare, and Stay as the authenticated Home entry points. Additional components become available through explicit user actions, not through navigation that silently expands a Draft.
- Make profile nesting, alternative cards, component search and selection, booking review, cancellation history, and comparison usable on desktop and mobile without hiding required information or actions.
- Comparison remains limited to three Planned alternatives from one Trip. Desktop uses columns; mobile uses a stacked comparison with a persistent itinerary selector.
- Provide purposeful empty, loading, saving, saved, failure, and retry states for the affected navigation and Draft workflows. An autosave failure must remain apparent and recoverable without suggesting unsaved data was persisted.
- Preserve owner isolation, server validation, immutable Planned/Booked snapshots, and existing Trip lifecycle rules. Do not introduce public catalog browsing or a new routing contract solely for presentation.

## Acceptance criteria

- [ ] From each authenticated primary screen, a user can reach Home and Profile, return to an open Trip/itinerary, and log out with clearly named controls.
- [ ] Navigation among airfare, stay, and rental views preserves the active Draft context and shows authoritative saved selections and tally after a refresh or return.
- [ ] A first-time user can start a trip from each Home entry point and understands empty and loading states; save failure is visible with a usable retry path.
- [ ] Profile and Trip information and all core actions remain usable at narrow mobile widths without horizontal loss of required content.
- [ ] Comparison shows up to three Planned alternatives in columns on desktop and as stacked content with a persistent selector on mobile; absent components are distinct from zero-cost ones.

## Context

- **Phase/work package:** Phase 7, 7.2. Authoritative sources: [roadmap](../phases/README.md), [Phase 7](../phases/phase-7-product-experience-and-release.md), and [Phase 5 comparison rules](../phases/phase-5-planning-budget-and-comparison.md).
- **Hard dependency:** Phases 1–6 are implemented. This ticket may run alongside P07-T01 and P07-T03; P07-T04 audits their combined result.
- **Scope boundary:** P07-T01 owns shared visual conventions; P07-T03 owns disclosure/copy; P07-T04 owns final keyboard and assistive-technology review.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Navigation and responsive behavior span the application, and context preservation can affect user-visible Draft state.
- **Reassessment triggers:** none.
