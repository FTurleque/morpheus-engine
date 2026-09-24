# ADR-0028 — Contrat de lecture provider unifié et résultats partiels explicites

- Statut : **Acceptée — M2**
- Date : 22 juillet 2026
- Dépend de : ADR-0001, ADR-0002, ADR-0011, ADR-0022, ADR-0024, ADR-0025
- Portée : lecture provider, ingestion partielle, diagnostics

## Décision

Séparer :

```text
SpecificationProvider.probe()
        !=
SpecificationContentReader.read()
```

Le `probe` décrit compatibilité/capacités. Le reader décrit ce qui a réellement été produit.

Contrat applicatif :

```text
ProviderReadRequest
SpecificationContentReader
ProviderReadResult
ReadCategory
ReadCategoryStatus
ReadCategoryReport
```

## Statuts

```text
READ         contenu demandé produit
ABSENT       catégorie supportée mais absente de la source
UNSUPPORTED  sémantique non exposée par ce reader
FAILED       lecture tentée mais en échec
PARTIAL      contenu valide conservé mais incomplet
```

Invariant :

```text
empty collection != ambiguous success
```

## Catégories M2

```text
CURRENT_SPECIFICATIONS
REQUIREMENTS
SCENARIOS
CHANGES
REQUIREMENT_DELTAS
CONSTRAINTS
DESIGN_DECISIONS
IMPLEMENTATION_TASKS
ACCEPTANCE_CRITERIA
EXTERNAL_REFERENCES
ARCHIVES
```

## OpenSpec

Les groupes current/change/deltas sont lus indépendamment afin qu'un groupe en échec ne masque pas automatiquement les autres groupes lisibles.

`openspec-partial` produit :

```text
CURRENT_SPECIFICATIONS = READ      (1)
REQUIREMENTS           = READ      (2)
SCENARIOS              = PARTIAL   (1)
CHANGES                = ABSENT    (0)
PARTIAL_INGESTION
```

Les éléments valides restent accessibles malgré la lecture partielle.

## AcceptanceCriterion

Règle ferme :

```text
Scenario != AcceptanceCriterion
```

OpenSpec ne revendique pas `READ_ACCEPTANCE_CRITERIA` et le reader M2 retourne :

```text
ACCEPTANCE_CRITERIA = UNSUPPORTED
```

Aucune conversion implicite n'est autorisée.

## Catégories différées

En M2-S6 :

```text
EXTERNAL_REFERENCES = UNSUPPORTED
ARCHIVES            = UNSUPPORTED
```

`ExternalReference` existe dans le domaine, mais son ingestion OpenSpec n'est pas définie. Les archives nécessitent la projection temporelle M3.

## Diagnostics

```text
PARTIAL -> PARTIAL_INGESTION
UNSUPPORTED demandé -> OPTIONAL_CAPABILITY_UNAVAILABLE
FAILED -> INVALID_SOURCE ou diagnostic du probe
```

Une catégorie `ABSENT` n'est pas une erreur.

## Preuve d'acceptation — 22 juillet 2026

Gate exécuté sous Windows :

```text
.\mvnw.cmd clean test
Windows 10 x64
Apache Maven 3.9.16
JDK 24.0.1
javac release 21
```

Résultats observés :

```text
ProviderReadContractTest                   3/3 PASS
OpenSpecSpecificationContentReaderTest     5/5 PASS

Domain module                              4 tests
Application module                        38 tests
OpenSpec provider                         26 tests
SQLite store                               6 tests
Architecture tests                        10 tests

TOTAL                                     84/84 PASS
Failures                                      0
Errors                                        0
Skipped                                       0
BUILD SUCCESS
```

Les critères d'acceptation sont démontrés :

1. `openspec-basic` produit `READ` pour les catégories M2 implémentées ;
2. `openspec-partial` conserve 2 requirements et 1 scenario avec `SCENARIOS=PARTIAL` ;
3. `CHANGES=ABSENT` est distinct de `UNSUPPORTED` ;
4. les cinq statuts sont testés ;
5. une source malformée produit `FAILED` sans exception sortante ;
6. un probe incompatible retourne aucun contenu et un échec explicite ;
7. AcceptanceCriterion reste non dérivé ;
8. les contrats applicatifs ne contiennent aucun type OpenSpec ;
9. le build complet est vert.

Les warnings JDK 24 `--enable-native-access=ALL-UNNAMED` de SQLite et SLF4J NOP d'ArchUnit restent non bloquants et ne justifient aucune dépendance de production supplémentaire à ce stade.

## Amendement du 24 septembre 2026 (PRV-1) — la racine publiée par un lecteur est la racine du workspace reçue

### Le contrat ne disait pas ce qu'est la racine d'un projet

`ProviderReadRequest` porte la racine du workspace, `ProjectSpecification` porte un `rootLocator`, et rien ne reliait
les deux. Trois lecteurs publiaient la racine reçue, chacun avec sa propre expression ; le lecteur Markdown structuré
publiait le fichier qu'il lisait (`morpheus/specification.md`). La publication compare ce `rootLocator`, caractère
par caractère, avec la racine sous laquelle le projet a été enregistré (`morpheus projects add`) : dès que le
provider Markdown était primaire — un workspace sans OpenSpec — `composition sync` échouait sur
`project identity collision`. Un workspace uniquement Markdown ne pouvait pas être publié.

### Décision

La racine de projet publiée par un lecteur **est la racine du workspace qu'il a reçue**, normalisée par un point
unique : `ProviderProjectRoot.locator(Path)` (`com.morpheus.application.read`), qui rend
`SourceLocator.file(workspaceRoot.toAbsolutePath().normalize().toString())` — exactement l'expression qu'employaient
déjà les lecteurs OpenSpec, de référence et synthétique, qui ne changent donc pas de chaîne publiée. Un lecteur qui
publie autre chose rend sa propre publication impossible. Les évidences et provenances continuent de nommer le
fichier où elles ont été observées : seule la racine du projet change.

Le point vit dans `morpheus-application`, à côté de `ProviderReadRequest`, et non dans `morpheus-provider-sdk` : les
providers intégrés (`openspec`, `markdown`, `synthetic`) ne dépendent pas du SDK, et lui ajouter cette arête mettrait
la mécanique de découverte et de probe des plugins sur leur classpath pour une seule méthode ; le contrat de lecture
est déjà applicatif (voir « Contrat applicatif » plus haut), et le SDK, le testkit et tout plugin le voient
transitivement.

### Preuves exécutables ajoutées

- `ProviderProjectRootTest#spellsEveryWorkspaceExactlyAsBothFormerReaderExpressionsDid` — chemins relatif, absolu,
  avec `..`, avec `.` et avec séparateurs `\` : le point unique rend la même chaîne que les deux expressions
  antérieures.
- `ProviderPluginContractAssertions.verifyRead` (testkit publié) échoue quand le contenu publié porte une autre racine
  que `ProviderProjectRoot.locator(request.workspaceRoot())`, comparée en `SourceLocator` et non en `Path`.
- `ProviderProjectRootArchitectureTest` — assertion textuelle sur les sources de tous les modules
  `morpheus-provider-*` (le troisième argument de `new ProjectSpecification(` est `ProviderProjectRoot.locator(...)`,
  y compris dans `morpheus-provider-reference`, hors classpath ArchUnit) et règle ArchUnit (toute classe qui construit
  un `ProjectSpecification` appelle `ProviderProjectRoot.locator`), les deux selon ADR-0103.
- `StructuredMarkdownSpecificationContentReaderTest#publishesTheWorkspaceRootAsProjectRootAndKeepsTheFileAsEvidenceSource`
  et `MorpheusCompositionCliTest#syncsAMarkdownOnlyWorkspaceUnderItsRegisteredRoot` — rouges avant le correctif, le
  second sur `project identity collision`.

## Amendement du 24 septembre 2026 (PRV-2) — une section non reconnue termine la précédente

### Constat

`OpenSpecRequirementDeltaReader` ne changeait de genre courant qu'en rencontrant une des trois sections
`## ADDED|MODIFIED|REMOVED Requirements`. Toute autre ligne `## ` était sautée sans effet : une exigence placée sous
`## Notes` après `## REMOVED Requirements` héritait du genre `REMOVED` et était normalisée en suppression — et passait
la validation, puisque `RequirementDelta` n'exige un `statement` qu'hors `REMOVED`. Symétriquement, une exigence
placée avant toute section reconnue était jetée sans rien dire, et la catégorie `REQUIREMENT_DELTAS` restait `READ`.

### Décision

1. **Une section termine la précédente.** Toute ligne qui commence par `## `, hors bloc de code clôturé, et qui n'est
   pas une section de delta normalisée remet le genre courant à « aucun ». Le genre ne traverse plus une section
   étrangère.
2. **Une ligne `## ` non reconnue, dans un bloc de code clôturé, ne termine pas la section.** Elle ne remet pas le
   genre à zéro et ne produit aucun diagnostic : une exigence qui montre un exemple Markdown contenant `## Overview`
   est un document bien formé, et les exigences qui la suivent dans la même section gardent leur genre.

   **La référence est OpenSpec amont**, pas CommonMark : c'est le format pour lequel ces fichiers sont écrits. Le
   masque reproduit `buildCodeFenceMask` (`src/core/parsers/code-fence.ts`) : un bloc s'ouvre sur une suite d'au
   moins trois ```` ` ```` ou `~`, précédée de n'importe quelle indentation et suivie de n'importe quoi ; il ne se
   ferme que sur une suite **du même caractère**, **au moins aussi longue**, suivie **uniquement d'espaces** ; les
   espaces sont ceux du `\s` de JavaScript. CommonMark limiterait l'indentation à trois espaces et refuserait une
   info string contenant un ```` ` ```` : suivre CommonMark ferait, par exemple, disparaître une exigence placée
   après un bloc indenté de quatre espaces contenant `## Overview` en colonne 0, que `develop` conservait et
   qu'OpenSpec tient pour bien formé.

   **Portée du masque : les seules sections non reconnues.** Une ligne `## ADDED|MODIFIED|REMOVED Requirements` ou
   `### Requirement:` écrite dans un bloc de code reste interprétée, exactement comme avant ce correctif (voir « Hors
   périmètre »).
3. **`## RENAMED Requirements` est reconnue, pas normalisée.** C'est une des quatre sections du format de delta
   OpenSpec amont (ADDED / MODIFIED / REMOVED / RENAMED). Elle termine la section précédente sans avertissement ; son
   contenu (lignes `FROM:` / `TO:`) n'est pas normalisé, `RequirementDeltaKind` n'ayant pas de genre de renommage.
4. **Ce qui a été sauté est nommé dans les diagnostics de lecture**, deux `WARNING` dont `source` est le chemin du
   fichier **relatif à la racine du workspace** (jamais absolu) et dont les détails portent `provider`, `change` et
   `line` :
   - section `##` non reconnue → `UNRECOGNIZED_SECTION`, détail `section` (le titre) ;
   - `### Requirement:` rencontré hors de toute section de delta → `PARTIAL_INGESTION`, détail `requirement`.

   Ce nom n'existe qu'en mémoire, dans `Diagnostic.details`. Aucune surface CLI, HTTP ou MCP ne sérialise aujourd'hui
   les détails d'un diagnostic de lecture : la synchronisation n'expose que le nombre de diagnostics, et
   `SnapshotValidationResult` ne garde que `code: message`. Un opérateur voit ce nombre augmenter, pas **quelle**
   exigence a été sautée. Rendre ce nom visible est une autre décision, hors de cet amendement.
5. **La catégorie passe à `PARTIAL`** dès qu'au moins une exigence a été sautée : du contenu valide est conservé mais
   la lecture est incomplète, ce que le tableau « Statuts » ci-dessus appelle `PARTIAL`. Une section non reconnue qui
   ne contient aucune exigence produit l'avertissement mais laisse la catégorie `READ` : rien n'a été perdu que le
   modèle sache porter.

`UNRECOGNIZED_SECTION` est le seul code ajouté à `DiagnosticCode` (en fin d'énumération). `PARTIAL_INGESTION` est
réutilisé pour l'exigence sautée parce qu'il nomme exactement ce cas et reste cohérent avec la table « Diagnostics »
(`PARTIAL -> PARTIAL_INGESTION`). Réutiliser `PARTIAL_INGESTION` pour la section seule aurait associé ce code à une
catégorie restée `READ` ; réutiliser `UNSUPPORTED_SOURCE` aurait signifié « source entière non reconnue ».

### Ce qui ne change pas

- **Un fichier bien formé produit exactement ce qu'il produisait** : mêmes deltas, mêmes genres, aucun diagnostic.
  Vérifié le 24 septembre 2026 sur les **trois** fichiers de delta que le lecteur lit dans le dépôt
  (`openspec-basic/.../add-remember-me`, `openspec-state-matrix/.../extend-timeout` et `.../shorten-timeout`) : sortie
  identique avant et après, zéro avertissement nouveau. Le quatrième fichier présent,
  `openspec-state-matrix/openspec/changes/archive/...`, n'est jamais lu : `listChangeRoots` exclut `archive`.
- **Le piège : la troncature d'une exigence est inchangée.** `requirementEnd` s'arrêtait déjà sur toute ligne `## `,
  qu'elle soit ou non dans un bloc de code, donc une ligne `##` écrite dans le corps d'une exigence la tronquait déjà ;
  ce prédicat n'est pas modifié et n'utilise pas le masque. Hors bloc de code, cette ligne termine désormais aussi la
  section et elle est nommée. Dans un bloc de code, elle ne termine que l'exigence, comme avant : le genre est
  conservé, et rien n'est signalé.

### Hors périmètre, laissé ouvert

- Le contenu d'une section `RENAMED` n'est pas normalisé et n'est pas signalé (comportement antérieur).
- Seul le préfixe littéral `## ` est une section. `##<TAB>Notes` et un titre indenté `   ## Notes` sont des titres
  ATX valides en CommonMark mais ne le satisfont pas : une exigence placée dessous hérite encore du genre précédent,
  exactement comme avant ce correctif.
- La troncature du corps d'une exigence sur une ligne `## ` placée dans un bloc de code reste le défaut préexistant.
- **Le masque ne couvre que les sections non reconnues.** Un exemple de code contenant `## REMOVED Requirements`
  change encore le genre des exigences qui suivent — une exigence `ADDED` peut ainsi sortir `REMOVED`, variante de la
  suppression fantôme d'origine —, et un `### Requirement:` écrit dans un exemple crée encore une exigence fantôme.
  C'est le comportement de `develop`, inchangé. OpenSpec amont masque les trois cas (`requirement-blocks.ts`) ; les
  masquer ici sortirait du reset que ce constat exige et changerait des deltas aujourd'hui publiés.
- **Un bloc de code jamais fermé masque jusqu'à la fin du fichier**, comme chez OpenSpec amont : une ligne qui
  commence par ```` ``` ```` ou `~~~` suivie de texte (```` ```inline``` markers are gone ````) suffit à en ouvrir un.
  Une section non reconnue placée après n'est plus vue, et une exigence dessous hérite du genre précédent — une
  exigence peut ainsi sortir `REMOVED` sans diagnostic, catégorie `READ`, exactement comme sur `develop`. OpenSpec
  amont perd cette exigence sans rien dire ; MORPHEUS en fait un delta fantôme parce que son masque ne couvre pas les
  titres d'exigence (point précédent). Option écartée pour l'instant : un diagnostic quand un bloc est encore ouvert
  en fin de fichier — à reconsidérer avec le point précédent, dont il est la conséquence.

### Preuves exécutables ajoutées

- `OpenSpecRequirementDeltaReaderTest#aRequirementUnderAnUnrecognizedSectionDoesNotInheritTheKindOfThePreviousSection`
  — rouge avant le correctif (l'exigence sous `## Notes` sortait `REMOVED`) ; asserte aussi qu'aucun message, détail ni
  `source` ne satisfait `ServerLocationDisclosure.namesAServerLocation`.
- `OpenSpecRequirementDeltaReaderTest#aRequirementBeforeAnyRecognizedSectionIsNamedInsteadOfSilentlyDropped`,
  `#theRenamedSectionIsRecognizedAndEndsThePreviousSectionWithoutAWarning`,
  `#aLevelTwoHeadingInsideARequirementStillEndsItsBody`, `#theStateMatrixDeltasReadExactlyAsBeforeWithoutAnyDiagnostic`.
- `OpenSpecRequirementDeltaReaderTest#aLevelTwoLineInsideACodeFenceNeitherEndsTheSectionNorWarns` et
  `#aFenceIsRecognizedAtAnyIndentationAsUpstreamOpenSpecDoes` — mêmes deltas et même `statement` tronqué que sur
  `develop`, aucun diagnostic.
- Une garde de fermeture par test, chacune prouvée seule en la retirant :
  `#aShorterRunOfTheSameCharacterDoesNotCloseAFence` (`~~~` dans `~~~~`, longueur),
  `#aRunOfTheOtherCharacterDoesNotCloseAFence` (```` ```` ```` dans `~~~~`, caractère),
  `#aFenceRunFollowedByTextDoesNotCloseAFence` (```` ``` ```` suivi de texte dans ```` ``` ````).
- `#aNonBreakingSpaceBeforeAFenceIsWhitespaceAsInUpstreamOpenSpec` — garde la classe d'espaces de JavaScript : une
  insécable devant l'ouverture et la fermeture donne la sortie de `develop` ; réduite à l'ASCII, seul ce test tombe.
- `#anUnclosedFenceMasksTheRestOfTheFileSoALaterSectionKeepsThePreviousKind` — fige le comportement du bloc jamais
  fermé décrit ci-dessus (`REMOVED` hérité, aucun diagnostic), égal à `develop`.
- `OpenSpecSpecificationContentReaderTest#aSkippedRequirementDeltaMakesTheCategoryPartialInsteadOfRead` et
  `#aLevelTwoLineInsideACodeFenceLeavesTheCategoryRead`.

## Amendement du 24 septembre 2026 (PRV-3) — une lecture en échec nomme le fichier en cause

### Le défaut

Le lecteur OpenSpec rapportait l'échec d'un groupe (`current`, `changes`, `requirement-deltas`) par un `INVALID_SOURCE`
qui ne portait que le nom du groupe et le type de l'exception. Le message de l'exception, qui désignait le fichier —
`OpenSpec specification has no title: <chemin>` —, était jeté, et le champ `source` de `Diagnostic` restait vide.
Le diagnostic étant bloquant, la publication échouait ensuite sur `normalized content contains blocking diagnostics`
sans que rien, ni dans le diagnostic ni dans le refus, ne dise quel fichier corriger. Les lecteurs Markdown et
synthétique reportent le message de leur exception : l'omission n'était pas une politique de non-divulgation, c'était
une perte.

### Décision

**Un échec de lecture nomme le fichier en cause, en chemin relatif à la racine du workspace.** Le diagnostic
`INVALID_SOURCE` porte ce chemin dans `source`, et son message porte le chemin et la cause.

**L'attribution se fait à la source, pas dans le diagnostic.** Chaque lecteur OpenSpec normalise ses fichiers un par
un ; c'est là que le fichier est connu, et c'est là que l'échec est rattaché à lui (`OpenSpecSourceAttribution`). La
variante écartée — relativiser dans `invalidSource` à partir de `request.workspaceRoot()` — demandait de retrouver un
chemin dans un texte : le texte peut venir de la plateforme (`NoSuchFileException` ne porte que le chemin absolu), le
chemin peut y apparaître sous une autre forme (séparateurs, lien résolu), et un chemin à moitié retiré d'une phrase
reste un chemin. Aucun texte n'est donc réécrit : une cause dont le texte nomme un emplacement du serveur est
remplacée par son type, selon la même décision que `ServerLocationDisclosure` applique aux frontières. Les messages
écrits par le lecteur lui-même ne portent plus de chemin absolu, puisque l'attribution fournit le chemin relatif.
`invalidSource` applique la même décision une seconde fois, à tout ce qu'il reçoit, attribué ou non.

**Une attribution ne change pas la catégorie d'un échec.** Seuls les trois échecs qu'un fichier peut causer par son
contenu ou sa lecture sont attribués, chacun dans sa propre catégorie : une `IllegalArgumentException` reste une
`IllegalArgumentException`, une `IllegalStateException` reste une `IllegalStateException`, une `UncheckedIOException`
reste une `UncheckedIOException` sur la même `IOException`. Tout le reste passe **inchangé et non attribué** : un défaut
(`NullPointerException`, dépassement arithmétique), un échec d'un collaborateur (magasin d'identités, persistance) ou
un dépassement de budget. Un défaut n'est pas un échec de contenu d'un fichier, et nommer un fichier pour un échec du
magasin d'identités accuserait un fichier innocent. Vérifié route par route contre `develop` : la synchronisation HTTP
locale et remote (400 pour `IllegalArgumentException`, 409 pour `IllegalStateException` et `KnowledgeStoreException`,
500 pour toute autre exception qu'une lecture de fichier peut lever ; `PublishedHistoryException`, que le serveur mappe
aussi en 409, ne peut pas naître d'une lecture de fichier), la CLI `sync` et `analyze-change` (codes de sortie `USAGE`, `STATE_ERROR`, `INTERNAL_ERROR` sur les
mêmes catégories) et la CLI `composition sync` (échec du groupe converti en diagnostic, puis `STATE_ERROR` sur le refus
de publication) répondent avec le même statut et le même code de sortie qu'avant, pour tout type d'exception.

**Un chemin relatif s'écrit avec `/` sur toutes les plateformes.** `SafeWorkspaceFileResolver` et
`ProviderIngestionBudget` écrivaient le chemin relatif de leurs refus avec le séparateur de la plateforme : répertoire
absent, fichier absent ou non régulier, fichier non UTF-8, fichier changé pendant la lecture, lien symbolique, chemin
canonique hors du workspace, budget dépassé. Sous Windows le `\` fait rejeter le texte entier par
`ServerLocationDisclosure`, et la cause était remplacée par son type là où Linux la relayait. Ces messages nomment
désormais le chemin par un point unique, `WorkspaceRelativePathText`, qui joint ses **composants** par `/` : le même
refus se lit à l'identique sur les deux plateformes. Le chemin n'est pas réécrit caractère par caractère : sous Linux
`\` est un caractère légal d'un nom, et `docs\proof.md` y est un seul nom, qui doit rester `docs\proof.md` sous peine de
désigner un autre fichier. Un chemin qui porte une racine n'est pas relatif et reste tel que la plateforme l'écrit.

**Le refus de publication dit pourquoi.** `ProjectSnapshotImportService` inclut dans son refus les diagnostics
bloquants, borné à ce que `ServerLocationDisclosure.isSafeToRelay` accepte : ce refus atteint la CLI et, par le
serveur local, les appelants remote. Un diagnostic qui nomme un emplacement du serveur est retenu plutôt que nettoyé,
ceux qui ne tiennent pas dans la borne sont comptés, et les deux comptes sont écrits dans le refus.

**Le statut de la catégorie ne change pas : `FAILED`.** Un groupe est lu d'un seul appel, et son contenu n'est versé
dans le résultat qu'au retour de cet appel ; un fichier en échec fait donc tomber le groupe sans qu'aucun élément déjà
normalisé n'y soit conservé. C'est « lecture tentée mais en échec », pas « contenu valide conservé mais incomplet ».
Faire du groupe un `PARTIAL` exigerait de garder les fichiers valides, ce qui est un autre changement.

### Preuves exécutables ajoutées

- `OpenSpecSpecificationContentReaderTest#aFileWithoutTitleIsNamedRelativeToTheWorkspaceAndItsCauseIsReported` —
  deux `spec.md` valides et un sans titre : `source` vaut `openspec/specs/broken/spec.md`, le message porte la cause,
  le groupe est `FAILED` et aucune spécification n'est conservée ; ni message, ni `source`, ni `details` ne nomment un
  emplacement du serveur.
- `OpenSpecSpecificationContentReaderTest#aPlatformFailureNamingAnAbsolutePathIsAttributedWithoutRelayingThatPath` —
  une `UncheckedIOException` dont le texte est un chemin absolu est attribuée au fichier sans que ce chemin soit relayé.
- `ProjectSnapshotImportContractTest#aRejectedPublicationNamesTheFileThatFailedToReadRelativeToTheWorkspace` et
  `#aRejectedPublicationWithholdsABlockingDiagnosticThatNamesAServerLocationAndSaysSo` — le refus de publication, sur
  le store mémoire, nomme le fichier, reste relayable, et retient en le disant un diagnostic qui nomme un chemin absolu.
- `ProjectSnapshotImportContractTest#aRejectedPublicationStaysWithinTheRelayedBoundAndCountsTheDiagnosticsItDoesNotShow`
  — vingt diagnostics bloquants : le refus tient dans `MAX_RELAYED_LENGTH` et se termine par le compte exact de ceux
  qu'il ne montre pas.
- `OpenSpecProjectContentReaderTest#anAttributedFailureKeepsTheCategoryEverySurfaceMapsToAStatus` et
  `#aFailureThatIsNotTheFilesPassesThroughUnchangedAndUnattributed` — les trois catégories attribuées gardent leur type
  (et la même `IOException` pour la troisième) ; un dépassement arithmétique et un échec de collaborateur ressortent
  comme la même instance.
- `MorpheusApiProjectSyncIntegrationTest#aSyncRefusedForInvalidContentIsABadRequestThatNamesTheFileRelativeToTheWorkspace`
  et `MorpheusCliTest#aSyncRefusedForInvalidContentIsAUsageErrorThatNamesTheFileRelativeToTheWorkspace` — un contenu
  invalide reste un 400 et un `USAGE`, avec le fichier en relatif.
- `OpenSpecSpecificationContentReaderTest#aProposalWithoutIntentIsNamedAsTheFileAtFault`,
  `#aDesignDecisionWithoutBodyIsNamedAsTheDesignFileNotTheProposal` et `#aMalformedRequirementDeltaIsNamedAsItsDeltaFile`
  — l'attribution nomme le fichier le plus interne hors du groupe `current`.
- `OpenSpecSpecificationContentReaderTest#aRefusedReadKeepsItsCauseAndNamesTheFileWithForwardSlashesOnEveryPlatform`
  — un fichier non UTF-8 garde sa cause et son chemin en `/` sous Windows.
- `WorkspaceRelativePathTextTest` — un chemin construit par composants s'écrit `docs/proof.md` partout ;
  `Path.of("docs\\proof.md")` s'écrit comme la jonction de ses composants, soit `docs/proof.md` sous Windows et
  `docs\proof.md` sous Linux.
- `SafeWorkspaceFileResolverTest#aNonRegularFileInASubdirectoryIsNamedWithForwardSlashesAndNoServerLocation` et
  `ProviderIngestionBudgetTest#aBudgetRefusalNamesAFileInASubdirectoryWithForwardSlashesAndNoServerLocation` — un
  second refus du résolveur et le refus de budget nomment le fichier en `/`, sans emplacement du serveur.
