# ADR-0103 — Assertion textuelle et règle ArchUnit n'enforcent pas la même chose

- Statut : **Acceptée — pilote livré, généralisation non décidée**
- Date : 10 septembre 2026
- Dépend de : ADR-0078 et ADR-0093 (sémantique tri-state), ADR-0085 (déterminisme des gates)
- Portée : `morpheus-architecture-tests` — critère de choix du mécanisme d'enforcement

## Contexte

`morpheus-architecture-tests` compte 119 classes de test. Une large part de ses assertions ne sont pas
des assertions sur du comportement mais sur du **texte** : elles lisent un fichier du dépôt et y
cherchent une chaîne. Une sous-famille a la forme strictement mécanique :

```java
assertFalse(<source .java lu>.contains("<Identifiant>"));
```

Chacune dit « cette classe ne doit pas dépendre de celle-là », ce qui est mot pour mot l'énoncé d'une
règle ArchUnit. ArchUnit 1.5.0 est déjà une dépendance déclarée du module et n'y servait avant ce
pilote que dans **cinq** classes.

Les totaux exacts dépendent de la regex de comptage et **ne doivent pas être recopiés** (`rules/meta.md`).
Mesure du 10/09/2026 sur `origin/develop`, méthode citée pour être rejouable :

| Grandeur | Commande | Valeur mesurée |
|---|---|---|
| assertions du module | `grep -rhoE "assert[A-Z][A-Za-z]*\("` | 2 885 |
| dont `assertTrue/False(<var>.contains(` | `grep -rhoE "assert(True\|False)\([a-zA-Z0-9_]+\.contains\("` | 1 525 |
| famille mécanique ci-dessus | `grep -rhoE "assertFalse\([a-zA-Z0-9_]+\.contains\(\"[A-Z][A-Za-z0-9_]*\"\)\)"` | 198 |
| classes la portant | même motif, `grep -rl` | 30 |

L'audit du 10/09/2026 annonçait 2 880 / 1 428 / 197 / 32 avec une regex plus large — l'écart est de
définition, pas de fond, et **aucune décision de cet ADR n'en dépend**.

Ce même audit a résolu 502 de ces assertions jusqu'à leur fichier cible pour chercher celles que seul
un commentaire satisfait — le cas où l'assertion serait creuse. **Une seule sur 502**, et elle est
délibérée. Il n'y a donc **aucun contournement vivant** : ce qui suit réduit une dette de forme, ne
répare pas une fuite. La barre est en conséquence « aucune règle affaiblie », pas « le build est
vert ».

### La question que cet ADR tranche

La formulation intuitive — « ArchUnit est le bon outil, le texte est un pis-aller historique » — est
fausse, et il faut l'écrire avant qu'un mainteneur ne migre la famille entière sur cette croyance.

Les deux formes n'enforcent pas la même proposition :

| | `assertFalse(src.contains("X"))` | `noClasses()…should().dependOnClassesThat()…` |
|---|---|---|
| Interdit | la **mention** de `X` dans la source | la **dépendance** compilée vers `X` |
| Voit un nom en commentaire ou en littéral | **oui** | non |
| Voit une dépendance que personne n'a épelée | non | **oui** |
| Voit une dépendance indirecte | non | **oui** |
| Voit une référence à une constante de compilation | **oui** | **non** — javac l'inline |
| Portée | tout `src/main/java`, y compris hors classpath | le classpath du module de test |

**Aucune des deux colonnes ne domine l'autre.** Le choix se fait donc par intention, jamais par
préférence d'outil.

### L'angle mort mesuré, pas supposé

La quatrième ligne du tableau est celle qu'on oublie, et elle a été vérifiée expérimentalement sur le
pilote plutôt que déduite.

`javac` remplace toute référence à une constante de compilation (`static final` d'un type primitif ou
`String`, initialisée par une expression constante) par sa valeur. La classe compilée qui écrit
`MorpheusRemoteRoutePolicy.GET` porte le `String` `"GET"` et **aucune référence** à la classe dont il
vient. Une règle ArchUnit passe ; une recherche du nom échoue.

Parmi les huit identifiants interdits par le pilote, deux appartiennent à des classes qui déclarent
de telles constantes : `MorpheusHttpResponseWriter` (une) et `MorpheusRemoteRoutePolicy` (trois).
Toutes quatre sont `private` aujourd'hui — l'angle mort est donc fermé **par accident, pas par
conception** : ces classes partagent leur package avec leurs consommateurs, donc élargir une
constante en package-private est un changement d'un mot qui le rouvre sans rien pour le signaler.

L'expérience 5 du pilote a exécuté exactement ce scénario. La règle ArchUnit a **passé** ; seule
l'assertion textuelle conservée a mordu.

Et ce n'est pas un scénario de laboratoire : **l'angle mort est déjà exercé dans le code actuel.**
`MorpheusHttpServer` déclare `public static final String API_PREFIX = "/api/v1"`, empruntée par neuf
classes de `morpheus-api`, dont quatre routeurs `*HttpRoutes`. Chacune de ces références est inlinée
à la compilation : pour ArchUnit, ces routeurs ne dépendent **pas** de `MorpheusHttpServer`. Toute
règle qui prétendrait borner ce couplage sans assertion textuelle le manquerait aujourd'hui, sur du
code livré.

### Une seconde asymétrie : `contains` produit des faux positifs

L'inverse existe aussi et se constate sur les mêmes fichiers. `assertFalse(x.contains("HttpServer"))`
vise le type `com.sun.net.httpserver.HttpServer`, mais la chaîne est également un sous-mot de
`MorpheusHttpServer`. Six routeurs écrivent `MorpheusHttpServer.SyncRequest` ou
`MorpheusHttpServer.API_PREFIX` : appliquée à eux, l'assertion échouerait sur un couplage qu'elle ne
visait pas. Une règle de package ne confond pas les deux.

Le texte rate donc des dépendances réelles **et** en signale d'imaginaires ; la règle ArchUnit fait
l'exact opposé. C'est la raison de fond pour laquelle le choix se fait par intention et non par
préférence d'outil.

## Décision

**Le mécanisme d'enforcement se choisit par l'intention de la règle, énoncée en une phrase avant
d'écrire quoi que ce soit.**

### 1. Une règle ArchUnit remplace le texte quand l'intention est « aucune dépendance »

Et à trois conditions cumulatives, vérifiées et non supposées :

1. **La classe visée est réellement dans l'ensemble importé.** `importPackages("com.morpheus")` ne
   voit que le classpath de `morpheus-architecture-tests`. `morpheus-mcp-transport` y arrive
   transitivement via `morpheus-mcp` ; **`morpheus-provider-reference` et `morpheus-provider-testkit`
   n'y sont pas** — le premier est délibérément chargé comme JAR depuis `target/` par les tests M22.
2. **Le type interdit ne peut pas être atteint par une constante inlinée**, ou le texte est conservé
   en plus (voir 3).
3. **La règle exprime la vraie portée de l'intention.** Un littéral peut être un *préfixe de
   famille*, pas un nom de classe : `assertFalse(x.contains("MorpheusRemote"))` interdit dix types,
   pas un. Traduit naïvement en une règle sur une classe unique, il en perd neuf.

### 2. L'assertion textuelle reste seule quand l'intention est « ce nom ne doit pas apparaître ici »

C'est le cas de tout ce qui n'est pas du Java compilé, et de certains invariants de sécurité.

**Les assertions visant un artefact non-Java ne sont pas concernées par cette décision** — `.yml`,
`.ps1`, `.md`, `.iss`, `.yaml`, `.sh`, `.xml`. Pour un workflow, un script
ou un Markdown, le texte est la **seule prise possible** et c'est le bon outil. `.claude/rules/security.md`
l'écrit déjà : ces invariants sont *« assertés textuellement dans les sources »* et *« supprimer une
de ces chaînes casse le build »*. Rien ici ne change cela.

Restent seuls textuels, même quand la cible est du Java :

- **Les interdits de chaîne par nature** : `activateDefaultTyping(`, `enableDefaultTyping(`,
  `request.header("Authorization"`, `token + "|"`. Ceux-là sont scannés sur **tout**
  `src/main/java`, au-delà du classpath d'ArchUnit — les migrer perdrait les fichiers des modules
  absents de ce classpath. C'est la perte que cet ADR existe surtout pour empêcher.
- **Les expressions de câblage.** MORPHEUS câble explicitement (`.claude/rules/architecture.md`) et
  aucune règle sur le bytecode ne peut dire *quel argument* un constructeur a reçu.
  `new MorpheusDiagnosticsHttpRoutes(this.service)` n'est pas exprimable en ArchUnit.

### 3. Les deux formes coexistent quand chacune voit ce que l'autre rate

Le coût de deux assertions qui disent la même chose sous deux angles est très inférieur à celui d'une
règle perdue. La coexistence est donc le défaut en cas de doute, et elle est **obligatoire** dès que
le type interdit déclare une constante de compilation.

Elle est également retenue pour les frontières de sécurité, où la mention elle-même est un signal :
`MorpheusRemoteRole` est un `enum`, ses constantes ne sont jamais inlinées et la règle le voit — il
garde son assertion textuelle parce qu'il **est** la frontière RBAC.

### 4. Une règle migrée n'est acceptée qu'après avoir été cassée

Une règle qui passe ne prouve rien : une règle vide passe aussi. Toute règle migrée doit être
accompagnée de la violation qui la fait échouer, introduite puis retirée, et le résultat consigné.

Cette exigence n'est pas nouvelle dans ce dépôt : `CoverageQualityGateTest#ratchetRejectsARegressionThatTheOldD2FloorWouldHaveAccepted`
et `perModuleRatchetWindowRejectsAnUnqualifiedRaiseAndAReturnToTheD2Floor` prouvent déjà la fenêtre du
ratchet aux deux bouts au lieu de se contenter de la valeur configurée du jour.

Le filet d'ArchUnit y aide et **reste activé** : `archRule.failOnEmptyShould` vaut `true` par défaut
et rien dans ce dépôt ne le désactive. Une règle dont la clause `that()` ne retient aucune classe
échoue bruyamment. Vérifié : une règle pointée sur `ReferenceSpecificationProvider`, classe hors du
classpath, produit *« failed to check any classes »* et casse le build. **Ne pas le désactiver.**

### 5. Découper par intention, jamais par classe

Regrouper huit assertions textuelles en une règle ArchUnit par classe de test ferait **descendre** le
nombre de méthodes `@Test`, et `.claude/rules/testing.md` interdit d'affaiblir `architectureTestsMinimum`.

Le pilote montre que le découpage par intention produit l'effet inverse : une méthode qui portait deux
intentions et seize assertions en a donné **six**, et le compte du module est passé de **414 à 419**.
Compter avant, compter après ; si le total descend, c'est le découpage qui est faux, pas le ratchet.

## Conséquences

### Acquis

- Le pilote `LocalDiagnosticsHttpRoutesArchitectureTest` couvre strictement plus qu'avant :
  la famille `MorpheusRemote*` entière (dix types au lieu de deux), les packages
  `com.sun.net.httpserver..` et `tools.jackson..` entiers au lieu de trois noms, et toute méthode
  nommée `routeDiagnostics` quelle que soit sa signature.
- Sept violations ont été introduites puis retirées ; chacune a fait échouer la règle visée et elle
  seule. Le détail est dans la PR du pilote.
- `architectureTestsMinimum` n'est pas touché et ne l'a jamais été.

### Assumé

- **La migration est additive plus souvent que substitutive.** Trois des huit littéraux du pilote
  gardent leur assertion textuelle. Un mainteneur qui espérait supprimer 198 assertions doit
  s'attendre à en supprimer une partie seulement, et à en ajouter des règles.
- **Le gain n'est pas la réduction de volume, c'est la portée.** Compter les lignes supprimées est le
  mauvais indicateur ; ce qui compte est le nombre de violations désormais atteignables.

### Non décidé par cet ADR

La généralisation aux **vingt-neuf classes restantes** de la famille mécanique (trente la portent, une
est pilotée ; l'audit en annonçait trente-deux, écart de regex sans incidence). Le pilote donne la méthode et
son coût réel ; il ne donne pas mandat. Toute extension applique les cinq règles ci-dessus,
classe par classe, avec sa preuve de violation.

**Et surtout : le périmètre par classe est porteur, pas accidentel.** L'idée naturelle en voyant
trente classes répéter les mêmes interdits est d'exprimer la frontière **une fois** sur la famille
`*HttpRoutes`, comme `LayerDependencyTest` le fait pour les modules. Mesuré, ce raccourci est faux :
sur les dix-sept routeurs `*HttpRoutes` de `morpheus-api`, **neuf portent légitimement `HttpExchange`**
et le décodeur ou le mapper qui va avec — ce sont exactement ceux qui lisent un corps de requête. Une
règle unique sur la famille échouerait immédiatement, et l'affaiblir pour qu'elle passe reviendrait à
supprimer l'invariant des huit routeurs en lecture seule.

La partition utile n'est donc pas « par classe » ni « par famille » mais **par capacité** : les
routeurs qui lisent un corps et ceux qui n'en lisent pas. Trois interdits en revanche sont bien
famille-larges et vérifiés tels quels — aucun des dix-sept ne porte `MorpheusRemote*`,
`MorpheusHttpResponseWriter` ni `MorpheusHttpPathParser`. Ceux-là, et eux seuls, s'expriment une fois
pour toutes.

### Ce qu'il ne faut pas faire

Migrer `assertFalse(src.contains("activateDefaultTyping("))` vers ArchUnit. Ce scan porte aujourd'hui
sur **tout** `src/main/java`, y compris les fichiers absents du classpath des tests d'architecture.
Une règle ArchUnit équivalente serait silencieusement plus étroite — exactement le genre de perte que
la section 2 existe pour interdire.
