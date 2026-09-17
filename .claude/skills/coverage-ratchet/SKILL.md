---
name: coverage-ratchet
description: Relever un ratchet ou un plafond de couverture MORPHEUS sans casser un gate - distinguer la décision (ratchet dans un plafond déjà qualifié) de la requalification (plafond, qui exige une preuve Windows ET Linux), choisir la bonne échelle, calculer la marge en lignes et en branches, et répercuter la hausse partout. À utiliser dès qu'il est question de faire monter la couverture, de modifier config/m21-quality-ratchets.properties, ou quand un gate de couverture échoue.
---

# Relever un ratchet de couverture

Avant tout : lire les valeurs courantes avec [live-numbers](../live-numbers/SKILL.md). Rien de
ce qui suit ne doit être fait sur un chiffre mémorisé.

## Deux choses différentes, souvent confondues

| | Ratchet | Plafond qualifié |
|---|---|---|
| Où | `config/m21-quality-ratchets.properties` | une constante `*_QUALIFIED_*_RATIO` dans le gate de son échelle |
| Ce que c'est | le minimum que le build exige | la mesure reproductible la plus basse observée |
| Pour le monter | une **décision** — aucune mesure nouvelle tant qu'on reste sous le plafond | une **requalification** — preuve Windows **et** Linux, citée dans le commentaire |
| Session mono-plateforme | peut le faire | ne peut pas le faire |

Le gate applique `max(plancher D2, ratchet)` et refuse un ratchet supérieur au plafond **de sa
propre échelle**. Un plafond n'est jamais qualifié sur la meilleure des deux plateformes : les
deux exécutent le même nombre de tests, mais certains no-opent hors de leur OS, donc Linux
couvre légèrement moins de lignes à nombre de tests identique.

## Choisir l'échelle — elles ne mesurent pas la même grandeur

| Échelle | Rapport lu | Clés | Qui la contraint |
|---|---|---|---|
| Par module | somme des `*/target/site/jacoco/jacoco.xml`, module des tests d'architecture exclu | `perModule*` | `CoverageQualityGateTest` |
| Agrégée (canonique) | `jacoco-aggregate`, fusionne l'exécution inter-modules | `aggregate*` | `AggregateCoverageGateTest` |

`CoverageScaleSeparationTest` fait échouer le build si un gate lit une clé, un rapport ou une
preuve de l'autre échelle. **Ne jamais comparer un ratio par module à un seuil agrégé, ni
l'inverse.** Le *pourquoi* est dans
`docs/adr/0104-two-coverage-scales-share-one-population.md` : la lire avant de toucher à une
clé, à un plafond ou à la population mesurée.

Conséquence pratique quand on écrit des tests pour faire monter l'échelle par module : un
rapport par module ne crédite que les tests **du module qui porte la ligne**. Un service de
`morpheus-application` exercé uniquement par des tests de `morpheus-api` y lit comme non
couvert, et un test ajouté sous `morpheus-architecture-tests` n'y déplace rien du tout.

## Calculer la marge en lignes et en branches, jamais en points

Un ratchet collé à la mesure transforme une variation ordinaire de runner en échec de build. La
marge se calcule sur les populations réelles de lignes et de branches, pas en points de
pourcentage : les deux clés ne se resserrent pas au même rythme, et la branche est la plus
serrée des deux. Rien n'oblige à les faire bouger ensemble — ADR-0104 interdit seulement de les
aligner sur une moyenne.

## Obtenir la moitié Linux d'une preuve

1. **Artefacts CI d'abord.** Chaque run de `ci.yml` téléverse `m21-integrity-<OS>`, qui contient
   la preuve de couverture et les `jacoco.xml` par module. Pour la tête de `develop`, le run
   `push` et le run `pull_request` de la PR de promotion construisent le même SHA, ce qui donne
   deux runs exact-head par plateforme sans rien construire :
   `gh run list --workflow ci.yml --commit <sha>` puis
   `gh run download <id> -n m21-integrity-Linux`. Déposer les `jacoco.xml` téléchargés dans un
   worktree suffit à faire tourner le gate sur de vrais rapports.
2. **Build WSL en secours.** Plus lent, et il a déjà échoué localement sur un test sensible à
   l'horloge pendant que la CI passait.

Citer la preuve — SHA, plateforme, run — dans le commentaire du plafond. Un plafond sans preuve
citée n'est pas qualifié.

## Répercuter la hausse — ne pas se fier à une liste écrite à la main

La valeur vit dans le `.properties`, mais elle est recopiée dans des pages de documentation et
épinglée dans des tests. Une liste manuelle a déjà raté une destination dont la mise en forme
différait de celle des autres.

La méthode qui tient, décrite dans [rules/meta.md](../../rules/meta.md) :

1. changer la valeur dans `config/m21-quality-ratchets.properties` ;
2. lancer `RepositoryDocumentationCoherenceTest` **et** `ProductionIntegrityContractTest` ;
3. laisser les échecs **énumérer** les destinations réelles ;
4. les corriger toutes dans le même changement.

Ne toucher qu'à l'échelle réellement mesurée. Les deux échelles ont des clés, des plafonds, des
fichiers de preuve et des messages distincts : les aligner sur une valeur moyenne referait sous
un autre nom le défaut que leur séparation a corrigé.

Une **mesure datée** ne se réécrit pas. Seule une affirmation courante se met à jour.

## Interdits

- **Jamais baisser un ratchet pour faire passer un build.** Écrire les tests manquants.
- Jamais recoder un ratchet en dur à la valeur du plancher D2 — un gate vérifie explicitement
  que le fichier du gate par module ne contient pas ces littéraux et qu'il lit bien le
  `.properties`.
- Jamais relever un ratchet au-delà du plafond de son échelle sans requalifier ce plafond.
- Jamais relever un plafond depuis une session qui ne dispose que d'une plateforme.

## Vérifier

```bash
./mvnw clean verify
./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest
./mvnw test -pl morpheus-coverage-report
```

Les deux gates exigent un `clean verify` complet préalable : ils refusent un réacteur à moitié
construit et **nomment** les modules dont le rapport manque, en séparant « jamais construit par
cette invocation » de « construit sans produire de rapport ».
