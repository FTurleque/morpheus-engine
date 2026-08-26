# Registre des risques et de la dette — MORPHEUS ENGINE

> Synthèse opérationnelle de [§11](../arc42/11-risques-dette.md).
> Baseline active : MORPHEUS 1.2.1 corrective — branche de développement post-audit.
> Dernière release publiée : `v1.2.0`.
> Mise à jour : **2026-08-26**.
>
> **P** = probabilité, **I** = impact, **E** = exposition = P × I ; échelle 1 à 3.

---

## Risques techniques

| ID | Risque | P | I | E | Mitigation actuelle | Révision |
|----|--------|:-:|:-:|:-:|---------------------|----------|
| RT-01 | Concurrence SQLite si le mode remote devient fortement multi-écrivain | 2 | 3 | **6** | `journal_mode=PERSIST`, busy timeout, transactions bornées, une connexion physique scopée par opération API/Query/CLI, index d'unicité et backups | Si le profil d'usage remote évolue |
| RT-02 | Rollback applicatif après migration de schéma | 2 | 3 | **6** | Migrations forward-only, checksums, refus des schémas futurs, backup/restore offline | À chaque évolution de schéma |
| RT-10 | Promotion stable contournant les gates tant que `main` n'a pas de ruleset administrateur complet | 2 | 3 | **6** | M21, Dependency-Check et CodeQL existent dans le dépôt ; réglage administrateur suivi dans #154 | Jusqu'à clôture de #154 |
| RT-11 | Push direct sur `develop` contournant les checks pré-merge | 2 | 3 | **6** | CI de défense en profondeur sur push ; protection/ruleset obligatoire désormais décidée et suivie dans #166 | Jusqu'à clôture de #166 |
| RT-03 | Limites de `jdk.httpserver` sous forte charge | 2 | 2 | **4** | Concurrence remote bornée, inventaires filesystem bornés ; mesurer avant substitution | Lors de load tests représentatifs |
| RT-04 | Breaking change MCP SDK / clients MCP | 2 | 2 | **4** | Version épinglée, tests de contrat, budgets de frames/queues, cancellation serveur et cleanup fail-closed | À chaque upgrade MCP |
| RT-05 | Provider externe malformé, bloquant ou non fiable | 2 | 2 | **4** | Discovery metadata-only sans symlink, activation explicite, SHA-256 obligatoire en remote, staging vérifié, environnement enfant minimisé et descendants retenus ; un probe remote garde son slot jusqu'à sa vraie fin | À chaque évolution du Provider SDK |
| RT-12 | Peer MCP externe MINOS/NEXUS compromis | 2 | 2 | **4** | JAR optionnel/pinnable, environnement hérité réduit à une allowlist de lancement, variables explicites seulement, descendants observés retenus et terminés au shutdown ; la frontière n'est pas une sandbox OS | À chaque évolution du transport MCP |
| RT-06 | Diagnostic runtime limité par le logging silencieux | 2 | 2 | **4** | Health/metrics, erreurs structurées, validation ; préserver stdout MCP | Permanent |
| RT-09 | Drift documentaire entre sources historiques et HEAD | 2 | 2 | **4** | Hiérarchie des sources, séparation release publiée `1.2.0` / baseline active `1.2.1`, preuves historiques non réécrites et guides actifs réconciliés | À chaque release/hardening |
| RT-07 | Auth remote sans SSO/LDAP | 1 | 2 | **2** | Bearer auth + RBAC, mutations inter-processus sérialisées, live reload, audit secret-free roulant borné à 512 événements | Si besoin entreprise démontré |
| RT-08 | macOS non qualifié | 2 | 1 | **2** | Support officiel Windows + Linux uniquement dans la baseline actuelle | Si support macOS décidé |

---

## Dette technique / documentaire

| ID | Dette | Domaine | Priorité | Action |
|----|-------|---------|----------|--------|
| DT-01 | Documents historiques encore présentés avec des baselines C0/M20/M27 | Documentation | **Haute** | Les qualifier comme historiques ou les réconcilier dans des PR dédiées sans falsifier les preuves passées |
| DT-06 | `main` ne possède pas encore tous les rulesets/required checks décidés pour la branche stable | Gouvernance GitHub | **Haute** | Appliquer #154 : PR obligatoire vers `main`, checks exact-head M21 + sécurité/SAST, blocage force-push/suppression |
| DT-09 | `develop` reste non protégée malgré la décision de durcissement du 22/08 | Gouvernance GitHub | **Haute** | Appliquer #166 : PR obligatoire, checks Linux/Windows, architecture, diff coverage, sécurité, conversations résolues, pas de force-push/suppression |
| DT-07 | SonarCloud peut encore accepter 0 % de couverture sur le nouveau code selon le réglage administrateur constaté | Qualité | **Haute** | Configurer un Quality Gate differential non nul selon #154 ; conserver le ratchet JaCoCo global et le changed-line gate séparés |
| DT-08 | État des alertes Dependabot / Code Scanning / Secret Scanning non vérifiable par le connecteur | Supply chain | **Moyenne** | Vérifier/activer les réglages administrateur selon #154 ; le dépôt fournit Dependabot, OWASP Dependency-Check et désormais CodeQL versionné |
| DT-03 | Seuils de performance M19 peu visibles depuis la documentation d'architecture | Qualité | **Moyenne** | Relier les scénarios qualité aux tests/gates autoritatifs |
| DT-04 | SQLite reste l'unique backend persistant | Architecture | **Faible à moyenne** | N'engager un backend alternatif qu'après besoin et ADR dédiés |
| DT-05 | Distribution macOS absente | Distribution | **Faible** | Décision produit avant ajout du packaging/CI |

### Décision explicite pour `develop`

Depuis l'audit du **22 août 2026**, la non-protection de `develop` n'est plus une politique cible. L'issue **#166** formalise désormais l'exigence d'un ruleset / branch protection : pull request obligatoire, checks Linux et Windows, architecture M21, changed-line coverage, Dependency-Check, résolution des conversations, interdiction du force-push et de la suppression.

Tant que ce réglage administrateur n'est pas appliqué, `MORPHEUS CI`, `MORPHEUS Security` et `MORPHEUS CodeQL` sur `develop` restent une défense en profondeur **après** le push et ne remplacent pas la prévention offerte par un ruleset GitHub.

---

## Correctifs issus de l'audit du 26/08/2026

| Constat | Traitement dans la baseline corrective |
|---|---|
| Environnement MORPHEUS intégralement hérité par MINOS/NEXUS | `BoundedStdioClientTransport` conserve uniquement une allowlist de lancement puis applique les variables explicitement configurées pour le peer |
| Descendant MCP pouvant survivre après sortie du parent | observation périodique des `ProcessHandle`, rétention des descendants vus puis cleanup forcé même si le parent a déjà disparu |
| Secret NVD disponible sur le chemin PR | `security.yml` sépare les événements de confiance du chemin `pull_request`; l'update PR n'injecte aucun repository secret |
| Absence de SAST versionné | `.github/workflows/codeql.yml` ajouté, actions CodeQL pinnées par SHA, Java `security-extended` |
| Ratchets devenus trop permissifs | M21 relevé à `820 / 258 / 50% / 42%`; changed-line coverage reste `>= 80%`; présence D2 relevée à `820 / 258` |
| Manifeste update distant en HTTP | `UpdateDiscoveryService` accepte `file:` et `https:` uniquement ; `http:` est refusé avant I/O |
| Registre des risques en dérive | présent document réconcilié avec #154, #166 et la baseline CI exacte |

La réduction d'environnement et le suivi des descendants sont des mesures de confinement de lifecycle et de secrets ; ils ne transforment pas un JAR externe explicitement configuré en code non fiable sandboxé. MINOS/NEXUS et les plugins exécutables restent sous le même compte OS que MORPHEUS.

---

## Correctifs issus de l'audit post-#153

Les constats techniques démontrés le 13 août 2026 ont reçu des mitigations exécutables dans la baseline corrective 1.2.1 :

| Constat | Traitement actif |
|---|---|
| Sync déjà commité reclassé en échec | relecture du commit durable, un retry idempotent borné, puis `BASELINE_INCONSISTENT` si publication potentiellement déjà effective |
| `SCAN_INCOMPLETE` écrasé par `EXECUTION_FAILED` | cause spécifique conservée |
| `Error` hors rollback SQLite | rollback best-effort sur `Error`, erreur primaire réémise, cleanup/rollback en suppressed |
| Audit d'identités pouvant remplir 256 KiB | fenêtre roulante atomique des 512 derniers événements sans secret |
| Discovery plugin suivant les symlinks | `NOFOLLOW_LINKS`, répertoire et JAR symboliques refusés |
| Probe plugin retournant 504 sans cancellation garantie | aucune deadline façade sur le probe tiers ; slot remote détenu jusqu'à la fin réelle |
| Query/CLI ouvrant plusieurs connexions physiques | `SqliteConnectionScope` partagé par opération/runtime |
| Version 1.2.0 réutilisée après release publiée | reactor, gates et builders actifs en `1.2.1`; tag/release `v1.2.0` historique inchangé |
| SCA uniquement manuel | workflow `MORPHEUS Security` + Dependabot |

La publication de snapshot et la baseline d'inventaire restent physiquement deux transactions distinctes. La mitigation est volontairement une **réconciliation bornée et fail-safe**, pas une prétendue transaction distribuée atomique.

---

## État CI actif

Le workflow public exact-head utilise :

```text
Ubuntu  -> bash ./scripts/validate-m21.sh 1.2.1
Windows -> scripts\validate.cmd m21 -Version 1.2.1
```

M21 s'exécute sur les pull requests ainsi que sur les pushes `main` et `develop`. Les actions tierces des workflows actifs sont référencées par SHA immuable et le Maven Wrapper vérifie le SHA-256 de la distribution Maven.

Ratchets actifs :

```text
Surefire total      >= 820
architecture        >= 258
line coverage       >= 50%
branch coverage     >= 42%
changed-line        >= 80%
```

Baseline exact-head ayant justifié ces ratchets :

```text
tests               824
architecture        258
line coverage       50.3630%
branch coverage     42.7823%
changed-line        87.63%
reactor             18/18
```

`MORPHEUS Security` exécute OWASP Dependency-Check (CVSS >= 7 bloquant) sur PR/push `main` et `develop`, hebdomadairement et sur demande. Les PR n'obtiennent pas la clé NVD depuis ce workflow. `MORPHEUS CodeQL` exécute un SAST Java versionné avec `security-extended`.

Ces workflows rendent les contrôles exécutables mais **ne remplacent pas** les rulesets administrateur suivis dans #154 et #166.

Le nom **M21** désigne le gate durable d'intégrité/surface-convergence ; il ne signifie pas que les fonctionnalités M22 à M28 sont absentes ou non qualifiées. Les workflows M9 à M12 conservés dans `.github/workflows` sont historiques/manuels et leurs actions sont elles aussi épinglées par SHA.

---

## Incohérences documentaires connues

| ID | Incohérence | Gravité | Traitement recommandé |
|----|-------------|---------|-----------------------|
| IC-02 | `docs/architecture/overview.md` conserve son statut de proposition C0 | **Faible si qualifié comme historique** | Ne pas le promouvoir artificiellement ; utiliser les ADR/code/validations récents comme sources actives |
| IC-03 | Certains documents de milestone restent ancrés sur leurs versions historiques | **Faible si explicitement historiques** | Ne pas modifier leurs preuves ; maintenir les guides actifs séparément |

---

## Principes de traitement

1. Corriger immédiatement toute documentation active qui affirme une version, un provider, un gate ou une architecture faux.
2. Conserver les documents de validation historiques immuables lorsque leur rôle est de prouver un milestone passé.
3. Ne pas inventer un ADR à partir d'un risque : créer l'ADR seulement après besoin démontré.
4. Ne pas remplacer automatiquement le gate CI M21 par le numéro du dernier milestone fonctionnel.
5. Toute évolution sécurité, stockage, remote ou packaging doit conserver des preuves exact-head reproductibles.
6. Une promotion stable ne doit pas considérer #154 comme résolue tant que les réglages administrateur `main`/SonarCloud/alertes/scanning n'ont pas été effectivement appliqués et vérifiés.
7. `develop` ne doit pas considérer #166 comme résolue tant que le ruleset requis n'est pas effectivement appliqué et vérifié.
