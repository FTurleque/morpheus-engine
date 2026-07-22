# MORPHEUS

**MORPHEUS** est un moteur d'intelligence des spécifications et de l'intention (*Specification & Intent Intelligence Engine*).

Sa responsabilité est de construire, maintenir et exposer une compréhension structurée, persistante, versionnée et interrogeable de ce qu'un projet **doit devenir** : exigences, changements, contraintes, scénarios, décisions de conception, critères d'acceptation et tâches associées.

MORPHEUS ne remplace ni le code, ni les outils de gestion de projet, ni les agents IA. Il fournit une couche de connaissance dédiée à l'intention et aux spécifications.

## Question fondamentale

MORPHEUS répond principalement à la question :

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

Les responsabilités sont séparées :

- **MORPHEUS** comprend l'intention, les exigences, les changements, les contraintes et les critères d'acceptation ;
- **MINOS** comprend le code, les symboles, les relations, les dépendances et les impacts ;
- **NEXUS** sélectionne et classe le contexte pertinent pour une tâche donnée ;
- **JARVIS** orchestre les différentes capacités de l'écosystème ;
- **Alfred** et **Brainiac** représentent des agents ou profils spécialisés pouvant consommer ces capacités.

Chaque brique doit rester autonome.

## Architecture validée

```text
Sources de spécifications
        │
        ▼
SpecificationProvider Registry
        │
   ┌────┼──────────────┐
   ▼    ▼              ▼
OpenSpec Markdown     Futur
        │
        ▼
Ingestion / Normalisation MORPHEUS
        │
        ▼
Domaine MORPHEUS
        │
        ├── Specification
        ├── Requirement
        ├── Scenario
        ├── ChangeProposal
        ├── Constraint
        ├── DesignDecision
        ├── AcceptanceCriterion
        ├── ImplementationTask
        ├── Evidence / Provenance
        └── TraceabilityLink
        │
        ▼
KnowledgeSnapshot
        │
        ▼
SpecificationKnowledgeStore
    ┌───┴────┐
    ▼        ▼
  Memory   SQLite
        │
        ▼
Query / Search / Traceability / Context
        │
   ┌────┼─────┐
   ▼    ▼     ▼
  CLI  MCP   API
```

**OpenSpec est le premier provider de référence, pas le domaine de MORPHEUS.**

## Invariants validés

MORPHEUS :

- possède son propre modèle de domaine ;
- est local-first et fonctionne sans LLM obligatoire ;
- distingue `CURRENT`, `PROPOSED` et `HISTORICAL` ;
- sépare identité, version, locator et identifiant externe ;
- utilise UUIDv7 comme format canonique opaque de `DomainIdentity` ;
- traite la traçabilité comme un concept de premier ordre ;
- sélectionne les providers selon leurs capacités effectives ;
- sépare lecture et écriture ;
- publie la connaissance par snapshots cohérents à activation atomique observable ;
- conserve un backend mémoire de référence pour les tests contractuels ;
- utilise SQLite comme backend persistant initial derrière `SpecificationKnowledgeStore` ;
- conserve un modèle conceptuel de graphe sans graph database obligatoire au MVP ;
- expose des références cross-engine sans dépendance directe à MINOS, NEXUS ou JARVIS ;
- fournit des vues compactes sans absorber le ranking global de NEXUS.

## Fondation technique retenue

À la sortie de M0 :

```text
Language             : Java
Compatibility        : Java 21 source / bytecode
Compiler JDK         : Java 21+ avec --release 21
Build                : Maven 3.9.16 + Maven Wrapper
Persistent store     : SQLite derrière SpecificationKnowledgeStore
Memory store         : référence des tests contractuels
DomainIdentity       : UUIDv7
Graph DB             : aucune au MVP
Server framework     : aucun dans la fondation
DI framework         : aucun obligatoire
LLM                  : aucun obligatoire
```

La baseline Java 21 est volontairement alignée avec l'écosystème existant. Un JDK plus récent peut compiler MORPHEUS tant que `--release 21` est respecté.

## Phase actuelle

Les phases :

```text
C0 — Cadrage fonctionnel et architectural     ✅ VALIDÉE
M0 — Faisabilité technique                    ✅ VALIDÉE
M1 — Découverte des projets et providers      🚧 EN COURS
```

Le projet entre maintenant en :

> **M1 — Découverte des projets et providers**

M1 commence par un bootstrap technique obligatoire avant toute fonctionnalité significative : Maven Wrapper, Java release 21, build local reproductible, tests d'architecture, portage des invariants M0 critiques et premier schéma SQLite versionné.

La règle de travail devient :

> **Transformer les preuves M0 en code de production, sans affaiblir les frontières validées.**

## Vérification du build

Le gate obligatoire du dépôt est local et reproductible via le Maven Wrapper.

Sous Windows :

```text
.\mvnw.cmd clean test
```

Sous Linux/macOS :

```text
./mvnw clean test
```

Une PR ne doit pas être considérée comme prête si ce build échoue sur l'environnement de développement concerné.

GitHub Actions ou une autre CI pourront être ajoutés ultérieurement lorsqu'un besoin réel de validation distante, multi-OS, publication ou automatisation de release apparaîtra. **La CI n'est pas une dépendance fonctionnelle ni un gate obligatoire de MORPHEUS.**

## Documents de référence

### Sources de vérité et validations

- [`docs/CAHIER_DES_CHARGES.md`](docs/CAHIER_DES_CHARGES.md) — source de vérité fonctionnelle et architecturale ;
- [`docs/VALIDATION_C0.md`](docs/VALIDATION_C0.md) — sortie C0 ;
- [`docs/VALIDATION_M0.md`](docs/VALIDATION_M0.md) — sortie M0 et fondation M1 ;
- [`experiments/m0/results/README.md`](experiments/m0/results/README.md) — synthèse des preuves M0.

### Architecture et domaine

- [`docs/ECOSYSTEME.md`](docs/ECOSYSTEME.md) — frontières MORPHEUS / MINOS / NEXUS / JARVIS ;
- [`docs/architecture/overview.md`](docs/architecture/overview.md) — architecture ;
- [`docs/domain/MODEL.md`](docs/domain/MODEL.md) — modèle de domaine ;
- [`docs/domain/CHANGE_LIFECYCLE.md`](docs/domain/CHANGE_LIFECYCLE.md) — machine d'état des changements ;
- [`docs/USE_CASES.md`](docs/USE_CASES.md) — cas d'usage ;
- [`docs/MVP.md`](docs/MVP.md) — MVP ;
- [`docs/PLAN.md`](docs/PLAN.md) — plan ;
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — roadmap.

### Contrats

- [`docs/contracts/SPECIFICATION_PROVIDER.md`](docs/contracts/SPECIFICATION_PROVIDER.md) — provider et capabilities ;
- [`docs/contracts/SPECIFICATION_KNOWLEDGE_STORE.md`](docs/contracts/SPECIFICATION_KNOWLEDGE_STORE.md) — store de connaissance.

### Recherche

- [`docs/research/openspec-provider-study.md`](docs/research/openspec-provider-study.md) — OpenSpec ;
- [`docs/research/M0_EXPERIMENT_MATRIX.md`](docs/research/M0_EXPERIMENT_MATRIX.md) — matrice M0 ;
- [`docs/research/domain-identity-format.md`](docs/research/domain-identity-format.md) — UUIDv7 ;
- [`docs/research/production-stack-evaluation.md`](docs/research/production-stack-evaluation.md) — choix de fondation.

### Décisions

- [`docs/adr/`](docs/adr/) — registre ADR et statuts de sortie M0.

## Gates immédiats de M1

Avant toute fonctionnalité M1 significative :

1. Maven Wrapper 3.9.16 ;
2. `maven.compiler.release=21` ;
3. `.\mvnw.cmd clean test` validé sous Windows ;
4. test d'architecture interdisant `domain -> adapters` ;
5. portage en JUnit des invariants M0 critiques ;
6. store mémoire de référence ;
7. schéma SQLite initial versionné et migrable ;
8. validation du driver SQLite sous Windows ;
9. conservation des fixtures M0 comme corpus de non-régression.

Une CI distante reste optionnelle et pourra être ajoutée plus tard selon les besoins du projet.
