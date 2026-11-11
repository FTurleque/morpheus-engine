# application-technology-boundary Specification

## Purpose
Define which technology the application layer may reach, through which classes, and where that is written.

Documentation durable (vue consolidée, diagrammes, traçabilité) : [docs/developer/specifications/application-technology-boundary.md](../../../docs/developer/specifications/application-technology-boundary.md).

## Requirements

### Requirement: The architecture record SHALL say what the application layer may touch

An architecture decision record MUST state, for each technology family - outbound network, file access, process
environment, standard streams, threads, SQL, JSON library, process execution, class loading - whether
`com.morpheus.application` may use it, and MUST name every class that does today with what was decided for it: it
stays, or it leaves.

#### Scenario: A reader asks whether a use case may open a file

- **WHEN** a contributor reads the record to know whether a new application class may read the workspace
- **THEN** the record answers for that family, and names the classes already allowed to

#### Scenario: A class that does I/O has no decision

- **WHEN** a class of the application reaches a confined family and the record does not name it
- **THEN** the architecture suite fails, and the record cannot be said to describe the code

### Requirement: A technology the application does not use SHALL be refused in all of its packages

A family the record states as unused MUST be refused for every class under `com.morpheus.application`, not only for
the packages that carried a ban before the record.

#### Scenario: A use case acquires a database dependency

- **WHEN** a class of any application package comes to depend on `java.sql`
- **THEN** the architecture suite fails and names the class and the family

#### Scenario: The stricter bans of the deterministic cores still hold

- **WHEN** the suite runs after this change
- **THEN** the textual assertions on `application.reasoning` and `application.policy` are still present and still pass

### Requirement: A technology the application does use SHALL be confined to named classes

A family in use MUST be reachable only from the classes the record names for it. The named set MUST be exactly the set
that reaches the family: it may shrink, and it MUST NOT grow without a consolidated record replacing it.

#### Scenario: A second class opens an outbound connection

- **WHEN** a class other than the named ones comes to depend on the HTTP client
- **THEN** the architecture suite fails and says the class is not named by the record

#### Scenario: A named class no longer reaches the family

- **WHEN** a named class stops using the family it is named for
- **THEN** the suite fails until the name is removed, so the list only shrinks

#### Scenario: A path is not an access

- **WHEN** an application class uses `java.nio.file.Path` as a value without opening, listing or writing anything
- **THEN** the suite does not count it as file access

### Requirement: The descriptions of the application layer SHALL say what is enforced

The repository's instructions and its architecture pages MUST describe the application layer as the record decides it,
and MUST NOT present as technology-free a layer the record allows to do I/O.

#### Scenario: The record allows local file access

- **WHEN** the record keeps a family in the application
- **THEN** the diagram caption, the rules page and the building-block view say so and point to the record

### Requirement: Une classe que le registre de la frontière technologique nomme par un nom qualifié DOIT exister à ce nom

Toute classe de `com.morpheus.application` que l'ADR de la frontière technologique de l'application nomme par un nom
qualifié, dans n'importe quelle section, DOIT (MUST) exister à ce nom dans les sources de `morpheus-application`, et le
build DOIT échouer en nommant le nom introuvable sinon.

#### Scenario: Une classe déplacée dans un autre paquet

- **GIVEN** l'ADR qui écrit `security.LocalWritePermissionHardener`
- **WHEN** la classe vit dans `com.morpheus.application.files`
- **THEN** la suite d'architecture échoue et nomme `security.LocalWritePermissionHardener`
- **AND** l'ADR reste inchangé jusqu'à sa correction

#### Scenario: Une classe retirée des sources

- **WHEN** l'ADR nomme par un nom qualifié une classe qui n'existe plus dans `morpheus-application`
- **THEN** la suite d'architecture échoue et nomme la classe

#### Scenario: Le contrôle a lu des noms

- **WHEN** le contrôle ne trouve aucun nom qualifié dans l'ADR
- **THEN** il échoue au lieu de passer

### Requirement: Le registre de la frontière technologique NE DOIT PAS porter de mesure périssable de ses classes nommées

L'ADR de la frontière technologique de l'application NE DOIT PAS (MUST NOT) énoncer comme un fait présent la longueur
d'une classe nommée, un total de lignes, un numéro de ligne, ni attribuer à une classe une famille que sa décision
refuse ; une mesure datée qui a servi à décider vit dans un relevé daté sous `docs/project/`, auquel l'ADR renvoie.

#### Scenario: La mesure du 25/10/2026 quitte le registre

- **WHEN** l'ADR est relu après ce changement
- **THEN** il ne porte plus la table « Mesure » ni ses longueurs, son total et ses numéros de ligne, et il renvoie au
  relevé daté qui la conserve telle qu'elle a été écrite

#### Scenario: Une famille refusée n'est attribuée à aucune classe

- **WHEN** l'ADR est relu après ce changement
- **THEN** aucune de ses sections n'attribue le réseau sortant ni les fils d'exécution à une classe de l'application

#### Scenario: Chaque ligne de la décision nomme ses classes

- **WHEN** la décision 2 est relue
- **THEN** chaque famille confinée renvoie à des noms de classes écrits, et aucune ne renvoie à « la mesure » pour les
  connaître
