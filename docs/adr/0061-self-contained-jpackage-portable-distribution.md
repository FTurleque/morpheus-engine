# ADR-0061 — Distribution portable autonome via JAR ombré et jpackage app-image

- Statut : **Acceptée — M9**
- Date : 24 juillet 2026
- Dépend de : ADR-0016, ADR-0017, ADR-0027, ADR-0059
- Portée : M9 — packaging et runtime local

## Contexte

ADR-0027 impose une expérience native-first : l'utilisateur final ne doit pas devoir installer Docker ou configurer manuellement un JDK pour lancer MORPHEUS.

M9 doit également prouver une voie raisonnable Windows et Linux et évaluer `jlink` / `jpackage` sans imposer un installateur natif comme seule forme de livraison.

## Décision

### JAR autonome

`morpheus-cli` produit :

```text
morpheus-cli-<version>-all.jar
```

via Apache Maven Shade Plugin. Les services `META-INF/services` sont fusionnés afin de préserver notamment la découverte JDBC SQLite.

Main class officiel :

```text
com.morpheus.cli.MorpheusMain
```

### App-image portable

Artefact principal M9 :

```text
jpackage --type app-image
```

Pour une application non modulaire, `jpackage` génère le runtime Java associé via `jlink` lorsqu'aucun `--runtime-image` n'est fourni.

L'application autonome contient :

```text
launcher natif
application JAR
runtime Java embarqué
```

Chaque build exécute le launcher packagé avec `--version` et `--json version` avant archivage.

### Archives

```text
Windows x64 -> morpheus-<version>-windows-x64.zip
Linux x64   -> morpheus-<version>-linux-x64.tar.gz
```

L'archive portable est la distribution de référence car elle ne dépend pas d'un outil d'installation présent chez l'utilisateur.

## Installateur Windows

Un script optionnel construit un EXE via :

```text
jpackage --type exe --app-image ...
```

Sur JDK 21, la création d'un installateur Windows nécessite WiX sur la machine de packaging. Cette dépendance concerne le **build de l'installateur**, pas l'exécution par l'utilisateur final.

L'absence de WiX ne bloque pas l'archive portable M9. Le script détecte cette absence avant d'appeler `jpackage` et termine par un skip explicite.

## Linux

La cible officielle M9 est le `tar.gz` autonome. `deb` / `rpm` sont volontairement optionnels : ils ajoutent des prérequis de packaging propres aux distributions sans changer le moteur MORPHEUS.

## Data/config hors installation

```text
installation != data directory
installation != config directory
```

Cette séparation permet de remplacer l'application lors d'un upgrade sans écraser intentionnellement SQLite/config et rend l'uninstall du binaire distinct de la suppression des données.

## Plugins Maven

```text
maven-jar-plugin    3.5.0
maven-shade-plugin  3.6.2
```

## Preuve Windows — 24 juillet 2026

```text
clean test                      298/298 PASS
Architecture Tests             149/149 PASS
uber-JAR                        BUILD SUCCESS
jpackage app-image             PASS
morpheus.exe --version         PASS
morpheus.exe --json version    PASS
Windows ZIP                    PASS
runtime Java embarqué          PASS
WiX absent                     installateur EXE optionnel SKIPPED proprement
```

Artefact :

```text
dist/morpheus-0.1.0-windows-x64.zip
```

## Preuve Linux — 24 juillet 2026

Environnement de packaging : WSL/Ubuntu avec OpenJDK/Javac/jpackage 21.0.11, sur filesystem Linux local.

```text
clean test                      298/298 PASS
Architecture Tests             149/149 PASS
uber-JAR                        BUILD SUCCESS
jpackage app-image             PASS
morpheus --version             PASS
morpheus --json version        PASS
Linux tar.gz                   PASS
runtime Java embarqué          PASS
```

Artefact :

```text
dist/morpheus-0.1.0-linux-x64.tar.gz
```

Sorties smoke communes :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

Le script Linux confirme explicitement que l'archive contient son runtime Java et que l'utilisateur final n'a pas besoin d'un JDK séparé.

Les fins de ligne des scripts cross-platform sont figées via `.gitattributes` (`mvnw`/`*.sh` en LF, scripts Windows en CRLF).

Validation complète : [`../VALIDATION_M9.md`](../VALIDATION_M9.md).

## Critères d'acceptation

Tous les critères sont satisfaits :

1. `clean test` vert sur Windows et Linux ;
2. JAR `-all.jar` exécutable ;
3. Windows app-image créé ;
4. launcher Windows smoke PASS ;
5. ZIP Windows produit ;
6. Linux app-image créé ;
7. launcher Linux smoke PASS ;
8. tar.gz Linux produit ;
9. aucune installation JDK requise pour exécuter les archives ;
10. installateur Windows évalué/documenté ;
11. upgrade/uninstall et data/config documentés.

**Décision : ADR-0061 acceptée.**