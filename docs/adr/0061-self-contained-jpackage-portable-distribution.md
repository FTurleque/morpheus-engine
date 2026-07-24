# ADR-0061 — Distribution portable autonome via JAR ombré et jpackage app-image

- Statut : **Proposée — M9, preuves Windows/Linux pending**
- Date : 24 juillet 2026
- Dépend de : ADR-0016, ADR-0017, ADR-0027, ADR-0059
- Portée : M9 — packaging et runtime local

## Contexte

ADR-0027 impose une expérience native-first : l'utilisateur final ne doit pas devoir installer Docker ou configurer manuellement un JDK pour lancer MORPHEUS.

M9 doit également prouver une voie raisonnable Windows et Linux et évaluer `jlink` / `jpackage` sans imposer un installateur natif comme seule forme de livraison.

## Décision

### JAR autonome

`morpheus-cli` produit un artefact :

```text
morpheus-cli-<version>-all.jar
```

via Apache Maven Shade Plugin. Les services `META-INF/services` sont fusionnés afin de préserver notamment la découverte JDBC SQLite.

Le main class officiel est :

```text
com.morpheus.cli.MorpheusMain
```

### App-image portable

Artefact principal M9 :

```text
jpackage --type app-image
```

Pour une application non modulaire, `jpackage` génère le runtime Java associé via `jlink` lorsqu'aucun `--runtime-image` n'est fourni.

M9 utilise cette propriété pour produire une application autonome contenant :

```text
launcher natif
application JAR
runtime Java embarqué
```

Chaque build exécute ensuite le launcher packagé avec `--version` avant archivage.

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

L'absence de WiX ne bloque pas l'archive portable M9.

## Linux

La cible officielle M9 est le `tar.gz` autonome. `deb` / `rpm` sont volontairement optionnels : ils ajoutent des prérequis de packaging propres aux distributions sans changer le moteur MORPHEUS.

## Data/config hors installation

Le binaire et le runtime ne possèdent pas la base utilisateur. Par défaut :

```text
installation != data directory
installation != config directory
```

Cette séparation permet de remplacer l'application lors d'un upgrade sans écraser intentionnellement SQLite/config et rend l'uninstall du binaire distinct de la suppression des données.

## Plugins Maven

M9 fixe explicitement :

```text
maven-jar-plugin   3.5.0
maven-shade-plugin 3.6.2
```

Ces versions sont compatibles avec la baseline Maven/JDK du projet et sont vérifiées dans la documentation officielle Apache Maven au moment de M9.

## Critères d'acceptation

ADR acceptée après preuve :

1. `clean test` vert ;
2. JAR `-all.jar` exécutable ;
3. Windows app-image créé ;
4. launcher Windows `--version` PASS ;
5. ZIP Windows produit ;
6. Linux app-image créé ;
7. launcher Linux `--version` PASS ;
8. tar.gz Linux produit ;
9. aucune installation JDK requise pour exécuter les archives ;
10. installateur Windows évalué/documenté ;
11. upgrade/uninstall et data/config documentés.
