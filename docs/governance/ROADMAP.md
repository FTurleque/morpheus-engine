# Feuille de route — MORPHEUS

Statut : **MORPHEUS 1.1.0 publié — M28 intégré — R3 / 1.2.0 actif**

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
M28           ✅ validé et intégré dans develop
R3            🚧 stabilisation et publication 1.2.0
```

```text
stable version          1.1.0
stable tag              v1.1.0
release commit          31506029ded1101f0571edeb0d79c59bbf3f68c6
post-release main       8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
develop post-M28        2080c99895115464dafefb6515541666c5d972d8
M28 executable head     58adfeb13b79808da12830f2d0b0b24ec46f67e6
M28 merge commit        1e606c63b9f74e45a2c0b3d2162d3db4721f4af4
R3 branch               r3-release-1.2.0
R3 issue                #117 OPEN
R3 target               1.2.0 / v1.2.0
```

## 3. M28 — MCP Client Integration & Installer Wiring

Question de sortie :

> Un utilisateur peut-il connecter explicitement le serveur MCP STDIO natif de MORPHEUS à Copilot, Claude et Codex, sans Docker obligatoire, sans écraser une configuration tierce et avec une désinstallation conservatrice ?

Réponse : **OUI — COMPLETE / VALIDATED / INTEGRATED**.

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
```

Références :

- [`../roadmap/M28_EXECUTION.md`](../roadmap/M28_EXECUTION.md)
- [`../validation/VALIDATION_M28.md`](../validation/VALIDATION_M28.md)
- [`../user/MCP_CLIENTS.md`](../user/MCP_CLIENTS.md)
- [`../developer/MCP.md`](../developer/MCP.md)

## 4. R3 — Stabilisation et publication MORPHEUS 1.2.0

Question de sortie :

> M28 peut-il être consolidé dans `main` et publié comme MORPHEUS 1.2.0 avec une version cohérente, une qualification exacte Windows/Linux et huit assets vérifiés après publication ?

Réponse actuelle : **NON DÉMONTRÉE — gates R3 non exécutés**.

```text
issue                     #117 OPEN
branch                    r3-release-1.2.0
main baseline             8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
develop baseline          2080c99895115464dafefb6515541666c5d972d8
reactor target            1.2.0 across 17 POMs
tag target                v1.2.0
Windows exact-head        NOT RUN
Linux/WSL exact-head      NOT RUN
merge main                NOT AUTHORIZED
exact-tag builds          NOT RUN
GitHub Release            NOT PUBLISHED
```

Étapes :

```text
prepare release branch and version coherence
qualify exact same SHA on Windows and Linux/WSL
prove post-gate executable delta = NONE
merge release branch into main
create immutable v1.2.0 tag
build Windows and Linux from exact tag
publish eight GitHub Release assets
redownload and compare all SHA-256
reconcile documentation and close #117
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
