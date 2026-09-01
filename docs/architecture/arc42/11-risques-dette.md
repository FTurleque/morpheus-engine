# §11 — Risques et dette technique

> **Sources actives** : code et build du HEAD `develop`, ADR, preuves R3/D2,
> pipeline exact-head et registre détaillé [`../risks/register.md`](../risks/register.md).
>
> Échelle : probabilité et impact de 1 (faible) à 3 (élevé) ; exposition = P × I.

---

## 11.1 Risques techniques

| ID | Risque | P | I | E | Mitigation actuelle |
|----|--------|:-:|:-:|:-:|---------------------|
| RT-01 | Concurrence SQLite accrue si le mode remote devient fortement multi-écrivain | 2 | 3 | **6** | `journal_mode=PERSIST`, busy timeout, transactions bornées, leases et backups ; substitution possible derrière les ports si besoin prouvé |
| RT-02 | Retour à un ancien binaire après migration de schéma non rétrocompatible | 2 | 3 | **6** | Migrations forward-only explicites, checksums, refus des schémas futurs, backup avant opérations sensibles, restore offline |
| RT-03 | `jdk.httpserver` peut devenir limitant sous charge serveur importante | 2 | 2 | **4** | Mode remote optionnel et borné ; mesurer avant toute substitution et formaliser un ADR si nécessaire |
| RT-04 | Évolution du MCP SDK 2.0.1 ou de ses contrats clients | 2 | 2 | **4** | Version épinglée, tests de contrat, MCP STDIO borné, diagnostics redacted et configuration conservatrice |
| RT-05 | Plugin provider externe malformé, trop volumineux ou non fiable | 2 | 2 | **4** | Activation explicite, budgets d'ingestion, vérification d'intégrité JAR, testkit et isolation des frontières |
| RT-06 | Diagnostic runtime limité par les choix de logging silencieux compatibles MCP | 2 | 2 | **4** | Health/metrics, erreurs structurées, redaction des diagnostics peer et preuves de validation ; toute évolution doit préserver stdout MCP |
| RT-07 | Baseline remote limitée à Bearer auth / RBAC, sans IAM entreprise | 1 | 2 | **2** | Périmètre explicitement documenté ; SSO/LDAP seulement après besoin et ADR dédiés |
| RT-08 | macOS non qualifié dans la baseline de distribution | 2 | 1 | **2** | Windows + Linux sont les plateformes qualifiées ; ajouter macOS uniquement si support produit décidé |
| RT-09 | Drift entre documents historiques et HEAD actuel | 2 | 2 | **4** | Sources de vérité hiérarchisées ; réconciliation documentaire et contrats d'architecture sur les invariants CI |

Les anciens risques de gouvernance liés à l'absence de protection de `main`/`develop` sont résolus : le ruleset **Protect main & develop** impose les PR, les checks exact-head requis, la résolution des conversations et interdit suppression/non-fast-forward sans bypass.

---

## 11.2 Dette technique / documentaire

| ID | Dette | Domaine | Priorité | Traitement |
|----|-------|---------|----------|------------|
| DT-01 | Certains documents historiques restent ancrés sur C0/M20/M27 alors que la release stable est 1.2.0 et M28 est livré | Documentation | **Haute** | Les marquer comme historiques ou les réconcilier lorsqu'ils sont promus comme documentation active |
| DT-03 | Le suivi des seuils de performance M19 est difficile à lire depuis la documentation d'architecture | Qualité | **Moyenne** | Maintenir les scénarios qualité et pointer vers les tests/gates autoritatifs plutôt que dupliquer les valeurs |
| DT-04 | Aucun backend persistant alternatif à SQLite n'est implémenté | Architecture | **Faible à moyenne** | Ne pas pré-déclarer une solution ; créer un ADR seulement si un besoin réel de substitution apparaît |
| DT-05 | Pas de distribution macOS qualifiée | Distribution | **Faible** | Décision produit préalable avant investissement CI/packaging |
| DT-06 | Réglages externes SonarCloud / alertes de sécurité non tous vérifiables depuis le connecteur | Gouvernance externe | **Moyenne** | Conserver les gates repository indépendants et vérifier les réglages sur les plateformes concernées |

L'ancien `DT-02` relatif à l'absence d'ADR-0096 dans l'index ADR est résolu : `docs/adr/README.md` référence désormais ADR-0096 et sa qualification M28.

---

## 11.3 Points explicitement **non** considérés comme des incohérences

### Gate CI M21

Le workflow public exact-head exécute actuellement le **gate M21 avec la
version produit 1.2.1** sur Ubuntu et Windows. Ce choix est intentionnel : M21
sert de gate d'intégrité/surface-convergence durable. Les gates M22 à M28
restent des preuves de milestones spécialisés et ne doivent pas remplacer
mécaniquement M21 dans `ci.yml`.

La baseline active du gate est :

```text
Surefire total       >= 1000
architecture         >= 300
line coverage        >= 52.0%
branch coverage      >= 45.0%
changed-line         >= 80%
changed-branch       >= 70%
```

Le workflow de sécurité rafraîchit sa base OWASP de confiance **quotidiennement** et refuse sur PR un cache âgé de plus de 72 h. Le workflow de release produit une attestation GitHub de provenance sur les tags `vX.Y.Z` atteignables depuis `main` et refuse d'écraser une release existante.

Par conséquent :

```text
ci.yml -> validate-m21 1.2.1   == état attendu
M28 livré                         != obligation d'appeler validate-m28 en CI
D2 qualification locale           != nouveau numéro de gate produit
```

### `docs/architecture/overview.md`

Ce document date de la phase de conception C0. Son statut historique ne doit
pas être changé artificiellement en « M27 » ou « M28 ». Lorsqu'une documentation
active le cite, elle doit le qualifier comme source historique et laisser les
ADR, le code et les validations plus récentes primer.

---

## 11.4 Règle d'évolution

Les risques futurs (backend alternatif, IAM entreprise, nouveau framework HTTP,
macOS, changement de build) ne reçoivent pas à l'avance un numéro d'ADR. La
séquence attendue est :

```text
besoin démontré
  -> options comparées
  -> ADR créé
  -> implémentation
  -> tests / preuves
  -> acceptation
```

Cette règle évite de transformer des hypothèses en roadmap implicite.
