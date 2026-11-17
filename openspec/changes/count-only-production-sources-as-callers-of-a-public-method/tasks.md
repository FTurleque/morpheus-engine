# Tâches : ne compter que les sources de production comme appelants d'une méthode publique (AUD-QUA-28)

## 1. Préalable

- [ ] 1.1 Vérifier que `give-every-public-type-of-the-product-a-production-caller` (AUD-QUA-27) est appliqué — vérification :
  ses tâches du groupe 3 cochées sur la branche

## 2. Reproduction

- [ ] 2.1 Écrire les tests de forme `UnreferencedPublicMethodArchitectureTest#aMethodCalledOnlyByATestIsReported`,
  `#aRecordFactoryCalledOnlyByATestIsReported` et `#anExcuseWithoutAReasonOrForACalledMethodFails` (à écrire) sur des
  sources plantées — exigence : `dead-code-refusal` › A public method of a product module SHALL be referenced or excused
  by name ; vérification : rouges avant 4.1
- [ ] 2.2 Réécrire `QueryExportContractTest` pour qu'il prouve la borne d'export sur `QueryReportFormatter` (au-delà de
  `QueryBudgets.MAX_EXPORT_BYTES` refusé, à la borne admis) — exigence : idem (D2) ; vérification : le test casse si la
  borne de `QueryReportFormatter` est retirée (violation plantée, trace dans la PR)

## 3. Retraits

- [ ] 3.1 Retirer les huit fabriques de records et réécrire leurs tests sur le constructeur canonique — exigence :
  `dead-code-refusal` › A public method of a product module SHALL be referenced or excused by name, scénario « A static
  factory of a record called only by tests fails the build » ; vérification : tests des modules concernés verts
- [ ] 3.2 Retirer `requireBytes` (et `QueryExportBudgetPolicy` s'il ne lui reste rien) — vérification : `QueryExportContractTest` vert
- [ ] 3.3 Traiter chaque méthode de service ou de valeur du tableau de design.md (retirer ou câbler, la PR dit lequel et
  pourquoi) — vérification : tests des modules concernés verts
- [ ] 3.4 Traiter les points d'observation (déplacer vers les sources de test, ou lister avec leur raison) — vérification :
  chaque entrée de la table porte une raison non vide

## 4. Porte

- [ ] 4.1 Restreindre le compte des références de `UnreferencedPublicMethodArchitectureTest` aux sources principales,
  et mettre sa Javadoc et son message de refus au présent de la règle — exigence : `dead-code-refusal` › A public method
  of a product module SHALL be referenced or excused by name ; vérification :
  `./mvnw test -pl morpheus-architecture-tests -Dtest=UnreferencedPublicMethodArchitectureTest` vert, tests de 2.1 verts ;
  justification de PR (test d'architecture)
- [ ] 4.2 Mettre à jour `docs/developer/specifications/dead-code-refusal.md` et
  `docs/developer/specifications/record-construction.md` (exigence, diagramme, écarts, traçabilité) — vérification :
  `RepositoryDocumentationCoherenceTest` vert

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate count-only-production-sources-as-callers-of-a-public-method --strict` —
  vérification : aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → conception → tâche → preuve) —
  vérification : chaque scénario a une preuve
- [ ] 5.3 Lancer `./mvnw clean verify` — vérification : vert ; comptes d'exécutions déclarées au-dessus de
  `testsMinimum` et `architectureTestsMinimum` de `config/m21-quality-ratchets.properties`, sans baisse de clé

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
