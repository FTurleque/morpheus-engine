# ADR-0037 — Domaine de traçabilité et taxonomie contrôlée

- Statut : **Proposée — M4**
- Date : 23 juillet 2026
- Dépend de : ADR-0005, ADR-0009, ADR-0010, ADR-0012, ADR-0031, ADR-0033, ADR-0036
- Portée : M4-S1, modèle de domaine de traçabilité

## Contexte

M0 a déjà démontré avec E06/E06b qu'une traçabilité typée, directionnelle, explicable et traversable peut fonctionner en mémoire et sur SQLite sans backend graphe dédié.

M3 a ensuite stabilisé des invariants qui n'existaient pas encore sous leur forme de production pendant ces spikes :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
ACTIVE observable atomiquement
published history = RETIRED* -> ACTIVE
```

M4 doit donc reconstruire le modèle de traçabilité sur ces fondations, sans copier le spike Python et sans inventer une nouvelle identité implicite.

## Décision S1

S1 introduit uniquement le **domaine provider-neutral** de la traçabilité :

```text
TraceabilityLinkId
TraceabilityEntityKind
TraceabilityEntityRef
TraceabilityRelationType
TraceabilityLinkOrigin
TraceabilityResolutionState
TraceabilityConfidence
TraceabilityTransitivityPolicy
TraceabilitySemanticClass
TraceabilityLink
```

La persistance, le membership snapshot, la dérivation provider/model, les traversées et les références externes non résolues restent dans les slices suivantes.

## Identité du lien

`TraceabilityLinkId` est une identité MORPHEUS explicite basée sur `DomainIdentity`.

Invariant :

```text
TraceabilityLinkId != (source, relation, target) hash
TraceabilityLinkId != EntityVersionId
TraceabilityLinkId != KnowledgeSnapshotId
```

Le domaine n'invente jamais une identité de lien à partir du contenu de l'arête. Un appelant peut créer explicitement un nouvel identifiant, mais aucune normalisation ou persistance S1 ne génère un ID caché.

Deux liens sémantiquement similaires peuvent être deux observations distinctes si leurs identités/provenances diffèrent. La politique de déduplication snapshot-scoped sera figée avec la persistance S2.

## Endpoints internes typés

S1 représente un endpoint interne par :

```text
TraceabilityEntityRef
├── kind: TraceabilityEntityKind
└── identity: DomainIdentity
```

Les kinds S1 correspondent uniquement aux concepts déjà matérialisés dans le domaine de production :

```text
PROJECT
SPECIFICATION
REQUIREMENT
SCENARIO
CHANGE
REQUIREMENT_DELTA
CONSTRAINT
DESIGN_DECISION
IMPLEMENTATION_TASK
EXTERNAL_REFERENCE
```

`AcceptanceCriterion` n'est pas ajouté artificiellement comme kind S1 tant qu'il n'existe pas comme entité normalisée de production.

Le kind rend la référence interrogeable sans transformer `DomainIdentity` en identifiant typé universel.

## Taxonomie contrôlée

`TraceabilityRelationType` est fermé dans le cœur métier :

```text
REFINES
DERIVES_FROM
CONSTRAINS
SATISFIES
IMPLEMENTS
VALIDATES
VERIFIED_BY
DEPENDS_ON
AFFECTS
DECIDED_BY
SUPERSEDES
LINKS_TO_CODE
LINKS_TO_TEST
RELATED_TO
```

Aucune relation libre `String` n'est autorisée dans `TraceabilityLink`.

Chaque type porte au minimum :

```text
semantic class
transitivity policy
inverse query name éventuel
```

Les `allowed source kinds / target kinds` ne deviennent pas encore des contraintes rigides S1 : les relations réelles du corpus de production seront éprouvées pendant S3 avant de figer ces cardinalités sémantiques. Cette retenue évite de transformer les exemples C0 en contraintes non prouvées.

## Direction canonique et inverse

La direction du lien stocké est significative.

Exemples :

```text
Scenario REFINES Requirement
Constraint CONSTRAINS Change
Change DECIDED_BY DesignDecision
ImplementationTask IMPLEMENTS Requirement
Change AFFECTS Requirement
```

Une relation inverse est une **vue de requête**, pas une deuxième preuve ni une deuxième arête physique.

Invariant :

```text
inverse view != persisted duplicate link
```

S1 expose uniquement le nom inverse lorsqu'il est défini. L'algorithme incoming/outgoing appartient à S4.

## Origine, résolution et confiance

Le type de relation ne code ni la provenance ni la confiance.

```text
TraceabilityLinkOrigin
- EXPLICIT
- DERIVED
- HEURISTIC
```

```text
TraceabilityResolutionState
- RESOLVED
- PARTIALLY_RESOLVED
- UNRESOLVED
- HEURISTIC
```

Ainsi :

```text
HEURISTIC_IMPLEMENTS  // interdit

IMPLEMENTS
origin = HEURISTIC
resolution = HEURISTIC
confidence = 0.74
```

`TraceabilityConfidence` est une valeur bornée inclusive `[0.0, 1.0]`.

Une confiance est obligatoire si `origin == HEURISTIC` ou `resolution == HEURISTIC`.

Une confiance reste optionnelle dans les autres cas et ne change jamais la relation métier.

## Evidence

Tout `TraceabilityLink` S1 possède au moins un `EvidenceId`.

Raison : M4 a pour objectif de produire un graphe **explicable**, pas seulement navigable.

Invariant :

```text
TraceabilityLink without evidence = invalid
```

Plusieurs preuves peuvent soutenir la même observation ; S1 les conserve sous forme d'un ensemble immuable sans doublon.

## Temps d'observation

`observedAt` représente l'instant où MORPHEUS a observé/produit le lien.

Il ne remplace ni :

```text
SpecificationVersion
KnowledgeSnapshot
TemporalState
```

Le membership version/snapshot sera explicite en S2.

## Sémantique des relations S1

Classes :

```text
STRUCTURAL       REFINES, DERIVES_FROM
CONSTRAINT       CONSTRAINS
REALIZATION      IMPLEMENTS, SATISFIES
VERIFICATION     VALIDATES, VERIFIED_BY
DECISION         DECIDED_BY
DEPENDENCY       DEPENDS_ON
IMPACT           AFFECTS
HISTORY          SUPERSEDES
EXTERNAL         LINKS_TO_CODE, LINKS_TO_TEST
WEAK_ASSOCIATION RELATED_TO
```

Transitivité :

```text
NON_TRANSITIVE : CONSTRAINS, IMPLEMENTS, SATISFIES, VALIDATES,
                 VERIFIED_BY, DECIDED_BY, AFFECTS,
                 LINKS_TO_CODE, LINKS_TO_TEST, RELATED_TO

CONTEXTUAL      : REFINES, DERIVES_FROM, DEPENDS_ON, SUPERSEDES
```

S1 n'introduit aucune relation `TRANSITIVE` universelle : une traversée possible ne signifie pas qu'une nouvelle arête métier transitive peut être affirmée.

## Frontières

S1 ne fait pas encore :

```text
- table SQLite
- TraceabilityStore
- membership KnowledgeSnapshot
- outgoing/incoming
- traversal/path
- dérivation depuis NormalizedProjectContent
- résolution MINOS/GitHub/Jira
- fuzzy matching
- LLM/embedding
```

Aucune dépendance provider, SQLite, CLI, MINOS, NEXUS ou JARVIS n'entre dans le domaine.

## ADR historiques

ADR-0005 et ADR-0010 ont été acceptées par les preuves M0 (`E06` + `E06b`) et l'index ADR les enregistre déjà comme telles. Leurs fichiers conservent encore un ancien header C0 « Proposée » ; M4 corrige cette incohérence documentaire sans rouvrir les décisions M0.

ADR-0037 ne remplace pas ADR-0005/0010. Elle opérationnalise leur modèle sur les invariants de production acquis jusqu'à M3.

## Critères d'acceptation S1

ADR-0037 pourra passer à **Acceptée — M4** lorsque le gate local complet démontre :

1. `TraceabilityLinkId` reste distinct de l'identité des endpoints ;
2. aucune relation libre string n'entre dans le cœur ;
3. les 14 relations contrôlées sont présentes ;
4. chaque relation expose classe sémantique et politique de transitivité ;
5. la direction canonique est conservée ;
6. une inverse éventuelle est une métadonnée de requête, pas une seconde arête ;
7. origin, resolution et confidence sont orthogonaux au type ;
8. confidence est bornée `[0,1]` ;
9. les liens heuristiques exigent une confidence ;
10. chaque lien exige au moins une evidence ;
11. les collections d'evidence sont immuables et dédupliquées ;
12. le domaine n'importe aucun type provider/store/adapter ;
13. aucune persistance ou traversée n'est introduite prématurément ;
14. `\.\mvnw.cmd clean test` est vert à partir de la baseline M3 `147/147`.

## Preuve d'acceptation

À compléter uniquement après exécution du gate local complet.