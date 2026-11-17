# Proposition : servir toute l'aide de la CLI par la branche de l'aide (AUD-ARC-31)

## Why

ARCH-0149 décide que « l'aide et `unknown command` restent à `MorpheusCli`, branche par défaut », et que
`MorpheusMain` distribue. L'aide embarquée est pourtant écrite par deux classes : `MorpheusCli.printHelp` imprime la
première partie (usage, commandes de base, variables d'environnement, codes de sortie), puis `MorpheusMain.run`, après
le retour du gestionnaire, ajoute le reste par 113 appels `out.println` (sections MCP, API locale, serveur distant,
policies, requêtes, portfolio, plugins, intégrations…) quand l'appel était une demande d'aide réussie.

Revérifié le 17/11/2026 sur `1a539c22` : le bloc `if (exitCode == CliExitCode.SUCCESS.code() && isHelpRequest(args))`
occupe les lignes 229 à 343 de `morpheus-cli/src/main/java/com/morpheus/cli/MorpheusMain.java` (491 lignes), avec 113
`out.println`, les seuls appels d'écriture de la classe. Ajouter une commande impose d'écrire sa ligne d'aide dans le
lanceur, loin de la branche de l'aide ; la moitié de l'aide échappe à `MorpheusCli`, qui est censé la posséder.

Preuve : constat `AUD-ARC-31` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd`, confirmé à la ligne près.

## What Changes

- Le texte d'aide écrit par `MorpheusMain.run` est déplacé dans `MorpheusCli`, à la suite de son texte actuel ;
  `MorpheusMain` n'écrit plus rien sur la sortie standard, et `isHelpRequest` disparaît du lanceur.
- La sortie de `morpheus help`, `morpheus --help`, `morpheus -h` et d'un appel sans commande reste identique à l'octet,
  en texte et sous `--json` ; aucune commande, option, code de sortie ni ligne de `contracts/public-surfaces.tsv` ne
  change.
- Une assertion du module d'architecture refuse qu'une classe de `com.morpheus.cli` autre que celle de la branche de
  l'aide écrive un texte d'aide.

## Capabilities

### New Capabilities

### Modified Capabilities
- `adapter-capability-decomposition` : exigence ajoutée — l'aide embarquée de la CLI est écrite par la seule branche
  de l'aide, jamais par le lanceur.

## Périmètre et exclusions

**Inclus :** `MorpheusMain` et `MorpheusCli` (`com.morpheus.cli`), leurs tests, une assertion d'architecture, la page
durable de la capacité.

**Exclus :** l'aide propre d'une famille (`reason help`, servie par `MorpheusReasoningCli`), déjà dans sa classe de
capacité ; la répartition de l'aide générale entre classes de capacité (D2, option écartée) ; le texte de l'aide
lui-même, qui ne change pas ; le reste de la longueur de `MorpheusMain.run`, traité par
`split-the-longest-production-methods-along-their-refusals`.

## Acteurs et surfaces concernés

L'utilisateur de la CLI qui lit `morpheus help` (sortie inchangée) ; le contributeur qui ajoute une commande et sa
ligne d'aide.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| L'aide est écrite par `MorpheusCli.printHelp` puis par 113 `println` de `MorpheusMain.run` | existant vérifié | `MorpheusMain.java` l. 229-343, `MorpheusCli.java` |
| ARCH-0149 place l'aide dans `MorpheusCli` | existant vérifié | ARCH-0149, décision 2 |
| La sortie complète de `help` est figée | existant vérifié | `CliCommandTranscriptTest`, transcription `help.txt` |
| `QueryBudgets` reste dans la liste permise des classes de distribution | existant vérifié | `DispatchersHoldNoUseCaseArchitectureTest.CLI_ALLOWANCE` (MorpheusCli est une classe de `com.morpheus.cli`) |
| Aucun ADR nouveau n'est requis | décision | le changement exécute ARCH-0149 sans le modifier |

## Impact

`morpheus-cli` (deux classes de `com.morpheus.cli`) et ses tests ; `morpheus-architecture-tests` (une assertion
ajoutée : fichier de gouvernance, justification dans la description de la PR). Aucun contrat public.

Ordre du sprint 9 : ce changement précède `split-the-longest-production-methods-along-their-refusals` (constat
`AUD-QUA-35`), dont la plus longue méthode, `MorpheusMain.run`, l'est surtout par cette aide. Le sprint 9 suit le
sprint 3 (`AUD-SEC-22`), sans fichier commun avec ce changement.

## Questions ouvertes

Aucune.
