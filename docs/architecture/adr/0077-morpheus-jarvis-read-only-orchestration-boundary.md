# ADR-0077 — Frontière d'orchestration read-only MORPHEUS / JARVIS

- Statut : **Acceptée — M14**
- Date : 24 juillet 2026
- Dépend de : ADR-0007, ADR-0032, ADR-0063, ADR-0066
- Portée : M14 — responsabilité cross-engine

## Contexte

MORPHEUS connaît l'intention, les règles lifecycle, les contraintes et la qualité observable. JARVIS est l'orchestrateur de l'écosystème.

Copier la machine d'état MORPHEUS dans JARVIS créerait deux sources de vérité. Faire appeler JARVIS depuis MORPHEUS inverserait les responsabilités.

## Décision

```text
MORPHEUS = facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

La frontière cross-engine est un contrat machine read-only exposé par MORPHEUS et consommé par JARVIS.

MORPHEUS ne dépend d'aucun type, artefact Maven ou runtime `com.jarvis.*`.

JARVIS ne dépend d'aucun type `com.morpheus.*`; il consomme JSON via HTTP local.

## Non-objectifs

```text
MORPHEUS choisit la prochaine action
MORPHEUS déclenche JARVIS
MORPHEUS écrit le lifecycle provider
JARVIS recode la machine d'état MORPHEUS
```

## Preuve d'acceptation

```text
MORPHEUS 357/357 PASS
Architecture 160/160 PASS
Packaging Windows PASS
JARVIS jarvis-core 536 tests, 0 failure, 0 error
MorpheusOrchestrationClientTest 6/6 PASS
```

Validation : `docs/VALIDATION_M14.md`.

## Critères d'acceptation

1. aucune dépendance compile-time MORPHEUS -> JARVIS ;
2. aucune dépendance compile-time JARVIS -> MORPHEUS ;
3. contrat read-only documenté ;
4. transition-check n'effectue aucune mutation ;
5. client JARVIS optionnel et fail-open ;
6. architecture tests MORPHEUS interdisent `com.jarvis.*` ;
7. gate M14 vert.
