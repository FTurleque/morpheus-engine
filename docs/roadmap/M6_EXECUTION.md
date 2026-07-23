# M6 — Plan d'exécution détaillé

Statut : **M6 actif — 4/6 validés ; S1-S3 intégrés, S4 Ready, S5 prochain après merge**

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
M6-S3 merge    = 03fd5a86e11f2afc40e3f1ecd5b1b8a1d1d211f7
M6-S3 gate     = 248/248 PASS
M6-S4 gate     = 254/254 PASS
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
design-decision justification availability
broken/unresolved references
stable aggregate metrics/order
compact quality exposure
Memory == SQLite
SQLite reopen
no LLM/semantic dependency
```

---

# 3. Progression M6

```text
S1  ✅ requirement traceability coverage + orphan requirements — PR #44 — ADR-0048 — 234/234 — MERGED
S2  ✅ implementation-task coverage + acceptance capability gap — PR #45 — ADR-0049 — 241/241 — MERGED
S3  ✅ change completeness + lifecycle blocking conditions — PR #46 — ADR-0050 — 248/248 — MERGED
S4  ✅ design-decision justification + external reference quality — PR #47 — ADR-0051 — 254/254 — READY
S5  ⏳ aggregate quality report + stable metrics/order + compact exposure — PROCHAIN APRÈS MERGE S4
S6  ⏳ validation finale VALIDATION_M6.md
```

```text
M6 : 4 / 6 slices validés
```

---

# 4. Invariants M6

```text
quality finding != ingestion diagnostic
finding = résultat dérivé, non persisté
snapshot-scoped
CURRENT only pour Requirement
absence de lien != lien inventé
Scenario != AcceptanceCriterion
DesignDecision.decision != justification
risks != blockers
0 task != preuve d'absence de plan
lifecycle state != snapshot state
lifecycle state != temporal state
lifecycle state != task completion
DETERMINISTIC != HEURISTIC
DETERMINISTIC => confidence interdite
HEURISTIC => confidence obligatoire [0,1]
provider-neutral
backend-neutral
```

Aucun fait indisponible n'est remplacé par `false` et aucune heuristique n'est présentée comme certitude.

---

# 5. M6-S1 — INTÉGRÉ

ADR : **ADR-0048 — Acceptée — M6**  
PR : **#44 — MERGED**  
Merge : `5b0984ec7777eabb6f2d1417b4c900c08a038947`

Contrats :

```text
QualityFinding
QualityFindingCode
QualityEvidenceKind
RequirementTraceabilityCoverage
RequirementQualityService
```

Règles principales :

```text
ACTIVE default
ACTIVE/RETIRED explicit only
CURRENT Requirement only
orphan = aucun lien direct entrant/sortant
coverage = linked / total CURRENT
zero CURRENT = 1.0
ORPHAN_REQUIREMENT = WARNING + DETERMINISTIC
```

Gate : **234/234 PASS**.

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

Règles :

```text
task.changeId = C
Change(C) --AFFECTS--> Requirement CURRENT => task couverte
PROPOSED-only / cible absente / aucun AFFECTS => non couverte
Scenario != AcceptanceCriterion
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

Gate : **241/241 PASS**.

---

# 7. M6-S3 — INTÉGRÉ

ADR : **ADR-0050 — Acceptée — M6**  
PR : **#46 — MERGED**  
Merge : `03fd5a86e11f2afc40e3f1ecd5b1b8a1d1d211f7`

Contrats :

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

Règles :

```text
requirementsIdentified = TRUE/FALSE via AFFECTS -> CURRENT
designDecisionsAvailable = TRUE/FALSE
planPresent = TRUE si task, sinon UNAVAILABLE
criticalConstraintsKnown = UNAVAILABLE
acceptanceCriteriaDefined = UNAVAILABLE
designRequired = UNAVAILABLE
knownBlocker = UNAVAILABLE
blocking acceptance facts = UNAVAILABLE
```

Le `ChangeLifecycle` est fourni explicitement par l'appelant. La machine M3 reste source de vérité ; un fait requis `UNAVAILABLE` empêche la fausse évaluation de transition.

Gate : **248/248 PASS**.

---

# 8. M6-S4 — VALIDÉ TECHNIQUEMENT / READY

ADR : **ADR-0051 — Acceptée — M6**  
PR : **#47 — Ready après finalisation post-gate**

Head de code testé :

```text
53356fe77df0d5a3cc474b8aca3224d970fb88d7
```

Contrats :

```text
DecisionJustificationStatus
DesignDecisionQualityAssessment
ExternalReferenceQualityAssessment
DecisionReferenceQualityReport
DecisionReferenceQualityService
```

Findings :

```text
DESIGN_DECISION_WITHOUT_TRACE
DECISION_JUSTIFICATION_UNAVAILABLE
EXTERNAL_REFERENCE_UNVALIDATED
EXTERNAL_REFERENCE_UNRESOLVED
EXTERNAL_REFERENCE_STALE
EXTERNAL_REFERENCE_BROKEN
```

Règles décision :

```text
Change --DECIDED_BY--> DesignDecision = trace structurelle
absence de DECIDED_BY = WARNING déterministe
justification normalisée absente = INFO déterministe
absence de justification != mauvaise décision
```

Règles externes :

```text
REFERENCE_RESOLVED    -> aucun finding
REFERENCE_UNVALIDATED -> EXTERNAL_REFERENCE_UNVALIDATED
REFERENCE_UNRESOLVED  -> EXTERNAL_REFERENCE_UNRESOLVED
REFERENCE_STALE       -> EXTERNAL_REFERENCE_STALE
BROKEN_REFERENCE      -> EXTERNAL_REFERENCE_BROKEN
```

`ExternalTraceabilityQueryService` M4 reste la source de vérité ; S4 ne refait aucune résolution.

Preuve :

```text
DecisionReferenceQualityContractTest     6/6 PASS
Architecture tests                    127/127 PASS
TOTAL                                 254/254 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
Total time                            21.444 s
Finished at                2026-07-23T22:52:42+02:00
```

Warnings connus non bloquants : Xerial SQLite/JDK restricted native access et SLF4J NOP.

Aucune migration, aucun store adapter, aucun provider, aucun `pom.xml` modifié.

---

# 9. M6-S5 — Rapport qualité agrégé

Prochaine slice après merge S4.

Stabiliser :

```text
QualityReport
snapshot metadata
stable metrics
counts par code
counts par severity
counts par evidence kind
findings triés/dédupliqués
compact quality view
canonical deterministic JSON
```

Le rapport agrège les services S1 à S4 ; il ne recalcule pas leurs règles et n'est pas persisté.

Les dimensions volontairement indisponibles restent explicitement visibles dans les findings existants.

---

# 10. M6-S6 — Validation finale

Créer :

```text
docs/VALIDATION_M6.md
```

Prouver explicitement :

```text
Requirement coverage/orphans
Task coverage
Acceptance gap
Change completeness
Lifecycle blockers
Decision trace / justification availability
External degraded states
Aggregate metrics/order
Compact deterministic serialization
Memory == SQLite
SQLite reopen
ACTIVE/RETIRED policy
CURRENT isolation
DETERMINISTIC/HEURISTIC separation
no LLM / semantic dependency
```

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
8. merge uniquement sous autorisation utilisateur
9. issue #43 + roadmap mises à jour
```

L'autorisation utilisateur courante couvre la poursuite jusqu'à la clôture de M6, mais chaque merge reste conditionné au gate local officiel de la slice correspondante.

**Prochaine ligne active après merge S4 : M6-S5 — aggregate quality report + compact exposure.**
