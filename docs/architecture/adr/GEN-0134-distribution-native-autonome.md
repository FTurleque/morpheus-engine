---
id: GEN-0134
title: "Distribution native autonome, sans image de conteneur"
domain: general
rules: true
files: ["**/*compose*.yaml", "**/*compose*.yml", "**/Dockerfile*", ".github/workflows/*.yml", "distribution/**", "distribution/**/*"]
---

# Distribution native autonome, sans image de conteneur

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0027, ADR-0061

## Context

MORPHEUS est local-first. Ses contraintes de distribution sont :

```text
local-first
Windows prioritaire, Linux supporté
aucun cloud obligatoire
CLI locale réaliste
serveur MCP STDIO et API HTTP
SQLite embarqué
MORPHEUS autonome de MINOS, NEXUS et JARVIS
```

Un utilisateur doit pouvoir lancer `morpheus <command>` sans installer Docker Desktop, sans installer un JDK à la main
et sans démarrer plusieurs services externes. Le produit est livré sur Windows et sur Linux, sans imposer un
installateur natif comme seule forme de livraison.

Options écartées :

- **Distribution conteneurisée en mode secondaire**, pour les usages headless, MCP réseau, API, CI/CD et serveur
  partagé, avec une composition multi-service des moteurs : le serveur MCP est servi par STDIO, l'API locale et le
  mode serveur d'équipe sont servis par la distribution native, et chaque moteur de l'écosystème reste installable et
  exécutable indépendamment.
- **Paquets Linux `deb` ou `rpm`** : ils ajouteraient des prérequis de packaging propres aux distributions sans
  changer le moteur.

## Decision

### 1. Native seulement

La distribution de MORPHEUS est **native seulement** : une archive portable autonome par plateforme, avec runtime
Java embarqué, un installeur Windows, et un launcher natif unique qui route la CLI, le serveur MCP et l'API
(ADR-0127).

MORPHEUS ne publie aucune image Docker et ne requiert Docker pour aucun usage : ni la CLI, ni le serveur MCP, ni
l'API, ni l'intégration des clients MCP, ni le build local de développement. `docker compose` n'est pas une
dépendance de MORPHEUS ; aucune composition multi-service n'est fournie.

Le même cœur (`domain`, `application`, stores, providers) sert tous les modes d'exécution, et aucune logique métier ne
dépend du packaging. Les données persistantes et la configuration sont séparées de l'installation, selon ADR-0088.

```text
native CLI != Docker required
packaging != business architecture
same core in every execution mode
persistent data separated from the installation
no cloud dependency
MORPHEUS remains standalone
```

### 2. JAR autonome

`morpheus-cli` produit un JAR ombré `morpheus-cli-<version>-all.jar` par Apache Maven Shade Plugin (classifier
`all`). Les fichiers `META-INF/services` sont fusionnés (`ServicesResourceTransformer`) afin de préserver notamment
la découverte JDBC de SQLite. La classe principale est `com.morpheus.cli.MorpheusMain`. Les plugins Maven sont
versionnés dans le `<pluginManagement>` du POM racine.

### 3. App-image portable

L'artefact principal est une app-image `jpackage --type app-image` contenant :

```text
launcher natif
JAR applicatif
runtime Java embarqué, avec son propre bin/java
```

Le runtime est produit par `jlink` à partir des options que passent `distribution/build-portable.ps1` et
`distribution/build-portable.sh`. Il garde `bin/java` ; les options `jlink` qui le garantissent et la vérification de
sa présence sont fixées par ADR-0129.

Chaque build exécute le launcher packagé avec `--version` et `--json version` avant archivage.

### 4. Archives

```text
Windows x64 -> morpheus-<version>-windows-x64.zip
Linux x64   -> morpheus-<version>-linux-x64.tar.gz
```

L'archive portable est la distribution de référence : elle ne dépend d'aucun outil d'installation présent chez
l'utilisateur et ne requiert aucun JDK séparé. La cible Linux est le `tar.gz` autonome ; aucun paquet `deb` ou `rpm`
n'est construit.

### 5. Installeur Windows

L'installeur Windows est construit avec Inno Setup par `distribution/build-installer.ps1`, à partir de la même
app-image : il hérite du même runtime, sans invocation `jlink` propre. Le compilateur Inno Setup est résolu et vérifié
par `distribution/ensure-inno-setup.ps1`. Cet outil est une dépendance du **build de l'installeur**, jamais de
l'exécution par l'utilisateur final. Le comportement d'installation, de mise à jour et de désinstallation relève
d'ADR-0088.

### 6. Fins de ligne

`.gitattributes` fige les fins de ligne des scripts multi-plateformes : `mvnw` et `*.sh` en LF, `mvnw.cmd` et `*.ps1`
en CRLF.

## Do's and Don'ts

### Do

- Livrer MORPHEUS comme distribution native : archive portable avec runtime embarqué, et installeur Windows.
- Construire la distribution comme une app-image `jpackage --type app-image` embarquant son runtime.
- Exécuter `--version` et `--json version` sur le launcher packagé avant d'archiver.
- Construire l'installeur Windows à partir de l'app-image validée.
- Garder le même cœur pour la CLI, le serveur MCP et l'API.
- Garder chaque moteur de l'écosystème installable et exécutable indépendamment.

### Don't

- Ne pas publier d'image Docker de MORPHEUS.
- Ne pas exiger Docker, Docker Desktop ni `docker compose` pour installer, lancer, intégrer ou construire MORPHEUS.
- Ne pas mentionner `docker` dans l'installeur ni dans le gestionnaire d'intégration des clients MCP.
- Ne pas faire dépendre une logique métier du mode de packaging.
- Ne pas exiger de l'utilisateur final un JDK ou un outil d'installation pour exécuter l'archive portable.
- Ne pas retirer `bin/java` du runtime embarqué (ADR-0129).
- Ne pas faire dépendre l'archive portable de l'outil de construction de l'installeur.
- Ne pas déclarer de version de plugin Maven dans le POM de `morpheus-cli`.

## Consequences

### Positives

- L'expérience locale ne dépend d'aucun moteur de conteneurs ni d'un JDK installé.
- Une seule chaîne de packaging à qualifier et à publier ; une archive autonome par plateforme.
- Un runtime embarqué complet, utilisable par la sonde isolée de plugin.

### Négatives

- Un déploiement serveur s'installe comme une application native ; aucune image n'est fournie pour un orchestrateur.
- Une composition de MORPHEUS avec MINOS, NEXUS et JARVIS se fait par configuration des pairs, pas par un fichier de
  composition.
- Une archive par système d'exploitation.
- La construction de l'installeur Windows exige un outil supplémentaire (Inno Setup) sur la machine de packaging.

### Risques

- Un besoin de déploiement conteneurisé exigerait une nouvelle décision, avec son packaging, sa qualification et sa
  politique de données.

## Compliance and Enforcement

- Règle Archgate `GEN-0134-distribution-native-autonome.rules.ts` : `no-container-image-definition`,
  `no-container-build-in-pipelines`.
- Règle Archgate d'ADR-0129 `embedded-runtime-keeps-native-commands` : aucun script de l'image portable ne passe
  `--strip-native-commands`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m28/McpClientIntegrationArchitectureTest.java` :
  le gestionnaire d'intégration des clients MCP ne contient pas `docker`, et l'installeur ne propose pas de tâche
  `mcp_docker`.
- `scripts/validate-m28.ps1` et `scripts/validate-m28.sh` : refusent un gestionnaire d'intégration qui mentionne Docker.
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
- Revue : le type `app-image` dans les scripts (aucun test ne le lit), l'absence de `Dockerfile`, de fichier de
  composition et de publication d'image dans le dépôt et les workflows.

## References

- [ADR-0004](SEC-0004-local-first-no-llm-core.md) — local-first.
- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — SQLite embarqué.
- [ADR-0059](GEN-0059-stable-local-cli-contract.md) — contrat CLI local.
- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — serveur MCP STDIO natif.
- [ADR-0088](GEN-0088-product-release-installation-and-persistent-data-separation.md) — release, installation et
  séparation des données.
- [ADR-0094](SEC-0094-optional-team-remote-server-mode.md) — mode serveur d'équipe.
- [ADR-0096](GEN-0096-conservative-native-mcp-client-integration.md) — intégration native des clients MCP.
- [ADR-0127](GEN-0127-un-launcher-natif-unique.md) — launcher natif unique.
- [ADR-0129](SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) — plugins providers hors processus,
  runtime embarqué avec `bin/java`.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — toolchain de build Java 21 et Maven.
- `distribution/README.md` — artefacts publiés.
- [VALIDATION_M9](../../project/validation/VALIDATION_M9.md)
