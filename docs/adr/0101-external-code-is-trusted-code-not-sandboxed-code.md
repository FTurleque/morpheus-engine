# ADR-0101 — Le code externe approuvé est du code de confiance, pas du code sandboxé

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 8 septembre 2026
- Dépend de : ADR-0090 (plateforme de découverte de plugins), ADR-0096 (intégration client MCP native), ADR-0098 (runtime embarqué `bin/java`)
- Portée : plugins providers (`morpheus-provider-sdk`), pairs MCP configurés (`morpheus-mcp-transport`, `morpheus-integration-*`)

## Contexte

MORPHEUS exécute deux catégories de code qu'il n'a pas écrit : un **plugin provider** activé
explicitement par un opérateur, et un **pair MCP** (MINOS, NEXUS) dont l'opérateur a configuré la
commande de lancement. Les deux traversent une frontière de processus et une frontière
d'environnement réelles et testées : pin SHA-256 obligatoire, copie de vérification immuable,
environnement d'enfant réduit à une liste blanche, timeout borné, terminaison de sous-arbre de
processus, projection remote sur liste blanche.

L'accumulation de ces contrôles crée un risque de lecture : on peut croire que MORPHEUS *isole* du
code non fiable. Ce n'est pas le cas, et le prétendre serait la pire des issues — un opérateur
choisirait alors d'exécuter un plugin qu'il ne fait pas confiance, en s'appuyant sur une garantie
qui n'existe pas.

Un plugin approuvé s'exécute sous le **compte système MORPHEUS**. Il a les mêmes droits fichiers et
réseau que MORPHEUS lui-même. Le pin SHA-256 garantit que le code exécuté est **exactement celui qui
a été approuvé** ; il ne dit rien sur ce que ce code fait.

## Décision

### 1. Le modèle de confiance est nommé, et il est binaire

```text
un plugin provider activé explicitement    = code de confiance
un pair MCP explicitement configuré        = code de confiance
```

L'approbation est un acte d'opérateur, pas une propriété que MORPHEUS peut vérifier. Pour exécuter
du code tiers **non fiable**, la réponse de MORPHEUS est : ne le faites pas ici — isolez MORPHEUS ou
le processus externe avec un conteneur, un job object ou un cgroup, sous un compte dédié de moindre
privilège.

### 2. Ce que la frontière garantit réellement

| Garantie | Mécanisme |
|---|---|
| Le code exécuté est celui qui a été approuvé | pin SHA-256 obligatoire à l'activation, revérifié **dans l'enfant** avant chargement |
| Le pathname approuvé ne peut pas être substitué entre vérification et chargement | copie de staging vérifiée, propriétaire seul, seule exposée au `URLClassLoader` |
| La découverte n'exécute rien | métadonnées uniquement, `NOFOLLOW_LINKS`, aucun classloading au démarrage |
| Un probe ne peut pas tourner indéfiniment | budget d'exécution borné, terminaison gracieuse puis forcée |
| Un probe ne peut pas laisser un processus derrière lui | le worker réape son propre sous-arbre, le parent réape l'arbre observé |
| Un probe n'hérite pas des secrets d'environnement de MORPHEUS | liste blanche de lancement ; `MORPHEUS_SERVER_TLS_PASSWORD` et les variables d'injection JVM ne sont jamais transmises |
| Le modèle ne peut pas déclencher l'exécution de code tiers | `provider.plugins.probe` est `EXPLICITLY_NOT_EXPOSED` côté MCP, remote-only et ADMIN côté HTTP |
| Un appelant remote ne choisit pas le répertoire de plugins | le répertoire est imposé côté serveur |
| Une exception de plugin ne traverse pas la frontière remote | projection sur liste blanche, jamais un modèle interne filtré |

### 3. Ce que la frontière ne garantit pas

```text
PAS une sandbox du système d'exploitation
PAS une limite sur les fichiers que le code approuvé peut lire ou écrire
PAS une limite sur le réseau que le code approuvé peut joindre
PAS une garantie de terminaison des descendants d'un pair MCP (best-effort)
```

La terminaison des descendants est **garantie** pour un probe de plugin, parce que le worker est du
code MORPHEUS et réape son propre sous-arbre au moment où celui-ci est encore énumérable. Elle reste
**best-effort** pour un pair MCP, qui n'est pas du code MORPHEUS : l'observation périodique du parent
ne peut rien promettre pour un descendant créé puis orphelin dans le même intervalle.

### 4. Aucune fausse sandbox

MORPHEUS n'ajoutera pas de mécanisme qui ressemble à une sandbox sans en être une. Toute véritable
sandbox OS devrait être explicite, testée, fail-closed, et soit portable, soit documentée comme
limitée à une plateforme — et serait sa propre décision d'architecture, pas un effet de bord d'un
durcissement.

## Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Présenter la frontière de processus comme une sandbox | Faux, et dangereux dans le seul sens qui compte : un opérateur exécuterait du code non fiable en s'y fiant |
| Restreindre le plugin par un `SecurityManager` | Supprimé de la plateforme Java ; une réimplémentation partielle donnerait la même fausse assurance |
| Lancer le probe dans un conteneur | ADR-0027 et ADR-0061 excluent Docker de la distribution ; l'exiger pour un probe rendrait la fonctionnalité indisponible sur une installation locale standard |
| Restreindre par ACL le processus enfant sur chaque OS | Non portable, non testable de manière reproductible sur les deux plateformes supportées, et hors du périmètre d'un correctif post-audit |
| Ne rien documenter, puisque le code est déjà correct | C'est exactement ainsi que la lecture erronée s'installe ; l'invariant qui compte ici est une **absence** de garantie, et une absence ne se déduit pas du code |

## Conséquences

- `ProviderPluginTrustBoundaryContractTest` rend le modèle exécutable : il vérifie chaque garantie du
  tableau §2 dans les sources qui l'implémentent, et refuse que `SECURITY.md`, `PROVIDER_SDK.md` ou le
  SDK lui-même se remettent à promettre une sandbox.
- La formulation « frontière process/OS distincte, différée au-delà de M22 » de `PROVIDER_SDK.md` est
  corrigée : la frontière de processus **existe** depuis M22 ; c'est la sandbox qui n'existe pas, et
  qui n'est pas planifiée.
- Le trou de terminaison best-effort côté pair MCP reste documenté dans `SECURITY.md` comme un trou,
  jamais comme une garantie.
