# Spec delta : performance-budget-coverage

## ADDED Requirements

### Requirement: L'orchestration, le rapport qualité, l'évaluation de gouvernance et le portfolio ont un budget déclaré avant leur correction

L'état d'orchestration d'un change, le rapport qualité d'un snapshot, l'évaluation d'un pack de gouvernance et la
lecture d'une page de références d'un portfolio DOIVENT (MUST) chacun avoir une ligne datée dans
`docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md` qui donne l'opération, la fixture et la borne, validée avant tout
commit qui modifie le code qu'elle juge, et tenue par un gate que `validate-m19` lance sur les deux plateformes.

#### Scenario: Le budget précède la correction

- **GIVEN** une correction qui borne le coût de l'une des quatre opérations
- **WHEN** l'historique du dépôt est lu
- **THEN** le commit qui ajoute la ligne de cette opération est un ancêtre du commit de la correction
- **AND** aucune ligne existante du document des budgets n'a changé dans ce commit

#### Scenario: Le code non corrigé est mesuré

- **WHEN** l'un des quatre gates est exécuté pour la première fois, sur le code d'avant la correction
- **THEN** ses lignes `M19_METRIC` et son verdict sont consignés dans le document des budgets, que la borne soit tenue
  ou manquée

#### Scenario: Un budget manqué fait échouer la lane

- **WHEN** l'une des quatre opérations dépasse sa borne déclarée sur un environnement de référence
- **THEN** le validateur sort avec un code non nul et le job nocturne de cette plateforme échoue

### Requirement: Chacun des quatre budgets est mesuré sur une fixture qui tient les lignes avec lesquelles son coût croît

Le gate de l'état d'orchestration et celui du rapport qualité DOIVENT (MUST) mesurer un snapshot qui tient les nombres
de requirements, de changes et de critères du profil gelé ; le gate de gouvernance DOIT évaluer un pack qui tient le
nombre maximal de règles que le contrat de politique admet ; le gate du portfolio DOIT lire une page d'un portfolio
dont les références dépassent plusieurs pages. Chaque gate DOIT échouer avant de mesurer si sa fixture est plus petite.

#### Scenario: Le pack de gouvernance est au nombre maximal de règles

- **GIVEN** le gate de l'évaluation de gouvernance
- **WHEN** il construit son pack
- **THEN** le pack tient le nombre maximal de règles admis par pack, et le gate échoue avant de mesurer s'il en tient
  moins

#### Scenario: Le portfolio dépasse une page

- **GIVEN** le gate de la page de références d'un portfolio
- **WHEN** il a écrit sa fixture
- **THEN** le portfolio tient plus de références qu'une page n'en rend, et la page mesurée n'est pas la première

#### Scenario: Une fixture de portfolio ne déplace pas les mesures existantes

- **WHEN** le gate du portfolio écrit ses références
- **THEN** il les écrit dans une base à lui, et la taille de la base, le temps de publication et les mesures des
  autres gates sont ceux d'avant ce changement
