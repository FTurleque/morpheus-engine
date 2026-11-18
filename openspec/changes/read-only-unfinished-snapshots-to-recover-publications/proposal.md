# Proposition : ne lire que les snapshots inachevés pour reprendre les publications interrompues (AUD-PRF-29)

## Why

Avant de servir, chaque commande CLI qui travaille sur des snapshots publiés, le serveur MCP et le serveur HTTP local
reprennent les publications qu'un processus précédent a laissées en cours. Cette reprise relit tout l'historique des
snapshots de chaque projet pour n'en garder que les états techniques `BUILDING` et `VALIDATING` :
`RuntimeSnapshotRecovery.recoverAll` liste les projets, puis `SnapshotRecoveryService.recoverStaleCandidates` appelle
`store.listSnapshots(projectId)` et filtre en Java. Côté SQLite, cette lecture est
`WHERE project_id = ? ORDER BY created_at, id`, sans filtre d'état, alors que l'index
`idx_knowledge_snapshots_project_state (project_id, state)` existe depuis `V001`. Aucun snapshot n'est jamais supprimé
(`PublishedHistoryRetentionPolicy.KEEP_ALL_PUBLISHED`) : le coût croît avec chaque publication, sur un chemin
interactif.

Preuve : constat `AUD-PRF-29` (Faible, sprint 6) de `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd` et revérifié le 17/11/2026 sur `1a539c22` : 21 appels de `CliRuntime.recovering(` dans
`morpheus-cli/src/main` ; `CliRuntime.recovering`, `MorpheusMcpRuntime.recoverInterruptedPublications` et
`MorpheusLocalHttpServerBootstrap` construisent `RuntimeSnapshotRecovery` ; `SnapshotRecoveryService.isRecoverable`
filtre l'état et la date en Java ; `SqliteSpecificationKnowledgeStore.listSnapshots` ne filtre pas l'état.

## What Changes

- Le port `SpecificationKnowledgeStore` offre une lecture des snapshots d'un projet aux états `BUILDING` ou
  `VALIDATING`, dans l'ordre de `listSnapshots`. Les deux stores livrés l'implémentent eux-mêmes ; côté SQLite, la
  requête filtre l'état et est servie par `idx_knowledge_snapshots_project_state`.
- `SnapshotRecoveryService` lit ses candidats par cette lecture ; le seuil de date reste appliqué par le service.
- Le comportement de la reprise ne change pas : mêmes snapshots passés à `FAILED`, mêmes courses signalées, mêmes
  événements opérationnels et métriques, même ordre des projets.
- Aucun schéma, aucune migration, aucune surface publique ne change.

## Capabilities

### New Capabilities
- `interrupted-publication-recovery` : la reprise des publications interrompues — quels snapshots elle lit, lesquels
  elle fait échouer, avec quel résultat, et ce que chaque store livré doit fournir pour elle.

### Modified Capabilities

Aucune.

## Périmètre et exclusions

**Inclus :** le port `SpecificationKnowledgeStore` (paquet `com.morpheus.application.store`), `SnapshotRecoveryService`
(paquet `com.morpheus.application.snapshot`), `MemorySpecificationKnowledgeStore`, `SqliteSpecificationKnowledgeStore`,
leurs tests, la garde de plan de requête du store SQLite, la page durable de la capacité nouvelle.

**Exclus :**
- la fréquence de la reprise (une fois par commande CLI, une fois au démarrage des serveurs) : décision en vigueur,
  que le constat ne conteste pas ;
- une requête unique tous projets confondus : sans index qui commence par l'état, elle parcourrait la table
  (design.md, D2) ;
- le filtre de date en SQL : `created_at` est le texte d'`Instant.toString()`, dont l'ordre textuel n'est pas l'ordre
  du temps (design.md, D3) ;
- la méthode par défaut de `listSnapshots`, qui lève `UnsupportedOperationException` : hors du constat.

## Acteurs et surfaces concernés

L'utilisateur de la CLI, dont chaque commande sur des snapshots publiés paie la reprise ; les serveurs HTTP local et MCP
au démarrage. Aucune réponse ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| La reprise lit tout l'historique de chaque projet | existant vérifié | `SnapshotRecoveryService.recoverStaleCandidates`, `SqliteSpecificationKnowledgeStore.listSnapshots` |
| 21 commandes CLI passent par la reprise | existant vérifié | `grep -rn "CliRuntime.recovering(" morpheus-cli/src/main \| wc -l` = 21, le 17/11/2026 |
| L'index `(project_id, state)` existe | existant vérifié | `V001__foundation.sql`, `idx_knowledge_snapshots_project_state` |
| Aucun snapshot n'est supprimé | existant vérifié | `PublishedHistoryRetentionPolicy.KEEP_ALL_PUBLISHED` |
| Le filtre de date reste en Java | décision | design.md, D3 |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : lecture ajoutée à un port, sans contrat public ni donnée touchés |

## Impact

- `morpheus-application` (port, service, doubles de test qui implémentent le port), `morpheus-store-memory`,
  `morpheus-store-sqlite` ; tests de ces modules et des contrats M19 de reprise.
- Ordre du sprint 6 : ce changement suit le sprint 5 de l'audit (`AUD-PRF-21` à `AUD-PRF-26`), qui modifie les mêmes
  fichiers de `morpheus-store-sqlite`. Il n'a pas de dépendance avec les autres changements du sprint 6.
- Aucun test d'architecture modifié, aucune ligne de `contracts/public-surfaces.tsv`, aucun OpenAPI.

## Questions ouvertes

Aucune.
