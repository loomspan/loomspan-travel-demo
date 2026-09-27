# DeTour

DeTour is a local Spring Boot and React trip planner. Its catalog is fictional: airfare, accommodations, and rental cars for departures from Portland (PDX) to San Francisco, Munich, or Mexico City on supported March 2027 dates. Bookings are simulated; no payment or real reservation occurs. The application uses no AI/model service, supplier API, or API key. Events, exchanges, disruption recovery, public catalog browsing, deployment, native apps, dark mode, and localization are outside this release.

## Requirements and first run

- Java 21 or newer; Node.js and npm for a source build; PowerShell for the supplied helper scripts.
- From the repository root, build and run:

```powershell
.\mvnw.cmd clean package
$env:DETOUR_SECURE_COOKIES = 'false' # local loopback HTTP only
.\scripts\run.ps1
```

Open <http://127.0.0.1:8082/>. The JAR serves the React app and the API together; no separate frontend server is needed. The first run creates the default `data/detour.mv.db` through Flyway and seeds fictional catalog data, but creates no user. Home and Trips are public, and registration is available when saving a Trip. No external credentials are needed.

The server listens on loopback only. For local HTTP development, set `DETOUR_SECURE_COOKIES=false` before running so the browser can send the session cookie over HTTP. Leave secure cookies enabled for HTTPS deployments. The packaged release verifier sets this only for its isolated loopback process.

## First trip workflow

1. Open public **Home**, then go to **Trips**. Enter a Trip name, destination, March 2027 travel dates, traveler count and ages. Budget is optional. Choose **Start planning** to log in or register, then choose **Start planning** again to explicitly save the entered Trip.
2. The new Trip has one **Working plan**. Search and select an eligible flight, stay, or rental car. Working plan changes save to that same plan; its dates can change, and selections are rechecked for the new dates.
3. Give the Working plan a name and choose **Save new option** to create a **Saved option** with its own dates. You can change the Working plan and save another named option, then compare up to three options. Open an unbooked option in Working to update it in place, or explicitly save a new option to keep both.
4. Review an eligible Saved option, including its server-calculated total and fictional-booking notice, then confirm a simulated Booking. Record the displayed booking reference. An active Booking can be canceled before its option's departure date begins in Portland time; canceled bookings remain in history. **Profile** contains account details.

Trips, their Working plans and Saved options, and booked snapshots are stored in the configured H2 database. Profile groups Upcoming and Past Trips using the application clock. A server restart clears in-memory sessions, so log in again; it does not remove accounts, Trips, bookings, or cancellation history. Unsaved trip-start fields are held in the current browser view during login or registration and a failed save; reload the page only after saving.

## Architecture and configuration

React/TypeScript in `frontend/src` talks to Spring MVC JSON endpoints under `/api`. Spring Security handles servlet sessions and CSRF. Service and JDBC repository layers in `src/main/java/app/detour` own identity, Trips, catalog search, booking, inventory, and cancellation. The server rechecks ownership, versions, eligibility, prices, totals, and inventory; the browser is not the authority for a Booking. Booking and cancellation use database transactions and idempotency/uniqueness rules. Flyway migrations in `src/main/resources/db/migration` are the only schema and catalog lineage, starting at DeTour V1. The JAR contains the built React assets.

Defaults are in `src/main/resources/application.yml`:

| Setting | Default | Purpose |
| --- | --- | --- |
| `DETOUR_PORT` | `8082` | Loopback HTTP port. |
| `DETOUR_DATABASE_URL` | `jdbc:h2:file:./data/detour;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000` | Persistent H2 file, relative to the working directory. |
| `DETOUR_SECURE_COOKIES` | `true` | Secure session and CSRF cookies; set `false` only for local loopback HTTP. |
| `detour.clock.fixed-instant` | unset | Optional ISO instant for deterministic local verification; production uses the Portland clock. |

For example, a separate local database and port can be chosen before starting the JAR:

```powershell
$env:DETOUR_PORT = '8083'
$env:DETOUR_DATABASE_URL = 'jdbc:h2:file:./data/detour-local;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000'
$env:DETOUR_SECURE_COOKIES = 'false'
.\scripts\run.ps1
```

Run from the repository root if using the default relative database URL. Do not use the same H2 file from two application processes at once.

## Inspect the deterministic fixtures

```powershell
.\scripts\show-catalog-fixture-summary.ps1
```

The script compiles the project, migrates a new in-memory H2 database, and prints destination, flight, property, rental class, price, and inventory examples. It does not start the web app or touch `data/detour`.

## Build and verify

```powershell
Push-Location frontend
npm ci
npm test
Pop-Location
.\mvnw.cmd clean verify
.\scripts\verify-packaged-release.ps1
```

`npm test` runs frontend interaction and accessibility assertions; Maven runs backend unit and HTTP/integration tests, inventory concurrency and idempotency tests, clean-database migration, clock-boundary and restart/persistence tests. `clean verify` also compiles the frontend and produces `target/detour-0.1.0-SNAPSHOT.jar`. The packaged verifier starts that JAR on a temporary loopback port with an isolated temporary H2 database, checks authentication and CSRF through a Trip with one Working plan, two named Saved options with different dates, booking and cancellation, then restarts the app and checks that a new login recovers the same Trip, options, and booking history. It checks owner isolation and removes its own process and files. It does not touch the default development database. See `ai/thoughts/release/2026-09-23-p07-t05-verification.md` for prior release results and any remaining visual checks.

## Reset disposable development data

Development data uses a clean-break policy: no compatibility schema or migration from superseded app states is maintained. Stop the app before resetting the *default* local database. The first command only previews the exact target; the second explicitly confirms removal:

```powershell
.\scripts\reset-detour.ps1
.\scripts\reset-detour.ps1 -ConfirmReset
```

This script only removes `data/detour.mv.db`, never a custom database or neighboring file. Back up any data you wish to keep before confirming. Application startup never deletes the database file. On the next start, Flyway creates a clean DeTour database and reseeds the fictional catalog.
