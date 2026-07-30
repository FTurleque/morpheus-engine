param(
    [string]$Version = "1.1.0",
    [string]$OutputDirectory = "dist"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$dist = Join-Path $repo $OutputDirectory
$work = Join-Path $dist ".m20-windows"
$input = Join-Path $work "input"
$appImageRoot = Join-Path $work "image"

$mvnw = Join-Path $repo "mvnw.cmd"
$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
$jarTool = Join-Path $env:JAVA_HOME "bin\jar.exe"
$jimageTool = Join-Path $env:JAVA_HOME "bin\jimage.exe"
if (-not (Test-Path $jpackage)) { throw "jpackage.exe not found under JAVA_HOME=$env:JAVA_HOME" }
if (-not (Test-Path $jarTool)) { throw "jar.exe not found under JAVA_HOME=$env:JAVA_HOME" }
if (-not (Test-Path $jimageTool)) { throw "jimage.exe not found under JAVA_HOME=$env:JAVA_HOME" }

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

function Test-PackagedApiOperability {
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
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    try {
        $healthUri = "http://127.0.0.1:$port/api/v1/health"
        $readinessUri = "http://127.0.0.1:$port/api/v1/readiness"
        $metricsUri = "http://127.0.0.1:$port/api/v1/metrics"
        $versionUri = "http://127.0.0.1:$port/api/v1/version"
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) {
                $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { "" }
                throw "Packaged API exited before health check. stderr=$diagnostic"
            }
            try {
                $health = Invoke-WebRequest -Uri $healthUri -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
                $readiness = Invoke-WebRequest -Uri $readinessUri -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
                $metrics = Invoke-WebRequest -Uri $metricsUri -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
                $versionResponse = Invoke-WebRequest -Uri $versionUri -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
                $versionPayload = $versionResponse.Content | ConvertFrom-Json
                if ($health.StatusCode -eq 200 -and $health.Content -match '"status":"UP"' `
                        -and $readiness.StatusCode -eq 200 -and $readiness.Content -match '"status":"READY"' `
                        -and $metrics.StatusCode -eq 200 -and $metrics.Content -match '"counters"' `
                        -and $versionResponse.StatusCode -eq 200 -and [string]$versionPayload.data.version -eq $Version) {
                    Write-Host "Packaged API health/readiness/metrics/version smoke: PASS (http://127.0.0.1:$port/api/v1)"
                    return
                }
            } catch { Start-Sleep -Milliseconds 100 }
        }
        $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { "" }
        throw "Packaged API operability/version smoke timed out. stderr=$diagnostic"
    } finally {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        try { $process.WaitForExit(5000) | Out-Null } catch { }
        Start-Sleep -Milliseconds 300
    }
}

Write-Host "Building MORPHEUS $Version CLI + MCP + API + provider SDK + optional MINOS/NEXUS adapters + M14-M28 contracts uber-JAR..."
& $mvnw -pl morpheus-cli -am -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Maven package failed with exit code $LASTEXITCODE" }

$jar = Get-ChildItem (Join-Path $repo "morpheus-cli\target") -Filter "morpheus-cli-*-all.jar" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $jar) { throw "Shaded MORPHEUS CLI JAR not found" }

Write-Host "Verifying baseline MCP/API/provider-SDK/MINOS/NEXUS classes are embedded in the shaded JAR..."
$jarEntries = & $jarTool tf $jar.FullName
if ($LASTEXITCODE -ne 0) { throw "Unable to inspect shaded JAR" }
$requiredEntries = @(
    "com/morpheus/mcp/MorpheusMcpServer.class",
    "com/morpheus/mcp/MorpheusJarvisOrchestrationMcpTools.class",
    "com/morpheus/mcp/MorpheusControlledLifecycleMcpTools.class",
    "com/morpheus/mcp/MorpheusCompositionMcpTools.class",
    "com/morpheus/mcp/MorpheusProductMcpTools.class",
    "com/morpheus/mcp/MorpheusProviderPluginMcpTools.class",
    "io/modelcontextprotocol/server/McpServer.class",
    "io/modelcontextprotocol/client/McpClient.class",
    "io/modelcontextprotocol/client/transport/StdioClientTransport.class",
    "com/morpheus/api/MorpheusHttpServer.class",
    "com/morpheus/api/MorpheusJarvisOrchestrationApiService.class",
    "com/morpheus/api/MorpheusControlledLifecycleApiService.class",
    "com/morpheus/api/MorpheusCompositionApiService.class",
    "com/morpheus/api/MorpheusOperabilityApiService.class",
    "com/morpheus/api/MorpheusProviderPluginApiService.class",
    "com/morpheus/cli/MorpheusJarvisOrchestrationCli.class",
    "com/morpheus/cli/MorpheusControlledLifecycleCli.class",
    "com/morpheus/cli/MorpheusCompositionCli.class",
    "com/morpheus/cli/MorpheusProductCli.class",
    "com/morpheus/cli/MorpheusProviderPluginCli.class",
    "com/morpheus/sdk/provider/MorpheusProviderPlugin.class",
    "com/morpheus/sdk/provider/ProviderPluginService.class",
    "com/morpheus/application/product/ProductMetadata.class",
    "com/morpheus/application/product/UpdateDiscoveryService.class",
    "com/morpheus/application/orchestration/ChangeOrchestrationStateService.class",
    "com/morpheus/application/orchestration/ChangeTransitionEvaluationService.class",
    "com/morpheus/application/lifecycle/mutation/ControlledChangeLifecycleMutationService.class",
    "com/morpheus/application/composition/MultiProviderCompositionService.class",
    "com/morpheus/application/composition/CompositionQueryService.class",
    "com/morpheus/provider/markdown/StructuredMarkdownSpecificationProvider.class",
    "com/morpheus/provider/markdown/StructuredMarkdownSpecificationContentReader.class",
    "com/morpheus/store/sqlite/SqliteChangeLifecycleMutationStore.class",
    "com/morpheus/store/sqlite/SqliteCompositionStateStore.class",
    "db/migration/V011__controlled_lifecycle_mutations.sql",
    "db/migration/V012__multi_provider_composition.sql",
    "com/morpheus/integration/minos/MinosMcpExternalReferenceResolver.class",
    "com/morpheus/integration/minos/MinosMcpCodeGateway.class",
    "com/morpheus/integration/minos/MinosIntegrationRuntime.class",
    "com/morpheus/integration/nexus/NexusMcpContextGateway.class",
    "com/morpheus/integration/nexus/NexusMcpTechnicalContextProvider.class",
    "com/morpheus/integration/nexus/NexusIntegrationRuntime.class",
    "tools/jackson/databind/json/JsonMapper.class"
)
foreach ($entry in $requiredEntries) {
    if ($jarEntries -notcontains $entry) { throw "Baseline packaging proof failed; shaded JAR is missing $entry" }
}
$embeddedMinosDomain = $jarEntries | Where-Object { $_ -like "com/minos/*" }
if ($embeddedMinosDomain) { throw "Packaging proof failed; MINOS implementation classes must not be embedded: $($embeddedMinosDomain | Select-Object -First 5)" }
$embeddedNexusDomain = $jarEntries | Where-Object { $_ -like "com/nexus/*" }
if ($embeddedNexusDomain) { throw "Packaging proof failed; NEXUS implementation classes must not be embedded: $($embeddedNexusDomain | Select-Object -First 5)" }
$embeddedJarvisDomain = $jarEntries | Where-Object { $_ -like "com/jarvis/*" }
if ($embeddedJarvisDomain) { throw "Packaging proof failed; JARVIS implementation classes must not be embedded: $($embeddedJarvisDomain | Select-Object -First 5)" }
$embeddedReferenceProvider = $jarEntries | Where-Object { $_ -like "com/morpheus/provider/reference/*" }
if ($embeddedReferenceProvider) { throw "Packaging proof failed; reference provider plugin must remain external: $($embeddedReferenceProvider | Select-Object -First 5)" }
Write-Host "Baseline MCP/API/provider-SDK/MINOS/NEXUS packaging proof: PASS"

Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item $input -ItemType Directory -Force | Out-Null
New-Item $appImageRoot -ItemType Directory -Force | Out-Null
Copy-Item $jar.FullName (Join-Path $input "morpheus.jar")

Write-Host "Creating self-contained Windows app-image with embedded runtime + jdk.httpserver + java.sql + java.net.http..."
& $jpackage --type app-image --name morpheus --app-version $Version `
    --description "MORPHEUS Specification & Intent Intelligence Engine" `
    --input $input --main-jar "morpheus.jar" --main-class "com.morpheus.cli.MorpheusMain" `
    --add-modules jdk.httpserver,java.sql,java.net.http --java-options "--enable-native-access=ALL-UNNAMED" --win-console --dest $appImageRoot
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed with exit code $LASTEXITCODE" }

$launcher = Join-Path $appImageRoot "morpheus\morpheus.exe"
if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }

$integrationSource = Join-Path $repo 'integration'
$integrationTarget = Join-Path $appImageRoot 'morpheus\integration'
$integrationManager = Join-Path $integrationSource 'configure-mcp-clients.ps1'
$integrationSetup = Join-Path $integrationSource 'configure-mcp-clients-setup.ps1'
if (-not (Test-Path -LiteralPath $integrationManager) -or -not (Test-Path -LiteralPath $integrationSetup)) {
    throw "M28 MCP client integration scripts are missing under $integrationSource"
}
Copy-Item -LiteralPath $integrationSource -Destination $integrationTarget -Recurse -Force
if (-not (Test-Path -LiteralPath (Join-Path $integrationTarget 'configure-mcp-clients.ps1')) `
        -or -not (Test-Path -LiteralPath (Join-Path $integrationTarget 'configure-mcp-clients-setup.ps1')) `
        -or -not (Test-Path -LiteralPath (Join-Path $integrationTarget 'README.md'))) {
    throw 'M28 MCP client integration packaging proof failed'
}
Write-Host 'Packaged MCP client integration manager: PASS'

Write-Host "Smoke testing packaged launcher without MINOS/NEXUS/JARVIS, write-capable provider or external provider-plugin configuration..."
& $launcher --version
if ($LASTEXITCODE -ne 0) { throw "Packaged launcher --version smoke test failed with exit code $LASTEXITCODE" }
$jsonVersionText = (& $launcher --json version) -join "`n"
if ($LASTEXITCODE -ne 0) { throw "Packaged launcher JSON version smoke failed: $jsonVersionText" }
$jsonVersion = $jsonVersionText | ConvertFrom-Json
if ([string]$jsonVersion.version -ne $Version) {
    throw "Packaged launcher version is $($jsonVersion.version); expected $Version"
}
Write-Host $jsonVersionText

$productInfoText = (& $launcher --json product-info) -join "`n"
if ($LASTEXITCODE -ne 0) { throw "Packaged product-info smoke failed: $productInfoText" }
$productInfo = $productInfoText | ConvertFrom-Json
if ([string]$productInfo.version -ne $Version -or [string]$productInfo.updateChannel -ne 'stable') {
    throw "Packaged product-info mismatch: $productInfoText"
}
Write-Host $productInfoText

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
if ($LASTEXITCODE -ne 0 `
        -or $help -notmatch 'change-orchestration' `
        -or $help -notmatch 'lifecycle apply' `
        -or $help -notmatch 'composition sync' `
        -or $help -notmatch 'update-check' `
        -or $help -notmatch 'provider-plugins') {
    throw "Packaged baseline CLI help smoke failed: $help"
}
Write-Host "Packaged standalone optional-engines + provider SDK + CLI baseline smoke: PASS"

Test-PackagedApiOperability -Launcher $launcher -WorkDirectory $work

$packagedModuleImage = Join-Path $appImageRoot "morpheus\runtime\lib\modules"
if (-not (Test-Path $packagedModuleImage)) { throw "Packaged runtime module image not found: $packagedModuleImage" }
$packagedModules = & $jimageTool list $packagedModuleImage
if ($LASTEXITCODE -ne 0 `
        -or -not ($packagedModules -match '^Module: jdk\.httpserver$') `
        -or -not ($packagedModules -match '^Module: java\.sql$') `
        -or -not ($packagedModules -match '^Module: java\.net\.http$')) {
    throw "Packaged runtime must contain jdk.httpserver, java.sql and java.net.http"
}
Write-Host "Packaged jdk.httpserver + java.sql + java.net.http module proof: PASS"

New-Item $dist -ItemType Directory -Force | Out-Null
$archive = Join-Path $dist "morpheus-$Version-windows-x64.zip"
Compress-PortableArchiveWithRetry -SourceDirectory (Join-Path $appImageRoot "morpheus") -DestinationArchive $archive
if (-not (Test-Path $archive)) { throw "Portable Windows archive is missing after archive creation: $archive" }

Write-Host "Portable Windows distribution: $archive"
Write-Host "The archive contains MORPHEUS $Version, its Java runtime, provider SDK, MCP/API, optional MINOS/NEXUS client adapters, M14-M28 contracts and the opt-in MCP client integration manager. External provider plugins, MINOS, NEXUS and JARVIS are not embedded or required; lifecycle writes still require an explicit WRITE_CHANGE-capable provider."
