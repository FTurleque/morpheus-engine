# Feuille de route — MORPHEUS

Statut : **Roadmap active — C0 à M4 validés et intégrés ; M5 actif — 5/6 validés ; S1-S4 intégrés, S5 Ready, S6 prochain après merge**

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
| M3 | État temporel, lifecycle, snapshots, versions | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M3.md`, 147/147 tests |
| M4 | Traçabilité | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M4.md`, 189/189 tests |
| **M5** | **Requêtes et contexte compact** | **🚧 ACTIF — 5/6 VALIDÉS** | S1 196/196 merged ; S2 202/202 merged ; S3 210/210 merged ; S4 217/217 merged ; S5 227/227 Ready ; S6 prochain après merge |
| M6 | Qualité / couverture | ⏳ PLANIFIÉ | après M5 |
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
- [`roadmap/M5_EXECUTION.md`](roadmap/M5_EXECUTION.md)
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

Les expériences E01 à E14 ont validé notamment : provider detection, domain mapping, stable identity / UUIDv7, current reconstruction, lifecycle, knowledge snapshots, traceability, Memory + SQLite, recherche lexicale, contexte compact et références externes.

Décisions héritées :

```text
lexical search déterministe = ADOPTÉ
semantic search = NOT_REQUIRED_FOR_MVP
compact context MORPHEUS = ADOPTÉ
global ranking / multi-engine fusion / token compression = NEXUS
```

Porte : **VALIDÉE — ADOPTER AVEC CONTRAINTES**.

---

# 5. M1 — Discovery, providers et fondation store ✅

Discovery explicit-first, provider registry/capabilities, OpenSpec read-only, diagnostics, UUIDv7, Memory store, SQLite et migrations de fondation.

```text
42/42 PASS
BUILD SUCCESS
```

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

Gate final : **94/94 PASS**.

---

# 7. M3 — État temporel, lifecycle, snapshots et versions ✅

Question de sortie :

> **MORPHEUS peut-il publier et requêter un état `CURRENT` cohérent tout en conservant séparément les propositions, l'historique et les changements en cours, sans jamais exposer un snapshot partiellement construit ?**

**Réponse : OUI.**

Invariants principaux :

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

`get_acceptance_criteria` reste conditionnel à une sémantique explicite ; aucun `Scenario` n'est transformé artificiellement en `AcceptanceCriterion`.

Inclut : recherche lexicale, pagination, limites, vues compactes, warnings, provenance/evidence et JSON déterministe.

N'inclut pas : ranking global, fusion multi-engine ou compression par budget de tokens ; ces responsabilités restent NEXUS.

## Progression

| Slice | Contenu | État |
|---|---|---|
| **S1** | **`find_requirements` + pagination déterministe** | **✅ MERGED — PR #37 — ADR-0043 — 196/196** |
| **S2** | **projection métier requêtable des autres familles** | **✅ MERGED — PR #38 — ADR-0044 — 202/202** |
| **S3** | **getters/lists déterministes** | **✅ MERGED — PR #39 — ADR-0045 — 210/210** |
| **S4** | **`trace_requirement` query view + `get_change_context`** | **✅ MERGED — PR #40 — ADR-0046 — 217/217** |
| **S5** | **vues compactes + warnings/provenance + JSON déterministe** | **✅ VALIDÉ — PR #41 Ready — ADR-0047 — 227/227** |
| **S6** | **validation finale `VALIDATION_M5.md`** | **⏳ PROCHAIN APRÈS MERGE S5** |

### M5-S1

```text
RequirementQueryService
ACTIVE by default
ACTIVE/RETIRED explicit
CURRENT only
lexical deterministic search
bounded pagination
Memory == SQLite
SQLite reopen
```

Gate : **196/196 PASS**.

### M5-S2

Projection snapshot-scoped des familles métier hors `Requirement`, avec SQLite V007 normalisée sans payload JSON métier.

Gate : **202/202 PASS**.

### M5-S3

Getters/listes déterministes pour `Specification`, `ChangeProposal`, `Constraint`, `DesignDecision`, `ImplementationTask`, avec not-found explicite et pagination stable. Aucun `AcceptanceCriterion` synthétique.

Gate : **210/210 PASS**.

### M5-S4

```text
TraceRequirementQueryService
ChangeContextQueryService
ChangeContextResult
```

`trace_requirement` réutilise M4. `get_change_context` agrège un seul snapshot publié, conserve les liens `AFFECTS` directs, les requirements `CURRENT`, constraints, decisions, tasks, sous-graphe borné et références externes unresolved/broken.

Gate : **217/217 PASS**.

### M5-S5 — validé techniquement

```text
CompactQueryTypes
CompactRequirementSearchView
CompactTraceRequirementView
CompactChangeContextView
CompactQueryViewService
CompactWarningCode
CanonicalJsonSerializer
```

Invariants :

```text
schemaVersion = 1
snapshot + pagination metadata
RequirementId != EntityVersionId visible
SpecificationVersionId + TemporalState explicites
provenance/evidence conservées
warnings structurés dérivés de faits
JSON canonique byte-identical
no third-party JSON dependency
no persistence/migration/store adapter change
no semantic/LLM/NEXUS ranking-fusion-compression
```

Gate :

```text
Architecture tests 100/100 PASS
TOTAL              227/227 PASS
Failures             0
Errors               0
Skipped              0
BUILD SUCCESS
```

ADR : **ADR-0047 — Acceptée — M5**.

## M5-S6 — prochaine porte

Créer [`VALIDATION_M5.md`](VALIDATION_M5.md) et consolider les preuves S1-S5. S6 ne doit ajouter une capacité métier que si un gap réel est découvert ; sinon il s'agit d'une slice de validation/documentation avec gate final.

Vue opérationnelle : [`roadmap/M5_EXECUTION.md`](roadmap/M5_EXECUTION.md).  
Issue : **#36**.

---

# 10. M6 — Qualité et couverture ⏳

Détecter : requirements orphelins, tâches sans requirement, critères non reliés/non vérifiés, changements incomplets, décisions sans justification, références cassées, couverture de traçabilité, blocages de transition et distinction déterministe/heuristique.

---

# 11. M7 — Synchronisation incrémentale et fraîcheur ⏳

Périmètre : fingerprints, source revisions, ajouts/modifications/suppressions, mouvements/renommages, archives, invalidation, watcher local, fallback full rebuild et métriques de fraîcheur.

Invariant : **la fiabilité prime ; en cas de doute, full rebuild.**

---

# 12. M8 — Analyse des changements ⏳

Analyser l'étendue fonctionnelle/documentaire d'un changement : current/proposed, exigences ajoutées/modifiées/supprimées, contraintes, décisions, critères, dépendances et chemins explicatifs. L'analyse du code reste MINOS.

---

# 13. M9 — CLI stabilisée et distribution locale ⏳

CLI publique stabilisée, distribution native-first, archive portable, runtime Java embarqué à prouver, Windows + Linux.

---

# 14. M10 — Serveur MCP ⏳

Transport local prioritaire : `stdio`. Les handlers MCP restent minces et réutilisent les services applicatifs M5.

---

# 15. M11 — API / headless ⏳

API/headless autour des contrats applicatifs stabilisés. Docker officiel uniquement si justifié par le mode serveur.

---

# 16. M12 — Intégration MINOS ⏳

Relier intention et code via `ExternalReference(system=MINOS, ...)`, sans fusionner les domaines.

---

# 17. M13 — Intégration NEXUS ⏳

MORPHEUS fournit intention, requirements, contraintes, décisions, critères, tâches, provenance et chemins. NEXUS sélectionne, classe, fusionne et compresse le contexte global.

**MORPHEUS reste utilisable sans NEXUS.**

---

# 18. M14 — Orchestration JARVIS ⏳

MORPHEUS expose les faits et transitions ; JARVIS décide de la séquence d'actions. Aucune logique JARVIS dans le cœur MORPHEUS.

---

# 19. Explorations futures

Non engagées : génération assistée par LLM, recherche sémantique/embeddings, contradictions avancées, trackers externes, éditeur visuel, collaboration temps réel, providers distants, composition multi-provider de production, fédération multi-projets, event sourcing complet, conformité automatique code ↔ spécification et mutations orchestrées par agents.

---

# 20. Règle de pilotage

```text
1. documenter l'invariant / ADR
2. implémenter le plus petit vertical slice
3. ajouter les preuves contractuelles
4. exécuter .\mvnw.cmd clean test
5. accepter l'ADR uniquement après preuve
6. merger seulement après signal explicite
7. mettre à jour roadmap + issue
```

**Prochaine ligne active après merge S5 : M5-S6 — validation finale de M5.**
