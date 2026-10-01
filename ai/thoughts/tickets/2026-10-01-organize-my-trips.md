# Make My Trips predictable to scan and navigate

## Outcome

Users can quickly find their current or next trip, revisit recent trips, and open a trip without scanning a long card full of option details. Preserve the familiar status filters while providing explicit date ordering and compact summaries.

## Requirements

- Keep the All, Upcoming, Past, and Cancelled filter buttons and their counts. In All, display Upcoming, then Past, then Cancelled sections; cancelled trips do not also appear in the active sections.
- Upcoming shows trips happening now before future trips. A trip is in progress from its start date through its end date, inclusive, and is past after its end date. Within the in-progress and future groups, order by start date ascending. Mark trips happening now with an In progress badge while retaining them in the Upcoming filter.
- Past orders by end date descending. Cancelled orders by planned start date descending. Use trip name A–Z as the secondary ordering when the relevant dates match. Use a stable final tie-breaker so matching dates and names do not shuffle between renders.
- Date classification must be consistent with the app's trip date semantics and status classification, including at date boundaries. Refreshing or revisiting My Trips must reflect the current dates and persisted changes.
- Make each card's visual hierarchy trip name, then destination and a readable date range, then a compact status summary, then the primary Open trip action. Format dates for people rather than displaying raw ISO strings; retain enough year information to distinguish trips across years.
- Summarize booking status and saved-option counts compactly. Move the detailed saved-option list into the trip workspace rather than expanding it on each My Trips card.
- Put secondary card actions, including rename and applicable cancellation or deletion, in an accessible action menu. Preserve their existing permissions, confirmations, availability rules, and errors.
- Keep filtering, card actions, and ordering usable on narrow screens and by keyboard and assistive technology.

## Acceptance criteria

- [x] The four filters display correct counts and matching trips; All presents the agreed section order without duplicating cancelled trips in other sections.
- [x] In-progress trips appear before future trips, carry an In progress badge, and remain accessible through Upcoming. Start/end-day boundaries produce consistent classification.
- [x] Upcoming uses ascending start dates within each group, Past uses descending end dates, and Cancelled uses descending planned start dates; matching dates sort by name A–Z and otherwise remain stable.
- [x] Reopening or refreshing the list reflects date transitions and saved name/date/status changes in both classification and ordering.
- [x] Cards show the agreed name/destination/date/status hierarchy, human-readable dates with unambiguous years, compact booking/option information, and a clear Open trip action without expanded saved-option details.
- [x] Secondary actions remain available through an accessible menu and retain their existing behavior and restrictions.
- [x] The filters, summaries, and actions work on narrow screens and with keyboard and assistive technology.

## Context

This ticket covers My Trips organization only. The related workspace redesign will introduce independently editable plans and an explicitly designated primary plan; this ticket does not implement that model or require it to ship first. If that redesign lands first, use its plan terminology consistently in card summaries without restoring expanded plan details.

Month/year headings were discussed as a possible later improvement for larger lists, not as a requirement for this change. Destination grouping and additional sort controls are outside this ticket.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** medium
- **Rationale:** The agreed behavior is bounded to list presentation, ordering, and existing card actions, with no intended change to persistence or booking lifecycle contracts. Targeted investigation, verification, and independent review should provide sufficient assurance.
- **Reassessment triggers:** Use the full profile if implementation requires changing supported API or persisted contracts, shared date/lifecycle semantics, authorization boundaries, or broader production behavior rather than consuming existing trip data.

## Execution notes

- On 2026-10-01, the developer approved upgrading this run to the Full 5-Step Pipeline after the shared date-classification mismatch was found. The selected profile is now `full`; fresh research and planning must account for the preserved checkout before implementation.
- On 2026-10-01, the developer approved the Fast-Track 2-Step Pipeline — Implementation & Review after Step 0 triage. The checkout was clean at the start of Step 4; no unrelated changes were found.
- Step 4 reconnaissance found that `TripService.tripsProfile` classifies a trip as Past only after both its Working plan and all Saved options have ended. The summary's displayed start/end dates are the Working plan dates. Because Saved options retain independent dates when Working dates change, a trip can remain Upcoming after its displayed end date. This conflicts with the requirement that a trip becomes Past after its end date; resolving it requires either a requirements decision that preserves the current classification or a shared classification change. A Full 5-Step Pipeline upgrade is proposed and pending; production code and tests have not been changed.
- Existing date boundaries use `America/Los_Angeles` (`ClockConfiguration.PDX_ZONE`): the end day is inclusive. My Trips already refreshes on navigation, browser back, workspace return/update, and rename/cancel/delete. Saved-option details already exist in the workspace and can be removed from list cards without relocating functionality.

- Step 4 completed under the approved `full` profile on 2026-10-01. Both endpoints classify from displayed parent/Working dates with a single Portland clock snapshot and additive `inProgress`; independent Saved dates/expiry and booking guards remain intact. My Trips now sorts copied groups deterministically, presents compact readable cards and exposes secondary actions through a keyboard-accessible menu. Required removal-focus verification also corrected hidden-heading modal fallback selection. Material adaptations, red evidence, resolved failures and acceptance traceability are persisted in the implementation/testing plans.
- Final Step 4 gates passed: frontend 188 tests in 17 files; clean Maven verification 206 backend tests plus final frontend build/JAR packaging. Routine operations stayed within mocked frontend requests and isolated backend test databases/servers. Optional real narrow geometry/zoom/screen-reader/forced-color observations were not performed; automated semantic/CSS evidence has those limits. Independent Step 5 review remains required before final pipeline completion.
