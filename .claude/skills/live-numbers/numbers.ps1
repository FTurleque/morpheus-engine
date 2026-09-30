# Lit tous les chiffres perissables de MORPHEUS depuis leur source vivante.
# Aucune valeur n'est codee ici : chaque section affiche d'ou elle vient.
# Parite exigee avec numbers.sh. ASCII uniquement (cf. .claude/rules/tooling.md).
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
Set-Location -LiteralPath $root

function Section([string]$title) { Write-Host ''; Write-Host "== $title" }
function Item([string]$label, $value) { Write-Host ("  {0,-42} {1}" -f $label, $value) }

Section 'Version produit  <- pom.xml (racine)'
$pom = Get-Content -Raw -LiteralPath 'pom.xml'
Item 'pom.xml <version>' ([regex]::Match($pom, '<version>([^<]+)</version>').Groups[1].Value)

Section 'Ratchets  <- config/m21-quality-ratchets.properties'
Get-Content -LiteralPath 'config/m21-quality-ratchets.properties' |
    Where-Object { $_ -match '^[a-zA-Z]+=' } |
    ForEach-Object { $pair = $_ -split '=', 2; Item $pair[0] $pair[1] }

Section 'Plafonds qualifies  <- les deux gates, une constante par echelle'
@(
    'morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java',
    'morpheus-coverage-report/src/test/java/com/morpheus/coverage/AggregateCoverageGateTest.java'
) | ForEach-Object {
    foreach ($match in [regex]::Matches((Get-Content -Raw -LiteralPath $_),
            '(PER_MODULE|AGGREGATE)_QUALIFIED_(LINE|BRANCH)_RATIO = ([0-9.]+)d')) {
        Item $match.Value.Split(' ')[0] $match.Groups[3].Value
    }
}

Section 'Preuves de couverture  <- ecrites par le dernier clean verify'
foreach ($proof in @(
        'morpheus-architecture-tests/target/m21-per-module-coverage-summary.txt',
        'morpheus-architecture-tests/target/m21-aggregate-coverage-summary.txt')) {
    if (Test-Path -LiteralPath $proof) {
        Item (Split-Path -Leaf $proof) (Get-Content -LiteralPath $proof -TotalCount 1)
    } else {
        Item (Split-Path -Leaf $proof) 'absente - lancer ./mvnw clean verify'
    }
}

Section 'ADR  <- docs/adr/ (compte, jamais recopie)'
$records = @(Get-ChildItem -LiteralPath 'docs/adr' -Filter '0*.md' -File)
$numbers = @($records | ForEach-Object { $_.Name.Substring(0, 4) })
Item 'records numerotes' $records.Count
Item 'numero le plus haut attribue' ($numbers | Sort-Object | Select-Object -Last 1)
Item 'doublons de numerotation' (($numbers | Group-Object | Where-Object Count -gt 1 |
        ForEach-Object Name) -join ' ')

Section 'Reacteur  <- pom.xml <module>'
Item 'modules declares' ([regex]::Matches($pom, '<module>').Count)

Section 'Manifeste de convergence  <- contracts/public-surfaces.tsv'
$manifest = Get-Content -LiteralPath 'contracts/public-surfaces.tsv'
Item 'lignes (en-tete comprise)' $manifest.Count
[regex]::Matches(($manifest -join "`n"), 'EXPLICITLY_[A-Z_]+') |
    Group-Object Value | Sort-Object Name |
    ForEach-Object { Item $_.Name $_.Count }

Section "Gates d'architecture  <- repertoires reels"
Item 'suites par milestone' ((Get-ChildItem -LiteralPath `
    'morpheus-architecture-tests/src/test/java/com/morpheus/architecture' -Directory |
    ForEach-Object Name) -join ' ')

Section 'Schema SQLite  <- la constante qui le declare'
$schema = Get-Content -Raw -LiteralPath `
    'morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSchemaManager.java'
Item 'SUPPORTED_SCHEMA_VERSION' ([regex]::Match($schema, 'SUPPORTED_SCHEMA_VERSION = (\d+)').Groups[1].Value)

Section 'Versions pinnees  <- proprietes du pom.xml racine'
foreach ($match in [regex]::Matches($pom,
        '<((?:jackson|sqlite-jdbc|junit|archunit|mcp-sdk|slf4j|jacoco|dependency-check\.maven\.plugin)\.version)>([^<]+)<')) {
    Item ('<' + $match.Groups[1].Value + '>') $match.Groups[2].Value
}
