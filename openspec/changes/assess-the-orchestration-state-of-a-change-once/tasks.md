# Tâches : évaluer une seule fois l'état d'orchestration d'un changement

## 1. Reproduire avant de corriger

- [ ] 1.0 Vérifier que le budget M19 de l'état d'orchestration déclaré par `AUD-PRF-30` est livré et relever sa métrique sur `HEAD` — vérification : ligne de `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md` et métrique avant correction citées dans la PR ; sinon s'arrêter
- [ ] 1.1 Écrire `ChangeOrchestrationStateCostTest` (à écrire, `morpheus-architecture-tests`, paquet `com.morpheus.architecture`) sur le patron de `ChangeAnalysisTraversalCostTest` : stores mémoire enveloppés dans des ports comptés, deux snapshots (le changement seul, puis parmi au moins vingt autres changements liés), un cycle de vie fourni — exigence : `change-orchestration-state` › Building the orchestration state SHALL read the snapshot content once and assess the requested change only ; vérification : rouge sur `HEAD`, message qui nomme les deux comptes (contenu, requirements, traçabilité) ; la description de PR justifie l'ajout d'un test sous `morpheus-architecture-tests`
- [ ] 1.2 Dans le même test, l'assertion « une lecture du contenu, une liste des requirements CURRENT » pour neuf états cibles, et l'égalité des états du changement sur les deux snapshots (tous champs sauf `snapshot`) — vérification : la première rouge sur `HEAD`, l'égalité verte sur `HEAD` (référence que la correction ne doit pas déplacer)

## 2. La correction

- [ ] 2.1 Ajouter à `ChangeCompletenessService` l'évaluation publique d'un seul changement sur snapshot, contenu et identités CURRENT déjà lus, réutilisant `assessChange` — exigence : idem ; vérification : 1.2 (égalité) reste vert
- [ ] 2.2 Ajouter à `ChangeLifecycleQualityService` et `ChangeTransitionEvaluationService` la variante qui reçoit snapshot, contenu et complétude ; faire déléguer `assessDerivedActive` / `evaluateActive` à cette variante — exigence : idem ; vérification : `ChangeOrchestrationStateServiceTest`, `JarvisOrchestrationContractTest` et les tests de `orchestration.transition` verts sans modification
- [ ] 2.3 Faire lire une fois snapshot, contenu et requirements par `ChangeOrchestrationStateService.active` et passer les valeurs aux neuf évaluations — exigence : idem ; vérification : 1.1 et 1.2 verts
- [ ] 2.4 Casser la règle une fois : rétablir l'appel `assessSnapshot` dans la boucle — vérification : 1.1 rouge en nommant les deux comptes, puis revert ; la trace va dans la Javadoc du test ou la PR
- [ ] 2.5 Compléter le test du refus d'un changement absent : aucune évaluation de transition n'est faite — exigence : idem, scénario « A change absent from the snapshot is still refused » ; vérification : `ChangeOrchestrationStateServiceTest` vert

## 3. Documentation durable

- [ ] 3.1 Mettre à jour `docs/developer/specifications/change-orchestration-state.md` : exigence nouvelle, diagramme de séquence cible de design.md, ligne de traçabilité — vérification : page relue, diagramme rendu, `RepositoryDocumentationCoherenceTest` vert

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate assess-the-orchestration-state-of-a-change-once --strict` — vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → diagramme → tâche → preuve) — vérification : chaque scénario a sa preuve ou est marqué « preuve manquante »
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=ChangeOrchestrationStateCostTest,JarvisOrchestrationContractTest` puis `./mvnw clean verify` — vérification : vert ; aucun ratchet de `config/m21-quality-ratchets.properties` baissé

## Workflow follow-up

- Livrer avant `resolve-each-governance-report-once-per-evaluation`, qui réutilise la variante de 2.2.
- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
