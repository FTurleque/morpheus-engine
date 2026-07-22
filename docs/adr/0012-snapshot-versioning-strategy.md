# ADR-0012 — Publier l'état de connaissance par snapshots versionnés

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Portée : ingestion, versionnement, stockage, cohérence

---

## 1. Contexte

MORPHEUS doit maintenir une représentation de connaissance qui évolue dans le temps.

Lors d'une réingestion, plusieurs centaines ou milliers d'entités peuvent être ajoutées, modifiées ou retirées. Si le moteur publie ces changements au fil de l'eau, un consommateur peut observer un état incohérent :

```text
new Requirement visible
old AcceptanceCriterion still visible
TraceabilityLink not updated yet
version pointer already changed
```

Ce problème devient plus grave avec :

- l'indexation incrémentale ;
- les références croisées ;
- l'historique ;
- les requêtes concurrentes ;
- les changements `CURRENT` / `PROPOSED` ;
- les intégrations futures avec NEXUS et JARVIS.

---

## 2. Problème

MORPHEUS doit garantir qu'un consommateur observe un état de connaissance cohérent sans imposer à toutes les technologies de stockage une mécanique interne identique.

Il faut également pouvoir :

- identifier l'état courant ;
- conserver un historique minimal ;
- comparer deux états ;
- rejouer une ingestion ;
- revenir logiquement à l'état précédent en cas d'échec de publication ;
- reconstruire l'index depuis les sources.

---

## 3. Forces en présence

### Cohérence

Pas d'état courant partiellement remplacé.

### Simplicité du domaine

Le snapshot doit rester un concept MORPHEUS, pas une transaction SQL exposée.

### Historique

Certaines versions doivent rester requêtables.

### Coût de stockage

Une copie complète de toutes les données à chaque version peut être excessive.

### Incrémental

Le moteur doit pouvoir publier un snapshot construit à partir de deltas.

### Reconstruction

Le store reste dérivable des sources ; il ne doit pas devenir l'unique vérité impossible à reconstruire.

---

## 4. Décision proposée

MORPHEUS introduit le concept de :

```text
SpecificationVersion
KnowledgeSnapshot
```

Un snapshot représente un état cohérent du modèle normalisé pour un projet à une révision donnée.

La publication d'un snapshot est atomique **au niveau observable** :

```text
before publish -> consumers see Vn
after publish  -> consumers see Vn+1
```

Ils ne doivent pas observer l'état intermédiaire.

---

## 5. Snapshot logique, pas copie physique obligatoire

Cette ADR n'impose pas une duplication physique complète des données.

Une implémentation peut utiliser :

- MVCC ;
- version columns ;
- generation IDs ;
- copy-on-write ;
- tables staging + activation ;
- event log + materialized view ;
- snapshot natif du backend.

L'invariant public reste le même.

---

## 6. Structure conceptuelle

```text
KnowledgeSnapshot
- id: SnapshotId
- projectId: ProjectSpecificationId
- specificationVersionId: SpecificationVersionId
- sourceRevision: string?
- providerId: string
- providerVersion: string?
- formatVersion: string?
- createdAt: Instant
- predecessor: SnapshotId?
- status: SnapshotStatus
- fingerprint: string?
- statistics: SnapshotStatistics
```

Statuts candidats :

```text
BUILDING
VALIDATING
READY
ACTIVE
FAILED
RETIRED
```

Ces statuts décrivent le snapshot technique, pas le cycle métier d'un changement.

---

## 7. Cycle de publication

Flux candidat :

```text
Source
  ↓
ProviderSnapshot
  ↓
Normalize
  ↓
Build KnowledgeSnapshot Vn+1
  ↓
Validate invariants
  ↓
READY
  ↓
Atomic activation
  ↓
ACTIVE Vn+1
  ↓
Retire previous active snapshot according to retention policy
```

En cas d'échec avant activation :

```text
Vn remains ACTIVE
Vn+1 becomes FAILED
```

---

## 8. Validation avant activation

Avant publication, MORPHEUS doit au minimum vérifier :

- unicité des identités selon les règles du snapshot ;
- cohérence des références internes ;
- liens vers cibles absentes explicitement marqués `UNRESOLVED` ;
- version et provenance présentes ;
- absence de mélange non contrôlé entre `CURRENT` et `PROPOSED` ;
- diagnostics provider non fatals connus ;
- règles d'intégrité du store.

Un snapshot comportant des warnings peut être publiable ; un snapshot invalide structurellement ne doit pas l'être.

---

## 9. Idempotence

Réingérer exactement la même source à la même révision doit être détectable.

Le moteur peut calculer un fingerprint sur :

```text
source revision
provider version
format version
normalized content fingerprints
```

Si le résultat est identique :

- soit aucun nouveau snapshot n'est publié ;
- soit un snapshot équivalent est reconnu comme tel.

La politique précise sera choisie selon le backend.

---

## 10. Version métier vs snapshot technique

Il faut distinguer :

### `SpecificationVersion`

Version logique du contenu de spécification.

### `KnowledgeSnapshot`

État technique ingéré et normalisé par MORPHEUS.

Deux snapshots techniques peuvent éventuellement représenter la même version logique, par exemple après :

- mise à jour du provider ;
- correction d'un mapping ;
- reconstruction du store.

Cette distinction évite de faire croire qu'une réindexation technique modifie nécessairement la spécification métier.

---

## 11. Source revision

Lorsque la source est versionnée par Git :

```text
sourceRevision = commit SHA
```

peut être conservé comme provenance.

Mais le commit Git n'est pas l'identité du snapshot MORPHEUS ni de `SpecificationVersion`.

D'autres sources pourront fournir :

- version de document ;
- timestamp ;
- ETag ;
- revision ID ;
- aucune révision native.

---

## 12. Current / Proposed

Un snapshot peut contenir simultanément :

- l'état `CURRENT` ;
- des éléments `PROPOSED` ;
- des références historiques nécessaires.

La cohérence du snapshot signifie que ces dimensions sont correctement étiquetées, pas qu'il ne contient qu'un seul état temporel.

Une requête `CURRENT` filtre selon le domaine, indépendamment de la version technique du snapshot.

---

## 13. Comparaison de snapshots

MORPHEUS doit pouvoir dériver au minimum :

```text
ADDED
MODIFIED
REMOVED
UNCHANGED
```

et idéalement :

```text
MOVED
RENAMED
```

lorsque l'identité stable permet de le déterminer.

Les comparaisons servent à :

- historique ;
- ingestion incrémentale ;
- diagnostics ;
- analyse de changements ;
- invalidation de caches ;
- notifications futures.

---

## 14. Rétention

MORPHEUS ne doit pas conserver indéfiniment tous les snapshots techniques par défaut sans politique.

Politique candidate :

- snapshot actif toujours conservé ;
- predecessor récent conservé ;
- snapshots associés à versions métier importantes conservables ;
- snapshots techniques redondants purgeables ;
- historique logique des entités conservé selon les besoins d'explicabilité.

La politique exacte dépendra des mesures M0.

---

## 15. Reconstruction

Le store est une projection reconstructible.

MORPHEUS doit pouvoir :

```text
clear derived store
reingest sources
rebuild active snapshot
```

sans perdre les informations qui ne sont pas dérivables des sources.

Conséquence : toute information MORPHEUS créée localement et non présente dans la source devra avoir une stratégie de persistance clairement séparée avant que l'écriture soit activée.

---

## 16. Concurrence

Le MVP n'exige pas une forte concurrence multi-utilisateur, mais le modèle doit éviter une impasse.

Règle minimale :

- une seule activation atomique gagne pour un projet ;
- un snapshot construit sur un predecessor obsolète doit être détectable ;
- aucune activation silencieuse ne doit écraser une version plus récente sans politique.

---

## 17. Conséquences positives

- état cohérent pour les consommateurs ;
- rollback logique simple ;
- historique structuré ;
- tests reproductibles ;
- meilleure ingestion incrémentale ;
- comparaison de versions ;
- découplage du mécanisme transactionnel du backend ;
- reconstruction possible.

---

## 18. Conséquences négatives

- modèle technique supplémentaire ;
- coût de stockage potentiel ;
- complexité de publication ;
- nécessité d'une politique de rétention ;
- gestion des snapshots failed/stale ;
- distinction supplémentaire entre version métier et technique.

---

## 19. Alternatives étudiées

### A. Modifier directement l'état courant au fil de l'ingestion

**Rejetée.**

Expose des états intermédiaires incohérents.

### B. Une transaction SQL globale comme contrat public

**Rejetée.**

Couple le domaine à une famille de backend.

### C. Snapshot logique avec activation atomique observable

**Retenue.**

Le mécanisme interne reste libre.

### D. Event sourcing complet dès le MVP

**Différée.**

Puissant mais complexité disproportionnée sans besoin démontré.

---

## 20. Risques et mitigations

### Risque — duplication excessive

Mitigation : snapshot logique, stockage différentiel ou MVCC possible.

### Risque — confusion version métier / technique

Mitigation : concepts distincts et documentation explicite.

### Risque — accumulation de snapshots

Mitigation : politique de rétention mesurée.

### Risque — snapshot bloqué en BUILDING

Mitigation : état explicite, nettoyage et reprise idempotente.

### Risque — publication d'un snapshot incomplet

Mitigation : phase `VALIDATING` et invariants obligatoires.

---

## 21. Validation M0

L'expérience E05 doit démontrer :

1. activation V1 ;
2. construction V2 ;
3. interruption avant activation ;
4. V1 toujours lisible ;
5. reprise et activation V2 ;
6. rejeu idempotent ;
7. comparaison V1/V2 ;
8. conservation de provenance ;
9. comportement sur snapshot invalide ;
10. coût stockage/performance.

Tests à exécuter sur backend mémoire et backend persistant candidat.

---

## 22. Critères d'acceptation

Cette ADR peut passer à **Acceptée** lorsque :

- les concepts `SpecificationVersion` et `KnowledgeSnapshot` sont stabilisés ;
- les deux backends M0 passent le scénario d'activation atomique ;
- une interruption ne rend pas l'état courant incohérent ;
- le rejeu est idempotent ;
- current/proposed reste correctement isolé ;
- la politique minimale de rétention est définie ;
- le coût de snapshot reste compatible avec les jeux de volume ;
- la reconstruction depuis les sources est démontrée.

---

## 23. Impact sur les autres décisions

Cette ADR influence :

- identité stable ;
- `SpecificationKnowledgeStore` ;
- ingestion incrémentale ;
- historique ;
- comparaison de versions ;
- qualité et diagnostics ;
- contexte NEXUS ;
- orchestration future de JARVIS.