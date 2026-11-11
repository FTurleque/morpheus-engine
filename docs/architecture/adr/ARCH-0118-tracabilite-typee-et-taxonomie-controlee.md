---
id: ARCH-0118
title: "Tracer par des liens typés selon une taxonomie contrôlée"
domain: architecture
rules: false
---

# Tracer par des liens typés selon une taxonomie contrôlée

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0005, ADR-0010, ADR-0037

## Context

La valeur de MORPHEUS ne tient pas au seul stockage de documents de spécification. Le moteur doit répondre à des
questions comme : quelle exigence justifie cette tâche, quels critères valident cette exigence, quelle décision de
conception explique ce changement, quel changement remplace une règle précédente, pourquoi cet élément apparaît dans le
contexte transmis à un agent. Ces questions exigent des relations explicites entre concepts. Des liens laissés
implicites dans le texte, ou recalculés à chaque requête, font perdre l'explication et le contrôle de couverture.

Un graphe générique sans vocabulaire fermé dégénère en relations floues, redondantes ou contradictoires : plusieurs
providers ou développeurs nommeraient la même relation `IMPLEMENTS`, `IMPLEMENTED_BY`, `REALIZES`, `SATISFIES` ou
`FULFILLS`, et les requêtes, les traversées, la couverture et les intégrations deviendraient incohérentes. Deux
providers qui observent la même relation métier doivent produire la même sémantique MORPHEUS, et une nouvelle relation
ne doit pas exiger une refonte du store.

Options écartées :

- **relations uniquement dans le texte** : impossibles à interroger de façon déterministe sans retraitement ;
- **colonnes ou clés étrangères propres à chaque entité** : conviennent à certaines relations mais deviennent rigides
  quand les types de liens évoluent ;
- **relation libre de type `String`** : trop souple, impossible à gouverner durablement ;
- **une classe dédiée par relation** : trop rigide et coûteuse à faire évoluer pour un stockage générique.

L'option retenue est un lien générique mais typé, dont le type est pris dans une énumération fermée.

## Decision

### Le lien est un concept de premier ordre

`com.morpheus.domain.traceability.TraceabilityLink` est un record immuable et provider-neutral qui porte :

| Champ | Rôle |
|---|---|
| `id` | `TraceabilityLinkId`, identité MORPHEUS fondée sur `DomainIdentity` |
| `source`, `target` | `TraceabilityEntityRef` : un type d'entité et une `DomainIdentity` |
| `relationType` | `TraceabilityRelationType`, pris dans la taxonomie fermée |
| `origin` | `TraceabilityLinkOrigin` |
| `resolution` | `TraceabilityResolutionState` |
| `confidence` | `TraceabilityConfidence` optionnelle |
| `evidenceIds` | ensemble immuable et non vide d'`EvidenceId` |
| `observedAt` | instant où MORPHEUS a observé ou produit le lien |

`TraceabilityLinkId` n'est ni un hachage de `(source, relation, target)`, ni un `EntityVersionId`, ni un
`KnowledgeSnapshotId`. Le domaine ne dérive jamais l'identité d'un lien du contenu de l'arête : deux observations
sémantiquement proches peuvent garder deux identités distinctes. La déduplication relève de la persistance par snapshot
(ADR-0038) et de la dérivation déterministe (ADR-0039).

`observedAt` ne remplace ni `SpecificationVersion`, ni `KnowledgeSnapshot`, ni `TemporalState` : l'appartenance d'un
lien à un snapshot est explicite et relève d'ADR-0038.

### Types d'entités traçables

Les extrémités d'un lien sont typées par `TraceabilityEntityKind` : `PROJECT`, `SPECIFICATION`, `REQUIREMENT`,
`SCENARIO`, `CHANGE`, `REQUIREMENT_DELTA`, `CONSTRAINT`, `DESIGN_DECISION`, `IMPLEMENTATION_TASK`,
`ACCEPTANCE_CRITERION`, `EVIDENCE` et `EXTERNAL_REFERENCE`. Une même `DomainIdentity` sous deux types différents donne
deux références distinctes. `ACCEPTANCE_CRITERION` et `EVIDENCE` participent à la traçabilité parce que critères et
preuves de vérification sont des entités de production (ADR-0081). Un type d'entité n'est ajouté que pour une entité
normalisée de production.

Les couples source/cible admis par une relation ne sont pas des contraintes du domaine : `TraceabilityLink` n'en
vérifie aucun. Les cardinalités typiques (une exigence a plusieurs critères, un changement plusieurs tâches) se
contrôlent par diagnostics, pas par des contraintes de stockage.

### Taxonomie fermée

`TraceabilityRelationType` est une énumération fermée ; aucune relation libre de type `String` n'entre dans
`TraceabilityLink`. Chaque relation porte une classe sémantique, une politique de transitivité et le nom de sa vue
inverse.

| Relation | Classe sémantique | Transitivité | Nom inverse |
|---|---|---|---|
| `REFINES` | `STRUCTURAL` | `CONTEXTUAL` | `REFINED_BY` |
| `DERIVES_FROM` | `STRUCTURAL` | `CONTEXTUAL` | `DERIVES` |
| `CONSTRAINS` | `CONSTRAINT` | `NON_TRANSITIVE` | `CONSTRAINED_BY` |
| `SATISFIES` | `REALIZATION` | `NON_TRANSITIVE` | `SATISFIED_BY` |
| `IMPLEMENTS` | `REALIZATION` | `NON_TRANSITIVE` | `IMPLEMENTED_BY` |
| `VALIDATES` | `VERIFICATION` | `NON_TRANSITIVE` | `VALIDATED_BY` |
| `VERIFIED_BY` | `VERIFICATION` | `NON_TRANSITIVE` | `VERIFIES` |
| `DEPENDS_ON` | `DEPENDENCY` | `CONTEXTUAL` | `DEPENDENCY_OF` |
| `AFFECTS` | `IMPACT` | `NON_TRANSITIVE` | `AFFECTED_BY` |
| `DECIDED_BY` | `DECISION` | `NON_TRANSITIVE` | `DECIDES` |
| `SUPERSEDES` | `HISTORY` | `CONTEXTUAL` | `SUPERSEDED_BY` |
| `LINKS_TO_CODE` | `EXTERNAL` | `NON_TRANSITIVE` | `CODE_LINKED_FROM` |
| `LINKS_TO_TEST` | `EXTERNAL` | `NON_TRANSITIVE` | `TEST_LINKED_FROM` |
| `RELATED_TO` | `WEAK_ASSOCIATION` | `NON_TRANSITIVE` | `RELATED_TO` |

Sémantique des relations proches :

- `IMPLEMENTS` relie un artefact de travail ou d'exécution à l'intention qu'il réalise ; `SATISFIES` exprime une
  conformité fonctionnelle ou logique plus générale ;
- `VALIDATES` décrit le rôle d'un critère ; `VERIFIED_BY` décrit une preuve ou un mécanisme de vérification concret ;
- `SUPERSEDES` reste compatible avec le versionnement et ne duplique pas mécaniquement les liens de prédécesseur de
  snapshot ;
- `RELATED_TO` est volontairement faible et s'emploie avec parcimonie.

Exemples de direction canonique : `Scenario REFINES Requirement`, `Constraint CONSTRAINS Change`,
`Change DECIDED_BY DesignDecision`, `ImplementationTask IMPLEMENTS Requirement`, `Change AFFECTS Requirement`,
`AcceptanceCriterion VALIDATES Requirement`.

### Direction, inverse et transitivité

La direction du lien stocké est significative. Une relation inverse est une **vue de requête** : son nom est exposé
par `inverseQueryName()`, elle n'est jamais persistée comme seconde arête et ne compte jamais comme une seconde preuve
indépendante.

Il n'existe que deux politiques de transitivité, `NON_TRANSITIVE` et `CONTEXTUAL`. Aucune relation n'est transitive de
façon universelle : qu'une chaîne soit traversable ne permet pas d'affirmer une nouvelle arête métier, et le moteur de
requête n'applique jamais la transitivité au seul motif que la relation est stockée dans un graphe. La traversée
bornée relève d'ADR-0040.

### Trois dimensions orthogonales au type

Le type de relation ne code ni la provenance, ni la résolution, ni la confiance. `HEURISTIC_IMPLEMENTS` est
interdit ; on écrit `IMPLEMENTS` avec `origin = HEURISTIC`, `resolution = HEURISTIC` et une confiance.

| Dimension | Valeurs | Question |
|---|---|---|
| `TraceabilityLinkOrigin` | `EXPLICIT`, `DERIVED`, `HEURISTIC` | comment MORPHEUS a obtenu le lien |
| `TraceabilityResolutionState` | `RESOLVED`, `PARTIALLY_RESOLVED`, `UNRESOLVED`, `HEURISTIC` | la cible est-elle résolue |
| `TraceabilityConfidence` | réel fini dans `[0.0, 1.0]` | quelle confiance accorder au lien |

- `EXPLICIT` : déclaré par la source ou par un identifiant non ambigu ;
- `DERIVED` : calculé de façon déterministe à partir de faits fiables ;
- `HEURISTIC` : hypothèse utile mais non certaine.

Une confiance est obligatoire dès que l'origine ou la résolution vaut `HEURISTIC`. Un lien heuristique n'est jamais
présenté comme explicite.

### Preuve et liens non résolus

Tout lien porte au moins un `EvidenceId` ; plusieurs preuves d'une même observation forment un ensemble immuable sans
doublon. Deux liens d'apparence identique peuvent avoir des provenances différentes (déclaration du provider,
convention de structure, inférence MORPHEUS) et le consommateur doit pouvoir les distinguer.

Un lien dont la cible n'a pas pu être résolue reste visible avec `resolution = UNRESOLVED` ; il n'est jamais supprimé
silencieusement. Un lien que la source ne porte plus est invalidé lors de la synchronisation. Les liens vers des
références externes (cross-engine) sont distingués des liens internes par le type `EXTERNAL_REFERENCE` de leur
extrémité (ADR-0142).

### Relations propres à un provider

Un provider qui rencontre une relation sans équivalent MORPHEUS :

1. la mappe vers une relation existante si la sémantique est réellement équivalente ;
2. sinon la conserve en métadonnées ;
3. ou propose une extension de la taxonomie par ADR si le besoin est générique.

Un provider n'injecte jamais de nouveau `TraceabilityRelationType` public. Une relation n'est ajoutée à la taxonomie
que si un cas d'usage concret l'exige.

### Frontière du domaine

Le domaine de traçabilité ne dépend d'aucun provider, store, transport, ni de MINOS, NEXUS ou JARVIS. Le stockage
physique n'a pas à être un graphe : la traversée dans les deux directions se fait par requête. Persistance
(ADR-0038), dérivation (ADR-0039), traversée (ADR-0040) et références externes (ADR-0142) sont décidées à part.

## Do's and Don'ts

### Do

- Typer chaque lien par une valeur de `TraceabilityRelationType` et chaque extrémité par un `TraceabilityEntityKind`.
- Donner à chaque lien un `TraceabilityLinkId` explicite, indépendant de ses extrémités et de son contenu.
- Attacher au moins une preuve à chaque lien.
- Fournir une confiance bornée dès que l'origine ou la résolution est `HEURISTIC`.
- Conserver un lien non résolu avec `resolution = UNRESOLVED`.
- Exposer une relation inverse comme vue de requête, sous le nom que porte la relation.
- Mapper une relation provider vers la taxonomie quand la sémantique est équivalente, et la garder en métadonnées sinon.

### Don't

- Ne pas introduire de relation libre de type `String` dans `TraceabilityLink`.
- Ne pas encoder l'origine, la résolution ou la confiance dans le nom du type (`HEURISTIC_IMPLEMENTS`).
- Ne pas persister l'inverse comme seconde arête, ni le compter comme une seconde preuve.
- Ne pas appliquer la transitivité parce que la relation est stockée dans un graphe, ni affirmer une arête transitive.
- Ne pas dériver l'identité d'un lien d'un hachage de `(source, relation, target)`.
- Ne pas supprimer silencieusement un lien non résolu.
- Ne pas présenter un lien heuristique comme explicite.
- Ne pas utiliser `RELATED_TO` comme preuve forte ni comme fourre-tout.
- Ne pas laisser un provider ajouter un type de relation public.

## Consequences

### Positives

- Explicabilité, analyse de couverture, détection d'orphelins et chemins de justification reposent sur des liens
  interrogeables.
- Requêtes cohérentes et contrats API/MCP stables d'un provider à l'autre.
- Moins de duplication sémantique ; le store peut s'optimiser sur des familles de relations connues.
- Intégration plus propre avec MINOS et NEXUS.

### Négatives

- Modèle plus complexe, qui exige la résolution des liens et la gestion des références cassées.
- Gouvernance de la taxonomie : certains mappings provider resteront imparfaits et une redéfinition de relation peut
  imposer une migration.
- Coût potentiel des traversées profondes.

### Risques

- Taxonomie trop large : n'ajouter une relation que pour un cas d'usage concret.
- Taxonomie trop étroite : métadonnées propres au provider et extension par ADR.
- Confusion entre `IMPLEMENTS` et `SATISFIES` : s'en tenir à la sémantique écrite ci-dessus.
- `RELATED_TO` employé comme fourre-tout : il reste exclu des preuves fortes.

## Compliance and Enforcement

- `morpheus-domain/src/test/java/com/morpheus/domain/traceability/TraceabilityLinkTest.java` : taxonomie exacte
  (`controlledTaxonomyContainsExactlyTheMvpRelations`), deux références typées pour une même identité, preuve
  obligatoire, immuable et dédupliquée, confiance exigée pour l'heuristique et bornée, identités de lien distinctes
  pour des arêtes semblables, métadonnées de relation indépendantes de l'origine et de la résolution.
- Constructeurs compacts de `TraceabilityLink` et `TraceabilityConfidence` : rejets à la construction.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`domainMustNotDependOnAdapters`) : le domaine ne dépend d'aucun adaptateur ni de MINOS, NEXUS ou JARVIS.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ExternalTraceabilityContractTest.java`
  (`brokenReferenceRemainsVisibleOnMemoryAndSQLite`) : un lien non résolu reste visible sur les deux stores.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/TraceabilityRelationFilterParityTest.java` :
  filtres par relation identiques dans les deux directions sur les deux stores.
- Revue : pas de politique `TRANSITIVE`, pas d'origine `UNRESOLVED`, pas de type de relation ajouté par un provider,
  `RELATED_TO` jamais retenu comme preuve forte.

## References

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — domaine possédé par MORPHEUS.
- [ADR-0140](DATA-0140-identite-de-domaine.md) — identité de domaine stable.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — projection temporelle explicite.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [ADR-0038](DATA-0038-snapshot-scoped-traceability-persistence.md) — persistance de la traçabilité par snapshot.
- [ADR-0039](BE-0039-deterministic-traceability-derivation.md) — dérivation déterministe de la traçabilité.
- [ADR-0040](BE-0040-bounded-deterministic-traceability-traversal.md) — traversée bornée et déterministe.
- [ADR-0142](DATA-0142-references-externes.md) — traçabilité des références externes.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation et preuves de
  vérification.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md) — preuve du jalon M0.
- [VALIDATION_M4](../../project/validation/VALIDATION_M4.md) — preuve du jalon M4.
