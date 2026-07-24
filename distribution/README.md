# MORPHEUS — Distribution locale

Stratégie : **native-first**, archive portable autonome comme artefact principal.

## Artefacts

```text
Windows x64 -> dist/morpheus-<version>-windows-x64.zip
Linux x64   -> dist/morpheus-<version>-linux-x64.tar.gz
```

Les archives embarquent leur runtime Java. Aucun JDK séparé n'est requis pour exécuter MORPHEUS depuis une distribution portable.

## Contenu

L'uber-JAR et l'app-image embarquent :

```text
CLI MORPHEUS
serveur MCP
API HTTP
services applicatifs
adapters MINOS/NEXUS optionnels
Jackson
SQLite JDBC
```

Ils n'embarquent **ni MINOS, ni NEXUS, ni JARVIS**.

Le build échoue si le shaded JAR contient :

```text
com/minos/*
com/nexus/*
com/jarvis/*
```

## Windows

Prérequis de build : JDK 21 avec `jpackage`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\distribution\build-portable.ps1
```

Le script :

1. construit et teste MORPHEUS ;
2. vérifie le contenu de l'uber-JAR ;
3. produit un `jpackage --type app-image` ;
4. smoke-teste le launcher ;
5. vérifie MINOS/NEXUS désactivés sans configuration ;
6. vérifie `change-orchestration` ;
7. smoke-teste l'API packagée ;
8. crée le ZIP portable.

Workdir courant : `dist/.m14-windows`.

Preuve M14 obtenue :

```text
MCP/API/MINOS/NEXUS/M14 orchestration packaging proof: PASS
MORPHEUS 0.1.0-SNAPSHOT
MINOS status -> DISABLED sans configuration
NEXUS status -> DISABLED sans configuration
Packaged standalone optional-engines + M14 orchestration smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive M14 validée :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,702,405 bytes
```

### Installateur Windows optionnel

```powershell
.\distribution\build-windows-installer.ps1
```

Il réutilise l'app-image construite par le packaging portable. Les prérequis éventuels d'un installateur natif restent distincts de l'archive ZIP portable.

## Linux

```bash
export JAVA_HOME=/path/to/jdk-21
chmod +x mvnw distribution/build-portable.sh
./distribution/build-portable.sh
```

Artefact :

```text
dist/morpheus-<version>-linux-x64.tar.gz
```

Le script vérifie les classes attendues, `jdk.httpserver`, le launcher et l'absence d'implémentations MINOS/NEXUS/JARVIS embarquées.

## Configuration runtime

Overrides globaux :

```text
--data-dir PATH
--config-dir PATH
--db PATH
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Defaults :

```text
Windows  %LOCALAPPDATA%\Morpheus / %APPDATA%\Morpheus
Linux    standards XDG data/config
```

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

Smokes cross-repo complémentaires :

```text
distribution/test-minos-compatibility.ps1
distribution/test-nexus-compatibility.ps1
```

JARVIS n'est jamais embarqué. Il consomme le contrat HTTP local MORPHEUS.

## Gates historiques

```text
M9  298/298 Windows + Linux
M10 307/307 Windows + MCP packaging
M11 314/314 Windows + packaged API health
M12 331/331 Windows + MINOS optional packaging
M13 346/346 Windows + MINOS/NEXUS optional packaging
M14 357/357 Windows + Architecture 160/160 + orchestration packaging PASS
```

## Documentation

- [Démarrage rapide utilisateur](../docs/user/QUICKSTART.md)
- [Configuration des intégrations](../docs/user/INTEGRATIONS.md)
- [Build et tests développeur](../docs/developer/BUILD_AND_TEST.md)
- [Architecture](../docs/developer/ARCHITECTURE.md)
- [Validation M14](../docs/validation/VALIDATION_M14.md)