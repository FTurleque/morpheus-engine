# ADR-0047 — Vues de requête compactes et JSON canonique déterministe

- Statut : **Proposée — M5**
- Date : 23 juillet 2026
- Dépend de : ADR-0004, ADR-0009, ADR-0033, ADR-0034, ADR-0041, ADR-0042, ADR-0043, ADR-0044, ADR-0045, ADR-0046
- Portée : M5-S5, vues compactes, warnings structurés, provenance/evidence et sérialisation JSON déterministe

## Contexte

M5-S1 à S4 sont intégrés :

```text
M5-S1 merge = 92b1321a0e23553641ea5dbe1f1c25c0acc874e3 — 196/196
M5-S2 merge = 3a39371518d9d327ea4cbee0994da65b218ec64c — 202/202
M5-S3 merge = 28c32ea2ede7b9144eb10a2a7fb60b0df44f2a73 — 210/210
M5-S4 merge = a1be0820f16c077a33047eefb1e0deac0d5ab680 — 217/217
```

Les queries existent, mais leurs résultats restent des objets applicatifs/métier riches. M5 doit fournir une représentation compacte stable et directement consommable par scripts/agents sans déplacer vers MORPHEUS le ranking, la fusion ou la compression globale de NEXUS.

M0 a déjà fixé :

```text
compact context MORPHEUS = ADOPTÉ
global ranking = NEXUS
multi-engine fusion = NEXUS
token-budget compression = NEXUS
```

Le projet ne dépend d'aucune bibliothèque JSON tierce.

## Décision candidate

Introduire une couche applicative dédiée :

```text
com.morpheus.application.query.compact
```

avec :

```text
CompactQueryTypes
CompactRequirementSearchView
CompactTraceRequirementView
CompactChangeContextView
CompactQueryViewService
CompactWarningCode
CanonicalJsonSerializer
```

Les DTO sont typés. Aucun objet de domaine ou de store n'est sérialisé directement.

S5 stabilise trois vues couvrant les résultats les plus riches :

```text
find_requirements
trace_requirement
get_change_context
```

Les getters/listes S3 restent sources de vérité et pourront réutiliser ces types dans les adapters futurs.

## Rétention de la query find_requirements

`RequirementSearchPage` ne conservait historiquement que :

```text
snapshot
items
pageRequest
totalMatches
hasMore
```

S5 ajoute :

```text
RequirementSearchQuery query
```

Le `RequirementQueryService` renseigne toujours la query normalisée ayant produit la page.

Un constructeur de compatibilité 5 arguments reste disponible et utilise `RequirementSearchQuery.all()` pour les rares callers qui construisent eux-mêmes une page sans métadonnée de query.

La vue compacte reçoit donc uniquement `RequirementSearchPage`; elle ne peut pas être associée accidentellement à un texte de recherche différent.

## Métadonnées de query

Chaque vue contient :

```text
schemaVersion = 1
operation = find_requirements | trace_requirement | get_change_context
```

Le snapshot réellement utilisé est conservé ; aucun mode d'appel n'est inventé.

## Snapshot et pagination

La vue snapshot conserve :

```text
snapshotId
projectId
state
predecessorId?
sourceRevision?
createdAt ISO-8601
```

`find_requirements` conserve :

```text
searchText normalisé
offset
limit
totalMatches
hasMore
```

La pagination ne modifie pas la sémantique S1.

## DTO Requirement

La compacité ne doit pas effacer la séparation identité/version :

```text
id                       = RequirementId / DomainIdentity stable
entityVersionId          = occurrence versionnée
specificationVersionId   = version de spécification
 temporalState           = CURRENT/PROPOSED/HISTORICAL
specificationId
key?
title
statement
provenance
```

Dans les queries S1/S4 validées, seules les occurrences `CURRENT` sont exposées, mais le champ temporel reste explicite dans le DTO.

## Autres DTO métier

### ChangeProposal

```text
id
projectId
key?
title
intent
scope
outOfScope
risks
provenance
```

### Constraint

```text
id
changeId
statement
provenance
```

### DesignDecision

```text
id
changeId
title
decision
provenance
```

### ImplementationTask

```text
id
changeId
key?
title
completed
provenance
```

Toutes les listes sont réordonnées par identité avant exposition.

## Provenance et evidence

La provenance compacte conserve :

```text
providerId
providerVersion?
source
externalId?
sourceRevision?
evidenceId
```

`EvidenceView` conserve :

```text
id
source
range {startLine,endLine}?
excerptHash?
```

Une réponse compacte inclut uniquement les evidence effectivement référencées par les entités, les liens de trace ou les références externes exposés. Elles sont dédupliquées et triées par `EvidenceId`.

Si une `EvidenceId` référencée n'existe pas dans `SnapshotBusinessContent.evidence`, la donnée métier reste visible et un warning `EVIDENCE_NOT_FOUND` est émis.

L'absence complète de projection `SnapshotBusinessContent` pour un snapshot publié reste une `KnowledgeStoreException`, conformément à S3/S4.

## Trace compacte

Le graphe expose :

```text
nodes: kind + identity
links: id + source + relation + target + origin + resolution + evidenceIds
```

S5 ne retraverse pas le graphe et n'ajoute aucune arête.

Les références externes exposent :

```text
linkId
availability
referenceId
system?
project?
resourceType?
externalId?
revision?
provenance?
```

Une `BROKEN_REFERENCE` conserve l'identité portée par le lien même sans objet `ExternalReference` disponible.

## Warnings structurés

S5 introduit un catalogue applicatif dédié, sans étendre le `DiagnosticCode` de domaine orienté discovery/ingestion.

Format :

```text
code
severity = WARNING
message
details
```

`WarningView` rejette toute autre sévérité.

Codes :

```text
CHANGE_NOT_FOUND
AFFECTED_REQUIREMENT_UNRESOLVED
EXTERNAL_REFERENCE_UNVALIDATED
EXTERNAL_REFERENCE_UNRESOLVED
EXTERNAL_REFERENCE_STALE
EXTERNAL_REFERENCE_BROKEN
EVIDENCE_NOT_FOUND
```

Ils sont dérivés uniquement de faits observables :

```text
ChangeContextResult.change empty
AFFECTS target sans Requirement CURRENT
ExternalTraceabilityAvailability
EvidenceId référencée absente
```

Aucune heuristique textuelle.

Les warnings sont dédupliqués et triés de manière stable par code puis détails canoniques.

## Sérialisation JSON

`CanonicalJsonSerializer` n'introduit aucune dépendance tierce et n'accepte que le sous-ensemble nécessaire aux DTO compacts :

```text
record
String / char
boolean
number fini
enum
Optional
Collection / array
Map<String, ?>
null
```

Contrat :

```text
record fields = ordre de déclaration
map keys = ordre lexicographique
map values peuvent être null
collections = ordre déjà canonisé du DTO
Optional.empty = null
enum = name()
aucun pretty-print
échappement strict quote/backslash/control/surrogate
même DTO -> même String JSON
même String -> mêmes octets UTF-8
```

Un type non supporté, une clé de map non String ou un `NaN/Infinity` est rejeté explicitement.

Les identités, `SourceLocator` et `Instant` sont convertis en chaînes dans la projection ; le sérialiseur n'interprète jamais un objet métier.

Le JSON est uniquement une vue d'exposition. Aucune payload JSON métier n'est persistée.

## Backends

La projection compacte dépend uniquement de `SnapshotBusinessContentStore` pour résoudre les evidence référencées. Le même résultat compact doit être obtenu avec Memory et SQLite, y compris après reopen SQLite.

Les résultats riches S1-S4 conservent leurs preuves backend-neutral déjà validées.

## Frontières

S5 ne fait pas :

```text
nouvelle table / migration SQLite
nouvelle persistance métier
JSON métier dans SQLite
ORM
nouveau backend
semantic search / embeddings
ranking global
fusion multi-engine
compression par budget de tokens
LLM
NEXUS
MCP / API / CLI publics
```

## Preuves attendues

Tests S5 ajoutés :

```text
CompactQueryViewContractTest           6 tests
CanonicalJsonSerializerTest            3 tests
RequirementQueryMetadataRetentionTest  1 test
-----------------------------------------------
TOTAL S5                              10 tests
```

Baseline : `217/217 PASS`.  
Gate attendu : **227/227**.

Le gate doit démontrer :

1. query `find_requirements` retenue par sa page ;
2. query/snapshot/pagination compactes ;
3. Memory == SQLite pour la projection compacte ;
4. SQLite reopen ;
5. `RequirementId != EntityVersionId` visible dans la vue ;
6. `trace_requirement` compact et borné ;
7. `get_change_context` conserve change/requirements/constraints/decisions/tasks ;
8. provenance conservée ;
9. evidence référencée incluse une seule fois et triée ;
10. evidence manquante produit un warning sans masquer l'entité ;
11. change absent produit `CHANGE_NOT_FOUND` ;
12. cible AFFECTS non résolue produit un warning ;
13. external unvalidated/unresolved/stale/broken produit les warnings attendus ;
14. external resolved ne produit aucun warning ;
15. warnings de sévérité `WARNING` uniquement ;
16. ordre stable ;
17. JSON byte-identical sur répétitions/backends ;
18. clés de map triées ;
19. échappement JSON strict ;
20. types invalides / NaN / clés non String rejetés ;
21. aucune dépendance JSON tierce ;
22. aucune migration S5 ;
23. `.\mvnw.cmd clean test` vert.

## Preuve d'acceptation

À compléter uniquement après gate local complet vert.
