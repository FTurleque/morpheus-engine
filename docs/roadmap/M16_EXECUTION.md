# M16 — Constraint Semantics & Policy Enforcement

Statut : **🚧 EN COURS — S1→S6 codées ; gate réel S7 restant**

Dernière mise à jour : 26 juillet 2026

Issue : **#78**  
Branche : `m16/constraint-semantics-policy`  
PR : **#79 — Draft**

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

## 4. Modèle M16

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

La règle de blocage est volontairement stricte :

```text
APPLICABLE
+ BLOCK_WHEN_VIOLATED
+ target lifecycle explicite
+ VIOLATED avec evidence
= BLOCKING
```

Aucun texte, mot-clé ou niveau de sévérité n'est interprété comme une politique exécutable.

## 5. Slices

### M16-S1 — Domaine canonique ✅ CODED

- ✅ `ConstraintApplicability` ;
- ✅ `ConstraintSeverity` ;
- ✅ `ConstraintSatisfaction` ;
- ✅ `ConstraintBlockingMode` + `ConstraintBlockingPolicy` ;
- ✅ `ConstraintEvaluationState` + `ConstraintEvaluation` ;
- ✅ extension rétrocompatible de `Constraint` ;
- ✅ contraintes legacy => sémantique `UNKNOWN` ;
- ✅ supporting evidence obligatoire pour `SATISFIED` / `VIOLATED` ;
- ✅ `warning != blocker`, `UNKNOWN != BLOCKED` couverts par tests.

### M16-S2 — Normalisation et persistance ✅ CODED

- ✅ contraintes historiques/OpenSpec => sémantique `UNKNOWN`, jamais inférée ;
- ✅ Synthetic provider => contraintes M16 explicites ;
- ✅ fixture : CRITICAL violée réellement bloquante + WARNING violée non bloquante ;
- ✅ supporting evidence explicite et validée ;
- ✅ SQLite V010 : colonnes sémantiques + lifecycle targets + evidence ;
- ✅ contrat Memory == SQLite écrit ;
- ✅ contrat SQLite close/reopen écrit.

### M16-S3 — Evaluation déterministe ✅ CODED

- ✅ `ConstraintPolicyEvaluationService` provider-neutral ;
- ✅ target lifecycle explicite ;
- ✅ `NOT_APPLICABLE`, `NON_BLOCKING`, `BLOCKING`, `UNKNOWN` ;
- ✅ aucune heuristique texte/sévérité ;
- ✅ raison, source evidence et supporting evidence exposées ;
- ✅ `ConstraintEvaluationQueryService` snapshot-scoped et paginé.

### M16-S4 — Orchestration ✅ CODED

- ✅ `applicableConstraints` expose applicabilité/sévérité/satisfaction/policy/evidence ;
- ✅ `blockingConstraints` n'utilise plus `UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED` ;
- ✅ statut `AVAILABLE`, `PARTIALLY_AVAILABLE` ou `UNKNOWN` selon les faits ;
- ✅ unknown n'est jamais compté comme blocker ;
- ✅ chaque `ChangeTransitionEvaluation` transporte les `constraintEvaluations` ;
- ✅ aucune mutation/persistance d'état d'orchestration.

### M16-S5 — Lifecycle ✅ CODED

- ✅ machine lifecycle M3 conservée comme règle structurelle ;
- ✅ politique M16 évaluée après les faits/règles lifecycle ;
- ✅ blocker explicite => `BLOCKING_CONSTRAINT` + `BLOCKED` ;
- ✅ policy/satisfaction inconnue => `UNKNOWN`, jamais `BLOCKED` ni `ALLOWED` ;
- ✅ `REQUIRES_INPUT` structurel conservé ;
- ✅ `knownBlocker` dérivé uniquement d'une politique explicite pour la cible pertinente.

### M16-S6 — Surfaces ✅ CODED

- ✅ query/application : `ConstraintEvaluationQueryService` ;
- ✅ CLI : `constraints evaluate --project ID --change ID --target STATE` ;
- ✅ MCP : catalogue read-only inchangé ; `get_change_orchestration_state` + `evaluate_change_transition` transportent la sémantique M16 ;
- ✅ transport MCP STDIO M16 couvert par test dédié ;
- ✅ HTTP : routes existantes `/orchestration` + `/transition-check` enrichies, sans endpoint redondant ;
- ✅ contrat HTTP positif couvert avec fixture M16 explicite ;
- ✅ OpenAPI **1.5.0** documente les enums/policy/evaluations M16 ;
- ✅ contrat JARVIS enrichi sans changer la frontière MORPHEUS/JARVIS.

### M16-S7 — Gate 🚧

- ✅ tests domaine/application/providers/store écrits ;
- ✅ tests architecture Memory/SQLite/reopen écrits ;
- ✅ tests CLI/API/MCP transport écrits ;
- ✅ `validate-m16.cmd` + `scripts/validate-m16.ps1` ajoutés ;
- ⏳ reactor Maven complet réel ;
- ⏳ corrections de tout échec réel ;
- ⏳ packaging Windows + smokes ;
- ⏳ `VALIDATION_M16.md` avec SHA/compteurs exacts ;
- ⏳ ADR-0082 acceptée seulement après preuve ;
- ⏳ PR #79 Ready seulement après gate.

## 6. Gate M16

```text
blockingConstraints.status != UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED     CODED
transition decisions explain every blocking constraint                      CODED
UNAVAILABLE remains distinct from false / allowed                           CODED
no provider-specific policy type leaks into domain                          CODED
Memory == SQLite                                                            TEST WRITTEN
SQLite close/reopen identical                                               TEST WRITTEN
CLI/MCP/HTTP coherent                                                       TESTS WRITTEN
full Maven reactor PASS                                                     NOT RUN
Windows packaging PASS                                                      NOT RUN
```

## 7. Validation

Aucun PASS final n'est revendiqué avant exécution réelle du Maven Wrapper et du packaging Windows sur le head courant.

```text
PR #79    reste Draft
ADR-0082  reste Proposée
M16       reste EN COURS
```

## 8. Gouvernance

La branche/PR M16 reste isolée de `main` jusqu'au gate complet. Aucun merge sans autorisation explicite distincte.
