# Prompt — Sprint 1 : analyse et spécification OpenSpec

> À coller dans une session ouverte sur `N:\workspace-dev\morpheus-engine`.
> Produit des artefacts OpenSpec et **aucune implémentation**.

---

Tu analyses et spécifies le **sprint 1 de l'audit du 9 octobre 2026**, soit 8 constats, avec OpenSpec.

## Ce que tu produis, ce que tu ne produis pas

**Tu produis** : un ou plusieurs changes OpenSpec complets sous `openspec/changes/`, validés par `openspec validate --strict`, puis une PR vers `develop` qui ne contient que des artefacts OpenSpec et, si nécessaire, la correction de la divergence règle/code signalée plus bas.

**Tu ne produis pas** : de code applicatif, de script corrigé, de workflow modifié, de test écrit. `tasks.md` **décrit** le travail, il ne le fait pas. L'implémentation est un lot séparé qui viendra après validation de la spécification.

La seule exception : si l'analyse établit qu'une règle de `.claude/rules/` affirme un comportement que le code n'a pas, `.claude/rules/meta.md` exige de le signaler — fais-le dans la PR, et corrige la règle dans le même change si la correction est une phrase.

## À lire avant toute chose

1. `docs/audit/2026-10-09/README.md` — synthèse, notes par axe, thèmes transverses, angles morts.
2. `docs/audit/2026-10-09/sprints.md`, section **Sprint 1** — pourquoi ces 8 constats sont ensemble, le critère de sortie, l'ordre interne.
3. `docs/audit/2026-10-09/findings.json` — les 8 constats en entier. Filtre sur `sprint == 1`. Chaque constat porte `preuve` (avec `fichier:ligne`), `impact`, `action`, `depends_on`, `bloque`.
4. `openspec/changes/archive/` — **huit changes réels**, c'est ta référence de forme. Lis-en deux en entier, dont `2026-10-09-align-openspec-provider-with-upstream-format` (il a des deltas de spec) et `2026-10-09-strengthen-test-evidence` (il a `skip_specs: true`).
5. `openspec/specs/` — les 8 capacités déjà spécifiées. L'une d'elles est concernée par ce sprint.
6. Les règles du dépôt : `.claude/rules/governance.md`, `testing.md`, `tooling.md`, `meta.md`, `build.md`.

Charge les skills du dépôt qui s'appliquent : **`live-numbers`** (obligatoire, voir plus bas), **`enforcement-choice`** (deux constats posent exactement la question qu'elle tranche), **`milestone-quadruplet`** (un constat porte sur des validateurs de milestone), **`coverage-ratchet`** (un constat porte sur le gate de couverture).

**N'utilise aucun outil MINOS** (`minos_*`) : non validé sur cette application.

## Périmètre — les 8 constats

Critère de sortie du sprint, à reprendre comme intention de haut niveau :
**« Tout gate déclaré par le dépôt est atteignable par une invocation réelle du dépôt, et aucun validateur ne rend PASS sur une valeur périmée. »**

| Constat | Sév. | Eff. | Objet | Point d'entrée dans le code |
|---|---|---|---|---|
| `AUD-TST-01` | Élevée | S | Les 5 budgets de performance M19 ne sont exécutés par aucun pipeline ni par `clean verify` | `morpheus-architecture-tests/.../m19/M19PerformanceGate.java:24` · `.github/workflows/ci.yml:43,86` · `morpheus-architecture-tests/pom.xml:146-154` |
| `AUD-DEP-06` | Moyenne | S | `validate-d2` code en dur un plancher 820/258 au lieu de lire la source vivante | `scripts/validate-d2.sh:110-115` · `scripts/validate-d2.ps1:132-133` · `.../d2/D2RepositoryHardeningArchitectureTest.java:106-115` |
| `AUD-DEP-07` | Moyenne | M | Le gate CVE a un point de défaillance unique : pas de rafraîchissement NVD sans `NVD_API_KEY` | `.github/workflows/security.yml:17,75-86,136,179-184` · RT-13 dans `docs/architecture/risks/register.md:27` |
| `AUD-TRV-01` | Moyenne | M | Les validateurs M15 à M18 commutent sur des branches disparues, sans `.sh`, et les commandes documentées n'existent pas | `scripts/validate-m15.ps1:9,112-114` (idem m16/m17/m18) · `docs/validation/VALIDATION_M15.md:199` · `.../m28/McpClientIntegrationArchitectureTest.java:219-231` |
| `AUD-TST-02` | Moyenne | S | Le gate de couverture par module accepte un rapport JaCoCo périmé : aucun contrôle de fraîcheur | `morpheus-architecture-tests/.../m21/CoverageQualityGateTest.java:270-288` (test de refus existant : `:184-204`) |
| `AUD-QUA-14` | Faible | S | SpotBugs et PIT sont épinglés et configurés mais lancés par aucun workflow | `pom.xml:80-90` · aucun des 5 workflows ne les nomme · `config/spotbugs-exclude.xml` vide |
| `AUD-TST-11` | Faible | S | Nom de test périmé et version de schéma recopiée en dur trois fois | `morpheus-store-sqlite/.../SqliteSchemaMigrationTest.java:39,140,154,194` · contre-exemple correct : `SqliteMigrationChecksumGoldenTest.java:50` |
| `AUD-TST-12` | Faible | S | La doc M19 renvoie à un `validate-m19.cmd` qui n'existe pas | `docs/validation/VALIDATION_M19.md:89` · `docs/roadmap/M19_EXECUTION.md:210` |

`AUD-TST-01` est le **chemin critique de tout l'audit** : effort S, et il débloque 7 constats des sprints 6, 8 et 13 (`AUD-PRF-01/02/04/05/06`, `AUD-TRV-02`, `AUD-DEP-12`). Si un seul constat doit être spécifié sans compromis, c'est celui-là.

Ordre interne : aucune dépendance intra-sprint. Les 8 peuvent être spécifiés en parallèle.

## Phase 1 — Analyse

### 1. Revérifier chaque constat sur `HEAD`

L'audit date du 9 octobre, et le dépôt bouge. Pour chacun des 8 constats, ouvre le fichier à la ligne citée et **confirme ou réfute** la preuve. Rends un tableau : constat, preuve tenue / décalée / corrigée depuis, et ce que tu as lu.

Un constat déjà corrigé sort du périmètre — dis-le, ne le spécifie pas. Un constat dont la ligne a bougé reste valide : donne la nouvelle ligne.

### 2. Décider le découpage en changes, et le justifier

Un change OpenSpec porte **une capacité**. 8 constats ne font pas forcément un change, et ne font pas forcément huit.

Hypothèse de départ, à confirmer ou à casser — ne la prends pas pour acquise :

- **Un change principal** sur une capacité nouvelle du type *« atteignabilité et intégrité des gates »* : `AUD-TST-01`, `AUD-DEP-06`, `AUD-TRV-01`, `AUD-TST-02`, `AUD-QUA-14`, `AUD-TST-12`. L'invariant commun est le critère de sortie du sprint, et `AUD-TST-12` en fait partie — une commande documentée qui ne résout pas est un gate non atteignable.
- **`AUD-TST-11` à part**, parce qu'il touche une capacité **qui existe déjà** : `openspec/specs/sqlite-schema-history`. Un constat qui modifie une capacité existante mérite son change plutôt que de gonfler une capacité neuve.
- **`AUD-DEP-07` à part ou dedans**, à trancher : son livrable n'est pas un correctif mais une alerte et une procédure de rotation, et il dépend d'une publication amont (Dependency-Check 13.0.1) qui peut ne pas exister. C'est une capacité de *résilience de la chaîne d'approvisionnement*, pas d'atteignabilité.

Critères pour trancher : une capacité se spécifie par des exigences qui partagent un même sujet et un même critère de vérification ; si tu dois écrire « et par ailleurs » entre deux exigences, ce sont deux capacités. Nomme chaque capacité en kebab-case, en anglais, comme les 8 existantes.

### 3. Nommer les décisions de conception avant d'écrire

Trois arbitrages réels, qui vont dans `design.md` avec leur alternative rejetée :

- **`AUD-TST-01`** — un job nightly dédié, ou renommer les gates en `*Test` derrière un profil Maven ? Le renommage les rend comptés par `testsMinimum` et exécutés à chaque `clean verify`, avec des budgets sur fixtures de 10 000 requirements et 25 000 liens et une enveloppe de 60 s pour une publication complète. Chiffre la conséquence sur la durée de build avant de choisir.
- **`AUD-DEP-06`** — faire lire la source vivante aux scripts, ou assumer un plancher historique et renommer la méthode de test ? Les deux sont défendables ; une seule est compatible avec `.claude/rules/governance.md` tel qu'il est écrit.
- **`AUD-TRV-01`** — réparer M15 à M18, ou les archiver ? Et dans les deux cas, la parité `.ps1`/`.sh` doit cesser d'être assertée milestone par milestone. Passe par **`enforcement-choice`** pour décider si l'invariant générique est une règle ArchUnit ou une assertion textuelle, et applique sa procédure : **casse la règle une fois pour prouver qu'elle tient** avant de l'accepter dans la spécification.

## Phase 2 — Spécification

Pour chaque change retenu, sous `openspec/changes/<AAAA-MM-JJ du jour>-<slug-anglais-kebab>/` :

- **`.openspec.yaml`** — `schema: spec-driven`, `created:`, et `skip_specs: true` seulement si le change n'introduit ni ne modifie aucune exigence (2 des 8 changes archivés le font ; ici ce sera rare).
- **`proposal.md`** — structure exacte des changes archivés : `# Proposal`, `## Why`, `## What Changes`, `## Capabilities` (avec `### New Capabilities` et `### Modified Capabilities`), `## Impact`. Le `## Why` cite les identifiants de constats et des preuves `fichier:ligne` — c'est ce qui rend la proposition vérifiable par un relecteur qui n'a pas lu l'audit.
- **`design.md`** — les arbitrages de la phase 1.3, chacun avec son alternative rejetée et la raison du rejet. Indique si un ADR est nécessaire ; si oui, **n'attribue pas de numéro de mémoire** — relis les ADR existants (`live-numbers` donne le plus haut attribué et détecte les doublons).
- **`specs/<capability>/spec.md`** — deltas, format des changes archivés : `# Spec Delta`, `## Purpose`, puis `## ADDED Requirements` / `## MODIFIED Requirements` selon le cas. Une exigence s'écrit `### Requirement: <phrase>` avec SHALL / MUST, et porte au moins un `#### Scenario: <cas>` en **WHEN / THEN**. Chaque scénario doit être vérifiable par une commande ou un test nommé — une exigence dont on ne peut pas écrire le scénario de refus n'est pas une exigence.
- **`tasks.md`** — le travail d'implémentation, découpé, dans l'ordre, avec pour chaque tâche le fichier visé et la preuve attendue. Pas de code.

**Les artefacts OpenSpec s'écrivent en anglais.** Les 8 changes archivés et les 8 specs existantes le sont. Ton analyse et ton rapport final en session restent en français.

## Conventions non négociables

- **`live-numbers` avant de citer un chiffre périssable.** Lance `bash .claude/skills/live-numbers/numbers.sh`. Ne recopie jamais un ratchet, un compte d'ADR, une version de schéma ou une version de produit depuis une page de documentation, depuis l'audit, ou depuis ce prompt. Si une valeur lue contredit une page, signale-le : la source vivante gagne.
- **Parité dual-platform.** Tout validateur se livre en `.ps1` **et** `.sh`. Une spécification qui n'exige pas les deux reproduit le défaut qu'elle corrige.
- **Pas de dégradation silencieuse** (`.claude/rules/code-style.md`) : un refus porte un code nommé, jamais un repli muet.
- **Pas de code mort, pas de shim de compatibilité.**
- Les scripts `.ps1` restent en ASCII pur (`.claude/rules/tooling.md`).

## Pièges connus — ne les redécouvre pas

1. **`AUD-DEP-06`, le verrou ne verrouille pas.** `D2RepositoryHardeningArchitectureTest.java:110` asserte `script.contains("820")`, satisfait par la sous-chaîne de « **3820** ». Le test censé épingler le plancher périmé ne détecterait donc même pas une mise à jour correcte de `testsMinimum`. La spécification doit traiter l'assertion, pas seulement la valeur.
2. **La règle est fausse, pas seulement le code.** `.claude/rules/governance.md:102-106` affirme que « `scripts/validate-m21.*` et `scripts/validate-d2.*` lisent ce fichier ». `grep -c m21-quality-ratchets` donne 1, 1, 0, 0. C'est la divergence que `meta.md` demande de signaler.
3. **`AUD-DEP-07` dépend d'une publication amont.** Vérifie si Dependency-Check 13.0.1 existe aujourd'hui. Si non, la spécification doit tenir **sans** : l'exigence porte sur l'alerte précoce et la procédure de rotation documentée, l'adoption de 13.0.1 est une tâche conditionnelle.
4. **`AUD-TST-02`, le périmètre est le poste de développement, pas le CI.** `scripts/validate-m21.sh:66` lance `./mvnw clean verify` lui-même, donc le CI n'est pas affecté. Le risque est une fausse assurance locale. Ne surdimensionne pas l'exigence.
5. **Un milestone a quatre artefacts obligatoires** (`milestone-quadruplet`). Si ton change touche la structure des validateurs de milestone, vérifie que la spécification ne casse pas ce quadruplet pour les milestones existants.

## Supervision en parallèle

Lance, **en parallèle** de ton travail de spécification, deux agents de supervision qui n'écrivent aucun artefact et rendent une liste d'objections :

- **`architect`** (agent du dépôt) — la spécification respecte-t-elle les frontières ports & adapters ? Une exigence demande-t-elle quelque chose que `morpheus-architecture-tests` interdit déjà, ou qu'une règle existante contredit ? Il doit citer le test concerné.
- **`contract-guardian`** (agent du dépôt) — ce sprint touche-t-il une surface publique ? *A priori* non : aucune commande CLI, aucun outil MCP, aucune route HTTP ne bouge. Fais-le confirmer plutôt que de le supposer, et fais-le trancher sur la nécessité d'un ADR.

Un troisième contrôle, à faire toi-même et à ne pas déléguer : **aucune exigence ne doit dupliquer une exigence déjà présente dans `openspec/specs/`**. Lis les 8 capacités existantes et dis explicitement, pour chacune de tes exigences nouvelles, qu'elle n'y figure pas.

Traite les objections avant de finir. Une objection écartée se justifie par écrit dans `design.md`.

## Fin de course

1. `openspec validate --strict` sur chaque change — vert, sans exception.
2. `openspec list` pour vérifier que les changes apparaissent.
3. Une branche par change, nommée `<type>/<slug>`, un commit par artefact logique, et une PR vers **`develop`** par change. La description de PR liste les constats couverts, le critère de sortie du sprint, les décisions de `design.md` et les objections de supervision traitées.
4. **Ne fusionne rien.** Les checks CI doivent être verts et l'état CLEAN avant fusion, et c'est moi qui fusionne.
5. Rends en fin de session, en français : le tableau de revérification des 8 constats, le découpage retenu avec sa justification, les décisions de conception, les objections de supervision et leur traitement, les divergences règle/code trouvées, et ce que tu n'as pas pu vérifier.
