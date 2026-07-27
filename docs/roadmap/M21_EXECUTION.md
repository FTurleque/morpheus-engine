# M21 — Production Integrity & Surface Convergence

Statut : **IMPLÉMENTÉ — QUALIFICATION EXACT-HEAD BLOQUÉE PAR LE DÉMARRAGE DES RUNNERS GITHUB ACTIONS** — issue #98 — PR #99 — branche `m21/production-integrity-surface-convergence`

Baseline : `main@83ad1dfc264a4797130ebd61353ce0e78552d88c` — MORPHEUS 1.0.0 publié.

Dernier head exécutable candidat avant consolidation documentaire : `a7508fbfc22f0a0b65b1e3a9095769e7d410e340`.

## Question de sortie

> MORPHEUS 1.x possède-t-il une baseline de production durable où build, qualité, contrats publics, documentation et chaîne de release convergent sans divergence silencieuse entre CLI, MCP et HTTP ?

Réponse : **pas encore démontrée**. Les slices S0→S7 sont implémentées. S8 reste ouverte car les jobs GitHub Actions Windows et Ubuntu sont créés puis échouent avant tout step, sans log exécutable ; aucun résultat Maven/JaCoCo/packaging ne peut donc être présenté comme PASS.

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

## Budgets / gates gelés avant implémentation

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

Les seuils JaCoCo M21 sont des **floors de non-régression instrumentale**, pas des objectifs de couverture finaux. Ils doivent être relevés dans les jalons ultérieurs à partir de tendances réelles.

# DONE — implémentation

## M21-S0 — cadrage / ADR

- [x] issue canonique #98 ;
- [x] plan M21 ;
- [x] ADR-0089 proposée avant changements structurants ;
- [x] budgets gelés.

## M21-S1 — CI durable

- [x] workflow générique `.github/workflows/ci.yml`, sans nom de milestone ;
- [x] matrice Windows/Linux + JDK 21 ;
- [x] Maven Wrapper ;
- [x] gate unique exact-head par OS ;
- [x] publication configurée des artefacts couverture/SBOM/provenance ;
- [ ] exécution effective des steps GitHub-hosted — **BLOQUÉE INFRASTRUCTURE**.

## M21-S2 — couverture / quality gates

- [x] instrumentation JaCoCo sur le reactor ;
- [x] rapports XML/HTML par module ;
- [x] gate aggregate line >= 25% ;
- [x] gate aggregate branch >= 20% ;
- [x] résumé machine lisible `m21-coverage-summary.txt` ;
- [x] parser XML compatible avec le DOCTYPE JaCoCo tout en bloquant le chargement DTD externe ;
- [ ] seuils réellement franchis sur Windows/Linux — **à prouver en S8**.

## M21-S3 — Maven / reproductibilité

- [x] versions plugins structurants centralisées ;
- [x] `project.build.outputTimestamp` stable ;
- [x] manifestes JAR avec version produit ;
- [x] `dependency:analyze-only` lié à `verify` en diagnostic non destructif ;
- [x] warnings de dépendances visibles sans faux gate implicite ;
- [x] JaCoCo 0.8.15 ;
- [ ] résultat réel du reactor — **à prouver en S8**.

## M21-S4 — convergence CLI / MCP / HTTP

- [x] manifeste machine `contracts/public-surfaces.tsv` ;
- [x] capability intent explicite READ/WRITE ;
- [x] asymétries de transport explicites ;
- [x] version produit dérivée des métadonnées de build ;
- [x] tests empêchant une divergence silencieuse ;
- [x] sérialisation canonique des URI de release ;
- [ ] convergence packaged CLI/MCP/HTTP réellement exécutée — **à prouver en S8**.

## M21-S5 — documentation single-source-of-truth

- [x] `docs/reference/PUBLIC_SURFACES.md` pointe sur le manifeste ;
- [x] `docs/developer/PRODUCTION_INTEGRITY.md` documente les gates ;
- [x] `docs/user/PRODUCT_INTEGRITY.md` documente les surfaces produit utilisateur ;
- [x] test de cohérence documentation/version/surfaces ;
- [x] absence de duplication normative volontaire : le TSV reste source machine.

## M21-S6 — supply chain

- [x] CycloneDX aggregate JSON/XML configuré ;
- [x] provenance de build explicite Windows/Linux ;
- [x] SHA-256 des artefacts de release hérité et conservé du contrat M20 ;
- [x] politique de confiance documentée ;
- [x] signature cryptographique séparée des checksums et non simulée en l’absence de clé ;
- [x] runtime portable enrichi avec `java.net.http` requis par M21 ;
- [ ] SBOM/provenance/package réellement produits sur les deux OS — **à prouver en S8**.

## M21-S7 — update channel / version discovery

- [x] métadonnées produit centralisées dans `ProductMetadata` ;
- [x] manifest update explicite ;
- [x] source `file:`, `http:` ou `https:` uniquement sur invocation ;
- [x] aucune requête réseau au démarrage par contrat + test de sites de construction ;
- [x] aucune redirection HTTP implicite ;
- [x] manifeste borné à 64 Kio ;
- [x] comparaison version/prerelease et build metadata testée ;
- [x] aucune installation/mutation automatique ;
- [x] CLI `update-check` ;
- [x] MCP `check_product_update` ;
- [x] HTTP `EXPLICITLY_NOT_EXPOSED` explicitement documenté pour ne pas créer un fetcher SSRF arbitraire.

# BLOCKED — gate final

## M21-S8 — qualification exact-head

Commandes canoniques :

```powershell
.\validate-m21.cmd -Version 1.0.0
```

```bash
./scripts/validate-m21.sh 1.0.0
```

Le même gate est appelé par `.github/workflows/ci.yml`.

- [ ] `git diff --check` ;
- [ ] Windows exact-head PASS ;
- [ ] Linux exact-head PASS ;
- [ ] reactor complet PASS ;
- [ ] tests >= baseline ;
- [ ] architecture >= baseline ;
- [ ] coverage gate PASS ;
- [ ] public surfaces gate PASS ;
- [ ] SBOM/provenance PASS ;
- [ ] packaging/smokes pertinents PASS ;
- [x] `VALIDATION_M21.md` créée avec état factuel du blocage ;
- [ ] `VALIDATION_M21.md` convertie en preuve PASS avec SHA réel ;
- [ ] ADR-0089 acceptée seulement après preuve ;
- [ ] PR Ready seulement après gate vert ;
- [ ] merge uniquement après autorisation explicite du propriétaire.

## Blocage GitHub Actions observé

Le run `30298669189` du candidat exécutable `a7508fbfc22f0a0b65b1e3a9095769e7d410e340` crée bien les deux jobs `exact-head (windows-latest)` et `exact-head (ubuntu-latest)`, mais chacun termine `failure` avec `steps: None` et sans URL de logs de job. Les changements de versions `actions/checkout/setup-java/upload-artifact`, le checkout explicite du SHA de tête et une relance manuelle n’ont pas modifié ce comportement. Ce symptôme est donc classé **runner startup / infrastructure**, et non `Maven FAIL`.

Aucun PASS Windows/Linux ne sera déclaré tant qu’un des validateurs ci-dessus n’aura pas réellement exécuté le reactor et les smokes.

## Fichiers M21

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
