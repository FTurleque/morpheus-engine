# Spec delta : cli-technology-boundary

## Purpose

Dire quelles familles technologiques le module de transport `morpheus-cli` peut atteindre, par quelles classes, et
comment le build le tient, pour le mainteneur qui y ajoute une dépendance technique. Page durable :
[docs/developer/specifications/cli-technology-boundary.md](/docs/developer/specifications/cli-technology-boundary.md).

## ADDED Requirements

### Requirement: Chaque famille technologique DOIT avoir un régime déclaré pour la CLI

Le gate de la CLI DOIT (MUST) déclarer, pour chaque famille du vocabulaire que partagent les gates de familles, un
régime parmi refusée, confinée à des classes nommées, libre, ou tenue par un autre gate nommé, avec sa raison ; une
famille du vocabulaire sans régime pour la CLI DOIT faire échouer le build.

#### Scenario: Une famille ajoutée au vocabulaire

- **GIVEN** une famille ajoutée à la déclaration partagée pour le gate de l'application
- **WHEN** la suite d'architecture s'exécute sans que le gate de la CLI lui donne un régime
- **THEN** elle échoue et nomme la famille

#### Scenario: Le régime de chaque famille aujourd'hui

- **WHEN** le gate de la CLI est lu après ce changement
- **THEN** SQL, bibliothèque JSON et exécution de processus sont refusées ; réseau sortant, fils d'exécution, accès
  fichier, environnement du processus et flux standard sont confinés ; l'horloge murale est libre ; la réflexion et le
  chargement de classes sont tenus par le gate de réflexion du produit

### Requirement: Une famille refusée DOIT l'être dans tout `com.morpheus.cli`

Aucune classe de production de `com.morpheus.cli` NE DOIT (MUST NOT) atteindre une famille que le gate de la CLI
déclare refusée, et le build DOIT échouer en nommant la classe et la famille sinon.

#### Scenario: Une commande acquiert une dépendance SQL

- **WHEN** une classe de `com.morpheus.cli` dépend de `java.sql`
- **THEN** la suite d'architecture échoue et nomme la classe et la famille « SQL »
- **AND** aucune liste ne permet de l'admettre sans changer le régime de la famille

#### Scenario: Une commande écrit son JSON avec Jackson

- **WHEN** une classe de `com.morpheus.cli` dépend de `tools.jackson`
- **THEN** la suite d'architecture échoue et nomme la classe

### Requirement: Une famille confinée DOIT n'être atteinte que par les classes que le gate de la CLI nomme pour elle

Une classe de `com.morpheus.cli` qui atteint une famille confinée DOIT (MUST) figurer dans la liste que le gate de la
CLI tient pour cette famille, et le build DOIT échouer en nommant la classe et la famille sinon.

#### Scenario: Une seconde classe ouvre une connexion

- **GIVEN** le réseau sortant confiné à la classe qui lit le manifeste de mise à jour
- **WHEN** une autre classe de `com.morpheus.cli` dépend de `java.net.http`
- **THEN** la suite d'architecture échoue et nomme cette classe et la famille « réseau sortant »

#### Scenario: Une commande démarre ses propres fils

- **WHEN** une classe de commande non nommée appelle `Executors.newVirtualThreadPerTaskExecutor()` ou `Thread.ofVirtual()`
- **THEN** la suite d'architecture échoue et nomme la classe et la famille « fils d'exécution »

#### Scenario: Une commande écrit sur la sortie standard du processus

- **WHEN** une classe de commande non nommée lit `System.out` au lieu du flux qu'elle reçoit
- **THEN** la suite d'architecture échoue et nomme la classe et la famille « flux standard »

### Requirement: Une classe nommée pour une famille confinée DOIT encore l'atteindre

Chaque liste du gate de la CLI DOIT (MUST) être exactement l'ensemble des classes de `com.morpheus.cli` qui atteignent
la famille, mesuré sur le bytecode : une classe nommée qui ne l'atteint plus DOIT faire échouer le build jusqu'à son
retrait de la liste.

#### Scenario: La lecture du manifeste quitte la CLI

- **WHEN** la classe nommée pour le réseau sortant cesse d'en dépendre
- **THEN** la suite d'architecture échoue et demande de retirer son nom de la liste

#### Scenario: La règle a quelque chose à juger

- **WHEN** l'ensemble importé ne contient aucune classe de `com.morpheus.cli`
- **THEN** la suite échoue au lieu de passer

### Requirement: Les listes de classes du gate de la CLI DOIVENT être des tables de faits déclarées

Chaque liste de classes que tient le gate de la CLI DOIT (MUST) être déclarée table de faits auprès du test qui classe
les listes de classes de production, et chaque entrée DOIT porter la raison pour laquelle cette classe atteint la
famille.

#### Scenario: Une liste de la CLI non déclarée

- **WHEN** le gate de la CLI tient une liste que le test qui classe les listes ne déclare pas
- **THEN** la suite d'architecture échoue et nomme le gate et la liste

#### Scenario: Aucun cliquet de dette n'est créé

- **WHEN** le changement est livré
- **THEN** `config/debt-counter-ratchets.properties` ne porte aucune clé pour une liste de la CLI
