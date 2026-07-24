# Feuille de route — MORPHEUS

Statut : **C0 à M9 validés et intégrés ; M10 validé, intégration portée par PR #58**

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
| M8 | Analyse des changements | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M8.md`, 289/289 |
| M9 | CLI stabilisée et distribution locale | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M9.md`, 298/298 Windows + Linux, merge `2533f325c6ef55070857a8bf75808648d99da5a2` |
| **M10** | **Serveur MCP STDIO natif** | **✅ VALIDÉ** | `VALIDATION_M10.md`, 307/307, MCP STDIO réel + Windows ZIP |
| M11 | API / headless | ⏳ PLANIFIÉ | après intégration M10 |
| M12 | MINOS | ⏳ PLANIFIÉ | intégration optionnelle |
| M13 | NEXUS | ⏳ PLANIFIÉ | MORPHEUS autonome |
| M14 | JARVIS | ⏳ PLANIFIÉ | orchestration seulement |

Références actives :

- [`VALIDATION_M10.md`](VALIDATION_M10.md)
- [`roadmap/M10_EXECUTION.md`](roadmap/M10_EXECUTION.md)
- [`MCP.md`](MCP.md)
- [`VALIDATION_M9.md`](VALIDATION_M9.md)
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
Merger uniquement après autorisation explicite
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

M0 prouve provider detection, mapping de domaine, UUIDv7, current reconstruction, lifecycle, snapshots, traceability, Memory/SQLite, recherche lexicale, contexte compact et références externes.

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

**Réponse de sortie : OUI.**

```text
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
```

Gate : **147/147 PASS**.

---

# 5. M4 — Traçabilité typée ✅

**Réponse de sortie : OUI.**

```text
TraceabilityLink typé et directionnel
snapshot-scoped persistence
bounded deterministic traversal
unresolved/broken references explicites
trace(requirement)
```

Gate : **189/189 PASS**.

---

# 6. M5 — Requêtes et contexte compact ✅

**Réponse de sortie : OUI.**

```text
find_requirements lexical déterministe
pagination bornée
ACTIVE par défaut
CURRENT isolation
business getters/lists
trace_requirement
get_change_context
compact DTOs
canonical JSON byte-déterministe
Memory == SQLite
SQLite reopen
```

Gate : **227/227 PASS**.

---

# 7. M6 — Qualité, couverture et diagnostics explicables ✅

**Réponse de sortie : OUI.**

```text
QualityFinding machine-readable
DETERMINISTIC != HEURISTIC
requirement/task coverage
acceptance capability gap explicite
change completeness
lifecycle blockers explicites
external UNVALIDATED / UNRESOLVED / STALE / BROKEN
aggregate metrics stables
compact quality JSON
```

Frontières :

```text
Scenario != AcceptanceCriterion
DesignDecision.decision != justification
risks != blockers
lifecycle non inféré depuis snapshot
aucun TraceabilityLink inventé
```

Gate : **261/261 PASS**.

---

# 8. M7 — Synchronisation incrémentale et fraîcheur ✅

**Réponse de sortie : OUI.**

```text
SHA-256(content)
ADDED / MODIFIED / DELETED / MOVED / UNCHANGED
move ambigu => FULL_REBUILD
SyncStateStore Memory + SQLite
prepare / complete / fail
baseline après succès seulement
WatchService local
OVERFLOW => FULL_REBUILD
freshness UNKNOWN / FRESH / STALE / REBUILD_REQUIRED
```

Gate : **282/282 PASS**, architecture **139/139**.

---

# 9. M8 — Analyse des changements ✅

**Réponse de sortie : OUI.**

```text
CURRENT baseline vs ProposedChangeSet
ADDED / MODIFIED / REMOVED
Constraint / DesignDecision / ImplementationTask
Scenario != AcceptanceCriterion
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
DEPENDS_ON persisted only
bounded impact paths
CompactChangeAnalysisView
canonical JSON
code impact = MINOS
```

Gate : **289/289 PASS**, architecture **146/146**.  
Merge : `6780fb024fe5b8645226f0aacecddb32bcfa7517`.

---

# 10. M9 — CLI stabilisée et distribution locale ✅

**Réponse de sortie : OUI.**

```text
MorpheusMain / MorpheusCli
human + --json
stable exit codes
SQLite persistent state
OpenSpec -> full snapshot publication
old ACTIVE kept until validation
CLI sync -> FULL_REBUILD conservateur
shaded executable JAR
jpackage app-image
embedded Java runtime
Windows ZIP
Linux tar.gz
```

Gate cross-platform :

```text
Windows  298/298 PASS | Architecture 149/149 | app-image + ZIP
Linux    298/298 PASS | Architecture 149/149 | app-image + tar.gz
```

Validation : [`VALIDATION_M9.md`](VALIDATION_M9.md).  
Merge : `2533f325c6ef55070857a8bf75808648d99da5a2`.

M9 est **VALIDÉ ET INTÉGRÉ**.

---

# 11. M10 — Serveur MCP STDIO natif ✅

Question de sortie :

> **MORPHEUS peut-il exposer ses capacités de lecture d'intention/specification à des agents via un serveur MCP local stdio natif, avec des tools déterministes, des JSON Schemas stricts, des erreurs explicites et aucune logique métier essentielle dans les handlers MCP, tout en restant utilisable sans serveur HTTP, Docker, MINOS, NEXUS ou JARVIS ?**

**Réponse : OUI.**

Transport / SDK :

```text
Java MCP SDK officiel 2.0.0
McpServer.sync
StdioServerTransportProvider
morpheus mcp --stdio
validateToolInputs=true
stdout = MCP JSON-RPC uniquement
```

Catalogue exact :

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

Contrats :

```text
all tools read-only
Scenario != AcceptanceCriterion
get_acceptance_criteria -> UNAVAILABLE_IN_NORMALIZED_MODEL
lifecycle non inféré
get_change_status -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
ACTIVE/CURRENT preserved
SQLite shared with CLI
no write/promote/activate tool
```

Gate Windows :

```text
MORPHEUS MCP              5/5 PASS
MORPHEUS CLI             10/10 PASS
Architecture Tests      149/149 PASS
TOTAL                   307/307 PASS
BUILD SUCCESS
```

Le gate inclut un vrai échange STDIO :

```text
initialize
notifications/initialized
tools/list
tools/call
invalid schema input rejection
```

Packaging final :

```text
MCP packaging proof: PASS
jpackage app-image PASS
morpheus.exe --version PASS
morpheus.exe --json version PASS
Portable archive creation: PASS
Windows ZIP 77275075 bytes
runtime Java embarqué
```

Validation : [`VALIDATION_M10.md`](VALIDATION_M10.md).  
Exécution : [`roadmap/M10_EXECUTION.md`](roadmap/M10_EXECUTION.md).  
MCP : [`MCP.md`](MCP.md).

ADR :

```text
ADR-0062 — Acceptée
ADR-0063 — Acceptée
ADR-0064 — Acceptée
```

M10 est **VALIDÉ**. L'intégration est portée par PR #58 et la fusion reste soumise à autorisation explicite.

---

# 12. M11 — API / headless ⏳

Périmètre : projets, spécifications, requirements, changements, contraintes, critères disponibles, traçabilité, versions, contexte, synchronisation, diagnostics et DTO stables.

M11 ne doit pas dupliquer les règles déjà exposées par CLI/MCP ; l'API reste un adapter.

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

**Prochaine porte : intégrer M10 après autorisation explicite, puis démarrer M11 — API / headless.**
