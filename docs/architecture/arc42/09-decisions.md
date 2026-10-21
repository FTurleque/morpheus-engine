# §9 — Décisions architecturales

Les ADR produit sont stockés dans [`../adr/`](../adr/). Cette section
fournit une vue architecturale synthétique ; le contenu de chaque ADR reste la
source autoritative de sa décision, de son statut et de ses preuves.

Cette section est une synthèse par thème, pas l'index complet. La baseline 1.2.0 porte les ADR
**ADR-0001 à ADR-0096** (9.1 à 9.3) ; ceux qui la suivent sont en 9.5. L'index de tous les ADR, avec leur statut, est
`docs/architecture/adr/README.md` ; `AdrIndexCoherenceTest` vérifie qu'il correspond aux fichiers.

---

## 9.1 Décisions fondatrices

| Sujet | ADR |
|-------|-----|
| Domaine indépendant des providers | [ADR-0001](../adr/0001-morpheus-owned-domain.md) |
| OpenSpec comme premier provider sans verrouillage | [ADR-0002](../adr/0002-openspec-reference-provider.md) |
| Store derrière un port MORPHEUS | [ADR-0003](../adr/0003-specification-knowledge-store.md) |
| Cœur local-first sans LLM obligatoire | [ADR-0004](../adr/0004-local-first-no-llm-core.md) |
| CURRENT / PROPOSED / HISTORICAL séparés | [ADR-0006](../adr/0006-current-vs-proposed-state.md) |
| Intégrations cross-engine découplées | [ADR-0007](../adr/0007-cross-engine-integration.md) |
| Providers read-first | [ADR-0008](../adr/0008-read-first-write-capability.md) |
| Identité stable | [ADR-0009](../adr/0009-stable-domain-identity.md) |
| Snapshot versionné et activation atomique | [ADR-0012](../adr/0012-snapshot-versioning-strategy.md) |
| Java 21 | [ADR-0016](../adr/0016-java-21-production-baseline.md) |
| Maven + Wrapper | [ADR-0017](../adr/0017-maven-build-foundation.md) |
| SQLite initial derrière port | [ADR-0018](../adr/0018-sqlite-initial-persistent-store.md) |

---

## 9.2 Surfaces, distribution et intégrations

| Sujet | ADR |
|-------|-----|
| Distribution native-first | [ADR-0027](../adr/0027-native-first-container-supported-distribution.md) |
| CLI stable | [ADR-0059](../adr/0059-stable-local-cli-contract.md) |
| Distribution jpackage autonome | [ADR-0061](../adr/0061-self-contained-jpackage-portable-distribution.md) |
| MCP Java SDK + STDIO | [ADR-0062](../adr/0062-official-java-mcp-sdk-and-native-stdio.md) |
| Contrat MCP read-only initial | [ADR-0063](../adr/0063-read-only-mcp-tool-contract.md) |
| Launcher MCP natif | [ADR-0064](../adr/0064-native-launcher-mcp-routing.md) |
| API HTTP locale | [ADR-0065](../adr/0065-jdk-httpserver-local-api.md) |
| Contrat HTTP `/api/v1` | [ADR-0066](../adr/0066-versioned-http-api-contract.md) |
| MINOS via MCP STDIO | [ADR-0069](../adr/0069-minos-mcp-stdio-integration.md) |
| NEXUS via MCP STDIO | [ADR-0073](../adr/0073-nexus-mcp-stdio-integration.md) |
| Frontière MORPHEUS / JARVIS | [ADR-0077](../adr/0077-morpheus-jarvis-read-only-orchestration-boundary.md) |

---

## 9.3 Plateformes fonctionnelles récentes

| Milestone | Décision structurante | ADR |
|-----------|------------------------|-----|
| M14 | Lifecycle explicite et évaluation de transition tri-state | [ADR-0078](../adr/0078-explicit-lifecycle-and-tristate-transition-evaluation.md) |
| M14 | État d'orchestration non destructif | [ADR-0079](../adr/0079-nondestructive-change-orchestration-state.md) |
| M14 | Surfaces d'orchestration et client JARVIS optionnel | [ADR-0080](../adr/0080-jarvis-orchestration-surfaces-and-optional-client.md) |
| M15 | Critères d'acceptation et statut de vérification | [ADR-0081](../adr/0081-first-class-acceptance-verification-evidence.md) |
| M16 | Sémantique des contraintes et politique de blocage | [ADR-0082](../adr/0082-explicit-constraint-semantics-and-blocking-policy.md) |
| M17 | Controlled lifecycle writes | [ADR-0083](../adr/0083-controlled-lifecycle-write-operations.md) |
| M18 | Composition multi-provider | [ADR-0084](../adr/0084-provider-neutral-multi-provider-composition.md) |
| M19 | Budgets de performance | [ADR-0085](../adr/0085-predeclared-performance-budgets-and-deterministic-large-fixtures.md) |
| M19 | Recovery atomique / concurrence SQLite | [ADR-0086](../adr/0086-failure-atomic-snapshot-recovery-and-bounded-sqlite-concurrency.md) |
| M19 | Opérabilité local-first | [ADR-0087](../adr/0087-local-first-operability-and-secure-diagnostic-defaults.md) |
| M20 | Release / programme / données persistantes | [ADR-0088](../adr/0088-product-release-installation-and-persistent-data-separation.md) |
| M21 | Intégrité production et convergence des surfaces | [ADR-0089](../adr/0089-production-integrity-surface-convergence.md) |
| M22 | Provider SDK et plugin discovery | [ADR-0090](../adr/0090-provider-sdk-plugin-discovery-platform.md) |
| M23 | Portfolio multi-projets | [ADR-0091](../adr/0091-multi-project-portfolio-intelligence.md) |
| M24 | Query DSL / saved views / reporting | [ADR-0092](../adr/0092-provider-neutral-query-dsl-saved-views-reporting.md) |
| M25 | Policy Packs / governance | [ADR-0093](../adr/0093-provider-neutral-policy-packs-governance-automation.md) |
| M26 | Serveur d'équipe optionnel | [ADR-0094](../adr/0094-optional-team-remote-server-mode.md) |
| M27 | Reasoning fondé sur preuves | [ADR-0095](../adr/0095-evidence-backed-assisted-reasoning.md) |
| M28 | Intégration conservatrice des clients MCP | [ADR-0096](../adr/0096-conservative-native-mcp-client-integration.md) |

---

## 9.4 Relation avec D2 et les MRA

D2 et les MRA post-R3 sont des travaux de hardening et de réconciliation de la
baseline 1.2.0. Ils ne doivent pas être artificiellement renumérotés comme des
milestones fonctionnels M29+ tant qu'une nouvelle décision architecturale n'est
pas formalisée.

Les changements de dépendances, sécurité, CI ou qualité issus de ce hardening
sont documentés par leurs plans et preuves (`docs/project/roadmap/`,
`docs/project/validation/`) et, lorsqu'ils changent une décision structurante, doivent
faire l'objet d'un nouvel ADR.

---

## 9.5 Décisions postérieures à la baseline 1.2.0

Décisions du durcissement qui a suivi la baseline (audit 1.2.1 et travaux postérieurs). Elles sont toutes acceptées ;
ADR-0117 remplace en partie la section « Sync M7 » d'ADR-0060.

| Sujet | ADR |
|-------|-----|
| Sémantique de version d'une publication complète et candidats en échec | [ADR-0097](../adr/0097-full-publish-version-semantics-and-failed-candidates.md) |
| Le runtime embarqué conserve son lanceur Java | [ADR-0098](../adr/0098-embedded-runtime-retains-its-java-launcher.md) |
| Durée de vie bornée d'une réponse de la façade remote | [ADR-0099](../adr/0099-bounded-response-lifetime-on-the-remote-facade.md) |
| L'audit d'identités est une preuve, pas une autorité | [ADR-0100](../adr/0100-identity-audit-is-evidence-not-authority.md) |
| Le code externe approuvé est du code de confiance, non sandboxé | [ADR-0101](../adr/0101-external-code-is-trusted-code-not-sandboxed-code.md) |
| Contrat d'échec MCP unique | [ADR-0102](../adr/0102-mcp-failure-contract-is-one-rule.md) |
| Assertion textuelle ou règle ArchUnit | [ADR-0103](../adr/0103-textual-assertions-and-archunit-rules-enforce-different-things.md) |
| Deux échelles de couverture, une population | [ADR-0104](../adr/0104-two-coverage-scales-share-one-population.md) |
| Pas de coffre de secrets | [ADR-0105](../adr/0105-no-secret-vault-no-reusable-secret-at-rest.md) |
| Borne entrante défensive, borne sortante défaut | [ADR-0106](../adr/0106-inbound-bound-is-defensive-outbound-bound-is-a-defect.md) |
| Vocabulaire unique d'une réponse paginée | [ADR-0107](../adr/0107-one-vocabulary-for-a-paginated-response.md) |
| Une réponse dit ce qu'elle n'a pas pu observer | [ADR-0108](../adr/0108-a-response-says-what-it-could-not-observe.md) |
| Adaptateurs de transport : racines de composition nommées | [ADR-0109](../adr/0109-transport-adapters-are-named-composition-roots.md) |
| Copie de la base avant l'avance du schéma | [ADR-0110](../adr/0110-a-copy-is-kept-before-the-schema-advances.md) |
| Un seul binding de journalisation | [ADR-0111](../adr/0111-the-product-ships-one-logging-binding-its-own.md) |
| Journal opérationnel borné | [ADR-0112](../adr/0112-the-logs-directory-holds-a-bounded-operational-log.md) |
| Journal d'accès du serveur remote | [ADR-0113](../adr/0113-the-remote-server-records-who-was-authorized-to-do-what.md) |
| Pool borné de threads plateforme pour la base | [ADR-0114](../adr/0114-database-work-runs-on-a-bounded-pool-of-platform-threads.md) |
| Runtime opérationnel passé comme valeur | [ADR-0115](../adr/0115-the-operational-runtime-is-a-value-the-launcher-hands-on.md) |
| Journaux conservés et lus par pages | [ADR-0116](../adr/0116-the-journals-are-kept-and-read-by-pages.md) |
| Le reçu d'un sync dit ce qui a été exécuté | [ADR-0117](../adr/0117-the-sync-receipt-says-what-was-executed.md) |

---

## 9.6 Prochaine décision

Aucun ADR au-delà du plus haut numéro du registre n'est présupposé par cette documentation. Les sujets futurs
(stockage alternatif, IAM entreprise, nouvelle API majeure, changement de
système de build, etc.) restent des **candidats** et ne deviennent des décisions
qu'après besoin démontré, ADR dédié et qualification.
