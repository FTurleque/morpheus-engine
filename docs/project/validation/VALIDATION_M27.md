# Validation M27 — Evidence-backed Assisted Reasoning

Statut : **QUALIFIÉ — WINDOWS + LINUX/WSL PASS SUR LE MÊME SHA EXACT**

Date de qualification : 30 juillet 2026

## 1. Portée

M27 démontre que MORPHEUS peut enrichir une réponse par des claims assistées sans confondre :

```text
published facts
inferences
heuristics
suggestions
```

La preuve couvre également la confiance explicite, les citations d’évidence, la provenance, les adaptateurs optionnels, l’isolation des erreurs, l’absence de mutation et la convergence CLI/MCP/HTTP.

## 2. Baseline et candidat qualifié

```text
repository              FTurleque/morpheus-engine
integration branch      develop
baseline SHA            c1eb1e74afe92db8b4a9250b678ce7d0d5c99ca7
M26 qualified SHA       bf481b24054c4577144b4cb2ede2bdbc4d9974a2
M26 merge               49016a18c844a78ec864235c544d82d487da7c8a
M27 issue               #111
M27 branch              m27-evidence-assisted-reasoning
M27 PR                  #112
M27 qualified SHA       f97307c878125550693699124ca717f64f305a3a
version                 1.0.0
```

Les qualifications Windows et Linux/WSL ont toutes deux démarré et terminé sur ce même SHA exact.

## 3. Commandes autoritatives exécutées

Windows :

```powershell
.\validate-m27.cmd 1.0.0
```

Linux/WSL :

```bash
bash ./scripts/validate-m27.sh 1.0.0
```

Aucun workflow GitHub Actions n’a été utilisé comme gate en juillet 2026.

## 4. Seuils

```text
tests                >= 602
architecture tests   >= 238
line coverage        >= 42%
branch coverage      >= 35%
portable             Windows + Linux
SBOM/provenance      Windows + Linux
exact head           même SHA
post-gate executable NONE
```

Le seuil de 602 correspond aux 579 tests M26 plus les 23 tests M27.

## 5. Résultats exact-head

| Gate | Windows | Linux/WSL |
|---|---:|---:|
| `git diff --check` | PASS | PASS |
| reactor `clean verify` | PASS | PASS |
| tests | 602 PASS | 602 PASS |
| architecture | 238 PASS | 238 PASS |
| line coverage | 45.2226% PASS | 45.2246% PASS |
| branch coverage | 38.4456% PASS | 38.4456% PASS |
| facts/inference separation | PASS | PASS |
| confidence bounds/bands | PASS | PASS |
| evidence citations/provenance | PASS | PASS |
| facts-only without adapter | PASS | PASS |
| adapter fault isolation | PASS | PASS |
| no silent mutation | PASS | PASS |
| CLI/MCP/HTTP convergence | PASS | PASS |
| remote READ RBAC | PASS | PASS |
| shaded runtime classes | PASS | PASS |
| portable launcher | PASS | PASS |
| packaged reasoning smokes | PASS | PASS |
| CycloneDX JSON/XML | PASS | PASS |
| build provenance | PASS | PASS |
| HEAD exact | `f97307c...` | `f97307c...` |
| post-gate executable delta | NONE | NONE |

## 6. Contrats prouvés

### Facts-only

```text
adapterIds = []
facts       = exact PUBLISHED_FACT subset
inferences  = []
heuristics  = []
suggestions = []
executions  = []
assisted    = false
mutated     = false
```

### Assisted

```text
adapter selected explicitly
claim kind separated
confidence score in [0,1]
confidence band coherent
at least one evidenceId
adapterId truthful
mutated=false
```

### Failure isolation

```text
adapter failure        => AdapterExecution FAILED
accepted claims        => 0 for failed adapter
published evidence     => retained
facts                  => retained
lifecycle/store write  => none
```

## 7. Packaging

Le shaded JAR et les distributions portables contiennent les classes M27 requises :

```text
com/morpheus/application/reasoning/ReasoningContracts.class
com/morpheus/application/reasoning/ReasoningService.class
com/morpheus/application/reasoning/ReasoningAdapter.class
com/morpheus/application/reasoning/EvidenceSynthesisReasoningAdapter.class
com/morpheus/cli/MorpheusReasoningCli.class
com/morpheus/api/MorpheusReasoningHttpRoutes.class
com/morpheus/mcp/MorpheusReasoningMcpTools.class
```

Artefacts produits :

```text
Windows  validation-output/m27/dist/morpheus-1.0.0-windows-x64.zip
Linux    validation-output/m27/dist/morpheus-1.0.0-linux-x64.tar.gz
SBOM     target/m21-supply-chain/morpheus-sbom.xml
SBOM     target/m21-supply-chain/morpheus-sbom.json
Provenance target/m21-supply-chain/build-provenance.properties
```

Les smokes packagés ont validé le mode facts-only et le raisonnement assisté explicitement sélectionné sur les deux plateformes.

## 8. Relevé de qualification

```text
qualified SHA                f97307c878125550693699124ca717f64f305a3a
Windows qualification        PASS — 30 juillet 2026 vers 12:20 CEST
Linux/WSL qualification      PASS — 30 juillet 2026 vers 12:37 CEST
Windows tests                602 PASS
Linux tests                  602 PASS
Windows architecture         238 PASS
Linux architecture           238 PASS
Windows line/branch          0.452226 / 0.384456
Linux line/branch            0.452246 / 0.384456
portable                     PASS Windows + Linux
SBOM/provenance              PASS Windows + Linux
surface convergence          PASS Windows + Linux
remote READ RBAC             PASS Windows + Linux
postGateExecutableDelta      NONE Windows + Linux
```

## 9. Avertissements non bloquants

Les builds ont émis les avertissements historiques relatifs à l’analyse des dépendances Maven, aux ressources/classes chevauchées du shaded JAR, aux API dépréciées et à l’accès natif SQLite. Aucun avertissement n’a produit de failure, error ou échec de gate.

## 10. Décision

```text
M27 qualification   PASS
ADR-0095            ACCEPTED — M27
PR #112             READY FOR REVIEW / MERGE
PR merge             AUTHORIZED après vérification docs-only post-gate
issue #111 closure   après merge + réconciliation documentaire
```

Les commits documentaires ajoutés après `f97307c878125550693699124ca717f64f305a3a` ne peuvent modifier ni code, ni POM, ni contrat runtime, ni OpenAPI, ni packaging, ni scripts de validation. La comparaison finale doit confirmer `postGateExecutableDelta=NONE`.