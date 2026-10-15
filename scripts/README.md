# Scripts de validation

Les implémentations de validation sont regroupées dans ce répertoire :

```text
validate-<target>.ps1   Windows / PowerShell
validate-<target>.sh    Linux / WSL
```

Un validateur existe sur les deux plateformes ou sur aucune : `GateReachabilityArchitectureTest` refuse un `.ps1` sans
`.sh`, et l'inverse.

Sous Windows, utiliser le dispatcher unique :

```powershell
.\scripts\validate.cmd <target> [arguments du validateur]
```

Lister les cibles :

```powershell
.\scripts\validate.cmd list
```

## M19 — budgets de performance

```powershell
.\scripts\validate.cmd m19
```

```bash
bash ./scripts/validate-m19.sh
```

`validate-m19` ne prend **pas** de version : `-Version` y est refusé. Il exige l'environnement de référence
(au moins 4 processeurs logiques, 8 GiB, disque local SSD ; un runner GitHub hébergé, dont le disque virtuel ne déclare
pas de type, est reconnu par `GITHUB_ACTIONS` et `RUNNER_ENVIRONMENT=github-hosted`, jamais un runner auto-hébergé), lance un `clean verify`, les cinq gates de performance
(`M19PerformanceGate`, `M19QueryPerformanceGate`, `M19TraceabilityPerformanceGate`, `M19CompositionPerformanceGate`,
`M19FullPublishPerformanceGate`, dont aucun ne correspond aux motifs par défaut de Surefire), le packaging et le démarrage
packagé. Le workflow `nightly.yml` le lance chaque nuit sur les deux plateformes. Les switches PowerShell `-SkipPackaging` et `-SkipBenchmarks`
sont réservés au diagnostic et ne constituent jamais une qualification.

## M21 — gate durable exact-head

La baseline corrective active est `1.2.1` :

```powershell
.\scripts\validate.cmd m21 -Version 1.2.1
```

```bash
bash ./scripts/validate-m21.sh 1.2.1
```

M21 applique notamment :

```text
clean verify
Surefire total       >= 3820
architecture         >= 585
aggregate line coverage    >= 90.0%
aggregate branch coverage  >= 75.7%
per-module line coverage   >= 69.1%
per-module branch coverage >= 61.2%
CycloneDX SBOM
provenance
portable smoke
product/package version = 1.2.1
HEAD exact + workspace tracked clean
```

Sur les pull requests, `MORPHEUS CI` complète M21 avec le gate différentiel :

```text
changed executable lines >= 80%
changed branches         >= 70%
```

Le même SHA doit réussir sur Windows et Linux.

## D2 — Post-R3 Repository Hardening

Windows :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.1 -BaseRef origin/develop
```

Linux / WSL :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.1
```

D2 reste un gate **local spécialisé** :

```text
.github/workflows delta forbidden
clean verify required
Surefire total         >= testsMinimum of config/m21-quality-ratchets.properties
architecture total     >= architectureTestsMinimum of the same file
absolute line floor    >= 40.0%
absolute branch floor  >= 35.0%
dependency hygiene blocking
OWASP Dependency-Check local aggregate
portable smoke required
same SHA Windows/Linux required
```

Il n'est pas destiné à qualifier une PR qui modifie précisément les workflows GitHub ; le gate durable de ces PR reste M21, complété par `MORPHEUS Security` sur `main` et `develop`.

Les switches/variables de skip D2 sont réservés au diagnostic et ne constituent jamais une qualification finale.

## Qualification d'une release publiée

Les validateurs `validate-*` qualifient un **commit**. Une release publiée demande une vérification
distincte, exécutée après coup et depuis une machine qui n'est pas le runner :

```bash
bash ./scripts/verify-release-provenance.sh v1.2.1
```

```powershell
.\scripts\verify-release-provenance.ps1 -Tag v1.2.1
```

Les deux variantes lisent ce que GitHub a réellement publié et échouent fermé sur : tag inatteignable
depuis `main`, asset manquant **ou inattendu**, checksum divergent, manifeste incohérent avec le tag ou
le commit, attestation de provenance non vérifiable, bundle d'attestation vide. Elles ne créent jamais
un tag, une release ni un asset.

Procédure complète et critère de clôture de l'issue #185 :
[docs/project/validation/RELEASE_QUALIFICATION.md](../docs/project/validation/RELEASE_QUALIFICATION.md).

Le dispatcher Windows sélectionne `scripts/validate-<target>.ps1`, lui transmet les arguments tels quels, et sort en échec dès que le validateur sort en échec (toujours avec le code 1, quel que soit celui du validateur).

## Tests des scripts

Les scripts de ce répertoire sont ce par quoi le dépôt tient ses gates ; ils sont eux-mêmes testés, sans construire
le produit :

```bash
python -m unittest discover -s scripts/tests
```

`MORPHEUS CI` lance cette commande sur ses deux jambes (`GateReachabilityArchitectureTest` refuse une jambe qui
cesse de le faire). Chaque jambe exécute les cas de son interpréteur : les cas `.ps1` exigent Windows PowerShell 5.1,
celui avec lequel le workflow lance les validateurs, et ne tournent donc que sous Windows ; les cas `.sh` tournent sur
les deux. En CI, un outil manquant (`bash`, `jq`, `powershell`) fait **échouer** le cas ; sur un poste de
développement il est sauté, avec sa raison.

### Comment les validateurs sont testés

`scripts/tests/validator_harness.py` fabrique un checkout : un dépôt Git temporaire qui contient les vrais
`scripts/`, les vrais POM, un faux `mvnw` qui enregistre ses arguments et ne construit rien, et les fichiers qu'un
`./mvnw clean verify` laisse derrière lui (rapports Surefire, preuve de couverture agrégée, SBOM). Un validateur trouve
le dépôt comme le parent de son propre répertoire : lancé depuis ce checkout, c'est lui qu'il valide. Chaque cas
altère **un** fichier d'un checkout qui passe, lance le vrai validateur, et vérifie le refus : code de sortie non nul,
message qui nomme la cause, aucune ligne `PASS` pour le contrôle refusé, aucun `validation-summary.txt`.

| Fichier | Ce qu'il tient |
|---|---|
| `test_validator_verdicts.py` | le contrat commun de `m21`, `d2` et `m22` à `m27`, sur le `.sh` et le `.ps1` ; pour chacun, le verdict suit les minimums écrits dans le `config/m21-quality-ratchets.properties` du checkout fabriqué (`m22` à `m27` comparaient jusqu'au 15/10/2026 aux planchers littéraux de leur date) |
| `test_validator_entry_refusals.py` | ce que `m19`, `m20`, `m28` et `r3` refusent avant d'avoir besoin d'un produit, le verdict complet de `r2`, et pour `r2` et `r3` le compte de POM qu'ils dérivent des `<module>` du POM racine |
| `test_validate_dispatcher.py` | `validate.ps1` et `validate.cmd` : cibles listées, cibles refusées, arguments transmis, échec propagé |
| `test_read_quality_ratchet.py`, `test_require_aggregate_coverage_evidence.py` | les bibliothèques de `lib/` écrites en deux langages, sur **une** liste de cas |
| `test_script_inventory.py` | l'inventaire : tout script est lancé par quelque chose, et exécuté par un test ou excusé par son nom |

Ce qu'un faux build peut prouver est borné : rien ici ne dit que Maven, le packaging ou l'installeur fonctionnent. Le
packaging est sauté par le switch de chaque validateur ; `test_script_inventory.py` dit, cible par cible, la partie
qu'aucun test n'atteint (`NOT_REACHED_BY_A_TEST`).

### Ajouter un cas

Dans `VerdictContract` (ou la classe propre à une cible), ajouter un scénario `given_<nom>(checkout, validate)` qui
altère le checkout, et un test qui appelle `self.refused("<nom>", "<le message attendu>")`. Le cas tourne alors contre
le `.sh` et le `.ps1` de chaque cible du contrat, sans autre changement. Les scénarios d'une classe sont joués une
fois, en parallèle, chacun dans son checkout.

Pour une bibliothèque de `lib/` écrite en deux langages, ajouter une ligne à la table `CASES` : elle est exécutée
contre les deux implémentations.

### Ajouter, retirer ou excuser un script

Tout fichier de test déclare ce qu'il exécute dans `SCRIPTS_UNDER_TEST`. Un script ajouté sans test, ou que plus
rien ne lance, fait échouer `test_script_inventory.py`, qui le nomme. Il n'y a pas d'exception silencieuse :

- est lancé ce que nomme un workflow, ou un script lui-même lancé ; une page de documentation ne lance rien ;
- un point d'entrée qu'une personne lance à la main (le dispatcher `validate.cmd`, la vérification de provenance
  d'une release publiée) est inscrit dans `LAUNCHED_BY_HAND`, avec la raison, et doit être nommé par une page
  courante ; ce qu'il nomme est lancé à son tour ;
- un script qu'aucun workflow, script lancé ou point d'entrée manuel ne nomme est inscrit dans `NOT_LAUNCHED`, avec
  la raison pour laquelle il est conservé ;
- un script qu'aucun test n'exécute est inscrit dans `NOT_RUN_BY_A_TEST`, avec la raison ;
- une entrée qui vise un script désormais lancé, testé ou supprimé fait échouer le test : elle se retire.

Une mention dans un commentaire ne lance rien, et un test qui lit un script sans l'exécuter ne le teste pas. La
déclaration `SCRIPTS_UNDER_TEST` est confrontée au code du test : un validateur doit être la cible d'une classe que le
harnais génère, une bibliothèque doit être nommée par un validateur que le module exécute, et tout autre script doit
apparaître dans une chaîne du code, hors de la déclaration et des docstrings. Que cette chaîne atteigne un processus
dans chaque cas, ce sont les cas eux-mêmes qui l'assertent.

Les validateurs `.sh` qu'aucun autre workflow ne lançait (`d2`, `m22` à `m26`, `r3`, et par la chaîne de `r3`,
`m28`, `r2`, `m27`) tournent chaque nuit dans la lane `validators-without-a-lane` de `nightly.yml`, en attendant que
le mainteneur décide pour chacun de le garder, de le tester ou de le retirer.

```bash
python scripts/tests/test_script_inventory.py     # imprime la table : lancé par, exécuté par, non atteint
```

Les documents et validateurs de preuve historiques (R2/R3/milestones) peuvent conserver les versions réellement exécutées à l’époque. Les guides et gates actifs utilisent `1.2.1`.
