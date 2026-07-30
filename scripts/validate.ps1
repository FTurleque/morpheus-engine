Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-AvailableValidationTargets {
    Get-ChildItem -LiteralPath $PSScriptRoot -Filter 'validate-*.ps1' -File |
        ForEach-Object { $_.BaseName.Substring('validate-'.Length) } |
        Sort-Object -Unique
}

if ($args.Count -eq 0) {
    $available = (Get-AvailableValidationTargets) -join ', '
    throw "Missing validation target. Expected for example m28 or r3. Available targets: $available"
}

$target = [string]$args[0]
$normalizedTarget = $target.Trim().ToLowerInvariant()
$forwardedArguments = if ($args.Count -gt 1) {
    @($args[1..($args.Count - 1)])
} else {
    @()
}

if ($normalizedTarget -in @('list', '--list', '-list')) {
    Get-AvailableValidationTargets
    exit 0
}

if ($normalizedTarget -notmatch '^(c|d|m|r)[0-9]+$') {
    $available = (Get-AvailableValidationTargets) -join ', '
    throw "Invalid validation target '$target'. Expected for example m28 or r3. Available targets: $available"
}

$delegate = Join-Path $PSScriptRoot "validate-$normalizedTarget.ps1"
if (-not (Test-Path -LiteralPath $delegate -PathType Leaf)) {
    $available = (Get-AvailableValidationTargets) -join ', '
    throw "Validation target '$normalizedTarget' does not exist. Available targets: $available"
}

& $delegate @forwardedArguments
if (-not $?) {
    exit 1
}

exit 0
