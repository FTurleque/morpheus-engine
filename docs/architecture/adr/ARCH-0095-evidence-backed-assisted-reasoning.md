---
id: ARCH-0095
title: "Evidence-backed assisted reasoning"
domain: architecture
rules: false
---

# Evidence-backed assisted reasoning

- Statut : **Acceptée**
- Date : 2026-07-30

## Context

MORPHEUS publie et interroge des faits de spécification, des observations provider, des résultats de politiques et du
contexte externe. Il doit pouvoir enrichir une réponse par une analyse assistée sans transformer une sortie
heuristique ou probabiliste en fait publié, sans rendre un LLM obligatoire et sans introduire une mutation implicite du
cycle de vie.

La difficulté principale n'est pas de produire du texte : elle consiste à conserver une frontière vérifiable entre ce
que MORPHEUS sait, ce qu'un adaptateur déduit et ce qu'il suggère.

Structure du noyau de `morpheus-application` :

```text
Evidence
  id
  kind
  subject
  statement
  provenance

Claim
  id
  kind = INFERENCE | HEURISTIC | SUGGESTION
  statement
  confidence
  evidenceIds
  adapterId
  provenance
```

Principes que le noyau tient :

```text
facts != inference
inference != suggestion
heuristic != published fact
```

L'identité d'un élément d'évidence est stable dans la requête. Bandes de confiance :

```text
[0.00, 0.20) VERY_LOW
[0.20, 0.40) LOW
[0.40, 0.65) MEDIUM
[0.65, 0.85) HIGH
[0.85, 1.00] VERY_HIGH
```

La confiance n'est pas une probabilité de vérité publiée. Principes des adaptateurs, chacun exécuté séparément, et de
la frontière de mutation :

```text
adapter discovery != adapter execution
empty adapterIds => facts-only result
adapter absence != MORPHEUS failure
adapter failure != fact loss
reasoning execution != lifecycle mutation
reasoning execution != policy override
inference never overwrites published facts
```

Un provider isolé ne peut pas empêcher le fonctionnement facts-only. La décision qui promouvrait une claim porterait
capability d'écriture, confirmation, CAS, acteur, raison et audit. Les constantes de `ReasoningContracts` (valeurs à
lire dans la classe) bornent les éléments d'évidence (`MAX_EVIDENCE`), les adaptateurs sélectionnés (`MAX_ADAPTERS`),
les claims acceptées (`MAX_CLAIMS`), les références d'évidence par claim (`MAX_EVIDENCE_REFERENCES`), les entrées de
provenance (`MAX_PROVENANCE_ENTRIES`) et de paramètres (`MAX_PARAMETER_ENTRIES`), la longueur d'une question
(`MAX_QUESTION_CHARS`) et d'un énoncé (`MAX_STATEMENT_CHARS`) ; aucune troncature silencieuse n'est présentée comme
une analyse complète.

Les deux capacités sont la découverte des adaptateurs et l'analyse ; un principal remote `READ` peut appeler
l'analyse. Une route absente de la politique remote est refusée (`404 NOT_FOUND`), une méthode non déclarée pour une
route l'est aussi (`405 METHOD_NOT_ALLOWED`). Les demandes et résultats sont des vues calculées :

```text
reasoning result != materialized truth
reasoning execution != audit mutation
```

## Decision

1. **Noyau provider-neutral** dans `com.morpheus.application.reasoning` : `Evidence` et `Claim`, de kind `INFERENCE` |
   `HEURISTIC` | `SUGGESTION`. `PUBLISHED_FACT` est une catégorie d'`Evidence`, jamais de `Claim`.
2. Évidence : catégories fermées `PUBLISHED_FACT`, `SOURCE_EXCERPT`, `POLICY_RESULT`, `EXTERNAL_CONTEXT`,
   `OBSERVATION` ; identité stable et provenance explicite. Une claim acceptée cite au moins une évidence de la même
   enveloppe. `facts` égale, dans l'ordre, les `PUBLISHED_FACT` de l'enveloppe, jamais reconstruits d'une claim.
3. Confiance : score fini dans `[0.0, 1.0]` et bande cohérente aux seuils 0,20 / 0,40 / 0,65 / 0,85 (`VERY_LOW`
   à `VERY_HIGH`), sinon rejet ; elle n'autorise aucune promotion.
4. Adaptateurs : `ReasoningAdapter`, SPI read-only ; découverte libre, exécution du seul identifiant sélectionné,
   facts-only sans sélection. `builtin-evidence-synthesis-v1` (`EvidenceSynthesisReasoningAdapter`), local, sans
   réseau ni LLM ; un provider `ServiceLoader` invalide, indisponible ou dupliqué est isolé.
5. Isolation : exception, budget dépassé, claim dupliquée, `adapterId` mensonger ou citation inconnue rendent
   l'exécution `FAILED`, sans toucher aux faits ni accepter une partie de ses claims.
6. Mutation : `mutated = false`, `true` rejeté au constructeur ; aucune commande ni route `apply`, `promote`,
   `activate`, `override` ou de cycle de vie. Promouvoir une claim exige un ADR distinct.
7. Budgets : les constantes `MAX_*` de `ReasoningContracts` et `MorpheusHttpServer.MAX_REQUEST_BODY_BYTES` ; un
   dépassement échoue sans tronquer.
8. Surfaces : lignes `reasoning.adapters` et `reasoning.analyze` de `contracts/public-surfaces.tsv`, `READ` sur CLI,
   MCP et HTTP ; `MorpheusRemoteRoutePolicy` classe `POST /api/v1/reasoning/analyze` en rôle `READ`.
9. Ni store, ni table, ni migration : demandes et résultats sont des vues éphémères.

## Do's and Don'ts

### Do

- Ranger un fait publié dans l'enveloppe comme `Evidence` `PUBLISHED_FACT`, jamais comme `Claim`.
- Faire citer à chaque claim au moins une évidence de la même enveloppe.
- Exiger un `adapterId` explicitement sélectionné pour exécuter un adaptateur.
- Rendre un résultat facts-only quand aucun adaptateur n'est sélectionné.
- Isoler chaque adaptateur : son échec le marque `FAILED` sans toucher aux faits.
- Échouer explicitement sur tout dépassement de budget.

### Don't

- Ne pas reconstruire la liste `facts` à partir d'une claim.
- Ne pas accepter une partie des claims d'un adaptateur défaillant.
- Ne pas exposer de route ou de commande `apply`, `promote`, `activate` ou `override` sur le raisonnement.
- Ne pas produire un résultat `mutated = true`.
- Ne pas persister une demande ou un résultat de raisonnement.
- Ne pas importer de transport, de persistance ni d'exécution de processus dans `application.reasoning..`.

## Consequences

### Positives

- Séparation vérifiable par machine des faits et des inférences.
- Confiance explicite et provenance obligatoire.
- Fonctionnement local-first sans LLM (ADR-0004).
- Adaptateurs extensibles et isolés en cas d'échec.
- Convergence des surfaces CLI, MCP et HTTP.

### Négatives

- Format de requête plus strict ; obligation de fournir des preuves identifiées.
- Validation supplémentaire des sorties d'adaptateurs.
- Absence volontaire de mémoire conversationnelle et de promotion automatique.

### Risques

- Un adaptateur découvert via `ServiceLoader` s'exécute dans le processus MORPHEUS : il n'est isolé que dans ses
  échecs, pas dans ses effets.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m27/ReasoningPlatformArchitectureTest.java` :
  noyau sans dépendance d'adaptateur, de store concret ni de paquet de mutation ; aucune occurrence de `java.net.http`,
  `tools.jackson`, `java.sql`, `ProcessBuilder`, `Runtime.getRuntime` ; manifeste et OpenAPI `READ`, bornés,
  `const: false` ; POST d'analyse classé `READ` par la façade remote.
- `morpheus-application/src/test/java/com/morpheus/application/reasoning/ReasoningServiceTest.java` : facts-only sans
  adaptateur, claims séparées et citées, faits conservés après un échec d'adaptateur, citation inconnue rejetée.
- `morpheus-application/src/test/java/com/morpheus/application/reasoning/ReasoningAdapterRegistryTest.java` :
  adaptateur intégré toujours présent, provider défaillant écarté, identité dupliquée refusée.
- `morpheus-architecture-tests/src/test/java/com/morpheus/api/PublicHttpRouteConvergenceTest.java` et
  `morpheus-architecture-tests/src/test/java/com/morpheus/mcp/PublicSurfaceManifestCoversEveryServedToolTest.java` :
  convergence des surfaces.
- Revue : absence de store, de table et de migration propres au raisonnement.

## References

- [ADR-0004](SEC-0004-local-first-no-llm-core.md) — cœur local-first sans LLM.
- [morpheus-v1-reasoning-m27.yaml](../../developer/reference/openapi/morpheus-v1-reasoning-m27.yaml)
- [ASSISTED_REASONING](../../developer/ASSISTED_REASONING.md)
- [VALIDATION_M27](../../project/validation/VALIDATION_M27.md)
