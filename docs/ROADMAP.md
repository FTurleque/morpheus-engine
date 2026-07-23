# Feuille de route — MORPHEUS

Statut : **Roadmap active — C0 à M4 validés et intégrés ; M5 autorisé / prochain**

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
| **M4** | **Traçabilité** | **✅ VALIDÉ / INTÉGRÉ** | `VALIDATION_M4.md`, 6/6, 189/189 tests, PR #34 |
| **M5** | **Requêtes et contexte compact** | **▶ AUTORISÉ / PROCHAIN** | démarrer après clôture M4 |
| M6 | Qualité / couverture | ⏳ PLANIFIÉ | après primitives de requête |
| M7 | Synchronisation incrémentale | ⏳ PLANIFIÉ | après snapshots stables |
| M8 | Analyse des changements | ⏳ PLANIFIÉ | après M3/M4 |
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

Et :

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

## Objectif

Définir précisément ce que MORPHEUS doit fournir avant le développement fonctionnel.

## Livré

- cahier des charges ;
- position dans l'écosystème ;
- périmètre MVP ;
- modèle de domaine ;
- cycle de vie des changements ;
- cas d'usage prioritaires ;
- stratégie `SpecificationProvider` ;
- négociation de capacités ;
- stratégie `SpecificationKnowledgeStore` ;
- stratégie d'identité ;
- snapshots/versionnement ;
- modèle de traçabilité ;
- critères de validation ;
- ADR structurantes ;
- matrice d'expérimentation M0.

Porte : **VALIDÉE**.

---

# 4. M0 — Faisabilité technique ✅

## Objectif

Valider les choix structurants par expérimentation réelle.

Preuves principales : provider detection, domain mapping, stable identity, current reconstruction, change lifecycle, knowledge snapshots, traceability, Memory/SQLite, lexical search, incremental inventory, diagnostics, compact context et external references.

Décision structurante : **graph DB non requise pour le MVP**.

Porte : **VALIDÉE — ADOPTER AVEC CONTRAINTES**.

---

# 5. M1 — Discovery, providers et fondation store ✅

## Objectif

Détecter les sources de spécification et sélectionner un provider compatible de manière déterministe.

Livré :

```text
registre local des projets
workspace discovery explicit-first
SpecificationProviderRegistry
probes/capabilities
required/preferred capabilities
local-first
remote opt-in
diagnostics structurés
provider OpenSpec read-only
SourceLocator provider-neutral
UUIDv7
Memory store
SQLite + migrations V001/V002/V003
architecture tests
```

Preuve :

```text
42/42 PASS
BUILD SUCCESS
```

Porte : **VALIDÉE — M2 AUTORISÉE**.

---

# 6. M2 — Ingestion et modèle normalisé ✅

## Objectif

Transformer les sources supportées en concepts MORPHEUS indépendants du provider.

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

`AcceptanceCriterion` reste un concept cible uniquement lorsqu'une source expose une sémantique explicite.

## Huit slices validés

| Slice | Contenu | PR | ADR | Gate |
|---|---|---|---|---|
| S1 | domaine courant + provenance/evidence | #10 | ADR-0022 | 48/48 |
| S2 | identité persistante provider-scoped | #11 | ADR-0023 | 58/58 |
| S3 | change / constraint / decision / task | #12 | ADR-0024 | 64/64 |
| S4 | requirement deltas | #13 | ADR-0025 | 70/70 |
| S5 | ExternalReference + résolution optionnelle | #15 | ADR-0026 | 76/76 |
| S6 | lecture unifiée + partiel + diagnostics | #17 | ADR-0028 | 84/84 |
| S7 | second provider anti-lock-in | #18 | ADR-0029 | 94/94 |
| S8 | audit final + frontière persistance | #19 | ADR-0030 | 94/94 |

Contrat de lecture :

```text
SpecificationProvider.probe()
        !=
SpecificationContentReader.read()
```

Le second provider synthétique reste `verification-only` et n'a nécessité aucune modification du domaine ou de l'application.

Preuve finale :

```text
94/94 PASS
Failures = 0
Errors   = 0
Skipped  = 0
BUILD SUCCESS
```

Porte : **VALIDÉE — M3 AUTORISÉE**.

---

# 7. M3 — État temporel, lifecycle, snapshots et versions ✅

## Question de sortie

> **MORPHEUS peut-il publier et requêter un état `CURRENT` cohérent tout en conservant séparément les propositions, l'historique et les changements en cours, sans jamais exposer un snapshot partiellement construit ?**

**Réponse : OUI.**

## Six slices validés et intégrés

| Slice | Contenu | PR | ADR | Gate |
|---|---|---|---|---|
| S1 | `TemporalState` + `SpecificationVersion` + `EntityVersionId` | #21 | ADR-0031 | 103/103 |
| S2 | `ChangeLifecycleState` explicite | #22 | ADR-0032 | 119/119 |
| S3 | lifecycle complet `KnowledgeSnapshot` + activation atomique | #23 | ADR-0033 | 127/127 |
| S4 | persistance métier versionnée `Requirement` | #24 | ADR-0034 | 134/134 |
| S5 | application / promotion explicites des `RequirementDelta` | #25 | ADR-0035 | 142/142 |
| S6 | historique publié / comparaison / rollback logique / rétention | #26 | ADR-0036 | 147/147 |

Invariants :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
COMPLETED != CURRENT
COMPLETED != promotion
COMPLETED != activation
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

Preuve finale :

```text
147/147 PASS
Failures = 0
Errors   = 0
Skipped  = 0
BUILD SUCCESS
```

Validation : [`VALIDATION_M3.md`](VALIDATION_M3.md).

Merge final M3 : PR #26 — `30f4ea43c55b5f6ff7cf235d0d1acc75ab4053fa`.

Porte : **VALIDÉE — M4 AUTORISÉE**.

---

# 8. M4 — Traçabilité ✅

## Question de sortie

> **MORPHEUS peut-il relier les éléments d'intention/specification par des relations typées, directionnelles et explicables, conserver les liens non résolus, puis produire un sous-graphe borné et déterministe sans dépendre d'un backend graphe ?**

**Réponse : OUI.**

## Six slices validés et intégrés

| Slice | Contenu | PR | ADR | Gate | État |
|---|---|---|---|---|---|
| S1 | domaine `TraceabilityLink` + taxonomie contrôlée | #28 | ADR-0037 | 155/155 | ✅ MERGED |
| S2 | persistance snapshot-scoped Memory + SQLite | #29 | ADR-0038 | 160/160 | ✅ MERGED |
| S3 | dérivation déterministe depuis `NormalizedProjectContent` | #31 | ADR-0039 | 167/167 | ✅ MERGED |
| S4 | direct / inverse / traversal / path | #32 | ADR-0040 | 174/174 | ✅ MERGED |
| S5 | external / unresolved / broken-reference semantics | #33 | ADR-0041 | 184/184 | ✅ MERGED |
| S6 | validation finale `trace(requirement)` | #34 | ADR-0042 | 189/189 | ✅ MERGED |

Merges :

```text
S1 = 07d9bb1c2c85501ad5a5f6a1eab562a27ec53e9f
S2 = 32694f2c74aa9ce4248f9eea907d85460de93eff
S3 = 4b3bb5c79e65b8f1501b9949b49f4940294c4312
S4 = cafbc8e61a4af2ed204cd6fc24dcdd262f6ed9e4
S5 = e25aebf0479dfa9d1f146df4d2af0f072b551d39
S6 = ac317eb63bbe0edb854c04660c5c143ba46e0c43
```

## Modèle et persistance

```text
TraceabilityLink first-class
14 relations contrôlées
direction canonique
origin / resolution / confidence séparés
evidence obligatoire
snapshot-scoped
Memory == SQLite
```

SQLite V005 :

```text
traceability_links
traceability_link_evidence
snapshot_traceability_links
```

SQLite V006 :

```text
snapshot_external_references
snapshot_external_reference_attributes
snapshot_external_reference_history
```

## Dérivation

```text
Requirement -> Specification        DERIVES_FROM
Scenario -> Requirement             REFINES
Constraint -> Change                CONSTRAINS
Change -> DesignDecision            DECIDED_BY
Change -> Requirement               AFFECTS
```

Aucun fuzzy matching, LLM/embedding ou identité de lien cachée n'est requis.

## Traversal

```text
OUTGOING / INCOMING / BIDIRECTIONAL
maxDepth > 0
BFS borné
cycle-safe
ordre déterministe
relation filters
shortest path déterministe
inverse query != seconde arête persistée
traversal != transitivity
```

## Références externes

```text
UNRESOLVED reste visible
STALE reste explicable
BROKEN_REFERENCE reste visible
TraceabilityResolutionState != ExternalReferenceResolutionState
resolver externe != mutation du lien canonique
MINOS indisponible != MORPHEUS indisponible
```

## Porte finale

```text
TraceRequirementService.traceActive(...)
TraceRequirementService.traceSnapshot(...)
```

Règles :

```text
ACTIVE courant uniquement via traceActive
ACTIVE/RETIRED explicites via traceSnapshot
snapshots non publiés rejetés
CURRENT requirement obligatoire
racine REQUIREMENT + RequirementId.value
BIDIRECTIONAL
Memory == SQLite
SQLite reopen conserve la vue finale
```

Scénario final :

```text
Scenario -> Requirement               REFINES
Change -> Requirement                 AFFECTS
Constraint -> Change                  CONSTRAINS
Change -> DesignDecision              DECIDED_BY
DesignDecision -> Specification       profondeur 3
DesignDecision -> Change              cycle réel
Requirement -> ExternalReference      UNRESOLVED
Requirement -> ExternalReference      BROKEN_REFERENCE
```

Preuve finale :

```text
TraceRequirementFinalValidationTest  5/5 PASS
TOTAL                              189/189 PASS
Failures                            0
Errors                              0
Skipped                             0
BUILD SUCCESS
```

Gate final : **23 juillet 2026 à 14:57:23 +02:00**.

Merge final : PR #34 — `ac317eb63bbe0edb854c04660c5c143ba46e0c43`.

Validation : [`VALIDATION_M4.md`](VALIDATION_M4.md).

Porte : **VALIDÉE — M5 AUTORISÉ**.

---

# 9. M5 — Requêtes et contexte compact ▶

## Objectif

Rendre MORPHEUS directement interrogeable par humains, scripts et agents.

Primitives prévues :

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

Inclut : recherche lexicale, pagination, limites, JSON compact, warnings et provenance.

---

# 10. M6 — Qualité et couverture ⏳

À détecter : requirements orphelins, tâches sans requirement, critères d'acceptation non reliés/non vérifiés, changements incomplets, décisions sans justification, références cassées, couverture de traçabilité, blocages de transition et distinction déterministe/heuristique.

---

# 11. M7 — Synchronisation incrémentale et fraîcheur ⏳

Périmètre : empreintes, source revisions, fichiers ajoutés/modifiés/supprimés, mouvements/renommages, archives, `INCREMENTAL_READ`, invalidation, watcher local, changement de format/version, fallback full rebuild et métriques de fraîcheur.

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

Distribution selon ADR-0027 : native-first, archive portable, runtime Java embarqué à prouver, jlink/jpackage à évaluer, Windows + Linux.

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

Objectif : relier intention et code sans fusionner les domaines.

```text
ExternalReference(system=MINOS, ...)
```

Périmètre : symboles, fichiers, modules, tests, Requirement → code, ChangeProposal → code, AcceptanceCriterion → tests, références non résolues conservées et indisponibilité MINOS tolérée.

---

# 17. M13 — Intégration NEXUS ⏳

MORPHEUS fournit intention, requirements, contraintes, décisions, critères, tâches, provenance et chemins.

NEXUS sélectionne, classe, fusionne et compresse le contexte global.

**MORPHEUS reste utilisable sans NEXUS.**

---

# 18. M14 — Orchestration JARVIS ⏳

MORPHEUS peut exposer :

```text
change state
allowed transitions
blocking conditions
acceptance status
unresolved references
specification context
```

JARVIS décide de la séquence d'actions. MORPHEUS ne contient aucune logique JARVIS.

---

# 19. Explorations futures

Non engagées dans la roadmap principale : génération assistée par LLM, recherche sémantique/embeddings, contradictions avancées, trackers externes, éditeur visuel, collaboration temps réel, providers distants, composition multi-provider de production, fédération multi-projets, event sourcing complet, conformité automatique code ↔ spécification et mutations orchestrées par agents.

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
7. mettre à jour ce tableau de bord
```

**Prochaine ligne active : M5 — Requêtes et contexte compact.**
