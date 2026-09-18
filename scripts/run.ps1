$ErrorActionPreference = 'Stop'
$detourProject = Split-Path -Parent $PSScriptRoot
$detourJar = Join-Path $detourProject 'target\detour-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $detourJar)) { throw 'Build first with .\mvnw.cmd package.' }
$detourJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
Push-Location $detourProject
try { & $detourJava -jar $detourJar } finally { Pop-Location }
