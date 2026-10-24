---
id: ARCH-0080
title: "Surfaces d'orchestration et client JARVIS optionnel"
domain: architecture
rules: false
---

# Surfaces d'orchestration et client JARVIS optionnel

- Statut : **Acceptée**
- Date : 2026-07-24

## Context

JARVIS orchestre le travail autour d'un changement ; MORPHEUS porte les faits de cycle de vie et l'évaluation des
transitions (ADR-0077, ADR-0079, ADR-0119). JARVIS doit pouvoir lire ces faits et demander une évaluation sans
recoder les règles de cycle de vie, sans dépendance binaire entre les deux dépôts, et sans que l'indisponibilité de
MORPHEUS ne bloque JARVIS.

## Decision

**Un use case, trois adaptateurs.** MORPHEUS expose le même use case d'orchestration par la CLI, MCP et HTTP : lire
l'état d'orchestration d'un changement, et évaluer une transition. Les surfaces exactes sont les lignes
`orchestration.state` et `orchestration.transition` de `contracts/public-surfaces.tsv`, toutes deux d'intention
`READ`.

**Lecture seule.** Les outils MCP d'orchestration sont en lecture seule. La route HTTP `POST .../transition-check`
est une requête de calcul, sans écriture : un verdict `ALLOWED` n'est pas appliqué. Les opérations d'écriture du
cycle de vie relèvent d'ADR-0083, pas de ces surfaces.

**Client JARVIS optionnel.** Le dépôt JARVIS porte un client HTTP optionnel qui :

- ne connaît que l'URL de MORPHEUS et un `projectId` configurable ;
- lit l'état d'orchestration ;
- demande une évaluation de transition ;
- ne recode aucune règle de cycle de vie ;
- échoue ouvert lorsque MORPHEUS est désactivé ou indisponible.

Les raisons d'abandon (observation, source, cible) sont transmises explicitement et séparément.

**Partage des responsabilités.**

```text
MORPHEUS API          = source des règles et des faits
JARVIS client         = transport + DTO local
JARVIS orchestration  = choix de la prochaine action
```

**Packaging.** Aucune dépendance Maven entre les deux dépôts. Aucun runtime JARVIS n'est embarqué dans MORPHEUS ;
aucun runtime MORPHEUS n'est embarqué dans JARVIS.

## Do's and Don'ts

### Do

- Exposer l'état d'orchestration et l'évaluation de transition de façon cohérente sur la CLI, MCP et HTTP, et les
  déclarer dans `contracts/public-surfaces.tsv`.
- Garder l'évaluation de transition sans effet : elle rend un verdict, elle ne l'applique pas.
- Transmettre séparément les raisons d'abandon d'observation, de source et de cible.

### Don't

- Ne pas faire dépendre une classe `com.morpheus..` d'un package `com.jarvis..`.
- Ne pas ajouter de dépendance Maven entre MORPHEUS et JARVIS, ni embarquer le runtime de l'un dans l'autre.
- Ne pas faire porter une écriture du cycle de vie par les surfaces d'orchestration.

## Consequences

### Positives

- JARVIS consomme les règles et les faits de MORPHEUS sans les dupliquer.
- Les deux produits restent livrables et déployables séparément ; JARVIS fonctionne sans MORPHEUS.

### Négatives

- Le client JARVIS maintient ses propres DTO, alignés à la main sur le contrat HTTP de MORPHEUS.

### Risques

- Le client JARVIS vit hors de ce dépôt : aucun contrôle de MORPHEUS ne vérifie qu'il reste optionnel et fail-open.

## Compliance and Enforcement

- `LayerDependencyTest#morpheusMustNotDependOnJarvisImplementation`
  (`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/JarvisOrchestrationContractTest.java` tient la
  sémantique de l'état et du verdict de transition (aucune inférence de cycle de vie, `UNKNOWN` jamais `BLOCKED`,
  abandon sans raison qui demande une entrée au lieu de muter).
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusJarvisOrchestrationApiContractTest.java` et
  `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusJarvisOrchestrationCliTest.java` exercent les surfaces HTTP et
  CLI.
- `PublicHttpRouteConvergenceTest` (`morpheus-architecture-tests/src/test/java/com/morpheus/api/`),
  `PublicSurfaceManifestCoversEveryServedToolTest` (`.../com/morpheus/mcp/`) et
  `PublicSurfaceManifestCoversEveryServedCliCommandTest` (`.../com/morpheus/cli/`) comparent les surfaces servies au
  manifeste.
- Revue : absence de dépendance Maven vers JARVIS et de runtime JARVIS embarqué ; comportement du client JARVIS.

## References

- [ADR-0059](GEN-0059-stable-local-cli-contract.md) — contrat CLI local stable.
- [ADR-0063](ARCH-0063-read-only-mcp-tool-contract.md) — contrat des outils MCP.
- [ADR-0066](ARCH-0066-versioned-http-api-contract.md) — contrat HTTP `/api/v1`.
- [ADR-0077](ARCH-0077-morpheus-jarvis-read-only-orchestration-boundary.md) — frontière d'orchestration JARVIS.
- [ADR-0079](BE-0079-nondestructive-change-orchestration-state.md) — état d'orchestration non destructif.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — opérations d'écriture contrôlées du cycle de vie.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie et transition tri-état.
- [Validation M14](../../project/validation/VALIDATION_M14.md)
