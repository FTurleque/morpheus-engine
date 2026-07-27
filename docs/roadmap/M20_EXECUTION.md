# M20 — Release Engineering, Installation PROD & MORPHEUS 1.0

Statut : **✅ VALIDÉ TECHNIQUEMENT — issue #92 / PR #93 — merge non autorisé**

Baseline : `main@762b6dedd0760f8e08722ef5ee5dcf5057309574` (M19 intégré).

Code qualifié Windows + Linux : `9199ed43c4bd8596a97db055eeff17ae31399eb8`.

## Question de sortie

> MORPHEUS peut-il être installé, mis à jour, diagnostiqué et désinstallé comme un produit Windows/Linux sans Git, Maven ou JDK utilisateur, tout en préservant les données et en conservant les archives portables pour l’automatisation ?

**Réponse : OUI.**

## Invariants

```text
programme != données persistantes
update programme != reset knowledge store
uninstall programme != delete knowledge store
runtime utilisateur != JDK utilisateur
setup Windows != portable ZIP
release asset != artifact non vérifié
checksum publié == bytes publiés
MCP/MINOS/NEXUS restent opt-in et réversibles
```

## Cible produit

Version : **1.0.0**.

### Windows

Programme :

```text
%LOCALAPPDATA%\Programs\MORPHEUS
```

État persistant :

```text
%LOCALAPPDATA%\MORPHEUS\data
%LOCALAPPDATA%\MORPHEUS\config
%LOCALAPPDATA%\MORPHEUS\logs
%LOCALAPPDATA%\MORPHEUS\backups
```

Setup :

```text
MORPHEUS-1.0.0-windows-x64-setup.exe
MORPHEUS-1.0.0-windows-x64-setup.exe.sha256
```

Portable :

```text
morpheus-1.0.0-windows-x64.zip
morpheus-1.0.0-windows-x64.zip.sha256
```

### Linux

État par défaut :

```text
${XDG_DATA_HOME:-$HOME/.local/share}/morpheus
${XDG_CONFIG_HOME:-$HOME/.config}/morpheus
${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/logs
${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups
```

Portable :

```text
morpheus-1.0.0-linux-x64.tar.gz
morpheus-1.0.0-linux-x64.tar.gz.sha256
```

## S0 — Release contract ✅

- layout programme/données figé ;
- noms d’artefacts figés ;
- stratégie upgrade/uninstall figée ;
- ADR-0088 acceptée après preuves Windows + Linux.

## S1 — Runtime layout & 1.0 ✅

- `CliLayout` est la source des chemins PROD ;
- `backupsDirectory` présent ;
- `--data-dir`, `--config-dir`, `--db` et variables d’environnement conservés ;
- Windows, Linux/XDG et overrides explicites testés ;
- chemins effectifs exposés par `morpheus paths` pour data/config/logs/database.

## S2 — Portable release assets ✅

- Windows app-image + ZIP ;
- Linux app-image + tar.gz ;
- runtime Java embarqué ;
- `.sha256` générés ;
- checksum revérifié après écriture ;
- manifest release version/tag/SHA/assets.

## S3 — Windows setup ✅

- setup per-user ;
- destination `%LOCALAPPDATA%\Programs\MORPHEUS` ;
- option PATH utilisateur ;
- aucune dépendance JDK/Maven/Git à l’exécution ;
- knowledge store hors répertoire programme ;
- Inno Setup 7.0.2 bootstrap vérifié Authenticode si nécessaire.

## S4 — Upgrade / uninstall ✅

- AppId stable ;
- upgrade in-place ;
- data/config/logs/backups hors répertoire programme ;
- uninstall retire le programme et préserve les données ;
- réinstallation retrouve les données existantes.

## S5 — Integrations opt-in ✅

- MINOS/NEXUS désactivés sans configuration ;
- configuration externe au programme ;
- retrait de configuration réversible ;
- smokes `version`, `paths`, `minos-status`, `nexus-status`, API health/readiness/metrics.

## S6 — Release from tag ✅

- release refusée depuis workspace sale ;
- version attendue explicite ;
- tag exact requis `== HEAD` ;
- artefacts + checksums liés au SHA/tag ;
- GitHub Actions non autoritatif pour la preuve du jalon.

## S7 — Windows gate ✅

Validation réelle via `validate-m20.cmd -> scripts/validate-m20.ps1` :

```text
SHA                             9199ed43c4bd8596a97db055eeff17ae31399eb8
Version                         1.0.0
Tests                           454/454 PASS
Architecture                    182/182 PASS
Failures/errors/skipped         0/0/0
Reactor                         14/14 SUCCESS
BUILD                           SUCCESS
Installer contract              PASS
Tagged Windows release build    PASS
Windows portable ZIP            PASS
Windows setup EXE               PASS
SHA-256 + manifest              PASS
Install + PATH + no-JDK + API   PASS
Upgrade preservation            PASS
Uninstall preservation          PASS
Reinstall preservation          PASS
Exact-head stability            PASS
```

## S8 — Linux gate ✅

Validation réelle via `scripts/validate-m20.sh` dans un clone Linux sous `$HOME` sur filesystem ext4/WSL2 :

```text
SHA                             9199ed43c4bd8596a97db055eeff17ae31399eb8
Version                         1.0.0
Full Maven reactor              PASS
Architecture                    182/182 PASS
Reactor                         14/14 SUCCESS
Tagged Linux release build      PASS
Linux portable tar.gz           PASS
SHA-256                         PASS
Embedded runtime / no user JDK  PASS
XDG data/config/state           PASS
SQLite                          PASS
MINOS/NEXUS opt-in defaults     PASS
Exact-head stability            PASS
```

Asset Linux qualifié :

```text
morpheus-1.0.0-linux-x64.tar.gz
bytes   39449807
sha256  f0c28959f492e246810293db74f26e6929a27bb2b6d75bad1f6f48ca309c1bf8
```

## S9 — Finalisation ✅

- `docs/validation/VALIDATION_M20.md` contient les résultats réels ;
- ADR-0088 acceptée ;
- roadmap/index réconciliés ;
- delta post-gate limité à la documentation et contrôlé avant Ready ;
- PR #93 Ready uniquement après ce contrôle ;
- merge uniquement après autorisation explicite.

## Gate M20

```text
Windows setup installation       PASS
Windows portable ZIP             PASS
Linux portable archive           PASS
SHA-256 assets                   PASS Windows + Linux
no JDK required at runtime       PASS Windows + Linux
per-user install path            PASS
PATH option                      PASS
program/data separation          PASS
uninstall preserves data         PASS
upgrade preserves data/config    PASS
MCP integrations opt-in          PASS Windows + Linux
release from exact tag           PASS Windows + Linux
release documentation            PASS
full Maven reactor               PASS Windows + Linux
exact-head stability             PASS Windows + Linux
```

Preuve autoritative : [`../validation/VALIDATION_M20.md`](../validation/VALIDATION_M20.md).

**M20 est techniquement terminé. La PR #93 ne doit pas être mergée sans autorisation explicite du propriétaire.**
