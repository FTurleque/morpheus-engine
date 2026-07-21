# ADR-0005 — La traçabilité est un concept de premier ordre dans MORPHEUS

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Portée : modèle de connaissance et requêtes

---

## 1. Contexte

La valeur de MORPHEUS ne réside pas seulement dans le stockage de documents de spécification.

Le moteur doit pouvoir répondre à des questions comme :

- quelle exigence justifie cette tâche ?
- quels critères d'acceptation valident cette exigence ?
- quelle décision de conception explique ce changement ?
- quel changement remplace une règle précédente ?
- pourquoi cet élément apparaît-il dans le contexte transmis à un agent ?

Ces questions nécessitent des relations explicites entre concepts.

Si les liens restent uniquement implicites dans le texte ou sont recalculés à chaque requête, MORPHEUS perd sa capacité d'explication et de contrôle de couverture.

---

## 2. Décision proposée

Introduire `TraceabilityLink` comme concept de premier ordre du domaine.

Structure conceptuelle :

```text
TraceabilityLink
├── source
├── relationType
├── target
├── origin
├── evidence
├── confidence
├── createdAt / observedAt
└── externalReference éventuelle
```

Les relations candidates incluent :

```text
REFINES
DERIVES_FROM
CONSTRAINS
SATISFIES
IMPLEMENTS
VALIDATES
VERIFIED_BY
SUPERSEDES
DEPENDS_ON
AFFECTS
DECIDED_BY
LINKS_TO_CODE
```

La liste finale sera stabilisée selon les cas d'usage.

---

## 3. Relations factuelles et dérivées

MORPHEUS doit distinguer au minimum :

```text
EXPLICIT
DERIVED
HEURISTIC
UNRESOLVED
```

### EXPLICIT

Le lien est déclaré directement par la source ou par un identifiant non ambigu.

### DERIVED

Le lien est calculé de manière déterministe à partir de faits fiables.

### HEURISTIC

Le lien est une hypothèse utile mais non certaine.

### UNRESOLVED

Une relation est mentionnée mais sa cible n'a pas pu être résolue.

Aucune relation heuristique ne doit être présentée comme explicite.

---

## 4. Pourquoi stocker la provenance du lien

Deux liens apparemment identiques peuvent avoir des origines différentes.

Exemple :

```text
Requirement R1 VALIDATES AcceptanceCriterion AC1
```

peut provenir :

- d'une déclaration explicite du provider ;
- d'une convention de structure ;
- d'une inférence MORPHEUS ;
- d'une suggestion IA future.

Le consommateur doit pouvoir distinguer ces cas.

---

## 5. Traversée bidirectionnelle

Lorsque la sémantique le permet, MORPHEUS doit pouvoir répondre dans les deux directions.

Exemples :

```text
Requirement -> AcceptanceCriteria
AcceptanceCriterion -> Requirements

ChangeProposal -> Requirements
Requirement -> Changes

ChangeProposal -> Tasks
ImplementationTask -> ChangeProposal
```

Cela ne signifie pas que le stockage physique doit nécessairement être un graphe.

---

## 6. Conséquences positives

- explicabilité ;
- analyse de couverture ;
- détection d'orphelins ;
- chemins de justification ;
- meilleure intégration avec NEXUS ;
- meilleure intégration future avec MINOS ;
- possibilité d'analyse de changement ;
- base naturelle pour un éventuel backend graphe.

---

## 7. Conséquences négatives

- modèle plus complexe ;
- résolution des liens nécessaire ;
- gestion des références cassées ;
- risques de multiplication des types de relations ;
- besoin de politiques de confiance et provenance ;
- coût potentiel des traversées profondes.

---

## 8. Alternatives étudiées

### A. Relations uniquement dans le texte

**Rejetée.**

Impossible à interroger de manière déterministe sans retraitement.

### B. Colonnes/foreign keys spécifiques par entité

**Insuffisant comme modèle général.**

Convient à certaines relations mais devient rigide avec l'évolution des types de liens.

### C. `TraceabilityLink` générique mais typé

**Retenu.**

Permet une extension contrôlée sans rendre toutes les relations non structurées.

---

## 9. Règles de conception

1. un type de relation possède une sémantique documentée ;
2. la direction du lien est significative ;
3. la relation inverse éventuelle est définie explicitement ;
4. la provenance est obligatoire pour tout lien dérivé ;
5. un lien non résolu reste visible ;
6. un lien supprimé par la source doit être invalidé lors de la synchronisation ;
7. les liens cross-engine sont distingués des liens internes.

---

## 10. Validation M0

Le corpus de référence doit permettre au minimum :

```text
ChangeProposal -> Requirement
Requirement -> AcceptanceCriterion
ChangeProposal -> ImplementationTask
DesignDecision -> ChangeProposal
```

Les tests doivent couvrir :

- création ;
- recherche inverse ;
- cible absente ;
- relation dupliquée ;
- suppression ;
- provenance ;
- traversée multi-niveaux.

---

## 11. Critères d'acceptation

Cette ADR peut passer à **Acceptée** lorsque :

- les premiers types de relations sont spécifiés ;
- les liens du corpus de référence sont ingérés ou dérivés de manière explicable ;
- `trace_requirement` produit un chemin stable ;
- les relations non résolues ne sont pas silencieusement supprimées ;
- le backend choisi supporte les traversées MVP avec des performances acceptables.

---

## 12. Impact futur

Cette décision constitue la base de :

- M4 — Traçabilité ;
- M6 — Qualité et couverture ;
- M8 — Analyse des changements ;
- intégration MINOS ;
- intégration NEXUS ;
- conformité future spécification ↔ code.