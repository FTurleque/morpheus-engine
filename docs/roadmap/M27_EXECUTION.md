# M27 — Evidence-backed Assisted Reasoning — plan d’exécution

Statut : **QUALIFIÉ WINDOWS + LINUX/WSL — PRÊT POUR INTÉGRATION**

Issue : **#111**
Branche : **`m27-evidence-assisted-reasoning`**
PR : **#112 vers `develop`**
Baseline : **`c1eb1e74afe92db8b4a9250b678ce7d0d5c99ca7`**
SHA exact qualifié : **`f97307c878125550693699124ca717f64f305a3a`**

## 1. Question de sortie

> MORPHEUS peut-il enrichir ses réponses par des inférences assistées sans mélanger faits publiés, heuristiques et suggestions ?

Réponse : **oui, démontré sur Windows et Linux/WSL sur le même SHA exact**.

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

Statut : **QUALIFICATION TERMINÉE — INTÉGRATION AUTORISÉE**.

Gates exécutés :

```text
Windows  .\validate-m27.cmd 1.0.0                 PASS
Linux    bash ./scripts/validate-m27.sh 1.0.0     PASS
```

Les deux gates ont visé `f97307c878125550693699124ca717f64f305a3a` et produit `postGateExecutableDelta=NONE`.

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

État :

1. Windows PASS sur SHA exact — **OK** ;
2. Linux/WSL PASS sur le même SHA — **OK** ;
3. tests et coverage relevés — **OK** ;
4. ADR-0095 acceptée — **OK** ;
5. `VALIDATION_M27.md` finalisé — **OK** ;
6. review threads résolus — **OK, aucun thread** ;
7. delta exécutable post-gate nul — **à confirmer après commits docs-only** ;
8. merge vers `develop` avec `expected_head_sha` — **autorisé après confirmation** ;
9. issue #111 fermée après merge et réconciliation documentaire.

## 10. État de preuve

```text
implementation branch       PRESENT
issue                       #111 OPEN jusqu’au merge
application contracts       QUALIFIED
CLI/MCP/HTTP                 QUALIFIED
architecture tests          238 PASS Windows + Linux
Windows exact-head          PASS
Linux/WSL exact-head        PASS
qualified SHA               f97307c878125550693699124ca717f64f305a3a
PR                          #112 READY FOR MERGE après contrôle docs-only
merge                       AUTHORIZED après contrôle docs-only
```

Aucun PASS n’est déclaré sans log concret. Les commits post-gate sont documentaires uniquement ; toute modification exécutable invaliderait les deux qualifications.