# M28 — Validation MCP Client Integration & Installer Wiring

Statut : **DUAL-PLATFORM PASS — MERGED INTO DEVELOP**

Date : 30 juillet 2026

```text
Issue                  #115 CLOSED / completed
PR                     #116 MERGED
Merge commit           1e606c63b9f74e45a2c0b3d2162d3db4721f4af4
Baseline               8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Stable release         v1.1.0
Target release         1.2.0
Qualified exact head   58adfeb13b79808da12830f2d0b0b24ec46f67e6
Windows result         PASS
Linux/WSL result       PASS
Dual-platform result   PASS
```

## 1. Périmètre qualifié

M28 livre un gestionnaire Windows opt-in pour cinq clients MCP, une fusion JSON conservatrice, l’enregistrement CLI, un registre de propriété persistant, des backups, une désinstallation state-driven, le packaging Windows/Linux, les tâches optionnelles de l’installateur et la documentation associée.

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

## 2. Qualification Windows

```text
SHA                         58adfeb13b79808da12830f2d0b0b24ec46f67e6
Base ref                    origin/develop
Version                     1.1.0
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

## 3. Qualification Linux/WSL

```text
SHA                         58adfeb13b79808da12830f2d0b0b24ec46f67e6
Base ref                    origin/develop
Version                     1.1.0
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

## 4. Parité exact-head

Windows et Linux/WSL ont qualifié exactement :

```text
58adfeb13b79808da12830f2d0b0b24ec46f67e6
```

Après le gate Windows, le delta jusqu’au head de PR est resté exclusivement documentaire. Aucun code, script, POM, packaging, contrat runtime ou validateur n’a changé.

```text
same executable SHA          PASS
post-gate executable delta   NONE
review threads               0
blocking reviews             0
ADR-0096                     ACCEPTED
```

## 5. Incident initial corrigé

La première tentative Windows sur `3acfef278c2e238b53517a1338305c807466a1ef` a échoué avant entrée dans le gate avec `exit=9009`, car `powershell.exe` dépendait du `PATH` de `cmd.exe`. Le wrapper utilise désormais le chemin système Windows PowerShell, `Sysnative` et un fallback `pwsh.exe`; un contrat d’architecture verrouille ce comportement.

## 6. Sécurité

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

## 7. Décision finale

```text
implementation                COMPLETE
Windows exact-head            PASS
Linux/WSL exact-head          PASS
same executable SHA           PASS
post-gate executable delta    NONE
PR #116                       MERGED
merge commit                  1e606c63b9f74e45a2c0b3d2162d3db4721f4af4
issue #115                    CLOSED / completed
Result                        M28 COMPLETE / VALIDATED / INTEGRATED
```

Les avertissements Maven de dépendances, shading, API dépréciée et accès natif SQLite sont non bloquants : les deux reactors concluent `BUILD SUCCESS` et aucun test n’échoue.