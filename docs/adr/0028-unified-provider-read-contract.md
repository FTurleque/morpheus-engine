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

**L'attribution garde la catégorie de sa cause.** Un contenu invalide reste une `IllegalArgumentException`, tout autre
échec devient une `IllegalStateException` ; un dépassement de budget n'est pas attribué — il nomme déjà sa source en
relatif, et ses appelants abandonnent la lecture entière. Les surfaces qui distinguent ces deux catégories (codes de
sortie CLI, statut HTTP de la synchronisation) répondent comme avant.

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
