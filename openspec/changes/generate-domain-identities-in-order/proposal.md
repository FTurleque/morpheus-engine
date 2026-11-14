# Proposition : générer les identités de domaine dans l'ordre

## Why

`DomainIdentity.generate()` remplit les 12 bits `rand_a` du UUIDv7 au hasard. Deux identités créées dans la même
milliseconde se comparent donc dans un ordre tiré au sort, alors que l'application trie de nombreuses listes visibles
par identité (`ProposedChangeSet`, `ChangeAnalysisResult`, `RequirementChangeImpact`, `ChangeOrchestrationStateService`,
`TaskQualityService`, `BusinessContentQueryService`, `ChangeContextQueryService`) et que les stores SQLite paginent par
`ORDER BY id`. Deux ingestions de la même source rendent alors deux ordres différents.

Preuve : sur la PR 641, le check `exact-head (ubuntu-latest)` (run 43538053585) a échoué dans
`CliCommandTranscriptTest#eachCommandPrintsAndRefusesAsItsTranscriptSays()[63]` (transcription `analyze-change-json`) :
le seul écart est `add-remember-me/task-2` avant `task-1` dans `implementationTasks`. Le même check a échoué sur `main` à
`12cd8874`. `DomainIdentityTest#identitiesGeneratedInOneMillisecondCompareInTheOrderOfGeneration`, écrit avant le
correctif, échoue dès la deuxième identité.

## What Changes

- `DomainIdentity.generate()` place dans `rand_a` un compteur monotone par processus (RFC 9562, section 6.2, méthode 1),
  tenu par `MonotonicTimestampSequence` (`morpheus-domain`, `com.morpheus.domain.identity`). `rand_b` reste aléatoire.
- L'ADR DATA-0140 (archivé), qui écrivait `rand_a` aléatoire et aucun ordre dans une milliseconde, est remplacé par l'ADR consolidé
  DATA-0168 et archivé.
- Aucune surface publique, aucun format persisté ni schéma SQLite ne change : la valeur reste un UUIDv7 canonique.

## Capabilities

### New Capabilities
- `domain-identity-generation` : ordre des identités de domaine générées par un même processus.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** la génération de `DomainIdentity`, ses tests, l'ADR d'identité, la page durable de la capacité.

**Exclus :** l'ordre entre identités générées par deux processus dans la même milliseconde, que RFC 9562 ne garantit
pas et qu'aucun tri n'exige ; le remplacement des tris par identité par des clés métier (option écartée, voir
design.md, D1) ; les identités déjà persistées, relues telles quelles.

## Acteurs et surfaces concernés

Tout lecteur d'une liste triée par identité (CLI, MCP, HTTP) ; le mainteneur, qui accepte l'ADR DATA-0168.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| `rand_a` est tiré au hasard | existant vérifié | `DomainIdentity.generate()` avant ce changement |
| L'ordre d'une transcription CLI varie d'une exécution à l'autre | existant vérifié | run 43538053585, `analyze-change-json` |
| Un compteur dans `rand_a` rend l'ordre égal à l'ordre de génération dans un processus | décision | DATA-0168, `MonotonicTimestampSequenceTest` |
| Le changement exige un ADR | décision | `.claude/rules/adr.md` : la décision de DATA-0140 (archivé) change en partie |

## Impact

`morpheus-domain` (`DomainIdentity`, nouvelle classe `MonotonicTimestampSequence`) ; ADR DATA-0140 (archivé) remplacé, DATA-0168
proposé, liens des ADR et pages qui citaient DATA-0140 (archivé) ; `docs/developer/specifications/domain-identity-generation.md` ;
l'exemple de `.claude/rules/code-style.md`. Aucun test d'architecture modifié.

## Questions ouvertes

Aucune.
