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
# An empty array, never the result of an if expression: PowerShell unrolls @() returned by a statement into $null,
# and splatting $null hands the validator one null positional argument. validate-m19.ps1 takes none and refused it,
# and a validator with a -Version default received '' in its place.
$forwardedArguments = @()
if ($args.Count -gt 1) {
    $forwardedArguments = @($args[1..($args.Count - 1)])
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
