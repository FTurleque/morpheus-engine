[CmdletBinding()]
param(
    [string]$Version = '1.0.0'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repo
$outputRoot = Join-Path $repo 'validation-output\m20'
$logRoot = Join-Path $outputRoot 'logs'
$installRoot = Join-Path $outputRoot 'installed\MORPHEUS'
# The installed launcher resolves its PROD data/config/logs from LOCALAPPDATA, and MORPHEUS hardens what it
# creates there. Pointing it inside the repository made that hardening inspect the checkout's inherited
# ACLs, so the PROD-path proof depended on the permissions of a development directory.
$validationLocalAppData = Join-Path ([IO.Path]::GetTempPath()) ('morpheus-m20-localappdata-' + [Guid]::NewGuid().ToString('N'))
$stateRoot = Join-Path $validationLocalAppData 'MORPHEUS'
$sentinel = Join-Path $stateRoot 'config\m20-upgrade-preservation.txt'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

$script:Results = [ordered]@{}
$script:ValidationSha = $null
$script:CurrentStage = $null
$script:CurrentLog = $null
$script:ValidationTag = $null
$script:TagCreated = $false
$script:FullTestSummary = $null
$script:ArchitectureTestSummary = $null
$script:PowerShellExecutable = (Get-Process -Id $PID).Path

function Write-Section([string]$Title) {
    Write-Host ''
    Write-Host ('=' * 78)
    Write-Host $Title
    Write-Host ('=' * 78)
}

function Assert-Tool([string]$Name) {
    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required tool is not available on PATH: $Name"
    }
}

function Invoke-NativeProcessToLog {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$LogPath,
        [string]$WorkingDirectory = $repo
    )
    $stderrPath = $LogPath + '.stderr'
    Remove-Item -LiteralPath $LogPath, $stderrPath -Force -ErrorAction SilentlyContinue
    try {
        $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments `
            -WorkingDirectory $WorkingDirectory -NoNewWindow -Wait -PassThru `
            -RedirectStandardOutput $LogPath -RedirectStandardError $stderrPath
        $stdoutLines = @(Get-Content -LiteralPath $LogPath -ErrorAction SilentlyContinue)
        $stderrLines = @(Get-Content -LiteralPath $stderrPath -ErrorAction SilentlyContinue)
        if ($stderrLines.Count -gt 0) { Add-Content -LiteralPath $LogPath -Value $stderrLines }
        $stdoutLines | ForEach-Object { Write-Host $_ }
        $stderrLines | ForEach-Object { Write-Host $_ }
        return [int]$process.ExitCode
    } finally {
        Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-LoggedStage {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$LogName
    )
    $script:CurrentStage = $Name
    $script:CurrentLog = Join-Path $logRoot $LogName
    Write-Section $Name
    Write-Host ('Command: ' + $FilePath + ' ' + ($Arguments -join ' '))
    Write-Host ('Log:     ' + $script:CurrentLog)
    $started = Get-Date
    $exitCode = Invoke-NativeProcessToLog -FilePath $FilePath -Arguments $Arguments -LogPath $script:CurrentLog
    if ($exitCode -ne 0) {
        $script:Results[$Name] = "FAIL ($exitCode)"
        throw "Stage '$Name' failed with exit code $exitCode"
    }
    $elapsed = (Get-Date) - $started
    $script:Results[$Name] = ('PASS ({0:n1}s)' -f $elapsed.TotalSeconds)
}

function Get-SurefireTotals([string]$Root) {
    $totals = [ordered]@{ Tests = 0; Failures = 0; Errors = 0; Skipped = 0; Suites = 0 }
    $reports = @(Get-ChildItem -Path $Root -Recurse -File -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]target[\\/]surefire-reports[\\/]' })
    foreach ($report in $reports) {
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
        $suite = $document.testsuite
        if ($null -eq $suite) { continue }
        $totals.Tests += [int]$suite.tests
        $totals.Failures += [int]$suite.failures
        $totals.Errors += [int]$suite.errors
        $totals.Skipped += [int]$suite.skipped
        $totals.Suites++
    }
    return [pscustomobject]$totals
}

function Assert-Sha256([string]$AssetPath) {
    if (-not (Test-Path -LiteralPath $AssetPath)) { throw "Missing release asset: $AssetPath" }
    $checksumPath = $AssetPath + '.sha256'
    if (-not (Test-Path -LiteralPath $checksumPath)) { throw "Missing checksum: $checksumPath" }
    $recorded = ((Get-Content -LiteralPath $checksumPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $AssetPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($recorded -ne $actual) { throw "SHA-256 mismatch for $AssetPath recorded=$recorded actual=$actual" }
    Write-Host "SHA-256 PASS: $(Split-Path -Leaf $AssetPath) $actual"
}

function Assert-InstallerContract {
    $iss = Join-Path $repo 'distribution\windows\MORPHEUS.iss'
    $text = Get-Content -LiteralPath $iss -Raw
    $required = @(
        'DefaultDirName={localappdata}\Programs\MORPHEUS',
        'PrivilegesRequired=lowest',
        'Name: "addtopath"',
        'AppId={{4D0DC052-2FD6-49F5-88F4-E32C9B1EB67A}'
    )
    foreach ($token in $required) {
        if ($text -notlike "*$token*") { throw "Installer contract missing: $token" }
    }
    if ($text -match '(?i)UninstallDelete.*MORPHEUS') {
        throw 'Installer must not delete persistent MORPHEUS state during uninstall'
    }
    $script:Results['Installer contract'] = 'PASS'
}

function Invoke-Setup([string]$SetupPath, [string]$Target, [bool]$AddToPath) {
    $taskArgument = if ($AddToPath) { '/TASKS="addtopath"' } else { '/TASKS=""' }
    $arguments = @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', "/DIR=`"$Target`"", $taskArgument)
    $process = Start-Process -FilePath $SetupPath -ArgumentList $arguments -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "Setup failed with exit code $($process.ExitCode)" }
}

function Invoke-Uninstall([string]$Target) {
    $uninstaller = Get-ChildItem -LiteralPath $Target -Filter 'unins*.exe' -File | Select-Object -First 1
    if ($null -eq $uninstaller) { throw "Uninstaller not found under $Target" }
    $process = Start-Process -FilePath $uninstaller.FullName `
        -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART') -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "Uninstall failed with exit code $($process.ExitCode)" }
    Start-Sleep -Milliseconds 750
}

function Normalize-PathEntry([string]$Value) {
    return $Value.Trim().TrimEnd('\')
}

function UserPathContains([string]$Entry) {
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    if ($null -eq $userPath) { return $false }
    $target = Normalize-PathEntry $Entry
    return @($userPath -split ';' | ForEach-Object { Normalize-PathEntry $_ }) -contains $target
}

function Invoke-InstalledNoJdkSmoke([string]$Launcher) {
    $savedJavaHome = $env:JAVA_HOME
    $savedPath = $env:Path
    $savedLocalAppData = $env:LOCALAPPDATA
    try {
        $env:JAVA_HOME = ''
        $env:Path = "$env:SystemRoot\System32;$env:SystemRoot"
        $env:LOCALAPPDATA = $validationLocalAppData

        $versionText = (& $Launcher --json version 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Installed no-JDK version smoke failed: $versionText" }
        $versionView = $versionText | ConvertFrom-Json
        if ([string]$versionView.version -ne $Version) {
            throw "Installed version is $($versionView.version); expected $Version"
        }

        $pathsText = (& $Launcher --json paths 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Installed paths smoke failed: $pathsText" }
        $paths = $pathsText | ConvertFrom-Json
        $expectedData = (Join-Path $stateRoot 'data')
        $expectedConfig = (Join-Path $stateRoot 'config')
        $expectedLogs = (Join-Path $stateRoot 'logs')
        if ([IO.Path]::GetFullPath([string]$paths.dataDirectory) -ne [IO.Path]::GetFullPath($expectedData)) {
            throw "Unexpected PROD data path: $($paths.dataDirectory) expected=$expectedData"
        }
        if ([IO.Path]::GetFullPath([string]$paths.configDirectory) -ne [IO.Path]::GetFullPath($expectedConfig)) {
            throw "Unexpected PROD config path: $($paths.configDirectory) expected=$expectedConfig"
        }
        if ([IO.Path]::GetFullPath([string]$paths.logsDirectory) -ne [IO.Path]::GetFullPath($expectedLogs)) {
            throw "Unexpected PROD logs path: $($paths.logsDirectory) expected=$expectedLogs"
        }

        $minosText = (& $Launcher --json minos-status 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Installed MINOS status failed: $minosText" }
        $minos = $minosText | ConvertFrom-Json
        if ([string]$minos.state -ne 'DISABLED') { throw "MINOS must be opt-in: $minosText" }

        $nexusText = (& $Launcher --json nexus-status 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Installed NEXUS status failed: $nexusText" }
        $nexus = $nexusText | ConvertFrom-Json
        if ([string]$nexus.state -ne 'DISABLED') { throw "NEXUS must be opt-in: $nexusText" }

        $projects = (& $Launcher --json projects list 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Installed SQLite creation smoke failed: $projects" }
    } finally {
        $env:JAVA_HOME = $savedJavaHome
        $env:Path = $savedPath
        $env:LOCALAPPDATA = $savedLocalAppData
    }
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port } finally { $listener.Stop() }
}

function Test-InstalledApi([string]$Launcher) {
    $port = Get-FreeLoopbackPort
    # MORPHEUS creates and hardens its own data directory, so the gate must not pre-create it: a directory made
    # here inherits the ACLs of whatever it sits under, and the real owner-controlled storage path is never
    # exercised. Under the repository that inheritance is what the hardener refuses, which made a packaged
    # product gate depend on the permissions of a development checkout.
    $apiData = Join-Path ([IO.Path]::GetTempPath()) ('morpheus-m20-api-' + [Guid]::NewGuid().ToString('N'))
    $stdout = Join-Path $logRoot 'installed-api.stdout.log'
    $stderr = Join-Path $logRoot 'installed-api.stderr.log'
    $process = Start-Process -FilePath $Launcher `
        -ArgumentList @('--data-dir', $apiData, 'api', '--host', '127.0.0.1', '--port', "$port") `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    try {
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) {
                $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { '' }
                throw "Installed API exited before becoming ready: $diagnostic"
            }
            try {
                $health = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/v1/health" -UseBasicParsing -TimeoutSec 2
                $ready = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/v1/readiness" -UseBasicParsing -TimeoutSec 2
                $metrics = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/v1/metrics" -UseBasicParsing -TimeoutSec 2
                if ($health.StatusCode -eq 200 -and $ready.StatusCode -eq 200 -and $metrics.StatusCode -eq 200) { return }
            } catch { Start-Sleep -Milliseconds 100 }
        }
        throw 'Installed API health/readiness/metrics timed out'
    } finally {
        # Best effort, and deliberately not allowed to replace whatever failure is already unwinding.
        Remove-Item -LiteralPath $apiData -Recurse -Force -ErrorAction SilentlyContinue
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        try { $process.WaitForExit(5000) | Out-Null } catch { }
    }
}

function Write-FailureSummary([System.Exception]$Failure) {
    $path = Join-Path $outputRoot 'failure-summary.txt'
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('M20 VALIDATION FAILURE')
    $lines.Add(('Timestamp: ' + (Get-Date).ToString('o')))
    if ($script:ValidationSha) { $lines.Add(('SHA:       ' + $script:ValidationSha)) }
    if ($script:CurrentStage) { $lines.Add(('Stage:     ' + $script:CurrentStage)) }
    $lines.Add(('Error:     ' + $Failure.Message))
    if ($script:CurrentLog -and (Test-Path -LiteralPath $script:CurrentLog)) {
        $lines.Add('Relevant log tail:')
        foreach ($line in (Get-Content -LiteralPath $script:CurrentLog -Tail 100)) { $lines.Add($line) }
    }
    $lines | Set-Content -LiteralPath $path -Encoding utf8
    $lines | ForEach-Object { Write-Host $_ }
    Write-Host "Failure summary: $path"
}

function Write-Summary([bool]$Success) {
    $path = Join-Path $outputRoot 'validation-summary.txt'
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('M20 VALIDATION SUMMARY')
    $lines.Add(('Timestamp: ' + (Get-Date).ToString('o')))
    $lines.Add(('SHA:       ' + $script:ValidationSha))
    $lines.Add(('Version:   ' + $Version))
    $lines.Add(('Result:    ' + $(if ($Success) { 'PASS' } else { 'FAIL' })))
    $lines.Add('Linux proof: NOT EXECUTED BY THIS WINDOWS VALIDATOR')
    if ($script:FullTestSummary) {
        $lines.Add(('Full reactor tests: {0}; failures: {1}; errors: {2}; skipped: {3}; suites: {4}' -f
            $script:FullTestSummary.Tests, $script:FullTestSummary.Failures, $script:FullTestSummary.Errors,
            $script:FullTestSummary.Skipped, $script:FullTestSummary.Suites))
    }
    if ($script:ArchitectureTestSummary) {
        $lines.Add(('Architecture tests: {0}; failures: {1}; errors: {2}; skipped: {3}' -f
            $script:ArchitectureTestSummary.Tests, $script:ArchitectureTestSummary.Failures,
            $script:ArchitectureTestSummary.Errors, $script:ArchitectureTestSummary.Skipped))
    }
    $lines.Add('')
    foreach ($entry in $script:Results.GetEnumerator()) {
        $lines.Add(('{0,-38} {1}' -f $entry.Key, $entry.Value))
    }
    $lines | Set-Content -LiteralPath $path -Encoding utf8
    Write-Section 'M20 summary'
    $lines | ForEach-Object { Write-Host $_ }
    Write-Host "Summary file: $path"
}

try {
    Write-Section 'Workspace / SHA / version'
    Assert-Tool 'git'
    $script:ValidationSha = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve Git HEAD' }
    $status = @(git status --porcelain)
    if ($status.Count -gt 0) { throw 'Exact-head validation requires a clean Git workspace' }
    $pom = [xml](Get-Content -LiteralPath (Join-Path $repo 'pom.xml') -Raw)
    $projectVersion = [string]$pom.project.version
    if ($projectVersion -ne $Version) { throw "pom.xml version is $projectVersion; expected $Version" }
    Write-Host "SHA:     $script:ValidationSha"
    Write-Host "Version: $projectVersion"
    $script:Results['Workspace / SHA / version'] = 'PASS'

    Assert-Tool 'java'
    $mvnw = Join-Path $repo 'mvnw.cmd'
    # verify, not test: the architecture suite this reactor runs reads the JaCoCo reports and the packaged
    # morpheus-provider-reference JAR, and neither exists after `clean test`. The coverage gate and the
    # provider-plugin contract failed here for that reason alone. Every other validator uses verify.
    Invoke-LoggedStage -Name 'Full Maven reactor' -FilePath $mvnw -Arguments @('clean', 'verify') -LogName '01-full-reactor.log'
    $script:FullTestSummary = Get-SurefireTotals $repo
    $script:ArchitectureTestSummary = Get-SurefireTotals (Join-Path $repo 'morpheus-architecture-tests')
    if ($script:FullTestSummary.Failures -ne 0 -or $script:FullTestSummary.Errors -ne 0 -or $script:FullTestSummary.Skipped -ne 0) {
        throw 'Full Maven reactor contains failures/errors/skipped tests'
    }

    $script:CurrentStage = 'Installer contract'
    $script:CurrentLog = $null
    Assert-InstallerContract

    $script:CurrentStage = 'Validation tag preparation'
    $script:CurrentLog = $null
    $shortSha = $script:ValidationSha.Substring(0, 12)
    $script:ValidationTag = "m20-validation-$shortSha"
    $existingValidationTags = @(git tag --list $script:ValidationTag)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect local validation tags' }
    if ($existingValidationTags.Count -gt 0) {
        git tag -d $script:ValidationTag | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Unable to delete stale local validation tag $($script:ValidationTag)" }
    }
    git tag $script:ValidationTag $script:ValidationSha
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create local validation tag' }
    $script:TagCreated = $true
    $script:Results['Validation tag preparation'] = 'PASS'

    $releaseScript = Join-Path $repo 'distribution\build-release.ps1'
    Invoke-LoggedStage -Name 'Tagged Windows release build' -FilePath $script:PowerShellExecutable `
        -Arguments @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $releaseScript, '-Version', $Version, '-ExpectedTag', $script:ValidationTag) `
        -LogName '02-release-build.log'

    $zip = Join-Path $repo "dist\morpheus-$Version-windows-x64.zip"
    $setup = Join-Path $repo "dist\MORPHEUS-$Version-windows-x64-setup.exe"
    Assert-Sha256 $zip
    Assert-Sha256 $setup
    $manifestPath = Join-Path $repo "dist\morpheus-$Version-windows-x64-release-manifest.json"
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.version -ne $Version -or $manifest.gitSha -ne $script:ValidationSha -or $manifest.tag -ne $script:ValidationTag) {
        throw 'Release manifest version/tag/SHA does not match validation head'
    }
    if ($manifest.userJdkRequired -ne $false -or $manifest.uninstallPreservesPersistentState -ne $true) {
        throw 'Release manifest runtime/data invariants are invalid'
    }
    $script:Results['SHA-256 + release manifest'] = 'PASS'

    Remove-Item -LiteralPath $installRoot -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $validationLocalAppData -Recurse -Force -ErrorAction SilentlyContinue

    Write-Section 'Windows install + PATH + no-JDK + API'
    Invoke-Setup -SetupPath $setup -Target $installRoot -AddToPath $true
    $launcher = Join-Path $installRoot 'morpheus.exe'
    if (-not (Test-Path -LiteralPath $launcher)) { throw "Installed launcher missing: $launcher" }
    if (-not (UserPathContains $installRoot)) { throw 'PATH option did not add the install directory to the user PATH' }
    Invoke-InstalledNoJdkSmoke -Launcher $launcher
    Test-InstalledApi -Launcher $launcher
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $sentinel) | Out-Null
    'preserve-across-upgrade-and-uninstall' | Set-Content -LiteralPath $sentinel -Encoding utf8
    $database = Join-Path $stateRoot 'data\morpheus.db'
    if (-not (Test-Path -LiteralPath $database)) { throw "Installed runtime did not create persistent database: $database" }
    $script:Results['Install + PATH + no-JDK + API'] = 'PASS'

    Write-Section 'Upgrade preservation'
    Invoke-Setup -SetupPath $setup -Target $installRoot -AddToPath $true
    if (-not (Test-Path -LiteralPath $sentinel) -or -not (Test-Path -LiteralPath $database)) {
        throw 'Upgrade replaced or deleted persistent state'
    }
    Invoke-InstalledNoJdkSmoke -Launcher $launcher
    $script:Results['Upgrade preserves data/config'] = 'PASS'

    Write-Section 'Uninstall preservation'
    Invoke-Uninstall -Target $installRoot
    if (-not (Test-Path -LiteralPath $sentinel) -or -not (Test-Path -LiteralPath $database)) {
        throw 'Uninstall deleted persistent MORPHEUS state'
    }
    if (UserPathContains $installRoot) { throw 'Uninstall left the MORPHEUS install directory in the user PATH' }
    $script:Results['Uninstall preserves persistent state'] = 'PASS'

    Write-Section 'Reinstall over preserved state'
    Invoke-Setup -SetupPath $setup -Target $installRoot -AddToPath $false
    $launcher = Join-Path $installRoot 'morpheus.exe'
    Invoke-InstalledNoJdkSmoke -Launcher $launcher
    if (-not (Test-Path -LiteralPath $sentinel) -or -not (Test-Path -LiteralPath $database)) {
        throw 'Reinstall did not preserve existing data/config'
    }
    $script:Results['Reinstall reuses persistent state'] = 'PASS'
    Invoke-Uninstall -Target $installRoot

    Write-Section 'Exact-head stability'
    $endingSha = (git rev-parse HEAD).Trim()
    $endingStatus = @(git status --porcelain)
    if ($endingSha -ne $script:ValidationSha) { throw "HEAD changed during validation: $script:ValidationSha -> $endingSha" }
    if ($endingStatus.Count -gt 0) { throw 'Tracked or untracked workspace state changed during validation' }
    $script:Results['Exact-head stability'] = 'PASS'

    Write-Summary $true
    exit 0
} catch {
    if ($script:CurrentStage -and -not $script:Results.Contains($script:CurrentStage)) {
        $script:Results[$script:CurrentStage] = 'FAIL'
    }
    Write-FailureSummary $_.Exception
    Write-Summary $false
    exit 1
} finally {
    if ($script:TagCreated -and $script:ValidationTag) {
        git tag -d $script:ValidationTag 2>$null | Out-Null
    }
}
