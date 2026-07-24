# ADR-0063 — Catalogue MCP read-only et sémantique explicite

- Statut : **Acceptée — M10**
- Date : 24 juillet 2026
- Dépend de : ADR-0043 à ADR-0058
- Portée : M10 — tools MCP

## Décision

M10 expose exactement quatorze tools read-only :

```text
get_current_specification
find_requirements
get_change
list_changes
get_constraints
get_acceptance_criteria
get_design_decisions
get_implementation_tasks
trace_requirement
get_change_context
get_specification_context
get_change_status
get_blocking_conditions
get_sync_status
```

Les handlers font uniquement :

```text
validate args
-> resolve ACTIVE/current state
-> call existing application service/store port
-> map deterministic response
```

Aucune règle métier essentielle n'est réimplémentée dans l'adapter MCP.

## Absence de sémantique

MORPHEUS ne transforme jamais un `Scenario` en `AcceptanceCriterion`. Le modèle normalisé courant ne persiste pas non plus un lifecycle de changement dans la projection métier publiée.

Donc :

```text
get_acceptance_criteria -> UNAVAILABLE_IN_NORMALIZED_MODEL si absent
get_change_status       -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT si lifecycle non persisté
```

L'absence est une information explicite ; elle ne doit pas être masquée par une valeur inventée.

## Résultats

Les réponses complexes réutilisent les vues compactes et le JSON canonique déjà validés en M5/M6/M8 lorsque possible. Les nouvelles vues M10 restent déterministes, ordonnées et snapshot-scoped.

`get_specification_context` agrège uniquement les relations `AFFECTS` persistées. `get_blocking_conditions` réutilise `ChangeCompletenessService` et les facts tri-state M6.

## Preuve M10

```text
MorpheusMcpToolCatalogTest      3/3 PASS
MorpheusMcpToolServiceTest      1/1 PASS
MorpheusMcpStdioIntegrationTest 1/1 PASS
TOTAL                          307/307 PASS
Architecture                   149/149 PASS
```

Le fixture SQLite de `MorpheusMcpToolServiceTest` appelle les quatorze tools et vérifie explicitement :

```text
criteria=[] quand AcceptanceCriterion est indisponible
Scenario jamais relabellé AcceptanceCriterion
lifecycleState=UNAVAILABLE quand lifecycle non persisté
ACTIVE/CURRENT conservés
```

Validation détaillée : `docs/VALIDATION_M10.md`.
