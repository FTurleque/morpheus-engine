# VALIDATION M21 — Production Integrity & Surface Convergence

Statut : **PARTIELLEMENT VALIDÉE — Windows exact-head PASS ; Linux bloqué avant exécution par l’infrastructure GitHub Actions**

Date : 27 juillet 2026

Issue : #98
PR : #99
Branche : `m21/production-integrity-surface-convergence`

Baseline M20 : `83ad1dfc264a4797130ebd61353ce0e78552d88c` (`main`, MORPHEUS 1.0.0 publié).

Head exécutable qualifié Windows :

```text
239d99657fbf193761767f382489dd637e642fe9
```

## Verdict

```text
M21 implementation S0-S7     IMPLEMENTED
M21 exact-head gate S8       PARTIAL
Windows reactor              PASS — 14/14
Linux reactor                NOT EXECUTED / NOT PROVEN
Tests >= 454                 PASS — 473
Architecture >= 182          PASS — 187
JaCoCo line >= 25%           PASS — 46.2800%
JaCoCo branch >= 20%         PASS — 41.2734%
CycloneDX SBOM               PASS — JSON/XML
Build provenance             PASS
Portable Windows             PASS
Portable Linux               NOT PROVEN
CLI/MCP/HTTP convergence     PASS ON WINDOWS PACKAGED RUNTIME
ADR-0089                     PROPOSED
PR #99                       DRAFT
Merge                        NOT AUTHORIZED / NOT ELIGIBLE
```

M21 **ne peut pas encore être déclaré terminé** : la preuve Windows est complète, mais la qualification Linux exigée par les budgets M21 n’a pas exécuté un seul step sur GitHub Actions.

## Preuve Windows exact-head

Commande canonique exécutée localement sur Windows :

```powershell
.\validate-m21.cmd -Version 1.0.0
```

Résultat exact :

```text
M21 VALIDATION PASS
sha=239d99657fbf193761767f382489dd637e642fe9
baseRef=origin/main
version=1.0.0
tests=473
architectureTests=187
lineCoverage=0.462800
branchCoverage=0.412734
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

Le gate Windows a réellement exécuté et validé :

- `git diff --check` ;
- reactor Maven complet 14/14 ;
- 473 tests ;
- 187 tests d’architecture ;
- JaCoCo lignes 46,28 % et branches 41,2734 % ;
- CycloneDX JSON/XML ;
- provenance de build ;
- packaging shaded JAR ;
- `jpackage` Windows autonome ;
- version CLI `1.0.0` ;
- `product-info` ;
- MINOS/NEXUS optionnels et désactivés sans configuration ;
- smoke CLI M14→M21 ;
- API packagée health/readiness/metrics/version ;
- runtime embarqué contenant `jdk.httpserver`, `java.sql`, `java.net.http` ;
- archive `morpheus-1.0.0-windows-x64.zip` ;
- convergence CLI/update/API ;
- absence de delta exécutable post-gate.

## Ce qui est implémenté

### Build / qualité

- CI générique `.github/workflows/ci.yml` ;
- matrice `windows-latest` / `ubuntu-latest`, Java 21 ;
- validateurs exact-head uniques Windows/Linux ;
- JaCoCo 0.8.15 reactor + floor aggregate 25% lignes / 20% branches ;
- parser de rapports compatible avec le DOCTYPE JaCoCo tout en bloquant les DTD externes ;
- résumé machine `m21-coverage-summary.txt` ;
- `maven-dependency-plugin:analyze-only` en diagnostic non bloquant ;
- `project.build.outputTimestamp` ;
- manifests JAR versionnés ;
- versions de plugins structurants centralisées ;
- `git diff --check` porte sur le delta complet `base...HEAD`, pas seulement sur le worktree courant.

### Convergence des surfaces

- `ProductMetadata` comme primitive application-level de métadonnées produit ;
- version MCP dérivée de la même métadonnée de build ;
- CLI `version` / `product-info` ;
- manifeste `contracts/public-surfaces.tsv` ;
- intent READ/WRITE explicite ;
- asymétries de transport explicites ;
- tests de cohérence documentation/surfaces ;
- sérialisation canonique de `URI` ajoutée pour les résultats M21.

### Supply chain

- CycloneDX aggregate JSON/XML ;
- provenance de build Windows/Linux ;
- checksums SHA-256 M20 conservés ;
- politique `checksum != signature` ;
- aucune fausse signature sans identité/clé réelle ;
- runtime `jpackage` Windows/Linux enrichi avec `java.net.http`.

### Update discovery

- manifeste explicite : `version`, `channel`, `artifactUri`, `sha256` ;
- `file:`, `http:`, `https:` ;
- pas de redirection HTTP implicite ;
- manifeste plafonné à 64 Kio ;
- comparaison version/prerelease et build metadata testée ;
- pas d’I/O réseau implicite au démarrage ;
- invocation explicite CLI/MCP seulement ;
- aucun download/install/replace ;
- aucun auto-update ;
- absence HTTP déclarée `EXPLICITLY_NOT_EXPOSED` afin de ne pas créer un fetcher d’URI arbitraire dans l’API locale.

## Gate canonique

Windows :

```powershell
.\validate-m21.cmd -Version 1.0.0
```

Linux :

```bash
./scripts/validate-m21.sh 1.0.0
```

Chaque gate doit exécuter réellement :

```text
git diff --check base...HEAD
full Maven clean verify
Surefire total >= 454
architecture >= 182
JaCoCo line >= 25%
JaCoCo branch >= 20%
CycloneDX JSON + XML
build provenance
portable jpackage
packaged product-info
explicit local update-check
packaged HTTP /api/v1/version == 1.0.0
exact HEAD unchanged
tracked workspace unchanged
```

## GitHub Actions — blocage Linux observé

Plusieurs runs M21 ont été déclenchés pendant l’implémentation. Le symptôme est stable : les jobs de matrice sont créés puis terminent `failure` **avant tout step**. Le connecteur GitHub retourne `steps: None` et aucune URL de logs de job.

Runs observés, entre autres :

```text
30296855276  failure
30297207829  failure
30297510547  failure
30297658567  failure
30297801921  failure
30298020147  failure + rerun failure before steps
30298106004  failure
30298669189  failure
30302997998  failure
```

Run observé sur le head Windows qualifié `239d99657fbf193761767f382489dd637e642fe9` :

```text
run 30302997998
exact-head (windows-latest)  failure | steps=None | logs_url=None
exact-head (ubuntu-latest)   failure | steps=None | logs_url=None
```

Des essais ont déjà éliminé plusieurs causes de workflow simples :

- retour aux actions `checkout/setup-java/upload-artifact` v4, cohérentes avec les workflows historiques du dépôt ;
- checkout explicite de `${{ github.event.pull_request.head.sha || github.sha }}` au lieu du merge-ref de PR ;
- relance manuelle des jobs échoués.

Le résultat reste identique. L’incident est donc enregistré comme **runner startup / infrastructure unavailable**. Aucun message Maven, test, JaCoCo ou packaging n’a été produit par ces jobs.

## Audit statique complémentaire avant gel

L’audit M21 a identifié et corrigé avant la qualification Windows :

```text
URI canonical JSON support              FIXED
java.net.http jpackage module           FIXED
JaCoCo external DTD parser issue        FIXED
branch-wide git diff --check            FIXED
update manifest size bound              FIXED (64 KiB)
prerelease ordering                     FIXED / TESTED
public-surfaces TSV shape               FIXED / TESTED
Windows Maven gate invocation           FIXED
Windows provenance no-tag handling      FIXED
Windows native stderr handling          FIXED
JaCoCo stable version                   0.8.15
```

Ces corrections sont maintenant couvertes par le PASS Windows exact-head. Elles ne constituent toutefois pas une preuve Linux.

## Condition de déblocage

La preuve M21 devient complète uniquement après :

```text
Windows exact-head  PASS — acquis sur 239d99657fbf193761767f382489dd637e642fe9
Linux exact-head    PASS — restant
```

À ce moment seulement :

1. reporter le SHA Linux et ses compteurs réels dans ce document ;
2. vérifier `post-gate executable delta = NONE` ;
3. passer ADR-0089 de `Proposée` à `Acceptée — M21` ;
4. passer PR #99 de Draft à Ready ;
5. demander/appliquer l’autorisation de merge conformément à la gouvernance.

Toute consolidation documentaire postérieure au SHA Windows qualifié doit rester **docs-only** ; elle ne remplace pas le SHA exécutable prouvé.

## Intégrité de la preuve

```text
facts != inference
workflow exists != workflow passed
job created != job executed
configured gate != successful gate
Windows PASS != Linux PASS
checksum != signature
update discovery != automatic update
```

Cette validation est volontairement factuelle : **Windows est prouvé vert ; Linux reste non exécuté à cause du démarrage des runners GitHub Actions**.
