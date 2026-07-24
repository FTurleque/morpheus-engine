# ADR-0060 — Sync CLI conservateur par publication complète de snapshot

- Statut : **Proposée — M9, gate pending**
- Date : 24 juillet 2026
- Dépend de : ADR-0033, ADR-0034, ADR-0039, ADR-0053, ADR-0054, ADR-0055
- Portée : M9 — ingestion exécutable depuis la CLI

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

Séquence :

```text
register project
resolve predecessor ACTIVE/version
create SpecificationVersion
create BUILDING KnowledgeSnapshot
bind snapshot/version
persist CURRENT RequirementVersionRecord
persist SnapshotBusinessContent
derive/persist deterministic traceability
validate normalized diagnostics
READY -> ACTIVE atomically
```

L'ancien snapshot reste ACTIVE jusqu'à la dernière étape.

## Diagnostics

```text
ERROR   -> candidate FAILED, ancien ACTIVE conservé
WARNING -> candidate READY puis ACTIVE
none    -> candidate READY puis ACTIVE
```

Un échec ne transforme jamais une ingestion partielle en nouvelle baseline publiée.

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

## Critères d'acceptation

ADR acceptée lorsque les tests prouvent :

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
11. CLI sync enregistre FULL_REBUILD et non un faux INCREMENTAL.
