# Proposition : lire les deltas OpenSpec à travers les blocs de code, en temps linéaire (AUD-QUA-29, AUD-SEC-20)

## Why

Deux constats Élevés de l'audit du 17/11/2026 (`docs/project/audits/2026-11-17/findings.md`, constats `AUD-QUA-29` et
`AUD-SEC-20`, mesurés sur `a04f32bd`, revérifiés sur `1a539c22`) visent les mêmes lignes des lecteurs OpenSpec.

- **`AUD-QUA-29`** : `OpenSpecRequirementDeltaReader.requirementEnd(List<String>, int)` coupe une exigence ADDED ou
  MODIFIED sur un `## …` ou un `### Requirement: …` écrit en exemple dans un bloc de code, alors que
  `OpenSpecCurrentSpecificationReader.requirementEnd(List<String>, OpenSpecCodeFences, int)` saute les lignes clôturées.
  L'en-tête d'exigence du delta (boucle principale de `normalizeFile`) et ses deux recherches de scénario n'interrogent
  pas non plus la clôture. `OpenSpecRequirementDeltaReaderTest#aLevelTwoLineInsideACodeFenceNeitherEndsTheSectionNorWarns`
  épingle la troncature : il attend l'énoncé `The renderer SHALL render level-two headings, for example: ```markdown`.
  Une même exigence lue dans `specs/` garde son bloc entier : le delta promu et la spécification divergent sans
  diagnostic.
- **`AUD-SEC-20`** : neuf motifs de forme `(.+?)\s*$` (trois par lecteur : `OpenSpecCurrentSpecificationReader`,
  `OpenSpecRequirementDeltaReader`, `OpenSpecChangeMetadataReader`) ont un coût quadratique sur une ligne longue
  d'espaces. L'audit mesure 19 s pour une ligne de 80 000 caractères ; la seule borne est `maxDocumentBytes` de
  `ProviderIngestionBudget.DEFAULT`, sans longueur de ligne. Une synchronisation HTTP lit ces fichiers sur un des workers
  de `DatabaseCallWorkers`, que l'interruption à l'échéance ne libère pas, et `java.util.regex` ne réagit pas à
  l'interruption.

Les deux constats touchent les mêmes constantes `REQUIREMENT_HEADING` et `SCENARIO_HEADING` et la même méthode
`requirementEnd`, que la section Sprint 3 de `docs/project/audits/2026-11-17/sprints.md` demande de ne toucher qu'une
fois : ils forment un seul changement. Deux correctifs séparés réécriraient deux fois les mêmes lignes et le second
devrait refaire les tests du premier.

## What Changes

- Le lecteur de deltas saute les lignes clôturées pour borner une exigence, reconnaître son en-tête et chercher ses
  scénarios : l'énoncé d'une exigence ADDED ou MODIFIED est celui que le lecteur de spécifications courantes rend pour
  le même texte.
- Les deux lecteurs de spécifications ignorent un `#### Scenario:` écrit dans un bloc de code.
- Les neuf motifs de titre, de scénario, d'étape, de décision et de tâche sont réécrits sans paire « capture paresseuse
  suivie d'espaces finaux » : la capture prend la fin de ligne, puis est rognée. Le coût de lecture d'une ligne devient
  linéaire en sa longueur.
- L'attente de `OpenSpecRequirementDeltaReaderTest#aLevelTwoLineInsideACodeFenceNeitherEndsTheSectionNorWarns` est
  réécrite : l'énoncé garde son bloc de code entier.
- Aucun code de diagnostic, aucun format persisté, aucune surface publique ne change. Les identités dérivées des titres
  ne changent pas : un titre rogné reste le même titre.

## Capabilities

### New Capabilities

### Modified Capabilities
- `openspec-provider-reading` : un delta suit les mêmes frontières Markdown qu'une spécification courante, un
  scénario en exemple dans un bloc de code n'est pas lu, et la lecture d'une ligne a un coût linéaire.

## Périmètre et exclusions

**Inclus :** `OpenSpecRequirementDeltaReader`, `OpenSpecCurrentSpecificationReader`, `OpenSpecChangeMetadataReader`
(`morpheus-provider-openspec`), leurs tests, la page durable `openspec-provider-reading`.

**Exclus :** la mutualisation de `requirementEnd`, de la provenance et de l'analyse des étapes entre les lecteurs, qui
est le constat `AUD-QUA-34` (sprint 8) et dépend de ce changement ; une longueur maximale de ligne dans
`ProviderIngestionBudget` (design.md, D2) ; l'interruptibilité d'un worker de base (`DatabaseCallWorkers`), qui
resterait vraie de tout calcul long.

## Acteurs et surfaces concernés

L'opérateur qui synchronise un workspace (CLI `sync`, HTTP local, remote WRITE) ; le contributeur d'un dépôt dont les
fichiers OpenSpec sont lus.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le delta ne consulte pas la clôture pour borner une exigence, son en-tête et ses scénarios | existant vérifié | `OpenSpecRequirementDeltaReader.requirementEnd`, `normalizeFile`, `normalizeRequirementDelta` |
| Le lecteur courant ne consulte pas la clôture pour chercher un scénario | existant vérifié | `OpenSpecCurrentSpecificationReader.normalizeRequirement` (deux boucles sur `SCENARIO_HEADING`) |
| Le test épingle l'énoncé tronqué | existant vérifié | `OpenSpecRequirementDeltaReaderTest#aLevelTwoLineInsideACodeFenceNeitherEndsTheSectionNorWarns` |
| Neuf motifs `(.+?)\s*$` | existant vérifié | constantes `Pattern` des trois lecteurs |
| 19 s pour 80 000 caractères, × 4 quand la longueur double | mesure datée | audit du 17/11/2026, OpenJDK 21 |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : correctif local d'un adaptateur, sans format persisté ni surface |

## Impact

`morpheus-provider-openspec` et ses tests. Aucun test d'architecture, aucune ligne de `contracts/public-surfaces.tsv`,
aucune OpenAPI. Ce changement **conditionne** `AUD-QUA-34` (sprint 8) : factoriser les lecteurs avant lui figerait
l'écart de clôture et les motifs quadratiques dans le code partagé.

## Questions ouvertes

Aucune.
