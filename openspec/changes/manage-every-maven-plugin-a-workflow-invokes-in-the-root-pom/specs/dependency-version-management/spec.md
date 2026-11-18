## ADDED Requirements

### Requirement: Un plugin Maven qu'un workflow ou un validateur appelle par coordonnées DOIT avoir sa version décidée par le POM racine

Pour tout appel `groupe:artefact[:version]:but` écrit dans `.github/workflows/*.yml` ou dans un script de `scripts/`,
le POM racine DOIT (MUST) déclarer ce plugin avec sa version, et une version écrite dans l'appel DOIT égaler celle que
le POM déclare. Le build DOIT échouer sinon, en nommant le fichier, l'appel et, s'il y en a une, la version du POM.

#### Scenario: Le scanner Sonar est visible du robot de mises à jour

- **GIVEN** le POM racine et `ci.yml` après ce changement
- **WHEN** la suite d'architecture s'exécute
- **THEN** `sonar-maven-plugin` est déclaré dans le `<pluginManagement>` racine avec une version portée par une
  propriété, et le job `sonar` l'appelle sans version
- **AND** le test passe

#### Scenario: Un plugin appelé hors du POM fait échouer le build

- **GIVEN** un workflow qui appelle `org.example:some-maven-plugin:1.0.0:run` et un POM racine qui ne déclare pas ce
  plugin
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le workflow et l'appel
- **AND** aucun fichier n'est réécrit par le test

#### Scenario: Une version d'appel restée en arrière fait échouer le build

- **GIVEN** `scripts/validate-d2.sh` qui appelle `dependency-check-maven` à une version différente de celle que le POM
  racine déclare
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le script, l'appel et la version du POM

#### Scenario: Un appel avec la version gérée est admis

- **WHEN** `security.yml` appelle `dependency-check-maven` à la version que déclarent les profils du POM racine
- **THEN** l'appel n'est pas refusé

#### Scenario: Le contrôle a trouvé des appels à juger

- **WHEN** le contrôle ne trouve aucun appel de plugin par coordonnées dans les fichiers parcourus
- **THEN** il échoue au lieu de passer
