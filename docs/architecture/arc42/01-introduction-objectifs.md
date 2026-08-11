# §1 — Introduction et objectifs

> **Sources actives** : `pom.xml`, `docs/adr/`, `docs/governance/`,
> `docs/validation/`, `contracts/public-surfaces.tsv` et code du HEAD `develop`.
> Les documents de conception plus anciens sont utilisés comme contexte
> historique lorsqu'ils restent cohérents avec ces sources.

---

## 1.1 Résumé du système

**MORPHEUS ENGINE 1.2.0** est un moteur local-first d'intelligence des
spécifications logicielles. Il ingère des workspaces au moyen de providers
explicites — notamment **OpenSpec** et **Structured Markdown** — puis produit
une représentation normalisée, versionnée, traçable et interrogeable de
l'intention d'un projet.

Il est distribué avec un runtime Java embarqué et expose trois surfaces
principales : CLI, serveur MCP STDIO et API HTTP `/api/v1`. Un mode serveur
d'équipe HTTPS est disponible en option, avec authentification Bearer et RBAC.

Le cœur fonctionnel ne requiert ni LLM, ni service cloud, ni provider externe
obligatoire. MINOS et NEXUS restent des intégrations optionnelles et isolées.

---

## 1.2 Objectifs métier

| # | Objectif | Priorité |
|---|----------|----------|
| OM-1 | Comprendre les états CURRENT, PROPOSED et HISTORICAL des spécifications sans fusion silencieuse | Critique |
| OM-2 | Fournir une traçabilité vérifiable entre exigences, changements, tâches, décisions et preuves | Critique |
| OM-3 | Automatiser les contrôles de gouvernance via des Policy Packs | Haute |
| OM-4 | Permettre l'analyse multi-projets via des portfolios | Haute |
| OM-5 | Exposer des faits et du contexte aux agents IA sans leur déléguer la vérité publiée | Haute |
| OM-6 | Fonctionner sur Windows et Linux sans infrastructure cloud obligatoire | Critique |
| OM-7 | Supporter des providers externes via le Provider SDK sans modifier le cœur | Moyenne |

---

## 1.3 Parties prenantes

| Partie prenante | Rôle | Intérêts architecturaux |
|-----------------|------|------------------------|
| Développeurs individuels | Utilisateurs CLI et MCP | Ergonomie, déterminisme, fonctionnement local |
| Équipes (mode remote) | Utilisateurs du serveur partagé | Authentification, RBAC, concurrence et backups |
| Clients et agents MCP | Consommateurs des tools MORPHEUS | Stabilité des contrats et convergence des surfaces |
| MINOS ENGINE | Fournisseur optionnel de code intelligence | Contrat MCP STDIO et isolation des pannes |
| NEXUS ENGINE | Fournisseur optionnel de sélection de contexte | Contrat MCP STDIO et isolation des pannes |
| Mainteneur MORPHEUS | Architecture, développement et releases | Maintenabilité, sécurité, testabilité et évolutivité |
| Auteurs de providers | Développeurs de plugins | SDK stable, activation explicite et testkit |

---

## 1.4 Objectifs qualité prioritaires

| Rang | Qualité | Justification |
|------|---------|---------------|
| 1 | **Exactitude** | Les faits publiés restent séparés des inférences, heuristiques et suggestions ; provenance et evidence sont conservées. |
| 2 | **Maintenabilité** | Frontières de couches testées, ADR versionnés, SBOM et gates reproductibles. |
| 3 | **Sécurité** | Validation des entrées, confinement filesystem, durcissement SQLite/JSON et intégrations externes opt-in. |
| 4 | **Portabilité** | Java 21, runtime embarqué, SQLite local et distribution native-first Windows/Linux. |
| 5 | **Extensibilité / résilience** | Providers et intégrations derrière des ports ; l'absence ou la panne d'un adaptateur optionnel ne détruit pas les faits locaux. |
