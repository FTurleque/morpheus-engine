# M7 — Plan d'exécution détaillé

Statut : **M7 VALIDÉ — intégration finale portée par PR #51**

Dernière mise à jour : 24 juillet 2026

Base :

```text
M6 final merge = 904058251829b0ae39b34cd9da25c2b8918851a6
M6 final gate  = 261/261 PASS
```

Issue : **#50**  
PR : **#51**  
Branche : `m7/incremental-sync-freshness`

Head exécutable validé :

```text
2e19ab104be18b98536eb871981d60e6b95e1e8c
```

## Question de sortie

> **MORPHEUS peut-il détecter de façon déterministe les changements de sources locales, appliquer une stratégie incrémentale fiable, conserver archives et état de synchronisation, exposer une fraîcheur explicable et basculer vers un full rebuild dès que la sûreté de l'incrémental n'est plus démontrable ?**

**Réponse : OUI.**

## Contrats validés

```text
SourcePath
SourceFingerprint
SourceInventory
SourceInventoryScanResult
LocalSourceInventoryScanner
SourceInventoryDiff
SourceInventoryDiffer
SourceWatchSignal
WatchSignalPolicy
LocalSourceWatcher
SyncPlan
SourceArchiveRecord
ProjectSyncState
SyncStateStore
MemorySyncStateStore
SqliteSyncStateStore
IncrementalSyncService
SyncFreshness
SyncFreshnessService
```

## Source inventory

```text
path relatif canonique
SHA-256(content)
sizeBytes
optional sourceRevision
capturedAt explicite
scan incomplet => jamais de baseline
mutation pendant hash => scan incomplet
```

`sourceRevision` est opaque : aucune comparaison lexicale/numérique n'est utilisée.

## Diff

```text
ADDED
MODIFIED
DELETED
MOVED
UNCHANGED
```

Un move est reconnu uniquement pour une paire unique `1 deleted <-> 1 added` de même SHA-256 + taille.

```text
ambiguïté => pas de guessing => FULL_REBUILD
```

## Fallback full rebuild

Raisons explicites :

```text
NO_BASELINE
SCAN_INCOMPLETE
WATCH_OVERFLOW
AMBIGUOUS_MOVE
REVISION_INCONSISTENCY
REVISION_SIGNAL_LOST
BASELINE_INCONSISTENT
PREVIOUS_REBUILD_PENDING
EXECUTION_FAILED
FORCED
```

## Invalidation / archives

```text
ADDED    => refresh new
MODIFIED => invalidate + refresh
DELETED  => invalidate old + archive DELETED
MOVED    => invalidate old + refresh new + archive MOVED
```

En `FULL_REBUILD` : toutes les anciennes sources sont invalidées et toutes les sources du scan complet sont à rafraîchir.

Archive source : immutable, revision de l'ancienne baseline conservée.

## Persistance

ADR-0054 introduit `V008` :

```text
sync_state
sync_inventory_entries
sync_source_archives
```

Parité : Memory + SQLite.  
Reopen SQLite couvert.  
Aucun JSON métier générique.

## Watcher

`LocalSourceWatcher` utilise `java.nio.file.WatchService` récursivement.

```text
CREATE / MODIFY / DELETE => rescan
OVERFLOW => FULL_REBUILD
```

Le watcher ne suit pas les symlinks pour enregistrer des répertoires et n'est jamais la source de vérité ; le scanner SHA-256 l'est.

## Fraîcheur

```text
UNKNOWN
FRESH
STALE
REBUILD_REQUIRED
```

Calcul avec `now` et `maxAge` fournis explicitement. Aucun clock read caché.

## ADR

```text
ADR-0053 — Acceptée — source inventory/diff
ADR-0054 — Acceptée — persisted state/archive/freshness
ADR-0055 — Acceptée — watcher/fallback
```

Acceptées après gate local final M7.

## Preuves

Nouveaux tests M7 : **21**.

```text
SourceSynchronizationCoreTest              14/14
SyncReliabilityFallbackTest                  2/2
IncrementalSyncPersistenceContractTest       5/5
```

`SqliteSchemaMigrationTest` conserve 4 tests et valide V008 et 8 migrations immuables.

## Gate final officiel

```text
MORPHEUS Domain          21/21 PASS
MORPHEUS Application     82/82 PASS
OpenSpec Provider        26/26 PASS
Synthetic Provider        7/7 PASS
SQLite Store              7/7 PASS
Architecture Tests      139/139 PASS

TOTAL                   282/282 PASS
Failures                   0
Errors                     0
Skipped                    0
BUILD SUCCESS
Total time               21.141 s
Finished 2026-07-24T00:22:11+02:00
```

## Invariants finaux

```text
reliability > incremental speed
content SHA-256 > mtime/size
mtime/size = stabilité du scan seulement
sourceRevision opaque
same revision + different inventory => FULL_REBUILD
revision signal lost => FULL_REBUILD
baseline inconsistency => FULL_REBUILD
ambiguous move => FULL_REBUILD
watcher != source of truth
watch event => rescan
OVERFLOW => FULL_REBUILD
archive != published-history deletion
invalidation != snapshot deletion
baseline persisted only after complete(success)
failed execution => rebuild pending
freshness uses explicit now/maxAge
no LLM
no semantic matching
no fuzzy rename
```

## Porte finale

Gate local Windows : **PASS**.

```text
282/282 PASS
Architecture 139/139 PASS
```

M7 est **VALIDÉ**. Après intégration de PR #51, l'issue #50 peut être clôturée et M8 devient le prochain jalon.