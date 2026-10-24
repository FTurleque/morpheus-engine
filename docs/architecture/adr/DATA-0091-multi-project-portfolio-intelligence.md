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

## Decision

Le portfolio repose sur un modèle provider-neutral, dont les types sont dans `com.morpheus.domain.portfolio` :

```text
PortfolioId
PortfolioMembership(PortfolioId, ProjectSpecificationId, locations, providers, status)
PortfolioEntityRef(ProjectSpecificationId, entityType, DomainIdentity)
CrossProjectReference(source, target, relation, evidence, provenance)
PortfolioFreshness(projectId, observedAt, revision, state)
```

Invariants :

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

### Registry

Le registry conserve une identité de portfolio stable et des adhésions par `ProjectSpecificationId`. Un workspace, un
repository ou un provider est une observation attachée à l'adhésion, jamais sa clé métier.

Une observation absente marque le projet `MISSING` mais ne supprime ni l'identité du projet, ni ses références
historiques.

### Cross-project references

Une référence relie deux `PortfolioEntityRef`. Elle conserve relation, provenance, evidence et timestamps. Plusieurs
observations concurrentes restent visibles ; aucune stratégie last-write-wins silencieuse n'est autorisée.

### Queries

Les requêtes sont explicitement :

```text
project-scoped
portfolio-scoped
```

Un résultat portfolio conserve toujours le `ProjectSpecificationId` de chaque élément.

### Traversal

Le traversal inter-projets est un BFS déterministe avec budgets explicites : profondeur, nombre de nœuds et nombre de
liens, chacun plafonné par une constante de `PortfolioTraversalService` (`MAX_DEPTH`, `MAX_NODES`, `MAX_LINKS`). Le
résultat expose les liens visités, les profondeurs et la raison de troncature.

L'ordre observable des nœuds est l'ordre de découverte BFS. Il est préservé indépendamment de l'ordre lexical des
UUID de projet ou d'entité. Les voisins sont ordonnés déterministiquement avant exploration ; le résultat ne réordonne
pas ensuite les nœuds par identité.

### Freshness

La fraîcheur est enregistrée par projet. Mettre à jour un projet ne réécrit pas les autres adhésions. Une révision
inchangée produit une mise à jour idempotente.

### Persistence

Un port `PortfolioStore` est implémenté en mémoire et en SQLite. SQLite le porte par une migration additive,
`V013__portfolio_intelligence.sql`.

### Surfaces

CLI, MCP et HTTP exposent les mêmes intentions : registry, overview, references, traversal et freshness. Les
transports peuvent différer de forme, pas de sémantique. `contracts/public-surfaces.tsv` fait foi pour chaque
capacité `portfolio.*` et déclare explicitement toute absence d'un transport.

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
