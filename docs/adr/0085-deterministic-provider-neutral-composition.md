# ADR-0085 — Composition multi-provider déterministe et provider-neutral

Statut : **Proposée — M18**

Date : 26 juillet 2026

## Contexte

MORPHEUS sait déjà sonder plusieurs providers et lire du contenu normalisé via `SpecificationContentReader`, mais chaque import courant est produit par une seule source. M18 doit construire un graphe cohérent depuis plusieurs providers sans convertir un conflit en last-write-wins silencieux.

## Décision

La composition appartient à `morpheus-application` et ne dépend d'aucun adapter concret.

Entrée : une liste de sources provider-neutral :

```text
SpecificationContentReader
ProviderId
precedence entier explicite
required / optional
```

Ordre canonique : precedence décroissante puis `ProviderId` lexical.

Clés logiques inter-provider :

```text
Specification = specification.key normalisée
Requirement   = requirement.key normalisée lorsqu'elle existe
Scenario      = requirement logical key + scenario.title normalisé
```

Une identité MORPHEUS n'est jamais remplacée par un `ProviderId`, un path ou une clé logique. La clé logique sert uniquement à détecter une continuité/collision candidate pendant la composition.

## Résolution

### Contributions équivalentes

Si deux providers décrivent la même clé logique avec le même contenu métier, la contribution de precedence supérieure est retenue comme identité canonique du snapshot et la contribution secondaire est enregistrée dans le rapport.

### Contributions différentes avec precedence différente

La precedence supérieure gagne, mais un `ProviderCompositionConflict` `RESOLVED_BY_PRECEDENCE` est obligatoirement enregistré avec gagnant, concurrents et raison.

### Contributions différentes avec même precedence

Aucun tie-break silencieux n'est autorisé. Le conflit est `UNRESOLVED_EQUAL_PRECEDENCE`, un diagnostic ERROR est émis et le snapshot ne peut pas devenir ACTIVE.

### Élément sans clé logique

Il n'est pas fusionné avec un élément d'un autre provider. L'absence de preuve de continuité n'est jamais convertie en continuité inventée.

## Références

Lorsque specification/requirement gagnants changent d'identité, les références internes des scénarios retenus sont remappées vers l'identité retenue. La provenance de la contribution gagnante reste inchangée.

## Provider failure semantics

- source `optional` absente/unsupported : contribution enregistrée, composition continue ;
- source `required` sans contenu lisible : diagnostic ERROR ;
- succès d'un provider optional n'est jamais effacé par l'échec d'un autre provider optional ;
- aucun provider ayant produit du contenu : composition impossible.

## Invariants

```text
provider identifier != DomainIdentity
source path != identity
conflict != silent last-write-wins
missing key != inferred continuity
provider-specific type -X-> application composition API
```

## Conséquences

La sortie de composition est :

```text
ComposedProjectContent
├── NormalizedProjectContent
└── ProviderCompositionReport
```

Le rapport est queryable et persisté séparément du contenu métier.

## Validation attendue

```text
OpenSpec-only compatibility PASS
Markdown-only PASS
OpenSpec + Markdown PASS
resolved conflict visible PASS
equal-precedence divergent conflict blocks PASS
scenario references remapped PASS
optional absent PASS
required failed PASS
canonical order PASS
```