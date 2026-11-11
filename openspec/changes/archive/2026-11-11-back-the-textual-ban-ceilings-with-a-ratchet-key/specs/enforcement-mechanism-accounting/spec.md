## ADDED Requirements

### Requirement: Chaque plafond d'une population d'interdits textuels égale une clé d'un fichier de cliquets

Le plafond des interdits textuels de type sans intention et celui des interdits qui attendent une règle sur le
bytecode DOIVENT (MUST) chacun égaler une clé, nommée `TestClass.CHAMP`, de `config/enforcement-ceiling-ratchets.properties`.
Le build DOIT échouer en nommant la constante, la clé et les deux valeurs s'ils diffèrent, si la clé manque, ou si le
fichier porte une clé qu'aucun des deux plafonds ne lit.

#### Scenario: Un plafond relevé dans le seul test

- **GIVEN** le plafond des interdits qui attendent une règle et sa clé, égaux
- **WHEN** un changement relève la constante dans le test sans modifier le fichier de cliquets
- **THEN** la suite d'architecture échoue et nomme la constante, la clé, la valeur de la constante et celle de la clé
- **AND** le fichier de cliquets n'est pas réécrit par le test

#### Scenario: Un relèvement qui touche le fichier de gouvernance

- **GIVEN** un changement qui relève à la fois la constante et sa clé
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle passe
- **AND** la modification de `config/enforcement-ceiling-ratchets.properties` est soumise à la section de justification
  de gouvernance du gabarit de pull request, que la revue juge

#### Scenario: Un membre retiré, la clé laissée telle quelle

- **GIVEN** un interdit sans intention retiré, et la constante abaissée au compte mesuré
- **WHEN** la clé n'est pas abaissée dans le même changement
- **THEN** la suite d'architecture échoue et demande d'abaisser la clé à la valeur de la constante

#### Scenario: Une clé manquante ou orpheline

- **WHEN** le fichier de cliquets ne porte pas la clé d'un des deux plafonds, ou porte une clé qu'aucun d'eux ne lit
- **THEN** la suite d'architecture échoue et nomme la clé manquante ou en trop
