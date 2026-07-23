# Validation M5 — Requêtes déterministes et contexte compact

Statut : **CANDIDAT À VALIDATION — 5/6 intégrés ; S6 gate final en attente**

Date : 23 juillet 2026

## Question de sortie

> **MORPHEUS peut-il exposer des requêtes métier déterministes, snapshot-cohérentes et bornées, puis produire un contexte compact avec provenance et warnings sans dépendre d'un moteur sémantique, d'un LLM ou de NEXUS ?**

**Réponse candidate : OUI.**

Les slices M5-S1 à M5-S5 démontrent déjà la capacité fonctionnelle. M5-S6 ne rajoute aucune architecture : elle consolide les preuves et exige un dernier gate Maven complet avant de déclarer M5 validée et intégrée.

---

# 1. Progression M5

| Slice | Contenu | PR | ADR | Gate | État |
|---|---|---|---|---|---|
| S1 | recherche lexicale `find_requirements` + pagination | #37 | ADR-0043 | 196/196 | MERGED |
| S2 | projection métier snapshot-scoped | #38 | ADR-0044 | 202/202 | MERGED |
| S3 | getters/listes déterministes | #39 | ADR-0045 | 210/210 | MERGED |
| S4 | `trace_requirement` query view + `get_change_context` | #40 | ADR-0046 | 217/217 | MERGED |
| S5 | vues compactes + warnings/provenance + JSON canonique | #41 | ADR-0047 | 227/227 | MERGED |
| S6 | validation finale | #42 | — | **227/227 attendu** | GATE EN ATTENTE |

Merges intégrés :

```text
M5-S1 merge = 92b1321a0e23553641ea5dbe1f1c25c0acc874e3
M5-S2 merge = 3a39371518d9d327ea4cbee0994da65b218ec64c
M5-S3 merge = 28c32ea2ede7b9144eb10a2a7fb60b0df44f2a73
M5-S4 merge = a1be0820f16c077a33047eefb1e0deac0d5ab680
M5-S5 merge = 330c7831dfe5261247fef98eef850d82c8f0e7c9
```

---

# 2. Requête Requirement déterministe

Contrats :

```text
PageRequest
RequirementSearchQuery
RequirementSearchPage
RequirementQueryService
```

Primitives :

```text
findActive(...)
findSnapshot(...)
```

Sémantique validée :

```text
ACTIVE par défaut
ACTIVE / RETIRED uniquement pour snapshot explicite
CURRENT only
PROPOSED jamais exposé comme CURRENT
recherche lexicale key/title/statement
normalisation Unicode-aware + Locale.ROOT
AND entre termes
aucun fuzzy matching
ordre stable par RequirementId
pagination après filtrage + tri
offset >= 0
1 <= limit <= 100
Memory == SQLite
SQLite reopen
```

S5 renforce le contrat : `RequirementSearchPage` conserve la `RequirementSearchQuery` normalisée qui a réellement produit la page.

Preuves principales :

```text
RequirementQueryContractTest               7/7 PASS
RequirementQueryMetadataRetentionTest      1/1 PASS
```

---

# 3. Projection métier requêtable

Contrats :

```text
SnapshotBusinessContent
SnapshotBusinessContentStore
MemorySnapshotBusinessContentStore
SqliteSnapshotBusinessContentStore
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

`Requirement` reste dans `VersionedRequirementStore` et conserve son modèle versionné spécialisé.

Invariants :

```text
KnowledgeSnapshotId ownership explicite
SpecificationVersionId binding explicite
DomainIdentity stable
aucun faux TemporalState
aucun faux EntityVersionId
projection immuable par snapshot
idempotence exacte
collision explicite
provenance -> evidence obligatoire
Change -> Constraint/Decision/Task validé
Scenario.requirementId conservé
Memory == SQLite
SQLite close/reopen
```

SQLite V007 est normalisée, sans payload JSON métier générique. Les collections ordonnées utilisent des tables enfants avec `ordinal`.

Preuve principale :

```text
SnapshotBusinessContentPersistenceTest      6/6 PASS
```

---

# 4. Getters et listes métier

Contrats :

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

Sémantique :

```text
ACTIVE par défaut
ACTIVE / RETIRED explicite uniquement
absence d'ACTIVE != entity not found
not-found explicite
snapshot publié sans projection = erreur
ordre stable par DomainIdentity
pagination bornée après filtrage/tri
Memory == SQLite
SQLite reopen
aucune nouvelle persistance
aucune V008
```

`get_current_specification` est adressé par `SpecificationId`, car un projet peut porter plusieurs spécifications.

MORPHEUS n'invente aucun `AcceptanceCriterion` :

```text
Scenario != AcceptanceCriterion
```

Aucun type `AcceptanceCriterion` n'est exposé tant qu'une source ne fournit pas cette sémantique explicitement.

Preuves principales :

```text
BusinessContentQueryContractTest            7/7 PASS
BusinessContentQueryBackendParityTest       1/1 PASS
```

---

# 5. `trace_requirement`

M5 n'introduit pas une seconde implémentation de trace. `TraceRequirementQueryService` délègue à la capacité M4 déjà validée.

Résultat :

```text
TraceRequirementResult
  snapshot
  CURRENT requirement
  bounded TraceabilitySubgraph
  external traceability views
```

Règles héritées et conservées :

```text
ACTIVE par défaut
ACTIVE / RETIRED explicite
CURRENT requirement obligatoire
BIDIRECTIONAL
maxDepth > 0
cycle-safe
ordre déterministe
relation filters explicites
unresolved/broken external refs visibles
Memory == SQLite
SQLite reopen
```

Preuves M4/M5 :

```text
TraceRequirementFinalValidationTest         5/5 PASS
ChangeContextQueryContractTest              7/7 PASS
```

---

# 6. `get_change_context`

Contrats :

```text
ChangeContextQueryService
ChangeContextResult
```

Agrégation strictement snapshot-scoped :

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

Les requirements affectés sont dérivés exclusivement des liens `Change --AFFECTS--> Requirement` déjà publiés. Aucune inférence n'est faite depuis le titre, le texte ou le chemin d'une source.

Les `RequirementDelta` bruts ne sont pas présentés comme requêtables : ils n'existent pas comme collection persistée dédiée.

Une cible `AFFECTS` cassée reste visible dans les liens bruts même si aucune occurrence CURRENT ne peut être résolue.

Invariants :

```text
ACTIVE par défaut
ACTIVE / RETIRED explicite
CURRENT only
PROPOSED jamais exposé
AFFECTS directs uniquement
références cassées conservées
traversal borné et cycle-safe
Memory == SQLite
SQLite reopen
aucune persistance supplémentaire
aucune V008
```

Preuve principale :

```text
ChangeContextQueryContractTest              7/7 PASS
```

---

# 7. Vues compactes

Contrats :

```text
CompactQueryTypes
CompactRequirementSearchView
CompactTraceRequirementView
CompactChangeContextView
CompactQueryViewService
CompactWarningCode
```

Les vues couvrent :

```text
find_requirements
trace_requirement
get_change_context
```

Métadonnées conservées :

```text
schemaVersion = 1
operation
snapshotId
projectId
snapshot state
predecessorId?
sourceRevision?
createdAt
pagination metadata
```

L'identité/version reste visible :

```text
RequirementId
EntityVersionId
SpecificationVersionId
TemporalState
```

Donc :

```text
DomainIdentity != EntityVersionId
```

reste explicitement observable dans la surface compacte.

Les objets métier ne sont jamais sérialisés directement ; ils sont projetés vers des DTO applicatifs dédiés.

Preuve principale :

```text
CompactQueryViewContractTest                6/6 PASS
```

---

# 8. Provenance et evidence

La provenance compacte conserve :

```text
providerId
providerVersion?
source
externalId?
sourceRevision?
evidenceId
```

Les evidence exposées sont uniquement celles effectivement référencées par la réponse, triées et dédupliquées par `EvidenceId`.

Une evidence référencée mais absente ne masque pas l'entité ; elle produit un warning `EVIDENCE_NOT_FOUND`.

Invariants :

```text
provenance jamais supprimée pour compacter
evidence absente != suppression de la donnée métier
même projection Memory/SQLite
SQLite reopen conserve les faits nécessaires
```

---

# 9. Warnings structurés

Catalogue M5 :

```text
CHANGE_NOT_FOUND
AFFECTED_REQUIREMENT_UNRESOLVED
EXTERNAL_REFERENCE_UNVALIDATED
EXTERNAL_REFERENCE_UNRESOLVED
EXTERNAL_REFERENCE_STALE
EXTERNAL_REFERENCE_BROKEN
EVIDENCE_NOT_FOUND
```

Format :

```text
code
severity = WARNING
message
details
```

Les warnings sont dérivés exclusivement de faits observables :

```text
change absent
AFFECTS sans Requirement CURRENT
état de résolution d'une ExternalReference
evidence référencée absente
```

Ils ne reposent sur aucun score, embedding, fuzzy matching ou LLM.

Une external reference `RESOLVED` n'émet aucun warning.

---

# 10. JSON canonique déterministe

Contrat :

```text
CanonicalJsonSerializer
```

Sous-ensemble supporté :

```text
record
String / char
boolean
nombre fini
enum
Optional
Collection / array
Map<String, ?>
null
```

Règles canoniques :

```text
record fields = ordre de déclaration
map keys = ordre lexicographique
map null values supportées
collections = ordre déjà canonisé
Optional.empty = null
enum = name()
aucun pretty-print
échappement strict quote/backslash/control/surrogate
même DTO -> même String JSON
même String -> mêmes octets UTF-8
```

Rejets explicites :

```text
type non supporté
clé Map non String
NaN
Infinity
```

Aucune dépendance Jackson/Gson/autre bibliothèque JSON n'est introduite.

Le JSON est une **vue d'exposition** seulement ; il n'est jamais utilisé comme payload métier générique de persistance.

Preuve principale :

```text
CanonicalJsonSerializerTest                 3/3 PASS
```

---

# 11. Parité backend et historique

La preuve M5 couvre les deux backends de référence :

```text
Memory
SQLite
```

Les contrats démontrent :

```text
mêmes résultats de recherche Requirement
mêmes pages métier
mêmes vues compactes
mêmes relations et contexte
même reconstruction après SQLite reopen
ACTIVE et RETIRED explicitement distingués
READY / états techniques non publiés rejetés
```

MORPHEUS ne dépend d'aucun backend graphe, moteur de recherche sémantique ou service distant pour ces capacités.

---

# 12. Frontière MORPHEUS / NEXUS

M5 fournit :

```text
facts métier
queries déterministes
pagination bornée
trace/context snapshot-scoped
provenance/evidence
warnings structurés
DTO compacts
JSON canonique
```

M5 ne fournit pas :

```text
ranking global
fusion multi-engine
compression selon budget de tokens
sélection globale de contexte inter-engines
semantic search / embeddings
```

Ces responsabilités restent NEXUS.

MORPHEUS reste utilisable sans NEXUS.

---

# 13. Dépendances exclues

La preuve S1-S5 confirme l'absence de dépendance obligatoire à :

```text
LLM
semantic search
embeddings
NEXUS
MINOS
backend graphe
bibliothèque JSON tierce
```

Les références MINOS éventuelles restent des `ExternalReference` optionnelles et ne conditionnent pas la disponibilité de MORPHEUS.

---

# 14. Gate final M5 — EN ATTENTE

Commande obligatoire :

```text
Windows : .\mvnw.cmd clean test
```

Baseline S5 intégrée :

```text
Domain                                  21 tests
Application                             66 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                     100 tests
-----------------------------------------------
TOTAL                                  227/227 PASS
```

M5-S6 n'ajoute volontairement aucun test de comportement : elle ne modifie aucun contrat de production. Le gate final attendu reste donc :

```text
TOTAL       227/227 PASS
Failures      0
Errors        0
Skipped       0
BUILD SUCCESS
```

La date, la durée, le head testé et la preuve finale seront inscrits ici uniquement après exécution locale réussie du gate S6.

Warnings connus et non bloquants :

```text
Xerial SQLite / JDK native-access
SLF4J NOP dans les tests d'architecture
```

---

# 15. Décision de sortie — BLOQUÉE PAR LE GATE S6

Toutes les preuves fonctionnelles S1-S5 soutiennent déjà la réponse :

```text
Question de sortie M5 = OUI
```

La clôture formelle reste toutefois bloquée tant que le dernier gate local S6 n'est pas vert.

Après un `227/227 PASS`, la décision finale devra devenir :

```text
M5 = VALIDÉ ET INTÉGRÉ
6/6 slices = VALIDÉS ET INTÉGRÉS
227/227 = PASS
M6 = AUTORISÉ
```
