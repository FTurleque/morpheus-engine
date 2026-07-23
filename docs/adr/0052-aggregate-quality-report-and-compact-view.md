# ADR-0052 — Rapport qualité agrégé et vue compacte déterministe

- Statut : **Acceptée — M6**
- Date : 23 juillet 2026
- Dépend de : ADR-0047, ADR-0048, ADR-0049, ADR-0050, ADR-0051
- Portée : M6-S5

## Contexte

M6-S1 à S4 fournissent déjà des analyses spécialisées et validées :

```text
RequirementQualityService
TaskQualityService
AcceptanceQualityService
ChangeCompletenessService
ChangeLifecycleQualityService
DecisionReferenceQualityService
```

M6-S5 expose une vue qualité globale, stable et compacte sans dupliquer les règles de ces services.

Baseline :

```text
M6-S4 merge = ef6975d05d4bfcd994669d27e3a6600bc4ecdc1a
M6-S4 gate  = 254/254 PASS
```

## Décision

Ajouter :

```text
LifecycleQualityAggregationStatus
QualityReportMetrics
QualityReport
QualityReportService
CompactQualityReportView
CompactQualityReportService
```

## Agrégation

`QualityReportService` résout le snapshot publié puis délègue aux services existants :

```text
RequirementQualityService.assessSnapshot
TaskQualityService.assessSnapshot
AcceptanceQualityService.assessSnapshot
ChangeCompletenessService.assessSnapshot
DecisionReferenceQualityService.assessSnapshot
```

Les résultats spécialisés restent la source de vérité. S5 ne recalcule ni orphelin requirement, ni task coverage, ni acceptance availability, ni change completeness, ni external availability, ni decision trace.

## Snapshot coherence

`assessActive(projectId)` résout l'ACTIVE une seule fois, puis agrège explicitement cet ID.

Chaque composant doit retourner exactement le même `KnowledgeSnapshotMetadata`. Une divergence est une erreur de cohérence, jamais un warning.

`assessSnapshot(snapshotId)` reste limité aux snapshots publiés :

```text
ACTIVE / RETIRED seulement
```

## Lifecycle

`ChangeLifecycleQualityService` nécessite un `ChangeLifecycle` explicitement fourni par l'appelant. Un rapport snapshot global ne possède pas cette donnée et ne doit jamais inventer un lifecycle.

S5 expose :

```text
LifecycleQualityAggregationStatus.REQUIRES_EXPLICIT_LIFECYCLE_INPUT
```

Les analyses lifecycle restent disponibles via le service S3 spécialisé et sont validées séparément dans M6.

## Findings agrégés

Les findings des composants snapshot-scoped sont :

```text
concaténés
puis distinct()
puis triés par l'ordre canonique QualityFinding
```

Aucun code n'est réinterprété.

## Métriques

`QualityReportMetrics` expose :

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

Les maps de compte sont stables et n'incluent que les valeurs observées.

## Vue compacte

`CompactQualityReportView` expose :

```text
query metadata : schemaVersion=1, operation=get_quality_report
published snapshot metadata
metrics
findings structurés
```

Chaque finding compact conserve :

```text
code
severity
evidenceKind
subject kind + identity
message
details
confidence
evidenceIds
```

`CompactQualityReportService` réutilise `CanonicalJsonSerializer` M5 :

```text
même QualityReport -> même DTO compact
même DTO -> même String JSON
même String -> mêmes bytes UTF-8
```

Aucun pretty printing, timestamp runtime, hash runtime ou ordre de map non déterministe.

## Persistance et frontières

Le rapport est calculé à la demande : aucune nouvelle table, migration ou payload JSON métier persisté.

M6-S5 n'ajoute aucune nouvelle règle qualité, inférence lifecycle, résolution externe, persistance de rapport, ranking arbitraire, LLM, semantic search ni fonction NEXUS.

## Preuve d'acceptation

Gate local Windows exécuté sur le head de code :

```text
head = 0ba5b8a78116a21f0fb1fb36fef58772b2f4da64

AggregateQualityReportContractTest        6/6 PASS
QualityReportSnapshotCoherenceTest        1/1 PASS
Architecture tests                      134/134 PASS
TOTAL                                   261/261 PASS
Failures                                  0
Errors                                    0
Skipped                                   0
BUILD SUCCESS
Total time                              19.543 s
Finished at                  2026-07-23T23:32:49+02:00
```

Preuves couvertes : Memory == SQLite, ACTIVE/RETIRED, cohérence stricte de snapshot, métriques exactes, findings distincts/triés, lifecycle explicit-only, vue compacte, JSON/UTF-8 déterministes et SQLite reopen.

Warnings connus non bloquants : Xerial SQLite/JDK restricted native access et SLF4J NOP.

**Décision acceptée.**
