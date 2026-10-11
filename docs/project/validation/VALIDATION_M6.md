# Validation M6 — Qualité, couverture et diagnostics explicables

Statut : **VALIDÉ — M6-S6**

Date : 23 juillet 2026

## Question de sortie

> **MORPHEUS peut-il détecter et expliquer les lacunes de qualité d'une spécification sur un snapshot publié, mesurer sa couverture, exposer les blocages et références cassées, tout en distinguant strictement les constats déterministes des heuristiques et sans inventer les relations absentes ?**

## Réponse

**OUI.**

M6 fournit des analyses de qualité snapshot-scoped, déterministes et explicables, une couverture chiffrée, des lacunes structurées, un rapport agrégé stable et une exposition compacte canonique. Les dimensions non observables restent explicitement indisponibles ; MORPHEUS ne fabrique ni acceptance criteria, ni justification de décision, ni lifecycle, ni relation de trace manquante.

---

# 1. Baseline et progression

```text
M5 final merge = 6bbaf086cf1fed81e3517bb1cef5b643264fb836
M5 final gate  = 227/227 PASS

M6-S1 merge    = 5b0984ec7777eabb6f2d1417b4c900c08a038947
M6-S1 gate     = 234/234 PASS

M6-S2 merge    = 916201c724722cf9ace50d44e55d001d8faf383c
M6-S2 gate     = 241/241 PASS

M6-S3 merge    = 03fd5a86e11f2afc40e3f1ecd5b1b8a1d1d211f7
M6-S3 gate     = 248/248 PASS

M6-S4 merge    = ef6975d05d4bfcd994669d27e3a6600bc4ecdc1a
M6-S4 gate     = 254/254 PASS

M6-S5 merge    = ab91b6c537c73c586b925dd6367021e2780808aa
M6-S5 gate     = 261/261 PASS
```

S6 n'introduit aucun changement exécutable. Le `261/261 PASS` de S5 est donc le gate technique final du code M6.

---

# 2. Modèle de finding

Contrats introduits en M6-S1 :

```text
QualityFinding
QualityFindingCode
QualityEvidenceKind
```

Invariants :

```text
QualityFinding != diagnostic d'ingestion
finding = résultat dérivé, non persisté
DETERMINISTIC != HEURISTIC
DETERMINISTIC => confidence interdite
HEURISTIC => confidence obligatoire et comprise dans [0,1]
```

Les findings déterministes conservent le sujet, le message, les détails et les `EvidenceId` disponibles.

M6 n'introduit aucune heuristique métier implicite. Le contrat autorise des findings heuristiques futurs uniquement avec une confidence explicite.

---

# 3. Couverture des requirements — M6-S1

Services :

```text
RequirementTraceabilityCoverage
RequirementQualityService
```

Population : uniquement les occurrences `Requirement` en `CURRENT` du snapshot publié.

```text
linked = au moins un TraceabilityLink direct entrant ou sortant
orphan = aucun lien direct entrant ET aucun lien direct sortant
coverage = linked / total CURRENT
zero CURRENT = 1.0
```

Un requirement orphelin produit :

```text
ORPHAN_REQUIREMENT
WARNING
DETERMINISTIC
```

Les occurrences `PROPOSED` ne contaminent jamais le calcul CURRENT.

Preuve : **234/234 PASS**.

---

# 4. Couverture des tâches et acceptance capability gap — M6-S2

Services :

```text
TaskRequirementCoverage
TaskQualityService
AcceptanceCoverageStatus
AcceptanceCoverageAssessment
AcceptanceQualityService
```

Une task est couverte si son changement possède un lien direct :

```text
Change(task.changeId) --AFFECTS--> Requirement CURRENT
```

Sinon :

```text
IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT
WARNING
DETERMINISTIC
```

La couverture d'acceptance criteria n'est pas inventée :

```text
Scenario != AcceptanceCriterion
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
ACCEPTANCE_COVERAGE_UNAVAILABLE
```

L'absence de type normalisé `AcceptanceCriterion` est donc un capability gap explicite, pas un faux résultat de couverture.

Preuve : **241/241 PASS**.

---

# 5. Complétude des changements et lifecycle — M6-S3

Services :

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

Pour chaque changement publié, la complétude snapshot-derived observe notamment :

```text
requirementsIdentified
constraintCount
designDecisionCount
implementationTaskCount
designDecisionsAvailable
planPresent
```

Les dimensions non prouvables restent `UNAVAILABLE` :

```text
criticalConstraintsKnown
acceptanceCriteriaDefined
designRequired
knownBlocker
blockingAcceptanceCriterionFailed
blockingAcceptanceCriterionUnverified
```

MORPHEUS ne confond donc pas :

```text
risks != blockers
0 task != preuve d'absence de plan
task.completed != lifecycle state
```

Un changement sans requirement CURRENT produit :

```text
CHANGE_WITHOUT_CURRENT_REQUIREMENT
WARNING
DETERMINISTIC
```

Une complétude partiellement observable produit :

```text
CHANGE_COMPLETENESS_PARTIALLY_OBSERVABLE
INFO
DETERMINISTIC
```

## Lifecycle explicite

Le `ChangeLifecycle` est fourni par l'appelant. Il n'est jamais reconstruit depuis le snapshot.

En mode dérivé, si un fait requis par une transition M3 est `UNAVAILABLE`, la machine d'état n'est pas appelée et MORPHEUS expose :

```text
LIFECYCLE_REQUIRED_FACT_UNAVAILABLE
```

Quand tous les faits requis sont disponibles, la machine M3 reste la source de vérité. Ses blockers exacts sont conservés et projetés en :

```text
LIFECYCLE_TRANSITION_BLOCKED
WARNING
DETERMINISTIC
```

Preuve : **248/248 PASS**.

---

# 6. Décisions et références externes — M6-S4

Services :

```text
DecisionJustificationStatus
DesignDecisionQualityAssessment
ExternalReferenceQualityAssessment
DecisionReferenceQualityReport
DecisionReferenceQualityService
```

## Design decisions

Une décision est structurellement tracée si le snapshot contient :

```text
Change(decision.changeId) --DECIDED_BY--> DesignDecision(decision.id)
```

Sinon :

```text
DESIGN_DECISION_WITHOUT_TRACE
WARNING
DETERMINISTIC
```

`DesignDecision` ne possède pas de champ normalisé `rationale/justification`. MORPHEUS n'utilise donc ni `title`, ni `decision`, ni la provenance pour inventer une justification.

```text
DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
DECISION_JUSTIFICATION_UNAVAILABLE
INFO
DETERMINISTIC
```

## Références externes

S4 réutilise strictement le contrat M4 :

```text
REFERENCE_RESOLVED    -> aucun finding
REFERENCE_UNVALIDATED -> EXTERNAL_REFERENCE_UNVALIDATED
REFERENCE_UNRESOLVED  -> EXTERNAL_REFERENCE_UNRESOLVED
REFERENCE_STALE       -> EXTERNAL_REFERENCE_STALE
BROKEN_REFERENCE      -> EXTERNAL_REFERENCE_BROKEN
```

Un lien cassé reste auditable même si l'`ExternalReference` persistée n'existe plus ; les evidence IDs du lien restent visibles.

Aucune nouvelle résolution externe n'est déclenchée par l'analyse qualité.

Preuve : **254/254 PASS**.

---

# 7. Rapport agrégé et exposition compacte — M6-S5

Services :

```text
LifecycleQualityAggregationStatus
QualityReportMetrics
QualityReport
QualityReportService
CompactQualityReportView
CompactQualityReportService
```

`QualityReportService` résout l'ACTIVE une fois puis appelle sur le même snapshot ID :

```text
RequirementQualityService
TaskQualityService
AcceptanceQualityService
ChangeCompletenessService
DecisionReferenceQualityService
```

Le rapport ne recalcule aucune règle S1-S4.

## Cohérence snapshot

Chaque composant doit porter exactement le même `KnowledgeSnapshotMetadata`. Une divergence rend le rapport invalide ; elle n'est jamais dégradée en warning.

## Findings

```text
findings composants
-> concaténation
-> distinct()
-> ordre canonique QualityFinding
```

## Métriques stables

```text
totalFindings
requirements total / linked / orphan / coverage ratio
tasks total / covered / uncovered / coverage ratio
acceptance status
change count
design-decision count
external-reference count
counts par QualityFindingCode
counts par DiagnosticSeverity
counts par QualityEvidenceKind
```

Le rapport global n'invente pas de lifecycle :

```text
LifecycleQualityAggregationStatus.REQUIRES_EXPLICIT_LIFECYCLE_INPUT
```

## Vue compacte

```text
schemaVersion = 1
operation = get_quality_report
published snapshot metadata
metrics
structured findings
```

Chaque finding compact conserve :

```text
code
severity
evidenceKind
subject kind
subject identity
message
details
confidence
evidenceIds
```

`CompactQualityReportService` réutilise `CanonicalJsonSerializer` de M5 :

```text
même rapport -> même DTO
même DTO -> même JSON
même JSON -> mêmes bytes UTF-8
```

Aucun JSON métier générique n'est persisté.

Preuve : **261/261 PASS**.

---

# 8. Preuve cross-backend et reopen

Les contrats M6 valident les analyses sur Memory et SQLite.

Les tests S1, S2, S3, S4 et S5 vérifient selon leur périmètre :

```text
Memory == SQLite
SQLite close/reopen -> même résultat
ordre déterministe
snapshot publié uniquement
```

La fixture S5 non triviale valide notamment :

```text
2 requirements : 1 linked / 1 orphan
2 tasks        : 1 covered / 1 uncovered
1 specification : acceptance indisponible
2 changes      : 1 relié / 1 sans CURRENT requirement
2 decisions    : 1 traced / 1 untraced
2 external refs: 1 RESOLVED / 1 UNRESOLVED

total findings = 10
WARNING        = 6
INFO           = 4
DETERMINISTIC  = 10
```

---

# 9. Snapshot policy

Toutes les analyses snapshot-scoped suivent la même politique :

```text
ACTIVE par défaut
ACTIVE / RETIRED en inspection explicite
READY / BUILDING / VALIDATING / FAILED rejetés
unknown snapshot rejeté
absence d'ACTIVE != rapport vide
```

Pour les requirements :

```text
CURRENT only
PROPOSED never leaks into CURRENT
```

---

# 10. Gate technique final

Commande officielle :

```powershell
.\mvnw.cmd clean test
```

Résultat final du code M6 :

```text
Domain tests                           21/21 PASS
Application tests                      66/66 PASS
OpenSpec provider tests                26/26 PASS
Synthetic provider tests                7/7 PASS
SQLite store tests                       7/7 PASS
Architecture tests                    134/134 PASS
------------------------------------------------
TOTAL                                 261/261 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
Total time                            19.543 s
Finished at                2026-07-23T23:32:49+02:00
```

Warnings connus non bloquants :

```text
Xerial SQLite / JDK restricted native access
SLF4J NOP provider dans les tests d'architecture
```

S6 est documentaire uniquement ; aucun changement de code, test, store, migration, provider ou dépendance n'est ajouté après ce gate.

---

# 11. Frontières confirmées

M6 ne fait pas :

```text
création artificielle d'AcceptanceCriterion
inférence de justification de DesignDecision
inférence de lifecycle depuis un snapshot
invention de TraceabilityLink absent
résolution externe supplémentaire
persistance de QualityFinding / QualityReport
nouvelle migration SQLite
heuristique textuelle implicite
LLM
semantic search
NEXUS ranking / fusion / compression
MINOS code intelligence
JARVIS orchestration
```

Responsabilités conservées :

```text
MORPHEUS = intention / spécification / qualité de spécification
MINOS    = code intelligence
NEXUS    = sélection / ranking / fusion / compression de contexte
JARVIS   = orchestration
```

---

# 12. Conclusion

La question de sortie M6 reçoit la réponse **OUI**.

MORPHEUS sait désormais :

```text
détecter les requirements orphelins
mesurer la couverture de traçabilité
mesurer la couverture requirements des implementation tasks
rendre explicite l'absence de modèle AcceptanceCriterion
mesurer la complétude observable d'un changement
exposer les facts lifecycle indisponibles et blockers explicites
contrôler la trace structurelle des design decisions
ne pas inventer leur justification
exposer les références UNVALIDATED / UNRESOLVED / STALE / BROKEN
agréger des métriques snapshot-cohérentes
produire des findings machine-readable
séparer DETERMINISTIC et HEURISTIC par contrat
produire une vue compacte JSON byte-déterministe
fonctionner avec Memory et SQLite, y compris après reopen
```

**M6 — Qualité, couverture et diagnostics explicables : VALIDÉ.**
