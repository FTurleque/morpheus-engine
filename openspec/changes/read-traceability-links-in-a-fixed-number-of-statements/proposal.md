# Proposition : lire les liens de traçabilité en un nombre fixe de requêtes (AUD-PRF-24)

## Why

Chaque lecture des liens d'une extrémité exécute une requête de preuves par lien retourné et revérifie l'existence du
snapshot à chaque appel. Constat `AUD-PRF-24` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré par
l'audit du 17/11/2026 sur `a04f32bd` et revérifié le 17/11/2026 sur `1a539c22`, ligne pour ligne :

- `SqliteTraceabilityStore.endpointQuery` appelle `requireSnapshot(snapshotId)` (`:180`, `SELECT 1 FROM
  knowledge_snapshots`) avant chaque lecture, puis `mapLink(result)` pour chaque ligne (`:191`), qui appelle
  `evidence(linkId)` (`:414-417`) : une requête `SELECT evidence_id FROM traceability_link_evidence WHERE link_id = ?`
  préparée et exécutée dans la boucle du `ResultSet` parent (`:441-453`).
- L'écriture lit pourtant les preuves en tranches (`findDefinitions`, `:304-336`), et son coût est gardé par
  `SqliteTraceabilityBatchWriteCostTest` ; aucun test de coût ne garde la lecture.

Une lecture `outgoing` / `incoming` coûte donc 2 + L requêtes (L = liens retournés). Toutes les traversées
(`TraceabilityTraversalService`, `ChangeAnalysisService`, contextes de changement et de requirement) et les services
qualité multiplient ce coût par nœud. Le gate M19 de traversée mesure ce chemin avec exactement une preuve par lien et
sous-estime donc un projet à plusieurs preuves par lien.

## What Changes

- Une lecture des liens sortants ou entrants d'une extrémité exécute un nombre de requêtes qui ne dépend ni du nombre
  de liens retournés ni du nombre de preuves par lien.
- L'existence du snapshot n'est vérifiée que lorsque la lecture ne retourne aucun lien ; un snapshot inconnu reste
  refusé par son nom.
- Les liens retournés, leurs preuves et leur ordre ne changent pas, sur les deux stores.
- Un test de coût de la suite ordinaire, à côté de `SqliteTraceabilityBatchWriteCostTest`, compte les requêtes préparées
  d'une lecture.

## Capabilities

### New Capabilities
- `traceability-link-read-cost` : ce que coûte la lecture des liens d'une extrémité, et ce qu'elle retourne.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `SqliteTraceabilityStore` (`com.morpheus.store.sqlite.adapter`) ; un test de coût et des tests de valeur
dans `morpheus-store-sqlite` ; un test de parité mémoire / SQLite ; la page durable
`docs/developer/specifications/traceability-link-read-cost.md` (à créer).

**Exclus :**
- le nombre de lectures que font les services (`AUD-PRF-21`, `AUD-PRF-23` :
  `assess-the-orchestration-state-of-a-change-once`, `find-linked-quality-subjects-in-one-traceability-read`) ;
- le filtre par type de relation, déjà appliqué en base (`sqlite-read-shaping`) ;
- l'écriture en lot, qui tient et n'est pas touchée (`AUD-PRF-31`, Info) ;
- la fixture du gate M19 de traversée, qui reste à une preuve par lien : la changer déplacerait un budget existant
  (`performance-budget-coverage` › A new budget SHALL NOT move an existing one).

## Acteurs et surfaces concernés

Indirectement, tout client d'une lecture qui traverse la traçabilité : `trace-requirement`, `change-context`,
`analyze change`, rapport qualité, état d'orchestration, sur CLI, MCP et HTTP. Aucune forme de réponse ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Une requête de preuves par lien, dans la boucle du résultat parent | existant vérifié | `SqliteTraceabilityStore.java:191`, `:414-417`, `:441-453` |
| Le snapshot est revérifié à chaque lecture | existant vérifié | `SqliteTraceabilityStore.java:180`, `:455-466` |
| Le gate M19 de traversée met une preuve par lien | existant cité par l'audit | `M19TraceabilityPerformanceGate.java:353` (audit) |
| Lecture jointe ou en tranches des preuves | proposition à valider | design.md, D1 |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : forme de requête interne au store |

## Impact

`morpheus-store-sqlite` seulement (le store mémoire ne change pas de comportement) ; un test sous
`morpheus-architecture-tests` pour la parité (justification de PR exigée). Aucun port, contrat, OpenAPI, schéma ni ADR ;
aucune migration (`traceability_link_evidence` a pour clé `(link_id, evidence_id)`, V005).

Sans ordre imposé ; `find-linked-quality-subjects-in-one-traceability-read` touche la même classe (lectures
ensemblistes) : le second livré rebase sur le premier et réutilise sa forme de lecture des preuves.

## Questions ouvertes

Aucune.
