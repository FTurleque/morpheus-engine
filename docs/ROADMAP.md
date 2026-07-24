# Feuille de route — MORPHEUS

Statut : **C0 à M8 validés et intégrés ; M9 validé, intégration portée par PR #56**

Dernière mise à jour : 24 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves : contrats stables, ADR cohérentes, tests reproductibles et réponse explicite à chaque question de sortie.

---

# 1. Vue globale

| Jalon | Sujet | Statut | Preuve / prochaine porte |
|---|---|---|---|
| C0 | Cadrage fonctionnel et architectural | ✅ VALIDÉ | `VALIDATION_C0.md` |
| M0 | Faisabilité technique | ✅ VALIDÉ | `VALIDATION_M0.md` |
| M1 | Discovery, providers et fondation store | ✅ VALIDÉ | `VALIDATION_M1.md`, 42/42 |
| M2 | Ingestion et modèle normalisé | ✅ VALIDÉ | `VALIDATION_M2.md`, 94/94 |
| M3 | Temporalité, lifecycle, snapshots, versions | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M3.md`, 147/147 |
| M4 | Traçabilité typée | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M4.md`, 189/189 |
| M5 | Requêtes et contexte compact | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M5.md`, 227/227 |
| M6 | Qualité, couverture et diagnostics explicables | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M6.md`, 261/261 |
| M7 | Synchronisation incrémentale et fraîcheur | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M7.md`, 282/282 |
| **M8** | **Analyse des changements** | **✅ VALIDÉ / INTÉGRÉ** | `VALIDATION_M8.md`, 289/289, merge `6780fb024fe5b8645226f0aacecddb32bcfa7517` |
| **M9** | **CLI stabilisée et distribution locale** | **✅ VALIDÉ** | `VALIDATION_M9.md`, 298/298 Windows + Linux |
| M10 | Serveur MCP | ⏳ PLANIFIÉ | stdio natif d'abord |
| M11 | API / headless | ⏳ PLANIFIÉ | après CLI/MCP |
| M12 | MINOS | ⏳ PLANIFIÉ | intégration optionnelle |
| M13 | NEXUS | ⏳ PLANIFIÉ | MORPHEUS autonome |
| M14 | JARVIS | ⏳ PLANIFIÉ | orchestration seulement |

Références actives :

- [`VALIDATION_M9.md`](VALIDATION_M9.md)
- [`roadmap/M9_EXECUTION.md`](roadmap/M9_EXECUTION.md)
- [`CLI.md`](CLI.md)
- [`../distribution/README.md`](../distribution/README.md)
- [`adr/README.md`](adr/README.md)

---

# 2. Principes de séquencement

```text
Documenter d'abord
Décider ensuite
Implémenter après
Prouver avant de valider
```

Responsabilités :

```text
MORPHEUS owns intent/specification semantics
MINOS owns code intelligence
NEXUS owns context selection/ranking/compression
JARVIS owns orchestration
```

Invariants transverses :

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
Scenario != AcceptanceCriterion par défaut
provider facts != MORPHEUS domain
backend details != domain
PROPOSED never leaks into CURRENT
APPLY != PROMOTE != ACTIVATE
```

---

# 3. C0 à M2 — Fondation ✅

C0 fixe le domaine, les frontières et la stratégie de validation.

M0 prouve notamment : provider detection, mapping de domaine, UUIDv7, current reconstruction, lifecycle, snapshots, traceability, Memory/SQLite, recherche lexicale, contexte compact et références externes.

M1 stabilise discovery, provider registry/capabilities, OpenSpec read-only, diagnostics, Memory/SQLite et migrations de fondation.

M2 stabilise le modèle normalisé :

```text
ProjectSpecification
Specification
Requirement
RequirementDelta
ChangeProposal
Constraint
Scenario
DesignDecision
ImplementationTask
Evidence
Provenance
ExternalReference
```

`AcceptanceCriterion` n'est créé que si une source expose cette sémantique explicitement.

Gate M2 : **94/94 PASS**.

---

# 4. M3 — Temporalité, lifecycle, snapshots et versions ✅

Question de sortie :

> MORPHEUS peut-il publier et requêter un état CURRENT cohérent tout en conservant séparément propositions, historique et changements en cours, sans exposer un snapshot partiellement construit ?

**Réponse : OUI.**

```text
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED
published history = RETIRED* -> ACTIVE
retention = KEEP_ALL_PUBLISHED
```

Gate : **147/147 PASS**.

---

# 5. M4 — Traçabilité ✅

Question de sortie :

> MORPHEUS peut-il relier les éléments d'intention/specification par des relations typées, directionnelles et explicables, conserver les liens non résolus, puis produire un sous-graphe borné et déterministe sans backend graphe ?

**Réponse : OUI.**

| Slice | Contenu | Gate |
|---|---|---|
| S1 | `TraceabilityLink` + taxonomie | 155/155 |
| S2 | persistance snapshot-scoped | 160/160 |
| S3 | dérivation déterministe | 167/167 |
| S4 | traversal / path | 174/174 |
| S5 | external unresolved/broken refs | 184/184 |
| S6 | `trace(requirement)` | 189/189 |

---

# 6. M5 — Requêtes et contexte compact ✅

Question de sortie :

> MORPHEUS peut-il exposer des requêtes métier déterministes, snapshot-cohérentes et bornées, puis produire un contexte compact avec provenance et warnings sans moteur sémantique, LLM ou NEXUS ?

**Réponse : OUI.**

```text
find_requirements lexical déterministe
pagination bornée
ACTIVE par défaut
CURRENT isolation
business getters/lists
trace_requirement
get_change_context
broken AFFECTS conservés
external unresolved/stale/broken visibles
compact DTOs typés
provenance/evidence conservées
warnings structurés
JSON canonique byte-déterministe
Memory == SQLite
SQLite reopen
```

Gate : **227/227 PASS**.  
Merge final : `6bbaf086cf1fed81e3517bb1cef5b643264fb836`.

---

# 7. M6 — Qualité, couverture et diagnostics explicables ✅

Question de sortie :

> **MORPHEUS peut-il détecter et expliquer les lacunes de qualité d'une spécification sur un snapshot publié, mesurer sa couverture, exposer les blocages et références cassées, tout en distinguant strictement les constats déterministes des heuristiques et sans inventer les relations absentes ?**

**Réponse : OUI.**

Capacités :

```text
QualityFinding machine-readable
DETERMINISTIC != HEURISTIC
requirement orphan detection
requirement traceability coverage
implementation-task coverage
acceptance capability gap explicite
change completeness
lifecycle blockers explicites
design decision trace
justification indisponible explicite
external UNVALIDATED / UNRESOLVED / STALE / BROKEN
aggregate metrics stables
compact quality report
canonical JSON / UTF-8 déterministe
Memory == SQLite
SQLite reopen
ACTIVE / RETIRED policy
CURRENT isolation
```

Frontières :

```text
Scenario != AcceptanceCriterion
DesignDecision.decision != justification
risks != blockers
lifecycle non inféré depuis snapshot
aucun TraceabilityLink inventé
aucune persistance de QualityFinding/QualityReport
aucun LLM
aucune recherche sémantique
aucun ranking/fusion/compression NEXUS
```

Gate : **261/261 PASS**.  
Merge final : `904058251829b0ae39b34cd9da25c2b8918851a6`.

---

# 8. M7 — Synchronisation incrémentale et fraîcheur ✅

Question de sortie :

> **MORPHEUS peut-il détecter de façon déterministe les changements de sources locales, appliquer une stratégie incrémentale fiable, conserver archives et état de synchronisation, exposer une fraîcheur explicable et basculer vers un full rebuild dès que la sûreté de l'incrémental n'est plus démontrable ?**

**Réponse : OUI.**

```text
SourcePath canonique relatif
SHA-256(content)
sourceRevision opaque
scan complet/incomplet explicite
ADDED / MODIFIED / DELETED / MOVED / UNCHANGED
move unique 1:1 seulement
move ambigu => FULL_REBUILD
invalidation/refresh explicites
archives DELETED / MOVED
SyncStateStore Memory + SQLite
prepare / complete / fail
baseline après succès seulement
WatchService local
OVERFLOW => FULL_REBUILD
freshness UNKNOWN / FRESH / STALE / REBUILD_REQUIRED
SQLite reopen
```

Fallbacks :

```text
NO_BASELINE
SCAN_INCOMPLETE
WATCH_OVERFLOW
AMBIGUOUS_MOVE
REVISION_INCONSISTENCY
REVISION_SIGNAL_LOST
BASELINE_INCONSISTENT
PREVIOUS_REBUILD_PENDING
EXECUTION_FAILED
FORCED
```

Gate : **282/282 PASS**, architecture **139/139**.  
Merge final : `c3c397f4e5a2c97b686c96cfa936e00ac29a52bf`.

Validation : [`VALIDATION_M7.md`](VALIDATION_M7.md).  
Exécution : [`roadmap/M7_EXECUTION.md`](roadmap/M7_EXECUTION.md).  
Intégration : [`roadmap/M7_INTEGRATION.md`](roadmap/M7_INTEGRATION.md).

---

# 9. M8 — Analyse des changements ✅

Question de sortie :

> **MORPHEUS peut-il analyser de façon déterministe l'étendue fonctionnelle et documentaire d'un changement en confrontant la baseline CURRENT au contenu proposé, classifier les exigences ajoutées/modifiées/supprimées, exposer scénarios, contraintes, décisions et tâches associées, puis expliquer les dépendances et impacts transitifs par des chemins de traçabilité bornés, sans analyser le code ni inventer de relations ou de critères d'acceptation ?**

**Réponse : OUI.**

Capacités :

```text
CURRENT baseline vs ProposedChangeSet
ADDED / MODIFIED / REMOVED
SPECIFICATION / KEY / TITLE / STATEMENT / SCENARIOS
warnings d'incohérence explicites
Constraint / DesignDecision / ImplementationTask
Scenario != AcceptanceCriterion
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
DEPENDS_ON persisted only
DEPENDENCY / DEPENDENT
shortest paths bornés
non-resolved paths visibles
proposed-only trace gap explicite
CompactChangeAnalysisView
operation=analyze_change
canonical JSON / UTF-8
Memory == SQLite
SQLite reopen
code impact = MINOS
```

ADR :

```text
ADR-0056 — Acceptée
ADR-0057 — Acceptée
ADR-0058 — Acceptée
```

Gate final :

```text
ChangeAnalysisContractTest  7/7 PASS
Architecture Tests        146/146 PASS
TOTAL                     289/289 PASS
Failures                    0
Errors                      0
Skipped                     0
BUILD SUCCESS
Finished 2026-07-24T09:44:51+02:00
```

Merge final :

```text
6780fb024fe5b8645226f0aacecddb32bcfa7517
```

Validation : [`VALIDATION_M8.md`](VALIDATION_M8.md).  
Exécution : [`roadmap/M8_EXECUTION.md`](roadmap/M8_EXECUTION.md).  
Intégration : [`roadmap/M8_INTEGRATION.md`](roadmap/M8_INTEGRATION.md).

M8 est **VALIDÉ ET INTÉGRÉ**.

---

# 10. M9 — CLI stabilisée et distribution locale ✅

Question de sortie :

> **MORPHEUS peut-il être utilisé de façon fiable depuis une CLI locale stable, avec des commandes explicites et scriptables, des codes de sortie déterministes, une configuration de workspace/base de données claire, une archive portable reproductible, et une stratégie de runtime Java embarqué évaluée et prouvée sur Windows et Linux sans déplacer la logique métier dans l'adapter CLI ?**

**Réponse : OUI.**

CLI :

```text
MorpheusMain
MorpheusCli
CliLayout
CliRuntime
CliExitCode
```

Commandes :

```text
help / version / paths
projects add/list
sync / sync-status
requirements find
changes list/get
constraints list
decisions list
tasks list
trace-requirement
change-context
analyze-change
quality
```

Contrats :

```text
human + --json
stdout=result
stderr=error
stable exit codes
SQLite persistent state
```

Sync :

```text
ProjectSnapshotImportService
ProjectSnapshotImportResult
OpenSpec -> full snapshot publication
old ACTIVE kept until validation
ERROR diagnostic -> FAILED candidate
official CLI sync -> FULL_REBUILD
no fake incremental receipt
```

Distribution :

```text
shaded executable JAR
jpackage app-image
embedded Java runtime
Windows ZIP
Linux tar.gz
Windows EXE installer optional if WiX available
upgrade/uninstall preserves external data/config by default
```

Tests ciblés :

```text
MorpheusCliTest                    4/4 PASS
MorpheusMainTest                   2/2 PASS
ProjectSnapshotImportContractTest  3/3 PASS
```

ADR :

```text
ADR-0059 — Acceptée
ADR-0060 — Acceptée
ADR-0061 — Acceptée
```

Gate cross-platform final :

```text
Windows  298/298 PASS | Architecture 149/149 | app-image + ZIP    | smoke human/JSON
Linux    298/298 PASS | Architecture 149/149 | app-image + tar.gz | smoke human/JSON
```

Head exécutable validé :

```text
3b0fb46486cb28257d87d56084ef6e4fbe4cf7c7
```

Linux a été validé sous WSL/Ubuntu avec OpenJDK/Javac/jpackage 21.0.11 sur filesystem Linux local.

Les deux archives embarquent leur runtime Java ; aucun JDK séparé n'est requis chez l'utilisateur final.

Validation : [`VALIDATION_M9.md`](VALIDATION_M9.md).  
Exécution : [`roadmap/M9_EXECUTION.md`](roadmap/M9_EXECUTION.md).  
CLI : [`CLI.md`](CLI.md).  
Distribution : [`../distribution/README.md`](../distribution/README.md).

M9 est **VALIDÉ**. L'intégration est portée par PR #56 et la fusion reste soumise à autorisation explicite.

---

# 11. M10 — Serveur MCP ⏳

Transport local prioritaire : `stdio` natif.

Outils candidats :

```text
get_current_specification
find_requirements
get_change
list_changes
get_constraints
get_acceptance_criteria
get_design_decisions
get_implementation_tasks
trace_requirement
get_change_context
get_specification_context
get_change_status
get_blocking_conditions
get_sync_status
```

Aucune logique métier essentielle dans les handlers MCP.

---

# 12. M11 — API / headless ⏳

Périmètre : projets, spécifications, requirements, changements, contraintes, critères, traçabilité, versions, contexte, synchronisation, diagnostics et DTO stables.

---

# 13. M12 — MINOS ⏳

Relier intention et code via `ExternalReference(system=MINOS, ...)`. MINOS reste optionnel.

---

# 14. M13 — NEXUS ⏳

MORPHEUS fournit intention/specification ; NEXUS sélectionne, classe, fusionne et compresse le contexte global.

**MORPHEUS reste utilisable sans NEXUS.**

---

# 15. M14 — JARVIS ⏳

MORPHEUS expose états, transitions, blockers, acceptance status, références et contexte. JARVIS orchestre la séquence d'actions.

---

# 16. Règle de pilotage

```text
1. documenter invariant / ADR
2. implémenter le plus petit vertical slice
3. ajouter les preuves contractuelles
4. exécuter le gate local .\mvnw.cmd clean test
5. accepter l'ADR après preuve
6. merger uniquement après autorisation explicite
7. mettre à jour roadmap + issue
```

**Prochaine porte : intégrer M9 après autorisation explicite, puis démarrer M10 — Serveur MCP.**