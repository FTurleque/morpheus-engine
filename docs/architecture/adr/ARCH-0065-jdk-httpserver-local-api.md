---
id: ARCH-0065
title: "JDK HttpServer pour l'API locale"
domain: architecture
rules: true
files: ["**/pom.xml", "distribution/build-portable.ps1", "distribution/build-portable.sh"]
---

# JDK HttpServer pour l'API locale

- Statut : **Acceptée**
- Date : 2026-07-24

## Context

MORPHEUS doit s'exposer comme service headless local sans restructurer le produit autour d'un framework web. La
baseline est Java 21 (ADR-0133), la distribution est autonome via `jpackage` (ADR-0134), et le produit n'a
besoin ni de servlet container, ni d'injection de dépendances web, ni de templating, ni de websocket, ni de stack
cloud.

Java 21 fournit le module `jdk.httpserver` avec l'API `com.sun.net.httpserver.HttpServer`, suffisante pour un serveur
HTTP embarqué local et testable.

## Decision

L'API locale utilise :

```text
module = jdk.httpserver
server = com.sun.net.httpserver.HttpServer
```

Elle se lance par `morpheus api`, avec `--host` et `--port` ; les valeurs par défaut sont
`MorpheusHttpServer.DEFAULT_HOST` (loopback) et `MorpheusHttpServer.DEFAULT_PORT`. Le bind par défaut est loopback.

Contraintes :

- pas de Spring ;
- pas de servlet container ;
- pas d'exigence de Netty ;
- aucune dépendance à un framework HTTP ;
- aucun bind réseau public par défaut ;
- aucune terminaison TLS par le serveur local.

Le routeur HTTP reste un adaptateur fin. Les règles métier appartiennent à `morpheus-application` et
`morpheus-domain`.

Le packaging `jpackage` ajoute explicitement le module (`--add-modules jdk.httpserver`, avec les autres modules
requis) afin que le runtime embarqué le contienne.

## Do's and Don'ts

### Do

- Servir l'API locale avec `com.sun.net.httpserver.HttpServer`.
- Lier le serveur local sur une adresse loopback par défaut.
- Garder le routeur HTTP comme adaptateur fin qui délègue aux services de `morpheus-application`.
- Déclarer `jdk.httpserver` dans `--add-modules` de chaque script de packaging `jpackage`.
- Permettre un port éphémère en test.

### Don't

- Ne pas introduire Spring, un servlet container, Netty ou un autre framework HTTP.
- Ne pas exposer l'API locale sur une interface réseau publique par défaut.
- Ne pas terminer TLS dans le serveur local.
- Ne pas placer de règle métier dans le routeur HTTP.

## Consequences

### Positives

- Aucune dépendance externe pour le transport HTTP.
- Runtime embarqué minimal et maîtrisé.
- Serveur démarrable et arrêtable en test sur un port éphémère, interrogeable par un vrai client HTTP.

### Négatives

- API `com.sun.net.httpserver` de bas niveau : routage, décodage et écriture de réponse sont à la charge du projet.
- Aucune fonctionnalité de framework (filtres déclaratifs, négociation de contenu) disponible d'office.

### Risques

- Un runtime embarqué sans `jdk.httpserver` empêcherait l'API de démarrer ; les scripts de packaging vérifient la
  présence du module dans l'image produite.

## Compliance and Enforcement

- Règle Archgate `ARCH-0065-jdk-httpserver-local-api.rules.ts` : `no-server-framework-dependency`, `portable-image-adds-jdk-httpserver`.
- `morpheus-api/src/test/java/com/morpheus/api/LoopbackHostPolicyTest.java` : adresses loopback acceptées, joker et
  adresses non loopback refusées.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LocalHttpServerBootstrapArchitectureTest.java` :
  le démarrage du serveur local passe par la politique loopback.
- `distribution/build-portable.ps1` et `distribution/build-portable.sh` : ajout de `jdk.httpserver` par
  `--add-modules` et refus d'une image dont le runtime ne contient pas le module.
- `LayerDependencyTest` (`morpheus-architecture-tests`) : `domain` et `application` ne dépendent d'aucun adaptateur.
- Revue : absence de framework HTTP dans les `pom.xml`.

## References

- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — baseline Java 21.
- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution native d'abord.
- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution portable `jpackage`.
- [ADR-0066](ARCH-0066-versioned-http-api-contract.md) — contrat HTTP versionné.
- [ADR-0094](SEC-0094-optional-team-remote-server-mode.md) — mode serveur remote optionnel.
- [VALIDATION_M11](../../project/validation/VALIDATION_M11.md) — preuve du jalon M11.
