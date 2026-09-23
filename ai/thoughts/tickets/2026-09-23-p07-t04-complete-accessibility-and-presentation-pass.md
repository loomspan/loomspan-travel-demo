# P07-T04 — Make Core Workflows Accessible and Unambiguous

## Outcome

A traveler can register, plan, compare, book, and cancel on desktop and mobile using a keyboard and assistive technology, with dates, prices, warnings, and state changes presented unambiguously.

## Requirements

- Audit and correct the complete first-time path and its key variants: registration/login, Home entry points, Trip and Draft creation, each component search and filter, autosave, readiness and budget warnings, Planned promotion, comparison, booking review and confirmation, cancellation, and profile/history.
- Ensure semantic headings, programmatic input labels and descriptions, actionable error summaries, sensible focus placement and restoration, keyboard-operable controls, dialogs and the About panel, comparison semantics, and meaningful status announcements. Preserve modal destructive confirmations with consistent focus containment and dismissal rules; pending destructive actions must not accidentally dismiss.
- Check contrast, visible focus, 200% zoom, reduced-motion preference, and narrow mobile layouts. Do not rely on color alone for availability, validation, filters, warnings, booking status, or budget overage.
- Show flight departure and arrival in the relevant local zones and dates unambiguously for San Francisco, Munich, and Mexico City; distinguish origin and destination local times and date changes. Preserve the fixed PDX departure-timezone rule for expiration and cancellation.
- Keep authoritative USD component totals, selected grand total, and budget position understandable at each selection and review point. A missing optional component must not appear to cost $0.
- Correct defects discovered in this pass without changing settled planning, inventory, authorization, or lifecycle rules. Route material contract changes back through the pipeline's reassessment process.

## Acceptance criteria

- [ ] A keyboard-only user can complete the representative registration-to-booking path and Cancel Booking path on desktop and narrow mobile layouts, with no focus trap or unreachable action.
- [ ] Errors, autosave results, warnings, booking outcomes, and cancellation outcomes are announced and direct users to the next useful action.
- [ ] Destructive modals, the About panel, and comparison controls have predictable focus, Escape, and return-focus behavior; screen-reader labels distinguish actions and states.
- [ ] Core screens remain legible and operable at 200% zoom, with reduced motion honored and text or icons conveying all color-coded meaning.
- [ ] Representative schedules for each destination clearly show local date, time, and zone for both ends of travel, including an overnight or date-changing itinerary.
- [ ] Prices and missing components retain the same meaning through builder, comparison, booking review, and history.

## Context

- **Phase/work package:** Phase 7, 7.4 and cross-cutting release requirements. Authoritative sources: [roadmap](../phases/README.md) and [Phase 7](../phases/phase-7-product-experience-and-release.md).
- **Hard dependencies:** P07-T01, P07-T02, and P07-T03 should be complete so this pass verifies the final presentation. P07-T05 consumes its results.
- **Scope boundary:** This is a whole-workflow accessibility and presentation pass, not a new trip feature or a change to server-owned pricing, availability, or authorization.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The assurance and likely corrections cross most production workflows; a narrow review would miss interactions between dialogs, status messages, responsive layout, and schedule display.
- **Reassessment triggers:** none.
