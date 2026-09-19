$ErrorActionPreference = 'Stop'
$detourProject = Split-Path -Parent $PSScriptRoot

Push-Location $detourProject
try {
    & .\mvnw.cmd '-q' '-DskipFrontend=true' compile
    if ($LASTEXITCODE -ne 0) { throw "Maven compile failed with exit code $LASTEXITCODE." }
    & .\mvnw.cmd '-q' '-DskipFrontend=true' '-Dexec.mainClass=app.detour.catalog.CatalogFixtureSummary' exec:java
    if ($LASTEXITCODE -ne 0) { throw "Catalog fixture summary failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
