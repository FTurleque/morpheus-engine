# Feuille de route — MORPHEUS

Statut : **Roadmap active — C0 à M2 validés ; M3 autorisée**

Date de dernière mise à jour : 22 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves. Un jalon n'est pas terminé parce que son code existe : il doit disposer de contrats stables, de tests, d'ADR cohérentes et d'une preuve de sortie explicite.

---

# 1. Vue globale

| Jalon | Sujet | Statut | Preuve / prochaine porte |
|---|---|---|---|
| C0 | Cadrage fonctionnel et architectural | ✅ VALIDÉ | `VALIDATION_C0.md` |
| M0 | Faisabilité technique | ✅ VALIDÉ | `VALIDATION_M0.md` |
| M1 | Discovery, providers et fondation store | ✅ VALIDÉ | `VALIDATION_M1.md`, 42/42 tests |
| M2 | Ingestion et modèle normalisé | ✅ VALIDÉ | `VALIDATION_M2.md`, 94/94 tests |
| **M3** | **État temporel, lifecycle, snapshots, versions** | **🚀 AUTORISÉ / PROCHAIN** | ouvrir après merge de #19 |
| M4 | Traçabilité | ⏳ PLANIFIÉ | après modèle temporel stable |
| M5 | Requêtes et contexte compact | ⏳ PLANIFIÉ | après M4 |
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
- [`roadmap/M2_EXECUTION.md`](roadmap/M2_EXECUTION.md)
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
DomainIdentity != EntityVersion != SourceLocator != ExternalReference
Scenario != AcceptanceCriterion par défaut
provider facts != MORPHEUS domain
backend details != domain
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
- étude OpenSpec ;
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

## Preuves principales

```text
E01  provider detection
E02  domain mapping
E03  stable identity semantics
E03b UUIDv7
E04  current reconstruction
E04b change lifecycle
E05  knowledge snapshots
E05b rebuild / retention
E06  traceability
E06b store-backed traceability
E07  memory store
E08  SQLite
E09  graph DB NOT_NEEDED_FOR_MVP
E10  lexical search
E11  incremental inventory/invalidation
E12  diagnostics
E13  compact context
E14  external references
```

Porte : **VALIDÉE — ADOPTER AVEC CONTRAINTES**.

---

# 5. M1 — Discovery, providers et fondation store ✅

## Objectif

Détecter les sources de spécification et sélectionner un provider compatible de manière déterministe.

## Livré

- registre local des projets ;
- workspace discovery explicit-first ;
- fallback `.git` structurel sans binaire Git ;
- `SpecificationProviderRegistry` ;
- probes/capabilities ;
- required/preferred capabilities ;
- local-first ;
- remote opt-in ;
- diagnostics structurés ;
- provider OpenSpec `spec-driven` read-only ;
- `SourceLocator` provider-neutral ;
- UUIDv7 ;
- memory store de référence ;
- SQLite derrière le port ;
- migrations V001/V002/V003 ;
- tests d'architecture.

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

## Domaine stabilisé

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

`AcceptanceCriterion` est conservé comme concept cible uniquement lorsqu'une source expose une sémantique explicite.

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

## Contrat de lecture

```text
SpecificationProvider.probe()
        !=
SpecificationContentReader.read()
```

Résultat :

```text
ProviderReadResult
├── NormalizedProjectContent?
├── ReadCategoryReport[]
└── Diagnostic[]
```

Statuts :

```text
READ
ABSENT
UNSUPPORTED
FAILED
PARTIAL
```

## Anti-lock-in

```text
OpenSpec source ─────┐
                     ├──> SpecificationContentReader
Synthetic JSON ──────┘          ↓
                         ProviderReadResult
                               ↓
                      NormalizedProjectContent
```

Le second provider synthétique est `verification-only` et n'a nécessité aucune modification du domaine ou de l'application.

## Persistance à la sortie M2

Persisté :

```text
projects
knowledge snapshot metadata
entity identity bindings
migration ledger
```

Différé à M3 par ADR-0030 :

```text
premières tables métier complètes
TemporalState
SpecificationVersion
KnowledgeSnapshot complet
snapshot/version membership
```

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

# 7. M3 — État temporel, lifecycle, snapshots et versions 🚀

## Objectif

Reconstruire l'état de référence, les évolutions proposées et l'historique sans ambiguïté, puis persister ce modèle avec une ownership explicite par version/snapshot.

## Question de sortie

> **MORPHEUS peut-il publier et requêter un état `CURRENT` cohérent tout en conservant séparément les propositions, l'historique et les changements en cours, sans jamais exposer un snapshot partiellement construit ?**

## Slices candidats

### M3-S1 — TemporalState et SpecificationVersion

```text
CURRENT
PROPOSED
HISTORICAL
SpecificationVersion
```

À prouver :

- un contenu `PROPOSED` ne fuit jamais dans une requête `CURRENT` ;
- une identité stable peut avoir plusieurs occurrences/version states ;
- `DomainIdentity != EntityVersion` reste vrai ;
- la version logique reste distincte d'une réingestion technique.

### M3-S2 — ChangeLifecycleState

```text
DRAFT
PROPOSED
SPECIFIED
DESIGNED
PLANNED
IMPLEMENTING
VERIFYING
COMPLETED
ARCHIVED
ABANDONED
```

À prouver :

- transitions autorisées/interdites ;
- `SPECIFIED -> PLANNED` autorisé si `design_required=false` ;
- transitions backward selon politique ;
- `COMPLETED` n'implique pas promotion automatique en `CURRENT`.

### M3-S3 — KnowledgeSnapshot complet

```text
BUILDING
VALIDATING
READY
ACTIVE
FAILED
RETIRED
```

À livrer :

- construction ;
- validation ;
- publication ;
- activation atomique ;
- predecessor ;
- détection du predecessor obsolète ;
- aucune visibilité partielle.

### M3-S4 — Premières migrations métier versionnées

Concevoir les premières tables métier avec ownership explicite :

```text
content occurrence
    ↓
SpecificationVersion / KnowledgeSnapshot
```

Familles candidates :

```text
specifications
requirements
changes
constraints
scenarios
design_decisions
acceptance_criteria
implementation_tasks
external_references
provenance/evidence
```

Aucune table ne doit ambiguïser l'identité stable et l'occurrence versionnée.

### M3-S5 — Application / promotion des deltas

```text
ADDED
MODIFIED
REMOVED
```

À prouver :

- coexistence current/proposed ;
- application déterministe ;
- promotion explicite ;
- aucune promotion implicite à `COMPLETED` ;
- provenance de l'opération conservée.

### M3-S6 — Historique, comparaison et rétention

Comparer :

```text
ADDED
MODIFIED
REMOVED
UNCHANGED
MOVED / RENAMED si identité suffisante
```

À définir :

- rétention ;
- reconstruction ;
- rollback logique ;
- explicabilité entre deux snapshots.

## Porte de sortie M3

`get_current_specification` ne doit jamais contenir implicitement un delta seulement proposé, y compris pendant une resynchronisation ou une activation de snapshot.

---

# 8. M4 — Traçabilité ⏳

## Objectif

Relier les éléments de spécification et expliquer leur origine.

## Périmètre

```text
TraceabilityLink
REFINES
DERIVES_FROM
CONSTRAINS
SATISFIES
IMPLEMENTS
VALIDATES
VERIFIED_BY
DEPENDS_ON
AFFECTS
DECIDED_BY
SUPERSEDES
LINKS_TO_CODE
LINKS_TO_TEST
RELATED_TO
```

À livrer :

- direction canonique ;
- inverse éventuel ;
- origine ;
- résolution ;
- confiance ;
- preuves ;
- références cassées ;
- chemins de traçabilité ;
- change → requirement ;
- requirement → scenario/criterion/task ;
- design decision → change.

Porte : `trace <requirement>` produit un sous-graphe normalisé et explicable.

---

# 9. M5 — Requêtes et contexte compact ⏳

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

À détecter :

- requirements orphelins ;
- tâches sans requirement ;
- critères d'acceptation non reliés ;
- critères non vérifiés ;
- changements incomplets ;
- décisions sans justification ;
- références cassées ;
- couverture de traçabilité ;
- blocages de transition ;
- diagnostics déterministes vs heuristiques.

---

# 11. M7 — Synchronisation incrémentale et fraîcheur ⏳

Périmètre :

- empreintes ;
- source revisions ;
- fichiers ajoutés/modifiés/supprimés ;
- mouvements/renommages ;
- archives ;
- `INCREMENTAL_READ` ;
- invalidation ;
- watcher local ;
- format/version modifié ;
- fallback full rebuild ;
- métriques de fraîcheur.

Invariant : **la fiabilité prime ; en cas de doute, full rebuild.**

---

# 12. M8 — Analyse des changements ⏳

## Objectif

Analyser l'étendue fonctionnelle/documentaire d'un changement.

Inclut :

- comparaison current/proposed ;
- exigences ajoutées/modifiées/supprimées ;
- contraintes affectées ;
- décisions ;
- critères ;
- changements dépendants ;
- chemins explicatifs ;
- limites explicites des inférences.

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

Distribution selon ADR-0027 :

```text
native-first
archive portable
runtime Java embarqué à prouver
jlink / jpackage à évaluer
Windows + Linux
```

Les mutations restent hors périmètre sans ADR d'écriture acceptée.

---

# 14. M10 — Serveur MCP ⏳

Transport local prioritaire :

```text
stdio natif
```

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

Un conteneur headless reste optionnel si un transport réseau justifie son coût.

---

# 15. M11 — API / headless ⏳

Périmètre :

- projets ;
- spécifications ;
- requirements ;
- changements ;
- contraintes ;
- critères ;
- traçabilité ;
- versions ;
- contexte ;
- synchronisation ;
- diagnostics ;
- DTO stables.

Le framework serveur reste différé jusqu'à ce jalon.

Selon ADR-0027, M11 est le jalon naturel pour prouver une **image Docker officielle** :

```text
workspace montable read-only
state SQLite externalisé
healthcheck
config explicite
même core que le mode natif
```

---

# 16. M12 — Intégration MINOS ⏳

## Objectif

Relier intention et code sans fusionner les domaines.

```text
ExternalReference(system=MINOS, ...)
```

Périmètre : symboles, fichiers, modules, tests, Requirement → code, ChangeProposal → code, AcceptanceCriterion → tests, références non résolues conservées, indisponibilité MINOS tolérée.

---

# 17. M13 — Intégration NEXUS ⏳

MORPHEUS fournit : intention, requirements, contraintes, décisions, critères, tâches, provenance et chemins.

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

Non engagées dans la roadmap principale :

- génération assistée par LLM ;
- recherche sémantique / embeddings ;
- contradictions avancées ;
- Jira / GitHub Issues / trackers ;
- éditeur visuel ;
- collaboration temps réel ;
- providers distants ;
- composition multi-provider de production ;
- fédération multi-projets ;
- event sourcing complet ;
- conformité automatique code ↔ spécification ;
- mutations orchestrées de specs par agents.

---

# 20. Règle de pilotage

À chaque slice :

```text
1. documenter l'invariant / ADR
2. implémenter le plus petit vertical slice
3. ajouter les preuves contractuelles
4. exécuter .\mvnw.cmd clean test
5. accepter l'ADR uniquement après preuve
6. merger
7. mettre à jour ce tableau de bord
```

**Prochaine ligne active : M3-S1 — TemporalState et SpecificationVersion.**
