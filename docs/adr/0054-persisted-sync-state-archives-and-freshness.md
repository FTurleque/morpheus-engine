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

## Amendement du 25 septembre 2026 (SYN-1) — l'état de synchronisation est un état d'autorité, il s'écrit avec une révision attendue

L'état de synchronisation porte le drapeau `pending_full_rebuild_reason`, ce qui force la prochaine sync hors du chemin incrémental. Il était écrit
en upsert aveugle (`ON CONFLICT DO UPDATE`), le port n'offrait aucun paramètre de révision et `complete()` ne comparait rien avant d'écrire : un writer
périmé effaçait le drapeau d'un autre. C'est un last-write-wins silencieux, interdit par la doctrine du dépôt pour les écritures de configuration et
d'autorité (CAS obligatoire).

- `ProjectSyncState` porte `revision` (0 avant la première écriture, +1 à chaque écriture). `SyncStateStore.recordAttempt` et `commitSuccessfulSync`
  prennent la révision attendue et rendent la nouvelle ; une divergence est `SyncStateConflictException` (type nommé, pas une panne de stockage : l'appelant
  doit pouvoir les distinguer, comme `PolicyConflictException`).
- SQLite : `UPDATE … WHERE project_id = ? AND revision = ?` dont le nombre de lignes affectées est vérifié, et un `INSERT … ON CONFLICT DO NOTHING` pour la
  création, jamais un upsert. La révision est écrite dans la même transaction que l'inventaire et les archives : un commit refusé n'en laisse rien.
  Mémoire : la même garde sous son moniteur ; les deux adaptateurs sont exercés sur le même scénario (`SyncStateRevisionContractTest`).
- `SyncPlan.stateRevision` est la révision après l'enregistrement de la tentative du plan ; `complete` et `fail` écrivent contre elle. `complete` ne réessaie
  pas et ne pose pas `BASELINE_INCONSISTENT` sur un conflit : un plan dépassé ne réécrit pas l'état d'un plus récent. Le retry sur panne de stockage reste
  (il ne change pas la révision : la transaction annulée n'a rien écrit).

### Alternatives écartées

- **Verrou par projet dans l'application.** Ne couvre pas deux processus (CLI et API), seul cas où le `synchronized` ne protégeait déjà rien.
- **Horodatage comme jeton (compare `lastAttemptAt`).** Deux tentatives peuvent partager un instant ; une révision entière monotone ne ment pas.
- **`ON CONFLICT DO UPDATE … WHERE revision = excluded…`.** Un upsert conditionnel dont on n'inspecte pas le résultat ne garde rien ; le `UPDATE` explicite dit le nombre de lignes.

### Conséquences

- **Rupture annoncée** : le port change de signature, le schéma passe à 19 (migration `V019`, lignes existantes à 1), et deux syncs concurrentes du même projet
  voient l'une échouer (`409` en HTTP, code 4 en CLI).
- **Résidu assumé.** `fail()` appelé sur un plan dépassé lève le conflit à la place de la panne d'origine (le conflit est la vérité : un plus récent possède l'état) ;
  l'appelant qui l'avale perdrait la cause première.
- **Preuve.** `SyncStateRevisionContractTest` (mémoire **et** SQLite : drapeau non effacé, commit refusé sans effet sur l'inventaire, première écriture unique,
  révision +1, plan dépassé au complete et au fail, `prepare()` suivant en `FULL_REBUILD`), `SqliteSyncStateRevisionMigrationTest` (base au schéma 18 ouverte, lignes à 1).
