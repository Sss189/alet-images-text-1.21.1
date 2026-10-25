param([switch]$Smoke,[switch]$Packaged)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskJava=Join-Path $env:USERPROFILE '.gradle\jdks\azul_systems__inc_-21-amd64-windows.2'
if (Test-Path -LiteralPath "$taskJava\bin\java.exe") { $env:JAVA_HOME=$taskJava }
$env:GRADLE_USER_HOME=Join-Path (Split-Path $taskRoot -Parent) '.gradle-user'
Push-Location -LiteralPath $taskRoot
try {
    & .\gradlew.bat --console=plain build
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if ($Packaged) {
        New-Item -ItemType Directory -Path 'run\mods' -Force | Out-Null
        Copy-Item -LiteralPath 'build\libs\alet-images-text-neoforge-1.21.1-0.1.0-alpha.1.jar' -Destination 'run\mods\alet-images-text-neoforge-1.21.1-0.1.0-alpha.1.jar' -Force
    }
    if ($Smoke) {
        $taskFlags=@('--console=plain','runClient','-Psmoke')
        if ($Packaged) { $taskFlags+='-Ppackaged' }
        & .\gradlew.bat @taskFlags
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        $taskLog=Get-Content -LiteralPath 'run\logs\latest.log' -Raw
        if ($taskLog -notmatch 'ALET_SMOKE_PASS' -or $taskLog -match 'ALET_SMOKE_FAIL') { throw 'Smoke test failed; see run/logs/latest.log.' }
    }
} finally { Pop-Location }
