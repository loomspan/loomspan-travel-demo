# P01-T03 — Deliver the DeTour Authentication and Profile Experience

## Outcome

Give users an accessible, responsive React experience for registering, logging in, restoring an active in-process session on refresh, viewing their empty profile, changing their password, and logging out. Public and authenticated screens expose the required fictional-service disclosure without reviving Wayfarer or model-oriented product behavior.

## Requirements

- Build the React flows for registration, login, current-profile loading, authenticated password change, and logout against the P01-T02 identity and CSRF contracts. A browser refresh during the same application process restores the authenticated view from the server session; an application restart presents login again without losing the account.
- Registration provides an operable show/hide password control and communicates the 12–128 character rule without inventing character-class requirements. Password-change inputs apply the same range and require the current password.
- Keep the account email visible where needed but non-editable. A newly registered user lands in or can reach a clear empty-profile state; do not manufacture sample Trips or catalog content.
- Do not store session identifiers or credentials in local storage, session storage, or another JavaScript-managed persistence mechanism. Use the server cookie and CSRF mechanism defined by P01-T02.
- Present validation, authentication, session-expiration, password-change, CSRF, network, and unexpected-server failures in user-visible language without revealing account existence, stack traces, credential data, or other sensitive details. Failed actions must preserve safe user input where useful and must not imply success.
- Require authentication for the application shell and profile. Direct navigation to a protected client route without a valid session leads to the public authentication experience without briefly exposing protected user data.
- Provide the single allowed collapsed **About this demo** side tab on public authentication pages and authenticated application pages. It must state that suppliers, schedules, prices, availability, and bookings are fictional; no payment or real reservation occurs; origin is PDX; destinations are San Francisco, Munich, and Mexico City; travel dates are limited to March 2027; and itineraries can be created, expanded, compared, booked, and canceled. Treat this as functional interim disclosure content; final prose and visual identity remain Phase 7 work.
- Remove or replace any remaining Wayfarer, Loomspan, model, trace, conversational-change, exchange, disruption, or recovery UI and browser storage behavior encountered on the identity surfaces. Ordinary UI copy must call the product DeTour and must not describe it as a demo outside the disclosure tab.
- Make every identity action keyboard operable with programmatic labels, visible focus, appropriate focus movement after navigation/errors, an error summary or equivalent discoverable error treatment, and meaningful status announcements. Desktop and mobile layouts must preserve the same identity functionality.
- Do not add catalog browsing, Trip creation, itineraries, component selection, comparison, booking/cancellation implementation, or Version 2 Events. Disclosure of those fictional future-in-roadmap capabilities is not authorization to implement them here.

## Acceptance criteria

- [ ] From a public page, a user can register with a valid email/password, reach an empty authenticated profile, refresh and remain authenticated while the process is running, log out, and log back in.
- [ ] After an application restart, the same account can log in but the former browser session is not accepted automatically.
- [ ] Registration and password-change screens enforce and explain the inclusive 12–128 character range, accept a valid password without mixed character classes, and provide keyboard- and pointer-operable show/hide behavior without changing the entered value.
- [ ] An authenticated user can change the password only by entering the correct current password; success and failure are announced clearly, and the UI never displays or persists credential data beyond the active form.
- [ ] The profile presents the immutable account email and a deliberate empty state with no seeded Trip, role, administrator affordance, or inaccessible placeholder workflow.
- [ ] Refresh, direct navigation, logout, expired/invalid sessions, missing CSRF state, validation errors, network failures, and unexpected server failures each produce a coherent public or authenticated state without exposing protected data or falsely reporting success.
- [ ] Browser inspection confirms that no session identifier or credential is stored in local storage, session storage, URLs, or JavaScript-readable cookies.
- [ ] The collapsed **About this demo** tab is keyboard accessible on both public and authenticated pages and contains every required disclosure fact; no other routine identity/profile copy calls DeTour a demo.
- [ ] Focus order, labels, visible focus, error discovery, and status announcements work with a keyboard, and the registration, login, profile, password-change, logout, and disclosure flows remain functionally equivalent at representative mobile and desktop widths.
- [ ] Focused frontend interaction tests, the frontend production build, backend/HTTP integration tests affected by the client contract, and the packaged-JAR startup flow pass without a model credential or external model service.
- [ ] Scoped searches find no active Wayfarer/Loomspan/model/conversation/trace/exchange/disruption/recovery UI, storage key, or product-facing copy on the delivered surfaces.

## Context

- **Phase/work packages:** Phase 1 — Platform Reset and Identity; work package 1.3 frontend experience and Phase 1 exit criteria.
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-1-platform-reset-and-identity.md`](../phases/phase-1-platform-reset-and-identity.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependencies:** P01-T01 and P01-T02 must be complete. This ticket consumes rather than redesigns the established DeTour shell and identity/session/CSRF contracts.
- **Downstream dependency:** Phase 2 begins only after this ticket completes the Phase 1 user-visible identity outcome.
- **Scope exclusions:** final visual identity, colors, typography, wordmark/logo, and final authentication/disclosure prose (Phase 7); profile Trip grouping/status cards (Phase 3); catalog and inventory (Phase 2); planning, booking, cancellation, and Version 2 Events.
- This ticket is sized for GPT-5.6 Terra around one end-to-end frontend behavior set with explicit contract, security, accessibility, responsive, cleanup, and packaged-application verification boundaries.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Although frontend-focused, the ticket integrates authentication and CSRF contracts, protected-route behavior, sensitive-data handling, accessibility, responsive behavior, and packaged deployment across multiple application surfaces.
- **Reassessment triggers:** If P01-T02's completed contract makes the client work entirely localized and no security, supported-contract, or cross-component decisions remain, Step 0 may reassess to the Fast-Track 2-Step Pipeline — Implementation & Review.

