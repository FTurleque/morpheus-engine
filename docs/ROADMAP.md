# Feuille de route — MORPHEUS

Statut : **Roadmap active — C0 à M3 validés ; M4 en cours — 2/6 ; S3 prochain**

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
| M3 | État temporel, lifecycle, snapshots, versions | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M3.md`, 6/6, 147/147 tests, PR #26 |
| **M4** | **Traçabilité** | **🚧 EN COURS — 2/6** | S3 prochain ; dernier gate 160/160 |
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
- [`VALIDATION_M3.md`](VALIDATION_M3.md)
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

# 7. M3 — État temporel, lifecycle, snapshots et versions ✅

## Objectif

Reconstruire l'état de référence, les évolutions proposées et l'historique sans ambiguïté, puis persister ce modèle avec une ownership explicite par version/snapshot.

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

## Invariants stabilisés

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
COMPLETED != CURRENT
COMPLETED != promotion
COMPLETED != activation
```

Lifecycle snapshot :

```text
BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED
                         \-> FAILED
```

Règles :

- seul `ACTIVE` est observable comme snapshot courant ;
- un seul `ACTIVE` par projet ;
- un predecessor obsolète est rejeté ;
- `FAILED` n'évince jamais l'`ACTIVE` ;
- `ACTIVE` et `RETIRED` ne sont produits que par `activate` ;
- un snapshot `RETIRED` n'est jamais réactivé.

## Persistance versionnée

SQLite V004 :

```text
specification_versions
snapshot_specification_versions
requirement_versions
```

Invariants :

```text
SpecificationVersion 1:N KnowledgeSnapshot
snapshot/version ownership explicite
1 CURRENT max par (snapshot, DomainIdentity)
N PROPOSED concurrents permis
vue courante = ACTIVE snapshot + CURRENT
aucune payload JSON générique
```

## Deltas et publication

```text
normalized delta != applied delta
APPLY != PROMOTE
PROMOTE != ACTIVATE
```

- `ADDED` n'utilise aucun fuzzy matching ;
- `MODIFIED` conserve la `DomainIdentity` et crée un nouvel `EntityVersionId` ;
- `REMOVED` retire l'occurrence de la projection candidate sans muter l'ACTIVE ;
- un mouvement cross-specification n'est pas inventé implicitement.

## Historique publié

```text
RETIRED* -> ACTIVE
```

- seules les publications `ACTIVE/RETIRED` font partie de l'historique publié ;
- comparaison : `ADDED / MODIFIED / REMOVED / UNCHANGED` ;
- un `EntityVersionId` différent ne suffit pas à produire `MODIFIED` ;
- rollback logique = nouvelle publication via `RequirementDelta -> APPLY -> PROMOTE -> ACTIVATE` ;
- rétention : `KEEP_ALL_PUBLISHED` ;
- aucune TTL, purge ou compaction en M3.

## Preuve finale M3

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

# 8. M4 — Traçabilité 🚧

## Objectif

Relier les éléments de spécification et expliquer leur origine.

## Question de sortie

> **MORPHEUS peut-il relier les éléments d'intention/specification par des relations typées, directionnelles et explicables, conserver les liens non résolus, puis produire un sous-graphe borné et déterministe sans dépendre d'un backend graphe ?**

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

## Progression M4

| Slice | Contenu | PR | ADR | Gate | État |
|---|---|---|---|---|---|
| S1 | domaine `TraceabilityLink` + taxonomie contrôlée | #28 | ADR-0037 | 155/155 | ✅ MERGED |
| S2 | persistance snapshot-scoped Memory + SQLite | #29 | ADR-0038 | 160/160 | ✅ MERGED |
| **S3** | **dérivation déterministe depuis `NormalizedProjectContent`** | — | — | — | **🚧 PROCHAIN** |
| S4 | direct / inverse / traversal / path | — | — | — | ⏳ |
| S5 | external / unresolved / broken-reference semantics | — | — | — | ⏳ |
| S6 | validation finale `trace(requirement)` | — | — | — | ⏳ |

## M4-S1 — domaine et taxonomie

Validé :

```text
TraceabilityLinkId explicite
TraceabilityLinkId != hash(source,type,target)
endpoint = EntityKind + DomainIdentity
14 relations MVP contrôlées
relation type != origin != resolution
confidence bornée [0,1]
heuristic => confidence obligatoire
evidence obligatoire
direction canonique
inverse = vue de requête, pas seconde preuve
```

## M4-S2 — persistance snapshot-scoped

Validé :

```text
TraceabilityStore
MemoryTraceabilityStore
SqliteTraceabilityStore
TraceabilityLink definition != snapshot membership
KnowledgeSnapshotId obligatoire
same link id + different definition = collision
snapshot A links != snapshot B links
Memory == SQLite contract
```

SQLite V005 :

```text
traceability_links
traceability_link_evidence
snapshot_traceability_links
```

Preuve :

```text
TraceabilityPersistenceContractTest  5/5 PASS
Architecture tests                  45/45 PASS
TOTAL                              160/160 PASS
BUILD SUCCESS
```

Merge S2 : PR #29 — `32694f2c74aa9ce4248f9eea907d85460de93eff`.

## NOW — M4-S3

Dérivation déterministe depuis le modèle normalisé :

```text
Requirement -> Specification        DERIVES_FROM
Scenario -> Requirement             REFINES
Constraint -> Change                CONSTRAINS
Change -> DesignDecision            DECIDED_BY
Change -> Requirement               AFFECTS si RequirementDelta fournit l'identité
```

Contraintes :

```text
pas de fuzzy matching
pas de rapprochement par titre
pas de rapprochement par chemin
pas de LLM
pas d'identité de lien cachée dérivée d'un hash d'arête
pas d'inférence Task -> Requirement sans fait source
```

Toute dérivation doit conserver l'evidence qui la justifie.

Porte M4 : `trace <requirement>` produit un sous-graphe normalisé, borné, déterministe et explicable.

Vue opérationnelle : [`roadmap/M4_EXECUTION.md`](roadmap/M4_EXECUTION.md).

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
6. merger seulement après signal explicite
7. mettre à jour ce tableau de bord
```

**Prochaine ligne active : M4-S3 — dérivation déterministe depuis le modèle normalisé.**
