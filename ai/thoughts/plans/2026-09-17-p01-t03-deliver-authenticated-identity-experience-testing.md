# Deliver the DeTour Authentication and Profile Experience Testing Plan

## Change Summary

P01-T03 turns the minimal Vite/React foundation into a same-origin authentication and empty-profile application over P01-T02's five identity endpoints. It adds a narrow public SPA-document fallback for `/profile`, but the protected API remains the source of session truth. It also adds an accessible disclosure, responsive identity interactions, and a frontend test harness while retaining server-managed sessions and the existing Java HTTP/restart tests.

## Impacted Areas and Risks

| Category | Risk | Planned evidence |
| --- | --- | --- |
| Direct navigation | A `/profile` document request yields unusable JSON, broadly exposes routes, or weakens `/api/profile` authorization. | MVC integration test distinguishes public SPA fallback from protected profile API; UI bootstrap tests show no protected content before profile success. |
| Session lifecycle | Refresh fails to restore a live session, logout/stale async state leaves an email visible, or restart becomes client-persisted. | App bootstrap/logout interaction tests plus existing two-context restart integration test. |
| CSRF/client contract | Client omits/misreads the CSRF header or reports `403` success. | API-client header and 403 tests, existing real-filter CSRF integration coverage. |
| Credentials and browser persistence | Passwords/session IDs are retained in storage, URL, status text, or rendered profile state. | Active-form/clear-on-success tests, local/session-storage spies, response rendering assertions, scoped source search, optional DevTools inspection. |
| Validation and safe failures | UI changes the 12–128 rule, adds composition requirements, leaks account/credential data, or turns network/5xx into success. | Boundary/no-composition, generic failure, field-summary, 401/403/network/5xx interaction tests. |
| Accessibility and mobile | Controls lack names/focus/status behavior or mobile hides actions. | RTL role/label/keyboard/focus/live-region tests; optional real viewport/screen-reader observation. |
| Disclosure/copy cleanup | Required facts are missing, the tab cannot be toggled, or forbidden legacy/model/demo copy remains. | Disclosure content/keyboard test and scoped product-surface search. |
| Packaging | New Node tooling breaks Maven install/build or built JAR startup requires an external service/model. | frontend build, Maven full tests/package, bounded local JAR smoke script. |

## Existing Coverage and Environment Constraints

The Java project already has real Spring/H2/HTTP coverage in `IdentityApiIntegrationTest` and a two-context file-H2 lifecycle test in `ApplicationRestartIntegrationTest`; Maven's `skipFrontend` property is valid for focused backend tests, while normal Maven lifecycle runs `npm ci` and `npm run build`. There are no frontend test dependencies, test script, browser automation harness, or component test files today.

Planned frontend tests must run in jsdom with mocked `fetch`, never a production URL or a real account. They must create only the CSRF cookie value required to assert request-header behavior and must not fabricate/access `JSESSIONID`. Java tests continue using H2 and temporary paths; the packaged smoke script must use a generated temporary database and loopback port. `DETOUR_SECURE_COOKIES=false` is allowed only inside that local HTTP smoke path, not as a production-cookie assertion or a changed default.

## Failing Test First

- Name: `restoresTheAuthenticatedProfileFromTheExistingServerSession`
- Type: React interaction test using Vitest, jsdom, React Testing Library, and mocked same-origin `fetch`.
- Location: `frontend/src/App.test.tsx`
- Arrange/Act/Assert: set the browser path to `/profile`, mock `GET /api/profile` with `200 {email}`, render `App`, wait for bootstrap completion, and assert the immutable email plus empty-profile content appear while login/registration and password values do not.
- Expected pre-fix failure: the current `main.tsx` renders only the foundation message and has no app component, profile request, authenticated state, or test command.

## Tests to Add or Update

### 1. `restoresTheAuthenticatedProfileFromTheExistingServerSession`

- Type: React integration/interaction test.
- Location: `frontend/src/App.test.tsx`.
- Proves: initial `GET /api/profile` controls authenticated rendering after a refresh/direct protected client route; account email is displayed as immutable identity; the empty profile has no Trip, role, or admin workflow.
- Inputs/fixture: mocked same-origin `200` profile containing `ada@example.test`.
- Doubles or boundary isolation: mock `fetch`; do not mock or create a session ID.
- Edge cases: assert no profile content appears during loading and stale profile state is cleared when a later bootstrap returns `401 UNAUTHENTICATED`.

### 2. `registersThenShowsTheEmptyProfileAndSupportsLogoutAndLoginAgain`

- Type: React interaction test.
- Location: `frontend/src/App.test.tsx`.
- Proves: public registration submits the contract payload/header, accepts `201 {email}`, reaches empty profile, logout reaches public login, and a later `204` login followed by profile load restores the authenticated screen.
- Inputs/fixture: valid email and 12-character lowercase password; response sequence for profile/register/logout/login/profile.
- Doubles or boundary isolation: create only `document.cookie = 'XSRF-TOKEN=...'`; assert unsafe fetches use `credentials: 'same-origin'`, JSON content type, and `X-XSRF-TOKEN`, without inspecting a session cookie.
- Edge cases: no password appears in profile DOM, status message, route, localStorage, or sessionStorage; form fields clear after successful submission/navigation.

### 3. `enforcesPasswordRangeWithoutCompositionAndPreservesValueWhenVisibilityChanges`

- Type: React component/interaction test.
- Location: `frontend/src/components/PasswordField.test.tsx` and/or `frontend/src/App.test.tsx`.
- Proves: registration and password-change show a 12–128 inclusive rule; 11/129-code-point values prevent an unsafe request and give discoverable validation; a lowercase-only 12-character value is accepted; show/hide works with keyboard and pointer and does not alter the entered value.
- Inputs/fixture: 11, 12, 128, and 129 code-point values plus a repeated lowercase valid password.
- Doubles or boundary isolation: mocked callbacks/fetch; no encoder or backend stub with different password policy.
- Edge cases: use at least one astral Unicode boundary case if the client helper counts code points, and prove password-change current/new controls have distinct labels.

### 4. `changesPasswordOnlyOnConfirmedServerSuccessAndKeepsSecretFieldsEphemeral`

- Type: React interaction test.
- Location: `frontend/src/App.test.tsx` or `frontend/src/components/PasswordChangeForm.test.tsx`.
- Proves: current/new passwords form the sole `PUT /api/profile/password` payload; `204` yields a success announcement and clears both fields; `CURRENT_PASSWORD_INVALID`/validation/CSRF failures announce failure and do not claim an update.
- Inputs/fixture: authenticated profile, valid current/new values, then `400 CURRENT_PASSWORD_INVALID`, `400 VALIDATION_FAILED`, `403 CSRF_INVALID`, and `204` responses.
- Doubles or boundary isolation: mocked fetch; spy on localStorage and sessionStorage `setItem`/`removeItem` so the form never uses either.
- Edge cases: verify server-returned message/fields are rendered safely without echoing either submitted password and retained values follow the implementation's documented safe-input policy.

### 5. `returnsToPublicAuthForExpiredOrInvalidSessionsWithoutProtectedData`

- Type: React interaction test.
- Location: `frontend/src/App.test.tsx`.
- Proves: initial `401 UNAUTHENTICATED`, post-logout state, and a protected-action unauthenticated response clear email/profile state and render public authentication; no success status is emitted.
- Inputs/fixture: mocked `401` envelope and a prior authenticated render for stale-state coverage.
- Doubles or boundary isolation: mocked fetch only; do not model browser cookie storage.
- Edge cases: `/profile` URL can remain or be replaced according to the selected client navigation implementation, but it must not cause protected data to reappear without a later `200` profile response.

### 6. `showsDiscoverableSafeFailuresForValidationNetworkAndUnexpectedResponses`

- Type: React interaction test.
- Location: `frontend/src/App.test.tsx`.
- Proves: validation fields/summary, generic authentication failure, `CSRF_INVALID`, rejected fetch/network failure, malformed/non-JSON response, and `500 INTERNAL_ERROR` result in a visible error summary and meaningful non-sensitive status; failures preserve non-secret useful inputs when specified and never announce success.
- Inputs/fixture: representative P01-T02 `{code,message,fields}` JSON envelopes plus `Promise.reject` and invalid response body.
- Doubles or boundary isolation: mocked fetch and controlled component focus; no backend connection.
- Edge cases: focus moves to the summary on failure and the response text cannot create raw HTML/stack-trace rendering.

### 7. `providesKeyboardAccessibleDisclosureLabelsFocusAndStatus`

- Type: React accessibility/interaction test.
- Location: `frontend/src/App.test.tsx` and `frontend/src/components/AboutDemoTab.test.tsx` if the disclosure is separated.
- Proves: all identity fields have associated labels; button names are meaningful; keyboard can switch public forms, submit, toggle show/hide, toggle the collapsed disclosure, and logout; focus reaches the route heading after successful view change and errors after failures; a live region exposes status.
- Inputs/fixture: public and authenticated mocked responses.
- Doubles or boundary isolation: Testing Library role/label queries and user-event keyboard actions.
- Edge cases: assert `aria-expanded` changes; disclosure includes all six required fact groups; routine public/profile text contains no “demo” outside the disclosure title/content.

### 8. `sendsOnlyTheCsrfCookieTokenForUnsafeIdentityRequests`

- Type: API-client unit test.
- Location: `frontend/src/api/identityApi.test.ts`.
- Proves: register/login/logout/password-change include the expected cookie-derived CSRF header and same-origin credentials, profile GET does not need a CSRF header, and error parsing preserves only safe code/message/field information.
- Inputs/fixture: controlled `document.cookie`, mocked successful and error `Response` objects.
- Doubles or boundary isolation: direct mock of global fetch; no real cookie jar or session value.
- Edge cases: missing `XSRF-TOKEN` must result in a safe actionable client outcome rather than a fabricated token/header; test that source/helper has no localStorage/sessionStorage API usage.

### 9. `servesOnlyTheProfileDocumentFallbackWhileKeepingProfileDataProtected`

- Type: Spring MVC integration test.
- Location: `src/test/java/app/detour/identity/IdentityApiIntegrationTest.java`.
- Proves: unauthenticated `GET /profile` returns the SPA document, while unauthenticated `GET /api/profile` is JSON `401 UNAUTHENTICATED`; a non-SPA unknown path remains protected/not broadly forwarded according to existing security behavior.
- Inputs/fixture: anonymous request and an authenticated `Client` helper as needed.
- Doubles or boundary isolation: real Spring security chain/static resources; no `@WithMockUser`.
- Edge cases: assert the document response is HTML/static shell rather than profile JSON and `/api/profile` does not leak email.

### 10. `retainsAccountAcrossRestartButRejectsPriorSession`

- Type: existing application-restart HTTP integration test.
- Location: `src/test/java/app/detour/identity/ApplicationRestartIntegrationTest.java`.
- Proves: P01-T03 did not accidentally add client/persistent session semantics while consuming the contract.
- Inputs/fixture: existing temporary file-backed H2, captured session cookie, two application contexts.
- Doubles or boundary isolation: existing local real server contexts; no production database or external provider.
- Edge cases: retain cookie security attribute assertions in the existing test and a new login after restart.

### 11. `startsThePackagedJarAndServesTheIdentityShell`

- Type: local packaged-application smoke test.
- Location: `scripts/verify-packaged-identity.ps1`.
- Proves: the Maven-produced JAR includes the Vite build and starts without a model credential/service; `/` and `/profile` return the SPA shell in a temporary loopback configuration.
- Inputs/fixture: `target/detour-0.1.0-SNAPSHOT.jar`, generated temporary H2 location and unused loopback port.
- Doubles or boundary isolation: no external services; the script manages and terminates only its spawned Java process.
- Edge cases: timeout/startup diagnostics, cleanup on failure, and no write/reset of `data/detour`.

## Safe Verification Commands

- Focused frontend: `npm --prefix frontend run test`
- Focused HTTP contract: `mvn -DskipFrontend=true -Dtest=IdentityApiIntegrationTest,ApplicationRestartIntegrationTest,DetourApplicationTest test`
- Frontend production build: `npm --prefix frontend run build`
- Related Java suite: `mvn -DskipFrontend=true test`
- Full safe suite: `mvn test`
- Packaging gate: `mvn package`
- Packaged JAR flow: `.\scripts\verify-packaged-identity.ps1` (after `mvn package`)
- Scoped cleanup check: `rg -n -i -S "wayfarer|loomspan|model|conversation|trace|exchange|disruption|recovery" frontend/src src/main/java src/main/resources scripts README.md`

## Optional Developer Checks

- In a browser developer profile against the local loopback app, inspect Application/Storage and cookies: local/session storage and URLs contain no identity values; `JSESSIONID` is not JavaScript-readable; only `XSRF-TOKEN` is readable for header echo.
- At approximately 320px and a desktop width, complete registration, login, profile, password change, logout, and disclosure with keyboard only; observe visible focus, logical focus movement, summary/status announcements, and equal available functionality. Confirm with a screen reader when one is available.

## Exit Criteria

- [ ] The planned red test fails for the intended reason before implementation, when applicable.
- [x] New and updated frontend interaction/API-client tests pass after implementation.
- [x] The `/profile` document fallback and `/api/profile` authorization boundary pass real Spring MVC integration coverage.
- [x] Existing restart/session/CSRF HTTP integration tests pass without weakening their secure-default assertions.
- [x] `npm --prefix frontend run build`, `mvn test`, and `mvn package` pass with the updated lockfile and no external model service or credential.
- [x] The bounded packaged-JAR smoke script passes and leaves no durable app database/process behind.
- [x] Acceptance criteria map to executable evidence; manual browser/assistive-technology checks are reported as nonblocking observations, never pre-claimed.
- [x] Storage/URL/secret rendering and scoped legacy/model-copy risks are covered; routine automated tests perform no live or destructive operation.

## Implementation Verification Note

The planned red test was not run before implementation because the frontend test harness did not exist at the start of this execution. The completed focused Vitest suite, production build, Spring suites, packaging flow, and local JAR smoke verification provide the recorded post-implementation evidence; browser storage and assistive-technology observations remain optional developer checks.
