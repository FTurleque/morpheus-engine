## MODIFIED Requirements

### Requirement: Le seuil d'un module reste dans sa fenêtre qualifiée

Le seuil de chaque grandeur d'un module DOIT (MUST) être au moins le plancher D2 et au plus la plus basse mesure
conservée de ce module moins trois fois sa dispersion retenue, comptées en lignes ou en branches. Quand la preuve
conservée porte le module, son seuil NE DOIT PAS (MUST NOT) laisser sous cette plus basse mesure plus de neuf fois sa
dispersion retenue, sauf au plancher D2 quand aucun seuil plus haut ne tient dans cette bande. Le build DOIT échouer
en nommant le module, la clé, la valeur et la mesure dès qu'un seuil sort de cette fenêtre.

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
