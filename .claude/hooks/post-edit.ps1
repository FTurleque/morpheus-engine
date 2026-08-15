# PostToolUse hook: Edit | Write
# Warns when critical architectural layers are modified.

$json = $Input | Out-String | ConvertFrom-Json -ErrorAction SilentlyContinue
if (-not $json) { exit 0 }

$filePath = $json.file_path
if (-not $filePath) { exit 0 }

# Only care about Java source files
if ($filePath -notmatch '\.java$') { exit 0 }

$criticalModules = @{
    'morpheus-domain'       = 'Couche DOMAINE — zéro dépendance vers application autorisée'
    'morpheus-application'  = 'Couche APPLICATION — point central de la logique métier'
    'morpheus-provider-sdk' = 'SDK PROVIDER — contrat public pour les providers'
}

foreach ($module in $criticalModules.Keys) {
    if ($filePath -match [regex]::Escape($module)) {
        Write-Output "[GOVERNANCE] Module critique modifié: $module"
        Write-Output "  $($criticalModules[$module])"
        Write-Output "  → Vérifier les contraintes: ./mvnw test -pl morpheus-architecture-tests"
        break
    }
}

# Warn if touching milestone architecture tests directly
if ($filePath -match 'morpheus-architecture-tests.*m\d+') {
    $milestone = [regex]::Match($filePath, 'm(\d+)').Value
    Write-Output "[GOVERNANCE] Gate milestone $milestone modifié — s'assurer que le gate reste passant"
    Write-Output "  → Valider: ./mvnw test -pl morpheus-architecture-tests -Dtest=*M$($milestone.Substring(1))*"
}
