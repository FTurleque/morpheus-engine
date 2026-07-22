# E04b — Change lifecycle state machine

Statut : **PASS**

Date : 22 juillet 2026

## Pourquoi cette expérience complémentaire

E04 valide `CURRENT / PROPOSED / HISTORICAL`, mais ADR-0013 définit également une machine d'état métier du changement.

Cette expérience complémentaire ferme explicitement les points non couverts par E04 :

- préconditions de transition ;
- étape de design facultative ;
- retours arrière ;
- abandon ;
- réouverture ;
- critères d'acceptation bloquants ;
- séparation lifecycle / temporal state.

## Spike

```text
experiments/m0/spikes/e04b_change_lifecycle_python/
├── lifecycle.py
└── test_lifecycle.py
```

## Résultat

```text
Ran 11 tests
11 PASS
0 FAIL
```

## Politique exercée

Cycle nominal :

```text
DRAFT
  ↓
PROPOSED
  ↓
SPECIFIED
  ↓
DESIGNED
  ↓
PLANNED
  ↓
IMPLEMENTING
  ↓
VERIFYING
  ↓
COMPLETED
  ↓
ARCHIVED
```

`ABANDONED` est accessible depuis les états actifs avec raison obligatoire.

## Résultats structurants

### PROPOSED → SPECIFIED

La transition est bloquée sans :

```text
requirements
critical constraints
acceptance criteria
```

### Design facultatif

Le modèle conserve `DESIGNED`, mais une politique peut autoriser :

```text
SPECIFIED -> PLANNED
```

lorsque :

```text
design_required = false
plan présent
```

Cette stratégie est retenue pour éviter une étape documentaire artificielle sur un changement trivial.

### Retour arrière

Les transitions de révision comme :

```text
VERIFYING -> IMPLEMENTING
IMPLEMENTING -> PLANNED
DESIGNED -> SPECIFIED
```

sont légitimes et ne constituent pas des erreurs techniques.

### Passage en IMPLEMENTING

Un bloqueur connu empêche :

```text
PLANNED -> IMPLEMENTING
```

### Passage en COMPLETED

Un critère d'acceptation bloquant `FAILED` ou non vérifié empêche :

```text
VERIFYING -> COMPLETED
```

### COMPLETED != CURRENT

Une transition lifecycle réussie :

```text
VERIFYING -> COMPLETED
```

ne modifie pas :

```text
TemporalState = PROPOSED
```

La promotion de baseline reste une opération/fait distinct.

### Abandon

`ABANDONED` exige une raison explicite.

### Réouverture

Un changement `ABANDONED` peut revenir à :

```text
PROPOSED
```

Un changement `ARCHIVED` n'est pas réouvert directement par la même règle ; une politique explicite supplémentaire serait nécessaire.

## Impact ADR-0013

Les invariants principaux de la machine d'état sont maintenant démontrés :

- [x] états canoniques ;
- [x] préconditions ;
- [x] design facultatif ;
- [x] transitions arrière ;
- [x] abandon ;
- [x] réouverture ;
- [x] blocage par acceptance criteria ;
- [x] `COMPLETED` distinct de la promotion `CURRENT`.

La stratégie d'étape facultative retenue pour M0 est :

```text
transition conditionnelle SPECIFIED -> PLANNED
si design_required = false
```

## Décision

```text
E04b = PASS
CANONICAL_CHANGE_LIFECYCLE = RETAIN
OPTIONAL_DESIGN_POLICY = CONDITIONAL_SKIP
COMPLETED_IMPLIES_CURRENT = REJECT
```
