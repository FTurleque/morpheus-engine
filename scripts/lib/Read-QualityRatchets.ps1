# Reads one value of config\m21-quality-ratchets.properties, the living source of the quality ratchets.
#
# Dot-source this file, then call: Get-QualityRatchet -Path <properties file> -Key <key> -Kind integer|ratio
#
# A validator that compares an observed count to a ratchet reads the ratchet here; it never holds a copy. The path of
# the file and the name of the key stay in the calling script, so a reader of that script sees which ratchet it
# enforces, and the architecture tests that pin those names keep finding them there.
#
# There is no default. A missing file, a missing key, or a value that is not what the key is (a positive integer for a
# count, a ratio in (0, 1] for a coverage) throws, and the message names the key. The value is returned as the text
# the file holds, so the caller decides how to render it.
#
# This is the PowerShell twin of lib/read-quality-ratchet.sh. It has no CI lane (the Windows lane runs validate-m28 and
# the Python tests of the shell reader run on Linux), so it is verified by hand on Windows.

function Get-QualityRatchet {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Key,
        [Parameter(Mandatory)][ValidateSet('integer', 'ratio')][string]$Kind
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing M21 quality ratchet configuration: $Path"
    }
    $raw = $null
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed -notmatch '^([^=]+)=(.+)$') {
            throw "Invalid M21 quality ratchet entry: $trimmed"
        }
        if ($Matches[1].Trim() -ceq $Key) {
            $raw = $Matches[2].Trim()
            break
        }
    }
    if ($null -eq $raw) {
        throw "Missing M21 quality ratchet: $Key"
    }

    if ($Kind -eq 'integer') {
        $count = [int64]0
        if ($raw -notmatch '^[0-9]+$' -or -not [int64]::TryParse($raw, [ref]$count) -or $count -lt 1) {
            throw "M21 quality ratchet $Key must be a positive integer: '$raw'"
        }
    }
    else {
        $ratio = [double]0
        $parsed = [double]::TryParse($raw, [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$ratio)
        if (-not $parsed -or $ratio -le 0 -or $ratio -gt 1) {
            throw "M21 quality ratchet $Key must be a ratio in (0, 1]: '$raw'"
        }
    }
    return $raw
}
