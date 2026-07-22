# E05b — Snapshot rebuild and minimal retention

Statut : **PASS**

Date : 22 juillet 2026

## Objectif

Fermer les deux points restants de E05 :

1. démontrer que le store dérivé peut être reconstruit depuis les sources ;
2. définir une politique de rétention minimale suffisante pour M0.

## Test contractuel

```text
experiments/m0/spikes/e05b_rebuild_retention_python/test_rebuild_retention.py
```

Stores exercés :

```text
InMemorySpecificationKnowledgeStore
SQLiteSpecificationKnowledgeStore
```

## Politique minimale M0

```text
ACTIVE snapshot           -> toujours conservé
1 RETIRED predecessor     -> conservé par défaut
RETIRED plus anciens      -> purgeables
FAILED / BUILDING         -> gérés séparément, jamais promus implicitement
```

Cette politique est un minimum de faisabilité, pas encore la politique de rétention définitive de production.

## Scénarios validés

### Rétention mémoire

Après :

```text
V1 ACTIVE
V2 ACTIVE -> V1 RETIRED
V3 ACTIVE -> V2 RETIRED
```

avec :

```text
prune_retired(keep_recent=1)
```

le résultat attendu est :

```text
V3 ACTIVE   conservé
V2 RETIRED  conservé
V1 RETIRED  purgé
```

### Rétention SQLite

Le même contrat de rétention est appliqué au backend persistant candidat sans supprimer le snapshot actif.

### Reconstruction mémoire

Le store peut être recréé vide puis alimenté à nouveau depuis :

```text
OpenSpec fixture
  ↓
provider / normalization
  ↓
new KnowledgeSnapshot
```

La vue `CURRENT` reconstruite est identique et le fingerprint du payload normalisé est stable.

### Reconstruction SQLite

La base SQLite du spike peut être supprimée, recréée, puis reconstruite depuis les sources de référence sans dépendre de données cachées dans l'ancien store.

### Frontière CURRENT / PROPOSED après rebuild

Après reconstruction :

```text
auth-session/session-expiration
```

reste visible simultanément comme :

```text
CURRENT baseline
PROPOSED delta dans add-remember-me
```

sans fusion silencieuse.

## Invariant confirmé

> **Le knowledge store est une projection reconstructible des sources et de la normalisation, pas la source de vérité exclusive de l'intention.**

Toute donnée future créée uniquement dans MORPHEUS devra donc avoir une stratégie de persistance explicite avant activation de l'écriture.

## Impact ADR-0012

Les critères M0 sont maintenant couverts sur :

- activation atomique observable ;
- interruption/échec ;
- idempotence ;
- comparaison ;
- deux backends ;
- rétention minimale ;
- reconstruction depuis les sources ;
- maintien de `CURRENT/PROPOSED`.

## Décision

```text
E05b = PASS
MINIMUM_RETENTION = ACTIVE + 1 RECENT RETIRED
REBUILD_FROM_SOURCES = REQUIRED
STORE_AS_EXCLUSIVE_SOURCE_OF_TRUTH = REJECT
```
