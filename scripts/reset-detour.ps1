param([switch]$ConfirmReset)

$ErrorActionPreference = 'Stop'
$detourProject = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$detourData = [System.IO.Path]::GetFullPath((Join-Path $detourProject 'data'))
$detourTarget = [System.IO.Path]::GetFullPath((Join-Path $detourData 'detour.mv.db'))

if ([System.IO.Path]::GetDirectoryName($detourTarget) -ne $detourData) {
    throw 'Database target is outside the repository data directory.'
}

Write-Output 'Reset target: data/detour.mv.db'
if (-not $ConfirmReset) {
    Write-Output 'Preview only. Pass -ConfirmReset to remove this exact development database file.'
    return
}

if (Test-Path -LiteralPath $detourTarget -PathType Leaf) {
    Remove-Item -LiteralPath $detourTarget -ErrorAction Stop
    Write-Output 'Removed data/detour.mv.db.'
} else {
    Write-Output 'No data/detour.mv.db file exists; nothing was removed.'
}
