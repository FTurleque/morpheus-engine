# Audit outillé — MORPHEUS 1.2.1 — `develop` @ `3ec3ea46`

Statut : **Relevé daté — 8 octobre 2026.** Toutes les passes prévues ont été exécutées ; les limites qui restent (Linux, Dependency-Check local, constats R) sont au [§ 9](#9-limites-et-environnements-non-exécutés) et au [§ 10](#10-état-de-reprise).

> **Régime de ce document.** Relevé daté, comme les autres documents de [`docs/audits/`](README.md) : chaque nombre
> vaut au SHA `3ec3ea46` et à la date indiquée, sur la machine décrite au § 1, et n'est pas réputé courant. Les
> procédures réutilisables (profils Maven, commandes, interprétation) vivent dans
> [`../developer/CODE_AUDIT.md`](../developer/CODE_AUDIT.md) ; ce document ne les répète pas.
>
> **Ce que « couvert » veut dire ici.** Une exécution réussie n'est pas une analyse effective, et une analyse
> effective n'est pas une absence de défaut. Chaque ligne de la matrice (§ 3) dit séparément si l'outil a tourné, ce
> qu'il a réellement lu (compté), et ce qu'il a trouvé.

## 0. Résumé

| Outil | Périmètre réellement couvert | Résultat |
|---|---|---|
| **SpotBugs** 4.10.4 | 16/16 modules à code de production (1 041 `.class`) **et** le bytecode de test des 18 modules (741 `.class`) | 195 alertes production, 188 test ; production qualifiée (§ 5.1), aucune alerte de sécurité réelle |
| **PIT** 1.30.0 | 16/16 modules à code de production avec leurs propres tests (P01–P17), le cœur avec les tests d'architecture (X1), les classes amont encore non couvertes avec les tests de `mcp`, `api`, `cli` (X2), `application.read`/`files` avec les tests des providers (X3) | union de 23 rapports : 12 627 mutations, 9 969 tuées, 1 586 survivantes, 1 072 non couvertes (91,5 % couvertes) ; 2 faiblesses de test confirmées sur des contrôles de sécurité (§ 5.2) |
| **ArchUnit** 1.5.1 | 15 des 16 modules à code de production importés ; `morpheus-provider-testkit` absent (déclaré, § 5.3) | 1 règle vide corrigée, 9 règles et 1 contrôle de complétude ajoutés et prouvés par violation plantée, 2 gates textuels ajoutés ; 1 décision ouverte ; `clean verify` vert (§ 4.5 bis) |
| **OWASP Dependency-Check** 13.0.0 | rapport **CI** du SHA exact : 18 composants externes production + 10 test, 16 modules du réacteur | 0 vulnérabilité active, 30 suppressions vérifiées faux positifs ; **mise à jour NVD locale bloquée** |
| **Gitleaks** 8.30.1 | historique local (`--all`, 3 477 commits scannés) + miroir distant (343 têtes de PR, 3 799 commits) + arbre de travail (700 Mo) | 0 secret réel ; 5 + 8 + 21 détections, toutes faux positifs qualifiés |
| Revue ciblée (non-scanners) | providers, stockage, MCP/intégrations, packaging/CI | 4 défauts confirmés par exécution ou mesure (STO-AUD-1, PRV-AUD-1/2/3), 3 par lecture (MCP-AUD-1/2, API-AUD-1), 2 risques confirmés (PKG-AUD-1, INT-AUD-4) ; le reste est rapporté, non revérifié (§ 5.4) |

**Problèmes prioritaires confirmés.**

1. **STO-AUD-1 (P1)** — la somme de contrôle des migrations SQLite dépend des fins de ligne du checkout : une base
   créée par la version Windows est refusée par la version Linux, et une restauration croisée **supprime l'ancienne
   base** avant de constater qu'elle ne peut pas ouvrir la nouvelle.
2. **PRV-AUD-3 (P1)** — le provider OpenSpec annonce `spec-driven` SUPPORTED mais ne lit pas un changement écrit avec
   les gabarits de la CLI OpenSpec 1.14.1 : cinq catégories passent `FAILED` (mesuré sur l'`openspec/` de ce dépôt).
3. **PRV-AUD-1, PRV-AUD-2 (P2)** — une exigence dupliquée dans un delta fait **lever** `read()` ; un BOM UTF-8 produit
   un faux diagnostic (« pas de schéma », « pas de titre »). Reproduits par exécution.
4. **MCP-AUD-1, MCP-AUD-2 (P2)** — un échec d'ouverture SQLite s'échappe des outils MCP en erreur de protocole, contre
   ADR-0102 ; la borne de durée d'un handler est inférieure au pire cas d'une opération MINOS/NEXUS bornée.
5. **API-AUD-1 (P2)** — `GET /api/v1/provider-plugins/discover` est servi et déclaré au manifeste mais absent de
   l'OpenAPI.

## 1. État de référence reproductible

| Élément | Valeur |
|---|---|
| Commit | `develop` @ `3ec3ea46499f2a5a40790d25d6e4c0ebd52fb8ae` (= `origin/develop`) ; `main` @ `2684d001` |
| Arbre de travail au départ | propre côté suivi ; non suivis préexistants préservés : `openspec/` (initialisé le 07/10, vide), `.claude/skills/openspec-*`, `.claude/commands/opsx/`, `Claude outputs/` |
| Machine | Windows 10 Pro 10.0.19045, 16 cœurs, JDK 21.0.12.1 (Microsoft) via `JAVA_HOME` (un JDK 24 est premier dans le `PATH`) |
| Maven | wrapper du dépôt, Maven 3.10.0 |
| `TMP`/`TEMP` | répertoire dédié durci `C:\Users\fturl\morpheus-test-tmp-audit` (héritage coupé, utilisateur seul) — le `%TEMP%` par défaut échoue au durcissement ACL |
| Outils | SpotBugs 4.10.4 (plugin 4.10.4.1), PIT 1.30.0 + `pitest-junit5-plugin` 1.2.3, ArchUnit 1.5.1, JUnit 6.1.3, Dependency-Check 13.0.0, Gitleaks 8.30.1 (archive officielle, SHA-256 vérifié contre `gitleaks_8.30.1_checksums.txt`), OpenSpec CLI 1.14.1 |
| Modifications apportées par l'audit | profils d'audit de `pom.xml` (§ 4.1), 3 classes de test d'architecture ajoutées et 1 corrigée (§ 5.3), 6 changements OpenSpec (§ 7), ce document, [`CODE_AUDIT.md`](../developer/CODE_AUDIT.md), [`README.md`](README.md). **Aucun code de production modifié, aucun commit.** |

Rejouer le build de référence (Git Bash ; adapter les chemins) :

```bash
export JAVA_HOME=<JDK 21> TMP=<répertoire durci> TEMP=<répertoire durci>
unset NoDefaultCurrentDirectoryInExePath
./mvnw -B clean install -Paudit-spotbugs -Dspotbugs.failOnError=false
```

Résultat : `BUILD SUCCESS`, **3 642 tests exécutés, 0 échec, 0 erreur, 7 ignorés** — les 7 sont désactivés sous
Windows (`@DisabledOnOs`) : 5 dans `OpenSpecRefusedFileNameTest`, 1 dans `MorpheusProjectSyncDisclosureTest`, 1 dans
`SqliteDatabaseLeaseTest`. Ils n'ont **pas** été exécutés par cet audit. Couverture mesurée (Windows, constat, pas
une requalification de ratchet) : agrégée 89,80 % lignes / 75,07 % branches ; par module 68,30 % / 60,36 %.

## 2. Inventaire

**Réacteur** : 18 modules dans le `pom.xml` racine — les 17 de la liste de départ **plus `morpheus-coverage-report`**
(agrégation JaCoCo, 1 classe de test, aucune classe de production). IntelliJ (MCP JetBrains) voit les mêmes 18 + la
racine, et une seule racine Git. Aucun code Java hors réacteur (`git ls-files '*.java'` : 1 155 fichiers, tous sous
`morpheus-*/src`).

| Module | `src/main/java` | `src/test/java` | ressources main | tests exécutés | `.class` prod / test |
|---|---:|---:|---:|---:|---|
| morpheus-domain | 84 | 8 | 0 | 43 | 84 / 8 |
| morpheus-application | 341 | 73 | 0 | 362 | 526 / 117 |
| morpheus-provider-sdk | 20 | 23 | 0 | 87 | 37 / 40 |
| morpheus-provider-testkit | 1 | 1 | 0 | 8 | 3 / 4 |
| morpheus-provider-reference | 3 | 1 | 2 (`META-INF`) | 2 | 3 / 1 |
| morpheus-provider-openspec | 9 | 9 | 0 | 70 (5 ignorés) | 19 / 14 |
| morpheus-provider-markdown | 3 | 2 | 0 | 9 | 7 / 2 |
| morpheus-provider-synthetic | 4 | 4 | 0 | 30 | 7 / 5 |
| morpheus-store-memory | 10 | 7 | 0 | 24 | 13 / 9 |
| morpheus-store-sqlite | 29 | 46 | 20 (migrations V001–V020) | 142 (1 ignoré) | 49 / 52 |
| morpheus-mcp-transport | 11 | 20 | 0 | 75 | 13 / 30 |
| morpheus-integration-minos | 7 | 6 | 0 | 18 | 17 / 9 |
| morpheus-integration-nexus | 7 | 4 | 0 | 11 | 13 / 5 |
| morpheus-mcp | 20 | 28 | 0 | 139 | 22 / 35 |
| morpheus-api | 79 | 90 | 0 | 347 (1 ignoré) | 154 / 115 |
| morpheus-cli | 28 | 36 | 0 | 1 720 | 74 / 74 |
| morpheus-architecture-tests | 0 | 140 | 0 | 551 | 0 / 218 |
| morpheus-coverage-report | 0 | 1 | 0 | 4 | 0 / 3 |

`morpheus-cli` exécute 1 720 tests pour 177 annotations : trois classes paramétrées de refus d'options en portent
1 569 (`BlankOptionValueRefusalTest` 1 022).

**Hors réacteur, versionné** : `scripts/` (25 `.ps1`, 22 `.sh`, 1 `.cmd`, 1 `.py`), `distribution/` (jpackage, Inno
Setup `MORPHEUS.iss`, 7 scripts), `integration/` (2 `.ps1`), `.github/workflows/` (5 workflows), `contracts/`
(`public-surfaces.tsv`, 74 capacités), `config/` (3), `docs/openapi/` (6), `experiments/` (75, dont fixtures),
`.claude/hooks/` (2 `.ps1`), `.mvn/`, `.rtk/`. ADR : 108 fichiers numérotés, le plus haut `0108`, aucun doublon.

**Hors réacteur, non versionné, présent sur la machine** : `dist/` (installeur et zip 1.2.1 Windows construits
localement, runtime Java embarqué), `validation-output/` (2,6 Go de sorties de validateurs).

## 3. Matrice de couverture

Légende : **A** = analysé (compté) · **0** = applicable mais sans objet (pas de classe) · **NA** = non applicable ·
**B** = bloqué · **P** = partiel · **—** = non fait.

### 3.1 Modules × outils

| Module | SpotBugs prod | SpotBugs test | PIT (tests du module) | PIT (tests d'autres modules) | ArchUnit importé | DC (prod) | Gitleaks |
|---|---|---|---|---|---|---|---|
| domain | A 84 cl., 6 al. | A 8 cl., 1 al. | A 315 mut. | A via application (P02) et tests d'architecture (X1) | oui | A | A |
| application | A 526 cl., 135 al. | A 117 cl., 15 al. | A 4 508 mut. | A via tests d'architecture (X1) | oui | A | A |
| provider-sdk | A 37 cl., 3 al. | A 40 cl., 12 al. | A 373 mut. | A via tests d'architecture (X1) | oui | A | A |
| provider-testkit | A 3 cl., 0 al. | A 4 cl., 0 al. | A 33 mut. | NA | **non (déclaré)** | A | A |
| provider-reference | A 3 cl., 0 al. | A 1 cl., 0 al. | A 18 mut. | — | oui | A | A |
| provider-openspec | A 19 cl., 12 al. | A 14 cl., 13 al. | A 481 mut. | A via tests d'architecture (X1) | oui | A | A |
| provider-markdown | A 7 cl., 1 al. | A 2 cl., 7 al. | A 108 mut. | A via tests d'architecture (X1) | oui | A | A |
| provider-synthetic | A 7 cl., 1 al. | A 5 cl., 0 al. | A 274 mut. | A via tests d'architecture (X1) | oui | A | A |
| store-memory | A 13 cl., 0 al. | A 9 cl., 1 al. | A 352 mut. | A via tests d'architecture (X1) | oui | A | A |
| store-sqlite | A 49 cl., 28 al. | A 52 cl., 4 al. | A 1 682 mut. | A via tests d'architecture (X1) | oui | A | A |
| mcp-transport | A 13 cl., 0 al. | A 30 cl., 1 al. | A 341 mut. | — | oui | A | A |
| integration-minos | A 17 cl., 0 al. | A 9 cl., 0 al. | A 149 mut. | — | oui | A | A |
| integration-nexus | A 13 cl., 0 al. | A 5 cl., 0 al. | A 114 mut. | — | oui | A | A |
| mcp | A 22 cl., 0 al. | A 35 cl., 11 al. | A 404 mut. | — | oui | A | A |
| api | A 154 cl., 9 al. | A 115 cl., 72 al. | A 1 856 mut. | — | oui | A | A |
| cli | A 74 cl., 0 al. | A 74 cl., 1 al. | A 1 619 mut. | NA (module le plus aval) | oui | A | A |
| architecture-tests | 0 (aucune classe) | A 218 cl., 48 al. | NA (cible) / source de tests | — | NA | test seul | A |
| coverage-report | 0 (aucune classe) | A 3 cl., 2 al. | NA | NA | NA | NA (pom) | A |

« cl. » = fichiers `.class` du répertoire analysé ; « al. » = alertes. Le XML du plugin SpotBugs publie
`total_classes='0'` : le compte vient du répertoire désigné par l'élément `<Jar>` du rapport (toujours
`target/classes`, ou `target/test-classes` en plus pour la passe test), contrôlé non vide, avec un temps CPU non nul
et `missingClasses=0` pour chaque module.

### 3.2 Actifs non Java × outils

| Actif | SpotBugs / PIT | ArchUnit | Dependency-Check | Gitleaks | Autre vérification |
|---|---|---|---|---|---|
| Scripts `scripts/`, `distribution/`, `integration/`, hooks | NA | NA (gates textuels) | NA | A (fichiers + historique) | gate ASCII `.ps1` ajouté (§ 5.3) ; exécution CI partielle (§ 5.4, CI-AUD-3) |
| Workflows `.github/workflows/` | NA | NA | **non couvert** (actions GitHub hors DC) | A | gate SHA ajouté pour **toute** action (§ 5.3) |
| Contrats `public-surfaces.tsv`, OpenAPI | NA | NA | NA | A | revue : API-AUD-1 |
| Migrations SQL V001–V020 | NA | NA | NA | A | revue + mesure : STO-AUD-1 |
| Dépendances Maven produit | NA | NA | A (CI) : 18 composants | NA | suppressions vérifiées |
| Dépendances Maven test | NA | NA | A (CI) : +10 composants | NA | — |
| Plugins Maven de build, wrapper Maven | NA | NA | **non couvert** | NA | versions épinglées (D2) |
| Runtime JDK embarqué, Inno Setup | NA | NA | **non couvert** | A (fichiers locaux `dist/`) | PKG-AUD-3 |
| Binaires MINOS / NEXUS | NA | NA | **non couvert** (externes) | NA | INT-AUD-1 |
| Références Git | NA | NA | NA | A : 9 réf. locales (dont une `refs/codex/...` absente du distant) + 349 réf. du miroir (343 têtes de PR) | clone complet (`--is-shallow-repository` = false) |

## 4. Commandes et résultats par lot

Tous les lots tournent depuis la racine, environnement du § 1. Les rapports bruts sont archivés **hors
versionnement** dans `validation-output/audit-2026-10-08/` (§ 8).

### 4.1 Changements d'outillage (profils `pom.xml`)

| Changement | Raison | Effet par défaut |
|---|---|---|
| `audit-spotbugs` : propriétés `spotbugs.audit.includeTests` et `spotbugs.audit.xmlOutputFilename` | analyser le bytecode de test sans écraser le rapport production | aucun (défauts : `false`, `spotbugsXml.xml`) |
| `audit-mutation` : `<crossModule>${pit.crossModule}</crossModule>` | mesurer une classe avec les tests d'un module aval | aucun (défaut `false`) |
| `audit-mutation` : `jvmArgs -Dmorpheus.project.version=${project.version}` | PIT ne lit pas les `systemPropertyVariables` de Surefire : sans elle, `ProductMetadata.version()` vaut `development` dans le minion et deux tests de `ProductIntegrityTest` échouent avant toute mutation (« Mutation testing requires a green suite ») | corrige une erreur d'outillage, ne change aucun test |

### 4.2 SpotBugs

```bash
./mvnw -B clean install -Paudit-spotbugs -Dspotbugs.failOnError=false                       # production
./mvnw -B -o -Paudit-spotbugs -Dspotbugs.failOnError=false -Dspotbugs.audit.includeTests=true \
  -Dspotbugs.audit.xmlOutputFilename=spotbugsXml-with-tests.xml -DskipTests verify          # production + test
```

Production : **195 alertes** (identiques au relevé du 07/10 de `CODE_AUDIT.md` § 12) — `EI_EXPOSE_REP` 137,
`EI_EXPOSE_REP2` 35, `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` 15, `URF_UNREAD_FIELD` 3, et 1 chacun
`DMI_RANDOM_USED_ONLY_ONCE`, `DCN_NULLPOINTER_EXCEPTION`, `SBSC_USE_STRINGBUFFER_CONCATENATION`,
`CT_CONSTRUCTOR_THROW`, `MS_EXPOSE_REP`. Avec le bytecode de test : 383 au total, dont **188 dans le code de test**
(`NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` 77, `VA_FORMAT_STRING_USES_NEWLINE` 34, `NP_NULL_PARAM_DEREF_NONVIRTUAL`
31, `EI_EXPOSE_REP` 17, puis 29 réparties sur 12 motifs). Les 195 alertes production sont les mêmes dans les deux
passes. Le contrôle bloquant (`failOnError` par défaut) reste **rouge** : rien n'a été exclu (§ 5.1).

### 4.3 Gitleaks

```bash
gitleaks git <dépôt> --log-opts="--all" --redact --report-format json --report-path <hors dépôt>   # exit 1
gitleaks git <miroir> --log-opts="--all" --redact ...                                              # exit 1
gitleaks dir <dépôt> --redact ...                                                                  # exit 1
```

`exit 1` signifie « détections présentes », pas une erreur : les trois journaux se terminent par `leaks found: N`
sans erreur d'exécution. Miroir : `git clone --mirror` dans un répertoire temporaire, hors de l'arbre de travail ;
aucune branche locale n'a été modifiée. Gitleaks ne scanne pas les commits de fusion sans diff, d'où 3 477 commits
scannés sur 3 926 accessibles localement. Aucune configuration ni baseline Gitleaks n'a été créée.

### 4.4 PIT

Lanceur : `-Paudit-mutation -pl <module> -Dpit.targetClasses=<paquetages du module>.* -Dpit.targetTests=com.morpheus.*
-Dpit.threads=12 -Dpit.timeoutConstant=8000 test-compile org.pitest:pitest-maven:mutationCoverage`. Un seul lot à la
fois (deux lots simultanés ont été constatés une fois après une coupure de session, arrêtés, et leurs résultats
écartés et rejoués). Compatibilité JUnit 6.1.3 vérifiée par exécution à chaque lot : tests découverts et exécutés,
mutations tuées (`Ran N tests` non nul partout).

| Lot | Module (tests du module) | Durée | Tests exécutés | Mutations | Tuées | Survivantes | Non couvertes | Délai dépassé | Autres | Lignes mutées couvertes |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| P01 | domain | 24 s | 213 | 315 | 100 | 7 | 206 | 0 | 2 RUN_ERROR | 313/803 (39 %) |
| P02 | **domain via les tests d'application** (`crossModule`) | 72 s | 1 190 | 315 | 168 | 54 | 93 | 0 | — | 640/803 (80 %) |
| P03 | application | 199 s | 6 168 | 4 508 | 1 815 | 539 | 2 150 | 4 | — | 4 751/10 367 (46 %) |
| P04 | provider-sdk | 389 s | 937 | 373 | 286 | 32 | 54 | 1 | 5 JVM orphelines | 716/889 (81 %) |
| P05 | provider-testkit | 9 s | 31 | 33 | 23 | 1 | 9 | 0 | — | 49/60 (82 %) |
| P06 | provider-reference | 9 s | 17 | 18 | 16 | 1 | 1 | 0 | — | 62/70 (89 %) |
| P07 | provider-openspec | 54 s | 2 012 | 481 | 385 | 78 | 18 | 0 | — | 966/1 047 (92 %) |
| P08 | provider-markdown | 16 s | 226 | 108 | 70 | 29 | 9 | 0 | — | 315/341 (92 %) |
| P09 | provider-synthetic | 34 s | 827 | 274 | 208 | 40 | 26 | 0 | — | 521/581 (90 %) |
| P10 | store-memory | 19 s | 245 | 352 | 151 | 32 | 169 | 0 | — | 377/708 (53 %) |
| P11 | store-sqlite | 721 s | 4 007 | 1 682 | 1 087 | 179 | 392 | 24 | — | 2 812/3 873 (73 %) |
| P12 | mcp-transport | 707 s | 1 110 | 341 | 206 | 75 | 46 | 14 | — | 573/635 (90 %) |
| P13 | integration-minos | 49 s | 200 | 149 | 90 | 22 | 37 | 0 | — | 196/289 (68 %) |
| P14 | integration-nexus | 44 s | 137 | 114 | 68 | 22 | 24 | 0 | — | 195/276 (71 %) |
| P15 | mcp | 210 s | 946 | 404 | 300 | 38 | 66 | 0 | — | 1 397/1 550 (90 %) |
| P16 | api | 5 361 s | 2 958 | 1 856 | 1 463 | 169 | 138 | 86 | — | 3 616/3 888 (93 %) |
| P17 | cli | 2 708 s | 14 671 | 1 619 | 1 073 | 231 | 189 | 126 | — | 2 664/3 109 (86 %) |
| **X1** | **cœur (domain, application, sdk, providers intégrés, stores) via les tests de `morpheus-architecture-tests`** (budgets M19 exclus) | 2 647 s | — | 8 093 | 4 197 | 1 195 | 2 584 | 117 | — | 12 403/18 609 (67 %) |
| X2 · mcp | 189 classes amont encore non couvertes après l'union, via les tests de `mcp` | (14 modules : 23 900 s au total) | — | 5 263 | 1 088 | 600 | 3 568 | 7 | — | — |
| X2 · api | idem, via les tests de `api` | | — | 5 694 | 2 169 | 980 | 2 412 | 133 | — | — |
| X2 · cli | idem, via les tests de `cli` | | — | 6 031 | 2 462 | 966 | 2 451 | 152 | — | 7 749/13 209 (59 %) |
| X3 · openspec | `application.read`, `application.files` via les tests de `provider-openspec` | 66 s (5 modules) | 2 366 | 206 | 88 | 65 | 53 | 0 | — | — |
| X3 · markdown | idem, via les tests de `provider-markdown` | | 458 | 206 | 74 | 72 | 60 | 0 | — | — |
| X3 · synthetic | idem, via les tests de `provider-synthetic` | | 396 | 206 | 78 | 63 | 65 | 0 | — | — |

« Tests exécutés » est le compte d'exécutions de la phase de couverture PIT, pas un nombre de méthodes.
« Lignes mutées couvertes » se lit avant le score : un `NO_COVERAGE` dans un lot ne prouve pas l'absence de test
dans le dépôt (§ 5.2).

**Ce que `crossModule` exige, mesuré.** Lancé avec `-pl morpheus-application` seul, `-Dpit.crossModule=true` ne
trouve **aucune** mutation du domaine (`No mutations found`) : le module amont est alors un JAR de `~/.m2`. Il faut
le module amont **dans le réacteur** (`-pl morpheus-domain,morpheus-application`) ; PIT tourne alors aussi dans le
module amont (lot P01 rejoué au passage).

**Processus orphelins.** Les tests de `provider-sdk` et `mcp-transport` lancent de vrais processus fils. Un mutant
qui casse le nettoyage de l'arbre de processus laisse des JVM vivantes après la mort du minion : 5 après P04. Le
lanceur d'audit les compte et les arrête après chaque lot (`orphans.ps1`), mais **ce décompte est sous-estimé** : un orphelin dont le PID parent a été réattribué par Windows passe pour rattaché. 13 JVM pairs (8 de P04, 5 de P12, lancées entre 01:12 et 01:40) ont survécu ainsi jusqu'à 11:00 et ont été arrêtées à la main. Un lot interrompu de force en laisse davantage (14 constatées et arrêtées à 01:10).

**Tests de la CLI à sous-processus.** Les tests qui lancent `java … MorpheusMain mcp --stdio` exécutent le bytecode
non muté de `target/classes` : ils ne peuvent tuer aucune mutation. Leur absence de contribution au score n'est pas un
manque de test.

### 4.5 ArchUnit

```bash
./mvnw -B -o test -pl morpheus-architecture-tests -Dtest='ImportedClasspathCompletenessTest,AdapterSiblingArchitectureTest,PolicyPlatformArchitectureTest,RepositoryTextHygieneContractTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Résultat : **21 tests, 0 échec** (`AdapterSiblingArchitectureTest` 10, `RepositoryTextHygieneContractTest` 4,
`ImportedClasspathCompletenessTest` 4, `PolicyPlatformArchitectureTest` 3).

**Preuve que les règles mordent** (ADR-0103, obligation 3), sans toucher au code de production : 7 classes fautives,
placées dans les vrais paquetages (`com.morpheus.application`, `.application.policy`, `.provider.markdown`,
`.store.memory`, `.integration.minos`, `.integration.nexus`, `.mcp`), compilées hors dépôt contre les JAR 1.2.1, puis
ajoutées au classpath de test pour un seul run :

```bash
./mvnw -B -o test -pl morpheus-architecture-tests -Dtest=... "-Dmaven.test.additionalClasspath=<classes plantées>"
```

Les **9 règles** de `AdapterSiblingArchitectureTest` et la règle M25 corrigée échouent, chacune en nommant le champ
fautif de la classe plantée (`exit=1`) ; `ImportedClasspathCompletenessTest` reste vert (les classes plantées
résident dans des paquetages existants) ; le même run sans classe plantée est vert. L'ancienne clause
`..provider.sdk..` ne pouvait pas échouer : aucun paquetage ne contient cette séquence. Les deux gates textuels de
`RepositoryTextHygieneContractTest` portent leur preuve dans le dépôt (tests de violation plantée sur `@TempDir`).
Sources des classes plantées : `validation-output/audit-2026-10-08/planted-archunit/`.

### 4.5 bis Vérification finale du réacteur avec les modifications de l'audit

```bash
git worktree add --detach <hors dépôt> HEAD     # + copie des 8 fichiers modifiés ou ajoutés par l'audit
./mvnw -B -o clean verify                       # TMP durci dédié
```

Résultat (08/10/2026, 08:03–08:19) : **`BUILD SUCCESS`, 3 660 tests exécutés, 0 échec, 7 ignorés** (les mêmes 7
désactivés sous Windows) ; `morpheus-architecture-tests` passe de 551 à **569** exécutions (+18 : 10 + 4 + 4 nouveaux
tests) ; tous les gates, dont `CoverageQualityGateTest` et `AggregateCoverageGateTest`, sont verts. Couverture du run :
agrégée 89,81 % lignes / 75,08 % branches, par module 68,30 % / 60,37 % — aucun ratchet n'a été modifié. Le worktree
a été supprimé ensuite. Ce run a partagé la machine avec la passe PIT X2 : X2 a enregistré 9 délais dépassés pendant
ces 16 minutes, au même rythme qu'avant, donc sans biais mesurable.

### 4.6 Dependency-Check

| Tentative | Commande | Résultat |
|---|---|---|
| Mise à jour locale avec la clé de l'environnement | `./mvnw -N -Pd2-security -DdataDirectory=<hors dépôt> -DnvdApiKeyEnvironmentVariable=NVD_API_KEY org.owasp:dependency-check-maven:13.0.0:update-only` | **échec** : `Invalid API Key` |
| Mise à jour locale sans clé | même commande sans la variable | **échec** : le plugin envoie un en-tête `apiKey` vide |
| Diagnostic direct de l'API NVD | `curl` sur `/rest/json/cves/2.0?resultsPerPage=1` | sans clé : HTTP 200 ; avec la clé locale : 404 ; avec un en-tête `apiKey` vide : 404 |
| Rapport de référence retenu | artefact `dependency-check` du run CI `push` 37695303525 (`security.yml`, SHA `3ec3ea46`, succès) | moteur 13.0.0, données NVD « Last Modified » 2026-10-07T20:57:14Z, rapport du 2026-10-07T22:19Z |

Conclusion : la clé `NVD_API_KEY` **de cette machine** est refusée par NVD (la clé du dépôt GitHub, elle, fonctionne :
le run CI a mis à jour ses données). Aucun scan local indépendant n'a pu être fait ; la conclusion DC repose sur le
rapport CI du même SHA.

## 5. Constats qualifiés

Niveaux de vérification : **E** = confirmé par exécution ou mesure de l'auditeur · **L** = confirmé par lecture du
code par l'auditeur · **R** = rapporté par un agent de revue en lecture seule, **non revérifié** — à reproduire avant
toute correction.

### 5.1 SpotBugs (production)

| ID | Alertes | Qualification | Vérif. | Prio |
|---|---|---|---|---|
| SB-AUD-1 | 3 × `URF_UNREAD_FIELD` sur `OpenSpecSpecificationContentReader$ReadState` (`currentAttempted`, `changeAttempted`, `deltaAttempted`) | **défaut confirmé** (code mort, règle `code-style.md`) — champs écrits, jamais lus | L | P3 |
| SB-AUD-2 | `DCN_NULLPOINTER_EXCEPTION` `MorpheusReasoningApiService.analyze:54` | **risque** : le `catch (NullPointerException)` couvre aussi `service.execute(...)`, donc un NPE interne serait renvoyé au client comme « reasoning request contains a null value » | L | P3 |
| SB-AUD-3 | 26 `EI_EXPOSE_REP*` sur des records publics de vue/requête sans copie défensive (`PolicyPublicViews`, `PortfolioPublicViews`, `QueryPublicViews`, `OperationalMetrics.Snapshot`, `MorpheusPolicyApiService.{Create,Update}Request`) | **faiblesse** : immuables en pratique (construits par des fabriques en `.toList()`), mais un record public peut être construit avec une liste mutable | L | P3 |
| SB-AUD-4 | 92 `EI_EXPOSE_REP*` sur champs copiés par `List/Set/Map.copyOf`, `Collections.unmodifiable*`, `.toList()` ; 23 sur des records du domaine copiés par un assistant ; 31 sur des porteurs de services/stores partagés volontairement | **faux positifs** | L (classement outillé + lecture) | — |
| SB-AUD-5 | 15 `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` : 5 motifs `x.getFileName() == null ? … : x.getFileName()`, 9 `getFileName()` sur des chemins issus de `Files.list/walk` ou de la découverte, 1 `getParent()` d'un chemin absolu (`SqliteServerMaintenance:140`) | **faux positifs** | L | — |
| SB-AUD-6 | `DMI_RANDOM_USED_ONLY_ONCE`, `SBSC_…`, `CT_CONSTRUCTOR_THROW` (classe abstraite package-private), `MS_EXPOSE_REP` (singleton de métriques voulu) | **bénins** | L | — |
| SB-AUD-7 | contrôle bloquant `audit-spotbugs` rouge par construction | **décision ouverte** : exclure précisément SB-AUD-4/5/6 dans `config/spotbugs-exclude.xml` (une entrée par motif et classe, avec raison), ou relever `threshold`/`maxRank` ; rien n'a été exclu par cet audit | — | P3 |

Bytecode de test (188 alertes) : aucun défaut bloquant ; `RV_EXCEPTION_NOT_THROWN` (3) est un faux positif
(`assertThrows` construisant une exception), `OS_OPEN_STREAM` (5) et `AT_STALE_THREAD_WRITE_OF_PRIMITIVE` (3) sont de
l'hygiène de test à revoir, sans incidence sur le produit.

### 5.2 PIT

**Union des lots** (meilleur statut par mutation sur P01–P17, X1 et les trois volets de X2 et de X3 ; identité = classe,
méthode, descripteur, ligne, mutateur, index, blocs). C'est la mesure du périmètre par **l'ensemble** des tests
exécutés, pas par module :

| Module | Mutations | Tuées | Survivantes | Non couvertes | Couvertes | Tuées / couvertes |
|---|---:|---:|---:|---:|---:|---:|
| domain | 315 | 263 | 27 | 25 | 92,1 % | 90,7 % |
| application | 4 508 | 3 517 | 647 | 344 | 92,4 % | 84,5 % |
| provider-sdk | 373 | 288 | 32 | 53 | 85,8 % | 90,0 % |
| provider-testkit | 33 | 23 | 1 | 9 | 72,7 % | 95,8 % |
| provider-reference | 18 | 16 | 1 | 1 | 94,4 % | 94,1 % |
| provider-openspec | 481 | 391 | 75 | 15 | 96,9 % | 83,9 % |
| provider-markdown | 108 | 71 | 28 | 9 | 91,7 % | 71,7 % |
| provider-synthetic | 274 | 210 | 39 | 25 | 90,9 % | 84,3 % |
| store-memory | 352 | 295 | 35 | 22 | 93,8 % | 89,4 % |
| store-sqlite | 1 682 | 1 450 | 151 | 81 | 95,2 % | 90,6 % |
| mcp-transport | 341 | 221 | 75 | 45 | 86,8 % | 74,7 % |
| integration-minos | 149 | 102 | 18 | 29 | 80,5 % | 85,0 % |
| integration-nexus | 114 | 74 | 19 | 21 | 81,6 % | 79,6 % |
| mcp | 404 | 300 | 38 | 66 | 83,7 % | 88,8 % |
| api | 1 856 | 1 549 | 169 | 138 | 92,6 % | 90,2 % |
| cli | 1 619 | 1 199 | 231 | 189 | 88,3 % | 83,8 % |
| **Total** | **12 627** | **9 969** | **1 586** | **1 072** | **91,5 %** | **86,3 %** |

Progression des mutations non couvertes : 3 400 avec les seuls tests de chaque module, 1 363 avec ceux
d'architecture (X1), 1 072 avec ceux de `mcp`, `api` et `cli` (X2) ; les tests des providers (X3) ne couvrent aucune mutation supplémentaire et en tuent 2 de plus.

« Tuées » inclut les délais dépassés (`TIMED_OUT`), que PIT compte comme détectés. **Un délai dépassé n'est pas une assertion** : il peut venir d'une boucle infinie réelle comme d'un test lent sous charge. Les constats ci-dessous le disent quand c'est le seul mode de détection. Les tests d'architecture font
passer les mutations non couvertes de 3 400 (lots par module seuls) à 1 363 : `application` de 2 150 à 610,
`store-memory` de 169 à 22, `store-sqlite` de 392 à 89, `domain` de 72 à 27.

**Absence de mutation possible.** Sur les 1 041 classes de production, celles qui n'ont produit aucune mutation sont
des types sans logique mutable — énumérations, interfaces, records sans validation, exceptions à constructeur
simple, tables `switch` synthétiques (`$1`), porteurs d'état à champs seuls — classés par `javap`
(`pit-unmutated.txt`) : `application` 216 (86 records, 57 enums, 31 interfaces, 20 tables `switch`, 18 exceptions,
4 autres), `api` 56, `domain` 26 (24 enums, 2 records), `cli` 24, `provider-sdk` 15, `store-sqlite` 14,
`integration-minos` 12, `integration-nexus` 8, les autres modules entre 0 et 6. Ce n'est ni de la non-couverture ni
une impossibilité technique.

**Impossibilité d'environnement.** Les branches POSIX de `LocalWritePermissionHardener`
(`requireProtectedPosixDirectory`, bit sticky, administrateur POSIX) ne s'exécutent pas sous Windows : leurs mutations
sont « non couvertes » ici sans que cela dise quoi que ce soit des tests sous Linux. Les tests à sous-processus ne
tuent aucune mutation (§ 4.4).

| ID | Constat | Qualification | Vérif. | Prio |
|---|---|---|---|---|
| PIT-AUD-1 | `SqliteServerMaintenance.restoreOffline` : retirer l'un des 4 appels `rejectUnsafeEntry` (base, `-journal`, `-wal`, `-shm`) **survit à tous les lots** (module, architecture, `mcp`, `api`, `cli`) ; aucun test du dépôt ne crée de lien symbolique ou de fichier non régulier à la restauration. Les mutations de la boucle de `sha256`, dont le retrait de `MessageDigest.update`, ne sont détectées que par délai dépassé (lot X2 `cli`), jamais par une assertion : l'empreinte n'est comparée à aucune valeur calculée indépendamment | **faiblesse de test confirmée** sur un contrôle de sécurité | E + L | P2 |
| PIT-AUD-2 | `LocalWritePermissionHardener` : `isTrustedAclPrincipal → true` n'est tuée par aucune assertion (seulement un délai dépassé sous les tests `api`) ; le seul test du refus d'ACL (`refusesAclDirectoryGrantingMutationToBroadPrincipalWhenAclIsAvailable`) cherche `Everyone`, `BUILTIN\Users`, `Users` et fait `return` si aucun n'est résolu ; **mesuré** sur cette machine (Windows en français) : aucun ne l'est, seuls « Tout le monde » et « BUILTIN\Utilisateurs » existent | **faiblesse de test confirmée** : le refus d'ACL n'est vérifié que sur un Windows anglophone (CI, non mesuré) | E | P2 |
| PIT-AUD-3 | `ProviderIngestionBudget$Session.read` : 22 survivants après **tous** les lots, tests des providers compris (X3). Lecture : les re-contrôles `requireDocumentBytes`, `requireAggregateBytes`, `requireEvidenceBytes` (l. 146-150) suivent une lecture déjà bornée par `readMaximum` — mutations **probablement équivalentes** (code redondant, candidat à simplification) ; en revanche le retrait de `requireLines` (l. 148, budget de lignes) et de `requireFiles` (l. 117, budget de fichiers) n'est vu par aucun test, l'attribution de la métrique dans le `catch` (l. 132-142) et les bornes exactes (l. 119, 123) non plus | **faiblesse de test confirmée** (budgets de lignes et de fichiers, métrique) + **simplification possible** (re-contrôles) | E + L | P3 |
| PIT-AUD-4 | domaine : 25 mutations ne sont exécutées par aucun test (union) ; les tests du domaine seuls n'en couvrent que 109/315 | **faiblesse de placement** des tests | E | P3 |
| PIT-AUD-5 | concentrations de survivants : `MultiProviderCompositionService`, `QueryExecutionService`, `NormalizedProjectContent`, `SqlitePolicyPackStore`, `SqlitePortfolioStore`, `SyntheticJsonParser` | **à examiner** une mutation à la fois (fichier `pit-union-final.tsv`) avant d'en faire des tâches | E (survie) | P3 |

Aucune exclusion de classe peu testée n'a été faite.

### 5.3 ArchUnit et gates de dépôt

| ID | Constat | Qualification | Vérif. | Action de cet audit | Prio |
|---|---|---|---|---|---|
| ARC-AUD-1 | `m25/PolicyPlatformArchitectureTest` interdit `..provider.sdk..` ; le SDK vit sous `com.morpheus.sdk.provider` : la clause ne vise rien | **faiblesse de gate confirmée** | L | clause corrigée en `..sdk.provider..` | P2 |
| ARC-AUD-2 | aucune règle n'a pour sujet le SDK, les providers, les stores, les intégrations entre elles, ni `com.morpheus.mcp..` ; aucun test ne vérifie que l'import est complet | **faiblesse** — les POMs empêchent ces arêtes aujourd'hui, aucune règle ne les refuserait demain | L | `AdapterSiblingArchitectureTest` (9 règles fondées sur ADR-0090, ADR-0028 §182, ADR-0107 §65 et le principe « adaptateurs frères » du `CLAUDE.md`) et `ImportedClasspathCompletenessTest` (import non vide, chaque paquetage source de chaque module du réacteur importé, absences déclarées et vivantes) | P2 |
| ARC-AUD-3 | `morpheus-api` et `morpheus-mcp` dépendent de `morpheus-store-sqlite` (et `api` de `morpheus-provider-openspec`) : 17 classes, contre « aucune dépendance de CLI/MCP/API à la base choisie » (ADR-0003 §7) et « adaptateurs frères » | **décision ouverte** — aucun ADR n'acte l'écart | L | changement OpenSpec `decide-adapter-composition-roots` | P2 |
| ARC-AUD-4 | `morpheus-mcp-transport` ne dépend d'aucun module MORPHEUS (seulement écrit dans des Javadocs de test) ; aucune règle de cycles entre paquetages | **proposition** — non ajoutée faute de décision écrite | L | dans le même changement | P3 |
| ARC-AUD-5 | `morpheus-provider-testkit` absent du classpath des tests d'architecture | **décision documentée** : l'ajouter en `test` sans référence ferait échouer `dependency:analyze` ; absence déclarée avec sa raison dans `ImportedClasspathCompletenessTest`, qui refusera l'entrée si elle devient fausse | L | — | — |
| CI-AUD-1 | l'épinglage SHA n'est exigé que pour des actions nommées (`checkout`, `setup-java`, `upload-artifact`, `cache`, `codeql`, `attest`, `download-artifact`) ; une action tierce `@v1` passerait | **faiblesse de gate confirmée** (règle écrite : « toutes les actions ») | L | gate `RepositoryTextHygieneContractTest#everyWorkflowActionIsPinnedByACommitSha` (toute ligne `uses:`) + test de violation plantée | P2 |
| CI-AUD-2 | « tout `.ps1` reste ASCII » n'était tenu que par l'avertissement local de `post-edit.ps1` | **faiblesse** | L | gate `#everyPowerShellScriptIsAsciiUnlessItCarriesABom` + test de violation plantée | P3 |

Les règles ajoutées sont des **constats d'une décision existante**, pas de nouvelles contraintes. Les nouvelles
contraintes (ARC-AUD-3, ARC-AUD-4) restent des propositions dans `decide-adapter-composition-roots`.

### 5.4 Revue ciblée (au-delà des scanners)

| ID | Constat | Qualification | Vérif. | Prio |
|---|---|---|---|---|
| **STO-AUD-1** | somme de contrôle des migrations = SHA-256 des octets bruts ; `.gitattributes` `* text=auto` sans règle `*.sql` ; blob Git de V001 `eeb86cf3…` (LF) ≠ `target/classes`, JAR installé et JAR de `dist/morpheus-1.2.1-windows-x64.zip` `a4342864…` (CRLF) ; `verify()` ne contrôle que `integrity_check` et `MAX(version)` puis `restoreOffline` jette l'ancienne base (`quarantine.discard()`) | **défaut confirmé** : base Windows refusée par une version Linux ; restauration croisée destructive. Côté Linux : déduit (aucun artefact Linux mesuré) | E (Windows) + L | **P1** |
| **PRV-AUD-3** | lecture de l'`openspec/` de ce dépôt (gabarits CLI 1.14.1) : `CHANGES`, `REQUIREMENT_DELTAS`, `CONSTRAINTS`, `DESIGN_DECISIONS`, `IMPLEMENTATION_TASKS` = `FAILED` (« OpenSpec proposal has no Proposal title ») alors que la sonde répond `spec-driven` SUPPORTED | **défaut confirmé** (contrat annoncé non tenu) ; le dialecte M0 (`# Proposal: …`, `## Intent`) diffère du gabarit amont (`# Proposal`, `## Why`) | E | **P1** |
| PRV-AUD-1 | exigence ADDED dupliquée dans un delta : `read()` lève `IllegalArgumentException: duplicate requirement delta identity` | **défaut confirmé** (ADR-0028 : `FAILED`, jamais d'exception) | E | P2 |
| PRV-AUD-2 | BOM UTF-8 en tête de `config.yaml` → sonde `INVALID` « does not declare a schema » ; en tête de `spec.md` → « has no title » | **défaut confirmé** | E | P2 |
| PRV-AUD-10 | le diagnostic `INVALID_SOURCE` de la sonde OpenSpec porte un chemin **absolu** (`source=C:\…\openspec\config.yaml`) | **risque** : à vérifier sur les surfaces distantes (filtre `ServerLocationDisclosure`) | E (chemin) | P3 |
| PRV-AUD-6 | six tests de liens symboliques font `return` (succès) si le lien ne peut être créé | **faiblesse de test** ; sur cette machine les liens sont créables (sonde exécutée), donc le build de référence les a réellement exercés | L + E | P3 |
| MCP-AUD-1 | `SqliteConnectionScope.open` enveloppe toute `SQLException` en `IllegalStateException`, le bail exclusif lève `IllegalStateException` hors `try` ; `MorpheusMcpServer.call` et 6 autres classes d'outils ne mappent que `IllegalArgumentException | KnowledgeStoreException` | **défaut confirmé** contre ADR-0102 §2 (« n'omet pas une exception qu'il peut atteindre ») : erreur de protocole au lieu de `isError` | L | P2 |
| MCP-AUD-2 | `DEFAULT_HANDLER_DEADLINE` = 4 min, gate = 2 × 120 s ; une opération MINOS/NEXUS envoie `initialize` + `tools/list` + ≥ 1 `tools/call`, chacun borné à 120 s, sans délai global | **défaut confirmé** (la Javadoc affirme qu'un pair à son maximum ne peut atteindre la borne) | L | P2 |
| API-AUD-1 | `GET /api/v1/provider-plugins/discover` servi (`MorpheusProviderPluginHttpRoutes:22`) et au manifeste (ligne 7), absent des 6 documents OpenAPI | **défaut confirmé** (convergence) | L | P2 |
| PKG-AUD-1 | `verify-windows-setup-lifecycle.ps1` installe puis désinstalle avec l'AppId **de production** sans vérifier l'existence préalable de sa clé de désinstallation (il le fait pour le menu Démarrer) | **risque confirmé** : sur un poste où MORPHEUS est installé, l'inscription réelle est réécrite puis supprimée | L | P2 |
| INT-AUD-4 | `GET /api/v1/integrations/{minos,nexus}/status` est ouvert au rôle **READ** distant (`MorpheusRemoteRoutePolicy`) et `status()` ouvre une passerelle, donc lance un processus pair, à chaque appel (`NexusMcpTechnicalContextProvider.status`) | **risque confirmé** : amplification (un jeton READ déclenche des lancements de JVM), bornée seulement par la limitation de débit, non mesurée | L | P2 |
| DC-AUD-1 | rapport CI : 30 suppressions = CPE `sqlite:sqlite`/`apple`/`google` sur la purl interne `morpheus-store-sqlite@1.2.1` et `github:cli` sur `morpheus-cli@1.2.1` | **faux positifs confirmés** (règles étroites, une CPE sur une purl exacte, commentées) | L | — |
| DC-AUD-2 | hors de toute analyse de vulnérabilités : plugins Maven de build, wrapper Maven, runtime JDK embarqué par jlink, Inno Setup, actions GitHub, MINOS/NEXUS | **risque** (chaîne d'approvisionnement partiellement aveugle) | L (configuration DC) | P2 |
| GL-AUD-1 | historique : 2 `curl-auth-user` (`-u "${SONAR_TOKEN}:"`, alimenté par `secrets.*`) + 3 (`refs/pull/234/head`) ; 5 `generic-api-key` = flèches de diagramme `API -X-> CLI` ; arbre : 16 `jdk.tls.keyLimits` du runtime embarqué, 2 diagrammes, 2 journaux Maven, 1 lien Power BI public dans un fichier de langue tiers d'Inno Setup | **faux positifs** ; aucune valeur affichée | E | — |
| DC-AUD-3 | clé NVD locale refusée ; plugin 13.0.0 sans clé envoie un en-tête vide refusé | **limite d'environnement** + comportement d'outil observé | E | P3 |

**Rapportés par les agents de revue, non revérifiés (R)** — à reproduire avant correction ; détail dans les
changements OpenSpec qui les portent ou dans le relevé brut (§ 8) :

- Stockage : STO-AUD-2 (crash pendant la restauration → démarrage silencieux sur base vide), STO-AUD-3 (perte
  définitive de l'ancienne base, décision), STO-AUD-4 (atomicité d'une migration échouée non testée), STO-AUD-5 (bail
  entre processus jamais testé), STO-AUD-6 (bail indexé sur le chemin normalisé, pas réel), STO-AUD-7 (parité
  portfolio : la mémoire écrase, SQLite ignore), STO-AUD-8 (idempotence lifecycle testée en mémoire seulement),
  STO-AUD-9 (migrations concurrentes en `DEFERRED`), STO-AUD-10 (base étrangère adoptée, `SQLITE_CORRUPT` non classé),
  STO-AUD-11 (assertions sans message), STO-AUD-12 (sauvegarde partielle laissée sous nom normal).
- MCP / intégrations / HTTP : INT-AUD-1 (contrats MINOS/NEXUS testés contre des pairs fictifs du dépôt seulement),
  INT-AUD-2 (requêtes en vol non libérées à la mort du pair), INT-AUD-3 (ligne non JSON-RPC ferme la session),
  INT-AUD-5 (texte d'erreur du pair
  relayé), API-AUD-2 (message Jackson exposant des noms de classes internes), API-AUD-3 (TLS 1.1 jamais refusé par un
  vrai handshake), API-AUD-4 (API loopback sans authentification), MCP-AUD-3 (chaînes MCP sans `maxLength`).
- Providers : PRV-AUD-4 (pas de masque de code fence ni fin de section dans le lecteur de spec courante), PRV-AUD-7
  (aucun test Maven ne charge un plugin depuis sa copie vérifiée), PRV-AUD-8 (budget d'evidence compté 2 à 3 fois),
  PRV-AUD-9, -11 à -16.
- CI / packaging : CI-AUD-3 (validateurs Linux hors M21 jamais exécutés en CI), CI-AUD-4 (release Linux sans tests ;
  validateurs Windows sous `pwsh` seulement au tag), CI-AUD-5 (`check-diff-coverage.py` sans test), CI-AUD-6 (aucun
  analyseur de workflows ni scanner de secrets versionné), CI-AUD-7, PKG-AUD-2 (binaires non signés, sans ADR),
  PKG-AUD-3 (runtime JDK absent du SBOM et du manifeste), PKG-AUD-4 (valeurs par défaut périmées de scripts
  M12/M13), PKG-AUD-5.

## 6. Plan de correction

Ordre recommandé ; chaque ligne renvoie au changement OpenSpec qui porte ses critères d'acceptation et ses tâches
(test qui échoue **avant** la correction, puis correction, puis validation).

| Ordre | Constats | Changement | Pourquoi d'abord |
|---|---|---|---|
| 1 | STO-AUD-1 (+ restauration) | `fix-sqlite-migration-checksum-portability` | perte de données possible à la restauration ; touche des bases déjà en service |
| 2 | PRV-AUD-3, PRV-AUD-1, PRV-AUD-2, PRV-AUD-4 | `align-openspec-provider-with-upstream-format` | le provider phare ne lit pas le format courant de l'outil qu'il cible |
| 3 | MCP-AUD-1, MCP-AUD-2 | `unify-mcp-tool-failure-contract` | contrat ADR-0102 non tenu sur la surface model-facing |
| 4 | API-AUD-1 | `close-public-surface-convergence-gaps` | règle de gouvernance « tout ensemble » non tenue |
| 5 | ARC-AUD-3, ARC-AUD-4 | `decide-adapter-composition-roots` | décision à prendre avant toute règle |
| 6 | PKG-AUD-1, PRV-AUD-6, STO-AUD-4/5, INT-AUD-1, CI-AUD-5, SB-AUD-1/2, PIT-AUD-* | `strengthen-test-evidence` | preuves plus fortes, aucun comportement produit |
| 7 | SB-AUD-7 | (décision, pas de changement) | choisir le contrat bloquant de SpotBugs |
| 8 | DC-AUD-2, PKG-AUD-3 | à ouvrir si décidé | SBOM et scan du runtime embarqué |

## 7. Changements OpenSpec préparés

Le dépôt a été initialisé avec OpenSpec CLI 1.14.1 le 07/10/2026 (`openspec/`, schéma `spec-driven`, **non versionné**,
sans spec ni changement au départ). La présence du provider OpenSpec dans MORPHEUS n'est pas une preuve d'usage ; c'est
cette initialisation, faite par le mainteneur, qui l'est. Les six changements ont été créés par `openspec new change`
puis rédigés à partir de `openspec instructions`, et `openspec validate` les déclare valides. Ils sont **en anglais**,
langue des gabarits du schéma, dont le validateur attend `SHALL`/`MUST`.

| Changement | Capacité | Artefacts |
|---|---|---|
| `fix-sqlite-migration-checksum-portability` | `sqlite-schema-history` (nouvelle) | proposal, spec, design, tasks |
| `align-openspec-provider-with-upstream-format` | `openspec-provider-reading` (nouvelle) | proposal, spec, design, tasks — décision 1 du design à confirmer |
| `unify-mcp-tool-failure-contract` | `mcp-tool-failure-contract` (nouvelle) | proposal, spec, design, tasks |
| `close-public-surface-convergence-gaps` | `public-surface-convergence` (nouvelle) | proposal, spec, tasks (design conditionnel non requis) |
| `decide-adapter-composition-roots` | — (`skip_specs`) | proposal, design, tasks |
| `strengthen-test-evidence` | — (`skip_specs`) | proposal, tasks |

Ils sont des **artefacts de planification** : aucun n'a été appliqué.

## 8. Rapports

| Nature | Emplacement | Versionné |
|---|---|---|
| Synthèse (ce document), procédure ([`CODE_AUDIT.md`](../developer/CODE_AUDIT.md)), changements OpenSpec | `docs/`, `openspec/` | ce document et `CODE_AUDIT.md` : à versionner ; `openspec/` : au choix du mainteneur (non suivi à ce jour) |
| Rapports SpotBugs (XML/HTML par module, deux passes), relevés TSV classés | `validation-output/audit-2026-10-08/spotbugs-*`, `*.tsv` | non (ignoré par Git) |
| Rapports PIT (`mutations.xml`, HTML) et journaux par lot | `validation-output/audit-2026-10-08/pit/` | non |
| Rapports Gitleaks JSON (`--redact`) et journaux | `validation-output/audit-2026-10-08/gitleaks/` | non |
| Rapports Dependency-Check du run CI 37695303525 | `validation-output/audit-2026-10-08/dependency-check-ci/` | non |
| Journaux Maven des lots, scripts du lanceur, programmes de reproduction | `validation-output/audit-2026-10-08/{logs,repro}/`, `*.sh`, `*.py` | non |

Le journal de mise à jour NVD imprimait un fragment masqué de la clé locale : il a été remplacé par
`<REDACTED-BY-AUDITOR>` dans les copies archivées. Aucun rapport archivé ne contient de valeur de secret.

## 9. Limites et environnements non exécutés

- **Linux** : aucun lot n'a tourné sous Linux ; les 7 tests désactivés sous Windows ne l'ont pas été ; le côté Linux
  de STO-AUD-1 est déduit. **macOS** : jamais.
- **Dependency-Check** : aucun scan local ; données NVD = celles du run CI.
- **MINOS / NEXUS réels** : non lancés ; seuls les pairs fictifs du dépôt ont tourné via les tests.
- **Packaging** : l'installeur et le zip de `dist/` n'ont pas été reconstruits ni installés par cet audit.
- **PIT** : voir § 10 ; les tests à sous-processus ne peuvent pas tuer de mutation ; des mutants du nettoyage de
  processus laissent des JVM orphelines.
- **Revue** : les constats marqués R n'ont pas été reproduits par l'auditeur.

## 10. État de reprise

Au moment de cette révision (08/10/2026, ~12:00), **toutes les passes prévues ont été exécutées** : build de
référence ; SpotBugs production et test ; Gitleaks (historique local, miroir avec têtes de PR, arbre de travail) ;
Dependency-Check (rapport CI du SHA ; mise à jour locale bloquée et diagnostiquée) ; PIT P01–P17, X1, X2 et X3, union
finale de 23 rapports (`validation-output/audit-2026-10-08/pit/pit-union-final.tsv`) ; revue ciblée et
requalifications E/L ; règles ArchUnit et gates ajoutés, prouvés par violation plantée ; `clean verify` complet vert
avec les modifications de l'audit ; 6 changements OpenSpec validés.

**Conditions d'exécution de X3.** Vers 11:00, l'environnement d'exécution de la session a changé (constaté juste après
une interruption de session ; les ACL du profil, le jeton du processus et l'accès contrôlé aux dossiers de Defender ont
été lus et sont normaux) : création de répertoire refusée directement sous `C:\Users\fturl`, `AccessDeniedException` de JUnit sur ses `@TempDir` dans le
TMP durci sous `C:`, et `javac` en « Cannot close compiler resources » sur des JAR `~/.m2` intacts. Un premier essai de
X3 dans cet état a vu 39 tests du provider OpenSpec échouer avant toute mutation ; il a été écarté. X3 a été rejoué
avec un **TMP durci neuf sur `N:`** (même recette `icacls`), après avoir vérifié que les tests concernés y repassent
sous Surefire (9/9), et en appelant directement le but PIT **sans phase de compilation** : les classes de `target/`
sont celles du build de référence, inchangées. Aucun réglage de sécurité n'a été modifié.

**Non revérifié** : les modifications de ce document postérieures au `clean verify` de 08:19 (tableaux PIT, constats
PIT-AUD, cette section) n'ont pas pu repasser `RepositoryDocumentationCoherenceTest`, la compilation de
`morpheus-architecture-tests` échouant dans l'état d'environnement ci-dessus. Elles n'ajoutent aucun lien.

**Non fait, hors de portée de cette session** : exécution sous Linux (tests désactivés sous Windows, branches POSIX,
côté Linux de STO-AUD-1), Dependency-Check local (clé NVD locale à renouveler), reproduction des constats R, MINOS et
NEXUS réels, reconstruction et installation de la distribution.

## 11. Suivi du 8 octobre 2026 (après-midi) — STO-AUD-1 corrigé dans la copie de travail

Le changement OpenSpec `fix-sqlite-migration-checksum-portability` a été appliqué (non commité) : checksum canonique
calculé sur le texte normalisé en LF, acceptation de la seule variante CRLF du même texte sans réécriture, vérification
du registre d'une sauvegarde avant la mise en quarantaine, `*.sql text eol=lf`, amendement d'ADR-0021. Ce paragraphe
ajoute des mesures ; il ne réécrit pas les constats ci-dessus, qui décrivent `3ec3ea46`.

**Tests du module** (`./mvnw test -pl morpheus-store-sqlite`) : Windows 10, JDK 21.0.12.1 — 150 exécutions, 0 échec,
1 ignoré (désactivé sous Windows) ; Linux (WSL2 Ubuntu, noyau 6.18, JDK 21.0.11, clone de `3ec3ea46` + les fichiers
modifiés) — 150 exécutions, 0 échec, 0 ignoré. Les tests de reproduction ont été vus rouges avant correction : le
registre LF (celui d'un build Linux) refusé sous Windows avec « SQLite migration history mismatch for version 1 », et la
restauration d'une sauvegarde au registre refusé acceptée puis base illisible, ligne témoin perdue, aucune copie de
l'ancienne base.

**Builds réels** (JAR CLI `--db`, base créée par `projects add` + `sync` sur `openspec-basic`, rouverte par
`projects list`) — à la place des images portables jpackage, pour ne pas écraser `dist/` ; le checksum vit dans le JAR :

| Base créée par | Ouverte par | Résultat |
|---|---|---|
| build Windows de `dist/morpheus-1.2.1-windows-x64.zip` (17 migrations, V001 `a4342864…` CRLF) | build Linux de `3ec3ea46` (sans correctif) | **exit 4 — « SQLite migration history mismatch for version 1 »** : le défaut, reproduit sur des builds réels |
| idem | build Linux avec correctif | exit 0, projet listé ; registre migré à 20 versions, V001 resté `a4342864…` (non réécrit) |
| build Windows avec correctif (V001 `eeb86cf3…`) | build Linux sans correctif / avec correctif | exit 0 / exit 0 |
| build Linux avec correctif | build Windows avec correctif | exit 0, projet listé |

**Retour arrière** : un build *antérieur au correctif mais de même version de schéma* refuserait sous Windows une base
créée après lui (il attend l'empreinte CRLF). Aucune version publiée n'est concernée : v1.0.0 compte 12 migrations,
v1.1.0 et v1.2.0 en comptent 15 et refusent déjà une base au schéma 20 comme plus récente ; seuls des builds de
développement 1.2.1 antérieurs au correctif le seraient (déduit de la règle d'empreinte, non exécuté).

**Cohérence documentaire** : `AdrIndexCoherenceTest` (5) et `RepositoryDocumentationCoherenceTest` (21) repassent
verts sur ce document dans son état final, ce que le § 10 laissait en suspens.

## 12. Suivi du 9 octobre 2026 — PIT-AUD-5 traité (`close-pit-survivor-gaps`)

Les 169 mutations survivantes ou non couvertes des six classes de PIT-AUD-5 ont été qualifiées une à une
(`openspec/changes/close-pit-survivor-gaps/qualification.md`), puis fermées dans la PR #414. Ce paragraphe ajoute des
mesures ; il ne réécrit pas le constat du § 5.2, qui décrit `3ec3ea46`.

**Deux défauts confirmés et corrigés**, chacun après un test rouge :

- un identifiant de fournisseur portant un saut de ligne était relu par `SqlitePortfolioStore` comme d'autres
  fournisseurs ; `ProviderId` refuse désormais les caractères de contrôle (amendement d'ADR-0023), et HTTP, MCP et la
  CLI répondent par un refus nommé ;
- `SqlitePolicyPackStore.listAudit` triait le texte de `Instant.toString()`, non le temps ; les deux stores trient
  désormais par instant puis identité. L'audit public, déjà retrié par le service, n'était pas touché : le défaut
  était une divergence au port.

**Rejeu** des lots P03, P09, P11 et X1 restreints aux six classes, mêmes options qu'au § 4.4, sur la branche fusionnée
avec `develop` (`908a410f`) et les tests de la tâche 5.1, sous WSL2 Ubuntu (JDK 21). X2 et X3 non rejoués : X3 ne mute
pas ces classes, et l'apport de X2 est désormais tué par des tests d'application.

| Classe | Avant (union de l'audit) : survivantes + non couvertes | Après (rejeu) : survivantes + non couvertes |
|---|---:|---:|
| `MultiProviderCompositionService` | 30 + 2 | 0 + 0 |
| `QueryExecutionService` | 8 + 10 | 1 + 0 |
| `NormalizedProjectContent` | 25 + 0 | 0 + 0 |
| `SyntheticJsonParser` | 26 + 17 | 2 + 0 |
| `SqlitePolicyPackStore` | 20 + 11 | 5 + 0 |
| `SqlitePortfolioStore` | 20 + 0 | 8 + 0 |
| **Total** | **129 + 40 = 169** (sur 723 mutations) | **16 + 0 = 16** (sur 715) |

Les 16 restantes sont qualifiées : onze équivalentes déjà nommées par la qualification, et cinq que l'audit comptait
détectées — un `boundedRows` qui ne peut échouer (code redondant), deux `ensureOpen` qu'une lecture suivante répète, et
les deux `Connection::close`, tués sous Windows par le verrou du fichier et inobservables sous Linux.

**Ce que le rejeu a appris sur la mesure de l'audit.** Un `TIMED_OUT` compte comme détecté dans l'union (§ 5.2), mais
il ne prouve aucune assertion : sur `QueryExecutionService`, huit mutations n'étaient « détectées » que par un
délai dépassé du lot `cli` (X2) — l'ordre d'une matérialisation complète, un plafond d'une ligne, la validation
d'une définition, une source d'exactement le budget, `boundedRows` ; sur `SqlitePolicyPackStore`, six autres ne
l'étaient que par un délai dépassé de P11. Sous Linux, ces tests vont au bout et les mutants survivent. Tous sont
désormais tués par une assertion, ou qualifiés équivalents ou redondants.
