# MORPHEUS — Distribution et release 1.0

M20 transforme le packaging portable historique en contrat de release produit : **setup Windows + archives portables Windows/Linux + runtime Java embarqué + SHA-256 + manifest lié au SHA Git**.

La preuve autoritative reste locale via les validateurs M20 ; GitHub Actions n’est pas le gate du jalon.

## Artefacts 1.0

```text
Windows setup
  dist/MORPHEUS-1.0.0-windows-x64-setup.exe
  dist/MORPHEUS-1.0.0-windows-x64-setup.exe.sha256

Windows portable
  dist/morpheus-1.0.0-windows-x64.zip
  dist/morpheus-1.0.0-windows-x64.zip.sha256

Linux portable
  dist/morpheus-1.0.0-linux-x64.tar.gz
  dist/morpheus-1.0.0-linux-x64.tar.gz.sha256
```

Les distributions embarquent leur runtime Java, dont `jdk.httpserver` et `java.sql`. Aucun JDK utilisateur n’est requis pour exécuter MORPHEUS.

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
PATH utilisateur optionnel et décoché par défaut
uninstall programme uniquement
```

L’état persistant vit hors du répertoire programme :

```text
%LOCALAPPDATA%\MORPHEUS\data
%LOCALAPPDATA%\MORPHEUS\config
%LOCALAPPDATA%\MORPHEUS\logs
%LOCALAPPDATA%\MORPHEUS\backups
```

Le setup ne contient aucune règle de suppression de ce state root lors de l’uninstall.

## Build Windows

Prérequis de build :

```text
JDK avec jpackage
Maven Wrapper du dépôt
PowerShell
```

Le setup MORPHEUS est compilé avec Inno Setup. Si `ISCC.exe` n’est ni installé ni fourni par `MORPHEUS_ISCC`, `build-installer.ps1` exécute automatiquement `distribution/ensure-inno-setup.ps1` :

```text
version épinglée : Inno Setup 7.0.2 x64
source            : release GitHub immuable officielle JRSoftware
vérification      : signature Authenticode valide
éditeur attendu   : Pyrsys B.V.
mode              : portable / current-user
emplacement       : validation-output/m20/tooling
```

Ce bootstrap ne dépend pas de `winget`, ne requiert pas de droits administrateur et n’installe pas de dépendance système persistante. Inno Setup reste une dépendance **de build uniquement** ; les utilisateurs de MORPHEUS n’en ont jamais besoin.

Un compilateur déjà présent reste prioritaire. Un chemin explicite peut être fourni via :

```text
MORPHEUS_ISCC=C:\...\ISCC.exe
```

### Archive portable seule

```powershell
.\distribution\build-portable.ps1 -Version 1.0.0
```

### Setup Windows

```powershell
.\distribution\build-installer.ps1 -Version 1.0.0
```

### Release Windows depuis un tag exact

```powershell
.\distribution\build-release.ps1 -Version 1.0.0 -ExpectedTag v1.0.0
```

`build-release.ps1` :

1. refuse un workspace sale ;
2. exige que le tag attendu pointe exactement sur `HEAD` ;
3. construit le ZIP portable ;
4. construit le setup ;
5. écrit et revérifie les SHA-256 ;
6. produit `morpheus-1.0.0-windows-x64-release-manifest.json` avec version, tag, SHA Git, tailles et hashes.

## Build Linux

```bash
bash distribution/build-portable.sh 1.0.0
```

Release depuis le tag exact :

```bash
bash distribution/build-release.sh 1.0.0 v1.0.0
```

La chaîne Linux produit le tar.gz, son `.sha256`, vérifie le checksum et écrit le manifest Linux associé au SHA Git exact.

## Contenu packagé

Le JAR ombré et l’app-image embarquent :

```text
CLI MORPHEUS
serveur MCP
API HTTP
services application/domain
provider OpenSpec
provider Structured Markdown
adapters MINOS/NEXUS optionnels
store SQLite + migrations
Jackson
runtime Java minimal
```

Ils n’embarquent **ni l’implémentation MINOS, ni l’implémentation NEXUS, ni JARVIS**.

## Intégrations optionnelles

MINOS :

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

NEXUS :

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

Sans configuration, les deux intégrations restent `DISABLED`. JARVIS n’est jamais embarqué.

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

Linux respecte également :

```text
XDG_DATA_HOME
XDG_CONFIG_HOME
XDG_STATE_HOME
```

`morpheus paths` affiche le layout effectivement résolu.

## Gate M20

Windows :

```powershell
.\validate-m20.cmd
```

Linux :

```bash
bash scripts/validate-m20.sh
```

Les résultats Windows et Linux sont enregistrés séparément. Un environnement non exécuté n’est jamais déclaré PASS.

## Documentation

- [Installation / upgrade / uninstall 1.0](../docs/user/INSTALLATION.md)
- [Démarrage rapide](../docs/user/QUICKSTART.md)
- [Configuration des intégrations](../docs/user/INTEGRATIONS.md)
- [Build et tests développeur](../docs/developer/BUILD_AND_TEST.md)
- [Validation M20](../docs/validation/VALIDATION_M20.md)
