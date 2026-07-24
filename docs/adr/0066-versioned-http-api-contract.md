# ADR-0066 — Contrat HTTP `/api/v1` et erreurs JSON stables

- Statut : **Acceptée — M11**
- Date : 24 juillet 2026
- Dépend de : ADR-0043 à ADR-0058, ADR-0059, ADR-0063
- Portée : M11 — surface API headless

## Décision

La première surface HTTP stable est préfixée :

```text
/api/v1
```

Les réponses sont JSON UTF-8 et encapsulées explicitement.

Succès :

```json
{"apiVersion":"v1","data":{}}
```

Erreur :

```json
{"apiVersion":"v1","error":{"code":"NOT_FOUND","message":"...","details":{}}}
```

## Codes HTTP

```text
200 OK
201 CREATED
400 BAD_REQUEST
404 NOT_FOUND
405 METHOD_NOT_ALLOWED
409 STATE_CONFLICT
415 UNSUPPORTED_MEDIA_TYPE
500 INTERNAL_ERROR
```

Les erreurs applicatives ne renvoient jamais de stacktrace.

## Sémantique

L'API ne redéfinit pas les concepts MORPHEUS :

```text
ACTIVE / CURRENT conservés
Scenario != AcceptanceCriterion
absence acceptance -> UNAVAILABLE_IN_NORMALIZED_MODEL
absence lifecycle -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
traceability = liens persistés / services existants
published history = service M3
quality = services M6
```

## Pagination / bornes

```text
offset >= 0
1 <= limit <= 100
1 <= depth <= 20
1 <= maxAgeMinutes <= 525600
```

Les query params inconnus sont rejetés sur les routes qui déclarent un contrat fermé.

## JSON request

Les POST utilisent `application/json` et rejettent :

```text
unknown fields
missing required fields
malformed JSON
unsupported content type
body above configured limit
```

## Stabilité

Un changement incompatible de route, schéma ou sémantique ne peut pas remplacer silencieusement `/api/v1`; il requiert une nouvelle version de surface ou une décision explicite compatible.

## Critères d'acceptation

1. enveloppe succès stable ;
2. enveloppe erreur stable ;
3. content-type cohérent ;
4. 404/405/409/415 prouvés ;
5. pagination bornée ;
6. JSON strict ;
7. aucun fait métier inventé par le mapper HTTP ;
8. tests via vrai `HttpClient`.

## Preuve d'acceptation — 24 juillet 2026

Le head `a7daa9bb7eef1799926ea20b9e96606a388a301f` a passé :

```text
MorpheusApiContractTest                1/1 PASS
MorpheusApiHistoryContractTest         1/1 PASS
MorpheusApiProjectSyncIntegrationTest  2/2 PASS
MORPHEUS API                           4/4 PASS
TOTAL                                314/314 PASS
BUILD SUCCESS
```

Les tests utilisent un vrai `HttpServer` loopback + `java.net.http.HttpClient` et couvrent les enveloppes, content type, méthodes, JSON strict, erreurs 400/404/405/409/415, pagination, sync, historique et reopen SQLite.

La spécification OpenAPI 3.1 est publiée dans `docs/openapi/morpheus-v1.yaml`.

Décision : **ADR-0066 ACCEPTÉE — M11**. Voir `docs/VALIDATION_M11.md`.