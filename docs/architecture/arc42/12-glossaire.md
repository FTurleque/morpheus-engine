# §12 — Glossaire

> **Sources** : `docs/domain/MODEL.md`, `docs/adr/README.md` (invariants),
> `docs/developer/ARCHITECTURE.md`, packages `com.morpheus.domain`.

---

## 12.1 Termes métier

| Terme | Définition | Ambiguïté à éviter |
|-------|-----------|-------------------|
| **Spécification** | Représentation normalisée de l'intention d'un projet, issue d'un ou plusieurs providers | Ne pas confondre avec « fichier source » — la spécification est le résultat de la normalisation, pas la source |
| **Workspace** | Répertoire racine d'un projet, résolu par le `WorkspaceRootResolver` | Ne pas confondre avec « projet » au sens MORPHEUS — un workspace peut contenir plusieurs projets |
| **Project** | Unité de gestion dans MORPHEUS, identifiée par un `rootScheme`+`rootValue` | ≠ workspace ; le projet est l'entité MORPHEUS, pas le répertoire |
| **Requirement** | Exigence versionnée, normalisée depuis le provider | Toujours versionnée — une `Requirement` sans version est un état transitoire invalide |
| **Change** | Modification proposée, avec métadonnées normalisées et lifecycle propre | ≠ commit Git — un changement peut correspondre à plusieurs commits ou aucun |
| **Portfolio** | Agrégat de plusieurs projets permettant une vue cross-projet | ≠ collection — un portfolio porte une sémantique de gestion (fraîcheur, traversée, références) |
| **Policy Pack** | Ensemble de règles de gouvernance activables sur un ou plusieurs projets | ≠ contrainte de domaine — une policy pack est une règle métier externe au domaine |
| **Saved View** | Query DSL sauvegardée et versionnée | ≠ matérialisation — une saved view est une query, pas un snapshot résultat |

---

## 12.2 Termes techniques

| Terme | Définition | Ambiguïté à éviter |
|-------|-----------|-------------------|
| **DomainIdentity** | UUIDv7 opaque identifiant une entité MORPHEUS de manière stable et immuable | ≠ version (`EntityVersionId`), ≠ locator (`SourceLocator`), ≠ référence externe (`ExternalReference`) |
| **KnowledgeSnapshot** | Vue versionnée et immuable de l'état d'un projet à un instant t | ≠ `SpecificationVersion` — un snapshot est une activation atomique ; une version est un numéro incrémental de requirement |
| **SpecificationKnowledgeStore** | Port d'application définissant le contrat de persistance — abstraction derrière laquelle se trouve SQLite ou Memory | ≠ la base de données elle-même |
| **SourceLocator** | Référence à l'emplacement physique d'un artefact source (chemin, URL) | ≠ `DomainIdentity` — le locator peut changer si le fichier est déplacé ; l'identité reste stable |
| **TraceabilityLink** | Lien typé (taxonomie contrôlée) entre deux entités MORPHEUS | ≠ lien de dépendance code — un lien de traçabilité est une relation sémantique déclarée |
| **ExternalReference** | Référence à un artefact externe à MORPHEUS (ticket, PR, URL) — peut être non résolue (`UNRESOLVED`) | ≠ `SourceLocator` — une référence externe pointe vers un système tiers, pas vers un fichier source |
| **Provider** | Adaptateur de lecture qui ingère des sources hétérogènes et produit un contenu normalisé | ≠ service externe — un provider est une implémentation locale du contrat de lecture |
| **MCP** | Model Context Protocol — protocole JSON-RPC sur STDIO permettant aux clients IA d'interroger MORPHEUS | Acronyme : Model Context Protocol (non MCP = Multi-Cloud Platform dans ce contexte) |
| **STDIO** | Standard Input/Output — transport utilisé par le serveur MCP de MORPHEUS | Dans ce contexte : mode de communication inter-processus (stdin/stdout), pas « I/O standard » générique |
| **Gate de validation** | Script (`validate-mN.sh` / `.ps1`) définissant le critère de réussite d'un milestone | ≠ test unitaire — un gate est un ensemble de tests et assertions couvrant un milestone complet |
| **Surface** | Point d'accès public au système : CLI, MCP STDIO, HTTP API | Utilisé dans le sens de « surface publique » (API surface) |
| **Surface parity** | Garantie que CLI, MCP et HTTP exposent les mêmes capacités | Vérifiable via `contracts/public-surfaces.tsv` |
| **WAL** | Write-Ahead Logging — mode SQLite garantissant la cohérence en cas de crash | Terme SQLite standard ; ici utilisé pour la résilience du store |

---

## 12.3 Acronymes

| Acronyme | Développement | Contexte |
|----------|--------------|---------|
| **ADR** | Architecture Decision Record | Décision architecturale documentée |
| **arc42** | Architecture Communication Framework 42 | Cadre documentaire utilisé pour cette documentation |
| **C4** | Context, Container, Component, Code | Modèle de diagrammes d'architecture |
| **CLI** | Command-Line Interface | Surface d'accès en ligne de commande |
| **MCP** | Model Context Protocol | Protocole d'intégration IA |
| **SBOM** | Software Bill of Materials | Inventaire des dépendances (format CycloneDX 1.6) |
| **SDK** | Software Development Kit | Ici : kit pour développer des providers externes |
| **RBAC** | Role-Based Access Control | Contrôle d'accès par rôle (mode remote) |
| **TLS** | Transport Layer Security | Protocole de chiffrement (mode remote HTTPS) |
| **WAL** | Write-Ahead Logging | Mode de journalisation SQLite |
| **UUIDv7** | Universally Unique Identifier version 7 | Identifiant temporellement ordonné (MS timestamp + aléatoire) |
| **JVM** | Java Virtual Machine | Machine virtuelle Java (embarquée via jpackage) |
| **LLM** | Large Language Model | Modèle de langage de grande taille — volontairement absent du cœur MORPHEUS |

---

## 12.4 Systèmes de l'écosystème MORPHEUS

| Nom | Rôle | Relation avec MORPHEUS |
|-----|------|----------------------|
| **MINOS ENGINE** | Code intelligence — analyse statique, symboles, dépendances | Système externe optionnel ; appelé via MCP STDIO par `morpheus-integration-minos` |
| **NEXUS ENGINE** | Context selection et compression pour agents IA | Système externe optionnel ; appelé via MCP STDIO par `morpheus-integration-nexus` |
| **JARVIS** | Agent orchestrateur / sequencer IA | Consomme l'API HTTP MORPHEUS en read-only ; jamais intégré dans le processus MORPHEUS |
| **ALFRED / BRAINIAC** | Agents IA de l'écosystème | Référencés dans `docs/ECOSYSTEME.md` ; hors périmètre MORPHEUS ENGINE |
