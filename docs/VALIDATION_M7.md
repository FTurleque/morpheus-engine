# Validation M7 — Synchronisation incrémentale et fraîcheur

Statut : **CANDIDAT — gate local final en attente**

Date : 23 juillet 2026

## Question de sortie

> **MORPHEUS peut-il détecter de façon déterministe les changements de sources locales, appliquer une stratégie incrémentale fiable, conserver archives et état de synchronisation, exposer une fraîcheur explicable et basculer vers un full rebuild dès que la sûreté de l'incrémental n'est plus démontrable ?**

## Réponse candidate

**OUI, sous réserve du gate local final M7.**

## Baseline

```text
M6 final merge = 904058251829b0ae39b34cd9da25c2b8918851a6
M6 final gate  = 261/261 PASS
```

## Capacités M7 implémentées

```text
SourcePath canonique relatif
SourceFingerprint SHA-256 bytes
LocalSourceInventoryScanner
scan complet ou explicitement incomplet
SourceInventory + sourceRevision opaque
SourceInventoryDiffer
ADDED / MODIFIED / DELETED / MOVED / UNCHANGED
move unique seulement
move ambigu => FULL_REBUILD
SyncPlan + invalidation + refresh set
SourceArchiveRecord
ProjectSyncState
SyncStateStore
MemorySyncStateStore
SqliteSyncStateStore
V008 sync state/source inventory/source archives
IncrementalSyncService prepare/complete/fail
LocalSourceWatcher récursif
WatchService OVERFLOW => FULL_REBUILD
SyncFreshness UNKNOWN/FRESH/STALE/REBUILD_REQUIRED
SQLite reopen
```

## Politique de full rebuild

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

Aucune de ces conditions n'est silencieuse.

## Invariants

```text
reliability > incremental speed
fingerprint = SHA-256(content), jamais mtime seul
mtime/size servent uniquement à détecter une mutation pendant le scan
sourceRevision est opaque : égalité seulement, aucun ordre supposé
same revision + changed inventory => full rebuild
revision précédemment disponible puis absente => full rebuild
unique 1:1 content match => MOVED
ambiguous move => aucun guessing + full rebuild
watcher = trigger, jamais source de vérité
watch event => rescan SHA-256
OVERFLOW => full rebuild
archive != suppression historique publié
invalidation != suppression snapshot
baseline enregistrée seulement après complete(success)
fail => rebuild pending
freshness utilise now/maxAge explicites
```

## Persistance V008

```text
sync_state
sync_inventory_entries
sync_source_archives
```

Aucun JSON métier générique.

## Preuves ajoutées

### Application — 16 tests

```text
SourceSynchronizationCoreTest          14
SyncReliabilityFallbackTest             2
```

Preuves : path canonicalization, SHA-256, source-root selection, scan incomplete, diff complet, move unique/ambigu, initial full rebuild, incremental invalidation/archive, revision inconsistency/loss, watcher overflow, execution failure, freshness, WatchService event, revision opaque, baseline inconsistency.

### Architecture — 5 tests

```text
IncrementalSyncPersistenceContractTest  5
```

Preuves : Memory == SQLite, baseline/archives/freshness parity, SQLite reopen, pending rebuild reopen, successful full rebuild clears pending state, unknown project rejected.

### SQLite migration

`SqliteSchemaMigrationTest` reste à 4 tests et valide désormais V008, 8 ledger entries, tables/indexes M7 et absence de colonne JSON.

## Gate attendu

Baseline M6 : `261/261`.

Nouveaux tests M7 : `21`.

```text
MORPHEUS Application       attendu : 82 tests
Architecture Tests         attendu : 139 tests
TOTAL                      attendu : 282/282
```

La réponse de sortie ne devient définitive qu'après `BUILD SUCCESS` sur `./mvnw clean test` / `.\mvnw.cmd clean test`.
