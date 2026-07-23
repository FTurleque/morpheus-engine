# ADR-0052 — Rapport qualité agrégé et vue compacte déterministe

- Statut : **Proposée — M6**
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

M6-S5 doit exposer une vue qualité globale, stable et compacte sans dupliquer les règles de ces services.

Baseline :

```text
M6-S4 merge = ef6975d05d4bfcd994669d27e3a6600bc4ecdc1a
M6-S4 gate  = 254/254 PASS
```

## Décision candidate

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

Les résultats spécialisés restent la source de vérité.

S5 ne recalcule pas :

```text
orphan requirement
implementation-task coverage
acceptance availability
change completeness
external availability
decision trace
```

## Snapshot coherence

`assessActive(projectId)` résout l'ACTIVE une seule fois, puis agrège explicitement cet ID.

Chaque composant doit retourner exactement le même `KnowledgeSnapshotMetadata`. Une divergence est une erreur de cohérence et n'est jamais transformée en warning.

`assessSnapshot(snapshotId)` reste limité aux snapshots publiés selon les contrats S1-S4 :

```text
ACTIVE / RETIRED seulement
```

## Lifecycle

`ChangeLifecycleQualityService` nécessite un `ChangeLifecycle` explicitement fourni par l'appelant.

Un rapport snapshot global ne possède pas cette donnée et ne doit donc jamais inventer un lifecycle.

S5 expose :

```text
LifecycleQualityAggregationStatus.REQUIRES_EXPLICIT_LIFECYCLE_INPUT
```

Les analyses de transition lifecycle restent disponibles via le service S3 spécialisé et seront couvertes séparément par la validation finale M6.

## Findings agrégés

Les findings issus des composants snapshot-scoped sont :

```text
concaténés
puis distinct()
puis triés par l'ordre canonique QualityFinding
```

Aucun code n'est réinterprété.

## Métriques

`QualityReportMetrics` expose au minimum :

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

`CompactQualityReportService` réutilise `CanonicalJsonSerializer` M5.

Donc :

```text
même QualityReport -> même DTO compact
même DTO -> même String JSON
même String -> mêmes bytes UTF-8
```

Aucun pretty printing, timestamp runtime, hash runtime ou ordre de map non déterministe.

## Persistance

Le rapport et sa vue compacte sont calculés à la demande :

```text
aucune nouvelle table
aucune migration
aucun payload JSON métier persisté
```

## Frontières

M6-S5 ne fait pas :

```text
nouvelle règle qualité
inférence lifecycle
nouvelle résolution externe
persistance de QualityReport
ranking / score global arbitraire
LLM
semantic search
NEXUS ranking/fusion/compression
```

## Preuves attendues

- Memory == SQLite ;
- ACTIVE par défaut et RETIRED explicite ;
- cohérence stricte de snapshot ;
- métriques exactes ;
- findings distincts et triés ;
- lifecycle global explicitement non évalué sans input ;
- compact view complète ;
- JSON/UTF-8 répétés identiques ;
- SQLite reopen identique ;
- gate complet vert.

## Acceptation

À compléter après le gate local M6-S5.
