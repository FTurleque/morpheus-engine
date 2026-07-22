# Étude M0 — Fondation technique de production

Statut : **Recommandation M0**

Date : 22 juillet 2026

## 1. Objectif

ADR-0014 interdit que la technologie des spikes Python devienne implicitement la stack MORPHEUS.

Après E01–E14 et les preuves complémentaires, il est maintenant possible de choisir une fondation de production sur des critères réels.

Cette étude sépare volontairement :

1. langage/runtime ;
2. build ;
3. backend persistant initial ;
4. frameworks d'exposition futurs.

---

## 2. Contraintes issues de C0/M0

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

Elle ne doit pas imposer dès M1 :

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
- très bon tooling IDE ;
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

- nouvel écosystème à maintenir en parallèle ;
- moins d'alignement avec les composants Java existants ;
- certains choix SQLite demandent de choisir entre native/CGO/pure-Go ;
- moins de bénéfice spécifique pour un moteur principalement orienté domaine, parsing documentaire et stockage local.

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
- les avantages observés en M0 concernent surtout la vitesse de spike, précisément ce qu'ADR-0014 interdit de confondre avec la production.

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

Cette matrice ne prétend pas être une mesure scientifique. Elle rend explicites les raisons du choix et évite une décision basée uniquement sur préférence personnelle.

---

## 5. Runtime Java

### Java 25

JDK 25 est GA depuis le 16 septembre 2025 et OpenJDK indique qu'il s'agit d'une release LTS chez la plupart des vendors.

Il constitue la cible recommandée pour un nouveau projet démarré en 2026.

### Java 21

Alternative LTS plus ancienne, très mature.

Non retenue comme cible principale parce que MORPHEUS démarre après la GA de Java 25 et n'a aucune contrainte de compatibilité legacy nécessitant Java 21.

### Décision proposée

```text
production runtime baseline = Java 25
```

La politique de support de versions ultérieures devra être documentée séparément.

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
```

Le wrapper doit constituer la référence du dépôt afin que la version de Maven ne dépende pas de l'installation globale du poste.

Maven 4 pourra être étudié après GA, dans une ADR de migration si nécessaire.

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

E06b a confirmé que la traçabilité peut être persistée derrière le même port.

E09 n'a montré aucune nécessité actuelle d'imposer une graph database au MVP.

Pour Java, Xerial SQLite JDBC fournit un driver SQLite JDBC avec bibliothèques natives pour les plateformes majeures, dont Windows et Linux.

### Décision proposée

```text
initial persistent backend = SQLite
access = JDBC adapter
public boundary = SpecificationKnowledgeStore
```

Important : le schéma JSON du spike E08 est rejeté comme schéma de production.

La fondation M1 devra utiliser un schéma contrôlé/migrable adapté aux entités, snapshots et relations.

---

## 8. Graph database

Décision M0 :

```text
conceptual graph model = retained
mandatory graph database = no
```

Un moteur graphe dédié ne sera ajouté que si une mesure ultérieure démontre un besoin que SQLite ne satisfait pas raisonnablement.

---

## 9. Frameworks

### Framework serveur

Aucun framework serveur en M1.

L'API est prévue plus tard ; elle ne doit pas définir le domaine.

### Injection de dépendances

Aucun framework DI obligatoire dans la fondation.

Les ports/adapters doivent pouvoir être câblés explicitement tant que la complexité ne justifie pas une solution supplémentaire.

### CLI

Le choix précis de bibliothèque CLI est différé au jalon CLI, sauf besoin minimal de bootstrap.

### MCP

Le SDK/protocole concret sera décidé au jalon MCP sur la base de l'écosystème disponible à ce moment.

---

## 10. Structure de fondation proposée

```text
morpheus-engine
├── morpheus-domain
├── morpheus-application
├── morpheus-provider-openspec
├── morpheus-store-memory
├── morpheus-store-sqlite
└── morpheus-cli            (léger / évolutif)
```

Cette structure est une cible candidate. Le nombre exact de modules peut être simplifié au démarrage si les dépendances restent contrôlées.

Invariant de dépendances :

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
- tests de provider par fixtures ;
- tests d'architecture pour interdire les dépendances inversées ;
- tests hors réseau ;
- fixtures M0 conservées comme corpus de non-régression.

---

## 12. Décision recommandée

```text
Language/runtime : Java 25
Build            : Maven 3.9.16 + Maven Wrapper
Persistent store : SQLite behind SpecificationKnowledgeStore
Graph DB         : none for MVP
Server framework : none in foundation
DI framework     : none required
LLM              : none required
```

Python reste autorisé pour des outils/spikes de recherche mais ne fait pas partie du runtime produit par défaut.

---

## 13. Sources primaires consultées

- OpenJDK — JDK 25 project/release information.
- Apache Maven — release history/download documentation.
- Xerial SQLite JDBC — official repository/release documentation.
