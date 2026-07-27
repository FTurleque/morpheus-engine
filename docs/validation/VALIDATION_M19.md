# VALIDATION M19 — Production Hardening, Scale & Operability

Statut : **VALIDÉ TECHNIQUEMENT — SHA DE CODE EXACT TESTÉ — NON MERGÉ**

Issue : #88

PR : #89

Branche : `m19/production-hardening-scale-operability`

SHA de code exact : `dca27db969b426ad43941ccb8cee7e926efb931b`

## 1. Verdict

> MORPHEUS reste-t-il déterministe, observable et exploitable sur des dépôts réalistes de grande taille, avec des limites et performances mesurées plutôt que supposées ?

**Oui**, pour le profil gelé `M19-LARGE-GATE-1` et les environnements de preuve décrits ci-dessous.

Les gates locaux Windows et Linux ont réellement exécuté le même candidat de code. Les budgets ont été gelés avant optimisation au commit `fb3dc4741e1357ea69ae8e797d5cfa674b8f80d2` et n'ont pas été relevés après mesure.

```text
Final M19 code SHA     dca27db969b426ad43941ccb8cee7e926efb931b
Windows proof          PASS
Linux proof            PASS
Tests                   449/449 PASS sur chaque plateforme
Architecture            178/178 PASS sur chaque plateforme
Reactor                 14/14 SUCCESS sur chaque plateforme
Failures/errors/skipped 0/0/0
Packaging/smokes        PASS sur chaque plateforme
Budgets                 PASS sur chaque plateforme
```

GitHub Actions n'est pas une source de vérité M19. La tentative `.github/workflows/m19-validation.yml` a été supprimée. Les preuves autoritatives sont le Maven Wrapper et les validateurs locaux versionnés.

## 2. Baseline d'entrée et réalignement Git

```text
M18 issue                 #85 CLOSED
M18 PR                    #86 MERGED
M18 code gated            7e8caacff567f51354fcb88bd7505a6d135071c0
M18 merge                 30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
Réconciliation PR         #90 MERGED
Réconciliation head testé 9facf59ca496cf0cb0f03fc0363e47768bb127ba
Base main M19             2853318cba7a067a430dbd719d619529fdf85edf
```

Les 67 commits propres à M19 ont été transplantés de l'ancienne baseline documentaire `672d69417c2b9476cf8ddcfea5f5289af3d5aee4` vers cette base. Le `git range-diff` a confirmé leur équivalence et l'ancienne documentation de la PR #87 n'a pas été réintroduite.

## 3. Budgets gelés et résultats mesurés

Source : `docs/roadmap/M19_PERFORMANCE_BUDGETS.md`  
Profil : `M19-LARGE-GATE-1`

| Mesure bloquante | Budget gelé | Windows | Linux | Résultat |
|---|---:|---:|---:|---|
| inventory scan p95 | ≤ 20 000 ms | 699 ms | 107 ms | PASS |
| incremental diff/plan p95 | ≤ 2 000 ms | 8 ms | 14 ms | PASS |
| full publish p95 | ≤ 60 000 ms | 3 070 ms | 2 408 ms | PASS |
| requirement search p95 | ≤ 1 000 ms | 111 ms | 65 ms | PASS |
| trace traversal p95 | ≤ 2 000 ms | 249 ms | 167 ms | PASS |
| composition build | ≤ 1 000 ms | 33 ms | 41 ms | PASS |
| composition conflicts query | ≤ 1 000 ms | 25 ms | 20 ms | PASS |
| composition status query | ≤ 1 000 ms | 30 ms | 21 ms | PASS |
| SQLite reopen p95 | ≤ 2 000 ms | 7 ms | 2 ms | PASS |
| packaged startup p95 | ≤ 5 000 ms | 159,6 ms | 290,6 ms | PASS |
| heap ceiling | ≤ 768 MiB | 768 MiB | 768 MiB | PASS |
| SQLite / 5 snapshots | ≤ 512 MiB | 251 360 832 octets | 251 687 960 octets | PASS |
| retention growth | ≤ 128 MiB | 50 811 080 octets | 50 958 464 octets | PASS |

La fixture logique couvre notamment :

```text
5,000 source files / >= 10 MiB
50 modified files incremental
10,000 requirements
25,000 traceability links
10,000 provider observations
1,000 composition conflicts
5 retained published snapshots
```

Les traversées mesurées ont produit 2 730 liens / 1 583 nœuds sur chaque plateforme. La génération de fixture est exclue des latences applicatives.

## 4. Preuve Windows

Commande canonique réellement exécutée depuis le dépôt local :

```powershell
.\validate-m19.cmd
```

Environnement enregistré :

```text
Date de fin             2026-07-27 02:46:02+02:00
OS                      Windows 10 x64
CPU                     16 processeurs logiques
Mémoire                 47,9 GiB
Filesystem              N:\ NTFS, fixed NVMe SSD
Java                    24.0.1
Maven Wrapper           3.9.16
SHA avant/après gate    dca27db969b426ad43941ccb8cee7e926efb931b
```

Résultats :

```text
Tests                   449
Failures                0
Errors                  0
Skipped                 0
Suites                   127
Architecture            178/178 PASS
Reactor                 14/14 SUCCESS
BUILD                    SUCCESS
Full reactor            69,2 s
Robustness contracts    10,4 s
Performance gates       187,8 s
Packaging/smokes        24,5 s
Portable ZIP            36 629 559 octets
Packaged CLI            PASS
Health/readiness/metrics PASS
Packaged startup        PASS, p95 159,6 ms
Exact-head stability    PASS
```

## 5. Preuve Linux

Commande canonique réellement exécutée dans une copie Git propre sur ext4 :

```bash
scripts/validate-m19.sh
```

La copie `/tmp/morpheus-m19-linux-final-dca27db` a été vérifiée sur le même SHA exact avant et après le gate. L'exécution sur ext4 évite de présenter la latence spécifique au montage WSL `9p` comme une propriété de MORPHEUS.

Environnement enregistré :

```text
Date de fin             2026-07-27 00:53:45Z
OS                      Ubuntu sous WSL2, Linux 6.18.33.2, x86_64
CPU                     16 processeurs logiques
Mémoire                 23,4 GiB
Filesystem              ext4 local WSL (/dev/sdf)
Java                    21.0.11
Maven Wrapper           3.9.16
SHA avant/après gate    dca27db969b426ad43941ccb8cee7e926efb931b
```

Résultats :

```text
Tests                   449
Failures                0
Errors                  0
Skipped                 0
Suites                   127
Architecture            178/178 PASS, 41 suites
Reactor                 14/14 SUCCESS
BUILD                    SUCCESS
Full reactor            66 s
Robustness contracts    8 s
Performance gates       339 s
Packaging/smokes        20 s
Portable TAR            39 447 553 octets
Packaged CLI            PASS
Health/readiness/metrics PASS
Runtime modules         PASS
Packaged startup        PASS, p95 290,6 ms
Exact-head stability    PASS
```

Un premier essai sur le workspace monté via `/mnt/n` n'est pas compté comme preuve : un checkout local ancien avait conservé `mvnw` en CRLF, puis six tests MCP STDIO ont expiré à cause de la latence du filesystem `9p`. Le même test ciblé a passé en 1,0 s sur ext4. Aucun budget ni timeout n'a été modifié pour contourner ce résultat et aucun PASS Linux n'a été revendiqué avant le gate ext4 complet.

## 6. Robustesse et recovery prouvés

Les contrats M19 exécutés dans les deux gates couvrent :

```text
source partielle ou contenu normalisé invalide -> candidat non publiable
FAILED candidate -> ancien ACTIVE conservé
rebuild valide après échec
stale BUILDING/VALIDATING -> FAILED après cutoff explicite
recovery Memory + SQLite + reopen
ACTIVE/RETIRED jamais mutés par stale recovery
database locked -> diagnostic stable et délai borné
reprise après libération du lock
lecteurs concurrents -> vue ACTIVE complète
activation concurrente -> un seul successeur
migration V011-like -> V012 compatible
publication candidate/activation transactionnelle et failure-atomic
```

Un échec de construction, validation ou persistance n'expose donc jamais un `ACTIVE` partiellement construit.

## 7. Observabilité et sécurité locale prouvées

```text
événements structurés et codes diagnostiques stables
redaction secrets/chemins avant écriture
attributs à cardinalité bornée
compteurs et timings process-local
timings sync/provider/composition/intégrations externes
health distinct de readiness
readiness avec opération réelle sur le store local
routes HTTP /health, /readiness et /metrics câblées et testées
répertoires ignorés et liens symboliques non suivis par défaut
source root symbolique refusée
durcissement owner-only lorsque supporté
permissions d'un parent utilisateur préexistant préservées
tous les entry points SQLite publics utilisent la factory durcie
journal PERSIST owner-only ; WAL/SHM absents ; locking NORMAL
```

Aucune télémétrie externe n'est requise.

## 8. Packaging et configuration de développement

Les archives Windows et Linux, leurs launchers, les smokes CLI, les routes HTTP réellement câblées et le démarrage packagé ont passé les gates de plateforme.

La configuration IntelliJ partagée `.run/MORPHEUS API (dev).run.xml` est versionnée et lance `com.morpheus.cli.MorpheusMain` depuis le module `morpheus-cli`, avec les données/configurations sous `target/morpheus-dev`.

## 9. Chronologie et règle exact-head

Les corrections exécutables ont été terminées avant les deux gates finaux. Le SHA de code `dca27db969b426ad43941ccb8cee7e926efb931b` est la référence de qualification.

Les mises à jour post-gate sont exclusivement documentaires. Leur diff doit être vérifié avant publication ; elles ne changent ni le code, ni les scripts, ni les budgets, ni les artefacts qualifiés.

## 10. Gouvernance

M19 est techniquement terminé et qualifié. La PR #89 peut devenir **Ready for review** après vérification finale de son diff et de sa mergeabilité. L'issue #88 reste ouverte jusqu'à l'intégration décidée par le propriétaire.

**Aucun merge M19 sans autorisation explicite de l'utilisateur.**
