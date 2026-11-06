# Spec delta : dependency-version-management

## ADDED Requirements

### Requirement: Une version qu'une page de gouvernance IA écrit sous la forme d'une propriété du POM DOIT égaler la valeur de cette propriété

Quand une page de gouvernance IA écrit une version sous la forme d'une propriété du POM racine, `<nom.version>valeur</nom.version>`
ou `nom.version=valeur`, le build DOIT (MUST) comparer la valeur à celle que `pom.xml` déclare pour cette propriété et
échouer en nommant la page, la propriété et les deux valeurs ; une propriété que le POM ne déclare pas DOIT aussi faire
échouer le build.

#### Scenario: La version de JaCoCo restée en arrière

- **GIVEN** `.claude/rules/build.md` qui écrit `jacoco.version=0.8.15` et `pom.xml` qui déclare `0.8.16`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme `.claude/rules/build.md`, `jacoco.version`, `0.8.15` et `0.8.16`
- **AND** aucune page n'est réécrite par le test

#### Scenario: Un bump qui ne touche que le POM

- **GIVEN** une propriété de version que `build.md` recopie
- **WHEN** un changement modifie sa valeur dans `pom.xml` seulement
- **THEN** la suite d'architecture échoue jusqu'à ce que la page suive

#### Scenario: Les deux formes sont lues

- **WHEN** une page écrit la même propriété sous la forme d'un élément XML et sous la forme `nom=valeur`
- **THEN** chacune des deux occurrences est comparée au POM

#### Scenario: Une propriété que le POM ne déclare plus

- **WHEN** une page écrit `nom.version=valeur` pour une propriété absente de `pom.xml`
- **THEN** la suite d'architecture échoue et nomme la page et la propriété

#### Scenario: Le contrôle a trouvé des versions à comparer

- **WHEN** le contrôle ne trouve aucune version sous forme de propriété dans l'ensemble des pages parcourues
- **THEN** il échoue au lieu de passer
