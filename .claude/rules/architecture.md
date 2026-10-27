# Règles — Architecture (ports & adapters)

Source de vérité : `morpheus-architecture-tests/.../LayerDependencyTest.java`, `ApplicationTechnologyBoundaryArchitectureTest`
+ les `*ArchitectureTest` par milestone.
Ces règles sont **exécutables** — si tu doutes, lance le test, ne devine pas.

## Le modèle réel : hexagonal, pas en couches empilées

`morpheus-application` **définit les ports** (interfaces) et ne connaît **aucun** adaptateur.
Les adaptateurs (provider, store, cli, mcp, api, integration) implémentent ces ports et dépendent vers l'intérieur.

Exemple de port : `com.morpheus.application.read.SpecificationContentReader`
— implémenté par `provider.openspec`, `provider.markdown`, `provider.synthetic`.

## TOUJOURS

- Ajouter toute nouvelle capacité comme **port dans `application`** + **implémentation dans un adaptateur**
- Vérifier qu'un consommateur de port reste provider-neutre : il doit fonctionner avec les 3 providers (openspec, markdown, synthetic) — cf. `ProviderAntiLockInTest`
- Garder les identités **scopées par provider** : deux providers avec la même clé externe produisent des `DomainIdentity` **différentes**
- Faire passer toute communication MINOS/NEXUS par **MCP STDIO uniquement**

## Anatomie d'une règle de couche (exemple réel)

`LayerDependencyTest.java` encode chaque interdit comme une règle ArchUnit `noClasses()`,
avec un `because(...)` qui explique le *pourquoi* — c'est ce message qui apparaît dans le
rapport de test en cas de violation :

```java
@Test
void applicationMustNotDependOnAdapters() {
    noClasses()
            .that().resideInAPackage("com.morpheus.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.morpheus.provider..", "com.morpheus.store..", "com.morpheus.cli..",
                    "com.morpheus.mcp..", "com.morpheus.api..", "com.morpheus.integration..",
                    "com.minos..", "com.nexus..", "com.jarvis..")
            .because("application services define use cases and ports without depending on adapters")
            .check(classes);
}
```

**Ajouter une nouvelle frontière de module** = ajouter un test de cette forme dans
`LayerDependencyTest.java` (règles transverses) ou dans un `*ArchitectureTest` dédié au
milestone (règles de sous-plateforme, ex. `m24/QueryPlatformArchitectureTest`). Ne jamais
se contenter d'une convention non testée — si ArchUnit ne la vérifie pas, elle n'existe pas.

## Règle ArchUnit ou assertion textuelle

Beaucoup d'interdits de dépendance sont aujourd'hui écrits `assertFalse(<source lue>.contains("X"))`.
**Ce n'est pas une forme dégradée à migrer par réflexe** : les deux mécanismes n'enforcent pas la
même proposition, et aucun ne domine l'autre.

| | `assertFalse(src.contains("X"))` | règle ArchUnit |
|---|---|---|
| Interdit | la **mention** de `X` | la **dépendance** compilée |
| Dépendance indirecte, ou non épelée | ratée | **vue** |
| Référence à une constante de compilation | **vue** | ratée — `javac` l'inline |
| Portée | tout `src/main/java` | classpath de `morpheus-architecture-tests` |

Avant de choisir, **écrire l'intention en une phrase**. « Aucune dépendance » → ArchUnit.
« Ce nom ne doit pas apparaître ici » → texte. Dans le doute, **garder les deux**.

Restent textuels par nature, ne pas les migrer :

- les interdits de chaîne scannés sur tout le dépôt (`activateDefaultTyping(`,
  `request.header("Authorization"`, `token + "|"`) — ArchUnit ne voit pas les fichiers hors classpath ;
- les expressions de **câblage explicite** — aucune règle sur le bytecode ne dit quel argument un
  constructeur a reçu ;
- tout ce qui vise un `.yml`, `.ps1`, `.md`, `.iss`, `.sh`, `.xml`, `.tsv` : le texte y est la seule prise.

Trois obligations avant d'accepter une règle migrée :

1. **Vérifier que la classe visée est dans l'ensemble importé.** `morpheus-provider-testkit` n'y est
   pas (`morpheus-provider-reference` y est, ADR-0028) — relire
   le `pom.xml` de `morpheus-architecture-tests` plutôt que cette ligne. Le garde-fou `archRule.failOnEmptyShould` est actif et
   fait échouer une règle qui ne retient aucune classe — **ne pas le désactiver**.
2. **Vérifier qu'un littéral n'est pas un préfixe de famille.** `contains("MorpheusRemote")` interdit
   toute une famille de types, pas un seul.
3. **Casser la règle pour prouver qu'elle tient** : introduire la violation, constater l'échec,
   revenir en arrière. Une règle vide passe aussi.

Découper par **intention**, jamais par classe de test : regrouper fait descendre le compte de
méthodes `@Test`, et `architectureTestsMinimum` ne se baisse pas (`rules/testing.md`).

**Généralisation par groupe de capacité**, jamais par famille entière :

- les trois interdits vrais pour **tous** les routeurs (le paquet `com.morpheus.api.remote`, l'appel `sendResponseHeaders`,
  `MorpheusHttpPathParser`) vivent dans `HttpRoutesFamilyArchitectureTest`, règle **et** texte — n'y
  ajouter qu'un interdit vérifié sur chacun des `*HttpRoutes`. Le deuxième a visé **le type**
  `MorpheusHttpResponseWriter` jusqu'à DT-17 : vrai des treize routeurs que le serveur porte en champ, il
  était faux des quatre autres, qui écrivaient déjà leur réponse et ne pouvaient satisfaire un interdit sur
  le writer partagé qu'en gardant une copie privée de ses huit lignes — la règle nommait la bonne intention
  et enforçait son contraire. Interdire **la mécanique** plutôt que le collaborateur dit ce que la méthode a
  toujours annoncé ; le writer, lui, s'interdit **par groupe** (voir ci-dessous) ;
- la frontière transport/JSON se règle par capacité dans `HttpRoutesTransportBoundaryArchitectureTest`, en
  **trois groupes qui sont trois paquets** de `com.morpheus.api.routes` — `bodiless`, `shareddecoder`,
  `owncontext`, nommés par `ROUTERS_WITHOUT_A_BODY`, `ROUTERS_THROUGH_THE_SHARED_DECODER`,
  `ROUTERS_REGISTERING_THEIR_OWN_CONTEXT`. Ranger un routeur, c'est le classer : un routeur rangé ailleurs (le paquet
  parent, un paquet qu'aucune règle ne nomme) est refusé, et
  `theClassificationRefusesARouterLeftInTheParentPackageAndOneInAnUnnamedPackage` le prouve.
  Le troisième groupe porte une **règle de dépendance**, pas une règle de configuration : ces routeurs
  *doivent* dépendre de `MorpheusHttpRequestDecoder` **et de `MorpheusHttpResponseWriter`**, et ne dépendent
  ni de `tools.jackson..`, ni d'`HttpRequestBodyReader`, ni de `CanonicalJsonSerializer` (les volets Jackson
  et sérialiseur gardent leur doublon textuel, ces classes déclarant des constantes inlinables ; le décodeur,
  le reader et le writer n'en déclarent aucune, donc la règle seule suffit). Le writer est **exigé** ici et
  **interdit** dans les deux autres groupes : depuis DT-17 c'est une propriété de groupe, jamais de famille.
  Le troisième groupe s'est appelé « à `JsonMapper` propre » puis « qui écrit sa propre réponse » : les deux
  noms décrivaient une duplication depuis supprimée (DT-16, puis DT-17). Il est nommé pour ce qui reste vrai —
  ces quatre routeurs enregistrent leur propre contexte HTTP au lieu d'être portés en champ par le serveur.
  La règle de configuration existe toujours mais elle est **transverse, pas liée à un groupe** :
  `everyApiJsonMapperIsStrictAboutUnknownPropertiesAndTrailingTokens` exige les deux features de
  désérialisation stricte de **tout** `JsonMapper.builder()` de `morpheus-api` — depuis DT-16 elle ne trouve
  plus qu'un seul site, le décodeur. `MorpheusHttpServer` n'est la frontière d'aucun groupe : ne l'interdire
  nulle part. Aucun routeur ne le mentionne : les enveloppes (`ApiSuccess`, `ApiError`, `ApiErrorEnvelope`)
  sont des types de `com.morpheus.api.transport`, les records de requête des types du paquet des routeurs, et
  `API_PREFIX`, que `javac` inline, est déclaré par `MorpheusHttpRouteTable`. Le serveur local, dans
  `com.morpheus.api.local`, dépend des routeurs et jamais l'inverse ;

  > Rien ne compare cette description à la classe qu'elle décrit. Ces lignes ont menti d'une révision entière
  > après DT-16 (constat O-3 de l'audit du 16/09/2026) : elles annonçaient un groupe « à `JsonMapper` propre »
  > supprimé depuis, et une règle de configuration là où c'est une règle de dépendance. **Relire la classe avant
  > de modifier ce paragraphe** — les noms de constantes ci-dessus sont la seule prise fiable.
- les assertions visant des cibles sans famille (services, plomberie `LocalHttp*`, serveur remote) **ne
  migrent pas** ;
- le troisième groupe a une seconde règle de groupe : ses quatre
  routeurs, à qui le serveur ne remet aucun segment, les demandent à `MorpheusHttpRequestDecoder.pathSegments*` —
  et **eux seuls** (`onlyARouterThatRegistersItsOwnContextAsksTheDecoderForThePath`). L'interdit de famille sur
  `MorpheusHttpPathParser` tient le parseur ; cette règle tient le chemin. Un routeur porté par le serveur qui
  demanderait le chemin au décodeur passerait l'interdit de famille et referait la seconde lecture qu'il interdit.

## JAMAIS — interdits enforced par ArchUnit

### `com.morpheus.domain..` ne doit dépendre de rien de tout ça
```
provider..  store..  cli..  mcp..  api..  integration..
com.minos..  com.nexus..  com.jarvis..
```

### `com.morpheus.application..` — **exactement les mêmes interdits**
L'application ne dépend d'**aucun** adaptateur. Pas de `store`, pas de `provider`, pas de `provider.sdk`.
> `applicationMustNotDependOnAdapters` — *"application services define use cases and ports without depending on adapters"*

### `com.morpheus.api..` est un **frère** de cli/mcp, pas leur parent
Interdits depuis `api` : `cli..`, `mcp..`, `integration..`, `com.minos..`, `com.nexus..`, `com.jarvis..`
> L'adaptateur HTTP réutilise les contrats *application*, jamais un autre adaptateur.

### `integration.minos` / `integration.nexus`
Interdits : `cli..`, `mcp..`, `api..`, `store..`, **et l'implémentation `com.minos..` / `com.nexus..` elle-même**
> Les intégrations implémentent des ports application et parlent **MCP STDIO uniquement**.

### `com.morpheus..` ne dépend **jamais** de `com.jarvis..`
> M14 expose un contrat machine read-only ; JARVIS consomme MORPHEUS sans en devenir une dépendance.

### Sous-plateformes application — isolement renforcé

| Package | Interdits supplémentaires |
|---|---|
| `application.query.{dsl,saved,export}` | cli, mcp, api, store.memory, store.sqlite, provider.openspec, provider.markdown |
| `application.policy..` | idem **+ `provider.sdk`** |
| `application.reasoning..` | idem + **tout `provider..`** + **`application.lifecycle.mutation..`** |

### `domain` et `application` ignorent le SDK plugin — **jusque dans les POMs**
`morpheus-domain/pom.xml` et `morpheus-application/pom.xml` ne doivent contenir
ni `morpheus-provider-sdk` ni `morpheus-provider-reference`.
> `ProviderPluginPlatformContractTest#domainAndApplicationDoNotDependOnSdkOrReferencePlugin`

### `api` / `mcp` → stores et providers concrets : seulement par les racines nommées (ADR-0109)
Les serveurs HTTP et MCP construisent le runtime de chaque requête à partir de `store-sqlite` (et le provider OpenSpec
pour la synchronisation HTTP). Seules les classes de `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` y ont
droit ; la liste est exactement l'ensemble mesuré et ne fait que rétrécir. En ajouter une exige de remplacer ADR-0109 par un ADR consolidé qui la nomme.
> `onlyTheNamedCompositionRootsReachAConcreteStoreOrProvider`, `everyNamedCompositionRootStillReachesAConcreteStoreOrProvider`

Les stores d'une opération sont construits par **une seule classe**, `SqliteOperationRuntime`, qui ouvre la
`SqliteConnectionScope` avant tout store : une connexion physique et une vérification de schéma, sur tous les
transports. Chaque transport a **un** type de runtime qui l'enveloppe (`ApiRuntime`, `CliRuntime`, `MorpheusMcpRuntime`)
et ne rend que des ports de l'application — c'est ce qui garde les services hors de `COMPOSITION_ROOTS`. Une autre
classe qui construit un store est refusée, sauf si elle est **nommée** dans `SqliteConnectionScopeUseArchitectureTest`
avec son opération à store unique ; le texte (sans commentaires, ouverture avant la première construction) et le
bytecode (appel de `SqliteConnectionScope.open`, pas une simple dépendance) le tiennent (ADR-0109).

### `store-sqlite` : `adapter` et `runtime`, et le runtime reste interne
`com.morpheus.store.sqlite.adapter` ne contient que des implémentations de ports de l'application, plus les deux
collaborateurs nommés du store de contenu de snapshot. `com.morpheus.store.sqlite.runtime` porte la connexion, la
portée, le bail, le schéma et les transactions. Aucune classe de production hors du module ne dépend du runtime,
sauf `SqliteOperationRuntime` et `SqliteServerMaintenance`. Cette liste est fermée, et c'est AUD-ARC-01 qui la tranche.
Une source principale qui nomme une autre classe du runtime est refusée par le texte, qui voit aussi une constante
inlinée. Les classes publiques du runtime le sont pour les stores : ce n'est pas une autorisation pour les autres
modules.
> `SqliteStorePackagesArchitectureTest`

### `morpheus-api` : un rôle, un paquet, et six types pour les voisins (ADR-0147)
`remote` > `local` > routeurs (`routes.bodiless`, `routes.shareddecoder`, `routes.owncontext`) > services du paquet
parent > `transport` : une dépendance qui remonte cet ordre est un cycle, refusé par
`ApiPackageCyclesArchitectureTest`. Une nouvelle classe va dans le paquet de son rôle, un nouveau routeur dans le
paquet de son groupe. Les autres modules n'utilisent que `AllowedWorkspaceRoots`, `LoopbackHostPolicy`,
`MorpheusHttpServer`, `MorpheusRemoteHttpServer`, `MorpheusRemoteIdentityFile` et `MorpheusRemoteRole`
(`ApiEntryTypesArchitectureTest`, règle et texte) : un type public de l'adaptateur l'est pour ses propres paquets.
Une liste de classes de l'adaptateur qui reste dans les tests est un compteur de dette ou une table de faits,
déclarée dans `HttpAdapterClassListsArchitectureTest` ; une liste qui serait un rôle est un paquet.
> `noClassOutsideTheAdapterDependsOnATypeOfTheAdapterBesideTheEntryTypes`, `thePackagesOfTheHttpAdapterFormNoCycle`, `everyListOfTheAdaptersClassesIsADebtCounterOrATableOfFacts`

### `morpheus-cli` et `morpheus-mcp` : une commande, un outil, une classe de capacité (ADR-0149)
Une commande CLI va dans une classe de `com.morpheus.cli.command`, un outil MCP dans une classe de
`com.morpheus.mcp` qui déclare la lambda de son gestionnaire ; la classe sert une famille de
`contracts/public-surfaces.tsv`. `MorpheusMain`, `MorpheusCli`, `CliCommandPaths` (`com.morpheus.cli`) et
`MorpheusMcpServer` (`com.morpheus.mcp.server`) distribuent et ne nomment de l'application, d'un provider ou d'un
store que la liste autorisée exacte de leur adaptateur ; `MorpheusCli` ne sert que l'aide (et `version` appelé directement).
Le parseur des options, auquel chaque sous-commande déclare ce qu'elle accepte (`CommandOptions`), le cadre d'exécution
(`CliCommandRun`), la ligne d'erreur et le texte d'un échec (`CliErrorLine`), la lecture du jeton de commande
(`GlobalArgs`), les projections propres au MCP (`McpContentPayloads`) et le cadre des outils du catalogue
(`McpCatalogTool`) existent une fois. Les paquets de chaque adaptateur sont sans cycle ; les autres modules n'utilisent de
`morpheus-mcp` que `MorpheusMcpServer`.
> `DispatchersHoldNoUseCaseArchitectureTest`, `CliCommandDispatchIsExhaustiveTest`, `ServedToolsAreClaimedOnceTest`, `CliAndMcpPackageCyclesArchitectureTest`, `McpEntryTypesArchitectureTest`, `CliArgumentBaseArchitectureTest`, `CliOptionParsingRefusesUnknownOptionsTest`

### `morpheus-mcp-transport` ne dépend d'aucun autre paquet MORPHEUS
Le transport STDIO borné est partagé par le serveur MCP et les clients MINOS/NEXUS.
> `theMcpTransportDependsOnNoOtherMorpheusPackage`

### Les paquets du domaine sont sans cycle, à deux exceptions nommées
`ProviderProbeResult → SourceLocator` et `RequirementDelta → Scenario`, nommées par la paire de types exacte ; tout
autre cycle échoue. La même règle sur `application` est reportée (167 cycles mesurés le 08/10/2026, ADR-0109).
> `domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes`

### Pas de framework, pas de magie
Jamais Spring / Quarkus / Micronaut / Guice, jamais de réflexion, de classpath scanning
ou d'annotations d'injection. Le câblage est explicite : dans `MorpheusMain` pour le processus, dans les racines
de composition nommées d'ADR-0109 pour le runtime de chaque requête HTTP ou appel MCP.

## Ce que l'application touche (ADR-0150)

`com.morpheus.application..` ne dépend **jamais** de SQL (`java.sql`), d'une bibliothèque JSON (Jackson) ni de
l'exécution de processus. L'accès fichier, le réseau sortant, l'environnement du processus, les flux standard, les
fils d'exécution, l'horloge murale et la réflexion n'y sont atteints que par les classes que
`ApplicationTechnologyBoundaryArchitectureTest` nomme pour chaque famille — la liste est exacte, elle rétrécit, et un
nom ajouté exige un ADR consolidé qui remplace ADR-0150. Le test porte les listes de types et d'appels de chaque
famille : les lire là, pas ici. `Path` est du vocabulaire, pas un accès. Le seul nom marqué *sort* est le transport de
`UpdateDiscoveryService` (`move-marked-technology-access-behind-application-ports`).

> `ApplicationTechnologyBoundaryArchitectureTest` — `aFamilyTheApplicationDoesNotUseIsRefusedInEveryPackage`, `aConfinedFamilyIsReachedOnlyByTheClassesNamedForIt`, `everyNamedClassStillReachesItsFamily`

## Cœurs purs — interdits textuels (scannés dans les sources)

`application.reasoning..` ne doit contenir **aucune** occurrence de :
`java.net.http` · `tools.jackson` · `java.sql` · `ProcessBuilder` · `Runtime.getRuntime`
· `com.morpheus.application.store` · `com.morpheus.application.lifecycle.mutation`

`application.policy..` ne doit contenir **aucune** occurrence de :
`ScriptEngine` · `Class.forName` · `Runtime.getRuntime` · `ProcessBuilder` · `SELECT * FROM` · `executeQuery(`

> Le raisonnement et les policies sont des **noyaux déterministes** : pas de transport, pas de persistance, pas d'exécution.
