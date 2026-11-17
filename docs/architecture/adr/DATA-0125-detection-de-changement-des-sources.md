---
id: DATA-0125
title: "Détecter les changements de source par inventaire SHA-256, reconstruire en entier en cas de doute"
domain: data
rules: true
files: ["morpheus-application/src/main/java/com/morpheus/application/sync/SyncFreshness*.java", "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/adapter/SqliteSyncStateStore.java"]
---

# Détecter les changements de source par inventaire SHA-256, reconstruire en entier en cas de doute

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0053, ADR-0054, ADR-0055

## Context

Une synchronisation doit savoir ce qui a changé dans les sources locales d'un projet depuis la dernière baseline
publiée, sans jamais présenter comme incrémental un état qu'elle ne sait pas démontrer. Trois questions se posent :
comment observer les sources, où garder ce qui a été observé, et quel rôle donner à la surveillance du système de
fichiers.

Options écartées pour l'observation : une empreinte fondée sur la date de modification ou la taille seule (elle
manque une réécriture de même taille et voit un changement là où il n'y en a pas), un rapprochement flou des
renommages et toute heuristique textuelle (elles devinent).

L'état de synchronisation porte le drapeau de reconstruction en attente, qui force la synchronisation suivante hors
du chemin incrémental : c'est un état d'autorité. Écrit en dernier écrivain gagnant, un plan dépassé effacerait le
drapeau posé par un plus récent. Options écartées pour le protéger : un verrou par projet dans l'application (il ne
couvre pas deux processus, CLI et API, sur la même base), un horodatage comme jeton (deux tentatives peuvent partager
un instant), un upsert conditionnel dont le résultat n'est pas inspecté (il ne garde rien).

`java.nio.file.WatchService` peut perdre des événements (`OVERFLOW`) et n'observe que le processus qui l'ouvre : il ne
peut pas être la preuve de l'état des sources.

Les raisons de reconstruction complète couvrent l'absence de baseline, un inventaire incomplet ou en échec,
l'`OVERFLOW` du watcher, un déplacement ambigu, une même `sourceRevision` avec un inventaire différent, une
`sourceRevision` présente puis perdue, une baseline incohérente, une reconstruction précédente en attente, un échec
d'exécution et une demande explicite ; la liste exacte est l'énumération `FullRebuildReason`. Un déplacement ambigu
n'est jamais deviné.

Un `ProjectSyncState` porte la dernière tentative, le dernier succès et son mode, le dernier changement observé, la
`sourceRevision`, la raison de reconstruction en attente, le nombre de sources et la révision. Un plan écrit contre sa
propre révision, `SyncPlan.stateRevision`, la révision après l'enregistrement de la tentative, pour qu'un plan dépassé
ne réécrive pas l'état d'un plus récent ; la panne de stockage réessayée ne change pas la révision. En SQLite, la
création est un `INSERT … ON CONFLICT DO NOTHING` et la mise à jour un `UPDATE … WHERE project_id = ? AND revision = ?`
dont le nombre de lignes est vérifié ; la révision s'écrit dans la même transaction que l'inventaire et les archives,
dans les tables `sync_state`, `sync_inventory_entries` et `sync_source_archives`.

La fraîcheur est calculée, jamais lue d'une horloge cachée. Ses états :

| État | Condition |
|---|---|
| `UNKNOWN` | aucune synchronisation réussie et aucune reconstruction en attente |
| `FRESH` | âge depuis le dernier succès inférieur ou égal à l'âge maximal |
| `STALE` | âge depuis le dernier succès supérieur à l'âge maximal |
| `REBUILD_REQUIRED` | une raison de reconstruction en attente est présente |

`REBUILD_REQUIRED` domine `FRESH` et `STALE` : une baseline récente n'est pas présentée comme saine quand une tentative
plus récente a établi qu'une reconstruction complète est requise. Le watcher est une optimisation locale ; ses signaux
normalisés sont `CREATE`, `MODIFY`, `DELETE` et `OVERFLOW`.

Un `SourceInventory` porte le projet, l'instant de capture et une `sourceRevision` facultative. Les classes du diff
sont `ADDED`, `MODIFIED`, `DELETED`, `MOVED` et `UNCHANGED`.
`ProjectSyncState.revision` vaut 0 avant la première écriture et augmente de un à chaque écriture.

## Decision

1. **Inventaire** : `SourceInventory`, entrées triées par `SourcePath`, chacune avec sa taille et son
   `SourceFingerprint`, SHA-256 des octets.
2. **Révision opaque** : deux `sourceRevision` ne se comparent que par égalité.
3. **Diff** : `SourceInventoryDiffer` classe chaque source par `SourceInventoryDiff.ChangeKind`, par chemin. `MOVED`
   exige un unique ajout de même empreinte et taille qu'une suppression ; plusieurs candidats : reconstruction complète.
4. **Doute** : toute sûreté incrémentale non démontrée planifie `SyncPlan.SyncMode.FULL_REBUILD` avec sa
   `SyncPlan.FullRebuildReason`, jamais en silence.
5. **État** : `SyncStateStore` garde par projet un `ProjectSyncState`, l'inventaire et des `SourceArchiveRecord`
   immuables et ordonnés des sources supprimées ou déplacées ; mémoire et SQLite à l'identique, tables dédiées sans
   JSON générique. Les invalidations, explicites dans le plan, ne suppriment ni historique publié ni snapshot.
6. **Révision attendue** : `recordAttempt` et `commitSuccessfulSync` prennent la `ProjectSyncState.revision` lue et
   rendent la suivante ; un écart lève `SyncStateConflictException`, distinct d'une panne. Révision et inventaire
   s'écrivent ensemble (transaction SQLite, moniteur en mémoire).
7. **Plan** : `IncrementalSyncService.complete` et `fail` écrivent contre `SyncPlan.stateRevision`. Sur conflit,
   `complete` ne réessaie pas et ne pose pas `BASELINE_INCONSISTENT` ; une panne est réessayée une fois.
8. **Fraîcheur** : `SyncFreshnessService.assess` reçoit `now` et l'âge maximal ; `REBUILD_REQUIRED` si une raison de
   reconstruction attend, sinon `UNKNOWN` sans succès, `FRESH` jusqu'à l'âge maximal, `STALE` au-delà.
9. **Watcher** : `LocalSourceWatcher`, facultatif, émet des `SourceWatchSignal` qui déclenchent ou bornent un scan ;
   seul le scan SHA-256 prouve l'état. Un scan manuel décide comme watcher plus scan.

## Do's and Don'ts

### Do

- Calculer l'empreinte d'une source par SHA-256 de ses octets.
- Trier les entrées d'inventaire, les changements du diff, les invalidations et les archives par chemin canonique.
- Planifier `FULL_REBUILD` avec une `FullRebuildReason` dès que la sûreté de l'incrémental n'est pas démontrée.
- Écrire l'état de synchronisation en passant la révision lue, et laisser `SyncStateConflictException` remonter.
- Passer `now` et l'âge maximal en paramètres de tout calcul de fraîcheur.
- Garder les sources supprimées ou déplacées comme `SourceArchiveRecord` immuables.

### Don't

- Ne pas fonder une empreinte sur la date de modification ou la taille seule.
- Ne pas rapprocher un renommage par similarité, ni deviner entre plusieurs candidats de même contenu.
- Ne pas comparer deux `sourceRevision` autrement que par égalité.
- Ne pas écrire l'état de synchronisation par upsert aveugle (`ON CONFLICT DO UPDATE`).
- Ne pas réessayer ni marquer `BASELINE_INCONSISTENT` un plan dont l'écriture est refusée pour conflit de révision.
- Ne pas lire `Instant.now()` dans le calcul de fraîcheur.
- Ne pas traiter un signal du watcher comme la preuve d'un changement, ni sauter le scan qui le suit.
- Ne pas stocker l'état de synchronisation dans une colonne JSON générique.

## Consequences

### Positives

- Un reçu de synchronisation ne peut pas annoncer un incrémental que le diff ne démontre pas.
- Deux synchronisations concurrentes du même projet, même depuis deux processus, ne s'effacent pas : l'une échoue
  explicitement (HTTP `409`, `STATE_CONFLICT`).
- La fraîcheur est reproductible : le même état et le même `now` donnent le même verdict.

### Négatives

- Le calcul du SHA-256 lit chaque source en entier à chaque scan.
- Un projet qui déplace plusieurs fichiers de contenu identique paie une reconstruction complète.

### Risques

- `fail` appelé sur un plan dépassé lève le conflit à la place de la panne d'origine ; un appelant qui avale le
  conflit perd la cause première.
- Le watcher n'est branché sur aucune surface livrée ; s'il l'est, il doit garder le scan comme preuve.

## Compliance and Enforcement

- Règle Archgate `DATA-0125-detection-de-changement-des-sources.rules.ts` : `sync-freshness-reads-no-clock`, `sync-state-has-no-blind-upsert`.
- `morpheus-application/src/test/java/com/morpheus/application/sync/SourceSynchronizationCoreTest.java` : empreinte
  SHA-256 des seules racines choisies, diff stable, déplacement unique reconnu et ambigu jamais deviné, raisons de
  reconstruction (watcher `OVERFLOW`, scan incomplet, révision incohérente ou perdue), fraîcheur à `now` explicite,
  politique des signaux du watcher.
- `morpheus-application/src/test/java/com/morpheus/application/sync/SyncReliabilityFallbackTest.java` : révision de
  source opaque, baseline incohérente, retry unique, `BASELINE_INCONSISTENT` après publication.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SyncStateRevisionContractTest.java` : garde de
  révision sur les deux adaptateurs (drapeau non effacé, commit refusé sans effet, première écriture unique,
  révision +1, plan dépassé au `complete` et au `fail`).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/IncrementalSyncPersistenceContractTest.java` :
  parité mémoire/SQLite des plans, de l'état, de l'inventaire, des archives et de la fraîcheur, réouverture SQLite,
  reconstruction en attente.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/runtime/SqliteSyncStateRevisionMigrationTest.java` : une base
  existante reçoit la révision et reste inscriptible.
- Revue : absence de colonne JSON générique dans les migrations de synchronisation, et rôle de simple déclencheur du
  watcher s'il est branché sur une surface.

## References

- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [ADR-0036](DATA-0036-published-history-comparison-and-logical-rollback.md) — historique publié.
- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) et
  [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — SQLite et migrations.
- [ADR-0144](DATA-0144-publication-complete-cli-et-http.md) — ce qu'une synchronisation exécute et ce que son reçu
  en dit.
- [ADR-0144](DATA-0144-publication-complete-cli-et-http.md) — synchronisation HTTP.
- [VALIDATION_M7](../../project/validation/VALIDATION_M7.md).
