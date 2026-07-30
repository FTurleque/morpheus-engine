# M28 — Validation MCP Client Integration & Installer Wiring

Statut : **DUAL-PLATFORM EXACT-HEAD PASS — MERGE AUTORISÉ**

Date : 30 juillet 2026

```text
Issue                  #115 OPEN
PR                     #116 DRAFT -> READY
Branch                 m28-mcp-client-integration
Baseline               8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Stable release         v1.1.0
Target release         1.2.0
Qualified exact head   58adfeb13b79808da12830f2d0b0b24ec46f67e6
Windows result         PASS
Linux/WSL result       PASS
Dual-platform result   PASS
```

Plan : [`../roadmap/M28_EXECUTION.md`](../roadmap/M28_EXECUTION.md).

## 1. Périmètre qualifié

M28 ajoute un gestionnaire Windows opt-in pour cinq clients MCP, une fusion JSON conservatrice, l’enregistrement CLI, un registre de propriété persistant, des backups, une désinstallation state-driven, le packaging Windows/Linux, les tâches optionnelles de l’installateur et la documentation associée.

Clients qualifiés :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Contrat commun :

```text
server name               morpheus
command Windows           <install-root>\morpheus.exe
command Linux             <install-root>/bin/morpheus
args                      mcp --stdio
env                       MORPHEUS_DATA_DIR + MORPHEUS_CONFIG_DIR
Docker required           false
```

## 2. Incident Windows initial

La première tentative sur `3acfef278c2e238b53517a1338305c807466a1ef` a échoué avant entrée dans le gate avec `exit=9009`, car `powershell.exe` était résolu via le `PATH` de `cmd.exe`.

Le wrapper utilise désormais le chemin système Windows PowerShell, prend en charge `Sysnative` et utilise `pwsh.exe` uniquement en fallback. Un contrat d’architecture verrouille cette résolution.

## 3. Qualification Windows

```text
Date                        2026-07-30
SHA                         58adfeb13b79808da12830f2d0b0b24ec46f67e6
Base ref                    origin/develop
Version                     1.1.0
Workspace tracked delta     NONE avant gate
Reactor                     17/17 SUCCESS
Build                       SUCCESS
Tests                       608 PASS
Architecture tests          243 PASS
Line coverage               0.452226
Branch coverage             0.384456
MCP client manager          PASS
Clients                     5
JSON merge                  PASS
CLI registration            PASS
Idempotency                 PASS
Foreign entry preservation  PASS
Modified entry preservation PASS
State-driven uninstall      PASS
Invalid JSON protection     PASS
Portable Windows            PASS
Installer Windows           PASS
Docker required             false
Post-gate executable delta  NONE
Result                      M28 VALIDATION PASS
```

Preuves de packaging :

```text
M28 Windows portable integration payload    PASS
M28 Windows setup integration wiring         PASS
MORPHEUS-1.1.0-windows-x64-setup.exe         BUILT
morpheus-1.1.0-windows-x64.zip               BUILT
```

## 4. Qualification Linux/WSL

```text
Date                        2026-07-30
SHA                         58adfeb13b79808da12830f2d0b0b24ec46f67e6
Base ref                    origin/develop
Version                     1.1.0
Workspace tracked delta     NONE avant gate
Java                        OpenJDK 21.0.11
Reactor                     17/17 SUCCESS
Build                       SUCCESS
Tests                       608 PASS
Architecture tests          243 PASS
Line coverage               0.452246
Branch coverage             0.384456
Static integration contract PASS
Clients                     5
JSON/CLI mutations          WINDOWS_ONLY
Portable Linux              PASS
Packaged guidance           PASS
Installer                    NOT_APPLICABLE
Docker required             false
Post-gate executable delta  NONE
Result                      M28 VALIDATION PASS
```

Preuves de packaging :

```text
M28 Linux portable integration payload       PASS
morpheus-1.1.0-linux-x64.tar.gz               BUILT
MCP client integration guidance               PASS
```

## 5. Parité exact-head

Windows et Linux/WSL ont qualifié exactement :

```text
58adfeb13b79808da12830f2d0b0b24ec46f67e6
```

Après le gate Windows, seuls des documents de roadmap et de validation ont été modifiés. Aucun code, script, POM, packaging, contrat runtime ou validateur n’a changé. Le gate Linux a été exécuté sur le même SHA exécutable qualifié.

```text
same executable SHA          PASS
Windows post-gate delta      documentation only
Linux post-gate delta        NONE
requalification required     NO
```

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

Le câblage client ne modifie pas les autorisations métier. Les mutations de lifecycle restent soumises à `WRITE_CHANGE`, confirmation, CAS, idempotency et audit.

## 7. Décision

```text
implementation                COMPLETE
Windows exact-head            PASS
Linux/WSL exact-head          PASS
same executable SHA           PASS
post-gate executable delta    NONE
review threads                0
blocking reviews              0
ADR-0096                      ACCEPTÉE
PR                            #116 READY / MERGEABLE
merge                         AUTHORIZED
Result                        M28 COMPLETE — DUAL-PLATFORM PASS
```

Les avertissements Maven de dépendances, shading, API dépréciée et accès natif SQLite sont non bloquants : aucun test ni module n’échoue et les deux reactors concluent `BUILD SUCCESS`.