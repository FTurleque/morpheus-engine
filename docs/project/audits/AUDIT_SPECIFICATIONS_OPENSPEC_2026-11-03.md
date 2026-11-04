# Audit des spécifications OpenSpec — 2026-11-03

Relevé daté, établi sur `chore/sprint-15-medium` @ `836fffa5` avec OpenSpec 1.14.3, lors de la mise en place du
schéma de projet `detailed-uml` ([`../../developer/OPENSPEC_SCHEMA.md`](../../developer/OPENSPEC_SCHEMA.md)). Il
n'est pas réécrit : l'état courant de la couverture documentaire se lit dans
[`../../developer/specifications/README.md`](../../developer/specifications/README.md).

## Méthode

- Mesures structurelles par lecture des fichiers `spec.md` : nombre de `### Requirement:` et de `#### Scenario:`,
  scénarios dont le titre évoque une erreur, un refus ou une limite (heuristique lexicale, indicative seulement),
  présence d'étapes `GIVEN`, de diagrammes Mermaid et de liens.
- Validation : `openspec validate --all --strict --json`.
- Revue sémantique **des lots 1 et 2** : lecture des specs, du code, des tests cités et des ADR concernés ; pour le
  lot 2, relevé scénario par scénario délégué à deux agents de recherche, puis revérification des points structurants
  et de chaque nom de méthode cité. Les lots 3 à 6 n'ont reçu que les mesures structurelles.

## Constats globaux

| Constat | Valeur relevée |
|---|---|
| Specs principales | 64, toutes rédigées en anglais |
| Exigences / scénarios | 247 / 647 |
| Scénarios avec étape `GIVEN` | 0 (tous en `WHEN` / `THEN`, deux avec `AND`) |
| Diagrammes Mermaid dans les specs | 0 |
| Liens Markdown dans les specs | 0 avant ce relevé |
| `openspec validate --all` | 71 éléments, tous valides |
| `openspec validate --all --strict` | 4 specs échouent : une exigence de plus de 500 caractères dans `change-orchestration-state`, `gate-reachability`, `sqlite-store-packages`, `workspace-boundary-scan-cost` |
| Changements actifs | 7 en `spec-driven`, plus `state-what-the-http-boundary-already-does` créé en `detailed-uml` par ce relevé |
| Changements archivés | 71, non relus (les archives ne sont pas réécrites) |

### Incohérences relevées

1. **Capacités archivées absentes des specs principales.** Trois changements archivés portent une spec delta dont la
   capacité n'existe pas sous `openspec/specs/` : `2026-10-28-bound-every-upstream-call-of-the-remote-proxy`
   (`remote-proxy-upstream-bounds`), `2026-10-31-refuse-an-unsigned-or-wrongly-signed-windows-binary`
   (`windows-signature-verification`), `2026-10-31-sign-the-windows-installer-and-launcher`
   (`windows-release-signing`). Archivés sans application des deltas (probablement `--skip-specs`), ils laissent ces
   comportements sans spec de référence.
2. **Changement actif qui double un changement archivé.** `bound-every-write-of-the-remote-proxy` (actif, 11 tâches
   cochées sur 12) ajoute la capacité `remote-proxy-upstream-bounds`, que l'archive du 2026-10-28 portait déjà sans
   l'avoir consolidée.
3. **Exigence qui renvoie à une politique sans la dire.** `remote-access-journal` › « A record that cannot be written
   SHALL have a decided outcome » : la politique est celle de SEC-0113 §7 ; proposition dans
   `state-what-the-http-boundary-already-does`.
4. **Comportements testés non spécifiés** (lot 1) : `/` final unique ignoré, préfixe reconnu par segments et son
   message de refus, refus d'un segment non UTF-8, ligne `accepted` avant une écriture, refus de démarrer sans
   journal. Même proposition.
5. **Scénarios au passé.** `http-failure-disclosure` › « The four private helpers are gone » décrit l'état d'avant le
   changement qui a créé la capacité ; vrai comme interdit, historique dans sa formulation.
6. **Divergences du lot 2 qui demandent une décision** (sur `ef006fb7`, aucun changement de spec créé) :
   - `cli-argument-base` exige une seule ligne d'erreur ; plusieurs familles (`update-check`, `reason`, `server`,
     `provider-plugins`, intégrité produit) et `MorpheusMain` écrivent leur propre préfixe, figé par
     `CliOptionRefusalTableTest`. Tranché le même jour (unifier) : changement `report-every-cli-failure-through-one-error-line`,
     implémenté dans l'arbre de travail, non archivé ;
   - `launch-environment-disclosure` admet `docs/developer` ; le test ne lit que `docs/user` ;
   - `public-surface-convergence` admet une exclusion de l'OpenAPI ; le test n'en connaît aucune ;
   - `mcp-peer-launch-command` cite une « allowlist of remote-safe detail keys » qui appartient aux plugins providers ;
     le mécanisme réel est `IntegrationStatusDisclosure.LOCATION_KEYS` ;
   - libellés de migration au passé dans `adapter-capability-decomposition`, `payload-map-construction`,
     `launch-environment-disclosure`, `public-surface-convergence` et `cli-argument-base`.
7. **Preuves manquantes** (lot 2) : texte du guide du serveur distant (`launch-environment-disclosure`), réponse
   d'historique avec un côté absent sérialisé en `null` (`payload-map-construction`), identité permanente des contrats
   après une extraction (`adapter-capability-decomposition`), scénario de pair à délai maximal joué à l'échelle réelle
   (`mcp-tool-failure-contract`), lien symbolique du lanceur sous Windows (`mcp-peer-launch-command`).
8. **Preuves manquantes** (lot 1) : en-têtes de la réponse `LOCAL_SERVER_BUSY` et du refus de capacité interne ;
   filtre de divulgation sur un corps mal formé ; refus de démarrage du serveur distant entier (prouvé au niveau du
   journal seulement).

## Inventaire des specs principales et plan de lots

Les lots regroupent des capacités qui partagent code, tests et diagrammes ; ils fixent l'ordre de la suite du travail.
Une page durable existe pour les capacités des lots 1 et 2.

### Lot 1 · Frontière HTTP (traité)

| Capacité | Exigences | Scénarios | dont erreur ou limite (titre) | `--strict` | Page durable |
|---|---|---|---|---|---|
| `http-path-decoding` | 3 | 8 | 4 | passe | oui |
| `http-response-headers` | 3 | 6 | 4 | passe | oui |
| `http-failure-disclosure` | 2 | 7 | 2 | passe | oui |
| `remote-access-journal` | 5 | 8 | 3 | passe | oui |

### Lot 2 · Surfaces publiques (traité)

| Capacité | Exigences | Scénarios | dont erreur ou limite (titre) | `--strict` | Page durable |
|---|---|---|---|---|---|
| `public-surface-convergence` | 5 | 18 | 10 | passe | oui |
| `mcp-tool-failure-contract` | 2 | 3 | 1 | passe | oui |
| `cli-argument-base` | 5 | 12 | 2 | passe | oui |
| `adapter-capability-decomposition` | 4 | 12 | 3 | passe | oui |
| `payload-map-construction` | 5 | 9 | 1 | passe | oui |
| `launch-environment-disclosure` | 2 | 8 | 4 | passe | oui |
| `mcp-peer-launch-command` | 3 | 7 | 3 | passe | oui |

### Lot 3 · Persistance SQLite et stores

| Capacité | Exigences | Scénarios | dont erreur ou limite (titre) | `--strict` | Page durable |
|---|---|---|---|---|---|
| `sqlite-connection-policy` | 3 | 6 | 3 | passe | non |
| `sqlite-index-coverage` | 5 | 20 | 9 | passe | non |
| `sqlite-read-shaping` | 3 | 13 | 4 | passe | non |
| `sqlite-schema-history` | 9 | 28 | 13 | passe | non |
| `sqlite-store-packages` | 5 | 11 | 0 | échoue (> 500 car.) | non |
| `store-closed-refusal` | 1 | 3 | 1 | passe | non |
| `released-schema-upgrade` | 6 | 14 | 7 | passe | non |
| `schema-upgrade-guidance` | 5 | 13 | 5 | passe | non |
| `requirement-page-reads` | 7 | 17 | 7 | passe | non |
| `append-only-journal-reads` | 5 | 18 | 4 | passe | non |
| `traceability-link-batch-write` | 2 | 7 | 1 | passe | non |
| `policy-store-refusals` | 3 | 5 | 3 | passe | non |
| `policy-audit-order` | 1 | 3 | 0 | passe | non |

### Lot 4 · Frontières de modules et domaine

| Capacité | Exigences | Scénarios | dont erreur ou limite (titre) | `--strict` | Page durable |
|---|---|---|---|---|---|
| `adapter-internal-boundaries` | 6 | 16 | 0 | passe | non |
| `application-infrastructure-ports` | 4 | 7 | 2 | passe | non |
| `application-package-cycles` | 3 | 7 | 0 | passe | non |
| `application-technology-boundary` | 4 | 8 | 2 | passe | non |
| `module-package-correspondence` | 5 | 10 | 6 | passe | non |
| `provider-sdk-dependency-boundary` | 3 | 5 | 2 | passe | non |
| `reflection-boundary` | 4 | 7 | 3 | passe | non |
| `record-construction` | 3 | 6 | 2 | passe | non |
| `domain-value-decisions` | 2 | 4 | 0 | passe | non |
| `constraint-blocking-decision` | 4 | 8 | 2 | passe | non |
| `operational-runtime-injection` | 3 | 5 | 2 | passe | non |
| `project-ingestion-port` | 4 | 10 | 5 | passe | non |
| `text-digest-and-key-derivation` | 5 | 10 | 0 | passe | non |
| `dead-code-refusal` | 3 | 9 | 6 | passe | non |

### Lot 5 · Gates, preuves et dépendances

| Capacité | Exigences | Scénarios | dont erreur ou limite (titre) | `--strict` | Page durable |
|---|---|---|---|---|---|
| `gate-reachability` | 11 | 37 | 21 | échoue (> 500 car.) | non |
| `gate-verdict-currency` | 6 | 25 | 12 | passe | non |
| `validator-self-test` | 5 | 24 | 19 | passe | non |
| `coverage-attribution-gap` | 7 | 12 | 8 | passe | non |
| `coverage-qualification-evidence` | 4 | 12 | 1 | passe | non |
| `enforcement-mechanism-accounting` | 4 | 9 | 1 | passe | non |
| `architecture-rule-vacuity-guard` | 3 | 8 | 1 | passe | non |
| `refusal-test-evidence` | 3 | 11 | 9 | passe | non |
| `reactor-module-documentation` | 2 | 9 | 5 | passe | non |
| `repository-root-location` | 4 | 8 | 0 | passe | non |
| `repository-credential-hygiene` | 2 | 5 | 4 | passe | non |
| `warning-suppression-justification` | 2 | 3 | 2 | passe | non |
| `dependency-line-currency` | 2 | 5 | 1 | passe | non |
| `dependency-version-management` | 3 | 7 | 2 | passe | non |
| `vulnerability-feed-freshness` | 3 | 12 | 6 | passe | non |

### Lot 6 · Exploitation, providers et coûts

| Capacité | Exigences | Scénarios | dont erreur ou limite (titre) | `--strict` | Page durable |
|---|---|---|---|---|---|
| `operational-log-mode` | 3 | 6 | 5 | passe | non |
| `logs-directory-truth` | 1 | 4 | 2 | passe | non |
| `third-party-log-capture` | 2 | 4 | 1 | passe | non |
| `suppressed-failure-trace` | 3 | 5 | 1 | passe | non |
| `openspec-provider-reading` | 4 | 8 | 1 | passe | non |
| `provider-plugin-pinned-entry` | 5 | 8 | 3 | passe | non |
| `portfolio-provider-identity` | 1 | 3 | 1 | passe | non |
| `change-analysis-traversal-cost` | 3 | 7 | 0 | passe | non |
| `workspace-boundary-scan-cost` | 5 | 15 | 2 | échoue (> 500 car.) | non |
| `change-orchestration-state` | 7 | 14 | 5 | échoue (> 500 car.) | non |
| `release-component-disclosure` | 5 | 18 | 7 | passe | non |

## Changements actifs

| Changement | Schéma | Capacité (opération) | Tâches cochées / total | Traitement dans ce relevé |
|---|---|---|---|---|
| `admit-local-requests-before-they-meet-the-database` | `spec-driven` | `local-admission-control` (ADDED, nouvelle) | 7 / 12 | non relu |
| `bound-every-write-of-the-remote-proxy` | `spec-driven` | `remote-proxy-upstream-bounds` (ADDED, nouvelle) | 11 / 12 | relu : incohérence 2 |
| `declare-a-budget-before-bounding-a-cost` | `spec-driven` | `performance-budget-coverage` (ADDED, nouvelle) | 7 / 14 | non relu |
| `keep-the-scheduler-free-while-sqlite-waits` | `spec-driven` | `scheduler-availability` (ADDED, nouvelle) | 16 / 19 | non relu |
| `project-shared-domain-types-to-one-public-shape` | `spec-driven` | `public-payload-convergence` (ADDED, nouvelle) | 15 / 16 | non relu |
| `read-a-snapshot-content-in-a-fixed-number-of-statements` | `spec-driven` | `snapshot-content-read-cost` (ADDED, nouvelle) | 12 / 13 | non relu |
| `resolve-both-mcp-peers-through-one-settings-resolver` | `spec-driven` | `mcp-peer-settings-resolution` (ADDED, nouvelle) | 18 / 19 | non relu |
| `state-what-the-http-boundary-already-does` | `detailed-uml` | `http-path-decoding`, `remote-access-journal` (ADDED) | 0 / 9 | créé par ce relevé |

Les sept changements en `spec-driven` gardent leur schéma : `openspec/config.yaml` ne s'applique qu'aux changements
créés ensuite, et leurs artefacts sont complets dans ce schéma. Aucune migration n'a été faite ; leurs specs delta
n'ont pas de `## Purpose` qui renvoie à une page durable, ce qui sera à ajouter au moment de leur archivage.

## Reste à faire

- Lot 2 : trancher les divergences du constat 6, puis les porter par un changement OpenSpec (ou par un correctif de
  code, selon la décision).
- Lots 3 à 6 : relecture sémantique, page durable par capacité, diagrammes, traçabilité, changement OpenSpec pour
  chaque comportement vérifié non spécifié.
- Découpage des quatre exigences trop longues : un changement dédié, selon la règle d'OpenSpec (MODIFIED qui garde le
  titre et les scénarios, ADDED pour chaque comportement retiré).
- Décisions du mainteneur : sort des trois capacités archivées sans spec principale et du changement actif qui en
  double une ; langue des specs existantes (traduire changerait les titres, donc les clés de synchronisation) ;
  questions ouvertes de `state-what-the-http-boundary-already-does`.
