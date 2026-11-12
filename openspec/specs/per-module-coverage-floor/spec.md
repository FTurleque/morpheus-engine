# per-module-coverage-floor Specification

## Purpose
Garantir qu'un module du réacteur qui cesse de se tester fait échouer le build sous son nom, au lieu de se diluer
dans la couverture totale des autres modules de l'échelle par module. Page durable :
[seuil de couverture propre à chaque module](/docs/developer/specifications/per-module-coverage-floor.md).

## Requirements

### Requirement: Chaque module de la population est tenu par un seuil qui lui est propre

Le gate de l'échelle par module DOIT (MUST) comparer le ratio de lignes et le ratio de branches du rapport propre de
chaque module de la population dérivée du POM racine au seuil déclaré pour ce module, et échouer en nommant le module,
la grandeur, le ratio mesuré et le seuil dès qu'un ratio passe dessous, même quand le ratio total reste au-dessus de
son cliquet.

#### Scenario: Un module cesse de se tester pendant que la somme tient

- **GIVEN** un réacteur construit en entier dont le ratio total de lignes reste au-dessus de `perModuleLineCoverageMinimum`
- **WHEN** le rapport propre d'un module donne un ratio de lignes inférieur au seuil de lignes déclaré pour ce module
- **THEN** le gate échoue et nomme ce module, la grandeur « line », son ratio mesuré et son seuil
- **AND** aucun fichier de configuration ni de preuve conservée n'est modifié par l'échec

#### Scenario: Les branches sont jugées à part des lignes

- **GIVEN** un module dont le ratio de lignes est au-dessus de son seuil de lignes
- **WHEN** son ratio de branches passe sous son seuil de branches
- **THEN** le gate échoue et nomme le module et la grandeur « branch »

#### Scenario: Tous les modules tiennent leur seuil

- **WHEN** chaque module de la population est au-dessus de ses deux seuils et le total au-dessus de ses deux cliquets
- **THEN** le gate passe et écrit sa preuve, sans autre effet

### Requirement: Les seuils par module sont déclarés une fois, par module, dans la configuration de l'échelle par module

Les seuils par module DOIVENT (MUST) être lus dans un fichier de configuration propre à l'échelle par module, qui
porte pour chaque module une clé de lignes et une clé de branches nommées par le nom du module. Le gate DOIT échouer en
nommant le module d'une population sans ses deux clés, et la clé qui nomme un module absent de la population.

#### Scenario: Un module ajouté au réacteur sans seuil

- **GIVEN** un module déclaré dans les `<modules>` du POM racine, qui porte une classe sous `src/main/java`
- **WHEN** le fichier des seuils par module ne porte pas ses deux clés
- **THEN** le gate échoue, nomme le module et les deux clés à déclarer
- **AND** aucun seuil par défaut n'est appliqué à sa place

#### Scenario: Un module retiré du réacteur dont la clé reste

- **GIVEN** une clé du fichier des seuils par module qui nomme un module retiré des `<modules>` du POM racine
- **WHEN** le gate de l'échelle par module s'exécute
- **THEN** il échoue et nomme la clé orpheline, qui doit partir dans le même changement que le module

#### Scenario: Un module sans classe principale n'a pas de seuil

- **GIVEN** un module du réacteur qui ne porte aucune classe sous `src/main/java`
- **WHEN** le gate s'exécute
- **THEN** ce module n'exige aucune clé et une clé qui le nommerait est refusée comme orpheline

### Requirement: Un module absent de la preuve conservée ne déclare que le plancher D2

Le seuil d'un module dont la preuve conservée de l'échelle par module ne porte aucun compteur DOIT (MUST) égaler le
plancher D2 de sa grandeur, et le gate DOIT refuser toute autre valeur en disant qu'elle exige une qualification sur
les deux plateformes.

#### Scenario: Un nouveau module déclare un seuil sans qualification

- **GIVEN** un module ajouté au réacteur depuis la dernière qualification de l'échelle par module
- **WHEN** son seuil de lignes est déclaré au-dessus du plancher D2 de lignes
- **THEN** le build échoue, nomme le module et la clé, et dit qu'un seuil au-dessus du plancher exige une
  qualification Windows et Linux qui porte ce module
- **AND** la preuve conservée n'est pas modifiée

#### Scenario: Un nouveau module au plancher

- **WHEN** ce même module déclare exactement le plancher D2 en lignes et en branches
- **THEN** le build passe pour ce module

### Requirement: Le seuil d'un module reste dans sa fenêtre qualifiée

Le seuil de chaque grandeur d'un module DOIT (MUST) laisser sous la plus basse mesure conservée du module au moins
trois et au plus neuf fois sa dispersion retenue, sans descendre sous le plancher D2, qui reste admis quand aucun
seuil plus haut ne tient dans cette bande. Le build DOIT échouer en nommant le module, la clé, la valeur et la mesure
dès qu'un seuil sort de cette fenêtre.

#### Scenario: Un seuil posé sur la mesure

- **GIVEN** une preuve conservée qui porte la mesure de lignes d'un module sur les deux plateformes
- **WHEN** son seuil de lignes laisse moins de trois fois sa dispersion retenue sous la plus basse de ces mesures
- **THEN** le build échoue et nomme le module, la clé, la valeur, la mesure et la marge exigée en lignes

#### Scenario: Un seuil abandonné loin sous la mesure

- **GIVEN** une preuve conservée qui porte la mesure de lignes d'un module sur les deux plateformes
- **WHEN** son seuil de lignes laisse plus de neuf fois sa dispersion retenue sous la plus basse de ces mesures
- **THEN** le build échoue et nomme le module, la clé, la valeur, la mesure, l'écart maximal admis et le plus petit
  seuil qui le respecte
- **AND** aucun fichier de configuration ni de preuve conservée n'est modifié par l'échec

#### Scenario: Un seuil au plancher D2 dont la fenêtre est vide

- **GIVEN** un module que la preuve conservée porte, dont la plus basse mesure moins trois fois sa dispersion retenue
  reste sous le plancher D2
- **WHEN** son seuil égale le plancher D2
- **THEN** le build passe pour ce module

#### Scenario: Un seuil au plancher D2 alors que la bande est au-dessus

- **GIVEN** un module que la preuve conservée porte, dont la plus basse mesure moins neuf fois sa dispersion retenue
  est au-dessus du plancher D2
- **WHEN** son seuil égale le plancher D2
- **THEN** le build échoue et nomme le plus petit seuil admis

#### Scenario: Un seuil sous le plancher D2

- **WHEN** le seuil de branches d'un module est inférieur au plancher D2 de branches
- **THEN** le build échoue et nomme le module et la clé

#### Scenario: Un seuil dans la fenêtre

- **WHEN** chaque seuil est entre la plus basse mesure conservée moins neuf fois sa dispersion retenue (ou le plancher
  D2 s'il est plus haut) et cette mesure moins trois fois sa dispersion retenue
- **THEN** le build passe sans qu'aucun seuil ne change de valeur

### Requirement: Le seuil d'un module ne se baisse pas pour faire passer un build

Un seuil par module NE DOIT PAS (MUST NOT) être baissé pour faire passer un build. Le refus d'un module sous son
seuil DOIT nommer comme remède un test écrit dans ce module, jamais la baisse de la clé.

#### Scenario: Le message nomme le remède

- **WHEN** le gate refuse un module passé sous son seuil
- **THEN** le message dit d'écrire des tests dans ce module et ne propose pas de baisser son seuil

#### Scenario: Une baisse de seuil se justifie

- **GIVEN** un changement qui retire du code d'un module ou le scinde
- **WHEN** ce changement baisse un seuil de ce module
- **THEN** la description de la demande de fusion justifie la baisse, comme pour tout fichier de gouvernance

### Requirement: Les seuils par module ne gouvernent ni l'échelle agrégée ni le crédit emprunté

Le gate de l'échelle agrégée et celui du crédit emprunté NE DOIVENT PAS (MUST NOT) lire le fichier des seuils par
module, et aucun seuil par module ne DOIT être dérivé d'une clé, d'un plafond ou d'une preuve de l'échelle agrégée ou
du crédit emprunté.

#### Scenario: Le gate agrégé lit les seuils par module

- **WHEN** la source du gate de l'échelle agrégée ou du crédit emprunté nomme le fichier des seuils par module
- **THEN** la suite d'architecture échoue et nomme le gate et le fichier lu

#### Scenario: Le gate par module ne lit que son échelle

- **WHEN** la suite d'architecture relit la source du gate de l'échelle par module
- **THEN** elle constate qu'il lit le fichier des seuils par module et aucune clé, aucun rapport ni aucune preuve de
  l'échelle agrégée ou du crédit emprunté

### Requirement: Le cliquet total de l'échelle par module reste en vigueur à côté des seuils

Le gate de l'échelle par module DOIT (MUST) continuer à comparer le ratio total de la population à
`perModuleLineCoverageMinimum` et `perModuleBranchCoverageMinimum`, bornés par le plancher D2 et par les plafonds
qualifiés de l'échelle ; les seuils par module s'y ajoutent sans le remplacer.

#### Scenario: Le total passe sous son cliquet alors que chaque module tient son seuil

- **GIVEN** des seuils par module tous tenus
- **WHEN** le ratio total de lignes passe sous `perModuleLineCoverageMinimum`
- **THEN** le gate échoue sur le total, comme avant ce changement
