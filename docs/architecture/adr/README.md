# ADR — MORPHEUS

Ce répertoire est le registre unique des **Architecture Decision Records** de MORPHEUS, à l'emplacement que prévoit
arc42 (section 9, [`../arc42/09-decisions.md`](../arc42/09-decisions.md)). Archgate le lit directement
(`.archgate/config.json`, `paths.adrs`) ; la fiche d'usage est [`../../developer/ARCHGATE.md`](../../developer/ARCHGATE.md).

Une ADR dépendante d'une hypothèse technique n'est acceptée qu'après preuve. Une ADR acceptée n'est jamais supprimée :
elle est clarifiée sans changer de sens, ou remplacée par une ADR consolidée qui porte toute la décision en vigueur ;
l'ancienne rejoint l'[archive](archive/README.md), que ni cet index ni Archgate ne lisent.

## Statuts

- **Proposée** : décision candidate ;
- **Acceptée** : décision validée ;
- **Acceptée avec contraintes** : décision validée sous conditions ;
- **Remplacée** : supersédée ;
- **Rejetée** : option étudiée puis non retenue.

## Format

Chaque ADR suit le format Archgate ([ADR-0132](GEN-0132-tenir-le-registre-d-adr-au-format-archgate.md)) :

- fichier `{PRÉFIXE}-{NNNN}-{slug}.md`, préfixe du domaine (`architecture` → `ARCH`, `backend` → `BE`, `data` →
  `DATA`, `general` → `GEN`, `security` → `SEC`), numéro à quatre chiffres unique dans le registre et l'archive, pris
  à la suite du plus haut numéro attribué, jamais renuméroté ;
- en-tête YAML en ligne 1 (`id`, `title`, `domain`, `rules`, `files` facultatif), titre `#` égal au `title`, repris
  tel quel dans la ligne d'index ;
- statut et date dans le corps (`- Statut : **…**`) ;
- sections `Context`, `Decision`, `Do's and Don'ts`, `Consequences`, `Compliance and Enforcement`, `References`, en
  français, au présent ;
- une règle Archgate éventuelle à côté de son ADR (`{même nom}.rules.ts`), pour une contrainte qu'aucun test
  d'architecture ne tient.

Le texte cite une décision sous la forme `ADR-NNNN`. Gabarit : [`gabarit/template.md`](gabarit/template.md).

## Quand écrire un ADR

Un ADR mémorise une décision d'architecture **durable, en vigueur et coûteuse à inverser**. Avant d'en écrire un,
répondre « oui » aux trois questions — un seul « non » suffit, ce n'est pas un ADR :

1. **Décision** : fixe-t-il un choix entre plusieurs options réelles ?
2. **Portée et durée** : contraint-il durablement plusieurs modules, une surface publique (CLI, MCP, HTTP), un format
   persisté, un modèle de confiance ou de sécurité, la toolchain ou la livraison ?
3. **Coût du retour arrière** : l'inverser casserait-il des données, des contrats, des garanties de sécurité ou une
   part importante du code ?

| Ce n'est pas un ADR | Où l'écrire |
|---|---|
| état d'un jalon, preuve de qualification, SHA, mesure datée | `docs/project/validation/`, `docs/project/audits/` |
| plan, backlog, ordre de travail | `docs/project/roadmap/`, changement OpenSpec |
| exigence et scénarios d'un comportement | spécifications OpenSpec |
| étude, comparaison, option sans décision | `docs/project/research/` |
| description d'un mécanisme, mode d'emploi | `docs/developer/` ou `docs/user/` |
| convention locale, nommage, style | `.claude/rules/` ou un test d'architecture |
| correctif, fonctionnalité, classe | le commit et la PR |

Un ADR existant qui couvre le sujet se cite ou se clarifie ; une décision modifiée en partie se consolide dans un
nouvel ADR qui remplace les précédents. Le cycle de vie complet est dans `.claude/rules/adr.md`.

# Index

| ADR | Décision | Statut |
|---|---|---|
| [ADR-0001](ARCH-0001-morpheus-owned-domain.md) | Le domaine MORPHEUS est indépendant des formats et providers | **Acceptée avec contraintes** |
| [ADR-0002](ARCH-0002-openspec-reference-provider.md) | OpenSpec comme premier provider de référence, sans verrouillage du domaine | **Acceptée avec contraintes** |
| [ADR-0003](DATA-0003-specification-knowledge-store.md) | Isoler la persistance derrière `SpecificationKnowledgeStore` | **Acceptée** |
| [ADR-0004](SEC-0004-local-first-no-llm-core.md) | Le cœur MORPHEUS est local-first et ne dépend d'aucun LLM | **Acceptée** |
| [ADR-0006](ARCH-0006-current-vs-proposed-state.md) | Distinguer structurellement l'état courant des changements proposés | **Acceptée** |
| [ADR-0007](ARCH-0007-cross-engine-integration.md) | Les intégrations cross-engine utilisent des contrats explicites et découplés | **Acceptée** |
| [ADR-0008](SEC-0008-read-first-write-capability.md) | Le cœur MORPHEUS est read-first ; l'écriture est une capacité séparée | **Acceptée** |
| [ADR-0009](DATA-0009-stable-domain-identity.md) | Séparer l'identité logique, la version, l'emplacement et l'identifiant externe | **Acceptée** |
| [ADR-0011](BE-0011-provider-capability-negotiation.md) | Sélectionner les providers par capacités et non par type de source uniquement | **Acceptée** |
| [ADR-0015](DATA-0015-domain-identity-uuidv7.md) | Utiliser UUIDv7 comme format canonique de `DomainIdentity` | **Acceptée** |
| [ADR-0016](GEN-0016-java-21-production-baseline.md) | Utiliser Java 21 comme baseline de compatibilité MORPHEUS | **Acceptée** |
| [ADR-0017](GEN-0017-maven-build-foundation.md) | Utiliser Maven avec le Maven Wrapper comme fondation de build | **Acceptée avec contraintes** |
| [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) | Utiliser SQLite comme backend persistant initial derrière `SpecificationKnowledgeStore` | **Acceptée avec contraintes** |
| [ADR-0019](GEN-0019-maven-coordinates-java-namespace.md) | Stabiliser les coordonnées Maven et le namespace Java de MORPHEUS | **Acceptée** |
| [ADR-0020](BE-0020-workspace-root-resolution.md) | Résoudre la racine de workspace sans dépendance Git obligatoire | **Acceptée** |
| [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) | Versionner le schéma SQLite par migrations explicites et minimales | **Acceptée** |
| [ADR-0023](DATA-0023-persistent-provider-scoped-entity-identity.md) | Persister les identités d'entités par clé externe provider-scoped | **Acceptée** |
| [ADR-0026](ARCH-0026-optional-external-reference-resolution.md) | Références externes résolues par adapters optionnels | **Acceptée** |
| [ADR-0027](GEN-0027-native-distribution-without-container-image.md) | Distribution native, sans image Docker | **Acceptée avec contraintes** |
| [ADR-0028](BE-0028-unified-provider-read-contract.md) | Contrat de lecture provider unifié et résultats partiels explicites | **Acceptée** |
| [ADR-0029](ARCH-0029-second-provider-anti-lockin-proof.md) | Prouver l'anti-lock-in avec un second provider synthétique | **Acceptée** |
| [ADR-0031](ARCH-0031-explicit-temporal-projection-and-entity-version.md) | Projeter explicitement l'état temporel sur des occurrences versionnées | **Acceptée** |
| [ADR-0034](DATA-0034-versioned-requirement-persistence.md) | Introduire une persistance métier versionnée par occurrence | **Acceptée** |
| [ADR-0035](BE-0035-explicit-requirement-delta-application-and-promotion.md) | Séparer explicitement application, promotion et activation des RequirementDelta | **Acceptée** |
| [ADR-0036](DATA-0036-published-history-comparison-and-logical-rollback.md) | Historique publié, comparaison de snapshots et rollback logique | **Acceptée** |
| [ADR-0038](DATA-0038-snapshot-scoped-traceability-persistence.md) | Persistance de traçabilité snapshot-scoped | **Acceptée** |
| [ADR-0039](BE-0039-deterministic-traceability-derivation.md) | Dérivation déterministe de la traçabilité depuis le modèle normalisé | **Acceptée** |
| [ADR-0040](BE-0040-bounded-deterministic-traceability-traversal.md) | Traversée bornée et déterministe de la traçabilité | **Acceptée** |
| [ADR-0041](DATA-0041-snapshot-external-reference-traceability.md) | Références externes snapshot-scoped et traçabilité non résolue | **Acceptée** |
| [ADR-0043](ARCH-0043-deterministic-requirement-lexical-query.md) | Recherche lexicale déterministe et pagination des requirements | **Acceptée** |
| [ADR-0044](DATA-0044-snapshot-business-content-projection.md) | Projection métier requêtable snapshot-scoped | **Acceptée** |
| [ADR-0056](ARCH-0056-deterministic-current-proposed-change-analysis.md) | Analyse déterministe CURRENT / proposé des changements | **Acceptée** |
| [ADR-0057](ARCH-0057-explicit-bounded-dependency-impact-paths.md) | Impacts de dépendance explicites par chemins bornés | **Acceptée** |
| [ADR-0059](GEN-0059-stable-local-cli-contract.md) | Contrat CLI local stable, scriptable et explicite | **Acceptée** |
| [ADR-0061](GEN-0061-self-contained-jpackage-portable-distribution.md) | Distribution portable autonome via JAR ombré et jpackage app-image | **Acceptée** |
| [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) | Java MCP SDK officiel et transport STDIO natif | **Acceptée** |
| [ADR-0063](ARCH-0063-read-only-mcp-tool-contract.md) | Catalogue MCP read-only et sémantique explicite | **Acceptée** |
| [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) | JDK HttpServer pour l'API locale | **Acceptée** |
| [ADR-0066](ARCH-0066-versioned-http-api-contract.md) | Contrat HTTP `/api/v1` et erreurs JSON stables | **Acceptée** |
| [ADR-0067](BE-0067-explicit-conservative-http-sync.md) | Synchronisation HTTP explicite par full snapshot conservateur | **Acceptée** |
| [ADR-0070](BE-0070-exact-minos-symbol-reference-and-revision.md) | Référence MINOS par `symbolKey` exact et révision explicite | **Acceptée** |
| [ADR-0071](DATA-0071-live-external-resolution-without-snapshot-mutation.md) | Résolution externe live sans mutation d'un snapshot publié | **Acceptée** |
| [ADR-0074](BE-0074-explicit-nexus-project-mapping.md) | Mapping projet NEXUS explicite sans ownership de lifecycle | **Acceptée** |
| [ADR-0075](ARCH-0075-morpheus-intent-vs-nexus-technical-context.md) | Séparer l'intention MORPHEUS du contexte technique NEXUS | **Acceptée** |
| [ADR-0077](ARCH-0077-morpheus-jarvis-read-only-orchestration-boundary.md) | Frontière d'orchestration read-only MORPHEUS / JARVIS | **Acceptée** |
| [ADR-0079](BE-0079-nondestructive-change-orchestration-state.md) | État d'orchestration agrégé non destructif | **Acceptée** |
| [ADR-0080](ARCH-0080-jarvis-orchestration-surfaces-and-optional-client.md) | Surfaces d'orchestration et client JARVIS optionnel | **Acceptée** |
| [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) | AcceptanceCriterion et VerificationStatus first-class | **Acceptée** |
| [ADR-0082](DATA-0082-explicit-constraint-semantics-and-blocking-policy.md) | Sémantique explicite des contraintes et politique de blocage | **Acceptée** |
| [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) | Mutations lifecycle contrôlées, CAS, idempotency et audit | **Acceptée** |
| [ADR-0084](ARCH-0084-provider-neutral-multi-provider-composition.md) | Composition multi-provider provider-neutral, déterministe et explicable | **Acceptée** |
| [ADR-0085](GEN-0085-predeclared-performance-budgets-and-deterministic-large-fixtures.md) | Budgets de performance pré-déclarés et fixtures larges déterministes | **Acceptée** |
| [ADR-0086](DATA-0086-failure-atomic-snapshot-recovery-and-bounded-sqlite-concurrency.md) | Recovery failure-atomic des snapshots et concurrence SQLite bornée | **Acceptée** |
| [ADR-0087](SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) | Opérabilité local-first et diagnostics sûrs par défaut | **Acceptée** |
| [ADR-0088](GEN-0088-product-release-installation-and-persistent-data-separation.md) | Product release, installation and persistent-data separation | **Acceptée** |
| [ADR-0089](GEN-0089-production-integrity-surface-convergence.md) | Production integrity and public-surface convergence | **Acceptée** |
| [ADR-0091](DATA-0091-multi-project-portfolio-intelligence.md) | Multi-project portfolio intelligence | **Acceptée** |
| [ADR-0092](ARCH-0092-provider-neutral-query-dsl-saved-views-reporting.md) | Provider-neutral query DSL, saved views and reporting | **Acceptée** |
| [ADR-0093](ARCH-0093-provider-neutral-policy-packs-governance-automation.md) | Provider-neutral policy packs and governance automation | **Acceptée** |
| [ADR-0094](SEC-0094-optional-team-remote-server-mode.md) | Optional team / remote server mode | **Acceptée** |
| [ADR-0095](ARCH-0095-evidence-backed-assisted-reasoning.md) | Evidence-backed assisted reasoning | **Acceptée** |
| [ADR-0096](GEN-0096-conservative-native-mcp-client-integration.md) | Intégration conservatrice des clients MCP natifs | **Acceptée** |
| [ADR-0099](SEC-0099-bounded-response-lifetime-on-the-remote-facade.md) | Durée de vie bornée d'une réponse sur la façade remote | **Acceptée** |
| [ADR-0100](SEC-0100-identity-audit-is-evidence-not-authority.md) | L'audit d'identités est une preuve, pas une autorité | **Acceptée** |
| [ADR-0102](BE-0102-mcp-failure-contract-is-one-rule.md) | Le contrat d'échec MCP est une règle unique, pas une convention par outil | **Acceptée** |
| [ADR-0104](GEN-0104-two-coverage-scales-share-one-population.md) | Deux échelles de couverture, une population commune : elles ne diffèrent que par l'attribution | **Acceptée** |
| [ADR-0105](SEC-0105-no-secret-vault-no-reusable-secret-at-rest.md) | Pas de coffre de secrets : MORPHEUS ne stocke aucun secret réutilisable, et le seul qu'il lit vient de l'environnement | **Acceptée** |
| [ADR-0106](BE-0106-inbound-bound-is-defensive-outbound-bound-is-a-defect.md) | Une borne entrante est défensive, une borne sortante est un défaut interne | **Acceptée** |
| [ADR-0108](BE-0108-a-response-says-what-it-could-not-observe.md) | Une réponse dit ce qu'elle n'a pas pu observer : traversée bornée, budget de policy, ratio indéfini | **Acceptée** |
| [ADR-0109](ARCH-0109-transport-adapters-are-named-composition-roots.md) | Les adaptateurs de transport sont des racines de composition nommées, et leur liste ne fait que rétrécir | **Acceptée** |
| [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) | Une copie de la base est conservée avant que son schéma n'avance, et sans elle la migration n'a pas lieu | **Acceptée** |
| [ADR-0111](BE-0111-the-product-ships-one-logging-binding-its-own.md) | Le produit embarque un seul binding de journalisation, le sien | **Acceptée** |
| [ADR-0112](BE-0112-the-logs-directory-holds-a-bounded-operational-log.md) | Le répertoire de logs contient le journal opérationnel, borné, quand l'opérateur le demande | **Acceptée** |
| [ADR-0113](SEC-0113-the-remote-server-records-who-was-authorized-to-do-what.md) | Le serveur distant consigne qui a été autorisé à faire quoi | **Acceptée** |
| [ADR-0114](BE-0114-database-work-runs-on-a-bounded-pool-of-platform-threads.md) | Le travail sur la base s'exécute sur un pool borné de threads plateforme | **Acceptée** |
| [ADR-0115](ARCH-0115-the-operational-runtime-is-a-value-the-launcher-hands-on.md) | Le runtime opérationnel est une valeur que le lanceur remet ; le parallélisme JUnit en dépend | **Acceptée** |
| [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) | Tracer par des liens typés selon une taxonomie contrôlée | **Acceptée** |
| [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) | Évaluer le cycle de vie d'un changement par une machine d'état canonique et une transition tri-état | **Acceptée** |
| [ADR-0120](DATA-0120-snapshots-versionnes-et-activation-atomique.md) | Publier la connaissance par snapshots versionnés à activation atomique | **Acceptée** |
| [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) | Normaliser le contenu des providers sans temporalité ni effet appliqué | **Acceptée** |
| [ADR-0122](ARCH-0122-lectures-metier-sur-le-snapshot-publie.md) | Les lectures métier ne voient que le snapshot publié | **Acceptée** |
| [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) | Vues compactes typées et JSON canonique | **Acceptée** |
| [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) | Findings de qualité explicables et faits tri-état | **Acceptée** |
| [ADR-0125](DATA-0125-detection-de-changement-des-sources.md) | Détecter les changements de source par inventaire SHA-256, reconstruire en entier en cas de doute | **Acceptée** |
| [ADR-0126](DATA-0126-publication-complete-d-un-snapshot.md) | Publier un snapshot complet sous une ancre BUILDING, et dire dans le reçu ce qui a été exécuté | **Acceptée** |
| [ADR-0127](GEN-0127-un-launcher-natif-unique.md) | Un launcher natif unique pour la CLI, le serveur MCP STDIO et l'API HTTP | **Acceptée** |
| [ADR-0128](ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) | MINOS et NEXUS sont des pairs optionnels, lancés à la demande par MCP STDIO | **Acceptée** |
| [ADR-0129](SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) | Un plugin provider est du code de confiance, sondé hors du processus MORPHEUS | **Acceptée** |
| [ADR-0130](BE-0130-contrat-des-options-de-la-cli.md) | Une invocation de la CLI est validée avant tout effet, et son code de sortie dit ce qui a été observé | **Acceptée** |
| [ADR-0131](BE-0131-pagination-des-collections-et-journaux.md) | Une page parle un seul vocabulaire, et un journal est conservé et lu par pages | **Acceptée** |
| [ADR-0132](GEN-0132-tenir-le-registre-d-adr-au-format-archgate.md) | Tenir le registre d'ADR au format Archgate, à l'emplacement arc42 | **Acceptée** |

Les preuves de qualification de chaque jalon sont dans [`../../project/validation/`](../../project/validation/README.md).

# Contraintes actives principales

```text
domain/application -X-> provider/store/cli/mcp/api/integration
API -X-> CLI/MCP/integration
provider-specific types -X-> domain/application contracts
plugin discovery != plugin activation
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
APPLY != PROMOTE != ACTIVATE
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
UNKNOWN != BLOCKED
precedence != provenance erasure
conflict != silent last-write-wins
cross-project identity != source path
portfolio membership != source ownership
traversal is bounded and explainable
DSL != SQL passthrough
saved view != materialized truth
export != mutation
constraint text != executable policy
policy recommendation != applied mutation
dry-run != mutation
local mode remains first-class
remote mode is opt-in
remote mode requires TLS + authentication
authentication != authorization
READ != WRITE != ADMIN
backup != live restore
facts != inference
inference != suggestion
heuristic != published fact
confidence is explicit and bounded
assisted output cites evidence
adapter discovery != adapter execution
adapter absence != MORPHEUS failure
adapter failure != fact loss
reasoning execution != lifecycle mutation
reasoning execution != policy override
surface parity != same transport shape
native MCP first
third-party client config mutation is opt-in
ownership is recorded, never guessed
uninstall is state-driven
manual client changes are preserved
```

# Principe de validation

```text
1. documenter l'invariant / ADR
2. implémenter le plus petit vertical slice
3. prouver le comportement par tests reproductibles
4. accepter l'ADR seulement après la preuve
5. exécuter le gate complet
6. mettre à jour roadmap + issue après validation
7. fusionner uniquement après respect des gates
8. réconcilier roadmaps/index après merge
```
