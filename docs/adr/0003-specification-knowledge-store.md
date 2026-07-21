# ADR-0003 — Isoler la persistance derrière `SpecificationKnowledgeStore`

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Dépend de : ADR-0001
- Portée : stockage et requêtes

---

## 1. Contexte

MORPHEUS doit stocker et interroger des spécifications, exigences, changements, décisions, critères d'acceptation, tâches, versions et liens de traçabilité.

Plusieurs familles de stockage sont envisageables :

- base relationnelle ;
- base documentaire ;
- base graphe ;
- moteur hybride ;
- stockage embarqué ;
- backend mémoire pour tests.

Le besoin de traverser des relations plaide potentiellement pour un graphe, tandis que les besoins de simplicité locale, de versionnement et de distribution peuvent favoriser une solution embarquée plus classique.

Choisir trop tôt un produit puis exposer son API au domaine rendrait la décision difficilement réversible.

---

## 2. Décision proposée

Introduire un port possédé par MORPHEUS :

```text
SpecificationKnowledgeStore
```

Le contrat est défini à partir des cas d'usage du moteur, jamais comme copie de l'API d'une base de données.

Architecture :

```text
Domaine / Services MORPHEUS
          │
          ▼
SpecificationKnowledgeStore
          │
   ┌──────┼───────────┐
   ▼      ▼           ▼
Memory  Backend A   Backend futur
```

---

## 3. Responsabilités candidates

Le port devra permettre au minimum :

```text
storeProjectSpecification
storeSpecifications
storeRequirements
storeChanges
storeTraceabilityLinks
getCurrentSpecification
findSpecification
findRequirements
findChanges
findConstraints
findAcceptanceCriteria
findDesignDecisions
findTasks
findRelatedElements
trace
getHistory
```

La granularité exacte sera affinée après spécification des cas d'usage.

---

## 4. Ce que le port ne doit pas exposer

Le contrat public ne doit pas imposer :

- SQL ;
- Cypher ;
- Gremlin ;
- JSONPath ;
- index propres à un moteur ;
- transactions spécifiques à un produit ;
- types de driver ;
- identifiants physiques du backend.

Des options spécialisées peuvent exister dans un adaptateur interne mais ne doivent pas contaminer les services métier.

---

## 5. Forces en présence

### Simplicité

Une seule base directement utilisée serait plus rapide à implémenter.

### Traçabilité riche

Les traversées de relations peuvent nécessiter des requêtes sophistiquées.

### Local-first

Le backend doit rester raisonnablement installable et exploitable localement.

### Tests

Les tests unitaires doivent pouvoir fonctionner sans infrastructure externe.

### Évolution

La structure de connaissance peut évoluer fortement pendant les premières itérations.

### Performances

L'abstraction ne doit pas empêcher des optimisations adaptées au backend.

---

## 6. Backend mémoire obligatoire

Une implémentation mémoire de référence doit être possible.

Elle sert à :

- tester le domaine ;
- tester les services ;
- vérifier que le contrat n'est pas couplé au backend principal ;
- fournir des fixtures rapides ;
- faciliter les tests de providers.

Le backend mémoire n'a pas besoin d'offrir les mêmes performances qu'un backend de production, mais doit respecter la même sémantique fonctionnelle pour les opérations couvertes.

---

## 7. Conséquences positives

- remplacement possible du backend ;
- testabilité ;
- isolation technologique ;
- contrats stables ;
- possibilité d'expérimenter plusieurs moteurs ;
- possibilité de backend léger ou embarqué ;
- aucune dépendance de CLI/MCP/API à la base choisie.

---

## 8. Conséquences négatives

- couche supplémentaire ;
- mapping supplémentaire ;
- risque de concevoir un plus petit dénominateur commun ;
- besoin d'exposer certaines capacités avancées sans fuite technologique ;
- complexité de migration entre backends potentiels.

---

## 9. Alternatives étudiées

### A. Accès direct à une base relationnelle

**Non retenu comme frontière du domaine.**

Peut rester candidat comme implémentation.

### B. Graphe obligatoire dès le départ

**Non retenu à ce stade.**

La traçabilité est naturellement graphique, mais il faut mesurer si un moteur graphe dédié est nécessaire au MVP.

### C. Documents JSON dans Git uniquement

**Insuffisant comme moteur de requêtes principal.**

Git reste source et historique potentiel, mais MORPHEUS a besoin de requêtes normalisées et rapides.

### D. Port `SpecificationKnowledgeStore`

**Retenu.**

---

## 10. Risques

### Plus petit dénominateur commun

**Mitigation :** concevoir le port à partir des use cases et introduire des capacités explicites plutôt qu'une API générique `query(String)`.

### Double modèle coûteux

**Mitigation :** mapping simple et domaine ciblé ; ne pas reproduire toutes les capacités du backend.

### Mauvais choix de backend initial

**Mitigation :** spike M0 comparatif et mesures avant acceptation.

---

## 11. Expérimentations M0 obligatoires

1. implémenter un backend mémoire ;
2. implémenter un backend persistant candidat minimal ;
3. charger le même corpus dans les deux ;
4. exécuter les mêmes tests contractuels ;
5. mesurer ingestion, requêtes, mémoire et disque ;
6. tester au minimum une traversée de traçabilité multi-niveaux ;
7. vérifier qu'aucun type backend n'apparaît dans les services.

---

## 12. Critères de choix du backend initial

Le backend sera évalué selon :

- simplicité d'installation locale ;
- compatibilité OS cible ;
- performances ;
- qualité des traversées ;
- capacité d'indexation ;
- robustesse ;
- migration de schéma ;
- empreinte disque/mémoire ;
- maturité ;
- maintenance ;
- licence ;
- sauvegarde/reconstruction ;
- facilité d'intégration avec la stack retenue.

---

## 13. Condition d'acceptation

Cette ADR peut passer à **Acceptée** lorsque :

- les cas d'usage MVP sont définis ;
- le backend mémoire prouve la viabilité du port ;
- un backend persistant réel prouve qu'aucune capacité indispensable n'est masquée par l'abstraction ;
- les tests contractuels passent sur les deux implémentations.

Le choix du backend concret peut faire l'objet d'une ADR séparée.