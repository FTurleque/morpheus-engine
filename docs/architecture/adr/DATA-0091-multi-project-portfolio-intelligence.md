---
id: DATA-0091
title: "Multi-project portfolio intelligence"
domain: data
rules: false
---

# Multi-project portfolio intelligence

- Statut : **Acceptée**
- Date : 2026-07-28

## Context

MORPHEUS raisonne sur un projet et un snapshot. Raisonner sur plusieurs projets exige une frontière de portfolio
explicite : une agrégation naïve par chemin de workspace, URL de repository ou identifiant provider confondrait
localisation technique et identité métier.

Le modèle du portfolio, dans `com.morpheus.domain.portfolio` :

```text
PortfolioId
PortfolioMembership(PortfolioId, ProjectSpecificationId, locations, providers, status)
PortfolioEntityRef(ProjectSpecificationId, entityType, DomainIdentity)
CrossProjectReference(source, target, relation, evidence, provenance)
PortfolioFreshness(projectId, observedAt, revision, state)
```

Les invariants que la décision tient :

```text
cross-project identity != source path
project identity != workspace path
project identity != repository URL
project identity != provider identifier
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
conflict != silent last-write-wins
precedence != provenance erasure
traversal is bounded and explainable
freshness != full destructive rescan
local-first remains default
```

## Decision

1. **Modèle** : provider-neutral, types dans `com.morpheus.domain.portfolio` ; le mode local-first reste le défaut.
2. **Registry** : identité de portfolio stable, adhésions par `ProjectSpecificationId`. Workspace, repository et
   provider sont des observations de l'adhésion, jamais sa clé ; l'adhésion ne vaut pas propriété des sources. Une
   observation absente marque le projet `MISSING` sans supprimer son identité ni ses références historiques.
3. **Références inter-projets** : relient deux `PortfolioEntityRef`, conservent relation, provenance, evidence et
   timestamps, et ne sont pas une preuve de traçabilité. Les observations concurrentes restent visibles : aucun
   last-write-wins silencieux, et une précédence n'efface pas la provenance.
4. **Requêtes** : explicitement `project-scoped` ou `portfolio-scoped` ; un résultat portfolio garde le
   `ProjectSpecificationId` de chaque élément.
5. **Traversal** : BFS déterministe borné en profondeur, nœuds et liens par `PortfolioTraversalService` (`MAX_DEPTH`,
   `MAX_NODES`, `MAX_LINKS`) ; le résultat expose liens visités, profondeurs et raison de troncature. Les voisins sont
   ordonnés déterministiquement avant exploration ; les nœuds restent dans l'ordre de découverte BFS, jamais
   réordonnés par identité ni par UUID.
6. **Freshness** : enregistrée par projet, sans réécrire les autres adhésions ni rescan destructif complet ;
   idempotente pour une révision inchangée.
7. **Persistance** : port `PortfolioStore` en mémoire et en SQLite, migration additive
   `V013__portfolio_intelligence.sql`.
8. **Surfaces** : CLI, MCP et HTTP exposent les mêmes intentions (registry, overview, references, traversal,
   freshness), de forme libre, de sémantique identique ; `contracts/public-surfaces.tsv` fait foi pour chaque capacité
   `portfolio.*` et déclare toute absence d'un transport.

## Do's and Don'ts

### Do

- Identifier un projet du portfolio par son `ProjectSpecificationId`, et traiter workspace, repository et provider
  comme des observations de l'adhésion.
- Marquer `MISSING` un projet dont l'observation est absente, en conservant son identité et ses références.
- Conserver relation, provenance, evidence et timestamps sur chaque référence inter-projets, et garder visibles les
  observations concurrentes.
- Porter le `ProjectSpecificationId` de chaque élément d'un résultat portfolio.
- Borner le traversal en profondeur, nœuds et liens, et exposer la raison de troncature.
- Restituer les nœuds dans l'ordre de découverte BFS.
- Enregistrer la fraîcheur par projet, de façon idempotente pour une révision inchangée.
- Garder `store-memory` et `store-sqlite` équivalents pour `PortfolioStore`.

### Don't

- Ne pas dériver l'identité d'un projet d'un chemin de workspace, d'une URL de repository ou d'un identifiant provider.
- Ne pas supprimer une identité ou une référence parce qu'un projet est absent.
- Ne pas résoudre un conflit d'observations par last-write-wins silencieux.
- Ne pas présenter une référence inter-projets comme une preuve de traçabilité.
- Ne pas réordonner les nœuds d'un traversal par identité.
- Ne pas réécrire les autres adhésions lors de la mise à jour de fraîcheur d'un projet.

## Consequences

### Positives

- Identité inter-projets stable, absence non destructive, références explicables, traversal borné, local-first.

### Négatives

- Un store supplémentaire, une migration, des surfaces supplémentaires, et l'obligation de toujours conserver le scope
  projet.

### Risques

- Le traversal du portfolio déclare `DEPTH_BUDGET_REACHED` dès qu'un nœud de la frontière a un voisin, y compris
  déjà observé : il peut se dire tronqué alors que rien ne reste à voir (écart connu, ADR-0124).

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m23/PortfolioIntelligenceContractTest.java` :
  `projectIdentityIsNotDerivedFromWorkspaceRepositoryOrProvider`,
  `missingProjectRetainsIdentityReferencesAndFreshnessHistory`, `conflictsPreserveAllProviderObservations`,
  `traversalIsDeterministicBoundedAndExplainable`, `memoryAndSqliteAdaptersHaveEquivalentObservableState`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m23/PortfolioPersistenceParityTest.java` :
  parité mémoire/SQLite des providers d'une adhésion.
- `PublicHttpRouteConvergenceTest`, `PublicSurfaceManifestCoversEveryServedToolTest` et
  `PublicSurfaceManifestCoversEveryServedCliCommandTest` (tests d'architecture) : convergence des surfaces
  `portfolio.*` avec `contracts/public-surfaces.tsv`.

## References

- [Plateforme portfolio](../../developer/PORTFOLIO_INTELLIGENCE.md)
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — troncature déclarée, écart connu du portfolio.
- [VALIDATION_M23](../../project/validation/VALIDATION_M23.md)
- `contracts/public-surfaces.tsv`
