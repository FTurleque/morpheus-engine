## Purpose

Garantir que le classificateur du verdict SonarQube Cloud ne lit que l'analyse produite par le scanner du job et
n'envoie le jeton Sonar qu'à l'hôte configuré. Page durable à créer :
`docs/developer/specifications/sonar-quality-gate-classification.md`.

## ADDED Requirements

### Requirement: Le classificateur DOIT lire le rapport de tâche au chemin exact du module racine

`scripts/classify-sonar-quality-gate.sh` DOIT (MUST) lire `target/sonar/report-task.txt` sous la racine du workspace et
NE DOIT PAS (MUST NOT) choisir un autre fichier de ce nom trouvé ailleurs dans l'arbre. Un rapport absent à ce chemin
DOIT faire échouer le job par le refus nommé qui dit que le scanner a échoué avant de le produire.

#### Scenario: Un rapport planté dans un module n'est pas lu

- **GIVEN** un workspace qui porte `target/sonar/report-task.txt` à la racine et un autre `report-task.txt` sous
  `morpheus-api/target/sonar/`
- **WHEN** le classificateur s'exécute
- **THEN** il lit le fichier de la racine seulement

#### Scenario: Un rapport absent à la racine fait échouer le job

- **GIVEN** un workspace sans `target/sonar/report-task.txt` à la racine mais avec un tel fichier dans un module
- **WHEN** le classificateur s'exécute
- **THEN** il échoue par « refusing to hide a technical scanner failure »
- **AND** aucune requête HTTP n'est émise

### Requirement: Le classificateur NE DOIT PAS envoyer le jeton Sonar hors de l'hôte configuré

Avant toute requête, le classificateur DOIT (MUST) vérifier que `ceTaskUrl` et `serverUrl` commencent par
`https://sonarcloud.io/` (ou, pour `serverUrl`, l'égalent sans barre finale) et échouer sinon par une erreur nommée,
sans envoyer `SONAR_TOKEN` à aucune adresse.

#### Scenario: Un rapport qui désigne un autre hôte est refusé

- **GIVEN** un `report-task.txt` dont `ceTaskUrl` vaut `https://attacker.example/api/ce/task?id=1`
- **WHEN** le classificateur s'exécute
- **THEN** il échoue par une erreur qui nomme le champ refusé et l'hôte attendu
- **AND** aucune requête HTTP n'est émise, le jeton n'est envoyé nulle part

#### Scenario: Un hôte qui ne fait que commencer comme l'hôte attendu est refusé

- **GIVEN** un `serverUrl` qui vaut `https://sonarcloud.io.attacker.example`
- **WHEN** le classificateur s'exécute
- **THEN** il échoue par la même erreur nommée et n'émet aucune requête

#### Scenario: Un rapport conforme est classé comme aujourd'hui

- **GIVEN** un rapport dont les deux URL désignent `https://sonarcloud.io/`
- **WHEN** le classificateur s'exécute
- **THEN** il interroge la tâche puis le gate et rend `OK`, `ERROR` ou `NONE` comme avant ce changement

#### Scenario: Le contrat refuse un classificateur sans contrôle d'hôte

- **WHEN** le contrôle de l'hôte est retiré du script
- **THEN** la suite d'architecture échoue et nomme le script
