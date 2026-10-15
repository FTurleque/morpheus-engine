# Vérification du 15 octobre 2026 — les neuf constats « partiels »

Audit du 9 octobre, revérifié le 13 octobre sur `d5be889b`, le 15 octobre sur `b08b30fb`, puis **sur
`984bb270` après la fusion des six branches dédiées**. Cette passe répond à une question : **les neuf
constats laissés `partiel` le 13 octobre sont-ils maintenant corrigés ?**

## Réponse en une phrase

Oui pour trois d'entre eux, et le travail des six branches est maintenant **sur `develop`** : les branches
ont été fusionnées le 15 octobre (PR #487 à #492, 47 commits) puis supprimées, et j'ai revérifié le code
*après* la fusion plutôt que de faire confiance aux merges.

## L'état en un tableau

| Constat | Sév. | Statut sur `develop` après fusion |
|---|---|---|
| `AUD-DEP-14` | Faible | **corrigé** (PR #486) |
| `AUD-TRV-01` | Moyenne | **corrigé** (PR #487) |
| `AUD-ARC-14` | Faible | **corrigé** (PR #489) |
| `AUD-TRV-04` | Élevée | partiel |
| `AUD-TRV-05` | Élevée | partiel |
| `AUD-TRV-07` | Moyenne | partiel |
| `AUD-QUA-13` | Faible | partiel |
| `AUD-TST-12` | Faible | partiel |
| `AUD-DEP-07` | Moyenne | partiel — **bloqué amont** |

Deux constats neufs sont nés de cette passe : `AUD-QUA-17` et `AUD-TST-18`.

**Ce que la fusion a changé aux notes** : Dépendances 69 → **73**, Tests 61 → **65**, Qualité 48 → **52**,
Architecture 35 → **36**, moyenne 61 → **63**. Ces valeurs sont exactement celles que la projection
d'avant-fusion annonçait, ce qui confirme que les merges n'ont rien perdu.

## Vérification que les fusions ont préservé le travail

Je n'ai pas supposé qu'un merge conserve ce que sa branche contenait — je l'ai mesuré sur `develop` après
coup :

| Mesure | Avant | Après fusion |
|---|---|---|
| Constructeurs publics non canoniques sur les 472 records | 17 | **0** |
| Classe interne `Runtime` de `MorpheusQueryApiService` | présente | **absente** (fichier à 236 lignes) |
| Ouvertures de portée / sites de composition SQLite | 5 / 17 | **7 / 16** |
| `catch` muets (forme de la règle) | 17 | **13** |
| Utilisateurs du runtime statique en `src/main` | 25 | **20** |
| Tests Python de `scripts/tests` | 56 | **244, 0 échec, 17 sautés** |
| Encarts datés sur `VALIDATION_M15..M18` | 0 | **4** |

Les quatre gates neufs sont en place : `RecordSingleConstructorArchitectureTest` (liste d'exceptions vide),
la règle bytecode de `SqliteConnectionScopeUseArchitectureTest`,
`everyDatedRecordCitingARemovedWrapperSaysWhatReplacedIt`, et l'étape Windows de `ci.yml:56-57`.

---

## Ce qui a changé sur `develop`

Deux commits depuis `d5be889b`, tous deux de la PR #486 : *« build: let the root POM decide every dependency
and plugin version »*. Ils soldent `AUD-DEP-14`.

`org.xerial:sqlite-jdbc` est désormais déclaré dans le `<dependencyManagement>` racine (`pom.xml:132-133`)
et le module ne porte plus de `<version>` (`morpheus-store-sqlite/pom.xml:28-30`). Le piège signalé par
l'audit est évité : la propriété `<sqlite-jdbc.version>3.53.4.0</sqlite-jdbc.version>` reste en
`pom.xml:51`, donc le gate textuel `D2RepositoryHardeningArchitectureTest.java:47` tient toujours — seule
sa consommation a bougé. Quatre autres sites ont été traités de la même manière, et le gate D2 a gagné
78 lignes qui refusent tout `<version>` dans un POM de module.

Le second volet du constat portait sur `slf4j-nop`. Il est tranché autrement que prévu, et mieux :
`slf4j-nop` n'est pas passé en gestion centrale, il a quitté le produit (ADR-0111). `morpheus-cli/pom.xml`
ne déclare plus que `slf4j-api`, avec le commentaire *« The logging binding is MORPHEUS's own
(OperationalSlf4jServiceProvider): no second provider is shipped »*. L'inquiétude de l'audit — l'avertissement
« no SLF4J providers were found » au démarrage de la CLI — tombe d'elle-même : il y a bien un fournisseur.

---

## Les deux constats entièrement soldés par la fusion

### `AUD-TRV-01` — les validateurs M15 à M18

Le dépôt a pris une **troisième voie**, que l'audit n'avait pas envisagée et qui est plus exacte que
l'action proposée. L'audit demandait d'ajouter dans chacun des quatre relevés un renvoi vers
`scripts\validate.cmd <cible>`. Or cette commande échouerait : `scripts/validate.ps1:33-37` construit
`validate-<cible>.ps1` et lève si le fichier manque, et les cibles présentes sont `d2`, `m19` à `m28`, `r2`,
`r3` — pas `m15` à `m18`.

L'encart posé en ligne 3 de chaque relevé dit donc qu'**aucune commande ne rejoue la preuve**, et donne le
`git show` qui retrouve le validateur : `git show d1aebd974:scripts/validate-m15.ps1` et ses trois
homologues. Les quatre objets résolvent, je l'ai vérifié. La doctrine d'immuabilité des preuves datées est
préservée, et le lecteur est averti dans le fichier lui-même. L'encart a été posé sur 23 relevés, pas
seulement les quatre visés.

### `AUD-ARC-14` — les constructeurs de compatibilité

Soldé, et au-delà du constat. Aucun record de `src/main` ne déclare plus de constructeur public non
canonique, contre **17 dans 15 records** sur `develop`. Les quatre qui avaient un appelant de production
passent par trois fabriques nommées.

Surtout, l'enforcement est **structurel** : `RecordSingleConstructorArchitectureTest` compare par réflexion,
sur le bytecode, la signature de chaque constructeur public à celle du canonique — avec une liste
d'exceptions vide, une vérification bidirectionnelle, un plancher de non-vacuité et cinq records-fixtures
qui prouvent la détection. C'était le reproche central du constat : un gate textuel cherchant le mot
« compatibility » aurait été satisfait en supprimant un commentaire.

Le constat **sous-comptait d'un facteur deux**. Il citait huit constructeurs repérés au Javadoc ; la mesure
structurelle en trouve dix-sept, dont **cinq sans aucun appelant** — du code mort qu'aucune règle ne voyait,
parce que `UnreferencedPublicMethodArchitectureTest` juge les méthodes et pas les constructeurs.

---

## `AUD-DEP-07` change de nature

L'indétermination du 13 octobre est levée, et la réponse déplace le constat hors du dépôt.

Le 13 octobre je n'avais pas pu établir si Dependency-Check 13.0.1 était sorti. Vérification du 15 octobre
sur la page des releases du projet : **la dernière version publiée est 13.0.0, du 3 août**, et elle porte le
badge « Latest ». **13.0.1 n'existe pas.**

Le point de défaillance unique demeure — sans `NVD_API_KEY`, le gate CVE ne peut pas rafraîchir sa base —
mais **aucune action du dépôt ne le ferme aujourd'hui**. Le constat reste ouvert et devient un point de
veille, pas une tâche. Deux contournements existent si le risque est jugé trop haut, et ce sont des
décisions d'exploitation : poser une `NVD_API_KEY` en secret de dépôt, ou épingler un miroir de la base NVD.

---

## Les cinq constats encore partiels sur `develop`

Le travail y est substantiel. Ce qui suit n'est pas un reproche, c'est l'inventaire de ce qui n'est pas
encore fait — et dans les cinq cas, la section 4 des changes OpenSpec (vérification : `clean verify`, gate
de couverture, validateur M19, description de PR) est ouverte, ce que les `tasks.md` déclarent honnêtement.

**`AUD-TRV-04`** — Quatre des cinq sites de suppression silencieuse comptent désormais, le cinquième est
explicité et son explication tient. Le répertoire de logs a un écrivain. La question du contrat OpenAPI est
tranchée : le jeu de compteurs n'en fait pas partie, c'est un test Java qui épingle la liste.
Ce qui reste de plus notable : **deux `catch` du transport restent muets en pratique et aucune règle ne les
voit.** `BoundedStdioClientTransport.java:545-552` et `:617-624` journalisent au niveau `DEBUG`, que le
gestionnaire JDK par défaut jette dans tous les modes de lancement. La règle textuelle ne peut pas les
signaler : elle cherche un corps sans instruction, et ces blocs en contiennent une. C'est aujourd'hui le
seul endroit du produit où un échec réel disparaît sans compteur *et* sans journal effectif.

**`AUD-TRV-05`** — Le fond technique est corrigé : une connexion physique par opération, sur les trois
transports, **mesurée au pilote JDBC** et non inférée, avec une règle bytecode à côté de la règle textuelle.
La classe interne `Runtime` de `MorpheusQueryApiService`, que le constat nommait, n'existe plus.
Mais la propriété énoncée — « un seul type runtime par transport » — reste fausse : chaque transport en
expose trois. Et le constat **sous-estimait le défaut** : les deux vrais coupables vivaient dans
`morpheus-store-sqlite`, hors du périmètre de la règle textuelle qui ne lisait que les trois modules de
transport. Une opération de policy ouvrait huit connexions sur *tous* les transports, HTTP compris.
À noter : l'amendement précédent de l'ADR-0109 affirmait, par inférence, qu'un seul site ouvrait encore deux
connexions. La mesure l'a contredit, et le second amendement le reconnaît explicitement. Le dépôt a
lui-même documenté la différence entre une revendication et une mesure.

**`AUD-TRV-07`** — Les 26 fichiers de validateur sont désormais lancés pour de vrai, sur un checkout Git
fabriqué avec un `mvnw` bouchon, avec un jeu de cas partagé entre `.sh` et `.ps1`. **J'ai exécuté la suite :
244 tests, 0 échec, 0 erreur, 17 classes sautées** (le miroir PowerShell, absent de ce conteneur) — contre
56 tests le 13 octobre et aucun test de validateur sur `develop`. La jambe Windows est câblée et tenue par
une règle. Le dispositif a déjà payé : six défauts réels de validateurs ont été découverts par les tests
neufs.
Le volet (b) s'arrête en revanche à un constat : la règle d'inventaire **nomme** les orphelins, elle ne les
retire ni ne les répare. Et elle compte une page de documentation comme un lancement — mesuré depuis les
workflows seuls, 27 scripts sur 50 sont atteints par une invocation automatique.

**`AUD-QUA-13`** — Deux des trois demandes sont satisfaites : l'injection par constructeur existe et est
vérifiable, la liste des tests est tombée à zéro, et l'ADR-0115 est **acceptée**, pas proposée. La
troisième ne l'est pas : `MAIN_USERS` n'est descendue que de 25 à 20, et le parallélisme JUnit n'est
toujours pas activé — aucun `junit-platform.properties`, aucune propriété `parallel` dans surefire.
Le verrou spécifique à ce constat est levé ; un autre le remplace, plus large. Voir `AUD-QUA-17`.

**`AUD-TST-12`** — La note datée existe pour M19, et une règle neuve
(`everyDatedRecordCitingARemovedWrapperSaysWhatReplacedIt`) la tient. Mais le chemin périmé
`docs/validation/VALIDATION_M19.md` n'est pas corrigé — et il apparaît en fait **deux fois**, en `:213`
dans le bloc « Expected files » et en `:253` en prose — et deux angles morts s'ajoutent : l'ADR-0084 cite
`validate-m18.cmd` sans aucun avertissement, hors du balayage de la règle, alors que la spec range
explicitement les ADR parmi les enregistrements datés soumis à cette obligation ; et la règle ne contrôle ni
la date ni la position de la mention, alors que le README annonce une note « en tête » et « datée ».

---

## Deux constats neufs

Tous deux vérifiés directement sur `origin/develop`, indépendamment des branches en cours.

### `AUD-QUA-17` — Moyenne — le verrou du parallélisme se déplace

`SqliteConnectionScope.java:44-47` porte quatre compteurs statiques de processus, exposés par
`diagnostics()`, lus **en différence** par les tests. Ils étaient six classes avant la fusion ; ils sont
**dix après**, sur quatre modules — parce que le correctif d'`AUD-TRV-05`, par ailleurs bon, a payé sa
mesure en ajoutant les trois tests de comptage de connexions. C'est exactement la forme de défaut que
`AUD-QUA-13` décrit, déplacée sur une autre classe et désormais quatre fois plus large.

Le dépôt le sait et l'a mesuré : l'ADR-0115 relate deux exécutions du module store avec un
`junit-platform.properties` temporaire, qui donnent 2 puis 4 échecs sur 205, tous sur `diagnostics()`.
Fermer `AUD-QUA-13` sans ouvrir celui-ci laisserait le verrou du parallélisme intact tout en donnant
l'impression qu'il a été traité.

### `AUD-TST-18` — Moyenne — le validateur de release R3 ne peut pas passer

`scripts/validate-r3.sh:41-42` et `scripts/validate-r3.ps1:25` exigent **17** fichiers `pom.xml` dans le
réacteur. Le POM racine déclare **18 modules**, soit **19 POM** dans l'arbre. Les deux variantes échouent
donc avant tout le reste.

Le risque n'est pas un faux PASS — l'échec est bruyant — c'est que la preuve R3 **ne peut plus être produite
du tout**, et que personne ne s'en est aperçu parce qu'**aucun workflow ne lance `r3`** : la cible n'est
joignable que par le dispatcher. Même défaut de fond que `AUD-DEP-06`, un plancher littéral figé à une
taille de réacteur dépassée, mais sur un gate de release. **Attention à un faux ami** : le commit
*« the fabricated checkout reads the reactor from the root POM, not from git »* de la fusion du 15 octobre
corrige le harnais de test d'`AUD-TRV-07`, pas le validateur — le littéral 17 est toujours là, dans les deux
variantes. La suite de tests neuve de `AUD-TRV-07` ne
l'attrape pas : ses deux cas `r3` ne couvrent que la politique de périmètre, en amont de ce contrôle — le
motif même de `AUD-TRV-07` reproduit à l'intérieur de son correctif.

---

## Notes

Le 13 octobre, l'`AUD-TST-13` (*Info : hygiène de test remarquable*) a été confirmé. Sa preuve notait déjà
que « les compteurs globaux de `SqliteConnectionScope` sont consommés en delta et non en valeur absolue », et
le présentait comme rassurant. La mesure de l'ADR-0115 montre que c'est précisément cette lecture en delta
qui casse en parallèle. L'Info tient tel qu'il est écrit — il parle de l'état statique déclaré *dans les
classes de test* — mais il ne doit pas être lu comme une garantie que la suite supporterait le parallélisme.
Son statut est inchangé ; un renvoi vers `AUD-QUA-17` a été ajouté.

---

## Ce que cette passe n'a pas pu établir

La même limite qu'aux deux passes précédentes, et elle est structurante : **aucun build, aucun test Java,
aucun gate n'a pu être exécuté**. Maven Central est refusé par le proxy de ce conteneur et `~/.m2` est vide.
Tous les statuts ci-dessus sont établis par **lecture de code**, jamais par exécution — à une exception
près, la suite Python de `scripts/tests` (244 tests), qui a réellement tourné.

En particulier : que les règles d'architecture neuves passent, que les suites citées soient vertes, que les
ratchets de couverture tiennent, et que les validateurs franchissent leur phase de packaging sont des
affirmations que seul un `clean verify` sur une machine ayant accès au dépôt Maven peut établir. Les
sections 4 des cinq changes OpenSpec concernés sont ouvertes pour exactement cette raison.

## Méthode

Inchangée, et c'est elle qui fait la valeur de la passe : **une case cochée dans un `tasks.md`, un message
de commit ou une ligne de `proposal.md` est une revendication, pas une preuve.** Seule la lecture du code
établit un fait. Les sept agents de vérification ont reçu cette consigne, ainsi que la liste exacte de ce
qui restait ouvert sur chaque constat, et l'instruction de ne pas laisser vide la section « surprises ».

C'est cette discipline qui a fait apparaître, cette fois, que la fusion n'avait rien perdu — mais aussi
qu'elle avait **aggravé** `AUD-QUA-17` en quadruplant le nombre de tests qui lisent l'état statique partagé,
ce qu'aucune lecture des `tasks.md` n'aurait montré.
