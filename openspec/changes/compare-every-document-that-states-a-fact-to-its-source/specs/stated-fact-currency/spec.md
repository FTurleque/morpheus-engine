# Spec delta : stated-fact-currency

## Purpose

Garantir qu'un document vivant qui recopie un fait dont la source est lisible par un test (valeur de cliquet, nom de
paquet) est comparé à cette source par le build, pour les agents et les contributeurs qui le lisent avant d'agir. Page
durable : [stated-fact-currency](/docs/developer/specifications/stated-fact-currency.md).

## ADDED Requirements

### Requirement: Une valeur de cliquet qu'une page de règles recopie sous le nom de sa clé DOIT égaler cette clé

Quand une page de `.claude/rules/` écrit, dans une même ligne de table, une clé de `config/m21-quality-ratchets.properties`
en span de code suivie d'une valeur, le build DOIT (MUST) comparer cette valeur à celle de la clé et échouer en nommant
la page, la clé et les deux valeurs ; une clé écrite que le fichier ne déclare pas DOIT aussi faire échouer le build.

#### Scenario: Un cliquet relevé sans la page de règles

- **GIVEN** la table de `.claude/rules/testing.md` qui recopie `testsMinimum`
- **WHEN** la clé est relevée dans `config/m21-quality-ratchets.properties` et la page n'est pas modifiée
- **THEN** la suite d'architecture échoue et nomme `.claude/rules/testing.md`, `testsMinimum` et les deux valeurs
- **AND** le fichier de cliquets reste la seule source que lisent les validateurs

#### Scenario: Une clé retirée du fichier

- **WHEN** une page de règles recopie une clé que `config/m21-quality-ratchets.properties` ne déclare plus
- **THEN** la suite d'architecture échoue et nomme la page et la clé

#### Scenario: Une mesure datée n'est pas comparée

- **GIVEN** un paragraphe daté de `.claude/rules/testing.md` qui rapporte une requalification passée, sans clé en span
  de code dans une ligne de table
- **WHEN** la suite d'architecture s'exécute
- **THEN** ce paragraphe n'est pas comparé et n'est pas réécrit

#### Scenario: Le contrôle a trouvé des valeurs à comparer

- **WHEN** le contrôle ne trouve aucune clé recopiée dans les pages de `.claude/rules/`
- **THEN** il échoue au lieu de passer

### Requirement: Un nom de paquet `com.morpheus` cité dans un changement OpenSpec actif DOIT exister dans les sources du réacteur

Tout nom de paquet commençant par `com.morpheus.` qu'un fichier Markdown de `openspec/changes/` hors `archive/` cite
DOIT (MUST) être un paquet, ou le préfixe d'un paquet, déclaré par une source principale ou de test d'un module du POM
racine ; le build DOIT échouer en nommant le fichier, la ligne et le paquet sinon. Un nom de classe qualifié est jugé
sur son paquet.

#### Scenario: Le paquet supprimé par un renommage

- **GIVEN** le `design.md` de `resolve-both-mcp-peers-through-one-settings-resolver` dont la ligne 13 cite, sans
  marque, le paquet que le renommage `681794cb` a supprimé
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le fichier, la ligne 13 et le paquet
- **AND** aucun fichier du changement n'est réécrit par le test

#### Scenario: Un paquet existant cité sous plusieurs formes

- **WHEN** un changement actif cite `com.morpheus.api..`, `com.morpheus.application.security.PeerLaunchSettingsResolver`
  ou `com.morpheus.transport.mcp`
- **THEN** aucune de ces mentions n'est refusée

#### Scenario: Une archive cite un ancien nom

- **WHEN** un fichier de `openspec/changes/archive/` cite un paquet qui n'existe plus
- **THEN** le contrôle ne le lit pas

#### Scenario: Aucun changement actif

- **WHEN** `openspec/changes/` ne contient aucun changement hors `archive/`
- **THEN** le contrôle passe sans rien lire, et son test sur corpus prouve qu'il refuse un paquet inexistant

### Requirement: Seule une mention marquée « (ancien nom) » ou « (à créer) » DOIT être admise pour un paquet absent des sources

Un paquet absent des sources DOIT (MUST) être admis quand son span de code est suivi, dans la même ligne, de la marque
« (ancien nom) » ou « (à créer) », et seulement alors ; aucune liste d'exceptions tenue dans le test NE DOIT (MUST NOT)
admettre une mention.

#### Scenario: Un renommage raconté

- **WHEN** un changement actif écrit `` `com.morpheus.integration.mcp` (ancien nom) `` pour dire d'où vient un paquet
- **THEN** la mention est admise

#### Scenario: Un paquet qu'un changement propose de créer

- **WHEN** un changement actif écrit le nom d'un paquet qui n'existe pas encore suivi de « (à créer) »
- **THEN** la mention est admise tant que le paquet n'existe pas

#### Scenario: Une marque restée après la création

- **WHEN** un paquet marqué « (à créer) » existe désormais dans les sources
- **THEN** la suite d'architecture échoue et demande de retirer la marque

#### Scenario: Une autre formulation n'est pas une marque

- **WHEN** la ligne dit « renommé depuis » ou « formerly » sans la marque
- **THEN** la mention est refusée comme un paquet inexistant
