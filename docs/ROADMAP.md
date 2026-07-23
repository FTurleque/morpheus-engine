# Feuille de route — MORPHEUS

Statut : **Roadmap active — C0 à M4 validés et intégrés ; M5 actif — 3/6 intégrés ; S4 implémenté, gate en attente**

Date de dernière mise à jour : 23 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves. Un jalon n'est pas terminé parce que son code existe : il doit disposer de contrats stables, de tests, d'ADR cohérentes et d'une preuve de sortie explicite.

---

# 1. Vue globale

| Jalon | Sujet | Statut | Preuve / prochaine porte |
|---|---|---|---|
| C0 | Cadrage fonctionnel et architectural | ✅ VALIDÉ | `VALIDATION_C0.md` |
| M0 | Faisabilité technique | ✅ VALIDÉ | `VALIDATION_M0.md` |
| M1 | Discovery, providers et fondation store | ✅ VALIDÉ | `VALIDATION_M1.md`, 42/42 tests |
| M2 | Ingestion et modèle normalisé | ✅ VALIDÉ | `VALIDATION_M2.md`, 94/94 tests |
| M3 | État temporel, lifecycle, snapshots, versions | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M3.md`, 6/6, 147/147 tests |
| M4 | Traçabilité | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M4.md`, 6/6, 189/189 tests |
| **M5** | **Requêtes et contexte compact** | **🚧 ACTIF — 3/6 INTÉGRÉS** | S1 196/196 merged ; S2 202/202 merged ; S3 210/210 merged ; S4 implémenté, gate attendu 217 ; issue #36 |
| M6 | Qualité / couverture | ⏳ PLANIFIÉ | après primitives de requête |
| M7 | Synchronisation incrémentale | ⏳ PLANIFIÉ | après snapshots stables |
| M8 | Analyse des changements | ⏳ PLANIFIÉ | après M3/M4/M5 |
| M9 | CLI stabilisée et distribution native | ⏳ PLANIFIÉ | après cœur fonctionnel |
| M10 | MCP | ⏳ PLANIFIÉ | natif stdio d'abord |
| M11 | API / headless | ⏳ PLANIFIÉ | Docker officiel si justifié |
| M12 | MINOS | ⏳ PLANIFIÉ | intégration optionnelle |
| M13 | NEXUS | ⏳ PLANIFIÉ | MORPHEUS reste autonome |
| M14 | JARVIS | ⏳ PLANIFIÉ | orchestration seulement |

Références :

- [`VALIDATION_C0.md`](VALIDATION_C0.md)
- [`VALIDATION_M0.md`](VALIDATION_M0.md)
- [`VALIDATION_M1.md`](VALIDATION_M1.md)
- [`VALIDATION_M2.md`](VALIDATION_M2.md)
- [`VALIDATION_M3.md`](VALIDATION_M3.md)
- [`VALIDATION_M4.md`](VALIDATION_M4.md)
- [`roadmap/M2_EXECUTION.md`](roadmap/M2_EXECUTION.md)
- [`roadmap/M3_EXECUTION.md`](roadmap/M3_EXECUTION.md)
- [`roadmap/M4_EXECUTION.md`](roadmap/M4_EXECUTION.md)
- [`roadmap/M5_EXECUTION.md`](roadmap/M5_EXECUTION.md)
- [`roadmap/DEPLOYMENT.md`](roadmap/DEPLOYMENT.md)
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
OpenSpec-first, not OpenSpec-locked
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

# 3. C0 — Cadrage fonctionnel et architectural ✅

Cadrage fonctionnel, modèle de domaine, providers, store, snapshots, identité, traçabilité, critères de validation et ADR structurantes établis.

Porte : **VALIDÉE**.

---

# 4. M0 — Faisabilité technique ✅

Preuves E01 à E14 validées, notamment :

```text
provider detection / domain mapping
stable identity / UUIDv7
current reconstruction / lifecycle
knowledge snapshots / retention
traceability / Memory + SQLite
graph DB NOT_NEEDED_FOR_MVP
lexical search PASS
compact context PASS
external references PASS
```

Décisions M5 héritées de M0 :

```text
lexical search déterministe = ADOPTÉ
semantic search = NOT_REQUIRED_FOR_MVP
compact context MORPHEUS = ADOPTÉ
global ranking / multi-engine fusion / token compression = NEXUS
```

Porte : **VALIDÉE — ADOPTER AVEC CONTRAINTES**.

---

# 5. M1 — Discovery, providers et fondation store ✅

Livré : discovery explicit-first, provider registry/capabilities, OpenSpec read-only, diagnostics, UUIDv7, Memory store, SQLite et migrations de fondation.

```text
42/42 PASS
BUILD SUCCESS
```

Porte : **VALIDÉE — M2 AUTORISÉE**.

---

# 6. M2 — Ingestion et modèle normalisé ✅

Domaine stabilisé :

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

`AcceptanceCriterion` n'est créé que si une source expose une sémantique explicite.

Huit slices validés ; gate final : **94/94 PASS**.

Porte : **VALIDÉE — M3 AUTORISÉE**.

---

# 7. M3 — État temporel, lifecycle, snapshots et versions ✅

Question de sortie :

> **MORPHEUS peut-il publier et requêter un état `CURRENT` cohérent tout en conservant séparément les propositions, l'historique et les changements en cours, sans jamais exposer un snapshot partiellement construit ?**

**Réponse : OUI.**

Six slices validés et intégrés ; invariants :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED
                         \-> FAILED
published history = RETIRED* -> ACTIVE
retention = KEEP_ALL_PUBLISHED
```

SQLite V004 :

```text
specification_versions
snapshot_specification_versions
requirement_versions
```

Gate final : **147/147 PASS**.

---

# 8. M4 — Traçabilité ✅

Question de sortie :

> **MORPHEUS peut-il relier les éléments d'intention/specification par des relations typées, directionnelles et explicables, conserver les liens non résolus, puis produire un sous-graphe borné et déterministe sans dépendre d'un backend graphe ?**

**Réponse : OUI.**

| Slice | Contenu | PR | ADR | Gate |
|---|---|---|---|---|
| S1 | `TraceabilityLink` + taxonomie | #28 | ADR-0037 | 155/155 |
| S2 | persistance snapshot-scoped | #29 | ADR-0038 | 160/160 |
| S3 | dérivation déterministe | #31 | ADR-0039 | 167/167 |
| S4 | traversal / path | #32 | ADR-0040 | 174/174 |
| S5 | external / unresolved / broken refs | #33 | ADR-0041 | 184/184 |
| S6 | `trace(requirement)` | #34 | ADR-0042 | 189/189 |

Validation : [`VALIDATION_M4.md`](VALIDATION_M4.md).

Porte : **VALIDÉE — M5 AUTORISÉ**.

---

# 9. M5 — Requêtes et contexte compact 🚧

## Question de sortie

> **MORPHEUS peut-il exposer des requêtes métier déterministes, snapshot-cohérentes et bornées, puis produire un contexte compact avec provenance et warnings sans dépendre d'un moteur sémantique, d'un LLM ou de NEXUS ?**

## Objectif

Rendre MORPHEUS directement interrogeable par humains, scripts et agents au moyen de contrats applicatifs stables et provider/backend-neutral.

Primitives cibles :

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
```

`get_acceptance_criteria` reste conditionnel à l'existence future d'une sémantique explicite ; aucun `Scenario` n'est transformé artificiellement en `AcceptanceCriterion`.

Inclut : recherche lexicale, pagination, limites, vues compactes, warnings et provenance.

N'inclut pas : ranking global, fusion multi-engine ou compression par budget de tokens ; ces responsabilités restent NEXUS.

## Progression M5

| Slice | Contenu | État |
|---|---|---|
| **S1** | **`find_requirements` + pagination déterministe** | **✅ MERGED — PR #37 — ADR-0043 — 196/196** |
| **S2** | **projection métier requêtable des autres familles** | **✅ MERGED — PR #38 — ADR-0044 — 202/202** |
| **S3** | **getters/lists déterministes** | **✅ MERGED — PR #39 — ADR-0045 — 210/210** |
| **S4** | **`trace_requirement` query view + `get_change_context`** | **🚧 IMPLÉMENTÉ — PR #40 Draft — ADR-0046 proposée — gate attendu 217** |
| S5 | vues compactes + warnings/provenance + JSON déterministe | ⏳ |
| S6 | validation finale `VALIDATION_M5.md` | ⏳ |

### M5-S1 — intégré

```text
PageRequest
RequirementSearchQuery
RequirementSearchPage
RequirementQueryService
```

Gate : **196/196 PASS**.  
ADR : **ADR-0043 — Acceptée — M5**.  
Merge : `92b1321a0e23553641ea5dbe1f1c25c0acc874e3`.

### M5-S2 — intégré

```text
SnapshotBusinessContent
SnapshotBusinessContentStore
MemorySnapshotBusinessContentStore
SqliteSnapshotBusinessContentStore
SQLite V007
```

Familles snapshot-scoped :

```text
Specification
Scenario
ChangeProposal
Constraint
DesignDecision
ImplementationTask
Evidence / Provenance
```

Gate : **202/202 PASS**.  
ADR : **ADR-0044 — Acceptée — M5**.  
Merge : `3a39371518d9d327ea4cbee0994da65b218ec64c`.

### M5-S3 — intégré

```text
BusinessContentQueryService
SnapshotItemResult<T>
SnapshotPage<T>
```

Primitives validées :

```text
activeSpecification / snapshotSpecification
activeChange / snapshotChange
listActiveChanges / listSnapshotChanges
activeConstraints / snapshotConstraints
activeDesignDecisions / snapshotDesignDecisions
activeImplementationTasks / snapshotImplementationTasks
```

Gate : **210/210 PASS**.  
ADR : **ADR-0045 — Acceptée — M5**.  
Merge : `28c32ea2ede7b9144eb10a2a7fb60b0df44f2a73`.

### M5-S4 — implémenté / gate en attente

```text
TraceRequirementQueryService
ChangeContextQueryService
ChangeContextResult
```

`trace_requirement` réutilise exactement M4.

`get_change_context` compose dans un seul snapshot :

```text
ChangeProposal
AFFECTS directs
Requirement CURRENT résolus
Constraint
DesignDecision
ImplementationTask
TraceabilitySubgraph borné
ExternalTraceabilityView unresolved/broken
```

Invariants candidats :

```text
ACTIVE by default
ACTIVE/RETIRED explicit only
no ACTIVE != change not found
CURRENT only / PROPOSED excluded
AFFECTS direct only
broken AFFECTS target retained
bounded deterministic cycle-safe traversal
relation filter shapes trace view only
Memory == SQLite
SQLite reopen
no V008
no semantic/LLM/NEXUS ranking/fusion
```

Preuves ajoutées : `ChangeContextQueryContractTest` **7 tests**.  
Gate attendu : **217/217**.  
ADR : **ADR-0046 — Proposée — M5**.

Vue opérationnelle : [`roadmap/M5_EXECUTION.md`](roadmap/M5_EXECUTION.md).  
Issue : **#36**.

---

# 10. M6 — Qualité et couverture ⏳

Détecter : requirements orphelins, tâches sans requirement, critères non reliés/non vérifiés, changements incomplets, décisions sans justification, références cassées, couverture de traçabilité, blocages de transition et distinction déterministe/heuristique.

---

# 11. M7 — Synchronisation incrémentale et fraîcheur ⏳

Périmètre : fingerprints, source revisions, ajouts/modifications/suppressions, mouvements/renommages, archives, `INCREMENTAL_READ`, invalidation, watcher local, changement de format/version, fallback full rebuild et métriques de fraîcheur.

Invariant : **la fiabilité prime ; en cas de doute, full rebuild.**

---

# 12. M8 — Analyse des changements ⏳

Analyser l'étendue fonctionnelle/documentaire d'un changement : current/proposed, exigences ajoutées/modifiées/supprimées, contraintes, décisions, critères, dépendances et chemins explicatifs.

L'analyse du code reste MINOS.

---

# 13. M9 — CLI stabilisée et distribution locale ⏳

Commandes candidates :

```text
morpheus project add
morpheus project list
morpheus sync
morpheus specs
morpheus requirements
morpheus change get
morpheus change list
morpheus change status
morpheus constraints
morpheus acceptance
morpheus decisions
morpheus tasks
morpheus trace
morpheus context
morpheus versions
morpheus inspect
morpheus health
```

Distribution : native-first, archive portable, runtime Java embarqué à prouver, jlink/jpackage à évaluer, Windows + Linux.

---

# 14. M10 — Serveur MCP ⏳

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

# 15. M11 — API / headless ⏳

Périmètre : projets, spécifications, requirements, changements, contraintes, critères, traçabilité, versions, contexte, synchronisation, diagnostics et DTO stables.

M11 est le jalon naturel pour prouver une image Docker officielle si justifiée.

---

# 16. M12 — Intégration MINOS ⏳

Objectif : relier intention et code sans fusionner les domaines via `ExternalReference(system=MINOS, ...)`.

Périmètre : symboles, fichiers, modules, tests, Requirement → code, ChangeProposal → code, AcceptanceCriterion → tests, références non résolues conservées et indisponibilité MINOS tolérée.

---

# 17. M13 — Intégration NEXUS ⏳

MORPHEUS fournit intention, requirements, contraintes, décisions, critères, tâches, provenance et chemins.

NEXUS sélectionne, classe, fusionne et compresse le contexte global.

**MORPHEUS reste utilisable sans NEXUS.**

---

# 18. M14 — Orchestration JARVIS ⏳

MORPHEUS peut exposer : change state, allowed transitions, blocking conditions, acceptance status, unresolved references et specification context.

JARVIS décide de la séquence d'actions. MORPHEUS ne contient aucune logique JARVIS.

---

# 19. Explorations futures

Non engagées : génération assistée par LLM, recherche sémantique/embeddings, contradictions avancées, trackers externes, éditeur visuel, collaboration temps réel, providers distants, composition multi-provider de production, fédération multi-projets, event sourcing complet, conformité automatique code ↔ spécification et mutations orchestrées par agents.

---

# 20. Règle de pilotage

À chaque slice :

```text
1. documenter l'invariant / ADR
2. implémenter le plus petit vertical slice
3. ajouter les preuves contractuelles
4. exécuter .\mvnw.cmd clean test
5. accepter l'ADR uniquement après preuve
6. merger seulement après signal explicite
7. mettre à jour roadmap + issue
```

**Prochaine porte : gate local M5-S4 attendu 217/217.**
