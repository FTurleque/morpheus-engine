# Proposition : borner les appels simultanés à chaque pair MCP (AUD-SEC-24)

## Why

Un client remote de rôle READ fait lancer un processus Java pair (MINOS ou NEXUS) par requête, et huit requêtes en
boucle tiennent les huit workers de base pendant l'échéance d'un pair. Constat `AUD-SEC-24` (Moyenne) de l'audit du
17/11/2026 (`docs/project/audits/2026-11-17/findings.md`).

Revérifié sur `1a539c22` :

- `MorpheusRemoteRoutePolicy` ouvre au rôle READ `POST …/requirements/{requirementId}/augmented-context`,
  `POST …/changes/{changeId}/augmented-context` et `GET …/external-references/{referenceId}/resolution` ;
- chaque appel ouvre une passerelle neuve : `MinosMcpExternalReferenceResolver` (`gatewayFactory.open()`),
  `NexusMcpTechnicalContextProvider.status()` et `build(...)` (`gateways.open()`), donc une copie vérifiée du JAR épinglé
  et un processus par `BoundedStdioClientTransport` ;
- l'échéance par défaut d'une opération de pair est `PeerLaunchSettings.DEFAULT_TIMEOUT_SECONDS` ;
- le gestionnaire tourne entier sur un worker de `DatabaseCallWorkers` (`DATABASE_WORKERS`) ;
- aucun plafond de concurrence propre aux pairs : le seul `Semaphore` de `morpheus-mcp-transport`
  (`BoundedStdioClientTransport.inboundCapacity`) borne les messages entrants d'une session, pas le nombre de sessions.

Le rôle le plus faible obtient ainsi un déni de service de toute l'API : les autres requêtes attendent l'admission puis
sont refusées.

## What Changes

- Le nombre de sessions simultanées ouvertes vers un même pair, dans un processus MORPHEUS, est borné ; la borne est
  appliquée une fois, par le socle du cycle de vie `McpPeerSession`.
- Une ouverture au-delà de la borne n'attend pas : elle échoue immédiatement par une raison nommée, que la passerelle
  rend comme une observation `UNAVAILABLE` (contexte technique, résolution de référence, statut d'intégration), sans
  lancer de processus ni copier le JAR.
- Aucune route, aucun rôle, aucun outil MCP ne change ; le rôle WRITE n'est pas exigé de ces routes (design.md, D2).

## Capabilities

### New Capabilities

### Modified Capabilities
- `mcp-peer-gateway-lifecycle` : le socle borne les sessions simultanées par pair et refuse par nom au-delà.

## Périmètre et exclusions

**Inclus :** `McpPeerSession` (`morpheus-mcp-transport`), les passerelles et runtimes MINOS et NEXUS
(`morpheus-integration-minos`, `morpheus-integration-nexus`) qui fournissent la borne, leurs tests, la page durable
`mcp-peer-gateway-lifecycle`, `docs/user/INTEGRATIONS.md`.

**Exclus :** la réutilisation d'une session de pair par processus (design.md, D3) ; l'admission des workers de base
(`database-worker-admission`), inchangée ; la terminaison des descendants d'un pair (ADR-0106), inchangée.

## Acteurs et surfaces concernés

Client remote de rôle READ, client HTTP local, client MCP des outils de contexte augmenté et de résolution de
référence ; les pairs MINOS et NEXUS configurés.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Un processus pair par requête | existant vérifié | `MinosMcpExternalReferenceResolver`, `NexusMcpTechnicalContextProvider` |
| Routes ouvertes au rôle READ | existant vérifié | `MorpheusRemoteRoutePolicy` |
| Aucun plafond de sessions par pair | existant vérifié | grep `Semaphore` dans `morpheus-integration-*`, `morpheus-mcp-transport` |
| Une ouverture refusée devient une observation `UNAVAILABLE` | décision | sémantique tri-state existante des observations de pair |
| Valeur de la borne | proposition à valider | design.md, D1 |

## Impact

`morpheus-mcp-transport`, `morpheus-integration-minos`, `morpheus-integration-nexus`. Le socle ne dépend toujours
d'aucun autre paquet MORPHEUS (`theMcpTransportDependsOnNoOtherMorpheusPackage`). Aucune ligne de
`contracts/public-surfaces.tsv`, aucune OpenAPI : la forme d'une observation `UNAVAILABLE` existe déjà.

## Questions ouvertes

- Valeur de la borne par pair (design.md, D1), à confirmer à la revue ; elle ne change ni l'approche ni les tâches.
