# P07-T03 — Finalize Public Copy and the Single Demo Disclosure

## Outcome

First-time visitors understand what DeTour helps them do, and everyone can find the fictional-data limits and usage instructions in one accessible About this demo tab.

## Requirements

- Add a concise introduction to the public login/registration experience. Use the agreed tone and this proposed wording as the editorial baseline: “Plan a trip your way. Start with airfare, a stay, or a complete itinerary. Save alternatives, compare total costs, and choose what works for you.” Preserve clear login and registration action labels.
- Provide one collapsed **About this demo** side tab on public authentication and authenticated pages. Its final copy must explicitly state that suppliers, schedules, prices, availability, and bookings are fictional; no payment or real reservation occurs; the origin is PDX; destinations are San Francisco, Munich, and Mexico City; and travel dates are limited to March 2027.
- Explain the actual workflow concisely: start with Plan Trip, Airfare, or Stay; explicitly add components; save a Draft as Planned; compare up to three Planned alternatives; review one to book; and cancel an active Booking only before the departure date begins in the PDX `America/Los_Angeles` timezone. The proposed Phase 7 copy is a starting draft, not permission to change these facts.
- The side tab must be keyboard accessible, dismissible with Escape, restore focus to its trigger on close, manage focus within its open panel, and remain non-blocking for the rest of the page. The exact panel mechanism may follow existing component conventions if these outcomes are met.
- The About tab is the sole general product-level demo disclosure. Remove routine demo badges, walkthroughs, controls, footers, and misplaced general demo copy. Preserve the explicit fictional-booking notice in booking review required by Phase 6 and concise transaction-specific clarification where needed.
- Do not add marketing pages, public catalog browsing, external suppliers, or real payment/reservation claims.

## Acceptance criteria

- [x] Login and registration display a brief, accurate DeTour introduction and retain clear account actions.
- [x] The collapsed About this demo tab is available on public and authenticated pages and conveys every required fictional-data, geography, date, and workflow fact.
- [x] Keyboard users can open, read, dismiss, and leave the tab predictably, with focus restored on close and the rest of the page usable.
- [x] No other general product-level demo explanation appears in routine application screens; booking review still states the transaction is simulated and collects no payment.

## Context

- **Phase/work package:** Phase 7, 7.3. Authoritative sources: [roadmap](../phases/README.md), [Phase 7 and proposed copy](../phases/phase-7-product-experience-and-release.md), and [Phase 6 booking review](../phases/phase-6-booking-and-cancellation.md).
- **Hard dependency:** Phases 1–6 are implemented. This ticket may run alongside P07-T01 and P07-T02; P07-T04 audits the final interaction.
- **Scope boundary:** Final copy may be edited for clarity and brevity while preserving all listed facts. The separate booking-review notice is an intentional exception to the single general disclosure rule.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** medium
- **Rationale:** The affected public copy and disclosure component are bounded, the required facts are settled, and independent review can catch inaccurate wording or focus behavior.
- **Reassessment triggers:** Discovery that removing misplaced disclosures requires broad workflow changes or that the panel interaction changes shared navigation behavior.

## Execution notes

- Kept the About panel non-modal so Tab can continue into the page. Opening it focuses its heading; Escape while focus is in the tab, its close button, and its trigger restore focus to the trigger on close. Moving the existing tab before page content in DOM order gives keyboard users a natural path from the panel into the screen without changing shared navigation behavior.
- Removed redundant simulated-booking eyebrow copy from booking review and confirmation while retaining the required booking-review disclosure. The confirmation disclosure and cancellation clarification describe their specific transactions and remain in place.
