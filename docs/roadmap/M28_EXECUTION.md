# M28 — MCP Client Integration & Installer Wiring

Statut : **IMPLÉMENTATION TERMINÉE — WINDOWS PASS — LINUX/WSL REQUIS**

Dernière mise à jour : 30 juillet 2026

```text
Issue                  #115
PR                     #116 DRAFT
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

La réponse ne peut être **PASS** qu’après qualification Windows et Linux/WSL du même contenu exécutable exact.

## 2. Baseline

Après R2 :

```text
main                   8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
develop before M28     bccc118dda6fd818cf801750187afa4ad10b96e4
main...develop         main ahead 57 / behind 0
develop reconciled     8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
M28 branch             created from reconciled develop
```

La réconciliation est un fast-forward sans divergence.

## 3. Décisions

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

## 4. Sous-étapes

### M28-S1 — Gouvernance et baseline

- [x] issue #115 renommée et cadrée M28 ;
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
- [x] désinstallation complète après retour à la forme gérée ;
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
- [x] ADR-0096 proposée.

### M28-S6 — Qualification et livraison

- [x] gate exact-head Windows ;
- [ ] gate exact-head Linux/WSL sur le même SHA exécutable ;
- [x] résultat Windows inscrit dans `VALIDATION_M28.md` ;
- [ ] résultat Linux inscrit dans `VALIDATION_M28.md` ;
- [x] PR #116 vers `develop` ouverte en draft ;
- [ ] review threads contrôlés ;
- [ ] PR passée Ready après double qualification ;
- [ ] merge seulement si tous les gates passent ;
- [ ] issue #115 fermée `completed` après merge et réconciliation.

## 5. Clients cibles

```text
GitHub Copilot JetBrains  JSON servers.morpheus
Claude Desktop            JSON mcpServers.morpheus
GitHub Copilot CLI        copilot mcp add/get/remove
Claude Code               claude mcp add/get/remove --scope user
OpenAI Codex              codex mcp add/get/remove
```

## 6. Propriété et désinstallation

Le registre persistant contient uniquement les intégrations observées comme compatibles au moment de l’installation.

```text
ownership=managed      créée par MORPHEUS
ownership=preexisting  déjà présente et compatible
```

Règles :

```text
preexisting compatible  suivi mais jamais supprimé
foreign incompatible    non écrasé, non revendiqué
managed unchanged       supprimable
managed modified        préservé
missing client entry    état nettoyé
missing client binary   avertissement, aucune suppression aveugle
```

## 7. Gate Windows observé

```text
SHA                        58adfeb13b79808da12830f2d0b0b24ec46f67e6
Build                      SUCCESS
Tests                      608 PASS
Architecture tests         243 PASS
Coverage lines             0.452226
Coverage branches          0.384456
MCP manager                PASS
Five clients               PASS
Portable Windows           PASS
Installer Windows          PASS
Post-gate executable delta NONE
Result                     M28 VALIDATION PASS
```

La première tentative sur `3acfef...` avait échoué avant le gate avec l’exit `9009`; le wrapper Windows a été corrigé et verrouillé par test avant cette qualification réussie.

## 8. Gate Linux/WSL restant

Le gate Linux doit être exécuté sur le SHA exécutable Windows qualifié :

```text
58adfeb13b79808da12830f2d0b0b24ec46f67e6
```

Commande :

```bash
MORPHEUS_M28_BASE_REF=origin/develop bash ./scripts/validate-m28.sh 1.1.0
```

Il valide les contrats statiques, le reactor hérité et le packaging Linux. Les mutations réelles des profils clients Windows restent qualifiées uniquement sur Windows.

## 9. Politique CI — juillet 2026

```text
no GitHub Actions gate
no workflow rerun
no workflow dispatch
no .github/workflows modification
local Windows + Linux/WSL exact-head logs are authoritative
```

## 10. État courant

```text
implementation          COMPLETE
Windows exact-head      PASS @ 58adfeb13b79808da12830f2d0b0b24ec46f67e6
Linux exact-head        NOT RUN
same executable SHA     NOT YET PROVEN
PR                      #116 DRAFT / mergeable
merge                   NOT AUTHORIZED
result                  WINDOWS QUALIFIED — LINUX/WSL PENDING
```
