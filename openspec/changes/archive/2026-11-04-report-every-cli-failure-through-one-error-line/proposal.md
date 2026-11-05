# Proposition : écrire chaque échec de la CLI par une seule ligne d'erreur

## Why

`cli-argument-base` exige qu'un échec de toute sous-commande soit écrit en une ligne, dans un format, par un seul
écrivain. Le code ne le fait pas (relevé du 2026-11-03 sur `ef006fb7`, page
`docs/developer/specifications/cli-argument-base.md`) : `version`, `product-info`, `update-check`, `reason`,
`provider-plugins`, `server`, les lanceurs `api`, `mcp` et `api --remote`, et le refus du mode ou du fichier de journal
dans `MorpheusMain` écrivent chacun leur propre préfixe (`MORPHEUS usage error: `, `MORPHEUS server error: `,
`MORPHEUS MCP startup error: `…), sans code de sortie dans la ligne et sans forme JSON. `CliOptionRefusalTableTest`
fige ces préfixes, et `CliArgumentBaseArchitectureTest` ne cherche que le littéral `"MORPHEUS error [`. Un script qui
lit les erreurs de la CLI doit donc connaître neuf formats. Le mainteneur a choisi d'unifier plutôt que de déclarer ces
différences dans la spec.

## What Changes

- **BREAKING** (texte de la sortie d'erreur seulement) : chaque échec de ces commandes et lanceurs est écrit
  `MORPHEUS error [<code>]: <message>`, ou en objet JSON `{"exitCode":…,"code":"…","message":"…"}` quand l'invocation
  porte `--json`. Les codes de sortie ne changent pas.
- Le refus d'une `CliFailure` sans message écrit le nom de son type, comme les autres échecs.
- Le build refuse tout autre littéral de préfixe d'erreur `MORPHEUS …error…` dans les sources de la CLI.
- Notes de version et guide d'installation de 1.2.2 mis à jour.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `cli-argument-base` : l'exigence de la ligne d'erreur unique couvre explicitement les lanceurs et retire la clause
  « The format … MUST NOT change », que l'unification contredit ; une exigence ajoutée refuse un préfixe propre.

## Périmètre et exclusions

**Inclus :** toutes les lignes d'erreur écrites par `morpheus-cli` sur la sortie d'erreur, lanceurs compris.

**Exclus :**
- les lignes d'information des lanceurs (`MORPHEUS API listening on …`), qui ne sont pas des erreurs ;
- les codes de sortie, inchangés ;
- le texte des messages eux-mêmes, sauf l'ajout du nom du transport au refus de démarrage d'un lanceur (voir
  `design.md`, D3).

## Acteurs et surfaces concernés

Utilisateur de la CLI et script qui lit sa sortie d'erreur ; opérateur qui lance `api`, `mcp` ou le serveur distant.
Aucune surface HTTP ni MCP ne change : `contracts/public-surfaces.tsv` et l'OpenAPI ne bougent pas.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Neuf préfixes d'erreur coexistent dans `morpheus-cli` | existant vérifié | recherche des littéraux `"MORPHEUS …error` dans `morpheus-cli/src/main/java` |
| Ces préfixes sont figés par les tests | existant vérifié | `CliOptionRefusalTableTest.TABLE`, `MorpheusServerCliTest`, `MorpheusProductCliTest`, `MorpheusProviderPluginCliTest`, `OperationalLogModeLaunchTest`, `MorpheusStateRefusalExitCodeTest` |
| Unifier plutôt que déclarer | décision | choix du mainteneur, 2026-11-03 |
| Les codes de sortie restent ceux d'aujourd'hui | décision | `design.md`, D2 |

## Impact

- Code : `CliErrorLine`, `CliCommandRun`, `MorpheusProductCli`, `MorpheusReasoningCli`, `MorpheusProviderPluginCli`,
  `MorpheusServerCli`, `MorpheusMain` (`morpheus-cli`).
- Tests : les attentes des tests cités ci-dessus ; `CliArgumentBaseArchitectureTest` (règle ajoutée).
- Documentation : `docs/user/release-notes/RELEASE_NOTES_1.2.2.md`, `docs/user/INSTALLATION.md`,
  `docs/developer/specifications/cli-argument-base.md`.
- ADR : ARCH-0149 couvre la classe de capacité ; aucune décision nouvelle de portée ADR.

## Questions ouvertes

Aucune.
