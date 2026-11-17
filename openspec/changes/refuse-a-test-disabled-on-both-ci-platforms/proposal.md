# Proposition : refuser un test désactivé sur les deux plateformes de CI (AUD-TST-30)

## Why

Le gate des tests sautés accepte tout saut dont la raison commence par `Disabled on operating system`, quel que soit
le système (`morpheus-coverage-report/src/test/java/com/morpheus/coverage/SkippedTestReasonGateTest.java:44,155`,
revérifié le 17/11/2026 sur `1a539c22`) ; `scripts/validate-m20.ps1:131` applique la même règle à la répétition de
release. Un test annoté `@EnabledOnOs(OS.MAC)` ou `@DisabledOnOs({OS.LINUX, OS.WINDOWS})` est donc sauté sur les deux
plateformes de CI, accepté par le gate, et désactivé pour toujours sans `@Disabled` ni ticket, ce que
`.claude/rules/testing.md` interdit. Aucun test ne refuse une telle condition : les dix-neuf conditions d'OS du dépôt
incluent toutes Linux ou Windows aujourd'hui, mais rien ne le garantit.

Les comptes de présence ne retranchent pas les exécutions sautées (`validate-m21.sh:63-67`, `validate-m21.ps1:43`) :
un test sauté compte comme une exécution déclarée sous `testsMinimum`. `validate-d2.*` imprime le nombre de sautés,
`validate-m21.*` ne le publie pas.

Constat `AUD-TST-30` (Faible), `docs/project/audits/2026-11-17/findings.md`.

## What Changes

- Gate de build : la suite d'architecture refuse, dans les sources de test de tous les modules, une condition d'OS dont
  l'effet combiné (classe et méthode) désactive le test à la fois sur Linux et sur Windows ; le refus nomme la classe,
  la méthode et les conditions.
- Les validateurs qui comptent la présence (`validate-m21.*`, `validate-d2.*`) publient le nombre d'exécutions sautées
  dans leur ligne de verdict et dans `validation-summary.txt`.
- Que ces exécutions soient retranchées des comptes comparés aux minima est une décision du mainteneur (design.md,
  [QUESTION] Q1) ; la proposition est de ne pas les retrancher.
- Page durable nouvelle `docs/developer/specifications/skipped-test-justification.md`, indexée.

## Capabilities

### New Capabilities
- `skipped-test-justification` : un test ne peut être sauté que par une condition d'OS qui le laisse actif sur au moins
  une plateforme de CI, et les sauts d'un build sont comptés et publiés.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** une règle nouvelle de la suite d'architecture ; `SkippedTestReasonGateTest` (sa Javadoc renvoie à la
règle) ; la ligne de verdict et le relevé de `validate-m21.*` et `validate-d2.*` ; la page durable.

**Exclus :** la règle du gate par rapport (seul un saut de condition d'OS est accepté), inchangée et décrite comme
existante ; les valeurs des ratchets de `config/m21-quality-ratchets.properties`, inchangées sauf si Q1 retient le
retranchement ; `@Disabled`, déjà absent et interdit sans ticket.

## Acteurs et surfaces concernés

L'auteur d'un test, le mainteneur qui lit un relevé de validation, la CI (Linux et Windows).

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Tout saut d'OS est accepté, quel que soit l'OS | existant vérifié | `SkippedTestReasonGateTest.java:155`, `validate-m20.ps1:131` |
| Les dix-neuf conditions actuelles incluent Linux ou Windows | existant vérifié | recherche `@EnabledOnOs`/`@DisabledOnOs` dans `src/test/java` |
| Les sautés comptent dans le compte déclaré | existant vérifié | `validate-m21.sh:63-67` |
| Une règle sur les sources, pas sur les rapports | décision | un run ne voit qu'une plateforme (design.md, D1) |
| Retrancher ou non les sautés des comptes | question ouverte | GEN-0089 fixe ce qu'est une exécution déclarée |

## Impact

`morpheus-architecture-tests` (une classe de test nouvelle, fichier de gouvernance : justification de PR requise),
`morpheus-coverage-report` (Javadoc), `scripts/validate-m21.*`, `scripts/validate-d2.*` et leurs tests
(`scripts/tests/test_validator_verdicts.py`), la page durable et l'index de `docs/developer/specifications/`.

Si le changement `count-presence-once-from-the-declared-modules` est appliqué avant celui-ci, la publication des
sautés se fait dans sa bibliothèque de comptage ; sinon dans les validateurs, puis elle migre avec eux.

## Questions ouvertes

- **Q1 (mainteneur)** : retrancher les exécutions sautées des comptes comparés à `testsMinimum`,
  `generatedTestsMinimum` et `architectureTestsMinimum`, ou les publier à part sans les retrancher. Le retranchement
  change la définition d'une exécution déclarée que porte GEN-0089 : il exigerait un ADR consolidé qui le remplace et
  une requalification des ratchets sur les deux plateformes (design.md, Q1).
