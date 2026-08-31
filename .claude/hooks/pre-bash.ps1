# PreToolUse hook: Bash
# Intercepts common mistakes before they run.
#
# Design principle: FAIL-OPEN on internal hook errors, FAIL-CLOSED only on a
# deliberately detected violation. A bug in this script must never block 100%
# of tool calls — that would be a worse outcome than the mistake it tries to
# prevent. Every risky check below runs inside the try block; any unexpected
# exception (bad JSON, null property, regex failure, PowerShell edge case)
# falls through to the catch and allows the command through unmodified.

try {
    $json = $Input | Out-String | ConvertFrom-Json -ErrorAction SilentlyContinue
    if (-not $json) { exit 0 }

    $command = $json.command
    if (-not $command) { exit 0 }

    $isProtectedBranchCommand = $command -match '(main|develop|master)'

    # Warn: bare `mvn` instead of `./mvnw`
    if ($command -match '(?<![/\\w])mvn\s' -and $command -notmatch 'mvnw') {
        Write-Output "[WARNING] Utiliser './mvnw' (Maven Wrapper 3.9.16) plutôt que 'mvn'"
        Write-Output "  Commande détectée: $($command.Trim().Substring(0, [Math]::Min(80, $command.Trim().Length)))"
        # Soft warning only — ne bloque pas
    }

    # Block: git push --force on main/develop/master
    if ($command -match 'git\s+push.*--force' -and $isProtectedBranchCommand) {
        Write-Output "[DANGER] Force push détecté sur une branche protégée — opération bloquée"
        exit 1
    }

    # Block: git push --delete / -d on main/develop/master
    if ($command -match 'git\s+push.*(--delete|\s-d\s)' -and $isProtectedBranchCommand) {
        Write-Output "[DANGER] Suppression distante d'une branche protégée — opération bloquée"
        exit 1
    }

    # Block: git branch -D / --delete --force on main/develop/master
    if ($command -match 'git\s+branch.*(-D|--delete\s+--force)' -and $isProtectedBranchCommand) {
        Write-Output "[DANGER] Suppression forcée d'une branche protégée — opération bloquée"
        exit 1
    }

    # Warn: git reset --hard — destructive, discards local work silently
    if ($command -match 'git\s+reset\s+--hard') {
        Write-Output "[WARNING] 'git reset --hard' efface les modifications locales non commitées"
        Write-Output "  Vérifier 'git status' avant de continuer si des changements utiles ne sont pas commités"
    }

    # Warn: git clean -f(d)(x) — destructive, deletes untracked files
    if ($command -match 'git\s+clean\s+-[a-zA-Z]*f') {
        Write-Output "[WARNING] 'git clean' supprime des fichiers non suivis de façon irréversible"
    }

    # Block: rm -rf on project directories
    if ($command -match 'rm\s+-rf?\s+[^\s]*morpheus') {
        Write-Output "[DANGER] Suppression récursive d'un répertoire morpheus — vérifier avant de continuer"
        exit 1
    }

    # Block: rm -rf with no path restriction at all (rm -rf / or rm -rf *)
    if ($command -match 'rm\s+-rf?\s+(/|\*|~)(\s|$)') {
        Write-Output "[DANGER] Suppression récursive non bornée détectée — opération bloquée"
        exit 1
    }

    exit 0
}
catch {
    # Any unexpected failure in this hook must never block the underlying
    # command. Log to stderr for diagnosis, then fail open.
    Write-Error "[HOOK ERROR] pre-bash.ps1 a levé une exception, exécution autorisée par défaut: $($_.Exception.Message)"
    exit 0
}
