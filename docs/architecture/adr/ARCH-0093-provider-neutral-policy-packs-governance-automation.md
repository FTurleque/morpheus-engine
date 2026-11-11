---
id: ARCH-0093
title: "Provider-neutral policy packs and governance automation"
domain: architecture
rules: false
---

# Provider-neutral policy packs and governance automation

- Statut : **Acceptée**
- Date : 2026-07-29

## Context

MORPHEUS possède des règles métier explicites pour les contraintes (ADR-0082), l'évaluation de transitions et les
mutations lifecycle contrôlées (ADR-0119, ADR-0083), des diagnostics qualité (ADR-0124) et un moteur de requêtes
provider-neutral (ADR-0092). Ces capacités sont codées comme contrats applicatifs individuels. Distribuer et
versionner des ensembles de politiques de gouvernance ne doit introduire ni un second moteur de vérité, ni du texte,
du SQL ou du code arbitraire rendu exécutable.

## Decision

L'application porte des **policy packs** provider-neutral (`com.morpheus.application.policy`) : versions
immuables, activation explicite, overrides auditables et évaluation read-only.

Invariants :

```text
constraint text != executable policy
UNKNOWN != BLOCKED
severity != blocking policy
policy recommendation != applied mutation
policy version != mutable latest
policy override != provenance erasure
dry-run != mutation
policy evaluation != lifecycle mutation
pack activation != domain truth mutation
```

**Identité et version** (`PolicyIds`, `PolicyPack`) :

```text
PolicyIds.PackId     identité stable du pack
PolicyIds.VersionId  identité immuable d'une version
PolicyIds.RuleId     identité stable de règle dans le pack
revision             CAS de la configuration courante du pack
versionNumber        numéro monotone immuable de version publiée
```

Le nom d'un pack n'est pas son identité. Une mise à jour crée une nouvelle version ; elle ne modifie jamais le contenu
d'une version historique.

**Règles fermées et typées.** `PolicyRule.Kind` est fermé :

```text
CONSTRAINT_GUARD
LIFECYCLE_GUARD
QUALITY_THRESHOLD
QUERY_ASSERTION
```

Chaque type a une configuration typée (`PolicyRule.Config`, interface scellée). Aucun moteur de script, expression
libre, SQL passthrough, nom de classe, code de plugin ou gabarit LLM n'est accepté comme règle.

- *Constraint guard* : consomme les `ConstraintEvaluation` du moteur de contraintes ; peut tester la présence de
  décisions explicitement bloquantes pour une cible lifecycle déclarée ; n'analyse jamais `Constraint.statement()`.
- *Lifecycle guard* : consomme l'évaluation read-only de transition ; peut recommander `BLOCK`, `WARN`, `PASS` ou
  `UNKNOWN`, sans déclencher aucune mutation.
- *Quality threshold* : compare une métrique déclarée dans un registre fermé (`PolicyRule.QualityMetric`) à un seuil
  numérique explicite.
- *Query assertion* : réutilise `QueryDefinition` / `QueryExecutionService` et compare `totalMatches` à un opérateur
  de comptage fermé. Le DSL de requête reste la seule grammaire de sélection métier ; aucune syntaxe provider ou SQL
  n'est ajoutée.

**Scope.** Une activation porte un scope explicite, projet ou portfolio (`PolicyScope`) : les identités sont
`ProjectSpecificationId` / `PortfolioId`, jamais workspace, dépôt, provider ou chemin.

**Applicabilité et décisions** (`PolicyEvaluation`). Applicabilité : `APPLICABLE`, `NOT_APPLICABLE`, `UNKNOWN`.
Décision : `PASS`, `WARN`, `BLOCK`, `UNKNOWN`. Une absence d'information produit `UNKNOWN`. Seule une règle explicite
ou un override explicite peut produire `BLOCK`.

**Overrides.** Un override (`PolicyConfiguration.Override`) est de premier ordre et auditable : règle, scope, mode,
raison, acteur, révision et instant. Modes (`PolicyConfiguration.OverrideMode`) : `DISABLE`, `FORCE_WARN`,
`FORCE_BLOCK`. L'évaluation conserve toujours la décision originale et l'override appliqué ; un override ne supprime
ni provenance, ni evidence, ni raison source.

**Activation.** L'activation sélectionne explicitement `(PackId, VersionId)` sur un scope, sous CAS. Elle n'est ni
une publication de snapshot, ni une mutation lifecycle, ni un remplacement de la vérité provider.

**Dry-run.** Le dry-run peut évaluer une version non active sur un scope. Il est strictement read-only : aucune
activation, aucun override, aucun audit de mutation métier ni écriture lifecycle. Seule une réponse transport-safe
est produite.

**Audit.** Chaque opération de configuration de gouvernance (`PolicyConfiguration.AuditAction` : création, mise à
jour de version, activation, désactivation, pose et retrait d'override) produit un `PolicyConfiguration.AuditRecord`
append-only. L'audit de configuration de policy est distinct de l'audit des mutations lifecycle (ADR-0083).

**Budgets.** Les bornes sont les constantes de `PolicyBudgets`, dont : règles par pack, packs actifs par scope,
overrides par scope, longueur du nom de pack, longueur de la description de règle, règles évaluées par un dry-run.
Tout dépassement est rejeté explicitement avant toute exécution partielle.

**Budget d'évaluation d'une garde de contraintes.** `PolicyBudgets.MAX_CONSTRAINT_EVALUATIONS_PER_FACT` borne le
parcours d'une garde de contraintes, et donc son evidence, qui compte une entrée par évaluation ; ce budget ne refuse
pas la configuration, il borne l'observation. Si une contrainte reste à observer au-delà du budget, le fait rend
`UNKNOWN` avec la raison `EVALUATION_BUDGET_REACHED:<budget> …`, jamais `PASS`. Un change dont toutes les contraintes
sont observées dans le budget rend `PASS` ; une contrainte `BLOCKING` rencontrée dans le budget rend `FAIL`
immédiatement.

Une page d'évaluations de contraintes (`ConstraintEvaluationQueryService`) filtre et trie l'ensemble des contraintes du
change, ce qui donne le total et l'ordre déterministe, puis n'évalue que la tranche de la page.

**Persistance.** Port `PolicyPackStore`, adaptateurs mémoire et SQLite. La migration SQLite est additive
(`V015__policy_packs.sql`), avec versions et audit append-only ; aucune migration antérieure n'est réécrite.

**Surfaces.** CLI, MCP et HTTP exposent les mêmes intentions applicatives de registre, versionnement, activation,
override, évaluation et audit. La parité d'intention n'impose pas la même forme transport ; elle se lit dans
`contracts/public-surfaces.tsv`.

## Do's and Don'ts

### Do

- Créer une nouvelle version pour toute mise à jour d'un pack.
- Activer une version par CAS sur un scope projet ou portfolio explicite.
- Rendre `UNKNOWN` quand l'information manque, et conserver la décision originale à côté de l'override appliqué.
- Rejeter tout dépassement d'une borne de `PolicyBudgets` avant d'exécuter quoi que ce soit.
- Rendre `UNKNOWN`, avec la raison du budget, quand une garde n'a pas observé toutes les contraintes.
- Écrire un enregistrement d'audit append-only pour chaque opération de configuration.

### Don't

- Ne jamais convertir `UNKNOWN` en `BLOCK`.
- Ne jamais conclure `PASS` après un parcours interrompu par un budget.
- Ne jamais exécuter un script, une expression libre, du SQL, un nom de classe ou du code de plugin comme règle.
- Ne jamais analyser le texte d'une contrainte pour en déduire une politique.
- Ne jamais muter le lifecycle, le snapshot ou la vérité provider depuis une évaluation, une activation ou un
  dry-run.
- Ne jamais modifier le contenu d'une version publiée.
- Ne jamais dériver un scope d'un workspace, d'un dépôt, d'un provider ou d'un chemin.

## Consequences

### Positives

- Politiques partageables et versionnées, explicables et reproductibles.
- Réutilisation des sémantiques existantes (contraintes, transitions, qualité, requêtes).
- Séparation forte entre recommandation et mutation ; dry-run sûr.
- Un change au-delà du budget d'évaluation sans contrainte bloquante observée rend `UNKNOWN` ; une policy qui force
  `UNKNOWN` en `BLOCK` par override explicite le bloque.

### Négatives

- Nouveau modèle de configuration, avec store, versionnement et audit.
- Registre fermé de types de règles et de métriques à faire évoluer explicitement.
- Migration SQLite et surfaces supplémentaires.

### Risques

- La garde de cycle de vie (`DefaultPolicyFactResolver.lifecycle()`) s'appuie sur `ChangeTransitionEvaluationService`,
  qui évalue toutes les contraintes du change sans budget ; la borner changerait sa sémantique et relève d'une décision
  séparée.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m25/PolicyPlatformArchitectureTest.java` :
  `application.policy` ne dépend ni des adaptateurs, ni des providers, ni des stores concrets ; le modèle de policy ne
  contient aucun contrat d'exécution de script, de SQL ou de réflexion ; manifeste et OpenAPI exposent les mêmes
  familles d'intention.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m25/PolicyPackContractTest.java`,
  `PolicyCodecAndBudgetContractTest.java`, `PolicyPersistenceParityTest.java`, `PolicyStoreAtomicityContractTest.java`
  et `PolicyAuditOrderTest.java` : contrat des packs, bornes, parité mémoire / SQLite, atomicité du store, ordre de
  l'audit.
- `morpheus-application/src/test/java/com/morpheus/application/policy/ConstraintGuardBudgetTest.java` : parcours
  au-delà du budget `UNKNOWN`, parcours complet `PASS`, contrainte bloquante vue dans le budget `FAIL`.
- `morpheus-application/src/test/java/com/morpheus/application/query/ConstraintEvaluationPageTest.java` : une page
  n'évalue que sa tranche et compte tout le change.
- `scripts/validate-m25.ps1` et `scripts/validate-m25.sh` : gate du jalon.
- Revue : l'absence de mutation lifecycle dans le dry-run et l'activation au-delà de ce que les tests exercent.

## References

- [ADR-0082](DATA-0082-explicit-constraint-semantics-and-blocking-policy.md) — sémantique des contraintes.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — écritures lifecycle contrôlées.
- [ADR-0084](ARCH-0084-provider-neutral-multi-provider-composition.md) — ratio `UNKNOWN` sous duplication.
- [ADR-0091](DATA-0091-multi-project-portfolio-intelligence.md) — portfolio.
- [ADR-0092](ARCH-0092-provider-neutral-query-dsl-saved-views-reporting.md) — DSL de requête.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — transition tri-état.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — qualité et faits tri-état.
- [VALIDATION_M25](../../project/validation/VALIDATION_M25.md)
