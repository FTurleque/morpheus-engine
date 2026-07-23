# M6 — Plan d'exécution détaillé

Statut : **M6 VALIDÉ — 6/6 slices validées ; intégration finale via PR #49**

Dernière mise à jour : 23 juillet 2026

Issue de pilotage : **#43**.  
Validation finale : [`../VALIDATION_M6.md`](../VALIDATION_M6.md).

---

# 1. Question de sortie

> **MORPHEUS peut-il détecter et expliquer les lacunes de qualité d'une spécification sur un snapshot publié, mesurer sa couverture, exposer les blocages et références cassées, tout en distinguant strictement les constats déterministes des heuristiques et sans inventer les relations absentes ?**

**Réponse : OUI.**

---

# 2. Progression finale

| Slice | Contenu | PR | ADR | Gate | État |
|---|---|---|---|---|---|
| S1 | requirement traceability coverage + orphan requirements | #44 | ADR-0048 | 234/234 | ✅ MERGED |
| S2 | implementation-task coverage + acceptance capability gap | #45 | ADR-0049 | 241/241 | ✅ MERGED |
| S3 | change completeness + lifecycle blocking conditions | #46 | ADR-0050 | 248/248 | ✅ MERGED |
| S4 | decision justification availability + external reference quality | #47 | ADR-0051 | 254/254 | ✅ MERGED |
| S5 | aggregate quality report + compact exposure | #48 | ADR-0052 | 261/261 | ✅ MERGED |
| S6 | validation finale `VALIDATION_M6.md` | #49 | — | 261/261 | ✅ VALIDÉ — documentaire |

Merges intégrés S1-S5 :

```text
S1 = 5b0984ec7777eabb6f2d1417b4c900c08a038947
S2 = 916201c724722cf9ace50d44e55d001d8faf383c
S3 = 03fd5a86e11f2afc40e3f1ecd5b1b8a1d1d211f7
S4 = ef6975d05d4bfcd994669d27e3a6600bc4ecdc1a
S5 = ab91b6c537c73c586b925dd6367021e2780808aa
```

---

# 3. Invariants M6

```text
QualityFinding != diagnostic d'ingestion
finding = résultat dérivé, non persisté
snapshot-scoped
CURRENT only pour Requirement
PROPOSED never leaks into CURRENT
absence de lien != lien inventé
Scenario != AcceptanceCriterion
DesignDecision.decision != justification
risks != blockers
0 task != preuve d'absence de plan
lifecycle explicite != snapshot state
lifecycle explicite != task completion
DETERMINISTIC != HEURISTIC
DETERMINISTIC => confidence interdite
HEURISTIC => confidence obligatoire [0,1]
provider-neutral
backend-neutral
```

Aucun fait indisponible n'est remplacé par `false` et aucune heuristique n'est présentée comme certitude.

---

# 4. S1 — Requirements

Contrats :

```text
QualityFinding
QualityFindingCode
QualityEvidenceKind
RequirementTraceabilityCoverage
RequirementQualityService
```

Règles :

```text
ACTIVE par défaut
ACTIVE / RETIRED explicites
CURRENT Requirement only
linked = >=1 lien direct entrant ou sortant
orphan = aucun lien direct entrant/sortant
coverage = linked / total CURRENT
zero CURRENT = 1.0
ORPHAN_REQUIREMENT = WARNING + DETERMINISTIC
```

Gate : **234/234 PASS**.

---

# 5. S2 — Tasks et acceptance gap

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
Change(task.changeId) --AFFECTS--> Requirement CURRENT => task couverte
sinon IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT
Scenario != AcceptanceCriterion
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
ACCEPTANCE_COVERAGE_UNAVAILABLE
```

Gate : **241/241 PASS**.

---

# 6. S3 — Change completeness et lifecycle

Contrats :

```text
QualityFactValue = TRUE / FALSE / UNAVAILABLE
LifecycleFactSource = DERIVED / EXPLICIT
ChangeCompletenessService
ChangeLifecycleQualityService
```

Le snapshot observe seulement les faits réellement prouvables. Les autres restent `UNAVAILABLE`.

```text
CHANGE_WITHOUT_CURRENT_REQUIREMENT
CHANGE_COMPLETENESS_PARTIALLY_OBSERVABLE
LIFECYCLE_REQUIRED_FACT_UNAVAILABLE
LIFECYCLE_TRANSITION_BLOCKED
```

Le `ChangeLifecycle` est fourni explicitement par l'appelant ; la machine M3 reste source de vérité.

Gate : **248/248 PASS**.

---

# 7. S4 — Decisions et références externes

Contrats :

```text
DecisionJustificationStatus
DesignDecisionQualityAssessment
ExternalReferenceQualityAssessment
DecisionReferenceQualityReport
DecisionReferenceQualityService
```

Décisions :

```text
Change --DECIDED_BY--> DesignDecision = preuve structurelle
absence de DECIDED_BY => DESIGN_DECISION_WITHOUT_TRACE
justification normalisée absente => DECISION_JUSTIFICATION_UNAVAILABLE
aucune justification inférée depuis title/decision/provenance
```

Références externes :

```text
REFERENCE_RESOLVED    -> aucun finding
REFERENCE_UNVALIDATED -> EXTERNAL_REFERENCE_UNVALIDATED
REFERENCE_UNRESOLVED  -> EXTERNAL_REFERENCE_UNRESOLVED
REFERENCE_STALE       -> EXTERNAL_REFERENCE_STALE
BROKEN_REFERENCE      -> EXTERNAL_REFERENCE_BROKEN
```

Le contrat M4 reste la source de vérité ; aucune nouvelle résolution n'est effectuée.

Gate : **254/254 PASS**.

---

# 8. S5 — Rapport qualité agrégé

Contrats :

```text
LifecycleQualityAggregationStatus
QualityReportMetrics
QualityReport
QualityReportService
CompactQualityReportView
CompactQualityReportService
```

`QualityReportService` résout l'ACTIVE une fois puis appelle sur le même snapshot ID les services S1-S4 snapshot-scoped.

Chaque composant doit porter exactement le même `KnowledgeSnapshotMetadata`.

Findings :

```text
concaténation -> distinct() -> ordre canonique
```

Métriques :

```text
total findings
coverage requirements
coverage tasks
acceptance status
changes
design decisions
external references
counts par code / severity / evidence kind
```

Lifecycle global :

```text
REQUIRES_EXPLICIT_LIFECYCLE_INPUT
```

Vue compacte :

```text
schemaVersion = 1
operation = get_quality_report
snapshot metadata
metrics
structured findings
CanonicalJsonSerializer M5
```

Fixture non triviale validée :

```text
2 requirements : 1 linked / 1 orphan
2 tasks : 1 covered / 1 uncovered
2 changes
2 decisions : 1 traced / 1 untraced
2 external refs : 1 RESOLVED / 1 UNRESOLVED
10 findings = 6 WARNING + 4 INFO
DETERMINISTIC = 10
```

Gate : **261/261 PASS**, architecture **134/134**.

---

# 9. S6 — Validation finale

S6 ajoute uniquement :

```text
docs/VALIDATION_M6.md
mises à jour roadmap / index ADR / issue
```

Aucun code, test, store, migration, provider ou `pom.xml` n'est modifié après le gate S5.

Le gate technique final reste donc :

```text
Domain tests                           21/21 PASS
Application tests                      66/66 PASS
OpenSpec provider tests                26/26 PASS
Synthetic provider tests                7/7 PASS
SQLite store tests                       7/7 PASS
Architecture tests                    134/134 PASS
TOTAL                                 261/261 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
Finished                    2026-07-23T23:32:49+02:00
```

Warnings non bloquants : Xerial SQLite/JDK restricted native access et SLF4J NOP.

---

# 10. Frontières confirmées

M6 n'introduit pas :

```text
AcceptanceCriterion artificiel
justification de décision inférée
lifecycle reconstruit depuis snapshot
TraceabilityLink inventé
nouvelle résolution externe
persistance de finding/report
migration SQLite
heuristique textuelle implicite
LLM
semantic search
NEXUS ranking/fusion/compression
MINOS code intelligence
JARVIS orchestration
```

---

# 11. Conclusion

La porte M6 est franchie.

```text
Requirement coverage/orphans      ✅
Task coverage                     ✅
Acceptance capability gap         ✅
Change completeness               ✅
Lifecycle blockers explicites     ✅
Decision trace                    ✅
Justification indisponible explicite ✅
External degraded states          ✅
Aggregate metrics/order           ✅
Compact deterministic JSON        ✅
Memory == SQLite                  ✅
SQLite reopen                     ✅
ACTIVE/RETIRED policy             ✅
CURRENT isolation                 ✅
DETERMINISTIC/HEURISTIC contract  ✅
No LLM / semantic dependency      ✅
```

**M6 — Qualité, couverture et diagnostics explicables : VALIDÉ.**
