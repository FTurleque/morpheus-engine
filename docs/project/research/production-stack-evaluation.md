# Étude M0 — Fondation technique de production

Statut : **Recommandation M0**

Date : 22 juillet 2026

## 1. Objectif

ADR-0014 interdit que la technologie des spikes Python devienne implicitement la stack MORPHEUS.

Après E01–E14 et les preuves complémentaires, il est maintenant possible de choisir une fondation de production sur des critères réels.

Cette étude sépare volontairement :

1. langage/runtime ;
2. baseline de compatibilité Java ;
3. build ;
4. backend persistant initial ;
5. frameworks d'exposition futurs.

---

## 2. Contraintes issues de C0/M0 et de l'écosystème

La fondation doit permettre :

- modèle de domaine fortement structuré ;
- providers multiples ;
- parsing Markdown/YAML/JSON ;
- fonctionnement Windows prioritaire ;
- fonctionnement Linux ;
- local-first ;
- aucune dépendance LLM ;
- CLI ;
- SQLite embarqué initial ;
- tests contractuels ;
- snapshots et transactions ;
- MCP futur ;
- API future ;
- maintenance sur plusieurs années ;
- intégration simple dans l'écosystème existant.

Contrainte d'écosystème importante :

```text
NEXUS source / bytecode baseline = Java 21
```

Cette baseline est déjà compatible avec l'environnement de développement utilisant un JDK plus récent.

MORPHEUS n'a démontré aucun besoin exigeant une API Java >21.

La fondation ne doit pas imposer dès M1 :

- serveur HTTP ;
- framework d'injection ;
- conteneur applicatif ;
- graph database ;
- runtime Python ;
- service externe.

---

## 3. Candidats langage

### Java

Forces :

- typage nominal adapté au domaine riche MORPHEUS ;
- très bon tooling IntelliJ ;
- écosystème de tests mature ;
- JDBC ;
- packaging multiplateforme ;
- forte compatibilité avec l'écosystème existant ;
- bibliothèques Markdown/YAML/JSON nombreuses ;
- API/MCP futures réalistes ;
- maintenance longue durée.

Faiblesses :

- distribution plus lourde qu'un binaire Go unique ;
- consommation mémoire supérieure à Go pour certains usages ;
- packaging runtime à concevoir explicitement.

### Go

Forces :

- excellent CLI ;
- binaire autonome ;
- démarrage rapide ;
- concurrence simple ;
- empreinte d'exploitation légère.

Faiblesses pour ce projet :

- nouvel écosystème de production à maintenir en parallèle ;
- moins d'alignement avec les composants Java existants ;
- certains choix SQLite demandent de choisir entre native/CGO/pure-Go ;
- aucun besoin de performance ou concurrence démontré qui rende Go nécessaire.

### Python

Forces :

- très rapide pour prototyper providers et parsers ;
- bibliothèques documentaires très riches ;
- spikes M0 efficaces.

Faiblesses pour la fondation :

- distribution Windows plus délicate ;
- runtime/package management supplémentaire ;
- garanties de types plus faibles au runtime ;
- performance et consommation moins prévisibles pour un moteur long-lived ;
- son avantage observé en M0 est précisément la vitesse de spike, pas une preuve de meilleure fondation produit.

---

## 4. Matrice qualitative

Échelle : 1 faible → 5 très favorable.

| Critère | Poids | Java | Go | Python |
|---|---:|---:|---:|---:|
| modèle de domaine / typage | 20 | 5 | 4 | 3 |
| Windows local-first | 15 | 4 | 5 | 3 |
| alignement écosystème | 15 | 5 | 2 | 2 |
| SQLite embarqué | 10 | 5 | 4 | 5 |
| CLI | 10 | 4 | 5 | 4 |
| MCP/API futurs | 10 | 4 | 4 | 5 |
| performance/robustesse | 10 | 4 | 5 | 2 |
| tooling/maintenance | 10 | 5 | 4 | 4 |

Résultat indicatif pondéré :

```text
Java   ~4.55 / 5
Go     ~4.00 / 5
Python ~3.30 / 5
```

Cette matrice n'est pas une mesure scientifique ; elle rend explicites les forces qui justifient le choix.

---

## 5. Baseline Java

### Java 21

JDK 21 est une release LTS et fournit déjà les fonctionnalités nécessaires à MORPHEUS.

Avantages spécifiques :

- baseline commune avec NEXUS ;
- records, sealed types, pattern matching et virtual threads déjà disponibles lorsque pertinents ;
- compatibilité plus large ;
- compilation possible avec un JDK plus récent via `javac --release 21` ;
- aucune fonctionnalité Java 22–25 nécessaire au domaine validé par M0.

### Java 25

JDK 25 est GA depuis septembre 2025 et constitue une LTS chez la plupart des vendors.

Il reste un excellent **JDK de développement/compilation possible**, mais l'utiliser comme baseline bytecode apporterait actuellement peu de valeur tout en créant une divergence gratuite avec NEXUS.

### Décision proposée

```text
language = Java
source / bytecode baseline = Java 21
compiler JDK = Java 21+ compatible
maven compiler release = 21
```

Le JDK du développeur peut donc évoluer indépendamment de la baseline produit tant que `--release 21` est respecté.

---

## 6. Build

### Maven 4

Au 22 juillet 2026, Maven 4 reste en release candidate et la documentation Apache indique qu'il n'est pas encore GA / sûr comme base production.

### Maven 3.9.x

La branche 3.9.x reste GA et maintenue ; Maven 3.9.16 est une version GA publiée en mai 2026.

### Décision proposée

```text
build = Maven
baseline = Maven 3.9.16 via Maven Wrapper
compiler release = 21
```

Le wrapper constitue l'interface de build officielle du dépôt.

Maven 4 pourra être étudié après GA dans une migration explicite.

---

## 7. Backend persistant initial

E08 a validé un candidat SQLite :

- persistance locale ;
- réouverture ;
- activation transactionnelle ;
- snapshots ;
- requêtes ;
- comparaison ;
- faible complexité opérationnelle.

E06b confirme la traçabilité persistée derrière le même port.

E09 ne montre aucune nécessité actuelle d'imposer une graph database au MVP.

Pour Java, Xerial SQLite JDBC fournit un driver JDBC SQLite avec natives pour les plateformes majeures dont Windows et Linux.

### Décision proposée

```text
initial persistent backend = SQLite
access = JDBC adapter
public boundary = SpecificationKnowledgeStore
```

Le schéma JSON du spike E08 est explicitement rejeté comme schéma de production.

La fondation M1 devra créer un schéma contrôlé et migrable pour les entités, snapshots et relations.

---

## 8. Graph database

Décision M0 :

```text
conceptual graph model = retained
mandatory graph database = no
graph store for MVP = not needed
```

Un moteur graphe spécialisé ne sera ajouté que si une mesure ultérieure montre un besoin réel.

---

## 9. Frameworks

### Framework serveur

Aucun framework serveur dans la fondation M1.

L'API arrive plus tard et ne doit pas définir le domaine.

### Injection de dépendances

Aucun framework DI obligatoire.

Les ports/adapters sont câblés explicitement tant que cette simplicité reste suffisante.

### CLI

Le choix précis de bibliothèque CLI est différé au jalon approprié.

### MCP

Le SDK concret sera décidé au jalon MCP selon l'écosystème disponible à ce moment.

---

## 10. Structure de fondation proposée

```text
morpheus-engine
├── morpheus-domain
├── morpheus-application
├── morpheus-provider-openspec
├── morpheus-store-memory
├── morpheus-store-sqlite
└── morpheus-cli
```

Le nombre exact de modules peut être simplifié si nécessaire ; la direction des dépendances est l'invariant :

```text
domain
  ↑
application
  ↑
adapters/providers/stores/exposure
```

Jamais :

```text
domain -> SQLite
domain -> OpenSpec
domain -> CLI framework
domain -> MCP
```

---

## 11. Tests

Fondation recommandée :

- JUnit 5 ;
- tests contractuels partagés pour les stores ;
- tests de providers par fixtures ;
- tests d'architecture pour interdire les dépendances inversées ;
- tests hors réseau ;
- fixtures M0 conservées comme corpus de non-régression.

---

## 12. Décision recommandée

```text
Language          : Java
Compatibility     : Java 21 source / bytecode
Compiler JDK      : 21+ compatible via --release 21
Build             : Maven 3.9.16 + Maven Wrapper
Persistent store  : SQLite behind SpecificationKnowledgeStore
Graph DB          : none for MVP
Server framework  : none in foundation
DI framework      : none required
LLM               : none required
```

Python reste autorisé pour les outils/spikes de recherche mais ne fait pas partie du runtime produit par défaut.

---

## 13. Sources primaires consultées

- OpenJDK — JDK 21 et JDK 25 project/release information.
- Oracle `javac` documentation — `--release` pour compiler vers une release antérieure.
- Apache Maven — release history/download documentation.
- Xerial SQLite JDBC — official repository/release documentation.
