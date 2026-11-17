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

Invariants que les policy packs tiennent, et rôle de leurs identifiants (`PolicyIds`, `PolicyPack`) :

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

PolicyIds.PackId     identité stable du pack
PolicyIds.VersionId  identité immuable d'une version
PolicyIds.RuleId     identité stable de règle dans le pack
revision             CAS de la configuration courante du pack
versionNumber        numéro monotone immuable de version publiée
```

Chaque type de règle consomme un contrat existant. Une garde de contraintes lit les `ConstraintEvaluation` du moteur
de contraintes et peut tester la présence de décisions explicitement bloquantes pour une cible lifecycle déclarée. Une
garde lifecycle lit l'évaluation read-only de transition et recommande `BLOCK`, `WARN`, `PASS` ou `UNKNOWN`. Un seuil
de qualité compare une métrique du registre fermé `PolicyRule.QualityMetric` à un seuil numérique explicite. Une
assertion de requête réutilise `QueryDefinition` / `QueryExecutionService` et compare `totalMatches` à un opérateur de
comptage fermé ; le DSL de requête reste la seule grammaire de sélection métier.

`PolicyBudgets` borne notamment les règles par pack, les packs actifs et les overrides par scope, la longueur du nom de
pack et de la description de règle, et les règles évaluées par un dry-run. Le budget d'une garde de contraintes compte
une entrée d'evidence par évaluation : il borne l'observation sans refuser la configuration. Une page d'évaluations de
contraintes (`ConstraintEvaluationQueryService`) filtre et trie toutes les contraintes du change, d'où le total et
l'ordre déterministe, puis n'évalue que la tranche de la page. Les actions d'audit couvrent création, mise à jour de
version, activation, désactivation, pose et retrait d'override. La parité des surfaces est d'intention, pas de forme
transport.

## Decision

1. Policy packs provider-neutral dans `com.morpheus.application.policy`. Le nom n'est pas l'identité ; une mise à
   jour crée une version (`versionNumber` monotone), sans modifier une version publiée ; `revision` est le CAS.
2. `PolicyRule.Kind` fermé (`CONSTRAINT_GUARD`, `LIFECYCLE_GUARD`, `QUALITY_THRESHOLD`, `QUERY_ASSERTION`),
   configuration scellée `PolicyRule.Config`. Ni script, expression libre, SQL, nom de classe, code de plugin ou
   gabarit LLM comme règle, ni analyse de `Constraint.statement()`, ni syntaxe ajoutée au DSL.
3. `PolicyScope` : `ProjectSpecificationId` ou `PortfolioId`, jamais workspace, dépôt, provider ou chemin.
4. Applicabilité `APPLICABLE` / `NOT_APPLICABLE` / `UNKNOWN`, décision `PASS` / `WARN` / `BLOCK` / `UNKNOWN`.
   Information absente : `UNKNOWN`. Seule une règle ou un override explicite produit `BLOCK`.
5. `PolicyConfiguration.Override` (règle, scope, mode `DISABLE` / `FORCE_WARN` / `FORCE_BLOCK`, raison, acteur,
   révision, instant) garde décision originale, provenance, evidence et raison source.
6. Activation de `(PackId, VersionId)` sur un scope sous CAS. Le dry-run d'une version non active n'active,
   n'override ni n'audite rien et rend une réponse transport-safe. Rien ne publie de snapshot ni ne mute lifecycle ou
   vérité provider.
7. Chaque `PolicyConfiguration.AuditAction` écrit un `PolicyConfiguration.AuditRecord` append-only, hors audit
   lifecycle (ADR-0083).
8. Dépassement de `PolicyBudgets` rejeté avant exécution partielle. Au-delà de
   `PolicyBudgets.MAX_CONSTRAINT_EVALUATIONS_PER_FACT`, une garde rend `UNKNOWN`
   (`EVALUATION_BUDGET_REACHED:<budget> …`), jamais `PASS` ; tout observé : `PASS` ; `BLOCKING` vue : `FAIL`.
9. Port `PolicyPackStore` (mémoire, SQLite) ; `V015__policy_packs.sql` additive, sans réécrire de migration. CLI, MCP
   et HTTP exposent les mêmes intentions (`contracts/public-surfaces.tsv`).

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
