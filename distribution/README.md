# MORPHEUS — Distribution et release 1.2

MORPHEUS 1.2.0 est la release stable publiée. R3 a produit et vérifié les distributions Windows/Linux avec runtime Java embarqué, SHA-256 et manifests liés au SHA Git exact.

## Artefacts 1.2.0

```text
Windows setup
  dist/MORPHEUS-1.2.0-windows-x64-setup.exe
  dist/MORPHEUS-1.2.0-windows-x64-setup.exe.sha256

Windows portable
  dist/morpheus-1.2.0-windows-x64.zip
  dist/morpheus-1.2.0-windows-x64.zip.sha256
  dist/morpheus-1.2.0-windows-x64-release-manifest.json

Linux portable
  dist/morpheus-1.2.0-linux-x64.tar.gz
  dist/morpheus-1.2.0-linux-x64.tar.gz.sha256
  dist/morpheus-1.2.0-linux-x64-release-manifest.json
```

La preuve R3 confirme la parité publiée **8/8 PASS**.

## Contenu packagé

Les distributions embarquent :

```text
CLI MORPHEUS
serveur MCP STDIO
API HTTP locale
façade remote HTTPS opt-in
services application/domain
Provider SDK + providers
store SQLite + migrations V001→V015
Jackson
runtime Java minimal
integration/configure-mcp-clients.ps1
integration/configure-mcp-clients-setup.ps1
integration/README.md
```

Elles n’embarquent ni l’implémentation MINOS, ni l’implémentation NEXUS, ni JARVIS.

## Windows setup

Le setup est défini par :

```text
distribution/windows/MORPHEUS.iss
```

Contrat :

```text
per-user
PrivilegesRequired=lowest
%LOCALAPPDATA%\Programs\MORPHEUS
AppId stable
PATH utilisateur opt-in
cinq intégrations MCP opt-in
uninstall conservateur
état persistant conservé par défaut
```

## Build Windows

Portable :

```powershell
.\distribution\build-portable.ps1 -Version 1.2.0
```

Setup :

```powershell
.\distribution\build-installer.ps1 -Version 1.2.0
```

Release depuis le tag exact déjà publié :

```powershell
.\distribution\build-release.ps1 -Version 1.2.0 -ExpectedTag v1.2.0
```

Le tag `v1.2.0` est immuable et ne doit pas être déplacé par D2.

## Build Linux

Portable :

```bash
bash distribution/build-portable.sh 1.2.0
```

Release exacte :

```bash
bash distribution/build-release.sh 1.2.0 v1.2.0
```

## D2 — hardening post-release

D2 ne republie pas 1.2.0. Il requalifie le HEAD de développement après mise à jour de dépendances et qualité :

```text
Jackson       3.1.5 LTS
sqlite-jdbc   3.53.2.0
```

Les validateurs D2 reconstruisent un portable sur chaque plateforme et vérifient que `product-info.version == 1.2.0`.

Windows :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.0 -BaseRef origin/develop
```

Linux/WSL :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.0
```

D2 est **local-only** et interdit tout delta `.github/workflows/**`.

## Runtime layout

Overrides MORPHEUS :

```text
--data-dir PATH
--config-dir PATH
--db PATH
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_LOGS_DIR
MORPHEUS_BACKUPS_DIR
MORPHEUS_DB
```

Linux respecte aussi `XDG_DATA_HOME`, `XDG_CONFIG_HOME` et `XDG_STATE_HOME`.

## Documentation

- [Installation 1.2](../docs/user/INSTALLATION.md)
- [Upgrade 1.2](../docs/user/UPGRADE_1_2.md)
- [Clients MCP](../docs/user/MCP_CLIENTS.md)
- [Build et tests](../docs/developer/BUILD_AND_TEST.md)
- [Validation R3](../docs/validation/VALIDATION_R3.md)
- [D2](../docs/roadmap/D2_EXECUTION.md)
