# ADR-0043 — Recherche lexicale déterministe et pagination des requirements

- Statut : **Proposée — M5**
- Date : 23 juillet 2026
- Dépend de : ADR-0004, ADR-0006, ADR-0012, ADR-0034, ADR-0036
- Portée : M5-S1, première primitive de requête métier persistante

## Contexte

M4 est validé et intégré sur :

```text
main = d4a4c9f4816e42a8629d2f41cfe22703f53f210a
189/189 PASS
```

M0 a démontré qu'une recherche lexicale déterministe est suffisante pour le MVP et que la recherche sémantique n'est pas requise.

M3 a déjà fourni un stockage durable versionné de `Requirement` :

```text
VersionedRequirementStore
RequirementVersionRecord
CURRENT / PROPOSED
KnowledgeSnapshot ownership
SpecificationVersion ownership
```

M5-S1 doit donc produire `find_requirements` sans introduire un second index prématuré ni une nouvelle migration.

## Décision candidate

Ajouter un service applicatif provider/backend-neutral :

```text
RequirementQueryService
```

avec deux entrées explicites :

```text
findActive(projectId, query, pageRequest)
findSnapshot(snapshotId, query, pageRequest)
```

`findActive` sélectionne uniquement le snapshot ACTIVE du projet.

`findSnapshot` accepte uniquement :

```text
ACTIVE
RETIRED
```

et rejette :

```text
BUILDING
VALIDATING
READY
FAILED
```

## Occurrences requêtables

Une recherche M5-S1 ne porte que sur les occurrences :

```text
TemporalState.CURRENT
```

Les `PROPOSED` ne sont jamais incluses dans `find_requirements`.

Cela conserve :

```text
PROPOSED never leaks into CURRENT
```

## Champs lexicaux

Le corpus d'un requirement est strictement :

```text
key (si présent)
title
statement
```

La provenance reste retournée dans le `Requirement` mais n'est pas utilisée comme texte de recherche en S1.

## Normalisation

Pour chaque requête et chaque champ :

```text
Unicode lowercase via Locale.ROOT
trim
split on Unicode whitespace
ignore empty terms
```

La recherche est volontairement simple et explicable.

Aucune :

```text
stemming
fuzzy matching
edit distance
semantic embedding
LLM expansion
provider-specific tokenization
```

## Sémantique des termes

Tous les termes doivent être présents dans le corpus normalisé :

```text
term1 AND term2 AND ...
```

Chaque terme utilise une correspondance substring déterministe.

Une requête vide correspond à :

```text
all CURRENT requirements in addressed snapshot
```

## Ordre

Le résultat global avant pagination est ordonné uniquement par :

```text
RequirementId
```

Il n'existe aucun score ni ranking global en S1.

Cela évite de confondre :

```text
lexical filtering != ranking NEXUS
```

## Pagination

Introduire :

```text
PageRequest(offset, limit)
RequirementSearchPage
```

Règles :

```text
offset >= 0
1 <= limit <= 100
```

Le résultat conserve :

```text
items
offset
limit
totalMatches
hasMore
```

La pagination est appliquée **après** filtrage et tri déterministe.

## Snapshot historique

`findSnapshot` permet une lecture historique explicite d'un snapshot RETIRED sans réactivation.

Un snapshot technique non publié n'est pas une source de requête métier M5.

## Backend

S1 réutilise :

```text
SpecificationKnowledgeStore
VersionedRequirementStore
```

Aucune nouvelle table SQLite n'est ajoutée.

La sémantique observable doit être identique avec :

```text
MemorySpecificationKnowledgeStore
SqliteSpecificationKnowledgeStore + SqliteVersionedRequirementStore
```

## Frontières

S1 ne fait pas :

```text
semantic search
ranking pondéré
FTS SQLite spécifique
index Lucene
stemming
fuzzy matching
context aggregation
query des autres familles métier
JSON public
CLI/MCP/API
```

## Preuves attendues

Le gate S1 doit démontrer au minimum :

1. `findActive` utilise seulement le snapshot ACTIVE ;
2. `PROPOSED` ne fuit jamais dans les résultats ;
3. `findSnapshot` accepte ACTIVE/RETIRED et rejette les états non publiés ;
4. recherche case-insensitive sur key/title/statement ;
5. termes multiples en AND ;
6. requête vide retourne tous les CURRENT ;
7. ordre stable par `RequirementId` ;
8. offset/limit appliqués après tri ;
9. limites invalides rejetées ;
10. `totalMatches` et `hasMore` corrects ;
11. Memory et SQLite produisent le même résultat ;
12. SQLite reopen conserve le même résultat ;
13. aucune migration SQLite S1 ;
14. `\.\mvnw.cmd clean test` vert.

## Preuve d'acceptation

À compléter uniquement après gate local complet vert.
