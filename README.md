# DeTour

DeTour is a clean Spring Boot and React foundation for the travel platform's next development stage. It intentionally contains no catalog, trip, or booking behavior.

## Build and run

Requires Java 21+ and Node.js. Maven builds the React shell and packages it into one executable JAR:

```powershell
.\mvnw.cmd package
.\scripts\run.ps1
```

The application listens on loopback. It defaults to port `8082` and stores its local H2 database at `data/detour.mv.db`. No frontend development server, API key, or external provider is required.

Registered DeTour accounts persist in the configured database. Browser sessions are intentionally servlet-memory state, so an application restart requires users to log in again. HTTPS deployments must retain the default secure session-cookie setting; `DETOUR_SECURE_COOKIES=false` is only for local loopback development or automated HTTP tests.

After packaging, verify the packaged identity shell locally with:

```powershell
.\scripts\verify-packaged-identity.ps1
```

The check starts the JAR only on a temporary loopback port with an isolated temporary H2 database and removes both its child process and temporary files afterward.

## Review the deterministic catalog fixtures

Generate a concise Phase 2 fixture report with:

```powershell
.\scripts\show-catalog-fixture-summary.ps1
```

The command compiles the project and migrates a new in-memory H2 database; it does not start the web application or access `data/detour`. The report shows overall counts, one direct and one connecting flight, a representative property for each destination/stay type, and each destination/rental-class daily total and fleet count. It is a fixture-review aid, not an API or UI feature.

Optional environment configuration:

```powershell
$env:DETOUR_PORT = '8083'
$env:DETOUR_DATABASE_URL = 'jdbc:h2:file:./data/detour;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000'
$env:DETOUR_SECURE_COOKIES = 'false' # local loopback only
```

## Reset the disposable development database

Stop the application first. The reset command always previews the exact target, `data/detour.mv.db`, and does not remove anything until the confirmation switch is supplied:

```powershell
.\scripts\reset-detour.ps1
.\scripts\reset-detour.ps1 -ConfirmReset
```

The command only targets that one default database file; custom database locations and neighboring files are not touched. Application startup never deletes local files.
