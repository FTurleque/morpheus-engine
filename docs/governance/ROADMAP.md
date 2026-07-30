# Feuille de route — MORPHEUS

Statut : **MORPHEUS 1.1.0 publié — M28 / intégration clients MCP actif**

Dernière mise à jour : 30 juillet 2026

MORPHEUS est piloté par des preuves : contrats stables, tests reproductibles, SHA exacts et réponse explicite à chaque question de sortie.

## 1. Politique de branches

```text
feature / milestone branch -> develop
release branch             -> main après qualification
develop                    -> intégration
main                       -> stabilisation / livraison
```

M28 part de `develop` réconciliée avec la baseline post-release 1.1.0.

## 2. Baseline publiée

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21 → M27     ✅ validés et intégrés
R2            ✅ MORPHEUS 1.1.0 publié
M28           🚧 MCP Client Integration & Installer Wiring
```

```text
stable version          1.1.0
stable tag              v1.1.0
release commit          31506029ded1101f0571edeb0d79c59bbf3f68c6
R2 qualified head       31212087ee5fab3c88b269d56f7f21402f31b683
post-release main       8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
develop M28 baseline    8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
M28 branch              m28-mcp-client-integration
M28 issue               #115
```

## 3. R2 — MORPHEUS 1.1.0

Réponse de sortie : **OUI — COMPLETE**.

```text
PR                     #114 MERGED
Issue                  #113 CLOSED / completed
Windows gate           603 tests / 238 architecture PASS
Linux/WSL gate         603 tests / 238 architecture PASS
same SHA               PASS
exact-tag builds       PASS Windows + Linux
GitHub Release         stable / 8 assets
published parity       8/8 PASS
```

Références :

- [`../roadmap/R2_EXECUTION.md`](../roadmap/R2_EXECUTION.md)
- [`../validation/VALIDATION_R2.md`](../validation/VALIDATION_R2.md)
- [`../release/RELEASE_NOTES_1.1.0.md`](../release/RELEASE_NOTES_1.1.0.md)

## 4. M28 — MCP Client Integration & Installer Wiring

Question de sortie :

> Un utilisateur peut-il connecter explicitement le serveur MCP STDIO natif de MORPHEUS à Copilot, Claude et Codex, sans Docker obligatoire, sans écraser une configuration tierce et avec une désinstallation conservatrice ?

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

Points d’entrée :

- plan : [`../roadmap/M28_EXECUTION.md`](../roadmap/M28_EXECUTION.md) ;
- preuve : [`../validation/VALIDATION_M28.md`](../validation/VALIDATION_M28.md) ;
- utilisateur : [`../user/MCP_CLIENTS.md`](../user/MCP_CLIENTS.md) ;
- architecture : [`../developer/MCP.md`](../developer/MCP.md).

## 5. État M28

```text
baseline audit           COMPLETE
branch                   CREATED
implementation           COMPLETE
packaging wiring         COMPLETE
documentation            COMPLETE
Windows exact-head       NOT RUN
Linux/WSL exact-head     NOT RUN
same SHA                 NOT PROVEN
PR                       NOT OPEN
merge                    NOT AUTHORIZED
```

Aucun PASS M28 n’est déclaré avant réception des deux logs exact-head.

## 6. Suite produit

Après intégration M28 dans `develop`, une phase de consolidation release doit :

```text
bump reactor 1.1.0 -> 1.2.0
qualify release candidate Windows + Linux
merge release branch into main
create immutable v1.2.0 tag
build exact-tag artifacts
publish and verify GitHub Release
```

Le tag `v1.1.0` reste immuable.

## 7. Capacités acquises

```text
provider-neutral domain and identities
CURRENT / PROPOSED / HISTORICAL
snapshots and versions
explicit RequirementDelta lifecycle
traceability and bounded traversal
multi-provider composition
provider SDK and external plugins
portfolio multi-project intelligence
Query DSL / saved views / reporting
Policy Packs / overrides / dry-run / audit
optional remote HTTPS + RBAC
SQLite backup / restore
assisted reasoning separated from facts
CLI / MCP STDIO / HTTP
portable Windows/Linux + Windows setup
CycloneDX and provenance
native MCP client integration layer (M28 implementation)
```

## 8. Invariants

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

## 9. Politique CI — juillet 2026

```text
GitHub Actions is not a gate
no workflow rerun
no workflow dispatch
no opportunistic .github/workflows changes
local Windows + Linux/WSL exact-head logs are authoritative
```
