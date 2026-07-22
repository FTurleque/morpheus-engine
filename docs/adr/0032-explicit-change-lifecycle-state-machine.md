# ADR-0032 — Appliquer une machine d'état explicite au lifecycle des changements

- Statut : **Acceptée — M3**
- Date : 22 juillet 2026
- Dépend de : ADR-0006, ADR-0013, ADR-0024, ADR-0031
- Portée : M3-S2, lifecycle métier des changements, validation de transitions

## Contexte

M2 a normalisé `ChangeProposal` comme contenu provider-neutral sans lui injecter un lifecycle complet.

M3-S1 a introduit `TemporalState` comme dimension séparée :

```text
CURRENT
PROPOSED
HISTORICAL
```

M3-S2 matérialise le lifecycle métier démontré par M0/E04b sans confondre :

```text
progression du changement
état temporel du contenu
état technique du snapshot
état de vérification
```

## Décision

Introduire le cycle canonique :

```text
DRAFT
PROPOSED
SPECIFIED
DESIGNED
PLANNED
IMPLEMENTING
VERIFYING
COMPLETED
ARCHIVED
ABANDONED
```

Le cycle nominal est :

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

`ABANDONED` reste un état terminal métier accessible depuis les états actifs avec raison explicite.

## Séparation des dimensions

Invariant :

```text
ChangeLifecycleState != TemporalState
ChangeLifecycleState != KnowledgeSnapshotState
ChangeLifecycleState != task checkbox
```

Conséquences :

```text
COMPLETED != CURRENT
ARCHIVED  != CURRENT
```

Une transition lifecycle ne modifie jamais implicitement une projection temporelle.

## Modèle

Le domaine introduit :

```text
ChangeLifecycleState
ChangeAbandonmentReason
ChangeLifecycle
```

`ChangeLifecycle` contient :

```text
ChangeId
ChangeLifecycleState
abandonmentReason?
```

Règles :

- `ABANDONED` exige une raison ;
- une raison d'abandon est interdite hors `ABANDONED` ;
- `ChangeProposal` reste inchangé.

Raisons normalisées :

```text
REJECTED
OBSOLETE
DUPLICATE
NOT_FEASIBLE
NO_LONGER_NEEDED
SUPERSEDED_BY_OTHER_CHANGE
UNKNOWN
```

## Machine applicative

Introduire :

```text
ChangeLifecycleFacts
ChangeLifecyclePolicy
ChangeLifecycleBlocker
ChangeLifecycleTransitionRequest
ChangeLifecycleTransitionDecision
ChangeLifecycleStateMachine
```

La machine répond explicitement à :

```text
can transition ?
why blocked ?
```

Elle ne modifie aucune source externe et ne devient pas un orchestrateur généraliste.

## Préconditions démontrées par E04b

### PROPOSED -> SPECIFIED

Requiert :

```text
requirements identified
critical constraints known
acceptance criteria defined
```

### SPECIFIED -> DESIGNED

Lorsque `design_required=true`, les décisions de design nécessaires doivent être disponibles.

### Design facultatif

La stratégie M0 retenue est :

```text
SPECIFIED -> PLANNED
```

uniquement lorsque :

```text
design_required = false
plan present
```

### DESIGNED -> PLANNED

Requiert un plan présent.

### PLANNED -> IMPLEMENTING

Est bloqué lorsqu'un bloqueur connu existe.

### VERIFYING -> COMPLETED

Est bloqué lorsqu'un critère d'acceptation bloquant est :

```text
FAILED
ou
non vérifié
```

## Retours arrière

Les transitions de révision canoniques sont :

```text
SPECIFIED     -> PROPOSED
DESIGNED      -> SPECIFIED
PLANNED       -> DESIGNED
IMPLEMENTING  -> PLANNED
VERIFYING     -> IMPLEMENTING
COMPLETED     -> VERIFYING
```

Elles ne sont autorisées que lorsque la politique applicative active explicitement les retours arrière.

`COMPLETED -> VERIFYING` dispose d'une permission supplémentaire car cette réouverture est exceptionnelle.

## Abandon et réouverture

Depuis un état actif :

```text
* -> ABANDONED
```

requiert `ChangeAbandonmentReason`.

La réouverture canonique démontrée par E04b est :

```text
ABANDONED -> PROPOSED
```

`ARCHIVED` n'est jamais rouvert implicitement dans S2.

## Politique

`ChangeLifecyclePolicy` gouverne explicitement :

```text
allowBackwardTransitions
allowCompletedReopen
```

Aucune transition backward n'est déduite d'un provider, d'un chemin, d'une archive ou d'un timestamp.

## Hors périmètre S2

- mapping lifecycle OpenSpec complet ;
- écriture dans les sources ;
- historique persistant des transitions ;
- `KnowledgeSnapshot` complet : S3 ;
- persistance métier : S4 ;
- promotion de baseline : S5 ;
- traçabilité : M4.

## Critères d'acceptation

ADR-0032 passe à **Acceptée — M3** lorsque le build complet démontre :

1. les 10 états canoniques existent exactement ;
2. `ABANDONED` exige une raison structurée ;
3. `PROPOSED -> SPECIFIED` est bloqué si un prérequis manque ;
4. `SPECIFIED -> PLANNED` n'est possible que si `design_required=false` et un plan existe ;
5. `PLANNED -> IMPLEMENTING` est bloqué par un bloqueur connu ;
6. `VERIFYING -> COMPLETED` est bloqué par un critère bloquant failed ou non vérifié ;
7. les retours arrière sont gouvernés par politique explicite ;
8. `ABANDONED -> PROPOSED` est possible ;
9. `ARCHIVED` n'est pas rouvert implicitement ;
10. `COMPLETED` et `ARCHIVED` ne modifient jamais `TemporalState` ;
11. `ChangeProposal` et `ImplementationTask.completed` ne deviennent pas le lifecycle ;
12. `.\mvnw.cmd clean test` est vert.

## Preuve d'acceptation — 22 juillet 2026

Gate exécuté sous Windows :

```text
.\mvnw.cmd clean test
Windows 10 x64
Apache Maven 3.9.16
JDK 24.0.1
javac release 21
```

Résultats :

```text
ChangeLifecycleTest               4/4 PASS
ChangeLifecycleStateMachineTest  12/12 PASS

Domain                           13 tests
Application                      54 tests
OpenSpec provider                26 tests
Synthetic provider                7 tests
SQLite store                      6 tests
Architecture tests               13 tests
-----------------------------------------
TOTAL                           119/119 PASS
Failures                           0
Errors                             0
Skipped                            0
BUILD SUCCESS
```

Les preuves E04b sont reproduites en Java : préconditions de spécification, design facultatif, bloqueurs d'implémentation, critères d'acceptation bloquants, retours arrière gouvernés, abandon avec raison, réouverture depuis `ABANDONED`, absence de réouverture implicite depuis `ARCHIVED` et séparation stricte du lifecycle avec `TemporalState`.

Le test d'orthogonalité démontre explicitement qu'un changement peut être `COMPLETED` tandis que son occurrence reste `TemporalState.PROPOSED`. Une tâche `completed=true` ne modifie pas non plus le lifecycle du changement.

Les warnings JDK 24 liés au chargement natif SQLite et l'absence de provider SLF4J dans les tests ArchUnit restent connus et non bloquants.
