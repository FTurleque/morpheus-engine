# §6 — Vue d'exécution (scénarios runtime)

> **Sources** : `docs/developer/ARCHITECTURE.md`, `docs/developer/MCP.md`,
> `docs/developer/API.md`, `docs/openapi/morpheus-v1.yaml`,
> `morpheus-application/`, `morpheus-store-sqlite/`.
>
> Les noms sont strictement cohérents avec la §5.

---

## 6.1 Scénario nominal — Synchronisation d'un projet

**Description** : un développeur demande la synchronisation d'un projet via
la CLI. MORPHEUS lit le workspace, construit un snapshot, le valide et l'active.

```mermaid
sequenceDiagram
  autonumber
  actor Développeur as Développeur «Person»
  participant CLI as CLI Runtime<br/>«adapter»
  participant AppIngestion as Ingestion & Sync<br/>«Component»
  participant Provider as Provider (Markdown/OpenSpec)<br/>«adapter»
  participant Workspace as Workspace Projet<br/>«Software System»
  participant AppSnapshot as Temporal & Snapshot<br/>«Component»
  participant StoreDB as SQLite Store<br/>«database»

  Développeur->>CLI: morpheus sync --project ./mon-projet
  CLI->>AppIngestion: syncProject(projectRoot)
  AppIngestion->>StoreDB: getOrCreateProject(projectRoot)
  StoreDB-->>AppIngestion: Project{id, rootScheme, rootValue}
  AppIngestion->>Provider: readSpecification(projectRoot, capabilities)
  Provider->>Workspace: lire fichiers sources (SHA-256 diff)
  Workspace-->>Provider: fichiers modifiés
  Provider-->>AppIngestion: SpecificationContent{entities, changes, references}
  AppIngestion->>AppSnapshot: createSnapshot(projectId, content)
  AppSnapshot->>StoreDB: persist BUILDING → VALIDATING → READY
  AppSnapshot->>StoreDB: activateSnapshot(snapshotId) [atomique]
  StoreDB-->>AppSnapshot: OK — état = ACTIVE
  AppSnapshot-->>AppIngestion: KnowledgeSnapshot{id, state=ACTIVE}
  AppIngestion-->>CLI: SyncResult{snapshotId, requirementsCount, changesCount}
  CLI-->>Développeur: Synchronisation réussie [stdout]
```

---

## 6.2 Scénario d'erreur — Défaillance d'un adaptateur externe (MINOS)

**Description** : un agent IA demande une analyse de code intelligence ; MINOS
est indisponible. MORPHEUS répond avec les faits locaux disponibles et signale
explicitement l'absence de contexte code.

```mermaid
sequenceDiagram
  autonumber
  actor AgentIA as Agent IA «Person»
  participant McpServer as MCP STDIO Server<br/>«adapter»
  participant AppReasoning as Assisted Reasoning<br/>«Component»
  participant IntegMinos as Adaptateur MINOS<br/>«adapter»
  participant Minos as MINOS ENGINE<br/>«Software System»
  participant AppQuery as Query & Read<br/>«Component»
  participant StoreDB as SQLite Store<br/>«database»

  AgentIA->>McpServer: tool: morpheus_reasoning_analyze {projectId, question}
  McpServer->>AppReasoning: analyze(projectId, question)
  AppReasoning->>IntegMinos: requestCodeContext(projectId, symbols)
  IntegMinos->>Minos: MCP STDIO — minos_find_symbols(...)
  Minos--xIntegMinos: TIMEOUT / processus absent
  IntegMinos-->>AppReasoning: MinosIntegrationException{unavailable}

  Note over AppReasoning: adapter failure != fact loss<br/>Passage en mode dégradé

  AppReasoning->>AppQuery: getLocalFacts(projectId)
  AppQuery->>StoreDB: query snapshot actif
  StoreDB-->>AppQuery: SpecificationFacts{requirements, changes, traceability}
  AppQuery-->>AppReasoning: LocalFacts{...}
  AppReasoning-->>McpServer: ReasoningResult{facts, evidence, codeContextAvailable=false, warning="MINOS unavailable"}
  McpServer-->>AgentIA: JSON — résultat avec warning explicite
```

---

## 6.3 Scénario d'exploitation — Démarrage en mode MCP STDIO

**Description** : un client MCP (ex. Claude Desktop) lance MORPHEUS comme
sous-processus. MORPHEUS initialise le store SQLite (migrations si nécessaire),
enregistre les tools et entre en boucle d'écoute.

```mermaid
sequenceDiagram
  autonumber
  participant ClientMcp as Client MCP<br/>«Software System»
  participant Process as Processus JVM<br/>(morpheus-cli:MorpheusMain)<br/>«adapter»
  participant McpRuntime as MCP Runtime<br/>«adapter»
  participant SchemaMgr as SqliteSchemaManager<br/>«adapter»
  participant StoreDB as SQLite Store<br/>«database»
  participant ToolCatalog as MCP Tool Catalog<br/>«adapter»

  ClientMcp->>Process: spawn — morpheus mcp
  Process->>SchemaMgr: initSchema()
  SchemaMgr->>StoreDB: vérifier version schema / checksum SHA-256
  alt Migrations manquantes
    SchemaMgr->>StoreDB: appliquer V00N__*.sql séquentiellement
    StoreDB-->>SchemaMgr: OK
  end
  SchemaMgr-->>Process: schema OK (version 15)
  Process->>McpRuntime: start(STDIO)
  McpRuntime->>ToolCatalog: enregistrer les 13 familles de tools
  McpRuntime-->>ClientMcp: initialize response (protocole MCP 2.0.0)

  loop Boucle MCP
    ClientMcp->>McpRuntime: JSON-RPC request (tool call)
    McpRuntime->>ToolCatalog: dispatch(toolName, args)
    ToolCatalog-->>McpRuntime: result JSON
    McpRuntime-->>ClientMcp: JSON-RPC response
  end

  ClientMcp->>Process: SIGTERM / stdin EOF
  Process->>McpRuntime: shutdown gracieux
  McpRuntime-->>Process: stopped
```

---

## 6.4 Scénario d'exploitation — Évaluation d'une policy pack

**Description** : un développeur évalue une policy pack active sur l'état
courant d'un projet via l'API HTTP.

```mermaid
sequenceDiagram
  autonumber
  actor Dev as Développeur «Person»
  participant API as HTTP API Server<br/>«adapter»
  participant AppPolicy as Policy & Governance<br/>«Component»
  participant AppQuery as Query & Read<br/>«Component»
  participant StoreDB as SQLite Store<br/>«database»

  Dev->>API: POST /api/v1/policies/evaluate {projectId, policyPackId}
  API->>AppPolicy: evaluate(projectId, policyPackId)
  AppPolicy->>StoreDB: getPolicyPack(policyPackId)
  StoreDB-->>AppPolicy: PolicyPack{rules[], overrides[]}
  AppPolicy->>AppQuery: getActiveSnapshot(projectId)
  AppQuery->>StoreDB: SELECT snapshot WHERE state='ACTIVE'
  StoreDB-->>AppQuery: KnowledgeSnapshot + facts
  AppQuery-->>AppPolicy: SpecificationFacts
  AppPolicy->>AppPolicy: évaluer chaque règle (constraint != policy recommendation)
  AppPolicy-->>API: PolicyEvaluationResult{findings[], violations[], recommendations[]}
  API-->>Dev: 200 OK — JSON résultat (dry-run != mutation)
```
