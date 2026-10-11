# ADR-0022 — Normaliser le contenu en M2 avant la projection temporelle M3

- Statut : **Acceptée — M2**
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

## Décision

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

## Validation

Gate exécuté sous Windows le 22 juillet 2026 :

```text
Windows 10 x64
Apache Maven 3.9.16
JDK 24.0.1
javac release 21

.\mvnw.cmd clean test
```

Résultats :

```text
42 tests hérités de M1                         PASS
NormalizedProjectContentTest               3/3 PASS
OpenSpecCurrentSpecificationReaderTest     3/3 PASS

TOTAL                                     48/48 PASS
Failures                                      0
Errors                                        0
BUILD SUCCESS
```

La preuve démontre notamment :

- normalisation de `Specification`, `Requirement` et `Scenario` depuis `openspec-basic` ;
- provenance et evidence présentes pour chaque entité importée du slice ;
- cohérence référentielle vérifiée par `NormalizedProjectContent` ;
- aucune dépendance OpenSpec dans `com.morpheus.domain` ;
- aucun `TemporalState` inventé par le reader ;
- mapping `GIVEN/AND/WHEN/THEN` vers `preconditions/action/expectedOutcome` provider-neutral ;
- identités MORPHEUS distinctes des clés externes OpenSpec.

## Critère d'acceptation

Le critère est **satisfait**.

ADR-0022 est **Acceptée — M2** : le premier vertical slice OpenSpec normalise le contenu structurel avec provenance/evidence, sans fuite provider ni projection temporelle prématurée, et le build complet est vert.