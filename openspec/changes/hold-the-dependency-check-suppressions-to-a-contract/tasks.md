# Tâches : tenir le fichier de suppressions Dependency-Check par un contrat

## 1. Contrat écrit et prouvé

- [ ] 1.1 Écrire `DependencyCheckSuppressionContractTest` (`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/`, `@CoherenceCheck`) : une fonction pure qui rend la liste des problèmes d'un document de suppressions, à partir des `<module>`, de la `<version>` et de `<sqlite-jdbc.version>` du POM racine et de la table des exemptions ; une méthode l'applique au vrai fichier et exige une liste vide — exigences : `dependency-check-suppression-contract` › Chaque règle de suppression prend une des deux formes admises, › Une règle d'association vise exactement un module MORPHEUS à la version du réacteur, › Une règle de vulnérabilité nommée vise un seul CVE sur les bibliothèques extraites du pilote, › Chaque règle de suppression porte sa justification, › Les règles exemptées du contrôle des règles inutilisées sont exactement celles que le contrat nomme ; vérification : test à écrire, vert sur le fichier actuel
- [ ] 1.2 Ajouter un corpus fabriqué par scénario de refus de la spec (règle sans CPE, `cvssBelow`/`sha1`/`gav`/deux `cpe`, fichier vide, expression élargie, expression non ancrée, dépendance tierce, version du réacteur changée, `filePath` `.*`, `vulnerabilityName` qui n'est pas un CVE, règle sans note, exemption sans raison, exemption hors table, entrée de table sans règle) ; chaque cas exige le problème attendu, qui nomme la règle — exigences : idem ; vérification : tests à écrire, chacun rouge si le contrôle correspondant est retiré
- [ ] 1.3 Casser le contrat une fois sur le vrai fichier : poser `base="true"` sur la règle de `morpheus-cli`, puis remplacer son `packageUrl` par `^pkg:maven/io\.github\.fturleque/morpheus-.*$` ; constater les deux échecs nommés ; revenir en arrière — exigences : › Les règles exemptées…, › Une règle d'association… ; vérification : sortie des deux runs rouges consignée dans la PR, fichier restauré (`git diff config/` vide)
- [ ] 1.4 Vérifier le marqueur et les comptes de la suite — vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=DependencyCheckSuppressionContractTest,CoherenceCheckMarkerTest,EnforcementMechanismAccountingTest` vert

## 2. Documentation durable

- [ ] 2.1 Créer `docs/developer/specifications/dependency-check-suppression-contract.md` à partir du gabarit de `docs/developer/specifications/README.md` (objet, exigences, diagramme de classement repris de `design.md`, écarts, traçabilité) — exigences : toutes celles de la capacité ; vérification : page relue, diagramme rendu
- [ ] 2.2 Ajouter la ligne de la capacité à l'index `docs/developer/specifications/README.md`, et, dans `openspec/changes/hold-the-dependency-check-suppressions-to-a-contract/specs/dependency-check-suppression-contract/spec.md`, transformer le chemin de la page durable écrit en code dans `## Purpose` en lien `/docs/developer/specifications/dependency-check-suppression-contract.md` — vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=RepositoryDocumentationCoherenceTest` vert
- [ ] 2.3 Dans `docs/developer/specifications/vulnerability-feed-freshness.md`, remplacer « Preuve manquante (R5) » par le test du groupe 1 et renvoyer vers la page nouvelle pour les autres suppressions — exigence : `vulnerability-feed-freshness` › A suppression on the embedded engine SHALL name one vulnerability and the extracted libraries only (preuve seulement, exigence inchangée) ; vérification : traçabilité relue, liens contrôlés par `RepositoryDocumentationCoherenceTest`
- [ ] 2.4 Dans `.claude/rules/build.md`, dire que la justification d'une suppression va dans ses `<notes>` et qu'elle est tenue par `DependencyCheckSuppressionContractTest` — vérification : relecture ; aucun chiffre périssable ajouté

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate hold-the-dependency-check-suppressions-to-a-contract --strict` — vérification : aucun avertissement
- [ ] 3.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → diagramme → tâche → preuve) — vérification : chaque exigence a une preuve existante
- [ ] 3.3 Lancer `./mvnw test -pl morpheus-architecture-tests` — vérification : verdict vert, `architectureTestsMinimum` respecté (lu dans `config/m21-quality-ratchets.properties`)
- [ ] 3.4 Lancer `archgate check` — vérification : aucune violation

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
