# §1 — Introduction et objectifs

> **Sources** : `docs/architecture/overview.md`, `docs/developer/ARCHITECTURE.md`,
> `docs/adr/README.md` (section invariants), `pom.xml` (version 1.2.0),
> `docs/README.md` (entrée documentation).

---

## 1.1 Résumé du système

**MORPHEUS ENGINE** (v1.2.0) est un moteur local-first d'intelligence des
spécifications logicielles. Il ingère des workspaces de projets depuis des
sources hétérogènes (fichiers Markdown structurés, specs OpenAPI, providers
tiers), puis produit une représentation normalisée, versionnée, traçable et
interrogeable de l'intention d'un projet.

Il est distribué comme binaire autonome (JVM embarquée via jpackage) et expose
trois surfaces d'accès complémentaires : une interface CLI interactive, un
serveur MCP STDIO pour les clients d'IA, et une API HTTP locale. Un mode
serveur distant est disponible en option, avec TLS et RBAC.

Le système ne dépend d'aucun LLM en fonctionnement nominal, d'aucun service
cloud, et d'aucun provider particulier.

---

## 1.2 Objectifs métier

| # | Objectif | Priorité |
|---|----------|----------|
| OM-1 | Permettre à un développeur de comprendre l'état courant, proposé et historique des spécifications d'un projet sans interprétation manuelle | Critique |
| OM-2 | Fournir une traçabilité vérifiable entre exigences, changements, tâches et décisions | Critique |
| OM-3 | Automatiser les contrôles de gouvernance via des policy packs | Haute |
| OM-4 | Permettre l'analyse multi-projets via des portfolios | Haute |
| OM-5 | S'intégrer comme source de contexte enrichi pour les agents IA (MCP) sans leur déléguer la vérité des faits | Haute |
| OM-6 | Fonctionner sur poste développeur Windows et Linux sans infrastructure cloud | Critique |
| OM-7 | Supporter des plugins provider externes sans modification du moteur | Moyenne |

---

## 1.3 Parties prenantes

| Partie prenante | Rôle | Intérêts architecturaux |
|-----------------|------|------------------------|
| Développeurs individuels | Utilisateurs CLI et MCP | CLI ergonomique, latence faible, fonctionnement hors-ligne |
| Équipes (mode remote) | Utilisateurs du serveur partagé | Authentification, RBAC, backups, multi-utilisateurs |
| Agents IA (JARVIS, clients MCP) | Consommateurs MCP STDIO et HTTP API | Stabilité des surfaces, surface parity CLI/MCP/HTTP |
| MINOS ENGINE | Fournisseur de code intelligence | Contrat MCP STDIO, tolérance aux pannes |
| NEXUS ENGINE | Fournisseur de context selection | Contrat MCP STDIO, tolérance aux pannes |
| Auteur du système (F. Turleque) | Architecte, développeur principal | Maintenabilité, testabilité, roadmap M28+ |
| Contributeurs de providers | Développeurs de plugins | SDK stable, documentation PROVIDER_SDK |

---

## 1.4 Objectifs qualité prioritaires

Les cinq objectifs qualité structurants, par ordre de priorité décroissante :

| Rang | Qualité | Justification |
|------|---------|---------------|
| 1 | **Exactitude** — les faits produits sont vérifiables et jamais inventés | Toute inférence est explicitement marquée ; politique « heuristic != published fact » (ADR-0004) |
| 2 | **Maintenabilité** — isolation en couches, gates CI, SBOM, tests d'architecture | Architecture en couches enforced par ArchUnit ; 96 ADR ; 731 fichiers Java |
| 3 | **Portabilité** — fonctionnement sur Windows et Linux sans infrastructure externe | JVM embarquée, SQLite local, distribution native-first (ADR-0027) |
| 4 | **Extensibilité** — ajout de providers et d'intégrations sans modifier le cœur | Port-adapter pattern ; SDK provider externe (ADR-0090) |
| 5 | **Résilience** — les défaillances des systèmes externes ne dégradent pas la disponibilité des faits locaux | Absence d'intégrations obligatoires ; adapter failure != fact loss (invariant ADR README) |
