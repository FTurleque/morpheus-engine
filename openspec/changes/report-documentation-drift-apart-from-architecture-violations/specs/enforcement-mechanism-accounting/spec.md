# Spec delta : enforcement-mechanism-accounting

## ADDED Requirements

### Requirement: Un échec d'un contrôle de cohérence est rapporté comme une désynchronisation documentaire

À chaque exécution de la suite d'architecture, le build DOIT (MUST) classer chaque test en échec selon le marqueur de
cohérence de sa classe et rendre deux verdicts distincts, sur sa sortie et dans un fichier de preuve du module :
désynchronisation documentaire pour un échec d'une classe marquée, violation d'architecture pour tout autre. Chaque
verdict DOIT (MUST) nommer ses tests et le fichier DOIT (MUST) donner le nombre d'exécutions de chaque nature.

#### Scenario: Seul un contrôle de cohérence échoue

- **GIVEN** une page de `docs/` reformulée de sorte qu'une classe marquée de la suite échoue, le code restant conforme
- **WHEN** la suite d'architecture s'exécute
- **THEN** le build échoue, et son verdict nomme le test en échec comme désynchronisation documentaire
- **AND** le verdict de violation d'architecture est vide, et le fichier de preuve l'écrit

#### Scenario: Une règle sur le code échoue

- **GIVEN** une violation d'une règle sur le bytecode introduite dans une classe de production
- **WHEN** la suite d'architecture s'exécute
- **THEN** le build échoue, et son verdict nomme le test en échec comme violation d'architecture
- **AND** aucun test n'est rangé comme désynchronisation documentaire

#### Scenario: Les deux natures échouent dans la même exécution

- **WHEN** un contrôle de cohérence et une règle sur le code échouent ensemble
- **THEN** les deux verdicts sont rendus, chacun avec ses seuls tests

#### Scenario: Aucun échec

- **WHEN** la suite d'architecture passe
- **THEN** le fichier de preuve est écrit avec deux verdicts vides et un nombre d'exécutions de classes marquées
  supérieur à zéro

### Requirement: Le verdict distinct ne change ni la sélection ni le compte des exécutions

Le verdict DOIT (MUST) être rendu dans l'exécution Surefire unique du module, sans second répertoire de rapports ni
renommage des rapports : chaque classe de la suite s'exécute une fois, ses rapports restent dans le répertoire que
lisent les validateurs de présence, et une invocation qui sélectionne des tests par `-Dtest` ou `-Dgroups` garde le
comportement qu'elle avait.

#### Scenario: Le compte des validateurs de présence

- **GIVEN** la même révision construite avec et sans l'écouteur du verdict
- **WHEN** le compte des exécutions déclarées du module est relevé dans `morpheus-architecture-tests/target/surefire-reports/`
- **THEN** les deux comptes sont égaux
- **AND** aucun rapport n'est écrit dans un autre répertoire

#### Scenario: Une seule classe marquée sélectionnée par -Dtest

- **WHEN** la suite est lancée avec `-Dtest` qui ne désigne qu'une classe marquée
- **THEN** cette classe s'exécute et la commande réussit si ses tests passent
- **AND** le verdict ne porte que ses exécutions
