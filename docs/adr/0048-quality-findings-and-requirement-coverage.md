# ADR-0048 — Findings de qualité explicables et couverture de traçabilité des requirements

- Statut : **Acceptée — M6**
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

## Décision

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

Une finding `HEURISTIC` exige une confidence explicite bornée dans `[0,1]`. Une finding `DETERMINISTIC` interdit une confidence afin de ne pas présenter un score comme une preuve.

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

## Preuve d'acceptation — 23 juillet 2026

Gate local Windows exécuté sur :

```text
branch = m6/requirement-quality-coverage
head   = 34ecc48057f27990221cbe7669b555eb73950581
.\mvnw.cmd clean test
javac release 21
```

Preuve ciblée :

```text
RequirementQualityContractTest  7/7 PASS
```

Résultat global :

```text
Domain                                  21 tests
Application                             66 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                     107 tests
-----------------------------------------------
TOTAL                                  234/234 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
Total time                             27.269 s
Finished at                 2026-07-23T20:52:28+02:00
```

Warnings connus et non bloquants uniquement : Xerial SQLite/JDK restricted native access et SLF4J NOP.

Les preuves couvrent : ACTIVE par défaut, ACTIVE/RETIRED explicite, CURRENT only, couverture par lien entrant ou sortant, exclusion PROPOSED, finding orpheline déterministe avec evidence, population vide à 100 %, séparation absence d'ACTIVE / ACTIVE vide, contrat de confidence déterministe/heuristique, parité Memory/SQLite et SQLite reopen.

Décision finale :

```text
ADR-0048 = ACCEPTÉE — M6
M6-S1    = VALIDÉ — 234/234
```
