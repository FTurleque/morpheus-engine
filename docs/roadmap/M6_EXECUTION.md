# M6 — Plan d'exécution détaillé

Statut : **M6 actif — 4/6 intégrés ; S5 implémenté, gate en attente**

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
M6-S4 merge    = ef6975d05d4bfcd994669d27e3a6600bc4ecdc1a
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
S4  ✅ design-decision justification + external reference quality — PR #47 — ADR-0051 — 254/254 — MERGED
S5  🚧 aggregate quality report + stable metrics/order + compact exposure — PR #48 — ADR-0052 proposée — gate attendu 261
S6  ⏳ validation finale VALIDATION_M6.md
```

```text
M6 : 4 / 6 slices intégrés
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

# 5. M6-S1 à S4 — INTÉGRÉS

## S1 — Requirements

```text
RequirementTraceabilityCoverage
RequirementQualityService
orphan = aucun lien direct entrant/sortant
coverage = linked / total CURRENT
zero CURRENT = 1.0
```

Gate : **234/234 PASS**.  
Merge : `5b0984ec7777eabb6f2d1417b4c900c08a038947`.

## S2 — Tasks et acceptance gap

```text
TaskRequirementCoverage
TaskQualityService
AcceptanceCoverageAssessment
AcceptanceQualityService
Change --AFFECTS--> Requirement CURRENT => task couverte
Scenario != AcceptanceCriterion
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

Gate : **241/241 PASS**.  
Merge : `916201c724722cf9ace50d44e55d001d8faf383c`.

## S3 — Change completeness et lifecycle

```text
QualityFactValue = TRUE / FALSE / UNAVAILABLE
ChangeCompletenessService
ChangeLifecycleQualityService
ChangeLifecycle explicite obligatoire
machine M3 = source de vérité
UNAVAILABLE n'est jamais remplacé par false
```

Gate : **248/248 PASS**.  
Merge : `03fd5a86e11f2afc40e3f1ecd5b1b8a1d1d211f7`.

## S4 — Decisions et références externes

```text
DecisionReferenceQualityService
DesignDecision.decision != justification
DECIDED_BY = preuve structurelle
ExternalTraceabilityAvailability M4 = source de vérité
RESOLVED = silencieux
UNVALIDATED / UNRESOLVED / STALE / BROKEN = findings déterministes
```

Gate : **254/254 PASS**.  
Merge : `ef6975d05d4bfcd994669d27e3a6600bc4ecdc1a`.

---

# 6. M6-S5 — IMPLÉMENTÉ / GATE EN ATTENTE

ADR : **ADR-0052 — Proposée — M6**  
PR : **#48 — Draft**  
Branche : `m6/aggregate-quality-report`

Contrats :

```text
LifecycleQualityAggregationStatus
QualityReportMetrics
QualityReport
QualityReportService
CompactQualityReportView
CompactQualityReportService
```

## Agrégation snapshot-cohérente

`QualityReportService` résout l'ACTIVE une fois puis appelle, sur le même snapshot ID :

```text
RequirementQualityService
TaskQualityService
AcceptanceQualityService
ChangeCompletenessService
DecisionReferenceQualityService
```

Chaque composant doit porter exactement le même `KnowledgeSnapshotMetadata`.

Le rapport ne recalcule aucune règle spécialisée.

## Lifecycle

Le rapport snapshot-only expose :

```text
LifecycleQualityAggregationStatus.REQUIRES_EXPLICIT_LIFECYCLE_INPUT
```

`ChangeLifecycleQualityService` reste l'API spécialisée pour les transitions, car son `ChangeLifecycle` doit être fourni explicitement.

## Findings

```text
component findings
-> concaténation
-> distinct()
-> ordre canonique QualityFinding
```

Aucun code n'est réinterprété.

## Métriques

```text
totalFindings
requirements total / linked / orphan / ratio
tasks total / covered / uncovered / ratio
acceptance status
change count
design-decision count
external-reference count
counts par QualityFindingCode
counts par DiagnosticSeverity
counts par QualityEvidenceKind
```

## Vue compacte

```text
schemaVersion = 1
operation = get_quality_report
snapshot metadata
metrics
structured findings
```

Chaque finding conserve :

```text
code
severity
evidenceKind
subject kind / identity
message
details
confidence
evidenceIds
```

`CompactQualityReportService` réutilise `CanonicalJsonSerializer` M5.

```text
même rapport -> même DTO
même DTO -> même JSON
même JSON -> mêmes UTF-8 bytes
```

## Fixture de preuve

Population ACTIVE :

```text
2 CURRENT requirements : 1 linked / 1 orphan
2 tasks : 1 covered / 1 uncovered
1 specification : acceptance coverage unavailable
2 changes : 1 avec CURRENT requirement / 1 sans
2 design decisions : 1 traced / 1 untraced
2 external references : 1 RESOLVED / 1 UNRESOLVED
```

Métriques attendues :

```text
requirements coverage = 1/2 = 0.5
task coverage = 1/2 = 0.5
total findings = 10
WARNING = 6
INFO = 4
DETERMINISTIC = 10
```

Preuves ajoutées : **7 tests** :

```text
Memory == SQLite aggregate report
exact aggregate metrics
stable counts by code/severity/evidence kind
lifecycle explicit-only status
compact DTO + canonical JSON/UTF-8 repeatability
ACTIVE / RETIRED / READY / unknown / missing ACTIVE policy
SQLite reopen report + JSON equality
truncated findings / inconsistent metrics rejected
cross-snapshot component rejected
```

Baseline : **254/254 PASS**.  
Gate attendu : **261/261**, dont **134 tests d'architecture**.

Aucune migration, aucun store adapter, aucun provider, aucun `pom.xml` modifié.

---

# 7. M6-S6 — Validation finale

Après merge S5, créer :

```text
docs/VALIDATION_M6.md
```

La validation finale doit couvrir séparément le service lifecycle explicite et le rapport snapshot-only.

Preuves finales :

```text
Requirement coverage/orphans
Task coverage
Acceptance gap
Change completeness
Lifecycle blockers explicites
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

# 8. Gouvernance

```text
1. branche dédiée depuis le merge exact précédent
2. ADR proposée avant code si décision structurelle
3. PR Draft avant implémentation substantielle
4. tests contractuels ciblés
5. gate Windows .\mvnw.cmd clean test
6. ADR acceptée uniquement après preuve
7. PR Ready uniquement après gate vert
8. merge sous autorisation utilisateur
9. issue #43 + roadmap mises à jour
```

L'autorisation utilisateur courante couvre la poursuite jusqu'à la clôture de M6, mais chaque merge reste conditionné au gate local officiel.

**Prochaine porte : gate local M6-S5 attendu 261/261.**
