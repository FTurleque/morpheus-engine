param(
    [switch]$SkipUpdate,
    [switch]$SkipPackaging
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$Branch = 'm17/controlled-lifecycle-write-operations'
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

# Validation logs live outside target/: `mvn clean` deletes target while Tee-Object keeps logs open.
$LogRoot = Join-Path $RepoRoot '.git\morpheus-validation\m17'
$MavenLog = Join-Path $LogRoot 'maven-clean-test.log'
$PackagingLog = Join-Path $LogRoot 'windows-packaging.log'
New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null

function Write-Stage([string]$Title) {
    Write-Host ''
    Write-Host ('=' * 78) -ForegroundColor Cyan
    Write-Host ("M17 :: {0}" -f $Title) -ForegroundColor Cyan
    Write-Host ('=' * 78) -ForegroundColor Cyan
}

function Resolve-PowerShellHost {
    $candidates = @()
    if ($PSHOME) {
        $candidates += (Join-Path $PSHOME 'powershell.exe')
        $candidates += (Join-Path $PSHOME 'pwsh.exe')
    }
    if ($env:SystemRoot) {
        $candidates += (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe')
    }
    foreach ($candidate in $candidates | Select-Object -Unique) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
    }
    foreach ($name in @('pwsh.exe', 'powershell.exe')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            return $command.Source
        }
    }
    throw 'No PowerShell host could be resolved for the packaging step.'
}

function Invoke-NativeLogged {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$LogFile
    )

    Write-Stage $Label
    $previousErrorActionPreference = $ErrorActionPreference
    $exitCode = -1
    try {
        # Native stderr warnings (notably Java/SQLite) are log content, not PowerShell failures.
        $ErrorActionPreference = 'Continue'
        & $Command @Arguments 2>&1 |
            ForEach-Object {
                if ($_ -is [System.Management.Automation.ErrorRecord]) {
                    $_.ToString()
                } else {
                    $_
                }
            } |
            Tee-Object -FilePath $LogFile
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode. Log: $LogFile"
    }
}

try {
    Write-Stage 'Workspace'

    $dirty = @(git status --porcelain)
    if ($LASTEXITCODE -ne 0) {
        throw 'git status failed.'
    }
    if ($dirty.Count -gt 0) {
        Write-Host 'Working tree contains local changes:' -ForegroundColor Yellow
        $dirty | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
        throw 'Refusing to update/test a dirty working tree. Commit or stash local changes first.'
    }

    if (-not $SkipUpdate) {
        Write-Host "Updating $Branch from origin..."
        git fetch origin
        if ($LASTEXITCODE -ne 0) { throw 'git fetch origin failed.' }

        $currentBranch = (git branch --show-current).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Cannot determine current Git branch.' }
        if ($currentBranch -ne $Branch) {
            git switch $Branch
            if ($LASTEXITCODE -ne 0) { throw "Cannot switch to $Branch." }
        }

        git pull --ff-only origin $Branch
        if ($LASTEXITCODE -ne 0) { throw "Cannot fast-forward $Branch from origin." }
    }

    $testedSha = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve tested SHA.' }
    Write-Host "Branch : $Branch"
    Write-Host "SHA    : $testedSha"
    Write-Host "Logs   : $LogRoot"

    Write-Stage 'Toolchain'
    java -version
    if ($LASTEXITCODE -ne 0) { throw 'java -version failed.' }
    .\mvnw.cmd --version
    if ($LASTEXITCODE -ne 0) { throw 'Maven Wrapper validation failed.' }

    Invoke-NativeLogged `
        -Label 'Full Maven reactor - clean test' `
        -Command '.\mvnw.cmd' `
        -Arguments @('clean', 'test') `
        -LogFile $MavenLog

    if (-not $SkipPackaging) {
        $powerShellHost = Resolve-PowerShellHost
        Write-Host "Packaging PowerShell host: $powerShellHost"
        Invoke-NativeLogged `
            -Label 'Windows portable packaging + smokes' `
            -Command $powerShellHost `
            -Arguments @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', '.\distribution\build-portable.ps1') `
            -LogFile $PackagingLog
    }

    Write-Stage 'RESULT'
    Write-Host 'M17 VALIDATION PASSED' -ForegroundColor Green
    Write-Host "Tested SHA : $testedSha" -ForegroundColor Green
    Write-Host "Maven log  : $MavenLog" -ForegroundColor Green
    if (-not $SkipPackaging) {
        Write-Host "Package log: $PackagingLog" -ForegroundColor Green
    } else {
        Write-Host 'Packaging   : SKIPPED by request' -ForegroundColor Yellow
    }
    exit 0
}
catch {
    Write-Stage 'RESULT'
    Write-Host 'M17 VALIDATION FAILED' -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ''
    Write-Host 'Fix the reported failure, then rerun the SAME command.' -ForegroundColor Yellow
    exit 1
}
