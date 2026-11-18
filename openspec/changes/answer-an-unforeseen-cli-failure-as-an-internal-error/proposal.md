# Proposition : un échec imprévu de la CLI sort en 10 et est compté (AUD-QUA-31)

## Why

`CliExitCode` se présente comme « Stable process exit codes for scriptable MORPHEUS CLI consumers », et la table des
codes de sortie de `docs/user/CLI.md` (§ 19) réserve `10` (`INTERNAL_ERROR`) à « erreur inattendue » et `4`
(`STATE_ERROR`) à un état incompatible. Le socle commun le respecte : le dernier `catch (RuntimeException)` de
`CliCommandRun.run` rend `INTERNAL_ERROR`, comme celui de `MorpheusServerCli`. Mais treize classes de capacité de
`com.morpheus.cli.command` terminent par `catch (RuntimeException failure) { return CliErrorLine.refuse(args, err,
CliExitCode.STATE_ERROR, failure); }` : `MorpheusAcceptanceCriteriaCli`, `MorpheusAugmentedContextCli`,
`MorpheusCompositionCli`, `MorpheusConstraintSemanticsCli`, `MorpheusControlledLifecycleCli`,
`MorpheusExternalIntegrationCli`, `MorpheusJarvisOrchestrationCli`, `MorpheusPolicyCli`, `MorpheusPortfolioCli`,
`MorpheusProductCli`, `MorpheusProviderPluginCli`, `MorpheusQueryCli`, `MorpheusReasoningCli`. Un
`NullPointerException` sous `portfolio`, `policy` ou `query` est donc annoncé comme un conflit d'état, quand le même
défaut sous `projects` sort en `10`.

Aucun transport de la CLI ne compte ces défauts, alors que le HTTP compte les siens
(`UnforeseenFailureTrace.record` → famille `http-internal-error`) : un défaut interne de la CLI ne laisse ni compteur
ni événement dans le journal opérationnel.

Preuve : constat `AUD-QUA-31` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd` ; revérifié sur `1a539c22` le 17/11/2026 (treize `catch` aux lignes citées, inchangées).
Le sprint 2 de l'audit place ce signal avant les corrections des sprints 3 à 6, qui touchent ces commandes.

## What Changes

- Les treize classes de capacité classent un échec comme le socle commun : un échec qu'aucun de leurs refus ne
  classe sort avec le code `10` et sa ligne d'erreur ; un refus d'état (`IllegalStateException`,
  `KnowledgeStoreException`, refus nommés existants) garde le code `4`.
- La classification d'un échec en code de sortie est écrite une fois, dans le cadre d'exécution de la CLI ; les
  classes de capacité y délèguent leur dernier `catch`.
- Un échec rendu en `10` est compté sur le recorder du processus sous une famille nommée `cli-internal-error`, sur
  toutes les commandes, celles de `CliCommandRun` comprises.
- Surface CLI : le code de sortie d'un défaut interne de ces treize familles passe de `4` à `10`. **BREAKING** pour un
  script qui traiterait ce `4` comme un refus d'état réessayable ; conforme à la table publiée de `docs/user/CLI.md`.
- Aucune route HTTP, aucun outil MCP, aucune ligne de `contracts/public-surfaces.tsv`, aucun format persisté ne change.

## Capabilities

### New Capabilities

### Modified Capabilities
- `cli-argument-base` : un échec qu'aucune commande ne prévoit sort avec le code d'erreur interne sur toutes les
  familles ; la phrase « The exit codes MUST NOT change » de la première exigence est précisée pour ne plus interdire
  cette correction.
- `suppressed-failure-trace` : un échec imprévu d'une commande CLI est compté sous une famille nommée.

## Périmètre et exclusions

**Inclus :** les treize classes de capacité citées, `CliCommandRun` (classification et comptage), `MorpheusServerCli`,
`MorpheusPathsCli` et `MorpheusCli` (comptage de leur `10`), `SuppressedFailureFamily` (famille ajoutée), leurs tests,
`docs/developer/OPERABILITY.md` (table des familles), `docs/user/CLI.md` (§ 19), les notes de version de la version
courante, les pages durables des deux capacités.

**Exclus :** les lanceurs `api`, `mcp` et `api --remote` de `MorpheusMain`, dont l'échec de démarrage rend déjà `10` ;
le serveur MCP, qui a son propre contrat d'échec (`mcp-tool-failure-contract`) ; le code `5` (`IO_ERROR`), qu'aucune
de ces classes n'emploie aujourd'hui ; l'ordre des `catch` de `CliCommandRun` pour les sous-types
d'`IllegalArgumentException` (`EntityNotFoundException`, `EntityStateException`), hors constat.

## Acteurs et surfaces concernés

Le script ou l'agent qui lit le code de sortie de la CLI ; l'opérateur qui lit le journal opérationnel d'un processus
CLI (mode JSON sur la sortie d'erreur ou fichier).

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Treize familles rendent `4` pour tout `RuntimeException` non classé | existant vérifié | `grep -rn "catch (RuntimeException" morpheus-cli/src/main/java`, lignes citées dans le Why |
| Le socle commun et `server` rendent `10` | existant vérifié | `CliCommandRun.java:78-80`, `MorpheusServerCli.java:92-93` |
| `10` signifie « erreur inattendue » pour l'utilisateur | existant vérifié | `docs/user/CLI.md` § 19 |
| Le HTTP compte ses défauts internes, la CLI non | existant vérifié | `UnforeseenFailureTrace`, `SuppressedFailureFamily.HTTP_INTERNAL_ERROR` |
| Les valeurs numériques de `CliExitCode` ne changent pas | exigence | `GEN-0059`, Do's and Don'ts |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : correction d'une classification déjà publiée, couverte par `GEN-0059` |

## Impact

`morpheus-cli` (`com.morpheus.cli.command`, `com.morpheus.cli`), `morpheus-application`
(`com.morpheus.application.operability`). Tests de `morpheus-cli` et `morpheus-application` ; un interdit textuel
ajouté sous `morpheus-architecture-tests` (fichier de gouvernance, justification de PR). `GEN-0059` reste la décision
en vigueur. Aucun contrat de `contracts/public-surfaces.tsv` ni de l'OpenAPI.

Aucune dépendance stricte avec les autres constats du sprint 2. Les changements des sprints 3 à 6 qui touchent ces
commandes héritent de la classification.

## Questions ouvertes

Aucune.
