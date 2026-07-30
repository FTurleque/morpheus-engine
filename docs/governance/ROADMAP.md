# Feuille de route — MORPHEUS

Statut : **MORPHEUS 1.1.0 publié — M28 qualifié — intégration dans develop autorisée**

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
M28           ✅ qualifié Windows + Linux/WSL, merge autorisé
```

```text
stable version          1.1.0
stable tag              v1.1.0
release commit          31506029ded1101f0571edeb0d79c59bbf3f68c6
post-release main       8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
develop M28 baseline    8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
M28 executable head     58adfeb13b79808da12830f2d0b0b24ec46f67e6
M28 branch              m28-mcp-client-integration
M28 issue               #115
M28 PR                  #116
```

## 3. M28 — MCP Client Integration & Installer Wiring

Question de sortie :

> Un utilisateur peut-il connecter explicitement le serveur MCP STDIO natif de MORPHEUS à Copilot, Claude et Codex, sans Docker obligatoire, sans écraser une configuration tierce et avec une désinstallation conservatrice ?

Réponse : **OUI — PASS**.

Clients :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Périmètre :

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
Review threads           0
Blocking reviews         0
```

Références :

- [`../roadmap/M28_EXECUTION.md`](../roadmap/M28_EXECUTION.md)
- [`../validation/VALIDATION_M28.md`](../validation/VALIDATION_M28.md)
- [`../user/MCP_CLIENTS.md`](../user/MCP_CLIENTS.md)
- [`../developer/MCP.md`](../developer/MCP.md)

## 4. Suite produit

Après intégration M28 dans `develop`, une phase de consolidation 1.2.0 devra :

```text
bump reactor 1.1.0 -> 1.2.0
qualify release candidate Windows + Linux
merge release branch into main
create immutable v1.2.0 tag
build exact-tag artifacts
publish and verify GitHub Release
```

Le tag `v1.1.0` reste immuable.

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
```

## 6. Politique CI — juillet 2026

```text
GitHub Actions is not a gate
no workflow rerun
no workflow dispatch
no opportunistic .github/workflows changes
local Windows + Linux/WSL exact-head logs are authoritative
```