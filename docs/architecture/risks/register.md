# Registre des risques et de la dette — MORPHEUS ENGINE

> Synthèse opérationnelle de [§11](../arc42/11-risques-dette.md).
> Baseline : MORPHEUS 1.2.0 — `develop` post-D2.
> Mise à jour : **2026-08-11**.
>
> **P** = probabilité, **I** = impact, **E** = exposition = P × I ; échelle 1 à 3.

---

## Risques techniques

| ID | Risque | P | I | E | Mitigation actuelle | Révision |
|----|--------|:-:|:-:|:-:|---------------------|----------|
| RT-01 | Concurrence SQLite si le mode remote devient fortement multi-écrivain | 2 | 3 | **6** | WAL, transactions bornées, tests de concurrence, backups | Si le profil d'usage remote évolue |
| RT-02 | Rollback applicatif après migration de schéma | 2 | 3 | **6** | Migrations forward-only, checksums, backup/restore offline | À chaque évolution de schéma |
| RT-03 | Limites de `jdk.httpserver` sous forte charge | 2 | 2 | **4** | Mesurer avant substitution ; mode remote borné | Lors de load tests représentatifs |
| RT-04 | Breaking change MCP SDK / clients MCP | 2 | 2 | **4** | Version épinglée, tests de contrat, configuration native conservatrice | À chaque upgrade MCP |
| RT-05 | Provider externe malformé ou non fiable | 2 | 2 | **4** | Activation explicite, JAR integrity, budgets d'ingestion, testkit | À chaque évolution du Provider SDK |
| RT-06 | Diagnostic runtime limité par le logging silencieux | 2 | 2 | **4** | Health/metrics, erreurs structurées, validation ; préserver stdout MCP | Permanent |
| RT-09 | Drift documentaire entre sources historiques et HEAD | 2 | 2 | **4** | Hiérarchie des sources et réconciliation documentaire | À chaque release/hardening |
| RT-07 | Auth remote sans SSO/LDAP | 1 | 2 | **2** | Bearer auth + RBAC explicitement bornés | Si besoin entreprise démontré |
| RT-08 | macOS non qualifié | 2 | 1 | **2** | Support officiel Windows + Linux uniquement dans la baseline actuelle | Si support macOS décidé |

---

## Dette technique / documentaire

| ID | Dette | Domaine | Priorité | Action |
|----|-------|---------|----------|--------|
| DT-01 | Documents historiques encore présentés avec des baselines C0/M20/M27 | Documentation | **Haute** | Les qualifier comme historiques ou les réconcilier dans des PR dédiées |
| DT-02 | `docs/adr/README.md` n'indexe pas encore ADR-0096 | ADR | **Moyenne** | Réconcilier l'index sans modifier l'ADR accepté |
| DT-03 | Seuils de performance M19 peu visibles depuis la documentation d'architecture | Qualité | **Moyenne** | Relier les scénarios qualité aux tests/gates autoritatifs |
| DT-04 | SQLite reste l'unique backend persistant | Architecture | **Faible à moyenne** | N'engager un backend alternatif qu'après besoin et ADR dédiés |
| DT-05 | Distribution macOS absente | Distribution | **Faible** | Décision produit avant ajout du packaging/CI |

---

## État CI à ne pas interpréter comme dette

Le workflow public exact-head utilise actuellement :

```text
Ubuntu  -> bash ./scripts/validate-m21.sh 1.2.0
Windows -> scripts\validate.cmd m21 -Version 1.2.0
```

C'est l'état attendu de la baseline d'intégrité publique. Le nom **M21** désigne
le gate durable d'intégrité/surface-convergence ; il ne signifie pas que les
fonctionnalités M22 à M28 sont absentes ou non qualifiées. Les gates spécialisés
restent dans leurs preuves de milestones.

---

## Incohérences documentaires connues

| ID | Incohérence | Gravité | Traitement recommandé |
|----|-------------|---------|-----------------------|
| IC-01 | `docs/adr/README.md` s'arrête à ADR-0095 alors qu'ADR-0096 est présent et accepté | **Moyenne** | Mettre à jour l'index ADR dans un changement dédié |
| IC-02 | `docs/architecture/overview.md` conserve son statut de proposition C0 | **Faible si qualifié comme historique** | Ne pas le promouvoir artificiellement ; utiliser les ADR/code/validations récents comme sources actives |
| IC-03 | Certains documents développeur restent ancrés sur la baseline 1.0.0/M20 | **Moyenne** | Réconciliation documentaire progressive, sans réécrire l'historique des preuves |

---

## Principes de traitement

1. Corriger immédiatement toute documentation active qui affirme une version,
   un provider, un gate ou une architecture faux.
2. Conserver les documents de validation historiques immuables lorsque leur
   rôle est de prouver un milestone passé.
3. Ne pas inventer ADR-0097/0098 ou une roadmap technique à partir d'un risque :
   créer l'ADR seulement après besoin démontré.
4. Ne pas remplacer automatiquement le gate CI M21 par le numéro du dernier
   milestone fonctionnel.
5. Toute évolution sécurité, stockage, remote ou packaging doit conserver des
   preuves exact-head reproductibles.
