param(
    [string]$Version = "0.1.0",
    [string]$OutputDirectory = "dist"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$dist = Join-Path $repo $OutputDirectory
$work = Join-Path $dist ".m17-windows"
$input = Join-Path $work "input"
$appImageRoot = Join-Path $work "image"

$mvnw = Join-Path $repo "mvnw.cmd"
$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
$jarTool = Join-Path $env:JAVA_HOME "bin\jar.exe"
if (-not (Test-Path $jpackage)) { throw "jpackage.exe not found under JAVA_HOME=$env:JAVA_HOME" }
if (-not (Test-Path $jarTool)) { throw "jar.exe not found under JAVA_HOME=$env:JAVA_HOME" }

function Compress-PortableArchiveWithRetry {
    param([Parameter(Mandatory = $true)][string]$SourceDirectory,
          [Parameter(Mandatory = $true)][string]$DestinationArchive,
          [int]$MaxAttempts = 8)
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        Remove-Item $DestinationArchive -Force -ErrorAction SilentlyContinue
        try {
            Compress-Archive -Path $SourceDirectory -DestinationPath $DestinationArchive -CompressionLevel Optimal -ErrorAction Stop
            if (-not (Test-Path $DestinationArchive)) { throw "Compress-Archive returned without creating $DestinationArchive" }
            $archiveInfo = Get-Item $DestinationArchive -ErrorAction Stop
            if ($archiveInfo.Length -le 0) { throw "Compress-Archive created an empty archive: $DestinationArchive" }
            Write-Host "Portable archive creation: PASS (attempt $attempt/$MaxAttempts, $($archiveInfo.Length) bytes)"
            return
        } catch {
            Remove-Item $DestinationArchive -Force -ErrorAction SilentlyContinue
            if ($attempt -eq $MaxAttempts) { throw "Unable to create portable Windows archive after $MaxAttempts attempts: $($_.Exception.Message)" }
            $delayMilliseconds = 500 * $attempt
            Write-Warning "Portable archive attempt $attempt/$MaxAttempts failed, likely due to a transient file lock: $($_.Exception.Message)"
            Start-Sleep -Milliseconds $delayMilliseconds
        }
    }
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port } finally { $listener.Stop() }
}

function Test-PackagedApiHealth {
    param([Parameter(Mandatory = $true)][string]$Launcher,
          [Parameter(Mandatory = $true)][string]$WorkDirectory)
    $port = Get-FreeLoopbackPort
    $apiData = Join-Path $WorkDirectory "api-smoke-data"
    $stdout = Join-Path $WorkDirectory "api-smoke.stdout.log"
    $stderr = Join-Path $WorkDirectory "api-smoke.stderr.log"
    New-Item $apiData -ItemType Directory -Force | Out-Null
    Remove-Item $stdout, $stderr -Force -ErrorAction SilentlyContinue
    $process = Start-Process -FilePath $Launcher `
        -ArgumentList @("--data-dir", $apiData, "api", "--host", "127.0.0.1", "--port", "$port") `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    try {
        $uri = "http://127.0.0.1:$port/api/v1/health"
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) {
                $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { "" }
                throw "Packaged API exited before health check. stderr=$diagnostic"
            }
            try {
                $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
                if ($response.StatusCode -eq 200 -and $response.Content -match '"status":"UP"') {
                    Write-Host "Packaged API health smoke: PASS ($uri)"
                    return
                }
            } catch { Start-Sleep -Milliseconds 100 }
        }
        $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { "" }
        throw "Packaged API health smoke timed out. stderr=$diagnostic"
    } finally {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        try { $process.WaitForExit(5000) | Out-Null } catch { }
        Start-Sleep -Milliseconds 300
    }
}

Write-Host "Building MORPHEUS CLI + MCP + API + optional MINOS/NEXUS adapters + M14-M17 contracts uber-JAR..."
& $mvnw -pl morpheus-cli -am -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Maven package failed with exit code $LASTEXITCODE" }

$jar = Get-ChildItem (Join-Path $repo "morpheus-cli\target") -Filter "morpheus-cli-*-all.jar" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $jar) { throw "Shaded MORPHEUS CLI JAR not found" }

Write-Host "Verifying MCP/API/MINOS/NEXUS/M14-M17 classes are embedded in the shaded JAR..."
$jarEntries = & $jarTool tf $jar.FullName
if ($LASTEXITCODE -ne 0) { throw "Unable to inspect shaded JAR" }
$requiredEntries = @(
    "com/morpheus/mcp/MorpheusMcpServer.class",
    "com/morpheus/mcp/MorpheusJarvisOrchestrationMcpTools.class",
    "com/morpheus/mcp/MorpheusControlledLifecycleMcpTools.class",
    "io/modelcontextprotocol/server/McpServer.class",
    "io/modelcontextprotocol/client/McpClient.class",
    "io/modelcontextprotocol/client/transport/StdioClientTransport.class",
    "com/morpheus/api/MorpheusHttpServer.class",
    "com/morpheus/api/MorpheusJarvisOrchestrationApiService.class",
    "com/morpheus/api/MorpheusControlledLifecycleApiService.class",
    "com/morpheus/cli/MorpheusJarvisOrchestrationCli.class",
    "com/morpheus/cli/MorpheusControlledLifecycleCli.class",
    "com/morpheus/application/orchestration/ChangeOrchestrationStateService.class",
    "com/morpheus/application/orchestration/ChangeTransitionEvaluationService.class",
    "com/morpheus/application/lifecycle/mutation/ControlledChangeLifecycleMutationService.class",
    "com/morpheus/store/sqlite/SqliteChangeLifecycleMutationStore.class",
    "com/morpheus/integration/minos/MinosMcpExternalReferenceResolver.class",
    "com/morpheus/integration/minos/MinosMcpCodeGateway.class",
    "com/morpheus/integration/minos/MinosIntegrationRuntime.class",
    "com/morpheus/integration/nexus/NexusMcpContextGateway.class",
    "com/morpheus/integration/nexus/NexusMcpTechnicalContextProvider.class",
    "com/morpheus/integration/nexus/NexusIntegrationRuntime.class",
    "tools/jackson/databind/json/JsonMapper.class"
)
foreach ($entry in $requiredEntries) {
    if ($jarEntries -notcontains $entry) { throw "M17 packaging proof failed; shaded JAR is missing $entry" }
}
$embeddedMinosDomain = $jarEntries | Where-Object { $_ -like "com/minos/*" }
if ($embeddedMinosDomain) { throw "M17 packaging proof failed; MINOS implementation classes must not be embedded: $($embeddedMinosDomain | Select-Object -First 5)" }
$embeddedNexusDomain = $jarEntries | Where-Object { $_ -like "com/nexus/*" }
if ($embeddedNexusDomain) { throw "M17 packaging proof failed; NEXUS implementation classes must not be embedded: $($embeddedNexusDomain | Select-Object -First 5)" }
$embeddedJarvisDomain = $jarEntries | Where-Object { $_ -like "com/jarvis/*" }
if ($embeddedJarvisDomain) { throw "M17 packaging proof failed; JARVIS implementation classes must not be embedded: $($embeddedJarvisDomain | Select-Object -First 5)" }
Write-Host "MCP/API/MINOS/NEXUS/M14-M17 packaging proof: PASS"

Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item $input -ItemType Directory -Force | Out-Null
New-Item $appImageRoot -ItemType Directory -Force | Out-Null
Copy-Item $jar.FullName (Join-Path $input "morpheus.jar")

Write-Host "Creating self-contained Windows app-image with embedded runtime + jdk.httpserver..."
& $jpackage --type app-image --name morpheus --app-version $Version `
    --description "MORPHEUS Specification & Intent Intelligence Engine" `
    --input $input --main-jar "morpheus.jar" --main-class "com.morpheus.cli.MorpheusMain" `
    --add-modules jdk.httpserver --win-console --dest $appImageRoot
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed with exit code $LASTEXITCODE" }

$launcher = Join-Path $appImageRoot "morpheus\morpheus.exe"
if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }

Write-Host "Smoke testing packaged launcher without MINOS/NEXUS/JARVIS or write-capable provider configuration..."
& $launcher --version
if ($LASTEXITCODE -ne 0) { throw "Packaged launcher --version smoke test failed with exit code $LASTEXITCODE" }
$jsonVersion = & $launcher --json version
if ($LASTEXITCODE -ne 0 -or $jsonVersion -notmatch '"version"') { throw "Packaged launcher JSON version smoke failed: $jsonVersion" }
Write-Host $jsonVersion

$minosStatus = & $launcher --json minos-status
if ($LASTEXITCODE -ne 0 -or $minosStatus -notmatch '"state":"DISABLED"') {
    throw "Packaged standalone MINOS status smoke failed: $minosStatus"
}
Write-Host $minosStatus

$nexusStatus = & $launcher --json nexus-status
if ($LASTEXITCODE -ne 0 -or $nexusStatus -notmatch '"state":"DISABLED"') {
    throw "Packaged standalone NEXUS status smoke failed: $nexusStatus"
}
Write-Host $nexusStatus

$help = (& $launcher help) -join "`n"
if ($LASTEXITCODE -ne 0 -or $help -notmatch 'change-orchestration' -or $help -notmatch 'lifecycle apply') {
    throw "Packaged M14/M17 CLI help smoke failed: $help"
}
Write-Host "Packaged standalone optional-engines + M14 read-only + M17 controlled-write surface smoke: PASS"

Test-PackagedApiHealth -Launcher $launcher -WorkDirectory $work

New-Item $dist -ItemType Directory -Force | Out-Null
$archive = Join-Path $dist "morpheus-$Version-windows-x64.zip"
Compress-PortableArchiveWithRetry -SourceDirectory (Join-Path $appImageRoot "morpheus") -DestinationArchive $archive
if (-not (Test-Path $archive)) { throw "Portable Windows archive is missing after archive creation: $archive" }

Write-Host "Portable Windows distribution: $archive"
Write-Host "The archive contains MORPHEUS, its Java runtime, MCP/API, optional MINOS/NEXUS client adapters, the M14 read-only orchestration contract and the M17 controlled lifecycle mutation surface. MINOS, NEXUS and JARVIS are not embedded or required; lifecycle writes still require an explicit WRITE_CHANGE-capable provider."
