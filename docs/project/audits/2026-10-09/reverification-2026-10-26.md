# Revérification du 26 octobre 2026

Base : `origin/develop` à **`4694b8b2`** (PR #564, 25/10/2026).
Point de départ : `711ce5df`, état de la passe du 24 octobre.
Écart : **67 commits, 25 pull requests (#540 à #564), 907 fichiers, +15 100 / −5 206.**

C'est la plus grosse vague depuis l'audit du 9 octobre, et la première passe de cette mission qui
dispose d'une **exécution réelle du build**.

---

## 1. Ce qui a changé dans l'environnement de vérification

Toutes les passes précédentes ont travaillé sans compiler : Maven Central est injoignable depuis le
conteneur d'audit et son `~/.m2` est vide. Cette limite était inscrite comme angle mort permanent
depuis le 9 octobre.

Le 26 octobre, le build a été lancé sur la machine du mainteneur, où Maven 3.9.11 et un dépôt local
existent :

```
JAVA_HOME=~/.jdks/ms-21.0.12.1   ./mvnw -B -o clean verify
BUILD SUCCESS — 19/19 modules — 10 min 46
4 437 tests, 0 échec, 0 erreur, 13 skips
```

Détail par module : domain 57, application 459 (5 skips), provider-sdk 90, testkit 8, reference 2,
openspec 81 (4), markdown 11, synthetic 41, store-memory 24, store-sqlite 238 (1), mcp-transport 87,
minos 39 (1), nexus 31 (1), mcp 211, api 414 (1), cli 1 855, **architecture-tests 779**,
coverage-report 10.

Deux conséquences pour l'audit.

**Les règles ArchUnit des trois nouveaux ADR passent réellement.** Les agents de vérification avaient
lu leur logique sans pouvoir l'exécuter ; le build le confirme. Un découpage de paquets tenu par une
règle qui compile et passe n'est plus une lecture, c'est un fait.

**La couverture est mesurée pour la première fois**, et elle modifie deux constats. Lecture des
`jacoco.csv` produits par ce build, par module et par ses **propres** tests :

| Module | Lignes | Branches | Lignes instrumentées |
|---|---|---|---|
| `morpheus-application` | **48,00 %** | 44,61 % | 11 076 |
| `morpheus-store-memory` | 52,87 % | 33,58 % | 732 |
| `morpheus-domain` | 59,78 % | 50,39 % | 930 |
| `morpheus-store-sqlite` | 79,56 % | 69,69 % | 3 905 |
| `morpheus-cli` | 88,19 % | 78,51 % | 3 327 |
| `morpheus-mcp` | 91,53 % | 74,50 % | 1 642 |
| `morpheus-api` | 94,24 % | 84,13 % | 4 184 |
| **agrégat tous modules** | **91,33 %** | **77,49 %** | 30 294 |

Un seul angle mort subsiste côté exécution : le build a tourné sur **Windows uniquement**, donc la
parité des validateurs sur les deux plateformes reste non mesurée, comme le scan de vulnérabilités.

---

## 2. Ce qui se ferme

Trois constats passent à *corrigé*, tous en architecture, tous avec une règle exécutable à l'appui.

### `AUD-ARC-02` — Élevée — le chemin d'ingestion n'avait aucun port

C'est la clôture la plus importante de cette passe : un des trois derniers constats Élevée.

`OpenSpecProjectContentReader`, l'agrégat concret que `api` et `cli` instanciaient directement, a
disparu du dépôt — zéro occurrence, tests compris. À sa place, le port
`application/read/SpecificationContentReader` et le cas d'usage
`application/ingestion/ProjectContentIngestion`, qui ne connaît que le port. Le littéral `"openspec"`
est sorti des trois transports vers `OpenSpecSpecificationProvider.SOURCE_DIRECTORIES`.

Ce qui ferme vraiment le constat est une règle de dépendance compilée :
`TransportsReadAProjectThroughTheProviderPortTest:45` refuse à toute classe de `com.morpheus.api..`,
`com.morpheus.cli..` et `com.morpheus.mcp..` de dépendre d'un type de `com.morpheus.provider..` qui
n'est assignable ni à `SpecificationContentReader` ni à `SpecificationProvider`. Le test textuel qui
l'accompagne ne vaudrait rien seul ; celui-là tient.

### `AUD-ARC-06` — `morpheus-api`, 80 classes dans un paquet plat

90 classes aujourd'hui, en sept paquets de rôle, décidés par ARCH-0147 : `transport` 20, `remote` 15,
`local` 10, `routes/bodiless` 8, `routes/shareddecoder` 7, `routes/owncontext` 4, et 26 à la racine —
les services d'application, que l'ADR place là explicitement. Les 17 routeurs sont tous classés.

Trois règles ArchUnit remplacent trois listes de noms : cycle par tranches de paquet, liste fermée de
six types d'entrée, classement obligatoire d'un routeur dans l'un des trois groupes. La liste des
racines de composition passe de **32 noms à 5**.

### `AUD-ARC-09` — découpage incohérent d'un adaptateur à l'autre

`MorpheusCli` tombe de 908 à **109 lignes** : son dispatch ne garde que `help` et `version`.
`MorpheusMcpToolService` est supprimé. Les quatre adaptateurs sont désormais découpés — `api` par
rôle, `cli` par `command`/`launch`, `mcp` par `server`/capacités, `sqlite` par `adapter`/`runtime` —
et chaque découpage est tenu par des règles de même facture. La convention unifiée est la **forme**
de la règle, pas le nom des paquets, ce qui est le bon niveau d'uniformité pour des rôles différents.

---

## 3. Ce qui bouge sans se fermer

| Constat | Avant | Après | Ce qui a changé |
|---|---|---|---|
| `AUD-ARC-01` (Élevée) | toujours valide | **partiel** | Le gate existe et c'est du vrai ArchUnit ; mais le réseau n'est pas sorti du cœur |
| `AUD-ARC-11` | toujours valide | **partiel** | La réflexion est bornée par une règle — dans `application` seulement |
| `AUD-QUA-07` | toujours valide | **partiel** | 10 copies du constructeur de `Map` → 4 ; `morpheus-api` assaini |
| `AUD-TRV-03` | toujours valide | **partiel** | 40 → 58 constructeurs de règle, 15 → 30 fichiers ArchUnit |
| `AUD-TRV-07` | partiel | **partiel**, très réduit | 44 des 50 scripts sont désormais exécutés par un test |
| `AUD-TST-19` | toujours valide | **partiel** | Le ratchet passe de 585 à 769, à dix sous une mesure de 779 — confirmée par le build |
| `AUD-TST-10` | partiel | **partiel** | `ReleasedSchemaUpgradeTest` couvre les trois paliers publiés 12, 15 et 20 |

### `AUD-ARC-01` mérite un mot

ADR-0148 tranche le constat, mais pas dans le sens qu'il proposait. Il écarte le cœur pur et retient
le confinement : SQL, Jackson et l'exécution de processus sont refusés partout dans l'application —
et ils le sont effectivement, zéro occurrence —, l'accès fichier reste mais sous une liste nommée de
onze classes verrouillée dans les deux sens, et le réseau doit sortir.

Le réseau n'est pas sorti. `UpdateDiscoveryService` construit toujours son `HttpClient` dans
`morpheus-application`, il n'y a aucune interface dans `application.product`, et `MorpheusProductCli`
fait un `new` sur le service concret. L'ADR dit « sort », le gate dit « reste, à cette seule classe ».
Les deux coexistent, rien ne date le départ, et le dépôt l'assume en dette DT-18.

### `AUD-TRV-07` est le progrès le plus net

Le constat disait : aucun script n'est testé, sauf le gate de couverture différentielle. Il est
caduc. Un test d'inventaire bidirectionnel, `scripts/tests/test_script_inventory.py`, établit que les
50 scripts ont tous un lanceur, que 44 sont exécutés par un test, et que les 6 restants sont excusés
par nom et par raison. La suite Python passe de 7 tests à **322**.

Ce qui reste est l'asymétrie Linux/Windows : huit validateurs `.ps1` n'ont aucun lanceur de workflow,
et les 21 skips de la suite Python sont tous des cas PowerShell.

---

## 4. Ce qui naît de cette restructuration

Quatre constats, tous versés au sprint 15, tous issus de la vague elle-même.

**`AUD-ARC-16` — Moyenne.** Le découpage de `morpheus-store-sqlite` a élargi la surface publique du
module : trois types et onze membres passés de package-private à `public`
(`SqliteStoreConnection`, `SqliteTransactionRunner`, `SqliteContentionMetrics`, plus une constante).
Avant, le compilateur tenait la frontière gratuitement ; maintenant c'est une règle, qui doit être
écrite, maintenue et étendue. Rien ne consomme cette surface aujourd'hui : le coût est latent, mais
c'est exactement l'échange que tout découpage en paquets impose, et il mérite d'être nommé.

**`AUD-TST-20` — Moyenne.** `AppendOnlyJournalReadsArchitectureTest:61-70` est censé prouver que la
règle anti-lecture-non-bornée voit les deux appels qu'elle refuse. Il compte tout appel dont le
propriétaire est assignable aux deux ports, sans filtrer sur le nom de la méthode : un
`recordAttempt` suffit à le satisfaire. Si la règle devenait aveugle, les deux tests resteraient
verts. C'est la seule protection d'`AUD-PRF-04`, dont deux requêtes sur trois ne sont toujours pas
bornées.

**`AUD-ARC-17` — Faible.** La table de onze classes d'ADR-0148 et les constantes du gate qui la tient
sont deux listes recopiées à la main. Elles coïncident aujourd'hui, terme à terme, et pour les six
familles. Rien ne les compare.

**`AUD-DEP-26` — Faible.** Deux dérives de la trace de suivi : RT-13 est daté clos au 22/09 dans le
registre des risques alors que cinq messages de `security.yml` y renvoient comme à un risque vivant,
et `UPGRADE_1_2_2.md` s'annonce « ACTIVE » pour une version que le POM ignore.

---

## 5. Aggravations mesurées

| Mesure | Avant | Aujourd'hui |
|---|---|---|
| Copies de `sha256(String)` en `main` | 6 (09/10) | **7** — la nouvelle est dans `SqliteSchemaManager`, née du découpage |
| `safeMessage(Throwable)` dans `morpheus-cli` | 10 (09/10) | **11** |
| Blocs de 12 lignes dupliqués, tout `src/main` | 322 (09/10) | **405** (+26 %) |
| Blocs dupliqués par module d'intégration | 45 / 45 | **97 / 97** |
| Empreintes distinctes du localisateur de racine | 7 implémentations | **16** |
| Assertions textuelles dans les gates | 1 668 | **1 826** |

`AUD-QUA-02` est le cas intéressant : le nombre de copies du localisateur **baisse** de 99 à 71, sous
le chiffre d'origine de 73 pour la première fois — mais la baisse vient de la réécriture de 31
fichiers par les 25 PR, pas d'une extraction. Et pendant que le compte baisse, la divergence double :
16 écritures différentes du même localisateur, 5 façons incompatibles de détecter la racine. Un dépôt
qui converge en nombre et diverge en contenu n'a pas corrigé le constat.

---

## 6. Errata

**Sur `AUD-DEP-05`.** Les passes précédentes portaient dans le « reste à faire » la rédaction d'une
section de vérification SHA-256 dans `docs/user/INSTALLATION.md`. Cette section existe aux lignes
199-211, et elle est **antérieure au constat** : elle est présente au tag `v1.2.1`. Le reste à faire
était donc en partie fondé sur une lecture inexacte du fichier. Seul l'avertissement « éditeur
inconnu / SmartScreen » manque réellement.

**Sur `AUD-ARC-11`.** Le constat d'origine annonçait trois classes de production atteignant la
réflexion. Le recomptage large en trouve **huit** : le motif d'origine ratait `URLClassLoader`,
`ServiceLoader` et une classe apparue depuis. Le constat sous-comptait son propre sujet.

**Sur `AUD-TRV-07`.** Le chiffre de 44 scripts était déjà périmé au 21 octobre : le périmètre exact
est de 50 fichiers sous `scripts/` hors `scripts/tests/`.

---

## 7. Score

| Axe | 21 octobre | 26 octobre |
|---|---|---|
| Sécurité | 96 | 96 |
| Dépendances | 84 | 83 |
| Performance | 77 | 77 |
| Qualité | 71 | 70 |
| Tests | 75 | 70 |
| Architecture | **46** | **55** |
| **Moyenne** | **75** | **75** |

La moyenne ne bouge pas, et c'est le résultat honnête. L'architecture gagne neuf points — trois
constats fermés dont un Élevée, avec de vraies règles — mais quatre constats neufs et une lecture
plus exacte de la couverture reprennent ailleurs ce qui a été gagné. Un score stable après la plus
grosse vague de travail de la mission ne veut pas dire que rien n'a avancé : il veut dire que le
travail a déplacé la dette autant qu'il en a soldé, et que la mesure le voit.

**Deux constats Élevée restent** : `AUD-ARC-01` (partiel) et `AUD-PRF-04` (partiel). Il y en avait
trois au 21 octobre.

---

## 8. Angles morts de cette passe

- Le build a tourné sur **Windows seulement**. La parité des validateurs sur les deux plateformes
  reste non mesurée, et les 21 cas PowerShell de la suite Python sont les seuls exécutés — leurs
  homologues Linux ne le sont pas, et réciproquement.
- Aucun scan de vulnérabilités : Dependency-Check exige la base NVD, inaccessible depuis le conteneur
  comme depuis la machine sans la clé.
- `AUD-QUA-17` : l'affirmation « `morpheus-cli` n'a aucun obstacle au parallélisme » est confirmée au
  sens de ses cinq critères nommés, pas au-delà ; un champ statique mutable ou un verrou de fichier
  échapperait à cette mesure.
- Les mesures de blocs dupliqués (405, 97/97) proviennent d'un script de cette passe, comparable à
  lui-même sur les deux révisions, mais pas garanti identique à l'implémentation du 9 octobre. La
  direction — hausse — est robuste ; les valeurs absolues le sont moins.
