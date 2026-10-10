# Sprints de correction
Ordre de correction des **72 constats actionnables** de `findings.md`, réparti en 14 sprints. Les 24 constats *Info* ne sont dans aucun sprint : ils documentent ce qui tient et ne demandent aucune action.
L'ordre n'est pas une préférence. Il est **contraint par 33 dépendances** relevées pendant l'audit, vérifiées par script : aucun prérequis ne se trouve dans un sprint postérieur à celui qui en dépend. Les dépendances internes à un sprint sont listées sous chaque sprint et donnent l'ordre à l'intérieur du lot.
Trois principes ont dicté le découpage, au-delà des dépendances :
1. **Le dispositif avant ce qu'il mesure.** Les sprints 1 et 2 ne touchent presque aucun code applicatif. Corriger une performance dont le budget ne tourne plus, ou livrer une release dont le pipeline n'a pas été exercé depuis 1 588 commits, revient à corriger à l'aveugle.
2. **L'observabilité avant les corrections de fond.** Le sprint 4 existe tôt parce que les sprints 5 à 13 produisent des régressions possibles qu'un produit muet ne saura pas signaler.
3. **Les mêmes fichiers dans des sprints successifs, jamais parallèles.** Les sprints 6 et 7 touchent tous deux `morpheus-store-sqlite` : les séparer évite des conflits de fusion sur des lots qui pourraient sinon partir ensemble.

## Vue d'ensemble
| Sprint | Objet | Constats | Élevée | Moyenne | Faible | Charge |
|---|---|---|---|---|---|---|
| **1** | Remettre le dispositif en marche | 8 | 1 | 4 | 3 | 3.5 à 12 j |
| **2** | Débloquer la release 1.2.1 | 6 | 1 | 3 | 2 | 3.75 à 12 j |
| **3** | Fermer les écarts de sécurité | 8 | 1 | 4 | 3 | 2.75 à 10 j |
| **4** | Rendre le produit observable | 5 | 1 | 2 | 2 | 3.5 à 11 j |
| **5** | Écarter le risque d'indisponibilité | 4 | 2 | 2 | 0 | 3.25 à 10 j |
| **6** | Borner les coûts qui croissent avec les données | 4 | 3 | 1 | 0 | 4 à 12 j |
| **7** | Le SQL de détail | 7 | 0 | 3 | 4 | 2.5 à 9 j |
| **8** | Fiabiliser les tests | 7 | 0 | 5 | 2 | 4.75 à 15 j |
| **9** | Supprimer la duplication du socle | 6 | 0 | 6 | 0 | 4.5 à 14 j |
| **10** | Dire la vérité dans les règles | 7 | 0 | 3 | 4 | 3.25 à 11 j |
| **11** | Trancher l'architecture | 4 | 2 | 1 | 1 | 10 à 27 j |
| **12** | Restructurer les adaptateurs | 3 | 0 | 3 | 0 | 7 à 19 j |
| **13** | Couverture et pagination | 2 | 0 | 2 | 0 | 6 à 16 j |
| **14** | Signer la distribution | 1 | 0 | 1 | 0 | 1 à 3 j |
| | **Total** | **72** | **11** | **40** | **21** | **59.75 à 181 j** |

Charge dérivée des efforts par constat (**S** < 1 j, **M** 1 à 3 j, **L** > 3 j, borné à 8), sommés par sprint. C'est une enveloppe, pas une estimation : elle ne tient compte ni de la revue, ni des allers-retours de CI, ni du fait que plusieurs chantiers de fond demandent un arbitrage écrit avant la première ligne de code.

## Chemin critique
Quatre constats conditionnent le plus de travail en aval. Les traiter en retard décale tout ce qui en dépend :

- **AUD-TST-01** (sprint 1, effort S) débloque 7 constat(s) : AUD-DEP-12, AUD-PRF-01, AUD-PRF-02, AUD-PRF-04, AUD-PRF-05, AUD-PRF-06, AUD-TRV-02. Les cinq budgets de performance M19 ne sont exécutés par aucun pipeline ni par clean verify
- **AUD-ARC-01** (sprint 11, effort L) débloque 4 constat(s) : AUD-ARC-02, AUD-ARC-03, AUD-ARC-06, AUD-PRF-05. morpheus-application, la couche qui définit les ports, fait de l'I/O fichier et du HTTP sortant en direct
- **AUD-DEP-01** (sprint 2, effort M) débloque 3 constat(s) : AUD-DEP-12, AUD-DEP-16, AUD-TST-10. La 1.2.1 non publiée ajoute 5 migrations SQLite (V016 à V020) et aucun document d'upgrade ou de retour arrière ne les couvre
- **AUD-TRV-04** (sprint 4, effort M) débloque 2 constat(s) : AUD-QUA-13, AUD-SEC-04. Le produit est muet : aucun journal applicatif, slf4j-nop comme seul binding, et un répertoire de logs publié sans aucun écrivain

`AUD-TST-01` est le cas le plus net : un effort **S** qui conditionne six corrections de performance et de couverture. C'est la raison pour laquelle il ouvre le sprint 1.

---

## Sprint 1 — Remettre le dispositif en marche

**8 constats** · 1 Élevée · 4 Moyenne · 3 Faible · charge 3.5 à 12 j

*Pourquoi ici* — Rien ne se corrige de façon mesurable tant que les gates ne tournent pas. Ce sprint ne touche aucun code applicatif.

*Critère de sortie* — Tout gate déclaré par le dépôt est atteignable par une invocation réelle du dépôt, et aucun validateur ne rend PASS sur une valeur périmée.

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-TST-01` | Élevée | Tests | S | Les cinq budgets de performance M19 ne sont exécutés par aucun pipeline ni par clean verify |
| `AUD-DEP-06` | Moyenne | Dépendances | S | validate-d2 ne lit pas le fichier de ratchets et code en dur un plancher de 820/258 tests, contrairement à ce que la règle du dépôt affirme |
| `AUD-DEP-07` | Moyenne | Dépendances | M | Le gate CVE a un point de défaillance unique : Dependency-Check 13.0.0 ne peut pas rafraîchir la base NVD sans NVD_API_KEY |
| `AUD-TRV-01` | Moyenne | Qualité · Tests · Dépendances | M | Les quatre validateurs M15 à M18 sont inexécutables, sans équivalent .sh, et les commandes documentées pour les rejouer n'existent pas |
| `AUD-TST-02` | Moyenne | Tests | S | Le gate de couverture par module accepte un rapport JaCoCo périmé : aucun contrôle de fraîcheur |
| `AUD-QUA-14` | Faible | Qualité | S | SpotBugs et PIT sont épinglés, configurés et documentés mais lancés par aucun workflow |
| `AUD-TST-11` | Faible | Tests | S | Nom de test périmé et version de schéma recopiée en dur trois fois dans le test de rejeu des migrations |
| `AUD-TST-12` | Faible | Tests | S | La documentation M19 renvoie à un point d'entrée validate-m19.cmd qui n'existe pas |

## Sprint 2 — Débloquer la release 1.2.1

**6 constats** · 1 Élevée · 3 Moyenne · 2 Faible · charge 3.75 à 12 j

*Pourquoi ici* — Le seul cluster de l'audit dont la matérialisation est irréversible pour un utilisateur. À faire avant de taguer.

*Critère de sortie* — v1.2.1 peut être taguée : procédure d'upgrade écrite, sauvegarde impérative, attributions de licence et SBOM publiées, chaîne de release exercée de bout en bout.

*Ordre interne* — AUD-DEP-01 avant AUD-DEP-12 ; AUD-DEP-01 avant AUD-DEP-16 ; AUD-DEP-03 avant AUD-DEP-12 ; AUD-DEP-04 avant AUD-DEP-12.

*Prérequis des sprints antérieurs* — AUD-TST-01 (sprint 1).

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-DEP-01` | Élevée | Dépendances | M | La 1.2.1 non publiée ajoute 5 migrations SQLite (V016 à V020) et aucun document d'upgrade ou de retour arrière ne les couvre |
| `AUD-DEP-03` | Moyenne | Dépendances | S | La distribution d'un produit sous licence propriétaire n'embarque aucune attribution de licence tierce |
| `AUD-DEP-04` | Moyenne | Dépendances | S | La SBOM est produite à chaque verify et exigée par les validateurs, mais n'est jamais publiée avec la release |
| `AUD-DEP-12` | Moyenne | Dépendances | M | pom.xml à 1.2.1 sans tag v1.2.1 : la chaîne de release signée n'a pas tourné depuis 1 588 commits et 2,3 mois |
| `AUD-DEP-15` | Faible | Dépendances | S | MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR, qui désigne le répertoire de plugins exécutables du serveur remote, n'est documentée nulle part |
| `AUD-DEP-16` | Faible | Dépendances | M | Aucune sauvegarde automatique avant migration de schéma : la procédure repose sur la discipline de l'opérateur |

## Sprint 3 — Fermer les écarts de sécurité

**8 constats** · 1 Élevée · 4 Moyenne · 3 Faible · charge 2.75 à 10 j

*Pourquoi ici* — Huit corrections petites et indépendantes, dont la seule divulgation établie vers un appelant hors machine.

*Critère de sortie* — Plus aucun message d'exception brut ne sort vers un appelant distant, et plus aucune branche inatteignable sur un chemin de sécurité.

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-SEC-02` | Élevée | Sécurité | S | Quatre routeurs HTTP qui enregistrent leur propre contexte renvoient le message d'exception brut, sans passer par le filtre de divulgation de chemins serveur |
| `AUD-DEP-02` | Moyenne | Dépendances | S | reactor-bom figé à 2024.0.0 : 18 correctifs de retard dans la ligne même que le POM impose, et dependabot ne peut pas proposer ce correctif |
| `AUD-QUA-05` | Moyenne | Qualité | S | 16 méthodes publiques ne sont référencées nulle part, tests compris |
| `AUD-SEC-03` | Moyenne | Sécurité | M | Les segments de chemin HTTP sont décodés deux fois côté local, alors que l'autorisation remote les décode une seule fois |
| `AUD-TRV-06` | Moyenne | Sécurité · Qualité | S | Trois branches inatteignables sur des chemins de sécurité, dont deux sur l'exécution de code tiers |
| `AUD-SEC-05` | Faible | Sécurité | S | La commande Java des pairs MCP est résolue via le PATH plutôt que par un chemin absolu |
| `AUD-SEC-06` | Faible | Sécurité | S | En-têtes de sécurité incomplets : pas de X-Frame-Options ni de CSP sur les réponses de succès locales, pas de HSTS sur la façade remote |
| `AUD-SEC-07` | Faible | Sécurité | S | Aucune règle .gitignore ne protège le fichier d'identités remote ni le keystore TLS, que SECURITY.md interdit de committer |

## Sprint 4 — Rendre le produit observable

**5 constats** · 1 Élevée · 2 Moyenne · 2 Faible · charge 3.5 à 11 j

*Pourquoi ici* — Sans trace, les sprints suivants corrigent à l'aveugle et aucun incident n'est reconstituable.

*Critère de sortie* — Un échec sur un chemin SQLite, MCP ou d'adaptateur laisse une trace exploitable, et aucune variable d'observabilité ne retombe en silence.

*Ordre interne* — AUD-DEP-10 avant AUD-TRV-04 ; AUD-TRV-04 avant AUD-QUA-13 ; AUD-TRV-04 avant AUD-SEC-04.

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-TRV-04` | Élevée | Qualité · Dépendances | M | Le produit est muet : aucun journal applicatif, slf4j-nop comme seul binding, et un répertoire de logs publié sans aucun écrivain |
| `AUD-DEP-10` | Moyenne | Dépendances | S | MORPHEUS_OPERATIONAL_LOGS retombe silencieusement sur noop pour toute valeur non reconnue |
| `AUD-SEC-04` | Moyenne | Sécurité | M | Aucune trace d'attribution : le principal authentifié d'une requête remote n'est enregistré nulle part |
| `AUD-QUA-12` | Faible | Qualité | S | 47 @SuppressWarnings dont environ cinq portent une justification écrite, alors que les exclusions SpotBugs en exigent une |
| `AUD-QUA-13` | Faible | Qualité | M | L'observabilité repose sur un singleton statique mutable qui interdit d'activer le parallélisme JUnit |

## Sprint 5 — Écarter le risque d'indisponibilité

**4 constats** · 2 Élevée · 2 Moyenne · charge 3.25 à 10 j

*Pourquoi ici* — Le chemin vers l'indisponibilité du serveur entier, et la politique de connexion qui le sous-tend.

*Critère de sortie* — Une écriture concurrente n'affame plus l'ordonnanceur de threads virtuels, et un pic de charge devient une file d'attente au lieu d'échecs SQLITE_BUSY.

*Ordre interne* — AUD-PRF-18 avant AUD-PRF-03 ; AUD-TRV-05 avant AUD-PRF-03.

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-PRF-18` | Élevée | Performance · Architecture | M | Épinglage des threads virtuels : 94 méthodes public synchronized faisant du JDBC bloquant, atteintes depuis les serveurs HTTP, sur une baseline Java 21 |
| `AUD-TRV-05` | Élevée | Architecture | M | La politique de connexion SQLite est une propriété de chaque site de composition, pas du module : 12 sites, 3 ouvertures de scope |
| `AUD-PRF-03` | Moyenne | Performance | M | Serveur HTTP local sans contrôle d'admission : threads virtuels illimités, une connexion SQLite physique par requête, journal PERSIST |
| `AUD-PRF-09` | Moyenne | Performance | S | Le proxy remote n'impose ni timeout de lecture ni budget mémoire sur les routes WRITE et ADMIN |

## Sprint 6 — Borner les coûts qui croissent avec les données

**4 constats** · 3 Élevée · 1 Moyenne · charge 4 à 12 j

*Pourquoi ici* — Les quatre chemins dont le coût croît avec le volume, mesurables seulement après le sprint 1.

*Critère de sortie* — Aucun chemin public ne charge un volume non borné, et chaque correction est mesurée contre son budget M19.

*Prérequis des sprints antérieurs* — AUD-TST-01 (sprint 1).

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-PRF-01` | Élevée | Performance | M | N+1 SQL dans la lecture du contenu métier d'un snapshot : 7 requêtes filles par ligne parente |
| `AUD-PRF-02` | Élevée | Performance | M | ChangeAnalysisService refait un BFS complet par nœud déjà découvert par la traversée |
| `AUD-PRF-04` | Élevée | Performance | M | Trois tables en croissance monotone lues sans LIMIT ni pagination, dont une exposée en HTTP et en MCP |
| `AUD-PRF-06` | Moyenne | Performance | M | Écriture N+1 des liens de traçabilité : trois à quatre prepareStatement par lien, sans batch, dans une transaction unique |

## Sprint 7 — Le SQL de détail

**7 constats** · 3 Moyenne · 4 Faible · charge 2.5 à 9 j

*Pourquoi ici* — Même couche que le sprint 6, volontairement après lui pour éviter les conflits de fusion sur les mêmes fichiers.

*Critère de sortie* — Chaque colonne filtrée ou triée est servie par un index, vérifié par EXPLAIN QUERY PLAN avant modification.

*Prérequis des sprints antérieurs* — AUD-PRF-01 (sprint 6), AUD-PRF-02 (sprint 6), AUD-PRF-04 (sprint 6).

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-PRF-07` | Moyenne | Performance | S | Six index redondants dupliquant l'index implicite d'une clé primaire |
| `AUD-PRF-08` | Moyenne | Performance | M | La vérification de frontière du workspace fait un toRealPath par segment, deux fois par fichier scanné |
| `AUD-PRF-10` | Moyenne | Performance | S | Le seul SELECT paginé du module trie sur une colonne que son index ne couvre pas |
| `AUD-PRF-11` | Faible | Performance | S | ORDER BY name COLLATE NOCASE sur saved_views ne peut pas utiliser l'index BINARY qui porte la colonne |
| `AUD-PRF-12` | Faible | Performance | S | Le filtre de type de relation des liens de traçabilité est appliqué en Java alors que l'index le porte |
| `AUD-PRF-13` | Faible | Performance | S | Deux lectures triées en SQL puis retriées en Java |
| `AUD-PRF-14` | Faible | Performance | S | Aucun cache de lecture de document : chaque lecture reparcourt le texte deux fois de plus et consomme le budget |

## Sprint 8 — Fiabiliser les tests

**7 constats** · 5 Moyenne · 2 Faible · charge 4.75 à 15 j

*Pourquoi ici* — Ce qui empêche la suite de détecter une régression, une fois le dispositif remis en marche.

*Critère de sortie* — Un test de rejet asserte son motif et non seulement son type, les validateurs sont eux-mêmes testés, et la colonne CLI du manifeste a son gate.

*Prérequis des sprints antérieurs* — AUD-DEP-01 (sprint 2), AUD-DEP-06 (sprint 1), AUD-TRV-01 (sprint 1).

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-QUA-04` | Moyenne | Qualité | S | Dérive documentation/code sur le nombre de modules : 18 déclarés, 17 annoncés par quatre pages vivantes |
| `AUD-TRV-07` | Moyenne | Tests · Dépendances | M | Les 44 scripts de scripts/ sont le dispositif d'enforcement du dépôt et aucun n'est testé, sauf le gate de couverture différentielle |
| `AUD-TST-03` | Moyenne | Tests | M | 300 des 831 assertThrows n'assertent qu'un type d'exception JDK générique, sans message ni code de rejet |
| `AUD-TST-06` | Moyenne | Tests | M | La colonne cli du manifeste de convergence n'est vérifiée que pour sa non-vacuité, sans gate bidirectionnel, contrairement à http et mcp |
| `AUD-TST-10` | Moyenne | Tests | M | Aucun test d'upgrade depuis une base réelle aux paliers de schéma 13 à 19 ; le seul vrai test d'upgrade part du palier 12 |
| `AUD-TST-08` | Faible | Tests | S | Le ratchet testsMinimum est dominé par la combinatoire : au moins 40 % des exécutions comptées viennent de 13 méthodes dans 3 fichiers |
| `AUD-TST-09` | Faible | Tests | S | Les quatre ratchets de couverture sont à moins de 0,41 point de leur plafond qualifié : plus de marge sans requalification CI |

## Sprint 9 — Supprimer la duplication du socle

**6 constats** · 6 Moyenne · charge 4.5 à 14 j

*Pourquoi ici* — Le thème le mieux soutenu quantitativement : un helper qui existe en six exemplaires dérive déjà.

*Critère de sortie* — Chaque helper du socle n'existe qu'une fois, et une copie privée est refusée par un test.

*Ordre interne* — AUD-QUA-07 avant AUD-ARC-10 ; AUD-QUA-07 avant AUD-QUA-09.

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-ARC-10` | Moyenne | Architecture | M | Projection domaine vers payload public dupliquée à l'identique entre api et mcp, sans gate sur les payloads |
| `AUD-QUA-02` | Moyenne | Qualité | M | 73 copies du localisateur de racine de dépôt dans les tests, sous 3 noms et 7 implémentations divergentes |
| `AUD-QUA-06` | Moyenne | Qualité | M | Chaque sous-commande CLI reporte sa propre copie du même socle d'analyse d'arguments |
| `AUD-QUA-07` | Moyenne | Qualité | S | Le constructeur de Map varargs est recopié 10 fois dans api et mcp, sous deux noms, avec un cast (String) non vérifié |
| `AUD-QUA-08` | Moyenne | Qualité | S | sha256, slug, readAllLines et joinContent sont recopiés dans les providers, avec deux messages d'erreur divergents |
| `AUD-QUA-09` | Moyenne | Qualité | M | MinosIntegrationSettings et NexusIntegrationSettings sont le même fichier à 90 %, avec une dérive déjà installée |

## Sprint 10 — Dire la vérité dans les règles

**7 constats** · 3 Moyenne · 4 Faible · charge 3.25 à 11 j

*Pourquoi ici* — Sept règles du dépôt sont fausses au sens où elles sont écrites, ou absentes là où un javadoc les annonce.

*Critère de sortie* — Aucune règle de .claude/rules/ ni aucun javadoc de test n'affirme un invariant que le code ne tient pas.

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-ARC-07` | Moyenne | Architecture | M | Le module morpheus-mcp-transport livre le paquet com.morpheus.integration.mcp, donc le transport partagé est réglé comme une intégration |
| `AUD-ARC-08` | Moyenne | Architecture | S | com.morpheus.sdk n'est sujet d'aucune règle ArchUnit, contrairement à ce qu'annonce le javadoc du test qui le nomme |
| `AUD-ARC-11` | Moyenne | Architecture | S | Interdiction absolue de la réflexion dans les règles du dépôt, contredite par quatre classes de production |
| `AUD-ARC-14` | Faible | Architecture | M | Huit constructeurs de compatibilité rétroactive alors que les règles du dépôt les interdisent |
| `AUD-DEP-13` | Faible | Dépendances | S | Version littérale 1.0.4 en dur dans un POM de module, contre la règle du dépôt |
| `AUD-DEP-14` | Faible | Dépendances | S | sqlite-jdbc et slf4j-nop sont versionnés par propriété dans le module au lieu du dependencyManagement racine |
| `AUD-QUA-11` | Faible | Qualité | S | Deux shims de compatibilité @Deprecated(forRemoval = true) vivent depuis deux mois sans plan de retrait |

## Sprint 11 — Trancher l'architecture

**4 constats** · 2 Élevée · 1 Moyenne · 1 Faible · charge 10 à 27 j

*Pourquoi ici* — Chantier de fond. Un ADR d'abord : c'est la décision dont dépendent les sprints 12 et 13.

*Critère de sortie* — Un ADR dit si le cœur applicatif est pur, les ports d'infrastructure existent ou l'interdit est étendu, et le compte de cycles commence à décroître.

*Ordre interne* — AUD-ARC-01 avant AUD-ARC-02 ; AUD-ARC-01 avant AUD-ARC-03 ; AUD-ARC-03 avant AUD-ARC-13.

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-ARC-01` | Élevée | Architecture | L | morpheus-application, la couche qui définit les ports, fait de l'I/O fichier et du HTTP sortant en direct |
| `AUD-ARC-02` | Élevée | Architecture | M | Le chemin d'ingestion principal n'a aucun port : api et cli instancient directement le lecteur OpenSpec concret |
| `AUD-ARC-03` | Moyenne | Architecture | L | morpheus-application contient une composante fortement connexe de 17 paquets sur 36 |
| `AUD-ARC-13` | Faible | Architecture | L | Domaine sans aucun type polymorphe : 59 records et 25 enums, zéro interface, pour 9,4 fois moins de lignes que l'application |

## Sprint 12 — Restructurer les adaptateurs

**3 constats** · 3 Moyenne · charge 7 à 19 j

*Pourquoi ici* — Rendre les frontières internes exprimables par la structure, pour que les gates cessent de maintenir des listes de noms.

*Critère de sortie* — Les listes nominatives de classes dans les gates sont remplacées par des règles de paquet, et la part textuelle de l'enforcement est plafonnée.

*Ordre interne* — AUD-ARC-06 avant AUD-ARC-09 ; AUD-ARC-06 avant AUD-TRV-03.

*Prérequis des sprints antérieurs* — AUD-ARC-01 (sprint 11).

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-ARC-06` | Moyenne | Architecture | M | morpheus-api : 80 classes dans un unique paquet plat, aucune frontière intra-module exprimable |
| `AUD-ARC-09` | Moyenne | Architecture | L | Découpage incohérent d'un adaptateur à l'autre : api décomposé par capacité, mcp et cli gardent un monolithe historique à côté |
| `AUD-TRV-03` | Moyenne | Tests · Architecture | L | L'enforcement repose majoritairement sur des assertions textuelles et non sur la structure du code |

## Sprint 13 — Couverture et pagination

**2 constats** · 2 Moyenne · charge 6 à 16 j

*Pourquoi ici* — Les deux chantiers longs qui ne deviennent mesurables qu'après les sprints 1 et 11.

*Critère de sortie* — L'écart de 20,9 points entre les deux échelles de couverture décroît sans requalifier un plafond, et la pagination est portée par le port.

*Prérequis des sprints antérieurs* — AUD-ARC-01 (sprint 11), AUD-TST-01 (sprint 1).

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-PRF-05` | Moyenne | Performance | L | Pagination de la recherche de requirements appliquée en mémoire après chargement intégral du snapshot |
| `AUD-TRV-02` | Moyenne | Tests · Qualité | L | Écart de 20,9 points entre les deux échelles de couverture : morpheus-application n'est pas testable sans ses modules voisins |

## Sprint 14 — Signer la distribution

**1 constats** · 1 Moyenne · charge 1 à 3 j

*Pourquoi ici* — Chemin critique externe, pas technique : à amorcer dès le sprint 2 (achat du certificat), à livrer quand il arrive.

*Critère de sortie* — Setup.exe et le launcher sont signés, et verify-release-provenance asserte présence et validité de la signature.

*Prérequis des sprints antérieurs* — AUD-DEP-12 (sprint 2).

| Constat | Sévérité | Axe | Effort | Objet |
|---|---|---|---|---|
| `AUD-DEP-05` | Moyenne | Dépendances | M | L'installeur Windows et les archives ne sont pas signés Authenticode, alors que le dépôt exige cette signature de son propre outillage |

---

## Hors sprint

Les 24 constats *Info* ne demandent aucune action. Ils documentent ce qui tient, et servent de référence pour détecter une régression au prochain audit — c'est leur seule raison d'être dans le rapport.

| Constat | Axe | Objet |
|---|---|---|
| `AUD-DEP-17` | Dépendances | Info : toutes les dépendances et plugins sauf reactor sont à la dernière version publiée à ce jour |
| `AUD-DEP-18` | Dépendances | Info : le build est entièrement épinglé et reproductible — aucune version flottante, aucun SNAPSHOT, aucun dépôt tiers, wrapper vérifié par empreinte |
| `AUD-DEP-19` | Dépendances | Info : chaîne CI/CD verrouillée — actions toutes épinglées par SHA 40, permissions minimales, secrets jamais exposés à du code non fusionné |
| `AUD-DEP-20` | Dépendances | Info : aucun Dockerfile ni compose dans le dépôt, conforme à la revendication « sans Docker » |
| `AUD-DEP-21` | Dépendances | Info : hygiène du dépôt — aucun artefact de build suivi par git, suppressions CVE et dérogations dependency:analyze toutes justifiées |
| `AUD-DEP-22` | Dépendances | Info : mesure de stabilité de ci.yml — 29 commits sur tout l'historique disponible, un seul sur les 200 derniers |
| `AUD-PRF-15` | Performance | Info : les bornes annoncées par le transport MCP sont effectivement appliquées |
| `AUD-PRF-16` | Performance | Info : hygiène des ressources JDBC sans faille détectée, et écritures du projecteur correctement batchées |
| `AUD-PRF-17` | Performance | Info : les parcours d'arborescence fournis par l'utilisateur sont bornés explicitement |
| `AUD-QUA-15` | Qualité | Info : 72 715 lignes de Markdown pour 65 181 lignes de Java main, dont 18 937 d'historique de milestones figé |
| `AUD-QUA-16` | Qualité | Info : hygiène de base sans défaut mesurable — ni marqueur de dette, ni trace avalée classique, ni fonction démesurée |
| `AUD-SEC-09` | Sécurité | Info : aucun secret en clair dans le code, les tests, les workflows, les scripts ou l'historique git |
| `AUD-SEC-10` | Sécurité | Info : aucune injection SQL — tout le SQL est paramétré et les rares identifiants dynamiques sont validés par expression régulière |
| `AUD-SEC-11` | Sécurité | Info : autorisation remote fail-closed à couverture exacte, comparaison de jeton à temps constant et révocation immédiate |
| `AUD-SEC-12` | Sécurité | Info : le serveur local est protégé contre le DNS rebinding et le CSRF navigateur, et l'enveloppe de protection est fail-closed |
| `AUD-SEC-13` | Sécurité | Info : aucune injection de commande — lancement par liste d'arguments sans shell, aucun interpréteur dynamique dans les scripts |
| `AUD-SEC-14` | Sécurité | Info : confinement des chemins fournis par l'utilisateur — allowlist canonicalisée, refus des liens symboliques, ré-vérification de la racine à chaque requête |
| `AUD-SEC-15` | Sécurité | Info : pas de SSRF — deux clients HTTP sortants seulement, sans suivi de redirection, et la découverte de mise à jour n'est pas exposée au modèle |
| `AUD-SEC-16` | Sécurité | Info : chaîne d'approvisionnement CI verrouillée, et suppressions de sécurité minimales et justifiées |
| `AUD-TST-13` | Tests | Info : hygiène de test remarquable — aucun test désactivé, aucun JUnit 4, aucun état statique mutable, aucun port fixe |
| `AUD-TST-14` | Tests | Info : les six chiffres normatifs sont identiques dans les onze destinations documentaires vérifiées |
| `AUD-TST-15` | Tests | Info : les vingt migrations SQLite sont épinglées par checksum golden, et le compte est lié à la constante de schéma |
| `AUD-TST-16` | Tests | Info : les chemins d'erreur HTTP sont assertés par code de statut sur plus de la moitié des assertions |
| `AUD-TST-17` | Tests | Info : les tests du gate de couverture différentielle sont le seul outillage exécutable sans Maven, et ils passent |

Cinq d'entre eux méritent d'être lus avant de commencer, parce qu'ils expliquent pourquoi plusieurs risques sont déjà bornés et ne doivent pas être « corrigés » par erreur : `AUD-PRF-15` (les bornes du transport MCP sont réellement appliquées), `AUD-PRF-16` (toutes les ressources JDBC sont en try-with-resources), `AUD-DEP-18` (le build est entièrement épinglé et reproductible), `AUD-DEP-19` (les 18 actions GitHub sont épinglées par SHA et `SONAR_TOKEN` ne partage jamais son environnement avec du code de PR), `AUD-TST-15` (les 20 migrations SQLite sont épinglées par checksum golden).

## Rapport à `plan-action.md`

`plan-action.md` classe les mêmes constats en trois temps — *Immédiat*, *Court terme*, *Fond* — c'est-à-dire par urgence. Ce document les classe par **ordre d'exécution**, c'est-à-dire par dépendance. Les deux ne se contredisent pas : les sprints 1 à 5 recouvrent l'*Immédiat* et le début du *Court terme*, les sprints 6 à 10 le reste du *Court terme*, les sprints 11 à 14 le *Fond*. Quand les deux divergent, c'est l'ordre des sprints qui s'applique : une action urgente dont le prérequis n'est pas livré ne peut pas être vérifiée.

Le découpage est aussi fait pour la façon dont ce dépôt travaille : un sprint est un lot de constats pour lequel un prompt d'implémentation unique a du sens, avec un agent d'implémentation et un agent de supervision en parallèle, et qui finit par l'ouverture des PR. Les sprints 1, 3, 7 et 10 sont les plus adaptés à ce format — beaucoup de constats courts et indépendants. Les sprints 11 à 13 ne le sont pas : chacun demande un ADR avant toute implémentation.
