# Guide utilisateur MORPHEUS

MORPHEUS est un **Specification & Intent Intelligence Engine** local-first. Il transforme une ou plusieurs sources de spécification en un modèle normalisé, versionné, composable et interrogeable, puis expose ce modèle par CLI, MCP STDIO et API HTTP locale. Depuis M26, un mode serveur d’équipe **remote explicitement opt-in** peut exposer l’API via HTTPS avec authentification et RBAC, sans changer le fonctionnement local par défaut.

Baseline documentée : **M26 validé et intégré**, qualification Windows + Linux/WSL sur MORPHEUS `1.0.0`.

```text
M26 qualified     bf481b24054c4577144b4cb2ede2bdbc4d9974a2
M26 PR head       36378842e3ef41e379ade17f869b0939d052bbbc
M26 merge         49016a18c844a78ec864235c544d82d487da7c8a
Tests             579 PASS Windows + Linux
Architecture      234 PASS Windows + Linux
```

## 1. À quoi sert MORPHEUS ?

MORPHEUS permet notamment de savoir :

- quelles exigences sont actuellement publiées ;
- quels changements restent proposés ;
- quelles contraintes, décisions, critères d’acceptation et tâches sont liés ;
- d’où vient une information et à quoi elle est reliée ;
- quels providers ont contribué à une vue et selon quelle priorité ;
- quels conflits de composition existent entre sources ;
- quels plugins provider externes sont découvrables et compatibles ;
- quels projets appartiennent à un portfolio sans confondre identité et localisation ;
- quelles références relient des entités de projets différents ;
- comment interroger un projet ou portfolio avec un DSL provider-neutral ;
- comment sauvegarder une requête sous forme de saved view versionnée ;
- comment exporter un résultat en JSON canonique, CSV ou Markdown ;
- comment appliquer des Policy Packs versionnés et expliquer leurs décisions ;
- comment évaluer une gouvernance en dry-run sans mutation ;
- si une transition lifecycle est autorisée compte tenu des faits disponibles ;
- comment exposer MORPHEUS à une équipe via un serveur HTTPS optionnel ;
- quelles références MINOS ou quel contexte NEXUS sont disponibles en complément.

MORPHEUS ne remplace ni Git, ni un tracker, ni MINOS, ni NEXUS, ni JARVIS.

```text
MORPHEUS = specification facts + intent + lifecycle rules
           + controlled state invariants
           + provider composition facts
           + portfolio specification facts
           + provider-neutral query/view/reporting contracts
           + provider-neutral governance policy contracts
           + optional remote/team access boundary
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

## 2. Surfaces

| Surface | Usage principal | Transport | Écriture contrôlée |
|---|---|---|---|
| CLI | humain, scripts, administration locale | processus local | oui, explicite |
| MCP | IDE, agents, orchestrateurs | STDIO / JSON-RPC | oui selon tools/capabilities |
| API locale | intégration locale | HTTP `/api/v1`, loopback | oui selon routes |
| API remote M26 | équipe / clients réseau | HTTPS `/api/v1` | RBAC READ/WRITE/ADMIN |

Les surfaces utilisent les mêmes services applicatifs ; elles ne réimplémentent pas les règles métier.

## 3. Projet, snapshot, providers et portfolio

Un `ProjectSpecificationId` est l’identité métier stable d’un projet MORPHEUS. Il n’est pas dérivé du workspace, du repository ou d’un identifiant provider.

```text
PortfolioId
  |
  +-- ProjectSpecificationId A
  +-- ProjectSpecificationId B
  +-- ProjectSpecificationId C
  |
  +-- cross-project references
  +-- project-scoped freshness
```

Un projet temporairement absent peut être marqué `MISSING` sans perdre son identité ni ses références historiques.

Guide : [Portfolios multi-projets](PORTFOLIOS.md).

## 4. Query DSL, Saved Views & Reporting

```text
DSL != SQL passthrough
saved view != materialized truth
export != mutation
```

Exemple :

```bash
morpheus query execute \
  --project <projectId> \
  --entity requirement \
  --filter 'title contains "session"' \
  --sort title:asc \
  --limit 50
```

Saved view :

```bash
morpheus views create \
  --name "Current requirements" \
  --project <projectId> \
  --entity requirement \
  --filter 'status eq CURRENT'
```

Export :

```bash
morpheus export view --id <savedViewId> --format csv
```

Guide : [Query DSL, Saved Views & Reporting](QUERY_VIEWS_REPORTING.md).

## 5. Policy Packs / Governance

M25 ajoute des Policy Packs provider-neutral, versionnés et auditables.

```text
constraint text != executable policy
severity != blocking policy
policy recommendation != applied mutation
policy version != mutable latest
policy override != provenance erasure
dry-run != mutation
```

Guide : [Policy Packs](POLICY_PACKS.md).

## 6. Team / Remote Server Mode

Le mode **LOCAL** reste la baseline produit : loopback uniquement, sans authentification imposée.

Le mode **REMOTE** est opt-in et exige :

```text
HTTPS
PKCS12
TLSv1.3 / TLSv1.2
Bearer authentication
READ / WRITE / ADMIN
```

Les tokens sont générés avec 256 bits d’entropie et seul `sha256(token)` est persisté.

Surfaces serveur :

```text
GET  /api/v1/server/status   READ
POST /api/v1/server/backups  ADMIN
GET  /api/v1/metrics         ADMIN
```

Maintenance locale :

```text
server identity create
server backup create
server backup verify
server restore --confirm
```

Le restore est **offline uniquement** et le provisioning d’identité n’est pas exposé en HTTP/MCP.

La concurrence remote est bornée ; une saturation applicative retourne HTTP `429`.

Guide complet : [Team / Remote Server](TEAM_REMOTE_SERVER.md).

## 7. Providers et plugins externes

```text
discover != activate
probe != read
metadata != trust
classloader isolation != security sandbox
```

Voir [Plugins provider](PROVIDER_PLUGINS.md).

## 8. Temporalité et lifecycle

```text
CURRENT     état publié de référence
PROPOSED    intention non encore publiée
HISTORICAL  état publié antérieur
```

`SpecificationVersion != KnowledgeSnapshot`.

Une évaluation lifecycle peut être `ALLOWED`, `BLOCKED`, `UNKNOWN` ou `REQUIRES_INPUT`.

**Évaluer une transition ne l’applique jamais.**

## 9. Parcours recommandé

1. installer ou extraire MORPHEUS ;
2. `projects add` ;
3. `sync` ;
4. vérifier `sync-status` ;
5. composer les providers si nécessaire ;
6. examiner les conflits ;
7. interroger requirements, changes, traçabilité et qualité ;
8. créer un portfolio si plusieurs projets doivent être corrélés ;
9. utiliser Query DSL / saved views / exports ;
10. activer des Policy Packs si une gouvernance explicite est nécessaire ;
11. utiliser HTTP/MCP si un outil consomme MORPHEUS ;
12. n’activer `api --remote` que pour un usage équipe explicite avec TLS/auth configurés ;
13. appliquer un lifecycle uniquement via l’opération write explicite et ses garde-fous.

Voir [Démarrage rapide](QUICKSTART.md).

## 10. Commandes principales

| Besoin | Commande |
|---|---|
| enregistrer un workspace | `projects add` |
| lister les projets | `projects list` |
| reconstruire/publier | `sync` |
| contrôler la fraîcheur | `sync-status` |
| composer les providers | `composition sync` |
| voir les conflits provider | `composition conflicts` |
| découvrir des plugins | `provider-plugins discover` |
| créer un portfolio | `portfolio create` |
| vue portfolio | `portfolio overview` |
| traversal inter-projets | `portfolio traverse` |
| requête générique | `query execute` |
| saved views | `views create/get/versions/update/archive/execute` |
| exporter | `export query/view` |
| policy packs | `policy pack ...` |
| dry-run policy | `policy dry-run` |
| créer une identité remote | `server identity create` |
| backup | `server backup create/verify` |
| restore offline | `server restore --confirm` |
| appliquer transition | `lifecycle apply` |

Référence détaillée : [CLI](CLI.md).

## 11. Garanties structurantes

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
UNKNOWN != FAILED
UNKNOWN != BLOCKED
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
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
non-loopback bind requires remote mode
remote mode requires TLS + authentication
authentication != authorization
READ != WRITE != ADMIN
token plaintext != persisted credential
backup != live restore
restore != implicit migration
server state != provider source of truth
```

MORPHEUS préfère `UNAVAILABLE`/`UNKNOWN` à un fait inventé.

## 12. Stockage local

SQLite est le store persistant par défaut.

```text
--data-dir PATH       MORPHEUS_DATA_DIR
--config-dir PATH     MORPHEUS_CONFIG_DIR
--db PATH             MORPHEUS_DB
```

M23 ajoute V013 portfolio, M24 V014 saved views et M25 V015 policy packs. M26 n’ajoute pas de V016 : la configuration remote reste distincte de la vérité métier.

## 13. Intégrations optionnelles

| Intégration | Apport | Si absente |
|---|---|---|
| MINOS | résolution vers le code | seule la résolution code est indisponible |
| NEXUS | contexte technique sous budget | seul le contexte augmenté est indisponible |
| JARVIS | séquencement/orchestration | MORPHEUS reste autonome |

## 14. Baseline M26 intégrée

```text
code M26 qualifié     bf481b24054c4577144b4cb2ede2bdbc4d9974a2
PR head docs-only     36378842e3ef41e379ade17f869b0939d052bbbc
merge M26             49016a18c844a78ec864235c544d82d487da7c8a
version               1.0.0
tests                 579 PASS Windows + Linux
architecture          234 PASS Windows + Linux
TLS/auth/RBAC         PASS
bounded concurrency   PASS / HTTP 429
secret disclosure     NONE
backup/restore        PASS
SQLite                V015
packaging Win/Linux   PASS
SBOM/provenance       PASS Windows + Linux
executable delta      NONE Windows + Linux
```

## 15. Documentation associée

- [Démarrage rapide](QUICKSTART.md)
- [Référence CLI](CLI.md)
- [Portfolios multi-projets](PORTFOLIOS.md)
- [Query DSL, Saved Views & Reporting](QUERY_VIEWS_REPORTING.md)
- [Policy Packs](POLICY_PACKS.md)
- [Team / Remote Server](TEAM_REMOTE_SERVER.md)
- [Plugins provider](PROVIDER_PLUGINS.md)
- [Intégrations optionnelles](INTEGRATIONS.md)
- [Architecture développeur](../developer/ARCHITECTURE.md)
- [Remote Server Platform — développeur](../developer/REMOTE_SERVER_PLATFORM.md)
- [API HTTP](../developer/API.md)
- [Serveur MCP](../developer/MCP.md)
- [Validation M26](../validation/VALIDATION_M26.md)
- [Portail de documentation](../README.md)
