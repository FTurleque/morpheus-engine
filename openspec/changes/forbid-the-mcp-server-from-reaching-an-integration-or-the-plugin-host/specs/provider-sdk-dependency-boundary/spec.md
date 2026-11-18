## ADDED Requirements

### Requirement: Le serveur MCP NE DOIT dépendre d'aucune intégration MINOS ou NEXUS

Aucune classe de production de `com.morpheus.mcp..` NE DOIT (MUST NOT) dépendre d'une classe de
`com.morpheus.integration..`, et le build DOIT (MUST) échouer en nommant les deux classes sinon, même quand le POM de
`morpheus-mcp` déclare la dépendance. Un outil MCP qui sollicite un pair passe par un port de l'application.

#### Scenario: Une dépendance du serveur MCP vers une intégration fait échouer le build

- **GIVEN** une classe de `morpheus-mcp` qui référence une classe de `com.morpheus.integration.minos`, la dépendance
  étant déclarée dans le POM du module
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue, nomme les deux classes et donne la raison que les adaptateurs sont frères
- **AND** aucun fichier n'est réécrit

#### Scenario: La règle juge au moins une classe

- **GIVEN** les classes de production importées par la suite
- **WHEN** la règle s'exécute
- **THEN** son sujet et chacun des paquets qu'elle nomme retiennent au moins une classe

### Requirement: Le serveur MCP NE DOIT dépendre d'aucune classe du SDK provider

Aucune classe de production de `com.morpheus.mcp..` NE DOIT (MUST NOT) dépendre d'une classe de `com.morpheus.sdk..`,
et le build DOIT (MUST) échouer en nommant les deux classes sinon : le transport model-facing n'atteint ni la
découverte ni le probe de plugins.

#### Scenario: Le serveur MCP sans dépendance au SDK

- **GIVEN** l'arbre après le retrait de l'outil MCP de plugins
- **WHEN** la suite d'architecture s'exécute
- **THEN** la règle passe et `morpheus-mcp` ne déclare plus `morpheus-provider-sdk`

#### Scenario: Une classe MCP qui importe la découverte de plugins fait échouer le build

- **GIVEN** une classe de `com.morpheus.mcp` qui référence `ProviderPluginDiscovery`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme les deux classes
- **AND** aucun fichier n'est réécrit

### Requirement: L'adaptateur HTTP N'atteint le SDK provider que par des classes nommées

Une classe de production de `com.morpheus.api..` qui dépend d'une classe de `com.morpheus.sdk..` DOIT (MUST) être
nommée par une liste exacte du gate, et une classe nommée qui n'en dépend plus DOIT faire échouer le build jusqu'à son
retrait de la liste. La liste DOIT être déclarée d'une sorte reconnue (compteur de dette ou table de faits).

#### Scenario: Une nouvelle classe HTTP qui atteint le SDK

- **GIVEN** une classe de `morpheus-api` absente de la liste qui appelle `ProviderPluginService`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme la classe et la classe du SDK atteinte

#### Scenario: Une classe nommée qui n'atteint plus le SDK

- **GIVEN** une classe de la liste dont plus aucune dépendance ne vise `com.morpheus.sdk..`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme la classe à retirer de la liste
- **AND** la liste n'est pas réécrite par le test
