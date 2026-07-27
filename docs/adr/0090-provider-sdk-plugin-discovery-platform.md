# ADR-0090 — Provider SDK and explicit plugin discovery platform

Statut : **Proposée — M22**

Date : 27 juillet 2026

## Contexte

MORPHEUS possède déjà plusieurs providers intégrés et un contrat provider-neutral dans `domain` / `application`. Jusqu’à M21, ajouter un provider réel implique néanmoins de modifier le reactor ou le launcher, puis de republier MORPHEUS. M22 doit permettre l’extension par un JAR provider externe sans introduire de dépendance provider-specific dans `morpheus-domain` ou `morpheus-application`.

Le point de sécurité principal est de ne pas confondre découverte et exécution : inspecter un répertoire de plugins ne doit pas charger des classes inconnues. De même, un manifeste déclaratif n’est pas une preuve de confiance ni de comportement.

## Décision

### 1. SDK dédié

Un module `morpheus-provider-sdk` expose l’API publique de plugin. Il dépend des contrats provider-neutral existants de MORPHEUS mais `domain` et `application` ne dépendent jamais du SDK.

### 2. Métadonnées sans exécution

Chaque JAR plugin porte `META-INF/morpheus-provider.properties` avec au minimum :

```text
plugin.id
provider.id
plugin.version
sdk.apiVersion
morpheus.minVersion
```

`morpheus.maxVersion` est optionnel.

La découverte lit uniquement cette entrée ZIP. Aucun `ClassLoader`, `ServiceLoader`, constructeur ou code plugin n’est exécuté pendant `discover`.

### 3. Compatibilité explicite

M22 gèle `sdk.apiVersion=1`. Un plugin est activable uniquement si :

- son API SDK est exactement supportée ;
- la version MORPHEUS courante satisfait son intervalle déclaré ;
- son manifeste est valide.

Un plugin incompatible reste visible dans les diagnostics mais n’est jamais chargé silencieusement.

### 4. Activation séparée

L’activation est une opération explicite distincte de la découverte. Le JAR compatible est chargé dans un `URLClassLoader` dédié, parent-first vers les contrats MORPHEUS, puis résolu via `ServiceLoader<MorpheusProviderPlugin>`.

Le plugin doit exposer exactement un service et le `provider.id` du provider créé doit correspondre au manifeste. Un échec de chargement reste borné au plugin et devient un diagnostic ; il ne doit pas faire tomber le core.

### 5. Isolation M22

M22 retient **un classloader dédié par JAR plugin, en processus**. Cela isole les dépendances des plugins entre elles tout en conservant les types de contrats partagés via le parent MORPHEUS.

L’isolation par processus n’est pas retenue pour M22 : elle exigerait un protocole RPC provider complet, alors que le contrat actuel est objet/in-process. Elle reste une évolution possible pour des plugins non fiables. Un classloader n’est pas une frontière de sécurité.

### 6. Capability negotiation

La compatibilité du plugin ne vaut pas capacité métier. Après activation explicite, le provider est sondé via le contrat `SpecificationProvider.probe(...)`; seules les `ProviderCapability` réellement retournées pour la source sont utilisées par les politiques de sélection existantes.

### 7. Plugin de référence et test kit

Un module `morpheus-provider-reference` sert de template vérifiable mais n’est pas une dépendance runtime du launcher. Un module `morpheus-provider-testkit` fournit des assertions/contrats réutilisables par les auteurs de providers.

### 8. Surfaces publiques

CLI, MCP et HTTP exposent la découverte explicite et le diagnostic. Aucun scan de plugin n’est effectué au démarrage. L’activation/probe reste une commande explicite avec répertoire et workspace fournis par l’appelant.

## Invariants

```text
provider plugin != domain dependency
plugin discovery != plugin activation
optional provider absence != project failure
incompatible provider != silently loaded provider
provider metadata != executable trust
plugin failure != core crash
capability declaration != capability implementation proof
classloader isolation != security sandbox
local-first remains default
```

## Conséquences

Positives :

- providers externes ajoutables sans recompilation du core ;
- découverte déterministe sans exécution de code ;
- compatibilité et diagnostics explicites ;
- dépendances plugin isolées entre JARs ;
- test kit réutilisable.

Coûts :

- nouvelle surface SDK à maintenir de manière compatible ;
- métadonnées dupliquées entre manifeste et objet plugin puis vérifiées à l’activation ;
- classloader in-process insuffisant pour traiter du code non fiable.

## Validation requise avant Acceptée

- reactor complet avec les nouveaux modules ;
- tests discovery sans activation ;
- tests compatibilité valide/invalide ;
- activation ServiceLoader d’un JAR de référence externe ;
- mismatch manifeste/provider rejeté ;
- capability probe/negotiation démontré ;
- absence de dépendance SDK/plugin dans `domain` et `application` ;
- CLI/MCP/HTTP explicites ;
- packaging MORPHEUS sans embarquer le provider de référence comme provider intégré ;
- qualification Windows + Linux exact-head.
