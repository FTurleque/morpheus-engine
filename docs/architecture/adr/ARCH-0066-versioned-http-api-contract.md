---
id: ARCH-0066
title: "Contrat HTTP `/api/v1` et erreurs JSON stables"
domain: architecture
rules: false
---

# Contrat HTTP `/api/v1` et erreurs JSON stables

- Statut : **Acceptée**
- Date : 2026-07-24

## Context

MORPHEUS expose une surface HTTP headless à côté de la CLI et de MCP. Cette surface réutilise les contrats de
l'application (requêtes, historique publié, qualité, synchronisation) et doit offrir à ses consommateurs une forme de
réponse, une grammaire d'erreur et une règle d'évolution stables. Elle ne doit pas redéfinir les concepts MORPHEUS ni
inventer des faits métier dans le mapping HTTP.

Formes des deux enveloppes :

```json
{"apiVersion":"v1","data":{}}
```

```json
{"apiVersion":"v1","error":{"code":"NOT_FOUND","message":"...","details":{}}}
```

La liste des statuts et des codes d'erreur n'est pas fermée, parce que chaque opération a les siens. Le résolveur de
cible du proxy distant garde son propre parseur de query string parce qu'il lève une erreur d'enveloppe distante.
`HttpQueryBudget` borne le nombre de paramètres et la longueur des noms, des valeurs et de la query. Le serveur local,
les routeurs qui enregistrent leur propre contexte et la politique de rôles distante tirent tous leurs segments de
`MorpheusHttpPathParser`.

## Decision

1. Surface stable sous `/api/v1` (`MorpheusHttpRouteTable.API_PREFIX`) ; réponses JSON UTF-8 en enveloppe
   `apiVersion` (`ProductMetadata.API_VERSION`) plus `data` ou `error` (`code`, `message`, `details`).
2. Une erreur porte statut HTTP et code stable, déclarés par opération dans l'OpenAPI
   (`docs/developer/reference/openapi/`), qui fait foi ; pas de stacktrace.
3. Aucun concept n'est redéfini (`ACTIVE` / `CURRENT`, `Scenario` ≠ `AcceptanceCriterion`, traçabilité, historique
   et qualité de l'application). Un fait absent du modèle publié a un statut explicite, jamais inféré (cycle de vie
   hors snapshot : `UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT`).
4. Bornes : `offset >= 0`, `limit` de 1 au maximum de la route (`MorpheusApiService.MAX_LIMIT` en pagination
   commune), `depth` ≤ `MorpheusApiService.MAX_DEPTH`, `maxAgeMinutes` ≤ `MorpheusApiService.MAX_MAX_AGE_MINUTES`,
   tous deux ≥ 1 ; paramètre inconnu rejeté si la route a un contrat fermé.
5. Un `POST` JSON rejette champ inconnu ou requis absent, JSON malformé, autre type que `application/json`, corps
   au-delà de `MorpheusHttpServer.MAX_REQUEST_BODY_BYTES`.
6. `MorpheusHttpPathParser` est le seul lecteur du chemin (brut, segments décodés une fois, `+` intact) ; jamais
   `getRequestURI().getPath()` ni comparaison textuelle. Seuls les routeurs à contexte propre demandent leurs
   segments à `MorpheusHttpRequestDecoder.pathSegments*` ; les autres les reçoivent du serveur.
7. Query string lue par `MorpheusHttpQuery` sous `HttpQueryBudget` (le proxy distant : son parseur, même budget).
   Segment vide sans clé ; nom vide, doublon refusés.
8. Un `enum:` d'OpenAPI égale dans les deux sens l'énuméré Java publié, sauf exemption motivée.
9. Un changement incompatible (route, schéma, sémantique) exige une nouvelle version de surface ou une décision
   explicite compatible, jamais un remplacement silencieux.

## Do's and Don'ts

### Do

- Préfixer toute route stable par `/api/v1` et encapsuler chaque réponse dans l'enveloppe `apiVersion` / `data` ou
  `apiVersion` / `error`.
- Déclarer dans l'OpenAPI les statuts et codes d'erreur de chaque opération.
- Borner `offset`, `limit`, `depth` et `maxAgeMinutes`, et rejeter un paramètre de requête inconnu sur une route à
  contrat fermé.
- Rejeter un corps JSON qui porte un champ inconnu, manque un champ requis, est malformé, a un autre type de contenu
  ou dépasse la limite.
- Obtenir les segments d'un chemin de requête de `MorpheusHttpPathParser`.
- Parser une query string par `MorpheusHttpQuery`, sous `HttpQueryBudget`.
- Ajouter à `PublishedEnumsMatchJavaEnumsTest` tout `enum:` nouveau d'un contrat OpenAPI, ou l'exempter avec sa raison.

### Don't

- Ne pas renvoyer de stacktrace dans une réponse d'erreur.
- Ne pas inventer dans le mapping HTTP un fait métier que le modèle publié ne porte pas.
- Ne pas lire un chemin par `getRequestURI().getPath()`, ni le décoder par `URLDecoder`, ni le comparer par `equals`,
  `startsWith` ou `endsWith`.
- Ne pas découper une query string par `split("&")` ni la décoder hors de `MorpheusHttpQuery`.
- Ne pas recopier dans un contrat un énuméré qui diffère de l'énuméré Java qu'il publie.
- Ne pas modifier de façon incompatible une route, un schéma ou une sémantique sous `/api/v1`.

## Consequences

### Positives

- Les consommateurs disposent d'une enveloppe et d'une grammaire d'erreur uniformes sur toutes les routes.
- L'autorisation distante et le routage local lisent un même chemin de la même façon.

### Négatives

- Toute évolution incompatible exige une nouvelle version de surface ou une décision explicite.

### Risques

- L'OpenAPI et le code peuvent diverger sur les codes d'erreur d'une opération ; seule la convergence route par route
  est contrôlée automatiquement.

## Compliance and Enforcement

- `morpheus-api/src/test/java/com/morpheus/api/MorpheusApiContractTest.java` exerce les enveloppes, le type de
  contenu, les méthodes, le JSON strict, les erreurs et la pagination par un vrai serveur loopback et un
  `java.net.http.HttpClient`.
- `morpheus-api/src/test/java/com/morpheus/api/JacksonSecurityRegressionTest.java` et
  `HttpRoutesTransportBoundaryArchitectureTest#everyApiJsonMapperIsStrictAboutUnknownPropertiesAndTrailingTokens`
  (`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/`) tiennent la désérialisation stricte.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/HttpPathReadingArchitectureTest.java` tient
  le lecteur unique du chemin ; `HttpRoutesTransportBoundaryArchitectureTest#onlyARouterThatRegistersItsOwnContextAsksTheDecoderForThePath`
  tient la lecture des segments par le décodeur.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/BoundaryResilienceContractTest.java` découvre
  les lecteurs de query string de `morpheus-api` et exige `MorpheusHttpQuery` ou `HttpQueryBudget` ;
  `morpheus-api/src/test/java/com/morpheus/api/routes/owncontext/ExtensionRoutesRequestBoundaryParityTest.java` refuse une query hors
  budget sur les routes à contexte propre.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/PublishedEnumsMatchJavaEnumsTest.java` compare
  les énumérés des contrats OpenAPI aux énumérés Java.
- `morpheus-architecture-tests/src/test/java/com/morpheus/api/PublicHttpRouteConvergenceTest.java` compare les routes
  servies, `contracts/public-surfaces.tsv` et l'OpenAPI.
- `morpheus-api/src/test/java/com/morpheus/api/transport/BoundaryFailureMessageTest.java` tient le message d'une erreur relayée.
- Revue : la règle de versionnement d'une évolution incompatible.

## References

- [ADR-0059](GEN-0059-stable-local-cli-contract.md) — contrat CLI local stable.
- [ADR-0063](ARCH-0063-read-only-mcp-tool-contract.md) — contrat des outils MCP.
- [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) — API locale sur `jdk.httpserver`.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation de première classe.
- [ADR-0151](BE-0151-pagination-des-collections-et-journaux.md) — pagination des collections et des journaux.
- [API HTTP](../../developer/API.md)
- [Validation M11](../../project/validation/VALIDATION_M11.md)
