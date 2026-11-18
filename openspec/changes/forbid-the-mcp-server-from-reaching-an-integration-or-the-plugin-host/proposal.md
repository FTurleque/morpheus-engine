# Proposition : interdire au serveur MCP d'atteindre une intégration ou l'hôte de plugins (AUD-ARC-29)

## Why

Constat `AUD-ARC-29` (Faible), `docs/project/audits/2026-11-17/findings.md`, revérifié le 17/11/2026 sur `1a539c22`.
L'adaptateur HTTP a sa règle de frère : `LayerDependencyTest#httpApiMustRemainSiblingOfCliAndMcp` lui interdit
`com.morpheus.integration..`. Le serveur MCP n'en a pas : `AdapterSiblingArchitectureTest.MCP_IGNORES_HTTP_AND_CLI`
ne lui interdit que l'adaptateur HTTP et la CLI, et `AdapterCompositionRootArchitectureTest.CONCRETE_ADAPTERS` ne nomme
que les stores et les providers. Seul le POM de `morpheus-mcp` empêche aujourd'hui un outil MCP d'appeler une
intégration MINOS ou NEXUS en direct, alors que la Javadoc d'`AdapterSiblingArchitectureTest` annonce précisément de
refuser une arête ajoutée à un POM. Même trou pour l'hôte de plugins (`com.morpheus.sdk..`, qui lance des processus) :
aucune règle ne borne qui, dans `api` et `mcp`, l'atteint.

## What Changes

- Le serveur MCP (`com.morpheus.mcp..`) ne dépend d'aucune intégration (`com.morpheus.integration..`) : règle de
  bytecode ajoutée à la règle de frère du MCP, prouvée par une violation plantée.
- Le serveur MCP ne dépend d'aucune classe du SDK provider (`com.morpheus.sdk..`) ; vrai seulement après
  `remove-the-unserved-provider-plugin-mcp-tool` (`AUD-ARC-27`).
- L'adaptateur HTTP n'atteint le SDK provider que par les classes qu'une liste exacte du gate nomme ; la forme de cette
  liste (racine de composition d'`ARCH-0162` ou table de faits propre au SDK) attend la décision du mainteneur.
- Aucune surface publique, aucun format persisté ne change ; seuls des tests d'architecture changent (justification
  de PR exigée).

## Capabilities

### New Capabilities

### Modified Capabilities
- `provider-sdk-dependency-boundary` : les règles de frères couvrent le serveur MCP face aux intégrations et au SDK,
  et l'accès de l'adaptateur HTTP au SDK est borné par une liste nommée.

## Périmètre et exclusions

**Inclus :** `AdapterSiblingArchitectureTest` (règle de frère du MCP, sujets), selon la décision
`AdapterCompositionRootArchitectureTest` ou une nouvelle liste de classes déclarée dans
`ProductionClassListsArchitectureTest`, la page durable de la capacité, `.claude/rules/architecture.md`.

**Exclus :** le retrait de l'outil MCP mort (`AUD-ARC-27`, prérequis) ; la séparation SPI / hôte du SDK
(`AUD-ARC-30`, change `separate-the-provider-plugin-spi-from-the-plugin-host`), qui changera le paquet que vise la
règle sur le SDK ; la CLI, racine du processus, qui dépend légitimement de tout.

## Acteurs et surfaces concernés

Mainteneur et contributeur qui ajoutent une dépendance à `morpheus-mcp` ou `morpheus-api` ; CI (verdict de build).

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Aucune règle n'interdit `com.morpheus.integration..` au MCP | existant vérifié | `AdapterSiblingArchitectureTest.java:100-101`, `LayerDependencyTest.java:52-62` |
| `CONCRETE_ADAPTERS` ne nomme ni intégration ni SDK | existant vérifié | `AdapterCompositionRootArchitectureTest.java:41` |
| Dans `morpheus-api`, `MorpheusProviderPluginApiService` importe le SDK et `MorpheusProviderPluginHttpRoutes` reçoit ses types | existant vérifié | imports ; signatures `discover` / `probe` du service |
| L'hôte de plugins est-il un adaptateur concret au sens d'`ARCH-0162` ? | question ouverte | décision du mainteneur, design.md D3 |

## Impact

`morpheus-architecture-tests` uniquement (fichiers de gouvernance). Si l'option A de D3 est retenue : ADR consolidé qui
remplace `ARCH-0162` (ADR à proposer) et hausse de la clé `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS`
de `config/debt-counter-ratchets.properties` ; si l'option B est retenue : une table de faits nouvelle, sans ADR.
Dépend de `remove-the-unserved-provider-plugin-mcp-tool` (`AUD-ARC-27`) : interdire `com.morpheus.sdk..` au MCP casse
le build tant que l'outil mort porte l'arête.

## Questions ouvertes

- L'hôte de plugins (`com.morpheus.sdk..` côté hôte) est-il un adaptateur concret au sens d'`ARCH-0162`, à ajouter à
  `CONCRETE_ADAPTERS` avec ses classes d'`api` comme racines nommées (ADR consolidé à proposer), ou une frontière propre
  tenue par une table de faits d'`AdapterSiblingArchitectureTest` ? Proposition : la seconde (design.md D3) ; décision
  du mainteneur.
