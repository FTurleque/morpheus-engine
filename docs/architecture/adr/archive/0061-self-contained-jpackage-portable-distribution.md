# ADR-0061 — Distribution portable autonome via JAR ombré et jpackage app-image

> **Archivée.** Cette décision est consolidée dans [ADR-0134](../GEN-0134-distribution-native-autonome.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0134**
- Date : 2026-07-24

## Context

ADR-0027 impose une distribution native : l'utilisateur final ne doit ni installer Docker ni configurer manuellement
un JDK pour lancer MORPHEUS. Le produit doit être livré sur Windows et sur Linux, sans imposer un installateur natif
comme seule forme de livraison.

## Decision

**JAR autonome.** `morpheus-cli` produit un JAR ombré `morpheus-cli-<version>-all.jar` par Apache Maven Shade Plugin
(classifier `all`). Les fichiers `META-INF/services` sont fusionnés (`ServicesResourceTransformer`) afin de préserver
notamment la découverte JDBC de SQLite. La classe principale est `com.morpheus.cli.MorpheusMain`. Les plugins Maven
sont versionnés dans le `<pluginManagement>` du POM racine.

**App-image portable.** L'artefact principal est une app-image `jpackage --type app-image` contenant :

```text
launcher natif
JAR applicatif
runtime Java embarqué, avec son propre bin/java
```

Le runtime est produit par `jlink` à partir des options que passent `distribution/build-portable.ps1` et
`distribution/build-portable.sh` : les options par défaut de `jpackage` moins `--strip-native-commands`
(`--jlink-options "--strip-debug --no-man-pages --no-header-files"`). Le runtime embarqué garde ainsi
`bin/java`, exactement le chemin que `java.home` désigne, dont la sonde isolée de plugin provider a besoin
(ADR-0129) ; chaque script vérifie sa présence avant de poursuivre.

Chaque build exécute le launcher packagé avec `--version` et `--json version` avant archivage.

**Archives.**

```text
Windows x64 -> morpheus-<version>-windows-x64.zip
Linux x64   -> morpheus-<version>-linux-x64.tar.gz
```

L'archive portable est la distribution de référence : elle ne dépend d'aucun outil d'installation présent chez
l'utilisateur et ne requiert aucun JDK séparé.

**Installeur Windows.** L'installeur Windows est construit avec Inno Setup par `distribution/build-installer.ps1`, à
partir de la même app-image : il hérite du même runtime, sans invocation `jlink` propre. Le compilateur Inno Setup est
résolu et vérifié par `distribution/ensure-inno-setup.ps1`. Cet outil est une dépendance du **build de
l'installeur**, jamais de l'exécution par l'utilisateur final. Le comportement d'installation, de mise à jour et de
désinstallation relève d'ADR-0088 ; le launcher unique et ses modes, d'ADR-0127.

**Linux.** La cible Linux est le `tar.gz` autonome. Aucun paquet `deb` ou `rpm` n'est construit : ils ajouteraient
des prérequis de packaging propres aux distributions sans changer le moteur.

**Données et configuration hors installation.**

```text
installation != data directory
installation != config directory
```

Cette séparation permet de remplacer l'application lors d'une mise à jour sans écraser SQLite ni la configuration, et
rend la désinstallation du binaire distincte de la suppression des données.

**Fins de ligne.** `.gitattributes` fige les fins de ligne des scripts multi-plateformes : `mvnw` et `*.sh` en LF,
`mvnw.cmd` et `*.ps1` en CRLF.

## Do's and Don'ts

### Do

- Construire la distribution comme une app-image `jpackage --type app-image` embarquant son runtime.
- Passer à `jpackage` des options `jlink` qui n'incluent pas `--strip-native-commands`, et vérifier la présence du
  `java` embarqué.
- Exécuter `--version` et `--json version` sur le launcher packagé avant d'archiver.
- Construire l'installeur Windows à partir de l'app-image validée.
- Garder données et configuration hors du répertoire d'installation.

### Don't

- Ne pas exiger de l'utilisateur final un JDK, Docker ou un outil d'installation pour exécuter l'archive portable.
- Ne pas retirer `bin/java` du runtime embarqué.
- Ne pas faire dépendre l'archive portable de l'outil de construction de l'installeur.
- Ne pas déclarer de version de plugin Maven dans le POM de `morpheus-cli`.

## Consequences

### Positives

- Une archive autonome par plateforme, exécutable sans JDK installé.
- Un runtime embarqué complet, utilisable par la sonde isolée de plugin.
- Mise à jour et désinstallation sans perte des données.

### Négatives

- Une archive par système d'exploitation.
- La construction de l'installeur Windows exige un outil supplémentaire (Inno Setup) sur la machine de packaging.

### Risques

- Une option `jlink` ajoutée aux scripts pourrait retirer `bin/java` ; la vérification de présence dans les scripts
  fait échouer le build dans ce cas.

## Compliance and Enforcement

- `distribution/build-portable.ps1` et `distribution/build-portable.sh` : smoke `--version` / `--json version` du
  launcher packagé, et refus d'un runtime embarqué sans lanceur Java.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m20/ProductReleaseContractTest.java` : contrat
  du build de release et de l'installeur (installation par utilisateur, désinstallation qui préserve l'état
  persistant, sommes de contrôle, bootstrap vérifié de l'outil).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m20/InnoSetupToolchainTrustArchitectureTest.java` :
  l'installeur ne contourne pas la résolution épinglée et signée du compilateur.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m20/ReleaseQualificationContractTest.java` :
  attributions tierces écrites par les deux scripts portables.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/D2RepositoryHardeningArchitectureTest.java`
  (`noModulePomStatesAVersionTheRootPomShouldDecide`) : aucune version de plugin dans un POM de module.
- Revue : le type `app-image` et l'absence de `--strip-native-commands` dans les scripts (aucun test ne les lit).

## References

- [ADR-0016](0016-java-21-production-baseline.md) — baseline Java.
- [ADR-0017](0017-maven-build-foundation.md) — build Maven.
- [ADR-0027](0027-native-distribution-without-container-image.md) — distribution native.
- [ADR-0059](../GEN-0059-stable-local-cli-contract.md) — contrat CLI local.
- [ADR-0088](../GEN-0088-product-release-installation-and-persistent-data-separation.md) — release, installation et
  séparation des données.
- [ADR-0127](../GEN-0127-un-launcher-natif-unique.md) — un launcher natif unique.
- [ADR-0129](../SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) — plugins providers hors processus,
  runtime embarqué avec `bin/java`.
- [VALIDATION_M9](../../../project/validation/VALIDATION_M9.md)
