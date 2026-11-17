# Spec delta : sqlite-schema-history

## ADDED Requirements

### Requirement: La vérification de schéma d'une opération lit le registre en une requête sans relire les migrations

La vérification de schéma d'une opération DOIT (MUST) lire le registre `schema_migrations` en une seule requête et
NE DOIT PAS (MUST NOT) relire ni rehacher une ressource de migration que le processus a déjà lue ; le texte canonique
et les empreintes admises d'une migration sont lus au plus une fois par processus.

#### Scenario: Deux opérations successives sur une base à jour

- **GIVEN** un processus qui a déjà vérifié le schéma d'une base à jour une fois
- **WHEN** une seconde opération ouvre une portée sur la même base
- **THEN** sa vérification exécute une seule lecture du registre et ne charge aucune ressource de migration
- **AND** le registre de la base est inchangé

#### Scenario: Une base neuve reçoit toutes ses migrations

- **GIVEN** un fichier de base qui n'existe pas
- **WHEN** une opération l'ouvre
- **THEN** chaque migration supportée est appliquée et inscrite une fois au registre, avec l'empreinte de son texte
  canonique
- **AND** l'opération suivante sur cette base n'applique ni n'inscrit rien

#### Scenario: Une base en retard reçoit seulement ses migrations manquantes

- **GIVEN** une base au niveau de schéma de la dernière version publiée
- **WHEN** une opération l'ouvre
- **THEN** seules les migrations postérieures à ce niveau sont appliquées et inscrites
- **AND** les lignes déjà présentes au registre gardent l'empreinte qu'elles portaient

### Requirement: La vérification de schéma refuse à chaque opération un registre que le programme ne reconnaît pas

Chaque opération DOIT (MUST) comparer le registre lu à cette opération avec les migrations que le programme connaît, et
refuser par le message en vigueur une version future, un nom inconnu ou une empreinte qui n'est ni celle du texte
canonique ni celle de sa variante CRLF, même quand une opération précédente du même processus a accepté la même base.

#### Scenario: Un registre altéré entre deux opérations d'un même processus

- **GIVEN** un processus qui a ouvert une base à jour une première fois
- **WHEN** l'empreinte d'une migration appliquée est modifiée dans le registre, puis une seconde opération ouvre la base
- **THEN** la seconde opération est refusée avec `SQLite migration history mismatch for version <N>`
- **AND** aucune migration n'est appliquée et le registre garde la valeur altérée

#### Scenario: Une version future apparue entre deux opérations

- **GIVEN** un processus qui a ouvert une base à jour
- **WHEN** une ligne d'une version supérieure à la version supportée est ajoutée au registre, puis une opération
  ouvre la base
- **THEN** l'opération est refusée avec `SQLite schema version <N> is newer than supported <M>`
- **AND** rien n'est écrit dans la base

#### Scenario: Un registre écrit par un checkout CRLF reste accepté

- **GIVEN** une base dont le registre porte, pour chaque migration, l'empreinte de son texte en CRLF
- **WHEN** deux opérations successives du même processus l'ouvrent
- **THEN** les deux sont acceptées et le registre est inchangé

#### Scenario: La restauration hors ligne garde sa règle d'acceptation

- **GIVEN** une sauvegarde dont le registre porte une empreinte inconnue
- **WHEN** une restauration hors ligne confirmée la vérifie, après des opérations du même processus qui ont mémorisé
  les empreintes connues
- **THEN** la restauration est refusée avec sa raison explicite
- **AND** la base vivante est inchangée
