# Make trip-plan search prominent before and after selections

## Outcome

After defining a trip's destination, dates, and travelers, users immediately see how to find flights, stays, and eligible rental cars. The same Build your plan layout remains useful once a plan contains selections: users can see their current choices, explore alternatives, understand the price difference, and deliberately replace a choice without losing their existing plan while browsing.

The current empty-plan screen emphasizes planning details and a Compare more options heading even when nothing has been selected. Promote search and reduce redundant headings and empty-state repetition while keeping DeTour's Golden Hour design and independent-plan workflow intact.

## Requirements

- Use the **Search first** mockup as the chosen layout direction, with the populated-plan mockup defining how that layout extends to existing selections. Guided start is an exploratory alternative, not the chosen design.
- Keep a single prominent **Build your plan** heading and clearly visible **Search flights** and **Search stays** tabs. Integrate **Search cars** into the same search area for saved plans, retaining rental eligibility and pickup/return controls. Remove the redundant **Compare more options** heading in both empty and populated states.
- Show saved destination, dates, and traveler context compactly above search. Make saved plan dates and traveler details available through an edit disclosure or equivalent compact editing surface, preserving independent dates and travelers for each plan. Do not force users through an expanded editing form to reach search.
- For an empty plan, show search and concise getting-started guidance immediately. Open a usable search panel instead of an unselected tab state: respect the flight/stay entry choice when present; otherwise start with flights when available, or another available search when flights are locked. If every component is locked, show the read-only state rather than opening an unusable search.
- Keep a compact **Your selections** summary alongside search on desktop, including flight, stay, optional car, and the selected plan's total. Distinguish missing selections and partial totals from a complete total. Show useful choice details and access to fuller details without duplicating large selection cards above search results. On smaller screens, reflow the summary and search without hiding essential information or actions.
- In a category with a selection, show its current selection as a comparison reference above that category's results. Identify an option already selected; it must not offer a redundant replacement action. Use **Replace flight**, **Replace stay**, or **Replace car** for other options, and a save/add action when the component is missing. Summary actions such as **Change flight** open the corresponding search category.
- Show each alternative's price difference against the selected component using comparable full totals: round trip for the saved party, the full stay and required rooms, or the chosen rental interval. Include taxes and fees represented in the application's totals. Do not compare a per-traveler, per-night, or per-day display price directly against a component total. If a valid comparison is unavailable, label it unavailable rather than inventing a difference or using zero as a fallback.
- Replacing an existing selection requires a clear confirmation identifying the old and new choice and the price impact. Browsing, changing tabs, or canceling confirmation leaves the selection unchanged. A failed save retains the previous choice, shows the failure, and leaves recovery available. The summary and total reflect the successful saved result, not an optimistic replacement that failed.
- Preserve purchased component locks, canceled-trip restrictions, booking history, version/conflict handling, and authentication boundaries. Confirmed or historically locked purchases remain visible with their purchased dates, party, and prices; they cannot be removed or replaced through this redesign. Retain removal actions for editable selections without weakening existing safeguards.
- Preserve the rule that searches and prices use saved planning details. Unsaved dates or traveler edits must remain visible and must be saved or discarded before selection mutations; keep existing leave/save/discard protections. Rental eligibility still requires a qualifying driver, and its pickup/return interval must satisfy existing constraints.
- Apply consistent search layout, headings, selection summaries, and action language across guest exploration, saved independent plans, and supported legacy workspace views. Guest exploration retains flight/stay search and its existing login/save handoff; do not add public rental search. Guest choices must not be described as already persisted.
- Keep plan switching, creation, copying, renaming, primary choice, deletion, comparison, budget editing, and booking review accessible. Maintain the distinction between comparing search options within a plan and comparing independent plans. Keep booking review and confirmation consistent in typography and selection-summary language while preserving their separate review and purchase/history purpose.
- Follow `frontend/DESIGN.md`, reuse the existing design tokens and primitives, and keep application styles in `frontend/src/style.css`. Search tabs need correct semantics, keyboard navigation, and visible focus. Preserve loading, empty-results, retry, pending, and error states in the promoted search area.

## Acceptance criteria

- [ ] A newly saved empty plan presents Build your plan, compact trip/plan context, prominent search tabs, and an active usable search panel without the Compare more options heading or repeated missing-component sections.
- [ ] Plans with any combination of flight, stay, and car selections use the same layout; their summary correctly shows saved choices, missing components, and the server-backed full or partial total at desktop and phone widths.
- [ ] Category search shows the current selection as a reference, marks the already-selected option, and displays accurate full-component price differences for alternatives, or an explicit unavailable comparison when necessary.
- [ ] Change actions open the matching search; missing components can be added, and editable selections can be removed using existing safeguards. Replacement confirmation identifies old/new choices and price impact. Canceling or a failed request retains the previous choice; success updates the summary and total.
- [ ] Saved dates and travelers can be edited without permanently dominating the screen. Search and mutation behavior respects saved plan context and unsaved-edit protections, and switching independent plans preserves each plan's distinct context and selections.
- [ ] Car search appears in the saved-plan search area and preserves driver eligibility, interval validation, and booking locks. Guest exploration offers flights/stays with its existing save/authentication flow and no new public rental capability.
- [ ] Purchased selections and canceled/read-only plans retain their historical context and restrictions in every entry path, including confirmation and replacement actions.
- [ ] Guest, independent-plan, and supported legacy planning screens use consistent layout and language. Booking review/confirmation remain clear and consistent; plan management, independent-plan comparison, budget editing, and history remain accessible.
- [ ] Golden Hour styling, keyboard-operated tabs, visible focus, responsive layouts, and all search loading/error/retry/pending/empty states remain functional.

## Context

### Included mockups

The original interactive mockup sources are copied into this ticket's asset directory so future pipeline contexts do not depend on chat history or a user-specific visualization path:

- [Empty plan and layout alternatives](assets/2026-10-02-promote-plan-search/trip-search-layout.html): **Search first** is the chosen direction. The alternate Guided start layout is retained only as design context.
- [Populated plan with replacement interaction](assets/2026-10-02-promote-plan-search/trip-with-selections.html): illustrates search tabs, current-selection reference, full-component price differences, replacement confirmation, and a selections sidebar. Its optional preview controls allow flight, stay, and car selections to be toggled to explore partial plans.

These files are inline HTML visualization fragments with local demo interactions, not application components or production pricing sources. Their illustrative schedules, properties, rental choices, amounts, simplified filters, and abbreviated navigation are not requirements. Preserve real application details, fee calculations, controls, eligibility, and persistence behavior. Requirements in this ticket take precedence over mockup omissions. The asset filenames are stable references for implementation and review.

### Scope and source hints

This ticket covers the planning/search presentation and deliberate selection replacement behavior. It does not change supported travel dates, catalog data, API contracts, persisted schemas, guest rental availability, purchase semantics, or the separate default trip-name date-format change already present in the working tree. Preserve those pre-existing edits.

Prior conversation inspection identified shared flight/stay exploration, separate rental controls, guest exploration, independent plans, and a legacy workspace path. These are source hints for research, not a prescribed component architecture. The sidebar must show the selected plan's context rather than inadvertently using another plan's dates, party, totals, or purchases.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** medium
- **Rationale:** Product direction is settled, but integrating rental search, consolidating selection summaries, compacting plan-detail editing, and maintaining consistency across multiple planning entry paths need coordinated discovery and design. Research and planning should resolve the shared structure and protected-state presentation before implementation and independent review.
- **Reassessment triggers:** Research demonstrates that all affected entry paths can be handled as a bounded presentation change with no material remaining design or lifecycle questions, supporting fast-track; discovery of API, persistence, authorization, or lifecycle changes requires explicit scope reassessment rather than silently expanding this ticket.
