# ADR-0054 — État de synchronisation persisté, archives, invalidation et fraîcheur

- Statut : **Proposée — M7**
- Date : 23 juillet 2026
- Dépend de : ADR-0018, ADR-0021, ADR-0036, ADR-0053
- Portée : M7, état de sync Memory/SQLite, archives, invalidation, freshness

## Décision candidate

Ajouter un port `SyncStateStore` avec parité Memory/SQLite et une migration dédiée `V008`.

L'état courant conserve :

```text
projectId
lastSuccessfulSyncAt
lastAttemptAt
lastObservedChangeAt
optional sourceRevision
last mode INCREMENTAL/FULL_REBUILD
current source inventory
```

Les sources supprimées ou déplacées sont conservées comme `SourceArchiveRecord` immuables et ordonnées. Les invalidations sont explicites dans le résultat de synchronisation ; elles ne suppriment ni historique publié ni snapshot.

La fraîcheur est calculée à la demande avec un `now` et un seuil explicites : aucun `Instant.now()` caché dans les métriques.

```text
UNKNOWN = aucune sync réussie
FRESH   = age <= maxAge
STALE   = age > maxAge
```

## Persistance

V008 crée uniquement les tables spécialisées nécessaires à M7. Aucun JSON métier générique.

## Acceptation

À compléter après le gate local final M7.
