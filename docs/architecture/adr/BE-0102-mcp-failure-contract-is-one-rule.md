---
id: BE-0102
title: "Le contrat d'échec MCP est une règle unique, pas une convention par outil"
domain: backend
rules: false
---

# Le contrat d'échec MCP est une règle unique, pas une convention par outil

- Statut : **Acceptée**
- Date : 2026-09-09

## Context

`contracts/public-surfaces.tsv` revendique la convergence CLI / MCP / HTTP, et MCP est la surface des IDE, agents et
orchestrateurs. Le destinataire d'un échec MCP est un agent qui doit réparer son appel. Sans règle unique, chaque
classe d'outils de `morpheus-mcp` écrit sa propre lecture d'arguments et son propre mapping d'exceptions, et trois axes
dérivent sans que rien ne le voie :

- **le message** : la même faute (argument requis absent) reçoit une phrase différente selon l'outil ;
- **les exceptions mappées** : la même exception devient, selon la classe, un `CallToolResult` en erreur ou une
  exception non mappée qui s'échappe du handler ;
- **les arguments inconnus** : chaque schéma publie `additionalProperties: false`, sans que l'on sache qui fait
  respecter cette promesse.

Fait mesuré qui fonde la décision 4 : `MorpheusMcpServer.build` construit le serveur avec `.validateToolInputs(true)`.
Le SDK appelle alors son validateur de schéma (`DefaultJsonSchemaValidator`, networknt, dialecte draft 2020-12)
**avant** le handler, et rend son propre résultat d'erreur sans jamais atteindre celui-ci. Ce validateur applique
`additionalProperties`, `required`, `enum`, `minLength`, `minimum` et `maximum`, y compris sur les sous-schémas
imbriqués.

Options écartées :

- **Faire lever le service quand il rend un refus comme valeur** : changerait le contrat d'`application` et les
  sorties CLI et HTTP, qui lisent l'état ; le défaut porte sur la traduction MCP, pas sur le modèle.
- **Deux copies de la partition succès/refus (MCP et CLI) gardées égales par un test** : une garde entre deux copies ne
  se justifie que là où la copie ne peut pas disparaître ; ici elle le peut.
- **Un `default -> false` ou un `Set` d'états de succès** : un état futur y serait classé sans que personne ne l'ait
  décidé, la conversion silencieuse qu'interdit ADR-0093.
- **Ignorer un paramètre déclaré en corrigeant seulement la description** : un client qui borne obtiendrait tout.
- **Convertir le type de l'échec d'ouverture du store** : aucun défaut observable ne le justifie, puisque le chemin
  d'ouverture MCP le convertit déjà (décision 7).

## Decision

### 1. Un format de message canonique

Le message nomme l'**argument fautif en premier**, puis ce qui était attendu, et distingue une **omission** d'une
**valeur malformée**, parce que la réparation n'est pas la même :

```text
absent                          <nom> is required and must be <attendu>
présent mais inutilisable       <nom> must be <attendu>
optionnel présent, inutilisable <nom> must be <attendu> when present
```

où `<attendu>` est l'un de : `a non-blank string` · `an integer` · `an integer between <min> and <max>` ·
`a boolean` · `a finite number`.

C'est le format vers lequel convergent les deux autres surfaces : le CLI dit `--<key> must be an integer`,
l'adaptateur HTTP `<name> must be an integer` (`MorpheusHttpQuery`). La forme requise est celle des outils du
catalogue (`McpCatalogTool` et les classes qui servent ces outils). La forme `missing required MCP argument: <nom>` est écartée : elle ne dit pas à
quoi ressemble une valeur valide et place le nom en fin de phrase.

`McpArguments` est le seul endroit du paquet où ce format est écrit, et le seul qui lit un argument d'outil.

### 2. L'ensemble d'exceptions mappées est dérivé de ce que le handler peut lever

| Ce que le handler fait | Ce qu'il mappe |
|---|---|
| lit des arguments | `IllegalArgumentException` |
| ouvre un `MorpheusMcpRuntime` ou touche un store | `+ KnowledgeStoreException` |
| délègue à un service qui refuse un état (policy, query DSL, saved views) | `+ IllegalStateException` |
| compose des policies | `+ PolicyConflictException` |

Un handler ne déclare pas une exception qu'il ne peut pas atteindre, et n'en omet pas une qu'il peut atteindre.
`MorpheusReasoningMcpTools` n'ouvre aucun store : il ne mappe pas `KnowledgeStoreException`. `MorpheusProductMcpTools`
ne lit aucun argument et n'ouvre rien.

### 3. `MorpheusProviderPluginMcpTools` est une frontière de rédaction

Ce handler garde un `catch (RuntimeException)`. Ce n'est pas un filet trop large : il ne renvoie jamais le message de
l'exception, mais le code stable `PROVIDER_PLUGIN_DISCOVERY_FAILED`, parce que les exceptions de système de fichiers
rendent souvent leur pathname comme message et que cette surface est model-facing. Élargir le `catch` garantit
qu'aucun message arbitraire ne sorte. Le commentaire qui le motive reste dans le code. Chaque refus de ce fichier porte
le code stable et passe par `McpToolFailure.refusal`.

### 4. Les arguments inconnus sont refusés avant le handler

La promesse `additionalProperties: false` est tenue par le validateur du SDK, armé par `.validateToolInputs(true)` dans
`MorpheusMcpServer`, et non par une copie dans chaque handler. Le drapeau est épinglé par un test : le retirer casse le
build au lieu de désarmer silencieusement les schémas.

Le message d'un argument inconnu est produit par le SDK, pas par MORPHEUS. Il est localisé par la bibliothèque networknt
selon la locale par défaut de la JVM. Le format de la décision 1 ne gouverne que les messages que MORPHEUS écrit
lui-même ; les tests assertent le refus, ni le texte du SDK ni sa langue.

### 5. Un refus rendu comme valeur se lit comme un refus

Un service peut rendre son refus dans un résultat au lieu de lever : `ControlledChangeLifecycleMutationService.apply`
rend `CONFLICT`, `NOT_AUTHORIZED`, `REQUIRES_CONFIRMATION` ou `REJECTED`. Pour ces outils, `isError` se décide sur l'état
du résultat :

- succès : `APPLIED` et `ALREADY_APPLIED` ; tout le reste est une erreur ;
- le corps ne change pas : l'état, la raison, `lifecycleState` et `audit` restent présents. Le drapeau ajoute un fait,
  il n'en retire aucun ;
- la partition s'écrit à un seul endroit, `ChangeLifecycleMutationResultState.successful()`, un `switch` qui nomme
  chaque constante **sans `default`** : un nouvel état ne compile pas tant que personne n'a décidé de son côté. Le CLI
  (code de sortie), MCP (`isError`) et l'invariant de `ChangeLifecycleMutationResult` (seul un succès porte un
  enregistrement d'audit) la consomment.

`McpToolFailure` est le **seul** endroit du module qui pose `isError(true)` : `refusal(String body)` construit le
résultat d'erreur à partir d'un corps déjà structuré, `result(RuntimeException)` y délègue avec le message de
l'exception. Tout autre résultat d'outil écrit lui-même son `isError`, au littéral `false`.

Un verdict n'est pas un refus : `evaluate_change_transition` rend le verdict d'une évaluation (`ALLOWED`,
`BLOCKED`…), `evaluate_policies` et `dry_run_policy_pack` rendent une décision (`BLOCK` et `UNKNOWN` comprises), et le
statut `FAILED` d'un adaptateur de raisonnement est une exécution partielle déclarée ; tous rendent `isError(false)`.
Un statut d'intégration, un `resolutionState` ou un conflit de composition sont des observations déclarées dans la
réponse (ADR-0124), pas le refus d'une opération.

### 6. Un schéma d'entrée ne déclare que ce que le handler honore

Un paramètre publié est honoré ou refusé, jamais accepté puis ignoré. L'export en est l'application : son contrat
— complet sur les trois surfaces, paramètre de page refusé, limite du cadre STDIO MCP et ses recours — est décidé par
ADR-0092.

### 7. L'échec d'ouverture du store est un refus

Aucun handler MCP n'ouvre de `SqliteConnectionScope` ; chaque store qu'il ouvre passe par `SqliteStoreConnection.open`,
qui convertit toute `RuntimeException` (refus du bail de maintenance, échec du pilote) en `KnowledgeStoreException`.
La règle de la décision 2 suffit donc : tout handler qui atteint un store répond par un refus `isError`, sans chaîne
d'exception ni chemin absolu.

Ce contrat ne dit rien de la **sémantique** des échecs métier : `UNKNOWN` n'est pas `BLOCKED` (ADR-0093), un conflit de
composition reste explicite, et un résultat en erreur reste un refus, jamais une dégradation silencieuse.

## Do's and Don'ts

### Do

- Lire tout argument d'outil par `McpArguments`, et produire les messages au format de la décision 1.
- Mapper exactement les exceptions que le handler peut atteindre, selon le tableau de la décision 2.
- Construire tout résultat d'erreur par `McpToolFailure` ; écrire `isError(false)` explicitement sur tout autre
  résultat.
- Décider `isError` d'une mutation de cycle de vie par `ChangeLifecycleMutationResultState.successful()`, en gardant le
  corps inchangé.
- Garder `.validateToolInputs(true)` dans `MorpheusMcpServer`.
- Rendre par le code stable `PROVIDER_PLUGIN_DISCOVERY_FAILED` tout échec de `MorpheusProviderPluginMcpTools`.

### Don't

- Ne pas écrire de copie privée de `requiredString` ou d'un autre lecteur d'argument dans une classe d'outils.
- Ne pas poser `isError(true)` hors de `McpToolFailure`, ni laisser un résultat sortir sans avoir décidé son `isError`.
- Ne pas classer un état de résultat par `default` ou par ensemble implicite.
- Ne pas recopier dans un handler le refus des arguments inconnus que le SDK applique.
- Ne pas publier dans un schéma un paramètre que le handler ignore.
- Ne pas relayer le message d'une exception depuis `MorpheusProviderPluginMcpTools`.
- Ne pas asserter le texte ni la langue du message de validation du SDK.

## Consequences

### Positives

- Une même faute reçoit le même message sur tous les outils, et la même forme sur les trois surfaces.
- Un outil ajouté sans mapping d'erreur, une copie privée d'un lecteur d'argument, un `catch` qui oublie
  `KnowledgeStoreException` ou un résultat sans `isError` décidé font échouer le build.
- Un agent qui lit `isError`, comme le protocole l'y invite, voit un refus de mutation comme un refus.

### Négatives

- Le message d'un argument inconnu échappe au format canonique : il est celui du SDK, localisé.

### Risques

- Les garde-fous statiques ne savent pas si un **corps** porte un refus : un handler qui répond `isError(false)` autour
  d'un état `BLOCKED` les satisfait. Cette propriété se teste outil par outil, là où l'état existe.
- La garde « un paramètre déclaré est honoré ou refusé » est tenue par des tests de bout en bout écrits pour l'export
  (ADR-0092) ; elle ne couvre pas mécaniquement le prochain outil.
- Le sens de `isError` pour un verdict n'est pas fixé : `POST /api/v1/projects/{projectId}/changes/{changeId}/lifecycle-transitions`
  répond `200` avec l'état de refus dans le corps, `NOT_AUTHORIZED` compris ; et `evaluate_policies` /
  `dry_run_policy_pack` rendent `isError(false)` pour `BLOCK` et `UNKNOWN`, là où `policy evaluate` et
  `policy dry-run` rendent un code de sortie d'échec.

## Compliance and Enforcement

- `morpheus-mcp/src/test/java/com/morpheus/mcp/McpFailureContractTest.java` :
  `everyPublishedToolAnswersARefusalInsteadOfThrowing`, `aMissingRequiredArgumentIsRefusedInTheCanonicalFormat`,
  `anUnknownArgumentIsRefusedBeforeAnyHandlerSeesIt`, `theServerArmsSchemaValidationForEveryToolItServes`,
  `everyPublishedToolDeclaresAStrictSchema`.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/McpArgumentHelperOwnershipTest.java` : la lecture d'arguments et le
  mapping d'échec appartiennent à `McpArguments` et `McpToolFailure`.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/McpResultOwnershipTest.java` : chaque résultat décide son `isError`,
  `McpToolFailure` pose `isError(true)` exactement une fois.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/MorpheusControlledLifecycleMcpToolsTest.java` :
  `everyResultStateIsReachedThroughTheToolAndReadsAccordingToTheEnum`, `theDefaultWiringRefusesEveryWriteAsAnError` ;
  `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusM17McpStdioIntegrationTest.java` sur un vrai processus.
- `morpheus-application/src/test/java/com/morpheus/application/lifecycle/mutation/ChangeLifecycleMutationContractTest.java` :
  `onlyAppliedAndAlreadyAppliedAreSuccessful`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/AuditRemediationContractTest.java` :
  `providerPluginMcpFallbackNeverRelaysArbitraryExceptionMessages`.
- Paramètre déclaré honoré ou refusé : les tests de bout en bout de l'export complet, listés par ADR-0092.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/McpStoreFailureInjectionTest.java` : chaque outil servi, contre une base
  illisible puis réservée, répond par un refus du store ; `theInjectedClassesAreExactlyTheServedOnes`.

## References

- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP officiel et STDIO natif.
- [ADR-0092](ARCH-0092-provider-neutral-query-dsl-saved-views-reporting.md) — contrat de l'export.
- [ADR-0093](ARCH-0093-provider-neutral-policy-packs-governance-automation.md) — `UNKNOWN` n'est jamais `BLOCKED`.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — ce qui n'est pas observé est dit.
- [ADR-0129](SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) — frontière du code externe.
- [ADR-0151](BE-0151-pagination-des-collections-et-journaux.md) — pagination des collections.
- `contracts/public-surfaces.tsv`
