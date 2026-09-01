# PostToolUse hook: Edit | Write
# Warns when critical architectural layers or governance-sensitive files are modified.
#
# Design principle: FAIL-OPEN on internal hook errors. This hook only ever emits
# advisory output — it must never be able to break the surrounding tool call, so
# the entire body runs inside a try/catch and any exception is swallowed silently.

try {
    $json = $Input | Out-String | ConvertFrom-Json -ErrorAction SilentlyContinue
    if (-not $json) { exit 0 }

    $filePath = $json.file_path
    if (-not $filePath) { exit 0 }

    # Java source: critical architectural layers
    if ($filePath -match '\.java$') {
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
    }

    # Governance-sensitive files outside Java: convergence and ratchets
    if ($filePath -match [regex]::Escape('contracts\public-surfaces.tsv') -or $filePath -match 'contracts/public-surfaces\.tsv') {
        Write-Output "[GOVERNANCE] contracts/public-surfaces.tsv modifié"
        Write-Output "  → Mettre à jour en même temps docs/openapi/morpheus-v1-*.yaml (comparaison textuelle exacte dans les gates)"
        Write-Output "  → Aucune case vide autorisée : chaque absence porte un sentinelle explicite (EXPLICITLY_*)"
    }

    if ($filePath -match 'docs[\\/]openapi[\\/].*\.ya?ml$') {
        Write-Output "[GOVERNANCE] Spécification OpenAPI modifiée"
        Write-Output "  → Vérifier la convergence avec contracts/public-surfaces.tsv"
        Write-Output "  → additionalProperties: false et bornes explicites (maximum/maxItems/maxLength) exigées sur les schémas d'entrée"
    }

    if ($filePath -match 'config[\\/].*ratchet.*\.properties$') {
        Write-Output "[GOVERNANCE] Fichier de ratchets de qualité modifié: $filePath"
        Write-Output "  → Un ratchet ne doit JAMAIS descendre — vérifier qu'il s'agit d'une hausse justifiée par une preuve reproductible"
        Write-Output "  → Répercuter la nouvelle valeur dans .claude/rules/testing.md et .claude/rules/governance.md (voir rules/meta.md)"
    }

    if ($filePath -match '\.claude[\\/]rules[\\/].*\.md$') {
        Write-Output "[GOVERNANCE] Règle .claude modifiée: $filePath"
        Write-Output "  → Si un chiffre (coverage, nb de tests, nb d'ADR) est cité, revalider contre sa source avant de committer (cf. rules/meta.md)"
    }

    exit 0
}
catch {
    exit 0
}
