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

- [ ] The four filters display correct counts and matching trips; All presents the agreed section order without duplicating cancelled trips in other sections.
- [ ] In-progress trips appear before future trips, carry an In progress badge, and remain accessible through Upcoming. Start/end-day boundaries produce consistent classification.
- [ ] Upcoming uses ascending start dates within each group, Past uses descending end dates, and Cancelled uses descending planned start dates; matching dates sort by name A–Z and otherwise remain stable.
- [ ] Reopening or refreshing the list reflects date transitions and saved name/date/status changes in both classification and ordering.
- [ ] Cards show the agreed name/destination/date/status hierarchy, human-readable dates with unambiguous years, compact booking/option information, and a clear Open trip action without expanded saved-option details.
- [ ] Secondary actions remain available through an accessible menu and retain their existing behavior and restrictions.
- [ ] The filters, summaries, and actions work on narrow screens and with keyboard and assistive technology.

## Context

This ticket covers My Trips organization only. The related workspace redesign will introduce independently editable plans and an explicitly designated primary plan; this ticket does not implement that model or require it to ship first. If that redesign lands first, use its plan terminology consistently in card summaries without restoring expanded plan details.

Month/year headings were discussed as a possible later improvement for larger lists, not as a requirement for this change. Destination grouping and additional sort controls are outside this ticket.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** medium
- **Rationale:** The agreed behavior is bounded to list presentation, ordering, and existing card actions, with no intended change to persistence or booking lifecycle contracts. Targeted investigation, verification, and independent review should provide sufficient assurance.
- **Reassessment triggers:** Use the full profile if implementation requires changing supported API or persisted contracts, shared date/lifecycle semantics, authorization boundaries, or broader production behavior rather than consuming existing trip data.
