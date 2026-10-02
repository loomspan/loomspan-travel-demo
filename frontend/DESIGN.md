# DeTour design system: "Golden Hour"

The whole UI is styled from one file, `frontend/src/style.css`. This document says what is in it and how to use it.
**Read this before you change any UI.** Reviews should reject UI changes that ignore it.

## The idea

Travel at golden hour: a warm sand **canvas**, deep **night-navy** chrome (the nav, banners, and boarding passes), **lagoon-teal** for actions, and **sunset coral / sun gold** used sparingly for accents. Boarding-pass details carry the travel feel:

- dashed **perforation** dividers (`border-top: 2px dashed var(--color-line)`)
- half-circle **ticket notches** (trip cards, confirmation banner, boarding pass)
- **route lines** (`PDX - - ✈ - - SFO`) and section titles that end in a dashed line
- pill-shaped badges and buttons, like stamps and tags

Headings use the display serif (`--font-display`). Body text, labels, and numbers use the system sans (`--font-body`). Booking codes use `--font-mono`.

## Golden rules

1. **Use tokens, not literal values.** Colors, radii, shadows, spacing, and font sizes come from the `:root` variables in section 1 of `style.css`. Don't add hex colors to component rules.
2. **No inline `style={{…}}` in components.** Add or reuse a class.
3. **Reuse primitives before you add CSS.** Most new UI needs no new CSS at all, only the classes below.
4. **Only add CSS for a specific screen in section 11** of `style.css`, under the matching screen heading. Put responsive overrides in section 12. Keep the reduced-motion and forced-colors rules in section 13.
5. **Allow at most one `.primary` button per region** (a card, toolbar, form, or dialog). Every other action is secondary, text, or danger.
6. **Use tabs only to switch panels.** Use a segmented control for filters and modes. Never style plain buttons to look like either one.
7. **Never hide information on small screens.** Reflow it instead (stack, wrap, or scroll horizontally).
8. **Formatting caveat:** `src/VisualSystem.test.tsx` checks some rules in `style.css` with regexes. Keep those selectors and properties in place: the reduced-motion and forced-colors rules, the checkbox and select states, `.trip-action-menu`, and the grid rules.

## Tokens (section 1)

| Group | Tokens |
| --- | --- |
| Surfaces | `--color-canvas`, `--color-surface`, `--color-surface-alt` (inset panels), `--color-line`, `--color-line-strong` |
| Text | `--color-ink`, `--color-ink-soft`, `--color-muted` |
| Brand | `--color-night` / `--color-night-2` (chrome), `--color-primary` / `-strong` / `-soft` / `-line` (actions), `--color-accent` / `--color-accent-text` / `--color-accent-soft` (coral), `--color-sun` (gold) |
| Status | `--color-{success,warning,danger,info}` plus `-soft` and `-line` variants |
| Type | `--font-display`, `--font-body`, `--font-mono`, `--text-xs` … `--text-3xl` |
| Space | `--space-1` (.25rem) … `--space-7` (3rem) |
| Shape | `--radius-sm`, `--radius-md`, `--radius-lg`, `--radius-pill`, `--shadow-sm/md/lg` |
| Layout | `--page-max`, `--gutter`, `--nav-h`, `--control-h`, `--control-h-sm` |

To retheme the app, change the token values. Component rules should not need to change.

## Layout

- `main.shell` → `nav.primary-navigation` (sticky, full width, starts with `<BrandMark />`) → page blocks. Every direct child of `.shell`, or of `.app-frame` for signed-in users, is centered at `--page-max` automatically.
- A **page** is a `.card` (`section className="card …"`). The page header uses `.page-header`, or one of the existing header classes: an `.eyebrow` kicker, an `h1`, and a meta line on the left, with the primary action on the right.
- A **`.card` inside another `.card`** automatically becomes a flatter panel. Use `.panel-inset` for a sand-tinted inset area such as search controls, totals, or settings.
- `.section-title` is a heading with a dashed route line trailing to the right.
- `.empty-state` is a dashed placeholder box.
- Two-column screens (booking review) use a `…-layout` grid with a `…-main` column and a sticky `…-sidebar`. Below 1050px they collapse to one column.

## Buttons (section 5)

A `<button>` with no class already renders as a secondary button. Pick a variant on purpose anyway:

| Class | Use for |
| --- | --- |
| `.primary` (`.primary-button` is a legacy alias) | The one main action in a region |
| `.secondary` (`.secondary-action-button` is a legacy alias) | Other actions |
| `.text-button` | Low-emphasis or inline actions such as Cancel, Show password, Remove, and back links |
| `.text-button.delete-button` | Inline destructive links |
| `.button-danger-outline` | Destructive actions that open a confirmation, e.g. Delete plan or Cancel trip |
| `.danger-button` | The final confirm button inside a destructive dialog |
| `.button-sm` | Compact size for toolbars and inline rows. Combine it with any variant. |
| `.button-block` | Full-width button |
| `.back-link` | Add to a `.text-button` that starts with "← Back to …" |

**Grouping:** wrap related buttons in `.button-row`, or in an existing row class such as `.modal-actions`, `.form-actions`, `.plan-details-actions`, `.workspace-footer-actions`, or `.confirmation-actions`. Rows handle gaps, wrapping, and vertical alignment, so never space buttons with margins. Button order is primary first, then secondary, then text/cancel. In `.modal-actions` the group aligns right.

Toolbars put secondary actions in a `.plan-actions-group` on the left and the single primary on the right. See `.plan-actions` in `IndependentPlansWorkspace.tsx`.

## Tabs vs. segmented controls (section 6)

| Pattern | Markup | Looks like | Use when |
| --- | --- | --- | --- |
| **Tabs** | `role="tablist"` containing `button role="tab" aria-selected aria-controls`, with a matching `role="tabpanel"` | Text tabs on a baseline, with a coral underline on the active tab | Switching between **panels** of content (plans, flights/stays search, compared itineraries) |
| **Segmented control** | A container with `.segmented` (or the existing `.tabs` / `.trip-filters`) and `role="group"`, holding `button aria-pressed` | A pill track; the active segment is a raised white pill | **Filtering** a list or picking a **mode** (All/Upcoming/Past, Log in/Register) |

The ARIA attributes drive the styling, so you only need the right markup. Tabs must support arrow-key navigation (see `PlanNavigation.tsx`).

## Forms (section 6)

- `.field` wraps a label, the control, and then an optional `.hint` and `.field-error`. Link these with `aria-describedby`. Invalid controls get `aria-invalid="true"`, which turns them red.
- `.field-group` is an auto-fit grid for fields that sit side by side, such as dates or ages.
- `.checkbox-label` is an inline checkbox with a 44px touch target. `.checkbox-list` stacks selectable rows (used in dialogs).
- `.search-controls` is a filter bar of compact fields placed above results.

## Feedback (sections 7–8)

- **Badges:** `.badge` plus a tone: `-upcoming`/`-active`/`-booked` (teal), `-planned`/`-success` (green), `-draft` (gold), `-warning`/`-overage` (red), or `-past`/`-canceled`/`-expired` (neutral). `.count-pill` is a neutral counter. `.missing-component` is the dashed "not selected" pill.
- **Callouts:** `.callout` plus `.callout-success`, `-warning`, `-danger`, or `-neutral` (the default is info). Older banner classes such as `.readiness-banner` and `.booking-conflict-alert` already map to these tones. Use `.callout` for anything new.
- **Toast:** `StatusRegion` renders `.status`, a navy toast at the bottom of the screen. `.error-summary` is the focused error panel at the top.

## Dialogs (section 9)

Markup is `.modal-backdrop > .modal[role=dialog][aria-modal]`, then an optional `.modal-header` (an `h2` and a round close `.text-button`), the body, and finally `.modal-actions`. On phones, dialogs become bottom sheets. `PlanDialog` already renders this structure.

## Travel motifs you can reuse

- `.boarding-pass` > `img` + `.boarding-pass-body` (with `.boarding-pass-route` and a `dl`). It is decorative, so mark it `aria-hidden` when it repeats form data.
- `.route-line` is a dashed line with a plane icon. Put it between two `<span><strong>CODE</strong>City</span>` elements.
- `.trip-route` is a meta line with a coral plane icon in front.
- A ticket perforation is a dashed top border, optionally with notches. See `.trip-card-actions`.

## Responsive behavior

Breakpoints: 1100 (wide builder grid), 1050 (sidebars collapse, flight legs stack), 900 (the boarding-pass aside hides, grids drop to 2 columns), 768 (the comparison matrix becomes a tabbed switcher), 720 (the nav stops being sticky and wraps; result cards stack their price and action; notices join the page flow), 520 (single column; action rows go full-width; dialogs become bottom sheets).

For new dense data, offer a stacked or tabbed mobile view rather than a sideways-scrolling table, as in `ItineraryComparisonView`.

## Checklist for a UI change

- [ ] No new hex colors, inline styles, or one-off font sizes
- [ ] At most one `.primary` per region, and every button has an intentional variant
- [ ] Button groups sit inside a row class
- [ ] Tabs use `role=tab`; filters use `aria-pressed` inside `.segmented`
- [ ] Checked at about 1440px, 820px, and 390px wide
- [ ] `npm test` passes (including `VisualSystem.test.tsx`)
