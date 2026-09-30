# ADR-0102 — Le contrat d'échec MCP est une règle unique, pas une convention par outil

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 9 septembre 2026
- Dépend de : ADR-0062 (SDK MCP natif), ADR-0093 (sémantique tri-state), ADR-0101 (frontière de code externe)
- Portée : `morpheus-mcp` (les douze classes d'outils et le service catalogue)

## Contexte

`contracts/public-surfaces.tsv` revendique la convergence CLI / MCP / HTTP et le README présente MCP
comme la surface des IDE, agents et orchestrateurs. C'est pourtant la seule des trois dont les
chemins d'erreur n'étaient pas exercés : sur quatorze fichiers de test du module, deux seulement
appelaient un `callHandler`. Les douze autres vérifiaient le catalogue, les noms d'outils et la
**forme du schéma publié** — la déclaration, jamais le comportement.

L'absence de couverture a laissé la duplication diverger. Relevé sur `develop` au commit `664256a5` :

```text
requiredString   10 définitions   (5 statiques, 5 d'instance)  + 1 alias "required" non compté
optionalString    6 définitions
safeMessage      11 définitions   (les 11 corps sont identiques)
requiredLong / requiredBoolean / optionalInteger   1 chacune
bool / integer / intValue / longValue / doubleValue / integral   9 définitions numériques
```

Trois axes avaient dérivé, chacun de manière invisible faute de test.

### Axe 1 — le message

Pour la même condition — argument requis absent ou vide — cinq formes coexistaient :

```java
"missing required MCP argument: " + key                // AugmentedContext, Reasoning, ControlledLifecycle, Jarvis
key + " must be a non-blank string"                    // Composition, PolicyManagement, Policy, Portfolio, Query
key + " must be a non-blank string when present"       // Policy, Portfolio, Query (valeur présente mais vide)
name + " is required and must be a non-blank string"   // McpToolService (les 14 outils du catalogue)
key + " is required"                                   // Query (longValue)
```

L'agent qui doit réparer son appel recevait donc une phrase différente selon l'outil, pour une faute
identique.

### Axe 2 — les exceptions mappées

```text
IllegalArgumentException | KnowledgeStoreException                                    AugmentedContext, Composition,
                                                                                      ControlledLifecycle, ExternalReference,
                                                                                      Jarvis, Portfolio, McpServer
+ IllegalStateException                                                               PolicyManagement, Query
+ IllegalStateException | PolicyConflictException                                     Policy
IllegalArgumentException | IllegalStateException   (pas de KnowledgeStoreException)   Reasoning
RuntimeException   (attrape-tout)                                                     ProviderPlugin
```

`KnowledgeStoreException` et `PolicyConflictException` étendent toutes deux `RuntimeException`.
Selon la classe, la même exception devenait donc soit un `CallToolResult` avec `isError(true)`, soit
une exception non mappée qui s'échappe du handler.

### Axe 3 — les arguments inconnus

Les douze classes publient `additionalProperties: false`. Une seule (`MorpheusReasoningMcpTools`, via
`rejectUnknown`) le faisait respecter dans le handler. La question — le schéma est-il une promesse que
onze handlers ne tiennent pas, ou `rejectUnknown` est-il redondant ? — a été tranchée par mesure, pas
par supposition.

**Mesure.** `MorpheusMcpServer.build` construit le serveur avec `.validateToolInputs(true)`.
`McpAsyncServer` appelle alors `ToolInputValidator.validate(tool, arguments, ...)` **avant**
`callHandler().apply(...)` et retourne son `CallToolResult` d'erreur sans jamais atteindre le handler.
Le validateur par défaut (`DefaultJsonSchemaValidator`, networknt, dialecte draft 2020-12) applique
`additionalProperties`, `required`, `enum`, `minLength`, `minimum` et `maximum`, **y compris sur les
sous-schémas imbriqués**. Sondé sur les schémas réellement publiés par MORPHEUS :

```text
apply_change_lifecycle_transition + {"bogusArgument": ...}
  -> invalid : la propriété 'bogusArgument' n'est pas définie dans le schéma
     et le schéma n'autorise pas de propriétés supplémentaires

apply_change_lifecycle_transition + {}
  -> invalid : propriété requise 'projectId' introuvable (... et les six autres)

reason_with_evidence + {"evidence": [{... , "bogusNested": ...}]}
  -> invalid en /evidence/0 : même règle appliquée à l'item imbriqué
```

**Réponse : le SDK rejette avant dispatch.** `rejectUnknown` était donc du code mort sur la surface
servie — mais du code mort dont la mort dépendait d'un drapeau posé dans une *autre* classe.

## Décision

### 1. Un format de message canonique, dérivé des deux surfaces qui convergent déjà

Le destinataire est un agent qui doit réparer son appel : le message nomme l'**argument fautif en
premier**, puis ce qui était attendu, et distingue une **omission** d'une **valeur malformée** —
parce que la réparation n'est pas la même.

```text
absent                          <nom> is required and must be <attendu>
présent mais inutilisable       <nom> must be <attendu>
optionnel présent, inutilisable <nom> must be <attendu> when present
```

où `<attendu>` est l'un de : `a non-blank string` · `an integer` ·
`an integer between <min> and <max>` · `a boolean` · `a finite number`.

Ce format n'est pas une préférence : c'est celui vers lequel les deux autres surfaces convergeaient
déjà. Le CLI dit `--<key> must be an integer`, l'adaptateur HTTP dit `<name> must be an integer`
(`MorpheusHttpQuery`) — MCP était l'exception. Et la forme requise retenue,
`<nom> is required and must be <attendu>`, est celle que servait déjà `MorpheusMcpToolService`,
c'est-à-dire les **quatorze outils du catalogue**, la plus grande sous-surface MCP. La forme écartée,
`missing required MCP argument: <nom>`, ne dit pas à l'agent à quoi ressemble une valeur valide et
place le nom de l'argument en fin de phrase.

`McpArguments` est le seul endroit du paquet où ce format est écrit.

### 2. L'ensemble d'exceptions mappées est dérivé de ce que le handler peut lever

La règle n'est pas « la liste la plus longue partout ». Elle est :

| Ce que le handler fait | Ce qu'il mappe |
|---|---|
| lit des arguments | `IllegalArgumentException` |
| ouvre un `MorpheusMcpRuntime` ou touche un store | `+ KnowledgeStoreException` |
| délègue à un service qui refuse un état (policy, query DSL, saved views) | `+ IllegalStateException` |
| compose des policies | `+ PolicyConflictException` |

Un handler ne déclare pas une exception qu'il ne peut pas atteindre, et n'en omet pas une qu'il peut
atteindre. `MorpheusReasoningMcpTools` n'ouvre aucun store : il ne mappe pas `KnowledgeStoreException`,
et c'est correct. `MorpheusProductMcpTools` ne lit aucun argument et n'ouvre rien.

### 3. `MorpheusProviderPluginMcpTools` reste un `catch (RuntimeException)`, et c'est motivé

Ce n'est pas un filet trop large : c'est une **frontière de rédaction**, pas une frontière de
mapping. Le handler ne renvoie jamais `safeMessage(...)` — il renvoie le code stable
`PROVIDER_PLUGIN_DISCOVERY_FAILED`, parce que les exceptions de système de fichiers rendent
fréquemment leur pathname comme message et que cette surface est model-facing. Élargir le `catch`
sert ici à **garantir qu'aucun message arbitraire ne sorte**, l'inverse exact d'un attrape-tout qui
masquerait une faute. Le commentaire qui le motive reste dans le code, au même titre que les
`catch (Throwable)` motivés du dépôt.

### 4. Les arguments inconnus sont refusés **avant** le handler, et cette garantie est testée

`rejectUnknown` disparaît de `MorpheusReasoningMcpTools`. La promesse `additionalProperties: false`
n'est pas abandonnée pour autant : elle est **déplacée là où elle est réellement appliquée** et
verrouillée par deux tests.

- `McpFailureContractTest` prouve, pour **chaque** outil publié et avec le validateur du SDK, qu'un
  argument absent du schéma est refusé, et qu'un argument requis manquant est refusé.
- `McpFailureContractTest` épingle aussi `.validateToolInputs(true)` dans `MorpheusMcpServer` :
  retirer le drapeau casse le build au lieu de désarmer silencieusement onze schémas.

Sans cette seconde assertion, supprimer `rejectUnknown` échangerait une redondance visible contre une
dépendance invisible. Avec elle, la règle est énoncée une fois et vérifiée une fois.

**Conséquence assumée** : le message d'un argument inconnu est produit par le SDK, pas par MORPHEUS.
Il est **localisé** par la bibliothèque networknt selon la locale par défaut de la JVM. Le format
canonique de la section 1 ne gouverne que les messages que MORPHEUS écrit lui-même ; les tests de
contrat n'assertent donc ni sur le texte du SDK ni sur sa langue, seulement sur le refus.

## Conséquences

### Ce qui change de manière observable

| Surface | Avant | Après |
|---|---|---|
| Argument requis absent, 8 classes d'outils | `missing required MCP argument: X` ou `X must be a non-blank string` | `X is required and must be a non-blank string` |
| Argument requis présent mais vide, catalogue | `X is required and must be a non-blank string` | `X must be a non-blank string` |
| Argument optionnel présent mais vide | `X must be a non-blank string` ou `... when present` | `X must be a non-blank string when present` |
| `reason_with_evidence` + argument inconnu | `unknown MCP argument: X` (MORPHEUS) | message de validation du SDK, avant dispatch |
| Entier hors bornes | inchangé | `X must be an integer between M and N` |

Aucun nom d'outil, aucun schéma publié, aucune ligne de `contracts/public-surfaces.tsv` ne change.
`isError(true)` reste `isError(true)` dans tous les cas ci-dessus : c'est le **texte** qui converge,
pas la classe de résultat.

### Ce que ça rend impossible

Un treizième outil ajouté sans mapping d'erreur fait échouer `McpFailureContractTest`. Une nouvelle
copie privée de `requiredString` fait échouer `McpArgumentHelperOwnershipTest`. Un `catch` qui oublie
`KnowledgeStoreException` sur un handler qui ouvre un store fait échouer le test qui appelle ce
handler sur une base absente.

### Ce que ça ne fait pas

Ce contrat ne dit rien de la **sémantique** des échecs métier — `UNKNOWN` n'est toujours pas
`BLOCKED` (ADR-0093), un conflit de composition reste explicite, et un `CallToolResult` en erreur
reste un refus, jamais une dégradation silencieuse.

## Amendement du 29 septembre 2026 (MCP-5) — un refus rendu comme valeur se lit comme un refus

### Constat

Le contrat ci-dessus dérive ce que le handler *mappe* de ce qu'il peut *lever*. Il ne dit rien d'un refus que le service
**rend**. `ControlledChangeLifecycleMutationService.apply` rend ses refus dans `ChangeLifecycleMutationResult` : `CONFLICT`
(clé d'idempotence réutilisée pour une autre commande, révision attendue périmée, ou perte de la course CAS à l'écriture),
`NOT_AUTHORIZED`, `REQUIRES_CONFIRMATION`, `REJECTED`. Aucun ne lève, donc aucun ne franchissait le `catch` de
`MorpheusControlledLifecycleMcpTools`, et tous sortaient d'un résultat sans `isError` : un agent qui lit le drapeau, comme le
protocole l'y invite, voyait un succès. Le câblage par défaut (`MorpheusMcpServer.build(Path)`, `run(Path)`) passe
`deniedWrites()` : **toute** tentative de mutation de cycle de vie y rendait `NOT_AUTHORIZED` sous la forme d'un succès (les autres
outils d'écriture — portefeuilles, vues sauvegardées, packs de policy — ne passent pas par ce résolveur). Le lanceur
`mcp --stdio` passe `CliProjectWriteCapabilityResolver` : il refuse de même tant que le provider qu'il embarque (OpenSpec)
n'expose pas `WRITE_CHANGE`, ce que `MorpheusM17McpStdioIntegrationTest` observe sur un vrai processus.
Le corps portait `"state":"CONFLICT"`, ce qui bornait la gravité sans la supprimer. Le CLI, lui, distinguait déjà : code de
sortie `4` pour tout état hors `APPLIED` et `ALREADY_APPLIED`.

### Décision

1. **`isError` se décide sur l'état du résultat.** Succès : `APPLIED` (appliqué maintenant) et `ALREADY_APPLIED` (appliqué par
   un appel antérieur de même clé — le changement est là où l'appelant le voulait). Tout le reste est une erreur.
2. **Le corps ne change pas d'un octet.** L'état, la raison, `lifecycleState` et `audit` restent ceux d'avant : le drapeau
   ajoute un fait, il n'en retire aucun (ADR-0108). Un client qui lisait `state` continue de le lire.
3. **La partition s'écrit à un seul endroit : `ChangeLifecycleMutationResultState.successful()`**, un `switch` qui nomme
   chaque constante et n'a **pas de `default`** (patron de `MorpheusPolicyCli.exitCodeOf`) : un nouvel état ne compile pas tant
   que personne n'a décidé de quel côté de la ligne il tombe. Trois lecteurs la consomment : le CLI (code de sortie), MCP
   (`isError`) et l'invariant de `ChangeLifecycleMutationResult` (seul un succès porte un enregistrement d'audit). Le CLI et
   l'invariant écrivaient chacun la paire `APPLIED` / `ALREADY_APPLIED` (l'invariant deux fois, dont une niée) ; MCP n'en avait
   aucune, ce qui était le défaut. Deux copies plus l'absence d'une troisième deviennent une méthode : c'est le cas où l'on
   supprime la copie plutôt que de la garder sous garde, parce que MCP et CLI dépendent tous deux d'`application`, où
   l'énumération vit.
4. **`McpToolFailure` est étendue, pas contournée.** `refusal(String body)` construit le résultat d'erreur à partir d'un corps
   déjà structuré et le laisse passer tel quel ; `result(RuntimeException)` y délègue avec le message de l'exception. C'est
   désormais **le seul endroit du module qui pose `isError(true)`** : le talon `check_product_update` et la frontière de
   rédaction de `MorpheusProviderPluginMcpTools` le construisaient à la main, et le passent désormais par lui. La rédaction de
   ce dernier ne change pas (il rend toujours un code stable, jamais le message de l'exception).
5. **Deux résultats sans décision ont reçu la leur.** `MorpheusAugmentedContextMcpTools` et `MorpheusCompositionMcpTools`
   construisaient un succès sans écrire `isError` (résultat de lecture, `false` par défaut du protocole) ; ils l'écrivent, pour
   que la garde puisse exiger qu'aucun résultat n'échappe à la décision.

### Inventaire des constructions de `CallToolResult` dans `morpheus-mcp`

Chaque site, et s'il peut porter un refus que le service rend au lieu de le lever :

| Site | Peut porter un refus rendu ? |
|---|---|
| `McpToolFailure.refusal` | Le seul constructeur d'erreur. |
| `MorpheusControlledLifecycleMcpTools` | **Oui**, quatre états de refus (sept chemins de retour) : corrigé, `isError` suit `successful()`. |
| `MorpheusMcpServer.call` (les quatorze outils du catalogue) | Non : lectures ; leurs refus lèvent. |
| `MorpheusQueryMcpTools` | Non : les écritures de vues (CAS, archivage) lèvent `SavedViewConflictException` (une `IllegalStateException`), attrapée ; les exports lèvent. |
| `MorpheusPolicyMcpTools` | Écritures : non, elles lèvent `PolicyConflictException`. `evaluate_policies` et `dry_run_policy_pack` rendent une **décision** (`BLOCK`, `UNKNOWN` comprises) — voir plus bas. |
| `MorpheusPolicyMcpManagementTools`, `MorpheusPortfolioMcpTools`, `MorpheusExternalReferenceMcpTools`, `MorpheusAugmentedContextMcpTools`, `MorpheusCompositionMcpTools` | Non : lectures. Un statut d'intégration, un `resolutionState` ou un conflit de composition sont des observations déclarées dans la réponse (ADR-0108), pas le refus d'une opération. |
| `MorpheusJarvisOrchestrationMcpTools` | `evaluate_change_transition` rend le verdict d'une évaluation (`ALLOWED`, `BLOCKED`…) : c'est la réponse à la question posée, et le CLI le rend par le code `0`. |
| `MorpheusReasoningMcpTools` | Le statut `FAILED` d'un adaptateur figure dans `executions` : une exécution partielle déclarée, que le CLI rend aussi par le code `0`. |
| `MorpheusProductMcpTools` | `product_info` : non. `check_product_update` : refus par construction, désormais via `McpToolFailure.refusal`. |
| `MorpheusProviderPluginMcpTools` | Échec : via `McpToolFailure.refusal` avec le code stable ; succès : non. |

### Alternatives écartées

- **Faire lever le service.** Changerait le contrat d'`application` et les sorties CLI et HTTP, qui lisent l'état ; le constat
  porte sur la traduction MCP, pas sur le modèle.
- **Deux copies (MCP et CLI) et une garde qui les tient égales**, sur le modèle de `PagedResponseVocabularyArchitectureTest`.
  Une garde entre deux copies ne se justifie que là où la copie ne peut pas disparaître ; ici elle le peut.
- **Un `default -> false` ou un `Set` d'états de succès.** Un état futur y serait classé en erreur (ou en succès) sans que
  personne ne l'ait décidé : c'est la conversion silencieuse que l'ADR-0093 interdit, en plus discret.
- **Poser `isError` sur `CONFLICT` seulement.** Le constat était plus large que le conflit : `NOT_AUTHORIZED` est ce que
  reçoit tout client du câblage par défaut.

### Preuves exécutables

- `MorpheusControlledLifecycleMcpToolsTest` : un test par état de refus (les deux `CONFLICT` distincts, `NOT_AUTHORIZED`,
  `REQUIRES_CONFIRMATION`, `REJECTED`) qui exige `isError()` **et** que le corps garde l'état, la raison et `"audit":null` ;
  `APPLIED` et `ALREADY_APPLIED` en succès ; `everyResultStateIsReachedThroughTheToolAndReadsAccordingToTheEnum` exige que
  chaque constante de l'énumération soit atteinte par l'outil et lue selon `successful()` ; `theDefaultWiringRefusesEveryWriteAsAnError`
  appelle le câblage par défaut sur le transport réel et lit le membre `isError` du résultat JSON-RPC.
  `MorpheusM17McpStdioIntegrationTest` l'exige aussi d'un vrai processus `mcp --stdio`.
- `ChangeLifecycleMutationContractTest#onlyAppliedAndAlreadyAppliedAreSuccessful` écrit la partition indépendamment du `switch`.
- `McpResultOwnershipTest` : hors `McpToolFailure`, chaque chaîne `CallToolResult.builder(...)` décide elle-même son `isError` et
  le décide au littéral `false` ; `isError` n'est jamais appelé avec autre chose ; l'import statique du builder ou de `McpToolFailure`, le type
  `CallToolResult.Builder` et le constructeur sont refusés ; `McpToolFailure` pose `isError(true)` exactement une fois. Elle lit du
  code (commentaires retirés, littéraux vidés par un petit scanner qui connaît les blocs de texte et les caractères), juge chaque
  builder sur sa propre chaîne (deux builders dans une expression, ou un `;` dans un argument lambda, ne brouillent pas le verdict),
  est récursive, échoue si elle ne juge rien dans une classe qui retourne un résultat d'outil, et chaque règle est éprouvée dans les
  deux sens sur des sources synthétiques.
- `AuditRemediationContractTest#providerPluginMcpFallbackNeverRelaysArbitraryExceptionMessages` (fichier de gouvernance) exigeait le
  texte `addTextContent(REMOTE_DISCOVERY_FAILURE)`. Il exige désormais `McpToolFailure.refusal(REMOTE_DISCOVERY_FAILURE)`, refuse
  `McpToolFailure.result(` et `McpToolFailure.safeMessage` (les deux relaient le message d'une exception, ce que cette frontière de
  rédaction ne fait jamais — §3) et veut que **chaque** `refusal(` de ce fichier porte le code stable. Aucune assertion n'est retirée.

### Ce que la garde ne couvre pas

`McpResultOwnershipTest` ne peut pas savoir si un **corps** porte un refus : un handler qui répond `isError(false)` autour d'un
résultat d'état `BLOCKED` la satisfait. Cette propriété est celle de chaque outil et se teste là où l'état existe ; l'inventaire
ci-dessus est le jugement porté aujourd'hui, pas une vérification qui se rejoue. Elle ne voit pas non plus : un builder gardé dans une
variable et décidé dans une instruction suivante (refusé comme non décidé — conservateur, pas exact) ; un résultat assemblé hors des
sources principales de ce module (un autre paquet, `morpheus-mcp-transport` qui écrit des erreurs JSON-RPC et non des résultats
d'outil, la réflexion, une méthode qui retourne un builder) ; une séquence d'échappement Unicode qui tient lieu de guillemet ; un
`McpToolFailure.refusal(...)` alimenté par un corps qui n'est pas un refus ; ni si les outils jugés sont ceux qui sont servis (elle lit
des sources, pas les spécifications enregistrées) ; le propriétaire lui-même, exempté de toutes les règles sauf « exactement un
`isError(true)` » (il pourrait construire par constructeur ou import statique sans rien faire échouer) ; le contrôle « quelque chose a-t-il été
jugé dans cette classe » est lâche de trois façons : une classe qui ne cite que `McpToolFailure.safeMessage` compte comme routée par le propriétaire
sans y construire de résultat, et un `Optional<CallToolResult>` ou une lambda sans méthode nommée ne font pas compter leur classe comme
retournant un résultat — la règle principale, elle, juge tout builder de tout fichier, quoi que le fichier déclare. Elle interdit qu'un résultat sorte **sans** que son `isError` ait été écrit ; elle
n'affirme pas qu'il soit juste.

### Ce que cet amendement ne tranche pas

- **HTTP.** `POST /api/v1/projects/{projectId}/changes/{changeId}/lifecycle-transitions` répond `200` avec l'état de refus dans
  le corps, y compris `NOT_AUTHORIZED` (`MorpheusControlledLifecycleApiContractTest` le fige). C'est la même classe de défaut
  que celle corrigée ici, sur une surface dont le contrat OpenAPI et les statuts sont un choix distinct, à arbitrer séparément.
- **Décisions de policy.** `evaluate_policies` et `dry_run_policy_pack` rendent `isError(false)` pour `BLOCK` et `UNKNOWN`,
  là où `policy evaluate` et `policy dry-run` rendent le code `4` (amendement CLI-1 d'ADR-0108). La décision est la réponse de
  l'évaluation, pas le refus de l'appel ; mais les deux surfaces divergent, et le sens à donner à `isError` pour un verdict est
  une question ouverte.


## Amendement du 29 septembre 2026 (MCP-3) — un schéma d'entrée ne déclare que ce que le handler honore

### Constat

`exportQuerySchema()` publiait `offset` et `limit` en reprenant les propriétés d'une requête paginée. Le handler de `export_query`
les lisait bel et bien (`query(...)` est partagé avec `execute_query`, `create_saved_view` et `update_saved_view`) et les rangeait dans la
`QueryDefinition`, mais `QueryExportService.export` appelle `materializeComplete`, qui ne lit jamais `query.page()`. Un agent qui bornait
son export (`limit = 10`) recevait l'export **complet**, jusqu'à 10 000 lignes, ou une erreur de budget sur le total : une demande
bornée était acceptée et ignorée. La description de l'outil (« Export the complete bounded query view ») était juste ; c'est le
schéma qui mentait. Les deux autres surfaces avaient le même défaut : `export query` du CLI avait `--offset` et `--limit` dans son
allowlist, et le corps de `POST /api/v1/exports` portait une `QueryRequest` complète, `offset` et `limit` compris.

### Décision

**L'export est complet par contrat, sur les trois surfaces ; un paramètre de page y est refusé, non ignoré.** Ce qui est refusé
l'est là où chaque surface refuse déjà : le SDK avant tout handler pour MCP (`additionalProperties: false`, §4 ci-dessus), le
décodeur strict pour HTTP (`400 BAD_REQUEST`), l'analyse des options pour le CLI (code d'usage).

- **MCP.** `exportQuerySchema()` ne déclare plus que le périmètre, `entity`, `filter`, `sort`, `fields` et `format`
  (`queryShapeProperties()` ; `queryProperties()` y ajoute la page pour les trois autres outils). Le handler d'`export_query` n'appelle plus
  la lecture de `offset` et `limit`. La description de l'outil dit pourquoi : l'export est toujours complet, borné par
  `QueryBudgets.MAX_EXPORT_ROWS`, et ne prend ni `offset` ni `limit` ; celle d'`export_saved_view` dit qu'il ignore la page stockée avec la vue.
- **CLI.** `export query` retire `--offset` et `--limit` de son allowlist et **dit pourquoi** (`--limit is not accepted by export: an
  export is always complete…`) plutôt que le générique « unknown option ». L'aide le dit, et précise que `export view` ignore la page stockée.
- **HTTP.** `ExportRequest.query` devient un `ExportQueryRequest` (`entity`, `filter`, `sort`, `fields`) : `limit` et `offset` sont des
  propriétés inconnues, refusées en 400 par le décodeur strict. `docs/openapi/morpheus-v1-query-m24.yaml` est mis à jour dans le même
  changement (un schéma `ExportQueryRequest`, `additionalProperties: false`, référencé par `ExportRequest`) : sans cela le contrat
  publié promettait un paramètre que le serveur refuse. Sa phrase « la pagination interactive ne tronque pas silencieusement
  l'export » devient vraie de bout en bout.
- **Saved view.** `export view` et `POST /saved-views/{id}/export` ignoraient déjà la page stockée avec la vue ; ils la disent.

**Pourquoi ce deuxième amendement d'ADR-0102 et non un autre ADR.** Le défaut est l'inverse exact de l'axe 3 de cet ADR : un schéma
qui promet ce que le handler ne tient pas. L'ADR-0107 traite du vocabulaire d'une page, pas de ce qu'un schéma peut déclarer ; l'ADR-0108,
de ce qu'une réponse dit ne pas avoir observé. Le mot de l'ADR-0102 — le refus est explicite, jamais une dégradation silencieuse — est celui
qui s'applique ici.

### Conséquence directe, écrite sans détour : un export de plus d'1 Mio n'est pas rendu par `export_query`

Un export est complet par contrat, donc **il ne se pagine pas**. Or le cadre MCP est d'1 Mio (`BoundedStdioServerTransportProvider.DEFAULT_MAX_FRAME_BYTES`,
1 048 576 octets) et le budget d'un export est de **10 Mio** (`QueryBudgets.MAX_EXPORT_BYTES`), dix fois plus, sur 10 000 lignes au plus. Un export valide de
plus d'1 Mio est donc **refusé par le transport MCP** : le client reçoit non pas un résultat mais une erreur JSON-RPC dont le texte est
`MCP_RESPONSE_TOO_LARGE: the response is <N> bytes, past the 1048576-byte MCP STDIO frame bound; paginated read tools accept offset and
limit: retry with a smaller limit, then page with offset` (`BoundedStdioServerTransportProvider.responseTooLarge`, la fin du message est
`MorpheusMcpServer.OVERSIZED_RESPONSE_GUIDANCE`). **Ce conseil ne peut pas être suivi sur `export_query`**, qui ne prend ni `limit` ni `offset` : c'est
exactement ce que la décision ci-dessus impose, et c'est le même trou que celui de l'amendement MCP-2 d'ADR-0107, où `export_query` (et `export_saved_view`)
figurent dans la garde comme résidu reconnu (`UNBOUNDED_ACKNOWLEDGED`). Ce n'est pas une régression : avant cet amendement l'outil acceptait `limit` et
l'ignorait, si bien que le conseil du transport ne servait pas non plus. Ce n'est pas non plus corrigé ici : un export entre 1 et 10 Mio reste valide sur
le CLI et sur HTTP et **inatteignable en un appel MCP**.

**Recours, tels qu'ils ont été vérifiés.**

- **`execute_query`, paginé.** Mêmes `scopeKind`, `scopeId`, `entity`, `filter`, `sort`, `fields`, avec `offset` et `limit` (de 1 à 500) : il rend les **mêmes
  lignes**, page par page, dans le JSON d'un résultat de requête. Il ne rend **ni l'enveloppe d'un export ni ses rendus CSV et Markdown**. Prouvé de bout en bout
  sur le serveur réel (`anExportPastTheFrameIsRefusedByTheTransportAndItsRowsAreReadThroughPagedQueries`) : un export de plus d'1 Mio est refusé avec ce message, et les
  quatorze exigences sont relues, une fois chacune, en trois pages.
- **CLI `export query`** : écrit l'export complet sur la sortie standard (`MorpheusQueryCli.export`), sans cadre d'1 Mio. **HTTP `POST /api/v1/exports`** : route de
  lecture du serveur local et du remote (`MorpheusRemoteRoutePolicy`), sans cadre non plus ; le proxy remote refuse une réponse locale de plus de 16 Mio
  (`MorpheusRemoteHttpServer.MAX_PROXY_RESPONSE_BYTES`), au-dessus du budget de 10 Mio. **Non exécuté** sur un export de plus d'1 Mio : ces deux recours sont lus dans le code,
  pas mesurés, et la marge du proxy n'a pas été éprouvée avec un export réel.
- **`export_saved_view`** a la même conséquence et **n'a pas de recours MCP paginé** : `execute_saved_view` exécute la définition avec la page stockée, que l'appelant
  ne choisit pas. Son recours est le CLI (`export view`) ou HTTP (`POST /api/v1/saved-views/{id}/export`), lus dans le code et non mesurés.

La description de l'outil `export_query` le dit désormais à l'agent qui la lit, au moment où il choisit l'outil.

### Alternatives écartées

- **(b) L'export honore la fenêtre.** `QueryExportService.export` appliquerait `offset` et `limit` et le budget se mesurerait sur la
  fenêtre. Plus risqué (il change `QueryExport` pour les trois surfaces, dont le CSV et le Markdown, contrats d'un rapport), et il
  contredit le contrat déjà publié d'un export : la vue bornée **complète**.
- **Ignorer sans le dire, en corrigeant seulement la description.** C'est l'état d'avant : un client qui bornait obtenait tout.

### Preuves exécutables

- `MorpheusQueryMcpToolsTest` : `export_query` ne déclare que ses sept propriétés attendues et sa description dit pourquoi ; `limit` et
  `offset` y sont refusés par la validation de schéma alors que `execute_query` les accepte ; **sur le serveur réel**, un projet de 120
  exigences (au-delà de la page par défaut) s'exporte en entier, et le même export avec `limit` ou `offset` est une erreur ; **un export de plus
  d'1 Mio est refusé par le transport avec `MCP_RESPONSE_TOO_LARGE` et le conseil du serveur, et ses lignes sont relues par `execute_query` paginé**.
- `MorpheusQueryCliTest` : `export query --limit` et `--offset` sortent en code d'usage avec le motif, sans rien imprimer ; sans eux l'export
  réussit ; l'aide dit qu'un export est complet.
- `MorpheusQueryApiContractTest` : `POST /exports` avec `limit` ou `offset` dans `query` répond 400 ; sans eux, 200.
- `QueryExportMaterializationTest#anExportIgnoresThePageOfItsDefinitionAndIsComplete` : une définition dont la page est étroite exporte les
  mêmes 300 lignes qu'une définition dont la page est large — c'est la raison pour laquelle aucune surface ne doit l'accepter.

### Ce que la garde ne couvre pas

Une garde **forte** — confronter, pour chaque outil, les propriétés que son schéma déclare à celles que son handler lit — n'est pas
posée, et elle aurait été **aveugle dans le cas même du défaut** : le handler lit `offset` et `limit` par une méthode partagée entre quatre
outils, donc une lecture statique de la classe les voit lues pour `export_query` comme pour `execute_query`. Ce qui tient la classe « un paramètre
déclaré est honoré ou refusé » est le test de bout en bout ci-dessus : il est écrit pour l'export, sur les trois surfaces, et ne couvre
pas mécaniquement le prochain outil. La garde **faible** est la liste exacte des propriétés d'`export_query` : elle ne dit rien de ce qui est
lu. Pas de comparaison de complétude sur le CLI et HTTP avec des données réelles au-delà d'une page : ils sont vérifiés sur le refus, et la
complétude par le test d'application et le test MCP sur données réelles. Le message d'un argument inconnu côté MCP est celui du SDK,
localisé (§4) : les tests assertent le refus, pas son texte ; le « pourquoi » est porté par la description de l'outil.
