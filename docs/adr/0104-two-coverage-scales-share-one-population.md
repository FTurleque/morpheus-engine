# ADR-0104 — Deux échelles de couverture, une population commune : elles ne diffèrent que par l'attribution

- Statut : **Acceptée — séparation livrée le 09/09/2026 (#297), population commune le 11/09/2026 (#309)**
- Date : 11 septembre 2026
- Dépend de : ADR-0089 (intégrité de production M21), ADR-0085 (déterminisme des gates), ADR-0103 (une règle n'est acceptée qu'après avoir été cassée)
- Portée : `CoverageQualityGateTest`, `AggregateCoverageGateTest`, `CoverageScaleSeparationTest`, `config/m21-quality-ratchets.properties`, les validateurs `validate-m21.*` et ceux qui consomment la même preuve

## Contexte

Deux gates mesurent la couverture de MORPHEUS, et ils ne mesurent pas la même grandeur.

| Gate | Rapport lu | Ce qui peut créditer une ligne |
|---|---|---|
| `CoverageQualityGateTest` (`morpheus-architecture-tests/.../m21/`) | somme des `*/target/site/jacoco/jacoco.xml`, module des tests d'architecture exclu | les tests **du module qui porte la ligne**, et eux seuls |
| `AggregateCoverageGateTest` (`morpheus-coverage-report`) | `target/site/jacoco-aggregate/jacoco.xml` — la mesure canonique | **toute** exécution du réacteur, y compris l'exécution inter-modules des tests d'architecture |

Un service de `morpheus-application` exercé seulement par les tests de `morpheus-api` compte comme non couvert
sur la première échelle et comme couvert sur la seconde. Les deux lectures sont justes ; elles répondent à deux
questions différentes — *ce que chaque module couvre par lui-même* et *ce que le produit couvre*.

### Le défaut d'origine : une clé pour deux grandeurs

Jusqu'au 09/09/2026, les deux gates lisaient **les deux mêmes clés** (`lineCoverageMinimum`,
`branchCoverageMinimum`) et écrivaient **le même fichier de preuve**, le dernier écrivain gagnant. Le seuil avait
été qualifié sur l'échelle par module (`0.620 / 0.535`) ; appliqué à l'échelle agrégée, qui mesurait alors
85,7 % de lignes, il laissait une marge d'environ 24 points que rien ne justifiait.

Mesuré à `e5127486` (28 619 lignes exécutables, 10 424 branches sur l'échelle canonique) : **≈ 6 790 lignes et
≈ 1 566 branches** de couverture agrégée pouvaient disparaître sans qu'aucun gate ne réagisse. Et aucun
consommateur de la preuve ne pouvait savoir quelle échelle il venait de lire.

### Le défaut latent : croire que les deux échelles portent sur des modules différents

Séparer les clés corrigeait le premier défaut et en invitait un second. Deux gates, deux seuils, deux preuves :
la lecture naturelle est qu'ils mesurent **deux populations** de modules. C'était exactement la croyance qui
avait rendu le défaut d'origine invisible — deux chiffres dont on suppose qu'ils parlent de la même chose, ou de
choses sans rapport, sans que rien ne le dise.

Or la population agrégée n'était écrite nulle part : elle résultait des dépendances déclarées dans
`morpheus-coverage-report/pom.xml`. Un module retiré de ce POM sortait de la mesure canonique sans que le gate
le remarque, et le plafond qualifié continuait de borner une population sur laquelle il n'avait jamais été
mesuré.

## Décision

### 1. Une échelle, une paire de clés, un plafond qualifié, une preuve qui se nomme

- `config/m21-quality-ratchets.properties` déclare `perModuleLineCoverageMinimum` /
  `perModuleBranchCoverageMinimum` et `aggregateLineCoverageMinimum` / `aggregateBranchCoverageMinimum`. **Aucune
  clé indépendante de l'échelle** : `lineCoverageMinimum` et `branchCoverageMinimum` sont refusées.
- Chaque gate porte le plafond qualifié **de sa propre échelle** : `PER_MODULE_QUALIFIED_*_RATIO` dans
  `CoverageQualityGateTest`, `AGGREGATE_QUALIFIED_*_RATIO` dans `AggregateCoverageGateTest`. Le gate applique
  `max(plancher D2, ratchet)` et refuse un ratchet au-dessus de son plafond comme un ratchet ramené au plancher.
- Chaque gate écrit sa propre preuve et y déclare son échelle **en première ligne** :
  `m21-per-module-coverage-summary.txt` (`coverageScope=per-module`) et `m21-aggregate-coverage-summary.txt`
  (`coverageScope=aggregate`). Les validateurs M21 — et les validateurs D2, M22 à M27 qui lisaient le même
  fichier — lisent la preuve agrégée et **refusent de conclure** sur une preuve d'une autre échelle ou sans
  portée déclarée.
- `CoverageScaleSeparationTest` fait échouer le build si un gate lit la clé, le rapport ou la preuve de l'autre
  échelle, et exécute le garde des validateurs contre une preuve `per-module`, une preuve sans portée et une
  preuve absente.

### 2. Les deux échelles portent sur la même population, dérivée du réacteur

La population est : **tout module déclaré dans les `<modules>` du POM racine qui porte au moins une classe sous
`src/main/java`**. Elle est dérivée, jamais listée à la main.

- `CoverageQualityGateTest` exige le `jacoco.xml` de chacun de ces modules et nomme ceux qui manquent.
- `AggregateCoverageGateTest` dérive la même population, **refuse un rapport agrégé dont les groupes de modules
  diffèrent** en nommant le module absent ou étranger, et écrit `populationRule=`, `populationModules=` et
  `population=` dans sa preuve.
- Les modules sans sources principales (`morpheus-architecture-tests`, `morpheus-coverage-report`) sont
  **expliqués** par la règle, pas exclus par leur nom. Déclarer un module le rend obligatoire du même geste.

Les deux échelles ne diffèrent donc plus que par **les exécutions autorisées à créditer une ligne** — la seule
différence que leur séparation visait. *Séparer les échelles* et *leur donner une population commune* sont les
deux moitiés d'une même décision : la première empêche un seuil de gouverner la mauvaise grandeur, la seconde
empêche de croire que deux grandeurs parlent de deux produits.

### 3. L'outillage de vérification reste dans la population

`morpheus-store-memory`, `morpheus-provider-synthetic`, `morpheus-provider-testkit` et
`morpheus-provider-reference` portent des classes principales ; ils sont dans la population, **délibérément**.

Mesuré sur le rapport agrégé du 11/09/2026 (`86d1799a`), groupe par groupe :

| | lignes | branches |
|---|---|---|
| les quatre modules d'outillage | 1 266 / 1 407 = 89,98 % | 400 / 533 = 75,05 % |
| échelle agrégée mesurée | 87,0753 % | 70,4048 % |
| échelle agrégée sans l'outillage | 86,9242 % | 70,1496 % |
| **écart** | **−0,15 point** | **−0,26 point** |

Trois raisons de ne pas les retirer, dans cet ordre :

1. **Aucune décision de gate n'en dépend.** L'outillage est couvert au-dessus de la moyenne ; le retirer ferait
   baisser le ratio, sans qu'aucun seuil ne bascule d'un côté ou de l'autre.
2. **Le plafond qualifié bornerait une population sur laquelle il n'a jamais été mesuré.**
   `AGGREGATE_QUALIFIED_*` a été qualifié avec l'outillage dedans. Le retirer imposerait une requalification
   Windows + Linux pour 0,15 point — ou laisserait un plafond sans preuve, ce qui est le défaut d'origine sous un
   autre nom.
3. **Les deux échelles différeraient aussi par la population**, et la décision 2 perdrait sa raison d'être.

Retirer l'outillage reste possible : c'est un changement de population, donc une requalification des deux
plateformes et un amendement de cet ADR, jamais un réglage de `morpheus-coverage-report/pom.xml`.

### 4. Ce qu'une hausse de plafond exige

Relever un `*_QUALIFIED_*_RATIO` exige une **mesure exact-head de cette échelle sur Windows et sur Linux**,
citée dans le commentaire de la constante, et qualifiée sur **la plus basse** des mesures, jamais la meilleure :
les deux plateformes exécutent le même nombre de tests, mais certains no-opent hors de leur OS, donc Linux couvre
légèrement moins de lignes à nombre de tests identique.

Le ratchet se place **sous** le plafond, avec une marge supérieure à la variation observée d'une exécution à
l'autre — deux lignes couvertes d'écart entre deux runs du même commit sur la même machine (échelle par module,
08/09/2026), 0,045 point en lignes et 0,038 en branches sur quatre runs (échelle agrégée, 09/09/2026). Un ratchet
collé à la mesure transformerait une variation ordinaire en échec de build.

Une session qui ne dispose que d'une plateforme peut relever un ratchet **dans la marge déjà qualifiée** ; elle
ne touche pas au plafond. Une hausse ne concerne que **l'échelle réellement mesurée** : aligner les deux sur une
valeur moyenne referait sous un autre nom le défaut que la séparation a corrigé.

### 5. Ce que la règle interdit

- Comparer un ratio par module à un seuil agrégé, ou un ratio agrégé à un seuil par module.
- Réintroduire une clé de couverture qui ne nomme pas son échelle.
- Écrire ou consommer une preuve de couverture qui ne déclare pas sa portée en première ligne.
- Lister la population à la main, ou la modifier sur une seule échelle.
- Baisser un ratchet pour faire passer un build.

## Conséquences

### Acquis

- La marge morte de l'échelle agrégée est passée de ≈ 6 790 lignes / ≈ 1 566 branches à ≈ 208 lignes /
  ≈ 55 branches à `e5127486` : le ratchet agrégé (`0.850 / 0.680`) réagit désormais à une régression réelle.
- Chaque plafond est prouvé aux deux bornes, sur chaque échelle.
- La population est prouvée **en la cassant** (ADR-0103, règle 4) : retirer le groupe `morpheus-store-memory`
  du rapport agrégé réel a fait échouer le gate avec `absent from the report: morpheus-store-memory` ; un réacteur
  synthétique refuse aussi un module étranger par son nom.

### Assumé

- **Le plafond par module est daté.** Qualifié le 08/09/2026 à 62,5013 % / 53,7997 %, il est dépassé par la
  mesure depuis (63,02–63,06 % de lignes relevés le 09/09/2026). Le requalifier est un lot distinct, sur deux
  plateformes ; cet ADR ne le fait pas.
- **Une mesure plus exigeante coûte un `clean verify` complet.** Le gate par module refuse un réacteur à moitié
  construit, et le gate agrégé refuse un rapport qui ne mesure pas toute la population : aucune des deux échelles
  ne sait conclure d'un arbre partiellement construit.

### Ce que cet ADR ne décide pas

Les valeurs. Les ratchets vivent dans `config/m21-quality-ratchets.properties`, les plafonds et leurs preuves dans
les commentaires des deux gates ; ils montent au fil des lots et ne doivent jamais être recopiés d'ici
(`rules/meta.md`). Cet ADR fixe **comment** une valeur est qualifiée et à quelle échelle elle s'applique, pas
laquelle.
