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
provider OpenSpec
provider Structured Markdown
persistance SQLite V012
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

Prérequis de build : JDK 21+ avec `jpackage`.

```powershell
.\distribution\build-portable.ps1
```

Le script M18 :

1. construit MORPHEUS ;
2. vérifie le contenu de l'uber-JAR ;
3. vérifie les classes MCP/API/M14→M18 ;
4. vérifie le provider Structured Markdown et SQLite V012 ;
5. produit un `jpackage --type app-image` ;
6. smoke-teste le launcher et les intégrations optionnelles désactivées ;
7. smoke-teste les surfaces M14/M17/M18 ;
8. smoke-teste l'API packagée ;
9. crée le ZIP portable.

Workdir M18 :

```text
dist/.m18-windows
```

Preuve M18 réelle :

```text
MCP/API/MINOS/NEXUS/M14-M18 classes + provider Markdown + V012 embedded: PASS
Packaged standalone optional-engines + M14 read-only + M17 controlled-write + M18 composition smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive validée :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,919,431 bytes
```

Code réellement gated : `7e8caacff567f51354fcb88bd7505a6d135071c0`.  
Merge M18 ultérieur : `30f11ac3ffc522bcc0c71e31216a3fb70f0631d7`.

### Installateur Windows optionnel

```powershell
.\distribution\build-windows-installer.ps1
```

L'installateur et le chantier de release/installation PROD complet restent dans la cible M20. Le ZIP portable reste l'artefact validé actuel.

## Linux

```bash
export JAVA_HOME=/path/to/jdk
chmod +x mvnw distribution/build-portable.sh
./distribution/build-portable.sh
```

Artefact :

```text
dist/morpheus-<version>-linux-x64.tar.gz
```

Le script vérifie les classes attendues, `jdk.httpserver`, le launcher et l'absence d'implémentations MINOS/NEXUS/JARVIS embarquées.

**La preuve Windows M18 ne constitue pas une preuve Linux M18.** Les validations M19 devront enregistrer séparément la plateforme réellement exécutée.

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
M15 371/371 Windows + Architecture 157/157 + packaging/smokes PASS
M16 393/393 Windows + Architecture 161/161 + packaging/smokes PASS
M17 410/410 Windows + Architecture 167/167 + controlled-write packaging/smokes PASS
M18 418/418 Windows + Architecture 170/170 + composition packaging/smokes/API health PASS
```

## Documentation

- [Démarrage rapide utilisateur](../docs/user/QUICKSTART.md)
- [Configuration des intégrations](../docs/user/INTEGRATIONS.md)
- [Build et tests développeur](../docs/developer/BUILD_AND_TEST.md)
- [Architecture](../docs/developer/ARCHITECTURE.md)
- [Validation M18](../docs/validation/VALIDATION_M18.md)
