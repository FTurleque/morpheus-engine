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

### Requirement: Une version qu'une page vivante de `docs/` écrit sous la forme d'une propriété du POM DOIT égaler la valeur de cette propriété

Le build DOIT (MUST) lire chaque page Markdown suivie par Git sous `docs/`, hors `docs/project/audits/`,
`docs/user/release-notes/`, `docs/project/validation/`, `docs/project/roadmap/` et hors blocs de code clôturés, et
comparer toute version écrite sous la forme d'une propriété que le POM racine déclare à la valeur du POM, en échouant
sur la page, la propriété et les deux valeurs. Une clé que le POM ne déclare pas n'y est pas comparée.

#### Scenario: Un bump qui ne touche que le POM

- **GIVEN** une page de `docs/developer/` qui écrit `spotbugs.maven.plugin.version=4.10.4.1`
- **WHEN** `pom.xml` déclare `4.10.5.0` pour cette propriété
- **THEN** la suite d'architecture échoue et nomme la page, `spotbugs.maven.plugin.version`, `4.10.4.1` et `4.10.5.0`
- **AND** aucune page n'est réécrite par le test

#### Scenario: Un relevé daté n'est pas lu

- **WHEN** une page de `docs/project/audits/` ou de `docs/user/release-notes/` écrit une propriété du POM à la valeur
  de sa date
- **THEN** elle n'est pas comparée

#### Scenario: Une transcription dans un bloc de code clôturé

- **WHEN** une page vivante de `docs/` reproduit, dans un bloc de code clôturé, une sortie de console ou un fichier
  d'exemple qui écrit une version sous forme de propriété
- **THEN** cette occurrence n'est pas comparée

#### Scenario: La clé d'un autre artefact

- **GIVEN** `docs/architecture/risks/register.md`, qui écrit `data.version=5.6` à propos de Dependency-Check
- **WHEN** la suite d'architecture s'exécute
- **THEN** cette clé, que le POM ne déclare pas, n'est pas comparée et la page n'est pas refusée

#### Scenario: Un fichier local non suivi

- **WHEN** `docs/project/rapport-supervisor/` porte un rapport non suivi par Git qui écrit une ancienne version
- **THEN** il n'est pas lu

### Requirement: Une version écrite en ligne de tableau ou en égalité de spans de code est une version écrite sous la forme d'une propriété

Sur toute page que lit le contrôle des versions, le build DOIT (MUST) lire comme une version écrite sous la forme d'une
propriété du POM : une ligne de tableau dont une cellule est le span de code d'un nom `*.version` et une cellule
suivante le span de code d'une valeur, et `` `nom.version` = `valeur` ``. Une ligne de tableau qui nomme la propriété
sans valeur en span de code NE DOIT PAS (MUST NOT) être lue comme une version.

#### Scenario: La ligne de tableau d'origine du constat

- **GIVEN** une ligne `| \`pitest.maven.plugin.version\` | \`1.30.0\` | plugin Maven PIT |` et `pom.xml` qui déclare `1.30.2`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme la page, `pitest.maven.plugin.version`, `1.30.0` et `1.30.2`

#### Scenario: L'égalité de deux spans de code

- **WHEN** une page écrit `` `spotbugs.maven.plugin.version` = `4.10.4.1` `` alors que le POM déclare une autre valeur
- **THEN** la suite d'architecture échoue et nomme la page, la propriété et les deux valeurs

#### Scenario: Un tableau qui nomme les propriétés sans valeur

- **GIVEN** un tableau `| Propriété | Rôle |` dont les cellules nomment `spotbugs.maven.plugin.version` et décrivent son
  rôle
- **WHEN** la suite d'architecture s'exécute
- **THEN** aucune version n'est lue sur ces lignes et la page n'est pas refusée
