# MORPHEUS

**MORPHEUS** est un moteur d'intelligence des spécifications et de l'intention (*Specification & Intent Intelligence Engine*).

Sa responsabilité est de construire, maintenir et exposer une compréhension structurée, persistante, versionnée et interrogeable de ce qu'un projet **doit devenir** : exigences, changements, contraintes, scénarios, décisions de conception, critères d'acceptation et tâches associées.

MORPHEUS ne remplace ni le code, ni les outils de gestion de projet, ni les agents IA. Il fournit une couche de connaissance dédiée à l'intention et aux spécifications.

## Question fondamentale

> **Qu'est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l'intention ?**

## Position dans l'écosystème

```text
                           JARVIS
                        Orchestration
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
          ▼                  ▼                  ▼
      MORPHEUS              MINOS              NEXUS
  Spec & Intent             Code              Context
   Intelligence          Intelligence        Intelligence
          │                  │                  │
          └──────────────────┼──────────────────┘
                             ▼
                     ALFRED / BRAINIAC
                       Agents / profils IA
```

Responsabilités :

- **MORPHEUS** comprend l'intention, les exigences, les changements, les contraintes et les critères d'acceptation ;
- **MINOS** comprend le code, les symboles, les relations, les dépendances et les impacts ;
- **NEXUS** sélectionne et classe le contexte pertinent ;
- **JARVIS** orchestre les capacités de l'écosystème ;
- **Alfred** et **Brainiac** représentent des agents ou profils spécialisés.

Chaque brique reste autonome.

## Architecture

```text
Sources / workspaces
        ↓
Source discovery
        ↓
SpecificationProviderRegistry
        ↓
Providers
        ↓
ProviderSnapshot / ingestion
        ↓
Normalisation MORPHEUS
        ↓
Domaine MORPHEUS
        ↓
KnowledgeSnapshot
        ↓
SpecificationKnowledgeStore
   ┌───────┴───────┐
   ↓               ↓
 Memory          SQLite
        ↓
Query / Search / Traceability / Context
        ↓
CLI / MCP / API
```

**OpenSpec est le premier provider de référence, pas le domaine de MORPHEUS.**

## Invariants structurants

MORPHEUS :

- possède son propre modèle de domaine ;
- est local-first et fonctionne sans LLM obligatoire ;
- sépare identité, version, locator et identifiant externe ;
- utilise UUIDv7 comme format canonique opaque de `DomainIdentity` ;
- sélectionne les providers selon leurs capacités effectives ;
- sépare lecture et écriture ;
- publie la connaissance par snapshots cohérents à activation atomique observable ;
- conserve un backend mémoire de référence pour les tests contractuels ;
- utilise SQLite derrière `SpecificationKnowledgeStore` ;
- conserve un modèle conceptuel de graphe sans graph database obligatoire au MVP ;
- reste découplé de MINOS, NEXUS et JARVIS.

Les concepts `CURRENT / PROPOSED / HISTORICAL`, la traçabilité complète et les références cross-engine restent gouvernés par leurs jalons respectifs de la roadmap.

## Fondation technique

```text
Language             : Java
Compatibility        : Java 21 source / bytecode
Compiler JDK         : Java 21+ avec --release 21
Build                : Maven 3.9.16 + Maven Wrapper
Persistent store     : SQLite JDBC 3.53.1.0
Memory store         : référence des tests contractuels
DomainIdentity       : UUIDv7
Graph DB             : aucune au MVP
Server framework     : aucun dans la fondation
DI framework         : aucun obligatoire
LLM                  : aucun obligatoire
Remote CI            : optionnelle, non gate
```

## État du projet

```text
C0 — Cadrage fonctionnel et architectural     ✅ VALIDÉE
M0 — Faisabilité technique                    ✅ VALIDÉE
M1 — Découverte des projets et providers      ✅ VALIDÉE
M2 — Ingestion et modèle normalisé            🚧 AUTORISÉE / À DÉMARRER
```

M1 a validé :

```text
workspace discovery
        ↓
provider registry
        ↓
capability negotiation
        ↓
OpenSpec spec-driven probe
        ↓
LocalProjectRegistry
        ↓
SpecificationKnowledgeStore
        ↓
Memory / SQLite migrations V1 + V2
```

Gate final M1 :

```text
42/42 tests PASS
Failures: 0
Errors: 0
BUILD SUCCESS
```

Le projet entre maintenant en :

> **M2 — Ingestion et modèle normalisé**

Objectif M2 : transformer une source supportée en concepts MORPHEUS indépendants du provider.

Contrainte principale :

> **aucun type OpenSpec ne traverse le domaine MORPHEUS ni ses services publics.**

## Vérification du build

Le gate obligatoire du dépôt reste le Maven Wrapper.

Sous Windows :

```text
.\mvnw.cmd clean test
```

Sous Linux/macOS :

```text
./mvnw clean test
```

Une CI distante pourra être ajoutée lorsqu'un besoin réel de validation distante, multi-OS, publication ou release automation apparaîtra.

## Documents de référence

### Sources de vérité et validations

- [`docs/CAHIER_DES_CHARGES.md`](docs/CAHIER_DES_CHARGES.md) — source de vérité fonctionnelle et architecturale ;
- [`docs/VALIDATION_C0.md`](docs/VALIDATION_C0.md) — sortie C0 ;
- [`docs/VALIDATION_M0.md`](docs/VALIDATION_M0.md) — sortie M0 ;
- [`docs/VALIDATION_M1.md`](docs/VALIDATION_M1.md) — sortie M1 et autorisation M2 ;
- [`experiments/m0/results/README.md`](experiments/m0/results/README.md) — synthèse des preuves M0.

### Architecture et domaine

- [`docs/ECOSYSTEME.md`](docs/ECOSYSTEME.md) — frontières MORPHEUS / MINOS / NEXUS / JARVIS ;
- [`docs/architecture/overview.md`](docs/architecture/overview.md) — architecture ;
- [`docs/domain/MODEL.md`](docs/domain/MODEL.md) — modèle de domaine cible ;
- [`docs/domain/CHANGE_LIFECYCLE.md`](docs/domain/CHANGE_LIFECYCLE.md) — machine d'état des changements ;
- [`docs/USE_CASES.md`](docs/USE_CASES.md) — cas d'usage ;
- [`docs/MVP.md`](docs/MVP.md) — MVP ;
- [`docs/PLAN.md`](docs/PLAN.md) — plan ;
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — roadmap.

### Contrats

- [`docs/contracts/SPECIFICATION_PROVIDER.md`](docs/contracts/SPECIFICATION_PROVIDER.md) — provider et capabilities ;
- [`docs/contracts/SPECIFICATION_KNOWLEDGE_STORE.md`](docs/contracts/SPECIFICATION_KNOWLEDGE_STORE.md) — store de connaissance.

### Recherche et décisions

- [`docs/research/openspec-provider-study.md`](docs/research/openspec-provider-study.md) — OpenSpec ;
- [`docs/research/M0_EXPERIMENT_MATRIX.md`](docs/research/M0_EXPERIMENT_MATRIX.md) — matrice M0 ;
- [`docs/research/domain-identity-format.md`](docs/research/domain-identity-format.md) — UUIDv7 ;
- [`docs/research/production-stack-evaluation.md`](docs/research/production-stack-evaluation.md) — fondation technique ;
- [`docs/adr/`](docs/adr/) — registre des décisions architecturales.
