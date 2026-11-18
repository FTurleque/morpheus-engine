# Proposition : lire références externes et conflits de composition en un nombre fixe de requêtes (AUD-PRF-26)

## Why

Deux lectures SQLite gardent une requête par ligne parente. Constat `AUD-PRF-26` (Faible),
`docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026 sur `a04f32bd` et revérifié le
17/11/2026 sur `1a539c22` :

- `SqliteExternalReferenceStore.findByOwner` (`:89-115`) appelle `mapReference` pour chaque référence (`:104-107`) ;
  `mapReference` lit les attributs résolus par une requête par référence résolue (`resolvedAttributes`, `:266`,
  `:294-312`) et l'historique par une requête par référence (`history`, `:291`, `:314-338`). Coût :
  2 + K + K_résolues requêtes (K = références du propriétaire). `findByOwner` est appelé par chaque état d'orchestration
  (`ChangeOrchestrationStateService.java:186`) et par la résolution vivante
  (`LiveExternalReferenceResolutionService.java:47`).
- `SqliteCompositionStateStore.find` (`:51-71`) lit l'en-tête, les fournisseurs (`readProviders`) puis les conflits
  (`readConflicts`, `:186-210`), qui lit les candidats d'un conflit par une requête (`readCandidates`, `:202`,
  `:212-...`) dans la boucle du `ResultSet` parent. Coût revérifié : 3 + N requêtes (N = conflits) ; l'audit écrit
  2 + N en ne comptant pas les fournisseurs. L'état de composition est relu par les règles de couverture de la
  gouvernance (`DefaultPolicyFactResolver.java:198`) et par les surfaces de composition.

Le gate M19 de composition mesure ce second chemin à pleine charge sous son budget gelé
(`M19CompositionPerformanceGate`, `QUERY_BUDGET_NANOS`) : le risque est borné tant que ce gate tourne, d'où la
sévérité Faible. Les tables enfants ont déjà la clé qui sert une lecture groupée : `(snapshot_id, reference_id,
attribute_key)`, `(snapshot_id, reference_id, event_index)`, `(conflict_id, candidate_order)` (V006, V012) — le patron
de `SqliteSnapshotBusinessContentReader.readValuesByOwner` s'applique sans migration.

## What Changes

- La lecture des références externes d'un propriétaire exécute un nombre de requêtes qui ne dépend ni du nombre de
  références, ni du nombre de références résolues, ni de la longueur de leurs historiques.
- La lecture de l'état de composition d'un snapshot exécute un nombre de requêtes qui ne dépend ni du nombre de
  conflits ni du nombre de candidats.
- Les valeurs lues ne changent pas : attributs, historique dans l'ordre de `event_index`, candidats dans l'ordre de
  `candidate_order`, liste vide pour une référence sans historique.
- Deux tests de coût de la suite ordinaire comptent les requêtes préparées de chaque lecture.

## Capabilities

### New Capabilities
- `external-reference-and-conflict-read-cost` : ce que coûtent la lecture des références externes d'un propriétaire et
  celle de l'état de composition d'un snapshot, et ce qu'elles retournent.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `SqliteExternalReferenceStore` et `SqliteCompositionStateStore` (`com.morpheus.store.sqlite.adapter`) ;
deux tests de coût et de valeur dans `morpheus-store-sqlite` ; un test de parité ; la page durable
`docs/developer/specifications/external-reference-and-conflict-read-cost.md` (à créer).

**Exclus :**
- `findReference` (une seule référence, coût déjà constant) ;
- la mémoïsation de l'état de composition entre règles de gouvernance : `AUD-PRF-22`,
  `resolve-each-governance-report-once-per-evaluation` ;
- la lecture du contenu métier et l'écriture de traçabilité, qui tiennent (`AUD-PRF-31`, Info) ;
- le budget et la fixture du gate M19 de composition, inchangés.

## Acteurs et surfaces concernés

Indirectement : clients de `orchestration.state`, des surfaces de composition et de `policy.evaluate`
(`contracts/public-surfaces.tsv`). Aucune forme de réponse ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Deux requêtes enfants par référence dans `findByOwner` | existant vérifié | `SqliteExternalReferenceStore.java:104-107`, `:266`, `:291` |
| Une requête de candidats par conflit | existant vérifié | `SqliteCompositionStateStore.java:202`, `:212-...` |
| `find` coûte 3 + N, pas 2 + N | existant vérifié (écart avec l'audit) | `SqliteCompositionStateStore.java:51-71` |
| Clés primaires des tables enfants | existant vérifié | V006, V012 |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : forme de requête interne |

## Impact

`morpheus-store-sqlite` seulement ; un test de parité sous `morpheus-architecture-tests` (justification de PR exigée).
Aucun port, contrat, OpenAPI, schéma, migration ni ADR. Sans ordre imposé avec les autres changements du sprint ;
`assess-the-orchestration-state-of-a-change-once` et `resolve-each-governance-report-once-per-evaluation` en
profitent.

## Questions ouvertes

Aucune.
