[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InstallRoot,

    [Parameter(Mandatory = $true)]
    [string] $PayloadZip,

    [string] $ExpectedPayloadSha256 = '',

    [string] $Version = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not (Test-Path -LiteralPath $PayloadZip -PathType Leaf)) {
    throw "MORPHEUS installation payload not found: $PayloadZip"
}

$InstallRoot = [System.IO.Path]::GetFullPath($InstallRoot)
$OwnershipMarker = Join-Path $InstallRoot '.morpheus-install.json'
$StagingDir = Join-Path $InstallRoot '.install-staging'
$RollbackDir = Join-Path $InstallRoot '.install-rollback'
$JournalPath = Join-Path $InstallRoot '.install-journal.json'
$ReservedNames = @('.morpheus-install.json', '.install-staging', '.install-rollback', '.install-journal.json')

function Test-ReparsePointOnPath([string] $Path) {
    $Current = $Path
    while (-not [string]::IsNullOrWhiteSpace($Current)) {
        if (Test-Path -LiteralPath $Current) {
            $Item = Get-Item -LiteralPath $Current -Force
            if ($Item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
                return $true
            }
        }
        $Parent = Split-Path -Parent $Current
        if ($Parent -eq $Current) { break }
        $Current = $Parent
    }
    return $false
}

function Get-FileSha256([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

# A transactional upgrade must never silently take over a directory it did not create: an unrelated non-empty
# folder the user pointed the installer at is refused, not adopted. The ownership marker is the only proof
# that a non-empty {app} belongs to MORPHEUS.
function Assert-SafeInstallRoot {
    if (Test-ReparsePointOnPath -Path $InstallRoot) {
        throw "MORPHEUS installation root contains a reparse point/symlink; refusing to install: $InstallRoot"
    }
    if (-not (Test-Path -LiteralPath $InstallRoot -PathType Container)) {
        return
    }
    $Entries = @(Get-ChildItem -LiteralPath $InstallRoot -Force -ErrorAction SilentlyContinue)
    if ($Entries.Count -eq 0) {
        return
    }
    if (-not (Test-Path -LiteralPath $OwnershipMarker -PathType Leaf)) {
        throw "MORPHEUS installation root is not empty and was not created by MORPHEUS; refusing to install: $InstallRoot"
    }
}

function Write-JournalPhase([string] $Phase) {
    $Journal = [ordered]@{
        phase = $Phase
        updatedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
        installRoot = $InstallRoot
        payloadZip = $PayloadZip
    }
    ($Journal | ConvertTo-Json -Depth 8) | Set-Content -LiteralPath $JournalPath -Encoding UTF8
}

function Remove-JournalIfPresent {
    Remove-Item -LiteralPath $JournalPath -Force -ErrorAction SilentlyContinue
}

function Get-LiveEntries {
    return @(Get-ChildItem -LiteralPath $InstallRoot -Force -ErrorAction SilentlyContinue |
        Where-Object { $ReservedNames -notcontains $_.Name })
}

# If a previous run crashed mid-activation, its journal is still 'activating': the safest recovery is always
# to roll back that interrupted transaction rather than guess whether it finished, then proceed with the
# fresh request.
function Resume-InterruptedTransaction {
    if (-not (Test-Path -LiteralPath $JournalPath -PathType Leaf)) {
        return
    }
    Write-Warning 'Found an interrupted MORPHEUS installation transaction; rolling it back before continuing.'
    foreach ($Entry in (Get-LiveEntries)) {
        Remove-Item -LiteralPath $Entry.FullName -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $RollbackDir -PathType Container) {
        Get-ChildItem -LiteralPath $RollbackDir -Force | ForEach-Object {
            Move-Item -LiteralPath $_.FullName -Destination (Join-Path $InstallRoot $_.Name) -Force
        }
        Remove-Item -LiteralPath $RollbackDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $StagingDir -Recurse -Force -ErrorAction SilentlyContinue
    Remove-JournalIfPresent
}

function Expand-Payload {
    if (Test-Path -LiteralPath $StagingDir) {
        Remove-Item -LiteralPath $StagingDir -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $StagingDir | Out-Null
    Expand-Archive -LiteralPath $PayloadZip -DestinationPath $StagingDir -Force
}

function Test-StagedPayload {
    $Exe = Join-Path $StagingDir 'morpheus.exe'
    if (-not (Test-Path -LiteralPath $Exe -PathType Leaf)) {
        throw "Staged MORPHEUS payload is missing morpheus.exe: $StagingDir"
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedPayloadSha256)) {
        $Actual = Get-FileSha256 -Path $PayloadZip
        if (-not $Actual.Equals($ExpectedPayloadSha256, [StringComparison]::OrdinalIgnoreCase)) {
            throw "MORPHEUS installation payload checksum mismatch: expected=$ExpectedPayloadSha256 actual=$Actual"
        }
    }
}

function Move-LiveEntriesToRollback {
    if (Test-Path -LiteralPath $RollbackDir) {
        Remove-Item -LiteralPath $RollbackDir -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $RollbackDir | Out-Null
    foreach ($Entry in (Get-LiveEntries)) {
        Move-Item -LiteralPath $Entry.FullName -Destination (Join-Path $RollbackDir $Entry.Name) -Force
    }
}

# Anything present in the previous version but absent from the new payload simply never comes back here --
# this is the obsolete-file cleanup an upgrade requires, without a separate deletion pass.
function Move-StagedEntriesToLive {
    foreach ($Entry in (Get-ChildItem -LiteralPath $StagingDir -Force)) {
        Move-Item -LiteralPath $Entry.FullName -Destination (Join-Path $InstallRoot $Entry.Name) -Force
    }
}

function Restore-RollbackEntries {
    foreach ($Entry in (Get-LiveEntries)) {
        Remove-Item -LiteralPath $Entry.FullName -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $RollbackDir -PathType Container) {
        Get-ChildItem -LiteralPath $RollbackDir -Force | ForEach-Object {
            Move-Item -LiteralPath $_.FullName -Destination (Join-Path $InstallRoot $_.Name) -Force
        }
    }
}

New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null
Resume-InterruptedTransaction
Assert-SafeInstallRoot

Expand-Payload
Test-StagedPayload
Write-JournalPhase -Phase 'staged'

Write-JournalPhase -Phase 'activating'
try {
    Move-LiveEntriesToRollback
    Move-StagedEntriesToLive
}
catch {
    Write-Warning "MORPHEUS installation activation failed; rolling back: $($_.Exception.Message)"
    Restore-RollbackEntries
    Remove-Item -LiteralPath $StagingDir -Recurse -Force -ErrorAction SilentlyContinue
    Remove-JournalIfPresent
    throw
}

$Marker = [ordered]@{
    ownedBy = 'MORPHEUS'
    version = $Version
    installedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
}
($Marker | ConvertTo-Json -Depth 4) | Set-Content -LiteralPath $OwnershipMarker -Encoding UTF8

Remove-Item -LiteralPath $RollbackDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $StagingDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-JournalIfPresent

Write-Host "MORPHEUS installation activated: $InstallRoot"
