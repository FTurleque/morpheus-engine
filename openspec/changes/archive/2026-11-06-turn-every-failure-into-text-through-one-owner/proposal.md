# Proposition : former le texte de tout échec par un seul propriétaire

## Why

Le constat `AUD-QUA-19` (Faible) de l'audit `docs/project/audits/2026-10-09/findings.md`, revérifié le 2026-11-06 sur
`30520bd3`, relève que la règle qui a retiré `safeMessage` de la CLI ne lit que `com.morpheus.cli` : hors de
`morpheus-cli`, sept méthodes privées refont la même conversion d'un échec en texte (son message, ou le nom simple de
son type quand le message est absent ou blanc), dans cinq modules et sous trois types de paramètre. Le dépôt a déjà
écrit le patron de clôture pour la CLI : `CliErrorLine.failureText(Throwable)` est le corps exact de ces copies, et
`CliArgumentBaseArchitectureTest#noCliTypeButTheOwnerTurnsAFailureIntoText` refuse une copie **par sa signature**
(`Throwable` → `String`), avec un propriétaire unique nommé — mais seulement dans `com.morpheus.cli`
(`CliArgumentBaseArchitectureTest.java:53` et `:67-69`). Sept copies d'une chose qui ne doit pas différer dérivent comme
l'ont fait les onze copies des outils MCP avant `McpToolFailure`.

## What Changes

- Un propriétaire partagé, `FailureText.of(Throwable)` dans `com.morpheus.application.foundation`, forme le texte d'un
  échec. Les sept copies et `CliErrorLine.failureText` disparaissent ; leurs appelants l'appellent.
- La forme filtrée (texte d'un échec relayé hors de la machine), aujourd'hui écrite deux fois
  (`BoundaryFailureMessage.safe` et `OpenSpecSourceAttribution.relayable`), a elle aussi un seul propriétaire,
  `ServerLocationDisclosure.relayableText(Throwable)`, qui applique le filtre au texte de `FailureText.of`.
- La règle par signature est étendue de `com.morpheus.cli` à `com.morpheus..`, avec ses deux propriétaires et une table
  de faits des méthodes de même signature et d'autre usage ; un scan textuel sur tout `src/main/java` la double pour
  les copies que le bytecode ne voit pas (lambda, expression en ligne, module hors du classpath des tests
  d'architecture).
- Aucun texte d'échec ne change, sauf un : la copie en ligne de `SqliteSavedViewStore` garde un message blanc ; elle
  écrira le nom du type, comme toutes les autres (`design.md`, D6).

## Capabilities

### New Capabilities
- `failure-text-ownership` : qui forme le texte d'un échec, filtré ou non, dans tout le produit, et ce qui refuse une
  copie de cette conversion.

### Modified Capabilities

Aucune. `cli-argument-base` garde ses exigences : « A private copy of the argument base SHALL be refused » reste vrai
d'une classe de la CLI ; seule sa preuve change de classe de test (`design.md`, D2). `http-failure-disclosure` garde
`BoundaryFailureMessage.safe` comme seul point d'obtention du texte dans `morpheus-api` (`design.md`, D4).

## Périmètre et exclusions

**Inclus :** les méthodes de production de `com.morpheus..` qui prennent un échec et rendent son texte ; la copie en
ligne de `SqliteSavedViewStore` ; la règle d'architecture et son double textuel.

**Exclus :**
- le filtrage des chemins : aucune des sept copies ne filtre, et ce changement n'en ajoute ni n'en retire
  (`design.md`, D7 et question ouverte) ;
- `McpDiagnosticRedactor.describe` (`morpheus-mcp-transport`), qui forme un autre texte (type et message expurgé) dans
  un module qui ne peut voir aucun type MORPHEUS ;
- les méthodes `failureType`, qui ne rendent que le nom du type pour la frontière distante.

## Acteurs et surfaces concernés

Mainteneur et CI (règle d'architecture). Aucune surface publique ne change : `contracts/public-surfaces.tsv` et
l'OpenAPI ne bougent pas ; les textes rendus par la CLI, le MCP et le HTTP restent ceux d'aujourd'hui, sauf le cas de
D6 : la raison d'une vue enregistrée illisible dont l'échec porte un message blanc.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Sept copies hors CLI, cinq modules, trois types de paramètre | existant vérifié | `ProviderPluginService.java:129`, `LocalSourceInventoryScanner.java:349`, `ProviderPluginDiscovery.java:250`, `ReasoningService.java:146`, `MinosIntegrationRuntime.java:67`, `McpToolFailure.java:27`, `NexusMcpTechnicalContextProvider.java:94` (mesure de l'audit du 06/11 sur `30520bd3`, relue ce jour) |
| `CliErrorLine.failureText` est le corps exact des copies | existant vérifié | `morpheus-cli/src/main/java/com/morpheus/cli/command/CliErrorLine.java:23-26` |
| La règle par signature est bornée à la CLI | existant vérifié | `CliArgumentBaseArchitectureTest.java:53`, `:67-69`, `:71-74`, `:86-89` |
| La forme filtrée est écrite deux fois | existant vérifié | `BoundaryFailureMessage.java:22`, `OpenSpecSourceAttribution.java:104` (second site absent du constat) |
| Les cinq modules voient `morpheus-application` sans arête nouvelle | existant vérifié | `pom.xml` de `morpheus-provider-sdk`, `morpheus-mcp`, `morpheus-integration-minos`, `morpheus-integration-nexus`, `morpheus-application` |
| Propriétaire dans `application.foundation` | décision | `design.md`, D1 |
| Second propriétaire filtrant dans `application.security` | décision | `design.md`, D4 |
| Le message blanc de `SqliteSavedViewStore` devient le nom du type | proposition à valider | `design.md`, D6 |

## Impact

- Code : `morpheus-application` (`foundation.FailureText` ajouté, `security.ServerLocationDisclosure`, `ReasoningService`,
  `LocalSourceInventoryScanner`), `morpheus-provider-sdk` (`ProviderPluginService`, `ProviderPluginDiscovery`),
  `morpheus-mcp` (`McpToolFailure`), `morpheus-integration-minos` (`MinosIntegrationRuntime`),
  `morpheus-integration-nexus` (`NexusMcpTechnicalContextProvider`), `morpheus-cli` (`CliErrorLine`, `CliCommandRun`,
  `MorpheusMain`), `morpheus-api` (`BoundaryFailureMessage`), `morpheus-provider-openspec`
  (`OpenSpecSourceAttribution`), `morpheus-store-sqlite` (`SqliteSavedViewStore`).
- Tests d'architecture : `FailureTextOwnershipArchitectureTest` (nouveau), `CliArgumentBaseArchitectureTest`,
  `CliOptionValueHasOneBlankDefinitionTest`, `AuditRemediationContractTest`, `ProductionClassListsArchitectureTest`.
- Documentation : `docs/developer/specifications/failure-text-ownership.md` (nouvelle),
  `docs/developer/specifications/README.md`, `docs/developer/specifications/cli-argument-base.md`.
- ADR : aucun (filtre de `.claude/rules/adr.md` : une aide partagée tenue par un test se renverse sans coût).

## Questions ouvertes

- Le changement de D6 (message blanc d'une vue enregistrée illisible) est-il accepté, ou la copie doit-elle garder son
  texte et être nommée dans la table des exceptions ? — mainteneur.
- Faut-il, dans un changement séparé, filtrer les chemins dans les textes qui atteignent un client MCP ou le
  raisonnement (`design.md`, D7) ? — mainteneur.
