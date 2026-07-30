# M27 — Evidence-backed Assisted Reasoning — plan d’exécution

Statut : **IMPLÉMENTATION TERMINÉE SUR BRANCHE — QUALIFICATION EXACT-HEAD À EXÉCUTER**

Issue : **#111**  
Branche : **`m27-evidence-assisted-reasoning`**  
PR : **#112 DRAFT vers `develop`**  
Baseline : **`c1eb1e74afe92db8b4a9250b678ce7d0d5c99ca7`**

## 1. Question de sortie

> MORPHEUS peut-il enrichir ses réponses par des inférences assistées sans mélanger faits publiés, heuristiques et suggestions ?

La réponse ne peut être déclarée positive qu’après qualification Windows + Linux/WSL sur un même SHA exact et absence de delta exécutable post-gate.

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

Statut : **IMPLÉMENTÉ**.

- catégories d’évidence fermées ;
- claims limitées à `INFERENCE`, `HEURISTIC`, `SUGGESTION` ;
- confiance score + bande ;
- citations obligatoires ;
- provenance bornée ;
- résultat `mutated=false` ;
- budgets centralisés.

### M27-S2 — SPI et orchestration optionnelle

Statut : **IMPLÉMENTÉ**.

- `ReasoningAdapter` provider-neutral ;
- registre immuable ;
- découverte classpath passive et fault-isolated ;
- sélection explicite par requête ;
- facts-only sans adaptateur ;
- isolation transactionnelle logique des sorties d’adaptateurs ;
- rejet d’une claim non sourcée ou mensongère.

### M27-S3 — Adaptateur déterministe de référence

Statut : **IMPLÉMENTÉ**.

`builtin-evidence-synthesis-v1` :

- local ;
- déterministe ;
- sans réseau ;
- sans LLM ;
- produit des claims séparées ;
- ne s’exécute que lorsqu’il est sélectionné.

### M27-S4 — Surfaces publiques

Statut : **IMPLÉMENTÉ**.

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

Statut : **IMPLÉMENTÉ**.

- convergence `contracts/public-surfaces.tsv` ;
- OpenAPI M27 ;
- ADR-0095 ;
- documentation développeur et utilisateur ;
- tests d’architecture M27 ;
- scripts exact-head Windows + Linux.

### M27-S6 — Qualification et intégration

Statut : **EN ATTENTE D’EXÉCUTION LOCALE**.

Gates :

```text
Windows  .\validate-m27.cmd 1.0.0
Linux    bash ./scripts/validate-m27.sh 1.0.0
```

Les deux doivent viser le même SHA et produire `postGateExecutableDelta=NONE`.

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

## 7. Plan de qualification

### 7.1 Gate reactor

```text
git diff --check develop...HEAD
mvnw clean verify
```

Minimums M27 :

```text
tests                >= 602
architecture tests   >= 238
line coverage        >= 42%
branch coverage      >= 35%
```

Le seuil de 602 correspond aux 579 tests M26 et aux 23 tests M27 ajoutés. Il empêche une qualification qui ignorerait une partie des nouveaux contrats.

### 7.2 Contrats fonctionnels

- facts-only avec `adapterIds=[]` ;
- claims classées par nature ;
- confiance bornée et bande cohérente ;
- citation inconnue rejetée ;
- duplicate evidence rejeté ;
- panne adapter isolée ;
- sortie `mutated=false` ;
- provider optionnel invalide sans panne de MORPHEUS ;
- budget claims global ;
- schémas stricts CLI/MCP/HTTP.

### 7.3 Packaging

Le shaded JAR et le portable doivent contenir :

```text
ReasoningContracts
ReasoningService
ReasoningAdapter
EvidenceSynthesisReasoningAdapter
MorpheusReasoningCli
MorpheusReasoningHttpRoutes
MorpheusReasoningMcpTools
```

Smokes :

- `reason adapters` trouve l’adaptateur builtin ;
- facts-only retourne un fait, zéro inférence, `assisted=false`, `mutated=false` ;
- analyse explicite retourne au moins une inference et une heuristic sourcées ;
- score de confiance dans `[0,1]`.

### 7.4 Supply chain

- CycloneDX JSON/XML ;
- build provenance ;
- portable Windows ;
- portable Linux ;
- aucun changement CI/GitHub Actions en juillet 2026.

## 8. Gates de merge

La PR M27 ne peut être mergée que si :

1. Windows PASS sur SHA exact ;
2. Linux/WSL PASS sur le même SHA ;
3. nombre de tests et coverage relevés ;
4. ADR-0095 acceptée avec les preuves réelles ;
5. `VALIDATION_M27.md` finalisé ;
6. PR non draft et review threads résolus ;
7. HEAD inchangé depuis les gates ;
8. delta exécutable post-gate nul ;
9. merge vers `develop` avec `expected_head_sha` ;
10. issue #111 fermée seulement après merge et réconciliation documentaire.

## 9. État de preuve actuel

```text
implementation branch       PRESENT
issue                       #111 OPEN
application contracts       IMPLEMENTED
CLI/MCP/HTTP                 IMPLEMENTED
architecture tests          IMPLEMENTED
static Java 21 checks       CORE + CLI PASS (non substitutifs aux gates)
Windows exact-head          NOT RUN
Linux/WSL exact-head        NOT RUN
PR                          #112 OPEN / DRAFT
merge                       BLOCKED BY LOCAL GATES
```

Aucun PASS de qualification n’est déclaré sans log concret.
