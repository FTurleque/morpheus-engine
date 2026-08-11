# Scénarios de qualité — MORPHEUS ENGINE

> Ce fichier développe les scénarios de qualité définis en §10.
> Il suit le format arc42 : stimulus, environnement, réponse, mesure, seuil,
> vérification, propriétaire.
>
> Les scénarios validés par des tests automatisés sont marqués ✅.
> Les scénarios sans seuil documenté sont marqués ⚠ Hypothèse à valider.

---

## Exactitude

### Q-EX-01 — Aucun fait inventé ✅

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Query sur snapshot ACTIVE après sync sans modification du workspace |
| **Environnement** | Mode local, processus JVM actif |
| **Réponse** | Données identiques aux fichiers source |
| **Mesure** | Divergence entre sortie MORPHEUS et source |
| **Seuil** | 0 divergence |
| **Vérification** | `MorpheusApiContractTest`, invariants ADR |
| **Propriétaire** | Architecte système |

### Q-EX-02 — PROPOSED ne fuit pas dans CURRENT ✅

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Snapshot BUILDING en parallèle d'un snapshot ACTIVE |
| **Environnement** | Mode local, SQLite WAL |
| **Réponse** | Query CURRENT ne retourne aucune donnée PROPOSED |
| **Mesure** | Résultat CURRENT ∩ données PROPOSED = ∅ |
| **Seuil** | 0 fuite |
| **Vérification** | `ChangeLifecycleTest`, isolation snapshot SQLite |
| **Propriétaire** | Architecte système |

### Q-EX-03 — Surface parity CLI/MCP/HTTP ⚠ Hypothèse à valider

| Champ | Valeur |
|-------|--------|
| **Stimulus** | La même requête est émise sur CLI, MCP et HTTP |
| **Environnement** | Même snapshot ACTIVE, même projet |
| **Réponse** | Les trois surfaces retournent des données sémantiquement équivalentes |
| **Mesure** | Différences sémantiques entre les trois réponses |
| **Seuil** | 0 différence sémantique |
| **Vérification** | `contracts/public-surfaces.tsv` ; tests de contrat croisés — à implémenter |
| **Propriétaire** | Architecte surfaces |

---

## Maintenabilité

### Q-MA-01 — Isolation des couches ArchUnit ✅

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Tentative d'ajout d'une dépendance domain → adapter |
| **Environnement** | `mvnw verify` |
| **Réponse** | Build échoue avec violation ArchUnit |
| **Mesure** | Nombre de violations ArchUnit dans `main` |
| **Seuil** | 0 |
| **Vérification** | `LayerDependencyTest` dans `morpheus-architecture-tests` |
| **Propriétaire** | CI |

### Q-MA-02 — Gate CI de milestone ✅

| Champ | Valeur |
|-------|--------|
| **Stimulus** | PR vers `main` avec changements fonctionnels |
| **Environnement** | GitHub Actions matrix ubuntu + windows |
| **Réponse** | Pipeline bloque la fusion si un test du gate échoue |
| **Mesure** | Taux de réussite gate CI sur `main` |
| **Seuil** | 100 % |
| **Vérification** | `.github/workflows/ci.yml` |
| **Propriétaire** | CI |

### Q-MA-03 — Durée de build CI ⚠ Hypothèse à valider

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Push sur une PR |
| **Environnement** | GitHub Actions |
| **Réponse** | Build complet terminé en moins de N minutes |
| **Mesure** | Durée totale du pipeline CI |
| **Seuil** | Non documenté — **à définir** |
| **Vérification** | Historique des runs GitHub Actions |
| **Propriétaire** | CI |

---

## Portabilité

### Q-PO-01 — Démarrage sans JDK ✅

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Installation depuis archive ZIP sur Windows sans JDK dans PATH |
| **Environnement** | Windows 10 ; archive portable jpackage |
| **Réponse** | `morpheus --version` répond correctement |
| **Mesure** | Code de sortie = 0 |
| **Seuil** | 100 % des installations portables |
| **Vérification** | `distribution/build-portable.ps1` ; test de smoke post-build |
| **Propriétaire** | Équipe distribution |

### Q-PO-02 — CI cross-platform ✅

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Build CI déclenché |
| **Environnement** | ubuntu-latest + windows-latest |
| **Réponse** | 0 test échouant sur chaque plateforme |
| **Mesure** | Nombre de tests échouant |
| **Seuil** | 0 |
| **Vérification** | `.github/workflows/ci.yml` |
| **Propriétaire** | CI |

### Q-PO-03 — Support macOS ⚠ Hypothèse à valider

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Utilisation sur macOS (Apple Silicon ou Intel) |
| **Environnement** | macOS ; JVM embarquée |
| **Réponse** | Fonctionnement identique à Linux |
| **Mesure** | Tests passants sur macos-latest |
| **Seuil** | 0 test échouant |
| **Vérification** | Ajout de `macos-latest` à la matrice CI — **non implémenté** |
| **Propriétaire** | CI |

---

## Extensibilité

### Q-EX-SDK-01 — Ajout de provider externe ✅ (partiel)

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Développeur tiers implémente le `morpheus-provider-sdk` |
| **Environnement** | Aucune modification des modules `domain` ou `application` |
| **Réponse** | Provider découvert et exposé via les trois surfaces |
| **Mesure** | Lignes modifiées dans `morpheus-domain` ou `morpheus-application` |
| **Seuil** | 0 |
| **Vérification** | `morpheus-provider-testkit` ; `morpheus-provider-reference` |
| **Propriétaire** | Architecte SDK |

---

## Résilience

### Q-RE-01 — MINOS indisponible ✅

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Timeout MCP STDIO vers MINOS (processus absent) |
| **Environnement** | Mode local |
| **Réponse** | Réponse structurée avec `codeContextAvailable=false` et `warning` ; pas de crash |
| **Mesure** | Nombre de réponses non structurées en mode dégradé |
| **Seuil** | 0 |
| **Vérification** | Tests `morpheus-integration-minos` ; `MinosIntegrationException` |
| **Propriétaire** | Architecte intégrations |

### Q-RE-02 — Crash pendant sync ✅

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Processus JVM tué pendant écriture d'un snapshot BUILDING |
| **Environnement** | SQLite WAL |
| **Réponse** | Snapshot ACTIVE précédent intact ; snapshot BUILDING absent ou en état FAILED |
| **Mesure** | Nombre de snapshots ACTIVE corrompus après crash |
| **Seuil** | 0 |
| **Vérification** | `SqliteConcurrencyHardeningTest` |
| **Propriétaire** | Architecte stockage |

---

## Performance

### Q-PE-01 — Latence de sync ⚠ Hypothèse à valider

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Sync d'un projet de taille standard (N fichiers) |
| **Environnement** | Mode local ; SQLite WAL ; SSD |
| **Réponse** | Sync terminée en moins de X secondes |
| **Mesure** | Durée de la commande `morpheus sync` |
| **Seuil** | **Non documenté** — à extraire des gates M19 dans `morpheus-architecture-tests/m19/` |
| **Vérification** | Gates M19 |
| **Propriétaire** | Architecte performance |

### Q-PE-02 — Latence de démarrage JVM ⚠ Hypothèse à valider

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Lancement de `morpheus --version` |
| **Environnement** | Distribution portable ; JVM embarquée |
| **Réponse** | Réponse en moins de X ms |
| **Mesure** | Temps entre lancement du processus et affichage de la version |
| **Seuil** | **Non documenté** — attendu < 2 s (hypothèse) |
| **Vérification** | Test de smoke post-distribution |
| **Propriétaire** | Équipe distribution |

---

## Sécurité

### Q-SE-01 — Isolation filesystem ⚠ Hypothèse à valider

| Champ | Valeur |
|-------|--------|
| **Stimulus** | Commande `morpheus sync` sur un projet dont le workspace est déclaré |
| **Environnement** | Mode local |
| **Réponse** | MORPHEUS ne lit que les fichiers dans le workspace déclaré |
| **Mesure** | Accès filesystem hors workspace |
| **Seuil** | 0 accès hors workspace |
| **Vérification** | Revue de code du `WorkspaceRootResolver` et des providers — **pas de test automatisé identifié** |
| **Propriétaire** | Architecte sécurité |
