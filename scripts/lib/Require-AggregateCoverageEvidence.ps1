# Refuses M21 coverage evidence that was not produced on the aggregate scale.
#
# Two gates measure coverage and they measure different grandeurs: CoverageQualityGateTest sums each module's
# own JaCoCo report, AggregateCoverageGateTest reads the canonical jacoco-aggregate report. Until 09/09/2026
# both wrote one file under one name, so whichever ran last in a given reactor invocation left its own ratio
# behind and every validator compared it to the same threshold under the same label -- with no way to tell
# which scale it had just read. Each gate now writes its own file, declares its scale on the first line, and
# a consumer that wants one scale must refuse the other rather than conclude from whatever it found.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$EvidencePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $EvidencePath -PathType Leaf)) {
    Write-Error "Missing M21 aggregate coverage evidence: $EvidencePath"
    exit 1
}

$scope = ''
foreach ($line in Get-Content -LiteralPath $EvidencePath) {
    if ($line -match '^coverageScope=(.*)$') {
        $scope = $matches[1].Trim()
        break
    }
}
if ($scope -ne 'aggregate') {
    $declared = if ([string]::IsNullOrWhiteSpace($scope)) { '<none>' } else { $scope }
    Write-Error "M21 coverage evidence is not the aggregate scope: $EvidencePath declares '$declared'"
    exit 1
}
