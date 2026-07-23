# ADR-0047 — Vues de requête compactes et JSON canonique déterministe

- Statut : **Acceptée — M5**
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

Les queries S1-S4 exposent des objets applicatifs/métier riches. M5 doit fournir une représentation compacte stable, directement consommable par scripts et agents, sans déplacer vers MORPHEUS les responsabilités NEXUS de ranking, fusion multi-engine ou compression par budget de tokens.

Le projet ne dépend d'aucune bibliothèque JSON tierce.

## Décision

Introduire la couche applicative :

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

S5 stabilise trois vues :

```text
find_requirements
trace_requirement
get_change_context
```

## Rétention de la query `find_requirements`

`RequirementSearchPage` conserve désormais la `RequirementSearchQuery` normalisée ayant réellement produit la page.

```text
snapshot
query
items
pageRequest
totalMatches
hasMore
```

`RequirementQueryService` propage toujours cette query. Un constructeur de compatibilité cinq arguments reste disponible et utilise `RequirementSearchQuery.all()` pour les callers historiques qui construisent eux-mêmes une page.

La vue compacte reçoit uniquement `RequirementSearchPage`; elle ne peut donc pas être associée accidentellement à un texte de recherche différent.

## Métadonnées

Chaque vue contient :

```text
schemaVersion = 1
operation
snapshotId
projectId
state
predecessorId?
sourceRevision?
createdAt ISO-8601
```

`find_requirements` conserve également :

```text
searchText normalisé
offset
limit
totalMatches
hasMore
```

## Identité et temporalité

Le DTO `RequirementView` conserve explicitement :

```text
id                       = RequirementId / DomainIdentity stable
entityVersionId          = occurrence versionnée
specificationVersionId   = version de spécification
temporalState            = CURRENT / PROPOSED / HISTORICAL
specificationId
key?
title
statement
provenance
```

La compacité ne masque donc pas :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
```

Les queries validées S1/S4 n'exposent que les occurrences `CURRENT`, mais le champ temporel reste explicite.

## DTO métier

Les vues compactes de `ChangeProposal`, `Constraint`, `DesignDecision` et `ImplementationTask` conservent leurs identités, relations métier, contenu utile et provenance. Les listes sont ordonnées de manière stable par identité.

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

L'absence complète de projection `SnapshotBusinessContent` pour un snapshot publié reste une `KnowledgeStoreException`.

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

S5 introduit un catalogue applicatif dédié. Le format est :

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

Les warnings sont dérivés uniquement de faits observables : change absent, cible `AFFECTS` sans occurrence `CURRENT`, `ExternalTraceabilityAvailability`, evidence référencée absente. Aucune heuristique textuelle.

Les warnings sont dédupliqués et ordonnés de façon stable. Une external reference `RESOLVED` n'émet aucun warning.

## JSON canonique

`CanonicalJsonSerializer` n'introduit aucune dépendance tierce et accepte uniquement le sous-ensemble nécessaire aux DTO compacts :

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
collections = ordre canonique du DTO
Optional.empty = null
enum = name()
aucun pretty-print
échappement strict quote/backslash/control/surrogate
même DTO -> même String JSON
même String -> mêmes octets UTF-8
```

Un type non supporté, une clé de map non `String`, `NaN` ou `Infinity` est rejeté explicitement.

Les identités, `SourceLocator` et `Instant` sont convertis en chaînes dans la projection ; le sérialiseur n'interprète jamais un objet métier.

Le JSON est exclusivement une vue d'exposition. Aucune payload JSON métier n'est persistée.

## Backends

La projection compacte dépend uniquement de `SnapshotBusinessContentStore` pour résoudre les evidence référencées. Le même résultat compact doit être obtenu avec Memory et SQLite, y compris après reopen SQLite.

Les résultats riches S1-S4 conservent leurs preuves backend-neutral déjà validées.

## Frontières

S5 n'introduit pas :

```text
pom.xml change
bibliothèque JSON tierce
nouvelle table / migration SQLite
nouvelle persistance métier
JSON métier dans SQLite
store adapter change
semantic search / embeddings
ranking global
fusion multi-engine
compression par budget de tokens
LLM / NEXUS
MCP / API / CLI publics
```

## Preuves S5

Tests ajoutés :

```text
CompactQueryViewContractTest           6 tests
CanonicalJsonSerializerTest            3 tests
RequirementQueryMetadataRetentionTest  1 test
-----------------------------------------------
TOTAL S5                              10 tests
```

Le gate couvre notamment : query retenue par la page, snapshot/pagination, Memory == SQLite, SQLite reopen, identité/version/temporalité explicites, trace compacte, change context compact, provenance/evidence, evidence manquante, warnings factuels, external resolved sans warning, ordre stable, JSON byte-identical, tri des maps, valeurs null, escaping strict et rejets explicites.

## Preuve d'acceptation — 23 juillet 2026

Gate local Windows exécuté sur :

```text
branch = m5/compact-query-views
head   = 77df15e4ea5aaa93722b25d0f18f7c38214b0d9e
.\mvnw.cmd clean test
javac release 21
```

Résultat :

```text
Domain                                  21 tests
Application                             66 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                     100 tests
-----------------------------------------------
TOTAL                                  227/227 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
Total time                             19.928 s
Finished at                 2026-07-23T20:06:21+02:00
```

Le gate confirme en particulier `RequirementQueryMetadataRetentionTest 1/1 PASS` et l'ensemble Architecture **100/100 PASS**. Les warnings Xerial SQLite/JDK native-access et SLF4J NOP restent les warnings connus, non bloquants, lorsqu'ils apparaissent.

Décision finale :

```text
ADR-0047 = ACCEPTÉE — M5
M5-S5    = VALIDÉ — 227/227
```
