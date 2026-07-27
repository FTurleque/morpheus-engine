# M20 — Release Engineering, Installation PROD & MORPHEUS 1.0

Statut : **EN COURS — issue #92 / PR Draft**

Baseline : `main@762b6dedd0760f8e08722ef5ee5dcf5057309574` (M19 intégré).

## Question de sortie

> MORPHEUS peut-il être installé, mis à jour, diagnostiqué et désinstallé comme un produit Windows/Linux sans Git, Maven ou JDK utilisateur, tout en préservant les données et en conservant les archives portables pour l’automatisation ?

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

## S0 — Release contract

- figer le layout programme/données ;
- figer les noms d’artefacts ;
- figer la stratégie upgrade/uninstall ;
- ADR-0088 proposée puis acceptée uniquement après preuve.

## S1 — Runtime layout & 1.0

- faire de `CliLayout` la source unique des chemins PROD ;
- ajouter `backupsDirectory` ;
- conserver `--data-dir`, `--config-dir`, `--db` et variables d’environnement ;
- tester Windows, Linux/XDG et overrides explicites ;
- exposer les chemins effectifs via `morpheus paths`.

## S2 — Portable release assets

- construire Windows app-image + ZIP ;
- construire Linux app-image + tar.gz ;
- embarquer le runtime Java ;
- générer `.sha256` ;
- vérifier chaque checksum après écriture ;
- produire un manifest de release versionné.

## S3 — Windows setup

- setup per-user ;
- destination `%LOCALAPPDATA%\Programs\MORPHEUS` ;
- option d’ajout au PATH utilisateur ;
- aucune dépendance JDK/Maven/Git à l’exécution ;
- aucune écriture de knowledge store dans le répertoire programme.

## S4 — Upgrade / uninstall

- AppId stable ;
- upgrade in-place ;
- data/config/logs/backups hors répertoire programme ;
- uninstall retire le programme mais préserve les données par défaut ;
- réinstallation retrouve les données existantes.

## S5 — Integrations opt-in

- MINOS/NEXUS désactivés sans configuration ;
- configuration externe au programme ;
- retrait de configuration réversible ;
- smokes post-install `version`, `paths`, `minos-status`, `nexus-status`, API health/readiness/metrics.

## S6 — Release from tag

- refuser une release depuis workspace sale ;
- version attendue explicite ;
- tag cible `v1.0.0` ;
- artefacts et checksums reproductibles à partir du tag ;
- documenter publication GitHub Release sans faire de GitHub Actions la preuve autoritative.

## S7 — Windows gate

`validate-m20.cmd -> scripts/validate-m20.ps1` doit couvrir :

```text
workspace/SHA/version
Maven clean test
portable Windows + checksum
setup build + checksum
silent install
install path
PATH option
no-JDK runtime smoke
program/data separation
upgrade preservation
uninstall preservation
reinstall preservation
API/CLI/integration smokes
exact-head stability
```

## S8 — Linux gate

`scripts/validate-m20.sh` doit couvrir :

```text
workspace/SHA/version
Maven clean test
Linux portable archive + checksum
no-JDK runtime smoke
XDG layout
API/CLI/integration smokes
exact-head stability
```

## S9 — Finalisation

- `docs/validation/VALIDATION_M20.md` avec SHA et résultats réels ;
- accepter ADR-0088 après preuves ;
- réconcilier roadmap/index ;
- PR Ready uniquement sur gate final vert ;
- merge uniquement après autorisation explicite.

## Gate M20

```text
Windows setup installation PASS
Windows portable ZIP PASS
Linux portable archive PASS
SHA-256 assets generated and verified
no JDK required at runtime
per-user install path PASS
PATH option PASS
program/data separation PASS
uninstall preserves data by default
upgrade preserves data/config PASS
MCP integrations opt-in + reversible
release from tag reproducible
release documentation complete
full Maven reactor PASS
```
