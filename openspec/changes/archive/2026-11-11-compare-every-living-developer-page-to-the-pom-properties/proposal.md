# Proposition : comparer aux propriétés du POM toute page vivante de `docs/`

## Why

`GovernanceSurfaceCoherenceTest#everyVersionAGovernancePageWritesAsAPomPropertyEqualsThePom` compare au POM racine une
version écrite sous la forme d'une propriété, mais seulement sur les pages de gouvernance IA (`GOVERNANCE_PAGES` =
`.claude/CLAUDE.md`, `.github/AI_GOVERNANCE.md` ; `GOVERNANCE_DIRECTORIES` = `.claude/commands`, `.claude/agents`,
`.claude/rules`). Tout `docs/` lui est invisible.

Constat `AUD-QUA-23` (sévérité Faible), revérifié le 10/11/2026 sur `38a4079f` : `docs/developer/CODE_AUDIT.md` et
`docs/developer/SPOTBUGS.md` donnaient `spotbugs.maven.plugin.version` = `4.10.4.1` pour `4.10.5.0` au POM (dérive du
19/10/2026) et `pitest.maven.plugin.version` = `1.30.0` pour `1.30.2`. **Le cas est corrigé** depuis `91bb2d86`
(PR #624, 10/11/2026) : ces pages nomment désormais les propriétés sans recopier de valeur (vérifié le 11/11/2026). Ce
qui reste est le mécanisme : la même dérive peut revenir sur n'importe quelle page vivante de `docs/`.

Écart relevé en préparant ce changement : les deux formes fautives — une ligne de tableau
`| \`propriété\` | \`valeur\` |` et `` `propriété` = `valeur` `` — ne sont lues par **aucun** des deux motifs du gate
(`VERSION_PROPERTY_ELEMENT`, `VERSION_PROPERTY_ASSIGNMENT`). Élargir la portée sans apprendre ces formes n'aurait pas
vu le défaut d'origine.

## What Changes

- Le gate de versions lit aussi toute page Markdown suivie par Git sous `docs/`, hors relevés datés que
  `.claude/rules/documentation.md` nomme (`docs/project/audits/`, `docs/user/release-notes/`,
  `docs/project/validation/`, `docs/project/roadmap/`) et hors blocs de code clôturés.
- Il apprend deux formes : ligne de tableau dont une cellule est le span de code d'une propriété et une autre le span
  de code d'une valeur, et `` `propriété` = `valeur` ``. Ces formes valent pour toutes les pages qu'il lit.
- Dans `docs/`, seule une propriété que le POM racine déclare est comparée ; sur les pages de gouvernance IA, une
  propriété inconnue reste refusée.
- `.claude/rules/build.md` décrit la portée élargie.

## Capabilities

### New Capabilities

### Modified Capabilities

- `dependency-version-management` : deux exigences ajoutées — les pages vivantes de `docs/` sont comparées au POM, et
  une version écrite en ligne de tableau ou en égalité de spans de code est une version écrite sous forme de propriété.

## Périmètre et exclusions

**Inclus :** le gate `everyVersionAGovernancePageWritesAsAPomPropertyEqualsThePom` et ses exemples, `.claude/rules/build.md`,
la page durable de la capacité.

**Exclus :** les relevés datés (ils citent la version de leur date) ; la prose libre (« SpotBugs 4.10.5.0 »), qu'aucun
motif ne relie à une propriété — `.claude/rules/build.md` demande déjà de citer la propriété plutôt que sa valeur ; le
dossier local du superviseur `docs/project/rapport-supervisor/`, non suivi par Git ; la fraîcheur des dépendances
elles-mêmes (`AUD-DEP-17`).

## Acteurs et surfaces concernés

Contributeur qui lit `docs/developer/` avant de lancer un outil ; Dependabot et le mainteneur qui fusionnent un bump
qui ne touche que `pom.xml` ; CI.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| La portée du gate s'arrête à `.claude/` et `.github/AI_GOVERNANCE.md` | existant vérifié | `GovernanceSurfaceCoherenceTest.java:52-54` |
| Les trois cellules fautives sont corrigées | existant vérifié | `git show 91bb2d86`, pages relues le 11/11/2026 |
| Les formes tableau et `= ` ne sont lues par aucun motif | existant vérifié | motifs `VERSION_PROPERTY_ELEMENT` et `VERSION_PROPERTY_ASSIGNMENT`, lignes 65-69 ; formes d'origine dans `git show 91bb2d86^:docs/developer/CODE_AUDIT.md` |
| `docs/` porte des clés `*.version` qui ne sont pas des propriétés du POM | existant vérifié | `docs/architecture/risks/register.md:158` (`data.version` de Dependency-Check), `docs/developer/PROVIDER_SDK.md:84` (manifeste de plugin, bloc clôturé) |
| Comparer dans `docs/` les seules propriétés déclarées par le POM | décision | voir `design.md`, D3 |

## Impact

- `morpheus-architecture-tests` : `GovernanceSurfaceCoherenceTest` (classe `@CoherenceCheck` : ne lit aucune source
  principale ; inchangé).
- `.claude/rules/build.md`, `docs/developer/specifications/dependency-version-management.md`.
- Aucun ADR (filtre de `.claude/rules/adr.md`, question 2 : un contrôle documentaire local).

## Questions ouvertes

Aucune.
