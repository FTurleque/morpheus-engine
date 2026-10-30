---
id: ARCH-0156
title: "Les transports sont des racines de composition nommées, les paquets sont sans cycle, et une décision sur une valeur du domaine est écrite sur son type"
domain: architecture
rules: false
---

# Les transports sont des racines de composition nommées, les paquets sont sans cycle, et une décision sur une valeur du domaine est écrite sur son type

- Statut : **Acceptée**
- Date : 2026-10-30
- Remplace : ADR-0152

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

L'application est tranchée au premier niveau (`com.morpheus.application.(*)..`). Mesures du graphe des tranches,
calculé sur le bytecode, constat AUD-ARC-03 de l'audit du 09/10/2026 :

| Date, révision | Tranches | Dépendances sur un cycle | Composantes cycliques |
|---|---|---|---|
| 29/10/2026, `6b3f6304` | 31 | 52 | une de 16 tranches |
| 30/10/2026, `11bb1ccf` | 28 | 21 | `{context, ingestion, operability, query, read, reference, security, snapshot, traceability}`, `{lifecycle, orchestration, quality}` |
| 30/10/2026, après les déplacements ci-dessous | 28 | 0 | aucune |

Chacune des 21 dépendances restantes venait d'un type rangé dans un paquet plus haut que ceux qui l'employaient :

- `NormalizedProjectContent` dans `ingestion`, alors que `read` (le port `SpecificationContentReader`) et
  `traceability` le lisent et qu'il ne dépend que du domaine ;
- `IntegrationStatusDisclosure` dans `security`, alors qu'il projette des types de `context` et de `reference` ;
- `LocalWritePermissionHardener` dans `security`, alors que `operability` (le journal borné) l'emploie et qu'il ne
  touche que le système de fichiers ;
- la machine d'état du cycle de vie dans `application.lifecycle`, à côté de `lifecycle.mutation` qui dépend
  d'`orchestration`, alors qu'elle ne décide que sur des valeurs du domaine.

Aucun format persisté ni aucune surface publique ne nomme ces paquets.

### Décisions sur une valeur du domaine

ADR-0082 a descendu le blocage d'une contrainte dans `Constraint`. Deux autres décisions portaient sur une seule valeur
du domaine et vivaient dans l'application : la transition d'un cycle de vie (`ChangeLifecycleStateMachine`) et l'état
de résolution d'une référence externe (`ExternalReferenceResolutionService`, qui choisissait entre `UNRESOLVED` et
`STALE`, et `ExternalTraceabilityLinkFactory`, qui traduisait cet état en résolution de lien). Le résultat d'une
transition était un record à drapeau dont deux combinaisons sur quatre étaient refusées par le constructeur : une
famille fermée de deux variantes.

## Decision

1. **Racines de composition nommées.** Les classes de `com.morpheus.api` et `com.morpheus.mcp` qui dépendent d'un store
   ou d'un provider concret sont exactement `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS`. En ajouter une
   exige un ADR consolidé qui remplace celui-ci.
2. **Une classe construit les stores d'une opération** : `SqliteOperationRuntime`, qui ouvre la `SqliteConnectionScope`
   avant tout store. Chaque transport l'enveloppe dans un runtime qui ne rend que des ports. Hors portée, seules les
   classes de `SqliteConnectionScopeUseArchitectureTest.ONE_STORE_PER_OPERATION`.
3. **`com.morpheus.transport.mcp` ne dépend d'aucun autre paquet MORPHEUS.**
4. **Domaine** : aucun cycle entre tranches, sauf les deux nommés par leur paire de types.
5. **Application** : aucun cycle entre tranches de premier niveau, sans exception. Un type employé par deux paquets vit
   dans le plus bas, un port dans le paquet de la capacité dont il parle le vocabulaire.
6. **Une décision sur une seule valeur du domaine est écrite sur son type.** Le blocage d'une contrainte (ADR-0082),
   la transition d'un cycle de vie (`ChangeLifecycleTransitionRequest.decide()`, dont le résultat est la famille
   scellée `ChangeLifecycleTransitionDecision` : `Allowed` ou `Blocked`) et l'état de résolution d'une référence
   externe (`ExternalReference.resolvedAs`, `withoutResolver`, `notFound`,
   `ExternalReferenceResolutionState.traceabilityResolution`). L'application les demande et ne les refait pas.

## Do's and Don'ts

### Do

- Faire passer une nouvelle classe de `api` ou `mcp` qui a besoin d'un store par une racine nommée existante.
- Lire les stores d'une opération par le runtime de son transport, donc par `SqliteOperationRuntime`.
- Ranger un type employé par deux paquets de l'application dans le plus bas des deux, ou dans le domaine s'il ne parle
  que du domaine.
- Écrire sur le type du domaine une décision qui ne lit que cette valeur, et la tenir par une règle sans exception.
- Modéliser une famille fermée de variantes du domaine par une interface scellée dont chaque variante est un record.

### Don't

- Ne pas ajouter une racine de composition sans un ADR consolidé qui remplace celui-ci.
- Ne pas construire plusieurs stores SQLite hors de `SqliteOperationRuntime`, ni imbriquer deux portées de connexion.
- Ne pas faire dépendre `com.morpheus.transport.mcp` d'un autre paquet `com.morpheus..`.
- Ne pas introduire de cycle entre paquets du domaine ou de l'application, ni de liste d'exceptions pour en tolérer un.
- Ne pas lire dans l'application une constante de `ChangeLifecycleBlocker`, ni construire une
  `ChangeLifecycleTransitionDecision` hors du domaine, ni appeler `ExternalReference.transition` hors du domaine.

## Consequences

### Positives

- La dépendance des transports aux stores concrets est écrite, bornée et ne peut que diminuer.
- Une opération coûte une connexion physique et une vérification de schéma, sur tous les transports.
- Tout paquet de l'application peut être extrait sans traverser une composante cyclique, et une dépendance qui en
  fermerait une échoue sous son nom.
- Les règles du cycle de vie et de la résolution d'une référence se testent dans `morpheus-domain`, sans store.

### Négatives

- Le modèle n'est pas purement hexagonal : des classes nommées de `api` et `mcp` dépendent de `store-sqlite`.
- La règle de l'application tranche au premier niveau : une dépendance entre `query.dsl` et `query.saved` lui échappe ;
  `QueryPlatformArchitectureTest` est l'endroit d'une règle sur eux.
- Les autres familles du domaine (évaluation tri-état d'une contrainte, delta d'exigence) restent des records à enum :
  leur forme est persistée et sérialisée en JSON canonique.

### Risques

- Les classes nommées dans `ONE_STORE_PER_OPERATION` ouvrent chacune une connexion hors portée.
- Le graphe est lu sur le bytecode : une constante que `javac` inline ne crée aucune dépendance.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterCompositionRootArchitectureTest.java` :
  `onlyTheNamedCompositionRootsReachAConcreteStoreOrProvider`,
  `everyNamedCompositionRootStillReachesAConcreteStoreOrProvider`, `theMcpTransportDependsOnNoOtherMorpheusPackage`,
  `domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ApplicationPackageCyclesArchitectureTest.java` :
  `theApplicationPackagesAreFreeOfCycles` (`slices().matching("com.morpheus.application.(*)..").should().beFreeOfCycles()`),
  `theCycleRuleSeesEveryFirstLevelPackageOfTheApplicationSources`, et
  `theCycleRuleRefusesTwoPackagesThatDependOnEachOther`, qui prouve la règle sur deux paquets de test cycliques.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LifecycleTransitionDecisionInTheDomainArchitectureTest.java`
  et `ConstraintDecisionInTheDomainArchitectureTest.java` : décision 6, sans exception nommée.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SqliteConnectionScopeUseArchitectureTest.java`
  et `MultiResourceOwnershipContractTest.java` : décision 2.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` et
  `ModulePackageCorrespondenceTest.java` : couches et paquet du transport MCP.

## References

- [ADR-0003](DATA-0003-specification-knowledge-store.md) — port de stockage de connaissance.
- [ADR-0082](DATA-0082-explicit-constraint-semantics-and-blocking-policy.md) — sémantique des contraintes.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie et transition tri-état.
- [ADR-0150](ARCH-0150-ce-que-le-coeur-applicatif-peut-toucher.md) — ce que le cœur applicatif peut toucher.
- `openspec/specs/application-package-cycles/spec.md`, `openspec/specs/domain-value-decisions/spec.md`.
- `.claude/rules/architecture.md`
