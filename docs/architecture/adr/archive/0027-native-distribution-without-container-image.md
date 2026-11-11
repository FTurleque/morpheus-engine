# ADR-0027 — Distribution native, sans image Docker

> **Archivée.** Cette décision est consolidée dans [ADR-0134](../GEN-0134-distribution-native-autonome.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0134**
- Date : 2026-07-22

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
et sans démarrer plusieurs services externes.

Une distribution conteneurisée a été envisagée comme mode secondaire pour les usages headless, MCP réseau, API, CI/CD
et serveur partagé, avec une composition multi-service des moteurs. Elle n'est pas retenue : le serveur MCP est servi
par STDIO, l'API locale et le mode serveur d'équipe sont servis par la distribution native, et chaque moteur de
l'écosystème reste installable et exécutable indépendamment.

## Decision

La distribution de MORPHEUS est **native seulement** :

- une archive portable autonome par plateforme, avec runtime Java embarqué, construite par `jlink` et `jpackage`
  (ADR-0061) ;
- un installeur Windows (ADR-0061) ;
- un launcher natif unique, qui route la CLI, le serveur MCP et l'API (ADR-0127).

MORPHEUS ne publie aucune image Docker, et ne requiert Docker pour aucun usage : ni la CLI, ni le serveur MCP, ni
l'API, ni l'intégration des clients MCP, ni le build local de développement.

Le même cœur (`domain`, `application`, stores, providers) sert tous les modes d'exécution, et aucune logique métier ne
dépend du packaging. Les données persistantes sont séparées de l'installation (ADR-0088).

`docker compose` n'est pas une dépendance de MORPHEUS ; aucune composition multi-service n'est fournie.

Invariants :

```text
native CLI != Docker required
packaging != business architecture
same core in every execution mode
persistent data separated from the installation
no cloud dependency
MORPHEUS remains standalone
```

## Do's and Don'ts

### Do

- Livrer MORPHEUS comme distribution native : archive portable avec runtime embarqué, et installeur Windows.
- Garder le même cœur pour la CLI, le serveur MCP et l'API.
- Garder chaque moteur de l'écosystème installable et exécutable indépendamment.

### Don't

- Ne pas publier d'image Docker de MORPHEUS.
- Ne pas exiger Docker, Docker Desktop ni `docker compose` pour installer, lancer, intégrer ou construire MORPHEUS.
- Ne pas mentionner `docker` dans l'installeur ni dans le gestionnaire d'intégration des clients MCP.
- Ne pas faire dépendre une logique métier du mode de packaging.

## Consequences

### Positives

- L'expérience locale ne dépend d'aucun moteur de conteneurs ni d'un JDK installé.
- Une seule chaîne de packaging à qualifier et à publier.

### Négatives

- Un déploiement serveur s'installe comme une application native ; aucune image n'est fournie pour un orchestrateur.
- Une composition de MORPHEUS avec MINOS, NEXUS et JARVIS se fait par configuration des pairs, pas par un fichier de
  composition.

### Risques

- Un besoin de déploiement conteneurisé exigerait une nouvelle décision, avec son packaging, sa qualification et sa
  politique de données.

## Compliance and Enforcement

- Règle Archgate `GEN-0027-native-distribution-without-container-image.rules.ts` : `no-container-image-definition`, `no-container-build-in-pipelines`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m28/McpClientIntegrationArchitectureTest.java` :
  le gestionnaire d'intégration des clients MCP ne contient pas `docker`, et l'installeur ne propose pas de tâche
  `mcp_docker`.
- `scripts/validate-m28.ps1` et `scripts/validate-m28.sh` : refusent un gestionnaire d'intégration qui mentionne Docker.
- Revue : l'absence de `Dockerfile`, de fichier de composition et de publication d'image dans le dépôt et les
  workflows.

## References

- [ADR-0004](../SEC-0004-local-first-no-llm-core.md) — local-first.
- [ADR-0016](0016-java-21-production-baseline.md) — baseline Java 21.
- [ADR-0017](0017-maven-build-foundation.md) — fondation de build Maven.
- [ADR-0018](../DATA-0018-sqlite-initial-persistent-store.md) — SQLite embarqué.
- [ADR-0061](0061-self-contained-jpackage-portable-distribution.md) — distribution portable autonome.
- [ADR-0062](../ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — serveur MCP STDIO natif.
- [ADR-0088](../GEN-0088-product-release-installation-and-persistent-data-separation.md) — séparation installation et
  données.
- [ADR-0094](../SEC-0094-optional-team-remote-server-mode.md) — mode serveur d'équipe.
- [ADR-0096](../GEN-0096-conservative-native-mcp-client-integration.md) — intégration native des clients MCP.
- [ADR-0127](../GEN-0127-un-launcher-natif-unique.md) — launcher natif unique.
- `distribution/README.md` — artefacts publiés.
