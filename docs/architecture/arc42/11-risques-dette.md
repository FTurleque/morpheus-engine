# §11 — Risques et dette technique

> **Sources** : `docs/adr/README.md` (ADR « avec contraintes »),
> `docs/governance/ROADMAP.md`, `docs/architecture/overview.md`,
> `docs/validation/`, structure du code explorée.
>
> **Légende probabilité/impact** : 1 = faible · 2 = moyen · 3 = élevé
> **Exposition** = probabilité × impact

---

## 11.1 Registre des risques techniques

| ID | Risque | Probabilité | Impact | Exposition | Mitigation | Propriétaire | Date cible |
|----|--------|:-----------:|:------:|:----------:|-----------|-------------|-----------|
| RT-01 | `jdk.httpserver` — limite de performance sous charge (ex. mode remote multi-utilisateurs) | 2 | 2 | **4** | ADR-0065 permet la substitution ; déclencher ADR si load tests échouent | Architecte | Post-M28 |
| RT-02 | SQLite — concurrence en mode remote (plusieurs écrivains simultanés) | 2 | 3 | **6** | Mode WAL ; `SqliteConcurrencyHardeningTest` ; ADR-0018 prévoit la substitution du backend | Architecte stockage | Si mode remote évolue |
| RT-03 | Migrations SQLite forward-only — pas de rollback de schéma disponible | 2 | 3 | **6** | Backups automatiques avant migration ; `SqliteSchemaManager` bloque sur checksum mismatch | Architecte stockage | Permanent |
| RT-04 | Dépendance `io.modelcontextprotocol.sdk:mcp 2.0.0` — API jeune, risque de breaking change | 2 | 2 | **4** | BOM isolé ; tests de contrat MCP ; ADR-0062 | Architecte MCP | À chaque release MCP SDK |
| RT-05 | Provider SDK — compatibilité binaire non testée sur plugin tiers non contrôlé | 1 | 2 | **2** | `morpheus-provider-testkit` ; `morpheus-provider-reference` comme référence | Architecte SDK | Post-ADR-0090 |
| RT-06 | Absence de macOS dans la matrice CI | 2 | 1 | **2** | Hypothèse que Linux coverage est suffisant ; à valider si usage macOS documenté | CI | Prochaine roadmap |
| RT-07 | Mode remote — authentification limitée à Bearer token (pas de SSO/LDAP) | 1 | 2 | **2** | ADR-0094 signale la contrainte ; un ADR-0098 est anticipé si besoin entreprise | Architecte sécurité | Selon demande |
| RT-08 | Logging `slf4j-nop` — diagnostic en production difficile en cas de bug runtime | 2 | 2 | **4** | Le mode MCP impose ce choix (stdout réservé) ; endpoints health/metrics compensent partiellement | Architecte observabilité | Permanent |
| RT-09 | Tests de performance sans seuil documenté (latence sync, démarrage) | 2 | 2 | **4** | Gates M19 présents dans `morpheus-architecture-tests` mais seuils non extraits | Architecte qualité | Court terme |
| RT-10 | Rollback applicatif — retour au binaire précédent avec base SQLite déjà migrée | 2 | 3 | **6** | Backup obligatoire avant mise à jour ; procédure documentée dans §7 | Équipe distribution | Permanent |

---

## 11.2 Registre de la dette technique

| ID | Dette | Domaine | Priorité | Impact si non traité | Plan de remboursement |
|----|-------|---------|----------|---------------------|-----------------------|
| DT-01 | ADR-0018 « avec contraintes » — SQLite est un choix initial, pas définitif ; aucun autre backend n'est implémenté | Architecture stockage | Haute | Blocage si besoin de scalabilité multi-utilisateurs | Formaliser ADR-0097 ; implémenter un second adaptateur store (ex. DuckDB) |
| DT-02 | Scénarios de qualité manquants (§10.3) — seuils de performance non formalisés | Documentation | Moyenne | Impossibilité de prouver les objectifs de performance | Extraire les seuils des tests M19 ; compléter §10 |
| DT-03 | ADR-0063 à ADR-0082 — titres non inclus dans ce document (référence à l'index ADR externe) | Documentation | Faible | Complétude de la documentation d'architecture | Ajouter une ligne par ADR dans §9.5 lors du prochain cycle de documentation |
| DT-04 | Pas de tests d'injection SQL explicites dans le code review | Sécurité | Haute | Vulnérabilité potentielle dans les stores SQLite | Revue de code ciblée sur les requêtes paramétrées dans `morpheus-store-sqlite` |
| DT-05 | Macros de build `mvnw` — pas de caching des dépendances documenté pour les PRs (temps de build potentiellement long) | Build | Faible | Feedback CI lent | Configurer le cache Maven dans `.github/workflows/ci.yml` si nécessaire |
| DT-06 | `slf4j-nop` — log no-op permanent ; aucune option de debug logging sans recompilation | Observabilité | Moyenne | Diagnostic difficile en cas de bug silencieux en production | Envisager un niveau DEBUG optionnel activable via variable d'environnement |
| DT-07 | Distribution macOS non couverte (pas de `build-portable.sh` pour macOS testé en CI) | Distribution | Moyenne | Utilisateurs macOS non supportés officiellement | Hypothèse à valider — ajouter macOS à la matrice CI si usage avéré |
| DT-08 | Seul `validate-m21` est intégré dans `ci.yml` (gate principal) — les gates M22–M28 sont manuels | CI/CD | Moyenne | Régression possible sur les milestones avancés sans signal CI | Planifier l'intégration des gates post-M21 dans ci.yml |

---

## 11.3 Incohérences détectées

| ID | Incohérence | Localisation | Gravité | Résolution proposée |
|----|-------------|-------------|---------|---------------------|
| IC-01 | `ci.yml` intègre uniquement le gate M21 (`validate-m21.sh/.cmd`) mais le système est à M27 | `.github/workflows/ci.yml` vs `docs/validation/` | Élevée | Mettre à jour `ci.yml` pour exécuter le gate M27 (ou le dernier qualifié) |
| IC-02 | `docs/architecture/overview.md` porte le statut « Proposition — à valider pendant C0 » alors que M27 est qualifié | `docs/architecture/overview.md` | Moyenne | Mettre à jour le statut de `overview.md` |
| IC-03 | Les seuils de performance des gates M19 ne sont pas surfacés dans la documentation d'architecture | `morpheus-architecture-tests/m19/` | Faible | Extraire et documenter dans §10 |
