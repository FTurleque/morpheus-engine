## ADDED Requirements

### Requirement: The embedded help of the CLI SHALL be written by the help branch alone

Le texte entier de l'aide générale de la CLI DOIT (MUST) être écrit par la classe qui sert la commande `help`, en une
seule écriture déclenchée par cette commande. Le lanceur et toute autre classe de distribution NE DOIVENT PAS (MUST
NOT) écrire de texte d'aide, ni décider après coup qu'un appel était une demande d'aide. La sortie de l'aide DOIT
rester identique à l'octet.

#### Scenario: L'aide complète est imprimée par une seule classe
- **GIVEN** une CLI construite sur une base vide
- **WHEN** `morpheus help` est appelé, puis `morpheus --json help`, puis `morpheus` sans commande
- **THEN** chaque sortie standard est identique à l'octet à la transcription `help` enregistrée, sections MCP, API
  locale et serveur distant comprises, et le code de sortie est `0`
- **AND** rien n'est écrit sur la sortie d'erreur ni dans la base

#### Scenario: Une option globale devant la commande
- **WHEN** `morpheus --data-dir <rép> help` est appelé
- **THEN** la sortie est la même aide complète, sans qu'une seconde lecture des options globales décide de sa
  seconde moitié

#### Scenario: Le lanceur écrit une ligne d'aide
- **GIVEN** une ligne d'aide ajoutée dans le lanceur, écrite sur la sortie standard après le retour du gestionnaire
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme la classe qui écrit, avec le remède : écrire la ligne dans la branche de l'aide
- **AND** aucun fichier n'est réécrit par le test

#### Scenario: Une demande d'aide refusée
- **WHEN** `morpheus --db` est appelé sans valeur
- **THEN** la commande est refusée par la ligne d'erreur d'usage, et aucun texte d'aide n'est écrit
