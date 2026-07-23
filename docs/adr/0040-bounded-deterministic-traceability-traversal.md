# ADR-0040 — Traversée bornée et déterministe de la traçabilité

- Statut : **Acceptée — M4**
- Date : 23 juillet 2026
- Dépend de : ADR-0005, ADR-0010, ADR-0037, ADR-0038, ADR-0039
- Portée : M4-S4, requêtes directes, traversal et findPath

## Contexte

M4-S1 a stabilisé le modèle `TraceabilityLink`, M4-S2 sa persistance snapshot-scoped et M4-S3 la dérivation déterministe depuis le modèle normalisé.

Baseline intégrée :

```text
M4-S3 merge = 4b3bb5c79e65b8f1501b9949b49f4940294c4312
M4-S3 gate  = 167/167 PASS
```

S4 rend les liens navigables sans introduire de backend graphe, sans matérialiser d'arêtes inverses et sans transformer une traversée en nouvelle preuve sémantique.

## Décision

Ajouter un service applicatif provider/store-neutral au-dessus de `TraceabilityStore.outgoing/incoming` :

```text
TraceabilityTraversalDirection
  OUTGOING
  INCOMING
  BIDIRECTIONAL

TraceabilityTraversalService
  direct(...)
  traverse(...)
  findPath(...)
```

Résultats explicites :

```text
TraceabilitySubgraph
TraceabilityPath
TraceabilityPathStep
```

Aucune modification de schéma SQLite n'est nécessaire en S4.

## Snapshot comme frontière obligatoire

Toute requête conserve un `KnowledgeSnapshotId` explicite et ne lit que les links appartenant à ce snapshot via le port S2.

```text
traversal(snapshot A) != traversal(snapshot B)
```

Aucune fusion cross-snapshot n'est autorisée.

## Direction

La direction de traversal est une propriété de requête :

```text
OUTGOING      suit source -> target
INCOMING      parcourt target -> source
BIDIRECTIONAL peut emprunter les deux vues
```

Même en parcours inverse, le `TraceabilityLink` retourné conserve sa direction canonique persistée. Une vue inverse ne crée jamais une seconde arête ni une seconde preuve.

## Profondeur

`maxDepth` est obligatoire et strictement positif.

```text
maxDepth <= 0 -> rejet explicite
```

Le nœud de départ est à profondeur 0. Une arête n'est explorée que depuis un nœud dont la profondeur est strictement inférieure à `maxDepth`.

## Cycle safety

La traversée est un BFS borné avec suivi explicite des nœuds déjà découverts.

Les cycles restent visibles dans le sous-graphe lorsque leurs arêtes appartiennent à la frontière explorée, mais aucun cycle ne peut provoquer une exploration infinie.

## Déterminisme

Les voisins sont ordonnés de manière canonique par :

```text
next TraceabilityEntityRef
relation type
TraceabilityLinkId
```

Les nœuds et arêtes du sous-graphe sont ensuite retournés dans un ordre stable.

`findPath` utilise le même ordre et choisit donc de manière déterministe le premier plus court chemin BFS admissible.

## Filtres

Le filtre est un `Set<TraceabilityRelationType>` explicite.

Comme en S2 :

```text
empty set = toutes les relations
```

Aucun type arbitraire n'est accepté.

## Path explicable

`TraceabilityPathStep` conserve :

```text
TraceabilityLink persistedLink
from
into
```

`from/into` indiquent le sens de parcours pour cette étape, tandis que `persistedLink` conserve l'arête canonique réelle.

Ainsi un path bidirectionnel peut emprunter une arête à rebours sans falsifier son sens de preuve.

## Traversal != transitivity

Le fait qu'une relation soit traversable n'affirme aucune transitivité sémantique.

```text
A -R1-> B -R2-> C
```

peut produire un chemin de longueur 2, mais ne produit jamais :

```text
A -> C
```

Aucun `TraceabilityLink` synthétique n'est ajouté par S4.

## Direct / inverse

Les requêtes directes S4 réutilisent les primitives S2 :

```text
outgoing = canonical direct view
incoming = inverse query view over canonical persisted edge
```

Les résultats restent triés et snapshot-scoped.

## Frontières

S4 ne fait pas :

```text
external resolution
LINKS_TO_CODE / LINKS_TO_TEST enrichment
fuzzy matching
link derivation
link persistence mutation
transitive edge materialization
graph database
```

Les références externes/unresolved restent S5.

## Preuves S4

`TraceabilityTraversalContractTest` apporte **7 tests** :

1. direct/inverse/bidirectional conservent l'arête canonique persistée ;
2. traversal borné cycle-safe et aucune arête transitive synthétique ;
3. `findPath` choisit un plus court chemin déterministe ;
4. filtres relationnels respectés sur traversal/path ;
5. `maxDepth <= 0` rejeté ;
6. isolation stricte entre snapshots ;
7. Memory et SQLite produisent exactement le même subgraph/path.

## Critères d'acceptation

Les critères sont validés :

1. `maxDepth > 0` obligatoire ;
2. OUTGOING / INCOMING / BIDIRECTIONAL explicites ;
3. traversal snapshot-scoped ;
4. ordre déterministe ;
5. cycles bornés sans boucle infinie ;
6. filtres relationnels respectés ;
7. incoming/bidirectional ne créent aucune seconde arête ;
8. `findPath` retourne un plus court chemin déterministe ;
9. chaque step conserve le `TraceabilityLink` réel et son sens de parcours ;
10. aucun lien transitif synthétique n'est produit ;
11. Memory et SQLite exposent les mêmes résultats ;
12. aucune migration SQLite S4 ;
13. `\.\mvnw.cmd clean test` est vert.

## Preuve d'acceptation

Gate local Windows :

```text
.\mvnw.cmd clean test
javac release 21

TraceabilityTraversalContractTest      7/7 PASS
LayerDependencyTest                    2/2 PASS

Domain                                21 tests
Application                           61 tests
OpenSpec provider                     26 tests
Synthetic provider                     7 tests
SQLite store                           7 tests
Architecture tests                    52 tests
---------------------------------------------
TOTAL                                174/174 PASS
Failures                               0
Errors                                 0
Skipped                                0
BUILD SUCCESS
Total time                           15.256 s
```

Gate terminé le **23 juillet 2026 à 13:26:39 +02:00**.

Warnings connus et non bloquants uniquement :

```text
Xerial SQLite / JDK native-access
SLF4J NOP dans les tests d'architecture
```

Conclusion : **ADR-0040 acceptée ; M4-S4 validé techniquement. M4-S5 devient le prochain slice.**
