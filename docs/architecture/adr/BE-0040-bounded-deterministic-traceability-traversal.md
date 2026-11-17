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

Forme du service, de ses directions et de l'ordre des voisins :

```text
TraceabilityTraversalDirection
  OUTGOING      suit source -> target
  INCOMING      parcourt target -> source
  BIDIRECTIONAL peut emprunter les deux vues

TraceabilityTraversalService
  direct(...)
  traverse(...)
  traverseExplained(...)

ordre des voisins : next TraceabilityEntityRef, relation type, TraceabilityLinkId
traversal(snapshot A) != traversal(snapshot B)
```

Qu'une relation soit traversable n'affirme aucune transitivité sémantique : `A -R1-> B -R2-> C` peut produire un chemin
de longueur 2, mais jamais un lien `A -> C`. Parce que `TraceabilityPathStep` garde le lien persisté et le sens de
l'étape, un chemin bidirectionnel peut emprunter une arête à rebours sans falsifier son sens de preuve. Avec l'ordre
canonique, le plus court chemin reconstruit depuis l'étape de découverte est le premier plus court chemin du parcours
en largeur, choisi de façon déterministe. Les vues compactes de `trace_requirement` et `get_change_context` sont le
chemin commun de MCP, de HTTP et du JSON de la CLI.

## Decision

1. **Service** : `TraceabilityTraversalService` (`morpheus-application`), neutre envers providers et stores, lit
   `TraceabilityStore.outgoing` et `incoming`, rend `TraceabilitySubgraph`, `ExplainedTraversal`, `TraceabilityPath`,
   `TraceabilityPathStep`, sans table SQLite.
2. **Snapshot** : chaque requête porte un `KnowledgeSnapshotId` explicite, ne lit que ses liens, sans fusion.
3. **Direction** : `OUTGOING`, `INCOMING` ou `BIDIRECTIONAL`, par requête. Le lien rendu garde sa direction
   persistée ; une vue inverse ne crée ni seconde arête ni preuve.
4. **Profondeur** : à l'appelant, bornée par les transports ; `maxDepth <= 0` rejeté ; une arête n'est explorée que
   depuis une profondeur `< maxDepth`, départ à 0.
5. **Budgets** : `TraceabilityTraversalService.MAX_NODES` et `MAX_LINKS` (ADR-0091). Une traversée incomplète
   renseigne `truncationReason()` et `truncated()` : `NODE_BUDGET_REACHED:<n>`, `LINK_BUDGET_REACHED:<n>`,
   ou `DEPTH_BUDGET_REACHED:<n>` si un lien reste non observé au-delà de la frontière. Un lien vers un nœud refusé
   est écarté. Les vues compactes ont `truncationReason` (chaîne ou `null`) et `truncated` (ADR-0123).
6. **Cycles et ordre** : largeur bornée avec nœuds découverts suivis ; un cycle de la frontière reste visible, sans
   boucle infinie. Voisins par entité suivante, relation, `TraceabilityLinkId` ; sortie stable.
7. **Chemins** : `traverseExplained` rend à chaque nœud atteint son plus court chemin, issu de l'étape de découverte,
   et ne dit aucun nœud non atteint « sans chemin ». Une étape garde lien persisté et sens (`from`, `into`).
8. **Filtre** : `Set<TraceabilityRelationType>` explicite, vide = tout ; hors énumération refusé.
9. **Exclu** : lien transitif synthétique, résolution externe (ADR-0142), enrichissement `LINKS_TO_CODE` /
   `LINKS_TO_TEST`, correspondance floue, dérivation ou mutation de lien, base graphe.

## Do's and Don'ts

### Do

- Passer un `KnowledgeSnapshotId` explicite à chaque requête de traversée.
- Exiger `maxDepth > 0`.
- Ordonner les voisins par entité suivante, type de relation, puis identifiant de lien.
- Exposer la raison de troncature quand un budget de nœuds, de liens ou de profondeur est atteint.
- Écarter tout lien qui mène à un nœud refusé par le budget.
- Rendre dans chaque étape de chemin le lien persisté réel et le sens de parcours.

### Don't

- Ne pas fusionner des liens de snapshots différents.
- Ne pas matérialiser d'arête inverse ni de lien transitif synthétique.
- Ne pas présenter un sous-graphe tronqué comme complet, ni un nœud non atteint comme sans chemin.
- Ne pas introduire de base graphe pour la traversée.

## Consequences

### Positives

- La traçabilité est navigable sur les deux stores sans backend graphe.
- Un même graphe, une même requête et un même snapshot donnent le même sous-graphe et les mêmes chemins.

### Négatives

- Une traversée large lit le store nœud par nœud ; les budgets bornent ce coût au lieu de le supprimer.
- Les réponses de trace et de contexte de changement portent deux champs de plus ; les routes concernées répondent la
  réponse générique `Success` de l'OpenAPI et les outils MCP ne déclarent pas de schéma de sortie.
- La fixture large de M19 se tronque sur `NODE_BUDGET_REACHED`, ce que son gate asserte.

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
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/M19TraceabilityPerformanceGate.java` :
  troncature de la fixture large sur le budget de nœuds (lane nocturne).
- Revue : l'absence de base graphe et de lien synthétique dans tout nouveau consommateur ; les budgets de traversée et
  la règle « un lien vers un nœud refusé n'est pas retenu ».

## References

- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée et taxonomie contrôlée.
- [ADR-0038](DATA-0038-snapshot-scoped-traceability-persistence.md) — persistance snapshot-scoped de la traçabilité.
- [ADR-0039](BE-0039-deterministic-traceability-derivation.md) — dérivation déterministe de la traçabilité.
- [ADR-0142](DATA-0142-references-externes.md) — traçabilité des références externes.
- [ADR-0138](ARCH-0138-analyse-de-changement-derivee-et-bornee.md) — chemins d'impact bornés.
- [ADR-0091](DATA-0091-multi-project-portfolio-intelligence.md) — traversée du portefeuille, valeurs et raisons des
  budgets.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vues compactes de la trace.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — ce qui n'est pas observé est dit.
- [Validation M4](../../project/validation/VALIDATION_M4.md)
