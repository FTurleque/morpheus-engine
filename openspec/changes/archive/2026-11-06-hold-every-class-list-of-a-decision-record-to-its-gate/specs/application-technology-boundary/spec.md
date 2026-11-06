# Spec delta : application-technology-boundary

## ADDED Requirements

### Requirement: Une classe que le registre de la frontière technologique nomme par un nom qualifié DOIT exister à ce nom

Toute classe de `com.morpheus.application` que l'ADR de la frontière technologique de l'application nomme par un nom
qualifié, dans n'importe quelle section, DOIT (MUST) exister à ce nom dans les sources de `morpheus-application`, et le
build DOIT échouer en nommant le nom introuvable sinon.

#### Scenario: Une classe déplacée dans un autre paquet

- **GIVEN** l'ADR qui écrit `security.LocalWritePermissionHardener`
- **WHEN** la classe vit dans `com.morpheus.application.files`
- **THEN** la suite d'architecture échoue et nomme `security.LocalWritePermissionHardener`
- **AND** l'ADR reste inchangé jusqu'à sa correction

#### Scenario: Une classe retirée des sources

- **WHEN** l'ADR nomme par un nom qualifié une classe qui n'existe plus dans `morpheus-application`
- **THEN** la suite d'architecture échoue et nomme la classe

#### Scenario: Le contrôle a lu des noms

- **WHEN** le contrôle ne trouve aucun nom qualifié dans l'ADR
- **THEN** il échoue au lieu de passer

### Requirement: Le registre de la frontière technologique NE DOIT PAS porter de mesure périssable de ses classes nommées

L'ADR de la frontière technologique de l'application NE DOIT PAS (MUST NOT) énoncer comme un fait présent la longueur
d'une classe nommée, un total de lignes, un numéro de ligne, ni attribuer à une classe une famille que sa décision
refuse ; une mesure datée qui a servi à décider vit dans un relevé daté sous `docs/project/`, auquel l'ADR renvoie.

#### Scenario: La mesure du 25/10/2026 quitte le registre

- **WHEN** l'ADR est relu après ce changement
- **THEN** il ne porte plus la table « Mesure » ni ses longueurs, son total et ses numéros de ligne, et il renvoie au
  relevé daté qui la conserve telle qu'elle a été écrite

#### Scenario: Une famille refusée n'est attribuée à aucune classe

- **WHEN** l'ADR est relu après ce changement
- **THEN** aucune de ses sections n'attribue le réseau sortant ni les fils d'exécution à une classe de l'application

#### Scenario: Chaque ligne de la décision nomme ses classes

- **WHEN** la décision 2 est relue
- **THEN** chaque famille confinée renvoie à des noms de classes écrits, et aucune ne renvoie à « la mesure » pour les
  connaître
