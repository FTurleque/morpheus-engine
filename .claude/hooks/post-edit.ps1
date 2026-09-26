# PostToolUse hook: Edit | Write
# Warns when critical architectural layers or governance-sensitive files are modified.
#
# ---------------------------------------------------------------------------
# ASCII ONLY. Do not reintroduce accented characters or em dashes in this file.
# ---------------------------------------------------------------------------
# See the same banner in pre-bash.ps1: `powershell` is Windows PowerShell 5.1 and
# decodes a BOM-less file as the ANSI code page, which turns a UTF-8 em dash into
# a curly quote that PowerShell treats as a string delimiter. Both hooks were
# silently unparseable for an entire revision because of it. The `nonAsciiScript`
# check below exists so the next .ps1 to acquire an accent is reported instead of
# dying quietly.
#
# Advisories are emitted as PostToolUse JSON `additionalContext` rather than as
# plain stdout: plain stdout only reaches the transcript, so the model that just
# made the edit never saw a word of it. A governance reminder nobody reads is an
# expensive no-op.
#
# Design principle: FAIL-OPEN on internal hook errors. This hook only ever emits
# advisory output - it must never be able to break the surrounding tool call, so
# the entire body runs inside a try/catch and any exception is swallowed silently.

try {
    $json = $Input | Out-String | ConvertFrom-Json -ErrorAction SilentlyContinue
    if (-not $json) { exit 0 }

    $filePath = $json.file_path
    if (-not $filePath) { exit 0 }

    $notes = New-Object System.Collections.ArrayList
    function Note([string]$text) { [void]$notes.Add($text) }

    # Java source: critical architectural layers
    if ($filePath -match '\.java$') {
        $criticalModules = @{
            'morpheus-domain'       = 'Couche DOMAINE - modele pur, aucune dependance sortante autorisee'
            'morpheus-application'  = 'Couche APPLICATION - definit les ports, ne connait aucun adaptateur'
            'morpheus-provider-sdk' = 'SDK PROVIDER - contrat public pour les providers tiers'
        }

        foreach ($module in $criticalModules.Keys) {
            if ($filePath -match [regex]::Escape($module)) {
                Note "[GOVERNANCE] Module critique modifie: $module. $($criticalModules[$module]). Verifier: ./mvnw test -pl morpheus-architecture-tests"
                break
            }
        }

        # Warn if touching milestone architecture tests directly
        if ($filePath -match 'morpheus-architecture-tests.*m\d+') {
            $milestone = [regex]::Match($filePath, 'm(\d+)').Value
            Note "[GOVERNANCE] Gate milestone $milestone modifie. Un gate ne se modifie jamais pour passer a tort. Valider: ./mvnw test -pl morpheus-architecture-tests -Dtest=*M$($milestone.Substring(1))*"
        }
    }

    # Any PowerShell script that acquires a non-ASCII byte. scripts/*.ps1 are ASCII
    # by convention and the two hooks are ASCII by hard requirement.
    if ($filePath -match '\.ps1$') {
        $nonAsciiScript = $false
        try {
            $bytes = [System.IO.File]::ReadAllBytes($filePath)
            if ($bytes.Length -gt 0) {
                $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
                foreach ($byte in $bytes) { if ($byte -gt 0x7F) { $nonAsciiScript = $true; break } }
                if ($nonAsciiScript -and -not $hasBom) {
                    Note "[ENCODAGE] $filePath contient des octets non-ASCII sans BOM. Windows PowerShell 5.1 le lira en page de codes ANSI et peut cesser de le parser entierement. Reecrire en ASCII (convention des scripts du depot) ou ajouter un BOM UTF-8."
                }
            }
        } catch { }

        if ($filePath -match '\.claude[\\/]hooks[\\/]') {
            Note "[GOVERNANCE] Hook Claude modifie. Le tester avant de conclure: echo '{\"command\":\"git status\"}' | powershell -NoProfile -File $filePath - une erreur de parsing rend le garde-fou muet sans rien casser de visible."
        }
    }

    # Milestone validators ship in pairs: the Windows/Linux parity is asserted.
    if ($filePath -match '(?i)scripts[\\/](validate|verify)-[a-z0-9]+\.(ps1|sh)$') {
        Note "[GOVERNANCE] Validateur modifie. La parite dual-platform est exigee: repercuter le meme changement dans le .ps1 ET le .sh correspondant."
    }

    # Governance-sensitive files outside Java: convergence and ratchets
    if ($filePath -match 'contracts[\\/]public-surfaces\.tsv') {
        Note "[GOVERNANCE] contracts/public-surfaces.tsv modifie. Mettre a jour docs/openapi/morpheus-v1-*.yaml dans le meme changement (comparaison textuelle exacte dans les gates). Aucune case vide: chaque absence porte un sentinelle EXPLICITLY_*."
    }

    if ($filePath -match 'docs[\\/]openapi[\\/].*\.ya?ml$') {
        Note "[GOVERNANCE] Specification OpenAPI modifiee. Verifier la convergence avec contracts/public-surfaces.tsv; additionalProperties: false et bornes explicites (maximum/maxItems/maxLength) exigees sur les schemas d'entree."
    }

    if ($filePath -match 'config[\\/].*ratchet.*\.properties$') {
        Note "[GOVERNANCE] Ratchets de qualite modifies: $filePath. Un ratchet ne descend jamais. Une hausse exige une preuve Windows ET Linux, et se repercute dans le meme changement partout: lancer RepositoryDocumentationCoherenceTest et ProductionIntegrityContractTest et laisser les echecs enumerer les destinations reelles (cf. .claude/rules/meta.md)."
    }

    if ($filePath -match 'docs[\\/]adr[\\/]\d{4}-.*\.md$') {
        Note "[GOVERNANCE] ADR ajoutee ou modifiee. AdrIndexCoherenceTest exige une ligne d'index et une seule dans docs/adr/README.md, un verdict identique des deux cotes, et un numero non deja attribue."
    }

    if ($filePath -match '\.claude[\\/](rules|skills)[\\/]') {
        Note "[GOVERNANCE] Surface IA modifiee: $filePath. Si un chiffre (coverage, nombre de tests, nombre d'ADR, version) y est cite, le revalider contre sa source vivante avant de committer (cf. .claude/rules/meta.md). Ces pages sont scannees par RepositoryDocumentationCoherenceTest."
    }

    if ($filePath -match '\.rtk[\\/]filters\.toml$') {
        Note "[TOOLING] Filtre RTK projet modifie. Rejouer ses tests inline puis re-accorder la confiance: rtk verify --filter morpheus-validators && rtk trust -y. Un filtre non re-trust n'est tout simplement pas charge."
    }

    if ($notes.Count -eq 0) { exit 0 }

    $payload = @{
        hookSpecificOutput = @{
            hookEventName    = 'PostToolUse'
            additionalContext = ($notes -join [Environment]::NewLine)
        }
    }
    Write-Output ($payload | ConvertTo-Json -Compress -Depth 5)
    exit 0
}
catch {
    exit 0
}
