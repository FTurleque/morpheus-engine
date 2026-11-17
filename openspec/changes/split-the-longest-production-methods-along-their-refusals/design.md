# Conception : découper les plus longues méthodes de production selon leurs refus (AUD-QUA-35)

<!-- Conception allégée : refactorisation interne de trois méthodes, sans comportement spécifié nouveau. -->

## Contexte

- [EXISTANT VÉRIFIÉ] `LocalSourceInventoryScanner.scanRoot` (`com.morpheus.application.sync`) passe à
  `Files.walkFileTree` un `SimpleFileVisitor` anonyme. Le refus `WORKSPACE_BOUNDARY_ESCAPED` (ajout d'un
  `SourceInventoryScanResult.Failure` avec `display(workspace, chemin)` et `FailureText.of(échec)`, puis
  `budget.exhaust()`) y est écrit quatre fois : sur la racine (avant le parcours, `return`), dans `preVisitDirectory`
  et deux fois dans `visitFile` (`TERMINATE`). Le refus de profondeur (`limitFailure` puis `budget.exhaust()`) y est
  écrit deux fois.
- [EXISTANT VÉRIFIÉ] `ApplicationTechnologyBoundaryArchitectureTest` (ARCH-0150) confine l'accès fichier de
  l'application aux classes qu'il nomme, dont `sync.LocalSourceInventoryScanner`, `sync.ScanWorkspaceBoundary` et
  `sync.SourceFingerprint` ; une classe est reconnue par son nom de premier niveau (`TechnologyFamily.topLevelName`),
  donc une classe imbriquée compte sous sa classe englobante. Une nouvelle classe de premier niveau qui atteint
  `java.nio.file.Files` exigerait un ADR consolidé qui remplace ARCH-0150.
- [EXISTANT VÉRIFIÉ] Les tests du scan sont `LocalSourceInventorySecurityTest`, `IntermediateSymlinkBoundaryTest`,
  `ScanWorkspaceBoundaryTest`, `WorkspacePathBoundaryTest`, `PartialSourceInventoryContractTest` et
  `SourceInventoryScanDisclosureTest` (`morpheus-application`).
- [EXISTANT VÉRIFIÉ] `SyntheticSpecificationContentReader.normalize` résout les identités par
  `identities.resolve(providerId(), <sorte>, <clé externe>)` ; les clés de scénario synthétiques sont persistées
  (`.claude/rules/code-style.md`, `persistedScenarioKey`) : l'ordre et la dérivation ne doivent pas bouger.
- [EXISTANT VÉRIFIÉ] `ChangeCompletenessService.assessChange` calcule successivement l'état des contraintes, celui de
  l'implémentation, puis la liste ordonnée des constats.

## Exigences couvertes

Sans objet : `skip_specs: true`, aucune exigence ne change. Les exigences existantes que la refactorisation doit
laisser vraies sont celles de `workspace-boundary-scan-cost`, en particulier « A source scan SHALL refuse every path
that leaves the workspace physically » et « A segment replaced by a link during a scan MUST still be refused before
the file enters the inventory ».

## Décisions

### D1 : le visiteur devient une classe imbriquée nommée, le refus de frontière une méthode
- **Nature** : [DÉCISION]
- **Exigences servies** : `workspace-boundary-scan-cost` › A source scan SHALL refuse every path that leaves the workspace physically (inchangée)
- **Choix** : une classe `private final` imbriquée dans `LocalSourceInventoryScanner` porte le parcours (racine,
  frontière, budget, entrées, échecs en champs) ; ses méthodes `boundaryEscaped(chemin, échec)` et
  `depthExceeded(chemin)` écrivent le refus et épuisent le budget ; `visitFile` se découpe en contrôle de frontière et
  de budget, puis empreinte vérifiée. Le refus sur la racine appelle la même `boundaryEscaped`.
- **Alternatives écartées** : une classe de premier niveau (exigerait un nom de plus dans la liste d'ARCH-0150, donc
  un ADR consolidé) ; une simple méthode privée sans extraire le visiteur (laisse `scanRoot` au-delà de cent lignes).
- **Compromis** : le fichier garde sa longueur ; seules les méthodes raccourcissent.
- **ADR** : non concerné ; ARCH-0150 respecté sans changement.

### D2 : `normalize` et `assessChange` découpées par catégorie, ordre conservé
- **Nature** : [DÉCISION]
- **Exigences servies** : aucune exigence modifiée.
- **Choix** : une méthode privée par catégorie lue (spécification, exigences et scénarios, critères, changements) pour
  `normalize`, par volet (contraintes, implémentation, constats) pour `assessChange` ; les collections sont passées et
  remplies dans le même ordre qu'aujourd'hui.
- **Alternatives écartées** : un objet de normalisation par catégorie (abstraction prématurée,
  `.claude/rules/code-style.md`).
- **Compromis** : aucun.
- **ADR** : non concerné.

### D3 : pas de gate de longueur
- **Nature** : [DÉCISION]
- **Exigences servies** : aucune.
- **Choix** : aucun gate n'est ajouté ; l'audit ne le propose pas.
- **Alternatives écartées** : un compteur des méthodes de plus de 60 lignes qui ne ferait que baisser (se satisfait
  en découpant mécaniquement, raison pour laquelle ARCH-0149 a écarté une borne de taille).
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| 51 méthodes (audit) contre 52 (revérification) | `AUD-QUA-35` | scan de revérification, méthodes de classes anonymes comprises | sans objet : différence de méthode de comptage, aucune décision n'en dépend |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| `workspace-boundary-scan-cost` › A source scan SHALL refuse every path that leaves the workspace physically | ses scénarios | tests du scan listés au Contexte, inchangés | existant |
| idem | les quatre sites rendent le même refus | `LocalSourceInventoryScanBoundaryRefusalTest` (à écrire, avant D1) : pour chaque site, code, texte, chemin affiché et budget épuisé | à écrire |
| identités synthétiques inchangées | lecture de `synthetic-basic` | `SyntheticSpecificationContentReaderTest`, `PublicPayloadConvergenceTest` | existant |
| constats de complétude inchangés | ordre et contenu | `QualityReportServiceTest`, `QualityThresholdFactTest`, `ChangeCompletenessAbsentObservationContractTest`, `ChangeLifecycleQualityContractTest` | existant |
| `ApplicationTechnologyBoundaryArchitectureTest` vert | — | `./mvnw test -pl morpheus-architecture-tests` | existant |

## Documentation durable et diagrammes à mettre à jour

`docs/developer/specifications/workspace-boundary-scan-cost.md` : relire la description du parcours si elle nomme le
visiteur anonyme ; aucune autre page.

## Questions ouvertes

Un gate de longueur reste possible si le mainteneur le demande (D3) ; il ne change pas ce changement.
