# Audit de code : SpotBugs et PIT

SpotBugs (analyse statique) et PIT (tests de mutation) sont deux profils Maven **opt-in**. Ni l'un ni l'autre ne
s'exécute dans `./mvnw clean verify`, ni dans la CI : ce sont des outils d'enquête que l'on lance à la main, sur un
périmètre choisi, pour produire des constats à qualifier.

Aucun seuil de qualité global n'est imposé par PIT. SpotBugs, lui, a un contrôle bloquant explicite (§ 7) qui est
**rouge sur le code actuel** : l'état de référence est consigné au § 12, il n'a pas été masqué pour obtenir un vert.

## 1. Le rôle de chacun

| Outil | Question posée | Ce qu'il lit | Ce qu'il produit |
|---|---|---|---|
| **SpotBugs** | « Ce bytecode contient-il un motif connu pour être fautif ? » | `target/classes` de chaque module | une liste d'alertes, avec confiance et rang |
| **PIT** | « Si je casse ce comportement, un test le remarque-t-il ? » | les classes ciblées **et** les tests du module | une liste de mutations tuées, survivantes ou non couvertes |

Aucun des deux ne dit « ce code est correct ». SpotBugs signale des **suspects** ; PIT mesure la **capacité des tests
à détecter une altération**, pas la valeur du code.

## 2. Complémentarité avec les contrôles existants

| Contrôle | Garantit | Ne dit rien sur |
|---|---|---|
| **JaCoCo** | quelles lignes et branches un test a *exécutées* | si le test aurait *échoué* en cas de défaut |
| **PIT** | si une altération du comportement est *détectée* par un test | le code que personne n'exécute dans ce module (il le compte « non couvert ») |
| **ArchUnit** (`morpheus-architecture-tests`) | la structure : couches, dépendances, contrats textuels | le comportement ; ces tests ne tuent aucune mutation d'un autre module (§ 9) |
| **SpotBugs** | des motifs fautifs du bytecode (nullité, exposition d'état, ressources) | l'architecture, la couverture, l'intention métier |
| **Dependency-Check** (`d2-security`) | les CVE des dépendances | le code de MORPHEUS lui-même |

JaCoCo répond à « exécuté ? », PIT à « vérifié ? ». Un module peut avoir une couverture élevée et un score de mutation
faible : c'est précisément le signal que PIT apporte.

## 3. Versions et compatibilité vérifiée

Toutes les versions sont des propriétés du POM racine ([`pom.xml`](../../pom.xml)), jamais `LATEST`.

| Propriété | Valeur | Rôle |
|---|---|---|
| `spotbugs.maven.plugin.version` | `4.10.4.1` | plugin Maven (publié le 05/09/2026) |
| `spotbugs.version` | `4.10.4` | moteur d'analyse, épinglé comme dépendance du plugin |
| `pitest.maven.plugin.version` | `1.30.0` | plugin Maven PIT (publié le 27/08/2026) |
| `pitest.junit5.plugin.version` | `1.2.3` | passerelle JUnit Platform pour PIT |

Pourquoi ces choix :

- **SpotBugs 4.10.4.1** est la dernière version stable ; elle embarque déjà le moteur 4.10.4, qui est épinglé quand même
  pour qu'une montée du plugin ne change pas les détecteurs sans décision. Le plugin exige Maven 3.8.9 ou plus (le
  wrapper est en 3.10.0) et s'exécute sur le JDK 21 imposé par Enforcer. La version 4.10.4.1 a des ruptures de
  configuration documentées (`outputEncoding` et `outputDirectory` ne se configurent plus directement) : aucune des
  deux n'est utilisée ici.
- **PIT 1.30.0**. Les notes de version officielles précisent que le saut depuis la série 1.25.x vient d'une release
  mal étiquetée `1.29.10` ; `1.29.10` est donc à éviter.
- **`pitest-junit5-plugin` 1.2.3** est la dernière version publiée, **datée de mai 2025**. Elle est compilée contre
  JUnit Platform 1.9.2, et le ticket
  [`pitest/pitest-junit5-plugin#113`](https://github.com/pitest/pitest-junit5-plugin/issues/113) (ouvert, non
  confirmé par le mainteneur au 07/10/2026) rapporte « 0 test exécuté » avec JUnit Platform 6 sous Gradle.

**Compatibilité JUnit 6.1.3 : vérifiée par exécution, pas par supposition.** Avec PIT 1.30.0 et le plugin 1.2.3, le
plugin Maven ajoute lui-même au classpath des tests `junit-platform-launcher`, `junit-platform-engine` et
`junit-jupiter-engine` en **6.1.3**, alignés sur le projet (visible dans le journal : `Auto adding
org.junit.platform:junit-platform-launcher:jar:6.1.3`). Les tests sont découverts et exécutés, et des mutations sont
tuées (§ 10). Le défaut du ticket ne se reproduit donc pas dans cette configuration. Cette compatibilité repose sur
un plugin qui n'a pas été mis à jour depuis 2025 : si une montée de JUnit la casse, le symptôme est un
`Ran 0 tests`, et le garde-fou du § 8 fait alors échouer le run au lieu de le laisser passer.

Aucune version de JUnit ou de Java n'a été modifiée.

## 4. Les profils Maven et leur effet exact

| Profil | Déclare | Lié à une phase ? | Effet |
|---|---|---|---|
| *(aucun)* | rien de SpotBugs ni de PIT | — | build habituel inchangé ; les deux plugins n'apparaissent pas dans le POM effectif d'un module |
| `audit-spotbugs` | `spotbugs-maven-plugin` dans `<build><plugins>`, hérité par tous les modules ; propriétés `spotbugs.audit.includeTests` (`false`) et `spotbugs.audit.xmlOutputFilename` (`spotbugsXml.xml`) | **oui**, `check` sur `verify` | analyse chaque module qui a des classes de production, écrit les rapports, puis échoue s'il reste des alertes |
| `audit-mutation` | `pitest-maven` + `pitest-junit5-plugin`, des propriétés `pit.*` (dont `pit.crossModule`, `false`), et `-Dmorpheus.project.version` transmis au minion | **non** | ne fait rien tant que l'objectif `pitest:mutationCoverage` n'est pas appelé explicitement |

Le contrôle du POM effectif (`./mvnw help:effective-pom -Paudit-spotbugs -pl morpheus-domain`) a été fait : le plugin
SpotBugs n'apparaît dans `morpheus-domain` qu'avec `audit-spotbugs`, PIT qu'avec `audit-mutation`.

`audit-spotbugs` est liée à `verify` pour que `-Paudit-spotbugs verify` soit la commande unique, mais **`verify`
exécute aussi les tests** : on ajoute `-DskipTests` pour n'analyser que le bytecode (§ 6).

## 5. Périmètre : modules inclus et exclusions

**SpotBugs** analyse les 16 modules qui portent des classes sous `src/main/java`. `morpheus-architecture-tests` et
`morpheus-coverage-report` n'en portent aucune : le plugin n'a rien à analyser et ne produit pas de rapport. Ils ne
sont pas exclus par leur nom, c'est le plugin qui constate l'absence de classes. Les classes de test ne sont pas
analysées (`includeTests=false`).

**Aucune exclusion n'est en place.** [`config/spotbugs-exclude.xml`](../../config/spotbugs-exclude.xml) est un filtre
vide, volontairement. Aucun module de production n'est désactivé.

**PIT** ne s'exécute **jamais** sur tout le dépôt en une seule commande : un audit complet enchaîne un lot par module
(§ 14). Le périmètre par défaut est petit et déterministe, dans `morpheus-domain` :

```text
classes   com.morpheus.domain.constraint.*   com.morpheus.domain.change.lifecycle.*
tests     les mêmes paquetages
```

Ce sont des règles de décision pures (politique de blocage, évaluation de contrainte, états et révisions du cycle de
vie), couvertes par 15 méthodes `@Test`. Une recherche dans ces deux paquetages ne trouve ni horloge, ni aléa, ni
entrée/sortie : le résultat ne dépend pas de la machine. Ces valeurs sont des propriétés `pit.*` du profil, à changer
depuis la ligne de commande (§ 8).

## 6. Prérequis et commandes

Prérequis :

- **JDK 21.** Enforcer impose `[21,22)` : un JDK plus récent premier dans le `PATH` fait échouer le build. Pointer
  `JAVA_HOME` vers un JDK 21 avant tout appel.
- Le wrapper du dépôt : `.\mvnw.cmd` sous Windows, `./mvnw` ailleurs. Jamais `mvn`.
- Réseau pour la première exécution (téléchargement des plugins).
- PIT dans un module autre que `morpheus-domain` : `-pl <module>` lit les modules amont depuis `~/.m2`, qui peut être
  périmé. Installer d'abord l'amont : `./mvnw install -DskipTests -pl morpheus-domain`. Sans cela, on obtient une
  erreur de compilation des tests sans rapport avec PIT.

> **PowerShell : mettre les `-D…` entre guillemets.** Mesuré sous Windows PowerShell 5.1 :
> `-Dpit.targetClasses=com.morpheus…` non quoté est coupé au premier point et Maven répond
> `Unknown lifecycle phase ".targetClasses=…"`. Écrire `"-Dpit.targetClasses=…"`. Bash n'a pas ce défaut.

Chaque commande porte une étiquette qui dit ce qui a réellement tourné, sur cette machine (Windows 10, JDK 21.0.12.1,
Maven 3.9.16, le 07/10/2026 ; les deux profils ont ensuite été rejoués sous Maven 3.10.0 avec les mêmes résultats sur `morpheus-domain`) :

- **[exécutée]** : lancée telle quelle ;
- **[exécutée avec `-pl morpheus-domain`]** : la variante limitée à un module a tourné, pas la version sur tout le
  réacteur ;
- **[exécutée sous Git Bash]** : lancée avec la syntaxe Bash, sous Windows ;
- **[adaptée]** : mêmes arguments, syntaxe Linux/macOS, jamais lancée sur un vrai Linux ou macOS.

## 7. SpotBugs

Référence complète, relevé du 09/10/2026 (192 alertes, 1 046 classes) et dépannage : [`SPOTBUGS.md`](SPOTBUGS.md).

### Générer les rapports, sans échec

Windows PowerShell **[exécutée avec `-pl morpheus-domain`]** (sans `-pl`, c'est le réacteur entier, lancé sous Git Bash) :

```powershell
.\mvnw.cmd -Paudit-spotbugs "-Dspotbugs.failOnError=false" "-DskipTests" verify
```

Linux / macOS **[adaptée]** (**exécutée sous Git Bash** sur le réacteur entier) :

```bash
./mvnw -Paudit-spotbugs -Dspotbugs.failOnError=false -DskipTests verify
```

Pour un seul module, ajouter `-pl morpheus-domain`.

Le build réussit même s'il y a des alertes, et les rapports sont écrits.

### Lancer le contrôle bloquant

C'est la même commande **sans** `-Dspotbugs.failOnError=false` (**exécutée avec `-pl morpheus-domain`**, sous
PowerShell et sous Git Bash : code de sortie 1) :

```powershell
.\mvnw.cmd -Paudit-spotbugs "-DskipTests" verify
```

```bash
./mvnw -Paudit-spotbugs -DskipTests verify
```

Elle échoue (code de sortie 1) avec `failed with N bugs and 0 errors` dès qu'une alerte subsiste. Paramètres explicités
dans le profil :

| Paramètre | Valeur | Sens |
|---|---|---|
| `effort` | `Max` | analyse la plus approfondie, la plus lente |
| `threshold` | `Medium` | confiance : alertes de confiance moyenne et haute (exclut `Low`) |
| `maxRank` | `20` | gravité : tous les rangs, 1 (le plus inquiétant) à 20 (le moins) |
| `maxAllowedViolations` | `0` | une seule alerte suffit à échouer |
| `includeTests` | `false` | seul le code de production |

### Consulter les rapports

| Fichier | Contenu |
|---|---|
| `<module>/target/reports/spotbugs.html` | rapport lisible, à ouvrir dans un navigateur |
| `<module>/target/spotbugsXml.xml` | rapport machine, une `BugInstance` par alerte (type, priorité, rang, classe, ligne) |

Compter les alertes par module après un run :

```powershell
Get-ChildItem morpheus-*\target\spotbugsXml.xml | ForEach-Object {
    [xml]$x = Get-Content -LiteralPath $_.FullName -Encoding UTF8
    '{0,-30} {1}' -f $_.Directory.Parent.Name, $x.SelectNodes('//BugInstance').Count
}
```

```bash
for f in morpheus-*/target/spotbugsXml.xml; do
  printf '%s %s\n' "$(basename "$(dirname "$(dirname "$f")")")" "$(grep -o '<BugInstance ' "$f" | wc -l)"
done
```

Les deux ont été exécutées et donnent les mêmes comptes. Deux pièges évités : `grep -c` compte les lignes (le XML
tient sur une ligne) et `@($null).Count` vaut 1 en PowerShell.

Le texte des alertes suit la langue de la machine (français ici). Cela ne change ni les types ni les comptes.

## 8. PIT

### Lancer le périmètre par défaut

Windows PowerShell **[adaptée]** (le périmètre par défaut a été **exécuté sous Git Bash** ; sous PowerShell, ce sont
des variantes à périmètre réduit qui ont tourné, voir plus bas) :

```powershell
.\mvnw.cmd -Paudit-mutation -pl morpheus-domain test-compile org.pitest:pitest-maven:mutationCoverage
```

Linux / macOS **[adaptée]** (**exécutée sous Git Bash**) :

```bash
./mvnw -Paudit-mutation -pl morpheus-domain test-compile org.pitest:pitest-maven:mutationCoverage
```

`test-compile` est nécessaire : PIT lit les classes et les tests compilés, mais n'exécute pas le cycle de vie. Il
reprend aussi l'`argLine` posé par l'agent JaCoCo et embarque un filtre « Disable JaCoCo » : `target/jacoco.exec` n'a
pas été modifié par un run PIT (date et taille identiques avant et après, mesuré une fois).

### Changer le périmètre

Cinq propriétés, toutes `-D` :

| Propriété | Défaut | Rôle |
|---|---|---|
| `pit.targetClasses` | paquetages du § 5 | classes à muter, séparées par des virgules, jokers `*` |
| `pit.targetTests` | idem | tests autorisés à les tuer |
| `pit.threads` | `2` | parallélisme de PIT |
| `pit.timeoutConstant` / `pit.timeoutFactor` | `4000` / `1.25` | délai d'une mutation : constante en ms + facteur sur le temps normal |
| `pit.coverageThreshold` | `1` | garde-fou, voir ci-dessous |
| `pit.crossModule` | `false` | muter aussi les classes de modules amont avec les tests du module courant (§ 9) |

Le profil transmet aussi `-Dmorpheus.project.version=${project.version}` au minion. PIT ne lit pas les
`systemPropertyVariables` de Surefire : sans cette ligne, `ProductMetadata.version()` vaut `development` dans le minion,
deux tests de `ProductIntegrityTest` échouent avant toute mutation, et PIT refuse le module entier
(« Mutation testing requires a green suite »). Mesuré le 08/10/2026.

Exemple, **une seule classe** et **un seul test**. Sous PowerShell, une variante à deux classes avec guillemets a été
**exécutée** (14 mutations) ; la forme ci-dessous a tourné **sous Git Bash** (10 mutations) :

```powershell
.\mvnw.cmd -Paudit-mutation -pl morpheus-domain `
  "-Dpit.targetClasses=com.morpheus.domain.constraint.ConstraintBlockingPolicy" `
  "-Dpit.targetTests=com.morpheus.domain.constraint.ConstraintSemanticsTest" `
  test-compile org.pitest:pitest-maven:mutationCoverage
```

Un autre module (installer l'amont d'abord, § 6), **[exécutée sous Git Bash]** :

```bash
./mvnw -Paudit-mutation -pl morpheus-application \
  -Dpit.targetClasses=com.morpheus.application.files.WorkspaceRelativePathText \
  -Dpit.targetTests=com.morpheus.application.files.WorkspaceRelativePathTextTest \
  test-compile org.pitest:pitest-maven:mutationCoverage
```

Sans surcharge, lancer PIT sur un autre module échoue avec `No mutations found` : les valeurs par défaut visent le
domaine. C'est voulu.

### Le garde-fou `pit.coverageThreshold`

PIT termine en `BUILD SUCCESS` quand le motif de tests ne retient **aucun test** : toutes les mutations passent en
« non couvertes » et rien n'échoue. Mesuré avec un motif de tests sans correspondance, avant le garde-fou : code de
sortie 0. Le profil exige donc au moins 1 % de lignes couvertes dans les classes mutées. **Ce n'est pas un objectif
de qualité**, seulement la détection d'un périmètre de tests faux : avec lui, le même run échoue avec
`Line coverage of 0(0/131) is below threshold of 1`. Aucun seuil de score de mutation n'est posé avant qu'une
première mesure d'ensemble ait été discutée.

### Chemins des rapports

| Fichier | Contenu |
|---|---|
| `<module>/target/pit-reports/index.html` | rapport HTML, avec le code annoté par mutation |
| `<module>/target/pit-reports/mutations.xml` | rapport XML : une `<mutation>` par ligne, avec statut, mutateur, méthode, ligne |

Le nom du dossier est stable (`timestampedReports=false`) : un run écrase le précédent.

## 9. Interpréter un résultat PIT

Le résumé de fin de run donne `Generated N mutations Killed K (x %)`, `Mutations with no coverage C` et
`Test strength s %`. Les statuts de `mutations.xml` :

| Statut | Sens | Réflexe |
|---|---|---|
| `KILLED` | un test a échoué : l'altération est détectée | rien à faire |
| `TIMED_OUT` | la mutation a fait boucler le code ; PIT la compte comme détectée | rien, sauf si leur nombre est élevé (alors régler les délais) |
| `SURVIVED` | tous les tests passent avec le code altéré | **à qualifier** : test manquant, assertion trop faible, ou mutation équivalente |
| `NO_COVERAGE` | aucun test **de ce module** n'exécute la ligne | à qualifier : vraiment non testé, ou testé ailleurs (voir ci-dessous) |
| `NON_VIABLE` | la mutation ne produit pas un bytecode valide | ignorer |
| `EQUIVALENT` | marquée équivalente par un filtre | ignorer |

`Test strength` vaut tuées ÷ (générées − non couvertes). **Piège mesuré** : sur une classe dont aucune mutation n'est
couverte, PIT affiche tout de même `Test strength 100%`. Lire toujours `Line Coverage (for mutated classes only)` et
`Mutations with no coverage` avant le score.

**Une mutation survivante n'est pas un bug.** C'est une question posée aux tests. Trois réponses possibles : un test
manque (ajouter l'assertion), le comportement n'a pas d'importance observable (mutation équivalente, à écarter), ou le
code est inutilement compliqué (le simplifier).

**Les tests d'un autre module ne comptent pas.** PIT n'exécute que les tests présents dans le classpath de test du
module où il tourne. Mesuré : la classe `PortfolioMembership` (domaine), citée dans 4 tests de `morpheus-application` mais dans aucun test
du domaine, donne 11 mutations, 0 ligne couverte, `Ran 0 tests` — et le build réussit. Il s'ensuit que :

- un `NO_COVERAGE` dans un module ne prouve pas que le comportement est non testé dans le dépôt ;
- les tests d'architecture (ArchUnit) vérifient la structure, pas le comportement, et ne tuent aucune mutation d'un
  autre module ; ils ne remplacent pas des tests comportementaux placés **dans** le module de la classe ;
- pour mesurer une classe, il faut des tests dans son propre module, **ou** la passe transverse ci-dessous.

**Passe transverse (`-Dpit.crossModule=true`).** Lancée dans un module aval, elle mute les classes amont nommées par
`pit.targetClasses` et les fait tuer par les tests du module aval. Condition mesurée : le module amont doit être
**dans le réacteur**. `-pl morpheus-application -Dpit.crossModule=true -Dpit.targetClasses=com.morpheus.domain.*`
répond `No mutations found` (le domaine est alors un JAR de `~/.m2`) ; il faut
`-pl morpheus-domain,morpheus-application`, et PIT tourne alors aussi dans le module amont. Mesuré : les tests
d'application tuent 168 des 315 mutations du domaine, contre 100 pour les tests du domaine seuls.

Deux limites de la passe transverse. Un test qui lance un **sous-processus** (`java … MorpheusMain`) exécute le
bytecode non muté de `target/classes` et ne peut tuer aucune mutation. Un mutant qui casse le nettoyage d'un arbre de
processus (`provider-sdk`, `mcp-transport`) peut laisser des **JVM orphelines** après la mort du minion : les compter
et les arrêter après chaque lot (le relevé du 08/10/2026 en a trouvé 5 après le lot `provider-sdk`).

## 10. Mesures

Mesurées le 07/10/2026 sur `develop` à `3c87bcc5` plus les changements de cette intégration (non commités). Ce sont des
constats datés, pas des ratchets : ils ne se recopient pas ailleurs sans les relire.

### Durées

| Exécution | Durée |
|---|---|
| `./mvnw -DskipTests verify`, sans profil (référence) | 31 s |
| `./mvnw -Paudit-spotbugs -Dspotbugs.failOnError=false -DskipTests verify`, 16 modules | 231 s |
| idem, `morpheus-domain` seul | environ 22 s (hors téléchargement) |
| PIT, périmètre par défaut (`morpheus-domain`) | 13 s (24 s la première fois, téléchargements compris) |
| PIT, une classe de `morpheus-application` | 15 s |

SpotBugs coûte donc environ 200 s sur le dépôt entier à `effort=Max`. Une seule mesure par cas : ce sont des ordres
de grandeur.

### Volumes

| | SpotBugs | PIT (périmètre par défaut) |
|---|---|---|
| Analysé | 1 041 fichiers `.class` dans 16 modules | 8 classes mutées, 18 classes de test examinées |
| Exécuté | — | 55 tests (1,04 par mutation) |
| Produit | 195 alertes | 53 mutations |

## 11. Faux positifs, exclusions et limites

**Traitement d'un faux positif SpotBugs.** Ne pas l'exclure par réflexe. Qualifier d'abord : lire le code, écrire en une
phrase pourquoi l'alerte est fausse *pour ce code*. Si c'est établi, ajouter dans
[`config/spotbugs-exclude.xml`](../../config/spotbugs-exclude.xml) une entrée qui nomme **un motif sur une classe (ou
une méthode)**, avec un commentaire qui donne la raison. Jamais un paquetage entier, jamais une famille de motifs, jamais
un module. Une exclusion qui masque aussi des cas réels est pire que l'alerte.

```xml
<Match>
    <!-- La liste est une copie immuable (List.copyOf) construite par le constructeur compact. -->
    <Class name="com.morpheus.domain.acceptance.AcceptanceCriterion"/>
    <Bug pattern="EI_EXPOSE_REP"/>
    <Method name="verificationEvidenceIds"/>
</Match>
```

(Exemple de forme, **non appliqué** : voir § 12.)

**Limites de SpotBugs.** Il raisonne sur le bytecode, sans connaître les invariants du projet. Il ignore qu'une méthode
privée renvoie une liste immuable, ou qu'un `getFileName() == null` vient d'être testé. Il produit des faux positifs et
des faux négatifs ; une analyse propre ne prouve rien.

**Limites de PIT.**

- Une mutation équivalente (qui ne change pas le comportement observable) survit toujours : un score de 100 % n'est
  pas un objectif.
- PIT mesure les tests du module courant (§ 9).
- Le coût croît avec le nombre de mutations et de tests : d'où le périmètre restreint par défaut.
- Un test qui dépend de l'horloge, de l'aléa ou du réseau donne des résultats instables ; les classes retenues par
  défaut n'en ont pas.

## 12. Résultats initiaux (constats, non corrigés)

L'intégration n'a corrigé aucun défaut. Ces constats sont consignés pour décision.

### SpotBugs : 195 alertes, contrôle bloquant rouge

Par module : `morpheus-application` 135, `morpheus-store-sqlite` 28, `morpheus-provider-openspec` 12,
`morpheus-api` 9, `morpheus-domain` 6, `morpheus-provider-sdk` 3, `morpheus-provider-markdown` 1,
`morpheus-provider-synthetic` 1 ; **huit modules ont 0 alerte** (`cli`, `integration-minos`, `integration-nexus`, `mcp`,
`mcp-transport`, `provider-reference`, `provider-testkit`, `store-memory`).

Par motif : `EI_EXPOSE_REP` 137, `EI_EXPOSE_REP2` 35, `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` 15, `URF_UNREAD_FIELD` 3,
puis 1 chacun : `DMI_RANDOM_USED_ONLY_ONCE`, `DCN_NULLPOINTER_EXCEPTION`, `SBSC_USE_STRINGBUFFER_CONCATENATION`,
`CT_CONSTRUCTOR_THROW`, `MS_EXPOSE_REP`. Confiance : 194 moyennes, 1 haute. Rang : 177 au rang 18 (le moins inquiétant),
15 au rang 13, 3 aux rangs 14, 16, 17.

Qualification d'un échantillon :

| Alerte | Constat |
|---|---|
| `EI_EXPOSE_REP` sur `AcceptanceCriterion.verificationEvidenceIds()` | **faux positif de mutabilité probable** : le constructeur compact stocke `List.copyOf(...)`, donc la liste renvoyée est immuable ; SpotBugs ne le voit pas à travers la méthode privée `canonicalEvidenceIds` |
| `DMI_RANDOM_USED_ONLY_ONCE` (confiance haute) sur `MorpheusInternalCapability.generate()` | **bénin** : un `SecureRandom` est instancié une fois par jeton, et le jeton est généré au démarrage du serveur ; style, pas défaut de sécurité |
| `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` sur `StructuredMarkdownSpecificationContentReader.normalize:141` | **faux positif probable** : `getFileName() == null` est testé juste avant l'appel ; SpotBugs ne sait pas que le second appel renvoie la même valeur |

Les 172 alertes `EI_EXPOSE_REP*` ne sont **pas** qualifiées une à une : l'échantillon ci-dessus ne démontre rien pour
les autres, et chaque record doit être lu. Les 14 autres `NP_NULL…` sont à lire, ce sont les plus utiles.

Décision à prendre (non prise ici) : qualifier d'abord, puis soit corriger, soit exclure précisément, soit relever
`maxRank` / `threshold` pour un contrôle bloquant plus étroit. Tant que rien n'est décidé, `audit-spotbugs` sans
`failOnError=false` échoue sur le dépôt actuel.

### PIT : périmètre par défaut

53 mutations : 39 tuées, 3 survivantes, 11 non couvertes (couverture des lignes mutées : 115/131, 88 %). Les 3
survivantes :

| Mutation | Lecture |
|---|---|
| `ChangeLifecycleIdempotencyKey` ligne 13, frontière de condition changée | **manque de test probable** : la ligne est `value.length() > 200`, et la valeur limite 200 n'est pas testée |
| `Constraint.requireNonBlank`, retour remplacé par `""` | à qualifier : aucun test ne vérifie que la valeur renvoyée est bien la chaîne rognée |
| `ConstraintEvaluation` ligne 35, condition niée | **surprenant, à lire avant de conclure** : la ligne est `if (reason.isEmpty())` ; nier cette condition devrait faire échouer toute construction valide, donc sa survie suggère un rattachement de ligne approximatif dans le bytecode. Ouvrir `index.html` pour voir la mutation exacte |

Parmi les 11 non couvertes, `ConstraintBlockingPolicy.targets` (le prédicat qui dit si une politique vise un état) n'est
exécuté par aucun test du **module domaine** ; il l'est peut-être par des tests de `morpheus-application`, ce qu'il
faut vérifier avant d'en faire une tâche (§ 9).

## 13. Du constat à la tâche de correction

Un constat n'est pas une tâche. Pour en faire une :

1. **Reproduire.** SpotBugs : relire l'alerte dans `spotbugs.html`, ouvrir la ligne. PIT : ouvrir `index.html`, lire la
   mutation et le code annoté.
2. **Qualifier** en une phrase : défaut réel, test manquant, faux positif, mutation équivalente, ou code mort.
3. **Décider**, et le consigner :
   - défaut réel : un test qui échoue **avant** la correction (règle du dépôt), puis la correction ;
   - test manquant : écrire le test qui tue la mutation, **dans le module de la classe** ;
   - faux positif SpotBugs : exclusion précise avec raison (§ 11) ;
   - mutation équivalente : ne rien faire, ou simplifier le code si elle révèle du superflu.
4. **Ouvrir une issue** avec : l'outil et sa version, la commande exacte, la classe et la ligne, le statut ou le type,
   la qualification, et la décision. Une alerte non qualifiée ne devient pas une issue.
5. **Vérifier** : relancer le même périmètre et constater la disparition du constat.

Ne pas corriger en masse : un changement qui ne vise que « faire taire » l'outil (un `@SuppressFBWarnings` posé sans
analyse, un test sans assertion) dégrade ce que l'outil est censé protéger.

## 14. Audit complet du réacteur

Les profils servent aussi à un audit couvrant tous les modules, découpé en lots. Les chiffres d'un tel audit sont
des relevés datés : celui du 08/10/2026 est [`../audits/AUDIT_OUTILLE_2026-10-08.md`](../audits/AUDIT_OUTILLE_2026-10-08.md).

**SpotBugs, deux passes.** La passe production est celle du § 7. La passe qui ajoute le bytecode de test écrit un
autre fichier et n'écrase pas la première :

```bash
./mvnw -o -Paudit-spotbugs -Dspotbugs.failOnError=false -Dspotbugs.audit.includeTests=true \
  -Dspotbugs.audit.xmlOutputFilename=spotbugsXml-with-tests.xml -DskipTests verify
```

Le XML du plugin publie `total_classes='0'` : pour prouver ce qui a été analysé, lire l'élément `<Jar>` du rapport (le
répertoire analysé), compter ses `.class`, et vérifier `missingClasses='0'` et un `cpu_seconds` non nul. Une alerte se
classe « test » quand sa classe n'existe que sous `target/test-classes`.

**PIT, un lot par module**, chacun sur tous les paquetages de production du module, avec tous ses tests :

```bash
./mvnw -o -Paudit-mutation -pl <module> "-Dpit.targetClasses=<paquetage racine du module>.*" \
  "-Dpit.targetTests=com.morpheus.*" -Dpit.threads=12 -Dpit.timeoutConstant=8000 \
  test-compile org.pitest:pitest-maven:mutationCoverage
```

puis les passes transverses du § 9 pour les classes que leur module teste peu. Règles tirées de l'audit du 08/10 :

- **un seul lot à la fois** — deux lots simultanés se disputent les cœurs et transforment des mutations en
  `TIMED_OUT`, comptées comme tuées ;
- après un lot de `provider-sdk` ou `mcp-transport`, compter et arrêter les JVM orphelines (§ 9) ;
- arrêter un lot de force laisse son Maven et ses minions vivants : retrouver les JVM dont la ligne de commande porte
  le dépôt, et ne terminer que celles-là ;
- archiver `target/pit-reports` après chaque lot : le lot suivant du même module l'écrase.

**Gitleaks et Dependency-Check** ne sont pas des profils de ce dépôt. L'audit du 08/10 donne les commandes exactes,
la vérification de l'archive Gitleaks, et le repli sur le rapport CI quand la clé NVD locale est refusée.
