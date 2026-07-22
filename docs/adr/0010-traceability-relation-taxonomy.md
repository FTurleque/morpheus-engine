# ADR-0010 — Définir une taxonomie contrôlée des relations de traçabilité

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Portée : domaine, requêtes, qualité, intégrations

---

## 1. Contexte

ADR-0005 établit `TraceabilityLink` comme concept de premier ordre.

Il reste cependant une question structurante : comment éviter qu'un modèle de graphe générique dégénère en collection de relations floues, redondantes ou contradictoires ?

Sans taxonomie contrôlée, plusieurs providers ou développeurs pourraient représenter le même concept sous des noms différents :

```text
IMPLEMENTS
IMPLEMENTED_BY
REALIZES
SATISFIES
FULFILLS
```

Cela rendrait les requêtes, les traversées, l'analyse de couverture et les intégrations incohérentes.

---

## 2. Problème

MORPHEUS a besoin d'un ensemble de relations :

- suffisamment riche pour exprimer les cas d'usage ;
- suffisamment restreint pour conserver une sémantique stable ;
- extensible sans casser les consommateurs ;
- directionnel et documenté ;
- compatible avec des relations explicites, dérivées, heuristiques ou non résolues.

---

## 3. Forces en présence

### Cohérence

Deux providers doivent produire la même sémantique MORPHEUS lorsqu'ils observent la même relation métier.

### Extensibilité

De nouveaux besoins apparaîtront, notamment avec MINOS, NEXUS et d'autres sources.

### Requêtabilité

Les consommateurs doivent pouvoir demander des familles de liens sans connaître les particularités de chaque provider.

### Explicabilité

La direction et la signification de chaque relation doivent être stables.

### Compatibilité

Une nouvelle relation ne doit pas exiger une refonte du store.

---

## 4. Décision proposée

MORPHEUS maintient une taxonomie contrôlée de `TraceabilityRelationType`.

Chaque type définit au minimum :

```text
name
semantic description
allowed source kinds
allowed target kinds
direction
inverse relation if any
transitivity policy
strength / semantic class
```

La liste n'est pas ouverte à des chaînes arbitraires dans le cœur métier.

Les providers peuvent conserver une relation externe inconnue en métadonnées ou relation d'extension, mais ils doivent mapper vers la taxonomie MORPHEUS lorsqu'une sémantique équivalente existe.

---

## 5. Taxonomie initiale candidate

### 5.1 Décomposition / raffinement

```text
REFINES
DERIVES_FROM
```

Exemples :

```text
Scenario REFINES Requirement
Requirement DERIVES_FROM Specification
```

### 5.2 Contraintes

```text
CONSTRAINS
```

Exemples :

```text
Constraint CONSTRAINS ChangeProposal
Constraint CONSTRAINS Requirement
```

### 5.3 Réalisation

```text
IMPLEMENTS
SATISFIES
```

Différence proposée :

- `IMPLEMENTS` : artefact d'exécution ou de travail réalisant une intention ;
- `SATISFIES` : relation de conformité fonctionnelle ou logique plus générale.

Exemples :

```text
ImplementationTask IMPLEMENTS Requirement
ExternalReference(MINOS Symbol) SATISFIES Requirement
```

Cette distinction devra être éprouvée en M0/M4.

### 5.4 Validation

```text
VALIDATES
VERIFIED_BY
```

Exemples :

```text
AcceptanceCriterion VALIDATES Requirement
Requirement VERIFIED_BY ExternalReference(Test)
```

`VALIDATES` décrit le rôle d'un critère ; `VERIFIED_BY` décrit une preuve ou mécanisme de vérification concret.

### 5.5 Décision

```text
DECIDED_BY
```

Exemple :

```text
ChangeProposal DECIDED_BY DesignDecision
```

### 5.6 Dépendance

```text
DEPENDS_ON
```

Exemples :

```text
ImplementationTask DEPENDS_ON ImplementationTask
ChangeProposal DEPENDS_ON ChangeProposal
```

La transitivité ne doit pas être supposée universellement sans contexte.

### 5.7 Impact / portée

```text
AFFECTS
```

Exemple :

```text
ChangeProposal AFFECTS Specification
```

### 5.8 Historique

```text
SUPERSEDES
```

Exemple :

```text
Requirement V2 SUPERSEDES Requirement V1
```

Cette relation doit être compatible avec le modèle de versionnement et ne doit pas dupliquer mécaniquement tous les liens de predecessor de snapshot.

### 5.9 Intégration externe

```text
LINKS_TO_CODE
LINKS_TO_TEST
RELATED_TO
```

`RELATED_TO` reste volontairement faible et doit être utilisé avec parcimonie.

---

## 6. Relations inverses

La relation stockée possède une direction canonique.

Exemples candidats :

```text
source IMPLEMENTS target
source VALIDATES target
source CONSTRAINS target
```

Les inverses peuvent être exposés comme vues de requête :

```text
IMPLEMENTED_BY
VALIDATED_BY
CONSTRAINED_BY
```

sans nécessairement être persistés comme deux arêtes physiques.

### Invariant

Une relation inverse dérivée ne doit pas être comptée comme une seconde preuve indépendante.

---

## 7. Cardinalité

La taxonomie peut documenter des cardinalités typiques, mais MORPHEUS ne doit pas imposer prématurément des contraintes trop fortes.

Exemples :

```text
Requirement -> many AcceptanceCriteria
ChangeProposal -> many ImplementationTasks
DesignDecision -> many affected entities
```

Certaines cardinalités sont validées par diagnostics plutôt que par contraintes de stockage strictes.

---

## 8. Transitivité

Chaque relation définit une politique :

```text
NON_TRANSITIVE
TRANSITIVE
CONTEXTUAL
```

Exemples candidats :

- `SUPERSEDES` : chaîne traversable ;
- `DEPENDS_ON` : traversable, mais la sémantique de dépendance transitive doit rester explicite ;
- `RELATED_TO` : non transitive ;
- `REFINES` : traversable pour construire une hiérarchie, sans prétendre à une équivalence.

Le moteur de requête ne doit jamais appliquer la transitivité uniquement parce que la relation est stockée dans un graphe.

---

## 9. Origine et résolution orthogonales au type

Le type de relation ne doit pas encoder la confiance.

Exemple incorrect :

```text
HEURISTIC_IMPLEMENTS
```

Exemple correct :

```text
type = IMPLEMENTS
origin = HEURISTIC
resolution = HEURISTIC
confidence = 0.74
```

Cela évite une explosion combinatoire des types.

---

## 10. Extensions provider-specific

Un provider peut rencontrer une relation sans équivalent MORPHEUS.

Trois options :

1. mapper vers une relation existante si la sémantique est réellement équivalente ;
2. conserver la relation externe dans des métadonnées ;
3. proposer une extension de taxonomie via ADR si le besoin est générique.

Un provider ne doit pas injecter automatiquement un nouveau `TraceabilityRelationType` public.

---

## 11. Conséquences positives

- requêtes cohérentes ;
- contrats API/MCP stables ;
- meilleur mapping multi-provider ;
- diagnostics de couverture plus fiables ;
- traversées explicables ;
- moins de duplication sémantique ;
- intégration MINOS plus propre ;
- possibilité d'optimiser le store selon des familles de relations connues.

---

## 12. Conséquences négatives

- gouvernance supplémentaire ;
- certains mappings providers seront imparfaits ;
- nécessité de faire évoluer la taxonomie ;
- risques de débats sémantiques ;
- migration potentielle lorsqu'une relation est redéfinie.

---

## 13. Alternatives étudiées

### A. Relation libre `string`

**Rejetée pour le cœur.**

Trop souple, impossible à gouverner durablement.

### B. Une classe dédiée pour chaque relation

**Rejetée pour le stockage générique.**

Trop rigide et coûteux à faire évoluer.

### C. Enum/taxonomie contrôlée + métadonnées

**Retenue.**

Elle combine stabilité sémantique et extensibilité contrôlée.

---

## 14. Risques et mitigations

### Risque — taxonomie trop large

Mitigation : n'ajouter une relation que si un cas d'usage concret l'exige.

### Risque — taxonomie trop étroite

Mitigation : métadonnées provider-specific et processus ADR d'extension.

### Risque — confusion `IMPLEMENTS` / `SATISFIES`

Mitigation : exemples normatifs, tests de mapping, possibilité de fusionner les types avant acceptation finale.

### Risque — `RELATED_TO` utilisé comme fourre-tout

Mitigation : diagnostics, limitation dans les analyses de couverture, interdiction de l'utiliser comme preuve forte.

---

## 15. Validation M0

Les datasets D1-D3 doivent produire au minimum les parcours :

```text
Requirement <- REFINES - Scenario
Requirement <- VALIDATES - AcceptanceCriterion
Requirement <- IMPLEMENTS - ImplementationTask
ChangeProposal - AFFECTS -> Requirement
ChangeProposal - DECIDED_BY -> DesignDecision
Constraint - CONSTRAINS -> ChangeProposal
```

M0 doit vérifier :

- mapping sans ambiguïté majeure ;
- requête inverse ;
- traversée profondeur 3 ;
- conservation de provenance ;
- absence de relation libre non contrôlée dans les résultats publics.

---

## 16. Critères d'acceptation

Cette ADR peut passer à **Acceptée** lorsque :

1. la taxonomie MVP est documentée avec sémantique et direction ;
2. les mappings OpenSpec de référence sont définis ;
3. les relations nécessaires aux UC-05, UC-07, UC-09 et UC-10 sont couvertes ;
4. les inverses sont spécifiés ;
5. la politique de transitivité est documentée ;
6. les relations provider-specific inconnues ont une stratégie de conservation ;
7. les tests de contrat démontrent des traversées cohérentes sur au moins deux backends.

---

## 17. Impact sur les autres décisions

Cette ADR complète ADR-0005 et influence :

- modèle de domaine ;
- `SpecificationKnowledgeStore` ;
- qualité et couverture ;
- analyse de changement ;
- MCP/API ;
- liens MINOS ;
- construction de contexte pour NEXUS.