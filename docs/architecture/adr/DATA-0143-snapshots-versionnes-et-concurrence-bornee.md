---
id: DATA-0143
title: "Snapshots versionnés, activation atomique, récupération et concurrence bornées"
domain: data
rules: false
---

# Snapshots versionnés, activation atomique, récupération et concurrence bornées

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0120, ADR-0086

## Context

MORPHEUS maintient une représentation de connaissance qui évolue. Une réingestion peut ajouter, modifier ou retirer
des centaines d'entités ; publiées au fil de l'eau, ces modifications laisseraient un consommateur observer un état
incohérent (une nouvelle exigence visible, un ancien critère encore là, un lien de traçabilité pas encore à jour, un
pointeur de version déjà déplacé). Le risque grandit avec l'indexation incrémentale, les références croisées,
l'historique, les requêtes concurrentes, la coexistence `CURRENT` / `PROPOSED` et les intégrations NEXUS et JARVIS.

Il faut aussi identifier l'état courant, garder un historique, comparer deux états, rejouer une ingestion, conserver
l'état courant quand une publication échoue et reconstruire le store depuis les sources, sans imposer à toutes les
technologies de stockage la même mécanique interne. Les garanties doivent rester explicites en cas d'interruption, de
candidat invalide, de concurrence entre commandes, de lecteurs simultanés et de base verrouillée.

Options écartées :

- **modifier directement l'état courant pendant l'ingestion** : expose des états intermédiaires incohérents ;
- **une transaction SQL globale comme contrat public** : couple le domaine à une famille de backend ;
- **event sourcing complet** : puissant, mais d'une complexité disproportionnée sans besoin démontré.

L'option retenue est un snapshot logique à activation atomique observable, dont le mécanisme interne reste libre.

## Decision

### 1. Snapshot et version métier

Deux concepts distincts :

- `SpecificationVersion` : version logique du contenu de spécification (ADR-0136) ;
- `KnowledgeSnapshot` : état technique ingéré et normalisé par MORPHEUS pour un projet, dont les métadonnées sont
  `KnowledgeSnapshotMetadata` (identité, projet, prédécesseur optionnel, état, révision source optionnelle, date de
  création).

```text
SpecificationVersion != KnowledgeSnapshot
KnowledgeSnapshotState != TemporalState
KnowledgeSnapshotState != ChangeLifecycleState
```

Deux snapshots techniques peuvent représenter la même version logique (mise à jour du provider, correction d'un
mapping, reconstruction du store) : une réindexation technique ne modifie pas nécessairement la spécification métier.
Une révision de la source (SHA Git, version de document, ETag, horodatage) est conservée comme provenance ; elle n'est
l'identité ni du snapshot ni de la `SpecificationVersion`.

Un snapshot peut contenir à la fois du contenu `CURRENT`, des éléments `PROPOSED` et des références historiques : sa
cohérence signifie que ces dimensions sont correctement étiquetées, et une requête `CURRENT` filtre selon le domaine,
indépendamment du snapshot technique.

### 2. Snapshot logique

La décision n'impose pas de copie physique complète. Une implémentation peut utiliser MVCC, colonnes de version,
identifiants de génération, copy-on-write, tables de préparation puis activation, journal et vue matérialisée, ou
snapshot natif du backend. L'invariant public reste le même : avant publication les consommateurs voient `Vn`, après
publication ils voient `Vn+1`, jamais un état intermédiaire. Un état publié n'est observable que par un snapshot
`ACTIVE` complet.

### 3. États techniques

`KnowledgeSnapshotState` compte six états : `BUILDING`, `VALIDATING`, `READY`, `ACTIVE`, `FAILED`, `RETIRED`. Ils
décrivent le snapshot technique, pas le cycle métier d'un changement.

```text
BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED
                      \-> FAILED
```

| État | Visibilité |
|---|---|
| `BUILDING`, `VALIDATING`, `READY`, `FAILED` | invisibles comme état courant |
| `ACTIVE` | seul état courant, lu par `activeSnapshot(projectId)` |
| `RETIRED` | historique |

Un candidat commence en `BUILDING`. `SpecificationKnowledgeStore` expose aussi un listing déterministe des snapshots
par projet (`listSnapshots`) pour la récupération et les diagnostics.

### 4. Transitions par compare-and-set

`SpecificationKnowledgeStore` reste l'unique port de persistance des métadonnées de snapshot. Ses transitions passent
par `transitionSnapshotState(snapshotId, expectedState, targetState)`, qui :

- exige l'état source attendu et échoue par `SnapshotConflictException` si le snapshot a changé entre-temps ;
- ne produit jamais `ACTIVE` ni `RETIRED`.

`SnapshotLifecycleService` orchestre `BUILDING → VALIDATING`, puis `VALIDATING → READY` si le `SnapshotValidator` rend
un résultat valide et `VALIDATING → FAILED` sinon. Une exception du validateur fait aussi passer le candidat à
`FAILED`, puis remonte comme `SnapshotValidationException`. Le service ne contient aucune logique propre à un backend.

Avant activation, la validation vérifie au minimum l'unicité des identités, la cohérence des références internes, le
marquage `UNRESOLVED` des liens vers des cibles absentes, la présence de la version et de la provenance, l'absence de
mélange non contrôlé entre `CURRENT` et `PROPOSED`, et les règles d'intégrité du store. Un snapshot porteur
d'avertissements peut être publié ; un snapshot structurellement invalide ne l'est pas.

### 5. Activation atomique, seule propriétaire d'ACTIVE

`activateSnapshot(snapshotId, expectedActiveSnapshotId)` est la seule opération qui produit `ACTIVE` et `RETIRED` :

```text
Vn   ACTIVE
Vn+1 READY (predecessor = Vn)
        | activation atomique
Vn   RETIRED
Vn+1 ACTIVE
```

Le store vérifie ensemble que la cible est `READY`, que son prédécesseur est l'actif attendu et que l'actif courant est
l'actif attendu. Sinon il lève `SnapshotConflictException` sans modifier l'actif. Un projet a au plus un snapshot
`ACTIVE`. En SQLite, l'ancien `ACTIVE` est retiré et le nouveau activé dans la même transaction.

Sous concurrence :

- deux commandes qui ciblent le même prédécesseur ne publient pas deux successeurs : une seule gagne, l'autre échoue
  explicitement ; un snapshot construit sur un prédécesseur obsolète est détecté, et aucune activation n'écrase
  silencieusement une version plus récente ;
- les lecteurs concurrents observent l'ancien ou le nouveau `ACTIVE`, jamais une absence transitoire produite par
  l'activation.

Un échec avant activation laisse l'ancien actif en place, qui reste l'état de référence :

```text
Vn   ACTIVE
Vn+1 BUILDING -> VALIDATING -> FAILED
=> Vn reste ACTIVE
```

Un snapshot `RETIRED` n'est jamais réactivé ; l'historique publié est `RETIRED* -> ACTIVE`. La comparaison de
snapshots, le rollback logique et la rétention relèvent d'ADR-0036.

### 6. Récupération d'un candidat interrompu

Un candidat `BUILDING` ou `VALIDATING` laissé par une interruption peut être récupéré vers `FAILED` uniquement s'il est
plus ancien qu'un cutoff explicite. Le cutoff par défaut est de dix minutes (`SnapshotRecoveryPolicy`), afin qu'une
récupération ne tue pas un candidat fraîchement détenu par une autre commande locale. `READY`, `ACTIVE`, `RETIRED` et
`FAILED` ne sont jamais modifiés par cette récupération.

### 7. Attente bornée sur une base verrouillée

L'attente d'un verrou SQLite est bornée. Le défaut de production est de 5 000 ms ; les tests peuvent utiliser une
borne plus courte sans changer le contrat de production. Après libération d'un verrou, le même store reprend les
opérations normales.

### 8. Reconstruction

Le store est une projection reconstructible : vider le store dérivé, réingérer les sources et reconstruire le snapshot
actif ne perd aucune information dérivable des sources. Toute information créée localement par MORPHEUS et absente de
la source a une persistance clairement séparée avant que l'écriture soit activée.

### Invariants

```text
failed candidate != ACTIVE
interrupted BUILDING/VALIDATING -> FAILED only after explicit stale cutoff
previous valid ACTIVE remains authoritative after failed publication
published history = RETIRED* -> ACTIVE
concurrent successor activation -> one winner + explicit loser failure
concurrent readers -> previous ACTIVE or next ACTIVE, never partial
SQLite locked != indefinite hang
stale recovery != overwrite of fresh concurrent work
```

## Do's and Don'ts

### Do

- Faire démarrer tout candidat en `BUILDING`.
- Passer chaque transition non publiante par `transitionSnapshotState` avec l'état source attendu.
- Faire passer un candidat invalide, ou dont la validation lève une exception, à `FAILED`.
- Publier uniquement par `activateSnapshot`, avec l'actif attendu ; en SQLite, retirer l'ancien `ACTIVE` et activer le
  nouveau dans la même transaction.
- Faire échouer explicitement la commande perdante d'une activation concurrente.
- Lire l'état courant uniquement par `activeSnapshot(projectId)`.
- Récupérer vers `FAILED` un candidat `BUILDING` ou `VALIDATING` seulement au-delà du cutoff explicite.
- Borner l'attente d'un verrou SQLite.
- Conserver la révision de la source comme provenance.
- Faire respecter le même contrat par tous les adaptateurs du port, `store-memory` et `store-sqlite` compris.

### Don't

- Ne pas produire `ACTIVE` ou `RETIRED` par `transitionSnapshotState`, ni écrire directement un snapshot dans ces états.
- Ne pas activer un snapshot qui n'est pas `READY`.
- Ne pas laisser un candidat `FAILED` devenir `ACTIVE` ni modifier l'actif.
- Ne pas exposer `BUILDING`, `VALIDATING`, `READY` ou `FAILED` comme état courant, ni d'instant sans `ACTIVE` pendant
  une activation.
- Ne pas publier deux successeurs d'un même prédécesseur.
- Ne pas réactiver un snapshot `RETIRED`.
- Ne pas modifier un snapshot `READY`, `ACTIVE`, `RETIRED` ou `FAILED` lors d'une récupération de candidat interrompu.
- Ne pas attendre un verrou SQLite sans borne.
- Ne pas utiliser une révision source comme identité de snapshot ou de version métier.
- Ne pas confondre `KnowledgeSnapshot` et `SpecificationVersion`.

## Consequences

### Positives

- Les consommateurs voient toujours un état cohérent ; l'état publié reste failure-atomic.
- Historique structuré, comparaison de versions et tests reproductibles.
- Le mécanisme transactionnel du backend reste découplé du domaine.
- Le store reste reconstructible depuis les sources.
- Les interruptions sont diagnostiquables et récupérables ; la contention a une durée bornée.
- Les tests Memory et SQLite portent le même invariant métier.

### Négatives

- Modèle technique supplémentaire et publication plus complexe.
- Coût de stockage potentiel et nécessité d'une politique de rétention.
- Gestion des candidats `FAILED` ou interrompus.
- Distinction supplémentaire entre version métier et snapshot technique.
- La détection d'un candidat interrompu repose sur une politique temporelle explicite : un candidat récent réellement
  abandonné n'est pas récupéré avant expiration du cutoff.

### Risques

- Duplication excessive : le snapshot est logique, un stockage différentiel est permis.
- Candidat bloqué en `BUILDING` : état explicite et récupération au-delà du cutoff (§6).
- Publication d'un snapshot incomplet : phase `VALIDATING` et invariants obligatoires.
- Le verrou multi-processus reste soumis aux primitives SQLite et système de fichiers de la plateforme.
- La borne d'attente n'a pas de source unique dans le code : deux constantes `DEFAULT_BUSY_TIMEOUT_MILLIS = 5_000`
  (`SqliteDatabaseSecurity`, `SqliteSpecificationKnowledgeStore`) et plusieurs stores SQLite qui écrivent
  `PRAGMA busy_timeout = 5000` en littéral. Une modification du défaut doit les reprendre tous ; aucun test ne compare
  ces littéraux aux constantes.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SnapshotLifecycleServiceTest.java` :
  activation atomique avec retrait de l'ancien actif, candidat invalide ou validateur en exception passé à `FAILED`
  sans toucher l'actif, états non actifs jamais renvoyés comme courants, prédécesseur obsolète rejeté, activation avant
  `READY` rejetée, candidat obligatoirement en `BUILDING`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SpecificationKnowledgeStoreContractTest.java` :
  même contrat de snapshot sur le store mémoire et le store SQLite.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteSnapshotLifecyclePersistenceTest.java` :
  l'actif et son prédécesseur `RETIRED` survivent à la réouverture.
- `morpheus-store-sqlite/src/main/resources/db/migration/V001__foundation.sql` : contrainte `CHECK` sur les six états
  et index unique partiel `uq_knowledge_snapshots_active_project` (un seul `ACTIVE` par projet).
- Adaptateurs `MemorySpecificationKnowledgeStore` et `SqliteSpecificationKnowledgeStore` : refus de `ACTIVE` et
  `RETIRED` hors activation.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/SnapshotRecoveryContractTest.java` :
  récupération Memory et SQLite des seuls candidats au-delà du cutoff, `ACTIVE` préservé à la réouverture.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/RuntimeSnapshotRecoveryContractTest.java` :
  récupération multi-projets en ordre déterministe.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/FailedPublishRecoveryContractTest.java` :
  un candidat invalide ne fuit jamais dans `ACTIVE`, une reconstruction valide publie ensuite.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteConcurrencyHardeningTest.java` : base
  verrouillée en échec dans la borne puis reprise, activation concurrente à gagnant unique.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteConcurrentReaderContractTest.java` : lecteurs
  qui voient l'ancien ou le nouveau `ACTIVE`, jamais un état vide ou partiel.
- Revue : aucune autre opération que `activateSnapshot` ne produit `ACTIVE`.

## References

- [ADR-0003](DATA-0003-specification-knowledge-store.md) — store de connaissance de spécification.
- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — SQLite comme store persistant.
- [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — migrations du schéma SQLite.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — projection temporelle et version métier.
- [ADR-0036](DATA-0036-published-history-comparison-and-logical-rollback.md) — historique publié, comparaison et
  rollback logique.
- [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) — copie gardée avant migration du schéma.
- [ADR-0114](BE-0114-database-work-runs-on-a-bounded-pool-of-platform-threads.md) — pool borné pour le travail en
  base.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie des changements.
- [ADR-0144](DATA-0144-publication-complete-cli-et-http.md) — publication complète d'un snapshot.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — contenu métier persisté par snapshot.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md), [VALIDATION_M3](../../project/validation/VALIDATION_M3.md),
  [VALIDATION_M19](../../project/validation/VALIDATION_M19.md)
