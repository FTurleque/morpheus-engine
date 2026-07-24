param(
    [string]$Version = "0.1.0",
    [string]$OutputDirectory = "dist"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$dist = Join-Path $repo $OutputDirectory
$appImage = Join-Path $dist ".m9-windows\image\morpheus"
$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"

if (-not (Test-Path $jpackage)) {
    throw "jpackage.exe not found under JAVA_HOME=$env:JAVA_HOME"
}
if (-not (Test-Path $appImage)) {
    Write-Host "Portable app-image not found; building it first..."
    & (Join-Path $PSScriptRoot "build-portable.ps1") -Version $Version -OutputDirectory $OutputDirectory
    if ($LASTEXITCODE -ne 0) { throw "Portable build failed with exit code $LASTEXITCODE" }
}

$installerDir = Join-Path $dist "installer"
New-Item $installerDir -ItemType Directory -Force | Out-Null

Write-Host "Building Windows EXE installer from the validated app-image..."
Write-Host "Note: jpackage requires WiX to create Windows MSI/EXE installers."
& $jpackage `
    --type exe `
    --name morpheus `
    --app-version $Version `
    --description "MORPHEUS Specification & Intent Intelligence Engine" `
    --app-image $appImage `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut `
    --dest $installerDir
if ($LASTEXITCODE -ne 0) { throw "jpackage Windows installer failed with exit code $LASTEXITCODE" }

Write-Host "Windows installer output: $installerDir"
Write-Host "MORPHEUS data remains outside the installation directory, so uninstall/reinstall does not intentionally delete the SQLite data directory."