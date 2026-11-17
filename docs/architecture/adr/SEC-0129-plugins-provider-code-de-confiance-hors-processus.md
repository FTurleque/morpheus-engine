---
id: SEC-0129
title: "Un plugin provider est du code de confiance, sondé hors du processus MORPHEUS"
domain: security
rules: true
files: ["distribution/build-portable.ps1", "distribution/build-portable.sh"]
---

# Un plugin provider est du code de confiance, sondé hors du processus MORPHEUS

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0090, ADR-0098, ADR-0101

## Context

MORPHEUS doit pouvoir être étendu par un JAR provider externe sans recompiler le cœur et sans introduire de
dépendance spécifique à un provider dans `morpheus-domain` ou `morpheus-application`. Il exécute ainsi deux catégories
de code qu'il n'a pas écrit : un **plugin provider** activé explicitement par un opérateur, et un **pair MCP** (MINOS,
NEXUS) dont l'opérateur a configuré la commande de lancement.

Trois confusions sont à éviter. Inspecter un répertoire de plugins n'est pas exécuter du code. Un manifeste déclaratif
n'est ni une preuve de confiance ni une preuve de comportement. Sonder un provider n'est pas lire son contenu normalisé
(ADR-0028).

Les contrôles qui entourent ce code — pin SHA-256, copie de vérification, environnement réduit, délai borné,
terminaison du sous-arbre, projection remote sur liste blanche — peuvent faire croire que MORPHEUS isole du code non
fiable. Ce n'est pas le cas. Un plugin approuvé s'exécute sous le compte système de MORPHEUS, avec ses droits fichiers
et réseau. Le pin garantit que le code exécuté est exactement celui qui a été approuvé ; il ne dit rien de ce que ce
code fait. Un opérateur qui croirait à une isolation exécuterait un code auquel il ne fait pas confiance en s'appuyant
sur une garantie qui n'existe pas.

Options écartées :

- **Présenter la frontière de processus comme une sandbox** : faux, et dangereux dans le seul sens qui compte.
- **Restreindre le plugin par un `SecurityManager`** : retiré de la plateforme Java ; une réimplémentation partielle
  donnerait la même fausse assurance.
- **Lancer le probe dans un conteneur** : la distribution n'utilise pas Docker (ADR-0134) ; l'exiger rendrait
  le probe indisponible sur une installation locale standard.
- **Restreindre le processus enfant par ACL sur chaque OS** : non portable, non testable de façon reproductible sur les
  deux plateformes supportées.
- **Chercher la JVM enfant dans le `PATH`** : un `java` substituable rendrait le pin sans valeur, l'interpréteur
  devenant le vecteur.
- **Déclarer le probe non supporté dans la distribution packagée** : `provider.plugins.probe` est une surface publique
  de `contracts/public-surfaces.tsv` ; la retirer pour une raison de packaging réduirait le contrat.
- **Ne rien documenter, puisque le code est correct** : l'invariant porté ici est une absence de garantie, et une
  absence ne se déduit pas du code.

Le SPI `MorpheusProviderPlugin` dépend des contrats provider-neutral de MORPHEUS et fournit `ProviderPluginMetadata`,
un `SpecificationProvider` qui sonde et négocie les capacités (`ProviderCapability`) et un `SpecificationContentReader`
qui lit par `ProviderReadRequest` / `ProviderReadResult`. Le manifeste porte au minimum `plugin.id`, `provider.id`,
`plugin.version`, `sdk.apiVersion` et `morpheus.minVersion` ; `morpheus.maxVersion` est facultatif. Le parent vérifie
le pin avant de lancer la JVM enfant, qu'il arrête au délai gracieusement puis de force ; un `plugin.id` déclaré par
plusieurs JAR n'en active aucun. Les refus d'activation et de probe sont des diagnostics bornés au plugin, qui ne font
jamais tomber MORPHEUS.
`morpheus-provider-reference` sert de modèle vérifiable ; `morpheus-provider-testkit` fournit aux auteurs de providers
des assertions réutilisables (cohérence des identités, déterminisme du probe, présence du reader). La découverte
s'appuie sur `LinkOption.NOFOLLOW_LINKS` et `Files.isSymbolicLink`, et résout un ancêtre lié par `toRealPath()`. La
compatibilité compare les versions en ordre SemVer, identifiants de prerelease comparés numériquement.

Le `URLClassLoader` dédié du JAR est parent-first vers les contrats MORPHEUS. Les scripts du build portable passent à
`jpackage` les options `jlink` par défaut moins `--strip-native-commands` (`--strip-debug --no-man-pages
--no-header-files`) ; l'installeur Windows réutilise l'app-image validée et hérite de ce runtime. L'absence réelle de
JVM enfant reste un échec explicite du probe. L'environnement de l'enfant est reconstruit, jamais filtré depuis celui du
parent. Intégrer un plugin à un workflow métier est une décision explicite de l'hôte.

L'exposition par transport :

| Capacité | CLI | MCP | HTTP |
|---|---|---|---|
| `provider.plugins.discover` | `provider-plugins discover` | `EXPLICITLY_NOT_EXPOSED` | `GET /api/v1/provider-plugins/discover` |
| `provider.plugins.probe` | `provider-plugins probe` (pin exigé) | `EXPLICITLY_NOT_EXPOSED` | `POST /api/v1/provider-plugins/probe`, remote seulement, rôle `ADMIN` |

La projection remote sur liste blanche, jamais un modèle interne filtré, empêche un texte écrit par le plugin de
révéler un emplacement du serveur. Le câblage MCP par défaut ne sert aucun outil de plugin.

Le modèle de confiance est binaire :

```text
un plugin provider activé explicitement    = code de confiance
un pair MCP explicitement configuré        = code de confiance
```

L'approbation est un acte d'opérateur, pas une propriété que MORPHEUS peut vérifier. Pour exécuter du code tiers non
fiable, la réponse de MORPHEUS est : ne pas le faire ici — isoler MORPHEUS ou le processus externe avec un conteneur,
un job object ou un cgroup, sous un compte dédié de moindre privilège.

Ce que la frontière garantit :

| Garantie | Mécanisme |
|---|---|
| Le code exécuté est celui qui a été approuvé | pin SHA-256 exigé à l'activation, revérifié dans l'enfant |
| Le chemin approuvé ne peut pas être substitué entre vérification et chargement | copie de staging vérifiée, seule exposée au classloader |
| La découverte n'exécute rien | métadonnées seules, `NOFOLLOW_LINKS`, aucun classloading |
| Un probe ne tourne pas indéfiniment | délai borné, terminaison gracieuse puis forcée |
| Un probe ne laisse pas de processus derrière lui | le worker termine son sous-arbre, le parent l'arbre observé |
| Un probe n'hérite pas des secrets d'environnement | liste blanche de lancement |
| Le modèle ne peut pas déclencher l'exécution d'un plugin | probe `EXPLICITLY_NOT_EXPOSED` en MCP, remote et `ADMIN` en HTTP |
| Un appelant remote ne choisit pas le répertoire de plugins | répertoire imposé côté serveur |
| Une exception de plugin ne traverse pas la frontière remote | projection sur liste blanche |

Ce que la frontière ne garantit pas :

```text
PAS une sandbox du système d'exploitation
PAS une limite sur les fichiers que le code approuvé peut lire ou écrire
PAS une limite sur le réseau que le code approuvé peut joindre
PAS une garantie de terminaison des descendants d'un pair MCP (best-effort)
```

Un classloader n'est pas une frontière de sécurité. La terminaison des descendants est garantie pour un probe de
plugin parce que le worker est du code MORPHEUS et termine son sous-arbre quand celui-ci est encore énumérable ; un
pair MCP n'est pas du code MORPHEUS. Une véritable sandbox OS serait explicite, testée, fail-closed, portable ou
documentée comme limitée à une plateforme.

## Decision

1. **SDK** : `morpheus-provider-sdk` expose le SPI `MorpheusProviderPlugin`. Ni `morpheus-domain`, ni
   `morpheus-application` (POM compris), ni le launcher ne dépendent du SDK ou de `morpheus-provider-reference`.
2. **Découverte** : explicite, jamais au démarrage ; lit le seul `META-INF/morpheus-provider.properties`, sans
   classloading ni processus ; répertoire lié refusé, JAR lié ignoré, ancêtre lié déclaré
   (`PLUGIN_DIRECTORY_PATH_RESOLVED`).
3. **Compatibilité** : `sdk.apiVersion` = `ProviderSdk.API_VERSION`, version dans l'intervalle, manifeste valide,
   sinon non chargé.
4. **Activation** : pin SHA-256 exigé (« provider plugin activation requires a trusted SHA-256 pin »), revérifié par
   la JVM enfant ; seule la copie de staging vérifiée (propriétaire seul) va au `URLClassLoader` dédié, puis à
   `ServiceLoader<MorpheusProviderPlugin>`. Un service ; manifeste, `plugin.metadata()`, `SpecificationProvider.id()`
   et `SpecificationContentReader.providerId()` concordent ; `plugin.id` en double : `PLUGIN_ID_AMBIGUOUS`.
5. **JVM enfant** : `java.home/bin/java` (gardé dans le build portable), jamais le `PATH`. Environnement en
   liste blanche sans `MORPHEUS_SERVER_TLS_PASSWORD` ni injection JVM ; délai
   `ProviderPluginProbeProcess.DEFAULT_TIMEOUT` ; le worker tue son sous-arbre, le parent l'arbre observé.
6. **Capacités** : observées par `probe(...)` seul ; lecture par `SpecificationContentReader` ; `sync` jamais
   remplacé.
7. **Exposition** : rien en MCP ; probe remote, `ADMIN` (« provider-plugin probe is remote-only ») ; répertoire et
   workspaces imposés par le serveur ; réponse en liste blanche.
8. **Confiance** : plugin activé, pair MCP configuré = code de confiance ; frontière de processus et
   d'environnement, sans limite fichiers ni réseau ; descendants d'un pair MCP en best-effort. Rien qui imite une
   sandbox OS, qui exigerait son propre ADR.

## Do's and Don'ts

### Do

- Exiger un pin SHA-256 pour toute activation, et le revérifier dans la JVM enfant avant le chargement.
- Ne charger que la copie de staging vérifiée, jamais le chemin d'origine.
- Résoudre la JVM enfant depuis `java.home` seulement, et garder `bin/java` dans le runtime embarqué.
- Reconstruire l'environnement de l'enfant depuis une liste blanche.
- Garder la découverte métadonnées seules, sans classloading, sans processus, sans suivre les liens symboliques.
- Garder le probe `EXPLICITLY_NOT_EXPOSED` en MCP, remote seulement et `ADMIN` en HTTP.
- Décrire la frontière comme une frontière de processus et d'environnement, et le code approuvé comme code de confiance.

### Don't

- Ne pas activer un plugin sans pin, ni à la découverte, ni au démarrage.
- Ne pas chercher `java` dans le `PATH`, ni retirer `--strip-native-commands` de la liste des options exclues.
- Ne pas transmettre `MORPHEUS_SERVER_TLS_PASSWORD` ni une variable d'injection JVM à l'enfant.
- Ne pas laisser un appelant MCP ou remote choisir le répertoire de plugins.
- Ne pas faire dépendre `morpheus-domain` ou `morpheus-application` du SDK ou du plugin de référence.
- Ne pas embarquer `morpheus-provider-reference` dans `morpheus-cli`.
- Ne jamais présenter la frontière comme une sandbox, ni promettre la terminaison des descendants d'un pair MCP.

## Consequences

### Positives

- Un provider externe s'ajoute sans recompiler le cœur, derrière un port provider-neutral.
- La découverte est déterministe et n'exécute aucun code ; compatibilité et échecs sont des diagnostics explicites.
- Le probe de la distribution packagée fonctionne avec la même résolution de JVM que dans le build.
- L'opérateur sait exactement ce que la frontière garantit et ce qu'elle ne garantit pas.

### Négatives

- Le SDK est une surface publique à maintenir de façon compatible.
- Les métadonnées sont dupliquées entre manifeste et objet plugin, puis comparées à l'activation.
- L'hôte doit décider explicitement quand utiliser un reader de plugin.
- Le runtime embarqué est légèrement plus gros, puisqu'il garde ses commandes natives.

### Risques

- Un plugin approuvé peut lire, écrire et joindre tout ce que le compte MORPHEUS atteint : le pin n'en limite rien.
- Un descendant d'un pair MCP créé puis orphelin entre deux observations peut survivre ; le trou est documenté dans
  `SECURITY.md`.
- **Question ouverte** : trois outils MCP — `resolve_external_reference`, `get_augmented_requirement_context` et
  `get_augmented_change_context` — lancent à la demande du modèle le pair MCP que l'opérateur a configuré (MINOS
  ou NEXUS), comme le disent leurs lignes de `contracts/public-surfaces.tsv`. La garantie « le modèle ne peut pas
  déclencher l'exécution de code tiers » ne porte que sur le probe de plugin. L'exposition de ces trois outils au
  modèle est une décision de produit à prendre.

## Compliance and Enforcement

- Règle Archgate `SEC-0129-plugins-provider-code-de-confiance-hors-processus.rules.ts` : `embedded-runtime-keeps-native-commands`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m22/ProviderPluginTrustBoundaryContractTest.java` :
  pin exigé et revérifié par l'enfant, copie de staging seule chargée, découverte métadonnées seules et sans lien
  symbolique, environnement sur liste blanche, délai et terminaison du sous-arbre, probe absent de MCP et refusé en
  local, répertoire MCP non choisi par l'appelant, et refus de toute formulation de sandbox dans `SECURITY.md`,
  `docs/developer/PROVIDER_SDK.md`, `docs/developer/MCP.md`, ce registre et les sources du SDK.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m22/ProviderPluginPlatformContractTest.java` :
  découverte sans activation, POM de `domain` et `application` sans SDK ni plugin de référence, plugin de référence
  absent de `morpheus-cli/pom.xml`, lignes `provider.plugins.*` du manifeste, projection remote sur liste blanche.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterSiblingArchitectureTest.java` :
  `domain` et `application` ne dépendent pas du SDK.
- `distribution/build-portable.ps1` et `distribution/build-portable.sh` échouent si le runtime embarqué n'a pas son
  launcher Java ; `scripts/validate-m22.ps1` exécute `discover`, activation et `probe` depuis le launcher packagé et
  exige le statut `SUPPORTED`.
- Revue : les options `--jlink-options` des deux scripts de distribution, qu'aucun test ne lit.

## References

- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution native, sans image Docker
- [ADR-0028](BE-0028-unified-provider-read-contract.md) — contrat de lecture provider unifié, probe distinct de read
- [ADR-0134](GEN-0134-distribution-native-autonome.md) — app-image `jpackage` autoportée
- [ADR-0096](GEN-0096-conservative-native-mcp-client-integration.md) — intégration client MCP native
- [ADR-0128](ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS par MCP STDIO
- [Preuve du jalon M22](../../project/validation/VALIDATION_M22.md)
- [Guide du SDK provider](../../developer/PROVIDER_SDK.md)
- `SECURITY.md`
- `contracts/public-surfaces.tsv`
