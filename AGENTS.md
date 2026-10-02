# AGENTS.md

DeTour is a Spring Boot (Java 21) + React/TypeScript (Vite) trip planner. See `README.md` for architecture and running the app.

## Build and test

- Frontend tests: `cd frontend && npm test` (Vitest + Testing Library, jsdom)
- Frontend typecheck/build: `cd frontend && npm run build`
- Full build and backend tests: `.\mvnw.cmd clean verify` (also bundles the frontend into the JAR)

## UI and styling

- Every visual change must follow `frontend/DESIGN.md`, the "Golden Hour" design system. Read it before touching components or CSS.
- All styles live in `frontend/src/style.css`, in numbered sections: tokens, primitives, screens, responsive, accessibility. Reuse tokens and primitives (`.card`, `.primary`/`.secondary`/`.text-button`/`.button-danger-outline`, `.button-row`, `role="tablist"` tabs, `.segmented` filters, `.badge-*`, `.callout-*`, `.modal`) instead of adding one-off rules.
- Don't use inline `style={{…}}`, hard-coded colors in component rules, or buttons without an intended variant.
- `frontend/src/VisualSystem.test.tsx` asserts some CSS rules with regexes. Keep them passing.
