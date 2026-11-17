## ADDED Requirements

### Requirement: Les modules qui compilent contre le SDK provider DOIVENT être exactement ceux que le build nomme

L'ensemble des modules du réacteur dont le POM déclare `morpheus-provider-sdk` en portée de compilation DOIT (MUST)
égaler la liste qu'une règle de la suite d'architecture nomme, dans les deux sens, et le build DOIT échouer en nommant
chaque module en trop ou manquant. `morpheus-domain`, `morpheus-application` et les providers intégrés NE DOIVENT
(MUST NOT) jamais y figurer.

#### Scenario: Le launcher porte le SDK et pas le plugin de référence

- **GIVEN** l'arbre courant
- **WHEN** la règle s'exécute
- **THEN** `morpheus-cli`, module du launcher, figure parmi les modules qui compilent contre le SDK
- **AND** le POM de `morpheus-cli` ne déclare pas `morpheus-provider-reference`

#### Scenario: Un module ajoute le SDK sans être nommé

- **GIVEN** le POM de `morpheus-store-sqlite` qui déclare `morpheus-provider-sdk` en compilation
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme `morpheus-store-sqlite` comme module en trop
- **AND** aucun POM ni aucune liste n'est réécrit

#### Scenario: Un module nommé ne déclare plus le SDK

- **GIVEN** un module nommé par la règle dont le POM ne déclare plus `morpheus-provider-sdk`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le module à retirer de la liste

#### Scenario: Un provider intégré nommé est refusé

- **GIVEN** une liste qui nomme `morpheus-provider-openspec`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue en disant qu'un provider intégré ne dépend pas du SDK
