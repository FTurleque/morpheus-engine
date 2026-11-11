# Revérification du 28 octobre 2026 — fin des sprints 1 et 5

Base : `origin/develop` à **`5c3ce4dd`** (PR #577), branche `fix/finish-sprints-1-5`.
Périmètre : les deux constats que laissait ouverts `reverification-2026-10-28.md`, `AUD-DEP-07` (sprint 1) et
`AUD-PRF-09` (sprint 5). Les statuts sont établis sur la branche ; ils valent pour `develop` à la fusion de sa PR.

## Statuts

| Constat | Sprint | Avant | Après | Ce qui reste |
|---|---|---|---|---|
| `AUD-DEP-07` | 1 | partiel | **corrigé** | Hors du constat, dans RT-13 : les deux sources viennent du NVD ; une base neuve n'associe plus `sqlite-jdbc` au SQLite natif qu'il embarque |
| `AUD-PRF-09` | 5 | toujours valide, clos par décision | **corrigé** | — |

## `AUD-DEP-07` — une seconde source de rafraîchissement

Dependency-Check 13.0.0 lit, par son chemin *data feed*, les flux CVE JSON 2.0 que le NVD publie
(`https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz`). Ce chemin ne demande pas de clé et n'atteint
pas le client API que casse l'amont #8715. `security.yml` l'essaie chaque fois que le chemin par `NVD_API_KEY` n'a
pas rafraîchi la base.

Mesuré sur Windows, sans `NVD_API_KEY`, avec le Dependency-Check épinglé :

| Commande | Résultat |
|---|---|
| `update-only -DnvdDatafeedUrl=…` dans un répertoire de données vide | `BUILD SUCCESS`, 10 min 45, aucune trace `Invalid API Key` ; 246 212 vulnérabilités, 267 955 entrées CPE |
| `aggregate -DautoUpdate=false`, profil `d2-security`, dépôt Maven local vide | `BUILD SUCCESS` après `base="true"` sur la règle `morpheus-store-sqlite` ; refus « 1 unused suppression rule » avant |
| `aggregate -DautoUpdate=false`, profil `d2-security-tests`, dépôt Maven local vide | `BUILD SUCCESS` |

Comparé au rapport du dernier scan de `develop` (run `40688562162`), le scan sur la base neuve trouve les mêmes
30 dépendances et diffère sur deux identifications. `morpheus-store-sqlite` n'est plus associé à tort à
`cpe:/a:sqlite:sqlite`. `sqlite-jdbc-3.53.4.0` garde `sqlite_jdbc` et perd `cpe:2.3:a:sqlite:sqlite:3.53.4.0`. La base
neuve range les 15 entrées `sqlite:sqlite` dans l'écosystème `native`, que l'analyseur écarte pour un JAR Java.
L'écart suit l'histoire de la base, pas sa source : une base reconstruite par l'API devrait se comporter de même,
ce que cette passe n'a pas pu mesurer sans clé.

## `AUD-PRF-09` — un délai pour chaque appel amont

Le mainteneur rouvre le 28 octobre la décision du 17 octobre. Le plan rédigé le 13 octobre est exécuté :
`openspec/changes/bound-every-write-of-the-remote-proxy/`. Une lecture garde 60 s, une écriture ou un appel ADMIN a
300 s, et une écriture expirée répond `504 UPSTREAM_WRITE_TIMEOUT`, un résultat inconnu. Le test de reproduction est
rouge sur `5c3ce4dd` et vert sur la branche ; l'essai de cassure (délai d'écriture retiré) fait échouer les deux
tests d'écriture.

## Ce que cette passe n'a pas fait

- **Pas de `clean verify` complet** ni de gate de couverture des lignes modifiées : la CI de la PR les rend.
  Exécutés : `./mvnw -pl morpheus-api -am test` (242 tests dans `morpheus-api`, 0 échec), cinq classes
  d'architecture touchées (76 tests, 0 échec), les tests Python du workflow (27, 0 échec), `archgate check`.
- **Le chemin par les flux n'a pas tourné en CI** : la clé y est acceptée, donc le chemin par clé réussit et les flux
  ne sont pas essayés. La preuve est la mesure locale ci-dessus et l'exécution de l'étape par
  `scripts/tests/test_trusted_update_step.py`.
- **Une route ADMIN en lecture** (`GET metrics`) reçoit le délai de son rôle, 300 s et `UPSTREAM_WRITE_TIMEOUT`.
