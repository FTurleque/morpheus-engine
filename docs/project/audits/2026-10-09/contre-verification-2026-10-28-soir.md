# Contre-vérification du 28 octobre 2026, au soir — sprints 1 et 5, et un build rouge

Base : `origin/develop` à **`9c027eeb`** (PR #579).
Point de départ : `5c3ce4dd`. Écart : **7 commits, 2 PR (#578, #579), 36 fichiers, +1 570 / −365.**

Troisième document du 28 octobre. Il contre-vérifie les deux clôtures annoncées par
`reverification-2026-10-28-sprints-1-5.md`, répare une perte de fusion, et rapporte un build rouge.

---

## 1. Les deux clôtures tiennent

**`AUD-DEP-07` — corrigé.** Le constat disait : le gate CVE a un point de défaillance unique, `NVD_API_KEY`,
seul écrivain possible de la sentinelle de rafraîchissement, parce que Dependency-Check 13.0.0 ne sait pas se
rafraîchir anonymement. La correction ne change pas de version : elle change de **chemin de code**. Le flux
JSON 2.0 du NVD (`security.yml:26`) est une seconde voie, câblée à `:207-211`, qui ne passe pas
`-DnvdApiKeyEnvironmentVariable` et ne lit donc pas la clé. Elle s'essaie dans les trois cas où la première n'a
pas rafraîchi — clé refusée, échec avec clé, clé absente — et elle écrit la sentinelle (`:212-214`). Le point
de défaillance unique n'existe plus. Trois garde-fous le tiennent, dont un test Python qui exécute réellement
l'étape contre un faux wrapper Maven sur sept scénarios.

Une nuance de formulation, pas de fond : « échoue fermé » est plus exact en disant **« échoue fermé à
l'expiration du budget »**. Quand les deux voies échouent, le run passe tant que la sentinelle du cache
restauré est dans les 72 h ; il refuse au-delà, ou s'il n'y a pas de sentinelle du tout. C'est le dessin voulu,
antérieur au correctif.

**`AUD-PRF-09` — corrigé, et sur le bon objet.** Le nom du change OpenSpec dit *write*, mais son contenu dit
*upstream read timeout*, et c'est bien le délai de lecture amont qui a été posé :
`MorpheusRemoteProxyTransport.java:114` rend `request.timeout(...)` **inconditionnel**, et `:240-250` donne
deux valeurs — 60 s pour READ, 300 s pour WRITE. Le constructeur compact refuse une durée nulle : aucune route
ne peut représenter « pas de délai ». `MorpheusRemoteRoutePolicy.java:212-217` remplace le prédicat booléen par
un `upstreamDeadline()` dont la branche par défaut est le délai, pas son absence — un rôle ajouté demain hérite
d'une borne. Les deux codes de refus sont distingués, et celui de l'écriture dit que le résultat est inconnu
plutôt que de le déguiser en échec.

## 2. La suppression Dependency-Check est légitime

C'était le point à surveiller : une règle de suppression trop large transforme une correction en angle mort.
Vérifié — `config/dependency-check-suppressions.xml` ne gagne **aucune règle** et n'élargit **aucune portée**.
Le seul changement est l'attribut `base="true"` sur la règle existante, pour qu'une base neuve, où
l'association erronée n'existe pas, ne fasse pas échouer le scan sur `failBuildOnUnusedSuppressionRule`. Le
`packageUrl` reste ancré, le CPE reste unique, et le vrai pilote `org.xerial:sqlite-jdbc` n'est pas couvert par
la règle : il reste scanné.

## 3. Quatre constats naissent, dont un qui rouvre le sprint 1

**`AUD-TRV-08` — Moyenne — le build est rouge, et pas à cause du code livré.**
`./mvnw clean verify` : 4 666 tests, **2 échecs**, tous deux dans `morpheus-architecture-tests`, tous deux de
la même cause. `m20/ProductReleaseContractTest:34` marche depuis la racine, exclut `target`, attend 19 POM et
en trouve **57** : les 38 surnuméraires sont sous `.claude/worktrees`. `m21/ProductionIntegrityContractTest:117`
fait la même marche sur tout `src/main/java` et compte les `new UpdateDiscoveryService()`. Il y a **2 724
fichiers Java** dans ces worktrees, et `.gitignore` ne les couvre pas — il ne liste que
`.claude/settings.local.json`. La CI ne voit rien : elle part d'un checkout neuf.

Le verdict est faux dans le sens gênant — rouge sans raison — et c'est ainsi qu'on apprend à ignorer un gate.
Le risque symétrique est pire et invisible : le même motif `Files.walk` depuis la racine est répandu dans le
module de gates, et un test qui *cherche* une occurrence peut la trouver dans un worktree qui porte une forme
que le dépôt n'a plus.

**`AUD-DEP-27` — Moyenne.** La validation sur base NVD neuve a révélé que `cpe:/a:sqlite:sqlite` y est classé
dans l'écosystème natif, si bien que `sqlite-jdbc` n'est plus associé au moteur SQLite qu'il embarque. Une CVE
contre SQLite 3.53.x ne serait signalée que par l'identification `sqlite_jdbc`. C'est inscrit dans RT-13 comme
« écart de détection ouvert », et suivi par aucun constat ni aucun test.

**`AUD-TST-21` — Faible.** Le fichier de suppressions n'est tenu par aucune assertion, alors que le workflow
qui le consomme en a trois. L'état actuel est sain ; c'est la facilité d'un élargissement futur qui est le sujet.

**`AUD-PRF-20` — Faible.** L'attente d'un créneau de réponse en écriture n'a pas de délai propre : elle n'est
bornée que par composition, et le pire cas composé — environ 420 s — n'est écrit nulle part.

## 4. Une perte de fusion, réparée

Vingt de mes mises à jour du 28 octobre matin n'avaient pas survécu à la fusion : le dépôt portait encore le
texte du 26 pour `AUD-ARC-01`, `-03`, `-07`, `-08`, `-11`, `-13`, `-15`, `-17`, `AUD-DEP-05`, `-25`, `-26`,
`AUD-PRF-04`, `-05`, `-19`, `AUD-QUA-11`, `-17`, `AUD-SEC-17`, `AUD-TRV-02`, `-03` et `AUD-TST-20`. Deux
statuts en dépendaient — `AUD-TRV-02` et `AUD-DEP-26`, que j'avais passés à partiel. Tout est restauré et
revérifié sur `9c027eeb`.

## 5. Un erratum sur mon erratum

Ce matin j'ai porté `AUD-ARC-16` à caduc, au motif que la garde d'exactitude que je prescrivais existait déjà.
C'était exact, mais mon action comportait **deux** voies et j'avais oublié la seconde : écrire dans un ADR que
l'élargissement de visibilité est le prix du découpage. La session qui a travaillé ensuite l'a vu et a remis le
constat à **partiel**. Elle a raison, et je m'y range : je me suis trompé deux fois sur ce constat, d'abord en
réclamant une garde qui existait, puis en concluant trop vite à la caducité.

## 6. Score

| Axe | 28 oct. matin | 28 oct. soir | |
|---|---|---|---|
| Sécurité | 92 | **88** | −4 |
| Dépendances | 87 | **86** | −1 |
| Qualité | 88 | **84** | −4 |
| Performance | 78 | **81** | +3 |
| Tests | 83 | **78** | −5 |
| Architecture | 59 | **59** | — |
| **Moyenne** | **81** | **79** | **−2** |

Deux constats Élevée se ferment dans la journée sans que la moyenne monte, parce que quatre constats neufs
naissent des corrections elles-mêmes et du dispositif. Il reste **deux Élevée** : `AUD-ARC-01` et `AUD-PRF-04`.

## 7. Angles morts

- Build sur **Windows seulement**, comme les deux précédents.
- La mesure empirique du correctif CVE — 246 212 vulnérabilités chargées, aucune trace `Invalid API Key` — n'a
  pas pu être reproduite : pas de réseau, pas de base NVD. Le **câblage** est vérifié, pas le fait que le
  chemin data-feed réussisse réellement avec Dependency-Check 13.0.0. Si ce chemin s'avérait cassé en amont lui
  aussi, `AUD-DEP-07` retomberait sur la clé.
- `HttpRequest.timeout()` borne l'attente jusqu'aux en-têtes ; le ruissellement du corps est repris par
  l'interruption du writer borné. Le chemin complet paraît borné, mais par deux dispositifs dont un seul est
  nommé dans la preuve, et je n'ai pas pu vérifier à l'exécution qu'une lecture bloquée sur le flux du client
  HTTP du JDK réagit à l'interruption.
