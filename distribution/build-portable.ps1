param(
    [string]$Version = "0.1.0",
    [string]$OutputDirectory = "dist"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$dist = Join-Path $repo $OutputDirectory
$work = Join-Path $dist ".m9-windows"
$input = Join-Path $work "input"
$appImageRoot = Join-Path $work "image"

$mvnw = Join-Path $repo "mvnw.cmd"
$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $jpackage)) {
    throw "jpackage.exe not found under JAVA_HOME=$env:JAVA_HOME"
}

Write-Host "Building MORPHEUS CLI uber-JAR..."
& $mvnw -pl morpheus-cli -am -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Maven package failed with exit code $LASTEXITCODE" }

$jar = Get-ChildItem (Join-Path $repo "morpheus-cli\target") -Filter "morpheus-cli-*-all.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $jar) { throw "Shaded MORPHEUS CLI JAR not found" }

Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item $input -ItemType Directory -Force | Out-Null
New-Item $appImageRoot -ItemType Directory -Force | Out-Null
Copy-Item $jar.FullName (Join-Path $input "morpheus.jar")

Write-Host "Creating self-contained Windows app-image with embedded runtime..."
& $jpackage `
    --type app-image `
    --name morpheus `
    --app-version $Version `
    --description "MORPHEUS Specification & Intent Intelligence Engine" `
    --input $input `
    --main-jar "morpheus.jar" `
    --main-class "com.morpheus.cli.MorpheusCli" `
    --win-console `
    --dest $appImageRoot
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed with exit code $LASTEXITCODE" }

$launcher = Join-Path $appImageRoot "morpheus\morpheus.exe"
if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }

Write-Host "Smoke testing packaged launcher..."
& $launcher --version
if ($LASTEXITCODE -ne 0) { throw "Packaged launcher smoke test failed with exit code $LASTEXITCODE" }

New-Item $dist -ItemType Directory -Force | Out-Null
$archive = Join-Path $dist "morpheus-$Version-windows-x64.zip"
Remove-Item $archive -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $appImageRoot "morpheus") -DestinationPath $archive -CompressionLevel Optimal

Write-Host "Portable Windows distribution: $archive"
Write-Host "The archive contains its Java runtime; end users do not need a separately installed JDK."