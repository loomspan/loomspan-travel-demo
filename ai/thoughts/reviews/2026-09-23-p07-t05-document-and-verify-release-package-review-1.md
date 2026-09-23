# P07-T05 Code Review — Cycle 1

## Scope and Repository State
Reviewed the ticket-scoped staged, unstaged, and untracked diff on main at `8211b9cf0b7e0e2d8441a919b75a57ad545ade23`: README, security and trip routes, updated backend tests, release verifier, research/plans, and verification evidence. The initial checkout was clean. This review was performed in the implementation context; it is a local review record and cannot serve as the full pipeline's required fresh independent certification.

## Findings
### [P3] Remove alias-era test duplication and comments
- Location: `src/test/java/app/detour/trip/StaySearchAndSelectionIntegrationTest.java:101`, `src/test/java/app/detour/trip/TripApiIntegrationTest.java:887`.
- Scenario: replacing singular and `/revisions` paths left duplicate requests to the same canonical stay route and comments still calling them aliases.
- Impact: the tests misleadingly suggested obsolete routes remained covered and repeated a request without additional proof.
- Evidence: full diff and test source inspection.
- Fix: remove duplicate canonical requests and update comments; focused rerun passed 51 tests.

## Findings Resolved in This Context
The P3 test cleanup above was applied after reviewing the full diff. No additional actionable issue was identified in the follow-up local pass. A fresh independent review is still required by the selected full pipeline.

## Acceptance-Criteria and Plan Conformance
| Criterion/decision | Code evidence | Test evidence | Result |
| --- | --- | --- | --- |
| README, setup and explicit reset | `README.md`, `reset-detour.ps1` | preview-only reset result | implemented |
| Clean package, no provider credentials | `pom.xml`, V1–V18, packaged verifier | clean verify and JAR run | implemented |
| Register to Plan, Booking, restart/history | service/JDBC/clock code unchanged | packaged HTTP run, restart tests | implemented |
| Server validation, ownership, atomic inventory | security/service/repository code unchanged except obsolete route removal | 185 backend tests and packaged owner/isolation/idempotency probes | implemented |
| Frontend accessibility/responsive | frontend code unchanged | 134 tests, desktop browser focus check; mobile visual check optional | implemented with optional visual check |
| Obsolete paths/copy removed | controller/security diff, generated assets | scoped search, 404 probes | implemented |

## Active Project Guardrails
None recorded in `ai/thoughts/design-lens.md`.

## Open Questions and Assumptions
- The selected full pipeline requires an independent fresh review context. This local implementation context cannot certify its own fixes.

## Verification Results
- PASS — `npm test` in `frontend/`: 134 tests.
- PASS — `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' clean verify`: 185 tests and JAR.
- PASS — `.\scripts\verify-packaged-release.ps1`: isolated packaged HTTP journey.
- PASS — `.\mvnw.cmd '-Dmaven.repo.local=C:\Users\rmelcher\.m2\repository' -DskipFrontend=true '-Dtest=StaySearchAndSelectionIntegrationTest,TripApiIntegrationTest' test`: 51 tests after local fix.
- PASS — scoped source/generated-asset searches and `jar tf` inspection.
- NOT RUN — rendered narrow-width browser walkthrough: this browser exposed no viewport override.

## Residual Risks and Optional Developer Checks
A 320px/mobile-width rendered keyboard walkthrough remains optional. The full pipeline also requires fresh independent review before its outcome can be certified complete.

## Disposition
- `fixes-applied` in this local review context. Fresh independent review required.
