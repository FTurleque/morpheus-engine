---
name: milestone-quadruplet
description: Livrer ou compléter un milestone MORPHEUS avec ses quatre artefacts obligatoires - suite ArchUnit, validateurs dual-platform .ps1 et .sh, plan d'exécution, preuve de validation. À utiliser pour démarrer un nouveau milestone, vérifier qu'un milestone existant est complet, ou diagnostiquer l'échec d'un gate de milestone.
---

# Le quadruplet de milestone

Un milestone sans ses quatre artefacts est **incomplet**, et c'est vérifié par un test, pas par
une convention.

```
morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m<N>/   <- suite ArchUnit
scripts/validate-m<N>.ps1  +  scripts/validate-m<N>.sh                     <- dual-platform
docs/roadmap/M<N>_EXECUTION.md                                             <- plan
docs/validation/VALIDATION_M<N>.md                                         <- preuve
```

Ne jamais deviner le numéro du milestone courant ni du suivant : lire la section Milestones de
[.claude/CLAUDE.md](../../CLAUDE.md) et lister les répertoires réellement présents sous
`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/`. La plage évolue.
[live-numbers](../live-numbers/SKILL.md) affiche les suites existantes.

## La parité dual-platform n'est pas optionnelle

`.ps1` **et** `.sh`, et les deux valident la même chose. Un changement dans l'un se répercute
dans l'autre dans le même commit — le hook `post-edit` le rappelle dès qu'un validateur est
touché. Le dispatcher est `scripts/validate.cmd` → `scripts/validate.ps1`.

**Passer la version explicitement.** Chaque validateur a une version par défaut datée de son
milestone : un appel sans argument valide contre une version morte et échoue sur la version du
lanceur empaqueté. Lire la version courante avec [live-numbers](../live-numbers/SKILL.md) et la
passer. Le hook `pre-bash` avertit quand l'argument manque.

## Écrire la suite ArchUnit

Règle transverse → `LayerDependencyTest`. Règle de sous-plateforme → un `*ArchitectureTest` dédié
au milestone. Pour choisir entre une règle ArchUnit et une assertion textuelle, et pour les trois
obligations avant d'accepter une règle migrée, voir
[enforcement-choice](../enforcement-choice/SKILL.md).

Le gate du milestone doit inclure sa propre exigence de complétude : la suite du dernier
milestone livré vérifie elle-même l'existence de ses scripts, de son plan et de sa preuve.
Reproduire ce patron.

## Isolement d'une sous-plateforme application

Une nouvelle sous-plateforme sous `application` se déclare avec ses interdits. Les existantes
donnent l'échelle de rigueur attendue :

| Package | Interdits supplémentaires |
|---|---|
| `application.query.{dsl,saved,export}` | cli, mcp, api, store.memory, store.sqlite, provider.openspec, provider.markdown |
| `application.policy..` | idem **+ `provider.sdk`** |
| `application.reasoning..` | idem + **tout `provider..`** + **`application.lifecycle.mutation..`** |

Un **noyau pur** porte en plus des interdits textuels : pas de transport, pas de persistance, pas
d'exécution de processus. `rules/architecture.md` liste les chaînes exactes par package.

## Tester

- Un test de reproduction qui échoue **avant** le fix.
- JUnit 5 exclusivement.
- `morpheus-store-memory` ou `morpheus-provider-synthetic` en test ; jamais SQLite en unitaire,
  jamais de mock de SQLite.
- Les fixtures déterministes de `experiments/m0/fixtures/` plutôt que de nouveaux jeux ad hoc.
- Un cas d'échec se teste par `assertThrows` **et** une assertion sur le message ou le code de
  rejet : un échec survenu pour une autre raison ne doit pas faire passer le test.
- Quand un store change, la parité de persistance mémoire/SQLite est exigée.
- Construire `morpheus-provider-reference` **avant** les tests d'architecture : son JAR est lu
  depuis `target/`.
- Jamais de champ `static` mutable partagé entre tests. Jamais de `@Disabled` sans justification
  et ticket.

## Budgets de performance

Les gates de performance sont des budgets **prédéclarés** sur fixtures larges déterministes. Une
régression de perf casse le build : elle ne se corrige pas en relevant le budget.

## Vérifier

```bash
./mvnw test -pl morpheus-architecture-tests -Dtest=*M<N>*
./mvnw clean verify
```

Puis le validateur du milestone, avec la version courante. Son verdict et ses écarts arrivent
filtrés — voir [rules/tooling.md](../../rules/tooling.md). Le relevé complet reste écrit dans
`validation-output/m<N>/validation-summary.txt`.

## Interdit prioritaire

**Jamais casser un gate passant.** C'est un bloqueur avant tout autre travail.
