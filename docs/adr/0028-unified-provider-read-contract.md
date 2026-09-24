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
- `OpenSpecSpecificationContentReaderTest#aSkippedRequirementDeltaMakesTheCategoryPartialInsteadOfRead` et
  `#aLevelTwoLineInsideACodeFenceLeavesTheCategoryRead`.
