$ErrorActionPreference = 'Stop'
$travelProject = Split-Path -Parent $PSScriptRoot
$travelJar = Join-Path $travelProject 'target\wayfarer-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $travelJar)) { throw 'Build first with .\mvnw.cmd package.' }
$travelJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
Push-Location $travelProject
try { & $travelJava -jar $travelJar } finally { Pop-Location }
