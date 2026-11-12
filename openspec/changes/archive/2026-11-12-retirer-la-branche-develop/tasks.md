# Tâches : une seule branche longue, `main`

Le travail se fait sur une branche créée depuis `main` et livrée par une seule pull request vers `main`. La bascule
GitHub (groupe 6) vient après la fusion.

## 1. Contrats d'architecture, écrits avant les workflows

- [x] 1.1 Ajouter à `D2RepositoryHardeningArchitectureTest` une méthode (à écrire) qui lit `.github/workflows/*.yml`, `.github/dependabot.yml`, `scripts/validate-*.ps1`, `scripts/validate-*.sh` et `.archgate/config.json`, refuse `\bdevelop\b` en nommant chaque fichier fautif, et exige l'absence de `target-branch` dans `dependabot.yml` — exigence : `single-integration-branch` › Aucune configuration versionnée ne DOIT nommer une branche d'intégration autre que main ; `single-integration-branch` › Dependabot DOIT viser la branche par défaut ; vérification : la méthode échoue sur l'arbre actuel et nomme les fichiers attendus (les cinq workflows, `dependabot.yml`, les validateurs, `.archgate/config.json`)
- [x] 1.2 Réécrire dans `D2RepositoryHardeningArchitectureTest` les assertions `branches: [main, develop]` (`security.yml`, `codeql.yml`) en `branches: [main]`, et `target-branch: develop` en absence de `target-branch` — exigence : `single-integration-branch` › Aucune configuration versionnée ne DOIT nommer une branche d'intégration autre que main ; vérification : les assertions échouent avant le groupe 2
- [x] 1.3 Compléter `AuditHardeningWorkflowContractTest#pullRequestCoverageGateIncludesChangedBranches` : exiger `origin/main...HEAD` et `Unsupported pull-request base branch` dans `ci.yml` — exigence : `single-integration-branch` › Toute pull request DOIT être comparée à main pour la couverture des lignes modifiées ; vérification : la méthode passe, et échoue si la branche `main)` du `case` est retirée (violation plantée, notée dans la PR)
- [x] 1.4 Renommer et réécrire `AuditHardeningWorkflowContractTest#developIsAnalysedOnABoundedCadenceInAWorkflowNoPullRequestCanReach` : `nightly.yml` ne porte aucun `SONAR_TOKEN`, n'a aucun déclencheur `pull_request` et garde sa planification ; `ci.yml` garde `if: github.event_name == 'push' && github.ref_name == 'main'` sur le job `sonar` — exigence : `single-integration-branch` › Chaque fusion dans main DOIT recevoir une analyse statique ; vérification : la méthode échoue tant que `sonar-develop` existe, et le compte de méthodes `@Test` du module ne baisse pas
- [x] 1.5 Réécrire `SonarQualityGateClassificationContractTest` : aucun workflow ne nomme `SONAR_BRANCH_NAME`, et `scripts/classify-sonar-quality-gate.sh` ne contient plus `UNREADABLE_OFF_MAIN` — exigence : `single-integration-branch` › Chaque fusion dans main DOIT recevoir une analyse statique ; vérification : la méthode échoue avant le groupe 2

## 2. Workflows et Dependabot

- [x] 2.1 `ci.yml` : `push.branches: [main]`, `case` de la garde de couverture réduit à `main)` et `*)`, commentaire du job `sonar` réécrit au présent sans renvoi à `sonar-develop` — exigence : `single-integration-branch` › Toute pull request DOIT être comparée à main pour la couverture des lignes modifiées ; vérification : tâches 1.3 et 1.1 au vert
- [x] 2.2 `codeql.yml` et `security.yml` : `branches: [main]` sur `push` et `pull_request` — exigence : `single-integration-branch` › Aucune configuration versionnée ne DOIT nommer une branche d'intégration autre que main ; vérification : tâche 1.2 au vert, `DependencyCheckWorkflowContractTest` inchangé et au vert
- [x] 2.3 `nightly.yml` : supprimer le job `sonar-develop`, remplacer les sept `ref: ${{ github.event_name == 'schedule' && 'develop' || github.ref }}` par `ref: ${{ github.ref }}`, retirer `ref: develop` de `macos-smoke`, réécrire l'en-tête et les noms d'étapes « or develop on a schedule » — exigence : `gate-reachability` › The M19 budget gates SHALL run on a schedule on both platforms and a missed budget SHALL fail the run ; `gate-reachability` › The release build SHALL be rehearsed on a schedule on both platforms and a failed rehearsal SHALL fail the run ; vérification : tâches 1.1 et 1.4 au vert, `GateReachabilityArchitectureTest` au vert
- [x] 2.4 `release.yml` : commentaire du job Windows réécrit (« every push to main and every PR ») — exigence : `single-integration-branch` › Aucune configuration versionnée ne DOIT nommer une branche d'intégration autre que main ; vérification : tâche 1.1 au vert, `release.yml` sans autre modification (`git diff` limité au commentaire)
- [x] 2.5 `dependabot.yml` : retirer les deux `target-branch: develop` — exigence : `single-integration-branch` › Dependabot DOIT viser la branche par défaut ; vérification : tâches 1.1 et 1.2 au vert
- [x] 2.6 Retirer l'exemption `UNREADABLE_OFF_MAIN` de `scripts/classify-sonar-quality-gate.sh` et adapter `scripts/tests/test_classify_sonar_quality_gate.py` : un 403 est une erreur sur toute branche — exigence : `single-integration-branch` › Chaque fusion dans main DOIT recevoir une analyse statique ; vérification : `python -m unittest scripts.tests.test_classify_sonar_quality_gate` au vert, tâche 1.5 au vert

## 3. Validateurs et leurs tests

- [x] 3.1 `scripts/validate-{d2,m25,m26,m27}.{ps1,sh}` : base par défaut `origin/main`, repli `main`, messages d'échec qui nomment les deux ; `scripts/validate-{m28,r2,r3}.{ps1,sh}` : base par défaut `origin/main`. Les `.ps1` restent en ASCII pur — exigence : `single-integration-branch` › La base de comparaison par défaut d'un validateur DOIT être main ; vérification : tâche 1.1 au vert, `GateReachabilityArchitectureTest` au vert
- [x] 3.2 Adapter `scripts/tests/{validator_harness,test_validator_verdicts,test_validator_entry_refusals,test_validate_dispatcher,test_dependency_check_scripts,test_write_build_provenance}.py` : dépôts fabriqués et branches de donnée sur `main` — exigence : `single-integration-branch` › La base de comparaison par défaut d'un validateur DOIT être main ; vérification : `python -m unittest discover -s scripts/tests -v` au vert sous Linux (WSL) et Windows
- [x] 3.3 `scripts/README.md`, `docs/developer/README.md` et `docs/developer/BUILD_AND_TEST.md` : exemples `-BaseRef origin/main` et `MORPHEUS_D2_BASE_REF=origin/main` — vérification : `RepositoryDocumentationCoherenceTest` au vert

## 4. Configuration des agents

- [x] 4.1 `.archgate/config.json` : `baseBranch` à `origin/main` — exigence : `single-integration-branch` › Aucune configuration versionnée ne DOIT nommer une branche d'intégration autre que main ; vérification : tâche 1.1 au vert, `archgate check` sans erreur
- [x] 4.2 `.claude/settings.json` (refus de force-push) et `.claude/hooks/pre-bash.ps1` (motif des branches protégées) : retirer `develop`, garder `main` et `master` — vérification : `echo '{"command":"git push --force origin main"}' | powershell -NoProfile -File .claude/hooks/pre-bash.ps1` bloque (code 2), le fichier reste ASCII
- [x] 4.3 `.claude/rules/build.md` (`security.yml` et `dependabot.yml`), `.claude/rules/governance.md` (« Jamais force-pusher sur `main` »), `.claude/skills/coverage-ratchet/SKILL.md` (runs d'une requalification : `push` de `main` et `workflow_dispatch` du même SHA) — vérification : `GovernanceSurfaceCoherenceTest` au vert. Les mesures datées de `.claude/rules/testing.md` ne bougent pas

## 5. Documentation vivante et registre des risques

- [x] 5.1 Écrire la page durable `docs/developer/specifications/single-integration-branch.md` (modèle de branches, ordre de bascule repris du diagramme de séquence de `design.md`, matrice de traçabilité) et l'indexer dans `docs/developer/specifications/README.md` — exigence : toutes celles de `single-integration-branch` ; vérification : `RepositoryDocumentationCoherenceTest` au vert (liens), diagramme Mermaid rendu
- [x] 5.2 Mettre à jour les pages durables de `gate-reachability` et de `vulnerability-feed-freshness` sous `docs/developer/specifications/` si elles nomment `develop` ou « Protect main & develop » — exigence : les exigences modifiées des deux capacités ; vérification : `git grep -w develop docs/developer/specifications` vide
- [x] 5.3 Classer chaque occurrence de `develop` des pages vivantes listées dans `design.md` (« Documentation durable ») et de `SECURITY.md`, `distribution/README.md`, `docs/user/UPGRADE_1_2.md` : réécrire au présent une phrase qui décrit l'état actif, laisser une mesure ou un état daté — vérification : `UserDocumentationSpeaksOfTheInstalledProductTest` et `RepositoryDocumentationCoherenceTest` au vert ; la PR liste les occurrences laissées et pourquoi
- [x] 5.4 `docs/architecture/risks/register.md` : retirer DT-13 du tableau actif, réécrire RT-13 et les lignes d'état actif (CI, ruleset « Protect main »), laisser les tableaux de preuves datés ; `docs/architecture/arc42/11-risques-dette.md` aligné — vérification : `ProductionIntegrityContractTest` et `RepositoryDocumentationCoherenceTest` au vert
- [x] 5.5 GEN-0133 : clarifier la phrase « `develop` et `main` » sans changer la décision — vérification : `AdrIndexCoherenceTest` au vert, `archgate check` sans erreur
- [x] 5.6 `docs/project/governance/ROADMAP.md` (schéma des branches) et `docs/project/governance/README.md` : modèle à une branche — vérification : `RepositoryDocumentationCoherenceTest` au vert

## 6. Bascule GitHub, après fusion

- [x] 6.1 Fermer la pull request #632 (back-merge devenu sans objet), avec l'accord du mainteneur — vérification : `gh pr view 632 --json state` vaut `CLOSED`
- [x] 6.2 Mainteneur : retirer `refs/heads/develop` du ruleset `20792822` et le renommer « Protect main » — exigence : `vulnerability-feed-freshness` › The alert SHALL come after the scan and SHALL NOT block by name ; vérification : `gh api repos/FTurleque/morpheus-engine/rulesets/20792822` montre `include: ["~DEFAULT_BRANCH"]`, les quatre checks requis et aucun nom de job d'alerte
- [x] 6.3 Mainteneur : supprimer la branche `develop` — vérification : `git ls-remote --heads origin develop` vide
- [x] 6.4 Constater le premier run planifié de `nightly.yml` sur `main` (budgets M19 et répétition de release sur les deux plateformes) et le job `sonar` du push de la fusion — exigence : `gate-reachability` › les deux exigences modifiées ; `single-integration-branch` › Chaque fusion dans main DOIT recevoir une analyse statique ; vérification : identifiants de run notés dans la PR ou dans la page durable

## 7. Vérification d'ensemble

- [x] 7.1 Lancer `openspec validate retirer-la-branche-develop --strict` — vérification : aucun avertissement
- [x] 7.2 Compléter la matrice de traçabilité de `docs/developer/specifications/single-integration-branch.md` (exigence → scénario → décision de `design.md` → tâche → preuve) — vérification : chaque exigence a une preuve existante ou est marquée « preuve manquante »
- [x] 7.3 Lancer `./mvnw test -pl morpheus-architecture-tests` puis laisser la CI de la pull request exécuter `./mvnw clean verify` sur Linux et Windows — vérification : verdict de `morpheus-architecture-tests/target/enforcement-verdict.txt` sans échec, checks requis verts, compte d'exécutions déclarées au moins égal à `architectureTestsMinimum`
- [x] 7.4 Lancer `archgate check` — vérification : aucune violation, avertissements rapportés au mainteneur

## Workflow follow-up

- Archiver le changement après la bascule (groupe 6), sur demande explicite.
- Après l'archivage, relire les specs principales consolidées et la page durable.
