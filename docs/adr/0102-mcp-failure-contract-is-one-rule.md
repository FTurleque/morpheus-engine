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
