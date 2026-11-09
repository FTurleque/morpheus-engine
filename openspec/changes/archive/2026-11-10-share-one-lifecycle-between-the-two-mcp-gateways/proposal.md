# Proposition : partager un seul cycle de vie entre les deux passerelles MCP

## Why

Le constat `AUD-QUA-20` (Faible) de l'audit `docs/project/audits/2026-10-09/findings.md`, revérifié le 2026-11-06 sur
`30520bd3`, relève que `MinosMcpCodeGateway` (`morpheus-integration-minos`) et `NexusMcpContextGateway`
(`morpheus-integration-nexus`) portent deux copies du cycle de vie d'un client de pair MCP : lancement du transport
borné, armement de l'échéance d'opération, initialisation et vérification des outils requis, appel d'un outil,
fermeture, suppression de la copie vérifiée du JAR et comptage des échecs qui ne font pas échouer l'opération. La mesure
de l'audit du 06/11 sur `30520bd3` en compte 197 lignes communes, sans socle ni test de parité ; `morpheus-mcp-transport`,
dont les deux modules dépendent, ne porte aucun cycle de vie de passerelle. Les réglages des deux pairs ont dérivé de
cette façon avant d'être réunis (`resolve-both-mcp-peers-through-one-settings-resolver`) ; ce changement-là a laissé
les passerelles hors de son périmètre au motif qu'elles diffèrent réellement. La comparaison membre par membre
(`design.md`, « Comparaison des deux passerelles ») montre que ce qui diffère est borné aux opérations, à leurs charges
et à la forme de la commande de lancement ; le cycle de vie, lui, est identique au nom du pair près.

## What Changes

- Le cycle de vie commun (ouverture d'une session bornée, appel d'un outil, fermeture, suppression de la copie vérifiée,
  abandon compté) existe une fois, dans `morpheus-mcp-transport` (`com.morpheus.transport.mcp`), paramétré par le pair :
  son nom, ses outils requis, sa borne de réponse, la fabrique de son exception et les observateurs qui comptent.
- Chaque passerelle garde ce qui est propre à son pair : constantes d'outils, nombre de requêtes par opération,
  préparation du lancement (copie vérifiée du JAR, forme `-cp`/`-jar`, propriété du répertoire de travail), opérations
  et lecture de leurs charges, type d'exception.
- Aucun comportement observable ne change : mêmes messages, mêmes types d'exception, mêmes familles comptées, mêmes
  échéances.
- Une règle d'architecture refuse qu'une classe des intégrations construise à nouveau le client MCP, arme l'échéance ou
  nettoie la copie elle-même, et exige que toute passerelle passe par le socle.

## Capabilities

### New Capabilities
- `mcp-peer-gateway-lifecycle` : le cycle de vie d'un client de pair MCP, commun à MINOS et NEXUS, ce que chaque
  passerelle garde en propre, et ce qui refuse une copie de ce cycle.

### Modified Capabilities

Aucune. `mcp-peer-launch-command` (quel exécutable lance un pair), `suppressed-failure-trace` (familles comptées) et
`mcp-tool-failure-contract` (borne d'une opération déléguée à un pair) gardent leurs exigences ; ce changement déplace
leur implémentation côté passerelle sans changer ce qu'elles disent.

## Périmètre et exclusions

**Inclus :** les membres identiques des deux passerelles (`design.md`, tableau de comparaison) et la règle qui en
refuse la réapparition.

**Exclus :**
- les réglages des pairs, que traite le changement vivant `resolve-both-mcp-peers-through-one-settings-resolver`, non
  touché ici ;
- les opérations des pairs et leurs charges, les runtimes (`MinosIntegrationRuntime`, `NexusIntegrationRuntime`) et
  les fournisseurs (`MinosMcpExternalReferenceResolver`, `NexusMcpTechnicalContextProvider`) ;
- le texte d'un échec formé par les runtimes, traité par `turn-every-failure-into-text-through-one-owner`.

## Acteurs et surfaces concernés

Pairs MINOS et NEXUS lancés en MCP STDIO ; mainteneur et CI. Aucune surface publique ne change :
`contracts/public-surfaces.tsv` et l'OpenAPI ne bougent pas.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Les deux passerelles portent le même cycle de vie au nom du pair près | existant vérifié | lecture intégrale des deux fichiers et `diff` après remplacement de `MINOS`/`NEXUS` par un même nom, `design.md` |
| Longueurs des deux fichiers et lignes communes | existant vérifié | mesure de l'audit du 06/11 sur `30520bd3` ; longueurs relues ce jour, identiques |
| Aucun test ne compare les deux passerelles | existant vérifié | `ExternalCodeTrustBoundaryArchitectureTest`, `SilentCatchArchitectureTest`, `UnreachableSecurityBranchArchitectureTest`, `ProductionIntegrityContractTest` les nomment ensemble sans les comparer |
| Le transport ne dépend d'aucun autre paquet MORPHEUS | existant vérifié | `AdapterCompositionRootArchitectureTest#theMcpTransportDependsOnNoOtherMorpheusPackage` |
| Le socle vit dans `morpheus-mcp-transport` et reçoit le comptage par des observateurs | décision | `design.md`, D1 et D2 |
| La parité est tenue par la structure (un seul socle) et par les tests existants de chaque passerelle | décision | `design.md`, D4 |

## Impact

- Code : `morpheus-mcp-transport` (une classe publique ajoutée au paquet `com.morpheus.transport.mcp`),
  `MinosMcpCodeGateway`, `NexusMcpContextGateway`.
- Tests : tests du socle dans `morpheus-mcp-transport` ; `MinosSuppressedFailureTraceTest`,
  `NexusSuppressedFailureTraceTest`, `MinosMcpTransportIntegrationTest`, `NexusMcpTransportIntegrationTest` retargetés ;
  tests d'architecture `McpPeerGatewayLifecycleArchitectureTest` (nouveau), `SilentCatchArchitectureTest`,
  `ExternalCodeTrustBoundaryArchitectureTest` (épinglages réalignés, jamais affaiblis).
- Documentation : `docs/developer/specifications/mcp-peer-gateway-lifecycle.md` (nouvelle) et son index.
- ADR : ADR-0128 (pairs par MCP STDIO) et ARCH-0162 (décision 3, le transport ne connaît aucun type MORPHEUS) restent
  vrais ; ADR-0128 nomme les passerelles qui vérifient les outils requis et se clarifie si son texte ne l'est plus
  (`design.md`, D1).

## Questions ouvertes

- Le mainteneur préfère-t-il un module dédié aux clients de pairs (qui verrait `morpheus-application`) au socle dans
  `morpheus-mcp-transport` ? `design.md`, D1, écarte cette option pour son coût.
