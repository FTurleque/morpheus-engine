# Tâches : budgéter l'orchestration, le rapport qualité, l'évaluation de gouvernance et le portfolio (AUD-PRF-30)

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D1 (budgets nocturnes M19 recommandés, ou seuls tests de comptage) — vérification : décision écrite
  dans la PR ; si B, réécrire la proposition et les specs avant toute autre tâche.
- [ ] 1.2 Fixer les quatre bornes (D2) et la taille de la fixture de portfolio (D4), d'après l'usage — vérification :
  valeurs et raisons écrites par le mainteneur dans la PR, avant toute mesure.

## 2. Déclarer avant de mesurer

- [ ] 2.1 Ajouter les quatre lignes datées en section 4 bis de `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`
  (opération, fixture, borne, raison), dans un commit à elles — exigence : `performance-budget-coverage` ›
  L'orchestration, le rapport qualité, l'évaluation de gouvernance et le portfolio ont un budget déclaré avant leur
  correction ; vérification : le commit ne modifie aucune ligne existante du document (`git diff` relu) et précède tout
  gate et toute correction.

## 3. Les gates

- [ ] 3.1 Mesure de l'état d'orchestration et du rapport qualité sur le snapshot du profil (D3), avec garde de fixture
  — exigence : `performance-budget-coverage` › Chacun des quatre budgets est mesuré sur une fixture qui tient les
  lignes avec lesquelles son coût croît ; vérification : vider une liste de la fixture fait échouer la garde avant la
  mesure, puis restaurer ; trace dans la PR.
- [ ] 3.2 Mesure de l'évaluation d'un pack au nombre maximal de règles, dans une base à elle (D3), avec garde —
  exigence : idem ; vérification : un pack d'une règle de moins fait échouer la garde, puis restaurer.
- [ ] 3.3 Mesure d'une page de références d'un portfolio au-delà de la première page, dans une base à elle (D3, D4),
  avec garde ; si une classe de gate est ajoutée, la nommer dans le `-Dtest=` de `scripts/validate-m19.ps1` **et** de
  `scripts/validate-m19.sh` — exigence : idem ; vérification :
  `./mvnw test -pl morpheus-architecture-tests -Dtest=GateReachabilityArchitectureTest` vert ; scripts `.ps1` restés en
  ASCII pur. Fichiers de gouvernance (tests d'architecture) : justification dans la PR.
- [ ] 3.4 Première exécution des quatre mesures sur le code non corrigé, Windows et Linux (lane nocturne en
  `workflow_dispatch` pour Linux), et consignation des lignes `M19_METRIC` et du verdict dans le document — exigence :
  `performance-budget-coverage` › L'orchestration, le rapport qualité, l'évaluation de gouvernance et le portfolio ont
  un budget déclaré avant leur correction ; vérification : `scripts\validate.cmd m19` (sans `-Version`) et son jumeau
  Linux, relevé `validation-output/m19/validation-summary.txt`.
- [ ] 3.5 Vérifier que les métriques existantes gardent leurs noms et leurs comptes — exigence :
  `performance-budget-coverage` › A new budget SHALL NOT move an existing one ; vérification : relevé `validate-m19`
  comparé au précédent consigné dans le document ; tas `-Xmx768m` inchangé.

## 4. Documentation durable

- [ ] 4.1 Créer `docs/developer/specifications/performance-budget-coverage.md` selon le gabarit de
  `docs/developer/specifications/README.md` (comportement, diagramme d'ordre de design.md, écarts, traçabilité), l'ajouter
  à l'index de ce README et compléter le `Purpose` de la spec principale par le renvoi à la page — vérification :
  `RepositoryDocumentationCoherenceTest` vert (aucun lien cassé).

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate budget-orchestration-quality-policy-and-portfolio-reads --strict` — vérification :
  aucun avertissement.
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → D1 à D4 ou diagramme → tâche →
  preuve) — vérification : chaque exigence a une preuve existante ou est marquée « preuve manquante ».
- [ ] 5.3 Lancer `./mvnw test -pl morpheus-architecture-tests` et `scripts\validate.cmd m19` — vérification : 0 échec,
  verdict `PASS` ou budget manqué consigné comme mesure du code non corrigé.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Les corrections `AUD-PRF-21`, `AUD-PRF-22`, `AUD-PRF-23` et `AUD-PRF-25` vérifient que le commit des quatre lignes
  est un ancêtre du leur, et consignent leur mesure après correction.
