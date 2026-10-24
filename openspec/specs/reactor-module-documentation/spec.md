# reactor-module-documentation Specification

## Purpose
Define what the current developer and architecture documentation may state about the modules of the Maven reactor, and
how those statements are kept equal to the root POM.

## Requirements

### Requirement: A current page that lists the reactor's modules SHALL list every module the root POM declares

Among the current developer and architecture pages, a page that names more than half of the modules declared in the
`<modules>` block of the root POM MUST name every one of them, and the build MUST fail and name the page and the
missing modules otherwise.

#### Scenario: A module added to the POM and not to the pages

- **WHEN** a module is declared in the root POM and a page that lists the reactor does not name it
- **THEN** the build fails and names the page and the module

#### Scenario: The coverage report module is listed

- **WHEN** the rule runs on the corrected pages
- **THEN** each page that lists the reactor names `morpheus-coverage-report`

#### Scenario: A page that mentions a few modules is not concerned

- **WHEN** a page names fewer than half of the declared modules
- **THEN** the rule does not require it to name the others

#### Scenario: A module removed from the POM and left on a page

- **WHEN** a page that lists the reactor names, in its list, a module the root POM no longer declares
- **THEN** the build fails and names the page and the module

#### Scenario: The rule found something to check

- **WHEN** the rule reads no module from the root POM, or finds no page that lists the reactor
- **THEN** it fails instead of passing

### Requirement: A current page SHALL NOT state how many modules or Maven projects the reactor has

No current developer or architecture page MUST state, as a present fact, a total of modules, of Maven projects or of
POMs of the reactor; a measurement that carries its own date or commit is a record and is not concerned.

#### Scenario: A stated total is refused even when it is right

- **WHEN** a current page states the number of modules the root POM declares today
- **THEN** the build fails and names the page and the sentence

#### Scenario: The four pages of the finding no longer carry a total

- **WHEN** the developer guide, the build guide and the two arc42 sections are read after the change
- **THEN** none of them states a number of modules, of Maven projects or of POMs

#### Scenario: A dated measurement is left as it is

- **WHEN** a table of a current page reports the modules analysed by a run and carries the date of that run
- **THEN** the rule does not refuse it and the table is not rewritten

#### Scenario: A dated record is not scanned

- **WHEN** an ADR, a validation record, an audit or a release note states a module total
- **THEN** the rule does not read it
