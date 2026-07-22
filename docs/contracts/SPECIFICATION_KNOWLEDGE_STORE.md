# Contrat conceptuel — `SpecificationKnowledgeStore`

Statut : **Proposition C0 — à valider en M0**

Date : 22 juillet 2026

Ce document définit les capacités que MORPHEUS attend d'un backend de connaissance sans choisir prématurément une technologie.

> Le contrat du store est dérivé des cas d'usage MORPHEUS. Il ne doit pas être une copie d'une API SQL, graphe, documentaire ou vectorielle.

---

## 1. Responsabilité

`SpecificationKnowledgeStore` persiste et interroge le modèle normalisé MORPHEUS.

Il doit pouvoir stocker au minimum :

- projets de spécification ;
- spécifications ;
- exigences ;
- contraintes ;
- scénarios ;
- changements ;
- décisions ;
- critères d'acceptation ;
- tâches ;
- versions ;
- preuves ;
- références externes ;
- liens de traçabilité.

---

## 2. Non-responsabilités

Le store ne doit pas :

- parser OpenSpec ;
- comprendre un format externe ;
- décider quel provider utiliser ;
- orchestrer JARVIS ;
- sélectionner le contexte global pour NEXUS ;
- parser le code à la place de MINOS ;
- inventer silencieusement des relations métier.

---

## 3. Familles d'opérations

Le contrat logique est organisé autour de quatre catégories :

```text
WRITE
READ
SEARCH
TRAVERSE
```

auxquelles s'ajoutent :

```text
SNAPSHOT / VERSION
MAINTENANCE
DIAGNOSTICS
```

---

## 4. Écriture atomique d'un snapshot

Opération conceptuelle :

```text
storeSnapshot(snapshot) -> StoreSnapshotResult
```

Un snapshot contient un état normalisé cohérent pour une source et une révision données.

### Propriétés attendues

- atomicité logique : un consommateur ne doit pas observer un demi-snapshot comme état courant ;
- idempotence lorsque le même snapshot est rejoué ;
- détection de collision d'identité ;
- conservation de la provenance ;
- possibilité d'invalider ou historiser les éléments disparus.

Une implémentation peut utiliser une transaction, un double-buffer, un numéro de génération ou une autre stratégie interne.

---

## 5. Écriture incrémentale

Opérations candidates :

```text
applyDelta(delta)
upsertEntities(entities)
removeOrRetireEntities(ids)
upsertTraceabilityLinks(links)
```

L'API exacte doit être validée après le spike incrémental.

### Invariant

Une suppression dans la source ne signifie pas nécessairement suppression physique immédiate dans le store. L'historique et les liens explicatifs peuvent nécessiter un état retiré ou historique.

---

## 6. Lecture par identité

Opérations candidates :

```text
getProjectSpecification(id)
getSpecification(id)
getRequirement(id)
getChange(id)
getConstraint(id)
getScenario(id)
getDecision(id)
getAcceptanceCriterion(id)
getTask(id)
```

Le résultat doit permettre de distinguer :

```text
FOUND
NOT_FOUND
RETIRED
AMBIGUOUS
```

lorsque cette distinction est pertinente.

---

## 7. Lecture par clé métier

Exemples :

```text
findSpecification(projectId, key)
findChange(projectId, key)
findRequirement(projectId, key)
```

Les collisions doivent être représentables au lieu d'être résolues arbitrairement.

---

## 8. Recherche

Opération conceptuelle :

```text
search(query) -> SearchResultPage
```

Filtres nécessaires au MVP :

```text
project
entityType
text
key
temporalState
lifecycleState
specification
change
provider
version
limit
cursor
```

Le MVP peut commencer par une recherche lexicale. La recherche sémantique n'est pas une exigence du cœur.

---

## 9. Requêtes de portée

Exemples :

```text
findRequirements(scope)
findConstraints(scope, includeInherited=true)
findAcceptanceCriteria(scope)
findDesignDecisions(scope)
findImplementationTasks(changeId)
```

Le store peut retourner les liens nécessaires pour que la couche d'intelligence applique les règles métier de portée. Il ne doit pas cacher ces règles dans des requêtes backend opaques sans tests de contrat.

---

## 10. Traçabilité

Opérations candidates :

```text
findOutgoingLinks(entity, relationTypes?)
findIncomingLinks(entity, relationTypes?)
traverse(start, direction, relationTypes, maxDepth)
findPath(source, target, constraints)
```

Chaque arête retournée doit conserver :

- type ;
- résolution ;
- origine ;
- confiance éventuelle ;
- preuves ;
- fenêtre de validité éventuelle.

---

## 11. Versionnement et snapshots

Opérations candidates :

```text
getCurrentVersion(projectId)
getVersion(projectId, versionId)
listVersions(projectId)
compareVersions(left, right)
activateVersion(versionId)
```

`activateVersion` est conceptuel : l'implémentation peut utiliser une génération courante plutôt qu'une mutation de toutes les entités.

### Règles

- l'état courant est explicitement identifiable ;
- une version historique reste requêtable selon la politique de rétention ;
- une comparaison doit pouvoir distinguer ajout, modification, suppression et mouvement logique lorsque connu.

---

## 12. État courant vs proposé

Le store doit permettre de requêter séparément :

```text
CURRENT
PROPOSED
HISTORICAL
```

Une requête `current` ne doit jamais inclure implicitement un artefact `PROPOSED`.

Les deux états peuvent partager une même identité logique dans des versions différentes selon la stratégie retenue.

---

## 13. Preuves et provenance

Opérations candidates :

```text
getEvidence(entityOrLink)
getProvenance(entityOrLink)
findBySource(sourceReference)
findByExternalReference(externalReference)
```

MORPHEUS doit pouvoir répondre à :

> Quelle source a produit ce résultat ?

et :

> Quels éléments seront invalidés si cette source change ?

---

## 14. Références externes

Opérations candidates :

```text
findExternalReferences(entity)
findEntitiesByExternalReference(system, resourceType, externalId)
```

Le store ne résout pas nécessairement la référence dans le système externe. Il stocke le lien et son état de résolution connu.

---

## 15. Diagnostics d'intégrité

Le backend doit permettre de détecter ou aider à détecter :

- références internes cassées ;
- identités dupliquées ;
- arêtes orphelines ;
- versions incohérentes ;
- snapshot partiel ;
- source importée deux fois sous deux identités incompatibles.

Les diagnostics métier restent produits par la couche MORPHEUS lorsque la règle dépasse l'intégrité structurelle du stockage.

---

## 16. Transactions et cohérence

Le contrat n'impose pas ACID complet à toute technologie, mais exige les invariants observables suivants :

1. pas d'état courant partiellement remplacé ;
2. pas de lien visible vers une entité non publiée dans le même snapshot, sauf référence explicitement `UNRESOLVED` ;
3. idempotence du rejeu d'un snapshot identique ;
4. cohérence entre version active et entités courantes ;
5. échec explicite lorsqu'une écriture ne respecte pas les invariants.

---

## 17. Performance à mesurer

M0 doit mesurer au minimum :

```text
ingestion full
upsert incremental
find by id
find by key
text search
trace depth 1
trace depth 3
path search
current snapshot activation
storage size
memory footprint
startup time
```

Les tailles de jeux de données devront couvrir plusieurs ordres de grandeur.

---

## 18. Backend mémoire

Un backend mémoire est obligatoire pour :

- tests unitaires ;
- tests de contrat ;
- validation du découplage ;
- prototypage rapide.

Il doit implémenter le même contrat logique que les backends persistants pour les capacités déclarées.

Il n'est pas nécessaire qu'il fournisse les mêmes performances ni les mêmes optimisations.

---

## 19. Backend persistant candidat

M0 doit comparer au moins un backend persistant simple au backend mémoire.

Familles possibles :

- SQLite / relationnel embarqué ;
- documentaire embarqué ;
- graphe ;
- combinaison légère.

Aucune de ces familles n'est retenue par cette spécification.

---

## 20. Graphe

La traçabilité rend les opérations de graphe naturelles, mais cela ne suffit pas à imposer une graph database.

M0 doit mesurer :

- complexité de mapping ;
- requêtes de profondeur ;
- recherche de chemin ;
- coût opérationnel ;
- portabilité ;
- distribution locale ;
- sauvegarde / reconstruction.

Une architecture hybride ne sera retenue que si elle apporte un bénéfice mesurable supérieur à sa complexité.

---

## 21. Tests de contrat

Chaque backend devra passer une suite commune couvrant notamment :

```text
snapshot idempotency
current/proposed isolation
identity uniqueness
version activation
traceability traversal
unresolved references
historical retention
provenance preservation
collision reporting
```

Le test de contrat est la preuve principale que le domaine n'est pas couplé à une technologie de stockage.

---

## 22. Critères d'acceptation M0

Le contrat sera considéré viable si :

1. le backend mémoire permet d'implémenter tous les cas d'usage MVP nécessaires ;
2. un backend persistant implémente le même contrat sans fuite technologique dans le domaine ;
3. les traversées de traçabilité restent acceptables sur les datasets de référence ;
4. l'activation d'un nouveau snapshot ne produit pas d'état partiel visible ;
5. l'historique minimal peut être conservé ;
6. la taille de stockage et le coût de démarrage sont compatibles avec un outil local-first ;
7. la reconstruction complète reste possible depuis les sources.

---

## 23. Questions ouvertes

- API exacte d'écriture ;
- stratégie de pagination ;
- stratégie de cache ;
- granularité de transaction ;
- rétention historique ;
- gestion de plusieurs workspaces ;
- séparation métadonnées / contenu ;
- chiffrement local éventuel ;
- index textuel ;
- backend persistant initial.