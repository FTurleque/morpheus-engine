# ADR-0048 — Findings de qualité explicables et couverture de traçabilité des requirements

- Statut : **Proposée — M6**
- Date : 23 juillet 2026
- Dépend de : ADR-0033, ADR-0034, ADR-0037, ADR-0038, ADR-0042, ADR-0043
- Portée : M6-S1, modèle de findings et couverture de traçabilité des `Requirement CURRENT`

## Contexte

M5 est validé et intégré :

```text
merge final = 6bbaf086cf1fed81e3517bb1cef5b643264fb836
gate final  = 227/227 PASS
```

M6 doit détecter les lacunes de qualité sans confondre preuve déterministe et heuristique.

Les `Diagnostic` existants décrivent principalement discovery/ingestion. Les findings de qualité sont des résultats dérivés d'un snapshot publié ; ils ne doivent ni étendre artificiellement le catalogue d'ingestion ni devenir des entités persistées.

## Décision candidate

Introduire dans la couche applicative qualité :

```text
QualityFinding
QualityFindingCode
QualityEvidenceKind
RequirementTraceabilityCoverage
RequirementQualityService
```

`QualityFinding` réutilise `DiagnosticSeverity` pour la gravité, mais possède son propre code métier de qualité.

`QualityEvidenceKind` distingue explicitement :

```text
DETERMINISTIC
HEURISTIC
```

Une finding `HEURISTIC` exige une confidence explicite. Une finding `DETERMINISTIC` n'en exige pas.

## Sémantique S1

Population analysée :

```text
RequirementVersionRecord
snapshot publié ACTIVE ou RETIRED
TemporalState = CURRENT uniquement
```

Un requirement est **lié** s'il possède au moins un `TraceabilityLink` direct entrant ou sortant dans le même snapshot.

Un requirement est **orphelin** si :

```text
incoming(requirement) = empty
AND
outgoing(requirement) = empty
```

Aucune restriction de relation n'est appliquée en S1 : tout lien de traçabilité persisté et direct constitue une couverture structurelle.

Le calcul est déterministe :

```text
totalRequirements
linkedRequirements
orphanRequirements
coverageRatio = linked / total
```

Pour `total = 0`, `coverageRatio = 1.0` : un ensemble vide ne contient aucun requirement non couvert.

## Finding `ORPHAN_REQUIREMENT`

Chaque requirement orphelin produit une finding :

```text
code = ORPHAN_REQUIREMENT
severity = WARNING
evidenceKind = DETERMINISTIC
subject = TraceabilityEntityRef(REQUIREMENT, requirementId)
confidence = empty
evidenceIds = requirement.provenance.evidenceId
```

Les findings sont triées par sujet puis code et sont stables entre Memory et SQLite.

## Snapshot policy

```text
active(projectId) -> snapshot ACTIVE uniquement
snapshot(snapshotId) -> ACTIVE ou RETIRED uniquement
BUILDING / VALIDATING / READY / FAILED rejetés
```

L'absence de snapshot ACTIVE est distincte d'un rapport vide.

## Frontières

M6-S1 ne fait pas :

```text
fuzzy matching
semantic search
LLM
invention de lien
mutation de TraceabilityLink
persistance de finding
nouvelle migration
nouveau backend
acceptance coverage
implementation-task coverage
lifecycle blockers
aggregate M6 report
```

## Preuves attendues

- ACTIVE par défaut ;
- ACTIVE/RETIRED explicite uniquement ;
- CURRENT only ;
- requirement lié par incoming ou outgoing ;
- requirement sans lien -> `ORPHAN_REQUIREMENT` ;
- PROPOSED ignoré ;
- coverage déterministe et stable ;
- zero requirements -> 100 % ;
- evidence du requirement conservée ;
- Memory == SQLite ;
- SQLite reopen ;
- aucune migration/persistance de finding ;
- aucune finding heuristique sans confidence ;
- gate local Windows complet vert.

## Acceptation

À compléter uniquement après le gate local complet M6-S1.