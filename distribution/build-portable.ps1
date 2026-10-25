param(
    [string]$Version = "1.2.1",
    [string]$OutputDirectory = "dist"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$dist = Join-Path $repo $OutputDirectory
$work = Join-Path $dist ".m20-windows"
$jpackageInput = Join-Path $work "input"
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

function Get-PackagedApiProbe {
    param([Parameter(Mandatory = $true)][int]$Port)
    $baseUri = "http://127.0.0.1:$Port/api/v1"
    $health = Invoke-WebRequest -Uri "$baseUri/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
    $readiness = Invoke-WebRequest -Uri "$baseUri/readiness" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
    $metrics = Invoke-WebRequest -Uri "$baseUri/metrics" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
    $versionResponse = Invoke-WebRequest -Uri "$baseUri/version" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
    $versionPayload = $versionResponse.Content | ConvertFrom-Json
    return [pscustomobject]@{
        HealthStatus = $health.StatusCode
        HealthBody = $health.Content
        ReadinessStatus = $readiness.StatusCode
        ReadinessBody = $readiness.Content
        MetricsStatus = $metrics.StatusCode
        MetricsBody = $metrics.Content
        VersionStatus = $versionResponse.StatusCode
        Version = [string]$versionPayload.data.version
    }
}

function Test-PackagedApiProbe {
    param([Parameter(Mandatory = $true)]$Probe)
    return $Probe.HealthStatus -eq 200 -and $Probe.HealthBody -match '"status":"UP"' `
        -and $Probe.ReadinessStatus -eq 200 -and $Probe.ReadinessBody -match '"status":"READY"' `
        -and $Probe.MetricsStatus -eq 200 -and $Probe.MetricsBody -match '"counters"' `
        -and $Probe.VersionStatus -eq 200 -and $Probe.Version -eq $Version
}

function Test-PackagedApiOperability {
    param([Parameter(Mandatory = $true)][string]$Launcher,
          [Parameter(Mandatory = $true)][string]$WorkDirectory)
    $port = Get-FreeLoopbackPort
    # Keep proof logs in the build work directory, but let MORPHEUS itself create and harden a fresh data directory
    # under the current user's temp root. Pre-creating it from PowerShell would preserve inherited ACLs and would not
    # exercise the real owner-controlled storage creation path.
    $apiData = Join-Path ([IO.Path]::GetTempPath()) ('morpheus-portable-api-' + [Guid]::NewGuid().ToString('N'))
    $stdout = Join-Path $WorkDirectory "api-smoke.stdout.log"
    $stderr = Join-Path $WorkDirectory "api-smoke.stderr.log"
    Remove-Item $stdout, $stderr -Force -ErrorAction SilentlyContinue
    $process = Start-Process -FilePath $Launcher `
        -ArgumentList "--data-dir `"$apiData`" api --host 127.0.0.1 --port $port" `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    try {
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) {
                $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { "" }
                throw "Packaged API exited before health check. stderr=$diagnostic"
            }
            try {
                $probe = Get-PackagedApiProbe -Port $port
                if (Test-PackagedApiProbe -Probe $probe) {
                    Write-Output "Packaged API health/readiness/metrics/version smoke: PASS (http://127.0.0.1:$port/api/v1)"
                    return
                }
            } catch {
                Write-Verbose "Packaged API probe attempt $attempt failed: $($_.Exception.Message)"
            }
            Start-Sleep -Milliseconds 100
        }
        $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { "" }
        throw "Packaged API operability/version smoke timed out. stderr=$diagnostic"
    } finally {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        try {
            $process.WaitForExit(5000) | Out-Null
        } catch {
            Write-Verbose "Packaged API process wait failed during cleanup: $($_.Exception.Message)"
        }
        Start-Sleep -Milliseconds 300
        Remove-Item -LiteralPath $apiData -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Building MORPHEUS $Version CLI + MCP + API + provider SDK + optional MINOS/NEXUS adapters + M14-M28 contracts uber-JAR..."
# The dependency list is what the notices are written from: the runtime classpath Maven resolved for the launcher, in
# this very build. Each module of the reactor writes the file in turn and morpheus-cli, built last, has the last word.
$runtimeDependencies = Join-Path $repo 'morpheus-cli\target\runtime-dependencies.txt'
& $mvnw -pl morpheus-cli -am -DskipTests package dependency:list `
    '-DincludeScope=runtime' '-DoutputAbsoluteArtifactFilename=true' "-DoutputFile=$runtimeDependencies"
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
    "com/morpheus/api/local/MorpheusHttpServer.class",
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
    "com/morpheus/store/sqlite/adapter/SqliteChangeLifecycleMutationStore.class",
    "com/morpheus/store/sqlite/adapter/SqliteCompositionStateStore.class",
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
New-Item $jpackageInput -ItemType Directory -Force | Out-Null
New-Item $appImageRoot -ItemType Directory -Force | Out-Null
Copy-Item $jar.FullName (Join-Path $jpackageInput "morpheus.jar")

Write-Host "Creating self-contained Windows app-image with embedded runtime + jdk.httpserver + java.sql + java.net.http..."
& $jpackage --type app-image --name morpheus --app-version $Version `
    --description "MORPHEUS Specification & Intent Intelligence Engine" `
    --input $jpackageInput --main-jar "morpheus.jar" --main-class "com.morpheus.cli.MorpheusMain" `
    --add-modules jdk.httpserver,java.sql,java.net.http --java-options "--enable-native-access=ALL-UNNAMED -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8" `
    --jlink-options "--strip-debug --no-man-pages --no-header-files" --win-console --dest $appImageRoot
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed with exit code $LASTEXITCODE" }

$launcher = Join-Path $appImageRoot "morpheus\morpheus.exe"
if (-not (Test-Path $launcher)) { throw "Packaged launcher not found: $launcher" }

# The isolated provider probe starts its child from the MORPHEUS runtime alone -- java.home/bin/java -- and never
# from PATH, so the distribution has to carry that launcher. jpackage's default jlink options include
# --strip-native-commands, which removed it: discovery worked in the packaged build and the probe could not run at
# all. The options above are those defaults minus that one, so nothing else about the runtime changes.
# The launcher is located rather than assumed: the runtime directory sits at a different depth per platform.
$runtimeJava = Get-ChildItem -LiteralPath (Join-Path $appImageRoot 'morpheus') -Recurse -File -Filter 'java.exe' |
    Select-Object -First 1
if ($null -eq $runtimeJava) {
    throw 'Embedded runtime is missing the Java launcher the isolated provider probe starts'
}
Write-Host "Embedded runtime child Java launcher: PASS ($($runtimeJava.FullName))"

# A distribution redistributes third-party components and a Java runtime; their licences ask for their texts to travel
# with them. The product licence and the notices go to the root of the image, where someone who unpacks the archive
# sees them; the runtime keeps the legal notices the JDK ships, and stripping them is refused here.
$imageHome = Join-Path $appImageRoot 'morpheus'
$runtimeHome = Split-Path -Parent (Split-Path -Parent $runtimeJava.FullName)
$python = Get-Command python -ErrorAction SilentlyContinue
if ($null -eq $python) { throw 'Python 3 is required to write THIRD-PARTY-NOTICES and was not found on PATH' }
Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination (Join-Path $imageHome 'LICENSE') -Force
& $python.Source (Join-Path $repo 'scripts\write-third-party-notices.py') `
    --dependencies $runtimeDependencies `
    --third-party (Join-Path $repo 'distribution\third-party') `
    --jdk-release (Join-Path $env:JAVA_HOME 'release') `
    --runtime-release (Join-Path $runtimeHome 'release') `
    --output (Join-Path $imageHome 'THIRD-PARTY-NOTICES')
if ($LASTEXITCODE -ne 0) { throw "Third-party notices were refused with exit code $LASTEXITCODE" }
foreach ($required in @('LICENSE', 'THIRD-PARTY-NOTICES')) {
    $requiredFile = Join-Path $imageHome $required
    if (-not (Test-Path -LiteralPath $requiredFile) -or (Get-Item -LiteralPath $requiredFile).Length -le 0) {
        throw "Packaging proof failed; $required is missing or empty at the root of the image"
    }
}
$runtimeModules = (Get-Content -LiteralPath (Join-Path $runtimeHome 'release') |
    Where-Object { $_ -match '^MODULES="(.*)"$' } | ForEach-Object { $Matches[1] }) -split ' '
if (@($runtimeModules | Where-Object { $_ }).Count -eq 0) {
    throw "Packaging proof failed; the embedded runtime's release file lists no module, so no legal notice was checked"
}
foreach ($module in $runtimeModules) {
    $legal = Join-Path $runtimeHome (Join-Path 'legal' $module)
    if (-not (Test-Path -LiteralPath $legal) -or @(Get-ChildItem -LiteralPath $legal -File).Count -eq 0) {
        throw "Packaging proof failed; the embedded runtime carries no legal notice for $module"
    }
}
Write-Host 'Product licence, third-party notices and runtime legal notices: PASS'

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
