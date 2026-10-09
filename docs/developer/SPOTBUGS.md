# SpotBugs : analyse statique du bytecode

SpotBugs est l'analyse statique de MORPHEUS. Elle est portée par le profil Maven **opt-in** `audit-spotbugs` du POM
racine : elle ne tourne ni dans `./mvnw clean verify`, ni dans la CI. Ce document est la référence de ce profil ;
[`CODE_AUDIT.md`](CODE_AUDIT.md) le replace à côté de PIT (tests de mutation) et des autres contrôles.

Le contrôle bloquant (`spotbugs:check`) est **rouge sur le code actuel** : il n'a pas été masqué pour obtenir un
vert, et aucun défaut de production n'a été corrigé par cette intégration (§ 9).

## 1. Ce qui est configuré

| Élément | Où | Rôle |
|---|---|---|
| `spotbugs.maven.plugin.version` = `4.10.4.1` | [`pom.xml`](../../pom.xml), `<properties>` | version du plugin Maven |
| `spotbugs.version` = `4.10.4` | idem | moteur d'analyse, épinglé comme dépendance du plugin |
| profil `audit-spotbugs` | `pom.xml`, `<profiles>` | déclare le plugin dans `<build><plugins>` (donc hérité par chaque module) et lie `check` à `verify` |
| [`config/spotbugs-exclude.xml`](../../config/spotbugs-exclude.xml) | dépôt | filtre d'exclusion, **vide** volontairement (§ 7) |

Le plugin n'est **pas** dans `<pluginManagement>` du build par défaut : sans le profil, le POM effectif d'un module ne
contient que les deux propriétés ci-dessus et aucun plugin SpotBugs (vérifié avec
`./mvnw help:effective-pom -pl morpheus-domain`, comparé avec `-Paudit-spotbugs`). Le build habituel est donc
inchangé, ainsi que JaCoCo, Enforcer, `dependency:analyze`, CycloneDX et Dependency-Check, qui ne sont pas touchés.

Paramètres du profil :

| Paramètre | Valeur | Sens |
|---|---|---|
| `effort` | `Max` | analyse la plus approfondie, la plus lente |
| `threshold` | `Medium` | confiance : alertes de confiance moyenne et haute |
| `maxRank` | `20` | gravité : tous les rangs (1 = le plus inquiétant, 20 = le moins) |
| `maxAllowedViolations` | `0` | une seule alerte fait échouer `check` |
| `xmlOutput` / `htmlOutput` | `true` / `true` | rapports XML et HTML |
| `includeTests` | `${spotbugs.audit.includeTests}`, `false` | seul le code de production par défaut |
| `spotbugsXmlOutputFilename` | `${spotbugs.audit.xmlOutputFilename}`, `spotbugsXml.xml` | nom du XML, surchargeable |
| `excludeFilterFile` | `config/spotbugs-exclude.xml` | exclusions qualifiées |
| exécution `audit-spotbugs-check` | phase `verify`, but `check` | analyse puis échec si des alertes subsistent |

`failOnError` (défaut du plugin : `true`) n'est pas posé dans le POM : il se surcharge en ligne de commande
(`-Dspotbugs.failOnError=false`) pour produire les rapports sans faire échouer le build.

## 2. Version retenue et vérification

Vérifié le 09/10/2026 :

| Constat | Source |
|---|---|
| `spotbugs-maven-plugin` : `latest` = `release` = **4.10.4.1** (publiée le 05/09/2026) | `maven-metadata.xml` sur Maven Central |
| `spotbugs` (moteur) : `release` = **4.10.4** (20/08/2026), celui que le POM du plugin 4.10.4.1 déclare | `maven-metadata.xml` et POM du plugin |
| exigences du plugin : Maven ≥ 3.8.9, compilation Java 11 | POM du plugin 4.10.4.1 ; notes de version 4.10.4.0 |
| ruptures de configuration en 4.10.4.1 : `outputEncoding` et `outputDirectory` ne se configurent plus dans le plugin (propriétés `project.reporting.*`), `fork` supprimé | notes de version GitHub |

Compatibilité avec le dépôt : le wrapper est en Maven 3.10.0 (≥ 3.8.9) ; le code cible Java 21
(`maven.compiler.release`) et Enforcer impose le JDK `[21,22)`. Le profil n'utilise **aucun** des trois paramètres
supprimés. Le moteur est épinglé en dépendance du plugin pour qu'une montée du plugin ne change pas les détecteurs sans
décision explicite.

Mettre à jour : relire `maven-metadata.xml` des deux artefacts, lire les notes de version GitHub du plugin, changer les
deux propriétés **ensemble**, puis rejouer l'analyse complète (§ 4) et comparer le total d'alertes avant et après.

## 3. Périmètre : modules analysés

Le réacteur compte **18 modules** (19 projets avec le POM parent). **16** portent des classes sous `src/main/java` et
sont tous analysés ; les deux autres n'ont aucune classe de production :

| Module | Traitement | Raison |
|---|---|---|
| `morpheus-domain`, `-application`, `-api`, `-mcp`, `-cli`, `-store-sqlite`, `-mcp-transport`, `-integration-minos`, `-integration-nexus`, `-provider-sdk`, `-provider-openspec`, `-provider-markdown`, `-provider-synthetic` | analysés | code livré ou chargé en production |
| `morpheus-store-memory`, `-provider-testkit`, `-provider-reference` | **analysés** | outillage de vérification, mais leurs classes sont sous `src/main/java` ; les exclure par nom serait une exclusion silencieuse. Le plugin de référence est de plus du code que l'on charge réellement (M22) |
| `morpheus-architecture-tests`, `morpheus-coverage-report` | **aucune classe** à analyser | modules de tests et d'agrégation : le plugin constate l'absence de `target/classes` et ne produit pas de rapport. Ils ne sont **pas** exclus par leur nom |
| POM parent `morpheus-engine` | ignoré par le plugin (`Skipping pom project`) | packaging `pom` |

**Classes de test** : non analysées par défaut. Une passe séparée les ajoute sans écraser le rapport de production
(§ 4). Le fichier d'exclusion n'écarte aucun module, aucun paquetage, aucun motif.

## 4. Commandes

Prérequis : **JDK 21** (un JDK plus récent premier dans le `PATH` ou dans `JAVA_HOME` fait échouer Enforcer), le
wrapper du dépôt (`mvnw.cmd` / `./mvnw`, jamais `mvn`), et le réseau à la première exécution. `verify` exécute aussi les
tests : `-DskipTests` limite le travail au bytecode.

Windows PowerShell (mettre les `-D…` entre guillemets : non quoté, PowerShell 5.1 coupe l'argument au premier point) :

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12.1"   # adapter au JDK 21 de la machine

# Rapports, sans faire échouer le build (réacteur entier)
.\mvnw.cmd -Paudit-spotbugs "-Dspotbugs.failOnError=false" "-DskipTests" verify

# Contrôle bloquant, tous les modules (continue après le premier échec)
.\mvnw.cmd -fae -Paudit-spotbugs "-DskipTests" verify

# Un seul module
.\mvnw.cmd -Paudit-spotbugs "-Dspotbugs.failOnError=false" "-DskipTests" -pl morpheus-domain verify

# Passe qui ajoute le bytecode de test, dans un autre fichier
.\mvnw.cmd -Paudit-spotbugs "-Dspotbugs.failOnError=false" "-Dspotbugs.audit.includeTests=true" `
  "-Dspotbugs.audit.xmlOutputFilename=spotbugsXml-with-tests.xml" "-DskipTests" verify
```

Linux / macOS :

```bash
export JAVA_HOME=/chemin/vers/jdk-21

./mvnw -Paudit-spotbugs -Dspotbugs.failOnError=false -DskipTests verify
./mvnw -fae -Paudit-spotbugs -DskipTests verify
./mvnw -Paudit-spotbugs -Dspotbugs.failOnError=false -DskipTests -pl morpheus-domain verify
./mvnw -Paudit-spotbugs -Dspotbugs.failOnError=false -Dspotbugs.audit.includeTests=true \
  -Dspotbugs.audit.xmlOutputFilename=spotbugsXml-with-tests.xml -DskipTests verify
```

Statut de ces commandes, mesuré le 09/10/2026 sous Git Bash, Windows 10, JDK 21.0.12.1, Maven 3.10.0 : les deux
premières ont été **exécutées sur le réacteur entier** (sections 9.1 et 9.2). Les formes PowerShell et Linux/macOS ne
sont que la traduction de syntaxe de ces exécutions : elles n'ont pas été relancées sous PowerShell ni sur Linux/macOS
pour ce relevé. La variante `-pl` et la passe avec bytecode de test ont été exécutées lors de l'intégration initiale
(voir `CODE_AUDIT.md`), pas rejouées ce jour-là.

Sans `-fae`, le contrôle bloquant s'arrête au premier module en échec. Les modules qui en dépendent sont alors
`SKIPPED`, et non « sans alerte » : pour compter les alertes de tous les modules, utiliser les rapports (commande
sans échec) puis le relevé ci-dessous.

## 5. Rapports

| Fichier | Contenu |
|---|---|
| `<module>/target/reports/spotbugs.html` | rapport lisible, à ouvrir dans un navigateur |
| `<module>/target/spotbugsXml.xml` | rapport machine, une `BugInstance` par alerte (type, priorité, rang, classe, ligne) |
| `<module>/target/spotbugsXml-with-tests.xml` | idem, passe avec bytecode de test |

`target/` est ignoré par Git : les rapports ne sont jamais versionnés.

Compter les alertes par module :

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

**Prouver ce qui a été analysé.** Le champ `total_classes` du résumé XML vaut `0` avec ce plugin : il ne prouve rien.
À la place, dans le XML : l'élément `Project/Jar` donne le répertoire analysé (`target/classes`), l'élément `Errors`
doit porter `errors='0'` et `missingClasses='0'`, et `FindBugsSummary` un `cpu_seconds` non nul. Compter ensuite les
`.class` du répertoire : `find <module>/target/classes -name '*.class' | wc -l`.

Le texte des alertes suit la langue de la machine (français ici). Les types (`EI_EXPOSE_REP`…), les comptes et les
rangs n'en dépendent pas.

## 6. Distinguer une alerte d'une erreur de configuration

| Ce que dit Maven | Nature | Quoi faire |
|---|---|---|
| `failed with N bugs and 0 errors` | **alertes sur le code** : la configuration est bonne | qualifier (§ 8) ; `-Dspotbugs.failOnError=false` pour continuer |
| `failed with N bugs and M errors`, `M > 0` | des classes n'ont pas pu être analysées | lire l'élément `Errors` du XML (`missingClasses`, `MissingClass`) |
| `BugInstance size is N` suivi d'un rapport | analyse réussie | rien |
| `Skipping … spotbugs report goal` | module sans classe (ou paquetage `pom`) | normal pour les deux modules sans production |
| échec avant toute analyse (Enforcer, plugin introuvable, paramètre inconnu) | **configuration** | § 10 |

Un `BUILD SUCCESS` avec `-Dspotbugs.failOnError=false` ne dit **pas** que le code est propre : lire le nombre d'alertes.

## 7. Exclusions

Le filtre est vide. Un faux positif ne s'exclut pas par réflexe : l'établir d'abord en une phrase écrite, puis ajouter
dans `config/spotbugs-exclude.xml` **un motif sur une classe (ou une méthode)**, avec un commentaire qui donne la
raison. Jamais un paquetage entier, jamais une famille de motifs, jamais un module. Une exclusion qui masque aussi
des cas réels est pire que l'alerte. `@SuppressFBWarnings` posé sans analyse est interdit pour la même raison.

```xml
<Match>
    <!-- La liste est une copie immuable (List.copyOf) construite par le constructeur compact. -->
    <Class name="com.morpheus.domain.acceptance.AcceptanceCriterion"/>
    <Bug pattern="EI_EXPOSE_REP"/>
    <Method name="verificationEvidenceIds"/>
</Match>
```

(Forme à suivre, **non appliquée**.) Le fichier est lu **sans** recompilation : le modifier suffit à rejouer l'analyse.

## 8. Lire un résultat

Une alerte est un **suspect**, pas un défaut. Pour en faire une tâche : relire l'alerte dans `spotbugs.html`, ouvrir la
ligne, la qualifier en une phrase (défaut réel, faux positif, bénin, code mort), décider (test qui échoue d'abord puis
correction ; exclusion précise ; rien), puis relancer le même périmètre. Une alerte non qualifiée ne devient pas une
issue. Détail et exemples : `CODE_AUDIT.md` § 12-13.

SpotBugs raisonne sur le bytecode, sans connaître les invariants du projet : il ignore qu'une méthode privée renvoie
une liste immuable. Une analyse propre ne prouve rien.

## 9. Relevé du 09/10/2026

Mesuré sur `develop` à `fe88c856` (arbre de travail : seuls des fichiers non suivis étrangers à cette intégration),
JDK 21.0.12.1, Maven 3.10.0, Windows 10. Constat daté, **pas un ratchet** : ne pas le recopier sans le relire.

### 9.1 Rapports sans échec

`./mvnw -Paudit-spotbugs -Dspotbugs.failOnError=false -DskipTests verify` : `BUILD SUCCESS`, code de sortie 0,
2 min 37 s, 19/19 projets en `SUCCESS`. Les 16 modules à code de production ont chacun un XML et un HTML ;
`errors='0'` et `missingClasses='0'` dans les 16.

| Module | `.class` analysées | Alertes |
|---|---:|---:|
| `morpheus-application` | 526 | 135 |
| `morpheus-store-sqlite` | 49 | 28 |
| `morpheus-api` | 156 | 9 |
| `morpheus-provider-openspec` | 20 | 9 |
| `morpheus-domain` | 84 | 6 |
| `morpheus-provider-sdk` | 37 | 3 |
| `morpheus-provider-markdown` | 7 | 1 |
| `morpheus-provider-synthetic` | 7 | 1 |
| `morpheus-cli` | 74 | 0 |
| `morpheus-mcp` | 22 | 0 |
| `morpheus-integration-minos` | 17 | 0 |
| `morpheus-mcp-transport` | 15 | 0 |
| `morpheus-integration-nexus` | 13 | 0 |
| `morpheus-store-memory` | 13 | 0 |
| `morpheus-provider-reference` | 3 | 0 |
| `morpheus-provider-testkit` | 3 | 0 |
| **Total** | **1 046** | **192** |

Par motif : `EI_EXPOSE_REP` 137, `EI_EXPOSE_REP2` 35, `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` 15, puis 1 chacun
`DMI_RANDOM_USED_ONLY_ONCE`, `DCN_NULLPOINTER_EXCEPTION`, `SBSC_USE_STRINGBUFFER_CONCATENATION`,
`CT_CONSTRUCTOR_THROW`, `MS_EXPOSE_REP`. Confiance : 191 moyennes, 1 haute. Rang : 174 au rang 18 (le moins
inquiétant), 15 au rang 13, un chacun aux rangs 14, 16 et 17.

Écart avec le relevé du 07/10 de `CODE_AUDIT.md` § 12 (195 alertes, dont `URF_UNREAD_FIELD` 3 et 12 pour
`provider-openspec`) : le code a changé entre les deux dates ; l'analyse n'a pas changé. Les deux relevés sont datés
et ne s'additionnent pas.

### 9.2 Contrôle bloquant

`./mvnw -o -fae -Paudit-spotbugs -DskipTests verify` : `BUILD FAILURE`, code de sortie 1, avec
`spotbugs-maven-plugin:4.10.4.1:check (audit-spotbugs-check) on project morpheus-domain: failed with 6 bugs and 0
errors`. Les 16 modules en aval sont `SKIPPED` par `-fae` (ils dépendent du domaine). C'est une **alerte sur le code**,
pas une erreur de configuration.

### 9.3 Modules bloqués

Aucun module n'est bloqué par la configuration. Seul le contrôle bloquant échoue, sur huit modules qui ont des
alertes (colonne ci-dessus). La décision à prendre (qualifier puis corriger, exclure précisément, ou resserrer
`threshold` / `maxRank`) n'est **pas prise** ici ; tant qu'elle ne l'est pas, ne pas brancher `audit-spotbugs` dans
la CI.

## 10. Dépannage

| Symptôme | Cause | Remède |
|---|---|---|
| Enforcer : `Detected JDK … is not in the allowed range [21,22)` | `JAVA_HOME` ou `PATH` désigne un autre JDK (24 sur certaines machines) | pointer `JAVA_HOME` vers un JDK 21, rouvrir le terminal |
| PowerShell : `Unknown lifecycle phase ".audit.includeTests=…"` | `-D…` non quoté, coupé au premier point | `"-Dspotbugs.audit.includeTests=true"` |
| module `SKIPPED` dans le résumé | `-fae` : un module amont est en échec | lire le module en `FAILURE`, pas ceux-ci |
| `Skipping … spotbugs report goal` | module sans classe | normal (`architecture-tests`, `coverage-report`, parent) |
| `spotbugs.html` de 0 octet | un autre run Maven réécrit `target/` en même temps | attendre la fin du run, ne pas lancer deux Maven dans le même arbre |
| rapport périmé après un changement de code | `target/classes` non recompilé (`-o` seul ne recompile pas) | lancer `verify`, ou `clean verify` après un changement de profil |
| erreur de résolution du plugin hors ligne | première exécution sans `~/.m2` peuplé | exécuter une fois sans `-o` |
| `missingClasses` > 0 dans le XML | dépendance absente du classpath d'analyse | lire les `MissingClass` ; ne pas ignorer, l'analyse est partielle |
| `outputEncoding`, `outputDirectory` ou `fork` refusés après une montée du plugin | paramètres supprimés en 4.10.4.1 | les retirer (le profil actuel n'en utilise aucun) |
| rien ne s'exécute sur `-Paudit-spotbugs` | profil mal écrit (Maven ignore un profil inconnu avec un simple avertissement) | lire `The requested profile "…" could not be activated` |

## 11. Sources officielles

Consultées le 09/10/2026 :

- Plugin Maven, documentation : <https://spotbugs.github.io/spotbugs-maven-plugin/>
- Objectif `check` (paramètres, valeurs par défaut) : <https://spotbugs.github.io/spotbugs-maven-plugin/check-mojo.html>
- Notes de version du plugin : <https://github.com/spotbugs/spotbugs-maven-plugin/releases>
- Versions publiées du plugin : <https://repo1.maven.org/maven2/com/github/spotbugs/spotbugs-maven-plugin/maven-metadata.xml>
- Versions publiées du moteur : <https://repo1.maven.org/maven2/com/github/spotbugs/spotbugs/maven-metadata.xml>
- POM du plugin 4.10.4.1 (exigences Maven et Java, version du moteur) : <https://repo1.maven.org/maven2/com/github/spotbugs/spotbugs-maven-plugin/4.10.4.1/spotbugs-maven-plugin-4.10.4.1.pom>
- Descriptions des motifs de bug : <https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html>
- Format du filtre d'exclusion : <https://spotbugs.readthedocs.io/en/stable/filter.html>
