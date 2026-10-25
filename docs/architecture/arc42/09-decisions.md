# §9 — Décisions architecturales

Les décisions de MORPHEUS vivent dans le registre [`../adr/`](../adr/README.md), seul registre du dépôt, lu par
Archgate. Chaque ADR est la source de sa décision et de son statut ; cette section les range par thème. L'index
du registre, que `AdrIndexCoherenceTest` confronte aux fichiers, donne le statut de chacun ; les décisions
remplacées sont dans l'[archive](../adr/archive/README.md).

---

## 9.1 Fondations du modèle

Domaine, identité, états, persistance et socle technique.

| Décision | ADR |
|---|---|
| Le domaine appartient à MORPHEUS, et sa neutralité se prouve sur plusieurs providers | [ADR-0135](../adr/ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) |
| OpenSpec comme premier provider de référence, sans verrouillage du domaine | [ADR-0002](../adr/ARCH-0002-openspec-reference-provider.md) |
| Isoler la persistance derrière `SpecificationKnowledgeStore` | [ADR-0003](../adr/DATA-0003-specification-knowledge-store.md) |
| Le cœur MORPHEUS est local-first et ne dépend d'aucun LLM | [ADR-0004](../adr/SEC-0004-local-first-no-llm-core.md) |
| L'état temporel est une donnée normalisée, portée par des occurrences versionnées | [ADR-0136](../adr/ARCH-0136-etat-temporel-et-occurrences-versionnees.md) |
| Les intégrations cross-engine utilisent des contrats explicites et découplés | [ADR-0007](../adr/ARCH-0007-cross-engine-integration.md) |
| Le cœur MORPHEUS est read-first ; l'écriture est une capacité séparée | [ADR-0008](../adr/SEC-0008-read-first-write-capability.md) |
| Identité de domaine : opaque, UUIDv7, distincte du locator, persistée par clé scopée par provider | [ADR-0140](../adr/DATA-0140-identite-de-domaine.md) |
| Normaliser le contenu des providers sans temporalité ni effet appliqué | [ADR-0121](../adr/ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) |
| Tracer par des liens typés selon une taxonomie contrôlée | [ADR-0118](../adr/ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) |
| Évaluer le cycle de vie d'un changement par une machine d'état canonique et une transition tri-état | [ADR-0119](../adr/BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) |
| Snapshots versionnés, activation atomique, récupération et concurrence bornées | [ADR-0143](../adr/DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) |
| Toolchain de build : Java 21 et Maven par Wrapper épinglé | [ADR-0133](../adr/GEN-0133-toolchain-java-21-et-maven-wrapper.md) |
| Utiliser SQLite comme backend persistant initial derrière `SpecificationKnowledgeStore` | [ADR-0018](../adr/DATA-0018-sqlite-initial-persistent-store.md) |
| Stabiliser les coordonnées Maven et le namespace Java de MORPHEUS | [ADR-0019](../adr/GEN-0019-maven-coordinates-java-namespace.md) |
| Versionner le schéma SQLite par migrations explicites et minimales | [ADR-0021](../adr/DATA-0021-sqlite-schema-migrations-foundation.md) |

---

## 9.2 Surfaces, distribution et intégrations

Ce que MORPHEUS expose, comment il se livre, à qui il parle.

| Décision | ADR |
|---|---|
| Distribution native autonome, sans image de conteneur | [ADR-0134](../adr/GEN-0134-distribution-native-autonome.md) |
| Un launcher natif unique pour la CLI, le serveur MCP STDIO et l'API HTTP | [ADR-0127](../adr/GEN-0127-un-launcher-natif-unique.md) |
| Product release, installation and persistent-data separation | [ADR-0088](../adr/GEN-0088-product-release-installation-and-persistent-data-separation.md) |
| Contrat CLI local stable, scriptable et explicite | [ADR-0059](../adr/GEN-0059-stable-local-cli-contract.md) |
| Une invocation de la CLI est validée avant tout effet, et son code de sortie dit ce qui a été observé | [ADR-0130](../adr/BE-0130-contrat-des-options-de-la-cli.md) |
| Java MCP SDK officiel et transport STDIO natif | [ADR-0062](../adr/ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) |
| Catalogue MCP read-only et sémantique explicite | [ADR-0063](../adr/ARCH-0063-read-only-mcp-tool-contract.md) |
| Le contrat d'échec MCP est une règle unique, pas une convention par outil | [ADR-0102](../adr/BE-0102-mcp-failure-contract-is-one-rule.md) |
| JDK HttpServer pour l'API locale | [ADR-0065](../adr/ARCH-0065-jdk-httpserver-local-api.md) |
| Contrat HTTP `/api/v1` et erreurs JSON stables | [ADR-0066](../adr/ARCH-0066-versioned-http-api-contract.md) |
| Les adaptateurs de transport sont des racines de composition nommées, et leur liste ne fait que rétrécir | [ADR-0109](../adr/ARCH-0109-transport-adapters-are-named-composition-roots.md) |
| Un rôle de l'adaptateur HTTP est un paquet ; ce qui reste une liste de noms en déclare la sorte | [ADR-0147](../adr/ARCH-0147-un-role-d-adaptateur-est-un-paquet.md) |
| Le cœur applicatif touche le disque local par des classes nommées, et jamais le réseau | [ADR-0148](../adr/ARCH-0148-ce-que-le-coeur-applicatif-peut-toucher.md) |
| Une commande CLI et un outil MCP sont servis par une classe de capacité ; un dispatcher ne tient aucun cas d'usage | [ADR-0149](../adr/ARCH-0149-une-commande-et-un-outil-sont-servis-par-une-classe-de-capacite.md) |
| MINOS et NEXUS sont des pairs optionnels, lancés à la demande par MCP STDIO | [ADR-0128](../adr/ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) |
| Une ressource d'un pair MCP se désigne explicitement, sans heuristique ni mutation du pair | [ADR-0145](../adr/BE-0145-ressource-de-pair-mcp-designee-explicitement.md) |
| Séparer l'intention MORPHEUS du contexte technique NEXUS | [ADR-0075](../adr/ARCH-0075-morpheus-intent-vs-nexus-technical-context.md) |
| Frontière MORPHEUS / JARVIS : un contrat machine en lecture seule | [ADR-0139](../adr/ARCH-0139-frontiere-morpheus-jarvis-en-lecture-seule.md) |
| Intégration conservatrice des clients MCP natifs | [ADR-0096](../adr/GEN-0096-conservative-native-mcp-client-integration.md) |

---

## 9.3 Plateformes fonctionnelles

Lecture, analyse, qualité, synchronisation et plateformes métier.

| Décision | ADR |
|---|---|
| Sélectionner les providers par capacités et non par type de source uniquement | [ADR-0011](../adr/BE-0011-provider-capability-negotiation.md) |
| Résoudre la racine de workspace sans dépendance Git obligatoire | [ADR-0020](../adr/BE-0020-workspace-root-resolution.md) |
| Références externes : persistées par snapshot, résolues sur demande, observées sans mutation | [ADR-0142](../adr/DATA-0142-references-externes.md) |
| Contrat de lecture provider unifié et résultats partiels explicites | [ADR-0028](../adr/BE-0028-unified-provider-read-contract.md) |
| Contenu métier persisté par snapshot en tables typées | [ADR-0141](../adr/DATA-0141-contenu-metier-par-snapshot.md) |
| Séparer explicitement application, promotion et activation des RequirementDelta | [ADR-0035](../adr/BE-0035-explicit-requirement-delta-application-and-promotion.md) |
| Historique publié, comparaison de snapshots et rollback logique | [ADR-0036](../adr/DATA-0036-published-history-comparison-and-logical-rollback.md) |
| Persistance de traçabilité snapshot-scoped | [ADR-0038](../adr/DATA-0038-snapshot-scoped-traceability-persistence.md) |
| Dérivation déterministe de la traçabilité depuis le modèle normalisé | [ADR-0039](../adr/BE-0039-deterministic-traceability-derivation.md) |
| Traversée bornée et déterministe de la traçabilité | [ADR-0040](../adr/BE-0040-bounded-deterministic-traceability-traversal.md) |
| Lectures métier et recherche sur le snapshot publié | [ADR-0137](../adr/ARCH-0137-lectures-et-recherche-sur-le-snapshot-publie.md) |
| Analyse de changement dérivée, déterministe et bornée | [ADR-0138](../adr/ARCH-0138-analyse-de-changement-derivee-et-bornee.md) |
| AcceptanceCriterion et VerificationStatus first-class | [ADR-0081](../adr/DATA-0081-first-class-acceptance-verification-evidence.md) |
| Sémantique explicite des contraintes et politique de blocage | [ADR-0082](../adr/DATA-0082-explicit-constraint-semantics-and-blocking-policy.md) |
| Mutations lifecycle contrôlées, CAS, idempotency et audit | [ADR-0083](../adr/BE-0083-controlled-lifecycle-write-operations.md) |
| Composition multi-provider provider-neutral, déterministe et explicable | [ADR-0084](../adr/ARCH-0084-provider-neutral-multi-provider-composition.md) |
| Multi-project portfolio intelligence | [ADR-0091](../adr/DATA-0091-multi-project-portfolio-intelligence.md) |
| Provider-neutral query DSL, saved views and reporting | [ADR-0092](../adr/ARCH-0092-provider-neutral-query-dsl-saved-views-reporting.md) |
| Provider-neutral policy packs and governance automation | [ADR-0093](../adr/ARCH-0093-provider-neutral-policy-packs-governance-automation.md) |
| Evidence-backed assisted reasoning | [ADR-0095](../adr/ARCH-0095-evidence-backed-assisted-reasoning.md) |
| Vues compactes typées et JSON canonique | [ADR-0123](../adr/ARCH-0123-vues-compactes-et-json-canonique.md) |
| Findings de qualité explicables et faits tri-état | [ADR-0124](../adr/ARCH-0124-qualite-findings-et-faits-tri-etat.md) |
| Détecter les changements de source par inventaire SHA-256, reconstruire en entier en cas de doute | [ADR-0125](../adr/DATA-0125-detection-de-changement-des-sources.md) |
| Publication complète d'un snapshot, en CLI comme en HTTP | [ADR-0144](../adr/DATA-0144-publication-complete-cli-et-http.md) |
| Une page parle un seul vocabulaire, et un journal est conservé et lu par pages | [ADR-0131](../adr/BE-0131-pagination-des-collections-et-journaux.md) |

---

## 9.4 Exploitation, sécurité et qualité

Serveur distant, données, journaux, budgets et gouvernance.

| Décision | ADR |
|---|---|
| Budgets de performance pré-déclarés et fixtures larges déterministes | [ADR-0085](../adr/GEN-0085-predeclared-performance-budgets-and-deterministic-large-fixtures.md) |
| Opérabilité local-first et diagnostics sûrs par défaut | [ADR-0087](../adr/SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) |
| Production integrity and public-surface convergence | [ADR-0089](../adr/GEN-0089-production-integrity-surface-convergence.md) |
| Optional team / remote server mode | [ADR-0094](../adr/SEC-0094-optional-team-remote-server-mode.md) |
| Durée de vie bornée d'une réponse sur la façade remote | [ADR-0099](../adr/SEC-0099-bounded-response-lifetime-on-the-remote-facade.md) |
| L'audit d'identités est une preuve, pas une autorité | [ADR-0100](../adr/SEC-0100-identity-audit-is-evidence-not-authority.md) |
| Pas de coffre de secrets : MORPHEUS ne stocke aucun secret réutilisable, et le seul qu'il lit vient de l'environnement | [ADR-0105](../adr/SEC-0105-no-secret-vault-no-reusable-secret-at-rest.md) |
| Une borne entrante est défensive, une borne sortante est un défaut interne | [ADR-0106](../adr/BE-0106-inbound-bound-is-defensive-outbound-bound-is-a-defect.md) |
| Une copie de la base est conservée avant que son schéma n'avance, et sans elle la migration n'a pas lieu | [ADR-0110](../adr/DATA-0110-a-copy-is-kept-before-the-schema-advances.md) |
| Journal opérationnel : un seul binding SLF4J, un fichier borné à la demande | [ADR-0146](../adr/BE-0146-journal-operationnel-slf4j-et-fichier-borne.md) |
| Le serveur distant consigne qui a été autorisé à faire quoi | [ADR-0113](../adr/SEC-0113-the-remote-server-records-who-was-authorized-to-do-what.md) |
| Le travail sur la base s'exécute sur un pool borné de threads plateforme | [ADR-0114](../adr/BE-0114-database-work-runs-on-a-bounded-pool-of-platform-threads.md) |
| Le runtime opérationnel est une valeur que le lanceur remet ; le parallélisme JUnit en dépend | [ADR-0115](../adr/ARCH-0115-the-operational-runtime-is-a-value-the-launcher-hands-on.md) |
| Un plugin provider est du code de confiance, sondé hors du processus MORPHEUS | [ADR-0129](../adr/SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) |
| Deux échelles de couverture, une population commune : elles ne diffèrent que par l'attribution | [ADR-0104](../adr/GEN-0104-two-coverage-scales-share-one-population.md) |
| Tenir le registre d'ADR au format Archgate, à l'emplacement arc42 | [ADR-0132](../adr/GEN-0132-tenir-le-registre-d-adr-au-format-archgate.md) |

---

## 9.5 Prochaine décision

Aucune décision n'est présupposée au-delà du registre. Un sujet futur (stockage alternatif, IAM d'entreprise,
nouvelle API majeure, changement de système de build) devient une décision après besoin démontré, filtre de
`.claude/rules/adr.md` et ADR dédié.
