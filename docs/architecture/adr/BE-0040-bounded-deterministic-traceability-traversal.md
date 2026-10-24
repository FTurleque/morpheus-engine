---
id: BE-0040
title: "Traversée bornée et déterministe de la traçabilité"
domain: backend
rules: false
---

# Traversée bornée et déterministe de la traçabilité

- Statut : **Acceptée**
- Date : 2026-07-23

## Context

Le modèle `TraceabilityLink`, sa persistance snapshot-scoped (ADR-0038) et sa dérivation déterministe depuis le modèle
normalisé (ADR-0039) rendent les liens disponibles. Il faut les rendre navigables sans introduire de backend graphe,
sans matérialiser d'arêtes inverses et sans transformer une traversée en nouvelle preuve sémantique.

## Decision

Un service applicatif neutre vis-à-vis des providers et des stores, `TraceabilityTraversalService`
(`morpheus-application`), s'appuie sur `TraceabilityStore.outgoing` et `TraceabilityStore.incoming` :

```text
TraceabilityTraversalDirection
  OUTGOING
  INCOMING
  BIDIRECTIONAL

TraceabilityTraversalService
  direct(...)
  traverse(...)
  traverseExplained(...)
```

Résultats explicites : `TraceabilitySubgraph`, `ExplainedTraversal`, `TraceabilityPath`, `TraceabilityPathStep`.
La traversée ne demande aucune table SQLite propre.

**Snapshot comme frontière obligatoire.** Toute requête porte un `KnowledgeSnapshotId` explicite et ne lit que les liens
de ce snapshot, par le port de persistance de la traçabilité. Aucune fusion entre snapshots n'est autorisée.

```text
traversal(snapshot A) != traversal(snapshot B)
```

**Direction.** La direction est une propriété de la requête :

```text
OUTGOING      suit source -> target
INCOMING      parcourt target -> source
BIDIRECTIONAL peut emprunter les deux vues
```

Même en parcours inverse, le `TraceabilityLink` retourné conserve sa direction canonique persistée. Une vue inverse ne
crée jamais une seconde arête ni une seconde preuve. Les requêtes directes réutilisent les primitives du port :
`outgoing` est la vue directe canonique, `incoming` la vue inverse sur l'arête persistée ; les résultats sont triés et
restent dans le snapshot.

**Profondeur.** `maxDepth` est obligatoire et strictement positif ; `maxDepth <= 0` est rejeté explicitement. Le nœud
de départ est à la profondeur 0 ; une arête n'est explorée que depuis un nœud de profondeur strictement inférieure à
`maxDepth`.

**Budgets et troncature.** La profondeur appartient à l'appelant ; les nœuds et les liens sont bornés par le service,
par `TraceabilityTraversalService.MAX_NODES` et `TraceabilityTraversalService.MAX_LINKS`. Une traversée qui s'arrête
avant d'avoir observé tout ce que sa profondeur admet le dit dans `TraceabilitySubgraph.truncationReason()` (budget de
nœuds, de liens ou de profondeur atteint), selon ADR-0108.

**Cycles.** La traversée est un parcours en largeur borné, avec suivi explicite des nœuds découverts. Un cycle reste
visible dans le sous-graphe lorsque ses arêtes appartiennent à la frontière explorée, mais aucun cycle ne provoque
d'exploration infinie.

**Déterminisme.** Les voisins sont ordonnés canoniquement par :

```text
next TraceabilityEntityRef
relation type
TraceabilityLinkId
```

Les nœuds et arêtes du sous-graphe sont retournés dans un ordre stable. `traverseExplained` rend, pour chaque nœud
atteint, le plus court chemin depuis le départ, reconstruit depuis l'étape qui a découvert ce nœud : avec le même ordre,
c'est le premier plus court chemin du parcours en largeur, choisi de façon déterministe.

**Filtres.** Le filtre est un `Set<TraceabilityRelationType>` explicite ; l'ensemble vide signifie toutes les
relations. Aucun type hors de l'énumération n'est accepté.

**Chemin explicable.** `TraceabilityPathStep` conserve le lien persisté réel et le sens de parcours de l'étape
(`from`, `into`). Un chemin bidirectionnel peut donc emprunter une arête à rebours sans falsifier son sens de preuve.

**Traversée n'est pas transitivité.** Qu'une relation soit traversable n'affirme aucune transitivité sémantique :

```text
A -R1-> B -R2-> C
```

peut produire un chemin de longueur 2, mais jamais un lien `A -> C`. Aucun `TraceabilityLink` synthétique n'est
produit.

**Hors du service de traversée** :

```text
external resolution
LINKS_TO_CODE / LINKS_TO_TEST enrichment
fuzzy matching
link derivation
link persistence mutation
transitive edge materialization
graph database
```

Les références externes relèvent d'ADR-0142.

## Do's and Don'ts

### Do

- Passer un `KnowledgeSnapshotId` explicite à chaque requête de traversée.
- Exiger `maxDepth > 0`.
- Ordonner les voisins par entité suivante, type de relation, puis identifiant de lien.
- Exposer la raison de troncature quand un budget de nœuds, de liens ou de profondeur est atteint.
- Rendre dans chaque étape de chemin le lien persisté réel et le sens de parcours.

### Don't

- Ne pas fusionner des liens de snapshots différents.
- Ne pas matérialiser d'arête inverse ni de lien transitif synthétique.
- Ne pas présenter un sous-graphe tronqué comme complet.
- Ne pas introduire de base graphe pour la traversée.

## Consequences

### Positives

- La traçabilité est navigable sur les deux stores sans backend graphe.
- Un même graphe, une même requête et un même snapshot donnent le même sous-graphe et les mêmes chemins.

### Négatives

- Une traversée large lit le store nœud par nœud ; les budgets bornent ce coût au lieu de le supprimer.

### Risques

- Un appelant qui ignore `truncationReason` peut lire un sous-graphe partiel comme complet.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/TraceabilityTraversalContractTest.java`, sur
  les stores mémoire et SQLite : `directAndInverseViewsPreserveTheCanonicalPersistedEdge`,
  `boundedTraversalIsCycleSafeAndNeverCreatesATransitiveEdge`, `anExplainedTraversalChoosesADeterministicShortestPath`,
  `relationFiltersApplyToTraversalAndPath`, `maxDepthMustBeStrictlyPositive`,
  `traversalNeverLeaksLinksAcrossSnapshots`, `memoryAndSqliteExposeIdenticalTraversalAndPathForTheSameGraph`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/TraceabilityTruncationContractTest.java` : la
  troncature de la traversée.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/TraceabilityRelationFilterParityTest.java` :
  parité du filtre de relations entre stores.
- Revue : l'absence de base graphe et de lien synthétique dans tout nouveau consommateur.

## References

- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée et taxonomie contrôlée.
- [ADR-0038](DATA-0038-snapshot-scoped-traceability-persistence.md) — persistance snapshot-scoped de la traçabilité.
- [ADR-0039](BE-0039-deterministic-traceability-derivation.md) — dérivation déterministe de la traçabilité.
- [ADR-0142](DATA-0142-references-externes.md) — traçabilité des références externes.
- [ADR-0138](ARCH-0138-analyse-de-changement-derivee-et-bornee.md) — chemins d'impact bornés.
- [ADR-0108](BE-0108-a-response-says-what-it-could-not-observe.md) — plafonds de nœuds et de liens, raison de
  troncature.
- [Validation M4](../../project/validation/VALIDATION_M4.md)
