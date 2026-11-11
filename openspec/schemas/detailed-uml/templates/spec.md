# Spec delta : <!-- capability-path -->

<!-- Marqueurs OpenSpec à garder en anglais : `## Purpose`, `## ADDED|MODIFIED|REMOVED|RENAMED
     Requirements`, `### Requirement:`, `#### Scenario:` (quatre #), et les étapes
     **GIVEN** / **WHEN** / **THEN** / **AND**. Aucune autre section `##` : l'archivage ne la
     reporte pas. Diagrammes et annexes vont dans design.md ou dans la page durable.
     Liens écrits depuis la racine du dépôt (/docs/...), seuls à survivre à l'archivage. -->

## Purpose
<!-- Capacité NOUVELLE uniquement, sinon supprimer cette section. Une à deux phrases
     (50 caractères au moins) : à quoi sert la capacité, pour qui. Terminer par le renvoi à
     la page durable /docs/developer/specifications/<capability-path>.md. -->

## ADDED Requirements

### Requirement: <!-- phrase descriptive et stable : c'est la clé de synchronisation -->
<!-- Un seul comportement, 500 caractères au plus. Le sujet (le système, le serveur distant,
     la commande, le gate) DOIT (MUST) ou NE DOIT PAS (MUST NOT) faire quoi, dans quelles
     conditions. Pas d'exemple ici : ils vont dans les scénarios. -->

#### Scenario: <!-- cas nominal -->
- **GIVEN** <!-- situation initiale : état, données, rôle de l'acteur -->
- **WHEN** <!-- action ou événement -->
- **THEN** <!-- résultat observable : réponse, sortie, code, verdict -->
- **AND** <!-- effet persisté, ou son absence -->

#### Scenario: <!-- erreur ou refus -->
- **GIVEN** <!-- situation initiale -->
- **WHEN** <!-- entrée invalide, droit manquant, ressource absente, limite atteinte -->
- **THEN** <!-- erreur attendue : code, message ou état de refus nommé -->
- **AND** <!-- rien n'est persisté ni modifié, ou l'état exact laissé -->

#### Scenario: <!-- cas limite, seulement s'il porte un risque réel -->
- **GIVEN** <!-- borne, valeur vide, concurrence, donnée existante d'une version antérieure -->
- **WHEN** <!-- événement -->
- **THEN** <!-- comportement à la borne -->

<!-- ## MODIFIED Requirements
     Copier le bloc ENTIER de openspec/specs/<capability-path>/spec.md, titre identique,
     puis l'éditer. Ne jamais découper, raccourcir ni traduire un texte existant.

## REMOVED Requirements

### Requirement: <titre exact existant>
**Reason**: <pourquoi le comportement disparaît>
**Migration**: <ce que l'acteur fait à la place>

## RENAMED Requirements
- FROM: `### Requirement: <ancien titre>`
- TO: `### Requirement: <nouveau titre>` -->
