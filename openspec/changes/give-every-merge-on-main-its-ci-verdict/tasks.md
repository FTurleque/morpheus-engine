# Tâches : donner à chaque fusion sur main son verdict CI

## 1. Contrats

- [ ] 1.1 Écrire `AuditHardeningWorkflowContractTest#aMergeOnMainIsNeverCancelledByTheNextOne` (à écrire) pour `ci.yml`
  et `codeql.yml` (D3 ; fichier de gouvernance, justification de PR) — exigence : `single-integration-branch` › Le run
  d'une fusion dans main NE DOIT PAS être annulé par une fusion suivante ; vérification : rouge sur l'arbre actuel,
  message qui nomme le fichier et l'événement `push`
- [ ] 1.2 Écrire `DependencyCheckWorkflowContractTest#aTrustedRunIsNeverCancelledByAnotherTrustedRun` (à écrire) — exigence :
  `vulnerability-feed-freshness` › A trusted run of the security workflow MUST NOT be cancelled by another trusted
  run ; vérification : rouge sur l'arbre actuel, message qui nomme `security.yml` et les deux événements

## 2. Workflows

- [ ] 2.1 Réécrire les blocs `concurrency` de `ci.yml` et `codeql.yml` (D1) — vérification : test 1.1 vert
- [ ] 2.2 Réécrire le bloc `concurrency` de `security.yml` (D2) — vérification : test 1.2 vert, contrats existants de
  `DependencyCheckWorkflowContractTest` et `D2RepositoryHardeningArchitectureTest` verts
- [ ] 2.3 Mettre à jour `docs/developer/specifications/single-integration-branch.md` et
  `docs/developer/specifications/vulnerability-feed-freshness.md` (exigences, séquence de design.md, traçabilité) —
  vérification : `RepositoryDocumentationCoherenceTest` vert

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate give-every-merge-on-main-its-ci-verdict --strict` — vérification : aucun
  avertissement
- [ ] 3.2 Compléter la matrice de traçabilité des deux pages durables — vérification : chaque scénario a une preuve
  existante, manuelle nommée, ou « preuve manquante »
- [ ] 3.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=AuditHardeningWorkflowContractTest+DependencyCheckWorkflowContractTest+D2RepositoryHardeningArchitectureTest`
  — vérification : verts
- [ ] 3.4 Après la fusion, constater sur deux fusions rapprochées que chaque commit de `main` porte ses checks
  `exact-head` et `SonarQube Cloud Analysis` — vérification : `gh run list --workflow ci.yml --branch main` sans run
  `cancelled` pour ces commits

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire les specs principales consolidées et les deux pages durables.
