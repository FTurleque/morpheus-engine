# M28 — MCP Client Integration & Installer Wiring

Statut : **TERMINÉ — DOUBLE QUALIFICATION EXACT-HEAD PASS — MERGE AUTORISÉ**

Dernière mise à jour : 30 juillet 2026

```text
Issue                  #115
PR                     #116 READY -> develop
Branch                 m28-mcp-client-integration
Baseline main          8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
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

## 3. Sous-étapes

### M28-S1 — Gouvernance et baseline

- [x] issue #115 cadrée M28 ;
- [x] audit `main` / `develop` ;
- [x] réconciliation fast-forward de `develop` ;
- [x] branche `m28-mcp-client-integration` créée ;
- [x] politique sans CI juillet 2026 conservée.

### M28-S2 — Gestionnaire d’intégration

- [x] `integration/configure-mcp-clients.ps1` ;
- [x] Copilot JetBrains JSON ;
- [x] Claude Desktop JSON ;
- [x] Copilot CLI ;
- [x] Claude Code ;
- [x] Codex ;
- [x] sauvegardes avant écriture ;
- [x] JSON UTF-8 sans BOM ;
- [x] fusion préservant propriétés et serveurs existants ;
- [x] refus d’écraser une entrée étrangère ;
- [x] idempotence ;
- [x] registre de propriété ;
- [x] journal d’audit ;
- [x] timeout borné des clients CLI ;
- [x] désinstallation state-driven ;
- [x] conservation des entrées modifiées.

### M28-S3 — Setup et distributions

- [x] cinq tâches Inno Setup opt-in et décochées ;
- [x] wrapper de vérification setup ;
- [x] désinstallation conservatrice avant suppression des fichiers ;
- [x] gestionnaire embarqué dans le ZIP Windows ;
- [x] gestionnaire et guide embarqués dans le TAR.GZ Linux ;
- [x] aucune dépendance Docker introduite.

### M28-S4 — Tests et contrats

- [x] qualification PowerShell avec faux clients ;
- [x] préservation des propriétés JSON ;
- [x] enregistrement des cinq clients ;
- [x] idempotence ;
- [x] entrée étrangère préservée ;
- [x] entrée gérée modifiée préservée ;
- [x] désinstallation state-driven ;
- [x] JSON invalide protégé ;
- [x] contrat d’architecture M28 ;
- [x] gate Windows `validate-m28.cmd` ;
- [x] gate Linux `scripts/validate-m28.sh`.

### M28-S5 — Documentation

- [x] guide utilisateur `docs/user/MCP_CLIENTS.md` ;
- [x] guide embarqué `integration/README.md` ;
- [x] mise à jour `docs/developer/MCP.md` ;
- [x] index user/developer/roadmap/validation ;
- [x] roadmap et statut documentaire globaux ;
- [x] ADR-0096 acceptée.

### M28-S6 — Qualification et livraison

- [x] gate exact-head Windows ;
- [x] gate exact-head Linux/WSL sur le même SHA exécutable ;
- [x] résultats Windows et Linux inscrits dans `VALIDATION_M28.md` ;
- [x] PR #116 vers `develop` ;
- [x] review threads contrôlés : 0 ;
- [x] reviews bloquantes contrôlées : 0 ;
- [x] PR passée Ready après double qualification ;
- [x] merge autorisé par les gates ;
- [ ] merge effectué ;
- [ ] issue #115 fermée `completed` après réconciliation post-merge.

## 4. Clients cibles

```text
GitHub Copilot JetBrains  JSON servers.morpheus
Claude Desktop            JSON mcpServers.morpheus
GitHub Copilot CLI        copilot mcp add/get/remove
Claude Code               claude mcp add/get/remove --scope user
OpenAI Codex              codex mcp add/get/remove
```

## 5. Propriété et désinstallation

```text
ownership=managed      créée par MORPHEUS
ownership=preexisting  déjà présente et compatible
```

```text
preexisting compatible  suivi mais jamais supprimé
foreign incompatible    non écrasé, non revendiqué
managed unchanged       supprimable
managed modified        préservé
missing client entry    état nettoyé
missing client binary   avertissement, aucune suppression aveugle
```

## 6. Qualification observée

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
```

La première tentative Windows sur `3acfef...` avait échoué avant le gate avec l’exit `9009`. Le wrapper a été corrigé puis qualifié sur Windows et Linux/WSL au SHA `58adfeb...`.

## 7. Politique CI — juillet 2026

```text
no GitHub Actions gate
no workflow rerun
no workflow dispatch
no .github/workflows modification
local Windows + Linux/WSL exact-head logs are authoritative
```

## 8. État courant

```text
implementation          COMPLETE
Windows exact-head      PASS
Linux exact-head        PASS
same executable SHA     PASS
ADR-0096                ACCEPTED
PR                      #116 READY / mergeable
merge                   AUTHORIZED
result                  M28 COMPLETE — MERGE PENDING
```