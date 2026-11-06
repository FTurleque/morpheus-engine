# Tâches : comparer à sa source chaque document qui affirme un fait courant

## 1. Versions des pages de gouvernance IA

- [x] 1.1 Ajouter à `GovernanceSurfaceCoherenceTest` la comparaison de D1 (formes `<nom.version>valeur</nom.version>` et `nom.version=valeur` sur `activeGovernanceSurfaces`, contre `pom.xml`), avec ses tests sur corpus temporaire (deux formes, propriété absente du POM, aucune version trouvée) — exigence : `dependency-version-management` › Une version qu'une page de gouvernance IA écrit sous la forme d'une propriété du POM DOIT égaler la valeur de cette propriété ; vérification : rouge sur le dépôt en nommant `.claude/rules/build.md`, `jacoco.version`, `0.8.15` et `0.8.16` ; consigner le message
- [x] 1.2 Corriger `jacoco.version` dans `.claude/rules/build.md`, puis réécrire la phrase « `mcp-sdk` 2.1.0 » pour citer la propriété sans sa valeur (D2) — exigence : idem ; vérification : 1.1 vert
- [x] 1.3 Casser la règle une fois : changer une valeur dans `build.md` (forme XML), constater l'échec, revenir en arrière — vérification : échec nommant la page et la propriété ; `git diff` vide sur `build.md` ensuite

## 2. Cliquets recopiés dans `.claude/rules/`

- [x] 2.1 Ajouter à `GovernanceSurfaceCoherenceTest` la comparaison de D3 (ligne de table dont la première cellule est une clé de `config/m21-quality-ratchets.properties` en span de code), avec ses tests sur corpus temporaire (clé absente du fichier, paragraphe daté ignoré, aucune clé trouvée) — exigence : `stated-fact-currency` › Une valeur de cliquet qu'une page de règles recopie sous le nom de sa clé DOIT égaler cette clé ; vérification : vert sur le dépôt (les copies concordent aujourd'hui)
- [x] 2.2 Casser la règle une fois : changer `testsMinimum` dans la table de `.claude/rules/testing.md`, constater l'échec, revenir en arrière — vérification : échec nommant la page, la clé et les deux valeurs
- [x] 2.3 Remplacer dans `.claude/rules/governance.md` la phrase « Valeurs constatées … » par un renvoi à la table de `.claude/rules/testing.md` et à `config/m21-quality-ratchets.properties` ; retirer `governance.md` de la liste des destinations de `.claude/rules/meta.md` et y dire que la table de `testing.md` est comparée — exigence : idem ; vérification : relecture ; `GovernanceSurfaceCoherenceTest` vert

## 3. Paquets cités par les changements OpenSpec actifs

- [x] 3.1 Écrire le contrôle de D4 et D5 dans une classe ajoutée de `morpheus-architecture-tests` (parcours par `RepositoryTree.files`), avec ses tests sur corpus temporaire : paquet existant sous ses formes, archive ignorée, aucun changement actif, marque « (ancien nom) », marque « (à créer) », marque restée après création, autre formulation refusée. Laisser à `CoherenceCheckMarkerTest` le verdict sur `@CoherenceCheck` — exigences : `stated-fact-currency` › Un nom de paquet `com.morpheus` cité dans un changement OpenSpec actif DOIT exister dans les sources du réacteur ; `stated-fact-currency` › Seule une mention marquée « (ancien nom) » ou « (à créer) » DOIT être admise pour un paquet absent des sources ; vérification : rouge sur le dépôt en nommant `openspec/changes/resolve-both-mcp-peers-through-one-settings-resolver/design.md`, la ligne 13 et le paquet ; consigner le message
- [x] 3.2 Après cet échec, et seulement alors, corriger la ligne 13 de ce `design.md` : nommer `com.morpheus.transport.mcp` ; si l'ancien nom reste utile au récit, le faire suivre de la marque « (ancien nom) ». Ne rien toucher d'autre dans ce changement — exigence : idem ; vérification : 3.1 vert ; `git diff` de ce changement limité à cette ligne
  - Écart : la phrase de la ligne 13 se poursuit ligne 14 (« AUD-ARC-07 (sprint 10) questions that package name »), fausse une fois le paquet renommé ; le diff porte donc sur les lignes 13 et 14 de ce `design.md`, rien d'autre.
- [x] 3.3 Vérifier que les changements actifs de ce lot passent le contrôle (chaque mention de l'ancien paquet porte sa marque) — vérification : 3.1 vert avec ces changements présents
- [x] 3.4 Ajouter les deux marques aux conventions de rédaction de `docs/developer/OPENSPEC_SCHEMA.md` — vérification : `RepositoryDocumentationCoherenceTest` vert

## 4. Documentation durable

- [x] 4.1 Créer `docs/developer/specifications/stated-fact-currency.md` depuis le gabarit de `docs/developer/specifications/README.md`, l'ajouter à l'index, puis transformer dans `specs/stated-fact-currency/spec.md` (`## Purpose`) le chemin écrit en code en lien `/docs/developer/specifications/stated-fact-currency.md` — vérification : `RepositoryDocumentationCoherenceTest` vert
- [x] 4.2 Mettre à jour `docs/developer/specifications/dependency-version-management.md` (exigence ajoutée, traçabilité) — vérification : page relue, liens contrôlés

## 5. Vérification d'ensemble

- [x] 5.1 `openspec validate compare-every-document-that-states-a-fact-to-its-source --strict` — vérification : aucun avertissement
- [x] 5.2 Compléter la matrice de traçabilité des deux pages durables — vérification : chaque exigence a une preuve existante ou « preuve manquante »
- [x] 5.3 `./mvnw test -pl morpheus-architecture-tests -Dtest=GovernanceSurfaceCoherenceTest,RepositoryDocumentationCoherenceTest,ModulePackageCorrespondenceTest,RepositoryTreeOwnershipTest,CoherenceCheckMarkerTest,EnforcementMechanismAccountingTest,D2RepositoryHardeningArchitectureTest` puis `./mvnw test -pl morpheus-architecture-tests` — vérification : verdict vert ; le nombre d'exécutions ne baisse pas sous `architectureTestsMinimum` (`config/m21-quality-ratchets.properties`)
  - Écart : en phase `test` seule, `CoverageQualityGateTest#perModuleCoverageDoesNotRegressBelowQualifiedBaseline` refuse faute de rapports JaCoCo (il exige un `clean verify` préalable) ; la suite entière se conclut dans le `clean verify` du réacteur.
- [x] 5.4 `archgate check` — vérification : aucune violation

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire les specs principales consolidées et les deux pages durables.
