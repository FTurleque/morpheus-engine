# ADR-0017 — Utiliser Maven 3.9.x avec Maven Wrapper comme fondation de build

- Statut : **Proposée — décision de sortie M0**
- Date : 22 juillet 2026
- Dépend de : ADR-0016
- Portée : build, dépendances, tests, CI, packaging

---

## 1. Contexte

Une fois Java 25 retenu comme runtime candidat, MORPHEUS doit disposer d'un build reproductible sur Windows et Linux.

Le build doit gérer :

- compilation Java 25 ;
- dépendances ;
- tests ;
- multi-module si nécessaire ;
- packaging ;
- CI future ;
- génération de distributions ;
- vérifications d'architecture.

Au 22 juillet 2026, Maven 4 reste en release candidate et la documentation Apache ne le considère pas encore GA pour une fondation de production.

La série Maven 3.9 reste GA et maintenue.

---

## 2. Décision proposée

Adopter :

```text
Build tool = Apache Maven
Baseline M0/M1 = Maven 3.9.16
Repository pinning = Maven Wrapper
```

Le dépôt doit utiliser `mvnw` / `mvnw.cmd` afin que la version de build de référence soit contrôlée par le projet.

---

## 3. Pourquoi Maven

### Cohérence

Maven fournit un modèle de build déclaratif stable et largement supporté par IntelliJ, CI et l'écosystème Java.

### Reproductibilité

Le Maven Wrapper permet de ne pas dépendre de la version globale installée sur le poste.

### Dépendances

Les bibliothèques nécessaires au futur provider OpenSpec, à SQLite JDBC, aux tests et aux couches d'exposition sont distribuées naturellement via Maven Central.

### Multi-module

MORPHEUS peut démarrer avec une structure simple puis séparer domain/application/adapters sans changer d'outil.

---

## 4. Pourquoi pas Maven 4 immédiatement

Maven 4 apporte des évolutions intéressantes, mais il n'est pas encore GA à la date de la décision.

La fondation MORPHEUS ne doit pas dépendre d'une release candidate uniquement pour être plus récente.

Règle :

> **adopter Maven 4 uniquement après GA et après vérification de compatibilité du build MORPHEUS.**

Une migration future peut faire l'objet d'une ADR dédiée ou d'une mise à jour de celle-ci.

---

## 5. Alternatives

### Gradle

**Non retenu.**

Gradle est techniquement capable de satisfaire le besoin, mais aucun avantage MORPHEUS concret ne justifie d'introduire un second modèle de build par rapport à Maven.

### Maven 4 RC

**Différé.**

Le projet préfère une GA stable pour son premier socle.

### Build manuel / scripts

**Rejeté.**

Insuffisant pour la gestion des dépendances, tests, packaging et CI à long terme.

---

## 6. Invariants de build

1. Java release = 25 ;
2. pas de preview feature sans ADR ;
3. tests unitaires exécutés par défaut ;
4. tests d'architecture exécutables en CI ;
5. versions de dépendances explicites ou centralisées ;
6. build reproductible via wrapper ;
7. aucun repository privé obligatoire pour compiler le cœur open/local ;
8. aucun secret requis pour `test` ;
9. le build principal fonctionne hors réseau une fois les dépendances en cache.

---

## 7. Structure multi-module

Structure initiale candidate :

```text
pom.xml
morpheus-domain/
morpheus-application/
morpheus-provider-openspec/
morpheus-store-memory/
morpheus-store-sqlite/
morpheus-cli/
```

Le nombre de modules peut être réduit si le coût initial est disproportionné.

L'invariant important est la direction des dépendances, pas le nombre de POMs.

---

## 8. Conséquences positives

- excellente intégration IntelliJ ;
- build familier et déclaratif ;
- dépendances Maven Central ;
- wrapper Windows/Linux ;
- support multi-module ;
- plugins de tests/packaging matures ;
- migration possible vers Maven 4 ultérieurement.

---

## 9. Conséquences négatives

- XML plus verbeux ;
- Maven 3.9 n'expose pas les nouveautés Maven 4 ;
- configuration multi-module à maintenir ;
- packaging final Windows nécessitera des plugins/outils complémentaires.

---

## 10. Risques et mitigations

### Migration Maven 4

Mitigation : utiliser un POM Maven 3 classique sans hacks inutiles afin de faciliter une migration après GA.

### Divergence de version Maven locale

Mitigation : wrapper comme chemin officiel de build.

### Plugins incompatibles Java 25

Mitigation : sélectionner des versions de plugins actuelles et les verrouiller explicitement.

---

## 11. Critères d'acceptation

Cette ADR peut être acceptée à la sortie M0 si :

- Java est retenu ;
- Maven 4 n'est pas encore GA ;
- Maven 3.9.x est GA et maintenu ;
- le wrapper est prévu comme interface officielle ;
- aucun besoin du projet n'exige une capacité spécifique Maven 4.

Ces conditions sont satisfaites.

---

## 12. Impact

Cette ADR permet la création du premier squelette produit après clôture M0.

Elle ne sélectionne pas les bibliothèques métier ni les frameworks futurs.
