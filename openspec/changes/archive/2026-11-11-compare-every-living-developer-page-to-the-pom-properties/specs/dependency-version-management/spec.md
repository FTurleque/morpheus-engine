## ADDED Requirements

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
