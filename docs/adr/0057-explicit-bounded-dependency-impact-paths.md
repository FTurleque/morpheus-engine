# ADR-0057 — Impacts de dépendance explicites par chemins bornés

- Statut : **Proposée — M8, preuve Maven en attente**
- Date : 24 juillet 2026
- Dépend de : ADR-0037, ADR-0038, ADR-0040, ADR-0056
- Portée : M8 — dépendances et chemins explicatifs d'un changement

## Contexte

M4 fournit déjà une taxonomie contrôlée et une traversée déterministe snapshot-scoped. M8 doit réutiliser ce graphe pour expliquer les dépendances d'un requirement directement changé, sans transformer une proximité de graphe en relation métier inventée.

La relation pertinente existe déjà :

```text
TraceabilityRelationType.DEPENDS_ON
```

## Décision

Pour chaque requirement directement affecté par un `RequirementDelta` et possédant une occurrence CURRENT dans la baseline publiée, M8 explore uniquement les liens persistés `DEPENDS_ON`.

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

## Chemin explicatif

Chaque `ChangeDependencyImpact` contient :

```text
originRequirementId
DependencyImpactDirection
impactedEntity
TraceabilityPath
```

Le chemin est obtenu par la traversée BFS déterministe existante et représente le plus court chemin observé dans la direction demandée, borné par `maxDepth`.

Aucun chemin n'est créé si le graphe persistant ne permet pas de le démontrer.

## Profondeur

`maxDepth` est obligatoire et strictement positif.

```text
maxDepth = 1 -> voisins directs uniquement
maxDepth = 2 -> jusqu'à deux relations DEPENDS_ON
```

M8 ne possède pas de profondeur implicite ou illimitée.

## Résolution partielle

Les liens `PARTIALLY_RESOLVED`, `UNRESOLVED` ou `HEURISTIC` restent visibles. Ils ne sont ni supprimés ni promus en faits certains.

Si un chemin contient au moins un lien dont :

```text
resolution != RESOLVED
```

M8 émet :

```text
TRACEABILITY_PATH_PARTIALLY_RESOLVED
```

Le chemin reste exposé avec l'état de résolution de chaque lien.

## Requirements uniquement proposés

Un `ADDED` absent de la baseline ne dispose pas encore d'un nœud publié fiable pour une traversée snapshot-scoped.

M8 n'essaie donc pas de faire correspondre son texte à un nœud existant et n'invente pas de `DEPENDS_ON`. Il expose le warning défini par ADR-0056.

## Entités atteintes

La relation `DEPENDS_ON` reste typée par le graphe M4. M8 expose donc le `TraceabilityEntityRef` réellement atteint ; il ne force pas artificiellement toute cible à être un `Requirement`.

## Frontière MINOS

Un lien vers une entité externe ou du code n'est jamais interprété comme une analyse statique du code.

```text
MORPHEUS -> dépendances de specification/intention explicitement tracées
MINOS    -> structure, symboles, références et impact de code
```

M8 ne parcourt pas un AST, un call graph, un index SCIP ou un graphe de symboles.

## Déterminisme

- relation filter fixe : `DEPENDS_ON` ;
- traversée bornée ;
- ordre canonique des voisins hérité de M4 ;
- shortest path déterministe ;
- impacts triés par requirement origine, direction, cible et profondeur ;
- aucun LLM ou semantic matching.

## Preuve attendue

L'ADR pourra passer à **Acceptée — M8** lorsque le gate démontre :

1. dépendances sortantes directes ;
2. dépendances transitives bornées ;
3. dépendants entrants ;
4. `maxDepth=1` exclut un nœud à profondeur 2 ;
5. tous les chemins sont composés uniquement de `DEPENDS_ON` persistés ;
6. un chemin non résolu produit un warning sans disparition du chemin ;
7. aucun lien n'est créé pour un requirement uniquement proposé ;
8. Memory et SQLite produisent le même résultat ;
9. `./mvnw clean test` vert.
