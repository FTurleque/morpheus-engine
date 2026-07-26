# M16 — Constraint Semantics & Policy Enforcement

Statut : **🚧 EN COURS — issue #78 / ADR-0082 proposée**

Dernière mise à jour : 26 juillet 2026

Issue : **#78**  
Branche : `m16/constraint-semantics-policy`

## 1. Question de sortie

> **MORPHEUS peut-il déterminer de façon explicable quelles contraintes sont applicables et lesquelles bloquent réellement une action ou une transition, sans convertir une absence d'information en interdiction ?**

## 2. Baseline d'entrée

```text
C0 -> M15       ✅ validés / intégrés
M15 merge       c37134439844cb088adff855c339a259bb908b6a
M15             371/371 PASS
Architecture    157/157 PASS
Packaging Win   PASS
```

## 3. Invariants

```text
applicable != blocking
warning != blocker
UNKNOWN != BLOCKED
constraint text != executable policy
policy decision must expose provenance and reason
provider-specific policy types never leak into domain
base lifecycle rules remain MORPHEUS-owned
JARVIS still owns sequencing / action choice
```

## 4. Modèle cible

```text
Constraint
├── ConstraintApplicability
├── ConstraintSeverity
├── ConstraintSatisfaction
├── ConstraintBlockingPolicy
├── blocking lifecycle targets
├── supporting Evidence
└── Provenance

ConstraintPolicyEvaluationService
        ↓
ConstraintEvaluation
├── NOT_APPLICABLE
├── NON_BLOCKING
├── BLOCKING
└── UNKNOWN
```

## 5. Slices

### M16-S1 — Domaine canonique

- `ConstraintApplicability` ;
- `ConstraintSeverity` ;
- `ConstraintSatisfaction` ;
- `ConstraintBlockingMode` + `ConstraintBlockingPolicy` ;
- `ConstraintEvaluationState` + `ConstraintEvaluation` ;
- extension compatible de `Constraint` ;
- invariants evidence/policy.

### M16-S2 — Normalisation et persistance

- contraintes historiques/OpenSpec => sémantique `UNKNOWN`, jamais inférée ;
- Synthetic provider => contraintes M16 explicites ;
- validation des supporting evidence ;
- SQLite V010 ;
- Memory == SQLite ;
- close/reopen identique.

### M16-S3 — Evaluation déterministe

- service provider-neutral ;
- target lifecycle explicite ;
- `warning != blocker` ;
- `UNKNOWN != BLOCKED` ;
- raisons et evidence exposées.

### M16-S4 — Orchestration

- `applicableConstraints` enrichies ;
- `blockingConstraints` réellement évaluées ;
- statut `AVAILABLE`, `PARTIALLY_AVAILABLE` ou `UNKNOWN` selon les faits ;
- chaque blocker expose sa raison/provenance ;
- aucune mutation.

### M16-S5 — Lifecycle

- les règles M3 sont évaluées en premier ;
- un blocker de contrainte explicite peut bloquer une transition autrement autorisée ;
- une contrainte `UNKNOWN` ne bloque jamais ;
- l'indisponibilité reste distincte de `ALLOWED`.

### M16-S6 — Surfaces

- query/application view ;
- CLI `constraints evaluate` ;
- MCP lecture/évaluation sans mutation ;
- HTTP GET constraint evaluations ;
- OpenAPI mis à jour ;
- contrat JARVIS enrichi sans changer la frontière.

### M16-S7 — Gate

- tests domaine/application/providers/store ;
- architecture ;
- reactor complet ;
- packaging Windows + smokes ;
- `VALIDATION_M16.md` ;
- ADR-0082 acceptée seulement après preuve ;
- PR Ready seulement après gate.

## 6. Gate M16

```text
blockingConstraints.status != UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED
transition decisions explain every blocking constraint
UNAVAILABLE remains distinct from false / allowed
no provider-specific policy type leaks into domain
Memory == SQLite
SQLite close/reopen identical
CLI/MCP/HTTP coherent
full Maven reactor PASS
Windows packaging PASS
```

## 7. Gouvernance

La branche/PR M16 reste isolée de `main` jusqu'au gate complet. Aucun merge sans autorisation explicite distincte.
