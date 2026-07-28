# Plugins de provider MORPHEUS

Statut : **M22 candidate**.

M22 ajoute une plateforme d’extensions provider par JAR. Un plugin provider est optionnel : MORPHEUS continue de fonctionner lorsqu’aucun répertoire de plugins n’existe.

## Principes de sécurité

```text
plugin discovery != plugin activation
provider metadata != executable trust
classloader isolation != security sandbox
optional provider absence != project failure
```

La commande `discover` inspecte uniquement les métadonnées du JAR. Elle n’exécute pas le plugin.

La commande `probe` est différente : elle charge explicitement le plugin compatible et exécute son probe sur le workspace fourni. **Ne probez que des JARs dont vous acceptez d’exécuter le code.**

## Découvrir les plugins

```powershell
morpheus --json provider-plugins discover --directory N:\morpheus-plugins
```

Linux :

```bash
morpheus --json provider-plugins discover --directory ~/morpheus-plugins
```

Résultats possibles :

```text
COMPATIBLE    métadonnées valides et plage MORPHEUS/SDK compatible
INCOMPATIBLE  plugin visible mais non activable par cette version
INVALID       JAR ou manifeste invalide
```

Un répertoire absent n’est pas une erreur fatale : MORPHEUS retourne zéro candidat et un diagnostic `PLUGIN_DIRECTORY_NOT_FOUND`.

## Tester un plugin sur un workspace

```powershell
morpheus --json provider-plugins probe `
  --directory N:\morpheus-plugins `
  --plugin my-provider-plugin `
  --workspace N:\workspace-dev\my-project
```

Le probe :

1. refait la discovery explicite ;
2. exige un candidat compatible ;
3. charge ce JAR dans un classloader dédié ;
4. vérifie que son identité runtime correspond au manifeste ;
5. appelle le `SpecificationProvider.probe(...)` ;
6. retourne les capabilities réellement observées.

Une panne plugin est transformée en diagnostic `PLUGIN_ACTIVATION_OR_PROBE_FAILED` et ne doit pas faire tomber le cœur MORPHEUS.

## MCP

Deux outils sont exposés :

```text
discover_provider_plugins(directory)
probe_provider_plugin(directory, pluginId, workspace)
```

Ils ne sont jamais appelés automatiquement par le serveur MCP.

## HTTP local

```text
GET /api/v1/provider-plugins/discover?directory=<path>
GET /api/v1/provider-plugins/probe?directory=<path>&pluginId=<id>&workspace=<path>
```

Ces routes sont locales et explicites. MORPHEUS ne scanne aucun répertoire de plugins au démarrage de l’API.

## Compatibilité

Chaque plugin déclare au minimum :

```text
plugin.id
provider.id
plugin.version
sdk.apiVersion
morpheus.minVersion
```

`morpheus.maxVersion` peut borner la version maximale supportée.

M22 supporte `sdk.apiVersion=1`.

Un plugin incompatible est **diagnostiqué**, pas chargé silencieusement.

## Où placer les plugins ?

M22 ne définit volontairement **aucun répertoire global implicite**. Vous passez le répertoire avec `--directory` ou le paramètre équivalent MCP/HTTP.

Cela conserve le fonctionnement local-first et évite qu’un simple démarrage MORPHEUS exécute ou même scanne du code tiers présent sur la machine.

## Provider de référence

Le dépôt source contient `morpheus-provider-reference`, un template de développement et de qualification. Il n’est pas embarqué comme provider built-in dans la distribution MORPHEUS.

Pour les auteurs : [`../developer/PROVIDER_SDK.md`](../developer/PROVIDER_SDK.md).
