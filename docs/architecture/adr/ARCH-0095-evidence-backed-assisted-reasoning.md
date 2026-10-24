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

## Decision

Un noyau provider-neutral vit dans `morpheus-application` (`com.morpheus.application.reasoning`) :

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

`PUBLISHED_FACT` est une catégorie d'`Evidence`, jamais une catégorie de `Claim`.

```text
facts != inference
inference != suggestion
heuristic != published fact
```

### Enveloppe d'évidence

Les catégories d'évidence sont fermées :

```text
PUBLISHED_FACT
SOURCE_EXCERPT
POLICY_RESULT
EXTERNAL_CONTEXT
OBSERVATION
```

Chaque élément possède une identité stable dans la requête et une provenance explicite. Toute claim acceptée cite au
moins une identité d'évidence présente dans la même enveloppe. Une citation inconnue invalide la sortie de
l'adaptateur concerné.

La liste `facts` du résultat correspond exactement, dans le même ordre, aux éléments `PUBLISHED_FACT` de l'enveloppe.
Elle n'est jamais reconstruite à partir d'une claim.

### Confiance

Chaque claim contient un score fini borné dans `[0.0, 1.0]` et une bande cohérente :

```text
[0.00, 0.20) VERY_LOW
[0.20, 0.40) LOW
[0.40, 0.65) MEDIUM
[0.65, 0.85) HIGH
[0.85, 1.00] VERY_HIGH
```

La confiance n'est ni une probabilité de vérité publiée, ni une autorisation de promotion. Une bande incohérente avec
le score est rejetée.

### Adaptateurs optionnels

`ReasoningAdapter` est un SPI applicatif read-only. Les adaptateurs disponibles peuvent être découverts, mais leur
exécution exige toujours un identifiant explicitement sélectionné dans la requête.

```text
adapter discovery != adapter execution
empty adapterIds => facts-only result
adapter absence != MORPHEUS failure
adapter failure != fact loss
```

Le registre standard fournit un adaptateur déterministe local, `builtin-evidence-synthesis-v1`
(`EvidenceSynthesisReasoningAdapter`), sans accès réseau ni LLM. Des providers du classpath peuvent être découverts
via `ServiceLoader` ; un provider invalide, indisponible ou dupliqué est isolé et ne peut pas empêcher le
fonctionnement facts-only.

### Isolation des erreurs

Chaque adaptateur est exécuté séparément. Une exception, un dépassement de budget, une identité de claim dupliquée, un
`adapterId` mensonger ou une citation inconnue produit une exécution `FAILED` sans supprimer ni modifier les preuves
et faits fournis. Les claims d'un adaptateur défaillant ne sont pas partiellement acceptées.

### Frontière de mutation

Le résultat contient obligatoirement `mutated = false` ; le constructeur du contrat rejette toute valeur `true`. Le
raisonnement ne fournit aucune commande ni route `apply`, `promote`, `activate`, `override` ou de cycle de vie.

```text
reasoning execution != lifecycle mutation
reasoning execution != policy override
inference never overwrites published facts
```

Une promotion éventuelle d'une claim exige une décision d'architecture distincte, avec capability d'écriture,
confirmation, CAS, acteur, raison et audit.

### Budgets

Les bornes sont les constantes de `ReasoningContracts` et la borne de corps HTTP de `MorpheusHttpServer` :

| Borne | Constante | Valeur |
|---|---|---|
| éléments d'évidence | `MAX_EVIDENCE` | 256 |
| adaptateurs sélectionnés | `MAX_ADAPTERS` | 8 |
| claims acceptées | `MAX_CLAIMS` | 256 |
| références d'évidence par claim | `MAX_EVIDENCE_REFERENCES` | 32 |
| entrées de provenance | `MAX_PROVENANCE_ENTRIES` | 32 |
| entrées de paramètres | `MAX_PARAMETER_ENTRIES` | 32 |
| question (caractères) | `MAX_QUESTION_CHARS` | 8 192 |
| énoncé (caractères) | `MAX_STATEMENT_CHARS` | 16 384 |
| corps HTTP (octets) | `MAX_REQUEST_BODY_BYTES` | 65 536 |

Un dépassement échoue explicitement ; aucune troncature silencieuse n'est présentée comme une analyse complète.

### Surfaces

```text
CLI   reason adapters
MCP   list_reasoning_adapters
HTTP  GET /api/v1/reasoning/adapters

CLI   reason analyze
MCP   reason_with_evidence
HTTP  POST /api/v1/reasoning/analyze
```

Les deux familles sont `READ` dans `contracts/public-surfaces.tsv`. La façade remote classe explicitement
`POST /api/v1/reasoning/analyze` comme un POST de rôle `READ` (`MorpheusRemoteRoutePolicy`) ; un principal `READ` peut
l'appeler. Une route absente de la politique remote est refusée (`404 NOT_FOUND`), une méthode non déclarée pour une
route l'est aussi (`405 METHOD_NOT_ALLOWED`).

### Persistance

Le raisonnement n'ajoute ni store, ni table, ni migration SQLite. Les demandes et résultats sont des vues calculées
éphémères :

```text
reasoning result != materialized truth
reasoning execution != audit mutation
```

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
