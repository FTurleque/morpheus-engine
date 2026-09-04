# Registre des risques et de la dette — MORPHEUS ENGINE

> Synthèse opérationnelle de [§11](../arc42/11-risques-dette.md).
> Baseline active : MORPHEUS 1.2.1 corrective — branche de développement post-audit.
> Dernière release publiée : `v1.2.0`.
> Mise à jour : **2026-09-04** (passes post-audit A-02..A-16 ; A-01 suivie séparément selon son cycle de qualification réelle — voir #185).
>
> **P** = probabilité, **I** = impact, **E** = exposition = P × I ; échelle 1 à 3.

---

## Risques techniques

| ID | Risque | P | I | E | Mitigation actuelle | Révision |
|----|--------|:-:|:-:|:-:|---------------------|----------|
| RT-01 | Concurrence SQLite si le mode remote devient fortement multi-écrivain | 2 | 3 | **6** | `journal_mode=PERSIST`, busy timeout, transactions bornées, une connexion physique scopée par opération API/Query/CLI, leases, réservation persistante atomique des séquences de version, index d'unicité, backups ; **observabilité de contention** (`sqlite.contention.*`, `sqlite.transaction.duration`) et tests de stress multi-écrivain | Si le profil d'usage remote évolue |
| RT-02 | Rollback applicatif après migration de schéma | 2 | 3 | **6** | Migrations forward-only, checksums, refus des schémas futurs, backup/restore offline | À chaque évolution de schéma |
| RT-03 | Limites de `jdk.httpserver` sous forte charge | 2 | 2 | **4** | Concurrence remote bornée, timeouts/budgets et inventaires filesystem bornés ; **harnais de charge reproductible** (`MorpheusRemoteLoadProfileTest`) et **critères objectifs de remplacement** documentés — mesurer avant substitution | Lors de load tests représentatifs |
| RT-04 | Breaking change MCP SDK / clients MCP | 2 | 2 | **4** | Version épinglée, tests de contrat, budgets de frames/queues, cancellation serveur, cleanup fail-closed et diagnostics redacted | À chaque upgrade MCP |
| RT-05 | Provider externe malformé, bloquant ou non fiable | 2 | 2 | **4** | Discovery metadata-only sans symlink avec revalidation d'identité avant/après lecture, activation explicite, SHA-256 obligatoire en remote, staging vérifié, budgets d'ingestion et environnement enfant minimisé | À chaque évolution du Provider SDK |
| RT-12 | Peer MCP externe MINOS/NEXUS compromis | 2 | 2 | **4** | JAR optionnel/pinnable, environnement hérité réduit à une allowlist, descendants observés et terminés, frames/queues bornées, stderr et exceptions peer redacted ; la frontière n'est pas une sandbox OS | À chaque évolution du transport MCP |
| RT-06 | Diagnostic runtime limité par le logging silencieux | 2 | 2 | **4** | Health/metrics, erreurs structurées et diagnostics MCP sanitizés ; préserver stdout MCP | Permanent |
| RT-09 | Drift documentaire entre sources historiques et HEAD | 2 | 2 | **4** | Hiérarchie des sources, séparation release publiée `1.2.0` / baseline active `1.2.1`, guides actifs réconciliés et contrats d'architecture sur les invariants CI | À chaque release/hardening |
| RT-07 | Auth remote sans SSO/LDAP | 1 | 2 | **2** | Bearer auth + RBAC, mutations inter-processus sérialisées, live reload, audit secret-free roulant borné ; mot de passe TLS résolu tardivement en `char[]` et jamais retenu dans les options de lancement | Si besoin entreprise démontré |
| RT-08 | macOS non qualifié | 2 | 1 | **2** | Support officiel Windows + Linux uniquement ; lane CI `macos-smoke` **advisory** (`continue-on-error`) qui exécute le reactor complet et publie les faits système observés — observation, pas qualification | Si support macOS décidé |

### Risques de gouvernance résolus le 27/08/2026

Les anciens risques `RT-10` (promotion stable sans ruleset complet) et `RT-11` (push direct sur `develop`) ne sont plus actifs : le ruleset **Protect main & develop** couvre les deux branches, exige une pull request, la résolution des conversations, les checks exact-head Linux/Windows, Dependency-Check et CodeQL, et interdit suppression/non-fast-forward sans bypass.

Ils restent traçables dans l'historique Git et les issues #154/#166, mais ne doivent plus être présentés comme risques ouverts.

---

## Dette technique / documentaire

| ID | Dette | Domaine | Priorité | Action |
|----|-------|---------|----------|--------|
| DT-01 | Documents historiques encore présentés avec des baselines C0/M20/M27 | Documentation | **Haute** | Les qualifier comme historiques ou les réconcilier dans des PR dédiées sans falsifier les preuves passées |
| DT-07 | Quality Gate SonarCloud potentiellement moins strict que le gate repository sur le nouveau code | Qualité externe | **Moyenne** | Vérifier le réglage SonarCloud ; le repository impose indépendamment `>= 80%` changed-line et `>= 70%` changed-branch coverage ; suivi #154 |
| DT-08 | État des alertes Dependabot / Secret Scanning non vérifiable par le connecteur | Supply chain | **Moyenne** | Vérifier/activer les réglages administrateur ; le dépôt fournit Dependabot, OWASP Dependency-Check et CodeQL versionné ; suivi #154 |
| DT-10 | Couverture historique globale encore modeste malgré un changed-code gate strict | Qualité | **Moyenne** | Ratchets M21 actifs à `1300 / 335 / 54,5% / 47,7%` (mesure exact-head Windows du 04/09/2026 : 57,49% lignes / 50,21% branches) ; #184 est clôturée, conserver la remontée progressive uniquement après nouvelle preuve exact-head reproductible |
| DT-11 | Nouveau workflow de release attestée pas encore qualifié par une vraie release publiée | Release | **Moyenne** | Valider l'enchaînement tag -> Linux/Windows -> attestations -> assets -> GitHub Release lors de la prochaine vraie release `v1.2.1+` ; suivi #185 |
| DT-12 | Identités remote historiques à trois champs sans expiration | Sécurité remote | **Faible à moyenne** | Compatibilité contractuelle et verrouillée par `legacyThreeFieldIdentityRemainsNonExpiring` ; `server identity list` expose `nonExpiring` par entrée et le total `nonExpiringIdentities`. **`server identity migrate-legacy`** donne une échéance explicite sans rotation de token, avec `--dry-run`, écriture atomique verrouillée, audit `EXPIRY_MIGRATED` et refus complet d'un lockout ADMIN. Les nouvelles identités exigent `--expires-at`, `never` restant un choix explicite. Retirer le format à trois champs reste une évolution explicitement incompatible, pas un patch 1.2.1 |
| DT-03 | Seuils de performance M19 peu visibles depuis la documentation d'architecture | Qualité | **Moyenne** | Relier les scénarios qualité aux tests/gates autoritatifs |
| DT-04 | SQLite reste l'unique backend persistant | Architecture | **Faible à moyenne** | N'engager un backend alternatif qu'après besoin et ADR dédiés |
| DT-05 | Distribution macOS absente | Distribution | **Faible** | Décision produit avant ajout du packaging ; la lane smoke n'en produit aucun |

Les anciennes dettes `DT-06` (protection `main`) et `DT-09` (protection `develop`) sont résolues et retirées du tableau actif.

---

## Correctifs issus de la passe post-audit du 04/09/2026 (A-02 à A-09)

| Constat | Traitement |
|---|---|
| **A-02** — une mutation remote bloquée retenait ses slots sans être observable, et `server/status` était admis par le sémaphore qu'il décrit : un serveur à son plafond répondait `429` à la seule question utile | status servi sur une **voie bornée dédiée** (hors budget de requêtes, toujours authentifié et autorisé) ; status expose `activePrivilegedRequests`, `maxConcurrentPrivilegedRequests`, `oldestActivePrivilegedRequestMillis`, `throttledPrivilegedRequests` ; tests adversariaux de saturation, de refus et de libération de tous les slots. **Résiduel assumé** : une mutation réellement bloquée retient son slot jusqu'à sa fin réelle — aucune deadline n'est ajoutée, car un `504` sur un commit peut-être déjà durable serait pire |
| **A-03** — la contention SQLite n'avait aucun signal en production ; `SqliteFailureClassifier` n'avait **aucun appelant** hors tests | compteurs process-local `sqlite.transaction.{started,committed,rolled_back}`, `sqlite.contention.{busy_or_locked,connection_open}` et timing `sqlite.transaction.duration`, exposés par `GET /api/v1/metrics` ; tests de stress multi-écrivain (aucune perte d'update, aucun doublon d'identité, aucun lease abandonné). **Portée déclarée** : transactions explicites et ouvertures de connexion, pas les écritures mono-instruction en autocommit |
| **A-04** — les deux frontières d'exécution externe (probe plugin, pair MCP) avaient des allowlists d'environnement identiques **par coïncidence**, sans contrat | `ExternalCodeTrustBoundaryArchitectureTest` : allowlists asserties identiques, aucun secret MORPHEUS ni variable d'injection JVM, environnement reconstruit depuis vide, pin + staging vérifié, et scan de **toute** source de production interdisant une revendication de sandbox |
| **A-05** — couverture historique globale modeste | tests ciblés sur les zones à risque (disponibilité remote, contention SQLite, migration d'identités, parsing fail-closed du fichier d'identités, cycle de vie du secret TLS, charge HTTP) ; ratchets relevés uniquement dans la marge déjà qualifiée sur les deux plateformes |
| **A-06** — le format d'identité sans expiration n'avait aucune sortie opérable | `server identity migrate-legacy` (dry-run, échéance explicite, aucune rotation de token, écriture atomique verrouillée, audit sans secret, refus complet d'un lockout ADMIN) et visibilité `nonExpiring` dans le listing. Le format reste supporté en 1.2.1 |
| **A-07** — `jdk.httpserver` jamais mesuré sous charge | `MorpheusRemoteLoadProfileTest` : tempête de lectures au-delà de la capacité, charge mixte lecture/mutation, corps surdimensionnés, fermeture de la façade. Le scénario « client abandonné » de cette suite s'est révélé faux (voir A-11) et vit désormais dans `MorpheusRemoteAdversarialClientTest`. Les propriétés sont asserties, les latences **enregistrées comme preuve** dans `target/remote-load-profile.txt` ; critères objectifs de remplacement documentés |
| **A-08** — `RemoteApiLaunchOptions` retenait le mot de passe TLS en `String` pendant toute la vie du serveur, et un record rend tous ses composants | les options ne portent plus que le moyen de retrouver la valeur ; résolution tardive en `char[]`, effacé dès que le `SSLContext` existe. **Pas une promesse d'effacement** : la JVM détient déjà le `String` d'origine |
| **A-09** — macOS inconnu | lane `macos-smoke` **advisory** exécutant le reactor complet et publiant les faits système observés. Aucun packaging, aucun engagement de support ; RT-08 reste ouvert |
| **A-01** — qualification de la chaîne de release attestée | **non traitée volontairement.** Aucun tag, aucune release, aucune simulation. #185 et DT-11 restent ouverts |


---

## Correctifs issus de la passe post-audit du 04/09/2026 (A-10 à A-16)

| Constat | Traitement |
|---|---|
| **A-10** — la façade remote bornait le nombre de requêtes, la mémoire et la taille des réponses, mais pas le **temps** qu'un client pouvait mettre à lire ; un client TLS authentifié qui cesse de lire retenait permits, slot de réponse et thread aussi longtemps qu'il restait connecté | `TimedBoundedResponseWriter` applique deux budgets à **toutes** les écritures de réponse (enveloppe et corps proxifié) : stall 15 s réarmé à chaque bloc réellement écrit, total 120 s. La deadline interrompt le thread écrivain — fermeture de canal spécifiée par `java.nio.channels.InterruptibleChannel`, **pas** la propriété interne `sun.net.httpserver.maxRspTime`. Compteur `responseWriteTimeouts` dans `server/status`. **Résiduel assumé** : la réponse est avortée, le client voit un corps tronqué et aucune enveloppe d'erreur ne peut lui parvenir — la connexion qui la porterait est la ressource récupérée |
| **A-11** — le scénario « client abandonné » de `MorpheusRemoteLoadProfileTest` utilisait `BodyHandlers.discarding()`, qui **consomme** le corps normalement : il ne pouvait pas échouer | scénario retiré et renommé pour ce qu'il testait réellement (corps surdimensionné). `MorpheusRemoteAdversarialClientTest` pilote une socket TLS brute contre la vraie façade : lecture des seuls en-têtes puis arrêt, lecture lente avec pauses, disparition brutale (`SO_LINGER 0`). `TimedBoundedResponseWriterTest` exerce le mécanisme sur des canaux TCP réels. Fixture dimensionnée pour dépasser les tampons de socket, sinon un client bloqué ne coûte rien au serveur |
| **A-12** — une seule ligne `# audit\|…` illisible faisait échouer **toute** mutation ultérieure du fichier d'identités : le credential compromis restait valide et devenait irrévocable | l'audit historique est une **preuve, pas une autorité**. Les entrées illisibles sont mises en quarantaine et la perte est elle-même enregistrée (`AUDIT_QUARANTINED`, sujet réservé `morpheus.audit`, aucun écho de la ligne rejetée). `revoke`, `rotate` et `create` aboutissent ; le fichier reste parsable et l'audit borné. La lecture stricte `audit(Path)` reste stricte : elle rapporte l'état du disque sans bloquer une opération de sécurité |
| **A-13** — `MorpheusHttpQuery.parse` et le resolver remote découpaient et décodaient la query **avant** toute limite MORPHEUS | `HttpQueryBudget` partagé par les deux parseurs : 16 KiB de query, 16 paramètres, 128 octets de nom, 8 KiB de valeur, comptés en **octets UTF-8** et vérifiés sur le texte brut avant allocation (le décodage ne peut que rétrécir). Dépassement = `400 BAD_REQUEST` déterministe, jamais `500` ; encodage `%` invalide également |
| **A-14** — `BoundedStdioClientTransport` n'avait aucune machine d'état : un second `connect()` démarrait un second pair et écrasait la référence au premier, laissant un processus que plus rien ne pouvait nommer — donc plus rien ne pouvait terminer | lifecycle explicite `NEW → CONNECTING → CONNECTED → CLOSING → CLOSED/FAILED`, revendiqué **avant** tout démarrage. Démarrage et teardown partagent un verrou, donc ils ne courent plus après le même processus. `claimTeardown()` donne le ticket unique à `closeGracefully()` ou à `failClosed()`, et un second `close` attend le premier au lieu de le répéter. Tests : double connect séquentiel et concurrent, connect après close, close concurrent, échec de démarrage — chacun comptant les lancements **côté pairs** |
| **A-15** — le ruleset exige une PR mais pas d'approbation humaine | **décision de gouvernance, aucune modification GitHub effectuée.** `.github/CODEOWNERS` existe déjà et couvre `.github/`, `scripts/`, `distribution/`, `morpheus-api/`, `morpheus-provider-sdk/`, `morpheus-mcp-transport/`, `morpheus-store-sqlite/` ; le ruleset **Protect main & develop** exige une PR mais porte `require_code_owner_review=false` et `required_approving_review_count=0`, assumé pour un dépôt à mainteneur unique. Si le dépôt devient collaboratif, la recommandation est d'activer au minimum une revue obligatoire (`required_approving_review_count >= 1` et/ou `require_code_owner_review=true`) sur le ruleset existant — `CODEOWNERS` n'est pas à créer, il est déjà en place |
| **A-16** — couverture à renforcer | uniquement par des scénarios adversariaux réels : slow reader, stalled reader, déconnexion brutale, récupération des permits, audit d'identités corrompu, budgets de query aux bornes, double connect MCP séquentiel et concurrent, nettoyage après échec de démarrage. Aucun seuil abaissé, aucun test supprimé, aucune exclusion de couverture |

Constat connexe relevé pendant la passe et corrigé : le schéma `ServerStatus` de `docs/openapi/morpheus-v1-remote-m26.yaml` portait `additionalProperties: false` tout en omettant sept champs que la réponse émettait déjà depuis M26 (`activePrivilegedRequests`, `oldestActivePrivilegedRequestMillis`, `requestBodyReadTimeoutMillis`, `totalPrivilegedRequests`, `throttledPrivilegedRequests`, `requestTimeouts`, `maxConcurrentPrivilegedRequests`). Le contrat publié rejetait donc la réponse réelle du serveur. Les champs manquants et `responseWriteTimeouts` y sont désormais décrits.

Constat connexe relevé pendant la passe et corrigé : `AuditHardeningWorkflowContractTest` interdisait `continue-on-error` dans **tout** `ci.yml`. Exact tant que le fichier ne portait qu'un seul job, faux dès qu'une lane advisory apparaît à côté. Le contrat vise désormais précisément le job `verify` requis : ce qui ne doit jamais être advisory est le job qui garde les merges, pas le fichier.

Observation de mesure, sans correction : l'agrégat JaCoCo **sous-estime structurellement** la couverture, parce que le rapport de `morpheus-architecture-tests` est exclu de l'agrégation alors que cette suite couvre une large part de `morpheus-application`. Aucun changement n'a été fait : inclure ce rapport ferait monter le chiffre sans ajouter un seul test, ce qui est exactement le jeu que les ratchets existent pour empêcher.

---

## Correctifs issus de l'audit du 30/08/2026

| Constat | Traitement |
|---|---|
| `nextSpecificationVersionSequence()` utilisait `MAX(sequence)+1`, ce qui pouvait attribuer le même numéro à deux connexions/processus concurrents | migration V017 `specification_version_sequences`, réservation durable dans une transaction d'écriture avant la publication, prise en compte du maximum déjà stocké, parité du store mémoire et test de concurrence à deux stores indépendants |
| La discovery provider validait un chemin puis rouvrait le JAR sans vérifier qu'il s'agissait toujours du même fichier | revalidation des attributs/identité avant et après la lecture metadata, diagnostic `PLUGIN_JAR_CHANGED_DURING_SCAN` en cas de remplacement ; l'activation exécutable conserve la frontière plus forte SHA-256 + staging vérifié déjà existante |
| `QueryFieldType.NUMBER` existait dans l'API mais héritait de l'égalité/du tri textuels | sémantique numérique explicite basée sur `BigDecimal` pour l'égalité canonique et l'ordre ; comportement historique TEXT/ENUM/BOOLEAN/IDENTITY préservé |
| L'audit initial signalait l'absence d'un gate changed-branch à 70 % et un registre de risques obsolète | ces deux points étaient déjà corrigés sur `develop` avant cette branche : gate `>=80%` lignes / `>=70%` branches et ruleset `main/develop` actifs ; aucun correctif redondant ajouté |
| La couverture globale restait modeste | les ratchets avaient déjà été qualifiés à `52,0%` lignes / `45,0%` branches et #184 clôturée ; les nouveaux correctifs ajoutent des tests ciblés, sans relever artificiellement les seuils avant mesure exact-head |
| La chaîne de release 1.2.1+ n'avait pas encore de qualification réelle | le workflow attesté existe déjà sur `develop`; #185 reste volontairement ouvert jusqu'à une vraie release, aucune release artificielle n'est créée pour fermer le constat |

La migration V017 change la version de schéma durable de 16 à 17. Les validations M26 Linux et Windows sont alignées sur `schemaVersion=17` pour les scénarios backup, verify et restore ; les migrations historiques V001..V016 restent immuables.

---

## Correctifs issus du réaudit post-#183 du 27/08/2026

| Constat | Traitement |
|---|---|
| `QueryDefinitionCodec.decode()` pouvait commencer une récursion avant application des budgets globaux M24 | codec désormais fail-fast : taille encodée <= 16 KiB avant Base64, profondeur <= 8 avant récursion, compteurs globaux <= 128 nœuds et <= 64 prédicats ; validation sémantique avant retour |
| `QueryValidator` continuait à parcourir l'AST après dépassement structurel | parcours interrompu dès le premier dépassement structurel pour borner aussi les AST construits directement en mémoire |
| HTTP/MCP/CLI pouvaient dépendre implicitement du comportement du codec sans garde d'architecture dédiée | contrat d'architecture ajouté : les trois adapters policy doivent passer par `QueryDefinitionCodec` et ne peuvent introduire un décodeur Base64 parallèle |
| Couverture historique à 50,7896 % lignes / 43,2215 % branches | aucun seuil abaissé ; tests adversariaux ciblés ajoutés ; la remontée progressive a ensuite permis de clôturer #184 après qualification des ratchets `52,0% / 45,0%` |
| Réglages SonarCloud / alertes administrateur externes non qualifiables par le code | vérifiés directement sur leurs plateformes ; #154 clôturée, aucune correction repository-side supplémentaire n'était requise |
| Workflow release attestée correct mais jamais exécuté sur une vraie release | qualification end-to-end suivie par #185 ; aucune release artificielle créée pour fermer le constat |

Les tests adversariaux du codec couvrent notamment une représentation >16 KiB, 10 000 `NOT` imbriqués, le 129e nœud et le 65e prédicat. La preuve M24 historique reste immuable ; ADR-0092 contient un addendum de hardening post-audit.

---

## Correctifs issus de l'audit du 27/08/2026

| Constat | Traitement dans la baseline corrective |
|---|---|
| Cache OWASP accepté 72 h mais refresh trusted hebdomadaire | refresh trusted quotidien à 04:17 UTC, TTL PR centralisé à 72 h et contrat d'architecture interdisant le retour au cron hebdomadaire |
| Release avec checksum sans preuve d'origine | workflow `MORPHEUS Release` sur tags `vX.Y.Z`, tag obligatoirement atteignable depuis `main`, attestation GitHub OIDC/Sigstore via `actions/attest` pinné par SHA, assets non écrasables |
| Changed-code gate uniquement par lignes | gate PR `>= 80%` lignes et `>= 70%` branches sur les lignes exécutables modifiées, preuve archivée dans `diff-coverage.txt` |
| Diagnostics MCP pouvant journaliser un `Throwable` peer brut | redaction JSON/named secrets, `describe(Throwable)` sanitizé, suppression des logs bruts dans les transports client et serveur, tests de régression |
| Guide build/test désynchronisé | reactor, baselines, ratchets, sécurité, release et couverture différentielle réconciliés |
| #166 et registre de risques encore basés sur `develop` non protégée | état GitHub réconcilié ; #166 peut être clôturée comme complétée |

---

## Correctifs issus de l'audit du 26/08/2026

| Constat | Traitement dans la baseline corrective |
|---|---|
| Environnement MORPHEUS intégralement hérité par MINOS/NEXUS | `BoundedStdioClientTransport` conserve uniquement une allowlist de lancement puis applique les variables explicitement configurées pour le peer |
| Descendant MCP pouvant survivre après sortie du parent | observation périodique des `ProcessHandle`, rétention des descendants vus puis cleanup forcé même si le parent a déjà disparu ; **résiduel assumé** : un descendant créé et orphelin dans un même intervalle d'observation n'est plus attribuable au pair et n'est pas terminé (cf. `SECURITY.md`, section Process-tree termination) |
| Descendant de plugin provider survivant au worker | **fermé** : `ProviderPluginProbeWorker` termine son propre sous-arbre avant de sortir, tant qu'il est encore énumérable ; le cleanup côté parent reste une seconde ligne de défense |
| Secret NVD disponible sur le chemin PR | `security.yml` sépare les événements de confiance du chemin `pull_request`; l'update PR n'injecte aucun repository secret |
| Absence de SAST versionné | `.github/workflows/codeql.yml` ajouté, actions CodeQL pinnées par SHA, Java `security-extended` |
| Ratchets devenus trop permissifs | M21 relevé progressivement ; baseline qualifiée au 02/09/2026 `1150 / 310 / 54,0% / 47,0%` (valeur historique ; voir l'état CI actif pour la baseline courante) |
| Manifeste update distant en HTTP | `UpdateDiscoveryService` accepte `file:` et `https:` uniquement ; `http:` est refusé avant I/O |

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
Surefire total       >= 1300
architecture         >= 335
line coverage        >= 54.5%
branch coverage      >= 47.7%
changed-line         >= 80%
changed-branch       >= 70%
```

La qualification exact-head de #230 sur `9602eaa4a20b08955e63a6dfe10e30fb1ec90f1d` a produit `1017` tests, `308` tests d'architecture, `52,6971%` lignes et `45,7250%` branches et a justifié les ratchets ci-dessus. Les PR de hardening ultérieures doivent satisfaire les mêmes gates ; le registre n'utilise pas un SHA mouvant de `develop` comme prétendue baseline active.

`MORPHEUS Security` exécute OWASP Dependency-Check (CVSS >= 7 bloquant) sur PR/push `main` et `develop`, quotidiennement et sur demande. Les PR n'obtiennent pas la clé NVD depuis ce workflow. `MORPHEUS CodeQL` exécute un SAST Java versionné avec `security-extended`.

Le ruleset GitHub actif rend ces checks obligatoires avant merge sur `main` et `develop`. Le nom **M21** désigne le gate durable d'intégrité/surface-convergence ; il ne signifie pas que les fonctionnalités M22 à M28 sont absentes ou non qualifiées.

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
6. Les réglages externes (SonarCloud, alertes/scanning GitHub non exposés au connecteur) restent ouverts tant qu'ils n'ont pas été vérifiés sur leur plateforme respective.
