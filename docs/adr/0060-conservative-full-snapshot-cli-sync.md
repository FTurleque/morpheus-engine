# ADR-0060 — Sync CLI conservateur par publication complète de snapshot

- Statut : **Acceptée — M9, récupération durcie le 19 août 2026**
- Date : 24 juillet 2026
- Dépend de : ADR-0033, ADR-0034, ADR-0039, ADR-0053, ADR-0054, ADR-0055
- Portée : M9 — ingestion exécutable depuis la CLI

## Précision de politique — 19 août 2026

Cette ADR constitue la politique explicite annoncée par ADR-0031 pour le chemin officiel de full rebuild. `ProjectSnapshotImportService.publishFull()` alloue une nouvelle `SpecificationVersion` à **chaque tentative durable de publication complète qui atteint la persistance de version**, même si une autre orchestration pourrait techniquement réutiliser une version existante.

Les invariants de récupération sont désormais :

```text
BUILDING candidate est la première ancre durable
sequence(project) est unique pour toute SpecificationVersion non nulle
next sequence = max(sequence de toutes les tentatives durables) + 1
FAILED candidate ayant une SpecificationVersion conserve et consomme sa sequence
retry ne réutilise jamais la sequence du FAILED
predecessor du retry = version du snapshot précédemment ACTIVE
```

Les artefacts d'une candidate FAILED restent conservés comme preuve de tentative et ne deviennent jamais une baseline publiée. La migration SQLite V016 répare d'éventuels doublons historiques de séquence avant d'imposer l'index unique `(project_id, sequence)`.

## Contexte

M7 sait scanner les sources, produire un diff, choisir `INCREMENTAL` ou `FULL_REBUILD` et persister la fraîcheur. Il ne fournit toutefois pas encore un exécuteur métier incrémental complet capable de reconstruire uniquement un sous-ensemble du graphe publié.

M9 doit rendre `morpheus sync` utilisable sans prétendre exécuter une stratégie plus fine que celle réellement matérialisée.

## Décision

Introduire :

```text
ProjectSnapshotImportService
ProjectSnapshotImportResult
```

Le service reçoit un `NormalizedProjectContent` complet et publie un nouveau snapshot par full rebuild conservateur.

Séquence durable :

```text
register project
resolve predecessor ACTIVE/version
register BUILDING KnowledgeSnapshot
allocate unique durable SpecificationVersion sequence
create/persist SpecificationVersion
bind snapshot/version
persist CURRENT RequirementVersionRecord
persist SnapshotBusinessContent
derive/persist deterministic traceability
validate normalized diagnostics
READY -> ACTIVE atomically
```

L'ancien snapshot reste ACTIVE jusqu'à la dernière étape. L'ancre BUILDING précède obligatoirement la création durable de la version : une panne d'allocation ou de persistance ne peut donc pas laisser une `SpecificationVersion` orpheline sans candidate observable.

## Diagnostics

```text
ERROR   -> candidate FAILED, ancien ACTIVE conservé
WARNING -> candidate READY puis ACTIVE
none    -> candidate READY puis ACTIVE
```

Un échec ne transforme jamais une ingestion partielle en nouvelle baseline publiée. Une candidate FAILED reste durablement observable pour l'audit ; lorsqu'une `SpecificationVersion` a déjà été persistée, sa séquence ne peut pas être réutilisée par une tentative ultérieure.

## Sync M7

Le launcher officiel M9 force l'exécution CLI de `sync` en `FULL_REBUILD` afin que :

```text
SyncPlan.mode == exécution réelle
lastSuccessfulMode == exécution réelle
```

Le moteur M7 conserve son planificateur incrémental inchangé. M9 n'expose simplement pas un mode incrémental tant qu'un exécuteur incrémental du graphe métier n'est pas disponible.

Invariant :

```text
reliability > incremental speed
no fake INCREMENTAL receipt
```

## Identités

Le reader OpenSpec utilise `PersistentEntityIdentityResolver` branché sur SQLite. Les identités provider-scoped restent donc stables entre deux invocations CLI et entre deux snapshots.

Les `TraceabilityLinkId` des observations dérivées sont des identités d'occurrence nouvelles explicitement générées lors de chaque publication. Aucun hash sémantique n'est utilisé.

## Frontières

`ProjectSnapshotImportService` appartient à `morpheus-application` parce que la cohérence de publication est une orchestration applicative. La CLI sélectionne la source/provider et rend le reçu, mais ne définit pas les transitions de snapshot.

Ce slice ne promet pas : application partielle des deltas M7, watcher CLI longue durée, merge de sous-graphes ou mutation in-place d'un snapshot.

## Preuves finales — 24 juillet 2026

Windows et Linux/WSL ont chacun prouvé :

```text
ProjectSnapshotImportContractTest  3/3 PASS
MorpheusCliTest                     4/4 PASS
MorpheusMainTest                    2/2 PASS
Architecture Tests                149/149 PASS
TOTAL                             298/298 PASS
BUILD SUCCESS
```

Le test CLI end-to-end couvre OpenSpec -> sync -> SQLite -> reopen et vérifie que le launcher officiel enregistre `FULL_REBUILD`, cohérent avec l'exécution réelle.

Le gate Linux a été exécuté avec OpenJDK 21.0.11 sur filesystem Linux WSL et confirme la même sémantique que le gate Windows.

Validation complète : [`../VALIDATION_M9.md`](../VALIDATION_M9.md).

## Critères d'acceptation

Les tests prouvent :

1. publication complète OpenSpec ;
2. requirements CURRENT dans le nouveau snapshot ;
3. projection métier persistée ;
4. traçabilité dérivée ;
5. activation finale ;
6. predecessor/version sequence ;
7. ancien ACTIVE devient RETIRED après succès ;
8. diagnostic ERROR laisse l'ancien ACTIVE ;
9. Memory + SQLite ;
10. SQLite reopen ;
11. CLI sync enregistre FULL_REBUILD et non un faux INCREMENTAL ;
12. une tentative FAILED ayant persisté une version consomme une sequence unique et un retry alloue la suivante ;
13. BUILDING est persisté avant l'allocation/persistance de la version.

**Décision : ADR-0060 acceptée.**