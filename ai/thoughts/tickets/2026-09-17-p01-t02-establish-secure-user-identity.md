# P01-T02 — Establish Secure DeTour Accounts and Tenant Isolation

## Outcome

Provide secure backend identity and session behavior for ordinary DeTour users. A person can register, authenticate, inspect an empty current profile, change their password after proving knowledge of the current one, and log out, while backend enforcement prevents one account from accessing another account's data.

## Requirements

- Add the DeTour user and credential persistence needed for identity to the fresh Flyway lineage established by P01-T01. Persist application data across restarts, but do not persist authenticated sessions across an application restart.
- Support self-service registration, login, logout, current-profile retrieval, and authenticated password change. A newly registered user has an empty profile and no seeded Trips, privileges, roles, or administrator status.
- Accept passwords from 12 through 128 characters inclusive. Do not require uppercase, lowercase, numeric, symbol, or other composition classes. Store only hashes produced by an established adaptive password encoder; never expose credential hashes or plaintext in responses or logs.
- Normalize email addresses for uniqueness and authentication so equivalent accepted forms cannot create multiple accounts. Profile email is immutable in Version 1.
- Password change must verify the current password before accepting a new valid password. After a successful change, the old password can no longer authenticate and the new password can.
- Use secure, HTTP-only, same-site session cookies and CSRF protection suitable for the same packaged Spring Boot/React application. Do not put session credentials in browser-managed application storage or expose them to client JavaScript.
- Prevent practical user enumeration: registration and authentication failures must not reveal whether an account exists beyond what is unavoidable for a usable uniqueness conflict. Error responses must not disclose another user's profile or credential information.
- Make registration, login, required static assets, and the demo-disclosure surface public. Require authentication for every other application or catalog endpoint, including current profile and password change. There is no default privileged credential, administrator account, or role model.
- Establish the backend ownership rule later phases must follow: every user-owned query and mutation is scoped through the authenticated `User`; future Trip data must follow the relational `User -> Trip -> downstream data` path. Requests involving another user's identifier return the same non-disclosing not-found behavior as an unavailable resource.
- Add isolation coverage using at least two independently authenticated users for every user-owned resource or identifier exposed in this phase. Do not add speculative Trip, itinerary, booking, or catalog tables solely to demonstrate the future ownership path.
- Define stable identity and CSRF HTTP contracts that the P01-T03 React work can consume, including useful validation, unauthenticated, forbidden/CSRF, missing-resource, and malformed-input behavior. Implementation details and endpoint shapes remain planning decisions unless constrained above.

## Acceptance criteria

- [ ] A clean database migration creates the identity persistence successfully, and registered users remain present after an application restart while their pre-restart sessions are rejected and login is required again.
- [ ] Registration accepts passwords of exactly 12 and 128 characters, rejects 11 and 129 characters, does not impose composition rules, and never persists or returns plaintext credentials.
- [ ] Email normalization prevents duplicate equivalent accounts and permits authentication using the accepted normalized form; no profile operation can change the account email.
- [ ] Login, current-profile retrieval, logout, and subsequent login work through secure server sessions; logout invalidates the active session.
- [ ] Password change fails without the correct current password, enforces the same length rules for the new password, and causes only the new password to authenticate after success.
- [ ] Session cookies are HTTP-only, secure under the production HTTPS configuration, and same-site; state-changing requests without a valid CSRF token are rejected without mutation.
- [ ] Every non-public application/catalog endpoint returns an unauthenticated response without a valid session, while registration, login, required static assets, and demo disclosure remain reachable publicly.
- [ ] Two-user integration tests prove that user-owned reads and mutations available in this ticket cannot cross account boundaries and that guessed foreign identifiers receive a non-disclosing not-found response.
- [ ] Authentication and registration failures do not expose credential data or unnecessarily confirm whether a supplied email belongs to an account.
- [ ] A new account's current profile is empty, contains no seeded Trip data or privileged role, and no administrator/default credential exists.
- [ ] Malformed identity requests and missing resources produce useful, stable HTTP errors without stack traces or sensitive data.

## Context

- **Phase/work packages:** Phase 1 — Platform Reset and Identity; work packages 1.3 and 1.4 (backend boundary).
- **Authoritative sources:** [`../phases/README.md`](../phases/README.md), [`../phases/CONTINUATION.md`](../phases/CONTINUATION.md), and [`../phases/phase-1-platform-reset-and-identity.md`](../phases/phase-1-platform-reset-and-identity.md).
- **Required architecture:** [`../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md`](../architecture/2026-09-17-p00-t02-detour-replacement-architecture.md).
- **Hard dependency:** P01-T01 must be complete so this work targets only the DeTour namespace, database, migration lineage, configuration, and packaged application.
- **Downstream dependency:** P01-T03 consumes the identity/session/CSRF contracts. Every later ticket introducing Trip or downstream persistence must implement the relational ownership path and add its own two-user isolation tests.
- **Scope exclusions:** final React authentication/profile experience; email verification; forgotten-password or account-recovery flows; social login; MFA; administrators/roles; account deletion; Trip, itinerary, catalog, booking, or Event persistence and behavior.
- Keep this ticket cohesive for GPT-5.6 Terra: implement and verify the full server-side identity/security boundary, but leave visual interaction work to P01-T03 and later domain ownership to the ticket that introduces each resource.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This ticket defines authentication, authorization, CSRF, credential handling, session lifecycle, supported HTTP contracts, and persisted identity data. These are security and compatibility boundaries that require explicit research, planning, and independent review.
- **Reassessment triggers:** Any requirement for cross-origin deployment, a persistent external session store, or an identity provider would materially change the settled same-application session design and requires developer direction.

