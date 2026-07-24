param(
    [string]$Version = "0.1.0",
    [string]$OutputDirectory = "dist"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$dist = Join-Path $repo $OutputDirectory
$work = Join-Path $dist ".m10-windows"
$input = Join-Path $work "input"
$appImageRoot = Join-Path $work "image"

$mvnw = Join-Path $repo "mvnw.cmd"
$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
$jarTool = Join-Path $env:JAVA_HOME "bin\jar.exe"
if (-not (Test-Path $jpackage)) {
    throw "jpackage.exe not found under JAVA_HOME=$env:JAVA_HOME"
}
if (-not (Test-Path $jarTool)) {
    throw "jar.exe not found under JAVA_HOME=$env:JAVA_HOME"
}

Write-Host "Building MORPHEUS CLI + MCP uber-JAR..."
& $mvnw -pl morpheus-cli -am -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Maven package failed with exit code $LASTEXITCODE" }

$jar = Get-ChildItem (Join-Path $repo "morpheus-cli\target") -Filter "morpheus-cli-*-all.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $jar) { throw "Shaded MORPHEUS CLI JAR not found" }

Write-Host "Verifying MCP classes are embedded in the shaded JAR..."
$jarEntries = & $jarTool tf $jar.FullName
if ($LASTEXITCODE -ne 0) { throw "Unable to inspect shaded JAR" }
$requiredEntries = @(
    "com/morpheus/mcp/MorpheusMcpServer.class",
    "io/modelcontextprotocol/server/McpServer.class",
    "io/modelcontextprotocol/server/transport/StdioServerTransportProvider.class"
)
foreach ($entry in $requiredEntries) {
    if ($jarEntries -notcontains $entry) {
        throw "MCP packaging proof failed; shaded JAR is missing $entry"
    }
}
Write-Host "MCP packaging proof: PASS"

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
    --main-class "com.morpheus.cli.MorpheusMain" `
    --win-console `
    --dest $appImageRoot
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed with exit code $LASTEXITCODE" }

$launcher = Join-Path $appImageRoot "morpheus\morpheus.exe"
if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }

Write-Host "Smoke testing packaged launcher..."
& $launcher --version
if ($LASTEXITCODE -ne 0) { throw "Packaged launcher --version smoke test failed with exit code $LASTEXITCODE" }

$jsonVersion = & $launcher --json version
if ($LASTEXITCODE -ne 0) { throw "Packaged launcher --json version smoke test failed with exit code $LASTEXITCODE" }
if ($jsonVersion -notmatch '"version"') {
    throw "Packaged launcher --json version did not emit the expected JSON version field: $jsonVersion"
}
Write-Host $jsonVersion

New-Item $dist -ItemType Directory -Force | Out-Null
$archive = Join-Path $dist "morpheus-$Version-windows-x64.zip"
Remove-Item $archive -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $appImageRoot "morpheus") -DestinationPath $archive -CompressionLevel Optimal

Write-Host "Portable Windows distribution: $archive"
Write-Host "The archive contains its Java runtime and MCP STDIO server; end users do not need a separately installed JDK."
