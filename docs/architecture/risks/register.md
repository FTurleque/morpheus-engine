# Registre des risques et de la dette — MORPHEUS ENGINE

> Synthèse opérationnelle de [§11](../arc42/11-risques-dette.md).
> Baseline active : MORPHEUS 1.2.1 corrective — branche de développement post-audit.
> Dernière release publiée : `v1.2.0`.
> Mise à jour : **2026-09-04** (passes post-audit A-02..A-16 ; A-01 suivie séparément selon son cycle de qualification réelle — voir #185).
>
> **P** = probabilité, **I** = impact, **E** = exposition = P × I ; échelle 1 à 3.

---

## Risques techniques

| ID | Risque | P | I | E | Mitigation actuelle | Révision |
|----|--------|:-:|:-:|:-:|---------------------|----------|
| RT-01 | Concurrence SQLite si le mode remote devient fortement multi-écrivain | 2 | 3 | **6** | `journal_mode=PERSIST`, busy timeout, transactions bornées, une connexion physique scopée par opération API/Query/CLI, leases, réservation persistante atomique des séquences de version, index d'unicité, backups ; **observabilité de contention** (`sqlite.contention.*`, `sqlite.transaction.duration`) et tests de stress multi-écrivain | Si le profil d'usage remote évolue |
| RT-02 | Rollback applicatif après migration de schéma | 2 | 3 | **6** | Migrations forward-only, checksums, refus des schémas futurs, backup/restore offline | À chaque évolution de schéma |
| RT-03 | Limites de `jdk.httpserver` sous forte charge | 2 | 2 | **4** | Concurrence remote bornée, timeouts/budgets et inventaires filesystem bornés ; **harnais de charge reproductible** (`MorpheusRemoteLoadProfileTest`) et **critères objectifs de remplacement** documentés — mesurer avant substitution | Lors de load tests représentatifs |
| RT-04 | Breaking change MCP SDK / clients MCP | 2 | 2 | **4** | Version épinglée, tests de contrat, budgets de frames/queues, cancellation serveur, cleanup fail-closed et diagnostics redacted | À chaque upgrade MCP |
| RT-05 | Provider externe malformé, bloquant ou non fiable | 2 | 2 | **4** | Discovery metadata-only sans symlink avec revalidation d'identité avant/après lecture, activation explicite, SHA-256 obligatoire en remote, staging vérifié, budgets d'ingestion et environnement enfant minimisé | À chaque évolution du Provider SDK |
| RT-12 | Peer MCP externe MINOS/NEXUS compromis | 2 | 2 | **4** | JAR optionnel/pinnable, environnement hérité réduit à une allowlist, descendants observés et terminés, frames/queues bornées, stderr et exceptions peer redacted ; la frontière n'est pas une sandbox OS | À chaque évolution du transport MCP |
| RT-06 | Diagnostic runtime limité par le logging silencieux | 2 | 2 | **4** | Health/metrics, erreurs structurées et diagnostics MCP sanitizés ; préserver stdout MCP | Permanent |
| RT-09 | Drift documentaire entre sources historiques et HEAD | 2 | 2 | **4** | Hiérarchie des sources, séparation release publiée `1.2.0` / baseline active `1.2.1`, guides actifs réconciliés et contrats d'architecture sur les invariants CI | À chaque release/hardening |
| RT-07 | Auth remote sans SSO/LDAP | 1 | 2 | **2** | Bearer auth + RBAC, mutations inter-processus sérialisées, live reload, audit secret-free roulant borné ; mot de passe TLS résolu tardivement en `char[]` et jamais retenu dans les options de lancement | Si besoin entreprise démontré |
| RT-08 | macOS non qualifié | 2 | 1 | **2** | Support officiel Windows + Linux uniquement ; lane `macos-smoke` **advisory** (`continue-on-error`) de `nightly.yml`, sur cadence quotidienne bornée et non par pull request, qui exécute le reactor complet et publie les faits système observés — observation, pas qualification | Si support macOS décidé |

### RT-13 — la base Dependency-Check n'avait plus de source de rafraîchissement (constaté le 09/09/2026, clos le 22/09/2026)

> **Clos le 22/09/2026.** Sortie n° 1 retenue : `NVD_API_KEY` est configuré. Posé le 17/09/2026, il a d'abord
> été refusé par le NVD (run `35269719882` sur `develop`, 17/09/2026, « Invalid API Key », puis chaque
> `schedule` de `main` jusqu'au run `35687241977` du 22/09/2026 04:32 UTC), avant d'être remplacé le
> 22/09/2026 à 07:40 UTC. Il est accepté depuis, par les deux versions épinglées :
>
> | Branche | Dependency-Check | Run | Constat |
> |---|---|---|---|
> | `main` | 12.2.2 | `35701902846` (`workflow_dispatch`, 22/09/2026) | `Wrote trusted Dependency-Check refresh sentinel (schema 5.6, plugin 12.2.2, via NVD API key refresh)` — `main` ne publie pas encore la ligne `Obtained via`, la preuve est la ligne du rédacteur de sentinelle |
> | `develop` | 13.0.0 | `35744008694` (`push`, merge de #345, 22/09/2026) | sentinelle écrite `via NVD API key refresh`, `obtained via NVD API key refresh: 0h 00m old`, aucune annotation `REFRESH_FAILED` |
>
> La panne annoncée ci-dessous — plus aucun rafraîchissement 72 h après la promotion — ne se produit donc
> pas : les deux versions épinglées rafraîchissent par la clé.
>
> **Condition de réouverture** : un résumé de *MORPHEUS Security* publie un « Obtained via » autre que
> `NVD API key refresh` sur une branche épinglée à 13.0.0 ou au-delà, une annotation
> `MORPHEUS_DEPENDENCY_CHECK_REFRESH=REFRESH_FAILED` apparaît, ou `gh secret list` ne retourne plus
> `NVD_API_KEY`. Le texte ci-dessous est l'analyse du 09/09/2026, conservée telle quelle.

Ce risque n'est pas une hypothèse : c'est une panne **datée**, qui se déclenchera environ
**72 heures après la promotion de 1.2.1 sur `main`**, et qu'aucune modification de workflow ne peut
éviter. Il est enregistré ici parce que sa résolution appartient au propriétaire du dépôt, pas au code.

Constats du 09/09/2026, vérifiés sur le dépôt et non recopiés :

- `NVD_API_KEY` **n'est pas configuré**. `gh api repos/FTurleque/morpheus-engine/actions/secrets` ne
  retourne que `SONAR_TOKEN` ; il n'existe ni secret d'organisation (le dépôt appartient à un compte
  utilisateur) ni environnement GitHub.
- Le job planifié quotidien **réussit** — dernière exécution `34311308046`, 09/09/2026 04:31 UTC — mais il
  s'exécute sur `main`. Un déclencheur `schedule` est toujours dispatché sur la branche par défaut : le
  filtre `branches: [main, develop]` ne s'applique qu'à `push` et `pull_request` et n'a aucun effet ici.
- `main` épingle encore Dependency-Check **12.2.2**, dont la mise à jour anonyme fonctionne (`3 788`
  enregistrements, 210 s dans ce run) et dont l'étape de sauvegarde de cache est inconditionnelle. C'est
  **cette** exécution qui alimente le cache que toutes les pull requests de `develop` restaurent via la
  `restore-key` `dependency-check-v12-trusted-`.
- `develop` épingle **13.0.0**, dont la mise à jour anonyme est cassée en amont
  ([issue #8715](https://github.com/dependency-check/DependencyCheck/issues/8715)). Le correctif #8716 est
  mergé mais jalonné **13.0.1**, qui n'est pas publiée : la dernière release est 13.0.0 du 03/08/2026.

Enchaînement une fois 1.2.1 promue : le workflow de `main` devient celui de `develop`, la branche de repli
s'active, elle ne rafraîchit rien, et l'étape de sauvegarde est conditionnée à
`steps.dependency-check-update.outputs.updated == 'true'` — donc plus aucun cache n'est publié. Le dernier
cache `v12` cesse de vieillir sous surveillance et franchit `DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS: 72`.
À partir de là, le job planifié **et chaque pull request** échouent, sur un motif qui n'est pas une
vulnérabilité.

Ce qui a été fait le 09/09/2026 (PR #299), et ce que ça ne fait pas : la panne devient **visible avant de se
produire** — âge du cache, marge restante et voie d'obtention publiés dans le résumé de job, alerte aux deux
tiers du budget, motif d'échec nommé `STALE_DATABASE` et distingué de `VULNERABILITY_THRESHOLD_EXCEEDED`.
**Aucune de ces améliorations ne remplace le secret manquant.** Elles transforment une falaise en pente ; elles
ne rafraîchissent pas la base.

#### Ce que la première mesure a réellement montré (corrigé le 09/09/2026)

Le premier rapport de fraîcheur a annoncé une base à **54 h, 75 % du budget consommé**, alors que le job
planifié avait réussi 15 h plus tôt. Les deux chiffres ne pouvaient pas être vrais ensemble, et c'est le
rapport qui avait tort. **Cette valeur était un artefact de mesure, pas un âge.** Trois constats, tous
vérifiés sur les artefacts et non déduits :

- La sonde lisait `stat -c %Y` sur le fichier retourné par `find … -print -quit`, c'est-à-dire **le premier
  fichier que la marche du système de fichiers croisait** — ni la base, ni le plus récent.
- `actions/cache` restaure via tar, qui **préserve les mtimes**. Même en visant le bon fichier, la date lue
  est celle de la dernière écriture par Dependency-Check, pas celle de la restauration ; et un `update-only`
  incrémental qui ne réécrit rien laisse un mtime ancien sur une base courante.
- Aucun de ces mécanismes ne mesurait la seule chose qui compte : **quand le flux de vulnérabilités a
  réellement été renouvelé**.

Conséquence pour l'appréciation du risque : **un job planifié vert sur `main` n'a jamais prouvé que la base
scannée par `develop` était fraîche.** Il prouvait qu'un rafraîchissement avait réussi sur `main` ; rien, dans
la chaîne, ne datait la base que `develop` restaurait. RT-13 restait donc juste sur le fond — il n'y a pas de
source de rafraîchissement propre à `develop` — mais l'instrumentation censée le surveiller ne le surveillait
pas.

#### Ce qui n'était **pas** un défaut : le repli de clé `v12`

L'audit soupçonnait que la `restore-key` `dependency-check-v12-trusted-` faisait scanner `develop` (13.0.0)
avec une base écrite par 12.2.2, et que la version dans la clé existait précisément pour l'empêcher. **Mesuré,
ce n'est pas le cas** :

- 12.2.2 et 13.0.0 déclarent tous deux `data.version=5.6` dans le `dependencycheck.properties` de
  `dependency-check-core`, et livrent des `data/initialize.sql` et `data/dbStatements.properties`
  **identiques octet pour octet**. Le changement de schéma amont a eu lieu **à 12.2.2**, pas à 13.0.0 — les
  notes de version 13.0.0 le disent explicitement (« highlighting 12.2.2 DB schema change »).
- `DatabaseManager#ensureSchemaVersion` vérifie le schéma **à chaque ouverture de connexion**. Sous
  `-DautoUpdate=false`, que tous les scans MORPHEUS passent, une base d'un autre schéma fait lever
  `DatabaseException` au lieu d'être migrée silencieusement. La compatibilité est donc **déjà fail-closed**,
  au seul endroit qui peut la connaître.

La version du plugin dans la clé de cache mesurait donc la mauvaise variable : trop stricte (elle orphelinait
une base parfaitement lisible à chaque bump de routine) et sans rapport avec ce qu'elle prétendait garantir.
La clé est désormais construite sur la **version de schéma** (`<dependency-check.data.version>` du POM
racine), et il n'y a plus de repli : un changement de schéma produit une clé différente, aucun `hit`, et un
démarrage à froid honnête — qui est de toute façon le seul résultat qu'un repli aurait pu produire, puisque
l'analyseur aurait refusé la base.

#### Correctif du 09/09/2026 — la fraîcheur est un fait, plus une déduction

Un **sentinelle horodaté** (`dependency-check-refresh.sentinel`) est écrit dans le répertoire de données
**uniquement** par un rafraîchissement réussi, avant toute publication de cache, et voyage avec le cache. Il
porte l'epoch du rafraîchissement, la **version de schéma** (divergence = refus) et la **version du plugin**
qui l'a écrit (divergence = signalée, jamais fatale — 12.2.2 et 13.0.0 écrivent le même schéma, donc la
réutilisation croisée est correcte et doit être *visible*, pas interdite). Plus aucune lecture de fraîcheur ne
passe par un mtime.

**Un cache restauré sans sentinelle est traité comme périmé**, jamais comme frais : son âge est *inconnu*, et
la règle tri-state de ce dépôt (ADR-0078, ADR-0093) interdit de convertir `UNKNOWN` en `PASS`.
`DependencyCheckWorkflowContractTest` verrouille les cinq points.

**Effet attendu et assumé de ce correctif :** le premier scan qui suit ne trouvera ni cache à la nouvelle clé,
ni sentinelle dans l'ancien, et **refusera** — `STALE_DATABASE`. Ce n'est pas une régression : c'est la
première mesure honnête. Elle se résorbe dès qu'un événement de confiance écrit une sentinelle, c'est-à-dire
au premier `schedule`/`workflow_dispatch` exécuté sur `main` **après** que ce correctif y soit promu (12.2.2 y
rafraîchit encore anonymement), ou immédiatement si `NVD_API_KEY` est configuré. Ne **pas** relâcher le gate
pour raccourcir cette fenêtre.

Trois sorties possibles, par ordre de préférence :

1. **Configurer `NVD_API_KEY`** (clé gratuite auprès du NIST) — **retenue, voir la clôture ci-dessus**. C'est la seule qui restaure un vrai
   rafraîchissement et la seule qui clôt RT-13.
2. Attendre la publication de **13.0.1** et bumper le pin — remet la mise à jour anonyme en service, mais la
   date de publication n'est pas maîtrisée.
3. Revenir à **12.2.2**. Réévalué le 09/09/2026 avec les deux vérifications qu'exige un tel retour, et
   **écarté sur la première** :
   - *Aucune déficience corrigée par 13.0.0 ?* **Faux.** 13.0.0 contient
     [#8509](https://github.com/dependency-check/DependencyCheck/pull/8509) (« use more conservative CPE 22
     prefix suppression syntax **to avoid false negatives** ») et
     [#8548](https://github.com/dependency-check/DependencyCheck/pull/8548), qui resserrent le *matching* des
     suppressions CPE 2.2. Redescendre en 12.2.2 réintroduirait une classe de **faux négatifs de suppression**
     dans le gate dont la raison d'être est précisément de ne pas en avoir — et ce dépôt exécute
     `failBuildOnUnusedSuppressionRule`, dont la sémantique dépend de ce même *matching*. Échanger une gêne
     d'exploitation contre un angle mort de détection est le mauvais sens de l'échange.
   - *Aucune configuration `d2-security` dépendante de 13.0.0 ?* **Vrai, vérifié** : les douze paramètres
     configurés par les profils `d2-security` et `d2-security-tests` (`failBuildOnCVSS`, `junitFailOnCVSS`,
     `failBuildOnUnusedSuppressionRule`, `skipTestScope`, `prettyPrint`, `format`, `suppressionFiles`,
     `outputDirectory`, `failOnError`, `dataDirectory`, `autoUpdate`, `nvdApiKeyEnvironmentVariable`) existent
     tous dans le `plugin.xml` de 12.2.2. Ce critère-là ne bloquait pas ; le premier bloque.

   S'y ajoute que le retour n'était pas nécessaire au problème de cache : le schéma étant identique
   (voir ci-dessus), `develop` scanne déjà avec le *matching* de 13.0.0 une base rafraîchie par 12.2.2, ce qui
   est la meilleure des deux combinaisons et non un compromis. Enfin
   `D2RepositoryHardeningArchitectureTest#dependencyAndQualityBaselineIsPinned` épingle 13.0.0 textuellement.

Ne **pas** relâcher `DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS` pour faire disparaître le symptôme : une base plus
vieille est une base moins fiable, et le problème est le rafraîchissement, pas le seuil.

### Risques de gouvernance résolus le 27/08/2026

Les anciens risques `RT-10` (promotion stable sans ruleset complet) et `RT-11` (push direct sur `develop`) ne sont plus actifs : le ruleset **Protect main & develop** couvre les deux branches, exige une pull request, la résolution des conversations, les checks exact-head Linux/Windows, Dependency-Check et CodeQL, et interdit suppression/non-fast-forward sans bypass.

Ils restent traçables dans l'historique Git et les issues #154/#166, mais ne doivent plus être présentés comme risques ouverts.

---

## Dette technique / documentaire

| ID | Dette | Domaine | Priorité | Action |
|----|-------|---------|----------|--------|
| DT-10 | Couverture historique globale encore modeste malgré un changed-code gate strict | Qualité | **Moyenne** | Ratchets M21 actifs à `1550 / 385`, couverture `85,0% / 68,0%` agrégée et `64,6% / 56,9%` par module (mesures exact-head dual-plateforme : échelle agrégée le 09/09/2026, plus basse des quatre 85,7263% lignes / 68,5246% branches sous Linux ; échelle par module requalifiée le 22/09/2026, voir la fin de cette ligne) ; #184 est clôturée, conserver la remontée progressive uniquement après nouvelle preuve exact-head reproductible. **Plafond par module requalifié le 15/09/2026** (ADR-0104 §4) : `PER_MODULE_QUALIFIED_*` passe de 62,5013 % / 53,7997 % (08/09/2026, dépassé depuis le 09/09) à **64,5143 % / 56,2964 %**, la plus basse de quatre mesures exact-head de `develop` à `a1fc0a8d` sur les runners GitHub — deux runs par plateforme, le `push` de `develop` et le `pull_request` de la PR de promotion #274, qui construisent le même SHA : Windows 64,5952 % / 64,5987 % lignes et 56,3942 % / 56,4040 % branches, Linux 64,5143 % / 64,5495 % et 56,2964 % / 56,3453 %, `reports=16`, 28 434 lignes et 10 228 branches sur les quatre. Les deux runs Linux diffèrent de 10 lignes et 5 branches, les plateformes de 24 lignes et 11 branches au plus : plus que les deux lignes du 08/09, à citer comme dispersion. **Le ratchet par module est relevé le 16/09/2026 de `0.620 / 0.535` à `0.640 / 0.560`** — le premier mouvement de ratchet depuis le 08/09/2026. Il reste **sous** le plafond requalifié du 15/09, ce qui est sa définition : aucune nouvelle mesure n'a été prise et le plafond n'est pas touché, car relever un ratchet à l'intérieur d'un plafond déjà qualifié est une décision, pas une mesure. **Marge résiduelle : 146 lignes et 30 branches** sur l'échelle par module, contre une dispersion inter-plateforme de 24 lignes et 11 branches — soit 6,1 fois la dispersion en lignes mais **2,8 fois seulement en branches**. *C'est la clé de branche qui est désormais serrée, pas celle de ligne* : un relèvement ultérieur doit traiter les deux clés séparément, rien n'obligeant à les faire bouger ensemble. **L'échelle agrégée n'est pas touchée** : autre population de lignes, autre plafond dans `AggregateCoverageGateTest`, autre marge ; les aligner referait le défaut qu'A-06 a corrigé. La répercussion a suivi la méthode de `rules/meta.md` — changer la valeur, lancer `RepositoryDocumentationCoherenceTest` et `ProductionIntegrityContractTest`, laisser les échecs énumérer les destinations — et non la liste de cette page, périssable par construction. Une mesure locale sous WSL au même SHA a échoué avant le rapport de couverture (`MorpheusQueryApiContractTest#savedViewUpdateUsesCasAndVersionsRemainReadable`, « updatedAt must not precede createdAt », très probablement un recul d'horloge de WSL) et n'entre pas dans la qualification. **Le 22/09/2026, la seule clé ligne par module est relevée de `0.640` à `0.642`**, dans le même plafond et sans mesure nouvelle : **marge résiduelle 89 lignes et 30 branches**, soit 3,7 fois la dispersion inter-plateforme en lignes et toujours 2,8 fois en branches. La clé branche ne bouge pas, précisément parce que c'est la clé serrée ; l'échelle agrégée non plus. La population par module a été relue le même jour sur les artefacts `m21-integrity-Linux` des runs `35756505624` (`push`) et `35756512657` (`pull_request`) de `develop` à `e8e0f0ca` : 28 352 lignes et 10 212 branches, 82 lignes de moins que la mesure du 15/09, ce qui ne déplace aucune des deux marges d'une unité ; la mesure réelle de ces runs (Linux 64,9302 % lignes, 57,2366 % branches au plus bas) reste au-dessus du plafond, qui n'est pas requalifié ici. **Plafond par module requalifié le 22/09/2026** : le plafond du 15/09 (64,5143 % / 56,2964 %) était **dépassé depuis le 22/09/2026** sur les deux clés, et bloquait les deux ratchets bien sous ce que le code couvre. Les quatre preuves `m21-per-module-coverage-summary.txt` des mêmes runs `35756505624` et `35756512657` à `e8e0f0ca` (première ligne `coverageScope=per-module`, `reports=16`, 28 352 lignes et 10 212 branches sur les quatre) donnent Windows 64,9831 % / 64,9831 % lignes et 57,3051 % / 57,3051 % branches, Linux 64,9302 % / 64,9302 % et 57,2464 % / 57,2366 % : `PER_MODULE_QUALIFIED_*` passe à **64,9302 % / 57,2366 %**, la plus basse par clé, les deux minima venant de Linux. Dispersion : 0 ligne et 1 branche entre les deux runs Linux, aucune entre les deux runs Windows, 15 lignes et 7 branches au plus entre plateformes — dans la dispersion du 15/09 (24 lignes, 11 branches), qui reste celle contre laquelle les marges se dimensionnent. Entre `e8e0f0ca` et la tête de `develop` seules des sources de `morpheus-architecture-tests` ont changé, module que cette échelle exclut. **Les deux ratchets par module sont relevés séparément** dans ce plafond, chacun au plus haut millième qui garde au moins trois fois la dispersion : ligne `0.642` → `0.646`, **marge 93 lignes** (3,9 fois) ; branche `0.560` → `0.569`, **marge 34 branches** (3,1 fois) — le premier mouvement de la clé branche depuis le 16/09/2026. Un millième de plus tomberait sous trois fois (65 lignes, 24 branches). L'échelle agrégée n'est pas touchée |
| DT-11 | Nouveau workflow de release attestée pas encore qualifié par une vraie release publiée | Release | **Moyenne** | Valider l'enchaînement tag -> Linux/Windows -> attestations -> assets -> GitHub Release lors de la prochaine vraie release `v1.2.1+` ; suivi #185 |

Les anciennes dettes `DT-06` (protection `main`) et `DT-09` (protection `develop`) sont résolues et retirées du tableau actif.

Il ne reste qu'**une dette active qui attend un événement extérieur** : `DT-11`, qui ne se ferme qu'à la
première vraie release publiée (#185). `DT-10` reste au tableau parce que son action est encore la nôtre — la
remontée progressive des ratchets, dans le plafond qualifié de chaque échelle. `DT-13` en sort le 22/09/2026 :
voir la requalification ci-dessous.

### Requalification du 22/09/2026 — `DT-13` est une position, pas une dette

`ci.yml` s'exécute deux fois sur le même arbre quand la PR de promotion `develop -> main` est ouverte, et
**cela restera vrai**. Ce n'est pas une dette en attente de correctif : les trois faits du 15/09/2026
ci-dessous montrent qu'aucune réduction sûre n'existe, et le coût est délibérément accepté parce que la
lane Linux de cette PR est **le seul endroit** où tourne le gate de couverture des lignes modifiées
`origin/main...HEAD`.

Mesure du 22/09/2026 (`gh run list --workflow ci.yml`, exécutions créées depuis le 23/08/2026, puis
`gh api .../actions/runs/<id>/jobs` pour la lane Windows) :

| Grandeur | 10/09/2026 | 22/09/2026 |
|---|---|---|
| Exécutions de `ci.yml` sur 30 jours | 776 | **634** |
| dont `pull_request` de la PR de promotion (tête `develop`) | 39 (5,0 %) | **61 (9,6 %)** — les 61 SHA ont aussi leur run `push` sur `develop` |
| Depuis l'ouverture de #274 (06/09/2026) | 21 contre 23 pushes, 17,6 % des exécutions | **54 contre 61 pushes, 20,4 % des 265 exécutions** |
| Cadence des pushes sur `develop` | — | **5,8 / jour** du 23/08 au 09/09, **3,2 / jour** du 10/09 au 22/09 |
| Lane Windows (`exact-head (windows-latest)`, jobs réussis) | ≈ 17 min, 12-29 sur 53 | **médiane 17,1 min**, 12,4-40,0 (p10-p90 14,0-23,5) sur 100 runs de `develop` depuis le 06/09 |

La part sur 30 jours a presque doublé, mais **pas parce que la cadence a monté** : la fenêtre du 10/09 ne
couvrait que quatre jours de PR permanente, celle-ci en couvre seize. Depuis l'ouverture de #274 la
duplication reste quasi 1:1 avec les pushes, et le nombre de runs dupliqués par jour a **baissé** (≈ 5,3 le
10/09, ≈ 3,4 aujourd'hui) avec la cadence de `develop`. La condition de réouverture de la ligne active —
« si la cadence de `develop` augmente nettement » — n'est pas remplie ; la queue de la lane Windows s'est
allongée (40 min au plus), sa médiane non.

**Condition de réouverture** : la cadence de `develop` augmente au point que la part dupliquée dépasse
**30 %** des exécutions de `ci.yml` sur 30 jours, ou plus de **7 exécutions dupliquées par jour** en
moyenne sur la même fenêtre (le double d'aujourd'hui) ; la PR de promotion cesse d'être permanente ; ou
GitHub documente lequel de deux checks homonymes attachés au même SHA fait foi. Le seuil en volume est
ajouté au seuil en part parce que, la duplication étant 1:1 avec les pushes, une hausse de cadence fait
monter le coût sans faire monter la part.

#### Analyse du 10/09/2026 et du 15/09/2026 (inchangée)

Texte de la ligne `DT-13` du tableau actif au moment de sa requalification, conservé tel quel : l'audit
du 10/09/2026 et son suivi y renvoient.

> **Coût accepté, mesuré le 10/09/2026.** L'événement `push` sur `develop` et l'événement `pull_request` de la PR permanente construisent le même SHA : `ci.yml` fait `checkout` de `github.event.pull_request.head.sha`, donc la PR teste la tête exacte, pas `refs/pull/<n>/merge`. Sur 30 jours : 776 exécutions dont 39 imputables à la PR de promotion (**5,0 %**) ; depuis l'ouverture de #274 la duplication est quasi 1:1 (21 contre 23 pushes, **17,6 %** des exécutions), soit ≈ 434 min de runner en 4 jours, dominées par la lane Windows (≈ 17 min, dispersion 12-29 min sur 53 exécutions réussies échantillonnées le 10/09/2026 : la variance ordinaire de cette lane est plus large que le chiffre lui-même, ne pas citer une valeur ponctuelle). **Rien n'est modifié** : le dépôt est public (pas d'argument de coût), l'attente de file mesurée est nulle (médiane et p90 à 0 s sur les 100 dernières exécutions, donc aucun délai de restitution), et surtout la lane Linux de cette PR est **le seul endroit** où tourne le gate de changed-line coverage `origin/main...HEAD` (`scripts/check-diff-coverage.py`, `>= 80 %` / `>= 70 %`) : la supprimer retirerait un gate de promotion. Filtrer `pull_request` sur `branches: [develop]` est exclu — les checks requis `exact-head (*)` resteraient *Pending* et bloqueraient la PR de promotion définitivement (documentation GitHub : un workflow écarté par filtre de branche laisse ses checks en attente). À rouvrir seulement si la cadence de `develop` augmente nettement. **Réduction étudiée le 15/09/2026, non livrée — la dette reste acceptée telle quelle.** L'idée : écarter la seule jambe Windows sur la PR de promotion, puisque le `push` sur `develop` l'exécute déjà sur ce SHA (≈ 17 des 21 minutes dupliquées), en gardant la lane Linux qui porte le gate `origin/main...HEAD`. Trois faits l'arrêtent. **(1) Le mécanisme proposé n'existe pas** : `jobs.<job_id>.if` n'a accès qu'aux contextes `github`, `needs`, `vars` et `inputs`, pas à `matrix` (documentation GitHub, *Contexts reference*, table *Context availability*) — un conditionnel de job ne peut pas viser une jambe de matrice. Écarter la jambe par une matrice dynamique (`strategy`, qui voit `github`) ne crée pas le check `exact-head (windows-latest)` : check requis absent, PR *Pending*, exactement le blocage à éviter. Seul un `if` **d'étape** (qui voit `matrix`) fonctionne : la jambe tourne une à deux minutes sans rien vérifier et rapporte *Success*. **(2) La condition `base_ref == 'main'` est trop large** : elle vise aussi une PR de hotfix ouverte directement sur `main` (cas de #303), dont la tête n'a jamais tourné sous Windows sur `develop` ; la condition sûre exige `head_ref == 'develop'` depuis ce même dépôt. **(3) L'observation exigée n'est pas possible avant livraison** : sur un `pull_request`, le workflow exécuté est celui de la référence de merge, donc le conditionnel ne s'observe sur une PR `develop -> main` qu'une fois déjà fusionné dans `develop` ; et le point réellement incertain — quel check run fait foi quand le run `push` (Windows en échec) et le run `pull_request` (Windows sauté, *Success*) attachent au **même SHA** deux checks du même nom — ne se lit dans aucune documentation. Tant qu'il n'est pas observé, le saut pourrait faire passer une promotion dont la lane Windows a échoué sur `develop`

### Requalifications du 16/09/2026 — `DT-04`, `DT-05`, `DT-12` sont des positions, pas des dettes

Ces trois lignes décrivaient des **positions tenues**. Les garder dans un tableau de dette suggérait qu'on
avait l'intention d'en sortir, ce qui était faux pour les trois. Chacune part là où elle appartient, avec
l'événement qui la rouvrira — une requalification qui ne nomme pas cet événement est une disparition, et le
prochain audit recréerait la ligne sous un autre numéro.

- **`DT-04` — SQLite unique backend persistant → [ADR-0018 §15](../../adr/0018-sqlite-initial-persistent-store.md).**
  Son action se lisait « n'engager un backend alternatif qu'après besoin et ADR dédiés », c'est-à-dire *ne rien
  faire jusqu'à nouvel ordre*. Les critères de réouverture n'étaient pas à écrire : ADR-0018 §15 les porte déjà,
  au nombre de cinq (volume ou latence hors objectifs, concurrence multi-utilisateur prioritaire, traversées
  complexes dominantes, distribution du driver problématique, mode serveur central devenu déploiement principal).
  La ligne n'a jamais décrit autre chose que cette section.
- **`DT-05` — distribution macOS absente → [arc42 §10.4](../arc42/10-exigences-qualite.md).**
  La position y est déjà écrite, documentée **et outillée** : macOS n'est pas déclaré supporté par analogie avec
  Linux, une lane `macos-smoke` **advisory** (`runs-on: macos-latest`, `continue-on-error: true`) exécute le
  reactor complet dans `nightly.yml` sur une cadence bornée, et §10.4 conclut « **observation n'est pas
  qualification** ». Vérifié le 16/09/2026 : cette lane ne publie qu'un artefact de preuve
  (`macos-smoke-evidence` — rapports surefire et profil de charge), **aucun artefact de distribution**, et la
  liste des plateformes qualifiées reste `Windows` / `Linux`. **Événement de réouverture** : une décision
  produit de supporter macOS, qui appelle alors packaging et qualification dédiés — pas la simple accumulation
  de lanes smoke vertes, que §10.4 refuse explicitement comme preuve de support.
- **`DT-12` — identités remote historiques à trois champs → [ADR-0094](../../adr/0094-optional-team-remote-server-mode.md).**
  Le tableau disait lui-même que retirer le format « reste une évolution explicitement incompatible, pas un
  patch 1.2.1 » : c'est la définition d'une position. Elle est verrouillée par test, visible par
  `server identity list` et migrable par `server identity migrate-legacy`. **Événement de réouverture** : une
  version majeure qui assume la rupture, ou la constatation qu'une identité historique non expirante a servi à
  un accès non légitime — auquel cas c'est un incident, pas une dette.

### Sorties du registre technique du 16/09/2026 — `DT-07` et `DT-08` vivent dans #154

```text
DT-07  Quality Gate SonarCloud potentiellement moins strict que le gate repository
DT-08  État des alertes Dependabot / Secret Scanning non vérifiable par le connecteur
```

Ces deux lignes ne décrivaient **rien qui vive dans le dépôt**. Ce sont des réglages de comptes externes : leur
action se lisait « vérifier/activer les réglages administrateur », aucun commit ne peut les fermer, aucun test
ne peut les surveiller, et elles portaient déjà **le même numéro d'issue**, ce qui disait bien qu'elles étaient
suivies ailleurs. Le tableau porte le mot « dette **technique / documentaire** » ; une case de configuration
chez SonarCloud n'est ni l'un ni l'autre.

**Ce n'est pas « c'est réglé » — c'est « c'est suivi ailleurs », et la nuance est tout le contenu de ce
paragraphe.** Ce que le dépôt garantit **indépendamment** de tout verdict externe, et qui ne bouge pas avec
ces deux lignes : le gate de couverture des lignes modifiées (`>= 80 %` changed-line, `>= 70 %`
changed-branch, `scripts/check-diff-coverage.py`), `dependabot.yml` versionné sur les écosystèmes `maven` et
`github-actions`, OWASP Dependency-Check avec `failBuildOnCVSS = 7.0`, et CodeQL épinglé par SHA exécutant les
requêtes `security-extended`. **Événement de réouverture** : une divergence constatée entre un verdict externe
et un gate du dépôt — un Quality Gate qui passe sur un changement que le gate repository refuse, ou l'inverse.
C'est ce constat qui ferait revenir le sujet dans ce tableau, pas l'état d'une case de configuration.

`DT-03` est **éliminée le 16/09/2026 et retirée du tableau actif**. `docs/architecture/arc42/10-exigences-qualite.md` §10.3 énonçait correctement la doctrine (`measured budget > undocumented expectation`) mais **ne nommait aucune classe de gate — zéro**, alors qu'il en existe cinq : `M19PerformanceGate`, `M19QueryPerformanceGate`, `M19CompositionPerformanceGate`, `M19TraceabilityPerformanceGate`, `M19FullPublishPerformanceGate`. Un lecteur de la documentation d'architecture ne pouvait pas aller de §10.3 à la preuve qui fait autorité, ce qui mettait §10.3 en contradiction avec sa voisine §10.5, laquelle exige que chaque exigence de qualité pointe vers une preuve exécutable. §10.3 gagne donc les cinq noms, le contrat de fixture partagé (`M19LargeFixtureContractTest`, outillé par `M19LargeFixtureSupport`), l'emplacement des jeux déterministes (`experiments/m0/fixtures/`), la déclaration gelée des budgets (`docs/roadmap/M19_PERFORMANCE_BUDGETS.md`, explicitement signalée comme preuve datée M19-S1 et non comme état courant) et ADR-0085. **Aucun seuil n'est recopié** : les budgets sont portés par les gates sous forme de constantes, et la page dit pourquoi elle ne les reproduit pas — une valeur recopiée deviendrait périmée sans que rien ne le signale. C'est la règle de `rules/meta.md`, et c'est aussi ce que §10.3 affirmait déjà d'elle-même ; l'ajout ne la contredit pas.

**Condition de réouverture** : un nouveau budget de performance qui n'est pas atteignable depuis §10.3 — un gate ajouté sans être nommé, ou un budget déplacé hors des constantes de son gate sans que le renvoi suive.

`DT-14` est **tranchée le 11/09/2026 et retirée du tableau actif** : les quatre workflows historiques `m9-validation.yml`, `m10-preflight.yml`, `m11-preflight.yml` et `m12-preflight.yml` sont supprimés, ce qui clôt aussi F-13 (audit du 09/09/2026). Les garder exigeait de nommer une preuve qu'ils portaient encore, et aucune ne tient : ils lançaient `mvnw clean test`, qui ne construit pas le JAR que `ProviderPluginPlatformContractTest` exige avant les tests d'architecture, donc ils échouaient sur l'arbre courant ; aucun tag M9–M12 ne permet de les rejouer sur leur état d'origine ; `ci.yml` exécute `clean verify` exact-head sous Linux et Windows. Le commit `b1edfffc` (24/07/2026) les gardait comme validation manuelle optionnelle ; depuis que les contrats M22 chargent ce JAR, ils ne peuvent plus remplir ce rôle. `D2RepositoryHardeningArchitectureTest#historicalPreflightsAvoidDeprecatedSetupJavaV4` est **remplacé, pas supprimé**, par `everyWorkflowAvoidsDeprecatedActionGenerationsAndTheHistoricalPreflightsStayRemoved`, plus strict : l'interdit d'un `checkout`/`setup-java` antérieur à Node 24 couvre désormais **tous** les workflows du répertoire au lieu d'une liste de trois noms, et les quatre noms historiques sont refusés. `docs/roadmap/DEPLOYMENT.md` §9 renvoie désormais à `ci.yml`.

`DT-15` est **livrée le 15/09/2026 et retirée du tableau actif** : le groupe 2 d'ADR-0103, la frontière transport/JSON des routeurs `*HttpRoutes`, est exprimé dans `HttpRoutesTransportBoundaryArchitectureTest`, classe distincte de `HttpRoutesFamilyArchitectureTest` parce que ses règles ne sont pas famille-larges. **Le découpage livré est 8/5/4, pas le 8/9 de l'amendement.** L'établissement littéral par littéral sur les dix-sept routeurs a montré que les neuf routeurs à corps forment deux groupes : cinq (`Changes`, `Portfolio`, `ProjectRoot`, `ProjectSync`, `Requirements`) lisent leur corps par `MorpheusHttpRequestDecoder` et ne touchent jamais Jackson ; quatre (`Policy`, `PolicyManagement`, `Query`, `Reasoning`) — les quatre qu'aucun test ne couvrait avant le groupe 1 — construisent leur propre `JsonMapper`, sérialisent par `CanonicalJsonSerializer` et lisent leur corps par `HttpRequestBodyReader`. Leurs protections sont celles du décodeur (même plafond de corps, même délai de lecture, mêmes `FAIL_ON_UNKNOWN_PROPERTIES` et `FAIL_ON_TRAILING_TOKENS`, même 415) : ce qu'ils dupliquent est une configuration, pas une protection manquante. D'où trois règles, chaque groupe listé par nom : les huit routeurs sans corps ne dépendent ni de `com.sun.net.httpserver..`, ni de `tools.jackson..`, ni du décodeur ; les cinq routeurs à décodeur ni de `tools.jackson..`, ni de `CanonicalJsonSerializer` ; et tout `JsonMapper.builder()` de `morpheus-api` active les deux features — règle **textuelle**, la configuration d'un builder n'étant pas une dépendance. Une quatrième méthode refuse un routeur classé dans aucun groupe, dans deux, ou disparu. `MorpheusHttpServer` ne figure dans aucun interdit : quatre routeurs n'en lisent que `API_PREFIX`, constante inlinée invisible à ArchUnit, deux en décodent des records imbriqués, visibles — ce n'est pas la frontière. Le texte est conservé là où le comptage de constantes l'impose (Jackson 3.2.2 : 110 constantes publiques inlinables dans `jackson-core`, 44 dans `jackson-databind` ; `CanonicalJsonSerializer.DEFAULT_MAX_UTF8_BYTES`) et omis là où il n'en trouve aucune (`jdk.httpserver`, `MorpheusHttpRequestDecoder`). Chaque règle a été cassée le 15/09/2026, résultat consigné dans son Javadoc : les deux violations par constante inlinée ont passé la règle ArchUnit et n'ont été attrapées que par le texte, sans qu'aucune suite par routeur ne les voie ; un champ `Headers` dans un routeur sans corps, un routeur non classé et un flag retiré d'un mapper n'étaient attrapés par aucune suite existante. La classification et la règle de configuration portent en plus une preuve résidente. Aucune assertion textuelle existante n'est retirée ; six méthodes `@Test` s'ajoutent. Faire passer les quatre routeurs à mapper propre par le décodeur partagé supprimerait quatre copies de configuration mais changerait le comportement de quatre surfaces d'écriture : ce n'est pas ce lot, et c'est une dette à nommer (DT-16) seulement si on le décide.

`DT-16` est **ouverte et close le 15/09/2026, dans le même geste** : les quatre routeurs d'extension (`Policy`, `PolicyManagement`, `Query`, `Reasoning`) lisent désormais leur requête par le `MorpheusHttpRequestDecoder` du serveur. **Ce n'est pas un correctif de sécurité** : leurs protections étaient déjà celles du décodeur, aucune garde ne manquait. La dette était structurelle — ils s'enregistraient comme contextes HTTP indépendants hors de la construction du serveur et portaient chacun une copie de la frontière de requête (un `JsonMapper`, `readJson`/`readBody`/`requireEmptyBody`, un `HttpFailure` privé), soit quatre configurations que rien ne gardait alignées sur celle du décodeur. **Voie retenue : injection du décodeur au `register(...)`**, pas branchement dans la construction du serveur : les contextes restent indépendants, donc la précédence de routage ne bouge pas, et le décodeur injecté est celui du serveur (même exécuteur, mêmes bornes). Le décodeur gagne `requireEmptyBody` et lit désormais par `HttpRequestBodyReader`, dont la variante `read(HttpExchange)` et l'exécuteur de processus propres aux routes d'extension disparaissent. **Le risque réel était le rendu des échecs**, pas la lecture : les routeurs rendaient leur `HttpFailure`, le décodeur lève `ApiFailure`, qui n'est ni une `IllegalArgumentException` ni une `IllegalStateException`. `ExtensionRoutesRequestBoundaryParityTest` a donc été écrit **avant** la migration, sur le code d'origine : 41 cas sur les quatre contextes, statut, `Content-Type`, `Allow` et enveloppe comparés octet pour octet (corps absent, `Content-Type` absent, faux ou de charset non UTF-8, JSON invalide, champ inconnu, jeton traînant, corps au-delà du plafond, méthode refusée, corps non vide attendu vide). Une route par commit, parité rejouée à chacun, **identique avant et après**. Sa valeur est prouvée : une première migration de `Reasoning` sans `catch (ApiFailure)` répondait `500` sur ses onze cas, et le test les a tous nommés. **Elle était incomplète au premier push, et c'est le gate de couverture des lignes modifiées qui l'a montré** (68,52 % < 80 % sur la lane Linux) : le `HttpFailure` privé portait aussi les refus de routage (404), de méthode (405 et son `Allow`) et de paramètres de requête (400), eux aussi passés à `ApiFailure` sans être épinglés. Trente cas de plus, capturés de la même manière sur le code d'origine, portent la parité à **71 cas**, identique après migration ; couverture des lignes modifiées 104/108 et des branches 26/28 sur le seul rapport de `morpheus-api`. Une cinquième garde textuelle épinglait l'appel direct à `TimedBoundedInputReader.read(` dans le décodeur (`LocalHttpRequestDecoderArchitectureTest`) ; elle suit la même chaîne que D2. **Ce qui reste délibérément dupliqué** : ces quatre routeurs écrivent eux-mêmes leur enveloppe de réponse par `CanonicalJsonSerializer` (exports bruts, en-tête `Allow`, codes métier propres). Le brief envisageait d'étendre aux neuf routeurs à corps l'interdit `CanonicalJsonSerializer` ; c'est faux tant que l'écriture des réponses n'est pas migrée, ce qui est une autre dette, non nommée faute de décision. D'où, dans `HttpRoutesTransportBoundaryArchitectureTest`, un groupe « par le décodeur, écrivant leur propre réponse » : il **doit** dépendre du décodeur et ne dépend ni de `tools.jackson..` ni d'`HttpRequestBodyReader` (règle et texte), le groupe « à mapper propre » est **supprimé plutôt que laissé vide**, la classification refusant toujours un routeur inclassé, et l'interdit d'`HttpRequestBodyReader` s'étend aux cinq routeurs à décodeur. Les deux gardes textuelles portant sur quatre noms passent à la famille entière : aucun `*HttpRoutes` ne lit `getRequestBody()`, n'utilise `HttpRequestBodyReader`, ne lit `getRequestHeaders()` ni ne mentionne `JsonMediaType` (`RepositoryDocumentationCoherenceTest`, `D2RepositoryHardeningArchitectureTest`), et D2 suit la chaîne décodeur → `HttpRequestBodyReader.read(` → `TimedBoundedInputReader.read(` maillon par maillon. Contre-épreuves sur l'arbre réel : un `JsonMapper` dans `MorpheusQueryHttpRoutes` n'est attrapé que par la règle du nouveau groupe (`everyApiJsonMapperIsStrict…` passe, le mapper étant strict) ; une lecture par `HttpRequestBodyReader` est attrapée par la règle et par la garde textuelle. `everyApiJsonMapperIsStrict…` ne trouve plus qu'un site, le décodeur, et garde sa preuve résidente. Les quatre routeurs passent de 1 018 à 863 lignes ; **sept méthodes `@Test` s'ajoutent, aucune n'est retirée**. **Le reste laissé non nommé est tranché le 16/09/2026 : il devient `DT-17`.** La phrase ci-dessus le disait « une autre dette, non nommée faute de décision » ; laisser ce mandat ouvert une révision de plus reproduisait le défaut d'ADR-0103, resté cinq jours sans verdict parce que personne ne savait s'il fallait le faire. Le verdict est une dette, pas une position — et la justification que ce paragraphe avançait pour en douter (« des enveloppes légitimement différentes ») ne survit pas à la lecture des quatre sources : trois des quatre méthodes d'envoi sont identiques octet pour octet au writer partagé. Le détail et les conditions d'acceptation étaient dans la ligne `DT-17` du tableau actif ; elle en est sortie le jour même, close, et le paragraphe ci-dessous la remplace.

`DT-17` est **ouverte et close le 16/09/2026**, comme `DT-16` l'avait été la veille : les quatre routeurs d'extension (`Policy`, `PolicyManagement`, `Query`, `Reasoning`) écrivent désormais leur réponse par le `MorpheusHttpResponseWriter` du serveur. **Il n'y avait ni maison à construire ni type neutre à inventer** : le writer existait, les quatre routeurs partagent son package, et les trois records d'enveloppe étaient déjà `public` sur `MorpheusHttpServer`. Il leur manquait une référence, pas un déménagement. **Voie retenue : injection du writer au `register(...)`**, exactement la chaîne que DT-16 avait construite pour le décodeur — les contextes restent indépendants, la précédence de routage ne bouge pas. Le writer gagne la surcharge `sendRaw(exchange, status, contentType, bytes)` dont `send` devient la spécialisation JSON : ce n'est pas une généralisation spéculative, `MorpheusQueryHttpRoutes` écrivait déjà cette paire, et l'export brut porte les mêmes `Cache-Control` et `X-Content-Type-Options` que toute réponse JSON.

**Le piège annoncé n'était pas celui-là.** Le brief désignait comme risque principal le passage brut de `failure.getMessage()` sur la branche `catch (ApiFailure)`, là où les records publics refusent le null. Vérification faite, ce chemin ne peut pas produire de null, et c'est une propriété de type, pas une absence de contre-exemple : `ApiFailure` est `final`, chacun de ses constructeurs fait `Objects.requireNonNull` sur le message et le code et `Map.copyOf(Objects.requireNonNull(...))` sur les détails, et son constructeur à quatre arguments **n'a aucun appelant** — `details` vaut donc toujours `Map.of()` sur ces quatre routeurs. Le durcissement réel porte sur `ApiSuccess.data` : les records `Response` de `Policy` et de `PolicyManagement` ne valident rien et `Reasoning` passe un résultat de service tel quel, là où `Query` refusait déjà le null par son `json(...)`. **Décision écrite** : le contrat strict est adopté: une enveloppe de succès dont la charge est nulle est une dégradation silencieuse, que `code-style.md` refuse au profit de l'échec explicite. `ApiEnvelopeNullContractTest` épingle les deux moitiés (refus du null, copie défensive des détails).

**La découverte du lot est ailleurs, et c'est une règle qui enforçait le contraire de son intention.** `HttpRoutesFamilyArchitectureTest#noRouterWritesTheResponseItself` interdisait à **tout** routeur de dépendre de `MorpheusHttpResponseWriter`, au motif qu'« un routeur retourne un `MorpheusHttpRouteResponse`, l'encodage appartient au serveur ». Vrai des treize routeurs que le serveur porte en champ. Faux des quatre autres, qui ont toujours écrit leur réponse eux-mêmes : le seul moyen pour eux de satisfaire un interdit sur le writer partagé était d'en garder une copie privée. La règle nommait la bonne intention et **entretenait la duplication qu'elle était censée empêcher**. Elle interdit désormais **l'appel `sendResponseHeaders`** — ce que son nom annonçait depuis le début, et qui est vrai des dix-sept : déléguer au writer du serveur n'est pas écrire sa réponse. Le writer devient une **propriété de groupe** : exigé des quatre qui enregistrent leur contexte, interdit des deux groupes que le serveur porte, les treize gardant en plus leur assertion textuelle par classe. Rien de ce qui était vérifié ne cesse de l'être.

**Parité avant migration, comme DT-16.** `ExtensionRoutesRequestBoundaryParityTest` épingle la requête mais ne dit rien de la réponse : il n'asserte que `Content-Type`, jamais `Cache-Control` ni `X-Content-Type-Options`, et aucun succès. `ExtensionRoutesResponseWritingParityTest` comble l'écart, capturé sur le code d'origine — statut, les trois en-têtes, `Allow` et l'enveloppe complète sur les quatre contextes, succès et échecs, dont le `201` de `policy-packs` et de `saved-views`, l'export CSV brut de `Query` (seul corps qui n'est pas une enveloppe) et les codes métier propres. Une route par commit, parité rejouée à chacun, **identique avant et après**. Résultat : plus aucun `*HttpRoutes` ne contient `sendResponseHeaders` ni `CanonicalJsonSerializer`, les **douze** records d'enveloppe privés et les **quatre** méthodes d'envoi ont disparu, et l'interdit de `CanonicalJsonSerializer` couvre enfin les neuf routeurs à corps — ce que le brief de clôture de DT-16 affirmait à tort comme déjà vrai. Le groupe est **renommé** `ROUTERS_REGISTERING_THEIR_OWN_CONTEXT`, pas laissé vide : ses deux noms précédents décrivaient chacun une duplication depuis supprimée, et il est désormais nommé pour ce qui reste vrai de lui. **Aucune méthode `@Test` retirée** — une seule renommée — et huit s'ajoutent dans `morpheus-api`. Les deux règles ont été cassées pour preuve : `sendResponseHeaders` injecté dans `MorpheusChangesHttpRoutes` fait échouer la règle de famille et elle seule ; un champ `MorpheusHttpResponseWriter` injecté dans `MorpheusRootHttpRoutes` fait échouer la règle de groupe et l'assertion par classe, la famille passant — un champ n'est pas un appel, les deux règles sont complémentaires.

**Condition de réouverture** : qu'un routeur écrive à nouveau une réponse hors du writer partagé — redéclaration d'un record d'enveloppe en `private`, sérialiseur propre, ou appel direct à `sendResponseHeaders`. Les trois sont désormais refusés par une règle **et** par un texte ; une réouverture voudrait donc dire qu'une de ces gardes a été retirée, ce que `governance.md` interdit sans remplacement équivalent ou plus strict.

`DT-01` est **éliminée le 16/09/2026 et retirée du tableau actif**. Son périmètre réel n'a jamais été les « ~40 documents non à jour » que la rév. 4 de l'audit comptait, ni les cinq documents du brief de clôture : c'est **douze**, répartis en deux classes que l'action de la dette distinguait déjà (« les qualifier comme historiques **ou** les réconcilier dans des PR dédiées sans falsifier les preuves passées »). **Quatre documents vivants portaient un statut faux et sont corrigés.** `docs/release/RELEASE_NOTES_1.1.0.md` annonçait « CANDIDATE — NON PUBLIÉE » et `docs/user/UPGRADE_1_1.md` « GUIDE CANDIDAT — 1.1.0 NON ENCORE PUBLIÉE », alors que le tag `v1.1.0` existe et que `VALIDATION_R2.md` conclut « PASS — RELEASE 1.1.0 PUBLIÉE ET VÉRIFIÉE » (PR #114 mergée vers `main`, issue #113 close, merge `31506029`, exact head qualifié `31212087`, 603 tests PASS sous Windows et sous Linux/WSL, GitHub Release stable à 8 assets). **Ce n'était pas de la dette documentaire mais une surface utilisateur qui mentait sur l'état du produit** — une release publiée depuis deux mois s'y présentait comme bloquée. Les notes portent désormais `PUBLISHED / STABLE / SUPERSEDED BY 1.2.0` et un bloc « Publication vérifiée » sourcé ligne à ligne sur R2 ; le guide porte `ACTIVE — MORPHEUS 1.1.0 PUBLIÉ`, ne nomme plus `v1.1.0` « la candidate » et renvoie vers `UPGRADE_1_2.md` pour la suite. La convention appliquée est celle du fichier frère `RELEASE_NOTES_1.2.0.md` (`PUBLISHED / STABLE / LATEST`) : pour des notes de version le statut est **vivant**, pas un horodatage, et celles de 1.1.0 n'avaient simplement jamais été mises à jour au moment de la publication. `docs/user/PROVIDER_PLUGINS.md` et `docs/developer/PROVIDER_SDK.md` annonçaient « M22 candidate » alors que M22 est livré, son gate actif et `VALIDATION_M22` PASS dual-plateforme ; ils reprennent mot pour mot la formule déjà en service dans `docs/developer/OPERABILITY.md`, qui sépare le comportement courant de la preuve historique du jalon.

**Huit documents C0 sont qualifiés, pas réécrits — et le brief n'en voyait qu'un.** `docs/ECOSYSTEME.md` était présenté comme un cas isolé ; le relevé de toutes les lignes `Statut` hors `docs/validation/`, `docs/research/` et `docs/audits/` montre qu'il appartient à une classe homogène de **huit** fichiers tous datés du 22 juillet 2026 et tous en « Proposition C0 — à valider » : `ECOSYSTEME.md`, `architecture/overview.md`, `domain/MODEL.md`, `domain/CHANGE_LIFECYCLE.md`, `contracts/SPECIFICATION_PROVIDER.md`, `contracts/SPECIFICATION_KNOWLEDGE_STORE.md`, `product/MVP.md`, `product/USE_CASES.md`. N'en corriger qu'un aurait coupé la classe en deux sans raison énonçable. **Aucune ligne `Statut` n'est touchée** : chacun reçoit une notice historique posée sous sa date, qui dit que le statut décrit le cadrage au moment où il a été établi, que **C0 à M14 sont validés et intégrés**, et que le vocabulaire prospectif du document ne doit pas être lu comme une question encore ouverte. Le gabarit n'est pas inventé — c'est celui que `docs/product/CAHIER_DES_CHARGES.md` applique déjà à son propre vocabulaire prospectif, et que `docs/validation/VALIDATION_D2.md` applique à ses mesures. Le renvoi d'état courant va vers `governance/ROADMAP.md` et `governance/DOCUMENTATION_STATUS.md`, à la profondeur relative propre à chaque page.

**Ce qui est délibérément laissé.** Aucun fichier de `docs/validation/` ni de `docs/research/` n'est modifié, et aucun `docs/roadmap/*_EXECUTION.md` : leurs statuts — « PR #79 prête à intégrer », « intégration S6 en attente du merge #42 », « NON MERGÉ » — sont **vrais à la date où ils ont été écrits**, et les réécrire serait exactement la falsification que l'action de DT-01 interdit. Le défaut de la rév. 4 de l'audit était de présupposer que « pas à jour » veut dire « devrait l'être ». Un document de validation qui prête à confusion reçoit une notice, jamais une réécriture de sa ligne `Statut`.

**Condition de réouverture** : un document **vivant** — une surface utilisateur, un guide, une page `docs/developer/` — dont le statut a résolu sans être mis à jour, c'est-à-dire qui continue d'annoncer comme attendu, candidat ou bloqué un état que le dépôt a depuis atteint. Un statut daté qui reste daté n'en est pas un ; un jalon livré dont la page utilisateur dit encore « candidate », si. Rien ne surveille automatiquement cette classe : `everyRelativeMarkdownLinkResolvesFromThePageThatHoldsIt` vérifie les liens, pas la véracité d'un statut.

---

## Constats de l'audit du 09/09/2026 restés sans verdict : F-11 et F-12 (tranchés le 11/09/2026)

Ces deux constats ont traversé quatre révisions d'audit sans être ré-examinés. Ils sont mesurés et clos ici ; leur voisin F-13 est clos avec DT-14.

| Constat | Verdict |
|---|---|
| **F-11** — les ratchets de comptage `testsMinimum` et `architectureTestsMinimum` sont des valeurs absolues : « fusionner deux cas en un test paramétré, ou retirer une classe devenue redondante, fait échouer le gate alors que la qualité n'a pas bougé » (marges de +11 et +6 le 09/09/2026) | **Aucun changement de forme — clos.** La marge est relue dans un **relevé Surefire** (`validate-m21`, somme des attributs `tests` des `TEST-*.xml`), jamais dans le source : à `24bd5a9b` (Windows, 11/09/2026) `tests=1674` pour un plancher de 1550, **marge 124 (8,0 %)**, et `architectureTests=422` pour 385, **marge 37 (9,6 %)** ; à `86d1799a` le même jour, 1663 / 420. La marge a été multipliée par dix depuis le constat. Trois faits fondent le verdict. **(1) La moitié du scénario n'existe pas sous ce comptage** : Surefire compte une exécution par invocation paramétrée — mesuré le 11/09/2026 avec les versions du dépôt (Surefire 3.6.0, JUnit 6.1.3) sur un projet témoin, une classe portant un `@Test` et un `@ParameterizedTest` à trois valeurs produit `tests="4"` pour deux annotations. Fusionner des cas en test paramétré ne fait donc pas descendre le compte ; le dépôt ne porte d'ailleurs aucun `@ParameterizedTest` à cette date. **(2)** Retirer des tests le fait descendre, mais la marge absorbe désormais une consolidation de plus de cent tests ; une consolidation plus large est un événement qui mérite une décision écrite, et c'est précisément ce que le plancher force. **(3)** Les formes alternatives ne sont pas qualifiées : un seuil relatif doit mémoriser la valeur précédente, soit le même absolu sous un autre nom ; un seuil par module multiplie les clés sans preuve supplémentaire. **Grandeur à ne pas confondre** : `grep -rhoE '@Test\b'` compte des annotations — 1673 dans tout le dépôt et 427 dans `morpheus-architecture-tests` à `24bd5a9b` — quand les ratchets comptent des exécutions (1674 et 422 au même SHA) ; citer une marge exige le relevé Surefire. À rouvrir sur un **fait** : une consolidation légitime qui bute sur le plancher |
| **F-12** — mélangeait une surface d'entrée non couverte et une observation sur la taille des classes | **Coupé en deux, clos.** **(a) Le défaut réel, corrigé.** `MorpheusAugmentedContextCli$ContextOptions`, parseur d'options de la commande `augmented-context`, était à **0/49 lignes et 0/32 branches** sur l'agrégat du 11/09/2026 (`86d1799a`) : le pendant CLI de F-02, une validation d'arguments jamais atteinte. `MorpheusAugmentedContextCliTest` (neuf tests, chaque refus asserté par le message que lit l'utilisateur, aucune base créée par un refus) le porte à **49/49 et 32/32** à `24bd5a9b`, sur l'échelle agrégée comme sur l'échelle par module ; la classe englobante passe de 24/78 à 78/78 lignes (16/34 à 32/34 branches), `$Parsed` de 14/21 à 19/21 (5/13 à 11/13). **(b) L'observation de taille, sans action.** 17 classes de production dépassent 400 lignes (`MorpheusCli` 853 en tête, puis `SqlitePolicyPackStore` 743 et `BoundedStdioClientTransport` 570), inchangé depuis le 09/09/2026. Aucune ne traverse une frontière de module (`LayerDependencyTest`), et aucun instrument ne qualifie un seuil de taille : en fabriquer un inventerait une règle que rien ne mesure. Aucune n'est touchée. À rouvrir pour une raison indépendante de la taille — un défaut, une couverture manquante, une frontière franchie |

---

## Correctifs issus de la passe post-audit du 04/09/2026 (A-02 à A-09)

| Constat | Traitement |
|---|---|
| **A-02** — une mutation remote bloquée retenait ses slots sans être observable, et `server/status` était admis par le sémaphore qu'il décrit : un serveur à son plafond répondait `429` à la seule question utile | status servi sur une **voie bornée dédiée** (hors budget de requêtes, toujours authentifié et autorisé) ; status expose `activePrivilegedRequests`, `maxConcurrentPrivilegedRequests`, `oldestActivePrivilegedRequestMillis`, `throttledPrivilegedRequests` ; tests adversariaux de saturation, de refus et de libération de tous les slots. **Résiduel assumé** : une mutation réellement bloquée retient son slot jusqu'à sa fin réelle — aucune deadline n'est ajoutée, car un `504` sur un commit peut-être déjà durable serait pire |
| **A-03** — la contention SQLite n'avait aucun signal en production ; `SqliteFailureClassifier` n'avait **aucun appelant** hors tests | compteurs process-local `sqlite.transaction.{started,committed,rolled_back}`, `sqlite.contention.{busy_or_locked,connection_open}` et timing `sqlite.transaction.duration`, exposés par `GET /api/v1/metrics` ; tests de stress multi-écrivain (aucune perte d'update, aucun doublon d'identité, aucun lease abandonné). **Portée déclarée** : transactions explicites et ouvertures de connexion, pas les écritures mono-instruction en autocommit |
| **A-04** — les deux frontières d'exécution externe (probe plugin, pair MCP) avaient des allowlists d'environnement identiques **par coïncidence**, sans contrat | `ExternalCodeTrustBoundaryArchitectureTest` : allowlists asserties identiques, aucun secret MORPHEUS ni variable d'injection JVM, environnement reconstruit depuis vide, pin + staging vérifié, et scan de **toute** source de production interdisant une revendication de sandbox |
| **A-05** — couverture historique globale modeste | tests ciblés sur les zones à risque (disponibilité remote, contention SQLite, migration d'identités, parsing fail-closed du fichier d'identités, cycle de vie du secret TLS, charge HTTP) ; ratchets relevés uniquement dans la marge déjà qualifiée sur les deux plateformes |
| **A-06** — le format d'identité sans expiration n'avait aucune sortie opérable | `server identity migrate-legacy` (dry-run, échéance explicite, aucune rotation de token, écriture atomique verrouillée, audit sans secret, refus complet d'un lockout ADMIN) et visibilité `nonExpiring` dans le listing. Le format reste supporté en 1.2.1 |
| **A-07** — `jdk.httpserver` jamais mesuré sous charge | `MorpheusRemoteLoadProfileTest` : tempête de lectures au-delà de la capacité, charge mixte lecture/mutation, corps surdimensionnés, fermeture de la façade. Le scénario « client abandonné » de cette suite s'est révélé faux (voir A-11) et vit désormais dans `MorpheusRemoteAdversarialClientTest`. Les propriétés sont asserties, les latences **enregistrées comme preuve** dans `target/remote-load-profile.txt` ; critères objectifs de remplacement documentés |
| **A-08** — `RemoteApiLaunchOptions` retenait le mot de passe TLS en `String` pendant toute la vie du serveur, et un record rend tous ses composants | les options ne portent plus que le moyen de retrouver la valeur ; résolution tardive en `char[]`, effacé dès que le `SSLContext` existe. **Pas une promesse d'effacement** : la JVM détient déjà le `String` d'origine |
| **A-09** — macOS inconnu | lane `macos-smoke` **advisory** exécutant le reactor complet et publiant les faits système observés. Aucun packaging, aucun engagement de support ; RT-08 reste ouvert |
| **A-01** — qualification de la chaîne de release attestée | **non traitée volontairement.** Aucun tag, aucune release, aucune simulation. #185 et DT-11 restent ouverts |


---

## Correctifs issus de la passe post-audit du 04/09/2026 (A-10 à A-16)

| Constat | Traitement |
|---|---|
| **A-10** — la façade remote bornait le nombre de requêtes, la mémoire et la taille des réponses, mais pas le **temps** qu'un client pouvait mettre à lire ; un client TLS authentifié qui cesse de lire retenait permits, slot de réponse et thread aussi longtemps qu'il restait connecté | `TimedBoundedResponseWriter` applique deux budgets à **toutes** les écritures de réponse (enveloppe et corps proxifié) : stall 15 s réarmé à chaque bloc réellement écrit, total 120 s. La deadline interrompt le thread écrivain — fermeture de canal spécifiée par `java.nio.channels.InterruptibleChannel`, **pas** la propriété interne `sun.net.httpserver.maxRspTime`. Compteur `responseWriteTimeouts` dans `server/status`. **Résiduel assumé** : la réponse est avortée, le client voit un corps tronqué et aucune enveloppe d'erreur ne peut lui parvenir — la connexion qui la porterait est la ressource récupérée |
| **A-11** — le scénario « client abandonné » de `MorpheusRemoteLoadProfileTest` utilisait `BodyHandlers.discarding()`, qui **consomme** le corps normalement : il ne pouvait pas échouer | scénario retiré et renommé pour ce qu'il testait réellement (corps surdimensionné). `MorpheusRemoteAdversarialClientTest` pilote une socket TLS brute contre la vraie façade : lecture des seuls en-têtes puis arrêt, lecture lente avec pauses, disparition brutale (`SO_LINGER 0`). `TimedBoundedResponseWriterTest` exerce le mécanisme sur des canaux TCP réels. Fixture dimensionnée pour dépasser les tampons de socket, sinon un client bloqué ne coûte rien au serveur |
| **A-12** — une seule ligne `# audit\|…` illisible faisait échouer **toute** mutation ultérieure du fichier d'identités : le credential compromis restait valide et devenait irrévocable | l'audit historique est une **preuve, pas une autorité**. Les entrées illisibles sont mises en quarantaine et la perte est elle-même enregistrée (`AUDIT_QUARANTINED`, sujet réservé `morpheus.audit`, aucun écho de la ligne rejetée). `revoke`, `rotate` et `create` aboutissent ; le fichier reste parsable et l'audit borné. La lecture stricte `audit(Path)` reste stricte : elle rapporte l'état du disque sans bloquer une opération de sécurité |
| **A-13** — `MorpheusHttpQuery.parse` et le resolver remote découpaient et décodaient la query **avant** toute limite MORPHEUS | `HttpQueryBudget` partagé par les deux parseurs : 16 KiB de query, 16 paramètres, 128 octets de nom, 8 KiB de valeur, comptés en **octets UTF-8** et vérifiés sur le texte brut avant allocation (le décodage ne peut que rétrécir). Dépassement = `400 BAD_REQUEST` déterministe, jamais `500` ; encodage `%` invalide également |
| **A-14** — `BoundedStdioClientTransport` n'avait aucune machine d'état : un second `connect()` démarrait un second pair et écrasait la référence au premier, laissant un processus que plus rien ne pouvait nommer — donc plus rien ne pouvait terminer | lifecycle explicite `NEW → CONNECTING → CONNECTED → CLOSING → CLOSED/FAILED`, revendiqué **avant** tout démarrage. Démarrage et teardown partagent un verrou, donc ils ne courent plus après le même processus. `claimTeardown()` donne le ticket unique à `closeGracefully()` ou à `failClosed()`, et un second `close` attend le premier au lieu de le répéter. Tests : double connect séquentiel et concurrent, connect après close, close concurrent, échec de démarrage — chacun comptant les lancements **côté pairs** |
| **A-15** — le ruleset exige une PR mais pas d'approbation humaine | **décision de gouvernance, aucune modification GitHub effectuée.** `.github/CODEOWNERS` existe déjà et couvre `.github/`, `scripts/`, `distribution/`, `morpheus-api/`, `morpheus-provider-sdk/`, `morpheus-mcp-transport/`, `morpheus-store-sqlite/` ; le ruleset **Protect main & develop** exige une PR mais porte `require_code_owner_review=false` et `required_approving_review_count=0`, assumé pour un dépôt à mainteneur unique. Si le dépôt devient collaboratif, la recommandation est d'activer au minimum une revue obligatoire (`required_approving_review_count >= 1` et/ou `require_code_owner_review=true`) sur le ruleset existant — `CODEOWNERS` n'est pas à créer, il est déjà en place |
| **A-16** — couverture à renforcer | uniquement par des scénarios adversariaux réels : slow reader, stalled reader, déconnexion brutale, récupération des permits, audit d'identités corrompu, budgets de query aux bornes, double connect MCP séquentiel et concurrent, nettoyage après échec de démarrage. Aucun seuil abaissé, aucun test supprimé, aucune exclusion de couverture |

Constat connexe relevé pendant la passe et corrigé : le schéma `ServerStatus` de `docs/openapi/morpheus-v1-remote-m26.yaml` portait `additionalProperties: false` tout en omettant sept champs que la réponse émettait déjà depuis M26 (`activePrivilegedRequests`, `oldestActivePrivilegedRequestMillis`, `requestBodyReadTimeoutMillis`, `totalPrivilegedRequests`, `throttledPrivilegedRequests`, `requestTimeouts`, `maxConcurrentPrivilegedRequests`). Le contrat publié rejetait donc la réponse réelle du serveur. Les champs manquants et `responseWriteTimeouts` y sont désormais décrits.

Constat connexe relevé pendant la passe et corrigé : `AuditHardeningWorkflowContractTest` interdisait `continue-on-error` dans **tout** `ci.yml`. Exact tant que le fichier ne portait qu'un seul job, faux dès qu'une lane advisory apparaît à côté. Le contrat vise désormais précisément le job `verify` requis : ce qui ne doit jamais être advisory est le job qui garde les merges, pas le fichier.

Observation de mesure, sans correction : l'agrégat JaCoCo **sous-estime structurellement** la couverture, parce que le rapport de `morpheus-architecture-tests` est exclu de l'agrégation alors que cette suite couvre une large part de `morpheus-application`. Aucun changement n'a été fait : inclure ce rapport ferait monter le chiffre sans ajouter un seul test, ce qui est exactement le jeu que les ratchets existent pour empêcher.

---

## Correctifs issus de l'audit du 30/08/2026

| Constat | Traitement |
|---|---|
| `nextSpecificationVersionSequence()` utilisait `MAX(sequence)+1`, ce qui pouvait attribuer le même numéro à deux connexions/processus concurrents | migration V017 `specification_version_sequences`, réservation durable dans une transaction d'écriture avant la publication, prise en compte du maximum déjà stocké, parité du store mémoire et test de concurrence à deux stores indépendants |
| La discovery provider validait un chemin puis rouvrait le JAR sans vérifier qu'il s'agissait toujours du même fichier | revalidation des attributs/identité avant et après la lecture metadata, diagnostic `PLUGIN_JAR_CHANGED_DURING_SCAN` en cas de remplacement ; l'activation exécutable conserve la frontière plus forte SHA-256 + staging vérifié déjà existante |
| `QueryFieldType.NUMBER` existait dans l'API mais héritait de l'égalité/du tri textuels | sémantique numérique explicite basée sur `BigDecimal` pour l'égalité canonique et l'ordre ; comportement historique TEXT/ENUM/BOOLEAN/IDENTITY préservé |
| L'audit initial signalait l'absence d'un gate changed-branch à 70 % et un registre de risques obsolète | ces deux points étaient déjà corrigés sur `develop` avant cette branche : gate `>=80%` lignes / `>=70%` branches et ruleset `main/develop` actifs ; aucun correctif redondant ajouté |
| La couverture globale restait modeste | les ratchets avaient déjà été qualifiés à `52,0%` lignes / `45,0%` branches et #184 clôturée ; les nouveaux correctifs ajoutent des tests ciblés, sans relever artificiellement les seuils avant mesure exact-head |
| La chaîne de release 1.2.1+ n'avait pas encore de qualification réelle | le workflow attesté existe déjà sur `develop`; #185 reste volontairement ouvert jusqu'à une vraie release, aucune release artificielle n'est créée pour fermer le constat |

La migration V017 change la version de schéma durable de 16 à 17. Les validations M26 Linux et Windows sont alignées sur `schemaVersion=17` pour les scénarios backup, verify et restore ; les migrations historiques V001..V016 restent immuables.

---

## Correctifs issus du réaudit post-#183 du 27/08/2026

| Constat | Traitement |
|---|---|
| `QueryDefinitionCodec.decode()` pouvait commencer une récursion avant application des budgets globaux M24 | codec désormais fail-fast : taille encodée <= 16 KiB avant Base64, profondeur <= 8 avant récursion, compteurs globaux <= 128 nœuds et <= 64 prédicats ; validation sémantique avant retour |
| `QueryValidator` continuait à parcourir l'AST après dépassement structurel | parcours interrompu dès le premier dépassement structurel pour borner aussi les AST construits directement en mémoire |
| HTTP/MCP/CLI pouvaient dépendre implicitement du comportement du codec sans garde d'architecture dédiée | contrat d'architecture ajouté : les trois adapters policy doivent passer par `QueryDefinitionCodec` et ne peuvent introduire un décodeur Base64 parallèle |
| Couverture historique à 50,7896 % lignes / 43,2215 % branches | aucun seuil abaissé ; tests adversariaux ciblés ajoutés ; la remontée progressive a ensuite permis de clôturer #184 après qualification des ratchets `52,0% / 45,0%` |
| Réglages SonarCloud / alertes administrateur externes non qualifiables par le code | vérifiés directement sur leurs plateformes ; #154 clôturée, aucune correction repository-side supplémentaire n'était requise |
| Workflow release attestée correct mais jamais exécuté sur une vraie release | qualification end-to-end suivie par #185 ; aucune release artificielle créée pour fermer le constat |

Les tests adversariaux du codec couvrent notamment une représentation >16 KiB, 10 000 `NOT` imbriqués, le 129e nœud et le 65e prédicat. La preuve M24 historique reste immuable ; ADR-0092 contient un addendum de hardening post-audit.

---

## Correctifs issus de l'audit du 27/08/2026

| Constat | Traitement dans la baseline corrective |
|---|---|
| Cache OWASP accepté 72 h mais refresh trusted hebdomadaire | refresh trusted quotidien à 04:17 UTC, TTL PR centralisé à 72 h et contrat d'architecture interdisant le retour au cron hebdomadaire |
| Release avec checksum sans preuve d'origine | workflow `MORPHEUS Release` sur tags `vX.Y.Z`, tag obligatoirement atteignable depuis `main`, attestation GitHub OIDC/Sigstore via `actions/attest` pinné par SHA, assets non écrasables |
| Changed-code gate uniquement par lignes | gate PR `>= 80%` lignes et `>= 70%` branches sur les lignes exécutables modifiées, preuve archivée dans `diff-coverage.txt` |
| Diagnostics MCP pouvant journaliser un `Throwable` peer brut | redaction JSON/named secrets, `describe(Throwable)` sanitizé, suppression des logs bruts dans les transports client et serveur, tests de régression |
| Guide build/test désynchronisé | reactor, baselines, ratchets, sécurité, release et couverture différentielle réconciliés |
| #166 et registre de risques encore basés sur `develop` non protégée | état GitHub réconcilié ; #166 peut être clôturée comme complétée |

---

## Correctifs issus de l'audit du 26/08/2026

| Constat | Traitement dans la baseline corrective |
|---|---|
| Environnement MORPHEUS intégralement hérité par MINOS/NEXUS | `BoundedStdioClientTransport` conserve uniquement une allowlist de lancement puis applique les variables explicitement configurées pour le peer ; **incomplet jusqu'au 23/09/2026 (MCP-5)** : ces variables « explicites » étaient relues sur le `ServerParameters` du SDK, dont le builder remplit sa propre allowlist par défaut — un pair lancé sans configuration recevait sur Windows huit variables hors de celle de MORPHEUS (`APPDATA`, `USERPROFILE`, `USERNAME`…). **Fermé** : l'environnement explicite vient de `McpPeerLaunch`, construit par la passerelle ; mesuré sur un vrai processus pair |
| Descendant MCP pouvant survivre après sortie du parent | observation périodique des `ProcessHandle`, rétention des descendants vus puis cleanup forcé même si le parent a déjà disparu ; **résiduel assumé** : un descendant créé et orphelin dans un même intervalle d'observation n'est plus attribuable au pair et n'est pas terminé (cf. `SECURITY.md`, section Process-tree termination) |
| Descendant de plugin provider survivant au worker | **fermé** : `ProviderPluginProbeWorker` termine son propre sous-arbre avant de sortir, tant qu'il est encore énumérable ; le cleanup côté parent reste une seconde ligne de défense |
| Secret NVD disponible sur le chemin PR | `security.yml` sépare les événements de confiance du chemin `pull_request`; l'update PR n'injecte aucun repository secret |
| Absence de SAST versionné | `.github/workflows/codeql.yml` ajouté, actions CodeQL pinnées par SHA, Java `security-extended` |
| Ratchets devenus trop permissifs | M21 relevé progressivement ; baseline qualifiée au 02/09/2026 `1150 / 310 / 54,0% / 47,0%` (valeur historique ; voir l'état CI actif pour la baseline courante) |
| Manifeste update distant en HTTP | `UpdateDiscoveryService` accepte `file:` et `https:` uniquement ; `http:` est refusé avant I/O |

La réduction d'environnement et le suivi des descendants sont des mesures de confinement de lifecycle et de secrets ; ils ne transforment pas un JAR externe explicitement configuré en code non fiable sandboxé. MINOS/NEXUS et les plugins exécutables restent sous le même compte OS que MORPHEUS.

---

## Correctifs issus de l'audit post-#153

Les constats techniques démontrés le 13 août 2026 ont reçu des mitigations exécutables dans la baseline corrective 1.2.1 :

| Constat | Traitement actif |
|---|---|
| Sync déjà commité reclassé en échec | relecture du commit durable, un retry idempotent borné, puis `BASELINE_INCONSISTENT` si publication potentiellement déjà effective |
| `SCAN_INCOMPLETE` écrasé par `EXECUTION_FAILED` | cause spécifique conservée |
| `Error` hors rollback SQLite | rollback best-effort sur `Error`, erreur primaire réémise, cleanup/rollback en suppressed |
| Audit d'identités pouvant remplir 256 KiB | fenêtre roulante atomique des 512 derniers événements sans secret |
| Discovery plugin suivant les symlinks | `NOFOLLOW_LINKS`, répertoire et JAR symboliques refusés |
| Probe plugin retournant 504 sans cancellation garantie | aucune deadline façade sur le probe tiers ; slot remote détenu jusqu'à la fin réelle |
| Query/CLI ouvrant plusieurs connexions physiques | `SqliteConnectionScope` partagé par opération/runtime |
| Version 1.2.0 réutilisée après release publiée | reactor, gates et builders actifs en `1.2.1`; tag/release `v1.2.0` historique inchangé |
| SCA uniquement manuel | workflow `MORPHEUS Security` + Dependabot |

La publication de snapshot et la baseline d'inventaire restent physiquement deux transactions distinctes. La mitigation est volontairement une **réconciliation bornée et fail-safe**, pas une prétendue transaction distribuée atomique.

---

## État CI actif

Le workflow public exact-head utilise :

```text
Ubuntu  -> bash ./scripts/validate-m21.sh 1.2.1
Windows -> scripts\validate.cmd m21 -Version 1.2.1
```

M21 s'exécute sur les pull requests ainsi que sur les pushes `main` et `develop`. Les actions tierces des workflows actifs sont référencées par SHA immuable et le Maven Wrapper vérifie le SHA-256 de la distribution Maven.

Deux signaux tournent sur une **cadence bornée** plutôt que par pull request, dans `nightly.yml` : l'analyse SonarQube Cloud de `develop` — que `ci.yml` ne couvrait pas, puisqu'il n'analyse que les pushes sur `main`, soit une fois par promotion — et la lane advisory `macos-smoke`. `nightly.yml` n'a **aucun** déclencheur `pull_request`, ce qui est la raison même de son existence : `SONAR_TOKEN` est un secret durable et ne doit jamais côtoyer du code non mergé.

Ratchets actifs :

```text
Surefire total          >= 1550
architecture            >= 385
aggregate line          >= 85.0%
aggregate branch        >= 68.0%
per-module line         >= 64.6%
per-module branch       >= 56.9%
changed-line            >= 80%
changed-branch          >= 70%
```

La qualification exact-head de #230 sur `9602eaa4a20b08955e63a6dfe10e30fb1ec90f1d` a produit `1017` tests, `308` tests d'architecture, `52,6971%` lignes et `45,7250%` branches et a justifié les ratchets ci-dessus. Les PR de hardening ultérieures doivent satisfaire les mêmes gates ; le registre n'utilise pas un SHA mouvant de `develop` comme prétendue baseline active.

`MORPHEUS Security` exécute OWASP Dependency-Check (CVSS >= 7 bloquant) sur PR/push `main` et `develop`, quotidiennement et sur demande. Les PR n'obtiennent pas la clé NVD depuis ce workflow. `MORPHEUS CodeQL` exécute un SAST Java versionné avec `security-extended`.

Le ruleset GitHub actif rend ces checks obligatoires avant merge sur `main` et `develop`. Le nom **M21** désigne le gate durable d'intégrité/surface-convergence ; il ne signifie pas que les fonctionnalités M22 à M28 sont absentes ou non qualifiées.

---

## Incohérences documentaires connues

| ID | Incohérence | Gravité | Traitement recommandé |
|----|-------------|---------|-----------------------|
| IC-02 | `docs/architecture/overview.md` conserve son statut de proposition C0 | **Faible si qualifié comme historique** | Ne pas le promouvoir artificiellement ; utiliser les ADR/code/validations récents comme sources actives |
| IC-03 | Certains documents de milestone restent ancrés sur leurs versions historiques | **Faible si explicitement historiques** | Ne pas modifier leurs preuves ; maintenir les guides actifs séparément |

---

## Principes de traitement

1. Corriger immédiatement toute documentation active qui affirme une version, un provider, un gate ou une architecture faux.
2. Conserver les documents de validation historiques immuables lorsque leur rôle est de prouver un milestone passé.
3. Ne pas inventer un ADR à partir d'un risque : créer l'ADR seulement après besoin démontré.
4. Ne pas remplacer automatiquement le gate CI M21 par le numéro du dernier milestone fonctionnel.
5. Toute évolution sécurité, stockage, remote ou packaging doit conserver des preuves exact-head reproductibles.
6. Les réglages externes (SonarCloud, alertes/scanning GitHub non exposés au connecteur) restent ouverts tant qu'ils n'ont pas été vérifiés sur leur plateforme respective.
