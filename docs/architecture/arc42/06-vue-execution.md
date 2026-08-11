# §6 — Vue d'exécution (scénarios runtime)

> Cette section décrit les interactions architecturales stables. Les noms de
> commandes, endpoints, classes et tools détaillés restent autoritatifs dans les
> contrats et le code ; ils ne sont pas dupliqués ici lorsqu'ils sont volatils.

---

## 6.1 Synchronisation d'un projet

```mermaid
sequenceDiagram
  autonumber
  actor U as Utilisateur
  participant S as CLI / MCP / HTTP
  participant A as Application
  participant P as Provider
  participant W as Workspace
  participant K as Snapshot services
  participant DB as Store

  U->>S: demande de synchronisation
  S->>A: use case sync
  A->>P: lecture via contrat provider
  P->>W: lecture bornée des sources
  W-->>P: contenu source
  P-->>A: contenu normalisé + diagnostics
  A->>K: construire / valider le candidat
  K->>DB: persister le candidat
  K->>DB: activation atomique si valide
  DB-->>K: snapshot actif
  K-->>A: résultat de sync
  A-->>S: résultat structuré
  S-->>U: succès / diagnostics
```

Invariants :

```text
provider input != published fact until validation
candidate failure != partial ACTIVE exposure
PROPOSED never leaks into CURRENT
activation == atomic
```

---

## 6.2 Défaillance d'une intégration externe

```mermaid
sequenceDiagram
  autonumber
  actor C as Client
  participant S as Surface MORPHEUS
  participant A as Application
  participant I as Adaptateur MINOS/NEXUS
  participant E as Moteur externe
  participant L as Faits locaux

  C->>S: requête nécessitant un enrichissement optionnel
  S->>A: use case
  A->>I: demande d'enrichissement
  I->>E: MCP STDIO
  E--xI: indisponible / timeout / réponse invalide
  I-->>A: échec explicite de l'adaptateur
  A->>L: lire les faits MORPHEUS disponibles
  L-->>A: faits + provenance
  A-->>S: résultat local + warning explicite
  S-->>C: réponse structurée
```

```text
adapter failure != fact loss
external enrichment != published local fact
optional integration != startup dependency
```

---

## 6.3 Démarrage MCP natif

Le mode MCP public de la baseline M28 est lancé en STDIO, par exemple via :

```text
morpheus mcp --stdio
```

```mermaid
sequenceDiagram
  autonumber
  participant Client as Client MCP
  participant Launcher as MORPHEUS launcher
  participant Runtime as MCP runtime
  participant App as Application services

  Client->>Launcher: spawn morpheus mcp --stdio
  Launcher->>Runtime: initialiser le serveur STDIO
  Runtime->>App: câbler les use cases exposés
  Runtime-->>Client: handshake MCP

  loop appels tools
    Client->>Runtime: requête MCP
    Runtime->>App: exécuter le use case
    App-->>Runtime: résultat structuré
    Runtime-->>Client: réponse MCP
  end

  Client->>Launcher: fermeture STDIO / arrêt processus
  Launcher->>Runtime: shutdown
```

La version **2.0.0** mentionnée dans le build est la version du **SDK Java MCP**,
pas un numéro de version du protocole à afficher comme contrat produit.

---

## 6.4 Évaluation de gouvernance en lecture

```mermaid
sequenceDiagram
  autonumber
  actor U as Utilisateur
  participant S as CLI / MCP / HTTP
  participant G as Policy service
  participant Q as Query services
  participant DB as Store

  U->>S: demande d'évaluation / dry-run
  S->>G: évaluer une policy
  G->>Q: lire les faits du snapshot ciblé
  Q->>DB: lecture
  DB-->>Q: faits versionnés
  Q-->>G: faits + provenance
  G->>G: évaluer règles et overrides
  G-->>S: findings / décisions / explications
  S-->>U: résultat
```

Invariants :

```text
dry-run != mutation
policy recommendation != domain fact
warning != blocker unless policy says so
```

---

## 6.5 Mutation lifecycle contrôlée

```mermaid
sequenceDiagram
  autonumber
  actor U as Utilisateur autorisé
  participant S as Surface d'écriture
  participant A as Lifecycle service
  participant DB as Mutation store

  U->>S: demande de transition + révision attendue
  S->>A: validation de capacité / confirmation
  A->>A: évaluer la transition
  alt transition autorisée
    A->>DB: mutation CAS + idempotency + audit
    DB-->>A: nouvel état / nouvelle révision
    A-->>S: mutation appliquée
  else bloquée / inconnue / entrée requise
    A-->>S: décision explicite sans mutation
  end
  S-->>U: résultat
```

```text
ALLOWED != applied
stale revision != overwrite
idempotent retry != duplicate mutation
transition evaluation != lifecycle mutation
```
