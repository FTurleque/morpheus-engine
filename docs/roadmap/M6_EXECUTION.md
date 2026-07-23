# M6 — Plan d'exécution détaillé

Statut : **M6 actif — 3/6 validés ; S1-S2 intégrés, S3 Ready, S4 prochain après merge**

Dernière mise à jour : 23 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et pilote l'exécution de M6.

---

# 1. Baseline

```text
M5 = VALIDÉ ET INTÉGRÉ
M5 final merge = 6bbaf086cf1fed81e3517bb1cef5b643264fb836
M5 final gate  = 227/227 PASS

M6-S1 merge    = 5b0984ec7777eabb6f2d1417b4c900c08a038947
M6-S1 gate     = 234/234 PASS
M6-S2 merge    = 916201c724722cf9ace50d44e55d001d8faf383c
M6-S2 gate     = 241/241 PASS
M6-S3 gate     = 248/248 PASS
```

Issue de pilotage : **#43**.

---

# 2. Question de sortie M6

> **MORPHEUS peut-il détecter et expliquer les lacunes de qualité d'une spécification sur un snapshot publié, mesurer sa couverture, exposer les blocages et références cassées, tout en distinguant strictement les constats déterministes des heuristiques et sans inventer les relations absentes ?**

La porte finale doit démontrer :

```text
ACTIVE by default
ACTIVE/RETIRED explicit inspection
CURRENT isolation where applicable
quality findings machine-readable
DETERMINISTIC != HEURISTIC
heuristic confidence explicit
orphan detection
traceability coverage
implementation-task coverage
acceptance capability gap explicit
change completeness
lifecycle blockers
design-decision justification
broken/unresolved references
stable aggregate metrics/order
Memory == SQLite
SQLite reopen
no LLM/semantic dependency
```

---

# 3. Progression M6

```text
S1  ✅ requirement traceability coverage + orphan requirements — PR #44 — ADR-0048 — 234/234 — MERGED
S2  ✅ implementation-task coverage + acceptance capability gap — PR #45 — ADR-0049 — 241/241 — MERGED
S3  ✅ change completeness + lifecycle blocking conditions — PR #46 — ADR-0050 — 248/248 — READY
S4  ⏳ design-decision justification + broken/unresolved reference quality — PROCHAIN APRÈS MERGE S3
S5  ⏳ aggregate quality report + stable metrics/order + compact exposure
S6  ⏳ validation finale VALIDATION_M6.md
```

```text
M6 : 3 / 6 slices validés
```

---

# 4. Principes

```text
quality finding != ingestion diagnostic
finding = résultat dérivé, pas entité persistée
snapshot-scoped
CURRENT only pour Requirement
absence de lien != lien inventé
Scenario != AcceptanceCriterion
DETERMINISTIC != HEURISTIC
heuristic finding => confidence obligatoire
lifecycle state != snapshot state
lifecycle state != temporal state
lifecycle state != task completion
provider-neutral
backend-neutral
```

Aucune heuristique n'est présentée comme certitude et aucun fait indisponible n'est remplacé par `false`.

---

# 5. M6-S1 — INTÉGRÉ

ADR : **ADR-0048 — Acceptée — M6**  
PR : **#44 — MERGED**  
Merge : `5b0984ec7777eabb6f2d1417b4c900c08a038947`

```text
RequirementQualityContractTest          7/7 PASS
Architecture tests                    107/107 PASS
TOTAL                                 234/234 PASS
BUILD SUCCESS
Finished at                2026-07-23T20:52:28+02:00
```

---

# 6. M6-S2 — INTÉGRÉ

ADR : **ADR-0049 — Acceptée — M6**  
PR : **#45 — MERGED**  
Merge : `916201c724722cf9ace50d44e55d001d8faf383c`

Contrats :

```text
TaskRequirementCoverage
TaskQualityService
AcceptanceCoverageStatus
AcceptanceCoverageAssessment
AcceptanceQualityService
```

Sémantique :

```text
task.changeId = C
Change(C) --AFFECTS--> Requirement(R) CURRENT => task couverte
PROPOSED-only / cible absente / aucun AFFECTS => non couverte
Scenario != AcceptanceCriterion
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

Preuve :

```text
TaskAcceptanceQualityContractTest        7/7 PASS
Architecture tests                    114/114 PASS
TOTAL                                 241/241 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
Finished at                2026-07-23T22:08:29+02:00
```

---

# 7. M6-S3 — VALIDÉ TECHNIQUEMENT / READY

ADR : **ADR-0050 — Acceptée — M6**  
PR : **#46 — Ready après gate**  
Branche : `m6/change-lifecycle-quality`

Head de code testé :

```text
84f41498610af2d76236fe1e6c419a6234a5f8c9
```

## Contrats

```text
QualityFactValue = TRUE / FALSE / UNAVAILABLE
LifecycleFactSource = DERIVED / EXPLICIT
ChangeLifecycleFactAssessment
ChangeCompletenessAssessment
ChangeCompletenessReport
ChangeCompletenessService
ChangeLifecycleQualityAssessment
ChangeLifecycleQualityService
```

Extensions `QualityFindingCode` :

```text
CHANGE_WITHOUT_CURRENT_REQUIREMENT
CHANGE_COMPLETENESS_PARTIALLY_OBSERVABLE
LIFECYCLE_REQUIRED_FACT_UNAVAILABLE
LIFECYCLE_TRANSITION_BLOCKED
```

## Complétude snapshot-scoped

Pour chaque change publié :

```text
currentRequirementCount
constraintCount
designDecisionCount
implementationTaskCount
```

Projection des faits M3 :

```text
requirementsIdentified
  TRUE  si >=1 AFFECTS -> Requirement CURRENT
  FALSE sinon

criticalConstraintsKnown
  UNAVAILABLE

acceptanceCriteriaDefined
  UNAVAILABLE

designRequired
  UNAVAILABLE

designDecisionsAvailable
  TRUE/FALSE selon DesignDecision liée

planPresent
  TRUE si >=1 ImplementationTask
  UNAVAILABLE si aucune task

knownBlocker
  UNAVAILABLE

blockingAcceptanceCriterionFailed
blockingAcceptanceCriterionUnverified
  UNAVAILABLE
```

Donc :

```text
risks != blockers
0 task != preuve d'absence de plan
task.completed != lifecycle state
```

Un change sans requirement CURRENT produit :

```text
CHANGE_WITHOUT_CURRENT_REQUIREMENT
WARNING
DETERMINISTIC
```

Les dimensions non observables produisent un finding informatif :

```text
CHANGE_COMPLETENESS_PARTIALLY_OBSERVABLE
INFO
DETERMINISTIC
```

## Lifecycle quality

Le `ChangeLifecycle` source est toujours fourni explicitement par l'appelant. Il n'est jamais reconstruit depuis le snapshot.

### Mode DERIVED

La liste des faits réellement consultés par la transition M3 est connue explicitement.

Si un fait requis est `UNAVAILABLE` :

```text
state machine NON appelée
decision = empty
LIFECYCLE_REQUIRED_FACT_UNAVAILABLE
```

Si tous les faits requis sont disponibles :

```text
state machine M3 = source de vérité
```

### Mode EXPLICIT

L'appelant fournit directement un `ChangeLifecycleFacts` complet :

```text
aucune dérivation
aucune substitution
state machine M3 appelée avec ces faits exacts
```

Chaque blocker M3 est conservé dans `ChangeLifecycleTransitionDecision` et projeté aussi en :

```text
LIFECYCLE_TRANSITION_BLOCKED
WARNING
DETERMINISTIC
blocker=<ChangeLifecycleBlocker exact>
```

## Preuve

```text
ChangeLifecycleQualityContractTest       7/7 PASS
Architecture tests                    121/121 PASS
TOTAL                                 248/248 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
Total time                            20.687 s
Finished at                2026-07-23T22:29:23+02:00
```

Warnings connus non bloquants uniquement : Xerial SQLite/JDK restricted native access et SLF4J NOP.

Aucune migration, aucun store adapter, aucun provider, aucun `pom.xml` modifié.

---

# 8. M6-S4 — Décisions et références

Prochaine slice après merge S3.

Cibles :

```text
decision sans justification/trace suffisante
external unresolved
external stale
broken reference
qualité de résolution explicite
```

Le design devra réutiliser la persistance/lecture M4 des références externes et ne pas transformer l'absence de justification en preuve d'une mauvaise décision sans signal structurel explicite.

---

# 9. M6-S5 — Rapport qualité agrégé

Stabiliser :

```text
QualityReport
metrics stables
findings triés
counts par code/severity/evidence kind
compact exposure
published snapshot metadata
```

Aucune persistance de rapport si elle n'est pas nécessaire.

---

# 10. M6-S6 — Validation finale

Créer :

```text
docs/VALIDATION_M6.md
```

Répondre explicitement à la question de sortie et prouver la parité des backends ainsi que la séparation déterministe/heuristique.

---

# 11. Gouvernance

```text
1. branche dédiée depuis le merge exact précédent
2. ADR proposée avant code si décision structurelle
3. PR Draft avant implémentation substantielle
4. tests contractuels ciblés
5. gate Windows .\mvnw.cmd clean test
6. ADR acceptée uniquement après preuve
7. PR Ready uniquement après gate vert
8. merge uniquement après signal explicite
9. issue #43 + roadmap mises à jour
```

**Prochaine ligne active après merge S3 : M6-S4 — design-decision justification + broken/unresolved reference quality.**