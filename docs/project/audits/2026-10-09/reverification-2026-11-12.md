# Revérification du 12 novembre 2026 — `12cd8874`

Base : **`12cd8874`** = `origin/main`. **`develop` n'existe plus** : `git ls-remote --heads origin` ne renvoie qu'une
seule référence. Les six pull requests qui vivaient hier sur la machine du mainteneur sont poussées, et neuf commits
s'y ajoutent — **79 fichiers**, +1 114 / −390 depuis `555aa2e0`. Méthode inchangée : *une revendication n'est pas une
preuve, seul le code tranche.*

> **Le piège de la passe, et il a mordu.** J'ai enchaîné la lecture de `HEAD` et le lancement du build dans une seule
> commande `cmd` avec des `&&`. La lecture de `origin/develop` a échoué — la branche n'existe plus — donc la chaîne
> s'est arrêtée **avant** le build, qui n'a pas démarré. L'erreur était visible (`fatal: ambiguous argument`) ; sans
> elle, j'aurais attendu un build qui ne tournait pas. Les commandes qui mesurent et les commandes qui lancent ne se
> chaînent pas.
>
> **Et une situation à noter** : mes fichiers d'audit du 11 novembre sont **non commités** dans son arbre. Ma copie de
> référence vit hors du dépôt, donc aucune réconciliation n'était nécessaire cette fois — mais c'est la troisième
> passe de suite où la publication de l'audit traîne derrière la vérification.

## Ce qui s'est passé

Cinq pull requests. **#631** pousse l'état local vers `main`. **#633** fait de `main` la seule branche longue. **#634**
monte Jackson à 3.3.0. **#635** archive le changement OpenSpec `retirer-la-branche-develop`, fusionne ses trois
spécifications et répare la répétition de signature Windows. Entre-temps, un classifieur de quality gate Sonar arrive
avec son script, son contrat et ses tests.

## Le retrait de `develop` est tenu par un gate

`D2RepositoryHardeningArchitectureTest#noVersionedConfigurationNamesAnIntegrationBranchOtherThanMain` lit les
workflows, `.github/dependabot.yml`, `.archgate/config.json` et tous les `scripts/validate-*.{ps1,sh}`, et refuse le
mot `develop` avec une **frontière de mot** — `developer` et `docs/developer/` passent — sans lire les relevés datés,
« puisqu'ils décrivent ce qui était vrai à leur date ». Il assert en plus l'absence de `target-branch:` dans
Dependabot, qui vise donc la branche par défaut.

Vérifié de mon côté, un par un : les cinq workflows filtrent `[main]` ; Dependabot n'a plus de `target-branch` ;
`.claude/rules/governance.md` écrit « jamais force-pusher sur `main`, seule branche longue » ; `.claude/settings.json`
et `.claude/hooks/pre-bash.ps1` ont retiré leurs règles `develop` — ce n'est pas un affaiblissement, il n'y a plus
rien à protéger. Recherche exhaustive du mot dans tout le dépôt, archives et relevés écartés : **toutes les mentions
restantes sont des mesures datées**, y compris une section du registre des risques qui dit elle-même être « l'analyse
du 09/09/2026, conservée telle quelle ». Jackson 3.3.0 est répercuté aux trois endroits qui le nomment — le POM, la
table de `.claude/rules/build.md` et l'assertion littérale du gate D2.

**Une limite de portée, pour mémoire et non comme constat** : ce gate lit les configurations de pipeline et les
validateurs, pas les pages de `.claude/` ni de `docs/`. Elles sont justes aujourd'hui, et elles l'ont été à la main.

## Deux constats caducs décrivaient exactement ce qui vient d'arriver

Le 21 octobre, j'ai classé `AUD-DEP-23` et `AUD-DEP-24` **caducs**, en écrivant noir sur blanc que j'avais « pris une
fenêtre de release pour un changement de modèle ». C'était juste à cette date : la PR #515 avait resynchronisé
`develop`, et les dix-neuf PR suivantes y avaient toutes atterri. Le modèle n'avait pas changé.

Il a changé trois semaines plus tard. Et les quatre volets qu'`AUD-DEP-24` énumérait — Dependabot, les validateurs, la
documentation du modèle de branches, les voies nocturnes — sont précisément ce que la PR #633 a dû traiter.
**Mon erreur portait sur la date, pas sur le contenu.** Les deux constats restent caducs, parce qu'un constat caduc ne
se rouvre pas et que le travail est fait ; mais le relevé le dit, et c'est le genre de chose qu'un audit doit dire de
lui-même.

## Le dépôt a trouvé un défaut que son dispositif ne pouvait pas voir

Le premier nightly planifié sur `main` a échoué à la dernière étape de la répétition de signature Windows :
`Get-StoredCopies` rend un tableau vide, PowerShell le déroule en `$null`, et `$left.Count` lève
`PropertyNotFoundStrict` sous `Set-StrictMode -Version Latest`. Donc **le retrait réussi de l'identité de test
échouait toujours**. Le défaut datait du lot de signature et n'avait **jamais été exécuté** : la planification ne
tourne que sur `main`, qui ne l'a reçu qu'à la promotion. Un test rejoue désormais ces fonctions sous leur mode strict.

Je le relève parce que c'est la démonstration par l'incident de ce que cet audit répète depuis cinq semaines : un
chemin qu'aucune exécution n'atteint n'est pas tenu, quel que soit le nombre de règles autour. Le retrait de `develop`
raccourcit d'ailleurs ce délai — il n'y a plus de promotion à attendre pour qu'un chemin nocturne s'exécute.

## Le constat restant

`AUD-TST-26` est **inchangé**. `config/m21-per-module-coverage-floors.properties` n'est pas touché par cette vague,
les deux clés valent toujours 0,473 et 0,560, et les quatre résumés conservés sont les mêmes : les bornes admissibles
restent **0,873** et **0,905**, recalculées par la formule de `GEN-0166`. Le mou est intact à **40,0 et 34,5 points**.

## Contrôles

- **Build** : `./mvnw clean verify` sur `12cd8874`, machine du mainteneur — **vert** — 19 modules, **5 426 tests, 0 échec, 14 ignorés**, `BUILD SUCCESS` en **8 min 46**, verdict séparé `documentation drift: 0` / `architecture violation: 0`. Le module des gates déclare **1 091 exécutions** contre 1 090 la veille : la seule de plus est le test qui rejoue les fonctions de la répétition Windows sous leur mode strict. Cette exécution de plus est la preuve, et non la promesse, que le correctif de la répétition Windows est joué : le défaut avait tenu parce que personne ne l'exécutait
- **Barème rejoué** : reconstruit depuis `findings.json`, il redonne les six axes et la moyenne 99,83 publiés hier.
- **Formule de la marge rejouée** sur les deux clés du constat restant, avec les mêmes résumés conservés.
- **Étiquette de branche corrigée dans tout `findings.md`** : les 131 lignes de statut disaient « Statut sur
  `develop` », une branche qui n'existe plus. Elles disent maintenant « Statut sur l'intégration », et l'en-tête dit
  laquelle et depuis quand.
- **Ce que je n'ai pas vérifié** : que Jackson 3.3.0 soit la dernière version publiée (`AUD-DEP-17`, Info) — le
  conteneur n'atteint pas Maven Central ; et le classifieur Sonar neuf, dont j'ai lu le contrat et constaté qu'un
  workflow l'exécute, sans rejouer ses cas.

## Scores

Inchangés : Sécurité, Performance, Architecture, Qualité et Dépendances à **100**, Tests à **99**, moyenne **99,8**.

**131 constats : 103 corrigés · 22 Info qui tiennent · 4 caducs · 1 non vérifiable · 1 toujours valide.** Un seul
constat actif, `Faible`, effort S. Charge restante : 0,25 à 1 jour. Quatorze des quinze sprints soldés.

## Réserves

- Les fichiers d'audit du 11 novembre sont **non commités**, et ceux de ce soir s'y ajoutent. Le dépôt porte donc un
  relevé d'audit en retard de deux passes.
- Le gate du retrait de `develop` ne lit pas les pages de `docs/` ni de `.claude/`. Ce n'est pas un constat — elles
  sont justes — mais si une page recommence à décrire un modèle à deux branches, rien ne le dira.
- Avec un seul constat `Faible` ouvert, la valeur de cet audit n'est plus dans ce qu'il reste à corriger mais dans ce
  qu'il vérifie à chaque passe. La question honnête à poser au mainteneur est la cadence : revérifier à chaque vague
  ou passer à un rythme plus lâche, par exemple avant chaque release.
