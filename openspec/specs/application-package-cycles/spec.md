# application-package-cycles Specification

## Purpose
Define which dependencies between the packages of the application layer may lie on a cycle, and how that set changes.

Documentation durable (vue consolidée, diagrammes, traçabilité) : [docs/developer/specifications/application-package-cycles.md](../../../docs/developer/specifications/application-package-cycles.md).

## Requirements

### Requirement: The dependencies that lie on a cycle between application packages SHALL be named

The architecture suite MUST refuse any dependency between first-level packages of `com.morpheus.application` that
closes a cycle, read on the compiled classes, and MUST hold no list of tolerated dependencies or components.

#### Scenario: A new dependency closes a cycle

- **WHEN** a class of one application package comes to depend on a class of another that already reaches it
- **THEN** the architecture suite fails and names the cycle, package by package

#### Scenario: A new dependency between two packages already in a cycle

- **WHEN** a dependency is added between two packages that already reach each other through others
- **THEN** the suite fails in the same way

#### Scenario: A dependency outside any cycle

- **WHEN** a dependency is added that leaves the package graph acyclic
- **THEN** the suite passes

#### Scenario: The rule is proven on a known cycle

- **WHEN** the same rule is evaluated on two test packages that depend on each other
- **THEN** it fails and names both

### Requirement: The package of the store ports SHALL take part in no cycle

`com.morpheus.application.store` MUST NOT depend on an application package that depends on it, directly or through
others.

#### Scenario: A store port speaks the vocabulary of a capability

- **WHEN** an interface of `application.store` comes to depend on a type of a capability package that uses the store
  ports
- **THEN** the architecture suite fails and names the interface and the type

#### Scenario: The port lives with its capability

- **WHEN** a port is declared in the package of the capability whose types it uses
- **THEN** the suite passes, and both stores still implement it with identical behaviour

### Requirement: The architecture record SHALL state the rule and its measurement

The architecture record MUST state the rule, the measurement it started from with its date and revision, and the
moves that brought the application packages to no cycle.

#### Scenario: A reader looks for the current number of cycles

- **WHEN** a contributor reads the record or the repository's rules to know the state of the application's cycles
- **THEN** they find a dated measurement and a rule without exception, not a number presented as current

### Requirement: Les paquets sous la plateforme de requête sont sans cycle, à une paire nommée près

La suite d'architecture DOIT (MUST) refuser, lue sur les classes compilées, toute dépendance entre la racine
`com.morpheus.application.query` et ses paquets de deuxième niveau qui ferme un cycle, à la seule exception des
dépendances de `query.dsl` vers `query.saved` qui figurent dans la liste exacte des arêtes tolérées de cette paire.

#### Scenario: Un cycle nouveau entre deux sous-paquets de la requête

- **WHEN** une classe de `query.dsl` vient à dépendre d'une classe de `query.export`, qui dépend déjà de `query.dsl`
- **THEN** la suite d'architecture échoue et nomme le cycle, paquet par paquet
- **AND** aucune liste ne permet de l'admettre

#### Scenario: Une arête de `query.dsl` vers `query.saved` hors de la liste

- **GIVEN** la liste exacte des arêtes tolérées de la paire
- **WHEN** une classe de `query.dsl` qui n'y figure pas vient à dépendre d'une classe de `query.saved`
- **THEN** la suite d'architecture échoue et nomme le cycle et la dépendance ajoutée

#### Scenario: Un cycle qui passe par la racine de la requête

- **WHEN** une classe de la racine `com.morpheus.application.query` vient à dépendre de `query.saved`, et une classe de
  `query.saved` d'elle
- **THEN** la suite d'architecture échoue et nomme le cycle

#### Scenario: Le code en vigueur passe

- **WHEN** la règle est évaluée sur les classes de production, avec les arêtes listées
- **THEN** la suite passe sans que la liste ni le code changent

#### Scenario: La règle est prouvée sur une paire connue

- **WHEN** la même règle est évaluée sur deux paquets de test de deuxième niveau qui dépendent l'un de l'autre, sans
  arête tolérée
- **THEN** elle échoue et nomme les deux

### Requirement: Les arêtes entre le DSL de requête et les vues enregistrées forment une liste exacte qui ne fait que rétrécir

La suite DOIT (MUST) tenir la liste exacte des arêtes, dans les deux sens, entre types de premier rang de `query.dsl`
et de `query.saved`, lues sur le bytecode et sur le texte hors commentaires des sources, et échouer en nommant toute
arête mesurée absente de la liste et toute arête listée qui n'est plus mesurée.

#### Scenario: Une arête ajoutée dans le sens des vues vers le DSL

- **WHEN** une classe de `query.saved` vient à employer un type de `query.dsl` qu'elle n'employait pas
- **THEN** la suite d'architecture échoue et nomme l'arête, origine et cible
- **AND** l'admettre exige d'ajouter l'entrée et de relever la clé du compteur, ce que seul un ADR consolidé autorise

#### Scenario: Une arête disparue reste listée

- **WHEN** un changement supprime l'emploi d'un type de `query.dsl` par une classe de `query.saved`
- **THEN** la suite échoue jusqu'à ce que l'entrée et la clé du compteur baissent dans le même changement

#### Scenario: Une arête portée par une constante inlinée

- **GIVEN** une classe de `query.saved` qui ne lit d'un type de `query.dsl` qu'une constante que `javac` inline
- **WHEN** la liste est comparée à la mesure
- **THEN** l'arête est mesurée par le texte de la source et doit figurer dans la liste

#### Scenario: Un type imbriqué compte pour son type de premier rang

- **WHEN** une classe emploie un type imbriqué d'un type de l'autre paquet
- **THEN** l'arête mesurée a pour cible le type de premier rang qui l'enclôt

### Requirement: La liste des arêtes tolérées est un compteur de dette adossé à une décision acceptée

La liste des arêtes de la paire DOIT (MUST) être déclarée compteur de dette dans `ProductionClassListsArchitectureTest`,
sous un ADR accepté qui la nomme par `ApplicationPackageCyclesArchitectureTest.QUERY_DSL_AND_SAVED_EDGES`, et sa taille
DOIT égaler sa clé de `config/debt-counter-ratchets.properties`, qui ne fait que baisser.

#### Scenario: La liste grandit sans que la clé suive

- **WHEN** une entrée est ajoutée à la liste et la clé du compteur reste à sa valeur
- **THEN** la suite d'architecture échoue et nomme le compteur, sa taille et sa clé

#### Scenario: La liste rétrécit sans que la clé suive

- **WHEN** une entrée est retirée de la liste et la clé reste à sa valeur
- **THEN** la suite échoue jusqu'à ce que la clé baisse dans le même changement

#### Scenario: Le compteur n'est nommé par aucune décision acceptée

- **WHEN** la décision déclarée pour le compteur n'est pas acceptée ou ne le nomme pas par `TestClass.CHAMP`
- **THEN** la suite d'architecture échoue et nomme le compteur et la décision

### Requirement: La règle de la plateforme de requête juge chacun de ses paquets

La suite DOIT (MUST) échouer quand l'ensemble des paquets de deuxième niveau que déclarent les sources de
`com.morpheus.application.query` diffère de celui que la règle de deuxième niveau juge sur les classes compilées.

#### Scenario: Un sous-paquet ajouté à la requête

- **WHEN** un paquet `com.morpheus.application.query.<nouveau>` est ajouté avec une classe
- **THEN** la règle le juge comme une tranche, sans modification de la règle

#### Scenario: Des classes de la requête non importées

- **WHEN** un sous-paquet déclaré par les sources n'apparaît pas parmi les classes compilées importées
- **THEN** la suite échoue et nomme les deux ensembles

### Requirement: La décision d'architecture nomme la paire tolérée et son compteur

La décision d'architecture en vigueur sur les cycles de paquets de l'application DOIT (MUST) énoncer la règle de
deuxième niveau sous `query`, nommer la paire `query.dsl` / `query.saved` comme seule exception et son compteur, et
porter la mesure datée, avec sa révision, d'où part ce compteur.

#### Scenario: Un lecteur cherche pourquoi la paire est tolérée

- **WHEN** un contributeur lit la décision d'architecture ou les règles du dépôt sur les cycles de l'application
- **THEN** il trouve la règle de deuxième niveau, l'exception nommée, le compteur et une mesure datée, et aucun nombre
  d'arêtes présenté comme courant hors de la clé du compteur
