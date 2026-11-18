## ADDED Requirements

### Requirement: Le build DOIT refuser une version du SDK MCP qu'aucune entrée ne lie à la ligne Reactor tenue

Le build DOIT (MUST) lire `mcp-sdk.version` dans le POM racine et échouer quand le test de ligne ne déclare pour cette
version aucune ligne Reactor qualifiée, ou quand la ligne qu'il déclare diffère de celle de `reactor-bom.version`. Le
refus DOIT nommer la version du SDK, la ligne attendue et la ligne tenue.

#### Scenario: La version courante du SDK est liée à la ligne tenue

- **GIVEN** le POM racine et la table du test de ligne
- **WHEN** la suite d'architecture s'exécute
- **THEN** la table déclare `mcp-sdk.version` avec la ligne de `reactor-bom.version` et le test passe

#### Scenario: Une montée du SDK proposée seule fait échouer le build

- **GIVEN** une pull request qui ne change que `mcp-sdk.version`, vers une version absente de la table
- **WHEN** la suite d'architecture s'exécute
- **THEN** le test échoue et nomme la version du SDK et la table à compléter
- **AND** aucun fichier n'est réécrit par le test

#### Scenario: Une version déclarée sur une autre ligne fait échouer le build

- **GIVEN** une table qui lie `mcp-sdk.version` à une ligne Reactor différente de celle de `reactor-bom.version`
- **WHEN** la suite d'architecture s'exécute
- **THEN** le test échoue et nomme la version du SDK, la ligne déclarée et la ligne tenue

#### Scenario: Le contrôle a trouvé la propriété à lire

- **WHEN** le POM racine ne déclare plus `mcp-sdk.version`
- **THEN** le test échoue au lieu de passer
