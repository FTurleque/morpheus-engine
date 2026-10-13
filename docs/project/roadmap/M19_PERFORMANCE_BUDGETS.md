# M19 — Performance & Capacity Budgets

Statut : **FROZEN BEFORE OPTIMIZATION — M19-S1**

Date de gel : 26 juillet 2026

Ce document fixe les volumes, métriques et seuils de M19 **avant toute optimisation de code**. Les seuils ne seront pas relevés après observation d'une implémentation pour faire passer le gate. Toute modification future d'un seuil devra être motivée comme changement de contrat, avec historique explicite.

## 1. Environnement de référence

Le gate doit enregistrer :

```text
OS / architecture
CPU logical processors
RAM visible
filesystem du workspace et de la DB
java -version
mvnw --version
Git SHA
```

Environnement minimum de référence pour appliquer les budgets bloquants :

```text
CPU    >= 4 logical processors
RAM    >= 8 GiB
Disk   local SSD / runner local filesystem
Java   >= 21, compilation target release 21
```

Les résultats sur une machine inférieure restent informatifs et ne doivent pas être présentés comme preuve de budget.

## 2. Fixture de gate `M19-LARGE-GATE-1`

La fixture est **générée déterministiquement** ; elle n'est pas stockée comme milliers de fichiers Git.

```text
seed                         1901
source files                 5,000
source bytes                 >= 10 MiB total
changed files incremental    50
requirements                 10,000
changes                      2,000
acceptance criteria          4,000
traceability links           25,000
composition observations     10,000
composition conflicts        1,000
published snapshots retained 5
query page size              50
trace maxDepth               4
```

Le générateur doit produire les mêmes identités logiques, chemins, contenus, graphes et checksums pour un même seed.

## 3. Protocole de mesure

Sauf mention contraire :

```text
warmup iterations    1
measured iterations  5
reported latency     p95 (nearest-rank)
time source           System.nanoTime()
GC                    aucune exigence de GC forcé entre itérations
process timeout       seuil + marge de sécurité du validateur
```

Les mesures sont exécutées après compilation ; le temps Maven de compilation ne fait pas partie des latences applicatives.

## 4. Budgets bloquants

| Opération | Fixture | Budget M19 |
|---|---|---:|
| scan inventaire complet | 5,000 fichiers / >=10 MiB | p95 <= 20 s |
| diff + plan incrémental | 5,000 entrées, 50 modifiées | p95 <= 2 s |
| publication full snapshot | 10,000 requirements / 25,000 liens | p95 <= 60 s |
| recherche requirements | 10,000 requirements | p95 <= 1,000 ms |
| traversal traceability | 25,000 liens, depth 4 | p95 <= 2,000 ms |
| composition status/conflicts | 10,000 observations / 1,000 conflits | p95 <= 1,000 ms |
| reopen SQLite + active snapshot lookup | DB de gate | p95 <= 2,000 ms |
| launcher `--json version` packagé | app-image chaud/froid mélangé, 5 runs | p95 <= 5,000 ms |
| heap Java benchmark | gate complet in-process | <= 768 MiB (`-Xmx768m`) |
| taille SQLite | 5 snapshots de gate | <= 512 MiB |
| coût rétention | snapshot supplémentaire équivalent | croissance <= 128 MiB |

Le budget de `publication full snapshot` couvre la persistance et l'activation du nouveau snapshot mais exclut la génération de la fixture.

### 4 bis. Budgets ajoutés après le gel — 13 octobre 2026

Les lignes ci-dessus sont celles du gel et ne changent pas. Celles-ci sont ajoutées selon l'amendement du 13 octobre
2026 d'ADR-0085 : un coût trouvé sans budget en reçoit un, daté, **déclaré avant que le code qu'il jugera ne soit
modifié**. Elles sont écrites avant leurs gates et avant les corrections du sprint 6 de l'audit du 9 octobre 2026, dont
trois n'avaient aucun budget auquel se mesurer. Aucune n'a encore été mesurée : la première mesure se fera sur le code
non corrigé, et son résultat sera consigné ici, qu'il passe ou qu'il échoue.

| Opération | Fixture | Budget | Raison de la valeur |
|---|---|---|---|
| lecture du contenu d'un snapshot | les 2,000 changes et 4,000 critères du profil gelé, dans une base propre à cette mesure ; chaque change avec périmètre, hors-périmètre et risques, chaque contrainte avec cibles bloquantes et preuves, chaque critère avec preuves de vérification, chaque scénario avec préconditions | p95 <= 1,000 ms | une lecture interactive, comme la recherche de requirements ; elle est derrière la plupart des lectures des trois transports |
| analyse d'un change | une proposition dont chaque delta atteint, par des liens `DEPENDS_ON`, un sous-graphe au budget de nœuds de la traversée, dans les deux sens | p95 <= 2,000 ms | une analyse est une poignée de traversées et doit en coûter une poignée ; c'est le budget d'une traversée |
| lecture d'une page de journal | une page de 50 entrées dans un journal de 100,000 | p95 <= 1,000 ms | une lecture interactive ; **ce budget n'aura de gate que si la correction ajoute un index ou une migration** — sinon la preuve par comptage de la suite ordinaire suffit, et cette ligne sera retirée avec sa raison écrite ici |

Ce que ces lignes ne changent pas : le protocole (§ 3), le tas de 768 MiB, les tailles de la fixture `M19-LARGE-GATE-1`
et chaque mesure du tableau du gel. La mesure de lecture du contenu publie son propre snapshot dans son propre fichier,
pour que ni le temps de publication, ni la taille SQLite, ni le coût de rétention ne bougent.

Les valeurs suivent la recommandation de la spécification
(`openspec/changes/declare-a-budget-before-bounding-a-cost`), qui les laissait au mainteneur ; il a lancé le sprint sans
les fixer. Elles sont choisies d'après ce à quoi sert l'opération, pas d'après une exécution.

## 5. Budgets de comportement non temporels

Ces contraintes sont également bloquantes :

```text
same fixture + same baseline -> same ordered query results
same fixture + same seed -> same logical fixture manifest
incremental plan deterministic across runs
query page order deterministic
trace traversal bounded by maxDepth
failed candidate never becomes ACTIVE
previous ACTIVE remains authoritative after failed publish
SQLite reopen preserves last valid ACTIVE and composition provenance
```

## 6. Mesures informatives obligatoires

Elles sont enregistrées mais ne bloquent pas M19 tant qu'aucun seuil n'est défini ici :

```text
provider read timing per provider
external MINOS timing when enabled
external NEXUS timing when enabled
composition phase timing
SQLite file size per snapshot
history-retention cumulative size
number of operational warnings/errors
```

Une intégration optionnelle absente doit produire `UNAVAILABLE`/`DISABLED`, pas une mesure fictive de succès.

## 7. Reproductibilité Windows / Linux

Le même générateur, le même seed et les mêmes budgets sont utilisés sur Windows et Linux.

```text
Windows PASS != Linux PASS
Linux PASS != inferred from Windows
```

Le validateur Windows enregistre la preuve Windows. Une exécution séparée de `scripts/validate-m19.sh` doit enregistrer la preuve Linux. Si elle n'existe pas, l'état reste explicitement `LINUX PROOF MISSING`.

## 8. Politique anti-déplacement des seuils

Après ce commit :

- une optimisation peut faire baisser les temps/mémoire/taille ;
- une correction de mesure peut être acceptée si le protocole était objectivement erroné ;
- un seuil ne peut pas être augmenté simplement parce que le code le dépasse ;
- un changement de fixture/seuil exige une justification dans la PR et l'issue M19, et invalide la comparaison avec la baseline précédente.

Cette règle est un invariant de gouvernance de M19.
