# Reprise à froid

> Par où recommencer sur MORPHEUS après une longue interruption.

Ce document ne décrit **pas** l'architecture — [ARCHITECTURE.md](ARCHITECTURE.md) et
[l'arc42](../architecture/arc42/) le font déjà, et mieux. Il ne décrit pas non plus comment
contribuer : [CONTRIBUTING.md](../../CONTRIBUTING.md) énonce que MORPHEUS est un logiciel
propriétaire *source-available* dont les contributions externes ne sont pas acceptées par défaut.

Il répond à une seule question, celle qu'aucun autre document ne traite : **par où je
recommence ?** Il s'adresse au propriétaire revenant après six mois, ou à qui hériterait
légitimement du dépôt. Le dépôt est mono-mainteneur par choix assumé
([CODEOWNERS](../../.github/CODEOWNERS)) ; ce document existe parce que ce choix rend la
connaissance tacite plus périssable, pas parce qu'il faudrait recruter.

**Aucun seuil chiffré n'est recopié ici.** Chaque section renvoie au document normatif. Un
nombre écrit dans ce fichier serait un quatorzième endroit à maintenir et dériverait — c'est
exactement le défaut que la règle `.claude/rules/meta.md` interdit.

---

## 1. La commande qui dit si le dépôt est sain

Une seule commande qualifie l'ensemble : compilation, tests, couverture, gates d'architecture,
convergence des surfaces, intégrité de production, SBOM et provenance.

Linux et macOS :

```bash
bash ./scripts/validate-m21.sh <baseline>
```

Windows :

```powershell
.\scripts\validate.cmd m21 -Version <baseline>
```

`<baseline>` est la version que déclare le POM racine — jamais un littéral mémorisé. La source
unique est `ProductMetadata` ([Version produit](PRODUCT_VERSION.md)) ; en pratique :

```bash
grep -m1 '<version>' pom.xml
```

**M21 est le nom du gate durable d'intégrité et de convergence, pas le dernier milestone
livré.** Un dépôt sain répond `M21 VALIDATION PASS`.

Combien de temps : le réacteur complet plus l'empaquetage. Comptez des dizaines de minutes sur
une machine de développement — c'est un gate de qualification, pas une boucle de feedback. Pour
une boucle courte pendant le travail, `./mvnw test -pl morpheus-architecture-tests` suffit à
faire tomber la plupart des régressions structurelles.

Détail des gates, de leur portée et de leur ordre : [BUILD_AND_TEST.md](BUILD_AND_TEST.md).

---

## 2. Secrets et services externes

C'est la connaissance la plus périssable du dépôt, et la seule qui ne soit récupérable ni depuis
le code ni depuis l'historique Git. Rien ici n'est nécessaire pour **compiler** ou **qualifier**
MORPHEUS localement : les gates locaux n'ont besoin d'aucun secret. Ce qui suit ne concerne que
la CI.

| Secret / service | À quoi il sert | Ce qui casse sans lui |
|---|---|---|
| `SONAR_TOKEN` | Analyse SonarQube Cloud : job `sonar` de [`ci.yml`](../../.github/workflows/ci.yml) après un push sur `main`, job `sonar-develop` de `nightly.yml` sur cadence quotidienne | Ces deux jobs échouent. **Aucun merge n'est bloqué** : Sonar n'est délibérément pas un check requis, pour qu'une panne SonarCloud ne bloque pas une promotion dont les gates propres passent |
| `NVD_API_KEY` | Rafraîchir la base de vulnérabilités OWASP Dependency-Check dans [`security.yml`](../../.github/workflows/security.yml) | **Actuellement absent — voir RT-13 ci-dessous.** À terme : plus aucun rafraîchissement, la base périme, et *toutes* les pull requests échouent sur `STALE_DATABASE` |
| Attestations de release | Provenance signée des artefacts publiés ([`release.yml`](../../.github/workflows/release.yml), `actions/attest`) | Aucun secret à gérer : la signature passe par l'OIDC GitHub, via les permissions `id-token: write` et `attestations: write` déclarées par job. Retirer ces permissions supprime silencieusement la provenance des artefacts publiés |

Il n'existe **ni secret d'organisation** (le dépôt appartient à un compte utilisateur) **ni
environnement GitHub**. Ce que liste `gh secret list` est donc exhaustif.

### RT-13 — à lire en premier après une longue absence

`NVD_API_KEY` n'est pas configuré. Tant que `main` épingle Dependency-Check 12.2.2, dont la mise
à jour anonyme fonctionne, le job planifié quotidien alimente le cache que toutes les pull
requests réutilisent. À la promotion de la baseline courante, `main` prend le workflow qui
épingle 13.0.0, dont la mise à jour anonyme est cassée en amont : plus rien ne rafraîchit, plus
rien n'est sauvegardé, et environ 72 heures plus tard tout devient rouge.

Le résumé de job publie l'âge du cache et la marge restante à chaque exécution, et alerte avant
la falaise — mais **l'observabilité ne remplace pas le secret**. Depuis le 09/09/2026 cet âge
est lu sur une **sentinelle écrite par le rafraîchissement lui-même**, et non plus déduit du
`mtime` d'un fichier : la déduction précédente annonçait 54 h sur une base rafraîchie 15 h plus
tôt. Un cache restauré sans sentinelle a un âge **inconnu**, donc refusé — jamais supposé frais.
Le détail complet, les preuves datées et les trois sorties possibles sont dans
[le registre des risques, RT-13](../architecture/risks/register.md).

---

## 3. Les invariants qui ne se négocient pas

MORPHEUS a la propriété inhabituelle que **ses règles sont exécutables**. Il n'existe presque
aucune convention non vérifiée : si une règle n'a pas de test, elle n'existe pas. En cas de
doute, lancez le test — ne devinez jamais.

| Invariant | Ce qui le fait respecter |
|---|---|
| Distinctions sémantiques du domaine (`UNKNOWN != BLOCKED`, `PROPOSED` ne fuit pas dans `CURRENT`, `saved view != materialized truth`…) | La liste « Invariants structurants » du [README](../../README.md), et les suites de contrat par domaine |
| Frontières de modules : `application` ne connaît aucun adaptateur, les adaptateurs sont frères, `domain` ne dépend de rien | `LayerDependencyTest` et les `*ArchitectureTest` par milestone dans `morpheus-architecture-tests` |
| Convergence des surfaces publiques CLI / MCP / HTTP | [`contracts/public-surfaces.tsv`](../../contracts/public-surfaces.tsv), comparé caractère par caractère avec les specs OpenAPI. **Une case vide est une violation** : une absence se déclare avec une sentinelle explicite (`EXPLICITLY_NOT_EXPOSED`, `EXPLICITLY_LOCAL_ONLY`…) |
| Sécurité : pas de typage par défaut Jackson, serveur local en loopback, plugins fail-closed, actions CI épinglées par SHA | `D2RepositoryHardeningArchitectureTest` et les suites de sécurité associées |
| Les deux échelles de couverture ne se mélangent pas | `CoverageScaleSeparationTest` — voir §4 |

Le *pourquoi* de chaque décision structurelle vit dans [`docs/adr/`](../adr/). Comptez les ADR
avec un `glob` sur `docs/adr/0*.md` avant d'en citer le nombre ou d'en attribuer un nouveau : le
répertoire contient aussi un `README.md`, et un doublon de numérotation a déjà existé.

---

## 4. Comment lire une preuve

Chaque validateur écrit `validation-output/<gate>/validation-summary.txt`. C'est la preuve, et
elle se lit sans contexte :

```text
M21 VALIDATION PASS
sha=<le commit exact qualifié>
baseRef=<la base de comparaison>
version=<la baseline>
tests=<total surefire>
architectureTests=<total architecture>
coverageScope=aggregate
lineCoverage=<mesuré>
branchCoverage=<mesuré>
qualityRatchets=<les minima appliqués>
sbom=PASS
provenance=PASS
```

Deux points valent qu'on s'y arrête.

**`sha=` est la question à poser en premier.** Une preuve qualifie un commit précis. Une preuve
dont le `sha` n'est pas celui qu'on croit examiner ne prouve rien sur le code en question.

**`coverageScope=` n'est pas décoratif.** Deux gates mesurent la couverture et ne mesurent pas
la même grandeur :

- l'échelle **agrégée** (`morpheus-coverage-report`) lit le rapport `jacoco-aggregate`, qui
  fusionne l'exécution cross-module des tests d'architecture. C'est la mesure canonique ;
- l'échelle **par module** somme les rapports JaCoCo de chaque module, le module de tests
  d'architecture exclu. Un service exercé uniquement à travers les tests d'un module voisin y
  apparaît comme non couvert.

Les deux échelles portent pourtant sur la même population — tout module du réacteur qui porte une
classe sous `src/main/java`, outillage de vérification compris — et ne diffèrent que par les
exécutions qui créditent une ligne. Elles ont donc des ratios légitimement différents, des clés de seuil distinctes dans
`config/m21-quality-ratchets.properties` (préfixes `aggregate*` et `perModule*`), des fichiers de
preuve distincts et des messages distincts. **Comparer un ratio par module à un seuil agrégé, ou
l'inverse, produit une conclusion fausse** — c'est précisément le défaut que la PR #297 a corrigé,
et `CoverageScaleSeparationTest` fait échouer le build si un gate lit la clé, le rapport ou la
preuve de l'autre échelle.

D'où la règle : **une preuve dont la première ligne ne déclare pas sa portée doit être refusée.**
Les validateurs `validate-m21.*` l'appliquent eux-mêmes. Un chiffre de couverture sans son
échelle ne veut rien dire, et une preuve muette sur sa portée invite à le lire sur la mauvaise.

---

## 5. Un gate rouge n'est pas toujours une régression

Trois échecs connus n'indiquent aucun défaut du code. Savoir les reconnaître évite de chercher
une régression qui n'existe pas — ou, pire, d'affaiblir un gate pour le faire taire.

**Base Dependency-Check périmée.** Le workflow le dit lui-même : le motif d'échec est
`STALE_DATABASE`, distinct de `VULNERABILITY_THRESHOLD_EXCEEDED`. C'est une panne
d'infrastructure — la base de vulnérabilités est trop vieille pour qu'on scanne contre elle —
et non une CVE. Voir RT-13 au §2. Ne **jamais** relâcher
`DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS` pour faire disparaître le symptôme : une base plus
vieille est une base moins fiable, et le problème est le rafraîchissement, pas le seuil.

**Premier scan après un changement de namespace de cache.** La clé de cache Dependency-Check
est construite sur la **version de schéma H2** (`<dependency-check.data.version>` du POM racine),
pas sur la version du plugin. Changer cette valeur — ou introduire la sentinelle, comme le
09/09/2026 — invalide délibérément le cache : le scan suivant ne trouve rien, refuse sur
`STALE_DATABASE`, et c'est la bonne réponse. La fenêtre se referme au premier événement de
confiance qui écrit une sentinelle (`schedule` ou `workflow_dispatch` sur `main`), ou
immédiatement si `NVD_API_KEY` est configuré. **Ne pas** ajouter de repli pour la raccourcir :
un repli qui accepte une base non datée annule la mesure qu'il est censé protéger.

**Voie macOS (RT-08).** macOS enracine son répertoire temporaire sous `/var`, lien symbolique
vers `/private/var`, et MORPHEUS refuse tout chemin atteint par lien symbolique. **Ce refus est
un invariant de sécurité qui fonctionne exactement comme prévu.** La voie est `continue-on-error`,
tourne sur cadence quotidienne bornée dans `nightly.yml`, et n'est pas réparable depuis la CI :
ni un `TMPDIR` résolu ni `-Djava.io.tmpdir` n'atteignent la JVM de test forkée sur cette
plateforme. Fermer RT-08 suppose une décision de support macOS, pas un réglage de workflow.

**Variation de couverture entre deux exécutions.** Deux exécutions du même commit sur la même
machine peuvent différer de quelques lignes couvertes, et Linux couvre légèrement moins que
Windows à nombre de tests identique — certains tests no-opent hors de leur OS. Les ratchets sont
délibérément placés *sous* la mesure réelle pour absorber cette variation. Un échec juste sous le
seuil se diagnostique en relançant, pas en baissant le ratchet.

**Ne jamais baisser un ratchet pour faire passer un build ; écrire les tests manquants.** Un
ratchet ne descend pas. Le relever exige une preuve reproductible sur les deux plateformes, et
n'affecte que l'échelle réellement mesurée.

Sur cette machine de développement, trois réglages d'environnement font échouer les tests pour
des raisons qui ne sont pas des défauts du dépôt — ACL du répertoire temporaire, JDK par défaut,
variables héritées de Git Bash. Ils sont propres au poste et ne sont pas documentés ici.

---

## 6. Où vit la connaissance tacite

| Question | Document |
|---|---|
| Qu'est-ce qui est ouvert, connu, accepté ? | [Registre des risques](../architecture/risks/register.md) — risques techniques `RT-*`, dette, incohérences documentaires connues, état CI actif |
| Pourquoi cette décision plutôt qu'une autre ? | [Index des ADR](../adr/README.md) |
| Comment on compile, teste et qualifie | [BUILD_AND_TEST.md](BUILD_AND_TEST.md) |
| Ce que la production garantit et comment on le prouve | [PRODUCTION_INTEGRITY.md](PRODUCTION_INTEGRITY.md) |
| Où en est la feuille de route | [ROADMAP.md](../governance/ROADMAP.md) |
| Quels documents sont actifs, historiques ou périmés | [DOCUMENTATION_STATUS.md](../governance/DOCUMENTATION_STATUS.md) |
| Comment l'IA est paramétrée sur ce dépôt | [`.github/AI_GOVERNANCE.md`](../../.github/AI_GOVERNANCE.md) et `.claude/rules/` |

### Le piège des ratchets

`config/m21-quality-ratchets.properties` est la **source normative** des minima. Ses valeurs sont
aussi **restatées en prose** dans une douzaine de fichiers — README, docs de build, arc42,
registre des risques, scripts, règles `.claude/` — parce qu'un lecteur veut le chiffre là où il
lit, pas un renvoi.

Conséquence : une hausse de ratchet doit être répercutée partout **dans le même changement**, et
la liste des destinations n'est pas fiable de mémoire. Ne la reconstituez pas à la main —
lancez :

```bash
./mvnw test -pl morpheus-architecture-tests -Dtest='RepositoryDocumentationCoherenceTest,ProductionIntegrityContractTest'
```

et laissez les échecs énumérer les destinations réelles. C'est la seule méthode qui ne rate pas
un fichier : une hausse antérieure avait fait échouer quatre tests d'architecture sur des
fichiers absents de la liste écrite à l'époque.

Les **mesures historiques datées** ne se réécrivent pas. Un document qui dit « mesuré le
JJ/MM/AAAA » est un enregistrement, pas une affirmation courante : le mettre à jour falsifierait
une preuve. Seule une valeur active se met à jour.
