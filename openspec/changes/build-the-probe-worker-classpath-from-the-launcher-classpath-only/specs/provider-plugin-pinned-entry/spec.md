## ADDED Requirements

### Requirement: Le worker du probe DOIT être lancé avec le classpath de la JVM hôte seulement

La JVM enfant qui exécute le worker du probe DOIT (MUST) recevoir comme classpath la valeur de `java.class.path` de la
JVM hôte, et aucune autre propriété système NE DOIT (MUST NOT) le choisir. Une valeur absente ou vide DOIT faire
échouer le probe avec le refus nommé `current JVM classpath is unavailable`, sans lancer de processus.

#### Scenario: Une propriété d'outillage de test posée au lanceur est ignorée

- **GIVEN** une JVM hôte lancée avec `-Dsurefire.test.class.path` pointant vers un répertoire étranger
- **WHEN** un probe épinglé est lancé
- **THEN** la commande de la JVM enfant porte en `-cp` la valeur de `java.class.path` de l'hôte
- **AND** le répertoire étranger n'apparaît pas dans la commande

#### Scenario: Classpath de l'hôte indisponible

- **GIVEN** une JVM hôte dont `java.class.path` est vide
- **WHEN** un probe épinglé est lancé
- **THEN** il échoue avec le message `current JVM classpath is unavailable`
- **AND** aucun processus enfant n'est lancé et aucun fichier de résultat n'est écrit

### Requirement: Aucune source principale NE DOIT lire une propriété de l'outillage de test

Aucune source sous `src/main/java` d'un module du réacteur NE DOIT (MUST NOT) lire une propriété système dont le nom
commence par `surefire.`, et le build DOIT (MUST) échouer en nommant le fichier et la ligne sinon. Les sources de test
ne sont pas concernées.

#### Scenario: Une lecture de propriété Surefire dans le code principal fait échouer le build

- **GIVEN** une source principale qui appelle `System.getProperty("surefire.test.class.path")`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le fichier et la ligne
- **AND** aucun fichier n'est réécrit

#### Scenario: Les sources de test gardent leur lecture

- **GIVEN** une source de test qui lit `surefire.test.class.path` pour lancer un processus enfant
- **WHEN** la suite d'architecture s'exécute
- **THEN** le contrôle ne la signale pas, et il a lu au moins une source principale
