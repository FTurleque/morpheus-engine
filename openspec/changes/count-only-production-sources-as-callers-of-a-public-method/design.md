# Conception : ne compter que les sources de production comme appelants d'une méthode publique (AUD-QUA-28)

## Contexte

- [EXISTANT VÉRIFIÉ] `UnreferencedPublicMethodArchitectureTest` lit toutes les sources Java sous `src/` des modules
  `morpheus-*` (`javaSources`), blanchit chaînes et commentaires, compte les identifiants, et déclare « nommée ailleurs »
  une méthode publique d'un module jugé dont le nom apparaît plus souvent qu'il n'est déclaré. Les sources de test
  entrent dans le compte. Sa table `PUBLIC_AND_NAMED_NOWHERE_ELSE` est vide ; ses modules non jugés sont `NOT_JUDGED`.
- [EXISTANT VÉRIFIÉ] Réplique de cette porte réduite aux sources principales (même expression de déclaration, même
  blanchiment, `@Override` et `main` statique exclus), relevé du 17/11/2026 sur la tête de
  `docs/openspec-audit-2026-11-17` : 963 méthodes examinées, 32 relevées.

  | Groupe | Méthodes (`Fichier#méthode`) |
  |---|---|
  | Fabriques de records (8, celles de l'audit) | `ConstraintBlockingPolicy#blockWhenViolated`, `#nonBlocking` ; `ChangeLifecyclePolicy#revisionsAllowed`, `#revisionsAndCompletedReopenAllowed` ; `ChangeLifecycleFacts#permissive` ; `ExternalReference#unvalidated` ; `ProviderSelectionRequest#localOnly` ; `ReasoningContracts.Request#factsOnly` |
  | Dans un type traité par AUD-QUA-27 (6) | `RequirementDeltaPromotionService#promote`, `ExternalLinkPolicy#mayFollow`, `TemporalProjection#currentFor`, `#forEntity`, `ProjectDiscoveryResult#resolvedFromGitAncestor`, `RequirementLogicalRollbackPlan#isNoOp` |
  | Méthodes de service ou de valeur appelées par des tests seuls (13) | `QueryExportBudgetPolicy#requireBytes`, `ChangeLifecycleQualityService#assessDerivedSnapshot`, `#assessExplicitActive`, `ChangeAnalysisService#analyzeSnapshot`, `SavedViewService#listIncludingArchived`, `MorpheusQueryApiService#executeProject`, `BusinessContentQueryService#snapshotSpecification`, `CompactQueryViewService#requirementSearch`, `StartupOwnership#keepAction`, `WatchSignalPolicy#affectedPaths`, `Constraint#hasExplicitSemantics`, `PortfolioDefinition#rename`, `ProviderIngestionBudget#requireUtf8Document` |
  | Points d'observation lus par des tests ou des portes (5) | `MorpheusHttpRouteTable#declaredRoutes` (lue par `PublicHttpRouteConvergenceTest`), `ProductMetadata#developmentRuntime` (lue par `GovernanceSurfaceCoherenceTest`), `BoundedStdioClientTransport#setStdErrorHandler`, `MorpheusMcpServer#deniedWriteCapability`, `#unconfiguredTechnicalContext` (fabriques de serveurs de test) |

  L'audit compte 21 méthodes et 8 fabriques ; l'écart tient à la méthode (comptage par nom, qui ne voit pas une méthode
  morte homonyme d'une vivante, et ne retient pas les mêmes modules) et aux commits postérieurs à `a04f32bd`. La liste
  qui fait foi à l'application est le refus de la porte modifiée, pas ce tableau.
- [EXISTANT VÉRIFIÉ] `QueryReportFormatter` borne l'export par `new Utf8BoundedTextBuilder(QueryBudgets.MAX_EXPORT_BYTES)`
  (lignes 41 et 136) ; `QueryExportBudgetPolicy.requireBytes` n'est appelé que par `QueryExportContractTest:90,94`.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `dead-code-refusal` | A public method of a product module SHALL be referenced or excused by name | §Décisions D1, D2, D3 |

## Composants et frontières

- `morpheus-architecture-tests` : `UnreferencedPublicMethodArchitectureTest` (porte), `QueryExportContractTest` (m24).
- Modules du produit : retrait des méthodes, réécriture des tests sur le constructeur canonique.
- Aucune règle de couche ne bouge.

## Modèle de domaine et invariants

Les fabriques retirées n'ajoutent aucun invariant : la validation reste dans le constructeur compact de chaque record
(`record-construction` › A record SHALL offer one public constructor). Un test qui employait `permissive()` construit la
même valeur par le constructeur canonique ; `record-construction` › A default SHALL be obtained through a factory that
names it reste tenue pour les fabriques que la production appelle.

## Traitements et interactions

Sans objet : aucune interaction d'exécution ne change ; seule la porte change de population de références.

## États et transitions

Sans objet.

## Contrats d'interface

Sans objet : aucune méthode relevée n'est servie par une surface publique (`contracts/public-surfaces.tsv` inchangé).

## Persistance et intégrité

Sans objet.

## Transactions, concurrence et idempotence

Sans objet.

## Sécurité et isolation

Sans objet.

## Gestion des erreurs et reprise

Sans objet.

## Observabilité

Le refus de la porte nomme `Fichier#méthode` et dit qu'un appel de test ne compte pas.

## Performance et stratégie de mesure

Sans objet.

## Compatibilité, migration et retour arrière

Aucune donnée ni surface. Retour arrière : `git revert`.

## Décisions

### D1 : une seule porte, plus stricte, plutôt qu'une seconde passe
- **Nature** : [PROPOSITION]
- **Exigences servies** : `dead-code-refusal` › A public method of a product module SHALL be referenced or excused by
  name.
- **Choix** : la porte existante compte les identifiants des seules sources principales ; sa table d'exceptions reçoit
  les méthodes publiques sans appelant de production qui doivent le rester, chacune avec sa raison.
- **Alternatives écartées** : une seconde passe « production seule » limitée aux méthodes statiques de records, à côté
  de la passe actuelle (suggestion de l'audit) — deux tables d'exceptions pour la même question, et les 13 méthodes de
  service resteraient hors règle alors que le critère de sortie du sprint 8 vise toute méthode publique ; une règle
  ArchUnit sur le bytecode — le module d'architecture n'importe ni les tests des autres modules ni le testkit, et la
  première forme essayée a été abandonnée pour cette raison (`docs/developer/specifications/dead-code-refusal.md`).
- **Compromis** : la porte garde sa limite connue (une méthode morte homonyme d'une vivante n'est pas vue), déclarée
  dans sa Javadoc.
- **ADR** : non concerné.

### D2 : sort de chaque méthode
- **Nature** : [PROPOSITION]
- **Exigences servies** : idem.
- **Choix** :
  - fabriques de records : retirer ; tests réécrits sur le constructeur canonique (règle de `.claude/rules/code-style.md`,
    scénario « No entry exists for tests alone » de `record-construction`) ;
  - `requireBytes` : réécrire `QueryExportContractTest` sur `QueryReportFormatter` (export au-delà de
    `QueryBudgets.MAX_EXPORT_BYTES` refusé, export à la borne admis), puis retirer la méthode, et la classe s'il ne lui
    reste rien ;
  - autres méthodes de service ou de valeur : retirer, ou câbler quand un appelant de production manque à tort
    (à instruire méthode par méthode à l'application, la PR dit lequel) ;
  - points d'observation : `declaredRoutes` et `developmentRuntime` listés avec leur raison (lus par une porte
    d'architecture) ou remplacés par une lecture que la production emploie ; les fabriques de serveurs de test de
    `MorpheusMcpServer` et `setStdErrorHandler` déplacées vers les sources de test si la visibilité le permet, sinon
    listées avec leur raison ;
  - méthodes des types d'AUD-QUA-27 : sans objet ici, retirées avec leur type ou listées par ce change-là.
- **Alternatives écartées** : lister toutes les méthodes relevées — la table deviendrait la règle.
- **ADR** : non concerné.

### D3 : ordre
- **Nature** : [DÉCISION] (ordre interne du sprint 8 de l'audit)
- **Choix** : appliquer après `give-every-public-type-of-the-product-a-production-caller` ; resserrer la porte en dernier,
  une fois les méthodes retirées, pour que chaque commit reste vert.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| Le scénario « No entry exists for tests alone » n'est tenu par aucun test | `openspec/specs/record-construction/spec.md` | huit fabriques de tests seuls | tenu par la porte modifiée ; citer la preuve dans `docs/developer/specifications/record-construction.md` |
| Un contrat M24 prouve une borne non employée | `QueryExportContractTest:90` | `QueryReportFormatter:41` | D2 |
| Compte de l'audit | constat `AUD-QUA-28` (29) | réplique du 17/11/2026 (32, dont 6 d'AUD-QUA-27) | la liste de la porte fait foi |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| A public method of a product module SHALL be referenced or excused by name | A public method called only by tests fails the build | `UnreferencedPublicMethodArchitectureTest#aMethodCalledOnlyByATestIsReported` | à écrire |
| idem | A static factory of a record called only by tests fails the build | `#aRecordFactoryCalledOnlyByATestIsReported` | à écrire |
| idem | A new uncalled public method fails the build | `#aMethodNamedOnlyByItsOwnDeclarationIsReportedAndACallerClearsIt` | existant |
| idem | An excuse without a reason, or for a method that is called, fails | `#anExcuseWithoutAReasonOrForACalledMethodFails` (liste non vide plantée) | à écrire |
| idem | The sixteen methods are gone | `#everyPublicMethodOfAProductModuleIsNamedSomewhereElseOrSaysWhy` (renommé si son nom ment) | existant |

## Documentation durable et diagrammes à mettre à jour

- `docs/developer/specifications/dead-code-refusal.md` : R3 et son diagramme (« nom présent dans une autre source
  principale »), écarts (la preuve manquante de la liste non vide disparaît), traçabilité.
- `docs/developer/specifications/record-construction.md` : preuve du scénario « No entry exists for tests alone ».

## Questions ouvertes

Aucune.
