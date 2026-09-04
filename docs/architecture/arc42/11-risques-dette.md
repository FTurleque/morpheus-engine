# §11 — Risques et dette technique

> **Sources actives** : code et build du HEAD `develop`, ADR, preuves R3/D2,
> pipeline exact-head et registre détaillé [`../risks/register.md`](../risks/register.md).
>
> Échelle : probabilité et impact de 1 (faible) à 3 (élevé) ; exposition = P × I.

---

## 11.1 Risques techniques

| ID | Risque | P | I | E | Mitigation actuelle |
|----|--------|:-:|:-:|:-:|---------------------|
| RT-01 | Concurrence SQLite accrue si le mode remote devient fortement multi-écrivain | 2 | 3 | **6** | `journal_mode=PERSIST`, busy timeout, transactions bornées, une connexion physique scopée par opération, leases, réservation atomique des séquences de version, backups ; observabilité de contention (`sqlite.contention.*`, `sqlite.transaction.duration`) et tests de stress multi-écrivain ; substitution possible derrière les ports si besoin prouvé |
| RT-02 | Retour à un ancien binaire après migration de schéma non rétrocompatible | 2 | 3 | **6** | Migrations forward-only explicites, checksums, refus des schémas futurs, backup avant opérations sensibles, restore offline |
| RT-03 | `jdk.httpserver` peut devenir limitant sous charge serveur importante | 2 | 2 | **4** | Mode remote optionnel et borné ; harnais de charge reproductible (`MorpheusRemoteLoadProfileTest`) et critères objectifs de remplacement documentés — mesurer avant toute substitution et formaliser un ADR si nécessaire |
| RT-04 | Évolution du MCP SDK 2.0.1 ou de ses contrats clients | 2 | 2 | **4** | Version épinglée, tests de contrat, MCP STDIO borné, diagnostics redacted et configuration conservatrice |
| RT-05 | Plugin provider externe malformé, trop volumineux ou non fiable | 2 | 2 | **4** | Discovery metadata-only sans symlink avec revalidation d'identité avant/après lecture, activation explicite, SHA-256 obligatoire en remote, staging vérifié, budgets d'ingestion et environnement enfant minimisé |
| RT-06 | Diagnostic runtime limité par les choix de logging silencieux compatibles MCP | 2 | 2 | **4** | Health/metrics, erreurs structurées, redaction des diagnostics peer et preuves de validation ; toute évolution doit préserver stdout MCP |
| RT-07 | Baseline remote limitée à Bearer auth / RBAC, sans IAM entreprise | 1 | 2 | **2** | Périmètre explicitement documenté ; mutations inter-processus sérialisées, live reload, audit secret-free roulant borné ; mot de passe TLS résolu tardivement en `char[]` et jamais retenu dans les options de lancement ; SSO/LDAP seulement après besoin et ADR dédiés |
| RT-08 | macOS non qualifié dans la baseline de distribution | 2 | 1 | **2** | Windows + Linux sont les plateformes qualifiées ; lane CI advisory `macos-smoke` (observation, pas qualification) ; ajouter macOS uniquement si support produit décidé |
| RT-09 | Drift entre documents historiques et HEAD actuel | 2 | 2 | **4** | Sources de vérité hiérarchisées ; réconciliation documentaire et contrats d'architecture sur les invariants CI |
| RT-12 | Peer MCP externe MINOS/NEXUS compromis | 2 | 2 | **4** | JAR optionnel/pinnable, environnement hérité réduit à une allowlist, descendants observés et terminés, frames/queues bornées, stderr et exceptions peer redacted ; la frontière n'est pas une sandbox OS |

Les anciens risques de gouvernance liés à l'absence de protection de `main`/`develop` sont résolus : le ruleset **Protect main & develop** impose les PR, les checks exact-head requis, la résolution des conversations et interdit suppression/non-fast-forward sans bypass.

---

## 11.2 Dette technique / documentaire

| ID | Dette | Domaine | Priorité | Traitement |
|----|-------|---------|----------|------------|
| DT-01 | Certains documents historiques restent ancrés sur C0/M20/M27 alors que la release stable est 1.2.0 et M28 est livré | Documentation | **Haute** | Les marquer comme historiques ou les réconcilier lorsqu'ils sont promus comme documentation active |
| DT-03 | Le suivi des seuils de performance M19 est difficile à lire depuis la documentation d'architecture | Qualité | **Moyenne** | Maintenir les scénarios qualité et pointer vers les tests/gates autoritatifs plutôt que dupliquer les valeurs |
| DT-04 | Aucun backend persistant alternatif à SQLite n'est implémenté | Architecture | **Faible à moyenne** | Ne pas pré-déclarer une solution ; créer un ADR seulement si un besoin réel de substitution apparaît |
| DT-05 | Pas de distribution macOS qualifiée | Distribution | **Faible** | Décision produit préalable avant investissement packaging ; la lane smoke n'en produit aucun |
| DT-07 | Quality Gate SonarCloud potentiellement moins strict que le gate repository sur le nouveau code | Qualité externe | **Moyenne** | Le repository impose indépendamment `>= 80%` changed-line et `>= 70%` changed-branch coverage ; vérifier le réglage SonarCloud sur sa propre plateforme |
| DT-08 | État des alertes Dependabot / Secret Scanning non vérifiable par le connecteur | Supply chain | **Moyenne** | Vérifier/activer les réglages administrateur ; le dépôt fournit indépendamment Dependabot, OWASP Dependency-Check et CodeQL versionné |
| DT-10 | Couverture historique globale encore modeste malgré un changed-code gate strict | Qualité | **Moyenne** | Ratchets M21 actifs à `1300 / 335 / 54,5% / 47,7%` ; ne relever qu'après nouvelle preuve exact-head reproductible sur les deux plateformes |
| DT-11 | Nouveau workflow de release attestée pas encore qualifié par une vraie release publiée | Release | **Moyenne** | Valider l'enchaînement tag -> Linux/Windows -> attestations -> assets -> GitHub Release lors de la prochaine vraie release `v1.2.1+` ; suivi #185 |
| DT-12 | Identités remote historiques à trois champs sans expiration | Sécurité remote | **Faible à moyenne** | Compatibilité contractuelle verrouillée par test ; `server identity migrate-legacy` donne une échéance explicite sans rotation de token ; retirer le format à trois champs reste une évolution incompatible, pas un patch 1.2.1 |

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
Surefire total       >= 1300
architecture         >= 335
line coverage        >= 54.5%
branch coverage      >= 47.7%
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
