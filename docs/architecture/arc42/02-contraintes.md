# §2 — Contraintes

> **Sources actives** : `pom.xml`, `docs/adr/`, `docs/governance/`,
> `docs/validation/`, tests d'architecture et code du HEAD `develop`.

---

## 2.1 Contraintes métier

| ID | Contrainte | Nature | Preuve |
|----|-----------|--------|--------|
| CB-1 | Les faits publiés ne doivent pas être confondus avec les inférences ou suggestions | **Imposée** — ADR-0004 / ADR-0095 | Invariants de reasoning et tests associés |
| CB-2 | Les états CURRENT, PROPOSED et HISTORICAL restent strictement séparés | **Imposée** — ADR-0006 | Invariant `PROPOSED never leaks into CURRENT` |
| CB-3 | Les mutations lifecycle sont contrôlées, révisionnées et auditées | **Imposée** — ADR-0083 | Store de mutations et audit append-only |
| CB-4 | Les providers sont read-first ; toute écriture requiert une capacité explicite | **Imposée** — ADR-0008 | Invariant `READ_CHANGES != WRITE_CHANGE` |
| CB-5 | Export, dry-run et analyse restent read-only | **Imposée** | Contrats de surfaces et tests de non-mutation |

---

## 2.2 Contraintes techniques

| ID | Contrainte | Nature | Preuve |
|----|-----------|--------|--------|
| CT-1 | Java 21 est la baseline minimum de production | **Imposée** — ADR-0016 | `maven-enforcer-plugin`, `maven.compiler.release=21` |
| CT-2 | Maven 3.9.16 ≤ version < 4.0.0 | **Imposée** — ADR-0017 | `maven-enforcer-plugin` dans `pom.xml` |
| CT-3 | Le domaine et l'application ne dépendent pas des adaptateurs | **Imposée** | Tests ArchUnit de `morpheus-architecture-tests` |
| CT-4 | Le serveur HTTP repose sur `jdk.httpserver` dans la baseline 1.2.0 | **Imposée** — ADR-0065 | Module `morpheus-api` |
| CT-5 | Le serveur MCP natif utilise `io.modelcontextprotocol.sdk:mcp` 2.0.0 en STDIO | **Imposée** — ADR-0062 | `mcp-sdk.version` dans `pom.xml` |
| CT-6 | Le stockage persistant initial est SQLite via `sqlite-jdbc` **3.53.2.0**, derrière des ports applicatifs | **Imposée** — ADR-0018 | `sqlite-jdbc.version` dans `pom.xml` |
| CT-7 | Jackson est aligné sur la BOM **3.1.5** | **Imposée par la baseline D2** | `jackson.version` dans `pom.xml` |
| CT-8 | L'identité métier est indépendante du chemin, de la version et des références externes | **Imposée** — ADR-0009 / ADR-0015 | Types domaine et tests d'identité |
| CT-9 | Les distributions Windows/Linux embarquent le runtime Java | **Imposée** — ADR-0061 | Scripts de `distribution/` |
| CT-10 | Les entrées externes sont bornées et validées avant ingestion | **Imposée par le hardening D2** | Budgets d'ingestion, validation JSON et confinement filesystem |

---

## 2.3 Contraintes supply-chain et sécurité

| ID | Contrainte | Nature | Preuve |
|----|-----------|--------|--------|
| CS-1 | Un SBOM CycloneDX est généré par le build | **Imposée** | `cyclonedx-maven-plugin` |
| CS-2 | L'analyse de dépendances Maven est bloquante sur les anomalies de déclaration | **Imposée par D2** | `maven-dependency-plugin:analyze-only` avec `failOnWarning=true` |
| CS-3 | Les dépendances vulnérables connues sont contrôlées par le gate SCA local | **Imposée par D2** | OWASP Dependency-Check + suppressions versionnées |
| CS-4 | L'installateur Windows reste per-user et ne requiert pas d'élévation | **Imposée** | Configuration Inno Setup / `%LOCALAPPDATA%` |

---

## 2.4 Contraintes organisationnelles

| ID | Contrainte | Nature | Preuve |
|----|-----------|--------|--------|
| CO-1 | Les décisions structurantes sont conservées comme ADR et accompagnées de preuves | **Imposée** | `docs/adr/` |
| CO-2 | L'intégration suit la politique `feature/milestone → develop`, puis release qualifiée vers `main` | **Imposée** | `docs/governance/ROADMAP.md` |
| CO-3 | Les migrations SQLite appliquées ne sont pas réécrites ; une évolution ajoute une nouvelle migration | **Imposée** | Vérification de checksum du schéma |
| CO-4 | Un ADR accepté n'est pas supprimé : il est remplacé explicitement si la décision évolue | **Imposée** | Convention ADR |
| CO-5 | La documentation active est versionnée dans le dépôt avec le code et les preuves | **Préférence forte** | Dossier `docs/` |

---

## 2.5 Contrainte vs choix substituable

| Sujet | Baseline actuelle | Évolution possible |
|-------|------------------|--------------------|
| Langage | Java 21 | Toute évolution nécessite une décision et une qualification dédiées |
| Build | Maven + Wrapper | Aucune migration de build n'est décidée dans la roadmap active |
| Stockage | SQLite | Les ports permettent un backend alternatif si un besoin prouvé le justifie |
| HTTP | `jdk.httpserver` | Substitution possible par ADR si les objectifs qualité l'exigent |
| LLM | Aucun LLM requis dans le cœur | Les moteurs externes peuvent utiliser de l'IA hors du processus MORPHEUS |
