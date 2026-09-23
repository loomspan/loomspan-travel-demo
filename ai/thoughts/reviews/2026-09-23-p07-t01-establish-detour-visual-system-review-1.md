# P07-T01 Code Review — Cycle 1

## Scope and Repository State

Reviewed the ticket, research, implementation and testing plans, design lens, all staged/unstaged changes against `main` at `a71c695`, and the untracked `VisualSystem.test.tsx` plus ticket artifacts. The change is confined to frontend styling, display text, and mocked UI tests. There were no staged changes or unrelated dirty files identified. No API, authorization, persistence, or external-service code changed.

## Findings

### [P2] Finish the itinerary and component vocabulary across comparison and workspace
- Location: `frontend/src/components/ItineraryComparisonView.tsx:144`
- Scenario: A user opens comparison on a narrow screen after seeing “Planned itinerary” in the list; its card still said “Planned Alternative,” and the financial sections used “Accommodation” and “Subtotal.” Workspace counts and notices also retained “Planned alternative.”
- Impact: The same itinerary and component appeared under different names at adjacent steps, so the ticket's cross-screen terminology criterion was only partial.
- Evidence: The initial diff changed the comparison heading and desktop badge while leaving the mobile heading and financial row labels unchanged; workspace headings and help text still used the older terms. Existing tests primarily asserted the desktop comparison heading.
- Fix: Align these rendered labels and the corresponding assertions; keep model identifiers and workflows intact.

## Findings Resolved in This Context

- Aligned comparison mobile and desktop labels, workspace counts/notices, card read-only notice, and trip/workspace booking badges with the new display vocabulary. Updated existing assertions and added checks for comparison component total labels.
- Re-reviewed the resulting diff. No actionable findings remain in this context. Because implementation artifacts changed, another fresh Step 5 review is required.

## Acceptance-Criteria and Plan Conformance

| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| Shared identity and styling | `style.css` tokens and wordmark on auth, profile, workspace | `VisualSystem.test.tsx`; production build | implemented |
| Actions, states, warnings, focus | text badges and status messages; shared CSS focus, warning, and action rules | Existing App, promotion, cancellation, and booking tests | implemented |
| Lifecycle, component, money labels | List, cards, workspace, comparison, review, confirmation, history, and tally now use the aligned terms and USD context | Full mocked suite; focused tally, comparison, booking assertions | implemented |
| Progressive entry and server-backed behavior | Existing handlers/API calls unchanged | Progressive builder, booking, and cancellation suites | implemented |
| Keep navigation, disclosure copy, dialog audit, backend out of scope | No corresponding behavior or backend changes | Diff inspection | implemented |

## Active Project Guardrails

- None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `npm test -- --run src/VisualSystem.test.tsx src/ProgressiveTripBuilder.test.tsx src/ItineraryComparisonAndBookingReview.test.tsx src/components/BookingConfirmationView.test.tsx` — 36 tests passed before review fixes.
- FAIL — `npm test` — three assertions still expected superseded display labels after review fixes; assertions were updated.
- PASS — `npm test` — 107 tests passed after fixes.
- PASS — `npm run build` — TypeScript and Vite production build passed.
- PASS — `git diff --check` — no whitespace errors.

## Residual Risks and Optional Developer Checks

- CSS appearance is not measured by jsdom. Optional visual inspection at narrow and wide widths with keyboard focus can assess hierarchy, contrast, and wrapping.
- No live external services were used.

## Disposition

- `fixes-applied`
