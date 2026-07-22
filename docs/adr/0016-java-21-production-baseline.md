# ADR-0016 — Utiliser Java 21 comme baseline de compatibilité MORPHEUS

- Statut : **Proposée — décision de sortie M0**
- Date : 22 juillet 2026
- Dépend de : ADR-0014
- Portée : langage, bytecode/runtime minimal, packaging, maintenance

---

## 1. Contexte

M0 a volontairement utilisé Python pour produire rapidement des preuves fonctionnelles.

ADR-0014 interdit de transformer la technologie des spikes en fondation de production par inertie.

La sortie de M0 exige maintenant un langage/runtime maintenable pour :

- le domaine riche MORPHEUS ;
- les providers ;
- le stockage local ;
- la CLI ;
- les futurs MCP/API ;
- Windows et Linux ;
- une durée de vie longue.

Une contrainte d'écosystème supplémentaire doit être prise en compte : NEXUS utilise déjà une baseline Java 21, validée dans l'environnement de développement existant avec un JDK plus récent.

---

## 2. Décision proposée

Adopter :

```text
Language = Java
Source / bytecode baseline = Java 21
Compiler JDK = Java 21 ou version plus récente compatible
Maven compiler release = 21
```

Le cœur MORPHEUS n'utilisera donc que les API et fonctionnalités Java SE 21 dans sa fondation.

Un JDK 24, 25 ou ultérieur peut compiler le projet avec :

```text
--release 21
```

sans modifier la baseline runtime.

Les outils/spikes de recherche peuvent utiliser d'autres langages lorsqu'ils restent hors runtime produit.

---

## 3. Pourquoi Java

Java apporte :

- types explicites pour le domaine ;
- records et sealed types disponibles dans la baseline ;
- excellente intégration IntelliJ ;
- JDBC ;
- outillage de tests mature ;
- bibliothèques Markdown/YAML/JSON ;
- packaging multiplateforme ;
- écosystème adapté à CLI/API/MCP futurs ;
- cohérence avec l'environnement existant.

Le domaine MORPHEUS manipule de nombreux concepts orthogonaux : identités, versions, états temporels, lifecycle, relations, evidence et diagnostics. Un langage fortement typé limite la dérive vers des dictionnaires ou chaînes interchangeables.

---

## 4. Pourquoi Java 21 comme baseline plutôt que Java 25

JDK 25 est disponible et constitue une LTS chez la plupart des vendors. Il reste un excellent JDK de développement.

Cependant MORPHEUS ne nécessite aucune fonctionnalité Java 25 pour satisfaire C0/M0.

Java 21 est retenu comme baseline parce que :

1. il s'agit d'une LTS ;
2. il couvre déjà les fonctionnalités nécessaires au domaine ;
3. il aligne MORPHEUS sur la baseline Java de NEXUS ;
4. l'environnement développeur actuel peut compiler une cible 21 avec un JDK plus récent ;
5. il évite d'imposer immédiatement l'installation d'un runtime 25 pour exécuter MORPHEUS ;
6. il conserve une marge de compatibilité plus large sans coût fonctionnel identifié.

Le choix est donc :

> **utiliser une LTS suffisamment moderne et déjà commune à l'écosystème, plutôt que choisir la LTS la plus récente sans besoin concret.**

---

## 5. `--release 21`

Le build doit utiliser le mécanisme standard `javac --release 21` via Maven.

Cela garantit que :

- le bytecode cible Java 21 ;
- la compilation utilise l'API documentée de Java 21 ;
- un JDK de compilation plus récent ne permet pas accidentellement l'usage d'une API plus récente.

---

## 6. Alternatives

### Java 25 comme baseline

**Non retenu à M0.**

Avantages : LTS plus récente, nouvelles fonctionnalités.

Inconvénient déterminant : aucune de ces fonctionnalités n'est nécessaire et la cible créerait une divergence gratuite avec la baseline Java déjà utilisée par NEXUS.

Java 25 reste autorisé comme JDK de compilation si `--release 21` est respecté.

### Go

**Non retenu pour la fondation initiale.**

Excellent pour CLI et distribution binaire, mais aucun besoin de concurrence/performance ne compense l'introduction d'un second écosystème de production.

### Python

**Conservé pour les spikes, non retenu comme runtime produit.**

Sa vitesse de prototypage a été utile en M0 mais ne justifie pas un runtime/package manager supplémentaire pour un domaine fortement structuré et distribué sur Windows.

---

## 7. Invariants

1. le domaine ne dépend d'aucun framework Java ;
2. Java 21 ne signifie pas Spring/Quarkus ;
3. aucune preview feature dans la fondation ;
4. `maven.compiler.release = 21` ou équivalent ;
5. les contrats restent sérialisables indépendamment de la JVM ;
6. les providers et stores restent des adapters ;
7. les fixtures M0 doivent devenir des tests de non-régression Java ;
8. les spikes Python restent des preuves et ne deviennent pas une seconde implémentation produit.

---

## 8. Conséquences positives

- domaine fortement typé ;
- compatibilité avec une LTS moderne ;
- alignement NEXUS/MORPHEUS ;
- compilation possible avec JDK 21+ compatible ;
- environnement Windows réaliste ;
- JDBC mature ;
- tooling IntelliJ/Maven robuste ;
- possibilité de migrer ultérieurement la baseline par ADR.

---

## 9. Conséquences négatives

- les fonctionnalités Java 22–25 ne peuvent pas être utilisées dans le code produit ;
- runtime plus lourd qu'un binaire Go ;
- packaging JRE/jpackage reste à traiter plus tard ;
- une future migration de baseline nécessitera une décision explicite.

---

## 10. Risques et mitigations

### Utilisation accidentelle d'une API >21 avec un JDK récent

Mitigation : `--release 21` obligatoire dans Maven et CI.

### Divergence de JDK développeur

Mitigation : distinguer clairement le JDK utilisé pour compiler de la baseline du bytecode.

### Baseline trop conservatrice à terme

Mitigation : réévaluer uniquement lorsqu'une fonctionnalité, une dépendance ou une politique de support apporte un bénéfice concret.

### Packaging Windows

Mitigation : prévoir une expérimentation de distribution avant stabilisation CLI.

---

## 11. Critères d'acceptation

Cette ADR peut être acceptée à la sortie M0 lorsque :

- aucune exigence M0 ne nécessite > Java 21 ;
- Java/JDBC reste viable pour SQLite ;
- le build impose `release 21` ;
- aucune dépendance Python n'est nécessaire au runtime ;
- la compatibilité Windows reste une exigence explicite.

Ces conditions sont satisfaites par les preuves M0.

---

## 12. Impact

Cette ADR permet de démarrer la fondation M1 et déclenche les décisions séparées sur :

- Maven ;
- modules/packages ;
- SQLite JDBC ;
- tests ;
- parsing OpenSpec ;
- distribution future.

Elle ne choisit ni framework web, ni DI, ni bibliothèque CLI.
