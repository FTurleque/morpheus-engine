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

Lorsqu’un SHA-256 de confiance est fourni, MORPHEUS copie le JAR dans un staging privé, vérifie le digest sur cette copie puis charge uniquement cette copie. Remplacer le fichier d’origine après la vérification ne modifie donc pas le code effectivement exécuté.

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

Pour un usage approuvé, épinglez le digest calculé depuis une source de confiance :

```powershell
morpheus --json provider-plugins probe `
  --directory N:\morpheus-plugins `
  --plugin my-provider-plugin `
  --workspace N:\workspace-dev\my-project `
  --sha256 <64-hex>
```

Le probe :

1. refait la discovery explicite ;
2. exige un candidat compatible ;
3. vérifie le SHA-256 fourni lorsqu’un pin est présent ;
4. charge le JAR vérifié dans un classloader dédié ;
5. vérifie que son identité runtime correspond au manifeste ;
6. appelle le `SpecificationProvider.probe(...)` ;
7. retourne les capabilities réellement observées.

Le mode local historique sans `--sha256` reste disponible pour des usages explicitement non approuvés. Il ne transforme jamais des métadonnées en preuve de confiance.

Une panne plugin est transformée en diagnostic `PLUGIN_ACTIVATION_OR_PROBE_FAILED` et ne doit pas faire tomber le cœur MORPHEUS.

## MCP local

Deux outils sont exposés :

```text
discover_provider_plugins(directory)
probe_provider_plugin(directory, pluginId, workspace, sha256?)
```

Ils ne sont jamais appelés automatiquement par le serveur MCP. Pour une activation approuvée, fournissez `sha256`.

## HTTP local

```text
GET /api/v1/provider-plugins/discover?directory=<path>
GET /api/v1/provider-plugins/probe?directory=<path>&pluginId=<id>&workspace=<path>&sha256=<64-hex>
```

Le `sha256` reste optionnel sur cette surface locale historique. Ces routes sont locales et explicites ; MORPHEUS ne scanne aucun répertoire de plugins au démarrage de l’API.

## HTTP remote

Le serveur d’équipe durcit volontairement le contrat du probe exécutable :

```text
discovery  GET  /api/v1/provider-plugins/discover               -> READ
probe      POST /api/v1/provider-plugins/probe?...&sha256=...    -> ADMIN
```

En remote :

- `sha256` est **obligatoire** ;
- le client ne peut pas sélectionner le répertoire de plugins ;
- le serveur injecte son `--provider-plugin-dir` configuré ;
- le workspace doit appartenir à `AllowedWorkspaceRoots` ;
- un probe sans pin retourne `PLUGIN_SHA256_REQUIRED` ;
- le JAR épinglé est chargé depuis sa copie de staging vérifiée.

Ce changement de forme est intentionnel : `surface parity != same transport shape`. Le probe reste la même capability, mais l’exposition réseau exige une autorisation et une preuve d’intégrité plus fortes.

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

En local, M22 ne définit volontairement **aucun répertoire global implicite**. Vous passez le répertoire avec `--directory` ou le paramètre équivalent MCP/HTTP.

En remote, le répertoire est au contraire une configuration **du serveur**, jamais une entrée choisie par la requête cliente.

Ces règles conservent le fonctionnement local-first et évitent qu’un simple démarrage MORPHEUS exécute ou même scanne du code tiers présent sur la machine.

## Provider de référence

Le dépôt source contient `morpheus-provider-reference`, un template de développement et de qualification. Il n’est pas embarqué comme provider built-in dans la distribution MORPHEUS.

Pour les auteurs : [`../developer/PROVIDER_SDK.md`](../developer/PROVIDER_SDK.md).
