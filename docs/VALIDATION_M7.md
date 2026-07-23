# Validation M7 — Synchronisation incrémentale et fraîcheur

Statut : **VALIDÉ ET INTÉGRÉ**

Date : 24 juillet 2026

## Question de sortie

> **MORPHEUS peut-il détecter de façon déterministe les changements de sources locales, appliquer une stratégie incrémentale fiable, conserver archives et état de synchronisation, exposer une fraîcheur explicable et basculer vers un full rebuild dès que la sûreté de l'incrémental n'est plus démontrable ?**

## Réponse

**OUI.**

Le gate local Windows final a validé le head exécutable exact :

```text
2e19ab104be18b98536eb871981d60e6b95e1e8c
```

L'intégration fonctionnelle finale a été vérifiée sur :

```text
PR #51
merge = c3c397f4e5a2c97b686c96cfa936e00ac29a52bf
```

## Baseline

```text
M6 final merge = 904058251829b0ae39b34cd9da25c2b8918851a6
M6 final gate  = 261/261 PASS
```

## Capacités M7 validées

```text
SourcePath canonique relatif
SourceFingerprint SHA-256 bytes
LocalSourceInventoryScanner
scan complet ou explicitement incomplet
mutation pendant hash détectée
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
baseline persistée seulement après succès
LocalSourceWatcher récursif
watcher sans suivi de symlink
WatchService OVERFLOW => FULL_REBUILD
SyncFreshness UNKNOWN/FRESH/STALE/REBUILD_REQUIRED
Memory == SQLite
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

## Preuves

### Application — 82/82 PASS

Le module Application contient notamment les 16 tests M7 :

```text
SourceSynchronizationCoreTest          14/14
SyncReliabilityFallbackTest             2/2
```

Preuves : path canonicalization, SHA-256, source-root selection, scan incomplete, diff complet, move unique/ambigu, initial full rebuild, incremental invalidation/archive, revision inconsistency/loss, watcher overflow, execution failure, freshness, WatchService event, revision opaque, baseline inconsistency.

### Architecture — 139/139 PASS

Les 5 tests `IncrementalSyncPersistenceContractTest` valident : Memory == SQLite, baseline/archives/freshness parity, SQLite reopen, pending rebuild reopen, successful full rebuild clears pending state, unknown project rejected.

### SQLite migration

`SqliteSchemaMigrationTest` reste à 4 tests et valide V008, 8 ledger entries, tables/indexes M7 et absence de colonne JSON.

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

Les warnings Xerial SQLite native-access et SLF4J NOP observés restent connus et non bloquants ; aucun test n'échoue.

## Audit post-gate

Après le head exécutable testé, les six commits suivants de PR #51 étaient exclusivement documentaires : roadmap, validation, plan M7 et ADR-0053/54/55. Aucun artefact exécutable n'a changé après le gate.

## Décision finale

**M7 satisfait sa question de sortie et est VALIDÉ ET INTÉGRÉ.**

MORPHEUS dispose désormais d'une stratégie de synchronisation incrémentale locale déterministe, persistante et conservatrice. Lorsqu'il ne peut plus démontrer la sûreté d'une mise à jour incrémentale, il expose la raison et impose un `FULL_REBUILD` plutôt que de fabriquer une continuité.

Prochain jalon : **M8 — Analyse des changements**.