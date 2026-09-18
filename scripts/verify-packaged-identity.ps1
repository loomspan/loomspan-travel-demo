$ErrorActionPreference = 'Stop'
$detourProject = Split-Path -Parent $PSScriptRoot
$detourJar = Join-Path $detourProject 'target\detour-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $detourJar)) { throw 'Build first with .\mvnw.cmd package.' }

$detourTemp = Join-Path ([System.IO.Path]::GetTempPath()) ("detour-identity-" + [guid]::NewGuid())
$detourOutput = Join-Path $detourTemp 'application.out.log'
$detourError = Join-Path $detourTemp 'application.err.log'
$detourJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$priorPort = $env:DETOUR_PORT; $priorDatabase = $env:DETOUR_DATABASE_URL; $priorSecureCookies = $env:DETOUR_SECURE_COOKIES
$detourProcess = $null

New-Item -ItemType Directory -Path $detourTemp | Out-Null
$detourListener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$detourListener.Start()
$detourPort = ($detourListener.LocalEndpoint).Port
$detourListener.Stop()

try {
    $env:DETOUR_PORT = "$detourPort"
    $env:DETOUR_DATABASE_URL = "jdbc:h2:file:$($detourTemp.Replace('\', '/'))/identity;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000"
    $env:DETOUR_SECURE_COOKIES = 'false'
    $detourProcess = Start-Process -FilePath $detourJava -ArgumentList @('-jar', $detourJar) -PassThru -WindowStyle Hidden -RedirectStandardOutput $detourOutput -RedirectStandardError $detourError
    $detourBase = "http://127.0.0.1:$detourPort"
    $detourReady = $false
    foreach ($attempt in 1..40) {
        if ($detourProcess.HasExited) { throw "The packaged application stopped early. $((Get-Content -Raw $detourError -ErrorAction SilentlyContinue))" }
        try {
            $root = Invoke-WebRequest -Uri "$detourBase/" -UseBasicParsing -TimeoutSec 2
            if ($root.StatusCode -eq 200 -and $root.Content -match 'DeTour') { $detourReady = $true; break }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $detourReady) { throw "The packaged application did not become ready. $((Get-Content -Raw $detourError -ErrorAction SilentlyContinue))" }
    $profile = Invoke-WebRequest -Uri "$detourBase/profile" -UseBasicParsing -TimeoutSec 5
    if ($profile.StatusCode -ne 200 -or $profile.Content -notmatch 'DeTour') { throw 'The packaged profile document did not serve the identity shell.' }
    Write-Output 'Packaged identity shell verification passed.'
} finally {
    if ($null -ne $detourProcess -and -not $detourProcess.HasExited) { Stop-Process -Id $detourProcess.Id -Force }
    if ($null -eq $priorPort) { Remove-Item Env:DETOUR_PORT -ErrorAction SilentlyContinue } else { $env:DETOUR_PORT = $priorPort }
    if ($null -eq $priorDatabase) { Remove-Item Env:DETOUR_DATABASE_URL -ErrorAction SilentlyContinue } else { $env:DETOUR_DATABASE_URL = $priorDatabase }
    if ($null -eq $priorSecureCookies) { Remove-Item Env:DETOUR_SECURE_COOKIES -ErrorAction SilentlyContinue } else { $env:DETOUR_SECURE_COOKIES = $priorSecureCookies }
    if (Test-Path -LiteralPath $detourTemp) { Remove-Item -LiteralPath $detourTemp -Recurse -Force }
}
