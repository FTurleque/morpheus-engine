# ADR-0017 — Utiliser Maven 3.9.x avec Maven Wrapper comme fondation de build

- Statut : **Acceptée avec contraintes — M0/M1**
- Date : 22 juillet 2026
- Dépend de : ADR-0016
- Portée : build, dépendances, tests, validation locale, CI future, packaging

---

## 1. Contexte

Une fois Java retenu avec une baseline de compatibilité Java 21, MORPHEUS doit disposer d'un build reproductible sur Windows et Linux.

Le build doit gérer :

- compilation avec `--release 21` ;
- exécution possible du build avec un JDK 21 ou plus récent compatible ;
- dépendances ;
- tests ;
- multi-module si nécessaire ;
- packaging ;
- CI future éventuelle ;
- génération de distributions ;
- vérifications d'architecture.

Au 22 juillet 2026, Maven 4 reste en release candidate et la documentation Apache ne le considère pas encore GA pour une fondation de production.

La série Maven 3.9 reste GA et maintenue.

---

## 2. Décision adoptée

Adopter :

```text
Build tool = Apache Maven
Baseline M0/M1 = Maven 3.9.16
Repository pinning = Maven Wrapper
Compiler release = 21
Mandatory verification gate = Maven Wrapper local build
Remote CI = optional
```

Le dépôt utilise `mvnw` / `mvnw.cmd` afin que la version de build de référence soit contrôlée par le projet.

Le gate obligatoire est :

```text
Windows : .\mvnw.cmd clean test
Unix   : ./mvnw clean test
```

GitHub Actions ou une autre CI pourront être ajoutés lorsque le projet aura un besoin démontré de validation distante, multi-OS, publication ou automatisation de release.

La CI n'est pas une dépendance fonctionnelle de MORPHEUS et n'est pas requise pour considérer un build local reproductible comme valide.

---

## 3. Pourquoi Maven

### Cohérence

Maven fournit un modèle de build déclaratif stable et largement supporté par IntelliJ et l'écosystème Java.

### Reproductibilité

Le Maven Wrapper permet de ne pas dépendre de la version globale installée sur le poste.

### Dépendances

Les bibliothèques nécessaires au futur provider OpenSpec, à SQLite JDBC, aux tests et aux couches d'exposition sont distribuées naturellement via Maven Central.

### Multi-module

MORPHEUS peut démarrer avec une structure simple puis séparer domain/application/adapters sans changer d'outil.

### Baseline Java indépendante du JDK développeur

Le build configure :

```text
maven.compiler.release = 21
```

Ainsi, un JDK de compilation plus récent ne permet pas accidentellement l'utilisation d'API Java >21.

La preuve M1 a validé ce fonctionnement avec JDK 24.0.1 compilant en `release 21`.

---

## 4. Pourquoi pas Maven 4 immédiatement

Maven 4 apporte des évolutions intéressantes, mais il n'est pas encore GA à la date de la décision.

La fondation MORPHEUS ne doit pas dépendre d'une release candidate uniquement pour être plus récente.

Règle :

> **adopter Maven 4 uniquement après GA et après vérification de compatibilité du build MORPHEUS.**

Une migration future peut faire l'objet d'une ADR dédiée ou d'une mise à jour de celle-ci.

---

## 5. Pourquoi la CI n'est pas obligatoire

Une première tentative de GitHub Actions a montré qu'un runner distant peut échouer avant même l'exécution des étapes du workflow.

Ce type d'échec ne constitue pas une preuve de défaut du code, du Wrapper, de Maven ou des tests.

MORPHEUS privilégie donc un gate qui reste sous le contrôle du projet :

```text
Maven Wrapper + tests + architecture tests + smoke tests
```

La CI distante est considérée comme une automatisation de confort et de gouvernance, pas comme une condition structurelle du moteur.

Cette décision évite :

- de bloquer le développement sur un service externe ;
- de confondre indisponibilité de runner et échec logiciel ;
- d'introduire une dépendance cloud contraire au principe local-first.

Elle n'interdit pas une future CI.

---

## 6. Alternatives

### Gradle

**Non retenu.**

Gradle est techniquement capable de satisfaire le besoin, mais aucun avantage MORPHEUS concret ne justifie d'introduire un second modèle de build par rapport à Maven.

### Maven 4 RC

**Différé.**

Le projet préfère une GA stable pour son premier socle.

### Build manuel / scripts

**Rejeté.**

Insuffisant pour la gestion des dépendances, tests et packaging à long terme.

### GitHub Actions obligatoire

**Non retenu comme gate M1.**

Avantage : validation distante automatique.

Inconvénients : dépendance à un service externe et possibilité d'échec avant exécution du build.

Le besoin pourra être réévalué lors des jalons de distribution/publication.

---

## 7. Invariants de build

1. Java `release = 21` ;
2. pas de preview feature sans ADR ;
3. tests unitaires exécutés par défaut ;
4. tests d'architecture exécutés par le build standard ;
5. versions de dépendances explicites ou centralisées ;
6. build reproductible via Wrapper ;
7. aucun repository privé obligatoire pour compiler le cœur local ;
8. aucun secret requis pour `test` ;
9. le build principal fonctionne hors réseau une fois les dépendances en cache ;
10. JDK plus récent autorisé seulement s'il respecte `--release 21` ;
11. la CI distante reste optionnelle tant qu'un besoin explicite ne la rend pas nécessaire.

---

## 8. Structure multi-module

Structure initiale validée :

```text
pom.xml
morpheus-domain/
morpheus-application/
morpheus-provider-openspec/
morpheus-store-memory/
morpheus-store-sqlite/
morpheus-cli/
morpheus-architecture-tests/
```

L'invariant important est la direction des dépendances, pas le nombre de POMs.

---

## 9. Conséquences positives

- excellente intégration IntelliJ ;
- build familier et déclaratif ;
- dépendances Maven Central ;
- Wrapper Windows/Linux ;
- support multi-module ;
- séparation claire baseline Java / JDK développeur ;
- plugins de tests/packaging matures ;
- build valide sans dépendance à GitHub Actions ;
- migration possible vers Maven 4 ultérieurement.

---

## 10. Conséquences négatives

- XML plus verbeux ;
- Maven 3.9 n'expose pas les nouveautés Maven 4 ;
- configuration multi-module à maintenir ;
- packaging final Windows nécessitera des plugins/outils complémentaires ;
- sans CI obligatoire, la discipline de lancement du build local avant merge doit être respectée.

---

## 11. Risques et mitigations

### Migration Maven 4

Mitigation : utiliser un POM Maven 3 classique sans hacks inutiles afin de faciliter une migration après GA.

### Divergence de version Maven locale

Mitigation : Wrapper comme chemin officiel de build.

### Utilisation accidentelle d'une API Java >21

Mitigation : `maven.compiler.release=21`, validé par compilation réelle.

### Build local oublié avant merge

Mitigation : règle de contribution simple : une PR n'est pas prête tant que `clean test` n'est pas vert sur l'environnement de développement concerné.

### Besoin futur de validation multi-OS automatique

Mitigation : ajouter une CI lorsque le besoin apparaît, sans modifier le contrat de build local.

---

## 12. Preuves d'acceptation

Le bootstrap M1 a démontré sous Windows 10 :

```text
Apache Maven 3.9.16
JDK de build 24.0.1
maven.compiler.release = 21
javac release 21
ArchUnit : PASS
SQLite JDBC smoke test : PASS
BUILD SUCCESS
```

Le Maven Wrapper 3.3.4 `only-script` est commité dans le dépôt.

Ces preuves satisfont les critères de la décision.

---

## 13. Impact

Cette ADR autorise la poursuite de M1 sans GitHub Actions obligatoire.

Le contrat de build demeure stable :

```text
Maven Wrapper -> clean test -> résultat déterministe
```

Une future CI devra appeler ce même Wrapper et ne devra pas créer un second chemin de build divergent.
