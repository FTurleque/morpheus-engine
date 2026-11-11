# Tâches : refuser qu'un workflow renvoie vers une entrée close du registre des risques

## 1. Règle écrite et prouvée

- [x] 1.1 Écrire `RiskRegisterReferencesTest` (`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/`, `@CoherenceCheck`) : une fonction pure (texte du registre, fichiers de `.github/workflows/`) → problèmes, et une méthode qui l'applique au dépôt et exige une liste vide — exigences : `risk-register-references` › Un workflow ne renvoie qu'à une entrée du registre qui n'est pas close, › Une entrée au tableau actif ou requalifiée en position n'est pas close, › Le registre ne dit pas close une entrée qu'il garde au tableau actif, › La règle des renvois ne passe pas à vide ; vérification : test à écrire, vert sur le dépôt actuel
- [x] 1.2 Ajouter un corpus fabriqué par scénario de la spec : entrée close et citée, `RT-99` inconnu, chemin du registre sans identifiant (admis), `RT-14` au tableau (admis), `DT-13` en position (admis), `DT-06` retiré du tableau (refusé « close »), en-tête « clos » avec ligne gardée (refus du registre), section « Risques techniques » renommée (refus de vacuité), workflows sans identifiant (refus de vacuité) ; chaque refus nomme fichier, ligne, identifiant et état — exigences : idem ; vérification : tests à écrire, chacun rouge si le contrôle correspondant est retiré
- [x] 1.3 Casser la règle une fois sur le dépôt : retirer la ligne `RT-13` du tableau des risques techniques et dater son en-tête « clos » ; constater l'échec qui nomme `security.yml` et ses lignes ; puis garder la ligne avec l'en-tête « clos » et constater le refus du registre ; revenir en arrière — exigences : › Un workflow ne renvoie qu'à une entrée…, › Le registre ne dit pas close… ; vérification : sorties rouges consignées dans la PR, `git diff docs/architecture/risks/register.md` vide
- [x] 1.4 Vérifier le marqueur et les comptes de la suite — vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=RiskRegisterReferencesTest,CoherenceCheckMarkerTest,RepositoryTreeOwnershipTest,RepositoryRootOwnershipTest` vert

## 2. Documentation durable

- [x] 2.1 Créer `docs/developer/specifications/risk-register-references.md` à partir du gabarit de `docs/developer/specifications/README.md` (objet, exigences, diagramme de classement repris de `design.md`, écarts, traçabilité) — exigences : toutes celles de la capacité ; vérification : page relue, diagramme rendu
- [x] 2.2 Ajouter la ligne de la capacité à l'index `docs/developer/specifications/README.md`, et, dans `openspec/changes/refuse-a-workflow-that-points-at-a-closed-risk/specs/risk-register-references/spec.md`, transformer le chemin de la page durable écrit en code dans `## Purpose` en lien `/docs/developer/specifications/risk-register-references.md` — vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=RepositoryDocumentationCoherenceTest` vert
- [x] 2.3 Ajouter en tête de `docs/architecture/risks/register.md` une phrase qui dit comment l'état d'une entrée se lit (ligne d'un tableau actif = ouverte, en-tête de requalification = position, sinon close) et que `RiskRegisterReferencesTest` le tient — exigence : › Une entrée au tableau actif ou requalifiée en position n'est pas close ; vérification : `RiskRegisterReferencesTest` et `RepositoryDocumentationCoherenceTest` verts

## 3. Vérification d'ensemble

- [x] 3.1 Lancer `openspec validate refuse-a-workflow-that-points-at-a-closed-risk --strict` — vérification : aucun avertissement
- [x] 3.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → diagramme → tâche → preuve) — vérification : chaque exigence a une preuve existante
- [x] 3.3 Lancer `./mvnw test -pl morpheus-architecture-tests` — vérification : verdict vert, `architectureTestsMinimum` respecté (lu dans `config/m21-quality-ratchets.properties`)
  - Relevé : `./mvnw clean verify` d'intégration (`chore/sprint-15-low`), 19 modules, `BUILD SUCCESS`, 5 349 tests, aucun échec ; `archgate check` 27/27.
  - Écart : la suite entière n'est pas lancée dans le lot ; seules les classes que la règle peut toucher le sont (`RiskRegisterReferencesTest`, `CoherenceCheckMarkerTest`, `RepositoryDocumentationCoherenceTest`, `EnforcementMechanismAccountingTest`, `GateReachabilityArchitectureTest`, `RefusalTestEvidenceArchitectureTest`, `ProductionClassListsArchitectureTest`, `RepositoryTreeOwnershipTest`, `RepositoryRootOwnershipTest`, `D2RepositoryHardeningArchitectureTest`, `DependencyCheckWorkflowContractTest`, toutes vertes). Le passage complet revient à l'intégration en série.
- [x] 3.4 Lancer `archgate check` — vérification : aucune violation
  - Relevé : `archgate check` passe, aucune violation, aucun avertissement.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
