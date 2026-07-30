# ADR-0095 — Evidence-backed assisted reasoning

Statut : **Acceptée — M27**

Date : 30 juillet 2026

## Contexte

MORPHEUS publie et interroge des faits de spécification, des observations provider, des résultats de politiques et du contexte externe. M27 doit permettre d’enrichir une réponse par une analyse assistée sans transformer une sortie heuristique ou probabiliste en fait publié, sans rendre un LLM obligatoire et sans introduire une mutation implicite du lifecycle.

La difficulté principale n’est pas de produire du texte : elle consiste à conserver une frontière vérifiable entre ce que MORPHEUS sait, ce qu’un adaptateur déduit et ce qu’il suggère.

## Décision

M27 introduit un noyau provider-neutral dans `morpheus-application` :

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

`PUBLISHED_FACT` est une catégorie d’`Evidence`, jamais une catégorie de `Claim`.

```text
facts != inference
inference != suggestion
heuristic != published fact
```

## Enveloppe d’évidence

Les catégories d’évidence sont fermées :

```text
PUBLISHED_FACT
SOURCE_EXCERPT
POLICY_RESULT
EXTERNAL_CONTEXT
OBSERVATION
```

Chaque élément possède une identité stable dans la requête et une provenance explicite. Toute claim acceptée doit citer au moins une identité d’évidence présente dans la même enveloppe. Une citation inconnue invalide la sortie de l’adaptateur concerné.

La liste `facts` du résultat doit correspondre exactement, dans le même ordre, aux éléments `PUBLISHED_FACT` de l’enveloppe. Elle n’est jamais reconstruite à partir d’une claim.

## Confiance

Chaque claim contient un score fini borné dans `[0.0, 1.0]` et une bande cohérente :

```text
[0.00, 0.20) VERY_LOW
[0.20, 0.40) LOW
[0.40, 0.65) MEDIUM
[0.65, 0.85) HIGH
[0.85, 1.00] VERY_HIGH
```

La confiance n’est ni une probabilité de vérité publiée, ni une autorisation de promotion. Une bande incohérente avec le score est rejetée.

## Adaptateurs optionnels

`ReasoningAdapter` est un SPI applicatif read-only. Les adaptateurs disponibles peuvent être découverts, mais leur exécution exige toujours un identifiant explicitement sélectionné dans la requête.

```text
adapter discovery != adapter execution
empty adapterIds => facts-only result
adapter absence != MORPHEUS failure
adapter failure != fact loss
```

Le registre standard fournit un adaptateur déterministe local `builtin-evidence-synthesis-v1`. Il ne réalise aucun accès réseau et n’utilise aucun LLM. Des providers classpath peuvent être découverts via `ServiceLoader`; un provider invalide, indisponible ou dupliqué est isolé et ne peut pas empêcher le fonctionnement facts-only.

## Isolation des erreurs

Chaque adaptateur est exécuté séparément. Une exception, un dépassement de budget, une identité de claim dupliquée, un `adapterId` mensonger ou une citation inconnue produit une exécution `FAILED` sans supprimer ni modifier les preuves et faits fournis.

Les claims d’un adaptateur défaillant ne sont pas partiellement acceptées.

## Frontière de mutation

Le résultat M27 contient obligatoirement :

```text
mutated = false
```

Le constructeur du contrat rejette toute valeur `true`. M27 ne fournit aucune commande ni route `apply`, `promote`, `activate`, `override` ou lifecycle.

```text
reasoning execution != lifecycle mutation
reasoning execution != policy override
inference never overwrites published facts
```

Une promotion future éventuelle devra être un jalon distinct, avec capability d’écriture, confirmation, CAS, acteur, raison, audit et nouvelle décision d’architecture.

## Budgets

```text
evidence items             <= 256
selected adapters          <= 8
accepted claims            <= 256
evidence refs / claim      <= 32
provenance entries         <= 32
parameter entries          <= 32
question                    <= 8,192 chars
statement                   <= 16,384 chars
HTTP body                   <= 65,536 bytes
```

Un dépassement échoue explicitement ; aucune troncature silencieuse n’est présentée comme une analyse complète.

## Surfaces

Convergence sémantique :

```text
CLI   reason adapters
MCP   list_reasoning_adapters
HTTP  GET /api/v1/reasoning/adapters

CLI   reason analyze
MCP   reason_with_evidence
HTTP  POST /api/v1/reasoning/analyze
```

Les deux familles sont READ. La façade remote classe explicitement `POST /api/v1/reasoning/analyze` comme POST read-only ; un principal READ peut l’appeler, tandis qu’une route POST inconnue reste WRITE/fail-closed.

## Persistance

M27 n’ajoute ni store, ni table, ni migration SQLite. Les demandes et résultats sont des vues calculées éphémères. L’absence de V016 est volontaire :

```text
reasoning result != materialized truth
reasoning execution != audit mutation
```

## Conséquences

Positives : séparation machine-verifiable des faits et inférences, confiance explicite, provenance obligatoire, fonctionnement local-first sans LLM, adaptateurs extensibles et fault-isolated, convergence des surfaces.

Coûts : format de requête plus strict, obligation de fournir des preuves identifiées, validation supplémentaire des sorties d’adaptateurs, absence volontaire de mémoire conversationnelle ou de promotion automatique.

## Qualification

La décision est acceptée après double qualification locale Windows + Linux/WSL sur le même SHA exact :

```text
SHA exact qualifié                         f97307c878125550693699124ca717f64f305a3a
Windows                                    PASS
Linux / WSL                                PASS
Tests                                      602 PASS sur chaque plateforme
Architecture                               238 PASS sur chaque plateforme
Windows line / branch                      45.2226% / 38.4456%
Linux line / branch                        45.2246% / 38.4456%
Facts / claims separation                  PASS
Confidence bounds + bands                  PASS
Evidence citations + provenance            PASS
Facts-only without selected adapter        PASS
Adapter failure isolation                  PASS
No silent mutation                         PASS
CLI / MCP / HTTP convergence               PASS
Remote READ classification                 PASS
Budgets                                    PASS
SBOM / provenance                          PASS Windows + Linux
Portable                                   PASS Windows + Linux
Packaged reasoning smokes                  PASS Windows + Linux
postGateExecutableDelta                    NONE Windows + Linux
```

Les preuves détaillées sont conservées dans [`../validation/VALIDATION_M27.md`](../validation/VALIDATION_M27.md).

En juillet 2026, aucune GitHub Actions / CI ne constitue une preuve M27.