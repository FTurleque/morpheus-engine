# Spec delta : interrupted-publication-recovery

## Purpose

Définir la reprise des publications interrompues : quels snapshots elle lit, lesquels elle fait échouer, avec quel
compte rendu, et ce que chaque store livré fournit pour elle. Page durable :
`docs/developer/specifications/interrupted-publication-recovery.md`.

## ADDED Requirements

### Requirement: La reprise ne lit que les snapshots inachevés de chaque projet

Le port des snapshots DOIT (MUST) offrir la lecture des snapshots d'un projet aux états `BUILDING` ou `VALIDATING`, et
la reprise DOIT (MUST) lire ses candidats par elle ; elle NE DOIT PAS (MUST NOT) lire les snapshots publiés, retirés
ou échoués d'un projet. Côté SQLite, la lecture DOIT (MUST) être servie par un index, sans parcours de table.

#### Scenario: Un long historique publié ne s'ajoute pas au coût de la reprise

- **GIVEN** un projet qui porte de nombreux snapshots `RETIRED` et `FAILED`, un snapshot `ACTIVE` et un snapshot
  `BUILDING` créé avant le seuil
- **WHEN** une commande CLI qui travaille sur des snapshots publiés démarre
- **THEN** le store ne rend à la reprise que le snapshot `BUILDING`
- **AND** ce snapshot passe à `FAILED` et les autres gardent leur état

#### Scenario: Le plan de la lecture SQLite cherche l'index de l'état

- **WHEN** la garde de plan de requête explique, par le pilote du produit, le texte de la lecture des snapshots
  inachevés que le store exécute
- **THEN** le plan cherche `idx_knowledge_snapshots_project_state` et aucune ligne ne commence par `SCAN`

#### Scenario: Un projet sans snapshot inachevé

- **GIVEN** un projet dont tous les snapshots sont publiés ou retirés
- **WHEN** la reprise traite ce projet
- **THEN** le compte rendu du projet ne porte ni snapshot repris ni course
- **AND** aucun snapshot ne change d'état

### Requirement: La reprise fait échouer les seuls snapshots inachevés créés au plus tard au seuil

La reprise DOIT (MUST) passer à `FAILED` chaque snapshot `BUILDING` ou `VALIDATING` créé au plus tard au seuil de sa
politique, NE DOIT PAS (MUST NOT) toucher un snapshot inachevé plus récent, et DOIT (MUST) signaler comme course, sans
le modifier, un candidat dont l'état a changé entre la lecture et la transition.

#### Scenario: Un candidat ancien et un candidat récent

- **GIVEN** un projet qui porte un snapshot `VALIDATING` créé avant le seuil et un snapshot `BUILDING` créé après
- **WHEN** la reprise s'exécute
- **THEN** le premier est rendu comme repris et passe à `FAILED`
- **AND** le second reste `BUILDING`

#### Scenario: Un candidat publié pendant la reprise

- **GIVEN** un candidat lu par la reprise, puis passé à un autre état par un autre écrivain
- **WHEN** la reprise tente sa transition
- **THEN** le compte rendu le porte comme course et la métrique `snapshot.recovery.race` augmente
- **AND** son état est celui que l'autre écrivain a posé

#### Scenario: L'ordre des projets et des candidats est inchangé

- **GIVEN** deux projets portant chacun un candidat ancien
- **WHEN** la reprise s'exécute
- **THEN** les comptes rendus suivent l'ordre des identités de projet, et les candidats d'un projet l'ordre de
  création puis d'identité

### Requirement: Chaque store livré fournit lui-même la lecture des snapshots inachevés

Le store mémoire et le store SQLite DOIVENT (MUST) implémenter eux-mêmes la lecture des snapshots inachevés, et rendre
les mêmes snapshots dans le même ordre pour un même contenu.

#### Scenario: Parité des deux stores

- **GIVEN** le même jeu de snapshots, dans les six états, écrit dans le store mémoire et dans le store SQLite
- **WHEN** la lecture des snapshots inachevés est demandée pour chaque projet
- **THEN** les deux stores rendent la même suite de snapshots

#### Scenario: Un store livré qui hérite de la lecture par défaut

- **WHEN** un store livré cesse de déclarer sa propre lecture des snapshots inachevés
- **THEN** le test de parité échoue et nomme le store
