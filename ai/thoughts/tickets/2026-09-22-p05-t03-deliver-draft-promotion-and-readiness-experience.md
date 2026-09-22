# P05-T03 — Deliver Draft Promotion, Actionable Readiness, and Budget Overage Acknowledgment Experience

## Outcome

Authenticated users can promote ready Drafts into immutable Planned snapshots directly from the progressive builder and Alternative cards, receive actionable visual feedback directing them to incomplete components when promotion is blocked, review and explicitly acknowledge budget-overage warnings in an accessible modal dialog, and observe real-time tally and alternative state transitions.

## Requirements

- Expose Draft Promotion action:
  - Add a prominent "Save as Planned Itinerary" / "Promote to Planned" button in the Progressive Trip Builder workspace and on each Draft `AlternativeCard`.
  - Disable the action or provide visual readiness indications when the draft is unready or trip is expired.
- Present actionable readiness feedback:
  - If a user attempts to promote an unready Draft (or requests readiness status), present an accessible readiness summary banner or dialog listing every blocking issue returned by the server.
  - Each issue must include a direct jump/focus action that guides the user to the relevant form field or component slot:
    - **Missing traveler ages:** focuses the first empty age input in Trip Details.
    - **Missing adult (no traveler 18+):** moves focus to the age inputs and highlights the adult requirement.
    - **Missing budget:** focuses the budget input in Trip Details.
    - **No selected components:** scrolls and focuses the progressive builder component slots.
    - **Stale or sold-out component:** highlights the affected component slot (Airfare, Stay, or Rental Car) with options to replace or remove it.
- Budget-overage warning and acknowledgment flow:
  - If the Draft exceeds the overall trip budget, intercept promotion with a dedicated, accessible confirmation modal.
  - The modal displays:
    - Overall trip budget.
    - Itinerary grand total.
    - Exact budget overage amount clearly highlighted.
  - Require the user to check an explicit acknowledgment checkbox (*"I understand this itinerary exceeds my overall trip budget"*) before the "Confirm and Save as Planned" button becomes enabled.
  - Submitting sends `budgetOverageAcknowledged: true` in the promotion request.
  - If the user closes the modal and edits any component, traveler, date, price, or budget setting, reset the acknowledgment state so any subsequent promotion requires fresh acknowledgment.
- Lifecycle transitions and real-time state synchronization:
  - Upon successful promotion, update the workspace state: the newly created Planned alternative appears immediately under the Alternatives list with a `Planned` badge, and the persistent summary tally reflects the authoritative server numbers.
  - Planned alternatives render with distinct styling, read-only indicators, and actions for "Duplicate to draft" and "Delete planned itinerary" (with confirmation modal).
  - Provide clear `aria-live` polite status announcements for saving, successful promotion, and validation errors.
  - Gracefully handle version conflicts (409 `VERSION_CONFLICT`) by preserving user input and offering a "Reload from server" action.
- Ensure desktop and mobile responsiveness and full keyboard accessibility (`aria-modal`, focus trapping, Escape key closing, error announcements).

## Acceptance criteria

- [x] "Promote to Planned" action is accessible in the progressive trip builder and Draft alternative cards.
- [x] Incomplete Drafts display actionable blocking issues, and clicking an issue moves focus directly to the missing field or component slot.
- [x] Over-budget Draft promotion displays a warning modal showing budget, grand total, and overage, requiring explicit checkbox acknowledgment before enabling confirmation.
- [x] Successfully promoted Drafts appear immediately as Planned snapshots with read-only badges and duplicate/delete actions.
- [x] In-place modifications to Planned snapshots are prevented, and duplicating a Planned snapshot produces a new mutable Draft.
- [x] Form edits following an overage warning invalidate previous client acknowledgment and require fresh acknowledgment on subsequent promotion attempts.
- [x] Keyboard navigation, focus trapping, Escape key dismissal, and `aria-live` status announcements are verified in Vitest tests.
- [x] Responsive tests confirm accessible layout and functionality on both desktop and mobile viewports.

## Context

- **Phase/work packages:** Phase 5 — Planning, Budget, and Comparison; user-facing integration of work package 5.2 (Validate readiness) and work package 5.3 (Save stable Planned snapshots).
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-5-planning-budget-and-comparison.md`](../phases/phase-5-planning-budget-and-comparison.md).
- **Hard dependencies:** P05-T01 (Canonical Pricing and Server Tally Engine) and P05-T02 (Authoritative Readiness and Planned Snapshots) must be complete.
- **Downstream dependencies:** P05-T04 consumes the generated Planned snapshots for multi-alternative comparison.
- **Scope exclusions:** Multi-alternative comparison columns, simulated booking checkout/reservation, and Version 2 Events.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Integrates promotion workflows, dynamic readiness navigation, modal focus management, overage acknowledgment invalidation, and state synchronization across the progressive builder and alternative cards.
- **Reassessment triggers:** If backend readiness response structures differ from the anticipated schema, align the TypeScript API client types before writing UI components.
