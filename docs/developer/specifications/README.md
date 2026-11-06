# Spécifications consolidées par capacité

Ce dossier donne, pour chaque capacité OpenSpec, la vue durable qu'une spec ne porte pas : le comportement en
français, les diagrammes maintenus, les écarts connus et la traçabilité jusqu'aux tests. La spec de référence reste
la seule source normative : une page de ce dossier la résume et la relie, elle ne la remplace pas.

| Document | Rôle | Mis à jour par |
|---|---|---|
| `openspec/specs/<capacité>/spec.md` | exigences et scénarios normatifs, en vigueur | l'archivage d'un changement |
| `openspec/changes/<changement>/` | évolution proposée, conception propre au changement | le changement, jusqu'à son archivage |
| `docs/developer/specifications/<capacité>.md` | vue consolidée, diagrammes, écarts, traçabilité | le groupe de tâches qui change la capacité |
| `docs/architecture/arc42/` | vues d'ensemble, frontières, scénarios transverses | les changements d'architecture |
| `docs/architecture/adr/` | décisions durables et leurs conséquences | un ADR, selon `.claude/rules/adr.md` |

Le mode d'emploi du schéma qui produit les specs est dans [`../OPENSPEC_SCHEMA.md`](../OPENSPEC_SCHEMA.md).

## Pages disponibles

Une capacité absente de cette table n'a pas encore de page durable ; l'état de couverture de toutes les specs est
relevé dans [`AUDIT_SPECIFICATIONS_OPENSPEC_2026-11-03.md`](../../project/audits/AUDIT_SPECIFICATIONS_OPENSPEC_2026-11-03.md).

### Frontière HTTP locale et distante

Vue commune : la séquence de la façade distante, dans [`remote-access-journal.md`](remote-access-journal.md#séquence-de-la-façade-distante).

| Capacité | Page | Spec de référence |
|---|---|---|
| `http-path-decoding` | [lecture du chemin d'une requête](http-path-decoding.md) | [spec](../../../openspec/specs/http-path-decoding/spec.md) |
| `http-response-headers` | [en-têtes de sécurité des réponses](http-response-headers.md) | [spec](../../../openspec/specs/http-response-headers/spec.md) |
| `http-failure-disclosure` | [texte d'un échec rendu à un appelant HTTP](http-failure-disclosure.md) | [spec](../../../openspec/specs/http-failure-disclosure/spec.md) |
| `remote-access-journal` | [journal d'accès du serveur distant](remote-access-journal.md) | [spec](../../../openspec/specs/remote-access-journal/spec.md) |

### Surfaces publiques et adaptateurs CLI et MCP

| Capacité | Page | Spec de référence |
|---|---|---|
| `public-surface-convergence` | [convergence des surfaces publiques](public-surface-convergence.md) | [spec](../../../openspec/specs/public-surface-convergence/spec.md) |
| `adapter-capability-decomposition` | [commandes et outils servis par des classes de capacité](adapter-capability-decomposition.md) | [spec](../../../openspec/specs/adapter-capability-decomposition/spec.md) |
| `cli-argument-base` | [base d'arguments de la CLI](cli-argument-base.md) | [spec](../../../openspec/specs/cli-argument-base/spec.md) |
| `payload-map-construction` | [construction des cartes de réponse](payload-map-construction.md) | [spec](../../../openspec/specs/payload-map-construction/spec.md) |
| `mcp-tool-failure-contract` | [échec d'un outil MCP et borne d'un appel](mcp-tool-failure-contract.md) | [spec](../../../openspec/specs/mcp-tool-failure-contract/spec.md) |
| `mcp-peer-launch-command` | [commande de lancement d'un pair MCP](mcp-peer-launch-command.md) | [spec](../../../openspec/specs/mcp-peer-launch-command/spec.md) |
| `launch-environment-disclosure` | [variables d'environnement documentées](launch-environment-disclosure.md) | [spec](../../../openspec/specs/launch-environment-disclosure/spec.md) |

### Persistance SQLite et stores

| Capacité | Page | Spec de référence |
|---|---|---|
| `sqlite-connection-policy` | [politique de connexion SQLite d'une opération](sqlite-connection-policy.md) | [spec](../../../openspec/specs/sqlite-connection-policy/spec.md) |
| `sqlite-index-coverage` | [couverture des index SQLite](sqlite-index-coverage.md) | [spec](../../../openspec/specs/sqlite-index-coverage/spec.md) |
| `sqlite-read-shaping` | [filtre et ordre des lectures SQLite](sqlite-read-shaping.md) | [spec](../../../openspec/specs/sqlite-read-shaping/spec.md) |
| `sqlite-schema-history` | [historique des migrations du schéma SQLite](sqlite-schema-history.md) | [spec](../../../openspec/specs/sqlite-schema-history/spec.md) |
| `sqlite-store-packages` | [paquets du store SQLite](sqlite-store-packages.md) | [spec](../../../openspec/specs/sqlite-store-packages/spec.md) |
| `store-closed-refusal` | [refus d'un store SQLite fermé](store-closed-refusal.md) | [spec](../../../openspec/specs/store-closed-refusal/spec.md) |
| `released-schema-upgrade` | [mise à niveau depuis chaque schéma publié](released-schema-upgrade.md) | [spec](../../../openspec/specs/released-schema-upgrade/spec.md) |
| `schema-upgrade-guidance` | [guide de mise à niveau et schéma SQLite](schema-upgrade-guidance.md) | [spec](../../../openspec/specs/schema-upgrade-guidance/spec.md) |
| `requirement-page-reads` | [lecture paginée des exigences d'un snapshot](requirement-page-reads.md) | [spec](../../../openspec/specs/requirement-page-reads/spec.md) |
| `append-only-journal-reads` | [lecture des journaux en ajout seul](append-only-journal-reads.md) | [spec](../../../openspec/specs/append-only-journal-reads/spec.md) |
| `traceability-link-batch-write` | [écriture d'un lot de liens de traçabilité](traceability-link-batch-write.md) | [spec](../../../openspec/specs/traceability-link-batch-write/spec.md) |
| `policy-store-refusals` | [refus d'écriture du store de policies](policy-store-refusals.md) | [spec](../../../openspec/specs/policy-store-refusals/spec.md) |
| `policy-audit-order` | [ordre de l'audit des policies](policy-audit-order.md) | [spec](../../../openspec/specs/policy-audit-order/spec.md) |

### Frontières de modules et domaine

| Capacité | Page | Spec de référence |
|---|---|---|
| `adapter-internal-boundaries` | [frontières internes d'un adaptateur de transport](adapter-internal-boundaries.md) | [spec](../../../openspec/specs/adapter-internal-boundaries/spec.md) |
| `application-infrastructure-ports` | [technologie sortie de l'application derrière un port](application-infrastructure-ports.md) | [spec](../../../openspec/specs/application-infrastructure-ports/spec.md) |
| `application-package-cycles` | [paquets de l'application sans cycle](application-package-cycles.md) | [spec](../../../openspec/specs/application-package-cycles/spec.md) |
| `application-technology-boundary` | [technologies que la couche application peut toucher](application-technology-boundary.md) | [spec](../../../openspec/specs/application-technology-boundary/spec.md) |
| `module-package-correspondence` | [correspondance entre modules et paquets](module-package-correspondence.md) | [spec](../../../openspec/specs/module-package-correspondence/spec.md) |
| `provider-sdk-dependency-boundary` | [frontière de dépendance du SDK de provider](provider-sdk-dependency-boundary.md) | [spec](../../../openspec/specs/provider-sdk-dependency-boundary/spec.md) |
| `reflection-boundary` | [réflexion, chargement de classes et injection](reflection-boundary.md) | [spec](../../../openspec/specs/reflection-boundary/spec.md) |
| `record-construction` | [construction d'un record du produit](record-construction.md) | [spec](../../../openspec/specs/record-construction/spec.md) |
| `domain-value-decisions` | [décisions sur une valeur du domaine](domain-value-decisions.md) | [spec](../../../openspec/specs/domain-value-decisions/spec.md) |
| `constraint-blocking-decision` | [décision de blocage d'une contrainte](constraint-blocking-decision.md) | [spec](../../../openspec/specs/constraint-blocking-decision/spec.md) |
| `operational-runtime-injection` | [injection du runtime opérationnel](operational-runtime-injection.md) | [spec](../../../openspec/specs/operational-runtime-injection/spec.md) |
| `project-ingestion-port` | [ingestion d'un projet par le port de fournisseur](project-ingestion-port.md) | [spec](../../../openspec/specs/project-ingestion-port/spec.md) |
| `text-digest-and-key-derivation` | [empreinte d'un texte et clé stable d'un titre](text-digest-and-key-derivation.md) | [spec](../../../openspec/specs/text-digest-and-key-derivation/spec.md) |
| `dead-code-refusal` | [refus du code mort et des branches inatteignables](dead-code-refusal.md) | [spec](../../../openspec/specs/dead-code-refusal/spec.md) |

### Gates, preuves et dépendances

| Capacité | Page | Spec de référence |
|---|---|---|
| `gate-reachability` | [atteignabilité des gates et des validateurs](gate-reachability.md) | [spec](../../../openspec/specs/gate-reachability/spec.md) |
| `gate-verdict-currency` | [actualité du verdict d'un gate](gate-verdict-currency.md) | [spec](../../../openspec/specs/gate-verdict-currency/spec.md) |
| `validator-self-test` | [auto-test des validateurs](validator-self-test.md) | [spec](../../../openspec/specs/validator-self-test/spec.md) |
| `coverage-attribution-gap` | [crédit de couverture emprunté](coverage-attribution-gap.md) | [spec](../../../openspec/specs/coverage-attribution-gap/spec.md) |
| `coverage-qualification-evidence` | [preuve de qualification d'un plafond de couverture](coverage-qualification-evidence.md) | [spec](../../../openspec/specs/coverage-qualification-evidence/spec.md) |
| `enforcement-mechanism-accounting` | [décompte des mécanismes d'enforcement](enforcement-mechanism-accounting.md) | [spec](../../../openspec/specs/enforcement-mechanism-accounting/spec.md) |
| `architecture-rule-vacuity-guard` | [garde contre les règles d'architecture vides](architecture-rule-vacuity-guard.md) | [spec](../../../openspec/specs/architecture-rule-vacuity-guard/spec.md) |
| `refusal-test-evidence` | [preuve de la raison d'un refus dans un test](refusal-test-evidence.md) | [spec](../../../openspec/specs/refusal-test-evidence/spec.md) |
| `reactor-module-documentation` | [modules du reactor dans la documentation courante](reactor-module-documentation.md) | [spec](../../../openspec/specs/reactor-module-documentation/spec.md) |
| `repository-root-location` | [localisation de la racine du dépôt par les tests](repository-root-location.md) | [spec](../../../openspec/specs/repository-root-location/spec.md) |
| `repository-credential-hygiene` | [hygiène des fichiers d'identité et de clés](repository-credential-hygiene.md) | [spec](../../../openspec/specs/repository-credential-hygiene/spec.md) |
| `warning-suppression-justification` | [justification des avertissements supprimés](warning-suppression-justification.md) | [spec](../../../openspec/specs/warning-suppression-justification/spec.md) |
| `dependency-line-currency` | [dépendance tenue sur une ligne de versions](dependency-line-currency.md) | [spec](../../../openspec/specs/dependency-line-currency/spec.md) |
| `dependency-version-management` | [gestion des versions de dépendances et de plugins](dependency-version-management.md) | [spec](../../../openspec/specs/dependency-version-management/spec.md) |
| `risk-register-references` | [renvois des workflows vers le registre des risques](risk-register-references.md) | `openspec/specs/risk-register-references/spec.md`, créée à l'archivage du changement `refuse-a-workflow-that-points-at-a-closed-risk` |
| `vulnerability-feed-freshness` | [fraîcheur de la base de vulnérabilités du gate CVE](vulnerability-feed-freshness.md) | [spec](../../../openspec/specs/vulnerability-feed-freshness/spec.md) |

### Exploitation, providers et coûts

| Capacité | Page | Spec de référence |
|---|---|---|
| `operational-log-mode` | [mode du journal opérationnel](operational-log-mode.md) | [spec](../../../openspec/specs/operational-log-mode/spec.md) |
| `logs-directory-truth` | [répertoire de logs annoncé et journal écrit](logs-directory-truth.md) | [spec](../../../openspec/specs/logs-directory-truth/spec.md) |
| `third-party-log-capture` | [capture des journaux des bibliothèques embarquées](third-party-log-capture.md) | [spec](../../../openspec/specs/third-party-log-capture/spec.md) |
| `suppressed-failure-trace` | [trace d'un échec écarté](suppressed-failure-trace.md) | [spec](../../../openspec/specs/suppressed-failure-trace/spec.md) |
| `openspec-provider-reading` | [lecture des espaces OpenSpec par le provider](openspec-provider-reading.md) | [spec](../../../openspec/specs/openspec-provider-reading/spec.md) |
| `provider-plugin-pinned-entry` | [entrée épinglée du code de plugin provider](provider-plugin-pinned-entry.md) | [spec](../../../openspec/specs/provider-plugin-pinned-entry/spec.md) |
| `portfolio-provider-identity` | [fournisseurs mémorisés par une appartenance de portefeuille](portfolio-provider-identity.md) | [spec](../../../openspec/specs/portfolio-provider-identity/spec.md) |
| `change-analysis-traversal-cost` | [coût de l'explication des dépendances d'un changement](change-analysis-traversal-cost.md) | [spec](../../../openspec/specs/change-analysis-traversal-cost/spec.md) |
| `workspace-boundary-scan-cost` | [frontière physique de l'espace de travail et coût d'un scan](workspace-boundary-scan-cost.md) | [spec](../../../openspec/specs/workspace-boundary-scan-cost/spec.md) |
| `change-orchestration-state` | [état d'orchestration d'un changement](change-orchestration-state.md) | [spec](../../../openspec/specs/change-orchestration-state/spec.md) |
| `release-component-disclosure` | [divulgation des composants d'une release](release-component-disclosure.md) | [spec](../../../openspec/specs/release-component-disclosure/spec.md) |

## Conventions

- **Exigences citées par leur titre exact**, dans la langue de la spec. Le titre est la clé de synchronisation
  d'OpenSpec : il n'est ni traduit ni renuméroté ici. Les alias `R1`, `R2`… d'une page ne valent que dans cette page.
- **Nature de chaque affirmation** : `[EXIGENCE]` (dit par la spec), `[EXISTANT VÉRIFIÉ]` (lu dans le code ou un test,
  avec sa référence), `[DÉCISION]` (ADR ou décision écrite), `[PROPOSITION]` (à valider, portée par un changement),
  `[QUESTION]` (à trancher). Un comportement vérifié dans le code mais absent de la spec n'est pas une exigence : il
  est signalé comme tel.
- **Diagrammes** : Mermaid. Chaque diagramme suit la question à laquelle il répond, dit s'il montre l'existant ou une
  cible, et nomme les exigences concernées. Un `flowchart` est un organigramme, pas un diagramme UML.
- **Preuve** : un test cité par chemin de fichier et nom de méthode. « Preuve manquante » est écrit en clair.
- **Changements** : un changement actif est cité par son nom en code, sans lien (l'archivage déplace son dossier) ;
  un changement archivé par son dossier daté sous `openspec/changes/archive/`.
- **Chiffres** : une borne citée renvoie à la constante ou au fichier qui la porte.

## Gabarit d'une page de capacité

```markdown
# <Titre en français> (`<capability-path>`)

Spec de référence : lien vers openspec/specs/<capability-path>/spec.md · Relevé du <date> sur <SHA>

## Objet et périmètre
Acteurs, déclencheurs, ce qui est inclus, ce qui est exclu.

## Exigences
| Alias | Exigence (titre exact) | En bref | Nature |

## Comportement détaillé
Entrées, validations, sorties, refus (code, message), effets persistés ou leur absence, bornes.

## Diagrammes
Question → diagramme → explication → exigences concernées.

## Écarts et questions ouvertes
Divergences code / tests / spec / documentation, et décisions attendues.

## Traçabilité
| Exigence | Scénario | Conception ou diagramme | Changement ou tâche | Preuve |

## Sources
ADR, archives OpenSpec, pages développeur liées.
```
