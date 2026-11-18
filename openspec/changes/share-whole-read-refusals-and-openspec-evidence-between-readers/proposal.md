# Proposition : partager les refus de lecture complète et l'analyse des lecteurs OpenSpec (AUD-QUA-34)

## Why

Deux recopies entretiennent des divergences entre lecteurs de spécifications.

- Le refus de budget est recopié entre le lecteur synthetic (`SyntheticSpecificationContentReader.budgetRefusal`,
  lignes 139 à 161) et le lecteur Markdown structuré (`StructuredMarkdownSpecificationContentReader.budgetRefusal`,
  lignes 134 à 156), identique hors libellé. Les branches voisines du refus d'une source illisible ont déjà divergé : le
  lecteur synthetic n'écrit pas la clé `provider` dans les détails du diagnostic et laisse la raison de catégorie vide,
  le lecteur Markdown la remplit du message de l'exception. Un même refus ne se lit donc pas de la même façon d'un
  provider à l'autre.
- Les trois lecteurs OpenSpec recopient `provenance` et `evidence` (`OpenSpecCurrentSpecificationReader` 402 à 432,
  `OpenSpecRequirementDeltaReader` 461 à 491, `OpenSpecChangeMetadataReader` 414 à 446), et deux d'entre eux la boucle
  d'analyse des étapes d'un scénario (`normalizeScenario`, 324 et 337). Les écarts de clôture de code d'AUD-QUA-29 sont
  nés de cette recopie : une correction faite dans un lecteur ne descend pas dans son jumeau.

Preuve : constat `AUD-QUA-34` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd`. Revérifié le 17/11/2026 sur la tête de la branche : les lignes citées sont exactes, et la comparaison
trouve une divergence que l'audit dit « toute de nommage » : `evidence` borne l'intervalle de lignes au document dans le
lecteur de deltas, pas dans les deux autres (design.md, Contexte).

## What Changes

- Une seule écriture du refus de lecture complète (budget dépassé, source illisible) pour les lecteurs synthetic et
  Markdown structuré, dans `com.morpheus.application.read`, à côté de `ProviderIngestionBudget` : même code, mêmes clés de
  détail, même raison de catégorie ; seul le nom du provider et du groupe change.
- Une seule écriture de la provenance, de la preuve citée et de l'analyse des étapes GIVEN/AND/WHEN/THEN pour les trois
  lecteurs OpenSpec, dans des classes package-private du module OpenSpec.
- Aucun code de diagnostic, aucune route, aucun format persisté ne change.

## Capabilities

### New Capabilities

### Modified Capabilities
- `provider-ingestion-budget` : ajoute qu'un refus de lecture complète s'écrit de la même façon par les providers
  synthetic et Markdown structuré.

## Périmètre et exclusions

**Inclus :** les deux lecteurs de contenu synthetic et Markdown structuré, les trois lecteurs OpenSpec, une classe
nouvelle de `morpheus-application` et deux de `morpheus-provider-openspec`, leurs tests.

**Exclus :** la réécriture des motifs d'expression régulière des lecteurs OpenSpec (AUD-SEC-20) et l'alignement de leur
traitement des blocs de code (AUD-QUA-29), deux constats du sprint 3 qui **précèdent** ce change : factoriser avant
figerait dans le code partagé des motifs quadratiques et l'écart de clôture. Le refus de budget du lecteur OpenSpec, de
forme propre (design.md, question ouverte).

## Acteurs et surfaces concernés

L'opérateur qui lit le refus d'une lecture synthetic ou Markdown (CLI, HTTP, MCP relaient le diagnostic) ; le
mainteneur d'un lecteur.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| `budgetRefusal` est identique hors libellé entre synthetic et Markdown | existant vérifié | lecture des deux méthodes, 17/11/2026 |
| Le refus d'une source illisible diverge (clé `provider`, raison de catégorie) | existant vérifié | `SyntheticSpecificationContentReader:122-134`, `StructuredMarkdownSpecificationContentReader:112-130` |
| `evidence` borne l'intervalle au document dans le seul lecteur de deltas | existant vérifié | `diff` des trois méthodes, design.md |
| Le refus de source illisible prend la forme Markdown (avec `provider`) | proposition à valider | design.md D2 |
| Ce change attend AUD-QUA-29 et AUD-SEC-20 | décision | arêtes `depends_on` de l'audit |

## Impact

- Modules : `morpheus-application` (`com.morpheus.application.read`), `morpheus-provider-synthetic`,
  `morpheus-provider-markdown`, `morpheus-provider-openspec`.
- Aucun ADR : factorisation et alignement de diagnostics, réversibles (`.claude/rules/adr.md`) ; BE-0028 (contrat de
  lecture unifié) reste la décision qui couvre la forme d'un `ProviderReadResult` refusé.
- Aucune ligne de `contracts/public-surfaces.tsv` ni de l'OpenAPI.
- Documentation : `docs/developer/specifications/provider-ingestion-budget.md`,
  `docs/developer/specifications/openspec-provider-reading.md`.
- Dépendances : AUD-QUA-29 et AUD-SEC-20 (sprint 3) s'appliquent avant.

## Questions ouvertes

- Le refus de budget du lecteur OpenSpec (diagnostic `invalidSource("ingestion-budget", …)`) doit-il rejoindre la même
  écriture ? Peut attendre : design.md, Questions ouvertes.
