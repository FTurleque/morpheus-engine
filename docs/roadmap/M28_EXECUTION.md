# M28 — MCP Client Integration & Installer Wiring

Statut : **TERMINÉ — VALIDÉ — INTÉGRÉ DANS DEVELOP**

Dernière mise à jour : 30 juillet 2026

```text
Issue                  #115 CLOSED / completed
PR                     #116 MERGED
Merge commit           1e606c63b9f74e45a2c0b3d2162d3db4721f4af4
Branch                 m28-mcp-client-integration
Baseline develop       8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Executable head        58adfeb13b79808da12830f2d0b0b24ec46f67e6
Stable release         v1.1.0
Target product release 1.2.0
Docker required        false
```

## 1. Question de sortie

> Un utilisateur peut-il installer MORPHEUS puis connecter explicitement son serveur MCP STDIO natif à Copilot, Claude et Codex, sans écraser de configuration tierce et avec une désinstallation conservatrice ?

Réponse : **OUI — PASS**.

## 2. Décisions

```text
MCP transport              STDIO natif
client modification        opt-in uniquement
Docker                      non requis
server name                 morpheus
Windows launcher            <install-root>\morpheus.exe
Linux launcher              <install-root>/bin/morpheus
arguments                   mcp --stdio
persistent data             MORPHEUS_DATA_DIR
persistent config           MORPHEUS_CONFIG_DIR
ownership registry          external persistent state
uninstall                   state-driven
foreign entry overwrite     interdit
modified managed entry      préservée
```

## 3. Livraison

### M28-S1 — Gouvernance et baseline

- [x] issue #115 cadrée ;
- [x] audit et réconciliation `main` / `develop` ;
- [x] branche créée depuis `develop` réconciliée ;
- [x] politique sans CI juillet 2026 respectée.

### M28-S2 — Gestionnaire d’intégration

- [x] cinq clients MCP pris en charge ;
- [x] backups, UTF-8 sans BOM et fusion JSON conservatrice ;
- [x] ownership `managed` / `preexisting` ;
- [x] idempotence, timeout borné et logs ;
- [x] entrées étrangères ou modifiées préservées ;
- [x] désinstallation state-driven.

### M28-S3 — Setup et distributions

- [x] cinq tâches Inno Setup opt-in ;
- [x] désinstallation conservatrice ;
- [x] gestionnaire embarqué dans le ZIP Windows ;
- [x] guide et scripts embarqués dans le TAR.GZ Linux ;
- [x] aucune dépendance Docker.

### M28-S4 — Tests, documentation et architecture

- [x] tests PowerShell avec faux profils et faux clients ;
- [x] contrat d’architecture M28 ;
- [x] guides utilisateur, développeur et embarqué ;
- [x] ADR-0096 acceptée ;
- [x] index et gouvernance réconciliés.

### M28-S5 — Qualification et intégration

- [x] Windows exact-head PASS ;
- [x] Linux/WSL exact-head PASS sur le même SHA ;
- [x] 608 tests et 243 tests d’architecture sur les deux plateformes ;
- [x] portable Windows + setup PASS ;
- [x] portable Linux PASS ;
- [x] review threads : 0 ;
- [x] reviews bloquantes : 0 ;
- [x] PR #116 Ready ;
- [x] PR #116 mergée dans `develop` ;
- [x] réconciliation post-merge ;
- [x] issue #115 fermée `completed`.

## 4. Clients cibles

```text
GitHub Copilot JetBrains  JSON servers.morpheus
Claude Desktop            JSON mcpServers.morpheus
GitHub Copilot CLI        copilot mcp add/get/remove
Claude Code               claude mcp add/get/remove --scope user
OpenAI Codex              codex mcp add/get/remove
```

## 5. Qualification observée

```text
Qualified executable SHA  58adfeb13b79808da12830f2d0b0b24ec46f67e6
Windows build              SUCCESS
Linux build                SUCCESS
Windows tests              608 PASS
Linux tests                608 PASS
Architecture tests         243 PASS sur les deux plateformes
Windows portable           PASS
Windows installer          PASS
Linux portable             PASS
Five clients               PASS
Conservative integration   PASS
Same executable SHA        PASS
Post-gate executable delta NONE
Docker required            false
Merge commit               1e606c63b9f74e45a2c0b3d2162d3db4721f4af4
```

## 6. Politique CI — juillet 2026

```text
no GitHub Actions gate
no workflow rerun
no workflow dispatch
no .github/workflows modification
local Windows + Linux/WSL exact-head logs are authoritative
```

## 7. Résultat final

```text
implementation          COMPLETE
Windows exact-head      PASS
Linux exact-head        PASS
same executable SHA     PASS
ADR-0096                ACCEPTED
PR #116                 MERGED
issue #115              CLOSED / completed
result                  M28 COMPLETE / VALIDATED / INTEGRATED
```