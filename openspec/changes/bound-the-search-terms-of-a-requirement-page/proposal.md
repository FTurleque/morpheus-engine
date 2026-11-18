# Proposition : borner les termes d'une recherche d'exigences (AUD-SEC-23)

## Why

Le nombre de termes d'une recherche d'exigences n'est pas borné. Chaque terme ajoute à la requête SQL un appel de la
fonction Java `morpheus_requirement_corpus` par ligne CURRENT du snapshot, évalué deux fois (total et page). Constat
`AUD-SEC-23` (Moyenne) de l'audit du 17/11/2026 (`docs/project/audits/2026-11-17/findings.md`).

Revérifié sur `1a539c22` :

- `RequirementSearchQuery.terms()` découpe le texte sur les blancs, sans plafond ni dédoublonnage ;
- `SqliteVersionedRequirementStore.pageSql` ajoute un `AND instr(morpheus_requirement_corpus(requirement_key, title,
  statement), ?) > 0` par terme dans le CTE `matches`, lu par le total et par la page ; `CorpusFunction` reconstruit et
  normalise le corpus à chaque appel ;
- la seule borne amont est `HttpQueryBudget.MAX_PARAMETER_VALUE_BYTES` sur la valeur brute ; `a+a+…` en donne des
  milliers de termes. L'OpenAPI déclare `maxLength: 8192` pour `query`.

La route `GET /api/v1/projects/{projectId}/requirements` est ouverte au rôle READ en remote ; la même recherche est
servie par la CLI (`requirements find`) et le MCP (`find_requirements`). Au-delà d'une profondeur d'expression, SQLite
échoue en erreur SQL dont le statut HTTP n'est pas un refus de requête nommé (à mesurer, tâche 1.1). Quelques requêtes
concurrentes occupent les workers de base au-delà de l'échéance READ, la requête SQLite ne réagissant pas à
l'interruption.

## What Changes

- Une recherche d'exigences porte au plus un nombre déclaré de termes distincts ; au-delà, elle est refusée par nom
  comme requête invalide, avant toute lecture de la base, sur les trois transports.
- Les termes répétés sont dédoublonnés : le résultat ne change pas (chaque terme doit être trouvé).
- Le corpus d'une ligne est calculé une fois par ligne, quel que soit le nombre de termes.
- L'OpenAPI documente le refus (description du paramètre `query` et réponse `400`).

## Capabilities

### New Capabilities

### Modified Capabilities
- `requirement-page-reads` : le nombre de termes d'une recherche est borné, et le coût d'une ligne ne croît plus avec
  le nombre de termes.

## Périmètre et exclusions

**Inclus :** `RequirementSearchQuery` (`morpheus-application`, `com.morpheus.application.query`),
`SqliteVersionedRequirementStore` (`morpheus-store-sqlite`), le classement HTTP de l'erreur, l'OpenAPI
`docs/developer/reference/openapi/morpheus-v1.yaml`, les tests de parité des stores, la page durable.

**Exclus :** l'annulation d'une requête SQLite à l'échéance (`DatabaseCallWorkers`), qui vaut pour toute lecture
longue ; les autres paramètres de requête, déjà bornés par `HttpQueryBudget`.

## Acteurs et surfaces concernés

Client HTTP local, client remote de rôle READ, utilisateur de la CLI `requirements find`, client MCP de
`find_requirements`.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Aucun plafond de termes | existant vérifié | `RequirementSearchQuery.terms()` |
| Un appel de fonction Java par terme et par ligne, total et page | existant vérifié | `SqliteVersionedRequirementStore.pageSql`, `CorpusFunction` |
| Statut HTTP rendu au-delà de la limite d'expression de SQLite | question ouverte | à mesurer par le test de reproduction (l'audit n'a pas pu l'exécuter) |
| Valeur du plafond de termes | proposition à valider | design.md, D1 |

## Impact

`morpheus-application`, `morpheus-store-sqlite`, `morpheus-api` (classement d'erreur) ; OpenAPI modifiée (gouvernance,
justification de PR) ; aucune ligne de `contracts/public-surfaces.tsv` ne change (même capacité `requirement.find`).
Parité `store-memory` / `store-sqlite` à tenir (`*PersistenceParityTest`).

## Questions ouvertes

- Valeur du plafond de termes (design.md, D1), à confirmer à la revue ; elle ne change ni l'approche ni les tâches.
