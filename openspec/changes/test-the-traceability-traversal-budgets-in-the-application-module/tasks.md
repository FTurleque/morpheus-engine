# Tâches : tester les budgets de nœuds et de liens de la traversée dans le module application (AUD-TST-32)

## 1. Test du module

- [ ] 1.1 Écrire `TraceabilityTraversalServiceTest` (à écrire) dans
  `morpheus-application/src/test/java/com/morpheus/application/traceability/`, avec le double de `TraceabilityStore`
  de design.md D1 — exigence : aucune modifiée (règle de déterminisme de `.claude/rules/code-style.md`, constat
  `AUD-TST-32`) ; vérification : le test compile et passe sur le code actuel.
- [ ] 1.2 Cas « budget de nœuds » : une étoile au-delà de `MAX_NODES`, raison `NODE_BUDGET_REACHED:` suivie de
  `MAX_NODES`, `MAX_NODES` nœuds rendus, deux exécutions identiques (D2) — vérification : changer à la main la garde
  de la ligne 130 (`>=` en `>`) fait échouer le cas, puis revenir ; trace dans la PR.
- [ ] 1.3 Cas « budget de liens » : plus de `MAX_LINKS` liens, raison `LINK_BUDGET_REACHED:` suivie de `MAX_LINKS`,
  `MAX_LINKS` liens rendus (D2) — vérification : retirer à la main la garde de la ligne 125 fait échouer le cas, puis
  revenir ; trace dans la PR.
- [ ] 1.4 Cas « sous les budgets » : aucune raison de troncature — vérification : le cas passe.

## 2. Vérification d'ensemble

- [ ] 2.1 Lancer `openspec validate test-the-traceability-traversal-budgets-in-the-application-module --strict` —
  vérification : aucun avertissement.
- [ ] 2.2 Matrice de traçabilité de design.md (Stratégie de vérification) complétée par les noms de méthodes écrits —
  vérification : chaque ligne « à écrire » nomme son test.
- [ ] 2.3 Lancer `./mvnw test -pl morpheus-application` puis
  `./mvnw test -pl morpheus-architecture-tests -Dtest=TraceabilityTruncationContractTest,RefusalTestEvidenceArchitectureTest`
  — vérification : 0 échec.
- [ ] 2.4 Après un `./mvnw clean verify` complet, lire le rapport propre de `morpheus-application` : les lignes 125-131
  de `TraceabilityTraversalService` sont couvertes ; si `BorrowedCoverageGateTest` constate une baisse du crédit
  emprunté, ne rien relever ni baisser dans ce changement — vérification : rapport relu, aucun fichier de
  `config/*ratchets*.properties` modifié.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Une baisse du maximum du crédit emprunté ou un relèvement du seuil de `morpheus-application` se décide ensuite, par
  la procédure `.claude/skills/coverage-ratchet`.
