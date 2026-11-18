# Tâches : un échec imprévu de la CLI sort en 10 et est compté (AUD-QUA-31)

## 1. Tests de reproduction

- [ ] 1.1 Écrire dans `morpheus-cli` un test paramétré par les treize familles (à écrire) qui fait lever à chacune un
  `RuntimeException` non classé et exige le code `10`, la ligne `MORPHEUS error [10]: …` et, sous `--json`, un objet
  dont `exitCode` vaut `10` — exigence : `cli-argument-base` › Un échec qu'aucune commande ne prévoit sort avec le
  code d'erreur interne ; vérification : le test échoue sur le code actuel (`4` obtenu), en nommant la famille. Une
  famille sans point d'injection (design.md, Stratégie de vérification) est listée dans le test avec sa raison.
- [ ] 1.2 Écrire le test de comptage (à écrire) : un `OperationalRuntime` de test, journal éteint puis en lignes JSON,
  un échec imprévu rendu par la classification ; exiger le compteur `failure.suppressed.cli-internal-error` à un et
  l'événement `FAILURE_SUPPRESSED` sans le message ; exiger qu'un refus d'état ne bouge pas ce compteur — exigence :
  `suppressed-failure-trace` › Un échec imprévu d'une commande CLI est compté sous une famille nommée ; vérification :
  le test ne compile pas ou échoue avant le correctif.

## 2. Classification partagée et comptage

- [ ] 2.1 Ajouter `CLI_INTERNAL_ERROR` (`cli-internal-error`, niveau `ERROR`, Javadoc qui dit ce que la famille
  signale) à `SuppressedFailureFamily` — exigence : `suppressed-failure-trace` › Un échec imprévu d'une commande CLI
  est compté sous une famille nommée ; vérification : `SuppressedFailureTraceTest`, `SuppressedFailureCountersServedTest`
  verts (ils parcourent `values()`).
- [ ] 2.2 Extraire de `CliCommandRun.run` la classification d'un échec (design.md, D1), lui faire recevoir le recorder
  et compter un `10` (D3) ; `run` reçoit le recorder de son appelant — exigence : les deux exigences ajoutées ;
  vérification : 1.2 vert, `CliErrorLineTest` et `CliCommandTranscriptTest` verts.
- [ ] 2.3 Remplacer le dernier `catch (RuntimeException)` des treize familles par l'appel à la classification, sans
  toucher à leurs `catch` de refus nommés — exigence : `cli-argument-base` › Un échec qu'aucune commande ne prévoit
  sort avec le code d'erreur interne ; vérification : 1.1 vert, `MorpheusStateRefusalExitCodeTest` et
  `OneErrorLineForEveryFamilyTest` verts sans modification de leurs attentes.
- [ ] 2.4 Donner `runtime.recorder()` à `MorpheusServerCli` et `MorpheusPathsCli`, et l'`OperationalRuntime` à
  `MorpheusCli`, depuis `MorpheusMain.run` (D3) ; passer le recorder à la classification dans `MorpheusServerCli` —
  exigence : `suppressed-failure-trace` › Un échec imprévu d'une commande CLI est compté sous une famille nommée ;
  vérification : `DispatchersHoldNoUseCaseArchitectureTest` vert sans changer `CLI_ALLOWANCE`, `MorpheusMainTest` vert.

## 3. Interdit de la copie

- [ ] 3.1 Ajouter à `morpheus-architecture-tests` l'interdit textuel de D2 (à écrire), avec message ; planter la
  violation (`STATE_ERROR` remis dans le dernier `catch` de `MorpheusPortfolioCli`), constater l'échec, revenir —
  exigence : `cli-argument-base` › Un échec qu'aucune commande ne prévoit sort avec le code d'erreur interne ;
  vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=<classe ajoutée>` vert, trace de la violation
  plantée dans la PR. Fichier de gouvernance : la PR justifie l'ajout (section « Justification d'une modification de
  gouvernance ») et écrit l'intention de l'assertion textuelle (section « Intention de chaque assertion textuelle »).

## 4. Documentation

- [ ] 4.1 Ajouter la ligne `cli-internal-error` à la table des familles de `docs/developer/OPERABILITY.md` — exigence :
  `suppressed-failure-trace` › Un échec imprévu d'une commande CLI est compté sous une famille nommée ; vérification :
  ligne relue contre la Javadoc de la famille.
- [ ] 4.2 Dire dans `docs/user/CLI.md` § 19 qu'un échec imprévu sort en `10` sur toutes les commandes, sans chemin
  `src/` ni nom de gate (`UserDocumentationSpeaksOfTheInstalledProductTest`) ; ajouter l'entrée aux notes de version de
  la version courante (`ProductMetadata`) sous `docs/user/release-notes/` — exigence : `cli-argument-base` › Un échec
  qu'aucune commande ne prévoit sort avec le code d'erreur interne ; vérification : relecture, contrôles de cohérence
  documentaire verts.
- [ ] 4.3 Mettre à jour `docs/developer/specifications/cli-argument-base.md` (comportement, diagramme du refus,
  écarts, traçabilité) et `docs/developer/specifications/suppressed-failure-trace.md` (famille, traçabilité) —
  vérification : diagrammes rendus, liens relus.

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate answer-an-unforeseen-cli-failure-as-an-internal-error --strict` — vérification :
  aucun avertissement.
- [ ] 5.2 Compléter la matrice de traçabilité des deux pages durables (exigence → scénario → D1 à D4 ou diagramme →
  tâche → test) — vérification : chaque exigence a une preuve existante ou est marquée « preuve manquante ».
- [ ] 5.3 Lancer `./mvnw test -pl morpheus-cli,morpheus-application -am` puis
  `./mvnw test -pl morpheus-architecture-tests` — vérification : 0 échec, `target/enforcement-verdict.txt` sans
  violation.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire les specs principales consolidées `cli-argument-base` et `suppressed-failure-trace` et
  leurs pages durables.
