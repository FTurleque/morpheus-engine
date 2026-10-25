---
id: ARCH-0149
title: "Une commande CLI et un outil MCP sont servis par une classe de capacité ; un dispatcher ne tient aucun cas d'usage"
domain: architecture
rules: false
---

# Une commande CLI et un outil MCP sont servis par une classe de capacité ; un dispatcher ne tient aucun cas d'usage

- Statut : **Acceptée**
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

Options considérées pour dire où vit un comportement : une borne sur le nombre de méthodes de commande d'une classe
(rejetée : la borne ne se justifie pas, et découper une méthode la satisfait), une règle sur les noms
(`MorpheusCli` ne dépend que de classes `Morpheus*Cli` : rejetée, un nom tient lieu de rôle), une règle de dépendance
sur des paquets (retenue).

Ce que distribuer exige : pour la CLI, le runtime opérationnel que le lanceur transmet, la version du produit et
la borne que l'aide imprime ; pour le serveur MCP, le recorder, la version du produit et les types d'intégration qu'il
passe aux classes de capacité. `MorpheusCli` sert aussi `version` quand il est appelé directement, et son propre point
d'entrée (`MorpheusCli.main`, ADR-0115) remet le runtime du processus à `MorpheusMain.run`. Le propriétaire d'un
outil MCP se lit par la classe de la lambda de son gestionnaire : chaque classe déclare cette lambda dans une méthode
privée de quelques lignes.

## Decision

### 1. Un rôle de l'adaptateur est un paquet

| Paquet | Rôle |
|---|---|
| `com.morpheus.cli` | lanceur, dispatcher, chemins de commande |
| `com.morpheus.cli.launch` | câblage des serveurs que le lanceur démarre |
| `com.morpheus.cli.command` | classes de capacité de la CLI et ce qu'elles partagent |
| `com.morpheus.mcp.server` | le serveur, qui enregistre les outils |
| `com.morpheus.mcp` | classes de capacité du serveur et ce qu'elles partagent |

Les paquets sont sans cycle (`cli` au-dessus de `launch`, `launch` au-dessus de `command` ; `mcp.server` au-dessus
de `mcp`) ; les autres modules n'utilisent de `morpheus-mcp` que `MorpheusMcpServer`.

### 2. Une commande et un outil sont servis par une classe de capacité

Chaque chemin de `CliCommandPaths` est revendiqué par une seule classe de la table de `MorpheusMain.run` ; chaque
outil publié a pour gestionnaire une lambda déclarée par une seule classe de `com.morpheus.mcp`. Une classe sert une
famille de `contracts/public-surfaces.tsv`. L'aide et `unknown command` restent à `MorpheusCli`, branche par défaut.

### 3. Un dispatcher ne tient aucun cas d'usage

Une classe de `com.morpheus.cli` ou de `com.morpheus.mcp.server` ne nomme de l'application, d'un provider ou d'un
store que la liste autorisée de son adaptateur, exacte, réduite à ce que distribuer exige.

### 4. Ce que les familles partagent est écrit une fois

`CommandOptions`, `CliCommandRun` et `CliFailure` dans `com.morpheus.cli.command` ; `McpContentPayloads` et
`McpCatalogTool` dans `com.morpheus.mcp`.

## Do's and Don'ts

### Do

- Ajouter une commande dans une classe de `com.morpheus.cli.command`, déclarer son chemin dans `CliCommandPaths` et
  sa ligne d'aide, et ajouter sa classe à la table de `MorpheusMain.run`.
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

### Risques

- Une commande ajoutée à `MorpheusCli` sans type de l'application passerait la règle de dépendance ; elle est refusée
  par la liste des chemins que `MorpheusCli` sert (`CliCommandDispatchIsExhaustiveTest`), réduite à l'aide.

## Compliance and Enforcement

- `DispatchersHoldNoUseCaseArchitectureTest` : bytecode et sources des dispatchers, une liste autorisée exacte par
  adaptateur, import joker et nom inutilisé refusés. Vue en échec : une commande écrite dans `MorpheusMain`, un outil écrit dans le
  serveur, une constante de l'application lue par son nom qualifié (vue par le scan des sources seul), une allocation
  inutilisée.
- `CliCommandDispatchIsExhaustiveTest` : chaque chemin déclaré revendiqué par une seule classe de la table, lue dans le
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
