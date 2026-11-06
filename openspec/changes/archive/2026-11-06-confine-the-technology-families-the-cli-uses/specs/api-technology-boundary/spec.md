# Spec delta : api-technology-boundary

## Purpose

Dire quelles familles technologiques le module de transport `morpheus-api` peut atteindre, par quelles classes, et
comment le build le tient, pour le mainteneur qui y ajoute une dépendance technique. Page durable :
[docs/developer/specifications/api-technology-boundary.md](/docs/developer/specifications/api-technology-boundary.md).

## ADDED Requirements

### Requirement: Chaque famille technologique DOIT avoir un régime déclaré pour l'adaptateur HTTP

Le gate de l'adaptateur HTTP DOIT (MUST) déclarer, pour chaque famille du vocabulaire que partagent les gates de
familles, un régime parmi refusée, confinée à des classes nommées, libre, ou tenue par un autre gate nommé, avec sa
raison ; une famille du vocabulaire sans régime pour l'adaptateur HTTP DOIT faire échouer le build.

#### Scenario: Une famille sans régime

- **GIVEN** une famille de la déclaration partagée
- **WHEN** la suite d'architecture s'exécute sans que le gate de l'adaptateur HTTP lui donne un régime
- **THEN** elle échoue et nomme la famille

#### Scenario: Le régime de chaque famille aujourd'hui

- **WHEN** le gate de l'adaptateur HTTP est lu après ce changement
- **THEN** SQL, exécution de processus, environnement du processus et flux standard sont refusés ; bibliothèque JSON,
  réseau sortant, fils d'exécution et accès fichier sont confinés ; l'horloge murale est libre ; la réflexion et le
  chargement de classes sont tenus par le gate de réflexion du produit

### Requirement: Une famille refusée DOIT l'être dans tout `com.morpheus.api`

Aucune classe de production de `com.morpheus.api` NE DOIT (MUST NOT) atteindre une famille que le gate de l'adaptateur
HTTP déclare refusée, et le build DOIT échouer en nommant la classe et la famille sinon.

#### Scenario: Le serveur lit l'environnement du processus

- **WHEN** une classe de `com.morpheus.api` appelle `System.getenv`
- **THEN** la suite d'architecture échoue et nomme la classe et la famille « process environment »

#### Scenario: Une classe du serveur acquiert une dépendance SQL

- **WHEN** une classe de `com.morpheus.api` dépend de `java.sql`
- **THEN** la suite d'architecture échoue et nomme la classe et la famille « SQL »

### Requirement: Une famille confinée DOIT n'être atteinte que par les classes que le gate de l'adaptateur HTTP nomme pour elle

Une classe de `com.morpheus.api` qui atteint une famille confinée DOIT (MUST) figurer dans la liste que le gate de
l'adaptateur HTTP tient pour cette famille, et le build DOIT échouer en nommant la classe et la famille sinon.

#### Scenario: Une classe non nommée ouvre une connexion

- **WHEN** une classe de `com.morpheus.api` que le gate ne nomme pas pour le réseau sortant dépend de `java.net.http`
- **THEN** la suite d'architecture échoue et nomme cette classe et la famille « outbound network »

#### Scenario: Un service construit son propre JsonMapper

- **WHEN** une classe de `com.morpheus.api` autre que le décodeur nommé dépend de `tools.jackson`
- **THEN** la suite d'architecture échoue et nomme la classe et la famille « JSON library »

### Requirement: Une classe nommée pour une famille confinée de l'adaptateur HTTP DOIT encore l'atteindre

Chaque liste du gate de l'adaptateur HTTP DOIT (MUST) être exactement l'ensemble des classes de `com.morpheus.api` qui
atteignent la famille, mesuré sur le bytecode : une classe nommée qui ne l'atteint plus DOIT faire échouer le build
jusqu'à son retrait de la liste.

#### Scenario: Une classe nommée n'atteint plus sa famille

- **WHEN** une classe nommée pour une famille cesse d'en dépendre
- **THEN** la suite d'architecture échoue et demande de retirer son nom de la liste

### Requirement: Les listes de classes du gate de l'adaptateur HTTP DOIVENT être des tables de faits déclarées

Chaque liste de classes que tient le gate de l'adaptateur HTTP DOIT (MUST) être déclarée table de faits auprès du test
qui classe les listes de classes de production, et chaque entrée DOIT porter la raison pour laquelle cette classe
atteint la famille.

#### Scenario: Une liste de l'adaptateur HTTP non déclarée

- **WHEN** le gate de l'adaptateur HTTP tient une liste que le test qui classe les listes ne déclare pas
- **THEN** la suite d'architecture échoue et nomme le gate et la liste

#### Scenario: Aucun cliquet de dette n'est créé

- **WHEN** le changement est livré
- **THEN** `config/debt-counter-ratchets.properties` ne porte aucune clé pour une liste de l'adaptateur HTTP
