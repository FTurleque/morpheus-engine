## MODIFIED Requirements

### Requirement: Une table de faits qu'un ADR en vigueur énonce DOIT déclarer cet ADR

Une liste de classes de production déclarée table de faits, dont un ADR accepté énonce le contenu, DOIT (MUST)
déclarer cet ADR auprès du test qui classe les listes. Quand l'ADR énumère la table, le build DOIT la comparer à son
énumération comme un compteur de dette ; quand il la nomme par sa seule référence `TestClass.CHAMP`, le build DOIT
exiger qu'il existe, soit accepté et la nomme ainsi. Une table de faits qui ne déclare aucun ADR n'est pas comparée.

#### Scenario: Les six types d'entrée de l'adaptateur HTTP

- **GIVEN** `ApiEntryTypesArchitectureTest.ENTRY_TYPES`, dont `ARCH-0155` §3 énumère les six types
- **WHEN** la suite d'architecture s'exécute après ce changement
- **THEN** la table déclare `ARCH-0155` et son contenu est comparé à l'énumération du §3

#### Scenario: Un type d'entrée ajouté sans l'ADR

- **WHEN** un type est ajouté à `ENTRY_TYPES` et `ARCH-0155` §3 n'est pas modifié
- **THEN** la suite d'architecture échoue et nomme le type absent de l'ADR

#### Scenario: Les listes confinées des modules de transport sous ARCH-0165

- **GIVEN** `ARCH-0165`, qui nomme `CliTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES` et
  `ApiTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES` sans énumérer leurs classes
- **WHEN** la suite d'architecture s'exécute
- **THEN** les deux tables déclarent `ARCH-0165` par référence, et aucune énumération ne leur est demandée
- **AND** aucune clé n'est ajoutée à `config/debt-counter-ratchets.properties`

#### Scenario: Une table de faits déclare un ADR qui ne l'énumère pas

- **WHEN** une table de faits déclare un ADR absent du registre, non accepté, ou qui ne l'énumère pas alors qu'elle
  se déclare énumérée, ou qui ne la nomme ni par une énumération ni par `TestClass.CHAMP`
- **THEN** la suite d'architecture échoue et nomme la table et l'ADR

#### Scenario: L'ADR d'une table est archivé

- **GIVEN** une table de faits qui déclare un ADR par référence
- **WHEN** cet ADR passe dans `docs/architecture/adr/archive/`
- **THEN** la suite d'architecture échoue et nomme la table et l'ADR absent du registre
- **AND** aucune déclaration n'est réécrite par le test

## ADDED Requirements

### Requirement: Un ADR accepté qui nomme une table de faits par sa référence DOIT être déclaré par elle

Quand un ADR accepté du registre écrit, en span de code, la référence `TestClass.CHAMP` d'une liste que le test qui
classe les listes déclare table de faits, cette table DOIT (MUST) déclarer cet ADR. Le build DOIT échouer en nommant
l'ADR et la table sinon. Une mention du seul nom de la classe de test n'est pas une référence à une liste.

#### Scenario: Une table nommée par un ADR et déclarée sans lui

- **GIVEN** `ARCH-0115`, qui nomme `StaticOperationalRuntimeUsersArchitectureTest.MAIN_USERS`, et cette table déclarée
  sans ADR
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme `ARCH-0115` et `StaticOperationalRuntimeUsersArchitectureTest.MAIN_USERS`

#### Scenario: Une table nommée par deux ADR

- **WHEN** deux ADR acceptés nomment la même table par sa référence
- **THEN** la suite d'architecture échoue et nomme les deux ADR, une table ne déclarant qu'une décision

#### Scenario: Un ADR qui cite la classe de test sans le champ

- **WHEN** un ADR accepté écrit `CliTechnologyBoundaryArchitectureTest` sans nom de champ
- **THEN** cette mention n'est pas lue comme la référence d'une liste

#### Scenario: Un ADR retiré qui nomme encore une table

- **WHEN** un ADR de `docs/architecture/adr/archive/` nomme une table de faits par sa référence
- **THEN** le contrôle ne le lit pas
