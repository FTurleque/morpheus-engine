---
id: ARCH-0147
title: "Un rôle de l'adaptateur HTTP est un paquet ; ce qui reste une liste de noms en déclare la sorte"
domain: architecture
rules: false
---

# Un rôle de l'adaptateur HTTP est un paquet ; ce qui reste une liste de noms en déclare la sorte

- Statut : **Acceptée**
- Date : 2026-10-25

## Context

`morpheus-api` a été un paquet unique de 84 classes. Chaque frontière interne y était une liste de noms dans un test
d'architecture (les trois groupes de routeurs, les tables de `HttpPathReadingArchitectureTest`, `COMPOSITION_ROOTS`)
ou un préfixe de nom (`MorpheusRemote`, le suffixe `HttpRoutes`), et aucune règle de cycle ne pouvait porter sur
l'adaptateur. Ce que le paquet plat tenait vraiment, c'était le compilateur : 71 types sur 84 étaient
package-private, donc invisibles des autres modules.

La mesure du changement OpenSpec `express-adapter-internal-roles-as-packages` (tâche 1 de `tasks.md`) a établi trois
faits :

- une organisation sans cycle existe, à condition d'extraire de `MorpheusHttpServer` ses records imbriqués et
  `API_PREFIX`, les seules arêtes qui remontaient des routeurs et de la plomberie vers le serveur local ;
- un paquet parent qui contient à la fois les serveurs et les services forme un cycle avec chaque paquet de routeurs ;
- les six types que `morpheus-cli` utilise vivent à deux niveaux : les deux serveurs et le fichier d'identités en
  haut, `AllowedWorkspaceRoots`, `LoopbackHostPolicy` et `MorpheusRemoteRole` en bas. Aucun paquet d'entrée unique
  ne peut les réunir sans cycle.

Options considérées pour la frontière avec les autres modules : un paquet d'entrée (rejeté, il forme un cycle), deux
paquets d'entrée (rejeté, il impose de garder les serveurs dans le parent, donc le cycle précédent), une liste fermée
de types (retenu).

## Decision

### 1. Un rôle de l'adaptateur est un paquet

| Paquet | Rôle |
|---|---|
| `com.morpheus.api.remote` | le serveur remote, son bootstrap et son modèle d'identité |
| `com.morpheus.api.local` | le serveur local, son bootstrap et ses protections |
| `com.morpheus.api.transport` | la plomberie partagée : décodeur, writer, parseur, table de routes, enveloppes, échecs |
| `com.morpheus.api.routes.bodiless` | les routeurs sans corps de requête |
| `com.morpheus.api.routes.shareddecoder` | les routeurs qui lisent leur corps par le décodeur partagé |
| `com.morpheus.api.routes.owncontext` | les routeurs qui enregistrent leur propre contexte HTTP |
| `com.morpheus.api` | les services de l'application, `ApiRuntime`, les records de requête des services |

Une règle sur un rôle sélectionne ses classes par paquet, jamais par une liste de noms, un préfixe ou un suffixe.
Ranger un routeur dans un paquet de groupe, c'est le classer ; un routeur rangé ailleurs est refusé.

### 2. Les paquets de l'adaptateur sont sans cycle

L'ordre est : `remote` au-dessus de `local`, `local` au-dessus des routeurs, les routeurs au-dessus des services du
paquet parent, `transport` sous tous les autres. Une dépendance qui remonte cet ordre est refusée. Un cycle accepté se
nomme par la paire exacte de types qui le ferme, avec sa raison ; il n'y en a aucun.

### 3. Les autres modules n'utilisent que six types de l'adaptateur

`AllowedWorkspaceRoots`, `LoopbackHostPolicy`, `MorpheusHttpServer`, `MorpheusRemoteHttpServer`,
`MorpheusRemoteIdentityFile` et `MorpheusRemoteRole`, avec leurs types imbriqués, où qu'ils résident. Ce sont les
types de l'adaptateur que `morpheus-cli` utilise. Les types publics des autres paquets le sont pour l'adaptateur
lui-même, pas pour ses voisins. Un type ne s'ajoute à la liste que par un ADR.

### 4. Une liste de noms qui reste déclare sa sorte

Une liste de noms de classes de l'adaptateur qui subsiste dans les tests d'architecture est de l'une de deux sortes :

- un **compteur de dette** : ce qu'un ensemble de classes fait aujourd'hui et doit cesser de faire, exact dans les
  deux sens, qui ne fait que rétrécir, sous l'ADR qui le dit (`COMPOSITION_ROOTS`, ADR-0109) ;
- une **table de faits** : chaque entrée porte sa raison, et la table contient exactement les classes dont la raison
  est vraie (les tables de `HttpPathReadingArchitectureTest`, la liste d'entrée du point 3).

Une liste qui serait un rôle n'existe pas : ses classes vont dans un paquet.

### Hors de cette décision

Le rangement des services, de `ApiRuntime` et des racines de composition attend la décision sur la technologie que
le cœur applicatif peut toucher (AUD-ARC-01). `morpheus-cli` et `morpheus-mcp` se découpent par capacité dans leur
propre changement.

## Do's and Don'ts

### Do

- Placer une nouvelle classe de `morpheus-api` dans le paquet de son rôle.
- Placer un nouveau routeur dans le paquet de son groupe de transport.
- Déclarer dans `HttpAdapterClassListsArchitectureTest.KINDS` toute nouvelle liste de classes de l'adaptateur, comme
  compteur de dette ou table de faits.
- Faire passer un module voisin par l'un des six types d'entrée.

### Don't

- Ne pas sélectionner un rôle par une liste de noms, un préfixe ou un suffixe.
- Ne pas faire dépendre un paquet de l'adaptateur d'un paquet situé au-dessus de lui dans l'ordre du point 2.
- Ne pas laisser un module voisin nommer un type de l'adaptateur hors des six, même pour une constante.
- Ne pas ajouter un type d'entrée sans ADR.

## Consequences

### Positives

- Une règle de cycle porte sur l'adaptateur.
- L'interdit remote voit tout `com.morpheus.api.remote`, y compris les classes que le préfixe `MorpheusRemote`
  laissait passer (`RemoteIdentity*`, `RemoteAccessJournal`).
- Déplacer une classe ne touche plus que les règles qui nomment son paquet : les tests lisent une source par le nom
  de sa classe (`ProductionSources`).

### Négatives

- La frontière avec les autres modules, que le compilateur tenait, est tenue par une règle : plus de 150 types et
  membres sont devenus publics pour être utilisés d'un paquet à l'autre de l'adaptateur.
- Regrouper des routeurs devient un déplacement de fichiers au lieu d'une modification de liste.

### Risques

- Une règle peut être affaiblie dans un diff là où un modificateur ne pouvait pas l'être. La règle d'entrée a sa
  moitié textuelle, et chaque règle a été vue en échec une fois.

## Compliance and Enforcement

- `ApiEntryTypesArchitectureTest` : règle sur le bytecode et scan des sources des autres modules (qui voit une
  constante inlinée), avec une garde qui exige que chaque type d'entrée soit encore atteint et nommé. Vue en échec : une
  classe publique plantée dans un sous-paquet, utilisée depuis `morpheus-cli`, refusée par la règle ; la seule lecture
  de sa constante, refusée par le scan seul.
- `ApiPackageCyclesArchitectureTest` : tranches par paquet de `com.morpheus.api`, garde qui exige au moins deux paquets.
  Vue en échec : une classe de `transport` dépendant d'un service du parent.
- `HttpRoutesTransportBoundaryArchitectureTest` : groupes sélectionnés par paquet ; un routeur hors des trois paquets
  ou deux routeurs de même nom simple sont refusés. Vue en échec : un routeur vide planté dans `com.morpheus.api`.
- `HttpRoutesFamilyArchitectureTest` : règles de famille sur `com.morpheus.api.routes..`, interdit remote par paquet,
  bijection avec les sources limitée à ce paquet. Vue en échec : un routeur qui appelle une classe publique de
  `remote` dont le nom ne commence pas par `MorpheusRemote`.
- `HttpAdapterClassListsArchitectureTest` : toute liste de classes de l'adaptateur est déclarée avec sa sorte, et
  aucune déclaration ne survit à sa liste. Vue en échec dans les deux sens.

## References

- [ADR-0109](ARCH-0109-transport-adapters-are-named-composition-roots.md) — racines de composition nommées.
- [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) — API HTTP sur `jdk.httpserver`.
- Changement OpenSpec `express-adapter-internal-roles-as-packages` : mesures (tâche 1) et notes des tâches 2 à 5.
- `.claude/rules/architecture.md`
