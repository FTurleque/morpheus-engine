---
id: ARCH-0109
title: "Les adaptateurs de transport sont des racines de composition nommées, et leur liste ne fait que rétrécir"
domain: architecture
rules: false
---

# Les adaptateurs de transport sont des racines de composition nommées, et leur liste ne fait que rétrécir

- Statut : **Acceptée**
- Date : 2026-10-08

## Context

ADR-0003 range parmi ses conséquences l'absence de dépendance des adaptateurs à la base choisie, et les règles du
dépôt disent que les adaptateurs sont frères et que le câblage est explicite. Or les serveurs HTTP et MCP construisent
le runtime de chaque requête ou appel d'outil à partir du chemin de la base qu'on leur passe : `morpheus-api` dépend à
la compilation de `morpheus-store-sqlite` et de `morpheus-provider-openspec`, `morpheus-mcp` de
`morpheus-store-sqlite`. Une règle non dite sur ces dépendances laisse le modèle documenté et le code diverger sans que
rien le signale.

Chaque store SQLite ouvert seul est une connexion physique et une vérification de schéma. Un transport qui assemble
lui-même ses stores paie autant de connexions qu'il ouvre de stores pour une seule opération, et chaque transport finit
par avoir son propre câblage des mêmes services.

Options considérées :

- **Ramener tout le câblage dans `morpheus-cli`**, les adaptateurs ne dépendant plus que des ports de l'application
  sous une règle `noClasses()` stricte : c'est le modèle le plus pur, mais il exige de déplacer des classes, les POMs
  et les bootstraps des serveurs, sans défaut observé qui le motive. Il reste possible : la liste nommée en est le
  compteur.
- **Ne rien écrire** : laisser la règle non dite.
- **Nommer les racines et borner leur liste** : retenu.

## Decision

### 1. `api` et `mcp` atteignent un store ou un provider concret par une liste nommée qui ne fait que rétrécir

Les classes de `com.morpheus.api` et `com.morpheus.mcp` qui dépendent d'un store (`com.morpheus.store..`) ou d'un
provider (`com.morpheus.provider..`) concret sont les **racines de composition** du runtime de leur transport, et
seulement elles. Elles sont nommées dans `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` : le runtime de
chaque transport (`ApiRuntime`, `MorpheusMcpRuntime`), le service de synchronisation HTTP qui construit le lecteur
OpenSpec, et le serveur remote avec son bootstrap, qui prennent le fichier de base lui-même par
`SqliteServerMaintenance` (sauvegarde, restauration, bail exclusif).

- Toute **autre** classe de `api` ou `mcp` qui dépend d'un store ou d'un provider concret est refusée.
- La liste est **exactement** l'ensemble des classes qui en dépendent : une racine qui n'en dépend plus doit être
  retirée. La liste peut rétrécir, elle ne peut pas grandir en silence.

**Ajouter une racine exige un ADR consolidé qui remplace celui-ci et la nomme**, avec la raison pour laquelle la classe ne peut pas passer par un port
de l'application. Les autres interdits valent sans exception : `application` ne connaît aucun adaptateur, `api` ne dépend ni
de `cli` ni de `mcp`, les providers et les stores restent frères.

La CLI est la racine de composition du processus : `MorpheusMain` câble le processus et `CliRuntime` construit le
runtime de chaque commande. La conséquence d'ADR-0003 (aucune dépendance à la base choisie) vaut donc pour
`morpheus-application` et pour toute classe de `api` et `mcp` hors des racines nommées.

### 2. Une seule classe construit les stores d'une opération

`SqliteOperationRuntime` (`morpheus-store-sqlite`) est la seule classe qui construit plusieurs stores SQLite. Elle ouvre
toujours la `SqliteConnectionScope` avant tout store, puis construit chaque store la première fois qu'une opération le
demande et l'inscrit aussitôt pour libération. Une opération paie les stores qu'elle lit, une connexion physique et
une vérification de schéma, quel que soit le transport. Elle câble aussi les services de requête et de policy, qui
n'ont qu'un câblage pour les trois transports.

- **Un type de runtime par transport** : `ApiRuntime`, `CliRuntime`, `MorpheusMcpRuntime`. Chacun enveloppe
  `SqliteOperationRuntime` et ne rend que des **ports de l'application** : une classe qui lit par lui ne dépend
  d'aucun type SQLite. La CLI a deux fabriques nommées : `recovering`, pour les commandes qui récupèrent les
  publications interrompues, et `open`, pour les autres.
- **Une base qui ne s'ouvre pas est la même erreur partout** : `KnowledgeStoreException`, avec le message du store de
  connaissance (`SqliteSpecificationKnowledgeStore.INITIALIZATION_FAILURE`).
- **Un store se construit après l'ouverture de la portée**, jamais pendant : une base au schéma trop récent échoue au
  premier store demandé. Le `try` qui ouvre le runtime l'entoure dans tous les sites.
- **Une portée ne s'imbrique pas** ; le refus est nommé (`SqliteConnectionScope.NESTED_REFUSAL`, levé par
  `SqliteConnectionScope.NestedScopeException`) et désigne un défaut de l'appelant, jamais un état de la base.
- **Une classe dont chaque opération ne construit qu'un store peut le faire hors portée, si elle est nommée** dans
  `SqliteConnectionScopeUseArchitectureTest.ONE_STORE_PER_OPERATION` avec son opération. Un store, c'est une
  connexion ; une portée n'y ajouterait rien. Une classe ainsi nommée qui ouvrirait deux stores ensemble est refusée.
  Un store construit pendant qu'une portée est ouverte sur le thread emprunte sa connexion, là où un runtime ouvert à
  cet endroit serait refusé comme portée imbriquée.

### 3. `morpheus-mcp-transport` ne dépend d'aucun autre paquet MORPHEUS

Le transport STDIO borné (`com.morpheus.integration.mcp`) est partagé par le serveur MCP et par les clients MINOS et
NEXUS : il ne connaît aucun type MORPHEUS.

### 4. Les paquets du domaine sont sans cycle, à deux exceptions nommées ; l'application n'a pas de règle de cycle

Tout cycle entre tranches de `com.morpheus.domain` est interdit, sauf deux, **nommés par la paire de types exacte**
qui ferme chacun : `ProviderProbeResult → SourceLocator` (un résultat de sonde porte le localisateur de la source
sondée) et `RequirementDelta → Scenario` (un delta porte ses scénarios, un scénario nomme son exigence). Toute autre
dépendance entre ces paquets, ou tout nouveau cycle, échoue.

Aucune règle de cycle ne porte sur `com.morpheus.application` : les cycles entre ses tranches ne se retirent pas sans
refonte des paquets, ce qui sort de la portée d'une décision d'architecture.

## Do's and Don'ts

### Do

- Faire passer une nouvelle classe de `api` ou `mcp` qui a besoin d'un store par une racine nommée existante.
- Retirer de `COMPOSITION_ROOTS` une racine qui ne dépend plus d'aucun store ni provider concret.
- Lire les stores d'une opération par le runtime de son transport, donc par `SqliteOperationRuntime`.
- Ouvrir la portée de connexion avant le premier store.
- Nommer dans `ONE_STORE_PER_OPERATION`, avec son opération, toute classe qui construit un store hors portée.

### Don't

- Ne pas ajouter une racine de composition sans un ADR consolidé qui remplace celui-ci et la nomme.
- Ne pas construire plusieurs stores SQLite hors de `SqliteOperationRuntime`.
- Ne pas exposer un type `Sqlite…Store` depuis un runtime de transport.
- Ne pas imbriquer deux portées de connexion sur un même thread.
- Ne pas faire dépendre `com.morpheus.integration.mcp` d'un autre paquet `com.morpheus..`.
- Ne pas introduire de cycle entre paquets du domaine.

## Consequences

### Positives

- La dépendance des transports aux stores concrets est écrite, bornée et ne peut que diminuer.
- Une opération coûte une connexion physique et une vérification de schéma, sur tous les transports.
- Les services de requête et de policy ont un seul câblage.
- Une base inutilisable produit la même erreur sur CLI, MCP et HTTP.

### Négatives

- Le modèle n'est pas purement hexagonal : des classes nommées de `api` et `mcp` dépendent de `store-sqlite`.
- Les cycles de paquets de l'application restent sans règle.

### Risques

- Les classes nommées dans `ONE_STORE_PER_OPERATION` ouvrent chacune une connexion hors portée : une opération qui
  passerait par deux d'entre elles en ouvrirait deux.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterCompositionRootArchitectureTest.java` :
  `onlyTheNamedCompositionRootsReachAConcreteStoreOrProvider`,
  `everyNamedCompositionRootStillReachesAConcreteStoreOrProvider`, `theMcpTransportDependsOnNoOtherMorpheusPackage`,
  `domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SqliteConnectionScopeUseArchitectureTest.java` :
  règle textuelle (code lu sans commentaires, ouverture avant la première construction, listes fermées `SCOPE_OWNERS`
  et `ONE_STORE_PER_OPERATION`) et règle sur le bytecode (une classe qui construit un store appelle
  `SqliteConnectionScope.open` ou est nommée). Les deux sont gardées : le texte voit l'ordre, le bytecode voit un store
  construit par référence de constructeur.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/MultiResourceOwnershipContractTest.java`
  (`theOperationRuntimeRegistersEveryStoreItOpensForRelease`).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` : `application` ne
  dépend d'aucun adaptateur, `api` reste frère de `cli` et `mcp`.

## References

- [ADR-0003](DATA-0003-specification-knowledge-store.md) — port de stockage de connaissance.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — build Maven multi-module.
- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP natif.
- [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) — API HTTP sur `jdk.httpserver`.
- `.claude/rules/architecture.md`
