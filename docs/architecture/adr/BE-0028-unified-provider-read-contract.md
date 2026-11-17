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

Fonctionnement détaillé du contrat que la décision fixe, point par point :

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
     sert qu'au message de `ProviderIngestionLimitException`. `Session.addEvidenceLines(lines, first, last)` attribue
     les lignes citées au dernier document lu par la session ; elle refuse l'appel si la session n'a lu aucun document.
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

`ProviderIngestionLimitException` hérite d'`IllegalArgumentException` : une clause large la rendrait comme une source
mal formée. Dans `morpheus-architecture-tests`, le plugin de référence est sur le classpath de test : `ServiceLoader`
y trouverait le service chargé par le classpath, et la copie vérifiée par SHA-256 ne s'exécuterait jamais.

## Decision

1. `SpecificationProvider.probe()` dit les capacités, `SpecificationContentReader.read()` ce qui est produit ; le
   contrat `com.morpheus.application.read` (`ProviderReadRequest`, `ProviderReadResult`, `ReadCategory`,
   `ReadCategoryStatus`, `ReadCategoryReport`) ne porte aucun type de provider.
2. Statut de toute catégorie demandée : `READ`, `ABSENT` (pas une erreur), `UNSUPPORTED`
   (`OPTIONAL_CAPABILITY_UNAVAILABLE`), `FAILED` (`INVALID_SOURCE` ou diagnostic de sonde), `PARTIAL`
   (`PARTIAL_INGESTION`) ; une collection vide n'est jamais un succès ambigu.
3. Groupes lus indépendamment ; source mal formée : `FAILED`, sans exception sortante ; sonde incompatible : aucun
   contenu. Aucun `Scenario` ne devient `AcceptanceCriterion`.
4. Racine publiée : `ProviderProjectRoot.locator(Path)` sur la racine reçue, telle quelle ; tout constructeur de
   `ProjectSpecification` a un test de `rootLocator()` dans son module ou passe par
   `ProviderPluginContractAssertions.verifyRead`.
5. Un échec causé par un fichier est un `INVALID_SOURCE` qui le nomme relatif au workspace, attribué à la source, type
   d'exception gardé ; une cause nommant un emplacement serveur (`ServerLocationDisclosure`) cède à son type. Défaut,
   collaborateur ou budget restent non attribués.
6. `SourceLocator` garde sa normalisation ; un texte de refus vient de `WorkspaceRelativePathText`, seul accepté par
   `ProviderIngestionBudget.Session`, dont `addEvidenceLines` exige un document lu.
7. Le refus de `ProjectSnapshotImportService` écrit les diagnostics bloquants relayables et compte les autres.
8. `sync` borne ses diagnostics par `BoundedDiagnostics` (`MAX_ITEMS`, `DIAGNOSTIC_LIMIT_REACHED:<borne>`) : `local`
   en CLI, `remote` en HTTP ; `com.morpheus.api..` n'appelle jamais `local`.
9. Les conventions OpenSpec (`docs/developer/OPENSPEC_PROVIDER.md`) appliquent cette décision.

## Do's and Don'ts

### Do

- Rendre un `ReadCategoryReport` par catégorie demandée ; `PARTIAL` (`PARTIAL_INGESTION`) quand du contenu valide
  est gardé et du contenu sauté.
- Publier la racine par `ProviderProjectRoot.locator(...)` sur la racine reçue, et la tester par `rootLocator()`.
- Rattacher un échec à son fichier là où le lecteur le normalise, type d'exception gardé.
- Attraper `ProviderIngestionLimitException` par une clause propre, avant toute clause d'`IllegalArgumentException`
  ou d'un supertype ; un lecteur de contenu la rend en échec de toutes les catégories demandées, `INVALID_SOURCE` de
  groupe `ingestion-budget`, sans `source`.
- Passer à `ProviderIngestionBudget.Session` le texte de `WorkspaceRelativePathText`, jamais un locator ; citer les
  preuves par `addEvidenceLines(lines, first, last)` dans la méthode qui vient de lire le fichier.
- Exposer les diagnostics de `sync` par `BoundedDiagnostics.local` en CLI, `BoundedDiagnostics.remote` en HTTP.

### Don't

- Ne jamais placer un type de provider dans le contrat de lecture, ni faire dépendre un provider intégré de
  `morpheus-provider-sdk`.
- Ne jamais publier comme racine le fichier lu, un parent, un sous-répertoire ou une racine normalisée ailleurs.
- Ne jamais faire sortir de `read()` une exception pour un contenu lu, ni attribuer à un fichier un défaut, un échec
  de collaborateur ou un dépassement de budget.
- Ne jamais laisser une clause d'`IllegalArgumentException`, `RuntimeException`, `Exception` ou `Throwable` recevoir
  un refus de budget.
- Ne jamais écrire un refus depuis un locator (`.value()`, `.toString()`) ni par `replace('\\', '/')`.
- Ne jamais modifier la normalisation de `SourceLocator` sans migrer les locators persistés.
- Ne jamais servir à un appelant HTTP la projection locale des diagnostics.
- Ne jamais activer le JAR du plugin de référence depuis un test de `morpheus-architecture-tests`.

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
- `addEvidenceLines(lines, first, last)` repose sur un couplage implicite : un lecteur lit un fichier et cite ses
  preuves dans la même méthode. Rien ne vérifie cet ordre ; un lecteur qui citerait les lignes d'un autre fichier que le
  dernier lu serait refusé sous un mauvais nom.
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
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderIngestionLimitCatchArchitectureTest.java` :
  dans les providers, un `try` qui peut atteindre la construction d'une `ProviderIngestionLimitException` et attrape
  `IllegalArgumentException` ou un supertype l'attrape aussi par une clause propre ; règle sur le bytecode, prouvée par
  des fixtures dans les deux sens. Les tests des lecteurs de contenu Markdown, synthétique et OpenSpec tiennent le
  résultat `ingestion-budget`.
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
  dépend `addEvidenceLines(lines, first, last)` ne sont tenus par aucun test.

## References

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — domaine possédé par MORPHEUS.
- [ADR-0169](ARCH-0169-openspec-provider-de-reference-et-schemas-qui-derivent-de-spec-driven.md) — OpenSpec, provider de référence.
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
