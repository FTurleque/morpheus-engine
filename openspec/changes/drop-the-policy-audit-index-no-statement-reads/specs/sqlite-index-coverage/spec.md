# Spec delta : sqlite-index-coverage

## ADDED Requirements

### Requirement: Le journal d'audit des policies n'est indexé que par sa clé d'ordre

Après application des migrations, le schéma NE DOIT PAS (MUST NOT) porter l'index `idx_policy_audit_pack`, et la page
comme le compte du journal d'audit d'un pack DOIVENT (MUST) rester servis par `idx_policy_audit_pack_order`, sans
parcours de table ni tri temporaire. Le retrait NE DOIT PAS (MUST NOT) changer un enregistrement, l'ordre d'une page
ni la valeur d'un compte.

#### Scenario: Une base neuve ne porte pas l'index retiré

- **GIVEN** un fichier de base qui n'existe pas
- **WHEN** le store des policies l'ouvre puis le ferme
- **THEN** `idx_policy_audit_pack` n'existe pas et `idx_policy_audit_pack_order` existe
- **AND** le registre `schema_migrations` porte une ligne par version supportée, migration du retrait comprise

#### Scenario: Une base mise à niveau perd l'index et garde son journal

- **GIVEN** une base au niveau de schéma de la dernière version publiée, dont le journal d'audit porte des
  enregistrements de plusieurs packs
- **WHEN** le store l'ouvre
- **THEN** `idx_policy_audit_pack` n'existe plus et chaque enregistrement est encore présent, avec la même clé d'ordre
- **AND** les pages lues après l'ouverture rendent les mêmes enregistrements dans le même ordre qu'avant

#### Scenario: La page et le compte restent servis par l'index de la clé d'ordre

- **WHEN** la garde de plan de requête explique, par le pilote du produit, le texte de la page et celui du compte que
  le store exécute
- **THEN** chaque plan cherche `idx_policy_audit_pack_order` et aucune ligne ne commence par `SCAN` ni ne contient
  `USE TEMP B-TREE`
- **AND** la garde échoue et nomme la requête si l'index de la clé d'ordre est retiré à son tour

#### Scenario: Un enregistrement sans pack reste refusé

- **GIVEN** une base migrée où les contraintes de clé étrangère sont actives
- **WHEN** un enregistrement d'audit est inséré pour un pack qui n'existe pas
- **THEN** l'insertion est refusée comme avant le retrait
- **AND** le journal ne gagne aucun enregistrement
