# Proposition : typer les réponses des services de l'adaptateur HTTP (AUD-QUA-36)

## Why

Les services de `com.morpheus.api` (`*ApiService`) rendent leurs réponses sous le type `Object`. Revérifié le
17/11/2026 sur `1a539c22` : `grep -n "public Object " morpheus-api/src/main/java/com/morpheus/api/*ApiService.java`
compte 91 méthodes, réparties dans 15 des 19 services (26 dans `MorpheusApiService`, 12 dans
`MorpheusPolicyApiService`, 11 dans `MorpheusPortfolioApiService`, 9 dans `MorpheusQueryApiService`…) ; aucune autre
classe de production de `morpheus-api`, de `morpheus-cli` ni de `morpheus-mcp` ne déclare de méthode publique rendant
`Object`.

Le type réel existe déjà à chaque site (`Map<String, Object>` de `PayloadMap` et `PublicPayloads`, vues publiques,
`PagedEnvelope`, records de résultat) ; il est effacé au retour. La forme d'une réponse n'est donc vérifiée qu'à
l'exécution, par `PublicPayloadConvergenceTest` et seulement pour les types partagés : le compilateur ne voit ni un
changement de type rendu ni une façade `MorpheusApiService` qui déléguerait au mauvais service.

Preuve : constat `AUD-QUA-36` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd` (« 19 services … 91 méthodes »), confirmé : 91 méthodes, dans 15 services sur 19.

## What Changes

- Chaque méthode publique d'un service de `com.morpheus.api` déclare le type qu'elle rend réellement, en commençant
  par la façade `MorpheusApiService` ; aucune réponse ne change à l'octet.
- Une règle du module d'architecture refuse une méthode publique d'un service de l'adaptateur HTTP qui rend `Object`
  (option A de design.md, D1) ; selon la décision du mainteneur, la règle est absolue (tout est typé dans ce
  changement) ou bornée par un plafond qui ne fait que baisser (option B).
- Aucune route, aucun outil MCP, aucune ligne de `contracts/public-surfaces.tsv` ni de l'OpenAPI ne change.

## Capabilities

### New Capabilities
- `http-service-answer-types` : le type déclaré des réponses que les services de l'adaptateur HTTP remettent aux
  routeurs, et la garantie que ce typage ne change aucune réponse.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** les méthodes publiques des classes `*ApiService` de `com.morpheus.api`, leurs appelants dans
`com.morpheus.api.local` et `com.morpheus.api.routes`, une règle d'architecture, la page durable de la capacité.

**Exclus :** l'enveloppe `ApiSuccess`, dont le champ `data` reste de type `Object` (elle porte toutes les réponses) ;
les projections des types partagés, déjà écrites une fois par `PublicPayloads` (`public-payload-convergence`) ; les
types de réponse de la CLI et du MCP.

## Acteurs et surfaces concernés

Le contributeur qui modifie un service ou un routeur HTTP. Le client HTTP n'observe aucun changement.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| 91 méthodes publiques rendent `Object`, dans 15 services | existant vérifié | `grep -n "public Object "`, `1a539c22` |
| Le type réel existe à chaque site | existant vérifié | ex. `MorpheusApiService.health` (`PayloadMap.of`), `MorpheusPolicyApiService.list` (`PolicyPublicViews.definitions`) |
| Les services ne sont pas des types d'entrée de l'adaptateur | existant vérifié | `ApiEntryTypesArchitectureTest.ENTRY_TYPES` (ARCH-0155) |
| Tout typer d'un coup, règle sans exception | proposition à valider | design.md, D1 |
| Aucun ADR n'est requis | décision | convention d'un adaptateur tenue par un test, `.claude/rules/adr.md` |

## Impact

`morpheus-api` (services, et routeurs pour les variables typées) et ses tests ; `morpheus-architecture-tests` (une
règle ajoutée ; en option B, une clé de cliquet ajoutée : fichiers de gouvernance, justification dans la description de
la PR). Aucun module voisin n'utilise un service : ils ne sont pas des types d'entrée (ARCH-0155), donc aucun
appelant hors de l'adaptateur ne change.

Aucune dépendance avec les autres constats du sprint 9 ; le changement
`map-every-http-failure-to-its-status-through-one-table` touche les mêmes routeurs `owncontext` sur une autre méthode
(`handle`) : appliquer l'un puis rebaser l'autre.

## Questions ouvertes

- Option A (tout typer dans ce changement, règle sans exception) ou option B (façade d'abord, plafond qui ne fait que
  baisser, dans quel fichier de cliquets) ? À trancher par le mainteneur avant la tâche 2.1 (design.md, D1).
