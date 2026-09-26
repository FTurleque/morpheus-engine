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

M22 ajoute deux capabilities explicites :

```text
provider.plugins.discover
  CLI   provider-plugins discover
  MCP   EXPLICITLY_NOT_EXPOSED
  HTTP local  GET /api/v1/provider-plugins/discover

provider.plugins.probe
  CLI   provider-plugins probe
  MCP   EXPLICITLY_NOT_EXPOSED
  HTTP local  POST /api/v1/provider-plugins/probe
```

La découverte MCP est volontairement absente : un client agentique ne doit pas pouvoir fournir un chemin arbitraire du filesystem local. Le code MCP conserve uniquement une implémentation désactivée par défaut, utilisable si une future configuration serveur fournit explicitement une racine de plugins ; cette forme utilise obligatoirement la projection `remoteDiscovery` sans chemin absolu.

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
agent input != local filesystem authority
```

Aucun répertoire de plugins n’est inspecté au démarrage CLI/MCP/HTTP.

### Overlay remote M26

La forme réseau durcit volontairement le probe exécutable sans changer sa sémantique :

```text
provider.plugins.discover  GET  -> READ
provider.plugins.probe     POST -> ADMIN + sha256 obligatoire
```

Le client remote ne peut pas choisir le répertoire de plugins ; celui-ci vient de la configuration serveur. Le workspace doit appartenir aux racines autorisées et le JAR est chargé depuis une copie privée dont le SHA-256 a été vérifié. Le manifeste machine-readable porte cette asymétrie dans la colonne `notes` : c’est un exemple volontaire de `surface parity != same transport shape`.

## M25 — mutations de policy

Les mutations de Policy Packs restent disponibles sur les surfaces qui disposent d’une autorisation adaptée à leur scope. `policy.override.remove` est volontairement `EXPLICITLY_NOT_EXPOSED` en MCP tant que le resolver MCP actuel, centré sur `ProjectSpecificationId`, ne peut pas autoriser de façon cohérente une mutation dont le scope peut être `PROJECT` ou `PORTFOLIO`.

```text
policy evaluation != policy mutation
policy read != governance write
```

La suppression auditée d’un override reste disponible en CLI et HTTP ; elle ne doit pas réapparaître dans le catalogue MCP sans capability resolver couvrant explicitement les deux scopes.

## M26 — Control plane local/remote

Les identités remote restent administrées localement :

```text
server.identity.create
server.identity.list
server.identity.rotate
server.identity.role
server.identity.revoke
```

Aucune de ces mutations n’est exposée par MCP ou HTTP. Le serveur recharge le fichier d’identités à chaque authentification : rotation, changement de rôle et révocation sont effectifs dès la requête suivante.

Les opérations serveur suivantes gardent leurs asymétries explicites :

```text
server.status         remote HTTP READ
server.backup.create  CLI local + remote HTTP ADMIN
server.backup.verify  CLI local uniquement
server.restore        CLI offline uniquement + confirmation
```

## Asymétrie déclarée : update discovery

`product.update-discovery` est disponible en CLI par invocation explicite :

```text
CLI  morpheus update-check --manifest URI_OR_PATH
MCP  check_product_update (stub historique sans I/O, retourne une erreur)
HTTP EXPLICITLY_NOT_EXPOSED
```

L’absence HTTP est volontaire. Une route HTTP acceptant une URI distante fournie par le client élargirait inutilement la surface SSRF de l’API locale. Le nom MCP historique est conservé comme stub sans lecture de fichier ni accès réseau. Ces différences sont donc des **choix contractuels explicites**, et non des divergences silencieuses.

L’update discovery CLI :

- n’est jamais lancée au démarrage ;
- ne télécharge pas l’artefact annoncé ;
- n’installe ni ne remplace MORPHEUS ;
- ne modifie aucun état métier ;
- valide la forme du SHA-256 annoncé ;
- exige pour un manifeste distant une référence d’attestation HTTPS ;
- **ne vérifie pas cryptographiquement** cette attestation et ne constitue donc pas une décision de confiance éditeur.

Toute future installation devra introduire une vérification cryptographique de provenance séparée avant utilisation de l’artefact.

## Version produit

`product.version` est dérivée de `ProductMetadata` et des métadonnées du build. Le serveur MCP utilise la même version. Les JAR distribués portent `Implementation-Version` et l’HTTP `/api/v1/version` lit les métadonnées d’implémentation du package.

Le fallback de développement n’est pas une version de release et ne doit jamais être utilisé comme preuve de publication.

## Évolution

Toute nouvelle capability soumise à convergence doit modifier le manifeste et les tests de cohérence dans le même changement. Une absence de transport doit être écrite comme `EXPLICITLY_NOT_EXPOSED` avec justification ; elle ne doit pas être laissée implicite.
