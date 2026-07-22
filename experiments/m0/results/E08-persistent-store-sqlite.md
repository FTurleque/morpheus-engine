# E08 — Persistent store candidate: SQLite

Statut : **PASS comme candidat expérimental — aucune adoption production**

Date : 22 juillet 2026

## Hypothèse

Un backend persistant embarqué peut reproduire les invariants principaux du store mémoire tout en survivant à un redémarrage, sans introduire de service externe.

SQLite est utilisé ici comme **candidat de spike**, conformément à ADR-0014.

## Spike

```text
experiments/m0/spikes/e08_sqlite_store_python/
├── sqlite_store.py
└── test_sqlite_store.py
```

Le schéma du spike stocke volontairement le payload normalisé en JSON dans une table de snapshots. Ce choix simplifie la preuve mais **n'est pas proposé comme schéma final MORPHEUS**.

## Environnement

```text
Python 3.13.5
sqlite3 standard library
Linux container
```

## Protocole fonctionnel

```text
python -m unittest -v
```

Résultat :

```text
Ran 8 tests
8 PASS
0 FAIL
```

## Scénarios validés

### Persistance après réouverture

Après fermeture et réouverture de la base :

```text
active snapshot id conservé
payload actif relisible
requêtes fonctionnelles
```

### Snapshot BUILDING après redémarrage

Un snapshot non activé reste `BUILDING` après réouverture et ne remplace pas le snapshot actif précédent.

### Snapshot invalide

Un snapshot invalide passe `FAILED`, ne peut pas être activé et ne modifie pas l'état courant.

### Activation transactionnelle

L'activation utilise une transaction SQLite `BEGIN IMMEDIATE` :

```text
ancien ACTIVE -> RETIRED
nouveau READY -> ACTIVE
meta.active_snapshot -> nouveau snapshot
```

La transaction est commitée en bloc ou rollbackée en cas d'erreur.

### Predecessor obsolète

Un snapshot construit depuis une génération qui n'est plus active produit `SnapshotConflict` et ne remplace pas le snapshot courant.

### Idempotence

Un payload canonique identique possède le même fingerprint et réutilise le snapshot déjà enregistré.

### Requêtes

Le candidat reproduit la sémantique minimale exercée sur le store mémoire :

```text
get_current_specification
find_requirements
get_change
compare snapshots
```

## Baseline de performance exploratoire

Une exécution locale unique a été effectuée sur un payload synthétique de :

```text
5 000 requirements
50 specifications logiques
```

Résultat observé :

```text
begin snapshot : ~21.0 ms
validate       : ~19.8 ms
activate       : ~19.3 ms
query p50      : ~3.9 ms
query max      : ~12.1 ms
database size  : ~1 581 056 bytes
```

Ces chiffres sont **des ordres de grandeur du spike**, pas des seuils ni des benchmarks de production :

- une seule machine/environnement ;
- une seule exécution d'ingestion ;
- stockage JSON non optimisé ;
- recherche qui recharge actuellement le payload JSON ;
- aucun index métier ;
- aucune relation de traçabilité persistée dans ce schéma expérimental.

## Lecture architecturale

Malgré un schéma volontairement naïf, le backend embarqué suffit déjà à démontrer :

- persistance locale ;
- transaction d'activation ;
- snapshot cohérent ;
- redémarrage ;
- idempotence ;
- comparaison ;
- requêtes simples ;
- absence de service réseau.

Cela constitue un signal positif pour une famille **relationnelle/embarquée** comme backend initial léger.

## Ce que E08 ne décide pas

E08 ne choisit pas SQLite comme backend produit.

Restent à comparer avant décision de fondation :

- schéma normalisé vs payload JSON ;
- persistance des `TraceabilityLink` ;
- requêtes de profondeur ;
- index lexical ;
- migrations ;
- stratégie de rétention ;
- volumétrie plus représentative ;
- concurrence réelle ;
- intégration des `DomainIdentity` ;
- portabilité Windows mesurée directement ;
- sauvegarde/reconstruction.

## Impact ADR-0003

**Preuve positive forte.**

Le même style de contrat peut être implémenté par :

```text
InMemorySpecificationKnowledgeStore
SQLiteSpecificationKnowledgeStore (spike)
```

sans exposer SQL aux requêtes métier du test.

ADR-0003 reste `Proposée` jusqu'à stabilisation des tests contractuels complets et des capacités de traçabilité.

## Impact ADR-0012 / E05

Le second backend confirme :

- activation observable atomique ;
- predecessor obsolète rejeté ;
- idempotence ;
- état actif persistant après redémarrage.

E05 reste néanmoins `PARTIAL_PASS` tant que les points suivants ne sont pas fermés :

- politique minimale de rétention ;
- reconstruction depuis les sources ;
- coût snapshot sur corpus de volume final ;
- intégration de la traçabilité persistée.

## Impact E09

Aucun besoin de graph database n'est démontré par E08 pour les opérations de snapshot et requêtes simples.

E09 doit donc rester conditionnelle à la mesure des traversées de traçabilité persistées plutôt qu'être déclenchée par principe.

## Décision

```text
E08_SQLITE_SPIKE = PASS
SQLITE_AS_PRODUCTION_BACKEND = UNDECIDED
EMBEDDED_RELATIONAL_FAMILY = PROMISING
```
