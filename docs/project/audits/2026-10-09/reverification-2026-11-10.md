# Revérification du 10 novembre 2026 — `38a4079f`

Base : `origin/develop` = **`38a4079f`** (PR #623), **17 commits, 31 fichiers** (+798 / −152) depuis `00ca1378`.
Méthode inchangée : *une revendication n'est pas une preuve, seul le code tranche.*

> **Rappel de piège, cinquième passe consécutive.** Le clone d'audit ne ramène que `main`. `origin/develop` se lit par
> `git ls-remote --heads origin` et se récupère par `git fetch origin '+refs/heads/develop:refs/remotes/origin/develop'`.
> S'y ajoute cette fois un piège de session : **le conteneur a été remplacé**, le clone et mes scripts avaient disparu.
> Le clone neuf pointe sur `main` ; les premières commandes ont donc lu l'arbre de `main` et affiché cinquante
> changements OpenSpec « en cours » qui sont archivés sur `develop`. Corrigé par un `checkout --detach origin/develop`
> avant toute mesure, et aucune conclusion de cette passe ne vient de la lecture fautive.

## Ce qui a changé, et ce que cela n'est pas

Cinq pull requests : **#619** PIT 1.30.1 → 1.30.2, **#620** `codeql-action` 4.39.1 → 4.39.3, **#621** `setup-java`
6.1.0 → 6.2.0, **#622** les scripts de fumée MINOS et NEXUS portés sur les deux plateformes, **#623** la publication de
mes fichiers d'audit du 7 novembre au soir.

**Aucun fichier `.java` ne change.** Mesuré, pas déduit : `git diff --name-only 00ca1378..38a4079f -- '*.java'` renvoie
zéro, et le diff par extension donne 19 `.md`, 5 `.yml`, 2 `.yaml`, 2 `.sh`, 2 `.json`, 1 `.xml`. Les quatre
revendications du dépôt que je pouvais vérifier le sont : les trois montées d'action GitHub sont **épinglées par SHA 40
avec leur commentaire de version** et aucun `uses: …@vN` mutable n'apparaît dans les cinq workflows ; les scripts de
fumée arrivent en paire `.ps1` / `.sh` pour chacun des deux pairs, donc la parité que le dépôt exige de ses validateurs
est tenue ici aussi — mais **aucun workflow ne les lance**, ce qui est normal : ils réclament un vrai JAR MINOS ou
NEXUS. Ce n'est pas un constat, c'est une propriété de ces scripts, et la documentation le dit.

**Le carnet OpenSpec est vide.** `openspec/changes/` ne contient plus que son `archive/` : **94 changements archivés,
zéro en cours**, les deux derniers — `resolve-both-mcp-peers-through-one-settings-resolver` et
`share-one-lifecycle-between-the-two-mcp-gateways` — archivés le 10 novembre avec la synchronisation de leurs
spécifications. C'est la première fois depuis le début de la mission. C'est aussi, je crois, ce qui a motivé la
demande : **un carnet vide n'est pas un audit soldé**. Un changement OpenSpec est du travail planifié ; un constat
d'audit est un défaut observé. Les trois constats encore actifs n'ont jamais eu de changement ouvert — les deux plus
récents sont nés le 7 novembre au soir, après la dernière vague.

## Les trois constats actifs : revérifiés, inchangés

Je ne les ai pas déclarés inchangés parce que le diff ne touche pas Java — je les ai rouverts un par un.

**`AUD-TRV-02`** — Les six clés au plancher absolu D2 sont relevées une à une dans
`config/m21-per-module-coverage-floors.properties` : `provider-testkit` et `provider-reference` entièrement, les seules
branches d'`integration-minos` et d'`integration-nexus`, sur 32 clés. Les quatre seuils de
`config/m21-quality-ratchets.properties` sont identiques au millième, donc l'écart entre les deux échelles n'a pas pu
bouger : il reste à **2,6823 point** en lignes. Sa preuve de qualification porte toujours le commit `dbd65062`, qui
s'éloigne : **25 commits** de la tête aujourd'hui, contre 8 le 7 novembre. Ce n'est pas une dérive — rien n'a changé
dans la couverture — mais la distance est à surveiller.

**`AUD-TST-25`** — `AWAITING_A_RULE_CEILING` et `UNEXPLAINED_TYPE_BAN_CEILING` valent toujours 0, assertés par égalité,
et la recherche des deux noms sur l'ensemble du dépôt ne sort d'aucun `.properties` :
`config/debt-counter-ratchets.properties` garde ses six clés. Le dépôt a pourtant créé une **septième clé de cliquet**
le 7 novembre, dans un fichier à elle, pour les suppressions CVE — le patron existe, documenté dans
`.claude/rules/build.md`, et il n'a pas été repris ici.

**`AUD-ARC-21`** — Le record `Kind` n'offre toujours que ses quatre fabriques, sans `tableOfFactsUnder` ; les deux
`CONFINED_FAMILIES` des modules de transport restent déclarées sans ADR ; et `ARCH-0165` n'est cité par **aucun
`.java`** du dépôt, trois jours après son acceptation.

## Un constat neuf : `AUD-QUA-23`

Il sort de la vague Dependabot, mais sa dérive la plus ancienne date du **19 octobre**.

Trois cellules de tableau écrivent une propriété du POM avec une valeur que le POM ne porte plus :
`docs/developer/CODE_AUDIT.md:41` et `docs/developer/SPOTBUGS.md:16` donnent `spotbugs.maven.plugin.version` =
`4.10.4.1` quand `pom.xml:88` dit `4.10.5.0` — bump `2b63af5f` du 19/10 — et `CODE_AUDIT.md:43` donne
`pitest.maven.plugin.version` = `1.30.0` quand le POM dit `1.30.2`, soit **deux bumps de retard**. La prose les répète
six fois, dont deux affirmations désormais fausses : « SpotBugs 4.10.4.1 est la dernière version stable » et
« `latest` = `release` = 4.10.4.1 ».

Le gate qui sait comparer exactement cette forme existe —
`GovernanceSurfaceCoherenceTest#everyVersionAGovernancePageWritesAsAPomPropertyEqualsThePom`, celui qu'`AUD-DEP-29` a
fait écrire. Sa portée est déclarée en deux constantes : `GOVERNANCE_PAGES = [".claude/CLAUDE.md",
".github/AI_GOVERNANCE.md"]` et `GOVERNANCE_DIRECTORIES = [".claude/commands", ".claude/agents", ".claude/rules"]`.
**Tout `docs/` lui est invisible.** J'ai rejoué son prédicat sur l'ensemble de `docs/`, relevés datés écartés : ces
trois cellules sont les seules en défaut, donc le correctif du cas est petit et c'est la portée qui compte.

Sévérité **Faible** : SpotBugs et PIT sont des profils opt-in qu'aucun workflow bloquant n'exécute. Ce qui justifie de
le suivre est que c'est exactement la dérive pour laquelle `.claude/rules/meta.md` a été écrit — « trois chiffres
différents pour le même seuil » — et que le dépôt l'a résolue d'un seul côté de la frontière. **Elle est aussi passée
sous quatre de mes propres revérifications** : je comparais les versions du POM aux pages de `.claude/`, pas à celles
de `docs/developer/`.

## Contrôles

- **Build** : `./mvnw clean verify` sur `38a4079f`, machine du mainteneur — **vert** — 19 modules, **5 413 tests, 0 échec, 14 ignorés**, `BUILD SUCCESS` en **9 min 22**, verdict séparé `documentation drift: 0` / `architecture violation: 0`. Totaux par module identiques à ceux du 7 novembre au soir, ce qu'on attend d'une base où aucun fichier `.java` ne change. Les dix-huit totaux par module sont identiques un à un à ceux du 7 novembre au soir — ce qui est la vérification, et non la supposition, qu'aucun comportement n'a bougé. Ce run a de nouveau demandé bien plus d'une heure d'horloge pour neuf minutes de temps Maven : la machine se met en veille entre mes relevés, ce n'est pas le dépôt qui ralentit. Les statuts de cette passe ne dépendaient de toute façon d'aucune exécution, puisque aucun code ne change
- **Méthode des scores rejouée** avant toute modification : le barème reconstruit depuis `findings.json` redonne
  exactement les six axes et la moyenne 98 publiés le 7 novembre au soir. Mes scripts avaient disparu avec le
  conteneur ; c'est la vérification qu'ils ont été reconstruits à l'identique.
- **Charge restante** recalculée depuis les efforts : 3,75 à 11 jours, cohérente avec le tableau d'avancement.
- Les relevés de couverture n'ont pas été remesurés : aucun code ne change, et les quatre seuils sont identiques.
- **Ce que je n'ai pas vérifié** : qu'aucune dépendance ne soit en retard sur sa dernière version publiée
  (`AUD-DEP-17`, Info). Le sandbox n'atteint pas Maven Central et je n'ai pas d'accès API au dépôt ; cet Info garde son
  statut de la passe précédente sans mesure neuve.

## Scores

| Axe | 7 nov. soir | 10 nov. |
|---|---|---|
| Sécurité | 100 | **100** |
| Performance | 100 | **100** |
| Dépendances | 100 | **99** |
| Architecture | 98 | **98** |
| Tests | 95 | **95** |
| Qualité | 95 | **94** |
| **Moyenne** | **98** | **98** |

**130 constats : 99 corrigés · 1 partiel · 3 toujours valides · 4 caducs · 22 Info qui tiennent · 1 non vérifiable.**
**4 constats actifs** : 1 `Moyenne`, 3 `Faible`. Aucun `Critique` ni `Élevée` depuis le 28 octobre.

## Réserves

- Le carnet OpenSpec vide invite à conclure que tout est fait. Les quatre constats actifs n'ont **aucun changement
  ouvert** : les traiter demande d'en ouvrir, ce que le prompt du 6 novembre sait faire.
- Les trois constats du 7 novembre au soir décrivent des faiblesses de **mécanisme**, pas d'état : les deux plafonds
  valent zéro, les deux listes sont exactes, les trois cellules sont presque sans conséquence. Leur coût est dans la
  facilité du retour en arrière, et c'est pour cela qu'ils restent ouverts.
- `AUD-QUA-23` est le troisième constat de la même famille en deux passes (`AUD-QUA-21`, `AUD-QUA-22`) : une
  affirmation vraie le jour où elle est écrite, dans un document vivant, qu'aucun gate ne relit. Les trois se ferment
  par le même geste — étendre la portée d'un contrôle qui existe déjà.
