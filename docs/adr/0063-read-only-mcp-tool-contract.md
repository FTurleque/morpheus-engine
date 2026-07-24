# ADR-0063 — Catalogue MCP read-only et sémantique explicite

- Statut : **Proposée — M10 gate pending**
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
get_change_status       -> UNAVAILABLE si lifecycle non persisté
```

L'absence est une information explicite ; elle ne doit pas être masquée par une valeur inventée.

## Résultats

Les réponses complexes réutilisent les vues compactes et le JSON canonique déjà validés en M5/M6/M8 lorsque possible. Les nouvelles vues M10 doivent rester déterministes, ordonnées et snapshot-scoped.

## Critères d'acceptation

1. catalogue exact ;
2. tools tous read-only ;
3. ACTIVE/CURRENT respectés ;
4. pagination/profondeur bornées ;
5. aucune acceptance criterion synthétique ;
6. aucun lifecycle inféré ;
7. résultats stables Memory/SQLite lorsqu'un service existant le garantit ;
8. erreurs tool-level explicites pour not-found / arguments métier invalides.
