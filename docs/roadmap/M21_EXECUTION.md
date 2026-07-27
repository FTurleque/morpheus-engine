# M21 — Production Integrity & Surface Convergence

Statut : **VALIDÉ — S0→S8 PASS Windows + Linux — PR #99 prête, merge sous autorisation explicite**

Issue : #98  
PR : #99  
Branche : `m21/production-integrity-surface-convergence`

Baseline : `main@83ad1dfc264a4797130ebd61353ce0e78552d88c` — MORPHEUS 1.0.0 publié.

Head exécutable qualifié Windows + Linux :

```text
239d99657fbf193761767f382489dd637e642fe9
```

Les commits postérieurs sont exclusivement documentaires.

## Question de sortie

> MORPHEUS 1.x possède-t-il une baseline de production durable où build, qualité, contrats publics, documentation et chaîne de release convergent sans divergence silencieuse entre CLI, MCP et HTTP ?

Réponse : **oui, démontré sur Windows et Linux sur le même head exécutable**.

## Invariants

```text
surface parity != same transport shape
read surface != write capability
release metadata != runtime business state
update discovery != automatic update
security metadata != hidden network dependency
checksum != signature
local-first remains default
no mandatory LLM in core
facts != inference
```

## Budgets / gates

```text
Java                         21+
Maven                        3.9.16+
OS qualification             Windows + Linux
full reactor                 14/14 modules SUCCESS minimum
baseline tests               >= 454 PASS
architecture                 >= 182 PASS
JaCoCo line aggregate        >= 25%
JaCoCo branch aggregate      >= 20%
public surface manifest      100% entries verified
product version convergence  CLI = MCP = HTTP
update discovery             explicit invocation only
update auto-apply            forbidden
SBOM                         CycloneDX JSON + XML
release integrity            SHA-256 + provenance manifest
post-gate executable delta   NONE
```

Résultat réel :

```text
Windows reactor              14/14 SUCCESS
Linux reactor                14/14 SUCCESS
Tests                        473 PASS
Architecture                 187 PASS
Windows JaCoCo line          46.2800%
Linux JaCoCo line            46.2430%
JaCoCo branch                41.2734% Windows + Linux
CycloneDX                    PASS JSON/XML
Provenance                   PASS Windows + Linux
Portable Windows             PASS
Portable Linux               PASS
CLI/MCP/HTTP convergence     PASS
post-gate executable delta   NONE Windows + Linux
```

Les seuils JaCoCo sont des floors de non-régression instrumentale, pas des objectifs de couverture finaux.

# DONE — M21-S0 à M21-S8

## M21-S0 — cadrage / ADR

- [x] issue canonique #98 ;
- [x] plan M21 ;
- [x] ADR-0089 proposée avant changements structurants ;
- [x] budgets gelés ;
- [x] ADR-0089 acceptée après preuve Windows + Linux.

## M21-S1 — CI durable

- [x] workflow générique `.github/workflows/ci.yml`, sans nom de milestone ;
- [x] matrice Windows/Linux + JDK 21 ;
- [x] Maven Wrapper ;
- [x] gate unique exact-head par OS ;
- [x] publication configurée des artefacts couverture/SBOM/provenance ;
- [x] incident GitHub-hosted runners documenté séparément comme infrastructure ;
- [x] validateurs canoniques réellement exécutés localement sur Windows et Linux.

## M21-S2 — couverture / quality gates

- [x] instrumentation JaCoCo sur le reactor ;
- [x] rapports XML/HTML par module ;
- [x] gate aggregate line >= 25% ;
- [x] gate aggregate branch >= 20% ;
- [x] résumé machine `m21-coverage-summary.txt` ;
- [x] parser XML compatible avec le DOCTYPE JaCoCo en bloquant la DTD externe ;
- [x] Windows : 46,2800 % lignes / 41,2734 % branches ;
- [x] Linux : 46,2430 % lignes / 41,2734 % branches.

## M21-S3 — Maven / reproductibilité

- [x] versions plugins structurants centralisées ;
- [x] `project.build.outputTimestamp` stable ;
- [x] manifestes JAR avec version produit ;
- [x] `dependency:analyze-only` lié à `verify` en diagnostic non destructif ;
- [x] warnings de dépendances visibles sans faux gate implicite ;
- [x] JaCoCo 0.8.15 ;
- [x] reactor Windows 14/14 PASS ;
- [x] reactor Linux 14/14 PASS.

## M21-S4 — convergence CLI / MCP / HTTP

- [x] manifeste machine `contracts/public-surfaces.tsv` ;
- [x] capability intent READ/WRITE explicite ;
- [x] asymétries de transport explicites ;
- [x] version produit dérivée des métadonnées de build ;
- [x] tests empêchant une divergence silencieuse ;
- [x] sérialisation canonique des URI de release ;
- [x] convergence packaged exécutée sur Windows ;
- [x] convergence packaged exécutée sur Linux.

## M21-S5 — documentation single-source-of-truth

- [x] `docs/reference/PUBLIC_SURFACES.md` pointe sur le manifeste ;
- [x] `docs/developer/PRODUCTION_INTEGRITY.md` documente les gates ;
- [x] `docs/user/PRODUCT_INTEGRITY.md` documente les surfaces produit utilisateur ;
- [x] test de cohérence documentation/version/surfaces ;
- [x] le TSV reste la source normative machine.

## M21-S6 — supply chain

- [x] CycloneDX aggregate JSON/XML ;
- [x] provenance de build explicite Windows/Linux ;
- [x] SHA-256 hérité du contrat M20 ;
- [x] politique de confiance documentée ;
- [x] signature cryptographique séparée des checksums et jamais simulée ;
- [x] runtime portable avec `java.net.http` ;
- [x] SBOM/provenance/package produits Windows ;
- [x] SBOM/provenance/package produits Linux.

## M21-S7 — update channel / version discovery

- [x] métadonnées produit centralisées dans `ProductMetadata` ;
- [x] manifest update explicite ;
- [x] source `file:`, `http:` ou `https:` uniquement sur invocation ;
- [x] aucune requête réseau au démarrage ;
- [x] aucune redirection HTTP implicite ;
- [x] manifeste borné à 64 Kio ;
- [x] comparaison version/prerelease/build metadata testée ;
- [x] aucune installation/mutation automatique ;
- [x] CLI `update-check` ;
- [x] MCP `check_product_update` ;
- [x] HTTP `EXPLICITLY_NOT_EXPOSED` pour éviter un fetcher SSRF arbitraire.

## M21-S8 — qualification exact-head

Commandes canoniques :

```powershell
.\validate-m21.cmd -Version 1.0.0
```

```bash
./scripts/validate-m21.sh 1.0.0
```

- [x] `git diff --check` ;
- [x] Windows exact-head PASS — `239d99657fbf193761767f382489dd637e642fe9` ;
- [x] Linux exact-head PASS — même SHA ;
- [x] reactor complet 14/14 sur chaque plateforme ;
- [x] tests 473 >= 454 ;
- [x] architecture 187 >= 182 ;
- [x] coverage floors franchis Windows + Linux ;
- [x] public surfaces gate PASS ;
- [x] SBOM/provenance PASS Windows + Linux ;
- [x] packaging/smokes PASS Windows + Linux ;
- [x] `VALIDATION_M21.md` contient la preuve complète ;
- [x] ADR-0089 acceptée ;
- [x] PR #99 peut passer Ready ;
- [ ] merge uniquement après autorisation explicite du propriétaire.

## Preuves exact-head

Windows :

```text
M21 VALIDATION PASS
sha=239d99657fbf193761767f382489dd637e642fe9
tests=473
architectureTests=187
lineCoverage=0.462800
branchCoverage=0.412734
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

Linux :

```text
M21 VALIDATION PASS
sha=239d99657fbf193761767f382489dd637e642fe9
tests=473
architectureTests=187
lineCoverage=0.462430
branchCoverage=0.412734
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

Le gate Linux a en plus confirmé `jpackage 21.0.11`, les smokes CLI/API/update, les modules runtime et l’archive `morpheus-1.0.0-linux-x64.tar.gz`.

## Incident GitHub Actions

Le run `30302997998` sur le head qualifié a créé les jobs Windows/Ubuntu mais ceux-ci ont échoué avant tout step (`steps=None`, aucun log). Cet incident reste classé **runner startup / infrastructure unavailable**. Il est distinct des qualifications locales exact-head effectivement exécutées et vertes.

## Fichiers M21 principaux

```text
.github/workflows/ci.yml
contracts/public-surfaces.tsv
docs/adr/0089-production-integrity-surface-convergence.md
docs/developer/PRODUCTION_INTEGRITY.md
docs/reference/PUBLIC_SURFACES.md
docs/roadmap/M21_EXECUTION.md
docs/user/PRODUCT_INTEGRITY.md
docs/validation/VALIDATION_M21.md
scripts/validate-m21.ps1
scripts/validate-m21.sh
scripts/write-build-provenance.ps1
scripts/write-build-provenance.sh
validate-m21.cmd
```

M21 est **techniquement terminé**. Il reste uniquement l’acte de gouvernance de merge de la PR #99, soumis à l’autorisation explicite du propriétaire.