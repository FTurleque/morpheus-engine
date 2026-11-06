# Proposition : demander dans la pull request l'intention de chaque assertion textuelle

## Why

Le constat `AUD-TRV-03` de l'audit du 09/10/2026 (`docs/project/audits/2026-10-09/findings.md`, revérifié le
2026-11-06 sur `30520bd3`, statut PARTIEL) relève que l'enforcement de `morpheus-architecture-tests` repose
majoritairement sur des assertions textuelles, et que la première clause de son action n'est pas exécutée :
« exiger dans la description de PR la phrase d'intention ». Le dépôt n'a aucun gabarit de pull request (`.github/` ne
porte que `workflows/`, `AI_GOVERNANCE.md`, `CODEOWNERS` et `dependabot.yml`). La phrase d'intention existe comme
convention (`.claude/rules/architecture.md`, « Avant de choisir, écrire l'intention en une phrase » ; skill
`enforcement-choice`), mais elle ne laisse aucune trace là où la revue la lit. De même, `.claude/rules/governance.md`
demande de justifier dans la description de la PR toute modification d'un fichier de gouvernance, sans que rien ne le
rappelle à l'auteur. Le mainteneur a validé le 06/11 la découpe de l'action en trois changements ; celui-ci porte la
première clause.

## What Changes

- Ajout d'un gabarit de description de pull request, `.github/pull_request_template.md`, avec deux sections : la phrase
  d'intention et le mécanisme retenu pour chaque assertion textuelle ajoutée ou modifiée dans la suite d'architecture,
  et la justification de toute modification d'un fichier de gouvernance.
- Ajout d'un contrôle de cohérence dans la suite d'architecture qui refuse l'absence du gabarit ou de l'une de ses
  deux sections.
- Mise à jour de `.claude/rules/architecture.md`, `.claude/rules/governance.md` et de la skill `enforcement-choice` pour
  renvoyer au gabarit.
- Aucun workflow ne lit la description d'une pull request (décision D3 de `design.md`).

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `enforcement-mechanism-accounting` : deux exigences ajoutées, sur le gabarit de pull request qui demande l'intention
  de chaque assertion textuelle et la justification d'une modification de gouvernance.

## Périmètre et exclusions

**Inclus :** le gabarit, son contrôle de présence et de contenu, les renvois depuis les règles et la skill.

**Exclus :**
- le contenu de chaque pull request : aucun test ne peut le lire, la revue le tient ;
- la migration d'interdits textuels vers des règles sur le bytecode : changement
  `write-every-dependency-ban-as-a-bytecode-rule` ;
- le verdict distinct d'un échec de cohérence documentaire : changement
  `report-documentation-drift-apart-from-architecture-violations` ;
- tout plafond de la part textuelle de l'enforcement, refusé délibérément par `.claude/rules/architecture.md`
  (« La part textuelle de l'enforcement n'est pas plafonnée »).

## Acteurs et surfaces concernés

Auteur et relecteur d'une pull request (mainteneur unique, `CODEOWNERS`). Aucune surface publique CLI, MCP ou HTTP ne
change : `contracts/public-surfaces.tsv` et l'OpenAPI ne bougent pas.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le dépôt n'a pas de gabarit de pull request | existant vérifié | `git ls-files` à `30520bd3` : aucun `pull_request_template` ni `PULL_REQUEST_TEMPLATE` |
| La phrase d'intention est une convention écrite, sans trace en revue | existant vérifié | `.claude/rules/architecture.md` (« Règle ArchUnit ou assertion textuelle ») ; `.claude/skills/enforcement-choice/SKILL.md` |
| L'ADR qui portait ce critère est archivé | existant vérifié | `docs/architecture/adr/archive/0103-textual-assertions-and-archunit-rules-enforce-different-things.md` (« Remplacée par ADR-0130 », bandeau d'archive) |
| La justification d'une modification de gouvernance est exigée par une règle | existant vérifié | `.claude/rules/governance.md`, « Justifier dans la description de la PR toute modification de … » ; `config/debt-counter-ratchets.properties` (en-tête : « the pull request says so ») |
| Le gabarit porte les deux demandes | décision | `design.md`, D1 et D2 |
| Aucun workflow ne lit la description de PR | décision | `design.md`, D3 |
| La découpe en trois changements | décision | mainteneur, 06/11/2026 |

## Impact

- Fichiers : `.github/pull_request_template.md` (nouveau) ; un contrôle de cohérence ajouté sous
  `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/` (classe marquée `@CoherenceCheck`).
- Règles et skill : `.claude/rules/architecture.md`, `.claude/rules/governance.md`,
  `.claude/skills/enforcement-choice/SKILL.md`.
- Documentation durable : `docs/developer/specifications/enforcement-mechanism-accounting.md`.
- ADR : aucun (filtre de `.claude/rules/adr.md` en `design.md`, D1).

## Questions ouvertes

Aucune.
