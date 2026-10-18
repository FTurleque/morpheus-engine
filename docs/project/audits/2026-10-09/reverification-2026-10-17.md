# Vérification du 17 octobre 2026 — les sept constats portés à « corrigé »

Audit du 9 octobre, revérifié le 13 octobre, le 15 octobre, puis le **17 octobre sur `ae368aa3`**
(`origin/develop`), après 54 commits et les PR #493 à #506 — dont la promotion de **v1.2.1** sur `main`.

Entre-temps, le mainteneur a lui-même porté **sept constats à `corrigé`** dans `findings.json`. Cette passe
ne répond qu'à une question : **est-ce que le code le confirme ?** La règle qui gouverne tout ce travail
depuis le 9 octobre s'applique à ces sept lignes comme à n'importe quelle autre : une ligne du fichier
d'audit est une revendication, pas une preuve.

## Réponse

**Cinq sur sept sont confirmés. Deux ne le sont pas**, pour des raisons différentes — et l'un des deux
révèle une erreur dans mon audit d'origine, pas dans le travail du dépôt.

| Constat | Sév. | Revendiqué | Vérifié | |
|---|---|---|---|---|
| `AUD-TRV-04` | Élevée | corrigé | **corrigé** | ✓ |
| `AUD-TRV-05` | Élevée | corrigé | **corrigé** | ✓ |
| `AUD-TST-12` | Faible | corrigé | **corrigé** | ✓ |
| `AUD-TST-18` | Moyenne | corrigé | **corrigé** | ✓ |
| `AUD-QUA-13` | Faible | corrigé | **corrigé** | ✓ |
| `AUD-QUA-17` | Moyenne | corrigé | **partiel** | ✗ |
| `AUD-DEP-12` | Moyenne | corrigé | **corrigé**, avec un erratum | ⚠ |

Un constat neuf est né de cette passe : `AUD-QUA-18`.

**Notes** : Sécurité 100 · Dépendances 87 · Tests 69 · Qualité 66 · Performance 52 · Architecture 46.
**Moyenne 63 → 70.** Sprints 2 et 3 terminés. Il ne reste que **quatre constats Élevée**, tous dans les deux
axes où rien n'a jamais pu être exécuté : `AUD-ARC-01`, `AUD-ARC-02`, `AUD-PRF-02`, `AUD-PRF-04`.

---

## Les cinq confirmés

### `AUD-TRV-05` — la politique de connexion SQLite

Le plus gros morceau, et il tient. `SqliteOperationRuntime` est désormais **la seule classe de production
qui ouvre la portée et construit les stores**, pour les trois transports ; `SqliteQueryRuntime` et
`SqlitePolicyRuntime` sont supprimés du dépôt, pas vidés. La propriété que le constat nommait — un seul type
runtime par transport — est enfin vraie.

J'ai refait les quatre comptages revendiqués : **5 / 3 / 1 / 2**. Tous exacts.

Et l'angle mort que je signalais le 15 octobre est fermé : la règle bytecode exige maintenant l'**appel** à
`SqliteConnectionScope.open`, non plus une simple dépendance ; la règle textuelle ignore les commentaires et
exige l'ouverture **avant** la première construction.

Une nuance sur la communication des chiffres : *« COMPOSITION_ROOTS 32 → 5 »* ne mesure pas ce que le libellé
suggère. La règle compte les classes d'`api` et `mcp` qui *dépendent d'un type* de store ; le chiffre est
tombé parce que les runtimes masquent les types SQLite derrière des ports, pas parce qu'il y a moins
d'endroits qui montent un runtime — il y en a 49 pour `ApiRuntime` seul. Ce qui est réellement centralisé,
et c'est l'objet du constat, c'est la politique de connexion.

### `AUD-TRV-04` — le produit muet

Les deux `catch` DEBUG du transport — le dernier angle mort que je signalais — ont disparu : ils sont fondus
en une méthode dont l'unique `catch` remet l'échec à un observateur **obligatoire des deux constructeurs**,
sans no-op par défaut. Plus aucun `Level.DEBUG` dans le fichier, et l'interdit est exécuté par une règle.

Les deux maillons qui n'étaient tenus que par du texte sont maintenant testés : deux tests de passerelle
lisent `failure.suppressed.mcp-peer-process-tree` dans le snapshot du recorder, et un test de façade remote
démarre un vrai serveur TLS, provoque un 500 et lit le compteur en rôle ADMIN.

Recompte des `catch` muets : 13 occurrences, 11 clés, correspondance exacte avec la table `EXPLAINED`, et
**zéro nouveau site muet** sur les 54 commits.

### `AUD-TST-18` — le validateur R3

Le piège était là, et il a été évité. Le littéral 17 a disparu des deux variantes, et le compte dérivé est
**juste** : `1 + count('<module>')` = 1 + 18 = 19, exactement le nombre de `pom.xml` de l'arbre. Le `+ 1` du
POM racine est bien présent des deux côtés — c'est l'erreur qu'un correctif pressé aurait commise.

Le contrat attrape réellement un retour en arrière : vérifié empiriquement en remettant le littéral 17 dans
le script, ce qui fait échouer deux cas, puis en le restaurant.

### `AUD-TST-12` — la documentation M19

La règle balaie désormais `docs/architecture/adr/` et n'accepte qu'une note `> Note du JJ/MM/AAAA` placée
**avant la première section `##`** : l'écart entre ce que le README annonçait et ce que la règle appliquait
est résorbé. L'ADR-0084 porte sa note, et le `git show` qu'elle cite résout.

Pour `M19_EXECUTION.md`, c'est une **clôture par décision** : le chemin périmé est toujours là, mais il était
le bon à sa date, une note de tête renvoie à la table de correspondance, et les deux SHA cités disent bien ce
qu'on leur fait dire. Une clôture par décision est légitime quand la décision est écrite et vérifiable ; elle
l'est.

### `AUD-QUA-13` — le singleton et le parallélisme

Soldé sur la substance : le parallélisme JUnit est réellement activé sur `morpheus-store-sqlite`, avec un
`junit-platform.properties` versionné, un `@Isolated` motivé sur le seul test qui lit un état de la JVM, et
aucune contradiction dans surefire. Les utilisateurs du runtime statique tombent de 20 à **6**.

La demande littérale — « faire tomber `MAIN_USERS` à zéro » — n'est pas tenue, mais **deux de ces six ne
peuvent pas l'être** : les points d'entrée doivent bien prendre le runtime quelque part, et
`OperationalSlf4jServiceProvider` est instancié par le `ServiceLoader`. La demande était en partie
irréalisable telle que je l'avais écrite.

---

## Le constat qui ne tient pas : `AUD-QUA-17`

La moitié nommée du constat est faite : `SqliteConnectionScope` n'a plus aucun compteur statique mutable,
`diagnostics()` est supprimé, et les dix classes de test qui le lisaient sont tombées à zéro.

Mais le constat ne portait pas sur une classe, il portait sur une **classe de défaut** : un état de processus
lu en différence par des tests. Et elle n'a pas disparu — elle s'est déplacée dans
**`PhysicalSqliteConnections`**, un pilote JDBC compteur recopié à l'identique dans trois modules, qui
déregistre le driver SQLite du `DriverManager` global de la JVM, met un compteur à sa place et le restaure.
C'est exactement la même forme, et c'est précisément pour lui que `morpheus-api`, `morpheus-cli` et
`morpheus-mcp` restent séquentiels — c'est-à-dire sept des dix classes qui lisaient `diagnostics()`.

**Le dépôt est plus honnête que la revendication** : ce résidu est explicitement déclaré dans l'ADR-0115,
second amendement du 15 octobre. C'est le résumé de PR recopié dans le fichier d'audit qui conclut sans
réserve. L'écart est entre le relevé et l'ADR, pas entre l'ADR et le code.

---

## L'erratum : `AUD-DEP-12` contenait une affirmation fausse, de moi

Le constat disait deux choses. La première était vraie et elle est corrigée : `main` n'était jamais promue,
la question « sur quelle branche taguer » était bloquante. v1.2.1 a été coupée le 16 octobre sur `main` à
`2a8b1ede`, et le tag est à sa pointe.

La seconde était **fausse**. J'affirmais que le retard de `main` *privait l'alerte CVE de son déclencheur
quotidien*. Un `schedule` GitHub s'arme depuis la branche par défaut, et `security.yml` était déjà présent
sur `main` **avant** la promotion, avec son `cron '17 4 * * *'` — vérifié sur `origin/main^1`, daté du
13 octobre, et présent depuis bien plus tôt. Un `main` en retard aurait fait tourner une version plus
ancienne du workflow ; il n'aurait jamais supprimé le déclencheur. **L'alerte n'a pas été interrompue, et le
dépôt n'avait rien à réparer de ce côté.**

Deux leçons de méthode, à retenir pour les passes suivantes. Les tags ne sont pas récupérés dans ce clone :
`git tag -l` ne montrait que trois versions, et seul `git ls-remote --tags origin` révèle `v1.2.1` — un
contrôle fondé sur la première commande aurait conclu à tort que le tag n'existe pas. Et l'API GitHub est
refusée dans cette session : la release publiée, ses douze assets et le run de provenance sont consignés dans
le dépôt mais **n'ont pas pu être lus** ici.

---

## Le constat neuf : `AUD-QUA-18`

Le parallélisme JUnit vient d'être activé sur `morpheus-store-sqlite` — c'est le résultat voulu
d'`AUD-QUA-13` — mais il rend atteignable un risque qui dormait, et aucun filet ne le couvre.

`SqliteConnectionScope` garde un `ThreadLocal<State> ACTIVE`, et toute seconde portée sur le même thread est
refusée. Or les tests tournent maintenant sur les threads réutilisés d'un `ForkJoinPool`.
`SqliteConnectionScopeThreadConfinementTest:105` ouvre une portée **hors try-with-resources** et ne la ferme
qu'après trois assertions. Si l'une échoue, le `ThreadLocal` reste posé et le test suivant qui atterrit sur
ce thread se voit refuser sa portée — un échec en cascade qui envoie le lecteur enquêter sur le mauvais test.

Le danger est nommé depuis le 13 octobre dans la table de l'état partagé de l'ADR-0115, à une ligne qui n'est
marquée traitée nulle part. Et le nombre de threads n'est pas versionné : sans `config.strategy`, le
parallélisme suit `availableProcessors()`, donc une course invisible sur un portable à 2 cœurs peut casser
sur un runner à 16.

Trois gestes le ferment, du moins cher au plus solide : mettre cette portée dans un try-with-resources ; une
extension JUnit qui vérifie après chaque test qu'aucune portée ne reste attachée, et échoue en nommant le
test fautif plutôt que sa victime ; et déclarer une stratégie de parallélisme fixe.

---

## Deux autres choses relevées en chemin

**`AUD-QUA-13` cache un septième utilisateur du runtime statique.** `SqliteDatabaseSecurity.java:34` appelle
`SqliteContentionMetrics.processDefault()`, qui lit `LocalOperationalRuntime.recorder()` — il écrit donc dans
le runtime du processus sans jamais nommer la classe. Ni le `grep`, ni la règle, ni la revendication ne le
voient, parce que la règle est textuelle. Inoffensif aujourd'hui ; la méthode de comptage ne l'est pas.

**`AUD-TRV-04` a introduit une attente de 5 s à la fermeture du serveur remote**, correcte et testée des deux
côtés, mais **documentée nulle part hors du code** : ni dans la page développeur, ni dans la page
utilisateur, ni dans la note de version. Sur la seule surface exposée hors machine, un `close()` qui peut
mettre 5 s de plus à rendre la main se voit d'un script d'arrêt ou d'un superviseur systemd au
`TimeoutStopSec` serré.

---

## `AUD-TRV-07` : partiel confirmé, et les progrès sont nets

Le mainteneur le laisse `partiel`, à juste titre. La suite passe de 244 à **292 tests** (0 échec, 17 sautés),
les 50 fichiers de `scripts/` sont tous soit lancés par un workflow soit nommés comme lancés à la main avec
leur raison, `NOT_LAUNCHED` est vide, et **une page de documentation ne compte plus comme un lancement** —
c'était le défaut que je reprochais à la règle d'inventaire. Les planchers littéraux sont corrigés : le `507`
de `validate-m24.sh` a disparu du dépôt.

Ce qui reste tient en deux points. **Huit `.ps1` n'ont toujours aucun lanceur automatique** — la lane nocturne
est Linux seule — alors que le constat porte sur l'ensemble de `scripts/`, pas sur sa moitié Linux. Et la
**tâche 1.4 n'est toujours pas faite** : aucun validateur n'a encore été exécuté de bout en bout sur un vrai
arbre ; tout est prouvé sur le checkout fabriqué à `mvnw` bouchon, qui s'arrête à l'interrupteur de
packaging.

À noter aussi : `test_validate_dispatcher.py` exécute **0 cas sous Linux**. `validate.cmd` et `validate.ps1`,
le chemin que tout guide donne à un utilisateur Windows, ne sont testés que par la jambe Windows de `ci.yml`
— laquelle, d'après le `tasks.md`, n'a pas encore tourné sur un runner GitHub.

## `AUD-DEP-07` : inchangé, toujours bloqué amont

Le dépôt épingle toujours Dependency-Check 13.0.0, et le workflow porte toujours le mot « unreleased » à
propos de 13.0.1. Je n'ai pas pu établir qu'une 13.0.1 existe : API GitHub refusée, Maven Central refusé par
le proxy, recherches web qui ne ramènent que des pages 13.0.0. Indice en faveur du statu quo, pas une preuve.

---

## Ce que cette passe n'a pas pu établir

La limite habituelle, et une nouvelle. **Aucun build, aucun test Java, aucun gate n'a pu être exécuté** —
Maven Central est refusé par le proxy. Tous les statuts sont établis par lecture de code, à une exception :
la suite Python de `scripts/tests`, réellement exécutée (292 tests). **PowerShell est absent** : les 22 `.ps1`
n'ont été lues que comme du texte, et les 17 tests sautés sont exactement ces cas. Et cette fois **l'API
GitHub est refusée** : impossible de confirmer la release v1.2.1, ses assets, ou qu'une lane nocturne ait
jamais tourné.

Conséquence directe : « le parallélisme est configuré » est établi ; « la suite passe en parallèle » ne l'est
pas. Les quatre exécutions propres citées par l'ADR-0115 sont une revendication que je ne peux ni reproduire
ni infirmer — d'autant qu'elles ont été faites sur Windows, où le test que le `@Isolated` protège est
précisément ignoré.
