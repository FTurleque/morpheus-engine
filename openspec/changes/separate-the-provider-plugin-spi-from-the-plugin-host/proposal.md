# Proposition : séparer le SPI des plugins provider de l'hôte de plugins (AUD-ARC-30)

## Why

Constat `AUD-ARC-30` (Faible), `docs/project/audits/2026-11-17/findings.md`, revérifié le 17/11/2026 sur `1a539c22`.
`com.morpheus.sdk.provider` porte treize types publics de premier rang. Un plugin n'en utilise que trois :
`morpheus-provider-reference` et `morpheus-provider-testkit` n'importent que `MorpheusProviderPlugin`,
`ProviderPluginMetadata` et `ProviderSdk`. Les transports en utilisent six autres (`ProviderPluginService`,
`ProviderPluginDiscovery`, `ProviderPluginDiscoveryResult`, `ProviderPluginCandidate`, `ProviderPluginProbeOutcome`,
`ProviderPluginViews`, ce dernier de 336 lignes : projections locale et remote-safe des transports). Tout est dans un
seul artefact que `SEC-0129` déclare « surface publique à maintenir de façon compatible » et que
`morpheus-provider-sdk/pom.xml` décrit comme « Public provider plugin SDK, metadata, discovery and explicit activation
platform ».

Conséquences : la liste blanche de divulgation remote et le moteur du probe sont publiés aux auteurs de plugins, un
plugin compile contre l'API de l'hôte, et `UnreferencedPublicMethodArchitectureTest` exclut tout le module
(`NOT_JUDGED`), donc tout code hôte échappe au contrôle de code mort.

## What Changes

- Le SPI publié aux auteurs de plugins ne contient que les types qu'un plugin implémente ou lit (contrat du plugin,
  métadonnées, version d'API) ; découverte, compatibilité, activation, probe, worker, service et vues vivent dans un
  hôte que les plugins ne voient pas. Forme de la séparation — paquet hôte dans le même module, ou module hôte
  distinct — : **décision du mainteneur** (design.md D1), ADR à proposer.
- Le contrôle de code mort ne dispense plus que le SPI : les méthodes publiques de l'hôte sont jugées.
- Une règle de build refuse qu'un plugin (référence, testkit) atteigne l'hôte, et qu'un type hôte rentre dans le SPI.
- **BREAKING** pour un auteur de plugin qui importait un type hôte : il ne compile plus contre le SPI. Aucun plugin du
  dépôt n'en importe (mesuré) ; `ProviderSdk.API_VERSION` et le manifeste `sdk.apiVersion` : question ouverte.
- Aucune surface CLI, MCP ou HTTP ne change ; aucun format persisté ne change.

## Capabilities

### New Capabilities

### Modified Capabilities
- `provider-sdk-dependency-boundary` : le SPI publié ne contient que les types destinés aux plugins, et un plugin
  n'atteint pas l'hôte.
- `dead-code-refusal` : seule la part SPI du SDK est dispensée du contrôle des méthodes publiques sans appelant.

## Périmètre et exclusions

**Inclus :** `morpheus-provider-sdk` (et, selon D1, un module hôte à créer), `morpheus-provider-reference`,
`morpheus-provider-testkit` (imports inchangés en principe), `morpheus-api`, `morpheus-cli` (imports des types hôtes),
`UnreferencedPublicMethodArchitectureTest`, `AdapterSiblingArchitectureTest`, les tests d'architecture qui lisent les
sources du SDK par chemin, `docs/developer/PROVIDER_SDK.md`, `SEC-0129` ou son remplaçant.

**Exclus :** le texte de la décision sur la dépendance du launcher (`AUD-ARC-26`, change
`state-that-the-launcher-carries-the-provider-sdk`, prérequis) ; la publication effective d'artefacts (aucun workflow
du dépôt ne publie le SDK aujourd'hui : relevé à confirmer par le mainteneur).

## Acteurs et surfaces concernés

Auteur de plugin (compile contre le SPI) ; mainteneur ; transports CLI et HTTP (hôtes du probe) ; CI.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Treize types publics de premier rang dans `com.morpheus.sdk.provider` | existant vérifié | `grep` des déclarations `public` de premier rang du module |
| Plugins : trois types importés ; transports : six autres | existant vérifié | `grep ^import com.morpheus.sdk` sur les sources principales |
| Le module entier est hors du contrôle de code mort | existant vérifié | `UnreferencedPublicMethodArchitectureTest.NOT_JUDGED` |
| Paquet hôte ou module hôte | question ouverte | décision du mainteneur, design.md D1 |
| Faut-il relever `ProviderSdk.API_VERSION` ? | question ouverte | design.md D3 |

## Impact

Modules `morpheus-provider-sdk`, `morpheus-api`, `morpheus-cli`, `morpheus-mcp` si `AUD-ARC-27` n'est pas appliqué ;
selon D1 un module de réacteur ajouté (POM racine, `ModulePackageCorrespondenceTest`,
`config/m21-per-module-coverage-floors.properties`, pages qui listent le réacteur, `05-vue-blocs.md`, scripts de build
portable) ; tests d'architecture (justification de PR) ; ADR à proposer (consolidé, remplace `SEC-0129`). Dépend de
`state-that-the-launcher-carries-the-provider-sdk` (`AUD-ARC-26`) ; la règle MCP → SDK de
`forbid-the-mcp-server-from-reaching-an-integration-or-the-plugin-host` (`AUD-ARC-29`) suit le paquet de l'hôte.

## Questions ouvertes

- Paquet hôte dans `morpheus-provider-sdk` ou module hôte distinct ? Décision du mainteneur, ADR à proposer
  (design.md D1).
- La séparation change-t-elle la version d'API du SDK (`ProviderSdk.API_VERSION`, `sdk.apiVersion`) ? Décision du
  mainteneur (design.md D3).
