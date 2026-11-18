## ADDED Requirements

### Requirement: Les sessions simultanées d'un même pair sont bornées par processus
Le socle du cycle de vie DOIT (MUST) borner le nombre de sessions ouvertes en même temps vers un même pair dans un processus MORPHEUS. Une ouverture au-delà de la borne NE DOIT PAS (MUST NOT) attendre, lancer de processus ni copier le JAR du pair : elle échoue aussitôt par une raison nommée, que la passerelle rend comme une observation `UNAVAILABLE` qui nomme cette raison.

#### Scenario: Une ouverture au-delà de la borne
- **GIVEN** un pair NEXUS configuré dont autant de sessions que la borne sont ouvertes et en cours
- **WHEN** une requête de contexte augmenté demande une session de plus
- **THEN** l'observation rendue est `UNAVAILABLE` et nomme la raison de saturation du pair
- **AND** aucun processus pair n'est lancé et aucune copie du JAR n'est écrite pour cette requête

#### Scenario: Une session libérée rend sa place
- **GIVEN** un pair dont la borne est atteinte
- **WHEN** une session se ferme, normalement ou à son échéance
- **THEN** l'ouverture suivante est servie

#### Scenario: Les deux pairs ont des bornes séparées
- **GIVEN** un pair MINOS dont la borne est atteinte
- **WHEN** une requête ouvre une session NEXUS
- **THEN** la session NEXUS est servie

#### Scenario: La borne tient sous une rafale de lecteurs
- **GIVEN** un serveur remote dont un pair est configuré, et plus de requêtes de rôle READ simultanées vers ce pair que la borne
- **WHEN** elles sont traitées
- **THEN** jamais plus de sessions que la borne ne sont ouvertes vers ce pair, et les requêtes en surplus rendent leur worker sans attendre l'échéance du pair
