# ADR-0090 — Provider SDK and explicit plugin discovery platform

Statut : **Acceptée — M22**

Date : 27 juillet 2026  
Acceptée : 28 juillet 2026 après qualification Windows + Linux exact-head sur `e42bc31384831e56592b11a3509b49a3fdf61773`.

## Contexte

MORPHEUS possède déjà plusieurs providers intégrés et des contrats provider-neutral dans `domain` / `application`. Jusqu’à M21, ajouter un provider réel implique néanmoins de modifier le reactor ou le launcher, puis de republier MORPHEUS. M22 doit permettre l’extension par un JAR provider externe sans introduire de dépendance provider-specific dans `morpheus-domain` ou `morpheus-application`.

Le point de sécurité principal est de ne pas confondre découverte et exécution : inspecter un répertoire de plugins ne doit pas charger des classes inconnues. De même, un manifeste déclaratif n’est pas une preuve de confiance ni de comportement.

M22 doit également respecter ADR-0028 : le sondage d’un provider et la lecture de contenu normalisé sont deux opérations différentes.

## Décision

### 1. SDK dédié et provider-neutral

Un module `morpheus-provider-sdk` expose l’API publique de plugin. Il dépend des contrats provider-neutral existants de MORPHEUS mais `domain` et `application` ne dépendent jamais du SDK.

Le SPI `MorpheusProviderPlugin` fournit explicitement :

```text
ProviderPluginMetadata
SpecificationProvider
SpecificationContentReader
```

Ainsi :

```text
probe != read
provider plugin != domain dependency
```

`SpecificationProvider` porte le probe/capability negotiation. `SpecificationContentReader` porte la lecture normalisée réelle via `ProviderReadRequest` / `ProviderReadResult`.

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

La comparaison des versions respecte les règles SemVer utiles au contrat, y compris l’ordre numérique des identifiants de prerelease.

Un plugin incompatible reste visible dans les diagnostics mais n’est jamais chargé silencieusement.

### 4. Activation séparée

L’activation est une opération explicite distincte de la découverte. Le JAR compatible est chargé dans un `URLClassLoader` dédié, parent-first vers les contrats MORPHEUS, puis résolu via `ServiceLoader<MorpheusProviderPlugin>`.

Le JAR doit exposer exactement un service. L’activation vérifie :

```text
manifest metadata == plugin.metadata()
manifest provider.id == SpecificationProvider.id()
manifest provider.id == SpecificationContentReader.providerId()
```

Un doublon de `plugin.id` entre plusieurs JARs est une ambiguïté explicite et aucun premier candidat n’est choisi silencieusement.

Un échec de chargement reste borné au plugin et devient un diagnostic ; il ne doit pas faire tomber le core.

### 5. Isolation M22

M22 retient **un classloader dédié par JAR plugin, en processus**. Cela isole les dépendances des plugins entre elles tout en conservant les types de contrats partagés via le parent MORPHEUS.

L’isolation par processus n’est pas retenue pour M22 : elle exigerait un protocole RPC provider complet, alors que le contrat actuel est objet/in-process. Elle reste une évolution possible pour des plugins non fiables. Un classloader n’est pas une frontière de sécurité.

### 6. Capability negotiation puis lecture normalisée

La compatibilité du plugin ne vaut pas capacité métier. Après activation explicite, le provider est sondé via `SpecificationProvider.probe(...)`; seules les `ProviderCapability` réellement retournées pour la source sont considérées comme observées.

Une capacité de lecture annoncée par le probe n’est pas le contenu lui-même. La lecture réelle passe ensuite par le `SpecificationContentReader` provider-neutral :

```text
compatibility != supported workspace
capability declaration != capability implementation proof
probe != read
```

Le provider externe de référence M22 démontre `READ_CURRENT_SPECIFICATIONS` et produit réellement une `Specification`, une `Evidence` et une `Provenance` normalisées via ce port.

### 7. Plugin de référence et test kit

Un module `morpheus-provider-reference` sert de template vérifiable mais n’est pas une dépendance runtime du launcher. Il expose un vrai `SpecificationProvider` et un vrai `SpecificationContentReader`.

Un module `morpheus-provider-testkit` fournit des assertions réutilisables par les auteurs de providers : cohérence des identités, déterminisme du probe et présence du reader provider-neutral.

### 8. Surfaces publiques

CLI, MCP et HTTP exposent la découverte explicite et le diagnostic/probe. Aucun scan de plugin n’est effectué au démarrage. L’activation/probe reste une commande explicite avec répertoire et workspace fournis par l’appelant.

La lecture normalisée est une capacité SDK/host ; M22 ne remplace pas silencieusement le flux historique `sync` par un plugin découvert. L’intégration d’un plugin dans un workflow métier doit rester une décision explicite du host.

## Invariants

```text
provider plugin != domain dependency
plugin discovery != plugin activation
optional provider absence != project failure
incompatible provider != silently loaded provider
provider metadata != executable trust
plugin failure != core crash
capability declaration != capability implementation proof
probe != read
classloader isolation != security sandbox
local-first remains default
```

## Conséquences

Positives :

- providers externes ajoutables sans recompilation du core ;
- découverte déterministe sans exécution de code ;
- compatibilité et diagnostics explicites ;
- dépendances plugin isolées entre JARs ;
- lecture normalisée réelle derrière un port provider-neutral ;
- test kit réutilisable.

Coûts :

- nouvelle surface SDK à maintenir de manière compatible ;
- métadonnées dupliquées entre manifeste et objet plugin puis vérifiées à l’activation ;
- le host doit décider explicitement quand utiliser un reader plugin ;
- classloader in-process insuffisant pour traiter du code non fiable.

## Validation acquise

- reactor complet avec les nouveaux modules : **PASS Windows + Linux** ;
- tests discovery sans activation : **PASS** ;
- tests compatibilité valide/invalide et SemVer : **PASS** ;
- activation ServiceLoader d’un JAR de référence externe : **PASS** ;
- mismatch manifeste/provider/reader rejeté : **PASS** ;
- ambiguïté duplicate `plugin.id` rejetée : **PASS** ;
- capability probe/negotiation démontré : **PASS** ;
- lecture provider-neutral réelle du JAR externe démontrée : **PASS** ;
- absence de dépendance SDK/plugin dans `domain` et `application` : **PASS** ;
- CLI/MCP/HTTP explicites : **PASS** ;
- packaging MORPHEUS sans embarquer le provider de référence comme provider intégré : **PASS Windows + Linux** ;
- qualification Windows + Linux exact-head locale : **PASS sur `e42bc31384831e56592b11a3509b49a3fdf61773`** ;
- tests : **494 PASS** ;
- architecture : **190 PASS** ;
- `postGateExecutableDelta=NONE` : **PASS Windows + Linux**.

Preuve : [`../validation/VALIDATION_M22.md`](../validation/VALIDATION_M22.md).