# PreToolUse hook: Bash
# Intercepts common mistakes before they run.

$json = $Input | Out-String | ConvertFrom-Json -ErrorAction SilentlyContinue
if (-not $json) { exit 0 }

$command = $json.command
if (-not $command) { exit 0 }

# Warn: bare `mvn` instead of `./mvnw`
if ($command -match '(?<![/\\w])mvn\s' -and $command -notmatch 'mvnw') {
    Write-Output "[WARNING] Utiliser './mvnw' (Maven Wrapper 3.9.16) plutôt que 'mvn'"
    Write-Output "  Commande détectée: $($command.Trim().Substring(0, [Math]::Min(80, $command.Trim().Length)))"
    # Soft warning only — ne bloque pas
}

# Warn: git push --force on main/develop
if ($command -match 'git\s+push.*--force' -and $command -match '(main|develop|master)') {
    Write-Output "[DANGER] Force push détecté sur une branche protégée — opération bloquée"
    exit 1
}

# Warn: rm -rf on project directories
if ($command -match 'rm\s+-rf?\s+[^\s]*morpheus') {
    Write-Output "[DANGER] Suppression récursive d'un répertoire morpheus — vérifier avant de continuer"
    exit 1
}
