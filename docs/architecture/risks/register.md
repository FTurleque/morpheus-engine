# Registre des risques et de la dette — MORPHEUS ENGINE

> Synthèse du §11 sous forme de tableaux de suivi.
> Mise à jour : 2026-08-06.
>
> **Probabilité** : 1 faible · 2 moyen · 3 élevé
> **Impact** : 1 faible · 2 moyen · 3 élevé
> **Exposition** = probabilité × impact

---

## Risques techniques — classés par exposition décroissante

| ID | Risque | P | I | E | Mitigation actuelle | Propriétaire | Révision |
|----|--------|:-:|:-:|:-:|---------------------|-------------|---------|
| RT-02 | SQLite concurrence en mode remote multi-écrivains | 2 | 3 | **6** | WAL + tests concurrence ; ADR-0018 prévoit substitution | Architecte stockage | Si mode remote évolue |
| RT-03 | Migrations SQLite forward-only — pas de rollback schéma | 2 | 3 | **6** | Backups avant migration ; `SqliteSchemaManager` bloque sur mismatch | Architecte stockage | Permanent |
| RT-10 | Rollback applicatif avec base déjà migrée | 2 | 3 | **6** | Backup obligatoire avant installation ; procédure §7 | Équipe distribution | Permanent |
| RT-01 | `jdk.httpserver` — limite de performance sous charge remote | 2 | 2 | **4** | ADR-0065 permet la substitution | Architecte | Post-M28 |
| RT-04 | MCP SDK 2.0.0 — API jeune, risque breaking change | 2 | 2 | **4** | BOM isolé ; tests de contrat MCP | Architecte MCP | Chaque release SDK |
| RT-08 | `slf4j-nop` — diagnostic difficile en production | 2 | 2 | **4** | Endpoints health/metrics ; contrainte MCP | Architecte observabilité | Permanent |
| RT-09 | Tests de performance sans seuil documenté | 2 | 2 | **4** | Gates M19 présents mais seuils non extraits | Architecte qualité | Court terme |
| RT-06 | Absence de macOS en matrice CI | 2 | 1 | **2** | Linux coverage considéré suffisant (hypothèse) | CI | Prochaine roadmap |
| RT-05 | Compatibilité binaire provider SDK non testée sur tiers | 1 | 2 | **2** | Provider testkit + provider référence | Architecte SDK | Post-ADR-0090 |
| RT-07 | Authentification remote limitée à Bearer token | 1 | 2 | **2** | ADR-0094 signale la contrainte ; ADR-0098 anticipé | Architecte sécurité | Selon demande |

---

## Dette technique — classée par priorité

| ID | Dette | Domaine | Priorité | Impact si non traité |
|----|-------|---------|----------|---------------------|
| DT-04 | Pas de tests d'injection SQL explicites | Sécurité | **Haute** | Vulnérabilité potentielle stores SQLite |
| DT-01 | SQLite unique backend — substitution non implémentée | Architecture | **Haute** | Blocage si scalabilité nécessaire |
| DT-02 | Scénarios de qualité sans seuils de performance | Documentation | **Moyenne** | Impossibilité de prouver les objectifs perf |
| DT-06 | `slf4j-nop` — pas d'option debug sans recompilation | Observabilité | **Moyenne** | Diagnostic difficile en cas de bug silencieux |
| DT-07 | Distribution macOS non testée en CI | Distribution | **Moyenne** | Utilisateurs macOS non supportés officiellement |
| DT-08 | Gates post-M21 non intégrés dans `ci.yml` | CI/CD | **Moyenne** | Régression possible sur milestones avancés |
| DT-03 | ADR-0063 à 0082 non listés individuellement dans §9 | Documentation | **Faible** | Complétude documentation d'architecture |
| DT-05 | Pas de cache Maven documenté dans CI | Build | **Faible** | Feedback CI potentiellement lent |

---

## Incohérences détectées — plan de résolution

| ID | Incohérence | Gravité | Action | Responsable |
|----|-------------|---------|--------|------------|
| IC-01 | `ci.yml` exécute gate M21 alors que M27 est qualifié | **Élevée** | Mettre à jour `ci.yml` pour exécuter `validate-m27.sh/.cmd` | CI |
| IC-02 | `docs/architecture/overview.md` porte statut « Proposition C0 » | **Moyenne** | Mettre à jour le statut de `overview.md` à « Acceptée — M27 » | Architecte |
| IC-03 | Seuils de performance gates M19 non surfacés en documentation | **Faible** | Extraire et documenter dans §10 / `quality/scenarios.md` | Architecte qualité |

---

## Plan de migration priorisé

### Court terme (avant M28)

1. **[IC-01]** Mettre à jour `.github/workflows/ci.yml` — remplacer `validate-m21` par `validate-m27` (ou le dernier gate qualifié).
2. **[IC-02]** Mettre à jour `docs/architecture/overview.md` — changer le statut de « Proposition C0 » à « Acceptée — M27 ».
3. **[RT-09 / DT-02]** Extraire les seuils de performance des tests `morpheus-architecture-tests/m19/` et les documenter dans `quality/scenarios.md`.

### Moyen terme (post-M28)

4. **[DT-04]** Revue de code ciblée sur `morpheus-store-sqlite` — vérifier les requêtes paramétrées (injection SQL).
5. **[DT-06]** Envisager un mode debug optionnel activable via `MORPHEUS_LOG_LEVEL` sans impacter le mode MCP.
6. **[DT-08]** Intégrer les gates M22–M27 dans `.github/workflows/ci.yml`.
7. **[RT-06 / DT-07]** Ajouter `macos-latest` à la matrice CI (si usage macOS avéré).

### Long terme (selon roadmap)

8. **[DT-01 / RT-02]** Formaliser ADR-0097 — backend de stockage alternatif (PostgreSQL ou DuckDB).
9. **[RT-07]** Formaliser ADR-0098 — authentification SSO/LDAP pour le mode remote entreprise.
10. **[RT-01]** Évaluer le remplacement de `jdk.httpserver` si les tests de charge mode remote révèlent des limites.

---

## Contrôles CI recommandés

| Contrôle | Outil | Priorité |
|----------|-------|----------|
| Mise à jour automatique du gate de validation dans `ci.yml` lors de chaque milestone | Script Maven / hook GitHub | **Haute** |
| Vérification que `docs/adr/README.md` contient un ADR pour toute décision structurante | Revue de PR | **Haute** |
| Test de smoke cross-platform sur macOS | GitHub Actions `macos-latest` | **Moyenne** |
| Analyse de dépendances vulnérables (OWASP Dependency Check ou équivalent) | Plugin Maven | **Moyenne** |
| Vérification du SBOM CycloneDX — aucune dépendance avec licence incompatible | `cyclonedx-maven-plugin` + filtre | **Faible** |
