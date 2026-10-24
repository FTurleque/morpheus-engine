---
id: BE-0130
title: "Une invocation de la CLI est validée avant tout effet, et son code de sortie dit ce qui a été observé"
domain: backend
rules: true
files: ["docs/user/CLI.md", "morpheus-cli/src/main/java/com/morpheus/cli/CliExitCode.java"]
---

# Une invocation de la CLI est validée avant tout effet, et son code de sortie dit ce qui a été observé

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0103

## Context

La CLI est une surface publique scriptée : un pipeline ne lit souvent que son code de sortie, et `docs/user/CLI.md`
prescrit de lever sur tout code non nul. Plusieurs familles de parseurs coexistent dans `morpheus-cli` (`SimpleOptions`,
`MorpheusCli.CommandOptions`, des `Options` propres à un adaptateur, des parseurs fermés par `switch`, des parseurs ad hoc
et les parseurs de lancement `ApiLaunchOptions`, `McpLaunchOptions`, `RemoteApiLaunchOptions`). Une famille qui
traite une invocation fautive autrement qu'une autre fait qu'une option mal orthographiée, vide ou répétée produit un
résultat calculé sur la mauvaise population, avec le code `0`, ou un fichier de base créé avant le refus.

Le principe d'ADR-0124 (ce qui n'est pas observé est dit) s'applique au processus : une réponse ne se présente pas comme réussie quand l'appel était mal
formé, ni comme mal formé quand c'est l'état qui refuse. Un code de sortie qui contredit ce qui a été observé est
converti en succès ou en faute d'usage par tout ce qui ne lit pas le JSON.

Options écartées :

- **Un refus par site de lecture** : chaque futur site devrait s'en souvenir, ce qui est la forme même du défaut.
- **Transmettre une valeur vide telle quelle** : l'échec viendrait plus loin, sans nommer l'option, ou une chaîne vide
  serait persistée là où une absence était attendue.
- **Garder la première ou la dernière valeur d'une option répétée**, même avec un avertissement : la CLI choisirait à la
  place de l'appelant entre deux intentions contradictoires.
- **Traduire un refus d'état en code par préfixe de message** : le texte deviendrait un contrat implicite.
- **Des exceptions d'état hors de la hiérarchie `IllegalArgumentException`** : chaque `catch` HTTP et MCP (ADR-0102)
  aurait dû les ajouter, et un oubli devenait une erreur interne.
- **Ouvrir le store paresseusement** : la propriété dépendrait de l'ordre des lectures dans chaque branche.
- **Un code distinct pour `UNKNOWN`** : `4` dit déjà « résultat métier non applicable » ; un code de plus change le
  contrat public sans nécessité.

## Decision

### 1. Les chemins de commande sont déclarés une fois

Un chemin de commande est la suite de mots qui précède la première option (`portfolio list`, `policy pack create`).
`CliCommandPaths` est la seule déclaration des chemins servis. `MorpheusMain.run` refuse tout autre chemin comme commande
inconnue avant de consulter une sous-commande : une action ajoutée au `switch` d'un adaptateur sans être déclarée est
inatteignable. La colonne `cli` de `contracts/public-surfaces.tsv` et l'aide embarquée sont comparées à cette liste.

### 2. Une option inconnue est refusée

Toute famille de parseurs refuse une option qu'elle ne connaît pas, code `2`, en la nommant (`unknown option: --x`).
Un parseur ouvert, qui accepte toute clé puis la vérifie, appelle `rejectUnknown` dans le bloc qui englobe son `parse`
(ou dans chaque branche quand ce bloc est un `switch`). Un `switch` d'options a une branche `default` qui lève ; seul un
pré-parseur des options globales (`--json`, `--data-dir`, `--config-dir`, `--db`) sur le vecteur brut d'arguments
transmet un jeton qu'il ne lit pas, pour le sous-parseur de la commande. Un parseur de lancement nomme l'argument inconnu.

### 3. Une valeur vide ou blanche est refusée, avec une seule définition du blanc

Aucune option ne donne à une valeur vide un sens que l'omission n'a pas déjà. Une option passée avec une valeur vide ou
blanche est refusée, code `2`, avec le message `--x requires a non-blank value; omit the option to leave it unset`.
Une valeur est blanche si **chacun de ses points de code** est `<= ' '` (ce que `trim()` retire) ou
`Character.isWhitespace` (ce que `isBlank()` retient). Cette définition a une seule implémentation,
`OptionValue.nonBlank`, et `OptionValue.path` pour une option qui désigne un chemin ; toutes les familles l'appellent
au point d'entrée de leurs valeurs, y compris pour les options de disposition (`--data-dir`, `--config-dir`, `--db`),
dans chacune de leurs orthographes. Un parseur ouvert la vérifie après le contrôle des clés, pour qu'une option
inconnue passée vide reçoive le refus d'option inconnue. `nonBlank` rend la valeur telle que donnée. Le répertoire
courant a une orthographe explicite, `.`.

Les variables d'environnement ne sont pas concernées : une variable vide vaut une variable absente.

### 4. Une option à valeur répétée est refusée

Une option à valeur unique donnée plus d'une fois est refusée, code `2`, avec `duplicate option: --x`, avant la lecture
de sa valeur. Les parseurs à table refusent la seconde clé ; les autres passent par `OptionOccurrence.once`, qui
enregistre le **nom** de l'option, jamais le jeton : `--x v` et `--x=v` sont une seule option. `--workspace-root`, qui
nomme une liste de racines, accumule. Les drapeaux sans valeur (`--json`, `--stdio`, `--remote`) répétés sont acceptés ;
les drapeaux de commande qu'un parseur refuse en double le restent. Les commandes qui ne lisent pas la disposition la
vérifient quand même, pour que le même préfixe `morpheus --data-dir "$D"` échoue de la même façon partout.

### 5. Rien n'est créé avant que l'invocation soit acceptée

Une commande reconnaît son action et refuse une option inconnue, vide ou répétée **avant** d'ouvrir le store ; un
lanceur refuse avant d'ouvrir un port ou le transport STDIO. Les adaptateurs qui lisent leurs options par
`SimpleOptions` déclarent pour cela les options de chaque action dans une table lue avant l'ouverture
(`MorpheusPortfolioCli.ACTION_OPTIONS`, `MorpheusPolicyCli.ACTION_OPTIONS`, `MorpheusQueryCli.EXECUTE_OPTIONS`,
`VIEW_ACTION_OPTIONS`, `EXPORT_ACTION_OPTIONS`). Un refus d'usage précède donc une erreur d'ouverture du store. Les refus
qui ont besoin de la valeur (option obligatoire absente, identifiant mal formé, entier invalide, portée absente ou
donnée deux fois) peuvent venir après l'ouverture.

### 6. Le code de sortie dit ce qui a été observé

Les codes sont ceux que déclare `CliExitCode` et que publie `docs/user/CLI.md` (§19) :

| Code | Nom | Sens |
|---:|---|---|
| 0 | `SUCCESS` | commande réussie |
| 2 | `USAGE` | appel mal formé, et lui seul |
| 3 | `NOT_FOUND` | un identifiant passé en argument ne désigne rien |
| 4 | `STATE_ERROR` | ce qui est désigné existe, mais la relation ou l'état exigé est refusé ; résultat métier non applicable |
| 5 | `IO_ERROR` | erreur d'entrée-sortie classifiée |
| 10 | `INTERNAL_ERROR` | erreur inattendue |

Les services et les stores mémoire et SQLite lèvent `EntityNotFoundException` (`3`) ou `EntityStateException` (`4`),
déclarées dans `com.morpheus.application.store`. Elles restent des `IllegalArgumentException`, donc HTTP et MCP les
traitent comme toute autre `IllegalArgumentException` ; les adaptateurs CLI les interceptent **avant** `IllegalArgumentException`.

Une décision de policy atteint le code de sortie : `policy evaluate` et `policy dry-run` rendent `0` pour `PASS` et
`WARN`, `4` pour `BLOCK` et `UNKNOWN`, par la seule méthode `MorpheusPolicyCli.exitCodeOf`, et impriment toujours la
décision. Une observation partielle n'est pas un refus : `analyze-change` rend `0` même quand sa traversée est tronquée.

### 7. La sortie texte dit ce que dit la sortie machine

La sortie texte ne compte pas sans nommer ce qu'elle compte. `analyze-change` ajoute en texte une ligne
`warningCodes=…` (un code par avertissement, dans l'ordre du résultat) et une ligne `truncationReason=…` par raison
distincte portée par un avertissement. La sélection se fait sur la présence du détail
`ChangeAnalysisWarning.TRUNCATION_REASON`, constante que le service et la CLI partagent, pas sur une liste de codes.

### 8. L'aide embarquée est exécutée contre son parseur

Aucune invocation que l'aide documente n'est refusée comme erreur d'usage. Les lignes d'usage de `morpheus help` et de
`morpheus reason help` sont développées en invocations — groupes facultatifs retirés, chaque branche d'une alternative
gardée, marqueurs remplis — et exécutées, chacune contre son propre store vide ; les lignes qui démarrent un serveur
sont confiées à leur parseur de lancement. Un marqueur que la garde ne sait pas remplir la fait échouer.

## Do's and Don'ts

### Do

- Déclarer tout nouveau chemin de commande dans `CliCommandPaths` et dans la colonne `cli` du manifeste.
- Passer toute valeur d'option par `OptionValue.nonBlank` (ou `OptionValue.path`) et toute option à valeur d'un parseur
  à variables par `OptionOccurrence.once`.
- Ajouter l'action et ses options à la table de l'adaptateur avant le `case` qui l'exécute.
- Lever `EntityNotFoundException` ou `EntityStateException` pour un refus qui porte sur l'état.
- Rendre `4` pour une décision `BLOCK` ou `UNKNOWN`, et imprimer la décision quand même.
- Écrire toute nouvelle ligne d'aide sous une forme que son parseur accepte.

### Don't

- Ne pas accepter une option inconnue, vide, blanche ou répétée, ni lui substituer une valeur par défaut.
- Ne pas écrire un second test du blanc dans `morpheus-cli`.
- Ne pas ouvrir le store, un port ou le transport STDIO avant d'avoir accepté action et options.
- Ne pas rendre `2` pour un refus d'état, ni traduire un code à partir du texte d'un message.
- Ne pas convertir `UNKNOWN` en `0`, ni faire échouer une observation seulement tronquée.
- Ne pas imprimer en texte un compteur que la sortie JSON détaille sans en nommer le contenu.

## Consequences

### Positives

- Un script reçoit le même refus, avec le même code et un message qui nomme l'option, quelle que soit la commande.
- Une invocation fautive ne laisse rien sur disque.
- Les codes `2`, `3` et `4` distinguent l'appel mal formé, l'entité absente et l'état refusé.
- L'aide ne peut pas documenter une invocation que la CLI refuse.

### Négatives

- Une valeur faite uniquement de blancs Unicode ne désigne pas un fichier de ce nom : il s'écrit `./…`.
- Chaque action est écrite deux fois, comme clé de table et comme `case` ; une clé sans `case` reproduirait le défaut.
- La garde de l'aide ne passe jamais les options facultatives et n'exerce que la première valeur d'une alternative de
  valeur.

### Risques

- `projects`, `changes` et `change-orchestration` ouvrent encore le store avant de refuser une partie de leurs options.
- Aucune garde ne découvre un futur parseur qui omettrait `OptionValue` ou `OptionOccurrence` : la preuve repose sur des
  tables d'invocations écrites à la main.
- Les caractères invisibles qui ne sont blancs pour aucune des deux lectures (espace insécable, `U+200B`, `U+FEFF`)
  restent des valeurs ; MCP et HTTP ont leur propre définition du blanc.
- Certains refus sur l'état rendent encore `2` (budgets de packs, d'overrides, de règles et d'identités ; une
  observation de fraîcheur qui recule), et `MorpheusServerCli` rend `10` pour une exception d'exécution que les autres
  adaptateurs classent en `4`.
- Une liste faite de séparateurs (`--providers ","`) passe le refus du blanc et vaut l'omission.

## Compliance and Enforcement

- Règle Archgate `BE-0130-contrat-des-options-de-la-cli.rules.ts` : `user-guide-exit-codes-match-the-enum`.
- `morpheus-cli/src/test/java/com/morpheus/cli/CliCommandPathsTest.java` et
  `morpheus-architecture-tests/src/test/java/com/morpheus/cli/PublicSurfaceManifestCoversEveryServedCliCommandTest.java` :
  chemins déclarés, servis, documentés par l'aide et portés par le manifeste.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/CliOptionParsingRefusesUnknownOptionsTest.java` :
  découvre les familles de parseurs par leur forme et exige le refus d'une option inconnue.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusMainTest.java` (`eachLaunchParserNamesAnUnknownArgument`) :
  refus nommé par les trois parseurs de lancement.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/CliOptionValueHasOneBlankDefinitionTest.java` :
  un seul test du blanc dans `morpheus-cli`, par point de code et avec ses deux moitiés.
- `morpheus-cli/src/test/java/com/morpheus/cli/OptionValueTest.java`, `SimpleOptionsTest.java`,
  `BlankOptionValueRefusalTest.java` : refus du vide et du blanc sur chaque option de chaque parseur, rien d'écrit.
- `morpheus-cli/src/test/java/com/morpheus/cli/DuplicateOptionRefusalTest.java` : refus de la répétition dans toutes les
  orthographes, accumulation de `--workspace-root`.
- `morpheus-cli/src/test/java/com/morpheus/cli/NothingIsCreatedBeforeTheOptionsAreAcceptedTest.java` : `policy`,
  `query`, `views`, `export` et `portfolio` refusent avant d'ouvrir le store ; tables comparées à une liste écrite à part.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusStateRefusalExitCodeTest.java` : codes `3` et `4` des refus
  d'état, maintien de `2` pour un appel mal formé.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusPolicyCliTest.java` : code de sortie des quatre décisions.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusCliTest.java`
  (`aTruncatedAnalysisNamesItsTruncationInTextAsInJsonAndStillSucceeds`) : troncature dite en texte, code `0`.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusHelpInvocationsParseTest.java` : chaque invocation documentée
  par l'aide est acceptée par son parseur.

## References

- [ADR-0059](GEN-0059-stable-local-cli-contract.md) — contrat CLI local stable
- [ADR-0102](BE-0102-mcp-failure-contract-is-one-rule.md) — contrat d'échec MCP
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — ce qui n'est pas observé est dit
- [ADR-0093](ARCH-0093-provider-neutral-policy-packs-governance-automation.md) — policy packs, `UNKNOWN` jamais `BLOCKED`
- [Référence de la CLI](../../user/CLI.md)
- `contracts/public-surfaces.tsv`
