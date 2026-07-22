# E05 — Knowledge snapshots

Statut : **PASS**

Date : 22 juillet 2026

## Hypothèse

MORPHEUS peut publier un nouvel état de connaissance de manière atomique au niveau observable : un consommateur voit soit l'ancien snapshot actif, soit le nouveau snapshot validé, jamais un état intermédiaire.

## Preuves associées

```text
E05/E07 memory store
E08 SQLite persistent store
E05b rebuild + retention
E06b store-backed traceability
```

## Backends exercés

```text
InMemorySpecificationKnowledgeStore
SQLiteSpecificationKnowledgeStore
```

SQLite reste un **candidat expérimental**, pas une décision de backend de production.

## Cycle validé

```text
BUILDING
   ↓
VALIDATING
   ↓
READY
   ↓
ACTIVE
```

L'ancien snapshot actif devient :

```text
RETIRED
```

Un snapshot structurellement invalide devient :

```text
FAILED
```

## Invariants validés

### État intermédiaire invisible

Un snapshot `BUILDING`, `VALIDATING`, `READY` ou `FAILED` n'est jamais présenté comme état courant.

### Activation observable atomique

```text
avant activation -> Vn visible
après activation -> Vn+1 visible
```

Aucun consommateur ne doit observer un mélange Vn/Vn+1.

### Concurrence / predecessor

Un snapshot construit sur un predecessor qui n'est plus actif est rejeté par `SnapshotConflict`.

Il ne peut donc pas écraser silencieusement une génération plus récente.

### Idempotence

Un payload normalisé identique possède un fingerprint stable et son rejeu est détectable.

### Comparaison

Le contrat sait dériver :

```text
ADDED
REMOVED
MODIFIED
UNCHANGED
```

### Persistance

Le backend SQLite du spike conserve l'état actif après fermeture/réouverture et applique l'activation dans une transaction.

### Traçabilité attachée au snapshot

E06b démontre que les relations d'un snapshot actif restent interrogeables sur les deux stores sans mélanger plusieurs générations.

### Reconstruction

Le store peut être supprimé/recréé puis reconstruit à partir des sources et du pipeline de normalisation.

La frontière `CURRENT / PROPOSED` reste identique après reconstruction.

### Rétention minimale M0

La politique validée pour la faisabilité est :

```text
ACTIVE                  toujours conservé
1 RETIRED predecessor   conservé par défaut
RETIRED plus anciens    purgeables
```

Cette politique pourra évoluer selon les volumes réels.

## Baseline de coût

E08 fournit une première mesure exploratoire sur 5 000 requirements avec un schéma SQLite volontairement naïf. Les résultats démontrent la faisabilité locale mais ne constituent pas des seuils de production.

## Points volontairement non figés par E05

- politique de rétention production ;
- schéma physique final ;
- backend produit final ;
- mécanisme interne d'atomicité pour un autre backend ;
- durée de conservation des snapshots liés à des releases métier.

Ces choix peuvent évoluer tant que l'invariant observable reste respecté.

## Impact ADR-0012

Les conditions M0 de l'ADR sont désormais démontrées :

- [x] `SpecificationVersion` distinct de `KnowledgeSnapshot` ;
- [x] deux implémentations de store ;
- [x] activation atomique observable ;
- [x] interruption/échec sans corruption de l'actif ;
- [x] idempotence ;
- [x] isolation `CURRENT / PROPOSED` ;
- [x] rétention minimale ;
- [x] coût initial mesuré ;
- [x] reconstruction depuis les sources.

## Décision

```text
E05 = PASS
KNOWLEDGE_SNAPSHOT_MODEL = RETAIN
OBSERVABLE_ATOMIC_ACTIVATION = REQUIRED
STORE_REBUILDABLE_FROM_SOURCES = REQUIRED
```
