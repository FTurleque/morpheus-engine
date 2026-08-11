# §9 — Décisions architecturales

Les ADR produit sont stockés dans [`../../adr/`](../../adr/). Cette section
fournit une vue architecturale synthétique ; le contenu de chaque ADR reste la
source autoritative de sa décision, de son statut et de ses preuves.

La baseline 1.2.0 contient les ADR **ADR-0001 à ADR-0096**. L'index
`docs/adr/README.md` est un document de navigation historique et peut être
réconcilié séparément lorsqu'il accuse un retard sur les fichiers ADR présents.

---

## 9.1 Décisions fondatrices

| Sujet | ADR |
|-------|-----|
| Domaine indépendant des providers | [ADR-0001](../../adr/0001-morpheus-owned-domain.md) |
| OpenSpec comme premier provider sans verrouillage | [ADR-0002](../../adr/0002-openspec-reference-provider.md) |
| Store derrière un port MORPHEUS | [ADR-0003](../../adr/0003-specification-knowledge-store.md) |
| Cœur local-first sans LLM obligatoire | [ADR-0004](../../adr/0004-local-first-no-llm-core.md) |
| CURRENT / PROPOSED / HISTORICAL séparés | [ADR-0006](../../adr/0006-current-vs-proposed-state.md) |
| Intégrations cross-engine découplées | [ADR-0007](../../adr/0007-cross-engine-integration.md) |
| Providers read-first | [ADR-0008](../../adr/0008-read-first-write-capability.md) |
| Identité stable | [ADR-0009](../../adr/0009-stable-domain-identity.md) |
| Snapshot versionné et activation atomique | [ADR-0012](../../adr/0012-snapshot-versioning-strategy.md) |
| Java 21 | [ADR-0016](../../adr/0016-java-21-production-baseline.md) |
| Maven + Wrapper | [ADR-0017](../../adr/0017-maven-build-foundation.md) |
| SQLite initial derrière port | [ADR-0018](../../adr/0018-sqlite-initial-persistent-store.md) |

---

## 9.2 Surfaces, distribution et intégrations

| Sujet | ADR |
|-------|-----|
| Distribution native-first | [ADR-0027](../../adr/0027-native-first-container-supported-distribution.md) |
| CLI stable | [ADR-0059](../../adr/0059-stable-local-cli-contract.md) |
| Distribution jpackage autonome | [ADR-0061](../../adr/0061-self-contained-jpackage-portable-distribution.md) |
| MCP Java SDK + STDIO | [ADR-0062](../../adr/0062-official-java-mcp-sdk-and-native-stdio.md) |
| Contrat MCP read-only initial | [ADR-0063](../../adr/0063-read-only-mcp-tool-contract.md) |
| Launcher MCP natif | [ADR-0064](../../adr/0064-native-launcher-mcp-routing.md) |
| API HTTP locale | [ADR-0065](../../adr/0065-jdk-httpserver-local-api.md) |
| Contrat HTTP `/api/v1` | [ADR-0066](../../adr/0066-versioned-http-api-contract.md) |
| MINOS via MCP STDIO | [ADR-0069](../../adr/0069-minos-mcp-stdio-integration.md) |
| NEXUS via MCP STDIO | [ADR-0073](../../adr/0073-nexus-mcp-stdio-integration.md) |
| Frontière MORPHEUS / JARVIS | [ADR-0077](../../adr/0077-morpheus-jarvis-read-only-orchestration-boundary.md) |

---

## 9.3 Plateformes fonctionnelles récentes

| Milestone | Décision structurante | ADR |
|-----------|------------------------|-----|
| M17 | Controlled lifecycle writes | [ADR-0083](../../adr/0083-controlled-lifecycle-write-operations.md) |
| M18 | Composition multi-provider | [ADR-0084](../../adr/0084-provider-neutral-multi-provider-composition.md) |
| M19 | Budgets de performance | [ADR-0085](../../adr/0085-predeclared-performance-budgets-and-deterministic-large-fixtures.md) |
| M19 | Recovery atomique / concurrence SQLite | [ADR-0086](../../adr/0086-failure-atomic-snapshot-recovery-and-bounded-sqlite-concurrency.md) |
| M19 | Opérabilité local-first | [ADR-0087](../../adr/0087-local-first-operability-and-secure-diagnostic-defaults.md) |
| M20 | Release / programme / données persistantes | [ADR-0088](../../adr/0088-product-release-installation-and-persistent-data-separation.md) |
| M21 | Intégrité production et convergence des surfaces | [ADR-0089](../../adr/0089-production-integrity-surface-convergence.md) |
| M22 | Provider SDK et plugin discovery | [ADR-0090](../../adr/0090-provider-sdk-plugin-discovery-platform.md) |
| M23 | Portfolio multi-projets | [ADR-0091](../../adr/0091-multi-project-portfolio-intelligence.md) |
| M24 | Query DSL / saved views / reporting | [ADR-0092](../../adr/0092-provider-neutral-query-dsl-saved-views-reporting.md) |
| M25 | Policy Packs / governance | [ADR-0093](../../adr/0093-provider-neutral-policy-packs-governance-automation.md) |
| M26 | Serveur d'équipe optionnel | [ADR-0094](../../adr/0094-optional-team-remote-server-mode.md) |
| M27 | Reasoning fondé sur preuves | [ADR-0095](../../adr/0095-evidence-backed-assisted-reasoning.md) |
| M28 | Intégration conservatrice des clients MCP | [ADR-0096](../../adr/0096-conservative-native-mcp-client-integration.md) |

---

## 9.4 Relation avec D2 et les MRA

D2 et les MRA post-R3 sont des travaux de hardening et de réconciliation de la
baseline 1.2.0. Ils ne doivent pas être artificiellement renumérotés comme des
milestones fonctionnels M29+ tant qu'une nouvelle décision architecturale n'est
pas formalisée.

Les changements de dépendances, sécurité, CI ou qualité issus de ce hardening
sont documentés par leurs plans et preuves (`docs/roadmap/`,
`docs/validation/`) et, lorsqu'ils changent une décision structurante, doivent
faire l'objet d'un nouvel ADR.

---

## 9.5 Prochaine décision

Aucun ADR-0097 n'est présupposé par cette documentation. Les sujets futurs
(stockage alternatif, IAM entreprise, nouvelle API majeure, changement de
système de build, etc.) restent des **candidats** et ne deviennent des décisions
qu'après besoin démontré, ADR dédié et qualification.
