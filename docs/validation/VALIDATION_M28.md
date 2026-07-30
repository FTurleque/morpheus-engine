# M28 — Validation MCP Client Integration & Installer Wiring

Statut : **IMPLÉMENTATION TERMINÉE — CORRECTION WRAPPER WINDOWS — QUALIFICATION EXACT-HEAD À REJOUER**

Date : 30 juillet 2026

```text
Issue                  #115 OPEN
PR                     #116 DRAFT
Branch                 m28-mcp-client-integration
Baseline               8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Stable release         v1.1.0
Target release         1.2.0
Qualified exact head   NOT SET
Windows result         ATTEMPT 1 FAILED BEFORE GATE — RERUN REQUIRED
Linux/WSL result       NOT RUN
```

Plan : [`../roadmap/M28_EXECUTION.md`](../roadmap/M28_EXECUTION.md).

## 1. Périmètre

M28 ajoute :

- un gestionnaire Windows opt-in pour cinq clients MCP ;
- la fusion conservatrice des configurations Copilot JetBrains et Claude Desktop ;
- l’enregistrement via les CLI Copilot, Claude Code et Codex ;
- un registre de propriété persistant ;
- des backups avant écriture ;
- une désinstallation state-driven ;
- le packaging dans les distributions ;
- les tâches optionnelles de l’installateur Windows ;
- la documentation et les gates exact-head.

## 2. Commandes canoniques

Windows :

```powershell
.\validate-m28.cmd -Version 1.1.0 -BaseRef origin/develop
```

Linux/WSL :

```bash
MORPHEUS_M28_BASE_REF=origin/develop bash ./scripts/validate-m28.sh 1.1.0
```

## 3. Contrats attendus

### Configuration

```text
server name               morpheus
command Windows           <install-root>\morpheus.exe
command Linux             <install-root>/bin/morpheus
args                      mcp --stdio
env                       MORPHEUS_DATA_DIR + MORPHEUS_CONFIG_DIR
Docker required           false
```

### Clients

```text
Copilot JetBrains         servers.morpheus
Copilot CLI               mcp add/get/remove
Claude Code               mcp add/get/remove --scope user
Claude Desktop            mcpServers.morpheus
Codex                     mcp add/get/remove
```

### Conservation

```text
unrelated JSON properties          preserved
unrelated MCP servers              preserved
foreign morpheus entry             preserved
compatible preexisting entry       never removed
managed entry modified by user     preserved
managed unchanged entry            removable
invalid JSON                       rejected without write
backup before JSON write           required
state-driven uninstall             required
```

## 4. Qualification Windows

Statut : **ATTEMPT 1 FAILED BEFORE GATE — CORRECTION APPLIQUÉE — RERUN REQUIRED**

### Tentative 1

```text
Date                      2026-07-30
SHA                       3acfef278c2e238b53517a1338305c807466a1ef
Workspace tracked delta   NONE
Launcher                  validate-m28.cmd
Exit code                 9009
Failure                   powershell.exe not resolved by cmd.exe through PATH
Gate entered              NO
Tests executed            NO
Qualification result      NOT PRODUCED
```

Diagnostic observé :

```text
'powershell.exe' n'est pas reconnu en tant que commande interne ou externe
M28 validation FAILED with exit code 9009
```

Cause : le wrapper invoquait `powershell.exe` par nom simple. Le shell utilisateur pouvait exécuter PowerShell, mais `cmd.exe` ne trouvait pas `powershell.exe` dans son `PATH`.

Correction :

```text
primary   %SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe
32-bit    %SystemRoot%\Sysnative\WindowsPowerShell\v1.0\powershell.exe
fallback  pwsh.exe resolved through where
```

Un contrat d’architecture interdit désormais le retour à une invocation `powershell.exe` dépendante du `PATH`.

### Résultat exact-head à renseigner après relance

```text
sha                       —
reactor                   —
tests                     —
architectureTests         —
lineCoverage              —
branchCoverage            —
clients                   5 expected
jsonMerge                 —
cliRegistration           —
idempotency               —
foreignEntryPreservation  —
modifiedEntryPreservation —
stateDrivenUninstall      —
invalidJsonProtection     —
portableWindows           —
installerWindows          —
postGateExecutableDelta   —
result                    NOT RUN AFTER FIX
```

## 5. Qualification Linux/WSL

Statut : **NOT RUN**

À renseigner après exécution réelle :

```text
sha                       —
reactor                   —
tests                     —
architectureTests         —
lineCoverage              —
branchCoverage            —
staticIntegrationContract —
portableLinux             —
packagedGuidance          —
installer                 NOT_APPLICABLE
postGateExecutableDelta   —
result                    NOT RUN
```

Les modifications de profils clients réels sont Windows-only. Linux valide la non-régression, les contrats statiques et le packaging.

## 6. Revue de sécurité

```text
stdout MCP                    JSON-RPC uniquement
stderr MCP                    diagnostics
secrets in tool schemas       none
client configuration          explicit opt-in
third-party JSON overwrite    prohibited
unbounded native wait         prohibited
write capability escalation   none
Docker dependency             none
```

Le câblage client ne modifie pas les autorisations métier. `apply_change_lifecycle_transition` reste soumis à la capability `WRITE_CHANGE`, la confirmation, CAS, idempotency et audit.

## 7. Résultat courant

```text
implementation                COMPLETE
Windows attempt 1             FAILED BEFORE GATE / exit 9009
Windows wrapper correction    COMPLETE
Windows exact-head after fix  NOT RUN
Linux/WSL exact-head          NOT RUN
same SHA                      NOT PROVEN
post-gate executable delta    NOT PROVEN
PR                            #116 DRAFT
merge                         NOT AUTHORIZED
Result                        M28 IMPLEMENTED — QUALIFICATION PENDING
```

Aucun PASS ne doit être déclaré avant les deux logs exact-head réels.
