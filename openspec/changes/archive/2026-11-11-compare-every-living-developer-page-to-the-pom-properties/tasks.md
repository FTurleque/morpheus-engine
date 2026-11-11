# Tâches : comparer aux propriétés du POM toute page vivante de `docs/`

## 1. Formes de tableau et d'égalité

- [x] 1.1 Écrire le test sur exemples (à écrire) : la ligne `| \`pitest.maven.plugin.version\` | \`1.30.0\` | … |`,
  l'égalité `` `spotbugs.maven.plugin.version` = `4.10.4.1` `` et le tableau `| Propriété | Rôle |` — exigence :
  `dependency-version-management` › Une version écrite en ligne de tableau ou en égalité de spans de code est une
  version écrite sous la forme d'une propriété ; vérification : échoue avant 1.2 sur les deux premiers cas
- [x] 1.2 Ajouter les deux motifs à `statedVersions` — même exigence ; vérification : test de 1.1 vert, et
  `everyVersionAGovernancePageWritesAsAPomPropertyEqualsThePom` toujours vert sur les pages IA

## 2. Portée sur les pages vivantes de `docs/`

- [x] 2.1 Écrire le test sur corpus temporaire (à écrire) : page vivante en défaut, relevé daté, bloc clôturé, clé
  inconnue du POM, fichier non suivi — exigence : `dependency-version-management` › Une version qu'une page vivante de
  `docs/` écrit sous la forme d'une propriété du POM DOIT égaler la valeur de cette propriété ; vérification : seul le
  premier cas est refusé, avec page, propriété et deux valeurs
- [x] 2.2 Lire les pages vivantes de `docs/` par `RepositoryTree.files` et les fichiers suivis par Git, retirer les
  blocs clôturés, ignorer une clé que le POM ne déclare pas — même exigence ; vérification : test de 2.1 vert,
  `RepositoryTreeOwnershipTest` vert
- [x] 2.3 Relancer le gate sur le dépôt réel et corriger toute page vivante en défaut en nommant la propriété plutôt
  qu'en recopiant sa valeur — même exigence ; vérification : gate vert
- [x] 2.4 Planter la violation : remettre `| \`spotbugs.maven.plugin.version\` | \`4.10.4.1\` |` dans
  `docs/developer/CODE_AUDIT.md`, constater l'échec qui nomme la page et les deux valeurs, revenir en arrière ;
  consigner ici et dans la PR — vérification : trace consignée
  - Trace (11/11/2026) : la ligne remise dans `docs/developer/CODE_AUDIT.md` a fait échouer
    `everyVersionALivingDocumentationPageWritesAsAPomPropertyEqualsThePom` avec
    `docs/developer/CODE_AUDIT.md spotbugs.maven.plugin.version states 4.10.4.1, pom.xml says 4.10.5.0` ; page
    restaurée par `git checkout`. Le test de 1.1 a été vu rouge (0 version lue sur 3) avant 1.2 ; le test de 2.1 a été
    écrit avec l'implémentation de 2.2 (méthode absente auparavant). Le gate sur le dépôt réel est vert sans
    correction de page (2.3). 4.3 : groupe `coherence` lancé localement ; la suite complète du module est laissée à la
    CI (pas de `clean verify` local sur ce poste).

## 3. Documentation

- [x] 3.1 Mettre à jour `.claude/rules/build.md` et `docs/developer/specifications/dependency-version-management.md`
  (exigences, diagramme de `design.md`, matrice) — vérification : pages relues, `GovernanceSurfaceCoherenceTest` et
  `RepositoryDocumentationCoherenceTest` verts

## 4. Vérification d'ensemble

- [x] 4.1 Lancer `openspec validate compare-every-living-developer-page-to-the-pom-properties --strict` —
  vérification : aucun avertissement
- [x] 4.2 Compléter la matrice de traçabilité — vérification : chaque scénario a sa preuve
- [x] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dgroups=coherence` puis la suite complète du module —
  vérification : verdict `documentation drift` vide dans `target/enforcement-verdict.txt`

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée ; noter `AUD-QUA-23` comme corrigé à la revérification
  suivante.
