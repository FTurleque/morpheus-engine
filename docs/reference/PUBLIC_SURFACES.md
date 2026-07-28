# Public surfaces — contrat de convergence MORPHEUS 1.x

La source machine lisible de la convergence des surfaces publiques est :

[`../../contracts/public-surfaces.tsv`](../../contracts/public-surfaces.tsv)

Ce document explique ce contrat ; il ne le duplique pas comme deuxième source normative.

## Principe

Une capability MORPHEUS doit conserver la même intention métier et les mêmes invariants à travers les transports qui l’exposent. La forme n’a pas à être identique :

```text
surface parity != same transport shape
READ != WRITE
ALLOWED != applied
```

Le manifeste indique pour chaque capability critique :

- son intention `READ` ou `WRITE` ;
- sa forme CLI ;
- son outil MCP ;
- sa route HTTP ou une omission explicitement justifiée.

## M22 — Provider plugins

M22 ajoute deux capabilities READ explicites sur les trois transports :

```text
provider.plugins.discover
  CLI   provider-plugins discover
  MCP   discover_provider_plugins
  HTTP  GET /api/v1/provider-plugins/discover

provider.plugins.probe
  CLI   provider-plugins probe
  MCP   probe_provider_plugin
  HTTP  GET /api/v1/provider-plugins/probe
```

Elles n’ont pas la même portée d’exécution :

```text
discovery = lecture bornée de META-INF/morpheus-provider.properties
probe     = activation explicite d’un plugin compatible + provider.probe(workspace)
```

Invariants :

```text
plugin discovery != plugin activation
provider metadata != executable trust
capability declaration != capability implementation proof
plugin failure != core crash
```

Aucun répertoire de plugins n’est inspecté au démarrage CLI/MCP/HTTP.

## Asymétrie déclarée : update discovery

`product.update-discovery` est disponible par invocation explicite :

```text
CLI  morpheus update-check --manifest URI_OR_PATH
MCP  check_product_update(manifestUri)
HTTP EXPLICITLY_NOT_EXPOSED
```

L’absence HTTP est volontaire. Une route HTTP acceptant une URI distante fournie par le client élargirait inutilement la surface SSRF de l’API locale. Cette différence est donc un **choix contractuel explicite**, et non une divergence silencieuse.

L’update discovery :

- n’est jamais lancée au démarrage ;
- ne télécharge pas l’artefact annoncé ;
- n’installe ni ne remplace MORPHEUS ;
- ne modifie aucun état métier ;
- valide le SHA-256 annoncé dans le manifeste.

## Version produit

`product.version` est dérivée de `ProductMetadata` et des métadonnées du build. Le serveur MCP utilise la même version. Les JAR distribués portent `Implementation-Version` et l’HTTP `/api/v1/version` lit les métadonnées d’implémentation du package.

Le fallback de développement n’est pas une version de release et ne doit jamais être utilisé comme preuve de publication.

## Évolution

Toute nouvelle capability soumise à convergence doit modifier le manifeste et les tests de cohérence dans le même changement. Une absence de transport doit être écrite comme `EXPLICITLY_NOT_EXPOSED` avec justification ; elle ne doit pas être laissée implicite.
