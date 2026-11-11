# Proposition : adosser les deux plafonds d'interdits textuels à une clé de cliquet

## Why

Les deux populations d'interdits textuels de type que la suite d'architecture mesure sont bornées par deux constantes
Java : `TypeBansAwaitingABytecodeRuleTest.AWAITING_A_RULE_CEILING` et
`EnforcementMechanismAccountingTest.UNEXPLAINED_TYPE_BAN_CEILING`. Les deux sont assertées par égalité avec le compte
mesuré, et la spec `enforcement-mechanism-accounting` exige qu'aucun plafond ne soit relevé. Rien ne tient cette
promesse : relever la constante dans le même changement que l'interdit qui la dépasse rend le build vert, et aucun
fichier de gouvernance ne bouge, donc la section « Justification d'une modification de gouvernance » du gabarit de
pull request ne s'applique pas.

Constat `AUD-TST-25` (sévérité Faible), revérifié le 10/11/2026 sur `38a4079f`
(`docs/project/audits/2026-10-09/reverification-2026-11-10.md`) : aucune des deux constantes n'apparaît dans un
`.properties`, alors que le dépôt a créé le 07/11/2026 une clé de cliquet dans un fichier propre pour les
suppressions CVE (`config/dependency-check-suppression-ratchets.properties`). Le patron existe ; il n'est pas appliqué
ici. Les deux plafonds valent zéro aujourd'hui : poser la clé ne demande aucune autre modification.

## What Changes

- Ajout d'un fichier de cliquets `config/enforcement-ceiling-ratchets.properties`, une clé par plafond, nommée
  `TestClass.CHAMP`.
- Chacun des deux tests compare sa constante à sa clé et échoue en nommant la constante, la clé et les deux valeurs.
- Une clé manquante, ou une clé du fichier qu'aucun des deux tests ne lit, fait échouer le build.
- `.claude/rules/architecture.md` (section « Un interdit textuel de type écrit son intention ») dit que relever un
  plafond touche ce fichier de gouvernance.
- Aucune surface publique, aucun format persisté ne change.

## Capabilities

### New Capabilities

### Modified Capabilities

- `enforcement-mechanism-accounting` : exigence ajoutée — chaque plafond d'une population d'interdits textuels égale
  une clé d'un fichier de cliquets de `config/`.

## Périmètre et exclusions

**Inclus :** les deux plafonds nommés ci-dessus, leur fichier de cliquets, la page de règles qui les décrit, la page
durable de la capacité.

**Exclus :** la mesure elle-même (expressions, exemples), inchangée ; les compteurs de dette de listes de classes, déjà
adossés à `config/debt-counter-ratchets.properties` ; tout mécanisme qui comparerait la clé à sa valeur sur la branche
de base, que le build n'a pas (la revue reste le dernier rempart, comme pour les autres cliquets).

## Acteurs et surfaces concernés

Mainteneur et contributeur qui ajoutent un interdit textuel à la suite d'architecture ; relecteur de la pull request ;
CI qui exécute `morpheus-architecture-tests`.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Les deux plafonds sont des constantes Java assertées par égalité | existant vérifié | `TypeBansAwaitingABytecodeRuleTest:48`, `EnforcementMechanismAccountingTest:51` |
| Aucun `.properties` ne les déclare | existant vérifié | recherche des deux noms sur le dépôt, 11/11/2026 |
| Le gabarit de PR exige la justification de toute modification de `config/*ratchets*.properties` | existant vérifié | `.github/pull_request_template.md:28` |
| Un fichier propre plutôt que `config/debt-counter-ratchets.properties` | décision | ce dernier ne déclare que les listes de `ProductionClassListsArchitectureTest.KINDS` (en-tête du fichier) |
| Les deux plafonds valent zéro | existant vérifié | sources lues le 11/11/2026 — à relire au moment d'appliquer |

## Impact

- `morpheus-architecture-tests` : `TypeBansAwaitingABytecodeRuleTest`, `EnforcementMechanismAccountingTest`.
- `config/enforcement-ceiling-ratchets.properties` (nouveau, fichier de gouvernance).
- `.claude/rules/architecture.md`, `docs/developer/specifications/enforcement-mechanism-accounting.md`.
- Aucun ADR : la règle « le plafond ne fait que baisser » existe déjà dans la spec ; le changement la rend tenue par un
  fichier de gouvernance, sans nouvelle décision durable (filtre de `.claude/rules/adr.md`, question 1).

## Questions ouvertes

Aucune.
