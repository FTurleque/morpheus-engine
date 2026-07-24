# MORPHEUS

**MORPHEUS** est un moteur d'intelligence des spécifications et de l'intention (*Specification & Intent Intelligence Engine*).

Sa responsabilité est de construire, maintenir et exposer une compréhension structurée, persistante, versionnée et interrogeable de ce qu'un projet **doit devenir** : exigences, changements, contraintes, scénarios, décisions de conception et tâches associées.

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

- **MORPHEUS** comprend l'intention, les exigences, les changements, les contraintes, les scénarios et les décisions ;
- **MINOS** comprend le code, les symboles, les relations et les impacts de code ;
- **NEXUS** sélectionne, classe et compresse le contexte pertinent ;
- **JARVIS** orchestre les capacités de l'écosystème.

Chaque brique reste autonome.

## Architecture

```text
Sources / workspaces
        ↓
Specification providers
        ↓
Normalisation MORPHEUS
        ↓
NormalizedProjectContent
        ↓
KnowledgeSnapshot / SpecificationVersion
        ↓
Persistence snapshot-scoped
   ┌────────┴────────┐
   ↓                 ↓
 Memory            SQLite
        ↓
Query / Search / Traceability / Quality / Change Analysis
        ↓
        CLI                  ← M9
        ↓
   MCP / API futurs
```

**OpenSpec est le provider de référence initial, pas le domaine de MORPHEUS.**

Un second provider synthétique démontre l'absence de verrouillage du cœur applicatif sur OpenSpec.

## Invariants structurants

MORPHEUS :

- possède son propre modèle de domaine ;
- est local-first et fonctionne sans LLM obligatoire ;
- sépare identité, version, locator et référence externe ;
- utilise UUIDv7 comme format canonique opaque de `DomainIdentity` ;
- namespace les identités externes par provider ;
- distingue CURRENT, PROPOSED et HISTORICAL ;
- publie la connaissance par snapshots cohérents à activation atomique ;
- ne convertit jamais automatiquement un `Scenario` en `AcceptanceCriterion` ;
- conserve les liens non résolus au lieu d'inventer des faits ;
- garde Memory comme backend de référence contractuelle et SQLite comme backend local persistant ;
- ne nécessite pas de graph database au MVP ;
- reste découplé de MINOS, NEXUS et JARVIS ;
- réserve l'analyse du code à MINOS.

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
Distribution M9      : native-first / archive portable autonome
```

## État du projet

```text
C0  Cadrage fonctionnel et architectural       ✅ VALIDÉ
M0  Faisabilité technique                      ✅ VALIDÉ
M1  Discovery / providers / store              ✅ VALIDÉ
M2  Ingestion et modèle normalisé              ✅ VALIDÉ — 94/94
M3  Temporalité / lifecycle / snapshots        ✅ VALIDÉ / INTÉGRÉ — 147/147
M4  Traçabilité typée                          ✅ VALIDÉ / INTÉGRÉ — 189/189
M5  Requêtes et contexte compact               ✅ VALIDÉ / INTÉGRÉ — 227/227
M6  Qualité / couverture / diagnostics         ✅ VALIDÉ / INTÉGRÉ — 261/261
M7  Synchronisation incrémentale / fraîcheur   ✅ VALIDÉ / INTÉGRÉ — 282/282
M8  Analyse des changements                    ✅ VALIDÉ / INTÉGRÉ — 289/289
M9  CLI stabilisée / distribution locale       🚧 IMPLÉMENTÉE, GATES PENDING
```

Dernier gate officiellement validé : **M8**.

```text
ChangeAnalysisContractTest  7/7 PASS
Architecture Tests        146/146 PASS
TOTAL                     289/289 PASS
Failures                    0
Errors                      0
Skipped                     0
BUILD SUCCESS
Finished 2026-07-24T09:44:51+02:00
```

Merge M8 :

```text
6780fb024fe5b8645226f0aacecddb32bcfa7517
```

Références :

- [`docs/ROADMAP.md`](docs/ROADMAP.md)
- [`docs/VALIDATION_M8.md`](docs/VALIDATION_M8.md)
- [`docs/roadmap/M8_INTEGRATION.md`](docs/roadmap/M8_INTEGRATION.md)
- [`docs/roadmap/M9_EXECUTION.md`](docs/roadmap/M9_EXECUTION.md)

## CLI M9

La branche M9 introduit une CLI locale persistante et scriptable.

### Options globales

```text
--json
--data-dir PATH
--config-dir PATH
--db PATH
```

### Commandes

```text
help
version
paths
projects list
projects add --workspace PATH
sync --project ID [--revision REV]
sync-status --project ID
requirements find --project ID [--query TEXT]
changes list --project ID
changes get --project ID --change ID
constraints list --project ID --change ID
decisions list --project ID --change ID
tasks list --project ID --change ID
trace-requirement --project ID --requirement ID
change-context --project ID --change ID
analyze-change --project ID --change ID
quality --project ID
```

Le launcher officiel M9 exécute `sync` comme **FULL_REBUILD conservateur** tant qu'un exécuteur métier incrémental complet n'est pas disponible. Le planificateur incrémental M7 reste intact dans le cœur.

Documentation : [`docs/CLI.md`](docs/CLI.md).

## Quick start M9

Après construction ou extraction d'une distribution :

```text
morpheus projects add --workspace <workspace-openspec>
# récupérer projectId
morpheus sync --project <projectId>
morpheus requirements find --project <projectId> --query session
morpheus changes list --project <projectId>
morpheus quality --project <projectId>
```

Pour les scripts :

```text
morpheus --json projects list
morpheus --json requirements find --project <projectId> --query session
morpheus --json change-context --project <projectId> --change <changeId>
```

## Distribution M9

Artefacts cibles :

```text
JAR autonome : morpheus-cli-<version>-all.jar
Windows      : morpheus-<version>-windows-x64.zip
Linux        : morpheus-<version>-linux-x64.tar.gz
```

Les archives portables sont générées avec `jpackage --type app-image` et embarquent leur runtime Java.

Scripts :

```text
distribution/build-portable.ps1
distribution/build-portable.sh
distribution/build-windows-installer.ps1   # optionnel, WiX requis au build
```

Le répertoire d'installation est séparé des données/config utilisateur afin de permettre upgrade et uninstall sans effacement implicite de SQLite.

Voir [`distribution/README.md`](distribution/README.md) et [`docs/roadmap/DEPLOYMENT.md`](docs/roadmap/DEPLOYMENT.md).

## Validation M9

M9 n'est **pas encore déclaré VALIDÉ**.

Gates requis :

Sous Windows :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

Sous Linux :

```bash
./mvnw clean test
bash distribution/build-portable.sh
```

Les ADR-0059, ADR-0060 et ADR-0061 restent **Proposées** jusqu'aux preuves reproductibles Windows/Linux.

## Roadmap suivante

Après validation et intégration M9 :

```text
M10 -> serveur MCP, stdio natif prioritaire
M11 -> API / headless
M12 -> intégration optionnelle MINOS
M13 -> intégration optionnelle NEXUS
M14 -> orchestration JARVIS
```

La fusion des jalons reste conditionnée par la validation et l'autorisation explicite prévue par la gouvernance du dépôt.