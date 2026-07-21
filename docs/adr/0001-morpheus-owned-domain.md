# ADR-0001 — Le domaine MORPHEUS est indépendant des formats et providers

- Statut : **Proposée — à valider pendant C0**
- Date : 22 juillet 2026
- Décision concernée : frontière du domaine
- Portée : structurante, difficile à inverser si elle est violée tôt

---

## 1. Contexte

MORPHEUS doit comprendre des spécifications et intentions provenant potentiellement de plusieurs sources : OpenSpec, Markdown structuré, formats futurs ou systèmes externes.

Le premier provider réel envisagé est OpenSpec. Ce choix crée un risque classique : concevoir les objets du domaine en reproduisant directement la structure du premier format disponible.

Une telle approche serait rapide au début mais transformerait progressivement MORPHEUS en façade d'un outil particulier.

Le produit perdrait alors :

- son indépendance ;
- sa capacité à intégrer d'autres sources ;
- la stabilité de ses contrats publics ;
- sa valeur propre de moteur d'intelligence.

---

## 2. Problème

Comment permettre à MORPHEUS d'exploiter profondément un provider de référence sans que ce provider définisse le langage métier et les frontières du moteur ?

---

## 3. Forces en présence

### 3.1 Vitesse initiale

Réutiliser directement les structures d'un provider réduit le travail de mapping et accélère un prototype.

### 3.2 Pérennité

Les formats externes évoluent indépendamment de MORPHEUS.

### 3.3 Multi-provider

Un futur provider peut exprimer les mêmes intentions avec une structure totalement différente.

### 3.4 Contrats publics

CLI, MCP, API, NEXUS et JARVIS ont besoin de concepts stables.

### 3.5 Testabilité

Le domaine doit pouvoir être testé sans installer le provider réel.

### 3.6 Valeur produit

La valeur de MORPHEUS réside dans la normalisation, la traçabilité et l'intelligence des spécifications, pas dans la simple lecture d'un format.

---

## 4. Décision proposée

MORPHEUS possède son propre modèle de domaine.

Les concepts publics sont définis à partir des cas d'usage MORPHEUS et non à partir des fichiers ou classes d'un provider.

Concepts candidats :

```text
ProjectSpecification
Specification
Requirement
Scenario
ChangeProposal
Constraint
DesignDecision
AcceptanceCriterion
ImplementationTask
SpecificationVersion
Evidence
TraceabilityLink
ExternalReference
```

Les providers sont placés derrière une abstraction :

```text
SpecificationProvider
```

Flux obligatoire :

```text
Format externe
     │
     ▼
Provider / Adapter
     │
     ▼
Ingestion / Normalisation
     │
     ▼
Domaine MORPHEUS
```

Le flux inverse, s'il existe un jour pour l'écriture, passera également par un adaptateur explicite.

---

## 5. Invariants architecturaux

1. Aucun type propre à un provider ne doit apparaître dans les interfaces du domaine.
2. Aucun chemin de fichier spécifique à un provider ne doit devenir une identité métier universelle sans justification.
3. Les statuts propres à un provider doivent être mappés vers des concepts MORPHEUS ou conservés comme métadonnées externes.
4. Les identifiants externes peuvent être conservés mais ne constituent pas automatiquement l'identité MORPHEUS.
5. Un test du domaine doit pouvoir s'exécuter sans OpenSpec ni autre provider installé.
6. Un deuxième provider doit pouvoir être ajouté sans modifier les cas d'usage publics existants, sauf extension légitime du domaine.

---

## 6. Conséquences positives

- découplage fort ;
- stabilité des contrats ;
- testabilité ;
- possibilité de plusieurs providers ;
- possibilité d'évolution indépendante ;
- meilleure séparation des responsabilités ;
- capacité à enrichir le modèle au-delà des limites du format source.

---

## 7. Conséquences négatives

- coût initial de mapping ;
- duplication apparente de certaines structures ;
- nécessité de définir des règles de normalisation ;
- gestion plus complexe des concepts non représentables ;
- besoin de tests de conformité par provider.

Ces coûts sont acceptés car ils protègent une frontière structurante.

---

## 8. Alternatives étudiées

### A. Exposer directement le modèle du premier provider

**Rejetée.**

Avantage : prototype rapide.

Inconvénients : couplage irréversible, contrats instables, multi-provider difficile.

### B. Utiliser uniquement des documents Markdown bruts

**Rejetée comme modèle principal.**

Le texte brut peut être conservé comme preuve ou contenu source, mais il ne permet pas à lui seul des requêtes fiables de traçabilité.

### C. Définir une union de tous les formats connus

**Rejetée.**

Cette approche produit un domaine guidé par les fournisseurs au lieu des cas d'usage.

### D. Domaine MORPHEUS normalisé + métadonnées externes

**Retenue.**

Elle préserve la sémantique MORPHEUS tout en conservant les informations nécessaires au round-trip ou au diagnostic.

---

## 9. Risques

### Risque : abstraction trop générique

Un domaine excessivement abstrait pourrait perdre des informations importantes.

**Réponse :** conserver `Evidence`, `ExternalReference` et métadonnées provider contrôlées.

### Risque : sur-conception avant expérimentation

Le modèle pourrait devenir théorique.

**Réponse :** valider chaque concept avec au moins un cas réel et ajuster pendant M0.

### Risque : fuite indirecte de concepts provider

Même sans types techniques, les noms ou états pourraient reproduire un provider.

**Réponse :** revue d'architecture et test avec un second provider minimal.

---

## 10. Validation requise

Pendant M0, démontrer que :

1. OpenSpec peut être ingéré vers le domaine MORPHEUS ;
2. aucun type OpenSpec ne traverse l'interface du provider ;
3. un jeu de données synthétique indépendant d'OpenSpec produit les mêmes concepts métier ;
4. les services `find_requirements`, `get_change` et `trace_requirement` fonctionnent sans connaissance du provider ;
5. un backend mémoire peut utiliser les mêmes objets de domaine.

---

## 11. Condition d'acceptation

Cette ADR pourra passer à **Acceptée** lorsque :

- le modèle de domaine C0 est validé ;
- le prototype M0 démontre l'isolation du provider ;
- au moins un test d'architecture protège l'absence de dépendance du domaine vers un adaptateur externe.

---

## 12. Conséquence sur les autres décisions

Cette ADR devient une contrainte pour :

- le provider OpenSpec ;
- `SpecificationKnowledgeStore` ;
- CLI ;
- MCP ;
- API ;
- intégrations MINOS/NEXUS/JARVIS.

Aucune décision ultérieure ne doit contourner cette frontière sans nouvelle ADR explicite.