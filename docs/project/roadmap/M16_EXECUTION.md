# M16 — Constraint Semantics & Policy Enforcement

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #79**

Dernière mise à jour : 26 juillet 2026

Issue : **#78 — closed / completed**  
PR : **#79 — merged**  
Merge : `97308005a63854c7cb08dc19cd3cdb02ac739404`  
Head de code validé : `f349c5f4701665e649d985426d35b5e6a6060e32`

## 1. Question de sortie

> **MORPHEUS peut-il déterminer de façon explicable quelles contraintes sont applicables et lesquelles bloquent réellement une action ou une transition, sans convertir une absence d'information en interdiction ?**

**Réponse : OUI.**

## 2. Baseline d'entrée

```text
C0 -> M15       ✅ validés / intégrés
M15 merge       c37134439844cb088adff855c339a259bb908b6a
M15             371/371 PASS
Architecture    157/157 PASS
Packaging Win   PASS
```

## 3. Invariants validés

```text
applicable != blocking
warning != blocker
UNKNOWN != BLOCKED
constraint text != executable policy
severity != blocking policy
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

### M16-S1 — Domaine canonique ✅

- ✅ `ConstraintApplicability` ;
- ✅ `ConstraintSeverity` ;
- ✅ `ConstraintSatisfaction` ;
- ✅ `ConstraintBlockingMode` + `ConstraintBlockingPolicy` ;
- ✅ `ConstraintEvaluationState` + `ConstraintEvaluation` ;
- ✅ extension rétrocompatible de `Constraint` ;
- ✅ contraintes legacy => sémantique `UNKNOWN` ;
- ✅ supporting evidence obligatoire pour `SATISFIED` / `VIOLATED` ;
- ✅ `warning != blocker`, `UNKNOWN != BLOCKED` couverts par tests.

### M16-S2 — Normalisation et persistance ✅

- ✅ contraintes historiques/OpenSpec => sémantique `UNKNOWN`, jamais inférée ;
- ✅ Synthetic provider => contraintes M16 explicites ;
- ✅ fixture : CRITICAL violée réellement bloquante + WARNING violée non bloquante ;
- ✅ supporting evidence explicite et validée ;
- ✅ SQLite V010 : colonnes sémantiques + lifecycle targets + evidence ;
- ✅ Memory == SQLite ;
- ✅ SQLite close/reopen identique.

### M16-S3 — Evaluation déterministe ✅

- ✅ `ConstraintPolicyEvaluationService` provider-neutral ;
- ✅ target lifecycle explicite ;
- ✅ `NOT_APPLICABLE`, `NON_BLOCKING`, `BLOCKING`, `UNKNOWN` ;
- ✅ aucune heuristique texte/sévérité ;
- ✅ raison, source evidence et supporting evidence exposées ;
- ✅ `ConstraintEvaluationQueryService` snapshot-scoped et paginé.

### M16-S4 — Orchestration ✅

- ✅ `applicableConstraints` expose applicabilité/sévérité/satisfaction/policy/evidence ;
- ✅ `blockingConstraints` n'utilise plus `UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED` ;
- ✅ statut `AVAILABLE`, `PARTIALLY_AVAILABLE` ou `UNKNOWN` selon les faits ;
- ✅ unknown n'est jamais compté comme blocker ;
- ✅ chaque `ChangeTransitionEvaluation` transporte les évaluations de contraintes ;
- ✅ projection JSON-safe séparée du domaine conformément à ADR-0047 ;
- ✅ aucune mutation/persistance d'état d'orchestration.

### M16-S5 — Lifecycle ✅

- ✅ machine lifecycle M3 conservée comme règle structurelle ;
- ✅ politique M16 évaluée après les faits/règles lifecycle ;
- ✅ blocker explicite => `BLOCKING_CONSTRAINT` + `BLOCKED` ;
- ✅ policy/satisfaction inconnue => `UNKNOWN`, jamais `BLOCKED` ni `ALLOWED` ;
- ✅ `REQUIRES_INPUT` structurel conservé ;
- ✅ `knownBlocker` dérivé uniquement d'une politique explicite pour la cible pertinente.

### M16-S6 — Surfaces ✅

- ✅ query/application : `ConstraintEvaluationQueryService` ;
- ✅ CLI : `constraints evaluate --project ID --change ID --target STATE` ;
- ✅ MCP : catalogue read-only inchangé ; `get_change_orchestration_state` + `evaluate_change_transition` transportent la sémantique M16 ;
- ✅ transport MCP STDIO M16 couvert par test dédié ;
- ✅ HTTP : routes existantes `/orchestration` + `/transition-check` enrichies, sans endpoint redondant ;
- ✅ contrat HTTP positif couvert avec fixture M16 explicite ;
- ✅ OpenAPI **1.5.0** documente enums/policy/evaluations M16 ;
- ✅ contrat JARVIS enrichi sans changer la frontière MORPHEUS/JARVIS.

### M16-S7 — Gate ✅

- ✅ reactor Maven complet réel ;
- ✅ correction du défaut JSON canonique détecté au premier gate ;
- ✅ Domain 37/37 ;
- ✅ Application 100/100 ;
- ✅ API 10/10 ;
- ✅ CLI 25/25 ;
- ✅ Architecture 161/161 ;
- ✅ TOTAL **393/393 PASS** ;
- ✅ Windows packaging + smokes ;
- ✅ archive portable créée ;
- ✅ `VALIDATION_M16.md` ;
- ✅ ADR-0082 acceptée ;
- ✅ PR #79 mergée dans `main`.

## 6. Gate M16

Head de code réellement testé :

```text
f349c5f4701665e649d985426d35b5e6a6060e32
```

```text
blockingConstraints.status != UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED     PASS
transition decisions explain every blocking constraint                      PASS
UNAVAILABLE remains distinct from false / allowed                           PASS
no provider-specific policy type leaks into domain                          PASS
Memory == SQLite                                                            PASS
SQLite close/reopen identical                                               PASS
CLI/MCP/HTTP coherent                                                       PASS
full Maven reactor 393/393                                                   PASS
Architecture 161/161                                                         PASS
Windows packaging + smokes                                                   PASS
```

Archive :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,767,379 bytes
```

## 7. Validation et intégration

Preuve autoritative : [`../validation/VALIDATION_M16.md`](../validation/VALIDATION_M16.md).

```text
M16       ✅ VALIDÉ / INTÉGRÉ
ADR-0082  ✅ Acceptée — M16
PR #79    ✅ MERGED
Merge     97308005a63854c7cb08dc19cd3cdb02ac739404
```

## 8. Gouvernance

M16 est intégré à `main`. La preuve technique reste attachée au head de code testé `f349c5f4701665e649d985426d35b5e6a6060e32`; les commits de clôture post-gate sont documentaires uniquement.