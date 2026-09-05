# Scénarios de qualité — MORPHEUS ENGINE

> Complément de [§10](../arc42/10-exigences-qualite.md).
> Baseline active : MORPHEUS 1.2.1 corrective — `develop` post-audit. Dernière release publiée : `v1.2.0`.
>
> Un scénario est marqué **qualifié** uniquement lorsqu'une preuve exécutable ou
> une validation identifiée existe. Les objectifs sans seuil mesuré restent des
> candidats et ne sont pas transformés en garanties.

---

## Exactitude

### Q-AC-01 — CURRENT / PROPOSED restent séparés — qualifié

| Champ | Valeur |
|-------|--------|
| Stimulus | Construction ou lecture d'un état PROPOSED pendant qu'un snapshot CURRENT est publié |
| Environnement | Stores mémoire ou SQLite selon le test |
| Réponse | Les lectures CURRENT n'exposent aucun fait PROPOSED |
| Mesure | Fuites temporelles |
| Seuil | 0 |
| Preuve | Tests temporal/snapshot et invariants de validation |

### Q-AC-02 — Facts / claims restent distingués — qualifié M27

| Champ | Valeur |
|-------|--------|
| Stimulus | Une analyse assistée produit faits, inférences ou suggestions |
| Réponse | Nature, confiance, evidence et provenance restent explicites |
| Mesure | Claim présenté comme fait sans statut/preuve conforme |
| Seuil | 0 |
| Preuve | ADR-0095 et validation M27 |

### Q-AC-03 — Convergence des surfaces

| Champ | Valeur |
|-------|--------|
| Stimulus | Une capacité publique existe sur une surface |
| Réponse | Son exposition attendue est explicitement suivie pour CLI, MCP et HTTP |
| Mesure | Écart non documenté au contrat de surfaces |
| Seuil | 0 écart silencieux |
| Preuve | `contracts/public-surfaces.tsv` + tests d'architecture/surfaces |

La convergence est **sémantique** ; elle n'exige pas des payloads ou transports
bit-à-bit identiques.

---

## Sécurité

### Q-SE-01 — Confinement des workspaces — qualifié D2

| Champ | Valeur |
|-------|--------|
| Stimulus | Résolution d'un chemin ou workspace hors des racines autorisées |
| Réponse | Refus avant lecture du contenu hors périmètre |
| Mesure | Accès accepté hors racine |
| Seuil | 0 |
| Preuve | `AllowedWorkspaceRootsTest`, `SafeWorkspaceFileResolverTest` |

### Q-SE-02 — Ingestion bornée — qualifié D2

| Champ | Valeur |
|-------|--------|
| Stimulus | Un provider dépasse une limite de taille/volume définie |
| Réponse | Échec explicite et borné |
| Mesure | Ingestion acceptée au-delà du budget |
| Seuil | 0 |
| Preuve | `ProviderIngestionBudgetTest` et tests providers associés |

### Q-SE-03 — JSON hostile / profondeur excessive — qualifié D2

| Champ | Valeur |
|-------|--------|
| Stimulus | Payload JSON pathologique ou dépassant les limites prévues |
| Réponse | Parsing refusé sans comportement non borné |
| Mesure | Payload hostile accepté contrairement au contrat |
| Seuil | 0 |
| Preuve | `JacksonSecurityRegressionTest` et tests JSON providers |

### Q-SE-04 — Intégrité des plugins externes — qualifié D2

| Champ | Valeur |
|-------|--------|
| Stimulus | Activation d'un JAR provider externe |
| Réponse | L'intégrité attendue est vérifiée avant utilisation selon le contrat |
| Mesure | JAR invalide accepté |
| Seuil | 0 |
| Preuve | `ExternalJarIntegrityTest` |

### Q-SE-05 — Écriture de réponse remote bornée en temps — qualifié post-audit 1.2.1 (A-10)

| Champ | Valeur |
|-------|--------|
| Stimulus | Un client TLS authentifié cesse de lire une réponse (lecture lente, arrêt après les en-têtes, disparition brutale) |
| Réponse | La deadline stall (15 s réarmée à chaque bloc écrit) ou totale (120 s) interrompt l'écriture et libère la connexion |
| Mesure | Un client abandonné retient un thread/slot de réponse au-delà des budgets |
| Seuil | 0 |
| Preuve | `TimedBoundedResponseWriterTest`, `MorpheusRemoteAdversarialClientTest` |

### Q-SE-06 — Audit d'identités : preuve, pas autorité — qualifié post-audit 1.2.1 (A-12)

| Champ | Valeur |
|-------|--------|
| Stimulus | Une entrée illisible ou corrompue existe dans le fichier d'audit des identités remote |
| Réponse | L'entrée est mise en quarantaine (`AUDIT_QUARANTINED`) sans bloquer `revoke`/`rotate`/`create` |
| Mesure | Une mutation de sécurité légitime est bloquée par une entrée d'audit illisible |
| Seuil | 0 |
| Preuve | `MorpheusRemoteIdentityAuditRecoveryTest`, ADR-0100 |

### Q-SE-07 — Budgets de query HTTP bornés avant décodage — qualifié post-audit 1.2.1 (A-13)

| Champ | Valeur |
|-------|--------|
| Stimulus | Une query string dépasse 16 KiB, 16 paramètres, 128 octets de nom ou 8 KiB de valeur, ou porte un encodage `%` invalide |
| Réponse | Rejet déterministe `400 BAD_REQUEST`, jamais `500`, budgets vérifiés en octets UTF-8 avant tout décodage |
| Mesure | Dépassement accepté ou provoquant une erreur non contrôlée |
| Seuil | 0 |
| Preuve | `MorpheusHttpQueryTest`, `LocalHttpQueryArchitectureTest` |

### Q-SE-08 — Lifecycle explicite du transport MCP borné — qualifié post-audit 1.2.1 (A-14)

| Champ | Valeur |
|-------|--------|
| Stimulus | Double `connect()` séquentiel ou concurrent, `connect()` après `close()`, ou échec de démarrage du pair MCP |
| Réponse | Machine d'état `NEW → CONNECTING → CONNECTED → CLOSING → CLOSED/FAILED` revendiquée avant tout démarrage ; un seul ticket de teardown est délivré |
| Mesure | Un second pair démarré sans que le premier soit nommé/terminé |
| Seuil | 0 |
| Preuve | `BoundedStdioClientTransportLifecycleTest`, `BoundedStdioClientTransportTest` |

---

## Maintenabilité

### Q-MA-01 — Isolation des couches — qualifié

| Champ | Valeur |
|-------|--------|
| Stimulus | Une dépendance interdite est introduite entre couches/modules |
| Environnement | `morpheus-architecture-tests` |
| Réponse | La qualification échoue |
| Mesure | Violations non détectées |
| Seuil | 0 |
| Preuve | Tests ArchUnit / architecture |

### Q-MA-02 — Dependency hygiene — qualifié D2

| Champ | Valeur |
|-------|--------|
| Stimulus | Une dépendance Maven déclarée/utilisée devient incohérente |
| Réponse | `verify` échoue sur l'analyse bloquante |
| Mesure | Warnings de dependency analysis ignorés |
| Seuil | 0 |
| Preuve | `maven-dependency-plugin:analyze-only`, `failOnWarning=true` |

### Q-MA-03 — Qualification publique exact-head

| Champ | Valeur |
|-------|--------|
| Stimulus | Une pull request déclenche MORPHEUS CI |
| Environnement | Ubuntu + Windows |
| Réponse | Les jobs qualifient le head exact de la PR |
| Mesure | Différence entre SHA attendu et SHA qualifié / job en échec |
| Seuil | 0 |
| Preuve | `.github/workflows/ci.yml` et metadata GitHub Actions |

Le workflow actuel appelle `validate-m21` avec la version 1.2.1. Le numéro M21
est celui du gate durable d'intégrité ; il ne représente pas le dernier
milestone fonctionnel livré.

---

## Portabilité

### Q-PO-01 — Runtime embarqué — qualifié R3

| Champ | Valeur |
|-------|--------|
| Stimulus | Lancement d'un artefact publié sans JDK utilisateur |
| Environnement | Windows ou Linux selon l'artefact |
| Réponse | MORPHEUS s'exécute avec le runtime embarqué |
| Mesure | Smoke test de l'artefact |
| Seuil | PASS pour les artefacts publiés |
| Preuve | `docs/validation/VALIDATION_R3.md` |

### Q-PO-02 — Parité des artefacts publiés — qualifié R3

| Champ | Valeur |
|-------|--------|
| Stimulus | Publication de la release 1.2.0 |
| Réponse | Les artefacts publiés correspondent aux artefacts qualifiés |
| Mesure | Parité des assets / checksums |
| Seuil | PASS |
| Preuve | Validation R3 et manifests de release |

### Q-PO-03 — macOS — observé au niveau smoke, non supporté

macOS n'est **pas** une plateforme publiée ou supportée par la baseline actuelle.
Aucun packaging macOS n'est produit et aucun engagement de support n'est pris.

Ce qui a changé : la lane `macos-smoke` de `ci.yml` fait tourner le reactor complet
sur `macos-latest` et enregistre les faits système dont MORPHEUS dépend
(sensibilité à la casse, permissions POSIX sur `TMPDIR`, création de liens
symboliques, version Java). Elle est **advisory** — `continue-on-error: true` —
parce qu'une plateforme jamais qualifiée ne doit pas bloquer les pull requests, et
parce qu'une lane verte ne vaut pas une décision de support.

| Champ | Valeur |
|-------|--------|
| Stimulus | Exécution du reactor complet sur `macos-latest` |
| Réponse | Observation enregistrée, sans engagement de support ni packaging |
| Mesure | Résultat de la lane advisory + faits système publiés dans le résumé de job |
| Seuil | Aucun — la lane est non bloquante |
| Preuve | Job `macos-smoke` de `.github/workflows/ci.yml` |

#### Premier fait observé (04/09/2026)

La lane a produit un constat dès sa première exécution, ce qui est exactement sa raison d'être.

Sur `macos-latest`, `TMPDIR` pointe sous `/var/folders/...`, et `/var` est un **lien symbolique**
vers `/private/var`. `LocalWritePermissionHardener` refuse de durcir ou de lire un chemin atteint
par un lien symbolique — ce refus est un invariant de sécurité, et il a fonctionné exactement comme
prévu. Conséquence : tous les tests utilisant `@TempDir` échouaient avant qu'aucun comportement
spécifique à macOS ne puisse être observé.

Second fait, découvert en tentant de corriger le premier : **cela ne se corrige pas depuis le
runner.** Exporter un `TMPDIR` résolu ne change rien — sur macOS la JVM prend son répertoire
temporaire d'une API Darwin au démarrage, pas de l'environnement — et passer
`-Djava.io.tmpdir` en ligne de commande Maven arrive trop tard pour la JVM de test forkée.

**Ce n'est pas un défaut MORPHEUS et l'invariant n'a pas été touché.** Aucun contournement n'a été
introduit : la lane reste advisory et **signale** le fait plutôt que de le masquer.

Conséquence pour une future décision de support macOS — c'est le livrable réel de A-09 :

1. sur macOS, le répertoire temporaire par défaut est derrière un lien symbolique, et MORPHEUS le
   refuse **correctement** ;
2. ce refus n'est pas contournable par variable d'environnement ni par propriété Maven ;
3. rendre la lane verte suppose de décider **où les tests MORPHEUS enracinent leurs fichiers
   temporaires** sur cette plateforme — une décision de support, pas un ajustement de CI ;
4. la même question se posera à l'exécution pour un répertoire de données dérivé du répertoire
   temporaire.

Tant que cette décision n'est pas prise, la lane observe, échoue de façon informative, et ne bloque
rien.

Passer de « observé » à « supporté » exige une décision produit explicite : lane
rendue bloquante, packaging macOS, validation dual-platform étendue, et ADR dédié.
L'état actuel reste **RT-08 ouvert**.

---

## Extensibilité

### Q-EX-01 — Provider externe isolé — qualifié par le SDK

| Champ | Valeur |
|-------|--------|
| Stimulus | Développement d'un provider externe compatible |
| Réponse | Le provider reste derrière les contrats du SDK sans contamination des couches internes |
| Mesure | Violation d'architecture ou de contrat SDK |
| Seuil | 0 |
| Preuve | `morpheus-provider-testkit`, provider de référence, tests d'architecture |

### Q-EX-02 — Configuration MCP conservatrice — qualifié M28

| Champ | Valeur |
|-------|--------|
| Stimulus | Installation/configuration d'un client MCP |
| Réponse | Modification opt-in ; entrée étrangère non écrasée ; uninstall state-driven |
| Mesure | Écrasement/suppression d'une configuration non possédée |
| Seuil | 0 |
| Preuve | ADR-0096 et validation M28 |

---

## Résilience

### Q-RE-01 — Adaptateur externe indisponible

| Champ | Valeur |
|-------|--------|
| Stimulus | MINOS ou NEXUS ne répond pas |
| Réponse | MORPHEUS conserve les faits locaux et expose l'indisponibilité explicitement |
| Mesure | Perte de faits locaux liée à l'adaptateur |
| Seuil | 0 |
| Preuve | Suites de tests des intégrations + invariants cross-engine |

### Q-RE-02 — Concurrence / atomicité SQLite

| Champ | Valeur |
|-------|--------|
| Stimulus | Accès concurrents ou échec pendant une opération persistante |
| Réponse | Pas de publication partielle ni corruption silencieuse |
| Mesure | État incohérent observable |
| Seuil | 0 |
| Preuve | Tests de transaction/concurrence SQLite et architecture M19/D2 |

---

## Performance

Les scénarios performance doivent utiliser les budgets et fixtures déjà
versionnés dans les tests M19. Cette documentation ne fournit pas de valeurs
inventées telles que « démarrage < 2 s » ou « sync < X s ».

Pour tout nouveau SLO :

```text
fixture représentative
+ métrique définie
+ seuil versionné
+ test reproductible
+ preuve Windows/Linux si cross-platform
```

Sans ces cinq éléments, la valeur reste une hypothèse et non une exigence
qualifiée.
