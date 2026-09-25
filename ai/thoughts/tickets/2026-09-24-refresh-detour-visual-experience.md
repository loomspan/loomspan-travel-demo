# Refresh DeTour's visual experience across the app

## Outcome

DeTour feels like a polished, image-rich travel product from Home through trip planning and booking. Travelers can recognize the same visual language, actions, and form controls on every screen. Home offers visual inspiration for the three destinations supported by the demo.

## Requirements

- Use the approved Home mock-up as the visual direction: retain DeTour's teal palette and editorial headings while introducing more open spacing, crisp light surfaces, restrained cards, destination photography, and small purposeful icons. Apply the direction consistently to authentication, Home, Profile, trip creation, component search and selection, comparison, booking, confirmation, cancellation, and dialogs.
- Add a **Featured destinations** section to Home with distinct images and labels for San Francisco, Munich, and Mexico City. These cards provide inspiration; they do not start a trip or change trip details. Keep the existing Plan Trip, Airfare, and Stay entry actions clear and functional.
- Refresh dropdowns and checkboxes across the app with consistent shape, spacing, borders, selected states, hover states, and visible keyboard focus. Preserve native control behavior, labels, keyboard access, disabled states, validation feedback, and readable contrast.
- Use icons to help identify existing navigation and actions without relying on icons or color alone to convey meaning. Provide descriptive alternatives for meaningful imagery and treat decorative imagery and icons appropriately for assistive technology.
- Use project-owned or appropriately licensed, optimized destination images and icons. The mock-up and Booking.com are design references; do not copy Booking.com branding, page layouts, text, or assets.
- Keep the existing trip, pricing, eligibility, booking, cancellation, authentication, and autosave behavior intact. The visual refresh must remain usable at narrow mobile widths and desktop widths.
- Include regression tests for the new Home content and preserved entry actions, and update relevant UI tests for restyled controls or navigation. Verify representative flows and responsive layouts, including keyboard and screen-reader semantics, before completion.

## Acceptance criteria

- [x] Home shows photo-led Featured destinations cards labeled San Francisco, Munich, and Mexico City; Plan Trip, Airfare, and Stay still open their existing flows.
- [x] The approved visual direction is recognizable and consistent across public screens, authenticated planning screens, booking and cancellation screens, and dialogs.
- [x] Dropdowns and checkboxes have consistent polished styling and remain usable by mouse, touch, keyboard, and assistive technology, with clear focus, selected, disabled, and error states.
- [x] Destination imagery and icons have appropriate accessible text or decorative treatment, render reliably, and do not cause layout shifts or horizontal overflow at supported widths.
- [x] Existing trip and booking workflows retain their behavior, and automated regression tests plus responsive and accessibility checks cover the changed experience.

## Context

- The accepted concept mock-up is at `C:\Users\rmelcher\.codex\visualizations\2026\09\24\01a0d52c-5dc0-78e2-bb66-4ec1ab4be7b1\detour-home-mockup.png`. It is a directional reference; the requirements above remain complete if that local file is unavailable.
- Booking.com's travel imagery, compact search controls, and destination-led discovery inspired the request. DeTour should retain its own brand and existing workflows.
- The current application already has a teal and Georgia visual language. This ticket extends the earlier [visual system ticket](2026-09-23-p07-t01-establish-detour-visual-system.md) with imagery, icons, form-control polish, and consistent application across screens.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The requested refresh spans most production screens and requires coordinated design, asset, responsive, accessibility, and regression decisions.
- **Reassessment triggers:** If current-checkout inspection shows the app-wide design is already implemented and only a bounded set of surfaces remains, reassess the route.
