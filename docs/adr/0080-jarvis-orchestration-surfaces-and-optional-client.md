# ADR-0080 — Surfaces d'orchestration et client JARVIS optionnel

- Statut : **Acceptée — M14**
- Date : 24 juillet 2026
- Dépend de : ADR-0059, ADR-0063, ADR-0066, ADR-0077, ADR-0078, ADR-0079
- Portée : M14 — CLI/MCP/HTTP et preuve cross-repo

## Décision

MORPHEUS expose le même use case d'orchestration via trois adapters.

CLI :

```text
change-orchestration state
change-orchestration transition-check
```

MCP read-only :

```text
get_change_orchestration_state
evaluate_change_transition
```

HTTP `/api/v1` :

```text
GET  /projects/{projectId}/changes/{changeId}/orchestration
POST /projects/{projectId}/changes/{changeId}/transition-check
```

Le POST est une requête de calcul, sans écriture.

## JARVIS

Le dépôt `FTurleque/jarvis` reçoit un client HTTP optionnel qui :

```text
- connaît seulement l'URL MORPHEUS et un projectId configurable ;
- lit l'état d'orchestration ;
- demande une évaluation de transition ;
- ne recode aucune règle lifecycle ;
- échoue ouvert lorsque MORPHEUS est désactivé ou indisponible.
```

Les raisons d'abandon observation/source/cible sont transmises explicitement et séparément.

Aucune dépendance Maven entre les deux dépôts.

## Sécurité de la frontière

```text
MORPHEUS API = source de règles/faits
JARVIS client = transport + DTO local
JARVIS orchestration = choix de la prochaine action
```

## Packaging

Aucun runtime JARVIS n'est embarqué dans MORPHEUS. Aucun runtime MORPHEUS n'est embarqué dans JARVIS.

## Preuve d'acceptation

```text
MORPHEUS 357/357 PASS
Architecture 160/160 PASS
Packaging Windows PASS — 33,702,405 bytes
MCP catalogue 20 tools read-only
JARVIS jarvis-core: 536 tests, 0 failure, 0 error, 16 skipped
MorpheusOrchestrationClientTest 6/6 PASS
```

Validation : `docs/VALIDATION_M14.md`.

## Critères d'acceptation

1. CLI/MCP/HTTP cohérents ;
2. MCP reste read-only ;
3. serveur MCP porte le catalogue à 20 tools ;
4. HTTP transition-check ne mute rien ;
5. client JARVIS optionnel/fail-open ;
6. aucune dépendance cross-repo binaire ;
7. packaging MORPHEUS reste autonome ;
8. gates locaux MORPHEUS et JARVIS verts.
