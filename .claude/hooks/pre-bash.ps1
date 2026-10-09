# PreToolUse hook: Bash
# Intercepts common mistakes before they run.
#
# ---------------------------------------------------------------------------
# ASCII ONLY. Do not reintroduce accented characters or em dashes in this file.
# ---------------------------------------------------------------------------
# .claude/settings.json runs this hook with `powershell`, i.e. Windows PowerShell
# 5.1, which decodes a BOM-less file as the ANSI code page instead of UTF-8. A
# UTF-8 em dash then decodes as a byte cp1252 maps to a curly quote, PowerShell
# treats curly quotes as string delimiters, and the whole script stops parsing:
# every Bash call returned a parser error instead of running this hook. That is
# how this file spent an entire revision doing nothing at all. The repository
# already keeps scripts/*.ps1 ASCII-only for the same reason. `rules/tooling.md`
# documents the constraint; post-edit.ps1 warns when a .ps1 gains a non-ASCII byte.
#
# Exit codes (Claude Code contract):
#   0 -> allow. stdout is advisory and shows in the transcript.
#   2 -> block. stderr is fed back to Claude so it can choose another command.
#   anything else -> non-blocking error. A `exit 1` here would NOT have blocked.
#
# Design principle: FAIL-OPEN on internal hook errors, FAIL-CLOSED only on a
# deliberately detected violation. A bug in this script must never block 100%
# of tool calls - that would be a worse outcome than the mistake it tries to
# prevent. Every risky check below runs inside the try block; any unexpected
# exception (bad JSON, null property, regex failure, PowerShell edge case)
# falls through to the catch and allows the command through unmodified.

function Deny([string]$message) {
    [Console]::Error.WriteLine($message)
    exit 2
}

try {
    $json = $Input | Out-String | ConvertFrom-Json -ErrorAction SilentlyContinue
    if (-not $json) { exit 0 }

    $command = $json.command
    if (-not $command) { exit 0 }

    $isProtectedBranchCommand = $command -match '(main|develop|master)'

    # Maven must go through the wrapper (3.9.16, Enforcer-checked).
    # RTK rewrites `./mvnw ...` into `rtk mvn ...`, and `rtk mvn` resolves the
    # wrapper distribution from ~/.m2/wrapper - it is NOT the bare `mvn` on PATH
    # (verified: `rtk mvn -v` reports 3.9.16, the PATH Maven is older). Warning on
    # it would fire on every single build command, so it is explicitly wrapped.
    $mavenIsWrapped = $command -match '(?i)(mvnw|rtk\s+mvnd?\b)'
    $mavenIsInvoked = $command -match '(?i)(?<![/\\\w.-])mvnd?\b'
    if ($mavenIsInvoked -and -not $mavenIsWrapped) {
        Write-Output "[WARNING] Utiliser './mvnw' (Maven Wrapper) plutot que 'mvn'"
        Write-Output "  Commande detectee: $($command.Trim().Substring(0, [Math]::Min(80, $command.Trim().Length)))"
        # Soft warning only - ne bloque pas
    }

    # Block: git push --force on main/develop/master
    if ($command -match 'git\s+push.*--force' -and $isProtectedBranchCommand) {
        Deny "[DANGER] Force push sur une branche protegee - bloque (voir rules/governance.md)"
    }

    # Block: git push --delete / -d on main/develop/master
    if ($command -match 'git\s+push.*(--delete|\s-d\s)' -and $isProtectedBranchCommand) {
        Deny "[DANGER] Suppression distante d'une branche protegee - bloque"
    }

    # Block: git branch -D / --delete --force on main/develop/master
    if ($command -match 'git\s+branch.*(-D|--delete\s+--force)' -and $isProtectedBranchCommand) {
        Deny "[DANGER] Suppression forcee d'une branche protegee - bloque"
    }

    # Block: rm -rf on project directories
    if ($command -match 'rm\s+-rf?\s+[^\s]*morpheus') {
        Deny "[DANGER] Suppression recursive d'un repertoire morpheus - bloque. Utiliser './mvnw clean'"
    }

    # Block: rm -rf with no path restriction at all (rm -rf / or rm -rf *)
    if ($command -match 'rm\s+-rf?\s+(/|\*|~)(\s|$)') {
        Deny "[DANGER] Suppression recursive non bornee - bloque"
    }

    # Warn: git reset --hard - destructive, discards local work silently
    if ($command -match 'git\s+reset\s+--hard') {
        Write-Output "[WARNING] 'git reset --hard' efface les modifications locales non commitees"
        Write-Output "  Verifier 'git status' avant de continuer si des changements utiles ne sont pas commites"
    }

    # Warn: git clean -f(d)(x) - destructive, deletes untracked files
    if ($command -match 'git\s+clean\s+-[a-zA-Z]*f') {
        Write-Output "[WARNING] 'git clean' supprime des fichiers non suivis de facon irreversible"
    }

    # Warn: staging or committing without naming paths.
    # A `git commit --amend` with no explicit path once swept an untracked scratch
    # directory into a published PR. Naming the paths is the fix that stuck.
    if ($command -match 'git\s+add\s+(-A|--all|\.)(\s|$)') {
        Write-Output "[WARNING] 'git add' sans chemin explicite embarque tout le repertoire de travail"
        Write-Output "  Nommer les fichiers : un repertoire de brouillon a deja fuite dans une PR"
    }
    if ($command -match 'git\s+commit\s' -and $command -match '(\s-a\b|\s-am\b|--all\b|--amend\b)' `
            -and $command -notmatch '--\s+\S') {
        Write-Output "[WARNING] 'git commit' global ou --amend sans chemin explicite"
        Write-Output "  Verifier 'git status' : ce qui est deja indexe partira avec le commit"
    }

    # Warn: milestone validator invoked without the version argument.
    # Each validate-m<N> script defaults to the version that was current when its
    # milestone shipped, so a bare invocation validates against a stale version
    # string. Read the current one in ProductMetadata / pom.xml and pass it.
    if ($command -match '(?i)scripts[/\\]validate[-.][a-z0-9]+\.(ps1|sh|cmd)' `
            -and $command -notmatch '(?i)\d+\.\d+\.\d+') {
        Write-Output "[WARNING] Validateur lance sans argument de version"
        Write-Output "  La valeur par defaut est datee du milestone : passer la version lue dans ProductMetadata"
    }

    exit 0
}
catch {
    # Any unexpected failure in this hook must never block the underlying
    # command. Log to stderr for diagnosis, then fail open.
    [Console]::Error.WriteLine("[HOOK ERROR] pre-bash.ps1 a leve une exception, execution autorisee par defaut: $($_.Exception.Message)")
    exit 0
}
