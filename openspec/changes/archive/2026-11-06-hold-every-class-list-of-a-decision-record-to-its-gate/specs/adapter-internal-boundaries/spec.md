# Spec delta : adapter-internal-boundaries

## ADDED Requirements

### Requirement: Une liste de classes qu'un ADR en vigueur énumère DOIT égaler la constante du gate qui la tient

Quand le test qui classe les listes déclare qu'un ADR accepté énumère une liste de classes de production, le build
DOIT (MUST) exiger cette énumération dans l'ADR, la comparer à la constante du test nom pour nom et dans les deux sens,
et échouer en nommant l'ADR, la constante et chaque nom en trop ou manquant. Un ADR déclaré ne nommer la liste que par
sa référence `TestClass.CHAMP` n'est pas comparé.

#### Scenario: Une classe ajoutée au gate et pas à l'ADR

- **GIVEN** une liste rattachée à un ADR qui en énumère le contenu
- **WHEN** un nom est ajouté à la constante du test, avec la clé du cliquet relevée dans le même changement, et l'ADR
  n'est pas modifié
- **THEN** la suite d'architecture échoue et nomme l'ADR, la constante et le nom absent de l'ADR
- **AND** aucun fichier n'est réécrit : l'écart reste à corriger par le contributeur

#### Scenario: Un nom que l'ADR énumère et que le gate ne porte plus

- **GIVEN** l'énumération d'`ARCH-0150` qui nomme une classe retirée de la constante, comme
  `product.UpdateDiscoveryService` dans la mesure du 25/10/2026
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme la classe en trop dans l'ADR

#### Scenario: Le même nom simple dans un autre paquet

- **GIVEN** une énumération qui écrit `security.LocalWritePermissionHardener` alors que la constante porte
  `files.LocalWritePermissionHardener`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme les deux noms, car la comparaison porte sur le nom qualifié

#### Scenario: Une énumération que le build ne sait pas lire

- **GIVEN** un ADR déclaré énumérer une liste, dont le bloc d'énumération est absent, vide, ou contient une famille
  hors du vocabulaire déclaré par le test
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et dit ce qu'elle n'a pas pu lire, au lieu de conclure que les deux listes concordent

#### Scenario: Un ADR qui nomme la liste par sa seule référence

- **GIVEN** `ARCH-0156`, qui nomme `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` sans énumérer ses noms
- **WHEN** la suite d'architecture s'exécute, la liste étant déclarée référencée seulement
- **THEN** la comparaison ne lui demande aucune énumération et ne le refuse pas

### Requirement: Une table de faits qu'un ADR en vigueur énonce DOIT déclarer cet ADR

Une liste de classes de production déclarée table de faits, dont un ADR accepté énonce le contenu, DOIT (MUST)
déclarer cet ADR auprès du test qui classe les listes, et le build DOIT la comparer à son énumération comme un compteur
de dette. Une table de faits qui ne déclare aucun ADR n'est pas comparée.

#### Scenario: Les six types d'entrée de l'adaptateur HTTP

- **GIVEN** `ApiEntryTypesArchitectureTest.ENTRY_TYPES`, dont `ARCH-0155` §3 énumère les six types
- **WHEN** la suite d'architecture s'exécute après ce changement
- **THEN** la table déclare `ARCH-0155` et son contenu est comparé à l'énumération du §3

#### Scenario: Un type d'entrée ajouté sans l'ADR

- **WHEN** un type est ajouté à `ENTRY_TYPES` et `ARCH-0155` §3 n'est pas modifié
- **THEN** la suite d'architecture échoue et nomme le type absent de l'ADR

#### Scenario: Une table de faits déclare un ADR qui ne l'énumère pas

- **WHEN** une table de faits déclare un ADR absent du registre, non accepté, ou qui ne porte aucune énumération de
  la table
- **THEN** la suite d'architecture échoue et nomme la table et l'ADR
