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
SpecificationContentReader
        ↓
ProviderReadResult
        ↓
Normalisation MORPHEUS
        ↓
NormalizedProjectContent
        ↓
KnowledgeSnapshot / versions            ← M3
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

Un second provider synthétique compilé démontre que les mêmes contrats applicatifs peuvent normaliser un autre format sans modifier le domaine.

## Invariants structurants

MORPHEUS :

- possède son propre modèle de domaine ;
- est local-first et fonctionne sans LLM obligatoire ;
- sépare identité, version, locator et référence externe ;
- utilise UUIDv7 comme format canonique opaque de `DomainIdentity` ;
- namespace les identités externes par provider ;
- sélectionne les providers selon leurs capacités effectives ;
- sépare `probe` et lecture réelle ;
- distingue explicitement `READ / ABSENT / UNSUPPORTED / FAILED / PARTIAL` ;
- sépare lecture et écriture ;
- ne convertit jamais automatiquement un `Scenario` en `AcceptanceCriterion` ;
- publiera la connaissance par snapshots cohérents à activation atomique observable ;
- conserve un backend mémoire de référence pour les tests contractuels ;
- utilise SQLite derrière `SpecificationKnowledgeStore` ;
- conserve un modèle conceptuel de graphe sans graph database obligatoire au MVP ;
- reste découplé de MINOS, NEXUS et JARVIS.

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
Distribution         : native-first / container-supported
```

## État du projet

```text
C0 — Cadrage fonctionnel et architectural     ✅ VALIDÉE
M0 — Faisabilité technique                    ✅ VALIDÉE
M1 — Découverte des projets et providers      ✅ VALIDÉE
M2 — Ingestion et modèle normalisé            🚧 7/8 — VALIDATION FINALE
M3 — État temporel / versions / snapshots     ⏳ BLOQUÉ PAR GATE M2-S8
```

### Preuves M2

```text
S1  domaine courant                         48/48 PASS
S2  identité persistante                    58/58 PASS
S3  modèle de changement                    64/64 PASS
S4  requirement deltas                      70/70 PASS
S5  ExternalReference                       76/76 PASS
S6  lecture unifiée / partiel / diagnostics 84/84 PASS
S7  second provider / anti-lock-in           94/94 PASS
S8  validation finale                       gate 94 attendu
```

Le dossier de sortie est préparé dans [`docs/VALIDATION_M2.md`](docs/VALIDATION_M2.md).

## Ce que M2 a stabilisé

```text
ProjectSpecification
Specification
Requirement
RequirementDelta
Scenario
ChangeProposal
Constraint
DesignDecision
ImplementationTask
Evidence
Provenance
ExternalReference
```

Lecture provider :

```text
SpecificationProvider.probe()
        !=
SpecificationContentReader.read()
```

Résultat explicite :

```text
ProviderReadResult
├── NormalizedProjectContent?
├── ReadCategoryReport[]
└── Diagnostic[]
```

Le second provider de vérification :

```text
OpenSpec source ─────┐
                     ├──> mêmes contrats application
Synthetic JSON ──────┘     même domaine MORPHEUS
```

## Frontière M2 → M3

M2 normalise la structure mais ne projette pas encore :

```text
CURRENT / PROPOSED / HISTORICAL
SpecificationVersion complet
KnowledgeSnapshot complet
ChangeLifecycleState complet
application / promotion des deltas
```

ADR-0030 propose que les **premières tables métier complètes** soient créées en M3 en même temps que le membership version/snapshot, plutôt que de figer un schéma provisoire à la fin de M2.

Les éléments déjà persistés restent :

```text
projects
knowledge snapshot metadata
entity identity bindings
migration ledger
```

## Distribution

ADR-0027 fixe la stratégie :

```text
Native-first
Container-supported
```

Trajectoire :

```text
M9  CLI + distribution locale native / portable
M10 MCP natif stdio, conteneur headless si justifié
M11 API + image Docker officielle si justifiée
```

Docker n'est pas requis pour utiliser le CLI local.

Voir [`docs/roadmap/DEPLOYMENT.md`](docs/roadmap/DEPLOYMENT.md).

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
- [`docs/VALIDATION_M1.md`](docs/VALIDATION_M1.md) — sortie M1 ;
- [`docs/VALIDATION_M2.md`](docs/VALIDATION_M2.md) — dossier de sortie M2, actuellement candidat ;
- [`docs/roadmap/M2_EXECUTION.md`](docs/roadmap/M2_EXECUTION.md) — tableau opérationnel M2 ;
- [`experiments/m0/results/README.md`](experiments/m0/results/README.md) — synthèse des preuves M0.

### Architecture et domaine

- [`docs/ECOSYSTEME.md`](docs/ECOSYSTEME.md) — frontières MORPHEUS / MINOS / NEXUS / JARVIS ;
- [`docs/architecture/overview.md`](docs/architecture/overview.md) — architecture ;
- [`docs/domain/MODEL.md`](docs/domain/MODEL.md) — modèle de domaine cible ;
- [`docs/domain/CHANGE_LIFECYCLE.md`](docs/domain/CHANGE_LIFECYCLE.md) — machine d'état des changements ;
- [`docs/USE_CASES.md`](docs/USE_CASES.md) — cas d'usage ;
- [`docs/MVP.md`](docs/MVP.md) — MVP ;
- [`docs/PLAN.md`](docs/PLAN.md) — plan ;
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — roadmap stratégique ;
- [`docs/roadmap/DEPLOYMENT.md`](docs/roadmap/DEPLOYMENT.md) — packaging et déploiement.

### Contrats

- [`docs/contracts/SPECIFICATION_PROVIDER.md`](docs/contracts/SPECIFICATION_PROVIDER.md) — provider et capabilities ;
- [`docs/contracts/SPECIFICATION_KNOWLEDGE_STORE.md`](docs/contracts/SPECIFICATION_KNOWLEDGE_STORE.md) — store de connaissance.

### Recherche et décisions

- [`docs/research/openspec-provider-study.md`](docs/research/openspec-provider-study.md) — OpenSpec ;
- [`docs/research/M0_EXPERIMENT_MATRIX.md`](docs/research/M0_EXPERIMENT_MATRIX.md) — matrice M0 ;
- [`docs/research/domain-identity-format.md`](docs/research/domain-identity-format.md) — UUIDv7 ;
- [`docs/research/production-stack-evaluation.md`](docs/research/production-stack-evaluation.md) — fondation technique ;
- [`docs/adr/`](docs/adr/) — registre des décisions architecturales.
