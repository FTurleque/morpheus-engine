[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') {
    throw 'MORPHEUS transactional upgrade verification must run on Windows.'
}

$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$Engine = Join-Path $RepoRoot 'distribution\windows\update-installation.ps1'
if (-not (Test-Path -LiteralPath $Engine -PathType Leaf)) {
    throw "Transactional installation engine not found: $Engine"
}

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function New-Payload([string] $Directory, [hashtable] $Files) {
    if (Test-Path -LiteralPath $Directory) { Remove-Item -LiteralPath $Directory -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $Directory | Out-Null
    foreach ($RelativePath in $Files.Keys) {
        $Target = Join-Path $Directory $RelativePath
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Target) | Out-Null
        Set-Content -LiteralPath $Target -Value ([string]$Files[$RelativePath]) -Encoding ascii -NoNewline
    }
}

function New-PayloadZip([string] $StagingSource, [string] $ZipPath) {
    if (Test-Path -LiteralPath $ZipPath) { Remove-Item -LiteralPath $ZipPath -Force }
    Compress-Archive -Path (Join-Path $StagingSource '*') -DestinationPath $ZipPath -CompressionLevel Fastest
}

$Root = Join-Path ([System.IO.Path]::GetTempPath()) ("morpheus-transactional-upgrade-" + [Guid]::NewGuid())
try {
    New-Item -ItemType Directory -Force -Path $Root | Out-Null

    # Scenario 1: fresh install activates the payload and records ownership.
    $InstallRoot1 = Join-Path $Root 'install1 with spaces'
    $PayloadSource1 = Join-Path $Root 'payload-v1'
    New-Payload -Directory $PayloadSource1 -Files @{ 'morpheus.exe' = 'v1-exe'; 'fileA.txt' = 'a' }
    $PayloadZip1 = Join-Path $Root 'payload-v1.zip'
    New-PayloadZip -StagingSource $PayloadSource1 -ZipPath $PayloadZip1

    & $Engine -InstallRoot $InstallRoot1 -PayloadZip $PayloadZip1 -Version '1.0.0'
    Assert-True (Test-Path -LiteralPath (Join-Path $InstallRoot1 'morpheus.exe')) 'Fresh install did not place morpheus.exe.'
    Assert-True (Test-Path -LiteralPath (Join-Path $InstallRoot1 'fileA.txt')) 'Fresh install did not place fileA.txt.'
    Assert-True (Test-Path -LiteralPath (Join-Path $InstallRoot1 '.morpheus-install.json')) 'Fresh install did not write the ownership marker.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $InstallRoot1 '.install-staging'))) 'Staging directory was not cleaned up.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $InstallRoot1 '.install-rollback'))) 'Rollback directory was not cleaned up.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $InstallRoot1 '.install-journal.json'))) 'Journal was not cleaned up.'

    # Scenario 2: idempotent reinstall with the same payload.
    & $Engine -InstallRoot $InstallRoot1 -PayloadZip $PayloadZip1 -Version '1.0.0'
    Assert-True ((Get-Content -Raw -LiteralPath (Join-Path $InstallRoot1 'morpheus.exe')) -eq 'v1-exe') 'Idempotent reinstall changed file content.'

    # Scenario 3: upgrade removes obsolete files and adds new ones.
    $PayloadSource2 = Join-Path $Root 'payload-v2'
    New-Payload -Directory $PayloadSource2 -Files @{ 'morpheus.exe' = 'v2-exe'; 'fileB.txt' = 'b'; 'sub\nested.txt' = 'n' }
    $PayloadZip2 = Join-Path $Root 'payload-v2.zip'
    New-PayloadZip -StagingSource $PayloadSource2 -ZipPath $PayloadZip2

    & $Engine -InstallRoot $InstallRoot1 -PayloadZip $PayloadZip2 -Version '2.0.0'
    Assert-True ((Get-Content -Raw -LiteralPath (Join-Path $InstallRoot1 'morpheus.exe')) -eq 'v2-exe') 'Upgrade did not activate the new morpheus.exe.'
    Assert-True (Test-Path -LiteralPath (Join-Path $InstallRoot1 'fileB.txt')) 'Upgrade did not add the new file.'
    Assert-True (Test-Path -LiteralPath (Join-Path $InstallRoot1 'sub\nested.txt')) 'Upgrade did not add the new nested file.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $InstallRoot1 'fileA.txt'))) 'Upgrade did not remove the obsolete file.'
    $Marker = Get-Content -Raw -LiteralPath (Join-Path $InstallRoot1 '.morpheus-install.json') | ConvertFrom-Json
    Assert-True ($Marker.version -eq '2.0.0') 'Ownership marker was not updated to the new version.'

    # Scenario 4: refuse a non-empty directory that MORPHEUS did not create.
    $ForeignRoot = Join-Path $Root 'foreign-install'
    New-Item -ItemType Directory -Force -Path $ForeignRoot | Out-Null
    Set-Content -LiteralPath (Join-Path $ForeignRoot 'unrelated.txt') -Value 'not ours' -Encoding ascii
    $Failed = $false
    try { & $Engine -InstallRoot $ForeignRoot -PayloadZip $PayloadZip1 -Version '1.0.0' }
    catch { $Failed = $true }
    Assert-True $Failed 'Engine did not refuse a foreign non-empty install root.'
    Assert-True (Test-Path -LiteralPath (Join-Path $ForeignRoot 'unrelated.txt')) 'Foreign file was removed despite refusal.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $ForeignRoot 'morpheus.exe'))) 'Foreign directory was activated despite refusal.'

    # Scenario 5: refuse a reparse point (directory junction) on the install root.
    $JunctionTarget = Join-Path $Root 'junction-target'
    New-Item -ItemType Directory -Force -Path $JunctionTarget | Out-Null
    $JunctionRoot = Join-Path $Root 'junction-install'
    $CmdExe = Join-Path ([Environment]::SystemDirectory) 'cmd.exe'
    & $CmdExe '/c' 'mklink' '/J' "$JunctionRoot" "$JunctionTarget" | Out-Null
    Assert-True (Test-Path -LiteralPath $JunctionRoot -PathType Container) 'Test setup could not create the junction.'
    $Failed = $false
    try { & $Engine -InstallRoot $JunctionRoot -PayloadZip $PayloadZip1 -Version '1.0.0' }
    catch { $Failed = $true }
    Assert-True $Failed 'Engine did not refuse a reparse point install root.'

    # Scenario 6: verification before activation -- a payload missing morpheus.exe must never touch the live
    # directory, and must fail before any journal/rollback is created.
    $InstallRoot6 = Join-Path $Root 'install6'
    New-Payload -Directory $InstallRoot6 -Files @{}
    & $Engine -InstallRoot $InstallRoot6 -PayloadZip $PayloadZip1 -Version '1.0.0'
    $BeforeCorrupt = Get-Content -Raw -LiteralPath (Join-Path $InstallRoot6 'morpheus.exe')
    $CorruptPayloadSource = Join-Path $Root 'payload-corrupt'
    New-Payload -Directory $CorruptPayloadSource -Files @{ 'notexe.txt' = 'no morpheus.exe here' }
    $CorruptZip = Join-Path $Root 'payload-corrupt.zip'
    New-PayloadZip -StagingSource $CorruptPayloadSource -ZipPath $CorruptZip
    $Failed = $false
    try { & $Engine -InstallRoot $InstallRoot6 -PayloadZip $CorruptZip -Version '9.9.9' }
    catch { $Failed = $true }
    Assert-True $Failed 'Engine accepted a payload without morpheus.exe.'
    Assert-True ((Get-Content -Raw -LiteralPath (Join-Path $InstallRoot6 'morpheus.exe')) -eq $BeforeCorrupt) 'Live installation was modified despite failed verification.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $InstallRoot6 '.install-journal.json'))) 'A journal was left behind after a pre-activation verification failure.'

    # Scenario 7: an expected payload checksum mismatch must also fail closed before touching the live install.
    $Failed = $false
    try { & $Engine -InstallRoot $InstallRoot6 -PayloadZip $PayloadZip1 -Version '1.0.0' -ExpectedPayloadSha256 '0000000000000000000000000000000000000000000000000000000000000000' }
    catch { $Failed = $true }
    Assert-True $Failed 'Engine accepted a payload with a mismatched checksum.'

    # Scenario 8: crash recovery. Simulate a previous run that crashed mid-activation (journal phase
    # 'activating', old files already moved into .install-rollback, live directory left partial) and confirm
    # the next run rolls that back before applying the newly requested (valid) upgrade.
    $InstallRoot8 = Join-Path $Root 'install8'
    New-Payload -Directory $InstallRoot8 -Files @{ 'morpheus.exe' = 'crash-old-exe'; 'oldfile.txt' = 'old' }
    Set-Content -LiteralPath (Join-Path $InstallRoot8 '.morpheus-install.json') -Value '{"ownedBy":"MORPHEUS","version":"1.0.0"}' -Encoding ascii
    $RollbackDir8 = Join-Path $InstallRoot8 '.install-rollback'
    New-Item -ItemType Directory -Force -Path $RollbackDir8 | Out-Null
    Move-Item -LiteralPath (Join-Path $InstallRoot8 'morpheus.exe') -Destination (Join-Path $RollbackDir8 'morpheus.exe')
    Move-Item -LiteralPath (Join-Path $InstallRoot8 'oldfile.txt') -Destination (Join-Path $RollbackDir8 'oldfile.txt')
    Set-Content -LiteralPath (Join-Path $InstallRoot8 '.install-journal.json') -Value '{"phase":"activating"}' -Encoding ascii

    & $Engine -InstallRoot $InstallRoot8 -PayloadZip $PayloadZip2 -Version '2.0.0'
    Assert-True ((Get-Content -Raw -LiteralPath (Join-Path $InstallRoot8 'morpheus.exe')) -eq 'v2-exe') 'Crash recovery did not apply the freshly requested upgrade.'
    Assert-True (Test-Path -LiteralPath (Join-Path $InstallRoot8 'fileB.txt')) 'Crash recovery did not bring in the new payload files.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $InstallRoot8 '.install-rollback'))) 'Crash recovery left a stale rollback directory behind.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $InstallRoot8 '.install-journal.json'))) 'Crash recovery left a stale journal behind.'

    Write-Host 'MORPHEUS transactional upgrade verification: PASS' -ForegroundColor Green
    Write-Host 'fresh-install=PASS idempotent=PASS upgrade-obsolete-cleanup=PASS foreign-refusal=PASS reparse-refusal=PASS verify-before-activate=PASS checksum-mismatch=PASS crash-recovery=PASS'
}
finally {
    Remove-Item -LiteralPath $Root -Recurse -Force -ErrorAction SilentlyContinue
}
