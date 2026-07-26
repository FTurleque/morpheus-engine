[CmdletBinding()]
param(
    [switch]$SkipPackaging,
    [switch]$SkipBenchmarks
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repoRoot

$outputRoot = Join-Path $repoRoot 'validation-output\m19'
$logRoot = Join-Path $outputRoot 'logs'
$extractRoot = Join-Path $outputRoot 'portable-smoke'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

$script:Results = [ordered]@{}
$script:CurrentStage = $null
$script:CurrentLog = $null
$script:ValidationSha = $null
$script:StartedAt = Get-Date

function Write-Section([string]$Title) {
    Write-Host ''
    Write-Host ('=' * 78)
    Write-Host $Title
    Write-Host ('=' * 78)
}

function Get-CommandLine([string]$FilePath, [string[]]$Arguments) {
    return (($FilePath) + ' ' + (($Arguments | ForEach-Object {
        if ($_ -match '\s') { '"' + $_.Replace('"', '\"') + '"' } else { $_ }
    }) -join ' ')).Trim()
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
    Write-Host ('Command: ' + (Get-CommandLine $FilePath $Arguments))
    Write-Host ('Log:     ' + $script:CurrentLog)

    $started = Get-Date
    & $FilePath @Arguments 2>&1 | Tee-Object -FilePath $script:CurrentLog
    $exitCode = $LASTEXITCODE
    $elapsed = (Get-Date) - $started
    if ($exitCode -ne 0) {
        $script:Results[$Name] = "FAIL ($exitCode)"
        throw "Stage '$Name' failed with exit code $exitCode"
    }
    $script:Results[$Name] = ('PASS ({0:n1}s)' -f $elapsed.TotalSeconds)
}

function Assert-Tool([string]$Name) {
    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required tool is not available on PATH: $Name"
    }
}

function Get-P95Milliseconds([System.Collections.Generic.List[double]]$Samples) {
    if ($Samples.Count -eq 0) { throw 'Cannot compute p95 from an empty sample set' }
    $ordered = @($Samples | Sort-Object)
    $rank = [Math]::Ceiling($ordered.Count * 0.95)
    return [double]$ordered[[Math]::Max(0, $rank - 1)]
}

function Invoke-PortableStartupGate {
    Write-Section 'Packaged startup benchmark'
    $script:CurrentStage = 'Packaged startup benchmark'
    $script:CurrentLog = Join-Path $logRoot '06-packaged-startup.log'

    $archive = Get-ChildItem -Path (Join-Path $repoRoot 'dist') -Filter 'morpheus-*-windows-x64.zip' -File |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $archive) {
        throw 'No Windows portable archive was produced in dist/'
    }

    if (Test-Path $extractRoot) { Remove-Item -Recurse -Force $extractRoot }
    New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
    Expand-Archive -Path $archive.FullName -DestinationPath $extractRoot -Force
    $launcher = Get-ChildItem -Path $extractRoot -Filter 'morpheus.exe' -File -Recurse | Select-Object -First 1
    if ($null -eq $launcher) {
        throw 'morpheus.exe was not found in the extracted portable archive'
    }

    $warmupOutput = & $launcher.FullName --json version 2>&1
    if ($LASTEXITCODE -ne 0) {
        $warmupOutput | Set-Content -Encoding UTF8 $script:CurrentLog
        throw 'Packaged launcher warmup failed'
    }

    $samples = [System.Collections.Generic.List[double]]::new()
    $allOutput = [System.Collections.Generic.List[string]]::new()
    for ($index = 1; $index -le 5; $index++) {
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        $output = & $launcher.FullName --json version 2>&1
        $exitCode = $LASTEXITCODE
        $watch.Stop()
        foreach ($line in $output) { $allOutput.Add([string]$line) }
        if ($exitCode -ne 0) {
            $allOutput | Set-Content -Encoding UTF8 $script:CurrentLog
            throw "Packaged launcher iteration $index failed with exit code $exitCode"
        }
        $samples.Add($watch.Elapsed.TotalMilliseconds)
        Write-Host ('M19_METRIC packaged_startup_run_{0}_ms={1:n1}' -f $index, $watch.Elapsed.TotalMilliseconds)
    }
    $allOutput | Set-Content -Encoding UTF8 $script:CurrentLog
    $p95 = Get-P95Milliseconds $samples
    Write-Host ('M19_METRIC packaged_startup_p95_ms={0:n1}' -f $p95)
    Write-Host ('M19_METRIC windows_archive_bytes={0}' -f $archive.Length)
    if ($p95 -gt 5000.0) {
        $script:Results[$script:CurrentStage] = 'FAIL (startup budget)'
        throw ('Packaged startup p95 {0:n1} ms exceeds frozen 5000 ms budget' -f $p95)
    }
    $script:Results[$script:CurrentStage] = ('PASS (p95 {0:n1}ms)' -f $p95)
}

function Write-FailureSummary([System.Exception]$Failure) {
    $summaryPath = Join-Path $outputRoot 'failure-summary.txt'
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('M19 VALIDATION FAILURE')
    $lines.Add(('Timestamp: ' + (Get-Date).ToString('o')))
    if ($script:ValidationSha) { $lines.Add(('SHA:       ' + $script:ValidationSha)) }
    if ($script:CurrentStage) { $lines.Add(('Stage:     ' + $script:CurrentStage)) }
    $lines.Add(('Error:     ' + $Failure.Message))
    if ($script:CurrentLog -and (Test-Path $script:CurrentLog)) {
        $lines.Add(('Log:       ' + $script:CurrentLog))
        $lines.Add('')
        $lines.Add('Relevant log lines:')
        $matches = Select-String -Path $script:CurrentLog -Pattern 'COMPILATION ERROR|\[ERROR\]|FAILURE|Failures:|Errors:|Tests run:|M19_METRIC' -CaseSensitive:$false |
            Select-Object -Last 80
        if ($matches) {
            foreach ($match in $matches) { $lines.Add($match.Line) }
        } else {
            foreach ($line in (Get-Content $script:CurrentLog -Tail 80)) { $lines.Add($line) }
        }
    }
    New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
    $lines | Set-Content -Encoding UTF8 $summaryPath
    Write-Host ''
    Write-Host ('Failure summary: ' + $summaryPath) -ForegroundColor Red
    $lines | ForEach-Object { Write-Host $_ }
}

function Write-FinalSummary([bool]$Success) {
    $summaryPath = Join-Path $outputRoot 'validation-summary.txt'
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('M19 VALIDATION SUMMARY')
    $lines.Add(('Timestamp: ' + (Get-Date).ToString('o')))
    if ($script:ValidationSha) { $lines.Add(('SHA:       ' + $script:ValidationSha)) }
    $lines.Add(('Result:    ' + $(if ($Success) { 'PASS' } else { 'FAIL' })))
    $lines.Add('Linux proof: NOT EXECUTED BY THIS WINDOWS VALIDATOR')
    $lines.Add('')
    foreach ($entry in $script:Results.GetEnumerator()) {
        $lines.Add(('{0,-34} {1}' -f $entry.Key, $entry.Value))
    }
    New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
    $lines | Set-Content -Encoding UTF8 $summaryPath
    Write-Section 'M19 summary'
    $lines | ForEach-Object { Write-Host $_ }
    Write-Host ('Summary file: ' + $summaryPath)
}

try {
    Write-Section 'Workspace / SHA'
    Assert-Tool 'git'
    if (-not (Test-Path (Join-Path $repoRoot '.git'))) {
        throw "Repository root does not contain .git: $repoRoot"
    }
    $script:ValidationSha = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve git HEAD' }
    $branch = (git branch --show-current).Trim()
    $status = @(git status --porcelain)
    Write-Host ('Workspace: ' + $repoRoot)
    Write-Host ('Branch:    ' + $branch)
    Write-Host ('SHA:       ' + $script:ValidationSha)
    Write-Host ('Dirty:     ' + ($status.Count -gt 0))
    $script:Results['Workspace / SHA'] = 'PASS'

    Write-Section 'Toolchain'
    Assert-Tool 'java'
    & java -version 2>&1 | Tee-Object -FilePath (Join-Path $logRoot '01-java-version.log')
    if ($LASTEXITCODE -ne 0) { throw 'java -version failed' }
    & (Join-Path $repoRoot 'mvnw.cmd') --version 2>&1 | Tee-Object -FilePath (Join-Path $logRoot '01-maven-version.log')
    if ($LASTEXITCODE -ne 0) { throw 'Maven Wrapper --version failed' }
    $script:Results['Toolchain'] = 'PASS'

    Invoke-LoggedStage -Name 'Full Maven reactor' -FilePath (Join-Path $repoRoot 'mvnw.cmd') `
        -Arguments @('clean', 'test') -LogName '02-full-reactor.log'

    Invoke-LoggedStage -Name 'M19 robustness contracts' -FilePath (Join-Path $repoRoot 'mvnw.cmd') `
        -Arguments @(
            '-Dsurefire.failIfNoSpecifiedTests=false',
            '-Dtest=LocalSourceInventorySecurityTest,OperationalObservabilityContractTest,LocalWritePermissionHardenerTest,SqliteConcurrencyHardeningTest,SnapshotRecoveryContractTest',
            'test'
        ) -LogName '03-robustness.log'

    if (-not $SkipBenchmarks) {
        Invoke-LoggedStage -Name 'M19 performance gates' -FilePath (Join-Path $repoRoot 'mvnw.cmd') `
            -Arguments @(
                '-pl', 'morpheus-architecture-tests', '-am',
                '-Dsurefire.failIfNoSpecifiedTests=false',
                '-Dtest=M19PerformanceGate,M19QueryPerformanceGate,M19TraceabilityPerformanceGate,M19CompositionPerformanceGate,M19FullPublishPerformanceGate',
                '-DargLine=-Xmx768m',
                'test'
            ) -LogName '04-performance-gates.log'
    } else {
        $script:Results['M19 performance gates'] = 'SKIPPED BY PARAMETER'
    }

    if (-not $SkipPackaging) {
        Invoke-LoggedStage -Name 'Windows portable packaging + smokes' -FilePath 'powershell.exe' `
            -Arguments @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $repoRoot 'distribution\build-portable.ps1')) `
            -LogName '05-packaging.log'
        Invoke-PortableStartupGate
    } else {
        $script:Results['Windows portable packaging + smokes'] = 'SKIPPED BY PARAMETER'
        $script:Results['Packaged startup benchmark'] = 'SKIPPED BY PARAMETER'
    }

    Write-FinalSummary $true
    exit 0
} catch {
    if ($script:CurrentStage -and -not $script:Results.Contains($script:CurrentStage)) {
        $script:Results[$script:CurrentStage] = 'FAIL'
    }
    Write-FailureSummary $_.Exception
    Write-FinalSummary $false
    exit 1
}
