# Proposition : écrire que le launcher porte le SDK provider, et le tenir par une règle (AUD-ARC-26)

## Why

Constat `AUD-ARC-26` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, revérifié le 17/11/2026 sur `1a539c22`.
La section Decision de `SEC-0129`, §1, écrit : « Ni `morpheus-domain`, ni `morpheus-application` (POM compris), ni le
launcher ne dépendent du SDK ou de `morpheus-provider-reference` ». Or le launcher unique est `MorpheusMain`
(`GEN-0127` §1), dans `morpheus-cli`, dont le POM déclare `morpheus-provider-sdk` en compilation
(`morpheus-cli/pom.xml:29`) ; `MorpheusProviderPluginCli` importe cinq types de `com.morpheus.sdk.provider` ; et
`ProviderPluginProbeProcess.command` lance `ProviderPluginProbeWorker` avec le classpath de l'hôte, qui doit donc porter
le SDK. Le seul contrôle (`ProviderPluginPlatformContractTest#referenceProviderIsNotEmbeddedAndExecutableSurfacesAreFailClosed`)
vérifie l'absence de `morpheus-provider-reference` dans le POM de la CLI, pas celle du SDK.

`archgate review-context` remet cette section telle quelle aux agents : un agent qui l'applique retirerait le SDK de la
CLI et casserait `provider-plugins discover`, `provider-plugins probe` et la qualification M22 du launcher packagé.
L'intention d'origine est connue : ADR-0090 (archivé) écrivait que le plugin de référence « n'est pas une dépendance
runtime du launcher », sans rien dire du SDK. Maintenant : `AUD-ARC-30` (change
`separate-the-provider-plugin-spi-from-the-plugin-host`) suppose que la décision dise d'abord qui dépend de quoi.

## What Changes

- Texte de la décision sur les plugins : le launcher porte le SDK (il en exécute la découverte, le probe et le worker)
  et n'embarque jamais `morpheus-provider-reference`. Forme du changement — clarification de `SEC-0129` ou ADR
  consolidé qui le remplace — : **décision du mainteneur** (design.md D1).
- Nouvelle règle de build : l'ensemble des modules du réacteur qui déclarent `morpheus-provider-sdk` en compilation
  est exactement celui qu'elle nomme ; `domain`, `application` et les providers intégrés n'y sont jamais. La décision
  cite cette règle au lieu d'énumérer les modules.
- Le contrôle textuel M22 existant (plugin de référence absent du POM de la CLI) est gardé.
- Aucune surface publique, aucun format persisté ne change.

## Capabilities

### New Capabilities

### Modified Capabilities
- `provider-sdk-dependency-boundary` : les modules qui compilent contre le SDK sont une liste exacte tenue par le build.

## Périmètre et exclusions

**Inclus :** `SEC-0129` (ou son remplaçant), une méthode de test d'architecture qui lit les POM, la page durable de la
capacité, `docs/developer/PROVIDER_SDK.md` (phrase « sans modifier … ni le launcher »).

**Exclus :** la séparation SPI / hôte (`AUD-ARC-30`), qui modifiera de nouveau ce que la décision dit du SDK ; le
retrait de la dépendance SDK de `morpheus-mcp` (`AUD-ARC-27`), que la liste suit.

## Acteurs et surfaces concernés

Agents et contributeurs qui lisent la décision par `archgate review-context` ; mainteneur ; CI.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| `SEC-0129` §1 dit que le launcher ne dépend pas du SDK | existant vérifié | `docs/architecture/adr/SEC-0129-plugins-provider-code-de-confiance-hors-processus.md`, Decision §1 |
| Le launcher est `MorpheusMain`, dans `morpheus-cli` | existant vérifié | `GEN-0127` §1 |
| `morpheus-cli` compile contre le SDK et le probe l'exige sur le classpath | existant vérifié | `morpheus-cli/pom.xml:29`, `MorpheusProviderPluginCli`, `ProviderPluginProbeProcess.java:253-264` |
| L'intention d'origine visait le plugin de référence | existant vérifié | ADR-0090 (archivé), « n'est pas une dépendance runtime du launcher » |
| Clarification plutôt qu'ADR consolidé | proposition à valider | design.md D1 |

## Impact

`morpheus-architecture-tests` (nouvelle méthode, justification de PR) ; `SEC-0129` ou ADR consolidé à proposer ;
`docs/architecture/adr/README.md` et `docs/architecture/arc42/09-decisions.md` si un ADR est créé ;
`docs/developer/PROVIDER_SDK.md` ; page durable `provider-sdk-dependency-boundary`. Bloque `AUD-ARC-30`
(`separate-the-provider-plugin-spi-from-the-plugin-host`). La liste suit `AUD-ARC-27`
(`remove-the-unserved-provider-plugin-mcp-tool`) : appliqué avant, `morpheus-mcp` n'y figure pas.

## Questions ouvertes

- Clarifier `SEC-0129` §1 (le sens visé, celui d'ADR-0090 (archivé), ne change pas) ou écrire un ADR consolidé qui le
  remplace ? Décision du mainteneur ; proposition : clarification si `AUD-ARC-30` n'est pas engagé dans la foulée,
  sinon un seul ADR consolidé pour les deux constats (design.md D1).
