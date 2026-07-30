param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Target
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-AvailableValidationTargets {
    Get-ChildItem -LiteralPath $PSScriptRoot -Filter 'validate-*.ps1' -File |
        ForEach-Object { $_.BaseName.Substring('validate-'.Length) } |
        Sort-Object -Unique
}

$normalizedTarget = $Target.Trim().ToLowerInvariant()

if ($normalizedTarget -in @('list', '--list', '-list')) {
    Get-AvailableValidationTargets
    exit 0
}

if ($normalizedTarget -notmatch '^(c|d|m|r)[0-9]+$') {
    $available = (Get-AvailableValidationTargets) -join ', '
    throw "Invalid validation target '$Target'. Expected for example m28 or r3. Available targets: $available"
}

$delegate = Join-Path $PSScriptRoot "validate-$normalizedTarget.ps1"
if (-not (Test-Path -LiteralPath $delegate -PathType Leaf)) {
    $available = (Get-AvailableValidationTargets) -join ', '
    throw "Validation target '$normalizedTarget' does not exist. Available targets: $available"
}

& $delegate @args
if (-not $?) {
    exit 1
}

exit 0
