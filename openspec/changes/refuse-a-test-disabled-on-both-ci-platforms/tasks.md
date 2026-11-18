# Tâches : refuser un test désactivé sur les deux plateformes de CI

## 1. Décision du mainteneur

- [ ] 1.1 Faire trancher Q1 de design.md (publier à part, retrancher, statu quo) — vérification : décision consignée
  dans la PR ; si (b), suspendre le groupe 3 jusqu'à l'ADR consolidé qui remplace GEN-0089 et adapter D4 et l'exigence
  `skipped-test-justification` › A presence validator SHALL publish the number of skipped executions avant toute
  implémentation

## 2. Règle sur les conditions d'OS

- [ ] 2.1 Écrire `OperatingSystemConditionLeavesACiPlatformTest` (à écrire) : cas fabriqués sous `@TempDir` (macOS
  seul, Linux et Windows désactivés, classe Windows + méthode Linux, condition non littérale) et méthode sur le dépôt
  réel (D1, D2 ; fichier de gouvernance, justification de PR) — exigence : `skipped-test-justification` › An
  operating-system condition MUST leave its test enabled on Linux or on Windows ; vérification : cas fabriqués refusés
  avec classe, méthode et conditions nommées ; dépôt réel accepté
- [ ] 2.2 Planter `@EnabledOnOs(OS.MAC)` sur un test réel, constater le refus, revenir en arrière — vérification :
  trace consignée dans la PR (section « Intention de chaque assertion textuelle »)
- [ ] 2.3 Compléter la Javadoc de `SkippedTestReasonGateTest` par le renvoi à la règle sœur (D3) — exigence :
  `skipped-test-justification` › A test of the reactor SHALL be skipped only by an operating-system condition ;
  vérification : test existant `anOperatingSystemConditionIsAcceptedAndEveryOtherSkipIsNamedWithItsReason` vert

## 3. Publication des sautés

- [ ] 3.1 Écrire les cas « relevé avec sautés » et « relevé sans sauté » de `scripts/tests/test_validator_verdicts.py`
  pour `validate-m21` et `validate-d2`, `.sh` et `.ps1` — exigence : `skipped-test-justification` › A presence
  validator SHALL publish the number of skipped executions ; vérification : rouges pour `validate-m21` avant 3.2
- [ ] 3.2 Ajouter `skipped=<n>` à la ligne de verdict et au relevé de `validate-m21.*` et au relevé de `validate-d2.*`
  (D4) — vérification : tests de 3.1 verts

## 4. Documentation durable

- [ ] 4.1 Créer `docs/developer/specifications/skipped-test-justification.md` depuis le gabarit de
  `docs/developer/specifications/README.md`, avec le diagramme de design.md, et l'ajouter à l'index — vérification :
  `RepositoryDocumentationCoherenceTest` vert

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate refuse-a-test-disabled-on-both-ci-platforms --strict` — vérification : aucun
  avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a une preuve
  existante ou « preuve manquante »
- [ ] 5.3 Lancer `./mvnw clean verify` (le gate des sautés exige le réacteur entier) puis `python -m pytest scripts/tests`
  sous Linux et sous Windows — vérification : verts, et les relevés M21 des deux plateformes portent `skipped=`

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
