param([switch]$ConfirmReset)
$ErrorActionPreference = 'Stop'
if (-not $ConfirmReset) { throw 'Stop Wayfarer, then pass -ConfirmReset to remove its default local database and all saved trips.' }
$travelProject = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$travelData = [System.IO.Path]::GetFullPath((Join-Path $travelProject 'data'))
foreach ($travelName in @('wayfarer.mv.db','wayfarer.trace.db')) {
    $travelTarget = [System.IO.Path]::GetFullPath((Join-Path $travelData $travelName))
    if ([System.IO.Path]::GetDirectoryName($travelTarget) -ne $travelData) { throw 'Database target is outside the default data directory.' }
    if (Test-Path -LiteralPath $travelTarget) { Remove-Item -LiteralPath $travelTarget -ErrorAction Stop }
}
Write-Output 'Default Wayfarer database removed. Flyway recreates fresh inventory at the next startup. Custom database locations were not touched.'
