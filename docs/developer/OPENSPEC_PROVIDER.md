# Provider OpenSpec — règles de lecture

Cette page décrit comment le provider OpenSpec (`morpheus-provider-openspec`) lit un espace de travail OpenSpec :
quels groupes il lit, comment il découpe un fichier Markdown, ce qu'il signale et ce qu'il ne sait pas lire. Elle
applique le contrat de lecture des providers, fixé par
[ADR-0028](../architecture/adr/BE-0028-unified-provider-read-contract.md) (statuts, diagnostics, racine publiée,
attribution des échecs, texte des refus) ; elle n'y déroge nulle part.

Lecteurs concernés, tous dans `com.morpheus.provider.openspec` :

| Classe | Rôle |
|---|---|
| `OpenSpecSpecificationProvider` | sonde : schéma déclaré, capacités |
| `OpenSpecSpecificationContentReader` | lecteur de contenu : lit les trois groupes et rend un rapport par catégorie |
| `OpenSpecCurrentSpecificationReader` | groupe `current` : `openspec/specs/**/spec.md` |
| `OpenSpecChangeMetadataReader` | groupe `changes` : `proposal.md`, contraintes, décisions de conception, tâches |
| `OpenSpecRequirementDeltaReader` | groupe `requirement-deltas` : `openspec/changes/<change>/specs/**/spec.md` |
| `OpenSpecCodeFences` | masque des blocs de code, partagé par les lecteurs courant et de deltas |
| `OpenSpecSourceAttribution` | rattachement d'un échec au fichier qui l'a causé |
| `OpenSpecProjectContentReader` | lecteur strict employé par `sync` |

## Sonde et capacités

La sonde lit `openspec/config.yaml` et en extrait la valeur de `schema:`. Une valeur entre guillemets simples ou
doubles est la même valeur que sans guillemets ; des guillemets dépareillés ne déclarent aucun schéma.

Les capacités annoncées dépendent des répertoires présents :

| Répertoire | Capacités |
|---|---|
| `openspec/specs/` | `READ_CURRENT_SPECIFICATIONS`, `READ_REQUIREMENTS`, `READ_SCENARIOS` |
| `openspec/changes/` | `READ_CHANGES`, `READ_DESIGN_DECISIONS`, `READ_IMPLEMENTATION_TASKS` |
| `openspec/changes/archive/` | `READ_HISTORY`, `READ_ARCHIVES` |

OpenSpec ne revendique jamais `READ_ACCEPTANCE_CRITERIA`.

## Groupes, catégories et statuts

Le lecteur de contenu lit trois groupes indépendamment, pour qu'un groupe en échec ne masque pas les autres :

| Groupe | Catégories |
|---|---|
| `current` | `CURRENT_SPECIFICATIONS`, `REQUIREMENTS`, `SCENARIOS` |
| `changes` | `CHANGES`, `CONSTRAINTS`, `DESIGN_DECISIONS`, `IMPLEMENTATION_TASKS` |
| `requirement-deltas` | `REQUIREMENT_DELTAS` |

- Une catégorie dont la capacité n'est pas annoncée par la sonde est `ABSENT`, comme une catégorie lue à zéro élément.
- Les deltas désignent des changements : si le groupe `changes` échoue, `REQUIREMENT_DELTAS` est `FAILED` lui aussi, et
  les deltas ne sont pas tentés.
- `SCENARIOS` est `PARTIAL` (`PARTIAL_INGESTION`) quand au moins une exigence courante n'a aucun scénario normalisé.
- Trois catégories ne sont jamais lues et rendent `UNSUPPORTED` (`OPTIONAL_CAPABILITY_UNAVAILABLE` quand elles sont
  demandées) :

  | Catégorie | Raison |
  |---|---|
  | `ACCEPTANCE_CRITERIA` | un scénario OpenSpec n'est pas un critère d'acceptance ; aucune conversion implicite |
  | `EXTERNAL_REFERENCES` | l'ingestion des références externes depuis OpenSpec n'est pas définie |
  | `ARCHIVES` | la normalisation des archives relève de la projection temporelle |

- Le répertoire `openspec/changes/archive/` n'est jamais lu comme un changement : `listChangeRoots` l'exclut.

La fixture `experiments/m0/fixtures/openspec-partial` illustre une lecture partielle : `CURRENT_SPECIFICATIONS` et
`REQUIREMENTS` sont `READ`, `SCENARIOS` est `PARTIAL`, `CHANGES` est `ABSENT`, et un `PARTIAL_INGESTION` est émis ; les
éléments valides restent publiés.

## Décodage des fichiers

Tout fichier est lu par `SafeWorkspaceFileResolver`, en UTF-8 strict. Un BOM UTF-8 initial est retiré au décodage, une
seule fois et seulement en tête : un second BOM, ou un BOM au milieu du texte, est du contenu. Un `spec.md` ou un
`config.yaml` écrit par un éditeur qui pose un BOM est donc lu comme sans BOM.

## Proposals : deux dialectes

Le lecteur de changements lit deux dialectes de `proposal.md` :

| | Dialecte d'origine | Gabarits `spec-driven` de la CLI OpenSpec |
|---|---|---|
| Titre | `# Proposal: <titre>` | `# Proposal`, sans titre : le titre est le nom du répertoire du changement |
| Intention | `## Intent` | `## Why` |
| Autres sections | contraintes, décisions | `## What Changes`, `## Capabilities`, `## Impact` |

Le titre vient de `# Proposal: <titre>` quand il est présent, sinon du nom du répertoire ; l'intention vient de
`## Intent` quand elle est présente, sinon de `## Why`. Un `proposal.md` qui n'a ni l'une ni l'autre est un échec du
changement. Les gabarits de la CLI n'ont pas de section de contraintes et écrivent les décisions de conception en texte
libre : pour un tel changement, `CONSTRAINTS` et `DESIGN_DECISIONS` sont `ABSENT`. Rien n'est inventé, rien n'échoue ;
lire ces décisions en texte libre n'est pas décidé.

La version de référence des gabarits est celle qui a produit la fixture `experiments/m0/fixtures/openspec-upstream-cli`
(générée par la CLI OpenSpec ; sa version et la procédure de régénération sont dans le `README.md` de la fixture).
Une évolution des gabarits se constate par un test en échec, pas chez un utilisateur.

## Masque des blocs de code

Les lecteurs courant et de deltas décident de la structure d'un fichier à travers un même masque, `OpenSpecCodeFences`.
Sa référence est OpenSpec amont (`buildCodeFenceMask`, `src/core/parsers/code-fence.ts`), pas CommonMark : c'est le
format pour lequel ces fichiers sont écrits.

- Un bloc s'ouvre sur une suite d'au moins trois ```` ` ```` ou `~`, précédée de n'importe quelle indentation et suivie
  de n'importe quoi.
- Il ne se ferme que sur une suite **du même caractère**, **au moins aussi longue**, suivie **uniquement d'espaces**.
- Les espaces sont ceux du `\s` de JavaScript (dont l'espace insécable), pas seulement l'ASCII.
- Les lignes de délimitation font partie du bloc.
- Un bloc jamais fermé masque le reste du fichier, comme chez OpenSpec amont. Une ligne de prose qui commence par
  ```` ``` ```` ou `~~~` suivie de texte suffit à en ouvrir un.

CommonMark limiterait l'indentation à trois espaces et refuserait une info string contenant un ```` ` ```` : suivre
CommonMark ferait disparaître, par exemple, une exigence placée après un bloc indenté de quatre espaces contenant
`## Overview` en colonne 0, qu'OpenSpec tient pour bien formé.

## Spécifications courantes

- Une exigence (`### Requirement: <titre>`) se termine à l'exigence suivante ou à la prochaine ligne qui commence par
  `## `. Un titre `### Requirement:` ou `## …` écrit dans un bloc de code n'ouvre ni ne ferme rien, pas plus que dans
  `## Purpose`.
- Un bloc jamais fermé dans une spécification courante est nommé par un `UNCLOSED_CODE_FENCE` (`WARNING`, détails
  `provider`, `specification`, `fence` — la suite qui l'a ouvert —, `line` — la ligne d'ouverture, base 1), et les
  catégories du groupe `current` passent `PARTIAL` (`[PARTIAL_INGESTION, UNCLOSED_CODE_FENCE]`) au lieu de perdre en
  silence les exigences masquées.
- Une exigence ou un scénario déclaré deux fois dans une même spécification est un échec du fichier.

## Deltas d'exigences

Un fichier de delta est découpé en sections de niveau deux.

1. **Sections normalisées.** `## ADDED Requirements`, `## MODIFIED Requirements` et `## REMOVED Requirements` (casse
   indifférente) posent le genre courant des exigences qui suivent.
2. **Une section termine la précédente.** Toute ligne qui commence par le préfixe littéral `## ` et n'est pas une
   section normalisée remet le genre courant à « aucun » : le genre ne traverse pas une section étrangère.
3. **`## RENAMED Requirements` est reconnue, pas normalisée.** C'est une des quatre sections du format de delta OpenSpec
   amont. Elle termine la section précédente sans avertissement ; son contenu (`FROM:` / `TO:`) n'est pas normalisé,
   `RequirementDeltaKind` n'ayant pas de genre de renommage.
4. **Le masque est symétrique.** Une ligne de section placée dans un bloc de code ne pose pas le genre et ne le remet
   pas à zéro : un exemple Markdown contenant `## Overview` ou `## REMOVED Requirements` ne change pas le genre des
   exigences qui suivent.
5. **Une section non reconnue est nommée** : `UNRECOGNIZED_SECTION` (`WARNING`, détail `section` = le titre). Une section
   non reconnue qui ne contient aucune exigence laisse la catégorie `READ` : rien n'a été perdu que le modèle sache
   porter.
6. **Une exigence sans genre est sautée et nommée** : `PARTIAL_INGESTION` (`WARNING`, détail `requirement` = le titre),
   quand elle est placée hors de toute section de delta.
7. **Un bloc jamais fermé est nommé**, et aucune exigence placée après sa ligne d'ouverture ne reçoit de genre, même
   sous un en-tête de delta bien formé : après l'ouverture, tout titre de section est masqué, et le genre en vigueur ne
   serait qu'une supposition. Le fichier produit un `UNCLOSED_CODE_FENCE` (détails `fence` et `line`), et chaque
   exigence qui suit est sautée et nommée par un `PARTIAL_INGESTION` dont le message dit qu'elle suit un bloc jamais
   fermé. OpenSpec amont perd ces exigences en silence ; MORPHEUS les perd aussi, et les nomme.
8. **La catégorie passe `PARTIAL`** dès qu'une exigence est sautée ou qu'un bloc reste ouvert, même si aucune exigence
   ne suit le bloc : codes `PARTIAL_INGESTION`, plus `UNCLOSED_CODE_FENCE` pour un bloc ouvert.
9. Une exigence déclarée deux fois dans un delta, ou un scénario déclaré deux fois, est un échec du fichier.

Tous ces avertissements portent les détails `provider`, `change` et `line` (base 1), et une `source` relative à la racine
du workspace, jamais absolue. Leur `source` est la valeur du locator du fichier (voir « Noms dans les diagnostics »).

Exemples du comportement voulu :

| Fichier (résumé) | Publié | Signalé |
|---|---|---|
| `## REMOVED Requirements` / `Old`, puis `## Notes` / `A` | `REMOVED Old` | `UNRECOGNIZED_SECTION` `Notes`, `PARTIAL_INGESTION` `A` |
| ligne 1 ```` ``` ```` jamais fermé, puis `## ADDED Requirements`, `### Requirement: A` | rien | `UNCLOSED_CODE_FENCE` ligne 1, `PARTIAL_INGESTION` `A` |
| `## ADDED` / `A` + scénario ouvrant un bloc jamais fermé, puis `B` | `ADDED A` | bloc nommé, `B` nommée |
| `## ADDED` / `A`, `## Notes`, exemple **fermé** contenant `## ADDED Requirements` et `### Requirement: Phantom` | `ADDED A` | `PARTIAL_INGESTION` `Phantom` |

La dernière ligne est un faux positif assumé : la section de l'exemple est masquée, le genre reste « aucun » après
`## Notes`, mais le titre d'exigence de l'exemple n'est pas masqué (voir « Écarts connus »), donc la catégorie passe
`PARTIAL` à cause d'un exemple de code.

Les trois fichiers de delta des fixtures du dépôt (`openspec-basic/.../add-remember-me`,
`openspec-state-matrix/.../extend-timeout` et `.../shorten-timeout`) ne contiennent aucun bloc de code et se lisent sans
aucun diagnostic ; leur sortie est figée par un test.

## Échecs, attribution et rejet par changement

- **Un échec est attribué au fichier qui l'a causé.** Chaque lecteur normalise ses fichiers un par un et rattache
  l'échec au fichier par `OpenSpecSourceAttribution`, au fichier le plus interne (le `proposal.md` d'un changement sans
  intention, le fichier de conception d'une décision sans corps, le fichier de delta d'un delta mal formé). Seuls trois
  échecs sont attribués, chacun gardant son type : `IllegalArgumentException`, `IllegalStateException`,
  `UncheckedIOException` (sur la même `IOException`). Tout autre échec passe inchangé et non attribué.
- **Le diagnostic** est un `INVALID_SOURCE` dont la `source` est le chemin du fichier relatif à la racine du workspace,
  écrit par `WorkspaceRelativePathText`, et dont le message porte ce chemin et la cause. Un message d'échec écrit par
  un lecteur pour un fichier ne porte pas son chemin absolu : l'attribution fournit le chemin relatif. Une cause dont
  le texte nomme un emplacement du serveur est remplacée par son type.
- **Aucune exception ne sort de `read()` pour un contenu lu** : un doublon, un fichier sans titre, une proposal sans
  intention produisent un `FAILED` attribué.
- **Groupe `current`** : il est lu d'un seul appel ; un fichier en échec fait tomber le groupe, `FAILED`, sans
  qu'aucune spécification déjà normalisée ne soit conservée.
- **Groupe `changes`** : chaque changement est lu séparément. Un changement dont la lecture échoue pour une raison
  attribuée à l'un de ses fichiers est rejeté **en entier** (rien de ce qui a été lu avant l'échec n'est publié) et
  nommé par un `INVALID_SOURCE` dont la source est ce fichier ; les autres changements sont publiés. `CHANGES`,
  `CONSTRAINTS`, `DESIGN_DECISIONS` et `IMPLEMENTATION_TASKS` passent alors `PARTIAL` (`[PARTIAL_INGESTION,
  INVALID_SOURCE]`), **même à zéro élément** : ce que portait le changement rejeté est inconnu, son silence n'est pas une
  absence. Ils restent `FAILED` si aucun changement n'a pu être lu. Un échec qui n'est attribuable à aucun fichier
  (défaut, magasin d'identités) fait échouer tout le groupe.
- **Deltas d'un changement rejeté** : ils sont retirés avec leurs preuves, puisqu'ils désigneraient un changement non
  publié, et `REQUIREMENT_DELTAS` passe `PARTIAL` (`[PARTIAL_INGESTION, INVALID_SOURCE]`) quand il en retire.
- **Le lecteur strict de `sync`** (`OpenSpecProjectContentReader`) ne rejette pas changement par changement : il refuse
  l'espace de travail entier.

## Noms dans les diagnostics et budget de preuve

- Le texte d'un refus nomme le fichier tel qu'il est (`WorkspaceRelativePathText`) ; le locator enregistré dans les
  deltas, les spécifications et les preuves est `SourceLocator.file(...)`, qui réécrit `\` en `/`. Les deux s'accordent
  pour tout chemin sans `\`, ce qui est le cas de tout chemin que ces lecteurs produisent sous Windows.
- Les trois lecteurs citent un fragment de preuve par `ProviderIngestionBudget.Session.addEvidenceFragment(String)`,
  dans la méthode qui vient de lire le fichier : le refus de budget de preuve nomme alors le dernier document lu par la
  session, sans passer par le locator.
- `specificationKey` (`relativeParent.toString().replace('\\', '/')`) est une clé d'identité externe
  (`specification:<clé>`), pas un texte de refus.
- La `source` des avertissements `UNRECOGNIZED_SECTION` et `PARTIAL_INGESTION` du lecteur de deltas, et de
  `UNCLOSED_CODE_FENCE`, est la valeur du locator du fichier : elle s'accorde avec le texte d'un refus pour tout nom
  sans `\`.

## Écarts connus avec OpenSpec amont

Ces comportements sont connus et non corrigés ; les changer modifierait des deltas publiés.

- **Un `### Requirement:` écrit dans un bloc de code d'un delta crée une exigence.** OpenSpec amont le masque
  (`requirement-blocks.ts`) ; le lecteur courant le masque, le lecteur de deltas non.
- **Dans un delta, la fin d'une exigence ne consulte pas le masque.** Une ligne `## ` écrite dans un bloc **fermé** au
  milieu du corps d'une exigence la termine : tout le texte et tous les scénarios qui la suivent dans cette exigence
  sont perdus, sans diagnostic ; le genre est conservé et la section continue. Le lecteur courant, lui, ignore une telle
  ligne.
- **Un `#### Scenario:` écrit dans un bloc**, fermé ou non, reste rattaché à l'exigence qui le contient comme un vrai
  scénario, dans les deux lecteurs. L'amont le masque (`requirement-text.ts`).
- **Seul le préfixe littéral `## ` termine une section.** `##<TAB>Notes` et un titre indenté `   ## Notes` sont des
  titres ATX valides en CommonMark mais ne terminent rien : une exigence placée dessous hérite du genre précédent.
- **Le contenu d'une section `RENAMED`** n'est ni normalisé ni signalé.
- **Un en-tête de delta bien formé placé après un bloc jamais fermé** ne publie pas ses exigences (règle 7 des deltas) ;
  ne masquer que les blocs fermés rendrait le genre, mais s'écarterait à la fois de l'amont et de CommonMark, pour qui un
  bloc non fermé se termine à la fin du document.

## Tests

- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecRequirementDeltaReaderTest.java` :
  sections, masque, gardes de fermeture (longueur, caractère, texte après la suite, espace insécable), bloc jamais
  fermé, faux positif `Phantom`, sortie figée des fixtures (`theRepositoryDeltaFixturesReadExactlyAsFrozen`).
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecCurrentSpecificationReaderTest.java` :
  masque dans les spécifications courantes, section de niveau deux après la dernière exigence.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecSpecificationContentReaderTest.java` :
  statuts par catégorie, espace de travail produit par la CLI (`aWorkspaceProducedByTheOpenSpecCliIsRead`), doublons,
  rejet par changement (`oneMalformedChangeNamesItsProposalAndLeavesTheValidChangeReadAsPartial`), BOM, attribution.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecChangeMetadataReaderTest.java` : les
  deux dialectes de proposal.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecSpecificationProviderTest.java` :
  schéma entre guillemets.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecProjectContentReaderTest.java` et
  `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecRefusedFileNameTest.java` : type
  des échecs attribués, nom d'un fichier à `\`.
- `morpheus-application/src/test/java/com/morpheus/application/files/SafeWorkspaceFileResolverTest.java` : retrait d'un
  seul BOM initial.
