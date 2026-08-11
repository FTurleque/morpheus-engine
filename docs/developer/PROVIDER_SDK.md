# Provider SDK — développer un plugin MORPHEUS

Statut : **M22 candidate** — SDK API `1`.

MORPHEUS M22 permet de livrer un provider comme JAR externe sans modifier `morpheus-domain`, `morpheus-application` ni le launcher.

## Frontière d’architecture

```text
external provider JAR
        |
        v
morpheus-provider-sdk
        |
        +--> SpecificationProvider       -> probe / capabilities
        |
        +--> SpecificationContentReader  -> normalized reads
        |
        v
provider-neutral application/domain contracts
```

Invariants :

```text
provider plugin != domain dependency
plugin discovery != plugin activation
provider metadata != executable trust
capability declaration != capability implementation proof
probe != read
classloader isolation != security sandbox
plugin failure != core crash
```

`morpheus-domain` et `morpheus-application` ne dépendent jamais du SDK. Le SDK dépend au contraire des ports provider-neutral existants afin qu’un plugin puisse fournir un probe et une lecture normalisée sans type métier provider-specific dans le core.

## 1. Dépendance Maven

Un provider externe compile contre :

```xml
<dependency>
  <groupId>io.github.fturleque</groupId>
  <artifactId>morpheus-provider-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

Le template maintenu dans le dépôt est `morpheus-provider-reference`.

## 2. SPI

Implémenter :

```java
public interface MorpheusProviderPlugin {
    ProviderPluginMetadata metadata();
    SpecificationProvider createProvider();
    SpecificationContentReader createContentReader();
}
```

Les deux ports sont volontairement distincts :

```text
SpecificationProvider.probe() != SpecificationContentReader.read()
```

Le `SpecificationProvider` porte l’identité, la version, le caractère local/remote et le `probe(Path)`. Le `SpecificationContentReader` produit un `ProviderReadResult` normalisé à partir d’un `ProviderReadRequest` et d’un `EntityIdentityResolver` fournis par le host.

## 3. Métadonnées déclaratives obligatoires

Le JAR doit contenir :

```text
META-INF/morpheus-provider.properties
```

Exemple :

```properties
plugin.id=my-provider-plugin
provider.id=my-provider
plugin.version=1.0.0
sdk.apiVersion=1
morpheus.minVersion=1.0.0
# morpheus.maxVersion=1.9.0   # optionnel
```

Contraintes M22 :

- `plugin.id` stable, minuscule, borné à 128 caractères ;
- `provider.id` doit correspondre au provider **et** au content reader réellement créés ;
- `plugin.version`, min/max MORPHEUS utilisent une version `x.y.z` avec prerelease/build optionnels ;
- `sdk.apiVersion` doit être exactement `1` pour M22.

Le manifeste est une déclaration de compatibilité, **pas une signature ni une preuve de confiance**.

## 4. ServiceLoader

Le JAR doit contenir :

```text
META-INF/services/com.morpheus.sdk.provider.MorpheusProviderPlugin
```

avec exactement une classe d’implémentation.

M22 exige exactement un `MorpheusProviderPlugin` par JAR. Cette règle conserve une unité de diagnostic, de compatibilité et d’isolation claire.

## 5. Discovery

`ProviderPluginDiscovery` :

- ne scanne qu’un répertoire explicitement fourni ;
- ne descend pas récursivement ;
- ne considère que les fichiers `.jar` ;
- trie les JARs par nom ;
- inspecte au plus 256 JARs ;
- refuse un JAR supérieur à 64 MiB ;
- lit au plus 16 Kio de métadonnées ;
- utilise `JarFile` uniquement ;
- ne crée aucun `ClassLoader` / `ServiceLoader`.

Donc :

```text
discover != activate
```

Un répertoire absent d’extensions optionnelles produit une liste vide + diagnostic, pas une panne projet.

Deux JARs déclarant le même `plugin.id` ne sont jamais départagés silencieusement : le probe retourne `PLUGIN_ID_AMBIGUOUS` et n’active aucun des deux.

## 6. Compatibilité

Avant activation :

```text
sdk.apiVersion == runtime API version
morpheus.minVersion <= runtime
runtime <= morpheus.maxVersion, si max déclaré
```

La comparaison respecte l’ordre SemVer utile au contrat, y compris l’ordre numérique des identifiants de prerelease (`rc.10 > rc.2`).

Un plugin incompatible reste visible avec ses diagnostics mais n’est pas activé.

## 7. Activation et isolation

`ProviderPluginActivator` crée un `URLClassLoader` dédié au JAR, avec le classloader MORPHEUS/SDK comme parent. Le service est ensuite résolu explicitement via `ServiceLoader`.

L’activation vérifie :

```text
exactement 1 service
manifest metadata == plugin.metadata()
manifest provider.id == provider.id()
manifest provider.id == contentReader.providerId()
```

Le handle `ProviderPluginActivation` expose le `SpecificationProvider` et le `SpecificationContentReader`. Il est `AutoCloseable` et ferme son classloader.

Cette isolation évite que les dépendances propres à deux plugins soient mélangées. Elle **n’est pas un sandbox de sécurité**. Exécuter du code non fiable demanderait une frontière process/OS distincte, différée au-delà de M22.

## 8. Probe, capabilities et lecture

Les métadonnées plugin ne déclarent pas une capacité métier comme un fait acquis. La vérité opérationnelle commence par :

```java
ProviderProbeResult probe(Path workspaceRoot)
```

et son `ProviderCapabilitySet`.

Une capacité de lecture observée n’est cependant pas le contenu lui-même. Le host appelle ensuite :

```java
ProviderReadResult read(
    ProviderReadRequest request,
    EntityIdentityResolver identityResolver)
```

sur le `SpecificationContentReader`.

Ainsi :

```text
compatibility != supported workspace
metadata != capability proof
probe != read
```

Le reader doit respecter les catégories explicitement demandées, retourner des `ReadCategoryReport` complets et produire du `NormalizedProjectContent` provider-neutral.

Toute lecture d'un fichier relatif au workspace passe par
`SafeWorkspaceFileResolver`. Cette frontière partagée refuse les chemins absolus,
les composants `..`, les symlinks et junctions, vérifie le confinement du real
path et revalide l'identité du fichier après lecture. Les providers ne doivent
pas réimplémenter ce contrôle avec un simple `normalize().startsWith(...)` ni
utiliser directement les méthodes de lecture `Files.*` sur un chemin fourni par
le contenu du workspace. Le locator et la provenance restent relatifs au
workspace, même si la primitive lit le chemin canonique.

## 9. Identités et provenance

Un plugin ne doit pas fabriquer une identité MORPHEUS à partir d’un simple chemin. Le host transmet un `EntityIdentityResolver`. Le provider l’utilise avec son `ProviderId`, le type d’entité et son identifiant externe stable.

Pour les données normalisées, la provenance doit conserver le `ProviderId`, la source et l’évidence réellement observée. Le template M22 montre ce flux avec une `Specification`, une `Evidence` et une `Provenance`.

## 10. Test kit

`morpheus-provider-testkit` fournit `ProviderPluginContractAssertions.verify(...)`.

Il vérifie notamment :

- version SDK ;
- cohérence metadata/provider ID ;
- cohérence metadata/content-reader ID ;
- version provider non vide ;
- probe déterministe sur workspace inchangé ;
- cohérence provider ID/version/remote entre provider et probe.

Le provider `morpheus-provider-reference` consomme réellement ce test kit, puis son propre test exécute également une lecture normalisée.

## 11. Provider de référence

Le template M22 :

```text
morpheus-provider-reference/
  pom.xml
  src/main/java/.../ReferenceProviderPlugin.java
  src/main/java/.../ReferenceSpecificationProvider.java
  src/main/java/.../ReferenceSpecificationContentReader.java
  src/main/resources/META-INF/morpheus-provider.properties
  src/main/resources/META-INF/services/com.morpheus.sdk.provider.MorpheusProviderPlugin
```

Il reconnaît un workspace contenant `morpheus-reference.spec` et expose :

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
```

Sa lecture produit réellement une specification `reference-current` ainsi que son evidence/provenance normalisées.

Ce module est construit par le reactor à des fins de preuve/template, mais **n’est pas une dépendance du CLI/runtime MORPHEUS**.

## 12. Surfaces de diagnostic

CLI :

```text
morpheus --json provider-plugins discover --directory <plugins>
morpheus --json provider-plugins probe --directory <plugins> --plugin <pluginId> --workspace <workspace>
```

MCP :

```text
discover_provider_plugins
probe_provider_plugin
```

HTTP local :

```text
GET /api/v1/provider-plugins/discover?directory=...
GET /api/v1/provider-plugins/probe?directory=...&pluginId=...&workspace=...
```

Aucune de ces opérations n’est appelée au démarrage.

Ces surfaces exposent discovery/probe. Elles ne remplacent pas implicitement le flux `sync` historique. L’utilisation du `SpecificationContentReader` par un workflow métier est une décision explicite du host.

## 13. Gate M22

Windows :

```powershell
.\validate-m22.cmd -Version 1.0.0
```

Linux :

```bash
./scripts/validate-m22.sh 1.0.0
```

Le reactor/architecture gate charge le JAR externe, exécute son probe puis son `SpecificationContentReader`. Le packaging gate exige que le SDK soit dans le runtime packagé, que `ReferenceProviderPlugin` n’y soit pas, puis copie le JAR de référence comme véritable plugin externe et exécute discovery + activation + probe.

Avant août, la qualification M22 est locale exact-head uniquement ; le workflow GitHub Actions n’est pas utilisé comme preuve.

ADR : [`../adr/0090-provider-sdk-plugin-discovery-platform.md`](../adr/0090-provider-sdk-plugin-discovery-platform.md).
