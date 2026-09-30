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
| RT-08 | macOS non qualifié dans la baseline de distribution | 2 | 1 | **2** | Windows + Linux sont les plateformes qualifiées ; lane advisory `macos-smoke` de `nightly.yml` sur cadence quotidienne bornée (observation, pas qualification) ; ajouter macOS uniquement si support produit décidé |
| RT-09 | Drift entre documents historiques et HEAD actuel | 2 | 2 | **4** | Sources de vérité hiérarchisées ; réconciliation documentaire et contrats d'architecture sur les invariants CI |
| RT-12 | Peer MCP externe MINOS/NEXUS compromis | 2 | 2 | **4** | JAR optionnel/pinnable, environnement hérité réduit à une allowlist, descendants observés et terminés, frames/queues bornées, stderr et exceptions peer redacted ; la frontière n'est pas une sandbox OS |

Les anciens risques de gouvernance liés à l'absence de protection de `main`/`develop` sont résolus : le ruleset **Protect main & develop** impose les PR, les checks exact-head requis, la résolution des conversations et interdit suppression/non-fast-forward sans bypass.

---

## 11.2 Dette technique / documentaire

| ID | Dette | Domaine | Priorité | Traitement |
|----|-------|---------|----------|------------|
| DT-10 | Couverture historique globale encore modeste malgré un changed-code gate strict | Qualité | **Moyenne** | Ratchets M21 actifs à `1550 / 385`, couverture `85,0% / 68,0%` agrégée et `64,6% / 56,9%` par module ; chaque échelle a son propre plafond qualifié et ne se relève qu'après nouvelle preuve exact-head reproductible sur les deux plateformes |
| DT-11 | Nouveau workflow de release attestée pas encore qualifié par une vraie release publiée | Release | **Moyenne** | Valider l'enchaînement tag -> Linux/Windows -> attestations -> assets -> GitHub Release lors de la prochaine vraie release `v1.2.1+` ; suivi #185 |

`DT-04` (SQLite unique backend), `DT-05` (macOS) et `DT-12` (identités historiques à trois champs) sont
**requalifiées le 16/09/2026** : ce sont des positions tenues, pas des dettes. Elles partent respectivement vers
[ADR-0018 §15](../../adr/0018-sqlite-initial-persistent-store.md), [§10.4 ci-contre](10-exigences-qualite.md) et
[ADR-0094](../../adr/0094-optional-team-remote-server-mode.md), chacune avec l'événement qui la rouvrira.
`DT-07` et `DT-08` **sortent du registre technique** le même jour : ce sont des réglages de comptes externes,
suivis dans #154, qu'aucun commit ne ferme et qu'aucun test ne surveille — suivis ailleurs, pas réglés. Détail
et conditions de réouverture : [`../risks/register.md`](../risks/register.md).

| DT-17 | Les quatre routeurs d'extension écrivent chacun leur propre enveloppe de réponse | API HTTP | **Faible à moyenne** | Nommée le 16/09/2026 après vérification sur le code : trois des quatre méthodes d'envoi sont identiques octet pour octet à `MorpheusHttpResponseWriter.send`, et les quatre redéclarent en `private` des records d'enveloppe identiques aux records `public` de `MorpheusHttpServer`. Parité de réponse à épingler avant migration, comme DT-16 l'a fait pour la requête. Détail : [`../risks/register.md`](../risks/register.md) |

`DT-03` est **éliminée le 16/09/2026** : [`10-exigences-qualite.md`](10-exigences-qualite.md) §10.3 nomme désormais les cinq gates de performance M19, leur contrat de fixture et ADR-0085, sans reproduire aucun seuil — les budgets restent portés par les constantes des gates.

`DT-01` est **éliminée le 16/09/2026** : les quatre documents vivants dont le statut avait résolu sans être mis à jour sont corrigés (`RELEASE_NOTES_1.1.0.md` et `UPGRADE_1_1.md`, qui présentaient encore comme non publiée une release taguée et vérifiée ; `PROVIDER_PLUGINS.md` et `PROVIDER_SDK.md`, encore en « M22 candidate »), et les huit documents de cadrage C0 du 22/07/2026 reçoivent une notice historique **sans que leur ligne `Statut` soit réécrite**. Les preuves datées de `docs/validation/`, `docs/research/` et `docs/roadmap/*_EXECUTION.md` sont intactes. Détail et condition de réouverture : [`../risks/register.md`](../risks/register.md).

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
Surefire total          >= 1550
architecture            >= 385
aggregate line          >= 85.0%
aggregate branch        >= 68.0%
per-module line         >= 64.6%
per-module branch       >= 56.9%
changed-line            >= 80%
changed-branch          >= 70%
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
