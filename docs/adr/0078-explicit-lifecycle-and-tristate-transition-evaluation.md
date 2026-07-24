# ADR-0078 — Lifecycle explicite et évaluation de transition tri-state

- Statut : **Acceptée — M14**
- Date : 24 juillet 2026
- Dépend de : ADR-0013, ADR-0032, ADR-0050
- Portée : M14 — état lifecycle et décision de transition

## Contexte

Le snapshot publié expose aujourd'hui des faits de complétude partiellement observables mais ne persiste pas un `ChangeLifecycleState` fiable. Plusieurs faits nécessaires à la machine d'état sont tri-state : `TRUE`, `FALSE`, `UNAVAILABLE`.

Convertir `UNAVAILABLE` en `false` produirait un faux blocage. Déduire un lifecycle depuis les tâches, chemins ou archives violerait ADR-0032.

## Décision

Le lifecycle d'une requête M14 est :

```text
state absent  -> UNAVAILABLE / source=UNAVAILABLE
state fourni  -> canonical state / source=CALLER_SUPPLIED
```

Aucune inférence implicite.

L'évaluation M14 retourne :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

Sémantique :

```text
ALLOWED        tous les faits requis sont observables et la machine autorise
BLOCKED        tous les faits requis sont observables et la machine bloque
UNKNOWN        au moins un fait requis est UNAVAILABLE
REQUIRES_INPUT information volontaire absente, ex. abandonment reason
```

Lorsque tous les faits requis sont connus, la décision délègue à `ChangeLifecycleStateMachine` plutôt que de dupliquer ses règles.

## Politique

Les retours arrière sont désactivés par défaut. `allowBackwardTransitions` et `allowCompletedReopen` doivent être explicitement fournis pour une évaluation qui les autorise.

Les raisons d'abandon source et cible sont transportées séparément par le client JARVIS et ne sont jamais inventées.

## Preuve d'acceptation

```text
JarvisOrchestrationContractTest 5/5 PASS
MORPHEUS 357/357 PASS
Architecture 160/160 PASS
JARVIS MorpheusOrchestrationClientTest 6/6 PASS
JARVIS jarvis-core BUILD SUCCESS
```

Validation : `docs/VALIDATION_M14.md`.

## Critères d'acceptation

1. lifecycle jamais inféré ;
2. `UNAVAILABLE` n'est jamais converti silencieusement en `false` ;
3. les préconditions inconnues produisent `UNKNOWN` ;
4. les décisions connues passent par `ChangeLifecycleStateMachine` ;
5. abandon sans raison produit `REQUIRES_INPUT` ;
6. transition-check ne persiste rien ;
7. gate M14 vert.
