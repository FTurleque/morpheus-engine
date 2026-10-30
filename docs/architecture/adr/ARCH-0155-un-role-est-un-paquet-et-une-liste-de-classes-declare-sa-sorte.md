---
id: ARCH-0155
title: "Un rôle de l'adaptateur HTTP est un paquet, et toute liste de classes de production d'un test d'architecture déclare sa sorte"
domain: architecture
rules: false
---

# Un rôle de l'adaptateur HTTP est un paquet, et toute liste de classes de production d'un test d'architecture déclare sa sorte

- Statut : **Acceptée**
- Date : 2026-10-30
- Remplace : ADR-0147

## Context

`morpheus-api` porte des rôles distincts : deux serveurs, la plomberie de transport qu'ils partagent, trois groupes de
routeurs qui ne lisent pas leur requête de la même façon, et les services de l'application. Une frontière entre ces
rôles se dit dans un test d'architecture ; elle ne peut s'y dire par un paquet que si chaque rôle en est un, sinon il
faut une liste de noms, un préfixe (`MorpheusRemote`) ou un suffixe (`HttpRoutes`), et aucune règle de cycle ne porte
sur l'adaptateur. Un type de l'adaptateur qu'un autre paquet utilise doit être public : la frontière avec les autres
modules ne peut alors pas être celle du compilateur.

Les contraintes, mesurées sur le code :

- une organisation sans cycle existe pour les rôles ci-dessous, à condition que les enveloppes de réponse, les records
  de requête et `API_PREFIX` ne soient pas portés par `MorpheusHttpServer` : les routeurs en dépendent ;
- un paquet qui contiendrait à la fois un serveur et les services forme un cycle avec chaque paquet de routeurs, car le
  serveur dépend des routeurs et les routeurs des services ;
- les six types que `morpheus-cli` utilise appartiennent à trois rôles : `MorpheusRemoteHttpServer`,
  `MorpheusRemoteIdentityFile` et `MorpheusRemoteRole` au serveur remote, `MorpheusHttpServer` et
  `LoopbackHostPolicy` au serveur local, `AllowedWorkspaceRoots` aux services qui le lisent. Un paquet d'entrée qui les
  réunirait dépendrait des services par le serveur remote et serait utilisé par eux : un cycle.

Options considérées pour la frontière avec les autres modules : un paquet d'entrée (rejeté, il forme un cycle), deux
paquets d'entrée (rejeté pour la même raison), une liste fermée de types (retenu).

Les services de l'application restent dans le paquet parent `com.morpheus.api`, avec `ApiRuntime` et les records de
requête qu'ils lisent. Les en sortir vers un sous-paquet laisserait le parent porter `ApiRuntime`,
`AllowedWorkspaceRoots` (un type d'entrée) et trois records, sans rôle commun, et déplacerait la racine de composition
`MorpheusProjectSyncApiService`, que nomme `COMPOSITION_ROOTS` (ADR-0156). Le paquet parent est déjà un rôle : il suffit
qu'une règle le sépare des routeurs et des serveurs, et qu'une classe `*ApiService` ne puisse résider ailleurs.

Les listes de noms de classes ne se trouvent pas que dans les tests de `morpheus-api` : les tests d'architecture en
déclarent qui nomment des classes du domaine, de l'application, des stores, des providers, de `morpheus-cli`, de
`morpheus-mcp` et des intégrations. Une liste qui nommerait un rôle est le défaut que la décision ci-dessous refuse,
quel que soit le module dont elle nomme les classes.

## Decision

### 1. Un rôle de l'adaptateur HTTP est un paquet

| Paquet | Rôle |
|---|---|
| `com.morpheus.api.remote` | le serveur remote, son bootstrap et son modèle d'identité |
| `com.morpheus.api.local` | le serveur local, son bootstrap et ses protections |
| `com.morpheus.api.routes.bodiless`, `.shareddecoder`, `.owncontext` | les trois groupes de routeurs |
| `com.morpheus.api` | les services de l'application (`*ApiService`), `ApiRuntime`, leurs records de requête |
| `com.morpheus.api.transport` | la plomberie partagée |

Une règle sur un rôle sélectionne ses classes par paquet.

### 2. Les paquets de l'adaptateur sont sans cycle et ordonnés

`remote` > `local` > routeurs > services > `transport`. Une dépendance qui remonte cet ordre est refusée : par la règle
de cycle, et sans attendre qu'un cycle se ferme pour la plomberie, les services et les routeurs. Aucune exception.

### 3. Les autres modules n'utilisent que six types de l'adaptateur

`AllowedWorkspaceRoots`, `LoopbackHostPolicy`, `MorpheusHttpServer`, `MorpheusRemoteHttpServer`,
`MorpheusRemoteIdentityFile` et `MorpheusRemoteRole`, avec leurs types imbriqués. Un type ne s'ajoute que par un ADR.

### 4. Toute liste de classes de production déclare sa sorte

Une liste d'un test d'architecture qui nomme une classe de n'importe quel module de production est :

- un **compteur de dette** : exact, qui ne fait que rétrécir, sous l'ADR qui le dit ;
- ou une **table de faits** : exactement les classes dont un fait énoncé est vrai, avec sa raison.

Une liste qui serait un rôle n'existe pas : ses classes vont dans un paquet.

## Do's and Don'ts

### Do

- Placer une nouvelle classe de `morpheus-api` dans le paquet de son rôle, un nouveau service `*ApiService` dans
  `com.morpheus.api`, un nouveau routeur dans le paquet de son groupe.
- Déclarer dans `ProductionClassListsArchitectureTest.KINDS` toute nouvelle liste d'un test d'architecture qui nomme
  une classe de production, comme compteur de dette ou table de faits.
- Faire passer un module voisin par l'un des six types d'entrée.

### Don't

- Ne pas sélectionner un rôle par une liste de noms, un préfixe ou un suffixe.
- Ne pas faire dépendre un paquet de l'adaptateur d'un paquet situé au-dessus de lui dans l'ordre du point 2.
- Ne pas laisser un module voisin nommer un type de l'adaptateur hors des six, même pour une constante.
- Ne pas ajouter un type d'entrée sans ADR.

## Consequences

### Positives

- Une règle de cycle et deux règles d'ordre portent sur l'adaptateur ; l'interdit remote voit tout
  `com.morpheus.api.remote`, y compris les classes que le préfixe `MorpheusRemote` laissait passer.
- Un interdit qui vaut pour un groupe de routeurs, pour les services ou pour la plomberie s'écrit une fois, sur le
  paquet, et voit le membre ajouté demain.
- Une liste de noms nouvelle, dans n'importe quel test d'architecture, est refusée tant que personne ne dit si c'est une
  dette ou un fait.

### Négatives

- La frontière avec les autres modules est tenue par une règle et non par le compilateur : des types et membres de
  l'adaptateur sont publics pour être utilisés d'un paquet à l'autre.
- Regrouper des routeurs est un déplacement de fichiers au lieu d'une modification de liste.
- La détection d'une liste est textuelle : une liste construite autrement qu'en `Set`, `List` ou `Map` `static final`
  initialisé par `of`, `copyOf` ou `ofEntries` lui échappe.

### Risques

- Une règle peut être affaiblie dans un diff là où un modificateur ne pouvait pas l'être. Chaque règle a sa preuve
  d'échec dans la section suivante, et les règles qui visent un type à constante inlinable gardent leur moitié textuelle.

## Compliance and Enforcement

- `ApiEntryTypesArchitectureTest` : règle sur le bytecode et scan des sources des autres modules. Vue en échec : une
  classe publique plantée dans un sous-paquet, utilisée depuis `morpheus-cli`, refusée par la règle ; la seule lecture de
  sa constante, refusée par le scan seul.
- `ApiPackageCyclesArchitectureTest` : tranches par paquet de `com.morpheus.api`. Vue en échec : une classe de
  `transport` dépendant d'un service du parent.
- `ApiServicesAndTransportPackagesArchitectureTest` : `transport` ne dépend d'aucun autre paquet de l'adaptateur ; les
  services ne dépendent ni des routeurs, ni des serveurs, ni de `com.sun.net.httpserver`, ni du writer de réponse ;
  toute classe `*ApiService` réside dans `com.morpheus.api`. Règle et scan des sources. Vue en échec :
  un `MorpheusApiService` dans `MorpheusHttpQuery`, un writer dans un service, un `PlantedApiService` dans `transport`,
  chacun refusé par sa méthode ; une constante d'un serveur lue dans `transport` ou dans un service, refusée par le scan
  seul.
- `HttpRoutesTransportBoundaryArchitectureTest` : groupes sélectionnés par paquet, aucun routeur ne dépend d'un routeur
  d'un autre groupe. Vue en échec : un routeur vide planté dans `com.morpheus.api` ; un champ
  `MorpheusProjectSyncHttpRoutes` dans `MorpheusRootHttpRoutes`.
- `HttpRoutesFamilyArchitectureTest` : règles de famille sur `com.morpheus.api.routes..`, interdits remote et serveur
  local par paquet. Vue en échec : un routeur qui appelle une classe publique de `remote` dont le nom ne commence pas par
  `MorpheusRemote` ; une constante de `MorpheusHttpServer` lue dans un routeur, refusée par le scan seul.
- `ProductionClassListsArchitectureTest` : toute liste qui nomme une classe d'un module de production est déclarée avec
  sa sorte, et aucune déclaration ne survit à sa liste. Vue en échec dans les deux sens : une liste non
  déclarée nommant `SqliteOperationRuntime`, une déclaration sans liste.

## References

- [ADR-0156](ARCH-0156-racines-de-composition-nommees-paquets-sans-cycle-et-decisions-dans-le-domaine.md) — racines de composition nommées, `COMPOSITION_ROOTS`.
- [ADR-0149](ARCH-0149-une-commande-et-un-outil-sont-servis-par-une-classe-de-capacite.md) — classes de capacité de `morpheus-cli` et `morpheus-mcp`.
- [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) — API HTTP sur `jdk.httpserver`.
- Spécification OpenSpec `openspec/specs/adapter-internal-boundaries/spec.md`.
- `.claude/rules/architecture.md`
