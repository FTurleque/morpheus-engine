# adapter-internal-boundaries Specification

## Purpose
Define how a boundary between the classes of one transport adapter is stated, which selections of classes may still
be a list of names, and what a relocation of classes must leave as strict as it found it.

Documentation durable (vue consolidée, diagrammes, traçabilité) : [docs/developer/specifications/adapter-internal-boundaries.md](../../../docs/developer/specifications/adapter-internal-boundaries.md).

## Requirements

### Requirement: A role inside the HTTP adapter SHALL be a package, and a rule about that role SHALL select by package

A rule of the architecture suite whose subject or target is "the routers", "the remote server and its identity
model", "the transport plumbing" or any other role of `morpheus-api` MUST select its classes by the package they
reside in. It MUST NOT select them by a suffix or a prefix of their simple name, nor by a list of class names.

#### Scenario: A remote class without the family prefix

- **WHEN** a router depends on a class of the remote identity model whose name does not start with `MorpheusRemote`
- **THEN** the rule that keeps routers away from the remote authorization model fails and names both classes

#### Scenario: A router added to the adapter

- **WHEN** a class named `*HttpRoutes` is added to `morpheus-api` outside the packages that classify routers
- **THEN** the architecture suite fails and names the class, before any transport rule is evaluated on it

#### Scenario: An application service outside the services package

- **WHEN** a class named `*ApiService` is added to `morpheus-api` outside `com.morpheus.api`
- **THEN** the architecture suite fails and names the class with the package it resides in

#### Scenario: A router declared outside the imported classes

- **WHEN** a source file named `*HttpRoutes.java` exists in a module the architecture suite does not import
- **THEN** the suite fails and names the file, as it does today

### Requirement: The packages of a transport adapter SHALL form no cycle

The architecture suite MUST refuse a dependency cycle between the packages of `morpheus-api`. An exception MUST be
named by the exact pair of types that closes it, with its reason, and MUST be recorded in the ADR.

#### Scenario: A plumbing class reaches back to a router

- **WHEN** a class of the transport plumbing acquires a dependency on a class of a router package that depends on it
- **THEN** the suite fails and reports the cycle between the two packages

#### Scenario: The rule has something to check

- **WHEN** `morpheus-api` is reduced to a single package again
- **THEN** the suite fails, instead of passing because no pair of packages exists

### Requirement: What another module may use of an adapter SHALL be a closed list of types, and SHALL NOT grow by a relocation

A class outside `com.morpheus.api..` MUST depend only on the entry types of the adapter and their nested types, and
its sources MUST NOT name another type of the adapter. The entry types MUST be the types other modules use on the day
the rule is written, re-measured, wherever they reside, and a type added to the list MUST be justified in the ADR.

#### Scenario: A neighbouring module uses a class that had to become public

- **WHEN** a class of `morpheus-cli` depends on a class of `morpheus-api` that is not an entry type
- **THEN** the architecture suite fails and names both classes

#### Scenario: A neighbouring module reads a constant of such a class

- **WHEN** a class of `morpheus-cli` reads a compile-time constant of a class of `morpheus-api` that is not an entry
  type, so that no dependency is compiled
- **THEN** the suite still fails, on the name the source had to write

#### Scenario: An entry type moves to a sub-package

- **WHEN** an entry type moves to another package of the adapter
- **THEN** the rule still accepts its users, and nothing outside the adapter gains access to its neighbours

### Requirement: A selection that remains a list of class names SHALL be of a declared kind and exact in both directions

A list of production class names in the architecture suite MUST be either a counter of a debt that an ADR says only
shrinks, or a table of facts in which every entry carries its reason. In both cases a class that has the listed
property without being named MUST fail the suite, and a named class that no longer has it MUST fail the suite.

#### Scenario: A list of class names of an undeclared kind

- **WHEN** a gate declares a list that names a class of any production module, declared neither a debt counter nor
  a table of facts
- **THEN** the suite fails and names the gate and the list

#### Scenario: A debt counter without its decision record

- **WHEN** a list declared a debt counter names a decision record that is absent from the registry, not accepted, or
  does not name the list by `TestClass.FIELD`
- **THEN** the suite fails and names the list and the record

#### Scenario: A debt counter grows

- **WHEN** a list declared a debt counter holds more entries than its key in `config/debt-counter-ratchets.properties`
- **THEN** the suite fails and names the list; when it holds fewer, the suite fails until the key is lowered

#### Scenario: A composition root added by a relocation

- **WHEN** a class that is not a named composition root comes to depend on a concrete store or provider because a
  class it uses was moved or split
- **THEN** `onlyTheNamedCompositionRootsReachAConcreteStoreOrProvider` fails as it does today, and the relocation
  does not add the name

### Requirement: Relocating a class within its module SHALL change no gate whose subject is not its package

A gate that reads the source of a production class MUST locate it by the type, within its module. Moving a class to
another package of the same module, with no other change, MUST leave every such gate passing, and MUST fail only the
rules that state which package a class of that role belongs to.

#### Scenario: A class is moved and nothing else changes

- **WHEN** a source file of `morpheus-api` is moved to another package of the module and its package declaration
  updated
- **THEN** every gate that reads that source still finds it and gives the verdict it gave before

#### Scenario: Two classes of one module share a simple name

- **WHEN** a gate asks for the source of a type whose simple name is declared twice in the module
- **THEN** the lookup fails and names both files, instead of reading the first one found

### Requirement: A relocation SHALL NOT lower the number of executed architecture tests nor remove a textual scan that a rule cannot replace

A rule that replaces a list MUST be accompanied by tests of what the new rule can still miss, so that the executions
counted against `architectureTestsMinimum` in `config/m21-quality-ratchets.properties` do not decrease. A textual
scan that exists because the banned type declares a compile-time constant MUST stay beside the package rule.

#### Scenario: A router reads an inlined constant of the remote server

- **WHEN** a router reads a public compile-time constant of a class of the remote package
- **THEN** the package rule passes and the textual scan of the router sources fails, naming the router

### Requirement: Une liste de classes qu'un ADR en vigueur énumère DOIT égaler la constante du gate qui la tient

Quand le test qui classe les listes déclare qu'un ADR accepté énumère une liste de classes de production, le build
DOIT (MUST) exiger cette énumération dans l'ADR, la comparer à la constante du test nom pour nom et dans les deux sens,
et échouer en nommant l'ADR, la constante et chaque nom en trop ou manquant. Un ADR déclaré ne nommer la liste que par
sa référence `TestClass.CHAMP` n'est pas comparé.

#### Scenario: Une classe ajoutée au gate et pas à l'ADR

- **GIVEN** une liste rattachée à un ADR qui en énumère le contenu
- **WHEN** un nom est ajouté à la constante du test, avec la clé du cliquet relevée dans le même changement, et l'ADR
  n'est pas modifié
- **THEN** la suite d'architecture échoue et nomme l'ADR, la constante et le nom absent de l'ADR
- **AND** aucun fichier n'est réécrit : l'écart reste à corriger par le contributeur

#### Scenario: Un nom que l'ADR énumère et que le gate ne porte plus

- **GIVEN** l'énumération d'`ARCH-0150` qui nomme une classe retirée de la constante, comme
  `product.UpdateDiscoveryService` dans la mesure du 25/10/2026
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme la classe en trop dans l'ADR

#### Scenario: Le même nom simple dans un autre paquet

- **GIVEN** une énumération qui écrit `security.LocalWritePermissionHardener` alors que la constante porte
  `files.LocalWritePermissionHardener`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme les deux noms, car la comparaison porte sur le nom qualifié

#### Scenario: Une énumération que le build ne sait pas lire

- **GIVEN** un ADR déclaré énumérer une liste, dont le bloc d'énumération est absent, vide, ou contient une famille
  hors du vocabulaire déclaré par le test
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et dit ce qu'elle n'a pas pu lire, au lieu de conclure que les deux listes concordent

#### Scenario: Un ADR qui nomme la liste par sa seule référence

- **GIVEN** `ARCH-0156`, qui nomme `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` sans énumérer ses noms
- **WHEN** la suite d'architecture s'exécute, la liste étant déclarée référencée seulement
- **THEN** la comparaison ne lui demande aucune énumération et ne le refuse pas

### Requirement: Une table de faits qu'un ADR en vigueur énonce DOIT déclarer cet ADR

Une liste de classes de production déclarée table de faits, dont un ADR accepté énonce le contenu, DOIT (MUST)
déclarer cet ADR auprès du test qui classe les listes, et le build DOIT la comparer à son énumération comme un compteur
de dette. Une table de faits qui ne déclare aucun ADR n'est pas comparée.

#### Scenario: Les six types d'entrée de l'adaptateur HTTP

- **GIVEN** `ApiEntryTypesArchitectureTest.ENTRY_TYPES`, dont `ARCH-0155` §3 énumère les six types
- **WHEN** la suite d'architecture s'exécute après ce changement
- **THEN** la table déclare `ARCH-0155` et son contenu est comparé à l'énumération du §3

#### Scenario: Un type d'entrée ajouté sans l'ADR

- **WHEN** un type est ajouté à `ENTRY_TYPES` et `ARCH-0155` §3 n'est pas modifié
- **THEN** la suite d'architecture échoue et nomme le type absent de l'ADR

#### Scenario: Une table de faits déclare un ADR qui ne l'énumère pas

- **WHEN** une table de faits déclare un ADR absent du registre, non accepté, ou qui ne porte aucune énumération de
  la table
- **THEN** la suite d'architecture échoue et nomme la table et l'ADR
