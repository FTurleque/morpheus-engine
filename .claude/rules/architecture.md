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
