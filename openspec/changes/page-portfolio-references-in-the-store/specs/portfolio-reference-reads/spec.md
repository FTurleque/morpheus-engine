# Spec delta : portfolio-reference-reads

## Purpose

Définir comment les références inter-projets d'un portfolio sont lues : page découpée par le store, ordre déclaré,
compte, conflits, dédoublonnage d'un ajout et lecture de la requête DSL, identiques dans chaque store livré. Page
durable : `docs/developer/specifications/portfolio-reference-reads.md`.

## ADDED Requirements

### Requirement: Une page de références d'un portfolio est découpée par le store

Le port du portfolio DOIT (MUST) rendre une page de références d'un portfolio, ou de ses seules références dont un projet
donné est la source ou la cible, à partir d'un décalage et d'une limite ; les services NE DOIVENT PAS (MUST NOT) charger
toutes les références du portfolio pour rendre une page.

#### Scenario: Une page d'un grand portfolio

- **GIVEN** un portfolio qui porte plus de références que la limite demandée
- **WHEN** un client demande `GET /api/v1/portfolios/{portfolioId}/references` avec un décalage et une limite
- **THEN** le store remet au service au plus la limite de références, et la réponse est cette page
- **AND** aucune référence n'est écrite ni modifiée

#### Scenario: Une page restreinte à un projet

- **GIVEN** un portfolio dont un projet membre est source de certaines références et cible d'autres
- **WHEN** la page de références de ce projet est demandée
- **THEN** elle ne contient que les références dont ce projet est la source ou la cible, chacune une fois

#### Scenario: Un projet qui n'est pas membre

- **WHEN** la page de références d'un projet qui n'est pas membre du portfolio est demandée
- **THEN** la lecture est refusée avec `project is not a portfolio member: <projectId>`
- **AND** aucune référence n'est lue

#### Scenario: Une limite hors bornes

- **WHEN** la limite demandée est nulle ou dépasse la taille de page maximale
- **THEN** la lecture est refusée avec `limit must be between 1 and <maximum>`, comme avant

#### Scenario: Le plan de la page SQLite

- **WHEN** la garde de plan de requête explique, par le pilote du produit, les textes de page que le store exécute
- **THEN** la page de portée portfolio est servie par un index dans son ordre, sans `USE TEMP B-TREE` ni ligne qui
  commence par `SCAN`

### Requirement: Une page de références suit un ordre déclaré et total

Une page de références DOIT (MUST) suivre l'ordre des identités de référence, qui est total, en portée portfolio comme en
portée projet ; deux pages successives d'une même lecture NE DOIVENT PAS (MUST NOT) répéter ni omettre une référence.

#### Scenario: Les pages d'une lecture partitionnent ses références

- **WHEN** toutes les pages d'une même lecture sont demandées tour à tour avec la même limite
- **THEN** mises bout à bout, elles donnent chaque référence une fois, dans l'ordre des identités

#### Scenario: Un décalage au-delà du total

- **WHEN** le décalage est supérieur ou égal au nombre de références de la lecture
- **THEN** la page est vide

### Requirement: Le compte et les conflits d'un portfolio sont calculés par le store

La vue d'ensemble d'un portfolio DOIT (MUST) obtenir le nombre de ses références sans les recevoir, et la liste des
conflits DOIT (MUST) être construite à partir des seules références dont la source et la relation ont plusieurs cibles
distinctes, avec le même résultat qu'aujourd'hui.

#### Scenario: La vue d'ensemble d'un grand portfolio sans conflit

- **GIVEN** un portfolio qui porte de nombreuses références, aucune en conflit
- **WHEN** un client demande `GET /api/v1/portfolios/{portfolioId}`
- **THEN** la réponse porte le nombre exact de références et aucun conflit
- **AND** le store ne remet au service aucune référence

#### Scenario: Deux observations contradictoires

- **GIVEN** deux références de même source et même relation vers deux cibles différentes, et d'autres références sans
  conflit
- **WHEN** les conflits du portfolio sont demandés
- **THEN** un conflit est rendu, qui porte ces deux observations, et rien d'autre
- **AND** aucune des deux observations n'est retirée ni remplacée

### Requirement: Un ajout de référence se dédoublonne par une recherche ciblée

L'ajout d'une référence DOIT (MUST) chercher une observation identique (même clé sémantique, provider, localisateur
source et preuve) par une lecture ciblée du store, et rendre l'observation existante sans rien écrire ; il NE DOIT PAS
(MUST NOT) lire les autres références du portfolio.

#### Scenario: Une observation déjà enregistrée

- **GIVEN** une référence enregistrée dans un portfolio qui en porte beaucoup d'autres
- **WHEN** la même observation est ajoutée de nouveau
- **THEN** l'ajout rend la référence existante, avec son identité
- **AND** le portfolio ne gagne aucune référence

#### Scenario: Une observation qui diffère par sa preuve

- **GIVEN** une référence enregistrée
- **WHEN** une observation de même clé sémantique et même provider, mais d'une autre preuve, est ajoutée
- **THEN** une nouvelle référence est enregistrée et rendue
- **AND** la première est conservée telle quelle

### Requirement: La requête de portée portfolio sur les références lit au plus son budget plus une ligne

Une requête DSL de portée portfolio sur les références DOIT (MUST) demander au store au plus le budget de lignes source
plus une, et refuser au-delà du budget par `QUERY_SOURCE_BUDGET_EXCEEDED` au chemin `$.source.portfolioReferences`.

#### Scenario: Un portfolio au-delà du budget

- **GIVEN** un portfolio qui porte plus de références que le budget de lignes source
- **WHEN** une requête de portée portfolio sur les références est exécutée
- **THEN** elle est refusée par `QUERY_SOURCE_BUDGET_EXCEEDED` au chemin `$.source.portfolioReferences`
- **AND** le store n'a pas remis plus que le budget plus une référence

#### Scenario: Un portfolio sous le budget

- **WHEN** la même requête porte sur un portfolio sous le budget
- **THEN** elle rend les mêmes lignes, dans le même ordre, qu'avant le changement

### Requirement: Chaque store livré répond identiquement aux lectures de références

Le store mémoire et le store SQLite DOIVENT (MUST) implémenter eux-mêmes chaque lecture de références et rendre, pour un
même contenu, les mêmes références dans le même ordre, le même compte et les mêmes conflits.

#### Scenario: Parité sur un portfolio difficile

- **GIVEN** le même portfolio écrit dans les deux stores, avec des références dont les types d'entité et les relations
  sont préfixes les uns des autres, un conflit et une dernière page partielle
- **WHEN** chaque lecture est faite sur les deux stores
- **THEN** chaque comparaison est égale
