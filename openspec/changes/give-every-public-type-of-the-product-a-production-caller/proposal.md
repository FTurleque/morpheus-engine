# Proposition : donner un appelant de production à tout type public du produit (AUD-QUA-27)

## Why

Dix classes publiques de `morpheus-application` ne sont nommées par aucune autre source principale du réacteur :
`RequirementDeltaApplicationService`, `RequirementDeltaPromotionService`, `ProjectDiscoveryService`,
`RequirementLogicalRollbackService`, `LocalProjectRegistry`, `ExternalLinkPolicy`, `LocalSourceWatcher`,
`CurrentRequirementQueryService`, `TemporalProjection` et `ExternalTraceabilityLinkFactory`. Elles sont testées, comptées
dans la couverture, et ne s'exécutent dans aucun processus livré : ni la CLI, ni le serveur HTTP, ni le serveur MCP ne
les construisent. La documentation développeur présente pourtant l'une d'elles comme appliquée
(`docs/developer/REMOTE_SERVER_PLATFORM.md`, ligne 195 : « `LocalSourceWatcher` applique désormais les mêmes limites… »).

Aucune règle ne le voit : `UnreferencedPublicMethodArchitectureTest` juge des méthodes, pas des types, et compte un appel
de test comme une référence.

Preuve : constat `AUD-QUA-27` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd`. Revérifié le 17/11/2026 sur la tête de la branche : `git grep -lw <Nom> -- 'morpheus-*/src/main/*.java'`
ne rend que le fichier propre de chacune des dix classes, et leurs longueurs font bien 1 212 lignes. La revérification
trouve en plus une cascade (design.md, Contexte) et cinq ADR de plus que l'audit qui nomment ces classes dans leur
section `Decision`.

Ce change ouvre le sprint 8 de l'audit : il précède `count-only-production-sources-as-callers-of-a-public-method`
(AUD-QUA-28) et `order-the-refusal-of-a-requirement-delta-application-plan` (AUD-QUA-38).

## What Changes

- **Décision du mainteneur, classe par classe** : câbler (avec sa ligne de `contracts/public-surfaces.tsv` si une
  surface publique apparaît) ou supprimer avec ses tests. Ce change ne tranche pas ; design.md porte les options et une
  recommandation par classe.
- Ajout d'une porte d'architecture : tout type public de premier niveau d'un module du produit est nommé par une autre
  source principale, déclaré par un fichier d'enregistrement de service d'un module de production, ou listé avec sa
  raison ; le build échoue sinon. Les modules d'outillage de vérification et le SDK publié sont hors règle.
- Pour chaque classe supprimée : retrait de ses tests et de ses collaborateurs devenus morts, des listes de tests
  d'architecture qui la nomment (`ApplicationTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES`,
  `SilentCatchArchitectureTest`), baisse de la clé correspondante de `config/debt-counter-ratchets.properties`,
  retrait de `ExternalLinkPolicyTest` des `-Dtest` de `scripts/validate-m19.ps1` et `.sh`, correction des pages
  `docs/developer/REMOTE_SERVER_PLATFORM.md` et `docs/developer/OPERABILITY.md`, et traitement des ADR qui la nomment.
- Pour chaque classe câblée : la surface qui la construit, ses tests d'intégration, et le manifeste et l'OpenAPI si la
  surface est publique.

## Capabilities

### New Capabilities

### Modified Capabilities
- `dead-code-refusal` : ajoute l'exigence qu'un type public d'un module du produit ait un appelant de production ou une
  raison écrite.

## Périmètre et exclusions

**Inclus :** les dix classes et leurs collaborateurs qui ne servent qu'à elles (liste mesurée dans design.md), la
nouvelle porte d'architecture, les listes, clés, validateurs, pages et ADR qui nomment ces classes.

**Exclus :** les méthodes publiques sans appelant de production hors de ces classes, traitées par
`count-only-production-sources-as-callers-of-a-public-method` (AUD-QUA-28) ; l'ordre du refus de
`RequirementDeltaApplicationService`, traité par `order-the-refusal-of-a-requirement-delta-application-plan`
(AUD-QUA-38), sans objet si la classe est supprimée ; les modules d'outillage de vérification
(`morpheus-store-memory`, `morpheus-provider-synthetic`, `morpheus-provider-testkit`, `morpheus-provider-reference`),
qui n'ont pas d'appelant de production par construction.

## Acteurs et surfaces concernés

Le mainteneur, qui décide du sort de chaque classe ; l'auteur d'une classe de production, que la nouvelle porte refuse
sans appelant ; la CI, qui porte le verdict. Aucune surface CLI, MCP ou HTTP ne change si toutes les classes sont
supprimées.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Les dix classes n'ont aucun appelant de production | existant vérifié | `git grep -lw` sur `morpheus-*/src/main`, 17/11/2026 ; aucun `META-INF/services` ne les nomme |
| Leur suppression rend morts treize autres types de l'application et `SpecificationSource` du domaine | existant vérifié | rejeu itératif du scan des noms, design.md, Contexte |
| La documentation présente `LocalSourceWatcher` comme appliqué | existant vérifié | `docs/developer/REMOTE_SERVER_PLATFORM.md:195` |
| Sept ADR en vigueur nomment une de ces classes dans leur section `Decision` | existant vérifié | DATA-0125, ARCH-0150, BE-0020, BE-0035, ARCH-0136, DATA-0141, DATA-0142 |
| Le sort de chaque classe | question ouverte | décision du mainteneur, design.md D1 |
| La porte nouvelle compte les noms dans les sources principales seules | proposition à valider | design.md D2 |

## Impact

- Modules : `morpheus-application` (paquets `com.morpheus.application.sync`, `com.morpheus.application.delta`,
  `com.morpheus.application.discovery`, `com.morpheus.application.history`, `com.morpheus.application.project`,
  `com.morpheus.application.security`, `com.morpheus.application.temporal`, `com.morpheus.application.traceability`),
  `morpheus-domain` (`com.morpheus.domain.source`), `morpheus-architecture-tests`.
- Fichiers de gouvernance : un test d'architecture nouveau, `ApplicationTechnologyBoundaryArchitectureTest`,
  `SilentCatchArchitectureTest`, `config/debt-counter-ratchets.properties` ; justification de PR obligatoire.
- ADR : DATA-0125 (point 9) et BE-0020 exigent un ADR consolidé qui les remplace si leurs classes sont supprimées ;
  BE-0035 décrit en entier deux services sans appelant ; ARCH-0150, ARCH-0136, DATA-0141 et DATA-0142 nomment une classe
  dans leur décision. ADR à proposer, selon la décision.
- Documentation : `docs/developer/REMOTE_SERVER_PLATFORM.md`, `docs/developer/OPERABILITY.md`,
  `docs/developer/specifications/dead-code-refusal.md`, `docs/developer/specifications/workspace-boundary-scan-cost.md`.
- Validateurs : `scripts/validate-m19.ps1` et `scripts/validate-m19.sh`.
- Couverture : retirer des lignes testées change numérateur et dénominateur des deux échelles ; aucun ratchet ne baisse
  (relire `config/m21-quality-ratchets.properties` et `config/m21-per-module-coverage-floors.properties` au moment de
  l'application).

## Questions ouvertes

- **Pour le mainteneur** : le sort de chacune des dix classes (câbler ou supprimer), design.md D1.
- **Pour le mainteneur** : le traitement des ADR qui nomment une classe supprimée (remplacement consolidé, archivage ou
  clarification), design.md D4.
