---
id: ARCH-0152
title: "Les transports sont des racines de composition nommées, et les cycles de paquets ne font que diminuer"
domain: architecture
rules: false
---

# Les transports sont des racines de composition nommées, et les cycles de paquets ne font que diminuer

- Statut : **Acceptée**
- Date : 2026-10-29
- Remplace : ADR-0109

## Context

ADR-0003 range parmi ses conséquences l'absence de dépendance des adaptateurs à la base choisie, et les règles du
dépôt disent que les adaptateurs sont frères et que le câblage est explicite. Or les serveurs HTTP et MCP construisent
le runtime de chaque requête ou appel d'outil à partir du chemin de la base qu'on leur passe : `morpheus-api` dépend à
la compilation de `morpheus-store-sqlite` et de `morpheus-provider-openspec`, `morpheus-mcp` de
`morpheus-store-sqlite`. Une règle non dite sur ces dépendances laisse le modèle documenté et le code diverger sans que
rien le signale. Ramener tout le câblage dans `morpheus-cli` serait le modèle le plus pur, mais exige de déplacer des
classes, les POMs et les bootstraps des serveurs sans défaut observé qui le motive ; la liste nommée des racines en est
le compteur.

Chaque store SQLite ouvert seul est une connexion physique et une vérification de schéma. Un transport qui assemble
lui-même ses stores paie autant de connexions qu'il ouvre de stores pour une seule opération, et chaque transport finit
par avoir son propre câblage des mêmes services.

### Cycles de paquets

Le domaine a deux cycles, chacun fermé par une paire de types : `ProviderProbeResult → SourceLocator` (un résultat de
sonde porte le localisateur de la source sondée) et `RequirementDelta → Scenario` (un delta porte ses scénarios, un
scénario nomme son exigence).

L'application, tranchée au premier niveau (`com.morpheus.application.(*)..`), forme une composante fortement connexe.
Mesures du 29/10/2026 sur `develop` à `6b3f6304`, constat AUD-ARC-03 de l'audit du 09/10/2026 :

| Mesure | Résultat |
|---|---|
| ArchUnit `beFreeOfCycles()`, `cycles.maxNumberToDetect` relevé pour la mesure | 395 cycles élémentaires |
| Graphe des tranches calculé sur le bytecode | 31 tranches, une composante de 16, 52 dépendances sur un cycle |
| Graphe des imports des sources, même jour | identique au bytecode |
| Graphe des imports du 13/10/2026 (`8b26d3d7`) | 29 tranches, la même composante, 47 dépendances |

Le nombre de cycles élémentaires est combinatoire : une dépendance entre deux tranches déjà cycliques en ajoute des
dizaines, et ArchUnit ne le rapporte que jusqu'à un plafond qu'aucune configuration versionnée ne fixe. Ce n'est pas un
seuil possible. L'ensemble des dépendances entre tranches qui appartiennent à une composante l'est : il est
déterministe, sans plafond, et une entrée est une chose à corriger.

`application.store` dépendait de trois tranches, par trois ports qui parlaient le vocabulaire de leur capacité
(`PolicyPackStore` → `policy`, `SavedViewStore` → `query.saved` et `query.dsl`, `SyncStateStore` → `sync`). Ces trois
ports vivent dans le paquet de leur capacité : il reste 21 dépendances en deux composantes,
`{context, ingestion, operability, query, read, reference, security, snapshot, traceability}` et
`{lifecycle, orchestration, quality}`. Déplacer les trois exceptions de `store` (`EntityNotFoundException`,
`EntityStateException`, `KnowledgeStoreException`) ne retire aucune dépendance d'un cycle : chaque paquet qui en
importe une importe aussi un port de `store`.

Options écartées pour la règle de l'application : `ignoreDependency` par paire de types comme pour le domaine (des
centaines de paires, et une nouvelle paire entre deux tranches déjà listées serait illisible en revue) ;
`FreezingArchRule` (un fichier que le build réécrit, dont les descriptions changent avec un numéro de ligne) ; un
compte dans `config/*ratchets*.properties` (un nombre qu'on tient en échangeant un cycle contre un autre).

## Decision

1. **Racines de composition nommées.** Les classes de `com.morpheus.api` et `com.morpheus.mcp` qui dépendent d'un store
   ou d'un provider concret sont exactement celles de `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS`. Une
   racine qui n'en dépend plus est retirée ; en ajouter une exige un ADR consolidé qui remplace celui-ci et la nomme.
   `MorpheusMain` câble le processus, `CliRuntime` le runtime de chaque commande.

2. **Une classe construit les stores d'une opération.** `SqliteOperationRuntime` ouvre la `SqliteConnectionScope`
   avant tout store, construit chacun à la première demande et l'inscrit pour libération. Chaque transport l'enveloppe
   dans un runtime (`ApiRuntime`, `CliRuntime`, `MorpheusMcpRuntime`) qui ne rend que des ports. Une portée ne
   s'imbrique pas (`SqliteConnectionScope.NESTED_REFUSAL`). Une classe à store unique par opération peut le construire
   hors portée si `SqliteConnectionScopeUseArchitectureTest.ONE_STORE_PER_OPERATION` la nomme.

3. **`com.morpheus.transport.mcp` ne dépend d'aucun autre paquet MORPHEUS**, et n'est rangé ni sous les intégrations ni
   sous l'adaptateur MCP.

4. **Cycles du domaine.** Aucun cycle entre tranches de `com.morpheus.domain`, sauf les deux nommés par leur paire de
   types.

5. **Cycles de l'application.** Les dépendances entre tranches de premier niveau qui appartiennent à un cycle sont
   exactement `ApplicationPackageCyclesArchitectureTest.DEPENDENCIES_ON_A_CYCLE`, et les composantes exactement
   `CYCLIC_COMPONENTS`. Une dépendance non nommée échoue ; un nom qui n'est plus sur un cycle échoue jusqu'à son retrait.
   Aucun compte de cycles n'est un seuil. `application.store` ne participe à aucun cycle : un port vit avec la capacité
   dont il parle le vocabulaire.

## Do's and Don'ts

### Do

- Faire passer une nouvelle classe de `api` ou `mcp` qui a besoin d'un store par une racine nommée existante.
- Retirer de `COMPOSITION_ROOTS` une racine qui ne dépend plus d'aucun store ni provider concret.
- Lire les stores d'une opération par le runtime de son transport, donc par `SqliteOperationRuntime`, et ouvrir la
  portée de connexion avant le premier store.
- Nommer dans `ONE_STORE_PER_OPERATION`, avec son opération, toute classe qui construit un store hors portée.
- Déclarer un port dans le paquet de la capacité dont il emploie les types.
- Retirer de `DEPENDENCIES_ON_A_CYCLE` et de `CYCLIC_COMPONENTS` ce que le test signale comme n'étant plus sur un cycle.

### Don't

- Ne pas ajouter une racine de composition sans un ADR consolidé qui remplace celui-ci et la nomme.
- Ne pas construire plusieurs stores SQLite hors de `SqliteOperationRuntime`, ni exposer un type `Sqlite…Store` depuis
  un runtime de transport, ni imbriquer deux portées de connexion sur un même thread.
- Ne pas faire dépendre `com.morpheus.transport.mcp` d'un autre paquet `com.morpheus..`.
- Ne pas introduire de cycle entre paquets du domaine.
- Ne pas ajouter à `DEPENDENCIES_ON_A_CYCLE` une dépendance qui ferme ou étend un cycle, ni faire dépendre un port de
  `application.store` d'un type d'une capacité.
- Ne pas remplacer la liste par un nombre de cycles.

## Consequences

### Positives

- La dépendance des transports aux stores concrets est écrite, bornée et ne peut que diminuer.
- Une opération coûte une connexion physique et une vérification de schéma, sur tous les transports, et une base
  inutilisable produit la même erreur (`KnowledgeStoreException`) sur CLI, MCP et HTTP.
- Aucun cycle ne s'ajoute entre paquets de l'application, et chaque baisse se lit comme des noms retirés.

### Négatives

- Le modèle n'est pas purement hexagonal : des classes nommées de `api` et `mcp` dépendent de `store-sqlite`.
- L'application garde deux composantes cycliques ; la règle les borne, elle ne les retire pas.
- La règle de l'application tranche au premier niveau : une dépendance entre `query.dsl` et `query.saved` lui échappe ;
  `QueryPlatformArchitectureTest` est l'endroit d'une règle sur eux.

### Risques

- Les classes nommées dans `ONE_STORE_PER_OPERATION` ouvrent chacune une connexion hors portée : une opération qui
  passerait par deux d'entre elles en ouvrirait deux.
- Le graphe est lu sur le bytecode : une constante que `javac` inline ne crée aucune dépendance.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterCompositionRootArchitectureTest.java` :
  `onlyTheNamedCompositionRootsReachAConcreteStoreOrProvider`,
  `everyNamedCompositionRootStillReachesAConcreteStoreOrProvider`, `theMcpTransportDependsOnNoOtherMorpheusPackage`,
  `domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ApplicationPackageCyclesArchitectureTest.java` :
  `noDependencyBetweenApplicationPackagesLiesOnACycleBesideTheNamedOnes`, `everyNamedDependencyStillLiesOnACycle`,
  `theCyclicComponentsAreTheNamedOnesInFull`, `theStorePortsPackageTakesPartInNoCycle`, et
  `theComponentComputationFindsEveryCycleOfAKnownGraph`, qui prouve le calcul des composantes sur un graphe connu.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SqliteConnectionScopeUseArchitectureTest.java` :
  règle textuelle (ouverture avant la première construction, listes fermées `SCOPE_OWNERS` et
  `ONE_STORE_PER_OPERATION`) et règle sur le bytecode (une classe qui construit un store appelle
  `SqliteConnectionScope.open` ou est nommée).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/MultiResourceOwnershipContractTest.java`
  (`theOperationRuntimeRegistersEveryStoreItOpensForRelease`).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` : `application` ne
  dépend d'aucun adaptateur, `api` reste frère de `cli` et `mcp`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ModulePackageCorrespondenceTest.java` :
  `theSharedMcpTransportResidesOutsideTheIntegrationsAndTheMcpAdapter`.

## References

- [ADR-0003](DATA-0003-specification-knowledge-store.md) — port de stockage de connaissance.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — build Maven multi-module.
- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP natif.
- [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) — API HTTP sur `jdk.httpserver`.
- [ADR-0150](ARCH-0150-ce-que-le-coeur-applicatif-peut-toucher.md) — ce que le cœur applicatif peut toucher.
- `openspec/changes/archive/2026-10-29-let-application-package-cycles-only-decrease/` et `openspec/specs/application-package-cycles/spec.md`.
- `.claude/rules/architecture.md`
