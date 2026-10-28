# Revérification du 28 octobre 2026 — sprints 1, 5, 7 et 8

Base : `origin/develop` à **`6c6a97f6`** (PR #576, 28/10/2026).
Périmètre : les **sept constats** encore ouverts des sprints 1, 5, 7 et 8, traités par les PR #570 à #576. Les
autres sprints gardent les statuts établis le 26 octobre sur `4694b8b2` : la PR #569 (sprint 9), fusionnée entre
les deux passes, n'est **pas** revérifiée ici.

Chaque statut est relu dans le code de `develop` après fusion, pas dans la description des PR.

## Les PR

| PR | Fusion | Constat | Ce qui change |
|---|---|---|---|
| #570 | `cd859f75` | `AUD-TST-09`, `AUD-TST-19` | Plafonds de couverture requalifiés sur `61198c68` (quatre runs CI, Linux et Windows), quatre ratchets relevés, marge « trois fois la dispersion » exécutée par un test, raison de la marge de dix écrite à côté d'`architectureTestsMinimum` |
| #571 | `283e4499` | `AUD-DEP-07` | Alerte `REFRESH_KEY_UNUSABLE` dès le premier run, RT-13 rouvert, rotation de `NVD_API_KEY` documentée |
| #572 | `0b7d2029` | `AUD-PRF-09` | Change OpenSpec archivé sur la décision du mainteneur du 17/10/2026 ; test qui épingle la répartition par rôle |
| #573 | `4ccb846d` | `AUD-TRV-07` | Lane nocturne `windows-validators` pour les huit `.ps1` ; règle `everyValidatorIsLaunchedByAWorkflow` |
| #574 | `0e07f6bf` | `AUD-TST-10` | Bases réelles de v1.0.0, v1.1.0 et v1.2.1, test d'upgrade depuis elles, R2 exige un cas par palier déclaré |
| #575 | `6b327aed` | `AUD-PRF-14` | Taille et lignes mesurées par la passe qui décode, chaque répertoire listé une fois |
| #576 | `6c6a97f6` | `AUD-TST-19` | `testsMinimum` et `generatedTestsMinimum` à dix sous la mesure, avec leur raison |

## Statuts

| Constat | Sprint | 26/10 | 28/10 | Ce qui reste |
|---|---|---|---|---|
| `AUD-DEP-07` | 1 | partiel | **partiel** | Source unique de rafraîchissement tant que Dependency-Check 13.0.0 est épinglé ; bloqué en amont (#8716 non publié). La dérive RT-13 est corrigée |
| `AUD-PRF-09` | 5 | toujours valide | **toujours valide, clos par décision** | Aucun délai amont pour WRITE et ADMIN : risque accepté, documenté dans `docs/user/TEAM_REMOTE_SERVER.md` |
| `AUD-PRF-14` | 7 | toujours valide | **corrigé** | — |
| `AUD-TRV-07` | 8 | partiel | **corrigé** | Premier verdict réel des jambes Windows `d2` et `r3` à la prochaine nocturne |
| `AUD-TST-09` | 8 | partiel | **corrigé** | — |
| `AUD-TST-10` | 8 | partiel | **corrigé** | Les paliers 21 et 22 fourniront leur base à la release qui les porte ; le test refuse d'ici là |
| `AUD-TST-19` | 8 | partiel | **corrigé** | — |

Preuves ligne à ligne dans `findings.json` (champs `verif_preuve`, `verif_constat`, `reste`) et `findings.md`.

## Comptage

**Plan du 9 octobre : 73 constats, 54 soldés, 19 restants** (48 et 25 le 26 octobre). Les sprints 5, 7 et 8 sont
terminés ; le sprint 1 garde `AUD-DEP-07`, que le dépôt ne peut pas lever seul.

`AUD-PRF-09` est compté **soldé** sans être corrigé : son statut reste « toujours valide » parce que le code n'a pas
changé, et c'est une clôture par décision du mainteneur. C'est le seul constat dans ce cas.

## Ce que cette passe n'a pas fait

- **Aucun build.** Les statuts reposent sur le code de `develop` et sur les checks requis des PR, tous verts sur leur
  tête mise à jour avant fusion (`java`, `exact-head` Linux et Windows, `dependency-check`).
- **La lane `windows-validators` n'a pas tourné sur `develop`.** Son seul run est le run manuel de la branche
  (`40607562511`) : `m21` à `m26` verts, `d2` et `r3` refusant à dessein un arbre qui modifie un workflow. La nocturne
  planifiée ne tourne que sur `main`.
- **Le sprint 9 n'est pas revérifié**, bien que la PR #569 soit fusionnée.
