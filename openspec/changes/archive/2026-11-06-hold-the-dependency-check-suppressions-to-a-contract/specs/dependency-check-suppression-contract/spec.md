# Spec delta : dependency-check-suppression-contract

## Purpose

Garantir qu'une règle du fichier de suppressions du gate CVE ne retire que ce qu'elle dit retirer, sur une cible ancrée,
avec sa raison écrite, pour que l'élargissement d'une suppression fasse échouer le build au lieu de reposer sur la seule
relecture. Page durable : [dependency-check-suppression-contract](/docs/developer/specifications/dependency-check-suppression-contract.md).

## ADDED Requirements

### Requirement: Chaque règle de suppression prend une des deux formes admises

Chaque `<suppress>` de `config/dependency-check-suppressions.xml` DOIT (MUST) être soit une règle d'association,
faite d'un seul `<packageUrl regex="true">` et d'un seul `<cpe>`, soit une règle de vulnérabilité nommée, faite d'un
seul `<filePath regex="true">` et d'un seul `<vulnerabilityName>`, sans aucun autre élément de correspondance. Toute
autre forme DOIT faire échouer la suite d'architecture en nommant la règle et les éléments trouvés.

#### Scenario: Le fichier actuel est admis

- **GIVEN** le fichier de suppressions de la révision courante
- **WHEN** la suite d'architecture le lit
- **THEN** chaque règle est classée dans l'une des deux formes et le contrat passe
- **AND** aucun fichier n'est modifié

#### Scenario: Une règle sans CPE retire toutes les vulnérabilités d'un paquet

- **GIVEN** une règle qui porte un `<packageUrl>` et aucun `<cpe>` ni `<vulnerabilityName>`
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la position de la règle et les éléments `packageUrl` trouvés

#### Scenario: Une forme non admise

- **GIVEN** une règle qui porte un `<cvssBelow>`, un `<sha1>`, un `<gav>` ou deux `<cpe>`
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la règle et la forme refusée

#### Scenario: Un fichier vide ne passe pas à vide

- **GIVEN** un fichier de suppressions sans aucune règle lue
- **WHEN** la suite d'architecture le lit
- **THEN** elle échoue en disant qu'aucune règle n'a été lue

### Requirement: Une règle d'association vise exactement un module MORPHEUS à la version du réacteur

Le `<packageUrl>` d'une règle d'association DOIT (MUST) être une expression ancrée par `^` et `$`, dont le seul
métacaractère admis est le point échappé, et qui ne reconnaît que `pkg:maven/io.github.fturleque/<module>@<version>`,
où `<module>` est un `<module>` du POM racine et `<version>` la `<version>` du POM racine. Un écart DOIT faire échouer la
suite d'architecture en nommant la règle.

#### Scenario: Une expression élargie

- **GIVEN** une règle d'association dont le `<packageUrl>` vaut `^pkg:maven/io\.github\.fturleque/morpheus-.*$`
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la règle et le métacaractère refusé

#### Scenario: Une expression non ancrée

- **GIVEN** une règle d'association dont le `<packageUrl>` ne commence pas par `^` ou ne finit pas par `$`
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la règle

#### Scenario: Une dépendance tierce visée

- **GIVEN** une règle d'association qui vise un paquet hors du groupe `io.github.fturleque`, ou un nom absent des
  modules du POM racine
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la règle et la coordonnée reconnue

#### Scenario: La version du réacteur change sans la règle

- **GIVEN** la `<version>` du POM racine passe à la version suivante et une règle d'association garde la précédente
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la règle, la version qu'elle porte et la version du réacteur

### Requirement: Une règle de vulnérabilité nommée vise un seul CVE sur les bibliothèques extraites du pilote

Le `<vulnerabilityName>` d'une règle de vulnérabilité nommée DOIT (MUST) être un identifiant CVE, et son `<filePath>`
DOIT reconnaître les bibliothèques natives extraites du JAR `sqlite-jdbc` à la version `<sqlite-jdbc.version>` du POM
racine, sous Windows et sous Linux, et NE DOIT PAS (MUST NOT) reconnaître le JAR du pilote ni le JAR d'un module
MORPHEUS. Un écart DOIT faire échouer la suite d'architecture en nommant la règle.

#### Scenario: Un chemin qui reconnaît tout

- **GIVEN** une règle de vulnérabilité nommée dont le `<filePath>` vaut `.*`
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la règle et le JAR qu'elle reconnaît à tort

#### Scenario: Un nom de vulnérabilité qui n'est pas un CVE

- **GIVEN** une règle de vulnérabilité nommée dont le `<vulnerabilityName>` est vide ou n'a pas la forme d'un
  identifiant CVE
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la règle et la valeur trouvée

### Requirement: Chaque règle de suppression porte sa justification

Chaque `<suppress>` DOIT (MUST) porter un `<notes>` dont le texte n'est pas vide une fois les blancs retirés, et les
notes d'une règle `base="true"` DOIVENT nommer `failBuildOnUnusedSuppressionRule`, pour dire pourquoi la règle en est
exemptée. Un écart DOIT faire échouer la suite d'architecture en nommant la règle.

#### Scenario: Une règle sans note

- **GIVEN** une règle sans `<notes>`, ou dont les notes ne contiennent que des blancs
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la règle

#### Scenario: Une exemption sans raison

- **GIVEN** une règle `base="true"` dont les notes ne nomment pas `failBuildOnUnusedSuppressionRule`
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la règle

### Requirement: Les règles exemptées du contrôle des règles inutilisées sont exactement celles que le contrat nomme

L'ensemble des règles `base="true"`, chacune identifiée par sa cible (le module et le CPE d'une règle d'association,
l'identifiant CVE d'une règle de vulnérabilité nommée), DOIT (MUST) égaler la table que tient le contrat. Une règle exemptée hors table, ou une entrée
de la table sans règle exemptée, DOIT faire échouer la suite d'architecture en la nommant.

#### Scenario: Une exemption ajoutée en passant

- **GIVEN** la règle d'association de `morpheus-cli` reçoit `base="true"` sans que la table change
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant la règle exemptée hors table
- **AND** rien n'est écrit : l'exemption n'entre que par une modification du contrat, visible dans le diff du test

#### Scenario: Une exemption retirée sans la table

- **GIVEN** la table nomme une règle qui ne porte plus `base="true"`, ou qui n'existe plus
- **WHEN** la suite d'architecture lit le fichier
- **THEN** elle échoue en nommant l'entrée de la table restée sans règle
