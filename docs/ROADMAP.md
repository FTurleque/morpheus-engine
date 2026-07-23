# Feuille de route — MORPHEUS

Statut : **C0 à M7 validés et intégrés ; M8 prochain**

Dernière mise à jour : 24 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves : contrats stables, ADR cohérentes, tests et réponse explicite à chaque question de sortie.

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
| **M7** | **Synchronisation incrémentale et fraîcheur** | **✅ VALIDÉ / INTÉGRÉ** | `VALIDATION_M7.md`, 282/282, merge `c3c397f4e5a2c97b686c96cfa936e00ac29a52bf` |
| **M8** | **Analyse des changements** | **⏳ PROCHAIN** | prochain jalon |
| M9 | CLI stabilisée et distribution native | ⏳ PLANIFIÉ | après cœur fonctionnel |
| M10 | MCP | ⏳ PLANIFIÉ | stdio natif d'abord |
| M11 | API / headless | ⏳ PLANIFIÉ | après CLI/MCP |
| M12 | MINOS | ⏳ PLANIFIÉ | intégration optionnelle |
| M13 | NEXUS | ⏳ PLANIFIÉ | MORPHEUS autonome |
| M14 | JARVIS | ⏳ PLANIFIÉ | orchestration seulement |

Références :

- [`VALIDATION_M3.md`](VALIDATION_M3.md)
- [`VALIDATION_M4.md`](VALIDATION_M4.md)
- [`VALIDATION_M5.md`](VALIDATION_M5.md)
- [`VALIDATION_M6.md`](VALIDATION_M6.md)
- [`VALIDATION_M7.md`](VALIDATION_M7.md)
- [`roadmap/M6_EXECUTION.md`](roadmap/M6_EXECUTION.md)
- [`roadmap/M7_EXECUTION.md`](roadmap/M7_EXECUTION.md)
- [`roadmap/M7_INTEGRATION.md`](roadmap/M7_INTEGRATION.md)
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
ACTIVE / RETIRED explicites
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

## Progression

| Slice | Contenu | PR | ADR | Gate |
|---|---|---|---|---|
| S1 | requirement traceability coverage + orphan requirements | #44 | ADR-0048 | 234/234 |
| S2 | implementation-task coverage + acceptance capability gap | #45 | ADR-0049 | 241/241 |
| S3 | change completeness + lifecycle blocking conditions | #46 | ADR-0050 | 248/248 |
| S4 | decision trace/justification availability + external reference quality | #47 | ADR-0051 | 254/254 |
| S5 | aggregate quality report + compact exposure | #48 | ADR-0052 | 261/261 |
| S6 | validation finale | #49 | — | 261/261 |

Merges S1-S5 :

```text
5b0984ec7777eabb6f2d1417b4c900c08a038947
916201c724722cf9ace50d44e55d001d8faf383c
03fd5a86e11f2afc40e3f1ecd5b1b8a1d1d211f7
ef6975d05d4bfcd994669d27e3a6600bc4ecdc1a
ab91b6c537c73c586b925dd6367021e2780808aa
```

Merge final M6 :

```text
904058251829b0ae39b34cd9da25c2b8918851a6
```

Capacités validées :

```text
QualityFinding machine-readable
DETERMINISTIC != HEURISTIC
heuristic confidence contract
requirement orphan detection
requirement traceability coverage
implementation-task coverage
acceptance capability gap explicite
change completeness
lifecycle facts indisponibles explicites
lifecycle blockers explicites
design decision trace
justification indisponible explicite
external UNVALIDATED / UNRESOLVED / STALE / BROKEN
aggregate metrics stables
findings distincts et canoniquement triés
compact quality report
canonical JSON / UTF-8 déterministe
Memory == SQLite
SQLite reopen
ACTIVE / RETIRED policy
CURRENT isolation
```

Frontières confirmées :

```text
Scenario != AcceptanceCriterion
DesignDecision.decision != justification
risks != blockers
lifecycle non inféré depuis snapshot
aucun TraceabilityLink inventé
aucune persistance de QualityFinding/QualityReport
aucune nouvelle migration M6
aucun LLM
aucune recherche sémantique
aucun ranking/fusion/compression NEXUS
```

Gate technique final :

```text
Architecture tests 134/134 PASS
TOTAL              261/261 PASS
Failures             0
Errors               0
Skipped              0
BUILD SUCCESS
Finished 2026-07-23T23:32:49+02:00
```

Validation : [`VALIDATION_M6.md`](VALIDATION_M6.md).  
Vue d'exécution : [`roadmap/M6_EXECUTION.md`](roadmap/M6_EXECUTION.md).

---

# 8. M7 — Synchronisation incrémentale et fraîcheur ✅

Question de sortie :

> **MORPHEUS peut-il détecter de façon déterministe les changements de sources locales, appliquer une stratégie incrémentale fiable, conserver archives et état de synchronisation, exposer une fraîcheur explicable et basculer vers un full rebuild dès que la sûreté de l'incrémental n'est plus démontrable ?**

**Réponse : OUI.**

Capacités validées :

```text
SourcePath canonique relatif
SHA-256(content) comme fingerprint
sourceRevision opaque
scan complet/incomplet explicite
mutation pendant hash détectée
ADDED / MODIFIED / DELETED / MOVED / UNCHANGED
move uniquement sur match contenu 1:1
move ambigu => FULL_REBUILD
invalidations et refresh sets explicites
archives DELETED / MOVED
SyncStateStore Memory + SQLite
V008 spécialisée
prepare / complete / fail
baseline seulement après succès
WatchService local récursif
watcher sans suivi de symlink
OVERFLOW => FULL_REBUILD
freshness UNKNOWN / FRESH / STALE / REBUILD_REQUIRED
SQLite reopen
```

Fallbacks explicites :

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

ADR :

```text
ADR-0053 — Acceptée
ADR-0054 — Acceptée
ADR-0055 — Acceptée
```

Gate technique final :

```text
MORPHEUS Application  82/82 PASS
Architecture Tests   139/139 PASS
TOTAL                282/282 PASS
Failures               0
Errors                 0
Skipped                0
BUILD SUCCESS
Finished 2026-07-24T00:22:11+02:00
```

Merge final M7 :

```text
c3c397f4e5a2c97b686c96cfa936e00ac29a52bf
```

Invariant : **la fiabilité prime ; en cas de doute, full rebuild.**

Validation : [`VALIDATION_M7.md`](VALIDATION_M7.md).  
Vue d'exécution : [`roadmap/M7_EXECUTION.md`](roadmap/M7_EXECUTION.md).  
Reçu d'intégration : [`roadmap/M7_INTEGRATION.md`](roadmap/M7_INTEGRATION.md).

M7 est **VALIDÉ ET INTÉGRÉ**.

---

# 9. M8 — Analyse des changements ⏳

Analyser l'étendue fonctionnelle/documentaire d'un changement : current/proposed, exigences ajoutées/modifiées/supprimées, contraintes, décisions, critères, dépendances et chemins explicatifs.

L'analyse du code reste MINOS.

---

# 10. M9 — CLI stabilisée et distribution locale ⏳

Stabiliser commandes utilisateur, archive portable, runtime Java embarqué à prouver, jlink/jpackage à évaluer, Windows + Linux.

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
4. exécuter .\mvnw.cmd clean test
5. accepter l'ADR après preuve
6. merger sous autorisation explicite
7. mettre à jour roadmap + issue
```

**Prochaine étape : M8 — Analyse des changements.**
