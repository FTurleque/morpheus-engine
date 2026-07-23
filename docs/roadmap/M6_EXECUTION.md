# M6 — Plan d'exécution détaillé

Statut : **M6 actif — 1/6 intégré ; S2 implémenté, gate en attente**

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
S2  🚧 implementation-task coverage + acceptance capability gap — PR #45 — ADR-0049 proposée — gate attendu 241
S3  ⏳ change completeness + lifecycle blocking conditions
S4  ⏳ design-decision justification + broken/unresolved reference quality
S5  ⏳ aggregate quality report + stable metrics/order + compact exposure
S6  ⏳ validation finale VALIDATION_M6.md
```

```text
M6 : 1 / 6 slices intégrés
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
provider-neutral
backend-neutral
```

Aucune heuristique n'est présentée comme certitude.

---

# 5. M6-S1 — INTÉGRÉ

ADR : **ADR-0048 — Acceptée — M6**  
PR : **#44 — MERGED**  
Merge : `5b0984ec7777eabb6f2d1417b4c900c08a038947`

Head de code testé :

```text
34ecc48057f27990221cbe7669b555eb73950581
```

Contrats :

```text
QualityFinding
QualityFindingCode
QualityEvidenceKind
RequirementTraceabilityCoverage
RequirementQualityService
```

Sémantique validée :

```text
ACTIVE by default
ACTIVE/RETIRED explicit only
CURRENT Requirement only
linked = >= 1 direct incoming/outgoing persisted TraceabilityLink
orphan = no direct incoming AND no direct outgoing link
coverage = linked / total CURRENT requirements
zero CURRENT requirements = coverage 1.0
ORPHAN_REQUIREMENT = WARNING + DETERMINISTIC
finding evidence = Requirement provenance evidence
```

Contrat de preuve :

```text
DETERMINISTIC => confidence interdite
HEURISTIC => confidence obligatoire et bornée [0,1]
```

Gate local Windows :

```text
RequirementQualityContractTest          7/7 PASS
Architecture tests                    107/107 PASS
TOTAL                                 234/234 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
Total time                            27.269 s
Finished at                2026-07-23T20:52:28+02:00
```

---

# 6. M6-S2 — IMPLÉMENTÉ / GATE EN ATTENTE

ADR : **ADR-0049 — Proposée — M6**  
PR : **#45 — Draft**  
Branche : `m6/task-acceptance-quality`

Contrats :

```text
TaskRequirementCoverage
TaskQualityService
AcceptanceCoverageStatus
AcceptanceCoverageAssessment
AcceptanceQualityService
```

Extensions de `QualityFindingCode` :

```text
IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT
ACCEPTANCE_COVERAGE_UNAVAILABLE
```

## Couverture task -> requirement

Aucune relation `Task -> Requirement` n'est inventée.

Pour une task `T` :

```text
T.changeId = C
```

La task est couverte seulement si :

```text
Change(C) --AFFECTS--> Requirement(R)
R possède une occurrence CURRENT dans le même snapshot
```

Donc :

```text
AFFECTS -> CURRENT          = couvert
AFFECTS -> PROPOSED only    = non couvert
AFFECTS -> cible absente    = non couvert
aucun AFFECTS               = non couvert
```

Finding d'une task non couverte :

```text
code = IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT
severity = WARNING
evidenceKind = DETERMINISTIC
subject = IMPLEMENTATION_TASK(taskId)
confidence = empty
evidence = task.provenance.evidenceId
```

Calcul :

```text
totalTasks
coveredTasks
uncoveredTasks
coverageRatio = covered / total
zero task => coverage 1.0
```

## Gap acceptance

Le contrat production possède `ProviderCapability.READ_ACCEPTANCE_CRITERIA`, mais aucun type normalisé/persisté `AcceptanceCriterion` n'existe encore.

S2 expose donc :

```text
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

et produit par specification :

```text
ACCEPTANCE_COVERAGE_UNAVAILABLE
WARNING
DETERMINISTIC
```

S2 ne calcule aucun faux dénominateur, aucun ratio `0 %` et ne convertit jamais un `Scenario` en critère d'acceptation.

```text
Scenario != AcceptanceCriterion
```

Preuves ajoutées : **7 tests** dans `TaskAcceptanceQualityContractTest` :

```text
Memory == SQLite task coverage
CURRENT target covers
PROPOSED-only target does not cover
missing/unresolved target does not cover
no AFFECTS does not cover
ACTIVE default / RETIRED allowed / READY rejected
missing ACTIVE distinct from empty population
zero tasks => 100 %
SQLite reopen task assessment
acceptance status explicit
Scenario never converted
AcceptanceCriterion production type absent
acceptance Memory == SQLite
```

Baseline : **234/234 PASS**.  
Gate attendu : **241/241**, dont **114 tests d'architecture**.

Aucune migration, aucun store adapter, aucun provider, aucun `pom.xml` modifié.

---

# 7. M6-S3 — Changement et lifecycle

Cibles :

```text
change incomplet
conditions de blocage de transition
état lifecycle explicite
faits structurels uniquement pour diagnostics déterministes
```

---

# 8. M6-S4 — Décisions et références

Cibles :

```text
decision sans justification/trace suffisante
external unresolved
external stale
broken reference
qualité de résolution explicite
```

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

**Prochaine porte : gate local M6-S2 attendu 241/241.**
