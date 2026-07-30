# Validation M27 — Evidence-backed Assisted Reasoning

Statut : **NON QUALIFIÉ — GATES LOCAUX À EXÉCUTER**

Date de cadrage : 30 juillet 2026

## 1. Portée

M27 doit démontrer que MORPHEUS peut enrichir une réponse par des claims assistées sans confondre :

```text
published facts
inferences
heuristics
suggestions
```

La preuve doit aussi démontrer : confiance explicite, citations d’évidence, provenance, adaptateurs optionnels, isolation des erreurs, absence de mutation et convergence CLI/MCP/HTTP.

## 2. Baseline

```text
repository          FTurleque/morpheus-engine
integration branch  develop
baseline SHA        c1eb1e74afe92db8b4a9250b678ce7d0d5c99ca7
M26 qualified SHA   bf481b24054c4577144b4cb2ede2bdbc4d9974a2
M26 merge           49016a18c844a78ec864235c544d82d487da7c8a
M26 tests           579 PASS Windows + Linux/WSL
M26 architecture    234 PASS Windows + Linux/WSL
M27 issue           #111
M27 branch          m27-evidence-assisted-reasoning
```

## 3. Commandes autoritatives

Windows :

```powershell
.\validate-m27.cmd 1.0.0
```

Linux/WSL :

```bash
bash ./scripts/validate-m27.sh 1.0.0
```

Aucun workflow GitHub Actions n’est utilisé comme gate en juillet 2026.

## 4. Minimums

```text
tests                >= 598
architecture tests   >= 238
line coverage        >= 42%
branch coverage      >= 35%
portable             Windows + Linux
SBOM/provenance      Windows + Linux
exact head           même SHA
post-gate delta      NONE
```

## 5. Matrice de preuve

| Gate | Windows | Linux/WSL | Preuve attendue |
|---|---:|---:|---|
| `git diff --check` | NOT RUN | NOT RUN | aucune erreur |
| reactor `clean verify` | NOT RUN | NOT RUN | zéro failure/error |
| tests >= 598 | NOT RUN | NOT RUN | total Surefire |
| architecture >= 238 | NOT RUN | NOT RUN | module architecture |
| line >= 42% | NOT RUN | NOT RUN | summary JaCoCo |
| branch >= 35% | NOT RUN | NOT RUN | summary JaCoCo |
| facts/inference separation | NOT RUN | NOT RUN | tests + portable smoke |
| confidence bounds/bands | NOT RUN | NOT RUN | tests + portable smoke |
| evidence citations/provenance | NOT RUN | NOT RUN | tests + portable smoke |
| facts-only without adapter | NOT RUN | NOT RUN | `assisted=false` |
| adapter fault isolation | NOT RUN | NOT RUN | facts retained / FAILED execution |
| no silent mutation | NOT RUN | NOT RUN | `mutated=false` |
| CLI/MCP/HTTP convergence | NOT RUN | NOT RUN | manifest + OpenAPI + tests |
| remote READ RBAC | NOT RUN | NOT RUN | architecture/security contract |
| shaded runtime classes | NOT RUN | NOT RUN | JAR entries |
| portable launcher | NOT RUN | NOT RUN | packaged smokes |
| CycloneDX JSON/XML | NOT RUN | NOT RUN | generated files |
| build provenance | NOT RUN | NOT RUN | generated properties |
| HEAD unchanged | NOT RUN | NOT RUN | exact SHA |
| executable delta | NOT RUN | NOT RUN | NONE |

## 6. Contrats à vérifier

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

## 7. Packaging attendu

Le shaded JAR et le portable doivent contenir au minimum :

```text
com/morpheus/application/reasoning/ReasoningContracts.class
com/morpheus/application/reasoning/ReasoningService.class
com/morpheus/application/reasoning/ReasoningAdapter.class
com/morpheus/application/reasoning/EvidenceSynthesisReasoningAdapter.class
com/morpheus/cli/MorpheusReasoningCli.class
com/morpheus/api/MorpheusReasoningHttpRoutes.class
com/morpheus/mcp/MorpheusReasoningMcpTools.class
```

## 8. Emplacements de logs

```text
validation-output/m27/validation-summary.txt
validation-output/m27/dist/
validation-output/m27/shaded-entries.txt
```

## 9. Relevé exact-head

À remplir uniquement après exécution réelle :

```text
qualified SHA          NOT SET
Windows date/time      NOT SET
Linux/WSL date/time    NOT SET
Windows tests          NOT SET
Linux tests            NOT SET
Windows architecture   NOT SET
Linux architecture     NOT SET
Windows line/branch    NOT SET
Linux line/branch      NOT SET
portable               NOT SET
SBOM/provenance        NOT SET
postGateExecutableDelta NOT SET
```

## 10. Décision

```text
M27 qualification   BLOCKED
ADR-0095            PROPOSED
PR merge             FORBIDDEN UNTIL BOTH LOCAL GATES PASS
issue #111 closure  FORBIDDEN UNTIL MERGE + RECONCILIATION
```

Ce document doit être remplacé par les valeurs et logs réels, sans extrapolation, après les deux gates exact-head.
