# M28 — Validation MCP Client Integration & Installer Wiring

Statut : **WINDOWS EXACT-HEAD PASS — LINUX/WSL EXACT-HEAD REQUIS**

Date : 30 juillet 2026

```text
Issue                  #115 OPEN
PR                     #116 DRAFT
Branch                 m28-mcp-client-integration
Baseline               8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Stable release         v1.1.0
Target release         1.2.0
Candidate exact head   58adfeb13b79808da12830f2d0b0b24ec46f67e6
Windows result         PASS
Linux/WSL result       NOT RUN
Dual-platform result   NOT YET PROVEN
```

Plan : [`../roadmap/M28_EXECUTION.md`](../roadmap/M28_EXECUTION.md).

## 1. Périmètre qualifié

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

Statut : **PASS**

### Tentative 1 — échec avant entrée dans le gate

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

Le wrapper a ensuite été corrigé pour résoudre Windows PowerShell par chemin système absolu, avec prise en charge de `Sysnative` et fallback `pwsh.exe`. Un contrat d’architecture interdit désormais le retour à une invocation dépendante du `PATH`.

### Tentative 2 — qualification exact-head

```text
Date                       2026-07-30
SHA                        58adfeb13b79808da12830f2d0b0b24ec46f67e6
Base ref                   origin/develop
Version                    1.1.0
Workspace tracked delta    NONE avant gate
Reactor                    17/17 SUCCESS
Build                      SUCCESS
Tests                      608 PASS
Architecture tests         243 PASS
Line coverage              0.452226
Branch coverage            0.384456
MCP client manager         PASS
Clients                    5
JSON merge                 PASS
CLI registration           PASS
Idempotency                PASS
Foreign entry preservation PASS
Modified entry preservation PASS
State-driven uninstall     PASS
Invalid JSON protection    PASS
Portable Windows           PASS
Installer Windows          PASS
Docker required            false
Post-gate executable delta NONE
Result                     M28 VALIDATION PASS
```

Preuves de packaging observées :

```text
M28 Windows portable integration payload    PASS
M28 Windows setup integration wiring         PASS
MORPHEUS-1.1.0-windows-x64-setup.exe         BUILT
morpheus-1.1.0-windows-x64.zip               BUILT
```

Les avertissements Maven de dépendances, shading, API dépréciée et accès natif SQLite sont non bloquants pour ce gate : ils ne produisent ni failure ni error et le reactor conclut `BUILD SUCCESS`.

## 5. Qualification Linux/WSL

Statut : **NOT RUN**

Le gate Linux/WSL doit qualifier exactement :

```text
58adfeb13b79808da12830f2d0b0b24ec46f67e6
```

Résultat à renseigner :

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

Les modifications de profils clients réels sont Windows-only. Linux valide la non-régression, les contrats statiques et le packaging portable Linux.

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
Windows exact-head after fix  PASS
Linux/WSL exact-head          NOT RUN
same SHA                      NOT YET PROVEN
Windows post-gate delta       NONE
PR                            #116 DRAFT
merge                         NOT AUTHORIZED
Result                        WINDOWS QUALIFIED — LINUX/WSL PENDING
```

M28 ne peut être déclaré entièrement PASS, la PR ne peut devenir Ready et le merge reste interdit avant un gate Linux/WSL PASS sur le même SHA exact.