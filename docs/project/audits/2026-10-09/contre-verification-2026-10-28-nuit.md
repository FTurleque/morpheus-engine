# Contre-vérification de la nuit du 28 au 29 octobre 2026 — plus aucun constat Élevée

Base : `origin/develop` à **`65a97b06`** (PR #580 `fix/audit-arc-01-prf-04`).
Point de départ : `9c027eeb`. Écart : **12 commits, 90 fichiers, +1 889 / −398.**

La PR vise les **deux derniers constats de sévérité Élevée**. Si ses verdicts tiennent, le dépôt n'en a plus
aucun — il y en avait onze le 9 octobre. C'est exactement pour cela qu'ils ont été examinés plus durement que
les autres : une clôture d'Élevée acceptée à tort est l'erreur la plus coûteuse que cet audit puisse commettre.

**Les deux tiennent.** Chaque fichier cité a été rouvert.

---

## 1. `AUD-ARC-01` — le réseau est sorti du cœur

Le port existe et n'est qu'un port : `application/product/UpdateManifestSource.java:20-22`, interface
fonctionnelle `byte[] read(URI, int, Duration)`, dont les seuls imports sont `java.net.URI` et
`java.time.Duration` — aucun type de transport ne fuit dans la signature. L'implémentation qui porte le
`HttpClient` vit dans `morpheus-cli` (`command/UpdateManifestFetcher.java:28`), câblée en un seul endroit.

Le recensement complet du réseau sortant dans `morpheus-application/src/main` — imports **et** usages
pleinement qualifiés — rend **6 occurrences, toutes `java.net.URI`**, qui est un type-valeur. Zéro
`java.net.http`, zéro `URL`, zéro socket, et zéro `Executors` non plus.

Deux choses méritent d'être relevées au-delà du verdict. D'abord le gate ne **confine** plus la famille
réseau, il la **refuse** : `ApplicationTechnologyBoundaryArchitectureTest:50-53` la fait passer dans
`UNUSED_FAMILIES`, appliquée à `com.morpheus.application..` tout entier. Ensuite la liste des classes à accès
fichier **rétrécit enfin**, de 12 à 11 — c'était le reproche porté par `AUD-ARC-17` : une liste qui ne devait
que décroître avait grandi à chaque extraction de socle.

Point vérifié par précaution, parce que c'est l'erreur classique d'un tel déplacement : la décision de
sécurité n'a **pas** suivi le transport. Le refus du schéma `http:`, la borne de taille et le délai restent
dans l'application, avant l'appel au port (`UpdateDiscoveryService.java:42, :61-68`).

## 2. `AUD-PRF-04` — les trois journaux sont bornés en SQL

`change_lifecycle_mutation_audit` reçoit `ORDER BY to_revision, mutation_id LIMIT ? OFFSET ?`, servi par un
index qui couvre exactement le `WHERE` et l'`ORDER BY`. `policy_audit` était déjà paginée.

`sync_source_archives` est le cas intéressant, parce que l'ordre stable n'allait pas de soi : `archived_at`
stocke un `Instant.toString()`, dont le texte ne trie pas par temps quand la fraction de seconde change de
longueur. La migration **V023** ajoute une colonne `archived_key` — epoch sur 13 chiffres, point, 9 chiffres
de nanosecondes —, la remplit pour l'existant, crée l'index correspondant et supprime l'ancien devenu mort ;
les nouvelles lignes l'alimentent. C'est le détail qu'une correction pressée aurait raté.

Le tri en mémoire a disparu des trois stores. Et un gate tient le résultat plutôt que la forme :
`SqliteQueryPlanGuardTest:148-180` lance `EXPLAIN QUERY PLAN` par le vrai pilote, signale tout balayage de
table ou arbre temporaire sans excuse écrite, et exige qu'une ligne `SEARCH` cite l'index nommé.

Ce qui reste linéaire — le `COUNT(*)` complet par page et le parcours d'`OFFSET` — est hors du périmètre du
constat, qui portait sur l'absence de borne et le tri en mémoire, et `BE-0151:148-149` l'assume par écrit.

## 3. `AUD-TST-20` — la garde est remplacée par plus fort

Le test que ce constat dénonçait comptait tout appel aux deux ports sans filtrer sur le nom de méthode. Il
n'existe plus. À sa place, une **énumération comparée par égalité stricte** à neuf noms qualifiés par leur
propriétaire : un renommage, un déplacement, une disparition ou un dixième sujet fait rougir. S'y ajoute un
test de cassure permanent qui soumet une fixture portant les deux formes interdites.

Et le piège que ce constat décrivait a été évité : l'ancienne règle, qui interdisait d'**appeler** des
méthodes devenues légitimes après pagination, aurait passé sur l'ensemble vide. Elle a été **retirée**, pas
laissée en place, et remplacée par une règle de signature.

---

## 4. Ce qui naît : `AUD-ARC-18`

Le transport sorti du cœur est arrivé dans `morpheus-cli`, et `UpdateManifestFetcher` y apporte **trois
familles techniques d'un coup** — réseau, accès fichier, fils d'exécution. Or
`ApplicationTechnologyBoundaryArchitectureTest` borne sa portée à `com.morpheus.application`, et aucun test ne
joue ce rôle pour `morpheus-cli`. Ce n'est pas une violation d'ADR-0150, dont la portée est la couche
applicative : c'est une zone non couverte, et c'est le prix du déplacement, que l'ADR n'écrit pas.

## 5. `AUD-TRV-08` : le symptôme a disparu, pas la cause

Le build est vert ce soir — mais parce que `.claude/worktrees` est actuellement **vide**. `.gitignore` ne le
couvre toujours pas, et les deux gates marchent toujours depuis la racine sans l'écarter. Pire, la PR #580 a
**aggravé** le second : son assertion a été repointée vers `morpheus-cli/MorpheusProductCli`, de sorte qu'une
copie de worktree contenant ce fichier ferait passer le compte de 1 à 2. Le rouge reviendra à la prochaine
session qui crée un worktree.

## 6. `AUD-ARC-17` gagne une démonstration

Ce constat disait que la table de l'ADR et les constantes du gate sont deux listes recopiées à la main que
rien ne compare. La PR #580 l'a démontré : la section « Mesure » d'ARCH-0150 liste encore
`UpdateDiscoveryService` — la classe qui vient d'en sortir — et omet `PeerLaunchSettingsResolver`. Les deux
listes comptent onze noms et diffèrent d'un nom chacune. C'est bénin ici, la section étant une mesure datée,
mais la divergence s'est produite à la première modification qui touchait les deux.

---

## 7. Le build

```
./mvnw -B -o clean verify      BUILD SUCCESS — 19/19 — 10 min 51
4 692 tests, 0 échec, 0 erreur, 13 skips
```

## 8. Score

| Axe | 28 soir | 28-29 nuit | |
|---|---|---|---|
| Performance | 81 | **91** | **+10** |
| Sécurité | 88 | **88** | — |
| Dépendances | 86 | **86** | — |
| Qualité | 84 | **84** | — |
| Tests | 78 | **82** | +4 |
| Architecture | 59 | **72** | **+13** |
| **Moyenne** | **79** | **84** | **+5** |

**Zéro constat `Élevée`** — il y en avait onze le 9 octobre. **Les neuf premiers sprints du plan sont
soldés** ; restent les sprints 10, 12, 13, 14, deux constats de fond au sprint 11, et les dérives.

## 9. Angles morts

- Build sur **Windows seulement**.
- Aucune exécution n'a confirmé que `EXPLAIN QUERY PLAN` choisit bien les deux nouveaux index : les colonnes
  correspondent exactement aux `WHERE` et `ORDER BY`, ce qui rend le choix attendu sans le garantir. Le gate
  `SqliteQueryPlanGuardTest` est fait pour trancher cela, et il est passé au vert dans ce build.
- `listAudit` et `listArchives` des ports lifecycle et sync n'ont **aucun appelant de production**. La
  correction est donc, pour deux des trois tables, une mise en conformité de signature plutôt que la
  réparation d'un chemin chaud. Le constat portait sur la forme du port, donc le verdict tient — mais la
  charge réellement évitée aujourd'hui est nulle.
- La mesure empirique du correctif CVE de la veille (base NVD reconstruite) reste non reproductible ici.
