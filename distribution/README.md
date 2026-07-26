# MORPHEUS — Distribution locale

Stratégie actuelle : **native-first**, archive portable autonome comme artefact principal. La transformation en installation produit stable est planifiée pour M20.

## Artefacts

```text
Windows x64 -> dist/morpheus-<version>-windows-x64.zip
Linux x64   -> dist/morpheus-<version>-linux-x64.tar.gz
```

Les archives embarquent leur runtime Java. Aucun JDK séparé n'est requis pour exécuter MORPHEUS depuis une distribution portable.

## Contenu M18

L'uber-JAR et l'app-image embarquent :

```text
CLI MORPHEUS
serveur MCP
API HTTP
services applicatifs
provider OpenSpec
provider Structured Markdown
adapters MINOS/NEXUS optionnels
SQLite JDBC + migrations jusqu'à V012
Jackson
```

Ils n'embarquent **ni MINOS, ni NEXUS, ni JARVIS**.

Le build vérifie l'absence d'implémentations externes embarquées.

## Windows

Prérequis de build : JDK compatible avec `jpackage` et baseline Java 21.

```powershell
.\distribution\build-portable.ps1
```

Le packaging M18 vérifie notamment :

```text
classes CLI / MCP / API
provider Structured Markdown
SQLite V012
MINOS/NEXUS optionnels
M14 orchestration read-only
M17 controlled lifecycle write
M18 composition surfaces
API health
portable archive
```

### Gate M18 réellement validé

```text
MCP/API/MINOS/NEXUS/M14-M18 classes + provider Markdown + V012 embedded: PASS
Packaged standalone optional-engines + M14 read-only + M17 controlled-write + M18 composition surface smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive validée :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,919,431 bytes
```

Code réellement testé : `7e8caacff567f51354fcb88bd7505a6d135071c0`.

## Linux

```bash
chmod +x mvnw distribution/build-portable.sh
./distribution/build-portable.sh
```

Artefact :

```text
dist/morpheus-<version>-linux-x64.tar.gz
```

Une preuve de packaging Windows ne constitue jamais une preuve Linux. Les qualifications M19 devront enregistrer séparément les résultats réellement exécutés sur chaque OS.

## Configuration runtime

```text
--data-dir PATH
--config-dir PATH
--db PATH
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Utiliser `morpheus paths` pour afficher le layout effectivement résolu.

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

JARVIS n'est jamais embarqué. Il consomme les contrats HTTP MORPHEUS et reste propriétaire de l'orchestration.

## Surfaces M18 packagées

```text
CLI  composition sync/status/conflicts
MCP  get_composition_status
MCP  list_composition_conflicts
HTTP GET /api/v1/projects/{projectId}/composition
HTTP GET /api/v1/projects/{projectId}/composition/conflicts
OpenAPI 1.7.0
SQLite V012
```

## Gates historiques récents

```text
M9  298/298 Windows + Linux
M10 307/307 Windows + MCP packaging
M11 314/314 Windows + packaged API health
M12 331/331 Windows + MINOS optional packaging
M13 346/346 Windows + MINOS/NEXUS optional packaging
M14 357/357 Windows + Architecture 160/160 + orchestration packaging PASS
M15 371/371 Windows + Architecture 157/157 + packaging/smokes PASS
M16 393/393 Windows + Architecture 161/161 + packaging/smokes PASS
M17 410/410 Windows + Architecture 167/167 + packaging/smokes PASS
M18 418/418 Windows + Architecture 170/170 + packaging/smokes PASS
```

## Validation mono-commande M18

```powershell
.\validate-m18.cmd
```

Preuve : [VALIDATION_M18.md](../docs/validation/VALIDATION_M18.md).

## Documentation

- [Démarrage rapide utilisateur](../docs/user/QUICKSTART.md)
- [Configuration des intégrations](../docs/user/INTEGRATIONS.md)
- [Build et tests développeur](../docs/developer/BUILD_AND_TEST.md)
- [Architecture](../docs/developer/ARCHITECTURE.md)
- [Validation M18](../docs/validation/VALIDATION_M18.md)