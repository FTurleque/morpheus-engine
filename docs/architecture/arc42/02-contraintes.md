# §2 — Contraintes

> **Sources** : `docs/adr/README.md` (invariants et principe de validation),
> `pom.xml` (enforcer Java ≥ 21, Maven [3.9.16, 4.0.0)),
> `docs/developer/ARCHITECTURE.md`, ADR-0004, ADR-0016, ADR-0017, ADR-0027.

---

## 2.1 Contraintes métier

| ID | Contrainte | Nature | Preuve |
|----|-----------|--------|--------|
| CB-1 | Le système ne doit jamais produire de faits non vérifiables sans les marquer explicitement comme inférences | **Imposée** — ADR-0004 | Invariant « heuristic != published fact » — `docs/adr/README.md` |
| CB-2 | Les états CURRENT, PROPOSED et HISTORICAL doivent rester strictement séparés | **Imposée** — ADR-0006 | Invariant « PROPOSED never leaks into CURRENT » |
| CB-3 | Les mutations d'état lifecycle sont tracées et irréversibles (pas de suppression silencieuse) | **Imposée** — ADR-0083 | `V011__controlled_lifecycle_mutations.sql` |
| CB-4 | Les providers sont read-first ; l'écriture est une capacité optionnelle déclarée | **Imposée** — ADR-0008 | Invariant « READ_CHANGES != WRITE_CHANGE » |
| CB-5 | Un export n'est jamais une mutation | **Imposée** | Invariant « export != mutation » |

---

## 2.2 Contraintes techniques

| ID | Contrainte | Nature | Preuve |
|----|-----------|--------|--------|
| CT-1 | Java 21 est le baseline minimum de production | **Imposée** — ADR-0016 | `maven-enforcer-plugin` : `java.version >= 21` dans `pom.xml` |
| CT-2 | Maven 3.9.16 ≤ version < 4.0.0 | **Imposée** — ADR-0017 | `maven-enforcer-plugin` dans `pom.xml` |
| CT-3 | Aucun framework d'injection (Spring, Quarkus, etc.) dans le module domain | **Imposée** | Tests ArchUnit `morpheus-architecture-tests/` ; `LayerDependencyTest` |
| CT-4 | Le serveur HTTP local utilise uniquement `jdk.httpserver` (pas de bibliothèque tierce) | **Imposée** — ADR-0065 | `morpheus-api/pom.xml` sans dépendance Netty/Undertow/Jetty |
| CT-5 | Le serveur MCP utilise uniquement `io.modelcontextprotocol.sdk:mcp 2.0.0` en mode STDIO | **Imposée** — ADR-0062 | `pom.xml` BOM MCP 2.0.0 |
| CT-6 | Le stockage permanent est SQLite (`org.xerial:sqlite-jdbc 3.53.1.0`) derrière un port d'application | **Imposée** — ADR-0018 | `morpheus-store-sqlite/pom.xml`, `V001__foundation.sql` |
| CT-7 | Le logging en production est `slf4j-nop` (aucune sortie log sur stdout/stderr MCP) | **Imposée** — ADR-0062 | `org.slf4j:slf4j-nop 2.0.16` dans `pom.xml` |
| CT-8 | Les dépendances inter-modules respectent le sens `adapters → application → domain` | **Imposée** | `LayerDependencyTest` (ArchUnit) |
| CT-9 | L'identité de domaine est UUIDv7 opaque — pas un chemin, pas une version, pas une référence externe | **Imposée** — ADR-0015 | `DomainIdentityTest` ; invariant `DomainIdentity != EntityVersionId != SourceLocator` |
| CT-10 | La distribution binaire inclut la JVM (portable) — pas de JDK requis sur la machine cible | **Imposée** — ADR-0061 | `distribution/build-portable.ps1` (jpackage) |

---

## 2.3 Contraintes réglementaires

| ID | Contrainte | Nature | Preuve |
|----|-----------|--------|--------|
| CR-1 | Un SBOM CycloneDX 1.6 est généré à chaque build Maven (`verify`) | **Imposée** | `cyclonedx-maven-plugin 2.9.2` dans `pom.xml` |
| CR-2 | L'installateur Windows n'exige pas de privilèges administrateur (`PrivilegesRequired=lowest`) | **Imposée** | `distribution/windows/MORPHEUS.iss` : `PrivilegesRequired=lowest` ; cible `%LOCALAPPDATA%\Programs\MORPHEUS` |

*Aucune contrainte RGPD ou réglementaire sectorielle identifiée — le système ne traite pas de données personnelles dans son fonctionnement nominal. Hypothèse à valider si le mode remote est déployé en entreprise.*

---

## 2.4 Contraintes organisationnelles

| ID | Contrainte | Nature | Preuve |
|----|-----------|--------|--------|
| CO-1 | Un ADR doit être accepté après preuve avant de pouvoir influencer le code (principe de validation en 7 étapes) | **Imposée** | `docs/adr/README.md` — « Principe de validation » |
| CO-2 | Tout milestone doit passer un gate de validation (`validate-mN.sh` / `.ps1`) avant fusion sur `main` | **Imposée** | `.github/workflows/ci.yml` ; scripts `scripts/` |
| CO-3 | Les migrations SQLite ne sont jamais modifiées après application — elles sont ajoutées | **Imposée** | `SqliteSchemaManager` : vérification SHA-256 à chaque démarrage |
| CO-4 | Un ADR accepté n'est jamais supprimé — il est marqué « Remplacé » et un nouvel ADR est créé | **Imposée** | Convention `docs/adr/README.md` |
| CO-5 | La documentation est maintenue dans le dépôt source | **Préférence** | Dossier `docs/` versionnés avec le code |

---

## 2.5 Distinction contrainte / préférence

| Sujet | Contrainte imposée | Préférence (peut évoluer) |
|-------|--------------------|--------------------------|
| Langage | Java 21 (enforcer) | — |
| Build | Maven (wrapper versionné) | Gradle envisagé post-M28 (Hypothèse à valider) |
| Stockage | SQLite initial | Port permet la substitution (ADR-0018 : « contrainte avec contraintes ») |
| HTTP framework | jdk.httpserver | Peut être remplacé si le besoin de performance l'exige (ADR-0065) |
| LLM | Absent du cœur | Les intégrations MINOS/NEXUS/JARVIS peuvent être LLM-backed en dehors du moteur |
