# VALIDATION M21 — Production Integrity & Surface Convergence

Statut : **BLOQUÉE — aucune preuve exact-head Windows/Linux exécutable disponible**

Date : 27 juillet 2026

Issue : #98  
PR : #99  
Branche : `m21/production-integrity-surface-convergence`

Baseline M20 : `83ad1dfc264a4797130ebd61353ce0e78552d88c` (`main`, MORPHEUS 1.0.0 publié).

Dernier head exécutable candidat avant consolidation documentaire :

```text
a7508fbfc22f0a0b65b1e3a9095769e7d410e340
```

## Verdict

```text
M21 implementation S0-S7     IMPLEMENTED
M21 exact-head gate S8       BLOCKED
Windows reactor              NOT EXECUTED / NOT PROVEN
Linux reactor                NOT EXECUTED / NOT PROVEN
Tests >= 454                 NOT PROVEN
Architecture >= 182          NOT PROVEN
JaCoCo line >= 25%           NOT PROVEN
JaCoCo branch >= 20%         NOT PROVEN
CycloneDX SBOM               CONFIGURED / NOT PROVEN
Build provenance             CONFIGURED / NOT PROVEN
Portable Windows             NOT PROVEN
Portable Linux               NOT PROVEN
CLI/MCP/HTTP convergence     IMPLEMENTED / RUNTIME PROOF PENDING
ADR-0089                     PROPOSED
PR #99                       DRAFT
Merge                        NOT AUTHORIZED / NOT ELIGIBLE
```

M21 **ne peut pas être déclaré terminé ni vert** sur cette preuve.

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

## GitHub Actions — blocage observé

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
```

Dernier run observé sur le candidat exécutable `a7508fbfc22f0a0b65b1e3a9095769e7d410e340` :

```text
run 30298669189
exact-head (windows-latest)  failure | steps=None | logs_url=None
exact-head (ubuntu-latest)   failure | steps=None | logs_url=None
```

Des essais ont déjà éliminé plusieurs causes de workflow simples :

- retour aux actions `checkout/setup-java/upload-artifact` v4, cohérentes avec les workflows historiques du dépôt ;
- checkout explicite de `${{ github.event.pull_request.head.sha || github.sha }}` au lieu du merge-ref de PR ;
- relance manuelle des jobs échoués.

Le résultat reste identique. L’incident est donc enregistré comme **runner startup / infrastructure unavailable**. Aucun message Maven, test, JaCoCo ou packaging n’a été produit par ces jobs.

## Audit statique complémentaire avant gel

L’audit M21 a identifié et corrigé avant le gel documentaire :

```text
URI canonical JSON support         FIXED
java.net.http jpackage module      FIXED
JaCoCo external DTD parser issue   FIXED
branch-wide git diff --check       FIXED
update manifest size bound         FIXED (64 KiB)
prerelease ordering                FIXED / TESTED IN SOURCE
JaCoCo stable version              0.8.15
```

Ces corrections réduisent les risques évidents, mais **un audit statique ne remplace pas une compilation ni un gate exact-head**.

## Condition de déblocage

La preuve M21 devient éligible uniquement après exécution complète du gate sur :

```text
Windows exact-head  PASS
Linux exact-head    PASS
```

À ce moment seulement :

1. reporter les compteurs réels et SHA exacts dans ce document ;
2. vérifier `post-gate executable delta = NONE` ;
3. passer ADR-0089 de `Proposée` à `Acceptée — M21` ;
4. passer PR #99 de Draft à Ready ;
5. demander/appliquer l’autorisation de merge conformément à la gouvernance.

## Intégrité de la preuve

```text
facts != inference
workflow exists != workflow passed
job created != job executed
configured gate != successful gate
static audit != compilation proof
checksum != signature
update discovery != automatic update
```

Cette validation est volontairement **incomplète et honnête** : elle décrit précisément ce qui est implémenté et ce qui n’a pas pu être exécuté.
