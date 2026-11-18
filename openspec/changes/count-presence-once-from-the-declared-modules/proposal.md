# Proposition : compter la présence une fois par plateforme, depuis les modules déclarés (AUD-QUA-32, AUD-TST-29)

## Why

Le partage des exécutions Surefire entre « déclarées » et « générées » (GEN-0089), frontière entre `testsMinimum` et
`generatedTestsMinimum`, est écrit dans chaque validateur au lieu de l'être une fois. Revérifié le 17/11/2026 sur
`1a539c22` : neuf fonctions `Get-SurefireTotals` (`scripts/validate-m19.ps1`, `validate-m20.ps1`, `validate-m21.ps1`,
`validate-m22.ps1` à `validate-m27.ps1`), une boucle en ligne dans `validate-d2.ps1`, et un programme Python en heredoc
dans `validate-m21.sh`, `validate-d2.sh`, `validate-m22.sh` à `validate-m27.sh` ; l'expression `\[[0-9]+\]` y apparaît
seize fois. `scripts/lib/` porte déjà une bibliothèque par langage pour les ratchets (`Read-QualityRatchets.ps1`,
`read-quality-ratchet.sh`). Aucun test ne compare les copies.

Ces mêmes copies parcourent tout l'arbre depuis la racine : `root.rglob('target/surefire-reports/TEST-*.xml')` dans
neuf `.sh` (`validate-m19.sh`, `validate-m21.sh` à `validate-m27.sh`, `validate-d2.sh`), `Get-ChildItem -Path $Root
-Recurse` dans les `.ps1` correspondants et dans `validate-m20.ps1`, et le compte des POM du réacteur de `validate-d2.*`
(`root.rglob('pom.xml')` filtré sur `target` seul). Aucune ne met de côté `.claude/worktrees/`, que `.gitignore`
ignore (`rtk proxy grep -rn worktrees scripts/` : aucun résultat). Dès qu'une copie de travail d'agent existe,
`validate-d2` refuse un build sain (POM « unexpected ») et les comptes de présence additionnent les exécutions de la
copie, ce qui masque une baisse réelle. Le côté Java a corrigé ce défaut (`RepositoryTree`, exigence
`repository-root-location` › A walk of the whole repository SHALL go through one owner that skips copies of the
repository) ; les validateurs non.

Les deux constats de `docs/project/audits/2026-11-17/findings.md` sont liés par l'audit (sprint 1 : « les mêmes
fichiers, donc le même lot, le défaut en premier ») : le parcours fautif est la première instruction de chaque fonction
de comptage. Corriger le parcours dans dix-huit copies puis les remplacer par une bibliothèque réécrirait deux fois les
mêmes lignes ; un seul changement les traite.

## What Changes

- Une bibliothèque par langage dans `scripts/lib/` (PowerShell et shell appelant Python par `scripts/lib/python.sh`)
  compte les exécutions déclarées, générées, en échec, en erreur et sautées des rapports Surefire, pour le réacteur ou
  pour un module. Elle est la seule à porter la règle qui reconnaît une exécution générée.
- Elle lit les rapports `<racine>/<module>/target/surefire-reports/TEST-*.xml` des seuls modules que déclare le POM
  racine, jamais un parcours récursif de la racine.
- `validate-m19`, `validate-m20`, `validate-m21` à `validate-m27` et `validate-d2`, dans leurs deux langages, appellent
  cette bibliothèque ; leurs fonctions et heredocs de comptage disparaissent.
- Le compte des POM de `validate-d2.*`, `validate-r2.*` et `validate-r3.*` ne lit que les POM des modules déclarés et
  le POM racine, ou ignore `.claude/worktrees/` et tout répertoire qui porte son propre `.git`.
- Gate de build : un test de la suite d'architecture refuse l'expression de la règle des exécutions générées hors des
  deux bibliothèques, et un parcours récursif de la racine du dépôt dans `scripts/`.
- Les cas de la bibliothèque sont écrits une fois et rejoués contre ses deux implémentations (`scripts/tests/`).

## Capabilities

### New Capabilities

### Modified Capabilities
- `gate-verdict-currency` : le partage déclarées/générées est implémenté une fois par langage, et une copie ailleurs
  fait échouer le build.
- `repository-root-location` : un validateur ne lit que les POM et les rapports des modules déclarés ; une copie de
  travail sous la racine ne change aucun de ses comptes.

## Périmètre et exclusions

**Inclus :** les validateurs `scripts/validate-m19.*` à `validate-m27.*`, `validate-d2.*`, `validate-r2.*`,
`validate-r3.*` ; deux bibliothèques nouvelles sous `scripts/lib/` ; leurs tests sous `scripts/tests/` ; une méthode
de test d'architecture ; les pages durables des deux capacités.

**Exclus :** la règle elle-même (un nom `méthode()[N]` est une exécution générée), inchangée ; les clés de
`config/m21-quality-ratchets.properties`, inchangées ; `Invoke-Native`, aussi copié six fois, qui ne porte aucune
règle de comptage et ne lit aucun arbre (piste de suite, hors de ce constat) ; le traitement des tests sautés, objet du
changement `refuse-a-test-disabled-on-both-ci-platforms`.

## Acteurs et surfaces concernés

Le mainteneur qui lance un validateur sur un poste où vivent des copies de travail d'agents ; la CI (checkout propre),
dont le verdict ne change pas.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Dix-huit implémentations du comptage, seize occurrences de `\[[0-9]+\]` | existant vérifié | `function Get-SurefireTotals` (9 `.ps1`), `validate-d2.ps1:121-141`, heredocs des 8 `.sh` |
| Aucun validateur n'écarte `.claude/worktrees/` | existant vérifié | `validate-d2.sh:66-69`, `validate-d2.ps1:57-58`, `validate-m21.sh:61`, `validate-m21.ps1:37` |
| `validate-r2.*` et `validate-r3.*` comptent aussi les POM par parcours récursif | existant vérifié (absent de l'audit) | `validate-r2.sh:31`, `validate-r3.sh:43`, `validate-r2.ps1:26`, `validate-r3.ps1:26` |
| Lire les modules déclarés plutôt qu'exclure un répertoire | proposition à valider | design.md, D2 |
| Aucun ADR | décision | `.claude/rules/adr.md` : outillage interne, retour arrière sans coût ; GEN-0089 inchangé |

## Impact

`scripts/` (vingt-quatre validateurs, deux bibliothèques nouvelles, tests Python), une méthode nouvelle dans
`RepositoryTreeOwnershipTest` ou `GateReachabilityArchitectureTest` (fichier de gouvernance : justification de PR
requise). Aucun module Java de production, aucune surface publique, aucun ratchet.

Aucune dépendance envers un autre constat du sprint. Le changement `refuse-a-test-disabled-on-both-ci-platforms`
publiera le compte des exécutions sautées : s'il est appliqué après celui-ci, il le fait dans la bibliothèque créée
ici, non dans dix-huit copies.

## Questions ouvertes

Aucune.
