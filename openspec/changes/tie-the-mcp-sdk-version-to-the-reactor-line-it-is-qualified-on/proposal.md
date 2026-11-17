# Proposition : lier la version du SDK MCP à la ligne Reactor sur laquelle il est qualifié (AUD-DEP-31)

## Why

Le POM racine importe `mcp-bom` (`mcp-sdk.version`, `2.1.0` le 17/11/2026) puis `reactor-bom`, qui l'emporte sur la
version de `reactor-core` que le SDK déclare. `DependencyLineCurrencyTest` ne lit que `reactor-bom.version`, jamais
`mcp-sdk.version`, et `<dependencyConvergence/>` ne voit pas un écart résolu par un BOM importé. `.github/dependabot.yml`
ne groupe pas les deux BOM : il proposera `mcp-bom` 2.2.0 seul. Or `mcp-core` 2.2.0 (publié le 13/11/2026) est bâti sur
`reactor-core` 3.9.0-RC1, quand `mcp-core` 2.1.0 l'est sur 3.7.0. La pull request du robot passerait la convergence,
l'Enforcer et le test de ligne avec un SDK compilé contre Reactor 3.9 exécuté sur la ligne tenue ; seule une erreur à
l'exécution sur un chemin couvert par les tests le révélerait, et le commentaire de l'import deviendrait faux sans
qu'aucun test ne casse.

Preuve : constat `AUD-DEP-31` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd`, revérifié le 17/11/2026 sur `1a539c22` (POM, `DependencyLineCurrencyTest`, `.github/dependabot.yml`,
métadonnées de `mcp-bom` et POM de `mcp-core` 2.2.0 relus sur Maven Central).

## What Changes

- Le build lit `mcp-sdk.version` et refuse une version du SDK MCP qu'une table du test de ligne ne lie pas à la ligne
  Reactor tenue par le POM. La table déclare, pour chaque version qualifiée du SDK, la ligne Reactor attendue.
- Le build refuse une entrée de la table dont la ligne diffère de celle de `reactor-bom.version`.
- Aucune montée du SDK MCP n'est faite par ce change ; la table ne déclare 2.2.0 que lorsque son Reactor est une version
  publiée (aujourd'hui `reactor-core` 3.9.0-RC1, pré-version).

## Capabilities

### New Capabilities

### Modified Capabilities
- `dependency-line-currency` : exigence ajoutée — la version du SDK MCP est liée par le build à la ligne Reactor tenue.

## Périmètre et exclusions

**Inclus :** `DependencyLineCurrencyTest` (`morpheus-architecture-tests`, paquet `d2`), la page durable de la capacité,
et, si le mainteneur le retient (design.md, D2), un groupe Dependabot qui propose `mcp-bom` et `reactor-bom` ensemble.

**Exclus :** le choix de la ligne Reactor, tranché par le change `decide-the-reactor-line-the-mcp-transport-runs-on`
(`AUD-DEP-30`), dont celui-ci dépend : la table encode la ligne retenue là-bas. La montée du SDK vers 2.2.0.

## Acteurs et surfaces concernés

Dependabot, qui propose les montées de `mcp-bom` ; le mainteneur, qui qualifie une version du SDK ; le build.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Aucun test ne lit `mcp-sdk.version` | existant vérifié | `DependencyLineCurrencyTest` |
| `mcp-core` 2.2.0 dépend de `reactor-core` 3.9.0-RC1 | existant vérifié | `mcp-core-2.2.0.pom`, Maven Central, relu le 17/11/2026 |
| Dependabot propose `mcp-bom` sans grouper `reactor-bom` | existant vérifié | `.github/dependabot.yml`, écosystème `maven` sans `groups` |
| La table vit dans le test de ligne | proposition à valider | design.md, D1 |
| Grouper les deux BOM dans Dependabot | question ouverte | design.md, D2 |

## Impact

`morpheus-architecture-tests` (`DependencyLineCurrencyTest`, fichier de gouvernance : justification de PR),
éventuellement `.github/dependabot.yml`, page durable `dependency-line-currency`. Dépend de `AUD-DEP-30`
(`decide-the-reactor-line-the-mcp-transport-runs-on`). Aucune surface publique, aucun code de production.

## Questions ouvertes

- **Mainteneur** : grouper `mcp-bom` et `reactor-bom` dans `.github/dependabot.yml` en plus du gate (design.md, D2) ?
