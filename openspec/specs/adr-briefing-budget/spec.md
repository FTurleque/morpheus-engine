# adr-briefing-budget Specification

## Purpose
Bound the sections of an architecture decision record that the Archgate briefing hands to agents, and measure them
the same way whatever the line endings of the working copy.

Documentation durable (vue consolidée, diagrammes, traçabilité) : [docs/developer/specifications/adr-briefing-budget.md](../../../docs/developer/specifications/adr-briefing-budget.md).

## Requirements

### Requirement: Chaque section d'un ADR que le briefing remet aux agents DOIT tenir dans la borne de ce briefing

Pour chaque ADR en vigueur de `docs/architecture/adr/`, les sections `Decision` et `Do's and Don'ts` DOIVENT (MUST)
compter chacune 2 000 caractères au plus, une section courant de sa ligne `## ` à la ligne `## ` suivante,
sous-sections `###` comprises, mesurée sans ses blancs de bord. Le build DOIT échouer en nommant chaque ADR, chaque
section et sa longueur hors borne.

#### Scenario: Une section Decision qui dépasse la borne

- **GIVEN** un ADR en vigueur dont la section `Decision` compte 2 001 caractères
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le fichier, `Decision` et la longueur 2 001
- **AND** aucun ADR n'est modifié par le contrôle

#### Scenario: Une section à la borne exacte

- **GIVEN** un ADR dont la section `Decision` compte exactement 2 000 caractères
- **WHEN** la suite d'architecture s'exécute
- **THEN** cet ADR n'est pas refusé

#### Scenario: Les sous-sections comptent

- **GIVEN** une section `Decision` dont le texte vit sous des titres `### 1.`, `### 2.`
- **WHEN** la section est mesurée
- **THEN** sa longueur inclut ces sous-sections et leurs titres

#### Scenario: Les sections Do's and Don'ts sont bornées aussi

- **GIVEN** un ADR dont la section `Do's and Don'ts` dépasse 2 000 caractères
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le fichier et `Do's and Don'ts`

#### Scenario: Les ADR archivés ne sont pas lus

- **GIVEN** un ADR de `docs/architecture/adr/archive/` dont la section `Decision` dépasse la borne
- **WHEN** la suite d'architecture s'exécute
- **THEN** cet ADR n'est pas refusé

### Requirement: La mesure d'une section DOIT être indépendante des fins de ligne de la copie de travail

La longueur d'une section DOIT (MUST) être la même sur une copie de travail en LF et en CRLF, et un ADR dont une
section remise est absente ou vide DOIT faire échouer le build, pour qu'une extraction qui ne trouve aucune section ne
passe pas tous les ADR.

#### Scenario: Un registre extrait en CRLF

- **GIVEN** une copie de travail Windows avec `core.autocrlf=true`, où chaque ligne des ADR finit par `\r\n`
- **WHEN** la suite d'architecture s'exécute
- **THEN** chaque section est mesurée à la même longueur que sur une copie en LF
- **AND** un dépassement y est refusé comme sur la CI Linux

#### Scenario: Une section remise absente

- **GIVEN** un ADR en vigueur sans section `## Do's and Don'ts`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le fichier et la section absente

#### Scenario: Un registre sans ADR lu

- **WHEN** le contrôle ne lit aucun ADR en vigueur
- **THEN** il échoue au lieu de passer
