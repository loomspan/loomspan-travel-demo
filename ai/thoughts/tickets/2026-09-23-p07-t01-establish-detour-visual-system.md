# P07-T01 — Establish a Consistent DeTour Visual System

## Outcome

DeTour feels like one coherent travel product across authentication, profile, planning, comparison, booking, and cancellation, so users can recognize actions, prices, states, and warnings without relearning each screen.

## Requirements

- Refine the existing teal palette, Georgia headings, and text-only DeTour wordmark. A separate logo is not required for the first release. Establish consistent typography, color, spacing, component, focus, status, and warning treatments across the application.
- Preserve progressive disclosure: airfare, stays, and cars appear in a Draft only when the entry flow or user action calls for them. Visual polish must not surface optional forms by default.
- Use consistent names for Trip, Draft, Planned itinerary, Booked itinerary/Booking, Canceled Booking history, Expired, and Canceled Trip. Distinguish missing components from zero-cost components, and retain clear component totals, grand totals, budget remaining/overage, and USD amounts wherever selections are shown.
- Preserve the existing modal confirmation pattern for destructive actions. Distinct Delete Draft, Delete Planned itinerary, Delete Trip, Cancel Booking, and Cancel Trip labels and scopes remain intact. The full dialog interaction audit belongs to P07-T04.
- Do not change deterministic pricing, eligibility, booking, cancellation, or authorization behavior as a byproduct of styling. Dark mode and a new logo are outside this release.

## Acceptance criteria

- [ ] Public and authenticated screens share a recognizable DeTour wordmark, typography, spacing, color, and component language without introducing a separate logo.
- [ ] A user can identify primary and destructive actions, selection status, saving state, warnings, and budget overage from text and visual treatment; color is never the sole signal.
- [ ] Trip, itinerary, component, and money labels remain consistent from the builder through comparison, booking review, confirmation, and history.
- [ ] The three entry flows and optional component reveal behavior remain progressive, and representative workflows retain their current server-backed results.

## Context

- **Phase/work package:** Phase 7, 7.1. Authoritative sources: [roadmap](../phases/README.md) and [Phase 7](../phases/phase-7-product-experience-and-release.md).
- **Hard dependency:** Phases 1–6 are implemented. This ticket may run alongside P07-T02 and P07-T03; P07-T04 audits their combined result.
- **Scope boundary:** This ticket establishes visual and terminology consistency. P07-T02 owns navigation and layout behavior; P07-T03 owns final public/disclosure copy; P07-T04 owns the cross-application accessibility audit.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change spans most production screens and requires design decisions about a shared visual system, even though the brand direction is settled.
- **Reassessment triggers:** none.
