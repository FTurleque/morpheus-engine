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

## Amendement du 30 septembre 2026 (PRV-6) — un locator normalise, un texte de refus ne substitue rien

### Le défaut

L'amendement du 24 septembre (PRV-3) a posé que le chemin d'un refus s'écrit par ses composants, jamais caractère par
caractère (`WorkspaceRelativePathText`), puisque sous Linux `\` est un caractère légal d'un nom. Dans le même lot, quatre
sites écrivaient encore le texte d'un refus à partir d'un locator, et `SourceLocator.file` réécrit `\` en `/` :
exactement la substitution que l'autre classe déclare interdite.

- `OpenSpecSourceAttribution.source()` : `SourceLocator.file(workspaceRoot.relativize(file).toString()).value()`.
- Les trois lecteurs OpenSpec (courant, changements, deltas) : `budget.addEvidenceFragment(excerpt, source.value())`,
  dont le second argument ne sert qu'au texte du refus de budget de preuve
  (`provider ingestion evidence bytes exceeds budget for <texte>`).

Sous Linux, un `spec.md` dans un répertoire `a\b` était donc nommé `openspec/specs/a/b/spec.md`, chemin qui n'existe pas,
par le refus de lecture **et** par le refus de budget de preuve. Aucun texte ne disait que ces deux écritures répondaient
à deux besoins différents ; l'audit les a lues comme une seule règle violée. L'attribution (22:52) précède le point
unique (23:33) : le commit qui a introduit celui-ci a migré le résolveur et le budget de document, pas elle, et n'a pas
vu le budget de preuve.

### Décision

**Il y a deux règles, et chacune est juste là où elle s'applique.**

- Un **locator** (`SourceLocator`) désigne une source de façon stable et comparable. Il **normalise** : `file(...)`
  retire les blancs de bord, réécrit `\` en `/` et retire un `./` initial, pour que le même fichier lu sur deux
  plateformes porte le même locator. Il n'est pas le texte d'un refus.
- Le **texte d'un refus** nomme le fichier tel que l'opérateur le trouvera. Il **ne substitue rien** et passe par
  `WorkspaceRelativePathText`.

**`SourceLocator` n'est pas modifié.** La normalisation est voulue, et elle est portée par ce que le dépôt persiste et
compare (inventaire ci-dessous) : la changer changerait des locators déjà publiés et le nom sous lequel un projet est
retrouvé. Les lecteurs OpenSpec continuent d'enregistrer le locator dans les deltas, les spécifications et les preuves.

**Les deux corrections.**

- `OpenSpecSourceAttribution.source()` passe par `WorkspaceRelativePathText.of(...)`.
- Le texte du refus de budget de preuve n'est pas disponible à l'endroit où les lecteurs construisent la preuve : ils n'y
  ont que le locator, et le texte sans substitution ne s'en déduit pas. Faire traverser un second paramètre par les
  méthodes de normalisation de trois lecteurs, dont celui que PRV-2 remanie, était possible ; la session de budget connaît
  déjà ce texte, puisqu'elle l'écrit pour ses propres refus en lisant le document. `Session.addEvidenceFragment(String)`
  attribue donc le fragment **au dernier document lu par la session**, et les trois lecteurs l'appellent sans texte.
  C'est un couplage implicite, nommé comme tel : il tient parce qu'un lecteur lit un fichier et cite ses preuves dans la
  même méthode, et rien ne vérifie cet ordre (voir la garde). La forme à deux arguments reste pour le lecteur Markdown
  (`StructuredMarkdownSpecificationContentReader`), qui passe la constante `SOURCE_FILE` ; le lecteur synthétique
  n'appelle pas `addEvidenceFragment`.
- Le paramètre `source` de chaque méthode de `ProviderIngestionBudget.Session` est **le texte d'un refus**, pas un locator :
  un nom écrit par `WorkspaceRelativePathText`, une étiquette de groupe comme `openspec/current`, ou la constante du
  lecteur Markdown. C'est écrit dans son
  Javadoc et dans `PROVIDER_SDK.md`. Le paramètre ne sert qu'à ce texte : `requireWithin` le place dans le message de
  `ProviderIngestionLimitException`, et nulle part ailleurs.

### Ce que cela implique, et qui est assumé

Pour un nom de fichier qui contient `\` sous une plateforme où c'est un caractère légal (Linux, macOS), **le locator
enregistré et le chemin nommé par le refus diffèrent** : le delta lu dans `openspec/specs/a\b/spec.md` porte
`file:openspec/specs/a/b/spec.md`, et le refus nomme `openspec/specs/a\b/spec.md`. Les deux s'accordent pour tout chemin
que ces lecteurs produisent et qui ne contient pas `\` : ils commencent par `openspec/` et finissent par un nom
`.md`, donc le retrait des blancs de bord du locator n'a rien à retirer. Sous Windows `\` est le séparateur, `a\b` y est
deux composants, le cas n'est pas atteignable et les deux textes s'accordent sur `a/b`.

**Ce que le correctif change, par surface, pour ce cas seul.** Un texte qui contient `\` est pris par
`ServerLocationDisclosure` pour un emplacement possible du serveur ; les surfaces ne demandent pas toutes ce filtre.
Mesuré par lecture du code et par un relevé indépendant sous Linux :

| Surface | Chemin du message | Avant | Après |
|---|---|---|---|
| CLI `sync` | `MorpheusCli.safeMessage`, texte brut | `openspec/specs/a/b/spec.md: <cause>` — un fichier qui n'existe pas | `openspec/specs/a\b/spec.md: <cause>` — le nom exact et la cause : **mieux** |
| HTTP `sync`, local et remote | `BoundaryFailureMessage.safe` (`MorpheusHttpServer`), filtre tout le message | même texte que la CLI, relayé | `InvalidOpenSpecSource` : ni fichier ni cause — **pire** |
| `composition sync` (diagnostic `INVALID_SOURCE`) | `invalidSource` filtre, puis `ProjectSnapshotImportService` relaie `code: message` | nom réécrit et cause | `OpenSpec content reader failed for group current: InvalidOpenSpecSource` — **pire**, et non compté « withheld » : le texte filtré ne contient plus de `\`, donc le refus de publication le tient pour montrable |

Le refus de budget de preuve suit le même chemin (il entre par les mêmes filtres) : avant, il nommait la version
réécrite ; après, il nomme `openspec/specs/a\b/spec.md` dans la CLI et est filtré ailleurs. `Diagnostic.source` porte le
chemin exact, qu'aucune surface CLI, HTTP ou MCP ne sérialise aujourd'hui.

Le refus du résolveur est déjà dans ce cas pour un nom à `\` : son texte contient `\` (il passe par le point), la cause
attribuée est remplacée par son type, et le diagnostic se réduit au même texte nu
(`OpenSpecRefusedFileNameTest#aResolverRefusalOnTheSameNameIsAlsoReportedByItsTypeAlone`). Le refus de budget de
**document** écrit aussi son nom par le point et suit la même règle de filtre, par lecture du code et non par un test.
Le refus de budget de **preuve** ne l'était pas ; il l'est maintenant.

**Option écartée : relayer, dans `invalidSource`, la cause sans le nom.** Elle ne suffit pas. Elle ne change que le
diagnostic de la lecture de contenu, donc `composition sync` ; la synchronisation HTTP n'y passe pas : elle filtre le
message de l'exception elle-même, dans `BoundaryFailureMessage.safe`, et n'a pas de « cause » à relayer séparément.
Le défaut est plus large que ce site.

### Constat à instruire à part

**Un refus retenu devient un type nu sans dire qu'il a été retenu.** `invalidSource` (lecture de contenu) et
`BoundaryFailureMessage.safe` (HTTP) remplacent le texte d'un refus par le nom de la classe de l'exception, et la réponse
ne dit pas qu'un texte a été retenu, ni pourquoi, ni qu'une cause existait. Le refus de publication, lui, compte ce qu'il
retient (`N withheld because they name a server location`) — mais seulement ce qu'il filtre lui-même : un texte déjà
réduit en amont passe pour montrable. La correction est une décision de divulgation, pas de ce correctif : dire qu'un
texte a été retenu, et garder la cause quand elle est relayable, sur ces deux filtres et sur le même modèle. Non traité
ici.

**La racine d'un projet est relue comme un chemin depuis son locator.** `Path.of(project.rootLocator().value())`
dans `MorpheusProjectSyncApiService`, `MorpheusCompositionCli` et `MorpheusCli#projectWorkspace`. Sous Linux, une racine
dont le chemin absolu contient `\` a été enregistrée en `/` (le locator normalise) et est relue comme un autre répertoire.
C'est le sens inverse de PRV-6 — un locator relu comme un chemin — et il préexiste. Non corrigé ici.

**`LocalSourceInventoryScanner#display` et la collision possible.** Voir « Sites de même classe » ; par lecture, non
exécuté, `SourcePath` y fait aussi se confondre `a\b/spec.md` et `a/b/spec.md`.

### Inventaire de `SourceLocator` (mesuré le 30 septembre 2026 à `933a63fe`)

La décision de ne pas modifier `SourceLocator` repose sur ceci, pas sur une intuition. Méthode de comptage :
`git grep` à `933a63fe`, occurrences littérales dans `src/main` et `src/test` ; « nomme » désigne tout fichier qui contient
le mot `SourceLocator`. Les nombres sont un ordre de grandeur daté, pas une constante.

- **Constructions** : 20 appels de production à `SourceLocator.file(` dans 16 fichiers, 11 `new SourceLocator(` dans 10
  fichiers ; 38 fichiers de production nomment le type ; 94 fichiers de test appellent `file(`.
- **Persisté (SQLite)** : `projects(root_scheme, root_value)` et la recherche d'un projet par racine
  (`WHERE root_scheme = ? AND root_value = ?`, `SqliteSpecificationKnowledgeStore`) ; `source_scheme`/`source_value` des
  preuves et de chaque famille de provenance (`SqliteSnapshotBusinessContentWriter`, `SqliteVersionedRequirementStore`) ;
  `provenance_source_*` des références externes ; `source_locator_*` du portefeuille ;
  `composition_conflict_candidate.source_locator` (`V012__multi_provider_composition.sql`), colonne de texte qui garde la
  forme `file:<chemin>` écrite depuis `MultiProviderCompositionService` (`provenance.source().toString()`). Les stores
  mémoire gardent l'objet.
- **Comparé** : égalité de `record` et `compareTo` (schéma puis valeur) ; la racine qu'un lecteur publie
  (`ProviderProjectRoot.locator`) doit égaler celle sous laquelle le projet est enregistré, sans quoi la publication
  entre en collision (PRV-1). Cette racine est un chemin **absolu** : sous Windows `C:\w` y est enregistré `C:/w`.
- **Relu comme un chemin** : la racine, par `Path.of(project.rootLocator().value())` (constat ci-dessus).
- **Servi** : `provenance.source().toString()` (`file:<chemin>`) dans les vues compactes et la composition ; IPC
  d'un résultat de probe (`ProviderProbeResultCodec`).

Aucun de ces usages ne peut changer de normalisation sans migrer des lignes existantes.

### Sites de même classe : corrigés, nommés, laissés

- **Corrigés** : `OpenSpecSourceAttribution.source()` et les trois appels `budget.addEvidenceFragment(excerpt,
  source.value())` des lecteurs OpenSpec.
- **Laissé, nommé** : `LocalSourceInventoryScanner#display` écrit `Failure.source` d'un échec de scan avec
  `replace('\\', '/')`, et la même classe écrit un autre `Failure.source` par `SourcePath.toString()`, qui normalise de
  la même façon. `SourcePath` est l'identité persistée du fichier dans l'état de synchronisation
  (`SqliteSyncStateStore`) : corriger `display` seul ferait nommer le même fichier de deux façons dans un même résultat,
  et corriger `SourcePath` est le changement d'un identifiant, qui n'est pas celui-ci. Par lecture du code, **non
  exécuté** : un fichier nommé `a\b` sous Linux y serait inventorié sous `a/b`, et `a\b/spec.md` et `a/b/spec.md`
  coexistant donneraient la même clé, donc un `SOURCE_OBSERVED_TWICE` si leur contenu diffère
  (`LocalSourceInventoryScanner`, `entries.putIfAbsent`) et une fusion silencieuse sinon. Constat à instruire à part.
- **Laissé, nommé** : `specificationKey` (`relativeParent.toString().replace('\\', '/')` dans le lecteur des
  spécifications courantes et dans celui des deltas) est une clé d'identité externe (`specification:<clé>`), pas un
  texte de refus.
- **Laissé, nommé** : le `Diagnostic.source` des diagnostics d'exigence sautée du lecteur de deltas
  (`OpenSpecRequirementDeltaReader`, valeur du locator). `Diagnostic.source` n'est pas un locator : c'est un texte libre,
  et il porte aujourd'hui trois formes (le texte d'un refus pour `INVALID_SOURCE` d'une lecture, la valeur d'un locator
  pour ces avertissements, un chemin sondé pour `INVALID_SOURCE` d'un probe) ; le Javadoc de `Diagnostic` le dit
  désormais. Ces avertissements ne sont pas modifiés ici parce que le fichier est celui que PRV-2 remanie et que leur
  aligner sur le texte de refus est un choix qui revient avec lui ; ils s'accordent avec le refus pour tout nom sans `\`.
- **Hors classe** : `MorpheusProjectRegistryApiService#workspaceName` réécrit `\` pour chercher le dernier segment d'un
  locator avant de le projeter ; c'est une projection de locator, pas un refus.

### Preuves exécutables ajoutées

- `OpenSpecRefusedFileNameTest` (7 tests, dont 5 sous Linux et macOS) : un refus nomme `openspec/specs/a\b/spec.md` tel
  qu'il est (`source` du diagnostic, exception de la lecture de projet), aucun texte ne nomme `a/b`, le locator du même
  fichier reste `file:openspec/specs/a/b/spec.md`, **le refus de budget de preuve des trois lecteurs nomme le fichier
  tel qu'il est**, et le refus du résolveur est lui aussi réduit à son type ; sur toute plateforme : le même fichier sans
  `\` porte le même texte comme refus et comme locator, et l'attribution écrit `a\b` ou `a/b` selon que `\` est un
  séparateur. Sous Windows, cinq des sept tests sont ignorés (`@EnabledOnOs`) parce que le nom n'y existe pas.
- `ProviderIngestionBudgetTest` : un fragment de preuve est refusé sous le nom du dernier document lu, et refusé comme
  un mésusage si la session n'en a lu aucun.
- `RefusalPathTextArchitectureTest` (8 tests) : une classe qui écrit le chemin d'un refus par
  `WorkspaceRelativePathText.of(` (ou l'importe statiquement) ne l'écrit par aucune des sept voies de locator,
  substitution ou séparateur de plateforme ; aucun appel d'une méthode de `ProviderIngestionBudget` ne reçoit un
  locator (`.value()`, `.toString()`, `SourceLocator`) comme texte ; règle ArchUnit : `OpenSpecSourceAttribution` ne dépend
  pas de `SourceLocator`.

### Ce que la garde ne couvre pas

Une lecture statique ne sait pas distinguer un refus d'un locator par leur intention : `SourceLocator.file(` et
`replace('\\', '/')` sont légitimes là où l'on construit un locator ou une clé. La garde **découvre** les classes qui se
déclarent constructrices de refus et juge les appels du budget ; elle ne voit donc pas :

- un constructeur de refus qui n'appelle jamais le point (`LocalSourceInventoryScanner#display`) ;
- une classe du plancher qui **reçoit** le texte de son refus en paramètre `String` : `ProviderIngestionBudget`
  n'est pas tenu sur ce que ses appelants lui passent — le défaut des trois sites de preuve était de ce type, et
  seule la seconde analyse, sur les sites d'appel, le voit ; un texte passé par une variable ou un assistant n'est vu
  par aucune ;
- l'ordre « lire un fichier, puis citer ses preuves » dont dépend `addEvidenceFragment(String)` : un lecteur qui
  citerait un fragment d'un autre fichier que le dernier lu serait refusé sous un mauvais nom ;
- un jugement par méthode (il est par classe), ni une écriture par un autre tour (`Path.toString()` puis `replace` aux
  autres arguments, un `Pattern`).

Les planchers qu'elle impose (trois classes de refus connues découvertes, les quatre modules qui appellent le budget
vus) empêchent qu'elle passe à vide.
## Amendement du 30 septembre 2026 (PRV-7) — la garde de la racine publiée contrôle l'argument entier

### Le défaut

La garde posée par l'amendement PRV-1 affirmait plus qu'elle ne vérifiait, sur trois points.

- **Le balayage textuel ne lisait que le préfixe.** Il exigeait que le troisième argument de
  `new ProjectSpecification(` commence par `ProviderProjectRoot.locator(` (`startsWith`). La forme
  `ProviderProjectRoot.locator(request.workspaceRoot().resolve("openspec"))` passait : un septième lecteur écrit ainsi
  republierait une racine divergente et PRV-1 se reproduirait à l'identique.
- **La règle ArchUnit était large et, pour un module, vacante.** Elle n'exigeait qu'un appel à `locator` quelque part
  dans la classe : une classe portant deux constructions dont une seule correcte passait. Et
  `morpheus-provider-reference` n'était pas une dépendance de test de `morpheus-architecture-tests` : la règle ne le
  voyait pas, seul le texte le gardait. L'amendement PRV-1 l'écrit (« hors classpath ArchUnit »).
- **Rien n'obligeait un nouveau lecteur à avoir un test comportemental de sa racine**, alors que ce sont ces tests qui
  tiennent l'invariant, le texte n'en tenant que l'orthographe.

### Décision

1. **L'argument entier est contrôlé.** La parenthèse ouverte par `ProviderProjectRoot.locator(` doit fermer
   l'argument, mesurée par équilibre des parenthèses (littéraux ignorés) et non par `endsWith(")")`, qui accepterait
   `locator(a).resolve(b)`. Ce qu'elle enclôt doit être reconnu par une des formes admises de
   `ProviderProjectRootArchitectureTest.ReceivedRootForm` : **chaque constante porte sa propre reconnaissance**, et le
   balayage les interroge dans l'ordre, si bien que la liste qu'un refus affiche est la logique appliquée, pas un
   message à tenir d'accord avec elle. Formes énumérées avec leur raison plutôt que laissées à une expression régulière
   qui refuserait sans dire pourquoi tout ce qu'elle n'a pas prévu :
   - directement dans l'argument : `<request>.workspaceRoot()`, **dont le receveur est lui-même reçu** — un paramètre
     déclaré du fichier, ou un nom lié seulement à de tels noms —, parce qu'une requête construite sur un chemin dérivé
     (`ProviderReadRequest.all(request.workspaceRoot().resolve("openspec"), …)`) porte ce chemin ; et un **nom** ou
     `this.<nom>` ;
   - dans une liaison de ce nom, en plus : `Objects.requireNonNull(<admise>, "<message>")` (rend son argument) et
     `<admise>.toAbsolutePath()` / `<admise>.normalize()` (le point unique les réapplique, et elles sont idempotentes :
     la racine publiée est celle que donne la racine reçue). Dans l'argument lui-même, ces deux dernières seraient une
     seconde orthographe de la normalisation que le point unique possède : elles y sont refusées.

   Un nom est suivi jusqu'à **chacune** de ses liaisons dans le fichier : déclaration, affectation parenthésée ou non,
   y compris à un champ du même nom. Une variable de boucle for-each, une variable de motif (`instanceof Path root`,
   `case Path root ->`) et une composante de motif d'enregistrement sont des liaisons, jamais admises. Un nom **sans
   liaison** n'est admis que si le fichier le **déclare comme paramètre** typé (`(Type nom,` ou `, Type nom)`) : un
   nom lié ailleurs — un champ hérité, par exemple — est refusé au lieu d'être tenu pour reçu.

   Le texte est lu **une fois**, comme le compilateur le lit pour ce qui compte ici : échappements unicode traduits
   d'abord (un `root = …` est une liaison de `root`), puis commentaires retirés, littéraux conservés. Un
   commentaire dans l'argument ne le fait donc plus refuser, et une apostrophe dans un commentaire ne fait plus lever
   d'exception ; une construction que le balayage ne sait pas délimiter lève une exception qui **nomme le fichier**.
   La construction est reconnue sous son nom simple ou qualifié (`new com.morpheus.domain.project.ProjectSpecification(`),
   et une référence de constructeur `ProjectSpecification::new`, dont l'argument est illisible, est refusée.

   Les six constructions du dépôt s'écrivent sous trois formes — `locator(request.workspaceRoot())` (Markdown,
   synthétique), `locator(root)` (trois lecteurs OpenSpec) et `locator(workspace)` (référence) — toutes admises sans
   modification des lecteurs : `request` y est partout le paramètre de `read`, sans autre liaison, et `root` /
   `workspace` n'y sont liés qu'à des formes admises.
2. **La règle ArchUnit est par unité de code, et voit les références de constructeur.** Chaque méthode, constructeur ou
   lambda qui construit un `ProjectSpecification` ou référence son constructeur appelle `ProviderProjectRoot.locator` au
   moins une fois par construction. Elle compte des appels et **ne lit pas l'argument** : une construction que seule la
   règle voit est tenue à la co-localisation d'un appel `locator`, pas à ce que devient son résultat
   (`SourceLocator.file(ProviderProjectRoot.locator(w).value() + "/openspec")` la satisfait ; c'est le texte, qui lit
   désormais aussi la forme qualifiée, qui la refuse).
3. **La vacance est comblée par la dépendance, pas écrite.** `morpheus-provider-reference` devient dépendance de test
   de `morpheus-architecture-tests`. Elle n'est lue que par l'import de classpath d'ArchUnit, jamais par nom : le POM
   la déclare donc utilisée (`usedDependencies`), selon le précédent de `morpheus-cli` pour une dépendance découverte
   au classpath. Un test (`#theRuleSeesEverySourceFileTheScanReads`) tient la règle et le balayage au même ensemble de
   fichiers source : un module lecteur absent du classpath est refusé par son nom, comme `HttpRoutesFamilyArchitectureTest`
   le fait pour les routeurs.
4. **Un lecteur a un test de sa racine.** Tout fichier source qui construit un `ProjectSpecification` a, dans son
   propre module, un test qui, **hors commentaires**, construit ce lecteur, mentionne `rootLocator()` et épelle une
   racine attendue (`ProviderProjectRoot.locator(` ou `SourceLocator.file(`), ou qui remet à
   `ProviderPluginContractAssertions.verifyRead(` un plugin dont la source construit ce lecteur (le contrat publié
   compare la racine publiée à la racine reçue, `ProviderPluginContractAssertionsTest` prouve qu'il refuse un fichier).
   Ce sont des **mentions** : la garde ne vérifie pas que le test compare les deux.

### Ce que l'ajout de la dépendance a déclenché

La suite complète de `morpheus-architecture-tests` a été exécutée avant et après l'ajout (réacteur `-am`, hors
`clean verify`) : **aucune règle existante ne tombe sur le code du plugin de référence.** Le seul échec, identique
avant et après, est `CoverageQualityGateTest#perModuleCoverageDoesNotRegressBelowQualifiedBaseline`, qui refuse de
conclure sans les rapports JaCoCo d'un `clean verify` complet (« 0 of 16 reactor modules ») — condition
d'exécution, pas un constat.

Un risque **latent** est nommé, non corrigé. `ProviderPluginActivator` crée son `URLClassLoader` avec pour parent le
chargeur de `MorpheusProviderPlugin` ; sur le classpath de test de ce module, ce parent voit désormais le service
`META-INF/services/com.morpheus.sdk.provider.MorpheusProviderPlugin` et les classes du plugin de référence. Mesuré sur
le JDK 21 du dépôt (deux `URLClassLoader` parent et enfant déclarant le même service et la même classe) :
`ServiceLoader` rend **un seul** service, **chargé par le parent** — il dédoublonne par nom de classe. Un test
d'architecture qui activerait le JAR de référence trouverait donc exactement un service, chargé depuis le classpath,
et **passerait sans exécuter la copie vérifiée par SHA-256** : le contrôle d'intégrité serait contourné sans que rien
ne le signale. Aucun test du module n'active de plugin aujourd'hui : le seul test M22 sur ce JAR
(`ProviderPluginPlatformContractTest#externalReferenceJarIsDiscoveredAsMetadataWithoutActivation`) s'arrête à la
découverte, qui ne charge aucune classe. Un commentaire du POM, à côté de la dépendance, le dit à qui la lira ; un
test d'activation, s'il devient nécessaire, a sa place dans `morpheus-provider-sdk` ou `morpheus-provider-reference`,
pas ici.

### Ce que la garde ne couvre pas

Figé par `ProviderProjectRootArchitectureTest#theScanAcceptsTheDivergencesItDoesNotFollow`, pour qu'un changement dans
un sens ou dans l'autre se voie :

- une valeur qui atteint un **paramètre** est tenue pour reçue : le balayage ne la suit pas jusqu'à l'appelant qui la
  passe. Le `read(Path workspaceRoot, …, budget)` package-privé des lecteurs OpenSpec en est un, et un appelant qui lui
  passerait `root.resolve("openspec")` ne serait vu que par les tests comportementaux ;
- un nom est suivi dans son fichier seulement, et sans portée. Une liaison du même nom dans une autre méthode est tenue
  aux mêmes formes (strict à dessein : un lecteur qui réutilise le nom pour un autre chemin doit le renommer), et un
  **paramètre du même nom dans une autre méthode** suffit à faire admettre un nom qui n'est lié nulle part dans le
  fichier — un champ hérité, par exemple ;
- le receveur de `.workspaceRoot()` doit être un paramètre ou un nom lié seulement à de tels noms, mais son **type**
  n'est pas lu ;
- un paramètre est reconnu à sa déclaration typée : un paramètre de lambda non typé n'en est pas un, et une racine
  obtenue par lui est refusée (strict, écrit) ;
- la garde des tests comportementaux vérifie des mentions : un test qui construit le lecteur, lit `rootLocator()` et
  épelle une racine attendue passe, qu'il les compare ou non, et qu'il s'exécute ou non ; elle est par fichier source,
  pas par construction ;
- une construction par réflexion n'est vue ni par le texte ni par la règle ;
- le point unique écrit qualifié ou importé statiquement est refusé, pas admis.

### Texte rendu faux, amendé ici

- Cet ADR, amendement PRV-1, « Preuves exécutables ajoutées » : « y compris dans `morpheus-provider-reference`, hors
  classpath ArchUnit » — vrai au 24 septembre, faux depuis cet amendement. Mesure datée, non réécrite.
- ADR-0103, décision 1, condition 1 : amendé à la même date.
- `.claude/rules/architecture.md` et le Javadoc de
  `HttpRoutesFamilyArchitectureTest#theRulesSeeEveryRouterTheSourcesDeclare` énonçaient au présent que le plugin de
  référence est hors de l'ensemble importé : énoncés de règle, corrigés. La skill `enforcement-choice`, qui ne nomme
  aucun module, reste juste et n'est pas touchée.
- Le Javadoc des quatre tests de lecteur qui l'écrivaient (`OpenSpecChangeMetadataReaderTest`,
  `OpenSpecCurrentSpecificationReaderTest`, `OpenSpecSpecificationContentReaderTest`,
  `SyntheticSpecificationContentReaderTest`) : « the architecture rule only proves the root argument starts with
  `ProviderProjectRoot.locator(` » est corrigé en ce que le balayage tient désormais, et ce qu'il ne suit pas.

### Preuves exécutables ajoutées

- `ProviderProjectRootArchitectureTest#theScanRefusesARootResolvedInsideTheSinglePoint` — la forme
  `ProviderProjectRoot.locator(request.workspaceRoot().resolve("openspec"))`, sur une source synthétique, est refusée
  et nommée.
- `#theScanRefusesEveryDerivedRootInsideOrAroundTheSinglePoint` — vingt autres formes, chacune refusée une fois :
  autour du point unique (`getParent()`, un appel chaîné après lui, `normalize()` dans l'argument, un point unique
  imbriqué, une racine qui n'y passe pas) ; par un nom (liaison à un sous-répertoire, réaffectation, second nom, boucle
  for-each, affectation parenthésée, nom en échappement unicode, champ sans liaison ni paramètre) ; par une requête
  dérivée, locale ou en champ ; par une variable de motif `instanceof`, de `case` ou une composante de motif
  d'enregistrement portant le nom d'un paramètre ; par un paramètre de lambda non typé ; par une construction qualifiée
  et par une référence de constructeur.
- `#theScanAcceptsTheFormsTheReadersWrite` — les formes des quatre modules lecteurs, écrites comme ils les écrivent,
  plus un commentaire (avec apostrophe) dans l'argument et dans la construction, et `this.root` lié depuis un paramètre.
- `#theScanAcceptsTheDivergencesItDoesNotFollow` — les trois angles morts ci-dessus, figés.
- `#aScanThatFindsNothingInARequiredModuleIsRefused` — le refus de passer à vide, auparavant prouvé seulement sur le
  dépôt réel, est rejoué sur un arbre synthétique ; l'assertion sur le dépôt réel reste.
- `#aConstructionTheScanCannotDelimitNamesItsFile`.
- `#theRuleRefusesAConstructionWithoutTheSinglePointInItsOwnCodeUnit` — trois classes de test sont refusées (deux
  constructions pour un appel ; l'appel dans une autre méthode ; une référence de constructeur, que l'ancienne règle ne
  voyait pas), une quatrième acceptée.
- `#theRuleSeesEverySourceFileTheScanReads` et `#aReaderWithoutATestOfItsPublishedRootIsRefused` (dont un test qui ne
  mentionne la racine qu'en commentaire, et un qui lit `rootLocator()` sans épeler de racine attendue).

Cassées pour de vrai, puis remises. Sur le code réel : la forme `resolve("openspec")`, puis une requête dérivée
`ProviderReadRequest sub = ProviderReadRequest.all(request.workspaceRoot().resolve("openspec"), …)` suivie de
`locator(sub.workspaceRoot())`, écrites dans `SyntheticSpecificationContentReader`, font chacune tomber
`#everyProviderPublishesItsProjectRootThroughTheSinglePoint` en nommant le fichier ; la dépendance retirée du POM fait
tomber `#theRuleSeesEverySourceFileTheScanReads` en nommant `ReferenceSpecificationContentReader.java` ; l'assertion
de racine retirée de `SyntheticSpecificationContentReaderTest` fait tomber
`#everyClassThatConstructsAProjectSpecificationHasATestThatReadsTheRootItPublishes`. Dans la garde : chaque correctif
retiré fait tomber exactement les cas qu'il tient (receveur non suivi, variables de motif, exigence de paramètre,
traduction unicode, retrait des commentaires, références de constructeur, racine attendue du test comportemental) ;
le contrôle réduit à l'ancien préfixe fait tomber les deux tests de refus synthétiques.
## Amendement du 30 septembre 2026 (PRV-2, suite) — un bloc jamais fermé est un diagnostic, une exigence sautée a un nom

Cet amendement reprend deux points que l'amendement PRV-2 du 24 septembre a laissés ouverts par écrit (« Hors périmètre,
laissé ouvert », quatrième et cinquième puces, et la fin de sa décision 4). Il **applique** l'option que ce texte
reportait — un diagnostic pour un bloc encore ouvert en fin de fichier — et **prend** la décision de visibilité qu'il
renvoyait ailleurs. Le texte du 24 septembre reste tel quel : il décrit `develop` à cette date.

### Constat (à `933a63fe`)

1. **Le masque conditionnait une moitié de la décision de section, pas l'autre.** Dans `normalizeDeltaFile`, une ligne
   `## ADDED|MODIFIED|REMOVED Requirements` posait le genre sans consulter le masque (`OpenSpecRequirementDeltaReader.java:147-151`),
   alors que le reset sur une section non reconnue le consultait (`:152`). Un `## REMOVED Requirements` écrit dans un
   exemple de code changeait donc le genre des exigences suivantes. OpenSpec amont masque les deux.
2. **Un bloc jamais fermé désactivait le reset sans rien dire.** `OPENING_FENCE` ouvre sur une ligne qui *commence* par
   trois accents graves ou tildes, quoi qu'il suive (`:51-52`) ; `CLOSING_FENCE` exige une ligne entière (`:53-54`). Une
   ligne de prose ```` ```inline``` markers are gone ```` masquait donc le reste du fichier : une exigence sous `## Notes`
   sortait `REMOVED`, sans diagnostic, catégorie `READ`. `anUnclosedFenceMasksTheRestOfTheFileSoALaterSectionKeepsThePreviousKind`
   l'épinglait comme attendu.
3. **Le nom de l'exigence sautée n'atteignait aucune surface.** Il vivait dans `Diagnostic.details` (clé `requirement`) ;
   `sync` n'exposait que `diagnosticCount`, en CLI comme en HTTP. La prémisse selon laquelle `SnapshotValidationResult`
   serait le point qui perd l'information est **inexacte** : ce record ne quitte jamais l'application.
   `SnapshotLifecycleService.validate` n'en lit que `isValid()` pour choisir `READY` ou `FAILED`, et ses `warnings` sont
   jetés. L'élargir n'aurait rien rendu visible. Le point qui perd le nom est la vue de sync, qui réduit
   `ProjectSnapshotImportResult.diagnostics()` — des `Diagnostic` complets, détails compris — à sa taille.

### Décision

1. **Le masque est symétrique.** Une ligne masquée ne pose pas le genre et ne le remet pas à zéro. C'est la correction
   d'une incohérence interne, pas une divergence d'avec l'amont, qui masque les deux. Conséquence mesurée : un exemple
   **fermé** placé sous `## Notes` et contenant `## ADDED Requirements` puis `### Requirement: Phantom` était publié
   `ADDED Phantom` ; il ne l'est plus, et un `PARTIAL_INGESTION` nomme `Phantom` (le genre reste « aucun » après
   `## Notes`, et le titre d'exigence de l'exemple n'est pas masqué — voir « Hors périmètre »). C'est une amélioration,
   avec un **faux positif assumé** : la catégorie passe à `PARTIAL` à cause d'un exemple de code.
2. **Un bloc encore ouvert en fin de fichier est nommé.** `UNCLOSED_CODE_FENCE` (ajouté en fin de `DiagnosticCode`), en
   `WARNING`, `source` relative au workspace, détails `provider`, `change`, `fence` (la suite qui l'a ouvert) et `line`
   (la ligne d'ouverture, base 1). La catégorie `REQUIREMENT_DELTAS` passe à `PARTIAL`, codes
   `[PARTIAL_INGESTION, UNCLOSED_CODE_FENCE]`, **même si aucune exigence ne suit le bloc** : MORPHEUS dit qu'il a lu un
   fichier dont la structure lui échappe, et la table « Diagnostics » ci-dessus (`PARTIAL -> PARTIAL_INGESTION`) reste
   vraie. Les règles d'ouverture et de fermeture (`OPENING_FENCE`, `CLOSING_FENCE`) ne changent pas : le masque reste
   celui de `buildCodeFenceMask`.
3. **Aucune exigence placée après l'ouverture d'un bloc jamais fermé ne reçoit de genre — décision à confirmer
   explicitement par le relecteur.** Elle est sautée et nommée par un `PARTIAL_INGESTION` (détail `requirement`, message
   « follows a code fence that is never closed »), **même quand un en-tête de delta bien formé suit le bloc**. Ce point
   n'était pas dans l'option reportée ; il en est la condition, et il a un coût qu'il faut lire en entier :
   - **Ce qu'il empêche.** Le point 1 seul aggravait le bloc jamais fermé : un `## ADDED Requirements` placé après lui
     devenait masqué, et les exigences qui le suivent sortaient avec le genre d'avant le bloc — `REMOVED` pour une
     exigence `ADDED`, la suppression fantôme d'origine, sur une section bien écrite.
   - **Ce qu'il coûte.** Jusqu'ici, une exigence placée après un bloc jamais fermé mais **sous un en-tête de delta bien
     formé** était publiée **avec le bon genre**, parce que `DELTA_SECTION` n'était pas masqué. Elle ne l'est plus. Exemple
     minimal : un fichier dont la ligne 1 est ```` ``` ````, suivi de `## ADDED Requirements` puis
     `### Requirement: A` — 1.2.0 publiait `ADDED A` ; 1.2.1 ne publie rien, et nomme `A` avec la ligne d'ouverture du
     bloc. Mesuré le 30 septembre 2026 (tableau ci-dessous) : c'est aussi le cas d'un bloc indenté ouvert sous
     `## REMOVED` puis d'un `## ADDED` bien formé, et d'un bloc ouvert dans un scénario, suivi d'une exigence de la
     **même** section.
   - **Pourquoi l'accepter.** Après l'ouverture d'un bloc jamais fermé, tout titre de section est masqué : le genre en
     vigueur n'est plus une lecture du fichier mais une supposition, que MORPHEUS ne peut pas distinguer de la bonne
     réponse. C'est aussi ce que fait l'amont, sans le dire : dans OpenSpec (`src/core/parsers/code-fence.ts`,
     `buildCodeFenceMask` ; `src/core/parsers/requirement-blocks.ts`, lu sur `main` à `c879d13d`),
     `splitTopLevelSections` ignore toute ligne masquée, `parseRequirementBlocksFromSection` ne reconnaît pas un
     `### Requirement:` masqué, `parseRemovedNames` saute les lignes masquées, et `findOrphanedRequirements` — qui
     signale une exigence hors section — les saute aussi. Une exigence après un bloc jamais fermé est donc perdue par
     l'amont **en silence** ; MORPHEUS la perd de même, **et la nomme**.
   - **Alternative écartée : « un bloc jamais fermé n'est pas un bloc »** (ne masquer que les blocs fermés). Elle
     rendrait `ADDED A` dans l'exemple, mais s'écarte **et** de l'amont, dont le masque court jusqu'à la fin du fichier,
     **et** de CommonMark, pour qui un bloc non fermé se termine à la fin du document. Elle réintroduirait aussi une
     lecture de la structure là où le format dit qu'il n'y en a pas.
4. **Le nom atteint l'opérateur par `sync`, en CLI et en HTTP.** C'est la commande qui publie, donc celle que l'opérateur
   lit au moment où une exigence disparaît. Le vocabulaire est celui de `Diagnostic` — `code`, `severity`, `message`,
   `details`, `source` —, porté par un seul record applicatif, `BoundedDiagnostics` : au plus `MAX_ITEMS` (32)
   diagnostics, les plus graves d'abord puis dans l'ordre de production, `truncated` et `truncationReason`
   (`DIAGNOSTIC_LIMIT_REACHED:32`) quand il en manque, sur le modèle des traversées ; `diagnosticCount` reste le total.
   - **CLI** (`sync`, texte et `--json`) : `BoundedDiagnostics.local`, chaque diagnostic tel que produit — la CLI est
     l'endroit où l'opérateur corrige ses fichiers. Une ligne `diagnostic=<SEVERITY> <CODE> <source> {détails} <message>`
     par élément, puis `truncationReason=` sous le nom que les commandes de traversée impriment déjà. Ces lignes vont sur
     **stdout**, alors qu'ADR-0059 réserve stderr aux « erreurs/diagnostics d'exécution CLI » : ce ne sont pas des
     diagnostics d'exécution de la commande, qui a réussi, mais des **données de son résultat** — ce que la lecture a
     publié et ce qu'elle a sauté —, au même titre que `diagnostics=<n>` qui y était déjà. Le `--json` les porte dans le
     même objet ; les séparer sur stderr couperait le résultat en deux flux.
   - **HTTP** (`POST /api/v1/projects/{projectId}/sync`, joignable par un appelant remote `WRITE`) :
     `BoundedDiagnostics.remote`, projection **allowlistée** sur le modèle de `ProviderPluginViews` : seules les clés de
     `REMOTE_DETAIL_KEYS`, seules les valeurs que `ServerLocationDisclosure.isSafeToRelay` accepte ; un message refusé
     est remplacé par le code, une `source` refusée est retirée. Chaque valeur relayée tient donc dans
     `MAX_RELAYED_LENGTH`.
   - **OpenAPI** : la réponse de `syncProject` était le `SuccessEnvelope` générique ; elle est typée (`SyncResult`,
     `BoundedDiagnostics`, `Diagnostic`, `DiagnosticCode`, `DiagnosticSeverity`). Les deux énumérations sont tenues
     contre Java par `PublishedEnumsMatchJavaEnumsTest` ; la borne, la forme de la liste et d'un élément et la liste
     des clés relayées par `SyncDiagnosticsContractTest` ; les clés de `SyncResult` par le test d'intégration HTTP, sur
     une vraie réponse.
   - **Le choix de projection est gardé.** `SyncDiagnosticsContractTest` porte deux règles ArchUnit : aucune classe de
     `com.morpheus.api..` n'appelle `BoundedDiagnostics.local`, et `MorpheusProjectSyncApiService` appelle
     `BoundedDiagnostics.remote`. Le test d'intégration HTTP le prouve sur la valeur servie : un titre refusé par le
     prédicat de localisation (`Support TCP / UDP`) est absent du corps HTTP, présent dans la sortie CLI ; ce test ne
     dépend d'aucune permission POSIX et tourne sous Windows, contrairement à `MorpheusProjectSyncDisclosureTest`.
   - `SnapshotValidationResult` **n'est pas élargi** (constat 3) : aucune surface ne le lit.

### Ce qui change — mesure du 30 septembre 2026

Mesurée sur le lecteur de `933a63fe` et sur celui de cet amendement, par un harnais jetable qui écrivait, pour chaque
fichier, les deltas publiés, les diagnostics et `skippedRequirements` :

| Fichier (résumé) | `933a63fe` | Cet amendement |
|---|---|---|
| ligne 1 ```` ``` ````, puis `## ADDED Requirements`, `### Requirement: A` | `ADDED A` | rien ; `PARTIAL_INGESTION` nomme `A`, `UNCLOSED_CODE_FENCE` ligne 1 |
| `## REMOVED` / `Old`, bloc indenté jamais fermé, puis `## ADDED` / `B` | `REMOVED Old`, `ADDED B` | `REMOVED Old` ; `B` nommée, bloc nommé |
| `## ADDED` / `A` + scénario ouvrant un bloc jamais fermé, puis `B` | `ADDED A`, `ADDED B` | `ADDED A` ; `B` nommée, bloc nommé |
| `## REMOVED` / `Legacy…`, ```` ```inline``` ```` jamais fermé, `## Notes` / `Keep the audit trail` | `REMOVED Legacy…`, `REMOVED Keep the audit trail`, rien de signalé | `REMOVED Legacy…` ; `Keep the audit trail` nommée, bloc nommé |
| `## ADDED` / `A`, `## Notes`, exemple **fermé** avec `## ADDED` + `### Requirement: Phantom` | `ADDED A`, `ADDED Phantom` | `ADDED A` ; `Phantom` nommée (faux positif assumé, décision 1) |

Les trois premières lignes sont le coût de la décision 3 : un genre qui était juste est perdu, et l'exigence est
nommée au lieu d'être publiée. La quatrième est le défaut que la décision 3 corrige.

### Ce qui ne change pas — mesure du 30 septembre 2026

Mesurée avant toute modification du lecteur puis après, par un harnais jetable qui écrivait pour chaque fixture les
deltas (genre, clé, titre, énoncé, source, identifiant externe), leurs scénarios (étapes et identifiants externes), les
preuves (source, plage, empreinte SHA-256), les diagnostics et `skippedRequirements` d'`OpenSpecRequirementDeltaReader`,
puis le rapport `REQUIREMENT_DELTAS` et les diagnostics d'`OpenSpecSpecificationContentReader`. Les deux relevés sont
**identiques octet pour octet** sur les trois fichiers de delta lus (`openspec-basic/.../add-remember-me`,
`openspec-state-matrix/.../extend-timeout` et `.../shorten-timeout`) : trois deltas et huit preuves, puis deux deltas et
quatre preuves, aucun diagnostic, aucun bloc ouvert. Aucun de ces fichiers ne contient de bloc de code.

La catégorie de `openspec-state-matrix`, lue par `OpenSpecSpecificationContentReader`, est `FAILED` **avant comme
après** : ses changes n'ont pas de `proposal.md`, le groupe `changes` échoue et les deltas ne sont pas tentés. Ce n'est
pas l'effet de cet amendement ; c'est la raison pour laquelle le test qui fige ces fixtures lit le lecteur de deltas
directement.

Inchangés aussi (mesurés identiques avant et après) : un `#### Scenario:` écrit dans un bloc, et la ligne `## ` écrite
dans un bloc fermé au milieu du corps d'une exigence — deux défauts nommés ci-dessous —, le seul préfixe littéral `## `,
le contenu non normalisé de `RENAMED`, et `composition sync`, dont les diagnostics restent un compte par provider.

### Hors périmètre, laissé ouvert

- Un `### Requirement:` écrit dans un bloc **fermé** crée toujours une exigence fantôme : OpenSpec amont le masque,
  MORPHEUS non. Le masquer changerait des deltas que ce lot n'a pas mesurés sur des données réelles.
- `composition sync` et `composition status` ne portent toujours qu'un compte de diagnostics par provider.
- Dans `SyncResult`, `mode` et `fullRebuildReason` sont typés `string` sans énumération : ils étaient publiés non typés
  avant ce schéma, et `fullRebuildReason` vaut `none` hors de toute énumération Java.
- La projection locale ne borne la longueur d'une valeur que par le budget d'ingestion ; seule la projection remote
  applique `MAX_RELAYED_LENGTH`.
- Une valeur légitime qui ressemble à un chemin (un titre d'exigence contenant ` /admin`) est retirée de la projection
  remote : c'est le coût assumé d'un faux positif de `ServerLocationDisclosure`. La CLI la montre.
- Un `#### Scenario:` écrit dans un bloc, fermé ou non, reste rattaché à l'exigence précédente comme un vrai scénario
  (mesuré : `ADDED A` porte `[Real, Fenced]`, avant comme après). L'amont le masque (`requirement-text.ts`).
- Une ligne `## ` écrite dans un bloc **fermé** au milieu du corps d'une exigence ne fait pas que « tronquer » : tout le
  texte et **tous les scénarios** qui la suivent dans cette exigence sont perdus, **sans diagnostic** (`requirementEnd`
  n'utilise pas le masque). Mesuré : `The system SHALL also a2.` et le scénario `Lost` disparaissent, avant comme après.
  Le mot « troncature » de l'amendement du 24 septembre sous-estimait ce défaut.
- Un diagnostic `FATAL` est compté dans `diagnosticCount` mais ne bloque pas la publication :
  `ProjectSnapshotImportService` ne refuse que sur `ERROR` et ne relaie en avertissement que `WARNING`. Préexistant ;
  `BoundedDiagnostics` le place en tête de liste, sans changer ce qu'il bloque.

### Preuves exécutables ajoutées

- `OpenSpecRequirementDeltaReaderTest#anUnclosedFenceIsNamedAndNoRequirementAfterItInheritsAKind` — **remplace**
  `anUnclosedFenceMasksTheRestOfTheFileSoALaterSectionKeepsThePreviousKind`, même fichier, attente changée :
  avant, `[REMOVED Legacy session warning, REMOVED Keep the audit trail]`, aucun diagnostic, `skippedRequirements = 0` ;
  après, `[REMOVED Legacy session warning]`, `UNCLOSED_CODE_FENCE` ligne 7 (`fence` = ```` ``` ````),
  `PARTIAL_INGESTION` nommant `Keep the audit trail` ligne 11, `skippedRequirements = 1`.
- `#aDeltaSectionAfterAnUnclosedFenceIsMaskedSoItsRequirementIsNamedRatherThanGivenTheKindBeforeTheFence` — le cas que la
  décision 3 existe pour refuser.
- `#aRemovedSectionWrittenInACodeExampleDoesNotChangeTheKind` — décision 1.
- `#aWellFormedSectionAfterAFenceThatNeverClosesNoLongerPublishesItsRequirement` — épingle le coût de la décision 3
  (1.2.0 publiait `ADDED A`) ; tenu à la fois par la décision 1 et par la décision 3, il ne tombe que si les deux sont
  retirées.
- `#aDeltaSectionInAClosedExampleUnderAForeignSectionNamesTheExampleRequirementInsteadOfPublishingIt` — le faux positif
  assumé de la décision 1.
- `#theRepositoryDeltaFixturesReadExactlyAsFrozen` — fige la sortie des trois fichiers de delta du dépôt.
- `OpenSpecSpecificationContentReaderTest#anUnclosedFenceMakesTheCategoryPartialEvenWhenNoRequirementFollowsIt`.
- `BoundedDiagnosticsTest` — borne et raison de troncature, ordre, projection remote allowlistée, projection locale
  intacte.
- `MorpheusCliTest#aSyncNamesTheRequirementItSkippedAndTheFenceThatWasNeverClosed` et
  `#theSyncTextNamesTheTruncationReasonWhenTheDiagnosticsWereBounded` ;
  `MorpheusApiProjectSyncIntegrationTest#aSyncNamesTheRequirementItSkippedWithoutNamingTheServerWorkspace` — le nom
  lu sur la surface, par un vrai serveur HTTP et par la CLI.
- `SyncDiagnosticsContractTest` et deux lignes de `PublishedEnumsMatchJavaEnumsTest`.

Chaque moitié a été cassée une fois avant acceptation : `DELTA_SECTION` sans le masque fait tomber
`aRemovedSectionWrittenInACodeExampleDoesNotChangeTheKind` et le test du faux positif `Phantom` ; la règle de la
décision 3 retirée fait tomber les deux tests du bloc jamais fermé ; le diagnostic retiré, ces deux-là et le test de
catégorie ; le `PARTIAL` retiré, le test de catégorie seul ; la clé `diagnostics` retirée de la réponse HTTP, le test
HTTP ; les lignes `diagnostic=` retirées, le test CLI ; la projection remote remplacée par l'identité, le test de
projection ; `BoundedDiagnostics.remote` remplacé par `.local` dans `MorpheusProjectSyncApiService`, les deux règles
ArchUnit et le test HTTP ; `truncationReason` retiré du `required` publié de `BoundedDiagnostics`, le test de forme ;
`diagnostics` retiré du `required` de `SyncResult`, le test HTTP.
