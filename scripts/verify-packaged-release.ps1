$ErrorActionPreference = 'Stop'
$detourProject = Split-Path -Parent $PSScriptRoot
Push-Location $detourProject
try {
    & node .\scripts\verify-packaged-release.mjs
    if ($LASTEXITCODE -ne 0) { throw "Packaged release verification failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
