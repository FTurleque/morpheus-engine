# Tâches : ne lire que les snapshots inachevés pour reprendre les publications interrompues

Prérequis : le sprint 5 de l'audit du 17/11/2026 (`AUD-PRF-21` à `AUD-PRF-26`) est appliqué ; il modifie les mêmes
fichiers de `morpheus-store-sqlite`.

## 1. Reproduction

- [ ] 1.1 Écrire dans `SnapshotRecoveryServiceTest` un cas dont le double de store refuse `listSnapshots` et rend un
  projet à long historique publié (test à écrire) — exigence : `interrupted-publication-recovery` › La reprise ne lit
  que les snapshots inachevés de chaque projet ; vérification : échoue avant 2.2 (le service appelle `listSnapshots`)
- [ ] 1.2 Écrire le test de parité des deux stores sur les six états, avec des instants à fractions de longueurs
  différentes, et la vérification que chaque store livré déclare lui-même la lecture (test à écrire, paquet `m19` des
  tests d'architecture) — exigence : `interrupted-publication-recovery` › Chaque store livré fournit lui-même la lecture
  des snapshots inachevés ; vérification : ne compile pas avant 2.1
- [ ] 1.3 Ajouter à `RuntimeSnapshotRecoveryContractTest` le cas d'un projet sans snapshot inachevé — exigence : même
  capacité, scénario « Un projet sans snapshot inachevé » ; vérification : vert avant et après

## 2. Correctif

- [ ] 2.1 Ajouter au port `SpecificationKnowledgeStore` la lecture des snapshots inachevés, avec son repli par défaut
  sur `listSnapshots` et sa javadoc (D1) — exigence : `interrupted-publication-recovery` › La reprise ne lit que les
  snapshots inachevés de chaque projet ; vérification : le réacteur compile, les doubles de test inchangés
- [ ] 2.2 Faire lire ses candidats à `SnapshotRecoveryService` par cette méthode, seuil de date gardé (D3) — exigence :
  `interrupted-publication-recovery` › La reprise fait échouer les seuls snapshots inachevés créés au plus tard au
  seuil ; vérification : 1.1 vert, `SnapshotRecoveryServiceTest` et les contrats `m19` de reprise verts
- [ ] 2.3 Implémenter la lecture dans `MemorySpecificationKnowledgeStore` et dans `SqliteSpecificationKnowledgeStore`
  (constante SQL filtrée par état, tri des candidats par instant puis identité, D2 et D4) — exigence :
  `interrupted-publication-recovery` › Chaque store livré fournit lui-même la lecture des snapshots inachevés ;
  vérification : 1.2 vert
- [ ] 2.4 Ajouter la constante à `SqliteQueryPlanGuardTest.GUARDED`, recherche de
  `idx_knowledge_snapshots_project_state` — exigence : scénario « Le plan de la lecture SQLite cherche l'index de
  l'état » ; vérification : la garde passe, et échoue en nommant la requête quand l'index est retiré d'une base de test
  (violation plantée consignée dans la PR, puis retirée)

## 3. Documentation durable

- [ ] 3.1 Créer `docs/developer/specifications/interrupted-publication-recovery.md` selon le gabarit de
  `docs/developer/specifications/README.md` (exigences, diagramme de séquence de design.md, écarts, traçabilité) et
  l'indexer dans la section « Persistance SQLite et stores » du README — vérification :
  `RepositoryDocumentationCoherenceTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate read-only-unfinished-snapshots-to-recover-publications --strict` — vérification :
  aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → D1 à D4 → tâche → test) —
  vérification : chaque scénario a sa preuve
- [ ] 4.3 Lancer `./mvnw clean verify` — vérification : vert ; `MorpheusApiRuntimeRecoveryContractTest`,
  `CliCommandConnectionCountTest` et les contrats `m19` de reprise passent sans modification de ce qu'ils affirment

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire `openspec/specs/interrupted-publication-recovery/spec.md` et sa page durable.
