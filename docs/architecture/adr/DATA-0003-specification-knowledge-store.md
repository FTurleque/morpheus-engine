---
id: DATA-0003
title: "Isoler la persistance derrière `SpecificationKnowledgeStore`"
domain: data
rules: false
---

# Isoler la persistance derrière `SpecificationKnowledgeStore`

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

MORPHEUS stocke et interroge des spécifications, exigences, changements, décisions, critères d'acceptation, tâches,
versions et liens de traçabilité. Plusieurs familles de stockage sont envisageables : base relationnelle,
documentaire, graphe, moteur hybride, stockage embarqué, backend mémoire pour les tests. Le besoin de traverser des
relations plaide pour un graphe ; la simplicité locale, le versionnement et la distribution plaident pour une solution
embarquée plus classique.

Choisir trop tôt un produit puis exposer son API au domaine rendrait la décision difficilement réversible.

Forces en présence : simplicité (une base utilisée directement serait plus rapide à écrire), traçabilité riche
(requêtes de traversée), local-first (backend installable et exploitable localement), tests sans infrastructure
externe, évolution forte de la structure de connaissance, et performances (l'abstraction ne doit pas empêcher les
optimisations propres au backend).

Options considérées :

- **Accès direct à une base relationnelle** : non retenu comme frontière du domaine ; reste candidat comme
  implémentation.
- **Graphe obligatoire dès le départ** : non retenu ; la traçabilité est graphique, mais un moteur graphe dédié n'est
  pas nécessaire.
- **Documents JSON dans Git uniquement** : insuffisant comme moteur de requêtes principal ; Git reste source et
  historique, mais MORPHEUS a besoin de requêtes normalisées et rapides.
- **Ports possédés par MORPHEUS** : retenu.

## Decision

La persistance est isolée derrière des ports possédés par MORPHEUS, déclarés dans `morpheus-application`
(`com.morpheus.application.store`). `SpecificationKnowledgeStore` porte les projets et le cycle de vie des snapshots
de connaissance ; les autres contenus (exigences versionnées, contenu métier d'un snapshot, traçabilité, références
externes, états de synchronisation, portfolios, vues sauvegardées, policy packs) ont chacun leur port dans le même
paquet.

Chaque contrat est défini à partir des cas d'usage du moteur, jamais comme copie de l'API d'une base de données.

```text
Domaine / Services MORPHEUS
          │
          ▼
Ports de com.morpheus.application.store
          │
   ┌──────┴──────┐
   ▼             ▼
store-memory   store-sqlite
```

Le contrat public d'un port n'impose ni SQL, ni Cypher, ni Gremlin, ni JSONPath, ni index propre à un moteur, ni
transaction propre à un produit, ni type de driver, ni identifiant physique du backend. Des options spécialisées
peuvent exister dans un adaptateur, sans contaminer les services métier. Une capacité avancée s'expose par une
opération explicite, jamais par une API générique `query(String)`.

**Backend mémoire de référence.** `morpheus-store-memory` implémente les ports. Il sert à tester le domaine et les
services, à vérifier que le contrat n'est pas couplé au backend persistant, à fournir des fixtures rapides et à tester
les providers. Il n'a pas à offrir les performances d'un backend de production, mais respecte la même sémantique
fonctionnelle pour les opérations couvertes. Le backend persistant est SQLite (ADR-0018).

**Qui dépend d'un store concret.** Aucune classe de `morpheus-application` ni de `morpheus-domain` ne dépend d'un
store concret. Dans `morpheus-api` et `morpheus-mcp`, seules les racines de composition nommées d'ADR-0109 construisent
le runtime d'une requête à partir de `morpheus-store-sqlite`. La CLI est la racine de composition du processus :
`CliRuntime` et les classes de câblage de `morpheus-cli` construisent les stores concrets. Les stores d'une opération
sont construits par `SqliteOperationRuntime`, sur les trois transports.

## Do's and Don'ts

### Do

- Ajouter toute nouvelle capacité de persistance comme port dans `com.morpheus.application.store`, défini depuis un
  cas d'usage.
- Implémenter chaque port dans `morpheus-store-memory` et dans `morpheus-store-sqlite`, avec la même sémantique.
- Construire les stores concrets dans une racine de composition : la CLI, ou une racine nommée d'ADR-0109.

### Don't

- Ne pas exposer dans un port un langage de requête, un type de driver, une transaction propre à un produit ni un
  identifiant physique du backend.
- Ne pas faire dépendre `morpheus-domain` ni `morpheus-application` d'un paquet `com.morpheus.store..`.
- Ne pas construire de store concret dans une classe de `morpheus-api` ou de `morpheus-mcp` hors des racines nommées
  d'ADR-0109.
- Ne pas offrir une API générique `query(String)` à la place d'une capacité explicite.

## Consequences

### Positives

- Backend remplaçable, testabilité, isolation technologique et contrats stables.
- Possibilité d'expérimenter plusieurs moteurs, et backend léger ou embarqué.
- Les services métier et les adaptateurs hors racines de composition ignorent la base choisie.

### Négatives

- Une couche et un mapping supplémentaires.
- Risque de concevoir un plus petit dénominateur commun ; certaines capacités avancées doivent être exposées sans fuite
  technologique.
- Une migration entre backends reste complexe.

### Risques

- **Plus petit dénominateur commun** : mitigé en concevant les ports depuis les cas d'usage et en introduisant des
  capacités explicites.
- **Double modèle coûteux** : mitigé par un mapping simple et un domaine ciblé, sans reproduire toutes les capacités du
  backend.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` :
  `domainMustNotDependOnAdapters`, `applicationMustNotDependOnAdapters`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterCompositionRootArchitectureTest.java` :
  `onlyTheNamedCompositionRootsReachAConcreteStoreOrProvider`,
  `everyNamedCompositionRootStillReachesAConcreteStoreOrProvider`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SqliteConnectionScopeUseArchitectureTest.java` :
  construction des stores d'une opération.
- Parité des deux backends : `BusinessContentQueryBackendParityTest`, `TraceabilityRelationFilterParityTest`,
  `m23/PortfolioPersistenceParityTest`, `m24/SavedViewPersistenceParityTest`, `m25/PolicyPersistenceParityTest`
  (`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/`), et les tests de contrat de ce module qui
  s'exécutent sur les deux stores.
- Revue : l'absence de langage de requête ou de type de driver dans la signature d'un port.

## References

- [ADR-0001](ARCH-0001-morpheus-owned-domain.md) — domaine possédé par MORPHEUS.
- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — SQLite comme backend persistant.
- [ADR-0109](ARCH-0109-transport-adapters-are-named-composition-roots.md) — racines de composition nommées.
- [ADR-0114](BE-0114-database-work-runs-on-a-bounded-pool-of-platform-threads.md) — travail sur la base.
- [Validation M0](../../project/validation/VALIDATION_M0.md)
