# Proposition : une seule branche longue, `main`

## Why

Le dépôt tient deux branches longues : `develop` reçoit chaque pull request, `main` ne bouge qu'à une promotion. Pour
un mainteneur seul, cet écart produit des défauts mesurés, sans apporter de garantie que les tags `v*` n'apportent
déjà :

- **`main` publiée en retard sur ses correctifs de sécurité.** Le 11/11/2026, le scan de `main` (run `43108324489`)
  trouvait `sqlite-jdbc` 3.53.4.0 et quatre CVE notées 8.5. Leur correctif, `sqlite-jdbc` 3.54.1.0, était dans `develop`
  depuis des semaines. `main` n'avait pas bougé depuis le 18/10 (`bd994fcc`, PR #516).
- **Une panne de clé passée inaperçue six jours.** Le rafraîchissement planifié de `MORPHEUS Security` sur `main` a
  échoué du 06/11 au 11/11 (`Invalid API Key`, run `42958923568`), parce que personne ne regarde une branche qui ne
  bouge pas. Elle n'a été vue qu'en ouvrant la promotion #631.
- **Chaque promotion coûte deux pull requests et une CI en double.** #631 (548 commits) puis le back-merge #632.
  Le coût du double run est accepté et mesuré par DT-13 dans `docs/architecture/risks/register.md`. Une promotion de
  548 commits n'est pas relisible : elle ne vérifie rien que la CI de `develop` n'ait déjà vérifié, sauf la couverture
  des lignes modifiées contre `main`.

Pourquoi maintenant : `main` et `develop` ont aujourd'hui le même arbre (#631 fusionnée, #632 ouverte, diff vide), et
aucune autre pull request n'est ouverte. Le passage à une seule branche ne demande donc aucune réconciliation de
contenu.

## What Changes

- **Branche unique.** Toute pull request vise `main`. La branche `develop` est supprimée une fois le changement
  fusionné. Une release reste un tag `v*` posé sur un commit atteignable depuis `main`, ce que `release.yml` vérifie
  déjà avant de construire quoi que ce soit.
- **Workflows.** `ci.yml`, `codeql.yml` et `security.yml` ne filtrent plus que `main`. La garde de couverture des
  lignes modifiées de `ci.yml` ne connaît plus que la base `main`. `nightly.yml` analyse le commit de sa branche par
  défaut au lieu d'épingler `develop`, et perd le job `sonar-develop`, puisque le job `sonar` de `ci.yml` analyse
  déjà chaque push sur `main`, c'est-à-dire chaque fusion. Les commentaires de `release.yml` sont alignés.
- **Dependabot.** Les deux écosystèmes visent la branche par défaut. `target-branch: develop` disparaît.
- **Validateurs.** La base de comparaison par défaut de `validate-d2`, `validate-m25` à `validate-m28`, `validate-r2`
  et `validate-r3` (`.ps1` et `.sh`) passe à `origin/main`, avec `main` en repli. Leurs tests sous `scripts/tests/`
  suivent.
- **Tests d'architecture.** Les assertions qui exigent `develop` sont réécrites pour exiger la nouvelle forme
  (`D2RepositoryHardeningArchitectureTest`, `AuditHardeningWorkflowContractTest`,
  `SonarQualityGateClassificationContractTest`). Aucune n'est supprimée sans une assertion équivalente ou plus stricte.
- **Gouvernance et documentation vivante.** `.claude/rules/` (`build.md`, `governance.md`), `.claude/settings.json`,
  `.claude/hooks/pre-bash.ps1`, la skill `coverage-ratchet`, `.archgate/config.json` (`baseBranch`), `SECURITY.md`,
  et les pages vivantes de `docs/developer/`, `docs/architecture/` et
  `docs/project/governance/`. DT-13 est retirée du registre des risques.
- **Hors dépôt, par le mainteneur.** Le ruleset GitHub « Protect main & develop » perd l'inclusion de `develop` et se
  renomme « Protect main ». La branche `develop` n'est supprimée qu'après ce retrait, puisque le ruleset en interdit
  la suppression.
- **BREAKING (processus de contribution).** Une pull request ouverte vers `develop` est refusée après la suppression
  de la branche : elle doit être reciblée sur `main`.

## Capabilities

### New Capabilities

- `single-integration-branch` : le modèle de branches du dépôt. `main` est la seule branche longue et la cible de
  toute pull request. Une release est un tag atteignable depuis `main`. Aucun workflow, validateur ni configuration
  versionnée ne nomme `develop`.

### Modified Capabilities

- `gate-reachability` : la lane nocturne des budgets M19 et la répétition de release analysent, sur planification,
  le commit de la branche par défaut (`main`), et non plus celui de `develop`.
- `vulnerability-feed-freshness` : le ruleset dont l'alerte ne doit pas être un check requis s'appelle « Protect
  main ».

## Périmètre et exclusions

**Inclus :** les workflows, Dependabot, les validateurs et leurs tests, les tests d'architecture qui nomment
`develop`, la configuration Archgate et Claude Code, les pages de documentation vivantes, le registre des risques,
la procédure de bascule GitHub.

**Exclus :**
- les relevés datés (audits, `VALIDATION_*`, plans d'exécution, notes de version, changements OpenSpec archivés) et
  les mesures datées citées dans des commentaires ou dans `.claude/rules/testing.md` (« Requalifié le … sur `develop`
  à … ») : ils décrivent ce qui était vrai à leur date et ne se réécrivent pas ;
- la publication de la 1.2.2, une étape indépendante ;
- une branche de maintenance d'une version publiée (voir Questions ouvertes) ;
- l'échec du job automatique `update-pip-graph` sur `main` (transcripts nommés `requirements-*.txt`), un autre sujet.

## Acteurs et surfaces concernés

Le mainteneur (cible des pull requests, rulesets GitHub, suppression de la branche), la CI GitHub Actions
(déclencheurs, base de la garde de couverture, lanes nocturnes, cache Dependency-Check), Dependabot (branche cible),
SonarQube Cloud (branche analysée), et les agents qui lisent `.claude/` et Archgate. Aucune surface du produit (CLI,
MCP, HTTP, schéma SQLite) ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| `main` a porté `sqlite-jdbc` 3.53.4.0 vulnérable alors que `develop` avait le correctif | existant vérifié | run `43108324489` (11/11/2026), PR #631 |
| Le refresh planifié de `main` a échoué six jours sur `Invalid API Key` | existant vérifié | runs `42162196141` à `42958923568` de `security.yml` |
| Le double run de la promotion est un coût accepté | existant vérifié | DT-13, `docs/architecture/risks/register.md` |
| Le ruleset protège `~DEFAULT_BRANCH` et `refs/heads/develop`, avec checks requis stricts | existant vérifié | `gh api repos/FTurleque/morpheus-engine/rulesets/20792822`, lu le 11/11/2026 |
| Le job `sonar` de `ci.yml` analyse chaque push sur `main` | existant vérifié | `.github/workflows/ci.yml`, condition `github.event_name == 'push' && github.ref_name == 'main'` |
| `main` devient la seule branche longue, releases par tags | décision | demande du mainteneur, 11/11/2026 |
| Le modèle de branches n'appelle pas d'ADR | décision | `.claude/rules/adr.md` : le retour arrière (recréer une branche, rétablir des filtres) ne casse ni données, ni contrat, ni garantie de sécurité |
| `release.yml` refuse un tag qui n'est pas atteignable depuis `main` | existant vérifié | étapes « Verify tag belongs to main » de `.github/workflows/release.yml` |

## Impact

- `.github/workflows/{ci,codeql,security,nightly,release}.yml`, `.github/dependabot.yml`.
- `scripts/validate-{d2,m25,m26,m27,m28,r2,r3}.{ps1,sh}`, `scripts/README.md` et les tests `scripts/tests/` qui
  fabriquent une branche `develop`.
- `morpheus-architecture-tests` : `D2RepositoryHardeningArchitectureTest`, `AuditHardeningWorkflowContractTest`,
  `SonarQualityGateClassificationContractTest` (assertions textuelles sur des `.yml`, qui restent textuelles). Le
  compte d'exécutions déclarées ne doit pas baisser (`architectureTestsMinimum`).
- `.archgate/config.json`, `.claude/rules/{build,governance}.md`, `.claude/settings.json`,
  `.claude/hooks/pre-bash.ps1` (ASCII pur), `.claude/skills/coverage-ratchet/SKILL.md`.
- Documentation vivante : `SECURITY.md`, `docs/developer/{README,BUILD_AND_TEST,ARCHGATE}.md`
  et les autres pages listées en design, `docs/project/governance/{ROADMAP,README}.md`,
  `docs/architecture/risks/register.md`, `docs/architecture/arc42/` là où une phrase au présent nomme `develop`.
- Aucun ADR nouveau. GEN-0133 contient une phrase au présent sur la CI de « `develop` et `main` » : elle est clarifiée
  sans que la décision change.

## Questions ouvertes

- Corriger une version déjà publiée sans livrer `main` demanderait une branche de maintenance, et un tag que
  `release.yml` refuse aujourd'hui (tag non atteignable depuis `main`). Aucun correctif de ce type n'a été fait à ce
  jour. La question est laissée au mainteneur et à un changement séparé, si le besoin apparaît.

- Le mainteneur veut-il garder la protection `develop` dans le hook `pre-bash.ps1` et les refus de
  `.claude/settings.json` comme filet contre une recréation accidentelle, ou les retirer avec la branche ? La
  proposition par défaut est de les retirer : `master` y figure déjà sans exister, et la liste doit dire ce qui est
  protégé.
- Faut-il que le ruleset de `main` passe les checks requis en mode non strict, pour ne pas rebaser chaque pull
  request après chaque fusion ? À décider par le mainteneur. Le changement n'en dépend pas.
