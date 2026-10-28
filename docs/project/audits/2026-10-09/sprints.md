# Sprints de correction
État au **28 octobre 2026** sur `6c6a97f6` (`origin/develop`) pour les sprints 7 et 8 et sur la branche `fix/finish-sprints-1-5` pour les sprints 1 et 5, au **26 octobre 2026** sur `4694b8b2` pour les autres, après un `./mvnw clean verify` vert sur les 19 modules. L'ordre et le découpage sont ceux du 9 octobre ; seuls les statuts ont été réétablis, constat par constat, sur le code.

**Plan du 9 octobre : 73 constats, 55 soldés, 18 restants.** Les sprints 1, 2, 3, 4, 5, 7 et 8 sont terminés ; le sprint 6 garde `AUD-PRF-04`. Le **sprint 12** n'a plus qu'un constat ouvert sur trois, et le **sprint 11** trois sur quatre : les trois ADR du 25 octobre l'ont tranché sur le papier, mais seul `AUD-ARC-02` est refermé dans le code. Les sprints 9, 13 et 14 n'ont pas commencé.

**Sprint 15 — dérives : 13 constats, 9 restants.** Ce sprint n'est pas du plan d'origine : il rassemble les constats nés *après* l'audit, d'une scission, d'un effet de bord d'un correctif ou du changement de branche d'intégration. Les ranger dans les sprints d'origine faisait croire que ceux-ci traînaient depuis le 9 octobre ; ils sont soldés, et du travail neuf est apparu à côté.

## Avancement

| Sprint | Objet | Constats | Soldés | Restants | Charge restante |
|---|---|---|---|---|---|
| **1** ✓ | Remettre le dispositif en marche | 8 | 8 | 0 | — |
| **2** ✓ | Débloquer la release 1.2.1 | 6 | 6 | 0 | — |
| **3** ✓ | Fermer les écarts de sécurité | 8 | 8 | 0 | — |
| **4** ✓ | Rendre le produit observable | 5 | 5 | 0 | — |
| **5** ✓ | Écarter le risque d'indisponibilité | 4 | 4 | 0 | — |
| **6** | Borner les coûts qui croissent avec les données | 4 | 3 | 1 | 1 à 3 j |
| **7** ✓ | Le SQL de détail | 7 | 7 | 0 | — |
| **8** ✓ | Fiabiliser les tests | 8 | 8 | 0 | — |
| **9** | Supprimer la duplication du socle | 6 | 0 | 6 | 4.5 à 14 j |
| **10** | Dire la vérité dans les règles | 7 | 3 | 4 | 1.75 à 6 j |
| **11** | Trancher l'architecture | 4 | 1 | 3 | 9 à 24 j |
| **12** | Restructurer les adaptateurs | 3 | 2 | 1 | 3 à 8 j |
| **13** | Couverture et pagination | 2 | 0 | 2 | 6 à 16 j |
| **14** | Signer la distribution | 1 | 0 | 1 | 1 à 3 j |
| **15** | Dérives apparues pendant les correctifs | 13 | 4 | 9 | 4.5 à 15 j |
| | **Total** | **86** | **59** | **27** | **30.75 à 89 j** |

Charge restante dérivée des efforts par constat (S 0,25 à 1 j · M 1 à 3 j · L 3 à 8 j). Un constat `partiel` compte pour son effort entier : ce qui reste d'un constat partiellement traité n'est pas proportionnel à ce qui en a été fait.

---

## Sprint 1 — Remettre le dispositif en marche  ·  **terminé**

8 constats · 8 corrigé

*Pourquoi ici* — Rien ne se corrige de façon mesurable tant que les gates ne tournent pas. Ce sprint ne touche aucun code applicatif.

*Critère de sortie* — Tout gate déclaré par le dépôt est atteignable par une invocation réelle du dépôt, et aucun validateur ne rend PASS sur une valeur périmée.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-TST-01` | corrigé | Élevée | S | Les cinq budgets de performance M19 ne sont exécutés par aucun pipeline ni par clean verify |
| `AUD-DEP-06` | corrigé | Moyenne | S | validate-d2 ne lit pas le fichier de ratchets et code en dur un plancher de 820/258 tests, contrairement à ce que la règle du dépôt affirme |
| `AUD-DEP-07` | corrigé | Moyenne | M | Le gate CVE a un point de défaillance unique : Dependency-Check 13.0.0 ne peut pas rafraîchir la base NVD sans NVD_API_KEY |
| `AUD-TRV-01` | corrigé | Moyenne | M | Les quatre validateurs M15 à M18 sont inexécutables, sans équivalent .sh, et les commandes documentées pour les rejouer n'existent pas |
| `AUD-TST-02` | corrigé | Moyenne | S | Le gate de couverture par module accepte un rapport JaCoCo périmé : aucun contrôle de fraîcheur |
| `AUD-QUA-14` | corrigé | Faible | S | SpotBugs et PIT sont épinglés, configurés et documentés mais lancés par aucun workflow |
| `AUD-TST-11` | corrigé | Faible | S | Nom de test périmé et version de schéma recopiée en dur trois fois dans le test de rejeu des migrations |
| `AUD-TST-12` | corrigé | Faible | S | La documentation M19 renvoie à un point d'entrée validate-m19.cmd qui n'existe pas |

## Sprint 2 — Débloquer la release 1.2.1  ·  **terminé**

6 constats · 6 corrigé

*Pourquoi ici* — Le seul cluster de l'audit dont la matérialisation est irréversible pour un utilisateur. À faire avant de taguer.

*Critère de sortie* — v1.2.1 peut être taguée : procédure d'upgrade écrite, sauvegarde impérative, attributions de licence et SBOM publiées, chaîne de release exercée de bout en bout.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-DEP-01` | corrigé | Élevée | M | La 1.2.1 non publiée ajoute 5 migrations SQLite (V016 à V020) et aucun document d'upgrade ou de retour arrière ne les couvre |
| `AUD-DEP-03` | corrigé | Moyenne | S | La distribution d'un produit sous licence propriétaire n'embarque aucune attribution de licence tierce |
| `AUD-DEP-04` | corrigé | Moyenne | S | La SBOM est produite à chaque verify et exigée par les validateurs, mais n'est jamais publiée avec la release |
| `AUD-DEP-12` | corrigé | Moyenne | M | pom.xml à 1.2.1 sans tag v1.2.1 : la chaîne de release signée n'a pas tourné depuis 1 588 commits et 2,3 mois |
| `AUD-DEP-15` | corrigé | Faible | S | MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR, qui désigne le répertoire de plugins exécutables du serveur remote, n'est documentée nulle part |
| `AUD-DEP-16` | corrigé | Faible | M | Aucune sauvegarde automatique avant migration de schéma : la procédure repose sur la discipline de l'opérateur |

## Sprint 3 — Fermer les écarts de sécurité  ·  **terminé**

8 constats · 8 corrigé

*Pourquoi ici* — Huit corrections petites et indépendantes, dont la seule divulgation établie vers un appelant hors machine.

*Critère de sortie* — Plus aucun message d'exception brut ne sort vers un appelant distant, et plus aucune branche inatteignable sur un chemin de sécurité.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-SEC-02` | corrigé | Élevée | S | Quatre routeurs HTTP qui enregistrent leur propre contexte renvoient le message d'exception brut, sans passer par le filtre de divulgation de chemins serveur |
| `AUD-DEP-02` | corrigé | Moyenne | S | reactor-bom figé à 2024.0.0 : 18 correctifs de retard dans la ligne même que le POM impose, et dependabot ne peut pas proposer ce correctif |
| `AUD-QUA-05` | corrigé | Moyenne | S | 16 méthodes publiques ne sont référencées nulle part, tests compris |
| `AUD-SEC-03` | corrigé | Moyenne | M | Les segments de chemin HTTP sont décodés deux fois côté local, alors que l'autorisation remote les décode une seule fois |
| `AUD-TRV-06` | corrigé | Moyenne | S | Trois branches inatteignables sur des chemins de sécurité, dont deux sur l'exécution de code tiers |
| `AUD-SEC-05` | corrigé | Faible | S | La commande Java des pairs MCP est résolue via le PATH plutôt que par un chemin absolu |
| `AUD-SEC-06` | corrigé | Faible | S | En-têtes de sécurité incomplets : pas de X-Frame-Options ni de CSP sur les réponses de succès locales, pas de HSTS sur la façade remote |
| `AUD-SEC-07` | corrigé | Faible | S | Aucune règle .gitignore ne protège le fichier d'identités remote ni le keystore TLS, que SECURITY.md interdit de committer |

## Sprint 4 — Rendre le produit observable  ·  **terminé**

5 constats · 5 corrigé

*Pourquoi ici* — Sans trace, les sprints suivants corrigent à l'aveugle et aucun incident n'est reconstituable.

*Critère de sortie* — Un échec sur un chemin SQLite, MCP ou d'adaptateur laisse une trace exploitable, et aucune variable d'observabilité ne retombe en silence.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-TRV-04` | corrigé | Élevée | M | Le produit est muet : aucun journal applicatif, slf4j-nop comme seul binding, et un répertoire de logs publié sans aucun écrivain |
| `AUD-DEP-10` | corrigé | Moyenne | S | MORPHEUS_OPERATIONAL_LOGS retombe silencieusement sur noop pour toute valeur non reconnue |
| `AUD-SEC-04` | corrigé | Moyenne | M | Aucune trace d'attribution : le principal authentifié d'une requête remote n'est enregistré nulle part |
| `AUD-QUA-12` | corrigé | Faible | S | 47 @SuppressWarnings dont environ cinq portent une justification écrite, alors que les exclusions SpotBugs en exigent une |
| `AUD-QUA-13` | corrigé | Faible | M | L'observabilité repose sur un singleton statique mutable qui interdit d'activer le parallélisme JUnit |

## Sprint 5 — Écarter le risque d'indisponibilité  ·  **terminé**

4 constats · 3 corrigé · 1 caduc

*Pourquoi ici* — Le chemin vers l'indisponibilité du serveur entier, et la politique de connexion qui le sous-tend.

*Critère de sortie* — Une écriture concurrente n'affame plus l'ordonnanceur de threads virtuels, et un pic de charge devient une file d'attente au lieu d'échecs SQLITE_BUSY.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-PRF-18` | caduc | Élevée | M | Épinglage des threads virtuels : 94 méthodes public synchronized faisant du JDBC bloquant, atteintes depuis les serveurs HTTP, sur une baseline Java 21 |
| `AUD-TRV-05` | corrigé | Élevée | M | La politique de connexion SQLite est une propriété de chaque site de composition, pas du module : 12 sites, 3 ouvertures de scope |
| `AUD-PRF-03` | corrigé | Moyenne | M | Serveur HTTP local sans contrôle d'admission : threads virtuels illimités, une connexion SQLite physique par requête, journal PERSIST |
| `AUD-PRF-09` | corrigé | Moyenne | S | Le proxy remote n'impose aucun délai de lecture amont sur les routes WRITE et ADMIN |

## Sprint 6 — Borner les coûts qui croissent avec les données

4 constats · 3 corrigé · 1 partiel

*Pourquoi ici* — Les quatre chemins dont le coût croît avec le volume, mesurables seulement après le sprint 1.

*Critère de sortie* — Aucun chemin public ne charge un volume non borné, et chaque correction est mesurée contre son budget M19.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-PRF-01` | corrigé | Élevée | M | N+1 SQL dans la lecture du contenu métier d'un snapshot : 7 requêtes filles par ligne parente |
| `AUD-PRF-02` | corrigé | Élevée | M | ChangeAnalysisService refait un BFS complet par nœud déjà découvert par la traversée |
| `AUD-PRF-04` | partiel | Élevée | M | Trois tables en croissance monotone lues sans LIMIT ni pagination, dont une exposée en HTTP et en MCP |
| `AUD-PRF-06` | corrigé | Moyenne | M | Écriture N+1 des liens de traçabilité : trois à quatre prepareStatement par lien, sans batch, dans une transaction unique |

## Sprint 7 — Le SQL de détail  ·  **terminé**

7 constats · 7 corrigé

*Pourquoi ici* — Même couche que le sprint 6, volontairement après lui pour éviter les conflits de fusion sur les mêmes fichiers.

*Critère de sortie* — Chaque colonne filtrée ou triée est servie par un index, vérifié par EXPLAIN QUERY PLAN avant modification.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-PRF-07` | corrigé | Moyenne | S | Onze index redondants dupliquant l'index implicite d'une clé primaire (l'audit en annonçait six) |
| `AUD-PRF-08` | corrigé | Moyenne | M | La vérification de frontière du workspace fait un toRealPath par segment, deux fois par fichier scanné |
| `AUD-PRF-10` | corrigé | Moyenne | S | Le seul SELECT paginé du module trie sur une colonne que son index ne couvre pas |
| `AUD-PRF-11` | corrigé | Faible | S | ORDER BY name COLLATE NOCASE sur saved_views ne peut pas utiliser l'index BINARY qui porte la colonne |
| `AUD-PRF-12` | corrigé | Faible | S | Le filtre de type de relation des liens de traçabilité est appliqué en Java alors que l'index le porte |
| `AUD-PRF-13` | corrigé | Faible | S | Deux lectures triées en SQL puis retriées en Java |
| `AUD-PRF-14` | corrigé | Faible | S | Aucun cache de lecture de document : chaque lecture reparcourt le texte deux fois de plus et consomme le budget |

## Sprint 8 — Fiabiliser les tests  ·  **terminé**

8 constats · 8 corrigé

*Pourquoi ici* — Ce qui empêche la suite de détecter une régression, une fois le dispositif remis en marche.

*Critère de sortie* — Un test de rejet asserte son motif et non seulement son type, les validateurs sont eux-mêmes testés, et la colonne CLI du manifeste a son gate.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-QUA-04` | corrigé | Moyenne | S | Dérive documentation/code sur le nombre de modules : 18 déclarés, 17 annoncés par quatre pages vivantes |
| `AUD-TRV-07` | corrigé | Moyenne | M | Les 44 scripts de scripts/ sont le dispositif d'enforcement du dépôt et aucun n'est testé, sauf le gate de couverture différentielle |
| `AUD-TST-03` | corrigé | Moyenne | M | 300 des 831 assertThrows n'assertent qu'un type d'exception JDK générique, sans message ni code de rejet |
| `AUD-TST-06` | corrigé | Moyenne | M | La colonne cli du manifeste de convergence n'est vérifiée que pour sa non-vacuité, sans gate bidirectionnel, contrairement à http et mcp |
| `AUD-TST-10` | corrigé | Moyenne | M | Aucun test d'upgrade depuis une base réelle aux paliers de schéma 13 à 19 ; le seul vrai test d'upgrade part du palier 12 |
| `AUD-TST-19` | corrigé | Moyenne | S | Le ratchet des tests d'architecture a cent huit exécutions de jeu : il est resté à 585 alors que la mesure du lot donnait 693 |
| `AUD-TST-08` | corrigé | Faible | S | Le ratchet testsMinimum est dominé par la combinatoire : au moins 40 % des exécutions comptées viennent de 13 méthodes dans 3 fichiers |
| `AUD-TST-09` | corrigé | Faible | S | Les quatre ratchets de couverture sont à moins de 0,41 point de leur plafond qualifié : plus de marge sans requalification CI |

## Sprint 9 — Supprimer la duplication du socle

6 constats · 1 partiel · 5 toujours valide

*Pourquoi ici* — Le thème le mieux soutenu quantitativement : un helper qui existe en six exemplaires dérive déjà.

*Critère de sortie* — Chaque helper du socle n'existe qu'une fois, et une copie privée est refusée par un test.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-ARC-10` | toujours valide | Moyenne | M | Projection domaine vers payload public dupliquée à l'identique entre api et mcp, sans gate sur les payloads |
| `AUD-QUA-02` | toujours valide | Moyenne | M | 73 copies du localisateur de racine de dépôt dans les tests, sous 3 noms et 7 implémentations divergentes |
| `AUD-QUA-06` | toujours valide | Moyenne | M | Chaque sous-commande CLI reporte sa propre copie du même socle d'analyse d'arguments |
| `AUD-QUA-07` | partiel | Moyenne | S | Le constructeur de Map varargs est recopié 10 fois dans api et mcp, sous deux noms, avec un cast (String) non vérifié |
| `AUD-QUA-08` | toujours valide | Moyenne | S | sha256, slug, readAllLines et joinContent sont recopiés dans les providers, avec deux messages d'erreur divergents |
| `AUD-QUA-09` | toujours valide | Moyenne | M | MinosIntegrationSettings et NexusIntegrationSettings sont le même fichier à 90 %, avec une dérive déjà installée |

## Sprint 10 — Dire la vérité dans les règles

7 constats · 3 corrigé · 1 partiel · 3 toujours valide

*Pourquoi ici* — Sept règles du dépôt sont fausses au sens où elles sont écrites, ou absentes là où un javadoc les annonce.

*Critère de sortie* — Aucune règle de .claude/rules/ ni aucun javadoc de test n'affirme un invariant que le code ne tient pas.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-ARC-07` | toujours valide | Moyenne | M | Le module morpheus-mcp-transport livre le paquet com.morpheus.integration.mcp, donc le transport partagé est réglé comme une intégration |
| `AUD-ARC-08` | toujours valide | Moyenne | S | com.morpheus.sdk n'est sujet d'aucune règle ArchUnit, contrairement à ce qu'annonce le javadoc du test qui le nomme |
| `AUD-ARC-11` | partiel | Moyenne | S | Interdiction absolue de la réflexion dans les règles du dépôt, contredite par trois classes de production |
| `AUD-ARC-14` | corrigé | Faible | M | Huit constructeurs de compatibilité rétroactive alors que les règles du dépôt les interdisent |
| `AUD-DEP-13` | corrigé | Faible | S | Version littérale 1.0.4 en dur dans un POM de module, contre la règle du dépôt |
| `AUD-DEP-14` | corrigé | Faible | S | sqlite-jdbc et slf4j-nop sont versionnés par propriété dans le module au lieu du dependencyManagement racine |
| `AUD-QUA-11` | toujours valide | Faible | S | Deux shims de compatibilité @Deprecated(forRemoval = true) vivent depuis deux mois sans plan de retrait |

## Sprint 11 — Trancher l'architecture

4 constats · 1 corrigé · 1 partiel · 2 toujours valide

*Pourquoi ici* — Chantier de fond. Un ADR d'abord : c'est la décision dont dépendent les sprints 12 et 13.

*Critère de sortie* — Un ADR dit si le cœur applicatif est pur, les ports d'infrastructure existent ou l'interdit est étendu, et le compte de cycles commence à décroître.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-ARC-01` | partiel | Élevée | L | morpheus-application, la couche qui définit les ports, fait de l'I/O fichier et du HTTP sortant en direct |
| `AUD-ARC-02` | corrigé | Élevée | M | Le chemin d'ingestion principal n'a aucun port : api et cli instancient directement le lecteur OpenSpec concret |
| `AUD-ARC-03` | toujours valide | Moyenne | L | morpheus-application contient une composante fortement connexe de 17 paquets sur 36 |
| `AUD-ARC-13` | toujours valide | Faible | L | Domaine sans aucun type polymorphe : 59 records et 25 enums, zéro interface, pour 9,4 fois moins de lignes que l'application |

## Sprint 12 — Restructurer les adaptateurs

3 constats · 2 corrigé · 1 partiel

*Pourquoi ici* — Rendre les frontières internes exprimables par la structure, pour que les gates cessent de maintenir des listes de noms.

*Critère de sortie* — Les listes nominatives de classes dans les gates sont remplacées par des règles de paquet, et la part textuelle de l'enforcement est plafonnée.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-ARC-06` | corrigé | Moyenne | M | morpheus-api : 80 classes dans un unique paquet plat, aucune frontière intra-module exprimable |
| `AUD-ARC-09` | corrigé | Moyenne | L | Découpage incohérent d'un adaptateur à l'autre : api décomposé par capacité, mcp et cli gardent un monolithe historique à côté |
| `AUD-TRV-03` | partiel | Moyenne | L | L'enforcement repose majoritairement sur des assertions textuelles et non sur la structure du code |

## Sprint 13 — Couverture et pagination

2 constats · 2 toujours valide

*Pourquoi ici* — Les deux chantiers longs qui ne deviennent mesurables qu'après les sprints 1 et 11.

*Critère de sortie* — L'écart de 20,9 points entre les deux échelles de couverture décroît sans requalifier un plafond, et la pagination est portée par le port.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-PRF-05` | toujours valide | Moyenne | L | Pagination de la recherche de requirements appliquée en mémoire après chargement intégral du snapshot |
| `AUD-TRV-02` | toujours valide | Moyenne | L | Écart de 20,9 points entre les deux échelles de couverture : morpheus-application n'est pas testable sans ses modules voisins |

## Sprint 14 — Signer la distribution

1 constats · 1 toujours valide

*Pourquoi ici* — Chemin critique externe, pas technique : à amorcer dès le sprint 2 (achat du certificat), à livrer quand il arrive.

*Critère de sortie* — Setup.exe et le launcher sont signés, et verify-release-provenance asserte présence et validité de la signature.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-DEP-05` | toujours valide | Moyenne | M | L'installeur Windows et les archives ne sont pas signés Authenticode, alors que le dépôt exige cette signature de son propre outillage |

## Sprint 15 — Dérives apparues pendant les correctifs

13 constats · 2 corrigé · 2 partiel · 7 toujours valide · 2 caduc

*Pourquoi ici* — Ce sprint n'est pas du plan du 9 octobre : il rassemble les constats nés **après** l'audit, soit d'une scission quand la mesure du dépôt a montré que ma cause était fausse, soit d'un effet de bord d'un correctif, soit du changement de branche d'intégration du 18 octobre. Les ranger dans les sprints d'origine faisait croire que ceux-ci traînaient depuis le 9 octobre, ce qui est faux : ils sont soldés, et du travail neuf est apparu à côté. La passe du 26 octobre en ajoute quatre, tous nés de la grande restructuration des adaptateurs : l'élargissement de visibilité qu'elle a imposé, un test de garde qui ne garde rien, la liste d'ADR-0148 recopiée à la main dans son gate, et deux dérives de la trace de suivi.

*Critère de sortie* — Aucun constat né pendant les correctifs ne reste ouvert, et chacun a soit une règle exécutable qui l'empêche de revenir, soit une décision écrite qui l'assume.

| Constat | Statut | Sév. | Eff. | Objet |
|---|---|---|---|---|
| `AUD-ARC-16` | toujours valide | Moyenne | M | La restructuration de morpheus-store-sqlite a élargi la surface publique du module : trois types et onze membres passés de package-private à public, la garde du compilateur remplacée par une règle |
| `AUD-DEP-23` | caduc | Moyenne | S | Les huit voies nocturnes analysent `develop`, qui n'est pas la branche d'intégration, et que rien ne garantit à jour |
| `AUD-DEP-24` | caduc | Moyenne | S | Le basculement vers main n'est répercuté ni dans Dependabot, ni dans les sept validateurs, ni dans la documentation du modèle de branches |
| `AUD-DEP-25` | toujours valide | Moyenne | S | Le produit s'annonce 1.2.1 alors qu'il migre la base en V022, et aucun gate ne relie la version du réacteur au schéma |
| `AUD-PRF-19` | partiel | Moyenne | M | Le serveur remote sert toujours sur threads virtuels nus, et sa route de sauvegarde appelle SQLite directement |
| `AUD-QUA-17` | partiel | Moyenne | M | Le verrou du parallelisme JUnit se deplace : SqliteConnectionScope porte quatre compteurs statiques de processus lus en difference par les tests |
| `AUD-SEC-17` | toujours valide | Moyenne | S | L'optimisation de la frontière du workspace a rétréci le contrôle avant lecture : un fichier peut être lu et empreinté avant d'être refusé |
| `AUD-TST-18` | corrigé | Moyenne | S | Le validateur de release R3 ne peut pas passer : il exige 17 POM dans le reacteur, le depot en compte 19 |
| `AUD-TST-20` | toujours valide | Moyenne | S | Le test censé prouver que la règle anti-lecture-non-bornée voit ce qu'elle refuse reste vert même si la règle devient aveugle |
| `AUD-ARC-15` | toujours valide | Faible | S | Les 94 méthodes public synchronized de morpheus-store-sqlite n'ont aucune raison d'être documentée |
| `AUD-ARC-17` | toujours valide | Faible | S | La liste de onze classes d'ADR-0148 et les constantes du gate qui la tient sont deux listes recopiées à la main, que rien ne compare |
| `AUD-DEP-26` | toujours valide | Faible | S | La trace de suivi ne décrit plus l'état réelle des gates : un risque clos qui sert encore de renvoi, un guide d'upgrade pour une version que le réacteur ignore |
| `AUD-QUA-18` | corrigé | Faible | S | Le parallelisme JUnit est active sur le module ou une portee mal fermee empoisonne un thread du pool, sans aucun filet |
