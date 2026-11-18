# Proposition : retirer l'outil MCP de plugins provider que rien ne sert (AUD-ARC-27)

## Why

Constat `AUD-ARC-27` (Faible), `docs/project/audits/2026-11-17/findings.md`, revérifié le 17/11/2026 sur `1a539c22`.
`MorpheusMcpServer` ajoute `new MorpheusProviderPluginMcpTools().specifications()` à ses outils
(`morpheus-mcp/src/main/java/com/morpheus/mcp/server/MorpheusMcpServer.java:164`) ; ce constructeur sans argument fixe
un répertoire absent, et `specifications()` rend alors une liste vide (`MorpheusProviderPluginMcpTools.java:30-31`,
`:40-41`). Le constructeur qui prend un répertoire (`:34`) n'est appelé que par deux tests du module
(`MorpheusProviderPluginMcpToolsTest`, `MorpheusProviderPluginMcpExposureTest`). L'outil `discover_provider_plugins`
n'a donc aucun chemin de production : c'est un interrupteur dormant que `.claude/rules/code-style.md` refuse (pas de
code mort, pas de feature flag), et c'est le seul code de `morpheus-mcp` qui importe `com.morpheus.sdk.provider`
(`:4-5`), donc la seule raison de l'arête POM `morpheus-mcp` → `morpheus-provider-sdk`.

La documentation en dit plus que le code : `docs/user/PROVIDER_PLUGINS.md` annonce un outil
`discover_provider_plugins(directory)` model-facing, et `docs/developer/PROVIDER_SDK.md` un outil servi « quand
l'opérateur configure un répertoire de plugins au lancement du serveur MCP » ; aucun lancement ne le permet.

Maintenant : `AUD-ARC-29` (`forbid-the-mcp-server-from-reaching-an-integration-or-the-plugin-host`) interdit au
serveur MCP d'atteindre l'hôte de plugins, ce qui casse le build tant que cette arête existe.

## What Changes

- Retrait de `MorpheusProviderPluginMcpTools`, de son ajout dans `MorpheusMcpServer`, de la dépendance
  `morpheus-provider-sdk` du POM de `morpheus-mcp` et des deux tests qui n'exercent que le constructeur à répertoire.
- Aucun outil servi ne disparaît : le câblage par défaut ne sert aucun outil de plugin aujourd'hui, et la colonne `mcp`
  des lignes `provider.plugins.discover` et `provider.plugins.probe` de `contracts/public-surfaces.tsv` reste
  `EXPLICITLY_NOT_EXPOSED`. Seule la note de `provider.plugins.discover` est réécrite pour dire que le MCP ne déclare
  aucun outil de plugin (proposition, design.md D3). Aucune route HTTP, aucune OpenAPI ne change.
- Les assertions textuelles M22 qui épinglent le texte de la classe retirée (`this.pluginDirectory = Optional.empty()`,
  `ProviderPluginViews.remoteDiscovery`, `PROVIDER_PLUGIN_DISCOVERY_FAILED`) sont remplacées par un contrôle qui refuse
  toute déclaration d'outil de plugin dans le module MCP.
- Les listes d'entrées exigées du JAR ombré dans `distribution/build-portable.ps1` et `.sh` perdent la classe retirée.
- Documentation durable et ADR : retrait des mentions de l'outil (pages développeur et utilisateur, `BE-0102` §3,
  contexte et conformité de `SEC-0129`).

## Capabilities

### New Capabilities

### Modified Capabilities
- `public-surface-convergence` : le module MCP ne déclare aucun outil de plugin provider, sous aucun câblage.

## Périmètre et exclusions

**Inclus :** `morpheus-mcp` (classe, serveur, POM, tests), les tests d'architecture qui lisent la classe, les deux
scripts de build portable, la note de la ligne `provider.plugins.discover`, les pages et ADR qui nomment l'outil.

**Exclus :** la règle qui interdit au serveur MCP de dépendre de l'hôte de plugins (`AUD-ARC-29`, change
`forbid-the-mcp-server-from-reaching-an-integration-or-the-plugin-host`) ; la séparation SPI / hôte du SDK
(`AUD-ARC-30`) ; `docs/project/roadmap/M22_EXECUTION.md`, relevé daté dont la prose n'est pas réécrite.

## Acteurs et surfaces concernés

Client MCP (aucun changement observable : aucun outil servi ne disparaît) ; mainteneur et contributeur qui lisent les
pages MCP et plugins ; auteur de la qualification du launcher packagé (liste des classes exigées du JAR).

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le câblage par défaut ne sert aucun outil de plugin | existant vérifié | `MorpheusMcpServer.java:164`, `MorpheusProviderPluginMcpTools.java:30-41` |
| Le constructeur à répertoire n'a aucun appelant de production | existant vérifié | grep du dépôt : deux tests de `morpheus-mcp` seulement |
| L'outil est le seul importateur du SDK dans `morpheus-mcp` | existant vérifié | grep `^import com.morpheus.sdk` sur `morpheus-mcp/src/main` |
| La documentation annonce un outil MCP de découverte disponible | existant vérifié | `docs/user/PROVIDER_PLUGINS.md`, `docs/developer/PROVIDER_SDK.md` |
| La note du manifeste est réécrite | proposition à valider | design.md D3 |
| Aucun ADR nouveau n'est requis | décision | `.claude/rules/adr.md` : retrait de code mort ; `SEC-0129` §7 dit déjà « rien en MCP » |

## Impact

`morpheus-mcp` (une classe et deux tests retirés, POM), `morpheus-architecture-tests` (`ProviderPluginPlatformContractTest`,
`ProviderPluginTrustBoundaryContractTest`, `AuditRemediationContractTest`, `SilentCatchArchitectureTest`,
`PublicSurfaceManifestCoversEveryServedToolTest` : fichiers de gouvernance, justification de PR exigée),
`distribution/build-portable.ps1` et `.sh`, `contracts/public-surfaces.tsv` (note seulement, justification de PR),
`docs/developer/MCP.md`, `docs/developer/PROVIDER_SDK.md`, `docs/user/PROVIDER_PLUGINS.md`, `BE-0102`, `SEC-0129`.
Ce change débloque `AUD-ARC-29`.

## Questions ouvertes

- Retirer de `BE-0102` §3 la règle de rédaction propre à la classe retirée est-il une clarification (la règle n'a
  plus de sujet) ou un changement partiel qui appelle un ADR consolidé ? Proposition : clarification (design.md D4) ;
  décision du mainteneur.
