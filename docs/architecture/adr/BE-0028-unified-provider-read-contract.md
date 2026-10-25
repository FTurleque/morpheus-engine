---
id: BE-0028
title: "Contrat de lecture provider unifié et résultats partiels explicites"
domain: backend
rules: false
---

# Contrat de lecture provider unifié et résultats partiels explicites

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

Un provider sait dire s'il reconnaît une source et ce qu'il prétend savoir en lire (sa sonde) ; il ne sait le dire
de ce qu'il a réellement produit qu'au moment de la lecture. Confondre les deux fait passer une collection vide pour
un succès, et une lecture qui a sauté du contenu pour une lecture complète.

La lecture alimente la publication d'un snapshot. Trois propriétés en dépendent :

- la publication compare, caractère par caractère, la racine de projet publiée par le lecteur à la racine sous
  laquelle le projet est enregistré (`morpheus projects add`) : un lecteur qui publie autre chose, le fichier qu'il lit
  par exemple, rend sa propre publication impossible (`project identity collision`) ;
- un diagnostic bloquant fait refuser la publication ; un refus qui ne dit pas quel fichier corriger laisse
  l'opérateur sans recours ;
- le même fichier doit porter le même locator sur toutes les plateformes, alors que le texte d'un refus doit nommer
  le fichier tel que l'opérateur le trouvera : sous Linux et macOS, `\` est un caractère légal d'un nom.

Options écartées :

- relativiser le chemin d'un échec dans le diagnostic, à partir de la racine du workspace : il faudrait retrouver un
  chemin dans un texte, que la plateforme écrit parfois seule (`NoSuchFileException` ne porte que le chemin absolu),
  sous une autre forme (séparateurs, lien résolu) ; un chemin à moitié retiré d'une phrase reste un chemin ;
- placer le point unique de la racine dans `morpheus-provider-sdk` : les providers intégrés (`openspec`, `markdown`,
  `synthetic`) ne dépendent pas du SDK, et cette arête mettrait la mécanique de découverte et de sonde des plugins sur
  leur classpath pour une seule méthode ; le contrat de lecture est déjà applicatif, et le SDK, le testkit et tout
  plugin le voient transitivement ;
- modifier la normalisation de `SourceLocator` : elle est persistée (racine de projet, sources des preuves et des
  provenances, références externes, portefeuille, candidats de conflit de composition) et comparée (égalité, ordre,
  recherche d'un projet par sa racine) ; la changer changerait des locators publiés et le nom sous lequel un projet est
  retrouvé ;
- relayer, dans le diagnostic d'une lecture en échec, la cause sans le nom du fichier : la synchronisation HTTP n'y
  passe pas, elle filtre le message de l'exception elle-même.

## Decision

1. **La sonde et la lecture sont séparées.** `SpecificationProvider.probe()` décrit la compatibilité et les capacités ;
   `SpecificationContentReader.read()` décrit ce qui a réellement été produit. Le contrat applicatif
   (`com.morpheus.application.read`) est `ProviderReadRequest`, `SpecificationContentReader`, `ProviderReadResult`,
   `ReadCategory`, `ReadCategoryStatus`, `ReadCategoryReport`, et ne contient aucun type d'un provider. Les catégories
   demandables sont celles de l'énumération `ReadCategory`.
2. **Chaque catégorie demandée reçoit un statut explicite** (`ReadCategoryStatus`) :

   | Statut | Sens |
   |---|---|
   | `READ` | contenu demandé produit |
   | `ABSENT` | catégorie supportée mais absente de la source |
   | `UNSUPPORTED` | sémantique non exposée par ce lecteur |
   | `FAILED` | lecture tentée mais en échec |
   | `PARTIAL` | contenu valide conservé mais incomplet |

   Invariant : une collection vide n'est jamais un succès ambigu. Une catégorie `ABSENT` n'est pas une erreur.
3. **Les statuts portent leurs diagnostics** :

   | Situation | Diagnostic |
   |---|---|
   | `PARTIAL` | `PARTIAL_INGESTION` |
   | `UNSUPPORTED` demandé | `OPTIONAL_CAPABILITY_UNAVAILABLE` |
   | `FAILED` | `INVALID_SOURCE` ou diagnostic de la sonde |

   Un lecteur lit ses groupes de contenu indépendamment, pour qu'un groupe en échec ne masque pas les groupes lisibles.
   Une source mal formée produit `FAILED`, sans exception sortante ; une sonde incompatible ne produit aucun contenu et
   un échec explicite.
4. **Un scénario n'est pas un critère d'acceptance.** Aucun lecteur ne convertit implicitement un `Scenario` en
   `AcceptanceCriterion`. OpenSpec ne revendique pas `READ_ACCEPTANCE_CRITERIA`, et son lecteur rend
   `ACCEPTANCE_CRITERIA` `UNSUPPORTED`.
5. **La racine de projet publiée est la racine du workspace reçue**, normalisée par un point unique :
   `ProviderProjectRoot.locator(Path)` (`com.morpheus.application.read`), qui rend
   `SourceLocator.file(workspaceRoot.toAbsolutePath().normalize().toString())`. Tout lecteur, intégré ou plugin, publie
   le résultat de ce point, appliqué à la racine reçue sans dérivation (ni `resolve`, ni `getParent()`, ni appel chaîné
   après le point, ni seconde normalisation dans l'argument). Les preuves et provenances nomment le fichier où elles
   ont été observées : seule la racine du projet est la racine reçue. Tout fichier source qui construit un
   `ProjectSpecification` a, dans son propre module, un test qui lit la racine publiée (`rootLocator()`) et épelle la
   racine attendue, ou qui passe le lecteur à `ProviderPluginContractAssertions.verifyRead`.
6. **Un échec de lecture nomme le fichier en cause, en chemin relatif à la racine du workspace.** Le diagnostic
   `INVALID_SOURCE` porte ce chemin dans `source`, et son message porte le chemin et la cause. L'attribution se fait à
   la source, là où le lecteur normalise le fichier, jamais en réécrivant un texte. Une cause dont le texte nomme un
   emplacement du serveur (`ServerLocationDisclosure`) est remplacée par son type. Une attribution ne change pas la
   catégorie d'un échec : seuls les échecs qu'un fichier peut causer par son contenu ou sa lecture sont attribués, et
   chacun garde son type (`IllegalArgumentException`, `IllegalStateException`, `UncheckedIOException` sur la même
   `IOException`) ; un défaut, un échec d'un collaborateur (magasin d'identités, persistance) ou un dépassement de
   budget passe inchangé et non attribué. Chaque surface (HTTP local et remote, CLI `sync`, `analyze-change`,
   `composition sync`) répond donc avec le même statut et le même code de sortie, attribution ou non.
7. **Un locator normalise, le texte d'un refus ne substitue rien.**
   - Un locator (`SourceLocator`) désigne une source de façon stable et comparable : `SourceLocator.file(...)` retire
     les blancs de bord, réécrit `\` en `/` et retire un `./` initial. Il n'est pas le texte d'un refus.
     `SourceLocator` garde cette normalisation.
   - Le texte d'un refus nomme le fichier par ses composants, joints par `/` sur toutes les plateformes, par un point
     unique : `WorkspaceRelativePathText`. Il ne réécrit aucun caractère ; un chemin qui porte une racine n'est pas
     relatif et reste tel que la plateforme l'écrit. Les refus de `SafeWorkspaceFileResolver`, de
     `ProviderIngestionBudget` et l'attribution d'un échec de lecture passent par ce point.
   - Le paramètre `source` de chaque méthode de `ProviderIngestionBudget.Session` est le texte d'un refus (un nom écrit
     par `WorkspaceRelativePathText`, une étiquette de groupe, ou une constante de lecteur), jamais un locator ; il ne
     sert qu'au message de `ProviderIngestionLimitException`. `Session.addEvidenceFragment(String)` attribue le
     fragment au dernier document lu par la session ; elle refuse l'appel si la session n'a lu aucun document.
8. **Le refus de publication dit pourquoi.** `ProjectSnapshotImportService` inclut dans son refus les diagnostics
   bloquants, bornés à ce que `ServerLocationDisclosure` accepte de relayer : un diagnostic qui nomme un emplacement du
   serveur est retenu, pas nettoyé ; ceux qui ne tiennent pas dans la borne sont comptés ; les deux comptes sont écrits
   dans le refus.
9. **Les diagnostics d'une synchronisation atteignent l'opérateur, bornés.** `sync` porte ses diagnostics de lecture
   par un seul record applicatif, `BoundedDiagnostics` : au plus `BoundedDiagnostics.MAX_ITEMS` diagnostics, les plus
   graves d'abord puis dans l'ordre de production, avec `truncated` et `truncationReason`
   (`DIAGNOSTIC_LIMIT_REACHED:<borne>`) quand il en manque ; `diagnosticCount` reste le total.
   - La CLI (`sync`, texte et `--json`) emploie `BoundedDiagnostics.local` : chaque diagnostic tel que produit, une
     ligne `diagnostic=<SEVERITY> <CODE> <source> {détails} <message>` par élément puis `truncationReason=`, sur
     stdout. Ce sont des données du résultat de la commande, au même titre que `diagnostics=<n>`, pas des diagnostics
     d'exécution au sens d'ADR-0059 ; le `--json` les porte dans le même objet.
   - L'HTTP (`POST /api/v1/projects/{projectId}/sync`, joignable par un appelant remote) emploie
     `BoundedDiagnostics.remote` : seules les clés de détail de `BoundedDiagnostics.REMOTE_DETAIL_KEYS` et les valeurs
     que `ServerLocationDisclosure.isSafeToRelay` accepte ; un message refusé est remplacé par le code, une `source`
     refusée est retirée. Aucune classe de `com.morpheus.api..` n'appelle `BoundedDiagnostics.local`.
   - L'OpenAPI type la réponse (`SyncResult`, `BoundedDiagnostics`, `Diagnostic`, `DiagnosticCode`,
     `DiagnosticSeverity`).
10. **Les règles de lecture propres au format OpenSpec** (masque des blocs de code, sections de delta, dialectes de
    proposal de la CLI OpenSpec, rejet changement par changement, BOM, statuts des catégories non lues) sont des
    conventions du provider OpenSpec, tenues dans [`docs/developer/OPENSPEC_PROVIDER.md`](../../developer/OPENSPEC_PROVIDER.md).
    Elles appliquent cette décision sans y déroger.

## Do's and Don'ts

### Do

- Rendre un `ReadCategoryReport` pour chaque catégorie demandée, avec un des cinq statuts et ses diagnostics.
- Rendre `PARTIAL` avec `PARTIAL_INGESTION` quand du contenu valide est conservé et que du contenu a été sauté.
- Publier la racine de projet par `ProviderProjectRoot.locator(...)` appliqué à la racine reçue, telle quelle.
- Écrire, pour tout lecteur qui construit un `ProjectSpecification`, un test qui lit `rootLocator()` et le compare à la
  racine attendue.
- Rattacher un échec de lecture au fichier qui l'a causé, à l'endroit où le lecteur le normalise, et garder le type de
  l'exception.
- Écrire le chemin d'un refus par `WorkspaceRelativePathText`, et passer ce texte (jamais un locator) aux méthodes de
  `ProviderIngestionBudget.Session`.
- Citer les preuves d'un fichier par `addEvidenceFragment(String)` dans la méthode qui vient de lire ce fichier.
- Exposer les diagnostics de `sync` par `BoundedDiagnostics.local` en CLI et `BoundedDiagnostics.remote` en HTTP.

### Don't

- Ne jamais traiter une collection vide comme un succès, ni une catégorie `ABSENT` comme une erreur.
- Ne jamais convertir un `Scenario` en `AcceptanceCriterion`.
- Ne jamais placer un type d'un provider dans le contrat applicatif de lecture.
- Ne jamais faire dépendre un provider intégré de `morpheus-provider-sdk`.
- Ne jamais publier comme racine de projet le fichier lu, un parent, un sous-répertoire ou une racine normalisée
  ailleurs que par le point unique.
- Ne jamais faire sortir de `read()` une exception pour un contenu lu : un contenu invalide est un `FAILED` attribué.
- Ne jamais attribuer à un fichier un défaut, un échec de collaborateur ou un dépassement de budget.
- Ne jamais écrire le texte d'un refus à partir d'un locator (`SourceLocator.file(...).value()`, `.toString()`), ni
  par substitution de séparateur (`replace('\\', '/')`).
- Ne jamais modifier la normalisation de `SourceLocator` sans migrer les locators persistés.
- Ne jamais servir à un appelant HTTP la projection locale des diagnostics.
- Ne jamais activer le JAR du plugin de référence depuis un test de `morpheus-architecture-tests` : le plugin y est
  sur le classpath de test, `ServiceLoader` y trouverait le service chargé par le classpath et la copie vérifiée par
  SHA-256 ne s'exécuterait jamais.

## Consequences

### Positives

- Une lecture partielle est visible : statut, diagnostic, et nom de ce qui a été sauté jusqu'à la CLI et l'HTTP.
- Un espace de travail lu par n'importe quel provider, y compris Markdown seul, se publie sous sa racine enregistrée.
- Un refus de lecture ou de publication nomme le fichier à corriger, en relatif, de la même façon sous Windows et
  sous Linux pour tout nom sans `\`.
- Le même fichier porte le même locator sur toutes les plateformes.

### Négatives

- Pour un nom de fichier qui contient `\` sous Linux ou macOS, le locator enregistré (`file:openspec/specs/a/b/spec.md`)
  et le chemin nommé par le refus (`openspec/specs/a\b/spec.md`) diffèrent. Un texte qui contient `\` est pris par
  `ServerLocationDisclosure` pour un emplacement possible du serveur : la CLI `sync` nomme ce fichier exactement avec sa
  cause, mais la synchronisation HTTP (`BoundaryFailureMessage.safe`) et `composition sync` (diagnostic
  `INVALID_SOURCE`) réduisent le refus au type de l'exception.
- `addEvidenceFragment(String)` repose sur un couplage implicite : un lecteur lit un fichier et cite ses preuves dans
  la même méthode. Rien ne vérifie cet ordre ; un lecteur qui citerait un fragment d'un autre fichier que le dernier lu
  serait refusé sous un mauvais nom.
- Une valeur légitime qui ressemble à un chemin (un titre d'exigence contenant ` /admin`) est retirée de la projection
  remote des diagnostics ; la CLI la montre.
- La projection locale des diagnostics ne borne la longueur d'une valeur que par le budget d'ingestion ; seule la
  projection remote applique `ServerLocationDisclosure.MAX_RELAYED_LENGTH`.

### Risques

- Un refus retenu par `invalidSource` (lecture de contenu) ou par `BoundaryFailureMessage.safe` (HTTP) devient un type
  nu sans dire qu'un texte a été retenu ; le refus de publication ne compte comme retenus que les diagnostics qu'il
  filtre lui-même.
- La racine d'un projet est relue comme un chemin depuis son locator (`Path.of(project.rootLocator().value())` dans
  `MorpheusProjectSyncApiService`, `MorpheusCompositionCli`, `MorpheusCli`) : sous Linux, une racine dont le chemin
  absolu contient `\` est enregistrée en `/` et relue comme un autre répertoire.
- `LocalSourceInventoryScanner#display` écrit le chemin d'un échec de scan par `replace('\\', '/')`, et `SourcePath`
  normalise de même : sous Linux, `a\b/spec.md` et `a/b/spec.md` donneraient la même clé d'inventaire.
- `Diagnostic.source` est un texte libre qui porte trois formes : le texte d'un refus (`INVALID_SOURCE` d'une
  lecture), la valeur d'un locator (avertissements de section et d'exigence sautée du lecteur de deltas OpenSpec), un
  chemin sondé (`INVALID_SOURCE` d'une sonde).
- Un diagnostic `FATAL` est compté dans `diagnosticCount` et placé en tête par `BoundedDiagnostics`, mais ne bloque pas
  la publication : `ProjectSnapshotImportService` ne refuse que sur `ERROR`.
- La garde de la racine publiée lit du texte : une valeur qui atteint un paramètre est tenue pour reçue sans être suivie
  jusqu'à l'appelant ; un nom est suivi dans son fichier seulement, sans portée ; le type du receveur de
  `.workspaceRoot()` n'est pas lu ; un paramètre de lambda non typé n'est pas un paramètre (racine refusée) ; le test
  comportemental exigé est vérifié par mentions, pas par comparaison ; une construction par réflexion n'est vue par
  aucun contrôle ; le point unique écrit qualifié ou importé statiquement est refusé.
- La garde du texte des refus juge par classe, pas par méthode : elle ne voit ni un constructeur de refus qui n'appelle
  jamais `WorkspaceRelativePathText`, ni un texte passé au budget par une variable ou un assistant.

## Compliance and Enforcement

- `morpheus-application/src/test/java/com/morpheus/application/read/ProviderReadContractTest.java` : forme de la
  requête et du résultat, recherche de rapport neutre vis-à-vis du provider.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecSpecificationContentReaderTest.java` :
  statuts de lecture sur les fixtures (dont `PARTIAL` et `ABSENT` sur `openspec-partial`, `UNSUPPORTED` pour
  `ACCEPTANCE_CRITERIA`), attribution d'un échec au fichier en relatif.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterSiblingArchitectureTest.java`
  (`builtInProvidersDoNotDependOnTheProviderSdk`) : aucun provider intégré ne dépend du SDK.
- `morpheus-application/src/test/java/com/morpheus/application/read/ProviderProjectRootTest.java` : orthographe du
  point unique.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderProjectRootArchitectureTest.java` :
  balayage textuel de l'argument entier de chaque `new ProjectSpecification(` (formes admises énumérées dans
  `ReceivedRootForm`), règle ArchUnit par unité de code, ensemble de fichiers de la règle égal à celui du balayage
  (`theRuleSeesEverySourceFileTheScanReads`), test comportemental exigé par lecteur
  (`everyClassThatConstructsAProjectSpecificationHasATestThatReadsTheRootItPublishes`), refus de passer à vide.
- `morpheus-provider-testkit/src/main/java/com/morpheus/sdk/provider/testkit/ProviderPluginContractAssertions.java`
  (`verifyRead`) : un plugin qui publie une autre racine que la racine reçue échoue au contrat publié.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecSpecificationContentReaderTest.java` :
  un échec attribué à un fichier fait échouer son groupe et nomme le fichier ; un échec qui n'est pas celui d'un
  fichier quitte la lecture inchangé (`aFailureThatIsNotTheFilesLeavesTheReadUnchangedAndFailsNoCategory`).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RefusalPathTextArchitectureTest.java` : une
  classe qui écrit un refus par `WorkspaceRelativePathText` ne l'écrit par aucune voie de locator ou de substitution ;
  aucun appel du budget ne reçoit un locator ; `OpenSpecSourceAttribution` ne dépend pas de `SourceLocator`.
- `morpheus-application/src/test/java/com/morpheus/application/files/WorkspaceRelativePathTextTest.java`,
  `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecRefusedFileNameTest.java`,
  `morpheus-application/src/test/java/com/morpheus/application/read/ProviderIngestionBudgetTest.java` : jonction par
  composants, refus et locator d'un nom à `\`, fragment de preuve attribué au dernier document lu.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProjectSnapshotImportContractTest.java` : le
  refus de publication nomme le fichier, retient en le disant un diagnostic qui nomme un emplacement du serveur, et
  tient dans la borne relayée.
- `morpheus-application/src/test/java/com/morpheus/application/ingestion/BoundedDiagnosticsTest.java`,
  `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SyncDiagnosticsContractTest.java` (dont deux
  règles ArchUnit sur le choix de projection), `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/PublishedEnumsMatchJavaEnumsTest.java`,
  `morpheus-api/src/test/java/com/morpheus/api/MorpheusApiProjectSyncIntegrationTest.java`,
  `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusCliTest.java` : borne, ordre, projections, OpenAPI et surfaces
  réelles de `sync`.
- Revue : la séparation sonde/lecture, l'interdiction d'activer le JAR de référence depuis `morpheus-architecture-tests`
  (commentaire à côté de la dépendance dans `morpheus-architecture-tests/pom.xml`) et l'ordre « lire puis citer » dont
  dépend `addEvidenceFragment(String)` ne sont tenus par aucun test.

## References

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — domaine possédé par MORPHEUS.
- [ADR-0002](ARCH-0002-openspec-reference-provider.md) — OpenSpec, provider de référence.
- [ADR-0011](BE-0011-provider-capability-negotiation.md) — négociation des capacités, que décrit la sonde.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé neutre que les lecteurs
  produisent.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptance de première classe.
- [ADR-0059](GEN-0059-stable-local-cli-contract.md) — contrat de la CLI locale (stdout et stderr).
- [ADR-0129](SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) — plugins provider.
- [Provider OpenSpec — règles de lecture](../../developer/OPENSPEC_PROVIDER.md).
- [SDK des providers](../../developer/PROVIDER_SDK.md).
- OpenAPI : [`morpheus-v1.yaml`](../../developer/reference/openapi/morpheus-v1.yaml) (`SyncResult`).
- Preuve de jalon : [VALIDATION_M2](../../project/validation/VALIDATION_M2.md).
