# ADR-0057 — Impacts de dépendance explicites par chemins bornés

> **Archivée.** Cette décision est consolidée dans [ADR-0138](../ARCH-0138-analyse-de-changement-derivee-et-bornee.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0138**
- Date : 2026-07-24

## Context

MORPHEUS dispose d'une taxonomie de relations de traçabilité contrôlée et d'une traversée déterministe limitée à un
snapshot publié (ADR-0118, ADR-0038, ADR-0040). L'analyse d'un changement (ADR-0056) doit expliquer les dépendances
d'un requirement directement modifié, sans transformer une proximité dans le graphe en relation métier inventée.

La seule relation autorisée pour cette explication est `TraceabilityRelationType.DEPENDS_ON`.

## Decision

Pour chaque requirement directement affecté par un `RequirementDelta` et possédant une occurrence CURRENT dans la
baseline publiée, l'analyse de changement explore uniquement les liens persistés `DEPENDS_ON`.

Deux directions sont exposées :

```text
OUTGOING DEPENDS_ON -> DEPENDENCY
INCOMING DEPENDS_ON -> DEPENDENT
```

Exemple :

```text
A --DEPENDS_ON--> B --DEPENDS_ON--> C
D --DEPENDS_ON--> A
```

Pour un changement direct sur A :

```text
B = DEPENDENCY depth 1
C = DEPENDENCY depth 2
D = DEPENDENT  depth 1
```

### Chemin explicatif

Chaque `ChangeDependencyImpact` contient :

```text
originRequirementId
DependencyImpactDirection
impactedEntity
TraceabilityPath
```

Le chemin est obtenu par la traversée BFS déterministe de traçabilité et représente le plus court chemin observé dans
la direction demandée, borné par `maxDepth`. Aucun chemin n'est créé si le graphe persistant ne permet pas de le
démontrer.

### Profondeur et troncature

`maxDepth` est obligatoire et strictement positif :

```text
maxDepth = 1 -> voisins directs uniquement
maxDepth = 2 -> jusqu'à deux relations DEPENDS_ON
```

Il n'existe ni profondeur implicite, ni profondeur illimitée. La traversée porte en outre les budgets de nœuds et de
liens de `TraceabilityTraversalService` (ADR-0108) : quand elle s'arrête avant d'avoir tout observé dans la profondeur
demandée, l'analyse émet `TRACEABILITY_TRAVERSAL_TRUNCATED` avec la raison de troncature, au lieu de présenter la
liste des impacts comme complète.

### Résolution partielle

Les liens `PARTIALLY_RESOLVED`, `UNRESOLVED` ou `HEURISTIC` restent visibles. Ils ne sont ni supprimés ni promus en
faits certains. Si un chemin contient au moins un lien `resolution != RESOLVED`, l'analyse émet
`TRACEABILITY_PATH_PARTIALLY_RESOLVED` et conserve le chemin avec l'état de résolution de chaque lien.

### Requirements uniquement proposés

Un `ADDED` absent de la baseline ne dispose pas encore d'un nœud publié fiable pour une traversée limitée au
snapshot. L'analyse n'essaie pas de faire correspondre son texte à un nœud existant et n'invente pas de `DEPENDS_ON`.

### Entités atteintes

La relation `DEPENDS_ON` reste typée par le graphe de traçabilité. L'analyse expose le `TraceabilityEntityRef`
réellement atteint ; elle ne force pas artificiellement toute cible à être un `Requirement`.

### Frontière MINOS

```text
MORPHEUS -> dépendances de spécification/intention explicitement tracées
MINOS    -> structure, symboles, références et impact de code
```

L'analyse ne parcourt ni AST, ni call graph, ni index SCIP, ni graphe de symboles.

### Déterminisme

- filtre fixe `DEPENDS_ON` ;
- traversée bornée ;
- ordre canonique des voisins hérité de la traversée de traçabilité ;
- plus court chemin déterministe ;
- impacts triés par requirement origine, direction, cible et profondeur ;
- aucun LLM ni semantic matching.

## Do's and Don'ts

### Do

- Ne suivre que les liens persistés `DEPENDS_ON` du snapshot publié de référence.
- Exiger un `maxDepth` strictement positif et le refuser sinon.
- Associer à chaque impact son requirement d'origine, sa direction (`DEPENDENCY` ou `DEPENDENT`), l'entité atteinte
  et le plus court chemin qui la démontre.
- Émettre `TRACEABILITY_PATH_PARTIALLY_RESOLVED` dès qu'un chemin contient un lien non `RESOLVED`, en gardant le
  chemin.
- Émettre `TRACEABILITY_TRAVERSAL_TRUNCATED`, avec sa raison, quand la traversée est tronquée par un budget.
- Trier les impacts par requirement origine, direction, cible puis profondeur.

### Don't

- Ne pas déduire une dépendance d'une autre relation de traçabilité, d'une proximité de graphe ou d'une similarité
  textuelle.
- Ne pas calculer d'impact de dépendance pour un requirement `ADDED` absent de la baseline.
- Ne pas supprimer ni promouvoir en fait certain un lien partiellement résolu, non résolu ou heuristique.
- Ne pas forcer une entité atteinte à être un `Requirement`.
- Ne pas parcourir de structure de code (AST, call graph, symboles) : c'est le domaine de MINOS.

## Consequences

### Positives

- Chaque impact de dépendance est explicable par un chemin persisté et vérifiable.
- Le résultat est déterministe et identique entre `store-memory` et `store-sqlite`.
- La frontière entre intention (MORPHEUS) et code (MINOS) reste nette.

### Négatives

- Un requirement seulement proposé n'a pas d'impact de dépendance tant qu'il n'est pas publié.
- Une dépendance qui n'a pas été tracée explicitement par `DEPENDS_ON` n'apparaît pas.

### Risques

- Un graphe très connecté atteint les budgets de traversée : la liste d'impacts est alors partielle, et seul
  l'avertissement de troncature le signale à l'appelant.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ChangeAnalysisContractTest.java` :
  `expandsOnlyExplicitDependsOnAndKeepsShortestBoundedExplanationPaths` (dépendances directes et transitives,
  dépendants entrants, coupure à `maxDepth`, chemins uniquement `DEPENDS_ON`, avertissement de résolution partielle,
  refus de `maxDepth = 0`), `memoryAndSqliteProduceExactlySameAnalysis`,
  `sqliteReopenAndCompactJsonRemainByteDeterministic`.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusCliTest.java` : restitution de
  `TRACEABILITY_TRAVERSAL_TRUNCATED` par la CLI.
- Revue : l'absence de parcours de structure de code et le refus de faire correspondre un requirement `ADDED` à un
  nœud existant.

## References

- [ADR-0038](../DATA-0038-snapshot-scoped-traceability-persistence.md) — persistance de traçabilité par snapshot.
- [ADR-0040](../BE-0040-bounded-deterministic-traceability-traversal.md) — traversée bornée et déterministe.
- [ADR-0056](0056-deterministic-current-proposed-change-analysis.md) — analyse current/proposed d'un changement.
- [ADR-0108](../BE-0108-a-response-says-what-it-could-not-observe.md) — budgets de traversée et raison de troncature.
- [ADR-0118](../ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — taxonomie de traçabilité contrôlée.
- [VALIDATION_M8](../../../project/validation/VALIDATION_M8.md)
