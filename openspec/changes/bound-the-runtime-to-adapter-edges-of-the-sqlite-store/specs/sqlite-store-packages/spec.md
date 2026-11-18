# Spec delta : sqlite-store-packages

## ADDED Requirements

### Requirement: Le runtime du store SQLite ne dépend de son adaptateur que par des arêtes nommées

La suite d'architecture DOIT (MUST) refuser, lue sur les classes compilées et sur le texte hors commentaires des sources
du runtime, toute dépendance d'une classe de `com.morpheus.store.sqlite.runtime` vers une classe de
`com.morpheus.store.sqlite.adapter` qui ne figure pas, origine et cible, dans la liste exacte des arêtes tolérées.

#### Scenario: Une classe du runtime emploie un store

- **GIVEN** la liste exacte des arêtes tolérées
- **WHEN** une classe du runtime qui n'y figure pas vient à dépendre d'une classe de l'adaptateur
- **THEN** la suite d'architecture échoue et nomme l'origine et la cible
- **AND** aucune autre liste ne permet de l'admettre

#### Scenario: Une classe nommée emploie un store de plus

- **WHEN** la classe de maintenance hors ligne vient à dépendre d'un store de l'adaptateur qu'elle n'employait pas
- **THEN** la suite d'architecture échoue et nomme l'arête ajoutée

#### Scenario: Une arête qui ne laisse qu'une mention dans le texte

- **GIVEN** une source du runtime qui nomme une classe de l'adaptateur sans que le bytecode en garde la dépendance
- **WHEN** la liste est comparée au texte des sources du runtime
- **THEN** la suite d'architecture échoue et nomme le fichier et la classe nommée

#### Scenario: Le code en vigueur passe

- **WHEN** la règle est évaluée sur les classes de production, avec les arêtes listées
- **THEN** la suite passe sans que la liste ni le code de production changent

### Requirement: Les arêtes du runtime vers l'adaptateur forment une liste exacte qui ne fait que rétrécir

Les arêtes tolérées DOIVENT (MUST) être un compteur de dette déclaré sous l'ADR accepté qui le nomme : chaque arête listée
DOIT (MUST) être encore mesurée, et la taille de la liste DOIT (MUST) égaler sa clé de
`config/debt-counter-ratchets.properties`.

#### Scenario: Une arête disparue reste listée

- **WHEN** une classe du runtime cesse de dépendre d'une classe de l'adaptateur qui figure dans la liste
- **THEN** la suite d'architecture échoue jusqu'à ce que l'entrée parte et que la clé baisse dans le même changement

#### Scenario: Une arête ajoutée sans relever la clé

- **WHEN** une arête est ajoutée à la liste sans que sa clé soit relevée
- **THEN** la suite d'architecture échoue parce que la liste dépasse ce que sa clé admet
- **AND** relever la clé exige un ADR consolidé qui nomme l'arête

#### Scenario: La liste sans ADR qui la nomme

- **WHEN** la liste est déclarée compteur de dette sous un ADR qui ne la nomme pas par `TestClass.CHAMP`
- **THEN** la suite d'architecture échoue et nomme la liste et l'ADR
