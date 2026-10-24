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

## Decision

**Préfixe versionné.** La surface HTTP stable est préfixée par `/api/v1` (`MorpheusHttpServer.API_PREFIX`).

**Enveloppe.** Les réponses sont en JSON UTF-8 et encapsulées explicitement, avec la version d'API
(`ProductMetadata.API_VERSION`) :

```json
{"apiVersion":"v1","data":{}}
```

```json
{"apiVersion":"v1","error":{"code":"NOT_FOUND","message":"...","details":{}}}
```

**Codes et erreurs.** Une erreur porte un statut HTTP et un code stable dans l'enveloppe d'erreur. La liste des
statuts et des codes n'est pas fermée : chaque opération déclare les siens dans l'OpenAPI
(`docs/developer/reference/openapi/`), qui fait foi. Une erreur ne renvoie jamais de stacktrace.

**Sémantique.** L'API ne redéfinit pas les concepts MORPHEUS : les états `ACTIVE` / `CURRENT` sont conservés, un
`Scenario` n'est pas un `AcceptanceCriterion`, la traçabilité est celle des liens persistés et des services existants,
l'historique publié et la qualité sont ceux des services de l'application. Un fait que le modèle publié ne porte pas
est rendu par un statut explicite au lieu d'être inféré : l'état de cycle de vie d'un changement absent du snapshot
publié est rendu `UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT`.

**Bornes.** Les paramètres de pagination et de parcours sont bornés : `offset >= 0`, `limit` entre 1 et le maximum de
la route (`MorpheusApiService.MAX_LIMIT` pour les routes qui partagent la pagination commune), `depth` entre 1 et
`MorpheusApiService.MAX_DEPTH`, `maxAgeMinutes` entre 1 et `MorpheusApiService.MAX_MAX_AGE_MINUTES`. Les paramètres de
requête inconnus sont rejetés sur les routes qui déclarent un contrat fermé.

**Requête JSON.** Les `POST` utilisent `application/json` et rejettent les champs inconnus, les champs requis
manquants, le JSON malformé, un type de contenu non supporté et un corps au-delà de la limite configurée
(`MorpheusHttpServer.MAX_REQUEST_BODY_BYTES`).

**Lecture du chemin.** Le chemin d'une requête a un seul lecteur, `MorpheusHttpPathParser` : il découpe le chemin
brut, tel qu'envoyé, puis décode chaque segment une fois, sans transformer `+`. Le serveur local, les routeurs qui
enregistrent leur propre contexte et la politique de rôles distante obtiennent leurs segments de lui. Aucune classe de
l'adaptateur ne lit le chemin décodé par `getRequestURI().getPath()` ni ne compare le chemin comme du texte. Un routeur
que le serveur porte reçoit ses segments du serveur ; seuls les routeurs qui enregistrent leur propre contexte les
demandent à `MorpheusHttpRequestDecoder.pathSegments*`.

**Lecture de la query string.** Toute route lit sa query string par `MorpheusHttpQuery`, sous le budget partagé
`HttpQueryBudget` (nombre de paramètres, longueur des noms, des valeurs et de la query). Le résolveur de cible du proxy
distant garde son propre parseur, parce qu'il lève une erreur d'enveloppe distante, et applique le même budget. Un
segment vide ne porte aucune clé ; un nom vide et un paramètre en double sont refusés.

**Énumérés publiés.** Un énuméré écrit dans un contrat OpenAPI (`enum:`) est égal, dans les deux sens, à l'énuméré
Java qu'il publie ; un énuméré qui n'a pas d'équivalent Java est une exemption déclarée avec sa raison.

**Stabilité.** Un changement incompatible de route, de schéma ou de sémantique ne remplace pas silencieusement
`/api/v1` : il requiert une nouvelle version de surface ou une décision explicite compatible.

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
  `morpheus-api/src/test/java/com/morpheus/api/ExtensionRoutesRequestBoundaryParityTest.java` refuse une query hors
  budget sur les routes à contexte propre.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/PublishedEnumsMatchJavaEnumsTest.java` compare
  les énumérés des contrats OpenAPI aux énumérés Java.
- `morpheus-architecture-tests/src/test/java/com/morpheus/api/PublicHttpRouteConvergenceTest.java` compare les routes
  servies, `contracts/public-surfaces.tsv` et l'OpenAPI.
- `morpheus-api/src/test/java/com/morpheus/api/BoundaryFailureMessageTest.java` tient le message d'une erreur relayée.
- Revue : la règle de versionnement d'une évolution incompatible.

## References

- [ADR-0059](GEN-0059-stable-local-cli-contract.md) — contrat CLI local stable.
- [ADR-0063](ARCH-0063-read-only-mcp-tool-contract.md) — contrat des outils MCP.
- [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) — API locale sur `jdk.httpserver`.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation de première classe.
- [ADR-0131](BE-0131-pagination-des-collections-et-journaux.md) — pagination des collections et des journaux.
- [API HTTP](../../developer/API.md)
- [Validation M11](../../project/validation/VALIDATION_M11.md)
