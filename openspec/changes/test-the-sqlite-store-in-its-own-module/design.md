# Conception : tester le store SQLite dans son propre module, puis relever son seuil (AUD-TST-31)

Conception allégée : des tests ajoutés dans le module qui porte les classes, puis une requalification par la procédure
existante. Aucun comportement ne change.

## Contexte

[EXISTANT VÉRIFIÉ] Rapport JaCoCo propre de `morpheus-store-sqlite` présent dans l'arbre le 17/11/2026
(`morpheus-store-sqlite/target/site/jacoco/jacoco.xml`, 3 228 lignes couvertes, 750 manquées, 859 / 358 branches :
le total de l'audit à `a04f32bd`). Fichiers qui portent le plus de lignes manquées, lignes puis branches manquées :

| Fichier | Lignes manquées / total | Branches manquées / total |
|---|---|---|
| `adapter/SqliteExternalReferenceStore.java` | 173 / 189 | 33 / 36 |
| `adapter/SqliteVersionedRequirementStore.java` | 92 / 338 | 38 / 96 |
| `adapter/SqliteChangeLifecycleMutationStore.java` | 76 / 246 | 30 / 60 |
| `adapter/SqliteSavedViewStore.java` | 70 / 212 | 34 / 64 |
| `adapter/SqliteCompositionStateStore.java` | 70 / 137 | 20 / 34 |
| `adapter/SqliteSpecificationKnowledgeStore.java` | 41 / 184 | 21 / 62 |
| `adapter/SqlitePolicyPackStore.java` | 39 / 402 | 33 / 117 |
| `runtime/SqliteServerMaintenance.java` | 25 / 201 | 24 / 78 |

Ce tableau est une mesure datée, à relire sur le rapport du moment de l'implémentation (tâche 1.1).

[EXISTANT VÉRIFIÉ] `SqliteSnapshotBusinessContentWriter` : 0 ligne manquée sur 186, 1 branche manquée sur 32 ; ses
lignes sont exécutées par les tests du module qui passent par `SqliteSnapshotBusinessContentStore`
(`SqliteSnapshotBusinessContentProjectionTest`, `SqliteSnapshotBusinessContentReadCostTest`,
`SqliteSnapshotOrderedValuePersistenceTest`…). Le `grep -w` de l'audit mesurait qu'aucun test ne le **nomme**, pas
qu'aucun ne le **couvre**.

[EXISTANT VÉRIFIÉ] Règles qui bornent la suite :
- un test de store sous `morpheus-store-sqlite` emploie une vraie base SQLite temporaire, jamais un mock
  (`.claude/rules/testing.md`) ; un refus s'asserte par `assertThrows` et son message (`RefusalTestEvidenceArchitectureTest`) ;
- une parité `store-memory` / `store-sqlite` changée se prouve par un `*PersistenceParityTest` (module
  d'architecture), dont l'exécution ne crédite pas l'échelle par module ;
- la fenêtre d'un seuil de module : au moins trois, au plus neuf dispersions retenues sous la plus basse mesure
  conservée (`per-module-coverage-floor`, `CoverageQualificationEvidenceTest#everyModuleFloorStaysInsideItsQualifiedWindow`) ;
- les deux échelles se requalifient à une seule tête, les seuils de module ensuite (`GEN-0167`, `.claude/rules/testing.md`) ;
- le crédit emprunté a un maximum qui ne fait que baisser et une liste exacte de classes entièrement empruntées
  (`coverage-attribution-gap`, `BorrowedCoverageGateTest`).

Motivation : voir `proposal.md`, section Why.

## Exigences couvertes

Sans objet : aucune exigence ne change (`skip_specs: true`). Les exigences appliquées sans changement :
`per-module-coverage-floor` › Le seuil d'un module reste dans sa fenêtre qualifiée ; `coverage-attribution-gap` › The
total borrowed credit SHALL NOT exceed a recorded maximum that is only lowered.

## Composants et frontières

Sans objet : aucune frontière ne bouge. Les tests vivent dans `com.morpheus.store.sqlite.adapter` et
`com.morpheus.store.sqlite.runtime` (sources de test), paquets des classes testées.

## Modèle de domaine et invariants

Sans objet.

## Traitements et interactions

Sans objet : aucun traitement ne change.

## États et transitions

Sans objet.

## Contrats d'interface

Sans objet.

## Persistance et intégrité

Sans objet : aucun schéma, aucune migration. Les tests ouvrent leur base par `SqliteConnectionScope.open(chemin,
recorder)` ou par les fabriques de test du module, jamais par une ouverture JDBC directe.

## Transactions, concurrence et idempotence

Les chemins de conflit des stores à CAS (révision attendue périmée, écrivain concurrent) font partie des cas visés ; un
test concurrent suit `SqliteConcurrencyHardeningTest` et reste déterministe.

## Sécurité et isolation

Sans objet.

## Gestion des erreurs et reprise

Les chemins d'échec testés assertent l'exception nommée et son message, et l'état laissé (rien de persisté, révision
inchangée).

## Observabilité

Sans objet.

## Performance et stratégie de mesure

Sans objet : la grandeur suivie est la couverture propre et le crédit emprunté, mesurés par les gates existants.

## Compatibilité, migration et retour arrière

Sans objet : des tests ajoutés. Une requalification se revient en restaurant l'ensemble de preuves précédent.

## Décisions

### D1 : l'ordre des tests suit le rapport propre, pas le nom des classes
- **Nature** : [DÉCISION]
- **Choix** : lire le rapport propre du module après un `clean verify`, écrire les tests par lignes manquées
  décroissantes, `SqliteExternalReferenceStore` en premier ; garder les branches comme second critère, la marge du
  seuil de branches étant la plus serrée.
- **Alternatives écartées** : commencer par `SqliteSnapshotBusinessContentWriter` comme l'audit le recommande — il est
  déjà couvert, le gain serait nul.
- **Compromis** : aucun.
- **ADR** : non concerné.

### D2 : un test par comportement du port, pas par ligne
- **Nature** : [DÉCISION]
- **Choix** : chaque test exerce une méthode du port de l'application que le store implémente (résolution, historique,
  conflit, refus d'un argument), avec son effet persisté relu ; un comportement que le store mémoire porte aussi reste
  tenu, en plus, par les `*PersistenceParityTest` existants du module d'architecture, sans cas ajouté là.
- **Alternatives écartées** : appeler des méthodes privées par réflexion — interdit (`ReflectionAndClassLoadingBoundaryArchitectureTest`)
  et sans valeur de régression.
- **Compromis** : plus de tests pour une même couverture.
- **ADR** : non concerné.

### D3 : relever le seuil après une requalification exact-head, avec les valeurs que nomme le gate
- **Nature** : [PROPOSITION]
- **Choix** : une fois les tests fusionnés, mesurer à une même tête sur les runners Linux et Windows (deux runs), garder
  les résumés dans `config/coverage-qualification/` à cette tête pour les deux échelles, puis poser les clés
  `morpheus-store-sqlite.*` au plus haut millième que la fenêtre admet — le refus de
  `CoverageQualificationEvidenceTest` nomme le plus petit seuil admis, aucune valeur n'est calculée à la main — et
  baisser, si la mesure le permet, le maximum du crédit emprunté. Procédure : `.claude/skills/coverage-ratchet`.
- **Alternatives écartées** : relever la clé sans nouvelle mesure, dans la fenêtre de la preuve du 07/11 — n'atteint pas
  le critère de sortie du sprint (« un seuil propre relevé au-dessus de sa mesure du 17/11 ») ; requalifier dans un
  changement séparé — possible, c'est la question ouverte de la proposition.
- **Compromis** : la requalification touche aussi les plafonds des deux échelles, à la même tête.
- **ADR** : non concerné (`GEN-0167` décide la procédure).

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| L'audit désigne `SqliteSnapshotBusinessContentWriter` comme priorité ; il est couvert à 100 % des lignes par le module | `docs/project/audits/2026-11-17/findings.md`, `AUD-TST-31` | rapport JaCoCo propre du module | D1 : commencer par `SqliteExternalReferenceStore` |
| Pourcentages de l'audit (81,15 % / 70,58 %) et de la preuve Linux (81,17 % / 70,67 %) | audit (mesure Windows et build local) | `Linux-42306627340.txt` | aucun : deux plateformes ; la plus basse gouverne |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| couverture propre relevée | après les tests | rapport propre du module, `CoverageQualityGateTest#everyModuleOfThePopulationHoldsItsOwnFloor` | à mesurer |
| seuil dans sa fenêtre | après requalification | `CoverageQualificationEvidenceTest` | existant |
| crédit emprunté sous son maximum | après les tests | `BorrowedCoverageGateTest` | existant |
| refus asserté par message | chaque test d'échec | `RefusalTestEvidenceArchitectureTest` | existant |

## Documentation durable et diagrammes à mettre à jour

À la requalification seulement : les destinations que `RepositoryDocumentationCoherenceTest`,
`ProductionIntegrityContractTest` et `GovernanceSurfaceCoherenceTest` énumèrent en échouant (`.claude/rules/meta.md`).
Les seuils de module ne sont recopiés nulle part (`.claude/rules/testing.md`).

## Questions ouvertes

- Requalification dans ce changement ou dans un changement séparé (proposition, Questions ouvertes ; D3 propose ce
  changement). Elle n'affecte que le dernier groupe de tâches.
