# ADR-0054 — État de synchronisation persisté, archives, invalidation et fraîcheur

- Statut : **Acceptée — M7**
- Date : 23 juillet 2026
- Acceptée : 24 juillet 2026
- Dépend de : ADR-0018, ADR-0021, ADR-0036, ADR-0053
- Portée : M7, état de sync Memory/SQLite, archives, invalidation, freshness

## Décision

Ajouter un port `SyncStateStore` avec parité Memory/SQLite et une migration dédiée `V008`.

L'état courant conserve :

```text
projectId
lastSuccessfulSyncAt
lastAttemptAt
lastObservedChangeAt
optional sourceRevision
last mode INCREMENTAL/FULL_REBUILD
optional pendingFullRebuildReason
current source inventory
```

Les sources supprimées ou déplacées sont conservées comme `SourceArchiveRecord` immuables et ordonnées. Les invalidations sont explicites dans le résultat de synchronisation ; elles ne suppriment ni historique publié ni snapshot.

La fraîcheur est calculée à la demande avec un `now` et un seuil explicites : aucun `Instant.now()` caché dans les métriques.

```text
UNKNOWN          = aucune sync réussie et aucun rebuild pending
FRESH            = age <= maxAge
STALE            = age > maxAge
REBUILD_REQUIRED = pendingFullRebuildReason présent
```

`REBUILD_REQUIRED` domine FRESH/STALE : une baseline encore récente n'est pas présentée comme saine lorsqu'une tentative plus récente a démontré qu'un full rebuild est requis.

## Persistance

V008 crée uniquement les tables spécialisées nécessaires à M7 :

```text
sync_state
sync_inventory_entries
sync_source_archives
```

Aucun JSON métier générique.

## Preuve d'acceptation

Gate local Windows final M7 exécuté sur le head :

```text
2e19ab104be18b98536eb871981d60e6b95e1e8c
```

Résultat :

```text
MORPHEUS Application  82/82 PASS
Architecture Tests   139/139 PASS
TOTAL                282/282 PASS
Failures               0
Errors                 0
Skipped                0
BUILD SUCCESS
Finished 2026-07-24T00:22:11+02:00
```

Les preuves couvrent notamment la parité Memory/SQLite, V008, SQLite reopen, archives et rebuild pending. ADR acceptée après preuve.