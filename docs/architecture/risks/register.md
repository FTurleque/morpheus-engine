# Registre des risques et de la dette — MORPHEUS ENGINE

> Synthèse opérationnelle de [§11](../arc42/11-risques-dette.md).
> Baseline active : MORPHEUS 1.2.1 corrective — branche de développement post-audit.
> Dernière release publiée : `v1.2.0`.
> Mise à jour : **2026-08-13**.
>
> **P** = probabilité, **I** = impact, **E** = exposition = P × I ; échelle 1 à 3.

---

## Risques techniques

| ID | Risque | P | I | E | Mitigation actuelle | Révision |
|----|--------|:-:|:-:|:-:|---------------------|----------|
| RT-01 | Concurrence SQLite si le mode remote devient fortement multi-écrivain | 2 | 3 | **6** | `journal_mode=PERSIST`, busy timeout, transactions bornées, une connexion physique scopée par opération API/Query/CLI, index d'unicité et backups | Si le profil d'usage remote évolue |
| RT-02 | Rollback applicatif après migration de schéma | 2 | 3 | **6** | Migrations forward-only, checksums, refus des schémas futurs, backup/restore offline | À chaque évolution de schéma |
| RT-10 | Promotion stable contournant les gates tant que `main` n'a pas de ruleset administrateur | 2 | 3 | **6** | PR de promotion maintenue en draft ; M21 et SCA existent dans le dépôt ; réglage administrateur tracé dans #154 | Jusqu'à clôture de #154 |
| RT-03 | Limites de `jdk.httpserver` sous forte charge | 2 | 2 | **4** | Concurrence remote bornée, inventaires filesystem bornés ; mesurer avant substitution | Lors de load tests représentatifs |
| RT-04 | Breaking change MCP SDK / clients MCP | 2 | 2 | **4** | Version épinglée, tests de contrat, cleanup fail-closed à l'initialisation, configuration native conservatrice | À chaque upgrade MCP |
| RT-05 | Provider externe malformé, bloquant ou non fiable | 2 | 2 | **4** | Discovery metadata-only sans symlink, activation explicite, SHA-256 obligatoire en remote, staging vérifié, budgets d'ingestion ; un probe remote garde son slot jusqu'à sa vraie fin | À chaque évolution du Provider SDK |
| RT-06 | Diagnostic runtime limité par le logging silencieux | 2 | 2 | **4** | Health/metrics, erreurs structurées, validation ; préserver stdout MCP | Permanent |
| RT-09 | Drift documentaire entre sources historiques et HEAD | 2 | 2 | **4** | Hiérarchie des sources, séparation release publiée `1.2.0` / baseline active `1.2.1`, réconciliation documentaire | À chaque release/hardening |
| RT-07 | Auth remote sans SSO/LDAP | 1 | 2 | **2** | Bearer auth + RBAC, mutations inter-processus sérialisées, live reload, audit secret-free roulant borné à 512 événements | Si besoin entreprise démontré |
| RT-08 | macOS non qualifié | 2 | 1 | **2** | Support officiel Windows + Linux uniquement dans la baseline actuelle | Si support macOS décidé |

---

## Dette technique / documentaire

| ID | Dette | Domaine | Priorité | Action |
|----|-------|---------|----------|--------|
| DT-01 | Documents historiques encore présentés avec des baselines C0/M20/M27 | Documentation | **Haute** | Les qualifier comme historiques ou les réconcilier dans des PR dédiées sans falsifier les preuves passées |
| DT-06 | `main` ne possède pas encore le ruleset/required checks décidé pour la branche stable | Gouvernance GitHub | **Haute** | Appliquer #154 : PR obligatoire vers `main`, **0 approbation requise**, checks exact-head M21 + sécurité, blocage force-push/suppression |
| DT-07 | SonarCloud peut encore accepter 0 % de couverture sur le nouveau code | Qualité | **Haute** | Configurer un Quality Gate differential non nul selon #154 ; conserver le ratchet JaCoCo global séparé |
| DT-08 | Alertes de vulnérabilité Dependabot non activées côté dépôt | Supply chain | **Moyenne** | Activer les alertes administrateur ; `.github/dependabot.yml` et `MORPHEUS Security` assurent déjà update PRs + scan OWASP |
| DT-03 | Seuils de performance M19 peu visibles depuis la documentation d'architecture | Qualité | **Moyenne** | Relier les scénarios qualité aux tests/gates autoritatifs |
| DT-04 | SQLite reste l'unique backend persistant | Architecture | **Faible à moyenne** | N'engager un backend alternatif qu'après besoin et ADR dédiés |
| DT-05 | Distribution macOS absente | Distribution | **Faible** | Décision produit avant ajout du packaging/CI |

### Décision explicite pour `develop`

Le projet est actuellement maintenu par un seul développeur. `develop` reste donc **volontairement non protégée** afin d'éviter une friction administrative sans bénéfice collaboratif immédiat. Ce choix n'est pas traité comme une dette bloquante tant que le projet reste mono-développeur.

La défense en profondeur est conservée : `MORPHEUS CI` s'exécute également sur les pushes `develop`. Lorsque plusieurs contributeurs travailleront simultanément sur le dépôt, la protection de `develop`, les approbations et éventuellement CODEOWNERS/merge queue devront être réévalués.

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
| Ratchets de présence M21 obsolètes | floors `698` tests / `250` architecture + couverture scriptée `47% / 40%` |
| SCA uniquement manuel | workflow `MORPHEUS Security` sur PR/push `main`, hebdomadaire et manuel ; Dependabot update config ajoutée |

La publication de snapshot et la baseline d'inventaire restent physiquement deux transactions distinctes. La mitigation est volontairement une **réconciliation bornée et fail-safe**, pas une prétendue transaction distribuée atomique.

---

## État CI actif

Le workflow public exact-head utilise :

```text
Ubuntu  -> bash ./scripts/validate-m21.sh 1.2.1
Windows -> scripts\validate.cmd m21 -Version 1.2.1
```

M21 s'exécute sur les pull requests ainsi que sur les pushes `main` et `develop`. Les actions tierces du workflow canonique sont référencées par SHA immuable et le Maven Wrapper vérifie le SHA-256 de la distribution Maven.

Ratchets actifs :

```text
Surefire total      >= 698
architecture        >= 250
line coverage       >= 47%
branch coverage     >= 40%
```

Le workflow `MORPHEUS Security` exécute OWASP Dependency-Check (CVSS >= 7 bloquant) sur les PR ciblant `main`, les pushes `main`, chaque lundi et sur demande. Ces workflows rendent les contrôles exécutables, mais **ne remplacent pas un ruleset `main`** tant que GitHub n'empêche pas explicitement de les contourner.

Au 2026-08-13, l'application du ruleset `main`, du Quality Gate SonarCloud differential et des alertes de vulnérabilité Dependabot reste un réglage administrateur suivi dans **#154**.

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
3. Ne pas inventer ADR-0097/0098 ou une roadmap technique à partir d'un risque : créer l'ADR seulement après besoin démontré.
4. Ne pas remplacer automatiquement le gate CI M21 par le numéro du dernier milestone fonctionnel.
5. Toute évolution sécurité, stockage, remote ou packaging doit conserver des preuves exact-head reproductibles.
6. Une promotion stable ne doit pas considérer #154 comme résolue tant que les réglages administrateur `main`/SonarCloud/alertes n'ont pas été effectivement appliqués et vérifiés.
7. La protection de `develop` est réévaluée lors du passage à un workflow réellement collaboratif ; elle n'est pas un prérequis de promotion dans le contexte mono-développeur actuel.
