# ADR-0022 — Normaliser le contenu en M2 avant la projection temporelle M3

- Statut : **Proposée — validation M2 requise**
- Date : 22 juillet 2026
- Dépend de : ADR-0001, ADR-0006, ADR-0009, ADR-0015
- Portée : modèle normalisé M2, ingestion, frontière M2/M3

## Contexte

Le modèle C0 décrit des entités telles que `Specification` et `Requirement` avec une dimension `TemporalState`.

La roadmap validée affecte toutefois explicitement la reconstruction :

```text
CURRENT / PROPOSED / HISTORICAL
```

au jalon M3.

M2 doit d'abord démontrer qu'une source provider peut être transformée en concepts MORPHEUS indépendants de son format.

## Problème

Introduire dès le premier parser M2 `CURRENT / PROPOSED / HISTORICAL` créerait trois risques :

1. coupler la normalisation structurelle à la reconstruction temporelle ;
2. porter prématurément dans le domaine des règles qui appartiennent au snapshot/versioning M3 ;
3. laisser OpenSpec dicter implicitement la temporalité MORPHEUS parce que ses répertoires distinguent specs, changes et archives.

## Décision proposée

M2 normalise d'abord le **contenu structurel** et son origine :

```text
ProjectSpecification
Specification
Requirement
Constraint
Scenario
ChangeProposal
DesignDecision
AcceptanceCriterion
ImplementationTask
Provenance
Evidence
ExternalReference
```

Les entités M2 ne portent pas obligatoirement `TemporalState` tant que M3 n'a pas stabilisé la projection temporelle.

Le provider peut conserver dans ses faits internes toute information permettant à M3 de reconstruire ultérieurement l'état temporel, mais cette information ne devient pas automatiquement une propriété publique du domaine M2.

## Invariants

```text
provider facts != MORPHEUS domain
content normalization != temporal projection
Scenario != AcceptanceCriterion
DomainIdentity != SourceLocator != externalId
```

Une position dans l'arborescence OpenSpec ne suffit pas à définir un état temporel MORPHEUS public.

## Premier slice

Le premier slice M2 porte :

```text
ProjectSpecification
Specification
Requirement
Scenario
Provenance
Evidence
NormalizedProjectContent
```

à partir de la spécification courante de la fixture `openspec-basic`.

Il ne porte pas encore :

```text
ChangeProposal temporal projection
archives temporal projection
SpecificationVersion complet
KnowledgeSnapshot métier complet
```

## Conséquences

### Positives

- frontière M2/M3 explicite ;
- parser OpenSpec incapable de contaminer le domaine avec ses conventions temporelles ;
- modèle testable avec un second provider ;
- migration progressive du modèle C0 vers des contrats Java prouvés.

### Négatives

- M2 ne fournit pas encore de vue `get_current_specification` complète ;
- certaines informations de source sont conservées comme faits provider avant leur projection finale.

Ces limites sont intentionnelles et cohérentes avec la roadmap.

## Critère d'acceptation

ADR-0022 passe à **Acceptée — M2** lorsque :

- le premier vertical slice OpenSpec normalise au moins `Specification`, `Requirement` et `Scenario` ;
- toute entité importée possède une provenance et une preuve ;
- aucune dépendance OpenSpec n'apparaît dans `com.morpheus.domain` ;
- aucun `TemporalState` n'est inventé par le premier reader ;
- les tests M2 et le build complet sont verts.
