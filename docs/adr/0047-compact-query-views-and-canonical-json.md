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

Les contrats de query existent désormais, mais ils exposent encore directement les objets applicatifs et métier. M5 doit fournir une représentation compacte, stable et directement consommable par scripts/agents sans déplacer vers MORPHEUS les responsabilités de ranking, fusion ou compression globale de NEXUS.

M0 a déjà fixé :

```text
compact context MORPHEUS = ADOPTÉ
global ranking = NEXUS
multi-engine fusion = NEXUS
token-budget compression = NEXUS
```

Le projet ne dépend actuellement d'aucune bibliothèque JSON tierce.

## Décision candidate

Introduire une couche d'exposition applicative dédiée :

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

Les DTO sont typés et indépendants des adapters CLI/MCP/API futurs.

S5 stabilise trois vues représentatives couvrant les primitives les plus riches de M5 :

```text
find_requirements
trace_requirement
get_change_context
```

Les getters/listes S3 restent sources de vérité et pourront réutiliser les mêmes types compacts dans les adapters futurs sans créer une seconde sémantique.

## Métadonnées de query

Chaque vue contient une métadonnée explicite :

```text
schemaVersion = 1
operation = find_requirements | trace_requirement | get_change_context
```

Aucune information sur le mode d'appel n'est inventée : le résultat conserve le snapshot réellement utilisé et son état `ACTIVE` ou `RETIRED`.

## Snapshot et pagination

La vue snapshot conserve au minimum :

```text
snapshotId
projectId
state
predecessorId?
sourceRevision?
builtAt ISO-8601
```

`find_requirements` conserve également :

```text
query text normalisé
offset
limit
totalMatches
hasMore
```

La pagination ne change pas la sémantique S1.

## DTO métier compacts

Les vues compactes conservent les champs sémantiques nécessaires, les identités stables et la provenance.

### Requirement

```text
id
specificationId
key?
title
statement
provenance
```

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

Les listes sont réordonnées par identité avant exposition même si les sources les fournissent déjà dans un ordre stable.

## Provenance et evidence

La provenance compacte conserve exactement :

```text
providerId
providerVersion?
source
externalId?
sourceRevision?
evidenceId
```

La vue `Evidence` conserve :

```text
id
source
range {startLine,endLine}?
excerptHash?
```

Une réponse compacte ne recopie pas toute la banque d'evidence du snapshot. Elle contient uniquement les `EvidenceId` effectivement référencés par les entités et liens exposés, triés par identité.

Si une evidence référencée ne peut pas être retrouvée dans `SnapshotBusinessContent.evidence`, la donnée métier n'est pas supprimée : un warning structuré `EVIDENCE_NOT_FOUND` est ajouté.

L'absence complète de projection `SnapshotBusinessContent` pour un snapshot publié reste une erreur de store, conformément à S3/S4 ; S5 ne la transforme pas en warning.

## Trace compacte

Les sous-graphes conservent :

```text
nodes: kind + identity
links: id + source + relation + target + origin + resolution + evidenceIds
```

Ils restent bornés par les résultats S4 ; S5 ne retraverse pas le graphe et n'ajoute aucune arête.

Les références externes conservent :

```text
linkId
availability
referenceId?
system?
project?
resourceType?
externalId?
revision?
```

Une référence cassée reste représentée avec `availability = BROKEN_REFERENCE` et sans faux objet `ExternalReference`.

## Warnings structurés

S5 introduit un catalogue applicatif dédié plutôt que d'étendre le `DiagnosticCode` de domaine orienté discovery/ingestion.

Format :

```text
code
severity
message
details
```

Les consommateurs automatiques doivent utiliser `code`, `severity` et `details`, jamais parser le message humain.

Codes candidats :

```text
CHANGE_NOT_FOUND
AFFECTED_REQUIREMENT_UNRESOLVED
EXTERNAL_REFERENCE_UNVALIDATED
EXTERNAL_REFERENCE_UNRESOLVED
EXTERNAL_REFERENCE_STALE
EXTERNAL_REFERENCE_BROKEN
EVIDENCE_NOT_FOUND
```

Tous sont de sévérité `WARNING` dans S5.

Ils sont dérivés uniquement de faits observables :

```text
ChangeContextResult.change empty
AFFECTS target sans Requirement CURRENT résolu
ExternalTraceabilityAvailability
EvidenceId référencée absente du snapshot content
```

Aucun warning n'est produit par heuristique textuelle.

Les warnings sont dédupliqués et triés de manière stable par `code` puis détails canoniques.

## Sérialisation JSON

Introduire `CanonicalJsonSerializer` sans dépendance JSON tierce pour le périmètre compact M5.

Le sérialiseur prend uniquement en charge le sous-ensemble nécessaire aux DTO S5 :

```text
record
String / char
boolean
number
enum
Optional
List / Collection
Map<String, ?>
null
```

Contrat canonique :

```text
record fields = ordre de déclaration
map keys = ordre lexicographique
collections = ordre du DTO
Optional.empty = null
enum = name()
aucun pretty-print / aucun espace non nécessaire
échappement JSON strict des chaînes
même DTO -> même String JSON
même String JSON -> mêmes octets UTF-8
```

Les DTO convertissent les identités, `SourceLocator` et `Instant` en chaînes explicites avant sérialisation ; le sérialiseur n'interprète pas les objets métier.

Le JSON est une **vue d'exposition**. Il n'est jamais stocké comme payload métier et n'introduit aucune migration SQLite.

## Frontières

S5 ne fait pas :

```text
nouvelle persistance
nouvelle migration SQLite
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

Le gate S5 doit démontrer :

1. projection compacte déterministe de `find_requirements` ;
2. métadonnées de pagination conservées ;
3. projection compacte de `trace_requirement` ;
4. projection compacte de `get_change_context` ;
5. provenance conservée sur les entités exposées ;
6. evidence référencée incluse une seule fois et triée ;
7. evidence manquante visible par warning sans masquer l'entité ;
8. change absent visible par warning ;
9. AFFECTS non résolu visible par warning ;
10. external unvalidated/unresolved/stale/broken traduit en warning stable ;
11. aucune warning pour external resolved ;
12. ordre stable des entités, liens et warnings ;
13. JSON byte-identical sur répétitions ;
14. échappement JSON correct ;
15. map details triée canoniquement ;
16. aucune nouvelle dépendance JSON ;
17. aucune migration S5 ;
18. `.\mvnw.cmd clean test` vert.

## Preuve d'acceptation

À compléter uniquement après gate local complet vert.
