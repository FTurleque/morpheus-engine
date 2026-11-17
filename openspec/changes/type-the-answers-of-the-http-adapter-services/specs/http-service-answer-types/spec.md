## Purpose

Fixer que les services de l'adaptateur HTTP déclarent le type des réponses qu'ils remettent aux routeurs, pour que le
compilateur voie un changement de forme, sans qu'aucune réponse HTTP ne change. Page durable :
`docs/developer/specifications/http-service-answer-types.md`.

## ADDED Requirements

### Requirement: A public method of an HTTP adapter service SHALL declare the type of the answer it returns

Une méthode publique d'une classe de service de l'adaptateur HTTP (classe `*ApiService` du paquet des services) NE
DOIT PAS (MUST NOT) déclarer `Object` comme type de retour, et la suite d'architecture DOIT (MUST) échouer en nommant
la classe et la méthode sinon.

#### Scenario: Un service ajouté rend Object
- **GIVEN** un service de l'adaptateur HTTP qui déclare `public Object summary()`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le service et la méthode, avec le remède : déclarer le type réellement rendu
- **AND** aucun fichier n'est réécrit par le test

#### Scenario: Une méthode non publique
- **WHEN** un service déclare une méthode privée qui rend `Object`
- **THEN** la règle ne la juge pas

#### Scenario: La règle a quelque chose à juger
- **WHEN** aucune classe ne correspond plus à la sélection des services
- **THEN** la suite échoue au lieu de passer sur une sélection vide

### Requirement: Typing the answer of an HTTP adapter service SHALL change no HTTP response

Le typage des réponses NE DOIT PAS (MUST NOT) changer le statut, les en-têtes ni le corps d'une réponse HTTP, ni le
contenu comparé entre HTTP, MCP et CLI pour un type partagé.

#### Scenario: Les réponses figées restent identiques
- **GIVEN** les tests qui figent octet pour octet les réponses des routeurs
- **WHEN** ils s'exécutent après le typage
- **THEN** ils passent sans qu'aucune valeur attendue ne soit modifiée
- **AND** rien n'est persisté par ces lectures au-delà de ce qu'elles persistaient

#### Scenario: Le contenu partagé reste convergent
- **WHEN** la comparaison des surfaces publiques sur `openspec-basic` et `synthetic-basic` s'exécute
- **THEN** chaque type partagé a la même forme sur HTTP, MCP et CLI qu'avant le typage
