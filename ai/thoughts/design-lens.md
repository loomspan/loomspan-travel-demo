# Design Lens

## Status

One active guardrail is recorded below: the shared visual design system.

## Purpose

This document preserves durable, non-obvious, cross-cutting decisions that future research, planning, implementation, and review must remember. It is intentionally a placeholder until this app develops such constraints.

An entry belongs here only when all of the following are true:

- it is specific to this app rather than general software-engineering advice;
- it applies across multiple tickets or feature areas;
- it is expected to remain valid long enough to guide future work;
- violating it could produce a plausible but incorrect implementation; and
- the rule cannot be reliably reconstructed from the checked-out code and tests alone.

Do not add repository architecture summaries, coding conventions visible in the source, ticket-specific requirements, temporary implementation notes, or generic reminders such as testing, authorization, validation, and error handling. Put those in the relevant code, tests, ticket, plan, or command instead.

Possible future subjects include AI decision authority, provenance and audit requirements for AI-derived values, model data boundaries, evaluation requirements for prompt or model changes, failure and fallback policy, and whether AI results may initiate external side effects. These examples are not current policy.

## Entry Format

```markdown
## <Guardrail name>

- **Decision:** <The rule future work must preserve.>
- **Why this is non-obvious:** <What a reasonable implementation might otherwise get wrong.>
- **Applies to:** <Features, data, integrations, or workflows covered by the rule.>
- **Exceptions:** <Explicit exceptions, or `None`.>
- **Established by:** <Ticket, decision, date, or other authoritative source.>
```

## Shared "Golden Hour" visual design system

- **Decision:** All UI must be built from the tokens and primitives in `frontend/src/style.css`, as documented in `frontend/DESIGN.md`. Don't use inline styles, new hard-coded colors, or buttons without a variant. Allow at most one `.primary` action per region. Group buttons in a row class (`.button-row`, `.modal-actions`, etc.). Use `role="tablist"`/`role="tab"` only for panel switching. Use `aria-pressed` buttons inside `.segmented` (or `.tabs` / `.trip-filters`) for filters and modes. Add CSS for a specific screen only in section 11 of `style.css`. Every screen must stay usable at about 390px wide by reflowing, never by hiding data.
- **Why this is non-obvious:** Plain buttons and one-off CSS appear to work and pass behavioral tests. Before this system, they produced misaligned, inconsistently sized buttons and tab-like controls that did not read as tabs. A plain `<button>` now falls back to a secondary style, which hides a missing variant decision.
- **Applies to:** All React components under `frontend/src` and all changes to `frontend/src/style.css`.
- **Exceptions:** Legacy class names already mapped onto the system (for example `.primary-button`, `.secondary-action-button`, `.readiness-banner`) may remain. New code uses the documented classes.
- **Established by:** UI redesign, 2026-10-02 (`frontend/DESIGN.md`).
