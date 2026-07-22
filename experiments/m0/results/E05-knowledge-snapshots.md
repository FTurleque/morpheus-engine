# E05 — Knowledge snapshots

Statut : **PARTIAL_PASS — backend mémoire validé, backend persistant encore requis**

Date : 22 juillet 2026

## Hypothèse

MORPHEUS peut publier un nouvel état de connaissance de manière atomique au niveau observable : un consommateur voit soit l'ancien snapshot actif, soit le nouveau snapshot validé, jamais un état intermédiaire.

## Spike

```text
experiments/m0/spikes/e05_e07_memory_store_python/
├── store.py
└── test_store.py
```

## Environnement

```text
Python 3.13.5
Linux container
standard library only
```

Le spike est expérimental conformément à ADR-0014.

## Protocole exécuté

```text
python -m unittest -v
```

Suite E05/E07 :

```text
Ran 8 tests
8 PASS
0 FAIL
```

Sous-ensemble snapshot E05 : **7 tests PASS**.

## Cycle exercé

```text
BUILDING
   ↓
VALIDATING
   ↓
READY
   ↓
ACTIVE
```

Le snapshot actif précédent passe à :

```text
RETIRED
```

Un snapshot invalide passe à :

```text
FAILED
```

## Scénarios validés

### Activation V1

Un premier snapshot validé devient l'état actif.

### Construction V2 interrompue

Pendant que V2 reste `BUILDING` :

```text
active = V1
queries = V1
```

V2 n'est jamais visible comme état courant.

### Validation V2 échouée

Un snapshot incomplet devient `FAILED` et ne peut pas être activé.

V1 reste actif.

### Activation atomique V2

Après validation :

```text
before activate -> V1 ACTIVE
after activate  -> V2 ACTIVE / V1 RETIRED
```

Les requêtes basculent directement de V1 vers V2.

### Concurrence sur predecessor

Deux snapshots construits depuis V1 ne peuvent pas tous deux écraser l'état actif.

Si A est activé avant B :

```text
B.predecessor = V1
active = A
=> SnapshotConflict
```

Cela empêche un snapshot obsolète d'écraser silencieusement une génération plus récente.

### Rejeu idempotent

Deux constructions avec le même payload canonique réutilisent le même fingerprint et le même snapshot expérimental.

### Comparaison

Le store sait dériver au minimum :

```text
ADDED
REMOVED
MODIFIED
UNCHANGED
```

sur les requirements `CURRENT`.

## Invariants confirmés sur backend mémoire

- [x] aucun état `BUILDING` visible comme actif ;
- [x] aucun état `FAILED` activable ;
- [x] activation observable atomique ;
- [x] predecessor obsolète détecté ;
- [x] rejeu identique idempotent ;
- [x] snapshot précédent conservé comme `RETIRED` ;
- [x] comparaison Vn / Vn+1 possible.

## Limites

E05 n'est pas encore complètement validée car ADR-0012 exige les mêmes garanties sur un **backend persistant candidat**.

Restent notamment à mesurer :

- coût disque ;
- coût de rétention ;
- reconstruction après redémarrage ;
- atomicité avec une vraie transaction/persistance ;
- migration de schéma ;
- comportement après crash process ;
- politique finale de rétention.

## Impact ADR-0012

**Preuve positive forte sur la sémantique snapshot et le backend mémoire.**

ADR-0012 reste `Proposée` jusqu'à E08.

## Décision provisoire

```text
E05_MEMORY = PASS
E05_OVERALL = PARTIAL_PASS
SNAPSHOT_MODEL = RETAIN
CONTINUE_TO_PERSISTENT_STORE = YES
```
