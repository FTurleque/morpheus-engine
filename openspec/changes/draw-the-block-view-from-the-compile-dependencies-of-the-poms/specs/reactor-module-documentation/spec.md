## ADDED Requirements

### Requirement: Une arête dessinée entre deux modules de la vue en blocs DOIT être une dépendance de compilation

Dans les diagrammes Mermaid de `docs/architecture/arc42/05-vue-blocs.md`, chaque arête entre deux nœuds qui désignent
des modules du réacteur DOIT (MUST) correspondre à une dépendance de portée compilation ou exécution déclarée par le
POM du module source vers le module cible, et le build DOIT échouer en nommant le diagramme et l'arête sinon.

#### Scenario: Une arête provider vers SDK que le POM ne déclare pas

- **GIVEN** la vue en blocs qui dessine une arête de `morpheus-provider-openspec` vers `morpheus-provider-sdk`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le diagramme et l'arête
- **AND** la page n'est pas réécrite par le test

#### Scenario: Une dépendance de test n'autorise pas une arête

- **GIVEN** le POM de `morpheus-api` qui déclare `morpheus-provider-synthetic` en portée de test seulement
- **WHEN** la vue en blocs dessine une arête de `morpheus-api` vers `morpheus-provider-synthetic`
- **THEN** la suite d'architecture échoue et nomme l'arête

### Requirement: Un module de la vue en blocs DOIT atteindre par ses arêtes tout ce que ses POM lui font atteindre

Pour chaque module du réacteur qu'un diagramme de dépendances de `05-vue-blocs.md` dessine, l'ensemble des modules
dessinés qu'il atteint par les arêtes du diagramme DOIT (MUST) égaler l'ensemble des modules dessinés qu'il atteint par
les dépendances de compilation des POM ; le build DOIT échouer en nommant le module et chaque module non atteint.

#### Scenario: Le câblage de la CLI absent du diagramme

- **GIVEN** un diagramme qui dessine `morpheus-cli` et `morpheus-api` sans chemin de l'un vers l'autre, alors que le POM
  de la CLI déclare l'adaptateur HTTP
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme `morpheus-cli` et `morpheus-api`

#### Scenario: Une exception d'ARCH-0162 cachée

- **GIVEN** un diagramme qui ne relie pas `morpheus-mcp` à `morpheus-store-sqlite`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme `morpheus-mcp` et `morpheus-store-sqlite`

#### Scenario: Le contrôle a quelque chose à comparer

- **WHEN** le contrôle ne trouve aucun diagramme, ou aucun nœud qui désigne un module du réacteur
- **THEN** il échoue au lieu de passer
