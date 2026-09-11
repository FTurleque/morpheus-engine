# Règles — Architecture (ports & adapters)

Source de vérité : `morpheus-architecture-tests/.../LayerDependencyTest.java` + les `*ArchitectureTest` par milestone.
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

## Règle ArchUnit ou assertion textuelle — cf. ADR-0103

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

1. **Vérifier que la classe visée est dans l'ensemble importé.** `morpheus-provider-reference` et
   `morpheus-provider-testkit` n'y sont pas. Le garde-fou `archRule.failOnEmptyShould` est actif et
   fait échouer une règle qui ne retient aucune classe — **ne pas le désactiver**.
2. **Vérifier qu'un littéral n'est pas un préfixe de famille.** `contains("MorpheusRemote")` interdit
   toute une famille de types, pas un seul.
3. **Casser la règle pour prouver qu'elle tient** : introduire la violation, constater l'échec,
   revenir en arrière. Une règle vide passe aussi.

Découper par **intention**, jamais par classe de test : regrouper fait descendre le compte de
méthodes `@Test`, et `architectureTestsMinimum` ne se baisse pas (`rules/testing.md`).

**Généralisation décidée le 11/09/2026** (amendement d'ADR-0103), par groupe de capacité, jamais par
famille entière :

- les trois interdits vrais pour **tous** les routeurs (`MorpheusRemote*`, `MorpheusHttpResponseWriter`,
  `MorpheusHttpPathParser`) vivent dans `HttpRoutesFamilyArchitectureTest`, règle **et** texte — n'y
  ajouter qu'un interdit vérifié sur chacun des `*HttpRoutes` ;
- la frontière transport/JSON se règle par capacité, routeurs sans corps d'un côté, routeurs à corps de
  l'autre (DT-15 du registre des risques) ;
- les assertions visant des cibles sans famille (services, plomberie `LocalHttp*`, serveur remote) **ne
  migrent pas**.

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

### Pas de framework, pas de magie
Jamais Spring / Quarkus / Micronaut / Guice, jamais de réflexion, de classpath scanning
ou d'annotations d'injection. Le câblage est explicite dans `MorpheusMain`.

## Cœurs purs — interdits textuels (scannés dans les sources)

`application.reasoning..` ne doit contenir **aucune** occurrence de :
`java.net.http` · `tools.jackson` · `java.sql` · `ProcessBuilder` · `Runtime.getRuntime`
· `com.morpheus.application.store` · `com.morpheus.application.lifecycle.mutation`

`application.policy..` ne doit contenir **aucune** occurrence de :
`ScriptEngine` · `Class.forName` · `Runtime.getRuntime` · `ProcessBuilder` · `SELECT * FROM` · `executeQuery(`

> Le raisonnement et les policies sont des **noyaux déterministes** : pas de transport, pas de persistance, pas d'exécution.
