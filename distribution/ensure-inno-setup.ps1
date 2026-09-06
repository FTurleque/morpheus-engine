[CmdletBinding()]
param(
    [string]$ToolDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$innoVersion = '7.0.2'
$assetName = "innosetup-$innoVersion-x64.exe"
$assetUri = "https://github.com/jrsoftware/issrc/releases/download/is-7_0_2/$assetName"
$expectedSignerPattern = 'Pyrsys B\.V\.'

if ([string]::IsNullOrWhiteSpace($ToolDirectory)) {
    $ToolDirectory = Join-Path $repo "validation-output\m20\tooling\inno-setup-$innoVersion"
}
$toolRoot = [IO.Path]::GetFullPath($ToolDirectory)

function Get-TrustedIsccPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$Strict,
        [switch]$PinnedBootstrap
    )

    try {
        if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
            throw "Inno Setup compiler is not a file: $Path"
        }
        $resolved = (Resolve-Path -LiteralPath $Path).Path
        $signature = Get-AuthenticodeSignature -LiteralPath $resolved
        if ($signature.Status -ne [System.Management.Automation.SignatureStatus]::Valid) {
            throw "Inno Setup compiler Authenticode signature is not valid: $($signature.Status) ($resolved)"
        }
        $subject = [string]$signature.SignerCertificate.Subject
        if ($subject -notmatch $expectedSignerPattern) {
            throw "Unexpected Inno Setup compiler signer: $subject ($resolved)"
        }

        $version = [Diagnostics.FileVersionInfo]::GetVersionInfo($resolved)
        $hasVersionMetadata = $version.FileMajorPart -ne 0 -or
            $version.FileMinorPart -ne 0 -or
            $version.FileBuildPart -ne 0 -or
            $version.FilePrivatePart -ne 0

        if ($hasVersionMetadata) {
            if ($version.FileMajorPart -ne 7 -or $version.FileMinorPart -ne 0 -or $version.FileBuildPart -ne 2) {
                throw "Inno Setup compiler must be version ${innoVersion}: $resolved reports $($version.FileVersion)"
            }
        }
        elseif (-not $PinnedBootstrap) {
            # Arbitrary system, PATH and explicit-override candidates must prove their exact version.
            # The official compiler currently carries no usable FileVersionInfo after bootstrap, so only
            # the compiler extracted from our already-pinned and Authenticode-validated installer may use
            # installer provenance in place of absent PE version metadata.
            throw "Inno Setup compiler version metadata is unavailable for unpinned candidate: $resolved"
        }

        $versionEvidence = if ($hasVersionMetadata) { $version.FileVersion } else { "pinned-bootstrap-$innoVersion" }
        Write-Host "Inno Setup compiler trust: PASS ($resolved, $versionEvidence, $subject)"
        return $resolved
    }
    catch {
        if ($Strict) { throw }
        Write-Verbose "Ignoring untrusted or unpinned ISCC candidate '$Path': $($_.Exception.Message)"
        return $null
    }
}

function Find-Iscc {
    if ($env:MORPHEUS_ISCC) {
        # An explicit override is an operator trust decision. Never silently fall back if it is invalid.
        return Get-TrustedIsccPath -Path $env:MORPHEUS_ISCC -Strict
    }

    $candidates = [System.Collections.Generic.List[string]]::new()
    $roots = @($env:ProgramFiles, ${env:ProgramFiles(x86)}) | Where-Object { $_ }
    foreach ($root in $roots) {
        foreach ($major in 7, 6) {
            $candidate = Join-Path $root "Inno Setup $major\ISCC.exe"
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                $candidates.Add($candidate)
            }
        }
    }
    $command = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($command) { $candidates.Add($command.Source) }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        $trusted = Get-TrustedIsccPath -Path $candidate
        if ($trusted) { return $trusted }
    }
    return $null
}

$existing = Find-Iscc
if ($existing) {
    Write-Output $existing
    exit 0
}

New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
$installer = Join-Path $toolRoot $assetName
$compilerRoot = Join-Path $toolRoot 'compiler'

if (-not (Test-Path -LiteralPath $installer)) {
    Write-Host "Downloading pinned Inno Setup $innoVersion x64 from JRSoftware's immutable GitHub release..."
    Invoke-WebRequest -Uri $assetUri -OutFile $installer -UseBasicParsing
}

$signature = Get-AuthenticodeSignature -LiteralPath $installer
if ($signature.Status -ne [System.Management.Automation.SignatureStatus]::Valid) {
    throw "Inno Setup bootstrap Authenticode signature is not valid: $($signature.Status)"
}
$subject = [string]$signature.SignerCertificate.Subject
if ($subject -notmatch $expectedSignerPattern) {
    throw "Unexpected Inno Setup signer: $subject"
}
Write-Host "Inno Setup bootstrap signature: PASS ($subject)"

$existingPortable = Get-ChildItem -LiteralPath $compilerRoot -Recurse -Filter ISCC.exe -File -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -eq $existingPortable) {
    Remove-Item -LiteralPath $compilerRoot -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $compilerRoot | Out-Null
    Write-Host "Installing Inno Setup $innoVersion in portable current-user mode under validation-output..."
    $arguments = @(
        '/VERYSILENT',
        '/SUPPRESSMSGBOXES',
        '/NORESTART',
        '/CURRENTUSER',
        '/PORTABLE=1',
        "/DIR=`"$compilerRoot`""
    )
    $process = Start-Process -FilePath $installer -ArgumentList $arguments -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Inno Setup bootstrap installer failed with exit code $($process.ExitCode)"
    }
}

$iscc = Get-ChildItem -LiteralPath $compilerRoot -Recurse -Filter ISCC.exe -File -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -eq $iscc) {
    throw "Inno Setup bootstrap completed but ISCC.exe was not found under $compilerRoot"
}

# The no-version-metadata exception is valid only for the compiler under the controlled bootstrap root.
$resolvedCompilerRoot = (Resolve-Path -LiteralPath $compilerRoot).Path.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
$resolvedIscc = (Resolve-Path -LiteralPath $iscc.FullName).Path
$compilerPrefix = $resolvedCompilerRoot + [IO.Path]::DirectorySeparatorChar
if (-not $resolvedIscc.StartsWith($compilerPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Pinned bootstrap compiler escaped controlled compiler root: $resolvedIscc"
}

$trustedIscc = Get-TrustedIsccPath -Path $iscc.FullName -Strict -PinnedBootstrap
Write-Host "Inno Setup compiler ready: $trustedIscc"
Write-Output $trustedIscc
