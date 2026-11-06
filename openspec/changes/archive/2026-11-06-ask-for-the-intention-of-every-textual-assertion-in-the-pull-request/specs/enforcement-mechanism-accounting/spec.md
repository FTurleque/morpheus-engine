# Spec delta : enforcement-mechanism-accounting

## ADDED Requirements

### Requirement: Le gabarit de pull request demande l'intention de chaque assertion textuelle ajoutée

Le dépôt DOIT (MUST) porter un gabarit de description de pull request, à l'emplacement que GitHub lit, dont une
section demande, pour chaque assertion textuelle ajoutée ou modifiée dans la suite d'architecture, la phrase
d'intention qui la justifie et le mécanisme retenu, assertion textuelle ou règle sur le bytecode. La suite
d'architecture DOIT (MUST) échouer, en nommant le fichier, si le gabarit ou cette section manque.

#### Scenario: Le gabarit est présent avec sa section

- **GIVEN** le gabarit de pull request du dépôt, qui porte la section d'intention
- **WHEN** la suite d'architecture s'exécute
- **THEN** le contrôle du gabarit passe
- **AND** aucun fichier n'est écrit ni modifié par ce contrôle

#### Scenario: La section d'intention est retirée du gabarit

- **GIVEN** un gabarit de pull request dont la section d'intention a été supprimée
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue, nomme le chemin du gabarit et dit quelle section manque
- **AND** le gabarit reste tel qu'il a été édité

#### Scenario: Le gabarit est supprimé

- **GIVEN** un dépôt sans gabarit de pull request
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le chemin attendu du gabarit

#### Scenario: Une pull request laisse la section vide

- **GIVEN** une pull request dont la description laisse vide la section d'intention alors qu'elle ajoute une assertion
  textuelle
- **WHEN** la suite d'architecture s'exécute sur sa branche
- **THEN** la suite ne juge pas la description, qu'aucun fichier du dépôt ne porte
- **AND** le refus, s'il a lieu, est celui de la revue

### Requirement: Le gabarit de pull request demande la justification d'une modification de gouvernance

Le gabarit de description de pull request DOIT (MUST) porter une section qui demande la justification de toute
modification d'un fichier de gouvernance : manifeste des surfaces publiques, fichiers de ratchets et de compteurs de
dette sous `config/`, contrats OpenAPI, tests de la suite d'architecture. La suite d'architecture DOIT (MUST) échouer, en
nommant le fichier, si cette section manque.

#### Scenario: La section de gouvernance est retirée du gabarit

- **GIVEN** un gabarit de pull request qui garde la section d'intention mais a perdu celle de gouvernance
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue, nomme le chemin du gabarit et dit que la section de gouvernance manque
- **AND** aucun autre fichier n'est lu ni modifié

#### Scenario: La section nomme les fichiers de gouvernance

- **GIVEN** le gabarit de pull request du dépôt
- **WHEN** la suite d'architecture lit sa section de gouvernance
- **THEN** elle y trouve le manifeste `contracts/public-surfaces.tsv`, les fichiers de ratchets sous `config/`, les
  contrats OpenAPI et la suite d'architecture, et passe
