# VALIDATION M21 — Production Integrity & Surface Convergence

Statut : **VALIDÉE — Windows + Linux exact-head PASS**

Date : 27 juillet 2026

Issue : #98  
PR : #99  
Branche : `m21/production-integrity-surface-convergence`

Baseline M20 : `83ad1dfc264a4797130ebd61353ce0e78552d88c` (`main`, MORPHEUS 1.0.0 publié).

Head exécutable qualifié sur les deux plateformes :

```text
239d99657fbf193761767f382489dd637e642fe9
```

Les commits postérieurs au head qualifié sont exclusivement documentaires. La comparaison GitHub `239d996... → branche M21` ne contenait avant consolidation finale que `docs/roadmap/M21_EXECUTION.md` et `docs/validation/VALIDATION_M21.md`; la finalisation ajoute uniquement des documents de gouvernance/ADR et ne modifie aucun code, POM, script, contrat machine ou packaging.

## Verdict

```text
M21 implementation S0-S7     PASS
M21 exact-head gate S8       PASS
Windows reactor              PASS — 14/14
Linux reactor                PASS — 14/14
Tests >= 454                 PASS — 473
Architecture >= 182          PASS — 187
JaCoCo line >= 25%           PASS — Windows 46.2800% / Linux 46.2430%
JaCoCo branch >= 20%         PASS — Windows/Linux 41.2734%
CycloneDX SBOM               PASS — JSON/XML
Build provenance             PASS
Portable Windows             PASS
Portable Linux               PASS
CLI/MCP/HTTP convergence     PASS
post-gate executable delta   NONE — Windows + Linux
ADR-0089                     ACCEPTED — M21
PR #99                       READY AFTER DOC CONSOLIDATION
Merge                        REQUIRES EXPLICIT OWNER AUTHORIZATION
```

La question de sortie M21 reçoit donc une réponse **oui** : MORPHEUS 1.x possède une baseline de production démontrée sur Windows et Linux où build, qualité, contrats publics, documentation et chaîne de release convergent sans divergence silencieuse entre CLI, MCP et HTTP.

## Preuve Windows exact-head

Commande canonique :

```powershell
.\validate-m21.cmd -Version 1.0.0
```

Résumé produit par le gate :

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

Le gate Windows a réellement validé :

- `git diff --check base...HEAD` ;
- reactor Maven complet 14/14 ;
- 473 tests ;
- 187 tests d’architecture ;
- JaCoCo lignes 46,28 % et branches 41,2734 % ;
- CycloneDX JSON/XML ;
- provenance de build ;
- shaded JAR ;
- `jpackage` Windows autonome ;
- CLI `--version` / `product-info` ;
- update discovery explicite ;
- MINOS/NEXUS optionnels et désactivés sans configuration ;
- API packagée health/readiness/metrics/version ;
- runtime embarqué `jdk.httpserver`, `java.sql`, `java.net.http` ;
- archive `morpheus-1.0.0-windows-x64.zip` ;
- convergence CLI/update/API ;
- absence de delta exécutable post-gate.

## Preuve Linux exact-head

Environnement : **WSL2 Linux**, OpenJDK 21 avec `jpackage 21.0.11`.

Commande canonique exécutée dans Linux :

```bash
./scripts/validate-m21.sh 1.0.0
```

Le lancement depuis PowerShell a explicitement vérifié le SHA, supprimé tout ancien résumé, conservé le log, contrôlé les marqueurs de fin et refusé tout faux positif.

Résumé produit par le gate Linux :

```text
M21 VALIDATION PASS
sha=239d99657fbf193761767f382489dd637e642fe9
baseRef=origin/main
version=1.0.0
tests=473
architectureTests=187
lineCoverage=0.462430
branchCoverage=0.412734
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

Sortie complémentaire observée :

```text
Reactor Summary for MORPHEUS 1.0.0: 14/14 SUCCESS
BUILD SUCCESS
Tests: PASS (473, baseline >= 454)
Architecture: PASS (187, baseline >= 182)
JaCoCo: PASS (line=0.462430, branch=0.412734)
Supply chain: PASS (CycloneDX JSON/XML + provenance)
MCP/API/MINOS/NEXUS/M14-M21 packaging proof: PASS
Packaged standalone optional-engines + M14-M21 CLI surface smoke: PASS
Packaged API health/readiness/metrics/version smoke: PASS
Packaged jdk.httpserver + java.sql + java.net.http module proof: PASS
Portable Linux distribution: validation-output/m21/dist/morpheus-1.0.0-linux-x64.tar.gz
Packaged CLI/update/API convergence: PASS
M21 VALIDATION PASS
M21 LINUX EXIT CODE: 0
```

Le wrapper de qualification a ensuite relu un `validation-summary.txt` généré après suppression de l’ancien fichier et a vérifié les marqueurs :

```text
M21 exact-head validation SHA: 239d99657fbf193761767f382489dd637e642fe9
BUILD SUCCESS
Portable Linux distribution:
M21 VALIDATION PASS
postGateExecutableDelta=NONE
```

La preuve Linux est donc indépendante du résumé Windows et qualifie réellement le même head exécutable.

## Ce qui est implémenté

### Build / qualité

- CI générique `.github/workflows/ci.yml` ;
- matrice `windows-latest` / `ubuntu-latest`, Java 21 ;
- validateurs exact-head uniques Windows/Linux ;
- JaCoCo 0.8.15 reactor + floors 25 % lignes / 20 % branches ;
- parser de rapports compatible avec le DOCTYPE JaCoCo en bloquant les DTD externes ;
- résumé machine `m21-coverage-summary.txt` ;
- `maven-dependency-plugin:analyze-only` diagnostic non bloquant ;
- `project.build.outputTimestamp` ;
- manifests JAR versionnés ;
- versions de plugins structurants centralisées ;
- `git diff --check` sur le delta complet `base...HEAD`.

### Convergence des surfaces

- `ProductMetadata` comme primitive application-level de métadonnées produit ;
- version MCP dérivée de la même métadonnée de build ;
- CLI `version` / `product-info` ;
- manifeste `contracts/public-surfaces.tsv` ;
- intent READ/WRITE explicite ;
- asymétries de transport explicites ;
- tests de cohérence documentation/surfaces ;
- sérialisation canonique de `URI`.

### Supply chain

- CycloneDX aggregate JSON/XML ;
- provenance de build Windows/Linux ;
- SHA-256 des releases conservés ;
- politique `checksum != signature` ;
- aucune fausse signature sans identité/clé réelle ;
- runtime `jpackage` Windows/Linux avec `java.net.http`.

### Update discovery

- manifeste explicite `version`, `channel`, `artifactUri`, `sha256` ;
- `file:`, `http:`, `https:` ;
- pas de redirection HTTP implicite ;
- manifeste plafonné à 64 Kio ;
- comparaison version/prerelease/build metadata testée ;
- pas d’I/O réseau implicite au démarrage ;
- invocation explicite CLI/MCP seulement ;
- aucun download/install/replace ;
- aucun auto-update ;
- absence HTTP déclarée `EXPLICITLY_NOT_EXPOSED` afin de ne pas créer un fetcher d’URI arbitraire dans l’API locale.

## Incident GitHub Actions conservé comme fait historique

Les GitHub-hosted runners ont échoué à plusieurs reprises avant tout step, notamment sur le run `30302997998` associé au head exécutable qualifié :

```text
exact-head (windows-latest)  failure | steps=None | logs_url=None
exact-head (ubuntu-latest)   failure | steps=None | logs_url=None
```

Ce symptôme reste classé **runner startup / infrastructure unavailable**. Il ne remet pas en cause les deux qualifications locales exact-head, exécutées réellement sur Windows et Linux avec les validateurs canoniques.

## Audit statique corrigé avant qualification

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

## Intégrité de la preuve

```text
facts != inference
workflow exists != workflow passed
job created != job executed
configured gate != successful gate
Windows PASS != Linux PASS
checksum != signature
update discovery != automatic update
qualified executable head != later docs-only head
```

M21 est **techniquement validé**. La seule action de gouvernance restante avant intégration est le merge de la PR #99, explicitement autorisé par le propriétaire.