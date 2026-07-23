# M7 — Plan d'exécution détaillé

Statut : **M7 actif — implémentation intégrale dans PR #51 Draft**

Dernière mise à jour : 23 juillet 2026

Base :

```text
M6 final merge = 904058251829b0ae39b34cd9da25c2b8918851a6
M6 final gate  = 261/261 PASS
```

Issue : **#50**  
PR : **#51**  
Branche : `m7/incremental-sync-freshness`

## Question de sortie

> **MORPHEUS peut-il détecter de façon déterministe les changements de sources locales, appliquer une stratégie incrémentale fiable, conserver archives et état de synchronisation, exposer une fraîcheur explicable et basculer vers un full rebuild dès que la sûreté de l'incrémental n'est plus démontrable ?**

## Sous-contrats

```text
A. SourcePath + SHA-256 SourceFingerprint
B. SourceInventory + SourceRevision opaque
C. deterministic diff ADDED/MODIFIED/DELETED/MOVED/UNCHANGED
D. unique move detection; ambiguity => FULL_REBUILD
E. InvalidationSet + SourceArchiveRecord
F. SyncStateStore Memory + SQLite / V008
G. IncrementalSyncService + explicit FullRebuildReason
H. local WatchService trigger; OVERFLOW => FULL_REBUILD
I. FreshnessStatus UNKNOWN/FRESH/STALE
J. Memory == SQLite + SQLite reopen + deterministic ordering
K. VALIDATION_M7.md
```

## ADR

```text
ADR-0053 source inventory/diff
ADR-0054 persisted sync state/archive/freshness
ADR-0055 watcher/fallback
```

Toutes restent **Proposées** jusqu'au gate final M7.

## Invariants

```text
reliability > incremental speed
content SHA-256 > mtime/size
sourceRevision opaque
same revision + different inventory => FULL_REBUILD
ambiguous move => FULL_REBUILD
watcher != source of truth
watcher event => rescan
OVERFLOW => FULL_REBUILD
archive != published-history deletion
invalidation != snapshot deletion
freshness uses explicit now/maxAge
no LLM
no semantic matching
no fuzzy rename
```

## Validation finale attendue

Le gate local Windows final sera :

```text
.\mvnw.cmd clean test
```

Le nombre exact de tests sera fixé après l'implémentation et l'audit du diff.
