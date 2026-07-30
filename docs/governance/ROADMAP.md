# Feuille de route — MORPHEUS

Statut : **MORPHEUS 1.2.0 PUBLIÉ — R3 TERMINÉ**

Dernière mise à jour : 30 juillet 2026

MORPHEUS est piloté par des preuves : contrats stables, tests reproductibles, SHA exacts et réponse explicite à chaque question de sortie.

## 1. Politique de branches

```text
feature / milestone branch -> develop
release branch             -> main après qualification
develop                    -> intégration
main                       -> stabilisation / livraison
```

## 2. Baseline

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21 → M27     ✅ validés et intégrés
R2            ✅ MORPHEUS 1.1.0 publié
M28           ✅ validé, intégré et livré dans 1.2.0
R3            ✅ MORPHEUS 1.2.0 publié
```

```text
stable version              1.2.0
stable tag                  v1.2.0
stable release commit       3ad9ebf030b58df97482e21e272c24feae6b9d86
qualified executable SHA    d08542026817f0d743766656a0197790c6809eca
final PR head               a2023d96dd0c4ad6d1f7a658bf3e7b4f8390e1bb
R3 PR                       #118 MERGED
R3 issue                    #117 CLOSED / completed
published assets            8/8
published parity            8/8 PASS
previous stable version     1.1.0
previous stable tag         v1.1.0
previous release commit     31506029ded1101f0571edeb0d79c59bbf3f68c6
```

## 3. M28 — MCP Client Integration & Installer Wiring

Question de sortie :

> Un utilisateur peut-il connecter explicitement le serveur MCP STDIO natif de MORPHEUS à Copilot, Claude et Codex, sans Docker obligatoire, sans écraser une configuration tierce et avec une désinstallation conservatrice ?

Réponse : **OUI — COMPLETE / VALIDATED / INTEGRATED / RELEASED**.

Clients :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Périmètre livré :

```text
native MCP command       morpheus mcp --stdio
JSON merge               Copilot JetBrains + Claude Desktop
CLI registration         Copilot CLI + Claude Code + Codex
backup before write      required
ownership registry       required
foreign entry overwrite  prohibited
modified entry removal   prohibited
setup tasks              explicit opt-in / unchecked
portable packaging       Windows + Linux
Docker required          false
```

Preuves :

```text
Windows exact-head       PASS @ 58adfeb13b79808da12830f2d0b0b24ec46f67e6
Linux/WSL exact-head     PASS @ 58adfeb13b79808da12830f2d0b0b24ec46f67e6
Tests                    608 PASS sur les deux plateformes
Architecture             243 PASS sur les deux plateformes
Windows portable         PASS
Windows installer        PASS
Linux portable           PASS
Same executable SHA      PASS
Post-gate executable     NONE
ADR-0096                 ACCEPTÉE
PR #116                  MERGED
Issue #115               CLOSED / completed
Release                  MORPHEUS 1.2.0
```

Références :

- [`../roadmap/M28_EXECUTION.md`](../roadmap/M28_EXECUTION.md)
- [`../validation/VALIDATION_M28.md`](../validation/VALIDATION_M28.md)
- [`../user/MCP_CLIENTS.md`](../user/MCP_CLIENTS.md)
- [`../developer/MCP.md`](../developer/MCP.md)

## 4. R3 — MORPHEUS 1.2.0

Question de sortie :

> M28 peut-il être consolidé dans `main` et publié comme MORPHEUS 1.2.0 avec une version cohérente, une qualification exacte Windows/Linux et huit assets vérifiés après publication ?

Réponse finale : **OUI — COMPLETE / VALIDATED / PUBLISHED**.

```text
issue                     #117 CLOSED / completed
PR                        #118 MERGED
qualified executable      d08542026817f0d743766656a0197790c6809eca
main release commit       3ad9ebf030b58df97482e21e272c24feae6b9d86
tag                       v1.2.0
Windows exact-head        PASS
Linux/WSL exact-head      PASS
same executable SHA       PASS
exact-tag Windows         PASS
exact-tag Linux           PASS
GitHub Release            PUBLISHED / stable / latest
published assets          8/8
published parity          8/8 PASS
```

Références :

- [`../roadmap/R3_EXECUTION.md`](../roadmap/R3_EXECUTION.md)
- [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)
- [`../release/RELEASE_NOTES_1.2.0.md`](../release/RELEASE_NOTES_1.2.0.md)
- [`../user/UPGRADE_1_2.md`](../user/UPGRADE_1_2.md)

## 5. Invariants

```text
DomainIdentity != source path
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
READ != WRITE
ALLOWED != applied
facts != inference
reasoning != mutation
MCP local remains native-first
Docker is not required
third-party client modification is opt-in
foreign `morpheus` entry is never overwritten
manual client changes are preserved
uninstall is state-driven
stable tag is immutable
release tag target == exact main release commit
published assets == exact-tag assets
```

## 6. Politique CI — juillet 2026

```text
GitHub Actions is not a gate
no workflow inspection
no workflow rerun
no workflow dispatch
no opportunistic .github/workflows changes
local Windows + Linux/WSL exact-head logs are authoritative
```

**La prochaine phase produit doit partir de `develop` après synchronisation avec la baseline stable 1.2.0.**