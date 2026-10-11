# M27 — Evidence-backed Assisted Reasoning — plan d’exécution

Statut : **TERMINÉ / VALIDÉ / INTÉGRÉ DANS `develop`**

Issue : **#111 CLOSED / completed**
Branche : **`m27-evidence-assisted-reasoning`**
PR : **#112 MERGED dans `develop`**
Baseline : **`c1eb1e74afe92db8b4a9250b678ce7d0d5c99ca7`**
SHA exact qualifié : **`f97307c878125550693699124ca717f64f305a3a`**
Head PR post-gate docs-only : **`026c1d5f8671cd7b879fa89d51af8e83a5f06272`**
Merge : **`f8810803bd5ae7d57c4858e1e384c6a0132e1a45`**

## 1. Question de sortie

> MORPHEUS peut-il enrichir ses réponses par des inférences assistées sans mélanger faits publiés, heuristiques et suggestions ?

Réponse : **oui, démontré sur Windows et Linux/WSL sur le même SHA exact puis intégré dans `develop`**.

## 2. Invariants

```text
facts != inference
inference != suggestion
heuristic != published fact
inference never overwrites published facts
confidence is explicit and bounded
assisted output cites evidence
adapter discovery != adapter execution
adapter absence != MORPHEUS failure
adapter failure != fact loss
reasoning execution != lifecycle mutation
reasoning execution != policy override
reasoning adapter != mandatory LLM
```

## 3. Découpage livré

### M27-S1 — Contrats d’évidence et de claims

Statut : **TERMINÉ / VALIDÉ**.

- catégories d’évidence fermées ;
- claims limitées à `INFERENCE`, `HEURISTIC`, `SUGGESTION` ;
- confiance score + bande ;
- citations obligatoires ;
- provenance bornée ;
- résultat `mutated=false` ;
- budgets centralisés.

### M27-S2 — SPI et orchestration optionnelle

Statut : **TERMINÉ / VALIDÉ**.

- `ReasoningAdapter` provider-neutral ;
- registre immuable ;
- découverte classpath passive et fault-isolated ;
- sélection explicite par requête ;
- facts-only sans adaptateur ;
- isolation transactionnelle logique des sorties d’adaptateurs ;
- rejet d’une claim non sourcée ou mensongère.

### M27-S3 — Adaptateur déterministe de référence

Statut : **TERMINÉ / VALIDÉ**.

`builtin-evidence-synthesis-v1` :

- local ;
- déterministe ;
- sans réseau ;
- sans LLM ;
- produit des claims séparées ;
- ne s’exécute que lorsqu’il est sélectionné.

### M27-S4 — Surfaces publiques

Statut : **TERMINÉ / VALIDÉ**.

```text
CLI   reason adapters
CLI   reason analyze
MCP   list_reasoning_adapters
MCP   reason_with_evidence
HTTP  GET  /api/v1/reasoning/adapters
HTTP  POST /api/v1/reasoning/analyze
```

- schémas stricts ;
- budgets exposés ;
- erreurs explicites ;
- remote RBAC READ pour l’analyse ;
- aucune surface de mutation.

### M27-S5 — Contrats, architecture et documentation

Statut : **TERMINÉ / VALIDÉ**.

- convergence `contracts/public-surfaces.tsv` ;
- OpenAPI M27 ;
- ADR-0095 acceptée ;
- documentation développeur et utilisateur ;
- tests d’architecture M27 ;
- scripts exact-head Windows + Linux.

### M27-S6 — Qualification et intégration

Statut : **TERMINÉ / VALIDÉ / INTÉGRÉ**.

Gates exécutés :

```text
Windows  .\validate-m27.cmd 1.0.0                 PASS
Linux    bash ./scripts/validate-m27.sh 1.0.0     PASS
```

Les deux gates ont visé `f97307c878125550693699124ca717f64f305a3a` et produit `postGateExecutableDelta=NONE`.

Les trois commits ajoutés après le SHA qualifié et avant le merge n’ont modifié que :

```text
docs/adr/0095-evidence-backed-assisted-reasoning.md
docs/roadmap/M27_EXECUTION.md
docs/validation/VALIDATION_M27.md
```

La PR #112 a ensuite été fusionnée dans `develop` avec contrôle `expected_head_sha=026c1d5f8671cd7b879fa89d51af8e83a5f06272`.

## 4. Modèle fonctionnel

```text
request
  question
  evidence[]
  adapterIds[]
  parameters
  maxClaims
       |
       v
validate budgets + identities
       |
       +--> facts = exact PUBLISHED_FACT subset
       |
       +--> explicit adapter selection
               |
               +--> adapter result validation
               |      - adapter ownership
               |      - claim id uniqueness
               |      - evidence references
               |      - confidence bounds
               |      - global claim budget
               |
               +--> failure isolated
       |
       v
result
  evidence
  facts
  inferences
  heuristics
  suggestions
  executions
  assisted
  mutated=false
```

## 5. Non-objectifs

M27 n’implémente pas :

- un fournisseur LLM obligatoire ;
- une clé API ou un appel cloud intégré ;
- une mémoire conversationnelle persistée ;
- une table de résultats de raisonnement ;
- une promotion automatique ;
- une mutation lifecycle ;
- un override policy ;
- une décision d’orchestration JARVIS ;
- une nouvelle migration SQLite.

## 6. Budgets

```text
MAX_EVIDENCE                 256
MAX_ADAPTERS                   8
MAX_CLAIMS                   256
MAX_EVIDENCE_REFERENCES       32
MAX_PROVENANCE_ENTRIES        32
MAX_PARAMETER_ENTRIES         32
MAX_QUESTION_CHARS          8192
MAX_STATEMENT_CHARS        16384
HTTP request bytes         65536
```

## 7. Qualification finale

```text
SHA exact qualifié          f97307c878125550693699124ca717f64f305a3a
Version                     1.0.0
Windows                     PASS
Linux / WSL                 PASS
Tests                       602 PASS sur chaque plateforme
Architecture                238 PASS sur chaque plateforme
Windows line / branch       45.2226% / 38.4456%
Linux line / branch         45.2246% / 38.4456%
Facts-only                  PASS
Assisted reasoning          PASS
Adapter failure isolation   PASS
No silent mutation          PASS
CLI/MCP/HTTP convergence    PASS
Remote READ RBAC            PASS
CycloneDX/provenance        PASS Windows + Linux
Portable                    PASS Windows + Linux
Packaged reasoning smokes   PASS Windows + Linux
Executable delta            NONE Windows + Linux
ADR-0095                    Acceptée — M27
CI / GitHub Actions         non utilisé — juillet 2026
```

Preuve : [`../validation/VALIDATION_M27.md`](../validation/VALIDATION_M27.md).

## 8. Packaging validé

Le shaded JAR et les distributions portables contiennent :

```text
ReasoningContracts
ReasoningService
ReasoningAdapter
EvidenceSynthesisReasoningAdapter
MorpheusReasoningCli
MorpheusReasoningHttpRoutes
MorpheusReasoningMcpTools
```

Smokes validés :

- `reason adapters` trouve l’adaptateur builtin ;
- facts-only retourne un fait, zéro inférence, `assisted=false`, `mutated=false` ;
- analyse explicite retourne des claims sourcées ;
- score de confiance dans `[0,1]`.

## 9. Gates de merge

```text
Windows exact-head                    PASS
Linux/WSL exact-head                  PASS
Même SHA                              PASS
Tests / architecture / coverage       PASS
ADR-0095                              ACCEPTED
VALIDATION_M27.md                     FINAL
Review threads                        NONE
Post-gate executable delta            NONE
PR #112                               MERGED
Merge commit                          f8810803bd5ae7d57c4858e1e384c6a0132e1a45
Post-merge reconciliation             DONE
Issue #111                            CLOSED / completed
```

## 10. État final

```text
implementation branch       DELIVERED
application contracts       QUALIFIED / INTEGRATED
CLI/MCP/HTTP                 QUALIFIED / INTEGRATED
architecture tests          238 PASS Windows + Linux
Windows exact-head          PASS
Linux/WSL exact-head        PASS
qualified SHA               f97307c878125550693699124ca717f64f305a3a
PR head docs-only           026c1d5f8671cd7b879fa89d51af8e83a5f06272
merge                       f8810803bd5ae7d57c4858e1e384c6a0132e1a45
PR                          #112 MERGED
issue                       #111 CLOSED / completed
```

M27 est terminé, validé et intégré. Toute évolution future de promotion ou mutation des résultats de raisonnement devra faire l’objet d’un jalon et d’une décision d’architecture distincts.