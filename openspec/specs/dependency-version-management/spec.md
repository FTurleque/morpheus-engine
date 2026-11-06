# dependency-version-management Specification

## Purpose
Define where the version of a dependency or of a build plugin is decided in the reactor, and how the build refuses a
module that decides one.

Documentation durable (vue consolidée, diagrammes, traçabilité) : [docs/developer/specifications/dependency-version-management.md](../../../docs/developer/specifications/dependency-version-management.md).

## Requirements

### Requirement: A module POM SHALL state no dependency or plugin version

The POM of every module declared in the root POM MUST NOT contain a `<version>` element other than the version of its
parent and `${project.version}` on a module of the same reactor, and the build MUST fail otherwise.

#### Scenario: A literal version in a module fails the build

- **WHEN** a module POM declares a third-party dependency with a literal version
- **THEN** the architecture suite fails and names the POM and the line

#### Scenario: A property-backed version in a module fails the build

- **WHEN** a module POM declares a dependency or a plugin with a `${...}` version other than `${project.version}`
- **THEN** the suite fails in the same way

#### Scenario: A sibling module is referenced by the project version

- **WHEN** a module depends on another module of the reactor with `${project.version}`
- **THEN** the suite reports nothing for it

#### Scenario: A newly declared module is judged

- **WHEN** a module is added to the `<modules>` of the root POM
- **THEN** its POM is read by the same test without any other change

### Requirement: Every third-party version SHALL be decided in the root POM

Each third-party dependency a module declares MUST obtain its version from the root POM, through an imported BOM or a
managed entry, and each build plugin a module configures MUST obtain its version from the root plugin management.

#### Scenario: A module resolves the version the root POM manages

- **WHEN** the dependency tree of a module and the generated SBOM are produced
- **THEN** each third-party component carries the version that the root POM, or a BOM it imports, manages

#### Scenario: The logging backend follows the logging API

- **WHEN** the SLF4J version is changed in the root POM
- **THEN** the API and the backend packaged by the CLI change together, with no edit to a module POM

### Requirement: The build rule SHALL describe the mechanism as enforced

The repository's build rule MUST state that a module POM carries no version except the two allowed forms, and MUST
cite the test that enforces it.

#### Scenario: The rule page and the POMs agree

- **WHEN** the rule page is read
- **THEN** every module POM satisfies the sentence as written, and the page names the test that would fail otherwise

### Requirement: Une version qu'une page de gouvernance IA écrit sous la forme d'une propriété du POM DOIT égaler la valeur de cette propriété

Quand une page de gouvernance IA écrit une version sous la forme d'une propriété du POM racine, `<nom.version>valeur</nom.version>`
ou `nom.version=valeur`, le build DOIT (MUST) comparer la valeur à celle que `pom.xml` déclare pour cette propriété et
échouer en nommant la page, la propriété et les deux valeurs ; une propriété que le POM ne déclare pas DOIT aussi faire
échouer le build.

#### Scenario: La version de JaCoCo restée en arrière

- **GIVEN** `.claude/rules/build.md` qui écrit `jacoco.version=0.8.15` et `pom.xml` qui déclare `0.8.16`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme `.claude/rules/build.md`, `jacoco.version`, `0.8.15` et `0.8.16`
- **AND** aucune page n'est réécrite par le test

#### Scenario: Un bump qui ne touche que le POM

- **GIVEN** une propriété de version que `build.md` recopie
- **WHEN** un changement modifie sa valeur dans `pom.xml` seulement
- **THEN** la suite d'architecture échoue jusqu'à ce que la page suive

#### Scenario: Les deux formes sont lues

- **WHEN** une page écrit la même propriété sous la forme d'un élément XML et sous la forme `nom=valeur`
- **THEN** chacune des deux occurrences est comparée au POM

#### Scenario: Une propriété que le POM ne déclare plus

- **WHEN** une page écrit `nom.version=valeur` pour une propriété absente de `pom.xml`
- **THEN** la suite d'architecture échoue et nomme la page et la propriété

#### Scenario: Le contrôle a trouvé des versions à comparer

- **WHEN** le contrôle ne trouve aucune version sous forme de propriété dans l'ensemble des pages parcourues
- **THEN** il échoue au lieu de passer
