# The Authenticode verification of the MORPHEUS Windows binaries, in one place.
#
# Dot-sourced by the release qualification (scripts/verify-release-provenance.ps1), by the release rehearsal
# (scripts/validate-m20.ps1) and by the release build (distribution/build-release.ps1), so the rehearsal proves the
# code the qualification runs. Windows' own trust decision (Get-AuthenticodeSignature) is the authority.
#
# A verification answers PASS, UNSIGNED (no signature, and none required) or one named reason:
#   AUTHENTICODE_SIGNATURE_MISSING       no embedded signature, and one is required
#   AUTHENTICODE_SIGNATURE_INVALID       the content no longer matches its signature
#   AUTHENTICODE_PUBLISHER_NOT_DECLARED  a signed binary is checked while no publisher is declared
#   AUTHENTICODE_SIGNATURE_NOT_TRUSTED   the signature chains to no root the platform trusts
#   AUTHENTICODE_SIGNER_UNEXPECTED       a trusted signature issued to another subject than the declared publisher
#   AUTHENTICODE_TIMESTAMP_MISSING       a valid signature by the publisher without a timestamp countersignature
# A wrong signature is refused whatever the baseline: only the absence of a signature depends on it.

Set-StrictMode -Version Latest

$script:MorpheusSigningDeclarationKeys = @('publisherSubject', 'signingBaseline', 'timestampAuthority')

function Get-MorpheusSigningDeclarationPath([Parameter(Mandatory = $true)][string]$Repository) {
    return (Join-Path $Repository 'distribution\windows\authenticode-signing.properties')
}

function Read-MorpheusSigningDeclaration {
    param([Parameter(Mandatory = $true)][string]$Repository)

    $path = Get-MorpheusSigningDeclarationPath $Repository
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "AUTHENTICODE_PUBLISHER_NOT_DECLARED: the signing declaration is missing: $path"
    }
    $values = [ordered]@{}
    foreach ($line in Get-Content -LiteralPath $path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { throw "the signing declaration holds a line that is not key=value: $trimmed" }
        $key = $trimmed.Substring(0, $separator).Trim()
        if ($script:MorpheusSigningDeclarationKeys -notcontains $key) {
            throw "the signing declaration holds an unknown key: $key"
        }
        if ($values.Contains($key)) { throw "the signing declaration states $key twice" }
        $values[$key] = $trimmed.Substring($separator + 1).Trim()
    }
    foreach ($key in $script:MorpheusSigningDeclarationKeys) {
        if (-not $values.Contains($key)) { throw "the signing declaration does not state $key" }
    }
    $baseline = $values['signingBaseline']
    if ($baseline.Length -gt 0 -and $baseline -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
        throw "the signing baseline is not a version X.Y.Z: $baseline"
    }
    return [pscustomobject]@{
        Path = $path
        PublisherSubject = $values['publisherSubject']
        SigningBaseline = $baseline
        TimestampAuthority = $values['timestampAuthority']
    }
}

# True when a release of Version must be signed under the declared baseline; false below it or with no baseline.
function Test-MorpheusSigningRequired {
    param(
        [Parameter(Mandatory = $true)][string]$Version,
        [AllowEmptyString()][string]$Baseline
    )
    if ([string]::IsNullOrEmpty($Baseline)) { return $false }
    return ([version]$Version) -ge ([version]$Baseline)
}

function Test-MorpheusAuthenticode {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [AllowEmptyString()][string]$ExpectedSubject,
        [switch]$SignatureRequired
    )

    $name = Split-Path -Leaf $Path
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "the binary to verify does not exist: $Path" }
    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    $signer = if ($null -ne $signature.SignerCertificate) { [string]$signature.SignerCertificate.Subject } else { '' }

    function New-Outcome([string]$Outcome, [string]$Detail) {
        return [pscustomobject]@{ Name = $name; Path = $Path; Outcome = $Outcome; Signer = $signer; Detail = $Detail }
    }

    # A copy of a file listed in a Windows catalog verifies through the catalog, not through a signature the file
    # carries: only an embedded signature travels with a published binary.
    $embedded = [string]$signature.SignatureType -eq 'Authenticode'
    if ($signature.Status -eq [System.Management.Automation.SignatureStatus]::NotSigned -or -not $embedded) {
        if ($SignatureRequired) {
            return (New-Outcome 'AUTHENTICODE_SIGNATURE_MISSING' "$name carries no embedded Authenticode signature")
        }
        return (New-Outcome 'UNSIGNED' "$name carries no embedded Authenticode signature")
    }
    if ($signature.Status -eq [System.Management.Automation.SignatureStatus]::HashMismatch) {
        return (New-Outcome 'AUTHENTICODE_SIGNATURE_INVALID' "$name no longer matches its signature")
    }
    if ([string]::IsNullOrEmpty($ExpectedSubject)) {
        return (New-Outcome 'AUTHENTICODE_PUBLISHER_NOT_DECLARED' "$name is signed by '$signer' and no publisher is declared")
    }
    if ($signature.Status -ne [System.Management.Automation.SignatureStatus]::Valid) {
        return (New-Outcome 'AUTHENTICODE_SIGNATURE_NOT_TRUSTED' "$name is signed by '$signer' with status $($signature.Status)")
    }
    if ($signer -cne $ExpectedSubject) {
        return (New-Outcome 'AUTHENTICODE_SIGNER_UNEXPECTED' "$name is signed by '$signer', expected '$ExpectedSubject'")
    }
    if ($null -eq $signature.TimeStamperCertificate) {
        return (New-Outcome 'AUTHENTICODE_TIMESTAMP_MISSING' "$name is signed by '$signer' without a timestamp")
    }
    return (New-Outcome 'PASS' "$name is signed by '$signer', timestamped by '$($signature.TimeStamperCertificate.Subject)'")
}

# What the release manifest states about one binary: read from its bytes, never written from an intention.
function Get-MorpheusSignatureStatement {
    param([Parameter(Mandatory = $true)][string]$Path)

    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    if ($signature.Status -eq [System.Management.Automation.SignatureStatus]::NotSigned `
            -or [string]$signature.SignatureType -ne 'Authenticode') {
        return [ordered]@{ status = 'unsigned' }
    }
    return [ordered]@{ status = 'signed'; signer = [string]$signature.SignerCertificate.Subject }
}

# The statement for a set of binaries that ship as one asset (the executables of the Windows archive). They are signed
# together or not at all: a mixed set is a defect of the build, and no single statement could describe it.
function Get-MorpheusCombinedSignatureStatement {
    param([Parameter(Mandatory = $true)][string[]]$Paths)

    $statements = @($Paths | ForEach-Object { Get-MorpheusSignatureStatement -Path $_ })
    $distinct = @($statements | ForEach-Object { "$($_['status'])|$($_['signer'])" } | Sort-Object -Unique)
    if ($statements.Count -eq 0) { throw 'no executable to state a signature for' }
    if ($distinct.Count -ne 1) {
        throw "the executables of one asset do not share one signature status: $($distinct -join '; ')"
    }
    return $statements[0]
}

# The executables of an extracted Windows image: those outside the embedded runtime, and those of the runtime, which
# are third-party and only counted. The population is read from the image, never from a list of names.
function Get-MorpheusImageExecutables {
    param([Parameter(Mandatory = $true)][string]$ImageRoot)

    $root = (Resolve-Path -LiteralPath $ImageRoot).Path.TrimEnd('\')
    $runtimePrefix = $root + '\runtime\'
    $all = @(Get-ChildItem -LiteralPath $root -Recurse -File |
        Where-Object { $_.Extension -eq '.exe' -or $_.Extension -eq '.dll' } |
        Sort-Object FullName)
    return [pscustomobject]@{
        Product = @($all | Where-Object { -not $_.FullName.StartsWith($runtimePrefix, [StringComparison]::OrdinalIgnoreCase) } |
            ForEach-Object { $_.FullName })
        Runtime = @($all | Where-Object { $_.FullName.StartsWith($runtimePrefix, [StringComparison]::OrdinalIgnoreCase) } |
            ForEach-Object { $_.FullName })
    }
}
