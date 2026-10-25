---
id: ARCH-0149
title: "Une commande CLI et un outil MCP sont servis par une classe de capacité ; un dispatcher ne tient aucun cas d'usage"
domain: architecture
rules: false
---

# Une commande CLI et un outil MCP sont servis par une classe de capacité ; un dispatcher ne tient aucun cas d'usage

- Statut : **Proposée**
- Date : 2026-10-25

## Context

`morpheus-cli` et `morpheus-mcp` ont chacun une classe qui distribue les appels : `MorpheusMain` demande à une chaîne
de classes si elles prennent en charge les arguments, `MorpheusMcpServer` enregistre les outils que lui fournissent des
classes. Une commande ou un outil servi par la classe qui distribue est un comportement que rien ne range : la classe
devient l'endroit où une commande ou un outil arrive sans que personne le décide (AUD-ARC-09). Le contenu de leurs réponses est une surface publique (texte, JSON, codes de sortie,
charges utiles) que les transports doivent garder identique quand le code qui les sert change de classe.

Les contraintes, mesurées sur le code :

- `MorpheusMain` est la classe principale que nomment la distribution (`jpackage`) et le JAR ombré : elle ne quitte pas
  `com.morpheus.cli` ;
- `MorpheusMcpRuntime` est une racine de composition nommée d'ADR-0109 : elle ne change pas de nom, donc reste dans
  `com.morpheus.mcp` ;
- `projects`, `sync` et `analyze-change` construisent eux-mêmes le lecteur OpenSpec, que le changement
  `ingest-a-project-through-the-provider-port` remplace par le port d'ingestion.

Options considérées pour dire où vit un comportement : une borne sur le nombre de méthodes de commande d'une classe
(rejetée : la borne ne se justifie pas, et découper une méthode la satisfait), une règle sur les noms
(`MorpheusCli` ne dépend que de classes `Morpheus*Cli` : rejetée, un nom tient lieu de rôle), une règle de dépendance
sur des paquets (retenue).

## Decision

### 1. Un rôle de l'adaptateur est un paquet

| Paquet | Rôle |
|---|---|
| `com.morpheus.cli` | le lanceur, le dispatcher et la déclaration des chemins de commande |
| `com.morpheus.cli.launch` | le câblage des serveurs que le lanceur démarre |
| `com.morpheus.cli.command` | les classes de capacité de la CLI et ce qu'elles partagent : options, cadre d'exécution, runtime, disposition, codes de sortie |
| `com.morpheus.mcp.server` | le serveur, qui enregistre les outils |
| `com.morpheus.mcp` | les classes de capacité du serveur, leur runtime et ce qu'elles partagent |

Les paquets de chaque adaptateur sont sans cycle : `com.morpheus.cli` au-dessus de `launch`, `launch` au-dessus de
`command` ; `com.morpheus.mcp.server` au-dessus de `com.morpheus.mcp`. Les autres modules n'utilisent de
`morpheus-mcp` que `MorpheusMcpServer`.

### 2. Une commande et un outil sont servis par une classe de capacité

Chaque chemin de commande déclaré par `CliCommandPaths` est revendiqué par exactement une classe de la chaîne de
`MorpheusMain.run` ; chaque outil publié a pour gestionnaire une lambda déclarée par exactement une classe de
`com.morpheus.mcp`. Une classe de capacité sert les commandes ou les outils d'une famille de
`contracts/public-surfaces.tsv` (`requirement.*`, `change.*`, `specification.*`, …). `help` et la réponse
`unknown command` restent au dispatcher.

### 3. Un dispatcher ne tient aucun cas d'usage

Une classe de `com.morpheus.cli` ou de `com.morpheus.mcp.server` ne nomme de `com.morpheus.application..`,
`com.morpheus.provider..` et `com.morpheus.store..` que les types de la liste autorisée de son adaptateur : pour la
CLI, le runtime opérationnel que le lanceur transmet, la version du produit et la borne que l'aide imprime ; pour le
serveur MCP, le recorder, la version du produit et les types d'intégration qu'il passe aux classes de capacité. Chaque
liste est exacte ; un nom qu'aucun dispatcher de l'adaptateur n'utilise en sort. Une commande écrite dans un
dispatcher n'a donc rien à appeler.

`MorpheusCli` sert encore `projects`, `sync` et `analyze-change` jusqu'à ce que leur ingestion passe par le port du
provider : les types qu'ils utilisent, `OpenSpecProjectContentReader` compris, forment une dette nommée, qui ne fait
que rétrécir. `MorpheusCli` sert aussi `version` à son propre point d'entrée (`MorpheusCli.main`, ADR-0115) ; depuis
`MorpheusMain`, c'est `MorpheusProductCli` qui le sert.

### 4. Ce que les familles partagent est écrit une fois

Le parseur d'options (`CommandOptions`), le cadre d'exécution, la correspondance des échecs et la ligne d'erreur
(`CliCommandRun`, `CliFailure`) des commandes extraites de `MorpheusCli` sont des collaborateurs de
`com.morpheus.cli.command`. Les projections des outils du catalogue (`McpContentPayloads`), leur cadre d'appel et leurs
lectures communes (`McpCatalogTool`) sont des collaborateurs de `com.morpheus.mcp`. Chaque classe MCP déclare elle-même la lambda du gestionnaire de ses outils, dans une méthode
privée de quelques lignes : c'est par elle que se lit le propriétaire d'un outil.

## Do's and Don'ts

### Do

- Ajouter une commande dans une classe de `com.morpheus.cli.command`, déclarer son chemin dans `CliCommandPaths` et
  sa ligne d'aide, et la brancher dans la chaîne de `MorpheusMain.run`.
- Ajouter un outil dans une classe de `com.morpheus.mcp`, dont la lambda du gestionnaire est déclarée par cette classe.
- Ranger une commande ou un outil avec ceux de sa famille du manifeste.
- Faire passer un module voisin par `MorpheusMcpServer`.

### Don't

- Ne pas écrire de commande dans `MorpheusMain`, `MorpheusCli` ou `CliCommandPaths`, ni d'outil dans
  `MorpheusMcpServer`.
- Ne pas ajouter de type à la liste autorisée d'un dispatcher pour servir une commande ou un outil.
- Ne pas recopier dans une classe de capacité le parseur, le cadre d'exécution ou une projection qu'une autre utilise
  (tenu par la revue).
- Ne pas faire dépendre `com.morpheus.cli.command` de `com.morpheus.cli`, ni `com.morpheus.mcp` de
  `com.morpheus.mcp.server`.

## Consequences

### Positives

- Une commande ou un outil arrive dans une classe que sa famille désigne ; le dispatcher ne grossit pas.
- La sortie de chaque commande et la réponse de chaque outil sont fixées par une transcription, quelle que soit la
  classe qui les sert.
- Les changements qui remplacent le parseur de la CLI ou les projections MCP les trouvent chacun en un seul endroit.

### Négatives

- Les classes de capacité sont publiques pour leur dispatcher ; la frontière avec les autres modules est tenue par une
  règle (`McpEntryTypesArchitectureTest`), plus par le compilateur.
- `MorpheusCli` garde trois commandes et leur dette tant que l'ingestion n'est pas recâblée.

### Risques

- Une commande ajoutée à `MorpheusCli` avec les types de sa dette passerait la règle de dépendance ; elle est refusée
  par la liste des chemins que `MorpheusCli` sert (`CliCommandDispatchIsExhaustiveTest`).

## Compliance and Enforcement

- `DispatchersHoldNoUseCaseArchitectureTest` : bytecode et sources des dispatchers, une liste autorisée exacte par
  adaptateur, dette de `MorpheusCli`, import joker et nom inutilisé refusés. Vue en échec : une commande écrite dans `MorpheusMain`, un outil écrit dans le
  serveur, une constante de l'application lue par son nom qualifié (vue par le scan des sources seul), une allocation
  inutilisée.
- `CliCommandDispatchIsExhaustiveTest` : chaque chemin déclaré revendiqué par une seule classe de la chaîne, lue dans le
  bytecode de `MorpheusMain.run`. Vue en échec : un chemin sans classe, un chemin revendiqué par deux.
- `ServedToolsAreClaimedOnceTest` (`morpheus-mcp`) : un outil publié une fois, servi par une classe hors du paquet du
  serveur. Vue en échec : un outil publié deux fois, un outil du catalogue servi par le serveur.
- `CliAndMcpPackageCyclesArchitectureTest` et `McpEntryTypesArchitectureTest` : l'ordre des paquets et la frontière du
  module MCP.
- `CliCommandTranscriptTest` (`morpheus-cli`) et `McpToolTranscriptTest` (`morpheus-mcp`) : la sortie, les codes de
  sortie et les refus des commandes et des outils, et le catalogue publié.

## References

- [ADR-0147](ARCH-0147-un-role-d-adaptateur-est-un-paquet.md) — un rôle de l'adaptateur HTTP est un paquet.
- [ADR-0109](ARCH-0109-transport-adapters-are-named-composition-roots.md) — racines de composition nommées.
- [ADR-0130](BE-0130-contrat-des-options-de-la-cli.md) — contrat des options de la CLI.
- [ADR-0115](ARCH-0115-the-operational-runtime-is-a-value-the-launcher-hands-on.md) — les deux points d'entrée de
  la CLI.
