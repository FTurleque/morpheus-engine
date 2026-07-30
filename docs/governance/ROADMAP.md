# Feuille de route — MORPHEUS

Statut : **C0 à M27 + D0 + D1 intégrés — MORPHEUS 1.0.0 publié — R2 / 1.1.0 actif**

Dernière mise à jour : 30 juillet 2026

MORPHEUS est piloté par des preuves : contrats stables, ADR cohérentes, tests reproductibles, SHA exacts et réponse explicite à chaque question de sortie.

La trajectoire fonctionnelle 1.x est [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md). Le plan de stabilisation actif est [`R2_EXECUTION.md`](../roadmap/R2_EXECUTION.md).

## 1. Politique de branches

La branche d'intégration de travail est **`develop`**.

```text
feature / milestone branch -> develop
release branch             -> main après qualification
main                       -> stabilisation / livraison
```

Les nouveaux jalons fonctionnels sont basés sur `develop` et ciblent `develop`. Une branche de release peut être basée sur le head exact de `develop` et cibler `main` lorsque son périmètre, ses gates et son SHA sont explicitement tracés.

R2 utilise :

```text
source baseline   develop@bccc118dda6fd818cf801750187afa4ad10b96e4
release branch    r2-release-1.1.0
target branch     main
issue             #113
PR                #114 DRAFT
```

## 2. Baseline intégrée et publiée

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21           ✅ validé et intégré
M22           ✅ validé et intégré
M23           ✅ validé et intégré
M24           ✅ validé et intégré
M25           ✅ validé et intégré dans develop
M26           ✅ validé et intégré dans develop
M27           ✅ validé et intégré dans develop
R2            🚧 MORPHEUS 1.1.0 en préparation
```

Références :

```text
M20 merge          75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 / release SHA   51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 merge          2fdce6601a07628c315fe03932750cd8ece3d777
M22 merge          67c587057e287d57b0733f9e425a57b26cc38ae4
M23 merge          88355b69c493677c8689eecad214fb00d283359b
M24 merge          2b483ded10c783fff22c25035db89475c5c9fdaf
M25 exact head     a392604fc9e8d00f4021351ab5ba53f8488ab920
M25 merge          62bf0ea37f732116e821df7d98ae89d36c6dd75d
M26 exact head     bf481b24054c4577144b4cb2ede2bdbc4d9974a2
M26 merge          49016a18c844a78ec864235c544d82d487da7c8a
M27 exact head     f97307c878125550693699124ca717f64f305a3a
M27 merge          f8810803bd5ae7d57c4858e1e384c6a0132e1a45
develop final      bccc118dda6fd818cf801750187afa4ad10b96e4
stable version     1.0.0
stable tag         v1.0.0
candidate version  1.1.0
candidate tag      v1.1.0 — NOT CREATED
```

## 3. Référence M27 intégrée

```text
Issue                #111 CLOSED / completed
PR                   #112 MERGED dans develop
Baseline             develop@c1eb1e74afe92db8b4a9250b678ce7d0d5c99ca7
Head exact qualifié  f97307c878125550693699124ca717f64f305a3a
Head PR docs-only    026c1d5f8671cd7b879fa89d51af8e83a5f06272
Merge                f8810803bd5ae7d57c4858e1e384c6a0132e1a45
Windows reactor      17/17 SUCCESS
Linux reactor        17/17 SUCCESS
Tests                602 PASS Windows + Linux
Architecture         238 PASS Windows + Linux
Windows JaCoCo       45.2226% line / 38.4456% branch
Linux JaCoCo         45.2246% line / 38.4456% branch
Facts / claims       séparation PASS
Confidence           bornée + explicite PASS
Evidence             citations + provenance PASS
Adapters             optionnels + fault isolation PASS
No silent mutation   PASS / mutated=false
CLI/MCP/HTTP         convergence PASS
Remote READ RBAC     PASS
CycloneDX            PASS JSON/XML
Provenance           PASS Windows + Linux
Portable             PASS Windows + Linux
Packaged smokes      PASS Windows + Linux
Executable delta     NONE Windows + Linux
ADR-0095             Acceptée — M27
```

Preuve : [`VALIDATION_M27.md`](../validation/VALIDATION_M27.md).

## 4. R2 — Stabilisation et publication MORPHEUS 1.1.0

Question de sortie :

> Les évolutions M21 à M27 peuvent-elles être consolidées dans `main` et publiées comme MORPHEUS 1.1.0 avec des artefacts reproductibles, une qualification exacte Windows/Linux et une traçabilité complète de release ?

État : **EN COURS — aucun PASS R2 déclaré**.

```text
Issue                  #113 OPEN
PR                     #114 DRAFT vers main
Branch                 r2-release-1.1.0
Release baseline       develop@bccc118dda6fd818cf801750187afa4ad10b96e4
Target version         1.1.0
Target tag             v1.1.0
Qualification SHA      NOT SET
Windows gate           NOT RUN
Linux/WSL gate         NOT RUN
Merge main             NOT AUTHORIZED
Tag                    NOT AUTHORIZED
GitHub Release         NOT CREATED
```

Périmètre :

```text
Maven reactor 1.1.0
upgrade SQLite V012 -> V015
Windows setup per-user
Windows/Linux portable
runtime Java embarqué
SHA-256 + manifests
CycloneDX + provenance
packaged smokes M25/M26/M27
release notes + upgrade guide
exact-tag builds
GitHub Release stable / 8 assets
```

Commandes prévues :

```powershell
.\validate-r2.cmd -Version 1.1.0
```

```bash
bash ./scripts/validate-r2.sh 1.1.0
```

Plan : [`R2_EXECUTION.md`](../roadmap/R2_EXECUTION.md).  
Preuve en construction : [`VALIDATION_R2.md`](../validation/VALIDATION_R2.md).  
Notes candidates : [`RELEASE_NOTES_1.1.0.md`](../release/RELEASE_NOTES_1.1.0.md).  
Upgrade : [`UPGRADE_1_1.md`](../user/UPGRADE_1_1.md).

## 5. Capacités acquises

```text
modèle de domaine provider-neutral
identité persistante stable
CURRENT / PROPOSED / HISTORICAL
KnowledgeSnapshot + SpecificationVersion
published history + rollback logique
RequirementDelta apply/promote/activate explicites
traçabilité typée + traversal bornée
requêtes métier + vues compactes
qualité / couverture / diagnostics
synchronisation incrémentale + freshness
change analysis
acceptance / verification / evidence
constraint semantics + blocking policy
controlled lifecycle write + CAS/idempotency/audit
OpenSpec + Structured Markdown providers réels
composition multi-provider + provenance + conflits
Provider SDK v1 + plugins externes
portfolio registry provider-neutral
cross-project references + traversal bornée
Memory + SQLite V013 portfolio
Query DSL provider-neutral typé
filter/sort/projection/pagination déterministes
saved views versionnées + CAS
Memory + SQLite V014 saved views
canonical JSON + CSV + Markdown reporting
query/export/saved-view budgets
Policy Packs provider-neutral
policy identities + immutable versions
project / portfolio policy scopes
explicit applicability + PASS/WARN/BLOCK/UNKNOWN
policy overrides + provenance + CAS
policy dry-run read-only
policy audit append-only
Memory + SQLite V015 policies
local-first API loopback
optional remote HTTPS server
Bearer auth + hash-only credential persistence
READ / WRITE / ADMIN RBAC
bounded remote concurrency + HTTP 429
remote observability without secrets
SQLite backup / verify / offline restore
evidence envelopes provider-neutral
PUBLISHED_FACT séparé des claims
INFERENCE / HEURISTIC / SUGGESTION séparées
confidence explicite et bornée
reasoning adapters optionnels
adaptateur local déterministe sans LLM
failure isolation sans perte des faits
reasoning read-only / mutated=false
CLI / MCP STDIO / HTTP API
setup Windows per-user
archives portables Windows/Linux avec runtime Java
CycloneDX + build provenance
```

## 6. Responsabilités et invariants

```text
MORPHEUS = specification facts
           + intent
           + lifecycle rules
           + controlled state invariants
           + provider composition facts
           + portfolio specification facts
           + provider-neutral query/view/reporting contracts
           + provider-neutral governance policy contracts
           + optional remote/team access boundary
           + evidence-backed assisted claims separated from published facts

MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
provider identifier != DomainIdentity
source path != identity
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Evidence != assertion
UNKNOWN != FAILED
UNKNOWN != BLOCKED
applicable != blocking
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
stale revision != overwrite
idempotent retry != duplicate mutation/audit
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
plugin discovery != plugin activation
probe != read
cross-project identity != source path
project identity != workspace path
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
DSL != SQL passthrough
saved view != materialized truth
export != mutation
bounded query != silently truncated semantics
constraint text != executable policy
severity != blocking policy
policy recommendation != applied mutation
policy version != mutable latest
policy override != provenance erasure
dry-run != mutation
local mode remains first-class
remote mode is opt-in
non-loopback bind requires remote mode
remote mode requires TLS + authentication
authentication != authorization
READ != WRITE != ADMIN
token plaintext != persisted credential
backup != live restore
restore != implicit migration
server state != provider source of truth
facts != inference
inference != suggestion
heuristic != published fact
confidence is explicit and bounded
adapter discovery != adapter execution
adapter absence != MORPHEUS failure
adapter failure != fact loss
reasoning execution != lifecycle mutation
release tag != development branch
qualified SHA == packaged SHA
Windows qualified SHA == Linux qualified SHA
post-gate executable delta == NONE
checksum != signature
```

## 7. Trajectoire

### DONE

| Sujet | Statut | Résultat |
|---|---|---|
| **D1** | ✅ TERMINÉ / INTÉGRÉ — #94 / PR #95 | Baseline documentaire 1.x consolidée |
| **R1** | ✅ PUBLIÉ — #96 | Tag `v1.0.0` + GitHub Release |
| **M21** | ✅ TERMINÉ / INTÉGRÉ — #98 / PR #99 | Production Integrity & Surface Convergence |
| **M22** | ✅ TERMINÉ / INTÉGRÉ — #100 / PR #101 | Provider SDK & Plugin Discovery Platform |
| **M23** | ✅ TERMINÉ / INTÉGRÉ — #103 / PR #104 | Multi-project / Portfolio Specification Intelligence |
| **M24** | ✅ TERMINÉ / INTÉGRÉ — #105 / PR #106 | Query DSL, Saved Views & Export/Reporting |
| **M25** | ✅ TERMINÉ / INTÉGRÉ — #107 / PR #108 | Policy Packs & Governance Automation |
| **M26** | ✅ TERMINÉ / INTÉGRÉ — #109 / PR #110 | Optional Team/Remote Server Mode |
| **M27** | ✅ TERMINÉ / INTÉGRÉ — #111 / PR #112 | Evidence-backed Assisted Reasoning |

### NOW

| Sujet | Statut | Résultat attendu |
|---|---|---|
| **R2** | 🚧 EN COURS — #113 / PR draft #114 | MORPHEUS 1.1.0 qualifié, mergé, tagué et publié |

### LATER

- modernisation GitHub Actions à partir d'août 2026 ;
- cadrage explicite d'un éventuel M28 après R2 ;
- aucun jalon fonctionnel post-M27 n'est implicitement actif.

## 8. Politique CI — juillet 2026

Jusqu'au 31 juillet 2026 inclus :

```text
GitHub Actions is not a gate
no workflow rerun
no workflow_dispatch
no opportunistic .github/workflows change
Windows + Linux/WSL local exact-head logs are authoritative
```

## 9. Décision courante

**MORPHEUS 1.1.0 n'est pas encore publié.** Le merge de la PR #114, le tag `v1.1.0` et la GitHub Release restent interdits avant la double qualification exact-head, la preuve d'upgrade V012→V015, les packages et la confirmation `postGateExecutableDelta=NONE`.