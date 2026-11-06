# Spec delta : enforcement-mechanism-accounting

## ADDED Requirements

### Requirement: Les interdits textuels de type qui attendent une règle sur le bytecode forment une population qui ne fait que rétrécir

La suite d'architecture DOIT (MUST) compter, par une expression qu'elle tient et prouve sur exemples, ses interdits
textuels d'un nom de type, avec ou sans message, dont la cible est déclarée dans les sources principales d'un module
qu'elle importe, ne déclare aucune constante de compilation non privée, et n'est nommée par aucune de ses classes qui
évaluent des règles sur le bytecode. Ce compte DOIT (MUST) égaler un plafond déclaré, jamais relevé.

#### Scenario: Un interdit textuel est ajouté sur une cible qui permet une règle

- **GIVEN** un type des sources principales d'un module importé, sans constante de compilation non privée, qu'aucune
  classe de règles ne nomme
- **WHEN** un test de la suite ajoute `assertFalse(source.contains("CeType"), "…")`
- **THEN** la suite échoue, donne le compte mesuré, le plafond et les membres par fichier
- **AND** le message dit d'écrire la règle sur le bytecode, l'assertion textuelle pouvant rester à côté

#### Scenario: La cible déclare une constante inlinable

- **GIVEN** un type qui déclare une constante `static final` non privée de type `String` ou primitif
- **WHEN** un test de la suite l'interdit par une assertion textuelle
- **THEN** l'interdit n'est pas compté, car une règle sur le bytecode ne verrait pas l'usage de la constante
- **AND** la suite passe sans que le plafond change

#### Scenario: Une règle est écrite et le plafond est laissé tel quel

- **WHEN** une classe de règles de la suite nomme désormais la cible d'un membre, et le plafond déclaré n'est pas abaissé
- **THEN** la suite échoue et demande d'abaisser le plafond au compte mesuré dans le même changement

#### Scenario: L'expression est prouvée dans les deux sens

- **WHEN** la mesure s'exécute sur des exemples : un membre ; une cible à constante non privée ; une cible à constante
  privée seulement ; une cible hors du dépôt ; une cible nommée par une classe de règles ; un interdit écrit dans un
  commentaire ou une chaîne ; un récepteur qui n'est pas un identifiant (`Files.readString(p).contains(…)`)
- **THEN** elle compte exactement le membre, la cible à constante privée seulement et le récepteur qualifié

### Requirement: Une règle qui remplace ou double un interdit textuel est livrée avec sa violation plantée

Le changement qui ajoute une règle sur le bytecode pour remplacer ou doubler un interdit textuel DOIT (MUST) consigner,
dans ses tâches ou sa pull request, la violation introduite pour la faire échouer, l'échec constaté qui nomme la classe
fautive, et le retour arrière. Une règle qui ne retient aucune classe DOIT (MUST) continuer d'échouer
(`archRule.failOnEmptyShould`).

#### Scenario: Une règle migrée sans trace de violation

- **GIVEN** une pull request qui ajoute une règle sur le bytecode à la place d'un interdit textuel
- **WHEN** ses tâches et sa description ne consignent aucune violation plantée
- **THEN** la revue la refuse ; le build, qui ne lit pas cette trace, ne la refuse pas
- **AND** aucune règle n'est acceptée sur la seule foi d'un build vert

#### Scenario: Une règle migrée qui ne voit aucune classe

- **WHEN** une règle ajoutée par la migration cible un paquet absent de l'ensemble importé
- **THEN** la suite échoue par le garde-fou des règles vides
- **AND** aucune exemption ne permet de la garder
