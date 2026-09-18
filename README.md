# DeTour

DeTour is a clean Spring Boot and React foundation for the travel platform's next development stage. It intentionally contains no catalog, trip, booking, or model-service behavior.

## Build and run

Requires Java 21+ and Node.js. Maven builds the React shell and packages it into one executable JAR:

```powershell
.\mvnw.cmd package
.\scripts\run.ps1
```

The application listens on loopback. It defaults to port `8082` and stores its local H2 database at `data/detour.mv.db`. No frontend development server, model service, API key, or external provider is required.

Optional environment configuration:

```powershell
$env:DETOUR_PORT = '8083'
$env:DETOUR_DATABASE_URL = 'jdbc:h2:file:./data/detour;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000'
```

## Reset the disposable development database

Stop the application first. The reset command always previews the exact target, `data/detour.mv.db`, and does not remove anything until the confirmation switch is supplied:

```powershell
.\scripts\reset-detour.ps1
.\scripts\reset-detour.ps1 -ConfirmReset
```

The command only targets that one default database file; custom database locations and neighboring files are not touched. Application startup never deletes local files.
