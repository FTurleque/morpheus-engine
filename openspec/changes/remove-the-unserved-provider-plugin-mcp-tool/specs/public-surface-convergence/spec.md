## ADDED Requirements

### Requirement: Le module MCP NE DOIT déclarer aucun outil de plugin provider

Aucune classe de production du module `morpheus-mcp` NE DOIT (MUST NOT) déclarer un outil MCP qui sert une capacité
`provider.plugins.*` de `contracts/public-surfaces.tsv`, qu'un câblage le serve ou non : la colonne `mcp`
`EXPLICITLY_NOT_EXPOSED` de ces lignes DOIT (MUST) être vraie de tout serveur que le module peut construire. Le build
DOIT échouer en nommant la source et l'outil sinon.

#### Scenario: Le serveur MCP ne sert aucun outil de plugin

- **GIVEN** le serveur MCP construit par son câblage par défaut
- **WHEN** la liste de ses outils servis est lue
- **THEN** elle ne contient ni `discover_provider_plugins` ni `probe_provider_plugin`
- **AND** les lignes `provider.plugins.discover` et `provider.plugins.probe` du manifeste portent toujours
  `EXPLICITLY_NOT_EXPOSED` en colonne `mcp`

#### Scenario: Un outil de plugin déclaré mais non servi fait échouer le build

- **GIVEN** une classe de production de `morpheus-mcp` qui déclare l'outil `discover_provider_plugins` derrière un
  constructeur que le câblage par défaut n'appelle pas
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme la source et le nom d'outil
- **AND** aucun fichier n'est réécrit : la déclaration reste à retirer par le contributeur

#### Scenario: Le contrôle voit les sources du module

- **GIVEN** les sources principales de `morpheus-mcp`
- **WHEN** le contrôle s'exécute
- **THEN** il a lu au moins une source, au lieu de passer sur un ensemble vide
