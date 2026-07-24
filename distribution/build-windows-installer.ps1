param(
    [string]$Version = "0.1.0",
    [string]$OutputDirectory = "dist"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$dist = Join-Path $repo $OutputDirectory
$appImage = Join-Path $dist ".m10-windows\image\morpheus"
$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"

if (-not (Test-Path $jpackage)) {
    throw "jpackage.exe not found under JAVA_HOME=$env:JAVA_HOME"
}
if (-not (Test-Path $appImage)) {
    Write-Host "Portable app-image not found; building it first..."
    & (Join-Path $PSScriptRoot "build-portable.ps1") -Version $Version -OutputDirectory $OutputDirectory
    if ($LASTEXITCODE -ne 0) { throw "Portable build failed with exit code $LASTEXITCODE" }
}

$wix = Get-Command wix.exe -ErrorAction SilentlyContinue
$candle = Get-Command candle.exe -ErrorAction SilentlyContinue
$light = Get-Command light.exe -ErrorAction SilentlyContinue
$hasWixModern = $null -ne $wix
$hasWix3 = $null -ne $candle -and $null -ne $light

if (-not $hasWixModern -and -not $hasWix3) {
    Write-Warning "WiX was not found on PATH. Windows EXE/MSI packaging is optional and will be skipped."
    Write-Host "Portable app-image remains valid: $appImage"
    Write-Host "Install WiX and rerun this script when an EXE installer is required."
    return
}

$installerDir = Join-Path $dist "installer"
New-Item $installerDir -ItemType Directory -Force | Out-Null

if ($hasWixModern) {
    Write-Host "WiX detected: $($wix.Source)"
} else {
    Write-Host "WiX v3 tools detected: candle=$($candle.Source), light=$($light.Source)"
}

Write-Host "Building Windows EXE installer from the validated app-image..."
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
