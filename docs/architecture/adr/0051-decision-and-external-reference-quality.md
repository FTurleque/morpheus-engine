# ADR-0051 — Qualité des décisions et références externes sans justification inventée

- Statut : **Acceptée — M6**
- Date : 23 juillet 2026
- Dépend de : ADR-0039, ADR-0041, ADR-0048, ADR-0050
- Portée : M6-S4

## Contexte

M6-S3 est intégrée :

```text
merge = 03fd5a86e11f2afc40e3f1ecd5b1b8a1d1d211f7
gate  = 248/248 PASS
```

`DesignDecision` normalise `id`, `changeId`, `title`, `decision`, `provenance`, mais aucun champ `rationale` ou `justification` explicite.

La traçabilité M4 dérive structurellement :

```text
Change --DECIDED_BY--> DesignDecision
```

M4 expose également les états de référence externe :

```text
REFERENCE_UNVALIDATED
REFERENCE_UNRESOLVED
REFERENCE_RESOLVED
REFERENCE_STALE
BROKEN_REFERENCE
```

## Décision

Ajouter :

```text
DecisionJustificationStatus
DesignDecisionQualityAssessment
ExternalReferenceQualityAssessment
DecisionReferenceQualityReport
DecisionReferenceQualityService
```

et les findings :

```text
DESIGN_DECISION_WITHOUT_TRACE
DECISION_JUSTIFICATION_UNAVAILABLE
EXTERNAL_REFERENCE_UNVALIDATED
EXTERNAL_REFERENCE_UNRESOLVED
EXTERNAL_REFERENCE_STALE
EXTERNAL_REFERENCE_BROKEN
```

## Design decision

Une décision est tracée si le snapshot contient un lien persistant :

```text
Change(decision.changeId) --DECIDED_BY--> DesignDecision(decision.id)
```

Sinon :

```text
DESIGN_DECISION_WITHOUT_TRACE
WARNING
DETERMINISTIC
```

La justification métier n'est pas déduite du texte `decision`, du titre ou de la provenance.

```text
DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
DECISION_JUSTIFICATION_UNAVAILABLE
INFO
DETERMINISTIC
```

Ce finding signifie uniquement que MORPHEUS ne possède pas encore un champ de justification normalisé. Il ne conclut jamais qu'une décision est mauvaise.

## Références externes

S4 réutilise `ExternalTraceabilityQueryService.inspect(...)` et ne refait aucune résolution.

Mapping :

```text
REFERENCE_RESOLVED    -> aucun finding
REFERENCE_UNVALIDATED -> EXTERNAL_REFERENCE_UNVALIDATED
REFERENCE_UNRESOLVED  -> EXTERNAL_REFERENCE_UNRESOLVED
REFERENCE_STALE       -> EXTERNAL_REFERENCE_STALE
BROKEN_REFERENCE      -> EXTERNAL_REFERENCE_BROKEN
```

Tous les findings sont `DETERMINISTIC` et conservent les evidence IDs du lien quand disponibles.

Un lien cassé reste visible et auditable même si l'`ExternalReference` ciblée n'existe pas dans le store.

## Snapshot policy

```text
ACTIVE par défaut
ACTIVE / RETIRED explicites
READY/BUILDING/VALIDATING/FAILED rejetés
```

## Déterminisme

- décisions triées par `DesignDecisionId` ;
- liens externes triés par `TraceabilityLinkId` ;
- findings triés canoniquement ;
- Memory == SQLite ;
- SQLite reopen identique.

## Frontières

M6-S4 ne fait pas :

```text
inférence de rationale
analyse sémantique du texte de décision
nouvelle résolution externe
nouvelle relation de trace
persistance de finding
migration
provider change
LLM / fuzzy / semantic search
```

## Preuves validées

`DecisionReferenceQualityContractTest` : **6/6 PASS**.

Preuves :

- décision correctement reliée -> pas de `DESIGN_DECISION_WITHOUT_TRACE` ;
- décision sans `DECIDED_BY` -> finding déterministe ;
- justification toujours explicitement indisponible ;
- chaque état externe dégradé -> code exact ;
- RESOLVED -> aucun finding ;
- lien externe dont la référence manque -> BROKEN ;
- ACTIVE / RETIRED / READY policy ;
- Memory == SQLite ;
- SQLite reopen ;
- ordre et rapport déterministes.

Gate local Windows complet :

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

Warnings connus non bloquants uniquement : Xerial SQLite/JDK restricted native access et SLF4J NOP.

Head de code effectivement testé :

```text
53356fe77df0d5a3cc474b8aca3224d970fb88d7
```

## Acceptation

**Acceptée — M6.**

La qualité des décisions et des références externes est exposée par des faits structurels et des états M4 existants, sans justification ni résolution inventée.
