# Tâches : ordonner le refus d'un plan d'application de delta (AUD-QUA-38)

## 1. Préalable

- [ ] 1.1 Vérifier que la décision d'AUD-QUA-27 (`give-every-public-type-of-the-product-a-production-caller`, tâche 1.1)
  garde `RequirementDeltaApplicationService` — vérification : décision écrite dans la PR ; sinon, retirer ce change sans
  l'appliquer (design.md D2)

## 2. Reproduction et correctif

- [ ] 2.1 Écrire `RequirementDeltaApplicationServiceTest#aMismatchedPlanListsMissingAndExtraIdentitiesInOrder` et
  `#theReusedOccurrenceReportedIsTheSmallest` (à écrire), qui comparent le message entier — exigence : aucune
  (`skip_specs: true`), règle de déterminisme de `.claude/rules/code-style.md` ; vérification : au moins le premier est
  rouge avant 2.2 (assez d'identités pour qu'un `HashSet` ne rende pas l'ordre trié)
- [ ] 2.2 Trier `missing`, `extra` et les candidats réutilisés (design.md D1) — vérification : tests de 2.1 verts,
  `RequirementDeltaApplicationServiceTest` entier vert

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate order-the-refusal-of-a-requirement-delta-application-plan --strict` — vérification :
  aucun avertissement
- [ ] 3.2 Matrice de traçabilité : sans page durable (aucune capacité) ; la preuve est la paire de tests de 2.1 —
  vérification : tests nommés dans la PR
- [ ] 3.3 Lancer `./mvnw test -pl morpheus-application` — vérification : vert

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite, ou le retirer si la classe est supprimée.
