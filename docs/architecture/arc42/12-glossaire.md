# §12 — Glossaire

> Définitions architecturales utilisées dans cette documentation. Les types et
> contrats du code/ADR priment si une définition historique diverge.

---

## 12.1 Termes métier

| Terme | Définition | À ne pas confondre avec |
|-------|------------|-------------------------|
| **Spécification** | Représentation normalisée de l'intention d'un projet, issue d'un ou plusieurs providers | Le fichier source brut |
| **Workspace** | Racine de fichiers autorisée à partir de laquelle un projet peut être découvert/ingéré | `DomainIdentity` |
| **Project** | Unité logique gérée par MORPHEUS | Un simple chemin filesystem |
| **Requirement** | Exigence normalisée et versionnée dans le modèle MORPHEUS | Un paragraphe source non interprété |
| **Change** | Changement de spécification avec état temporel/lifecycle explicites | Un commit Git |
| **Portfolio** | Agrégat multi-projets avec références et traversal bornés | Une simple liste sans sémantique |
| **Policy Pack** | Ensemble versionné de règles de gouvernance et de leur configuration | Une contrainte de domaine intrinsèque |
| **Saved View** | Définition versionnée d'une requête/vue | Le résultat matérialisé d'une requête |

---

## 12.2 Termes techniques

| Terme | Définition | À ne pas confondre avec |
|-------|------------|-------------------------|
| **DomainIdentity** | Identité logique MORPHEUS opaque et stable | `EntityVersionId`, `SourceLocator`, external ID |
| **EntityVersionId** | Identifiant d'une occurrence/version d'entité | Identité logique stable |
| **SourceLocator** | Emplacement permettant de retrouver une source ou une preuve | Identité métier |
| **ExternalReference** | Référence vers une ressource d'un système externe, résolue ou non | `SourceLocator` |
| **KnowledgeSnapshot** | Vue cohérente et publiable d'un état de connaissance | `SpecificationVersion` ; les deux concepts restent distincts |
| **SpecificationKnowledgeStore** | Port possédé par MORPHEUS pour la persistance/interrogation des connaissances | SQLite lui-même |
| **TraceabilityLink** | Relation sémantique typée, avec résolution/provenance selon le contrat | Dépendance technique implicite |
| **Provider** | Adaptateur qui traduit une source supportée vers les contrats de lecture MORPHEUS | Le domaine MORPHEUS |
| **Provider SDK** | Contrats, activation et outils permettant d'implémenter des providers externes | Un provider particulier |
| **MCP** | Model Context Protocol | Un moteur de raisonnement ou un modèle IA |
| **STDIO** | Transport inter-processus via stdin/stdout utilisé par le MCP natif | Un service réseau |
| **Surface publique** | CLI, MCP ou HTTP exposant une capacité MORPHEUS | La couche application interne |
| **Convergence des surfaces** | Cohérence des capacités métier entre surfaces selon le contrat public | Payloads ou transports obligatoirement identiques |
| **Gate** | Ensemble reproductible de contrôles/tests servant de preuve à une intégration ou un milestone | Un test unitaire isolé |
| **WAL** | Write-Ahead Logging SQLite | Une stratégie de backup |
| **CAS** | Compare-And-Set / révision attendue lors d'une mutation contrôlée | Un verrou global implicite |

---

## 12.3 Acronymes

| Acronyme | Développement | Contexte |
|----------|---------------|----------|
| **ADR** | Architecture Decision Record | Décision architecturale versionnée |
| **arc42** | Architecture communication template | Cadre documentaire |
| **C4** | Context, Container, Component, Code | Modèle de vues d'architecture |
| **CLI** | Command-Line Interface | Surface locale |
| **MCP** | Model Context Protocol | Intégration des clients/agents |
| **SBOM** | Software Bill of Materials | Inventaire de composants logiciels |
| **SCA** | Software Composition Analysis | Analyse de dépendances/vulnérabilités |
| **SDK** | Software Development Kit | Ici : Provider SDK |
| **RBAC** | Role-Based Access Control | Autorisation en mode remote |
| **TLS** | Transport Layer Security | Chiffrement HTTPS remote |
| **WAL** | Write-Ahead Logging | Mode journal SQLite |
| **UUIDv7** | Universally Unique Identifier version 7 | Format utilisé par certaines identités MORPHEUS |
| **JVM** | Java Virtual Machine | Runtime Java, embarqué dans les distributions |
| **LLM** | Large Language Model | Non requis par le cœur fonctionnel MORPHEUS |

---

## 12.4 Écosystème

| Nom | Rôle | Relation avec MORPHEUS |
|-----|------|------------------------|
| **MINOS ENGINE** | Code intelligence | Intégration optionnelle via MCP STDIO |
| **NEXUS ENGINE** | Sélection / ranking / compression de contexte | Intégration optionnelle via MCP STDIO |
| **JARVIS** | Orchestration / séquencement d'actions | Système externe ; MORPHEUS reste propriétaire de ses faits et règles |

---

## 12.5 Milestones cités dans l'architecture actuelle

| Repère | Signification |
|--------|---------------|
| **M21** | Gate durable d'intégrité production / convergence actuellement utilisé par la CI publique avec la version 1.2.0 |
| **M22–M28** | Milestones fonctionnels spécialisés qualifiés et conservés dans les validations historiques |
| **R3** | Release MORPHEUS 1.2.0 publiée |
| **D2** | Hardening post-R3 de la baseline 1.2.0 |
| **MRA** | Série de remédiations/audits post-D2 ; une PR draft n'appartient pas à la baseline tant qu'elle n'est pas intégrée |
